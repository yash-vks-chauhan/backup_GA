# Animation & Transition Quick Reference
## TL;DR - Copy-Paste Ready Values

---

## 🎯 Most Common Values (Use These First)

### Standard Durations
```kotlin
// Micro-interactions (icons, checkboxes)
duration = 150 // ms

// Buttons, small cards
duration = 200 // ms

// Menus, tabs, standard transitions
duration = 300 // ms

// Sidebar, navigation drawer
duration = 350 // ms

// Bottom sheets, dialogs
duration = 400 // ms

// Full-screen transitions
duration = 500 // ms
```

### Standard Easing (Material Design 3)
```kotlin
// Most common - use this for 80% of animations
PathInterpolator(0.2f, 0f, 0f, 1.0f) // Emphasized

// Elements entering screen
PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f) // Emphasized Decelerate

// Elements exiting screen
PathInterpolator(0.3f, 0f, 0.8f, 0.15f) // Emphasized Accelerate
```

### Standard Spring (Apple-Style)
```kotlin
// Professional & clean (no bounce)
dampingRatio = 1.0f
stiffness = 1500f

// Apple-style subtle bounce
dampingRatio = 0.7f
stiffness = 300f

// Engaging & playful
dampingRatio = 0.5f
stiffness = 200f
```

---

## 📱 Component-Specific Copy-Paste Code

### Sidebar / Navigation Drawer Animation

```kotlin
// In your DrawerLayout.DrawerListener
override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
    // Slide main content
    mainContent.translationX = drawerView.width * slideOffset * 0.7f
    
    // Fade scrim
    scrimView.alpha = slideOffset * 0.5f
    
    // Optional: Scale main content slightly
    val scale = 1f - (slideOffset * 0.05f) // 1.0 to 0.95
    mainContent.scaleX = scale
    mainContent.scaleY = scale
}

// Custom drawer animation
view.animate()
    .translationX(targetX)
    .setDuration(350)
    .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1.0f))
    .start()
```

### Bottom Sheet Animation

```kotlin
// Using BottomSheetBehavior
val behavior = BottomSheetBehavior.from(bottomSheet)
behavior.apply {
    peekHeight = 400 // Initial visible height
    isHideable = true
    isDraggable = true
    halfExpandedRatio = 0.5f
}

// Custom spring animation for bottom sheet content
val springAnim = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f)
springAnim.spring.apply {
    dampingRatio = 0.9f // High damping for smooth snap
    stiffness = 300f
}
springAnim.start()
```

### Button Press Animation

```kotlin
// On press - scale down
button.animate()
    .scaleX(0.95f)
    .scaleY(0.95f)
    .setDuration(200)
    .setInterpolator(DecelerateInterpolator())
    .start()

// On release - spring back
val scaleX = SpringAnimation(button, DynamicAnimation.SCALE_X, 1f)
val scaleY = SpringAnimation(button, DynamicAnimation.SCALE_Y, 1f)

scaleX.spring.apply {
    dampingRatio = 0.7f
    stiffness = 300f
}
scaleY.spring.apply {
    dampingRatio = 0.7f
    stiffness = 300f
}

scaleX.start()
scaleY.start()

// Add haptic feedback
button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
```

### Dialog Fade In/Out

```kotlin
// Fade in with scale
dialog.alpha = 0f
dialog.scaleX = 0.9f
dialog.scaleY = 0.9f
dialog.visibility = View.VISIBLE

dialog.animate()
    .alpha(1f)
    .scaleX(1f)
    .scaleY(1f)
    .setDuration(300)
    .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
    .start()

// Fade out with scale
dialog.animate()
    .alpha(0f)
    .scaleX(0.95f)
    .scaleY(0.95f)
    .setDuration(250)
    .setInterpolator(PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
    .withEndAction { dialog.visibility = View.GONE }
    .start()
```

### Card Expand/Collapse

