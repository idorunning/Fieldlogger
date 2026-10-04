/* App shell and original photos are separate: API responses are never cached. */
const CACHE = "fieldnotes-shell-v1";
const SHELL = [
  "/",
  "/favicon.svg",
  "/icon-192.png",
  "/icon-512.png",
  "/manifest.webmanifest",
  "/woodland.jpg",
];
self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE);
      await cache.addAll(SHELL);
      const r = await fetch("/offline-assets.json");
      if (r.ok && r.headers.get("content-type")?.includes("json")) {
        const assets = await r.json();
        await cache.addAll(assets);
      }
    })(),
  );
  self.skipWaiting();
});
self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      for (const name of await caches.keys())
        if (name.startsWith("fieldnotes-shell-") && name !== CACHE)
          await caches.delete(name);
      await self.clients.claim();
    })(),
  );
});
self.addEventListener("fetch", (event) => {
  const request = event.request,
    url = new URL(request.url);
  if (
    request.method !== "GET" ||
    url.origin !== self.location.origin ||
    url.pathname.startsWith("/api/") ||
    url.pathname.includes("signin-with-chatgpt") ||
    url.pathname.includes("signout-with-chatgpt") ||
    request.headers.has("rsc")
  )
    return;
  if (request.mode === "navigate") {
    event.respondWith(
      (async () => {
        try {
          const response = await fetch(request);
          if (
            response.ok &&
            !response.redirected &&
            new URL(response.url).origin === self.location.origin
          ) {
            const cache = await caches.open(CACHE);
            await cache.put("/", response.clone());
          }
          return response;
        } catch {
          return (
            (await caches.match("/")) ||
            new Response(
              "Open Fieldnotes online once to save it for offline use.",
              { headers: { "Content-Type": "text/plain" } },
            )
          );
        }
      })(),
    );
    return;
  }
  if (/\.(js|css|woff2?|png|jpg|svg)$/.test(url.pathname))
    event.respondWith(
      (async () => {
        const cached = await caches.match(request);
        if (cached) return cached;
        const response = await fetch(request);
        if (response.ok) {
          const cache = await caches.open(CACHE);
          await cache.put(request, response.clone());
        }
        return response;
      })(),
    );
});
function openDB() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("fieldnotes-v1", 1);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
function req(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
function done(tx) {
  return new Promise((resolve, reject) => {
    tx.oncomplete = resolve;
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}
async function uploadQueued() {
  const response = await fetch("/api/auth/me", { cache: "no-store" });
  if (!response.ok) return;
  const { user } = await response.json();
  if (!user) return;
  const db = await openDB();
  try {
    const records = await req(
      db
        .transaction("observations")
        .objectStore("observations")
        .index("owner")
        .getAll(user.id),
    );
    for (const record of records) {
      if (record.syncState === "synced") continue;
      const { photo, owner, syncState, error, ...metadata } = record;
      const form = new FormData();
      form.set("metadata", JSON.stringify(metadata));
      form.set("photo", photo, "discovery.jpg");
      const uploaded = await fetch("/api/observations/" + record.id, {
        method: "PUT",
        body: form,
      });
      if (uploaded.status === 401) return;
      if (!uploaded.ok) throw Error("Upload paused");
      const tx = db.transaction("observations", "readwrite"),
        completion = done(tx),
        store = tx.objectStore("observations");
      const fresh = await req(store.get(record.id));
      if (fresh && fresh.revision === record.revision)
        store.put({ ...fresh, syncState: "synced", error: undefined });
      await completion;
    }
  } finally {
    db.close();
  }
  for (const client of await self.clients.matchAll())
    client.postMessage({ type: "fieldnotes-synced" });
}
self.addEventListener("sync", (event) => {
  if (event.tag === "fieldnotes-upload") event.waitUntil(uploadQueued());
});
