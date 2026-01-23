# UX Physics & Animation Guide

This document details the advanced physics-based animation systems and Apple-inspired transition logic implemented in the Gridee Android application.

## 1. Design Philosophy: "Feel" over "Look"
Modern interactions, particularly those pioneered by iOS, rely on the principle of **Direct Manipulation**. When a user interacts with a digital object, it should respond with:
*   **Weight/Inertia**: Objects shouldn't stop instantly; they should carry momentum.
*   **Elasticity**: Objects should stretch or bounce when hitting limits, rather than hitting a hard wall.
*   **Fluidity**: Animations should run at 60fps+ and be interruptible.

## 2. The "Rubber Band" Scroll Effect
One of the most distinct iOS features is the "Rubber Band" effect when scrolling past the edge of a list. Standard Android usually shows a "glow" (EdgeEffect). We have replaced this with a physics-based translation engine.

### Implementation: `BounceEdgeEffectFactory`
Located in: `com.gridee.parking.ui.utils.BounceEdgeEffectFactory`

This class extends `RecyclerView.EdgeEffectFactory` to intercept scroll events at the boundaries.

#### How it works:
1.  **Translation instead of Glow**: When `onPull()` is detected (finger dragging past the edge), we calculate a `translationDelta`.
    *   **Logic**: `deltaDistance * magnitude * size`
    *   **Magnitude**: `0.2f` (OVERSCROLL_TRANSLATION_MAGNITUDE). This factor mimics the "resistance" of a rubber band stretching. It's harder to pull the further you go.
2.  **Physics-based Recovery**: When the user releases the finger (`onRelease()`), we don't just "snap" back linearly. We use a **SpringForce**.

### The Physics Engine: `SpringAnimation`
We utilize the `androidx.dynamicanimation` library, which provides physics-based animations.

```kotlin
SpringAnimation(view, SpringAnimation.TRANSLATION_Y)
    .setSpring(SpringForce()
        .setFinalPosition(0f) // Rest position
        .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
        .setStiffness(SpringForce.STIFFNESS_LOW)
    )
```

*   **Stiffness (Low)**: Determines how "tight" the spring is. Low stiffness makes it feel like a loose, heavy rubber band, which feels more "premium" and less "jittery."
*   **Damping (Medium Bouncy)**: Determines friction. If too high, it stops instantly. If too low, it oscillates forever. "Medium Bouncy" provides that signature single rebound settle.

## 3. Micro-Interactions (Touch Feedback)
We implement "Squish" or "Scale" effects on buttons to provide tactile feedback, mimicking the depression of a physical button.

### Implementation: `touch_scale.xml`
Located in: `res/animator/touch_scale.xml`

This `StateListAnimator` is attached to interactive elements.
*   **On Press**: Scales down to `0.95` (95% size).
*   **On Release**: Springs back to `1.0`.
*   **Duration**: Short (100-150ms) to feel snappy.

```xml
<selector>
    <item android:state_pressed="true">
        <set>
            <objectAnimator android:propertyName="scaleX" android:valueTo="0.95" ... />
            <objectAnimator android:propertyName="scaleY" android:valueTo="0.95" ... />
        </set>
    </item>
    <!-- Default state restores to 1.0 -->
</selector>
```

## 4. Ripple Effects & Shadows
*   **Shadows**: We often remove standard elevation shadows in favor of cleaner, flat designs with "implied" depth through borders or spacing, or extremely subtle, diffused shadows (creating a "glass" feel logic).
*   **Ripples**: Used `?attr/selectableItemBackground` or custom ripple drawables masked to rounded corners (Pill/Circle shapes) to ensure the touch feedback doesn't bleed outside the component's logical boundary.

## 5. Accordion Animation Principles (Apple-Inspired)

### Research Source
Based on comprehensive research of:
- Apple Human Interface Guidelines (HIG)
- Apple.com website accordion implementations
- SwiftUI Transition documentation
- Material Design 3 specifications
- Android SpringAnimation best practices

### 5.1 Apple's Core Animation Philosophy

Apple's accordion animations follow these fundamental principles:

**"Snappy Take-offs with Very Soft Landings"**
- Animations start quickly to feel responsive
- They decelerate smoothly to feel organic and natural
- No harsh stops or mechanical linear motion

**Asymmetric Timing**
- **Opening (Expansion)**: 300-400ms - Needs time for comprehension
- **Closing (Collapse)**: 250-350ms - Faster, requires less attention
- **Rule**: Collapsing elements should be 50-100ms faster than expanding

