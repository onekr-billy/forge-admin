import json
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1680, 'height': 1000})
    page = ctx.new_page()

    put_bodies = []
    render_after = []
    def on_request(req):
        if '/layout/list' in req.url and req.method == 'PUT':
            try: put_bodies.append(req.post_data_json)
            except Exception: put_bodies.append({'raw': req.post_data})
    def on_response(resp):
        if '/ai/crud-config/render/' in resp.url and 'object_11' in resp.url:
            try:
                cfg = resp.json().get('data') or {}
                render_after.append([f.get('field') for f in (cfg.get('searchSchema') or [])])
            except Exception: pass
    page.on('request', on_request)
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

    target = page.locator('.navigation-row', has=page.locator('button.navigation-page span:text-is("11")')).first
    target.locator('button[title="编辑"]').click()
    page.wait_for_timeout(3000)
    page.click('button.runtime-app-tab:has-text("列表设计")')
    page.wait_for_timeout(6000)

    state = page.evaluate('''() => {
        const btns = Array.from(document.querySelectorAll('.application-design-section button, button')).map(b => ({ text: b.textContent.trim(), disabled: b.disabled })).filter(b => b.text.includes('保存'))
        const all = document.querySelectorAll('*')
        let dirty = 'n/a'
        for (let i = 0; i < all.length; i++) {
            const inst = all[i].__vueParentComponent
            if (inst && inst.type?.__name === 'BusinessListDesigner') {
                const ss = inst.setupState || {}
                dirty = { saving: ss.saving, dirtyRef: ss.dirty }
                break
            }
        }
        return { saveButtons: btns, dirty }
    }''')
    print('SAVE STATE:', json.dumps(state, ensure_ascii=False))

    # 尝试点击列表设计器内的保存按钮
    clicked = page.evaluate('''() => {
        const btns = Array.from(document.querySelectorAll('button'))
        const b = btns.find(x => x.textContent.trim() === '保存' && !x.disabled)
        if (b) { b.click(); return true }
        return false
    }''')
    print('clicked save:', clicked)
    page.wait_for_timeout(5000)

    print('PUT bodies:', len(put_bodies))
    for b in put_bodies:
        zones = b.get('zones') or []
        for z in zones:
            print('  zone', z.get('zoneKey'), 'fieldRefs=', z.get('fieldRefs'))
    print('render after:', render_after)
    browser.close()
