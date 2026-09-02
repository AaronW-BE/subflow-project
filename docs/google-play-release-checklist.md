# Google Play Release Checklist

Everything below is either already wired into the code or is a value that has to
be supplied before the first upload. Items marked **REQUIRED** block the release.

## 1. Signing — REQUIRED

The release build reads its signing config from `android/keystore.properties`,
which is git-ignored. Without it the build falls back to the debug key, which
Play rejects.

```bash
keytool -genkeypair -v -keystore android/subflow-release.jks -alias subflow -keyalg RSA -keysize 4096 -validity 10000
```

Then copy `android/keystore.properties.template` to `android/keystore.properties`
and fill in the four values. Back the `.jks` up somewhere durable — losing it
means never being able to update the app under the same package name (unless
Play App Signing is enabled, which is recommended).

## 2. Monetization identifiers — REQUIRED

These are injected as Gradle properties so no live identifier is ever committed.
Put them in `~/.gradle/gradle.properties` or pass them on the command line:

| Property | Purpose |
| --- | --- |
| `subflow.admobAppId` | AdMob app ID → `AndroidManifest` meta-data |
| `subflow.admobBannerUnitId` | Banner ad unit → `BuildConfig.ADMOB_BANNER_UNIT_ID` |
| `subflow.apiBaseUrl` | HTTPS sync API base URL |
| `subflow.googleWebClientId` | OAuth **web** client ID for Sign in with Google |

```bash
./gradlew bundleRelease \
  -Psubflow.admobAppId=ca-app-pub-XXXX~YYYY \
  -Psubflow.admobBannerUnitId=ca-app-pub-XXXX/ZZZZ \
  -Psubflow.apiBaseUrl=https://your-own-host.example/api/v1/ \
  -Psubflow.googleWebClientId=XXXX.apps.googleusercontent.com
```

Defaults are Google's official **test** AdMob IDs, so an unconfigured build
never serves (or is billed for) live ads. `googleWebClientId` defaults to empty,
which hides the Sign in with Google button entirely rather than showing a
button that cannot work.

`apiBaseUrl` has **no default at all**. Left unset, `BACKEND_ENABLED` is
false and every backend call short-circuits to its offline fallback without
opening a connection. Setting it re-enables sync — which transmits the
user's subscription records, so Data Safety must be updated in the same
change.

## 3. Play Console products — REQUIRED

Create these exactly, or the paywall will render its "billing unavailable"
state because `queryProductDetailsAsync` returns nothing:

| Product ID | Type | Notes |
| --- | --- | --- |
| `subflow_sub_monthly` | Subscription | Monthly base plan |
| `subflow_sub_annual` | Subscription | Annual base plan, attach a free-trial offer |
| `subflow_inapp_lifetime` | One-time product | Non-consumable |

Prices are **not** hard-coded anywhere in the app. The paywall renders
`ProductDetails.formattedPrice` from Play, derives the "save N%" badge by
comparing the annual price against 12× the monthly price, and reads the trial
length from the offer's zero-cost pricing phase. Change the price in the Play
Console and the app follows.

## 4. Data safety & policy

- **Advertising ID** — `com.google.android.gms.permission.AD_ID` is declared.
  Declare it in the Data Safety form as collected for advertising.
- **UMP consent** — `AdsConsentManager` runs Google's User Messaging Platform
  before any ad request. No ad loads until `canRequestAds()` is true, and
  Settings exposes the privacy options form when the TCF policy requires it.
- **Notifications** — `POST_NOTIFICATIONS` is requested at first launch; the
  worker no-ops when it is denied.
- **Backup** — `backup_rules.xml` / `data_extraction_rules.xml` are an explicit
  allowlist: the subscription database and user preferences only. The auth
  session and the cached Pro flag are left out, so a restored device
  re-verifies entitlement against Play instead of trusting a copied flag.
- **Cleartext** — blocked in release by `network_security_config.xml`; the
  emulator loopback exception only ever reaches the `.debug` build.
- **Terms and Privacy Policy** — both live, and referenced by the paywall,
  Settings and the Play listing:
  `https://subflow.alwaysup.dpdns.org/privacy.html` and `/terms.html`.
  These used to point at `subflow.app`, which is **not our domain** (it
  resolves to cloudflare-ipfs.com). Never reintroduce that host.

## 5. Build the artifact

```bash
cd android && ./gradlew bundleRelease
```

Output: `android/app/build/outputs/bundle/release/app-release.aab`.

The bundle keeps every translation in the base module
(`bundle { language { enableSplit = false } }`) so the in-app language picker
works without an on-demand split download.

## 6. Pre-launch verification

- Install the release build and confirm the paywall shows real localized prices
  from a Play internal-testing track — a licence tester account is enough.
- Buy, then cancel in the Play Store, then reopen the app: `onResume` re-queries
  purchases and the entitlement is revoked only when both product-type queries
  succeed and return nothing.
- Test "Restore purchases" on a second device with the same Google account.
- Check the app in dark mode and at the largest system font size.
- Trigger a renewal alert with Settings → Send a test alert.

## Backend

The Go binary serves the API and the embedded Admin Console.

```bash
cd backend/web && npm run build   # also syncs dist/ into internal/static/dist
cd .. && go build -o subflow-server ./cmd/server
ADMIN_TOKEN=<long-random-string> DB_PATH=/var/lib/subflow/subflow.db ./subflow-server
```

`ADMIN_TOKEN` gates every `/api/v1/admin/*` route. If it is unset the server
generates a random one and prints it at startup, so the admin API is never
open by default. The Console prompts for the token and keeps it in
`localStorage`.

`POST /api/v1/billing/purchase` records the purchase token the client reports
after a successful Play purchase, which is what feeds the Console's revenue tab.
Entitlement itself is granted on-device by Play Billing and never depends on
this endpoint being reachable.

---

## Console progress (session note, 2026-08-28)

Developer account **Bin Tech** (personal), account id `6521552469820616814`,
signed in as zmtzwb@gmail.com. Three unrelated apps exist; **SubFlow did not
exist yet**.

The 创建应用 / "Create app" form is filled but **not submitted**:

| Field | Value |
| --- | --- |
| App name | SubFlow |
| Package name | `com.subflow.app` — availability confirmed |
| Default language | English (US) — deliberately not the pre-filled zh-CN, because `values/strings.xml` is the English fallback and Play is not distributed in mainland China |
| App or game | App |
| Free or paid | Free |

Left unticked on purpose: the two 声明 declarations (Developer Program Policy
compliance, and the US export-law declaration). Those are the developer's own
legal attestations.

Once the app is created, the remaining Console work is: store listing in six
languages, the three product SKUs (with the free-trial offer on the annual base
plan), Data Safety, content rating, and a testing track.

**Still blocking the bundle upload:** the AAB is signed `CN=Android Debug`
because `android/keystore.properties` does not exist. Play rejects debug-signed
uploads. See section 1 above.

### Update — app created

The 创建应用 form was submitted. The app now exists:

- Developer id `6521552469820616814`
- **App id `4974930032639760512`**
- Console URL: `https://play.google.com/console/u/0/developers/6521552469820616814/app/4974930032639760512/app-dashboard`


### App content declarations — session of 2026-08-28

Completed and saved (still need submitting via 发布概览):

| Declaration | Answer given | Reasoning |
|---|---|---|
| 登录详细信息 (App access) | **否** — no restricted parts | The 是 branch demands credentials granting full access *including paid content*; SubFlow has no accounts at all, and reviewers explicitly will not purchase or take trials. This declaration targets credential-gated content, which we have none of. Pro is IAP-gated only. |
| 广告 (Ads) | **是** — contains ads | AdMob banner shown to free-tier users. |
| 政府应用 | **否** | |
| 健康类应用 | 无任何健康相关功能 | |
| 金融产品和服务 | 不提供任何金融产品和服务 | SubFlow only totals subscriptions the user already pays elsewhere. It is not a lender, wallet, exchange, broker, insurer, or advisor. |
| 目标受众群体 | **年满 18 周岁 only** | Finance tool with ads; keeps the app clear of the Families policy entirely. |
| 广告 ID | **是**, purposes: 分析 / 广告或营销 / 欺诈防范 | Google Mobile Ads SDK; `com.google.android.gms.permission.AD_ID` is declared in the manifest. |
| 数据安全 | **SUBMITTED** 2026-08-29, once the privacy URL was live | |

#### Data safety — exactly what was declared

Verified against the source first. The only SDK in the shipped build that
transmits anything is `play-services-ads`. There is **no** Crashlytics,
Firebase, Sentry, or any analytics SDK (checked `app/build.gradle.kts` and
`libs.versions.toml`).

- Collects/shares disclosable data: **是**
- Encrypted in transit: **是** (`network_security_config.xml`, cleartext off)
- Account creation: **应用不允许用户创建账号**; cannot sign in with an outside account
  either — release `GOOGLE_WEB_CLIENT_ID` is empty, so `AuthRepository` throws
  `SignInNotConfiguredException`.
- Data-deletion request mechanism: **否** (no server-side user data, no account)
- Data types, both *collected* and *shared*, both non-ephemeral and required:
  - 应用活动 → 应用互动
  - 设备 ID 或其他 ID
  - Purposes on each: 分析, 广告或营销, 欺诈防范、安全和法规遵从

**Why nothing else was declared.** The Room database is local-only, and local
storage is explicitly not "collection". The backend calls that exist in code
(`auth/guest`, `sync`, `billing/purchase`) all target `https://api.subflow.app/`,
which is **not deployed**, and `sync` additionally needs a bearer token that can
only come from a successful server login — so no user data can leave the device
through them in the build that would ship today.

> **This must be revisited the moment either of these changes:**
> 1. `subflow.googleWebClientId` gets set → the app then collects **name + email**.
> 2. `api.subflow.app` goes live → `sync` then transmits the user's subscription
>    list (names, amounts, renewal dates) and `billing/purchase` transmits
>    **purchase history**.
>
> Shipping either without updating Data Safety is a policy violation.

#### Hard blocker reached

数据安全 cannot be submitted — Play refuses with *"若要提交，请在'隐私权政策'页面上
提供指向您隐私权政策的链接"*. The 保存 button is disabled; the questionnaire is
saved as a draft instead. A publicly reachable privacy policy URL is now on the
critical path for: 隐私权政策 declaration, 数据安全, and the store listing.

### Store settings + listing — session of 2026-08-28

| Item | State |
|---|---|
| 内容分级 (Content rating) | **DONE.** IARC questionnaire submitted 2026-08-28 18:50. Result: ESRB *Everyone*, PEGI 3, USK all ages, IARC 3+, Brazil 14+; interactive element *In-app purchases*. Rating contact `zmtzwb@gmail.com`. **Note: accepting the IARC Terms of Use was a required gate and was ticked to complete this.** |
| 应用类别 | **DONE.** 应用 / 金融 (Finance). |
| 详细联系信息 | **DONE.** Email `zmtzwb@gmail.com`. Phone and website left blank — `subflow.app` is not live. |
| 商品详情 (en-US) | **DRAFT saved.** Name `SubFlow: Subscription Tracker` (29/30), short description 74/80, full description 2119/4000. |
| Store graphics | Icon + feature graphic **generated**, see `play-assets/`. Not yet uploaded. |
| Screenshots | **5 captured**, `play-assets/screenshots/`. 1080×2160 each — note 1080×2400 (the Pixel 8a default) is **rejected**, because Play caps the long side at 2× the short side. |

#### Claims verified before going into the public listing

Two would have been false and were corrected:

- Preset count: confirmed **34** entries in `defaultLocalPresets()`.
- "Live exchange rates" — **dropped**. `refreshExchangeRates()` calls `/rates` on
  the undeployed backend, so the shipped build always uses the built-in
  `fallbackRatesToUSD` table. The listing now says only that amounts are
  "converted into your home currency", and names the 10 supported currencies.

Free/Pro split as stated in the listing was checked against the code:
`FREE_TIER_LIMIT = 5`; `ReminderLead.ONE_DAY` free, `THREE_DAYS`/`SEVEN_DAYS`
Pro; two `ProGate`d sections in `AnalyticsScreen`.

### Store assets generated

`play-assets/icon-512.png` and `play-assets/feature-graphic-1024x500.png`
were produced by `scratchpad/icon_ship.py`, which is the single source of truth
for the mark: the same ring spec (`SPEC`, `START`, `GAP`) drives both the PNGs
and the three vector drawables it writes into `res/drawable/`, so the store
icon and the launcher icon cannot drift apart. Re-run it after any brand-colour
change.

The mark is a segmented cycle ring — one arc per spend category, in the app's
own semantic colours — around the SubFlow S. It replaced a plain S on a flat
gradient, which read as a font glyph rather than a designed mark.

While regenerating, a real bug was fixed: `ic_launcher_foreground.xml` had been
drawing its own squircle. The launcher already masks the foreground layer, so
that nested a squircle inside the system shape. The foreground now carries the
mark only. `play-assets/adaptive-icon-preview.png` shows the result under both
the circle and squircle masks at 72px and 48px.

**These cannot be uploaded by the agent** — Play Console's 添加资源 opens a native
OS file dialog that browser automation cannot drive. Upload manually.

### Privacy policy

Written from the verified data practices, in two forms:
- `docs/privacy-policy.md`
- `docs/privacy.html` — self-contained, responsive, light/dark, ready to host

Host `privacy.html` at a public URL (GitHub Pages is enough), then paste that URL
into 应用内容 → 隐私权政策. Doing so unblocks the 数据安全 submission, which is
currently stuck as a draft.

Check before publishing: the contact address (`zmtzwb@gmail.com`) and the
publisher name ("Bin Tech").

### Remaining critical path

1. Host the privacy policy → fill 隐私权政策 → submit 数据安全.
2. Upload icon, feature graphic, and 2–8 real phone screenshots.
3. Create the upload keystore (still parked — the AAB is signed `CN=Android Debug`).
4. Rebuild the AAB with real values for `subflow.admobBannerUnitId`,
   `subflow.googleWebClientId` (or keep sign-in disabled) and `subflow.apiBaseUrl`.
