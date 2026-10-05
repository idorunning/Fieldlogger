import {PLANS,subscriptionGrants,type Plan} from './billing-policy';
import {DEFAULT_VISION_MODEL,STRONG_VISION_MODEL,VISION_INPUT_BUDGETS,VISION_OUTPUT_LIMITS} from './vision-model';

export type AiPurpose='identify'|'identify-simple'|'identify-strong'|'identify-strong-bonus'|'model-test'|'publish'|'avatar-check'|'avatar-generation';
export class AiBudgetError extends Error {
  readonly status=503;
  constructor(message='Photo analysis is temporarily resting. Your photo is saved in your private journal. Please try later.') {
    super(message);this.name='AiBudgetError';
  }
}
export class CloserLookLimitError extends AiBudgetError {
  constructor(){super('A Closer Look is unavailable for this period. Your photo is saved with its tentative identification.');this.name='CloserLookLimitError';}
}
// Reserve Luna 6k input/1200 output + safety 4k/160 = $0.00193/photo;
// a Sol 6k/4000 review adds $0.055. Separate pools preserve ordinary recognition.
export const AI_BASE_PHOTO_RESERVE_MICRO_USD=1930;
export const AI_STRONG_RESERVE_MICRO_USD=55000;
const margin=(amount:number)=>Math.ceil(amount*1.2);
const plans=Object.keys(PLANS) as Plan[];
export const AI_BASE_MONTHLY_BUDGET_MICRO_USD=Object.fromEntries(plans.map(plan=>[plan,margin(PLANS[plan].limit*AI_BASE_PHOTO_RESERVE_MICRO_USD)])) as Record<Plan,number>;
export const AI_STRONG_MONTHLY_BUDGET_MICRO_USD=Object.fromEntries(plans.map(plan=>[plan,margin(PLANS[plan].closerLookLimit*AI_STRONG_RESERVE_MICRO_USD)])) as Record<Plan,number>;
export const AI_MONTHLY_BUDGET_MICRO_USD=Object.fromEntries(plans.map(plan=>[plan,AI_BASE_MONTHLY_BUDGET_MICRO_USD[plan]+AI_STRONG_MONTHLY_BUDGET_MICRO_USD[plan]])) as Record<Plan,number>;
export const AI_AVATAR_MONTHLY_BUDGET_MICRO_USD=150000;
export const AI_GLOBAL_DAILY_BUDGET_MICRO_USD=20000000;
export const AI_BONUS_PHOTO_BUDGET_MICRO_USD=margin(AI_BASE_PHOTO_RESERVE_MICRO_USD);
const FRESH_MS=15*60*1000;
const IMAGE_MODELS=new Set(['gpt-image-2.5-sunburst','gpt-image-2.5-flare']);
type Db=D1Database;
type DbSubscription={plan:Plan;status:string;expires_at:string;checked_at:number;acknowledged:number;starts_at:string;is_trial:number;paid_period_start:string|null;paid_period_end:string|null};
export function aiPeriod(now=new Date()){return now.toISOString().slice(0,7);}
export function aiDay(now=new Date()){return now.toISOString().slice(0,10);}
export function dayEnds(now=new Date()){return Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),now.getUTCDate()+1);}
function integer(value:unknown){return typeof value==='number'&&Number.isSafeInteger(value)&&value>=0;}
type Usage={input_tokens:number;output_tokens:number;input_tokens_details?:{cached_tokens?:unknown;cache_write_tokens?:unknown;text_tokens?:unknown;image_tokens?:unknown};output_tokens_details?:{reasoning_tokens?:unknown}};
export function tokenUsage(result:unknown):Usage|null {
  const value=(result as {usage?:Usage}|null)?.usage;
  if(!value||!integer(value.input_tokens)||!integer(value.output_tokens)||value.input_tokens>1000000||value.output_tokens>128000)return null;
  const reasoning=value.output_tokens_details?.reasoning_tokens;
  if(reasoning!==undefined&&(!integer(reasoning)||Number(reasoning)>value.output_tokens))return null;
  return value;
}
// Integers are micro-USD, rounded up. Output includes reasoning already. Missing
// cache-write detail retains conservative higher input pricing.
export function usageCostMicroUsd(model:string,result:unknown):number|null {
  const usage=tokenUsage(result);if(!usage)return null;
  if(IMAGE_MODELS.has(model)) {
    const text=usage.input_tokens_details?.text_tokens,image=usage.input_tokens_details?.image_tokens;
    if(!integer(text)||!integer(image)||Number(text)+Number(image)!==usage.input_tokens)return null;
    return Math.ceil(Number(text)*5+Number(image)*8+usage.output_tokens*30);
  }
  if(model!==DEFAULT_VISION_MODEL&&model!==STRONG_VISION_MODEL)return null;
  const cached=usage.input_tokens_details?.cached_tokens??0,writes=usage.input_tokens_details?.cache_write_tokens;
  if(!integer(cached)||Number(cached)>usage.input_tokens||writes!==undefined&&(!integer(writes)||Number(cached)+Number(writes)>usage.input_tokens))return null;
  const writeCount=writes===undefined?usage.input_tokens-Number(cached):Number(writes),uncached=usage.input_tokens-Number(cached)-writeCount;
  const rate=model===DEFAULT_VISION_MODEL?{input:.1,cached:.01,write:.125,output:.5}:{input:2,cached:.1,write:2.5,output:10};
  return Math.ceil(uncached*rate.input+Number(cached)*rate.cached+writeCount*rate.write+usage.output_tokens*rate.output);
}
export function reservationCostMicroUsd(purpose:AiPurpose,model:string) {
  if(purpose==='avatar-generation') {if(!IMAGE_MODELS.has(model))throw new AiBudgetError('Avatar creation is temporarily unavailable. Your current avatar is kept.');return 80000;}
  if(model!==DEFAULT_VISION_MODEL&&model!==STRONG_VISION_MODEL)throw new AiBudgetError();
  const normal=normalPurpose(purpose,model),strong=normal.startsWith('identify-strong')||normal==='model-test';
  if(strong?model!==STRONG_VISION_MODEL:model!==DEFAULT_VISION_MODEL)throw new AiBudgetError();
  const stage=normal==='model-test'?'test':normal==='identify-simple'?'identify-simple':strong?'identify-strong':'publish';
  const input=VISION_INPUT_BUDGETS[stage],output=VISION_OUTPUT_LIMITS[stage];
  return Math.ceil(input*(model===DEFAULT_VISION_MODEL?.125:2.5)+output*(model===DEFAULT_VISION_MODEL?.5:10));
}
function normalPurpose(purpose:AiPurpose,model:string):AiPurpose{return purpose==='identify'?(model===DEFAULT_VISION_MODEL?'identify-simple':'identify-strong'):purpose==='identify-strong-bonus'?'identify-strong':purpose;}
const BASE_STRONG="(purpose IN ('identify-strong','identify-strong-reserved') OR (purpose IN ('identify','identify-reserved') AND observation_id IS NOT NULL))";
const BONUS_STRONG="purpose IN ('identify-strong-bonus','identify-strong-bonus-reserved')";
const STRONG_SPEND="(purpose LIKE 'identify-strong%' OR purpose LIKE 'model-test%' OR purpose IN ('identify','identify-reserved'))";
const BASE_ALLOWED='base_month_used<monthly_closer_limit AND (term_closer_limit IS NULL OR base_term_used<term_closer_limit)';
export const AI_RESERVATION_SQL=`WITH settings(id,user_id,observation_id,period,purpose,model,cost,created_at,pool,pool_limit,day_key,now_ms,global_limit,is_strong,monthly_closer_limit,term_start,term_closer_limit,bonus_year,bonus_closer_limit) AS (VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)),
  stats AS (SELECT s.*,
    COALESCE((SELECT SUM(micro_usd) FROM ai_usage WHERE user_id=s.user_id AND period=s.period AND CASE s.pool WHEN 'avatar' THEN purpose LIKE 'avatar-%' WHEN 'strong' THEN ${STRONG_SPEND} ELSE purpose NOT LIKE 'avatar-%' AND NOT ${STRONG_SPEND} END),0) AS pool_spend,
    COALESCE((SELECT count FROM auth_attempts WHERE key=s.day_key AND reset_at>s.now_ms),0) AS global_spend,
    (SELECT COUNT(*) FROM ai_usage WHERE user_id=s.user_id AND period=s.period AND ${BASE_STRONG}) AS base_month_used,
    (SELECT COUNT(*) FROM ai_usage WHERE user_id=s.user_id AND created_at>=s.term_start AND ${BASE_STRONG}) AS base_term_used,
    (SELECT COUNT(*) FROM ai_usage a JOIN photo_allowance p ON p.observation_id=a.observation_id AND p.user_id=a.user_id WHERE a.user_id=s.user_id AND p.bonus_year=s.bonus_year AND a.${BONUS_STRONG}) AS bonus_used,
    EXISTS(SELECT 1 FROM photo_allowance WHERE user_id=s.user_id AND observation_id=s.observation_id AND bonus_year=s.bonus_year) AS bonus_photo
  FROM settings s)
  INSERT INTO ai_usage(id,user_id,observation_id,period,purpose,model,input_tokens,output_tokens,micro_usd,created_at)
  SELECT id,user_id,observation_id,period,
    CASE WHEN is_strong=1 AND NOT (${BASE_ALLOWED}) THEN 'identify-strong-bonus-reserved' ELSE purpose||'-reserved' END,
    model,-1,-1,cost,created_at FROM stats
  WHERE pool_spend+cost<=pool_limit AND global_spend+cost<=global_limit
    AND (is_strong=0 OR (${BASE_ALLOWED}) OR (bonus_photo=1 AND bonus_used<bonus_closer_limit))
  RETURNING id,purpose`;
