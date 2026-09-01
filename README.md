# SubFlow

An Android app for tracking subscriptions you already pay for. You enter them by
hand; the app normalises every billing cycle to a monthly figure, converts
currencies, and reminds you before a renewal charge lands.

It has no connection to your bank, your card, or the services you track, and it
cannot cancel anything on your behalf. That constraint is the product decision,
not a limitation — see [ADR 0001](docs/adr/0001-privacy-first-manual-tracking.md).

**Status:** unreleased. Bundle `1.0.0 (2)` is built and verified against Play's
Android 16 and Billing 9 requirements; closed testing has not yet reached the
12-tester threshold.

## Layout

| Path | What it is |
| --- | --- |
| `android/` | The app. Kotlin, Jetpack Compose, Room. This is the deliverable. |
| `backend/` | A Go + SQLite sync server and its React admin console. **Not reachable from the released app** — `BACKEND_ENABLED` is false unless `subflow.apiBaseUrl` is set at build time. |
| `docs/adr/` | Why the project is shaped the way it is. Read these first. |
| `docs/google-play-release-checklist.md` | The release log. Long, and the authoritative record of what was verified on device and what was not. |
| `play-assets/` | Store listing artwork. |
| `CONTEXT.md` | Domain glossary — the words the code uses and the ones it deliberately avoids. |
| `DESIGN.md` | The design system the UI implements. |

## Building the app

Requires JDK 17. Gradle 9.3.1, AGP 8.13.2 and Kotlin 2.2.21 come from the
wrapper and the version catalog.

```bash
cd android && ./gradlew assembleDebug
```

The debug build is self-contained: it uses Google's official **test** AdMob unit
ids, so it can never serve or bill a live ad, and it installs alongside a release
build under `org.dpdns.alwaysup.subflow.debug`.

### Release builds

Two things are deliberately absent from this repository, because neither can be
undone once leaked.

**Signing.** Create `android/keystore.properties` from the template next to it.
Without it the build silently falls back to the debug key, which Play rejects.

```bash
keytool -genkeypair -v -keystore android/subflow-release.jks \
        -alias subflow -keyalg RSA -keysize 4096 -validity 10000
```

**Monetization identifiers.** Passed as Gradle properties so no live id is ever
committed. Put them in `~/.gradle/gradle.properties`, or on the command line:

| Property | Effect if unset |
| --- | --- |
| `subflow.admobAppId` | Google's test app id — no live ads |
| `subflow.admobBannerUnitId` | Google's test banner unit — no live ads |
| `subflow.apiBaseUrl` | `BACKEND_ENABLED=false`; the app makes no network call at all |
| `subflow.googleWebClientId` | Sign in with Google is hidden rather than shown broken |

```bash
cd android && ./gradlew bundleRelease
```

## Running the backend

```bash
cd backend && go run ./cmd/server
```

| Env var | Default |
| --- | --- |
| `JWT_SECRET` | random per boot, logged — sessions do not survive a restart |
| `ADMIN_TOKEN` | random per boot, logged |
| `DB_PATH` | `subflow.db` in the working directory |
| `PORT` | `8085` |

Set `JWT_SECRET` for anything long-lived. It signs session tokens, so whoever
holds it can forge a login for any user.

The admin console is served at `/admin/` and gated by `ADMIN_TOKEN`. It lives in
`backend/web` (Vite + React) and is embedded into the Go binary under
`internal/static/dist`, so a rebuilt console needs `npm run build` before
`go build` — see [backend/web/README.md](backend/web/README.md).

## What is not in the repository

`.gitignore` excludes the upload keystore and its passwords, `local.properties`,
build output, `node_modules`, the compiled Go binary, and the local SQLite
database with its WAL sidecars. Losing or leaking the upload key cannot be
reversed, so it is kept out on purpose rather than by habit.

The one deliberate exception is `backend/internal/static/dist` — the built admin
console. `//go:embed` treats a pattern that matches nothing as a compile error,
so without it checked in the Go server cannot be built at all without first
installing Node and running the Vite build.
