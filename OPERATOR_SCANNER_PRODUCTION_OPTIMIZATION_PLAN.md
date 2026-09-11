# Operator Scanner Production Optimization Plan

**Product:** Gridee Android operator check-in/check-out scanner<br>
**Scope:** Android vehicle number-plate scanning, booking QR scanning, CameraX preview, scanner UI, Android networking, and a reference-only backend operator roadmap<br>
**Created:** 2026-08-27<br>
**Status:** Android implementation completed through batch 11 on 2026-09-04; physical real-camera, recorded-frame, signed-release, and production-like QA remain mandatory before rollout

> **Current execution boundary:** Android app changes only. Phases 7 and 8, the infrastructure
> portion of Phase 9, server dashboards, load testing, and every other backend item remain
> documented for later work but were not changed in this implementation.

## 1. Objective

Make operator check-in and check-out feel fast, smooth, reliable, and safe in production for both vehicle plates and booking QR codes.

The target experience is:

1. The scanner opens with a usable camera preview almost immediately.
2. The scan rectangle stays visually centred and does not move when status text changes.
3. The camera focuses and meters exposure on the same centre region shown to the operator.
4. QR codes and number plates are analyzed only in the intended centre region.
5. Recognition does not make the UI stutter.
6. A recognized value reaches the backend quickly.
7. The operator receives clear sound, haptic, and visual confirmation.
8. The scanner becomes ready for the next vehicle without an unnecessary delay.
9. No noisy OCR frame can cause a wrong check-in or check-out.
10. Network uncertainty never causes a mutation to be applied twice.

## 2. Important capacity clarification

Having more than 9,000 registered users does not directly slow the phone's camera or on-device ML Kit processing.

The important capacity factors are:

- The speed and thermal state of each operator device.
- Whether the ML Kit models are already installed.
- The amount of work performed for every camera frame.
- Network latency between the operator, API, and MongoDB.
- The size of the bookings collection and the efficiency of its queries.
- The number of operator scans happening concurrently.
- Whether the production API scales to zero or experiences cold starts.

Performance testing must therefore use realistic concurrent operator traffic and a production-sized bookings collection, not only a count of registered users.

## 3. Current implementation map

### Android scanner

- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/QrScannerActivity.kt`
  - Operator workflow/lifecycle orchestration around the extracted scanner components.
  - ZXing scanner for non-operator QR flows.
  - Scanner status UI, animations, sound, haptics, manual entry, and voice entry.
  - Operator check-in/check-out submission.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerCameraController.kt`
  - Shared CameraX session, resolution, raw ROI mapping, focus, torch, zoom, and exposure.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerInputImageFactory.kt`
  - Reusable stride-safe YUV420-to-cropped-NV21 ML input.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerCropMapping.kt`
  - Rotation-safe crop-local detector coordinates back to the original camera buffer.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/OperatorQrAnalyzer.kt`
  - QR-only ML Kit detector, auto-zoom, and fail-closed centre containment.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/VehiclePlateAnalyzer.kt`
  - OCR, bounded candidate ranking, Indian plate interpretation, and temporal consensus.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerStateMachine.kt`
  - Testable lifecycle transitions and transition telemetry.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerAutomaticRecognitionGate.kt`
  - Live Plate/QR automatic-recognition kill switch, generation invalidation, and final-commit guards.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerFrameReadinessGate.kt`
  - Session-scoped first-frame readiness and stale-frame rejection.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerViewportBindPolicy.kt`
  - Deferred viewport-aware bind/rebind decisions without unnecessary camera churn.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerCameraFallbackPolicy.kt`
  - Permission, unavailable-camera, runtime-watchdog, and manual-fallback decisions.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerDetectorRuntimeFailurePolicy.kt`
  - Bounded detector-failure handling and recovery instead of a silently dead scanner.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerUiRenderer.kt`
  - Deduplicated panel rendering and coordinated corner animation.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerLayoutCalculator.kt`
  - Deterministic centred-frame sizing, inset correction, and responsive portrait/landscape scanner chrome with LTR/RTL side-rail compensation.
