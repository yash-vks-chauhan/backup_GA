package com.gridee.parking.ui.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import com.gridee.parking.R

/**
 * Handles the "Privacy Blur" animation for password fields.
 * Creates a premium, Apple-style glassmorphism effect when toggling password visibility.
 */
class PasswordBlurAnimator(
    private val context: Context,
    private val textInputLayout: TextInputLayout,
    private val editText: EditText
) {
    private var isPasswordVisible = false
    // Access the internal end icon view of TextInputLayout to animate it
    private val endIconView: View? = textInputLayout.findViewById(com.google.android.material.R.id.text_input_end_icon)

    // Premium Easing Curves
    // EaseInCubic: Great for building momentum (blurring out)
    private val blurInInterpolator = PathInterpolator(0.32f, 0f, 0.67f, 0f)
    // EaseOutQuart: Great for smooth settling (blurring in)
    private val blurOutInterpolator = PathInterpolator(0.25f, 1f, 0.5f, 1f)

    init {
        // Override the default listener to start our custom animation
        textInputLayout.setEndIconOnClickListener { toggle() }
        
        // Ensure initial state matches our logic
        // We assume the XML starts with password hidden (textPassword)
        isPasswordVisible = false
        // Ensure typeface is consistent from start
        editText.typeface = Typeface.DEFAULT 
    }

    private fun toggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performPremiumToggle()
        } else {
            performLegacyToggle()
        }
    }

    private fun performLegacyToggle() {
        // Fallback Animation: Fade & Scale (Cross-Dissolve)
        // Mimics the "Retreat -> Change -> Advance" physics of the blur effect
        
        // PHASE 1: RETREAT (Fade Out + Scale Down)
        val retreatAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 150
            interpolator = blurInInterpolator
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                // Alpha: 1 -> 0
                editText.alpha = value
                // Scale: 1 -> 0.95 (Subtle breathing effect)
                val scale = 0.95f + (0.05f * value)
                editText.scaleX = scale
                editText.scaleY = scale
            }
        }

        // PHASE 3: ADVANCE (Fade In + Scale Up)
        val advanceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = blurOutInterpolator
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                editText.alpha = value
                val scale = 0.95f + (0.05f * value)
                editText.scaleX = scale
                editText.scaleY = scale
            }
        }

        // Chain the phases
        retreatAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // PHASE 2: THE SWAP (While invisible)
                isPasswordVisible = !isPasswordVisible
                
                updateInputType(isPasswordVisible)
                updateIcon(isPasswordVisible)
                textInputLayout.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                advanceAnimator.start()
            }
        })
        
        // Icon Animation (Synchronized with text)
        animateIcon(shrink = true)
        advanceAnimator.addListener(object : AnimatorListenerAdapter() {
             override fun onAnimationStart(animation: Animator) {
                 animateIcon(shrink = false)
             }
        })

        retreatAnimator.start()
    }

    private fun performPremiumToggle() {
        // Dynamic Blur Radius: Calculate based on text size for perfect obstruction
        // Default text size is often ~16sp. 16/2 = 8, 16 close to 15. 
        // A fixed 15f is usually a good "strong" blur for standard inputs.
        val targetBlurRadius = 15f 
        
        // PHASE 1: INHALE (Rapid Blur to Obscure Content)
        // 0 -> Peak
        val obscureAnimator = ValueAnimator.ofFloat(0.01f, targetBlurRadius).apply {
            duration = 150 // Fast inhale
            interpolator = blurInInterpolator
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                applyBlur(radius)
            }
        }

        // PHASE 3: EXHALE (Smooth Reveal)
        // Peak -> 0
        val revealAnimator = ValueAnimator.ofFloat(targetBlurRadius, 0.01f).apply {
            duration = 200 // Elegant exhale
            interpolator = blurOutInterpolator
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                applyBlur(radius)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Clean up render effect completely to save resources
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                         editText.setRenderEffect(null)
                    }
                }
            })
        }

        // Chain the phases
        obscureAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // PHASE 2: THE SWAP (At Peak Blur)
                isPasswordVisible = !isPasswordVisible
                
                // 1. Swap Logic (Invisible to user due to blur)
                updateInputType(isPasswordVisible)
                updateIcon(isPasswordVisible)
                
                // 2. Precise Haptic Confirmation (The "Click" feel)
                textInputLayout.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                // 3. Start Reveal
                revealAnimator.start()
            }
        })
        
        // Icon Animation (Simultaneous with Blur)
        // Shrink icon during blur (Inhale)
        animateIcon(shrink = true)
        
        // Expand icon during reveal (Exhale)
        revealAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                animateIcon(shrink = false)
            }
        })
        
        obscureAnimator.start()
    }

    private fun applyBlur(radius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // RenderEffect requires radius > 0
            val safeRadius = radius.coerceAtLeast(0.1f)
            editText.setRenderEffect(
                RenderEffect.createBlurEffect(
                    safeRadius, 
                    safeRadius, 
                    Shader.TileMode.CLAMP
                )
            )
        }
    }

    private fun updateInputType(visible: Boolean) {
        val selection = editText.selectionEnd
        if (visible) {
            editText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        // ZERO-JITTER STABILIZATION
        // Password fields often switch font variants. We enforce DEFAULT (sans-serif)
        // to minimize width jumps between dots and characters.
        editText.typeface = Typeface.DEFAULT
        
        // Restore cursor position
        editText.setSelection(selection)
    }

    private fun updateIcon(visible: Boolean) {
        if (visible) {
            textInputLayout.setEndIconDrawable(R.drawable.ic_eye)
        } else {
            textInputLayout.setEndIconDrawable(R.drawable.ic_eye_off)
        }
    }

    private fun animateIcon(shrink: Boolean) {
        endIconView?.let { icon ->
            val targetScale = if (shrink) 0.8f else 1.0f
            val targetAlpha = if (shrink) 0.6f else 1.0f
            val duration = if (shrink) 150L else 200L
            
            icon.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .alpha(targetAlpha)
                .setDuration(duration)
                .setInterpolator(if (shrink) blurInInterpolator else blurOutInterpolator)
                .start()
        }
    }
}