5. Create the three products (`subflow_sub_monthly`, `subflow_sub_annual`,
   `subflow_inapp_lifetime`) — the paywall has never been exercised against real
   Play products.
6. Closed test: **12 testers for 14 continuous days** (personal account rule)
   before production access can be requested.

### Screenshots

Captured on a Pixel 8a emulator (API 36) from the debug build, at 1080×2160.
**Play rejects 1080×2160's default sibling 1080×2400** — the long side may not
exceed twice the short side — so the emulator was resized with `wm size` before
capturing. SystemUI demo mode supplied the clean status bar
(`adb shell am broadcast -a com.android.systemui.demo …`).

| File | Screen |
| --- | --- |
| `01-dashboard.png` | Hero total, renewal chip, staggered list |
| `02-dashboard-dark.png` | Same in dark mode |
| `03-detail.png` | Countdown ring, billing breakdown, paid-to-date |
| `04-add.png` | Preset grid with live preview |
| `05-settings.png` | Preferences, reminder tiers, Pro gating |

Demo data was staggered deliberately: every subscription had previously fallen
on the same renewal day, so all five rows read "31 days left", which is the
clearest possible tell that a listing screenshot is fabricated.

**Analytics has no usable screenshot.** The whole tab is Pro-gated: a free user
sees two "Unlock with Pro" blocks and three small stat tiles, nothing else.
Setting `is_pro` directly in `subflow_auth_prefs.xml` does not work — the app
re-queries Play on launch and revokes the entitlement, which is the intended
behaviour and confirms the local Pro fallback really is gone. A real Play
product plus a licence-tester account is the only way to capture that tab.

> Worth a product decision before launch: Analytics is a primary tab that is
> almost entirely a paywall for free users. Letting the category breakdown
> through for free (and keeping only spending *history* behind Pro) would give
> the tab a reason to exist without giving away the upgrade.

## App setup complete — 2026-08-29

The privacy policy is hosted at **https://subflow.alwaysup.dpdns.org/privacy.html**
(verified live over HTTPS; its disclosures match the Data Safety answers
exactly — device/other IDs and app interactions, for advertising, analytics and
fraud prevention).

That unblocked the last two declarations:

- **隐私权政策** — URL saved.
- **数据安全** — the draft submitted unchanged. Published preview: *shared* and
  *collected* = App interactions + Device or other IDs; encrypted in transit;
  no data-deletion request mechanism.

应用内容 now reads **没有需要注意的声明**, and the dashboard's 完成应用设置
section is gone. Store listing is complete: new icon, feature graphic and all
five screenshots are uploaded, and the listing reports 可以送审.

### The single remaining blocker

发布概览 lists every change as staged but refuses submission:

> 如要将更改内容送审，请在应用信息中心内完成必要步骤

Nothing is left in app setup, so what it now wants is a **release on a track**,
and that needs an uploadable AAB. The build is still debug-signed:
`android/keystore.properties` does not exist, and `~/.gradle/gradle.properties`
carries no `subflow.*` values. So the critical path is unchanged and entirely
off-console:

1. Create the upload keystore (section 1) — needs a password the developer owns.
2. Rebuild with real `subflow.admobBannerUnitId` / `subflow.admobAppId`. The
   release default is still Google's **test** ad unit; shipping that serves test
   ads to real users.
3. Upload to internal testing, then run the closed test: **12 testers, 14
   continuous days**, before production access can be requested.

## Live AdMob wired + a domain problem found — 2026-08-29

### Monetization identifiers

Written to `~/.gradle/gradle.properties` (outside the repo, so they are never
committed). A backup of the previous file is at `gradle.properties.bak`.

```
subflow.admobAppId=ca-app-pub-5166419462519653~7430440986
subflow.admobBannerUnitId=ca-app-pub-5166419462519653/4120812016
```

Verified in the release artifact:

| Value | Result |
| --- | --- |
| `ADMOB_BANNER_UNIT_ID` | `ca-app-pub-5166419462519653/4120812016` (live) |
| manifest `ads.APPLICATION_ID` | `ca-app-pub-5166419462519653~7430440986` (live) |
| `BACKEND_ENABLED` | `false` |
| `GOOGLE_WEB_CLIENT_ID` | empty — sign-in stays hidden, matching Data Safety |

### subflow.app is not ours

`nslookup` settles it: `api.subflow.app` does not exist, and **`subflow.app`
resolves to cloudflare-ipfs.com** — it belongs to someone else. The app was
shipping three links into that domain:

- paywall + Settings → `https://subflow.app/privacy` and `/terms`
- Settings support → `mailto:support@subflow.app`

So the in-app "Privacy Policy" link would have sent users to a stranger's site
while the Play listing pointed somewhere else entirely, and support mail would
have gone nowhere. All three now point at the domain we control:

- `https://subflow.alwaysup.dpdns.org/privacy.html`
- `https://subflow.alwaysup.dpdns.org/terms.html`
- `mailto:zmtzwb@gmail.com`

### The backend default was a latent leak

`API_BASE_URL` defaulted to `https://api.subflow.app/api/v1/`. That host does
not resolve today, so nothing leaked — but the app posts a generated install id
there on **every launch** (`signInAsGuest`), and whoever owns `subflow.app`
could create that subdomain at any time and start receiving them.

The release build no longer has a public default. When `subflow.apiBaseUrl` is
unset, `BACKEND_ENABLED` is false and the five backend calls
(`auth/guest`, `presets`, `rates`, `sync`, `billing/purchase`) return their
existing offline fallbacks without opening a connection. This also makes the
published privacy policy literally true: the only thing leaving the device is
what the Ads SDK sends.

Set `subflow.apiBaseUrl` to re-enable sync — and update Data Safety at the same
time, because sync transmits the user's subscription records.

### Still outstanding

1. **Host `docs/terms.html`** next to `privacy.html`. It is written but not yet
   uploaded, so the paywall's Terms link 404s until it is.
2. **Upload keystore** — still absent, so the bundle is debug-signed and cannot
   be uploaded. This is the only thing blocking 送审.
3. Closed test: 12 testers × 14 days.

## Upload key created + first AAB — 2026-08-29

Generated at the developer's explicit instruction, after flagging that the key
is normally theirs to create.

| | |
| --- | --- |
| Keystore | `android/subflow-release.jks` (git-ignored via `*.jks`) |
| Config | `android/keystore.properties` (listed in `.gitignore`; permissions `-rw-r--r--`, see the correction below) |
| Alias | `subflow` |
| Algorithm | RSA 4096, SHA384withRSA, 10000 days |
| DN | `CN=Bin Tech, O=Bin Tech, C=CN` |
| Password | 28 chars, `secrets.choice` over `[A-Za-z0-9]` — store and key identical. **Given to the developer in chat; it is not recorded here.** |

Verified on the produced bundle:

```
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
  所有者: CN=Bin Tech, O=Bin Tech, C=CN
  SHA256: 8F:07:2C:C6:0C:7B:5C:37:D4:20:2A:FC:6F:EC:27:F5:73:1D:EC:C7:39:6F:D9:3B:D9:23:20:72:2F:BE:A8:6F
```

Previously this read `CN=Android Debug`, which Play rejects. Artifact:
`android/app/build/outputs/bundle/release/app-release.aab` (8.4 MB,
versionCode 1, versionName 1.0.0).

> **Back up `subflow-release.jks` and the password off this machine.** Both live
> only in the working tree. Enrol in **Play App Signing** at first
> upload — Google then holds the app signing key and a lost *upload* key can be
> reset by support. Without it, losing this file ends the ability to update
> the app forever.

Both hosted pages are live and were checked:
`privacy.html` and `terms.html` on `subflow.alwaysup.dpdns.org`.

### What is left

1. **Upload the AAB** to 内部测试. Play Console's upload control opens a native
   OS file dialog, which browser automation cannot drive — manual, like the
   graphics were.
2. Submit the staged changes for review from 发布概览 (unlocks once a release
   exists).
3. Closed test: 12 testers × 14 continuous days, then request production access.

## Paywall verified against real Play products — 2026-08-29

Tested with the **release** build (R8 on) on a Pixel 8a emulator. Sideloading
works for product queries because the signed-in account is on the licence-test
list, so `queryProductDetailsAsync` returns live data.

All three tiers render with Play's own prices:

| Tier | Shown |
| --- | --- |
| Annual | `$9.99 / year` · 7-DAY FREE TRIAL · `$0.83 / month` |
| Monthly | `$1.99 / month` |
| Lifetime | `$24.99 one-time` |

The `yearly` base-plan correction is confirmed end to end: the app now says
"/ year" against a base plan that really does bill yearly.

### Two concurrency bugs found in BillingManager

Both were real, both were found from logcat during this session, and neither is
reliably reproducible — which is exactly why they were worth fixing before
launch.

**1. Overlapping connection attempts.** `startConnection()` guarded only on
`billingClient.isReady`, which stays false for the whole handshake, so init,
`onResume` and the reconnect timer could all call it while one was in flight.
Play answered the extras with `DEVELOPER_ERROR (5)` and the old code treated
that as a genuine failure: it set `UNAVAILABLE` **and** `catalogueLoaded`, so
the paywall could show "billing unavailable" while a perfectly good connection
was still being established. Guarded with an `AtomicBoolean`; reconnects now
route through the same path. Measured: 3 errors per cold start before, **0**
after.

**2. Lost update on `_plans`.** The SUBS and INAPP queries are issued together
and Play delivers their callbacks on different threads (observed: tids 7730 and
7731). `_plans.value = _plans.value + newPlans` is a read-modify-write, so
whichever wrote last with a stale read dropped the other's tier entirely.
Replaced with `MutableStateFlow.update`. `productDetailsCache` became a
`ConcurrentHashMap` and the two query-done flags are now `@Volatile` — a stale
read there leaves the paywall on skeletons for ever.

### Paywall layout

The Lifetime tier was being reported as "missing". It was not: the data was
always correct. The plan list sat at the end of the scrolling column and the
third card fell just below the fold on a 1080x2160 screen, with no cue that
anything was there. The highest-margin option was effectively invisible.

Pinning the plans to the bottom bar fixed that but collapsed the page at 1.5x
font scale — the copy was crushed to a sliver. Final arrangement:

- Plans moved **above** the feature list, still inside the scroll. All three are
  visible on first paint at normal scale, and at large scale everything simply
  scrolls instead of being squeezed.
- Only the CTA and legal links are pinned; both stay short at any font size.
- A fade at the bottom of the scroll area, so content cut by the fold reads as
  "scroll for more" rather than as a clipping fault.
- The legal links became a `FlowRow`: as a plain `Row` at 1.5x, "Privacy Policy"
  wrapped one character per line down the screen edge. Pre-existing, surfaced by
  this work.

Verified at 1.0x and 1.5x font scale. `testDebugUnitTest` and
`lintVitalRelease` both pass.

### Rebuilt for upload

`versionCode` bumped **1 → 2** (Play refuses a re-upload on a versionCode
already used on a track). `versionName` deliberately stays `1.0.0` — nothing
has been released publicly, so this is still the 1.0.0 launch build, just a
corrected upload.

Fresh `app-release.aab` verified before handing it over:

| Check | Result |
| --- | --- |
| Signer | `CN=Bin Tech, O=Bin Tech, C=CN` (not the debug key) |
| versionCode / versionName | `2` / `1.0.0` |
| `ADMOB_BANNER_UNIT_ID` | `ca-app-pub-5166419462519653/4120812016` (live) |
| manifest AdMob app id | present in the bundle manifest |
| `BACKEND_ENABLED` | `false` |

Upload this one, then the 8 staged changes can be submitted from 发布概览.

## Console state — 2026-08-30

**versionCode 2 (1.0.0) is uploaded and live on two tracks.** Internal testing
shows 有效 / 最新版本 2 (1.0.0). A **封闭式测试 - Alpha** track now also exists
with the same build.

The 8 app-content changes are no longer pending — they were submitted, and the
overview reports 最后发布日期 2026年8月29日.

### Pending submission (3 items, NOT submitted)

| Change | Detail |
| --- | --- |
| 2 (1.0.0) | 开始全面发布 on 封闭式测试 - Alpha |
| 轨道级支持状况 | 恢复轨道 |
| 测试用户数量 | testers set to the `inner-tester` email list |

Separately **already under review**: Alpha country rollout (176 countries) and
取消与正式版轨道的同步.

### Why those 3 are deliberately being held

The dashboard states the production gate exactly:

> 招募至少 12 名测试人员参与您的封闭式测试
> **目前已有 0 名测试人员选择参与**
> 招募至少 12 名测试人员参与您的封闭式测试至少 14 天

The counter is **opted-in humans**, not addresses on the list. `inner-tester`
holds 6 addresses and 0 have opted in. Submitting now would start a 14-day
continuous clock that cannot be satisfied, so the clock is being started only
once enough testers exist. Aim for ~15, not exactly 12, because the run has to
stay at or above 12 for the whole period.

`inner-tester` currently contains: aaron202519940101, gg412610153,
lonelykingyang, mushroom65525, zmtzwb, zshengde02 (all @gmail.com).

### Blocked on

Real tester Google accounts from the developer — these cannot be invented.
Once supplied: add to `inner-tester` → submit the 3 changes → distribute the
opt-in link (its 复制链接 button writes to the OS clipboard, so the developer
copies it).

Closed-testing setup is otherwise 4 of 5 tasks complete; the only outstanding
one is 将此发布版本送交 Google 审核.

### Not blocking, worth doing during the 14 days

The store listing exists only in en-US. de / fr / es / ja / zh are empty, so
users in most of the 176 countries would see English.

## Play compliance: Android 16 target + Billing 9 — 2026-08-30

The Console raised two blocking requirements on the pending release:

1. New releases must target **Android 16 (API 36)** or higher.
2. New releases must use **Google Play Billing Library 8.0.0** or higher.

