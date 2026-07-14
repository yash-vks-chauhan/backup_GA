package com.gridee.parking.ui.motion

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

object MotionTokens {

    const val STIFFNESS_STANDARD = 380f
    const val STIFFNESS_GENTLE = 220f
    const val STIFFNESS_SNAPPY = 700f

    const val DAMPING_STANDARD = 0.86f
    const val DAMPING_SOFT = 0.92f
    const val DAMPING_PLAYFUL = 0.72f

    // ── Active-indicator slide ──────────────────────────────────────────────────────
    // Modelled on iOS's tab selection: a confident slide with a small settle, not a
    // rubber-band. Tuned in iOS's own terms — duration (perceptual settle time, in
    // seconds) and bounce (0 = none, ~0.15 = the subtle iOS overshoot, 0.3 = playful) —
    // mirroring SwiftUI's .spring(duration:bounce:). These are the dials to tune on-device.
    const val PILL_BOUNCE = 0.15f
    // A faint lead/trail asymmetry keeps the pill feeling alive without rubber-banding: the
    // leading edge (in the direction of motion) settles a touch quicker than the trailing
    // edge. Keep the spread small — widening it brings back the heavy "liquid stretch".
    const val PILL_LEAD_DURATION = 0.34f
    const val PILL_TRAIL_DURATION = 0.46f

    const val PARALLAX_FACTOR = 0.30f
    const val OUTGOING_SCALE_FLOOR = 0.94f
    const val OUTGOING_ALPHA_FLOOR = 0.78f
    const val SCRIM_ALPHA_MAX = 0.18f

    const val EDGE_SWIPE_WIDTH_DP = 32f
    const val SWIPE_COMMIT_FRACTION = 0.32f
    const val SWIPE_COMMIT_VELOCITY_DP = 800f

    fun spring(
        view: View,
        property: DynamicAnimation.ViewProperty,
        target: Float,
        stiffness: Float = STIFFNESS_STANDARD,
        damping: Float = DAMPING_STANDARD,
        startVelocity: Float = 0f,
        onUpdate: ((Float) -> Unit)? = null,
        onEnd: ((Boolean) -> Unit)? = null
    ): SpringAnimation {
        // Listeners MUST be attached BEFORE start() — DynamicAnimation rejects late
        // addUpdateListener calls with UnsupportedOperationException.
        val anim = SpringAnimation(view, property).apply {
            spring = SpringForce(target).apply {
                this.stiffness = stiffness
                this.dampingRatio = damping
            }
            if (startVelocity != 0f) setStartVelocity(startVelocity)
            onUpdate?.let { cb ->
                addUpdateListener { _, value, _ -> cb(value) }
            }
            onEnd?.let { cb ->
                addEndListener { _, cancelled, _, _ -> cb(cancelled) }
            }
        }
        anim.start()
        return anim
    }

    // SwiftUI-style spring conversion. SwiftUI parameterises springs as duration + bounce;
    // SpringForce wants stiffness + dampingRatio. For unit mass:
    //   stiffness    = (2π / duration)²
    //   dampingRatio = 1 − bounce
    // This lets us tune in iOS's language and convert at the call site.
    fun stiffnessForDuration(durationSeconds: Float): Float {
        val omega = (2.0 * Math.PI) / durationSeconds
        return (omega * omega).toFloat()
    }

    fun dampingForBounce(bounce: Float): Float = (1f - bounce).coerceIn(0f, 1f)

}
