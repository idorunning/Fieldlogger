import {bindings} from './server';
import {PLAY_PACKAGE,PRICING_FINALISED,paidOrderPeriod,type GooglePurchase} from './billing-policy';
import {readBillingConfiguration} from './billing-config';
const b64=(bytes:Uint8Array)=>{let s='';for(let i=0;i<bytes.length;i+=8192)s+=String.fromCharCode(...bytes.subarray(i,i+8192));return btoa(s).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');};
const encode=(v:unknown)=>b64(new TextEncoder().encode(JSON.stringify(v)));
let cached:{token:string;until:number;identity:string}|undefined;
export async function playConfigured(){try{const config=await readBillingConfiguration();return config.enabled&&!!config.serviceAccount&&!!bindings().API_KEY_ENCRYPTION_KEY;}catch{return false;}}
export async function checkoutReady(){if(!PRICING_FINALISED)return false;try{const config=await readBillingConfiguration();return config.enabled&&!!config.serviceAccount&&config.launchReady&&!!bindings().TRAIL_TRADER_ADDRESS?.trim();}catch{return false;}}
async function accessToken(){
  const config=await readBillingConfiguration(),account=config.serviceAccount;
  if(!config.enabled||!account)throw new Error('Play verification unavailable');
  const identity=account.client_email+':'+config.revision+':'+await crypto.subtle.digest('SHA-256',new TextEncoder().encode(account.private_key)).then(v=>b64(new Uint8Array(v)));
  if(cached&&cached.until>Date.now()&&cached.identity===identity)return cached.token;
  const now=Math.floor(Date.now()/1000),payload=encode({iss:account.client_email,scope:'https://www.googleapis.com/auth/androidpublisher',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600}),header=encode({alg:'RS256',typ:'JWT'});
  const pem=account.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,'');
  const key=await crypto.subtle.importKey('pkcs8',Uint8Array.from(atob(pem),c=>c.charCodeAt(0)),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
  const assertion=header+'.'+payload+'.'+b64(new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,new TextEncoder().encode(header+'.'+payload))));
  const r=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion}),signal:AbortSignal.timeout(15000)});
  if(!r.ok)throw new Error('Play verification unavailable');const data:any=await r.json();if(typeof data.access_token!=='string')throw new Error('Play verification unavailable');
  cached={token:data.access_token,until:Date.now()+Math.min(3300,Number(data.expires_in)||0)*1000,identity};return cached.token;
}
export async function fetchPlayPurchase(token:string):Promise<GooglePurchase>{
  const r=await fetch('https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'+PLAY_PACKAGE+'/purchases/subscriptionsv2/tokens/'+encodeURIComponent(token),{headers:{Authorization:'Bearer '+await accessToken()},signal:AbortSignal.timeout(20000)});
  if(!r.ok)throw new Error(r.status===404||r.status===410?'Purchase unavailable':'Play verification unavailable');
  return r.json();
}
export async function acknowledgePlayPurchase(product:string,token:string){
  const r=await fetch('https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'+PLAY_PACKAGE+'/purchases/subscriptions/'+encodeURIComponent(product)+'/tokens/'+encodeURIComponent(token)+':acknowledge',{method:'POST',headers:{Authorization:'Bearer '+await accessToken(),'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(20000)});
  if(!r.ok)throw new Error('Play acknowledgement pending');
}
export async function paidAnnualPeriod(orderId:string,product:string,purchaseToken:string){
  if(!orderId||orderId.length>200)throw new Error('Annual order unavailable');
  const r=await fetch('https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'+PLAY_PACKAGE+'/orders/'+encodeURIComponent(orderId),{headers:{Authorization:'Bearer '+await accessToken()},signal:AbortSignal.timeout(20000)});
  if(!r.ok)throw new Error('Annual order unavailable');
  const order:any=await r.json();
  return paidOrderPeriod(order,product,purchaseToken);
}
