import json
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()

    put_layout, put_app = [], []
    render_search = []
    def on_request(req):
        if '/layout/list' in req.url and req.method == 'PUT':
            try: put_layout.append(req.post_data_json)
            except Exception: pass
        if req.url.endswith('/ai/business/application') and req.method == 'PUT':
            put_app.append(req.url)
    def on_response(resp):
        if '/ai/crud-config/render/' in resp.url and 'object_11' in resp.url:
            try:
                cfg = resp.json().get('data') or {}
                render_search.append([f.get('field') for f in (cfg.get('searchSchema') or [])])
            except Exception: pass
    page.on('request', on_request)
    page.on('response', on_response)

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

    # 保存按钮状态（挂载脏标记后应可点）
    state = page.evaluate('''() => {
        const b = Array.from(document.querySelectorAll('button')).find(x => x.textContent.trim() === '保存草稿')
        return b ? { disabled: b.disabled } : null
    }''')
    print('SAVE BUTTON after mount:', json.dumps(state))

    # 点击顶栏保存草稿
    page.evaluate('''() => {
        const b = Array.from(document.querySelectorAll('button')).find(x => x.textContent.trim() === '保存草稿' && !x.disabled)
        if (b) b.click()
    }''')
    page.wait_for_timeout(8000)
    print('PUT layout/list count:', len(put_layout))
    for b in put_layout:
        for z in (b.get('zones') or []):
            if z.get('zoneKey') == 'search':
                print('  saved search zone fieldRefs:', z.get('fieldRefs'))
    print('PUT application count:', len(put_app))

    browser.close()

# 重新加载验证渲染
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
    page.wait_for_timeout(4000)
    page.locator('button.navigation-page span:text-is("11")').first.click()
    page.wait_for_timeout(4000)
    info = page.evaluate('''() => {
        const toolbar = document.querySelector('.ai-crud-toolbar')
        const buttons = toolbar ? Array.from(toolbar.querySelectorAll('button')).map(b => b.textContent.trim()).filter(Boolean) : null
        const more = toolbar ? (toolbar.textContent || '') : ''
        const search = document.querySelector('.ai-crud-search')
        const all = document.querySelectorAll('*')
        let crud = null
        for (let i = 0; i < all.length; i++) {
            const inst = all[i].__vueParentComponent
            if (inst && inst.type?.__name === 'AiCrudPage') {
                crud = { showImport: inst.props?.showImport, showExport: inst.props?.showExport,
                         hideBatchDelete: inst.props?.hideBatchDelete,
                         searchFields: (inst.props?.searchSchema||[]).map(f=>f.field) }
                break
            }
        }
        return { toolbarButtons: buttons, toolbarText: more.replace(/\\s+/g, ' ').slice(0, 120),
                 searchText: search ? search.innerText.replace(/\\n/g, '|').slice(0, 120) : null, crud }
    }''')
    print('=== PAGE 11 RUNTIME AFTER FIX ===')
    print(json.dumps(info, ensure_ascii=False, indent=1))
    browser.close()
