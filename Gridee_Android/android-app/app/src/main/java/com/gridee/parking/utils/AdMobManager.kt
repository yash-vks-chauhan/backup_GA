package com.gridee.parking.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.gridee.parking.BuildConfig
import com.gridee.parking.config.RemoteConfigManager
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object AdMobManager {
    private const val PRODUCTION_REWARDED_AD_UNIT_ID = "ca-app-pub-5268197817154713/4238043733"

    // Every booking-transition ad (cancel, check-in, check-out) silently did nothing because this
    // pointed at .../2998879603, which is a *Rewarded Interstitial* unit in the AdMob console, not
    // an interstitial one. Probing each unit against each format on a real device showed AdMob
    // rejecting every InterstitialAd.load() against it with code 3 "Ad unit doesn't match format",
    // so interstitialAd stayed null forever and each queued transition just expired.
    //
    // Dedicated production interstitial for booking check-in, check-out, and cancellation.
    private const val PRODUCTION_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5268197817154713/5383971968"

    private const val PRODUCTION_NATIVE_AD_UNIT_ID = "ca-app-pub-5268197817154713/5433471254"
    private const val PRODUCTION_BOOKING_QR_NATIVE_AD_UNIT_ID =
        "ca-app-pub-5268197817154713/6657112495"
    private const val DEBUG_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val DEBUG_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val DEBUG_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val DEBUG_BOOKING_QR_NATIVE_AD_UNIT_ID = DEBUG_NATIVE_AD_UNIT_ID
    private const val INTERSTITIAL_MAX_AGE_MS = 55L * 60L * 1000L
    // A gate interaction cannot appear frozen while demand is unavailable. Interstitials are
    // preloaded, so this window is only a bounded fallback for a cold or failed load.
    private const val PENDING_INTERSTITIAL_MAX_WAIT_MS = 5_000L
    private const val INTERSTITIAL_RETRY_DELAY_MS = 1_000L
    private const val TAG = "AdMobManager"

    val rewardedAdUnitId: String
        get() = if (BuildConfig.DEBUG) DEBUG_REWARDED_AD_UNIT_ID else PRODUCTION_REWARDED_AD_UNIT_ID

    val interstitialAdUnitId: String
        get() = if (BuildConfig.DEBUG) DEBUG_INTERSTITIAL_AD_UNIT_ID else PRODUCTION_INTERSTITIAL_AD_UNIT_ID

    val nativeAdUnitId: String
        get() = if (BuildConfig.DEBUG) DEBUG_NATIVE_AD_UNIT_ID else PRODUCTION_NATIVE_AD_UNIT_ID

    /** QR-pass native slot. Debug uses Google's standard Native Advanced test creative. */
    val bookingQrNativeAdUnitId: String
        get() = if (BuildConfig.DEBUG) {
            DEBUG_BOOKING_QR_NATIVE_AD_UNIT_ID
        } else {
            PRODUCTION_BOOKING_QR_NATIVE_AD_UNIT_ID
        }

    @Volatile
    private var initializationStarted = false

    @Volatile
    private var initialized = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoadedAtMs = 0L
    private var interstitialLoadInProgress = false
    private var interstitialShowing = false
    private var pendingBookingInterstitial: PendingBookingInterstitial? = null
    private val handledBookingTransitions = linkedSetOf<String>()

    enum class BookingInterstitialOutcome {
        DISMISSED,
        FAILED_TO_SHOW,
        UNAVAILABLE,
        ALREADY_HANDLED
    }

    private data class PendingBookingInterstitial(
        val activityRef: WeakReference<Activity>,
        val eventKey: String,
        val targetStatus: String,
        val queuedAtMs: Long,
        val callbacks: MutableList<(BookingInterstitialOutcome) -> Unit>
    )

    /** Rewarded ads require both the global AdMob switch and the rewards switch. */
    fun initializeIfEnabled(context: Context, onInitialized: (() -> Unit)? = null): Boolean {
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isFeatureEnabled("adMob") ||
            !RemoteConfigManager.isFeatureEnabled("rewards") ||
            !AdConsentManager.canRequestAds(context)
        ) {
            return false
        }
        initialize(context, onInitialized)
        return true
    }

    /** Native and interstitial placements only depend on the global AdMob switch. */
    fun initializeAdsIfEnabled(context: Context, onInitialized: (() -> Unit)? = null): Boolean {
        RemoteConfigManager.loadCached(context)
        if (!RemoteConfigManager.isFeatureEnabled("adMob") ||
            !AdConsentManager.canRequestAds(context)
        ) {
            return false
        }
        initialize(context, onInitialized)
        return true
    }

    /**
     * Keeps one interstitial warm for the next completed booking transition. Loading is best
     * effort: a missing ad never delays check-in, check-out, or navigation.
     */
    fun preloadInterstitial(activity: Activity) {
        runOnMain {
            if (!isActivityUsable(activity)) return@runOnMain
            initializeAdsIfEnabled(activity) {
                if (!isActivityUsable(activity)) return@initializeAdsIfEnabled
                if (!tryShowPendingBookingInterstitial()) {
                    loadInterstitialIfNeeded(activity)
                }
            }
        }
    }

    /** True once an interstitial has actually been presented for this transition. */
    fun hasShownBookingTransition(bookingId: String, targetStatus: String): Boolean {
        val eventKey = eventKeyOf(bookingId, targetStatus)
        if (eventKey == null) return true
        return synchronized(handledBookingTransitions) { handledBookingTransitions.contains(eventKey) }
    }

    /**
     * Shows at most one interstitial for a specific booking/status transition in this process.
     *
     * The transition is only consumed once an ad is genuinely on screen. Consuming it up front
     * meant a check-in that arrived while AdMob was still initialising — or on the weak signal of
     * an underground garage — was marked as handled and could never be retried, so no ad was ever
     * shown for it. Callers are expected to retry until [hasShownBookingTransition] is true.
     */
    fun showBookingTransitionInterstitial(
        activity: Activity,
        bookingId: String,
        targetStatus: String,
        onFinished: (BookingInterstitialOutcome) -> Unit = {}
    ) {
        val eventKey = eventKeyOf(bookingId, targetStatus)
        if (eventKey == null) {
            runOnMain { onFinished(BookingInterstitialOutcome.UNAVAILABLE) }
            return
        }
        if (hasShownBookingTransition(bookingId, targetStatus)) {
            runOnMain { onFinished(BookingInterstitialOutcome.ALREADY_HANDLED) }
            return
        }

        runOnMain {
            if (!isActivityUsable(activity)) {
                onFinished(BookingInterstitialOutcome.UNAVAILABLE)
                return@runOnMain
            }

            val normalizedTargetStatus = targetStatus.trim().uppercase()
            val currentPending = pendingBookingInterstitial
            val shouldReplacePending = currentPending == null ||
                currentPending.eventKey == eventKey ||
                normalizedTargetStatus == "CANCELLED" ||
                currentPending.targetStatus != "CANCELLED"

            if (!shouldReplacePending) {
                onFinished(BookingInterstitialOutcome.UNAVAILABLE)
                return@runOnMain
            }

            if (currentPending != null && currentPending.eventKey != eventKey) {
                completePendingBookingInterstitial(
                    currentPending.eventKey,
                    BookingInterstitialOutcome.UNAVAILABLE
                )
            }

            val samePending = pendingBookingInterstitial?.takeIf { it.eventKey == eventKey }
            if (samePending != null) {
                samePending.callbacks.add(onFinished)
                pendingBookingInterstitial = samePending.copy(activityRef = WeakReference(activity))
            } else {
                pendingBookingInterstitial = PendingBookingInterstitial(
                    activityRef = WeakReference(activity),
                    eventKey = eventKey,
                    targetStatus = normalizedTargetStatus,
                    queuedAtMs = System.currentTimeMillis(),
                    callbacks = mutableListOf(onFinished)
                )
                schedulePendingInterstitialExpiry(eventKey)
            }

            // If initialization or consent is still settling, keep the transition queued.
            // The normal activity-resume preload will service it as soon as ads are allowed.
            val started = initializeAdsIfEnabled(activity) {
                tryShowPendingBookingInterstitial()
            }
            if (!started) {
                Log.d(TAG, "queued $eventKey but ads are not permitted yet (flag or consent)")
                completePendingBookingInterstitial(
                    eventKey,
                    BookingInterstitialOutcome.UNAVAILABLE
                )
            }
        }
    }

    /**
     * Shows a queued booking interstitial, or starts loading it while retaining the request.
     * This closes the old race where a cancellation was consumed before its ad finished loading.
     */
    private fun tryShowPendingBookingInterstitial(): Boolean {
        val pending = pendingBookingInterstitial ?: return false
        val activity = pending.activityRef.get()
        val now = System.currentTimeMillis()

        if (now - pending.queuedAtMs > PENDING_INTERSTITIAL_MAX_WAIT_MS ||
            activity == null ||
            !isActivityUsable(activity)
        ) {
            Log.d(
                TAG,
                "dropping ${pending.eventKey}: " +
                    if (activity == null) "host activity gone" else "no ad within ${PENDING_INTERSTITIAL_MAX_WAIT_MS}ms"
            )
            completePendingBookingInterstitial(
                pending.eventKey,
                BookingInterstitialOutcome.UNAVAILABLE
            )
            return false
        }

        if (interstitialShowing) return true
        if (activity is LifecycleOwner &&
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            Log.d(TAG, "waiting to show ${pending.eventKey}: host activity is not resumed")
            return true
        }
        if (!RemoteConfigManager.isFeatureEnabled("adMob")) {
            Log.d(TAG, "skipping ${pending.eventKey}: adMob feature flag is off")
            completePendingBookingInterstitial(
                pending.eventKey,
                BookingInterstitialOutcome.UNAVAILABLE
            )
            return false
        }
        if (!AdConsentManager.canRequestAds(activity.applicationContext)) {
            Log.d(TAG, "skipping ${pending.eventKey}: UMP consent has not authorised ad requests")
            completePendingBookingInterstitial(
                pending.eventKey,
                BookingInterstitialOutcome.UNAVAILABLE
            )
            return false
        }

        if (interstitialAd != null && now - interstitialLoadedAtMs > INTERSTITIAL_MAX_AGE_MS) {
            interstitialAd = null
            interstitialLoadedAtMs = 0L
        }

        val ad = interstitialAd
        if (ad == null) {
            loadInterstitialIfNeeded(activity)
            return true
        }

        pendingBookingInterstitial = null
        markBookingTransitionHandled(pending.eventKey)
        Log.d(TAG, "showing interstitial for ${pending.eventKey}")
        showLoadedInterstitial(activity, ad, pending.callbacks.toList())
        return true
    }

    private fun completePendingBookingInterstitial(
        eventKey: String,
        outcome: BookingInterstitialOutcome
    ) {
        val pending = pendingBookingInterstitial?.takeIf { it.eventKey == eventKey } ?: return
        pendingBookingInterstitial = null
        pending.callbacks.toList().forEach { callback ->
            runCatching { callback(outcome) }
                .onFailure { Log.w(TAG, "Booking interstitial callback failed", it) }
        }
    }

    private fun eventKeyOf(bookingId: String, targetStatus: String): String? {
        val id = bookingId.trim()
        if (id.isEmpty()) return null
        return "$id:${targetStatus.trim().uppercase()}"
    }

    private fun markBookingTransitionHandled(eventKey: String) {
        synchronized(handledBookingTransitions) {
            handledBookingTransitions.add(eventKey)
            while (handledBookingTransitions.size > MAX_TRACKED_BOOKING_TRANSITIONS) {
                val oldest = handledBookingTransitions.firstOrNull() ?: break
                handledBookingTransitions.remove(oldest)
            }
        }
    }

    private fun showLoadedInterstitial(
        activity: Activity,
        ad: InterstitialAd,
        callbacks: List<(BookingInterstitialOutcome) -> Unit>
    ) {
        // A loaded interstitial is single-use. Clear it before show() so a re-entrant callback
        // cannot present the same instance twice.
        interstitialAd = null
        interstitialLoadedAtMs = 0L
        interstitialShowing = true
        val completed = AtomicBoolean(false)
        fun finish(outcome: BookingInterstitialOutcome) {
            if (!completed.compareAndSet(false, true)) return
            callbacks.forEach { callback ->
                runCatching { callback(outcome) }
                    .onFailure { Log.w(TAG, "Booking interstitial callback failed", it) }
            }
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialShowing = false
                finish(BookingInterstitialOutcome.DISMISSED)
                if (isActivityUsable(activity)) preloadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                interstitialShowing = false
                Log.w(TAG, "Interstitial failed to show: ${adError.code} ${adError.message}")
                finish(BookingInterstitialOutcome.FAILED_TO_SHOW)
                if (isActivityUsable(activity)) preloadInterstitial(activity)
            }
        }

        runCatching { ad.show(activity) }
            .onFailure { error ->
                interstitialShowing = false
                Log.w(TAG, "Interstitial show threw an exception", error)
                finish(BookingInterstitialOutcome.FAILED_TO_SHOW)
                if (isActivityUsable(activity)) preloadInterstitial(activity)
            }
    }

    /**
     * Meta Audience Network and Unity Ads both require an Activity context for interstitial
     * bidding signal collection and loading. Never reduce this to applicationContext.
     */
    private fun loadInterstitialIfNeeded(activity: Activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                if (isActivityUsable(activity)) loadInterstitialIfNeeded(activity)
            }
            return
        }
        if (!isActivityUsable(activity)) return

        val now = System.currentTimeMillis()
        if (interstitialAd != null && now - interstitialLoadedAtMs <= INTERSTITIAL_MAX_AGE_MS) return
        if (interstitialLoadInProgress) return

        interstitialAd = null
        interstitialLoadedAtMs = 0L
        interstitialLoadInProgress = true
        InterstitialAd.load(
            activity,
            interstitialAdUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoadInProgress = false
                    interstitialAd = ad
                    interstitialLoadedAtMs = System.currentTimeMillis()
                    val source = ad.responseInfo
                        .loadedAdapterResponseInfo
                        ?.adSourceName
                        ?.takeIf { it.isNotBlank() }
                        ?: "unknown source"
                    Log.i(TAG, "Booking interstitial loaded from $source")
                    tryShowPendingBookingInterstitial()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialLoadInProgress = false
                    interstitialAd = null
                    interstitialLoadedAtMs = 0L
                    Log.w(
                        TAG,
                        "Interstitial failed to load: ${adError.code} ${adError.message}; " +
                            "responseInfo=${adError.responseInfo}"
                    )
                    scheduleInterstitialLoadRetry()
                }
            }
        )
    }

    /**
     * Retries the load while a transition is still waiting on it. A single failed request used to
     * end the attempt outright — one flaky response, or the weak signal of the underground garage
     * the user is checking out of, and the transition expired without ever showing an ad.
     */
    private fun scheduleInterstitialLoadRetry() {
        val pending = pendingBookingInterstitial ?: return
        val elapsed = System.currentTimeMillis() - pending.queuedAtMs
        if (elapsed + INTERSTITIAL_RETRY_DELAY_MS >= PENDING_INTERSTITIAL_MAX_WAIT_MS) return

        mainHandler.postDelayed({
            // Only still relevant if the same transition is waiting; anything else has either
            // been shown or replaced by a newer one.
            if (pendingBookingInterstitial?.eventKey == pending.eventKey) {
                pending.activityRef.get()
                    ?.takeIf(::isActivityUsable)
                    ?.let(::loadInterstitialIfNeeded)
            }
        }, INTERSTITIAL_RETRY_DELAY_MS)
    }

    private fun schedulePendingInterstitialExpiry(eventKey: String) {
        mainHandler.postDelayed({
            val pending = pendingBookingInterstitial ?: return@postDelayed
            if (pending.eventKey == eventKey &&
                System.currentTimeMillis() - pending.queuedAtMs >= PENDING_INTERSTITIAL_MAX_WAIT_MS
            ) {
                Log.d(TAG, "Pending booking interstitial expired before an ad became available")
                completePendingBookingInterstitial(
                    eventKey,
                    BookingInterstitialOutcome.UNAVAILABLE
                )
            }
        }, PENDING_INTERSTITIAL_MAX_WAIT_MS)
    }

    private fun isActivityUsable(activity: Activity): Boolean {
        return !activity.isFinishing && !activity.isDestroyed
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

        applyDebugRequestConfiguration(context)

        MobileAds.initialize(context.applicationContext) { status ->
            // Which mediation networks came up, and which are configured but absent.
            AdMediationStatus.log(status)
            val callbacks = synchronized(this) {
                initialized = true
                pendingCallbacks.toList().also { pendingCallbacks.clear() }
            }
            callbacks.forEach { runOnMain(it) }
        }
    }

    /**
     * Marks debug builds as test traffic.
     *
     * Debug builds already request Google's sample unit ids, but mediated networks serve against
     * the *real* units in a mediation waterfall, so the device also has to be registered as a
     * test device or those requests count as live impressions during QA. Release builds are left
     * untouched.
     */
    private fun applyDebugRequestConfiguration(context: Context) {
        if (!BuildConfig.DEBUG) return

        val testDeviceIds = TEST_DEVICE_IDS.filter { it.isNotBlank() }
        val configuration = MobileAds.getRequestConfiguration().toBuilder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)

        if (testDeviceIds.isEmpty()) {
            // The SDK prints the device's hashed id on the first ad request; add it below so
            // mediated networks treat this device as test traffic too.
            Log.i(TAG, "No test device ids registered — check logcat for \"Use RequestConfiguration\"")
        } else {
            Log.i(TAG, "Registered ${testDeviceIds.size} AdMob test device(s)")
        }
    }

    private fun runOnMain(callback: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback()
        } else {
            mainHandler.post(callback)
        }
    }

    private const val MAX_TRACKED_BOOKING_TRANSITIONS = 100

    /**
     * AdMob test device ids for QA. Add the hashed id logcat prints on the first ad request
     * ("Use RequestConfiguration.Builder().setTestDeviceIds(...)"). Debug builds only, so a
     * stale entry can never affect production traffic.
     */
    private val TEST_DEVICE_IDS = emptyList<String>()
}
