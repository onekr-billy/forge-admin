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

    for page_name in ['测试', '11', '12']:
        try:
            page.locator(f'button.navigation-page span:text-is("{page_name}")').first.click()
        except Exception as e:
            print(f'click {page_name} failed: {e}')
        page.wait_for_timeout(4000)
        info = page.evaluate('''() => {
            const toolbar = document.querySelector('.ai-crud-toolbar')
            const toolbarButtons = toolbar ? Array.from(toolbar.querySelectorAll('button')).map(b => b.textContent.trim()).filter(Boolean) : null
            // 「更多」下拉内容需要 hover 才能看到，直接读 dropdown trigger 的 title/aria
            const searchLabels = Array.from(document.querySelectorAll('.ai-crud-search label, .ai-crud-search .n-form-item-label')).map(l => l.textContent.trim()).filter(Boolean)
            const all = document.querySelectorAll('*')
            let crudProps = null
            for (let i = 0; i < all.length; i++) {
                const inst = all[i].__vueParentComponent
                if (inst && inst.type?.__name === 'AiCrudPage' && inst.props?.searchSchema) {
                    crudProps = {
                        showImport: inst.props?.showImport,
                        showExport: inst.props?.showExport,
                        hideBatchDelete: inst.props?.hideBatchDelete,
                        enableCustomQuery: inst.props?.enableCustomQuery,
                        searchFields: (inst.props?.searchSchema || []).map(f => f.field),
                    }
                    break
                }
            }
            return { toolbarButtons, searchLabels, crudProps }
        }''')
        print(f'=== PAGE {page_name} ===')
        print(json.dumps(info, ensure_ascii=False, indent=1))
        # hover「更多」按钮查看下拉项
        more = page.locator('.ai-crud-toolbar button:has-text("更多")').first
        if more.count() > 0:
            more.hover()
            page.wait_for_timeout(1200)
            items = page.evaluate('''() => Array.from(document.querySelectorAll('.n-dropdown-option, .n-dropdown-menu .n-dropdown-option')).map(o => o.textContent.trim()).filter(Boolean)''')
            print('  更多下拉:', items)
    browser.close()