**No Bounce on Accordions**
- Unlike spring-based scrolling, accordions use smooth, controlled motion
- Bounce/overshoot feels unprofessional in this context
- Focus on clean, minimal, precise animations

### 5.2 Easing Curves & Timing Functions

#### Apple's Preferred Curves

**For Opening (Expansion):**
```
Curve: ease-out or easeInOut
CSS Equivalent: cubic-bezier(0.4, 0, 0.2, 1)
Android: FastOutSlowInInterpolator()
Behavior: Quick start, gradual deceleration
```

**For Closing (Collapse):**
```
Curve: ease-in or linear-to-accelerate
CSS Equivalent: cubic-bezier(0.4, 0, 1.0, 1.0)
Android: AccelerateInterpolator()
Behavior: Gradual acceleration, quick finish
```

**Icon/Arrow Rotation:**
```
Curve: ease-out (decelerate)
Android: DecelerateInterpolator()
Duration: 280-300ms (synchronized or slightly faster)
```

### 5.3 Material Design Comparison

| Aspect | Material Design | Apple HIG |
|--------|----------------|-----------|
| **Standard Duration** | 225ms | 300-350ms |
| **Standard Easing** | cubic-bezier(0.4, 0, 0.2, 1) | Same |
| **Expansion Panel** | 225ms expansion | 300-400ms expansion |
| **Collapse Speed** | 195-250ms | 250-300ms |
| **Max Duration** | 400ms (feels slow) | 400ms (hard limit) |
| **Bounce/Spring** | Optional, M3 prefers springs | Not on accordions |

**Key Insight**: Both systems converge on 300ms as the "sweet spot" for mobile interactions.

### 5.4 Animation Components Breakdown

A professional accordion animation consists of **4 synchronized components**:

#### Component 1: Height Animation
```kotlin
// Opening
ValueAnimator.ofInt(0, targetHeight)
    .apply {
        duration = 350L
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener { 
            view.layoutParams.height = it.animatedValue as Int
            view.requestLayout()
        }
    }

// Closing
ValueAnimator.ofInt(currentHeight, 0)
    .apply {
        duration = 300L  // Faster
        interpolator = AccelerateInterpolator()
        doOnEnd { view.visibility = View.GONE }
    }
```

#### Component 2: Alpha/Opacity Fade
```kotlin
// Opening: Fade In
view.animate()
    .alpha(1f)
    .setDuration(350L)
    .setInterpolator(FastOutSlowInInterpolator())

// Closing: Fade Out (Faster)
view.animate()
    .alpha(0f)
    .setDuration(250L)  // Quicker fade
    .setInterpolator(AccelerateInterpolator())
```

#### Component 3: TranslationY (Slide Effect)
```kotlin
// Opening: Slide Down
view.translationY = -20f.dpToPx()
view.animate()
    .translationY(0f)
    .setDuration(350L)
    .setInterpolator(FastOutSlowInInterpolator())

// Closing: Slide Up
view.animate()
    .translationY(-20f.dpToPx())
    .setDuration(300L)
    .setInterpolator(AccelerateInterpolator())
```

**Why -20dp?**
- Subtle enough to feel smooth
- Noticeable enough to add sophistication
- Apple uses similar subtle offsets (10-30dp range)

#### Component 4: Arrow Icon Rotation
```kotlin
// Opening
arrowIcon.animate()
    .rotation(180f)
    .setDuration(300L)
    .setInterpolator(DecelerateInterpolator())

// Closing
arrowIcon.animate()
    .rotation(0f)
    .setDuration(280L)  // Slightly faster
    .setInterpolator(AccelerateInterpolator())
```

### 5.5 Advanced: Content Stagger Animation

For multiple items appearing inside the accordion (Apple-style):

```kotlin
// When expanding, stagger child item appearances
items.forEachIndexed { index, item ->
    itemView.alpha = 0f
    itemView.translationY = -10f.dpToPx()
    
    itemView.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(200L)
        .setStartDelay((index * 40L))  // 40ms stagger per item
        .setInterpolator(FastOutSlowInInterpolator())
        .start()
}
```

**Result**: Items appear sequentially with a cascading effect (very Apple-like).

### 5.6 Spring Physics: When NOT to Use

**❌ Avoid SpringAnimation for Accordions:**
- Scale animations (scaleX/scaleY) - Apple doesn't use this
- Bouncy damping ratios - Creates unprofessional "bouncing" effect
- Uncontrolled durations - Springs decide their own timing, breaking synchronization

**✅ Use SpringAnimation for:**
- Scroll overscroll effects (rubber band)
- Pull-to-refresh animations
- Draggable UI components
- Modal dismissal gestures

