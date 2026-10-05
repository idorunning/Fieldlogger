# My Trail Log: final subscription viability review

Reviewed 5 October 2026. The owner selected B prices with a larger free allowance and efficient recognition for ordinary photographs, using stronger review for harder cases. These figures match the final source catalogue. Paid checkout remains gated separately until provider configuration and real lifecycle tests pass.

## Final catalogue

| Plan | New cloud photos per UTC month | Closer Look attempts per UTC month | UK consumer price |
| --- | ---: | ---: | --- |
| Free | 100 | 5 | Free |
| Plus | 150 | 30 | £5.99/month |
| Premium | 300 | 60 | £11.99/month |
| Premium annual | 300 | 60 | £119.99/year |

Premium annual adds **150 bonus photographs and 30 bonus Closer Looks per verified paid membership year**. Base paid-year ceilings are **3,600 photographs and 720 Closer Looks**, so a year crossing thirteen UTC buckets cannot collect thirteen complete allowances. Bonus Closer Looks apply only to photos admitted through that paid year's bonus-photo allowance and expire with the paid term.

Monthly limits use the UTC calendar month, not the renewal date. A Google-verified eligible one-month trial receives the selected plan's normal photo and Closer Look allowances as **total trial ceilings**, alongside monthly limits; it has no annual bonuses. Failed provider-submitted stronger attempts still use a Closer Look. Archive/delete, reinstall, upgrade and restore do not refund or reset usage. Existing journals, offline capture and ZIP exports remain available without payment.

## Fresh official API evidence

[The saved official-source evidence](./OPENAI-PRICING-EVIDENCE-2026-10-05.md) records cache-bypassed retrieval at 22:13 UTC on 5 October 2026, HTTP 200 responses and scrape IDs. The official Standard short-context USD rates per million tokens are:

| Model | Ordinary input | Cached input | Cache writes | Output |
| --- | ---: | ---: | ---: | ---: |
| GPT-6 Luna | $0.10 | $0.01 | $0.125 | $0.50 |
| GPT-6.1 Sol | $2.00 | $0.10 | $2.50 | $10.00 |

Luna is 20× cheaper at ordinary input, cache-write and output rates. Both accept image input and strict structured output. Luna supports no reasoning; Sol requires at least low. The requests explicitly use Standard/default processing. No cache-read discount, Batch/Flex saving, Fast surcharge, regional processing or live FX quote is silently assumed. Reasoning is already included in output usage and is never charged twice.

The official vision sizing/calculator pages still omit exact Sol/Luna image multipliers. Our input-token envelopes are conservative reservations, not invented hard image-token guarantees. AI copies have actual decoded dimensions bounded, metadata stripped and context limited. Nature recognition preserves up to 1,800 pixels on the long edge; publication/avatar checks use a 1,024-pixel copy. Actual returned input/output/cache usage is recorded and measured overruns are charged in full.

## Recognition and quality

A focused **Luna first pass** produces concise field notes. A complex, uncertain, unsupported or incomplete result can receive one **Sol Closer Look** within both the attempt allowance and separate spend pool. An exhausted stronger allowance leaves a broad/genus/family/unknown tentative result rather than fabricated certainty. Ordinary recognition has a separate budget so exhausting stronger checks does not consume all remaining basic photo analysis.

Successful stage results are reused for an unchanged photo/version. An attempt marker is saved before a provider call; automatic retries do not endlessly repeat failed or timed-out stages. An explicit permitted retry of a failed stage can spend another bounded attempt. A publication-safety check is a separate Luna decision and uncertain/incomplete checks keep the photo private. The avatar uses a separate bounded generation/style-check path.

Official capability descriptions do not establish equivalent UK species accuracy. Neither model's qualitative confidence is calibrated probability. Test labelled common trees/flowers/birds, similar species, fungi/insects, winter/bare branches, blur, people/private text and non-nature images. Record species/genus/unknown correctness, confident errors, false publishing approvals, latency, incomplete replies and measured usage before claiming field reliability.

## Per-photo reservation and plan costs

Assume every new photo is identified and published once, input priced at cache-write rates, no cache-read saving:

| Stage | Model | Input envelope | Total output cap | Reserve |
| --- | --- | ---: | ---: | ---: |
| Ordinary recognition | Luna | 6,000 | 1,200 | $0.00135 |
| Publication safety | Luna | 4,000 | 160 | $0.00058 |
| Ordinary total | | | | **$0.00193/photo** |
| Additional Closer Look | Sol | 6,000 | 4,000, including reasoning | **$0.055/attempt** |

UK consumer prices include 20% VAT. This review assumes 15% total Play fees (current UK recurring 10% service + 5% Billing). Net receipts = displayed price/1.20×0.85. Reconcile actual Play settlement reports, refunds, other countries and tax treatment; do not subtract VAT twice.

At the explicit sensitivity assumption **£0.80/USD**, full stated photo use and every permitted Closer Look give:

| Plan | Net receipts | Photos | Maximum closer attempts | AI reserve cost | Remaining before overheads |
| --- | ---: | ---: | ---: | ---: | ---: |
| Free | £0.0000 | 100 | 5 | £0.3744 | Subsidised |
| Plus/month | £4.2429 | 150 | 30 | £1.5516 | £2.6913 |
| Premium/month | £8.4929 | 300 | 60 | £3.1032 | £5.3897 |
| Premium/year | £84.9929 | 3750 | 750 | £38.7900 | £46.2029 |

Separate monthly base/strong pools include an additional **20% cost/retry margin**:

