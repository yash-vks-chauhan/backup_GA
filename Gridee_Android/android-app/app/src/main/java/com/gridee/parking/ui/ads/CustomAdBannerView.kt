package com.gridee.parking.ui.ads

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import coil.load
import com.gridee.parking.data.model.CustomAd
import com.gridee.parking.databinding.ViewCustomAdBannerBinding

/**
 * Drop-in banner for a custom ad creative.
 *
 * Contract with the host screen: the view is invisible and zero-height until a creative has
 * actually decoded, and it collapses again the moment anything goes wrong. A screen can add it
 * to a layout and forget about it — it never shows a spinner, an error, or an empty box.
 */
class CustomAdBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewCustomAdBannerBinding.inflate(LayoutInflater.from(context), this)

    /** Tap on the creative. The host decides how to open it. */
    var onAdClick: ((CustomAd) -> Unit)? = null

    /** User closed the banner. */
    var onAdDismiss: ((CustomAd) -> Unit)? = null

    /** The creative is decoded and at least half of it is on screen. */
    var onAdDisplayed: ((CustomAd) -> Unit)? = null

    /** The image could not be loaded — the host should treat the ad as if it never existed. */
    var onAdLoadFailed: ((CustomAd) -> Unit)? = null

    private var currentAd: CustomAd? = null
    private var displayReported = false
    private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null

    init {
        isVisible = false
    }

    fun bind(ad: CustomAd) {
        // Re-binding the same creative would restart the load and replay the reveal on every
        // resume; the ad is already correct on screen.
        if (currentAd?.id == ad.id && isVisible) return

        currentAd = ad
        displayReported = false
        isVisible = false

        applyAspectRatio(ad.aspectRatio)
        contentDescription = listOfNotNull(ad.title, ad.subtitle)
            .joinToString(separator = ". ")
            .ifBlank { binding.tvCustomAdLabel.text.toString() }

        binding.btnCustomAdDismiss.isVisible = ad.dismissible
        binding.btnCustomAdDismiss.setOnClickListener {
            currentAd?.let { current ->
                clear()
                onAdDismiss?.invoke(current)
            }
        }

        val clickable = !ad.clickUrl.isNullOrBlank()
        binding.cardCustomAd.isClickable = clickable
        binding.cardCustomAd.isFocusable = clickable
        binding.cardCustomAd.setOnClickListener(
            if (clickable) {
                { currentAd?.let { onAdClick?.invoke(it) } }
            } else {
                null
            }
        )

        binding.ivCustomAd.load(ad.imageUrl) {
            crossfade(false)
            listener(
                onSuccess = { _, _ ->
                    if (currentAd?.id != ad.id) return@listener
                    reveal()
                    reportDisplayWhenVisible()
                },
                onError = { _, _ ->
                    if (currentAd?.id != ad.id) return@listener
                    clear()
                    onAdLoadFailed?.invoke(ad)
                }
            )
        }
    }

    /** Collapse the slot and forget the creative. Safe to call at any point. */
    fun clear() {
        stopVisibilityWatch()
        currentAd = null
        displayReported = false
        isVisible = false
        binding.ivCustomAd.setImageDrawable(null)
    }

    /**
     * Backend-supplied ratio ("16:9", "4:1", …) wins so a campaign can ship a tall or a thin
     * creative; anything unreadable or extreme falls back to the default banner shape.
     */
    private fun applyAspectRatio(aspectRatio: String?) {
        val ratio = aspectRatio?.trim()?.takeIf { it.matches(RATIO_PATTERN) }?.let { raw ->
            val (w, h) = raw.split(":").map { it.toFloat() }
            if (w <= 0f || h <= 0f) return@let null
            val value = w / h
            if (value < MIN_RATIO || value > MAX_RATIO) null else raw
        } ?: DEFAULT_RATIO

        val params = binding.ivCustomAd.layoutParams as ConstraintLayout.LayoutParams
        val next = "H,$ratio"
        if (params.dimensionRatio != next) {
            params.dimensionRatio = next
            binding.ivCustomAd.layoutParams = params
        }
    }

    private fun reveal() {
        if (isVisible) return
        isVisible = true
        alpha = 0f
        translationY = REVEAL_RISE_PX * resources.displayMetrics.density
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(REVEAL_DURATION_MS)
            .start()
    }

    /**
     * An impression means "the user could see it", not "we downloaded it". Home puts the banner
     * inside a scroll view, so a creative can decode while it is still below the fold — in that
     * case we watch scrolling until it comes into view.
     */
    private fun reportDisplayWhenVisible() {
        if (displayReported) return
        if (isAtLeastHalfVisible()) {
            displayReported = true
            stopVisibilityWatch()
            currentAd?.let { onAdDisplayed?.invoke(it) }
            return
        }
        startVisibilityWatch()
    }

    private fun isAtLeastHalfVisible(): Boolean {
        if (!isShown || height == 0) return false
        val visible = Rect()
        if (!getGlobalVisibleRect(visible)) return false
        return visible.height() * 2 >= height
    }

    private fun startVisibilityWatch() {
        if (scrollListener != null) return
        val listener = ViewTreeObserver.OnScrollChangedListener { reportDisplayWhenVisible() }
        scrollListener = listener
        viewTreeObserver.addOnScrollChangedListener(listener)
    }

    private fun stopVisibilityWatch() {
        scrollListener?.let { viewTreeObserver.removeOnScrollChangedListener(it) }
        scrollListener = null
    }

    override fun onDetachedFromWindow() {
        stopVisibilityWatch()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DEFAULT_RATIO = "16:9"
        const val REVEAL_DURATION_MS = 260L
        const val REVEAL_RISE_PX = 10f
        const val MIN_RATIO = 0.5f
        const val MAX_RATIO = 8f
        val RATIO_PATTERN = Regex("""^\d+(\.\d+)?:\d+(\.\d+)?$""")
    }
}
