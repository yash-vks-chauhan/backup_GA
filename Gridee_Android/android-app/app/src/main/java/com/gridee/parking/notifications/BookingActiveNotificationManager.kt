package com.gridee.parking.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gridee.parking.R
import com.gridee.parking.ui.auth.LoginActivity
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.AuthSession
import java.util.Date

object BookingActiveNotificationManager {
    const val ACTION_UPDATE_TIMER = "com.gridee.parking.action.BOOKING_TIMER_UPDATE"
    const val ACTION_CANCEL_TIMER = "com.gridee.parking.action.BOOKING_TIMER_CANCEL"
    const val EXTRA_BOOKING_ID = "extra_booking_id"
    const val EXTRA_END_TIME_MS = "extra_end_time_ms"

    private const val YELLOW_THRESHOLD_MS = 10 * 60 * 1000L
    private const val RED_THRESHOLD_MS = 5 * 60 * 1000L

    private const val REQUEST_YELLOW = 1
    private const val REQUEST_RED = 2
    private const val REQUEST_CANCEL = 3

    fun showOrUpdate(context: Context, bookingId: String, endTimeMillis: Long) {
        val safeBookingId = bookingId.ifBlank { "active_$endTimeMillis" }
        val now = System.currentTimeMillis()
        val remainingMs = endTimeMillis - now
        if (remainingMs <= 0L) {
            cancel(context, safeBookingId)
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        // Show standard notification directly without foreground service
        val notification = buildNotification(context, bookingId, endTimeMillis)
        val notificationId = notificationId(bookingId)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }

        scheduleThresholdUpdates(context, safeBookingId, endTimeMillis)
    }

    fun buildNotification(context: Context, bookingId: String, endTimeMillis: Long): android.app.Notification {
        val safeBookingId = bookingId.ifBlank { "active_$endTimeMillis" }
        val remainingMs = endTimeMillis - System.currentTimeMillis()
        
        NotificationChannels.ensureDefaultChannel(context)

        val endTimeLabel = DateFormat.getTimeFormat(context).format(Date(endTimeMillis))
        val contentText = when {
            remainingMs <= RED_THRESHOLD_MS -> "Ending soon"
            remainingMs <= YELLOW_THRESHOLD_MS -> "Last 10 mins remaining"
            else -> "Ends at $endTimeLabel"
        }

        val color = when {
            remainingMs <= RED_THRESHOLD_MS -> ContextCompat.getColor(context, R.color.red)
            remainingMs <= YELLOW_THRESHOLD_MS -> ContextCompat.getColor(context, R.color.orange)
            else -> ContextCompat.getColor(context, R.color.success_green)
        }

        val intent = buildBookingIntent(context, bookingId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            safeBookingId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Standard Notification using NotificationCompat for all versions
        // This avoids the need for special permissions required by the native Builder for Promoted traits
        return NotificationCompat.Builder(context, NotificationChannels.DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Active booking")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setWhen(endTimeMillis)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setChronometerCountDown(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(color)
            .setColorized(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun cancel(context: Context, bookingId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(bookingId))
        cancelScheduledUpdates(context, bookingId)
    }

    fun handleTimerUpdate(context: Context, bookingId: String, endTimeMillis: Long) {
        showOrUpdate(context, bookingId, endTimeMillis)
    }

    fun handleTimerCancel(context: Context, bookingId: String) {
        cancel(context, bookingId)
    }

    fun notificationId(bookingId: String): Int {
        return ("active_booking_$bookingId").hashCode()
    }

    private fun scheduleThresholdUpdates(context: Context, bookingId: String, endTimeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelScheduledUpdates(context, bookingId)

        val now = System.currentTimeMillis()
        scheduleAlarm(
            alarmManager,
            context,
            bookingId,
            endTimeMillis,
            endTimeMillis - YELLOW_THRESHOLD_MS,
            ACTION_UPDATE_TIMER,
            REQUEST_YELLOW,
            now
        )
        scheduleAlarm(
            alarmManager,
            context,
            bookingId,
            endTimeMillis,
            endTimeMillis - RED_THRESHOLD_MS,
            ACTION_UPDATE_TIMER,
            REQUEST_RED,
            now
        )
        scheduleAlarm(
            alarmManager,
            context,
            bookingId,
            endTimeMillis,
            endTimeMillis,
            ACTION_CANCEL_TIMER,
            REQUEST_CANCEL,
            now
        )
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        context: Context,
        bookingId: String,
        endTimeMillis: Long,
        triggerAtMillis: Long,
        action: String,
        requestOffset: Int,
        nowMillis: Long
    ) {
        if (triggerAtMillis <= nowMillis) return
        val pendingIntent = buildAlarmPendingIntent(
            context,
            bookingId,
            endTimeMillis,
            action,
            requestOffset
        )
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun cancelScheduledUpdates(context: Context, bookingId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            buildAlarmPendingIntent(context, bookingId, 0L, ACTION_UPDATE_TIMER, REQUEST_YELLOW),
            buildAlarmPendingIntent(context, bookingId, 0L, ACTION_UPDATE_TIMER, REQUEST_RED),
            buildAlarmPendingIntent(context, bookingId, 0L, ACTION_CANCEL_TIMER, REQUEST_CANCEL)
        ).forEach { alarmManager.cancel(it) }
    }

    private fun buildAlarmPendingIntent(
        context: Context,
        bookingId: String,
        endTimeMillis: Long,
        action: String,
        requestOffset: Int
    ): PendingIntent {
        val intent = Intent(context, BookingActiveNotificationReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_BOOKING_ID, bookingId)
            putExtra(EXTRA_END_TIME_MS, endTimeMillis)
        }
        val requestCode = bookingId.hashCode() + requestOffset
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildBookingIntent(context: Context, bookingId: String): Intent {
        return if (AuthSession.isAuthenticated(context)) {
            Intent(context, MainContainerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainContainerActivity.EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_BOOKINGS)
                if (bookingId.isNotBlank() && !bookingId.startsWith("active_")) {
                    putExtra(MainContainerActivity.EXTRA_HIGHLIGHT_BOOKING_ID, bookingId)
                    putExtra(MainContainerActivity.EXTRA_OPEN_BOOKING_ID, bookingId)
                }
            }
        } else {
            Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}
