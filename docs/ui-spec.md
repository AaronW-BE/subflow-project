# SubFlow UI/UX Specification & Stitch Screen Reference

This document catalogs the design system and screen models generated via StitchMCP for SubFlow, establishing the exact visual contract implemented in the native Compose client and Web Admin Console.

## Stitch Project Details
- **Project ID**: `12757208620384969978`
- **Design System Asset**: `assets/4656495248671492601` (SubFlow Apple HIG) & `assets/b312f2941f134d5faf2c3d341c326cfa` (Executive Desktop)
- **Primary Aesthetic**: Modern Apple HIG (Fluid cards, Squircle 20dp, 1dp hairline borders, Inter/SF Pro typography, Tabular numeric formatting).

## Generated Screens

### 1. Subscriptions Home (Main Dashboard)
- **Screen ID**: `665d2174e81a49d8b9f569e9d63deaeb`
- **Core Components**:
  - `TopAppBar`: Collapsible Large Title ("Subscriptions"), user profile avatar, and '+' add button.
  - `HeroCard`: Total monthly spend `$142.50` in bold tabular digits, amber badge ("2 days" to next renewal), and annual projection (`$1,710/yr`).
  - `FilterChipBar`: Horizontal category scroll ("All", "Streaming", "Work", "Utilities").
  - `SubscriptionGroupedList`: Grouped cards with 44dp squircle brand icons (Netflix, Spotify, ChatGPT Plus, iCloud) with cycle and renewal countdown.
  - `BottomNavBar`: Apple-style frosted blur navigation with active tab in Apple Indigo (`#5856D6`).

### 2. Pro Paywall & Conversion
- **Screen ID**: `b8ead0b524df4130aeca0b7d2f9a0bf3`
- **Core Components**:
  - `Header`: Close button ('X'), luminous `SUBFLOW PRO` badge, headline and one-line value proposition.
  - `FeatureCards`: Emerald checkmarks with a supporting line each - Unlimited Tracking, Pre-renewal Alerts (1/3/7 days), Full Analytics, Multi-currency Totals, Ad Removal.
  - `PricingTiers`: Annual (featured), Monthly, Lifetime, rendered as radio-style squircle cards.
  - `CTAButton`: Apple Indigo 52dp button, sticky above the legal footer so it never scrolls out of reach.
  - `LegalFooter`: "Restore Purchases", "Terms of Service", "Privacy Policy", plus a plan-specific billing disclosure.

> **Prices are never hard-coded.** Every figure comes from Google Play
> `ProductDetails`: `formattedPrice` for the headline price, the offer's
> zero-cost pricing phase for the trial length, and annual-vs-12x-monthly for
> the "SAVE N%" badge. The screen has three states - loading (shimmer),
> unavailable (retry), and already-Pro (manage subscription) - because the
> catalogue is a network resource, not a constant. See ADR 0006.

### 2b. Onboarding
- Three swipeable pages (spend clarity, renewal alerts, local-first privacy),
  skippable at any point, with an animated page indicator.
- Finishing the last page marks onboarding complete and opens the paywall -
  the moment a new user has the most context for what Pro buys.

### 3. Add Subscription & Preset Catalog
- **Screen ID**: `f2e1fb9bcd5f4dc186841268672aa2a7`
- **Core Components**:
  - `TopBar`: "Cancel", bold title "New Subscription", "Save" (Indigo).
  - `SearchBar`: Filter presets across 100+ services.
  - `PresetGrid`: 3-column squircle grid of popular services (Netflix, Spotify, ChatGPT Plus, YouTube Premium, iCloud, Disney+, Amazon Prime, Copilot, Adobe CC, Notion, Dropbox, Gym).
  - `CustomOption`: Card to create custom subscription with custom color and icon.
  - `FormFields`: Grouped iOS table with Service Name, Category picker, Price & Currency, Segmented Cycle Pill (Weekly / Monthly / Yearly), First Bill Date, and Reminder Alert toggle.

### 4. Analytics & Insights
- **Screen ID**: `cb3b52559e3345dca13cc69f87480b37`
- **Core Components**:
  - `TimeSelector`: Segmented control ("Monthly" vs "Annual").
  - `HeroMetricCard`: Average monthly spend, projected annual spend with month-over-month delta (+4.2%).
  - `SpendCurveChart`: 6-month historical trajectory curve with active point tooltip.
  - `CategoryDonut`: Expense distribution ring chart with color legend.
  - `TopRankings`: Ranked expenditure list (#1 ChatGPT, #2 Netflix, #3 Spotify).
  - `ExportBanner`: Pro-tier CSV export, shared via FileProvider as a real file rather than pasted text.

> The 6-month `SpendCurveChart` is computed from each subscription's own first
> bill date, not from placeholder factors, and the y-axis is anchored at zero so
> the shape reflects real proportions. Free users see the chart and the category
> breakdown blurred behind an unlock prompt.

### 5. Web Admin Console (Desktop Dashboard)
- **Screen ID**: `dd99d54b1eb44219aea413bbee43c257`
- **Core Components**:
  - `ExecutiveTopBar`: SubFlow logo, "Admin Console" badge, online system health indicator.
  - `Sidebar`: Navigation tabs (Overview & KPIs, Presets Catalog, Users & Devices, Analytics & MRR, Settings).
  - `KPIRow`: Total Registered Users (1,428), Active Subscriptions Tracked (9,842), Pro Conversion Rate (6.42%), Estimated MRR ($1,620) / ARR ($19,440).
  - `ChartsRow`: User growth trend curve & Top Tracked Services distribution.
  - `ActivityTable`: Recent sync events with device platform, active subscription count, Pro status, and instant action toggles.
  - `RevenueTab`: Purchase ledger fed by `POST /api/v1/billing/purchase`, with estimated MRR/ARR, lifetime sales and 30-day purchase count.
  - `TokenGate`: the console is unusable without the operator `ADMIN_TOKEN`; the admin API rejects unauthenticated requests outright.
