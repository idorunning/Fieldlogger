# My Trail Log: enable the recognition and avatar models

Live verification on 5 October 2026 found that the existing shared key can list only `gpt-6.1-sol`. The first economical-recognition request to `gpt-6-luna` returned HTTP 403. This is a provider permission failure; the native app and private journal do not need a new member key.

Update the existing key's project on the [OpenAI Platform](https://platform.openai.com/settings):

1. Select the project that owns the saved key.
2. Open **Limits → Model Usage**. Enable `gpt-6-luna` and `gpt-image-2.5-sunburst`, retaining `gpt-6.1-sol`.
3. Open **API Keys → Edit** for the existing key. If it uses Restricted permissions, allow **Responses: Write** and **Images: Write**, including `/v1/images/edits`. **Models: Read** supports diagnostics.
4. Save the permissions. Keep the key private; do not copy it into chat, the repository, the Android bundle or a member setting.
5. Tell Codex when the change is saved. The bounded live recognition, publication-safety and source-photo-to-cartoon checks can then be rerun.

No replacement or rotation is needed merely to update existing project/key permissions. If a model is unavailable to the project, request the required access through OpenAI rather than treating a failed request as a successful identification. Until permissions are ready, local capture, private synced photos, archive and free exports remain available; failed analysis requires an explicit retry and does not loop in background sync.

Sources checked 5 October 2026: [Managing projects](https://help.openai.com/en/articles/9186755-managing-projects-in-the-api-platform) and [Assign API key permissions](https://help.openai.com/en/articles/8867743-assign-api-key-permissions).
