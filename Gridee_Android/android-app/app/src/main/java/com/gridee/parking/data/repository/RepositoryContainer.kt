package com.gridee.parking.data.repository

import android.content.Context

/** Application-scoped repository instances for ViewModels and screens that support injection. */
class RepositoryContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val parkingRepository: ParkingRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ParkingRepository(applicationContext)
    }
    val walletRepository: WalletRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WalletRepository(applicationContext)
    }
    val bookingRepository: BookingRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BookingRepository(applicationContext)
    }
    val remoteConfigRepository: RemoteConfigRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RemoteConfigRepository()
    }

    /** Call on logout/account replacement. Keys already isolate users; this also releases memory. */
    fun clearSessionCaches() {
        ParkingRepository.clearReadCache()
        WalletRepository.clearReadCache()
        BookingRepository.clearReadCache()
    }
}
