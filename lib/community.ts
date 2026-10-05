import { z } from 'zod';
import {bindings,database,json,digest,randomToken} from './server';
import { getServiceKey } from './account-key';
import {checkPublication} from './publishing-check';
import {canReadPublication,distanceKm,stripJpegMetadata,publicSnapshot} from './social-policy';

type Viewer={id:string;email:string;name:string};
type Row={observation_id:string;owner_id:string;status:string;audience:string;snapshot:string;photo_key:string|null;generation:string;centre_lat:number|null;centre_lon:number|null;radius_km:number|null;reason:string;username:string;liked:number;saved:number;following:number;blocked:number;invited:number;acorns:number;updated_at:string;[key:string]:unknown};
export async function limitAction(key:string,maximum:number,ms:number) {
  const now=Date.now();
  const row=await database().prepare('INSERT INTO auth_attempts(key,count,reset_at) VALUES(?,1,?) ON CONFLICT(key) DO UPDATE SET count=CASE WHEN reset_at<=? THEN 1 ELSE count+1 END,reset_at=CASE WHEN reset_at<=? THEN excluded.reset_at ELSE reset_at END WHERE reset_at<=? OR count<? RETURNING count').bind(key,now+ms,now,now,now,maximum).first();
  return !!row;
}
const uuid=z.string().uuid();
const centre=(u:URL)=>{
  const lat=u.searchParams.has('lat')?Number(u.searchParams.get('lat')):null,lon=u.searchParams.has('lon')?Number(u.searchParams.get('lon')):null;
  if(lat===null&&lon===null)return {lat,lon};
  if(lat===null||lon===null||!Number.isFinite(lat)||!Number.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new Error('Invalid map centre');
  return {lat,lon};
};
async function profile(id:string) {
  await database().prepare('INSERT OR IGNORE INTO profiles(user_id,username,discoverable) VALUES(?,?,0)').bind(id,'trail_'+id.replaceAll('-','').slice(0,12)).run();
  return (await database().prepare('SELECT user_id,username,discoverable,terms_at FROM profiles WHERE user_id=?').bind(id).first<any>())!;
}
function select(viewer:Viewer) {
  return {sql:`SELECT p.*,pr.username,
  (SELECT count(*) FROM acorns a WHERE a.observation_id=p.observation_id) AS acorns,
  EXISTS(SELECT 1 FROM acorns a WHERE a.observation_id=p.observation_id AND a.user_id=?) AS liked,
  EXISTS(SELECT 1 FROM saved_places s WHERE s.observation_id=p.observation_id AND s.user_id=?) AS saved,
  EXISTS(SELECT 1 FROM follows f WHERE f.following_id=p.owner_id AND f.follower_id=?) AS following,
  EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=? AND b.blocked_id=p.owner_id) OR (b.blocked_id=? AND b.blocker_id=p.owner_id)) AS blocked,
  EXISTS(SELECT 1 FROM publication_recipients r WHERE r.observation_id=p.observation_id AND r.generation=p.generation AND r.accepted_user_id=?) AS invited
  FROM publications p JOIN profiles pr ON pr.user_id=p.owner_id`,values:[viewer.id,viewer.id,viewer.id,viewer.id,viewer.id,viewer.id]};
}
async function row(id:string,viewer:Viewer) {
  const q=select(viewer);return database().prepare(q.sql+' WHERE p.observation_id=?').bind(...q.values,id).first<Row>();
}
function readable(p:Row,viewer:Viewer,url:URL){return canReadPublication(p,viewer.id,{blocked:!!p.blocked,invited:!!p.invited,...centre(url)});}
function photoDTO(p:Row,viewer:Viewer,url:URL) {
  const at=centre(url),params=at.lat===null?'':`?lat=${at.lat}&lon=${at.lon}`;
  return {id:p.observation_id,author:{id:p.owner_id,username:p.username},...JSON.parse(p.snapshot),acorns:Number(p.acorns),acorned:!!p.liked,checkLater:!!p.saved,following:!!p.following,audience:p.audience,own:p.owner_id===viewer.id,photoUrl:`/api/social/photos/${p.observation_id}/photo${params}`};
}
export async function decorateOwnRecords(userId:string) {
  const rows=await database().prepare(`SELECT o.data,o.id,p.status,p.audience,p.reason,
  (SELECT count(*) FROM acorns a WHERE a.observation_id=o.id) AS acorns,
  EXISTS(SELECT 1 FROM acorns a WHERE a.observation_id=o.id AND a.user_id=?) AS liked,
  EXISTS(SELECT 1 FROM saved_places s WHERE s.observation_id=o.id AND s.user_id=?) AS saved
  FROM observations o LEFT JOIN publications p ON p.observation_id=o.id WHERE o.user_id=? ORDER BY o.updated_at DESC`).bind(userId,userId,userId).all<any>();
  return rows.results.map(r=>({...JSON.parse(r.data),acorned:!!r.liked,acornCount:Number(r.acorns),checkLater:!!r.saved,publication:{status:r.status||'private',audience:r.audience||null,reason:r.reason||''}}));
}
export async function syncOwnSocial(userId:string,id:string,metadata:{acorned?:boolean;checkLater?:boolean;archived?:boolean}) {
  const statements=[];
  for(const [name,value] of [['acorns',metadata.acorned],['saved_places',metadata.checkLater]] as const)if(value!==undefined)
    statements.push(value?database().prepare(`INSERT OR IGNORE INTO ${name}(observation_id,user_id,created_at) VALUES(?,?,?)`).bind(id,userId,new Date().toISOString()):database().prepare(`DELETE FROM ${name} WHERE observation_id=? AND user_id=?`).bind(id,userId));
  if(metadata.archived)statements.push(database().prepare("UPDATE publications SET status='unpublished',generation=?,updated_at=? WHERE observation_id=? AND owner_id=?").bind(randomToken(),new Date().toISOString(),id,userId));
  if(statements.length)await database().batch(statements);
}
export async function removePublicFiles(userId:string) {
  let cursor:string|undefined;
  do {
    const page=await bindings().BUCKET.list({prefix:`published/${userId}/`,cursor});
    if(page.objects.length)await bindings().BUCKET.delete(page.objects.map(x=>x.key));
    cursor=page.truncated?page.cursor:undefined;
  } while(cursor);
}
const publicationInput=z.object({audience:z.enum(['everyone','local','people']),emails:z.array(z.string().trim().email().max(254).transform(s=>s.toLowerCase())).max(30).default([]),latitude:z.number().min(-90).max(90).nullable().optional(),longitude:z.number().min(-180).max(180).nullable().optional(),radiusKm:z.number().min(.5).max(100).optional(),revision:z.number().int().positive(),agree:z.literal(true)});
async function publish(request:Request,id:string,viewer:Viewer) {
  const input=publicationInput.parse(await request.json());
  if(!await limitAction('publish:'+viewer.id,10,60000))return json({error:'Please wait a minute before publishing more photos.'},429);
  const own=await database().prepare('SELECT data,photo_key FROM observations WHERE id=? AND user_id=?').bind(id,viewer.id).first<{data:string;photo_key:string}>();
  if(!own)return json({error:'Save and upload this photo first.'},404);
  const data=JSON.parse(own.data);
  if(data.archived)return json({error:'Restore this photo from Archive before publishing.'},409);
  if(data.revision!==input.revision)return json({error:'Your photo changed. Sync your journal and try again.'},409);
  if(input.audience==='people'&&!input.emails.length)return json({error:'Add at least one email address.'},400);
  if(input.audience==='local'&&(input.latitude==null||input.longitude==null||!input.radiusKm))return json({error:'Choose the local sharing circle on the map.'},400);
  const old=await row(id,viewer);
  if(old?.status==='removed')return json({error:'This publication was removed after a report. Contact support before publishing it again.'},403);
  const key=await getServiceKey(viewer.id);if(!key)return json({error:'Photo publishing is temporarily unavailable. Your journal stays private.'},503);
  await profile(viewer.id);
  await database().prepare('UPDATE profiles SET terms_at=? WHERE user_id=?').bind(new Date().toISOString(),viewer.id).run();
  const object=await bindings().BUCKET.get(own.photo_key);if(!object)return json({error:'Photo unavailable.'},404);
  const bytes=stripJpegMetadata(new Uint8Array(await object.arrayBuffer())),snapshot=publicSnapshot(data),generation=randomToken(),when=new Date().toISOString();
  await database().prepare(`INSERT INTO publications(observation_id,owner_id,status,audience,snapshot,photo_key,generation,centre_lat,centre_lon,radius_km,reason,created_at,updated_at) VALUES(?,?,'checking',?,?,NULL,?,?,?,?,'',?,?) ON CONFLICT(observation_id) DO UPDATE SET status='checking',audience=excluded.audience,snapshot=excluded.snapshot,generation=excluded.generation,centre_lat=excluded.centre_lat,centre_lon=excluded.centre_lon,radius_km=excluded.radius_km,reason='',updated_at=excluded.updated_at`).bind(id,viewer.id,input.audience,JSON.stringify(snapshot),generation,input.latitude??null,input.longitude??null,input.radiusKm??null,when,when).run();
  try {
    const decision=await checkPublication(bytes,snapshot,key);
    if(!decision.allowed){await database().prepare("UPDATE publications SET status='blocked',reason=?,updated_at=? WHERE observation_id=? AND generation=? AND status='checking'").bind(decision.reason,new Date().toISOString(),id,generation).run();return json({published:false,status:'blocked',reason:decision.reason});}
    const publicKey=`published/${viewer.id}/${id}/${generation}.jpg`;
    await bindings().BUCKET.put(publicKey,bytes,{httpMetadata:{contentType:'image/jpeg'}});
    const invitations=[];
    const writes=[];
    for(const email of [...new Set(input.emails)])if(input.audience==='people'){
      const token=randomToken();invitations.push({email,url:new URL('/invite?token='+token,request.url).toString()});
      writes.push(database().prepare('INSERT INTO publication_recipients(id,observation_id,generation,email,token_hash) VALUES(?,?,?,?,?)').bind(crypto.randomUUID(),id,generation,email,await digest(token)));
    }
    writes.push(database().prepare("UPDATE publications SET status='published',photo_key=?,updated_at=? WHERE observation_id=? AND generation=? AND status='checking'").bind(publicKey,new Date().toISOString(),id,generation));
    const results=await database().batch(writes);
    if(!results.at(-1)?.meta.changes){await bindings().BUCKET.delete(publicKey);return json({error:'Publishing was cancelled. This photo is private.'},409);}
    if(old?.photo_key&&old.photo_key!==publicKey)await bindings().BUCKET.delete(old.photo_key);
    return json({published:true,status:'published',audience:input.audience,invitations});
  } catch(error) {
    await database().prepare("UPDATE publications SET status='blocked',reason='The publishing check could not finish. Your photo stays private.',updated_at=? WHERE observation_id=? AND generation=? AND status='checking'").bind(new Date().toISOString(),id,generation).run();
    throw error;
  }
}
export async function community(request:Request,path:string[],viewer:Viewer):Promise<Response> {
  const url=new URL(request.url),method=request.method,part=path[0],id=path[1],action=path[2];
  if(method!=='GET'&&request.headers.get('origin')!==url.origin)return json({error:'Open My Trail Log to make this change.'},403);
  const me=await profile(viewer.id);
  if(part==='me'){
    if(method==='GET')return json({profile:{id:viewer.id,username:me.username,discoverable:!!me.discoverable,termsAccepted:!!me.terms_at,isModerator:viewer.id===bindings().COMMUNITY_ADMIN_USER_ID}});
    if(method==='PUT'){
      const input=z.object({username:z.string().trim().regex(/^[a-zA-Z][a-zA-Z0-9_]{2,24}$/).transform(s=>s.toLowerCase()),discoverable:z.boolean()}).parse(await request.json());
      const taken=await database().prepare('SELECT user_id FROM profiles WHERE username=? AND user_id<>?').bind(input.username,viewer.id).first();if(taken)return json({error:'That username is taken. Try another.'},409);
      await database().prepare('UPDATE profiles SET username=?,discoverable=? WHERE user_id=?').bind(input.username,input.discoverable?1:0,viewer.id).run();return json({ok:true});
    }
  }
  if(part==='contacts'&&method==='POST'){
    if(!await limitAction('contacts:'+viewer.id,10,60000))return json({error:'Please wait a minute before searching again.'},429);
    const input=z.object({emails:z.array(z.string().trim().email().transform(s=>s.toLowerCase())).min(1).max(30)}).parse(await request.json());
    const found=[];
    for(const email of [...new Set(input.emails)]){
      const person=await database().prepare(`SELECT pr.user_id AS id,pr.username,EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=? AND f.following_id=pr.user_id) AS following FROM profiles pr JOIN users u ON u.id=pr.user_id WHERE u.email=? AND pr.discoverable=1 AND pr.user_id<>? AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=? AND b.blocked_id=pr.user_id) OR (b.blocked_id=? AND b.blocker_id=pr.user_id))`).bind(viewer.id,email,viewer.id,viewer.id,viewer.id).first();if(person)found.push(person);
    }
    return json({users:found});
  }
  if(part==='users'){
    if(!id&&method==='GET'){
      const query=(url.searchParams.get('query')||'').trim().toLowerCase().slice(0,25),following=url.searchParams.get('following')==='true';
      const list=await database().prepare(`SELECT pr.user_id AS id,pr.username,EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=? AND f.following_id=pr.user_id) AS following FROM profiles pr WHERE pr.user_id<>? AND instr(pr.username,?)>0 AND (?=0 OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=? AND f.following_id=pr.user_id)) AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=? AND b.blocked_id=pr.user_id) OR (b.blocked_id=? AND b.blocker_id=pr.user_id)) ORDER BY pr.username LIMIT 60`).bind(viewer.id,viewer.id,query,following?1:0,viewer.id,viewer.id,viewer.id).all();return json({users:list.results});
    }
    if(!uuid.safeParse(id).success)return json({error:'User unavailable.'},404);
    const target=await database().prepare('SELECT user_id AS id,username FROM profiles WHERE user_id=?').bind(id).first();if(!target)return json({error:'User unavailable.'},404);
    if(action==='follow'&&method==='PUT'){
      const input=z.object({following:z.boolean()}).parse(await request.json());if(id===viewer.id)return json({error:'This is your journal.'},400);
      const blocked=await database().prepare('SELECT 1 FROM blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)').bind(viewer.id,id,id,viewer.id).first();if(blocked)return json({error:'This user is unavailable.'},404);
      await (input.following?database().prepare('INSERT OR IGNORE INTO follows(follower_id,following_id) VALUES(?,?)').bind(viewer.id,id):database().prepare('DELETE FROM follows WHERE follower_id=? AND following_id=?').bind(viewer.id,id)).run();return json({following:input.following});
    }
    if(action==='block'&&method==='PUT'){
      const input=z.object({blocked:z.boolean()}).parse(await request.json());if(id===viewer.id)return json({error:'This is your journal.'},400);
      if(input.blocked)await database().batch([database().prepare('INSERT OR IGNORE INTO blocks(blocker_id,blocked_id) VALUES(?,?)').bind(viewer.id,id),database().prepare('DELETE FROM follows WHERE (follower_id=? AND following_id=?) OR (follower_id=? AND following_id=?)').bind(viewer.id,id,id,viewer.id)]);
      else await database().prepare('DELETE FROM blocks WHERE blocker_id=? AND blocked_id=?').bind(viewer.id,id).run();return json({blocked:input.blocked});
    }
    if(method==='GET')return json({user:target});
  }
  if(part==='blocked'&&method==='GET')return json({users:(await database().prepare('SELECT p.user_id AS id,p.username FROM blocks b JOIN profiles p ON p.user_id=b.blocked_id WHERE b.blocker_id=?').bind(viewer.id).all()).results});
  if(part==='invite'&&method==='POST'){
    if(!await limitAction('invite:'+viewer.id,20,60000))return json({error:'Try again shortly.'},429);
    const input=z.object({token:z.string().regex(/^[a-f0-9]{64}$/)}).parse(await request.json());
    const invite=await database().prepare("SELECT r.* FROM publication_recipients r JOIN publications p ON p.observation_id=r.observation_id AND p.generation=r.generation WHERE r.token_hash=? AND p.status='published' AND p.audience='people'").bind(await digest(input.token)).first<any>();
    if(!invite||invite.email!==viewer.email.toLowerCase()||(invite.accepted_user_id&&invite.accepted_user_id!==viewer.id))return json({error:'This invite is unavailable for this account. Sign in with the email it was sent to.'},404);
    await database().prepare('UPDATE publication_recipients SET accepted_user_id=? WHERE id=? AND (accepted_user_id IS NULL OR accepted_user_id=?)').bind(viewer.id,invite.id,viewer.id).run();return json({ok:true,id:invite.observation_id});
  }
  if(part==='feed'&&method==='GET'){
    const at=centre(url),scope=url.searchParams.get('scope')||'nearby',radius=Math.min(100,Math.max(.5,Number(url.searchParams.get('radius')||10))),author=url.searchParams.get('author'),offset=Math.max(0,Math.min(10000,Number(url.searchParams.get('offset')||0)));
    if(!Number.isFinite(radius)||!Number.isInteger(offset))return json({error:'Invalid map filter.'},400);
    const q=select(viewer);let rows=(await database().prepare(q.sql+" WHERE p.status='published' ORDER BY p.updated_at DESC").bind(...q.values).all<Row>()).results;
    rows=rows.filter(p=>readable(p,viewer,url)&&(!author||p.owner_id===author)&&(scope!=='following'||!!p.following)&&(scope!=='saved'||!!p.saved)&&(scope!=='invited'||!!p.invited));
    if(at.lat!==null&&at.lon!==null&&!author&&scope==='nearby')rows=rows.filter(p=>{const s=JSON.parse(p.snapshot);return s.latitude!==null&&s.longitude!==null&&distanceKm(at.lat!,at.lon!,s.latitude,s.longitude)<=radius;});
    const category=url.searchParams.get('category');if(category&&category!=='all')rows=rows.filter(p=>JSON.parse(p.snapshot).category===category);
    if(url.searchParams.get('sort')==='acorns')rows.sort((a,b)=>Number(b.acorns)-Number(a.acorns)||b.updated_at.localeCompare(a.updated_at));
    return json({photos:rows.slice(offset,offset+100).map(p=>photoDTO(p,viewer,url)),hasMore:rows.length>offset+100,nextOffset:offset+100});
  }
  if(part==='published'&&method==='GET'){
    const q=select(viewer);const rows=(await database().prepare(q.sql+' WHERE p.owner_id=? ORDER BY p.updated_at DESC').bind(...q.values,viewer.id).all<Row>()).results;
    return json({photos:rows.map(p=>({...photoDTO(p,viewer,url),status:p.status,reason:p.reason,radiusKm:p.radius_km,centreLat:p.centre_lat,centreLon:p.centre_lon}))});
  }
  if(part==='moderation')return moderation(request,path,viewer);
  if(part==='photos'&&uuid.safeParse(id).success){
    if(action==='invitations' && (method==='POST'||method==='GET')) {
      const p=await row(id,viewer);
      if(!p||p.owner_id!==viewer.id||p.status!=='published'||p.audience!=='people')return json({error:'Private invitations are unavailable for this photo.'},404);
      if(method==='GET')return json({recipients:(await database().prepare('SELECT email,accepted_user_id IS NOT NULL AS accepted FROM publication_recipients WHERE observation_id=? AND generation=?').bind(id,p.generation).all()).results});
      if(!await limitAction('invitations:'+viewer.id,10,60000))return json({error:'Please wait a minute before creating more invitations.'},429);
      const input=z.object({emails:z.array(z.string().trim().email().transform(s=>s.toLowerCase())).min(1).max(30)}).parse(await request.json());
      const invitations=[];
      for(const email of [...new Set(input.emails)]) {
        const token=randomToken(),hash=await digest(token);
        const existing=await database().prepare('SELECT id FROM publication_recipients WHERE observation_id=? AND generation=? AND email=?').bind(id,p.generation,email).first<{id:string}>();
        if(existing)await database().prepare('UPDATE publication_recipients SET token_hash=? WHERE id=?').bind(hash,existing.id).run();
        else await database().prepare('INSERT INTO publication_recipients(id,observation_id,generation,email,token_hash) VALUES(?,?,?,?,?)').bind(crypto.randomUUID(),id,p.generation,email,hash).run();
        invitations.push({email,url:new URL('/invite?token='+token,request.url).toString()});
      }
      return json({invitations});
    }
    if(action==='publish'&&method==='POST')return publish(request,id,viewer);
    if(action==='publish'&&method==='DELETE'){
      const p=await row(id,viewer);if(!p||p.owner_id!==viewer.id)return json({error:'Photo unavailable.'},404);
      await database().prepare("UPDATE publications SET status='unpublished',generation=?,updated_at=? WHERE observation_id=? AND owner_id=?").bind(randomToken(),new Date().toISOString(),id,viewer.id).run();
      if(p.photo_key)await bindings().BUCKET.delete(p.photo_key);return json({ok:true,status:'unpublished'});
    }
    const p=await row(id,viewer);const own=await database().prepare('SELECT id FROM observations WHERE id=? AND user_id=?').bind(id,viewer.id).first();
    const canRead=!!p&&readable(p,viewer,url);
    if(['acorn','save'].includes(action)&&method==='PUT'){
      if(!own&&!canRead)return json({error:'This photo is no longer shared with you.'},404);
      const input=z.object({active:z.boolean()}).parse(await request.json());const table=action==='acorn'?'acorns':'saved_places';
      await (input.active?database().prepare(`INSERT OR IGNORE INTO ${table}(observation_id,user_id,created_at) VALUES(?,?,?)`).bind(id,viewer.id,new Date().toISOString()):database().prepare(`DELETE FROM ${table} WHERE observation_id=? AND user_id=?`).bind(id,viewer.id)).run();
      const count=await database().prepare('SELECT count(*) AS n FROM acorns WHERE observation_id=?').bind(id).first<{n:number}>();return json({active:input.active,acorns:count?.n||0});
    }
    if(!p||!canRead)return json({error:'This photo is no longer shared with you.'},404);
    if(action==='photo'&&method==='GET'){
      const object=p.photo_key?await bindings().BUCKET.get(p.photo_key):null;if(!object)return json({error:'Photo unavailable.'},404);
      return new Response(object.body,{headers:{'Content-Type':'image/jpeg','Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff'}});
    }
    if(!action&&method==='GET')return json({photo:photoDTO(p,viewer,url)});
    if(action==='report'&&method==='POST'){
      if(p.owner_id===viewer.id)return json({error:'Use Unpublish to remove your own photo.'},400);
      if(!await limitAction('report:'+viewer.id,10,3600000))return json({error:'You have sent several reports. Please try later.'},429);
      const input=z.object({reason:z.enum(['people','unsafe','personal_information','spam','wrong_location','other']),detail:z.string().max(500).default('')}).parse(await request.json());
      await database().batch([database().prepare('INSERT INTO reports(id,observation_id,reporter_id,reason,detail,status,created_at) VALUES(?,?,?,?,?,\'open\',?)').bind(crypto.randomUUID(),id,viewer.id,input.reason,input.detail,new Date().toISOString()),database().prepare("UPDATE publications SET status='reported',reason='Hidden while a report is reviewed.',updated_at=? WHERE observation_id=? AND generation=? AND status='published'").bind(new Date().toISOString(),id,p.generation)]);
      return json({ok:true,message:'Reported. The photo is hidden while it is reviewed.'});
    }
  }
  return json({error:'Not found'},404);
}
async function moderation(request:Request,path:string[],viewer:Viewer){
  if(viewer.id!==bindings().COMMUNITY_ADMIN_USER_ID)return json({error:'Not found'},404);
  const id=path[1];
  if(!id&&request.method==='GET')return json({reports:(await database().prepare("SELECT r.id,r.observation_id,r.reason,r.detail,r.created_at,pr.username,p.snapshot FROM reports r JOIN publications p ON p.observation_id=r.observation_id JOIN profiles pr ON pr.user_id=p.owner_id WHERE r.status='open' AND p.status='reported' ORDER BY r.created_at DESC LIMIT 100").all()).results});
  if(!uuid.safeParse(id).success)return json({error:'Not found'},404);
  const p=await row(id,viewer);if(!p||p.status!=='reported')return json({error:'This report is no longer pending.'},404);
  if(path[2]==='photo'&&request.method==='GET'){
    const object=p.photo_key?await bindings().BUCKET.get(p.photo_key):null;if(!object)return json({error:'Photo unavailable.'},404);
    return new Response(object.body,{headers:{'Content-Type':'image/jpeg','Cache-Control':'private, no-store'}});
  }
  if(request.method!=='POST')return json({error:'Method not allowed'},405);
  const input=z.object({action:z.enum(['remove','review'])}).parse(await request.json());
  let status='removed',reason='Removed after review.';
  if(input.action==='review'){
    const key=await getServiceKey(viewer.id);const object=p.photo_key?await bindings().BUCKET.get(p.photo_key):null;
    if(!key||!object)return json({error:'Review service unavailable. The photo stays hidden.'},503);
    const decision=await checkPublication(new Uint8Array(await object.arrayBuffer()),JSON.parse(p.snapshot),key);
    status=decision.allowed?'published':'removed';reason=decision.reason;
  }
  await database().batch([database().prepare("UPDATE publications SET status=?,reason=?,updated_at=? WHERE observation_id=? AND generation=? AND status='reported'").bind(status,reason,new Date().toISOString(),id,p.generation),database().prepare("UPDATE reports SET status='reviewed' WHERE observation_id=? AND status='open'").bind(id)]);
  return json({status,reason});
}
