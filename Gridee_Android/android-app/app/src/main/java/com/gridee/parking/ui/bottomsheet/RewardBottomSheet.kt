package com.gridee.parking.ui.bottomsheet

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.gridee.parking.R
import com.gridee.parking.data.repository.WalletRepository
import com.gridee.parking.databinding.BottomSheetRewardBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.views.RewardCoinView
import com.gridee.parking.utils.AdMobManager
import com.gridee.parking.utils.InAppReviewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "The Mint" — the daily-reward sheet, designed as one continuous gesture with
 * the home header coin rather than a separate screen.
 *
 * When opened from the home medallion (a [startRect] is supplied), the coin you
 * tapped is recreated as an overlay that **launches the instant the sheet window
 * appears and flies down toward a dark obsidian "minting chamber"** at the top of
 * the sheet. Its target is retargeted every frame to the chamber's *live*
 * position, so while the sheet is still rising the coin homes onto the moving
 * slot — the two **converge** and settle together, the coin nestling in just as
 * the chamber arrives under it. It grows, tumbles once, lands with a soft spring
 * + a light-rake glint ([RewardCoinView.shimmerOnce]), then the content blooms on
 * staggered springs. Opened without a source (e.g. the wallet) it falls back to a
 * graceful scale/fade reveal.
 *
 * The reward itself — an AdMob rewarded video that credits the wallet — is
 * unchanged from the previous sheet; only the experience around it is new.
 */
class RewardBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRewardBinding? = null
    private val binding get() = _binding!!

    /** Screen rect of the source coin: [x, y, sizePx]. Null = no flight (fade in). */
    private var startRect: IntArray? = null

    /** Invoked on dismiss so the host can restore its hidden source coin. */
    var onDismissed: (() -> Unit)? = null

    /**
     * Invoked the instant the flying coin copy is airborne in the sheet. The host
     * keeps its source coin fully in place until this fires, then recedes it into
     * a faint "empty socket" — so the lift-off is a seamless hand-off with no
     * visible gap on the home header.
     */
    var onCoinAirborne: (() -> Unit)? = null

    // ── Rewarded-ad / wallet state ───────────────────────────────────────────
    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewardedAd = false
    private var pendingShowRewardedAd = false
    private var primaryButtonIdleLabel: CharSequence? = null
    private var isViewDestroyed = false
    private var isRewardEarned = false
    private val rewardAmount = 10.0

    // ── Entrance choreography ────────────────────────────────────────────────
    private var flightOverlay: RewardCoinView? = null
    private var flightSize = 0
    private var flightSpringY: SpringAnimation? = null
    private var hasStartedEntrance = false
    private var hasFinishedEntrance = false
    private var hasLanded = false
    private var haloBreatheAnim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        preloadRewardedAd()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        // We choreograph the entrance ourselves (coin flight + a self-driven sheet
        // rise) in a stable coordinate space, so disable the default window slide
        // that would otherwise transform the whole window and break the flight.
        dialog.window?.setWindowAnimations(0)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                // The sheet supplies its own chamber/content surfaces, so keep the
                // underlying material transparent and edge-to-edge.
                it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                it.fitsSystemWindows = false
                (it.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.setMargins(0, 0, 0, 0)
                    it.layoutParams = lp
                }
            }

            dialog.behavior.apply {
                isGestureInsetBottomIgnored = true
                isFitToContents = true
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }

            dialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false
                val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightNavigationBars = !isDark
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50
                    window.attributes = window.attributes
                }
            }

            // The window is on screen now — launch the coin so it flies in concert
            // with the sheet's rise rather than after it. Wait for a real layout
            // pass first so the slot is measured; otherwise the flight can't aim
            // and the header coin would appear to simply vanish.
            startEntranceWhenReady()
        }
        return dialog
    }

    /**
     * Defer the entrance until the sheet and the chamber slot have been measured.
     * Without this, [startEntrance] runs while [heroMedallion].width is still 0 and
     * falls back to a plain fade — which on the home screen reads as "the coin just
     * disappeared." Gating on layout guarantees the coin actually flips in and
     * attaches to the chamber.
     */
    private fun startEntranceWhenReady() {
        if (hasStartedEntrance || _binding == null) return

        fun sheetView(): View? = (dialog as? BottomSheetDialog)
            ?.findViewById(com.google.android.material.R.id.design_bottom_sheet)

        fun ready(): Boolean =
            _binding != null && binding.heroMedallion.width > 0 && (sheetView()?.height ?: 0) > 0

        if (ready()) {
            startEntrance()
            return
        }

        val root = binding.root
        root.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (_binding != null && !hasStartedEntrance && !ready()) return
                    root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    if (_binding != null && !hasStartedEntrance) startEntrance()
                }
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isViewDestroyed = false
        _binding = BottomSheetRewardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupInsets()
        prepareEntrance()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            binding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    private fun setupUI() {
        binding.heroMedallion.setRewardAvailable(true)

        styleClaimButton()
        // Reserve width for the final figure; it counts up when the content blooms.
        binding.rewardAmountView.prime(rewardAmount.toInt())
        primaryButtonIdleLabel = binding.btnPrimary.text

        binding.btnClose.setOnClickListener { hideSheet() }
        binding.btnPrimary.setOnClickListener { showRewardVideo() }
        binding.earnRow.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            // Open Google's in-app review card overlaid on the app (no trip to the
            // Play Store). Falls back to the store listing only if the flow can't be
            // requested at all. See InAppReviewManager.
            InAppReviewManager.requestReviewOnDemand(requireActivity())
        }

        // Tactile press feedback — a subtle spring-back that makes both the CTA
        // and the earn row feel physical rather than flat.
        addPressBounce(binding.btnPrimary)
        addPressBounce(binding.earnRow)
    }

    /** Springy scale-down on touch, scale-back on release — clicks still fire. */
    private fun addPressBounce(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    springTo(v, DynamicAnimation.SCALE_X, 0.96f, 900f, 0.7f)
                    springTo(v, DynamicAnimation.SCALE_Y, 0.96f, 900f, 0.7f)
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    springTo(v, DynamicAnimation.SCALE_X, 1f, 500f, 0.55f)
                    springTo(v, DynamicAnimation.SCALE_Y, 1f, 500f, 0.55f)
                }
            }
            false // don't consume — ripple + click continue to work
        }
    }

    /** Gold CTA styling + the watch/claim glyph (matches the medallion's accent).
     *  A press-darken tint plus a gloss foreground turn the flat gold into a
     *  struck-metal pill without disturbing the Material ripple beneath. */
    private fun styleClaimButton() {
        val ctx = requireContext()
        val goldText = ContextCompat.getColor(ctx, R.color.reward_button_text)
        binding.btnPrimary.apply {
            backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.reward_cta_tint)
            foreground = ContextCompat.getDrawable(ctx, R.drawable.bg_reward_cta_gloss)
            setTextColor(goldText)
            setIconResource(R.drawable.ic_reward_watch_claim)
            iconTint = null
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconSize = dp(20)
            iconPadding = dp(8)
        }
    }

    private fun setupInsets() {
        val base = binding.contentSurface.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentSurface.setPadding(
                binding.contentSurface.paddingLeft,
                binding.contentSurface.paddingTop,
                binding.contentSurface.paddingRight,
                base + dp(8) + bars.bottom
            )
            insets
        }
    }

    /** Animate the sheet down via the behavior (the window slide is disabled),
     *  then let BottomSheetDialog dismiss itself on STATE_HIDDEN. */
    private fun hideSheet() {
        pendingShowRewardedAd = false
        setRewardedAdLoading(false)
        val behavior = (dialog as? BottomSheetDialog)?.behavior
        if (behavior != null && _binding != null) {
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else {
            dismiss()
        }
    }

    // ── Entrance ──────────────────────────────────────────────────────────────

    private val cascadeItems
        get() = listOf(
            binding.tvTitle, binding.rewardAmountBlock, binding.tvSubtitle,
            binding.btnPrimary, binding.earnHeader, binding.earnRow, binding.tvRewardNote
        )

    /** Hide everything before the first frame; arm a safety so we always bloom. */
    private fun prepareEntrance() {
        binding.heroMedallion.alpha = 0f
        binding.chamberGlow.alpha = 0f
        cascadeItems.forEach { it.alpha = 0f }
        // Safety: land + bloom even if a spring never reports an end event. Kept
        // generous so it never clips the coin's flight on slower devices (the
        // entrance is also gated on layout, which can start a frame or two late).
        binding.root.postDelayed({ onCoinLanded(); bloomContent() }, 1500)
    }

    /**
     * Fired from onShow. We drive the sheet's rise AND the coin's flight ourselves
     * in a stable coordinate space (the default window slide is disabled), so the
     * coin visibly lifts off the header while the chamber rises to meet it — they
     * converge on the slot, then the content blooms.
     */
    private fun startEntrance() {
        if (hasStartedEntrance || _binding == null) return
        hasStartedEntrance = true

        if (!animatorsEnabled()) {
            binding.heroMedallion.alpha = 1f
            binding.chamberGlow.alpha = 1f
            cascadeItems.forEach { it.alpha = 1f }
            binding.rewardAmountView.setAmount(rewardAmount.toInt(), animate = false)
            hasFinishedEntrance = true
            hasLanded = true
            return
        }

        binding.chamberGlow.scaleX = 0.85f
        binding.chamberGlow.scaleY = 0.85f
        binding.chamberGlow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        // Once the glow has settled, let it breathe so the chamber feels alive at rest.
        binding.chamberGlow.postDelayed({ if (_binding != null) startHaloBreathing() }, 820L)

        val hero = binding.heroMedallion
        val rect = startRect
        if (rect != null && hero.width > 0) {
            // Capture the slot's RESTING position before we push the sheet down.
            val restingLoc = IntArray(2).also { hero.getLocationOnScreen(it) }
            riseSheet()
            flyCoin(rect, restingLoc, hero.width)
        } else {
            riseSheet()
            revealHero()
        }
    }

    /** Spring the sheet surface up from below; its end blooms the content. */
    private fun riseSheet() {
        val sheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val h = sheet?.height ?: 0
        if (sheet == null || h <= 0) return // the safety timer will bloom
        sheet.translationY = h.toFloat()
        SpringAnimation(sheet, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = 330f
            spring.dampingRatio = 0.82f
            setStartVelocity(-dpF(900f))
            addEndListener { _, _, _, _ -> bloomContent() }
            start()
        }
    }

    /** The coin overlay flies from the header to the chamber slot's resting spot
     *  while the sheet rises to bring the slot up to meet it. */
    private fun flyCoin(rect: IntArray, restingLoc: IntArray, size: Int) {
        val coordinator = (dialog as? BottomSheetDialog)
            ?.findViewById<ViewGroup>(com.google.android.material.R.id.coordinator)
        if (coordinator == null) {
            revealHero()
            return
        }
        flightSize = size
        val coord = IntArray(2).also { coordinator.getLocationOnScreen(it) }

        val startX = rect[0] + rect[2] / 2f - coord[0] - size / 2f
        val startY = rect[1] + rect[2] / 2f - coord[1] - size / 2f
        val endX = restingLoc[0] - coord[0].toFloat()
        val endY = restingLoc[1] - coord[1].toFloat()
        val startScale = rect[2].toFloat() / size

        val overlay = RewardCoinView(requireContext()).apply {
            setRewardAvailable(true)
            pivotX = size / 2f
            pivotY = size / 2f
            scaleX = startScale
            scaleY = startScale
            x = startX
            y = startY
        }
        coordinator.clipChildren = false
        coordinator.clipToPadding = false
        coordinator.addView(overlay, size, size)
        flightOverlay = overlay
        binding.heroMedallion.alpha = 0f

        // The bright copy now exists exactly over the source coin — tell the host
        // to recede its header coin into a socket so the lift-off has no gap.
        onCoinAirborne?.invoke()
        onCoinAirborne = null

        overlay.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

        // Tumble once, grow to full size, and arc onto the slot with a soft spring.
        ObjectAnimator.ofFloat(overlay, View.ROTATION_Y, 0f, 360f).apply {
            duration = 640
            interpolator = DecelerateInterpolator(1.25f)
            start()
        }
        springTo(overlay, DynamicAnimation.SCALE_X, 1f, 300f, 0.6f)
        springTo(overlay, DynamicAnimation.SCALE_Y, 1f, 300f, 0.6f)
        springTo(overlay, DynamicAnimation.X, endX, 250f, 0.86f)
        flightSpringY = SpringAnimation(overlay, DynamicAnimation.Y, endY).apply {
            spring.stiffness = 240f
            spring.dampingRatio = 0.84f
            setStartVelocity(-dpF(160f))
            addEndListener { _, _, _, _ -> onCoinLanded() }
            start()
        }
    }

    /** Bloom the content once the sheet has risen into place. */
    private fun bloomContent() {
        if (hasFinishedEntrance || _binding == null) return
        hasFinishedEntrance = true
        cascadeContent()
    }

    private fun onCoinLanded() {
        if (hasLanded || _binding == null) return
        hasLanded = true
        flightSpringY = null
        val hero = binding.heroMedallion
        hero.alpha = 0f
        hero.animate().alpha(1f).setDuration(130).start()
        flightOverlay?.let { ov ->
            ov.animate().alpha(0f).setDuration(130)
                .withEndAction { (ov.parent as? ViewGroup)?.removeView(ov) }
                .start()
        }
        flightOverlay = null
        hero.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        hero.post { _binding?.heroMedallion?.shimmerOnce() }
    }

    /** A slow, barely-there pulse on the gold halo so the chamber feels alive at
     *  rest — one quiet breath on a long loop, never a spin. */
    private fun startHaloBreathing() {
        if (!animatorsEnabled() || _binding == null) return
        haloBreatheAnim?.cancel()
        haloBreatheAnim = ObjectAnimator.ofPropertyValuesHolder(
            binding.chamberGlow,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.045f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.045f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.76f)
        ).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** No source coin (or measurement failed): a clean scale/fade reveal. */
    private fun revealHero() {
        val hero = binding.heroMedallion
        hero.alpha = 0f
        hero.scaleX = 0.62f
        hero.scaleY = 0.62f
        hero.animate().alpha(1f).setDuration(220).start()
        springTo(hero, DynamicAnimation.SCALE_X, 1f, 300f, 0.55f)
        springTo(hero, DynamicAnimation.SCALE_Y, 1f, 300f, 0.55f)
        hero.postDelayed({ _binding?.heroMedallion?.shimmerOnce() }, 380)
    }

    private fun cascadeContent() {
        cascadeItems.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dpF(22f)
            v.postDelayed({
                if (_binding == null) return@postDelayed
                v.animate().alpha(1f).setDuration(260).start()
                SpringAnimation(v, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                    spring.stiffness = 460f
                    spring.dampingRatio = 0.82f
                    setStartVelocity(-dpF(140f))
                    start()
                }
                // As the figure arrives, mint it: count the value up from zero, then
                // a tiny scale-pop + haptic tick the instant it lands, so the credit
                // reads as *struck* rather than printed.
                if (v === binding.rewardAmountBlock) {
                    binding.rewardAmountView.onCountSettled = {
                        _binding?.rewardAmountBlock?.let { block ->
                            block.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            block.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120)
                                .withEndAction {
                                    block.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                                }.start()
                        }
                    }
                    binding.rewardAmountView.setAmount(rewardAmount.toInt())
                }
            }, 70L + i * 55L)
        }
    }

    private fun springTo(
        view: View,
        property: DynamicAnimation.ViewProperty,
        finalValue: Float,
        stiffness: Float,
        damping: Float
    ) {
        SpringAnimation(view, property, finalValue).apply {
            spring.stiffness = stiffness
            spring.dampingRatio = damping
            start()
        }
    }

    private fun animatorsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            android.animation.ValueAnimator.areAnimatorsEnabled()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * resources.displayMetrics.density

    // ── Rewarded ad + wallet credit (ported, behaviour unchanged) ──────────────

    private fun preloadRewardedAd() {
        if (isLoadingRewardedAd || rewardedAd != null) return
        val appContext = requireContext().applicationContext
        val adUnitId = AdMobManager.rewardedAdUnitId
        isLoadingRewardedAd = true

        val initialized = AdMobManager.initializeIfEnabled(requireContext()) {
            if (isViewDestroyed) {
                isLoadingRewardedAd = false
                return@initializeIfEnabled
            }

            RewardedAd.load(
                appContext,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isLoadingRewardedAd = false
                        rewardedAd = ad
                        maybeShowRewardedAdIfPending()
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        logRewardedAdLoadFailure(adError)
                        isLoadingRewardedAd = false
                        rewardedAd = null
                        handleRewardedAdLoadFailure(rewardedAdLoadFailureMessage(adError))
                    }
                }
            )
        }

        if (!initialized) {
            isLoadingRewardedAd = false
            handleRewardedAdLoadFailure("Rewards are temporarily unavailable. Please try again later.")
        }
    }

    private fun showRewardVideo() {
        val ad = rewardedAd
        if (ad == null) {
            pendingShowRewardedAd = true
            setRewardedAdLoading(true)
            preloadRewardedAd()
            return
        }
        pendingShowRewardedAd = false
        setRewardedAdLoading(false)
        showRewardedAd(ad)
    }

    private fun maybeShowRewardedAdIfPending() {
        if (!pendingShowRewardedAd) return
        val ad = rewardedAd ?: return
        if (isViewDestroyed || !isAdded) return
        pendingShowRewardedAd = false
        setRewardedAdLoading(false)
        showRewardedAd(ad)
    }

    private fun setRewardedAdLoading(isLoading: Boolean) {
        if (_binding == null) return
        if (primaryButtonIdleLabel == null) primaryButtonIdleLabel = binding.btnPrimary.text

        binding.btnPrimary.isEnabled = !isLoading
        binding.btnPrimary.alpha = if (isLoading) 0.7f else 1f

        if (isLoading) {
            binding.btnPrimary.text = "Preparing video…"
            binding.btnPrimary.icon = buildButtonSpinner()
            binding.btnPrimary.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.reward_button_text)
            )
            binding.btnPrimary.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        } else {
            binding.btnPrimary.text = primaryButtonIdleLabel
            binding.btnPrimary.setIconResource(R.drawable.ic_reward_watch_claim)
            binding.btnPrimary.iconTint = null
        }
    }

    private fun buildButtonSpinner(): IndeterminateDrawable<CircularProgressIndicatorSpec> {
        val density = resources.displayMetrics.density
        val spec = CircularProgressIndicatorSpec(
            requireContext(), null, 0,
            com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
        ).apply {
            indicatorInset = 0
            indicatorSize = (20 * density).toInt()
            trackThickness = (2 * density).toInt()
        }
        return IndeterminateDrawable.createCircularDrawable(requireContext(), spec)
    }

    private fun showRewardedAd(ad: RewardedAd) {
        if (!isAdded) return
        if (_binding != null) {
            binding.btnPrimary.isEnabled = false
            binding.btnPrimary.alpha = 0.6f
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                if (!isRewardEarned) dismissAllowingStateLoss()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.w(
                    TAG,
                    "Rewarded ad failed to show: code=${adError.code}, " +
                        "domain=${adError.domain}, message=${adError.message}"
                )
                rewardedAd = null
                preloadRewardedAd()
                setRewardedAdLoading(false)
                Toast.makeText(
                    requireContext(),
                    "We could not open the reward video. Please try again in a moment.",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
            }
        }
        ad.show(requireActivity()) {
            isRewardEarned = true
            creditRewardToWallet(rewardAmount)
            Toast.makeText(
                requireContext(),
                "Reward earned! Processing your wallet top-up...",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleRewardedAdLoadFailure(message: String) {
        if (!pendingShowRewardedAd) return
        pendingShowRewardedAd = false
        setRewardedAdLoading(false)
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun logRewardedAdLoadFailure(adError: LoadAdError) {
        Log.w(
            TAG,
            "Rewarded ad failed to load: code=${adError.code}, " +
                "domain=${adError.domain}, message=${adError.message}, " +
                "responseInfo=${adError.responseInfo}"
        )
    }

    private fun rewardedAdLoadFailureMessage(adError: LoadAdError): String {
        return when (adError.code) {
            AdRequest.ERROR_CODE_NO_FILL,
            AdRequest.ERROR_CODE_MEDIATION_NO_FILL ->
                "No reward video is available right now. Please try again in a few minutes."
            AdRequest.ERROR_CODE_NETWORK_ERROR ->
                "We could not load the video. Check your internet connection and try again."
            AdRequest.ERROR_CODE_INVALID_REQUEST,
            AdRequest.ERROR_CODE_APP_ID_MISSING,
            AdRequest.ERROR_CODE_INVALID_AD_STRING ->
                "Rewards are not available in this app version yet. Please update or try again later."
            else -> "We could not load the reward video. Please try again in a moment."
        }
    }

    private fun creditRewardToWallet(amount: Double) {
        if (!isAdded) return
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            setRewardLoading(true)
            try {
                val result = withContext(Dispatchers.IO) { WalletRepository(ctx).topUpWallet(amount) }
                result.fold(
                    onSuccess = { payload -> showRewardDialog(amount, payload["balance"] as? Double) },
                    onFailure = { error ->
                        Toast.makeText(
                            ctx,
                            "Reward earned but could not be added: ${error.message ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(ctx, "Reward earned but could not be added: ${e.message}", Toast.LENGTH_LONG).show()
                dismissAllowingStateLoss()
            } finally {
                setRewardLoading(false)
            }
        }
    }

    private fun setRewardLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.btnPrimary.isEnabled = !isLoading
        binding.btnPrimary.alpha = if (isLoading) 0.6f else 1f
    }

    private fun showRewardDialog(amount: Double, newBalance: Double?) {
        val activityContext = activity ?: return
        val rewardIntent = Intent(activityContext, MainContainerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainContainerActivity.EXTRA_SHOW_WALLET_TRANSACTION, true)
            putExtra(MainContainerActivity.EXTRA_WALLET_TRANSACTION_TITLE, "Ad Top-Up")
            putExtra(
                MainContainerActivity.EXTRA_WALLET_TRANSACTION_AMOUNT,
                String.format(java.util.Locale.getDefault(), "%.0f", amount)
            )
            putExtra(MainContainerActivity.EXTRA_WALLET_TRANSACTION_IS_CREDIT, true)
            putExtra(MainContainerActivity.EXTRA_WALLET_TRANSACTION_ROUTE_TO_WALLET, true)
        }
        activityContext.startActivity(rewardIntent)
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewDestroyed = true
        pendingShowRewardedAd = false
        isLoadingRewardedAd = false
        primaryButtonIdleLabel = null
        flightSpringY?.cancel()
        flightSpringY = null
        haloBreatheAnim?.cancel()
        haloBreatheAnim = null
        flightOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        flightOverlay = null
        onCoinAirborne = null
        _binding = null
        rewardedAd = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        pendingShowRewardedAd = false
        onDismissed?.invoke()
        onDismissed = null
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG = "RewardBottomSheet"

        /**
         * @param startRect screen [x, y, sizePx] of the coin to fly in from, or
         *                  null to reveal with a scale/fade instead of a flight.
         */
        fun newInstance(startRect: IntArray? = null): RewardBottomSheet =
            RewardBottomSheet().apply { this.startRect = startRect }
    }
}
