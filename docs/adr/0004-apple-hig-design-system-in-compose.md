# 0004. Apple HIG Visual Design Standards in Jetpack Compose

We decided to implement Apple Human Interface Guidelines (HIG) aesthetics across the Android Jetpack Compose client instead of standard default Material Design 3 components.

## Context
Most Android subscription and finance utility apps suffer from dated Material 2/3 interfaces that convey a generic, AI-generated or low-cost perception. In contrast, iOS design standards (exemplified by apps like Bobby and Copilot) trigger high consumer willingness-to-pay and organic word-of-mouth. Android users in T1 markets (US, UK, Germany, etc.) eagerly pay for polished, fluid, minimalist craftsmanship that treats their device screen with care.

## Decision
1. **Surfaces**: We reject fuzzy, heavy drop shadows. All cards utilize continuous squircle curves (20dp) with a 1dp hairline border (`outlineVariant` at 10-15% alpha) in both Light (`#F2F2F7` background, `#FFFFFF` cards) and Dark (`#000000` OLED background, `#1C1C1E` cards).
2. **Typography**: Large collapsible titles, tight letter spacing (-0.5sp on titles), and strict tabular figures (`FontFeature.TabularFigures`) for all monetary amounts and percentages.
3. **Modal Sheets**: Bottom sheets feature Cupertino grab handles, spring dampening, and haptic feedback on dismiss/confirm.
4. **Localization (i18n)**: Out-of-the-box support for 6 key languages (English, German, French, Spanish, Japanese, Simplified Chinese) with locale-aware currency placements.

## Consequences
Requires custom modifier wrappers and styled Compose components rather than relying on out-of-the-box M3 defaults, but yields a distinct, premium look and feel that converts free users to Pro subscribers at significantly higher rates.
