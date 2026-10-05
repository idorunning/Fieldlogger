# My Trail Log 2.4 security review

This review is independent of the feature implementation. It distinguishes code inspection, automated checks and outstanding deployment/platform evidence. Findings are updated as the implementation lands; a checklist is not evidence that an attack was tested.

## Release gates

1. A plan shown by the app is never entitlement evidence. The server must validate Google Play purchase state, package `com.field.logger`, an allowlisted subscription/product, expiry and user/account binding before granting a paid entitlement. Pending purchases, forged tokens, mismatched products and expired purchases must not grant a paid quota. A token must belong to one account globally. A one-month free trial is a Play offer and must not be manufactured by an app checkbox or client date.
2. Monthly quota must be an atomic server operation using the server's UTC month. Concurrent requests must not overspend. The same observation ID may consume once; retries must not consume twice. Archived/deleted photos must not release quota. Edits must not let a consumed photo ID buy unlimited fresh AI analyses. Paid external operations need separate bounded retry/rate limits.
3. Existing trusted account ID, not an unverified email match, must bootstrap the root administrator. Registration currently does not verify email ownership. Adding administrators must require current administrator authority, target account validation, recent password verification and an audit record. Non-admin and cross-origin requests must fail before changing state. Removing the last administrator must not leave management inaccessible.
4. A photo ID owned by someone else must be rejected before quota consumption, object storage or social state mutation. The final database write must also guard owner and report failure if it did not write. GET/ZIP/export must remain owner-scoped.
5. Avatar source portraits are transient request input. Never write originals to R2, journal storage, diagnostic logs, public feeds, exports or retries. Public avatars must resolve to generated, server-owned assets; no arbitrary client URL or original-upload passthrough is permitted. Explain the external AI processing and obtain an explicit action before sending a person's portrait.
6. Exhausted quota or expired billing must not prevent free downloads, owner-only ZIP/data exports, unpublishing, archiving or account deletion. Signed ZIP downloads, if used, must have scoped short expiry and must not include another account's records.
7. Subscription verification errors fail closed for new grants. Existing validated entitlements must have explicit expiry and refresh/revocation semantics; cancellation, refund, suspension and grace state must be represented deliberately. Do not describe an unconfigured Play service account, subscription products, offers or webhook as live.
8. Website policies must match actual processing and native permissions. `Thinking About Ltd` is a proposed company name until registration; no incorporation claim, registration number or invented postal address. Required trader/billing disclosures and Play configuration still need real details before charging the public.

## Initial concrete finding: upload ownership collision

The 2.3 upload route queried an observation ID with `WHERE id=? AND user_id=?`, so another owner's known ID looked like a new upload. It wrote an orphan under the attacker's object prefix, then the ownership-protected UPSERT changed no observation but returned success. `syncOwnSocial` could nevertheless insert acorns or saved-place records for that private observation ID. The new upload/quota path must explicitly reject ownership collisions, including after concurrent writes. Test both a private and a public victim ID and inspect object, quota and social side effects.

## Targeted adversarial integration cases

| Area | Case | Expected result |
| --- | --- | --- |
| Quota | 20 concurrent distinct uploads at 29/30 usage | Exactly one new consumed slot and one observation; remaining requests report quota limit. |
| Quota | 20 concurrent uploads of one UUID | One slot, one owner record; retries return stable result. |
| Quota | Forge `capturedAt` and `localDate` in another month | Current server month is charged. |
| Quota | Delete/archive consumed photo, create replacement | Deleted/archived usage remains consumed. |
| Quota | Exhausted account updates note, archives and exports | Existing metadata operations and free export succeed; new uploads do not. |
| Ownership | Attacker uploads victim's known UUID with `acorned=true` | Reject; no attacker R2 object, charge, acorn or saved-place side effect. |
| Billing | Fake token, wrong package/product, mismatched account ID | Reject paid entitlement. |
| Billing | Valid token replayed by another account | Reject; original binding persists. |
| Billing | Pending, expired, on-hold/refunded subscription | No new paid grant; documented expiry/grace logic. |
| Billing | Revalidation network failure after grant expiry | Free entitlement; no fabricated extension. |
| Billing | Client submits plan, trial, expiry or role | Fields cannot confer authority. |
| Admin | Ordinary account requests every admin method | No member/account data or mutation. |
| Admin | Unverified registration claims root email | Does not become administrator. |
| Admin | Admin role grant without recent password/cross-origin | Reject and record no role change. |
| Admin | Concurrent attempts to remove last admin | At least one administrator remains. |
| Avatar | Oversized/non-image request, extra URL field, forged asset ID | Reject before paid provider call or public asset mutation. |
| Avatar | Provider fails/refuses or returns original image | No public original; prior avatar remains and error is safe. |
| Avatar | Original portrait EXIF and unique marker inspected in storage/logs/export | Original absent; only approved generated output can persist. |
| Export | User B requests user A export/photo by guessed ID | Not found/forbidden; no disclosure. |
| Export | Unsafe file name/path in metadata | ZIP paths remain safe fixed UUID-based names; JSON preserves text safely. |
| Security | Cross-origin POST/PUT/DELETE with valid cookie | Rejected; native same-origin/headerless design remains supported deliberately. |

