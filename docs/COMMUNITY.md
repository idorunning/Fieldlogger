# My Trail Log community — 2.2.0

The native app shares individual approved snapshots; private observations are never a public feed. Desktop journal design remains separate. The website supplies invitation acceptance, privacy and sharing guidelines alongside the API.

## Service and owner administration

All authenticated accounts use the shared server credential. Production `SHARED_OPENAI_KEY_OWNER_ID` points to the existing owner's encrypted account credential; `COMMUNITY_ADMIN_USER_ID` identifies the report moderator. Neither value is a secret. `API_KEY_ENCRYPTION_KEY` remains secret. A runtime `OPENAI_API_KEY` can alternatively supply the service credential. No OpenAI credential is embedded in Android, returned to users, or added to exports. Only the shared owner can change the saved account credential. OpenAI project billing/limits are managed in OpenAI Platform.

The current vision model is **GPT-6.1 Sol**, with low reasoning effort and high-detail images. The existing shared key permits this model. Requests reserve output room for both reasoning and the structured answer (4,000 tokens for identification; 1,600 for publication review). The earlier 4.1 mini default was not permitted by this project and has been replaced. No credential change was required.

Live validation on 5 October 2026 passed: a new account used the shared key for nature identification, a nature photo passed publication, a human portrait was blocked, an invite required acceptance by its intended account, acorns were unique, bookmarks persisted, editing the private original did not replace approved public bytes, and unpublishing revoked old invitation links. These limited fixtures validate the integration, not universal species accuracy or perfect moderation.

## Audiences and privacy

- Everyone: visible to signed-in users.
- Local: visible when the viewer's chosen browsing centre is inside the owner's chosen circle. This is deliberately not proof of physical proximity. Radius is 0.5–100 km in the API, 1–100 km in the native picker.
- Specific people: a per-photo email list. Access requires both the secret invitation link and a signed-in account with the matching email. Merely registering an email address grants no access. Email composition is per recipient, with no shared recipient list.
- Profiles expose username and opaque account ID. Account name, email, private notes and contact lists are excluded. Photo location, date/time and story are included with explicit publication consent.
- Email search is optional and defaults off. Android's single-email contact picker grants access to the selected address; no READ_CONTACTS permission or bulk address-book upload is used.
- Following does not bypass audience rules. Following/blocking lists and saved places are private. Acorn counts are visible, one per account per photo.

Archiving while offline queues unpublishing for the next sync; the native confirmation says so. Direct Unpublish requires a connection.

The server checks every feed item, photo detail, image, acorn, bookmark and report against audience and blocks. An immutable, metadata-stripped JPEG and allowlisted text snapshot are created only after the publishing check. Editing/re-uploading the private original cannot replace the shared bytes. Generation tokens prevent an in-flight check resurrecting a publication after unpublishing or archiving. Public images use private/no-store responses; the native loader disables shared-photo memory/disk caches.

## Moderation

Publishing requires agreement to `/community-rules`. The AI check blocks real people, unsafe content, visible private information, uncertainty and failed/malformed checks. It is fallible; users can report and block. Reports immediately hide a publication pending review. The owner sees Review reported photos in Community profile. Removing prevents republishing that observation; restoring runs the AI check again. Photos blocked from sharing remain in the private journal. Account deletion cascades social records and removes private/public storage objects.

## Verification

Run `npm run typecheck`, `npm test` and `npm run build`. `tests/integration/local-community.py` runs against a local Vite server on port 8787 and project-local D1, using Python requests. Its approved snapshots are **local test fixtures only**; no production moderation bypass exists. Checks cover invitation possession, local radius, blocked/direct URLs, unique acorns, private saved places, opt-in contact discovery, report hiding and revocation.

Native `CommunityActivityTest` checks API 24 and 36 map filters, full-screen mode, account gating and accessible offline acorn persistence. `ApiContractTest` opt-in live checks confirm new accounts receive shared service configuration and cannot change its credential. Screenshot renders use licensed demonstration photos, never a user's journal. A physical Pixel test is still required for real camera/GPS and Play delivery.
