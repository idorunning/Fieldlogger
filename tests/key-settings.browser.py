"""Mobile key setup: fake credentials, local backend; no calls to OpenAI."""
from playwright.sync_api import sync_playwright, expect
import uuid, os, json
BASE = os.environ.get('TEST_ORIGIN', 'http://localhost:8787')
FIXTURE = 'sk-test-only-not-a-real-api-key-333333333333'
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])
    ctx = browser.new_context(viewport={'width':390,'height':844}, is_mobile=True, has_touch=True)
    page = ctx.new_page(); errors=[]; page.on('pageerror', lambda e:errors.append(str(e)))
    page.goto(BASE+'/#api-key', wait_until='networkidle')
    expect(page.get_by_role('heading',name='API key settings',exact=True)).to_be_visible()
    page.get_by_role('button',name='Sign in or create account',exact=True).click()
    page.get_by_label('Your name',exact=True).fill('Key QA')
    page.get_by_label('Email',exact=True).fill(f'key-ui-{uuid.uuid4()}@example.test')
    page.get_by_label('Password',exact=True).fill('Fieldnotes-QA-2026-Only!')
    page.get_by_role('button',name='Create my account',exact=True).click()
    expect(page.get_by_role('heading',name='API key settings',exact=True)).to_be_visible()
    field=page.get_by_label('OpenAI API key',exact=True)
    expect(field).to_have_attribute('type','password')
    expect(field).to_be_enabled()
    field.fill(FIXTURE)
    page.get_by_role('button',name='Save key',exact=True).click()
    expect(page.get_by_text('Key saved securely.',exact=False)).to_be_visible()
    expect(field).to_have_value('')
    expect(page.get_by_role('button',name='Replace saved key',exact=True)).to_be_visible()
    assert FIXTURE not in page.content()
    assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
    page.screenshot(path='/workspace/artifacts/fieldnotes-api-key-mobile.png',full_page=True)
    page.get_by_role('button',name='Close',exact=True).click()
    page.reload(wait_until='networkidle')
    expect(page.get_by_label('OpenAI API key',exact=True)).to_have_value('')
    expect(page.get_by_role('button',name='Replace saved key',exact=True)).to_be_visible()
    field=page.get_by_label('OpenAI API key',exact=True)
    field.fill(FIXTURE.replace('333333333333','444444444444'))
    page.get_by_role('button',name='Replace saved key',exact=True).click()
    expect(page.get_by_text('Key saved securely.',exact=False)).to_be_visible()
    # UI failure rendering is simulated. Backend upstream status mapping has unit tests.
    page.route('**/api/settings/openai-key/test', lambda route:route.fulfill(status=422,content_type='application/json',body=json.dumps({'error':'Your OpenAI key has expired, been revoked, or is invalid. Replace it in API key settings. Your photos are saved.'})))
    page.get_by_role('button',name='Test connection',exact=True).click()
    expect(page.get_by_role('alert')).to_contain_text('expired')
    page.get_by_role('button',name='Remove key',exact=True).click()
    page.get_by_role('button',name='Confirm removal',exact=True).click()
    expect(page.get_by_text('Your saved key was removed.',exact=False)).to_be_visible()
    expect(page.get_by_role('button',name='Test connection',exact=True)).to_be_disabled()
    # Check all local journal stores and cached API responses without printing data.
    stores=page.evaluate('''async () => {
      const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('fieldnotes-v1');r.onsuccess=()=>resolve(r.result);r.onerror=reject;});
      let data='';for(const name of db.objectStoreNames){data+=JSON.stringify(await new Promise((resolve,reject)=>{const r=db.transaction(name).objectStore(name).getAll();r.onsuccess=()=>resolve(r.result);r.onerror=reject;}));}db.close();
      return data+JSON.stringify(localStorage)+JSON.stringify(sessionStorage);
    }''')
    assert FIXTURE not in stores and 'sk-test-only' not in stores
    ctx.set_offline(True)
    expect(page.get_by_text('Connect to the internet to manage your key.',exact=False)).to_be_visible()
    expect(field).to_be_disabled()
    assert not errors,errors
    print('PASS: mobile sign-in gate, masked entry, save, replace, clear field, reopen, expiry guidance, confirmed removal, offline lockout, no key in journal storage, no overflow or browser errors.')
    ctx.close();browser.close()
