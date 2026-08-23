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
    target = page.locator('.navigation-row', has=page.locator('button.navigation-page span:text-is("11")')).first
    target.locator('button[title="编辑"]').click()
    page.wait_for_timeout(3000)
    page.click('button.runtime-app-tab:has-text("列表设计")')
    page.wait_for_timeout(6000)

    info = page.evaluate('''() => {
        const all = document.querySelectorAll('*')
        const out = []
        for (let i = 0; i < all.length; i++) {
            const inst = all[i].__vueParentComponent
            if (inst && inst.type?.__name === 'BusinessListDesigner') {
                const ss = inst.setupState || {}
                const keys = Object.keys(ss).filter(k => /dirty|saving|save/i.test(k))
                const vals = {}
                for (const k of keys) { try { vals[k] = typeof ss[k] === 'function' ? 'fn' : ss[k] } catch (e) { vals[k] = 'err' } }
                out.push({ propsDefaultViewOnly: inst.props?.defaultViewOnly, setupKeys: vals })
            }
        }
        const btns = Array.from(document.querySelectorAll('button')).map(b => ({ t: b.textContent.trim(), d: b.disabled })).filter(b => b.t)
        return { instances: out, buttons: btns.filter(b => /保存|发布/.test(b.t)) }
    }''')
    print(json.dumps(info, ensure_ascii=False, indent=1))
    browser.close()
