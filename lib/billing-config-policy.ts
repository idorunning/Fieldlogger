import {z} from 'zod';
import {sealKey} from './key-crypto';
export const PLAY_CREDENTIAL_MAX_BYTES=30*1024;
export const GOOGLE_OAUTH_TOKEN_URI='https://oauth2.googleapis.com/token';
export const billingOwnerEmail='ntracey@gmail.com';
// These final write predicates run inside the setup transaction, after every
// asynchronous password/key check. A deleted owner cannot resurrect settings.
const ownerAlive='EXISTS(SELECT 1 FROM users WHERE id=? AND lower(email)=?)';
export const OWNER_SETUP_UPSERT_SQL=`INSERT INTO service_config(key,envelope,updated_at) SELECT ?,?,? WHERE ${ownerAlive} ON CONFLICT(key) DO UPDATE SET envelope=excluded.envelope,updated_at=excluded.updated_at WHERE ${ownerAlive}`;
export const OWNER_SETUP_DELETE_SQL=`DELETE FROM service_config WHERE key=? AND ${ownerAlive}`;
export const OWNER_SETUP_AUDIT_SQL=`INSERT INTO admin_audit(id,actor_id,target_id,action,created_at) SELECT ?,?,?,?,? WHERE ${ownerAlive}`;
export function isBillingOwner(user:{id:string;email:string},pinnedOwner?:string) {
  return !!pinnedOwner&&user.id===pinnedOwner&&user.email.toLowerCase()===billingOwnerEmail;
}
const serviceAccountShape=z.object({
  type:z.literal('service_account'),
  project_id:z.string().regex(/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/),
  client_email:z.string().max(320).regex(/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,180}@[a-z][a-z0-9-]{4,28}[a-z0-9]\.iam\.gserviceaccount\.com$/),
  token_uri:z.literal(GOOGLE_OAUTH_TOKEN_URI),
  private_key:z.string().min(200).max(12000),
});
export type GoogleServiceAccount=z.infer<typeof serviceAccountShape>;
export async function validateGoogleServiceAccount(value:string):Promise<GoogleServiceAccount> {
  if(new TextEncoder().encode(value).length>PLAY_CREDENTIAL_MAX_BYTES)throw new Error('The service-account file is too large.');
  let account:GoogleServiceAccount;
  try{account=serviceAccountShape.parse(JSON.parse(value));}catch{throw new Error('Use the original Google service-account JSON with its Google OAuth token endpoint.');}
  if(!/^-----BEGIN PRIVATE KEY-----\r?\n[A-Za-z0-9+/=\r\n]+-----END PRIVATE KEY-----\r?\n?$/.test(account.private_key))throw new Error('The Google private key is not valid PKCS8.');
  try {
    const pem=account.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,''),bytes=Uint8Array.from(atob(pem),char=>char.charCodeAt(0));
    const key=await crypto.subtle.importKey('pkcs8',bytes,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);bytes.fill(0);
    const algorithm=key.algorithm as RsaHashedKeyAlgorithm;
    if(algorithm.modulusLength<2048||algorithm.modulusLength>4096)throw new Error('Invalid RSA key size.');
  }catch{throw new Error('The Google private key must be a valid 2048–4096-bit RSA PKCS8 key.');}
  // Zod strips unrelated certificate/auth URLs. Runtime OAuth requests use the
  // fixed Google endpoint, never a URL copied from untrusted uploaded JSON.
  return account;
}
export function mayEnableCheckout(input:{pricingFinalised:boolean;enabled:boolean;configured:boolean;traderAddress:string|undefined}) {
  return input.pricingFinalised&&input.enabled&&input.configured&&!!input.traderAddress?.trim();
}
export const DISCONNECTED_PLAY_FLAGS={enabled:false,launchReady:false,disconnected:true} as const;
// This exact nonsecret marker is an erasure fallback, not a credential. It must
// keep environment credentials disabled even if a broken master is restored.
export const DISCONNECTED_PLAY_MARKER='my-trail-log:google-play:disconnected:v1';
export async function billingDeletionEnvelope(userId:string,pinnedOwner:string|undefined,secret:string|undefined) {
  if(!pinnedOwner||userId!==pinnedOwner)return null;
  if(!secret)return DISCONNECTED_PLAY_MARKER;
  try{return await sealKey(JSON.stringify(DISCONNECTED_PLAY_FLAGS),'service:google-play:'+pinnedOwner+':flags',secret);}
  catch{return DISCONNECTED_PLAY_MARKER;}
}
