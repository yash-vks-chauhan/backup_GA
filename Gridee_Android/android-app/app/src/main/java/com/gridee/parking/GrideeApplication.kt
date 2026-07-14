package com.gridee.parking

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.razorpay.Checkout
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.notifications.NotificationChannels
import com.gridee.parking.ui.utils.configureEdgeToEdge
import com.gridee.parking.utils.AdMobManager
import com.gridee.parking.utils.AppLocaleManager
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.NotificationTokenManager
import com.gridee.parking.utils.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GrideeApplication : Application() {
    companion object {
        lateinit var instance: GrideeApplication
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemeManager.applySavedTheme(this)
        AppLocaleManager.applySavedLocale(this)
        RemoteConfigManager.loadCached(this)
        registerActivityLifecycleCallbacks(AppLifecycleCallbacks(this, applicationScope))
        NotificationChannels.ensureDefaultChannel(this)
        try {
            Checkout.preload(applicationContext)
            AdMobManager.initializeIfEnabled(this)
        } catch (_: Exception) {
        }
        if (AuthSession.isAuthenticated(this) && RemoteConfigManager.areNotificationsEnabled()) {
            NotificationTokenManager.registerCurrentToken(this)
        }
    }

    private class AppLifecycleCallbacks(
        private val application: GrideeApplication,
        private val applicationScope: CoroutineScope
    ) : ActivityLifecycleCallbacks {
        private var startedActivities = 0

        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            configure(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                configure(activity)
            }
        }

        override fun onActivityStarted(activity: Activity) {
            if (startedActivities++ == 0) {
                applicationScope.launch {
                    RemoteConfigManager.refreshIfStale(application)
                    AdMobManager.initializeIfEnabled(application)
                    if (AuthSession.isAuthenticated(application) && RemoteConfigManager.areNotificationsEnabled()) {
                        NotificationTokenManager.registerCurrentToken(application)
                    }
                }
            }
        }

        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        private fun configure(activity: Activity) {
            (activity as? ComponentActivity)?.configureEdgeToEdge()
        }
    }
}
