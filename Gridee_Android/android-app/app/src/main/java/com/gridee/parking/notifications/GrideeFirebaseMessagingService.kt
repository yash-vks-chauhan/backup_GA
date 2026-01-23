package com.gridee.parking.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.FirebaseMessagingService
import com.gridee.parking.R
import com.gridee.parking.ui.activities.TransactionHistoryActivity
import com.gridee.parking.ui.auth.LoginActivity
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.BackendTimestampParser
import com.gridee.parking.utils.NotificationTokenManager
import java.util.Locale

class GrideeFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        NotificationTokenManager.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val type = data["type"]?.trim()?.uppercase(Locale.getDefault()).orEmpty()

        when (type) {
            "REFUND" -> {
                handleRefund(data)
                return
            }
            "BOOKING_EXTENDED" -> {
                handleBookingExtended(data)
                return
            }
            "BOOKING_CHECKED_IN", "BOOKING_ACTIVE" -> {
                handleBookingCheckedIn(data)
                return
            }
            "BOOKING_CANCELLED", "BOOKING_CANCELED", "BOOKING_CANCEL",
            "BOOKING_CHECKED_OUT", "BOOKING_CHECKOUT", "BOOKING_COMPLETED",
            "BOOKING_ENDED", "BOOKING_EXPIRED" -> {
                handleBookingEnded(data, remoteMessage)
                return
            }
        }

        val notification = remoteMessage.notification
        val title = data["title"] ?: notification?.title
        val body = data["body"] ?: notification?.body
        if (title.isNullOrBlank() && body.isNullOrBlank()) return

        showNotification(
            title = title?.ifBlank { getString(R.string.app_name) } ?: getString(R.string.app_name),
            body = body.orEmpty(),
            intent = buildDefaultIntent(),
            notificationId = ("generic_${System.currentTimeMillis()}").hashCode()
        )
    }

    private fun buildDefaultIntent(): Intent {
        return if (AuthSession.isAuthenticated(this)) {
            Intent(this, MainContainerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    private fun handleRefund(data: Map<String, String>) {
        val amount = data["amount"] ?: "0"
        val bookingId = data["bookingId"].orEmpty()

        val intent = buildRefundIntent(bookingId)
        showNotification(
            title = "Refund processed",
            body = "₹$amount refunded to your wallet.",
            intent = intent,
            notificationId = ("refund_$bookingId").hashCode()
        )
    }

    private fun handleBookingExtended(data: Map<String, String>) {
        val bookingId = data["bookingId"].orEmpty()
        val extra = data["additionalCharge"] ?: "0"
        val extraAmount = extra.toDoubleOrNull() ?: 0.0

        val body = if (extraAmount > 0.0) {
            "Extra ₹$extra charged. New checkout time updated."
        } else {
            "Extension confirmed."
        }

        val intent = buildBookingIntent(bookingId)
        showNotification(
            title = "Booking extended",
            body = body,
            intent = intent,
            notificationId = ("booking_extended_$bookingId").hashCode()
        )

        val endTimeMillis = resolveBookingEndTime(data)
        if (endTimeMillis != null) {
            BookingActiveNotificationManager.showOrUpdate(this, bookingId, endTimeMillis)
        }
    }

    private fun handleBookingCheckedIn(data: Map<String, String>) {
        val bookingId = data["bookingId"].orEmpty()
        val endTimeMillis = resolveBookingEndTime(data) ?: return
        BookingActiveNotificationManager.showOrUpdate(this, bookingId, endTimeMillis)
    }

    private fun handleBookingEnded(data: Map<String, String>, remoteMessage: RemoteMessage) {
        val bookingId = data["bookingId"].orEmpty()
        val endTimeMillis = resolveBookingEndTime(data)

        if (bookingId.isNotBlank()) {
            BookingActiveNotificationManager.cancel(this, bookingId)
        } else if (endTimeMillis != null) {
            BookingActiveNotificationManager.cancel(this, "active_$endTimeMillis")
        }

        val notification = remoteMessage.notification
        val title = data["title"] ?: notification?.title
        val body = data["body"] ?: notification?.body
        if (title.isNullOrBlank() && body.isNullOrBlank()) return

        showNotification(
            title = title?.ifBlank { getString(R.string.app_name) } ?: getString(R.string.app_name),
            body = body.orEmpty(),
            intent = buildBookingIntent(bookingId),
            notificationId = ("booking_end_${bookingId.ifBlank { System.currentTimeMillis().toString() }}").hashCode()
        )
    }

    private fun resolveBookingEndTime(data: Map<String, String>): Long? {
        val endTimeRaw = data["checkOutTime"]
            ?: data["checkOutTimeMs"]
            ?: data["checkoutTime"]
            ?: data["checkoutTimeMs"]
            ?: data["activeUntilMs"]
            ?: data["endTimeMs"]

        if (!endTimeRaw.isNullOrBlank()) {
            val parsed = BackendTimestampParser.parseToMillis(endTimeRaw, 0L)
            if (parsed > 0L) return parsed
        }

        val remainingMsRaw = data["remainingMs"] ?: data["remainingMillis"]
        if (!remainingMsRaw.isNullOrBlank()) {
            val remainingMs = remainingMsRaw.toLongOrNull()
            if (remainingMs != null && remainingMs > 0L) {
                return System.currentTimeMillis() + remainingMs
            }
        }

        val remainingSecRaw = data["remainingSeconds"] ?: data["remainingSec"]
        if (!remainingSecRaw.isNullOrBlank()) {
            val remainingSec = remainingSecRaw.toLongOrNull()
            if (remainingSec != null && remainingSec > 0L) {
                return System.currentTimeMillis() + (remainingSec * 1000L)
            }
        }

        return null
    }

    private fun buildRefundIntent(bookingId: String): Intent {
        return if (AuthSession.isAuthenticated(this)) {
            Intent(this, TransactionHistoryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (bookingId.isNotBlank()) {
                    putExtra("bookingId", bookingId)
                }
            }
        } else {
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    private fun buildBookingIntent(bookingId: String): Intent {
        return if (AuthSession.isAuthenticated(this)) {
            Intent(this, MainContainerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainContainerActivity.EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_BOOKINGS)
                putExtra(MainContainerActivity.EXTRA_SHOW_PENDING, false)
                if (bookingId.isNotBlank()) {
                    putExtra(MainContainerActivity.EXTRA_HIGHLIGHT_BOOKING_ID, bookingId)
                    putExtra(MainContainerActivity.EXTRA_OPEN_BOOKING_ID, bookingId)
                }
            }
        } else {
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        intent: Intent,
        notificationId: Int
    ) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        NotificationChannels.ensureDefaultChannel(this)

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }
}
