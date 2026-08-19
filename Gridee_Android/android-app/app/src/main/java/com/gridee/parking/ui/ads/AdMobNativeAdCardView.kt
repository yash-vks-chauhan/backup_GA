package com.gridee.parking.ui.ads

import android.content.Context
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
        val hasVideoContent: Boolean = false
    )

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
    var onAdUnavailable: (() -> Unit)? = null
    var onLoadEvent: ((LoadEvent) -> Unit)? = null
    var onAdImpression: (() -> Unit)? = null
    var onAdClicked: (() -> Unit)? = null
    var onPaidEvent: ((PaidEvent) -> Unit)? = null
    var onVideoEvent: ((action: String, muted: Boolean?) -> Unit)? = null

    private var nativeAd: NativeAd? = null
    private var adLoader: AdLoader? = null
    private var requestGeneration = 0
    private var loadInProgress = false

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
            isVisible = false
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
            loadInProgress = false
            isVisible = false
            onAdUnavailable?.invoke()
        }
    }

    private fun requestAd(generation: Int) {
        lastRequestStartedAtElapsedMs = SystemClock.elapsedRealtime()
        adLoader = AdLoader.Builder(context.applicationContext, AdMobManager.nativeAdUnitId)
            .forNativeAd { ad ->
                if (generation != requestGeneration || !isAttachedToWindow) {
                    ad.destroy()
                    return@forNativeAd
                }
                loadInProgress = false
                nativeAd?.destroy()
                nativeAd = ad
                registerAdCallbacks(ad)
                bind(ad)
                reveal()
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
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT)
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                    .build()
            )
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (generation != requestGeneration) return
                    loadInProgress = false
                    adLoader = null
                    isVisible = false
                    onLoadEvent?.invoke(
                        LoadEvent(
                            loaded = false,
                            errorCode = error.code
                        )
                    )
                    onAdUnavailable?.invoke()
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

    /** Replaces a sufficiently old ad. Home owns the visible-time threshold for this call. */
    fun refresh() {
        requestGeneration++
        loadInProgress = false
        adLoader = null
        nativeAd?.destroy()
        nativeAd = null
        animate().cancel()
        isVisible = false
        load()
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
        adAdvertiser.text = ad.advertiser?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.admob_sponsored)

        adCallToAction.text = ad.callToAction
        adCallToAction.isVisible = !ad.callToAction.isNullOrBlank()

        adMedia.mediaContent = ad.mediaContent

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

        @Volatile
        var lastRequestStartedAtElapsedMs = 0L
    }
}
