package com.gridee.parking.utils

import android.content.Context

/**
 * Remembers the booking statuses this app last saw, per user.
 *
 * Check-in and check-out are performed by an operator, so the app only ever learns about them by
 * refreshing — and it usually refreshes for the first time *after* the transition already
 * happened, because the user's phone was in their pocket at the gate. Keeping the previous
 * statuses only in memory meant the first load of every launch recorded "active" or "completed"
 * as if it had always been that way, and the transition was never noticed.
 */
object BookingAdTransitionStore {

    private const val PREFS_NAME = "gridee_booking_ad_transitions"
    private const val KEY_SNAPSHOT_PREFIX = "snapshot_"
    private const val KEY_SAVED_AT_PREFIX = "saved_at_"

    /** A snapshot older than this describes a parking session nobody is still in. */
    private const val MAX_SNAPSHOT_AGE_MS = 7L * 24L * 60L * 60L * 1000L

    private const val MAX_ENTRIES = 100
    private const val ENTRY_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "\t"

    /** @return the last saved statuses, or null when there is no usable snapshot to compare against. */
    fun load(context: Context, userId: String): Map<String, String>? {
        val prefs = prefs(context)
        val savedAt = prefs.getLong(KEY_SAVED_AT_PREFIX + userId, 0L)
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > MAX_SNAPSHOT_AGE_MS) return null

        val raw = prefs.getString(KEY_SNAPSHOT_PREFIX + userId, null)?.takeIf { it.isNotBlank() }
            ?: return null

        val statuses = LinkedHashMap<String, String>()
        raw.split(ENTRY_SEPARATOR).forEach { entry ->
            val parts = entry.split(FIELD_SEPARATOR)
            if (parts.size != 2) return@forEach
            val id = parts[0]
            val status = parts[1]
            if (id.isNotBlank() && status.isNotBlank()) statuses[id] = status
        }
        return statuses.takeIf { it.isNotEmpty() }
    }

    fun save(context: Context, userId: String, statuses: Map<String, String>) {
        if (statuses.isEmpty()) return

        val serialized = statuses.entries
            .toList()
            .takeLast(MAX_ENTRIES)
            .joinToString(ENTRY_SEPARATOR) { "${it.key}$FIELD_SEPARATOR${it.value}" }

        prefs(context).edit()
            .putString(KEY_SNAPSHOT_PREFIX + userId, serialized)
            .putLong(KEY_SAVED_AT_PREFIX + userId, System.currentTimeMillis())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
