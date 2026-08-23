from playwright.sync_api import sync_playwright
import json

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

    workspace_data = {}
    render_responses = []

    def on_response(resp):
        url = resp.url
        try:
            if '/workspace' in url and '/business/application/' in url:
                workspace_data['body'] = resp.json()
            elif '/ai/crud-config/render/' in url:
                body = resp.json()
                d = body.get('data') or {}
                render_responses.append({
                    'url': url,
                    'code': body.get('code'),
                    'searchSchema': d.get('searchSchema'),
                    'columns_len': len(d.get('columnsSchema') or []),
                    'edit_len': len(d.get('editSchema') or []),
                    'publishStatus': d.get('publishStatus'),
                })
        except Exception as e:
            print(f'response parse err: {url} {e}')

    page.on('response', on_response)

    app_code = 'presale_registration_apply'
    page.goto(f'{BASE}/app-center/application/{app_code}/runtime', wait_until='networkidle')
    page.wait_for_timeout(4000)

    # dump workspace builder
    body = (workspace_data.get('body') or {}).get('data') or {}
    builder = body.get('builder') or {}
    nodes = builder.get('nodes') or []

    def walk(node, depth=0):
        t = node.get('type')
        print('  ' * depth + f"- [{t}] {node.get('title')} (id={node.get('id')})")
        for b in (node.get('pageBlocks') or []):
            props = b.get('props') or {}
            print('  ' * (depth + 1)
                  + f"* block type={b.get('blockType')} component={b.get('component')} "
                  + f"fieldRefs={props.get('fieldRefs')} "
                  + f"hasSearchFieldRefs={'searchFieldRefs' in props} searchFieldRefs={props.get('searchFieldRefs')} "
                  + f"objectId={props.get('objectId')} objectCode={props.get('objectCode')} configKey={props.get('configKey')} "
                  + f"showSearch={props.get('showSearch')}")
        for child in (node.get('children') or []):
            walk(child, depth + 1)

    print('=== WORKSPACE NODES ===')
    for n in nodes:
        walk(n)

    print()
    print('=== RENDER RESPONSES ===')
    print(json.dumps(render_responses, ensure_ascii=False, indent=2))

    # 点击每个页面看渲染
    rows = page.locator('.navigation-page').all()
    print(f'nav rows: {len(rows)}')
    for row in rows:
        try:
            title = row.inner_text().strip()
            row.click(timeout=2000)
            page.wait_for_timeout(1800)
        except Exception:
            pass

    page.wait_for_timeout(2000)
    page.screenshot(path='/tmp/list_debug_view.png', full_page=True)

    print()
    print('=== FINAL RENDER RESPONSES ===')
    print(json.dumps(render_responses, ensure_ascii=False, indent=2))

    dom = page.evaluate('''() => ({
        searchAreas: Array.from(document.querySelectorAll('[class*="search"]')).map(el => el.className).slice(0, 10),
        text: document.body.innerText.slice(0, 600),
    })''')
    print('DOM:', json.dumps(dom, ensure_ascii=False))

    browser.close()