```kotlin
// Expand
val startHeight = card.height
val endHeight = expandedHeight

val animator = ValueAnimator.ofInt(startHeight, endHeight)
animator.apply {
    duration = 350
    interpolator = PathInterpolator(0.2f, 0f, 0f, 1.0f)
    addUpdateListener { animation ->
        val value = animation.animatedValue as Int
        card.layoutParams.height = value
        card.requestLayout()
    }
}
animator.start()

// Fade in expanded content
expandedContent.alpha = 0f
expandedContent.visibility = View.VISIBLE
expandedContent.animate()
    .alpha(1f)
    .setDuration(250)
    .setStartDelay(100) // Start after card begins expanding
    .start()
```

### Hamburger to Arrow Icon Animation

```kotlin
// Using AnimatedVectorDrawable in XML
<animated-vector
    android:drawable="@drawable/hamburger">
    <target
        android:name="top_bar"
        android:animation="@animator/top_bar_to_arrow" />
    <target
        android:name="middle_bar"
        android:animation="@animator/middle_bar_fade" />
    <target
        android:name="bottom_bar"
        android:animation="@animator/bottom_bar_to_arrow" />
</animated-vector>

// In code
val drawable = menuIcon.drawable as AnimatedVectorDrawable
drawable.start()
```

### Fade Through Transition (Tab Switch)

```kotlin
// Fade out current
currentView.animate()
    .alpha(0f)
    .setDuration(150)
    .withEndAction {
        currentView.visibility = View.GONE
        
        // Fade in next
        nextView.alpha = 0f
        nextView.visibility = View.VISIBLE
        nextView.animate()
            .alpha(1f)
            .setDuration(150)
            .start()
    }
    .start()
```

### Shared Axis Transition (Navigation)

```kotlin
// Horizontal (X-axis) - for peer-to-peer navigation
val exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
exitTransition.duration = 300

val enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
enterTransition.duration = 300

// Vertical (Y-axis) - for stepper/wizard
val exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
val enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)

// Depth (Z-axis) - for parent-child navigation
val exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
val enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
```

### Dropdown Menu (Apple Style)

```kotlin
// Apple-style dropdown with growth animation
fun showDropdownMenu(anchorView: View, menu: View) {
    // Set initial state
    menu.alpha = 0f
    menu.scaleX = 0.85f
    menu.scaleY = 0.85f
    menu.visibility = View.VISIBLE
    
    // Set pivot to anchor point (where menu originates)
    menu.pivotX = anchorX // Touch point or button center
    menu.pivotY = 0f // Top of menu
    
    // Spring animation for snappy feel
    val scaleX = SpringAnimation(menu, DynamicAnimation.SCALE_X, 1f)
    val scaleY = SpringAnimation(menu, DynamicAnimation.SCALE_Y, 1f)
    
    scaleX.spring.apply {
        dampingRatio = 0.8f // High damping for clean snap
        stiffness = 400f // Snappy response
    }
    scaleY.spring.apply {
        dampingRatio = 0.8f
        stiffness = 400f
    }
    
    // Fade in simultaneously
    menu.animate()
        .alpha(1f)
        .setDuration(250)
        .setInterpolator(DecelerateInterpolator())
        .start()
    
    scaleX.start()
    scaleY.start()
    
    // Haptic feedback for tactile feel
    menu.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

// Dismiss dropdown
fun dismissDropdownMenu(menu: View) {
    menu.animate()
        .alpha(0f)
        .scaleX(0.95f)
        .scaleY(0.95f)
        .setDuration(200) // Faster dismissal
        .setInterpolator(AccelerateInterpolator())
        .withEndAction { menu.visibility = View.GONE }
        .start()
}
```

### Context Menu with Lift Effect (Apple Style)

