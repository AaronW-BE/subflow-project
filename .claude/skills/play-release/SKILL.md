---
name: play-release
description: Prepare a SubFlow build for upload to Google Play - bump versionCode/versionName, write the release notes in every shipped language, re-check the store listings against the code, build and verify the AAB. Use whenever the user is getting ready to upload to the Play Store (准备上传, 上传 Play, 发版, 打 release 包, release notes, 版本说明, 更新内容).
---

# Preparing a Play Store upload

Every upload gets release notes in every language the app ships, and a
re-check of the store listings. Both live in `docs/play-store-copy.md`, and
`tools/check_play_copy.py` checks their form. The steps below go in order;
nothing is uploaded until the user does it.

## 1. What changed since the last upload

Each uploaded build is tagged `v<versionName>` on the commit it was built
from (v1.1.0 = versionCode 3 = `043c2d3`).

```bash
git describe --tags --abbrev=0 main   # the last uploaded version
git log --merges --oneline <that tag>..main
```

For each merged PR, read its description and its Linear issue. Sort the
changes into **what a user will notice** (features, fixes they would feel,
new languages) and **what they won't** (refactors, tests, build, debug-only).
Only the first kind goes in the notes.

If the newest tag is not the build the user actually uploaded, ask. Don't
guess what they uploaded.

## 2. Version

- `versionCode` = the last uploaded code + 1. Play rejects a reused code, even
  one that was only uploaded to a testing track.
- `versionName`: minor bump (1.2.0 → 1.3.0) for anything a user can go looking
  for, like a feature, a language or a reorganised screen; patch bump
  (1.2.0 → 1.2.1) for fixes only.
- Extend the comment above `versionCode` in `android/app/build.gradle.kts`
  with what this code carries, like the entries before it.

## 3. Release notes (every shipped language)

Add a new section at the **top** of `docs/play-store-copy.md`, above the
previous release, headed exactly:

```
## X.Y.Z (versionCode N): release notes
```

Include a short table of changes with their issue and PR, then one block in
Play's paste-in format covering every shipped language:

| Resource dir | Play codes |
|---|---|
| `values` | en-US |
| `values-zh` | zh-CN |
| `values-b+zh+Hant` | zh-TW, zh-HK (same text) |
| `values-de` / `-fr` / `-es` / `-ja` | de-DE / fr-FR / es-ES / ja-JP |

Rules that came from getting it wrong once:

- **Use the app's own words.** Read the actual strings in each locale's
  `strings.xml` and use them: German Settings pages are *Allgemein*, not
  *Einstellungen*; home currency is 本币 / 主要貨幣 / Hauptwährung / devise
  principale / moneda principal / 基準通貨.
- **Match the app's form of address**: du (de), vous (fr), tú (es), です/ます
  (ja). zh-TW in Taiwan usage (設定, 資料, 帳號, 扣款), not character conversion.
- Write for the user, not the developer: say what they'll see, not how the
  code changed. Around 3–5 bullets; 500 characters is the hard limit.
- The Console box pre-fills only the store listing's languages, but it
  accepts every tag. Paste the whole block.

## 4. Store listings: re-check, don't just carry forward

The listings in `docs/play-store-copy.md` make claims about the app. For each
row of the claims table ("What each claim rests on"), re-read the code it
points to. Counts drift: presets (`defaultLocalPresets()`), currencies
(`SupportedCurrencies`), the free limit (`FREE_TIER_LIMIT`), reminder leads
(`ReminderLead`), what's behind `ProGate`, and languages (`SupportedLanguages`).
If anything changed, fix **every** language's listing, not just one, and
update the table.

Never claim something the release build doesn't have. Cloud sync stays out
while `BACKEND_ENABLED` is false in release.

A new language needs a full listing (title, short description, full
description) translated from an existing one, plus a row in `PLAY_CODES` in
`tools/check_play_copy.py`.

## 5. Check the copy

```bash
python tools/check_play_copy.py
```

It must pass. It checks that the newest heading matches gradle's
versionCode/versionName, that every shipped language has notes, the
500 / 30 / 80 / 4000 limits, and the script of each Chinese variant (zh-CN in
GB2312, zh-TW/zh-HK in Big5). It checks form only; step 4 covers meaning.

## 6. Console items

Update the "Other Console items for this release" list in the copy file.
Always consider:

- **Data safety**: any new permission, SDK, network endpoint or stored
  personal data? If none, say so explicitly.
- **Screenshots**: did any screen in `play-assets/screenshots/` change? Play
  requires the long side to be no more than twice the short side: 1080×2160
  is accepted, the Pixel 8a's native 1080×2400 is rejected.
- **New listing languages** to add under Main store listing → Manage
  translations.

## 7. Build and verify the artifact

```bash
cd android
rm -rf app/build/outputs/bundle/release app/build/outputs/apk/release
./gradlew :app:testDebugUnitTest :app:bundleRelease :app:assembleRelease
```

Check the artifact itself, not the build script:

- `aapt2 dump badging app-release.apk`: the package is `org.dpdns.alwaysup.subflow`
  and the versionCode/versionName are the new ones
- `keytool -printcert -jarfile app-release.aab`: signed by `CN=Bin Tech`
  (keytool prints in Chinese here: 所有者)
- no `subflow.debug` in `base/manifest/AndroidManifest.xml`
- every shipped language's strings are in `base/resources.pb`. The resource
  filter strips any language it doesn't name, silently
- unit tests: 0 failures

## 8. PR, merge, final build

Put the version bump and the copy in one branch and one PR. The user merges;
if they say "merged", check `gh pr view <n> --json state` first, because twice
it wasn't. After the merge, rebuild on `main` and confirm with
`git diff --quiet <branch> main` (identical trees) that the AAB being handed
over is main's code. Give the user the AAB path and the release-notes block to
paste.

## 9. After the user confirms the upload

```bash
git tag -a vX.Y.Z <commit the AAB was built from> -m "Play upload, versionCode N"
git push origin vX.Y.Z
```

That tag is where step 1 starts next time.