export const AI_GLOBAL_RESERVATION_SQL=`INSERT INTO auth_attempts(key,count,reset_at)
  SELECT ?,?,? WHERE EXISTS(SELECT 1 FROM ai_reservations WHERE id=?)
  ON CONFLICT(key) DO UPDATE SET count=CASE WHEN auth_attempts.reset_at<=? THEN excluded.count ELSE auth_attempts.count+excluded.count END,reset_at=excluded.reset_at`;
export type AiReservation={id:string;userId:string;purpose:AiPurpose;model:string;dayKey:string;db:Db};
async function budgetDb(provided?:Db){return provided||(await import('./server')).database();}
type AiContext={plan:Plan;monthlyCloserLimit:number;termStart:string|null;termCloserLimit:number|null;trial:boolean;bonusYear:string|null;bonusCloserLimit:number;baseBudget:number;strongBudget:number;verificationPending:boolean};
async function contextFor(userId:string,db:Db,now:Date):Promise<AiContext>{
  const rows=(await db.prepare('SELECT plan,status,expires_at,checked_at,acknowledged,starts_at,is_trial,paid_period_start,paid_period_end FROM subscriptions WHERE user_id=? AND acknowledged=1 ORDER BY expires_at DESC LIMIT 8').bind(userId).all<DbSubscription>()).results;
  const valid=rows.filter(row=>row.checked_at>=now.getTime()-FRESH_MS&&subscriptionGrants(row.status,row.expires_at,now.getTime())&&['plus','premium','annual'].includes(row.plan));
  valid.sort((a,b)=>PLANS[b.plan].limit-PLANS[a.plan].limit||Number(b.plan==='annual')-Number(a.plan==='annual')||b.expires_at.localeCompare(a.expires_at));
  const row=valid[0],plan:Plan=row?.plan||'free',trial=!!row?.is_trial;
  const paidStart=plan==='annual'&&!trial&&row.paid_period_start&&row.paid_period_end&&Number.isFinite(Date.parse(row.paid_period_start))&&Date.parse(row.paid_period_start)<=now.getTime()&&Date.parse(row.paid_period_end)>Date.parse(row.paid_period_start)?row.paid_period_start:null;
  const bonusYear=paidStart&&Date.parse(row.paid_period_end!)>now.getTime()?paidStart:null;
  const trialStart=trial&&Number.isFinite(Date.parse(row.starts_at))&&Date.parse(row.starts_at)<=now.getTime()?row.starts_at:null;
  const verificationPending=plan==='annual'&&!trial&&!paidStart||trial&&!trialStart;
  // Fresh Google entitlement grants the monthly base even if an order snapshot
  // is delayed. Paid-term proof is needed for annual anchoring and all bonuses.
  const invalidTrial=trial&&!trialStart;
  const termStart=paidStart||trialStart,monthlyCloserLimit=invalidTrial?0:PLANS[plan].closerLookLimit;
  const termCloserLimit=invalidTrial?0:paidStart?PLANS[plan].closerLookLimit*12:trialStart?PLANS[plan].closerLookLimit:null;
  let baseBudget=AI_BASE_MONTHLY_BUDGET_MICRO_USD[plan],strongBudget=AI_STRONG_MONTHLY_BUDGET_MICRO_USD[plan];
  if(bonusYear){
    const bonus=await db.prepare('SELECT COUNT(*) AS total,SUM(CASE WHEN period=? THEN 1 ELSE 0 END) AS monthly FROM photo_allowance WHERE user_id=? AND bonus_year=?').bind(aiPeriod(now),userId,bonusYear).first<{total:number;monthly:number}>();
    baseBudget+=Math.min(PLANS.annual.annualBonusPhotos,Math.max(0,Number(bonus?.monthly)||0))*AI_BONUS_PHOTO_BUDGET_MICRO_USD;
    strongBudget+=margin(Math.min(PLANS.annual.annualBonusCloserLooks,Math.max(0,Number(bonus?.total)||0))*AI_STRONG_RESERVE_MICRO_USD);
  }
  return {plan,monthlyCloserLimit,termStart,termCloserLimit,trial,bonusYear,bonusCloserLimit:bonusYear?PLANS.annual.annualBonusCloserLooks:0,baseBudget,strongBudget,verificationPending};
}
export async function closerLookStatus(userId:string,providedDb?:Db,now=new Date()){
  const db=await budgetDb(providedDb),context=await contextFor(userId,db,now),period=aiPeriod(now);
  const month=await db.prepare(`SELECT COUNT(*) AS n FROM ai_usage WHERE user_id=? AND period=? AND ${BASE_STRONG}`).bind(userId,period).first<{n:number}>();
  const term=context.termStart?await db.prepare(`SELECT COUNT(*) AS n FROM ai_usage WHERE user_id=? AND created_at>=? AND ${BASE_STRONG}`).bind(userId,context.termStart).first<{n:number}>():null;
  const bonus=context.bonusYear?await db.prepare(`SELECT COUNT(*) AS n FROM ai_usage a JOIN photo_allowance p ON p.observation_id=a.observation_id AND p.user_id=a.user_id WHERE a.user_id=? AND p.bonus_year=? AND a.${BONUS_STRONG}`).bind(userId,context.bonusYear).first<{n:number}>():null;
  const spend=await db.prepare(`SELECT SUM(micro_usd) AS n FROM ai_usage WHERE user_id=? AND period=? AND ${STRONG_SPEND}`).bind(userId,period).first<{n:number}>();
  const used=Number(month?.n)||0,termRemaining=context.termCloserLimit===null?Infinity:Math.max(0,context.termCloserLimit-(Number(term?.n)||0));
  const dollarsAvailable=context.strongBudget-(Number(spend?.n)||0)>=AI_STRONG_RESERVE_MICRO_USD;
  const remaining=Math.min(Math.max(0,context.monthlyCloserLimit-used),termRemaining);
  return {closerLookLimit:PLANS[context.plan].closerLookLimit,closerLooksUsed:used,closerLooksRemaining:dollarsAvailable?remaining:0,annualBonusCloserLooksRemaining:dollarsAvailable?Math.max(0,context.bonusCloserLimit-(Number(bonus?.n)||0)):0,trialCloserLooksRemaining:context.trial?termRemaining:null,closerLookBonusPending:context.plan==='annual'&&context.verificationPending,closerLookStatus:context.trial&&context.verificationPending?'verification-pending':!dollarsAvailable?'temporarily-unavailable':remaining>0?'ready':'used'};
}
export async function reserveAiBudget(userId:string|undefined,observationId:string|null,purpose:AiPurpose,model:string,providedDb?:Db,now=new Date()):Promise<AiReservation> {
  if(!userId)throw new AiBudgetError();
  purpose=normalPurpose(purpose,model);
  const db=await budgetDb(providedDb),period=aiPeriod(now),dayKey='ai-spend:'+aiDay(now),avatar=purpose.startsWith('avatar-'),strong=purpose.startsWith('identify-strong'),pool=avatar?'avatar':strong||purpose==='model-test'?'strong':'base',cost=reservationCostMicroUsd(purpose,model);
  if(strong&&!observationId)throw new CloserLookLimitError();
  const context=await contextFor(userId,db,now),limit=avatar?AI_AVATAR_MONTHLY_BUDGET_MICRO_USD:pool==='strong'?context.strongBudget:context.baseBudget;
  const id=crypto.randomUUID(),pending=purpose+'-reserved',created=now.toISOString();
  // D1 batch is transactional. Global spend has no member FK: deleting an
  // account must not erase its already incurred provider costs.
  const results=await db.batch([
    db.prepare(AI_RESERVATION_SQL).bind(id,userId,observationId,period,purpose,model,cost,created,pool,limit,dayKey,now.getTime(),AI_GLOBAL_DAILY_BUDGET_MICRO_USD,strong?1:0,context.monthlyCloserLimit,context.termStart,context.termCloserLimit,context.bonusYear,context.bonusCloserLimit),
    db.prepare('INSERT INTO ai_reservations(id,day_key,reserved_micro_usd,created_at) SELECT ?,?,?,? WHERE EXISTS(SELECT 1 FROM ai_usage WHERE id=?)').bind(id,dayKey,cost,created,id),
    db.prepare(AI_GLOBAL_RESERVATION_SQL).bind(dayKey,cost,dayEnds(now),id,now.getTime()),
  ]);
  if(!results[0]?.results?.length){if(strong)throw new CloserLookLimitError();throw new AiBudgetError(avatar?'Avatar creation is temporarily resting. Your current avatar is kept. Please try later.':undefined);}
  const saved=results[0].results[0] as unknown as {purpose:string};
  return {id,userId,purpose:(saved?.purpose||pending).replace(/-reserved$/,'') as AiPurpose,model,dayKey,db};
}
export async function settleAiBudget(reservation:AiReservation,result:unknown) {
  const usage=tokenUsage(result),cost=usageCostMicroUsd(reservation.model,result);if(!usage||cost===null)return;
  const pending=reservation.purpose+'-reserved';
  await reservation.db.batch([
    // The anonymous reservation remains when member-linked usage is deleted.
    // Batch atomicity makes both the counter and settlement marker idempotent.
    reservation.db.prepare(`UPDATE auth_attempts SET count=MAX(0,count+?-(SELECT reserved_micro_usd FROM ai_reservations WHERE id=?)) WHERE key=(SELECT day_key FROM ai_reservations WHERE id=?) AND EXISTS(SELECT 1 FROM ai_reservations WHERE id=? AND settled_micro_usd IS NULL)`).bind(cost,reservation.id,reservation.id,reservation.id),
    reservation.db.prepare('UPDATE ai_reservations SET settled_micro_usd=?,settled_at=? WHERE id=? AND settled_micro_usd IS NULL').bind(cost,new Date().toISOString(),reservation.id),
    reservation.db.prepare('UPDATE ai_usage SET purpose=?,input_tokens=?,output_tokens=?,micro_usd=? WHERE id=? AND user_id=? AND purpose=?').bind(reservation.purpose,usage.input_tokens,usage.output_tokens,cost,reservation.id,reservation.userId,pending),
  ]);
}