```kotlin
// Full Apple-style context menu with lift effect
view.setOnLongClickListener {
    // Step 1: Shrink source view (pressure feedback)
    it.animate()
        .scaleX(0.96f)
        .scaleY(0.96f)
        .setDuration(150)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            // Step 2: Show backdrop blur
            val blurOverlay = createBlurOverlay()
            blurOverlay.alpha = 0f
            rootView.addView(blurOverlay)
            
            blurOverlay.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
            
            // Step 3: Show menu with growth
            val contextMenu = createContextMenu()
            showContextMenu(contextMenu, it)
            
            // Step 4: Lift source view
            it.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .translationZ(8.dp)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        .start()
    
    // Haptic feedback at start
    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    true
}

fun showContextMenu(menu: View, anchorView: View) {
    menu.alpha = 0f
    menu.scaleX = 0.85f
    menu.scaleY = 0.85f
    menu.visibility = View.VISIBLE
    
    // Position relative to anchor
    positionMenuNearAnchor(menu, anchorView)
    
    // Spring animation
    val scaleX = SpringAnimation(menu, DynamicAnimation.SCALE_X, 1f)
    val scaleY = SpringAnimation(menu, DynamicAnimation.SCALE_Y, 1f)
    
    scaleX.spring.apply {
        dampingRatio = 0.8f
        stiffness = 400f
    }
    scaleY.spring.apply {
        dampingRatio = 0.8f
        stiffness = 400f
    }
    
    menu.animate()
        .alpha(1f)
        .setDuration(250)
        .start()
    
    scaleX.start()
    scaleY.start()
}
```

### Popover Menu (iPad/Tablet Style)

```kotlin
// Popover with arrow indicator
fun showPopover(anchorView: View, popoverView: View) {
    // Initial state
    popoverView.alpha = 0f
    popoverView.scaleX = 0.9f // Slightly larger initial scale
    popoverView.scaleY = 0.9f
    popoverView.visibility = View.VISIBLE
    
    // Position with smart placement (below, above, or sides)
    val position = calculateBestPosition(anchorView, popoverView)
    positionPopover(popoverView, anchorView, position)
    
    // Animate arrow in sync
    val arrow = popoverView.findViewById<View>(R.id.popover_arrow)
    arrow.alpha = 0f
    
    // Spring animation
    val scaleX = SpringAnimation(popoverView, DynamicAnimation.SCALE_X, 1f)
    val scaleY = SpringAnimation(popoverView, DynamicAnimation.SCALE_Y, 1f)
    
    scaleX.spring.apply {
        dampingRatio = 0.8f
        stiffness = 400f
    }
    scaleY.spring.apply {
        dampingRatio = 0.8f
        stiffness = 400f
    }
    
    popoverView.animate()
        .alpha(1f)
        .setDuration(250)
        .start()
    
    arrow.animate()
        .alpha(1f)
        .setDuration(250)
        .start()
    
    scaleX.start()
    scaleY.start()
}
```

---

## 🎨 XML Definitions

### Interpolators (res/anim/)

**emphasized_interpolator.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.2"
    android:controlY1="0"
    android:controlX2="0"
    android:controlY2="1.0" />
```

**emphasized_decelerate_interpolator.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.05"
    android:controlY1="0.7"
    android:controlX2="0.1"
    android:controlY2="1.0" />
```

**emphasized_accelerate_interpolator.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.3"
    android:controlY1="0"
    android:controlX2="0.8"
    android:controlY2="0.15" />
```

### Animations (res/anim/)

**fade_in.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="300"
    android:fromAlpha="0.0"
    android:toAlpha="1.0"
    android:interpolator="@anim/emphasized_decelerate_interpolator" />
```

**fade_out.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="250"
    android:fromAlpha="1.0"
    android:toAlpha="0.0"
    android:interpolator="@anim/emphasized_accelerate_interpolator" />
```

**slide_in_right.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <translate
        android:duration="300"
        android:fromXDelta="100%"
        android:toXDelta="0%"
        android:interpolator="@anim/emphasized_decelerate_interpolator" />
    <alpha
        android:duration="300"
        android:fromAlpha="0.0"
        android:toAlpha="1.0" />
</set>
```

