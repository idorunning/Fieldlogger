# Validation — 4 October 2026

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

Not yet verified: a real OpenAI identification, Pl@ntNet/BioCLIP inference, a hosted production deployment, physical Android camera/GPS/Background Sync, or an Android APK/AAB. No provider key has been created or written, and no private photo has been sent to a model provider during these checks.
