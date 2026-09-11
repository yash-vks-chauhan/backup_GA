package com.gridee.parking.ui.wallet

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.NoCopySpan
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.TextKeyListener
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.util.AttributeSet
import android.view.inputmethod.BaseInputConnection
import android.view.animation.PathInterpolator
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.os.ConfigurationCompat
import androidx.core.view.doOnPreDraw
import com.gridee.parking.ui.motion.AnimatorSettingsCompat
import java.util.Locale
import kotlin.math.min

/**
 * A real EditText: IME, cursor, selection, clipboard and accessibility remain native.
 * Only the glyph drawing is animated, briefly; the underlying amount changes synchronously.
 */
class WalletAmountEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {
    private val formatter = WalletAmountFormatter(
        ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
    )
    internal var amountResult = formatter.format("", 0)
        private set
    internal var onAmountChanged: (() -> Unit)? = null
    internal var onInputTooLong: (() -> Unit)? = null
    private var formatting = false
    private var previous = ""
    private var previousCursor = 0
    private var editStart = 0
    private var editCount = 0
    private var insertedCount = 0
    private var oldGlyphs = emptyList<Glyph>()
    private var newGlyphs = emptyList<Glyph>()
    private var matches = emptyMap<Int, Int>()
    private var animator: ValueAnimator? = null
    private var progress = 1f
    private var generation = 0
    private val location = IntArray(2)
    private val glyphPaint = TextPaint()
    private val hiddenText = object : CharacterStyle(), UpdateAppearance, NoCopySpan {
        override fun updateDrawState(paint: TextPaint) { paint.alpha = 0 }
    }

