"""复现：点击编辑页面后跳转的不是对应页面，而是第一个页面。"""
import json

from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'
TARGET_TITLE = '12'


def dump_state(page, label):
    url = page.url
    selected = page.evaluate('''() => {
        const rows = Array.from(document.querySelectorAll('.navigation-row'))
        return rows.map(row => ({
            title: (row.querySelector('.navigation-page span:last-child, .navigation-group span:last-child') || {}).textContent || '',
            selected: row.classList.contains('base-app-sidebar__node_selected'),
            hasEditBtn: !!row.querySelector('button[title="编辑"]'),
        }))
    }''')
    print(f'--- {label} ---')
    print('URL:', url)
    for row in selected:
        mark = ' <-- SELECTED' if row['selected'] else ''
        edit = ' [有编辑按钮]' if row['hasEditBtn'] else ''
        print(f"  [{row['title']}]{edit}{mark}")
    print()


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()

    # 1. 登录
    page.goto(BASE, wait_until='networkidle')
    page.wait_for_timeout(1500)
    if page.locator('input[type="password"]').count() > 0:
        page.fill('input[placeholder*="账号"], input[placeholder*="用户"]', 'admin')
        page.fill('input[type="password"]', '123456')
        page.keyboard.press('Enter')
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(2000)

    # 2. 进入应用工作台
    app_code = 'presale_registration_apply'
    page.goto(f'{BASE}/app-center/application/{app_code}/runtime', wait_until='networkidle')
    page.wait_for_timeout(3000)
    dump_state(page, '初始页面管理视图')

    # 3. 按标题找到目标页面行，点击编辑按钮
    print(f'>>> 点击页面「{TARGET_TITLE}」的编辑按钮')
    target_row = page.locator('.navigation-row', has=page.locator(f'.navigation-page span:text-is("{TARGET_TITLE}")')).first
    target_row.locator('button[title="编辑"]').click()

    # 4. 多时间点观察状态
    page.wait_for_timeout(800)
    dump_state(page, '点击编辑后 0.8s')
    page.wait_for_timeout(2500)
    dump_state(page, '点击编辑后 3.3s')
    page.wait_for_timeout(4000)
    dump_state(page, '点击编辑后 7.3s（最终）')

    page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/enter_page_design_final.png')
    browser.close()