## Evidence and unresolved items

Initial review completed against 2.3.0 source. No production mutations or purchase/provider calls were performed by this reviewer. Final implementation review and executable results will be appended after new code lands.

## Implementation review in progress

The following issues were found in the first implementation and corrected during independent review:

- Paid entitlements initially ignored Play acknowledgement. Valid grants now require `acknowledged=1`; refresh updates acknowledgement and trial phase while preserving replacement terminal state.
- Trial phase was initially encoded as a replacement subscription status, accidentally denying active trials. It now has a separate `is_trial` field and preserves Google state.
- Linked purchase tokens are owner-checked, and replacement writes run in one D1 batch. Replaced token replays cannot become active again.
- Purchase verification now has both account and IP limits before Google calls.
- Native offer selection initially treated unknown server eligibility as ineligible, hiding actual Play trials. Eligibility now defers to eligible offers returned by Play unless explicitly denied; checkout never fabricates a trial.
- Native restore now clears expired purchases, blocks pending/unverified purchases and waits for a successful current-purchase query before enabling checkout.
- Switching from a generated avatar to the manual editor now constructs numeric-only attributes rather than carrying generated-asset keys into an editable client object.
- Avatar allowance copy now says submitted attempts, including failed processing, rather than promising only successful creations consume an allowance.
- Generated avatars now receive a separate fail-closed visual/style check before object storage, with explicit non-photographic and unsafe/uncertain rejection. This remains an AI assessment, not a mathematical guarantee about an image.
- Export photo paths are invariant UUID names, so an archive/date change during download cannot change the filename linked in journal JSON. Sharing recipients and the member's own role/status/audit information were added without invite or billing credentials.

Reviewer executed `npm exec -- tsx --test tests/avatar-export.test.ts`: **9 passed, 0 failed**. Checks include actual JPEG decoding and removal of EXIF/GPS/trailing payloads; generated PNG dimensions/CRC/metadata; avatar URL ownership and allowance reservation; style refusal; ZIP64 validation by an independent Python ZIP reader; cancellation/backpressure and extraction path rejection. These checks do not constitute a real production OpenAI image-generation test or a Play purchase test.

Outstanding at this review stage: final immutable-photo upload path and concurrent quota test; annual paid-period anchoring through Google order data; admin requested-field concurrency and in-flight suspension publication guards; final cost circuit breaker; complete API integration and live readiness evidence. Missing Play service-account/product/offer setup and real UK trader address must remain explicit launch blockers, not be described as configured.

## Executed adversarial API acceptance

Reviewer created three disposable accounts on `http://localhost:8787`, seeded generated-cartoon objects and selected verified subscription fixtures **in local D1/R2 only**, then executed `/workspace/scratch/trail24-security-api.py`. Result: **34 checks passed**, all three accounts deleted. Machine-readable report: `/workspace/artifacts/my-trail-log-2.4.0-security-checks.json`.

Evidence includes:

