package com.gridee.parking.ui.utils

import android.app.Activity
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
        // Enable edge-to-edge with proper system bar styles
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        
        // Additional configuration
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        
        // Disable navigation bar contrast enforcement if available (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
    }
    
    /**
     * Legacy setup for View-based activities
     */
    fun setupEdgeToEdgeLegacy(activity: Activity) {
        val window = activity.window
        
        // For API 30+ use the new WindowInsets API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Disable contrast enforcement
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
            
            // Set transparent bars
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            // Configure light/dark icons
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
            
        } else {
            // For older versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
            
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
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
            color = Color.Transparent, // Always use transparent
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
