package com.gridee.parking.ui.profile

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
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
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityDisplayThemeBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.ThemeManager

class DisplayThemeActivity : BaseActivity<ActivityDisplayThemeBinding>() {

    private var initialHeroGradientKey: String = "obsidian_dip"
    private var initialThemeMode: String = ThemeManager.MODE_LIGHT

    private var selectedHeroGradient: String = "obsidian_dip"
    private var selectedThemeMode: String = ThemeManager.MODE_LIGHT

    private val currentHeroResMap = mutableMapOf<View, Int>()
    private var isTornDown = false
    private var themeRestartInProgress = false
    private var pendingRevealRunnable: Runnable? = null
    private var transitionBitmapToRecycle: Bitmap? = null

    override fun getViewBinding(): ActivityDisplayThemeBinding =
        ActivityDisplayThemeBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isThemeTransition = ThemeManager.transitionBitmap != null
        if (isThemeTransition) {
            // Suppress the default enter animation so the new activity appears
            // instantly, not as a fade/slide that would betray the recreate.
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)

            // Paint the outgoing-theme snapshot straight onto the window so the
            // brief frame between the old activity's destroy and the new one's
            // first draw shows the snapshot — not the ProfileFragment underneath.
            ThemeManager.transitionBitmap?.let { bitmap ->
                window.setBackgroundDrawable(BitmapDrawable(resources, bitmap))
            }

