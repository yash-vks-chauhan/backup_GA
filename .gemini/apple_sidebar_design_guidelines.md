# 📱 Apple Sidebar Design Guidelines
## Complete Reference for iOS, iPadOS & Mobile App Sidebars

> **Last Updated:** January 18, 2026  
> **Source:** Apple Human Interface Guidelines (HIG), Design Resources, and Best Practices

---

## Table of Contents

1. [Overview & Philosophy](#1-overview--philosophy)
2. [Placement & Position](#2-placement--position)
3. [Dimensions & Sizing](#3-dimensions--sizing)
4. [Margins & Safe Areas](#4-margins--safe-areas)
5. [Spacing System (8-Point Grid)](#5-spacing-system-8-point-grid)
6. [Row & Item Heights](#6-row--item-heights)
7. [Section Headers & Dividers](#7-section-headers--dividers)
8. [Content Organization & Hierarchy](#8-content-organization--hierarchy)
9. [Header/Profile Section](#9-headerprofile-section)
10. [Typography](#10-typography)
11. [Icons](#11-icons)
12. [Interactive States](#12-interactive-states)
13. [User Control & Visibility](#13-user-control--visibility)
14. [Animation & Transitions](#14-animation--transitions)
15. [Tab Bar vs Sidebar Decision](#15-tab-bar-vs-sidebar-decision)
16. [Visual Layout Structure](#16-visual-layout-structure)
17. [Complete Spacing Quick Reference](#17-complete-spacing-quick-reference)
18. [Key Don'ts](#18-key-donts)

---

## 1. Overview & Philosophy

### Core Design Principles

Apple's Human Interface Guidelines (HIG) emphasize the following principles for sidebar design:

| Principle | Description |
|-----------|-------------|
| **Clarity** | Content is the focus; UI elements should be clear and legible |
| **Deference** | UI helps people understand and interact with content without competing with it |
| **Depth** | Visual layers and realistic motion convey hierarchy and facilitate understanding |
| **Consistency** | Uniform UI elements, typography, color schemes, and interaction patterns |

### Purpose of Sidebars

Sidebars provide a **broad, flat view** of an app's information hierarchy, offering access to several peer content areas or modes simultaneously. They are crucial for:

- Managing cognitive load by housing non-core functionalities
- Providing navigation between app sections
- Quick access to user profiles and settings
- Housing less frequently accessed features

### "Liquid Glass" Effect (iOS 18+)

Sidebars in iOS, iPadOS, and macOS float above the content in the "Liquid Glass" layer. Content should extend beneath the sidebar to reinforce this floating appearance.

---

## 2. Placement & Position

### General Placement

| Aspect | Details |
|--------|---------|
| **Position** | Leading side of the view (typically **left** in LTR languages) |
| **Floating Effect** | Sidebars float above the main content |
| **Layer** | "Liquid Glass" layer - content extends beneath |
| **Z-Index behavior** | Content extends beneath the sidebar |

### Device-Specific Guidelines

#### iPhones (iOS)
⚠️ **Apple generally advises AGAINST using sidebars on iPhones** due to:
- Limited screen real estate
- Portrait orientation constraints
- Tab bars are recommended as a more space-efficient alternative

#### iPads (iPadOS)
✅ **Sidebars are well-suited for iPads:**

| Orientation | Behavior |
|-------------|----------|
| **Portrait** | Sidebar **collapsed by default**, acts as overlay when expanded |
| **Landscape** | Sidebar **expanded by default** |

When expanded in portrait mode, the sidebar:
- Functions as an overlay
- Measures **320 pixels wide**
- Spans the entire height of the screen
- Background color extends to status bar and home indicator areas

---

## 3. Dimensions & Sizing

### Sidebar Width

| Platform | Width |
|----------|-------|
| **iPad Portrait (overlay mode)** | **320 points** |
| **iPad Landscape** | **320 points** |
| **iPhone** | ❌ Not recommended |

### Sidebar Height

| Specification | Value |
|---------------|-------|
| **Height** | Full screen height |
| **Extension** | Extends to status bar & home indicator areas |
| **Safe Area** | Background color fills entire safe area |

### Size Variations (macOS/iPadOS)

Sidebar elements (row height, text, glyph size) are adaptable:

| Size Category | Description |
|---------------|-------------|
| **Small** | Compact view |
| **Medium** | Default for iPadOS |
| **Large** | Expanded/accessible view |

Users can customize sidebar icon size through General settings.

---

## 4. Margins & Safe Areas

### Standard Margins

| Context | Margin Width |
|---------|--------------|
| **iPhone app margins** | **16 points** (left & right) |
| **iPad sidebar margins** | **24 points** (left & right) |
| **Minimum element padding** | **8 points** (breathing room) |

### Safe Area Insets (iOS)

| Area | Inset Value |
|------|-------------|
| **Top safe area** | **44-47 points** (varies by device) |
| **Bottom safe area** | **34 points** (home indicator) |
| **Additional for Dynamic Island** | Device-specific |

### Important Notes on Safe Areas

- Safe areas define regions where content is not obscured by:
  - Rounded corners
  - Sensor housings (notch, Dynamic Island)
  - Home indicator
  - Navigation bars, tab bars, toolbars

- Use `safeAreaLayoutGuide` for Auto Layout
- Use `safeAreaInsets` for raw inset values

---

## 5. Spacing System (8-Point Grid)

Apple uses an **8-point grid system** for all spacing:

```
Base Unit:    8pt
            ┌───────────────────────────────┐
            │  8pt  │ 16pt │ 24pt │ 32pt   │
            │  1x   │  2x  │  3x  │  4x    │
            └───────────────────────────────┘
```

### Recommended Spacing Values

| Usage | Spacing |
|-------|---------|
| **Micro spacing** (icon to text) | **8 points** |
| **Small padding** (cell internal) | **12-16 points** |
| **Medium spacing** (between groups) | **16-24 points** |
| **Large spacing** (sections) | **32 points** |

### Whitespace Philosophy

Apple's design philosophy on whitespace (negative space):

- **Macro whitespace**: Separates large sections
- **Micro whitespace**: Spacing between smaller elements
- Use **8-point, 16-point, and 32-point increments** for consistency

---

## 6. Row & Item Heights

### Touch Targets

| Specification | Minimum Value |
|---------------|---------------|
| **Minimum touch target** | **44 × 44 points** |
| **Comfortable hit region** | **44 × 44 points** |

### Row Heights by Size Category

| Size Category | Approximate Height |
|---------------|--------------------|
| **Small** | ~40-44 points |
| **Medium** (default) | ~48-52 points |
| **Large** | ~56-60 points |

### Default Table Row Height

| Context | Height |
|---------|--------|
| **UITableView default** | **44 points** |
| **Estimated row height** | **44 points** |
| **Self-sizing cells** | Auto-calculated via Auto Layout |

---

## 7. Section Headers & Dividers

### Section Header Specifications

| Element | Specification |
|---------|---------------|
| **Text style** | Smaller, muted (secondary) color |
| **Text transform** | UPPERCASE (optional, common pattern) |
| **Top padding above header** | **24-32 points** |
| **Bottom padding below header** | **8-12 points** |
| **Left/Right padding** | Match sidebar content margins |

### Dividers/Separators

| Specification | Value |
|---------------|-------|
| **Line thickness** | **0.5 - 1 point** (hairline) |
| **Color** | Subtle gray (system `separator` color) |
| **Opacity** | Low (subtle, not dominant) |
| **Inset from leading edge** | Match icon alignment or full-width |
| **Visibility** | Visible but not dominant |

### Grouping Methods (in order of preference)

1. **Negative space** (whitespace between groups) - Most preferred
2. **Background shapes/colors**
3. **Separator lines**
4. **Section headers with labels**

### Visual Distinction

- Group related items visually using:
  - Negative space
  - Background shapes
  - Colors
  - Materials
  - Separator lines
- Ensure content and controls remain distinct

---

## 8. Content Organization & Hierarchy

### Maximum Hierarchy Levels

⚠️ **Sidebars should display no more than 2 levels of hierarchy**

For deeper information structures, use:
- Split view interface
- Disclosure controls to manage vertical space

### Content Placement Priority

```
┌──────────────────────────────────┐
│  HEADER (Optional)               │  ← User Profile / App Branding
│  - Avatar (circular/rounded)     │
│  - Username                      │
│  - Chevron icon (for navigation) │
├──────────────────────────────────┤
│  PRIMARY ITEMS                   │  ← Most important items at TOP
│  - Icon + Label                  │
│  - Icon + Label                  │
├──────────────────────────────────┤
│  GROUPED SECTIONS                │  ← Use separators between groups
│  Section Title (optional)        │
│  - Icon + Label                  │
│  - Icon + Label                  │
├──────────────────────────────────┤
│  SECONDARY/BOTTOM ITEMS          │  ← Settings, Help, Logout
│  (Avoid critical actions here)   │
└──────────────────────────────────┘
```

### Placement Rules

- **Most important items**: Near the top and leading side
- **Reading order**: Top to bottom, leading to trailing
- **Essential information**: Ample space, positioned prominently
- **Critical actions**: ⚠️ **NOT at the bottom** (users may obscure this area)

### Labels

- Use **succinct, descriptive labels**
- Omit unnecessary words to keep labels short
- Ensure clarity and conciseness

---

## 9. Header/Profile Section

### Core Elements

| Element | Description |
|---------|-------------|
| **User Avatar/Profile Picture** | Circular or rounded square, prominently displayed |
| **User Name** | Full name or username, larger font for importance |
| **Status/Identifier** (Optional) | Email, user ID, or brief description |
| **Chevron Icon** (Optional) | Right-pointing, indicates navigable profile |

### Layout Options

#### Option A: Simple & Centered
```
    ┌────────────┐
    │  [Avatar]  │
    │    Name    │
    │   @email   │
    └────────────┘
```
**Purpose:** Straightforward, emphasizes user identity

#### Option B: Left-Aligned with Details
```
┌────────────────────────────┐
│ [Avatar]  User Name    [>] │
│           user@email.com   │
└────────────────────────────┘
```
**Purpose:** Space-efficient, allows more information

#### Option C: Prominent with Background Accent
```
┌────────────────────────────┐
│  ░░░░░░░░░░░░░░░░░░░░░░░  │  ← Subtle background
│  [Avatar]  Display Name    │
│            Subtitle/Status │
└────────────────────────────┘
```
**Purpose:** Draws attention to profile, prominent entry point

#### Option D: Interactive Header
Similar to left-aligned, but entire header is tappable area.
**Purpose:** Clear call to action for profile engagement

### Avatar Specifications

| Specification | Value |
|---------------|-------|
| **Shape** | Circular or rounded square |
| **Size** | ~48-64 points diameter |
| **Alignment** | Left-aligned or centered |

---

## 10. Typography

### Font Family

Apple uses the **San Francisco (SF) font family** for optimal legibility:
- SF Pro (iOS, macOS)
- SF Compact (watchOS)

### Font Weights & Styles

| Element | Font Style |
|---------|------------|
| **Menu item labels** | SF Pro Regular/Medium, Body size |
| **Section headers** | SF Pro Semibold, Caption/Subhead size |
| **User name** | SF Pro Semibold, Body/Headline size |
| **Secondary text** | SF Pro Regular, Caption size, muted color |

### Font Sizes (Dynamic Type Base)

| Text Type | Size Range |
|-----------|------------|
| **Body text** | 17pt (default, scales with Dynamic Type) |
| **Headline** | 17-20pt |
| **Caption** | 12-13pt |
| **Subhead** | 15pt |

### Dynamic Type Support

**CRITICAL:** All text must support Dynamic Type

- Users can adjust text size for optimal readability
- Text should adapt to user-preferred settings
- Maintain relative hierarchy when sizes change

### Typography Guidelines

| Guideline | Description |
|-----------|-------------|
| **Legibility** | Ensure text remains legible at various scales |
| **Contrast** | Maximize contrast between text and background |
| **Hierarchy** | Use font weight, size, and color to show importance |
| **Consistency** | Minimize different typefaces within the app |
| **Clarity** | Typography should be clear, big, and readable |

---

## 11. Icons

### Icon Source

**Use SF Symbols** (Apple's icon library):
- Wide range of customizable symbols
- Scalable and adaptable
- Consistent with system design

### Icon Sizes

| Size Category | Icon Size |
|---------------|-----------|
| **Small sidebar** | ~18-20 points |
| **Medium sidebar** | ~22-24 points |
| **Large sidebar** | ~26-28 points |
| **Tab bar icons** | 25-28 points |

### Icon-to-Text Alignment

| Specification | Value |
|---------------|-------|
| **Gap between icon and text** | **8-12 points** |
| **Vertical alignment** | Center-aligned with text baseline |

### Icon Color

⚠️ **Do NOT hardcode sidebar icon colors**

- Icons should adopt the **system accent color**
- Users expect to see their chosen accent color
- Respect user preferences

### Custom Icons

If custom icon is needed:
- Create a **custom SF Symbol** rather than bitmap image
- Ensure scalability with Dynamic Type

---

## 12. Interactive States

### Selection/Highlight States

| State | Visual Treatment |
|-------|------------------|
| **Normal** | No background, standard text color |
| **Hover** (iPad with trackpad) | Subtle background highlight |
| **Selected/Active** | Accent-colored background pill/shape, bold text |
| **Pressed** | Dimmed/darker background |

### Selection Background Shape

| Specification | Value |
|---------------|-------|
| **Shape** | Rounded rectangle (pill shape) |
| **Corner radius** | ~8-10 points |
| **Horizontal padding** | 8-12 points |
| **Background color** | System accent color with ~15-20% opacity |

---

## 13. User Control & Visibility

### Show/Hide Controls

Users should be able to toggle sidebar via:
- **Edge swipe gesture** (from left edge)
- **Dedicated show/hide icon/button**

### Visibility Guidelines

| Guideline | Description |
|-----------|-------------|
| **Don't hide by default** | Maintain discoverability |
| **Adaptive behavior** | Auto-hide as window resizes (optional) |
| **Clear affordance** | Always provide show/hide control |

### Customization

Allow users to:
- Customize sidebar contents
- Reorder items
- Prioritize shortcuts to frequently used content

---

## 14. Animation & Transitions

### Open/Close Animation

| Property | Value |
|----------|-------|
| **Duration** | 250-300ms |
| **Easing curve** | ease-out or spring |

### Spring Physics

| Parameter | Value |
|-----------|-------|
| **Damping ratio** | 0.85-0.9 |
| **Stiffness** | Medium-high |

### Content Behavior

| Element | Animation |
|---------|-----------|
| **Main content** | Slide only (no scale) |
| **Overlay dimming** | 0% → 30-40% opacity |
| **Sidebar entrance** | Slide in from left edge |

### General Animation Principles

- Animations should feel **smooth and natural**
- Use **subtle micro-animations** for enhanced UX
- Avoid jarring or sudden transitions
- Maintain **60 FPS** for smooth performance

---

## 15. Tab Bar vs Sidebar Decision

### Use Tab Bar When...

| Criteria |
|----------|
| ≤5 primary sections |
| iPhone (portrait) |
| Frequently visited destinations |
| Need always-visible navigation |
| Space is limited |

### Use Sidebar When...

| Criteria |
|----------|
| Many sections/features |
| iPad/macOS |
| Complex navigation hierarchy |
| Non-core features (settings, profile) |
| App requires deep navigation |

### iOS 18 Adaptive Approach

New `TabView`/sidebar API allows:
- Tab view on **compact size classes** (iPhones)
- Sidebar on **regular size classes** (iPads)
- Single codebase supporting both platforms

---

## 16. Visual Layout Structure

### Complete Sidebar Anatomy

```
┌─────────────────────────────────────────────┐
│ ▓▓▓▓▓▓▓  STATUS BAR EXTENSION  ▓▓▓▓▓▓▓▓▓▓▓ │ ← Background extends here
├─────────────────────────────────────────────┤
│                                             │
│   ┌─────────────────────────────────────┐   │
│   │      HEADER SECTION                 │   │  ← 24pt margin
│   │  ┌──────┐                           │   │
│   │  │Avatar│  User Name            [>] │   │  ← Row height: 48-64pt
│   │  │ 48pt │  user@email.com           │   │     Avatar: 48pt diameter
│   │  └──────┘                           │   │
│   └─────────────────────────────────────┘   │
│                                             │
│   ─────────────────────────────── (divider) │  ← 0.5pt, subtle gray
│                                             │
│   24pt margin ↓                             │
│   ┌─────────────────────────────────────┐   │
│   │  PRIMARY MENU GROUP                 │   │
│   │  ┌──────────────────────────────┐   │   │
│   │  │ [Icon 24pt]  Menu Item 1     │   │   │  ← Row: 44-48pt height
│   │  │   8pt gap    ← text aligned  │   │   │     16pt horizontal padding
│   │  └──────────────────────────────┘   │   │     12pt vertical padding
│   │                                     │   │
│   │  ┌──────────────────────────────┐   │   │
│   │  │ [Icon]       Menu Item 2     │   │   │  ← 0-4pt gap between items
│   │  └──────────────────────────────┘   │   │
│   │                                     │   │
│   │  ┌──────────────────────────────┐   │   │
│   │  │ [Icon]       Menu Item 3     │   │   │
│   │  └──────────────────────────────┘   │   │
│   └─────────────────────────────────────┘   │
│                                             │
│   16-24pt gap between groups ↓              │
│   ┌─────────────────────────────────────┐   │
│   │  SECTION HEADER (optional)          │   │  ← Small caps, muted color
│   │  "Settings"                         │   │     8pt padding below
│   └─────────────────────────────────────┘   │
│                                             │
│   ┌─────────────────────────────────────┐   │
│   │  SECONDARY MENU GROUP               │   │
│   │  ┌──────────────────────────────┐   │   │
│   │  │ [Icon]       Settings        │   │   │
│   │  └──────────────────────────────┘   │   │
│   │  ┌──────────────────────────────┐   │   │
│   │  │ [Icon]       Help & Support  │   │   │
│   │  └──────────────────────────────┘   │   │
│   │  ┌──────────────────────────────┐   │   │
│   │  │ [Icon]       Logout          │   │   │  ← ⚠️ Not at very bottom!
│   │  └──────────────────────────────┘   │   │
│   └─────────────────────────────────────┘   │
│                                             │
│          (flexible space)                   │
│                                             │
├─────────────────────────────────────────────┤
│ ▓▓▓▓▓▓  HOME INDICATOR EXTENSION  ▓▓▓▓▓▓▓▓ │ ← 34pt safe area
└─────────────────────────────────────────────┘
```

### Row Item Anatomy

```
┌────────────────────────────────────────────────────────┐
│                                                        │
│    16pt    ┌──────┐   8pt   ┌──────────────┐   16pt   │
│  ←─────→   │ Icon │ ←────→  │ Label Text   │ ←─────→  │
│   margin   │ 24pt │   gap   │              │  margin  │
│            └──────┘         └──────────────┘          │
│                                                        │
│   ↑                     44-48pt                     ↑  │
│   └────────────────── row height ───────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

## 17. Complete Spacing Quick Reference

### All Measurements at a Glance

| Element | Spacing |
|---------|---------|
| **Sidebar width** | 320pt |
| **Content margin (left/right)** | 16-24pt |
| **Row height** | 44-56pt |
| **Row internal vertical padding** | 12-16pt |
| **Row internal horizontal padding** | 12-16pt |
| **Icon size** | 20-24pt |
| **Icon-to-text gap** | 8-12pt |
| **Gap between rows** | 0-4pt |
| **Gap between sections** | 16-32pt |
| **Section header top padding** | 24pt |
| **Section header bottom padding** | 8pt |
| **Divider thickness** | 0.5-1pt |
| **Divider inset** | 0 or 56pt (after icon) |
| **Touch target minimum** | 44×44pt |
| **Safe area top** | 44-47pt |
| **Safe area bottom** | 34pt |
| **Avatar size** | 48-64pt |
| **Selection pill corner radius** | 8-10pt |

### Converting Points to dp (Android)

For Android implementation, points (pt) roughly equal density-independent pixels (dp):

```
1 pt ≈ 1 dp
```

So all measurements above can be used directly in Android with `dp` units.

---

## 18. Key Don'ts

### ❌ Things to Avoid

| Don't | Reason |
|-------|--------|
| **Don't place critical actions at the bottom** | Users may obscure this area |
| **Don't hide the sidebar by default** | Reduces discoverability |
| **Don't use more than 2 levels of hierarchy** | Creates cognitive overload |
| **Don't hardcode sidebar icon colors** | Violates user accent color preferences |
| **Don't use sidebars on iPhone as primary navigation** | Limited screen space |
| **Don't clutter** | Keep it minimal and focused |
| **Don't use bitmap images for icons** | Use SF Symbols for scalability |
| **Don't ignore Dynamic Type** | Accessibility requirement |
| **Don't use small touch targets** | Minimum 44×44 points |
| **Don't hide show/hide controls** | Always provide clear affordance |

---

## Summary

Apple's sidebar design emphasizes:

1. ✅ **Floating glass appearance** over main content
2. ✅ **Clean, hierarchical organization** with max 2 levels
3. ✅ **SF Symbols** for consistent, scalable icons
4. ✅ **System accent colors** respecting user preferences
5. ✅ **Customizable content** that users can personalize
6. ✅ **Accessible touch targets** (44×44 points minimum)
7. ✅ **Dynamic Type support** for all text
8. ✅ **Edge-swipe gestures** for show/hide functionality
9. ✅ **8-point grid system** for consistent spacing
10. ✅ **320pt sidebar width** as standard

---

## References

- [Apple Human Interface Guidelines - Sidebars](https://developer.apple.com/design/human-interface-guidelines/sidebars)
- [Apple Human Interface Guidelines - Layout](https://developer.apple.com/design/human-interface-guidelines/layout)
- [Apple Human Interface Guidelines - Typography](https://developer.apple.com/design/human-interface-guidelines/typography)
- [SF Symbols](https://developer.apple.com/sf-symbols/)
- [iOS Design Resources](https://developer.apple.com/design/resources/)

---

*This document was compiled from Apple's official Human Interface Guidelines and design resources for use in the Gridee Android app development.*
