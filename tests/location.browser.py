"""Location enrichment must survive slow GPS, offline use and manual edits."""
from playwright.sync_api import sync_playwright, expect
import os, json
BASE=os.environ.get('TEST_ORIGIN','http://localhost:8794')
READ="""() => new Promise((resolve,reject)=>{const r=indexedDB.open('fieldnotes-v1');r.onsuccess=()=>{const d=r.result;const q=d.transaction('observations').objectStore('observations').getAll();q.onsuccess=()=>{resolve(q.result.map(({photo,...rest})=>rest));d.close()};q.onerror=()=>reject(q.error)}})"""
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 c=b.new_context(viewport={'width':390,'height':844},is_mobile=True,has_touch=True,service_workers='block')
 c.add_init_script("""window.gpsCallbacks=[];navigator.geolocation.getCurrentPosition=(ok)=>window.gpsCallbacks.push(ok);window.releaseGps=()=>{const callback=window.gpsCallbacks.shift();callback({coords:{latitude:51.752,longitude:-1.258,accuracy:7}})}""")
 page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 requests=[]
 def places(route):
  requests.append(route.request.url)
  route.fulfill(json={'place':'Carfax, Oxford','source':'OpenStreetMap / Photon'})
 page.route('**/api/place?*',places)
 page.goto(BASE,wait_until='networkidle')
 with page.expect_file_chooser() as chooser:page.get_by_role('button',name='Camera',exact=True).click()
 chooser.value.set_files('public/field-robin.jpg')
 expect(page.get_by_role('button',name='Save discovery',exact=True)).to_be_visible()
 page.get_by_role('button',name='Save discovery',exact=True).click()
 expect(page.locator('.journal-photo')).to_have_count(1)
 assert page.evaluate(READ)[0]['latitude'] is None
 page.evaluate('releaseGps()')
 expect(page.locator('.journal-photo')).to_contain_text('Carfax, Oxford',timeout=15000)
 r=page.evaluate(READ)[0];assert r['latitude']==51.752 and r['longitude']==-1.258 and r['place']=='Carfax, Oxford'
 print('PASS: GPS arriving after save updates the correct photo and adds its place')
 # A gallery image without EXIF must not inherit the current camera coordinates.
 page.locator('input[type=file]').nth(1).set_input_files('public/field-robin.jpg')
 page.get_by_role('button',name='Save discovery',exact=True).click()
 expect(page.locator('.journal-photo')).to_have_count(2)
 rows=page.evaluate(READ);gallery=next(x for x in rows if x['id']!=r['id']);assert gallery['latitude'] is None and gallery['place']==''
 print('PASS: gallery photo without EXIF does not inherit current GPS')
 # Offline camera: attach GPS and preserve a user-supplied place despite late arrival.
 c.set_offline(True)
 with page.expect_file_chooser() as chooser:page.get_by_role('button',name='Camera',exact=True).click()
 chooser.value.set_files('public/field-fox.jpg')
 expect(page.get_by_role('button',name='Save discovery',exact=True)).to_be_visible()
 page.locator('.capture-details summary').click();page.get_by_label('Place',exact=True).fill('My own woodland name')
 page.get_by_role('button',name='Save discovery',exact=True).click();page.evaluate('releaseGps()')
 page.wait_for_function("""async () => {const rows=await ("""+READ+""")();return rows.some(r=>r.place==='My own woodland name' && r.latitude===51.752)}""")
 c.set_offline(False);page.wait_for_timeout(1500)
 assert any(x['place']=='My own woodland name' for x in page.evaluate(READ))
 # Offline uncached location, then reconnect. No new GPS confirmation.
 c.set_offline(True)
 with page.expect_file_chooser() as chooser:page.get_by_role('button',name='Camera',exact=True).click()
 chooser.value.set_files('public/woodland.jpg')
 expect(page.get_by_role('button',name='Save discovery',exact=True)).to_be_visible()
 page.evaluate("gpsCallbacks.shift()({coords:{latitude:51.76,longitude:-1.3,accuracy:8}})")
 page.get_by_role('button',name='Save discovery',exact=True).click();expect(page.locator('.journal-photo')).to_have_count(4)
 rows=page.evaluate(READ);assert next(x for x in rows if x['latitude']==51.76)['place']==''
 c.set_offline(False)
 page.wait_for_function("""async () => {const rows=await ("""+READ+""")();return rows.some(r=>r.latitude===51.76 && r.place==='Carfax, Oxford')}""",timeout=15000)
 assert not errors,errors
 print('PASS: offline GPS retained; place resolves on reconnect; manual name preserved; no uncaught errors')
 c.close();b.close()
