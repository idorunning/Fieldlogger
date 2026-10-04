import assert from "node:assert/strict";
import fs from "node:fs/promises";
const base = process.env.TEST_ORIGIN || "http://localhost:5173";
async function account() {
  const email = `qa-${crypto.randomUUID()}@example.test`,
    password = "Local-QA-password-2026!";
  const r = await fetch(base + "/api/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, name: "Integration test" }),
  });
  assert.equal(r.status, 200);
  assert.match(r.headers.get("set-cookie"), /HttpOnly/);
  return { cookie: r.headers.get("set-cookie").split(";")[0], email, password };
}
const first = await account(),
  second = await account(),
  id = crypto.randomUUID();
const meta = {
  id,
  capturedAt: "2026-09-30T08:12:00.000Z",
  localDate: "2026-09-30",
  localHour: 9,
  timezone: "Europe/London",
  latitude: 51.75,
  longitude: -1.25,
  accuracy: 4,
  locationSource: "gps",
  place: "Test woods",
  note: "An integration test observation",
  category: "plants",
  name: "Test leaf",
  scientificName: "",
  confirmed: true,
  identification: { name: "fabricated evidence" },
  analysisState: "complete",
  updatedAt: new Date().toISOString(),
  revision: 1,
};
const makeForm = async () => {
  const form = new FormData();
  form.set("metadata", JSON.stringify(meta));
  form.set(
    "photo",
    new Blob([await fs.readFile("public/woodland.jpg")], {
      type: "image/jpeg",
    }),
    "test.jpg",
  );
  return form;
};
for (let i = 0; i < 2; i++) {
  const r = await fetch(`${base}/api/observations/${id}`, {
    method: "PUT",
    headers: { Cookie: first.cookie },
    body: await makeForm(),
  });
  assert.equal(r.status, 200, await r.text());
}
const list = await (
  await fetch(base + "/api/observations", { headers: { Cookie: first.cookie } })
).json();
assert.equal(list.observations.filter((o) => o.id === id).length, 1);
assert.equal(list.observations[0].identification, null);
assert.equal(list.observations[0].analysisState, "pending");
assert.equal(
  (
    await fetch(`${base}/api/observations/${id}/photo`, {
      headers: { Cookie: first.cookie },
    })
  ).status,
  200,
);
assert.equal(
  (
    await fetch(`${base}/api/observations/${id}/photo`, {
      headers: { Cookie: second.cookie },
    })
  ).status,
  404,
);
assert.equal(
  (
    await fetch(`${base}/api/observations/${id}/identify`, {
      method: "POST",
      headers: { Cookie: first.cookie },
    })
  ).status,
  503,
);
assert.equal((await fetch(`${base}/api/observations`)).status, 401);
assert.equal(
  (
    await fetch(`${base}/api/auth/logout`, {
      method: "POST",
      headers: { Cookie: first.cookie, Origin: "https://unrelated.example" },
    })
  ).status,
  403,
);
const wrong = await fetch(base + "/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: first.email, password: "Wrong-password-1234" }),
});
assert.equal(wrong.status, 401);
await fetch(base + "/api/auth/logout", {
  method: "POST",
  headers: { Cookie: first.cookie },
});
assert.equal(
  (
    await fetch(`${base}/api/observations`, {
      headers: { Cookie: first.cookie },
    })
  ).status,
  401,
);
console.log(
  "PASS: signup, HttpOnly session, upload, idempotent retry, image access, ownership isolation, evidence integrity, missing-key response, invalid login, CSRF and logout revocation.",
);