**slide_out_left.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <translate
        android:duration="300"
        android:fromXDelta="0%"
        android:toXDelta="-30%"
        android:interpolator="@anim/emphasized_accelerate_interpolator" />
    <alpha
        android:duration="300"
        android:fromAlpha="1.0"
        android:toAlpha="0.3" />
</set>
```

**scale_fade_in.xml** (for dialogs)
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <scale
        android:duration="300"
        android:fromXScale="0.9"
        android:fromYScale="0.9"
        android:toXScale="1.0"
        android:toYScale="1.0"
        android:pivotX="50%"
        android:pivotY="50%"
        android:interpolator="@anim/emphasized_decelerate_interpolator" />
    <alpha
        android:duration="300"
        android:fromAlpha="0.0"
        android:toAlpha="1.0" />
</set>
```

---

## 🔧 Utility Functions

### Kotlin Extension Functions

```kotlin
// View extensions for common animations
fun View.fadeIn(duration: Long = 300) {
    alpha = 0f
    visibility = View.VISIBLE
    animate()
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
        .start()
}

fun View.fadeOut(duration: Long = 250, gone: Boolean = true) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
        .withEndAction {
            visibility = if (gone) View.GONE else View.INVISIBLE
        }
        .start()
}

fun View.scaleIn(duration: Long = 300, fromScale: Float = 0.9f) {
    scaleX = fromScale
    scaleY = fromScale
    alpha = 0f
    visibility = View.VISIBLE
    
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
        .start()
}

fun View.scaleOut(duration: Long = 250, toScale: Float = 0.95f, gone: Boolean = true) {
    animate()
        .scaleX(toScale)
        .scaleY(toScale)
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
        .withEndAction {
            visibility = if (gone) View.GONE else View.INVISIBLE
        }
        .start()
}

fun View.slideInFromRight(duration: Long = 300) {
    translationX = width.toFloat()
    visibility = View.VISIBLE
    animate()
        .translationX(0f)
        .setDuration(duration)
        .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
        .start()
}

fun View.slideOutToLeft(duration: Long = 300, gone: Boolean = true) {
    animate()
        .translationX(-width.toFloat())
        .setDuration(duration)
        .setInterpolator(PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
        .withEndAction {
            visibility = if (gone) View.GONE else View.INVISIBLE
        }
        .start()
}

fun View.springTo(
    property: DynamicAnimation.ViewProperty,
    targetValue: Float,
    dampingRatio: Float = 0.7f,
    stiffness: Float = 300f
) {
    val spring = SpringAnimation(this, property, targetValue)
    spring.spring.apply {
        this.dampingRatio = dampingRatio
        this.stiffness = stiffness
    }
    spring.start()
}

// Usage examples:
// view.fadeIn()
// view.fadeOut(gone = true)
// view.scaleIn()
// view.springTo(DynamicAnimation.TRANSLATION_Y, 0f)
```

### Animation Helper Class

```kotlin
object AnimationHelper {
    
    // Standard durations
    const val DURATION_SHORT = 150L
    const val DURATION_MEDIUM = 300L
    const val DURATION_LONG = 450L
    
    // Standard interpolators
    val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1.0f)
    val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
    val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    
    // Spring configurations
    data class SpringConfig(
        val dampingRatio: Float,
        val stiffness: Float
    )
    
    val SPRING_CLEAN = SpringConfig(1.0f, 1500f)
    val SPRING_APPLE = SpringConfig(0.7f, 300f)
    val SPRING_PLAYFUL = SpringConfig(0.5f, 200f)
    
    fun createSpring(
        view: View,
        property: DynamicAnimation.ViewProperty,
        targetValue: Float,
        config: SpringConfig = SPRING_APPLE
    ): SpringAnimation {
        return SpringAnimation(view, property, targetValue).apply {
            spring.dampingRatio = config.dampingRatio
            spring.stiffness = config.stiffness
        }
    }
}

// Usage:
// AnimationHelper.createSpring(view, DynamicAnimation.SCALE_X, 1f).start()
```

