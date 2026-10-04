# Android phase

The user requested a working web app before an Android bundle. This phase remains pending the first live identification and phone verification. There is no Android binary yet.

The web app already includes a Web App Manifest, 192/512 px icons, a service worker, a camera input, geolocation, native Web Share when available and a mobile navigation layout. It can be installed from Chrome after being hosted on HTTPS.

For a private single-user Android installation, provide a signed APK alongside the AAB: an AAB is a publishing bundle and cannot be installed directly by tapping it. Do not submit to the Play Store unless asked.

After the web app passes a real-device check:

1. Keep the existing backend, data model and same-origin session cookies.
2. Wrap the HTTPS app as a Trusted Web Activity to retain Chrome's offline storage and camera/share behaviour; test the private Sites sign-in boundary carefully. If that boundary prevents a reliable standalone experience, keep the verified PWA and resolve a suitable app-owned HTTPS host before packaging.
3. Generate the Android project with a stable application ID (proposed `uk.fieldnotes.journal`), Android camera/location permissions only when needed, adaptive icons and deep-link handling.
4. Use a persistent user-approved signing key. Never commit or embed signing passwords, provider keys, cookies or session tokens.
5. Host the generated Digital Asset Links file for the actual package and certificate fingerprint; verify the relationship on device.
6. Test camera permission denial, gallery EXIF, inaccurate/missing GPS, airplane-mode cold starts, app termination/relaunch, duplicate upload prevention, expired login and share-sheet behaviour.
7. Build a release AAB and a signed APK for direct installation; document the signing-key backup and app version.

Do not claim native background upload guarantees until tested on a real Android device. Chrome Background Sync and TWA scheduling can defer work; explicit retry on foreground is the reliable baseline.
