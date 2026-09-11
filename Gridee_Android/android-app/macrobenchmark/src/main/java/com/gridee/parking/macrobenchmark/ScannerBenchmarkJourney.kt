package com.gridee.parking.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.math.abs

internal const val TARGET_PACKAGE = "com.gridee.parking"
internal const val SCANNER_BENCHMARK_ALIAS =
    "$TARGET_PACKAGE.benchmark.OperatorScannerBenchmarkActivity"
internal const val MODE_SWITCH_SLA_MS = 2_000L

private const val SCAN_TYPE_EXTRA = "scan_type"
private const val PARKING_LOT_ID_EXTRA = "parking_lot_id"
private const val CAMERA_PERMISSION = "android.permission.CAMERA"
private const val TEST_LOT_ID = "ANDROID-SCANNER-BENCHMARK"
private const val UI_READY_TIMEOUT_MS = 15_000L
private const val MODE_SETTLE_POLL_MS = 16L
private const val READINESS_MARKER_RESOURCE = "scanner_benchmark_readiness"
private const val READY_PLATE_MARKER = "scanner_benchmark_ready_plate"
private const val READY_QR_MARKER = "scanner_benchmark_ready_qr"

internal enum class ScannerBenchmarkMode {
    PLATE,
    QR,
}

internal fun scannerBenchmarkIntent(): Intent = Intent().apply {
    component = ComponentName(TARGET_PACKAGE, SCANNER_BENCHMARK_ALIAS)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    putExtra(SCAN_TYPE_EXTRA, "VEHICLE_CHECK_IN")
    putExtra(PARKING_LOT_ID_EXTRA, TEST_LOT_ID)
}

internal fun MacrobenchmarkScope.prepareScannerBenchmarkStart() {
    device.executeShellCommand("pm grant $TARGET_PACKAGE $CAMERA_PERMISSION")
    pressHome()
}

internal fun MacrobenchmarkScope.startScannerBenchmark(
    initialMode: ScannerBenchmarkMode = ScannerBenchmarkMode.PLATE,
) {
    startActivityAndWait(scannerBenchmarkIntent())
    awaitScannerUi()
    awaitFrameMode(ScannerBenchmarkMode.PLATE, UI_READY_TIMEOUT_MS)
    awaitScannerReady(ScannerBenchmarkMode.PLATE, UI_READY_TIMEOUT_MS)
    if (initialMode == ScannerBenchmarkMode.QR) {
        switchScannerMode(
            targetMode = ScannerBenchmarkMode.QR,
            timeoutMs = UI_READY_TIMEOUT_MS,
        )
    }
}

internal fun MacrobenchmarkScope.switchScannerMode(
    targetMode: ScannerBenchmarkMode,
    timeoutMs: Long = MODE_SWITCH_SLA_MS,
): Long {
    val toggle = requireObject(
        when (targetMode) {
            ScannerBenchmarkMode.PLATE -> "segment_scanner_plate"
            ScannerBenchmarkMode.QR -> "segment_scanner_qr"
        }
    )
    val startedAtMs = SystemClock.elapsedRealtime()
    val deadlineMs = startedAtMs + timeoutMs
    device.click(toggle.visibleBounds.centerX(), toggle.visibleBounds.centerY())
    awaitFrameMode(targetMode, remainingTime(deadlineMs))
    awaitScannerReady(targetMode, remainingTime(deadlineMs))
    val uiElapsedMs = SystemClock.elapsedRealtime() - startedAtMs
    return uiElapsedMs
}

private fun MacrobenchmarkScope.awaitScannerUi() {
    requireObject("scanner_input_toggle", UI_READY_TIMEOUT_MS)
    requireObject("scanning_frame_container", UI_READY_TIMEOUT_MS)
}

private fun MacrobenchmarkScope.awaitFrameMode(
    expectedMode: ScannerBenchmarkMode,
    timeoutMs: Long,
) {
    val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs
    do {
        val frame = device.findObject(By.res(TARGET_PACKAGE, "scanning_frame_container"))
        if (frame != null && frame.visibleBounds.matches(expectedMode)) return
        SystemClock.sleep(MODE_SETTLE_POLL_MS)
    } while (SystemClock.elapsedRealtime() < deadlineMs)

    val lastBounds = device.findObject(
        By.res(TARGET_PACKAGE, "scanning_frame_container")
    )?.visibleBounds
    error(
        "Scanner did not render $expectedMode mode within ${timeoutMs}ms; " +
            "last frame bounds=$lastBounds"
    )
}

private fun MacrobenchmarkScope.awaitScannerReady(
    expectedMode: ScannerBenchmarkMode,
    timeoutMs: Long,
) {
    val expectedMarker = when (expectedMode) {
        ScannerBenchmarkMode.PLATE -> READY_PLATE_MARKER
        ScannerBenchmarkMode.QR -> READY_QR_MARKER
    }
    val marker = device.wait(
        Until.findObject(
            By.res(TARGET_PACKAGE, READINESS_MARKER_RESOURCE).desc(expectedMarker)
        ),
        timeoutMs.coerceAtLeast(1L),
    )
    if (marker != null) return

    val currentMarker = device.findObject(
        By.res(TARGET_PACKAGE, READINESS_MARKER_RESOURCE)
    )?.contentDescription
    error(
        "Scanner did not reach PreviewView STREAMING plus a valid $expectedMode analyzer " +
            "frame within ${timeoutMs}ms; current readiness marker=$currentMarker"
    )
}

private fun remainingTime(deadlineMs: Long): Long {
    return (deadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
}

private fun Rect.matches(mode: ScannerBenchmarkMode): Boolean {
    if (width() < 100 || height() < 100) return false
    return when (mode) {
        ScannerBenchmarkMode.QR -> abs(width() - height()) <= maxOf(4, width() / 50)
        ScannerBenchmarkMode.PLATE -> width().toFloat() / height().toFloat() >= 1.40f
    }
}

private fun MacrobenchmarkScope.requireObject(
    resourceName: String,
    timeoutMs: Long = UI_READY_TIMEOUT_MS,
): UiObject2 = device.wait(
    Until.findObject(By.res(TARGET_PACKAGE, resourceName)),
    timeoutMs,
) ?: error("Missing $TARGET_PACKAGE:id/$resourceName after ${timeoutMs}ms")

internal fun UiDevice.grantScannerCameraPermission() {
    executeShellCommand("pm grant $TARGET_PACKAGE $CAMERA_PERMISSION")
}
