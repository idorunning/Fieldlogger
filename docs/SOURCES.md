# Identification and reference sources

Researched 4 October 2026, before feature implementation. No image classifier is a source of certain truth. Fieldnotes separates the image suggestion, externally retrieved reference material, and the observer's own correction.

| Source | Purpose | Access and limits | Implemented |
|---|---|---|---|
| [OpenAI vision](https://platform.openai.com/docs/guides/images-vision) | Main photo interpretation and capture-context field notes | Server-side Responses API; configurable model, default gpt-4.1-mini. A paid API key is required. Qualitative confidence, possible alternatives, no edibility advice. | Adapter complete; secure key not yet provided, live call not tested. |
| [Wikipedia / MediaWiki Action API](https://www.mediawiki.org/wiki/API:Action_API) | Readable article extract for a suggested taxon or landmark | No key. Preserve article link and retrieval date; credit Wikipedia contributors under CC BY-SA 4.0. Extracts may be shortened. | Live retrieval, stored with discovery for offline reading. |
| [GBIF Species API](https://techdocs.gbif.org/en/openapi/v1/species) | Canonical taxonomic name and identifier | No key for reads. Exact name match with high name-matching confidence; this score is not visual identification confidence. | Live strict name matching. |
| [Pl@ntNet](https://my.plantnet.org/doc/api/identify) | Independent plant/flower image suggestion | Separate API key. API is available; its model should not be described as open source. JPEG/PNG, up to five views of one individual. Usage quotas and terms apply. | Optional server adapter. No key present; not live tested. |
| [BioCLIP 2](https://github.com/Imageomics/bioclip-2) / [model card](https://huggingface.co/imageomics/bioclip-2) | Open-source biological image classification, including plants, insects, birds and animals | MIT model card; separately hosted inference needs appreciable memory/compute. Not an offline mobile model and not an inference service included with the app. | Optional HTTP adapter and runnable local inference service. Not provisioned. |
| [iNaturalist](https://github.com/inaturalist/inatVisionAPI) | Community observation references and model research | Its full species classification weights remain private. The public small models cover about 500 taxa. Open-source server code is not unrestricted hosted vision access. | Research/reference links only. No uploads to iNaturalist. |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | Basemap for private GPS observations | Visible attribution. [Standard raster tile policy](https://operations.osmfoundation.org/policies/tiles/) forbids bulk offline tile downloading. | Leaflet map with coloured points. Photo/location data remains offline; basemap requires connectivity. |

The OpenAI answer is explicitly labelled an AI field note. The Wikipedia extract is displayed separately. GBIF matches names; neither GBIF nor Wikipedia verifies a photograph. Optional specialist disagreement is preserved as supporting evidence, not silently presented as consensus. User edits are separately marked.

Location and capture time are retained locally and in the account. OpenAI receives rounded coordinates (~1 km precision), capture date, local hour, timezone, and user notes. Saved photos are resized to at most 1800 pixels and re-encoded as JPEG, removing EXIF. The original selected file is never altered. Sharing excludes exact GPS; a place name is opt-in.

Cover photograph: Rob Wingate, [bluebell woodland](https://unsplash.com/photos/bluebells-bloom-beneath-the-lush-green-forest-canopy-QF2kkrpmx34), [Unsplash License](https://unsplash.com/license). It is decorative; it is not a user discovery or an identification example.
