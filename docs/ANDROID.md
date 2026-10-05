# My Trail Log native Android release

Package **com.field.logger**, version **2.2.0**, version code **9**. Minimum Android 7/API 24; target and compile SDK 36. The existing upload signing key is reused.

This release replaces the Trusted Web Activity with an Android interface written with AppCompat views. It has no WebView, browser launcher or downloaded HTML/JavaScript interface. CameraX provides camera preview, capture, tap-to-focus, pinch zoom and flash control. The app uses its own SQLite database and app-private photo files. WorkManager schedules connection-dependent background uploads and periodic retries; Android controls exact execution timing.

## Behaviour

- Home is a dated diary over the bundled woodland photograph, with overlapping paper photo stacks, horizontal swipes, accessible page-turn buttons and light haptics. Vertical scrolling moves between days. Native bottom navigation: Journal, Map, Camera, Collection and Milestones.
- Photos are resized to JPEG and EXIF removed, while original capture time and embedded GPS are saved separately. Gallery photos without EXIF are not assigned the phone's current coordinates.
- Camera and foreground location grants are reused. Location requests stop after a short capture window; there is no background location permission or continuous walk tracking.
- A late GPS fix updates the captured discovery, including after saving. Nearby place names use the existing Photon/OpenStreetMap server endpoint, with local caching and reconnect retries. Entered place names are preserved.
- Existing My Trail Log password login, registration and server-side encrypted OpenAI keys are reused. The native login cookie is encrypted using Android Keystore. Passwords and OpenAI keys are not stored in the app.
- Uploaded discoveries download through the same account. Each local record is separated by account. Upload completions and remote merges cannot replace newer pending edits.
- Native discovery detail, corrections, notes, date/place filters, category collection, species statistics, milestones, OpenStreetMap map, image sharing and story sharing are included. Exact GPS is omitted from shared story cards.
- A partially captured photo is retained as a draft and can be resumed or discarded after returning to Camera.
- Archive keeps photos, notes, dates and coordinates while removing them from the active journal, map and counts. Restore through Archive in account settings; archives sync and remain in exports. Older clients that omit the archive flag cannot inadvertently restore it.
- Photo & story creates a 1080×1350 branded JPEG with a large photo, category, short summary, interesting saved context, capture time and place by default. A preview can hide the place; precise GPS and EXIF are omitted.
- The native app has no key icon, credential entry or service-key wording. Existing server configuration is retained; account administration remains on the desktop website.
- Account settings provide JSON export/import and account deletion. Public references open their respective websites only when selected.

## Community discoveries

Photo cards have accessible acorn toggles. The full-screen native map uses photo-thumbnail pins and My photos, Following, Nearby, Everyone, Later and Invited filters, with category/radius controls and acorn ranking. Map results also have a list view. Open a journal photo to publish it, or use account settings for your username, selected-contact search, published photos, blocking and saved places. Shared user journals use dated scrapbook stacks. The normal Journal/Map/Camera/Collection/Milestones bar remains. See [community access and moderation](COMMUNITY.md) for service configuration, privacy rules and the verified shared GPT-6.1 Sol service.

## Moving from the web app

The old TWA stored local data and sessions in Chrome. Native apps cannot read another app's private storage. Before updating, open the old app or fieldlogger.co.uk in the same browser, reconnect, and let pending photos upload. Export the web journal for an additional backup, especially for device-only or queued photos.

Update through the same Google Play testing track. Sign in once with your existing My Trail Log account; uploaded discoveries download automatically. To transfer photos that only exist in browser storage, select **Import web journal backup** in the native account/login screen and choose the exported JSON. The import retains observation IDs, photos, dates, coordinates, names and notes. Records already present at an equal/newer revision are retained. The old browser journal is not deleted by the native app.

## Build

Use JDK 21, Android SDK 36, build tools 36.0.0 and the Gradle wrapper. Supply `ANDROID_HOME`, `FIELDLOGGER_KEYSTORE`, `FIELDLOGGER_STORE_PASSWORD`, `FIELDLOGGER_KEY_PASSWORD` and `JAVA_HOME` through a private process environment. Do not put secrets in source, logs or command arguments.

Run `python scripts/build-android.py` from the repository. It produces versioned signed AAB/APK files in the sibling `artifacts` folder. Signing alias `fieldlogger`, PKCS12 upload key. Java time support is desugared for API 24/25.

Run `android/gradlew testDebugUnitTest` from the Android project. The framework tests exercise native startup and navigation on SDK 24 and 36, and database tests exercise offline storage, account isolation, late GPS, stale-upload protection, manual place protection and analysis merging. An opt-in live API contract test creates/uploads/deletes its own throwaway account without configuring an OpenAI key; enable with `FIELDLOGGER_NATIVE_LIVE_TESTS=1`.

## Google Play

Upload **my-trail-log-2.2.0-play.aab** to the existing application and testing track. Package and upload key match prior releases; Play signs installed updates with the account's existing app-signing key. The separately delivered direct APK uses the upload key and cannot update a Play-signed installation.

Review Data safety for the native release: account details, photos, capture times, optional foreground location, background upload and optional OpenAI analysis. Keep the privacy and deletion URLs. Test camera/location denial, capture, offline reopen, reconnect, signing into the existing account and sharing on the Pixel before promoting the test release. The workspace's release report distinguishes automated checks from physical-phone checks.

The website now uses the My Trail Log name, handwritten wordmark and brighter nature palette. There is no automatic Google Play submission.
