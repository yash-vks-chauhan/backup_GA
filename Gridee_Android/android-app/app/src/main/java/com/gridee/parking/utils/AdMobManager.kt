package com.gridee.parking.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAdPreloader
import com.google.android.gms.ads.preload.PreloadCallbackV2
import com.google.android.gms.ads.preload.PreloadConfiguration
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

    // The warm buffer for booking transitions. Two, because a session can legitimately owe two
    // ads back to back — a check-out on one booking and a cancellation on another — and the old
    // single-slot cache had nothing left for the second.
    private const val INTERSTITIAL_PRELOAD_ID = "booking_transition_interstitial"
    private const val INTERSTITIAL_PRELOAD_BUFFER_SIZE = 2

    // Backoff for re-arming an exhausted or failed buffer. The old code only ever retried while a
    // transition was already waiting, so a no-fill during the quiet period before a check-in left
    // the cache empty with nothing to refill it.
    private const val WARM_UP_RETRY_BASE_MS = 5_000L
    private const val WARM_UP_RETRY_MAX_MS = 5L * 60L * 1000L
    private const val WARM_UP_MAX_RETRIES = 6

    /** Which side of the warm-up an impression came from, so the two can be told apart. */
    private const val FILL_PATH_BUFFER = "preload_buffer"
    private const val FILL_PATH_JUST_IN_TIME = "just_in_time"

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

    // Warm-up state. Touched on the main thread only, like the rest of the interstitial state.
    private var warmUpRequested = false
    private var preloaderStarted = false
    private var warmUpRetryAttempt = 0
    private var warmUpRetryScheduled = false
    private var warmUpActivityRef: WeakReference<Activity>? = null

    /** Application context, kept for telemetry from paths that no longer hold an Activity. */
    @Volatile
    private var analyticsContext: Context? = null

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
        analyticsContext = context.applicationContext
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
        // Set before the gate, not after it: a transition dropped because the flag is off or
        // consent has not landed is exactly the discard reason worth counting.
        analyticsContext = context.applicationContext
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
     * Services a booking transition that was queued while the host activity was away.
     *
     * Deliberately does *not* request an ad. Requesting on every resume is what held this unit at
     * a low single-digit show rate: the interstitial only ever displays on a check-in, check-out
     * or cancellation, and most sessions contain none of those, so the great majority of those
     * requests loaded an ad that was never shown to anyone. Requests now come from
     * [warmUpBookingInterstitial], which only fires while the user actually holds a booking that
     * can still move.
     */
    fun notifyHostResumed(activity: Activity) {
        runOnMain {
            if (!isActivityUsable(activity)) return@runOnMain
            if (warmUpRequested) warmUpActivityRef = WeakReference(activity)
            // Nothing owed means nothing to do. The warm-up is driven by booking state, not by
            // the activity coming forward.
            if (pendingBookingInterstitial == null) return@runOnMain
            initializeAdsIfEnabled(activity) {
                if (!isActivityUsable(activity)) return@initializeAdsIfEnabled
                tryShowPendingBookingInterstitial()
            }
        }
    }

    /**
     * Keeps a booking-transition interstitial warm while the user holds a booking that can still
     * move — a pending booking waiting on its barrier, or an active one waiting on check-out.
     *
     * Idempotent, and safe to call from every render: the caller is the bookings screen, which
     * re-syncs this on every refresh, tab change and pass open. A missing ad never delays
     * check-in, check-out, cancellation or navigation.
     */
    fun warmUpBookingInterstitial(activity: Activity) {
        runOnMain {
            if (!isActivityUsable(activity)) return@runOnMain

            // Ahead of the steady-state early return below, so a remote kill actually stops the
            // requests rather than only stopping the show. Two map lookups, no disk read.
            if (!RemoteConfigManager.isBookingTransitionInterstitialEnabled()) {
                if (warmUpRequested || preloaderStarted) {
                    AppLog.d(TAG) {
                        "warm-up stopped: booking transition interstitial is disabled"
                    }
                }
                warmUpRequested = false
                warmUpActivityRef = null
                cancelWarmUpRetry()
                // A transition still owed is left to its own terminal path, which now refuses it
                // for the same reason and calls back so the booking UI is never stranded.
                if (pendingBookingInterstitial == null) destroyInterstitialPreloader()
                return@runOnMain
            }

            warmUpActivityRef = WeakReference(activity)

            // Steady-state early return, and it has to cover both modes. This runs on every
            // render sync — as often as every 1.5 seconds while the pass is open at the barrier —
            // and everything past it reaches initializeAdsIfEnabled, which reads and re-parses the
            // cached remote config off disk.
            val bufferEnabled = RemoteConfigManager.isBookingTransitionPreloadBufferEnabled()
            val alreadyWarm = if (bufferEnabled) {
                preloaderStarted
            } else {
                interstitialAd != null || interstitialLoadInProgress
            }
            if (warmUpRequested && alreadyWarm) return@runOnMain

            // Set before the gate, not after it. When the SDK is already initialised the
            // callback below runs inline, and a start failure inside it schedules its retry
            // against this flag.
            val wasRequested = warmUpRequested
            if (!wasRequested) {
                warmUpRequested = true
                warmUpRetryAttempt = 0
            }
            val started = initializeAdsIfEnabled(activity) {
                if (!isActivityUsable(activity)) return@initializeAdsIfEnabled
                if (bufferEnabled) {
                    startInterstitialPreloader(activity)
                } else {
                    // Buffer killed remotely: fall back to the single-slot just-in-time load that
                    // predated it. Still intent-gated, so this is the old proven path without the
                    // old per-resume waste.
                    destroyInterstitialPreloader()
                    loadInterstitialIfNeeded(activity)
                }
            }
            if (!started) {
                // Consent or the remote flag has not settled yet. Rolled back on purpose so the
                // next sync from the bookings screen tries again.
                warmUpRequested = wasRequested
                AppLog.d(TAG) { "warm-up skipped: ads are not permitted yet (flag or consent)" }
            }
        }
    }

    /**
     * Stops warming once nothing can transition. Without this the SDK keeps refilling the buffer
     * as ads expire, which would put the placement straight back into requesting ads it has no
     * occasion to show.
     */
    fun stopBookingInterstitialWarmUp() {
        runOnMain {
            if (!warmUpRequested && !preloaderStarted) return@runOnMain
            warmUpRequested = false
            warmUpRetryAttempt = 0
            warmUpActivityRef = null
            cancelWarmUpRetry()
            // A transition still owed keeps its buffer: tearing it down here would throw away the
            // very ad it is waiting on. The teardown is deferred to whichever path completes that
            // transition, so a screen destroyed mid-transition can never leave the SDK refilling
            // a buffer nothing is left to consume.
            if (pendingBookingInterstitial != null) return@runOnMain
            destroyInterstitialPreloader()
        }
    }

    /**
     * Holds the buffer but stops actively chasing it while the app is not in front.
     *
     * The buffer itself is deliberately kept. The single most common shape of this placement is
     * the operator checking a car in off its number plate while the phone is in the user's
     * pocket; dropping the warm ad on every pause would make that case a cold load every time,
     * against the weak signal of an underground garage.
     */
    fun pauseBookingInterstitialWarmUp() {
        runOnMain { cancelWarmUpRetry() }
    }

    private fun startInterstitialPreloader(activity: Activity) {
        if (!isActivityUsable(activity)) return
        if (!RemoteConfigManager.isBookingTransitionPreloadBufferEnabled()) return
        if (preloaderStarted) return

        val alreadyConfigured = runCatching {
            InterstitialAdPreloader.getConfiguration(INTERSTITIAL_PRELOAD_ID) != null
        }.getOrDefault(false)
        if (alreadyConfigured) {
            preloaderStarted = true
            return
        }

        // Single-argument builder on purpose: the format is implied by the preloader the
        // configuration is handed to, and the pair that takes an explicit AdFormat is deprecated.
        val configuration = PreloadConfiguration
            .Builder(interstitialAdUnitId)
            .setAdRequest(AdRequest.Builder().build())
            .setBufferSize(INTERSTITIAL_PRELOAD_BUFFER_SIZE)
            .build()

        val started = runCatching {
            InterstitialAdPreloader.start(INTERSTITIAL_PRELOAD_ID, configuration, preloadCallback)
        }.getOrElse { error ->
            AppLog.w(TAG) {
                "Interstitial preloader failed to start (${error.javaClass.simpleName})"
            }
            false
        }
        preloaderStarted = started
        if (started) {
            AppLog.d(TAG) {
                "Booking interstitial buffer opened (size=$INTERSTITIAL_PRELOAD_BUFFER_SIZE)"
            }
        } else {
            // Not fatal: the just-in-time load at show time is still the fallback it always was.
            AppLog.w(TAG) { "Booking interstitial buffer refused to open" }
            scheduleWarmUpRetry()
        }
    }

    private val preloadCallback = object : PreloadCallbackV2() {
        override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo?) {
            runOnMain {
                warmUpRetryAttempt = 0
                val source = responseInfo?.loadedAdapterResponseInfo
                    ?.adSourceName
                    ?.takeIf { it.isNotBlank() }
                AppLog.i(TAG) { "Booking interstitial preloaded" }
                analyticsContext?.let {
                    AdRevenueAnalytics.logBookingInterstitialPreloaded(
                        it,
                        source,
                        numPreloadedInterstitials()
                    )
                }
                // A transition queued while the buffer was empty is still waiting on this.
                tryShowPendingBookingInterstitial()
            }
        }

        override fun onAdFailedToPreload(preloadId: String, adError: AdError) {
            runOnMain {
                AppLog.w(TAG) {
                    "Booking interstitial failed to preload (code=${adError.code})"
                }
                analyticsContext?.let {
                    AdRevenueAnalytics.logBookingInterstitialLoad(it, false, null, adError.code)
                }
                scheduleWarmUpRetry()
            }
        }

        override fun onAdsExhausted(preloadId: String) {
            runOnMain {
                AppLog.d(TAG) { "Booking interstitial buffer exhausted" }
                // The SDK refills on its own; this only steps in if it has not by the time the
                // backoff elapses.
                scheduleWarmUpRetry()
            }
        }
    }

    private val warmUpRetryRunnable = Runnable {
        warmUpRetryScheduled = false
        if (!warmUpRequested) return@Runnable
        if (numPreloadedInterstitials() > 0) {
            warmUpRetryAttempt = 0
            return@Runnable
        }
        val activity = warmUpActivityRef?.get()?.takeIf(::isActivityUsable)
        if (activity == null) {
            AppLog.d(TAG) { "warm-up retry dropped: host activity is gone" }
            return@Runnable
        }
        // Force a fresh buffer. A preloader that has been exhausted or has failed its request does
        // not reliably come back on its own, and the whole point of the retry is that the quiet
        // period before a check-in is exactly when an empty buffer goes unnoticed.
        destroyInterstitialPreloader()
        initializeAdsIfEnabled(activity) {
            if (isActivityUsable(activity)) startInterstitialPreloader(activity)
        }
    }

    private fun scheduleWarmUpRetry() {
        if (!warmUpRequested) return
        if (warmUpRetryScheduled) return
        if (warmUpRetryAttempt >= WARM_UP_MAX_RETRIES) {
            AppLog.d(TAG) {
                "warm-up retries exhausted; the just-in-time load remains the fallback"
            }
            return
        }
        val delay = (WARM_UP_RETRY_BASE_MS shl warmUpRetryAttempt)
            .coerceAtMost(WARM_UP_RETRY_MAX_MS)
        warmUpRetryAttempt++
        warmUpRetryScheduled = true
        mainHandler.postDelayed(warmUpRetryRunnable, delay)
    }

    private fun cancelWarmUpRetry() {
        mainHandler.removeCallbacks(warmUpRetryRunnable)
        warmUpRetryScheduled = false
    }

    private fun destroyInterstitialPreloader() {
        if (!preloaderStarted) return
        preloaderStarted = false
        runCatching { InterstitialAdPreloader.destroy(INTERSTITIAL_PRELOAD_ID) }
            .onFailure { error ->
                AppLog.w(TAG) {
                    "Interstitial preloader failed to stop (${error.javaClass.simpleName})"
                }
            }
    }

    private fun pollPreloadedInterstitial(): InterstitialAd? {
        if (!preloaderStarted) return null
        if (!RemoteConfigManager.isBookingTransitionPreloadBufferEnabled()) return null
        return runCatching {
            if (InterstitialAdPreloader.isAdAvailable(INTERSTITIAL_PRELOAD_ID)) {
                InterstitialAdPreloader.pollAd(INTERSTITIAL_PRELOAD_ID)
            } else {
                null
            }
        }.getOrElse { error ->
            AppLog.w(TAG) {
                "Interstitial preloader poll failed (${error.javaClass.simpleName})"
            }
            null
        }
    }

    private fun numPreloadedInterstitials(): Int {
        if (!preloaderStarted) return 0
        return runCatching {
            InterstitialAdPreloader.getNumAdsAvailable(INTERSTITIAL_PRELOAD_ID)
        }.getOrDefault(0)
    }

    private fun logDiscard(reason: String, targetStatus: String) {
        val context = analyticsContext ?: return
        AdRevenueAnalytics.logBookingInterstitialDiscarded(context, reason, targetStatus)
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

        val normalizedTargetStatus = targetStatus.trim().uppercase()

        runOnMain {
            // Terminal callback first, always. A disabled placement must let the transition
            // through untouched, exactly as a no-fill does.
            if (!RemoteConfigManager.isBookingTransitionInterstitialEnabled()) {
                logDiscard("placement_disabled", normalizedTargetStatus)
                onFinished(BookingInterstitialOutcome.UNAVAILABLE)
                return@runOnMain
            }
            if (!isActivityUsable(activity)) {
                logDiscard("host_activity_gone", normalizedTargetStatus)
                onFinished(BookingInterstitialOutcome.UNAVAILABLE)
                return@runOnMain
            }

            val currentPending = pendingBookingInterstitial
            val shouldReplacePending = currentPending == null ||
                currentPending.eventKey == eventKey ||
                normalizedTargetStatus == "CANCELLED" ||
                currentPending.targetStatus != "CANCELLED"

            if (!shouldReplacePending) {
                logDiscard("queue_busy", normalizedTargetStatus)
                onFinished(BookingInterstitialOutcome.UNAVAILABLE)
                return@runOnMain
            }

            if (currentPending != null && currentPending.eventKey != eventKey) {
                logDiscard("superseded", currentPending.targetStatus)
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
            // [notifyHostResumed] services it as soon as the host comes forward, and the warm
            // buffer's own preload callback services it as soon as an ad lands.
            val started = initializeAdsIfEnabled(activity) {
                tryShowPendingBookingInterstitial()
            }
            if (!started) {
                logDiscard("ads_not_permitted", normalizedTargetStatus)
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
            val hostGone = activity == null || !isActivityUsable(activity)
            logDiscard(
                if (hostGone) "host_activity_gone" else "no_fill_within_window",
                pending.targetStatus
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
            return true
        }
        if (!RemoteConfigManager.isFeatureEnabled("adMob")) {
            logDiscard("flag_off", pending.targetStatus)
            completePendingBookingInterstitial(
                pending.eventKey,
                BookingInterstitialOutcome.UNAVAILABLE
            )
            return false
        }
        if (!RemoteConfigManager.isBookingTransitionInterstitialEnabled()) {
            logDiscard("placement_disabled", pending.targetStatus)
            completePendingBookingInterstitial(
                pending.eventKey,
                BookingInterstitialOutcome.UNAVAILABLE
            )
            return false
        }
        if (!AdConsentManager.canRequestAds(activity.applicationContext)) {
            logDiscard("consent_denied", pending.targetStatus)
            completePendingBookingInterstitial(
                pending.eventKey,
                BookingInterstitialOutcome.UNAVAILABLE
            )
            return false
        }

        if (interstitialAd != null && now - interstitialLoadedAtMs > INTERSTITIAL_MAX_AGE_MS) {
            logDiscard("cache_expired", pending.targetStatus)
            interstitialAd = null
            interstitialLoadedAtMs = 0L
        }

        // The warm buffer first. Those ads were requested against this booking's own window, so
        // the auction behind them is minutes old rather than most of an hour, and taking one
        // leaves the SDK to refill the slot for the next transition.
        val preloaded = pollPreloadedInterstitial()
        val ad: InterstitialAd
        val fillPath: String
        if (preloaded != null) {
            ad = preloaded
            fillPath = FILL_PATH_BUFFER
        } else {
            val cached = interstitialAd
            if (cached == null) {
                loadInterstitialIfNeeded(activity)
                return true
            }
            // A loaded interstitial is single-use. Clear it before show() so a re-entrant callback
            // cannot present the same instance twice.
            interstitialAd = null
            interstitialLoadedAtMs = 0L
            ad = cached
            fillPath = FILL_PATH_JUST_IN_TIME
        }

        pendingBookingInterstitial = null
        markBookingTransitionHandled(pending.eventKey)
        showLoadedInterstitial(
            activity,
            ad,
            fillPath,
            pending.targetStatus,
            pending.callbacks.toList()
        )
        return true
    }

    private fun completePendingBookingInterstitial(
        eventKey: String,
        outcome: BookingInterstitialOutcome
    ) {
        val pending = pendingBookingInterstitial?.takeIf { it.eventKey == eventKey } ?: return
        pendingBookingInterstitial = null
        // Picks up a teardown that [stopBookingInterstitialWarmUp] deferred because this
        // transition was still owed an ad.
        if (!warmUpRequested) destroyInterstitialPreloader()
        pending.callbacks.toList().forEach { callback ->
            runCatching { callback(outcome) }
                .onFailure { error ->
                    AppLog.w(TAG) {
                        "Booking interstitial callback failed (${error.javaClass.simpleName})"
                    }
                }
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
        fillPath: String,
        targetStatus: String,
        callbacks: List<(BookingInterstitialOutcome) -> Unit>
    ) {
        interstitialShowing = true
        val appContext = activity.applicationContext
        val adSourceName = ad.responseInfo
            .loadedAdapterResponseInfo
            ?.adSourceName
            ?.takeIf { it.isNotBlank() }

        // This placement had no impression-level revenue coverage at all, so the only figure it
        // ever produced was an aggregate in the AdMob console. Both natives report theirs.
        ad.setOnPaidEventListener { value ->
            AdRevenueAnalytics.logBookingInterstitialPaidEvent(
                appContext,
                value.valueMicros,
                value.currencyCode,
                value.precisionType,
                adSourceName,
                fillPath
            )
        }

        val completed = AtomicBoolean(false)
        fun finish(outcome: BookingInterstitialOutcome) {
            if (!completed.compareAndSet(false, true)) return
            callbacks.forEach { callback ->
                runCatching { callback(outcome) }
                    .onFailure { error ->
                        AppLog.w(TAG) {
                            "Booking interstitial callback failed (${error.javaClass.simpleName})"
                        }
                    }
            }
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdImpression() {
                AdRevenueAnalytics.logBookingInterstitialImpression(
                    appContext,
                    adSourceName,
                    fillPath,
                    targetStatus
                )
            }

            override fun onAdClicked() {
                AdRevenueAnalytics.logBookingInterstitialClick(appContext)
            }

            override fun onAdDismissedFullScreenContent() {
                interstitialShowing = false
                finish(BookingInterstitialOutcome.DISMISSED)
                rearmAfterShow(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialShowing = false
                AppLog.w(TAG) { "Interstitial failed to show (code=${adError.code})" }
                logDiscard("show_failed", targetStatus)
                finish(BookingInterstitialOutcome.FAILED_TO_SHOW)
                rearmAfterShow(activity)
            }
        }

        runCatching { ad.show(activity) }
            .onFailure { error ->
                interstitialShowing = false
                AppLog.w(TAG) {
                    "Interstitial show threw an exception (${error.javaClass.simpleName})"
                }
                logDiscard("show_threw", targetStatus)
                finish(BookingInterstitialOutcome.FAILED_TO_SHOW)
                rearmAfterShow(activity)
            }
    }

    /**
     * Re-opens the buffer after a show, but only while a booking can still move. The bookings
     * screen re-syncs the warm-up on the re-render that follows every transition, so a check-out
     * or cancellation stops warming here rather than requesting an ad for a session that has
     * nothing left to transition.
     */
    private fun rearmAfterShow(activity: Activity) {
        if (!warmUpRequested) {
            // Also the last of the deferred teardowns: showing is the one path that consumes a
            // pending transition without going through completePendingBookingInterstitial.
            destroyInterstitialPreloader()
            return
        }
        if (!isActivityUsable(activity)) return
        startInterstitialPreloader(activity)
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
                    AppLog.i(TAG) { "Booking interstitial loaded" }
                    analyticsContext?.let {
                        AdRevenueAnalytics.logBookingInterstitialLoad(it, true, source)
                    }
                    tryShowPendingBookingInterstitial()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialLoadInProgress = false
                    interstitialAd = null
                    interstitialLoadedAtMs = 0L
                    AppLog.w(TAG) { "Interstitial failed to load (code=${adError.code})" }
                    analyticsContext?.let {
                        AdRevenueAnalytics.logBookingInterstitialLoad(it, false, null, adError.code)
                    }
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
                AppLog.d(TAG) {
                    "Pending booking interstitial expired before an ad became available"
                }
                logDiscard("no_fill_within_window", pending.targetStatus)
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
            AppLog.i(TAG) {
                "No test device ids registered — check logcat for \"Use RequestConfiguration\""
            }
        } else {
            AppLog.i(TAG) { "Registered ${testDeviceIds.size} AdMob test device(s)" }
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
    private val TEST_DEVICE_IDS = listOf(
        // SM-A546E (Galaxy A54), yash — read off logcat's own
        // "Use RequestConfiguration.Builder().setTestDeviceIds(...)" line.
        "7B1FA2C3FBFDA5C839094B93308FA8CB"
    )
}
