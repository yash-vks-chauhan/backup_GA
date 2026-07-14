package com.gridee.parking.ui.bottomsheet

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetWelcomeGiftBinding

class WelcomeGiftBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetWelcomeGiftBinding? = null
    private val binding get() = _binding!!

    private var onDismissAction: (() -> Unit)? = null

    // ── Timing Constants (ms) ──────────────────────────────────────────
    // Every value below is hand-tuned so each element enters in a
    // natural cascade — fast enough to feel snappy, slow enough that
    // the eye can follow each beat.
    //
    //  0ms        Sheet visible
    //  80ms       Outer glow begins fade-in
    //  160ms      Inner glow begins fade-in
    //  200ms      Gift box pops in (overshoot, 600ms)
    //  650ms      Gift box lands → THUD haptic + confetti burst (60 particles, 3.2s)
    //  ~750ms     Confetti haptic pulse #1 (CLOCK_TICK)
    //  ~900ms     Confetti haptic pulse #2
    //  700ms      Title fades in
    //  850ms      Coin chip springs in (physics bounce) + TICK haptic
    //  950ms      Counter starts (0→25 over 700ms, haptic every 5)
    //  1050ms     Subtitle fades in
    //  1200ms     Button fades in
    //  1450ms     Bottom fog fades in (particles entering fog zone)
    //  1450ms     Note fades in
    //  1650ms     Counter hits 25 → THUD haptic + scale punch
    //  ~3200ms    Confetti settles, fog fades out
    // ────────────────────────────────────────────────────────────────────

    companion object {
        const val TAG = "WelcomeGiftBottomSheet"
        private const val ARG_COIN_AMOUNT = "arg_coin_amount"

        // Entrance delays
        const val DELAY_OUTER_GLOW    =   80L
        const val DELAY_INNER_GLOW    =  160L
        const val DELAY_GIFT_POP      =  200L
        const val DELAY_CONFETTI      =  650L  // fires when gift box overshoot settles
        const val DELAY_TITLE         =  700L
        const val DELAY_COIN_CHIP     =  850L
        const val DELAY_COUNTER       =  950L
        const val DELAY_SUBTITLE      = 1050L
        const val DELAY_BUTTON        = 1200L
        const val DELAY_NOTE          = 1450L

        // Durations
        const val DUR_GLOW_FADE       =  800L
        const val DUR_GIFT_POP        =  600L
        const val DUR_CONFETTI        = 3200L  // long, gentle fall to bottom
        const val DUR_TITLE_FADE      =  450L
        const val DUR_CHIP_SPRING     =  500L  // visual; spring physics drives actual duration
        const val DUR_COUNTER         =  700L
        const val DUR_SUBTITLE_FADE   =  450L
        const val DUR_BUTTON_FADE     =  450L
        const val DUR_NOTE_FADE       =  350L
        const val DUR_GLOW_PULSE      = 3000L

        fun newInstance(coinAmount: Int = 50): WelcomeGiftBottomSheet {
            val fragment = WelcomeGiftBottomSheet()
            fragment.arguments = Bundle().apply {
                putInt(ARG_COIN_AMOUNT, coinAmount)
            }
            return fragment
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog

            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                sheet.fitsSystemWindows = false
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params
            }

            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true

            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false

                val wic = WindowCompat.getInsetsController(window, window.decorView)
                val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                wic.isAppearanceLightNavigationBars = !isDarkMode

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                // Glassmorphism blur (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50
                    window.attributes = window.attributes
                }
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetWelcomeGiftBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInsets()
        setupBehaviors()
        setupUI()

        // Set initial hidden state for ALL animated elements
        prepareInitialState()

        // Kick off the carefully timed entrance
        playEntranceAnimations()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            binding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            binding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ───────────────────────────────────────────────────────────

    private fun setupInsets() {
        val density = binding.root.context.resources.displayMetrics.density
        val baseBottomPadding = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPadding = (8 * density).toInt()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottomPadding + extraPadding + bars.bottom)
            insets
        }
    }

    private fun setupBehaviors() {
        val behavior = (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior
        behavior?.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED ||
                    newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
                ) {
                    bottomSheet.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    private fun setupUI() {
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnStartExploring.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onDismissAction?.invoke()
            dismiss()
        }
    }

    fun setOnDismissAction(action: () -> Unit) {
        this.onDismissAction = action
    }

    // ── Initial State (everything hidden) ───────────────────────────────

    private fun prepareInitialState() {
        // Glows
        binding.viewGiftGlow.alpha = 0f
        binding.viewGiftGlow.scaleX = 0.5f
        binding.viewGiftGlow.scaleY = 0.5f

        binding.viewGiftGlowInner.alpha = 0f
        binding.viewGiftGlowInner.scaleX = 0.3f
        binding.viewGiftGlowInner.scaleY = 0.3f

        // Gift box
        binding.ivGiftBox.scaleX = 0f
        binding.ivGiftBox.scaleY = 0f
        binding.ivGiftBox.rotation = -12f

        // Text & UI elements
        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = 18f

        binding.coinAmountContainer.alpha = 0f
        binding.coinAmountContainer.scaleX = 0.85f
        binding.coinAmountContainer.scaleY = 0.85f
        binding.coinAmountContainer.translationY = 20f

        binding.tvSubtitle.alpha = 0f
        binding.tvSubtitle.translationY = 12f

        binding.btnStartExploring.alpha = 0f
        binding.btnStartExploring.translationY = 14f

        binding.tvNote.alpha = 0f

        // Bottom fog starts invisible
        binding.viewBottomFog.alpha = 0f
    }

    // ── Entrance Animation Sequence ─────────────────────────────────────

    private fun playEntranceAnimations() {
        animateGlows()
        animateGiftBox()
        animateTitle()
        animateCoinChip()
        animateCounter()
        animateSubtitle()
        animateButton()
        animateNote()
    }

    // ─── 1. Dual Glow Fade-In + Breathing Pulse ────────────────────────

    private fun animateGlows() {
        // Outer glow — soft bloom
        binding.viewGiftGlow.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(DUR_GLOW_FADE)
            .setStartDelay(DELAY_OUTER_GLOW)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()

        // Inner glow — warmer, slightly delayed
        binding.viewGiftGlowInner.animate()
            .alpha(0.9f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(DUR_GLOW_FADE)
            .setStartDelay(DELAY_INNER_GLOW)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()

        // Breathing pulse on outer glow — starts after entrance completes
        startGlowPulse(binding.viewGiftGlow, startDelay = DELAY_OUTER_GLOW + DUR_GLOW_FADE, scaleRange = 1.12f)
        // Inner glow pulses slightly out of phase for organic feel
        startGlowPulse(binding.viewGiftGlowInner, startDelay = DELAY_INNER_GLOW + DUR_GLOW_FADE + 400, scaleRange = 1.08f)
    }

    private fun startGlowPulse(view: View, startDelay: Long, scaleRange: Float) {
        val pulseX = ObjectAnimator.ofFloat(view, "scaleX", 1f, scaleRange).apply {
            duration = DUR_GLOW_PULSE
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulseY = ObjectAnimator.ofFloat(view, "scaleY", 1f, scaleRange).apply {
            duration = DUR_GLOW_PULSE
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        AnimatorSet().apply {
            playTogether(pulseX, pulseY)
            this.startDelay = startDelay
            start()
        }
    }

    // ─── 2. Gift Box Pop-In + Confetti Burst ────────────────────────────

    private fun animateGiftBox() {
        // Wire up confetti haptic callback before burst
        binding.confettiView.onHapticPulse = {
            if (_binding != null) {
                binding.confettiView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        binding.ivGiftBox.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(DUR_GIFT_POP)
            .setStartDelay(DELAY_GIFT_POP)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                if (_binding == null) return@withEndAction

                // Gift box has landed — THUD haptic
                binding.ivGiftBox.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

                // Fire full-sheet confetti — wider, longer, with gravity fall
                binding.confettiView.burst(
                    particleCount = 60,
                    durationMs = DUR_CONFETTI,
                    originYFraction = 0.28f  // Spawn from upper area (gift position)
                )

                // Fade in the bottom fog as confetti starts falling
                val fogFadeIn = ObjectAnimator.ofFloat(binding.viewBottomFog, "alpha", 0f, 1f).apply {
                    duration = 1200
                    startDelay = 800
                    interpolator = DecelerateInterpolator()
                }
                // Fade out fog after confetti settles
                val fogFadeOut = ObjectAnimator.ofFloat(binding.viewBottomFog, "alpha", 1f, 0f).apply {
                    duration = 600
                }
                AnimatorSet().apply {
                    playSequentially(fogFadeIn, fogFadeOut)
                    start()
                }
            }
            .start()
    }

    // ─── 3. Title ───────────────────────────────────────────────────────

    private fun animateTitle() {
        binding.tvTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(DUR_TITLE_FADE)
            .setStartDelay(DELAY_TITLE)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    // ─── 4. Coin Chip — Spring Bounce ───────────────────────────────────

    private fun animateCoinChip() {
        // Fade in
        binding.coinAmountContainer.animate()
            .alpha(1f)
            .setDuration(DUR_CHIP_SPRING)
            .setStartDelay(DELAY_COIN_CHIP)
            .start()

        // Spring physics for the bounce (translationY + scale)
        binding.root.postDelayed({
            if (_binding == null) return@postDelayed

            // TranslationY spring — drops in and bounces
            SpringAnimation(binding.coinAmountContainer, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                spring = SpringForce(0f).apply {
                    dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY  // 0.2 — visible bounce
                    stiffness = 600f  // Snappy but not rigid
                }
                start()
            }

            // ScaleX spring
            SpringAnimation(binding.coinAmountContainer, DynamicAnimation.SCALE_X, 1f).apply {
                spring = SpringForce(1f).apply {
                    dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                    stiffness = 500f
                }
                start()
            }

            // ScaleY spring
            SpringAnimation(binding.coinAmountContainer, DynamicAnimation.SCALE_Y, 1f).apply {
                spring = SpringForce(1f).apply {
                    dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                    stiffness = 500f
                }
                start()
            }

            // Light haptic as chip appears
            binding.coinAmountContainer.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }, DELAY_COIN_CHIP)
    }

    // ─── 5. Counter 0→25 with Haptic Ticks ──────────────────────────────

    private fun animateCounter() {
        val coinAmount = arguments?.getInt(ARG_COIN_AMOUNT, 50) ?: 50
        var lastTickValue = -1

        val counterAnimator = ValueAnimator.ofInt(0, coinAmount).apply {
            duration = DUR_COUNTER
            startDelay = DELAY_COUNTER
            interpolator = DecelerateInterpolator(1.8f) // Starts fast, slows near 25
            addUpdateListener { anim ->
                if (_binding == null) return@addUpdateListener
                val value = anim.animatedValue as Int
                binding.tvCoinAmount.text = value.toString()

                // Light tick every 5 coins
                if (value != lastTickValue && value > 0 && value % 5 == 0) {
                    binding.tvCoinAmount.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    lastTickValue = value
                }

                // Final value — strong haptic + slight scale punch on the chip
                if (value == coinAmount && lastTickValue != coinAmount) {
                    lastTickValue = coinAmount
                    binding.coinAmountContainer.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

                    // Quick scale punch to celebrate landing on 25
                    binding.coinAmountContainer.animate()
                        .scaleX(1.08f)
                        .scaleY(1.08f)
                        .setDuration(100)
                        .withEndAction {
                            if (_binding == null) return@withEndAction
                            binding.coinAmountContainer.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                        }
                        .start()
                }
            }
        }
        counterAnimator.start()
    }

    // ─── 6. Subtitle ────────────────────────────────────────────────────

    private fun animateSubtitle() {
        binding.tvSubtitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(DUR_SUBTITLE_FADE)
            .setStartDelay(DELAY_SUBTITLE)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    // ─── 7. Button ──────────────────────────────────────────────────────

    private fun animateButton() {
        binding.btnStartExploring.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(DUR_BUTTON_FADE)
            .setStartDelay(DELAY_BUTTON)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    // ─── 8. Note ────────────────────────────────────────────────────────

    private fun animateNote() {
        binding.tvNote.animate()
            .alpha(1f)
            .setDuration(DUR_NOTE_FADE)
            .setStartDelay(DELAY_NOTE)
            .start()
    }

}
