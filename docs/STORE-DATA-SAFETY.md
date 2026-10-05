# Data safety worksheet — My Trail Log 2.4.0

For package `com.field.logger`, version code **11**, minimum SDK **24**, target SDK **36**. This worksheet guides the owner's Play Console answers; it is not a submitted declaration or independent security certification. Review the actual provider contracts and SDK handling.

| Data category | Transmission / purpose | Optional / retention |
| --- | --- | --- |
| Email, name, user IDs | Registration, account administration, member matching and purchase-account binding | Cloud accounts are optional for a device-local journal; account-associated records remain until deletion, subject to necessary legal/security exceptions |
| Journal photos | Signed-in private uploads; chosen images/context sent for identification and publication checks | Optional cloud/AI features; uploaded and archived copies persist until removal/deletion |
| Avatar source and generated portrait | Explicit **Make my avatar** action sends a cleaned portrait to the service and OpenAI; the cartoon becomes the profile image | Optional, with explicit permission/processing notice. My Trail Log does not persist or publish the source. OpenAI retention terms apply |
| Precise location | Foreground GPS or preserved capture coordinates upload with a discovery | Optional permission; subsequent captures reuse the grant. No background location permission. Retained with the discovery |
| Approximate location | Rounded coordinates for AI/place names, coarse weather coordinates, map display area | Optional location features; retained journal GPS differs from short-lived lookups/provider processing |
| Contacts | Local review after Contacts permission; selected/confirmed email addresses sent for opted-in member matching or invitations | Optional. No silent full address-book upload. Contact names/full lists are not stored; invitation addresses enforce access |
| Other user-generated content | Notes, corrections, dates, categories, identification, avatars, achievements, sharing choices, acorns, follows, saved places, blocks and reports | Feature operation/moderation; retained with the account. Archive is not deletion |
| Purchase history | Google product/purchase identity, server-verified entitlement, expiry, acknowledgement and usage | Optional subscriptions. Purchase tokens are server-encrypted. No full card details received |
| App activity / interactions | Deliberate social actions, badges, allowance and AI-processing counters | Functionality, moderation, quota enforcement and abuse prevention; no behavioural marketing analytics |
| Security / technical data | Session identifiers, salted password hashes, network/rate-limit data, administration audit | Account protection and operational security. These are not public profile data |

Uploaded data is linked to the signed-in account. Guest photos remain local unless the member signs in, explicitly shares or exports. Public identity is username, opaque ID and illustrated avatar; private email, account name and notes are excluded from ordinary community access.

## Collection and external transfers

**Do not select “no data collected”.** Cloudflare hosts private storage. OpenAI receives images for identification, publication checks and consented avatar creation with relevant context. Recognition uses an efficient first pass, with an automatic stronger Closer Look for harder/uncertain photos when allowance permits. Processing results and allowance history persist to avoid duplicate processing and enforce limits; they are not advertising profiles. Avatar-source EXIF/GPS is removed and the source never becomes a journal entry. The cartoon passes a separate suitability/non-photographic check before activation. AI checks can fail or be mistaken.

Photon receives rounded coordinates/place queries; Postcodes.io receives postcode searches. Open-Meteo or MET Norway receive coarse weather coordinates. Map providers receive IP/displayed area. Wikipedia and GBIF receive reference-name searches. Review Google Play Billing Library's current vendor declaration too.

Play sharing exceptions can cover qualifying processors/service providers or user-initiated transfers. Apply an exception only where the actual relationship and terms qualify; otherwise disclose conservatively. `store:false` does not prove zero OpenAI retention. Private journal storage is persistent; do not mark it as ephemeral. Even transient avatar-source handling on our service is not a guarantee that a provider instantly deletes its own copy.

Publishing is explicit and audience-specific: everyone signed in, a browsed local map circle, or accepted private invitations. Shared discoveries include the selected image, saved coordinates/place/date, story, username and cartoon. A local circle is the map being browsed, not verified physical presence. Unpublishing, archiving and deletion revoke service access but cannot recall downloads/screenshots.

## Supported Console answers

- **Encrypted in transit: yes.** HTTPS; Android cleartext disabled.
- **Account deletion: yes.** Native settings and https://fieldlogger.co.uk/delete-account.
- **Advertising: no.** No ad SDK or advertising-ID feature.
- **In-app purchases: supported.** Google Play Billing is integrated. Checkout stays disabled until products, secure verification and launch/lifecycle checks are complete. Do not reuse the old “no Billing” answer.
- **Background location: no.** Short foreground capture/weather fixes. Background uploads do not collect continuous location.
- **Contacts/notifications: optional.** READ_CONTACTS follows an explanation and review. POST_NOTIFICATIONS is requested when enabling alerts on Android 13+. WorkManager periodically checks accessible followed publications; no Firebase/push-token SDK.
- **Tracking SDK: none added by My Trail Log.** Providers/operating systems may keep operational information.
- **Independent security certification: do not claim one** from tests or adversarial agents.

Android sessions are protected using Keystore; app-private files are not enabled for Android cloud backup. AI and Google verification credentials remain server-side. Admins view membership identity/status, plan and usage for support; administrative changes are authorized and audited, not unrestricted access to private originals.

## Retention, deletion and exports

Archive preserves photos/metadata and permanent earned badges. The generated avatar persists; its source is not stored by My Trail Log. Deletion removes live account/journal/profile/avatar/social records/uploads and clears the requesting device's account data. Copies on other devices, exports and necessary provider/security/legal records can need separate handling.

Cloud ZIP downloads are always free, including active/archived originals and available account/journal metadata. Unsynced device-only photos require sync or local backup. Exports contain precise saved GPS/private notes. Password hashes, login/invitation tokens and encrypted billing credentials are excluded.

## Audience and public operator

This is user-generated content with social interaction. Update content rating and target-audience answers. Terms set account eligibility at **16+**; this is not verified identity/age or a child-directed/Families claim. Choose Console ratings from actual features, not the artwork.

Brand: **My Trail Log**. Current operator: **Nathan Tracey**, 422 Milton Road, Waterlooville, PO8 8LD, United Kingdom. **Thinking About Ltd is proposed, not incorporated**; do not invent a company number.

Keep the existing domain until a verified migration:

- https://fieldlogger.co.uk/privacy
- https://fieldlogger.co.uk/terms
- https://fieldlogger.co.uk/cookies
- https://fieldlogger.co.uk/community-rules
- https://fieldlogger.co.uk/delete-account
- https://fieldlogger.co.uk/support · ntracey@gmail.com

Official guidance: https://support.google.com/googleplay/android-developer/answer/10787469.
