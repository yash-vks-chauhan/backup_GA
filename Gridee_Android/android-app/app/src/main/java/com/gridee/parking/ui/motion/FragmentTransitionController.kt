package com.gridee.parking.ui.motion

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation

/**
 * Drives the page-to-page transition with one coherent spring. Replaces the fixed-duration
 * XML interpolators with velocity-aware physics: a tap starts at 0 velocity, a flick seeds
 * the spring with the gesture's velocity and the page continues naturally.
 *
 * Layering: incoming view rides on top at 1.0x speed; outgoing view drifts at PARALLAX_FACTOR
 * speed behind a translucent scrim. This is the "push" depth iOS uses on every navigation.
 */
class FragmentTransitionController(
    private val container: FrameLayout,
) {

    private var scrim: View = View(container.context).apply {
        setBackgroundColor(0xFF000000.toInt())
        alpha = 0f
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isClickable = false
        isFocusable = false
    }

    private val activeAnims = mutableListOf<SpringAnimation>()

    /**
     * Returns a lambda that maps the translation value to a sinusoidal bell-curve alpha
     * for the scrim — 0 at start, peak at the midpoint, 0 again on arrival. Used as the
     * `onUpdate` callback on the incoming view's TRANSLATION_X spring (listener has to be
     * attached before start() per DynamicAnimation contract).
     */
    private fun scrimBellDriver(screenWidth: Float): (Float) -> Unit = { value ->
        val progress = (1f - kotlin.math.abs(value) / screenWidth).coerceIn(0f, 1f)
        val bell = kotlin.math.sin(progress * Math.PI).toFloat()
        scrim.alpha = MotionTokens.SCRIM_ALPHA_MAX * bell
    }

    fun attachScrimIfNeeded() {
        if (scrim.parent == null) {
            container.addView(scrim, 0)
        } else if (scrim.parent !== container) {
            (scrim.parent as? ViewGroup)?.removeView(scrim)
            container.addView(scrim, 0)
        }
        container.bringChildToFront(scrim)
    }

    /**
     * Seed the incoming view off-screen and the outgoing view in place, then spring both to
     * their targets. Forward = incoming enters from right; backward = from left.
     */
    fun runSwitch(
        incoming: View,
        outgoing: View?,
        forward: Boolean,
        startVelocityPxPerSec: Float = 0f,
        onSettle: () -> Unit,
    ) {
        cancelAll()
        attachScrimIfNeeded()

        val width = container.width.takeIf { it > 0 } ?: container.resources.displayMetrics.widthPixels
        val incomingStart = if (forward) width.toFloat() else -width.toFloat()

        incoming.visibility = View.VISIBLE
        incoming.translationX = incomingStart
        incoming.scaleX = 0.98f
        incoming.scaleY = 0.98f
        incoming.alpha = 0.96f
        incoming.bringToFront()

        // Scrim above outgoing, below incoming
        scrim.alpha = 0f
        scrim.bringToFront()
        incoming.bringToFront()

        applyLayer(incoming, true)
        outgoing?.let { applyLayer(it, true) }

        // Incoming: spring to resting state with the gesture's velocity. Scrim follows
        // translationX via an update listener so it fades in and out smoothly.
        val incomingTx = MotionTokens.spring(
            incoming, DynamicAnimation.TRANSLATION_X, 0f,
            startVelocity = startVelocityPxPerSec,
            onUpdate = scrimBellDriver(width.toFloat()),
            onEnd = { _ ->
                applyLayer(incoming, false)
                scrim.alpha = 0f
                onSettle()
            },
        )
        activeAnims += incomingTx
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.SCALE_X, 1f)
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.SCALE_Y, 1f)
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.ALPHA, 1f, stiffness = MotionTokens.STIFFNESS_SNAPPY)

        // Outgoing: drift at parallax speed, dim slightly, scale floor
        outgoing?.let { out ->
            val outTarget = if (forward) -width * MotionTokens.PARALLAX_FACTOR else width * MotionTokens.PARALLAX_FACTOR
            activeAnims += MotionTokens.spring(out, DynamicAnimation.TRANSLATION_X, outTarget)
            activeAnims += MotionTokens.spring(out, DynamicAnimation.SCALE_X, MotionTokens.OUTGOING_SCALE_FLOOR)
            activeAnims += MotionTokens.spring(out, DynamicAnimation.SCALE_Y, MotionTokens.OUTGOING_SCALE_FLOOR)
            activeAnims += MotionTokens.spring(out, DynamicAnimation.ALPHA, MotionTokens.OUTGOING_ALPHA_FLOOR)
        }
    }

    /**
     * Drive the transition by an interactive progress in [-1f..1f]. Negative = swiping toward
     * previous tab (incoming enters from left). Positive = swiping toward next tab.
     */
    fun setProgress(progress: Float, incoming: View, outgoing: View?, forward: Boolean) {
        val width = container.width.takeIf { it > 0 } ?: container.resources.displayMetrics.widthPixels
        val p = progress.coerceIn(0f, 1f)

        incoming.visibility = View.VISIBLE
        incoming.translationX = if (forward) (1f - p) * width else -(1f - p) * width
        incoming.scaleX = 0.98f + 0.02f * p
        incoming.scaleY = 0.98f + 0.02f * p
        incoming.alpha = 0.96f + 0.04f * p
        incoming.bringToFront()

        outgoing?.let {
            val tx = if (forward) -width * MotionTokens.PARALLAX_FACTOR * p else width * MotionTokens.PARALLAX_FACTOR * p
            it.translationX = tx
            val s = 1f - (1f - MotionTokens.OUTGOING_SCALE_FLOOR) * p
            it.scaleX = s
            it.scaleY = s
            it.alpha = 1f - (1f - MotionTokens.OUTGOING_ALPHA_FLOOR) * p
        }
        scrim.alpha = MotionTokens.SCRIM_ALPHA_MAX * p
    }

    /**
     * Spring back to the previous tab when the user cancels mid-swipe.
     */
    fun cancelInteractive(
        incoming: View,
        outgoing: View?,
        forward: Boolean,
        startVelocityPxPerSec: Float,
        onSettle: () -> Unit,
    ) {
        cancelAll()
        val width = container.width.takeIf { it > 0 } ?: container.resources.displayMetrics.widthPixels
        val offTarget = if (forward) width.toFloat() else -width.toFloat()

        val incomingTx = MotionTokens.spring(
            incoming, DynamicAnimation.TRANSLATION_X, offTarget,
            startVelocity = startVelocityPxPerSec,
            onUpdate = scrimBellDriver(width.toFloat()),
            onEnd = { _ ->
                incoming.visibility = View.GONE
                incoming.translationX = 0f
                incoming.scaleX = 1f
                incoming.scaleY = 1f
                incoming.alpha = 1f
                scrim.alpha = 0f
                onSettle()
            },
        )
        activeAnims += incomingTx
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.SCALE_X, 0.98f)
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.SCALE_Y, 0.98f)
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.ALPHA, 0.96f)

        outgoing?.let {
            activeAnims += MotionTokens.spring(it, DynamicAnimation.TRANSLATION_X, 0f)
            activeAnims += MotionTokens.spring(it, DynamicAnimation.SCALE_X, 1f)
            activeAnims += MotionTokens.spring(it, DynamicAnimation.SCALE_Y, 1f)
            activeAnims += MotionTokens.spring(it, DynamicAnimation.ALPHA, 1f)
        }
    }

    /**
     * Complete an interactive swipe forward to the new tab with the user's release velocity.
     */
    fun commitInteractive(
        incoming: View,
        outgoing: View?,
        forward: Boolean,
        startVelocityPxPerSec: Float,
        onSettle: () -> Unit,
    ) {
        cancelAll()
        val width = container.width.takeIf { it > 0 } ?: container.resources.displayMetrics.widthPixels
        val outTarget = if (forward) -width * MotionTokens.PARALLAX_FACTOR else width * MotionTokens.PARALLAX_FACTOR

        val incomingTx = MotionTokens.spring(
            incoming, DynamicAnimation.TRANSLATION_X, 0f,
            startVelocity = startVelocityPxPerSec,
            onUpdate = scrimBellDriver(width.toFloat()),
            onEnd = { _ ->
                scrim.alpha = 0f
                onSettle()
            },
        )
        activeAnims += incomingTx
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.SCALE_X, 1f)
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.SCALE_Y, 1f)
        activeAnims += MotionTokens.spring(incoming, DynamicAnimation.ALPHA, 1f, stiffness = MotionTokens.STIFFNESS_SNAPPY)

        outgoing?.let {
            activeAnims += MotionTokens.spring(it, DynamicAnimation.TRANSLATION_X, outTarget)
            activeAnims += MotionTokens.spring(it, DynamicAnimation.SCALE_X, MotionTokens.OUTGOING_SCALE_FLOOR)
            activeAnims += MotionTokens.spring(it, DynamicAnimation.SCALE_Y, MotionTokens.OUTGOING_SCALE_FLOOR)
            activeAnims += MotionTokens.spring(it, DynamicAnimation.ALPHA, MotionTokens.OUTGOING_ALPHA_FLOOR)
        }
    }

    fun cancelAll() {
        activeAnims.forEach { if (it.isRunning) it.cancel() }
        activeAnims.clear()
    }

    fun resetOutgoingTransform(view: View?) {
        view ?: return
        view.translationX = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        applyLayer(view, false)
    }

    private fun applyLayer(view: View, enabled: Boolean) {
        val target = if (enabled) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        if (view.layerType != target) view.setLayerType(target, null)
    }
}
