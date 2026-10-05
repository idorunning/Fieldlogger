import { env } from "cloudflare:workers";
export type Bindings = {
  DB: D1Database;
  BUCKET: R2Bucket;
  API_KEY_ENCRYPTION_KEY?: string;
  OPENAI_MODEL?: string;
  OPENAI_API_KEY?: string;
  SHARED_OPENAI_KEY_OWNER_ID?: string;
  COMMUNITY_ADMIN_USER_ID?: string;
  GOOGLE_PLAY_SERVICE_ACCOUNT?: string;
  GOOGLE_PLAY_BILLING_ENABLED?: string;
  GOOGLE_PLAY_PACKAGE?: string;
  OPENAI_AVATAR_MODEL?: string;
  OPENAI_ECONOMY_MODEL?: string;
  TRAIL_TRADER_ADDRESS?: string;
  TRAIL_COMPANY_NUMBER?: string;
  TRAIL_BILLING_LAUNCH_READY?: string;
  PLANTNET_API_KEY?: string;
  BIOCLIP_URL?: string;
  BIOCLIP_TOKEN?: string;
};
export function bindings() {
  return env as unknown as Bindings;
}
export function database() {
  const db = bindings().DB;
  if (!db)
    throw new Error(
      "Journal storage is unavailable. Your photo is safe on this device.",
    );
  return db;
}
export const json = (
  data: unknown,
  status = 200,
  headers: Record<string, string> = {},
) =>
  Response.json(data, {
    status,
    headers: {
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...headers,
    },
  });
export async function digest(value: string) {
  return hex(
    new Uint8Array(
      await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)),
    ),
  );
}
export function hex(bytes: Uint8Array) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}
export function randomToken() {
  return hex(crypto.getRandomValues(new Uint8Array(32)));
}
export async function hashPassword(password: string, salt: string) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  return hex(
    new Uint8Array(
      await crypto.subtle.deriveBits(
        {
          name: "PBKDF2",
          salt: new TextEncoder().encode(salt),
          iterations: 100000,
          hash: "SHA-256",
        },
        key,
        256,
      ),
    ),
  );
}
export function constantTimeEqual(a: string, b: string) {
  let mismatch = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++)
    mismatch |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return mismatch === 0;
}
export async function getUser(request: Request) {
  const token = request.headers
    .get("cookie")
    ?.split(";")
    .map((x) => x.trim())
    .find((x) => x.startsWith("fieldnotes_session="))
    ?.split("=")[1];
  if (!token || !/^[a-f0-9]{64}$/.test(token)) return null;
  return await database()
    .prepare(
      "SELECT users.id,users.email,users.name,COALESCE(a.status,'active') AS status FROM sessions JOIN users ON users.id=sessions.user_id LEFT JOIN member_access a ON a.user_id=users.id WHERE sessions.hash=? AND sessions.expires_at>?",
    )
    .bind(await digest(token), Date.now())
    .first<{ id: string; email: string; name: string }>();
}
export async function createSession(request: Request, id: string) {
  const token = randomToken();
  await database()
    .prepare("INSERT INTO sessions(hash,user_id,expires_at) VALUES(?,?,?)")
    .bind(await digest(token), id, Date.now() + 30 * 86400000)
    .run();
  return cookie(request, token, 30 * 86400);
}
export function cookie(request: Request, token: string, maxAge: number) {
  return `fieldnotes_session=${token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${maxAge}${new URL(request.url).protocol === "https:" ? "; Secure" : ""}`;
}
export function sameOrigin(request: Request) {
  const origin = request.headers.get("origin");
  return !origin || origin === new URL(request.url).origin;
}
