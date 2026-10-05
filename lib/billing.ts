import {z} from 'zod';
import {database,bindings,json,digest} from './server';
import {sealKey,unsealKey} from './key-crypto';
import {PLANS,PRICING_FINALISED,planForProduct,utcPeriod,checkedPurchase,subscriptionGrants,retainedAnnualPeriod,type Plan} from './billing-policy';
import {playConfigured,checkoutReady,fetchPlayPurchase,acknowledgePlayPurchase,paidAnnualPeriod} from './play-server';
import {limitAction,actionLease} from './community';
import {closerLookStatus} from './ai-budget';
type Subscription={token_hash:string;user_id:string;token_envelope:string;product_id:string;plan:Plan;status:string;starts_at:string;expires_at:string;checked_at:number;acknowledged:number;is_trial:number;paid_period_start:string|null;paid_period_end:string|null;latest_order_id:string|null};
const CHECK_MAX_AGE=15*60*1000;
async function currentSubscription(userId:string,refresh:boolean){
  const rows=(await database().prepare('SELECT * FROM subscriptions WHERE user_id=? ORDER BY expires_at DESC LIMIT 8').bind(userId).all<Subscription>()).results;
  const valid:Subscription[]=[];let verificationDelayed=false;
  for(const row of rows){
    if(row.status==='SUBSCRIPTION_STATE_REPLACED')continue;
    if(refresh&&row.checked_at<Date.now()-CHECK_MAX_AGE&&await playConfigured()){
      const lease=await actionLease('play-refresh:'+row.token_hash,60000);
      if(!lease){verificationDelayed=true;continue;}
      try{
        const token=await unsealKey(row.token_envelope,'play:'+row.token_hash,bindings().API_KEY_ENCRYPTION_KEY!);
        const purchase=checkedPurchase(await fetchPlayPurchase(token),await digest(userId),row.product_id);
        let paid:{start:string;end:string}|null=null;
        if(purchase.granted&&purchase.plan==='annual'&&!purchase.isTrial){
          paid=retainedAnnualPeriod(row,purchase.orderId);
          if(!paid&&purchase.orderId){try{paid=await paidAnnualPeriod(purchase.orderId,row.product_id,token);}catch{/* Verified base allowance remains available; annual extras await a full paid order. */}}
        }
        row.paid_period_start=paid?.start||null;row.paid_period_end=paid?.end||null;row.latest_order_id=purchase.orderId;
        row.status=purchase.status;row.expires_at=purchase.expiresAt;row.starts_at=purchase.startsAt;row.checked_at=Date.now();row.is_trial=purchase.isTrial?1:0;row.acknowledged=purchase.acknowledged?1:0;row.plan=purchase.plan;
        const changed=await database().prepare("UPDATE subscriptions SET status=?,starts_at=?,expires_at=?,checked_at=?,is_trial=?,acknowledged=?,plan=?,paid_period_start=?,paid_period_end=?,latest_order_id=? WHERE token_hash=? AND user_id=? AND status<>'SUBSCRIPTION_STATE_REPLACED'").bind(row.status,row.starts_at,row.expires_at,row.checked_at,row.is_trial,row.acknowledged,row.plan,row.paid_period_start,row.paid_period_end,row.latest_order_id,row.token_hash,userId).run();
        if(!changed.meta.changes)continue;
      }catch{verificationDelayed=true;}finally{await database().prepare('DELETE FROM auth_attempts WHERE key=? AND reset_at=?').bind('play-refresh:'+row.token_hash,lease.reset_at).run().catch(()=>{});}
    }
    if(row.acknowledged&&subscriptionGrants(row.status,row.expires_at)&&row.checked_at>=Date.now()-CHECK_MAX_AGE)valid.push(row);
    else if(subscriptionGrants(row.status,row.expires_at))verificationDelayed=true;
  }
  valid.sort((a,b)=>PLANS[b.plan].limit-PLANS[a.plan].limit||Number(b.plan==='annual')-Number(a.plan==='annual')||b.expires_at.localeCompare(a.expires_at));
  return {row:valid[0]||null,verificationDelayed};
}
export async function billingStatus(userId:string,refresh=true){
  const {row,verificationDelayed}=await currentSubscription(userId,refresh),plan:Plan=row?.plan||'free',period=utcPeriod(),closer=await closerLookStatus(userId);
  // Bonus requires a paid annual cycle; free trial phases never grant annual extras.
  const paidYear=row?.plan==='annual'&&!row.is_trial?row.paid_period_start:null;
  const bonusYear=paidYear&&row?.paid_period_end&&Date.parse(row.paid_period_end)>Date.now()?paidYear:null;
  const count=await database().prepare('SELECT COUNT(*) AS used,SUM(CASE WHEN bonus_year IS NULL THEN 1 ELSE 0 END) AS baseUsed FROM photo_allowance WHERE user_id=? AND period=?').bind(userId,period.key).first<{used:number;baseUsed:number}>();
  const bonus=bonusYear?await database().prepare('SELECT COUNT(*) AS n FROM photo_allowance WHERE user_id=? AND bonus_year=?').bind(userId,bonusYear).first<{n:number}>():null;
  const annualUsed=paidYear?await database().prepare('SELECT COUNT(*) AS n FROM photo_allowance WHERE user_id=? AND created_at>=? AND bonus_year IS NULL').bind(userId,paidYear).first<{n:number}>():null;
  const trialUsed=row?.is_trial?await database().prepare('SELECT COUNT(*) AS n FROM photo_allowance WHERE user_id=? AND created_at>=?').bind(userId,row.starts_at).first<{n:number}>():null;
  const limit=PLANS[plan].limit;
  const termStart=paidYear||(row?.is_trial?row.starts_at:null),termBaseLimit=paidYear?PLANS[plan].limit*12:(row?.is_trial?limit:null);
  const termBaseUsed=annualUsed?.n||trialUsed?.n||0;
  const bonusRemaining=bonusYear?Math.max(0,PLANS.annual.annualBonusPhotos-(bonus?.n||0)):0,baseRemaining=Math.min(Math.max(0,limit-(count?.baseUsed||0)),termBaseLimit===null?Infinity:Math.max(0,termBaseLimit-termBaseUsed));
  return {...closer,plan,planName:PLANS[plan].name,limit,used:count?.used||0,baseUsed:count?.baseUsed||0,remaining:baseRemaining+bonusRemaining,bonusRemaining,bonusYear,termStart,termBaseLimit,isTrial:!!row?.is_trial,trialRemaining:row?.is_trial?Math.max(0,limit-termBaseUsed):null,periodStart:period.start,periodEnd:period.end,expiresAt:row?.expires_at||null,status:row?.status||'free',verificationDelayed,annualBonusPending:!!row&&row.plan==='annual'&&!row.is_trial&&!bonusYear,billingReady:await checkoutReady(),products:(Object.keys(PLANS) as Plan[]).filter(p=>PLANS[p].productId).map(p=>({productId:PLANS[p].productId,plan:p,name:PLANS[p].name,limit:PLANS[p].limit})),trialEligible:null,monthlyLimitBasis:'UTC calendar month',exportsAlwaysFree:true};
}
export async function reservePhoto(userId:string,id:string){
  const previous=await database().prepare('SELECT user_id FROM photo_allowance WHERE observation_id=?').bind(id).first<{user_id:string}>();
  if(previous)return {allowed:previous.user_id===userId,charged:false,billing:null};
  const billing=await billingStatus(userId),period=utcPeriod();
  // The count predicates and INSERT are one serialized SQLite write: concurrent captures cannot overspend.
  const row=await database().prepare(`INSERT OR IGNORE INTO photo_allowance(observation_id,user_id,period,bonus_year,created_at)
    SELECT ?,?,?,CASE WHEN (SELECT COUNT(*) FROM photo_allowance WHERE user_id=? AND period=? AND bonus_year IS NULL)>=? OR (? IS NOT NULL AND (SELECT COUNT(*) FROM photo_allowance WHERE user_id=? AND created_at>=? AND bonus_year IS NULL)>=?) THEN ? ELSE NULL END,?
    WHERE ((SELECT COUNT(*) FROM photo_allowance WHERE user_id=? AND period=? AND bonus_year IS NULL)<? AND (? IS NULL OR (SELECT COUNT(*) FROM photo_allowance WHERE user_id=? AND created_at>=? AND bonus_year IS NULL)<?))
       OR (? IS NOT NULL AND (SELECT COUNT(*) FROM photo_allowance WHERE user_id=? AND bonus_year=?)<?)
    RETURNING observation_id`).bind(id,userId,period.key,userId,period.key,billing.limit,billing.termStart,userId,billing.termStart,billing.termBaseLimit,billing.bonusYear,new Date().toISOString(),userId,period.key,billing.limit,billing.termStart,userId,billing.termStart,billing.termBaseLimit,billing.bonusYear,userId,billing.bonusYear,PLANS.annual.annualBonusPhotos).first();
  if(row)return {allowed:true,charged:true,billing};
  const raced=await database().prepare('SELECT user_id FROM photo_allowance WHERE observation_id=?').bind(id).first<{user_id:string}>();
  return {allowed:raced?.user_id===userId,charged:false,billing:await billingStatus(userId,false)};
}
export async function billingRoute(request:Request,path:string[],user:{id:string}){
  if(!path[0]&&request.method==='GET')return json(await billingStatus(user.id));
  if(path[0]!=='verify'||request.method!=='POST')return json({error:'Not found'},404);
  if(request.headers.get('origin')!==new URL(request.url).origin)return json({error:'Open My Trail Log to verify a purchase.'},403);
  if(!await playConfigured())return json({verified:false,error:'Subscriptions are being prepared. No paid access has been granted.'},503);
  if(!request.headers.get('content-type')?.startsWith('application/json'))return json({verified:false,error:'Use the My Trail Log purchase verification form.'},415);
  if(!await limitAction('play-verify:'+user.id,10,60000)||!await limitAction('play-verify-ip:'+await digest(request.headers.get('cf-connecting-ip')||'local'),30,60000))return json({verified:false,error:'Please wait a minute before restoring purchases again.'},429);
  const {productId,purchaseToken}=z.object({productId:z.string().max(100),purchaseToken:z.string().min(10).max(4096)}).strict().parse(await request.json());
  if(!planForProduct(productId))return json({verified:false,error:'Unknown subscription product.'},400);
  const hash=await digest(purchaseToken),old=await database().prepare('SELECT user_id,status,latest_order_id,paid_period_start,paid_period_end FROM subscriptions WHERE token_hash=?').bind(hash).first<{user_id:string;status:string;latest_order_id:string|null;paid_period_start:string|null;paid_period_end:string|null}>();
  if(old&&old.user_id!==user.id)return json({verified:false,error:'This purchase belongs to another My Trail Log account.'},409);
  if(old?.status==='SUBSCRIPTION_STATE_REPLACED')return json({verified:false,error:'This subscription was replaced. Restore your current purchase.'},409);
  let payload;try{payload=await fetchPlayPurchase(purchaseToken);}catch{return json({verified:false,error:'Google Play could not verify this purchase yet. Restore purchases to try again.'},503);}
  let checked;try{checked=checkedPurchase(payload,await digest(user.id),productId);}catch{return json({verified:false,error:'This purchase does not match your account or subscription.'},403);}
  const linked=payload.linkedPurchaseToken?await digest(payload.linkedPurchaseToken):null;
  if(linked){const owner=await database().prepare('SELECT user_id FROM subscriptions WHERE token_hash=?').bind(linked).first<{user_id:string}>();if(owner&&owner.user_id!==user.id)return json({verified:false,error:'The previous purchase belongs to another account.'},409);}
  let paid:{start:string;end:string}|null=null;
  if(checked.granted&&checked.plan==='annual'&&!checked.isTrial){
    paid=retainedAnnualPeriod(old,checked.orderId);
    if(!paid&&checked.orderId){try{paid=await paidAnnualPeriod(checked.orderId,productId,purchaseToken);}catch{/* Annual extras wait for paid-order proof; the account-bound base subscription is verified separately. */}}
  }
  const envelope=await sealKey(purchaseToken,'play:'+hash,bindings().API_KEY_ENCRYPTION_KEY!);
  const writes=[database().prepare(`INSERT INTO subscriptions(token_hash,user_id,token_envelope,product_id,plan,status,starts_at,expires_at,checked_at,acknowledged,is_trial,paid_period_start,paid_period_end,latest_order_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(token_hash) DO UPDATE SET product_id=excluded.product_id,plan=excluded.plan,status=excluded.status,starts_at=excluded.starts_at,expires_at=excluded.expires_at,checked_at=excluded.checked_at,acknowledged=excluded.acknowledged,is_trial=excluded.is_trial,paid_period_start=excluded.paid_period_start,paid_period_end=excluded.paid_period_end,latest_order_id=excluded.latest_order_id WHERE subscriptions.user_id=excluded.user_id AND subscriptions.status<>'SUBSCRIPTION_STATE_REPLACED'`).bind(hash,user.id,envelope,productId,checked.plan,checked.status,checked.startsAt,checked.expiresAt,Date.now(),checked.acknowledged?1:0,checked.isTrial?1:0,paid?.start||null,paid?.end||null,checked.orderId)];
  if(linked&&checked.granted)writes.push(database().prepare("UPDATE subscriptions SET status='SUBSCRIPTION_STATE_REPLACED',checked_at=? WHERE token_hash=? AND user_id=? AND EXISTS(SELECT 1 FROM subscriptions WHERE token_hash=? AND user_id=?)").bind(Date.now(),linked,user.id,hash,user.id));
  const results=await database().batch(writes),result=results[0];
  if(!result.meta.changes)return json({verified:false,error:'This purchase belongs to another account.'},409);
  if(!checked.granted)return json({verified:false,pending:checked.status==='SUBSCRIPTION_STATE_PENDING',billing:await billingStatus(user.id,false),error:'Google Play has not confirmed an active subscription.'},409);
  if(!checked.acknowledged){try{await acknowledgePlayPurchase(productId,purchaseToken);await database().prepare('UPDATE subscriptions SET acknowledged=1 WHERE token_hash=? AND user_id=?').bind(hash,user.id).run();}catch{return json({verified:false,error:'Purchase verification succeeded; Play acknowledgement is pending. Restore purchases shortly.'},503);}}
  return json({verified:true,billing:await billingStatus(user.id,false)});
}
export async function plansResponse(){return json({brand:'My Trail Log',pricingFinalised:PRICING_FINALISED,billingReady:await checkoutReady(),plans:(Object.keys(PLANS) as Plan[]).map(id=>({id,...PLANS[id],bonusPhotosPerYear:PLANS[id].annualBonusPhotos})),trial:'One calendar month on eligible Google Play offers, with the selected plan’s allowance as a total trial limit',quota:'New cloud journal photos per UTC calendar month; annual plan has 3,600 monthly-allowance photos plus 150 bonus photos per verified paid membership year. Existing data and ZIP downloads remain free',operator:{name:'Nathan Tracey',brand:'My Trail Log',proposedCompany:'Thinking About Ltd',companyNumber:bindings().TRAIL_COMPANY_NUMBER||null,address:bindings().TRAIL_TRADER_ADDRESS||null}});}
