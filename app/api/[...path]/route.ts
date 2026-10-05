import { reversePlace } from "@/lib/place-server";
import { mapSearch,weather } from "@/lib/outdoors";
import { z } from "zod";
import {
  bindings,
  database,
  json,
  hashPassword,
  randomToken,
  constantTimeEqual,
  getUser,
  createSession,
  cookie,
  digest,
  sameOrigin,
  hex,
} from "@/lib/server";
import { categories, type ObservationWire } from "@/lib/types";
import { identify } from "@/lib/identify";
import {
  accountKeySettings,
  getServiceKey,
  sharedKeyConfigured,
  keyStatus,
} from "@/lib/account-key";
import { community, decorateOwnRecords, syncOwnSocial, removePublicFiles, limitAction,actionLease } from "@/lib/community";
import { OpenAIConnectionError } from "@/lib/openai-response";
import {billingRoute,plansResponse,reservePhoto} from '@/lib/billing';
import {adminRoute,accessFor} from '@/lib/admin';
import {billingSetupRoute,disconnectBillingForDeletedOwner} from '@/lib/billing-config';
import {avatarHandler} from '@/lib/avatar-server';
import {exportJournal} from '@/lib/export-server';
import {AiBudgetError} from '@/lib/ai-budget';
import {ensureRuntimeSchema,RuntimeSchemaError} from '@/lib/runtime-migrations';
export const dynamic = "force-dynamic";
const authSchema = z.object({
  email: z
    .string()
    .trim()
    .email()
    .max(254)
    .transform((s) => s.toLowerCase()),
  password: z.string().min(8).max(256),
  name: z.string().trim().min(1).max(100).optional(),
});
const observationSchema = z
  .object({
    id: z.string().uuid(),
    capturedAt: z.string().datetime(),
    localDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
    localHour: z.number().int().min(0).max(23),
    timezone: z.string().max(100),
    latitude: z.number().min(-90).max(90).nullable(),
    longitude: z.number().min(-180).max(180).nullable(),
    accuracy: z.number().nonnegative().nullable(),
    locationSource: z.enum(["gps", "exif", "manual", "none"]),
    place: z.string().max(160),
    note: z.string().max(3000),
    category: z.enum(categories),
    name: z.string().max(160),
    scientificName: z.string().max(180),
    confirmed: z.boolean(),
    identification: z.unknown().nullable(),
    analysisState: z.enum(["pending", "complete", "error"]),
    archived: z.boolean().optional(),
    acorned: z.boolean().optional(),
    checkLater: z.boolean().optional(),
    updatedAt: z.string().datetime(),
    revision: z.number().int().min(1),
  })
  .refine(
    (x) => (x.latitude === null) === (x.longitude === null),
    "Coordinates must be a pair",
  );
