import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import {z} from 'zod';

const owner='45bccbf6-6db0-491b-8aa8-dcf6c774ff3c',secondary='bb42fb87-e14b-486b-ab89-cec7cd8c8c92',target='0f923f0f-c247-4c99-9773-b897f91cac0b';
const migrations=['0000_brainy_bullseye','0001_strange_virginia_dare','0002_absent_vision','0003_tidy_firedrake','0004_pretty_doctor_spectrum','0005_spotty_rumiko_fujikawa','0006_freezing_mach_iv','0007_gifted_nomad','0008_sturdy_quasar'];
// Run the genuine handler with an isolated Workers binding adapter. Its actual
// SQL runs against SQLite; only password/provider/network dependencies are
// replaced, so a deletion can commit after reauthentication but before batch.
function fixture(){
  const sqlite=new DatabaseSync(':memory:');sqlite.exec('PRAGMA foreign_keys=ON');
  for(const migration of migrations)sqlite.exec(readFileSync(new URL('../drizzle/'+migration+'.sql',import.meta.url),'utf8'));
  for(const [id,email] of [[owner,'ntracey@gmail.com'],[secondary,'secondary@example.invalid'],[target,'target@example.invalid']])sqlite.prepare('INSERT INTO users VALUES(?,?,?,?,?,?)').run(id,email,'Synthetic Member','synthetic-hash','synthetic-salt','2026-10-05');
  sqlite.prepare('INSERT INTO member_access VALUES(?,?,?,?)').run(secondary,'admin','active','2026-10-05');
  let beforeWrite:(()=>void)|undefined;
  class Prepared {
    values:any[]=[];constructor(readonly sql:string){}
    bind(...values:any[]){this.values=values;return this;}
    execute(){const statement=sqlite.prepare(this.sql);if(/^SELECT/i.test(this.sql))return {success:true,results:statement.all(...this.values),meta:{changes:0}};const result=statement.run(...this.values);return {success:true,results:[],meta:{changes:Number(result.changes)}};}
    async all(){return this.execute();}async first(){return this.execute().results[0]||null;}async run(){return this.execute();}
  }
  const db={prepare:(sql:string)=>new Prepared(sql),batch:async(statements:Prepared[])=>{
    beforeWrite?.();beforeWrite=undefined;sqlite.exec('BEGIN IMMEDIATE');try{const results=statements.map(s=>s.execute());sqlite.exec('COMMIT');return results;}catch(error){sqlite.exec('ROLLBACK');throw error;}
  }};
  const server={bindings:()=>({COMMUNITY_ADMIN_USER_ID:owner}),database:()=>db,json:(data:unknown,status=200)=>Response.json(data,{status}),hashPassword:async()=> 'synthetic-hash',constantTimeEqual:(a:string,b:string)=>a===b,digest:async()=> 'synthetic-ip'};
  const exports:any={};
  const source=ts.transpileModule(readFileSync(new URL('../lib/admin.ts',import.meta.url),'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText;
  const context=vm.createContext({exports,crypto,URL,Response,require:(name:string)=>{
    if(name==='zod')return {z};if(name==='./server')return server;
    if(name==='./billing')return {billingStatus:async()=>({plan:'free',status:'free'})};
    if(name==='./community')return {limitAction:async()=>true,removePublicFiles:async()=>undefined};
    throw new Error('Unexpected test import '+name);
  }});vm.runInContext(source,context,{filename:'admin.ts'});
  async function change(action:'role'|'status',actor=owner,value=action==='role'?'admin':'suspended',email=actor===owner?'ntracey@gmail.com':'secondary@example.invalid'){
    const request=new Request('https://test.invalid/api/admin/members/'+target+'/'+action,{method:'POST',headers:{origin:'https://test.invalid','content-type':'application/json'},body:JSON.stringify({password:'synthetic-password',[action]:value})});
    return await exports.adminRoute(request,['members',target,action],{id:actor,email,name:'Synthetic Actor'}) as Response;
  }
  return {sqlite,change,beforeWrite(callback:()=>void){beforeWrite=callback;}};
}
function targetAccess(sqlite:DatabaseSync){return sqlite.prepare('SELECT role,status FROM member_access WHERE user_id=?').get(target);}

test('deleted pinned owner cannot grant a role or record an audit after an already-successful reauthentication',async()=>{
  const f=fixture();f.beforeWrite(()=>f.sqlite.prepare('DELETE FROM users WHERE id=?').run(owner));
  assert.equal((await f.change('role')).status,403);assert.equal(targetAccess(f.sqlite),undefined);
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM admin_audit').get()?.n,0);f.sqlite.close();
});

test('deleted pinned owner cannot suspend a member after successful reauthentication',async()=>{
  const f=fixture();f.sqlite.prepare('INSERT INTO member_access VALUES(?,?,?,?)').run(target,'member','active','existing');
  f.beforeWrite(()=>f.sqlite.prepare('DELETE FROM users WHERE id=?').run(owner));
  assert.equal((await f.change('status')).status,403);assert.deepEqual({...targetAccess(f.sqlite)},{role:'member',status:'active'});
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM admin_audit').get()?.n,0);f.sqlite.close();
});

test('a current pinned owner retains access without requiring a delegated member-access row',async()=>{
  const f=fixture();assert.equal((await f.change('role')).status,200);assert.deepEqual({...targetAccess(f.sqlite)},{role:'admin',status:'active'});
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM admin_audit').get()?.n,1);f.sqlite.close();
});

test('final owner authorisation uses the current pinned email even if the request cached its former value',async()=>{
  const f=fixture();f.beforeWrite(()=>f.sqlite.prepare('UPDATE users SET email=? WHERE id=?').run('changed@example.invalid',owner));
  assert.equal((await f.change('role')).status,403);assert.equal(targetAccess(f.sqlite),undefined);
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM admin_audit').get()?.n,0);f.sqlite.close();
});

test('a new account reusing the deleted owner email receives no owner privilege',async()=>{
  const f=fixture(),replacement='fefcd366-e293-4cde-b88e-cb6296747f79';
  f.sqlite.prepare('DELETE FROM users WHERE id=?').run(owner);
  f.sqlite.prepare('INSERT INTO users VALUES(?,?,?,?,?,?)').run(replacement,'ntracey@gmail.com','Replacement','synthetic-hash','synthetic-salt','2026-10-05');
  assert.equal((await f.change('role',replacement,'admin','ntracey@gmail.com')).status,403);assert.equal(targetAccess(f.sqlite),undefined);
  assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM admin_audit').get()?.n,0);f.sqlite.close();
});

test('a deleted or suspended delegated administrator cannot use a previously authenticated request',async()=>{
  for(const mutation of ['delete','suspend'] as const){const f=fixture();f.beforeWrite(()=>mutation==='delete'?f.sqlite.prepare('DELETE FROM users WHERE id=?').run(secondary):f.sqlite.prepare("UPDATE member_access SET status='suspended' WHERE user_id=?").run(secondary));
    assert.equal((await f.change('role',secondary)).status,403);assert.equal(targetAccess(f.sqlite),undefined);assert.equal(f.sqlite.prepare('SELECT count(*) AS n FROM admin_audit').get()?.n,0);f.sqlite.close();}
});

test('a delegated active administrator may change only the requested field and creates one corresponding audit',async()=>{
  const f=fixture();f.sqlite.prepare('INSERT INTO member_access VALUES(?,?,?,?)').run(target,'member','suspended','existing');
  assert.equal((await f.change('role',secondary)).status,200);assert.deepEqual({...targetAccess(f.sqlite)},{role:'admin',status:'suspended'});
  const row=f.sqlite.prepare('SELECT actor_id,target_id,action FROM admin_audit').get();assert.deepEqual({...row},{actor_id:secondary,target_id:target,action:'role:admin'});f.sqlite.close();
});
