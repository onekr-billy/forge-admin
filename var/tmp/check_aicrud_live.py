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

    for url, label in [
        (f'{BASE}/app-center/application/presale_registration_apply/runtime?node=page-2090956178226143234', '页面11'),
        (f'{BASE}/app-center/application/presale_registration_apply/runtime?node=page-2089974506884993026', '测试页'),
    ]:
        page.goto(url, wait_until='networkidle')
        page.wait_for_timeout(4000)
        info = page.evaluate('''() => {
            const results = []
            const walk = (el) => {
                if (!el) return
                const inst = el.__vueParentComponent
                if (inst && (inst.type?.__name === 'AiCrudPage' || inst.type?.name === 'AiCrudPage')) {
                    const pr = inst.props || {}
                    results.push({
                        showImport: pr.showImport, showExport: pr.showExport,
                        hideBatchDelete: pr.hideBatchDelete, enableCustomQuery: pr.enableCustomQuery,
                        hideAdd: pr.hideAdd, hideToolbar: pr.hideToolbar,
                        showSearch: pr.showSearch,
                        searchFields: (pr.searchSchema || []).map(f => f.field),
                    })
                }
                for (const child of el.children || []) walk(child)
            }
            walk(document.body)
            return results
        }''')
        # 同时看工具栏按钮
        btns = page.evaluate('''() => Array.from(document.querySelectorAll('button')).map(b => b.textContent.trim()).filter(Boolean).slice(0, 20)''')
        print(f'--- {label}')
        print('  AiCrudPage props:', info)
        print('  buttons:', btns)
    browser.close()
