# Field Logger

A mobile-first personal countryside discovery journal: photograph something, save the moment and place, then explore what it might be. The product rewards variety and curiosity, not distance or walking totals.

## Current status

The web app includes private API-key settings at `/#api-key`. Sign in or create your journal account, paste your key into the masked field, and choose **Save key**, then **Test connection**. Keys can be replaced or removed here later. No key needs to be sent in chat. Identification remains unavailable until a working key is configured; the app never invents successful analysis.

Account keys are encrypted with AES-256-GCM before storage. The encryption key is a Sites runtime secret, separate from the database, source and browser bundles. See `docs/API-KEY-SETUP.md` for operation and test coverage. Live OpenAI identification has been verified on the deployed app with an explicitly authorized temporary test key. Replace it through the private key settings when needed.

## Included

- Mobile camera/gallery capture; EXIF GPS/date where available; explicit GPS request and editable capture date.
- JPEG resize and EXIF removal, IndexedDB originals-as-resized, persistent-storage request, offline app shell.
- Automatic foreground upload on reconnect, startup and visibility changes; Background Sync uploads when the browser supports them. Photos are saved before any network request.
- Email/password registration, login, logout and account-scoped D1/R2 storage; adapted from the requested SEBP membership code. See `docs/SEBP-REUSE.md`.
- Private API-key settings with masked entry, encrypted account-scoped storage, replacement/removal, connection testing and expiry guidance.
- OpenAI Responses API identification with uncertainty, alternatives, capture context, visible clues, and a suggestion for looking closer.
- Wikipedia extracts and GBIF scientific-name matching, cached with the discovery. Optional Pl@ntNet and BioCLIP adapters; no optional service has been provisioned.
- Leaflet/OpenStreetMap map, category colours, accessible marker buttons and a corresponding list; search across names, notes and places; category and date filters.
- Category counts, scientific-name collection, month chart and eight curiosity-based achievements.
- User corrections and field notes; image-only sharing or a generated photo/story card. Exact GPS is not placed on shared images; place name is optional.
- JSON journal export including photos, GPS and metadata.

## Source and hosting

The source repository is [idorunning/Fieldlogger](https://github.com/idorunning/Fieldlogger), on `main`. Make changes in this repository. Sites still requires a matching source push to its deployment mirror and a build archive; pushing GitHub alone does not publish the website. Keep `.openai/hosting.json` and the existing database and image storage.

`fieldlogger.co.uk` is attached to the existing deployment, and its DNS and HTTPS certificate have been verified active. See [docs/DOMAIN-SETUP.md](docs/DOMAIN-SETUP.md) for the retained records. The user approved Field Logger password login as the sole app entry point for Android. Journal data and encrypted account keys remain private to each account.

## Run locally

```sh
npm ci
npm run db:generate # only after changing db/schema.ts
npx wrangler d1 execute DB --local --config wrangler.dev.jsonc --file drizzle/0000_brainy_bullseye.sql
npx wrangler d1 execute DB --local --config wrangler.dev.jsonc --file drizzle/0001_strange_virginia_dare.sql
npm run dev
```

The migration command initializes a new local database; do not rerun the same SQL against an already initialized database. The development URL is printed by Vite. After a production build, preview with:

```sh
npm run build
npx wrangler dev --config dist/server/wrangler.json --port 8787 --persist-to "$PWD/.wrangler/state"
```

Never package `.wrangler`, `.env*`, or local browser profiles. The app's D1 migrations are schema-only; QA accounts and public test photos were created through the local and deployed APIs; deployment output contains no seeded accounts or photos.

## Checks

```sh
npm run typecheck
npm test
node tests/api.integration.mjs # local Vite server, default localhost:5173
python tests/browser.py        # production preview, default localhost:8787
```

The browser acceptance test uses Playwright and `/usr/bin/chromium`, a disposable local account and a test photo. It covers offline save/reload, registration, automatic upload, GPS/map interaction, manual corrections, stats, achievements, sharing and filtering. Tests do not call OpenAI or the optional classifier services.

## Runtime configuration

Server secrets belong in ignored local environment files through approved secure setup, and in hosting secret configuration for deployment. No provider key belongs in browser JavaScript. Optional variable names are documented in `.env.example`; it contains no secrets.

The existing Site is `appgprj_6ac2d981043081919ee03d9ff57bf87e`; reuse `.openai/hosting.json`. Do not register a replacement. The website is publicly reachable, with account-scoped Field Logger login protecting journal data and saved keys. Logical storage bindings are `DB` and `BUCKET`. Production build emits `dist/server/index.js`, `dist/client`, and `dist/.openai` for hosting metadata, and `dist/drizzle` for migrations. The deployment archive puts the latter two at `.openai/` and `drizzle/` at its root.

The Sites skill's local helper scripts were not installed in this environment. SEBP source was retrieved over its authenticated source repository and used as a retained build scaffold. Publication uses an exact-source push and validated build archive followed by native Sites deployment. Registration alone is not a live URL; check the native deployment result for the published status.

## Offline limits

The web app must load online once before it can reopen offline. The native Android app can capture and save offline from its first launch. IndexedDB contains resized JPEGs, capture metadata and downloaded field notes. Browser storage can be cleared or evicted; synced storage and exports provide copies. Background work is controlled by Android/browser scheduling; force-stopping the app can defer upload until reopening. Map tiles are online-only in accordance with the standard OSM tile policy. A GPS fix can work offline but depends on device and permissions.

## Next gate

Test the signed Android release on the user's phone, including camera/GPS and offline reopening. The deployed provider, image storage, and identification pipeline have passed live checks. Android version 2.0.0 implements native screens, camera, storage and background uploads; see `docs/ANDROID.md`. The Android project now targets `com.field.logger`; see [docs/ANDROID.md](docs/ANDROID.md) for release signing, rebuild and Play Console instructions.

## Native Android app

The Android source in `android/` now builds Field Logger 2.0.0 as a native app. It uses CameraX, app-private SQLite/photo storage and WorkManager rather than launching Chrome. It connects to the existing account and identification APIs; the web application remains separately implemented. See [native release and migration instructions](docs/ANDROID.md).
