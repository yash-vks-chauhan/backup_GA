package com.gridee.parking.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds
import com.gridee.parking.BuildConfig
import com.gridee.parking.config.RemoteConfigManager

object AdMobManager {
    private const val PRODUCTION_REWARDED_AD_UNIT_ID = "ca-app-pub-5268197817154713/4238043733"
    private const val DEBUG_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    val rewardedAdUnitId: String
        get() = if (BuildConfig.DEBUG) DEBUG_REWARDED_AD_UNIT_ID else PRODUCTION_REWARDED_AD_UNIT_ID

    @Volatile
    private var initializationStarted = false

    @Volatile
    private var initialized = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    fun initializeIfEnabled(context: Context, onInitialized: (() -> Unit)? = null): Boolean {
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isFeatureEnabled("adMob") ||
            !RemoteConfigManager.isFeatureEnabled("rewards")
        ) {
            return false
        }
        initialize(context, onInitialized)
        return true
    }

    private fun initialize(context: Context, onInitialized: (() -> Unit)?) {
        synchronized(this) {
            if (initialized) {
                onInitialized?.let { runOnMain(it) }
                return
            }
            onInitialized?.let { pendingCallbacks.add(it) }
            if (initializationStarted) return
            initializationStarted = true
        }

        MobileAds.initialize(context.applicationContext) {
            val callbacks = synchronized(this) {
                initialized = true
                pendingCallbacks.toList().also { pendingCallbacks.clear() }
            }
            callbacks.forEach { runOnMain(it) }
        }
    }

    private fun runOnMain(callback: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback()
        } else {
            mainHandler.post(callback)
        }
    }
}
