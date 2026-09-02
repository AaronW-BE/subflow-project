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

Settings come from a TOML file, the environment, or both. **The environment
wins**, so a deployment can override one value without rewriting a file it may
not be able to edit, and a leaked credential can be rotated without touching
disk.

```bash
cp backend/subflow.config.example.toml backend/subflow.config.toml
cd backend && go run ./cmd/server -config subflow.config.toml
```

Without `-config`, the server reads `$SUBFLOW_CONFIG`, then
`subflow.config.toml` in the working directory if it happens to exist. A file
named explicitly but missing is a startup error — a typo in `-config` should not
silently start a server with none of the settings you meant to pass. Unknown
keys are rejected for the same reason: `jwt_secrets` would otherwise look like a
working config right up until something failed to authenticate.

| Setting | Env var | Default |
| --- | --- | --- |
| `port` | `PORT` | `8085` |
| `db_path` | `DB_PATH` | `subflow.db` in the working directory |
| `jwt_secret` | `JWT_SECRET` | random per boot — sessions do not survive a restart |
| `admin_token` | `ADMIN_TOKEN` | random per boot, printed at startup |
| `exchange_rate_api_key` | `EXCHANGE_RATE_API_KEY` | unset — uses the open, keyless rate endpoint |

Set `jwt_secret` for anything long-lived. It signs session tokens, so whoever
holds it can forge a login for any user.

`exchange_rate_api_key` is optional. Left unset, the server uses
ExchangeRate-API's open endpoint, which needs no account but requires visible
attribution wherever the rates are shown. Setting it switches to the keyed
endpoint, which drops that requirement. The key appears in the request path, so
it is scrubbed from anything the server logs.

Note that the **Android app fetches rates directly from the keyless endpoint**
and is unaffected by this setting, so the in-app attribution stays regardless.

`subflow.config.toml` holds all three secrets and is git-ignored; only
`subflow.config.example.toml` is tracked. The startup log reports which settings
are present, never their values.

The admin console is served at `/admin/` and gated by `ADMIN_TOKEN`. It lives in
`backend/web` (Vite + React) and is embedded into the Go binary under
`internal/static/dist`, so a rebuilt console needs `npm run build` before
`go build` — see [backend/web/README.md](backend/web/README.md).

## What is not in the repository

`.gitignore` excludes the upload keystore and its passwords, `local.properties`,
`subflow.config.toml` and its secrets, build output, `node_modules`, the
compiled Go binary, and the local SQLite database with its WAL sidecars. Losing or leaking the upload key cannot be
reversed, so it is kept out on purpose rather than by habit.

The one deliberate exception is `backend/internal/static/dist` — the built admin
console. `//go:embed` treats a pattern that matches nothing as a compile error,
so without it checked in the Go server cannot be built at all without first
installing Node and running the Vite build.
