# PERF-023 verification — 2026-09-10

Owner: Android. Scope: Android frontend scroll-listener ownership and lifecycle cleanup. The changes are verified locally; the existing dirty checkout has not been committed, pushed, or published by this task. A commit/PR link remains release bookkeeping.

## Changed behavior

`BaseActivityWithBottomNav` and Main's Home referral chip now use `LifecycleBoundScrollListener`. Setup is idempotent, replacing a view disconnects the previous subscription, and view-lifecycle destruction removes all owned observer/view/callback references. Listeners run only for attached, resumed screens; their scroll offsets are resynchronized after resume or reattachment. Screen-owned listeners remain additive and unchanged. Main provides each selected fragment's view lifecycle owner and rejects setup after host destruction.

The work does not implement PERF-024, change backend behavior, alter navigation visuals/thresholds, or change versionCode/versionName. The inspected app remains versionCode 77 / versionName 1.73. There are no data migrations, new production SDKs, or added production logs/telemetry. The new API use remains within the app's minimum API 24; release vital lint passed.

## Measured evidence

| Check | Result |
| --- | --- |
| Full app unit suite | 397 tests across 74 suites; 0 failures/errors/skips |
| New Robolectric lifecycle suite | 12 tests passed |
| Legacy additive-registration control | 101 callbacks for one notification after initial setup plus 100 repeated setups |
| Fixed repeated-registration case | 1 callback for the same changed offset; 100 fragment switches also retain one delivery |
| Samsung Galaxy A54, Android 16 / API 36 | 3 instrumented tests passed, 0 failures/errors/skips |
| Touch scroll after 100 tab switches | One callback per changed selected-view offset observed by an independent window observer; Home referral callbacks only on Home |
| View retention on device | Root view, child, and view lifecycle owner collected while host, detached fragment and binding holders remain alive |
| Pause/resume and activity recreation | Real touch scroll delivery preserved |
| Debug/release build, release vital lint | Passed |
| Release APK signing | APK Signature Scheme v2 verified; exactly one signer |
| Release APK contents | Production listener and both host fields present; no probe fixture classes |
| Working tree whitespace | `git diff --check` passed |

The device probe installs as `com.gridee.parking.scrollprobe` and compiles the production helper verbatim. It has no Internet permission or production account/application services. Its relevant AndroidX runtime versions match the app: Core 1.16.0, Fragment 1.6.2, Lifecycle 2.7.0, RecyclerView 1.3.1. The test runner removed its disposable packages afterward; the installed Gridee app was not replaced.

The test harness uses a reference queue rather than repeatedly dereferencing weak references during GC. For touch tests it retries only an injected gesture that did not reach the target view during an activity transition; any delivered gesture with missing or duplicate callbacks fails. These details prevent test machinery from retaining watched objects or mistaking intercepted transition input for a listener failure.

This evidence verifies callback multiplicity and subscription-owned retention. It does not measure production frame-time percentiles, audit unrelated leaks, or claim a full production-account UI smoke test. Production observability and the wider device matrix remain under PERF-039/PERF-041.

## Reproduction and reports

Run from `Gridee_Android/android-app`:

```sh
./gradlew --no-daemon :app:testDebugUnitTest \
  -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process --console=plain

# Select the connected device with ANDROID_SERIAL when more than one is attached.
./gradlew --no-daemon :scrollprobe:connectedDebugAndroidTest \
  :app:assembleDebug :app:assembleRelease :app:lintVitalRelease \
  -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process --console=plain
```

The full unit run completed before the final device/build command; the release build's existing unit-test dependency reused the unchanged passing suite. The final device/build invocation ended `BUILD SUCCESSFUL` with all three device tests passing. ADB identified the physical reference device as SM-A546E, SDK 36.

- [Unit test report](Gridee_Android/android-app/app/build/reports/tests/testDebugUnitTest/index.html)
- [Device report](Gridee_Android/android-app/scrollprobe/build/reports/androidTests/connected/debug/index.html)
- [Device raw JUnit results](<Gridee_Android/android-app/scrollprobe/build/outputs/androidTest-results/connected/debug/TEST-SM-A546E - 16-_scrollprobe-.xml>)
- [Probe instructions and test coverage](Gridee_Android/android-app/scrollprobe/README.md)
- [Signed release APK](Gridee_Android/android-app/app/build/outputs/apk/release/app-release.apk)

SHA-256 of both `app/src/main/java/com/gridee/parking/ui/base/LifecycleBoundScrollListener.kt` and the generated source compiled into the probe:

```text
c2825823612c976f66bc64207c7c568f2e62b220423ea2c3b9e3ef9b14f07ba7
```

SHA-256 of the verified release APK:

```text
5c168a1e3b88020c056535434412a37df9555f80489e06c715f5b4a81549953d
```

## Rollout and rollback

Commit/review the scoped changes with the existing worktree owner before publishing; do not reset or discard unrelated pending changes. Use the normal internal-testing release process and check the complete Home/Bookings/Wallet/Profile navigation and referral-chip presentation in that release candidate. Publishing or promoting a Play release was outside this task.

For rollback, revert only the reviewed PERF-023 integration changes in `BaseActivityWithBottomNav` and `MainContainerActivity` and remove the helper if no callers remain; preserve the separate PERF-022 restoration changes. Rebuild and rerun the normal release gates. The independent probe module has no runtime dependency from `:app` and can be retained for regression evidence. No server or persisted-data rollback is needed.

The current code can be reviewed in [the lifecycle helper](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/base/LifecycleBoundScrollListener.kt), [base activity integration](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/base/BaseActivityWithBottomNav.kt), and [Main integration](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt). Lifecycle ownership follows the [Android fragment-view lifecycle](https://developer.android.com/guide/fragments/lifecycle); observer replacement and liveness use the [ViewTreeObserver API](https://developer.android.com/reference/android/view/ViewTreeObserver).
