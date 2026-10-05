import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {AI_GLOBAL_DAILY_BUDGET_MICRO_USD,AI_MONTHLY_BUDGET_MICRO_USD,AI_BASE_MONTHLY_BUDGET_MICRO_USD,AI_STRONG_MONTHLY_BUDGET_MICRO_USD,AiBudgetError,CloserLookLimitError,closerLookStatus,aiDay,aiPeriod,dayEnds,reservationCostMicroUsd,reserveAiBudget,settleAiBudget,usageCostMicroUsd,tokenUsage} from '../lib/ai-budget';
import {DEFAULT_VISION_MODEL,STRONG_VISION_MODEL} from '../lib/vision-model';
import {PLANS} from '../lib/billing-policy';

// Exercise the actual D1 SQL against SQLite, including transactional batches.
// No provider request, credentials or mocked purchase grants are involved.
function fixture(){
  const sqlite=new DatabaseSync(':memory:');
  sqlite.exec(`PRAGMA foreign_keys=ON;
    CREATE TABLE users(id TEXT PRIMARY KEY);
    CREATE TABLE auth_attempts(key TEXT PRIMARY KEY,count INTEGER NOT NULL,reset_at INTEGER NOT NULL);
    CREATE TABLE subscriptions(user_id TEXT,plan TEXT,status TEXT,expires_at TEXT,checked_at INTEGER,acknowledged INTEGER,starts_at TEXT,is_trial INTEGER,paid_period_start TEXT,paid_period_end TEXT);
    CREATE TABLE photo_allowance(observation_id TEXT PRIMARY KEY,user_id TEXT,period TEXT,bonus_year TEXT);
    CREATE TABLE ai_usage(id TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,observation_id TEXT,period TEXT,purpose TEXT,model TEXT,input_tokens INTEGER,output_tokens INTEGER,micro_usd INTEGER,created_at TEXT);
    CREATE TABLE ai_reservations(id TEXT PRIMARY KEY,day_key TEXT NOT NULL,reserved_micro_usd INTEGER NOT NULL,settled_micro_usd INTEGER,created_at TEXT NOT NULL,settled_at TEXT);
    INSERT INTO users VALUES('alice'),('bob');`);
  class Prepared {
    values:any[]=[];
    constructor(readonly sql:string){}
    bind(...values:any[]){this.values=values;return this;}
    execute(){const statement=sqlite.prepare(this.sql);if(/RETURNING/i.test(this.sql)||/^SELECT/i.test(this.sql))return {results:statement.all(...this.values),meta:{changes:0}};const r=statement.run(...this.values);return {results:[],meta:{changes:Number(r.changes)}};}
    async all(){return this.execute();}
    async first(){return this.execute().results[0]||null;}
    async run(){return this.execute();}
  }
  const db={prepare:(sql:string)=>new Prepared(sql),batch:async(statements:Prepared[])=>{
    sqlite.exec('BEGIN IMMEDIATE');try{const results=statements.map(statement=>statement.execute());sqlite.exec('COMMIT');return results;}catch(error){sqlite.exec('ROLLBACK');throw error;}
  }} as unknown as D1Database;
  const now=new Date('2026-10-05T14:30:00Z');
  function paid(plan='premium',checked=now.getTime(),acknowledged=1,status='SUBSCRIPTION_STATE_ACTIVE',options:{trial?:boolean;startsAt?:string;paidStart?:string|null;paidEnd?:string|null;expiresAt?:string}={}){
    sqlite.prepare('INSERT INTO subscriptions VALUES(?,?,?,?,?,?,?,?,?,?)').run('alice',plan,status,options.expiresAt||'2027-10-05T14:30:00Z',checked,acknowledged,options.startsAt||now.toISOString(),options.trial?1:0,options.paidStart===undefined?now.toISOString():options.paidStart,options.paidEnd===undefined?'2027-10-05T14:30:00Z':options.paidEnd);
  }
  function usage(id:string,purpose:string,cost=0,created=now.toISOString(),period=aiPeriod(new Date(created)),observationId:string|null=id){sqlite.prepare('INSERT INTO ai_usage VALUES(?,?,?,?,?,?,?,?,?,?)').run(id,'alice',observationId,period,purpose,purpose==='identify-simple'?DEFAULT_VISION_MODEL:STRONG_VISION_MODEL,1,1,cost,created);}
  return {sqlite,db,now,paid,usage};
}

