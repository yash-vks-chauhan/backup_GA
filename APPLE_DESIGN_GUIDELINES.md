# Apple UI/UX Design Guidelines Reference

> **Comprehensive styling guide based on Apple's Human Interface Guidelines**  
> Research conducted: January 8, 2026  
> Source: [Apple Developer Documentation](https://developer.apple.com/design/human-interface-guidelines/)

---

## 📋 Table of Contents

1. [Shadow & Depth Philosophy](#shadow--depth-philosophy)
2. [Button Styling Guidelines](#button-styling-guidelines)
3. [Color System](#color-system)
4. [Typography System](#typography-system)
5. [Layout & Spacing](#layout--spacing)
6. [Component Specifications](#component-specifications)
7. [Interaction States](#interaction-states)
8. [Accessibility Considerations](#accessibility-considerations)
9. [Android Implementation Guide](#android-implementation-guide)
10. [Official Resources](#official-resources)

---

## 🎨 Shadow & Depth Philosophy

### Materials Over Traditional Shadows

Apple has **moved away from heavy drop shadows** and instead uses a material-based approach:

#### **Liquid Glass Material** *(Updated Dec 16, 2025)*
- **Translucent backgrounds** with blur effects
- Allows underlying content to peek through
- Creates depth without obscuring context
- **Used for:** Sidebars, tab bars, toolbars, navigation bars

#### **Vibrancy System**
- Text and UI elements pull through background colors
- Maintains legibility on translucent surfaces
- Creates a "glassy" aesthetic
- Adapts to light and dark modes automatically

#### **Elevation Through Layering**
- Higher-level elements (modals, popovers) appear "closer" to user
- Soft, diffused shadows for elevated components
- Background elements remain flat
- Z-axis depth communicated through material changes

### **Key Principle:**
> **"Depth is communicated through materials and translucency, not heavy shadows"**

### **Shadow Specifications:**
- **No hard drop shadows** on standard UI elements
- **Soft, diffused shadows** only for:
  - Floating modals
  - Popovers
  - Context menus
  - Elevated cards
- **Shadow characteristics:**
  - Low opacity (10-20%)
  - Large blur radius
  - Minimal offset
  - Natural, subtle appearance

---

## 🔘 Button Styling Guidelines

### Button Hierarchy (4 Standard Styles)

Apple defines **four distinct button styles** to communicate hierarchy:

#### **1. Filled Buttons**
- **Purpose:** Primary action / Most likely user action
- **Usage:** Limit to **1-2 per screen**
- **Visual Weight:** Highest
- **Characteristics:**
  - Solid background color (typically System Blue)
  - White text for contrast
  - Clear press state (darkens 10-20% on tap)
  - Corner radius: **10-12pt**
  - Prominent and eye-catching

**Example Use Cases:**
- "Submit" button on forms
- "Continue" in onboarding
- "Save" in editing screens

#### **2. Tinted Buttons**
- **Purpose:** Secondary actions
- **Visual Weight:** Medium
- **Characteristics:**
  - Tinted background (lighter than filled)
  - Colored text matching tint
  - Subtle press state
  - Less prominent than filled

**Example Use Cases:**
- "Cancel" alongside primary action
- Alternative options
- Secondary navigation

#### **3. Gray Buttons**
- **Purpose:** Secondary actions with neutral emphasis
- **Visual Weight:** Medium-low
- **Characteristics:**
  - Gray background
  - Dark text
  - Neutral appearance
  - Non-destructive actions

**Example Use Cases:**
- "Maybe Later" options
- Neutral choices
- Dismissal actions

#### **4. Plain Buttons**
- **Purpose:** Tertiary / Subtle actions
- **Visual Weight:** Lowest
- **Characteristics:**
  - No background
  - Colored text only
  - Minimal visual footprint
  - Least intrusive

**Example Use Cases:**
- "Learn More" links
- Inline actions
- Navigation links

### **Mandatory Button Requirements:**

✅ **Always include a press state**  
Without it, buttons feel unresponsive, making users wonder if their input was registered.

✅ **Use prominent style for primary action**  
Guide users to the most likely choice through visual hierarchy.

✅ **Limit prominent buttons**  
Keep to 1-2 per view to avoid cognitive overload and decision paralysis.

✅ **Maintain minimum touch target**  
44x44pt minimum for all interactive elements.

---

## 🎨 Color System

### System Colors (Dynamic - Adapt to Light/Dark Mode)

Apple uses **semantic system colors** that automatically adapt to:
- Light and dark modes
- Increased contrast settings
- Accessibility preferences

#### **iOS/iPadOS Standard Colors**

| Color | SwiftUI API | Light Mode RGB | Dark Mode RGB |
|-------|-------------|----------------|---------------|
| **Red** | `.red` | `rgb(255, 59, 48)` | `rgb(255, 69, 58)` |
| **Orange** | `.orange` | `rgb(255, 149, 0)` | `rgb(255, 159, 10)` |
| **Yellow** | `.yellow` | `rgb(255, 204, 0)` | `rgb(255, 214, 10)` |
| **Green** | `.green` | `rgb(52, 199, 89)` | `rgb(48, 209, 88)` |
| **Mint** | `.mint` | `rgb(0, 199, 190)` | `rgb(99, 230, 226)` |
| **Teal** | `.teal` | `rgb(48, 176, 199)` | `rgb(64, 200, 224)` |
| **Cyan** | `.cyan` | `rgb(50, 173, 230)` | `rgb(100, 210, 255)` |
| **Blue** | `.blue` | `rgb(0, 122, 255)` | `rgb(10, 132, 255)` |
| **Indigo** | `.indigo` | `rgb(88, 86, 214)` | `rgb(94, 92, 230)` |
| **Purple** | `.purple` | `rgb(175, 82, 222)` | `rgb(191, 90, 242)` |
| **Pink** | `.pink` | `rgb(255, 45, 85)` | `rgb(255, 55, 95)` |
| **Brown** | `.brown` | `rgb(162, 132, 94)` | `rgb(172, 142, 104)` |

#### **System Gray Colors**

| Name | UIKit API | Light Mode | Dark Mode |
|------|-----------|------------|-----------|
| **Gray** | `systemGray` | `rgb(142, 142, 147)` | `rgb(142, 142, 147)` |
| **Gray 2** | `systemGray2` | `rgb(174, 174, 178)` | `rgb(99, 99, 102)` |
| **Gray 3** | `systemGray3` | `rgb(199, 199, 204)` | `rgb(72, 72, 74)` |
| **Gray 4** | `systemGray4` | `rgb(209, 209, 214)` | `rgb(58, 58, 60)` |
| **Gray 5** | `systemGray5` | `rgb(229, 229, 234)` | `rgb(44, 44, 46)` |
| **Gray 6** | `systemGray6` | `rgb(242, 242, 247)` | `rgb(28, 28, 30)` |

### Semantic Colors (UI Elements)

| Purpose | UIKit API | Usage |
|---------|-----------|-------|
| **Label** | `label` | Primary text content |
| **Secondary Label** | `secondaryLabel` | Less important text |
| **Tertiary Label** | `tertiaryLabel` | Disabled or placeholder text |
| **Quaternary Label** | `quaternaryLabel` | Watermarks or subtle text |
| **Separator** | `separator` | Dividing lines (translucent) |
| **Opaque Separator** | `opaqueSeparator` | Solid dividing lines |
| **Background** | `systemBackground` | Primary background |
| **Secondary Background** | `secondarySystemBackground` | Grouped content background |
| **Tertiary Background** | `tertiarySystemBackground` | Nested grouped content |

### **Color Best Practices:**

✅ **Use system colors instead of hard-coded values**  
They automatically adapt to light/dark mode and accessibility settings.

✅ **Provide light and dark variants**  
Even for custom colors, supply both variants.

✅ **Test with Increase Contrast**  
Ensure colors remain distinguishable with accessibility settings enabled.

✅ **Use semantic naming**  
Name colors by purpose (e.g., "primary", "accent") not appearance (e.g., "blue").

---

## ✍️ Typography System

### iOS/iPadOS Dynamic Type Sizes

#### **Large (Default) Text Styles**

| Style | Weight | Size (pt) | Leading (pt) | Usage |
|-------|--------|-----------|--------------|-------|
| **Large Title** | Regular | 34 | 41 | Page titles, navigation bars |
| **Title 1** | Regular | 28 | 34 | Primary section headers |
| **Title 2** | Regular | 22 | 28 | Subsection headers |
| **Title 3** | Regular | 20 | 25 | Group headers |
| **Headline** | Semibold | 17 | 22 | Important headings |
| **Body** | Regular | 17 | 22 | Primary content text |
| **Callout** | Regular | 16 | 21 | Secondary content |
| **Subhead** | Regular | 15 | 20 | Labels and descriptions |
| **Footnote** | Regular | 13 | 18 | Captions and footnotes |
| **Caption 1** | Regular | 12 | 16 | Small labels |
| **Caption 2** | Regular | 11 | 13 | Tiny labels, timestamps |

### **Typography Best Practices:**

✅ **Use Dynamic Type**  
Support all text size categories for accessibility.

✅ **Apply Vibrancy**  
Use vibrancy effects for text on translucent backgrounds.

✅ **Maintain clear hierarchy**  
Use size and weight differences, not just color.

✅ **Use Semibold for emphasis**  
Prefer weight changes over color for highlighting.

✅ **Respect line height**  
Use specified leading values for optimal readability.

✅ **Test at extreme sizes**  
Ensure layouts work with largest accessibility text sizes.

---

## 📐 Layout & Spacing

### Layout Principles

#### **1. Safe Areas**
- Respect device safe areas (notches, home indicators, rounded corners)
- Content should never be obscured by hardware
- Use system-provided safe area insets
- Maintain consistent margins across all devices

#### **2. Grid System**
- Use **8pt or 16pt gutters** for consistent spacing
- Align elements to a strict grid
- Maintain visual rhythm through consistent spacing
- Create visual harmony through alignment

#### **3. Concentric Design**
- UI corners mirror device hardware curves
- Creates visual harmony between software and hardware
- Standard corner radius: **10-12pt** for buttons/cards
- Larger radius for prominent elements

#### **4. Adaptive Layouts**
- Layouts adapt to different screen sizes and orientations
- Content reflows gracefully
- Maintains usability across all devices
- Responsive to dynamic type changes

### **Spacing Specifications:**

| Purpose | Value | Usage |
|---------|-------|-------|
| **Minimum Touch Target** | 44x44pt | All interactive elements |
| **Tight Padding** | 8pt | Compact spacing |
| **Standard Padding** | 16pt | Default element spacing |
| **Loose Padding** | 24pt | Generous spacing |
| **Section Spacing** | 32pt+ | Between major sections |
| **Edge Margins** | 16-20pt | Screen edge to content |

---

## 🧩 Component Specifications

### Corner Radius

| Component | Radius | Notes |
|-----------|--------|-------|
| **Buttons** | 10-12pt | Standard rounded corners |
| **Cards** | 12-16pt | Larger for prominent cards |
| **Modals** | Device-matched | Follows device corner radius |
| **Input Fields** | 8-10pt | Slightly smaller than buttons |
| **Chips/Tags** | 50% (pill shape) | Fully rounded ends |

**Note:** Apple uses **continuous corner curves** (squircles), not simple radius. This creates a more natural, organic appearance.

### Elevation Levels

| Level | Component Type | Shadow Characteristics |
|-------|----------------|------------------------|
| **0** | Background | Flat, no shadow |
| **1** | Content | Slight material effect, no shadow |
| **2** | Controls | Elevated with subtle shadow |
| **3** | Cards | Soft shadow, 4-8pt blur |
| **4** | Modals/Popovers | Highest elevation, 8-16pt blur |

### Component Sizing

| Component | Height | Padding | Notes |
|-----------|--------|---------|-------|
| **Standard Button** | 44pt min | 16pt horizontal | Minimum touch target |
| **Large Button** | 50-56pt | 20pt horizontal | Prominent actions |
| **Text Field** | 44pt min | 12-16pt | Comfortable input area |
| **List Row** | 44pt min | 16pt horizontal | Standard list item |
| **Toolbar** | 44pt | 16pt | Navigation bar height |

---

## 🎯 Interaction States

### Button States

| State | Visual Change | Duration | Notes |
|-------|---------------|----------|-------|
| **Default** | Base appearance | - | Resting state |
| **Highlighted/Pressed** | Darken 10-20% | Instant | Immediate feedback |
| **Disabled** | 50% opacity or gray | - | Non-interactive |
| **Focused** | Blue outline | - | Keyboard/accessibility navigation |
| **Loading** | Spinner or progress | - | Processing state |

### Visual Feedback Principles

✅ **Immediate response to touch**  
Visual feedback should be instant (< 100ms).

✅ **Smooth transitions**  
Use 0.2-0.3s duration for state changes.

✅ **Clear state changes**  
Make it obvious when state has changed.

✅ **Haptic feedback**  
Use haptics for important interactions (iOS).

### Animation Timing

| Animation Type | Duration | Easing |
|----------------|----------|--------|
| **Button Press** | 0.1s | Linear |
| **State Change** | 0.2-0.3s | Ease-in-out |
| **Modal Present** | 0.3-0.4s | Ease-out |
| **Modal Dismiss** | 0.25-0.35s | Ease-in |
| **Micro-interaction** | 0.15-0.2s | Ease-out |

---

## ♿ Accessibility Considerations

### Color Contrast

| Text Type | Minimum Ratio | Recommended |
|-----------|---------------|-------------|
| **Normal Text** | 4.5:1 | 7:1 |
| **Large Text** (18pt+) | 3:1 | 4.5:1 |
| **UI Components** | 3:1 | 4.5:1 |

**Note:** System colors automatically adjust with "Increase Contrast" setting.

### Dynamic Type Support

✅ **Support all text size categories**  
From xSmall to xxxLarge accessibility sizes.

✅ **Layouts adapt to larger text**  
Elements reflow, don't truncate.

✅ **Maintain readability at all sizes**  
Test with largest accessibility settings.

✅ **Preserve hierarchy**  
Size relationships should remain clear.

### Reduce Transparency

✅ **Provide solid alternatives**  
When "Reduce Transparency" is enabled.

✅ **Ensure usability**  
App should work without translucent materials.

✅ **Maintain visual hierarchy**  
Use other techniques (borders, shadows) when materials are disabled.

### Additional Accessibility Features

- **VoiceOver support** - All interactive elements labeled
- **Haptic feedback** - Tactile confirmation of actions
- **Reduce Motion** - Respect animation preferences
- **Bold Text** - Support system-wide bold text setting

---

## 📱 Android Implementation Guide

### Translating Apple's Guidelines to Android

#### **1. Materials & Shadows**

**Apple Approach:**
- Liquid Glass with translucency
- Soft, diffused shadows
- Material-based depth

**Android Equivalent:**
```xml
<!-- Material Design 3 Surface with elevation -->
<com.google.android.material.card.MaterialCardView
    android:elevation="4dp"
    app:cardElevation="4dp"
    app:cardCornerRadius="12dp"
    android:backgroundTint="?attr/colorSurfaceVariant">
    
    <!-- Content -->
    
</com.google.android.material.card.MaterialCardView>
```

**Elevation Mapping:**
- Apple Level 0 → Android 0dp
- Apple Level 1 → Android 1-2dp
- Apple Level 2 → Android 2-4dp
- Apple Level 3 → Android 4-8dp
- Apple Level 4 → Android 8-16dp

#### **2. Button Styles**

**Apple → Android Material 3 Mapping:**

| Apple Style | Android Material 3 | Implementation |
|-------------|-------------------|----------------|
| **Filled** | Filled Button | `<Button style="@style/Widget.Material3.Button">` |
| **Tinted** | Filled Tonal Button | `<Button style="@style/Widget.Material3.Button.TonalButton">` |
| **Gray** | Outlined Button | `<Button style="@style/Widget.Material3.Button.OutlinedButton">` |
| **Plain** | Text Button | `<Button style="@style/Widget.Material3.Button.TextButton">` |

**Example:**
```xml
<!-- Primary Action (Filled) -->
<Button
    android:id="@+id/primaryButton"
    style="@style/Widget.Material3.Button"
    android:layout_width="wrap_content"
    android:layout_height="56dp"
    android:text="Continue"
    android:paddingHorizontal="24dp" />

<!-- Secondary Action (Tonal) -->
<Button
    android:id="@+id/secondaryButton"
    style="@style/Widget.Material3.Button.TonalButton"
    android:layout_width="wrap_content"
    android:layout_height="56dp"
    android:text="Cancel" />
```

#### **3. Color System**

**Create `colors.xml` with semantic naming:**

```xml
<resources>
    <!-- System Colors (Light Mode) -->
    <color name="system_red_light">#FF3B30</color>
    <color name="system_orange_light">#FF9500</color>
    <color name="system_yellow_light">#FFCC00</color>
    <color name="system_green_light">#34C759</color>
    <color name="system_blue_light">#007AFF</color>
    
    <!-- System Colors (Dark Mode) -->
    <color name="system_red_dark">#FF453A</color>
    <color name="system_orange_dark">#FF9F0A</color>
    <color name="system_yellow_dark">#FFD60A</color>
    <color name="system_green_dark">#30D158</color>
    <color name="system_blue_dark">#0A84FF</color>
    
    <!-- Gray Scale -->
    <color name="system_gray">#8E8E93</color>
    <color name="system_gray2_light">#AEAEB2</color>
    <color name="system_gray2_dark">#636366</color>
    <color name="system_gray3_light">#C7C7CC</color>
    <color name="system_gray3_dark">#48484A</color>
    
    <!-- Semantic Colors -->
    <color name="label_primary_light">#000000</color>
    <color name="label_primary_dark">#FFFFFF</color>
    <color name="label_secondary_light">#3C3C43</color>
    <color name="label_secondary_dark">#EBEBF5</color>
    
    <!-- Backgrounds -->
    <color name="background_primary_light">#FFFFFF</color>
    <color name="background_primary_dark">#000000</color>
    <color name="background_secondary_light">#F2F2F7</color>
    <color name="background_secondary_dark">#1C1C1E</color>
</resources>
```

**Use Material Theme:**
```xml
<!-- themes.xml -->
<style name="Theme.App" parent="Theme.Material3.DayNight">
    <item name="colorPrimary">@color/system_blue_light</item>
    <item name="colorOnPrimary">@android:color/white</item>
    <item name="colorSecondary">@color/system_green_light</item>
    <item name="colorSurface">@color/background_primary_light</item>
    <item name="colorOnSurface">@color/label_primary_light</item>
</style>
```

#### **4. Typography**

**Create `type.xml` or define in `themes.xml`:**

```xml
<resources>
    <!-- Large Title -->
    <style name="TextAppearance.App.LargeTitle" parent="TextAppearance.Material3.HeadlineLarge">
        <item name="android:textSize">34sp</item>
        <item name="android:lineHeight">41sp</item>
        <item name="android:fontFamily">@font/sf_pro_display</item>
    </style>
    
    <!-- Title 1 -->
    <style name="TextAppearance.App.Title1" parent="TextAppearance.Material3.HeadlineMedium">
        <item name="android:textSize">28sp</item>
        <item name="android:lineHeight">34sp</item>
    </style>
    
    <!-- Headline -->
    <style name="TextAppearance.App.Headline" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textSize">17sp</item>
        <item name="android:lineHeight">22sp</item>
        <item name="android:fontFamily">@font/sf_pro_text_semibold</item>
    </style>
    
    <!-- Body -->
    <style name="TextAppearance.App.Body" parent="TextAppearance.Material3.BodyLarge">
        <item name="android:textSize">17sp</item>
        <item name="android:lineHeight">22sp</item>
    </style>
</resources>
```

#### **5. Spacing & Layout**

**Create `dimens.xml`:**

```xml
<resources>
    <!-- Spacing -->
    <dimen name="spacing_tight">8dp</dimen>
    <dimen name="spacing_standard">16dp</dimen>
    <dimen name="spacing_loose">24dp</dimen>
    <dimen name="spacing_section">32dp</dimen>
    
    <!-- Touch Targets -->
    <dimen name="touch_target_min">44dp</dimen>
    <dimen name="button_height_standard">48dp</dimen>
    <dimen name="button_height_large">56dp</dimen>
    
    <!-- Corner Radius -->
    <dimen name="corner_radius_small">8dp</dimen>
    <dimen name="corner_radius_medium">12dp</dimen>
    <dimen name="corner_radius_large">16dp</dimen>
    
    <!-- Elevation -->
    <dimen name="elevation_card">4dp</dimen>
    <dimen name="elevation_modal">8dp</dimen>
</resources>
```

#### **6. State Layers (Press States)**

**Material 3 automatically handles state layers:**

```xml
<!-- Custom state layer color if needed -->
<style name="Widget.App.Button" parent="Widget.Material3.Button">
    <item name="android:stateListAnimator">@null</item>
    <item name="backgroundTint">@color/button_background_selector</item>
</style>
```

**Create `button_background_selector.xml`:**
```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true" android:color="@color/button_pressed" />
    <item android:state_enabled="false" android:color="@color/button_disabled" />
    <item android:color="@color/button_default" />
</selector>
```

#### **7. Grid System Implementation**

**Use ConstraintLayout with guidelines:**

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">
    
    <!-- 8dp grid spacing between elements -->
    <TextView
        android:id="@+id/title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginBottom="8dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />
    
    <TextView
        android:id="@+id/subtitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        app:layout_constraintTop_toBottomOf="@id/title"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## 📚 Official Resources

### Apple Developer Documentation

1. **Human Interface Guidelines (Main)**  
   https://developer.apple.com/design/human-interface-guidelines/

2. **Color Guidelines**  
   https://developer.apple.com/design/human-interface-guidelines/color

3. **Typography Guidelines**  
   https://developer.apple.com/design/human-interface-guidelines/typography

4. **Layout Guidelines**  
   https://developer.apple.com/design/human-interface-guidelines/layout

5. **Materials (Depth & Shadows)**  
   https://developer.apple.com/design/human-interface-guidelines/materials

6. **Buttons**  
   https://developer.apple.com/design/human-interface-guidelines/buttons

7. **Foundations**  
   https://developer.apple.com/design/human-interface-guidelines/foundations

8. **Components**  
   https://developer.apple.com/design/human-interface-guidelines/components

### Android Material Design Resources

1. **Material Design 3**  
   https://m3.material.io/

2. **Material Components for Android**  
   https://github.com/material-components/material-components-android

3. **Material Theme Builder**  
   https://material-foundation.github.io/material-theme-builder/

---

## 🎯 Key Takeaways

### Apple's Design Philosophy

1. ✅ **Simplicity** - Remove unnecessary visual clutter
2. ✅ **Materials over shadows** - Use translucency and blur instead of heavy drop shadows
3. ✅ **System colors** - Dynamic colors that adapt to context
4. ✅ **Clear hierarchy** - Use size, weight, and color to guide users
5. ✅ **Responsive feedback** - Every interaction has immediate visual response
6. ✅ **Accessibility first** - Design works for everyone
7. ✅ **Concentric design** - UI harmonizes with hardware

### For Android Implementation

- Use **Material Design 3** elevation system (similar to Apple's materials)
- Apply **8dp grid** spacing (equivalent to Apple's 8pt)
- Use **corner radius 12-16dp** for cards and buttons
- Implement **state layers** for button press states
- Use **semantic colors** from Material Theme
- Support **dynamic color** (Material You)
- Respect **accessibility settings** (large text, high contrast)

---

## 📝 Document Information

- **Created:** January 8, 2026
- **Research Source:** Apple Human Interface Guidelines
- **Last Updated:** January 8, 2026
- **Version:** 1.0
- **Maintained by:** Gridee Android Development Team

---

**Note:** This document is for reference and inspiration. When implementing designs for Android, always follow Material Design guidelines while drawing inspiration from Apple's clean, professional aesthetic.
