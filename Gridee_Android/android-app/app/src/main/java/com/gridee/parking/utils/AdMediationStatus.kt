package com.gridee.parking.utils

import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.android.gms.ads.initialization.InitializationStatus
import com.gridee.parking.BuildConfig

/**
 * Reports which mediation adapters the Mobile Ads SDK actually brought up.
 *
 * This exists because the AdMob console and the app can disagree silently: a network enabled in
 * the console with no adapter in the APK simply never fills, and nothing in a release build says
 * so. On a debug build this prints one table at startup — READY, NOT READY, or ABSENT — which is
 * what you compare against the mediation groups in the console.
 *
 * Nothing here influences ad serving; it only observes.
 */
object AdMediationStatus {

    private const val TAG = "AdMediation"

    /**
     * Adapter classes we know how to name, so an absent one can be reported as absent rather
     * than just missing from the map. Presence in this list is not a claim that the network is
     * configured — it is the vocabulary used to describe what turned up.
     */
    private val KNOWN_ADAPTERS = linkedMapOf(
        "com.google.android.gms.ads.MobileAds" to "Google (AdMob)",
        "com.google.ads.mediation.facebook.FacebookMediationAdapter" to "Meta Audience Network",
        "com.google.ads.mediation.applovin.AppLovinMediationAdapter" to "AppLovin",
        "com.google.ads.mediation.unity.UnityMediationAdapter" to "Unity Ads",
        "com.google.ads.mediation.ironsource.IronSourceMediationAdapter" to "ironSource",
        "com.google.ads.mediation.vungle.VungleMediationAdapter" to "Liftoff Monetize (Vungle)",
        "com.google.ads.mediation.mintegral.MintegralMediationAdapter" to "Mintegral",
        "com.google.ads.mediation.pangle.PangleMediationAdapter" to "Pangle",
        "com.google.ads.mediation.inmobi.InMobiMediationAdapter" to "InMobi",
        "com.google.ads.mediation.chartboost.ChartboostMediationAdapter" to "Chartboost",
        "com.google.ads.mediation.fyber.FyberMediationAdapter" to "DT Exchange (Fyber)",
        "com.google.ads.mediation.line.LineMediationAdapter" to "LINE",
        "com.google.ads.mediation.moloco.MolocoMediationAdapter" to "Moloco"
    )

    /** Adapters bundled on purpose. Anything here that fails to initialise is a real problem. */
    private val EXPECTED_ADAPTERS = setOf(
        "com.google.android.gms.ads.MobileAds",
        "com.google.ads.mediation.facebook.FacebookMediationAdapter",
        "com.google.ads.mediation.unity.UnityMediationAdapter"
    )

    /**
     * Logs one line per adapter the SDK reported, then one line per bundled adapter that did not
     * report at all. Debug builds only — release builds do no work here.
     */
    fun log(status: InitializationStatus?) {
        if (!BuildConfig.DEBUG) return

        val statusMap = status?.adapterStatusMap.orEmpty()
        if (statusMap.isEmpty()) {
            AppLog.w(TAG) { "No adapter status reported — mediation may not be initialised yet" }
            return
        }

        AppLog.i(TAG) { "── Mediation adapter status (${statusMap.size} reported) ──" }
        statusMap.forEach { (className, adapterStatus) ->
            val label = KNOWN_ADAPTERS[className] ?: "Unknown adapter"
            val state = when (adapterStatus.initializationState) {
                AdapterStatus.State.READY -> "READY"
                AdapterStatus.State.NOT_READY -> "NOT READY"
                else -> "UNKNOWN"
            }
            AppLog.i(TAG) { "  [$state] $label (${adapterStatus.latency}ms)" }
        }

        EXPECTED_ADAPTERS.filterNot { statusMap.containsKey(it) }.forEach { missing ->
            val label = KNOWN_ADAPTERS[missing] ?: missing
            AppLog.w(TAG) { "  [ABSENT] $label — bundled adapter did not report; check the dependency" }
        }

        val unbundled = KNOWN_ADAPTERS.keys.filterNot { statusMap.containsKey(it) || it in EXPECTED_ADAPTERS }
        AppLog.i(TAG) {
            "Not bundled: " + unbundled.joinToString { KNOWN_ADAPTERS.getValue(it) } +
                ". If any of these are enabled in the AdMob console, they cannot fill — " +
                "add the adapter dependency or disable them in the mediation group."
        }
    }
}
