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
    page.wait_for_timeout(4000)
    page.locator('button.navigation-page span:text-is("11")').first.click()
    page.wait_for_timeout(4000)
    info = page.evaluate('''() => {
        const results = []
        const all = document.querySelectorAll('*')
        for (let i = 0; i < all.length; i++) {
            const inst = all[i].__vueParentComponent
            if (!inst || inst.type?.__name !== 'GridBlockRenderer') continue
            const block = inst.props?.block
            if (!block || block.blockType !== 'AiCrudPage') continue
            const bp = block.props || {}
            results.push({
                blockId: block.id,
                propKeys: Object.keys(bp),
                showImport: bp.showImport, showExport: bp.showExport,
                enableCustomQuery: bp.enableCustomQuery, hideBatchDelete: bp.hideBatchDelete,
                runtimeShowImport: inst.props?.runtimeCrudProps?.showImport,
                runtimeShowExport: inst.props?.runtimeCrudProps?.showExport,
                runtimeEnableCustomQuery: inst.props?.runtimeCrudProps?.enableCustomQuery,
            })
        }
        return results
    }''')
    print(json.dumps(info, ensure_ascii=False, indent=1))
    browser.close()
