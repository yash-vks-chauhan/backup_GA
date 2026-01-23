package com.gridee.parking.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import com.google.android.material.card.MaterialCardView
import com.gridee.parking.R

class TicketView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var notchRadius = 32f // Default 16dp roughly
    private var notchPosition = 0f // Y position
    
    // We can find the divider view dynamically if needed, 
    // but for now let's just expose a setter or rely on a fixed child ID?
    // A robust way: Look for a child view with a specific tag or ID "divider_dashed" 
    
    init {
        // Essential for the erase blend mode to work
        setLayerType(LAYER_TYPE_HARDWARE, null)
        // Remove default shadows/elevation drawing because we are cutting holes?
        // Actually MaterialCardView draws background. We need to cut THAT background.
        // But MaterialCardView background is a Property. 
        // Best approach: Draw on the canvas *after* super.draw?
        // If we want to cut the whole view (including content), we do it in dispatchDraw.
    }

    override fun draw(canvas: Canvas) {
        // Draw everything first (background, shadows, children)
        super.draw(canvas)
        
        // Now cut the holes
        if (notchPosition > 0) {
            // Left Notch
            canvas.drawCircle(0f, notchPosition, notchRadius, eraserPaint)
            // Right Notch
            canvas.drawCircle(width.toFloat(), notchPosition, notchRadius, eraserPaint)
        }
    }
    
    override fun dispatchDraw(canvas: Canvas) {
        // If we want to cut children too (content), we use dispatchDraw logic.
        // We probably want to cut children so they don't bleed into the hole.
        // But mainly we want to cut the white Card background.
        
        // This effectively punches a hole through the entire view stack of this card, 
        // revealing what is behind (the Activity background).
        
        // To make this work with a Shadow, MaterialCardView shadow is drawn outside bounds?
        // Cutting holes in a View with elevation is tricky because shadows won't wrap the hole.
        // But user wants "Physical Bite". Missing shadow in the bite is acceptable.
        
        // For simplicity, we assume we find the divider y-center.
        if (notchPosition == 0f) {
           calculateNotchPosition()
        }
        
        super.dispatchDraw(canvas)
        
        if (notchPosition > 0) {
             // Left Notch
            canvas.drawCircle(0f, notchPosition, notchRadius, eraserPaint)
            // Right Notch
            canvas.drawCircle(width.toFloat(), notchPosition, notchRadius, eraserPaint)
        }
    }
    
    private fun calculateNotchPosition() {
        // Find the divider view
        val divider = findViewById<View>(resources.getIdentifier("layout_cutout", "id", context.packageName))
        if (divider != null) {
            // Get divider center Y relative to this CardView
            val location = IntArray(2)
            divider.getLocationOnScreen(location)
            
            val cardLocation = IntArray(2)
            getLocationOnScreen(cardLocation)
            
            val relativeY = (location[1] - cardLocation[1]).toFloat()
            notchPosition = relativeY + (divider.height / 2f)
            
            // Adjust radius if needed (e.g. from resources)
            // notchRadius = divider.height / 2f // Or fixed size
             val displayMetrics = context.resources.displayMetrics
             notchRadius = 16 * displayMetrics.density // 16dp radius -> 32dp circle
        }
    }
}
