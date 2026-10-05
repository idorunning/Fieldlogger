# My Trail Log company website and desktop journal

The homepage now introduces My Trail Log as a desktop-oriented product/company site. The existing desktop journal is preserved at `/journal`; its PWA manifest and shortcuts open that route. The public brand remains the readable Kalam-style My Trail Log wordmark with cream, forest, yellow and terracotta accents, bundled real woodland/wildlife imagery, and a compressed 53 KB native scrapbook screenshot.

## Routes

- `/`: product introduction, illustrative app preview, sharing/privacy explanation, final photo/review allowances and paid-plan prices, with checkout gated on verified Google Play readiness, operator information.
- `/journal`: original desktop journal with native-compatible illustrated avatar generation, verified membership usage, cloud ZIP download and separate local pending-photo backup, permanent achievements and clustered photo map.
- `/pricing`: final Free / Plus / Premium / Premium annual plans. Obsolete A/B/C proposals are removed. Pricing selection and actual purchase readiness are separate; final paid prices remain visible while checkout is unavailable.
- `/privacy`, `/terms`, `/cookies`, `/community-rules`: consistent legal pages covering location, private originals, optional publication, OpenAI processing/retention, portrait processing, contacts, moderation, subscriptions, rights and storage.
- `/support`: contact, practical FAQs, source/photo credits and safe support guidance.
- `/delete-account`: existing authenticated deletion with free-export guidance and an explicit warning that deletion does not cancel Google Play subscriptions.
- `/admin`: fresh authenticated administrative data, member search by the backend's `query` parameter, usage/status and audit detail, existing-account role/status changes with password reauthentication and confirmation, and owner-only Google Play setup.

The current operator is Nathan Tracey. Thinking About Ltd is explicitly a proposed, unincorporated company name. The authorised postal address is 422 Milton Road, Waterlooville, PO8 8LD, United Kingdom; public support is ntracey@gmail.com. No company number or incorporation claim is invented. The owner selected the B price points with hybrid Smart AI recognition and higher photo allowances. Final limits are Free 100 photos / 5 closer reviews monthly; Plus 150 / 30 for £5.99 monthly; Premium 300 / 60 for £11.99 monthly; Premium annual 300 / 60 monthly with 150 photo and 30 closer-review extras for eligible bonus photos per verified paid membership year, £119.99 yearly. Paid checkout remains gated on Google Play setup and purchase lifecycle checks.

## Cross-platform behaviour

`lib/achievements.ts` mirrors the native 352-badge catalogue and criteria. The fixture `tests/fixtures/achievement-catalog.json` was exported from the actual Java evaluator, and every ID, name, description, layer and goal is compared in a test. The six material levels, colours and thresholds match native. The website evaluates all owned entries including archives, persists a per-account permanent earned ledger in IndexedDB and unions it through `/api/social/achievements`. Guest adoption migrates earned badges; account deletion removes the local ledger. Synchronisation checks the current server-authenticated account before writing a ledger.

`lib/map-clusters.ts` mirrors the native stable screen-distance Mercator grouping, including neighbouring cells and the longitude seam. Leaflet renders real photo thumbnails with category outlines and cluster counts; zoom merges/splits clusters, and coincident photos remain selectable in an accessible stack. User names are inserted as text DOM nodes, never HTML. Blob URLs are revoked when the map unmounts.

Avatar creation uses the same `/api/avatar` service as native. The website resizes a chosen portrait to a metadata-free JPEG of at most 1024 pixels/2 MB before sending. It requires explicit consent and a separate Make my avatar action. The local source preview is discarded after success and only a server-derived generated avatar URL is displayed. No photo is submitted during selection/preview.

Smart AI identification can automatically escalate uncertain photos within a separate closer-review allowance. The desktop journal labels a tentative result without presenting it as certain, and offers an explicit “Try a closer look” action that posts to the owner-only identify endpoint with `closer=1`, without uploading the photo again or using another photo slot. An errored entry offers “Retry identification” through that same explicit endpoint; ordinary background sync keeps its query-free request and preserves a safe 503 error so the failure is visible. The backend’s durable stage markers decide whether a provider call can repeat. Reserved/failed closer attempts count. Membership renders monthly used/remaining, trial-wide totals and separate annual extras from the verified server response; absent usage is reported unavailable rather than invented. Trials use the selected photo and closer-review limits in total, even across UTC resets, and grant no annual extras.

Cloud ZIP downloads always remain available through `/api/export` independently of subscription readiness. The ZIP covers synced cloud photos including archives and account data; pending/device-only photos require the separate local JSON backup. Membership and policy copy explain this distinction.

The service worker uses distinct public/journal route caches, excludes every API request and `/admin` route, and cannot serve a cached journal as the company homepage or an offline administrator page. The cache version is bumped to v10 for the final catalogue and recognition interface.

## Verification

- TypeScript check passed after the integrated frontend/backend changes.
- 6 new achievement tests cover full native catalogue parity, thresholds, exact holiday/local dates, time windows, taxonomy, archive/deletion permanence and ledger union.
- 5 new cluster tests cover merge/split, stable representative selection, neighbouring cells, the date line, invalid coordinates and coincident pins.
- The 6 existing discovery domain tests also passed with the new achievement evaluator.
- Chromium QA used the already-installed browser and Playwright. Real public routes and anonymous administrator rejection, 1440 px desktop and 390 px responsive layout, image loading, no horizontal overflow, policy consistency and service-worker cache isolation passed.
- Real Leaflet QA confirmed broad-scale merging, cluster-click zoom splitting, coincident-stack selection, HTML-name escaping and zoom-out merging. Local achievement QA confirmed progressive materials and permanence after source-photo removal/reload.
- Controlled frontend fixtures tested administrator password-error retry, confirmation-dialog focus/Escape, member quota/ZIP disclosures, portrait consent/submission/preprocessing, owner payment setup save/disconnect and sensitive-field clearing, checkout gating, and secondary-admin 403 hiding. These are UI checks, not evidence of a real paid AI generation or Google purchase.
- The final-catalogue regression used the real local `/api/plans` response to verify all four prices, photo and review limits, removed proposal cards, gated checkout and consistent marketing/policies. Controlled member fixtures verified trial-wide photo/review totals without a calendar-reset refill, no annual extras during trial, and a tentative-only explicit closer retry that never uploads the photo again. A review-allowance error remains visible; a successful reply updates the local result and removes the tentative action.
- The final v10 service worker installed the current company/journal/pricing shells. API and administrator routes remained absent from the cache, and offline administrator navigation failed closed. The integrated TypeScript check passed after the closer-review UI and local helper changes.
- Failed-stage UI fixtures verified an explicit retry for both a stored error state and a safe 503 from ordinary background sync. The background request stayed query-free; the intentional retry sent `closer=1`, made no repeated photo upload, and updated the local result. Independent security review found no material issue in the final plan disclosures or owner-checked retry helper.

Screenshots and JSON evidence are in `/workspace/artifacts/my-trail-log-2.4.0-website/`. Actual backend security, provider generation, deployment and Google Play lifecycle checks are separate release evidence. The owned development server on port 8795 was stopped after the original QA. A final-catalogue regression checks the real public API, all four plan cards and the tentative review UI separately.
