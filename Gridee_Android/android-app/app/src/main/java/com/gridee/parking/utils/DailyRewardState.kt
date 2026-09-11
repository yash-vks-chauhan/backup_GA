package com.gridee.parking.utils

import android.annotation.SuppressLint
import android.content.Context
import java.util.Calendar

/**
 * Tracks the user's daily reward: whether they have engaged with it today, and how many
 * rewards they have actually completed.
 *
 * The "seen" half drives the small gold "claim" dot on the home reward coin: the dot appears
 * once per local calendar day and stays until the user opens the reward sheet, then hides
 * until the next day. It is an attention nudge and nothing more.
 *
 * The "claimed" half is the real limit. It was previously absent entirely — the sheet could be
 * reopened and rewatched without bound — and it now caps completed rewards at
 * [DAILY_REWARD_CAP] per local day.
 *
 * Backed by the shared "gridee_prefs" store so it survives process death. Client-side state is
 * defeatable by a determined user clearing app data; this is a price and pacing control, not a
 * security boundary, and it is not the place to enforce one.
 */
object DailyRewardState {

    private const val PREFS = "gridee_prefs"
    private const val KEY_LAST_SEEN_DAY = "reward_daily_seen_day"
    private const val KEY_INTRO_PLAYED = "reward_intro_played"
    private const val KEY_CLAIMED_DAY = "reward_claimed_day"
    private const val KEY_CLAIMED_COUNT = "reward_claimed_count"

    /**
     * How many rewards one user may complete per local day.
     *
     * This is a price lever before it is a spend lever. Advertisers frequency-cap their own
     * campaigns, so the first rewarded impression a user sees in a day draws the full bidder set
     * and the eighth draws only whoever has not capped out yet. Uncapped heavy users therefore
     * generate a long tail of near-worthless impressions that drag the placement's average eCPM
     * down. Fewer, scarcer impressions are worth more per thousand than many cheap ones.
     *
     * Counted on completed rewards rather than on impressions, deliberately: a user who opens an
     * ad and backs out has not been paid, and burning their daily allowance for it would be a
     * punishment for a misfire. Claim rate is high enough that the two counts stay close.
     */
    const val DAILY_REWARD_CAP = 3

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

    /**
     * Rewards completed today. Reads as 0 on any day other than the one last written, so the
     * count rolls over at local midnight without needing a scheduled reset.
     */
    fun rewardsClaimedToday(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_CLAIMED_DAY, -1) != todayKey()) return 0
        return prefs.getInt(KEY_CLAIMED_COUNT, 0).coerceAtLeast(0)
    }

    /** Rewards still available today, never negative. */
    fun rewardsRemainingToday(context: Context): Int =
        (DAILY_REWARD_CAP - rewardsClaimedToday(context)).coerceAtLeast(0)

    /** True once the user has completed [DAILY_REWARD_CAP] rewards today. */
    fun hasReachedDailyCap(context: Context): Boolean =
        rewardsClaimedToday(context) >= DAILY_REWARD_CAP

    /**
     * Record one completed reward. Committed synchronously: this is called on the reward
     * callback, immediately before a wallet mutation and a sheet dismissal, and an `apply()`
     * that loses the race with process death would hand back a free extra reward.
     */
    @SuppressLint("ApplySharedPref")
    fun recordRewardClaimed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = todayKey()
        val current = if (prefs.getInt(KEY_CLAIMED_DAY, -1) == today) {
            prefs.getInt(KEY_CLAIMED_COUNT, 0).coerceAtLeast(0)
        } else {
            0
        }
        prefs.edit()
            .putInt(KEY_CLAIMED_DAY, today)
            .putInt(KEY_CLAIMED_COUNT, current + 1)
            .commit()
    }
}