**For Accordions, stick to synchronized ValueAnimator + ViewPropertyAnimator combos.**

### 5.7 Real-World Example: Apple.com Footer

**Observed Behavior** (from apple.com mobile site):
```
Opening:
- Effect: Slide down + Fade in
- Duration: ~350-400ms
- Easing: Ease-out (fast start, slow end)
- Height: Smooth expansion (no jank)
- Icon: 180° rotation synchronized

Closing:
- Effect: Slide up + Fade out
- Duration: ~300ms (noticeably faster)
- Easing: Ease-in (accelerates into closure)
- Height: Quick collapse
- Icon: Returns to 0° smoothly
```

### 5.8 Performance Considerations

**Optimize for 60fps:**
1. **Avoid layout thrashing**: Batch layout updates
2. **Use hardware acceleration**: `setLayerType(HARDWARE)` during animation
3. **Remove unnecessary redraws**: Set `willNotDraw(false)` on containers
4. **Measure once**: Cache measured heights, don't re-measure every frame

```kotlin
// Before animation
expandedLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null)

// After animation
doOnEnd { 
    expandedLayout.setLayerType(View.LAYER_TYPE_NONE, null) 
}
```

### 5.9 Accessibility Considerations

**Important**: Respect user preference for reduced motion:

```kotlin
private fun isReduceMotionEnabled(): Boolean {
    val resolver = context.contentResolver
    return Settings.Global.getFloat(
        resolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f
    ) == 0f
}

// In animation logic
val duration = if (isReduceMotionEnabled()) 0L else 350L
```

### 5.10 Quick Reference: Accordion Animation Checklist

**✅ DO:**
- Use FastOutSlowInInterpolator for opening
- Use AccelerateInterpolator for closing
- Keep opening: 300-350ms, closing: 250-300ms
- Combine slide + fade effects
- Synchronize all animations (same duration)
- Add subtle translationY offset (-20dp)
- Make collapse faster than expansion
- Test on low-end devices for smoothness

**❌ DON'T:**
- Use linear interpolators (feels robotic)
- Exceed 400ms duration (feels sluggish)
- Use bounce/spring on height changes
- Use scale animations on accordion content
- Forget to hide view after collapse
- Animate on UI thread (use proper animators)
- Ignore reduced motion preferences

### 5.11 Implementation Template

```kotlin
private fun toggleAccordion() {
    val isExpanding = !isExpanded
    isExpanded = isExpanding
    
    if (isExpanding) {
        // OPENING - Apple Style
        expandedView.visibility = View.VISIBLE
        expandedView.alpha = 0f
        expandedView.translationY = -20f.dpToPx()
        
        // Measure target height
        expandedView.measure(
            View.MeasureSpec.makeMeasureSpec(expandedView.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val targetHeight = expandedView.measuredHeight
        
        // Height expand
        ValueAnimator.ofInt(0, targetHeight).apply {
            duration = 350
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { 
                expandedView.layoutParams.height = it.animatedValue as Int
                expandedView.requestLayout()
            }
            start()
        }
        
        // Fade + Slide
        expandedView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        
        // Arrow
        arrowIcon.animate()
            .rotation(180f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
            
    } else {
        // CLOSING - Apple Style
        val currentHeight = expandedView.height
        
        // Height collapse
        ValueAnimator.ofInt(currentHeight, 0).apply {
            duration = 300
            interpolator = AccelerateInterpolator()
            addUpdateListener {
                expandedView.layoutParams.height = it.animatedValue as Int
                expandedView.requestLayout()
            }
            doOnEnd { expandedView.visibility = View.GONE }
            start()
        }
        
        // Fade + Slide
        expandedView.animate()
            .alpha(0f)
            .translationY(-20f.dpToPx())
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .start()
        
        // Arrow
        arrowIcon.animate()
            .rotation(0f)
            .setDuration(280)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }
}

private fun Float.dpToPx(): Float = 
    this * Resources.getSystem().displayMetrics.density
```

### 5.12 Key Takeaway

**Apple's accordion philosophy in one sentence:**
> "Expand with care and patience (350ms ease-out), collapse with confidence and speed (300ms ease-in)."

The user should **feel** the expansion reveal content thoughtfully, and **barely notice** the collapse as it quickly clears space. This asymmetry creates a professional, intentional user experience.

---

## Summary
By combining **Spring Physics** for large gestures (scrolling) and **Scale Animations** for precise gestures (tapping), we achieve a tactile, organic system that feels alive and responsive, closely mirroring the "Apple-level" quality standard.
