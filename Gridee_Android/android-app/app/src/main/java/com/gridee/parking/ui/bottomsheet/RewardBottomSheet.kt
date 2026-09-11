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
import android.os.SystemClock
import com.gridee.parking.utils.AppLog
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
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
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
import com.gridee.parking.databinding.BottomSheetRewardBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.motion.AnimatorSettingsCompat
import com.gridee.parking.ui.views.RewardCoinView
import com.gridee.parking.ui.wallet.OneShotGate
import com.gridee.parking.ui.wallet.RewardCreditCoordinator
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.utils.AdConsentManager
import com.gridee.parking.utils.AdMobManager
import com.gridee.parking.utils.AdRevenueAnalytics
import com.gridee.parking.utils.DailyRewardState
import com.gridee.parking.utils.InAppReviewManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val ARG_REWARD_START_RECT = "reward.start_rect"
private const val ARG_REWARD_ENTRY_POINT = "reward.entry_point"
private const val STATE_REWARD_SOURCE_GEOMETRY_CONSUMED = "reward_source_geometry_consumed"
private const val STATE_REWARD_COIN_AIRBORNE_RESULT_SENT = "reward_coin_airborne_result_sent"
private const val STATE_REWARD_DISMISS_RESULT_SENT = "reward_dismiss_result_sent"

internal data class RewardBottomSheetLaunchConfig(
    val startRect: IntArray? = null,
    val entryPoint: String = RewardBottomSheet.ENTRY_POINT_UNKNOWN,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putIntArray(ARG_REWARD_START_RECT, startRect?.copyOf())
        putString(ARG_REWARD_ENTRY_POINT, entryPoint)
    }

    companion object {
        fun from(bundle: Bundle): RewardBottomSheetLaunchConfig =
            RewardBottomSheetLaunchConfig(
                startRect = sanitizeStartRect(bundle.getIntArray(ARG_REWARD_START_RECT)),
                entryPoint = bundle.getString(ARG_REWARD_ENTRY_POINT)
                    ?.takeIf(String::isNotBlank)
                    ?: RewardBottomSheet.ENTRY_POINT_UNKNOWN,
            )

        private fun sanitizeStartRect(rect: IntArray?): IntArray? = rect
            ?.takeIf { it.size >= 3 && it[2] > 0 }
            ?.copyOfRange(0, 3)
    }

    /** A screen-space source rect is valid only for the activity/view tree that captured it. */
    fun forFragmentCreation(restoringSavedInstance: Boolean): RewardBottomSheetLaunchConfig =
        if (restoringSavedInstance) copy(startRect = null) else this
}

