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
