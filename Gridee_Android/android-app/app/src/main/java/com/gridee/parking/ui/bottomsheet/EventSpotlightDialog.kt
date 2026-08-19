package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.core.os.bundleOf
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import coil.load
import com.gridee.parking.R
import com.gridee.parking.data.model.CustomAd
import com.gridee.parking.data.model.CustomAdPayloadParser
import com.gridee.parking.databinding.DialogEventSpotlightBinding

/**
 * The spotlight a poster opens into.
 *
 * The backdrop blur is applied by the pass to its own view tree (see
 * `BookingQrPassBottomSheet.setPassBlurred`) rather than by this window: cross-window blur is
 * a device capability that One UI and others do not ship, and where it is missing the flag is
 * ignored with no fallback — which is why this dialog originally opened over a sharp screen.
 *
 * The rail deliberately shows creative and nothing else, so this is where an event finally
 * gets to explain itself: the artwork at ~2.4× its rail size, the title, the meta line, and
 * one primary action.
 *
 * Impression accounting lives with the host, not here: opening the spotlight *is* the click
 * (the host calls `trackClick` before showing this), so interest is measured even when the
 * user reads the detail and decides not to buy. This dialog only has to open the URL.
 */
class EventSpotlightDialog : DialogFragment() {

    private var _binding: DialogEventSpotlightBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val imageUrl: String get() = arguments?.getString(ARG_IMAGE).orEmpty()
    private val title: String? get() = arguments?.getString(ARG_TITLE)
    private val subtitle: String? get() = arguments?.getString(ARG_SUBTITLE)
    private val ctaText: String? get() = arguments?.getString(ARG_CTA)
    private val clickUrl: String? get() = arguments?.getString(ARG_CLICK_URL)
    private val advertiser: String? get() = arguments?.getString(ARG_ADVERTISER)
    private val previewMode: Boolean get() = arguments?.getBoolean(ARG_PREVIEW, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Gridee_NoActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), theme).apply { setCanceledOnTouchOutside(true) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEventSpotlightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind()
        playEntrance()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.attributes = window.attributes.apply { windowAnimations = 0 }
        }
    }

    private fun bind() = with(binding) {
        ivSpotlightPoster.load(imageUrl) { crossfade(false) }

        tvSpotlightTitle.text = title.orEmpty()
        tvSpotlightTitle.visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE

        tvSpotlightMeta.text = subtitle.orEmpty()
        tvSpotlightMeta.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE

        tvSpotlightAttribution.text = advertiser?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.spotlight_attribution, it) }
            ?: getString(R.string.spotlight_attribution_generic)

        val openable = !previewMode &&
            clickUrl?.let(CustomAdPayloadParser::isOpenableClickUrl) == true
        btnSpotlightCta.text = ctaText?.takeIf { it.isNotBlank() }
            ?: getString(R.string.spotlight_default_cta)
        btnSpotlightCta.visibility = if (openable) View.VISIBLE else View.GONE
        btnSpotlightCta.setOnClickListener { openCampaign() }
        cardSpotlightPoster.setOnClickListener { if (openable) openCampaign() }

        btnSpotlightClose.setOnClickListener { dismiss() }
        spotlightBackdrop.setOnClickListener { dismiss() }
    }

    /**
     * Poster lands first on the app's snappy spring, then the detail block rises into place
     * behind it — so the artwork reads as having been lifted out of the rail rather than as
     * a card that faded in.
     */
    private fun playEntrance() {
        val card = binding.cardSpotlightPoster
        card.alpha = 0f
        card.scaleX = ENTRANCE_START_SCALE
        card.scaleY = ENTRANCE_START_SCALE
        card.animate().alpha(1f).setDuration(120L).start()
        listOf(SpringAnimation(card, SpringAnimation.SCALE_X, 1f),
            SpringAnimation(card, SpringAnimation.SCALE_Y, 1f)).forEach { spring ->
            spring.spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            spring.spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.start()
        }

        listOf(
            binding.tvSpotlightTitle,
            binding.tvSpotlightMeta,
            binding.btnSpotlightCta,
            binding.tvSpotlightAttribution
        ).forEach { detail ->
            detail.alpha = 0f
            detail.translationY = DETAIL_RISE_PX * resources.displayMetrics.density
            detail.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(DETAIL_DELAY_MS)
                .setDuration(DETAIL_DURATION_MS)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .start()
        }
    }

    private fun openCampaign() {
        val url = clickUrl?.takeIf(CustomAdPayloadParser::isOpenableClickUrl) ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(requireContext().packageManager) == null) return
        startActivity(intent)
    }

    override fun onDismiss(dialog: DialogInterface) {
        // The pass blurs itself while this is open and needs to know when to stop.
        setFragmentResult(RESULT_KEY_CLOSED, bundleOf())
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        binding.cardSpotlightPoster.animate().cancel()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "EventSpotlightDialog"
        const val RESULT_KEY_CLOSED = "event_spotlight_closed"

        private const val ARG_IMAGE = "image_url"
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_CTA = "cta"
        private const val ARG_CLICK_URL = "click_url"
        private const val ARG_ADVERTISER = "advertiser"
        private const val ARG_PREVIEW = "preview_mode"

        private const val ENTRANCE_START_SCALE = 0.86f
        private const val DETAIL_RISE_PX = 8f
        private const val DETAIL_DELAY_MS = 60L
        private const val DETAIL_DURATION_MS = 160L

        fun newInstance(ad: CustomAd, previewMode: Boolean = false) =
            EventSpotlightDialog().apply {
                arguments = bundleOf(
                    ARG_IMAGE to ad.imageUrl,
                    ARG_TITLE to ad.title,
                    ARG_SUBTITLE to ad.subtitle,
                    ARG_CTA to ad.ctaText,
                    ARG_CLICK_URL to ad.clickUrl,
                    // The backend has no dedicated advertiser field; the campaign title is
                    // the closest honest label, and it is what the poster already showed.
                    ARG_ADVERTISER to ad.title,
                    ARG_PREVIEW to previewMode
                )
            }
    }
}
