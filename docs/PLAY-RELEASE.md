# Play Console release preparation

App name: Field Logger. Package: com.field.logger. Release: 1.0.2, version code 3. Minimum SDK: 24, satisfying Play automatic protection's minimum-SDK requirement. Target SDK: 36. Category suggestion: Lifestyle. No advertising, tracking SDK, distance tracking or health measurements. Start with internal testing; no Play submission is authorized in this task.

Short description: Photograph and remember the small wonders you find outdoors.

Full description: Field Logger is your personal countryside discovery journal. Capture a flower, bird, insect, animal or landmark, keep the moment and its place, and find out more when connected. Photos save on your device first, then back up to your private account. Explore discoveries on a coloured map, browse your growing collection and celebrate curiosity through milestones. Share a photo or a photo with its story. Connect your own OpenAI API key to enable photo identification. Suggestions include uncertainty and references from Wikipedia and GBIF. Identification is not a guide to edibility. Map tiles and photo identification need a connection; saved photos and field notes remain available offline after the app has opened online once.

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

Generated phone screenshots are review artifacts. Produce Play screenshots from your release on a real phone, plus a 1024×500 feature graphic if requested by Play. The delivered AAB targets API 36 and contains no native .so libraries. Keep the signing backup private and add Play's app-signing certificate to Digital Asset Links after enrolment.
