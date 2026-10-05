# API-key settings

Open `/#api-key`, sign in or create a Fieldnotes journal account, and paste an OpenAI API key into the masked field. **Save key** explicitly enables the account's pending photo analysis. **Test connection** sends a small billed Responses request with the configured model. A successful save alone does not prove validity or permissions. The same screen replaces or removes a key. Removal does not revoke it at OpenAI.

Keys that expire after 20 minutes are useful for a short test, but they remain credentials until expiry. Never send them in chat. Expired, revoked and invalid keys return an actionable message; photo storage and offline journaling keep working. Enter a replacement in settings to resume pending identification.

## Storage and access

- The app-owned HttpOnly session is required for every settings operation. Writes and tests additionally require the exact same Origin. The deployed Site is owner-private.
- Keys are submitted over the Site's HTTPS endpoint, then encrypted with AES-256-GCM and a fresh 96-bit nonce before the D1 write. The user ID and envelope version are authenticated encryption context, preventing copying an encrypted key between accounts.
- `API_KEY_ENCRYPTION_KEY` is a base64-encoded random 32-byte server-only Sites secret. It is not the OpenAI key. It must never be committed or exposed via public variable prefixes, frontend code, runtime responses or logs. Do not replace it without migrating existing encrypted records; doing so makes them unreadable.
- Credentials are decrypted only in server memory for requests to the fixed OpenAI API origin. Client GET responses report presence and update time only, never key material or key fragments.
- The key field clears before submission, uses password masking, and has autocomplete, capitalization and spelling assistance disabled. No app code persists a key to browser storage, URLs, analytics, journal exports or logs. API responses are excluded from service-worker caches.
- Removing a key deletes its active database record. Normal hosting database backup retention still applies. Remove/revoke it at OpenAI to invalidate any previous copy.
- A saved account key overrides an optional server `OPENAI_API_KEY`. With no saved key, the optional server key is used. No fallback to a different key occurs after an expired/rejected account key.

## Local verification

The production encryption secret is separate from the deliberate fixture used for local tests. Start a local Worker preview with an isolated test-only `API_KEY_ENCRYPTION_KEY`, then run:

```sh
npm test
TEST_ORIGIN=http://localhost:8787 node tests/api.integration.mjs
node tests/key-settings.integration.mjs
python tests/key-settings.browser.py
python tests/browser.py
```

The test files contain explicitly fake credentials and never call OpenAI. Test connection and an actual identification must still be exercised with the user's real key through the private app.
