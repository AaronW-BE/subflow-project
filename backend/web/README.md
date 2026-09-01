# SubFlow Admin Console

The operator dashboard for the SubFlow sync server. React + Vite + Tailwind,
built to static files and **embedded into the Go binary** — there is no separate
deployment.

## Running it

```bash
cd backend && go run ./cmd/server
```

Then open http://localhost:8085/admin/ and paste the `ADMIN_TOKEN`. If you did
not set one, the server prints a random per-boot token in its startup log.

For UI work, run Vite instead so you get hot reload. It proxies `/api` to the Go
server on 8085, so keep that running too.

```bash
cd backend/web && npm run dev
```

## Shipping a change

`npm run build` type-checks, bundles, **and copies `dist/` into
`internal/static/dist`**, which is the directory `//go:embed` picks up. Skipping
it means the Go binary keeps serving the previous build — rebuild the server
afterwards:

```bash
cd backend/web && npm run build && cd .. && go build ./cmd/server
```

## Layout

| Path | What it holds |
| --- | --- |
| `src/api.ts` | Every call to the server. Throws `AdminAuthError` on 401/503 and `ApiError` otherwise, so no caller can mistake a failed write for a successful one. |
| `src/types.ts` | Wire types. Field names match the Go `json` tags exactly. |
| `src/components/ui.tsx` | Card, table, banner, modal and pagination primitives. |
| `src/views/` | One file per tab, plus the login screen and the preset form. |
| `src/App.tsx` | Shell: auth, data loading, and the mutation handlers. |

## Things worth knowing

- **All tables are paginated server-side** (50 rows). The endpoints return a
  `total` alongside the page, and the UI shows "showing N of M" rather than
  presenting a page as the whole table. The search boxes filter *loaded* rows
  only, which the Users tab says out loud.
- **The token lives in `localStorage`** and goes out as `X-Admin-Token`. Locking
  the console clears it.
- **Numbers on the dashboard are measured, not modelled.** If a figure cannot be
  derived from a table it is not displayed. Revenue is estimated from US list
  prices — that is the one estimate, and it is labelled as one everywhere it
  appears.
- **"Seed demo data" writes fake users** into whatever database the server is
  pointed at. It is confirmed first and the server refuses once real users
  exist, but it is a dev convenience, not an operator tool.
