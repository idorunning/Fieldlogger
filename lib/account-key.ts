import { bindings, database, json } from "./server";
import { sealKey, unsealKey } from "./key-crypto";

import { checkOpenAIResponse } from "./openai-response";
export async function keyStatus(userId: string) {
  const row = await database()
    .prepare("SELECT updated_at FROM account_keys WHERE user_id=?")
    .bind(userId)
    .first<{ updated_at: string }>();
  return {
    hasKey: !!row,
    // Keep older clients compatible; identification is always account-scoped.
    serverKey: false,
    canSave: !!bindings().API_KEY_ENCRYPTION_KEY,
    updatedAt: row?.updated_at || null,
  };
}
export async function getAccountKey(userId: string) {
  const row = await database()
    .prepare("SELECT envelope FROM account_keys WHERE user_id=?")
    .bind(userId)
    .first<{ envelope: string }>();
  if (!row) return null;
  const secret = bindings().API_KEY_ENCRYPTION_KEY;
  if (!secret) throw new Error("Key storage is unavailable.");
  return unsealKey(row.envelope, userId, secret);
}
export async function accountKeySettings(
  request: Request,
  userId: string,
  action?: string,
) {
  if (request.method === "GET" && !action) return json(await keyStatus(userId));
  if (request.method === "POST" && action === "test") {
    // A tiny Responses request tests the same model and permission as photo analysis.
    // Rate limit tests to one per 10 seconds per account, including failed tests.
    const now = Date.now();
    const limit = await database()
      .prepare(
        "INSERT INTO auth_attempts(key,count,reset_at) VALUES(?,1,?) ON CONFLICT(key) DO UPDATE SET reset_at=excluded.reset_at WHERE auth_attempts.reset_at<=? RETURNING key",
      )
      .bind(`key-test:${userId}`, now + 10000, now)
      .first();
    if (!limit)
      return json(
        { error: "Please wait a few seconds before testing again." },
        429,
      );
    const apiKey = await getAccountKey(userId);
    if (!apiKey) return json({ error: "Save an OpenAI API key first." }, 400);
    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: bindings().OPENAI_MODEL || "gpt-4.1-mini",
        store: false,
        input: "Reply with OK.",
        max_output_tokens: 16,
      }),
      signal: AbortSignal.timeout(30000),
    });
    checkOpenAIResponse(response);
    await response.body?.cancel();
    return json({
      ok: true,
      message: "Connected. Photo identification is ready.",
    });
  }
  if (request.method === "DELETE" && !action) {
    await database()
      .prepare("DELETE FROM account_keys WHERE user_id=?")
      .bind(userId)
      .run();
    return json({ ok: true });
  }
  if (request.method !== "PUT" || action)
    return json({ error: "Method not allowed" }, 405);
  if (!request.headers.get("content-type")?.startsWith("application/json"))
    return json({ error: "Use a JSON request." }, 415);
  const secret = bindings().API_KEY_ENCRYPTION_KEY;
  if (!secret)
    return json(
      { error: "Secure key storage is not available yet. Please try later." },
      503,
    );
  // Enforce a byte limit even for chunked requests before reading/parsing a key.
  const reader = request.body?.getReader();
  if (!reader) return json({ error: "Enter an API key." }, 400);
  let body = "",
    length = 0;
  const decoder = new TextDecoder();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    length += value.byteLength;
    if (length > 4096) {
      await reader.cancel();
      return json({ error: "The key is too long." }, 413);
    }
    body += decoder.decode(value, { stream: true });
  }
  body += decoder.decode();
  const input = JSON.parse(body);
  const apiKey = typeof input?.apiKey === "string" ? input.apiKey.trim() : "";
  if (!/^sk-[A-Za-z0-9_-]{16,1000}$/.test(apiKey))
    return json(
      { error: "Enter an OpenAI secret API key beginning with sk-." },
      400,
    );
  const envelope = await sealKey(apiKey, userId, secret);
  await database()
    .prepare(
      "INSERT INTO account_keys(user_id,envelope,updated_at) VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE SET envelope=excluded.envelope,updated_at=excluded.updated_at",
    )
    .bind(userId, envelope, new Date().toISOString())
    .run();
  return json({ ok: true });
}
