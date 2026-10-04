# SEBP membership reuse

Inspected source version 13 of **SEBP Website Redesign**, commit `a0aa594e1aa283170e5ac478eb7d1ab68fcf72fa`.

Reused the account-provider pattern and email/password sign-in and registration flow from `app/components/AuthProvider.tsx` and `app/login/page.tsx`, along with the existing Vinext build and Cloudflare Worker scaffold. The original SEBP project was only read and has not been changed.

SEBP's source has a Supabase adapter, but its published login describes the live backend as unconfigured. Its hard-coded presentation-only logins are intentionally not copied into Fieldnotes. No SEBP member accounts, account data or secrets are copied.

For this personal app, the account provider calls Fieldnotes' own server routes. Passwords are salted PBKDF2-SHA256 hashes (100,000 iterations, Workers WebCrypto maximum); sessions use random 256-bit tokens, stored only as SHA-256 hashes in D1, with HttpOnly/SameSite=Strict cookies (Secure over HTTPS). Authentication attempts are rate limited, mutations reject cross-origin browser requests, and observation/image queries are scoped to the authenticated account. Password registration and sign-in do not require email verification. Email reset delivery is not implemented or offered.

The private Sites access boundary remains an additional layer. Moving to a publicly reachable Android backend requires an explicit access/configuration review, not republishing personal photographs publicly.
