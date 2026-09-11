package com.gridee.parking.ui.wallet

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/** A precise reason for invalidating/updating the wallet cache. */
enum class WalletRefreshSource {
    PAYMENT,
    REWARDED_AD,
    BOOKING_CREATE,
    BOOKING_CANCEL,
    CHECK_IN,
    CHECK_OUT
}

data class WalletRefreshEvent(
    /** Stable across duplicate callbacks for the same payment/reward. */
    val eventId: String,
    val source: WalletRefreshSource,
    /** When supplied by the mutation response, consumers may update cache without another GET. */
    val authoritativeBalance: Double? = null,
    /** True only when the publisher already wrote/refreshed the shared repository cache. */
    val cacheAlreadyRefreshed: Boolean = false
)

/**
 * Process-local bridge between money mutations and the wallet UI/repository.
 *
 * The most recent event is replayed because a successful payment can navigate to the wallet
 * before its collector starts. Consumers should also remember [WalletRefreshEvent.eventId]
 * across view recreation. This publisher itself suppresses duplicate callback emissions.
 */
object WalletRefreshEvents {
    private val lock = Any()
    private val publishedIds = linkedSetOf<String>()
    private val _events = MutableSharedFlow<WalletRefreshEvent>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<WalletRefreshEvent> = _events.asSharedFlow()

    fun publish(event: WalletRefreshEvent): Boolean {
        val normalizedId = event.eventId.trim()
        if (normalizedId.isEmpty()) return false

        synchronized(lock) {
            if (!publishedIds.add(normalizedId)) return false
            while (publishedIds.size > MAX_RETAINED_EVENT_IDS) {
                publishedIds.remove(publishedIds.first())
            }
        }
        return _events.tryEmit(event.copy(eventId = normalizedId))
    }

    private const val MAX_RETAINED_EVENT_IDS = 128
}

/** Small atomic primitive used where third-party SDKs may invoke a callback more than once. */
internal class OneShotGate {
    private val consumed = AtomicBoolean(false)
    fun tryAcquire(): Boolean = consumed.compareAndSet(false, true)
}