test('usage charges cache reads/writes correctly and counts reasoning once',()=>{
  const result={usage:{input_tokens:1000,input_tokens_details:{cached_tokens:300,cache_write_tokens:200},output_tokens:100,output_tokens_details:{reasoning_tokens:40}}};
  assert.equal(usageCostMicroUsd(DEFAULT_VISION_MODEL,result),128); // 50+3+25+50
  assert.equal(usageCostMicroUsd(STRONG_VISION_MODEL,result),2530);
  assert.equal(usageCostMicroUsd(DEFAULT_VISION_MODEL,{usage:{input_tokens:1000,output_tokens:100}}),175);
  assert.equal(usageCostMicroUsd('gpt-image-2.5-sunburst',{usage:{input_tokens:1500,input_tokens_details:{text_tokens:500,image_tokens:1000},output_tokens:196}}),16380);
});

test('malformed or unknown usage never reduces a spend reservation',()=>{
  assert.equal(tokenUsage({usage:{input_tokens:-1,output_tokens:2}}),null);
  assert.equal(tokenUsage({usage:{input_tokens:5,output_tokens:4,output_tokens_details:{reasoning_tokens:9}}}),null);
  assert.equal(usageCostMicroUsd(DEFAULT_VISION_MODEL,{usage:{input_tokens:20,output_tokens:2,input_tokens_details:{cached_tokens:21}}}),null);
  assert.equal(usageCostMicroUsd('unknown-expensive-model',{usage:{input_tokens:1,output_tokens:1}}),null);
  assert.throws(()=>reservationCostMicroUsd('identify-strong',DEFAULT_VISION_MODEL),AiBudgetError);
  assert.throws(()=>reservationCostMicroUsd('identify-simple',STRONG_VISION_MODEL),AiBudgetError);
  assert.throws(()=>reservationCostMicroUsd('publish',STRONG_VISION_MODEL),AiBudgetError);
  assert.throws(()=>reservationCostMicroUsd('avatar-generation','gpt-image-1-mini'),AiBudgetError);
});

test('concurrent primary attempts cannot overrun the free account allowance',async()=>{
  const {sqlite,db,now}=fixture();
  const results=await Promise.allSettled(Array.from({length:100},(_,i)=>reserveAiBudget('alice','photo-'+i,'identify',STRONG_VISION_MODEL,db,now)));
  const accepted=results.filter(result=>result.status==='fulfilled');
  assert.ok(accepted.length>0&&accepted.length<100);
  const spend=Number(sqlite.prepare('SELECT SUM(micro_usd) AS n FROM ai_usage').get()?.n);
  assert.equal(accepted.length,PLANS.free.closerLookLimit);
  assert.ok(spend<=AI_STRONG_MONTHLY_BUDGET_MICRO_USD.free);
  assert.equal((await closerLookStatus('alice',db,now)).closerLooksRemaining,0);
  await reserveAiBudget('alice','simple-after-review-limit','identify-simple',DEFAULT_VISION_MODEL,db,now);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,spend+1350);
  sqlite.close();
});

test('a failed request retains estimated spend and settlement is idempotent',async()=>{
  const {sqlite,db,now}=fixture();
  const reservation=await reserveAiBudget('alice','photo','publish',DEFAULT_VISION_MODEL,db,now);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,580);
  await settleAiBudget(reservation,{error:{message:'upstream failure'}});
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,580);
  assert.equal(sqlite.prepare('SELECT settled_micro_usd FROM ai_reservations').get()?.settled_micro_usd,null);
  const result={usage:{input_tokens:1000,input_tokens_details:{cached_tokens:0,cache_write_tokens:0},output_tokens:80}};
  await settleAiBudget(reservation,result);await settleAiBudget(reservation,result);
  assert.equal(sqlite.prepare('SELECT micro_usd FROM ai_usage').get()?.micro_usd,140);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,140);
  assert.equal(sqlite.prepare('SELECT purpose FROM ai_usage').get()?.purpose,'publish');sqlite.close();
});

test('post-deletion actual settlement survives and concurrent duplicates cannot alter global spend',async()=>{
  const {sqlite,db,now}=fixture();
  const reservation=await reserveAiBudget('alice','photo','identify',STRONG_VISION_MODEL,db,now);
  sqlite.prepare('DELETE FROM users WHERE id=?').run('alice');
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM ai_usage').get()?.n,0);
  const result={usage:{input_tokens:10000,input_tokens_details:{cached_tokens:0,cache_write_tokens:0},output_tokens:4000}};
  await Promise.all(Array.from({length:20},()=>settleAiBudget(reservation,result)));
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,60000);
  assert.equal(sqlite.prepare('SELECT settled_micro_usd FROM ai_reservations').get()?.settled_micro_usd,60000);
  assert.deepEqual(sqlite.prepare('PRAGMA table_info(ai_reservations)').all().map(column=>column.name),['id','day_key','reserved_micro_usd','settled_micro_usd','created_at','settled_at']);
  sqlite.close();
});

