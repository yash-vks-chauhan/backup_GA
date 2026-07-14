package com.gridee.parking.utils

import android.content.Context
import java.util.Calendar

/**
 * Tracks whether the user has engaged with their daily reward today.
 *
 * Drives the small gold "claim" dot on the home reward coin: the dot appears
 * once per local calendar day and stays until the user opens the reward sheet,
 * then hides until the next day. The reward itself (an ad-backed wallet credit)
 * is repeatable — this is an honest once-a-day attention nudge, not a hard lock.
 *
 * Backed by the shared "gridee_prefs" store so it survives process death.
 */
object DailyRewardState {

    private const val PREFS = "gridee_prefs"
    private const val KEY_LAST_SEEN_DAY = "reward_daily_seen_day"
    private const val KEY_INTRO_PLAYED = "reward_intro_played"

    /** A stable key for the current local day (year * 1000 + dayOfYear). */
    private fun todayKey(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    /** True when the user has not yet opened today's reward — show the dot. */
    fun shouldShowDailyDot(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Default -1 ensures the dot shows on first ever launch.
        return prefs.getInt(KEY_LAST_SEEN_DAY, -1) != todayKey()
    }

    /** Record that the user opened the reward for today — clears the dot until tomorrow. */
    fun markSeenToday(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_SEEN_DAY, todayKey())
            .apply()
    }

    /** True until the one-time reward "mint reveal" intro has played. */
    fun shouldPlayIntro(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_INTRO_PLAYED, false)

    fun markIntroPlayed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INTRO_PLAYED, true)
            .apply()
    }
}
