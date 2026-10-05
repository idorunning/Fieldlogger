import { lookupPlace } from "./place";
import { openDB, type DBSchema } from "idb";
import {mergeAchievementLedgers,validAchievementLedger} from "./achievements";
import {
  type Observation,
  type User,
  type Identification,
  type ObservationWire,
  toWire,
  validCoords,
} from "./types";
interface FieldDB extends DBSchema {
  observations: { key: string; value: Observation; indexes: { owner: string } };
  meta: { key: string; value: unknown };
}
export const db = () =>
  openDB<FieldDB>("fieldnotes-v1", 1, {
    upgrade(db) {
      const obs = db.createObjectStore("observations", { keyPath: "id" });
      obs.createIndex("owner", "owner");
      db.createObjectStore("meta");
    },
  });
export async function listLocal(owner: string) {
  return (
    await (await db()).getAllFromIndex("observations", "owner", owner)
  ).sort((a, b) => b.capturedAt.localeCompare(a.capturedAt));
}
export async function saveLocal(record: Observation) {
  await (await db()).put("observations", record);
}
export async function getMeta<T>(key: string) {
  return (await (await db()).get("meta", key)) as T | undefined;
}
export async function setMeta(key: string, value: unknown) {
  await (await db()).put("meta", value, key);
}
export async function adoptGuest(user: User) {
  const database = await db();
  const tx = database.transaction(["observations", "meta"], "readwrite");
  const guest = await tx.objectStore("observations").index("owner").getAll("guest");
  for (const row of guest)
    await tx.objectStore("observations").put({ ...row, owner: user.id, syncState: "pending" });
  const guestBadges=validAchievementLedger(await tx.objectStore("meta").get("achievements:guest")),ownBadges=validAchievementLedger(await tx.objectStore("meta").get("achievements:"+user.id));
  await tx.objectStore("meta").put(mergeAchievementLedgers(ownBadges,guestBadges),"achievements:"+user.id);
  await tx.objectStore("meta").delete("achievements:guest");
  await tx.done;
}
export async function requestPersistentStorage() {
  try {
    return await navigator.storage?.persist?.();
  } catch {
    return false;
  }
}
export async function identifyOne(owner: string, id: string, closer = false) {
  if (owner === "guest") throw new Error("Sign in to request a closer review.");
  if (!navigator.onLine) throw new Error("Reconnect to request a closer review.");
  const current = await fetch("/api/auth/me", { cache: "no-store" });
  if (!current.ok || (await current.json() as {user?: User}).user?.id !== owner)
    throw new Error("Sign in online to review this photo.");
  const record = await (await db()).get("observations", id);
  if (!record || record.owner !== owner || record.syncState !== "synced")
    throw new Error("Sync this photo before requesting a closer review.");
  const response = await fetch(`/api/observations/${encodeURIComponent(id)}/identify${closer ? "?closer=1" : ""}`, { method: "POST" });
  const data = await response.json() as { identification?: Identification; error?: string };
  if (!response.ok || !data.identification) throw new Error(data.error || "A closer review is unavailable. Your photo is still saved.");
  const tx = (await db()).transaction("observations", "readwrite");
  const latest = await tx.store.get(id);
  if (latest?.owner === owner) await tx.store.put({
    ...latest, identification: data.identification,
    ...(latest.confirmed ? {} : {name: data.identification.name, scientificName: data.identification.scientificName, category: data.identification.category}),
    analysisState: "complete", error: undefined,
  });
  await tx.done;
}
export async function syncRecords(owner: string, notify: () => void) {
  if (!navigator.onLine) return;
  await fillMissingPlaces(owner, notify);
  if (owner === "guest") return;
  const current = await fetch("/api/auth/me", { cache: "no-store" });
  if (!current.ok) return;
  const { user } = (await current.json()) as { user: User | null };
  if (user?.id !== owner) return;
  const local = await listLocal(owner);
  for (const record of local) {
    if (record.syncState === "synced" && record.analysisState !== "pending")
      continue;
    try {
      if (record.syncState !== "synced") {
        const form = new FormData();
        form.set("metadata", JSON.stringify(toWire(record)));
        form.set("photo", record.photo, "discovery.jpg");
        const response = await fetch(`/api/observations/${record.id}`, {
          method: "PUT",
          body: form,
        });
        if (response.status === 401) return;
        if (!response.ok)
          throw new Error(
            ((await response.json()) as { error?: string }).error ||
              "Upload paused. We’ll try again.",
          );
        const latest = await (await db()).get("observations", record.id);
        if (latest && latest.revision === record.revision)
          await saveLocal({ ...latest, syncState: "synced", error: undefined });
      }
      if (record.analysisState === "pending") {
        const response = await fetch(
          `/api/observations/${record.id}/identify`,
          { method: "POST" },
        );
        if (response.status === 503) {
          const data = await response.json() as { error?: string };
          const latest = await (await db()).get("observations", record.id);
          if (latest?.owner === owner) await saveLocal({ ...latest, error: data.error || "Identification is temporarily unavailable. Your photo is still saved." });
          notify();
          continue;
        }
        if (response.status === 401) return;
        if (!response.ok)
          throw new Error(
            ((await response.json()) as { error?: string }).error ||
              "Identification paused.",
          );
        const data = (await response.json()) as {
          identification: Identification;
        };
        const latest = await (await db()).get("observations", record.id);
        if (latest)
          await saveLocal({
            ...latest,
            identification: data.identification,
            ...(latest.confirmed
              ? {}
              : {
                  name: data.identification.name,
                  scientificName: data.identification.scientificName,
                  category: data.identification.category,
                }),
            analysisState: "complete",
            error: undefined,
          });
      }
    } catch (error) {
      const latest = await (await db()).get("observations", record.id);
      if (latest)
        await saveLocal({
          ...latest,
          error: error instanceof Error ? error.message : "Sync paused",
          syncState: latest.syncState === "synced" ? "synced" : "error",
        });
    }
    notify();
  }
  const response = await fetch("/api/observations", { cache: "no-store" });
  if (!response.ok) return;
  const { observations } = (await response.json()) as {
    observations: ObservationWire[];
  };
  for (const remote of observations) {
    const exists = await (await db()).get("observations", remote.id);
    if (exists && exists.syncState !== "synced") continue;
    if (exists && exists.revision > remote.revision) continue;
    if (
      exists &&
      exists.updatedAt === remote.updatedAt &&
      exists.analysisState === remote.analysisState
    )
      continue;
    let photo = exists?.photo;
    if (!photo) {
      const res = await fetch(`/api/observations/${remote.id}/photo`);
      if (!res.ok) continue;
      photo = await res.blob();
    }
    await saveLocal({ ...remote, owner, photo, syncState: "synced" });
  }
  await fillMissingPlaces(owner, notify);
  notify();
}

