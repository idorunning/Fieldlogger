import assert from "node:assert/strict";
const base = process.env.TEST_ORIGIN || "http://localhost:8787";
const endpoint = `${base}/api/settings/openai-key`;
async function account() {
  const r = await fetch(`${base}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email: `keys-${crypto.randomUUID()}@example.test`,
      password: "Local-QA-password-2026!",
      name: "Key settings test",
    }),
  });
  assert.equal(r.status, 200);
  return r.headers.get("set-cookie").split(";")[0];
}
const first = await account(),
  second = await account();
// Deliberately fake fixtures. These tests never send a key to OpenAI.
const fixture = "sk-test-only-not-a-real-api-key-111111111111";
const replacement = "sk-test-only-not-a-real-api-key-222222222222";
const headers = {
  Cookie: first,
  Origin: base,
  "Content-Type": "application/json",
};
assert.equal((await fetch(endpoint)).status, 401);
assert.equal(
  (
    await fetch(endpoint, {
      method: "PUT",
      headers: { "Content-Type": "application/json", Origin: base },
      body: JSON.stringify({ apiKey: fixture }),
    })
  ).status,
  401,
);
assert.equal(
  (
    await fetch(endpoint, {
      method: "PUT",
      headers: { ...headers, Origin: "https://unrelated.example" },
      body: JSON.stringify({ apiKey: fixture }),
    })
  ).status,
  403,
);
assert.equal(
  (
    await fetch(endpoint, {
      method: "PUT",
      headers: { Cookie: first, "Content-Type": "application/json" },
      body: JSON.stringify({ apiKey: fixture }),
    })
  ).status,
  403,
);
assert.equal(
  (
    await fetch(endpoint, {
      method: "PUT",
      headers,
      body: JSON.stringify({ apiKey: "invalid" }),
    })
  ).status,
  400,
);
assert.equal(
  (
    await fetch(endpoint, {
      method: "PUT",
      headers,
      body: JSON.stringify({ apiKey: "x".repeat(5000) }),
    })
  ).status,
  413,
);
for (const apiKey of [fixture, replacement]) {
  const r = await fetch(endpoint, {
    method: "PUT",
    headers,
    body: JSON.stringify({ apiKey }),
  });
  assert.equal(r.status, 200, await r.clone().text());
  assert.ok(!(await r.text()).includes(apiKey));
  const state = await fetch(endpoint, { headers });
  assert.match(state.headers.get("cache-control"), /no-store/);
  const body = await state.text();
  assert.ok(!body.includes(apiKey));
  assert.equal(JSON.parse(body).hasKey, true);
}
assert.equal(
  (await (await fetch(endpoint, { headers: { Cookie: second } })).json())
    .hasKey,
  false,
);
// Another account deleting its own key cannot affect the first account.
assert.equal(
  (
    await fetch(endpoint, {
      method: "DELETE",
      headers: { ...headers, Cookie: second },
    })
  ).status,
  200,
);
assert.equal((await (await fetch(endpoint, { headers })).json()).hasKey, true);
assert.equal(
  (
    await fetch(endpoint, {
      method: "DELETE",
      headers: { ...headers, Origin: "https://unrelated.example" },
    })
  ).status,
  403,
);
assert.equal(
  (await fetch(endpoint, { method: "DELETE", headers })).status,
  200,
);
assert.equal((await (await fetch(endpoint, { headers })).json()).hasKey, false);
assert.equal(
  (await fetch(endpoint + "/test", { method: "POST", headers })).status,
  400,
);
console.log(
  "PASS: authenticated key save, replacement, removal, no key disclosure, account isolation, strict origin checks, payload limits and no-key test. No provider calls made.",
);
