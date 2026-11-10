# SubFlow UI/UX Specification & Stitch Screen Reference

This document catalogs StitchMCP design references for SubFlow, with current Android behavior noted below. Generated examples are not runtime acceptance evidence or an exact inventory of the shipped UI. Subscription valuation/reporting contracts are owned by [`subflow-subscription-calculations`](../android/app/src/main/java/org/dpdns/alwaysup/subflow/domain/util/SPEC.md).

## Stitch Project Details
- **Project ID**: `12757208620384969978`
- **Design System Asset**: `assets/4656495248671492601` (SubFlow Apple HIG) & `assets/b312f2941f134d5faf2c3d341c326cfa` (Executive Desktop)
- **Primary Aesthetic**: Modern Apple HIG (Fluid cards, Squircle 20dp, 1dp hairline borders, Inter/SF Pro typography, Tabular numeric formatting).

## Generated Screens

### 1. Subscriptions Home (Main Dashboard)
- **Screen ID**: `665d2174e81a49d8b9f569e9d63deaeb`
- **Core Components**:
  - `TopAppBar`: Collapsible Large Title ("Subscriptions"), user profile avatar, and '+' add button.
  - `HeroCard`: Global normalized monthly/annual commitment, next valid today/future event, and explicit partial/unavailable coverage; not actual monthly payments. Amounts and inventory remain global while the list is filtered.
  - Compact All / Active / Paused / pending-trial count entries select a temporary subset and clear search/category. Subsequent filtering stays within the subset; a visible reset exits it. All includes paused records; ordinary home retains collapsed paused grouping. Paused-only and zero-match views retain search/exit controls. Unconvertible price-sort entries stay last with an explanation in either direction.
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
  - `SearchBar`: Filter presets across 100+ services; the bundled local directory is present on the first composition, before any asynchronous catalogue refresh.
  - `PresetGrid`: 3-column squircle grid of popular services (Netflix, Spotify, ChatGPT Plus, YouTube Premium, iCloud, Disney+, Amazon Prime, Copilot, Adobe CC, Notion, Dropbox, Gym).
  - `CustomOption`: Card to create custom subscription with custom color and icon.
  - `FormFields`: Grouped iOS table with Service Name, Category picker, Price & Currency, Segmented Cycle Pill (Weekly / Monthly / Yearly), First Bill Date, and Reminder Alert toggle.

### 4. Analytics & Insights
- **Screen ID**: `cb3b52559e3345dca13cc69f87480b37`
- **Core Components**:
  - `TimeSelector`: Segmented control ("Monthly" vs "Annual").
  - `HeroMetricCard`: Normalized selected-period cost, daily/per-active averages and full active count; known partial/unavailable coverage is explicit, rather than a fabricated month-over-month delta. Compact metric values must allow unavailable status text to wrap instead of clipping away its qualifier; numeric percentages can remain single-line.
  - `SpendCurveChart`: 6-month historical trajectory curve with active point tooltip.
  - `CategoryDonut`: Expense distribution ring chart with color legend.
  - `TopRankings`: Ranked expenditure list (#1 ChatGPT, #2 Netflix, #3 Spotify).
  - `ExportBanner`: Pro-tier CSV export, shared via FileProvider as a real file rather than pasted text.

> History reconstructs normalized costs from current records, not a payment ledger. The billing forecast covers six full calendar months including elapsed current-month dates. Coverage is evaluated independently for each comparison/series and the current reference line; affected comparisons pause while category amounts and independently complete content remain visible. Existing Pro gates remain. See the owning calculation spec for inclusion, conversion and coverage rules.

### 4b. Budget, CSV and home-screen widget
- Budget previews, meter and accepted-save/resume feedback suspend monetary conclusions when coverage is unknown, without blocking factual success. Settings explicitly labels original-currency editing when needed and preserves an open draft across rate/home-currency changes; semantics belong to the calculation spec.
- Analytics exports CSV with original source data, blank unavailable home amounts and appended `MonthlyConversionStatus`; JSON backups are unchanged. The exact export contract lives in the calculation spec.
- Responsive widget sizes are 180×60 and 180×110 dp (tall behavior from 100 dp height). Small prioritizes a concise monthly estimate and coverage; tall uses the remaining allocated height for as many valid upcoming events as fit and lets additional rows scroll. Each displayed event may show its app-private local service logo; remote catalogue/content URLs are never loaded. Numeric values retain 20sp; unavailable explanatory text uses compact 12sp. A usable partial amount carries a short localized converted-only caption that fits the minimum content width, rather than ellipsizing the full-screen coverage sentence. Unavailable amounts do not claim a displayed known portion. Empty inventory alone uses onboarding. Body opens home; event rows open detail. Rendering, launcher accessibility and clicks require device acceptance, not just compilation or a design mockup. Refresh is best effort, not guaranteed at background midnight.
- Acceptance evidence and remaining runtime checks: [subscriptions overview matrix](testing/subscriptions-overview.md).

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
