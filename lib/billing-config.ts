import {z} from 'zod';
import {bindings,database,json,digest,hashPassword,constantTimeEqual} from './server';
import {sealKey,unsealKey} from './key-crypto';
import {PRICING_FINALISED,PLAY_PACKAGE} from './billing-policy';
import {billingDeletionEnvelope,billingOwnerEmail,DISCONNECTED_PLAY_FLAGS,DISCONNECTED_PLAY_MARKER,OWNER_SETUP_UPSERT_SQL,OWNER_SETUP_DELETE_SQL,OWNER_SETUP_AUDIT_SQL,isBillingOwner,mayEnableCheckout,validateGoogleServiceAccount,type GoogleServiceAccount} from './billing-config-policy';

type Owner={id:string;email:string;name:string};
type Flags={enabled:boolean;launchReady:boolean;disconnected:boolean};
const flagsSchema=z.object({enabled:z.boolean(),launchReady:z.boolean(),disconnected:z.boolean()}).strict();
const ACCOUNT_KEY='google-play-service-account',FLAGS_KEY='google-play-flags';
function aad(){const owner=bindings().COMMUNITY_ADMIN_USER_ID;if(!owner)throw new Error('Payment setup is unavailable.');return 'service:google-play:'+owner;}
const flagAad=()=>aad()+':flags';

/** Server-only. Never return this object from a route: it contains credentials. */
export async function readBillingConfiguration() {
  // Read the encrypted rows in one transaction so flags cannot describe a
  // different credential revision during a concurrent rotation/disconnect.
  const rows=await database().batch<{envelope:string;updated_at:string}>([
    database().prepare('SELECT envelope,updated_at FROM service_config WHERE key=?').bind(FLAGS_KEY),
    database().prepare('SELECT envelope,updated_at FROM service_config WHERE key=?').bind(ACCOUNT_KEY),
  ]);
  const flagsRow=rows[0]?.results[0],accountRow=rows[1]?.results[0];
  if(flagsRow?.envelope===DISCONNECTED_PLAY_MARKER)return {serviceAccount:null as GoogleServiceAccount|null,enabled:false,launchReady:false,revision:flagsRow.updated_at,source:'disconnected'};
  const secret=bindings().API_KEY_ENCRYPTION_KEY;
  if(!secret)return {serviceAccount:null as GoogleServiceAccount|null,enabled:false,launchReady:false,revision:'',source:'unconfigured'};
  const flags:Flags=flagsRow?flagsSchema.parse(JSON.parse(await unsealKey(flagsRow.envelope,flagAad(),secret))):{
    enabled:bindings().GOOGLE_PLAY_BILLING_ENABLED==='true',launchReady:bindings().TRAIL_BILLING_LAUNCH_READY==='true',disconnected:false,
  };
  const raw=flags.disconnected?null:accountRow?await unsealKey(accountRow.envelope,aad(),secret):bindings().GOOGLE_PLAY_SERVICE_ACCOUNT||null;
  const serviceAccount=raw?await validateGoogleServiceAccount(raw):null;
  return {serviceAccount,enabled:flags.enabled&&!!serviceAccount,launchReady:flags.launchReady&&mayEnableCheckout({pricingFinalised:PRICING_FINALISED,enabled:flags.enabled,configured:!!serviceAccount,traderAddress:bindings().TRAIL_TRADER_ADDRESS}),revision:[accountRow?.updated_at,flagsRow?.updated_at].filter(Boolean).join(':'),source:accountRow?'website':serviceAccount?'runtime':'unconfigured'};
}
export async function readServiceAccount(){return (await readBillingConfiguration()).serviceAccount;}
/** Include these writes in the account-deletion transaction, before the owner
 * row is removed. No service credential is read, and no audit FK is inserted. */
