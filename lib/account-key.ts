import jpeg from 'jpeg-js';
import {STRONG_VISION_MODEL,visionDataUrl,visionRequestOptions} from './vision-model';
import {reserveAiBudget,settleAiBudget} from './ai-budget';
import {serviceKeyOwner} from './key-maintenance-policy';
import { bindings, database, json } from "./server";
import { sealKey, unsealKey } from "./key-crypto";

import { inspectOpenAIResponse } from "./openai-response";
export async function keyStatus(userId: string) {
  const row = await database()
    .prepare("SELECT updated_at FROM account_keys WHERE user_id=?")
    .bind(userId)
    .first<{ updated_at: string }>();
  return {
    hasKey: !!row,
    serverKey: await sharedKeyConfigured(),
    canSave: !!bindings().API_KEY_ENCRYPTION_KEY && serviceKeyOwner(userId,bindings()),
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
  if (!serviceKeyOwner(userId,bindings()))
    return json({error:"Identification is managed by My Trail Log."},403);
  if (request.method === "POST" && action === "test") {
    // A known tiny JPEG verifies image input and a complete primary-model reply.
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
    const apiKey = await getServiceKey(userId);
    if (!apiKey) return json({ error: "Identification is not configured yet." }, 503);
    const data=new Uint8Array(32*32*4);for(let i=0;i<data.length;i+=4){data[i]=40;data[i+1]=176;data[i+2]=80;data[i+3]=255;}
    const probe=visionDataUrl(new Uint8Array(jpeg.encode({width:32,height:32,data},80).data));data.fill(0);
    const reservation=await reserveAiBudget(userId,null,'model-test',STRONG_VISION_MODEL);
    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        ...visionRequestOptions(STRONG_VISION_MODEL, "test"),
        store: false,
        input:[{role:'user',content:[{type:'input_text',text:'What is the dominant colour of this square? Return the required JSON.'},{type:'input_image',image_url:probe,detail:'high'}]}],
        text:{format:{type:'json_schema',name:'vision_connection',strict:true,schema:{type:'object',additionalProperties:false,properties:{colour:{type:'string',enum:['green','red','blue','other']}},required:['colour']}}},
      }),
      signal: AbortSignal.timeout(30000),
    });
    await inspectOpenAIResponse(response, apiKey);
    const result:any=await response.json();
    await settleAiBudget(reservation,result).catch(()=>{});
    const output=result.output?.flatMap((item:any)=>item.content||[]).find((item:any)=>item.type==='output_text')?.text;
    let completed=false;try{completed=result.status==='completed'&&JSON.parse(output).colour==='green';}catch{}
    if(!completed)return json({error:'The identification connection did not complete its image check. Please try later.'},502);
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

export async function sharedKeyConfigured() {
  if (bindings().OPENAI_API_KEY) return true;
  const owner = bindings().SHARED_OPENAI_KEY_OWNER_ID;
  if (!owner) return false;
  return !!await database().prepare("SELECT user_id FROM account_keys WHERE user_id=?").bind(owner).first();
}
export async function getServiceKey(userId: string) {
  if (bindings().OPENAI_API_KEY) return bindings().OPENAI_API_KEY!;
  const owner = bindings().SHARED_OPENAI_KEY_OWNER_ID;
  return getAccountKey(owner || userId);
}
