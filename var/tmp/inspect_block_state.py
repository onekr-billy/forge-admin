import json
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()

    render_payloads = []
    def on_response(resp):
        if '/ai/crud-config/render/' in resp.url:
            try:
                render_payloads.append({'url': resp.url, 'json': resp.json()})
            except Exception:
                pass
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

    for page_name in ['11', '12']:
        try:
            page.locator(f'button.navigation-page span:text-is("{page_name}")').first.click()
        except Exception:
            pass
        page.wait_for_timeout(3500)

        info = page.evaluate('''() => {
            const all = document.querySelectorAll('*')
            const out = []
            for (let i = 0; i < all.length; i++) {
                const inst = all[i].__vueParentComponent
                if (!inst || inst.type?.__name !== 'GridBlockRenderer') continue
                const bp = inst.props?.block?.props || {}
                const rt = inst.props?.runtimeCrudProps || {}
                out.push({
                    blockSearchFieldRefs: bp.searchFieldRefs,
                    blockShowSearch: bp.showSearch,
                    blockHideToolbar: bp.hideToolbar,
                    blockHideBatchDelete: bp.hideBatchDelete,
                    blockHideSelection: bp.hideSelection,
                    blockToolbarActions: bp.toolbarActions,
                    blockFormOnly: bp.formOnly,
                    rtSearchFields: (rt.searchSchema || []).map(f => f.field || f.prop || f.label),
                    rtHideBatchDelete: rt.hideBatchDelete,
                    rtHideToolbar: rt.hideToolbar,
                    rtHideSelection: rt.hideSelection,
                    rtToolbarActions: rt.toolbarActions,
                    rtShowImport: rt.showImport,
                    rtShowExport: rt.showExport,
                })
            }
            return out
        }''')
        print(f'=== PAGE {page_name} BLOCKS ===')
        print(json.dumps(info, ensure_ascii=False, indent=1))

    print('=== RENDER API PAYLOADS ===')
    for item in render_payloads:
        cfg = item['json'].get('data') or item['json']
        print(item['url'].split('/render/')[-1])
        print('  searchSchema fields:', [f.get('field') or f.get('prop') for f in (cfg.get('searchSchema') or [])])
        print('  hideBatchDelete:', cfg.get('hideBatchDelete'), '| hideToolbar:', cfg.get('hideToolbar'), '| hideSelection:', cfg.get('hideSelection'))
        print('  toolbarActions:', cfg.get('toolbarActions'))

    browser.close()
