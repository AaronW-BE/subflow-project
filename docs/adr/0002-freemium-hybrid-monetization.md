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
