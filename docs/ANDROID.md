# My Trail Log native Android 2.4.0

Package **com.field.logger**, version code **11**, minimum Android 7/API **24**, target/compile SDK **36**. Original upload signing key retained. This is a native AppCompat/CameraX interface with private SQLite/photo storage and WorkManager, not a WebView/TWA or browser launcher.

## Behaviour

Home is a dated woodland diary: swipe layered photos horizontally within a day and scroll vertically through days. Bottom navigation stays Journal, Map, **Camera**, Collection and Milestones. Acorns sit at the lower-right of photo cards/details. Photo facts and branded story cards use saved identification context; shared stories omit exact GPS/EXIF and can hide the place.

Camera permission and foreground Location grants are reused. Brief fixes around capture can add/update coordinates and place labels even after a save. There is no background location permission/continuous tracking. Imported photos preserve known capture metadata; the phone's current GPS is not silently assigned to an older gallery photo.

Photos save before uploads/analysis. Existing account/password APIs are shared with the browser journal. Each account has isolated local data; Keystore protects the login cookie. Passwords and AI keys are not persisted in Android. Stale upload completions cannot replace newer pending edits. A capture draft can be resumed/discarded.

Archive retains originals, notes, GPS, dates and permanent badges while removing active journal/map visibility; archived discoveries remain in sync/exports. **352 achievements** use six material-coloured layers: Soil, Clay, Flint, Quartz, Amber and Gold. Existing badge IDs/earned records remain valid after the display-name change.

**Make my avatar** offers illustrated options and optional consented photo-to-cartoon creation. Portrait sources are JPEG resized/re-encoded to strip GPS/EXIF before sending privately to the service/OpenAI. My Trail Log never puts them in the nature journal or persistent storage. Only a checked generated illustration is served with the username. Provider retention terms apply. Free accounts have one initial attempt; verified paid/trial accounts have one per UTC month. A provider attempt can use the allowance even if it fails.

Settings groups identity, membership/community, journal, preferences and help/account. Manual Sync now does not block Camera. Weather/daylight backgrounds and brief rain/snow/wind effects can be disabled. Optional follower notifications use periodic WorkManager checks; Android controls timing.

## Map and community

Photo thumbnails merge into numbered clusters as the map zooms out and split apart as it zooms in. Filters include My photos, Following, Nearby, Everyone, Later and Invited, with category/tree, postcode/place/name, radius, top-acorn and photographed-area controls. A corresponding list remains accessible.

Publishing is an explicit audience choice: signed-in everyone, a selected browsed circle, or accepted email invitations. Following does not unlock private content. Each proposed image receives people/unsafe/private-information checks; uncertainty/failure stays private. Report/block/unpublish controls remain. Publish all is a confirmed one-off batch, not automatic publication of future captures. Unpublish all cancels queued work and revokes access.

Contacts permission is optional and followed by local review. Only selected addresses are sent for matching; names/full books are not server-stored. Public account identity is username, opaque ID and cartoon avatar, excluding account name/email/private notes. See [COMMUNITY.md](COMMUNITY.md).

## Subscription and exports

The native plans page integrates Google Play Billing and restore; the backend verifies account-bound purchases and serializes usage admission. Prices are finalised, while checkout stays disabled until Console products, secure credentials and real lifecycle tests are ready. A purchase callback or administrator role cannot grant paid entitlement.

| Plan | Photos / UTC month | Automatic Closer Looks / UTC month | UK price |
| --- | ---: | ---: | --- |
| Free | 100 | 5 | Free |
| Plus | 150 | 30 | £5.99/month |
| Premium | 300 | 60 | £11.99/month |
| Premium annual | 300 | 60 | £119.99/year |

Annual membership adds 150 photos and 30 Closer Looks per verified paid year, beyond a paid-year base cap of 3,600 photos and 720 Closer Looks. Eligible Google one-month trials have the selected plan's monthly limits as total trial limits and grant no annual bonus. Google's returned localized prices and actual offer eligibility control checkout; registration does not start a trial.

Ordinary photographs use an efficient AI pass; harder/uncertain photographs can receive one automatic stronger **Closer Look** within allowance and service budgets. Durable stage results are reused when unchanged processing is retried. When review is unavailable, preserve the first-pass result as tentative/broad with uncertainty. AI confidence labels are not calibrated species accuracy. Offline capture, existing journals/archive and free data downloads continue when usage is exhausted.

Native plan cards and usage read the server catalogue, including monthly review allowances and annual/trial balances. Review allowances count submitted attempts, including a provider failure. Tentative photo details explain the uncertainty and offer **Try a closer look** as an explicit retry; this reuses the existing cloud photo and completed first pass without another photo upload or charge. A still-unavailable review remains tentative, with no automatic retry loop. The camera remains usable while a requested review runs.

**Download all photos & data (ZIP)** streams the cloud account's active/archived originals, current cartoon and available metadata without a purchase check or photo charge. Unsynced local photos need sync or local backup. Local JSON backup/import remains separate. Exports contain precise saved GPS/private notes and must be kept private. Account deletion does not cancel Google Play billing; the app provides a manage/cancel link.

## Upgrade from the old browser wrapper

Native Android cannot read Chrome's private storage. Sync or export pending browser records before replacing the old TWA. Sign into the same account after updating to download cloud records. **Import web journal backup** transfers browser-only records, retaining IDs/photos/dates/places/notes; equal/newer local revisions are preserved.

Update through the existing Play testing track. The direct APK is upload-key signed and cannot update a Play-signed install. Do not uninstall and risk local pending photos.

## Build and validation

Use JDK 21, Android SDK/build tools 36 and the Gradle wrapper. Supply `ANDROID_HOME`, `JAVA_HOME`, `FIELDLOGGER_KEYSTORE`, `FIELDLOGGER_STORE_PASSWORD`, `FIELDLOGGER_KEY_PASSWORD` privately. Do not place credentials in source, logs or command arguments. PKCS12 upload alias: `fieldlogger`. Java time is desugared for API 24/25.

`python scripts/build-android.py` builds versioned signed AAB/APK artifacts. Run Gradle `testDebugUnitTest lintRelease bundleRelease assembleRelease` with the private signing environment. Framework tests cover SDK 24/36; DB tests cover account/offline isolation, late GPS, merge/revision protection and archive/badge persistence. Opt-in live contract tests must be distinguished from regular tests and paid provider calls.

Upload **my-trail-log-2.4.0-play.aab** to the existing Play application/test track. No automatic Play submission is performed. Before promotion, test physical capture/GPS/denial/offline sync, avatars, clusters, story sharing, archive and local/cloud exports on the Pixel. Complete the updated [Data safety](STORE-DATA-SAFETY.md), [reviewer access](STORE-APP-ACCESS.md) and [subscription setup](PLAY-SUBSCRIPTIONS-SETUP.md) guidance. Automated native screenshots do not replace physical-device or live-purchase verification.
