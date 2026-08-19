package com.gridee.parking.ui.main

import android.os.Bundle
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.databinding.ActivityMainContainerBinding
import com.gridee.parking.ui.base.BaseActivityWithBottomNav
import com.gridee.parking.ui.base.BottomOverlayHost
import com.gridee.parking.ui.bottomsheet.PartnerReferralBottomSheet
import com.gridee.parking.ui.bottomsheet.WelcomeGiftBottomSheet
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.fragments.BookingsFragmentNew
import com.gridee.parking.ui.fragments.HomeFragment
import com.gridee.parking.ui.fragments.ProfileFragment
import com.gridee.parking.ui.fragments.WalletFragmentNew
import com.gridee.parking.ui.motion.FragmentTransitionController
import com.gridee.parking.ui.motion.MotionTokens
import com.gridee.parking.ui.motion.TabSwipeGestureDetector
import com.gridee.parking.utils.NotificationPermissionHelper
import com.gridee.parking.utils.ThemeManager
import android.util.TypedValue
import android.widget.Toast

class MainContainerActivity :
    BaseActivityWithBottomNav<ActivityMainContainerBinding>(),
    BottomOverlayHost {

    companion object {
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_SHOW_PENDING = "extra_show_pending"
        const val EXTRA_HIGHLIGHT_BOOKING_ID = "extra_highlight_booking_id"
        const val EXTRA_OPEN_BOOKING_ID = "extra_open_booking_id"
        const val EXTRA_SHOW_WALLET_TRANSACTION = "extra_show_wallet_transaction"
        const val EXTRA_WALLET_TRANSACTION_TITLE = "extra_wallet_transaction_title"
        const val EXTRA_WALLET_TRANSACTION_AMOUNT = "extra_wallet_transaction_amount"
        const val EXTRA_WALLET_TRANSACTION_IS_CREDIT = "extra_wallet_transaction_is_credit"
        const val EXTRA_WALLET_TRANSACTION_ROUTE_TO_WALLET = "extra_wallet_transaction_route_to_wallet"
        const val EXTRA_SHOW_SIGNUP_GIFT = "extra_show_signup_gift"
        const val EXTRA_SHOW_LOGIN_WELCOME = "extra_show_login_welcome"
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001

        /** Resting gap between the floating dock controls and the tab bar, per the layout. */
        private const val DOCK_REST_GAP_DP = 22f

        /** Breathing room left between a transient banner and the lifted dock above it. */
        private const val DOCK_OVERLAY_GAP_DP = 10f
    }

    private var currentFragment: Fragment? = null
    private var currentTabId = CustomBottomNavigation.TAB_HOME
    private var statusBarInsetTop = 0
    private var renderedThemeMode: String? = null
    private var renderedDarkMode: Boolean? = null
    private var activityResumedForAds = false

    private val tabAnimDurationMs: Long = 360L
    private val tabAnimInterpolator by lazy {
        androidx.core.view.animation.PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f)
    }
    private var statusBarColorAnimator: android.animation.ValueAnimator? = null

    private val transitionController by lazy { FragmentTransitionController(binding.fragmentContainer) }

    // Interactive swipe state
    private var swipeIncomingFragment: Fragment? = null
    private var swipeForward = false
    private var swipeInProgress = false
    // Bumped each time a new swipe begins. Old animation callbacks check this before
    // touching shared state, so an interrupting swipe doesn't have its state wiped by
    // the previous swipe's settle callback.
    private var swipeGeneration = 0
    // Any view left in a transformed state by a cancelled/superseded transition. Cleared
    // by a new swipe so we don't have a stale fragment lingering at -0.3x parallax.
    private var staleOutgoingView: android.view.View? = null

    // Fragment instances (create once, reuse for better performance)
    private val homeFragment by lazy { HomeFragment() }
    private val bookingsFragment by lazy { BookingsFragmentNew() }
    private val walletFragment by lazy { WalletFragmentNew() }
    private val profileFragment by lazy { ProfileFragment() }

    override fun getViewBinding(): ActivityMainContainerBinding {
        return ActivityMainContainerBinding.inflate(layoutInflater)
    }

    override fun getCurrentTab(): Int {
        return currentTabId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Base bailed out (no auth session) and already redirected to login + finished.
        // _binding was never inflated, so stop before any binding access crashes.
        if (!isViewReady) return
        rememberRenderedTheme()

                // Handle system window insets for proper edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarInsetTop = systemBarsInsets.top

            // Re-apply the inset to every added fragment so pre-warmed ones get the
            // correct top padding even if they were added before the first insets dispatch.
            supportFragmentManager.fragments.forEach { applyTopInsetToFragmentView(it) }

            insets
        }

        // Setup bottom navigation manually using binding
        setupBottomNavigationManually(binding.bottomNavigation)
        setupSwipeGesture()
        setupPartnerReferralButton()
        
        val requestedInitialTab = if (savedInstanceState == null) {
            intent?.getIntExtra(EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_HOME)
                ?: CustomBottomNavigation.TAB_HOME
        } else {
            savedInstanceState.getInt("current_tab", CustomBottomNavigation.TAB_HOME)
        }
        val initialTab = resolveAllowedTab(requestedInitialTab, showMessage = false)

        if (savedInstanceState == null) {
            bottomNavigation.setActiveTab(initialTab)
            switchToFragment(getFragmentForTab(initialTab), initialTab)
        } else {
            currentTabId = initialTab
            bottomNavigation.setActiveTab(currentTabId)
            currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            updatePartnerReferralForTab(currentTabId)
        }

        handleNavigationIntent(intent, currentTabId)
        handleWalletGlobalIntents(intent)

        // Defer non-critical startup work until after first frame to avoid a blank/grey handoff.
        binding.root.post {
            enableHighRefreshRateIfSupported()
            requestNotificationPermissionIfNeeded()
            showLoginWelcomeIfNeeded()
            showSignupGiftIfNeeded()
        }

        // Pre-warm the inactive tabs after the home screen has settled. Each fragment's view
        // inflates on its own frame so a single tab switch later doesn't pay the inflation cost
        // mid-animation. setMaxLifecycle keeps them at STARTED so onResume work (network,
        // timers) only fires when the user actually visits the tab.
        binding.root.postDelayed({ prewarmInactiveFragment(bookingsFragment) }, 450)
        binding.root.postDelayed({ prewarmInactiveFragment(walletFragment) }, 750)
        binding.root.postDelayed({ prewarmInactiveFragment(profileFragment) }, 1050)
    }

    override fun onResume() {
        super.onResume()
        if (!isViewReady) return // redirected to login; nothing was set up
        activityResumedForAds = true
        updateHomeNativeAdPresentation()
        refreshAfterDeferredThemeChangeIfNeeded()
    }

    override fun onPause() {
        activityResumedForAds = false
        setHomeNativeAdVisible(false)
        super.onPause()
    }

    private fun rememberRenderedTheme() {
        renderedThemeMode = ThemeManager.getSavedThemeMode(this)
        renderedDarkMode = ThemeManager.isDarkMode(this)
    }

    private fun refreshAfterDeferredThemeChangeIfNeeded() {
        val currentMode = ThemeManager.getSavedThemeMode(this)
        val currentDarkMode = ThemeManager.isDarkMode(this)
        if (renderedThemeMode == currentMode && renderedDarkMode == currentDarkMode) return

        rememberRenderedTheme()
        binding.root.post {
            if (isFinishing || isDestroyed) return@post
            recreate()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun prewarmInactiveFragment(fragment: Fragment) {
        if (isFinishing || isDestroyed) return
        if (fragment === currentFragment || fragment.isAdded) return

        // hide() so FragmentManager knows the fragment is hidden — this is what fires
        // onHiddenChanged on the fragment (HomeFragment / BookingsFragmentNew use it to
        // start/stop auto-refresh timers; skipping it caused stale UI updates and crashes).
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment)
            .hide(fragment)
            .setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.STARTED)
            .commitNowAllowingStateLoss()

        applyTopInsetToFragmentView(fragment)
    }

    private fun showLoginWelcomeIfNeeded() {
        val showWelcome = intent?.getBooleanExtra(EXTRA_SHOW_LOGIN_WELCOME, false) ?: false
        if (!showWelcome) return

        val userName = intent?.getStringExtra("USER_NAME").orEmpty().trim()
        val title = if (userName.isNotEmpty()) "Welcome back, $userName!" else getString(R.string.welcome_back)

        // Clear the extra so it doesn't trigger again on rotation
        intent?.removeExtra(EXTRA_SHOW_LOGIN_WELCOME)

        val parentView = findViewById<android.view.ViewGroup>(R.id.fragment_container)
            ?: window.decorView as? android.view.ViewGroup
            ?: binding.root

        binding.root.postDelayed({
            com.gridee.parking.utils.NotificationHelper.showInfoNoIcon(
                parent = parentView,
                title = title,
                message = "",
                duration = 3000L
            )
        }, 350)
    }

    private fun showSignupGiftIfNeeded() {
        val showGift = intent?.getBooleanExtra(EXTRA_SHOW_SIGNUP_GIFT, false) ?: false
        if (!showGift) return

        // Clear the extra so it doesn't trigger again on rotation
        intent?.removeExtra(EXTRA_SHOW_SIGNUP_GIFT)

        // Small delay to let the home screen settle before showing the gift
        binding.root.postDelayed({
            // Display the actual welcome bonus from remote config so the sheet
            // never drifts from what's credited to the wallet.
            val bonusAmount = com.gridee.parking.config.RemoteConfigManager
                .currentConfig.financial.welcomeBonusAmount.toInt()
            val giftSheet = WelcomeGiftBottomSheet.newInstance(coinAmount = bonusAmount)
            giftSheet.show(supportFragmentManager, WelcomeGiftBottomSheet.TAG)
        }, 600)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (!NotificationPermissionHelper.shouldRequest(this)) return
        NotificationPermissionHelper.markRequested(this)
        val permission = Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            NOTIFICATION_PERMISSION_REQUEST
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_tab", currentTabId)
    }

    override fun setupUI() {
        // Setup any initial UI configuration
    }

    override fun onTabSelected(tabId: Int) {
        if (tabId == currentTabId) {
            // Same tab selected - scroll to top if fragment supports it
            scrollCurrentFragmentToTop()
            return
        }
        if (!isTabEnabled(tabId)) {
            bottomNavigation.setActiveTab(currentTabId)
            showFeatureDisabled(tabId)
            return
        }

        val targetFragment = getFragmentForTab(tabId)
        switchToFragment(targetFragment, tabId)
    }

    private fun switchToFragment(fragment: Fragment, tabId: Int) {
        // Cancel any in-flight spring so a rapid second tap picks up from current position.
        transitionController.cancelAll()

        val isInitialAttach = currentFragment == null
        val outgoingFragment = currentFragment
        val forward = tabId > currentTabId

        val transaction = supportFragmentManager.beginTransaction()
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.fragment_container, fragment)
        }
        transaction.setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.RESUMED)
        outgoingFragment?.let {
            transaction.hide(it)
            transaction.setMaxLifecycle(it, androidx.lifecycle.Lifecycle.State.STARTED)
        }
        transaction.commitNowAllowingStateLoss()

        applyTopInsetToFragmentView(fragment)
        // FragmentManager's hide() just set the outgoing view to GONE; override so the
        // parallax spring animation can render it during the transition. The end callback
        // sets it back to GONE.
        outgoingFragment?.view?.visibility = android.view.View.VISIBLE
        fragment.view?.visibility = android.view.View.VISIBLE

        currentFragment = fragment
        currentTabId = tabId
        updateStatusBarForFragment(fragment, animate = !isInitialAttach)
        updatePartnerReferralForTab(tabId)

        if (isInitialAttach) {
            outgoingFragment?.view?.let { transitionController.resetOutgoingTransform(it) }
            outgoingFragment?.view?.visibility = android.view.View.GONE
            binding.fragmentContainer.post { setupScrollBehaviorForCurrentFragment() }
            return
        }

        val incomingView = fragment.view ?: return
        val outgoingView = outgoingFragment?.view

        transitionController.runSwitch(
            incoming = incomingView,
            outgoing = outgoingView,
            forward = forward,
            startVelocityPxPerSec = 0f,
        ) {
            outgoingView?.let {
                transitionController.resetOutgoingTransform(it)
                it.visibility = android.view.View.GONE
            }
            setupScrollBehaviorForCurrentFragment()
        }
    }

    // ── Partner referral chip ───────────────────────────────────────
    // A promo that lives on every tab becomes furniture, so the chip is scoped to
    // Home and tucks itself off the right edge while the user is reading down the
    // page — it returns the moment they scroll back up or reach the top.

    private var referralChipShown = true
    private var referralScrollHookedView: android.view.View? = null
    private var referralLastScrollY = 0
    private var homeNativeAdAvailable = false
    private var homeNativeAdShown = false

    private fun updatePartnerReferralForTab(tabId: Int) {
        setPartnerReferralVisible(tabId == CustomBottomNavigation.TAB_HOME)
        updateHomeNativeAdPresentation()
    }

    private fun setPartnerReferralVisible(visible: Boolean) {
        if (!isViewReady) return
        val chip = binding.btnPartnerReferral
        if (referralChipShown == visible) return
        referralChipShown = visible

        chip.animate().cancel()
        if (visible) chip.visibility = android.view.View.VISIBLE
        // Slides out the way it came in — off its own edge — rather than fading in
        // place, so it reads as tucking away instead of blinking out.
        chip.animate()
            .alpha(if (visible) 1f else 0f)
            .translationX(if (visible) 0f else chip.width.toFloat())
            .setDuration(if (visible) 240 else 180)
            .setInterpolator(tabAnimInterpolator)
            .withEndAction {
                if (!visible) chip.visibility = android.view.View.INVISIBLE
            }
            .start()
    }

    /** Called by Home after the portrait native creative becomes available or is cleared. */
    fun setHomeNativeAdAvailable(available: Boolean) {
        if (!isViewReady) return
        homeNativeAdAvailable = available
        updateHomeNativeAdPresentation()
    }

    // Unlike the referral chip, the ad does NOT tuck away on scroll. The chip is a promo that
    // would become furniture if it followed the user down the page; the ad is the placement
    // itself, and its visible-time clock is what the unit earns from — pausing that every time
    // someone reads down Home costs real revenue for no clarity gained. It stays pinned for as
    // long as Home is the front tab.
    private fun updateHomeNativeAdPresentation() {
        setHomeNativeAdVisible(
            activityResumedForAds &&
                homeNativeAdAvailable &&
                currentTabId == CustomBottomNavigation.TAB_HOME
        )
    }

    /** Applies the newest UMP result without starting a request on a hidden Home tab. */
    fun onAdsConsentResult(canRequestAds: Boolean) {
        val home = supportFragmentManager.fragments
            .filterIsInstance<HomeFragment>()
            .firstOrNull()
            ?: return
        if (!canRequestAds) {
            home.onAdsConsentUnavailable()
        } else if (activityResumedForAds && currentTabId == CustomBottomNavigation.TAB_HOME) {
            home.onAdsConsentReady()
        }
    }

    private fun setHomeNativeAdVisible(visible: Boolean) {
        if (!isViewReady) return
        supportFragmentManager.fragments
            .filterIsInstance<HomeFragment>()
            .firstOrNull()
            ?.onHomeNativeAdDockVisibilityChanged(visible)
        if (homeNativeAdShown == visible) return
        homeNativeAdShown = visible

        val dock = binding.homeNativeAdDock
        dock.animate().cancel()
        if (visible) {
            dock.visibility = android.view.View.VISIBLE
            dock.alpha = 0f
            dock.translationX = -12f * resources.displayMetrics.density
        }
        dock.animate()
            .alpha(if (visible) 1f else 0f)
            .translationX(if (visible) 0f else -dock.width.toFloat())
            .setDuration(if (visible) 240 else 180)
            .setInterpolator(tabAnimInterpolator)
            .withEndAction {
                if (!homeNativeAdShown) dock.visibility = android.view.View.INVISIBLE
            }
            .start()
    }

    // ── Transient-banner clearance ──────────────────────────────────
    // The in-app banner is anchored to the same edge as the ad dock and the referral pill and
    // outranks both on elevation, so left alone it draws straight over them — burying the ad's
    // headline and call-to-action, and swallowing taps aimed at the ad while it was on screen.
    // Instead the banner reports its footprint (see BottomOverlayHost) and the dock springs up
    // out of the way, so the banner lands in genuinely empty space and the ad stays whole.

    private var bottomOverlayLiftPx = 0f
    private val dockLiftSprings = mutableListOf<androidx.dynamicanimation.animation.SpringAnimation>()

    override fun onBottomOverlayHeightChanged(overlayHeightAboveNavPx: Int) {
        if (!isViewReady) return
        val density = resources.displayMetrics.density
        // The dock already sits DOCK_REST_GAP_DP clear of the tab bar, so it only has to travel
        // the part of the banner's footprint that reaches past that, plus a breathing gap.
        val lift = (
            overlayHeightAboveNavPx + (DOCK_OVERLAY_GAP_DP - DOCK_REST_GAP_DP) * density
            ).coerceAtLeast(0f)
        if (lift == bottomOverlayLiftPx) return
        bottomOverlayLiftPx = lift
        springDockTranslationY(-lift)
    }

    /**
     * Mirrors the banner's own physics so the dock and the card feel like one gesture: the
     * softer 280 spring on the way up matches its entry glide, the stiffer 750 on the way back
     * matches its exit. Critically damped in both directions — the dock must never bounce.
     *
     * Deliberately a SpringAnimation on TRANSLATION_Y rather than the ViewPropertyAnimator the
     * show/hide paths use: those own alpha and translationX, and their `.cancel()` would
     * otherwise abandon the lift mid-travel.
     */
    private fun springDockTranslationY(target: Float) {
        val stiffness = if (target < 0f) 280f else 750f
        for (spring in dockLiftSprings) {
            if (spring.isRunning) spring.cancel()
        }
        dockLiftSprings.clear()

        for (view in listOf(binding.homeNativeAdDock, binding.btnPartnerReferral)) {
            val anim = androidx.dynamicanimation.animation.SpringAnimation(
                view,
                androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y
            ).apply {
                spring = androidx.dynamicanimation.animation.SpringForce(target).apply {
                    dampingRatio =
                        androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_NO_BOUNCY
                    this.stiffness = stiffness
                }
            }
            dockLiftSprings.add(anim)
            anim.start()
        }
    }

    private fun hookPartnerReferralToScroll(scrollable: android.view.View) {
        if (referralScrollHookedView === scrollable) return
        referralScrollHookedView = scrollable

        val readScrollY: () -> Int = when (scrollable) {
            is androidx.core.widget.NestedScrollView -> { { scrollable.scrollY } }
            is android.widget.ScrollView -> { { scrollable.scrollY } }
            is androidx.recyclerview.widget.RecyclerView -> {
                { scrollable.computeVerticalScrollOffset() }
            }
            else -> return
        }

        val density = resources.displayMetrics.density
        // Ignore sub-pixel jitter and the rubber-band at the top; only a deliberate
        // drag should move the chip.
        val deltaThresholdPx = 8f * density
        val topZonePx = 24f * density

        referralLastScrollY = readScrollY()
        scrollable.viewTreeObserver.addOnScrollChangedListener {
            if (currentTabId != CustomBottomNavigation.TAB_HOME) return@addOnScrollChangedListener
            val y = readScrollY()
            val delta = y - referralLastScrollY
            if (y <= topZonePx) {
                referralLastScrollY = y
                setPartnerReferralVisible(true)
                return@addOnScrollChangedListener
            }
            if (kotlin.math.abs(delta) < deltaThresholdPx) return@addOnScrollChangedListener
            referralLastScrollY = y
            setPartnerReferralVisible(delta < 0)
        }
    }

    private fun setupPartnerReferralButton() {
        binding.btnPartnerReferral.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            // Same squish the other floating controls use, so the pill has weight.
            // Pivot on the right edge: the button is docked flush to the screen edge,
            // so a centre-pivot squish would peel it away and expose its square corner.
            view.pivotX = view.width.toFloat()
            view.pivotY = view.height / 2f
            view.animate()
                .scaleX(0.94f).scaleY(0.94f)
                .setDuration(90)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    view.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(130)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                .start()

            if (supportFragmentManager.findFragmentByTag(PartnerReferralBottomSheet.TAG) == null) {
                PartnerReferralBottomSheet().show(
                    supportFragmentManager,
                    PartnerReferralBottomSheet.TAG
                )
            }
        }
    }

    private fun setupSwipeGesture() {
        val container = binding.fragmentContainer
        val commitVelocityPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            MotionTokens.SWIPE_COMMIT_VELOCITY_DP,
            resources.displayMetrics,
        )

        container.setSwipeListener(object : TabSwipeGestureDetector.Listener {
            override fun canSwipe(forward: Boolean): Boolean {
                val target = if (forward) currentTabId + 1 else currentTabId - 1
                return target in CustomBottomNavigation.TAB_HOME..CustomBottomNavigation.TAB_PROFILE
                    && isTabEnabled(target)
                // Allow a new swipe even if a prior commit/cancel spring is still settling;
                // we cancel the in-flight transition and clean up any stale view in onSwipeBegin.
            }

            override fun onSwipeBegin(forward: Boolean) {
                val targetTabId = if (forward) currentTabId + 1 else currentTabId - 1
                val incoming = getFragmentForTab(targetTabId)
                swipeForward = forward
                swipeIncomingFragment = incoming
                swipeInProgress = true
                swipeGeneration++

                transitionController.cancelAll()

                // If a prior transition left a fragment view mid-transform, clean it now so
                // it doesn't linger behind the new transition.
                staleOutgoingView?.let { stale ->
                    if (stale !== incoming.view && stale !== currentFragment?.view) {
                        transitionController.resetOutgoingTransform(stale)
                        stale.visibility = android.view.View.GONE
                    }
                    staleOutgoingView = null
                }

                if (!incoming.isAdded) {
                    supportFragmentManager.beginTransaction()
                        .add(R.id.fragment_container, incoming)
                        .hide(incoming)
                        .setMaxLifecycle(incoming, androidx.lifecycle.Lifecycle.State.STARTED)
                        .commitNowAllowingStateLoss()
                    applyTopInsetToFragmentView(incoming)
                }
                val incView = incoming.view ?: run {
                    // Defensive: if the fragment's view didn't materialize, abort the swipe.
                    cancelSwipeState()
                    return
                }
                // Force visible for the drag — FragmentManager still considers it hidden;
                // we only call show() on commit. On cancel, we restore visibility=GONE.
                incView.visibility = android.view.View.VISIBLE
                transitionController.attachScrimIfNeeded()
                transitionController.setProgress(0f, incView, currentFragment?.view, forward)
                bottomNavigation.previewActiveTab(targetTabId, 0f)
            }

            override fun onSwipeProgress(progress: Float) {
                val incoming = swipeIncomingFragment ?: return
                val incView = incoming.view ?: return
                transitionController.setProgress(progress, incView, currentFragment?.view, swipeForward)
                val targetTabId = if (swipeForward) currentTabId + 1 else currentTabId - 1
                bottomNavigation.previewActiveTab(targetTabId, progress)
                blendStatusBarForSwipe(incoming, progress)
            }

            override fun onSwipeRelease(forward: Boolean, progress: Float, velocityPxPerSec: Float) {
                val incoming = swipeIncomingFragment ?: return cancelSwipeState()
                val incView = incoming.view ?: return cancelSwipeState()
                val outgoing = currentFragment
                val outView = outgoing?.view
                val myGen = swipeGeneration

                val shouldCommit = progress >= MotionTokens.SWIPE_COMMIT_FRACTION ||
                    velocityPxPerSec >= commitVelocityPx

                if (shouldCommit) {
                    val targetTabId = if (forward) currentTabId + 1 else currentTabId - 1
                    bottomNavigation.setActiveTab(targetTabId)

                    val previousFragment = outgoing
                    currentFragment = incoming
                    currentTabId = targetTabId

                    // show() / hide() so FragmentManager fires onHiddenChanged on both
                    // (auto-refresh timers, etc. depend on this).
                    val transaction = supportFragmentManager.beginTransaction()
                        .show(incoming)
                        .setMaxLifecycle(incoming, androidx.lifecycle.Lifecycle.State.RESUMED)
                    previousFragment?.let {
                        transaction.hide(it)
                        transaction.setMaxLifecycle(it, androidx.lifecycle.Lifecycle.State.STARTED)
                    }
                    transaction.commitNowAllowingStateLoss()

                    // FragmentManager's hide() just set the outgoing view to GONE; override
                    // so the parallax spring can still render it. End callback re-hides it.
                    outView?.visibility = android.view.View.VISIBLE
                    incView.visibility = android.view.View.VISIBLE

                    updateStatusBarForFragment(incoming, animate = true)
                    updatePartnerReferralForTab(targetTabId)

                    // Mark outgoing as stale so a swipe that interrupts this settle can clean it.
                    staleOutgoingView = outView

                    transitionController.commitInteractive(
                        incoming = incView,
                        outgoing = outView,
                        forward = forward,
                        startVelocityPxPerSec = velocityPxPerSec,
                    ) {
                        outView?.let {
                            transitionController.resetOutgoingTransform(it)
                            it.visibility = android.view.View.GONE
                        }
                        // Only touch shared state if no newer swipe has taken over.
                        if (myGen == swipeGeneration) {
                            staleOutgoingView = null
                            setupScrollBehaviorForCurrentFragment()
                            cancelSwipeState()
                        }
                    }
                } else {
                    bottomNavigation.springPillToCurrent()
                    transitionController.cancelInteractive(
                        incoming = incView,
                        outgoing = outView,
                        forward = forward,
                        startVelocityPxPerSec = -velocityPxPerSec,
                    ) {
                        // Restore incoming to its FragmentManager-hidden state (visibility GONE)
                        // since we never committed the show(). cancelInteractive sets GONE already
                        // but the assignment is idempotent and explicit.
                        incView.visibility = android.view.View.GONE
                        // Snap status bar back to the unchanged fragment's color, unless a
                        // newer swipe has taken over (in which case it owns the status bar now).
                        if (myGen == swipeGeneration) {
                            currentFragment?.let { updateStatusBarForFragment(it, animate = true) }
                            cancelSwipeState()
                        }
                    }
                }
            }

            override fun onSwipeCancelled() {
                cancelSwipeState()
            }
        })
    }

    private fun cancelSwipeState() {
        swipeInProgress = false
        swipeIncomingFragment = null
    }

    private fun enableHighRefreshRateIfSupported() {
        // ENABLE HIGH REFRESH RATE (90Hz / 120Hz)
        // This ensures the OS doesn't throttle the app to 60Hz to save battery,
        // allowing our physics animations to run at maximum smoothness.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                window.windowManager.defaultDisplay
            }

            val supportedModes = display?.supportedModes
            // Find the mode with the highest refresh rate
            val highestRefreshRateMode = supportedModes?.maxByOrNull { it.refreshRate }

            if (highestRefreshRateMode != null && highestRefreshRateMode.refreshRate >= 90f) {
                val layoutParams = window.attributes
                layoutParams.preferredDisplayModeId = highestRefreshRateMode.modeId
                window.attributes = layoutParams
            }
        } catch (e: Exception) {
            // Fallback safely if display query fails
        }
    }

    private fun scrollCurrentFragmentToTop() {
        currentFragment?.let { fragment ->
            when (fragment) {
                is HomeFragment -> fragment.scrollToTop()
                is BookingsFragmentNew -> fragment.scrollToTop()
                is WalletFragmentNew -> fragment.scrollToTop()
                is ProfileFragment -> fragment.scrollToTop()
                else -> {
                    // Handle unknown fragment types
                }
            }
        }
    }

    private fun statusBarColorFor(fragment: Fragment?): Int {
        return if (fragment is HomeFragment) android.graphics.Color.TRANSPARENT
        else ContextCompat.getColor(this, R.color.background_primary)
    }

    private fun statusBarLightIconsFor(fragment: Fragment?): Boolean {
        return if (fragment is HomeFragment) false else !ThemeManager.isDarkMode(this)
    }

    /**
     * Blend the status bar color between the current and the candidate fragment for
     * interactive swipes. progress=0 stays on the current, progress=1 lands on the target.
     * Icon tint flips at progress >= 0.5 (mid-transition) to avoid a mid-drag flash.
     */
    private fun blendStatusBarForSwipe(targetFragment: Fragment?, progress: Float) {
        val window = window ?: return
        val from = statusBarColorFor(currentFragment)
        val to = statusBarColorFor(targetFragment)
        statusBarColorAnimator?.cancel()
        val blended = android.animation.ArgbEvaluator().evaluate(progress.coerceIn(0f, 1f), from, to) as Int
        window.statusBarColor = blended

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = if (progress < 0.5f) {
            statusBarLightIconsFor(currentFragment)
        } else {
            statusBarLightIconsFor(targetFragment)
        }
    }

    private fun updateStatusBarForFragment(fragment: Fragment, animate: Boolean = false) {
        val window = window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        val targetColor = statusBarColorFor(fragment)
        val lightIcons = statusBarLightIconsFor(fragment)

        statusBarColorAnimator?.cancel()
        val currentColor = window.statusBarColor
        if (!animate || currentColor == targetColor) {
            window.statusBarColor = targetColor
        } else {
            statusBarColorAnimator = android.animation.ValueAnimator
                .ofObject(android.animation.ArgbEvaluator(), currentColor, targetColor).apply {
                    duration = tabAnimDurationMs
                    interpolator = tabAnimInterpolator
                    addUpdateListener {
                        window.statusBarColor = it.animatedValue as Int
                    }
                    start()
                }
        }

        // Icon tint flips instantly — the OS doesn't support interpolating this and
        // a mid-transition flip is barely noticeable behind the moving content.
        controller.isAppearanceLightStatusBars = lightIcons
    }

    private fun applyTopInsetToFragmentView(fragment: Fragment?) {
        fragment ?: return
        val view = fragment.view ?: return
        val targetTop = if (fragment is HomeFragment) 0 else statusBarInsetTop
        if (view.paddingTop != targetTop) {
            view.updatePadding(top = targetTop)
        }
    }

    private fun setupScrollBehaviorForCurrentFragment() {
        currentFragment?.let { fragment ->
            when (fragment) {
                is HomeFragment -> fragment.getScrollableView()?.let {
                    setupScrollBehaviorForView(it)
                    hookPartnerReferralToScroll(it)
                }
                is BookingsFragmentNew -> fragment.getScrollableView()?.let { setupScrollBehaviorForView(it) }
                is WalletFragmentNew -> fragment.getScrollableView()?.let { setupScrollBehaviorForView(it) }
                is ProfileFragment -> fragment.getScrollableView()?.let { setupScrollBehaviorForView(it) }
                else -> {
                    // Handle unknown fragment types
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        val targetTab = intent.getIntExtra(EXTRA_TARGET_TAB, currentTabId)
        val allowedTargetTab = resolveAllowedTab(targetTab)
        if (allowedTargetTab != currentTabId) {
            bottomNavigation.setActiveTab(allowedTargetTab)
            onTabSelected(allowedTargetTab)
        }

        handleNavigationIntent(intent, allowedTargetTab)
        handleWalletGlobalIntents(intent)
    }

    private fun handleWalletGlobalIntents(intent: android.content.Intent?) {
        // Handle global real-time wallet notification popups
        if (intent?.getBooleanExtra(EXTRA_SHOW_WALLET_TRANSACTION, false) == true) {
            val title = intent.getStringExtra(EXTRA_WALLET_TRANSACTION_TITLE) ?: "Transaction Processed"
            val amount = intent.getStringExtra(EXTRA_WALLET_TRANSACTION_AMOUNT) ?: ""
            val isCredit = intent.getBooleanExtra(EXTRA_WALLET_TRANSACTION_IS_CREDIT, true)
            val routeToWallet = intent.getBooleanExtra(EXTRA_WALLET_TRANSACTION_ROUTE_TO_WALLET, false)
            
            // Clear the extra so it doesn't trigger again on rotation
            intent.removeExtra(EXTRA_SHOW_WALLET_TRANSACTION)

            val parentView = findViewById<android.view.ViewGroup>(R.id.fragment_container) 
                ?: window.decorView as? android.view.ViewGroup 
                ?: binding.root

            com.gridee.parking.utils.NotificationHelper.showWalletTransaction(
                parent = parentView,
                title = title,
                amountText = amount,
                isCredit = isCredit,
                duration = 5000L, // Slightly longer so user can tap it easily
                onClick = {
                    if (routeToWallet) {
                        bottomNavigation.setActiveTab(com.gridee.parking.ui.components.CustomBottomNavigation.TAB_WALLET)
                        onTabSelected(com.gridee.parking.ui.components.CustomBottomNavigation.TAB_WALLET)
                    } else {
                        val historyIntent = android.content.Intent(this, com.gridee.parking.ui.activities.TransactionHistoryActivity::class.java)
                        startActivity(historyIntent)
                    }
                },
                actionButtonText = if (routeToWallet) "View in Wallet" else null
            )
        }
    }

    private fun getFragmentForTab(tabId: Int): Fragment {
        return when (tabId) {
            CustomBottomNavigation.TAB_HOME -> homeFragment
            CustomBottomNavigation.TAB_BOOKINGS -> bookingsFragment
            CustomBottomNavigation.TAB_WALLET -> walletFragment
            CustomBottomNavigation.TAB_PROFILE -> profileFragment
            else -> homeFragment
        }
    }

    private fun resolveAllowedTab(tabId: Int, showMessage: Boolean = true): Int {
        if (isTabEnabled(tabId)) return tabId
        if (showMessage) showFeatureDisabled(tabId)
        return CustomBottomNavigation.TAB_HOME
    }

    private fun isTabEnabled(tabId: Int): Boolean {
        return when (tabId) {
            CustomBottomNavigation.TAB_BOOKINGS -> RemoteConfigManager.isFeatureEnabled("booking")
            CustomBottomNavigation.TAB_WALLET -> RemoteConfigManager.isFeatureEnabled("wallet")
            else -> true
        }
    }

    private fun showFeatureDisabled(tabId: Int) {
        val message = when (tabId) {
            CustomBottomNavigation.TAB_BOOKINGS -> "Booking is temporarily unavailable."
            CustomBottomNavigation.TAB_WALLET -> "Wallet is temporarily unavailable."
            else -> "This feature is temporarily unavailable."
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun handleNavigationIntent(intent: android.content.Intent?, targetTab: Int) {
        if (targetTab != CustomBottomNavigation.TAB_BOOKINGS) return

        val showPending = intent?.getBooleanExtra(EXTRA_SHOW_PENDING, false) ?: false
        val highlightBookingId = intent?.getStringExtra(EXTRA_HIGHLIGHT_BOOKING_ID)
        val openBookingId = intent?.getStringExtra(EXTRA_OPEN_BOOKING_ID)
        bookingsFragment.handleExternalNavigation(showPending, highlightBookingId, openBookingId)
    }

    // Handle back button to navigate to home or exit
    override fun onBackPressed() {
        when (currentTabId) {
            CustomBottomNavigation.TAB_HOME -> {
                // Exit app from home
                super.onBackPressed()
            }
            else -> {
                // Navigate to home
                bottomNavigation.setActiveTab(CustomBottomNavigation.TAB_HOME)
                onTabSelected(CustomBottomNavigation.TAB_HOME)
            }
        }
    }

    override fun onDestroy() {
        // On the redirect path nothing was initialized; touching transitionController here
        // would lazily build it from the null binding. Just hand off to super.
        if (!isViewReady) {
            super.onDestroy()
            return
        }
        statusBarColorAnimator?.cancel()
        statusBarColorAnimator = null
        activityResumedForAds = false
        setHomeNativeAdVisible(false)
        transitionController.cancelAll()
        super.onDestroy()
    }
}
