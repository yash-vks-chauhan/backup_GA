#!/bin/bash
# Launches the Gridee test emulator as a 6.1"/360dp phone (Galaxy S23-class).
# The app + your logged-in session are already inside this AVD.
SDK="$HOME/Library/Android/sdk"
EMU=emulator-5560
"$SDK/emulator/emulator" @gridee_test -port 5560 -gpu host -no-snapshot &
echo "Booting... (window will appear)"
"$SDK/platform-tools/adb" -s $EMU wait-for-device
until [ "$("$SDK/platform-tools/adb" -s $EMU shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
# 6.1"/360dp compact profile + dark theme
"$SDK/platform-tools/adb" -s $EMU shell wm size reset
"$SDK/platform-tools/adb" -s $EMU shell wm density 480
"$SDK/platform-tools/adb" -s $EMU shell cmd uimode night yes
# open the app (already logged in)
"$SDK/platform-tools/adb" -s $EMU shell am start -n com.gridee.parking/.ui.auth.SplashActivity >/dev/null 2>&1
echo "Ready — 6.1\" / 360dp. Close the emulator window when done."
echo "Switch sizes anytime:  adb -s emulator-5560 shell wm density 420   (=411dp, your A54)"
echo "                       adb -s emulator-5560 shell wm density 540   (=320dp, tiny phone)"
wait
