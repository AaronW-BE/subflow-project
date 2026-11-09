---
id: subflow-debug-device-acceptance
type: module-design
status: draft
title: Disposable Debug device acceptance
references: [subflow-subscription-calculations, subflow-localization-preferences]
---

## Responsibility

Exercise the real NavHost and native windows on an explicitly authorized disposable Debug installation. This is controlled-authorization UI acceptance, not evidence of real Google Play purchase ownership. Business calculations and preference semantics remain owned by the referenced modules.

## Boundary

- Assert both application ID and target-context package are `org.dpdns.alwaysup.subflow.debug` before fixture/preference mutation or Activity launch. Require an awake, user-unlocked device. The host runner requires explicit destructive opt-in, one selected adb serial and Debug foreground ownership; production data, purchases, provider cancellation, device time and security settings are outside scope.
- The controlled NavHost auth instance is never supplied to live billing. Distinct state instances permit testing Pro-only UI while the real billing query retains its authority to revoke unowned cached entitlements. Teardown resets synthetic entitlement and removes test subscriptions, including after failed assertions. No product entitlement override or replacement production DI layer is introduced.
- Exact-date DAO writes are fixture setup only; mutations being accepted, including budget confirmation and whole-record Undo, go through actual UI/repository callbacks. Fixture reminders are disabled. Tests do not substitute pure calculation output for navigation/window acceptance.
- The exported CSV receiver exists only in the test APK, accepts only the Debug FileProvider authority and CSV read grant, and saves into its own UID's private files. Replaying the real chooser with this owned initial target proves cross-UID local receiving/saving, not normal app ranking, arbitrary external destinations or cloud uploads. Remove the test APK after acceptance.
- Native capture preserves enabled accessibility services and requires exclusive ownership of the test UiAutomation connection; stop only the test-owned CLI instrumentation server, not user services. Accessibility text alone is not visual proof: the standalone receiver uses a no-ActionBar platform theme, explicit contrast and system-bar insets so saved-value/hash text is outside opaque window chrome; capture also awaits native idle. Actual TalkBack and Canvas geometry require separate evidence.
- `adb am instrument` can exit zero on failure/crash. The host runner accepts only the complete expected distinct success statuses plus JUnit and instrumentation success footers, and keeps native PNG evidence. Missing, failed or skipped cases are never silently treated as acceptance.

Dependencies and receiver components are confined to androidTest and must not leak into the production manifest, runtime dependencies or authorization behavior. Runtime recipes, observed results and explicit gaps live in `docs/testing/subscriptions-overview.md`.
