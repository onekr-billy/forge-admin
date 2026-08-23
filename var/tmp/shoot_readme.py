import sys, os
sys.path.insert(0, '/Users/yaomindong/.agents/skills/forge-docs-writer/scripts')
from playwright.sync_api import sync_playwright
from screenshot import login, BASE_URL

OUT = '/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/images'

PAGES = [
    ('/system/user', 'user-management.png'),
    ('/system/menu', '菜单管理.png'),
    ('/system/config-center', '配置管理.png'),
    ('/system/monitor', '服务监控.png'),
    ('/ai/agent', 'ai-agent.png'),
]

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1600, 'height': 900}, device_scale_factor=2)
    login(page)

    for route, name in PAGES:
        page.goto(f'{BASE_URL}{route}', timeout=30000)
        page.wait_for_timeout(4000)
        page.screenshot(path=os.path.join(OUT, name))
        print(f'saved {name}')

    browser.close()
print('DONE')
