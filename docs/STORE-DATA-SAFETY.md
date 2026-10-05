# Data safety worksheet — native My Trail Log 2.1.0

This is implementation guidance for completing the Play questionnaire, not a submitted or legally certified declaration. It covers the native app and existing Field Logger server. Verify answers against the provider terms used for your account and any later changes.

| Data | When collected/transmitted | Purpose | Optional / retention |
| --- | --- | --- | --- |
| Email address, account name, user ID | Registration/sign-in to the server | Account management and app functionality | Account is optional for the device-local journal; retained until deletion |
| Photos | Private upload after signing in; optional identification sends a resized photo to OpenAI | App functionality / identification | Optional; uploaded copies retained until account deletion |
| Precise location | GPS-tagged discovery uploads to private account | App functionality / journal map | Optional permission, no background location; retained with the discovery |
| Approximate location | Rounded coordinates for automatic place names and optional identification | App functionality | Optional when GPS/identification is used; do not claim provider processing is ephemeral without confirming their retention |
| Other user-generated content | Notes, names, discovery metadata, capture dates/times and corrections upload with a discovery | App functionality | Optional; retained with the discovery |
| Authentication secrets | Password supplied for account actions, salted hash retained; user API key encrypted on server | Account management / optional identification | Password and API key are not persisted by the native client; existing server service configuration is managed on the desktop website; the native app has no key entry |

Data is linked to the signed-in account when uploaded. Guest photos remain local unless the user signs in or chooses to share/export. Network calls use HTTPS; Android disallows cleartext traffic. The session cookie is encrypted locally using Android Keystore. Device-local private files are not Android cloud-backup enabled.

## Collection and sharing

The app collects account details, uploaded photos, optional location and user-generated journal content. **Do not declare “no data collected.”** Optional OpenAI analysis transmits photos and approximate location; Photon receives rounded coordinates for place names; map tiles expose IP address and the displayed tile area to the map provider. Wikipedia/GBIF receive name lookups.

Play's sharing definition has exceptions for qualifying service providers and explicitly user-initiated transfers. Assess each external provider under the actual contract and terms before selecting sharing answers. If an exception is not established, disclose the transfer conservatively: photos and approximate location to OpenAI when identification is enabled, approximate location to Photon for place names. Precise stored coordinates are not sent by the identification/place endpoints. User-chosen photo/story sharing is initiated by the user; story cards omit exact GPS.

Photos, coordinates and notes in private Cloudflare storage are retained, so they are not “processed ephemerally.” Avoid blanket statements of zero provider retention: OpenAI requests use `store:false`, which is not a guarantee that provider logs are disabled.

## Other answers supported by the source

- Data encrypted in transit: **yes** for app/server/provider requests.
- User can request account deletion: **yes**, in the native account menu and at https://fieldlogger.co.uk/delete-account.
- Advertising: **no**. No advertising SDK is included.
- Play in-app purchases: **no**. Optional OpenAI use is charged by OpenAI to the user's own API account.
- Continuous/background location collection: **no**. Foreground capture fixes stop after a short window.
- Native app's own analytics/tracking SDK: **none included**. This does not assert that operating systems or hosting providers keep no operational logs.
- Independent security review: **do not claim one** on the basis of automated tests.

Deletion clears the server account, sessions, saved API key and uploaded discoveries; it also clears that account's local journal where deletion is requested. Exports and copies on other devices must be removed separately. Signing out keeps local files separated by account; it is not deletion.

Official guide: https://support.google.com/googleplay/android-developer/answer/10787469.

Archive retains the complete discovery, including GPS, and is not deletion. Archived photos sync and remain in exports. Branded photo stories include the place by default, with a preview option to hide it, while omitting precise GPS and EXIF.
