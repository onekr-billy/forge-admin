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

    app_code = 'presale_registration_apply'
    page.goto(f'{BASE}/app-center/application/{app_code}/runtime', wait_until='networkidle')
    page.wait_for_timeout(4000)

    for page_name in ['测试', '12']:
        try:
            page.locator(f'button.navigation-page span:text-is("{page_name}")').first.click()
        except Exception:
            pass
        page.wait_for_timeout(3500)
        info = page.evaluate('''() => {
            const toolbar = document.querySelector('.ai-crud-toolbar')
            const buttons = toolbar ? Array.from(toolbar.querySelectorAll('button')).map(b => b.textContent.trim()).filter(Boolean) : null
            const searchArea = document.querySelector('.ai-crud-search, [class*="search"]')
            const checkboxes = document.querySelectorAll('.n-data-table .n-checkbox').length
            const all = document.querySelectorAll('*')
            let crudProps = null
            for (let i = 0; i < all.length; i++) {
                const inst = all[i].__vueParentComponent
                if (inst && inst.type?.__name === 'AiCrudPage') {
                    crudProps = {
                        hideToolbar: inst.props?.hideToolbar,
                        hideBatchDelete: inst.props?.hideBatchDelete,
                        hideSelection: inst.props?.hideSelection,
                        hideAdd: inst.props?.hideAdd,
                        showImport: inst.props?.showImport,
                        showExport: inst.props?.showExport,
                        toolbarActions: inst.props?.toolbarActions,
                        showSearch: inst.props?.showSearch,
                        searchFields: (inst.props?.searchSchema || []).map(f => f.field || f.prop),
                    }
                    break
                }
            }
            return { toolbarButtons: buttons, checkboxCount: checkboxes, crudProps }
        }''')
        print(f'=== PAGE {page_name} ===')
        print(json.dumps(info, ensure_ascii=False, indent=1))
    browser.close()
