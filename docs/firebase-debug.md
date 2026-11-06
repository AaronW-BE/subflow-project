# Firebase Android configuration

Debug and release use separate Android app registrations in the existing
Firebase project `subflow-9ed81`:

- Release: `org.dpdns.alwaysup.subflow`, configured locally in
  `android/app/google-services.json`.
- Debug: `org.dpdns.alwaysup.subflow.debug`, display name **SubFlow Debug**,
  configured locally in `android/app/src/debug/google-services.json`.

The Google Services plugin selects the debug source-set file for debug builds;
release continues to use the existing app-level file. Both configuration files
are ignored by Git and must be provisioned separately for real local builds.
Do not replace the release file with the debug configuration.

To obtain the debug file with an already authenticated Firebase CLI:

```bash
mkdir -p android/app/src/debug
firebase apps:sdkconfig ANDROID 1:169611570958:android:49ff91187934e56513715f \
  --project subflow-9ed81 --out android/app/src/debug/google-services.json
```

Verify normal processing without any local Gradle init-script workaround:

```bash
cd android
./gradlew :app:processDebugGoogleServices :app:processReleaseGoogleServices
```

This registration configures Firebase SDK initialization only. It does not add
Google sign-in or configure OAuth/SHA certificate fingerprints. Debug and release
remain in the same Firebase project; they are separate apps, not isolated
production/test projects. Analytics collection behavior is unchanged.

## CI-only synthetic configuration

The Android workflow tests `tools/prepare_ci_firebase.py` and runs it before
Gradle on a clean checkout. It requires `CI=true` and exclusively creates
`android/app/src/debug/google-services.json`; an existing file causes failure
without being read or overwritten. There is no release destination option.
It never reads or uploads local Firebase configuration or requires Firebase secrets.

The fixture uses the synthetic `demo-subflow-ci-only` project, an all-zero app
identifier and a clearly fake API key for `org.dpdns.alwaysup.subflow.debug`.
This lets the normal Google Services task, debug build, tests and lint run; it
is not a real Firebase registration or proof of Analytics delivery. Builds
using this fixture must not be distributed. Real local debug and production
builds continue to use their separately provisioned configurations above.

Test the helper without creating configuration in the working checkout:

```bash
python -m unittest discover -s tools -p 'test_prepare_ci_firebase.py' -v
```
