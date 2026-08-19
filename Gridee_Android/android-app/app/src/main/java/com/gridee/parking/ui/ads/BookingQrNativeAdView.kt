package com.gridee.parking.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
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
import com.gridee.parking.databinding.ViewBookingQrNativeAdBinding
import com.gridee.parking.utils.AdConsentManager
import com.gridee.parking.utils.AdMobManager

/**
 * The AdMob native card on the booking QR pass.
 *
 * It is a page of the pass's ad rail, so it wears the campaign deck's frame rather than
 * Home's floating-dock card, and it asks the SDK for landscape media to match the 16:9 page.
 * It used to inflate Home's compact portrait card into a 120 dp column beside the campaign
 * deck, where the headline was 11 sp and the button 104 dp wide.
 */
class BookingQrNativeAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class LoadEvent(
        val loaded: Boolean,
        val adSourceName: String? = null,
        val errorCode: Int? = null,
        val hasVideoContent: Boolean = false
    )

    data class PaidEvent(
        val valueMicros: Long,
        val currencyCode: String,
        val precisionType: Int,
        val adSourceName: String?
    )

    private val binding = ViewBookingQrNativeAdBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    /**
     * Suppresses the destroy-on-detach below while the pass deliberately moves this view out
     * of its hidden load host and into the rail page. Tearing the ad down on detach is right
     * for a dismissed sheet and wrong for a reparent — without this the card was destroyed at
     * the exact moment it had something to show.
     */
    var retainOnDetach = false

    var onAdLoaded: (() -> Unit)? = null
    var onAdUnavailable: (() -> Unit)? = null
    var onLoadEvent: ((LoadEvent) -> Unit)? = null
    var onAdImpression: (() -> Unit)? = null
    var onAdClicked: (() -> Unit)? = null
    var onPaidEvent: ((PaidEvent) -> Unit)? = null
    var onVideoEvent: ((action: String, muted: Boolean?) -> Unit)? = null

    private var nativeAd: NativeAd? = null
    private var adLoader: AdLoader? = null
    private var loadInProgress = false
    private var requestGeneration = 0
    private var consentRetryRequested = false

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
            now - lastRequestStartedAtElapsedMs < MIN_REQUEST_INTERVAL_MS
        ) {
            Log.d(TAG, "native skipped: throttled, " +
                "${(MIN_REQUEST_INTERVAL_MS - (now - lastRequestStartedAtElapsedMs)) / 1000}s left")
            onAdUnavailable?.invoke()
            return
        }

        loadInProgress = true
        val generation = ++requestGeneration
        val initialized = AdMobManager.initializeAdsIfEnabled(context) {
            if (generation != requestGeneration || !isAttachedToWindow) {
                loadInProgress = false
                return@initializeAdsIfEnabled
            }
            requestAd(generation)
        }
        if (!initialized) {
            // Either the adMob remote flag is off or UMP has not granted consent yet.
            Log.d(TAG, "native deferred: ads not initialised (remote flag or consent)")
            loadInProgress = false
            retryAfterConsentIfPossible(generation)
        }
    }

    /**
     * The QR can be opened on the first frame of a cold launch while UMP is still refreshing.
     * Join that process-wide consent request once, then load as soon as it completes instead of
     * leaving this placement permanently empty for the rest of the sheet's lifetime.
     */
    private fun retryAfterConsentIfPossible(generation: Int) {
        val activity = context.findActivity()
        if (consentRetryRequested || activity == null || AdConsentManager.canRequestAds(context)) {
            onAdUnavailable?.invoke()
            return
        }
        consentRetryRequested = true
        AdConsentManager.gatherConsent(activity) { allowed ->
            if (generation != requestGeneration || !isAttachedToWindow) return@gatherConsent
            if (allowed) {
                load()
            } else {
                onAdUnavailable?.invoke()
            }
        }
    }

    private fun requestAd(generation: Int) {
        lastRequestStartedAtElapsedMs = SystemClock.elapsedRealtime()
        adLoader = AdLoader.Builder(context.applicationContext, AdMobManager.bookingQrNativeAdUnitId)
            .forNativeAd { ad ->
                if (generation != requestGeneration || !isAttachedToWindow) {
                    ad.destroy()
                    return@forNativeAd
                }
                Log.d(TAG, "native loaded from ${adSourceName(ad) ?: "unknown"}")
                loadInProgress = false
                nativeAd?.destroy()
                nativeAd = ad
                registerAdCallbacks(ad)
                bind(ad)
                isVisible = true
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
                    // Landscape, to match the rail's 16:9 page. Asking for portrait and then
                    // centre-cropping it into a wide frame threw away most of the creative —
                    // the same mistake the campaign deck made with a 2:3 poster rail.
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                    .build()
            )
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (generation != requestGeneration) return
                    Log.d(TAG, "native failed: code=${error.code} ${error.message}")
                    loadInProgress = false
                    adLoader = null
                    isVisible = false
                    onLoadEvent?.invoke(LoadEvent(loaded = false, errorCode = error.code))
                    onAdUnavailable?.invoke()
                }

                override fun onAdImpression() {
                    this@BookingQrNativeAdView.onAdImpression?.invoke()
                }

                override fun onAdClicked() {
                    this@BookingQrNativeAdView.onAdClicked?.invoke()
                }
            })
            .build()

        adLoader?.loadAd(AdRequest.Builder().build())
    }

    private fun registerAdCallbacks(ad: NativeAd) {
        ad.setOnPaidEventListener { value ->
            onPaidEvent?.invoke(
                PaidEvent(
                    valueMicros = value.valueMicros,
                    currencyCode = value.currencyCode,
                    precisionType = value.precisionType,
                    adSourceName = adSourceName(ad)
                )
            )
        }
        ad.mediaContent?.videoController?.videoLifecycleCallbacks =
            object : VideoController.VideoLifecycleCallbacks() {
                override fun onVideoStart() = onVideoEvent?.invoke("start", null) ?: Unit
                override fun onVideoPlay() = onVideoEvent?.invoke("play", null) ?: Unit
                override fun onVideoPause() = onVideoEvent?.invoke("pause", null) ?: Unit
                override fun onVideoEnd() = onVideoEvent?.invoke("end", null) ?: Unit
                override fun onVideoMute(isMuted: Boolean) =
                    onVideoEvent?.invoke("mute_changed", isMuted) ?: Unit
            }
    }

    private fun bind(ad: NativeAd) = with(binding) {
        adHeadline.text = ad.headline
        adAdvertiser.text = ad.advertiser?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.admob_sponsored)
        adCallToAction.text = ad.callToAction
        adCallToAction.isVisible = !ad.callToAction.isNullOrBlank()

        // Fill the 16:9 page the way a campaign poster does. MediaView defaults to fitting the
        // creative inside its bounds, so anything that is not already 16:9 was letterboxed —
        // a band of bare card above and below the artwork, and the scrim the headline sits on
        // landing on that band instead of on image pixels.
        adMedia.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        adMedia.mediaContent = ad.mediaContent

        nativeAdView.headlineView = adHeadline
        nativeAdView.advertiserView = adAdvertiser
        nativeAdView.callToActionView = adCallToAction
        nativeAdView.mediaView = adMedia
        nativeAdView.setNativeAd(ad)
    }

    private fun adSourceName(ad: NativeAd): String? {
        return ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName
    }

    fun destroy() {
        requestGeneration++
        loadInProgress = false
        adLoader = null
        nativeAd?.destroy()
        nativeAd = null
        isVisible = false
    }

    override fun onDetachedFromWindow() {
        if (!retainOnDetach) destroy()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TAG = "BookingQrNativeAd"
        const val MIN_REQUEST_INTERVAL_MS = 20_000L

        @Volatile
        var lastRequestStartedAtElapsedMs = 0L
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
