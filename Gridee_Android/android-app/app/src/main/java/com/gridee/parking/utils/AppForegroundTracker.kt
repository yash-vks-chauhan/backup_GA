package com.gridee.parking.utils

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local generation for genuine foreground entries.
 *
 * Activity callbacks alone cannot distinguish returning from the background from returning from
 * an AdMob/Cashfree activity. The application increments this only when its started-activity count
 * moves from zero to one, so screens can refresh once per real foreground without making ad close
 * callbacks an accidental backend trigger.
 */
object AppForegroundTracker {
    private val generation = AtomicLong(0L)

    fun markForeground(): Long = generation.incrementAndGet()

    fun currentGeneration(): Long = generation.get()
}
