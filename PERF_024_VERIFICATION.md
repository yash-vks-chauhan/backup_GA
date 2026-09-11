# PERF-024 verification — lazy main-tab creation

Date: 2026-09-10–11

Scope: Android frontend only

State: implementation complete; representative-device performance gate pending

## Implemented behavior

- Removed the three delayed startup callbacks that created Bookings, Wallet, and Profile views at approximately 450, 750, and 1,050 ms.
- Removed `prewarmInactiveFragment` and its synchronous hidden-fragment transaction.
- Kept PERF-022's stable-tag registry and process-restoration reconciliation intact.
- Kept tap, swipe, and explicit external navigation as the only paths that may create an inactive destination on demand.
- Added a source contract that fails if inactive Bookings, Wallet, or Profile tabs are requested from `onCreate`, or if the removed prewarming path returns.
- Added API 29+ async trace markers around the existing Bookings, Wallet, and Profile switch animations so representative-device latency can be extracted from the target app process.

No backend source, API contract, or network behavior was changed.

## Automated verification

From `Gridee_Android/android-app`:

```bash
./gradlew --no-daemon \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:bundleRelease \
  :app:lintVitalRelease \
  -Pkotlin.incremental=false \
  -Pkotlin.compiler.execution.strategy=in-process \
  --console=plain
```

Final result after adding race-safe target-process trace markers: `BUILD SUCCESSFUL` in 4m 25s.

- Unit tests: 397 tests across 74 suites; 0 failures, 0 errors, 0 skipped.
- Focused coverage: 2 `MainContainerRestorationContractTest` tests and 13 `RestorableTabFragmentRegistryRobolectricTest` tests.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`
- Release APK signature: APK Signature Scheme v2, one signer, `CN=Upload, O=Gridee, C=IN`.
- Release AAB: JAR signature verified; the expected locally managed upload certificate is self-signed and not timestamped.

Artifact SHA-256 values:

```text
f80595188adcf6bcaa8bd6f1d4fa96471e02a5e66b5e6c50c1dd159fcb83f5c3  app-debug.apk
bd9c850c856ae3920ecf7101affa7a9da316bbc9e88b57c538c730363e7582f5  app-release.apk
5b3fffa49785e8a4048fbe082c8bd30ff93b67ad545e29d5e34b1587f1c33664  app-release.aab
```

## Emulator UI smoke

Environment: `gridee_ads_qa_api34`, Android 14 / API 34, authenticated existing QA session.

- Fresh launch followed by an eight-second idle: 1 root tab, `gridee.main.tab.home`.
- First Bookings tap: 2 root tabs, Home plus `gridee.main.tab.bookings`.
- Subsequent Wallet and Profile taps created one stable-tagged destination each on demand.
- No `FATAL EXCEPTION` or app ANR was observed during the flow.

This is functional evidence only. Emulator timing must not be used to close the production performance gate.

## Benchmark harness dry-run

On 2026-09-11, both benchmark paths completed all 10 iterations on the API 34 emulator.
The cold-start path emitted TTID, frame, and peak-memory metrics and 10 Perfetto traces.
The offline-safe Bookings/Profile journey's generated JSON contains target-process
`perf024_first_bookings_switchFirstMs` and `perf024_first_profile_switchFirstMs` metrics,
plus frame and peak-memory metrics, and another 10 Perfetto traces were captured. The emulator
override was used only for this harness validation; its timing values are not production
evidence and are not used to close PERF-024.

## Remaining acceptance gate

The offline benchmark harness is implemented in
`macrobenchmark/src/main/java/com/gridee/parking/macrobenchmark/MainTabsPerf024Benchmark.kt`.
It launches the real main container through a benchmark-only alias, seeds a synthetic local
session through a benchmark-only receiver, and records startup timing, frame timing, peak
heap/RSS, Perfetto traces, and offline-safe first Bookings/Profile tab-switch durations. These
entry points are absent from debug/release APKs, while the benchmark APK has no Internet
permission and loopback-only API URLs. Wallet remains a separate normal-QA-app measurement
because its existing empty-state animation is remotely hosted; changing that unrelated path
or weakening the benchmark APK's network guard is outside PERF-024.

Run on each approved physical reference device:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gridee.parking.macrobenchmark.MainTabsPerf024Benchmark
```

On the approved low-, mid-, and high-tier physical devices, record matched before/after measurements for:

- startup trace and post-first-frame CPU;
- retained heap after the former 1,050 ms prewarm window;
- frame timing/jank during the former prewarm window;
- first Bookings, Wallet, and Profile tab-switch latency;
- repeat tab-switch latency after each destination has been created.

PERF-024 remains `IN PROGRESS` until those representative-device results are attached. PERF-025 is outside this change.
