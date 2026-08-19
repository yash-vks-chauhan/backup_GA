package com.gridee.parking.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Owns the process-wide UMP consent flow. Ad requests stay disabled until the current app launch
 * has refreshed consent information, preventing a stale cached choice from being used silently.
 */
object AdConsentManager {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCallbacks = mutableListOf<(Boolean) -> Unit>()

    @Volatile
    private var consentFlowCompletedThisProcess = false

    private var requestInProgress = false

    fun canRequestAds(context: Context): Boolean {
        return consentFlowCompletedThisProcess &&
            UserMessagingPlatform.getConsentInformation(context.applicationContext).canRequestAds()
    }

    fun gatherConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        runOnMain {
            if (consentFlowCompletedThisProcess) {
                onComplete(canRequestAds(activity))
                return@runOnMain
            }

            pendingCallbacks += onComplete
            if (requestInProgress) return@runOnMain
            requestInProgress = true

            val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
            val parameters = ConsentRequestParameters.Builder().build()
            consentInformation.requestConsentInfoUpdate(
                activity,
                parameters,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                        finishConsentFlow(activity)
                    }
                },
                {
                    // Existing consent can still be valid during a transient network failure.
                    // canRequestAds() remains the source of truth after this launch's attempt.
                    finishConsentFlow(activity)
                }
            )
        }
    }

    fun isPrivacyOptionsRequired(context: Context): Boolean {
        return UserMessagingPlatform.getConsentInformation(context.applicationContext)
            .privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun showPrivacyOptions(activity: Activity, onDismissed: (Boolean) -> Unit = {}) {
        runOnMain {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                onDismissed(canRequestAds(activity))
            }
        }
    }

    private fun finishConsentFlow(context: Context) {
        val callbacks = synchronized(this) {
            consentFlowCompletedThisProcess = true
            requestInProgress = false
            pendingCallbacks.toList().also { pendingCallbacks.clear() }
        }
        val allowed = canRequestAds(context)
        callbacks.forEach { callback -> callback(allowed) }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
