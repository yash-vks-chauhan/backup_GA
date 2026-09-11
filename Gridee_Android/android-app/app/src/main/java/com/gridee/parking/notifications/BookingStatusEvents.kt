package com.gridee.parking.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Foreground-only signal that asks visible booking UI to reconcile with the backend.
 *
 * The payload is deliberately only a hint. The backend remains the source of truth, and the
 * booking screen always refreshes before applying a status transition.
 */
object BookingStatusEvents {
    data class Event(
        val bookingId: String?,
        val statusHint: String,
        val cacheAlreadyRefreshed: Boolean = false,
    )

    private val mutableEvents = MutableSharedFlow<Event>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events = mutableEvents.asSharedFlow()

    fun publish(
        bookingId: String?,
        statusHint: String,
        cacheAlreadyRefreshed: Boolean = false,
    ) {
        mutableEvents.tryEmit(
            Event(
                bookingId = bookingId?.trim()?.takeIf { it.isNotEmpty() },
                statusHint = statusHint.trim(),
                cacheAlreadyRefreshed = cacheAlreadyRefreshed,
            )
        )
    }
}
