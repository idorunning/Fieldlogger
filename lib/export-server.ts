import {bindings,database,json} from './server';
import {billingStatus} from './billing';
import {avatarObjectKey,avatarRequestId,generatedAvatar,readAvatarDescriptor} from './avatar-policy';
import {readableChunks,textChunks,zipStream,type ZipEntry} from './zip-stream';

type Viewer={id:string;email:string;name:string};
type ObservationRow={id:string;data:string;photo_key:string;updated_at:string};
const encoder=new TextEncoder();
const encode=(value:unknown)=>encoder.encode(JSON.stringify(value));
export function exportedPhotoName(id:string,data:{localDate?:unknown;archived?:unknown}) {
  if(!avatarRequestId.safeParse(id).success)throw new Error('Invalid photo reference.');
  // The filename stays stable even if sync edits a date or archive status while
  // the download streams. Date and archival state remain in journal metadata.
  void data;
  return `photos/${id}.jpg`;
}
async function* observations(userId:string):AsyncGenerator<ObservationRow> {
  let after='';
  for(;;) {
    const rows=(await database().prepare('SELECT id,data,photo_key,updated_at FROM observations WHERE user_id=? AND id>? ORDER BY id LIMIT 100').bind(userId,after).all<ObservationRow>()).results;
    if(!rows.length)return;
    for(const row of rows)yield row;
    after=rows[rows.length-1].id;
  }
}
// These are fixed server projections, never a client-supplied table/query. Raw
// purchase tokens, password material, session tokens and invitation tokens are
// deliberately excluded from a member's data export.
const ownTables:readonly {key:string;table:string;fields:string;owner?:string;where?:string}[]=[
  {key:'achievements',table:'achievement_unlocks',fields:'badge,earned_at',owner:'user_id'},
  {key:'publications',table:'publications',fields:'observation_id,status,audience,snapshot,centre_lat,centre_lon,radius_km,reason,created_at,updated_at',owner:'owner_id'},
  {key:'sharingRecipients',table:'publication_recipients',fields:'observation_id,email,accepted_user_id',where:'observation_id IN (SELECT observation_id FROM publications WHERE owner_id=?)'},
  {key:'acornsGiven',table:'acorns',fields:'observation_id,created_at',owner:'user_id'},
  {key:'checkOutLater',table:'saved_places',fields:'observation_id,created_at',owner:'user_id'},
  {key:'following',table:'follows',fields:'following_id',owner:'follower_id'},
  {key:'followers',table:'follows',fields:'follower_id',owner:'following_id'},
  {key:'blockedUsers',table:'blocks',fields:'blocked_id',owner:'blocker_id'},
  {key:'reportsMade',table:'reports',fields:'id,observation_id,reason,detail,status,created_at',owner:'reporter_id'},
  {key:'subscriptionHistory',table:'subscriptions',fields:'product_id,plan,status,starts_at,expires_at,acknowledged',owner:'user_id'},
  {key:'photoAllowanceHistory',table:'photo_allowance',fields:'observation_id,period,bonus_year,created_at',owner:'user_id'},
  {key:'avatarAllowanceHistory',table:'avatar_generations',fields:'period,used',owner:'user_id'},
  {key:'aiProcessingHistory',table:'ai_usage',fields:'observation_id,period,purpose,model,input_tokens,output_tokens,created_at',owner:'user_id'},
  {key:'identificationStageHistory',table:'identification_stages',fields:'observation_id,stage,data,created_at',owner:'user_id'},
  {key:'memberAccess',table:'member_access',fields:'role,status,updated_at',owner:'user_id'},
  {key:'adminActionsOnAccount',table:'admin_audit',fields:'action,created_at',owner:'target_id'},
];

