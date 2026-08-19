package com.gridee.parking.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gridee.parking.GrideeApplication
import com.gridee.parking.utils.AdMobManager

/** Debug-only ADB hook for previewing the booking interstitial without changing a booking. */
class AdMobDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_INTERSTITIAL) return

        val activity = GrideeApplication.currentActivity ?: return
        val previewId = intent.getStringExtra(EXTRA_PREVIEW_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "adb-preview-${System.nanoTime()}"

        activity.window.decorView.post {
            AdMobManager.showBookingTransitionInterstitial(
                activity = activity,
                bookingId = previewId,
                targetStatus = "PREVIEW"
            )
        }
    }

    companion object {
        const val ACTION_SHOW_INTERSTITIAL = "com.gridee.parking.DEBUG_SHOW_INTERSTITIAL"
        const val EXTRA_PREVIEW_ID = "preview_id"
    }
}
