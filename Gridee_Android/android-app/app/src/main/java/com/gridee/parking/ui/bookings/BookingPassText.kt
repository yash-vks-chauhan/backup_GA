package com.gridee.parking.ui.bookings

/**
 * The booking vocabulary, in one place.
 *
 * These strings used to live privately inside `BookingsAdapter`, which was fine while the
 * cards were the only thing that spoke them. The QR pass now shows the same state and the
 * same countdown, and a pass that says "Parked" while the card behind it says "LIVE" is a
 * bug the compiler cannot catch — so both read from here.
 */
object BookingPassText {

    /**
     * Big chunky remaining time: "1h 23m" / "12m" / "32s".
     *
     * Seconds only appear at minute-scale, so an hour-long session doesn't flicker, but a
     * session in its last minute doesn't look frozen at "1m" either.
     */
    fun remaining(remainingMillis: Long): String {
        val totalSeconds = remainingMillis.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    /**
     * Countdown to check-in: "Starts in 44 min" / "Starts in 2h 10m" / "Starts at 10:10 PM".
     *
     * [fallbackTime] is the formatted clock time to fall back on when the target is unknown
     * or further out than half a day, where a countdown stops being useful.
     */
    fun countdown(targetMillis: Long, fallbackTime: String): String {
        val safeFallback = fallbackTime.trim().ifEmpty { "--" }
        if (targetMillis <= 0L) return "Starts at $safeFallback"
        val diff = targetMillis - System.currentTimeMillis()
        return when {
            diff <= -60_000L -> "Started at $safeFallback"
            diff <= 0L -> "Starting now"
            diff < 60_000L -> "Starts in less than a minute"
            diff < 60L * 60_000L -> {
                val mins = (diff / 60_000L).toInt().coerceAtLeast(1)
                "Starts in $mins min"
            }
            diff < 12L * 60L * 60_000L -> {
                val hours = diff / (60L * 60_000L)
                val mins = (diff % (60L * 60_000L)) / 60_000L
                if (mins == 0L) "Starts in ${hours}h" else "Starts in ${hours}h ${mins}m"
            }
            else -> "Starts at $safeFallback"
        }
    }
}