    init {
        // DigitsKeyListener strips '.' from pasted "10.5" before validation. Keep the numeric
        // keyboard, but let the validator see the complete input, including invalid decimals.
        keyListener = TextKeyListener.getInstance()
        setRawInputType(InputType.TYPE_CLASS_NUMBER)
        filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            if (dest.length - (dend - dstart) + (end - start) > 64) {
                onInputTooLong?.invoke()
                dest.subSequence(dstart, dend) // Reject the WHOLE replacement, never truncate money.
            } else null
        })
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (formatting) return
                stopGlyphMotion()
                oldGlyphs = captureGlyphs()
                previous = s.toString()
                previousCursor = selectionStart
                editStart = start
                editCount = count
                insertedCount = after
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(editable: Editable?) {
                if (formatting || editable == null) return
                if (BaseInputConnection.getComposingSpanStart(editable) >= 0) {
                    // Do not rewrite an IME's composition, but still validate the full value.
                    amountResult = formatter.format(editable.toString(), selectionStart, selectionEnd)
                        .copy(text = editable.toString())
                    onAmountChanged?.invoke()
                    return
                }
                var raw = editable.toString()
                var start = selectionStart.coerceAtLeast(0)
                var end = selectionEnd.coerceAtLeast(0)
                // Backspacing a formatting comma should delete the adjacent digit, not appear
                // stuck because the formatter immediately puts the comma back.
                if (editCount == 1 && insertedCount == 0 &&
                    previous.getOrNull(editStart)?.let { !it.isDigit() } == true &&
                    formatter.format(previous, 0).error == null
                ) {
                    val index = if (previousCursor > editStart) editStart - 1 else editStart
                    if (raw.getOrNull(index)?.isDigit() == true) {
                        raw = raw.removeRange(index, index + 1)
                        start = (start - if (index < start) 1 else 0).coerceAtLeast(0)
                        end = start
                    }
                }
                amountResult = formatter.format(
                    raw, start, end,
                    allowRegrouping = insertedCount <= 1 && formatter.format(previous, 0).error == null,
                )
                formatting = true
                try {
                    if (editable.toString() != amountResult.text) {
                        editable.replace(0, editable.length, amountResult.text)
                        setSelection(amountResult.selectionStart, amountResult.selectionEnd)
                    }
                } finally {
                    formatting = false
                }
                onAmountChanged?.invoke()
                if (previous != amountResult.text && amountResult.error == null &&
                    isFocused && isLaidOut && AnimatorSettingsCompat.areEnabled(context)
                ) animateGlyphChange()
            }
        })
    }

    private data class Glyph(val character: String, val x: Float, val baseline: Float)

    private fun captureGlyphs(): List<Glyph> {
        val textLayout = layout ?: return emptyList()
        val value = text?.toString().orEmpty()
        if (value.length > 24 || textLayout.text.toString() != value) return emptyList()
        getLocationInWindow(location)
        return value.mapIndexed { index, char ->
            Glyph(char.toString(), location[0] + totalPaddingLeft - scrollX +
                textLayout.getPrimaryHorizontal(index), (location[1] + baseline - scrollY).toFloat())
        }
    }

    private fun animateGlyphChange() {
        val version = generation
        doOnPreDraw {
            if (version != generation || !isFocused || !AnimatorSettingsCompat.areEnabled(context)) {
                return@doOnPreDraw
            }
            newGlyphs = captureGlyphs()
            if (newGlyphs.isEmpty() && oldGlyphs.isEmpty()) return@doOnPreDraw
            matches = AmountGlyphDiff.match(
                oldGlyphs.joinToString("") { it.character },
                newGlyphs.joinToString("") { it.character },
            )
            glyphPaint.set(paint)
            glyphPaint.color = currentTextColor
            text?.takeIf { it.isNotEmpty() }?.setSpan(hiddenText, 0, length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180L
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { clearGlyphMotion() }
                })
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas) // Native cursor and selection are never replaced or hidden.
        if (animator == null) return
        getLocationInWindow(location)
        val rise = 4f * resources.displayMetrics.density
        newGlyphs.forEachIndexed { index, glyph ->
            val old = matches[index]?.let { oldGlyphs[it] }
            val x = if (old == null) glyph.x else old.x + (glyph.x - old.x) * progress
            glyphPaint.alpha = if (old == null) (255 * min(1f, progress * 1.125f)).toInt() else 255
            canvas.drawText(glyph.character, x - location[0],
                glyph.baseline - location[1] + if (old == null) rise * (1 - progress) else 0f, glyphPaint)
        }
        val removedProgress = min(1f, progress * 1.5f)
        oldGlyphs.forEachIndexed { index, glyph ->
            if (index !in matches.values) {
                glyphPaint.alpha = (255 * (1 - removedProgress)).toInt()
                canvas.drawText(glyph.character, glyph.x - location[0],
                    glyph.baseline - location[1] - rise * removedProgress, glyphPaint)
            }
        }
    }

    internal fun stopGlyphMotion() {
        generation++
        animator?.cancel()
        clearGlyphMotion()
    }

    private fun clearGlyphMotion() {
        animator = null
        text?.removeSpan(hiddenText)
        oldGlyphs = emptyList()
        newGlyphs = emptyList()
        progress = 1f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopGlyphMotion()
        super.onDetachedFromWindow()
    }
}

/** Stable character matching keeps unchanged digits in place, including repeated numerals. */
internal object AmountGlyphDiff {
    fun match(old: String, new: String): Map<Int, Int> {
        val lengths = Array(old.length + 1) { IntArray(new.length + 1) }
        for (i in old.lastIndex downTo 0) for (j in new.lastIndex downTo 0) {
            lengths[i][j] = if (old[i] == new[j]) 1 + lengths[i + 1][j + 1]
                else maxOf(lengths[i + 1][j], lengths[i][j + 1])
        }
        val matches = mutableMapOf<Int, Int>()
        var i = 0
        var j = 0
        while (i < old.length && j < new.length) {
            when {
                old[i] == new[j] -> { matches[j++] = i++ }
                lengths[i + 1][j] > lengths[i][j + 1] -> i++
                else -> j++
            }
        }
        return matches
    }
}
