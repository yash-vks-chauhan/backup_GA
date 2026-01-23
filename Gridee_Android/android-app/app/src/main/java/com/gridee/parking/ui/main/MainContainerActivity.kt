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
import com.gridee.parking.databinding.ActivityMainContainerBinding
import com.gridee.parking.ui.base.BaseActivityWithBottomNav
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.fragments.BookingsFragmentNew
import com.gridee.parking.ui.fragments.HomeFragment
import com.gridee.parking.ui.fragments.ProfileFragment
import com.gridee.parking.ui.fragments.WalletFragmentNew
import com.gridee.parking.utils.NotificationPermissionHelper

class MainContainerActivity : BaseActivityWithBottomNav<ActivityMainContainerBinding>() {

    companion object {
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_SHOW_PENDING = "extra_show_pending"
        const val EXTRA_HIGHLIGHT_BOOKING_ID = "extra_highlight_booking_id"
        const val EXTRA_OPEN_BOOKING_ID = "extra_open_booking_id"
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }

    private var currentFragment: Fragment? = null
    private var currentTabId = CustomBottomNavigation.TAB_HOME
    private var statusBarInsetTop = 0

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
        requestNotificationPermissionIfNeeded()

                // Handle system window insets for proper edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarInsetTop = systemBarsInsets.top
            
            // Update padding based on active fragment
            updateContainerPaddingForFragment(currentFragment)
            
            insets
        }
        
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
        
        // Setup bottom navigation manually using binding
        setupBottomNavigationManually(binding.bottomNavigation)
        
        val initialTab = if (savedInstanceState == null) {
            intent?.getIntExtra(EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_HOME)
                ?: CustomBottomNavigation.TAB_HOME
        } else {
            savedInstanceState.getInt("current_tab", CustomBottomNavigation.TAB_HOME)
        }

        if (savedInstanceState == null) {
            bottomNavigation.setActiveTab(initialTab)
            switchToFragment(getFragmentForTab(initialTab), initialTab)
        } else {
            currentTabId = initialTab
            bottomNavigation.setActiveTab(currentTabId)
            currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        }

        handleNavigationIntent(intent, currentTabId)
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

        val targetFragment = getFragmentForTab(tabId)
        switchToFragment(targetFragment, tabId)
    }

    private fun switchToFragment(fragment: Fragment, tabId: Int) {
        val transaction = supportFragmentManager.beginTransaction()
        
        // Add smooth slide animations
        when {
            tabId > currentTabId -> {
                // Sliding right (forward)
                transaction.setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
            }
            tabId < currentTabId -> {
                // Sliding left (backward)
                transaction.setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
            }
            else -> {
                // Same position or initial load - use fade
                transaction.setCustomAnimations(
                    R.anim.fade_in,
                    R.anim.fade_out
                )
            }
        }

        // Hide current fragment and show target fragment
        currentFragment?.let { transaction.hide(it) }
        
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.fragment_container, fragment)
        }

        transaction.commitNowAllowingStateLoss()
        
        currentFragment = fragment
        currentTabId = tabId
        updateStatusBarForFragment(fragment)
        updateContainerPaddingForFragment(fragment)
        
        // Delay scroll behavior setup until fragment view is ready
        binding.fragmentContainer.post {
            setupScrollBehaviorForCurrentFragment()
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

    private fun updateStatusBarForFragment(fragment: Fragment) {
        val window = window ?: return
        if (fragment is HomeFragment) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        }
    }

    private fun updateContainerPaddingForFragment(fragment: Fragment?) {
        val topPadding = if (fragment is HomeFragment) 0 else statusBarInsetTop
        binding.fragmentContainer.updatePadding(top = topPadding)
    }

    private fun setupScrollBehaviorForCurrentFragment() {
        currentFragment?.let { fragment ->
            when (fragment) {
                is HomeFragment -> fragment.getScrollableView()?.let { setupScrollBehaviorForView(it) }
                is BookingsFragmentNew -> fragment.getScrollableView()?.let { setupScrollBehaviorForView(it) }
                is WalletFragmentNew -> fragment.getScrollableView()?.let { setupScrollBehaviorForView(it) }
                is ProfileFragment -> fragment.getScrollableView()?.let { setupScrollBehaviorForView(it) }
                else -> {
                    // Handle unknown fragment types
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent ?: return

        setIntent(intent)

        val targetTab = intent.getIntExtra(EXTRA_TARGET_TAB, currentTabId)
        if (targetTab != currentTabId) {
            bottomNavigation.setActiveTab(targetTab)
            onTabSelected(targetTab)
        }

        handleNavigationIntent(intent, targetTab)
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
}
