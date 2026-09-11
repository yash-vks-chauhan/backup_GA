package com.gridee.parking.ui.wallet

import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.PaymentStatusResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-scoped, bounded verification for Cashfree orders.
 *
 * Cashfree callbacks, activity resume, and the payment result sheet can all ask about the same
 * order at nearly the same time. They must await one verification session rather than creating
 * independent polling loops. A session performs at most four GETs: immediately, then after
 * 2, 4, and 8 seconds. Its result remains cached for this app process so a late callback or a
 * automatic callbacks cannot restart polling for an exhausted/terminal order. After the UI's
 * cooldown, an explicit user action may make one shared immediate check without restarting the
 * four-attempt sequence.
 */
object PaymentStatusCoordinator {

    enum class Outcome { PAID, PENDING_OR_UNKNOWN, TERMINAL_UNPAID }

    data class Verification(
        val outcome: Outcome,
        val response: PaymentStatusResponse?,
        val attempts: Int
    ) {
        val isPaid: Boolean get() = outcome == Outcome.PAID
        val isPendingOrUnknown: Boolean get() = outcome == Outcome.PENDING_OR_UNKNOWN
    }

    private val verifier = BoundedPaymentStatusVerifier(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        statusFetcher = { orderId ->
            runCatching {
                ApiClient.apiService.getPaymentStatus(orderId)
                    .takeIf { it.isSuccessful }
                    ?.body()
            }.getOrNull()
        }
    )

    suspend fun verify(orderId: String): Verification = verifier.verify(orderId)

    /** One immediate safety check for a Cashfree failure/cancel callback. */
    suspend fun checkOnce(orderId: String): Verification = verifier.checkOnce(orderId)

    /** One explicit check after the UI's cooldown; it never restarts the four-attempt loop. */
    suspend fun manualRecheck(orderId: String): Verification = verifier.manualRecheck(orderId)
}

/** Testable implementation behind [PaymentStatusCoordinator]. */
internal class BoundedPaymentStatusVerifier(
    private val scope: CoroutineScope,
    private val statusFetcher: suspend (String) -> PaymentStatusResponse?,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val attemptDelaysMs: List<Long> = DEFAULT_ATTEMPT_DELAYS_MS
) {
    private val lock = Mutex()
    private val sessions = linkedMapOf<String, Deferred<PaymentStatusCoordinator.Verification>>()
    private val manualChecks = linkedMapOf<String, Deferred<PaymentStatusCoordinator.Verification>>()
    private val terminalResults = linkedMapOf<String, PaymentStatusCoordinator.Verification>()

    suspend fun verify(orderId: String): PaymentStatusCoordinator.Verification {
        val normalizedOrderId = orderId.trim()
        if (normalizedOrderId.isEmpty()) {
            return PaymentStatusCoordinator.Verification(
                outcome = PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN,
                response = null,
                attempts = 0
            )
        }

        lock.withLock { terminalResults[normalizedOrderId] }?.let { return it }

        val session = lock.withLock {
            sessions[normalizedOrderId] ?: scope.async(start = CoroutineStart.LAZY) {
                runVerification(normalizedOrderId)
            }.also { created ->
                sessions[normalizedOrderId] = created
                trimCompletedSessions()
                created.start()
            }
        }
        return session.await().also { result -> rememberTerminal(normalizedOrderId, result) }
    }

    suspend fun manualRecheck(orderId: String): PaymentStatusCoordinator.Verification {
        return checkOnce(orderId)
    }

    /**
     * Makes exactly one status request (or joins the same in-flight one-shot request). Unlike
     * [manualRecheck], this never starts or waits for the four-attempt verification sequence.
     */
    suspend fun checkOnce(orderId: String): PaymentStatusCoordinator.Verification {
        val normalizedOrderId = orderId.trim()
        if (normalizedOrderId.isEmpty()) {
            return PaymentStatusCoordinator.Verification(
                outcome = PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN,
                response = null,
                attempts = 0
            )
        }

        lock.withLock { terminalResults[normalizedOrderId] }?.let { return it }

        val check = lock.withLock {
            manualChecks[normalizedOrderId] ?: scope.async(start = CoroutineStart.LAZY) {
                runSingleCheck(normalizedOrderId)
            }.also { created ->
                manualChecks[normalizedOrderId] = created
                created.start()
            }
        }

        return try {
            check.await().also { result -> rememberTerminal(normalizedOrderId, result) }
        } finally {
            lock.withLock { manualChecks.remove(normalizedOrderId, check) }
        }
    }

    private suspend fun runVerification(orderId: String): PaymentStatusCoordinator.Verification {
        var lastResponse: PaymentStatusResponse? = null

        attemptDelaysMs.forEachIndexed { index, waitMs ->
            if (waitMs > 0L) wait(waitMs)

            val response = runCatching { statusFetcher(orderId) }.getOrNull()
            if (response != null) lastResponse = response
            val attempts = index + 1

            when {
                response?.isPaid == true -> {
                    return PaymentStatusCoordinator.Verification(
                        outcome = PaymentStatusCoordinator.Outcome.PAID,
                        response = response,
                        attempts = attempts
                    )
                }

                response.isTerminalUnpaid() -> {
                    return PaymentStatusCoordinator.Verification(
                        outcome = PaymentStatusCoordinator.Outcome.TERMINAL_UNPAID,
                        response = response,
                        attempts = attempts
                    )
                }
            }
        }

        return PaymentStatusCoordinator.Verification(
            outcome = PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN,
            response = lastResponse,
            attempts = attemptDelaysMs.size
        )
    }

    private suspend fun runSingleCheck(orderId: String): PaymentStatusCoordinator.Verification {
        val response = runCatching { statusFetcher(orderId) }.getOrNull()
        val outcome = when {
            response?.isPaid == true -> PaymentStatusCoordinator.Outcome.PAID
            response.isTerminalUnpaid() -> PaymentStatusCoordinator.Outcome.TERMINAL_UNPAID
            else -> PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN
        }
        return PaymentStatusCoordinator.Verification(outcome, response, attempts = 1)
    }

    private suspend fun rememberTerminal(
        orderId: String,
        result: PaymentStatusCoordinator.Verification,
    ) {
        if (result.outcome == PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN) return
        lock.withLock {
            terminalResults[orderId] = result
            while (terminalResults.size > MAX_RETAINED_ORDERS) {
                terminalResults.remove(terminalResults.keys.first())
            }
        }
    }

    /** Prevent an unbounded process-lifetime map while never evicting an active request. */
    private fun trimCompletedSessions() {
        if (sessions.size <= MAX_RETAINED_ORDERS) return
        val iterator = sessions.entries.iterator()
        while (sessions.size > MAX_RETAINED_ORDERS && iterator.hasNext()) {
            if (iterator.next().value.isCompleted) iterator.remove()
        }
    }

    private fun PaymentStatusResponse?.isTerminalUnpaid(): Boolean {
        if (this == null || isPaid || isPending) return false
        return !status.isNullOrBlank()
    }

    private companion object {
        val DEFAULT_ATTEMPT_DELAYS_MS = listOf(0L, 2_000L, 4_000L, 8_000L)
        const val MAX_RETAINED_ORDERS = 64
    }
}
