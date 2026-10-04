# Optional BioCLIP 2 inference service

This service follows the official [pybioclip Python tutorial](https://imageomics.github.io/pybioclip/python-tutorial/). It is implemented but has not been run against model weights in this workspace. The model is **not downloaded, running, or connected** in the first web build.

Use a separately provisioned machine with enough memory for BioCLIP 2 and the TreeOfLife-200M embeddings. Install `requirements.txt`, configure a long random `BIOCLIP_TOKEN`, and run `uvicorn app:app --host 127.0.0.1 --port 8091`. The first authenticated prediction downloads model assets and may take substantial time; warm it before connecting Fieldnotes. Serve it through an authenticated HTTPS reverse proxy. Set Fieldnotes' server-only `BIOCLIP_URL` to the `/identify` endpoint and configure the same token.

Never expose an unauthenticated inference endpoint. The returned scores rank candidates and are not calibrated correctness probabilities. Fieldnotes retains these as independent suggestions and does not silently replace the OpenAI result with them.
