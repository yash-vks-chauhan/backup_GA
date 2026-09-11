# Operator scanner Macrobenchmark harness

This module measures the real Android scanner in a non-debuggable, release-like
`benchmark` app variant. The app's normal `release` variant is unchanged: only
the benchmark source set is shell-profileable and only it contains the exported
scanner activity alias. The benchmark APK has no `INTERNET` permission and both
API URLs are hard-pinned to `https://127.0.0.1/`, so it cannot read from or mutate
the production backend.

Run on a physical Android 10+ device with the camera unobstructed and aimed at a
well-lit, high-detail scanner target. The Plate analyzer intentionally rejects
dark or featureless frames before inference, so a covered lens or blank wall is
a failed readiness precondition rather than a latency sample. Android 14+ is
preferred because compilation-state resets preserve app state there.

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Run one benchmark while iterating:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gridee.parking.macrobenchmark.ScannerModeSwitchBenchmark#plateToQrFrameTiming
```

The suite records cold/warm scanner startup plus the `scanner_preview_ready`
trace section, Plate to QR and QR to Plate frame timing plus the
`scanner_mode_switch` trace section, and repeated-switch jank. Startup and every
one-way switch now wait for an app marker that requires both PreviewView
`STREAMING` and a valid frame routed into the selected ML analyzer; frame geometry
alone cannot pass a run. Each one-way switch enforces the product's 2,000 ms
maximum readiness SLA without fixed sleeps inside the measured block. Perfetto
traces and JSON results are written
under `macrobenchmark/build/outputs/connected_android_test_additional_output/`.

Do not use emulator timing as a production gate. Camera, ML Kit, thermal, and GPU
behavior must be evaluated on the approved low-, mid-, and high-tier physical
device matrix. Recognition and mode-switch measurements in this harness are
strictly local: it cannot perform backend mutations and does not validate booking
lookup latency. Use dedicated non-production visual targets for recognition QA.

## PERF-024 main-tab benchmark

The benchmark APK also contains an offline-only exported alias for the real
`MainContainerActivity` and a benchmark-only receiver that seeds a synthetic local
session. Neither component is present in debug or release APKs. The benchmark
manifest removes Internet permission and both endpoints remain pinned to loopback,
so the tab journey cannot read from or mutate a backend.

Run the PERF-024 suite on each approved physical reference device:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gridee.parking.macrobenchmark.MainTabsPerf024Benchmark
```

Run this exact suite against the pre-fix and fixed APK sources with the same device,
thermal state, orientation, and compilation modes. It records cold main-container
startup, frame timing and peak heap/RSS across the former prewarm window, plus the
first Bookings and Profile switch durations. Full Perfetto traces and JSON results
are written under
`macrobenchmark/build/outputs/connected_android_test_additional_output/`.

Wallet is deliberately excluded from this offline journey: its existing empty-state
animation is remotely hosted, so denying Internet makes that unrelated path unsuitable
for an offline timing sample. Record first/repeat Wallet switching separately with the
normal QA app and a non-production account; do not remove the benchmark APK's Internet
guard or treat the partial offline journey as the complete PERF-024 device gate.

Before collecting results, install/open the production scanner once on the test
device with network access so Google Play services can provision the OCR and
barcode modules. The benchmark APK itself is deliberately offline. A missing
`scanner_preview_ready` or `scanner_mode_switch` metric means the camera/model
precondition was not met; do not treat that run as a passing latency sample.