- `Gridee_Android/android-app/app/src/main/res/layout/activity_qr_scanner.xml`
  - Camera preview, centre frame, controls, mode selector, and result panel.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/operator/OperatorViewModel.kt`
  - Mutation serialization, duplicate protection, retained operations, and terminal-result delivery.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/operator/OperatorTerminalDelivery.kt`
  - Single-consumer claim/ack ownership for retained terminal-result presentation across Activity recreation.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/operator/OperatorScanCoordinator.kt`
  - Process-wide active-operation lock and scan cooldown.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/repository/BookingRepository.kt`
  - Operator mutation repository calls and cache invalidation.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/api/ApiClient.kt`
  - OkHttp configuration, timeouts, authentication, and safe fallback rules.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/api/NetworkTimingEventListener.kt`
  - Privacy-safe non-overlapping network phase timings.
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerPerformanceTracker.kt`
  - Privacy-safe session, recognition, camera, detector, mutation, timeout, and jank telemetry.
- `Gridee_Android/android-app/macrobenchmark/`
  - Profileable startup and Plate ↔ QR first-frame Macrobenchmark harness.
- `Gridee_Android/android-app/app/build.gradle`
  - CameraX and ML Kit dependencies.
- `Gridee_Android/android-app/app/src/main/AndroidManifest.xml`
  - Camera permissions and ML Kit installation metadata.

### Backend

- `gridee_backend/src/main/java/com/parking/app/controller/operator/OperatorBookingController.java`
  - Operator-scoped check-in/check-out endpoints.
- `gridee_backend/src/main/java/com/parking/app/service/booking/BookingQueryService.java`
  - Booking lookup by QR, vehicle number, PIN, lot, spot, and status.
- `gridee_backend/src/main/java/com/parking/app/service/booking/CheckInService.java`
  - Check-in validation, locking, persistence, and event publication.
- `gridee_backend/src/main/java/com/parking/app/service/booking/CheckOutService.java`
  - Check-out validation, locking, persistence, and event publication.
- `gridee_backend/src/main/java/com/parking/app/config/MongoIndexConfig.java`
  - MongoDB indexes.
- `gridee_backend/src/main/java/com/parking/app/service/lock/MongoLockService.java`
  - Distributed mutation locking.

## 4. Existing decisions that should be preserved

The following current behavior is useful and should not be removed accidentally:

- `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` prevents camera-frame queues from growing.
- QR recognition is restricted to `FORMAT_QR_CODE`.
- Only one operator mutation is allowed to be active on a device at a time.
- Duplicate scans have a cooldown window.
- Android does not automatically retry ambiguous mutation requests.
- The scanner retains an in-flight operator operation across activity changes.
- Booking/spot cache refresh happens after success without delaying the immediate success result.
- Backend terminal side effects run asynchronously.
- Manual entry remains available as an operational fallback.

## 5. Production performance targets

These are release targets, not assumptions. They must be verified on real devices and production-like infrastructure.

| Stage | Target |
|---|---:|
| Warm scanner preview ready, p95 | Under 300–400 ms |
| Cold scanner preview ready, p95 | Under 700 ms |
| Clear booking QR recognition, p95 | Under 500 ms |
| Clear plate consensus, p95 | Under 1 second |
| Operator API response, p95 | Under 500 ms |
| Operator API response, p99 | Under 1 second |
| QR scan to confirmed mutation, p95 | Under 1.2 seconds |
| Plate scan to confirmed mutation, p95 | Under 1.5 seconds |
| Confirmed result to next-scan readiness | Under 1 second |
| Wrong automatic check-in/check-out | Zero tolerated |
| Duplicate mutation | Zero tolerated |

## 6. Work sequence overview

Follow this order. Do not begin by replacing the OCR model before the current latency is measured and the known integration problems are corrected.

| Phase | Work | Priority | Status |
|---|---|---|---|
| 0 | Baseline measurement and production safety controls | P0 | ANDROID IMPLEMENTED; dashboards/server timings pending |
| 1 | Authoritative centred scan region and camera focus | P0 | ANDROID IMPLEMENTED; device ROI matrix pending |
| 2 | Guaranteed ML Kit model availability | P0 | ANDROID IMPLEMENTED; fresh-install QA pending |
| 3 | Remove per-frame UI jank | P0 | ANDROID IMPLEMENTED; profileable-device QA pending |
| 4 | Faster repeat scanning and persistent camera session | P0 | ANDROID IMPLEMENTED; device QA pending |
| 5 | QR-specific speed and low-light improvements | P0 | ANDROID IMPLEMENTED; device benchmark pending |
| 6 | Safe, fast plate consensus and OCR tuning | P0 | ANDROID IMPLEMENTED; accuracy/resolution benchmark pending |
| 7 | Exact indexed backend lookup | P0 | BACKEND — EXCLUDED |
| 8 | Atomic and idempotent operator mutations | P1 | BACKEND — EXCLUDED |
| 9 | Network and deployment latency work | P1 | ANDROID IMPLEMENTED; infrastructure/idempotency excluded |
| 10 | CameraX upgrade | P1 | UPGRADED; multi-device compatibility QA pending |
| 11 | Dedicated on-device plate detector | P2 | BLOCKED on approved model/data/privacy/benchmark inputs |
| 12 | Device testing, load testing, and staged rollout | P0 | ANDROID EMULATOR PASS COMPLETE; external release gates pending |

---

### Android P0 batch 1 — completed 2026-08-28

- [x] Physically centre the adaptive plate/QR frame in the camera preview.
- [x] Share a CameraX viewport between preview and image analysis.
- [x] Replace manual rotation math with CameraX coordinate transforms.
- [x] Cache the preview transform/scan region and map the real ROI into each OCR/QR frame.
- [x] Focus and meter exposure through `PreviewView.meteringPointFactory`.
- [x] Request install-time delivery of the ML Kit OCR and barcode modules.
- [x] Move OCR candidate extraction and scoring away from the main thread.
- [x] Deduplicate and throttle high-frequency scanner status rendering.
- [x] Replace four per-frame corner animators with one transition-only animator.
- [x] Require two recent matching plate reads before automatic submission.
- [x] Reduce success hold to 850 ms and recoverable-error hold to 1.8 seconds.
- [x] Apply operator QR scanning only to the centre region and enable its torch control.
- [x] Add focused unit tests for plate consensus and UI update throttling.
- [x] Move the mode controls above the scan region so they never cover the centred plate/QR frame.
- [x] Prevent a stale torch visibility animation from hiding the QR torch after a mode switch.
- [x] Make ML Kit completion delivery safe while the camera executor is shutting down.
- [x] Reuse one CameraX Preview + ImageAnalysis session when switching Plate ↔ QR.

Still required before production rollout: a physical multi-device/real-camera ROI and recognition
matrix, fresh-install/offline dynamic-module testing, production telemetry dashboards, backend query
and idempotency work, recorded QR/plate benchmarks, load testing, and a staged signed release. The
Android feature controls and emergency kill switches are implemented.

### Physical-device pass 1 — Samsung SM-A546E, Android 16, 2026-08-28

Build tested: debug APK, 1080 × 2340 physical display, 420 dpi override.

- [x] Installed over the existing app and preserved the authenticated operator session.
- [x] Opened the production scanner flow after selecting the operator parking spot.
- [x] Verified both adaptive frames are physically centred at `(540, 1170)`.
- [x] Verified the top mode dock and bottom status card do not overlap either scan frame.
- [x] Repeated Plate ↔ QR switching without a black preview frame.
- [x] Verified torch availability and activation in both Plate and QR modes through camera service state.
- [x] Recreated the scanner twice through landscape/portrait rotation.
- [x] Repeated close/reopen while ML analysis was active.
- [x] Fixed and retested the executor-shutdown crash found during lifecycle stress.
- [x] Confirmed no fatal exception, executor rejection, camera exception, ANR, or process death after the fix.
- [x] Measured a 10-second steady preview: 609 frames, 0 modern janky frames, 0 missed vsync, and 0 slow UI-thread frames.
- [x] Recorded the pre-shared-session aggressive switch baseline: 911 frames and 79 modern janky frames (8.67%).
- [x] After sharing the camera session, verified both QR → Plate and Plate → QR were fully rendered at the 120 ms capture checkpoint.
- [x] Recorded the post-change switch stress run: 1,615 frames, 9 modern janky frames (0.56%), p95 15 ms, 2 missed vsync, and no black preview frame.
- [x] Confirmed the camera opened only once across the switch run; mode changes no longer close/reopen it.
- [x] Measured debug-wrapper scanner launch: 1.928–2.476 seconds cold and 314–459 ms warm in this test session. Add first-preview instrumentation before comparing these values with the product p95 targets.
- [ ] Decode a known physical QR target and record recognition latency; the generated QA QR was not in the phone camera's field of view.
- [ ] Run the full plate/QR sample matrix on at least the minimum supported low-, mid-, and high-tier devices.
- [ ] Measure a release/profileable build with Macrobenchmark and JankStats.
- [ ] Perform successful check-in/check-out mutation tests only with dedicated QA bookings; this pass intentionally avoided changing production booking state.

### Android P0 frontend batch 2 — completed 2026-08-29

- [x] Add a random scan session ID and structured per-attempt/session timing aggregation.
- [x] Measure camera bind/reuse, first preview, first frame, first analysis, first candidate, recognition, API, and total attempt time.
- [x] Count delivered, analyzed, completed, quality-skipped, low-light, and auto-zoom frames/actions.
- [x] Tag metrics with mode, Entry/Exit, app version, device model, Android API, and network type.
- [x] Respect the existing analytics privacy switch and emit Firebase events only in non-debug builds.
- [x] Exclude raw vehicle numbers, booking identifiers, and QR payloads from metrics and structured logs.
- [x] Enable ML Kit potential-barcode detection and bounded QR zoom suggestions.
- [x] Clamp QR zoom to both the device range and a 3× product maximum, with a 350 ms change cooldown.
- [x] Reset camera zoom when changing modes or starting the next scan attempt.
- [x] Add sustained luma-based low-light guidance and highlight the manual torch control.
- [x] Add tap-to-focus and quality-triggered AF/AE refresh at the exact centre ROI.
- [x] Replace deprecated fixed-resolution targeting with a 16:9 selector preferring 1280×720, then the closest supported size.
- [x] Reduce the normal plate vertical inset from 16% to 12% and analyze the full visible plate ROI every fourth frame for two-line/motorcycle plates.
- [x] Skip clearly unusable plate frames while forcing every third rejected frame through, preventing OCR starvation on unusual cameras.
- [x] Add focused unit coverage for timing aggregation, luma/detail sampling, low-light hysteresis, and frame usability gating.
- [x] Build the debug APK successfully and pass 14 isolated scanner tests.
- [ ] Install batch 2 on the Samsung device. The device currently contains the Google Play-signed split APK, which cannot be replaced by the local debug certificate without uninstalling and losing the operator session.

### Android P0 frontend batch 3 — completed 2026-08-29

- [x] Add explicit scanner readiness states: `PREPARING`, `READY`, and `UNAVAILABLE`.
- [x] Check both installed OCR and QR optional modules before starting the CameraX analyzer.
- [x] Request missing modules through Google Play services and ignore stale asynchronous callbacks after retry.
- [x] Suppress the normal scan-line animation while models are preparing or unavailable.
- [x] Show clear first-launch/offline guidance with Retry setup and manual vehicle entry.
- [x] Load scanner safety/performance controls from the existing cached Android app config without adding a backend endpoint or typed backend contract.
- [x] Clamp every configurable numeric value to a locally safe range and fall back to tested defaults when values are absent or invalid.
- [x] Add remote-capable controls for success/error result holds, clean/corrected consensus, consensus gap, 540p/720p analysis, QR auto-zoom, and forced plate confirmation.
- [x] Preserve the two-frame fast path for clean plates and require three recent matches by default when OCR character correction was necessary.
- [x] Penalize corrected OCR candidates during ranking and treat lookalike substitutions as uncertain.
- [x] Require one-tap operator confirmation before submitting an OCR-corrected plate; provide Scan again and Enter number alternatives.
- [x] Add the `operatorScannerPlateAutoSubmit` kill switch so disabling auto-submit forces confirmation for every scanned plate.
- [x] Record focus request/success/failure, model readiness/init failure, manual fallback, uncertain confirmation outcome, success rendering, and next-scan-ready timing without scan contents.
- [x] Compile the Android debug sources successfully.
- [x] Pass 24 isolated scanner unit tests, including readiness retry/stale callback, feature-control clamping, stronger consensus, and UI-stage telemetry.
- [ ] Run batch 3 on a physical device. At verification time ADB had no connected device; installation must also use a Play-signed internal build to preserve the existing production operator session.

Scanner config keys supported by this batch (all optional):

- `customSettings.operatorScanner.successResultHoldMs` (clamped to 600–1500 ms)
- `customSettings.operatorScanner.errorResultHoldMs` (clamped to 1000–5000 ms)
- `customSettings.operatorScanner.cleanPlateConsensusMatches` (clamped to 2–3)
- `customSettings.operatorScanner.correctedPlateConsensusMatches` (clamped to 3–5)
- `customSettings.operatorScanner.plateConsensusMaxGapMs` (clamped to 500–1500 ms)
- `customSettings.operatorScanner.analysisResolution` (`640x480`/`480p`/`minimum`, `960x540`/`540p`/`low`, otherwise safe 1280×720)
- `customSettings.operatorScanner.plateInitialZoomRatio` (clamped to 1.0×–1.35×; default 1.12×)
- `featureToggleMap.operatorScannerQrAutoZoom` (default `true`)
- `featureToggleMap.operatorScannerPlateAutoSubmit` (default `true`; set `false` for the emergency manual-confirmation kill switch)
- `featureToggleMap.operatorScannerAutomaticRecognition` (default `true`; set `false` to stop new automatic Plate and QR recognition while preserving manual plate entry and any retained mutation)
- `featureToggleMap.operatorScannerReflectivePlateExposureCompensation` (default `true`)

The scanner polls the existing Android app-config source every 60 seconds while resumed. The
repository cache is bounded to five minutes. Safety-relevant changes invalidate older analyzer
generations before they can commit, while an already-started operator mutation is never cancelled or
replaced by a config refresh.

### Android P0 frontend batch 4 — completed 2026-08-29

- [x] Stop automatically dismissing backend, network, authorization, scope, and other terminal operator-mutation failures.
- [x] Keep a terminal mutation failure visible until the operator explicitly chooses **Scan again** or **Enter number**.
- [x] Keep recognition paused while a terminal failure is awaiting operator action, preventing an accidental automatic resubmission.
- [x] Preserve the blocking error panel through Android Activity recreation/rotation using saved instance state.
- [x] Clear the blocking error only when the operator selects a recovery action or a later operation succeeds.
- [x] Continue using the bounded configurable hold only for local recoverable scan/validation failures.
- [x] Preserve the existing process-wide seven-second duplicate-scan cooldown; its operation/spot-change behavior already has focused unit coverage.
- [x] Add privacy-safe `error_awaiting_action` UI-stage telemetry.
- [x] Build the debug APK successfully and pass 25 isolated scanner tests.
- [ ] Verify the persistent error actions and rotation behavior during the deferred physical-device pass.

### Android P0 frontend batch 5 — completed 2026-08-29

- [x] Remove the per-update `ScannerPanelSnapshot` allocation from the high-frequency status-render path.
- [x] Preserve semantic render deduplication with structured primitive/string fields instead of an allocated key object.
- [x] Keep the existing 250 ms transient-update throttle while allowing processing, success, error, and voice states through immediately.
- [x] Cache scanner-panel drawable, icon, and resolved-color values so identical resources are not reassigned on repeated renders.
- [x] Skip identical badge, title, subtitle, meta, progress, action, and manual-fallback View mutations.
- [x] Add a fast path for already-clean status messages, avoiding sequence/regex work for the normal single-line case.
- [x] Avoid allocating a new uppercase badge string when the badge is already uppercase.
- [x] Add focused tests for structured UI-state changes, visual-field independence, deduplication, and cache reset.
- [x] Build the debug APK successfully and pass 29 isolated scanner unit tests.
- [x] Keep recognition, CameraX behavior, Android API/repository calls, and all backend code unchanged.
- [ ] Confirm the allocation/frame-time improvement with JankStats in the deferred profileable physical-device pass.

### Android scanner batch 6 — authoritative ROI and plate/QR pipeline, completed 2026-08-31

- [x] Add a dedicated full-preview camera stage so status text and controls cannot move the frame.
- [x] Correct both horizontal and vertical centring for asymmetric cutout/navigation insets.
- [x] Crop the CameraX YUV frame to the mapped centre ROI and copy it into a reusable NV21 buffer, ensuring ML Kit analyzes the same pixels shown to the operator.
- [x] Preserve padded and interleaved YUV plane strides and enforce even YUV420 chroma alignment.
- [x] Map crop-local rotated detector bounds back through the original CameraX viewport to preview coordinates and require at least 70% QR containment in the centre square.
- [x] Replace whole-text-first plate selection with a bounded, line-first spatial/format/size/correction/consistency ranker.
- [x] Keep no more than 12 ranked candidates per frame and precompute plate templates.
- [x] Move temporal plate consensus into `VehiclePlateAnalyzer`.
- [x] Require operator confirmation for corrected plates and valid-but-weak legacy plate shapes; only strong uncorrected candidates may auto-submit.
- [x] Add the guarded 640×480 analysis option, hardware-clamped configurable plate zoom, and reflective-plate exposure compensation.
- [x] Add deterministic YUV-copy, all-rotation crop mapping, QR-containment, frame-ranking, layout, format, correction, and normalization-benchmark tests.

### Android scanner batch 7 — network, observability, and dependency hardening, completed 2026-08-31

- [x] Block operator mutations before request creation when Android reports no validated internet connection.
- [x] Show a slow-network processing warning for best-effort degraded-link detection without creating an unsafe local retry.
- [x] Add privacy-safe OkHttp phase timings for DNS, TCP connect, TLS, upload, server wait, download, and total time.
- [x] End TCP timing at TLS start for HTTPS so connect and TLS durations do not overlap; accumulate failed connection attempts safely.
- [x] Emit only fixed endpoint categories and never URL paths, IDs, vehicle numbers, or QR payloads.
- [x] Preserve the existing OkHttp connection pool and keep automatic mutation replay disabled.
- [x] Keep the active-operation lock after a presentation timeout until the retained request actually completes.
- [x] Integrate JankStats with scanner lifecycle/mode/operation state for the deferred profileable-device run.
- [x] Align all CameraX artifacts at 1.6.1 and add AndroidX Metrics Performance 1.0.0.

### Android scanner batch 8 — architecture and final local verification, completed 2026-08-31

- [x] Extract `ScannerCameraController`, `OperatorQrAnalyzer`, `VehiclePlateAnalyzer`, `ScannerStateMachine`, `ScannerUiRenderer`, and process-wide `OperatorScanCoordinator` components.
- [x] Keep backend-dependent operation IDs/reconciliation out of Android until an idempotency contract exists.
- [x] Add focused state-machine, network-preflight, network-phase, layout/inset, candidate-policy, and coordinator tests.
- [x] Compile the batch-8 Android scanner sources and assemble the debug APK (40,720,485 bytes).
- [x] Run the complete debug unit suite successfully at the batch-8 checkpoint: 170 tests, 0 failures, 0 errors, 0 skipped.
- [x] Run the independent plate-normalization benchmark: 4,939 ns average over 10,000 iterations in the final JVM run (ML inference intentionally excluded).
- [x] Confirm the refreshed scanner scope has no Android Lint errors; two non-blocking layout warnings remain (camera-stage overdraw and a conservative wrapper).
- [ ] Resolve or formally baseline the repository-wide legacy Lint backlog separately. This batch checkpoint was superseded by the authoritative final result below.
- [ ] Install and execute the final build on a physical device. No physical device was connected on 2026-09-01; the API 34 emulator pass below does not replace camera-hardware QA.
- [ ] Execute the rotation/aspect-fill ROI instrumentation matrix on hardware; CameraX transform behavior cannot be certified by local JVM tests alone.
- [ ] Run the approved recorded-frame accuracy/resolution suite and the profileable long-run test.

### Android scanner batch 9 — responsive landscape and emulator QA, completed 2026-09-01

- [x] Reproduce and fix the landscape defect where the status card covered the operation/input controls and centre scan target.
- [x] Keep the ROI on the immutable physical preview centre while moving the spot/mode controls into a left landscape rail and a compact status/action card into a right rail.
- [x] Make the landscape spot selector fit its available rail and stack narrow result actions vertically, while restoring the original portrait dimensions and horizontal actions.
- [x] Merge system-bar and display-cutout insets, compensate the rail next to the translated frame for both LTR and RTL layouts, and translate the plate hint with the frame.
- [x] Add deterministic landscape/portrait chrome-selection and LTR/RTL rail-compensation regression tests.
- [x] Build, install, and execute the final debug APK on the Android 14 / API 34 arm64 emulator at 1080 × 2400 and 420 dpi.
- [x] Verify with UI geometry that the portrait QR frame is `[187,847]–[893,1553]`, centred at `(540,1200)`, and the landscape QR frame is `[780,120]–[1620,960]`, centred at `(1200,540)`.
- [x] Visually verify Plate and QR in portrait and landscape; the controls/status card remain outside both centre targets and the selected Entry/Exit/Input states render correctly.
- [x] Complete 50 rapid Plate ↔ QR changes without a black preview, app crash, ANR, executor rejection, illegal state, or camera reopen/close during switching.
- [x] Complete 20 alternating Entry ↔ Exit changes and verify the final `EXIT` segment and status badge without invoking a backend mutation.
- [x] Complete three portrait ↔ landscape cycles, five Home ↔ foreground cycles, and three full scanner close/reopen cycles with no app/CameraX lifecycle error.
- [x] Open and dismiss the manual-entry sheet without submission; no production or backend data was changed.
- [x] Run the batch-9 complete debug unit suite: 174 tests, 0 failures, 0 errors, 0 skipped; assemble `app-debug.apk` successfully at 40,720,485 bytes. The authoritative final checkpoint below is 258 tests.
- [x] Re-run Android Lint at the batch-9 checkpoint. The authoritative final Lint result is recorded below.
- [ ] Treat emulator timing/jank as non-authoritative: SwiftShader was heavily throttled and System UI itself produced one responsiveness dialog. Use the Play-signed profileable physical-device run for release latency and frame-time gates.
- [ ] Complete real-camera QR/plate recognition, torch, focus, exposure, auto-zoom, cutout/device matrix, lock/unlock, and long-run thermal testing on the deferred physical-device queue below.

### Android scanner batch 10 — production controls and fail-safe recovery, completed 2026-09-04

- [x] Add `operatorScannerAutomaticRecognition`, a single Android-side emergency switch for new automatic Plate and QR recognition; manual plate entry remains available.
- [x] Refresh scanner controls every 60 seconds from the existing app-config source with a maximum five-minute repository cache age, without adding or changing a backend contract.
- [x] Invalidate in-flight analyzer generations when safety controls or camera configuration change so a stale Plate/QR callback cannot commit after disable, re-enable, mode change, or rebind.
- [x] Preserve any operator mutation already in progress when the recognition switch changes; remote config never cancels, retries, or replaces a mutation.
- [x] Gate analysis on a valid viewport and a delivered frame rather than treating successful CameraX binding as proof that the preview is usable.
- [x] Add explicit permission-denied, no-camera, critical-camera-error, first-preview-timeout, and repeated-detector-failure fallbacks with retry/manual actions.
- [x] Add a bounded CameraX first-preview watchdog and ensure recoverable CameraX transitions are allowed to recover before showing a terminal fallback.
- [x] Restore analysis after manual-entry/confirmation sheets close, while keeping the camera released for a genuinely unavailable scanner or disabled automatic recognition.
- [x] Restore and validate the selected operator spot and operation/input mode without allowing a stale spot or mode to cross an operation boundary.
- [x] Make compact portrait, landscape, small-height, large-font, LTR, and RTL layouts keep the centred target and recovery actions usable; tiny unsupported viewports show an explicit fallback instead of a clipped scanner.
- [x] Localize new scanner status, fallback, recovery, and cooldown copy in the base, Hindi, Tamil, Telugu, Malayalam, and Bangla resource sets; use quantity-aware plurals for cooldown seconds.
- [x] Add screen-reader semantics for mode/operation selections, scanner status, tap-to-focus, torch state, and decorative elements.

### Android scanner batch 11 — lifecycle, mutation, and race hardening, completed 2026-09-04

- [x] Give each recognition session/frame an identity and revalidate lifecycle, viewport, detector, mode, generation, blocking UI, and mutation state immediately before any automatic commit.
- [x] Reject late OCR/QR callbacks and camera side effects from an old session after Plate ↔ QR switching, backgrounding, rotation, scanner close, config invalidation, or operation start.
- [x] Retain an active operator mutation in `OperatorViewModel` across Activity recreation/backgrounding and keep the process-wide coordinator locked until the repository call actually terminates.
- [x] Treat the 12-second UI timeout as presentation only: it does not cancel the request, release the mutation lock, allow a second request, or overwrite a later authoritative result.
- [x] Retain terminal success/error without wall-clock expiry until a visible Activity claims and explicitly acknowledges it; release the claim during Activity hand-off so rotation/background return can present it.
- [x] Prevent a completed request from producing a later false timeout and prevent timeout/background/rotation paths from leaving the scanner in a permanent loading state.
- [x] Keep terminal backend/network/authorization/scope errors visible until operator action, while local recoverable recognition errors still use the bounded result hold.
- [x] Remember successful booking Plate and QR aliases in the local cooldown so switching input type cannot immediately resubmit the same booking.
- [x] Reject non-finite remote numeric values (`NaN`/infinity), clamp all scanner configuration to tested local ranges, and preserve safe defaults.
- [x] Expand privacy-safe telemetry for first-frame readiness, camera/detector failures, feature availability, mode-switch timing, state transitions, mutation presentation/timeout, and jank without scan payloads or booking identifiers.

### Final Android build, benchmark, and emulator validation — 2026-09-04

- [x] Add a profileable `benchmark` app variant, benchmark-only readiness marker, and separate AndroidX Macrobenchmark module for scanner startup and Plate ↔ QR first-frame timing.
- [x] Run the full local command `:app:testDebugUnitTest :app:assembleDebug :app:assembleBenchmark :macrobenchmark:assembleBenchmark`: 132 tasks completed successfully in 2 minutes 49 seconds; the identical final post-layout-fix aggregate passed again incrementally in 9 seconds.
- [x] Pass all 49 debug unit-test suites: **258 tests, 0 failures, 0 errors, 0 skipped**.
- [x] Produce installable debug, profileable benchmark, and Macrobenchmark APKs: `app-debug.apk` (~39 MiB), `app-benchmark.apk` (~28 MiB), and `macrobenchmark-benchmark.apk` (~44 MiB), under their standard `build/outputs/apk/` directories. These are validation artifacts, not the Play-signed release build.
- [x] Record final SHA-256 values: debug `d41655d4fbb79af5247a16e8c42e9d29f9387bb327f1cb46392a961adf9698ba`; benchmark `fd3c72b548ee2889e29c15fd192a65705e29587eccf4fdc54d28f2841ec02442`; Macrobenchmark `21c6c85dcb4473c8b1eea1c640ee59b6b0712902d62da1aa04ce3e43b13a6ffa`.
- [x] Rebuild and install after the final compact recovery-action layout adjustment; the follow-up build passed in 1 minute 35 seconds.
- [x] Run final Android Lint: **318 errors and 1,728 warnings repository-wide**. The changed scanner scope has **0 errors** and one pre-existing non-blocking `MergeRootFrame` warning in `activity_qr_scanner.xml`; the global failure is unrelated legacy repository debt.
- [x] Validate scanner resource XML and base/Hindi/Tamil/Telugu/Malayalam/Bangla key parity, including the cooldown plurals; run `git diff --check` successfully.
- [x] Verify the native emulator target at `(540,1200)` in portrait and approximately `(1200.5,540)` in landscape; verify explicit tiny-viewport fallback, permission denial/recovery, five background/foreground cycles, Hindi at 1.3× font scale, and RTL layout.
- [x] Verify the smallest supported compact portrait at 720×1280 / 320 dpi (360×640 dp): the Plate ROI remained centred at `[210,544]–[510,736]`; the `Scanner setup needed` status scrolled to fully expose both recovery actions at `[84,1012]–[636,1128]`; **Enter Number** opened the inline manual sheet and its Cancel action remained visible, with no crash or ANR.
- [x] Complete 20 Entry ↔ Exit emulator taps in 2.938 seconds (about 146 ms per ADB-driven action including automation overhead), ending in the correct operation state with no app crash or ANR.
- [x] Attempt the connected Macrobenchmark with the emulator-error suppression required for an AVD; classify it as **environment-blocked and non-authoritative**, not as a product failure or a passed latency gate. The AVD produced a null UiAutomator accessibility root, had no usable virtual-camera feed, and could not provide the Google Play services optional OCR/barcode modules. The harness assembles and its readiness policies are unit-tested.
- [ ] Run the measured Macrobenchmark journey on a physical profileable device with a real camera and available OCR/barcode modules.

### Final Android validation boundary

The Android implementation and local/emulator verification are complete. This is not yet a
production certification. No emulator result proves real-camera recognition accuracy, focus,
exposure, zoom, thermal stability, dynamic-module first-use reliability, or end-to-end backend
latency. No backend source or contract was changed during these batches.

### Remaining external release gates

- [ ] Publish/install a Play-signed internal-testing build and repeat the supported physical-device, Android-version, cutout, orientation, large-font, LTR/RTL, keyboard, and smallest-viewport matrix.
- [ ] Certify real-camera Plate/QR recognition, ROI mapping, focus, exposure, torch, auto-zoom, glare/low-light behavior, motion handling, and false-positive safety against approved targets.
- [ ] Run the approved recorded-frame accuracy/resolution suite and select 480p/540p/720p only from measured exact-read, latency, and thermal results.
- [ ] Verify fresh install, offline-first launch, slow module download, retry, restart, and upgrade for the dynamic OCR/barcode modules.
- [ ] Run the startup/mode-switch Macrobenchmark and JankStats journey on low-, mid-, and high-tier physical profileable devices, including a 30-minute thermal/memory soak.
- [ ] With dedicated QA bookings only, verify Entry/Exit, slow response, timeout, background, rotation, Activity close/reopen, result-after-background, network loss, authorization/scope errors, cooldown aliases, and retained terminal delivery without loss or competing Activity consumers.
- [ ] Close or formally baseline the repository-wide Lint debt, produce the release-signed artifact, define production telemetry/alert thresholds, exercise both Android kill switches, and complete staged internal/lot/5%/25%/50%/100% rollout gates.
- [ ] Complete the separately owned backend exact-query/index, atomic idempotency/reconciliation, infrastructure, dashboard, and load-test roadmap before claiming end-to-end mutation guarantees. These items remain deferred and out of this Android-only change set.

### Deferred Android physical-device QA queue

**Decision:** Continue the remaining Android frontend implementation now. Run this queue before any production rollout, using a Play-signed internal-testing build so the installed operator session is not lost.

#### Build and setup

- [ ] Produce a Play-signed internal-testing build with a version code above the current production release.
- [ ] Install/update through Google Play internal testing without uninstalling the production-signed app.
- [ ] Confirm the authenticated operator, assigned lot, selected spot, Entry/Exit mode, and analytics privacy setting are preserved.
- [ ] Prepare dedicated QA bookings for successful check-in/check-out tests; never mutate an uncontrolled production booking.
- [ ] Capture device model, Android version, display size/density, build version, network type, and test time.

#### Model readiness and offline behavior

- [ ] Test a fresh install/device with no previously downloaded OCR or barcode modules.
- [ ] Confirm **Preparing scanner** appears without the normal scan-line animation.
- [ ] Test module preparation on Wi-Fi and slow mobile data.
- [ ] Test first launch in airplane mode and confirm **Scanner setup needed**, **Retry setup**, and manual entry.
- [ ] Restore connectivity, retry setup, and confirm transition to a working `READY` scanner.
- [ ] Restart the device and update the app, then confirm readiness still works.

#### Layout, camera, and switching

- [ ] Verify the plate rectangle and QR square remain physically centred in portrait and landscape.
- [ ] Confirm the top controls, status panel, system bars, keyboard, and error actions never cover the centre ROI.
- [ ] Repeat Plate ↔ QR switching at least 50 times; every switch must settle within 1–2 seconds with no black preview.
- [ ] Repeat Entry ↔ Exit switching and verify the active operation label and API action match.
- [ ] Verify torch, low-light guidance, QR auto-zoom, tap-to-focus, and focus recovery.
- [ ] Background/foreground, rotate, close/reopen, lock/unlock, and run lifecycle stress while analysis is active.

#### Recognition and safety

- [ ] Scan known clear front/rear, commercial, two-wheeler, BH, temporary, vintage, and legacy plates.
- [ ] Test glare, darkness, dirt, tilt, motion blur, distant plates, multiple plates, and non-plate text.
- [ ] Confirm a clean plate requires the configured two matching frames and remains within the latency target.
- [ ] Confirm an OCR-corrected plate requires the configured stronger agreement and one-tap confirmation.
- [ ] Confirm no corrected/uncertain plate reaches the mutation API before operator confirmation.
- [ ] Disable `operatorScannerPlateAutoSubmit` in config and verify every plate requires confirmation.
- [ ] Disable `operatorScannerAutomaticRecognition` while each automatic mode is active; verify old callbacks cannot commit, the camera/analyzers stop safely, manual plate entry remains available, and re-enable starts a fresh session.
- [ ] Test QR codes on paper and phone screens at low/high brightness, near/far, and with multiple codes around the ROI.
- [ ] Confirm only the QR inside the centre square can submit.

#### Results, errors, and repeat scanning

- [ ] Confirm success sound, haptic, visual state, displayed spot/time, and next-scan readiness under one second.
- [ ] Confirm local unreadable/blank scan errors auto-recover after the configured error hold.
- [ ] Confirm backend/network/authorization/scope errors remain visible until operator action.
- [ ] Rotate and background/foreground while a terminal error is visible; the blocking error must remain.
- [ ] Complete a request while backgrounded and across rotation; its terminal result must remain claimable without expiry until the visible Activity acknowledges it, must never be claimed by competing Activity instances, and must not replay after acknowledgement.
- [ ] Test **Scan again** and **Enter number** from a persistent error.
- [ ] Simulate a request taking longer than 12 seconds and confirm the app continues waiting without permitting another mutation.
- [ ] Complete that delayed request after the presentation timeout and confirm its authoritative result replaces waiting state with no false second timeout or permanent loading lock.
- [ ] Confirm rapid duplicate scans, mode changes, or spot changes cannot bypass the seven-second cooldown.
- [ ] Perform successful Entry and Exit only with dedicated QA bookings and verify exactly one mutation occurs.

#### Performance and observability

- [ ] Record cold/warm preview, first frame, first analysis, first candidate, recognition, API, success render, and next-ready timings.
- [ ] Capture `ScannerMetrics` and confirm focus success/failure, model readiness, manual fallback, confirmation, and persistent-error events.
- [ ] Confirm no plate value, QR payload, booking ID, or other scan content appears in metrics/logs.
- [ ] Record Android frame metrics/JankStats for steady scanning and switch stress.
- [ ] Run at least 30 minutes of repeat scans and check crashes, ANRs, camera failures, thermal throttling, and memory growth.
- [ ] Complete the low-, mid-, and high-tier device matrix before staged rollout.
- [ ] Write measured results and any discovered defects back into this file before closing the QA queue.

---

## 7. Phase 0 — Establish a measured baseline

### Goal

Separate camera startup, recognition, UI, network, and backend latency before changing behavior.

### Implementation checklist

- [x] Assign one scan session ID when the scanner activity opens.
- [x] Record scanner activity creation time.
- [x] Record when CameraX is bound.
- [x] Record when the first preview frame is visible.
- [x] Record the start and end time of each OCR/barcode task.
- [x] Record the first candidate time.
- [x] Record the accepted candidate/QR time.
- [x] Record API request start and completion.
- [ ] Record server query, validation, lock, update, and response timings.
- [x] Record success rendering and next-scan-ready time.
- [x] Record delivered-frame, analysis, completion, and app-quality-skip counts; CameraX continues to use keep-only-latest backpressure.
- [x] Tag timings with scan mode, entry/exit mode, app version, device model, Android version, and network type.
- [x] Never send a raw vehicle number or QR payload to analytics.
- [x] Use a random non-business session ID; do not transmit booking or vehicle identifiers.
- [ ] Create p50, p95, and p99 dashboards for each stage.

### Safety controls

- [x] Add remote configuration/feature flags for result hold time.
- [x] Add a feature flag for plate consensus rules.
- [x] Add a feature flag for analysis resolution.
- [x] Add a feature flag for QR auto-zoom.
- [x] Add a kill switch that forces manual confirmation if automatic plate confidence becomes unsafe.
- [x] Add a live emergency switch for all new automatic Plate/QR recognition, with stale-work invalidation and manual plate fallback.
- [x] Refresh controls while the resumed scanner is open without cancelling an already-started mutation.

### Acceptance criteria

- A single trace clearly shows where every slow scan spent its time.
- Production dashboards can distinguish recognition delay from API delay.
- No sensitive scan content appears in logs or analytics.

---

## 8. Phase 1 — Make the centre frame authoritative

### Original problem — resolved on Android

The original frame was centred inside the space between the controls rather than the physical
preview, its position could move with status-panel height, Plate mapping did not fully account for
`PreviewView` scale/crop, and operator QR analyzed the whole image. Focus could therefore target a
different sensor region from the visible box.

The implemented Android path now keeps an inset-corrected target on the immutable preview centre,
uses a shared CameraX viewport and cached transforms, crops both analyzers to that ROI, maps detector
bounds back to preview space, and meters focus/exposure through the `PreviewView` factory. Hardware
rotation/aspect-fill certification remains in the physical-device gate.

### Layout work

- [x] Add a dedicated full-preview scanner stage container.
- [x] Constrain the scan frame directly to the horizontal and vertical centre of the preview.
- [x] Keep the frame independent of status messages and bottom actions.
- [x] Use a centred square for QR mode.
- [x] Use a centred horizontal rectangle for plate mode.
- [x] Keep frame dimensions adaptive, but keep its centre point stable.
- [x] Verify on the API 34 emulator that the frame does not jump when changing Entry/Exit or Plate/QR mode; retain the physical-device matrix below.
- [x] Verify one- and two-line status messages do not move the frame.
- [x] Verify gesture/navigation/status-bar inset centring on the API 34 emulator and cover asymmetric/cutout correction deterministically; retain multi-device cutout QA below.

### Camera transformation work

- [x] Build `Preview` and `ImageAnalysis` with a shared CameraX `ViewPort`/`UseCaseGroup`, or migrate to `CameraController` with `MlKitAnalyzer`.
- [x] Use CameraX-provided transformation information instead of manually normalizing preview coordinates.
- [x] Calculate and cache the preview-space ROI after layout/transformation changes.
- [x] Cache that ROI; do not call view-location APIs from the camera executor for every frame.
- [x] Recalculate the ROI on rotation, size, inset, or mode changes.
- [x] Reject invalid or zero-sized transforms without stalling the analyzer.

### Focus and exposure work

- [x] Use `vehiclePreview.meteringPointFactory` for a point originating in `PreviewView` coordinates.
- [x] Focus and meter on the exact centre of the visible scan box.
- [x] Include AF and AE; add AWB only where supported and useful.
- [x] Observe the focus future and collect focus success/failure metrics.
- [x] Add tap-to-focus inside the scan stage as an operator fallback.

### QR acceptance work

- [x] Stop configuring the hidden ZXing framing rectangle for operator CameraX QR mode.
- [x] Transform QR bounding boxes into preview coordinates.
- [x] Accept only QR codes whose bounding box is contained in the centre square by at least 70%.
- [x] Ignore QR codes visible outside the operator frame.

### Acceptance criteria

- The frame centre is stable across supported screen sizes.
- A test grid placed in the visible frame maps to the intended analysis pixels for 0°, 90°, 180°, and 270° rotations.
- AF/AE visually targets the centre frame.
- A QR outside the centre square is not accepted.
- A QR inside the centre square is accepted.

---

## 9. Phase 2 — Guarantee ML model availability

### Original problem — resolved on Android

The app uses these Google Play services dependencies:

- `play-services-mlkit-text-recognition`
- `play-services-mlkit-barcode-scanning`

These modules are dynamically supplied by Google Play services. Originally, the manifest did not
request install-time delivery and scanning could start before a detector was available. The manifest
now requests `ocr,barcode`, and the readiness controller checks/requests the selected module before
analysis, shows `PREPARING`/`UNAVAILABLE`, and provides Retry/manual fallback instead of appearing to
hang. Fresh-install, offline-first, restart, and upgrade behavior still require the physical release
matrix.

### Implemented product decision

The universal app keeps the dynamic Google Play services modules to control app size, with install-time
metadata plus an explicit runtime availability/request gate. A bundled operator flavour is a
contingency only if measured fresh-install reliability misses the production gate.

#### Contingency — operator-specific bundled models

- Bundle OCR and barcode models in an operator build/flavour.
- Accept the app-size increase for predictable offline and first-use behavior.
- Keep consumer-only builds smaller if needed.

#### Implemented — install-time request plus runtime readiness

- Add `com.google.mlkit.vision.DEPENDENCIES` with `ocr,barcode` to the application manifest.
- Check module availability before enabling the scanner.
- Request installation explicitly when unavailable.
- Display “Preparing scanner” until both required models are ready.

### Implementation checklist

- [x] Record a product decision for bundled versus dynamically installed models: keep the current Google Play services dynamic modules for the universal build, require explicit readiness/install state, and reconsider a bundled operator flavour only if fresh-install field measurements miss the gate-reliability target.
- [x] Add the chosen dependencies/manifest configuration.
- [x] Add scanner readiness state: `PREPARING`, `READY`, `UNAVAILABLE`.
- [x] Do not show the normal scan animation while the required detector is unavailable.
- [x] Provide retry and manual entry if model installation fails.
- [ ] Test fresh install with no previous ML Kit modules.
- [ ] Test first launch on slow mobile data.
- [ ] Test offline first launch.
- [ ] Test upgrade from the current production app.

### Acceptance criteria

- A fresh production installation never silently scans with an unavailable model.
- The operator always sees whether the scanner is preparing, ready, or unavailable.
- The selected production configuration works after device restart and app update.

---

## 10. Phase 3 — Remove per-frame UI jank

### Original problem — resolved on Android

OCR callbacks originally rerendered status when no candidate was clear or the best guess had not
changed. A render could replace visual resources and start four corner animators, creating avoidable
main-thread work while ML was active.

The implemented renderer now uses immutable semantic state, structured deduplication, cached visual
resources, a 250 ms transient-update gate, background candidate work, and one transition-only corner
animation. Profileable low-end hardware still owns the release jank/thermal acceptance gate.

### Implementation checklist

- [x] Introduce an immutable scanner UI state model.
- [x] Render only when the meaningful state has changed.
- [x] Do not rerender an identical title, subtitle, candidate, or mode.
- [x] Conflate high-frequency OCR updates.
- [x] Limit best-guess/warning UI refresh to approximately four updates per second.
- [x] Move candidate extraction, correction, validation, and scoring off the main thread.
- [x] Post only the final display state to the main thread.
- [x] Use one coordinated corner animation rather than four new animators per frame.
- [x] Animate only transitions such as scanning → detected → success/error.
- [x] Do not animate repeated scanning or warning frames.
- [x] Avoid resetting identical drawables and image resources.
- [x] Integrate Android JankStats with scanner state; capture the final profileable-device trace in the deferred QA queue.

### Acceptance criteria

- Scanner main-thread slow frames remain within the agreed threshold on low-end devices.
- Repeated frames with the same candidate cause no new UI render.
- Preview animation stays smooth while OCR is active.
- No analyzer callback reads or mutates Android views from a background thread.

---

## 11. Phase 4 — Make repeat scanning fast

### Original problem

The scanner deliberately held success/error results for three seconds. Mode switches and some manual-entry transitions also unbound and rebuilt CameraX use cases.

### Result timing work

- [x] Reduce successful-result hold from 3 seconds to a remotely configurable 850 ms default (clamped to 600–1,500 ms).
- [x] Keep clear sound and haptic confirmation.
- [x] Use a longer 1.8-second default hold for recoverable errors (clamped to 1–5 seconds).
- [x] Keep serious errors visible until operator action.
- [x] Preserve the duplicate-scan cooldown.
- [ ] Show a subtle recent-success history row if operators need reassurance after the short hold.

### Persistent camera work

- [x] Bind one `Preview + ImageAnalysis` session for the scanner activity.
- [x] Switch the active detector/analyzer behavior without `unbindAll()` when changing Plate/QR mode.
- [x] Keep preview bound while a network operation is in progress.
- [x] Pause expensive recognition through state flags while preserving the live preview.
- [x] Resume analysis immediately after the result hold.
- [x] Avoid rebuilding the camera after each successful scan.
- [x] Rebind only after lifecycle loss, camera failure, or a configuration that genuinely requires it.

### Acceptance criteria

- A second vehicle can be scanned within one second after the first confirmed result.
- Plate/QR mode changes do not show a black preview flash on supported devices.
- No duplicate operation is submitted during fast resume.

---

## 12. Phase 5 — Optimize QR scanning

### Implementation checklist

- [x] Analyze only QR format.
- [x] Apply the authoritative centre-square rule from Phase 1.
- [x] Add an ML Kit failure listener and visible detector-failure handling.
- [x] Enable torch in QR mode.
- [x] Add automatic low-light guidance.
- [x] Add a manual torch toggle for QR mode.
- [x] Enable ML Kit zoom suggestions with a device-supported maximum zoom ratio.
- [x] Smooth or limit zoom changes so the preview does not pulse.
- [x] Reset zoom when leaving QR mode or starting a new session.
- [ ] Consider a lower QR analysis resolution only after device benchmarks.
- [x] Validate and trim the QR payload before submission.
- [x] Prevent multiple detections from submitting the same payload while a mutation is active.

### Acceptance criteria

- QR works in daylight, dim indoor parking, and with screen brightness variations.
- QR auto-zoom improves distant-code recognition without unstable preview behavior.
- A failure to initialize or run the barcode detector is visible to the operator.

---

## 13. Phase 6 — Optimize plate recognition safely

### Original problem — resolved on Android

The original regular-plate path could finalize one valid-looking OCR frame and corrections could turn
noise into a plausible format. The implemented analyzer now ranks bounded ROI candidates, requires
temporal agreement, distinguishes corrected/weak shapes, and routes uncertain values to operator
confirmation. Recorded-frame accuracy remains an external release gate.

### Consensus work

- [x] Require two matching consecutive reads for normal automatic acceptance.
- [x] Keep the agreement window short (default 900 ms, remotely clamped to 500–1,500 ms; tune only from approved device measurements).
- [x] Use timestamps instead of a long untimed candidate buffer.
- [x] Clear stale candidates when the target leaves the centre frame.
- [x] Require stronger agreement when OCR character corrections were applied.
- [x] Treat `O/0`, `I/1`, `B/8`, `S/5`, `Z/2`, and similar changes as lower confidence.
- [x] Never auto-submit an invalid Indian plate format.
- [x] Provide one-tap correction/confirmation for uncertain reads.
- [x] Keep manual plate entry available at all times.

### OCR processing work

- [x] Process only the authoritative plate ROI.
- [x] Avoid scanning text outside the centre rectangle.
- [x] Prefer line-level candidates spatially contained in the ROI.
- [x] Rank candidates using format, spatial position, size, consistency, and correction count.
- [x] Include OCR correction count in candidate ranking.
- [x] Bound each frame to a reusable 12-candidate ranker rather than allocating an unbounded collection.
- [x] Precompute format/template data.
- [x] Benchmark candidate normalization independently from ML inference (10,000-iteration JVM benchmark; device ML inference remains separate).

### Resolution work

- [ ] Benchmark 640×480, 960×540, and 1280×720 analysis.
- [ ] Verify that plate characters remain at least large enough for reliable OCR.
- [x] Record OCR duration through the on-device attempt telemetry for each future resolution/device run.
- [x] Use a 16:9 resolution selector that prefers 1280×720 and falls back to the closest supported size.
- [x] Support guarded remote 640×480, 960×540, and 1280×720 analysis choices; per-device-class assignment still requires benchmark data.

### Camera assistance

- [x] Add a modest configurable 1.12× initial zoom; validate/tune it with the physical plate matrix.
- [x] Clamp the requested zoom to the device-reported hardware range.
- [x] Support plate-mode torch and low-light guidance.
- [x] Add hysteresis-controlled reflective-plate exposure compensation; validate white/yellow plates on hardware.
- [x] Keep tap-to-focus available.

### Acceptance criteria

- No single noisy frame can trigger a mutation.
- Two clean matching reads still complete within the plate recognition target.
- Exact plate-read accuracy meets the pilot threshold across the approved operating conditions.
- Uncertain plates move to operator confirmation rather than automatic submission.

---

## 14. Phase 7 — Replace backend regex lookup with exact indexed lookup

### Current problem

The vehicle-number path uses an unanchored, case-insensitive MongoDB regex, fetches all matching bookings, and sorts them in application code.

The existing `vehicleNumber + status` index does not make this case-insensitive regex an efficient exact-match lookup. The hot query also adds spot and lot conditions.

### Data migration

- [ ] Add `vehicleNumberNormalized` to bookings.
- [ ] Define one backend normalization function: trim, uppercase, and remove approved separators.
- [ ] Apply normalization on every create/update path.
- [ ] Backfill all existing bookings in batches.
- [ ] Validate backfill counts and malformed values.
- [ ] Keep the raw/display vehicle number separately.

### Query redesign

- [ ] Query `vehicleNumberNormalized` with exact equality.
- [ ] Use canonical `lotId` in the operator hot path.
- [ ] Use exact `spotId` and expected booking status.
- [ ] Include an appropriate check-in time window where business rules require it.
- [ ] Sort pending check-in candidates by check-in time ascending in Mongo.
- [ ] Sort active check-out candidates by actual/check-in time descending in Mongo.
- [ ] Use `limit(1)` rather than loading and sorting every match in Java.
- [ ] Define explicit behavior if more than one eligible booking still exists.

### Index redesign

- [ ] Add a compound index suited to the exact operator query, for example:
  - `lotId, spotId, status, vehicleNumberNormalized, checkInTime`
- [ ] Add/adjust an index for active check-out ordering if its sort field differs.
- [ ] Deploy indexes through a controlled migration.
- [ ] Verify index creation in the actual production database.
- [ ] Run `explain("executionStats")` for check-in and check-out queries.
- [ ] Confirm low `totalDocsExamined` and `totalKeysExamined`.
- [ ] Add query-duration alerts.

### Lot-resolution cleanup

- [ ] Short-circuit when requested canonical lot ID equals the operator's assigned lot ID.
- [ ] Avoid repeated `findById`/`findByName` calls in one mutation.
- [ ] Remove `lotId OR lotName` from the performance-sensitive operator lookup after migration.
- [ ] Retain compatibility logic only outside the hot path and remove it after rollout.

### Acceptance criteria

- Vehicle lookup is an exact match, not a substring regex.
- Production query plans use the intended compound index.
- Backend lookup meets the p95/p99 target with a production-sized collection.
- A plate cannot accidentally match a longer or differently formatted vehicle value.

---

## 15. Phase 8 — Atomic and idempotent mutations

### Atomic update work

- [ ] Evaluate replacing lock + reread + save with a MongoDB atomic conditional update.
- [ ] Filter by booking ID or exact operator lookup fields plus the expected status.
- [ ] Update status, actual time, operator ID, scan mode, and audit fields atomically.
- [ ] Return the updated document.
- [ ] Treat no matched document as already processed, stale, or invalid based on an authoritative follow-up read.
- [ ] Keep distributed locking only where a single-document atomic update cannot enforce the invariant.
- [ ] Preserve asynchronous terminal events and recovery jobs.

### Idempotency work

- [ ] Generate a UUID `operationId` for every check-in/check-out attempt.
- [ ] Include it in Android requests and structured logs.
- [ ] Persist operation ID, request fingerprint, state, and result server-side.
- [ ] Return the original result for an identical repeated operation ID.
- [ ] Reject operation ID reuse with a different request.
- [ ] Add a status endpoint for uncertain/in-progress operations.
- [ ] Define retention and cleanup for idempotency records.
- [ ] Test response loss after the database mutation commits.

### Acceptance criteria

- Repeating the same operation ID never applies the mutation twice.
- A lost response can be reconciled without asking the operator to guess.
- Concurrent scans for the same booking produce one successful state transition.

---

## 16. Phase 9 — Network and deployment latency

### Android networking

- [x] Measure DNS, TCP connection, TLS, request upload, server wait, and response download separately without overlapping TCP/TLS time.
- [x] Reuse the existing OkHttp connection pool.
- [x] Do not enable automatic mutation replay without server idempotency.
- [ ] After idempotency is deployed, define a shorter operator-specific call timeout.
- [x] Show offline/poor-network status before submission where possible.
- [ ] Reconcile timed-out operations by operation ID.
- [x] Never turn a presentation timeout into permission for another mutation.

### Infrastructure

- [ ] Confirm actual operator location, API region, and MongoDB region.
- [ ] Co-locate API and MongoDB.
- [ ] Select a region appropriate for Indian operators, subject to infrastructure availability and data requirements.
- [ ] Use a production instance that does not scale to zero during operating hours.
- [ ] Configure a minimum running instance count if autoscaling is used.
- [ ] Load test database pool, Tomcat threads, and async executor together.
- [ ] Alert on cold starts, saturation, connection-pool wait, and slow queries.

### Acceptance criteria

- Normal operator traffic does not experience scale-to-zero startup latency.
- API and database cross-region latency is understood and within target.
- Network loss after commit is safely reconciled.

---

## 17. Phase 10 — CameraX stable upgrade

The Android scanner now uses aligned CameraX 1.6.1 artifacts. Multi-vendor compatibility and
before/after device measurements remain release gates.

### Checklist

- [x] Review the current stable CameraX release notes.
- [x] Upgrade all CameraX artifacts to the same stable version (1.6.1).
- [x] Keep the custom analyzers; `camera-mlkit-vision` is not applicable because `MlKitAnalyzer` was not adopted.
- [x] Replace deprecated target-resolution configuration with `ResolutionSelector` where appropriate.
- [x] Compile all scanner modes; final physical execution remains in the device queue.
- [ ] Compare cold/warm startup, analyzer latency, focus success, and device crashes before/after.
- [ ] Roll back the dependency upgrade independently if device compatibility regresses.

### Device coverage

- [ ] Samsung low/mid/high-range devices.
- [ ] Xiaomi/Redmi/Poco devices.
- [ ] Oppo/Realme devices.
- [ ] Vivo/iQOO devices.
- [ ] Motorola devices.
- [ ] Pixel reference device.
- [ ] At least one Android 7/8 device if still supported operationally.
- [ ] Android 12, 13, 14, 15, and 16 coverage.

---

## 18. Phase 11 — Dedicated on-device number-plate detector

Generic text recognition is useful for the existing scanner, but a professional ANPR pipeline should detect the physical plate before applying OCR.

**Android implementation dependency:** This phase is intentionally not guessed or shipped with an
unreviewed model. It requires an approved/licensed TFLite artifact, supported-format decision,
consented privacy-reviewed evaluation data, measured device thresholds, and an app-size/thermal
budget. The current ROI-only ML Kit pipeline and manual-confirmation safety path remain active until
those inputs exist.

### Target pipeline

`Camera frame → plate detector → plate crop → OCR → Indian plate normalization → temporal consensus → backend exact match`

### Checklist

- [ ] Select a small on-device TFLite plate detector.
- [ ] Define supported Indian plate types and operating conditions.
- [ ] Build a consented, privacy-reviewed evaluation dataset.
- [ ] Include cars, motorcycles, scooters, white plates, yellow plates, BH, temporary, vintage, and regional/legacy formats.
- [ ] Include daylight, night, shadows, rain/dust, glare, tilt, motion blur, and partial obstruction.
- [ ] Run the detector at a resolution/frame rate that fits low-end devices.
- [ ] OCR only the detected plate crop.
- [ ] Use detector confidence, crop sharpness, OCR format, and multi-frame agreement together.
- [ ] Keep manual confirmation below the automatic confidence threshold.
- [ ] Never upload or retain plate images without an explicit approved policy.

### Acceptance criteria

- The detector improves end-to-end exact-read accuracy over full-scene OCR.
- Inference remains within the device thermal/performance budget.
- Automatic mutation is allowed only above the measured production-safe threshold.

---

## 19. Scanner architecture cleanup

`QrScannerActivity.kt` remains the Android screen/orchestration boundary, but the camera, recognition,
state, rendering, feature-control, timeout, fallback, viewport, and retained-delivery decisions on the
production path have been extracted into deterministic components. Voice/manual input and Android
View wiring appropriately remain in the Activity.

Completed extraction:

- [x] `ScannerCameraController`
  - Camera provider, binding, focus, torch, zoom, transformation, and resolution.
- [x] `OperatorQrAnalyzer`
  - QR detector and centre-region result selection; model readiness and lifecycle callback orchestration remain shared with the Activity.
- [x] `VehiclePlateAnalyzer`
  - OCR/detector pipeline, ROI, candidate scoring, and temporal consensus.
- [x] `ScannerStateMachine`
  - Testable preparing, scanning, candidate, processing, success, error, timeout, and unavailable transitions; existing UI guard flags remain until device QA proves behavior parity.
- [x] `ScannerUiRenderer`
  - Deduplicated rendering, resolved visual caching, and one coordinated corner animation; transient throttling remains in the focused update gate.
- [x] `OperatorScanCoordinator`
  - Process-wide active-operation protection and cooldown. Operation UUIDs and reconciliation remain backend-contract dependent and are intentionally excluded.
- [x] `ScannerAutomaticRecognitionGate` and `ScannerFeatureControls`
  - Live kill-switch/config snapshots, safe clamping, generation invalidation, and stale-result rejection for automatic Plate and QR paths.
- [x] `ScannerViewportBindPolicy`, `ScannerViewportStatePolicy`, and `ScannerFrameReadinessGate`
  - Testable bind/rebind and first-frame rules across layout, mode, and lifecycle changes.
- [x] `ScannerCameraFallbackPolicy`, `ScannerCameraRuntimePolicy`, and `ScannerDetectorRuntimeFailurePolicy`
  - Permission/unavailable/runtime/detector recovery without a silent dead-scanner or modal-dismissal lock.
- [x] `OperatorOperationTimeoutPolicy`, `OperatorScannerResumePolicy`, and `OperatorTerminalDelivery`
  - Presentation-only timeout, retained mutation/result hand-off, claim/ack ownership, and safe scanner resume.
- [x] `ScannerSpotStatePolicy` and `ScannerCameraConfigUpdatePolicy`
  - Scoped state restoration and safe live camera-config application.

The extraction is complete for the Android performance path. Further reduction of the Activity is a
separate behavior-preserving cleanup after physical regression testing, not a blocker for the current
scanner pipeline.

---

## 20. Test plan

### Unit tests

- [x] Crop-local detector-to-raw transform tests for 0°, 90°, 180°, and 270° rotations.
- [x] Crop-local detector-to-raw transform tests for portrait and landscape crop aspect ratios.
- [x] Frame-centre stability tests for supported screen sizes and symmetric/asymmetric insets.
- [x] Plate normalization tests for the supported regular, legacy, BH, temporary, and vintage format families.
- [x] OCR confusion correction tests for the supported letter/digit lookalike map.
- [x] Candidate scoring tests.
- [x] Two-frame consensus and expiry tests.
- [x] Scanner timing aggregation and privacy-safe event-content tests.
- [x] Luma/detail sampling, low-light hysteresis, and unusable-frame fallback tests.
- [x] Duplicate scan cooldown tests.
- [x] Scanner state-machine tests.
- [x] Automatic Plate/QR kill-switch, config-generation, and stale-final-commit tests.
- [x] Camera configuration, viewport bind/state, frame readiness, fallback, watchdog, and detector-failure tests.
- [x] Operator timeout, retained terminal delivery, background/rotation resume, scoped spot restore, and alias-cooldown tests.
- [x] Responsive portrait/landscape/small-viewport, inset, LTR/RTL rail, and accessibility-resource policy tests.
- [x] Complete debug checkpoint: 49 suites / 258 tests, with 0 failures, 0 errors, and 0 skipped.
- [ ] Idempotency request/result tests.
- [ ] Exact backend query selection tests.

### Recorded-frame benchmark suite

For every sample, record expected plate/QR, detected result, recognition time, and whether manual confirmation is required.

- [ ] Clear daylight front plate.
- [ ] Clear daylight rear plate.
- [ ] Yellow commercial plate.
- [ ] Motorcycle/two-wheeler plate.
- [ ] BH registration.
- [ ] Temporary registration.
- [ ] Vintage registration.
- [ ] Legacy series without letters.
- [ ] Low light.
- [ ] Strong glare/reflection.
- [ ] Dirty/damaged plate.
- [ ] Tilted plate.
- [ ] Motion blur.
- [ ] Small/distant plate.
- [ ] Multiple vehicles/plates.
- [ ] Non-plate text inside and outside the ROI.
- [ ] QR on another phone at low and high brightness.
- [ ] Small/distant QR.
- [ ] Multiple QR codes with only one inside the centre square.

### Physical-device tests

- [ ] Cold app start and first scan.
- [ ] Warm repeat scans for at least 30 minutes.
- [x] Plate ↔ QR switching on Samsung SM-A546E (debug stress pass).
- [ ] Entry ↔ Exit switching.
- [x] Torch in Plate and QR modes on Samsung SM-A546E.
- [ ] QR auto-zoom.
- [ ] Airplane mode and network recovery.
- [ ] Response lost after backend commit.
- [ ] App background/foreground during a mutation.
- [x] Device rotation and analysis teardown on Samsung SM-A546E.
- [ ] Low battery and thermal throttling.
- [ ] Operator shift test with hundreds of sequential scans.

### Backend tests

- [ ] Exact normalized lookup with production-sized data.
- [ ] Query-plan verification.
- [ ] Concurrent scans for the same booking.
- [ ] Concurrent scans for different bookings at the same lot.
- [ ] Duplicate operation ID.
- [ ] Reused operation ID with a different payload.
- [ ] Database delay/failure.
- [ ] API instance restart during mutation.
- [ ] Async finalization backlog.

### Load test model

Use realistic scenarios such as:

- 20, 50, and 100 concurrent operators.
- Short bursts at event entry/exit times.
- Production-sized pending, active, and historical booking data.
- A mix of QR check-in, QR check-out, plate check-in, and plate check-out.
- Slow and failed client connections.

---

## 21. Rollout plan

### Before rollout

- [ ] Capture baseline production metrics from the current release.
- [ ] Define success and rollback thresholds.
- [ ] Confirm manual entry remains functional.
- [ ] Confirm feature flags and kill switches work.
- [ ] Train a pilot operator on new feedback timing.

### Stages

1. Internal QA devices.
2. One controlled parking lot and a small operator group.
3. 5% of eligible operator sessions.
4. 25% rollout after metric review.
5. 50% rollout after peak-period observation.
6. 100% rollout only after p95/p99 and error-rate approval.

### Monitor during rollout

- [ ] Scanner crash/ANR rate.
- [ ] Camera binding failure rate.
- [ ] ML model unavailable rate.
- [ ] Preview-ready latency.
- [ ] QR recognition latency and failure rate.
- [ ] Plate consensus latency and exact-read accuracy.
- [ ] Manual fallback rate.
- [ ] Backend p95/p99 latency.
- [ ] Duplicate/cooldown errors.
- [ ] Scope/spot mismatch errors.
- [ ] Operator cancellation rate.
- [ ] Wrong-operation incident reports.

### Rollback triggers

- Any confirmed wrong automatic mutation caused by scanner recognition.
- Material increase in scanner crashes or ANRs.
- Material increase in camera initialization failures.
- p95 end-to-end time worse than the prior production version.
- Backend duplicate or uncertain-operation incident.
- Device-specific regression affecting an important operator-device group.

### Rollback methods

- Restore prior consensus/hold/resolution values through remote configuration.
- Disable all new automatic Plate/QR recognition with `operatorScannerAutomaticRecognition`, or disable QR auto-zoom independently.
- Force manual confirmation for plates.
- Roll back the CameraX dependency release independently.
- Roll back backend exact-query/atomic-mutation changes through their isolated deployment plan.

---

## 22. Recommended release grouping

Do not ship all changes together.

### Release A — Measurement and obvious latency removal

- Baseline timing.
- Stable centred frame.
- Correct CameraX ROI/focus transform.
- ML model readiness.
- UI update deduplication/throttling.
- Shorter configurable success hold.

### Release B — Fast QR and persistent camera

- One persistent CameraX session.
- QR centre-region enforcement.
- QR torch and auto-zoom.
- QR failure handling.

### Release C — Safe fast plate OCR

- Short temporal consensus.
- ROI-only OCR.
- Resolution benchmarks and selected default.
- Improved low-light/focus assistance.

### Release D — Backend hot-path optimization

- Normalized vehicle field and backfill.
- Exact lookup and compound indexes.
- Canonical lot ID hot path.
- Query-plan verification.

### Release E — Mutation reliability

- Operation IDs and idempotency.
- Atomic conditional state transition.
- Status reconciliation.
- Operator-specific network timeout behavior.

### Release F — Advanced ANPR

- Dedicated plate detector.
- Confidence fusion.
- Expanded pilot and accuracy validation.

---

## 23. Definition of done

The scanner optimization project is complete only when:

- [ ] The centre frame is visually stable and is the actual analysis/focus region.
- [ ] Fresh installations do not silently wait for missing ML models.
- [ ] Camera analysis does not cause visible main-thread jank.
- [ ] QR and plate modes meet the agreed p95 latency targets.
- [ ] The scanner becomes ready for the next vehicle within one second after confirmation.
- [ ] Vehicle lookup is exact, normalized, indexed, and verified in production.
- [ ] Mutations are atomic and safely idempotent.
- [ ] A lost response can be reconciled without duplicate processing.
- [ ] Real-device, recorded-frame, backend integration, and load tests pass.
- [ ] A staged production rollout completes without a wrong automatic operation.
- [ ] Operational dashboards and alerts remain in place after rollout.

## 24. Reference documentation

- [ML Kit text recognition for Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [ML Kit barcode scanning for Android](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- [CameraX transform output](https://developer.android.com/media/camera/camerax/transform-output)
- [CameraX `ImageProxyTransformFactory`](https://developer.android.com/reference/androidx/camera/view/transform/ImageProxyTransformFactory)
- [CameraX configuration, focus, and metering](https://developer.android.com/media/camera/camerax/configuration)
- [CameraX ML Kit Analyzer](https://developer.android.com/media/camera/camerax/mlkitanalyzer)
- [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera)
- [Android JankStats](https://developer.android.com/topic/performance/jankstats)
- [AndroidX Metrics releases](https://developer.android.com/jetpack/androidx/releases/metrics)
- [ML Kit `InputImage`](https://developers.google.com/android/reference/com/google/mlkit/vision/common/InputImage)
- [MongoDB `$regex` query and index behavior](https://www.mongodb.com/docs/manual/reference/operator/query/regex/)
