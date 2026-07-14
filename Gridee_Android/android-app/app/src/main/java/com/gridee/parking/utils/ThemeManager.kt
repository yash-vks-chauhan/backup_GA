package com.gridee.parking.utils

import android.content.Context
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFS_NAME = "gridee_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SYSTEM = "system"
    
    // Eclipse Transition Support
    var transitionBitmap: Bitmap? = null
    var transitionCenter: IntArray? = null
    var transitionThemeLabel: String? = null
    var transitionOldStatusBarColor: Int? = null
    var transitionOldNavigationBarColor: Int? = null
    var transitionOldIsDark: Boolean? = null

    fun applySavedTheme(context: Context) {
        val mode = getSavedThemeMode(context)
        applyTheme(mode)
    }

    fun applyTheme(mode: String) {
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            MODE_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun saveThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }

    fun getSavedThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, MODE_LIGHT) ?: MODE_LIGHT
    }

    fun isDarkMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
