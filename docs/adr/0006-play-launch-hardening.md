# 0006. Play Launch Hardening: Entitlement, Consent and Secrets

We hardened the monetization and configuration paths so the app can be uploaded
to Google Play without shipping placeholder credentials or a bypassable
entitlement.

## Context

The pre-launch build had four issues that would each have been fatal in
production:

1. `BillingManager.launchPurchaseFlow` granted Pro locally whenever Play's
   `ProductDetails` were missing. Opening the paywall while offline unlocked
   everything for free.
2. Prices were hard-coded strings (`$19.99 / year`). Play localises prices per
   country and tax regime, so the displayed price would not have matched the
   charged price — a policy violation as well as a trust problem.
3. Ads were initialised unconditionally, with no TCF/GDPR consent signal, and
   `AD_ID` was undeclared.
4. The release build was signed with the debug key, the API base URL was the
   emulator loopback over cleartext, and the `/admin` API — which can flip any
   user's entitlement — required no authentication at all.

## Decision

1. **Entitlement is owned by Play, never by the app.** There is no local unlock
   path. When product details are unavailable the paywall reports it and offers
   a retry. `queryPurchasesAsync` runs on every `onResume`, and the cached Pro
   flag is revoked only when *both* the SUBS and INAPP queries succeed and
   return nothing — a network failure can never lock a paying user out.
2. **All prices come from `ProductDetails`.** The savings badge is computed from
   the annual price against 12× the monthly price, and trial length is read from
   the offer's zero-cost pricing phase. Changing a price in the Play Console
   changes the paywall with no app update.
3. **UMP gates every ad request.** `AdsConsentManager` runs Google's consent
   flow before `MobileAds.initialize`, no ad composes until `canRequestAds()`
   is true, and Settings exposes the privacy options form where required.
   `AD_ID` is declared in the manifest.
4. **No credential is committed.** Signing comes from a git-ignored
   `keystore.properties`; AdMob IDs, the API base URL and the Google web client
   ID are Gradle properties surfaced through `BuildConfig` and manifest
   placeholders, defaulting to Google's test IDs or to empty. An empty web
   client ID hides the Sign in with Google button rather than showing one that
   cannot work.
5. **The admin API is token gated.** `ADMIN_TOKEN` is compared in constant time;
   when unset the server generates a random token, prints it once and refuses
   requests without it. The embedded Console prompts for the token.
6. **Free versus Pro reminders.** ADR 0002 promised renewal alerts as a Pro
   feature but the app sent them to everyone. Free now receives exactly one
   alert the day before renewal; Pro unlocks the 3- and 7-day lead times, which
   is what the paywall advertises.

## Consequences

- A build with no configuration is safe by construction: test ad IDs, no live
  billing, no reachable admin API, no signing key.
- The paywall depends on the three products existing in the Play Console.
  Without them it renders its "billing unavailable" state rather than a broken
  price list — see `docs/google-play-release-checklist.md`.
- Revoking entitlement on resume means a refunded or expired subscription
  downgrades within one app launch, without a server round trip.
