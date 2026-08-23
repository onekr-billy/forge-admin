from playwright.sync_api import sync_playwright
import json

BASE = 'http://localhost:3000'

EXTRACT = '''() => {
    const results = []
    const seen = new Set()
    const all = document.querySelectorAll('*')
    for (let i = 0; i < all.length && i < 6000; i++) {
        const inst = all[i].__vueParentComponent
        if (!inst || seen.has(inst.uid))
            continue
        const name = inst.type?.__name || inst.type?.name || ''
        if (name !== 'GridBlockRenderer')
            continue
        seen.add(inst.uid)
        const p = inst.props || {}
        const block = p.block || {}
        const eff = inst.setupState?.effectiveRuntimeCrudProps
        results.push({
            blockShowSearch: block.props?.showSearch,
            formOnly: block.props?.formOnly,
            pageMode: block.props?.objectRef?.pageMode,
            effectiveShowSearch: eff ? eff.showSearch : null,
            effectiveSearchSchema: eff ? (eff.searchSchema || []).map(f => f.label || f.field) : null,
        })
    }
    return results
}'''

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()

    put_urls = []
    page.on('request', lambda req: put_urls.append(req.url) if req.method == 'PUT' else None)

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

    # 进入页面「12」设计 + 列表 tab（让 activePageDesignTab='list'）
    target = page.locator('.navigation-row', has=page.locator('button.navigation-page span:text-is("11")')).first
    target.locator('button[title="编辑"]').click()
    page.wait_for_timeout(3000)
    page.click('button.runtime-app-tab:has-text("列表设计")')
    page.wait_for_timeout(4000)

    # 通过 setupState 调用 handleEmbeddedDesignerSaved（模拟列表设计保存事件）
    put_urls.clear()
    called = page.evaluate('''async () => {
        const all = document.querySelectorAll('*')
        for (let i = 0; i < all.length && i < 8000; i++) {
            const inst = all[i].__vueParentComponent
            const name = inst?.type?.__name || inst?.type?.name || ''
            if (name.includes('application-runtime') && inst.setupState?.handleEmbeddedDesignerSaved) {
                await inst.setupState.handleEmbeddedDesignerSaved()
                return name
            }
        }
        return null
    }''')
    print('called on:', called)
    page.wait_for_timeout(3000)  # 等待 300ms 防抖 + PUT
    print('PUT after saved event:')
    for u in put_urls:
        print('  ', u[:120])

    # 回到运行视图验证渲染
    page.goto(f'{BASE}/app-center/application/{app_code}/runtime', wait_until='networkidle')
    page.wait_for_timeout(4000)
    page.locator('.navigation-row button.navigation-page:has(span:text-is("11"))').first.click()
    page.wait_for_timeout(3500)
    info = page.evaluate(EXTRACT)
    print('RENDERED PAGE 11 BLOCKS:', json.dumps(info, ensure_ascii=False, indent=1))

    dom = page.evaluate('''() => ({
        searchInputs: Array.from(document.querySelectorAll('.ai-crud-search input, .ai-crud-search .n-input')).length,
        searchText: (document.querySelector('.ai-crud-search') || {}).innerText || '',
    })''')
    print('SEARCH AREA:', json.dumps(dom, ensure_ascii=False))
    page.screenshot(path='/tmp/list_fix_verify.png', full_page=True)

    browser.close()
