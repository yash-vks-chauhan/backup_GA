package com.gridee.parking.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val DEFAULT_CHANNEL_ID = "gridee_updates"

    fun ensureDefaultChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(DEFAULT_CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            DEFAULT_CHANNEL_ID,
            "Gridee updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Booking and wallet notifications"
        }
        manager.createNotificationChannel(channel)
    }
}
