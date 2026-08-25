"""Targeted test for Issue 4 and Issue 5 tab overlap."""
import time
from playwright.sync_api import sync_playwright

BASE = "http://localhost:3000"

def login(page):
    page.goto(f"{BASE}/login")
    page.wait_for_load_state('networkidle')
    page.fill('input[placeholder*="用户名"], input[placeholder*="账号"]', 'admin')
    page.fill('input[type="password"]', '123456')
    page.click('button[type="submit"], button:has-text("登录"), button:has-text("登 录")')
    page.wait_for_load_state('networkidle')
    time.sleep(2)

def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1280, "height": 800})
        page = context.new_page()
        
        login(page)
        print("Logged in")
        
        # Issue 4: app-center with suiteCode at small screen
        page.goto(f"{BASE}/app-center?suiteCode=PRESALE_REGISTRATION")
        page.wait_for_load_state('networkidle')
        time.sleep(2)
        
        # Test at 500px width
        page.set_viewport_size({"width": 500, "height": 800})
        time.sleep(1)
        page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/issue4_app_center_small.png', full_page=True)
        
        # Check radio group buttons
        radio_buttons = page.query_selector_all('.toolbar-left .n-radio-button')
        print(f"Radio buttons found: {len(radio_buttons)}")
        for i, btn in enumerate(radio_buttons):
            box = btn.bounding_box()
            print(f"  Radio {i}: box={box}")
        
        # Check if radio buttons overlap with filter bar
        filter_bar = page.query_selector('.toolbar-filters')
        if filter_bar:
            fb_box = filter_bar.bounding_box()
            print(f"  Filter bar: box={fb_box}")
            for i, btn in enumerate(radio_buttons):
                box = btn.bounding_box()
                if box and fb_box:
                    # Check vertical overlap
                    if not (box['y'] + box['height'] <= fb_box['y'] or fb_box['y'] + fb_box['height'] <= box['y']):
                        print(f"  OVERLAP: Radio {i} and filter bar overlap vertically!")
        
        # Check toolbar actions
        actions = page.query_selector_all('.toolbar-actions .n-button')
        for i, btn in enumerate(actions):
            box = btn.bounding_box()
            print(f"  Action button {i}: box={box}")
        
        # Test at 768px width  
        page.set_viewport_size({"width": 768, "height": 800})
        time.sleep(1)
        page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/issue4_app_center_768.png', full_page=True)
        
        # Issue 5: Check market panel at small screen
        page.goto(f"{BASE}/app-center?view=MARKET")
        page.wait_for_load_state('networkidle')
        time.sleep(1)
        page.set_viewport_size({"width": 500, "height": 800})
        time.sleep(0.5)
        page.screenshot(path='/Users/yaomindong/Desktop/project/mdframe/forge-workpace/forge-project/var/tmp/issue5_market_small.png', full_page=True)
        
        # Check market toolbar
        toolbar = page.query_selector('.market-toolbar')
        if toolbar:
            tb_box = toolbar.bounding_box()
            print(f"Market toolbar box: {tb_box}")
        
        tabs_el = page.query_selector_all('.market-toolbar .n-tabs .n-tabs-tab')
        print(f"Market tabs found: {len(tabs_el)}")
        for i, tab in enumerate(tabs_el):
            box = tab.bounding_box()
            print(f"  Tab {i}: box={box}")
        
        search = page.query_selector('.market-toolbar .n-input')
        if search:
            si_box = search.bounding_box()
            print(f"  Search input: box={si_box}")
        
        browser.close()

if __name__ == '__main__':
    main()
