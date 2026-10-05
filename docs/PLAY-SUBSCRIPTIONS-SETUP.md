# My Trail Log: Google Play subscription activation

Reviewed 5 October 2026. App package: `com.field.logger`. Public brand: **My Trail Log**. Support contact: **ntracey@gmail.com**. Proposed future company name: **Thinking About Ltd**; do not represent it as an incorporated entity until registration is complete.

The app and website can share the same server-verified membership and quota. Native Android checkout must use Google Play Billing. A client callback, screenshot, email address, admin label or purchase token submitted without verification is not proof of paid entitlement.

## Current setup boundary

No callable Google Play Console, Android Publisher or Google Cloud account-management connector was available to this task. The following owner-side Console/API configuration and live test purchases cannot be truthfully marked complete using an Android build alone. Paid checkout must remain unavailable until configuration, legal trader details and live lifecycle tests pass.

The website now provides **Connect Google Play payments** inside https://fieldlogger.co.uk/admin for the protected owner account. Sign in there and paste the Google-issued service-account JSON directly into the private HTTPS setup form, with your owner password to authorize saving. You do not need a task environment or to send a credential in chat. Other administrators cannot view or change payment credentials. The server validates the fixed Google OAuth endpoint and a real RSA PKCS8 key, encrypts the JSON with a separate service/owner context, and returns only readiness status and the service-account email. The textarea and password clear after submission; no credential is placed in browser storage or member exports.

Leaving the JSON field blank keeps the saved credential. Replacing it requires checkout disabled, then fresh real verification tests. **Disconnect verification** requires confirmation/password, erases the saved credential and disables verification/checkout; it does not cancel existing Google Play subscriptions. Configuration changes are audited without credential content. Pricing must be finalised and actual products/lifecycle tests ready before the checkout flag can be enabled. This form cannot create Google products, grant Play permissions or prove a real purchase succeeded.

Do not paste a service-account private key, OpenAI API key, signing password or JSON credentials into chat. Credentials belong in the hosting platform's secure server-secret configuration, never a repository, APK/AAB, browser bundle or downloadable ZIP.

## Finalised subscription catalogue

The owner selected balanced subscriptions with hybrid recognition. The original £2.99/150, £4.99/1,000 and £29.99/year draft is superseded. Use the following catalogue consistently in the server, native app, website, terms and Play products. See the [viability review](./SUBSCRIPTION-VIABILITY.md) for assumptions and spending circuits.

| Plan | New cloud photos / UTC month | Automatic Closer Looks / UTC month | UK consumer price |
| --- | ---: | ---: | --- |
| Free | 100 | 5 | Free |
| Plus | 150 | 30 | £5.99/month |
| Premium | 300 | 60 | £11.99/month |
| Premium annual | 300 | 60 | £119.99/year |

The annual plan adds **150 photos and 30 Closer Looks per verified paid membership year**. Its base paid-year ceilings are **3,600 photos and 720 Closer Looks**; bonuses are additional. Bonus Closer Looks apply only to photos admitted through that paid year's bonus-photo allowance and expire at the proven paid-term end. Unused monthly allowances do not accumulate. Local capture, existing journals/archives and downloads remain free. Archiving, deletion, reinstall, upgrades and restored purchases do not refund or reset server usage.

An efficient recognition pass handles ordinary photographs. A harder, uncertain or incomplete result can receive one automatic stronger **Closer Look** within the separate allowance and provider-spend limits. A provider-submitted review consumes its allowance even if processing fails. If review is unavailable, preserve a broad/tentative first-pass result rather than invent certainty. Save a durable attempt marker before provider work; normal retries reuse completed stages or respect failed/attempted markers. Explicit permitted retries of failed stages consume another bounded attempt. Completed results survive a failed final journal save. Private stage caches are erased with the photo/account and included in the free export. Model confidence is not calibrated species accuracy; no official species benchmark establishes equivalence between the models. Publication and avatar safety checks remain separate and fail closed.

## Subscription products

Create these exact subscription IDs under **Monetize → Products → Subscriptions** for the existing application. IDs are case-sensitive and should be treated as permanent.

