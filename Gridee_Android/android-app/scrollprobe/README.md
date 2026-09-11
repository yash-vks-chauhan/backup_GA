# PERF-023 device regression probe

This independent test app compiles `LifecycleBoundScrollListener.kt` directly from the production source through the `syncScrollBindingSource` build task. It installs as `com.gridee.parking.scrollprobe`, alongside Gridee. It has no Internet permission, accounts, Firebase, advertising, payments, or production application initializer. Its activity is not exported.

The instrumented tests exercise real Android fragment view lifecycles and touch scrolling:

- Scroll delivery before and after 100 tab switches, with duplicate setup calls. An independent window observer counts changes to the selected scroll offset; the production listener must deliver exactly once for each change. The second subscription models the Home referral binding and must deliver only on Home.
- Detaching a fragment must allow its old root view, child view, and view lifecycle owner to be garbage-collected while the activity, fragment, and both subscription holders remain alive.
- Background/foreground and activity recreation must preserve scroll delivery and the selected tab.

Run from `Gridee_Android/android-app` with an unlocked Android device connected:

```sh
./gradlew --no-daemon :scrollprobe:connectedDebugAndroidTest --console=plain
```

For a specific device, prefix the command with `ANDROID_SERIAL=<serial>`. Reports are generated under `scrollprobe/build/reports/androidTests/connected/debug/` and raw results under `scrollprobe/build/outputs/androidTest-results/connected/debug/`.

The app's Robolectric suite separately covers 100 fragment switches, the old additive registration pattern versus the fixed callback count, window-level notifications from other views, preservation of a screen-owned scroll listener, pause/resume, detach/reattach, destroyed/replaced owners, activity recreation, and all three supported scroll types:

```sh
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests 'com.gridee.parking.ui.base.LifecycleBoundScrollListenerRobolectricTest' \
  --console=plain
```

The probe verifies the production subscription implementation on a physical device. It does not claim a whole-app UI smoke test or a production frame-time benchmark. Main's production integration remains in `MainContainerActivity` and `BaseActivityWithBottomNav`; app compilation, the complete unit suite, and release lint check that integration alongside the existing restoration tests.

After testing, remove only the two disposable probe packages if desired:

```sh
adb uninstall com.gridee.parking.scrollprobe.test
adb uninstall com.gridee.parking.scrollprobe
```
