# 0002. Hybrid Freemium with Quota Lockout and Play Billing

We decided to monetize SubFlow via a hybrid model: a 5-subscription free tier with non-intrusive AdMob banners, plus Google Play Billing tiers (Monthly $2.99, Annual $19.99 with 3-day trial, Lifetime $39.99) for Pro entitlement.

## Context
Google Play utility apps often suffer from either zero conversion (if too generous) or instant uninstall spikes (if hard-walled with no utility). Western users average 6-10 recurring subscriptions; setting the free threshold at 5 allows immediate real utility while guaranteeing that invested users hit the paywall naturally as they complete their full subscription inventory.

## Decision
1. **Free Quota**: Users can manage up to 5 active subscriptions for free with basic calculations.
2. **Pro Trigger**: Adding a 6th subscription triggers the Paywall. Pro unlocks unlimited entries, renewal push alerts (1/3/7 days ahead), multi-currency auto-conversion, advanced analytics, and removes ads.
3. **SKUs on Google Play Billing**:
   - `subflow_sub_monthly`: $2.99/month
   - `subflow_sub_annual`: $19.99/year (featured, 3-day free trial)
   - `subflow_inapp_lifetime`: $39.99 one-time non-consumable
4. **Ad Strategy**: Free users see a bottom adaptive banner. Zero interstitial popup interruptions to maintain Apple-grade luxury feel.

## Consequences
Revenue combines high-LTV annual subscriptions, steady monthly cashflow, non-recurring lifetime purchases from anti-subscription users, and residual ad CPM from casual users. Backend must verify Google Play purchase purchaseTokens via Google Play Developer API.

## Update — 2026-09-01: shipped prices differ from the ones decided here

The products created in the Play Console do not carry the prices proposed
above, and the paywall was observed rendering the live ones against production
Play on 2026-08-31 (recorded in `docs/google-play-release-checklist.md`):

| SKU | Decided here | Actually shipped |
| --- | --- | --- |
| `subflow_sub_monthly` | $2.99 / month | **$1.99 / month** |
| `subflow_sub_annual` | $19.99 / year, 3-day trial | **$9.99 / year, 7-day trial** |
| `subflow_inapp_lifetime` | $39.99 one-time | **$24.99 one-time** |

The app itself is unaffected — the paywall renders whatever
`queryProductDetailsAsync` returns and never hard-codes a price. The backend
was not: `skuMonthlyValueUSD` in `internal/service/billing_service.go` had been
populated from this ADR, so every MRR and ARR figure on the admin dashboard was
overstated (1.5x on monthly, 2x on annual) until it was corrected against the
observed prices. `TestRevenueMatchesPlayListPrices` now pins those constants.

Nothing about the decision above is retracted — only its price column is out of
date. If prices change again, the Play Console is the source of truth and the
Go constants must be updated to match; this ADR is a record of what was
intended, not of what users pay.
