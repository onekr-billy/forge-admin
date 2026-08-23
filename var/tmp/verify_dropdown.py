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
    page.wait_for_timeout(4500)
    page.locator('.ai-crud-toolbar button:has-text("更多")').first.hover()
    page.wait_for_timeout(1500)
    items = page.evaluate('''() => Array.from(document.querySelectorAll('[role="menu"] [role="menuitem"], .n-dropdown-option, .n-menu-item-content')).map(o => o.textContent.trim()).filter(Boolean)''')
    print('更多下拉项:', json.dumps(items, ensure_ascii=False))
    page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/page11_toolbar.png')
    browser.close()