- 29 real free uploads followed by 12 concurrent distinct uploads: exactly one succeeded and 11 hit the 30-photo limit. Twelve retries of the same saved UUID all succeeded without another charge.
- Forged capture dates did not select another billing month; archived records did not refund quota; changing saved JPEG bytes was rejected. A cross-owner UUID upload did not charge quota or mutate acorns/saved places.
- Local verified Plus and Premium fixtures enforced 150 and 1,000 limits under 12 competing final-slot requests. An annual fixture with 12,000 base photos and 499 bonuses accepted one final bonus only. A trial spent across months did not refill or receive annual extras.
- Client-supplied registration role/plan and fabricated purchase/expiry could not confer paid/admin rights. Real Google verification remains disabled in this environment, so this test checks the disabled fail-closed route; it does not test a live paid Google purchase.
- Administrator projection excluded credentials; wrong password failed, correct reauthentication could grant an existing account role; cross-origin changes failed; demotion applied on the next request; admin actions had audit records without submitted passwords.
- Generated avatar assets were server-owned, revision-scoped, hidden by blocking in both directions and hidden from others on suspension while remaining privately available to their owner. Client-selected arbitrary image URLs/revisions were rejected.
- ZIP remained free at exhaustion/suspension; contained private originals, archived metadata, generated avatar and selected sharing recipients; excluded credentials and other members' private originals/account email addresses.

Final inspected guards now include immutable JPEG digests for existing records; ownership precheck and affected-write check; retained quota reservations rather than unsafe refunds; acknowledged/still-fresh Google grants; separate trial state; original token terminal replacement; requested-field-only role/status updates with a final actor predicate; and final publication member-status checks. Current native community map uses osmdroid/Canvas/TextViews, with no WebView or JavaScript bridge: photo names such as `</script>` do not enter an inline script context.

Final follow-ups raised to the implementer: serialize competing first uploads of the same UUID before R2 writes; strictly validate paid Google order token/state/base-plan fields; use a native avatar processing timeout that covers both image generation and style validation. The provider's model permissions, real Google Play purchase/refund flow and physical Pixel behaviour remain separate live release evidence.

## Final local review update

After a fresh-upload lease was added, the reviewer reran the suite with additional attacks: **38 checks passed, all QA accounts deleted**. Twelve first uploads using one UUID and twelve different JPEG payloads produced exactly one success; the stored digest matched the retained image. Changing a different local account's email to `ntracey@gmail.com` did not bootstrap administrator access. Concurrent administrator role/status changes preserved both independent fields. The final local report supersedes the earlier 34-check run.

The account/global AI cost guard was also inspected: one transactional D1 batch reserves account and global spend; allowed model/purpose combinations are fixed; primary identification retains the chosen Sol model; unknown usage keeps the conservative reservation; settlement changes a pending reservation once; deleting an account cannot erase the global daily spend counter. Resizing/pixel-decoding the input bounds image analysis and rejects decompression bombs.

Remaining release evidence is explicitly external: actual Google Play products/offers/service-account purchase/refund/renewal tests, enabled OpenAI model permissions and a live photo-avatar generation, production trader disclosures, and physical Pixel behaviour. Automated local paid fixtures are not a substitute for a real Play licence-test purchase. The cloud ZIP intentionally includes synced cloud data; device-only unsynced photos require the separate device backup, so the interface and policies must keep that distinction clear.

## Secure operator payment setup and final billing review

The reviewer independently executed `tests/billing.test.ts` (**6 passed**) and the final `tests/billing-config.test.ts` (**8 passed**). These use actual transient RSA keys and AES-GCM, and check owner identity, uploaded credential validation, OAuth URL rejection, encryption context isolation, launch gates, account/product/base-plan binding, current trial phase and processed paid-order anchoring. The final setup tests also execute the actual guarded SQL against SQLite after owner deletion and verify missing/corrupt-master erasure fallback.

Full annual bonus proof now requires an exact-token processed Google order with the matching annual product/yearly base plan and a full `baseDetails` phase. Missing, unknown, trial, prorated and contradictory phases cannot create a 500-photo pool. A temporary order lookup failure preserves the account-bound, acknowledged/current subscriptions-v2 base allowance; it withholds annual extras instead of rejecting the whole legitimate subscription. Previous paid-period proof is retained only for the same unexpired order. Real Google licence-test periods can be accelerated, so the helper deliberately does not assume all Google-verified annual test periods last 365 days.

