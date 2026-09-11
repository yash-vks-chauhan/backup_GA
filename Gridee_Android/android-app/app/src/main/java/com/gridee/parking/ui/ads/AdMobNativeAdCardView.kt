package com.gridee.parking.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.gridee.parking.R
import com.gridee.parking.databinding.ViewAdmobNativeCardBinding
import com.gridee.parking.utils.AdMobManager

/**
 * A lifecycle-safe native AdMob card. It stays collapsed until every SDK asset is bound, so a
 * slow or failed request never leaves an empty box on Home.
 */
class AdMobNativeAdCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class LoadEvent(
        val loaded: Boolean,
        val adSourceName: String? = null,
        val errorCode: Int? = null,
        val hasVideoContent: Boolean = false,
        val reason: Unavailable? = null
    )

    /**
     * Why the slot has nothing. Previously every one of these collapsed into a bare
     * onAdUnavailable(), so a no-fill, a throttled request and a wedged load were
     * indistinguishable in both the UI and the dashboards.
     */
    enum class Unavailable { ADS_DISABLED, NO_ACTIVITY, THROTTLED, LOAD_FAILED, TIMEOUT }

    data class PaidEvent(
        val valueMicros: Long,
        val currencyCode: String,
        val precisionType: Int,
        val adSourceName: String?
    )

    private val binding = ViewAdmobNativeCardBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    var onAdLoaded: (() -> Unit)? = null
    var onAdUnavailable: ((Unavailable) -> Unit)? = null
    var onLoadEvent: ((LoadEvent) -> Unit)? = null
    var onAdImpression: (() -> Unit)? = null
    var onAdClicked: (() -> Unit)? = null
    var onPaidEvent: ((PaidEvent) -> Unit)? = null
    var onVideoEvent: ((action: String, muted: Boolean?) -> Unit)? = null

    private var nativeAd: NativeAd? = null
    private var adLoader: AdLoader? = null
    private var requestGeneration = 0
    private var loadInProgress = false

    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0

    /**
     * The SDK does not guarantee a callback. Without this a request that never returns leaves
     * loadInProgress pinned true, and the slot stays empty for the rest of the visit with
     * nothing scheduled to rescue it.
     */
    private val loadWatchdog = Runnable {
        if (!loadInProgress) return@Runnable
        loadInProgress = false
        adLoader = null
        onLoadEvent?.invoke(LoadEvent(loaded = false, reason = Unavailable.TIMEOUT))
        reportUnavailable(Unavailable.TIMEOUT)
        scheduleRetry()
    }

    val hasAd: Boolean
        get() = nativeAd != null

    init {
        isVisible = false
    }

    fun load() {
        if (nativeAd != null) {
            isVisible = true
            return
        }
        if (loadInProgress) return

        val now = SystemClock.elapsedRealtime()
        if (lastRequestStartedAtElapsedMs != 0L &&
            now - lastRequestStartedAtElapsedMs < MIN_AD_REQUEST_INTERVAL_MS
        ) {
            // Throttled rather than failed. Come back when the window opens instead of
            // giving the slot up for the rest of the visit, which is what used to happen
            // after any Activity recreate inside the throttle window.
            val waitMs = MIN_AD_REQUEST_INTERVAL_MS - (now - lastRequestStartedAtElapsedMs)
            reportUnavailable(Unavailable.THROTTLED)
            retryHandler.removeCallbacks(retryRunnable)
            retryHandler.postDelayed(retryRunnable, waitMs + RETRY_JITTER_MS)
            return
        }

        beginLoad()
    }

    private val retryRunnable = Runnable {
        if (isAttachedToWindow && nativeAd == null) load()
    }

    private fun beginLoad() {
        loadInProgress = true
        retryHandler.removeCallbacks(loadWatchdog)
        retryHandler.postDelayed(loadWatchdog, LOAD_TIMEOUT_MS)

        val generation = ++requestGeneration
        val initialized = AdMobManager.initializeAdsIfEnabled(context) {
            if (generation != requestGeneration || !isAttachedToWindow) {
                loadInProgress = false
                retryHandler.removeCallbacks(loadWatchdog)
                return@initializeAdsIfEnabled
            }
            requestAd(generation)
        }

        if (!initialized) {
            loadInProgress = false
            retryHandler.removeCallbacks(loadWatchdog)
            reportUnavailable(Unavailable.ADS_DISABLED)
        }
    }

    /**
     * Hides the slot and reports why — except while a replacement is in flight, where the
     * creative already on screen stays put and only the reason is reported.
     */
    private fun reportUnavailable(reason: Unavailable) {
        if (nativeAd == null) isVisible = false
        onAdUnavailable?.invoke(reason)
    }

    /** Bounded backoff. Transient no-fill and network blips should not cost the whole visit. */
    private fun scheduleRetry() {
        if (nativeAd != null) return
        if (retryAttempt >= RETRY_DELAYS_MS.size) return
        val delay = RETRY_DELAYS_MS[retryAttempt]
        retryAttempt++
        retryHandler.removeCallbacks(retryRunnable)
        retryHandler.postDelayed(retryRunnable, delay)
    }

    /**
     * Meta's adapter needs a real Activity to collect bidding signals and fails the request
     * outright when handed an application context. Resolved per request and never stored, so
     * the card cannot outlive the Activity it borrowed.
     */
    private fun resolveActivity(): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx.takeIf { !it.isFinishing && !it.isDestroyed }
            }
            ctx = ctx.baseContext
        }
        return null
    }

    private fun requestAd(generation: Int) {
        val activity = resolveActivity()
        if (activity == null) {
            loadInProgress = false
            retryHandler.removeCallbacks(loadWatchdog)
            onLoadEvent?.invoke(LoadEvent(loaded = false, reason = Unavailable.NO_ACTIVITY))
            reportUnavailable(Unavailable.NO_ACTIVITY)
            // Recreates and configuration changes hand us a finishing Activity for a beat.
            // Retrying rides that out instead of surrendering the slot.
            scheduleRetry()
            return
        }

        lastRequestStartedAtElapsedMs = SystemClock.elapsedRealtime()
        adLoader = AdLoader.Builder(activity, AdMobManager.nativeAdUnitId)
            .forNativeAd { ad ->
                if (generation != requestGeneration || !isAttachedToWindow) {
                    ad.destroy()
                    return@forNativeAd
                }
                loadInProgress = false
                retryHandler.removeCallbacks(loadWatchdog)
                retryAttempt = 0

                // Swap under the user: the outgoing creative is destroyed only once the new
                // one is bound, so the slot never blanks between the two.
                val wasShowing = nativeAd != null && isVisible
                nativeAd?.destroy()
                nativeAd = ad
                registerAdCallbacks(ad)
                bind(ad)
                if (wasShowing) isVisible = true else reveal()
                onLoadEvent?.invoke(
                    LoadEvent(
                        loaded = true,
                        adSourceName = adSourceName(ad),
                        hasVideoContent = ad.mediaContent?.hasVideoContent() == true
                    )
                )
                onAdLoaded?.invoke()
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    // Matches the card's 1.91:1 media frame. PORTRAIT excluded every
                    // landscape creative from the auction while the card was a narrow
                    // portrait tile; ANY would widen the pool further but letterboxes
                    // vertical creatives into a third of the frame.
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                    .build()
            )
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (generation != requestGeneration) return
                    loadInProgress = false
                    adLoader = null
                    retryHandler.removeCallbacks(loadWatchdog)
                    onLoadEvent?.invoke(
                        LoadEvent(
                            loaded = false,
                            errorCode = error.code,
                            reason = Unavailable.LOAD_FAILED
                        )
                    )
                    reportUnavailable(Unavailable.LOAD_FAILED)
                    scheduleRetry()
                }

                override fun onAdImpression() {
                    this@AdMobNativeAdCardView.onAdImpression?.invoke()
                }

                override fun onAdClicked() {
                    this@AdMobNativeAdCardView.onAdClicked?.invoke()
                }
            })
            .build()

        adLoader?.loadAd(AdRequest.Builder().build())
    }

    /**
     * Replaces a sufficiently old ad. Home owns the visible-time threshold for this call.
     *
     * Deliberately non-destructive: the previous implementation destroyed the creative and
     * hid the slot before requesting its replacement, so a full-width card visibly vanished
     * and reappeared under the user on every refresh — and if the replacement failed to fill,
     * the slot stayed empty having thrown away a perfectly good impression. The current ad now
     * stays on screen until the new one is bound.
     */
    fun refresh() {
        if (loadInProgress) return

        val now = SystemClock.elapsedRealtime()
        if (lastRequestStartedAtElapsedMs != 0L &&
            now - lastRequestStartedAtElapsedMs < MIN_AD_REQUEST_INTERVAL_MS
        ) {
            return
        }

        retryAttempt = 0
        beginLoad()
    }

    /**
     * The SDK automatically responds to viewability for normal native video. If custom playback
     * controls are enabled for this creative, mirror the Home dock's visibility explicitly too.
     */
    fun setPlacementVisible(visible: Boolean) {
        val controller = nativeAd?.mediaContent?.videoController ?: return
        if (!controller.isCustomControlsEnabled) return
        if (visible) controller.play() else controller.pause()
    }

    private fun registerAdCallbacks(ad: NativeAd) {
        ad.setOnPaidEventListener(com.google.android.gms.ads.OnPaidEventListener { value ->
            onPaidEvent?.invoke(
                PaidEvent(
                    valueMicros = value.valueMicros,
                    currencyCode = value.currencyCode,
                    precisionType = value.precisionType,
                    adSourceName = adSourceName(ad)
                )
            )
        })

        ad.mediaContent?.videoController?.videoLifecycleCallbacks =
            object : VideoController.VideoLifecycleCallbacks() {
                override fun onVideoStart() {
                    onVideoEvent?.invoke("start", null)
                }

                override fun onVideoPlay() {
                    onVideoEvent?.invoke("play", null)
                }

                override fun onVideoPause() {
                    onVideoEvent?.invoke("pause", null)
                }

                override fun onVideoEnd() {
                    onVideoEvent?.invoke("end", null)
                }

                override fun onVideoMute(isMuted: Boolean) {
                    onVideoEvent?.invoke("mute_changed", isMuted)
                }
            }
    }

    private fun adSourceName(ad: NativeAd): String? {
        return ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName
    }

    private fun bind(ad: NativeAd) = with(binding) {
        adHeadline.text = ad.headline
        // Short all-caps names are almost always the brand itself — BMW, ASOS, H&M — so they
        // keep their own casing; only a longer shout gets normalised.
        adAdvertiser.text = ad.advertiser?.takeIf { it.isNotBlank() }
            ?.unshoutAdvertiser()
            ?: context.getString(R.string.admob_sponsored)

        adCallToActionLabel.text = ad.callToAction?.unshout()
        adCallToAction.isVisible = !ad.callToAction.isNullOrBlank()

        adMedia.mediaContent = ad.mediaContent

        // The icon is optional in the response. Register the view only when an asset is
        // actually present — an empty registered slot is a native-policy problem, and the
        // headline carries layout_goneMarginStart so the text block closes the gap.
        val iconDrawable = ad.icon?.drawable
        if (iconDrawable != null) {
            adIcon.setImageDrawable(iconDrawable)
            adIcon.isVisible = true
            nativeAdView.iconView = adIcon
        } else {
            adIcon.setImageDrawable(null)
            adIcon.isVisible = false
            nativeAdView.iconView = null
        }

        // Star rating and price arrive only on app-install creatives. Register each view
        // only when its asset is actually present: a registered-but-empty slot is a native
        // policy problem, and a visible-but-unregistered one would be showing an asset we
        // never claimed. The row collapses entirely when neither is returned.
        val rating = ad.starRating?.toFloat()?.takeIf { it > 0f }
        if (rating != null) {
            adStars.rating = rating
            adStars.isVisible = true
            adRatingValue.text = String.format(java.util.Locale.US, "%.1f", rating)
            adRatingValue.isVisible = true
            nativeAdView.starRatingView = adStars
        } else {
            adStars.isVisible = false
            adRatingValue.isVisible = false
            nativeAdView.starRatingView = null
        }

        val price = ad.price?.takeIf { it.isNotBlank() }
        if (price != null) {
            adPrice.text = price
            adPrice.isVisible = true
            nativeAdView.priceView = adPrice
        } else {
            adPrice.isVisible = false
            nativeAdView.priceView = null
        }

        adMetaDot.isVisible = rating != null && price != null
        adMetaRow.isVisible = rating != null || price != null

        // The scrim block sits inside a frame whose height is locked to the 1.91:1 media, so
        // it cannot grow — anything taller clips off the top. Two lines of headline plus the
        // rating row plus a 48dp action overflows somewhere past a 1.3 font scale. Drop to a
        // single line when the row is present or the user has scaled text up; app-install
        // creatives, which are the ones carrying that row, have short headlines anyway.
        val dense = adMetaRow.isVisible || resources.configuration.fontScale > 1.3f
        adHeadline.maxLines = if (dense) 1 else 2

        nativeAdView.headlineView = adHeadline
        nativeAdView.advertiserView = adAdvertiser
        nativeAdView.callToActionView = adCallToAction
        nativeAdView.mediaView = adMedia
        nativeAdView.setNativeAd(ad)
    }

    private fun reveal() {
        animate().cancel()
        isVisible = true
        alpha = 0f
        translationY = 8f * resources.displayMetrics.density
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .start()
    }

    fun destroy() {
        requestGeneration++
        loadInProgress = false
        retryAttempt = 0
        retryHandler.removeCallbacks(retryRunnable)
        retryHandler.removeCallbacks(loadWatchdog)
        adLoader = null
        nativeAd?.destroy()
        nativeAd = null
        animate().cancel()
        isVisible = false
    }

    override fun onDetachedFromWindow() {
        destroy()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MIN_AD_REQUEST_INTERVAL_MS = 60_000L

        /** The SDK is not required to call back. Past this, treat the request as lost. */
        const val LOAD_TIMEOUT_MS = 15_000L

        /** Bounded backoff, then stop — Home re-triggers on the next resume regardless. */
        val RETRY_DELAYS_MS = longArrayOf(3_000L, 12_000L, 45_000L)

        /** Keeps a throttled retry from landing exactly on the boundary it is waiting for. */
        const val RETRY_JITTER_MS = 750L

        @Volatile
        var lastRequestStartedAtElapsedMs = 0L
    }
}
