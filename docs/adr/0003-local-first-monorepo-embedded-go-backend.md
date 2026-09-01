# 0003. Local-First Monorepo with Embedded Golang Backend

We decided on a monorepo containing a native Kotlin/Compose client, a Golang REST API, and an embedded React-based Admin Console.

## Context
Mobile utility apps in international markets must launch in zero milliseconds, work flawlessly on airplanes or subways with no signal, and avoid loading spinners. Simultaneously, operating backend servers and admin portals should have minimal DevOps friction, low memory footprint, and trivial deployment.

## Decision
1. **Monorepo Layout**: Root directory houses `android/`, `backend/`, and `docs/`.
2. **Local-First Architecture**: Android client writes immediately to Room (SQLite) and DataStore. Network operations are background sync tasks using versioned timestamps. Unauthenticated users have 100% full feature parity up to the 5-subscription quota without ever needing an internet connection.
3. **Golang Single Binary**: The Go backend embeds the production build of the Admin Web Console (`backend/web/dist`) via `embed.FS`. A single executable provides both the RESTful API on `/api/v1` and the full Admin Console on `/admin`.

## Consequences
Guarantees zero-lag UI responsiveness regardless of overseas network latency. Infrastructure hosting cost is nearly negligible (runs comfortably on a $5/month VPS or Cloud Run).
