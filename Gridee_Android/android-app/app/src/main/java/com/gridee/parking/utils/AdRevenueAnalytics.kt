package com.gridee.parking.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.gridee.parking.ui.ads.AdMobNativeAdCardView
import com.gridee.parking.ui.ads.BookingQrNativeAdView

/** Lightweight, privacy-switch-aware telemetry for every ad placement in the app. */
object AdRevenueAnalytics {
    private const val PRIVACY_PREFS = "gridee_privacy_prefs"
    private const val ANALYTICS_ENABLED = "analytics"
    private const val PLACEMENT_HOME_PORTRAIT = "home_portrait"
    private const val PLACEMENT_BOOKING_QR = "booking_qr_pass"
    private const val PLACEMENT_BOOKING_TRANSITION = "booking_transition"
    private const val PLACEMENT_REWARDED_WALLET = "rewarded_wallet_coins"

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

    /**
     * Booking-transition interstitial telemetry.
     *
     * This placement had no paid-event coverage at all, so its revenue could only ever be read
     * off the AdMob console in aggregate — there was no way to tell a preloaded impression from
     * a just-in-time one, or to account for the transitions that never produced an impression.
     * [logBookingInterstitialDiscarded] is the other half of that: every path that gives up on
     * a queued transition names why it did.
     */
    fun logBookingInterstitialLoad(
        context: Context,
        loaded: Boolean,
        adSourceName: String?,
        errorCode: Int? = null
    ) {
        log(context, "booking_interstitial_load", PLACEMENT_BOOKING_TRANSITION) {
            putString("result", if (loaded) "fill" else "no_fill")
            adSourceName?.let { putString("ad_source", it.take(100)) }
            errorCode?.let { putLong("error_code", it.toLong()) }
        }
    }

    fun logBookingInterstitialPreloaded(context: Context, adSourceName: String?, bufferSize: Int) {
        log(context, "booking_interstitial_preloaded", PLACEMENT_BOOKING_TRANSITION) {
            adSourceName?.let { putString("ad_source", it.take(100)) }
            putLong("buffer_size", bufferSize.toLong())
        }
    }

    fun logBookingInterstitialImpression(
        context: Context,
        adSourceName: String?,
        adSource: String,
        targetStatus: String
    ) {
        log(context, "booking_interstitial_impression", PLACEMENT_BOOKING_TRANSITION) {
            adSourceName?.let { putString("ad_source", it.take(100)) }
            putString("fill_path", adSource)
            putString("transition", targetStatus.take(40))
        }
    }

    fun logBookingInterstitialClick(context: Context) =
        log(context, "booking_interstitial_click", PLACEMENT_BOOKING_TRANSITION)

    fun logBookingInterstitialPaidEvent(
        context: Context,
        valueMicros: Long,
        currencyCode: String,
        precisionType: Int,
        adSourceName: String?,
        adSource: String
    ) {
        log(context, "booking_interstitial_paid", PLACEMENT_BOOKING_TRANSITION) {
            putLong("value_micros", valueMicros)
            putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)
            putLong("precision", precisionType.toLong())
            adSourceName?.let { putString("ad_source", it.take(100)) }
            putString("fill_path", adSource)
        }
    }

    /** Why a queued booking transition never became an impression. */
    fun logBookingInterstitialDiscarded(
        context: Context,
        reason: String,
        targetStatus: String
    ) {
        log(context, "booking_interstitial_discarded", PLACEMENT_BOOKING_TRANSITION) {
            putString("discard_reason", reason)
            putString("transition", targetStatus.take(40))
        }
    }

    /**
     * Rewarded wallet-coin telemetry.
     *
     * The rewarded unit earns the highest eCPM in the app and was the last placement with no
     * impression-level coverage at all, so every figure about it was an AdMob console aggregate.
     * Two numbers in particular could not be derived from the console:
     *
     *  - **Claim rate.** [logRewardedEarned] against [logRewardedImpression] gives the share of
     *    shown ads that actually reach the reward callback. Everything written about the cost of
     *    a reward so far assumes that share is 100%, which it is not.
     *  - **Who is filling.** `ad_source` on every event names the winning adapter, so per-network
     *    rewarded eCPM becomes readable before and after a mediation change.
     *
     * [logRewardedDiscarded] is the other half: every path that opens the sheet and never
     * produces an impression names why.
     */
    fun logRewardedLoad(
        context: Context,
        loaded: Boolean,
        adSourceName: String?,
        entryPoint: String,
        errorCode: Int? = null
    ) {
        log(context, "rewarded_load", PLACEMENT_REWARDED_WALLET) {
            putString("result", if (loaded) "fill" else "no_fill")
            adSourceName?.let { putString("ad_source", it.take(100)) }
            putString("entry_point", entryPoint)
            errorCode?.let { putLong("error_code", it.toLong()) }
        }
    }

    fun logRewardedImpression(context: Context, adSourceName: String?, entryPoint: String) {
        log(context, "rewarded_impression", PLACEMENT_REWARDED_WALLET) {
            adSourceName?.let { putString("ad_source", it.take(100)) }
            putString("entry_point", entryPoint)
        }
    }

    fun logRewardedClick(context: Context, adSourceName: String?) {
        log(context, "rewarded_click", PLACEMENT_REWARDED_WALLET) {
            adSourceName?.let { putString("ad_source", it.take(100)) }
        }
    }

    fun logRewardedPaidEvent(
        context: Context,
        valueMicros: Long,
        currencyCode: String,
        precisionType: Int,
        adSourceName: String?,
        entryPoint: String
    ) {
        log(context, "rewarded_paid", PLACEMENT_REWARDED_WALLET) {
            putLong("value_micros", valueMicros)
            putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)
            putLong("precision", precisionType.toLong())
            adSourceName?.let { putString("ad_source", it.take(100)) }
            putString("entry_point", entryPoint)
        }
    }

    /** The reward callback fired — the numerator of the claim rate. */
    fun logRewardedEarned(
        context: Context,
        adSourceName: String?,
        entryPoint: String,
        rewardAmount: Double
    ) {
        log(context, "rewarded_earned", PLACEMENT_REWARDED_WALLET) {
            adSourceName?.let { putString("ad_source", it.take(100)) }
            putString("entry_point", entryPoint)
            putLong("reward_amount", rewardAmount.toLong())
        }
    }

    /** Why an opened reward sheet never became an impression. */
    fun logRewardedDiscarded(context: Context, reason: String, entryPoint: String) {
        log(context, "rewarded_discarded", PLACEMENT_REWARDED_WALLET) {
            putString("discard_reason", reason)
            putString("entry_point", entryPoint)
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