Both are now met. versionCode moved 2 -> 3 because 2 is already consumed on
both the internal and the closed (Alpha) track.

### Toolchain moved to reach API 36

| | before | after | why |
|---|---|---|---|
| AGP | 8.8.0 | 8.13.2 | 8.8 was only tested to compileSdk 35 |
| Kotlin | 2.0.21 | 2.2.21 | billing 9.1.0 ships Kotlin 2.2 metadata, which the 2.0 compiler cannot read |
| KSP | 2.0.21-1.0.28 | 2.2.21-2.0.5 | tracks Kotlin |
| Room | 2.6.1 | 2.8.4 | 2.6.1 predates KSP2 |
| compileSdk / targetSdk | 35 | 36 | the Play requirement; also satisfies Billing 9's "compileSdk 35+" |

Deliberately stayed on AGP **8.13.2** rather than moving to AGP 9. 8.13.2 is
the last 8.x, supports compileSdk 36 and Gradle 9.3.1 (already the wrapper),
and needs no source changes. AGP 9 is a separate migration and is not worth
running days before a first release.

Compose BOM stayed at 2024.12.01 and play-services-ads at 23.6.0: neither
blocks the requirement, and both are API-surface changes with no upside right
now.

### Android 16 behaviour changes — all already handled

- **Edge-to-edge is mandatory at targetSdk 36** (the opt-out is gone).
  `MainActivity` already calls `enableEdgeToEdge()`.
- **Predictive back** — `android:enableOnBackInvokedCallback="true"` was
  already in the manifest.
- **Large-screen orientation/resize restrictions are ignored** — the manifest
  sets no `screenOrientation` and no `resizeableActivity`, so nothing changes.
- **16 KB page size** — does not apply: the app ships no native code, and
  `play-services-ads-23.6.0.aar` contains no `.so`.

### Billing 7.1.1 -> 9.1.0

Direct migration (two majors). Source changes, all in `BillingManager.kt`:

- **`queryProductDetailsAsync` listener signature changed.** It is now
  `(BillingResult, QueryProductDetailsResult)` instead of
  `(BillingResult, List<ProductDetails>)`. The list is read from
  `queryResult.productDetailsList`.
- **Unfetched products are now reported.** Billing 8 added
  `getUnfetchedProductList()`, so an OK response now says *why* a product is
  missing instead of just omitting it. Logged in `logUnfetched()`. This
  replaces the guessy "INAPP query OK but returned no products" check that was
  added while hunting the missing Lifetime tier.
- **One-time products now honour Play's purchase-options model.**
  `oneTimePurchaseOfferDetailsList` is read first, falling back to the legacy
  singular `oneTimePurchaseOfferDetails`. The singular field is only populated
  for products predating purchase options, so reading it alone silently drops
  a product configured the modern way — which is what `subflow_inapp_lifetime`
  is. The offer's `offerToken` is now carried into `BillingFlowParams`, the
  same way a subscription offer's token is.
- **`enableAutoServiceReconnection()` added.** The library now owns reconnection
  after a dropped service, so `onBillingServiceDisconnected` no longer calls
  `startConnection()` — doing both would have the two retry loops fighting.
  The backoff retry is kept for a *failed handshake*, which automatic
  reconnection does not cover; without it a user who opens the paywall during
  a Play blip sits on "unavailable" until they navigate away and back.

Checked and needing no change: `enablePendingPurchases(PendingPurchasesParams)`
was already the parameterised form; `queryPurchasesAsync` already takes
`QueryPurchasesParams`; SUBS purchases already pass an offer token; no
`SkuDetails`, `queryPurchaseHistory`, or `ProrationMode` anywhere in the
source; `minSdk` 26 clears Billing's 23. Suspended subscriptions
(`includeSuspendedSubscriptions`, new in 8.1) are deliberately left off — the
default excludes them, and a suspended subscription must not grant entitlement,
which is what the existing revoke logic assumes.

### Verified

- `:app:testDebugUnitTest` — 20 tests, 0 failures.
- `:app:assembleRelease` — green, including `lintVitalRelease`.
- `:app:bundleRelease` — green.

Only pre-existing deprecation warnings remain (`fallbackToDestructiveMigration`,
`Locale(String)`, `LocalLifecycleOwner`); none are new and none block.

### Not done, worth knowing

- **The paywall has not been re-run against real Play products since the
  Billing 9 upgrade.** The one-time-product path changed the most, so the
  Lifetime tier is the thing to re-check on a device before this AAB is
  promoted.
- play-services-ads 23.6.0 -> 25.4.0 is available. Not required by Play today.

## Package name changed — 2026-08-31

**`com.subflow.app` -> `org.dpdns.alwaysup.subflow`.** This supersedes every
earlier mention of `com.subflow.app` in this document, including the package
row in the app-creation table and the keystore warning.

Reverse-DNS of `subflow.alwaysup.dpdns.org`, which is a domain we actually
control. `com.subflow.app` was reverse-DNS of `subflow.app`, which we do not
own and never did - the same discovery that forced the privacy/terms/support
links to move. A package name is not a claim of ownership the way a URL is,
but it was the last thing in the build still pointing at a stranger's domain.

### What changed in the repo

- `namespace` and `applicationId` in `android/app/build.gradle.kts`.
- All 30 Kotlin sources moved from `src/{main,test}/java/com/subflow/app/`
  to `src/{main,test}/java/org/dpdns/alwaysup/subflow/`, package declarations
  and imports rewritten.
- The five `-keep` rules in `proguard-rules.pro`.

Nothing else needed touching. `AndroidManifest.xml` refers to its components
relatively (`.MainActivity`, `.SubFlowApplication`) so they resolve against the
new namespace, and the FileProvider and startup authorities are built from
`${applicationId}`. No resource, XML config, or backend file contained the old
package.

The debug variant is now `org.dpdns.alwaysup.subflow.debug`.

### What this costs in the Play Console — the important part

**An applicationId is permanent once an app entry exists.** The existing entry
cannot be renamed, so `org.dpdns.alwaysup.subflow` needs a **new app created in
Play Console**, and the old `com.subflow.app` entry is dead. Nothing was ever
publicly released from it, so no user is affected - but the Console work done
on 2026-08-28/29 does not carry over. To redo on the new entry:

- Store listing (en-US), icon, feature graphic, all 5 screenshots
- Data Safety form
- IARC content rating questionnaire
- Ads declaration, target audience, privacy policy URL
- Country/region availability, pricing
- **All three products**: `subflow_sub_monthly`, `subflow_sub_annual` (with the
  corrected `yearly` base plan, *not* `annual` - see the billing-period bug
  above), `subflow_inapp_lifetime`
- License testing, and the `inner-tester` list
- Internal + closed testing tracks

`play-assets/` is unchanged and can be re-uploaded as-is.

**AdMob needs nothing.** An earlier draft of this section claimed a new AdMob
app and ad unit were required; that was wrong. It assumed the AdMob app was
linked to a Play store listing, which it cannot be - the app has never been
published, and a closed-testing app does not appear in AdMob's store search.
The entry was therefore added manually as "not published on a store yet", and
that kind of entry is not bound to a package name at all.

So App ID `ca-app-pub-5166419462519653~7430440986` and banner unit
`ca-app-pub-5166419462519653/4120812016` stay valid,
`~/.gradle/gradle.properties` is untouched, and the publisher ID
`pub-5166419462519653` is unchanged either way, so **`app-ads.txt` needs no
edit**. Link the AdMob app to the store listing once the app is public - that
link will then point at the new package.

The upload keystore (`subflow-release.jks`) is reusable - a signing key is not
tied to a package name.

### Verified

- `:app:testDebugUnitTest` — 20 tests, 0 failures.
- `:app:bundleRelease` — green.

A pre-rename copy of `app/src`, `build.gradle.kts` and `proguard-rules.pro` is
in the session scratchpad under `pkg-rename-backup/`. There is no git history
in this project, so that copy is the only way back.

### Still true from before

versionCode is 3 and versionName 1.0.0. A brand-new Console entry has no used
versionCodes, so 3 is simply the next number rather than a constraint; leaving
it there costs nothing.

### Decision confirmed + versionCode 4 — 2026-08-31

The vc3 build that still carried `com.subflow.app` was uploaded to the old
Console entry before the rename landed, so the Console rejected the renamed
bundle twice over:

> 已有版本使用了版本代码"3"。请尝试改用其他版本代码。
> 您的 APK 或 Android App Bundle 的软件包名称必须为"com.subflow.app"

Both errors are the same fact from two angles: the upload was going to the
**old** app entry, which is permanently bound to `com.subflow.app` and has now
consumed versionCodes 1-3.

**Decision: create a new Console app and keep `org.dpdns.alwaysup.subflow`.**
The alternative was reverting the rename to keep the existing entry's setup;
that was declined.

versionCode is **1** — set at the developer's instruction, superseding the 4
this section originally recorded. The new entry has no used versionCodes, so
this is genuinely its first upload; 1..4 were spent on the old
`com.subflow.app` entry and do not constrain this one, because Play only
requires versionCode to increase within a single app.

The rework list is in the section above and is unchanged. Worth restating one
thing that is *not* lost: the 12-testers / 14-continuous-days closed-testing
clock never started (0 testers had opted in), so moving to a new entry forfeits
no elapsed time.

## Console review of the new entry — 2026-08-31

| | |
| --- | --- |
| `org.dpdns.alwaysup.subflow` | app id **4976411190419117043**, 草稿 / 尚未送审 |
| `com.subflow.app` | app id **4974930032639760512**, 已删除 |

Release pending on 封闭式测试 - Alpha is `1 (1.0.0)`, 176 countries. 14 changes
are staged but not submitted.

### Correct — verified in the Console

- **`subflow_sub_annual`**: base plan `annual` = **每年，自动续订**, 174
  countries, 有效, with the `freetrial-7d` offer attached and active. The
  billing-period bug from the old entry was *not* repeated.
- **`subflow_sub_monthly`**: base plan `monthly` = 每月，自动续订, 174
  countries, 有效.
- **App content: all 10 declarations complete and 可以送审** — 数据安全,
  目标受众群体和内容, 健康类应用, 政府应用, 广告 ID, 广告, 内容分级,
  登录详细信息, 隐私权政策, 金融产品和服务.
- **Data Safety** reads 收集或分享了 2 类数据 / 数据在传输过程中会加密 /
  不支持数据删除操作 — consistent with the app, where the ads SDK is the only
  thing that transmits.
- **License testing is enabled**: `inner-tester` (6 users) is checked, response
  RESPOND_NORMALLY. On the old entry this list existed but was never ticked,
  which would have charged testers real money.
- **Privacy policy URL** is `https://subflow.alwaysup.dpdns.org/privacy` (200).
  The app links `/privacy.html`, which 307-redirects there. Both work.
- **App category 金融应用** — staged. Appropriate: the app's core value is
  personal-finance tracking, and the separate 金融产品和服务 declaration is
  what governs financial-services scrutiny, not the category.
- The paywall's SAVE% badge is computed by `computeAnnualSavings()` from the
  prices Play returns, not hard-coded, so it cannot disagree with what the user
  is charged.

### Blocking problems found

Only one, once the AdMob claim below is discounted.

1. **`subflow_inapp_lifetime` does not exist.** 一次性商品 is empty on the new
   entry — the product was never recreated. The Lifetime tier will simply be
   absent from the paywall. This is a Console gap, not a code one: the
   `oneTimePurchaseOfferDetailsList` fix is in place and correct, but there is
   nothing for it to fetch.
~~2. The AAB still carries the old AdMob app id.~~ **Retracted — this was
   wrong**, and wrong in a way this document had already corrected once (see
   "AdMob needs nothing" above). The AdMob entry was created manually as "not
   published on a store yet", and that kind of entry is not bound to a package
   name, so `ca-app-pub-5166419462519653~7430440986` and the banner unit stay
   valid under `org.dpdns.alwaysup.subflow`. `~/.gradle/gradle.properties`
   needs no edit and the bundle needs no rebuild on this account.

### Worth a look, not errors

- Products are for sale in **174** countries, the release targets **176** — two
  countries can install but not buy. Normally the countries Play cannot take
  payment in.
- 目标受众群体 is **18+**. Defensible next to a finance category and ads, but
  it narrows reach for a subscription ledger.
- `subflow_sub_annual`'s 税费和政策设置 showed no value in the page text where
  `subflow_sub_monthly` showed 数字应用销售服务. Possibly just a rendering
  difference — worth opening both to compare.

### Still open

- **0 testers opted in** (needs 12 for 14 continuous days).
- Closed testing 4 of 5: only 将此发布版本送交 Google 审核 remains.
- Nothing was submitted during this review.

### Lifetime product recreated — 2026-08-31

The one blocking gap from the review above is closed. `subflow_inapp_lifetime`
now exists on the new entry with one active purchase option.

| Field | Value |
| --- | --- |
| 商品 ID | `subflow_inapp_lifetime` (permanent, cannot be reused) |
| 名称 | `SubFlow Pro - Lifetime` |
| 说明 | One payment unlocks SubFlow Pro forever: unlimited subscriptions, renewal alerts, multi-currency totals, full spending analytics with CSV export, and no ads. No recurring billing. |
| 购买选项 ID | `lifetime` |
| 购买类型 | 购买 (not 租赁) |
| 商品税种 | 数字应用销售 |
| 价格 | USD 24.99, applied to every country and auto-converted by Play |
| 状态 | 启用 |

