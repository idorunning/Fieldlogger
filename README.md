# Fieldnotes

A mobile-first personal countryside discovery journal: photograph something, save the moment and place, then explore what it might be. The product rewards variety and curiosity, not distance or walking totals.

## Current status

The web app is implemented and runs locally. Its private Sites identity is registered but **has not been published**. OpenAI key creation was approved in the conversation, but the secure picker has not delivered a key/account selection and the local secret destination still needs confirmation. There is no live OpenAI key in this checkout. Identification returns an explicit unavailable status until secure setup completes; it never invents a successful analysis.

## Included

- Mobile camera/gallery capture; EXIF GPS/date where available; explicit GPS request and editable capture date.
- JPEG resize and EXIF removal, IndexedDB originals-as-resized, persistent-storage request, offline app shell.
- Automatic foreground upload on reconnect, startup and visibility changes; Background Sync uploads when the browser supports them. Photos are saved before any network request.
- Email/password registration, login, logout and account-scoped D1/R2 storage; adapted from the requested SEBP membership code. See `docs/SEBP-REUSE.md`.
- OpenAI Responses API identification with uncertainty, alternatives, capture context, visible clues, and a suggestion for looking closer.
- Wikipedia extracts and GBIF scientific-name matching, cached with the discovery. Optional Pl@ntNet and BioCLIP adapters; no optional service has been provisioned.
- Leaflet/OpenStreetMap map, category colours, accessible marker buttons and a corresponding list; search across names, notes and places; category and date filters.
- Category counts, scientific-name collection, month chart and eight curiosity-based achievements.
- User corrections and field notes; image-only sharing or a generated photo/story card. Exact GPS is not placed on shared images; place name is optional.
- JSON journal export including photos, GPS and metadata.

## Run locally

```sh
npm ci
npm run db:generate # only after changing db/schema.ts
npx wrangler d1 execute DB --local --config wrangler.dev.jsonc --file drizzle/0000_brainy_bullseye.sql
npm run dev
```

The migration command initializes a new local database; do not rerun the same SQL against an already initialized database. The development URL is printed by Vite. After a production build, preview with:

```sh
npm run build
npx wrangler dev --config dist/server/wrangler.json --port 8787 --persist-to "$PWD/.wrangler/state"
```

Never package `.wrangler`, `.env*`, or local browser profiles. The app's D1 migrations are schema-only; QA accounts and photos exist only in the local emulator, not deployment output.

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

The existing private Site is `appgprj_6ac2d981043081919ee03d9ff57bf87e`; reuse `.openai/hosting.json`. Do not register a replacement. Logical storage bindings are `DB` and `BUCKET`. Production build emits `dist/server/index.js`, `dist/client`, and `dist/.openai` including migrations.

The Sites skill's local helper scripts were not installed in this environment. SEBP source was retrieved over its authenticated source repository and used as a retained build scaffold. Publication still needs exact-source push and a validated archive, then a successful native Sites deployment result. Registration alone is not a live URL.

## Offline limits

The app must load online once before it can reopen offline. IndexedDB contains resized JPEGs, capture metadata and downloaded field notes. Browser storage can be cleared or evicted; synced storage and exports provide copies. Background work is controlled by Android/browser scheduling; force-stopping the app can defer upload until reopening. Map tiles are online-only in accordance with the standard OSM tile policy. A GPS fix can work offline but depends on device and permissions.

## Next gate

Finish secure OpenAI setup and test an actual photo through the full provider pipeline, then publish privately and test on the user's phone. The Android bundle is the next phase after that working web version; see `docs/ANDROID.md`. No `.aab` or installable `.apk` has been generated yet.
