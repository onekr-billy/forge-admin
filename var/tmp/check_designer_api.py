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

    for oid, name in [('2090956178226143234', '11'), ('2090969102315413505', '12')]:
        data = page.evaluate(f'''async () => {{
            const token = JSON.parse(localStorage.getItem('prod_auth') || '{{}}').accessToken
            const resp = await fetch('/dev-api/ai/business/object/{oid}/designer', {{ headers: {{ Authorization: 'Bearer ' + token }} }})
            const body = await resp.json()
            return body.data || body
        }}''')
        ps = data.get('pageSchema') or {}
        print(f'--- object {name} designer API')
        for z in (ps.get('zones') or []):
            if z.get('zoneKey') in ('search', 'table', 'toolbar'):
                print('  designer zone', z.get('zoneKey'), 'fieldRefs=', z.get('fieldRefs'))
        keys = list(data.keys())
        print('  top keys:', keys)
    browser.close()
