"""Verify pageCount in API response and check menu hierarchy."""
import time
import json
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
        
        # Capture API responses
        api_responses = []
        def handle_response(response):
            url = response.url
            if 'business-application/page' in url or 'business-application/list' in url:
                try:
                    body = response.json()
                    api_responses.append({'url': url, 'body': body})
                except:
                    pass
        
        page.on('response', handle_response)
        
        login(page)
        print("Logged in")
        
        # Navigate to app center
        page.goto(f"{BASE}/app-center")
        page.wait_for_load_state('networkidle')
        time.sleep(3)
        
        # Check API response
        for resp in api_responses:
            print(f"\nAPI: {resp['url']}")
            body = resp['body']
            records = body.get('data', {}).get('records', []) or body.get('data', {}).get('list', []) or []
            if not records and isinstance(body.get('data'), list):
                records = body['data']
            for r in records[:5]:
                name = r.get('applicationName', 'N/A')
                pc = r.get('pageCount', 'NOT_PRESENT')
                oc = r.get('objectCount', 'NOT_PRESENT')
                ds = r.get('designStatus', 'NOT_PRESENT')
                print(f"  {name}: pageCount={pc}, objectCount={oc}, designStatus={ds}")
        
        if not api_responses:
            print("No API responses captured!")
        
        browser.close()

if __name__ == '__main__':
    main()
