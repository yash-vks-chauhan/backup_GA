package com.gridee.parking.ui.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.core.content.res.ResourcesCompat
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import com.gridee.parking.R
import com.gridee.parking.ui.motion.MotionTokens

class CustomBottomNavigation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface OnTabSelectedListener {
        fun onTabSelected(tabId: Int)
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_BOOKINGS = 1
        const val TAB_WALLET = 2
        const val TAB_PROFILE = 3

        // Light the destination tab up (colour + lift) once the morph pill's centre has
        // travelled this fraction of the way, so the pill "carries" the colour in rather than
        // sliding under a still-gray icon. Lower = lights up earlier in the slide.
        private const val ARRIVAL_POP_FRACTION = 0.55f

        // Tabs in layout order — used to re-assert the inactive look on every non-selected tab.
        private val TAB_ORDER = intArrayOf(TAB_HOME, TAB_BOOKINGS, TAB_WALLET, TAB_PROFILE)

        // Icon/label colour crossfade between inactive (gray) and active (gold). A real fade,
        // not a hard cut, so the selected tab "lights up" as the pill arrives under it.
        private const val TINT_DURATION_MS = 220L

        // The selected icon's "lift" as the pill docks — a small, quick settle (in iOS terms).
        private const val ICON_LIFT_DURATION = 0.30f
        private const val ICON_LIFT_FROM = 0.92f
        private const val LABEL_LIFT_FROM = 0.96f
    }

    private var onTabSelectedListener: OnTabSelectedListener? = null
    private var currentTab = TAB_HOME
    // The first setActiveTab fires from init{}, before the user has seen anything.
    // Animating that activation creates a startup pop on every cold launch — skip it.
    private var hasRenderedInitialState = false

    // Views
    private lateinit var tabHome: FrameLayout
    private lateinit var tabBookings: FrameLayout
    private lateinit var tabWallet: FrameLayout
    private lateinit var tabProfile: FrameLayout

    private lateinit var ivHome: ImageView
    private lateinit var ivBookings: ImageView
    private lateinit var ivWallet: ImageView
    private lateinit var ivProfile: ImageView

    private lateinit var tvHome: TextView
    private lateinit var tvBookings: TextView
    private lateinit var tvWallet: TextView
    private lateinit var tvProfile: TextView

    // Active background views (kept for geometry reference only; visual is the morph pill)
    private lateinit var homeActiveBg: View
    private lateinit var bookingsActiveBg: View
    private lateinit var walletActiveBg: View
    private lateinit var profileActiveBg: View

    // Liquid morphing indicator
    private lateinit var morphPill: View
    private var pillLeftSpring: SpringAnimation? = null
    private var pillRightSpring: SpringAnimation? = null
    private var pillAlphaSpring: SpringAnimation? = null
    private var pillLeftPx: Float = 0f
    private var pillRightPx: Float = 0f
    private var pillTopPx: Float = 0f
    private var pillBottomPx: Float = 0f

    // The destination icon's pop is deferred until the morph pill arrives, so a tap reads as
    // one connected gesture (indicator slides over, icon lifts as it lands) instead of two
    // simultaneous events. Armed in setActiveTab; fired from the pill spring's update listener.
    private var pendingPopTab: Int = -1
    private var popTriggered: Boolean = false
    private var pillTravelStartCenter: Float = 0f
    private var pillTravelTargetCenter: Float = 0f

    // Animation properties
    private var currentIndicatorAnimation: SpringAnimation? = null
    private var isHidden = false
    private var isMinimized = false
    private var expandedCapsuleHeight: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var minimizeAnimator: ValueAnimator? = null
    private var pendingActiveTab: Int = -1
    
    // Haptic feedback
    private val vibrator: Vibrator? by lazy { context.getSystemService<Vibrator>() }
    
    // Cached fonts to prevent reloading and crashes
    private var typefaceBold: Typeface? = null
    private var typefaceMedium: Typeface? = null

    // Icon/label colour crossfade between active (gold) and inactive (gray). One animator per
    // tab so an in-flight fade can be interrupted and re-aimed cleanly.
    private val argbEvaluator = android.animation.ArgbEvaluator()
    private val tintAnimators = HashMap<Int, ValueAnimator>()

    // Animation durations and properties
    private val scaleAnimationDuration = 150L
    private val bounceAnimationDuration = 200L

    // Scroll-pressure response — capsule lifts as content scrolls behind it. Cached
    // to avoid re-resolving findViewById and applying identical elevation each frame.
    private var capsuleViewRef: FrameLayout? = null
    private var currentPressure: Float = 0f
    private val baseElevationDp = 6f
    private val maxElevationDp = 12f

    init {
        LayoutInflater.from(context).inflate(R.layout.custom_bottom_navigation, this, true)

        // NOTE: do NOT setLayerType(LAYER_TYPE_HARDWARE) here. A hardware layer renders the
        // view into a fixed-size texture matching its own bounds — any elevation shadow
        // extending above the bar gets clipped by the layer. Hardware acceleration is
        // already on by default for views on API 14+; we don't need the explicit caching.

        // Allow the inner container's elevation shadow to extend upward beyond this
        // LinearLayout's bounds (the default clipChildren=true would clip it at the top edge).
        clipChildren = false
        clipToPadding = false

        // Horizontal LinearLayout ignores layout_gravity="center_horizontal" on its child
        // — children stack left-to-right by default. Setting the parent's gravity is what
        // actually centers the wrap_content capsule on screen.
        gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.BOTTOM

        // Handle window insets for edge-to-edge support
        setupWindowInsets()
        
        initViews()
        loadFonts()
        setupClickListeners()
        setActiveTab(TAB_HOME)
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Outer LinearLayout: no padding. The visible capsule has its own bottom
            // margin that absorbs the gesture-nav inset, so this view stays edge-to-edge.
            view.setPadding(0, 0, 0, 0)

            // Apply the gesture-nav inset as bottom margin on the floating capsule so it
            // hovers above the system gesture area with a constant ~16dp gap. iOS 26
            // insets its tab bar ~21pt from the bottom edge; 16dp + system inset is the
            // Android-density equivalent that still clears 3-button nav.
            val capsule = findViewById<FrameLayout>(R.id.bottom_navigation_container)
            capsule?.let { c ->
                val lp = c.layoutParams as? ViewGroup.MarginLayoutParams ?: return@let
                lp.bottomMargin = navigationBars.bottom + capsuleBottomGapPx()
                c.layoutParams = lp
            }

            // Outer wraps the capsule + its bottom margin. Letting it wrap means when the
            // capsule minimizes on scroll, the fragment above naturally gains screen space.
            val layoutParams = view.layoutParams
            if (layoutParams != null) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                view.layoutParams = layoutParams
            }

            insets
        }
    }

    private fun capsuleBottomGapPx(): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics
    ).toInt()

    private fun initViews() {
        tabHome = findViewById(R.id.tab_home)
        tabBookings = findViewById(R.id.tab_bookings)
        tabWallet = findViewById(R.id.tab_wallet)
        tabProfile = findViewById(R.id.tab_profile)

        ivHome = findViewById(R.id.iv_home)
        ivBookings = findViewById(R.id.iv_bookings)
        ivWallet = findViewById(R.id.iv_wallet)
        ivProfile = findViewById(R.id.iv_profile)

        tvHome = findViewById(R.id.tv_home)
        tvBookings = findViewById(R.id.tv_bookings)
        tvWallet = findViewById(R.id.tv_wallet)
        tvProfile = findViewById(R.id.tv_profile)
        
        // Active background views (used purely as geometry anchors for the morph pill)
        homeActiveBg = findViewById(R.id.home_active_bg)
        bookingsActiveBg = findViewById(R.id.bookings_active_bg)
        walletActiveBg = findViewById(R.id.wallet_active_bg)
        profileActiveBg = findViewById(R.id.profile_active_bg)

        morphPill = findViewById(R.id.morph_pill)

        // Position the morph pill once layout is ready (handles first frame). Subsequent
        // bounds changes on the active anchor (rotation, multi-window, font scale) are
        // tracked via OnLayoutChangeListener so the pill stays anchored.
        val firstLayout = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (width <= 0) return
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                positionMorphPillAt(currentTab, animate = false)
                morphPill.alpha = 1f
                if (pendingActiveTab != -1) {
                    val target = pendingActiveTab
                    pendingActiveTab = -1
                    positionMorphPillAt(target, animate = true)
                }
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(firstLayout)

        // Re-snap the pill when the active anchor's bounds change (rotation, resize).
        val anchorBoundsListener = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (l == ol && t == ot && r == or_ && b == ob) return@OnLayoutChangeListener
            if (width > 0 && morphPill.alpha > 0f) {
                positionMorphPillAt(currentTab, animate = false)
            }
        }
        homeActiveBg.addOnLayoutChangeListener(anchorBoundsListener)
        bookingsActiveBg.addOnLayoutChangeListener(anchorBoundsListener)
        walletActiveBg.addOnLayoutChangeListener(anchorBoundsListener)
        profileActiveBg.addOnLayoutChangeListener(anchorBoundsListener)
    }

    private fun getActiveBgForTab(tabId: Int): View = when (tabId) {
        TAB_HOME -> homeActiveBg
        TAB_BOOKINGS -> bookingsActiveBg
        TAB_WALLET -> walletActiveBg
        TAB_PROFILE -> profileActiveBg
        else -> homeActiveBg
    }

    private fun iconViewFor(tabId: Int): ImageView = when (tabId) {
        TAB_HOME -> ivHome
        TAB_BOOKINGS -> ivBookings
        TAB_WALLET -> ivWallet
        TAB_PROFILE -> ivProfile
        else -> ivHome
    }

    private fun labelViewFor(tabId: Int): TextView = when (tabId) {
        TAB_HOME -> tvHome
        TAB_BOOKINGS -> tvBookings
        TAB_WALLET -> tvWallet
        TAB_PROFILE -> tvProfile
        else -> tvHome
    }

    /** Light up the destination tab (fill + colour crossfade + lift). Called as the pill docks. */
    private fun popTab(tabId: Int) {
        applyActiveAppearance(tabId, animate = true)
    }

    /**
     * Compute the bounds of [anchor] in the morph pill's parent (the floating capsule
     * container) coordinate space. The pill is a child of that container, so its .x/.y
     * must be expressed there — not in this outer LinearLayout's coords. Walks up the
     * parent chain accumulating offsets until it reaches the pill's parent.
     */
    private fun computeBoundsRelativeToPillParent(anchor: View): FloatArray {
        val pillParent = morphPill.parent as? View ?: return floatArrayOf(0f, 0f, 0f, 0f)
        var x = 0f
        var y = 0f
        var v: View = anchor
        while (v !== pillParent) {
            x += v.x
            y += v.y
            v = v.parent as? View ?: break
        }
        return floatArrayOf(x, y, x + anchor.width, y + anchor.height)
    }

    private fun positionMorphPillAt(tabId: Int, animate: Boolean) {
        val anchor = getActiveBgForTab(tabId)
        if (anchor.width == 0) {
            pendingActiveTab = tabId
            return
        }
        val bounds = computeBoundsRelativeToPillParent(anchor)
        if (!animate) {
            applyPillBounds(bounds[0], bounds[1], bounds[2], bounds[3])
            return
        }
        // Hold top/bottom rigid; animate left/right independently for the "liquid stretch".
        pillTopPx = bounds[1]
        pillBottomPx = bounds[3]
        morphPill.y = pillTopPx
        morphPill.layoutParams = morphPill.layoutParams.also { it.height = (pillBottomPx - pillTopPx).toInt() }
        animatePillEdges(bounds[0], bounds[2])
    }

    private fun applyPillBounds(left: Float, top: Float, right: Float, bottom: Float) {
        pillLeftPx = left
        pillRightPx = right
        pillTopPx = top
        pillBottomPx = bottom
        morphPill.x = left
        morphPill.y = top
        val lp = morphPill.layoutParams
        lp.width = (right - left).toInt().coerceAtLeast(1)
        lp.height = (bottom - top).toInt().coerceAtLeast(1)
        morphPill.layoutParams = lp
    }

    private fun animatePillEdges(targetLeft: Float, targetRight: Float) {
        ensureEdgeSprings()

        // Record this leg of travel so the deferred icon pop can fire as the pill lands.
        pillTravelStartCenter = (pillLeftPx + pillRightPx) / 2f
        pillTravelTargetCenter = (targetLeft + targetRight) / 2f

        val movingRight = targetLeft > pillLeftPx
        // Leading edge (in direction of motion) settles a touch quicker than the trailing
        // edge — a faint stretch that reads as a clean iOS slide, not a rubber-band. Both
        // tuned via duration/bounce in MotionTokens (SwiftUI's spring language).
        val leadingStiffness = MotionTokens.stiffnessForDuration(MotionTokens.PILL_LEAD_DURATION)
        val trailingStiffness = MotionTokens.stiffnessForDuration(MotionTokens.PILL_TRAIL_DURATION)
        val damping = MotionTokens.dampingForBounce(MotionTokens.PILL_BOUNCE)
        val leftStiffness = if (movingRight) trailingStiffness else leadingStiffness
        val rightStiffness = if (movingRight) leadingStiffness else trailingStiffness

        // Reuse the existing springs rather than recreating them: animateToFinalPosition
        // continues from the pill's current position AND velocity, so a tap mid-flight bends
        // the motion toward the new tab instead of stopping dead and restarting. That momentum
        // carry-over is the difference between "alive" and "mechanical". Stiffness/damping are
        // read per-frame, so redirecting mid-transit is glitch-free.
        pillLeftSpring!!.spring.apply {
            stiffness = leftStiffness
            dampingRatio = damping
        }
        pillRightSpring!!.spring.apply {
            stiffness = rightStiffness
            dampingRatio = damping
        }
        pillLeftSpring!!.animateToFinalPosition(targetLeft)
        pillRightSpring!!.animateToFinalPosition(targetRight)
    }

    private fun ensureEdgeSprings() {
        if (pillLeftSpring == null) {
            pillLeftSpring = SpringAnimation(this, PillLeftProperty).apply {
                spring = SpringForce().apply { dampingRatio = MotionTokens.dampingForBounce(MotionTokens.PILL_BOUNCE) }
                // The left edge drives the arrival check — update listeners must be attached
                // before the spring is ever started, so do it once here at creation.
                addUpdateListener { _, _, _ -> maybeFireArrivalPop() }
            }
        }
        if (pillRightSpring == null) {
            pillRightSpring = SpringAnimation(this, PillRightProperty).apply {
                spring = SpringForce().apply { dampingRatio = MotionTokens.dampingForBounce(MotionTokens.PILL_BOUNCE) }
            }
        }
    }

    /**
     * Fire the deferred destination icon pop once the morph pill has travelled most of the way
     * to the new tab, so the icon lifts as the pill arrives under it. Fires at most once per
     * leg (guarded by [popTriggered]); a no-op when no pop is armed.
     */
    private fun maybeFireArrivalPop() {
        if (pendingPopTab == -1 || popTriggered) return
        val total = pillTravelTargetCenter - pillTravelStartCenter
        val progress = if (kotlin.math.abs(total) < 1f) {
            1f
        } else {
            ((pillLeftPx + pillRightPx) / 2f - pillTravelStartCenter) / total
        }
        if (progress >= ARRIVAL_POP_FRACTION) {
            val tab = pendingPopTab
            popTriggered = true
            pendingPopTab = -1
            popTab(tab)
        }
    }

    /**
     * Spring the morph pill back to the current active tab. Use this when an interactive
     * swipe is cancelled and the pill is mid-stretch between tabs.
     */
    fun springPillToCurrent() {
        if (width == 0 || !::morphPill.isInitialized) return
        positionMorphPillAt(currentTab, animate = true)
    }

    /**
     * Drive the morph pill interactively during an edge-swipe. `progress` in [0..1] morphs
     * the pill toward [tabId]. At 0 the pill sits at the current active tab; at 1 it has
     * fully settled at [tabId].
     */
    fun previewActiveTab(tabId: Int, progress: Float) {
        if (width == 0) return
        pillLeftSpring?.cancel()
        pillRightSpring?.cancel()
        val from = computeBoundsRelativeToPillParent(getActiveBgForTab(currentTab))
        val to = computeBoundsRelativeToPillParent(getActiveBgForTab(tabId))
        val p = progress.coerceIn(0f, 1f)
        val left = from[0] + (to[0] - from[0]) * p
        val right = from[2] + (to[2] - from[2]) * p
        val top = from[1] + (to[1] - from[1]) * p
        val bottom = from[3] + (to[3] - from[3]) * p
        // While dragging, stretch the pill very slightly at mid-travel — just enough to feel
        // elastic. Kept small (was 0.18) to match the clean iOS slide, not a rubber-band.
        val mid = (left + right) / 2f
        val halfWidth = (right - left) / 2f
        val stretchFactor = 1f + 0.10f * (1f - kotlin.math.abs(p - 0.5f) * 2f)
        applyPillBounds(mid - halfWidth * stretchFactor, top, mid + halfWidth * stretchFactor, bottom)
    }

    private object PillLeftProperty : FloatPropertyCompat<CustomBottomNavigation>("pillLeft") {
        override fun getValue(o: CustomBottomNavigation): Float = o.pillLeftPx
        override fun setValue(o: CustomBottomNavigation, value: Float) {
            o.pillLeftPx = value
            o.applyPillFromState()
        }
    }

    private object PillRightProperty : FloatPropertyCompat<CustomBottomNavigation>("pillRight") {
        override fun getValue(o: CustomBottomNavigation): Float = o.pillRightPx
        override fun setValue(o: CustomBottomNavigation, value: Float) {
            o.pillRightPx = value
            o.applyPillFromState()
        }
    }

    private fun applyPillFromState() {
        morphPill.x = pillLeftPx
        morphPill.y = pillTopPx
        val lp = morphPill.layoutParams
        lp.width = (pillRightPx - pillLeftPx).toInt().coerceAtLeast(1)
        lp.height = (pillBottomPx - pillTopPx).toInt().coerceAtLeast(1)
        morphPill.layoutParams = lp
    }

    private fun loadFonts() {
        try {
            typefaceBold = ResourcesCompat.getFont(context, R.font.inter_bold)
            typefaceMedium = ResourcesCompat.getFont(context, R.font.inter_medium)
        } catch (e: Exception) {
            Log.e("CustomBottomNav", "Error loading fonts", e)
            // Fallback to system fonts if resources fail
            typefaceBold = Typeface.DEFAULT_BOLD
            typefaceMedium = Typeface.DEFAULT
        }
    }

    private fun setupClickListeners() {
        tabHome.setOnClickListener { selectTab(TAB_HOME) }
        tabBookings.setOnClickListener { selectTab(TAB_BOOKINGS) }
        tabWallet.setOnClickListener { selectTab(TAB_WALLET) }
        tabProfile.setOnClickListener { selectTab(TAB_PROFILE) }
    }

    private fun performHapticFeedback() {
        // Try modern haptic feedback first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(10)
        }
    }

    private fun selectTab(tabId: Int) {
        val isReselect = currentTab == tabId
        // Add haptic feedback for tab selection
        performHapticFeedback()

        if (!isReselect) {
            setActiveTab(tabId)
        }
        // Fire the listener in both cases — reselect path lets the host scroll-to-top.
        // The listener's existing `tabId == currentTabId` branch handles the reselect.
        onTabSelectedListener?.onTabSelected(tabId)
    }

    fun setActiveTab(tabId: Int) {
        val previous = currentTab
        currentTab = tabId

        // TalkBack reads "selected" off this flag — no separate AccessibilityDelegate needed.
        tabHome.isSelected = tabId == TAB_HOME
        tabBookings.isSelected = tabId == TAB_BOOKINGS
        tabWallet.isSelected = tabId == TAB_WALLET
        tabProfile.isSelected = tabId == TAB_PROFILE

        // The pill — and the appearance crossfades — animate only when actually moving to a
        // different, already-laid-out tab.
        val pillWillAnimate = ::morphPill.isInitialized &&
            previous != tabId && width > 0 && getActiveBgForTab(tabId).width > 0
        val animateAppearance = hasRenderedInitialState && pillWillAnimate

        // Fade every non-selected tab back to its inactive look. Only the previously-selected
        // tab actually changes colour, so it's the only one that animates; the rest are already
        // inactive and settle silently (animateTint no-ops when the colour wouldn't change).
        for (t in TAB_ORDER) {
            if (t == tabId) continue
            applyInactiveAppearance(t, animate = animateAppearance && t == previous)
        }

        // The selected tab "lights up" — fills in, colours to gold and lifts. When the pill
        // glides we DEFER that to its arrival (armed here, fired from the pill's update
        // listener) so icon and indicator read as one gesture. Otherwise apply it now.
        if (animateAppearance) {
            pendingPopTab = tabId
            popTriggered = false
        } else {
            pendingPopTab = -1
            applyActiveAppearance(tabId, animate = false)
        }

        // Drive the liquid morphing pill toward the new active tab.
        if (::morphPill.isInitialized) {
            positionMorphPillAt(tabId, animate = pillWillAnimate)
        }

        hasRenderedInitialState = true
    }

    private fun filledIconFor(tabId: Int): Int = when (tabId) {
        TAB_HOME -> R.drawable.ic_home_filled
        TAB_BOOKINGS -> R.drawable.ic_bookings_filled
        TAB_WALLET -> R.drawable.ic_wallet_filled
        TAB_PROFILE -> R.drawable.ic_profile_nav_filled
        else -> R.drawable.ic_home_filled
    }

    private fun outlineIconFor(tabId: Int): Int = when (tabId) {
        TAB_HOME -> R.drawable.ic_home
        TAB_BOOKINGS -> R.drawable.ic_bookings
        TAB_WALLET -> R.drawable.ic_wallet
        TAB_PROFILE -> R.drawable.ic_profile_nav
        else -> R.drawable.ic_home
    }

    /**
     * Light a tab up as selected: fill the icon, shift it to the brand gold and give it a
     * whisper of lift. With [animate] the colour crossfades and the lift springs (used as the
     * pill docks in); without it the state snaps (initial layout / reselect — no pill glide).
     */
    private fun applyActiveAppearance(tabId: Int, animate: Boolean) {
        val icon = iconViewFor(tabId)
        val label = labelViewFor(tabId)
        // Per-tab background is only a geometry anchor now; keep it invisible.
        getActiveBgForTab(tabId).apply { visibility = View.INVISIBLE; alpha = 0f }
        icon.setImageResource(filledIconFor(tabId))
        label.typeface = typefaceBold ?: Typeface.DEFAULT_BOLD
        label.visibility = View.VISIBLE

        val activeColor = ContextCompat.getColor(context, R.color.nav_active_pill_on)
        if (animate) {
            animateTint(tabId, activeColor)
            animateLift(icon, label)
        } else {
            tintAnimators.remove(tabId)?.cancel()
            icon.imageTintList = ColorStateList.valueOf(activeColor)
            label.setTextColor(activeColor)
            icon.scaleX = 1f; icon.scaleY = 1f
            label.scaleX = 1f; label.scaleY = 1f
        }
    }

    /**
     * Return a tab to its inactive look: outline icon, quiet gray. [animate] crossfades the
     * colour (used for the tab being deselected); otherwise it snaps. The crossfade no-ops when
     * the tab is already inactive, so re-asserting the resting tabs each selection stays cheap.
     */
    private fun applyInactiveAppearance(tabId: Int, animate: Boolean) {
        val icon = iconViewFor(tabId)
        val label = labelViewFor(tabId)
        getActiveBgForTab(tabId).apply { visibility = View.INVISIBLE; alpha = 0f }
        icon.setImageResource(outlineIconFor(tabId))
        label.typeface = typefaceMedium ?: Typeface.DEFAULT
        label.visibility = View.VISIBLE

        val inactiveColor = ContextCompat.getColor(context, R.color.nav_inactive_tint)
        if (animate) {
            animateTint(tabId, inactiveColor)
        } else if (tintAnimators[tabId]?.isRunning != true) {
            // Snap to gray — but never interrupt a fade that's already on its way there.
            icon.imageTintList = ColorStateList.valueOf(inactiveColor)
            label.setTextColor(inactiveColor)
        }
        // Relax any leftover lift back to rest.
        applySpring(icon, SpringAnimation.SCALE_X, 1f)
        applySpring(icon, SpringAnimation.SCALE_Y, 1f)
        applySpring(label, SpringAnimation.SCALE_X, 1f)
        applySpring(label, SpringAnimation.SCALE_Y, 1f)
    }

    /**
     * Crossfade a tab's icon tint AND label colour to [toColor] (ARGB-interpolated), reading the
     * current colour as the start so an interrupted fade continues smoothly. Snaps (no animator)
     * when nothing would change, so it's safe to call on already-settled tabs.
     */
    private fun animateTint(tabId: Int, toColor: Int) {
        val icon = iconViewFor(tabId)
        val label = labelViewFor(tabId)
        val fromIcon = icon.imageTintList?.defaultColor ?: toColor
        val fromLabel = label.currentTextColor
        if (fromIcon == toColor && fromLabel == toColor) {
            tintAnimators.remove(tabId)?.cancel()
            icon.imageTintList = ColorStateList.valueOf(toColor)
            label.setTextColor(toColor)
            return
        }
        tintAnimators[tabId]?.cancel()
        tintAnimators[tabId] = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TINT_DURATION_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { a ->
                val f = a.animatedFraction
                icon.imageTintList = ColorStateList.valueOf(argbEvaluator.evaluate(f, fromIcon, toColor) as Int)
                label.setTextColor(argbEvaluator.evaluate(f, fromLabel, toColor) as Int)
            }
            start()
        }
    }

    /**
     * A whisper of lift on the selected icon + label as the pill docks — a small, quick settle,
     * not a pop. Shares the pill's bounce so the two read as one physical system.
     */
    private fun animateLift(icon: ImageView, label: TextView) {
        val stiffness = MotionTokens.stiffnessForDuration(ICON_LIFT_DURATION)
        val damping = MotionTokens.dampingForBounce(MotionTokens.PILL_BOUNCE)
        icon.scaleX = ICON_LIFT_FROM
        icon.scaleY = ICON_LIFT_FROM
        icon.alpha = 1f
        applySpring(icon, SpringAnimation.SCALE_X, 1f, stiffness, damping)
        applySpring(icon, SpringAnimation.SCALE_Y, 1f, stiffness, damping)
        label.scaleX = LABEL_LIFT_FROM
        label.scaleY = LABEL_LIFT_FROM
        label.alpha = 1f
        applySpring(label, SpringAnimation.SCALE_X, 1f, stiffness, damping)
        applySpring(label, SpringAnimation.SCALE_Y, 1f, stiffness, damping)
    }
    
    private fun applySpring(
        view: View, 
        property: DynamicAnimation.ViewProperty, 
        targetValue: Float,
        stiffness: Float = SpringForce.STIFFNESS_VERY_LOW,
        dampingRatio: Float = SpringForce.DAMPING_RATIO_NO_BOUNCY
    ) {
        val animation = SpringAnimation(view, property)
        animation.spring = SpringForce(targetValue).apply {
            this.stiffness = stiffness
            this.dampingRatio = dampingRatio
        }
        animation.start()
    }

    fun setOnTabSelectedListener(listener: OnTabSelectedListener) {
        this.onTabSelectedListener = listener
    }

    fun getCurrentTab(): Int = currentTab

    /**
     * iOS 26 "minimize on scroll": capsule shrinks vertically, labels fade out, leaving
     * an icon-only compact bar. Reverses with [expandBottomNavigation]. No-op if already
     * minimized or if the bar hasn't laid out yet.
     */
    fun minimizeBottomNavigation() {
        if (isMinimized) return
        val capsule = findViewById<FrameLayout>(R.id.bottom_navigation_container) ?: return
        if (capsule.height <= 0) return
        isMinimized = true
        if (expandedCapsuleHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
            expandedCapsuleHeight = capsule.height
        }
        val targetHeight = dp(48)
        animateCapsuleHeight(capsule, capsule.height, targetHeight)
        animateLabelsAlpha(0f)
    }

    fun expandBottomNavigation() {
        if (!isMinimized) return
        val capsule = findViewById<FrameLayout>(R.id.bottom_navigation_container) ?: return
        isMinimized = false
        val targetHeight = if (expandedCapsuleHeight > 0) expandedCapsuleHeight else dp(60)
        animateCapsuleHeight(capsule, capsule.height, targetHeight)
        animateLabelsAlpha(1f)
    }

    fun isMinimized(): Boolean = isMinimized

    private fun animateCapsuleHeight(capsule: View, fromHeight: Int, toHeight: Int) {
        minimizeAnimator?.cancel()
        minimizeAnimator = ValueAnimator.ofInt(fromHeight, toHeight).apply {
            duration = 220L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { anim ->
                val lp = capsule.layoutParams
                lp.height = anim.animatedValue as Int
                capsule.layoutParams = lp
            }
            start()
        }
    }

    private fun animateLabelsAlpha(target: Float) {
        listOf(tvHome, tvBookings, tvWallet, tvProfile).forEach { tv ->
            tv.animate()
                .alpha(target)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    // Professional scroll behavior with refined animations
    fun hideBottomNavigation() {
        if (!isHidden) {
            isHidden = true
            animate()
                .translationY(height.toFloat())
                .alpha(0.0f)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator(2.0f))
                .start()
        }
    }

    fun showBottomNavigation() {
        if (isHidden) {
            isHidden = false
            animate()
                .translationY(0f)
                .alpha(1.0f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }
    }

    fun isNavigationHidden(): Boolean = isHidden

    /**
     * Drive the capsule's "pressure" against scrolling content. [progress] in [0..1]:
     * 0 = at rest (no content underneath), 1 = content fully under the bar. Lifts the
     * capsule's elevation so the drop shadow widens, reading as the bar floating
     * higher when there's something behind it. Cheap — only mutates elevation.
     */
    fun setScrollPressure(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        if (kotlin.math.abs(p - currentPressure) < 0.005f) return
        currentPressure = p
        val capsule = capsuleViewRef
            ?: findViewById<FrameLayout>(R.id.bottom_navigation_container)?.also {
                capsuleViewRef = it
            }
            ?: return
        val targetDp = baseElevationDp + (maxElevationDp - baseElevationDp) * p
        capsule.elevation = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, targetDp, resources.displayMetrics
        )
    }
}
