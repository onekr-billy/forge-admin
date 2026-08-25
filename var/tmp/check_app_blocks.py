import json
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:3000'

def walk_blocks(blocks, path=''):
    out = []
    for b in (blocks or []):
        p = f'{path}/{b.get("blockType","?")}'
        if b.get('blockType') == 'AiCrudPage':
            out.append((p, b.get('props') or {}))
        for key in ('children', 'blocks', 'items'):
            if isinstance(b.get(key), list):
                out.extend(walk_blocks(b[key], p))
    return out

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

    data = page.evaluate('''async () => {
        const token = JSON.parse(localStorage.getItem('prod_auth') || '{}').accessToken
        const resp = await fetch('/dev-api/ai/business/application/2089968247981060098', { headers: { Authorization: 'Bearer ' + token } })
        return (await resp.json()).data || {}
    }''')
    nav = data.get('navigationConfig') or data.get('navigation') or {}
    if isinstance(nav, str):
        nav = json.loads(nav)
    nodes = nav.get('nodes') or []
    for node in nodes:
        page_id = node.get('id')
        blocks = node.get('pageBlocks') or node.get('blocks')
        print(f'=== node {page_id} ({node.get("name")}) template={node.get("pageTemplate")} ===')
        if not blocks:
            print('  无 pageBlocks 字段; keys:', list(node.keys()))
            continue
        for path, props in walk_blocks(blocks if isinstance(blocks, list) else [blocks]):
            keys_of_interest = {k: v for k, v in props.items() if k in ('showImport','showExport','hideBatchDelete','enableCustomQuery','hideAdd','hideToolbar','hideSelection','formOnly','showSearch','searchFieldRefs')}
            print(f'  {path}: {json.dumps(keys_of_interest, ensure_ascii=False)}')
    browser.close()