export async function clearLocalAccount(owner: string) {
  const database = await db();
  const tx = database.transaction(["observations", "meta"], "readwrite");
  const keys = await tx.objectStore("observations").index("owner").getAllKeys(owner);
  for (const key of keys) await tx.objectStore("observations").delete(key);
  await tx.objectStore("meta").delete("activeUser");
  await tx.objectStore("meta").delete("achievements:"+owner);
  await tx.done;
}

// Re-read inside a transaction so a delayed lookup cannot overwrite a field edit.
export async function fillMissingPlaces(owner: string, notify: () => void) {
  for (const record of (await listLocal(owner)).filter(r => !r.place.trim() && validCoords(r.latitude, r.longitude)).slice(0, 20)) {
    const place = await lookupPlace(record.latitude, record.longitude);
    if (!place) continue;
    const tx = (await db()).transaction("observations", "readwrite");
    const latest = await tx.store.get(record.id);
    if (latest && latest.owner === owner && !latest.place.trim() && latest.latitude === record.latitude && latest.longitude === record.longitude) {
      await tx.store.put({ ...latest, place, updatedAt: new Date().toISOString(), revision: latest.revision + 1, syncState: "pending" });
    }
    await tx.done;
    notify();
  }
}
export async function attachLateGps(id: string, owner: string, position: GeolocationPosition) {
  const tx = (await db()).transaction("observations", "readwrite");
  const record = await tx.store.get(id);
  if (record && record.owner === owner && !validCoords(record.latitude, record.longitude)) {
    await tx.store.put({ ...record, latitude: position.coords.latitude, longitude: position.coords.longitude,
      accuracy: position.coords.accuracy, locationSource: "gps", updatedAt: new Date().toISOString(), revision: record.revision + 1, syncState: "pending" });
  }
  await tx.done;
}
