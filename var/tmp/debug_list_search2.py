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
        {
                const p = inst.props || {}
                const block = p.block || {}
                const rc = p.runtimeCrudProps || {}
                const eff = inst.setupState?.effectiveRuntimeCrudProps
                results.push({
                    blockType: block.blockType,
                    blockShowSearch: block.props?.showSearch,
                    formOnly: block.props?.formOnly,
                    hideToolbar: block.props?.hideToolbar,
                    formOpenMode: block.props?.formOpenMode,
                    pageKey: block.props?.objectRef?.pageKey,
                    pageMode: block.props?.objectRef?.pageMode,
                    runtimeShowSearch: rc.showSearch,
                    runtimeOptionsShowSearch: rc.options?.showSearch,
                    blockPropsKeys: Object.keys(block.props || {}).filter(k => /search/i.test(k)),
                    searchFieldRefs: block.props?.searchFieldRefs,
                    fieldRefs: block.fieldRefs,
                    runtimeSearchSchema: (rc.searchSchema || []).map(f => f.field),
                    effectiveSearchSchema: eff ? (eff.searchSchema || []).map(f => f.field) : null,
                    effectiveShowSearch: eff ? eff.showSearch : null,
                    effectiveColumns: eff ? (eff.columns || []).map(c => c.prop || c.key) : null,
                })
        }
    }
    return results
}'''

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

    app_code = 'presale_registration_apply'
    page.goto(f'{BASE}/app-center/application/{app_code}/runtime', wait_until='networkidle')
    page.wait_for_timeout(4000)

    # 依次点导航，收集每个页面的 block props
    rows = page.locator('.navigation-row .navigation-page').all()
    print(f'nav rows: {len(rows)}')
    for i, row in enumerate(rows):
        try:
            title = row.inner_text().strip()
            row.click(timeout=2000)
            page.wait_for_timeout(2500)
            info = page.evaluate(EXTRACT)
            if info:
                print(f'=== page nav[{i}] {title} ===')
                print(json.dumps(info, ensure_ascii=False, indent=1))
        except Exception as e:
            print(f'row {i} err: {e}')

    browser.close()
