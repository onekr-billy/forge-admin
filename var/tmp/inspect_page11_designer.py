import json
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()

    layout_payloads = []
    def on_request(req):
        if '/layout/list' in req.url and req.method == 'PUT':
            try:
                layout_payloads.append(req.post_data_json)
            except Exception:
                pass
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

    # 1) 页面 11 运行态 DOM
    page.locator('button.navigation-page span:text-is("11")').first.click()
    page.wait_for_timeout(3500)
    info = page.evaluate('''() => {
        const toolbar = document.querySelector('.ai-crud-toolbar')
        const buttons = toolbar ? Array.from(toolbar.querySelectorAll('button')).map(b => b.textContent.trim()).filter(Boolean) : null
        const all = document.querySelectorAll('*')
        let crud = null
        for (let i = 0; i < all.length; i++) {
            const inst = all[i].__vueParentComponent
            if (inst && inst.type?.__name === 'AiCrudPage') {
                crud = { showImport: inst.props?.showImport, showExport: inst.props?.showExport,
                         hideBatchDelete: inst.props?.hideBatchDelete, searchFields: (inst.props?.searchSchema||[]).map(f=>f.field) }
                break
            }
        }
        return { toolbarButtons: buttons, crud }
    }''')
    print('=== PAGE 11 RUNTIME ===')
    print(json.dumps(info, ensure_ascii=False))

    # 2) 进入页面 11 编辑 → 列表设计 tab，读取当前 schema（不保存，仅读取实例状态）
    target = page.locator('.navigation-row', has=page.locator('button.navigation-page span:text-is("11")')).first
    target.locator('button[title="编辑"]').click()
    page.wait_for_timeout(3000)
    page.click('button.runtime-app-tab:has-text("列表设计")')
    page.wait_for_timeout(6000)

    schema = page.evaluate('''() => {
        const all = document.querySelectorAll('*')
        for (let i = 0; i < all.length; i++) {
            const inst = all[i].__vueParentComponent
            if (inst && inst.type?.__name === 'BusinessListDesigner') {
                const s = inst.setupState?.localSchema || inst.props?.modelValue
                const zones = (s?.zones || []).map(z => ({ zoneKey: z.zoneKey, enabled: z.enabled, fieldRefs: z.fieldRefs, props_keys: Object.keys(z.props||{}) }))
                return { layoutType: s?.layoutType, zones, toolbarProps: (s?.zones||[]).find(z=>z.zoneKey==='toolbar')?.props }
            }
        }
        return null
    }''')
    print('=== PAGE 11 DESIGNER SCHEMA ===')
    print(json.dumps(schema, ensure_ascii=False, indent=1))
    browser.close()
