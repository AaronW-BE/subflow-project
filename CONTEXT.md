# SubFlow Domain

SubFlow is an elegant, privacy-first subscription and recurring expense management platform that tracks recurring financial commitments and prevents unwanted charges.

## Language

### Core Entities
**Subscription**:
A single recurring financial commitment with an associated billing cycle, cost, currency, and renewal date.
_Avoid_: Membership, recurring payment, recurring charge, bill item

**Preset**:
A verified, pre-configured catalog template of a popular subscription service (such as Netflix, Spotify, or iCloud) containing brand identity, default billing cycles, and categories.
_Avoid_: Template, service icon, app preset

**Billing Cycle**:
The regular recurrence interval at which a subscription renews (Weekly, Monthly, Quarterly, Semi-Annually, Annually).
_Avoid_: Recurrence frequency, interval, period

**Renewal Alert**:
A scheduled push notification delivered prior to a subscription's renewal date to alert the user to review or cancel.
_Avoid_: Reminder, alarm, notice

### Monetization
**Free Tier**:
The default state allowing up to 5 active subscriptions with standard monthly totals and subtle banner display.
_Avoid_: Trial account, basic user

**Pro Entitlement**:
The verified purchase status granting unlimited subscriptions, multi-currency conversion, analytics, cloud backup, and ad removal.
_Avoid_: VIP, premium account, gold member

**Paywall**:
The in-app upsell interface presenting Pro tier value propositions, subscription terms, and Google Play Billing purchase triggers.
_Avoid_: Upgrade screen, pricing page, VIP dialog

### Architecture & Synchronization
**Local-First Sync**:
An offline-resilient architecture where all mutations write instantly to the on-device Room database and reconcile asynchronously with the Golang API upon connectivity.
_Avoid_: Remote-first, cloud-only database

**Admin Console**:
The operational web dashboard served directly from the Golang single binary via embedded assets for monitoring business KPIs and managing catalog presets.
_Avoid_: Backoffice, internal portal

### Visual Design (Apple HIG on Compose)
**Squircle Card**:
A continuous rounded container (20dp corner radius) with a 1dp hairline translucent border replacing heavy blurry drop shadows.
_Avoid_: Material elevated surface, standard box

**Tabular Numeral**:
Fixed-width typographic formatting for currency and figures preventing layout jitter during numeric changes.
_Avoid_: Variable font width, standard digits

**Large Title**:
A prominent collapsible top bar title that collapses smoothly to a centered header upon upward scrolling.
_Avoid_: Static action bar, standard top app bar
