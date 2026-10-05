# Official OpenAI pricing and vision evidence — 5 October 2026

Retrieved 2026-10-05T22:13:27Z using Firecrawl with `maxAge: 0` to bypass its cached page. This is public documentation retrieval; no paid OpenAI inference request or credential was used. Rates are USD, Standard processing and short context. Retrieval evidence records HTTP status and scraper IDs, not an invented last-updated date for the official documents.

| Official URL | HTTP | Scrape ID |
| --- | --- | --- |
| https://developers.openai.com/api/docs/pricing | 200 | 01a10e20-8669-77c0-af57-cb11377496a5 |
| https://developers.openai.com/api/docs/models/gpt-6-luna | 200 | 01a10e20-d8fa-7159-8c32-22549fc3fd96 |
| https://developers.openai.com/api/docs/models/gpt-6.1-sol | 200 | 01a10e20-e6c9-70b9-9022-b04b40b49aaf |
| https://developers.openai.com/api/docs/guides/images-vision | 200 | 01a10e20-ff17-7748-8aad-0288ba6280ed |
| https://developers.openai.com/api/docs/guides/model-selection | 200 | 01a10e20-f4a3-7339-a51a-2c79bdb33e60 |

## Exact pricing excerpt

Standard

|  | Short context | Long context |
| --- | --- | --- |
| Model | Input | Cached input | Cache writes | Output | Input | Cached input | Cache writes | Output |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gpt-6-astra | $10.00 | $1.00 | $12.50 | $50.00 | $20.00 | $2.00 | $25.00 | $75.00 |
| gpt-6.1-sol | $2.00 | $0.10 | $2.50 | $10.00 | $4.00 | $0.20 | $5.00 | $15.00 |
| gpt-6-luna | $0.10 | $0.01 | $0.125 | $0.50 | $0.20 | $0.02 | $0.25 | $0.75 |


## Supported task capabilities

[Official model page](https://developers.openai.com/api/docs/models/gpt-6-luna):

GPT-6 Luna is our most efficient model for focused, high-volume tasks.

`reasoning.effort` supports `none`, `low`, `medium` (default), `high`, `xhigh`, and `max`.
Use the Responses API for built-in tools and function calling. Chat Completions supports function calling only with `reasoning_effort` set to `none`.

EU data residency is available with Standard, Flex, and Batch processing.
See [data residency eligibility](https://developers.openai.com/api/docs/guides/your-data#which-models-and-features-are-eligible-for-data-residency).

Feature excerpt:

Features

Streaming

Supported

Function calling

Supported

Structured outputs

Supported

Fine-tuning

Not supported

[Official model page](https://developers.openai.com/api/docs/models/gpt-6.1-sol):

GPT-6.1 Sol delivers near-Astra performance at a lower cost for complex coding,
computer use, and professional work. Compare it with Astra on your tasks to
assess the tradeoff between quality and cost.

`reasoning.effort` supports `low`, `medium` (default), `high`, `xhigh`, and
`max`. The `none` and `minimal` reasoning efforts are not supported.

Use the Responses API for tool calling. Chat Completions is supported without
tool calling.

GPT-6.1 Sol supports US and EU data residency. Fast mode is unavailable with EU
data residency. See [data residency eligibility](https://developers.openai.com/api/docs/guides/your-data#which-models-and-features-are-eligible-for-data-residency).

See [GPT-6.1 Sol in the GPT-6 guide](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-6-astra#gpt-61-sol) and
[model-selection guidance](https://developers.openai.com/api/docs/guides/model-selection#when-to-consider-gpt-61-sol).

Feature excerpt:

Features

Streaming

Supported

Function calling

Supported

Structured outputs

Supported

Fine-tuning

Not supported

## Image-token caveat

The current official sizing/multiplier tables do not specify `gpt-6.1-sol` or `gpt-6-luna`, although their individual model pages explicitly support image input. Do not apply Astra/older Sol/Luna multipliers to these models as if verified. Exact multipliers shown on this date:

| Model | Multiplier |
| --- | --- |
| `gpt-6-astra` | 1.2 |
| `gpt-5.6-sol` | 1.2 |
| `gpt-5.6-terra` | 1.2 |
| `gpt-5.6-luna` | 1.2 |
| `gpt-5.5` | 1.2 |
| `gpt-5.4` | 1.2 |
| `gpt-5.4-mini` | 1.2 |
| `gpt-5.4-nano` | 1.2 |
| `gpt-5.2` | 1.2 |
| `gpt-5-mini`\* | 1.2 |
| `gpt-5-nano`\* | 1.5 |
| `gpt-4.1-mini` | 1.62 |
| `gpt-4.1-nano`\\* (2025-04-14 snapshot) | 2.46 |
| `o4-mini`\* | 1.72 |


The guide states: “Vision models can make mistakes.” It explicitly lists incorrect descriptions/captions as a limitation. File size does not by itself determine input-token cost; use actual provider usage and conservative input reservations.

## Official model-selection caution

### Experiment

Treat the guidance on this page as a starting point. The best way to find the right
model for your workflow is to experiment with different models and reasoning
settings to see what works.

Start by considering:

- **How often does your workflow run?** A frequent automation makes usage and cost
add up faster than an occasional project.
- **How quickly do you need the result?** A task you’re waiting on may need a
faster setting than one that runs overnight.
- **How will you use the output?** A draft for your review may need less polish
than something you’ll share externally.
- **How important is the quality of the result?** Depending on your use case or
industry, you might want to use a stronger model to put an emphasis on quality.

If you can, experiment using the same inputs to compare results and keep the
lightest setting that meets your quality bar.


The pages do not provide a UK countryside species-recognition benchmark comparing Luna and Sol. Both can process pictures; a cheap-first cascade is a product design requiring representative nature-photo testing, not proof of equal identification accuracy.

