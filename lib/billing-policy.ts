export const PLAY_PACKAGE = 'com.field.logger';
// Set only after the operator has selected the final commercial model.
export const PRICING_FINALISED = true;
export const PLANS = {
  free: {name:'Free',limit:100,closerLookLimit:5,monthlyPence:0,annualPence:null,productId:null,annualBonusPhotos:0,annualBonusCloserLooks:0},
  plus: {name:'Plus',limit:150,closerLookLimit:30,monthlyPence:599,annualPence:null,productId:'trail_plus_monthly',annualBonusPhotos:0,annualBonusCloserLooks:0},
  premium: {name:'Premium',limit:300,closerLookLimit:60,monthlyPence:1199,annualPence:null,productId:'trail_premium_monthly',annualBonusPhotos:0,annualBonusCloserLooks:0},
  annual: {name:'Premium annual',limit:300,closerLookLimit:60,monthlyPence:null,annualPence:11999,productId:'trail_premium_yearly',annualBonusPhotos:150,annualBonusCloserLooks:30},
} as const;
export type Plan = keyof typeof PLANS;
export function planForProduct(id:string):Plan|null {
  return (Object.keys(PLANS) as Plan[]).find(p=>PLANS[p].productId===id)||null;
}
export function utcPeriod(now = new Date()) {
  const start = new Date(Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),1));
  const end = new Date(Date.UTC(now.getUTCFullYear(),now.getUTCMonth()+1,1));
  return {key:start.toISOString().slice(0,7),start:start.toISOString(),end:end.toISOString()};
}
export function annualCycle(startsAt:string,now:Date):string|null {
  const start=new Date(startsAt); if(!Number.isFinite(start.getTime())||start>now)return null;
  let years=now.getUTCFullYear()-start.getUTCFullYear();
  const anniversary=(year:number)=>new Date(Date.UTC(year,start.getUTCMonth(),Math.min(start.getUTCDate(),new Date(Date.UTC(year,start.getUTCMonth()+1,0)).getUTCDate()),start.getUTCHours(),start.getUTCMinutes(),start.getUTCSeconds()));
  if(anniversary(start.getUTCFullYear()+years)>now)years--;
  return anniversary(start.getUTCFullYear()+years).toISOString();
}
export function subscriptionGrants(state:string,expiresAt:string,now=Date.now()) {
  return ['SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_IN_GRACE_PERIOD','SUBSCRIPTION_STATE_CANCELED'].includes(state)&&Number.isFinite(Date.parse(expiresAt))&&Date.parse(expiresAt)>now;
}
export type GooglePurchase = {subscriptionState?:string;startTime?:string;acknowledgementState?:string;externalAccountIdentifiers?:{obfuscatedExternalAccountId?:string};linkedPurchaseToken?:string;lineItems?:Array<{productId?:string;expiryTime?:string;latestSuccessfulOrderId?:string;offerDetails?:{basePlanId?:string;offerId?:string};offerPhase?:{freeTrial?:unknown}}>;};
export function checkedPurchase(p:GooglePurchase,expectedAccountHash:string,requestedProduct:string,now=Date.now()) {
  if(p.externalAccountIdentifiers?.obfuscatedExternalAccountId!==expectedAccountHash)throw new Error('account_binding');
  const items=(p.lineItems||[]).filter(i=>i.productId===requestedProduct&&planForProduct(i.productId));
  const item=items.sort((a,b)=>Date.parse(b.expiryTime||'')-Date.parse(a.expiryTime||''))[0];
  if(!item?.expiryTime||!p.startTime||!Number.isFinite(Date.parse(p.startTime)))throw new Error('product_binding');
  if(item.offerDetails?.basePlanId!==(requestedProduct==='trail_premium_yearly'?'yearly':'monthly'))throw new Error('base_plan_binding');
  const granted=subscriptionGrants(p.subscriptionState||'',item.expiryTime,now);
  const phase=item.offerPhase as any;
  return {plan:planForProduct(requestedProduct)!,status:p.subscriptionState||'unknown',startsAt:new Date(p.startTime).toISOString(),expiresAt:new Date(item.expiryTime).toISOString(),granted,acknowledged:p.acknowledgementState==='ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',isTrial:!!phase&&(Object.hasOwn(phase,'freeTrial')||phase.prorationPeriod?.originalOfferPhaseType==='FREE_TRIAL'),orderId:item.latestSuccessfulOrderId||null};
}
export function paidOrderPeriod(order:any,product:string,purchaseToken:string){
  if(order.purchaseToken!==purchaseToken||order.state!=='PROCESSED')throw new Error('Annual order unavailable');
  const details=order.lineItems?.find((i:any)=>i.productId===product)?.subscriptionDetails;
  const start=details?.servicePeriodStartTime,end=details?.servicePeriodEndTime;
  if(details?.basePlanId!=='yearly')throw new Error('Annual order unavailable');
  if(typeof start!=='string'||typeof end!=='string'||!Number.isFinite(Date.parse(start))||!Number.isFinite(Date.parse(end))||Date.parse(end)<=Date.parse(start))throw new Error('Annual order unavailable');
  const phase=details.offerPhaseDetails;
  if(!phase||!Object.hasOwn(phase,'baseDetails')||phase.baseDetails===null||typeof phase.baseDetails!=='object'||Object.hasOwn(phase,'freeTrialDetails')||Object.hasOwn(phase,'prorationPeriodDetails'))throw new Error('Annual bonus requires a full paid base period');
  return {start:new Date(start).toISOString(),end:new Date(end).toISOString()};
}
export function retainedAnnualPeriod(previous:{latest_order_id:string|null;paid_period_start:string|null;paid_period_end:string|null}|null,orderId:string|null,now=Date.now()){
  if(!orderId||previous?.latest_order_id!==orderId||!previous.paid_period_start||!previous.paid_period_end||!Number.isFinite(Date.parse(previous.paid_period_start))||Date.parse(previous.paid_period_end)<=now)return null;
  return {start:previous.paid_period_start,end:previous.paid_period_end};
}
