# Field Logger Android release

Package: **com.field.logger**. Version: **1.0.0**, version code **1**. Minimum Android: 6 (API 23). Target and compile SDK: Android 16 (API 36).

The Android app uses Google's Android Browser Helper 2.7.3 to open `https://fieldlogger.co.uk` as a Trusted Web Activity. This retains Chrome's camera/gallery intents, sharing, private account cookies, offline app shell and IndexedDB journal. The website must load online once before offline use. Reopening returns the saved shell immediately while refreshing it in the background; API responses and photos remain account-scoped. Uploads resume when the app is foregrounded and connected; Android/browser background scheduling is not guaranteed.

The phone interface opens on a full-screen woodland photograph with a large camera action and gallery button. Journal cards are large photographs; capture offers direct save and optional metadata; identification detail keeps the photo above expandable background information. Bottom navigation and capture controls support touch. Real source photos and credits are documented in `PHOTO-CREDITS.json` and `woodland-source.json`.

## Build again

Install Java 17 or 21 with a compiler, Android platform 36 and build tools. Set `ANDROID_HOME` to the SDK. Gradle 8.13 is pinned by the wrapper with its official SHA-256 checksum. AGP 8.13.2 may install its required build-tool version after licence acceptance.

Supply these variables through a private environment or secure CI secret configuration:

- `FIELDLOGGER_KEYSTORE`: absolute path to the persistent PKCS12 key file.
- `FIELDLOGGER_STORE_PASSWORD`: keystore password.
- `FIELDLOGGER_KEY_PASSWORD`: private-key password (same password for the delivered PKCS12 backup).

Run `python scripts/build-android.py`. Signing alias: `fieldlogger`. Increment `versionCode` and `versionName` for subsequent Play uploads. Never put signing credentials in source, command arguments, the app or the website. The release build refuses to proceed if signing variables are missing.

## Signing and backups

The release key is generated once and reused. Its public SHA-256 certificate fingerprint is in `public/.well-known/assetlinks.json`. The private signing backup is delivered separately and is excluded from the public repository and web archive. Download and keep that backup privately; it is needed for future releases. The AAB is signed for upload, and the APK is signed for direct installation. An AAB cannot be installed by tapping it.

## Google Play

1. Create the Play Console application and upload `fieldlogger-1.0.0-play.aab` to internal testing first.
2. Enrol in Play App Signing. If Play generates an app-signing key, copy the **app-signing certificate** SHA-256 from Play Console's App integrity page and add it to the website's existing `sha256_cert_fingerprints`. Keep the delivered APK/upload certificate too. Otherwise the Play-installed app will show browser controls rather than the full-screen interface. A public certificate fingerprint can be shared for this step; never share a private key or password.
3. Set the privacy-policy URL to `https://fieldlogger.co.uk/privacy`, and account-deletion URL to `https://fieldlogger.co.uk/delete-account`. Complete Data safety and content rating accurately: account details, photos and optional saved location; OpenAI receives photos and approximate location only after key setup. See `PLAY-RELEASE.md`.
4. Test camera and location permission denial, offline reopen, gallery import, reconnect upload and sharing on an actual phone before requesting production review.

This work produces release files; it does not submit to Google Play. Google Play review and account-specific testing requirements remain separate. Native fullscreen presentation, physical camera/GPS, and background scheduling need device confirmation.
