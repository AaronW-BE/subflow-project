# BIN-83 verification record

## Host checks

- `:app:testDebugUnitTest`: 268 tests, zero failures/errors/skips, including migration, legacy backup, date/DST, qualification, deduplication, permission denial/recovery, UI decisions and actual edit/save coverage.
- `:app:assembleDebug`: passed.
- `:app:assembleRelease :app:lintRelease`: passed with R8/resource shrinking enabled; lint has zero errors and 122 warnings. Existing Gradle/AGP deprecations remain.
- `python tools/check_android_locales.py`: all 11 locales passed complete-key, duplicate and format checks. Nontranslatable resources are inherited; plural formatting is compared per quantity, not by concatenating language-specific quantities.
- `python -m unittest discover -s tools -p test_check_android_locales.py`: six tests passed, including intentional missing/duplicate/wrong-format fixtures.
- Independent step reviews and an integration review found no blocking issues. `git diff --check` passed.

The initial combined build used a deliberately restricted 1 GiB heap and failed with `R8: java.lang.OutOfMemoryError: Java heap space`. Retrying only release assembly/lint with 2 GiB passed. No build settings, optimization rules or project processes were changed to bypass this failure.

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  --max-workers=1 --no-parallel --no-daemon \
  '-Dorg.gradle.jvmargs=-Xmx1024m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' --console=plain
./gradlew :app:assembleRelease :app:lintRelease \
  --max-workers=1 --no-parallel --no-daemon \
  '-Dorg.gradle.jvmargs=-Xmx2048m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' --console=plain
```

## Unverified device behavior

A new temporary API35 AVD was created from configuration only, without copying existing userdata. It exited during startup and did not remain connected. No device-level reminder scenario was completed; no real phone subscriptions, permissions or clocks were changed for BIN-83.

Still required in an isolated test installation, without real subscriptions or provider actions:

- Fresh install defaults off; denied exact access uses ordinary timing; deliberate grant and return refresh the status and schedule.
- Native time picker and settings legibility across locales, 12/24-hour formats and text sizes.
- Seed a disposable auto-converting trial and verify one notification, its detail link, same-day enable catch-up and no duplicate after edits/keep-undo.
- Verify confirmed local cancellation pauses only the test record; dismissing confirmation changes nothing.
- Disable notifications or its dedicated channel; check the independent warning and absence of a sent marker, then re-enable while the date is still current.
- Revoke exact access, restart, change clock/timezone, reboot and exercise Doze; verify supported reconciliation and ordinary fallback without claiming guaranteed delivery.
- Force-stop intentionally prevents delivery; reopening reconciles only still-current dates.

Host/Robolectric checks do not substitute for these platform/OEM checks or prove delivery before a provider charge. API31-specific device testing is also outstanding.

## Other release restrictions

`python tools/check_play_copy.py` validates listing lengths but fails the pre-existing mismatch between build 1.6.2/versionCode 12 and latest notes 1.6.1/versionCode 11. No fabricated notes or version change were introduced. Dutch copy remains a draft for a future upload.

No PR, commit, merge, Play upload or release was performed as part of this implementation. Linear stays In Progress pending user acceptance/merge; verification above is not a release authorization.
