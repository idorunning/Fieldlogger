import {z} from 'zod';
import {bindings,database,json,hashPassword,constantTimeEqual,digest} from './server';
import {billingStatus} from './billing';
import {removePublicFiles,limitAction} from './community';
type User={id:string;email:string;name:string};
export async function accessFor(user:User){
  const root=bindings().COMMUNITY_ADMIN_USER_ID===user.id&&user.email.toLowerCase()==='ntracey@gmail.com';
  const row=await database().prepare('SELECT role,status FROM member_access WHERE user_id=?').bind(user.id).first<{role:string;status:string}>();
  return {role:root?'admin':row?.role||'member',status:root?'active':row?.status||'active',owner:root};
}
const memberSelect=`SELECT u.id,u.email,u.name,u.created_at AS createdAt,p.username,COALESCE(a.role,'member') AS role,COALESCE(a.status,'active') AS status,(SELECT COUNT(*) FROM observations o WHERE o.user_id=u.id) AS photoCount FROM users u LEFT JOIN profiles p ON p.user_id=u.id LEFT JOIN member_access a ON a.user_id=u.id`;
async function displayMember(m:any){
  if(m.id===bindings().COMMUNITY_ADMIN_USER_ID&&String(m.email).toLowerCase()==='ntracey@gmail.com'){m.role='admin';m.status='active';m.owner=true;}
  const billing=await billingStatus(m.id,false);return {...m,plan:billing.plan,subscriptionStatus:billing.status};
}
async function reauthenticate(request:Request,user:User,input:{password:string}){
  if(request.headers.get('origin')!==new URL(request.url).origin)return false;
  const ipHash=await digest(request.headers.get('cf-connecting-ip')||'local');
  if(!await limitAction('admin-reauth:'+user.id+':'+ipHash,8,900000))return false;
  const u=await database().prepare('SELECT password_hash,salt FROM users WHERE id=?').bind(user.id).first<{password_hash:string;salt:string}>();
  return !!u&&constantTimeEqual(await hashPassword(input.password,u.salt),u.password_hash);
}
export async function adminRoute(request:Request,path:string[],user:User){
  const access=await accessFor(user);
  if(access.role!=='admin'||access.status!=='active')return json({error:'Administrator access is required.'},403);
  if(path[0]==='stats'&&request.method==='GET'){
    const root=bindings().COMMUNITY_ADMIN_USER_ID||'';
    const rows=await database().batch([
      database().prepare('SELECT COUNT(*) AS n FROM users'),
      database().prepare("SELECT COUNT(*) AS n FROM users u LEFT JOIN member_access a ON a.user_id=u.id WHERE (a.role='admin' OR u.id=?) AND COALESCE(a.status,'active')='active'").bind(root),
      database().prepare("SELECT COUNT(*) AS n FROM member_access WHERE status='suspended'"),
      database().prepare('SELECT COUNT(*) AS n FROM observations'),
      database().prepare("SELECT COUNT(*) AS n FROM publications WHERE status='published'"),
      database().prepare("SELECT COUNT(DISTINCT user_id) AS n FROM subscriptions WHERE acknowledged=1 AND checked_at>=? AND status IN ('SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_CANCELED','SUBSCRIPTION_STATE_IN_GRACE_PERIOD') AND expires_at>?").bind(Date.now()-15*60*1000,new Date().toISOString()),
    ]);
    return json(Object.fromEntries(['users','admins','suspended','observations','publications','activeSubscriptions'].map((k,i)=>[k,(rows[i].results[0] as {n:number}).n])));
  }
  if(path[0]!=='members')return json({error:'Not found'},404);
  const id=path[1],action=path[2];
  if(!id&&request.method==='GET'){
    const u=new URL(request.url),query=(u.searchParams.get('query')||'').trim().toLowerCase().slice(0,100),cursor=u.searchParams.get('cursor')||'';
    if(cursor&&!z.string().uuid().safeParse(cursor).success)return json({error:'Invalid page'},400);
    const result=await database().prepare(memberSelect+" WHERE u.id>? AND (?='' OR instr(lower(u.email||' '||u.name||' '||COALESCE(p.username,'')),?)>0) ORDER BY u.id LIMIT 51").bind(cursor,query,query).all();
    const members=await Promise.all(result.results.slice(0,50).map(displayMember));return json({members,nextCursor:result.results.length>50?members.at(-1)?.id:null});
  }
  if(!z.string().uuid().safeParse(id).success)return json({error:'Member unavailable'},404);
  const target=await database().prepare(memberSelect+' WHERE u.id=?').bind(id).first<any>();if(!target)return json({error:'Member unavailable'},404);
  if(!action&&request.method==='GET'){
    const billing=await billingStatus(id,false),audit=(await database().prepare('SELECT action,created_at AS createdAt,actor_id AS actorId FROM admin_audit WHERE target_id=? ORDER BY created_at DESC LIMIT 30').bind(id).all()).results;
    return json({member:await displayMember(target),billing:{plan:billing.plan,status:billing.status,expiresAt:billing.expiresAt,verificationDelayed:billing.verificationDelayed},usage:{used:billing.used,limit:billing.limit,periodStart:billing.periodStart,periodEnd:billing.periodEnd},audit});
  }
  if(request.method!=='POST'||!['role','status'].includes(action))return json({error:'Not found'},404);
  const input=z.object({password:z.string().min(8).max(256),role:z.enum(['admin','member']).optional(),status:z.enum(['active','suspended']).optional()}).strict().parse(await request.json());
  if(!await reauthenticate(request,user,input))return json({error:'Re-enter your administrator password to make this change.'},401);
  if(id===bindings().COMMUNITY_ADMIN_USER_ID)return json({error:'The owner account cannot be demoted or suspended.'},409);
  if(id===user.id)return json({error:'Ask another administrator to change your own role or status.'},409);
  const role=action==='role'?input.role:target.role,status=action==='status'?input.status:target.status;
  if(!role||!status)return json({error:'Choose a valid role or status.'},400);
  const now=new Date().toISOString();
  const result=await database().batch([
    database().prepare(`INSERT INTO member_access(user_id,role,status,updated_at) SELECT ?,?,?,? WHERE ?=? OR EXISTS(SELECT 1 FROM member_access WHERE user_id=? AND role='admin' AND status='active') ON CONFLICT(user_id) DO UPDATE SET ${action==='role'?'role=excluded.role':'status=excluded.status'},updated_at=excluded.updated_at`).bind(id,role,status,now,user.id,bindings().COMMUNITY_ADMIN_USER_ID||'',user.id),
    database().prepare('INSERT INTO admin_audit(id,actor_id,target_id,action,created_at) SELECT ?,?,?,?,? WHERE changes()>0').bind(crypto.randomUUID(),user.id,id,action+':'+(action==='role'?role:status),now),
  ]);
  if(!result[0].meta.changes)return json({error:'Your administrator access changed. Please refresh.'},403);
  const updated=await database().prepare(memberSelect+' WHERE u.id=?').bind(id).first<any>();
  if(updated.status==='suspended'){
    // Immediately hide their community photos. Private journal/export remain available.
    await database().prepare("UPDATE publications SET status=CASE WHEN status='removed' THEN status ELSE 'unpublished' END,generation=?,updated_at=? WHERE owner_id=?").bind(crypto.randomUUID(),new Date().toISOString(),id).run();
    await removePublicFiles(id);
  }
  return json({ok:true,member:await displayMember(updated)});
}
