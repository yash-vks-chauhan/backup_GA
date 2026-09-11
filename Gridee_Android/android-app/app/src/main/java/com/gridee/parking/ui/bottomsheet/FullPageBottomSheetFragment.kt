package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.ui.motion.AnimatorSettingsCompat

/**
 * A bottom sheet that opens as a full page, with the app's "Lift & Settle" motion.
 *
 * This is the choreography the parking-spot sheet established, lifted into a base
 * class so other sheets can read as the same object rather than re-implementing it:
 * the sheet rises on a critically-damped spring while the world behind it responds
 * in sync — the host page scales down a hair and picks up rounded corners so it
 * reads as a card receding, the dim deepens, and (where the device supports it) the
 * backdrop blurs. The exit mirrors it on a stiffer spring, since leaving should be
 * quicker than arriving.
 *
 * Subclasses supply their content the usual way (`onCreateView`); the sheet is
 * always MATCH_PARENT and starts expanded, so the layout root should be
 * `match_parent` in height with `fitsSystemWindows="false"`.
 */
open class FullPageBottomSheetFragment : BottomSheetDialogFragment() {

    private var backdropAnimator: android.animation.ValueAnimator? = null
    private var sheetSpring: SpringAnimation? = null
    private var currentBackdropProgress: Float = 0f
    private var isProgrammaticAnimation: Boolean = false

    // Transparent overlay parked inside the sheet during the entrance so a fast
    // tap can't focus a field (and throw the keyboard up) before the sheet has
    // finished landing.
    private var touchShield: View? = null

    protected var bottomSheetBehavior: BottomSheetBehavior<*>? = null
        private set

