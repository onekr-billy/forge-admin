import json
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()
    page.goto(BASE, wait_until='networkidle')
    page.wait_for_timeout(1500)
    if page.locator('input[type="password"]').count() > 0:
        page.fill('input[placeholder*="账号"], input[placeholder*="用户"]', 'admin')
        page.fill('input[type="password"]', '123456')
        page.keyboard.press('Enter')
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(2000)
    page.goto(f'{BASE}/app-center/application/presale_registration_apply/runtime', wait_until='networkidle')
    page.wait_for_timeout(3000)

    for oid in ['2089974506884993026', '2090956178226143234', '2090969102315413505']:
        data = page.evaluate(f'''async () => {{
            const token = JSON.parse(localStorage.getItem('prod_auth') || '{{}}').accessToken
            const resp = await fetch('/dev-api/ai/business/object/{oid}/layout/list', {{ headers: {{ Authorization: 'Bearer ' + token }} }})
            const body = await resp.json()
            return body.data || body
        }}''')
        print(f'--- object {oid} layoutType={data.get("layoutType")}')
        for z in (data.get('zones') or []):
            print('  saved zone', z.get('zoneKey'), 'enabled=', z.get('enabled'), 'fieldRefs=', z.get('fieldRefs'))
        for z in (((data.get('pageSchema') or {}).get('zones')) or []):
            print('  pageSchema zone', z.get('zoneKey'), 'enabled=', z.get('enabled'), 'fieldRefs=', z.get('fieldRefs'))
    browser.close()
