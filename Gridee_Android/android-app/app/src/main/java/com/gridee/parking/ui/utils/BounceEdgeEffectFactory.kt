package com.gridee.parking.ui.utils

import android.graphics.Canvas
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

/** The magnitude of translation distance while the list is over-scrolled. */
/** The magnitude of translation distance while the list is over-scrolled. */
private const val OVERSCROLL_TRANSLATION_MAGNITUDE = 0.5f

/** The magnitude of translation distance when the list reaches the edge on fling. */
private const val FLING_TRANSLATION_MAGNITUDE = 0.5f

/**
 * Replace edge effect by a bounce.
 * Supports both Vertical and Horizontal RecyclerViews.
 */
class BounceEdgeEffectFactory : RecyclerView.EdgeEffectFactory() {

    override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {

        return object : EdgeEffect(recyclerView.context) {

            // A reference to the [SpringAnimation] for this RecyclerView used to bring the item back after the over-scroll effect.
            var anim: SpringAnimation? = null

            override fun onPull(deltaDistance: Float) {
                super.onPull(deltaDistance)
                handlePull(deltaDistance)
            }

            override fun onPull(deltaDistance: Float, displacement: Float) {
                super.onPull(deltaDistance, displacement)
                handlePull(deltaDistance)
            }

            private fun handlePull(deltaDistance: Float) {
                // Translate the recyclerView with the distance
                val sign = when (direction) {
                    DIRECTION_BOTTOM -> -1
                    DIRECTION_RIGHT -> -1
                    else -> 1
                }
                
                val isHorizontal = direction == DIRECTION_LEFT || direction == DIRECTION_RIGHT
                
                val translationDelta = sign * (if (isHorizontal) recyclerView.width else recyclerView.height) * deltaDistance * OVERSCROLL_TRANSLATION_MAGNITUDE
                
                if (isHorizontal) {
                    recyclerView.translationX += translationDelta
                } else {
                    recyclerView.translationY += translationDelta
                }

                anim?.cancel()
            }

            override fun onRelease() {
                super.onRelease()
                // The finger is lifted. Start the animation to bring translation back to the resting state.
                val isHorizontal = direction == DIRECTION_LEFT || direction == DIRECTION_RIGHT
                anim = createAnim().also { it.start() }
            }

            override fun onAbsorb(velocity: Int) {
                super.onAbsorb(velocity)

                // The list has reached the edge on fling.
                val sign = when (direction) {
                    DIRECTION_BOTTOM -> -1
                    DIRECTION_RIGHT -> -1
                    else -> 1
                }
                val translationVelocity = sign * velocity * FLING_TRANSLATION_MAGNITUDE
                anim?.cancel()
                anim = createAnim().setStartVelocity(translationVelocity.toFloat())
                    ?.also { it.start() }
            }

            private fun createAnim(): SpringAnimation {
                val isHorizontal = direction == DIRECTION_LEFT || direction == DIRECTION_RIGHT
                val property = if (isHorizontal) SpringAnimation.TRANSLATION_X else SpringAnimation.TRANSLATION_Y
                
                return SpringAnimation(recyclerView, property)
                    .setSpring(SpringForce()
                        .setFinalPosition(0f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_MEDIUM))
            }

            override fun draw(canvas: Canvas?): Boolean {
                // don't paint the usual edge effect
                return false
            }

            override fun isFinished(): Boolean {
                // Without this, will skip future calls to onAbsorb()
                return anim?.isRunning?.not() ?: true
            }
        }
    }
}