The description was written from the app's own paywall strings rather than
invented, so it cannot overclaim: `feat_unlimited` ("The free plan stops at
5"), `feat_push_alerts`, `feat_rates`, `feat_analytics` ("History, categories
and CSV export"), `feat_no_ads`.

No icon was uploaded — it is optional, and Play requires a unique image with no
text or branding, which `play-assets/` does not currently contain.

### Correction: the AdMob "blocker" was wrong

The review above listed a second blocker claiming the AAB carried a stale AdMob
app id. That was wrong, and wrong in a way this document had already corrected
once. The AdMob entry was created manually as "not published on a store yet",
and such an entry is not bound to a package name, so
`ca-app-pub-5166419462519653~7430440986` and the banner unit remain valid under
`org.dpdns.alwaysup.subflow`. No gradle property change, no rebuild.

## Play warning: deprecated edge-to-edge APIs — 2026-09-01

Play flagged `Window.setStatusBarColor`, `Window.setNavigationBarColor` and
`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`, naming three call sites. All three
were decoded from `app/build/outputs/mapping/release/mapping.txt` of the exact
uploaded bundle:

| Reported | Actual |
| --- | --- |
| `d.s.b` | `androidx.activity.EdgeToEdgeApi26.setUp()` — EdgeToEdge.kt:278-281 |
| `d.u.b` | `androidx.activity.EdgeToEdgeApi29.setUp()` — EdgeToEdge.kt:310-317 |
| `com.google.android.gms.ads.internal.util.a.r` | Google Ads SDK 23.6.0 |

**None is SubFlow code.** A grep over all Kotlin and XML sources finds no direct
use of any flagged API: only `enableEdgeToEdge()` at `MainActivity.kt:79`,
`WindowCompat.getInsetsController()` in `Theme.kt`, and the transparent
`android:statusBarColor` / `android:navigationBarColor` theme attributes.

### Upgrading AndroidX does not remove it

`javap -c` on `androidx.activity.EdgeToEdgeApi35` from activity **1.12.4**:

```
27: invokestatic  androidx/core/view/WindowCompat.setDecorFitsSystemWindows
70: invokevirtual android/view/Window.setStatusBarColor:(I)V
75: invokevirtual android/view/Window.setNavigationBarColor:(I)V
```

The newest AndroidX still calls both (with 0, a no-op on API 35), and
`EdgeToEdgeApi26/29` remain for older devices. Bumping 1.9.3 -> 1.12.4/1.14.0
would not change the warning at all.

### Not a blocker

Deprecated is not removed. targetSdk is 36, and on API 35+ all three are already
no-ops — the platform enforces edge-to-edge, ignores the bar colours, and treats
every cutout mode as ALWAYS. On API 26-34 they are still needed and still work.
Current behaviour is correct; the warning does not block publishing or review.

### Options deliberately not taken

- `play-services-ads` 23.6.0 -> 25.4.0 (latest on Google Maven). Might drop the
  ads-side call site — unverified — but the two AndroidX sites would remain, so
  the warning would persist. A two-major-version SDK jump right after submitting
  for review is risk without benefit.
- Dropping `enableEdgeToEdge()` for a hand-written window setup. themes.xml
  already supplies the transparent colours, so only
  `WindowCompat.setDecorFitsSystemWindows(window, false)` would be needed — but
  it loses SHORT_EDGES cutout handling on API 28-29 and requires a rebuild and
  re-upload, to silence a harmless warning.

**Decision: no change.**

## play-services-ads 23.6.0 -> 25.4.0 — 2026-09-01

Requested after the edge-to-edge warning above. Done, and it builds, but **it
does not silence that warning** — see "Did not fix the warning" below.

### Changes

| File | Change |
| --- | --- |
| `gradle/libs.versions.toml` | `admob = "23.6.0"` -> `"25.4.0"` |
| `gradle/libs.versions.toml` | `ump = "3.1.0"` -> `"4.0.0"` |
| `AppleComponents.kt` | `@Suppress("DEPRECATION")` + comment on the AdSize call |

UMP was bumped only to make the declaration honest: `play-services-ads-api:25.4.0`
depends on `user-messaging-platform:4.0.0`, so Gradle was already resolving
3.1.0 -> 4.0.0. `AdsConsentManager` compiles against 4.0.0 unchanged, with no
deprecation warnings.

`:app:compileDebugKotlin`, `:app:assembleRelease` and `:app:bundleRelease` all
succeed; R8 and lintVital pass.

### Did not fix the warning

Verified in the 25.4.0 bytecode, not assumed:

- `com.google.android.gms.ads.internal.util.zzx.zzh(Activity)` still calls the
  helper that writes `WindowManager.LayoutParams.layoutInDisplayCutoutMode`.
  This is the class Play reported (as `...internal.util.a.r`).
- 25.4.0 **adds** a second site: `com.google.android.gms.ads.internal.overlay.zzm`
  writes `layoutInDisplayCutoutMode = 1`, i.e. SHORT_EDGES.
- The shipped release DEX still contains `setStatusBarColor`,
  `setNavigationBarColor` and `layoutInDisplayCutoutMode`.

So the Play notice will reappear, possibly naming one more location. The two
AndroidX Activity sites are unaffected by this change either way.

### Side effects measured

- **No new permissions.** The merged release manifest lists
  `ACCESS_ADSERVICES_AD_ID` / `_ATTRIBUTION` / `_TOPICS`, but grepping the
  already-submitted ads-23.6.0 bundle shows all three were present there too.
  Nothing to re-declare in Data Safety.
- **AAB 8,669,149 -> 9,522,854 bytes** (+854 KB, ~10%). In 25.x
  `play-services-ads-lite` and `play-services-ads-base` are gone; the
  implementation moved into `play-services-ads` itself — 6077 classes in
  `com/google/android/gms/internal/ads/` versus 776 in the old lite artifact.
- `minSdkVersion` of the aar is 23; the app is 26. No conflict.

### Deferred deliberately

- `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize` is deprecated in
  25.x. The non-deprecated replacement family is `getLarge*AnchoredAdaptive...`,
  which returns a **taller** slot. The banner is the dashboard's `bottomBar`
  (`DashboardScreen.kt:187`), so adopting it would shrink the visible list —
  a layout decision, not part of an SDK upgrade. Suppressed and commented.
- The manifest's `com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION` and
  `OPTIMIZE_AD_LOADING` meta-data keys appear nowhere in the 25.4.0 classes,
  though they were present in 23.6.0's lite jar. Left in place: inert if
  unsupported, and the runtime-loaded dynamite module may still read them.
  **Confirmed on device 2026-09-01** — with `log.tag.Ads VERBOSE` the SDK
  prints `OPTIMIZE_INITIALIZATION is enabled` and `OPTIMIZE_AD_LOADING is
  enabled`, so they are still honoured; they live in the GMS module, not the
  static jar. Keeping them was the right call.

### Before shipping this

- **versionCode is still 1**, which is the bundle currently in review. Bump to
  2 for any re-upload.
- A two-major-version ads jump warrants a device smoke test of the dashboard
  banner and the UMP consent form before release.

## Device test of the ads 25.4.0 build — 2026-09-01

Device: OnePlus **PJX110**, **Android 16 / API 36** — the exact platform the
release targets.

### Which build, and why

The **debug** build was installed (`org.dpdns.alwaysup.subflow.debug`). It has
`applicationIdSuffix = ".debug"`, so it sits alongside the existing copy; no
uninstall, no data loss.

The release APK could not replace the installed one:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package
org.dpdns.alwaysup.subflow signatures do not match newer version
```

`dumpsys` shows `installerPackageName=com.android.vending` — the installed copy
came from the Play closed-test track and is signed with **Play's app signing
key**, not the local upload key. Testing a locally built release APK requires
uninstalling the Play copy first, which discards its data.

### Passed

- Launches, no crash. The `crash` logcat buffer stayed empty for the whole run.
- **Ads SDK 25.4.0 initialises on API 36.** `MobileAds.initialize` ran, GMS ad
  services bound (`ads.service.START`, `ads.service.CACHE`,
  `identifier.service.EVENT_ATTESTATION`), parental controls fetched. No
  `ClassNotFoundException` / `NoSuchMethodError` — the integration is sound.
- **UMP 4.0.0 works.** `requestConsentInfoUpdate` completed and wrote
  `IABTCF_CmpSdkID=300`, `IABTCF_gdprApplies=0`.
- **The Billing 9 `logUnfetched()` added during the PBL migration fires:**
  `E SubFlowBilling: Product not fetched: subflow_inapp_lifetime (inapp) status=3`
  (expected — the `.debug` id has no Play Console entry).
- Paywall degrades gracefully: billing-unavailable card + Retry, feature list
  intact, no crash.
- Edge-to-edge renders correctly on API 36 across Subscriptions / Analytics /
  Settings.
- **R8 kept what ads needs.** `AdView`, `MobileAds`, `AdRequest` and
  `UserMessagingPlatform` are all present in the shipped release DEX.

### Could not test: no route to Google's ad servers

```
I Ads: Ad failed to load : 0          (ERROR_CODE_INTERNAL_ERROR)
```

From the device:

| Host | Result |
| --- | --- |
| `googleads.g.doubleclick.net` | 100% packet loss |
| `pagead2.googlesyndication.com` | unknown host |
| `googleadservices.com` | 100% packet loss |
| `fundingchoicesmessages.google.com` | 100% packet loss |

Mainland China network. **Banner fill is untestable here without a VPN.** This
is a reachability problem, not a code or SDK problem — the SDK got far enough to
issue the request and report a transport failure.

### Two real findings

1. **`DEBUG_GEOGRAPHY_EEA` is inert.** The SDK said so directly:
   `I UserMessagingPlatform: Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("06B14AD344CB7DAF5D678842FFCE014E") to set this as a debug device.`
   Without a registered test-device hash the debug geography is ignored, and the
   app received `gdprApplies=0`. The comment in `AdsConsentManager.kt` — "Lets a
   developer replay the EEA form without a VPN" — is currently false.
2. **No `setTestDeviceIds`.** The ads SDK logged the same suggestion. Without it
   a release build would serve live ads to the tester.

Both need a working route to Google to fix *and* verify, so neither was changed
here. The hash is device-specific and belongs in `~/.gradle/gradle.properties`,
not in source.

## Second device round, proxy on — 2026-09-01

### Root cause of the earlier failure: proxy ad-blocking rules, not the network

Per-host probe from the device (`curl`, not `ping` — ICMP does not traverse the
tunnel, which made the first round's evidence weaker than it looked):

| Host | Before | After the rule change |
| --- | --- | --- |
| `www.google.com` | 200 | 200 |
| `googleads.g.doubleclick.net` | TLS `WRONG_VERSION_NUMBER` | **404 (reachable)** |
| `pagead2.googlesyndication.com` | TLS `WRONG_VERSION_NUMBER` | **404 (reachable)** |
| `fundingchoicesmessages.google.com` | 404 | **000 (now blocked)** |

Every ad domain failing while every non-ad Google domain succeeded, with
`WRONG_VERSION_NUMBER` (something answering non-TLS on 443), is the signature of
a REJECT rule, not of an unreachable network.

### `DEBUG_GEOGRAPHY_EEA` fix works

`app/build.gradle.kts` (debug only) now exposes `UMP_TEST_DEVICE_HASH` from the
`subflow.umpTestDeviceHash` gradle property, and `AdsConsentManager` passes it to
`ConsentDebugSettings.addTestDeviceHashedId()`. The hash is in
`~/.gradle/gradle.properties`, outside the repo, and is read by the debug build
type only so it can never reach a shipped bundle.

Evidence it took effect:

| Run | Result |
| --- | --- |
| Before the fix | `IABTCF_gdprApplies=0`, no form, SDK printed the "use addTestDeviceHashedId" hint |
| After the fix | `W SubFlowConsent: Consent form error 4: Web view timed out.` |

Error 4 means UMP decided a form **was required** and tried to display it — the
forced EEA geography is now being honoured. The form cannot finish only because
`fundingchoicesmessages.google.com` is currently blocked by the proxy.

### Remaining blocker

`fundingchoicesmessages.google.com` must also be allowed. With it blocked the app
behaves exactly as designed — `AdsConsentManager` fails closed, `canRequestAds`
stays false, and the banner composable returns before creating an `AdView`, so no
ad is requested at all.

### `setTestDeviceIds` — decided against

Not added. Debug already uses Google's official test ad units, so it gets test
ads without registering anything; and a device-specific id baked into a release
build would help exactly one tester while risking config leaking into the
shipped bundle. Testers are told not to tap ads instead.

## Third device round — consent flow verified end to end, 2026-09-01

### The `DEBUG_GEOGRAPHY_EEA` fix is confirmed working

The TCF consent dialog ("Welcome to Publisher Test Ads") appeared on screen and
was completed. The decisive evidence is the consent value UMP persisted:

| Run | `IABTCF_gdprApplies` | Form shown? |
| --- | --- | --- |
| Before the fix | **0** | no |
| After the fix, ad domains blocked | — | attempted, `error 4: Web view timed out` |
| **After the fix, domains allowed** | **1** | **yes, completed** |

`gdprApplies` flipping 0 -> 1 is the forced EEA geography finally being honoured.
`AdsConsentManager` logged nothing at all on this run — no errors anywhere in the
consent path.

"Do not consent" was chosen (the privacy-preserving option, and the stricter code
path). `canRequestAds` still became true afterwards, which is correct: consent was
gathered, the answer was simply negative, so the SDK may serve non-personalised
ads. The banner composable then created and attached its `AdView` — verified in
the live hierarchy:

```
com.google.android.gms.ads.AdView{f1a2f6f V.E...... 0,0-1080,0 alpha=1.0}
```

Width 1080, height 0 — attached and laid out, waiting on an ad that never
arrived. The whole app-side chain works.

### Still blocked: the request itself

```
I/Ads: Ad failed to load : 2      (ERROR_CODE_NETWORK_ERROR)
```

Sharper than the earlier code 0. Per-host probe explains it — the v2rayNG
(`com.v2ray.ang`) routing proxies the ad domains but not the rest of Google:

| Reachable (404) | Unreachable (000) |
| --- | --- |
| `googleads.g.doubleclick.net` | `google.com` / `www.google.com` |
| `pagead2.googlesyndication.com` | `www.googleapis.com` |
| `tpc.googlesyndication.com` | `play.googleapis.com` |
| `csi.gstatic.com`, `www.gstatic.com` | `android.clients.google.com` |
| `adservice.google.com` | `fundingchoicesmessages.google.com` |

An AdMob request needs `*.googleapis.com` and `*.google.com` too — the SDK
already warned `Not retrying to fetch app settings`, and GMS logged
`Exception while getting advertising Id info: java.io.IOException: Connection
failure`. The fix is v2rayNG global mode, or adding `domain:google.com` and
`domain:googleapis.com` to the proxy rules.

### Also learned

- **`pm clear` is denied by this ROM**: `SecurityException: PID does not have
  permission android.permission.CLEAR_APP_USER_DATA`. Earlier rounds that
  appeared to start from cleared data had not actually cleared anything. Use
  uninstall + reinstall for a genuinely fresh state on this device.
- ICMP does not traverse the tunnel, so `ping` is useless here. Use
  `curl -o /dev/null -w '%{http_code}'` from `adb shell`.

## Correction: the phone was never actually reaching Google, 2026-09-01

Earlier rounds read `googleads.g.doubleclick.net` returning **404** as "the ad
domain is reachable". That was wrong. Adding `%{remote_ip}` to the probe shows
where the 404 came from:

| From | Host | Result |
| --- | --- | --- |
| Phone | `googleads.g.doubleclick.net` | `ip=120.253.255.38` **404** — a China Mobile address |
| PC, via its SOCKS5 proxy | `googleads.g.doubleclick.net` | **301** — a real Google response |

`120.253.255.38` is a domestic address, not Google's ad server. The 404 was a
black-hole response, so the ad request never left the country. That is the real
reason every ad attempt failed, from the first round onward.

### Nothing on the phone is being proxied

| Host | Phone |
| --- | --- |
| `www.youtube.com` | timeout |
| `x.com` | timeout |
| `www.google.com` | timeout |
| `github.com` | 200 (works direct from China anyway) |

v2rayNG (`com.v2ray.ang`, pid alive) has `tun0` up at `10.10.14.1/30`, but no
blocked host is reachable through it. Turning off per-app proxy changed nothing,
because per-app proxy selects *which apps* use the tunnel — it does not affect
domain routing or whether the outbound connection works at all.

The PC's SOCKS5 listener on `127.0.0.1:10808` reaches both `www.google.com`
(200) and the ad host (301), so the node itself is fine. The problem is the
phone client's connection state or routing mode, not the server.

`adb reverse` cannot bridge the two: Android's global `http_proxy` setting needs
an HTTP proxy, and the PC exposes SOCKS5 only — ports 10809, 7890, 1081, 8889,
10810 and 7891 all refused.

### Lesson for future probes

`curl -w '%{http_code}'` alone is not enough behind a censoring network. Always
include `%{remote_ip}`; a plausible status code from a domestic address is a
black hole, not a success.

## Banner verified rendering — ads 25.4.0 test complete, 2026-09-01

Once the phone's tunnel actually carried traffic, the banner filled.

```
com.google.android.gms.ads.AdView{c64303 V.E...... 0,0-1080,168 alpha=1.0}
D/Ads: onDefaultPositionReceived {"x":0,"y":676,"width":360,"height":56}
D/Ads: onAdVisibilityChanged {"isVisible":"1"}
```

Height 168 px = **56 dp** at this device's 3x density — the anchored adaptive
banner size `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize` returns.
On screen it reads "Test Ad — Nice job! This is a 468x60 test ad." with the
AdMob mark, sitting directly above the bottom navigation with no overlap and
edge-to-edge intact.

### What this closes

| Item | Status |
| --- | --- |
| play-services-ads 25.4.0 on Android 16 / API 36 | works |
| UMP 4.0.0 consent flow | works |
| Forced EEA geography via `addTestDeviceHashedId` | works (`gdprApplies` 0 -> 1) |
| TCF consent form shown and completed | works |
| Privacy-options re-entry from Settings | works |
| `canRequestAds` gate releasing after a negative answer | works |
| Banner request, fill and layout | works |
| Crash buffer across the whole session | empty |

The only untested path remains the **release** build's ad rendering, because the
installed `org.dpdns.alwaysup.subflow` came from Play and is signed with Play's
key; replacing it needs an uninstall that discards its data. R8 was verified
statically instead — `AdView`, `MobileAds`, `AdRequest` and
`UserMessagingPlatform` all survive in the shipped release DEX.

### Reminder before shipping

versionCode is still **1**, which is the bundle in review. Bump to **2** for any
re-upload carrying the ads 25.4.0 change.

## Release build on device — 2026-09-01

The Play-installed copy was uninstalled, so the locally signed release APK could
finally be tested. One build fix was needed first.

### Build fix: `UMP_TEST_DEVICE_HASH` was debug-only

`AdsConsentManager` reads `BuildConfig.UMP_TEST_DEVICE_HASH`. The field had been
declared in the debug build type only, so the release variant would not compile —
Kotlin needs the symbol even inside an `if (BuildConfig.DEBUG)` branch. The
release block now declares it hard-coded empty rather than reading the gradle
property, so the device hash in `~/.gradle/gradle.properties` can never reach a
shipped bundle. Verified in the generated release BuildConfig:

```java
public static final String ADMOB_BANNER_UNIT_ID = "ca-app-pub-5166419462519653/4120812016";
public static final String UMP_TEST_DEVICE_HASH = "";
```

### The important result: the paywall is fully populated from production Play

| Tier | Shown |
| --- | --- |
| Annual | **7-DAY FREE TRIAL**, $0.83 / month, **$9.99 / year** |
| Monthly | **$1.99 / month** |
| Lifetime | **$24.99 one-time** |

This is the first end-to-end proof of several things that were only reasoned
about before:

- **Play Billing 9.1.0 works against the real Console products.**
- **The Lifetime one-time product resolves** — the `oneTimePurchaseOfferDetailsList`
  change was the riskiest part of the PBL 9 migration, and the product created on
  2026-08-31 (`subflow_inapp_lifetime`, USD 24.99) fetches and prices correctly.
- **The `freetrial-7d` offer on the annual base plan resolves** — badge and
  "Free for 7 days, then $9.99 / year" footer both render.
- `computeAnnualSavings()` is right: $9.99 / 12 = $0.83.
- **R8 did not break billing** in the minified build.

### Other release-only confirmations

- No crash. R8-minified code ran clean.
- **The live AdMob app id is read from the manifest**:
  `D/Ads: Publisher provided Google AdMob App ID in manifest: ca-app-pub-5166419462519653~7430440986`.
  This finally settles the retracted "stale AdMob id" blocker — the entry is valid
  under `org.dpdns.alwaysup.subflow`.
- UMP 4.0.0 initialises in the minified build. Its test-device hash is
  per-package (`E580CEE2...` here vs `06B14AD3...` for `.debug`), so the debug
  hash could not have applied even if it had leaked.
- **The fail-closed consent gate works.** `W SubFlowConsent: Consent info update
  failed: The server timed out.` -> `canRequestAds` false -> the banner composable
  never created an `AdView`, and no ad was requested. Exactly the intended
  behaviour.
- Logging survives R8: `SubFlowConsent`, `SubFlowBilling` and the failure strings
  are all present in the shipped DEX, so absent logs mean absent events.

### Not tested, and not worth chasing

Release-build **banner rendering**. The tunnel dropped again mid-test
(`www.google.com` 000, `googleads.g.doubleclick.net` resolving to
`120.253.255.102`, another domestic black-hole), so consent could not complete.

How much this matters, stated precisely — "the only difference is the ad unit
id" would be wrong. Six BuildConfig fields differ (`DEBUG`,
`ADMOB_BANNER_UNIT_ID`, `UMP_TEST_DEVICE_HASH`, `APPLICATION_ID`,
`NETWORK_LOGGING`, `BACKEND_ENABLED`), plus `isMinifyEnabled`.

What *is* shared is the drawing code: `AdMobAdaptiveBanner` in
`AppleComponents.kt` has no build-type branch at all. It creates the `AdView`,
calls `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize`, `setAdSize`
and `loadAd` from one source, and only `BuildConfig.ADMOB_BANNER_UNIT_ID`
flows in differently.

The one difference that could break release alone is **R8**, and its coverage
is partial rather than absent:

- Static: `AdView`, `MobileAds`, `AdRequest` and `UserMessagingPlatform` survive
  in the release DEX, backed by `-keep class com.google.android.gms.ads.** { *; }`.
- Runtime: the release build's ads SDK loaded and initialised — it read
  `ca-app-pub-5166419462519653~7430440986` from the manifest and UMP started. The
  SDK is functional after minification.
- Not covered: the final hop, `AdView` -> ad returned -> laid out at 56 dp.

`BuildConfig.DEBUG` does branch the consent path (`AdsConsentManager.kt:49`
forces EEA), but that sits upstream of rendering — in the release run consent
never completed, so rendering was never reached.

So the judgement is "the last hop is uncovered and cheap to skip", not "there is
no difference". A live unit on an unpublished app will normally no-fill anyway,
so a blank banner would not settle it either.

## Banner ad unit replaced — 2026-09-01

The configured banner unit was wrong. A new one was created in AdMob and its
setup screen supplied both ids:

```
App ID  SubFlow  ca-app-pub-5166419462519653~7430440986
banner           ca-app-pub-5166419462519653/6280065326   (standard ad unit)
```

The app id is unchanged, so no manifest edit. Only
`subflow.admobBannerUnitId` in `~/.gradle/gradle.properties` was updated (the
previous file was backed up alongside it).

| Check | Result |
| --- | --- |
| `~/.gradle/gradle.properties` | `ca-app-pub-5166419462519653/6280065326` |
| release BuildConfig | same |
| shipped release DEX (grep) | same |
| old unit `4120812016` still present? | **0 occurrences** |

### What this settles

The open question from earlier — whether `ADMOB_BANNER_UNIT_ID` was correct —
is closed on the configuration side. The previous value `4120812016` was never
verified against AdMob and turned out to be wrong. The replacement came
straight from AdMob's own integration instructions for a **standard banner**
unit under this app, so unit existence, type and app ownership are all
confirmed at the source rather than inferred.

Note this was never a runtime failure: no ad request had ever used the live
unit. Debug builds use Google's test unit, and the one release run ended at the
consent step (`Consent info update failed: The server timed out`), so the bad
id had never been exercised.

### Bidding vs standard

Standard was the right choice and should stay that way for launch. Partner
bidding only helps once third-party networks are actually configured, and each
one costs a signup, an adapter SDK dependency, extra APK size and its own policy
surface. With no partner accounts, no published app and no traffic, an
enabled-but-empty bidding pool behaves identically to standard.

It is reversible: partners can be added to this same ad unit later without
changing the unit id or `gradle.properties` — the only code change is adding the
adapter dependency. Revisit after a month or two of real eCPM and fill data.

### Still to do

- **versionCode is still 1.** Bump to 2 for any upload carrying the ads 25.4.0
  change and this ad unit.
- A brand-new ad unit can take a few hours before it serves, and an unpublished
  app will normally no-fill regardless. A blank banner in the near term is not
  evidence of a bug.

## Functional test of the release build — 2026-09-01

Run on the minified release APK carrying ads 25.4.0 and the new banner unit.
No network to Google (tunnel down), so this covered everything that does not
need it. **The crash buffer stayed empty for the entire run.**

| Flow | Result |
| --- | --- |
| Add subscription (Netflix from Popular services) | live preview auto-filled `$15.49 /mo`, Save enabled |
| Save -> dashboard | total `$15.49`, "1 active subscription", `Netflix · 30d` renewal chip |
| Free-tier meter | "Free plan — 1 of 5 tracked" bar appeared at 1/5 |
| Search + category filter row | rendered once a row existed |
| Monthly / Annual toggle | switches header to "PROJECTED ANNUAL SPEND" |
| Detail screen | 30 days left, Next renewal Oct 1 2026, First payment Sep 1 2026 |
| Analytics ("Insights") | Monthly commitment `$15.49`, Per day `$0.51`, Tracked 1 service |
| Pro gating on Analytics | history and category breakdown blurred behind "Unlock with Pro" |
| Export backup (JSON) | produced `subflow-backup-2026-09-01.json`, offered via the system share sheet |
| Delete subscription | confirmation dialog, then back to the empty state |

### Arithmetic spot-checks

- Per day `$0.51` — `15.49 x 12 / 365 = 0.509`.
- Yearly commitment `$185.88` — `15.49 x 12`.
- Paywall annual `$0.83 / month` — `9.99 / 12`.

All three derived figures are computed, not hard-coded, and all three are right.

### Copy worth keeping

The delete dialog says "This removes it from your tracking. It does not cancel
the subscription with the provider." Accurate and non-overclaiming — exactly the
distinction a subscription tracker has to make.

### Left as found

The Netflix row was added by this test and deleted afterwards; the Monthly/Annual
toggle was flipped and flipped back. Device state matches what it was before.
The exported JSON was **not** shared to any app.

### Not covered

Anything needing a route to Google: UMP consent, the banner, and Play purchase
flows. Billing product *fetch* was already verified earlier (all three tiers with
real prices), so only the purchase button itself is unexercised.

## Upload bundle built — versionCode 2, 2026-09-01

`./gradlew clean :app:bundleRelease` — clean build, no errors.

`android/app/build/outputs/bundle/release/app-release.aab`, 9,522,815 bytes.

### Verified in the artifact, not assumed

| Check | Value |
| --- | --- |
| `versionCode` | **2** |
| `versionName` | 1.0.0 |
| `package` | `org.dpdns.alwaysup.subflow` |
| `targetSdkVersion` | **36** (satisfies the Android 16 requirement) |
| `minSdkVersion` | 26 |
| AdMob app id (manifest) | `ca-app-pub-5166419462519653~7430440986` |
| AdMob banner unit (dex) | `ca-app-pub-5166419462519653/6280065326` |
| Signer SHA-256 | `8F:07:2C:C6:0C:7B:5C:37:D4:20:2A:FC:6F:EC:27:F5:73:1D:EC:C7:39:6F:D9:3B:D9:23:20:72:2F:BE:A8:6F` |

The signer fingerprint was compared against `subflow-release.jks` directly and is
an exact match, so the bundle carries the upload key rather than the debug key.

### Play Billing 8.0.0+ requirement

Resolved versions: `billing:9.1.0`, `billing-ktx:9.1.0`, `play-services-ads:25.4.0`,
`user-messaging-platform:4.0.0`. Confirmed in the bundle by the presence of
APIs that only exist in Billing 8+/9: `QueryProductDetailsResult`,
`UnfetchedProduct` (x4), `enableAutoServiceReconnection`,
`getOneTimePurchaseOfferDetailsList`.

### Nothing from the debug build leaked

Grepped the bundle's dex for each:

| String | Occurrences |
| --- | --- |
| `06B14AD344CB7DAF5D678842FFCE014E` (debug UMP hash) | 0 |
| `3940256099942544` (Google test AdMob publisher) | 0 |
| `4120812016` (the wrong banner unit) | 0 |
| `10.0.2.2` (emulator loopback backend) | 0 |

### versionName kept at 1.0.0

Nothing has been released publicly, so this is still the 1.0.0 launch — a
corrected build of it, not a new version. The comment in `build.gradle.kts` was
rewritten to say so; the old one still described versionCode 1 as "first upload".

### Before uploading

versionCode **1** is the bundle currently in review on 封闭式测试 - Alpha.
Uploading 2 supersedes it on the track. If the 1 review is still pending, check
in the Console how that release is handled rather than assuming it is replaced
silently.

## Keystore audit and two corrections — 2026-09-01

### It is password protected — verified, not assumed

```
$ keytool -list -keystore subflow-release.jks -storepass ""
keytool 错误: java.io.IOException: keystore password was incorrect
```

An empty password is rejected, so the store is genuinely protected.

| Property | Value |
| --- | --- |
| Alias | `subflow` |
| Entry type | `PrivateKeyEntry` |
| Subject / Issuer | `CN=Bin Tech, O=Bin Tech, C=CN` (self-signed) |
| Key | **RSA 4096**, SHA384withRSA |
| Created | 2026-08-29 |
| Expires | **2054-01-14** (~27 years) |
| SHA-256 | `8F:07:2C:C6:0C:7B:5C:37:D4:20:2A:FC:6F:EC:27:F5:73:1D:EC:C7:39:6F:D9:3B:D9:23:20:72:2F:BE:A8:6F` |
| Passwords | store and key are **identical**, 28 characters each |

The fingerprint matches the versionCode 2 bundle exactly. The password itself is
not recorded here and was not printed to the terminal during this audit.

### Correction 1 — the file is not mode 600

The table above previously said `keystore.properties` was mode 600. It is not:

```
-rw-r--r--  271   keystore.properties
-rw-r--r--  4298  subflow-release.jks
```

Both are **644**. On Windows the POSIX bits are largely cosmetic — NTFS ACLs are
what actually govern access — so this is not the exposure it would be on Linux,
but the document was stating something untrue and now does not.

### Correction 2 — `.gitignore` is currently inert

`.gitignore` lines 12-14 do list `keystore.properties`, `*.jks` and `*.keystore`.
But **this project is not a git repository**, so those lines protect nothing
today. They are a precaution that takes effect only if `git init` is ever run
here. Earlier entries in this document describing these files as "git-ignored"
overstated the protection.

### Standing risk

Losing `subflow-release.jks` or its password ends the ability to upload updates
under this upload key. Play App Signing means Google holds the *app signing* key
and support can reset a lost *upload* key, but that process is slow. Back the
`.jks` and the 28-character password up off this machine — a password manager or
encrypted volume, not the same directory on the same disk.

## In-app browser for web links — 2026-09-01

Opening 服务条款 / 隐私政策 raised the Android "open with?" chooser every time.
`SubFlowNavGraph.openUrl()` used a bare `ACTION_VIEW`, and our own pages on
`subflow.alwaysup.dpdns.org` are claimed by no app, so every installed browser
matched.

### Why this was nearly free

`androidx.browser:browser:1.8.0` was **already in the release graph**, pulled
transitively by `play-services-ads:25.4.0` / `user-messaging-platform:4.0.0`.
It is now declared explicitly in the version catalog so `openUrl` does not
depend on that staying true — same version, no size change.

### Not a blanket conversion

Two of the URLs going through `openUrl` are Play Store links —
`BillingManager.manageSubscriptionUrl()` (manage subscription) and
`playStoreUrl()` (rate app). Wrapping those in a tab would show the Play
*website* instead of handing off to the Play Store app, which is strictly worse.

`openUrl` now routes in three steps:

1. `play.google.com` -> plain `ACTION_VIEW`, on every API level.
2. API 30+: `ACTION_VIEW` with `FLAG_ACTIVITY_REQUIRE_NON_BROWSER`. That flag
   throws rather than quietly landing in a browser, which is what separates
   "Netflix owns netflix.com" from "only browsers can open this". The
   cancellation links in `SubscriptionDetailScreen` benefit here.
3. Otherwise a Custom Tab, falling back to `ACTION_VIEW` if the device has no
   Custom Tabs provider at all.

### Verified on device, release build

| Entry | Lands on |
| --- | --- |
| 服务条款 | `com.android.chrome/…customtabs.CustomTabActivity` — no chooser, dark theme followed, page rendered |
| 给 SubFlow 评分 | `com.android.vending/…finsky.activities.MainActivity` — the Play Store app, not a tab |

### Rebuilt bundle

`clean :app:bundleRelease` (the first attempt failed on a Windows file lock over
`intermediates/lint-cache`; `./gradlew --stop` cleared it).

`app-release.aab`, 9,523,439 bytes — versionCode **2**, versionName 1.0.0,
targetSdk 36, minSdk 26, package `org.dpdns.alwaysup.subflow`, signer SHA-256
`8F:07:2C:C6:…:A8:6F`.

Custom Tabs confirmed inside the bundle by strings that survive R8:
`android.support.customtabs.extra.SESSION`,
`android.support.customtabs.action.CustomTabsService`,
`androidx.browser.customtabs`. (`CustomTabsIntent` itself greps as 0 because R8
renames the class, and `FLAG_ACTIVITY_REQUIRE_NON_BROWSER` is an inlined int
constant — neither absence means anything.)

Ad ids and the debug-leakage checks are unchanged from the previous bundle: app
id `…~7430440986` in the manifest, banner `…/6280065326` in the dex, and zero
occurrences of the debug UMP hash, the Google test publisher, the old wrong
banner unit, or the emulator loopback URL.

## Tester feedback round 1 — 2026-09-02

A tester on a real install reported three things. All three were checked against
the source and then reproduced on the PJX110 (1080x2376, 480dpi, viewport
360x792dp) before anything was changed.

> Installed yours I think different countries could have different subscription
> and there's no option to tweak the price. In the currency selector the last one
> position KRW is cut and not showing correctly, also it's better to have the
> rest currencies

| # | Report | Verdict |
| --- | --- | --- |
| 1 | "no option to tweak the price" | Wording wrong, underlying bug real |
| 2 | KRW cut off | Confirmed, worse than described |
| 3 | Too few currencies | Confirmed, plus an app/server mismatch |

### 1. The preset silently overwrote the home currency

The price field was always editable — it is a `BasicTextField` with a decimal
keyboard and a currency chip beside it. What was broken sat one step earlier:
tapping a preset ran `currency = "USD"` unconditionally, undoing the
locale-derived home currency, and filled `defaultAmountUSD`, a US list price.

Reproduced: with the home currency set to CNY the live preview read `¥0.00`;
tapping Netflix turned it into **`$15.49`**.

The catalogue has no regional pricing — `defaultAmountUSD` is the only price
column — so the fix does not try to invent one. The currency is left alone, and
the price is filled only when the user is already in USD. A non-USD user now
gets the name, category, colour and cycle from the preset and an empty price
field, which is worse than a correct local price and better than a confident
wrong one. Proper regional pricing needs a schema change and is not done.

### 2. The picker sheets did not scroll

`ModalBottomSheet` containing a plain `Column`. A Column does not scroll, and
the sheet opens half-height by default, so the currency list showed **5 of 10**
entries — the last visible row's subtitle ended at y=2309 on a 2376px screen.

An earlier note in this session claimed KRW was completely unreachable. That was
wrong: dragging the sheet upward expands it and all ten fit. The list itself
still never scrolled, so the gesture only worked by being interpreted as a sheet
drag. At full height the content ended ~30px from the bottom edge, meaning any
additional currency — i.e. item 3 below — would have clipped it for real.

The two sheet implementations were byte-identical private copies, one per
screen, which is exactly why one bug shipped twice. They are now a single
`SubFlowPickerSheet` in `AppleComponents.kt`: a `LazyColumn`, opened with
`skipPartiallyExpanded = true`, with a search field that appears past 12 items
and a navigation-bar spacer.

### 3. Currency list widened from 10 to 40

The app had 10 and the server 11 — CHF was being served and could not be
selected. Both are now 40, generated from one table so they cannot drift.

This mattered more than it looks: `CurrencyConverter.convert()` returns the
amount **unchanged** for a currency missing from its rate table, so a currency
added to the picker without a matching rate would have silently counted ¥100 as
$100 in the dashboard total.

Also fixed while in there: `RateService` stamped `UpdatedAt: time.Now()` on a
compile-time constant map, so a table that had never moved reported itself as
updated seconds ago on every client and on the admin console.

### Verified on device after the fix

| Check | Result |
| --- | --- |
| Rows visible when the sheet opens | 5 → 11, full height |
| Scroll to entry 40 (BDT) | Reaches it, clear of the bottom edge |
| Search `krw` | 40 filtered to South Korean Won (KRW) alone |
| Netflix preset with KRW home currency | `₩0` stays `₩0`, name still filled |
| Spotify preset with USD (regression) | Still autofills `$11.99` |
| Zero-decimal formatting | `₩0`, not `₩0.00` |
| Server `/rates` | 40 entries, CHF present, honest `updated_at` |

Note on the harness: `adb shell input text` goes through the device's Pinyin
IME, which holds latin text as an uncommitted composition. Typing needs a
`KEYCODE_ENTER` to commit or the field stays empty while the keyboard shows
candidates — this is a test artifact, not app behaviour.

### Known-bad data, tracked separately

The 40 rates in this commit were written from memory. Checked afterwards against
live data, **25 of the 40 are off by more than 5%**, the worst being TRY at
−29.6%, COP +28.4% and INR −12.3%. They are no worse than the 10 they replace
and they are not good enough to ship. Wiring a real feed is the next task.

## Live exchange rates — 2026-09-02

The 40 rates shipped in the previous commit were written from memory. Measured
against live data, 25 of them were off by more than 5% and TRY by 29.6%. This
replaces them with a feed.

### Choosing a provider

Three free no-key options were tested by actually calling them, not by reading
about them.

| Service | Currencies | Covers our 40 | Verdict |
| --- | --- | --- | --- |
| **open.er-api.com** (Exchange Rate API) | 166 | **40 / 40** | Chosen |
| Frankfurter (ECB) | 30 | 29 / 40 | Missing TWD, AED, SAR, VND, PKR, BDT, NGN, EGP, UAH, CLP, COP |
| fxapi.app | — | — | Would not connect (`http=000`) |

Frankfurter's site advertises "84 central banks, 201 currencies"; its
`/v1/currencies` endpoint actually returns 30. It republishes the ECB reference
table. The claim and the endpoint disagree, and the endpoint is what matters.

Reachability here is via a local proxy, so `remote_ip` is always `127.0.0.1` and
proves nothing. Both services were judged on the JSON they returned — an earlier
round of this project mistook a censored black hole for a working host by
trusting a status code, and that lesson applies directly.

### Terms, read rather than assumed

The integration was challenged on two points and both were checked against the
pages themselves.

**Attribution is required.** `/docs/free`, under the heading "Attribution":
"We require attribution on the pages you're using these rates with the link
below: `<a href="https://www.exchangerate-api.com">Rates By Exchange Rate API</a>`".
The wording is now used verbatim, and is deliberately **not translated** into the
other five locales — the required text is that exact English phrase.

**No key, for this endpoint.** The provider runs three tiers: Open (no key,
attribution required), Free (key required, no attribution) and Pro. We use Open,
at `open.er-api.com` — a different host from the keyed tiers. Verified by calling
it with no key or auth header and getting 166 real rates back. Attribution and a
key are alternatives, so switching to the keyed Free tier would let the
attribution be dropped.

**Redistribution is not permitted.** The LICENSE section: "this license does not
permit re-distribution of our data ... not in any product or service that offers
programmatic or automatic access to exchange rate data", and the caching policy:
"caching is for customer end-use only".

That ruled out what had originally been built. Their snapshot had been baked into
the app's compile-time `fallbackRatesToUSD`, which ships inside the APK to every
user — distribution, not caching. The offline table is now assembled from sources
that permit reuse:

| Source | Count | Notes |
| --- | --- | --- |
| ECB euro reference rates | 29 | Published by the ECB for reuse |
| Official hard pegs | 2 | AED 3.6725, SAR 3.75 |
| Our own approximations | 9 | TWD VND CLP COP NGN EGP UAH PKR BDT |

The nine remain rough. They are corrected on the first successful fetch, so the
exposure is a first launch with no network.

### Why BACKEND_ENABLED was not simply switched on

The release build had `BACKEND_ENABLED = false`, so it never called `/rates` and
shipped the built-in table permanently. Turning that flag on would have been the
one-line fix and was rejected: it also gates `AuthRepository` sign-in, preset
fetching and **cloud sync**, so it would have started uploading the user's
subscription list — the thing ADR 0001 exists to prevent — and would have
required deploying and maintaining a server.

Rates got their own path instead: `ExchangeRateApi` + `ExchangeRateRepository`
call the provider directly, independent of that flag. Each install is then the
provider's own end user rather than a client of a server re-serving their data,
which also settles the redistribution question for the app.

The server keeps its own refresher for the admin console, with the same
validation and a SQLite-cached last-good quote.

### Rejecting bad data, on both sides

A fetch is only allowed to replace the table if the base is USD, `USD == 1.0`,
and every selectable currency is present. The completeness check matters because
`CurrencyConverter.convert()` returns the amount **unchanged** for an unknown
currency — a partial table would convert those at 1:1 silently rather than fail.

The server's required set is fixed at construction. A first attempt derived it
from whatever was currently served, which ratchets: after one good fetch it would
have been all 166, and the provider dropping a single obscure currency would then
have rejected every subsequent table.

Failures never regress the served rates. Verified by killing the provider:

```
FX: restored 166 cached rates quoted 2026-09-01T00:02:31Z
FX: refresh failed (...connectex: No connection could be made...);
    keeping the current table, retrying in 5m0s
```

`/rates` still answered with the cached TRY of 48.27, not the built-in 34.

### Privacy policy updated — it had become untrue

The policy said "**The only** information that leaves your device is what the
Google Mobile Ads SDK collects". A daily rate request makes that false, and an
inaccurate disclosure on a shipping app is a Play problem, so both
`docs/privacy-policy.md` and `docs/privacy.html` gained a section: at most once a
day, no account or advertising ID or device identifier or subscription data in
the request, only the IP is inherently visible, result cached locally.

### Verified on device (PJX110, debug build, clean install)

| Check | Result |
| --- | --- |
| First launch | `--> GET https://open.er-api.com/v6/latest/USD` → `<-- 200` |
| Cache written | `quoted_at 2026-09-02 00:02:31Z`, `next_update 2026-09-03 00:07:41Z` (24.1h apart) |
| Second launch | **0** requests — the cached schedule suppresses it |
| Settings row | `Rates By Exchange Rate API` / `Updated Sep 2` |
| End-to-end total | 100,000 COP → **$31.13** |

The last row is the one that proves the rates are actually in use. COP is where
the old table was most wrong: live 3212.46 against a fallback of 4100, so the
same subscription would have read $24.39 on the old values. `100000 / 3212.4559 =
31.13`.

Release-build checks, because the DTO is Gson-reflected and R8 renames fields:
`ExchangeRateResponse` survives unobfuscated in `mapping.txt`, and
`open.er-api.com`, `v6/latest/USD`, `time_next_update_unix` and `base_code` are
all present in `classes.dex`, with `Rates By Exchange Rate API` in
`resources.arsc`. An initial sweep reported all of these missing; the controls
`ca-app-pub-` and `subflow_sub_monthly` were missing too, which showed the
`strings` invocation was broken rather than the APK.

### Still open

`/rates` on the server is public and unauthenticated, and re-serves provider data
programmatically. It reads as an internal endpoint for our own app rather than a
product, but documenting or publicising it as an API would change that.

### Keyed rate endpoint — 2026-09-02

`EXCHANGE_RATE_API_KEY` now selects the keyed tier. Unset keeps the open,
keyless endpoint, which is still the default and needs no account.

The two endpoints are not interchangeable. They differ in host, in path, and in
the name of the rate map — the open one says `rates`, the keyed one says
`conversion_rates`. Both field names are decoded and whichever arrived is used,
so one code path serves either.

The key sits in the URL path, which makes it a credential-leak hazard rather
than just a config value: `net/http` embeds the full request URL in every
`*url.Error`, and those are logged on each retry. Errors are scrubbed before
they reach the log, and there is a test that fails if the key ever appears in
one.

Verified against the live service with a deliberately invalid key: the log reads

```
FX: using the keyed endpoint (EXCHANGE_RATE_API_KEY is set).
FX: refresh failed (provider rejected the API key (HTTP 403) - check
    EXCHANGE_RATE_API_KEY; retrying will not fix it); keeping the current table
```

and the key appears nowhere in the log file. The first attempt reported only
`provider returned HTTP 403`, because the real service answers a bad key with a
bare 403 rather than the documented `{"result":"error","error-type":"invalid-key"}`
body — so the JSON error path never ran and the operator was left guessing.
Status codes are now explained, but only when a key is actually configured; a
keyless 403 must not blame a key that does not exist.

Keyless regression, same build: `FX: refreshed from Exchange Rate API, quoted
2026-09-02T00:02:31Z`.

**What the key does not change:** the Android app fetches directly from the
keyless endpoint, because the release build has no backend to route through.
Attribution in the app is therefore still required. The key removes the
obligation only for the admin console, and gives the server a real quota instead
of a shared rate-limited pool. Since the server fetches once a day either way,
that is currently the whole practical benefit. Routing the app through the
server would change this, and needs a deployed backend plus BACKEND_ENABLED —
which also switches on cloud sync.

### Configuration file — 2026-09-02

Server settings now come from a TOML file, the environment, or both:
`-config <path>`, else `$SUBFLOW_CONFIG`, else `subflow.config.toml` in the
working directory if present.

TOML rather than JSON, which the first version of this used. JSON has no
comments, so the template had to smuggle its documentation into the values
(`"jwt_secret": "CHANGE-ME - signs session tokens..."`) — a workaround that is
itself the argument against the format. The justification for JSON had been that
it needs no dependency, and that turned out to be false: `go mod why` shows both
`pelletier/go-toml/v2` and `goccy/go-yaml` are already compiled into this binary
through gin's request binding, so either costs nothing new.

TOML over YAML because this file holds secrets and TOML's strings are
quote-explicit. In YAML an `admin_token` of `no` decodes as a boolean and `0123`
as the integer 123; a format that silently retypes credentials is a poor fit for
a file made mostly of them. There is a test pinning that behaviour.

**The environment overrides the file.** A deployment can then change one setting
without rewriting a file it may not be able to edit, and a leaked credential can
be rotated without touching disk.

Adding this required removing a package-level initialiser. `jwtSecret` was a
package var in `auth_service.go` calling `os.Getenv` at init time, which runs
before `main()` — no config file could ever have reached it. The secret is now a
field on `AuthService`, passed in, with the same random-per-boot fallback when
unset.

Choices worth recording, each guarding a way this fails quietly:

- **A missing file named explicitly is a fatal error**, while a file merely
  looked for is not. A typo in `-config` would otherwise start a perfectly
  healthy server with none of the settings the operator believed they had passed.
- **Unknown keys are rejected** (`DisallowUnknownFields`). `jwt_secrets` looks
  like a working config until something fails to authenticate.
- **An empty environment variable is not an override.** A shell exporting
  `PORT=` would otherwise blank a configured port.
- **The startup banner reports presence, not values.** The point of moving
  secrets out of the source was that they stopped being readable; a banner
  echoing them would undo that.

`subflow.config.toml` and `*.config.toml` are git-ignored with an exception for
`*.config.example.toml`, verified by copying the template into place and
confirming git ignores it.

Unknown keys are reported as one line naming the key and its line number
(`unknown key(s): admin_tokens (line 40)`). go-toml's own message is a
seven-line annotated excerpt of the file, which reads well in a terminal and
badly in a log where one event should be one line.

Verified end to end, not just in unit tests:

| Check | Result |
| --- | --- |
| File drives port / db_path / admin_token | 8204 served, `cfg.db` created, file token → 200, wrong token → 401 |
| Secret values in the log | Neither the JWT secret nor the admin token appears |
| `PORT`/`ADMIN_TOKEN` env with the same file | 8205 served, 8204 not listening; env token → 200, **file token → 401** |

The last row is the one that matters: the file's admin token stops working the
moment the environment supplies one, which is what "the environment wins" has to
mean in practice.

## Brand logos and custom icons — 2026-09-02

Subscriptions rendered an initial-letter tile. They now render the real brand
mark, and users can supply their own image for anything the catalogue does not
cover.

### The runtime-fetch approach was rejected

The server's preset catalogue already carries `icon_url` values pointing at each
brand's own servers — `assets.nflxext.com`, `open.spotifycdn.com`,
`chatgpt.com/favicon.ico`. Loading those is the obvious implementation and it
leaks the thing this app exists to protect: a request to Netflix's CDN for the
Netflix logo tells Netflix this device tracks Netflix, and across a dozen rows
that distributes the user's financial profile to the companies they pay. It
contradicts ADR 0001 and the privacy policy line stating that what the user
enters stays on the device.

Marks are therefore bundled. `BrandIconBadge` renders only `file://` URIs the
app itself wrote; the catalogue's remote URLs are ignored by construction, so no
call site can reintroduce the leak.

### Why simple-icons rather than favicons

Favicon services were measured before choosing. Google's caps at 64x64 for
Netflix regardless of the requested size; DuckDuckGo returns 48x48 for Netflix,
32x32 for Spotify and 96x96 for Disney+. A 46dp badge on this 3x device needs
138px, so those would have been visibly soft and inconsistent between brands.
Clearbit was unreachable from here entirely.

simple-icons ships single-path monochrome SVGs. Converted to Android vector
drawables they stay crisp at any size, total 53.5 KB for 32 marks, and drawn
white on the existing brand-coloured tile they sit exactly where the initial
letter used to — the badge's layout does not change at all.

32 of 33 presets resolved. simple-icons carries no Disney mark, so Disney+ keeps
its "D" tile; the fallback is a supported outcome rather than a gap.

### Matching by name, not just preset id

Subscriptions store a name; the preset id only exists while the add screen is
open. A lookup keyed on id alone would have left every row already in someone's
vault with a letter tile forever. `BrandLogos.forName` normalises case, spaces,
punctuation and "+"/"plus", then falls back to a prefix match so a row renamed
"Netflix (family)" still resolves.

### Custom logos

Picked with `PickVisualMedia`, so no storage permission and the app sees only
the chosen image. The file is copied into private storage rather than
referenced: the picker's `content://` grant dies with the process, so storing
that string would give a logo that works until the next launch and then renders
nothing. Images are downsampled to 256px on decode — a 12MP photo decoded at
full size to draw at 44dp is an OutOfMemoryError on a low-end device.

Selecting a preset clears a custom logo, otherwise the previous image would
cover the new brand's mark.

### Verified on device (PJX110)

| Check | Result |
| --- | --- |
| Preset grid, debug | Netflix, Spotify, YouTube, HBO Max, Apple TV+, OpenAI, Figma, Slack all render their marks |
| Disney+ fallback | Renders "D", indistinguishable in style from the rest |
| Custom logo picked | Fills the tile edge to edge, remove control appears |
| Stored file | `files/subscription_logos/sub_fa3ae78a-f.png`, 1528 bytes, `-rw-------`, downscaled from 3057 |
| Persists after save | Dashboard row shows the custom image |
| **Release build, real data** | HBO Max, ChatGPT Plus, Apple TV+ and Netflix all render — and those rows predate the feature, so this is the name-matching path, not preset ids |
| R8 + resource shrinking | All 32 `brand_*` drawables present in `resources.arsc` |
| APK size | 4.2 MB to 4.3 MB |

### Worth a decision before release

The marks are trademarks. Using them to identify the actual service being
tracked is what every subscription tracker does and is the ordinary nominative
case, but it is bundled artwork in a commercial APK and Play has an
impersonation policy. Nothing here implies endorsement, and no brand-authored
bitmap is redistributed — the glyphs are simple-icons' own paths. Flagging it
rather than deciding it.

### App prefers the backend's rates — 2026-09-02

The app now asks the SubFlow backend for rates first and falls back to the
public endpoint. The deciding factor is whether the server has its own key.

`/rates` gained a `keyed` boolean. Without it the app could not tell a server
that is adding something from one that is relaying the very endpoint the app
can already reach — going through an unkeyed server buys nothing and adds a hop
that can be down.

Resolution order:

| Situation | Source |
| --- | --- |
| `BACKEND_ENABLED=false` (the shipped release) | Direct, `open.er-api.com` |
| Backend reachable and `keyed: true` | Backend `/rates` |
| Backend reachable, `keyed: false` | Direct |
| Backend unreachable, or its answer fails validation | Direct |

A backend answer is validated as strictly as a provider one — USD base,
`USD == 1.0`, every selectable currency present — and nothing is cached on
rejection, so a misconfigured server cannot displace good rates already held.

Debugging this on real hardware needed a build change: the debug `API_BASE_URL`
was hard-coded to `10.0.2.2`, the emulator's view of the host, which a physical
device cannot resolve. It is now overridable:

```
adb reverse tcp:8085 tcp:8085
./gradlew :app:assembleDebug -Psubflow.debugApiBaseUrl=http://127.0.0.1:8085/api/v1/
```

Verified on device, one build, server restarted between runs:

| Server | logcat |
| --- | --- |
| `keyed: true` | `GET http://127.0.0.1:8085/api/v1/rates` → 200. **Zero** requests to open.er-api.com |
| `keyed: false` | backend 200, rejected, then `GET https://open.er-api.com/v6/latest/USD` → 200 |

The second row is the whole feature: the app consulted the backend, decided it
had nothing to offer, and went direct without the user seeing anything.

### Settings footer

Attribution moved from under the currency row to the foot of Settings, centred
above the version, which is where this kind of credit is looked for.

The version no longer shows the build number — `SubFlow Version 1.0.0` rather
than `1.0.0 (2)`. The build number is an artefact of the Play upload process and
means nothing to the reader. It is still attached to support emails, where it
identifies the exact build and does mean something.

### Brand logo corrections — 2026-09-02

A tester flagged that some marks were wrong. Auditing them by driving the phone
meant 32 taps and a lot of scrolling on a device that kept surfacing the owner's
notifications, so the bundled drawables were rendered into a contact sheet on
the build machine instead (`tools/` has the scripts). Five were wrong, all of
them mine:

| Preset | Was showing | Why wrong |
| --- | --- | --- |
| Disney+ | letter "D" | simple-icons has no Disney mark |
| Google One | Google's "G" | the parent company, not the product |
| Microsoft 365 | old Office icon | a sibling product's previous mark |
| PlayStation Plus | PlayStation shield | platform mark — kept, see below |
| Nintendo Switch Online | Switch mark | platform mark — kept, see below |

**Disney+ is fixed properly.** Its wordmark is a single colour, so it works as a
white silhouette like every other mark. It is not in simple-icons, so it is
fitted from the published SVG. The source viewBox is 1041x565 rather than
24x24, and rather than rescale the coordinates the drawable declares the source
viewport and centres it with a group transform — rewriting path numbers by hand
is how a logo ends up subtly wrong.

**Google One and Microsoft 365 now show letter tiles.** Their real logos are
multi-colour (3 and 5 fills respectively, checked in the published SVGs) and do
not survive being reduced to a white silhouette. A recognisable but wrong mark
is worse than no mark, because it still reads as an answer.

**PlayStation Plus and Nintendo Switch Online keep the platform mark**, because
unlike the two above, their own logos are built from it.

Verified: Disney+ renders its wordmark on device, Microsoft 365 renders "M".
In the release APK, 31 `brand_*` drawables survive R8 and resource shrinking,
`brand_disney` is present, and the two removed ones appear zero times.

Getting official marks for the remaining gaps means the badge carrying
full-colour artwork on a neutral tile rather than a white glyph on brand colour
— a visual change to every row, and a heavier trademark position than
simple-icons' own paths. Not done.

### Apple Music, and a whole class of inverted marks — 2026-09-02

The tester was right again. The cause was not a wrong mark this time but a
wrong assumption about what simple-icons ships.

Some of its icons are the **whole app icon** — the rounded container and the
symbol as one filled shape — rather than a bare glyph. Painted white on a
coloured tile those invert: the container becomes a solid white square and the
symbol is knocked out of it in the brand colour. Apple Music rendered as a white
square with a red note, which is the exact negative of the real icon.

Five are affected: `applemusic`, `1password`, `duolingo`, `medium`,
`nintendo_online`. The badge now paints these the other way round — brand colour
on a light tile, at full tile size rather than inset — which reproduces each
icon as designed.

It is an explicit list, not a heuristic. "Does this path fill most of its
viewBox" also matches legitimate full-bleed glyphs like the Netflix N, and a
false positive silently inverts a logo that was fine.

The audit method mattered as much as the fix. The first attempt rendered the
drawables with a hand-rolled path sampler, which approximated curves by their
endpoints and produced shapes mangled enough to be useless — it could not have
found this. The drawables are now rendered as real SVG in a browser, on their
actual brand colours, which showed all 31 at once and made the inverted ones
obvious. Driving the phone would have been worse than slow: blind coordinate
taps twice opened the device owner's private messages.

Verified by rendering the bundled drawables against the simple-icons sources:
byte-identical, so the conversion was never the problem, and inverted they match
the real icons. Medium's mark is simple-icons' own cropped wordmark — the
partial "e" is their artwork, not damage from the conversion.

Not re-verified on device: the phone disconnected before this build could be
installed.

### The real Apple Music bug: Android cannot parse compact arc flags

The inversion fix above was correct but incomplete — Apple Music still rendered
as a mangled diagonal on device while rendering perfectly in a browser. That
split is the whole clue, and it took embarrassingly long to read: identical path
data, two renderers, two results, so the fault is in the parser, not the data.

An SVG elliptical arc takes seven parameters:

```
rx ry x-axis-rotation large-arc-flag sweep-flag x y
```

The two flags are single digits, and SVG lets them run together with each other
and with the coordinate that follows. simple-icons is minified, so it emits:

```
a9.23 9.23 0 00-.24-2.19
```

meaning flags `0` and `0`, then (-0.24, -2.19). Android's VectorDrawable path
parser tokenises numbers greedily: it reads `00` as a single value, every
later parameter shifts by one, and the rest of the subpath becomes noise.
Browsers implement the SVG grammar and get it right — which is exactly why the
side-by-side comparison earlier showed source and bundled as identical and
"proved" the conversion was fine. It was. The conversion was never the problem.

Five marks were affected: `applemusic`, `github_copilot`, `hulu`, `nordvpn`,
`primevideo`. Only Apple Music was obviously broken; the others were subtly
wrong in ways nobody would have reported.

`tools/normalise_arcs.py` expands the flags with explicit separators. The
numbers are untouched — only the whitespace between them changes — and it is
unit-tested against packed flags, flags packed against the coordinate, already
separated input, multiple groups after one command letter, and non-arc data.
Both generators now run it, and regenerating from scratch reproduces the
committed drawables exactly (30 of 30), so this cannot come back.

| Check | Result |
| --- | --- |
| Apple Music on device | Clean red tile, white note — the real icon |
| Prime Video, Hulu | Clean wordmarks |
| Whole grid | No regressions |
| Numbers preserved | Token counts rise only on the three files with packed flags (359→371, 113→117, 41→44) — the flags Android had been swallowing |
| Release build | Succeeds |

### Four more wrong marks, three of them my own doing — 2026-09-02

The tester reported Amazon Prime, Duolingo, Medium and Nintendo Switch Online.
Three of those four were broken by the inversion commit above.

**The inversion list was over-applied.** Only Apple Music is genuinely an
app-icon container. 1Password, Duolingo, Medium and Nintendo Switch Online read
correctly as plain white glyphs, and inverting them broke four working icons to
fix one. They were misclassified from a rendering that was itself corrupted by
the arc-flag parsing bug — the evidence for the classification was bad, and the
classification went in anyway. The list is now `setOf("applemusic")`.

**Amazon Prime** was never inverted; it was simply unreadable. simple-icons'
`amazonprime` is the two-word "amazon prime" wordmark, which shrinks to
illegible in a 44dp square. Replaced with Amazon's own a+smile, which is compact,
legible and still identifies the service.

**Medium** falls back to a letter tile. simple-icons' artwork is a container
with the wordmark cropped mid-"e" — that is their own artwork, not conversion
damage, and nothing renders it cleanly at badge size. A white "M" on Medium's
black is closer to the real icon than a cropped word.

The method that finally worked was rendering each mark in **both** treatments
side by side and comparing, rather than reasoning about which treatment ought to
be right. Two of my three previous logo commits would have been avoided by doing
that first.

Verified on device: Amazon Prime shows the a+smile, Duolingo a white owl on
green, Nintendo Switch Online white Joy-Cons on red, 1Password a white keyhole
on blue, Medium a white "M", and Apple Music is unchanged and still correct.

### One full-colour mark, not thirty — 2026-09-02

The question was whether to use PNG originals instead of vectors. Two findings
changed the answer.

**Format: WebP, not PNG.** Measured at 192px, the size a 64dp badge needs on a
3x screen: 30 icons cost 628 KB as PNG and 109 KB as WebP. Same images, six
times smaller. PNG has no advantage here.

**Scope: one icon, not thirty.** Switching the whole catalogue to App Store
artwork looked attractive until the comparison was rendered:

- Duolingo's App Store icon was a seasonal novelty variant on the day this was
  written. Bundling it would freeze a temporary promotion into the app.
- Searching "amazon prime video" returns Prime Video, which is a different
  product from the Amazon Prime membership the preset tracks. Name matching
  needs per-brand verification, thirty times.
- It is the brand's own icon artwork rather than simple-icons' redrawing, which
  is a heavier trademark position.

So the vectors stay, and full-colour bitmaps are the exception:

| Preset | Outcome |
| --- | --- |
| Google One | Official app icon, WebP, **1,362 bytes**. Its four-colour ribbon cannot be a silhouette, and the "G" it fell back to was Google's corporate mark, not this product's. |
| Microsoft 365 | Letter tile. No App Store app under that name, and the published SVG is the grey wordmark with no symbol at all. |
| Medium | Letter tile. A white "M" on black already *is* its icon. |

The badge gained a full-colour path: the artwork carries its own background, so
it renders edge to edge with no tile colour and no tint.

Verified on device and in the release APK, where the WebP survives resource
shrinking and the APK stays at 4.3 MB.

### Prime Video and the gym glyph; Duolingo blocked — 2026-09-02

**Prime Video** now uses its official app icon. The monochrome "prime video"
wordmark had the Amazon Prime problem: two stacked words squeezed into a badge,
legible only if you already knew what it said. 4,620 bytes as WebP.

**Gym membership** is the one preset that is not a brand — no website, no logo
to be right or wrong about. It showed a bare "G", which said nothing. It now
shows a dumbbell from Google's Material Symbols (Apache-2.0), which is the
honest answer for a category rather than a company.

**Duolingo could not be fixed.** Both stores currently ship the same seasonal
novelty icon — a melting owl — not the standard Duo. Checked all three plausible
sources:

| Source | What it has |
| --- | --- |
| App Store artwork | The seasonal melting owl |
| Google Play `og:image` | The same seasonal melting owl |
| Wikimedia | Only the "duolingo" wordmark, viewBox 1000x234 — the wide-wordmark trap again |

Shipping the seasonal icon would freeze a promotion into the app permanently, so
the monochrome owl stays for now. It is closer to the standard icon than the
melting one is. Worth revisiting once the promotion ends, since the Play icon
will then be the standard Duo.

Device note: the phone had another app (`vip.fastgo.gamebox`) throwing dialogs
over the screen during this round, including an install prompt for an unrelated
APK from "Unknown source from computer". Nothing was installed — the prompt was
dismissed without tapping Install. Verification taps now check
`mCurrentFocus` before firing.

### Duolingo, with the logo supplied — 2026-09-02

The previous round left Duolingo unresolved because every source I could reach
was serving a seasonal novelty icon. Supplying the standard mark directly closed
it, and the result argues for how the remaining gaps should be filled.

It is converted to a **multi-colour vector drawable**, not a bitmap: 4.6 KB,
crisp at any size, and — the point that mattered here — a vector of the standard
mark cannot go stale the way a scraped store icon can.

`tools/colour_vector.py` handles two things a naive conversion gets wrong:

- **fill-rule is inherited.** This file sets `fill-rule="evenodd"` on a wrapping
  `<g>`, and seven of the nine paths need it. Without it the owl's eyes fill
  solid and the face becomes a green blob.
- **the viewBox does not start at 0.** It is `-.016 0 250.024 250.024`, so the
  drawable declares the source viewport and translates, leaving path data
  byte-identical to the source rather than rescaling coordinates by hand.

Verified by rendering the converted drawable against the supplied SVG side by
side — indistinguishable — and then on device.

Round complete: Duolingo shows the real Duo owl, Prime Video its official icon,
Gym membership a dumbbell. In the release APK all four new marks survive
resource shrinking, the superseded monochrome owl is gone, and the APK is
unchanged at 4.3 MB.

Remaining gap: Microsoft 365, which still has no obtainable symbol and keeps a
letter tile. Supplying an SVG the way Duolingo's was supplied is now the fastest
route for anything like it.

### Microsoft 365 — the symbol was in the file all along

The last gap is closed, and it was my earlier analysis that was wrong, not the
source. I had concluded the published SVG "is the grey wordmark with no symbol".
It is not: the four squares are `<rect>` elements, and my extraction only looked
at `<path>`. A path-only reading of that file finds nothing but grey text, which
is exactly what it reported.

`tools/extract_m365_symbol.py` reads the squares' coordinates and colours from
the file rather than retyping them, so the geometry is Microsoft's:

| Colour | Position | Size |
| --- | --- | --- |
| `#f25022` | 0, 0 | 101.46² |
| `#7fba00` | 112.02, 0 | 101.46² |
| `#00a4ef` | 0, 112.06 | 101.46² |
| `#ffb900` | 112.02, 112.06 | 101.46² |

Symbol only. The full file is 1319 units wide against 213 tall — squeezing the
"Microsoft 365" wordmark into a square badge would repeat the Amazon Prime
mistake. Unlike Google One's artwork the symbol has no background of its own, so
a white ground is added; drawn edge to edge with transparency it would show the
card through it and break the tile shape. 1,641 bytes.

Every preset now has a mark it deserves, except Medium, which keeps a letter
tile because a white "M" on black already is its icon.

**The general lesson from this whole run of logo work:** every wrong logo traced
back to trusting an inference instead of rendering the thing and looking at it.
The arc-flag corruption, the over-applied inversion, the "no symbol in this
file" conclusion — all three were confident readings that a side-by-side render
would have disproved in seconds. The tools in `tools/` exist so that check is
cheap.

### Netflix, and the converter growing up — 2026-09-02

Netflix was the hardest supplied logo so far, and it pushed
`tools/colour_vector.py` from "handles Duolingo" to something general.

The file needed three things the converter did not do:

- **Fills as CSS classes.** `.st0{fill:#b1060f}` in a `<style>` block rather
  than `fill=` attributes. An attribute-only reader paints every path black —
  and would have done so silently.
- **Nested group transforms**, `translate` then `translate` + `scale`, two deep.
- **A non-square viewBox**, 122.8 x 222. Mapped straight onto a square drawable
  Android stretches it; it is centred in a square viewport instead.

One path is filled with a radial gradient. Android can express gradients but not
a `gradientTransform` matrix without baking it, so that path is skipped and the
skip is reported rather than silently dropped. Rendering with and without showed
the gradient is a depth effect invisible at badge size.

`--inset` was added at the same time: the N filled the tile edge to edge and
looked cramped against the rounded corners. Microsoft 365 had needed the same
thing, hardcoded in its own script; it is a proper option now.

Netflix sits on black, which is its real icon. The old monochrome N sat on a red
tile — red on red, barely legible, and it had been that way since the marks were
first added without anyone noticing.

**A regression was caught doing this.** Rewriting the converter for CSS support
dropped the group-inherited `fill-rule`, taking Duolingo from seven `evenOdd`
paths to two — which would have filled the owl's eyes solid. The check that
caught it was re-converting Duolingo and diffing against the committed drawable,
and it is worth keeping as the habit: after touching the converter, re-run every
logo it has already produced and compare.

Verified: Duolingo re-converts byte-identically in path data, colours and fill
rules; Netflix renders as the source does; both correct on device. In the
release APK all five full-colour marks survive shrinking, the superseded
monochrome N is gone, and the APK stays 4.3 MB.
