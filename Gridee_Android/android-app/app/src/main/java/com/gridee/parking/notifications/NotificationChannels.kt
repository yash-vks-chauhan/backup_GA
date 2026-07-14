package com.gridee.parking.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val DEFAULT_CHANNEL_ID = "gridee_live_updates_v2"

    fun ensureDefaultChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(DEFAULT_CHANNEL_ID)
        if (existing != null) {
            // Ensure importance is set correctly even if it exists
            if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                existing.importance = NotificationManager.IMPORTANCE_HIGH
                manager.createNotificationChannel(existing)
            }
            return
        }

        val channel = NotificationChannel(
            DEFAULT_CHANNEL_ID,
            "Booking Live Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time updates for your active bookings"
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}