test('a failed settlement rolls back the counter and marker and remains safely retryable',async()=>{
  const {sqlite,db,now}=fixture();
  const reservation=await reserveAiBudget('alice','photo','publish',DEFAULT_VISION_MODEL,db,now);
  const result={usage:{input_tokens:1000,input_tokens_details:{cached_tokens:0,cache_write_tokens:0},output_tokens:80}};
  sqlite.exec("CREATE TRIGGER fail_settlement BEFORE UPDATE ON ai_usage BEGIN SELECT RAISE(ABORT,'forced settlement failure'); END;");
  await assert.rejects(settleAiBudget(reservation,result),/forced settlement failure/);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,580);
  assert.equal(sqlite.prepare('SELECT settled_micro_usd FROM ai_reservations').get()?.settled_micro_usd,null);
  sqlite.exec('DROP TRIGGER fail_settlement');
  await settleAiBudget(reservation,result);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,140);
  sqlite.prepare('DELETE FROM users WHERE id=?').run('alice');
  await settleAiBudget(reservation,result);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,140);
  sqlite.close();
});

test('global circuit cannot be erased by account deletion',async()=>{
  const {sqlite,db,now}=fixture();
  await reserveAiBudget('alice',null,'avatar-generation','gpt-image-2.5-sunburst',db,now);
  sqlite.prepare('DELETE FROM users WHERE id=?').run('alice');
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM ai_usage').get()?.n,0);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,80000);
  sqlite.prepare('UPDATE auth_attempts SET count=?').run(AI_GLOBAL_DAILY_BUDGET_MICRO_USD-579);
  await assert.rejects(reserveAiBudget('bob','photo','publish',DEFAULT_VISION_MODEL,db,now),AiBudgetError);
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM ai_usage').get()?.n,0);sqlite.close();
});

test('the UTC daily global cap resets without resetting the monthly member spend',async()=>{
  const {sqlite,db,now}=fixture();
  await reserveAiBudget('alice','photo','publish',DEFAULT_VISION_MODEL,db,now);
  const next=new Date('2026-10-06T00:00:00Z');await reserveAiBudget('alice','photo2','publish',DEFAULT_VISION_MODEL,db,next);
  assert.equal(aiPeriod(now),aiPeriod(next));assert.notEqual(aiDay(now),aiDay(next));assert.equal(dayEnds(now),next.getTime());
  assert.equal(sqlite.prepare('SELECT SUM(micro_usd) AS n FROM ai_usage').get()?.n,1160);
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM auth_attempts').get()?.n,2);sqlite.close();
});

test('simple recognition uses Luna and stronger review uses Sol with no unknown-model bypass',async()=>{
  const {sqlite,db,now}=fixture();
  await assert.rejects(reserveAiBudget('alice','photo','identify-simple',STRONG_VISION_MODEL,db,now),AiBudgetError);
  await assert.rejects(reserveAiBudget('alice','photo','identify-strong',DEFAULT_VISION_MODEL,db,now),AiBudgetError);
  const simple=await reserveAiBudget('alice','photo','identify-simple',DEFAULT_VISION_MODEL,db,now);
  const strong=await reserveAiBudget('alice','photo','identify-strong',STRONG_VISION_MODEL,db,now);
  assert.equal(sqlite.prepare('SELECT micro_usd FROM ai_usage WHERE id=?').get(simple.id)?.micro_usd,1350);
  assert.equal(sqlite.prepare('SELECT micro_usd FROM ai_usage WHERE id=?').get(strong.id)?.micro_usd,55000);sqlite.close();
});

test('stale, unacknowledged and inactive subscriptions cannot raise AI budgets',async()=>{
  for(const kind of ['stale','unacknowledged','on-hold']){
    const {sqlite,db,now,paid}=fixture();paid('premium',kind==='stale'?now.getTime()-16*60*1000:now.getTime(),kind==='unacknowledged'?0:1,kind==='on-hold'?'SUBSCRIPTION_STATE_ON_HOLD':'SUBSCRIPTION_STATE_ACTIVE');
    sqlite.prepare('INSERT INTO ai_usage VALUES(?,?,?,?,?,?,?,?,?,?)').run('old','alice',null,aiPeriod(now),'identify',STRONG_VISION_MODEL,1,1,AI_MONTHLY_BUDGET_MICRO_USD.free,now.toISOString());
    await assert.rejects(reserveAiBudget('alice','photo','identify',STRONG_VISION_MODEL,db,now),AiBudgetError);sqlite.close();
  }
});

