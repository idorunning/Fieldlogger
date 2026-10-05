# Validation — 5 October 2026

Executed against the actual local Cloudflare Worker preview with D1 and R2 emulation, not an in-memory backend mock.

- TypeScript validation: passed.
- Production Worker/client build: passed, including offline asset precache manifest.
- Six domain tests: repeated species, unconfirmed low-confidence species, missing/zero GPS, curiosity milestones, wire payload privacy and genus-only labels.
- Server integration: real registration, HttpOnly session, invalid-password rejection, authenticated photo upload/retrieval, retry idempotency, owner isolation, rejection of client-fabricated reference evidence, explicit missing-API-key status, cross-origin mutation rejection, logout revocation.
- Browser acceptance on a 390 × 844 phone viewport: offline photo/GPS capture, IndexedDB persistence, complete offline page reload, real registration and adoption of device-local photos, automatic upload, coloured map markers and accessible details, naming correction, species collection, milestones, search/date filters, generated share-card download, signed-in offline queue surviving reload, automatic upload after reconnection, no uncaught browser errors or horizontal overflow.
- Desktop 1440 px and phone 390 px visual checks completed. Text-enlargement inspection found crowding in the bottom navigation; controls were changed to wrap and expand. Rechecking at 200% text size found no label overlap or horizontal overflow.
- Live public references: GBIF returned an EXACT match for Hyacinthoides non-scripta (5304283); Wikipedia returned an article extract.
- Optional BioCLIP service: Python syntax validation only; model dependencies and weights have not been installed or exercised.
- Browser WebMCP is feature-detected. Supported-context registration validation is unavailable in the test browser; this does not affect normal controls.

Live deployed verification passed after correcting deployment archive migration placement: registration, OpenAI connection, R2 photo upload/retrieval with an exact SHA-256 match, and real OpenAI identification of a public test bluebell photograph with Wikipedia and GBIF references. The temporary key was explicitly authorized for testing and configured as a hosting secret; it is absent from source and build output. No private user photo was used.

Not yet verified: Pl@ntNet/BioCLIP inference, physical Android camera/GPS/Background Sync, or an Android APK/AAB. The custom domain is pending DNS and certificate validation.

## Private API-key settings

- TypeScript, production build, and all nine domain/security tests passed.
- AES-GCM round trip, per-owner additional authenticated data, wrong-master rejection, random nonce per write and tamper rejection tested.
- Local D1 integration passed: authenticated create/replace/remove, cross-account isolation, no credential in responses, no-store response headers, strict Origin enforcement (including missing Origin), JSON/payload validation, and no-key connection test.
- Mobile browser passed: sign-in gate, masked entry, field clearing, persisted status after reload, replacement, expired-key message, confirmed removal, offline lockout, no key in IndexedDB/localStorage/sessionStorage, no overflow or uncaught browser errors.
- Full prior photo/offline browser suite and server integration rerun and passed after this change.
- All key tests used deliberate non-provider fixtures. The expired-key UI response was simulated; backend status mapping was unit-tested. Those key-settings regression tests do not call OpenAI. Separate live connection and photo tests subsequently passed as recorded above.

## Android and phone redesign

- Signed Android AAB and APK compiled successfully for com.field.logger, version 1.0.0 (1), target SDK 36/minimum 23. Release lint passed. Bundletool accepted the AAB structure; JAR signature and APK v1/v2 signatures verified; certificate matches Digital Asset Links. No native .so libraries are included.
- Phone design checks passed at 360×800, 390×844 and 430×932: camera visible without scrolling, controls at least 48 px high, direct save without optional metadata, large journal/detail photos, real image loading, no horizontal overflow at 200% text size and no browser errors.
- Full offline capture/reload, GPS, account adoption, reconnect upload, map, correction, stats, milestones, search, dates and photo/story sharing passed after the photo-first redesign.
- Password-protected account deletion passed origin and password rejection, session revocation, account/key/journal removal and owner isolation. R2 confirmed the deleted test photo key no longer exists.
- User approved removing the additional ChatGPT site gate; private Field Logger accounts remain required for synced data. The shared temporary testing key is removed from deployment configuration.
- Android camera, GPS, fullscreen browser handoff and background scheduling have not been exercised on physical hardware. Google Play submission is not part of this build. Play's app-signing certificate must be added to the site's asset links when Play generates a different signing key.
