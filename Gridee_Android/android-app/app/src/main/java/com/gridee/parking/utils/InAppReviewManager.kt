package com.gridee.parking.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.android.play.core.review.ReviewManagerFactory

object InAppReviewManager {

    private const val PREFS_NAME = "gridee_in_app_review"
    private const val KEY_SUCCESSFUL_BOOKINGS = "successful_bookings"
    private const val KEY_LAST_REVIEW_REQUEST_AT = "last_review_request_at"
    private const val KEY_REVIEW_REQUEST_COUNT = "review_request_count"

    private const val MIN_SUCCESSFUL_BOOKINGS_BEFORE_PROMPT = 2
    private const val MAX_REVIEW_REQUESTS = 3
    private const val REVIEW_COOLDOWN_MS = 3L * 24L * 60L * 60L * 1000L

    /**
     * Silent, quota-respecting nudge fired at a natural high-satisfaction moment
     * (right after a confirmed booking). Google may show nothing — quota reached or
     * the user already reviewed — and that's expected, so there is no fallback here:
     * a booking should never bounce the user out to the Play Store.
     */
    fun onBookingConfirmed(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val successfulBookings = prefs.getInt(KEY_SUCCESSFUL_BOOKINGS, 0) + 1
        prefs.edit().putInt(KEY_SUCCESSFUL_BOOKINGS, successfulBookings).apply()

        if (successfulBookings < MIN_SUCCESSFUL_BOOKINGS_BEFORE_PROMPT) return
        if (!canRequestReview(activity)) return

        markReviewRequested(activity)
        launchInAppReview(activity, fallbackToPlayStore = false)
    }

    /**
     * On-demand trigger for an explicit "Review" tap (e.g. the reward sheet row).
     * Shows the in-app review card overlaid on the app whenever Google allows it.
     * If the flow can't even be *requested* (sideloaded build, no Play Services, no
     * network) we open the Play Store listing so the tap never dead-ends.
     *
     * Not rate-limited — the user explicitly asked to review. Note the quota caveat:
     * if Google suppresses the card (already reviewed / shown recently) the request
     * still "succeeds" and nothing visible happens. The API gives no signal for that
     * case, so we deliberately do NOT also open the Play Store then — guessing wrong
     * would risk popping the card *and* the store on the same tap.
     */
    fun requestReviewOnDemand(activity: Activity) {
        launchInAppReview(activity, fallbackToPlayStore = true)
    }

    private fun launchInAppReview(activity: Activity, fallbackToPlayStore: Boolean) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(activity, task.result)
                } else if (fallbackToPlayStore) {
                    openPlayStoreListing(activity)
                }
            }
    }

    /** Deep-link to the Play Store listing — the Play Store app first, browser as a
     *  last resort. Used as the on-demand fallback when the in-app flow is unavailable. */
    fun openPlayStoreListing(activity: Activity) {
        val pkg = activity.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        runCatching { activity.startActivity(market) }.onFailure {
            runCatching { activity.startActivity(web) }.onFailure {
                Toast.makeText(activity, "Unable to open Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun canRequestReview(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requestCount = prefs.getInt(KEY_REVIEW_REQUEST_COUNT, 0)
        if (requestCount >= MAX_REVIEW_REQUESTS) return false

        val lastRequestedAt = prefs.getLong(KEY_LAST_REVIEW_REQUEST_AT, 0L)
        return lastRequestedAt == 0L ||
            System.currentTimeMillis() - lastRequestedAt >= REVIEW_COOLDOWN_MS
    }

    private fun markReviewRequested(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requestCount = prefs.getInt(KEY_REVIEW_REQUEST_COUNT, 0)
        prefs.edit()
            .putLong(KEY_LAST_REVIEW_REQUEST_AT, System.currentTimeMillis())
            .putInt(KEY_REVIEW_REQUEST_COUNT, requestCount + 1)
            .apply()
    }
}
