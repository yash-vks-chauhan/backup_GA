package com.gridee.parking.ui.qr

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat

internal data class ScannerPanelRenderState(
    val panelBackgroundRes: Int,
    val iconBackgroundRes: Int,
    val iconRes: Int,
    val iconTintColor: Int,
    val badgeBackgroundRes: Int,
    val badgeTextColor: Int,
    val badgeText: String,
    val title: String,
    val subtitle: String,
    val meta: String,
    val showMeta: Boolean,
    val showProgress: Boolean,
    val progressTintColor: Int,
    val showResultActions: Boolean,
    val showManualFallback: Boolean,
)

/** Applies one resolved scanner panel state while suppressing identical Android View mutations. */
internal class ScannerUiRenderer(
    private val statusContainer: View,
    private val statusIconContainer: View,
    private val statusIcon: ImageView,
    private val statusBadge: TextView,
    private val statusTitle: TextView,
    private val statusText: TextView,
    private val statusMeta: TextView,
    private val statusProgress: ProgressBar,
    private val resultActionsContainer: View,
    private val manualFallbackButton: View,
    cornerTopLeft: ImageView,
    cornerTopRight: ImageView,
    cornerBottomLeft: ImageView,
    cornerBottomRight: ImageView,
    private val visualCache: ScannerPanelVisualCache = ScannerPanelVisualCache(),
) {
    private val corners = arrayOf(
        cornerTopLeft,
        cornerTopRight,
        cornerBottomLeft,
        cornerBottomRight,
    )
    private var cornerTargetColor: Int? = null
    private var cornerAnimator: ValueAnimator? = null

    fun render(state: ScannerPanelRenderState) {
        applyResource(
            ScannerPanelVisualCache.Field.PANEL_BACKGROUND,
            state.panelBackgroundRes,
            statusContainer::setBackgroundResource,
        )
        applyResource(
            ScannerPanelVisualCache.Field.ICON_BACKGROUND,
            state.iconBackgroundRes,
            statusIconContainer::setBackgroundResource,
        )
        applyResource(
            ScannerPanelVisualCache.Field.ICON_RESOURCE,
            state.iconRes,
            statusIcon::setImageResource,
        )
        if (visualCache.shouldApply(ScannerPanelVisualCache.Field.ICON_TINT_COLOR, state.iconTintColor)) {
            statusIcon.imageTintList = ColorStateList.valueOf(state.iconTintColor)
        }
        applyResource(
            ScannerPanelVisualCache.Field.BADGE_BACKGROUND,
            state.badgeBackgroundRes,
            statusBadge::setBackgroundResource,
        )
        if (visualCache.shouldApply(
                ScannerPanelVisualCache.Field.BADGE_TEXT_COLOR,
                state.badgeTextColor,
            )
        ) {
            statusBadge.setTextColor(state.badgeTextColor)
        }

        setTextIfChanged(statusBadge, state.badgeText)
        setTextIfChanged(statusTitle, state.title)
        setTextIfChanged(statusText, state.subtitle)
        setTextIfChanged(statusMeta, state.meta)
        setVisibilityIfChanged(statusMeta, state.showMeta)
        setVisibilityIfChanged(statusProgress, state.showProgress)
        if (visualCache.shouldApply(
                ScannerPanelVisualCache.Field.PROGRESS_TINT_COLOR,
                state.progressTintColor,
            )
        ) {
            statusProgress.indeterminateTintList = ColorStateList.valueOf(state.progressTintColor)
        }
        setVisibilityIfChanged(resultActionsContainer, state.showResultActions)
        setVisibilityIfChanged(manualFallbackButton, state.showManualFallback)
    }

    fun animateCornerColor(targetColor: Int) {
        if (cornerTargetColor == targetColor) return
        cornerTargetColor = targetColor
        cornerAnimator?.cancel()
        val startColor = ImageViewCompat.getImageTintList(corners[0])?.defaultColor
            ?: targetColor
        cornerAnimator = ValueAnimator.ofArgb(startColor, targetColor).apply {
            duration = 400L
            addUpdateListener { animator ->
                val tint = ColorStateList.valueOf(animator.animatedValue as Int)
                corners.forEach { corner -> ImageViewCompat.setImageTintList(corner, tint) }
            }
            start()
        }
    }

    fun resetCornerColor(color: Int) {
        cornerAnimator?.cancel()
        cornerAnimator = null
        cornerTargetColor = null
        val tint = ColorStateList.valueOf(color)
        corners.forEach { corner -> ImageViewCompat.setImageTintList(corner, tint) }
    }

    fun close() {
        cornerAnimator?.cancel()
        cornerAnimator = null
    }

    private fun applyResource(
        field: ScannerPanelVisualCache.Field,
        resource: Int,
        apply: (Int) -> Unit,
    ) {
        if (visualCache.shouldApply(field, resource)) apply(resource)
    }

    private fun setTextIfChanged(view: TextView, value: String) {
        if (view.text != value) view.text = value
    }

    private fun setVisibilityIfChanged(view: View, visible: Boolean) {
        if (view.isVisible != visible) view.isVisible = visible
    }
}
