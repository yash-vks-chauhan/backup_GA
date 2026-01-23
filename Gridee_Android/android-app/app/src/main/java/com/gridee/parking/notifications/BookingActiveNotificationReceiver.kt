package com.gridee.parking.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BookingActiveNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getStringExtra(BookingActiveNotificationManager.EXTRA_BOOKING_ID).orEmpty()
        val endTimeMillis = intent.getLongExtra(BookingActiveNotificationManager.EXTRA_END_TIME_MS, 0L)

        when (intent.action) {
            BookingActiveNotificationManager.ACTION_UPDATE_TIMER -> {
                if (bookingId.isNotBlank() && endTimeMillis > 0L) {
                    BookingActiveNotificationManager.handleTimerUpdate(context, bookingId, endTimeMillis)
                }
            }
            BookingActiveNotificationManager.ACTION_CANCEL_TIMER -> {
                if (bookingId.isNotBlank()) {
                    BookingActiveNotificationManager.handleTimerCancel(context, bookingId)
                }
            }
        }
    }
}
