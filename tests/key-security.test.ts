import test from "node:test";
import assert from "node:assert/strict";
import { sealKey, unsealKey } from "../lib/key-crypto";
import {
  checkOpenAIResponse,
  OpenAIConnectionError,
} from "../lib/openai-response";
const fixtureMaster = Buffer.alloc(32, 7).toString("base64");
const fixture = "test-only-not-a-provider-credential";
test("encrypted keys cannot be read by another owner or another encryption key", async () => {
  const sealed = await sealKey(fixture, "owner-a", fixtureMaster);
  assert.ok(!sealed.includes(fixture));
  assert.equal(await unsealKey(sealed, "owner-a", fixtureMaster), fixture);
  await assert.rejects(unsealKey(sealed, "owner-b", fixtureMaster));
  await assert.rejects(
    unsealKey(sealed, "owner-a", Buffer.alloc(32, 8).toString("base64")),
  );
});
test("key replacement uses fresh encryption and tampering is rejected", async () => {
  const first = await sealKey(fixture, "owner", fixtureMaster);
  const second = await sealKey(fixture, "owner", fixtureMaster);
  assert.notEqual(first, second);
  const edited = JSON.parse(first);
  const bytes = Buffer.from(edited.data, "base64");
  bytes[0] ^= 1;
  edited.data = bytes.toString("base64");
  await assert.rejects(
    unsealKey(JSON.stringify(edited), "owner", fixtureMaster),
  );
});
test("expired keys, denied permissions and quota failures are actionable without reflecting upstream secrets", () => {
  for (const [status, pattern] of [
    [401, /expired/],
    [403, /permissions/],
    [429, /quota/],
    [500, /Try again/],
  ] as const) {
    assert.throws(
      () =>
        checkOpenAIResponse(
          new Response("sensitive upstream error must never be reflected", {
            status,
          }),
        ),
      (error) => {
        assert.ok(error instanceof OpenAIConnectionError);
        assert.match(error.message, pattern);
        assert.ok(!error.message.includes("sensitive upstream"));
        return true;
      },
    );
  }
  assert.doesNotThrow(() => checkOpenAIResponse(new Response("OK")));
});
