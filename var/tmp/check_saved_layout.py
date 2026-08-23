import json
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()

    designer_urls = []
    def on_request(req):
        if '/ai/business/object/' in req.url and '/designer' in req.url and req.method == 'GET':
            designer_urls.append(req.url)
    page.on('request', on_request)

    page.goto(BASE, wait_until='networkidle')
    page.wait_for_timeout(1500)
    if page.locator('input[type="password"]').count() > 0:
        page.fill('input[placeholder*="账号"], input[placeholder*="用户"]', 'admin')
        page.fill('input[type="password"]', '123456')
        page.keyboard.press('Enter')
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(2000)

    app_code = 'presale_registration_apply'
    page.goto(f'{BASE}/app-center/application/{app_code}/runtime', wait_until='networkidle')
    page.wait_for_timeout(4000)

    target = page.locator('.navigation-row', has=page.locator('button.navigation-page span:text-is("11")')).first
    target.locator('button[title="编辑"]').click()
    page.wait_for_timeout(3000)
    page.click('button.runtime-app-tab:has-text("列表设计")')
    page.wait_for_timeout(6000)

    print('designer urls:', designer_urls[:3])
    import re
    m = re.search(r'/object/(\d+)/designer', ' '.join(designer_urls))
    object_id = m.group(1) if m else None
    print('objectId:', object_id)
    if object_id:
        layout = page.evaluate(f'''async () => {{
            const resp = await fetch('/dev-api/ai/business/object/{object_id}/layout/list', {{ headers: {{ 'Accept': 'application/json' }} }})
            return await resp.json()
        }}''')
        data = layout.get('data') or {}
        zones = data.get('zones') or []
        for z in zones:
            print('saved zone', z.get('zoneKey'), 'enabled=', z.get('enabled'), 'fieldRefs=', z.get('fieldRefs'))
        ps_zones = ((data.get('pageSchema') or {}).get('zones')) or []
        for z in ps_zones:
            print('pageSchema zone', z.get('zoneKey'), 'fieldRefs=', z.get('fieldRefs'))
    browser.close()
