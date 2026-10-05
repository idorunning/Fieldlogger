import { validCoords } from "./types";

export function placeCell(latitude: number, longitude: number) {
  return `${latitude.toFixed(4)},${longitude.toFixed(4)}`;
}
// A nearby mapped place is context, not a claim about the precise photo subject.
export function photonPlace(data: unknown): string {
  const p = (data as { features?: { properties?: Record<string, unknown> }[] })?.features?.[0]?.properties;
  if (!p) return "";
  const values = [p.name || p.street, p.city || p.district || p.county || p.state];
  const parts = [...new Set(values.filter((v): v is string => typeof v === "string" && !!v.trim()))];
  return (parts.join(", ") || (typeof p.country === "string" ? p.country : "")).slice(0, 160);
}

const lookups = new Map<string, Promise<string>>();
let queue: Promise<unknown> = Promise.resolve();
export function lookupPlace(latitude: number | null, longitude: number | null): Promise<string> {
  if (!validCoords(latitude, longitude)) return Promise.resolve("");
  const key = `place:${placeCell(latitude!, longitude!)}`;
  if (lookups.has(key)) return lookups.get(key)!;
  const task = queue.catch(() => {}).then(async () => {
    const { getMeta, setMeta } = await import("./local");
    const cached = await getMeta<{ place: string; expires: number }>(key);
    if (cached && cached.expires > Date.now()) return cached.place;
    if (!navigator.onLine) return "";
    try {
      const response = await fetch(`/api/place?lat=${latitude}&lon=${longitude}`, { signal: AbortSignal.timeout(8000) });
      if (!response.ok) throw Error("Place lookup paused");
      const result = await response.json() as { place: string };
      const place = typeof result.place === "string" ? result.place.slice(0, 160) : "";
      await setMeta(key, { place, expires: Date.now() + (place ? 30 * 86400000 : 300000) });
      return place;
    } catch {
      if (navigator.onLine) await setMeta(key, { place: "", expires: Date.now() + 60000 });
      return "";
    } finally {
      await new Promise(resolve => setTimeout(resolve, 1100));
    }
  }).catch(() => "");
  lookups.set(key, task);
  queue = task;
  void task.finally(() => lookups.delete(key));
  return task;
}
