# Play Console release preparation

App name: Field Logger. Package: com.field.logger. Release: 2.0.0, version code 6. Minimum SDK: 24, satisfying Play automatic protection's minimum-SDK requirement. Target SDK: 36. Category suggestion: Lifestyle. No advertising, tracking SDK, distance tracking or health measurements. Start with internal testing; no Play submission is authorized in this task.

Short description: Photograph and remember the small wonders you find outdoors.

Full description: Field Logger is your personal countryside discovery journal. Capture a flower, bird, insect, animal or landmark, keep the moment and its place, and find out more when connected. Photos save on your device first, then back up to your private account. Explore discoveries on a coloured map, browse your growing collection and celebrate curiosity through milestones. Share a photo or a photo with its story. Connect your own OpenAI API key to enable photo identification. Suggestions include uncertainty and references from Wikipedia and GBIF. Identification is not a guide to edibility. Map tiles and photo identification need a connection; saved photos and field notes remain available offline. The native app can capture photos offline from first launch.

Privacy policy: https://fieldlogger.co.uk/privacy
Account deletion: https://fieldlogger.co.uk/delete-account
Support: https://github.com/idorunning/Fieldlogger/issues

Data safety preparation (verify against your actual published configuration):
- Personal information: name and email for account authentication and journal ownership.
- Photos: user-selected photographs uploaded for private backup and identification. Camera/gallery access is user-initiated; no full photo-library indexing.
- Location: precise saved GPS is optional for a discovery and private map; approximate coordinates accompany OpenAI identification. No background location tracking.
- API key: optional user-supplied credential, encrypted server-side and never included in the Android package.
- HTTPS in transit. Private account ownership checks for journal data. User-initiated sharing/export. Account and stored-journal deletion available.
- OpenAI API processing is separate from Field Logger storage; store:false is set, but provider retention policies still apply. Wikipedia/GBIF get name queries and OpenStreetMap gets tile requests. No analytics or ad SDK is included.

The maintainer must select the appropriate Play Console collection/sharing declarations, review current provider terms and complete the console questionnaires. No declaration is pre-submitted by this repository. Reviewer access should use a disposable journal account with no personal discoveries; provide instructions for key-free capture/journal/map features. OpenAI identification requires a valid provider key and is not included as a free service.

Generated phone screenshots are review artifacts. Produce Play screenshots from your release on a real phone, plus a 1024×500 feature graphic if requested by Play. The native Android AAB targets API 36; validate any dependency native libraries for 16 KB page compatibility. Keep the signing backup private. Digital Asset Links includes both the original APK/upload certificate and the Play app-signing certificate supplied by the owner, for Android app links. Native version 2.0.0 renders its own interface and does not rely on Chrome verification to hide a toolbar.

Play app-signing public SHA-256: `1E:3D:F4:0B:2D:FA:B4:4D:96:A6:F5:54:00:83:78:B2:02:E2:28:86:7C:5B:83:35:1D:BE:62:BB:5F:19:1C:E2`. This is a public certificate fingerprint, not a private signing key. If Play rotates its app-signing key, add the new public fingerprint to `public/.well-known/assetlinks.json` while retaining fingerprints needed for older supported Android versions.

Native 2.0.0 introduces Android-owned photo storage, SQLite, CameraX and WorkManager. Sign in to the existing account to retrieve uploaded discoveries; import a web journal export for photos only stored locally in Chrome. Keep the public privacy policy aligned with the native storage behaviour before promoting the Play test release.
