/** Server-side envelope; the master key must only be a runtime secret. */
const encode = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes));
const decode = (value: string) =>
  Uint8Array.from(atob(value), (c) => c.charCodeAt(0));
async function masterKey(secret: string) {
  const bytes = decode(secret);
  if (bytes.byteLength !== 32)
    throw new Error("Key storage is not configured.");
  return crypto.subtle.importKey("raw", bytes, "AES-GCM", false, [
    "encrypt",
    "decrypt",
  ]);
}
export async function sealKey(value: string, owner: string, secret: string) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    {
      name: "AES-GCM",
      iv,
      additionalData: new TextEncoder().encode(`fieldnotes:openai:v1:${owner}`),
    },
    await masterKey(secret),
    new TextEncoder().encode(value),
  );
  return JSON.stringify({
    v: 1,
    iv: encode(iv),
    data: encode(new Uint8Array(ciphertext)),
  });
}
export async function unsealKey(
  envelope: string,
  owner: string,
  secret: string,
) {
  const sealed = JSON.parse(envelope);
  if (sealed.v !== 1) throw new Error("Unsupported key storage version.");
  const result = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: decode(sealed.iv),
      additionalData: new TextEncoder().encode(`fieldnotes:openai:v1:${owner}`),
    },
    await masterKey(secret),
    decode(sealed.data),
  );
  return new TextDecoder().decode(result);
}
