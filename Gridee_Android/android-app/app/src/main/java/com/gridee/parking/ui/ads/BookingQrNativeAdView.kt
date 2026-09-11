package com.gridee.parking.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import com.gridee.parking.utils.AppLog
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
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
 * It is the whole bottom band of the pass, bled to all four edges, so it has no frame of its
 * own — the media is the surface. It asks the SDK for landscape media because the band is
 * wide and short, and it stays GONE until it has a creative so the SDK cannot log an
 * impression for a card sitting behind the skeleton.
 *
 * It used to be page 0 of a swipeable rail that also carried partner campaigns. The rail is
 * gone; this placement is the only paid surface on the screen.
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
            AppLog.d(TAG) {
                "native skipped: throttled, " +
                    "${(MIN_REQUEST_INTERVAL_MS - (now - lastRequestStartedAtElapsedMs)) / 1000}s left"
            }
            onLoadEvent?.invoke(LoadEvent(loaded = false, errorCode = ERROR_THROTTLED))
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
            AppLog.d(TAG) { "native deferred: ads not initialised (remote flag or consent)" }
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
            onLoadEvent?.invoke(LoadEvent(loaded = false, errorCode = ERROR_ADS_UNAVAILABLE))
            onAdUnavailable?.invoke()
            return
        }
        consentRetryRequested = true
        AdConsentManager.gatherConsent(activity) { allowed ->
            if (generation != requestGeneration || !isAttachedToWindow) return@gatherConsent
            if (allowed) {
                load()
            } else {
                onLoadEvent?.invoke(LoadEvent(loaded = false, errorCode = ERROR_CONSENT_DENIED))
                onAdUnavailable?.invoke()
            }
        }
    }

    private fun requestAd(generation: Int) {
        // An Activity, never applicationContext. Meta's adapter documents a failure when an
        // Activity context is required, and mediated demand that cannot bid is demand this
        // placement never sees. The Activity is used for the request and not retained past it.
        val activity = context.findActivity()?.takeIf { !it.isFinishing && !it.isDestroyed }
        if (activity == null) {
            AppLog.d(TAG) { "native skipped: no non-finishing Activity for the request" }
            loadInProgress = false
            onLoadEvent?.invoke(LoadEvent(loaded = false, errorCode = ERROR_NO_ACTIVITY))
            onAdUnavailable?.invoke()
            return
        }

        lastRequestStartedAtElapsedMs = SystemClock.elapsedRealtime()
        adLoader = AdLoader.Builder(activity, AdMobManager.bookingQrNativeAdUnitId)
            .forNativeAd { ad ->
                if (generation != requestGeneration || !isAttachedToWindow) {
                    ad.destroy()
                    return@forNativeAd
                }
                AppLog.d(TAG) { "native loaded" }
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
                    // ANY, deliberately. The band is roughly square — 411 x 430 dp on a
                    // Pixel — so there is no orientation to match, and every constraint here
                    // narrows the eligible creative pool for no layout benefit. The MediaView
                    // centre-crops to fill whatever arrives. Restricting orientation is the
                    // exact mistake the audit records against the Home placement.
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                    .build()
            )
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (generation != requestGeneration) return
                    AppLog.d(TAG) { "native failed (code=${error.code})" }
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

    /**
     * Tops up the text block's bottom padding with the gesture inset.
     *
     * The creative deliberately bleeds past the navigation bar — cropping it there would put
     * a band of page colour under an edge-to-edge image — so the inset is paid by the copy
     * rather than by the surface.
     */
    fun applyBottomInset(insetPx: Int) {
        val block = binding.adTextBlock
        val base = resources.getDimensionPixelSize(R.dimen.pass_creative_text_bottom_padding)
        block.updatePadding(bottom = base + insetPx)
    }

    private fun bind(ad: NativeAd) = with(binding) {
        adHeadline.text = ad.headline
        adAdvertiser.text = ad.advertiser?.takeIf { it.isNotBlank() }
            ?.unshoutAdvertiser()
            ?: context.getString(R.string.admob_sponsored)
        // "INSTALL" arrives shouting from most install creatives; the button reads "Install".
        adCallToAction.text = ad.callToAction?.unshout()
        adCallToAction.isVisible = !ad.callToAction.isNullOrBlank()

        // Fill the band edge to edge. MediaView defaults to fitting the creative inside its
        // bounds, so anything whose aspect does not match was letterboxed — bands of bare
        // surface above and below the artwork, and the scrim the headline sits on landing on
        // those bands instead of on image pixels.
        adMedia.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        adMedia.mediaContent = ad.mediaContent

        // ── optional assets ──
        // Register a view only when the response actually carries its asset. A registered but
        // empty slot is a native-policy problem, and a visible but unregistered one would be
        // showing an asset we never claimed.

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

        val body = ad.body?.takeIf { it.isNotBlank() }
        if (body != null) {
            adBody.text = body
            adBody.isVisible = true
            nativeAdView.bodyView = adBody
        } else {
            adBody.isVisible = false
            nativeAdView.bodyView = null
        }

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

        val store = ad.store?.takeIf { it.isNotBlank() }
        if (store != null) {
            adStore.text = store
            adStore.isVisible = true
            nativeAdView.storeView = adStore
        } else {
            adStore.isVisible = false
            nativeAdView.storeView = null
        }

        adMetaDot.isVisible = rating != null && (price != null || store != null)
        adMetaRow.isVisible = rating != null || price != null || store != null

        // The copy block grows upward from a fixed bottom edge, so at full height it eats the
        // creative it is sitting on. Drop the two multi-line assets to one line each when the
        // app-install row is present or the user has scaled text up — those creatives carry
        // short headlines anyway, and the alternative is a band that is all text.
        val dense = adMetaRow.isVisible || resources.configuration.fontScale > 1.3f
        adHeadline.maxLines = if (dense) 1 else 2
        adBody.maxLines = if (dense) 1 else 2

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
        destroy()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TAG = "BookingQrNativeAd"

        /**
         * Only wide enough to stop an open/close loop hammering the unit — not wide enough to
         * eat a real reopen.
         *
         * At 20 s this fired on most opens in ordinary use: a driver whose code did not read
         * first time closes the pass and opens it again within seconds, and got a blank band
         * for it. Every one of those was a legitimate, user-initiated view of the highest-dwell
         * inventory in the app, thrown away. Logcat during a two-minute session showed
         * throttled → loaded → throttled.
         *
         * This is a request-hygiene guard, not a refresh interval: the pass never refreshes a
         * live placement, so the 60 s refresh floor does not apply to it. Throttled opens are
         * now reported as ERROR_THROTTLED so the cost of this number is visible rather than
         * assumed.
         */
        const val MIN_REQUEST_INTERVAL_MS = 5_000L

        /**
         * Reported as `error_code` on the paths that never reach the SDK at all, so that every
         * open of the pass produces exactly one booking_qr_native_load event and match rate —
         * matched requests over total requests — has an honest denominator. All well outside
         * the SDK's own LoadAdError range so they cannot be mistaken for one.
         */
        const val ERROR_NO_ACTIVITY = -1001
        const val ERROR_THROTTLED = -1002
        const val ERROR_ADS_UNAVAILABLE = -1003
        const val ERROR_CONSENT_DENIED = -1004

        @Volatile
        var lastRequestStartedAtElapsedMs = 0L
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
