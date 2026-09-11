package com.gridee.parking.ui.bookings

import android.content.Context

/**
 * Whether opening the bookings tab is likely to land on a booking.
 *
 * A skeleton is a promise: it says "something is coming, hold this shape". Showing one to a
 * user with no booking is a promise the screen then breaks — a card-shaped ghost that resolves
 * into "No active bookings" is worse than the blank moment it replaced.
 *
 * The tab cannot answer that question on its own at cold start. BookingRepository's cache is a
 * TtlSingleFlightCache, which lives in memory only, so on a fresh process it knows nothing
 * until the network answers — which is exactly the window the skeleton exists to cover.
 *
 * So the answer is remembered from last time instead. One value per user, written on every
 * render, read once on the first paint. It records WHICH state as well as whether, because the
 * Booked and Live cards sit at different points on the rail and hold different heights — a
 * skeleton drawn for the wrong one drops ~50 dp when the real card lands.
 *
 * It can be wrong — a booking that ended while the app was closed shows a skeleton and then the
 * empty state — but only in that one direction and only once, and it is right for the case that
 * matters: a driver opening the app to check on a booking they know they have.
 */
object BookingsSkeletonHint {

    private const val PREFS = "bookings_skeleton_hint"
    private const val KEY_PREFIX = "expected_state_"

    /** What the tab expects to draw, and therefore which silhouette the skeleton holds. */
    enum class Expected { NOTHING, BOOKED, LIVE }

    fun expected(context: Context, userId: String): Expected {
        if (userId.isBlank()) return Expected.NOTHING
        val stored = prefs(context).getString(KEY_PREFIX + userId, null) ?: return Expected.NOTHING
        return runCatching { Expected.valueOf(stored) }.getOrDefault(Expected.NOTHING)
    }

    fun remember(context: Context, userId: String, expected: Expected) {
        if (userId.isBlank()) return
        val key = KEY_PREFIX + userId
        val store = prefs(context)
        // Written on every render, so guard the no-op write: this runs on the main thread and
        // apply() still hands work to a background writer each time it is called.
        if (store.getString(key, null) == expected.name) return
        store.edit().putString(key, expected.name).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
