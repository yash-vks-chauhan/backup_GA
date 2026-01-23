# Complete Animation & Transition Guide
## Professional, Clean, and Smooth UI Animations (Apple & Material Design 3)

This comprehensive guide documents all the animation and transition elements used by professional apps (especially Apple and Material Design 3) to create alive, natural, professional, clean, smooth, and neat user experiences.

---

## Table of Contents
1. [Core Animation Principles](#core-animation-principles)
2. [Duration Tokens & Timing](#duration-tokens--timing)
3. [Easing Curves & Timing Functions](#easing-curves--timing-functions)
4. [Spring Physics Parameters](#spring-physics-parameters)
5. [Friction & Damping](#friction--damping)
6. [Transition Patterns](#transition-patterns)
7. [Component-Specific Animations](#component-specific-animations)
8. [Android Implementation Details](#android-implementation-details)
9. [Quick Reference Tables](#quick-reference-tables)

---

## Core Animation Principles

### Apple's Philosophy
- **Purposeful & Subtle**: Animations must convey status or provide feedback, never being "gratuitous"
- **Physics-Based Realism**: Use spring physics rather than linear timing curves to make elements feel "alive"
- **Responsiveness**: Animations should be interruptible - users should be able to catch or reverse a transition mid-motion
- **Natural Consequence**: Animations should feel like a natural consequence of user action
- **Respect Reduce Motion**: Always check accessibility settings to ensure animations can be simplified

### Material Design 3 Philosophy
- **Motion Provides Meaning**: Animations should help users understand and navigate the app
- **Reinforce User Actions**: Most motions should be initiated by user actions, providing subtle feedback
- **Maintain Continuity**: Animations should not break the user's experience or force them to wait
- **Spatial Relationships**: Motion describes spatial relationships, functionality, and intent with fluidity

---

## Duration Tokens & Timing

### Apple Guidelines
- **Standard UI Transitions**: 0.25s to 0.35s (250ms - 350ms)
- Anything longer starts to feel sluggish
- Anything shorter feels jarring
- **Interactive Elements**: Typically 200ms for instant response

### Material Design 3 Duration System

#### Short (Subtle/Small) - For small component state changes
- **Short 1**: 50ms
- **Short 2**: 100ms
- **Short 3**: 150ms
- **Short 4**: 200ms
- **Usage**: Switches, checkboxes, small toggles

#### Medium (Functional/Standard) - For most interactive components
- **Medium 1**: 250ms
- **Medium 2**: 300ms
- **Medium 3**: 350ms
- **Medium 4**: 400ms
- **Usage**: Buttons, chips, menus, cards

#### Long (Structural/Large) - For large transitions
- **Long 1**: 450ms
- **Long 2**: 500ms
- **Long 3**: 550ms
- **Long 4**: 600ms
- **Usage**: Dialogs, bottom sheets, full-screen movements

#### Extra Long (Hero/Expressive) - For complex animations
- **Range**: 700ms to 1000ms
- **Usage**: Onboarding flows, hero animations, complex transitions

### General Best Practices
- **Micro-animations**: 70ms - 150ms
- **Small functional animations**: 150ms - 300ms
- **Larger transitions**: 300ms - 500ms
- **Maximum recommended**: 1000ms (anything longer feels like waiting)

---

## Easing Curves & Timing Functions

### Material Design 3 Cubic Bézier Curves

#### Emphasized (Standard for M3)
```
cubic-bezier(0.2, 0, 0, 1.0)
```
- **Effect**: Starts quickly and spends a long time decelerating
- **Feel**: Smooth and "alive"
- **Usage**: Most expressive transitions

#### Emphasized Decelerate
```
cubic-bezier(0.05, 0.7, 0.1, 1.0)
```
- **Usage**: Elements entering the screen
- **Effect**: Quick start with smooth landing

#### Emphasized Accelerate
```
cubic-bezier(0.3, 0, 0.8, 0.15)
```
- **Usage**: Elements exiting the screen
- **Effect**: Smooth start with quick exit

#### Standard (Utilitarian)
```
cubic-bezier(0.2, 0, 0, 1.0)
```
- **Usage**: Simpler elements without complex "hero" paths

### Apple's Easing Approach
- **Traditional Ease-In-Out**: For non-interactive elements
- **Custom Cubic Bézier**: Standard for custom flows
- **Spring-Based**: Preferred over linear timing curves for interactive elements

### Common Easing Patterns
- **Ease-In**: Slow start, fast end - `cubic-bezier(0.42, 0, 1.0, 1.0)`
- **Ease-Out**: Fast start, slow end - `cubic-bezier(0, 0, 0.58, 1.0)`
- **Ease-In-Out**: Slow start, fast middle, slow end - `cubic-bezier(0.42, 0, 0.58, 1.0)`
- **Linear**: Constant speed - `cubic-bezier(0, 0, 1.0, 1.0)` (avoid for interactive elements)

---

## Spring Physics Parameters

### Core Spring Properties

#### Stiffness
- **Definition**: Determines the strength of the spring
- **High Stiffness**: Faster return to target state, snappier feel
- **Low Stiffness**: Slower, more fluid and "lazy" feel
- **Default**: Usually 100.0
- **Range**: Typically 50.0 (soft) to 300.0 (very stiff)

#### Damping / Damping Ratio
- **Definition**: Controls how quickly oscillations are reduced
- **Damping Ratio = 1.0**: Critically damped (no overshoot/bounce)
- **Damping Ratio < 1.0**: Underdamped (adds bounce)
- **Damping Ratio > 1.0**: Overdamped (slower, no bounce)
- **Default**: Usually 10.0 or damping ratio of 0.7-0.8
- **Apple's Default**: ~10.0

#### Mass
- **Definition**: Represents the mass of the object attached to the spring
- **Default**: 1.0 for standard elements
- **Higher Mass**: Slower, heavier feel
- **Lower Mass**: Lighter, more responsive feel

#### Initial Velocity
- **Definition**: Initial push applied to the animated object
- **Value of 1.0**: Would cover the total animation distance in one second
- **Default**: 0.0
- **Usage**: For gesture-driven animations (e.g., swipe velocity)

### Material Design 3 Spring Schemes

#### Expressive Scheme
- **Character**: Opinionated, suitable for "hero moments" and key interactions
- **Properties**: Higher velocity, moderate damping
- **Feel**: "Magnetic" and engaging

#### Standard Scheme
- **Character**: Functional with minimal bounce
- **Properties**: Lower velocity, higher damping
- **Feel**: Clean and utilitarian

### Android Spring Animation Constants

#### Damping Ratio Constants
- `DAMPING_RATIO_HIGH_BOUNCY`: 0.2 (very bouncy)
- `DAMPING_RATIO_MEDIUM_BOUNCY`: 0.5 (medium bounce)
- `DAMPING_RATIO_LOW_BOUNCY`: 0.75 (subtle bounce)
- `DAMPING_RATIO_NO_BOUNCY`: 1.0 (no bounce, critically damped)

#### Stiffness Constants
- `STIFFNESS_HIGH`: 10,000 (very stiff, fast)
- `STIFFNESS_MEDIUM`: 1,500 (balanced)
- `STIFFNESS_LOW`: 200 (soft, slow)
- `STIFFNESS_VERY_LOW`: 50 (very soft)

---

## Friction & Damping

### Friction in Scrolling
- **Momentum Scrolling**: When user lifts finger, content continues with momentum
- **Deceleration**: Gradually slows down due to simulated friction
- **Rubber Banding**: Resistance at scroll edges (iOS signature effect)
- **Feel**: Provides tactile feedback and natural physics

### Interactive Friction
- **Static Friction**: Initial resistance to movement (makes elements feel more realistic)
- **Kinetic Friction**: Ongoing resistance during movement
- **Usage**: Drag-and-drop, swipe gestures, scrolling

### Damping in Animations
- **Purpose**: Controls how quickly oscillations stop
- **High Damping**: Quick settling, no bounce (professional, clean)
- **Low Damping**: More oscillations, bouncy (playful, engaging)
- **Critical Damping**: Perfect balance - fastest settling without overshoot

### Bottom Sheet Friction
- **Android BottomSheetBehavior**: Has internal friction coefficient
- **Effect**: Controls how easily the sheet can be dragged
- **Higher Friction**: Requires more force to move
- **Lower Friction**: Slides easily

---

## Transition Patterns

### Material Design 3 Four Core Patterns

#### 1. Container Transform
- **Description**: One element grows/transforms into another
- **Example**: Card expanding into full article, FAB into compose screen
- **Usage**: Creating visible connection between UI elements
- **Duration**: Long 1-2 (450-500ms)
- **Easing**: Emphasized
- **Best For**: Maintaining continuity and hierarchy

#### 2. Shared Axis
- **Description**: Movement along X, Y, or Z axis
- **Variants**:
  - **X-Axis**: Horizontal movement (e.g., carousel, tabs)
  - **Y-Axis**: Vertical movement (e.g., stepper, vertical list)
  - **Z-Axis**: Depth movement (e.g., parent-child navigation)
- **Duration**: Medium 3-4 (350-400ms)
- **Easing**: Emphasized Decelerate (entering), Emphasized Accelerate (exiting)
- **Best For**: Spatial or navigational relationships

#### 3. Fade Through
- **Description**: Sequential fade out and fade in
- **Effect**: Outgoing element fades out, incoming element fades in with slight scale
- **Duration**: Medium 1-2 (250-300ms)
- **Easing**: Standard
- **Best For**: Switching between unrelated screens (e.g., bottom nav tabs)

#### 4. Fade
- **Description**: Simple enter/exit within screen bounds
- **Entering**: Quick fade-in with subtle scale up
- **Exiting**: Simple fade out
- **Duration**: Short 3-4 (150-200ms)
- **Easing**: Standard
- **Best For**: Dialogs, menus, snackbars, tooltips

### Apple Transition Patterns

#### Push/Pop (Navigation)
- **Duration**: 0.3s (300ms)
- **Easing**: Spring-based with slight bounce
- **Effect**: Slides in from right, previous screen slides left

#### Modal Presentation
- **Duration**: 0.35s (350ms)
- **Easing**: Spring-based
- **Effect**: Slides up from bottom with subtle scale on presenting view

#### Sheet Presentation
- **Duration**: 0.3-0.4s (300-400ms)
- **Easing**: High-damping spring
- **Effect**: Slides up with detent snapping
- **Interactive**: 1:1 finger tracking

---

## Component-Specific Animations

### Sidebar / Navigation Drawer

#### Apple Style
- **Duration**: 0.3s (300ms)
- **Easing**: Spring with damping ratio 0.8
- **Effect**: 
  - Slides in from edge
  - Main content scales down slightly (0.95x) or slides
  - Scrim (dark overlay) fades in
  - Hamburger icon morphs to arrow/close
- **Interactive**: 1:1 finger tracking during drag

#### Material Design 3 Style
- **Duration**: Medium 4 (400ms)
- **Easing**: Standard Decelerate
- **Effect**:
  - Slides in from left (X-axis shared axis)
  - Scrim fades in
  - Main content can slide or stay in place
- **Interactive**: Swipe from edge to open

#### Android Implementation
- **Component**: `DrawerLayout`
- **Default Animation**: Uses `ViewDragHelper` internally
- **Customization**: Via `DrawerLayout.DrawerListener` callbacks
- **Easing**: Can use `PathInterpolator` for custom curves

### Bottom Sheet

#### Apple Style (Sheets)
- **Duration**: 0.3-0.4s (300-400ms)
- **Easing**: High-damping spring (damping ratio ~0.9)
- **Detents**: Pre-defined heights (e.g., 50%, 100%)
- **Effect**:
  - Slides up from bottom
  - Snaps to detents with "magnetic" feel
  - 1:1 finger tracking
  - Velocity-based dismissal
- **Interactive**: Drag handle for height adjustment

#### Material Design 3 Style
- **Duration**: Long 1 (450ms)
- **Easing**: Emphasized
- **Effect**:
  - Slides up from bottom
  - Scrim darkens background (modal)
  - Can be persistent or modal
- **Dismissal**: Swipe down, tap scrim, or close button

#### Android Implementation
- **Component**: `BottomSheetBehavior`
- **States**: Collapsed, Expanded, Hidden, Half-Expanded
- **Physics**: Internal spring-based animation
- **Friction**: Can be customized via `setHideFriction()`
- **Peek Height**: Initial visible height

### Buttons & Interactive Elements

#### Press Animation
- **Duration**: Short 4 (200ms)
- **Effect**: Scale down to 0.95x on press
- **Release**: Spring back to 1.0x
- **Damping**: Low (0.5-0.7) for subtle bounce
- **Haptics**: Light impact feedback

#### Ripple Effect (Material Design)
- **Duration**: 300-400ms
- **Easing**: Linear for expansion, fade out at end
- **Effect**: Circular wave emanating from touch point
- **Color**: Semi-transparent overlay

#### Hover States (Desktop/Tablet)
- **Duration**: Short 2-3 (100-150ms)
- **Effect**: Subtle elevation increase, color change
- **Easing**: Ease-out

### Dialogs & Modals

#### Entry Animation
- **Duration**: Medium 2-3 (300-350ms)
- **Effect**: 
  - Fade in with scale from 0.9x to 1.0x
  - Scrim fades in
- **Easing**: Emphasized Decelerate

#### Exit Animation
- **Duration**: Medium 1-2 (250-300ms)
- **Effect**:
  - Fade out with scale to 0.95x
  - Scrim fades out
- **Easing**: Emphasized Accelerate

### Cards & List Items

#### Expand/Collapse
- **Duration**: Medium 3 (350ms)
- **Effect**: Height animation with content fade
- **Easing**: Emphasized

#### Selection/Highlight
- **Duration**: Short 3 (150ms)
- **Effect**: Background color change, elevation increase
- **Easing**: Ease-out

### Icons & Micro-interactions

#### Icon Morph (e.g., Hamburger to Arrow)
- **Duration**: Short 4 (200ms)
- **Effect**: Path interpolation between shapes
- **Easing**: Emphasized

#### Loading Spinners
- **Duration**: Continuous (typically 1-2s per rotation)
- **Easing**: Linear or slight ease-in-out

#### Checkmarks/Success Indicators
- **Duration**: Medium 1-2 (250-300ms)
- **Effect**: Draw path animation with scale
- **Easing**: Spring with slight bounce

### Dropdown Menus & Popovers (Apple Style)

Apple's dropdown menus and context menus are designed to feel **fast, organic, and responsive**. They use a distinctive "growth" pattern rather than a simple fade or slide, creating a sense of the menu emerging from its origin point.

#### Key Animation Characteristics

**Duration**
- **Standard Dropdown**: 200-250ms (intentionally snappier than full-screen transitions)
- **Context Menu**: 250ms for appearance, 200ms for dismissal
- **Rationale**: Menus should feel instant and responsive, not sluggish

**Scale & Fade Effect**
- **Initial Scale**: Starts at **0.8x to 0.9x** of final size
- **Final Scale**: **1.0x** (full size)
- **Opacity**: Transitions from **0 to 1** simultaneously with scaling
- **Anchor Point**: Always set to the origin of touch or center of triggering button
- **Effect**: Menu "grows" from the point of interaction

**Spring Physics (The "Snappy" Feel)**
- **SwiftUI Preset**: `.snappy` 
  - Response: ~0.3s
  - Damping Fraction: 0.8
- **UIKit Parameters**: 
  - `usingSpringWithDamping: 0.8`
  - `initialSpringVelocity: 0`
- **Result**: Quick, sharp expansion with nearly imperceptible overshoot
- **Feel**: "Magnetic" snap into place

#### Context Menu Specifics (iOS 13+)

The context menu "lift" effect involves multiple coordinated animations:

**1. Long-Press Feedback**
- **Source View Shrink**: Element scales to **0.95x - 0.98x** to indicate pressure
- **Duration**: 100-150ms
- **Easing**: Quick decelerate

**2. Backdrop Effect**
- **Blur**: Heavy Gaussian blur (frosted glass/translucency)
- **Dim**: Background darkens with semi-transparent overlay
- **Duration**: 200ms, synchronized with menu appearance
- **Effect**: Focuses attention on the menu

**3. Menu Appearance**
- **Growth Animation**: Menu scales from **0.85x to 1.0x**
- **Source View Lift**: Original element scales to **1.05x** and elevates
- **Duration**: 250ms
- **Spring**: High damping (0.8) for clean snap
- **Shadow**: Soft shadow appears under lifted element

**4. Dismissal**
- **Menu Fade**: Scales to **0.95x** while fading out
- **Duration**: 200ms (faster than appearance)
- **Source View**: Returns to normal size (1.0x)
- **Backdrop**: Blur and dim fade out

#### Popover Menus (macOS/iPadOS)

**Appearance Animation**
- **Duration**: 250ms
- **Scale**: From **0.9x to 1.0x**
- **Opacity**: From **0 to 1**
- **Arrow/Pointer**: Animates in sync with menu body
- **Easing**: Spring with damping 0.8

**Position Adjustment**
- **Smart Positioning**: Automatically adjusts to stay on screen
- **Transition**: If repositioning needed, uses 150ms slide
- **Priority**: Prefers appearing below trigger, then above, then sides

#### Visual Polish Elements

**Translucency & Materials**
- **Background**: System-provided "Materials" (semi-transparent blur)
- **Effect**: Menu integrates with UI behind it
- **Vibrancy**: Text and icons use vibrancy effect for legibility
- **Adaptation**: Automatically adjusts for light/dark mode

**Shadows & Depth**
- **Shadow**: Soft, diffused shadow (blur radius: 20-30px)
- **Elevation**: Appears to float above content
- **Color**: Black with 15-25% opacity
- **Offset**: 0-2px vertical offset

**Haptic Feedback**
- **Trigger Point**: Light impact when menu appears
- **UIKit**: `UIImpactFeedbackGenerator(style: .light)`
- **Timing**: Synchronized with start of growth animation
- **Effect**: Reinforces the "snap" feeling

#### Interaction Behaviors

**Interruptibility**
- **100% Interruptible**: Tap outside during opening reverses animation
- **Smooth Reversal**: Uses current velocity to reverse naturally
- **No Jarring**: Never abruptly stops mid-animation

**Item Selection**
- **Highlight**: 100ms fade to highlight color
- **Press Feedback**: Subtle scale to 0.98x on touch
- **Selection**: Menu dismisses with 200ms fade + scale
- **Ripple**: No ripple effect (unlike Material Design)

**Scrolling (Long Menus)**
- **Momentum**: Standard iOS scroll physics
- **Rubber Banding**: Bounces at top/bottom
- **Fade Edges**: Content fades at scroll boundaries

#### Android Implementation Approach

To recreate Apple-style dropdown menus in Android:

**Basic Popup Menu Animation**
```kotlin
// Custom animation for PopupMenu
val popupMenu = PopupMenu(context, anchorView)

// Override default animation
popupMenu.setOnDismissListener {
    // Custom dismiss animation
}

// Show with custom animation
val contentView = popupMenu.menu
animateMenuAppearance(contentView)
```

**Scale + Fade Animation**
```kotlin
fun animateMenuAppearance(view: View) {
    view.alpha = 0f
    view.scaleX = 0.85f
    view.scaleY = 0.85f
    view.pivotX = anchorX // Set to touch point
    view.pivotY = 0f // Top of menu
    
    // Spring animation for Apple-like feel
    val scaleX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1f)
    val scaleY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f)
    
    scaleX.spring.apply {
        dampingRatio = 0.8f
        stiffness = 400f
    }
    scaleY.spring.apply {
        dampingRatio = 0.8f
        stiffness = 400f
    }
    
    // Fade in
    view.animate()
        .alpha(1f)
        .setDuration(250)
        .setInterpolator(DecelerateInterpolator())
        .start()
    
    scaleX.start()
    scaleY.start()
    
    // Haptic feedback
    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}
```

**Context Menu Style (Long Press)**
```kotlin
// Implement custom long-press behavior
view.setOnLongClickListener {
    // 1. Shrink source view
    it.animate()
        .scaleX(0.96f)
        .scaleY(0.96f)
        .setDuration(150)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            // 2. Show menu with blur backdrop
            showContextMenuWithBlur(it)
            
            // 3. Lift source view
            it.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .translationZ(8.dp)
                .setDuration(250)
                .start()
        }
        .start()
    
    true
}

fun showContextMenuWithBlur(anchorView: View) {
    // Add blur overlay
    val blurView = createBlurOverlay()
    blurView.alpha = 0f
    rootView.addView(blurView)
    
    blurView.animate()
        .alpha(1f)
        .setDuration(200)
        .start()
    
    // Show menu with growth animation
    val menu = createContextMenu()
    animateMenuAppearance(menu)
}
```

**Material Design Alternative**
For a Material Design approach with Apple-inspired smoothness:
```kotlin
// Use Material's built-in menu with custom animation
val menu = MaterialMenu(context)
menu.animationStyle = R.style.MenuAnimationAppleStyle

// In styles.xml
<style name="MenuAnimationAppleStyle">
    <item name="android:windowEnterAnimation">@anim/menu_enter</item>
    <item name="android:windowExitAnimation">@anim/menu_exit</item>
</style>

// menu_enter.xml
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <scale
        android:duration="250"
        android:fromXScale="0.85"
        android:fromYScale="0.85"
        android:toXScale="1.0"
        android:toYScale="1.0"
        android:pivotX="50%"
        android:pivotY="0%"
        android:interpolator="@android:interpolator/decelerate_cubic" />
    <alpha
        android:duration="250"
        android:fromAlpha="0.0"
        android:toAlpha="1.0" />
</set>
```

#### Quick Reference: Dropdown Animations

| Element | Duration | Scale | Damping | Notes |
|---------|----------|-------|---------|-------|
| **Standard Dropdown** | 200-250ms | 0.85→1.0 | 0.8 | Fast, snappy |
| **Context Menu** | 250ms | 0.85→1.0 | 0.8 | With backdrop blur |
| **Popover** | 250ms | 0.9→1.0 | 0.8 | Includes arrow |
| **Menu Item Highlight** | 100ms | - | - | Fade only |
| **Menu Dismissal** | 200ms | 1.0→0.95 | - | Faster than entry |
| **Source View Press** | 150ms | 1.0→0.96 | - | Feedback |
| **Source View Lift** | 250ms | 1.0→1.05 | 0.8 | With context menu |

---

## Android Implementation Details

### Interpolators

#### Built-in Interpolators
```kotlin
// Linear - constant speed (avoid for most UI)
LinearInterpolator()

// Accelerate - starts slow, ends fast
AccelerateInterpolator(factor = 1.0f) // factor > 1.0 = more exaggerated

// Decelerate - starts fast, ends slow
DecelerateInterpolator(factor = 1.0f) // factor > 1.0 = more exaggerated

// AccelerateDecelerate - slow-fast-slow
AccelerateDecelerateInterpolator()

// Anticipate - slight backward movement before forward
AnticipateInterpolator()

// Overshoot - overshoots target then settles
OvershootInterpolator()

// Bounce - bounces at the end
BounceInterpolator()
```

#### PathInterpolator (Custom Cubic Bézier)
```kotlin
// Material Design 3 Emphasized
PathInterpolator(0.2f, 0f, 0f, 1.0f)

// Material Design 3 Emphasized Decelerate
PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)

// Material Design 3 Emphasized Accelerate
PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

// Custom - define in XML
<pathInterpolator
    android:controlX1="0.2"
    android:controlY1="0"
    android:controlX2="0"
    android:controlY2="1.0" />
```

### Spring Animations

#### Basic Spring Animation
```kotlin
val springAnim = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, targetValue)
springAnim.spring.apply {
    dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY // 0.5
    stiffness = SpringForce.STIFFNESS_LOW // 200
}
springAnim.start()
```

#### Common Spring Configurations

**Professional & Clean (No Bounce)**
```kotlin
dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY // 1.0
stiffness = SpringForce.STIFFNESS_MEDIUM // 1500
```

**Apple-Style (Subtle Bounce)**
```kotlin
dampingRatio = 0.7f
stiffness = 300f
```

**Playful & Engaging**
```kotlin
dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY // 0.5
stiffness = SpringForce.STIFFNESS_LOW // 200
```

### View Property Animator

#### Basic Usage
```kotlin
view.animate()
    .translationY(targetY)
    .alpha(1.0f)
    .setDuration(300)
    .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1.0f))
    .start()
```

#### With Listener
```kotlin
view.animate()
    .scaleX(0.95f)
    .scaleY(0.95f)
    .setDuration(200)
    .setInterpolator(DecelerateInterpolator())
    .setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            // Animation complete
        }
    })
    .start()
```

### ObjectAnimator

#### Property Animation
```kotlin
val animator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
animator.duration = 300
animator.interpolator = PathInterpolator(0.2f, 0f, 0f, 1.0f)
animator.start()
```

### Transition Framework

#### Scene Transitions
```kotlin
val scene = Scene.getSceneForLayout(sceneRoot, R.layout.scene_layout, context)
val transition = AutoTransition().apply {
    duration = 300
    interpolator = PathInterpolator(0.2f, 0f, 0f, 1.0f)
}
TransitionManager.go(scene, transition)
```

### Material Motion Library

#### Container Transform
```kotlin
val transform = MaterialContainerTransform().apply {
    startView = startCard
    endView = endView
    duration = 450
    scrimColor = Color.TRANSPARENT
    containerColor = Color.WHITE
}
TransitionManager.beginDelayedTransition(rootView, transform)
```

---

## Quick Reference Tables

### Duration Quick Reference

| Use Case | Duration | Token |
|----------|----------|-------|
| Icon state change | 100-150ms | Short 2-3 |
| Button press feedback | 200ms | Short 4 |
| Checkbox/Switch toggle | 150ms | Short 3 |
| **Dropdown menu (Apple)** | **200-250ms** | **Short 4 / Medium 1** |
| **Context menu appear** | **250ms** | **Medium 1** |
| Card selection | 200-250ms | Short 4 / Medium 1 |
| Menu open/close | 300ms | Medium 2 |
| Bottom nav transition | 300-350ms | Medium 2-3 |
| Sidebar open/close | 300-400ms | Medium 2-4 |
| Bottom sheet expand | 400-450ms | Medium 4 / Long 1 |
| Dialog appear/dismiss | 300-350ms | Medium 2-3 |
| Full screen transition | 450-500ms | Long 1-2 |
| Hero animation | 500-700ms | Long 2-4 |
| Onboarding flow | 700-1000ms | Extra Long |

### Easing Quick Reference

| Use Case | Easing Curve | Cubic Bézier |
|----------|--------------|--------------|
| Element entering | Emphasized Decelerate | (0.05, 0.7, 0.1, 1.0) |
| Element exiting | Emphasized Accelerate | (0.3, 0, 0.8, 0.15) |
| Standard transition | Emphasized | (0.2, 0, 0, 1.0) |
| Simple fade | Standard | (0.2, 0, 0, 1.0) |
| Button press | Decelerate | AccelerateDecelerateInterpolator |
| Scroll momentum | Decelerate | DecelerateInterpolator(1.5f) |
| **Dropdown/Context menu** | **Spring (0.8 damping)** | **Snappy feel** |

### Spring Configuration Quick Reference

| Feel | Damping Ratio | Stiffness | Use Case |
|------|---------------|-----------|----------|
| Professional & Clean | 1.0 | 1500 | Dialogs, menus |
| Apple-Style Subtle | 0.7-0.8 | 300-500 | Buttons, cards |
| **Apple Dropdown Snappy** | **0.8** | **400** | **Dropdown menus, popovers** |
| Engaging & Playful | 0.5 | 200 | Interactive elements |
| Very Bouncy | 0.2 | 100 | Playful animations |
| Soft & Smooth | 0.9 | 100 | Bottom sheets |
| Snappy & Fast | 0.8 | 2000 | Quick interactions |

### Component Animation Summary

| Component | Duration | Easing | Special Notes |
|-----------|----------|--------|---------------|
| **Sidebar** | 300-400ms | Spring (0.8 damping) | 1:1 tracking, scrim fade |
| **Bottom Sheet** | 400-450ms | High-damping spring | Detent snapping, velocity-based |
| **Dialog** | 300-350ms | Emphasized Decelerate | Scale 0.9→1.0, scrim fade |
| **Dropdown Menu (Apple)** | **200-250ms** | **Spring (0.8 damping)** | **Scale 0.85→1.0, growth from origin** |
| **Context Menu** | **250ms** | **Spring (0.8 damping)** | **Lift effect, backdrop blur, haptics** |
| **Popover** | **250ms** | **Spring (0.8 damping)** | **Scale 0.9→1.0, arrow animation** |
| **Button Press** | 200ms | Spring (0.7 damping) | Scale to 0.95x, haptic feedback |
| **Card Expand** | 350ms | Emphasized | Height + content fade |
| **Tab Switch** | 300ms | Fade Through | Sequential fade |
| **Navigation** | 300-350ms | Shared Axis | Direction-based |
| **Menu** | 250-300ms | Emphasized Decelerate | Fade + scale |
| **Snackbar** | 150ms (in) / 75ms (out) | Emphasized | Slide up from bottom |
| **Tooltip** | 150ms | Fade | Simple fade in/out |

---

## Best Practices Summary

### DO's ✅
1. **Use spring physics** for interactive elements (buttons, sheets, drawers)
2. **Keep durations short** (200-400ms for most interactions)
3. **Use emphasized easing** for Material Design 3
4. **Implement 1:1 tracking** for draggable elements
5. **Add haptic feedback** for tactile interactions
6. **Respect "Reduce Motion"** accessibility setting
7. **Make animations interruptible** (user can reverse mid-motion)
8. **Use scrim/overlay** for modal elements
9. **Maintain visual continuity** between states
10. **Test on real devices** (animations feel different than emulators)

### DON'Ts ❌
1. **Don't use linear interpolation** for interactive UI
2. **Don't make animations too long** (>500ms for standard interactions)
3. **Don't animate too many properties** at once (causes jank)
4. **Don't use excessive bounce** (unprofessional)
5. **Don't ignore performance** (60fps minimum, 120fps ideal)
6. **Don't animate during heavy operations** (defer until idle)
7. **Don't use different animation styles** inconsistently
8. **Don't forget exit animations** (as important as entry)
9. **Don't animate layout changes** without TransitionManager
10. **Don't skip testing** on low-end devices

---

## Tools & Resources

### Design Tools
- **cubic-bezier.com**: Visualize and create custom easing curves
- **easings.net**: Reference for common easing functions
- **Material Theme Builder**: Generate M3 motion tokens
- **Lottie**: Complex animations from After Effects

### Android Libraries
- **Material Components**: `com.google.android.material:material`
- **AndroidX Transition**: `androidx.transition:transition`
- **Dynamic Animation**: `androidx.dynamicanimation:dynamicanimation`

### Testing Tools
- **Layout Inspector**: View animation properties in real-time
- **Systrace**: Profile animation performance
- **GPU Rendering Profile**: Identify jank and dropped frames

---

## Conclusion

Creating professional, clean, and smooth animations requires:
1. **Understanding physics**: Springs, damping, friction
2. **Appropriate timing**: Not too fast, not too slow
3. **Natural easing**: Mimicking real-world motion
4. **Consistency**: Using the same patterns throughout
5. **Performance**: Maintaining 60fps minimum
6. **Accessibility**: Respecting user preferences

By following these guidelines and using the values documented here, you can create animations that feel as polished and professional as Apple's iOS and Google's Material Design 3.

---

**Last Updated**: January 2026
**Sources**: Apple HIG, Material Design 3, Android Developer Documentation
