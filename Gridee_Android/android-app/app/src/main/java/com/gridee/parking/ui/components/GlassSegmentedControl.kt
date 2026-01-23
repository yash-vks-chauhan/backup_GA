package com.gridee.parking.ui.components

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.gridee.parking.R

/**
 * Enhanced Glass Segmented Control following iOS design specifications
 */
class GlassSegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Segment(
        val text: String,
        val contentDescription: String = text
    )

    interface OnSegmentSelectedListener {
        fun onSegmentSelected(index: Int, segment: Segment)
    }

    // Properties following the specifications
    private val segments = mutableListOf<Segment>()
    private var selectedIndex = 0
    private var listener: OnSegmentSelectedListener? = null

    // Paint objects
    private val containerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Colors from specifications
    private val glassTextSelected = ContextCompat.getColor(context, R.color.glass_text_selected)
    private val glassTextUnselected = ContextCompat.getColor(context, R.color.glass_text_unselected)
    private val glassContainerFill = ContextCompat.getColor(context, R.color.glass_container_fill)
    private val glassContainerBorder = ContextCompat.getColor(context, R.color.glass_container_border)
    private val glassIndicatorFill = ContextCompat.getColor(context, R.color.glass_indicator_fill)
    private val glassIndicatorBorder = ContextCompat.getColor(context, R.color.glass_indicator_border)
    private val glassShadow = ContextCompat.getColor(context, R.color.glass_shadow)

    // Dimensions (in dp, converted to px)
    private val containerRadius = 25f * context.resources.displayMetrics.density
    private val indicatorRadius = 19f * context.resources.displayMetrics.density
    private val textSize = 15f * context.resources.displayMetrics.scaledDensity
    private val horizontalPadding = 12f * context.resources.displayMetrics.density
    private val verticalPadding = 6f * context.resources.displayMetrics.density
    private val indicatorMargin = 6f * context.resources.displayMetrics.density
    private val shadowOffset = 2f * context.resources.displayMetrics.density
    private val borderWidth = 1f * context.resources.displayMetrics.density

    // Animation properties
    private var indicatorX = 0f
    private var indicatorWidth = 0f
    private var animatingToIndex = -1

    // Touch handling
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    init {
        setupPaints()
        isClickable = true
        isFocusable = true
        
        // Add default segments for bookings
        addSegment(Segment("Active", "Active bookings"))
        addSegment(Segment("Pending", "Pending bookings"))
        addSegment(Segment("Completed", "Completed bookings"))
        
        setSelectedIndex(1) // Default to Pending
    }

    private fun setupPaints() {
        // Container paint
        containerPaint.apply {
            color = glassContainerFill
            style = Paint.Style.FILL
        }

        // Indicator paint
        indicatorPaint.apply {
            color = glassIndicatorFill
            style = Paint.Style.FILL
        }

        // Text paint
        textPaint.apply {
            this.textSize = this@GlassSegmentedControl.textSize
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        // Shadow paint
        shadowPaint.apply {
            color = glassShadow
            style = Paint.Style.FILL
        }
    }

    fun addSegment(segment: Segment) {
        segments.add(segment)
        requestLayout()
        invalidate()
    }

    fun setSegments(newSegments: List<Segment>) {
        segments.clear()
        segments.addAll(newSegments)
        if (selectedIndex >= segments.size) {
            selectedIndex = 0
        }
        requestLayout()
        invalidate()
    }

    fun setSelectedIndex(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= segments.size || index == selectedIndex) return
        
        val oldIndex = selectedIndex
        selectedIndex = index
        
        if (animate && width > 0) {
            animateToIndex(index)
        } else {
            updateIndicatorPosition()
            invalidate()
        }
        
        listener?.onSegmentSelected(index, segments[index])
        announceForAccessibility("Selected: ${segments[index].contentDescription}")
        
        // Haptic feedback
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    fun setOnSegmentSelectedListener(listener: OnSegmentSelectedListener?) {
        this.listener = listener
    }

    private fun updateIndicatorPosition() {
        if (segments.isEmpty()) return
        
        val segmentWidth = (width - 2 * indicatorMargin) / segments.size
        indicatorX = indicatorMargin + selectedIndex * segmentWidth
        indicatorWidth = segmentWidth
    }

    private fun animateToIndex(index: Int) {
        if (animatingToIndex == index) return
        
        animatingToIndex = index
        val targetX = indicatorMargin + index * ((width - 2 * indicatorMargin) / segments.size)
        
        // Smooth animation as per specifications (180-220ms)
        ObjectAnimator.ofFloat(this, "indicatorAnimX", indicatorX, targetX).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { 
                invalidate() 
            }
            start()
        }
    }

    @Suppress("unused") // Used by ObjectAnimator
    private fun setIndicatorAnimX(x: Float) {
        indicatorX = x
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (50 * context.resources.displayMetrics.density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        
        val desiredWidth = (320 * context.resources.displayMetrics.density).toInt() // 320dp default
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateIndicatorPosition()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (segments.isEmpty()) return
        
        val rect = RectF(0f, shadowOffset, width.toFloat(), height.toFloat())
        val shadowRect = RectF(shadowOffset, shadowOffset * 2, width.toFloat() + shadowOffset, height.toFloat() + shadowOffset)
        
        // Draw shadow
        canvas.drawRoundRect(shadowRect, containerRadius, containerRadius, shadowPaint)
        
        // Draw container
        canvas.drawRoundRect(rect, containerRadius, containerRadius, containerPaint)
        
        // Draw container border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glassContainerBorder
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
        }
        canvas.drawRoundRect(rect, containerRadius, containerRadius, borderPaint)
        
        // Draw indicator
        val indicatorRect = RectF(
            indicatorX,
            indicatorMargin,
            indicatorX + indicatorWidth,
            height - indicatorMargin
        )
        canvas.drawRoundRect(indicatorRect, indicatorRadius, indicatorRadius, indicatorPaint)
        
        // Draw indicator border
        val indicatorBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glassIndicatorBorder
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
        }
        canvas.drawRoundRect(indicatorRect, indicatorRadius, indicatorRadius, indicatorBorderPaint)
        
        // Draw text labels
        val segmentWidth = (width - 2 * indicatorMargin) / segments.size
        val textY = height / 2f + textPaint.textSize / 3f // Center vertically
        
        segments.forEachIndexed { index, segment ->
            val centerX = indicatorMargin + (index + 0.5f) * segmentWidth
            
            textPaint.color = if (index == selectedIndex) glassTextSelected else glassTextUnselected
            canvas.drawText(segment.text, centerX, textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isDragging = false
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val deltaX = kotlin.math.abs(event.x - downX)
                val deltaY = kotlin.math.abs(event.y - downY)
                if (deltaX > touchSlop || deltaY > touchSlop) {
                    isDragging = true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val segmentWidth = (width - 2 * indicatorMargin) / segments.size
                    val clickedIndex = ((event.x - indicatorMargin) / segmentWidth).toInt()
                    
                    if (clickedIndex >= 0 && clickedIndex < segments.size) {
                        setSelectedIndex(clickedIndex, true)
                    }
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate() // Redraw to show/hide focus ring
    }

    // Accessibility support
    override fun getContentDescription(): CharSequence? {
        return if (segments.isNotEmpty()) {
            "Segmented control, ${segments[selectedIndex].contentDescription} selected, ${selectedIndex + 1} of ${segments.size}"
        } else {
            super.getContentDescription()
        }
    }
}
