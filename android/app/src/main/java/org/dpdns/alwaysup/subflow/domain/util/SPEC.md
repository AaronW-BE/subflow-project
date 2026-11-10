---
id: subflow-subscription-calculations
type: module-design
status: draft
title: Subscription valuation and reporting boundaries
references: [subflow-localization-preferences]
tags: [currency, overview, analytics]
---

## Responsibility

Own subscription inventory, normalized valuation and per-result coverage so reports cannot silently count an unconverted original amount as home currency. This is a calculation boundary, not a payment ledger or a second subscription lifecycle. Language and home-currency preference selection belong to `subflow-localization-preferences`.

## Pure calculation contract

Calculations accept records and an explicit immutable `CalculationSnapshot` (home currency, local date, rates); they depend only on domain values and standard-library arithmetic/date operations, not Android, storage, network or live globals. Android UI, export and widget adapters capture inputs and render results; they do not substitute raw amounts on conversion failure.

- Inventory excludes deleted records: total = active + paused. Pending trials are an active subset, including kept and expired unresolved trials. Pending trials contribute zero to current commitment; paused/deleted records contribute nothing. Quotas still count actual active records, including trials, not converted records.
- Commitment is monthly normalization: weekly × 4.3333, monthly unchanged, quarterly ÷ 3, yearly ÷ 12; annual commitment is monthly × 12. These are normalized costs, not deductions made this month. Saved paused records can still have a comparable reference price.
- `RatesSnapshot.ratesPerUsd` means currency units per USD. `convertAmount` returns `Available` or a specific `ConversionFailure`; finite nonnegative same-currency and zero amounts succeed without rates. Other conversions require finite positive source/target rates and reject overflow/underflow. No missing-rate 1:1/raw-amount fallback exists.
- `CostEstimate` retains known amount, convertible positive count and missing record/currency/reason. An incomplete estimate displays its known partial only if some positive cost converted; otherwise its amount is unavailable, not zero. Empty/free/current-trial estimates may be complete zero. Coverage means calculable, not fresh market precision.
- Next events use only active, nondeleted records with valid dates today or later (trial end for pending trials), ordered by date/name/id. Original event amount/currency remains intact. Past or malformed dates never become today's event; huge valid dates need not fit an Int countdown.

## Reporting and budget edges

Each comparison or series decides coverage from its own contributions, not a single global flag. Analytics retains counts and partial/unavailable amounts, including every category's absolute estimate, but suspends affected proportions, rankings, concentration and treemap. Complete categories and independently complete series remain usable. Per-active average includes all active records in its denominator.

History reconstructs six months from current records and monthly normalization, including records whose first date is unknown; it is not payment history. The forecast instead steps billing cycles through six full calendar months beginning on this month's first day, including elapsed dates this month. Pending converting trials use post-trial price/cycle from trial end; nonconverting trials contribute no forecast charges. Invalid schedules/arithmetic or exhausted date stepping suspend the affected series. Missing FX outside the forecast horizon does not invalidate it. Forecast coverage and its six-month average are independent of current commitment; an unavailable current monthly reference line is omitted without hiding a complete forecast.

Budget progress/remaining/overrun requires complete before and after totals and a convertible budget, with finite period arithmetic. Add/edit and single/bulk resume feedback uses one captured baseline/snapshot and only actually accepted persisted changes, not requested prices or a post-write collector total. Unknown coverage preserves factual success and the configured budget but suspends monetary conclusions. Existing Pro/quota/undo/trial gates remain. A budget retains its currency so changing home currency does not reinterpret its numeric limit. The settings editor freezes value and edit currency on opening: use home currency when convertible, otherwise explicitly edit/save the original currency; later rates/home changes must not replace the open draft.

CSV captures one snapshot per export and excludes deleted records but retains paused records and original source fields. The exact header is `Name,Category,Amount,Currency,Cycle,MonthlyNormalized,MonthlyIn<homeCurrency>,FirstBillDate,NextRenewalDate,ReminderDaysBefore,Notes,MonthlyConversionStatus`. The appended status is `AVAILABLE` or `INVALID_AMOUNT`, `MISSING_SOURCE_RATE`, `MISSING_TARGET_RATE`, `INVALID_SOURCE_RATE`, `INVALID_TARGET_RATE`, `OVERFLOW`, `UNDERFLOW`. Failed home conversion leaves that monthly cell blank; original monthly normalization remains available independently of FX. There is no annual column. UTF-8 BOM, US decimal formatting and quoted name/category/notes escaping (including quotes/newlines) remain; JSON backup/import schema and export eligibility are unchanged.

Widget adapters share inventory, estimate and valid future-event rules. Small layouts prioritize monthly cost/coverage; tall layouts add at most two upcoming events, with the first event's stored local logo when available. Tall layouts do not show a dense inventory-statistics block. Only empty saved inventory is onboarding; all-paused is not empty. Body opens home via explicit activity intent; event rows retain explicit detail links and original-currency prices. Widget logo rendering accepts only values approved by CustomLogoStore.isStoredLogo and Glance's URI ImageProvider; remote/content URLs are never loaded.

## Snapshot publication and refresh boundary

Android rate adapters atomically publish immutable rates with revision, quote time and provider name/URL. Persistence stores rates and attribution together; priming reads one cache generation, is idempotent, and cannot overwrite a newer publication or older-quote guard. Legacy unknown attribution is not invented and cannot displace known live attribution. Failed refresh retains usable values; identical publications do not trigger loops. Keep the licensed offline table and its provenance: ECB reusable reference rates, official AED/SAR pegs and identified approximations, not redistributed provider quotes. Offline coverage remains valid but approximate; currencies absent from usable coverage fail explicitly.

Foreground date adapters support the application's minimum API 26 without newer java.time convenience calls; desktop JVM tests alone are not Android API-availability evidence. Foreground resume and date/time/timezone changes, plus zone-aware midnight while resumed, refresh local date inputs. Record/rate/home-currency/language changes request widget refresh; live publication does not depend on a mounted destination. Widget requests are conflated/serialized with strictly increasing refresh stamps and existing Glance reload behavior. Background six-hour/system refresh is best effort under launcher/OEM/Doze/force-stop limits, not a precise closed-app midnight guarantee. No new service, permission, database migration or notification-scheduling contract is introduced.
