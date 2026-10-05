"""Regression for first-launch upgrade from a cached blank page.

Build previous live commit cbc0cd9 in a separate checkout. Set OLD_RELEASE_CLIENT
its dist/client path, and OLD_RELEASE_HTML to its rendered root HTML. The test
replays that cached shell, then uses the current origin's actual service worker
to recover and reopen offline, without clearing the IndexedDB note fixture.
Run with an overall process timeout to detect worker activation deadlocks.
"""
from pathlib import Path
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright, expect
import base64, json, mimetypes, os

BASE = os.environ.get('TEST_ORIGIN', 'http://localhost:8791').rstrip('/')
old_root = Path(os.environ['OLD_RELEASE_CLIENT'])
old_html = Path(os.environ['OLD_RELEASE_HTML']).read_bytes()
artifacts = Path(os.environ.get('TEST_ARTIFACTS', '/workspace/artifacts'))
artifacts.mkdir(parents=True, exist_ok=True)
old_assets = [old_root / name.lstrip('/') for name in json.loads((old_root / 'offline-assets.json').read_text())]
payload = [{'path':'/', 'data':base64.b64encode(old_html).decode(),'type':'text/html'}]
for path in old_assets:
    payload.append({'path':'/'+str(path.relative_to(old_root)),'data':base64.b64encode(path.read_bytes()).decode(),'type':mimetypes.guess_type(path)[0] or 'application/octet-stream'})
options = dict(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
proxy_url = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
if proxy_url:
    parsed = urlsplit(proxy_url)
    options['proxy'] = {'server':f'{parsed.scheme}://{parsed.hostname}:{parsed.port}'}
    if parsed.username: options['proxy']['username'] = parsed.username
    if parsed.password: options['proxy']['password'] = parsed.password
with sync_playwright() as p:
    browser = p.chromium.launch(**options)
    context = browser.new_context(viewport={'width':390,'height':844},is_mobile=True,has_touch=True)
    context.add_init_script("Object.defineProperty(document,'modelContext',{value:{registerTool(){window.__registrationFixtureReached=true;throw new Error('Optional integration fixture unavailable');}}});")
    page = context.new_page()
    errors = []
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto(BASE + '/privacy',wait_until='networkidle')
    page.evaluate('''async (assets) => {
      const cache = await caches.open('fieldnotes-shell-v4');
      for (const asset of assets) {
        const bytes = Uint8Array.from(atob(asset.data), c => c.charCodeAt(0));
        await cache.put(asset.path,new Response(bytes,{headers:{'Content-Type':asset.type}}));
      }
      await new Promise((resolve,reject) => {
        const open = indexedDB.open('fieldnotes-v1',1);
        open.onupgradeneeded = () => {
          const obs = open.result.createObjectStore('observations',{keyPath:'id'});
          obs.createIndex('owner','owner');
          open.result.createObjectStore('meta');
        };
        open.onerror = () => reject(open.error);
        open.onsuccess = () => {
          const db = open.result;
          const tx = db.transaction('meta','readwrite');
          tx.objectStore('meta').put('Keep this offline note','startup-preservation-fixture');
          tx.oncomplete = () => {db.close();resolve();};
          tx.onerror = () => reject(tx.error);
        };
      });
    }''',payload)
    replay = {'root_delivered':False,'old_blank_observed':False}
    def serve_previous_cache(route):
        request = route.request
        path = urlsplit(request.url).path
        if path == '/' and not replay['root_delivered']:
            replay['root_delivered'] = True
            route.fulfill(status=200,content_type='text/html',body=old_html)
        elif path.startswith('/assets/') and (old_root / path.lstrip('/')).is_file():
            file = old_root / path.lstrip('/')
            route.fulfill(status=200,content_type=mimetypes.guess_type(file)[0] or 'application/octet-stream',body=file.read_bytes())
        else:
            route.continue_()
    context.route(BASE + '/**',serve_previous_cache)
    page.goto(BASE + '/?app_version=1.0.2',wait_until='domcontentloaded')
    page.wait_for_function("window.__registrationFixtureReached && document.body.innerText.length === 0",timeout=15000)
    replay['old_blank_observed'] = True
    print('PASS: cached HTML and JavaScript from the previous release reproduce the blank startup')
    page.wait_for_function("!location.search.includes('app_version') && navigator.serviceWorker.controller !== null",timeout=40000)
    page.wait_for_load_state('networkidle',timeout=40000)
    expect(page.get_by_role('button',name='Take a photo',exact=True)).to_be_visible(timeout=15000)
    page.get_by_role('button',name='Journal',exact=True).click()
    expect(page.get_by_role('heading',name='Your field journal')).to_be_visible()
    caches = page.evaluate('caches.keys()')
    assert 'fieldnotes-shell-v5' in caches and 'fieldnotes-shell-v4' not in caches,caches
    note = page.evaluate('''() => new Promise(resolve => {
      const open = indexedDB.open('fieldnotes-v1',1);
      open.onsuccess = () => {
        const db = open.result;
        const value = db.transaction('meta').objectStore('meta').get('startup-preservation-fixture');
        value.onsuccess = () => {resolve(value.result);db.close();};
      };
    })''')
    assert note == 'Keep this offline note',note
    page.get_by_role('button',name='Discover home',exact=True).click()
    page.screenshot(path=str(artifacts / 'fieldlogger-startup-repaired.png'),full_page=True)
    context.unroute(BASE + '/**',serve_previous_cache)
    context.set_offline(True)
    page.reload(wait_until='domcontentloaded')
    expect(page.get_by_role('button',name='Take a photo',exact=True)).to_be_visible(timeout=15000)
    page.get_by_role('button',name='Journal',exact=True).click()
    expect(page.get_by_role('heading',name='Your field journal')).to_be_visible()
    assert not errors,errors
    result = {'method':'Replay cached HTML and JavaScript built from previous live commit; install actual production v5 service worker',
              'previous_source_commit':'cbc0cd9f3bfd6c82ffae17bcafe95975ea49c706',
              'old_blank_screen_reproduced_before_update':replay['old_blank_observed'],
              'old_v4_cache_replaced_with_v5':True,'first_updated_android_launch_refreshed':True,
              'camera_visible':True,'journal_interactive':True,'local_note_preserved':True,
              'repaired_shell_reopens_offline':True,'uncaught_browser_errors':errors}
    (artifacts / 'fieldlogger-startup-upgrade.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))
    context.close();browser.close()
