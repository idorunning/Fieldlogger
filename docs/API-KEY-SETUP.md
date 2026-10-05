# My Trail Log: private identification-service setup

Ordinary members do not enter, view or maintain provider keys. The website and Android app use the same privately configured identification service; the key is never bundled in an APK/AAB or sent to clients. Manage provider permissions, rotation and billing through the private OpenAI project and the hosting platform's server-secret configuration.

## Active service and owner maintenance

`getServiceKey` uses the server's `OPENAI_API_KEY` first. If absent, it uses the encrypted account key belonging to `SHARED_OPENAI_KEY_OWNER_ID`. Configure that value to an existing trusted operator account. Avoid operating a shared public application without one of these private service configurations.

The legacy `/settings/openai-key` endpoint exposes no credential material through GET. Every mutation or connection test requires a signed-in account that exactly matches **both** `SHARED_OPENAI_KEY_OWNER_ID` and `COMMUNITY_ADMIN_USER_ID`; missing or mismatched configuration fails closed. Email addresses and new account registration do not grant this permission. Writes and tests additionally require the exact same Origin.

An owner connection test uses the active shared service key, GPT-6.1 Sol, a tiny known JPEG and a bounded complete response. It has a ten-second request limit, a conservative cost reservation and actual usage settlement. Passing this checks image-input connectivity; it does not establish species-identification accuracy or replace field-photo quality tests. A successful save alone does not prove provider permissions.

## Storage and privacy

- `API_KEY_ENCRYPTION_KEY` is a base64-encoded random 32-byte server-only secret. It is separate from the provider key. Never replace it without migrating existing encrypted records.
- Saved owner keys use AES-256-GCM with a fresh 96-bit nonce and authenticated owner/envelope context, preventing reuse of an encrypted envelope by another account.
- Decryption occurs only in server memory for the fixed provider origin. Do not put credentials in public variables, browser storage, URLs, logs, analytics, screenshots or ZIP exports.
- Presence responses contain no key or key fragments. Photo-analysis failures return generic service messages to members; safe status/category diagnostics stay in server logs.
- Deleting an encrypted record does not revoke its credential at OpenAI, and an active `OPENAI_API_KEY` takes precedence over that record. Rotate/revoke at the provider and update the active private server configuration together.
- Photo capture, existing private-journal access and exports remain available when identification is temporarily unavailable.

## Verification

`npm test` exercises encryption ownership/tampering, trusted-owner restrictions, generic member errors, image preprocessing and transactional spend safeguards using deliberate non-provider fixtures. Type checking and local tests never establish live provider access. Real owner connectivity and representative nature/people-safety tests must be performed privately with the configured service. Older key-settings browser/integration scripts describe the retired member-key UI and should not be treated as current production acceptance evidence.
