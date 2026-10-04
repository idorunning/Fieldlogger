import { openDB, type DBSchema } from "idb";
import {
  type Observation,
  type User,
  type Identification,
  type ObservationWire,
  toWire,
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
  const tx = database.transaction("observations", "readwrite");
  const guest = await tx.store.index("owner").getAll("guest");
  for (const row of guest)
    await tx.store.put({ ...row, owner: user.id, syncState: "pending" });
  await tx.done;
}
export async function requestPersistentStorage() {
  try {
    return await navigator.storage?.persist?.();
  } catch {
    return false;
  }
}
export async function syncRecords(owner: string, notify: () => void) {
  if (owner === "guest" || !navigator.onLine) return;
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
  notify();
}
