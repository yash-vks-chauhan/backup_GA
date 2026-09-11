package com.gridee.parking.ui.wallet

import android.content.Context
import com.gridee.parking.data.repository.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs one legacy rewarded-wallet mutation for a locally stable reward event id.
 *
 * The application scope lets an accepted AdMob reward finish if the sheet view is recreated.
 * The completed result is retained, so neither reward-sheet implementation can submit the same
 * event again. There is intentionally no retry: the current backend contract has no idempotency
 * token that would make retrying a lost response safe.
 */
internal object RewardCreditCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val submissions = linkedMapOf<String, Deferred<Result<Double?>>>()

    suspend fun credit(
        context: Context,
        rewardEventId: String,
        amount: Double
    ): Result<Double?> {
        val eventId = rewardEventId.trim()
        if (eventId.isEmpty()) return Result.failure(IllegalArgumentException("Missing reward event"))

        val submission = lock.withLock {
            submissions[eventId] ?: scope.async(start = CoroutineStart.LAZY) {
                submitOnce(context.applicationContext, eventId, amount)
            }.also { created ->
                submissions[eventId] = created
                trimCompletedSubmissions()
                created.start()
            }
        }
        return submission.await()
    }

    private suspend fun submitOnce(
        context: Context,
        eventId: String,
        amount: Double
    ): Result<Double?> {
        val result = try {
            WalletRepository(context).topUpWallet(amount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }

        return result.map { payload ->
            (payload["balance"] as? Number)?.toDouble()
        }.onSuccess { newBalance ->
            WalletRefreshEvents.publish(
                WalletRefreshEvent(
                    eventId = eventId,
                    source = WalletRefreshSource.REWARDED_AD,
                    authoritativeBalance = newBalance
                )
            )
        }
    }

    private fun trimCompletedSubmissions() {
        if (submissions.size <= MAX_RETAINED_REWARDS) return
        val iterator = submissions.entries.iterator()
        while (submissions.size > MAX_RETAINED_REWARDS && iterator.hasNext()) {
            if (iterator.next().value.isCompleted) iterator.remove()
        }
    }

    private const val MAX_RETAINED_REWARDS = 64
}
