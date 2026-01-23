package com.gridee.parking

import android.app.Application
import com.razorpay.Checkout
import com.google.android.gms.ads.MobileAds
import com.gridee.parking.notifications.NotificationChannels
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.NotificationTokenManager

class GrideeApplication : Application() {
    companion object {
        lateinit var instance: GrideeApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationChannels.ensureDefaultChannel(this)
        try {
            Checkout.preload(applicationContext)
            // Initialize Google Mobile Ads SDK (AdMob)
            MobileAds.initialize(this) { }
        } catch (_: Exception) {
        }
        if (AuthSession.isAuthenticated(this)) {
            NotificationTokenManager.registerCurrentToken(this)
        }
    }
}
