# Design System: SubFlow (Apple HIG for Android Compose)

## 1. Visual Theme & Atmosphere
A restrained, crystalline, Apple-grade financial interface built for high-trust subscription management and recurring expense visibility. 
The atmosphere is clinical, calm, and exquisitely crafted—rejecting noisy gamification, loud gradients, and AI clichés in favor of continuous squircles, tactile haptic feedback, subtle 1px hairline borders, and pure OLED dark contrast.
- **Density:** 5 (Daily App Balanced — generous touch ergonomics with high information clarity)
- **Variance:** 7 (Offset Asymmetric — dynamic hero financial cards paired with structured grouped rows)
- **Motion:** 6 (Fluid Spring Physics — iOS-style rubber band bounds, spring-damped bottom sheets, and micro-scale interactions)

## 2. Color Palette & Roles
### Light Mode (iOS Grouped Aesthetic)
- **Canvas Ground** (`#F2F2F7`) — Soft cool neutral background creating separation for floating cards
- **Pure Surface** (`#FFFFFF`) — Primary card container, bottom navigation bar, and input surfaces
- **Primary Ink** (`#1C1C1E`) — Deep charcoal display headers, titles, and high-emphasis numbers
- **Secondary Ink** (`#8E8E93`) — Muted captions, metadata, billing cycle tags, and timestamps
- **Hairline Border** (`rgba(60, 60, 67, 0.12)`) — 1px micro-border defining card edges without muddy shadows

### Dark Mode (Pitch OLED Luxury)
- **OLED Ground** (`#000000`) — True black canvas maximizing battery efficiency and dramatic visual contrast
- **Elevated Surface** (`#1C1C1E`) — Level 1 squircle cards and grouped table sections
- **Elevated High Surface** (`#2C2C2E`) — Level 2 interactive chips, search inputs, and modal sheets
- **Primary Ink Dark** (`#FFFFFF`) — High-contrast pure white typography
- **Secondary Ink Dark** (`#98989D`) — Muted secondary descriptions and status indicators
- **Hairline Border Dark** (`rgba(255, 255, 255, 0.10)`) — 1px crisp luminous boundary

### Semantic & Accent Colors
- **Apple Indigo (Brand Primary)** (`#5856D6`) — Primary actions, Pro badges, active tab indicators, and progress tracks
- **Financial Emerald** (`#34C759`) — Active subscriptions, positive cashflow savings, money saved
- **Renewal Amber** (`#FF9500`) — Subscriptions renewing in < 3 days, upcoming payment alerts
- **Alert Coral** (`#FF3B30`) — High-cost zombie alerts, cancellation confirmations, negative warnings

## 3. Typography Rules
- **Display Headlines:** Inter / SF Pro Display — `fontSize: 32px`, `fontWeight: 700`, `letterSpacing: -0.02em`, line height 38px. Dynamic collapsing Large Title behavior on scroll.
- **Section Headers:** `fontSize: 13px`, `fontWeight: 600`, uppercase, `letterSpacing: 0.05em`, rendered in Secondary Ink with 16px bottom padding (grouped table header standard).
- **Body & Labels:** `fontSize: 16px`, `fontWeight: 400`, line height 22px.
- **Financial Numerals:** Tabular Figures (`FontFeature.TabularFigures`) mandatory for all amounts, currencies, and countdowns. Prevents jitter during layout updates and currency toggles.
- **Banned:** Emojis in system headers, decorative serif fonts, neon gradient text, unspaced all-caps.

## 4. Component Stylings
- **Apple Squircle Cards:** 20dp smooth corner radius with 1px hairline border (`rgba(60,60,67,0.12)` in Light, `rgba(255,255,255,0.10)` in Dark). Zero dirty diffuse drop-shadows.
- **Subscription Row Item:** Left-aligned 44x44dp squircle service logo, service name + category caption in center, tabular price + renewal date right-aligned with chevron.
- **Cupertino Modal Bottom Sheet:** Rounded top corners (24dp), frosted glass backdrop blur, centered top grabber pill (36x5dp, `#C5C5C7`), spring-damped dismiss gesture.
- **Segmented Control:** iOS-style sliding pill toggle for billing cycles (Weekly / Monthly / Yearly) with tactile haptic tick.
- **Buttons:**
  - Primary: 52dp height, full-width or pill, Apple Indigo fill with pure white bold text, subtle 0.98x scale on press.
  - Ghost / Outline: 1px hairline border with surface background.
- **Loaders:** Sleek shimmer skeletons matching card shapes. No generic circular spinners.

## 5. Layout Principles
- **Safe Area Insets:** 16dp horizontal page margins, strict Edge-to-Edge with transparent status bar and navigation bar.
- **Hero Spend Overview:** Top prominent card displaying Total Monthly Commitment, projected Yearly Spend, and Active Count in an asymmetric 2-column layout.
- **Grouped Categories:** Subscriptions grouped logically (Entertainment, Productivity, Utilities, Health) with sticky section headers.
- **Touch Targets:** Minimum 48x48dp for all interactive icons and toggles.

## 6. Motion & Micro-Interactions
- **Spring Physics:** Kotlin Compose `spring(dampingRatio = 0.8f, stiffness = 300f)` for list additions, deletions, and modal reveals.
- **Haptic Feedback:** `HapticFeedbackType.LongPress` on reordering, `HapticFeedbackType.TextHandleMove` on cycle segmented switches and toggle activations.
- **Large Title Collapse:** Collapses smoothly from 32sp Large Title to 17sp centered TopAppBar title when scroll offset exceeds 56dp.

## 7. Anti-Patterns (Strictly Banned)
- NO purple/neon AI glows or cyber aesthetics
- NO blurry diffuse heavy drop shadows
- NO standard Material 3 oversized pill FABs or flat default cards
- NO raw unformatted numbers (every price must include proper locale symbol: `$14.99`, `€12,50`)
- NO blocking network spinners (all edits commit instantly to local database)
