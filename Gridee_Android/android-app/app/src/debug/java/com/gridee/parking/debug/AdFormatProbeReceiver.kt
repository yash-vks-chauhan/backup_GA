package com.gridee.parking.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

/**
 * Debug-only diagnostic: loads every production ad unit against every full-screen format and
 * logs which combination the AdMob backend accepts. Used to identify a unit whose console
 * format does not match the API it is requested through.
 */
class AdFormatProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROBE) return
        val appContext = context.applicationContext

        MobileAds.initialize(appContext) {
            // Sequential with spacing: firing every combination at once got some requests
            // silently dropped, leaving gaps in the matrix.
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var delay = 0L
            UNITS.forEach { (label, unitId) ->
                listOf<(Context, String, String) -> Unit>(
                    ::probeInterstitial,
                    ::probeRewarded,
                    ::probeRewardedInterstitial,
                    ::probeNative,
                    ::probeBanner,
                    ::probeAppOpen
                ).forEach { probe ->
                    handler.postDelayed({ probe(appContext, label, unitId) }, delay)
                    delay += 3000L
                }
            }
        }
    }

    private fun probeBanner(context: Context, label: String, unitId: String) {
        val view = com.google.android.gms.ads.AdView(context)
        view.adUnitId = unitId
        view.setAdSize(com.google.android.gms.ads.AdSize.BANNER)
        view.adListener = object : AdListener() {
            override fun onAdLoaded() = ok(label, unitId, "BANNER")
            override fun onAdFailedToLoad(e: LoadAdError) = fail(label, unitId, "BANNER", e)
        }
        view.loadAd(AdRequest.Builder().build())
    }

    private fun probeAppOpen(context: Context, label: String, unitId: String) {
        com.google.android.gms.ads.appopen.AppOpenAd.load(
            context,
            unitId,
            AdRequest.Builder().build(),
            object : com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.appopen.AppOpenAd) =
                    ok(label, unitId, "APP_OPEN")

                override fun onAdFailedToLoad(e: LoadAdError) = fail(label, unitId, "APP_OPEN", e)
            }
        )
    }

    private fun probeInterstitial(context: Context, label: String, unitId: String) {
        InterstitialAd.load(
            context,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) = ok(label, unitId, "INTERSTITIAL")
                override fun onAdFailedToLoad(e: LoadAdError) = fail(label, unitId, "INTERSTITIAL", e)
            }
        )
    }

    private fun probeRewarded(context: Context, label: String, unitId: String) {
        RewardedAd.load(
            context,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = ok(label, unitId, "REWARDED")
                override fun onAdFailedToLoad(e: LoadAdError) = fail(label, unitId, "REWARDED", e)
            }
        )
    }

    private fun probeRewardedInterstitial(context: Context, label: String, unitId: String) {
        RewardedInterstitialAd.load(
            context,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) = ok(label, unitId, "REWARDED_INTERSTITIAL")
                override fun onAdFailedToLoad(e: LoadAdError) = fail(label, unitId, "REWARDED_INTERSTITIAL", e)
            }
        )
    }

    private fun probeNative(context: Context, label: String, unitId: String) {
        AdLoader.Builder(context, unitId)
            .forNativeAd { ad: NativeAd ->
                ok(label, unitId, "NATIVE")
                ad.destroy()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(e: LoadAdError) = fail(label, unitId, "NATIVE", e)
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun ok(label: String, unitId: String, format: String) {
        Log.w(TAG, "MATCH   $label ($unitId) loads as $format")
    }

    private fun fail(label: String, unitId: String, format: String, error: LoadAdError) {
        Log.w(TAG, "NOMATCH $label ($unitId) as $format -> code=${error.code} ${error.message}")
    }

    companion object {
        const val ACTION_PROBE = "com.gridee.parking.DEBUG_PROBE_AD_FORMATS"
        private const val TAG = "AdFormatProbe"

        private val UNITS = listOf(
            "rewarded-const" to "ca-app-pub-5268197817154713/4238043733",
            "interstitial-const" to "ca-app-pub-5268197817154713/2998879603",
            "native-const" to "ca-app-pub-5268197817154713/5433471254"
        )
    }
}
