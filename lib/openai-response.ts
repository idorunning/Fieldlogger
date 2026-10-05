export class OpenAIConnectionError extends Error {
  constructor(
    message: string,
    public readonly status = 502,
  ) {
    super(message);
  }
}
export function checkOpenAIResponse(response: Response) {
  // Never return or log upstream bodies: they may contain credential details.
  if (response.ok) return;
  if (response.status === 401)
    throw new OpenAIConnectionError(
      "Your OpenAI key has expired, been revoked, or is invalid. Replace it in API key settings. Your photos are saved.",
      422,
    );
  if (response.status === 403)
    throw new OpenAIConnectionError(
      "This key cannot access the selected OpenAI model. Check its project permissions or replace it in API key settings.",
      422,
    );
  if (response.status === 429)
    throw new OpenAIConnectionError(
      "OpenAI's usage limit or billing quota has been reached. Check your OpenAI project or try again later. Your photos are saved.",
      429,
    );
  throw new OpenAIConnectionError(
    "OpenAI could not complete the request. Try again shortly. Your photos are saved.",
  );
}

// Log only classified failure categories, never provider bodies or credential details.
export async function inspectOpenAIResponse(response: Response, apiKey?: string) {
  if (!response.ok) {
    let category = 'provider_error';
    try {
      const body: any = await response.clone().json();
      const message = String(body?.error?.message || '').toLowerCase();
      const code = String(body?.error?.code || '');
      if (message.includes('missing scopes') || message.includes('insufficient permissions')) category = 'missing_permissions';
      else if (message.includes('country') || message.includes('region') || code === 'unsupported_country_region_territory') category = 'unsupported_region';
      else if (code === 'model_not_found' || message.includes('model')) category = 'model_access';
      else if (response.status === 401) category = 'invalid_credential';
      else if (response.status === 429) category = 'usage_limit';
    } catch { category = 'non_json_provider_error'; }
    console.error('Identification provider failure', JSON.stringify({status:response.status,category}));
    // A failed model request can be diagnosed without exposing the credential or account details.
    if (response.status === 403 && apiKey) {
      try {
        const available = await fetch('https://api.openai.com/v1/models', {headers:{Authorization:`Bearer ${apiKey}`},signal:AbortSignal.timeout(10000)});
        const payload: any = available.ok ? await available.json() : null;
        const models = Array.isArray(payload?.data) ? payload.data.map((item:any) => String(item.id || '')).filter((name:string) => /^gpt-[4-9][a-z0-9.-]{0,70}$/.test(name) && !/audio|realtime|search|image|transcribe|tts|codex/.test(name)).sort().slice(0,80) : [];
        console.error('Identification model availability', JSON.stringify({status:available.status,total:Array.isArray(payload?.data)?payload.data.length:0,models}));
      } catch { console.error('Identification model availability unavailable'); }
    }
  }
  checkOpenAIResponse(response);
}
