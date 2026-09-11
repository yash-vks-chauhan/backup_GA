package com.gridee.parking.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val MAIN_BENCHMARK_ALIAS =
    "$TARGET_PACKAGE.benchmark.MainContainerBenchmarkActivity"
internal const val PERF024_SEED_RECEIVER =
    "$TARGET_PACKAGE.benchmark.Perf024BenchmarkSeedReceiver"

internal const val PERF024_BOOKINGS_SWITCH_TRACE = "perf024_first_bookings_switch"
internal const val PERF024_PROFILE_SWITCH_TRACE = "perf024_first_profile_switch"

private const val PERF024_SEED_ACTION =
    "com.gridee.parking.benchmark.PERF024_SEED_SESSION"
private const val NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val MAIN_UI_TIMEOUT_MS = 15_000L
private const val FORMER_PREWARM_WINDOW_MS = 1_600L

internal fun mainBenchmarkIntent(): Intent = Intent().apply {
    component = ComponentName(TARGET_PACKAGE, MAIN_BENCHMARK_ALIAS)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

internal fun MacrobenchmarkScope.prepareMainBenchmarkStart() {
    device.wakeUp()
    device.executeShellCommand("wm dismiss-keyguard")
    // Keep the runtime permission dialog out of both the measured window and tab hit targets.
    // This grant affects only the locally installed benchmark APK.
    device.executeShellCommand("pm grant $TARGET_PACKAGE $NOTIFICATION_PERMISSION")
    val result = device.executeShellCommand(
        "am broadcast -a $PERF024_SEED_ACTION -n $TARGET_PACKAGE/$PERF024_SEED_RECEIVER"
    )
    check(result.contains("result=0")) {
        "Unable to seed the offline PERF-024 session: $result"
    }
    pressHome()
}

internal fun MacrobenchmarkScope.startMainBenchmarkAndAwaitHome() {
    startActivityAndWait(mainBenchmarkIntent())
    requireMainObject("main_container_root")
    val home = requireMainObject("tab_home")
    check(home.isSelected) { "Main benchmark did not start on the Home tab" }
}

internal fun MacrobenchmarkScope.observeFormerPrewarmWindow() {
    SystemClock.sleep(FORMER_PREWARM_WINDOW_MS)
}

internal fun MacrobenchmarkScope.runOfflineSafeFirstTabSwitchJourney() {
    switchTab(
        tabResource = "tab_bookings",
        // The benchmark variant is intentionally offline, so the visible, stable
        // Bookings destination is its empty state; the RecyclerView remains GONE.
        destinationResource = "layout_empty_state",
    )
    switchTab(
        tabResource = "tab_profile",
        destinationResource = "scrollContent",
    )
}

private fun MacrobenchmarkScope.switchTab(
    tabResource: String,
    destinationResource: String,
) {
    device.wakeUp()
    device.executeShellCommand("wm dismiss-keyguard")
    val tab = requireMainObject(tabResource)
    device.click(tab.visibleCenter.x, tab.visibleCenter.y)
    device.waitForIdle()
    requireMainObject(destinationResource)
    val selectedTab = requireMainObject(tabResource)
    check(selectedTab.isSelected) { "$tabResource was not selected after navigation" }
}

private fun MacrobenchmarkScope.requireMainObject(resourceName: String) = device.wait(
    Until.findObject(By.res(TARGET_PACKAGE, resourceName)),
    MAIN_UI_TIMEOUT_MS,
) ?: error("Missing $TARGET_PACKAGE:id/$resourceName after ${MAIN_UI_TIMEOUT_MS}ms")
