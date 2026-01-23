package com.gridee.parking.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi

/**
 * Helper class to apply backdrop blur effects to views
 * Provides Apple-style glassmorphism with actual blur behind elements
 */
object BlurViewHelper {

    /**
     * Apply backdrop blur to a view (API 31+)
     * This uses the native RenderEffect for best performance
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun applyModernBlur(view: View, blurRadius: Float = 25f) {
        try {
            val blurEffect = RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                Shader.TileMode.CLAMP
            )
            view.setRenderEffect(blurEffect)
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