async function auth(request: Request, path: string) {
  if (path === "me") return json({ user: await getUser(request) });
  if (path === "logout") {
    const token = request.headers
      .get("cookie")
      ?.match(/(?:^|;\s*)fieldnotes_session=([a-f0-9]+)/)?.[1];
    if (token)
      await database()
        .prepare("DELETE FROM sessions WHERE hash=?")
        .bind(await digest(token))
        .run();
    return json({ ok: true }, 200, { "Set-Cookie": cookie(request, "", 0) });
  }
  if (!["login", "register"].includes(path))
    return json({ error: "Not found" }, 404);
  const input = authSchema.parse(await request.json());
  const key = await digest(
    (request.headers.get("cf-connecting-ip") || "local") + ":" + input.email,
  );
  const attempt = await database()
    .prepare("SELECT count,reset_at FROM auth_attempts WHERE key=?")
    .bind(key)
    .first<{ count: number; reset_at: number }>();
  if (attempt && attempt.reset_at > Date.now() && attempt.count >= 8)
    return json(
      { error: "Too many attempts. Please try again in 15 minutes." },
      429,
    );
  await database()
    .prepare(
      "INSERT INTO auth_attempts(key,count,reset_at) VALUES(?,1,?) ON CONFLICT(key) DO UPDATE SET count=CASE WHEN reset_at<? THEN 1 ELSE count+1 END,reset_at=CASE WHEN reset_at<? THEN excluded.reset_at ELSE reset_at END",
    )
    .bind(key, Date.now() + 900000, Date.now(), Date.now())
    .run();
  let user = await database()
    .prepare("SELECT * FROM users WHERE email=?")
    .bind(input.email)
    .first<any>();
  if (path === "register") {
    const ip=request.headers.get('cf-connecting-ip');
    if(!await limitAction('register-ip:'+await digest(ip||'unknown'),ip?5:20,3600000)||!await limitAction('register-day:'+await digest(ip||'unknown'),ip?20:100,86400000))return json({error:'Too many accounts have been created from this connection. Please try later.'},429);
    if (user)
      return json(
        { error: "An account already uses that email. Please sign in." },
        409,
      );
    if (!input.name) return json({ error: "Please enter your name." }, 400);
    const salt = randomToken(),
      id = crypto.randomUUID();
    await database()
      .prepare(
        "INSERT INTO users(id,email,name,password_hash,salt,created_at) VALUES(?,?,?,?,?,?)",
      )
      .bind(
        id,
        input.email,
        input.name,
        await hashPassword(input.password, salt),
        salt,
        new Date().toISOString(),
      )
      .run();
    user = { id, email: input.email, name: input.name };
  } else {
    const hash = await hashPassword(
      input.password,
      user?.salt || "nonexistent-user-constant-salt",
    );
    if (!user || !constantTimeEqual(hash, user.password_hash))
      return json({ error: "That email and password did not match." }, 401);
  }
  await database()
    .prepare("DELETE FROM auth_attempts WHERE key=?")
    .bind(key)
    .run();
  return json(
    { user: { id: user.id, email: user.email, name: user.name } },
    200,
    { "Set-Cookie": await createSession(request, user.id) },
  );
}
async function handle(request: Request) {
  let uploadLease:{key:string;resetAt:number}|null=null;
  try {
    const path = new URL(request.url).pathname
      .replace(/^\/api\//, "")
      .split("/");
    if (request.method !== "GET" && !sameOrigin(request))
      return json({ error: "Request origin not allowed" }, 403);
    const requestLimit=path[0]==='observations'&&request.method==='PUT'?6*1024*1024:path[0]==='avatar'&&path[1]==='photo'?3*1024*1024:path[0]==='admin'&&path[1]==='billing-setup'?40*1024:path[0]==='billing'?16*1024:['auth','account','settings'].includes(path[0])?8*1024:64*1024;
    if (
      request.method !== "GET" &&
      Number(request.headers.get("content-length") || 0) > requestLimit
    )
      return json({ error: "Request is too large." }, 413);
    if(request.method!=='GET'&&request.body){
      const reader=request.body.getReader(),chunks:Uint8Array[]=[];let bytes=0;
      for(;;){const {done,value}=await reader.read();if(done)break;bytes+=value.byteLength;if(bytes>requestLimit){await reader.cancel();return json({error:'Request is too large.'},413);}chunks.push(value);}
      const body=new Uint8Array(bytes);let offset=0;for(const chunk of chunks){body.set(chunk,offset);offset+=chunk.byteLength;}
      const headers=new Headers(request.headers);headers.delete('content-length');request=new Request(request.url,{method:request.method,headers,body});
    }
    await ensureRuntimeSchema(database());
    if(path[0]==='plans'&&request.method==='GET')return plansResponse();
    if (path[0] === "place" && request.method === "GET") return await reversePlace(request);
    if (path[0] === "status") {
      const viewer = await getUser(request);
      const saved = viewer ? await keyStatus(viewer.id) : null;
      return json({
        storage: !!bindings().DB && !!bindings().BUCKET,
        identification: !!saved?.hasKey || await sharedKeyConfigured(),
        plantnet: !!bindings().PLANTNET_API_KEY,
        bioclip: !!bindings().BIOCLIP_URL,
      });
    }
    if (path[0] === "auth") {
      if (path[1] === "me" && request.method === "GET")
        return await auth(request, "me");
      if (request.method !== "POST")
        return json({ error: "Method not allowed" }, 405);
      return await auth(request, path[1]);
    }
    const user = await getUser(request);
    if (!user) return json({ error: "Sign in to sync your journal." }, 401);
    if(path[0]==='billing')return await billingRoute(request,path.slice(1),user);
    if(path[0]==='admin'&&path[1]==='billing-setup'&&path.length===2)return await billingSetupRoute(request,user);
    if(path[0]==='admin')return await adminRoute(request,path.slice(1),user);
    const access=await accessFor(user);
    if(access.status==='suspended'&&request.method!=='GET'&&path[0]!=='account')return json({error:'Your account is suspended. Your private journal and free downloads remain available.',code:'account_suspended'},403);
    if(path[0]==='avatar')return await avatarHandler(request,path.slice(1),user);
    if(path[0]==='export'&&request.method==='GET')return await exportJournal(request,user);
    if (path[0] === "social") return await community(request,path.slice(1),user);
    if (path[0] === "map-search" && request.method === "GET") return await mapSearch(request);
    if (path[0] === "weather" && request.method === "GET") return await weather(request);
    if (path[0] === "account" && request.method === "DELETE") {
      if (request.headers.get("origin") !== new URL(request.url).origin)
        return json({ error: "Open your account in My Trail Log to delete it." }, 403);
      const input = z.object({ password: z.string().min(8).max(256) }).parse(await request.json());
      const account = await database().prepare("SELECT password_hash,salt FROM users WHERE id=?").bind(user.id).first<{password_hash: string; salt: string}>();
      if (!account || !constantTimeEqual(await hashPassword(input.password, account.salt), account.password_hash))
        return json({error: "That password did not match. Your account has not been deleted."}, 401);
      const billingDisconnectWrites=await disconnectBillingForDeletedOwner(user.id);
      // Revoke sessions first so queued uploads cannot restart during deletion.
      await database().prepare("DELETE FROM sessions WHERE user_id=?").bind(user.id).run();
      await database().batch([database().prepare('UPDATE avatar_generations SET locked_until=0,token=NULL WHERE user_id=?').bind(user.id),database().prepare('DELETE FROM auth_attempts WHERE key=?').bind('avatar-lock:'+user.id)]);
      await removePublicFiles(user.id);
      let cursor: string | undefined;
      do {
        const page = await bindings().BUCKET.list({prefix: user.id + "/", cursor});
        if (page.objects.length) await bindings().BUCKET.delete(page.objects.map(object => object.key));
        cursor = page.truncated ? page.cursor : undefined;
      } while (cursor);
      await database().batch([
        ...billingDisconnectWrites,
        database().prepare("DELETE FROM observations WHERE user_id=?").bind(user.id),
        database().prepare("DELETE FROM account_keys WHERE user_id=?").bind(user.id),
        database().prepare("DELETE FROM users WHERE id=?").bind(user.id),
      ]);
      return json({ok:true}, 200, {"Set-Cookie":cookie(request, "", 0)});
    }
    if (path[0] === "settings" && path[1] === "openai-key") {
      if (
        request.method !== "GET" &&
        request.headers.get("origin") !== new URL(request.url).origin
      )
        return json(
          { error: "Open API key settings in this app to make changes." },
          403,
        );
      return await accountKeySettings(request, user.id, path[2]);
    }
    if (path[0] !== "observations") return json({ error: "Not found" }, 404);
    const id = path[1];
    if (!id && request.method === "GET") {
      return json({observations: await decorateOwnRecords(user.id)});
    }

    if (!z.string().uuid().safeParse(id).success)
      return json({ error: "Invalid observation" }, 400);
    let existing = await database()
      .prepare(
        "SELECT data,photo_key FROM observations WHERE id=? AND user_id=?",
      )
      .bind(id, user.id)
      .first<{ data: string; photo_key: string }>();
    if (path[2] === "photo" && request.method === "GET") {
      if (!existing) return json({ error: "Not found" }, 404);
      const object = await bindings().BUCKET.get(existing.photo_key);
      if (!object) return json({ error: "Image unavailable" }, 404);
      return new Response(object.body, {
        headers: {
          "Content-Type": "image/jpeg",
          "Cache-Control": "private, no-store",
          "X-Content-Type-Options": "nosniff",
        },
      });
    }
    if (path[2] === "identify" && request.method === "POST") {
      if (!existing)
        return json({ error: "Photo must finish uploading first." }, 404);
      const closerRetry=new URL(request.url).searchParams.get('closer')==='1';
      const cached=JSON.parse(existing.data) as ObservationWire;
      if(cached.analysisState==='complete'&&cached.identification&&!(closerRetry&&cached.identification.recognition?.mode==='tentative'))return json({identification:cached.identification});
      if(!await limitAction('identify:'+user.id,20,60000))return json({error:'Please wait a minute. Your photo is saved.'},429);
      const lease=await actionLease('identify-photo:'+id,300000);
      if(!lease)return json({error:'This photo is already being identified. Try again shortly.'},429);
      try {
        // Re-read inside the lease; keep it through the final save so callers
        // with stale pending records cannot pay for the same stage twice.
        const owned=await database().prepare('SELECT data,photo_key FROM observations WHERE id=? AND user_id=?').bind(id,user.id).first<{data:string;photo_key:string}>();
        if(!owned)return json({error:'Photo unavailable.'},404);
        const record=JSON.parse(owned.data) as ObservationWire;
        if(record.analysisState==='complete'&&record.identification&&!(closerRetry&&record.identification.recognition?.mode==='tentative'))return json({identification:record.identification});
        const apiKey=await getServiceKey(user.id)||'';
        const object=await bindings().BUCKET.get(owned.photo_key);
        if(!object)return json({error:'Photo unavailable.'},404);
        const result=await identify(await object.arrayBuffer(),record,apiKey,user.id,closerRetry);
        const fresh=await database().prepare('SELECT data FROM observations WHERE id=? AND user_id=?').bind(id,user.id).first<{data:string}>();
        if(!fresh)return json({error:'Not found'},404);
        const latest=JSON.parse(fresh.data),next={...latest,identification:result,analysisState:'complete',...(latest.confirmed?{}:{name:result.name,scientificName:result.scientificName,category:result.category})};
        const saved=await database().prepare('UPDATE observations SET data=? WHERE id=? AND user_id=?').bind(JSON.stringify(next),id,user.id).run();
        if(!saved.meta.changes)return json({error:'Photo unavailable.'},404);
        return json({identification:result});
      } finally {await database().prepare('DELETE FROM auth_attempts WHERE key=? AND reset_at=?').bind('identify-photo:'+id,lease.reset_at).run().catch(()=>{});}
    }
    if (request.method === "PUT" && !path[2]) {
      if(!existing){
        const key='upload-photo:'+id,lock=await actionLease(key,180000);
        if(!lock)return json({error:'This photo is already uploading. Sync again shortly.'},409);
        uploadLease={key,resetAt:lock.reset_at};
        existing=await database().prepare('SELECT data,photo_key FROM observations WHERE id=? AND user_id=?').bind(id,user.id).first<{data:string;photo_key:string}>();
      }
      const owner=await database().prepare('SELECT user_id FROM observations WHERE id=?').bind(id).first<{user_id:string}>();
      if(owner&&owner.user_id!==user.id)return json({error:'Photo unavailable.'},404);
      const form = await request.formData();
      const validated = observationSchema.parse(
        JSON.parse(String(form.get("metadata"))),
      );
      if (validated.id !== id)
        return json({ error: "Photo identity mismatch" }, 400);
      const photo = form.get("photo");
      if (
        !(photo instanceof File) ||
        photo.size > 4 * 1024 * 1024 ||
        photo.size < 10
      )
        return json({ error: "Use a JPEG photo under 4 MB." }, 400);
      const bytes = await photo.arrayBuffer();
      const magic = new Uint8Array(bytes);
      if (magic[0] !== 255 || magic[1] !== 216 || magic[2] !== 255)
        return json({ error: "Please choose a valid JPEG image." }, 400);
      const prior = existing ? JSON.parse(existing.data) : null;
      const photoDigest=hex(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)));
      if(existing){
        let priorDigest=prior?.photoDigest;
        if(!priorDigest){const stored=await bindings().BUCKET.get(existing.photo_key);if(!stored)return json({error:'Photo unavailable.'},404);priorDigest=hex(new Uint8Array(await crypto.subtle.digest('SHA-256',await stored.arrayBuffer())));}
        if(photoDigest!==priorDigest)return json({error:'This photo is already saved. Add the changed image as a new discovery.'},409);
      }
      if (prior && prior.revision > validated.revision)
        return json(
          {
            error:
              "This discovery was edited on another device. Reload your journal before editing again.",
          },
          409,
        );
      // The client cannot manufacture reference evidence or completed AI analyses.
      const metadata = {
        ...validated,
        photoDigest,
        identification: prior?.identification || null,
        analysisState: prior?.identification ? "complete" : "pending",
        archived: validated.archived ?? prior?.archived ?? false,
        acorned: validated.acorned ?? prior?.acorned ?? false,
        checkLater: validated.checkLater ?? prior?.checkLater ?? false,
      };
      const key = `${user.id}/${id}.jpg`;
      if(!existing){const allowance=await reservePhoto(user.id,id);if(!allowance.allowed)return json({error:'You have reached your cloud photo allowance. This photo stays on your phone; existing photos and ZIP downloads remain free.',code:'photo_limit',keep_local:true,billing:allowance.billing},429);}
      try{
      if(!existing)await bindings().BUCKET.put(key, bytes, {
        httpMetadata: { contentType: "image/jpeg" },
      });
      const saved=await database()
        .prepare(
          "INSERT INTO observations(id,user_id,data,photo_key,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data,updated_at=excluded.updated_at WHERE observations.user_id=excluded.user_id",
        )
        .bind(id, user.id, JSON.stringify(metadata), key, metadata.updatedAt)
        .run();
      if(!saved.meta.changes)return json({error:'Photo unavailable.'},409);
      await syncOwnSocial(user.id,id,{archived: metadata.archived,acorned:validated.acorned,checkLater:validated.checkLater});
      return json({ ok: true });
      }catch(error){
        const retained=await database().prepare('SELECT id FROM observations WHERE id=? AND user_id=?').bind(id,user.id).first();
        if(!retained)await bindings().BUCKET.delete(key).catch(()=>{});
        throw error;
      }
    }
    return json({ error: "Not found" }, 404);
  } catch (error) {
    if(error instanceof RuntimeSchemaError){console.error('Static journal schema setup failed');return json({error:error.message,code:'storage_upgrading',keep_local:true},503);}
    if(error instanceof AiBudgetError)return json({error:error.message,code:'analysis_resting',keep_local:true},503);
    if (error instanceof OpenAIConnectionError) {
      const administration = new URL(request.url).pathname.startsWith("/api/settings/");
      return json({ error: administration ? error.message : "The identification service is temporarily unavailable. Your photo is saved in your private journal." }, administration ? error.status : 503);
    }
    if (error instanceof z.ZodError || error instanceof SyntaxError)
      return json(
        {
          error: "Some details were invalid. Please check them and try again.",
        },
        400,
      );
    console.error("Fieldnotes request failed");
    return json(
      {
        error:
          error instanceof Error &&
          /photo|Identification|Journal|image/i.test(error.message)
            ? error.message
            : "Journal service is unavailable. Your local discoveries are safe.",
      },
      500,
    );
  }finally{
    if(uploadLease)await database().prepare('DELETE FROM auth_attempts WHERE key=? AND reset_at=?').bind(uploadLease.key,uploadLease.resetAt).run().catch(()=>{});
  }
}
export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const DELETE = handle;
