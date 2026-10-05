"""A phone requesting the desktop site must keep readable capture controls.

The desktop user agent and 980px layout viewport recreate Chrome's desktop-site
presentation, while screen metrics and touch input remain those of the phone.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
import json, os

BASE = os.environ.get('TEST_ORIGIN', 'http://localhost:8794')
ARTIFACTS = Path(os.environ.get('TEST_ARTIFACTS', '/workspace/artifacts'))
DESKTOP_UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36'
ARTIFACTS.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])
    results = []
    for width, height in [(360,800),(430,932)]:
        ratio = 980 / width
        layout_height = round(height * ratio)
        context = browser.new_context(viewport={'width':980,'height':layout_height},
                                      screen={'width':width,'height':height},
                                      is_mobile=True,has_touch=True,user_agent=DESKTOP_UA)
        page = context.new_page(); errors = []
        page.on('pageerror',lambda error: errors.append(str(error)))
        page.goto(BASE,wait_until='networkidle')
        expect(page.locator('.sidebar')).not_to_be_visible()
        expect(page.locator('.mobile-nav')).to_be_visible()
        zoom = page.evaluate('Number(getComputedStyle(document.documentElement).zoom)')
        assert abs(zoom-ratio) < .02, zoom
        camera = page.get_by_role('button',name='Take a photo',exact=True)
        expect(camera).to_be_visible()
        box = camera.bounding_box(); navigation = page.locator('.mobile-nav').bounding_box()
        assert box['height']/ratio >= 60
        assert box['y']+box['height'] < navigation['y']
        assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
        for button in page.locator('.mobile-nav button').all():
            assert button.bounding_box()['height']/ratio >= 48
        # Capture the virtual wide viewport at the phone's display scale.
        session = context.new_cdp_session(page)
        capture = session.send('Page.captureScreenshot',{'format':'png','clip':{
            'x':0,'y':0,'width':980,'height':layout_height,'scale':1/ratio}})
        import base64
        (ARTIFACTS / f'fieldlogger-phone-desktop-site-{width}.png').write_bytes(base64.b64decode(capture['data']))
        page.get_by_role('button',name='Journal',exact=True).click()
        expect(page.get_by_role('heading',name='Your field journal')).to_be_visible()
        page.locator('input[type=file]').nth(1).set_input_files('public/field-robin.jpg')
        expect(page.get_by_role('button',name='Save discovery',exact=True)).to_be_visible()
        photo = page.locator('.capture-photo').bounding_box()
        assert photo['height']/ratio >= height*.4
        page.get_by_role('button',name='Save discovery',exact=True).click()
        expect(page.get_by_role('heading',name='A little mystery',exact=True)).to_be_visible()
        session.send('Emulation.setPageScaleFactor',{'pageScaleFactor':1.5})
        assert page.evaluate('visualViewport.scale') > 1
        assert abs(page.evaluate('Number(getComputedStyle(document.documentElement).zoom)')-ratio) < .02
        assert not errors, errors
        results.append({'physical_screen':f'{width}x{height}','desktop_layout_width':980,
                        'desktop_sidebar_hidden':True,'camera_physical_height':round(box['height']/ratio),
                        'touch_navigation_visible':True,'capture_and_save_work':True,'pinch_zoom_available':True})
        context.close()
    # A computer keeps its normal wide layout.
    context = browser.new_context(viewport={'width':1280,'height':900})
    page = context.new_page();page.goto(BASE,wait_until='networkidle')
    expect(page.locator('.sidebar')).to_be_visible()
    assert page.evaluate('Number(getComputedStyle(document.documentElement).zoom)') == 1
    context.close();browser.close()
    (ARTIFACTS / 'fieldlogger-phone-desktop-site.json').write_text(json.dumps(results,indent=2)+'\n')
    print(json.dumps(results))