test('annual bonus increases only the budget of admitted bonus photos and avatar pool is separate',async()=>{
  const {sqlite,db,now,paid,usage}=fixture();paid('annual');
  usage('old','identify-simple',AI_BASE_MONTHLY_BUDGET_MICRO_USD.annual);
  await assert.rejects(reserveAiBudget('alice','new','identify-simple',DEFAULT_VISION_MODEL,db,now),AiBudgetError);
  sqlite.prepare('INSERT INTO photo_allowance VALUES(?,?,?,?)').run('bonus-photo','alice',aiPeriod(now),now.toISOString());
  await reserveAiBudget('alice','bonus-photo','identify-simple',DEFAULT_VISION_MODEL,db,now);
  await reserveAiBudget('alice',null,'avatar-generation','gpt-image-2.5-sunburst',db,now);
  assert.equal(sqlite.prepare('SELECT SUM(micro_usd) AS n FROM ai_usage').get()?.n,AI_BASE_MONTHLY_BUDGET_MICRO_USD.annual+1350+80000);sqlite.close();
});

test('measured overrun is fully charged and stops future work instead of clipping costs',async()=>{
  const {sqlite,db,now}=fixture();const reservation=await reserveAiBudget('alice','photo','identify',STRONG_VISION_MODEL,db,now);
  await settleAiBudget(reservation,{usage:{input_tokens:900000,input_tokens_details:{cached_tokens:0,cache_write_tokens:0},output_tokens:128000}});
  assert.equal(sqlite.prepare('SELECT micro_usd FROM ai_usage').get()?.micro_usd,3080000);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,3080000);
  await assert.rejects(reserveAiBudget('alice','photo2','identify-strong',STRONG_VISION_MODEL,db,now),AiBudgetError);
  await reserveAiBudget('alice','photo2','publish',DEFAULT_VISION_MODEL,db,now);sqlite.close();
});

test('a trial cannot double its stronger reviews by crossing UTC months',async()=>{
  const {sqlite,db,now,paid}=fixture();paid('plus',now.getTime(),1,'SUBSCRIPTION_STATE_ACTIVE',{trial:true,expiresAt:'2026-11-05T14:30:00Z'});
  for(let i=0;i<PLANS.plus.closerLookLimit;i++)await reserveAiBudget('alice','trial-'+i,'identify-strong',STRONG_VISION_MODEL,db,now);
  const next=new Date('2026-11-01T10:00:00Z');sqlite.prepare('UPDATE subscriptions SET checked_at=?').run(next.getTime());
  await assert.rejects(reserveAiBudget('alice','next-month','identify-strong',STRONG_VISION_MODEL,db,next),CloserLookLimitError);
  const status=await closerLookStatus('alice',db,next);
  assert.equal(status.closerLooksUsed,0);assert.equal(status.closerLooksRemaining,0);assert.equal(status.trialCloserLooksRemaining,0);assert.equal(status.annualBonusCloserLooksRemaining,0);
  await reserveAiBudget('alice','pending-simple','identify-simple',DEFAULT_VISION_MODEL,db,next);sqlite.close();
});

test('an annual paid term cannot gain a thirteenth full bucket of stronger reviews',async()=>{
  const {sqlite,db,now,paid,usage}=fixture(),start='2025-10-15T10:00:00.000Z';
  paid('annual',now.getTime(),1,'SUBSCRIPTION_STATE_ACTIVE',{paidStart:start,paidEnd:'2026-10-15T10:00:00.000Z'});
  for(let month=0;month<12;month++){
    const created=new Date(Date.UTC(2025,9+month,16,10)).toISOString();
    for(let i=0;i<PLANS.annual.closerLookLimit;i++)usage('annual-'+month+'-'+i,'identify-strong',0,created);
  }
  await assert.rejects(reserveAiBudget('alice','thirteenth-bucket','identify-strong',STRONG_VISION_MODEL,db,now),CloserLookLimitError);
  assert.equal((await closerLookStatus('alice',db,now)).closerLooksRemaining,0);
  sqlite.prepare('INSERT INTO photo_allowance VALUES(?,?,?,?)').run('annual-bonus','alice',aiPeriod(now),start);
  const review=await reserveAiBudget('alice','annual-bonus','identify-strong',STRONG_VISION_MODEL,db,now);
  assert.equal(review.purpose,'identify-strong-bonus');sqlite.close();
});

