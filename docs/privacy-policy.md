# SubFlow — Privacy Policy

**Last updated: 28 August 2026**

SubFlow ("the app") is a subscription tracker published by Bin Tech ("we", "us").
This policy explains what the app does and does not do with your information.

Contact for privacy questions: **zmtzwb@gmail.com**

---

## Short version

SubFlow is local-first. The subscriptions you enter — their names, amounts,
billing cycles and renewal dates — are stored **only on your device**. We do not
have a copy of them, and we cannot read them.

Two things leave your device: what the Google Mobile Ads SDK collects in order
to show ads to users on the free tier, and a daily request for exchange rates
that carries none of your data.

---

## What stays on your device

All of the following is written to a local database (`subflow_local.db`) and to
local preferences, and is never uploaded to us:

- Subscription names, amounts, currencies, billing cycles, categories, colours,
  notes, first-bill and renewal dates
- Your display currency, language, theme, haptics and reminder preferences
- Your Pro entitlement state

Android's own backup service may include this local data in your personal
Google account backup if you have device backup enabled. That backup belongs to
you and is governed by Google's terms, not ours. The app deliberately **excludes**
its authentication preferences from backup.

You can erase everything at any time: **Settings → 危险操作 → erase all data**.
You can also export your data to a JSON file you control, and re-import it.

---

## What leaves your device

### Advertising (free tier only)

The app displays banner ads using the **Google Mobile Ads SDK (AdMob)**. That SDK
collects and shares with Google:

| Data type | Purpose |
|---|---|
| Device or other identifiers (including the Android advertising ID) | Advertising, analytics, fraud prevention and security |
| App interactions | Advertising, analytics, fraud prevention and security |

This data is transmitted over an encrypted connection. It is not used to build a
profile of your finances — the ad SDK has no access to your subscription data.

Google's handling of this data is described at
<https://policies.google.com/technologies/partner-sites>.

**Users in the EEA, UK and Switzerland** are shown a consent form on first launch
(Google's User Messaging Platform). Ads are only requested after consent is
resolved. You can reopen that form at any time from **Settings → 广告隐私选项 /
Ad privacy options**.

**Purchasing Pro removes ads entirely**, and with them this data collection.

### Exchange rates

To convert between currencies the app fetches a published rate table from
**Exchange Rate API** (`open.er-api.com`), at most once a day and only when the
cached table has expired.

The request carries **no data about you**: no account, no advertising ID, no
device identifier, and nothing about the subscriptions you track. It is an
unauthenticated request for a public table that is identical for every user. As
with any network request, the operator of that service can see the IP address it
came from.

The rates are cached on your device, so the app works offline and does not
request them again until the provider publishes a new table. Their terms are at
<https://www.exchangerate-api.com/terms>.

### Purchases

Purchases are processed by **Google Play Billing**. We never see or receive your
payment card, bank details, or billing address — Google handles the transaction
and tells the app only whether an entitlement is active.

To keep your purchase anonymous, the app sends Play a randomly generated,
non-reversible install identifier that contains no personal information.

---

## What we do not do

- We do **not** collect your name, email address, phone number or contacts.
- We do **not** ask for or store bank credentials or account numbers. SubFlow
  is manual-entry and preset-based by design; it does not connect to your bank.
- We do **not** collect your location.
- We do **not** access your photos, files, calendar, microphone or camera.
- We do **not** sell your personal information.

---

## Notifications

If you allow notifications, the app schedules renewal reminders **locally on your
device** using Android's WorkManager. Nothing is sent to a server to produce them,
and no push service is involved.

---

## Children

SubFlow is intended for users aged 18 and over. It is not directed at children
and we do not knowingly collect data from them.

---

## Your rights

Because we hold no personal data about you on our servers, there is no account to
close and no server-side record for us to delete. Everything the app holds is on
your device and under your control.

To reset the advertising identifier used by AdMob, use
**Android Settings → Privacy → Ads**.

If you have a question or a request about your data, email **zmtzwb@gmail.com**
and we will respond within 30 days.

---

## Changes

If the app's data practices change — in particular if optional account sign-in or
cloud sync is enabled in a future version — this policy and the Google Play Data
Safety section will be updated **before** that version is released.

---

*SubFlow is not a bank, broker, lender, insurer or financial adviser, and nothing
in the app constitutes financial advice.*
