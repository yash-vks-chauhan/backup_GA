package com.gridee.parking.ui.wallet

import com.gridee.parking.data.model.PaymentStatusResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PaymentStatusCoordinatorTest {

    @Test
    fun pendingOrderUsesImmediateThenTwoFourEightSecondScheduleOnly() = runBlocking {
        val calls = AtomicInteger(0)
        val waits = mutableListOf<Long>()
        val verifier = verifier(
            fetch = {
                calls.incrementAndGet()
                PaymentStatusResponse(orderId = it, status = "PENDING")
            },
            wait = { waits += it }
        )

        val result = verifier.verify("order-1")

        assertEquals(PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN, result.outcome)
        assertEquals(4, result.attempts)
        assertEquals(4, calls.get())
        assertEquals(listOf(2_000L, 4_000L, 8_000L), waits)

        // Automatic callbacks reuse exhaustion instead of restarting the four-call sequence.
        assertSame(result, verifier.verify("order-1"))
        assertEquals(4, calls.get())
    }

    @Test
    fun manualRecheckAfterExhaustionMakesOneCallAndCanObservePaid() = runBlocking {
        val calls = AtomicInteger(0)
        val verifier = verifier(
            fetch = {
                val call = calls.incrementAndGet()
                PaymentStatusResponse(status = if (call <= 4) "PENDING" else "PAID")
            }
        )

        assertEquals(
            PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN,
            verifier.verify("late-payment").outcome,
        )
        assertEquals(4, calls.get())

        val manual = verifier.manualRecheck("late-payment")
        assertEquals(PaymentStatusCoordinator.Outcome.PAID, manual.outcome)
        assertEquals(1, manual.attempts)
        assertEquals(5, calls.get())

        assertSame(manual, verifier.verify("late-payment"))
        assertEquals(5, calls.get())
    }

    @Test
    fun oneShotCheckDoesNotStartTheAutomaticFourAttemptSequence() = runBlocking {
        val calls = AtomicInteger(0)
        val waits = mutableListOf<Long>()
        val verifier = verifier(
            fetch = {
                calls.incrementAndGet()
                PaymentStatusResponse(orderId = it, status = "PENDING")
            },
            wait = { waits += it }
        )

        val result = verifier.checkOnce("checkout-launch-failure")

        assertEquals(PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN, result.outcome)
        assertEquals(1, result.attempts)
        assertEquals(1, calls.get())
        assertTrue(waits.isEmpty())
    }

    @Test
    fun paidAndFailedStatusesStopTheSequenceImmediately() = runBlocking {
        var paidCalls = 0
        val paidVerifier = verifier(
            fetch = {
                paidCalls += 1
                PaymentStatusResponse(status = if (paidCalls == 1) "PENDING" else "PAID")
            }
        )

        val paid = paidVerifier.verify("paid-order")
        assertEquals(PaymentStatusCoordinator.Outcome.PAID, paid.outcome)
        assertEquals(2, paid.attempts)

        var failedCalls = 0
        val failedVerifier = verifier(
            fetch = {
                failedCalls += 1
                PaymentStatusResponse(status = "FAILED")
            }
        )

        val failed = failedVerifier.verify("failed-order")
        assertEquals(PaymentStatusCoordinator.Outcome.TERMINAL_UNPAID, failed.outcome)
        assertEquals(1, failed.attempts)
        assertEquals(1, failedCalls)
    }

    @Test
    fun concurrentTriggersAwaitOneInFlightStatusRequest() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val verifier = BoundedPaymentStatusVerifier(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            statusFetcher = {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                PaymentStatusResponse(status = "PAID")
            },
            wait = {},
            attemptDelaysMs = listOf(0L)
        )

        val fromSdk = async { verifier.verify("shared-order") }
        started.await()
        val fromResume = async { verifier.verify("shared-order") }
        yield()

        assertEquals(1, calls.get())
        release.complete(Unit)
        assertEquals(PaymentStatusCoordinator.Outcome.PAID, fromSdk.await().outcome)
        assertEquals(PaymentStatusCoordinator.Outcome.PAID, fromResume.await().outcome)
        assertEquals(1, calls.get())
    }

    @Test
    fun oneShotGateAndWalletEventsSuppressDuplicateCallbacks() = runBlocking {
        val gate = OneShotGate()
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())

        val id = "rewarded-ad:${UUID.randomUUID()}"
        val event = WalletRefreshEvent(
            eventId = id,
            source = WalletRefreshSource.REWARDED_AD,
            authoritativeBalance = 42.0
        )

        assertTrue(WalletRefreshEvents.publish(event))
        assertFalse(WalletRefreshEvents.publish(event))
        assertEquals(event, WalletRefreshEvents.events.replayCache.single())
    }

    private fun verifier(
        fetch: suspend (String) -> PaymentStatusResponse?,
        wait: suspend (Long) -> Unit = {}
    ) = BoundedPaymentStatusVerifier(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        statusFetcher = fetch,
        wait = wait
    )
}
