"""Check touch targets, real imagery, direct save and photo-first phone layouts."""
from playwright.sync_api import sync_playwright, expect
from pathlib import Path
import json,os
BASE=os.environ.get('TEST_ORIGIN','http://localhost:8787')
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 results=[]
 for width,height in [(360,800),(390,844),(430,932)]:
  c=b.new_context(viewport={'width':width,'height':height},is_mobile=True,has_touch=True)
  page=c.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(BASE,wait_until='networkidle')
  camera=page.get_by_role('button',name='Take a photo',exact=True);expect(camera).to_be_visible()
  box=camera.bounding_box();assert box['height']>=60 and box['y']+box['height']<=height-75
  assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
  assert page.locator('.discover-backdrop').evaluate('(img)=>img.complete && img.naturalWidth > 0')
  for item in page.locator('.mobile-nav button').all(): assert item.bounding_box()['height']>=48
  expect(page.locator('.inspiration-photo')).to_have_count(3)
  page.locator('input[type=file]').nth(1).set_input_files('public/field-robin.jpg')
  expect(page.get_by_role('button',name='Save discovery',exact=True)).to_be_visible()
  expect(page.get_by_label('A name, if you know it')).not_to_be_visible()
  page.screenshot(path=f'/workspace/artifacts/fieldlogger-mobile-capture-{width}.png')
  page.get_by_role('button',name='Save discovery',exact=True).click()
  expect(page.get_by_role('heading',name='A little mystery',exact=True)).to_be_visible()
  assert page.locator('.photo-wrap').bounding_box()['height']>=300
  page.locator('.discovery-card').click()
  expect(page.get_by_role('heading',name='From your field journal')).to_be_visible()
  assert page.locator('.detail-photo').bounding_box()['height']>=height*.45
  page.screenshot(path=f'/workspace/artifacts/fieldlogger-mobile-detail-{width}.png')
  page.get_by_role('button',name='Close',exact=True).click()
  page.get_by_role('button',name='Discover home',exact=True).click()
  page.screenshot(path=f'/workspace/artifacts/fieldlogger-mobile-home-{width}.png')
  page.evaluate('document.documentElement.style.fontSize="200%"')
  assert not page.evaluate('document.documentElement.scrollWidth > innerWidth')
  assert not errors,errors
  results.append({'viewport':f'{width}x{height}','large_touch_controls':True,'photo_first_capture':True,'no_overflow_at_200_percent_text':True,'errors':errors})
  c.close()
 b.close();Path('/workspace/artifacts/fieldlogger-mobile-design.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))