test('annual bonus reviews require admitted bonus photos and stop after the paid-term bonus cap',async()=>{
  const {sqlite,db,now,paid,usage}=fixture();paid('annual');
  for(let i=0;i<PLANS.annual.closerLookLimit;i++)usage('base-'+i,'identify-strong',55000);
  await assert.rejects(reserveAiBudget('alice','ordinary','identify-strong-bonus',STRONG_VISION_MODEL,db,now),CloserLookLimitError);
  for(let i=0;i<40;i++)sqlite.prepare('INSERT INTO photo_allowance VALUES(?,?,?,?)').run('bonus-'+i,'alice',aiPeriod(now),now.toISOString());
  const results=await Promise.allSettled(Array.from({length:40},(_,i)=>reserveAiBudget('alice','bonus-'+i,'identify-strong',STRONG_VISION_MODEL,db,now)));
  assert.equal(results.filter(result=>result.status==='fulfilled').length,PLANS.annual.annualBonusCloserLooks);
  assert.equal((await closerLookStatus('alice',db,now)).annualBonusCloserLooksRemaining,0);
  assert.equal(sqlite.prepare("SELECT COUNT(*) AS n FROM ai_usage WHERE purpose='identify-strong-bonus-reserved'").get()?.n,30);sqlite.close();
});

test('trial and ended annual periods cannot use bonus reviews even when base access remains',async()=>{
  for(const trial of [true,false]){
    const {sqlite,db,now,paid,usage}=fixture(),start='2025-09-05T14:30:00.000Z';
    paid('annual',now.getTime(),1,trial?'SUBSCRIPTION_STATE_ACTIVE':'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',{trial,paidStart:start,paidEnd:'2026-09-05T14:30:00.000Z'});
    for(let i=0;i<PLANS.annual.closerLookLimit;i++)usage('base-'+i,'identify-strong',0);
    sqlite.prepare('INSERT INTO photo_allowance VALUES(?,?,?,?)').run('old-bonus','alice',aiPeriod(now),start);
    await assert.rejects(reserveAiBudget('alice','old-bonus','identify-strong',STRONG_VISION_MODEL,db,now),CloserLookLimitError);
    assert.equal((await closerLookStatus('alice',db,now)).annualBonusCloserLooksRemaining,0);
    await reserveAiBudget('alice','simple','identify-simple',DEFAULT_VISION_MODEL,db,now);sqlite.close();
  }
});

test('owner model probes spend money without consuming the member stronger-review count',async()=>{
  const {sqlite,db,now}=fixture();
  await reserveAiBudget('alice',null,'model-test',STRONG_VISION_MODEL,db,now);
  assert.equal(reservationCostMicroUsd('model-test',STRONG_VISION_MODEL),7620);
  assert.equal((await closerLookStatus('alice',db,now)).closerLooksUsed,0);
  assert.equal(sqlite.prepare('SELECT count FROM auth_attempts').get()?.count,7620);sqlite.close();
});

test('simple-stage concurrency cannot overspend its separate base pool',async()=>{
  const {sqlite,db,now}=fixture();
  const results=await Promise.allSettled(Array.from({length:200},(_,i)=>reserveAiBudget('alice','simple-'+i,'identify-simple',DEFAULT_VISION_MODEL,db,now)));
  const count=results.filter(result=>result.status==='fulfilled').length;
  assert.equal(count,Math.floor(AI_BASE_MONTHLY_BUDGET_MICRO_USD.free/1350));
  assert.ok(Number(sqlite.prepare('SELECT SUM(micro_usd) AS n FROM ai_usage').get()?.n)<=AI_BASE_MONTHLY_BUDGET_MICRO_USD.free);
  await reserveAiBudget('alice','strong-after-base-limit','identify-strong',STRONG_VISION_MODEL,db,now);sqlite.close();
});

test('a verified annual member keeps monthly base reviews while paid-order bonuses await proof',async()=>{
  const {sqlite,db,now,paid}=fixture();paid('annual',now.getTime(),1,'SUBSCRIPTION_STATE_ACTIVE',{paidStart:null,paidEnd:null});
  const before=await closerLookStatus('alice',db,now);
  assert.equal(before.closerLooksRemaining,60);assert.equal(before.annualBonusCloserLooksRemaining,0);assert.equal(before.closerLookBonusPending,true);assert.equal(before.closerLookStatus,'ready');
  for(let i=0;i<60;i++)await reserveAiBudget('alice','base-'+i,'identify-strong',STRONG_VISION_MODEL,db,now);
  sqlite.prepare('INSERT INTO photo_allowance VALUES(?,?,?,?)').run('unproved-bonus','alice',aiPeriod(now),now.toISOString());
  await assert.rejects(reserveAiBudget('alice','unproved-bonus','identify-strong',STRONG_VISION_MODEL,db,now),CloserLookLimitError);sqlite.close();
});
