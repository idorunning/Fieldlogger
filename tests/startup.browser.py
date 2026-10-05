"""Optional browser integrations must never blank the journal at startup."""
from playwright.sync_api import sync_playwright, expect
import os, json

BASE = os.environ.get('TEST_ORIGIN', 'http://localhost:8791')
fixtures = {
    'method_throws': "Object.defineProperty(document, 'modelContext', {value: {registerTool(){throw new Error('Optional integration unavailable');}}});",
    'registration_rejects': "Object.defineProperty(document, 'modelContext', {value: {registerTool(){return Promise.reject(new Error('Optional integration unavailable'));}}});",
    'property_throws': "Object.defineProperty(document, 'modelContext', {get(){throw new Error('Optional integration unavailable');}});",
}
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])
    results = []
    for name, fixture in fixtures.items():
        context = browser.new_context(viewport={'width':390,'height':844}, is_mobile=True, has_touch=True)
        context.add_init_script(fixture)
        page = context.new_page(); errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(BASE, wait_until='networkidle')
        expect(page.get_by_role('button', name='Camera', exact=True)).to_be_visible()
        page.get_by_role('button', name='Journal', exact=True).click()
        expect(page.get_by_role('heading', name='Your field journal')).to_be_visible()
        assert not errors, errors
        results.append({'optional_integration_failure':name, 'journal_opens':True, 'browser_errors':errors})
        context.close()
    browser.close()
    print(json.dumps(results))
