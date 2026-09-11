# Gridee Android Production Performance and Scalability Audit

> Audit date: 2026-09-04  
> Scope: `Gridee_Android/android-app` and the active `gridee_backend` service  
> Audited branch: `feature/edit-profile-dark-mode-fix`  
> Base commit: `d90db45043d8fa55a6b3d25392df2237389c3230`, plus the pre-existing dirty worktree  
> Status: working remediation backlog; production rollout is blocked by the open P0 items  
> Audience: Android, backend, QA, SRE/operations, security, and product engineering

## Document status

This document is the working source of truth for the production-readiness, performance, scalability, smoothness, and reliability work identified in the September 2026 audit. Update the owner, status, evidence, decisions, and validation results as work is completed.

Allowed status values:

- `OPEN`: no implementation has started.
- `IN PROGRESS`: an owner is actively implementing or validating the item.
- `BLOCKED`: a named external decision or dependency is preventing progress.
- `DONE`: the acceptance criteria have been verified and evidence is linked.
- `ACCEPTED RISK`: the risk was explicitly accepted by the engineering and product owners, with an expiry/review date.

Do not close a performance item merely because code was changed. Record before/after measurements on representative devices or production-like infrastructure. Do not close a financial-integrity item without retry, concurrency, timeout, and crash-recovery tests.

No application source was changed during the original audit. Gradle commands generated only local build and report outputs. The checkout was already heavily modified, so every finding must be reconciled with the exact commit deployed to Google Play before treating this as an audit of the live binary.

## Executive verdict

The audited checkout is **not ready for another production rollout**. Nine stop-ship findings can cause release-key compromise, failed builds, cold-start crashes, unauthorized wallet credits, duplicate financial operations, inconsistent bookings, user-visible stale state, and sensitive production logging.

The current scale concern is not the headline count of approximately 6,000 registered users. It is the number of concurrently active users and the shape of their traffic. Hundreds of users simultaneously polling bookings, checking payments, or creating bookings can stress the current blocking backend paths and 80-thread servlet pool. The correct order is:

1. Protect signing and financial integrity.
2. Make release builds reproducible and eliminate supported-OS crashes.
3. Add idempotency, atomicity, and authoritative post-mutation state.
4. Establish production observability and measurable release gates.
5. Remove wasteful polling and main-thread/UI work.
6. Scale shared infrastructure only from measured bottlenecks.

The codebase also has useful foundations: meaningful unit tests, safe retry boundaries for mutations, scanner backpressure, several lifecycle cleanup patterns, atomic spot-capacity decrement, backend metrics, and existing cache/index/rate-limit concepts. This is a focused production-hardening program, not a rewrite.

## Audit evidence and limitations

| Check | Result |
|---|---|
| Android main source | 250 Kotlin/Java files; 65,841 lines |
| Resources | 981 resources; 104 layouts; 745 qualified drawable files |
| Largest classes | `QrScannerActivity` 6,330 lines; `ParkingSpot_bottomsheet` 2,202; `HomeFragment` 1,882; `BookingsFragmentNew` 1,591; `BookingsAdapter` 1,541 |
| Unit tests | 259 tests across 49 suites; 0 failures, errors, or skips |
| Instrumented tests | No `androidTest` source files found |
| Full lint | Failed with 317 errors, including 1 fatal issue, and 1,720 warnings |
| Normal release build | Failed at `lintVitalRelease` |
| Diagnostic build with lint skipped | APK 29,863,417 bytes (about 28.5 MiB); expanded payload 67,730,634 bytes; 2,596 files; 6 app DEX files |
| Release observability | No app-wide crash/ANR/performance SDK or mapping-upload workflow found |
| Runtime profiling | Not performed: no physical device, Play Console access, production traces, production topology, Mongo cardinality, or Atlas `explain` output was available |

Commands used for the reproducible static checks:

```bash
cd Gridee_Android/android-app
./gradlew :app:testDebugUnitTest :app:lintDebug --continue
./gradlew :app:assembleRelease
```

The full generated lint report is at [`Gridee_Android/android-app/app/build/reports/lint-results-debug.html`](Gridee_Android/android-app/app/build/reports/lint-results-debug.html). It is a generated artifact and may need to be regenerated on another machine.

### Lint categories that require planned cleanup

| Category | Count |
|---|---:|
| Missing translations | 186 |
| Incorrect tints | 68 |
| Unguarded newer APIs | 37 |
| Android 16 back-navigation issues | 3 |
| Unused resources | 834 |
| Hardcoded strings | 200 |
| Missing content descriptions | 135 |
| `SetTextI18n` | 78 |
| Overdraw | 43 |
| Static leaks | 4 |
| Draw-time allocations | 4 |
| `notifyDataSetChanged()` | 4 |
| Excessive view count | 3 |
| Excessive layout depth | 3 |

## Master tracker

| ID | Priority | Area | Summary | Status | Owner | Target |
|---|---|---|---|---|---|---|
| PERF-001 | P0 | Release security | Rotate and remove tracked signing credentials | OPEN | TBD | 48 hours |
| PERF-002 | P0 | Release | Restore an unskipped release build | DONE | Android | 2026-09-05 |
| PERF-003 | P0 | Android crash | Guard API 33 splash call | DONE | Android | 2026-09-05 |
| PERF-004 | P0 | Android crash | Guard API 26 animator calls | DONE | Android | 2026-09-05 |
| PERF-005 | P0 | Financial security | Make rewarded credit server-verifiable | OPEN | TBD | 48 hours |
| PERF-006 | P0 | Payments | Durable payment idempotency and atomic completion | OPEN | TBD | 48 hours |
| PERF-007 | P0 | Booking/wallet | Atomic, idempotent mutations | OPEN | TBD | 48 hours |
| PERF-008 | P0 | Data consistency | Eliminate stale post-mutation cache reads | OPEN | TBD | 48 hours |
| PERF-009 | P1 | Backend load | Replace rapid booking polling | OPEN | TBD | Week 1-2 |
| PERF-010 | P1 | Correctness | Repair hidden/resume booking synchronization | OPEN | TBD | Week 1-2 |
| PERF-011 | P1 | Payments | Make status checks webhook/local-state first | OPEN | TBD | Week 1-2 |
| PERF-012 | P1 | Backend | Remove per-request configuration/auth DB reads | OPEN | TBD | Week 1-2 |
| PERF-013 | P1 | Backend | Replace blocking Mongo request-thread locks | OPEN | TBD | Week 1-2 |
| PERF-014 | P1 | Backend cache | Redesign hot-response cache | OPEN | TBD | Week 1-3 |
| PERF-015 | P1 | Horizontal scale | Share state and coordinate schedulers | OPEN | TBD | Week 2-4 |
| PERF-016 | P1 | API contract | Standardize booking pagination | OPEN | TBD | Week 1-2 |
| PERF-017 | P1 | Data/UI | Stop full-dataset downloads and fake paging | OPEN | TBD | Week 1-3 |
| PERF-018 | P1 | Support | Normalize and paginate chat messages | OPEN | TBD | Week 2-4 |
| PERF-019 | P1 | Database | Validate and add hot-query indexes | OPEN | TBD | Week 1-3 |
| PERF-020 | P1 | Operator | Move search/sort/limit into indexed queries | OPEN | TBD | Week 1-2 |
| PERF-021 | P1 | Notifications | Deduplicate FCM registration | OPEN | TBD | Week 1-2 |
| PERF-022 | P1 | Android state | Fix fragment process-restoration duplication | DONE | Android | 2026-09-05 |
| PERF-023 | P1 | Android lifecycle | Deduplicate scroll listeners | DONE | Android | 2026-09-10 |
| PERF-024 | P1 | Startup/memory | Remove eager tab-view prewarming | IN PROGRESS | Android | Week 1-2 |
| PERF-025 | P1 | Startup | Shorten and optimize splash | OPEN | TBD | Week 1-2 |
| PERF-026 | P1 | Animation | Pause hidden work and remove frame allocations | OPEN | TBD | Week 1-3 |
| PERF-027 | P1 | Lists | Restore RecyclerView virtualization | OPEN | TBD | Week 1-2 |
| PERF-028 | P1 | QR/memory | Generate QR off-main with a bounded cache | OPEN | TBD | Week 1-2 |
| PERF-029 | P1 | Thermal/battery | Remove global maximum refresh-rate forcing | OPEN | TBD | Week 1-2 |
| PERF-030 | P1 | UI | Stop profile accordion relayout per frame | OPEN | TBD | Week 1-3 |
| PERF-031 | P1 | Search | Debounce and pre-normalize Home search | OPEN | TBD | Week 1-2 |
| PERF-032 | P1 | Animation | Reduce wallet animation allocations | OPEN | TBD | Week 1-2 |
| PERF-033 | P1 | Memory | Remove static activity/view retention | OPEN | TBD | Week 1-2 |
| PERF-034 | P1 | Layout | Resolve layout depth, count, overdraw, and diffing | OPEN | TBD | Week 1-4 |
| PERF-035 | P1 | Android 16 | Migrate legacy back handling | OPEN | TBD | Week 1-2 |
| PERF-036 | P2 | Release size | Enable staged R8/resource shrinking | OPEN | TBD | Week 2-4 |
| PERF-037 | P2 | Runtime | Add an app-owned baseline profile | OPEN | TBD | Week 2-4 |
| PERF-038 | P2 | Startup | Rationalize SDK initialization | OPEN | TBD | Week 2-4 |
| PERF-039 | P1 | Observability | Add crash, ANR, startup, jank, and API telemetry | OPEN | TBD | Week 1-2 |
| PERF-040 | P0 | Privacy | Remove sensitive production logging | DONE | Android | 2026-09-05 |
| PERF-041 | P1 | Quality | Add Android CI and device/instrumented coverage | OPEN | TBD | Week 1-3 |
| PERF-042 | P2 | Build health | Align and verify dependencies/toolchains | OPEN | TBD | Week 2-4 |
| PERF-043 | P2 | Product safety | Remove or gate dormant legacy flows | OPEN | TBD | Week 2-4 |
| PERF-044 | P1 | Security/config | Harden tokens, cleartext config, channels, native packaging | OPEN | TBD | Week 1-3 |
| PERF-045 | P2 | Maintainability | Split monolithic screens by domain/state | OPEN | TBD | Week 3+ |
| PERF-046 | P2 | Backend efficiency | Address secondary cache, batch, media, and pool issues | OPEN | TBD | Week 3-6 |

## P0: release, crash, security, and data-integrity blockers

### PERF-001 — Rotate and remove tracked release signing credentials

**Status:** OPEN  
**Impact:** Anyone with repository-history access may be able to sign an upload artifact. This is a release-chain incident, not ordinary technical debt.

**Evidence**

