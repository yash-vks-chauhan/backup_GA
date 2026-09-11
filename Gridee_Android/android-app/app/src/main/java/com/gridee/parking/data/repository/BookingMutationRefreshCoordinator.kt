package com.gridee.parking.data.repository

import android.content.Context
import com.gridee.parking.GrideeApplication
import com.gridee.parking.notifications.BookingStatusEvents
import com.gridee.parking.notifications.ParkingSpotRefreshEvents
import com.gridee.parking.ui.wallet.WalletRefreshEvent
import com.gridee.parking.ui.wallet.WalletRefreshEvents
import com.gridee.parking.ui.wallet.WalletRefreshSource
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Performs the targeted reads after a successful booking mutation, once and in parallel.
 * Each repository provides endpoint-level single-flight protection, so another screen joining the
 * same refresh awaits it instead of issuing a duplicate request.
 */
object BookingMutationRefreshCoordinator {
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduledMutationIds = linkedSetOf<String>()
    private val scheduleLock = Any()

    fun refreshAfterSuccess(
        context: Context,
        parkingLotId: String?,
        bookingId: String?,
        statusHint: String,
        walletSource: WalletRefreshSource,
        invalidateHistory: Boolean = false,
        cachesAlreadyInvalidated: Boolean = false,
        mutationEventId: String? = null,
    ) {
        val normalizedBookingId = bookingId?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedEventId = mutationEventId?.trim()?.takeIf { it.isNotEmpty() }
        val deduplicationId = normalizedBookingId ?: normalizedEventId
        val mutationId = deduplicationId?.let { "${walletSource.name}:$it" }
        if (mutationId != null && !markScheduled(mutationId)) return

        val repositories = (context.applicationContext as? GrideeApplication)
            ?.repositories
            ?: GrideeApplication.instance.repositories
        val userId = AuthSession.getUserId(context.applicationContext)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val normalizedLotId = parkingLotId?.trim()?.takeIf { it.isNotEmpty() }

        if (!cachesAlreadyInvalidated) {
            // Remote/FCM mutations do not pass through this process's repository mutation method.
            // Advance every affected cache generation before loading so the refresh cannot join a
            // pre-mutation in-flight GET and mistakenly publish that old response as authoritative.
            userId?.let {
                BookingRepository.invalidateUserBookings(
                    userId = it,
                    lotId = normalizedLotId,
                    includeHistory = invalidateHistory,
                )
                WalletRepository.invalidateWallet(it)
            }
            normalizedLotId?.let(ParkingRepository::invalidateLotSpots)
        } else if (invalidateHistory) {
            userId?.let {
                BookingRepository.invalidateUserBookingHistory(it, normalizedLotId)
            }
        }
        val eventId = deduplicationId ?: "anonymous:${System.nanoTime()}"
        refreshScope.launch {
            performRefresh(
                repositories = repositories,
                parkingLotId = parkingLotId,
                bookingId = normalizedBookingId,
                eventId = eventId,
                statusHint = statusHint,
                walletSource = walletSource,
                forceRefresh = !cachesAlreadyInvalidated,
            )
        }
    }

    private suspend fun performRefresh(
        repositories: RepositoryContainer,
        parkingLotId: String?,
        bookingId: String?,
        eventId: String,
        statusHint: String,
        walletSource: WalletRefreshSource,
        forceRefresh: Boolean,
    ) = supervisorScope {
        val normalizedLotId = parkingLotId?.trim()?.takeIf { it.isNotEmpty() }
        val bookings = async {
            repositories.bookingRepository.getUserBookings(forceRefresh = forceRefresh)
        }
        val wallet = async {
            repositories.walletRepository.getWalletDetails(forceRefresh = forceRefresh)
        }
        val spots = normalizedLotId?.let { lotId ->
            async {
                repositories.parkingRepository.getParkingSpotsByLot(
                    lotId,
                    forceRefresh = forceRefresh,
                )
            }
        }

        val bookingsReady = bookings.await().isSuccess
        if (bookingsReady) {
            BookingStatusEvents.publish(
                bookingId = bookingId,
                statusHint = statusHint,
                cacheAlreadyRefreshed = true,
            )
        }

        wallet.await().getOrNull()?.let { details ->
            WalletRefreshEvents.publish(
                WalletRefreshEvent(
                    eventId = "booking:${walletSource.name}:$eventId",
                    source = walletSource,
                    authoritativeBalance = details.balance,
                    cacheAlreadyRefreshed = true,
                )
            )
        }

        spots?.await()?.takeIf { it.isSuccessful }?.let {
            ParkingSpotRefreshEvents.publish(
                parkingLotId = normalizedLotId,
                cacheAlreadyRefreshed = true,
            )
        }
    }

    private fun markScheduled(mutationId: String): Boolean = synchronized(scheduleLock) {
        if (!scheduledMutationIds.add(mutationId)) return@synchronized false
        while (scheduledMutationIds.size > MAX_RETAINED_MUTATIONS) {
            scheduledMutationIds.remove(scheduledMutationIds.first())
        }
        true
    }

    private const val MAX_RETAINED_MUTATIONS = 256
}
