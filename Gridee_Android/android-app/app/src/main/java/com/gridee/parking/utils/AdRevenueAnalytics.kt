package com.gridee.parking.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.gridee.parking.ui.ads.AdMobNativeAdCardView
import com.gridee.parking.ui.ads.BookingQrNativeAdView

/** Lightweight, privacy-switch-aware telemetry for native ad placements. */
object AdRevenueAnalytics {
    private const val PRIVACY_PREFS = "gridee_privacy_prefs"
    private const val ANALYTICS_ENABLED = "analytics"
    private const val PLACEMENT_HOME_PORTRAIT = "home_portrait"
    private const val PLACEMENT_BOOKING_QR = "booking_qr_pass"

    fun applyCollectionPreference(context: Context) {
        FirebaseAnalytics.getInstance(context.applicationContext)
            .setAnalyticsCollectionEnabled(isEnabled(context))
    }

    fun setCollectionEnabled(context: Context, enabled: Boolean) {
        FirebaseAnalytics.getInstance(context.applicationContext)
            .setAnalyticsCollectionEnabled(enabled)
    }

    fun logLoad(context: Context, event: AdMobNativeAdCardView.LoadEvent) {
        log(context, "home_native_load") {
            putString("result", if (event.loaded) "fill" else "no_fill")
            event.adSourceName?.let { putString("ad_source", it.take(100)) }
            event.errorCode?.let { putLong("error_code", it.toLong()) }
            putString(
                "media_type",
                when {
                    !event.loaded -> "none"
                    event.hasVideoContent -> "video"
                    else -> "image"
                }
            )
        }
    }

    fun logImpression(context: Context) = log(context, "home_native_impression")

    fun logClick(context: Context) = log(context, "home_native_click")

    fun logVideoEvent(context: Context, action: String, muted: Boolean? = null) {
        log(context, "home_native_video") {
            putString("action", action)
            muted?.let { putLong("muted", if (it) 1L else 0L) }
        }
    }

    fun logPaidEvent(context: Context, event: AdMobNativeAdCardView.PaidEvent) {
        log(context, "home_native_paid") {
            putLong("value_micros", event.valueMicros)
            putString(FirebaseAnalytics.Param.CURRENCY, event.currencyCode)
            putLong("precision", event.precisionType.toLong())
            event.adSourceName?.let { putString("ad_source", it.take(100)) }
        }
    }

    fun logBookingQrLoad(context: Context, event: BookingQrNativeAdView.LoadEvent) {
        log(context, "booking_qr_native_load", PLACEMENT_BOOKING_QR) {
            putString("result", if (event.loaded) "fill" else "no_fill")
            event.adSourceName?.let { putString("ad_source", it.take(100)) }
            event.errorCode?.let { putLong("error_code", it.toLong()) }
            putString(
                "media_type",
                when {
                    !event.loaded -> "none"
                    event.hasVideoContent -> "video"
                    else -> "image"
                }
            )
        }
    }

    fun logBookingQrImpression(context: Context) =
        log(context, "booking_qr_native_impression", PLACEMENT_BOOKING_QR)

    fun logBookingQrClick(context: Context) =
        log(context, "booking_qr_native_click", PLACEMENT_BOOKING_QR)

    fun logBookingQrVideoEvent(context: Context, action: String, muted: Boolean? = null) {
        log(context, "booking_qr_native_video", PLACEMENT_BOOKING_QR) {
            putString("action", action)
            muted?.let { putLong("muted", if (it) 1L else 0L) }
        }
    }

    fun logBookingQrPaidEvent(context: Context, event: BookingQrNativeAdView.PaidEvent) {
        log(context, "booking_qr_native_paid", PLACEMENT_BOOKING_QR) {
            putLong("value_micros", event.valueMicros)
            putString(FirebaseAnalytics.Param.CURRENCY, event.currencyCode)
            putLong("precision", event.precisionType.toLong())
            event.adSourceName?.let { putString("ad_source", it.take(100)) }
        }
    }

    private fun log(
        context: Context,
        eventName: String,
        placement: String = PLACEMENT_HOME_PORTRAIT,
        populate: Bundle.() -> Unit = {}
    ) {
        if (!isEnabled(context)) return
        val params = Bundle().apply {
            putString("placement", placement)
            populate()
        }
        FirebaseAnalytics.getInstance(context.applicationContext).logEvent(eventName, params)
    }

    private fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(ANALYTICS_ENABLED, true)
    }
}
