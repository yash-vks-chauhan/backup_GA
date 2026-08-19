package com.gridee.parking.ui.profile

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityLanguageRegionBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.AppLocaleManager
import com.gridee.parking.utils.LocaleTransition
import com.gridee.parking.utils.NotificationHelper
import com.gridee.parking.utils.ThemeManager

/**
 * Language & Region picker.
 *
 * Selecting a language applies it immediately. Because that recreates the
 * activity, the change is covered by the same snapshot + circular-reveal
 * transition the theme picker uses, so the switch reads as one deliberate
 * motion rather than a flicker.
 */
class LanguageRegionActivity : BaseActivity<ActivityLanguageRegionBinding>() {

    private var selectedCode: String = AppLocaleManager.DEFAULT_LANGUAGE
    private var isTornDown = false
    private var localeRestartInProgress = false
    private var pendingRevealRunnable: Runnable? = null
    private var bitmapToRecycle: Bitmap? = null

    /** Row + its check badge, keyed by language code. */
    private lateinit var rows: Map<String, Pair<View, View>>

    override fun getViewBinding(): ActivityLanguageRegionBinding =
        ActivityLanguageRegionBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rows = mapOf(
            "en" to (binding.rowEn to binding.checkEn),
            "hi" to (binding.rowHi to binding.checkHi),
            "ta" to (binding.rowTa to binding.checkTa),
            "te" to (binding.rowTe to binding.checkTe),
            "ml" to (binding.rowMl to binding.checkMl),
            "bn" to (binding.rowBn to binding.checkBn)
        )

        val isLocaleTransition = LocaleTransition.isPending()

        if (isLocaleTransition) {
            // Suppress the default enter animation and paint the outgoing snapshot
            // onto the window, so the frame between destroy and first draw shows
            // the old screen rather than whatever sits behind this activity.
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            LocaleTransition.bitmap?.let { bitmap ->
                window.setBackgroundDrawable(BitmapDrawable(resources, bitmap))
            }
        }

        val bgColor = ContextCompat.getColor(this, R.color.background_primary)
        window.statusBarColor = bgColor
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !ThemeManager.isDarkMode(this)

        selectedCode = AppLocaleManager.getEffectiveLanguage(this)
        setupClickListeners()
        renderSelection(animate = false)

        binding.btnBack.setOnClickListener { finish() }

        setupFrostedToolbar()

