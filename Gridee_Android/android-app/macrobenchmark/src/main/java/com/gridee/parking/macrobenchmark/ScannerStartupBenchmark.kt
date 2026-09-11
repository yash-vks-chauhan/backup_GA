package com.gridee.parking.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ScannerStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldScannerStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            TraceSectionMetric(
                sectionName = "scanner_preview_ready",
                mode = TraceSectionMetric.Mode.First,
            ),
        ),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            prepareScannerBenchmarkStart()
        },
        measureBlock = {
            startScannerBenchmark()
        },
    )

    @Test
    fun warmScannerStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            TraceSectionMetric(
                sectionName = "scanner_preview_ready",
                mode = TraceSectionMetric.Mode.First,
            ),
        ),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = {
            prepareScannerBenchmarkStart()
        },
        measureBlock = {
            startScannerBenchmark()
        },
    )
}
