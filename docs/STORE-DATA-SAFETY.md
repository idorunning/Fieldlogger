# Data safety worksheet — native My Trail Log 2.3.0

This is implementation guidance for completing the Play questionnaire, not a submitted or legally certified declaration. It covers the native app and existing Field Logger server. Verify answers against the provider terms used for your account and any later changes.

| Data | When collected/transmitted | Purpose | Optional / retention |
| --- | --- | --- | --- |
| Email address, account name, user ID | Registration/sign-in to the server | Account management and app functionality | Account is optional for the device-local journal; retained until deletion |
| Photos | Private upload after signing in; optional identification sends a resized photo to OpenAI | App functionality / identification | Optional; uploaded copies retained until account deletion |
| Precise location | GPS-tagged discovery uploads to private account | App functionality / journal map | Optional permission, no background location; retained with the discovery |
| Approximate location | Rounded coordinates for automatic place names and optional identification | App functionality | Optional when GPS/identification is used; do not claim provider processing is ephemeral without confirming their retention |
| Other user-generated content | Notes, names, discovery metadata, capture dates/times and corrections upload with a discovery | App functionality | Optional; retained with the discovery |
| Authentication secrets | Password supplied for account actions, salted hash retained; owner's shared service credential encrypted on server | Account management / optional identification | Password and API key are not persisted by the native client; existing server service configuration is managed on the desktop website; the native app has no key entry |

Data is linked to the signed-in account when uploaded. Guest photos remain local unless the user signs in or chooses to share/export. Network calls use HTTPS; Android disallows cleartext traffic. The session cookie is encrypted locally using Android Keystore. Device-local private files are not Android cloud-backup enabled.

## Collection and sharing

The app collects account details, uploaded photos, optional location and user-generated journal content. **Do not declare “no data collected.”** Optional OpenAI analysis transmits photos and approximate location; Photon receives rounded coordinates for place names; map tiles expose IP address and the displayed tile area to the map provider. Wikipedia/GBIF receive name lookups.

Play's sharing definition has exceptions for qualifying service providers and explicitly user-initiated transfers. Assess each external provider under the actual contract and terms before selecting sharing answers. If an exception is not established, disclose the transfer conservatively: photos and approximate location to OpenAI when identification is enabled, approximate location to Photon for place names. Identification/place lookups round coordinates. Publishing checks currently include the explicitly shared capture coordinates and caption. User-chosen photo/story sharing is initiated by the user; story cards omit exact GPS.

Photos, coordinates and notes in private Cloudflare storage are retained, so they are not “processed ephemerally.” Avoid blanket statements of zero provider retention: OpenAI requests use `store:false`, which is not a guarantee that provider logs are disabled.

## Other answers supported by the source

- Data encrypted in transit: **yes** for app/server/provider requests.
- User can request account deletion: **yes**, in the native account menu and at https://fieldlogger.co.uk/delete-account.
- Advertising: **no**. No advertising SDK is included.
- Play in-app purchases: **no**. OpenAI use is charged to the app owner's shared OpenAI project; users do not enter their own key.
- Continuous/background location collection: **no**. Foreground capture fixes stop after a short window.
- Native app's own analytics/tracking SDK: **none included**. This does not assert that operating systems or hosting providers keep no operational logs.
- Independent security review: **do not claim one** on the basis of automated tests.

Deletion clears the server account, sessions, saved API key and uploaded discoveries; it also clears that account's local journal where deletion is requested. Exports and copies on other devices must be removed separately. Signing out keeps local files separated by account; it is not deletion.

Official guide: https://support.google.com/googleplay/android-developer/answer/10787469.

Archive retains the complete discovery, including GPS, and is not deletion. Archived photos sync and remain in exports. Branded photo stories include the place by default, with a preview option to hide it, while omitting precise GPS and EXIF.


## Community features in 2.3.0

This is an app with user-generated content and user interaction. Update Play’s content-rating, target-audience and Data safety answers for this release before rollout. Published photos intentionally share the selected image, precise saved GPS, place, capture time and story with the chosen audience. Public profile information is limited to username and an opaque ID. Account names, emails and private notes are excluded. Acorn counts are public; bookmarks and following/blocking lists are private.

Selected email addresses are transmitted for opt-in contact discovery and private invitations. Android optionally requests READ_CONTACTS to review names and email addresses on the device. Only addresses explicitly selected and confirmed for matching are sent; contact names, full address books and contact lists are not stored on the server. Declare optional Contacts processing in the Console questionnaire. Invitation addresses are retained to enforce access. Disclose selected contacts/email data according to the Console questionnaire; do not claim that no contact information is processed. Reports store a reason, optional text and account association for moderation. Publishing sends the photo and caption to OpenAI for a people/unsafe/private-information check. Reports hide photos; the owner reviews them. AI is not a guarantee of perfect moderation. Community consent, report, block and unpublish controls are built in.

Unpublishing/archiving removes further shared access, but cannot recall saved copies. Account deletion also removes social records and published storage. Privacy: https://fieldlogger.co.uk/privacy. Guidelines: https://fieldlogger.co.uk/community-rules.

## Native preferences and permanent achievements in 2.3.0

Illustrated avatar choices and achievement IDs/earned timestamps sync to the account and are deleted with it. Avatars are public with a username, not profile photographs. Achievements are private and permanent after earning; archiving does not remove them.

Forecast lookups send coarse coordinates rounded to about 10 km to Open-Meteo and cache them for 15 minutes, without per-user location history. Postcode/place search queries go to Postcodes.io or Photon. Weather effects and backgrounds can be disabled.

POST_NOTIFICATIONS is optional on Android 13+. WorkManager periodically checks currently readable publications from followed members. There is no push token or third-party messaging SDK. Notification contents use the public username and photo name, with private lock-screen visibility.

Bulk publication requires explicit audience selection, consent and confirmation. It is a one-off batch for current active photos, not automatic sharing of future captures. Unpublish all cancels queued batches and revokes every shared audience/invite; private originals and achievements remain.
