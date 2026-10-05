# My Trail Log

A nature photo journal for noticing, discovering and remembering. The native Android app is a dated woodland scrapbook; the separately designed desktop website presents the service, policies and membership administration. Curiosity and variety matter, not distance or walk totals.

## Release 2.4.0

Android package **com.field.logger**, version code **11**, min SDK **24**, target SDK **36**. The original upload key is retained. Native CameraX, SQLite/private photo files and WorkManager are used; the app does not render a website through a WebView/TWA.

- Photos save locally before network work, with capture date/time and foreground GPS/place names when available. Previously granted location permission is reused; there is no continuous/background location tracking.
- Scrapbook day stacks, lower-right acorns, short interesting facts, branded share stories, collection, archive/restore and organised settings.
- **352 permanent achievements** in colour-coded Soil, Clay, Flint, Quartz, Amber and Gold layers, with progressively revealed species/time/date/place challenges.
- Optional **Make my avatar** creates an illustrated cartoon from a consented portrait. The source is re-encoded to remove metadata, processed transiently and sent to OpenAI; My Trail Log never stores or publishes it. Only the checked generated cartoon becomes the profile. Provider retention terms apply.
- Community audiences: everyone signed in, a selected browsed map circle or accepted private invitations. Publishing is explicit, checks fail closed, and report/block/unpublish controls remain available.
- Native photo map clustering merges markers when zooming out and splits them when zooming in. Postcode/place, type/tree, acorn and photographed-area filters remain.
- Optional reviewed-contact matching, periodic follower alerts, weather/time backgrounds and brief effects.
- **Free ZIP cloud exports** include active/archived originals and available private metadata. They stream with backpressure/ZIP64 and never require paid entitlement. Unsynced device-only photos need a local backup or sync first.
- Google Play Billing, account-bound server purchase verification, atomic photo/Closer Look allowances, restore and administration are implemented. **Prices are finalised; checkout remains disabled until real products, secure Google credentials and live purchase/lifecycle tests are ready.** Local capture, existing data and downloads remain free.

| Plan | New cloud photos / UTC month | Automatic Closer Looks / UTC month | UK price |
| --- | ---: | ---: | --- |
| Free | 100 | 5 | Free |
| Plus | 150 | 30 | £5.99/month |
| Premium | 300 | 60 | £11.99/month |
| Premium annual | 300 | 60 | £119.99/year, plus 150 photos and 30 Closer Looks per verified paid year |

AI recognition uses an efficient first pass and an automatic stronger **Closer Look** for harder or uncertain photographs when allowance and service budgets permit. If review is unavailable, the app retains a tentative or broader result and its uncertainty. Repeated delivery of unchanged photographs reuses durable stage results. These are suggestions, not verified species accuracy or edibility/medical advice. Wikipedia and GBIF provide references; optional Pl@ntNet/BioCLIP adapters are not provisioned by this release. All AI service credentials remain server-side. See [the viability review](docs/SUBSCRIPTION-VIABILITY.md) and [subscription setup](docs/PLAY-SUBSCRIPTIONS-SETUP.md).

An eligible one-month Google Play trial provides the selected plan's photo and Closer Look limits as **totals across the whole trial**, even if it crosses UTC months. Trials grant no annual bonuses and are not created by registration. Annual base limits are capped at 3,600 photos and 720 Closer Looks per verified paid year; the 150/30 bonuses are additional. Live checkout readiness must be verified separately from a build.

## Website, operator and source

Current domain: **https://fieldlogger.co.uk**. Company/app site at `/`, browser journal at `/journal`, authorised administration at `/admin`. Policies cover privacy, terms, cookies/storage, community standards, support and account deletion. Current operator: Nathan Tracey; **Thinking About Ltd is proposed, not incorporated**. Support: ntracey@gmail.com. A replacement domain must be connected and verified before changing canonical URLs and Android links.

Repository: [idorunning/Fieldlogger](https://github.com/idorunning/Fieldlogger), main. GitHub changes alone do not publish the website: the existing Sites deployment requires the matching source/build archive. Retain `.openai/hosting.json`, DB and BUCKET bindings, existing user data and domain configuration.

Owner administration is pinned to the existing trusted account ID and ntracey@gmail.com, not granted merely by signing up with an email. Other administrators can be granted membership roles. Payment setup is separately restricted to the protected owner and requires fresh password verification. It accepts Google-issued JSON directly on the private HTTPS website, encrypts it server-side and never returns it. No credential belongs in chat, source, browser storage or an AAB. See [subscription setup](docs/PLAY-SUBSCRIPTIONS-SETUP.md).

## Local development

Use Node 22.13+ and install with `npm ci`. Apply each schema migration once, in order, to a fresh local DB through `wrangler.dev.jsonc`; do not rerun existing migrations. Start with `npm run dev`. After changing the schema, generate a migration with `npm run db:generate` and review it before applying.

```sh
npm run typecheck
npm test
npm run build
npx wrangler dev --config dist/server/wrangler.json --port 8787 --persist-to "$PWD/.wrangler/state"
```

Existing integration scripts in `tests/` use disposable accounts/local fixtures. Native framework/database tests cover API 24 and 36. Live tests and physical-device checks must be reported separately from mocked/provider-free tests.

Server secrets stay in approved secure configuration. The shared OpenAI key and Google purchase tokens are server-encrypted with the separate runtime master secret. Owner-uploaded Play credentials use a distinct service/owner encryption context. Service configuration is excluded from member exports. Do not package `.env*`, `.wrangler`, signing keys, local browser profiles or provider credentials.

## Offline and operational limits

Android can save photos offline on first launch. The browser journal must load online once; browser storage can be cleared/evicted. Android/browser scheduling and battery restrictions affect background sync and approximately 15-minute follower checks. A force-stop can delay work until reopening. Map tiles, searches, new weather and AI require connectivity; standard map tiles are not an offline download feature. GPS depends on the device and granted permissions.

Quota exhaustion defers new cloud uploads; it does not delete a local photo, block archive/data access or paywall export. Archive does not refund usage or delete data. Deleting a My Trail Log account does not cancel an existing Google Play subscription.

See [Android build/upgrade guidance](docs/ANDROID.md), [community rules and service](docs/COMMUNITY.md), [avatar/export implementation](docs/AVATARS-AND-EXPORTS.md), [Play upload instructions](docs/STORE-UPLOAD-GUIDE.md) and [Data safety worksheet](docs/STORE-DATA-SAFETY.md). Test the signed update on the Pixel through the existing Play testing track before production rollout.
