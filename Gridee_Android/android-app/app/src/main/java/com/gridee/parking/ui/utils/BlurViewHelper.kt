package com.gridee.parking.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi

/**
 * Utilities for Android blur effects.
 * Note: View RenderEffect blurs a view's rendered content, not arbitrary content behind it.
 * For actual backdrop blur, Android exposes window-level blur APIs on Android 12+.
 */
object BlurViewHelper {

    /**
     * Blur the rendered content of a view on Android 12+.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun applyModernBlur(view: View, blurRadius: Float = 25f, saturation: Float = 1f) {
        try {
            if (blurRadius <= 0.05f && saturation >= 0.999f) {
                view.setRenderEffect(null)
                return
            }

            val safeRadius = blurRadius.coerceAtLeast(0.01f)
            val blurEffect = RenderEffect.createBlurEffect(
                safeRadius,
                safeRadius,
                Shader.TileMode.CLAMP
            )

            val effect = if (saturation < 0.999f) {
                val colorMatrix = ColorMatrix().apply {
                    setSaturation(saturation.coerceIn(0f, 1f))
                }
                RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(colorMatrix),
                    blurEffect
                )
            } else {
                blurEffect
            }

            view.setRenderEffect(effect)
        } catch (e: Exception) {
            // Fallback silently if blur fails
            e.printStackTrace()
        }
    }

    /**
     * Apply backdrop blur using RenderScript (API 21-30)
     * Legacy approach for older devices
     */
    fun applyLegacyBlur(context: Context, view: View, blurRadius: Float = 25f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyModernBlur(view, blurRadius)
            return
        }

        try {
            // This requires the view to have a background that can be blurred
            // We'll set a semi-transparent overlay instead
            view.alpha = 0.95f
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Remove blur effect from a view
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun removeBlur(view: View) {
        try {
            view.setRenderEffect(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check if device supports native blur effects
     */
    fun supportsNativeBlur(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * Apply Android 12+ window blur when the UI is hosted in a separate window
     * such as a dialog or bottom sheet.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun applyWindowBlur(
        window: Window,
        backgroundBlurRadius: Int = 48,
        blurBehindRadius: Int = 20,
        dimAmount: Float = 0.12f
    ) {
        val attributes = window.attributes
        attributes.flags = attributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        attributes.blurBehindRadius = blurBehindRadius.coerceAtLeast(0)
        attributes.dimAmount = dimAmount.coerceIn(0f, 1f)
        window.attributes = attributes
        window.setBackgroundBlurRadius(backgroundBlurRadius.coerceAtLeast(0))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun clearWindowBlur(window: Window) {
        val attributes = window.attributes
        attributes.blurBehindRadius = 0
        attributes.flags = attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        window.attributes = attributes
        window.setBackgroundBlurRadius(0)
    }

    /**
     * Apply blur to a bitmap using RenderScript
     * This is useful for creating blurred background images
     */
    @Suppress("DEPRECATION")
    fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float = 25f): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For modern devices, recommend using RenderEffect instead
            return bitmap
        }

        try {
            val renderScript = RenderScript.create(context)
            val input = Allocation.createFromBitmap(renderScript, bitmap)
            val output = Allocation.createTyped(renderScript, input.type)
            val script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
            
            script.setRadius(radius.coerceIn(0f, 25f))
            script.setInput(input)
            script.forEach(output)
            output.copyTo(bitmap)
            
            renderScript.destroy()
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Create a blurred background view for glassmorphism effect
     */
    fun createBlurredBackground(parentView: ViewGroup, targetView: View, blurRadius: Float = 25f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use modern blur effect
            applyModernBlur(targetView, blurRadius)
        } else {
            // Fallback: increase translucency for older devices
            targetView.alpha = 0.92f
        }
    }
}
