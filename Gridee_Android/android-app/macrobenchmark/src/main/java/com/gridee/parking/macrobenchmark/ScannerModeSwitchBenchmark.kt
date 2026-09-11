package com.gridee.parking.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
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
class ScannerModeSwitchBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun plateToQrFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric(
                sectionName = "scanner_mode_switch",
                mode = TraceSectionMetric.Mode.First,
            ),
        ),
        compilationMode = CompilationMode.Partial(),
        iterations = 15,
        setupBlock = {
            killProcess()
            prepareScannerBenchmarkStart()
            startScannerBenchmark(ScannerBenchmarkMode.PLATE)
        },
        measureBlock = {
            val elapsedMs = switchScannerMode(ScannerBenchmarkMode.QR)
            check(elapsedMs <= MODE_SWITCH_SLA_MS) {
                "Plate to QR took ${elapsedMs}ms; SLA is ${MODE_SWITCH_SLA_MS}ms"
            }
        },
    )

    @Test
    fun qrToPlateFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric(
                sectionName = "scanner_mode_switch",
                mode = TraceSectionMetric.Mode.First,
            ),
        ),
        compilationMode = CompilationMode.Partial(),
        iterations = 15,
        setupBlock = {
            killProcess()
            prepareScannerBenchmarkStart()
            startScannerBenchmark(ScannerBenchmarkMode.QR)
        },
        measureBlock = {
            val elapsedMs = switchScannerMode(ScannerBenchmarkMode.PLATE)
            check(elapsedMs <= MODE_SWITCH_SLA_MS) {
                "QR to Plate took ${elapsedMs}ms; SLA is ${MODE_SWITCH_SLA_MS}ms"
            }
        },
    )

    @Test
    fun repeatedModeSwitchJank() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric(
                sectionName = "scanner_mode_switch",
                mode = TraceSectionMetric.Mode.Average,
                label = "scanner_mode_switch_average",
            ),
            TraceSectionMetric(
                sectionName = "scanner_mode_switch",
                mode = TraceSectionMetric.Mode.Max,
                label = "scanner_mode_switch_max",
            ),
        ),
        compilationMode = CompilationMode.Partial(),
        iterations = 10,
        setupBlock = {
            killProcess()
            prepareScannerBenchmarkStart()
            startScannerBenchmark(ScannerBenchmarkMode.PLATE)
        },
        measureBlock = {
            repeat(6) {
                check(switchScannerMode(ScannerBenchmarkMode.QR) <= MODE_SWITCH_SLA_MS)
                check(switchScannerMode(ScannerBenchmarkMode.PLATE) <= MODE_SWITCH_SLA_MS)
            }
        },
    )
}