            // Hold the outgoing theme's system bars in place so the snapshot below
            // them stays edge-to-edge until the reveal animates the chrome over.
            ThemeManager.transitionOldStatusBarColor?.let { window.statusBarColor = it }
            ThemeManager.transitionOldNavigationBarColor?.let { window.navigationBarColor = it }
            ThemeManager.transitionOldIsDark?.let { wasDark ->
                WindowInsetsControllerCompat(window, window.decorView)
                    .isAppearanceLightStatusBars = !wasDark
            }
            performThemeTransition()
        } else {
            val bgColor = ContextCompat.getColor(this, R.color.background_primary)
            window.statusBarColor = bgColor
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
                !ThemeManager.isDarkMode(this)
        }

        loadInitialState()
        setupClickListeners()

        // Keep selection badges above any animated card elevation. Without this,
        // a card raised to 6dp would render in front of the badge sibling.
        liftSelectionBadges()

        renderSelection(animate = false)
        scrollGradientPickerToSelected(animate = false)

        // Staggered fade-in entry on a clean open. We skip this when arriving via a
        // theme transition since the circular reveal is itself the entry animation.
        if (!isThemeTransition) {
            animatePageEntry()
        }

        binding.btnBack.setOnClickListener { finish() }

        // Continuously update the gradient picker's edge fades as the user scrolls,
        // so each side fades in only when there is actually content past that edge.
        binding.scrollGradient.setOnScrollChangeListener { _, _, _, _, _ ->
            updateGradientFades()
        }

        // Fade in the appbar hairline when the content scrolls under it.
        binding.scrollContent.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                val targetAlpha = if (scrollY > 4) 1f else 0f
                val divider = binding.appbarDivider
                if (divider.alpha != targetAlpha) {
                    divider.animate()
                        .alpha(targetAlpha)
                        .setDuration(180)
                        .start()
                }
            }
        )
    }

    private fun animatePageEntry() {
        val items = listOf(
            binding.tvAppearanceTitle to 0L,
            binding.tvAppearanceSub to 30L,
            binding.phoneRow to 90L,
            binding.btnMatchSystem to 180L,
            binding.tvGradientTitle to 260L,
            binding.tvGradientSub to 290L,
            binding.gradientPickerFrame to 340L
        )
        val rise = 14f.dpToPx()
        // Set initial state synchronously so the first frame already has them off.
        items.forEach { (view, _) ->
            view.alpha = 0f
            view.translationY = rise
        }
        // Animate them in with stagger.
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

    private fun scrollGradientPickerToSelected(animate: Boolean) {
        val target: View = when (selectedHeroGradient) {
            "midnight_slate" -> binding.swatchMidnightSlate
            "obsidian_dip" -> binding.swatchObsidianDip
            "carbon_mist" -> binding.swatchCarbonMist
            "lavender_whisper" -> binding.swatchLavenderWhisper
            else -> binding.swatchObsidianDip
        }
        val scroll = binding.scrollGradient
        scroll.post {
            // Center the selected swatch in the visible area when possible.
            val targetCenter = target.left + target.width / 2
            val desiredScrollX = targetCenter - scroll.width / 2
            val maxScroll = (scroll.getChildAt(0)?.width ?: 0) - scroll.width
            val clamped = desiredScrollX.coerceIn(0, maxScroll.coerceAtLeast(0))
            if (animate) scroll.smoothScrollTo(clamped, 0) else scroll.scrollTo(clamped, 0)
            updateGradientFades()
        }
    }

    private fun updateGradientFades() {
        val scroll = binding.scrollGradient
        val contentWidth = scroll.getChildAt(0)?.width ?: 0
        val maxScroll = (contentWidth - scroll.width).coerceAtLeast(0)

        // No fade is needed when content fits without scrolling.
        if (maxScroll == 0) {
            binding.fadeStart.alpha = 0f
            binding.fadeEnd.alpha = 0f
            return
        }

        // Fade comes in over the first / last `fadeRange` pixels of scroll travel,
        // so the transition is smooth and continuous instead of snapping at the edges.
        val fadeRange = 32f.dpToPx()
        val scrollX = scroll.scrollX.toFloat()
        binding.fadeStart.alpha = (scrollX / fadeRange).coerceIn(0f, 1f)
        binding.fadeEnd.alpha = ((maxScroll - scrollX) / fadeRange).coerceIn(0f, 1f)
    }

    private fun loadInitialState() {
        val sharedPref = getSharedPreferences("gridee_prefs", Context.MODE_PRIVATE)
        initialHeroGradientKey =
            sharedPref.getString("hero_gradient_key", "obsidian_dip") ?: "obsidian_dip"
        initialThemeMode = ThemeManager.getSavedThemeMode(this)
        selectedHeroGradient = initialHeroGradientKey
        selectedThemeMode = initialThemeMode
    }

    private fun setupClickListeners() {
        binding.btnPhoneLight.setOnClickListener {
            it.tapBounce()
            applyThemeSelection(it, ThemeManager.MODE_LIGHT)
        }
        binding.btnPhoneDark.setOnClickListener {
            it.tapBounce()
            applyThemeSelection(it, ThemeManager.MODE_DARK)
        }
        binding.btnMatchSystem.setOnClickListener {
            it.tapBounce()
            applyThemeSelection(it, ThemeManager.MODE_SYSTEM)
        }

        val swatches = listOf(
            binding.swatchMidnightSlate to "midnight_slate",
            binding.swatchObsidianDip to "obsidian_dip",
            binding.swatchCarbonMist to "carbon_mist",
            binding.swatchLavenderWhisper to "lavender_whisper"
        )
        swatches.forEach { (container, key) ->
            container.setOnClickListener {
                it.tapBounce()
                applyGradientSelection(key)
            }
        }
    }

    private fun View.tapBounce() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        animate()
            .scaleX(0.96f).scaleY(0.96f)
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
        // Phone hero gradients (both phones reflect the currently picked gradient)
        val (lightHero, darkHero) = previewDrawablesFor(selectedHeroGradient)
        crossfadeBackground(binding.phoneLightHero, lightHero, animate)
        crossfadeBackground(binding.phoneDarkHero, darkHero, animate)

        // Theme picker selection state
        applyCardSelection(
            card = binding.cardPhoneLight,
            badge = binding.checkPhoneLight,
            selected = selectedThemeMode == ThemeManager.MODE_LIGHT,
            animate = animate
        )
        applyCardSelection(
            card = binding.cardPhoneDark,
            badge = binding.checkPhoneDark,
            selected = selectedThemeMode == ThemeManager.MODE_DARK,
            animate = animate
        )

        val systemSelected = selectedThemeMode == ThemeManager.MODE_SYSTEM
        binding.btnMatchSystem.isSelected = systemSelected
        val systemContentColor = ContextCompat.getColor(
            this,
            if (systemSelected) R.color.md3_on_primary else R.color.text_primary
        )
        binding.lblMatchSystem.setTextColor(systemContentColor)
        binding.iconMatchSystem.setColorFilter(systemContentColor)

        // Gradient swatch selection state
        val swatchSelections = listOf(
            Triple(binding.cardSwatchMidnightSlate, binding.checkSwatchMidnightSlate, "midnight_slate"),
            Triple(binding.cardSwatchObsidianDip, binding.checkSwatchObsidianDip, "obsidian_dip"),
            Triple(binding.cardSwatchCarbonMist, binding.checkSwatchCarbonMist, "carbon_mist"),
            Triple(binding.cardSwatchLavenderWhisper, binding.checkSwatchLavenderWhisper, "lavender_whisper")
        )
        swatchSelections.forEach { (card, badge, key) ->
            applyCardSelection(card, badge, key == selectedHeroGradient, animate)
        }
    }

    private fun previewDrawablesFor(key: String): Pair<Int, Int> = when (key) {
        "midnight_slate" -> R.drawable.bg_hero_preview_light_midnight_slate to
                R.drawable.bg_hero_preview_dark_midnight_slate
        "obsidian_dip" -> R.drawable.bg_hero_preview_light_obsidian_dip to
                R.drawable.bg_hero_preview_dark_obsidian_dip
        "carbon_mist" -> R.drawable.bg_hero_preview_light_carbon_mist to
                R.drawable.bg_hero_preview_dark_carbon_mist
        "lavender_whisper" -> R.drawable.bg_hero_preview_light_lavender_whisper to
                R.drawable.bg_hero_preview_dark_lavender_whisper
        else -> R.drawable.bg_hero_preview_light_obsidian_dip to
                R.drawable.bg_hero_preview_dark_obsidian_dip
    }

    private fun applyCardSelection(
        card: MaterialCardView,
        badge: View,
        selected: Boolean,
        animate: Boolean
    ) {
        // Selection is signalled by elevation + check badge alone — no colored ring.
        // The resting hairline stroke stays in place to define the card edge.

        // Lift selected card with a soft drop shadow.
        val targetElevation = (if (selected) 6f else 0f).dpToPx()
        if (animate && card.cardElevation != targetElevation) {
            ValueAnimator.ofFloat(card.cardElevation, targetElevation).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { card.cardElevation = it.animatedValue as Float }
                start()
            }
        } else {
            card.cardElevation = targetElevation
        }

        if (selected && badge.visibility != View.VISIBLE) {
            badge.scaleX = 0f
            badge.scaleY = 0f
            badge.alpha = 0f
            badge.visibility = View.VISIBLE
            if (animate) {
                badge.animate().alpha(1f).setDuration(120).start()
                springTo(badge, DynamicAnimation.SCALE_X, 1f, fromValue = 0f)
                springTo(badge, DynamicAnimation.SCALE_Y, 1f, fromValue = 0f)
            } else {
                badge.scaleX = 1f
                badge.scaleY = 1f
                badge.alpha = 1f
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

    private fun springTo(
        view: View,
        property: DynamicAnimation.ViewProperty,
        target: Float,
        fromValue: Float
    ) {
        SpringAnimation(view, property, target).apply {
            spring = SpringForce(target).apply {
                dampingRatio = 0.55f
                stiffness = 600f
            }
            setStartValue(fromValue)
            start()
        }
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density

    private fun liftSelectionBadges() {
        val badgeZ = 14f.dpToPx()
        val noShadow = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                // Empty outline: badge stays above the card via translationZ but
                // does not cast its own shadow.
            }
        }
        listOf(
            binding.checkPhoneLight,
            binding.checkPhoneDark,
            binding.checkSwatchMidnightSlate,
            binding.checkSwatchObsidianDip,
            binding.checkSwatchCarbonMist,
            binding.checkSwatchLavenderWhisper
        ).forEach { badge ->
            badge.translationZ = badgeZ
            badge.outlineProvider = noShadow
        }
    }

    private fun crossfadeBackground(view: View, newResId: Int, animate: Boolean) {
        val currentResId = currentHeroResMap[view]
        if (currentResId == newResId && view.background != null) return

        if (!animate || currentResId == null) {
            view.setBackgroundResource(newResId)
            currentHeroResMap[view] = newResId
            return
        }

        val current = ContextCompat.getDrawable(this, currentResId)?.mutate()
        val next = ContextCompat.getDrawable(this, newResId)?.mutate() ?: return
        if (current == null) {
            view.background = next
            currentHeroResMap[view] = newResId
            return
        }
        val transition = TransitionDrawable(arrayOf(current, next))
        transition.isCrossFadeEnabled = true
        view.background = transition
        transition.startTransition(220)
        currentHeroResMap[view] = newResId
    }

    private fun applyGradientSelection(newGradient: String) {
        if (newGradient == selectedHeroGradient) return
        selectedHeroGradient = newGradient
        initialHeroGradientKey = newGradient
        renderSelection(animate = true)

        getSharedPreferences("gridee_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("hero_gradient_key", newGradient)
            .apply()
    }

    private fun applyThemeSelection(originView: View, newTheme: String) {
        if (newTheme == selectedThemeMode) return

        // If the saved theme isn't actually changing (e.g. tapping Light when
        // the page is already in Light mode), just update the visible selection
        // and skip the bitmap-capture transition.
        if (newTheme == initialThemeMode) {
            selectedThemeMode = newTheme
            renderSelection(animate = true)
            return
        }

        clearPendingThemeTransitionState(recycleBitmap = true)

        // Capture the current screen for the circular-reveal transition before
        // we mutate any state — the bitmap should show the *outgoing* look.
        val w = binding.root.width
        val h = binding.root.height
        if (w > 0 && h > 0) {
            try {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(ContextCompat.getColor(this, R.color.background_primary))
                binding.root.draw(canvas)
                ThemeManager.transitionBitmap = bitmap

                // Origin of the reveal: center of the tapped control, in window
                // coordinates so it's stable across the activity recreate.
                val loc = IntArray(2).also { originView.getLocationInWindow(it) }
                ThemeManager.transitionCenter = intArrayOf(
                    loc[0] + originView.width / 2,
                    loc[1] + originView.height / 2
                )

                ThemeManager.transitionOldStatusBarColor = window.statusBarColor
                ThemeManager.transitionOldNavigationBarColor = window.navigationBarColor
                ThemeManager.transitionOldIsDark = ThemeManager.isDarkMode(this)

                ThemeManager.transitionThemeLabel = when (newTheme) {
                    ThemeManager.MODE_DARK -> "Dark Mode"
                    ThemeManager.MODE_LIGHT -> "Light Mode"
                    ThemeManager.MODE_SYSTEM -> "System Default"
                    else -> ""
                }
            } catch (_: OutOfMemoryError) {
                clearPendingThemeTransitionState(recycleBitmap = true)
            } catch (_: Exception) {
                clearPendingThemeTransitionState(recycleBitmap = true)
            }
        }

        selectedThemeMode = newTheme
        setThemeControlsEnabled(false)
        ThemeManager.saveThemeMode(this, newTheme)
        initialThemeMode = newTheme
        // Suppress the outgoing activity's exit animation; without this the
        // OS plays a default fade that can briefly reveal the ProfileFragment
        // underneath during the destroy→recreate cycle.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        themeRestartInProgress = true
        ThemeManager.applyTheme(newTheme)
        binding.root.post {
            if (isActivityTornDown()) return@post
            recreate()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun performThemeTransition() {
        setThemeControlsEnabled(false)
        val oldBitmap = ThemeManager.transitionBitmap
        val themeLabel = ThemeManager.transitionThemeLabel ?: ""
        val origin = ThemeManager.transitionCenter
        val oldStatusBarColor = ThemeManager.transitionOldStatusBarColor
        val oldNavigationBarColor = ThemeManager.transitionOldNavigationBarColor

        ThemeManager.transitionBitmap = null
        ThemeManager.transitionCenter = null
        ThemeManager.transitionThemeLabel = null
        ThemeManager.transitionOldStatusBarColor = null
        ThemeManager.transitionOldNavigationBarColor = null
        ThemeManager.transitionOldIsDark = null

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        transitionBitmapToRecycle = oldBitmap

        // Material 3 motion curves. `emphasized` is the lead curve for the
        // spatial reveal — strong start, gentle settle. `standardEnter` carries
        // the title-card pieces in; `emphasizedExit` carries them out.
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
                if (isDarkNow) Color.parseColor("#A0000000")
                else Color.parseColor("#90FFFFFF")
            )
            alpha = 0f
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        val tintColor = if (isDarkNow) Color.WHITE else Color.BLACK
        val iconRes = when {
            themeLabel.contains("Dark", ignoreCase = true) -> R.drawable.ic_crescent_moon
            themeLabel.contains("Light", ignoreCase = true) -> R.drawable.ic_sun
            else -> R.drawable.ic_app_theme_modern
        }

        val iconView = ImageView(this).apply {
            val sz = (32 * dp).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(sz, sz).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (12 * dp).toInt()
            }
            setImageResource(iconRes)
            setColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN)
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
        }

        val labelView = TextView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
            text = themeLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(tintColor)
            typeface = try {
                ResourcesCompat.getFont(this@DisplayThemeActivity, R.font.inter_semibold)
            } catch (_: Exception) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            alpha = 0f
            letterSpacing = 0.08f
            translationY = 8 * dp
        }

        val lineTargetWidth = (40 * dp).toInt()
        val accentLine = View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, (1 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (10 * dp).toInt()
            }
            setBackgroundColor(
                if (isDarkNow) Color.parseColor("#22FFFFFF")
                else Color.parseColor("#15000000")
            )
            alpha = 0f
        }

        val titleCardContainer = android.widget.LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            addView(iconView)
            addView(labelView)
            addView(accentLine)
        }

        rootLayout.addView(snapshotView, 0)
        rootLayout.addView(scrimView, 1)
        rootLayout.addView(titleCardContainer)
        binding.root.visibility = View.INVISIBLE

        binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        scrimView.animate()
            .alpha(1f)
            .setDuration(360)
            .setInterpolator(emphasized)
            .start()

        // Title-card entrance is tightened so it lands ~80ms before the reveal,
        // giving the reveal itself more room to breathe.
        iconView.postDelayed({
            iconView.animate()
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(standardEnter)
                .start()

            SpringAnimation(iconView, DynamicAnimation.SCALE_X, 1f).apply {
                spring = SpringForce(1f).apply {
                    dampingRatio = 0.78f
                    stiffness = 380f
                }
                setStartValue(0.4f)
                start()
            }
            SpringAnimation(iconView, DynamicAnimation.SCALE_Y, 1f).apply {
                spring = SpringForce(1f).apply {
                    dampingRatio = 0.78f
                    stiffness = 380f
                }
                setStartValue(0.4f)
                start()
            }
        }, 60)

        labelView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(280)
            .setStartDelay(140)
            .setInterpolator(standardEnter)
            .start()

        accentLine.animate()
            .alpha(1f)
            .setDuration(240)
            .setStartDelay(220)
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
                cleanupThemeTransitionOverlay(
                    snapshotView = snapshotView,
                    scrimView = scrimView,
                    titleCardContainer = titleCardContainer,
                    newWindowColor = ContextCompat.getColor(this, R.color.background_primary)
                )
                setThemeControlsEnabled(true)
                return@Runnable
            }

            binding.root.visibility = View.VISIBLE

            // Reveal origin: where the user actually tapped. The captured
            // window-coords are translated into binding.root local-coords so the
            // circle blooms from the fingertip rather than screen center.
            val rootLoc = IntArray(2).also { binding.root.getLocationInWindow(it) }
            val rawCx = origin?.getOrNull(0)?.minus(rootLoc[0]) ?: (binding.root.width / 2)
            val rawCy = origin?.getOrNull(1)?.minus(rootLoc[1]) ?: (binding.root.height / 2)
            val cx = rawCx.coerceIn(0, binding.root.width)
            val cy = rawCy.coerceIn(0, binding.root.height)

            // Largest distance from origin to any corner — guarantees the
            // circle reaches every pixel even when the tap is off-center.
            val maxDx = maxOf(cx.toFloat(), (binding.root.width - cx).toFloat())
            val maxDy = maxOf(cy.toFloat(), (binding.root.height - cy).toFloat())
            val finalRadius = kotlin.math.hypot(maxDx, maxDy).toFloat()

            val revealDuration = 720L

            // New theme blooms *forward* from the tap point: starts slightly
            // oversized, settles to natural size as the circle reaches its edges.
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

            // Old theme drops into soft focus as it recedes. API-gated because
            // RenderEffect requires Android 12+; pre-S falls back to plain fade.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val maxBlurPx = 18f * dp
                ValueAnimator.ofFloat(0f, maxBlurPx).apply {
                    duration = revealDuration
                    interpolator = emphasized
                    addUpdateListener { anim ->
                        val r = anim.animatedValue as Float
                        snapshotView.setRenderEffect(
                            if (r > 0.5f)
                                android.graphics.RenderEffect.createBlurEffect(
                                    r, r, android.graphics.Shader.TileMode.CLAMP
                                )
                            else null
                        )
                    }
                    start()
                }
            }

            titleCardContainer.animate()
                .alpha(0f)
                .scaleX(1.06f).scaleY(1.06f)
                .translationY(-6 * dp)
                .setDuration(280)
                .setInterpolator(emphasizedExit)
                .start()

            snapshotView.animate()
                .alpha(0f)
                .setDuration(revealDuration)
                .setInterpolator(emphasized)
                .start()

            scrimView.animate()
                .alpha(0f)
                .setDuration(revealDuration)
                .setInterpolator(emphasized)
                .start()

            // System chrome tween: keep status + nav bars synced with the
            // reveal so the screen never has mixed-theme edges.
            val newChromeColor = ContextCompat.getColor(this, R.color.background_primary)
            if (oldStatusBarColor != null && oldStatusBarColor != newChromeColor) {
                ValueAnimator.ofArgb(oldStatusBarColor, newChromeColor).apply {
                    duration = revealDuration
                    interpolator = emphasized
                    addUpdateListener { window.statusBarColor = it.animatedValue as Int }
                    start()
                }
            } else {
                window.statusBarColor = newChromeColor
            }
            if (oldNavigationBarColor != null && oldNavigationBarColor != newChromeColor) {
                ValueAnimator.ofArgb(oldNavigationBarColor, newChromeColor).apply {
                    duration = revealDuration
                    interpolator = emphasized
                    addUpdateListener { window.navigationBarColor = it.animatedValue as Int }
                    start()
                }
            }

            // Flip status-bar icon polarity slightly past the midpoint, when the
            // reveal has covered enough of the top edge that the new polarity
            // is correct against what the user sees.
            binding.root.postDelayed({
                if (isActivityTornDown()) return@postDelayed
                WindowInsetsControllerCompat(window, window.decorView)
                    .isAppearanceLightStatusBars = !isDarkNow
            }, (revealDuration * 0.55f).toLong())

            val reveal = android.view.ViewAnimationUtils.createCircularReveal(
                binding.root, cx, cy, 0f, finalRadius
            )
            reveal.duration = revealDuration
            reveal.interpolator = emphasized
            reveal.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cleanupThemeTransitionOverlay(
                        snapshotView = snapshotView,
                        scrimView = scrimView,
                        titleCardContainer = titleCardContainer,
                        newWindowColor = newChromeColor
                    )
                    setThemeControlsEnabled(true)
                }
            })
            reveal.start()
        }
        pendingRevealRunnable = revealRunnable
        binding.root.postDelayed(revealRunnable, 480)
    }

    private fun setThemeControlsEnabled(enabled: Boolean) {
        listOf(binding.btnPhoneLight, binding.btnPhoneDark, binding.btnMatchSystem).forEach {
            it.isEnabled = enabled
            it.isClickable = enabled
        }
    }

    private fun clearPendingThemeTransitionState(recycleBitmap: Boolean) {
        if (recycleBitmap) {
            ThemeManager.transitionBitmap?.takeIf { !it.isRecycled }?.recycle()
        }
        ThemeManager.transitionBitmap = null
        ThemeManager.transitionCenter = null
        ThemeManager.transitionThemeLabel = null
        ThemeManager.transitionOldStatusBarColor = null
        ThemeManager.transitionOldNavigationBarColor = null
        ThemeManager.transitionOldIsDark = null
    }

    private fun cleanupThemeTransitionOverlay(
        snapshotView: ImageView,
        scrimView: View,
        titleCardContainer: View,
        newWindowColor: Int
    ) {
        (snapshotView.parent as? ViewGroup)?.removeView(snapshotView)
        (scrimView.parent as? ViewGroup)?.removeView(scrimView)
        (titleCardContainer.parent as? ViewGroup)?.removeView(titleCardContainer)
        snapshotView.setLayerType(View.LAYER_TYPE_NONE, null)
        scrimView.setLayerType(View.LAYER_TYPE_NONE, null)
        titleCardContainer.setLayerType(View.LAYER_TYPE_NONE, null)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            snapshotView.setRenderEffect(null)
        }
        snapshotView.setImageDrawable(null)
        window.setBackgroundDrawable(ColorDrawable(newWindowColor))
        transitionBitmapToRecycle?.takeIf { !it.isRecycled }?.recycle()
        transitionBitmapToRecycle = null
    }

    private fun isActivityTornDown(): Boolean = isTornDown || isFinishing || isDestroyed

    override fun onDestroy() {
        isTornDown = true
        pendingRevealRunnable?.let { binding.root.removeCallbacks(it) }
        pendingRevealRunnable = null
        transitionBitmapToRecycle?.takeIf { !it.isRecycled }?.recycle()
        transitionBitmapToRecycle = null
        if (themeRestartInProgress) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        super.onDestroy()
    }
}
