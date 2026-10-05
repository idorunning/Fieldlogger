import { bindings, database, json } from "./server";
import { placeCell, photonPlace } from "./place";
import { validCoords } from "./types";

export async function reversePlace(request: Request) {
  const url = new URL(request.url);
  const latText = url.searchParams.get("lat"), lonText = url.searchParams.get("lon");
  const lat = Number(latText), lon = Number(lonText);
  if (!latText?.trim() || !lonText?.trim() || !validCoords(lat, lon)) return json({ error: "Valid coordinates are required" }, 400);
  const key = `places/v1/${placeCell(lat, lon)}.json`;
  const bucket = bindings().BUCKET;
  const cached = await bucket.get(key);
  if (cached) {
    const value = await cached.json<{ place: string; expires: number }>();
    if (value.expires > Date.now()) return json({ place: value.place, source: "OpenStreetMap / Photon" });
  }
  // One global gate across Worker instances, without storing visitors' IPs.
  const now = Date.now();
  const gate = await database().prepare("INSERT INTO auth_attempts(key,count,reset_at) VALUES('photon:global',1,?) ON CONFLICT(key) DO UPDATE SET reset_at=excluded.reset_at WHERE reset_at<=? RETURNING key").bind(now + 1000, now).first();
  if (!gate) return json({ error: "Place lookup will retry shortly" }, 429, { "Retry-After": "2" });
  const upstream = new URL("https://photon.komoot.io/reverse");
  upstream.search = new URLSearchParams({ lat: lat.toFixed(4), lon: lon.toFixed(4), lang: "en", radius: "1" }).toString();
  try {
    const response = await fetch(upstream, { headers: { "User-Agent": "MyTrailLog/2.0.1 (+https://fieldlogger.co.uk)", Accept: "application/json" }, signal: AbortSignal.timeout(6000) });
    if (!response.ok) return json({ error: "Place lookup is temporarily unavailable" }, 503);
    const place = photonPlace(await response.json());
    await bucket.put(key, JSON.stringify({ place, expires: now + (place ? 30 * 86400000 : 3600000) }), { httpMetadata: { contentType: "application/json" } });
    return json({ place, source: "OpenStreetMap / Photon" });
  } catch { return json({ error: "Place lookup is temporarily unavailable" }, 503); }
}