| Plan | Ordinary pool / month | Stronger pool / month | Combined / month |
| --- | ---: | ---: | ---: |
| Free | $0.2316 | $0.3300 | $0.5616 |
| Plus/month | $0.3474 | $1.9800 | $2.3274 |
| Premium/month | $0.6948 | $3.9600 | $4.6548 |

Annual base monthly pools match Premium. Extra base budget is added only for admitted bonus photos; extra strong budget is funded only by admitted bonus-photo availability, bounded by 30 bonus reviews over the paid term. Atomic monthly/trial/annual request predicates count both reserved failures and completed calls. Bonus checks never bypass ordinary monthly counts for non-bonus photos.

For annual 3,750 photos and 750 closer attempts, the unexpanded reserve is **$48.4875 / £38.79**. With 20% margin it is **$58.185 / £46.548**, leaving **£38.4449** after the margin and fees/VAT, before storage/avatars/free-member acquisition/trials/hosting/database/support/refunds.

## Sensitivity and hostile usage

At all allowed closer attempts, annual cost before 20% margin is £33.9413/£38.7900/£43.6388 for GBP/USD assumptions 0.70/0.80/0.90. These are sensitivity assumptions, not a live currency quotation.

If all 3,750 annual photographs could escalate without a cap, cost would be **$213.4875 / £170.79** at £0.80/USD against net £84.9929. Thus an assumed 20% average routing rate cannot finance an unlimited stronger-review promise. A user can upload only difficult photographs. Count caps, dollar pools and tentative fallbacks remain authoritative even at 100% complexity.

If the current Google subscription proves paid annual base entitlement but its immutable order snapshot is temporarily unavailable, keep the verified monthly base allowance and deny annual extras. The annual total can only be anchored once paid-period proof exists; do not claim its complete enforcement without that proof. One additional full UTC bucket of 300 basic photos/60 reviews would add £3.1032 before the processing margin at the same reserve/FX. Readiness checks should verify successful order retrieval before paid launch.

One free account fully using 100 ordinary photos and 5 stronger reviews consumes up to **£0.3744/month** before 20% margin and other costs. One thousand active free accounts could consume **£374.40/month**, or £449.28 with the extra processing margin, before avatars/storage/hosting. A 300-photo/60-review trial consumes £3.1032 at the same envelope; at 20% conversion, trial processing alone is about £15.52 per acquired paid member. These acquisition costs can erase otherwise reasonable paid margins.

## Storage and avatars

R2 Standard marginal benchmark is $0.015/GB-month, $4.50/million writes and $0.36/million reads, with free direct egress. At an assumed 2 MB/photo, 3,750 photos add 7.5 GB/year; average new first-year storage is about 3.75 GB, costing $0.675 (£0.54 at the stated FX). Prior years, thumbnails/public derivatives, backups and metadata add recurring cost even after membership ends. The provider-wide free tier is not a per-member allocation.

GPT Image 2.5 Sunburst/Flare costs $8/M image input, $5/M text input and $30/M image output. The guide's low-square example 196 output tokens is $0.00588 **output alone**; the source selfie/prompt cost is additional. The app requests one low-quality 1024-square output. Older GPT Image 1 Mini/1.5 are scheduled for removal 1 December 2026 and are not the new integration dependency.

Avatar attempts remain separately bounded: one initial free attempt per account lifetime, one per subscribed UTC month, and paid failures consume the attempt. Generation reserves $0.08, returned actual usage is metered and a separate $0.15 account-month avatar circuit plus a bounded Luna style check applies. Source selfies are temporary private input and never published journal photos. Unlimited retries/free-account farms would be unsafe economics.

## Implemented safeguards and remaining live checks

- Atomic reservation before provider work; separate ordinary/strong/avatar pools; monthly, total-trial and paid-term stronger attempt ceilings. No price, quota or email privilege supplied by a client is trusted.
- Anonymous provider-cost reservations retain random IDs, day keys, cost amounts and timestamps without user/photo/credential fields. Actual global settlement survives account deletion and duplicate concurrent settlement. Unknown usage retains its conservative reserve.
- A shared $20 UTC-day operational circuit includes photos, avatars and owner probes. Image-token uncertainty, measured overruns and requests already in flight mean this is not an exact hard cap on the provider bill. Increase only with actual sustainable costs, never to mask misuse.
- The operator-only connection probe uses fixed Sol 512 output and image input, remains metered, and does not consume a member Closer Look count. Ordinary member errors omit credentials/setup instructions.
- Completed/attempted stage cache and immutable photo identities prevent automatic duplicate work. Manual retry policy must remain clear and limited; changed captions get a fresh necessary publishing check.
- Paid checkout stays off until Google products, protected verification credentials, real Play purchase/acknowledgement/lifecycle tests, trader details and website/app terms agree. Mock tests and a build do not prove live billing. Confirm representative large-photo worker CPU/memory and actual token costs.

Official references: [API pricing](https://developers.openai.com/api/docs/pricing), [Luna](https://developers.openai.com/api/docs/models/gpt-6-luna), [Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol), [vision sizing](https://developers.openai.com/api/docs/guides/images-vision), [model selection](https://developers.openai.com/api/docs/guides/model-selection), [image generation costs](https://developers.openai.com/api/docs/guides/image-generation#calculating-costs), [Play fees](https://support.google.com/googleplay/android-developer/answer/112622?hl=en-GB), [UK VAT](https://www.gov.uk/vat-rates), [R2](https://developers.cloudflare.com/r2/pricing/), [Play activation steps](./PLAY-SUBSCRIPTIONS-SETUP.md).
