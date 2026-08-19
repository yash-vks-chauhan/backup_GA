package com.gridee.parking.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Single source of truth for the app's language.
 *
 * The selection is persisted here rather than via AppCompat's `autoStoreLocales`
 * service, and re-applied from GrideeApplication.onCreate, so the chosen locale
 * is in force before the first activity inflates.
 */
object AppLocaleManager {
    private const val PREFS_NAME = "gridee_prefs"
    private const val KEY_APP_LANGUAGE = "app_language"
    const val DEFAULT_LANGUAGE = "en"

    /**
     * A language the app ships translations for.
     *
     * [nativeName] is the primary label in the selector — a speaker should be able
     * to find their own row without reading English. [endonymInitial] is the single
     * glyph shown in the row's script badge.
     */
    data class Language(
        val code: String,
        val nativeName: String,
        val englishName: String,
        val endonymInitial: String,
        val regionLabel: String
    )

    val SUPPORTED: List<Language> = listOf(
        Language("en", "English", "English", "A", "India"),
        Language("hi", "हिन्दी", "Hindi", "अ", "भारत"),
        Language("ta", "தமிழ்", "Tamil", "அ", "இந்தியா"),
        Language("te", "తెలుగు", "Telugu", "అ", "భారతదేశం"),
        Language("ml", "മലയാളം", "Malayalam", "അ", "ഇന്ത്യ"),
        Language("bn", "বাংলা", "Bengali", "অ", "ভারত")
    )

    fun languageFor(code: String): Language =
        SUPPORTED.firstOrNull { it.code == code.trim().lowercase() } ?: SUPPORTED.first()

    fun getSavedLanguage(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, DEFAULT_LANGUAGE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_LANGUAGE
        // Guard against a stale pref pointing at a language we no longer ship.
        return if (SUPPORTED.any { it.code == stored }) stored else DEFAULT_LANGUAGE
    }

    /**
     * The locale actually in force, which is not always our saved pref: on
     * Android 13+ the user can change it from Settings › App languages, and
     * AppCompat stores that itself. The framework value wins when present.
     */
    fun getEffectiveLanguage(context: Context): String {
        val fromDelegate = AppCompatDelegate.getApplicationLocales()
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
            ?.lowercase()
        return fromDelegate?.takeIf { code -> SUPPORTED.any { it.code == code } }
            ?: getSavedLanguage(context)
    }

    fun getSavedLanguageInfo(context: Context): Language = languageFor(getEffectiveLanguage(context))

    /**
     * Called on every app start. If the OS-level picker already set a language
     * we mirror it into our pref rather than overwriting the user's choice —
     * otherwise a change made in system Settings would be undone on next launch.
     */
    fun applySavedLocale(context: Context) {
        val current = AppCompatDelegate.getApplicationLocales()
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
            ?.lowercase()
            ?.takeIf { code -> SUPPORTED.any { it.code == code } }

        if (current != null) {
            persist(context, current)
            return
        }
        applyLocale(getSavedLanguage(context))
    }

    fun setLocale(context: Context, languageCode: String) {
        val normalizedCode = languageCode.trim().lowercase()
            .takeIf { code -> SUPPORTED.any { it.code == code } }
            ?: DEFAULT_LANGUAGE
        persist(context, normalizedCode)
        applyLocale(normalizedCode)
    }

    private fun persist(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, languageCode)
            .apply()
    }

    private fun applyLocale(languageCode: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    }
}
