package com.gridee.parking.ui.base

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.gridee.parking.R
import com.gridee.parking.ui.auth.LoginActivity
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.utils.configureEdgeToEdge
import com.gridee.parking.ui.utils.withClampedFontScale
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.InAppUpdateController

abstract class BaseActivityWithBottomNav<T : ViewBinding> : AppCompatActivity(), 
    CustomBottomNavigation.OnTabSelectedListener {
    
    private var _binding: T? = null
    protected val binding get() = _binding!!

    /**
     * True only after [onCreate] has inflated the binding. It stays false when the base
     * bailed out early — the user wasn't authenticated, so we redirected to login and
     * finished. Calling finish() does NOT stop a subclass's onCreate/onResume/onDestroy
     * from running, so every subclass MUST `if (!isViewReady) return` (right after its
     * super call) before touching [binding] or anything derived from it. Otherwise the
     * `_binding!!` getter throws NPE on the redirect path (expired token, process death).
     */
    protected val isViewReady: Boolean get() = _binding != null
    
    protected lateinit var bottomNavigation: CustomBottomNavigation

    private var inAppUpdateController: InAppUpdateController? = null

    abstract fun getViewBinding(): T
    abstract fun getCurrentTab(): Int

    override fun attachBaseContext(newBase: Context) {
        // Cap the system font scale so extreme "Font size" settings can't break layouts
        // on small screens. Covers all four main tabs + bottom sheets hosted here.
        super.attachBaseContext(newBase.withClampedFontScale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // All bottom-nav activities require an authenticated session.
        if (!AuthSession.isAuthenticated(this)) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra(LoginActivity.EXTRA_FORCE_LOGIN, true)
            startActivity(intent)
            finish()
            return
        }

        // Keep legacy prefs in sync for older screens that still rely on "gridee_prefs".
        AuthSession.syncLegacyPrefsFromJwt(this)
        
        configureEdgeToEdge()
        
        _binding = getViewBinding()
        setContentView(binding.root)

        // In-app update prompt (shows on the "home" container in Play builds).
        inAppUpdateController = InAppUpdateController(
            activity = this,
            snackbarAnchorView = binding.root,
        ).also { it.checkForUpdates() }
        
        // Apply window insets to the root view
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            // Don't apply any padding to the root - let child views handle it
            insets
        }
        
        setupBottomNavigation()
        setupScrollBehavior()
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        inAppUpdateController?.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        inAppUpdateController?.onActivityResult(requestCode, resultCode, data)
    }
    
    private fun setupBottomNavigation() {
        try {
            bottomNavigation = findViewById(R.id.bottom_navigation)
            bottomNavigation.setOnTabSelectedListener(this)
            bottomNavigation.setActiveTab(getCurrentTab())
        } catch (e: Exception) {
            // Will be handled by subclass if findViewById fails
            // This allows subclasses to override navigation setup
        }
    }
    
    protected fun setupBottomNavigationManually(navigation: CustomBottomNavigation) {
        bottomNavigation = navigation
        bottomNavigation.setOnTabSelectedListener(this)
        bottomNavigation.setActiveTab(getCurrentTab())
    }
    
    private fun setupScrollBehavior() {
        // This method can be overridden by subclasses to implement scroll behavior
        // For now, we'll implement a simple version that activities can use
    }
    
    // Method that activities can call to setup scroll behavior for specific views
    protected fun setupScrollBehaviorForView(scrollableView: View) {
        // Content scrolls behind the floating capsule. Reserve bottom padding so the
        // last item clears the capsule + gesture-nav inset. Apply once via tag.
        applyFloatingNavBottomPadding(scrollableView)

        // Read current scroll position from whichever scrollable variant we got.
        val readScrollY: () -> Int = when (scrollableView) {
            is NestedScrollView -> { { scrollableView.scrollY } }
            is ScrollView -> { { scrollableView.scrollY } }
            is RecyclerView -> { { scrollableView.computeVerticalScrollOffset() } }
            else -> return
        }

        // Initial state — covers the case where a fragment is restored at a non-zero
        // scroll position (rotation, fragment switch back to a previously-scrolled tab).
        applyScrollPressure(readScrollY())

        // Additive listener: fragments may already own the View's setOnScrollChangeListener
        // for their own visual effects (ProfileFragment.setupFrostedToolbar). Hooking the
        // viewTreeObserver coexists with that instead of overwriting it.
        scrollableView.viewTreeObserver.addOnScrollChangedListener {
            applyScrollPressure(readScrollY())
        }
    }

    /**
     * Map current scroll offset to pressure [0..1] and drive the floating capsule's
     * elevation plus the bottom-edge fade scrim. Dead zone of 16dp + 120dp ramp with
     * ease-out cubic — mirrors the frosted top-toolbar curve in ProfileFragment so
     * the two effects feel like one motion system.
     */
    private fun applyScrollPressure(scrollY: Int) {
        val density = resources.displayMetrics.density
        val deadZonePx = 16f * density
        val rangePx = 120f * density
        val active = ((scrollY - deadZonePx) / rangePx).coerceIn(0f, 1f)
        val t = 1f - active
        val eased = 1f - (t * t * t)

        if (this::bottomNavigation.isInitialized) {
            bottomNavigation.setScrollPressure(eased)
        }
    }
    
    private fun applyFloatingNavBottomPadding(view: View) {
        if (view.getTag(R.id.tag_floating_nav_padding_applied) == true) return
        view.setTag(R.id.tag_floating_nav_padding_applied, true)

        val density = resources.displayMetrics.density
        // Capsule content (~52dp) + capsule bottom margin (16dp) + gesture-nav inset.
        val navInset = ViewCompat.getRootWindowInsets(window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val extra = (68 * density).toInt() + navInset

        (view as? ViewGroup)?.clipToPadding = false
        view.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            view.paddingBottom + extra
        )
    }

    override fun onTabSelected(tabId: Int) {
        val isMainContainer = this is MainContainerActivity
        if (tabId == getCurrentTab() && isMainContainer) {
            return // Already on this tab in the main container
        }

        navigateToMainContainer(tabId)
    }
    
    private fun navigateToMainContainer(tabId: Int) {
        val intent = Intent(this, MainContainerActivity::class.java)
        intent.putExtra(MainContainerActivity.EXTRA_TARGET_TAB, tabId)

        // Preserve user data if needed
        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        val userName = sharedPref.getString("user_name", "User")
        intent.putExtra("USER_NAME", userName)
        
        startActivity(intent)
        overridePendingTransition(0, 0) // No animation for tab navigation
        finish()
    }
    
    protected fun showToast(message: String) {
        val parentView = findViewById<android.view.ViewGroup>(R.id.fragment_container)
            ?: findViewById<android.view.ViewGroup>(android.R.id.content)
            
        if (parentView != null) {
            com.gridee.parking.utils.NotificationHelper.showInfoNoIcon(
                parent = parentView,
                title = "Notification",
                message = message,
                duration = 3000L
            )
        } else {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    // Method to manually control navigation visibility
    protected fun hideBottomNavigation() {
        bottomNavigation.hideBottomNavigation()
    }
    
    protected fun showBottomNavigation() {
        bottomNavigation.showBottomNavigation()
    }
    
    abstract fun setupUI()
    
    override fun onDestroy() {
        inAppUpdateController?.onDestroy()
        inAppUpdateController = null
        super.onDestroy()
        _binding = null
    }
}
