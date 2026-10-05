import { reversePlace } from "@/lib/place-server";
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
} from "@/lib/server";
import { categories, type ObservationWire } from "@/lib/types";
import { identify } from "@/lib/identify";
import {
  accountKeySettings,
  getAccountKey,
  keyStatus,
} from "@/lib/account-key";
import { OpenAIConnectionError } from "@/lib/openai-response";
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
  try {
    const path = new URL(request.url).pathname
      .replace(/^\/api\//, "")
      .split("/");
    if (request.method !== "GET" && !sameOrigin(request))
      return json({ error: "Request origin not allowed" }, 403);
    if (
      request.method !== "GET" &&
      Number(request.headers.get("content-length") || 0) > 6 * 1024 * 1024
    )
      return json({ error: "Image is too large." }, 413);
    if (path[0] === "place" && request.method === "GET") return await reversePlace(request);
    if (path[0] === "status") {
      const viewer = await getUser(request);
      const saved = viewer ? await keyStatus(viewer.id) : null;
      return json({
        storage: !!bindings().DB && !!bindings().BUCKET,
        identification: !!saved?.hasKey,
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
    if (path[0] === "account" && request.method === "DELETE") {
      if (request.headers.get("origin") !== new URL(request.url).origin)
        return json({ error: "Open your account in My Trail Log to delete it." }, 403);
      const input = z.object({ password: z.string().min(8).max(256) }).parse(await request.json());
      const account = await database().prepare("SELECT password_hash,salt FROM users WHERE id=?").bind(user.id).first<{password_hash: string; salt: string}>();
      if (!account || !constantTimeEqual(await hashPassword(input.password, account.salt), account.password_hash))
        return json({error: "That password did not match. Your account has not been deleted."}, 401);
      // Revoke sessions first so queued uploads cannot restart during deletion.
      await database().prepare("DELETE FROM sessions WHERE user_id=?").bind(user.id).run();
      let cursor: string | undefined;
      do {
        const page = await bindings().BUCKET.list({prefix: user.id + "/", cursor});
        if (page.objects.length) await bindings().BUCKET.delete(page.objects.map(object => object.key));
        cursor = page.truncated ? page.cursor : undefined;
      } while (cursor);
      await database().batch([
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
      const rows = await database()
        .prepare(
          "SELECT data FROM observations WHERE user_id=? ORDER BY updated_at DESC",
        )
        .bind(user.id)
        .all<{ data: string }>();
      return json({
        observations: rows.results.map((r) => JSON.parse(r.data)),
      });
    }
    if (!z.string().uuid().safeParse(id).success)
      return json({ error: "Invalid observation" }, 400);
    const existing = await database()
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
      const record = JSON.parse(existing.data) as ObservationWire;
      if (record.analysisState === "complete" && record.identification)
        return json({ identification: record.identification });
      const apiKey = await getAccountKey(user.id);
      if (!apiKey)
        return json(
          {
            error:
              "Open API key settings to connect photo identification. Your photo is saved.",
          },
          503,
        );
      const object = await bindings().BUCKET.get(existing.photo_key);
      if (!object) return json({ error: "Photo unavailable" }, 404);
      const result = await identify(await object.arrayBuffer(), record, apiKey);
      const fresh = await database()
        .prepare("SELECT data FROM observations WHERE id=? AND user_id=?")
        .bind(id, user.id)
        .first<{ data: string }>();
      if (!fresh) return json({ error: "Not found" }, 404);
      const latest = JSON.parse(fresh.data);
      const next = {
        ...latest,
        identification: result,
        analysisState: "complete",
        ...(latest.confirmed
          ? {}
          : {
              name: result.name,
              scientificName: result.scientificName,
              category: result.category,
            }),
      };
      await database()
        .prepare("UPDATE observations SET data=? WHERE id=? AND user_id=?")
        .bind(JSON.stringify(next), id, user.id)
        .run();
      return json({ identification: result });
    }
    if (request.method === "PUT" && !path[2]) {
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
        identification: prior?.identification || null,
        analysisState: prior?.identification ? "complete" : "pending",
      };
      const key = `${user.id}/${id}.jpg`;
      await bindings().BUCKET.put(key, bytes, {
        httpMetadata: { contentType: "image/jpeg" },
      });
      await database()
        .prepare(
          "INSERT INTO observations(id,user_id,data,photo_key,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data,updated_at=excluded.updated_at WHERE observations.user_id=excluded.user_id",
        )
        .bind(id, user.id, JSON.stringify(metadata), key, metadata.updatedAt)
        .run();
      return json({ ok: true });
    }
    return json({ error: "Not found" }, 404);
  } catch (error) {
    if (error instanceof OpenAIConnectionError)
      return json({ error: error.message }, error.status);
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
  }
}
export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const DELETE = handle;
