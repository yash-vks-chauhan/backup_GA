package com.gridee.parking.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLocaleManager {
    private const val PREFS_NAME = "gridee_prefs"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val DEFAULT_LANGUAGE = "en"

    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_LANGUAGE
    }

    fun applySavedLocale(context: Context) {
        applyLocale(getSavedLanguage(context))
    }

    fun setLocale(context: Context, languageCode: String) {
        val normalizedCode = languageCode.trim().ifBlank { DEFAULT_LANGUAGE }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, normalizedCode)
            .apply()
        applyLocale(normalizedCode)
    }

    private fun applyLocale(languageCode: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    }
}