---

## 📊 Decision Tree

### "Which duration should I use?"

```
Is it a micro-interaction (checkbox, icon)?
├─ YES → 150ms
└─ NO → Is it a button or small card?
    ├─ YES → 200ms
    └─ NO → Is it a menu, tab, or standard transition?
        ├─ YES → 300ms
        └─ NO → Is it a sidebar or navigation drawer?
            ├─ YES → 350ms
            └─ NO → Is it a bottom sheet or dialog?
                ├─ YES → 400ms
                └─ NO → Full-screen transition → 500ms
```

### "Which easing should I use?"

```
Is the element entering the screen?
├─ YES → Emphasized Decelerate (0.05, 0.7, 0.1, 1.0)
└─ NO → Is the element exiting the screen?
    ├─ YES → Emphasized Accelerate (0.3, 0, 0.8, 0.15)
    └─ NO → Standard Emphasized (0.2, 0, 0, 1.0)
```

### "Should I use spring or easing?"

```
Is it an interactive element (draggable, touchable)?
├─ YES → Use Spring Animation
│   └─ Want bounce? → damping 0.5-0.7
│       └─ No bounce? → damping 1.0
└─ NO → Use Easing (PathInterpolator)
```

---

## 🎬 Common Patterns Cheat Sheet

| Pattern | Duration | Easing | Code Snippet |
|---------|----------|--------|--------------|
| **Fade In** | 300ms | Decelerate | `view.fadeIn()` |
| **Fade Out** | 250ms | Accelerate | `view.fadeOut()` |
| **Scale In** | 300ms | Decelerate | `view.scaleIn()` |
| **Scale Out** | 250ms | Accelerate | `view.scaleOut()` |
| **Slide In** | 300ms | Decelerate | `view.slideInFromRight()` |
| **Slide Out** | 300ms | Accelerate | `view.slideOutToLeft()` |
| **Button Press** | 200ms | Spring (0.7) | `view.springTo(SCALE_X, 0.95f)` |
| **Button Release** | 200ms | Spring (0.7) | `view.springTo(SCALE_X, 1f)` |
| **Dialog Show** | 300ms | Scale + Fade | `view.scaleIn(fromScale = 0.9f)` |
| **Dialog Hide** | 250ms | Scale + Fade | `view.scaleOut(toScale = 0.95f)` |
| **Dropdown Menu** | **250ms** | **Spring (0.8)** | **`showDropdownMenu(anchor, menu)`** |
| **Context Menu** | **250ms** | **Spring (0.8)** | **`showContextMenu(menu, anchor)`** |
| **Popover** | **250ms** | **Spring (0.8)** | **`showPopover(anchor, popover)`** |

---

## 🚀 Performance Tips

1. **Use hardware acceleration**
   ```kotlin
   view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
   // After animation
   view.setLayerType(View.LAYER_TYPE_NONE, null)
   ```

2. **Animate only transform properties** (faster)
   - ✅ translationX, translationY, scaleX, scaleY, rotation, alpha
   - ❌ width, height, margin, padding (causes layout)

3. **Use ViewPropertyAnimator** for simple animations
   ```kotlin
   view.animate().alpha(1f).setDuration(300).start()
   ```

4. **Batch animations together**
   ```kotlin
   view.animate()
       .alpha(1f)
       .scaleX(1f)
       .scaleY(1f)
       .setDuration(300)
       .start()
   ```

5. **Cancel previous animations**
   ```kotlin
   view.animate().cancel()
   view.animate().alpha(1f).start()
   ```

---

**Quick Tip**: When in doubt, use **300ms** duration with **Emphasized** easing `(0.2, 0, 0, 1.0)`. This works for 80% of UI animations!