- Signing passwords are plaintext in [`gradle.properties`](gradle.properties#L28).
- [`keystore/upload.jks`](keystore/upload.jks) is tracked.
- During the audit, the tracked key was byte-identical to the ignored active nested key, and the signing values matched. Secret values are intentionally not reproduced here.

**Required work**

- [ ] Freeze release activity using this key.
- [ ] Use Play App Signing's upload-key reset/rotation process where applicable.
- [ ] Remove passwords and key material from the working tree and Git history.
- [ ] Store release material in CI secret storage and ignored developer-local files.
- [ ] Restrict Firebase/API keys appropriately and enable App Check where applicable; a tracked `google-services.json` is common but does not remove the need for restrictions.
- [ ] Document key custody, rotation, and emergency recovery.

**Acceptance criteria**

- The old upload key is rejected or retired.
- No signing password/private key exists anywhere in current Git history accessible to normal contributors.
- CI can sign a release using protected secrets, and local debug builds need no production secret.

### PERF-002 — Restore an unskipped release build

**Status:** DONE — 2026-09-05  
**Impact:** The normal release and benchmark builds fail. A build produced by skipping lint is diagnostic only and must never be shipped.

**Original evidence**

- `app/src/main/res/drawable-night/bg_notice_banner.xml` existed while the base `drawable/bg_notice_banner.xml` was deleted in the audited worktree.
- `assembleRelease` fails at `lintVitalRelease`.
- The diagnostic release is approximately 28.5 MiB as an APK and expands to about 67.7 MiB; it contains six app DEX files plus a large embedded Meta Audience Network DEX.

**Resolution**

- [x] Removed the orphaned night variant instead of restoring dead UI. Both original consumers had already been removed: the notice section was deleted from `activity_wallet_add_money.xml`, and `bottom_sheet_top_up.xml` was deleted entirely.
- [x] Confirmed there is no remaining source/resource reference to `bg_notice_banner`.
- [x] Ran clean release assembly, release-vital lint, unit tests, full lint reporting, signed bundle generation, artifact signature checks, and API 34 install/launch smoke tests without skipping build tasks.
- [x] Kept the mandatory CI release gate under PERF-041 so release automation has one owner rather than duplicated completion state.

**Verification evidence — 2026-09-05**

- `./gradlew :app:lintVitalRelease --stacktrace`: **PASS**, 28 actionable tasks, no `MissingDefaultResource` finding.
- `./gradlew clean :app:assembleRelease --stacktrace`: **PASS**, 57 actionable tasks, 56 executed.
- `./gradlew :app:testDebugUnitTest :app:bundleRelease --stacktrace`: **PASS**, 259 tests, 0 failures/errors/skips; APK and AAB produced and signed.
- APK signature verification: **PASS**, APK Signature Scheme v2, one signer.
- AAB JAR signature verification: **PASS**.
- Full `lintDebug` was deliberately run and still fails on the broader backlog with 316 errors and 1,719 warnings, but the previous fatal orphan-resource issue is absent. Before this fix it reported 317 errors and 1,720 warnings.
- One first disposable-emulator attempt emitted an incomplete native crash-buffer record while opening a permission dialog. It did not reproduce on a second fresh emulator or during five additional cold-process launches. Keep this caveat visible for PERF-039/physical-device testing; it does not match or invalidate the removed resource defect.

**Acceptance criteria**

- [x] A clean Gradle build of the audited worktree produces signed APK/AAB outputs with no skipped lint/build task.
- [x] `lintVitalRelease` both detected the original orphan and passes after the paired resource deletion.
- [x] CI ownership is explicitly assigned to PERF-041; that program-level gate remains open without duplicating PERF-002's resource-defect status.

### PERF-003 — Guard the API 33 splash animation call

**Status:** DONE — 2026-09-05  
**Impact:** Cold process launch can crash on Android API 24 through 32.

**Original evidence**

- Minimum SDK is API 24 in [`app/build.gradle`](Gridee_Android/android-app/app/build.gradle#L71).
- [`SplashActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/auth/SplashActivity.kt#L113) called `ValueAnimator.getDurationScale()`, which requires API 33, without an SDK guard.

**Resolution**

- [x] Added [`AnimatorSettingsCompat.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/motion/AnimatorSettingsCompat.kt), the single Android-facing animation-preference utility. API 26+ uses `ValueAnimator.areAnimatorsEnabled()`; API 24/25 reads `Settings.Global.ANIMATOR_DURATION_SCALE` with Android's `1f` default.
- [x] Isolated the API 26 platform invocation in an `@RequiresApi(26)` implementation behind a direct `SDK_INT` guard. Legacy settings-provider runtime failures default to animations enabled, while linkage, VM, and modern platform failures are not hidden by a broad `Throwable` catch.
- [x] Replaced the API 33 splash call and preserved the process-level short circuit through [`SplashCinematicPolicy.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/auth/SplashCinematicPolicy.kt).
- [x] Added six compatibility-branch tests and three splash-decision tests, including lazy branch isolation at the API 25/26 boundary.
- [x] Exercised the signed release APK through cold-process launches on disposable API 24 and API 34 devices with animator scales `1` and `0`.

**Verification evidence — 2026-09-05**

- `./gradlew :app:testDebugUnitTest :app:assembleRelease`: **PASS**; 268 tests across 51 suites, with 0 failures, errors, or skips; `lintVitalRelease` and release packaging also passed.
- Full `lintDebug` still reports the separately tracked backlog of 315 errors and 1,719 warnings. Its `NewApi` count fell from 44 to 43, and there is no `NewApi` issue for `SplashActivity` or `AnimatorSettingsCompat`.
- Main-source search finds no remaining `ValueAnimator.getDurationScale()` call.
- API 24 release smoke, scale `1`: **PASS**; cold launch reached `WelcomeActivity`, PID remained alive, and the crash buffer contained no app fatal or `NoSuchMethodError`.
- API 24 release smoke, scale `0`: **PASS** with the same assertions, proving the legacy reduced-motion branch on the minimum supported API.
- API 34 release smoke, scales `1` and `0`: **PASS** with the same assertions, proving the modern platform branch with the final APK.
- APK signature and alignment checks: **PASS**; APK Signature Scheme v2, one signer, and 16 KiB zip alignment verification successful.
- The complete API 24/25/26/29/32/33/36 automated device matrix remains program-level work under PERF-041; it does not duplicate or reopen this repaired call-site defect.

**Acceptance criteria**

- [x] No splash execution path invokes an API newer than the running device.
- [x] The helper and splash predicate have local coverage for API 24, 25, 26, 33, and 36 behavior, including both lazy branches.
- [x] Minimum-SDK and modern-SDK cold runtime smokes pass with animations enabled and disabled.
- [x] Ongoing all-supported-API launch coverage has one explicit owner in PERF-041.

### PERF-004 — Guard all API 26 animator calls

**Status:** DONE — 2026-09-05  
**Impact:** Android 7.0 and 7.1 users can crash when affected views/screens evaluate animation availability.

**Original evidence**

Unguarded `ValueAnimator.areAnimatorsEnabled()` calls were found in:

- [`FullPageBottomSheetFragment.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bottomsheet/FullPageBottomSheetFragment.kt#L266)
- [`ParkingSpot_bottomsheet.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bottomsheet/ParkingSpot_bottomsheet.kt#L414)
- [`SelectLotAdapter.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/lot/SelectLotAdapter.kt#L198)
- [`SelectParkingLotActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/lot/SelectParkingLotActivity.kt#L198)
- [`SkeletonShimmer.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/views/SkeletonShimmer.kt#L53)
- [`SpotDialView.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/views/SpotDialView.kt#L93)

**Resolution**

- [x] Replaced all six unguarded calls with `AnimatorSettingsCompat.areEnabled(context)` while preserving each immediate/static reduced-motion state.
- [x] Made both bottom-sheet checks detached-safe: a missing Fragment context now skips motion and completes teardown instead of introducing `requireContext()` failure risk.
- [x] Short-circuited the lot-selection dock check when the update did not come from a tap, avoiding an unnecessary legacy settings-provider read without changing the rendered result.
- [x] Extended the source-wide pass beyond the six lint failures. Existing guarded duplicates in `RewardAmountView`, `RewardCoinView`, `GrideeGateView`, and `RewardBottomSheet`, plus the raw settings read in `BrandFollowSwitcher`, now use the same helper.
- [x] Kept the API 24/25 and API 26+ behavior under the six focused compatibility tests introduced by PERF-003.

**Verification evidence — 2026-09-05**

- `./gradlew :app:testDebugUnitTest :app:assembleRelease`: **PASS**; 268 tests across 51 suites, with 0 failures, errors, or skips; release-vital lint and signed APK packaging passed.
- Full `lintDebug` moved from 315 errors to 309, with warnings unchanged at 1,719. The `NewApi` count moved from 43 to 37—exactly the six original failures—and none of the affected files retains an animator-availability `NewApi` issue.
- A whole-source scan now finds exactly one `ValueAnimator.areAnimatorsEnabled()` invocation and one `ANIMATOR_DURATION_SCALE` read; both are inside `AnimatorSettingsCompat` behind the tested API boundary.
- The shared Android-facing facade used by every migrated call site was already executed successfully on API 24 at animator scales `1` and `0` during the signed-release PERF-003 smoke. This proves the minimum-SDK runtime branch rather than only its pure resolver.
- Final APK signature and 16 KiB zip-alignment verification: **PASS**.
- Screen-by-screen API 24/25 navigation automation remains part of the reusable device suite owned by PERF-041, rather than bespoke manual coverage duplicated in every call-site ticket.

**Acceptance criteria**

- [x] No reachable animator-availability call requires an API above `minSdk`.
- [x] Relevant lint `NewApi` findings are zero.
- [x] Animation-preference behavior has one source of truth across the Android app.
- [x] Ongoing affected-screen API 24/25 navigation coverage has one explicit owner in PERF-041.

### PERF-005 — Make rewarded-wallet credit server-verifiable and replay-safe

**Status:** OPEN  
**Impact:** A modified authenticated client can request wallet value without proving that an ad reward occurred. Repetition makes the loss unbounded even when each request is capped.

**Evidence**

- Android submits an amount-only reward request in [`PaymentModels.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/model/PaymentModels.kt#L95).
- [`RewardCreditCoordinator.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/wallet/RewardCreditCoordinator.kt#L15) uses an in-process identifier rather than a durable provider event.
- [`WalletController.java`](gridee_backend/src/main/java/com/parking/app/controller/WalletController.java#L65) accepts the authenticated client's amount, capped per call, without provider proof, provider-event uniqueness, or an atomic daily quota.

**Required work**

- [ ] Disable the credit endpoint or remote-kill the reward until secured.
- [ ] Validate Google rewarded-ad server-side verification on the backend.
- [ ] Persist a unique `(provider, eventId, userId)` record.
- [ ] Enforce daily/weekly limits atomically on the server.
- [ ] Add a wallet-specific rate limit far below the generic API limit.
- [ ] Audit historical `AD_TOP_UP` frequency and total value against rewarded-ad revenue.
- [ ] Reconcile this work with [`ADMOB_MONETIZATION_AUDIT_AND_IMPLEMENTATION.md`](ADMOB_MONETIZATION_AUDIT_AND_IMPLEMENTATION.md).

**Acceptance criteria**

- No client-controlled field can independently authorize value creation.
- Replaying the same provider event is a no-op.
- Concurrent requests cannot exceed the server quota.
- Abuse, rejection, and credited-value metrics are alerted.

### PERF-006 — Make payments durably idempotent and crash-atomic

**Status:** OPEN  
**Impact:** Timeouts, retries, webhook duplication, or process failure can create orphan gateway orders, duplicate wallet credit, or replayed paid bookings.

**Evidence**

- The Android request contract lacks a durable idempotency key in [`PaymentModels.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/model/PaymentModels.kt#L12).
- [`PaymentGatewayService.java`](gridee_backend/src/main/java/com/parking/app/service/PaymentGatewayService.java#L140) can create a new order/random Cashfree idempotency value on retry before local persistence.
- A business operation can occur before the payment is marked consumed around [`PaymentGatewayService.java`](gridee_backend/src/main/java/com/parking/app/service/PaymentGatewayService.java#L252).
- Wallet mutation can precede completed-ledger persistence around [`PaymentGatewayService.java`](gridee_backend/src/main/java/com/parking/app/service/PaymentGatewayService.java#L634).

**Required work**

- [ ] Generate one stable client command/idempotency key and reuse it across retries.
- [ ] Enforce unique local command ID, gateway order ID, and gateway payment/event IDs.
- [ ] Persist intent before or atomically with the external-order workflow.
- [ ] Use a transaction plus durable outbox/state machine for local side effects.
- [ ] Make webhook and client reconciliation converge through the same idempotent command handler.
- [ ] Define recovery for every intermediate state.

**Acceptance criteria**

- Repeating any request or webhook 100 times changes value/booking state at most once.
- Process termination at every state-machine boundary recovers automatically.
- Gateway and local ledgers reconcile with no orphan or ambiguous records.

### PERF-007 — Make booking and wallet mutations atomic and idempotent

**Status:** OPEN  
**Impact:** Concurrent calls or crashes can leave capacity, booking, wallet balance, ledger, and emitted events inconsistent.

**Evidence**

- The active six-argument booking lifecycle overload lacks its expected retry annotation around [`BookingLifecycleService.java`](gridee_backend/src/main/java/com/parking/app/service/booking/BookingLifecycleService.java#L87).
- The named transactional workflow has no effective `@Transactional` boundary around capacity decrement, booking save, and event publication in [`BookingLifecycleService.java`](gridee_backend/src/main/java/com/parking/app/service/booking/BookingLifecycleService.java#L267).
- User concurrency is checked before the spot lock in [`BookingService.java`](gridee_backend/src/main/java/com/parking/app/service/BookingService.java#L69).
- Wallet balance and ledger writes are separate in [`WalletService.java`](gridee_backend/src/main/java/com/parking/app/service/WalletService.java#L243), and retry references may be randomized.

**Required work**

- [ ] Require and uniquely index `(userId, idempotencyKey)` for booking and wallet commands.
- [ ] Enforce user-scoped active-booking limits atomically.
- [ ] Use Mongo transactions where supported, or atomic conditional documents with a compensating state machine.
- [ ] Publish external events through a durable outbox after commit.
- [ ] Test same-user and same-spot races, double taps, network retry, and crash recovery.

**Acceptance criteria**

- Capacity never becomes negative or diverges from active bookings.
- One command produces one booking/ledger event under concurrency and retry.
- Failed publication is retried without repeating the committed business mutation.

### PERF-008 — Eliminate stale reads immediately after a successful mutation

**Status:** OPEN  
**Impact:** The UI can show success and then revert to old booking, wallet, or parking state for tens of seconds, making correct operations feel failed or buggy.

**Evidence**

- Android triggers booking/wallet/spot reads after a mutation in [`BookingMutationRefreshCoordinator.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/repository/BookingMutationRefreshCoordinator.kt#L84).
- The backend caches mutable responses for approximately 5–10 seconds in [`HotReadResponseCacheFilter.java`](gridee_backend/src/main/java/com/parking/app/config/HotReadResponseCacheFilter.java#L123) and [`ParkingSpotService.java`](gridee_backend/src/main/java/com/parking/app/service/ParkingSpotService.java#L288).
- Android may then retain the stale response for another 20–30 seconds in [`BookingRepository.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/repository/BookingRepository.kt#L392), [`WalletRepository.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/repository/WalletRepository.kt#L130), and [`ParkingRepository.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/repository/ParkingRepository.kt#L175).

**Required work**

- [ ] Treat mutation responses as the authoritative resulting state, including version/revision.
- [ ] Remove caching from mutable authenticated resources until invalidation is correct.
- [ ] Add atomic, user/resource-scoped invalidation or versioned cache keys.
- [ ] Prevent an older asynchronous response from overwriting a newer local version.

**Acceptance criteria**

- Successful booking, cancellation, payment, reward, and wallet operations never visually regress to an older state.
- Cache integration tests cover mutation followed immediately by reads from the same and another backend replica.

## P1: backend scalability and reliability

### PERF-009 — Replace rapid booking polling with event-driven updates

**Status:** OPEN  
**Impact:** The Bookings screen can produce a high sustained request rate and repeatedly rerender unchanged data.

**Evidence**

- [`BookingsFragmentNew.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/BookingsFragmentNew.kt#L130) polls active bookings every 2.5 seconds while visible and every 1.5 seconds during a barrier state.
- Active and history reads can run sequentially around [`BookingsFragmentNew.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/BookingsFragmentNew.kt#L790), followed by UI work even when the snapshot is unchanged.
- [`BookingRailAdapter.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/adapters/BookingRailAdapter.kt#L51) uses broad notifications and reinflates nested child views.

One visible user therefore produces about 24 active-list requests/minute, or 40/minute during the barrier. At 1,000 simultaneous visible users, that one path is approximately 400–667 requests/second before other traffic. This is scenario math, not measured production concurrency; history is not assumed to run on every tick.

**Required work**

- [ ] Deliver booking-state events through FCM data messages, SSE, or WebSockets.
- [ ] Keep a bounded, jittered, exponential-backoff reconciliation read on resume and after missed-event detection.
- [ ] Compare stable snapshot IDs/versions before emitting UI updates.
- [ ] Move the rail to `ListAdapter`/`DiffUtil` with stable item IDs.
- [ ] Instrument active polling RPS and event-to-render latency before changing intervals.

**Acceptance criteria**

- Idle visible bookings produce no fixed 1.5/2.5-second polling load.
- A booking change normally appears within the product SLO and always reconciles on resume/network recovery.
- An unchanged snapshot causes no full rail rebuild.

### PERF-010 — Repair hidden-tab and resume synchronization semantics

**Status:** OPEN  
**Impact:** The intended 12-second hidden watcher does not provide reliable freshness, so a user can return to stale bookings.

**Evidence**

- [`MainContainerActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt#L312) demotes hidden fragments to `STARTED`.
- [`BookingsFragmentNew.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/BookingsFragmentNew.kt#L440) stops work in `onPause` and refuses synchronization when not resumed around [line 178](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/BookingsFragmentNew.kt#L178).

**Required work**

- [ ] Define a single source of truth for booking state outside the fragment view lifecycle.
- [ ] Reconcile once on resume when state is stale or an event/version was missed.
- [ ] Remove the misleading unreachable watcher path.
- [ ] Test tab switching, background/foreground, process death, and delayed FCM delivery.

**Acceptance criteria**

- Returning to Bookings cannot remain stale beyond the documented reconciliation window.
- Lifecycle transitions create no duplicate jobs or observers.

### PERF-011 — Make payment status webhook/local-state first

**Status:** OPEN  
**Impact:** Pending payment waves can occupy most or all servlet threads while waiting on Cashfree.

**Evidence**

- Android performs up to four checks at approximately 0, 2, 6, and 14 seconds in [`PaymentStatusCoordinator.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/wallet/PaymentStatusCoordinator.kt#L124).
- Each request can call Cashfree synchronously through [`PaymentController.java`](gridee_backend/src/main/java/com/parking/app/controller/PaymentController.java#L138) and [`PaymentGatewayService.java`](gridee_backend/src/main/java/com/parking/app/service/PaymentGatewayService.java#L196).
- The gateway read timeout is 20 seconds while production Tomcat is configured for 80 threads in [`application-prod.properties`](gridee_backend/src/main/resources/application-prod.properties#L107).

**Required work**

- [ ] Return local persisted payment state immediately.
- [ ] Treat verified webhooks as the primary state transition.
- [ ] Run stale gateway reconciliation asynchronously with per-order single-flight protection.
- [ ] Add jitter, a total deadline, and a terminal/pending UI state.
- [ ] Monitor gateway latency, pending age, reconciliation lag, and executor saturation.

**Acceptance criteria**

- Client status reads never hold servlet threads on a gateway call.
- Duplicate polls/webhooks converge without duplicate value or booking state.
- A gateway outage does not exhaust the API pool.

### PERF-012 — Remove avoidable configuration and authentication database reads

**Status:** OPEN  
**Impact:** Even cacheable authenticated reads incur repeated database latency and load before reaching the response cache.

**Evidence**

- Maintenance configuration flows through [`MaintenanceModeFilter.java`](gridee_backend/src/main/java/com/parking/app/config/MaintenanceModeFilter.java#L27) and [`AppConfigService.java`](gridee_backend/src/main/java/com/parking/app/service/AppConfigService.java#L54).
- [`JwtAuthenticationFilter.java`](gridee_backend/src/main/java/com/parking/app/config/JwtAuthenticationFilter.java#L166) loads the user on authenticated requests.
- The hot-response cache is ordered after JWT authentication in [`SecurityConfig.java`](gridee_backend/src/main/java/com/parking/app/config/SecurityConfig.java#L217).

**Required work**

- [ ] Keep a versioned in-memory/shared configuration snapshot with explicit invalidation.
- [ ] Cache minimal principal/revocation state for a bounded period or encode the necessary claims safely.
- [ ] Preserve immediate account-disable/token-revocation behavior through shared versioning or revocation state.
- [ ] Measure database calls per route and request.

**Acceptance criteria**

- Hot authenticated reads do not require unconditional configuration and user queries.
- Permission/revocation changes propagate within a defined security SLO.

### PERF-013 — Replace blocking Mongo request-thread locks

**Status:** OPEN  
**Impact:** Lock contention consumes scarce servlet threads and can cascade into global latency/timeouts.

**Evidence**

- [`MongoLockService.java`](gridee_backend/src/main/java/com/parking/app/service/lock/MongoLockService.java#L16) identifies itself as a low-traffic fallback.
- Insert/retry loops use `Thread.sleep` and can wait 5–10 seconds around [line 61](gridee_backend/src/main/java/com/parking/app/service/lock/MongoLockService.java#L61).
- Collection and 30-second lease behavior are hard-coded and do not reflect production lock properties. TTL deletion is not an exact-time unlock mechanism.

**Required work**

- [ ] Prefer an atomic domain update that removes the distributed lock when possible.
- [ ] Otherwise use a managed/shared lock with fencing token, ownership validation, renewal, and short fail-fast acquisition.
- [ ] Never spin/sleep on a servlet request thread.
- [ ] Instrument acquisition time, contention, expiry, renewal failure, and owner mismatch.

**Acceptance criteria**

- A contended lock does not pin a request thread for seconds.
- An expired former owner cannot overwrite work performed by a newer owner.

### PERF-014 — Redesign the hot-response cache

**Status:** OPEN  
**Impact:** The current cache can leak memory budget, thrash at 6,000-user cardinality, serve stale mutations, and repeat work independently on every replica.

**Evidence**

- [`HotReadResponseCacheFilter.java`](gridee_backend/src/main/java/com/parking/app/config/HotReadResponseCacheFilter.java#L28) stores up to 800 whole response bodies per process rather than by byte weight.
- Private cache identity incorporates bearer-token-related key material around [line 165](gridee_backend/src/main/java/com/parking/app/config/HotReadResponseCacheFilter.java#L165).
- It lacks request coalescing and mutation-aware invalidation, and performs O(n log n) pruning on a request thread around [line 182](gridee_backend/src/main/java/com/parking/app/config/HotReadResponseCacheFilter.java#L182).

**Required work**

- [ ] Split public and user-private caching policies.
- [ ] Key private entries by stable internal principal/resource version, never raw authorization material.
- [ ] Use a byte-weighted cache such as Caffeine for per-process hot data and Redis/CDN only where sharing is beneficial.
- [ ] Coalesce concurrent identical fills and invalidate/version on mutation.
- [ ] Record hit rate, evictions, bytes, load time, stale serves, and cardinality by route.

**Acceptance criteria**

- Cache memory has a measured byte ceiling.
- Private entries cannot cross users.
- Mutation consistency tests pass across multiple replicas.

### PERF-015 — Make horizontal replicas share correctness-critical state

**Status:** OPEN  
**Impact:** Adding replicas currently weakens correctness because rate limits, revocations, caches, scheduled jobs, and async events are process-local.

**Evidence**

- Redis-backed behavior is disabled in [`application-prod.properties`](gridee_backend/src/main/resources/application-prod.properties#L36).
- Every replica can start scheduled work from [`BookingScheduler.java`](gridee_backend/src/main/java/com/parking/app/scheduler/BookingScheduler.java#L14).
- Notifications can be sent before their database record is marked around [`NotificationScheduler.java`](gridee_backend/src/main/java/com/parking/app/scheduler/NotificationScheduler.java#L142).
- The event executor in [`ExecutionConfig.java`](gridee_backend/src/main/java/com/parking/app/config/ExecutionConfig.java#L20) is bounded but in-memory and therefore loses queued work on process failure.

**Required work**

- [ ] Put abuse/rate controls at a shared gateway or Redis layer.
- [ ] Share token revocation/security state where immediate enforcement is required.
- [ ] Use leader election or, preferably, atomic per-job claims with leases.
- [ ] Use a durable outbox/queue with idempotent consumers for notifications and external events.
- [ ] Prove rolling deployments do not duplicate scheduled work.

**Acceptance criteria**

- Adding or restarting replicas does not reset limits, duplicate jobs, lose events, or return inconsistent private cache state.

### PERF-016 — Standardize booking pagination and preserve metadata

**Status:** OPEN  
**Impact:** Active bookings can be silently truncated while history responses grow without a bound.

**Evidence**

- Android booking calls have no page/size contract in [`ApiService.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/api/ApiService.kt#L213).
- The backend active-booking endpoint defaults to 10 items in [`BookingController.java`](gridee_backend/src/main/java/com/parking/app/controller/BookingController.java#L290).
- [`BookingPayloadParser.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/model/BookingPayloadParser.kt#L10) discards server pagination metadata.
- History is returned as a full list around [`BookingController.java`](gridee_backend/src/main/java/com/parking/app/controller/BookingController.java#L305).

**Required work**

- [ ] Define cursor-based response envelopes with `items`, `nextCursor`, and snapshot/version semantics.
- [ ] Use 20–50 item pages and a server-enforced maximum.
- [ ] Preserve metadata through repository/UI layers.
- [ ] Provide explicit `GET /bookings/{id}` access for active and archived records.

**Acceptance criteria**

- No user loses items because a default page size was silently ignored.
- Large histories load incrementally and deterministically.

### PERF-017 — Stop downloading and reprocessing whole datasets

**Status:** OPEN  
**Impact:** Latency, memory, backend bandwidth, and main-thread work grow with user history rather than visible content.

**Evidence**

- [`BookingDetailsActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bookings/BookingDetailsActivity.kt#L226) downloads history to locate one booking.
- [`BookingHistoryPlaceholderActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/profile/BookingHistoryPlaceholderActivity.kt#L238) performs sequential lot/spot enrichment.
- [`TransactionHistoryActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/activities/TransactionHistoryActivity.kt#L441) requests up to 1,000 records, creates multiple list copies/fake 20-item pages, and adds an approximately 850 ms artificial delay.
- [`TransactionService.java`](gridee_backend/src/main/java/com/parking/app/service/TransactionService.java#L50) accepts size/sort behavior without a strict cap and allowlist.

**Required work**

- [ ] Add by-ID booking/history endpoints.
- [ ] Denormalize immutable display labels required by history rows.
- [ ] Implement bounded server cursor pagination and sort allowlists.
- [ ] Use Paging 3 plus a local cache where offline history matters.
- [ ] Remove fake loading delays and per-scroll reverse scans.

**Acceptance criteria**

- First content cost is independent of total account history.
- No endpoint can be coerced into returning an unbounded or arbitrarily sorted result.

### PERF-018 — Normalize support-chat messages and deliver deltas

**Status:** OPEN  
**Impact:** Every poll and append becomes more expensive as a ticket grows; concurrent saves can lose messages.

**Evidence**

- [`SupportTicketChatActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/profile/SupportTicketChatActivity.kt#L836) polls at approximately 3/6/12/30 seconds and hashes all messages around [line 1384](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/profile/SupportTicketChatActivity.kt#L1384).
- Messages are embedded in [`SupportTicket.java`](gridee_backend/src/main/java/com/parking/app/model/SupportTicket.java#L32).
- [`SupportService.java`](gridee_backend/src/main/java/com/parking/app/service/SupportService.java#L125) reads and saves the growing parent document on append; ticket lists are unpaged.

**Required work**

- [ ] Store messages in a dedicated collection keyed by ticket and monotonic cursor/time.
- [ ] Require a unique client message ID for retry safety.
- [ ] Read only messages after the last cursor; provide ETag/version fallback.
- [ ] Push new-message events and retain bounded polling only for recovery.
- [ ] Paginate ticket lists and older messages.

**Acceptance criteria**

- Appending a message is O(1)-like with respect to ticket history.
- Concurrent/retried sends neither lose nor duplicate a message.

### PERF-019 — Validate and add indexes for hot query shapes

**Status:** OPEN  
**Impact:** User history, lookup, and device/support queries will degrade as collections grow.

**Candidate indexes to validate**

- Transactions: `(userId, timestamp desc)`.
- Payment gateway order/event identifiers: unique/partial as appropriate. The current gateway-order index in [`MongoIndexConfig.java`](gridee_backend/src/main/java/com/parking/app/config/MongoIndexConfig.java#L379) is not unique.
- Booking history: `(userId, checkInTime desc)` and `(userId, lotId, checkInTime desc)`.
- Normalized user lookup fields used during authentication/operator workflows.
- Devices: `(deviceId, active)`.
- Parking spots: `(lotId, active)`.
- Support tickets/messages: indexes matching user/admin list and delta-message queries.

**Required work**

- [ ] Capture actual production query shapes, cardinalities, and slow-query logs.
- [ ] Run Atlas `explain("executionStats")` against production-like data.
- [ ] Remove redundant indexes before adding new ones; estimate write/storage cost.
- [ ] Roll out large indexes safely and monitor build impact.

**Acceptance criteria**

- Hot reads are covered/selective and meet p95/p99 targets at forecast data volume.
- Duplicate financial identifiers are rejected by the database, not only application code.

### PERF-020 — Move operator lookup work into indexed database queries

**Status:** OPEN  
**Impact:** Broad regex and Java-side sorting/load grow linearly with booking volume and can be abused as expensive queries.

**Evidence**

- [`BookingQueryService.java`](gridee_backend/src/main/java/com/parking/app/service/booking/BookingQueryService.java#L166) performs raw case-insensitive vehicle regex lookup followed by Java min/max work.
- PIN lookup loads and sorts in Java around [line 209](gridee_backend/src/main/java/com/parking/app/service/booking/BookingQueryService.java#L209).

**Required work**

- [ ] Persist a canonical normalized vehicle-plate representation.
- [ ] Prefer indexed equality; if partial search is required, anchor/escape it and explicitly constrain result count.
- [ ] Move sort and `limit(1)` into the database with matching compound indexes.
- [ ] Add query cost/rate controls to operator endpoints.

**Acceptance criteria**

- Operator lookups examine a bounded/indexed set and return one deterministic result.

### PERF-021 — Deduplicate FCM token registration

**Status:** OPEN  
**Impact:** Foreground transitions create redundant Firebase token fetches and registration POSTs, adding network/backend noise and race conditions.

**Evidence**

- Registration is invoked during application start and foreground transitions in [`GrideeApplication.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/GrideeApplication.kt#L66) and around [line 87](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/GrideeApplication.kt#L87).
- [`NotificationTokenManager.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/NotificationTokenManager.kt#L26) always fetches/posts without a persisted acknowledged `(user, token, appVersion)` tuple, single-flight protection, or durable backoff.

**Required work**

- [ ] Use `FirebaseMessagingService.onNewToken` as the primary trigger.
- [ ] Persist the last acknowledged user/token tuple.
- [ ] Reconcile through unique WorkManager work with constraints and exponential backoff.
- [ ] Make the backend registration upsert idempotent.

**Acceptance criteria**

- An unchanged token/user causes no repeated registration request across foreground transitions.

## P1: Android smoothness, lifecycle, and memory

### PERF-022 — Fix fragment restoration after process death

**Status:** DONE — 2026-09-05  
**Impact:** Android can restore old fragments while the activity creates new lazy instances, producing duplicate tabs, wrong visibility, duplicated observers, and unpredictable back behavior.

**Resolution**

- [x] [`RestorableTabFragmentRegistry.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/main/RestorableTabFragmentRegistry.kt) now owns the four root tabs by unique stable tag, exact class, and the activity root-container ID. It synchronously rebinds every valid FragmentManager-restored instance before any factory can run, removes stable-tag conflicts, legacy duplicates, and unknown fragments from the owned container before their views start work, and creates only a genuinely missing selected tab.
- [x] [`MainContainerActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt) saves the selected tab explicitly, normalizes invalid restored/intent IDs, makes the selected fragment primary/visible/`RESUMED`, holds every inactive root at hidden/`STARTED`, and routes taps, swipes, intent navigation, and retained prewarming through the same registry without state-loss commits.
- [x] Legacy tagless root state is migrated without discarding valid fragment state. Same-class fragments in foreign containers remain untouched, while stale fragments in Main's exclusively owned container are removed.
- [x] All 22 concrete production Fragment classes are default-`FragmentFactory` constructible. Restoration-sensitive sheets now keep launch data and one-shot progress in primitive arguments/saved state and return actions through Fragment Results or an explicit restored-host rebind instead of required constructor callbacks.
- [x] Booking pass inputs use a versioned primitive snapshot with a one-time deployed-serialization migration. The parking-sheet draft now preserves the chosen vehicle and times through process recreation, validates the vehicle against the refreshed profile, refuses silent fallback when a saved plate disappeared, clamps stale times to current session bounds, and requires review after an adjustment.
- [x] Signup-gift, Profile child sheets, booking QR pass, vehicle actions, and wallet payment-outcome launches reject destroyed/state-saved managers and stable-tag duplicates. Pending results/cooldowns survive recreation, and delayed view/animation callbacks are cancelled during teardown.
- [x] `ParkingDiscoveryActivity` saves map/list selection and reuses the exact restored fragment under stable tags.

**Verification evidence**

- A forced, non-incremental `testDebugUnitTest` run passed all 342 tests across 66 suites with zero failures, errors, or skips. Fourteen PERF-022-focused suites account for 70 passing tests, including parcelled process recreation, repeated recreation, configuration and night-mode changes, low-memory restoration, duplicate/unknown-root removal before view work, inactive lifecycle caps, default FragmentFactory construction, booking draft/pass restoration, exact-once vehicle actions, and late payment/dialog state.
- `assembleDebug`, `assembleRelease`, `bundleRelease`, and `lintVitalRelease` passed in one production-gate run. The generated release APK verifies with APK Signature Scheme v2 and exactly one signer; Gradle also completed `signReleaseBundle` for the AAB.
- Static checks pass with no lazy fresh root-tab fields, root-container `findFragmentById` fallback, or allowing-state-loss commits in Main; `git diff --check` is clean.
- No ADB device was attached during final verification, so the optional physical-device “Don't keep activities” smoke was not run; the automated tests destroy hosts, parcel saved state, and create new instances to exercise the corresponding restoration path.

**Acceptance criteria**

- [x] Exactly one instance of each intended tab exists after every tested recreation.
- [x] Only the selected tab is resumed/visible; duplicates are removed before view creation can register a second observer or initial network job.

### PERF-023 — Deduplicate and lifecycle-bind scroll listeners

**Status:** DONE — 2026-09-10 (local implementation and acceptance verified)  
**Owner:** Android  
**Impact:** Revisiting tabs can accumulate listeners, multiply work per scroll event, retain views, and produce increasingly janky navigation.

**Resolution**

- [`LifecycleBoundScrollListener.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/base/LifecycleBoundScrollListener.kt) owns one replaceable subscription. Repeated setup reuses it, a replacement removes the previous subscription, and view-lifecycle destruction clears the listener, view, lifecycle, and callback references.
- The listener is registered only while its view is attached and its view lifecycle is `RESUMED`. It detaches on pause/window detachment and re-registers against the current live observer on resume/attachment. Notifications caused only by another view scrolling do not repeat UI work.
- [`BaseActivityWithBottomNav.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/base/BaseActivityWithBottomNav.kt) uses this binding for bottom-navigation pressure and clears it during activity destruction. Existing screen-owned scroll callbacks and one-time bottom padding are preserved.
- [`MainContainerActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt) supplies the selected fragment's **view** lifecycle owner, rejects late setup after destruction, and uses a separate instance for Home's referral chip. The old retained `referralScrollHookedView` and anonymous observer registrations are removed. Referral direction thresholds and top-zone behavior remain intact.

**Required work**

- [x] Store one listener reference per bound view lifecycle.
- [x] Make setup idempotent.
- [x] Remove listeners on view-lifecycle destruction and activity destruction.
- [x] Count callbacks during repeated tab churn in regression tests.

**Verification evidence**

- All **397 unit tests / 74 suites** passed with zero failures, errors, or skips, including **12 new Robolectric tests** for listener ownership, 100 fragment switches, teardown, recreation, pause/resume, detach/reattach, screen-owned listener coexistence, and NestedScrollView/ScrollView/RecyclerView offsets.
- Before/after callback measurement: reproducing the former additive setup 101 times gives **101 callbacks for one scroll notification**; 101 bindings of the fixed implementation give **1**. The separate 100-switch regression also verifies one delivery for each changed selected-view offset.
- All **3 physical-device tests** passed on a **Samsung Galaxy A54 (SM-A546E), Android 16 / API 36**. Real touch scrolling retains **1:1 callback delivery per changed window scroll event** before and after 100 tab switches. Pause/resume and activity recreation preserve delivery. A reference-queue check proves collection of the detached root view, its child, and its view lifecycle owner while the activity, fragment, and subscription holders remain alive.
- The standalone [`scrollprobe`](Gridee_Android/android-app/scrollprobe/README.md) app compiles the production listener file verbatim, uses matching resolved AndroidX versions, has a separate application ID and no Internet permission, and does not alter the installed Gridee app. The helper and generated probe source have identical SHA-256 hashes. This is device evidence for the subscription implementation, not a whole-app UI smoke or frame-time benchmark.
- `:app:assembleDebug`, `:app:assembleRelease`, and `:app:lintVitalRelease` passed. The final release APK verifies with APK Signature Scheme v2 and one signer. Its DEX contains the production bindings and excludes the probe fixture. `git diff --check` passed.
- Commands, report links, source/artifact hashes, compatibility notes, and rollout/rollback instructions: [`PERF_023_VERIFICATION.md`](PERF_023_VERIFICATION.md).

**Acceptance criteria**

- [x] Callback multiplicity per physical scroll remains constant after 100 tab switches.
- [x] Leak detection shows no retained obsolete view tree in the tested subscription lifecycle.

**Release bookkeeping:** The verified changes remain in the existing working tree; a commit/PR link and Play rollout reconciliation are pending publication. `DONE` here records local acceptance, not a claim that the installed or Play-distributed binary contains the fix. PERF-024 is tracked separately below.

### PERF-024 — Remove eager hidden-tab view prewarming

**Status:** IN PROGRESS — implementation and automated validation complete; representative-device performance comparison pending  
**Owner:** Android  
**Impact:** Work scheduled immediately after first frame inflates startup CPU, network activity, heap, and GC, causing the home screen to feel initially smooth and then stutter.

**Resolution**

- [`MainContainerActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt) no longer schedules Bookings, Wallet, or Profile creation at 450, 750, and 1,050 ms after startup. The `prewarmInactiveFragment` path was removed completely.
- A fresh activity creates only the selected root tab through `RestorableTabFragmentRegistry.reconcileRestoredState`. An inactive destination is created only when explicitly selected by a tap, interactive swipe, or external navigation request.
- Existing process-restored tabs are still rebound by stable tag and exact class. PERF-022 restoration correctness is therefore preserved rather than discarding previously visited tab state.
- No replacement data preload was introduced. Existing repository caches remain demand-driven, so this item adds no new network or backend behavior.
- API 29+ async trace markers now bracket the existing Bookings, Wallet, and Profile switch animations, allowing target-process latency extraction without changing tab creation or navigation behavior.

**Required work**

- [x] Lazy-create tabs on first selection; no adjacent tab is retained speculatively.
- [x] Remove complete-view preloading without adding a replacement network/data preload.
- [x] Remove every synchronous commit whose purpose was startup prewarming. The selected/restored-tab reconciliation required by PERF-022 and the existing interactive transition commits remain outside that deleted speculative path.
- [ ] Compare startup trace, post-first-frame CPU, retained heap, and tab-switch latency before/after.

**Verification evidence**

- All **397 unit tests / 74 suites** passed with zero failures, errors, or skips. The 15 focused main-container/registry tests verify fresh lazy creation, stable tags, direct tap/swipe creation paths, partial and full process restoration, configuration changes, repeated recreation, lifecycle caps, and duplicate removal.
- `:app:assembleDebug`, `:app:assembleRelease`, `:app:bundleRelease`, and `:app:lintVitalRelease` passed. The release APK verifies with APK Signature Scheme v2 and one signer; the release AAB is signed and verifies as a JAR.
- An authenticated Android 14 / API 34 emulator smoke showed exactly one root tab (`HomeFragment`) eight seconds after fresh launch and exactly two after the first Bookings selection. No app crash or ANR was observed. This proves the UI creation sequence, not representative performance.
- The offline benchmark harness completed a 10-iteration Bookings/Profile dry-run on the API 34 emulator and emitted both target-process switch-duration metrics plus frame, memory, and Perfetto artifacts. Emulator timings are explicitly non-gating; Wallet remains a normal-QA-app device measurement because its existing empty-state animation is remotely hosted.
- Detailed commands, artifact hashes, scope, and the remaining device gate: [`PERF_024_VERIFICATION.md`](PERF_024_VERIFICATION.md).

**Acceptance criteria**

- [x] A fresh startup creates only the selected root-tab view; restored user-visible state remains eligible for normal FragmentManager restoration.
- [x] No speculative tab preloading remains.
- [ ] Before/after startup CPU, retained heap, frame timing, and first tab-switch latency are recorded on the approved representative-device matrix.

**Release bookkeeping:** The implementation remains in the existing dirty working tree and has not been claimed as Play-distributed. Keep PERF-024 `IN PROGRESS` until the representative-device measurements are recorded; do not start PERF-025 as part of this item.

### PERF-025 — Shorten splash and remove per-frame allocations

**Status:** OPEN  
**Impact:** Users wait through an artificial startup floor, potentially exceeding five seconds with configuration delay, while frame allocations increase jank risk.

**Evidence**

- The splash sequence includes approximately 120 + 1,300 + 280 + 850 ms of staged delay in [`SplashActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/auth/SplashActivity.kt#L146).
- Configuration can add up to roughly three seconds around [line 323](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/auth/SplashActivity.kt#L323).
- Blur `RenderEffect` and `LinearGradient` work occurs during animation frames around [line 168](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/auth/SplashActivity.kt#L168).

**Required work**

- [ ] Route from cached authentication/configuration immediately when safe.
- [ ] Make branding brief and non-blocking.
- [ ] Cache shader/effect objects and update a matrix/property rather than allocating per frame.
- [ ] Call `reportFullyDrawn()` when usable content is actually ready.
- [ ] Measure cold/warm TTID and TTFD on representative low/mid/high-tier devices.

**Acceptance criteria**

- Proposed initial gate: cold TTID p95 under 2 seconds and TTFD p95 under 2.5–3 seconds on the chosen mid-tier reference device.
- No artificial delay holds navigation after required state is available.

Reference: [Android app startup time and time to full display](https://developer.android.com/topic/performance/vitals/launch-time).

### PERF-026 — Pause hidden animations and eliminate repeated frame work

**Status:** OPEN  
**Impact:** Invisible/repeated animations consume main-thread time, GPU work, battery, and thermal budget, increasing sustained jank.

**Evidence**

- [`HomeFragment.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/HomeFragment.kt#L195) creates a temporary reward introduction on every resume.
- [`BrandFollowSwitcher.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/views/BrandFollowSwitcher.kt#L154) can rebuild particles even for the same color, and reads/sorts full bitmap pixel arrays on the main thread around [line 221](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/views/BrandFollowSwitcher.kt#L221).
- [`RewardCoinView.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/views/RewardCoinView.kt#L321) creates gradient objects during glint rendering.
- [`RibbonBannerView.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/views/RibbonBannerView.kt#L138) requests callbacks every vsync even when `GONE` and stops only on detach.
- Brand animation similarly runs a long loop and primarily stops at detach around [`BrandFollowSwitcher.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/views/BrandFollowSwitcher.kt#L446).

**Required work**

- [ ] Remove disabled decorative views instead of hiding an active loop.
- [ ] Pause on visibility, lifecycle, window focus, and reduced-motion state.
- [ ] Precompute pixel/particle analysis on a background dispatcher and cache by immutable asset/color.
- [ ] Cache Paint, Shader, Matrix, Path, and other render objects.
- [ ] Profile with system tracing and frame metrics rather than relying on visual inspection.

**Acceptance criteria**

- Hidden/background screens schedule no continuous animation frames.
- Render-thread/main-thread traces show no avoidable per-frame allocation from these components.

### PERF-027 — Restore RecyclerView virtualization and diffed updates

**Status:** OPEN  
**Impact:** Nested scrolling and wrap-content measurement can inflate every list item, defeating recycling and making latency/memory grow with the full dataset.

**Evidence**

- Booking history nests a wrap-content RecyclerView inside `NestedScrollView` in [`activity_booking_history_placeholder.xml`](Gridee_Android/android-app/app/src/main/res/layout/activity_booking_history_placeholder.xml#L12).
- Lot selection repeats the pattern in [`activity_select_parking_lot.xml`](Gridee_Android/android-app/app/src/main/res/layout/activity_select_parking_lot.xml#L81).
- [`BookingHistoryPlaceholderActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/profile/BookingHistoryPlaceholderActivity.kt#L109) loads/sorts/maps the full result and animates the collection.
- [`BookingsAdapter.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/adapters/BookingsAdapter.kt#L237) uses `notifyDataSetChanged()`.

**Required work**

- [ ] Make RecyclerView the sole vertical scroller.
- [ ] Move headers/empty/loading states into adapter view types or `ConcatAdapter`.
- [ ] Use Paging 3 and `ListAdapter`/`DiffUtil` with stable IDs.
- [ ] Disable expensive whole-list entrance animation for large/restored datasets.

**Acceptance criteria**

- Created/bound view-holder count remains proportional to viewport, not total records.
- Updating one record does not rebind the full list.

### PERF-028 — Generate QR bitmaps off-main and bound their memory

**Status:** OPEN  
**Impact:** Recycler binding can block the main thread, while an unbounded bitmap map can consume tens of MiB during a long session.

**Evidence**

- [`BookingRailAdapter.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/adapters/BookingRailAdapter.kt#L37) owns an unbounded bitmap map.
- QR generation begins from bind-time work around [line 388](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/adapters/BookingRailAdapter.kt#L388).
- [`BookingQrCodeGenerator.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/BookingQrCodeGenerator.kt#L40) performs ZXing encoding, ARGB bitmap creation, and pixel loops. At 3x density, a roughly 456x456 ARGB bitmap is about 0.8 MiB before overhead.

**Required work**

- [ ] Generate on `Dispatchers.Default` at the actual target pixel size.
- [ ] Cancel work when a holder is recycled or payload changes.
- [ ] Use a byte-sized `LruCache` keyed by immutable QR payload/style/version.
- [ ] Avoid caching expired booking QR values and clear sensitive images appropriately.

**Acceptance criteria**

- Binding never performs QR encoding/pixel loops on the main thread.
- QR cache memory remains under an explicit tested ceiling.

### PERF-029 — Stop forcing maximum refresh rate globally

**Status:** OPEN  
**Impact:** Maximum refresh rate increases battery and thermal load; throttling can make long sessions less smooth than adaptive system behavior.

**Evidence**

- [`MainContainerActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt#L858) globally requests the maximum supported display refresh rate.

**Required work**

- [ ] Remove the global override and let Android select adaptively.
- [ ] If a specific interaction proves a need, scope the request to that visible surface and duration.
- [ ] Compare frame pacing, energy, surface temperature, and throttling during a 30-minute session.

**Acceptance criteria**

- Refresh policy is evidence-based and does not reduce sustained performance or battery SLOs.

### PERF-030 — Stop profile accordion height mutation on every frame

**Status:** OPEN  
**Impact:** Continuous `requestLayout` traversals and row reinflation produce avoidable main-thread work.

**Evidence**

- [`ProfileFragment.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/ProfileFragment.kt#L704) changes container height/request-layout during animation.
- Vehicle rows are cleared/reinflated around [line 499](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/ProfileFragment.kt#L499).

**Required work**

- [ ] Represent vehicles as a RecyclerView/ListAdapter section.
- [ ] Prefer clip/translation/content-fade animation that does not remeasure the full tree each frame.
- [ ] Preserve accessibility semantics and focus as the section changes.

**Acceptance criteria**

- Accordion animation has no repeated full-tree layout spike in a trace.
- Vehicle updates bind only changed rows.

### PERF-031 — Debounce and pre-normalize Home search

**Status:** OPEN  
**Impact:** Every keystroke performs repeated string normalization, filtering, chunking, dot rebuilding, and rendering on the UI path.

**Evidence**

- Text changes are handled immediately in [`HomeFragment.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/HomeFragment.kt#L1774).
- Search rendering/filtering work appears around [line 898](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/HomeFragment.kt#L898) and page/dot reconstruction around [line 1077](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/HomeFragment.kt#L1077).

**Required work**

- [ ] Debounce input by approximately 150–250 ms and cancel obsolete searches.
- [ ] Precompute normalized searchable fields when lot data changes.
- [ ] Filter/score larger data off-main and emit immutable diffable results.
- [ ] Preserve instant clearing and accessibility announcements.

**Acceptance criteria**

- Fast typing produces at most one active computation and no visible frame regression.

### PERF-032 — Reduce wallet count-up animation allocations

**Status:** OPEN  
**Impact:** Currency formatting and span construction every frame creates allocation/GC pressure for a cosmetic effect.

**Evidence**

- [`WalletFragmentNew.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/WalletFragmentNew.kt#L640) drives balance animation; formatting and styled spans occur around [line 733](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/fragments/WalletFragmentNew.kt#L733).

**Required work**

- [ ] Update only when the displayed integer/minimum currency unit changes.
- [ ] Avoid rebuilding spans for every animation callback.
- [ ] Set one exact final formatted value at completion.
- [ ] Respect disabled/reduced animation.

**Acceptance criteria**

- Allocation profiling shows no per-vsync text/span churn.

### PERF-033 — Remove static activity and view retention

**Status:** OPEN  
**Impact:** Process-singleton references can retain destroyed activities, large view trees, animations, and callbacks.

**Evidence**

- [`NotificationHelper.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/NotificationHelper.kt#L20) stores `View`, `ViewGroup`, runnable, and animation state in an object and primarily clears it on dismissal around [line 451](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/NotificationHelper.kt#L451).
- [`AdConsentManager.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/AdConsentManager.kt#L15) stores a process callback that can capture Main activity, with paths around [line 29](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/AdConsentManager.kt#L29) and [line 73](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/AdConsentManager.kt#L73).

**Required work**

- [ ] Bind UI work to a `LifecycleOwner` and clear it on host destruction.
- [ ] Prefer event/state delivery over singleton view ownership.
- [ ] Make consent callbacks lifecycle-aware or weak and guarantee timeout/cancellation.
- [ ] Add LeakCanary to debug builds or equivalent automated retained-object checks.

**Acceptance criteria**

- Repeated activity recreation retains no obsolete activity/view instance.

### PERF-034 — Resolve layout complexity, overdraw, and broad list updates

**Status:** OPEN  
**Impact:** Deep/large view trees, redundant backgrounds, draw allocations, and full refreshes raise measure/layout/draw cost across common devices.

**Evidence**

- Lint found 43 overdraw warnings, 3 excessive-view layouts, 3 excessive-depth layouts, 4 draw allocations, and 4 `notifyDataSetChanged()` sites.
- High-complexity areas include Display Theme, Operator Dashboard, Home, `ParkingSpot_bottomsheet`, and Profile.
- Reward amount drawing allocates around [`RewardAmountView.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/views/RewardAmountView.kt#L200).

**Required work**

- [ ] Use Layout Inspector/system traces to prioritize actually hot screens.
- [ ] Flatten redundant containers and remove covered backgrounds.
- [ ] Cache draw objects; never allocate in `onDraw`.
- [ ] Replace broad adapter notifications with payload-aware diffing.
- [ ] Fix all accessibility content-description failures while changing layouts.

**Acceptance criteria**

- Selected hot screens meet frame and accessibility gates on reference devices.
- Relevant lint findings are zero or have documented, reviewed suppressions.

### PERF-035 — Migrate legacy back handling for Android 16

**Status:** OPEN  
**Impact:** With target API 36, legacy `onBackPressed()` overrides may no longer be invoked, breaking navigation and predictive-back behavior.

**Evidence**

- Legacy overrides exist in [`ChooseCategoryActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/lot/ChooseCategoryActivity.kt#L208), [`MainContainerActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt#L1077), and [`SelectParkingLotActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/lot/SelectParkingLotActivity.kt#L378).

**Required work**

- [ ] Migrate activity/fragment behavior to `OnBackPressedDispatcher` and lifecycle-aware callbacks.
- [ ] Integrate predictive-back progress/cancel/commit where custom animation exists.
- [ ] Test gesture and button back on API 33–36, including nested sheets and unsaved state.

**Acceptance criteria**

- All back paths work on target API 36 and preview correctly under predictive back.

References: [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16) and [Predictive Back guidance](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture).

## Release engineering, observability, security, and maintainability

### PERF-036 — Enable staged R8 and resource shrinking

**Status:** OPEN  
**Impact:** The release ships unused code/resources and broad keep rules, increasing download size, install footprint, class loading, and attack surface.

**Evidence**

- Release minification is disabled in [`app/build.gradle`](Gridee_Android/android-app/app/build.gradle#L108), and resource shrinking is not enabled.
- [`proguard-rules.pro`](Gridee_Android/android-app/app/proguard-rules.pro#L13) broadly keeps application/model classes and can blunt R8 optimization.
- Lint reports 834 unused resources. Three apparently unused large JPEG assets total roughly 2.1 MiB, and four font files are around 400 KiB each.
- The diagnostic release APK was approximately 28.5 MiB and expanded to about 67.7 MiB. Treat this only as a baseline because lint was skipped.

**Required work**

- [ ] Remove confirmed unused dependencies and resources first.
- [ ] Enable `minifyEnabled` and `shrinkResources` in a non-production release flavor.
- [ ] Replace broad keep rules with rules justified by reflection, JNI, serialization, or vendor SDK requirements.
- [ ] Run all navigation/payment/scanner/notification flows against the optimized artifact.
- [ ] Upload mapping and native symbols as part of release.
- [ ] Compare AAB download size, installed size, startup, and regressions before rollout.

**Acceptance criteria**

- Optimized release passes the complete production test matrix.
- Mapping/symbol retrieval is proven with a test crash.
- Size improvement and any runtime effect are recorded.

Reference: [Enable app optimization with R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).

### PERF-037 — Add an app-owned baseline profile

**Status:** OPEN  
**Impact:** Critical Gridee journeys receive no deliberate ahead-of-time profile optimization on first launch/update.

**Evidence**

- No app-owned baseline-profile rules or generation flow was found.
- Packaged dependency profiles contained thousands of rules but no meaningful `com/gridee` journey coverage.
- The current macrobenchmark work is scanner-focused and was untracked in the audited checkout.

**Required work**

- [ ] Generate profiles for startup/login, Home, lot/spot selection, booking, Bookings, QR scanner, wallet top-up/payment, and operator scan.
- [ ] Keep profile generation deterministic and authentication/test-data safe.
- [ ] Validate profile installation and compilation state on release-like builds.
- [ ] Measure cold startup and journey benchmarks with/without the profile on physical devices.

**Acceptance criteria**

- The release AAB contains current app-owned rules for measured critical journeys.
- CI detects profile-generation failures and benchmark regressions.

Android reports that Baseline Profiles can improve first-launch code execution by about 30%; this is a general potential benefit, not a promised Gridee result. Reference: [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview).

### PERF-038 — Rationalize startup SDK initialization

**Status:** OPEN  
**Impact:** Synchronous work before/around the first screen competes for startup CPU and I/O; duplicate SDK setup adds risk without user value.

**Evidence**

- [`GrideeApplication.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/GrideeApplication.kt#L53) initializes theme, locale, configuration, notification channels, Cashfree, and FCM-related work.
- Cashfree also appears to initialize via a merged-manifest provider.
- ML Kit, Mobile Ads, Unity, Firebase, and related providers contribute additional automatic startup work.

**Required work**

- [ ] Trace cold startup with Perfetto/system tracing before removing anything.
- [ ] Memoize configuration reads and eliminate duplicate Cashfree initialization.
- [ ] Defer ads, scanner/ML, review, and other optional SDKs until their first valid use or a measured idle window.
- [ ] Use explicit initializer dependencies where automatic provider order is necessary.
- [ ] Confirm lazy initialization does not move a worse stall into a critical interaction.

**Acceptance criteria**

- Every startup initializer has an owner, dependency, measured cost, and documented eager/lazy decision.

Reference: [Android App Startup](https://developer.android.com/topic/libraries/app-startup).

### PERF-039 — Add production crash, ANR, startup, jank, and API observability

**Status:** OPEN  
**Priority note:** Although grouped here, this is P1 and should begin in the first week.  
**Impact:** The team cannot reliably detect, prioritize, or verify production smoothness and stability regressions.

**Evidence**

- No Crashlytics, Sentry, Bugsnag, Firebase Performance, or equivalent app-wide integration was found.
- No release mapping/native-symbol upload workflow was found.
- [`NetworkTimingEventListener.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/api/NetworkTimingEventListener.kt#L176) is a useful privacy-conscious base, but most non-operator traffic collapses into an `other` route, and HTTP 4xx/5xx can be classified as transport success.
- JankStats coverage is concentrated in the scanner rather than key app journeys.

**Required work**

- [ ] Add privacy-reviewed crash/ANR reporting with release, build, device, OS, and screen context.
- [ ] Collect `ApplicationExitInfo`, mapping files, and native symbols.
- [ ] Record TTID/TTFD, frame timing, slow/frozen frames, memory/LMK, and key journey duration.
- [ ] Record sampled, templated API route, status class, failure type, DNS/connect/TLS/server timing, and request correlation ID without PII/tokens.
- [ ] Build Play Android Vitals and backend dashboards with alerts, release comparison, and rollback ownership.
- [ ] Add product-level correctness metrics: duplicate-command rejection, pending payment age, stale-version rejection, event lag, and cache invalidation failures.

**Acceptance criteria**

- A test crash, ANR-like exit, slow startup, janky journey, and API 500 can each be found by release/route/device without exposing sensitive data.
- On-call alerts link to a runbook and responsible owner.

Reference: [Android vitals](https://developer.android.com/topic/performance/vitals).

### PERF-040 — Remove sensitive and noisy production logs

**Status:** DONE — 2026-09-05  
**Impact:** Authentication and booking data can be exposed through log collection, while high-volume logs add CPU/I/O and obscure useful signals. With R8 disabled, these calls remain in release code.

**Original evidence (before remediation)**

- The audit counted 311 `Log`/print/stack-trace calls across 30 source files but only 16 `BuildConfig.DEBUG` references.
- [`LoginViewModel.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/auth/LoginViewModel.kt) logged email/name/token-related information and a JWT prefix in nearby paths.
- [`GoogleSignInManager.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/GoogleSignInManager.kt) logged identity/authentication details.
- [`BookingRepository.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/repository/BookingRepository.kt) logged user, lot, spot, time, and vehicle context.

**Resolution**

- [x] Removed token, identity, email, vehicle, booking, payment, wallet, operator, and scanner-value logs from Android production source.
- [x] Replaced direct platform logging with one lazy, debug-only [`AppLog.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/AppLog.kt) gateway. Its production Logcat allowlist is deliberately empty; it has no eager-message or throwable overload that could evaluate or retain sensitive data in release builds.
- [x] Removed the OkHttp logging-interceptor dependency, unbounded `peekBody`, and raw URL/request/response/error-body logging from the network and JWT layers.
- [x] Reduced the remaining debug diagnostics to fixed state, bounded numeric result/timing values, app-owned enum values, and exception class names. Production observability, redaction, sampling, and crash reporting remain owned by PERF-039 rather than Logcat.
- [x] Added [`ProductionLoggingPolicyTest.kt`](Gridee_Android/android-app/app/src/test/java/com/gridee/parking/utils/ProductionLoggingPolicyTest.kt), which scans main and release source roots, enforces the single gateway and exact caller allowlist, rejects direct/common logging and raw HTTP logger paths, requires lazy calls, and rejects known sensitive/uncontrolled values.
- [x] Added [`ReleaseAppLogTest.kt`](Gridee_Android/android-app/app/src/testRelease/java/com/gridee/parking/utils/ReleaseAppLogTest.kt), which exercises every log level in the release variant and proves message lambdas are not evaluated.
- [x] Wired `preReleaseBuild` to the debug unit suite so the source policy must pass before release APK/AAB assembly. The debug-only JWT test screen may still display diagnostic values on screen by design, but it no longer forwards them to Logcat.

**Verification evidence**

- Main production source changed from 311 direct `Log`/print/stack-trace calls across 30 files to zero direct platform Logcat calls outside the gateway, zero console/stack-trace sinks, and zero raw HTTP logger paths. The 47 retained lazy debug diagnostics are confined to nine reviewed files.
- Compiled release Kotlin/Java bytecode contains zero `android/util/Log` references, including the release form of the gateway.
- A forced uncached `testDebugUnitTest` run passed all 272 tests across 52 suites with zero failures, errors, or skips; the four production logging-policy tests passed. The release-specific all-level gateway test also passed.
- `lintVitalRelease`, `assembleRelease`, and `bundleRelease` passed. The release APK remained v2-signed with one signer and passed 16 KiB zip-alignment verification; the AAB passed JAR-signature verification.
- Full debug lint still reports the pre-existing 309 errors and 1,719 warnings recorded by PERF-004, so this remediation introduced no new lint-count regression.

**Acceptance criteria**

- [x] Release Logcat contains no secret/PII values under login, booking, payment, wallet, operator, or scanner paths because the release gateway does not emit or evaluate messages.
- [x] Debug-only diagnostics are compiled out of production behavior and guarded against future known bypasses and sensitive patterns.

### PERF-041 — Add Android CI, instrumented tests, and performance regression gates

**Status:** OPEN  
**Impact:** Unit tests alone cannot detect lifecycle, process-death, rendering, API-level, packaging, integration, or device performance regressions.

**Evidence**

- 259 unit tests passed across 49 suites.
- No `androidTest` files were found.
- No Android build/test workflow was found in CI; the existing workflow is web-deployment focused.
- No dependency update/verification automation or broad physical-device benchmark matrix was found.

**Required work**

- [ ] Make clean unit test, full lint, and unskipped `bundleRelease` mandatory.
- [ ] Add device tests on API 24, 25, 26, 29, 34, and 36.
- [ ] Cover rotation/process death, login, Home, booking concurrency, Bookings resume, QR scanning, wallet/payment retry, offline recovery, notifications, and accessibility.
- [ ] Run Macrobenchmark on stable physical CI devices for startup and critical journeys.
- [ ] Add Gradle dependency verification/locking and a reproducible toolchain.
- [ ] Archive reports, mappings, profiles, size analysis, and benchmark history by commit.

**Acceptance criteria**

- A pull request cannot merge if any required release, compatibility, correctness, or agreed regression gate fails.

Reference: [Run benchmarks in Continuous Integration](https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci).

### PERF-042 — Align dependencies and make the build toolchain reproducible

**Status:** OPEN  
**Impact:** Stale/misaligned declarations and unpinned build inputs increase compatibility and supply-chain risk.

**Evidence**

- Kotlin plugin 2.3.0 is paired with a Compose BOM declaration resolving around Compose 1.5.4-era artifacts.
- Firebase BOM 32.8.1 is declared while some direct versions are silently overridden.
- Both Play in-app update base and KTX artifacts are declared redundantly; Navigation/ViewPager2 also appear potentially unused and require confirmation.
- JDK 22 compiles Java source/target 8, producing an obsolete-target warning, with no declared Java toolchain.
- The Gradle wrapper lacks `distributionSha256Sum`.

**Required work**

- [ ] Establish one version catalog/BOM strategy and document every exception.
- [ ] Declare a supported Java toolchain and remove obsolete compiler configuration.
- [ ] Add Gradle dependency verification, locks where appropriate, and the wrapper checksum.
- [ ] Remove dependencies only after usage/APK and regression verification.
- [ ] Plan migration from deprecated sign-in/auth APIs such as legacy Google Sign-In to Credential Manager where applicable.

**Acceptance criteria**

- A clean environment resolves verified, deterministic dependencies and uses the declared JDK/toolchain.
- Dependency declarations match the versions actually compiled.

### PERF-043 — Remove or feature-gate dormant legacy flows

**Status:** OPEN  
**Impact:** Dead or unfinished flows remain a future accidental-entry, security, compliance, testing, and binary-size risk.

**Evidence**

- Simulated payment success remains in [`PaymentActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/booking/PaymentActivity.kt#L149) and [`PaymentViewModel.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/booking/PaymentViewModel.kt#L73).
- Mock Chennai discovery data remains in [`ParkingDiscoveryViewModel.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/discovery/ParkingDiscoveryViewModel.kt#L130), with unfinished maps/filter behavior in [`ParkingDiscoveryActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/discovery/ParkingDiscoveryActivity.kt#L147).
- [`PrivacySettingsActivity.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/profile/PrivacySettingsActivity.kt#L140) labels account deletion but only clears local data.
- [`WalletApiHelper.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/network/WalletApiHelper.kt#L102) contains a fake top-up helper.

No active top-level entry into these legacy chains was found during the static search; that is not a security boundary.

**Required work**

- [ ] Prove reachability from manifest, navigation, deep links, notifications, reflection, and remote config.
- [ ] Delete obsolete flows or exclude them from production variants.
- [ ] Feature-gate unfinished product work server-side and fail closed.
- [ ] Implement true server-side account deletion/retention workflow before exposing that promise.
- [ ] Reconcile with [`DEAD_CODE_INVENTORY.md`](DEAD_CODE_INVENTORY.md).

**Acceptance criteria**

- Production binaries contain no simulated money mutation or misleading deletion behavior.

### PERF-044 — Harden token storage, network config, channels, and native packaging

**Status:** OPEN  
**Impact:** Several smaller configuration issues collectively weaken security, notification reliability, or release efficiency.

**Evidence and required work**

- [ ] **JWT storage:** [`JwtTokenManager.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/JwtTokenManager.kt#L14) stores JWTs in plain SharedPreferences and exposes the raw token around [line 162](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/JwtTokenManager.kt#L162). Keep tokens short-lived, minimize access, prefer platform-backed encryption where its threat model helps, and rotate refresh credentials. Disabled backup is useful but not sufficient.
- [ ] **Cleartext release config:** [`network_security_config.xml`](Gridee_Android/android-app/app/src/main/res/xml/network_security_config.xml#L3) permits local cleartext development domains. Move them exclusively to [`src/debug`](Gridee_Android/android-app/app/src/debug/res/xml/network_security_config.xml).
- [ ] **FCM channel mismatch:** The manifest default channel and the ID created in [`NotificationChannels.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/notifications/NotificationChannels.kt#L8) differ (`gridee_updates` versus `gridee_live_updates_v2`). Use one stable created default and test fallback delivery.
- [ ] **Native packaging:** [`app/build.gradle`](Gridee_Android/android-app/app/build.gradle#L141) uses legacy packaging/`extractNativeLibs`; 16 KiB alignment passed, but vendor libraries are unstripped. Test modern packaging, preserve required symbols separately, and confirm install/startup behavior across supported APIs.

**Acceptance criteria**

- Release traffic cannot use development cleartext exceptions.
- Notification fallback always targets an existing channel.
- Token compromise impact and rotation behavior are documented/tested.
- Native packaging passes Play validation, API/device smoke tests, and symbolication checks.

### PERF-045 — Reduce monolithic screen and mixed-flow regression surface

**Status:** OPEN  
**Impact:** Very large activities/fragments combine rendering, state, networking, parsing, animation, and navigation, making performance regressions hard to isolate and safe changes expensive.

**Evidence**

- `QrScannerActivity`: approximately 6,330 lines.
- `ParkingSpot_bottomsheet`: approximately 2,202 lines.
- `HomeFragment`: approximately 1,882 lines.
- `BookingsFragmentNew`: approximately 1,591 lines.
- `BookingsAdapter`: approximately 1,541 lines.
- Other files over 1,000 lines include Support Chat, Operator Dashboard, Reward Bottom Sheet, Main Container, and Wallet.

**Required work**

- [ ] Do not begin a broad rewrite before the P0 correctness work.
- [ ] Extract pure domain/state machines first, with characterization tests.
- [ ] Separate repository state, UI rendering, navigation, animation, and device integration.
- [ ] Establish one supported View/Compose interop strategy and delete superseded flows.
- [ ] Track class complexity/build time/testability, not line count alone.

**Acceptance criteria**

- Critical state transitions are testable without an Activity/Fragment.
- Refactoring produces no measured startup, frame, or memory regression.

### PERF-046 — Address secondary backend and offline-efficiency issues

**Status:** OPEN  
**Impact:** These are not the first stop-ship items, but they will become meaningful with higher data volume, longer sessions, and multiple replicas.

**Evidence and required work**

- [ ] **Cache bookkeeping:** Generation bookkeeping in [`TtlSingleFlightCache.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/repository/cache/TtlSingleFlightCache.kt#L171) can retain time-based keys. Bound/remove generation records together with entries and add a long-session cardinality test.
- [ ] **Operator O(n²) work:** [`OperatorParkingSpotLoader.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/operator/OperatorParkingSpotLoader.kt#L797) repeatedly scans collections. Pre-index by identifier and benchmark large lots.
- [ ] **Mongo connection settings:** Property names near [`application-prod.properties`](gridee_backend/src/main/resources/application-prod.properties#L8) may not bind as intended under the current Spring Boot version; no explicit Mongo client customizer/pool metrics were found. Verify effective settings through the URI/customizer and expose pool wait/usage metrics.
- [ ] **Ad media delivery:** [`CustomAdService.java`](gridee_backend/src/main/java/com/parking/app/service/CustomAdService.java#L171) serves GridFS-originated content. Move immutable content-hashed media to object storage/CDN with correct caching and invalidation.
- [ ] **Daily bulk jobs:** Wallet/spot jobs perform per-record queries/locks around [`WalletService.java`](gridee_backend/src/main/java/com/parking/app/service/WalletService.java#L151) and [`ParkingSpotService.java`](gridee_backend/src/main/java/com/parking/app/service/ParkingSpotService.java#L635). Use bulk conditional operations, checkpoints, bounded batches, and one claimed worker.
- [ ] **Offline/network behavior:** [`ApiClient.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/api/ApiClient.kt#L39) has no durable HTTP response cache/call-level deadline and uses broad 30-second phase timeouts. Define endpoint-specific deadlines, retry only safe reads, and add Room/Paging stale-while-revalidate for appropriate screens. Do not queue financial mutations offline until idempotency is complete.
- [ ] **Dormant contract mismatch:** Android expects a Boolean around [`ApiService.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/api/ApiService.kt#L204), while the server returns a map in [`ParkingLotController.java`](gridee_backend/src/main/java/com/parking/app/controller/ParkingLotController.java#L107). No active caller was found; delete or align the contract before it becomes reachable.

**Acceptance criteria**

- Long-session cache/key counts remain bounded.
- Large-lot operator processing is linear or better for indexed lookups.
- Effective Mongo pool/timeouts are observable in production.
- Media and batch jobs meet explicit bandwidth/runtime/restart targets.
- Offline reads behave predictably without replaying unsafe mutations.

## Existing strengths to preserve

- [`TtlSingleFlightCache.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/repository/cache/TtlSingleFlightCache.kt#L50) already contains request coalescing, stale fallback, backoff, and invalidation concepts.
- [`ApiClient.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/api/ApiClient.kt#L15) limits fallback/retry behavior to safe `GET`/`HEAD` semantics and avoids automatic mutation retry around [line 84](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/data/api/ApiClient.kt#L84).
- Scanner analysis uses `STRATEGY_KEEP_ONLY_LATEST`, a reusable YUV buffer, JankStats, and lifecycle teardown in [`ScannerCameraController.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/ScannerCameraController.kt#L248).
- Fragment view binding is centrally cleared in [`BaseTabFragment.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/base/BaseTabFragment.kt#L36).
- Wallet code contains explicit animator/job cleanup and Compose disposal paths.
- Booking history archival is transactional/idempotent in [`BookingHistoryService.java`](gridee_backend/src/main/java/com/parking/app/service/BookingHistoryService.java#L45).
- Spot-capacity decrement is atomic in [`ParkingSpotService.java`](gridee_backend/src/main/java/com/parking/app/service/ParkingSpotService.java#L515), availability loading is batched, and recovery work is bounded.
- The backend already exposes Prometheus/tracing/SLO histogram concepts and has an extensive index/rate-limit base to refine.
- Release configuration includes HTTPS pinning, wire logging disabled, backup disabled, non-exported components, and native 16 KiB alignment that passed inspection.
- Support polling is lifecycle-aware and backs off; payment polling is bounded/single-flight on the client even though its server interaction must change.
- The current 272 passing unit tests provide useful coverage around scanner, operator logic, caches, parsers, logging policy, and animation compatibility.

## Phased execution plan

### Phase 0 — incident containment and release restoration (0–48 hours)

- [ ] PERF-001: freeze signing use; rotate upload key; purge secrets.
- [x] PERF-002: fix the orphan resource and restore unskipped release builds.
- [x] PERF-003: guard the splash animation preference across the supported API range.
- [x] PERF-004: guard the remaining API 26 animator calls.
- [ ] PERF-005: disable or securely verify rewarded credits.
- [ ] PERF-006 and PERF-007: introduce stable command IDs and atomic state transitions.
- [ ] PERF-008: stop stale mutable caching until invalidation is correct.
- [x] PERF-040: remove sensitive logs.
- [ ] PERF-039: install minimum crash/ANR reporting and prove mapping upload.

**Exit gate:** A clean, signed, unskipped bundle passes supported-API smoke tests; signing exposure is remediated; money/booking commands are replay-safe; a test crash is visible and symbolicated.

### Phase 1 — user-perceived reliability and load reduction (week 1–2)

- [ ] Replace Bookings fixed polling with events plus resume reconciliation.
- [ ] Make payment status local/webhook first.
- [ ] Standardize paging/by-ID contracts and add the first required indexes after `explain` review.
- [x] Fix fragment restoration (PERF-022).
- [ ] Fix listener duplication, hidden-tab work, and splash delay.
- [ ] Fix list virtualization/diffing, QR generation, hidden animations, Home search, and wallet animation.
- [ ] Complete Android 16 back handling.
- [ ] Establish Android CI/device tests and baseline production dashboards.

**Exit gate:** Critical journeys pass the API/device matrix, fixed high-frequency polling is gone, post-mutation state never regresses, and baseline startup/frame/API metrics are recorded.

### Phase 2 — optimized and reproducible release (week 2–4)

- [ ] Enable staged R8/resource shrinking and upload mapping/symbols.
- [ ] Add app-owned baseline profiles and physical-device Macrobenchmarks.
- [ ] Rationalize startup SDKs and dependencies/toolchain.
- [ ] Complete cursor paging/Paging 3 and normalize support chat.
- [ ] Put distributed correctness state and scheduled work under shared coordination.
- [ ] Remove or gate dormant legacy flows.

**Exit gate:** Optimized release is functionally equivalent, reproducible, benchmarked, symbolicated, and safe under rolling multi-replica deployment.

### Phase 3 — scale and resilience (week 3–6)

- [ ] Deploy durable outbox/queue and atomic scheduler claims.
- [ ] Finish shared rate limiting/revocation/cache or atomic database alternatives.
- [ ] Run payment/booking/wallet failure-injection and reconciliation tests.
- [ ] Add Room/offline behavior where justified.
- [ ] Move immutable media to CDN/object storage.
- [ ] Convert daily work to bounded bulk/checkpointed jobs.
- [ ] Load test at 2–3x forecast peak and run rolling-restart/dependency-outage exercises.

**Exit gate:** Forecast-peak tests retain capacity headroom with no pool/thread exhaustion, duplicate mutation, lost event, scheduler duplication, or uncontrolled retry storm.

## Proposed production SLOs and release gates

These are proposed internal targets, not measurements from the audited checkout. Establish device, geography, network, and traffic segments before adopting final numbers.

| Area | Proposed gate |
|---|---|
| Release pipeline | 100% pass for full lint, unit, instrumented, API compatibility, optimized bundle, and required journey tests |
| Crash-free users | At least 99.9%; move toward 99.95% for mature releases |
| User-perceived ANR | Below 0.1% |
| Cold TTID | p95 below 2 seconds on the selected mid-tier reference device |
| TTFD | p95 below 2.5–3 seconds on the selected mid-tier reference device |
| Rendering | Slow frames below 5%; frozen frames below 0.1% on critical journeys |
| Memory/lifecycle | 30-minute tab churn with no monotonic retained-fragment, listener, bitmap-cache, or heap growth |
| Read API latency | p95 below 400–500 ms; p99 below 1 second for normal in-region reads |
| API errors | Below 0.5%, with dependency failures separated from client and server failures |
| Financial integrity | Zero duplicate wallet/payment/booking effects under replay, timeout, concurrent, webhook, and crash tests |
| Capacity | Sustain 2–3x forecast peak with defined CPU, heap, DB-pool, servlet/executor, queue, and dependency headroom |
| Rollout | Internal/dogfood, small canary, staged percentages, automatic halt/rollback on SLO regression |

Google Play's current bad-behavior thresholds are substantially looser than these proposed internal goals: 1.09% user-perceived crash rate and 0.47% user-perceived ANR rate. Treat Play thresholds as an outer enforcement boundary, not a quality target. Reference: [Android vitals](https://developer.android.com/topic/performance/vitals).

## Required benchmark and load-test scenarios

### Android device matrix

- API 24/25 low-memory cold start, login, Home, lot selection, booking, and all animator-guarded views.
- API 26/29 mid-tier startup, tab churn, booking refresh, history pagination, wallet/payment recovery, and QR generation.
- API 34/36 predictive back, foreground/background, process death, notifications, edge-to-edge, and permission behavior.
- Low-memory kill and restore from every primary tab and payment/booking boundary.
- Airplane mode, slow/lossy network, DNS failure, server 429/500, gateway timeout, and reconnection.
- Thirty-minute normal session and high-animation session with heap, GC, frame, thermal, and battery capture.
- Accessibility scan, font scale, TalkBack, reduced motion, dark mode, and supported locale coverage.

### Backend correctness and capacity matrix

- Same idempotency key repeated sequentially and concurrently.
- Different keys targeting the same user, spot, payment, and reward quota.
- Process termination before/after every database, gateway, outbox, and acknowledgement boundary.
- Duplicate/out-of-order Cashfree and rewarded SSV callbacks.
- Redis/Mongo/Cashfree/FCM slowdown and outage; queue backlog and recovery.
- Rolling deployment with multiple scheduler-enabled replicas.
- Bookings-event fan-out, reconnect storm, missed-event reconciliation, and jitter behavior.
- History/support/transaction queries at forecast 12–24 month cardinality.
- Load at current measured peak, forecast peak, and 2–3x forecast while tracking saturation and tail latency.

## Definition of done for each item

Every completed tracker item must include:

1. A linked pull request/commit and named owner.
2. The exact production risk it closes.
3. Automated correctness/regression tests.
4. Before/after measurement when performance is involved.
5. Dashboard/alert or operational evidence when production behavior is involved.
6. Rollout and rollback instructions.
7. Documentation of migrations, compatibility, and any accepted residual risk.
8. Confirmation that no sensitive data was added to logs/telemetry.

## Suggested weekly review format

| Item | Owner | Status | Evidence added this week | Blocker/decision | Next verified milestone |
|---|---|---|---|---|---|
| Example: PERF-006 | Backend owner | IN PROGRESS | Replay test PR/link | Gateway webhook test fixture | Crash-boundary suite passing |

At each review:

- Re-rank from production data rather than preserving the original order blindly.
- Keep every P0 visible until its acceptance criteria are proven.
- Separate “implemented” from “verified in a release-like environment.”
- Record regressions and rejected approaches so the same experiments are not repeated.
- Do not trade correctness or privacy for lower latency.

## Related repository documents

- [`ADMOB_MONETIZATION_AUDIT_AND_IMPLEMENTATION.md`](ADMOB_MONETIZATION_AUDIT_AND_IMPLEMENTATION.md)
- [`OPERATOR_SCANNER_PRODUCTION_OPTIMIZATION_PLAN.md`](OPERATOR_SCANNER_PRODUCTION_OPTIMIZATION_PLAN.md)
- [`ANDROID_BACKEND_SYNC_PLAN.md`](ANDROID_BACKEND_SYNC_PLAN.md)
- [`DEAD_CODE_INVENTORY.md`](DEAD_CODE_INVENTORY.md)
- [`Gridee_Android/android-app/macrobenchmark/README.md`](Gridee_Android/android-app/macrobenchmark/README.md)
- [`Gridee_Android/docs/UX_PHYSICS_AND_ANIMATIONS.md`](Gridee_Android/docs/UX_PHYSICS_AND_ANIMATIONS.md)
- [`Gridee_Android/android-app/font-audit-report.md`](Gridee_Android/android-app/font-audit-report.md)

## Official Android references

- [Android vitals](https://developer.android.com/topic/performance/vitals)
- [App startup and time to full display](https://developer.android.com/topic/performance/vitals/launch-time)
- [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [Enable app optimization with R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [Benchmarking in CI](https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci)
- [App Startup](https://developer.android.com/topic/libraries/app-startup)
- [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Predictive Back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
