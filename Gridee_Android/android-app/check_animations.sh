#!/bin/bash

echo "🔍 Checking Android Animation Settings..."
echo ""

# Check animator duration scale
ANIMATOR_SCALE=$(adb shell settings get global animator_duration_scale 2>/dev/null)
TRANSITION_SCALE=$(adb shell settings get global transition_animation_scale 2>/dev/null)
WINDOW_SCALE=$(adb shell settings get global window_animation_scale 2>/dev/null)

echo "Current Animation Scales:"
echo "  Animator Duration Scale: $ANIMATOR_SCALE"
echo "  Transition Animation Scale: $TRANSITION_SCALE"
echo "  Window Animation Scale: $WINDOW_SCALE"
echo ""

# Check if any scale is 0 (disabled)
if [ "$ANIMATOR_SCALE" = "0" ] || [ "$TRANSITION_SCALE" = "0" ] || [ "$WINDOW_SCALE" = "0" ]; then
    echo "⚠️  WARNING: Some animations are DISABLED!"
    echo ""
    echo "Would you like to enable all animations? (y/n)"
    read -r response
    
    if [ "$response" = "y" ]; then
        echo "Enabling animations..."
        adb shell settings put global animator_duration_scale 1.0
        adb shell settings put global transition_animation_scale 1.0
        adb shell settings put global window_animation_scale 1.0
        echo "✅ Animations enabled!"
    fi
else
    echo "✅ All animations are enabled!"
fi

echo ""
echo "📱 Device info:"
adb shell getprop ro.build.version.release
echo ""
