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
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.core.content.res.ResourcesCompat
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import com.gridee.parking.R

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
    }

    private var onTabSelectedListener: OnTabSelectedListener? = null
    private var currentTab = TAB_HOME

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

    // Active background views
    private lateinit var homeActiveBg: View
    private lateinit var bookingsActiveBg: View
    private lateinit var walletActiveBg: View
    private lateinit var profileActiveBg: View
    
    // Animation properties
    private var currentIndicatorAnimation: SpringAnimation? = null
    private var isHidden = false
    
    // Haptic feedback
    private val vibrator: Vibrator? by lazy { context.getSystemService<Vibrator>() }
    
    // Cached fonts to prevent reloading and crashes
    private var typefaceBold: Typeface? = null
    private var typefaceMedium: Typeface? = null

    // Animation durations and properties
    private val scaleAnimationDuration = 150L
    private val bounceAnimationDuration = 200L

    init {
        LayoutInflater.from(context).inflate(R.layout.custom_bottom_navigation, this, true)
        
        // Enable hardware acceleration for premium performance
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
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
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Force the view to extend to the very bottom edge
            view.setPadding(0, 0, 0, 0)
            
            // Apply padding only to the content container
            val navContainer = findViewById<LinearLayout>(R.id.nav_container)
            navContainer?.let { container ->
                // Reduce top padding and optimize bottom padding
                val bottomPadding = Math.max(navigationBars.bottom, 20) // Minimum 20dp for gesture area
                val topPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics).toInt()
                
                container.setPadding(
                    container.paddingLeft,
                    topPadding, // Compact top padding (10dp)
                    container.paddingRight,
                    bottomPadding + 4 // Minimal bottom buffer
                )
            }
            
            // Reduce minimum height to make navigation bar more compact
            view.minimumHeight = 56 + Math.max(navigationBars.bottom, 24)
            
            // Force layout to extend to bottom
            val layoutParams = view.layoutParams
            if (layoutParams != null) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                view.layoutParams = layoutParams
            }
            
            insets
        }
    }

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
        
        // Active background views
        homeActiveBg = findViewById(R.id.home_active_bg)
        bookingsActiveBg = findViewById(R.id.bookings_active_bg)
        walletActiveBg = findViewById(R.id.wallet_active_bg)
        profileActiveBg = findViewById(R.id.profile_active_bg)
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
        if (currentTab != tabId) {
            // Add haptic feedback for tab selection
            performHapticFeedback()
            
            setActiveTab(tabId)
            onTabSelectedListener?.onTabSelected(tabId)
        }
    }

    fun setActiveTab(tabId: Int) {
        currentTab = tabId
        
        // Reset all tabs to inactive state
        resetAllTabs()
        
        // Activate selected tab with elegant transitions
        when (tabId) {
            TAB_HOME -> activateHomeTab()
            TAB_BOOKINGS -> activateBookingsTab()
            TAB_WALLET -> activateWalletTab()
            TAB_PROFILE -> activateProfileTab()
        }
    }

    private fun resetAllTabs() {
        deactivateTab(homeActiveBg, ivHome, tvHome)
        deactivateTab(bookingsActiveBg, ivBookings, tvBookings)
        deactivateTab(walletActiveBg, ivWallet, tvWallet)
        deactivateTab(profileActiveBg, ivProfile, tvProfile)
    }

    private fun deactivateAllTabs() {
        deactivateTab(homeActiveBg, ivHome, tvHome)
        deactivateTab(bookingsActiveBg, ivBookings, tvBookings)
        deactivateTab(walletActiveBg, ivWallet, tvWallet)
        deactivateTab(profileActiveBg, ivProfile, tvProfile)
    }

    private fun activateHomeTab() {
        activateTab(tabHome, homeActiveBg, ivHome, tvHome)
    }

    private fun activateBookingsTab() {
        activateTab(tabBookings, bookingsActiveBg, ivBookings, tvBookings)
    }

    private fun activateWalletTab() {
        activateTab(tabWallet, walletActiveBg, ivWallet, tvWallet)
    }

    private fun activateProfileTab() {
        activateTab(tabProfile, profileActiveBg, ivProfile, tvProfile)
    }

    private fun activateTab(parentTab: FrameLayout, backgroundView: View, imageView: ImageView, textView: TextView) {
        // Show background pill with Apple-style Spring Physics
        // "Alive" state: Start physically smaller (0.8x) to allow for the expansion
        backgroundView.visibility = View.VISIBLE
        backgroundView.alpha = 0f
        backgroundView.scaleX = 0.8f
        backgroundView.scaleY = 0.8f
        
        // Background: "Hydraulic" smooth fill (Slow & No Bounce)
        // It grows politely behind the icon
        applySpring(backgroundView, SpringAnimation.ALPHA, 1f, SpringForce.STIFFNESS_VERY_LOW, SpringForce.DAMPING_RATIO_NO_BOUNCY)
        applySpring(backgroundView, SpringAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_VERY_LOW, SpringForce.DAMPING_RATIO_NO_BOUNCY)
        applySpring(backgroundView, SpringAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_VERY_LOW, SpringForce.DAMPING_RATIO_NO_BOUNCY)
        
        // Switch to filled icons and use Black tint (contrast against Light Grey pill)
        val activeColor = ContextCompat.getColor(context, R.color.brand_primary)
        when (imageView) {
            ivHome -> {
                imageView.setImageResource(R.drawable.ic_home_filled)
                imageView.imageTintList = ColorStateList.valueOf(activeColor)
            }
            ivBookings -> {
                imageView.setImageResource(R.drawable.ic_bookings_filled)
                imageView.imageTintList = ColorStateList.valueOf(activeColor)
            }
            ivWallet -> {
                imageView.setImageResource(R.drawable.ic_wallet_filled)
                imageView.imageTintList = ColorStateList.valueOf(activeColor)
            }
            ivProfile -> {
                imageView.setImageResource(R.drawable.ic_profile_nav_filled)
                imageView.imageTintList = ColorStateList.valueOf(activeColor)
            }
        }
        
        
        // Text is black (active) and Bold
        textView.setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
        textView.typeface = typefaceBold ?: Typeface.DEFAULT_BOLD
        textView.visibility = View.VISIBLE
        
        // Add smooth bounce animation
        animateTabActivation(imageView, textView)
    }
    
    private fun deactivateTab(backgroundView: View, imageView: ImageView, textView: TextView) {
        // Hide background with physics
        applySpring(backgroundView, SpringAnimation.ALPHA, 0f)
        applySpring(backgroundView, SpringAnimation.SCALE_X, 0.9f)
        applySpring(backgroundView, SpringAnimation.SCALE_Y, 0.9f)
        
        // Switch to outline icons and use Gray color
        val inactiveColor = ContextCompat.getColor(context, R.color.charcoal_black)
        when (imageView) {
            ivHome -> {
                imageView.setImageResource(R.drawable.ic_home)
                imageView.imageTintList = ColorStateList.valueOf(inactiveColor)
            }
            ivBookings -> {
                imageView.setImageResource(R.drawable.ic_bookings)
                imageView.imageTintList = ColorStateList.valueOf(inactiveColor)
            }
            ivWallet -> {
                imageView.setImageResource(R.drawable.ic_wallet)
                imageView.imageTintList = ColorStateList.valueOf(inactiveColor)
            }
            ivProfile -> {
                imageView.setImageResource(R.drawable.ic_profile_nav)
                imageView.imageTintList = ColorStateList.valueOf(inactiveColor)
            }
        }
        
        
        textView.setTextColor(inactiveColor)
        textView.typeface = typefaceMedium ?: Typeface.DEFAULT
        textView.visibility = View.VISIBLE
        
        // Add smooth deactivation animation
        animateTabDeactivation(imageView, textView)
    }
    
    private fun getTabForIcon(imageView: ImageView): FrameLayout {
        // Hierarchy: ImageView -> FrameLayout (Pill) -> LinearLayout -> FrameLayout (Tab)
        // Parent: Pill
        // Parent.Parent: LinearLayout
        // Parent.Parent.Parent: Tab
        return imageView.parent.parent.parent as FrameLayout
    }

    private fun animateTabActivation(imageView: ImageView, textView: TextView) {
        // Icon: Stronger "Heartbeat" Pop
        // Start smaller (0.7f) for more dramatic range -> Pop to 1.15x -> Settle at 1.0f
        imageView.scaleX = 0.7f
        imageView.scaleY = 0.7f
        imageView.alpha = 1.0f
        
        applySpring(imageView, SpringAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
        applySpring(imageView, SpringAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
        
        // Text: "Sympathetic Resonance"
        // Instead of being flat, the text now has a tiny, subtle bounce (High stiffness, low bounce)
        // This makes it feel connected to the icon's energy, not dead.
        textView.scaleX = 0.85f
        textView.scaleY = 0.85f
        textView.alpha = 1.0f
        
        applySpring(textView, SpringAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, 0.7f) // Custom "Low Bounce"
        applySpring(textView, SpringAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, 0.7f)
    }
    
    private fun animateTabDeactivation(imageView: ImageView, textView: TextView) {
        // Relax back to natural state
        applySpring(imageView, SpringAnimation.SCALE_X, 1f)
        applySpring(imageView, SpringAnimation.SCALE_Y, 1f)
        applySpring(imageView, SpringAnimation.ALPHA, 1f)
        
        applySpring(textView, SpringAnimation.SCALE_X, 1f)
        applySpring(textView, SpringAnimation.SCALE_Y, 1f) 
        applySpring(textView, SpringAnimation.ALPHA, 1f)
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
}
