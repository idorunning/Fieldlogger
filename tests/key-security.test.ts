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
test("provider failures preserve photos without exposing credentials or setup instructions to members", () => {
  for (const [status, pattern] of [
    [401, /temporarily unavailable/],
    [403, /temporarily unavailable/],
    [429, /busy or temporarily paused/],
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
        assert.match(error.message,/photos are saved/i);
        assert.doesNotMatch(error.message,/API|OpenAI|key|scope|project|permission|billing|quota|settings/i);
        assert.ok(!error.message.includes("sensitive upstream"));
        return true;
      },
    );
  }
  assert.doesNotThrow(() => checkOpenAIResponse(new Response("OK")));
});
