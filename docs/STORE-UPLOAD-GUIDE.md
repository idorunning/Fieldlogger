# My Trail Log — Google Play asset pack

Update the existing Play application. Keep **com.field.logger** and the existing Play app signing configuration. Upload **my-trail-log-2.1.0-play.aab**, version code **8**; it uses the original upload key. This pack has not been submitted to Google Play.

## Main store listing

| Field | File / value |
| --- | --- |
| App name | `app-name.txt` — My Trail Log |
| Short description | `short-description.txt` — within 80 characters |
| Full description | `full-description.txt` — within 4,000 characters |
| App icon | `app-icon-512.png` — 512 × 512 RGB PNG |
| Feature graphic | `feature-graphic-1024x500.png` — 1,024 × 500 RGB PNG |
| Phone screenshots | Six PNGs in `phone-screenshots/`, each 1,080 × 1,920 |
| Release notes | `release-notes.txt` — within 500 characters |
| Support email | ntracey@gmail.com |
| Website | https://fieldlogger.co.uk |
| Privacy policy | https://fieldlogger.co.uk/privacy |
| Account deletion URL | https://fieldlogger.co.uk/delete-account |
| Suggested category | Lifestyle |

The journal screenshots now show the dated scrapbook design. Suggested screenshot order: **01 journal**, **06 wildlife**, **05 notes/sharing**, **02 discovery photo**, **03 collection**, **04 milestones**. Play allows up to eight screenshots per device type and requires at least two overall. These six images meet the 9:16 / 1,080px phone size recommendation.

These are renders of the actual Android view hierarchy using Robolectric's native graphics engine, with licensed example photos and clearly labelled example journal data. They are not browser screenshots or invented app interfaces. Device status/navigation bars are excluded. A physical Pixel capture can replace them later; rendering these screenshots does not verify the phone's camera, GPS or background execution. The images do not contain your personal photos or coordinates. Bundled sample data is test-only and does not appear in the installed app.

Tablet, TV, Wear OS, Android Auto and XR assets are not required for this phone listing; this release does not claim dedicated experiences for those form factors. A promo video is optional and is not included.

## App content

Use `DATA-SAFETY.md` as a source-based worksheet and complete the Console questionnaire for this exact release. It is not an automatically submitted declaration. Use `APP-ACCESS.md` for reviewer instructions. The app has no advertising SDK or Play billing; Identification uses the existing configured account service; OpenAI account billing is managed by the owner outside the native app. Select a target audience and answer the content-rating questionnaire based on your intended distribution; an age rating cannot be invented from the graphics.

## Upgrade and testing

Before moving from the old web wrapper, upload pending photos or export a web journal backup. Native sign-in uses the same account. Uploaded photos download automatically; **Import web journal backup** transfers photos still only in browser storage.

Test camera capture, permission denial, GPS/place lookup, offline reopen, reconnect upload and sharing on your Pixel using the existing Play testing track. The direct APK is upload-key signed and cannot update a Play-signed install. Use the AAB through Play.

## Editable branding

`editable/feature-graphic.html` is the exact feature graphic source; serve this folder with a local HTTP server to edit/export it. `brand/` includes outlined, scalable handwritten wordmarks and the favicon. `fonts/` includes Kalam Bold, DM Sans and their SIL Open Font Licenses. `photos/` includes the public demonstration photographs and attribution records. The notebook/leaf app icon is generated artwork created for this rebrand. The app name uses **Kalam Bold**; regular interface text uses readable standard type.

Official references: [Store listing requirements](https://support.google.com/googleplay/android-developer/answer/9866151) · [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) · [Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111).
