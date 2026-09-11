package com.gridee.parking.ui.motion

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

/** Reads the effective system animator preference without calling APIs newer than the device. */
internal object AnimatorSettingsCompat {
    private const val DEFAULT_DURATION_SCALE = 1f

    fun areEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            resolveForSdk(
                sdkInt = Build.VERSION.SDK_INT,
                api26Enabled = { Api26Impl.areEnabled() },
                legacyDurationScale = { DEFAULT_DURATION_SCALE },
            )
        } else {
            resolveForSdk(
                sdkInt = Build.VERSION.SDK_INT,
                api26Enabled = { true },
                legacyDurationScale = {
                    Settings.Global.getFloat(
                        context.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        DEFAULT_DURATION_SCALE,
                    )
                },
            )
        }
    }

    /**
     * Lazy readers are intentional: tests prove that an API 24/25 device never evaluates the
     * API-26 reader and that API 26+ never touches the legacy settings provider.
     */
    internal fun resolveForSdk(
        sdkInt: Int,
        api26Enabled: () -> Boolean,
        legacyDurationScale: () -> Float,
    ): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.O) {
            api26Enabled()
        } else {
            try {
                legacyDurationScale() > 0f
            } catch (_: RuntimeException) {
                true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private object Api26Impl {
        fun areEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
    }
}