| Subscription product ID | Plan | Base-plan ID | Auto-renewing billing period | UK consumer price |
| --- | --- | --- | --- | ---: |
| `trail_plus_monthly` | Plus | `monthly` | 1 month | £5.99 |
| `trail_premium_monthly` | Premium | `monthly` | 1 month | £11.99 |
| `trail_premium_yearly` | Premium annual | `yearly` | 1 year | £119.99 |

The backend allowlist and native `BillingPolicy` use these three product IDs. Keep base-plan/offer identifiers consistent with the server's configured allowlist. Product creation and base-plan activation are required in Play Console; compiling these strings does not create a purchasable plan.

The catalogue must display Google's returned localized price and actual offer phases. The UK prices above do not replace the actual Play purchase sheet. Verify VAT-inclusive regional pricing, countries of availability and converted prices before activating outside the UK.

Photo and Closer Look monthly allowances use a **UTC calendar month**, not the Play renewal date. Annual paid-term base ceilings are **12 × each monthly limit**, so a mid-month paid year cannot collect thirteen whole monthly buckets. Paid-year bonuses are separate; trials never unlock them. Describe these limits clearly before purchase.

## One-month offer

For each paid base plan, create and activate a new-customer acquisition offer, for example `first-month-free`, with exactly one free phase of **1 month (`P1M`)**, then the normal recurring price. Select eligibility **Never had any subscription** in this application, not just never had this one product. This avoids moving between three plans to collect three introductory trials.

The app should launch only an eligible `P1M` free-trial offer actually returned by Google, with the subsequent paid phase visible. If the selected user has no such offer, show the normal price and no free-trial promise. State the post-trial price, renewal period, automatic renewal and how to cancel before the purchase sheet.

The backend must read the current trusted trial phase from `purchases.subscriptionsv2.get`:

```json
{
  "lineItems": [{
    "productId": "trail_premium_yearly",
    "offerPhase": { "freeTrial": {} }
  }]
}
```

`offerPhase` is a union: `freeTrial`, `introductoryPrice`, `basePrice` or `prorationPeriod`. A proration phase can report `originalOfferPhaseType: "FREE_TRIAL"`; handle that conservatively as a trial for bonus eligibility. `offerDetails.offerId` can remain present after the trial ends and must not be used as proof that the current period is free. Store trial status separately from the actual Google subscription state and refresh both together.

A one-month offer often crosses two UTC quota months. Enforce the selected plan's **total trial photo and Closer Look allowances**, in addition to the UTC monthly ceilings: Plus gets 150 photos/30 Closer Looks across the whole trial; Premium and Premium annual get 300/60. Trial access never grants an annual bonus. Do not grant trials based on account creation or a self-reported flag.

For paid annual bonus accounting, do not anchor the paid year to trial-inclusive `SubscriptionPurchaseV2.startTime` or derive it from a moving grace-period expiry. Fetch `orders.get` for the verified annual line item's `latestSuccessfulOrderId`; its matching `lineItems[].subscriptionDetails.servicePeriodStartTime` and `servicePeriodEndTime` are immutable accounting snapshots for the period funded by that order. Verify its package, order ID, purchase token, product/base plan, `state: "PROCESSED"` and `offerPhaseDetails.baseDetails` presence. `baseDetails` is fieldless; it does not contain a billing-duration property. Store that paid period as a stable bonus-ledger key. Google's current subscription state/expiry remains the authority for access; order snapshots are not a replacement for entitlement verification. A new order or a prorated plan change must not automatically manufacture another annual bonus grant. Licence-test renewals are accelerated, so a mandatory 365-day service-period assertion is unsuitable for trusted test purchases.

## Server verification account

1. In Google Cloud Console, select or create the project used for Play verification and enable the **Google Play Android Developer API**.
2. Create a dedicated service account for verification. In Play Console → **Users and permissions**, invite its service-account email and restrict application access to `com.field.logger`.
3. Google's Billing API instructions require **View financial data, orders, and cancellation survey responses** and **Manage orders and subscriptions**. Grant the required permissions for this app; do not grant unrelated release, signing or user-management privileges.
4. Prefer a supported workload identity over a long-lived downloaded key. If this hosting runtime requires service-account JSON, store it privately as the server's `GOOGLE_PLAY_SERVICE_ACCOUNT` secret through secure platform setup. The JSON must not be available through any admin endpoint. Its expected fields include the Google-issued `client_email`, `private_key` and appropriate token endpoint; do not manufacture them.
5. Keep `GOOGLE_PLAY_PACKAGE=com.field.logger`. The server must build Android Publisher requests for this fixed package, never accept an arbitrary package or API URL from a client.
6. Keep `GOOGLE_PLAY_BILLING_ENABLED` off until the service account can query real license-tester purchases and acknowledge them. Enable the separate `TRAIL_BILLING_LAUNCH_READY` gate only after pricing/model review, legal details and the tests below pass.
7. Supply the legal trader/contact address through private server configuration. Set a company number only after incorporation; use the actual current trader's name until then. Update Play's developer, support, privacy, billing and Data safety details to match.

