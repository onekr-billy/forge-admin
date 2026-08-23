"""Capture all API responses to find the application list endpoint."""
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
        
        all_responses = []
        def handle_response(response):
            url = response.url
            if '/api/' in url or 'business' in url or 'application' in url.lower():
                try:
                    ct = response.headers.get('content-type', '')
                    if 'json' in ct:
                        body = response.json()
                        all_responses.append({'url': url, 'status': response.status, 'body': body})
                except:
                    pass
        
        page.on('response', handle_response)
        
        login(page)
        print("Logged in")
        time.sleep(1)
        
        page.goto(f"{BASE}/app-center")
        page.wait_for_load_state('networkidle')
        time.sleep(3)
        
        for resp in all_responses:
            url = resp['url']
            # Truncate URL for display
            short_url = url[:120]
            body = resp['body']
            # Check if this response has application data
            data = body.get('data', body)
            if isinstance(data, dict):
                records = data.get('records') or data.get('list') or []
            elif isinstance(data, list):
                records = data
            else:
                records = []
            
            if records and isinstance(records[0], dict) and 'applicationCode' in records[0]:
                print(f"\nFound app list API: {short_url}")
                for r in records[:3]:
                    print(f"  {r.get('applicationName')}: pageCount={r.get('pageCount')}, objectCount={r.get('objectCount')}, designStatus={r.get('designStatus')}")
            elif 'application' in url.lower():
                print(f"\nOther API: {short_url} (status={resp['status']})")
        
        browser.close()

if __name__ == '__main__':
    main()