export async function disconnectBillingForDeletedOwner(userId:string):Promise<D1PreparedStatement[]> {
  const envelope=await billingDeletionEnvelope(userId,bindings().COMMUNITY_ADMIN_USER_ID,bindings().API_KEY_ENCRYPTION_KEY);
  if(!envelope)return [];
  return [
    database().prepare('DELETE FROM service_config WHERE key=?').bind(ACCOUNT_KEY),
    database().prepare('INSERT INTO service_config(key,envelope,updated_at) VALUES(?,?,?) ON CONFLICT(key) DO UPDATE SET envelope=excluded.envelope,updated_at=excluded.updated_at').bind(FLAGS_KEY,envelope,new Date().toISOString()),
  ];
}
export async function billingSetupStatus() {
  let config:Awaited<ReturnType<typeof readBillingConfiguration>>;
  try{config=await readBillingConfiguration();}catch{return {configured:false,enabled:false,launchReady:false,pricingFinalised:PRICING_FINALISED,secureStorageReady:!!bindings().API_KEY_ENCRYPTION_KEY,serviceAccountEmail:null,packageName:PLAY_PACKAGE,missingChecks:['Secure payment settings could not be opened. Restore the service-account configuration or contact support.']};}
  const missingChecks:string[]=[];
  if(!bindings().API_KEY_ENCRYPTION_KEY)missingChecks.push('Server encryption storage is not configured.');
  if(!config.serviceAccount)missingChecks.push('Upload the Google service-account JSON privately below.');
  if(!config.enabled)missingChecks.push('Enable server purchase verification after granting Google Play permissions.');
  if(!PRICING_FINALISED)missingChecks.push('Finalise the price and photo allowances across app, website and Play products.');
  if(!bindings().TRAIL_TRADER_ADDRESS?.trim())missingChecks.push('Publish and configure the current legal trader contact address.');
  if(!config.launchReady)missingChecks.push('Complete real Play license-tester lifecycle checks before activating checkout.');
  return {configured:!!config.serviceAccount,enabled:config.enabled,launchReady:config.launchReady,pricingFinalised:PRICING_FINALISED,secureStorageReady:!!bindings().API_KEY_ENCRYPTION_KEY,serviceAccountEmail:config.serviceAccount?.client_email||null,packageName:PLAY_PACKAGE,source:config.source,missingChecks};
}
async function limit(key:string,maximum:number,ms:number) {
  const now=Date.now();return !!await database().prepare('INSERT INTO auth_attempts(key,count,reset_at) VALUES(?,1,?) ON CONFLICT(key) DO UPDATE SET count=CASE WHEN reset_at<=? THEN 1 ELSE count+1 END,reset_at=CASE WHEN reset_at<=? THEN excluded.reset_at ELSE reset_at END WHERE reset_at<=? OR count<? RETURNING count').bind(key,now+ms,now,now,now,maximum).first();
}
async function body(request:Request) {
  const maximum=40*1024;
  if(!request.headers.get('content-type')?.startsWith('application/json'))throw new Error('Use the secure setup form.');
  if(Number(request.headers.get('content-length')||0)>maximum)throw new Error('The setup request is too large.');
  const reader=request.body?.getReader();if(!reader)throw new Error('Complete the setup form.');let size=0;const chunks:Uint8Array[]=[];
  try {for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>maximum){await reader.cancel();throw new Error('The setup request is too large.');}chunks.push(value);}}
  finally{reader.releaseLock();}
  const all=new Uint8Array(size);let offset=0;for(const chunk of chunks){all.set(chunk,offset);offset+=chunk.length;}
  try{return JSON.parse(new TextDecoder().decode(all));}finally{all.fill(0);}
}
export async function billingSetupRoute(request:Request,user:Owner) {
  if(!isBillingOwner(user,bindings().COMMUNITY_ADMIN_USER_ID))return json({error:'This setup is available only to the protected owner account.'},403);
  const url=new URL(request.url),local=['localhost','127.0.0.1','[::1]'].includes(url.hostname);
  if(url.protocol!=='https:'&&!local)return json({error:'Open the HTTPS website to configure payments securely.'},403);
  if(request.method==='GET')return json(await billingSetupStatus());
  if(request.method!=='POST')return json({error:'Method not allowed.'},405);
  if(request.headers.get('origin')!==url.origin)return json({error:'Open the My Trail Log website to change payment setup.'},403);
  const ip=await digest(request.headers.get('cf-connecting-ip')||'local');
  if(!await limit('billing-setup-user:'+user.id,10,900000)||!await limit('billing-setup:'+user.id+':'+ip,6,900000))return json({error:'Please wait 15 minutes before trying payment setup again.'},429);
  let input:{password:string;serviceAccount?:string;enabled?:boolean;launchReady?:boolean;action?:'save'|'disconnect';confirmDisconnect?:true};
  try{input=z.object({password:z.string().min(8).max(256),serviceAccount:z.string().max(30*1024).optional(),enabled:z.boolean().optional(),launchReady:z.boolean().optional(),action:z.enum(['save','disconnect']).optional(),confirmDisconnect:z.literal(true).optional()}).strict().parse(await body(request));}
  catch{return json({error:'Complete the setup form with a valid password and Google service-account JSON.'},400);}
  const storedPassword=await database().prepare('SELECT password_hash,salt FROM users WHERE id=?').bind(user.id).first<{password_hash:string;salt:string}>();
  if(!storedPassword||!constantTimeEqual(await hashPassword(input.password,storedPassword.salt),storedPassword.password_hash))return json({error:'That owner password did not match. No payment settings changed.'},401);
  input.password='';
  const secret=bindings().API_KEY_ENCRYPTION_KEY;
  if(!secret)return json({error:'Secure server encryption storage is unavailable. No credential was saved.'},503);
  try {
    const now=new Date().toISOString(),writes:D1PreparedStatement[]=[];
    if(input.action==='disconnect') {
      if(!input.confirmDisconnect)return json({error:'Confirm disconnecting Google verification and disabling checkout.'},400);
      writes.push(database().prepare(OWNER_SETUP_DELETE_SQL).bind(ACCOUNT_KEY,user.id,billingOwnerEmail));
      writes.push(database().prepare(OWNER_SETUP_UPSERT_SQL).bind(FLAGS_KEY,await sealKey(JSON.stringify(DISCONNECTED_PLAY_FLAGS),flagAad(),secret),now,user.id,billingOwnerEmail,user.id,billingOwnerEmail));
    } else {
      const provided=input.serviceAccount?.trim();let current:Awaited<ReturnType<typeof readBillingConfiguration>>;
      try{current=await readBillingConfiguration();}catch{if(!provided)return json({error:'The saved configuration could not be opened. Paste replacement Google credentials with checkout disabled, or disconnect it.'},503);current={serviceAccount:null,enabled:false,launchReady:false,revision:'',source:'unconfigured'};}
      let serviceAccount=current.serviceAccount;
      if(provided){try{serviceAccount=await validateGoogleServiceAccount(provided);}catch(error){return json({error:error instanceof Error?error.message:'Use valid Google service-account JSON.'},400);}}
      input.serviceAccount='';
      const enabled=input.enabled??current.enabled,launchReady=input.launchReady??current.launchReady;
      if(enabled&&!serviceAccount)return json({error:'Save valid service-account JSON before enabling verification.'},409);
      if(launchReady&&!mayEnableCheckout({pricingFinalised:PRICING_FINALISED,enabled,configured:!!serviceAccount,traderAddress:bindings().TRAIL_TRADER_ADDRESS}))return json({error:'Checkout stays disabled until pricing, verification credentials and legal trader details are ready.'},409);
      if(provided&&launchReady)return json({error:'Save replacement credentials with checkout disabled, then complete the verification checks before activating it.'},409);
      if(provided)writes.push(database().prepare(OWNER_SETUP_UPSERT_SQL).bind(ACCOUNT_KEY,await sealKey(JSON.stringify(serviceAccount),aad(),secret),now,user.id,billingOwnerEmail,user.id,billingOwnerEmail));
      writes.push(database().prepare(OWNER_SETUP_UPSERT_SQL).bind(FLAGS_KEY,await sealKey(JSON.stringify({enabled,launchReady,disconnected:!serviceAccount}),flagAad(),secret),now,user.id,billingOwnerEmail,user.id,billingOwnerEmail));
    }
    writes.push(database().prepare(OWNER_SETUP_AUDIT_SQL).bind(crypto.randomUUID(),user.id,user.id,input.action==='disconnect'?'billing:disconnected':'billing:setup-updated',now,user.id,billingOwnerEmail));
    const results=await database().batch(writes);
    if(!results.at(-1)?.meta.changes)return json({error:'The protected owner account is no longer available. No payment settings changed.'},401);
    return json({ok:true,message:input.action==='disconnect'?'Google verification disconnected. Checkout is disabled. This does not cancel existing Play subscriptions.':'Payment setup saved securely. The credential is never returned to your browser.',...await billingSetupStatus()});
  }catch{return json({error:'Secure payment setup could not be saved. No credential is displayed; check server encryption storage and try again.'},503);}
}