async function* accountJson(user:Viewer,profile:Record<string,unknown>|null,createdAt:string|null,billing:Awaited<ReturnType<typeof billingStatus>>,exportedAt:string) {
  yield encoder.encode('{"format":"my-trail-log-export-v1","exportedAt":');yield encode(exportedAt);
  yield encoder.encode(',"account":');yield encode({...user,createdAt});
  yield encoder.encode(',"profile":');yield encode(profile?{username:profile.username,discoverable:!!profile.discoverable,termsAcceptedAt:profile.terms_at,avatar:readAvatarDescriptor(profile.avatar,user.id,true)}:null);
  yield encoder.encode(',"membership":');yield encode(billing);
  yield encoder.encode(',"observations":[');let first=true;
  for await(const row of observations(user.id)) {
    const data=JSON.parse(row.data);
    if(!first)yield encoder.encode(',');first=false;
    yield encode({...data,id:row.id,photoFile:exportedPhotoName(row.id,data),serverUpdatedAt:row.updated_at});
  }
  yield encoder.encode(']');
  for(const table of ownTables) {
    yield encoder.encode(',');yield encode(table.key);yield encoder.encode(':[');let after=0,firstRow=true;
    for(;;) {
      const page=(await database().prepare(`SELECT ${table.fields},rowid AS export_rowid FROM ${table.table} WHERE (${table.where||table.owner+'=?'}) AND rowid>? ORDER BY rowid LIMIT 100`).bind(user.id,after).all<Record<string,unknown>>()).results;
      if(!page.length)break;
      for(const row of page){after=Number(row.export_rowid);const {export_rowid,...record}=row;void export_rowid;if(!firstRow)yield encoder.encode(',');firstRow=false;yield encode(record);}
    }
    yield encoder.encode(']');
  }
  yield encoder.encode('}');
}

export async function exportJournal(request:Request,user:Viewer) {
  if(request.method!=='GET')return json({error:'Method not allowed.'},405);
  const db=database(),account=await db.prepare('SELECT created_at FROM users WHERE id=?').bind(user.id).first<{created_at:string}>();
  if(!account)return json({error:'Account unavailable.'},404);
  const profile=await db.prepare('SELECT username,discoverable,terms_at,avatar FROM profiles WHERE user_id=?').bind(user.id).first<Record<string,unknown>>();
  // This is information only. Free, cancelled and over-quota accounts are all
  // allowed to export, without purchase verification or a paid entitlement.
  const billing=await billingStatus(user.id,false),exportedAt=new Date().toISOString(),avatar=generatedAvatar(profile?.avatar);
  const unavailable:{id:string;photoFile:string;reason:string}[]=[];
  async function* entries():AsyncGenerator<ZipEntry> {
    yield {name:'README.txt',chunks:textChunks('My Trail Log — complete cloud journal export\n\nYour private originals, archived photos, journal metadata, achievements, profile and membership history are included. Photo filenames use discovery IDs; journal.json links each filename to its date, location, notes and identification. Nothing is charged to your photo allowance. Photos still waiting to sync on a device are not yet in cloud storage; sync that device and export again to include them. Your current illustrated avatar is included when available. Passwords, login tokens, billing credentials and other members’ private account details are excluded.\n\nThis archive contains your exact saved GPS locations and private notes. Share it with care.\n')};
    yield {name:'journal.json',chunks:accountJson(user,profile,account!.created_at,billing,exportedAt)};
    if(avatar) {
      const object=await bindings().BUCKET.get(avatarObjectKey(user.id,avatar.revision));
      if(object)yield {name:'avatar.png',chunks:readableChunks(object.body)};
    }
    for await(const row of observations(user.id)) {
      const data=JSON.parse(row.data),name=exportedPhotoName(row.id,data);
      // Even a damaged DB row cannot redirect this export to another member's
      // object. Original uploads have exactly this owner/UUID-derived key.
      if(row.photo_key!==`${user.id}/${row.id}.jpg`){unavailable.push({id:row.id,photoFile:name,reason:'Invalid stored photo reference.'});continue;}
      const object=await bindings().BUCKET.get(row.photo_key);
      if(!object){unavailable.push({id:row.id,photoFile:name,reason:'The original is no longer available in cloud storage.'});continue;}
      const modifiedAt=new Date(row.updated_at);
      yield {name,chunks:readableChunks(object.body),modifiedAt:Number.isFinite(modifiedAt.getTime())?modifiedAt:undefined};
    }
    yield {name:'export-status.json',chunks:textChunks(JSON.stringify({exportedAt,missingOriginals:unavailable,includesArchived:true,includesLocalUnsynced:false}))};
  }
  return new Response(zipStream(entries()),{headers:{
    'Content-Type':'application/zip','Content-Disposition':`attachment; filename="my-trail-log-${exportedAt.slice(0,10)}.zip"`,
    'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'",
  }});
}
