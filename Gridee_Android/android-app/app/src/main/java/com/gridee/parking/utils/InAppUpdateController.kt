package com.gridee.parking.utils

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

class InAppUpdateController(
    private val activity: Activity,
    private val snackbarAnchorView: View,
    private val requestCode: Int = DEFAULT_REQUEST_CODE,
    private val preferImmediate: Boolean = true,
) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    private var flexibleListenerRegistered = false
    private var flexibleSnackbarShown = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            showFlexibleCompleteSnackbar()
        }
    }

    fun checkForUpdates() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info -> handleUpdateInfo(info, isFromResume = false) }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to fetch appUpdateInfo (is app installed from Play Store?)", e)
            }
    }

    fun onResume() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info -> handleUpdateInfo(info, isFromResume = true) }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to fetch appUpdateInfo onResume", e)
            }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, _data: Intent?) {
        if (requestCode != this.requestCode) return

        if (resultCode != Activity.RESULT_OK) {
            Log.i(TAG, "In-app update flow did not complete (resultCode=$resultCode); re-checking")
            checkForUpdates()
        }
    }

    fun onDestroy() {
        unregisterFlexibleListener()
    }

    private fun handleUpdateInfo(info: AppUpdateInfo, isFromResume: Boolean) {
        if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            Log.i(TAG, "Update in progress; resuming immediate update flow")
            startUpdateFlow(info, AppUpdateType.IMMEDIATE)
            return
        }

        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            showFlexibleCompleteSnackbar()
            return
        }

        if (isFromResume) return

        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
            Log.d(TAG, "No update available (availability=${info.updateAvailability()})")
            return
        }

        val immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        val flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

        val updateType = chooseUpdateType(
            preferImmediate = preferImmediate,
            immediateAllowed = immediateAllowed,
            flexibleAllowed = flexibleAllowed,
        )

        if (updateType == null) {
            Log.w(
                TAG,
                "Update available but no allowed update type (immediateAllowed=$immediateAllowed, flexibleAllowed=$flexibleAllowed)"
            )
            return
        }

        if (updateType == AppUpdateType.FLEXIBLE) {
            registerFlexibleListener()
        }

        Log.i(TAG, "Starting in-app update flow (type=$updateType)")
        startUpdateFlow(info, updateType)
    }

    private fun startUpdateFlow(info: AppUpdateInfo, updateType: Int) {
        try {
            appUpdateManager.startUpdateFlowForResult(info, updateType, activity, requestCode)
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Failed to start in-app update flow", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unexpected error starting in-app update flow", e)
        }
    }

    private fun registerFlexibleListener() {
        if (flexibleListenerRegistered) return
        flexibleListenerRegistered = true
        appUpdateManager.registerListener(installStateListener)
    }

    private fun unregisterFlexibleListener() {
        if (!flexibleListenerRegistered) return
        flexibleListenerRegistered = false
        runCatching { appUpdateManager.unregisterListener(installStateListener) }
    }

    private fun showFlexibleCompleteSnackbar() {
        if (flexibleSnackbarShown) return
        flexibleSnackbarShown = true

        Snackbar.make(snackbarAnchorView, "Update ready to install", Snackbar.LENGTH_INDEFINITE)
            .setAction("Restart") { appUpdateManager.completeUpdate() }
            .show()
    }

    companion object {
        private const val TAG = "InAppUpdate"
        const val DEFAULT_REQUEST_CODE = 9101

        internal fun chooseUpdateType(
            preferImmediate: Boolean,
            immediateAllowed: Boolean,
            flexibleAllowed: Boolean,
        ): Int? {
            return when {
                preferImmediate && immediateAllowed -> AppUpdateType.IMMEDIATE
                flexibleAllowed -> AppUpdateType.FLEXIBLE
                immediateAllowed -> AppUpdateType.IMMEDIATE
                else -> null
            }
        }
    }
}
