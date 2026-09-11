package com.gridee.parking.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Targeted signal for inventory changes caused by a completed booking/operator mutation. */
object ParkingSpotRefreshEvents {
    data class Event(
        val parkingLotId: String,
        val cacheAlreadyRefreshed: Boolean = false,
    )

    private val mutableEvents = MutableSharedFlow<Event>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events = mutableEvents.asSharedFlow()

    fun publish(parkingLotId: String?, cacheAlreadyRefreshed: Boolean = false): Boolean {
        val normalizedLotId = parkingLotId?.trim().orEmpty()
        if (normalizedLotId.isEmpty()) return false
        return mutableEvents.tryEmit(Event(normalizedLotId, cacheAlreadyRefreshed))
    }
}
