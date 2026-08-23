"""Test all 7 issues with Playwright."""
import time
from playwright.sync_api import sync_playwright

BASE = "http://localhost:3000"

def login(page):
    """Login to the system."""
    page.goto(f"{BASE}/login")
    page.wait_for_load_state('networkidle')
    page.fill('input[placeholder*="用户名"], input[placeholder*="账号"]', 'admin')
    page.fill('input[type="password"]', '123456')
    page.click('button[type="submit"], button:has-text("登录"), button:has-text("登 录")')
    page.wait_for_load_state('networkidle')
    time.sleep(2)

def test_issue_3_5(page, results):
    """Issue 3: Search/notification hidden; Issue 5: Hero removed in market"""
    page.goto(f"{BASE}/app-center?view=MARKET")
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    hero = page.query_selector('.market-hero')
    results['issue5_hero_removed'] = hero is None
    tabs = page.query_selector_all('.market-toolbar .n-tabs .n-tab')
    results['issue5_tabs_count'] = len(tabs)
    page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/issue5_market.png', full_page=True)
    print(f"Issue 5: hero_removed={results['issue5_hero_removed']}, tabs_count={results['issue5_tabs_count']}")

def test_issue_4(page, results):
    """Issue 4: Suite page small screen button overlap"""
    page.goto(f"{BASE}/app-center?suiteCode=PRESALE_REGISTRATION")
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    page.set_viewport_size({"width": 500, "height": 800})
    time.sleep(0.5)
    page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/issue4_suite_small.png', full_page=True)
    buttons = page.query_selector_all('.suite-head-top .n-button')
    overlap = False
    for i in range(len(buttons)):
        for j in range(i+1, len(buttons)):
            box1 = buttons[i].bounding_box()
            box2 = buttons[j].bounding_box()
            if box1 and box2:
                if not (box1['x'] + box1['width'] <= box2['x'] or box2['x'] + box2['width'] <= box1['x']):
                    overlap = True
                    print(f"  Buttons {i} and {j} overlap: {box1} vs {box2}")
    results['issue4_buttons_overlap'] = overlap
    print(f"Issue 4: buttons_overlap={overlap}")
    page.set_viewport_size({"width": 1280, "height": 800})

def test_issue_6(page, results):
    """Issue 6: Application card shows only page count"""
    page.goto(f"{BASE}/app-center")
    page.wait_for_load_state('networkidle')
    time.sleep(2)
    assets = page.query_selector_all('.application-assets span')
    asset_texts = [asset.inner_text() for asset in assets]
    results['issue6_asset_texts'] = asset_texts
    has_old = any('对象' in t or '入口' in t or '流程' in t or '扩展' in t for t in asset_texts)
    has_page = any('页面' in t for t in asset_texts)
    results['issue6_old_removed'] = not has_old
    results['issue6_page_shown'] = has_page
    print(f"Issue 6: assets={asset_texts}, old_removed={not has_old}, page_shown={has_page}")
    page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/issue6_cards.png', full_page=True)

def test_issue_2_3(page, results):
    """Issue 2: Profile new tab; Issue 3: Search/notification hidden in portal"""
    page.goto(f"{BASE}/app/presale_registration_apply?pageId=page_page")
    page.wait_for_load_state('networkidle')
    time.sleep(2)
    page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/issue2_portal.png', full_page=True)
    avatar = page.query_selector('#user-dropdown')
    results['issue2_avatar_found'] = avatar is not None
    search = page.query_selector('.portal-search')
    notification = page.query_selector('button[aria-label="通知"]')
    results['issue3_search_hidden'] = search is None
    results['issue3_notification_hidden'] = notification is None
    print(f"Issue 2: avatar_found={avatar is not None}")
    print(f"Issue 3: search_hidden={search is None}, notification_hidden={notification is None}")

def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1280, "height": 800})
        page = context.new_page()
        results = {}
        login(page)
        print("Logged in")
        test_issue_3_5(page, results)
        test_issue_4(page, results)
        test_issue_6(page, results)
        test_issue_2_3(page, results)
        browser.close()
        print("\n=== RESULTS ===")
        for k, v in results.items():
            print(f"  {k}: {v}")

if __name__ == '__main__':
    main()
