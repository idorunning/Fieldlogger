import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import {createRuntimeSchemaInitializer,RuntimeSchemaError,RUNTIME_SCHEMA_VERSION} from '../lib/runtime-migrations';

const migrations=['0000_brainy_bullseye','0001_strange_virginia_dare','0002_absent_vision','0003_tidy_firedrake','0004_pretty_doctor_spectrum','0005_spotty_rumiko_fujikawa','0006_freezing_mach_iv','0007_gifted_nomad','0008_sturdy_quasar'];
function apply(sqlite:DatabaseSync,last:number){for(let i=0;i<=last;i++)sqlite.exec(readFileSync(new URL('../drizzle/'+migrations[i]+'.sql',import.meta.url),'utf8'));}
function fixture(last=3){
  const sqlite=new DatabaseSync(':memory:');sqlite.exec('PRAGMA foreign_keys=ON');if(last>=0)apply(sqlite,last);
  const trace:string[]=[];let failAlter=false,duplicateAlters=0;
  class Prepared {
    values:any[]=[];
    constructor(readonly sql:string){}
    bind(...values:any[]){this.values=values;return this;}
    execute(){
      trace.push(this.sql);
      if(failAlter&&/^ALTER TABLE subscriptions/.test(this.sql)){failAlter=false;throw new Error('Synthetic temporary D1 failure');}
      try{
        const statement=sqlite.prepare(this.sql);
        if(/^(PRAGMA|SELECT)/i.test(this.sql))return {success:true,results:statement.all(...this.values),meta:{changes:0}};
        const result=statement.run(...this.values);return {success:true,results:[],meta:{changes:Number(result.changes)}};
      }catch(error){if(/^ALTER TABLE/.test(this.sql)&&String(error).includes('duplicate column'))duplicateAlters++;throw error;}
    }
    async all(){const snapshot=this.execute();await Promise.resolve();return snapshot;}
    async run(){return this.execute();}
  }
  const db={prepare:(sql:string)=>new Prepared(sql),batch:async(statements:Prepared[])=>{
    sqlite.exec('BEGIN IMMEDIATE');try{const results=statements.map(statement=>statement.execute());sqlite.exec('COMMIT');return results;}catch(error){sqlite.exec('ROLLBACK');throw error;}
  }} as unknown as D1Database;
  return {sqlite,db,trace,failNextAlter(){failAlter=true;},duplicateAlters(){return duplicateAlters;}};
}
function seedLegacy(sqlite:DatabaseSync){
  sqlite.exec(`INSERT INTO users VALUES('owner','synthetic-owner@example.invalid','Synthetic Owner','test-hash','test-salt','2026-10-05T12:00:00Z');
    INSERT INTO observations VALUES('photo','owner','{"name":"Existing private journal","archived":true}','private/test-photo','2026-10-05T12:00:00Z');
    INSERT INTO account_keys VALUES('owner','synthetic-existing-envelope','2026-10-05T12:00:00Z');
    INSERT INTO sessions VALUES('synthetic-session','owner',2000000000);
    INSERT INTO profiles(user_id,username,discoverable,avatar) VALUES('owner','testowner',0,'{}');
    INSERT INTO achievement_unlocks VALUES('owner','pine','2026-10-05T12:00:00Z');
    CREATE TABLE d1_migrations(id INTEGER PRIMARY KEY,name TEXT NOT NULL,applied_at TEXT NOT NULL);
    INSERT INTO d1_migrations VALUES(1,'legacy-0003','2026-10-05T12:00:00Z');`);
}
function legacyRows(sqlite:DatabaseSync){return ['users','observations','account_keys','sessions','profiles','achievement_unlocks','d1_migrations'].map(table=>sqlite.prepare('SELECT * FROM '+table).all());}
const newTables=['admin_audit','ai_usage','avatar_generations','member_access','photo_allowance','publication_checks','subscriptions','service_config','ai_reservations','identification_stages'];
function schema(sqlite:DatabaseSync){return newTables.map(name=>({name,columns:sqlite.prepare('PRAGMA table_info('+name+')').all(),foreignKeys:sqlite.prepare('PRAGMA foreign_key_list('+name+')').all(),indexes:sqlite.prepare('PRAGMA index_list('+name+')').all()}));}
function hasLedger(sqlite:DatabaseSync){return Boolean(sqlite.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name='_trail_runtime_schema'").get());}

test('old production schema upgrades to canonical schema without touching existing private rows or the managed migration ledger',async()=>{
  const f=fixture(),canonical=fixture(8);seedLegacy(f.sqlite);const before=legacyRows(f.sqlite);
  await createRuntimeSchemaInitializer()(f.db);
  assert.deepEqual(schema(f.sqlite),schema(canonical.sqlite));assert.deepEqual(legacyRows(f.sqlite),before);
  assert.equal(f.sqlite.prepare('SELECT version FROM _trail_runtime_schema').get()?.version,RUNTIME_SCHEMA_VERSION);
  assert.ok(f.trace.every(sql=>/^(PRAGMA|CREATE|ALTER|INSERT INTO _trail_runtime_schema)/i.test(sql)),'Initializer never reads journal/member/credential rows');
  assert.ok(f.trace.every(sql=>!/(DROP|DELETE|UPDATE)\s+(TABLE|FROM|users|observations|account_keys)/i.test(sql)));
  f.sqlite.close();canonical.sqlite.close();
});

test('same-isolate calls share one promise; later independent isolates reverify without rewriting rows or ledger time',async()=>{
  const f=fixture();seedLegacy(f.sqlite);const init=createRuntimeSchemaInitializer();const first=init(f.db);assert.equal(init(f.db),first);await first;
  const before=legacyRows(f.sqlite),ledger=f.sqlite.prepare('SELECT * FROM _trail_runtime_schema').all(),alters=f.trace.filter(sql=>/^ALTER/.test(sql)).length;
  await init(f.db);await createRuntimeSchemaInitializer()(f.db);
  assert.equal(f.trace.filter(sql=>/^ALTER/.test(sql)).length,alters);assert.deepEqual(legacyRows(f.sqlite),before);
  assert.deepEqual(f.sqlite.prepare('SELECT * FROM _trail_runtime_schema').all(),ledger);f.sqlite.close();
});

test('a partial prior upgrade completes only missing columns and preserves verified subscription and admin state',async()=>{
  const f=fixture(4);seedLegacy(f.sqlite);f.sqlite.exec(`ALTER TABLE subscriptions ADD COLUMN paid_period_start TEXT;
    INSERT INTO subscriptions(token_hash,user_id,token_envelope,product_id,plan,status,starts_at,expires_at,checked_at,acknowledged,is_trial,paid_period_start) VALUES('synthetic-token','owner','synthetic-envelope','test-product','annual','SUBSCRIPTION_STATE_ACTIVE','2026-10-05','2027-10-05',1,1,0,'2026-10-05');
    INSERT INTO member_access VALUES('owner','admin','active','2026-10-05');`);
  const before=f.sqlite.prepare('SELECT * FROM subscriptions').get(),members=f.sqlite.prepare('SELECT * FROM member_access').all();
  await createRuntimeSchemaInitializer()(f.db);
  const after=f.sqlite.prepare('SELECT * FROM subscriptions').get();assert.deepEqual({...after},{...before,paid_period_end:null,latest_order_id:null});
  assert.deepEqual(f.sqlite.prepare('SELECT * FROM member_access').all(),members);
  assert.equal(f.trace.filter(sql=>/^ALTER/.test(sql)).length,2);f.sqlite.close();
});

test('two independent isolates tolerate real duplicate ALTER races only after verifying correct postconditions',async()=>{
  const f=fixture();seedLegacy(f.sqlite);const before=legacyRows(f.sqlite);
  await Promise.all([createRuntimeSchemaInitializer()(f.db),createRuntimeSchemaInitializer()(f.db)]);
  assert.ok(f.duplicateAlters()>0,'Both isolates observed a missing column before one won the ALTER');
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM _trail_runtime_schema').get()?.n,1);
  assert.deepEqual(legacyRows(f.sqlite),before);f.sqlite.close();
});

test('a temporary failure leaves no success marker and the same initializer retries successfully',async()=>{
  const f=fixture();seedLegacy(f.sqlite);const before=legacyRows(f.sqlite),init=createRuntimeSchemaInitializer();f.failNextAlter();
  await assert.rejects(init(f.db),error=>error instanceof RuntimeSchemaError&&error.status===503);
  assert.equal(hasLedger(f.sqlite),false);assert.deepEqual(legacyRows(f.sqlite),before);
  await init(f.db);assert.equal(hasLedger(f.sqlite),true);assert.deepEqual(legacyRows(f.sqlite),before);f.sqlite.close();
});

test('incompatible existing columns fail closed without replacing schema or declaring success',async()=>{
  const f=fixture(4);seedLegacy(f.sqlite);f.sqlite.exec('ALTER TABLE subscriptions ADD COLUMN paid_period_start INTEGER');const before=legacyRows(f.sqlite);
  await assert.rejects(createRuntimeSchemaInitializer()(f.db),RuntimeSchemaError);
  assert.equal(hasLedger(f.sqlite),false);assert.equal(f.sqlite.prepare('PRAGMA table_info(subscriptions)').all().find(c=>c.name==='paid_period_start')?.type,'INTEGER');
  assert.deepEqual(legacyRows(f.sqlite),before);f.sqlite.close();
});

test('a forged preexisting success ledger cannot bypass schema/index verification',async()=>{
  const f=fixture(8);seedLegacy(f.sqlite);f.sqlite.exec(`CREATE TABLE _trail_runtime_schema(version TEXT PRIMARY KEY NOT NULL,applied_at TEXT NOT NULL);
    INSERT INTO _trail_runtime_schema VALUES('${RUNTIME_SCHEMA_VERSION}','synthetic-forged-marker');
    DROP INDEX identification_stages_owner;
    CREATE INDEX identification_stages_owner ON identification_stages(stage);`);
  await assert.rejects(createRuntimeSchemaInitializer()(f.db),RuntimeSchemaError);
  assert.equal(f.sqlite.prepare('SELECT applied_at FROM _trail_runtime_schema').get()?.applied_at,'synthetic-forged-marker');f.sqlite.close();
});

test('unexpected required columns on new tables cannot be declared compatible',async()=>{
  const f=fixture(8);seedLegacy(f.sqlite);f.sqlite.exec("ALTER TABLE member_access ADD COLUMN unexpected TEXT NOT NULL DEFAULT 'requires-client-value'");
  await assert.rejects(createRuntimeSchemaInitializer()(f.db),RuntimeSchemaError);assert.equal(hasLedger(f.sqlite),false);
  assert.equal(f.sqlite.prepare('PRAGMA table_info(member_access)').all().find(c=>c.name==='unexpected')?.notnull,1);f.sqlite.close();
});

test('an unexpected cascade cannot turn the anonymous cost ledger into member-deletable records',async()=>{
  const f=fixture(8);seedLegacy(f.sqlite);f.sqlite.exec(`DROP TABLE ai_reservations;
    CREATE TABLE ai_reservations(id TEXT PRIMARY KEY NOT NULL,day_key TEXT NOT NULL,reserved_micro_usd INTEGER NOT NULL,settled_micro_usd INTEGER,created_at TEXT NOT NULL,settled_at TEXT,FOREIGN KEY(id) REFERENCES users(id) ON DELETE CASCADE);`);
  await assert.rejects(createRuntimeSchemaInitializer()(f.db),RuntimeSchemaError);assert.equal(hasLedger(f.sqlite),false);
  assert.equal(f.sqlite.prepare('PRAGMA foreign_key_list(ai_reservations)').all().length,1);f.sqlite.close();
});

test('an empty or incompatible legacy database is rejected before any additive tables are manufactured',async()=>{
  const f=fixture(-1);await assert.rejects(createRuntimeSchemaInitializer()(f.db),RuntimeSchemaError);
  assert.equal(f.sqlite.prepare("SELECT count(*) AS n FROM sqlite_master WHERE type='table'").get()?.n,0);
  assert.ok(f.trace.every(sql=>/^PRAGMA/.test(sql)));f.sqlite.close();
});

test('runtime-created foreign keys preserve canonical privacy erasure and prevent quota refunds on photo deletion',async()=>{
  const f=fixture();seedLegacy(f.sqlite);await createRuntimeSchemaInitializer()(f.db);
  f.sqlite.exec(`INSERT INTO identification_stages VALUES('photo','owner','simple','{}','2026-10-05');
    INSERT INTO publication_checks VALUES('photo','digest','allowed','2026-10-05');
    INSERT INTO photo_allowance VALUES('photo','owner','2026-10',NULL,'2026-10-05');
    DELETE FROM observations WHERE id='photo';`);
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM identification_stages').get()?.n,0);
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM publication_checks').get()?.n,0);
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM photo_allowance').get()?.n,1);
  f.sqlite.exec("DELETE FROM users WHERE id='owner'");assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM photo_allowance').get()?.n,0);
  f.sqlite.close();
});