    // The sheet's drawable uses 40dp top corners; the host activity behind it gets
    // 32dp — slightly smaller so the receding page sits one step "deeper" in the
    // visual stack while still clearly belonging to the same family.
    private val hostCornerRadiusPx: Float by lazy { 32f * resources.displayMetrics.density }
    private val hostScaleOutline = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            val radius = hostCornerRadiusPx * currentBackdropProgress
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    // Cached so applyBackdropProgress doesn't walk the host's view tree on every
    // animation frame. Refreshed once when the dialog is shown.
    private var hostContentView: View? = null
    // Resolved up front so the per-frame path doesn't query the WindowManager to
    // decide whether pushing a blur radius is worth the IPC.
    private var crossWindowBlurSupported: Boolean = false

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_EXPANDED ||
                newState == BottomSheetBehavior.STATE_HIDDEN
            ) {
                bottomSheet.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            if (isProgrammaticAnimation) return
            // While the user drags, the backdrop tracks the sheet's position so the
            // world behind responds to the gesture in real time.
            applyBackdropProgress(slideOffset.coerceIn(0f, 1f))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Start with no dim — the entrance animator drives it up so the dim arrives
        // with the sheet rather than snapping on before the motion begins.
        dialog.window?.setDimAmount(0f)
        hostContentView = activity?.window?.decorView?.findViewById(android.R.id.content)
        // FLAG_BLUR_BEHIND alone isn't enough — on devices where the system has
        // disabled blur (battery saver, low-end GPU, OEM setting) the radius is
        // silently ignored and every per-frame update would be wasted IPC.
        crossWindowBlurSupported =
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            (requireContext().getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.isCrossWindowBlurEnabled == true

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog

            bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                sheet.fitsSystemWindows = false

                (sheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                    params.setMargins(0, 0, 0, 0)
                    sheet.layoutParams = params
                }

                // Park the sheet off-screen immediately so Material's default slide
                // doesn't flash a partial reveal before our spring takes over.
                sheet.translationY = resources.displayMetrics.heightPixels.toFloat()
                sheet.post { animateLiftEntrance(sheet) }
            }

            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true

            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }

                val isLightMode = (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
                val wic = WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = isLightMode
                wic.isAppearanceLightStatusBars = isLightMode

                if (crossWindowBlurSupported) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val attrs = window.attributes
                    attrs.blurBehindRadius = 0
                    window.attributes = attrs
                }
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            bottomSheet?.requestLayout()
            bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true

            if (bottomSheetBehavior == null) {
                bottomSheetBehavior = bottomSheetDialog.behavior
                bottomSheetDialog.behavior.addBottomSheetCallback(bottomSheetCallback)
            }
        }
    }

    override fun dismiss() {
        animateLiftExit { super.dismiss() }
    }

    override fun dismissAllowingStateLoss() {
        animateLiftExit { super.dismissAllowingStateLoss() }
    }

    override fun onDestroy() {
        // Safety net: if the fragment is torn down mid-animation (config change,
        // process death of the host flow), never leave the page scaled/rounded.
        backdropAnimator?.cancel()
        sheetSpring?.cancel()
        resetBackdrop()
        super.onDestroy()
    }

    // ── Lift & Settle: entrance ─────────────────────────────────────
    private fun animateLiftEntrance(sheet: View) {
        val sheetHeight = if (sheet.height > 0) sheet.height.toFloat()
            else resources.displayMetrics.heightPixels.toFloat()

        // Accessibility: if the system has reduced motion (animations off or
        // duration scale 0), skip the choreography and place the sheet at rest.
        if (shouldReduceMotion()) {
            sheet.translationY = 0f
            applyBackdropProgress(1f)
            isProgrammaticAnimation = false
            return
        }

        isProgrammaticAnimation = true
        bottomSheetBehavior?.isDraggable = false
        engageTouchShield()
        sheet.translationY = sheetHeight

        sheetSpring?.cancel()
        sheetSpring = SpringAnimation(sheet, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            // Stiffness 300 + critical damping settles in ~460ms — the same window
            // the backdrop animator runs on, so motion and depth resolve together.
            spring = SpringForce(0f).apply {
                dampingRatio = 1.0f
                stiffness = 300f
            }
            addEndListener { _, canceled, _, _ ->
                // Settle haptic — one soft tick the moment the sheet arrives.
                if (!canceled && isAdded) {
                    sheet.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            start()
        }

        backdropAnimator?.cancel()
        backdropAnimator = android.animation.ValueAnimator.ofFloat(currentBackdropProgress, 1f).apply {
            duration = 460
            interpolator = android.view.animation.PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)
            addUpdateListener { anim -> applyBackdropProgress(anim.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = finishEntranceGate()
                override fun onAnimationCancel(animation: android.animation.Animator) = finishEntranceGate()
            })
            start()
        }
    }

    private fun finishEntranceGate() {
        isProgrammaticAnimation = false
        bottomSheetBehavior?.isDraggable = true
        releaseTouchShield()
    }

    private fun engageTouchShield() {
        val sheet = (dialog as? BottomSheetDialog)
            ?.findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        if (touchShield != null) return
        val shield = View(sheet.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // setOnClickListener forces isClickable = true, which is what makes this
            // view absorb taps that would otherwise reach the content below.
            setOnClickListener { /* swallow */ }
            isFocusable = false
        }
        sheet.addView(shield)
        touchShield = shield
    }

    private fun releaseTouchShield() {
        val shield = touchShield ?: return
        (shield.parent as? ViewGroup)?.removeView(shield)
        touchShield = null
    }

    private fun shouldReduceMotion(): Boolean {
        val currentContext = context ?: return true
        return !AnimatorSettingsCompat.areEnabled(currentContext)
    }

    // ── Lift & Settle: exit ─────────────────────────────────────────
    // Two paths:
    //   • Programmatic dismiss (tap close, back press): full spring exit.
    //   • Swipe dismiss: Material already moved the sheet to STATE_HIDDEN, so the
    //     spring would be invisible work that just delays the window teardown —
    //     release the backdrop and close.
    private fun animateLiftExit(onComplete: () -> Unit) {
        if (shouldReduceMotion()) {
            backdropAnimator?.cancel()
            sheetSpring?.cancel()
            resetBackdrop()
            onComplete()
            return
        }

        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val sheetAlreadyOffscreen = bottomSheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN
        val startProgress = currentBackdropProgress

        if (bottomSheet == null || sheetAlreadyOffscreen) {
            backdropAnimator?.cancel()
            sheetSpring?.cancel()
            if (startProgress < 0.02f) {
                resetBackdrop()
                onComplete()
                return
            }
            isProgrammaticAnimation = true
            backdropAnimator = android.animation.ValueAnimator.ofFloat(startProgress, 0f).apply {
                duration = (180 * startProgress).toLong().coerceAtLeast(90)
                interpolator = android.view.animation.PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f)
                addUpdateListener { anim -> applyBackdropProgress(anim.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        resetBackdrop()
                        isProgrammaticAnimation = false
                        onComplete()
                    }
                })
                start()
            }
            return
        }

        isProgrammaticAnimation = true
        val durationScale = startProgress.coerceAtLeast(0.5f)

        backdropAnimator?.cancel()
        backdropAnimator = android.animation.ValueAnimator.ofFloat(startProgress, 0f).apply {
            duration = (260 * durationScale).toLong()
            interpolator = android.view.animation.PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f)
            addUpdateListener { anim -> applyBackdropProgress(anim.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = resetBackdrop()
            })
            start()
        }

        val sheetHeight = if (bottomSheet.height > 0) bottomSheet.height.toFloat()
            else resources.displayMetrics.heightPixels.toFloat()

        sheetSpring?.cancel()
        sheetSpring = SpringAnimation(bottomSheet, DynamicAnimation.TRANSLATION_Y, sheetHeight).apply {
            // Exit stiffness 520 settles in ~300ms — about 65% of entrance time, the
            // standard "leave-faster-than-you-arrive" ratio.
            spring = SpringForce(sheetHeight).apply {
                dampingRatio = 1.0f
                stiffness = 520f
            }
            addEndListener { _, _, _, _ ->
                isProgrammaticAnimation = false
                onComplete()
            }
            start()
        }
    }

    private fun applyBackdropProgress(t: Float) {
        currentBackdropProgress = t
        hostContentView?.let {
            // 3% shrink — visible enough to read as depth, restrained enough to not
            // call attention to itself.
            val scale = 1f - (0.03f * t)
            it.scaleX = scale
            it.scaleY = scale
            if (it.outlineProvider !== hostScaleOutline) {
                it.outlineProvider = hostScaleOutline
                it.clipToOutline = true
            }
            it.invalidateOutline()
        }
        dialog?.window?.setDimAmount(0.35f * t)
        if (crossWindowBlurSupported) {
            dialog?.window?.let { window ->
                val attrs = window.attributes
                // 28px — iOS-tier blur intensity. Stronger reads as smudge.
                attrs.blurBehindRadius = (28f * t).toInt()
                window.attributes = attrs
            }
        }
    }

    private fun resetBackdrop() {
        currentBackdropProgress = 0f
        hostContentView?.let {
            it.scaleX = 1f
            it.scaleY = 1f
            it.clipToOutline = false
            it.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
    }
}