The new owner-only setup pins both the existing owner UUID and account email, independently of delegated administrator roles. Requests require HTTPS outside localhost, exact Origin, password reauthentication, bounded request bytes and account/IP limits. Google JSON must contain an actually importable RSA 2048–4096-bit PKCS8 key and Google's fixed OAuth token URL. Unused uploaded authentication/certificate URLs are stripped. Credentials and enable/disconnect flags use separate owner-bound encrypted contexts in D1. GET, audit and exports contain no private credential. Runtime OAuth checks enabled state before cache use and includes credential revision/key digest in cache identity, preventing same-email rotation or disconnect from reusing a stale credential.

The reviewer executed `/workspace/scratch/trail24-billing-setup-security.py` against localhost only: **21 checks passed**. It first refused existing owner/payment configuration, created a disposable pinned-owner fixture and delegated administrator, and generated synthetic RSA only in process memory. It verified ordinary/delegated-admin rejection, Origin/password checks, malicious OAuth/type/RSA rejection, encrypted round-trip storage, tamper fail-closed behavior and safe recovery, checkout-gate rejection, no credential material in ZIP/audit, confirmed disconnect, account-wide password limits across changing source IPs, secondary-account deletion isolation, protected-owner credential erasure and session invalidation. Both oversized declared-length and chunked billing JSON received 413 before purchase verification. The exact erasure fallback marker disabled even a still-present stored credential. Both disposable accounts and their configuration were removed. Report: `/workspace/artifacts/my-trail-log-2.4.0-billing-setup-security-checks.json`. No Google request or real credential was used. An initial local run uncovered URL-safe rather than standard base64 in the synthetic master-key fixture; the fixture was corrected before the successful run.

The review found that a password-validated setup request could initially finish after owner deletion and resurrect global configuration. Every final insert/update/delete/audit now rechecks the pinned owner ID and email in the same D1 transaction; a vanished owner produces no mutations and a 401 response. The actual SQLite delayed-write test verifies that both credential recreation and tombstone replacement fail, including a new account reusing the owner's email. Owner deletion removes the service credential and persists disabled/disconnected state in its final account-deletion transaction. A nonsecret exact tombstone preserves erasure when the encryption master is missing or broken, and remains disabled if the master is later restored. Credential/flag reads remain one D1 batch snapshot, avoiding inconsistent states during rotation/disconnect.

The reviewer also independently executed the latest `tests/ai-budget.test.ts`: **12 checks passed**. A new anonymous settlement marker preserves actual global provider-cost accounting after account deletion. Actual SQLite transactional tests cover 20 duplicate post-deletion settlements and a forced SQL failure followed by rollback/retry. The anonymous reservation contains no member ID, observation ID, credential or photo and cannot reveal deleted journal content.

At the payment-setup review stage, `PRICING_FINALISED=false` deliberately prevented checkout activation. The subsequent selected catalogue below now finalises pricing independently from live billing readiness. Saving credential JSON validates shape and cryptography, not Google Play permissions, configured products or lifecycle correctness. Those require real operator setup and Play licence-test evidence before activation. Live avatar/provider permissions and physical Pixel operation likewise remain outside this local security evidence.

## Final hybrid recognition and selected catalogue

The operator selected the following separate photo/review limits: Free has 100 photos and 5 closer reviews per UTC month; Plus £5.99 has 150/30; Premium £11.99 has 300/60; annual Premium £119.99 has 300/60 monthly, capped at 3,600 photos and 720 base reviews per paid term, plus 150 bonus photos and up to 30 reviews for those bonus photos. Pricing is now finalised; this does not enable unconfigured Google Play checkout.

The first identification stage now uses Luna, with independently inspected original imagery sent to Sol for a closer review when necessary. The reviewer implemented and executed `tests/recognition-policy.test.ts`: **9 checks passed**. Strict structured review includes subject count, visibility, taxon rank, diagnostic feature visibility, lookalike risk and detail complexity. Confidence alone cannot bypass uncertainty, insufficient/distinct features, coarse taxon evidence, multiple/distant/blurred/occluded subjects or conservative insect/fungus/grass/sedge/rush gates. A failed or unavailable closer review removes unsupported common/scientific names, specific facts, seasonal prose, references and alternatives; the retained entry clearly says tentative and preserves harmless visible-feature cues. Raw provider prompts/review objects and arbitrary error text cannot enter that projected result. Independent specialist disagreement likewise removes the contradicted species story.

