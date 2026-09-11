package com.gridee.parking.ui.utils

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.utils.ThemeManager

/**
 * Edge-to-edge utilities for fixing the gray gaps around the home gesture pill.
 * This handles proper window insets, system UI configuration, and navigation bar styling.
 */
object EdgeToEdgeUtils {
    
    /**
     * Configures the activity for proper edge-to-edge display.
     * Call this in your Activity's onCreate() method.
     */
    fun setupEdgeToEdge(activity: ComponentActivity) {
        val isDarkMode = ThemeManager.isDarkMode(activity)
        val transparentStyle = SystemBarStyle.auto(
            lightScrim = AndroidColor.TRANSPARENT,
            darkScrim = AndroidColor.TRANSPARENT
        ) { isDarkMode }

        activity.enableEdgeToEdge(
            statusBarStyle = transparentStyle,
            navigationBarStyle = transparentStyle
        )

        configureSystemBars(
            window = activity.window,
            isDarkMode = isDarkMode
        )
    }
    
    /**
     * Legacy setup for View-based activities
     */
    fun setupEdgeToEdgeLegacy(activity: Activity) {
        if (activity is ComponentActivity) {
            setupEdgeToEdge(activity)
            return
        }

        val window = activity.window
        val isDarkMode = ThemeManager.isDarkMode(activity)

        configureSystemBars(window = window, isDarkMode = isDarkMode)
    }

    private fun configureSystemBars(
        window: Window,
        isDarkMode: Boolean
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkMode
        controller.isAppearanceLightNavigationBars = !isDarkMode
    }
}

/**
 * Extension function to easily configure edge-to-edge for any ComponentActivity
 */
fun ComponentActivity.configureEdgeToEdge() {
    EdgeToEdgeUtils.setupEdgeToEdge(this)
}
