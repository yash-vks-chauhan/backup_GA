package com.gridee.parking.ui.main

import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.databinding.ActivityMainContainerBinding
import com.gridee.parking.ui.base.BaseActivityWithBottomNav
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.ui.base.BottomOverlayHost
import com.gridee.parking.ui.base.LifecycleBoundScrollListener
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

internal fun normalizeMainTabId(tabId: Int): Int = when (tabId) {
    CustomBottomNavigation.TAB_HOME,
    CustomBottomNavigation.TAB_BOOKINGS,
    CustomBottomNavigation.TAB_WALLET,
    CustomBottomNavigation.TAB_PROFILE -> tabId
    else -> CustomBottomNavigation.TAB_HOME
}

class MainContainerActivity :
    BaseActivityWithBottomNav<ActivityMainContainerBinding>(),
    BottomOverlayHost {

    private data class TabSwitchTrace(val name: String, val cookie: Int)

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
        private const val STATE_CURRENT_TAB = "current_tab"
        private const val STATE_SIGNUP_GIFT_PENDING = "signup_gift_pending"
        private const val SIGNUP_GIFT_DELAY_MS = 600L
        private const val BOOKINGS_SWITCH_TRACE = "perf024_first_bookings_switch"
        private const val WALLET_SWITCH_TRACE = "perf024_first_wallet_switch"
        private const val PROFILE_SWITCH_TRACE = "perf024_first_profile_switch"

        internal const val TAB_TAG_HOME = "gridee.main.tab.home"
        internal const val TAB_TAG_BOOKINGS = "gridee.main.tab.bookings"
        internal const val TAB_TAG_WALLET = "gridee.main.tab.wallet"
        internal const val TAB_TAG_PROFILE = "gridee.main.tab.profile"

        /** Resting gap between the floating dock controls and the tab bar, per the layout. */
        private const val DOCK_REST_GAP_DP = 22f

        /** Breathing room left between a transient banner and the lifted dock above it. */
        private const val DOCK_OVERLAY_GAP_DP = 10f
    }

    private var currentFragment: Fragment? = null
    private var currentTabId = CustomBottomNavigation.TAB_HOME
    private var activeTabSwitchTrace: TabSwitchTrace? = null
    private var tabSwitchTraceSequence = 0
    private var statusBarInsetTop = 0
    private var renderedThemeMode: String? = null
    private var renderedDarkMode: Boolean? = null
    private var activityResumedForAds = false
    private var signupGiftPending = false
    private var signupGiftLaunchScheduled = false
    private val signupGiftLaunchRunnable = Runnable {
        signupGiftLaunchScheduled = false
        launchPendingSignupGiftIfSafe()
    }

    private val tabAnimDurationMs: Long = 360L
    private val tabAnimInterpolator by lazy {
        androidx.core.view.animation.PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f)
    }
    private var statusBarColorAnimator: android.animation.ValueAnimator? = null
    private var currentStatusBarColor: Int = android.graphics.Color.TRANSPARENT

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

    // Android restores fragments during super.onCreate(). Keep factories lazy, but first rebind
    // this registry to every restored tab so no second instance can be created after process death.
    private val tabFragments = RestorableTabFragmentRegistry(
        listOf(
            RestorableTabSpec(
                CustomBottomNavigation.TAB_HOME,
                TAB_TAG_HOME,
                HomeFragment::class.java,
            ) { HomeFragment() },
            RestorableTabSpec(
                CustomBottomNavigation.TAB_BOOKINGS,
                TAB_TAG_BOOKINGS,
                BookingsFragmentNew::class.java,
            ) { BookingsFragmentNew() },
            RestorableTabSpec(
                CustomBottomNavigation.TAB_WALLET,
                TAB_TAG_WALLET,
                WalletFragmentNew::class.java,
            ) { WalletFragmentNew() },
            RestorableTabSpec(
                CustomBottomNavigation.TAB_PROFILE,
                TAB_TAG_PROFILE,
                ProfileFragment::class.java,
            ) { ProfileFragment() },
        )
    )

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
        signupGiftPending = savedInstanceState?.getBoolean(STATE_SIGNUP_GIFT_PENDING) == true
        captureSignupGiftRequest(intent)
        rememberRenderedTheme()

        // Handle system window insets for proper edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarInsetTop = systemBarsInsets.top
            if (binding.statusBarScrim.layoutParams.height != statusBarInsetTop) {
                binding.statusBarScrim.updateLayoutParams {
                    height = statusBarInsetTop
                }
            }

            // Re-apply the inset to every added fragment. This covers restored tabs and a tab
            // selected before the first insets dispatch without creating any hidden tab views.
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
            savedInstanceState.getInt(STATE_CURRENT_TAB, CustomBottomNavigation.TAB_HOME)
        }
        val initialTab = resolveAllowedTab(requestedInitialTab, showMessage = false)
        currentTabId = initialTab

        // FragmentManager has already restored its instances at this point. Reconcile all root
        // tabs synchronously before intent routing can request a fragment. On a fresh launch this
        // creates only the selected tab; inactive tabs stay uncreated until the user taps or
        // swipes to them. The saved tab is authoritative; container insertion order is not.
        currentFragment = tabFragments.reconcileRestoredState(
            fragmentManager = supportFragmentManager,
            containerId = R.id.fragment_container,
            selectedTabId = initialTab,
        ).selectedFragment
        bottomNavigation.setActiveTab(initialTab)
        currentFragment?.let {
            applyTopInsetToFragmentView(it)
            updateStatusBarForFragment(it, animate = false)
        }
        updatePartnerReferralForTab(initialTab)
        binding.fragmentContainer.post { setupScrollBehaviorForCurrentFragment() }

        handleNavigationIntent(intent, currentTabId)
        handleWalletGlobalIntents(intent)

        // Defer non-critical startup work until after first frame to avoid a blank/grey handoff.
        binding.root.post {
            enableHighRefreshRateIfSupported()
            requestNotificationPermissionIfNeeded()
            showLoginWelcomeIfNeeded()
            showSignupGiftIfNeeded()
        }

    }

    override fun onResume() {
        super.onResume()
        if (!isViewReady) return // redirected to login; nothing was set up
        activityResumedForAds = true
        // Cheap safety net: if a slide-out was interrupted before this activity was last
        // backgrounded, the chip can come back stranded on a tab that should not show it.
        updatePartnerReferralForTab(currentTabId)
        updateHomeNativeAdPresentation()
        refreshAfterDeferredThemeChangeIfNeeded()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!isViewReady) return
        scheduleSignupGiftIfPossible()
    }

    override fun onPause() {
        if (isViewReady) {
            binding.root.removeCallbacks(signupGiftLaunchRunnable)
            signupGiftLaunchScheduled = false
        }
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
        captureSignupGiftRequest(intent)
        scheduleSignupGiftIfPossible()
    }

    /**
     * Moves the one-shot intent flag into saved Activity state before clearing the Intent.
     * A rotation during the settle delay therefore postpones the gift instead of losing it.
     */
    private fun captureSignupGiftRequest(source: android.content.Intent?) {
        if (source?.getBooleanExtra(EXTRA_SHOW_SIGNUP_GIFT, false) != true) return
        signupGiftPending = true
        source.removeExtra(EXTRA_SHOW_SIGNUP_GIFT)
    }

    private fun scheduleSignupGiftIfPossible() {
        if (!signupGiftPending || signupGiftLaunchScheduled || !isViewReady) return

        // A restored instance means FragmentManager already owns this request. Treat it as
        // consumed even if its view has not been recreated yet, so no duplicate is enqueued.
        if (supportFragmentManager.findFragmentByTag(WelcomeGiftBottomSheet.TAG) != null) {
            signupGiftPending = false
            return
        }
        if (isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            supportFragmentManager.isDestroyed || supportFragmentManager.isStateSaved
        ) {
            return
        }

        signupGiftLaunchScheduled = true
        binding.root.postDelayed(signupGiftLaunchRunnable, SIGNUP_GIFT_DELAY_MS)
    }

    private fun launchPendingSignupGiftIfSafe() {
        if (!signupGiftPending || !isViewReady) return
        val fragmentManager = supportFragmentManager
        if (fragmentManager.findFragmentByTag(WelcomeGiftBottomSheet.TAG) != null) {
            signupGiftPending = false
            return
        }
        if (isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            fragmentManager.isDestroyed || fragmentManager.isStateSaved
        ) {
            // Keep the saved pending bit. onPostResume() will schedule it again when transactions
            // are legal instead of committing after state save or silently dropping the gift.
            return
        }

        // Display the actual welcome bonus from remote config so the sheet never drifts from
        // what was credited to the wallet. showNow records the stable tag before another launch
        // can be admitted on the same main-loop turn.
        val bonusAmount = RemoteConfigManager.currentConfig.financial.welcomeBonusAmount.toInt()
        val shown = runCatching {
            WelcomeGiftBottomSheet.newInstance(coinAmount = bonusAmount)
                .showNow(fragmentManager, WelcomeGiftBottomSheet.TAG)
        }.isSuccess
        if (shown) signupGiftPending = false
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
        outState.putInt(STATE_CURRENT_TAB, currentTabId)
        outState.putBoolean(STATE_SIGNUP_GIFT_PENDING, signupGiftPending)
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

        val tabSwitchTrace = beginTabSwitchTrace(tabId)
        val targetFragment = getFragmentForTab(tabId)
        switchToFragment(targetFragment, tabId, tabSwitchTrace)
    }

    private fun switchToFragment(
        fragment: Fragment,
        tabId: Int,
        tabSwitchTrace: TabSwitchTrace?,
    ) {
        if (supportFragmentManager.isStateSaved) {
            // A late UI callback must not create state that the next process cannot restore.
            bottomNavigation.setActiveTab(currentTabId)
            endTabSwitchTrace(tabSwitchTrace)
            return
        }

        // Cancel any in-flight spring so a rapid second tap picks up from current position.
        transitionController.cancelAll()

        val isInitialAttach = currentFragment == null
        val outgoingFragment = currentFragment
        val forward = tabId > currentTabId

        val transaction = supportFragmentManager.beginTransaction()
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            tabFragments.addTo(transaction, R.id.fragment_container, fragment)
        }
        transaction.setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.RESUMED)
        transaction.setPrimaryNavigationFragment(fragment)
        outgoingFragment?.let {
            transaction.hide(it)
            transaction.setMaxLifecycle(it, androidx.lifecycle.Lifecycle.State.STARTED)
        }
        transaction.commitNow()

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
            endTabSwitchTrace(tabSwitchTrace)
            return
        }

        val incomingView = fragment.view ?: run {
            endTabSwitchTrace(tabSwitchTrace)
            return
        }
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
            endTabSwitchTrace(tabSwitchTrace)
        }
    }

    private fun beginTabSwitchTrace(tabId: Int): TabSwitchTrace? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        endTabSwitchTrace()
        val traceName = when (tabId) {
            CustomBottomNavigation.TAB_BOOKINGS -> BOOKINGS_SWITCH_TRACE
            CustomBottomNavigation.TAB_WALLET -> WALLET_SWITCH_TRACE
            CustomBottomNavigation.TAB_PROFILE -> PROFILE_SWITCH_TRACE
            else -> return null
        }
        tabSwitchTraceSequence++
        val trace = TabSwitchTrace(
            name = traceName,
            cookie = System.identityHashCode(this) xor tabSwitchTraceSequence,
        )
        activeTabSwitchTrace = trace
        Trace.beginAsyncSection(trace.name, trace.cookie)
        return trace
    }

    private fun endTabSwitchTrace(expected: TabSwitchTrace? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val activeTrace = activeTabSwitchTrace ?: return
        if (expected != null && activeTrace != expected) return
        Trace.endAsyncSection(activeTrace.name, activeTrace.cookie)
        activeTabSwitchTrace = null
    }

    // ── Partner referral chip ───────────────────────────────────────
    // A promo that lives on every tab becomes furniture, so the chip is scoped to
    // Home and tucks itself off the right edge while the user is reading down the
    // page — it returns the moment they scroll back up or reach the top.

    private var referralChipShown = true

    /**
     * Owns the chip's ALPHA and TRANSLATION_X. Kept off the view's ViewPropertyAnimator on
     * purpose — the ad dock cancels that to manage TRANSLATION_Y, and the two were killing
     * each other's animations.
     */
    private var referralChipAnimator: android.animation.AnimatorSet? = null
    private val referralScrollListener = LifecycleBoundScrollListener()
    private var referralLastScrollY = 0
    private var homeNativeAdAvailable = false
    private var homeCampaignAvailable = false
    private var homeNativeAdShown = false

    private fun updatePartnerReferralForTab(tabId: Int) {
        setPartnerReferralVisible(tabId == CustomBottomNavigation.TAB_HOME)
        updateHomeNativeAdPresentation()
    }

    private fun setPartnerReferralVisible(visible: Boolean) {
        if (!isViewReady) return
        val chip = binding.btnPartnerReferral

        if (referralChipShown == visible) {
            // The flag and the view can disagree. The slide-out is what sets INVISIBLE, in
            // withEndAction — so a cancelled animation (any tab switch during those 180ms,
            // or a width of 0 before the chip has measured) leaves the chip stranded
            // part-way off screen and still VISIBLE, with the flag already reading hidden.
            // That is how it came to sit on Bookings, Wallet and Profile. Re-assert instead
            // of trusting the flag.
            if (!visible && chip.visibility == android.view.View.VISIBLE) {
                settleReferralChip(chip, false)
            }
            return
        }
        referralChipShown = visible

        // Deliberately NOT chip.animate(): a View has one ViewPropertyAnimator, and
        // setHomeNativeAdVisible cancels it to stop its own TRANSLATION_Y lift. Since
        // updatePartnerReferralForTab calls that immediately after this, the fade-in was
        // being cancelled the same frame it started — leaving the chip at alpha 0 every
        // time you came back to Home with an ad loaded. First open looked fine only
        // because the flag already read shown, so no animation ran to be cancelled.
        //
        // Owning ALPHA and TRANSLATION_X on a separate animator splits the properties
        // cleanly: show/hide is ours, the dock's lift keeps TRANSLATION_Y.
        referralChipAnimator?.cancel()
        if (visible) chip.visibility = android.view.View.VISIBLE
        // Slides out the way it came in — off its own edge — rather than fading in
        // place, so it reads as tucking away instead of blinking out.
        val animator = android.animation.AnimatorSet().apply {
            playTogether(
                android.animation.ObjectAnimator.ofFloat(
                    chip, android.view.View.ALPHA, chip.alpha, if (visible) 1f else 0f
                ),
                android.animation.ObjectAnimator.ofFloat(
                    chip,
                    android.view.View.TRANSLATION_X,
                    chip.translationX,
                    if (visible) 0f else referralSlideDistance(chip)
                )
            )
            duration = if (visible) 240 else 180
            interpolator = tabAnimInterpolator
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Only settle if nothing has asked for the opposite in the meantime.
                    if (referralChipShown == visible) settleReferralChip(chip, visible)
                }
            })
        }
        referralChipAnimator = animator
        animator.start()
    }

    /** The chip's resting state, applied outright. Safe to call at any time. */
    private fun settleReferralChip(chip: android.view.View, visible: Boolean) {
        referralChipAnimator?.cancel()
        referralChipAnimator = null
        chip.alpha = if (visible) 1f else 0f
        chip.translationX = if (visible) 0f else referralSlideDistance(chip)
        chip.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
    }

    /**
     * How far off its own edge the chip tucks. Falls back to a generous fixed distance
     * because width is 0 until the chip has been laid out, and animating to 0 there would
     * hide it by alpha alone — leaving it fading in place on the next show.
     */
    private fun referralSlideDistance(chip: android.view.View): Float =
        if (chip.width > 0) chip.width.toFloat() else 240f * resources.displayMetrics.density

    /** Called by Home after the network creative becomes available or is cleared. */
    fun setHomeNativeAdAvailable(available: Boolean) {
        if (!isViewReady) return
        homeNativeAdAvailable = available
        updateHomeNativeAdPresentation()
    }

    /** Called by Home when a first-party campaign takes the dock, or gives it back. */
    fun setHomeCampaignAvailable(available: Boolean) {
        if (!isViewReady) return
        homeCampaignAvailable = available
        updateHomeNativeAdPresentation()
    }

    /** The dock's campaign slot, for Home to bind. Null until the view is ready. */
    val homeCampaignSlot: com.gridee.parking.ui.ads.CustomAdBannerView?
        get() = if (isViewReady) binding.homeCampaignSlot else null

    // Unlike the referral chip, the ad does NOT tuck away on scroll. The chip is a promo that
    // would become furniture if it followed the user down the page; the ad is the placement
    // itself, and its visible-time clock is what the unit earns from — pausing that every time
    // someone reads down Home costs real revenue for no clarity gained. It stays pinned for as
    // long as Home is the front tab.
    private fun updateHomeNativeAdPresentation() {
        setHomeNativeAdVisible(
            activityResumedForAds &&
                (homeNativeAdAvailable || homeCampaignAvailable) &&
                currentTabId == CustomBottomNavigation.TAB_HOME
        )
    }

    /** Applies the newest UMP result without starting a request on a hidden Home tab. */
    fun onAdsConsentResult(canRequestAds: Boolean) {
        val home = tabFragments.existing(CustomBottomNavigation.TAB_HOME) as? HomeFragment
            ?: return
        if (!canRequestAds) {
            home.onAdsConsentUnavailable()
        } else if (activityResumedForAds && currentTabId == CustomBottomNavigation.TAB_HOME) {
            home.onAdsConsentReady()
        }
    }

    /**
     * The ad dock, for Home to measure when reserving scroll clearance beneath it. Null before
     * the view is ready — callers must treat that as "no clearance needed" rather than waiting.
     */
    val homeNativeAdDockView: android.view.View?
        get() = if (isViewReady) binding.homeNativeAdDock else null

    private fun setHomeNativeAdVisible(visible: Boolean) {
        if (!isViewReady) return
        (tabFragments.existing(CustomBottomNavigation.TAB_HOME) as? HomeFragment)
            ?.onHomeNativeAdDockVisibilityChanged(visible)
        if (homeNativeAdShown == visible) return
        homeNativeAdShown = visible

        // The dock fades; it does not travel. TRANSLATION_Y on this view belongs to the lift
        // spring in springDockTranslationY, and an entry animator sharing that property fought
        // it whenever the in-app banner arrived mid-reveal. The motion instead comes from the
        // card's own reveal() — a different view inside the dock — so nothing is contended.
        val dock = binding.homeNativeAdDock
        val pill = binding.btnPartnerReferral
        val restY = bottomOverlayLiftPx.unaryMinus()
        dock.animate().cancel()
        pill.animate().cancel()

        if (visible) {
            dock.visibility = android.view.View.VISIBLE
            dock.alpha = 0f
            dock.translationY = restY
            // The pill hangs off the dock's top edge, so revealing the dock displaces it by the
            // ad's entire height in a single frame. Start it where it already was and let it
            // ride up with the ad rather than teleporting out from under the user's thumb.
            dock.doOnLayout { laid ->
                if (!homeNativeAdShown) return@doOnLayout
                pill.translationY = restY + (laid.height + dockPillGapPx).toFloat()
                pill.animate()
                    .translationY(restY)
                    .setDuration(240)
                    .setInterpolator(tabAnimInterpolator)
                    .start()
            }
        } else {
            // Walk the pill down to where the collapse will leave it, then collapse — otherwise
            // it drops the ad's full height the instant visibility flips.
            pill.translationY = restY
            pill.animate()
                .translationY(restY + (dock.height + dockPillGapPx).toFloat())
                .setDuration(180)
                .setInterpolator(tabAnimInterpolator)
                .start()
        }

        dock.animate()
            .alpha(if (visible) 1f else 0f)
            .setDuration(if (visible) 240 else 180)
            .setInterpolator(tabAnimInterpolator)
            .withEndAction {
                // GONE, not INVISIBLE: the referral pill is constrained to this dock's top, so
                // leaving it INVISIBLE would reserve the ad's full height and strand the pill
                // above an empty gap. Collapsing lets the pill fall back to its goneMargin
                // resting place directly above the tab bar.
                if (!homeNativeAdShown) {
                    dock.visibility = android.view.View.GONE
                    // Layout has now moved the pill down for real; drop the compensation.
                    pill.animate().cancel()
                    pill.translationY = restY
                }
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

    /**
     * Gap between the ad and the referral pill sitting on top of it — must match the pill's
     * layout_marginBottom. 20dp rather than a tighter number because the whole ad surface is
     * click-registered: a thumb that undershoots the pill would otherwise land on the creative
     * and bill an advertiser for a tap the user never intended.
     */
    private val dockPillGapPx: Int
        get() = (20f * resources.displayMetrics.density).toInt()
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
            // The reveal/collapse animators above also drive translationY on these two views.
            // Cancel them here so the banner lift and the ad transition never pull the same
            // property in opposite directions.
            view.animate().cancel()
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

    private fun hookPartnerReferralToScroll(scrollable: android.view.View, owner: LifecycleOwner) {
        val density = resources.displayMetrics.density
        // Ignore sub-pixel jitter and the rubber-band at the top; only a deliberate
        // drag should move the chip.
        val deltaThresholdPx = 8f * density
        val topZonePx = 24f * density

        referralScrollListener.bind(
            view = scrollable,
            owner = owner,
            onActivated = { y -> referralLastScrollY = y },
            onScroll = { y ->
                if (currentTabId == CustomBottomNavigation.TAB_HOME) {
                    val delta = y - referralLastScrollY
                    if (y <= topZonePx) {
                        referralLastScrollY = y
                        setPartnerReferralVisible(true)
                    } else if (kotlin.math.abs(delta) >= deltaThresholdPx) {
                        referralLastScrollY = y
                        setPartnerReferralVisible(delta < 0)
                    }
                }
            },
        )
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

            val fragmentManager = supportFragmentManager
            if (isFinishing || isDestroyed ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                fragmentManager.isDestroyed || fragmentManager.isStateSaved ||
                fragmentManager.findFragmentByTag(PartnerReferralBottomSheet.TAG) != null
            ) {
                return@setOnClickListener
            }
            // Record the stable tag synchronously so a rapid second tap cannot enqueue another
            // instance before an asynchronous FragmentTransaction has executed.
            runCatching {
                PartnerReferralBottomSheet().showNow(
                    fragmentManager,
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
                    && !supportFragmentManager.isStateSaved
                // Allow a new swipe even if a prior commit/cancel spring is still settling;
                // we cancel the in-flight transition and clean up any stale view in onSwipeBegin.
            }

            override fun onSwipeBegin(forward: Boolean) {
                if (supportFragmentManager.isStateSaved) {
                    bottomNavigation.springPillToCurrent()
                    cancelSwipeState()
                    return
                }
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
                    val transaction = supportFragmentManager.beginTransaction()
                    tabFragments.addTo(transaction, R.id.fragment_container, incoming)
                        .hide(incoming)
                        .setMaxLifecycle(incoming, androidx.lifecycle.Lifecycle.State.STARTED)
                        .commitNow()
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
                if (supportFragmentManager.isStateSaved) {
                    incView.visibility = android.view.View.GONE
                    bottomNavigation.springPillToCurrent()
                    currentFragment?.let { updateStatusBarForFragment(it, animate = false) }
                    return cancelSwipeState()
                }
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
                        .setPrimaryNavigationFragment(incoming)
                    previousFragment?.let {
                        transaction.hide(it)
                        transaction.setMaxLifecycle(it, androidx.lifecycle.Lifecycle.State.STARTED)
                    }
                    transaction.commitNow()

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
        val from = statusBarColorFor(currentFragment)
        val to = statusBarColorFor(targetFragment)
        statusBarColorAnimator?.cancel()
        val blended = android.animation.ArgbEvaluator().evaluate(progress.coerceIn(0f, 1f), from, to) as Int
        currentStatusBarColor = blended
        binding.statusBarScrim.setBackgroundColor(blended)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = if (progress < 0.5f) {
            statusBarLightIconsFor(currentFragment)
        } else {
            statusBarLightIconsFor(targetFragment)
        }
    }

    private fun updateStatusBarForFragment(fragment: Fragment, animate: Boolean = false) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        val targetColor = statusBarColorFor(fragment)
        val lightIcons = statusBarLightIconsFor(fragment)

        statusBarColorAnimator?.cancel()
        val currentColor = currentStatusBarColor
        if (!animate || currentColor == targetColor) {
            currentStatusBarColor = targetColor
            binding.statusBarScrim.setBackgroundColor(targetColor)
        } else {
            statusBarColorAnimator = android.animation.ValueAnimator
                .ofObject(android.animation.ArgbEvaluator(), currentColor, targetColor).apply {
                    duration = tabAnimDurationMs
                    interpolator = tabAnimInterpolator
                    addUpdateListener {
                        currentStatusBarColor = it.animatedValue as Int
                        binding.statusBarScrim.setBackgroundColor(currentStatusBarColor)
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
        if (!isViewReady || isFinishing || isDestroyed) return
        val fragment = currentFragment as? BaseTabFragment<*>
        val scrollable = fragment?.getScrollableView()
        val owner = fragment?.viewLifecycleOwnerLiveData?.value
        if (scrollable == null || owner == null || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            clearScrollBehavior()
            referralScrollListener.clear()
            return
        }

        setupScrollBehaviorForView(scrollable, owner)
        if (fragment is HomeFragment) {
            hookPartnerReferralToScroll(scrollable, owner)
        } else {
            referralScrollListener.clear()
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
        showSignupGiftIfNeeded()
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
        val normalizedTabId = normalizeMainTabId(tabId)
        return tabFragments.getOrCreate(normalizedTabId)
    }

    private fun resolveAllowedTab(tabId: Int, showMessage: Boolean = true): Int {
        val normalizedTabId = normalizeMainTabId(tabId)
        if (isTabEnabled(normalizedTabId)) return normalizedTabId
        if (showMessage) showFeatureDisabled(normalizedTabId)
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
        (getFragmentForTab(CustomBottomNavigation.TAB_BOOKINGS) as BookingsFragmentNew)
            .handleExternalNavigation(showPending, highlightBookingId, openBookingId)
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
        referralScrollListener.clear()
        // On the redirect path nothing was initialized; touching transitionController here
        // would lazily build it from the null binding. Just hand off to super.
        if (!isViewReady) {
            super.onDestroy()
            return
        }
        statusBarColorAnimator?.cancel()
        statusBarColorAnimator = null
        binding.root.removeCallbacks(signupGiftLaunchRunnable)
        signupGiftLaunchScheduled = false
        activityResumedForAds = false
        setHomeNativeAdVisible(false)
        transitionController.cancelAll()
        endTabSwitchTrace()
        super.onDestroy()
    }
}
