# My Trail Log: larger allowances with bounded stronger review

Historical proposal, superseded by the owner's final 100/150/300 catalogue with 150 annual bonus photos and 30 bonus Closer Looks. Read the [final viability review](./SUBSCRIPTION-VIABILITY.md) for the implemented limits and economics.

Proposal following the owner's choice of B prices and request for cheaper models on simple photographs, with stronger recognition for complex photographs. Prices £5.99/month, £11.99/month and £119.99/year are chosen; the larger allowances below are recommendations, not a claimed implemented or purchased contract.

[Fresh official pricing evidence](./OPENAI-PRICING-EVIDENCE-2026-10-05.md) was retrieved 5 October 2026 at 22:13 UTC with cache bypass. Luna is 20× cheaper than Sol at ordinary Standard input/output/cache-write rates. Both support image input and structured outputs. Luna supports `reasoning: none`; Sol requires at least `low`. No official nature-identification quality comparison was found.

## Recommended catalogue and protection

Free 50 photos/month; Plus 100 at £5.99; Premium 300 at £11.99; Annual 300/month plus 100 paid-year bonus at £119.99. A more cautious free alternative is 30/month. Keep unlimited offline capture, existing journals and free exports. The annual base ceiling is 3,600 per paid year, with 100 bonus additional; a trial grants one selected month's allowance across its full duration and no annual bonus.

Use one Luna first pass for a clear, focused recognition request, then at most one Sol review for an uncertain/incomplete/conflicting or complex result, within a separate monthly strong-review pool. Medium/low qualitative confidence, unsupported species claims, and specified difficult categories can trigger review; the model's self-confidence is not a calibrated accuracy measure. Safety remains a separate Luna check. Never automatically loop provider requests or repeatedly reanalyse an unchanged photo.

A suggested conservative request ceiling is 5 strong attempts/free month, 20/Plus and 60/Premium. Annual has 60 per UTC month **and 720 per paid term**, plus 20 bonus-photo reviews per paid term. Failed/time-out requests keep their reserved attempt. Actual strong spend must also be reserved atomically before each call. Prefer reserving a separate base pool so exhausted strong allowances do not prevent ordinary recognition of remaining photos. When stronger review is unavailable, return an explicitly tentative broad genus/family/unknown result; never invent a species. The user's private photograph remains saved. Customer-facing terms should disclose the bounded thorough checks rather than promise unlimited stronger analysis.

## Token reservation assumptions

These are planning input envelopes, not hard image-token formulas; official sizing tables omit the exact two selected model multipliers. Bound pixels/context, measure returned usage and revise the envelope if actual calls exceed it.

- Luna recognition: 6,000 inputs at$0.125/M cache-write pricing + 1,200 output cap at$0.50/M = **$0.00135**.
- Luna publication safety: 4,000 inputs at$0.125/M + 160 output cap at$0.50/M = **$0.00058**.
- Each ordinary fully identified/published photo therefore reserves **$0.00193** before retry margin.
- Additional Sol review: 6,000 inputs at$2.50/M + 4,000 total outputs at$10/M = **$0.055**. Reasoning is already included in those output tokens.
- Per-photo blended envelope = `$0.00193 + strong-review fraction × $0.055`.

No cache-read discount, Batch/Flex savings or live FX quote is assumed. Main reservations use Standard/default processing. The proposed1200-token Luna cap is a new recommendation awaiting workflow tests; concise schemas can still truncate and need a safe incomplete-response path.

## Full-use conservative economics

Net receipts use UK 20%VAT and 15%Play fees: price/1.2×0.85. FX assumption£0.80/USD. Strong-attempt counts below are capped maxima, not an expectation that every image must route to Sol.

| Offer | Net receipts | Photos | Maximum strong attempts | AI planning cost | Remaining before overheads |
| --- | ---: | ---: | ---: | ---: | ---: |
| Free30 | £0.0000 | 30 | 3 | £0.1783 | Subsidised |
| Free 50 | £0.0000 | 50 | 5 | £0.2972 | Subsidised |
| Plus 100 | £4.2429 | 100 | 20 | £1.0344 | £3.2085 |
| Premium 300 | £8.4929 | 300 | 60 | £3.1032 | £5.3897 |
| Annual 300+100 | £84.9929 | 3700 | 740 | £38.2728 | £46.7201 |

Include an additional 20% processing/retry envelope in spend-pool planning, not a hidden reduction of promised ordinary photo counts. Annual at 20%strong usage consumes$47.841 before margin, $57.4092 with 20%margin, or£45.9274 at the stated FX; net£84.9929 leaves£39.0656 before hosting/storage/avatars/free acquisition/refunds/support. The annual 720+20 request ceiling and immutable-photo idempotence prevent repeat review or thirteen-calendar-bucket inflation. Lower real usage can reduce cost, but never treat it as guaranteed.

## Hostile/high-complexity sensitivity

Costs for annual 3,700 photos if routing were unlimited:

| Fraction additionally reviewed by Sol | AI GBP at £0.80/USD |
| --- | ---: |
| 0% | £5.7128 |
| 5% | £13.8528 |
| 20% | £38.2728 |
| 30% | £54.5528 |
| 50% | £87.1128 |
| 100% | £168.5128 |

At 100% escalation, annual AI alone costs **£168.5128** against net£84.9929. Consequently an average 20%routing assumption cannot fund an unlimited review promise. A user can deliberately upload only difficult images; count and dollar limits must remain authoritative, with a useful tentative first pass after exhaustion.

At20%bounded stronger usage, annual FX£0.70/0.80/0.90 perUSD costs£33.4887/£38.2728/£43.0569 before the20%margin. Free 50+5strong costs up to£0.2972/account/month at full reservation; 1,000 active free accounts can therefore cost about£297/month plus avatar acquisition and other overheads. A free avatar still costs real image-generation input/output and must retain its separate lifetime/monthly controls. A 300-photo trial can cost£3.1032 at the stated capped mix; poor conversion still matters.

## Before promoting this design

Benchmark the same labelled photographs through Luna and Sol, covering common UK trees/flowers/birds, similar-looking species, winter leaves/bare branches, fungi, insects, blurred images, background people/private text and non-nature photos. Record correct species/genus/unknown rates, confident errors, false publish approvals, p50/p95 latency, usage tokens and incomplete JSON. Select complexity rules from those observations. Official descriptions of efficiency and capability alone cannot support a claim of unchanged recognition quality.
