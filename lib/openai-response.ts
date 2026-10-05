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