Model routing signals remain model assessments. The synthetic high-confidence/lookalike tests prove routing and sanitisation behavior, not empirical photo-identification accuracy. A diverse labelled-photo evaluation is still needed to measure confidently wrong identifications and decide whether conservative gates need strengthening.

The reviewer inspected the new account/UTC-month, trial and paid-term request predicates and independently executed the updated `tests/ai-budget.test.ts`: **18 checks passed**. Initial recognition, stronger review and avatar dollar pools are separate. Reserved/failed stronger attempts count, including requests across UTC resets; exhausting all five free stronger slots does not prevent Luna recognition. Annual review extras require an admitted bonus photo belonging to that user and stop at the paid-term bonus cap; unused extras cannot survive the term end. Actual usage, including an overrun, is fully charged and can stop later work. Anonymous global spend and idempotent transactional settlement survive deletion as previously reviewed. Economic safety uses hard attempt and dollar ceilings, rather than assuming only an average percentage of photos will request stronger processing.

Durable per-photo simple/strong stages now contain validated candidate/review data or bounded attempted/failed markers. Stage ownership is checked on reads and conditional writes. A unique compare-and-swap marker claims a provider attempt and protects the final stage write; automatic requests do not repeat a failed attempt. Explicit user retry is separately budgeted. A cached successful stage does not require an available provider key. The photo lease is held through final journal persistence, with a fresh owned-record/cache read after acquisition, closing the previous stale-pending double-charge race.

The reviewer executed `/workspace/scratch/trail24-stage-cache-security.py`: **12 checks passed**, with both disposable accounts removed. Report: `/workspace/artifacts/my-trail-log-2.4.0-stage-cache-security-checks.json`. Tests used validated local cache fixtures and a bounded failure marker, not provider credentials or model calls. Twelve competing requests resolved a pending photo from cached simple/strong stages while retaining a manually confirmed name. Twelve completed retries reused the final answer with no further photo charges or AI reservations. A failed closer-stage fixture retained the original photo and a generic tentative result, and automatic retries reused it. Another user could not identify or retrieve that private photo; free ZIP stage history was owner-scoped; actual SQLite foreign-key deletion of an owned photo removed its stages without refunding quota; HTTP account deletion erased all that owner's cached stages and revoked the session while retaining the other user's cache. Public taxonomy/reference lookups by the genuine route may occur during these fixtures; these do not validate the photograph.

Native and desktop explicit review actions were also inspected: they use owned synced entries, send an identification POST without a photo upload or client-selected entitlement, retain per-photo in-flight guards, preserve confirmed names and edits during result merge, and prevent another active account from receiving the previous account's result. Physical-phone behavior and actual Google Play lifecycle testing remain separate release evidence.

## Managed production schema upgrade

Live acceptance found that publishing the Sites application had not applied migrations 0004–0008 to its managed D1 database. The reviewer implemented a static additive runtime initializer before database-dependent API handling. It accepts no request SQL or migration input and exposes no migration endpoint. It checks the existing membership/journal/auth schema, creates the fixed new tables and indexes, and adds only the three allowlisted nullable subscription columns. A competing ALTER failure is tolerated only after verifying the expected column. Before writing its separate version marker, it checks the full new-table column and foreign-key sets and required index columns. Existing migration ledgers are untouched, and a pre-existing runtime marker cannot skip verification. One promise coordinates each isolate; unsuccessful initialization is retryable and returns a safe storage-upgrading response that keeps local photos.

The reviewer executed `tests/runtime-migrations.test.ts`: **11 tests passed against actual SQLite**. An old-schema upgrade matched the canonical migration schema while preserving existing users, sessions, journal data, private encrypted envelopes, profiles, achievements and managed migration records. Tests covered partially applied upgrades, two independent isolates racing real ALTER statements, failed-attempt retry, incompatible column rejection, forged ledger/index rejection, unexpected required columns/cascades, absent legacy prerequisites, and the actual photo/user erasure semantics. No initializer query reads existing member, journal or credential values. The final full TypeScript test suite passed **96 tests**, typechecking passed, and `git diff --check` passed. Production deployment and subsequent live schema/provider acceptance remain the root agent's separate validation step.
