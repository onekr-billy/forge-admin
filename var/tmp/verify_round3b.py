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
    put_app = []
    page.on('request', lambda req: put_app.append(req.url) if req.url.endswith('/ai/business/application') and req.method == 'PUT' else None)
    login(page)
    page.goto(f'{BASE}/app-center/application/presale_registration_apply/runtime', wait_until='networkidle')
    page.wait_for_timeout(4000)
    target = page.locator('.navigation-row', has=page.locator('button.navigation-page span:text-is("12")')).first
    target.locator('button[title="编辑"]').click()
    page.wait_for_timeout(3000)
    page.click('button.runtime-app-tab:has-text("列表设计")')
    page.wait_for_timeout(6000)
    clicked = page.evaluate('''() => {
        const b = Array.from(document.querySelectorAll('button')).find(x => x.textContent.trim() === '保存草稿' && !x.disabled)
        if (b) { b.click(); return true }
        return false
    }''')
    page.wait_for_timeout(8000)
    print(f'--- 12: 保存草稿点击={clicked}, PUT application 累计={len(put_app)}')
    browser.close()

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()
    login(page)
    page.goto(f'{BASE}/app-center/application/presale_registration_apply/runtime', wait_until='networkidle')
    page.wait_for_timeout(4000)
    for name in ['测试', '11', '12']:
        page.locator(f'button.navigation-page span:text-is("{name}")').first.click()
        page.wait_for_timeout(4500)
        info = page.evaluate('''() => {
            const toolbar = document.querySelector('.ai-crud-toolbar')
            const buttons = toolbar ? Array.from(toolbar.querySelectorAll('button')).map(b => b.textContent.trim()).filter(Boolean) : null
            const searchLabels = Array.from(document.querySelectorAll('.ai-crud-search label, .ai-crud-search .n-form-item-label')).map(l => l.textContent.trim()).filter(Boolean)
            return { toolbarButtons: buttons, searchLabels }
        }''')
        print(f'=== PAGE {name} ===')
        print(json.dumps(info, ensure_ascii=False))
        more = page.locator('.ai-crud-toolbar button:has-text("更多")').first
        if more.count() > 0:
            more.hover()
            page.wait_for_timeout(1200)
            items = page.evaluate('''() => Array.from(document.querySelectorAll('.n-dropdown-option')).map(o => o.textContent.trim()).filter(Boolean)''')
            print('  更多下拉:', json.dumps(items, ensure_ascii=False))
    browser.close()