        if (isLocaleTransition) {
            performLocaleTransition()
        } else {
            animatePageEntry()
            // Landing here right after a change (e.g. the reveal was skipped because
            // the window had no size) still owes the user a confirmation.
            LocaleTransition.pendingConfirmationLabel?.let {
                LocaleTransition.pendingConfirmationLabel = null
                binding.root.postDelayed({ showChangeConfirmation(it) }, 260)
            }
        }
    }

    /**
     * Same frosted-header behaviour as the Profile page: the overlay dissolves in
     * as content slides under it, and the title firms up from 0.88 to full alpha.
     */
    private fun setupFrostedToolbar() {
        val frostView = binding.viewToolbarFrost
        val titleView = binding.tvToolbarTitle

        frostView.alpha = 0f
        titleView.alpha = 0.88f

        binding.scrollContent.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                // Dead zone: the first 16dp of travel draws no reaction.
                val deadZone = 16f.dpToPx()
                val activeScroll = (scrollY - deadZone).coerceAtLeast(0f)

                val frostRange = 120f.dpToPx()
                val rawFrost = (activeScroll / frostRange).coerceIn(0f, 1f)
                val t = 1f - rawFrost
                frostView.alpha = 1f - (t * t * t)

                val titleRange = 80f.dpToPx()
                val rawTitle = (activeScroll / titleRange).coerceIn(0f, 1f)
                titleView.alpha = 0.88f + (0.12f * rawTitle)

                // Overscroll safety — snap back to rest when fully scrolled up.
                if (scrollY <= 0) {
                    frostView.alpha = 0f
                    titleView.alpha = 0.88f
                }
            }
        )
    }

    // ─────────────────────────────  Entry motion  ─────────────────────────────

    private fun animatePageEntry() {
        val items = listOf(
            binding.tvLanguageTitle to 0L,
            binding.tvLanguageSub to 30L,
            binding.cardLanguages to 90L,
            binding.tvRegionTitle to 200L,
            binding.tvRegionSub to 230L,
            binding.cardRegion to 280L,
            binding.tvRegionNote to 340L
        )
        val rise = 14f.dpToPx()
        // Set the start state synchronously so the first frame already has them off.
        items.forEach { (view, _) ->
            view.alpha = 0f
            view.translationY = rise
        }
        items.forEach { (view, delay) ->
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380)
                .setStartDelay(delay)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()
        }
    }

    // ──────────────────────────────  Selection  ───────────────────────────────

    private fun setupClickListeners() {
        rows.forEach { (code, pair) ->
            val (row, _) = pair
            row.setOnClickListener {
                it.tapBounce()
                applyLanguageSelection(it, code)
            }
        }
    }

    private fun View.tapBounce() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        animate()
            .scaleX(0.98f).scaleY(0.98f)
            .setDuration(80)
            .setInterpolator(DecelerateInterpolator())
            .withLayer()
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(140)
                    .setInterpolator(DecelerateInterpolator())
                    .withLayer()
                    .start()
            }
            .start()
    }

    private fun renderSelection(animate: Boolean) {
        rows.forEach { (code, pair) ->
            val (_, badge) = pair
            applyBadgeState(badge, selected = code == selectedCode, animate = animate)
        }
    }

    private fun applyBadgeState(badge: View, selected: Boolean, animate: Boolean) {
        if (selected && badge.visibility != View.VISIBLE) {
            badge.alpha = 0f
            badge.scaleX = 0f
            badge.scaleY = 0f
            badge.visibility = View.VISIBLE
            if (animate) {
                badge.animate().alpha(1f).setDuration(120).start()
                springTo(badge, DynamicAnimation.SCALE_X, fromValue = 0f)
                springTo(badge, DynamicAnimation.SCALE_Y, fromValue = 0f)
            } else {
                badge.alpha = 1f
                badge.scaleX = 1f
                badge.scaleY = 1f
            }
        } else if (!selected && badge.visibility == View.VISIBLE) {
            if (animate) {
                badge.animate()
                    .alpha(0f).scaleX(0.4f).scaleY(0.4f)
                    .setDuration(140)
                    .withEndAction {
                        badge.visibility = View.GONE
                        badge.alpha = 1f
                        badge.scaleX = 1f
                        badge.scaleY = 1f
                    }
                    .start()
            } else {
                badge.visibility = View.GONE
            }
        }
    }

    private fun springTo(view: View, property: DynamicAnimation.ViewProperty, fromValue: Float) {
        SpringAnimation(view, property, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = 0.55f
                stiffness = 600f
            }
            setStartValue(fromValue)
            start()
        }
    }

    private fun applyLanguageSelection(originView: View, newCode: String) {
        if (newCode == selectedCode) return

        LocaleTransition.clear(recycleBitmap = true)

        // Capture the outgoing screen before any state changes, so the snapshot
        // shows the language the user is leaving.
        val w = binding.root.width
        val h = binding.root.height
        if (w > 0 && h > 0) {
            try {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(ContextCompat.getColor(this, R.color.background_primary))
                binding.root.draw(canvas)
                LocaleTransition.bitmap = bitmap

                // Reveal origin: the tapped row, in window coordinates so it stays
                // valid across the recreate.
                val loc = IntArray(2).also { originView.getLocationInWindow(it) }
                LocaleTransition.center = intArrayOf(
                    loc[0] + originView.width / 2,
                    loc[1] + originView.height / 2
                )
                LocaleTransition.languageLabel = AppLocaleManager.languageFor(newCode).nativeName
            } catch (_: OutOfMemoryError) {
                LocaleTransition.clear(recycleBitmap = true)
            } catch (_: Exception) {
                LocaleTransition.clear(recycleBitmap = true)
            }
        }

        // Shown by whichever instance lands after the restart.
        LocaleTransition.pendingConfirmationLabel =
            AppLocaleManager.languageFor(newCode).nativeName

        selectedCode = newCode
        setRowsEnabled(false)
        localeRestartInProgress = true

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // setApplicationLocales recreates the activity itself (AppCompat below
        // API 33, the framework above it) — we must not call recreate() as well.
        AppLocaleManager.setLocale(this, newCode)
    }

    // ─────────────────────────────  Transition  ───────────────────────────────

    private fun performLocaleTransition() {
        setRowsEnabled(false)

        val oldBitmap = LocaleTransition.bitmap
        val label = LocaleTransition.languageLabel ?: ""
        val origin = LocaleTransition.center

        LocaleTransition.bitmap = null
        LocaleTransition.center = null
        LocaleTransition.languageLabel = null

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        bitmapToRecycle = oldBitmap

        // Material 3 motion curves, matched to the theme-change reveal.
        val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)
        val standardEnter = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val emphasizedExit = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

        val rootLayout = findViewById<FrameLayout>(android.R.id.content)
        val isDarkNow = ThemeManager.isDarkMode(this)
        val dp = resources.displayMetrics.density

        val snapshotView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageBitmap(oldBitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        val scrimView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(
                if (isDarkNow) Color.parseColor("#A0000000") else Color.parseColor("#90FFFFFF")
            )
            alpha = 0f
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        val tintColor = if (isDarkNow) Color.WHITE else Color.BLACK

        val iconView = ImageView(this).apply {
            val sz = (30 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (14 * dp).toInt()
            }
            setImageResource(R.drawable.ic_language)
            setColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN)
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
        }

        // The incoming language's own name is the hero of the transition.
        val labelView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTextColor(tintColor)
            typeface = try {
                ResourcesCompat.getFont(this@LanguageRegionActivity, R.font.inter_semibold)
            } catch (_: Exception) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            alpha = 0f
            translationY = 8 * dp
        }

        val lineTargetWidth = (40 * dp).toInt()
        val accentLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (1 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (12 * dp).toInt()
            }
            setBackgroundColor(
                if (isDarkNow) Color.parseColor("#22FFFFFF") else Color.parseColor("#15000000")
            )
            alpha = 0f
        }

        val titleCard = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            addView(iconView)
            addView(labelView)
            addView(accentLine)
        }

        rootLayout.addView(snapshotView, 0)
        rootLayout.addView(scrimView, 1)
        rootLayout.addView(titleCard)
        binding.root.visibility = View.INVISIBLE

        binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        scrimView.animate().alpha(1f).setDuration(360).setInterpolator(emphasized).start()

        iconView.postDelayed({
            iconView.animate().alpha(1f).setDuration(240).setInterpolator(standardEnter).start()
            SpringAnimation(iconView, DynamicAnimation.SCALE_X, 1f).apply {
                spring = SpringForce(1f).apply { dampingRatio = 0.78f; stiffness = 380f }
                setStartValue(0.4f)
                start()
            }
            SpringAnimation(iconView, DynamicAnimation.SCALE_Y, 1f).apply {
                spring = SpringForce(1f).apply { dampingRatio = 0.78f; stiffness = 380f }
                setStartValue(0.4f)
                start()
            }
        }, 60)

        labelView.animate()
            .alpha(1f).translationY(0f)
            .setDuration(280).setStartDelay(140)
            .setInterpolator(standardEnter)
            .start()

        accentLine.animate()
            .alpha(1f).setDuration(240).setStartDelay(220)
            .setInterpolator(standardEnter)
            .start()

        ValueAnimator.ofInt(0, lineTargetWidth).apply {
            duration = 240
            startDelay = 220
            interpolator = standardEnter
            addUpdateListener { anim ->
                accentLine.layoutParams.width = anim.animatedValue as Int
                accentLine.requestLayout()
            }
            start()
        }

        val revealRunnable = Runnable {
            if (isActivityTornDown()) return@Runnable

            if (binding.root.width <= 0 || binding.root.height <= 0) {
                binding.root.visibility = View.VISIBLE
                cleanupOverlay(snapshotView, scrimView, titleCard)
                setRowsEnabled(true)
                consumeConfirmation()
                return@Runnable
            }

            binding.root.visibility = View.VISIBLE

            val rootLoc = IntArray(2).also { binding.root.getLocationInWindow(it) }
            val rawCx = origin?.getOrNull(0)?.minus(rootLoc[0]) ?: (binding.root.width / 2)
            val rawCy = origin?.getOrNull(1)?.minus(rootLoc[1]) ?: (binding.root.height / 2)
            val cx = rawCx.coerceIn(0, binding.root.width)
            val cy = rawCy.coerceIn(0, binding.root.height)

            // Furthest corner from the origin, so the circle covers every pixel.
            val maxDx = maxOf(cx.toFloat(), (binding.root.width - cx).toFloat())
            val maxDy = maxOf(cy.toFloat(), (binding.root.height - cy).toFloat())
            val finalRadius = kotlin.math.hypot(maxDx, maxDy)

            val revealDuration = 700L

            // New language blooms forward from the tapped row.
            binding.root.pivotX = cx.toFloat()
            binding.root.pivotY = cy.toFloat()
            binding.root.scaleX = 1.04f
            binding.root.scaleY = 1.04f
            binding.root.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            binding.root.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(revealDuration)
                .setInterpolator(emphasized)
                .withEndAction { binding.root.setLayerType(View.LAYER_TYPE_NONE, null) }
                .start()

            // Old language recedes into soft focus. RenderEffect needs API 31+;
            // older devices get the plain fade below.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val maxBlurPx = 18f * dp
                ValueAnimator.ofFloat(0f, maxBlurPx).apply {
                    duration = revealDuration
                    interpolator = emphasized
                    addUpdateListener { anim ->
                        val r = anim.animatedValue as Float
                        snapshotView.setRenderEffect(
                            if (r > 0.5f) android.graphics.RenderEffect.createBlurEffect(
                                r, r, android.graphics.Shader.TileMode.CLAMP
                            ) else null
                        )
                    }
                    start()
                }
            }

            titleCard.animate()
                .alpha(0f).scaleX(1.06f).scaleY(1.06f).translationY(-6 * dp)
                .setDuration(280)
                .setInterpolator(emphasizedExit)
                .start()

            snapshotView.animate().alpha(0f).setDuration(revealDuration)
                .setInterpolator(emphasized).start()
            scrimView.animate().alpha(0f).setDuration(revealDuration)
                .setInterpolator(emphasized).start()

            val reveal = android.view.ViewAnimationUtils.createCircularReveal(
                binding.root, cx, cy, 0f, finalRadius
            )
            reveal.duration = revealDuration
            reveal.interpolator = emphasized
            reveal.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isActivityTornDown()) return
                    cleanupOverlay(snapshotView, scrimView, titleCard)
                    setRowsEnabled(true)
                    consumeConfirmation()
                }
            })
            reveal.start()
        }
        pendingRevealRunnable = revealRunnable
        binding.root.postDelayed(revealRunnable, 480)
    }

    /** Shows the confirmation card once, in the language just switched to. */
    private fun consumeConfirmation() {
        val label = LocaleTransition.pendingConfirmationLabel ?: return
        LocaleTransition.pendingConfirmationLabel = null
        showChangeConfirmation(label)
    }

    private fun showChangeConfirmation(label: String) {
        if (isActivityTornDown()) return
        NotificationHelper.showSuccess(
            parent = binding.root,
            title = getString(R.string.language_changed_title),
            message = getString(R.string.language_changed_message, label),
            duration = 3200L
        )
    }

    private fun setRowsEnabled(enabled: Boolean) {
        rows.values.forEach { (row, _) ->
            row.isEnabled = enabled
            row.isClickable = enabled
        }
    }

    private fun cleanupOverlay(snapshotView: ImageView, scrimView: View, titleCard: View) {
        (snapshotView.parent as? ViewGroup)?.removeView(snapshotView)
        (scrimView.parent as? ViewGroup)?.removeView(scrimView)
        (titleCard.parent as? ViewGroup)?.removeView(titleCard)
        snapshotView.setLayerType(View.LAYER_TYPE_NONE, null)
        scrimView.setLayerType(View.LAYER_TYPE_NONE, null)
        titleCard.setLayerType(View.LAYER_TYPE_NONE, null)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            snapshotView.setRenderEffect(null)
        }
        snapshotView.setImageDrawable(null)
        window.setBackgroundDrawable(
            ColorDrawable(ContextCompat.getColor(this, R.color.background_primary))
        )
        bitmapToRecycle?.takeIf { !it.isRecycled }?.recycle()
        bitmapToRecycle = null
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density

    private fun isActivityTornDown(): Boolean = isTornDown || isFinishing || isDestroyed

    override fun onDestroy() {
        isTornDown = true
        pendingRevealRunnable?.let { binding.root.removeCallbacks(it) }
        pendingRevealRunnable = null
        bitmapToRecycle?.takeIf { !it.isRecycled }?.recycle()
        bitmapToRecycle = null
        if (localeRestartInProgress) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        super.onDestroy()
    }
}
