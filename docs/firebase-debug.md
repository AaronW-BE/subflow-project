# Firebase Android configuration

Debug and release use separate Android app registrations in the existing
Firebase project `subflow-9ed81`:

- Release: `org.dpdns.alwaysup.subflow`, configured locally in
  `android/app/google-services.json`.
- Debug: `org.dpdns.alwaysup.subflow.debug`, display name **SubFlow Debug**,
  configured locally in `android/app/src/debug/google-services.json`.

The Google Services plugin selects the debug source-set file for debug builds;
release continues to use the existing app-level file. Both configuration files
are ignored by Git and must be provisioned separately on a new machine or CI.
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
