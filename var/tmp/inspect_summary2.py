import json
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'

def login(page):
    page.goto(BASE, wait_until='networkidle')
    page.wait_for_timeout(1500)
    if page.locator('input[type="password"]').count() > 0:
        page.fill('input[placeholder*="账号"], input[placeholder*="用户"]', 'admin')
        page.fill('input[type="password"]', '123456')
        page.keyboard.press('Enter')
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(2000)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()
    login(page)
    app_code = 'presale_registration_apply'
    out = {}
    for page_name in ['11', '12']:
        page.goto(f'{BASE}/app-center/application/{app_code}/runtime', wait_until='networkidle')
        page.wait_for_timeout(4000)
        page.locator(f'button.navigation-page span:text-is("{page_name}")').first.click()
        page.wait_for_timeout(3500)
        out[f'runtime_{page_name}'] = page.evaluate('''() => {
            const toolbar = document.querySelector('.ai-crud-toolbar')
            const buttons = toolbar ? Array.from(toolbar.querySelectorAll('button')).map(b => b.textContent.trim()).filter(Boolean) : null
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
            return { toolbarButtons: buttons, crud }
        }''')
        target = page.locator('.navigation-row', has=page.locator(f'button.navigation-page span:text-is("{page_name}")')).first
        target.locator('button[title="编辑"]').click()
        page.wait_for_timeout(3000)
        page.click('button.runtime-app-tab:has-text("列表设计")')
        page.wait_for_timeout(6000)
        out[f'designer_{page_name}'] = page.evaluate('''() => {
            const all = document.querySelectorAll('*')
            for (let i = 0; i < all.length; i++) {
                const inst = all[i].__vueParentComponent
                if (inst && inst.type?.__name === 'BusinessListDesigner') {
                    const s = inst.setupState?.localSchema || {}
                    const zones = {}
                    for (const z of (s.zones || [])) {
                        zones[z.zoneKey] = {
                            enabled: z.enabled,
                            fieldRefs: z.fieldRefs,
                            items: (z.items || []).map(it => ({ id: it.id, label: it.label, fieldRef: it.fieldRef })),
                            props: z.props,
                        }
                    }
                    return { dirty: inst.setupState?.dirty, zones }
                }
            }
            return null
        }''')
    with open('var/tmp/inspect_summary.json', 'w') as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print('saved ok')
    browser.close()
