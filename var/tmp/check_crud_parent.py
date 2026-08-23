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
            if (!inst || inst.type?.__name !== 'AiCrudPage') continue
            if (!inst.props?.searchSchema) continue
            // 向上找父组件链
            const chain = []
            let cur = inst.parent
            while (cur && chain.length < 8) {
                chain.push(cur.type?.__name || cur.type?.name || String(cur.type).slice(0, 30))
                cur = cur.parent
            }
            // 工具栏可见性
            const el = inst.subTree?.el
            const root = el?.closest?.('.ai-crud-page') || el
            const toolbar = root?.querySelector?.('.ai-crud-toolbar')
            results.push({
                showImport: inst.props.showImport,
                visible: !!(el && el.offsetParent !== null),
                hasToolbar: !!toolbar,
                parentChain: chain,
            })
        }
        return results
    }''')
    print(json.dumps(info, ensure_ascii=False, indent=1))
    browser.close()
