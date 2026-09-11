package com.gridee.parking.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class MainTabsPerf024Benchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun keepDeviceAwake() {
        benchmarkDevice().apply {
            executeShellCommand("svc power stayon true")
            wakeUp()
            executeShellCommand("wm dismiss-keyguard")
        }
    }

    @After
    fun restoreDeviceSleepPolicy() {
        benchmarkDevice().executeShellCommand("svc power stayon false")
    }

    @Test
    fun coldMainStartupAndFormerPrewarmWindow() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            peakMemoryMetric(),
        ),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            prepareMainBenchmarkStart()
        },
        measureBlock = {
            startMainBenchmarkAndAwaitHome()
            observeFormerPrewarmWindow()
        },
    )

    @Test
    fun firstOfflineSafeTabSwitches() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            peakMemoryMetric(),
            TraceSectionMetric(
                sectionName = PERF024_BOOKINGS_SWITCH_TRACE,
                mode = TraceSectionMetric.Mode.First,
            ),
            TraceSectionMetric(
                sectionName = PERF024_PROFILE_SWITCH_TRACE,
                mode = TraceSectionMetric.Mode.First,
            ),
        ),
        compilationMode = CompilationMode.Partial(),
        iterations = 10,
        setupBlock = {
            prepareMainBenchmarkStart()
            startMainBenchmarkAndAwaitHome()
        },
        measureBlock = {
            runOfflineSafeFirstTabSwitchJourney()
        },
    )

    private fun peakMemoryMetric() = MemoryUsageMetric(
        mode = MemoryUsageMetric.Mode.Max,
        subMetrics = listOf(
            MemoryUsageMetric.SubMetric.HeapSize,
            MemoryUsageMetric.SubMetric.RssAnon,
            MemoryUsageMetric.SubMetric.RssFile,
            MemoryUsageMetric.SubMetric.RssShmem,
            MemoryUsageMetric.SubMetric.Gpu,
        ),
    )

    private fun benchmarkDevice(): UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}