OAuth access uses Google's server-to-server flow with scope `https://www.googleapis.com/auth/androidpublisher`. The server signs its own short-lived assertion and caches the returned access token only in server memory for its valid lifetime. Never return an OAuth token or service-account material to the app.

## Required purchase-validation rules

- Require a signed-in My Trail Log session and a valid same-origin request. Bind the Play billing flow to the account with a deterministic one-way account ID; compare the returned `externalAccountIdentifiers.obfuscatedExternalAccountId` against the expected server account hash.
- Verify each submitted token with `purchases.subscriptionsv2.get` for the fixed package. Check the allowlisted product, correct base plan/offer, active line item, expiry and current subscription state. Do not grant `PENDING` purchases.
- A token is globally unique and may belong to only one My Trail Log account. Encrypt the stored token and use its hash for lookup/deduplication. Reject replay onto another account, including simultaneous submissions. Use atomic database constraints rather than a read-before-write check alone.
- Grant paid new-photo access for verified `ACTIVE` or `IN_GRACE_PERIOD`; keep a cancelled subscription's access only while its expiry is in the future. Revoke the paid allowance for expired, paused, on-hold, revoked or replaced purchases. Preserve account-data access and exports.
- Handle `linkedPurchaseToken` when a plan replaces another one. Supersede the older entitlement so a single purchase chain cannot grant benefits to two accounts. Do not permit simultaneous subscriptions to stack quota or repeatedly reset annual bonus periods.
- Acknowledge the initial verified purchase on the server immediately. Google refunds unacknowledged purchases after three days. A transient acknowledgement error must remain retryable; repeated restore requests must not grant or charge twice. Subsequent renewals do not require a new acknowledgement.
- Recheck paid entitlement before admitting new photos. The proposed cache freshness is at most **15 minutes**. If a refresh is required and Google is unavailable, fail closed for new paid uploads with a clear retry message while preserving local capture and existing data/export access. Do not silently reduce a verified paid member to free during an upstream outage.
- Expiry must be checked against the server clock even with a fresh cache. Phone clocks, Java preference flags and email addresses cannot raise allowances. Deletion, archive, reinstall and restore cannot reset server usage.
- Keep purchase tokens, OAuth headers and upstream error bodies out of logs, analytics and support exports.

## Renewal/refund notifications

Production should use Real-time Developer Notifications (RTDN) in addition to on-demand re-verification. An unauthenticated webhook that triggers Google API calls is an abuse and cost risk. Until authenticated push or a verified pull worker is configured, leave that public route disabled and use strict fresh re-verification; this has a polling delay and is not instant lifecycle delivery.

For authenticated Pub/Sub push:

1. Create a Cloud Pub/Sub topic and grant **Pub/Sub Publisher** on that topic to Google's documented sender, `google-play-developer-notifications@system.gserviceaccount.com`.
2. Create a push subscription with a dedicated service account, **Enable authentication**, the final HTTPS billing-notification endpoint and an explicitly configured OIDC audience. Configure the Pub/Sub service agent's required token-creation permission as described in Google's docs.
3. At the endpoint, verify the signed Google JWT using Google's trusted rotating public certificates; validate its signature, allowed algorithm, issuer, expiry, audience, expected push service-account email and `email_verified=true`. Reject an absent/invalid token before parsing or processing the notification. Matching a plaintext header is insufficient.
4. Validate the Pub/Sub wrapper and decoded payload size, expected package and recognized event type. Deduplicate message IDs. The event is a hint: fetch the purchase's current authoritative state from Google before changing entitlement. Account for duplicate and out-of-order delivery. Do not take a plan name, expiry or user identity from untrusted notification fields.
5. Configure the full topic name in Play Console → **Monetize → Monetization setup → Real-time developer notifications**, then send the Console test message and verify receipt. A test message is not a purchased subscription.
6. Monitor failed acknowledgement/notification processing and retry with backoff. Use the Voided Purchases API for refund/chargeback reconciliation where required. An admin's local membership adjustment does not cancel or refund a Play charge.

