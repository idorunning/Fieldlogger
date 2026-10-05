"""Local-only API/ACL integration checks. Start npm run dev -- --port 8787 first. Uses requests.
Publication fixtures are inserted into local D1 only; no production moderation bypass exists.
"""
import requests,uuid,json,sqlite3,pathlib,hashlib,datetime
base='http://localhost:8787';root=pathlib.Path(__file__).resolve().parents[2];now=datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z');clients=[];checks=[]
def checked(name,condition):
 assert condition,name
 checks.append(name)
def req(s,method,path,body=None,status=200):
 r=s.request(method,base+'/api/'+path,json=body,timeout=35)
 assert r.status_code==status,(path,r.status_code,r.text[:240])
 return r.json() if 'json' in r.headers.get('content-type','') else r.content
for label in ['owner','friend','outsider']:
 s=requests.Session();s.headers['Origin']=base
 email=label+'-'+str(uuid.uuid4())+'@example.test';password='Social-QA-2026-only!'
 u=req(s,'POST','auth/register',dict(email=email,password=password,name='PRIVATE REAL NAME'))['user'];clients.append((s,u,password));req(s,'GET','social/me')
a,b,c=[x[0] for x in clients];owner,friend,other=[x[1] for x in clients]
dbfile=next(p for p in (root/'.wrangler/state/v3/d1').rglob('*.sqlite') if sqlite3.connect(p).execute("SELECT count(*) FROM sqlite_master WHERE name='publications'").fetchone()[0]);db=sqlite3.connect(dbfile)
ids=[]
try:
 for audience in ['everyone','people','local']:
  id=str(uuid.uuid4());ids.append(id);metadata=dict(id=id,capturedAt=now,localDate=now[:10],localHour=12,timezone='Europe/London',latitude=51.75,longitude=-1.25,accuracy=5,locationSource='gps',place='QA woodland',note='PRIVATE NOTE',category='birds',name='QA Robin',scientificName='Erithacus rubecula',confirmed=True,identification=None,analysisState='pending',archived=False,updatedAt=now,revision=1)
  with open(root/'public/field-robin.jpg','rb') as image:r=a.put(base+'/api/observations/'+id,files={'metadata':(None,json.dumps(metadata)),'photo':('photo.jpg',image,'image/jpeg')},timeout=30)
  checked('upload '+audience,r.status_code==200)
  key=db.execute('SELECT photo_key FROM observations WHERE id=?',(id,)).fetchone()[0];snapshot={k:metadata[k] for k in ['name','scientificName','category','place','capturedAt','localDate','timezone','latitude','longitude']};snapshot.update(summary='QA bird',interestingInfo='QA fact',confidence='medium')
  db.execute("INSERT INTO publications(observation_id,owner_id,status,audience,snapshot,photo_key,generation,centre_lat,centre_lon,radius_km,reason,created_at,updated_at) VALUES(?,?,'published',?,?,?,'local-fixture',51.75,-1.25,5,'',?,?)",(id,owner['id'],audience,json.dumps(snapshot),key,now,now));db.commit()
 allid,privateid,localid=ids;token=uuid.uuid4().hex+uuid.uuid4().hex
 db.execute('INSERT INTO publication_recipients(id,observation_id,generation,email,token_hash) VALUES(?,?,?,?,?)',(str(uuid.uuid4()),privateid,'local-fixture',friend['email'],hashlib.sha256(token.encode()).hexdigest()));db.commit()
 avatar={'skin':6,'hair':4,'cut':10,'eyes':2,'expression':3,'glasses':1,'presentation':0,'pose':1,'outfit':5}
 req(a,'PUT','social/avatar',avatar);checked('avatar persists privately and appears on public username',req(a,'GET','social/me')['profile']['avatar']==avatar and req(b,'GET','social/photos/'+allid)['photo']['author']['avatar']==avatar)
 req(a,'PUT','social/avatar',{'skin':99},400)
 ledger={'achievements':[{'badge':'calendar-12-25','earnedAt':now}]};req(a,'PUT','social/achievements',ledger);req(a,'PUT','social/achievements',ledger);checked('permanent achievement sync is idempotent and private',len(req(a,'GET','social/achievements')['achievements'])==1 and not req(b,'GET','social/achievements')['achievements'])
 checked('map photo text filter and notification since filter',len(req(b,'GET','social/feed?scope=everyone&query=robin')['photos'])==1 and not req(b,'GET','social/feed?scope=everyone&query=oak')['photos'] and not req(b,'GET','social/feed?scope=following&since=2099-01-01T00:00:00Z')['photos'])
 checked('only everyone visible without centre or invite',len(req(b,'GET','social/feed?scope=everyone')['photos'])==1)
 for who in [b,c]:req(who,'GET','social/photos/'+privateid,status=404);req(who,'GET','social/photos/'+localid+'/photo',status=404)
 req(c,'POST','social/invite',{'token':token},404)
 req(b,'PUT','social/users/'+owner['id']+'/follow',{'following':True});req(b,'GET','social/photos/'+privateid,status=404)
 req(b,'POST','social/invite',{'token':token});checked('claimed private invite grants photo',len(req(b,'GET','social/photos/'+privateid+'/photo'))>100)
 req(c,'GET','social/photos/'+privateid+'/photo',status=404)
 req(b,'GET','social/photos/'+localid+'?lat=51.75&lon=-1.25');req(b,'GET','social/photos/'+localid+'?lat=55&lon=-1',status=404)
 checked('local circle includes browsing centre',len(req(b,'GET','social/feed?scope=everyone&lat=51.75&lon=-1.25')['photos'])==3)
 for i in range(2):req(b,'PUT','social/photos/'+allid+'/acorn',{'active':True})
 checked('acorns idempotent',req(b,'GET','social/photos/'+allid)['photo']['acorns']==1)
 req(c,'PUT','social/photos/'+allid+'/acorn',{'active':True});checked('acorns count different accounts',req(a,'GET','social/photos/'+allid)['photo']['acorns']==2)
 req(b,'PUT','social/photos/'+allid+'/save',{'active':True});checked('saved place private',len(req(b,'GET','social/feed?scope=saved')['photos'])==1 and len(req(c,'GET','social/feed?scope=saved')['photos'])==0)
 dto=req(b,'GET','social/photos/'+allid)['photo'];checked('no private fields or account name',all(x not in json.dumps(dto) for x in ['PRIVATE',owner['email'],'password']))
 checked('contacts opt-in default',not req(b,'POST','social/contacts',{'emails':[owner['email']]})['users'])
 profile=req(a,'GET','social/me')['profile'];req(a,'PUT','social/me',{'username':profile['username'],'discoverable':True});found=req(b,'POST','social/contacts',{'emails':[owner['email']]})['users'];checked('selected email returns username only',len(found)==1 and 'email' not in found[0])
 req(b,'PUT','social/users/'+owner['id']+'/block',{'blocked':True});req(b,'GET','social/photos/'+allid,status=404);checked('block hides both directions',not req(b,'GET','social/feed?scope=everyone')['photos']);req(b,'PUT','social/users/'+owner['id']+'/block',{'blocked':False})
 req(c,'GET','social/moderation',status=404)
 req(c,'POST','social/photos/'+allid+'/report',{'reason':'people','detail':'QA report'});req(b,'GET','social/photos/'+allid+'/photo',status=404);checked('report immediately hides saved shared photo',not req(b,'GET','social/feed?scope=saved')['photos'])
 req(a,'DELETE','social/photos/'+privateid+'/publish');req(b,'GET','social/photos/'+privateid+'/photo',status=404);req(b,'POST','social/invite',{'token':token},404);checks.append('unpublish invalidates photos and old invitations')
 req(a,'POST','social/photos/'+localid+'/publish',{'audience':'everyone','revision':1,'agree':True},503);checks.append('publishing fails closed without service credential')
 epoch=req(a,'GET','social/me')['bulkEpoch'];req(a,'POST','social/unpublish-all',{'confirm':False},400);req(a,'POST','social/unpublish-all',{'confirm':True});checked('unpublish all advances cancellation epoch and retains private photos',req(a,'GET','social/me')['bulkEpoch']>epoch and len(req(a,'GET','observations')['observations'])==3)
 req(a,'POST','social/photos/'+localid+'/publish',{'audience':'everyone','revision':1,'agree':True,'bulkEpoch':epoch},409);checked('cancelled bulk batch cannot restart publication',True)
 checked('all shared photos unavailable after unpublish-all',not req(b,'GET','social/feed?scope=everyone&lat=51.75&lon=-1.25')['photos'])
 print(json.dumps({'passed':len(checks),'checks':checks},indent=2))
finally:
 for s,u,p in clients:
  try:req(s,'DELETE','account',{'password':p})
  except Exception as e:print('cleanup failed',u['id'],type(e).__name__)
 db.close()