internal data class RewardBottomSheetOneShotState(
    val sourceGeometryConsumed: Boolean = false,
    val coinAirborneResultSent: Boolean = false,
    val dismissResultSent: Boolean = false,
) {
    fun writeTo(bundle: Bundle) {
        bundle.putBoolean(STATE_REWARD_SOURCE_GEOMETRY_CONSUMED, sourceGeometryConsumed)
        bundle.putBoolean(STATE_REWARD_COIN_AIRBORNE_RESULT_SENT, coinAirborneResultSent)
        bundle.putBoolean(STATE_REWARD_DISMISS_RESULT_SENT, dismissResultSent)
    }

    companion object {
        fun from(bundle: Bundle?): RewardBottomSheetOneShotState =
            RewardBottomSheetOneShotState(
                sourceGeometryConsumed = bundle?.getBoolean(
                    STATE_REWARD_SOURCE_GEOMETRY_CONSUMED,
                    false,
                ) ?: false,
                coinAirborneResultSent = bundle?.getBoolean(
                    STATE_REWARD_COIN_AIRBORNE_RESULT_SENT,
                    false,
                ) ?: false,
                dismissResultSent = bundle?.getBoolean(
                    STATE_REWARD_DISMISS_RESULT_SENT,
                    false,
                ) ?: false,
            )
    }
}

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

    // ── Rewarded-ad / wallet state ───────────────────────────────────────────
    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewardedAd = false
    private var pendingShowRewardedAd = false
    private var primaryButtonIdleLabel: CharSequence? = null
    private var isViewDestroyed = false
    private var isRewardEarned = false
    private val rewardAmount = 10.0
    private val rewardAdShowing = AtomicBoolean(false)

    /**
     * Where this sheet was opened from, reported on every rewarded event. Home and Wallet are
     * different populations — Home opens off the daily coin nudge, Wallet off a deliberate visit
     * — and their claim rates have no reason to match.
     */
    private var entryPoint: String = ENTRY_POINT_UNKNOWN

    /** Set once this sheet has joined the process-wide consent request, so it cannot recurse. */
    private var consentRetryRequested = false

    /**
     * Set once that request has come back, whatever it decided. Without it, a consent flow that
     * resolves as *denied* before the user taps Watch would look identical to one still in
     * flight, and the tap would be absorbed as "a retry is pending" — leaving the button
     * spinning on a retry that is never coming.
     */
    private var consentRetryResolved = false

    /** Elapsed-realtime stamp of when the held ad finished loading, for the expiry check. */
    private var rewardedAdLoadedAtMs = 0L

    /**
     * The winning adapter for the currently held ad, captured at load. Read from `responseInfo`
     * at show time instead would be too late on the paths where the ad is cleared first.
     */
    private var loadedAdSourceName: String? = null

    /** Whether the ad we are holding, or held, ever reached [FullScreenContentCallback.onAdImpression]. */
    private val rewardImpressionLogged = AtomicBoolean(false)

    /**
     * Why the most recent attempt failed, if one did. Discards are reported once, at teardown,
     * rather than at each failure: a load can fail and a retry can then succeed, and a sheet that
     * ends in an impression is not a discard no matter how many attempts it took to get there.
     */
    private var lastRewardFailureReason: String? = null

    /** Guards the single discard event so a dismiss following onDestroyView cannot double-count. */
    private val rewardDiscardLogged = AtomicBoolean(false)
    private val rewardCreditStarted = AtomicBoolean(false)
    private var rewardEventId = newRewardEventId()

    // ── Entrance choreography ────────────────────────────────────────────────
    private var flightOverlay: RewardCoinView? = null
    private var flightSize = 0
    private var flightSpringY: SpringAnimation? = null
    private var hasStartedEntrance = false
    private var hasFinishedEntrance = false
    private var hasLanded = false
    private var haloBreatheAnim: ObjectAnimator? = null
    private var useSettledRestoredPresentation = false
    private var sourceGeometryConsumed = false
    private var coinAirborneResultSent = false
    private var dismissResultSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        val restoringSavedInstance = savedInstanceState != null
        useSettledRestoredPresentation = restoringSavedInstance
        val launchConfig = (savedInstanceState?.getBundle(STATE_LAUNCH_CONFIG)
            ?.let(RewardBottomSheetLaunchConfig::from)
            ?: arguments?.let(RewardBottomSheetLaunchConfig::from))
            ?.forFragmentCreation(restoringSavedInstance)
        launchConfig?.let { config ->
            startRect = config.startRect?.copyOf()
            entryPoint = config.entryPoint
        }
        // Source coordinates belong to the old window. A restored open sheet starts settled and
        // Home reconstructs its socket state by finding this sheet, rather than replaying flight.
        val oneShotState = RewardBottomSheetOneShotState.from(savedInstanceState)
        sourceGeometryConsumed = restoringSavedInstance || oneShotState.sourceGeometryConsumed
        coinAirborneResultSent = oneShotState.coinAirborneResultSent
        dismissResultSent = oneShotState.dismissResultSent
        rewardEventId = savedInstanceState?.getString(STATE_REWARD_EVENT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: rewardEventId
        // Compatibility with state written by builds before launch configuration moved to args.
        entryPoint = savedInstanceState?.getString(STATE_ENTRY_POINT)
            ?.takeIf { it.isNotBlank() }
            ?: entryPoint
        preloadRewardedAd()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_REWARD_EVENT_ID, rewardEventId)
        outState.putString(STATE_ENTRY_POINT, entryPoint)
        RewardBottomSheetOneShotState(
            sourceGeometryConsumed = sourceGeometryConsumed,
            coinAirborneResultSent = coinAirborneResultSent,
            dismissResultSent = dismissResultSent,
        ).writeTo(outState)
        outState.putBundle(
            STATE_LAUNCH_CONFIG,
            RewardBottomSheetLaunchConfig(startRect, entryPoint).toBundle(),
        )
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightNavigationBars = !isDark
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

        applyDailyCapState()
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
        if (useSettledRestoredPresentation) {
            // This dialog was already open. Re-enter fully settled: replaying the source flight
            // would use coordinates from the destroyed window and make rotation/theme changes
            // look like a second user action.
            hasStartedEntrance = true
            hasFinishedEntrance = true
            hasLanded = true
            binding.heroMedallion.alpha = 1f
            binding.chamberGlow.alpha = 1f
            cascadeItems.forEach { it.alpha = 1f }
            binding.rewardAmountView.setAmount(rewardAmount.toInt(), animate = false)
            return
        }
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

        // Consume source geometry exactly once. It is window-relative and must never be replayed
        // after this view tree changes; keep only this local copy for the current choreography.
        val launchRect = startRect
        startRect = null
        if (launchRect != null) sourceGeometryConsumed = true

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
        val rect = launchRect
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
        // to recede its header coin into a socket so the lift-off has no gap. A
        // FragmentResult survives host view recreation; an assigned lambda does not.
        if (!coinAirborneResultSent) {
            coinAirborneResultSent = true
            publishHostResult(RESULT_KEY_COIN_AIRBORNE)
        }

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

    private fun animatorsEnabled(): Boolean {
        val currentContext = context ?: return false
        return AnimatorSettingsCompat.areEnabled(currentContext)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * resources.displayMetrics.density

    // ── Rewarded ad + wallet credit (ported, behaviour unchanged) ──────────────

    /**
     * Whether this user has spent today's rewards.
     *
     * Gated on the remote switch so the cap can be lifted without a release. Note the switch's
     * inverted sense: off means uncapped, which is the pre-cap behaviour.
     */
    private fun isDailyCapReached(): Boolean {
        val ctx = context ?: return false
        if (!RemoteConfigManager.isRewardedDailyCapEnabled()) return false
        return DailyRewardState.hasReachedDailyCap(ctx)
    }

    /**
     * Reflects the remaining allowance in the sheet. The count is stated plainly rather than
     * hidden until it runs out — a CTA that silently stops working reads as a bug, and a user
     * who knows the rule can pace themselves against it.
     */
    private fun applyDailyCapState() {
        if (_binding == null) return
        val ctx = context ?: return
        val capEnforced = RemoteConfigManager.isRewardedDailyCapEnabled()
        if (!capEnforced) {
            binding.tvRewardNote.setText(R.string.a_new_reward_every_day)
            return
        }
        val remaining = DailyRewardState.rewardsRemainingToday(ctx)
        if (remaining <= 0) {
            binding.btnPrimary.isEnabled = false
            binding.btnPrimary.alpha = 0.55f
            binding.btnPrimary.text = getString(R.string.reward_cap_reached_cta)
            primaryButtonIdleLabel = binding.btnPrimary.text
            binding.tvRewardNote.text =
                getString(R.string.reward_cap_reached_note, DailyRewardState.DAILY_REWARD_CAP)
        } else {
            binding.tvRewardNote.text = getString(
                R.string.reward_rewards_left_today,
                remaining,
                DailyRewardState.DAILY_REWARD_CAP
            )
        }
    }

    private fun preloadRewardedAd() {
        if (isLoadingRewardedAd || rewardedAd != null) return
        // A capped user will not be shown an ad, so a request here could never become an
        // impression. Run enough of them and the placement's request-to-impression ratio falls
        // for a reason unrelated to demand, which is a signal mediation partners bid down on.
        // Recording the reason as well makes the discard event say the cap was what bound.
        if (isDailyCapReached()) {
            lastRewardFailureReason = DISCARD_DAILY_CAP_REACHED
            return
        }
        // Mediated networks such as Meta require an Activity context for rewarded requests.
        // The request still uses the AdMob ad-unit ID and the normal Google Mobile Ads API.
        val activity = requireActivity()
        val adUnitId = AdMobManager.rewardedAdUnitId
        // Every callback below can outlive this fragment's view, so telemetry reports through the
        // application context rather than requireContext().
        val appContext = activity.applicationContext
        val requestEntryPoint = entryPoint
        isLoadingRewardedAd = true

        val initialized = AdMobManager.initializeIfEnabled(requireContext()) {
            if (isViewDestroyed) {
                isLoadingRewardedAd = false
                return@initializeIfEnabled
            }

            RewardedAd.load(
                activity,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isLoadingRewardedAd = false
                        val adSourceName = ad.responseInfo
                            .loadedAdapterResponseInfo
                            ?.adSourceName
                            ?.takeIf { it.isNotBlank() }
                        loadedAdSourceName = adSourceName
                        rewardedAdLoadedAtMs = SystemClock.elapsedRealtime()
                        AdRevenueAnalytics.logRewardedLoad(
                            appContext,
                            loaded = true,
                            adSourceName = adSourceName,
                            entryPoint = requestEntryPoint
                        )
                        // Set at load rather than at show: some mediation adapters report the
                        // paid event as soon as the auction resolves, and the show path clears
                        // `rewardedAd` before the callback would otherwise be attached.
                        ad.setOnPaidEventListener { value ->
                            AdRevenueAnalytics.logRewardedPaidEvent(
                                appContext,
                                value.valueMicros,
                                value.currencyCode,
                                value.precisionType,
                                adSourceName,
                                requestEntryPoint
                            )
                        }
                        rewardedAd = ad
                        maybeShowRewardedAdIfPending()
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        logRewardedAdLoadFailure(adError)
                        isLoadingRewardedAd = false
                        rewardedAd = null
                        loadedAdSourceName = null
                        rewardedAdLoadedAtMs = 0L
                        AdRevenueAnalytics.logRewardedLoad(
                            appContext,
                            loaded = false,
                            adSourceName = null,
                            entryPoint = requestEntryPoint,
                            errorCode = adError.code
                        )
                        lastRewardFailureReason = DISCARD_LOAD_FAILED
                        handleRewardedAdLoadFailure(rewardedAdLoadFailureMessage(adError))
                    }
                }
            )
        }

        if (!initialized) {
            isLoadingRewardedAd = false
            if (!joinConsentFlowAndRetry()) {
                lastRewardFailureReason = DISCARD_ADS_UNAVAILABLE
                handleRewardedAdLoadFailure("Rewards are temporarily unavailable. Please try again later.")
            }
        }
    }

    private fun showRewardVideo() {
        if (isDailyCapReached()) {
            lastRewardFailureReason = DISCARD_DAILY_CAP_REACHED
            applyDailyCapState()
            return
        }
        val ad = rewardedAd?.takeUnless { isRewardedAdExpired() }
        if (ad == null) {
            // Drop an expired ad rather than showing it: it would fail at show time and surface
            // as "we could not open the reward" to a user who did nothing wrong.
            if (rewardedAd != null) discardExpiredRewardedAd()
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
        val ad = rewardedAd?.takeUnless { isRewardedAdExpired() } ?: return
        if (isViewDestroyed || !isAdded) return
        pendingShowRewardedAd = false
        setRewardedAdLoading(false)
        showRewardedAd(ad)
    }

    /**
     * Joins the in-flight consent request rather than giving up on the reward.
     *
     * UMP is refreshed once per process from `MainContainerActivity.onResume`, and
     * `AdConsentManager.canRequestAds` stays false until that network call returns. The sheet
     * loads its ad on open, so anyone who taps the home coin during a cold start over a slow
     * connection was being told "Rewards are temporarily unavailable" for a race they had no
     * part in — on the highest-eCPM placement in the app. Both native placements already wait
     * for consent this way; rewarded was the only one that simply gave up.
     *
     * @return true when the failure has been absorbed and a retry is pending, false when the
     *         caller should report it — consent is settled and something else is the blocker,
     *         such as the `adMob` or `rewards` switch being off.
     */
    private fun joinConsentFlowAndRetry(): Boolean {
        val host = activity?.takeIf { !it.isFinishing && !it.isDestroyed } ?: return false
        // Consent already resolved, so it is not what blocked this load.
        if (AdConsentManager.canRequestAds(host)) return false
        // Consent has already come back and did not allow ads. Nothing further is pending, so
        // this is a real failure and the caller must report it rather than wait.
        if (consentRetryResolved) return false
        // Already waiting on the request started below. The pending-show machinery covers the
        // user who taps Watch inside the window: they keep the spinner, and the load that
        // follows completion is shown by maybeShowRewardedAdIfPending().
        if (consentRetryRequested) return true
        consentRetryRequested = true
        AdConsentManager.gatherConsent(host) { allowed ->
            // Recorded before the lifecycle guard: the flow is settled either way, and a sheet
            // that is briefly detached must not come back thinking one is still in flight.
            consentRetryResolved = true
            if (isViewDestroyed || !isAdded) return@gatherConsent
            if (allowed) {
                preloadRewardedAd()
            } else {
                lastRewardFailureReason = DISCARD_CONSENT_DENIED
                handleRewardedAdLoadFailure(
                    "Rewards are unavailable until you allow personalised ads."
                )
            }
        }
        return true
    }

    private fun isRewardedAdExpired(): Boolean =
        rewardedAdLoadedAtMs > 0L &&
            SystemClock.elapsedRealtime() - rewardedAdLoadedAtMs >= REWARDED_MAX_AGE_MS

    private fun discardExpiredRewardedAd() {
        rewardedAd = null
        loadedAdSourceName = null
        rewardedAdLoadedAtMs = 0L
        lastRewardFailureReason = DISCARD_AD_EXPIRED
    }

    private fun setRewardedAdLoading(isLoading: Boolean) {
        if (_binding == null) return
        if (primaryButtonIdleLabel == null) primaryButtonIdleLabel = binding.btnPrimary.text

        // A capped button must stay disabled through every state restore. Both loading helpers
        // otherwise re-enable it on their way back to idle, which would hand back a fourth watch.
        val capped = isDailyCapReached()
        binding.btnPrimary.isEnabled = !isLoading && !capped
        binding.btnPrimary.alpha = when {
            isLoading -> 0.7f
            capped -> 0.55f
            else -> 1f
        }

        if (isLoading) {
            binding.btnPrimary.text = getString(R.string.preparing_video)
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
        if (!rewardAdShowing.compareAndSet(false, true)) return
        if (_binding != null) {
            binding.btnPrimary.isEnabled = false
            binding.btnPrimary.alpha = 0.6f
        }
        // Scoped to this exact ad display so even a duplicated mediation callback can submit
        // only one wallet mutation.
        val callbackGate = OneShotGate()
        // Full-screen callbacks routinely arrive after this fragment's view is gone, so they
        // report through the application context and never touch the binding directly.
        val appContext = requireContext().applicationContext
        val showEntryPoint = entryPoint
        val adSourceName = loadedAdSourceName
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardAdShowing.set(false)
                rewardedAd = null
                if (!isRewardEarned) dismissAllowingStateLoss()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                rewardAdShowing.set(false)
                AppLog.w(TAG) { "Rewarded ad failed to show (code=${adError.code})" }
                rewardedAd = null
                loadedAdSourceName = null
                rewardedAdLoadedAtMs = 0L
                lastRewardFailureReason = DISCARD_SHOW_FAILED
                preloadRewardedAd()
                setRewardedAdLoading(false)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.we_could_not_open_the_reward),
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
                rewardedAdLoadedAtMs = 0L
            }

            override fun onAdImpression() {
                // The claim-rate denominator. Counted here rather than in onAdShowed because a
                // shown ad that never registers an impression earns nothing.
                if (rewardImpressionLogged.compareAndSet(false, true)) {
                    AdRevenueAnalytics.logRewardedImpression(
                        appContext,
                        adSourceName,
                        showEntryPoint
                    )
                }
            }

            override fun onAdClicked() {
                AdRevenueAnalytics.logRewardedClick(appContext, adSourceName)
            }
        }
        ad.show(requireActivity()) rewardCallback@{
            if (!callbackGate.tryAcquire()) return@rewardCallback
            isRewardEarned = true
            // Recorded before the credit call and committed synchronously: this is the last
            // moment the app fully controls, and a count lost to process death here would hand
            // back a free extra reward.
            DailyRewardState.recordRewardClaimed(appContext)
            // The claim-rate numerator. Against rewarded_impression this is the share of shown
            // ads that actually complete, which every cost-per-reward figure so far has assumed
            // to be 100%.
            AdRevenueAnalytics.logRewardedEarned(
                appContext,
                adSourceName,
                showEntryPoint,
                rewardAmount
            )
            creditRewardToWallet(rewardAmount)
            Toast.makeText(
                requireContext(),
                getString(R.string.reward_earned_processing_your_wallet_top),
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
        AppLog.w(TAG) { "Rewarded ad failed to load (code=${adError.code})" }
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
        if (!rewardCreditStarted.compareAndSet(false, true)) return
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            setRewardLoading(true)
            try {
                val result = RewardCreditCoordinator.credit(ctx, rewardEventId, amount)
                result.fold(
                    onSuccess = {
                        showRewardDialog(amount)
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            ctx,
                            "Reward earned but could not be added: ${error.message ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                        // There is no safe client-side idempotency key for this legacy endpoint,
                        // so a failed reward POST must not be retried automatically or by a tap.
                        dismissAllowingStateLoss()
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        val capped = isDailyCapReached()
        binding.btnPrimary.isEnabled = !isLoading && !capped
        binding.btnPrimary.alpha = when {
            isLoading -> 0.6f
            capped -> 0.55f
            else -> 1f
        }
    }

    private fun showRewardDialog(amount: Double) {
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

    /**
     * Reports, once, that this sheet was opened and produced no impression.
     *
     * Rotation is deliberately excluded: `screenOrientation` is unlocked and rotation is not in
     * this activity's `configChanges`, so a turn of the device tears the sheet down and rebuilds
     * it. That is one continuous reward attempt to the user, and counting it as an abandonment
     * would inflate the discard rate with something the placement did not do wrong.
     */
    private fun reportRewardedDiscardIfUnmonetized() {
        if (activity?.isChangingConfigurations == true) return
        if (rewardImpressionLogged.get()) return
        if (!rewardDiscardLogged.compareAndSet(false, true)) return
        val appContext = context?.applicationContext ?: return
        AdRevenueAnalytics.logRewardedDiscarded(
            appContext,
            lastRewardFailureReason ?: DISCARD_CLOSED_WITHOUT_WATCH,
            entryPoint
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportRewardedDiscardIfUnmonetized()
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
        _binding = null
        rewardedAd = null
        rewardedAdLoadedAtMs = 0L
    }

    override fun onDismiss(dialog: DialogInterface) {
        pendingShowRewardedAd = false
        if (activity?.isChangingConfigurations != true && !dismissResultSent) {
            dismissResultSent = true
            publishHostResult(RESULT_KEY_DISMISSED)
        }
        super.onDismiss(dialog)
    }

    private fun publishHostResult(requestKey: String) {
        if (!isAdded) return
        setFragmentResult(
            requestKey,
            Bundle().apply { putString(RESULT_ENTRY_POINT, entryPoint) },
        )
    }

    /** Used by a recreated Home view to restore the socket without replaying stale flight. */
    internal fun wasOpenedFrom(entryPoint: String): Boolean {
        val argumentEntryPoint = arguments
            ?.let(RewardBottomSheetLaunchConfig::from)
            ?.entryPoint
        return (argumentEntryPoint ?: this.entryPoint) == entryPoint
    }

    /**
     * Atomically admits one dialog for [TAG]. `showNow` closes the same-loop double-tap window
     * left by `show()`, while the state checks avoid transactions after FragmentManager saved.
     */
    fun showIfPossible(fragmentManager: FragmentManager): Boolean {
        if (fragmentManager.isDestroyed || fragmentManager.isStateSaved) return false
        if (fragmentManager.findFragmentByTag(TAG) != null) return false
        showNow(fragmentManager, TAG)
        return true
    }

    companion object {
        const val TAG = "RewardBottomSheet"
        const val RESULT_KEY_COIN_AIRBORNE = "reward_bottom_sheet.coin_airborne"
        const val RESULT_KEY_DISMISSED = "reward_bottom_sheet.dismissed"
        const val RESULT_ENTRY_POINT = "reward_bottom_sheet.entry_point"
        private const val STATE_REWARD_EVENT_ID = "reward_event_id"
        private const val STATE_ENTRY_POINT = "reward_entry_point"
        private const val STATE_LAUNCH_CONFIG = "reward_launch_config"

        const val ENTRY_POINT_HOME = "home"
        const val ENTRY_POINT_WALLET = "wallet"
        internal const val ENTRY_POINT_UNKNOWN = "unknown"

        // Why an opened sheet never produced an impression.
        private const val DISCARD_LOAD_FAILED = "load_failed"
        private const val DISCARD_ADS_UNAVAILABLE = "ads_unavailable"
        private const val DISCARD_SHOW_FAILED = "show_failed"
        private const val DISCARD_CLOSED_WITHOUT_WATCH = "closed_without_watch"
        private const val DISCARD_DAILY_CAP_REACHED = "daily_cap_reached"
        private const val DISCARD_AD_EXPIRED = "ad_expired"
        private const val DISCARD_CONSENT_DENIED = "consent_denied"

        /**
         * How long a loaded rewarded ad stays usable.
         *
         * AdMob expires rewarded ads roughly an hour after load, and an expired one does not
         * fail quietly — it reaches [FullScreenContentCallback.onAdFailedToShowFullScreenContent]
         * and the user is told the reward could not open. The sheet loads its ad on open, so any
         * session where the app is backgrounded with the sheet up and returned to much later
         * lands in that window. 55 minutes leaves margin under the hour, matching the guard
         * `AdMobManager` already applies to the booking-transition interstitial.
         */
        private const val REWARDED_MAX_AGE_MS = 55L * 60L * 1000L

        private fun newRewardEventId(): String = "rewarded-ad:${UUID.randomUUID()}"

        /**
         * @param startRect  screen [x, y, sizePx] of the coin to fly in from, or
         *                   null to reveal with a scale/fade instead of a flight.
         * @param entryPoint which surface opened the sheet, reported on every rewarded event.
         */
        fun newInstance(
            startRect: IntArray? = null,
            entryPoint: String = ENTRY_POINT_UNKNOWN
        ): RewardBottomSheet = RewardBottomSheet().apply {
            arguments = RewardBottomSheetLaunchConfig(startRect, entryPoint).toBundle()
        }
    }
}
