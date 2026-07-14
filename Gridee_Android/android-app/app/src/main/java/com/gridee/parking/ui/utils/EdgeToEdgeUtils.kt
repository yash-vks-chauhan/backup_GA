package com.gridee.parking.ui.utils

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.utils.ThemeManager
import com.google.accompanist.systemuicontroller.SystemUiController
import com.google.accompanist.systemuicontroller.rememberSystemUiController

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
            isDarkMode = isDarkMode,
            navigationBarColor = AndroidColor.TRANSPARENT
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            configureSystemBars(
                window = window,
                isDarkMode = isDarkMode,
                navigationBarColor = AndroidColor.TRANSPARENT
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
        }
    }

    private fun configureSystemBars(
        window: Window,
        isDarkMode: Boolean,
        navigationBarColor: Int
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = navigationBarColor

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkMode
        controller.isAppearanceLightNavigationBars = !isDarkMode
    }
}

/**
 * Composable that configures system UI for edge-to-edge display.
 * Use this in your main Composable to ensure proper system bar styling.
 */
@Composable
fun ConfigureEdgeToEdgeSystemUI(
    navigationBarColor: Color = MaterialTheme.colorScheme.surface,
    statusBarColor: Color = Color.Transparent,
    darkIcons: Boolean = !isSystemInDarkTheme()
) {
    val systemUiController = rememberSystemUiController()
    
    LaunchedEffect(navigationBarColor, statusBarColor, darkIcons) {
        // Configure navigation bar - this is key to preventing gray areas
        systemUiController.setNavigationBarColor(
            color = navigationBarColor,
            darkIcons = darkIcons,
            navigationBarContrastEnforced = false // Critical: disable contrast enforcement
        )
        
        // Configure status bar
        systemUiController.setStatusBarColor(
            color = statusBarColor,
            darkIcons = darkIcons
        )
    }
}

/**
 * Extension function to easily configure edge-to-edge for any ComponentActivity
 */
fun ComponentActivity.configureEdgeToEdge() {
    EdgeToEdgeUtils.setupEdgeToEdge(this)
}

/**
 * Data class representing system UI configuration
 */
data class SystemUIConfig(
    val statusBarColor: Color = Color.Transparent,
    val navigationBarColor: Color = Color.Transparent,
    val darkStatusBarIcons: Boolean = true,
    val darkNavigationBarIcons: Boolean = true,
    val navigationBarContrastEnforced: Boolean = false
)

/**
 * Apply system UI configuration using SystemUiController
 */
@Composable
fun ApplySystemUIConfig(
    config: SystemUIConfig = SystemUIConfig(),
    systemUiController: SystemUiController = rememberSystemUiController()
) {
    LaunchedEffect(config) {
        systemUiController.setStatusBarColor(
            color = config.statusBarColor,
            darkIcons = config.darkStatusBarIcons
        )
        
        systemUiController.setNavigationBarColor(
            color = config.navigationBarColor,
            darkIcons = config.darkNavigationBarIcons,
            navigationBarContrastEnforced = config.navigationBarContrastEnforced
        )
    }
}
