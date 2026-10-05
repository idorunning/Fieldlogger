import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {GOOGLE_OAUTH_TOKEN_URI,billingOwnerEmail,billingDeletionEnvelope,DISCONNECTED_PLAY_FLAGS,DISCONNECTED_PLAY_MARKER,OWNER_SETUP_UPSERT_SQL,OWNER_SETUP_DELETE_SQL,OWNER_SETUP_AUDIT_SQL,isBillingOwner,mayEnableCheckout,validateGoogleServiceAccount} from '../lib/billing-config-policy';
import {sealKey,unsealKey} from '../lib/key-crypto';

const owner='45bccbf6-6db0-491b-8aa8-dcf6c774ff3c';
test('payment setup owner is pinned to both existing account ID and email, not an admin role or email signup',()=>{
  assert.equal(isBillingOwner({id:owner,email:'NTRACEY@gmail.com'},owner),true);
  assert.equal(isBillingOwner({id:'a-new-signup',email:'ntracey@gmail.com'},owner),false);
  assert.equal(isBillingOwner({id:owner,email:'another-admin@example.test'},owner),false);
  assert.equal(isBillingOwner({id:owner,email:'ntracey@gmail.com'},undefined),false);
});
async function syntheticGoogleJson(bits=2048) {
  // Ephemeral synthetic keys exist only inside this test process. No credential
  // fixture is saved, seeded to a service or sent to Google.
  const pair=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:bits,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
  const bytes=new Uint8Array(await crypto.subtle.exportKey('pkcs8',pair.privateKey)),base64=Buffer.from(bytes).toString('base64');bytes.fill(0);
  return {type:'service_account',project_id:'test-project',client_email:'verify@test-project.iam.gserviceaccount.com',token_uri:GOOGLE_OAUTH_TOKEN_URI,private_key:'-----BEGIN PRIVATE KEY-----\n'+base64.match(/.{1,64}/g)!.join('\n')+'\n-----END PRIVATE KEY-----\n'};
}
test('Google JSON validation actually imports RSA PKCS8 and discards unused uploaded URLs',async()=>{
  const sample=await syntheticGoogleJson(),normal=await validateGoogleServiceAccount(JSON.stringify({...sample,auth_uri:'https://untrusted.invalid/steal',client_x509_cert_url:'https://untrusted.invalid/metadata'}));
  assert.deepEqual(normal,sample);assert.equal('auth_uri' in normal,false);
});
test('credential validation blocks OAuth SSRF, unrelated email domains, malformed/weak keys and oversized inputs',async()=>{
  const sample=await syntheticGoogleJson();
  for(const change of [{token_uri:'https://attacker.invalid/token'},{token_uri:'http://oauth2.googleapis.com/token'},{client_email:'attacker@example.test'},{project_id:'../../private'},{private_key:'-----BEGIN PRIVATE KEY-----\nnot-a-real-key\n-----END PRIVATE KEY-----\n'}])await assert.rejects(validateGoogleServiceAccount(JSON.stringify({...sample,...change})));
  await assert.rejects(validateGoogleServiceAccount(JSON.stringify(await syntheticGoogleJson(1024))));
  await assert.rejects(validateGoogleServiceAccount(JSON.stringify({...sample,padding:'x'.repeat(31000)})));
});
test('encrypted service credentials cannot be opened under another owner or account-key context',async()=>{
  const sample=JSON.stringify(await syntheticGoogleJson()),master=Buffer.alloc(32,19).toString('base64'),context='service:google-play:'+owner;
  const envelope=await sealKey(sample,context,master);
  assert.equal(envelope.includes('PRIVATE KEY'),false);assert.equal(envelope.includes('gserviceaccount'),false);
  assert.equal(await unsealKey(envelope,context,master),sample);
  await assert.rejects(unsealKey(envelope,'service:google-play:another-owner',master));
  await assert.rejects(unsealKey(envelope,owner,master));
});
test('checkout cannot be enabled without finalised pricing, configured/enabled verification and legal trader details',()=>{
  const ready={pricingFinalised:true,enabled:true,configured:true,traderAddress:'422 Milton Road, Waterlooville, PO8 8LD'};
  assert.equal(mayEnableCheckout(ready),true);
  for(const flag of ['pricingFinalised','enabled','configured'])assert.equal(mayEnableCheckout({...ready,[flag]:false}),false);
  assert.equal(mayEnableCheckout({...ready,traderAddress:undefined}),false);assert.equal(mayEnableCheckout({...ready,traderAddress:'   '}),false);
});
test('only protected owner deletion creates an encrypted disconnect tombstone that cannot re-enable runtime credentials',async()=>{
  const master=Buffer.alloc(32,19).toString('base64');
  assert.equal(await billingDeletionEnvelope('other-user',owner,master),null);
  assert.equal(await billingDeletionEnvelope(owner,undefined,master),null);
  const envelope=await billingDeletionEnvelope(owner,owner,master);assert.ok(envelope);
  assert.equal(envelope.includes('disconnected'),false);
  assert.deepEqual(JSON.parse(await unsealKey(envelope,'service:google-play:'+owner+':flags',master)),DISCONNECTED_PLAY_FLAGS);
  await assert.rejects(unsealKey(envelope,'service:google-play:replacement-owner:flags',master));
});
test('owner erasure remains possible with missing/corrupt master using an exact nonsecret disconnect marker',async()=>{
  assert.equal(await billingDeletionEnvelope(owner,owner,undefined),DISCONNECTED_PLAY_MARKER);
  assert.equal(await billingDeletionEnvelope(owner,owner,'not-a-valid-encryption-key'),DISCONNECTED_PLAY_MARKER);
  assert.equal(await billingDeletionEnvelope(owner,owner,Buffer.alloc(8,3).toString('base64')),DISCONNECTED_PLAY_MARKER);
  assert.equal(await billingDeletionEnvelope('other-user',owner,undefined),null);
});
test('final SQL guards prevent a delayed setup request from overwriting owner-deletion tombstones',()=>{
  const db=new DatabaseSync(':memory:');db.exec('CREATE TABLE users(id TEXT PRIMARY KEY,email TEXT);CREATE TABLE service_config(key TEXT PRIMARY KEY,envelope TEXT,updated_at TEXT);CREATE TABLE admin_audit(id TEXT,actor_id TEXT,target_id TEXT,action TEXT,created_at TEXT)');
  db.prepare('INSERT INTO users VALUES(?,?)').run(owner,billingOwnerEmail.toUpperCase());
  const put=(key:string,envelope:string)=>db.prepare(OWNER_SETUP_UPSERT_SQL).run(key,envelope,'now',owner,billingOwnerEmail,owner,billingOwnerEmail);
  assert.equal(put('google-play-service-account','encrypted-initial').changes,1);
  assert.equal(put('google-play-flags','encrypted-initial-flags').changes,1);
  // Simulate owner deletion committing while a password/key-validated setup
  // request is still waiting. The deletion helper writes are unconditional.
  db.exec('BEGIN');db.prepare('DELETE FROM service_config WHERE key=?').run('google-play-service-account');db.prepare('UPDATE service_config SET envelope=? WHERE key=?').run('encrypted-disconnected-tombstone','google-play-flags');db.prepare('DELETE FROM users WHERE id=?').run(owner);db.exec('COMMIT');
  db.exec('BEGIN');assert.equal(put('google-play-service-account','encrypted-late-key').changes,0);assert.equal(put('google-play-flags','encrypted-late-enabled-flags').changes,0);assert.equal(db.prepare(OWNER_SETUP_DELETE_SQL).run('google-play-flags',owner,billingOwnerEmail).changes,0);
  const audited=db.prepare(OWNER_SETUP_AUDIT_SQL).run('late-audit',owner,owner,'billing:setup-updated','now',owner,billingOwnerEmail);assert.equal(audited.changes,0);db.exec('COMMIT');
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM service_config WHERE key=?').get('google-play-service-account')?.n,0);
  assert.equal(db.prepare('SELECT envelope FROM service_config WHERE key=?').get('google-play-flags')?.envelope,'encrypted-disconnected-tombstone');
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM admin_audit').get()?.n,0);
  // A different account reusing the support email still cannot satisfy the pin.
  db.prepare('INSERT INTO users VALUES(?,?)').run('new-account',billingOwnerEmail);assert.equal(put('google-play-service-account','encrypted-new-signup').changes,0);db.close();
});