No Pub/Sub endpoint, topic, project ID, push-identity email or credential can be inferred from the user's support email. Those are setup inputs supplied by the account owner through the provider's secure configuration.

## Acceptance checklist before activating checkout

- Confirm the finalised hybrid-recognition catalogue above matches server/native/site/terms/Play and no superseded draft prices or limits remain.
- Upload the signed billing-enabled AAB to the existing Play internal/closed test track. Google currently requires Billing Library **8 or later** for app updates; the live getting-ready guide recommends **9.1.0**. Use a supported version validated by this build, rather than changing the library solely from a snippet.
- Configure licence testers and install through Google Play using the matching test account. An upload-signed APK installed separately is not an end-to-end Play subscription test.
- Test all three product IDs and localized price/offer rendering. Verify the one-month free trial only appears for eligible new subscribers; test an already-used offer and a plan switch.
- Test completed payment, slow/pending payment, cancellation at trial end, normal renewal, failed renewal/grace period, account hold, expiry, refund/revoke, restore after reinstall and another signed-in My Trail Log account.
- Test replaying the same token on two accounts and concurrent verification. Neither should steal or duplicate paid access. Test the linked-token replacement path.
- Test last-allowance concurrent photo uploads and Closer Looks, unchanged retry/cache recovery, altered JPEG under an old ID, calendar-month rollover and trial crossing a calendar month. Test annual photo/Closer Look bonuses once per paid membership year and no bonus during trial. Confirm uncertain photos retain a useful tentative/broad result when review is unavailable.
- Verify archive/delete never refunds usage, upgrade does not reset usage, and website/app show the same quota. Do not automatically subscribe users who only sign up.
- Test quota exhaustion and temporary Google/OpenAI failures while local capture remains available. Free ZIP export, archived-data export and account deletion must still work with no paid subscription.
- Verify every admin membership/role change is authenticated, authorized, CSRF-protected and audited. The owner `ntracey@gmail.com` must be promoted through a trusted existing account identity; an unauthenticated signup using that email must not automatically become admin.
- Test source-selfie privacy, avatar billing limits and generated-image deletion. Update Play Data safety for the actual selfie processing and purchase/account identifiers used.
- Save evidence of real Google verification and acknowledgement, notification authentication, quota boundary tests and actual OpenAI per-photo costs before setting launch readiness. Do not describe mocked purchase tests as live billing verification.

## Official references

- [Create and manage subscriptions](https://support.google.com/googleplay/android-developer/answer/140504?hl=en): base plans, prices and new-customer eligibility.
- [Play Billing getting ready](https://developer.android.com/google/play/billing/getting-ready): dependency requirements, test-track upload and RTDN setup.
- [Android Publisher getting started](https://developers.google.com/android-publisher/getting_started?hl=en): service accounts and required Billing API permissions.
- [Purchase verification and fraud protection](https://developer.android.com/google/play/billing/security): secure backend validation, account binding and acknowledgement.
- [SubscriptionPurchaseV2 resource](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2?hl=en): trial phases, line items, identifiers and linked tokens.
- [Orders resource](https://developers.google.com/android-publisher/api-ref/rest/v3/orders?hl=en) and [orders.get](https://developers.google.com/android-publisher/api-ref/rest/v3/orders/get?hl=en): paid-period accounting snapshots and authoritative order binding.
- [Subscription lifecycle](https://developer.android.com/google/play/billing/lifecycle/subscriptions?hl=en): grace, cancellation, hold, expiry and replacement.
- [Authenticated Pub/Sub push](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions): JWT signature/audience/service-account checks.
- [RTDN reference](https://developer.android.com/google/play/billing/rtdn-reference) and [Voided Purchases API](https://developers.google.com/android-publisher/voided-purchases).
- [Subscription viability review](./SUBSCRIPTION-VIABILITY.md): revenue, API/storage cost scenarios and unresolved pricing risks.
