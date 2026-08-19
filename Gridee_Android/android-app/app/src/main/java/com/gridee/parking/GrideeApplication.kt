package com.gridee.parking

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.cashfree.pg.api.CFPaymentGatewayService
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.notifications.NotificationChannels
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.utils.configureEdgeToEdge
import com.gridee.parking.utils.AdConsentManager
import com.gridee.parking.utils.AdMobManager
import com.gridee.parking.utils.AdRevenueAnalytics
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

        // Weakly-held reference to the current foreground activity, used to route to
        // login when the session dies. WeakReference so a backgrounded/finished
        // activity is never leaked by this static field.
        @Volatile
        private var currentActivityRef: java.lang.ref.WeakReference<Activity>? = null

        val currentActivity: Activity?
            get() = currentActivityRef?.get()

        internal fun setCurrentActivity(activity: Activity?) {
            currentActivityRef = activity?.let { java.lang.ref.WeakReference(it) }
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        ThemeManager.applySavedTheme(this)
        AppLocaleManager.applySavedLocale(this)
        RemoteConfigManager.loadCached(this)
        AdRevenueAnalytics.applyCollectionPreference(this)
        registerActivityLifecycleCallbacks(AppLifecycleCallbacks(this, applicationScope))
        NotificationChannels.ensureDefaultChannel(this)
        // Warms the Cashfree checkout so the first top-up does not pay the init cost. The SDK
        // also self-initializes via its content provider; this is belt and braces, and a
        // failure here must never take the app down at launch.
        runCatching { CFPaymentGatewayService.initialize(applicationContext) }
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
                    if (AuthSession.isAuthenticated(application) && RemoteConfigManager.areNotificationsEnabled()) {
                        NotificationTokenManager.registerCurrentToken(application)
                    }
                }
            }
        }

        override fun onActivityResumed(activity: Activity) {
            setCurrentActivity(activity)
            if (activity is MainContainerActivity) {
                AdConsentManager.gatherConsent(activity) { canRequestAds ->
                    if (activity.isFinishing || activity.isDestroyed) {
                        return@gatherConsent
                    }
                    activity.onAdsConsentResult(canRequestAds)
                    if (!canRequestAds) return@gatherConsent
                    // Meta and Unity require the foreground Activity when collecting bidding
                    // signals and loading interstitial demand.
                    AdMobManager.preloadInterstitial(activity)
                }
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (currentActivity === activity) setCurrentActivity(null)
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (currentActivity === activity) setCurrentActivity(null)
        }

        private fun configure(activity: Activity) {
            (activity as? ComponentActivity)?.configureEdgeToEdge()
        }
    }
}
