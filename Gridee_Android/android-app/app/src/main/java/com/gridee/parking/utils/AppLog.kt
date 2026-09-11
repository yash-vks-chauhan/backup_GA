package com.gridee.parking.utils

import android.util.Log
import com.gridee.parking.BuildConfig

/**
 * The only Android Logcat gateway allowed in main source.
 *
 * Production has an empty Logcat allowlist: operational reporting belongs in the redacted
 * telemetry pipeline tracked by PERF-039. Messages passed here must still avoid secrets and
 * personal data because debug builds can also be captured and shared.
 */
internal object AppLog {
    @PublishedApi
    internal const val LEGACY_TAG_LIMIT = 23

    @PublishedApi
    internal inline fun emitIfEnabled(
        debugBuild: Boolean = BuildConfig.DEBUG,
        crossinline emission: () -> Int,
    ): Int = if (debugBuild) emission() else 0

    internal inline fun d(tag: String, crossinline message: () -> String): Int =
        emitIfEnabled { Log.d(tag.take(LEGACY_TAG_LIMIT), message()) }

    internal inline fun i(tag: String, crossinline message: () -> String): Int =
        emitIfEnabled { Log.i(tag.take(LEGACY_TAG_LIMIT), message()) }

    internal inline fun w(tag: String, crossinline message: () -> String): Int =
        emitIfEnabled { Log.w(tag.take(LEGACY_TAG_LIMIT), message()) }

    internal inline fun e(tag: String, crossinline message: () -> String): Int =
        emitIfEnabled { Log.e(tag.take(LEGACY_TAG_LIMIT), message()) }
}
