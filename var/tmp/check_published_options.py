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

    for key in ['presale_registration_business_object_11', 'presale_registration_business_object']:
        for dp in ['false', 'true']:
            data = page.evaluate(f'''async () => {{
                const token = JSON.parse(localStorage.getItem('prod_auth') || '{{}}').accessToken
                const resp = await fetch('/dev-api/ai/crud-config/render/{key}?designPreview={dp}&applicationId=2089968247981060098', {{ headers: {{ Authorization: 'Bearer ' + token }} }})
                const j = await resp.json()
                return j.data || {{ error: j.msg }}
            }}''')
            opts = data.get('options') or {}
            print(f'--- {key} designPreview={dp}: publishStatus={data.get("publishStatus")} '
                  f'showImport={opts.get("showImport")} showExport={opts.get("showExport")} '
                  f'enableCustomQuery={opts.get("enableCustomQuery")} '
                  f'search={[f.get("field") for f in (data.get("searchSchema") or [])]}')
    browser.close()
