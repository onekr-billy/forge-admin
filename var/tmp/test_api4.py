"""Check designStatus using prod_auth token."""
import time
from playwright.sync_api import sync_playwright

BASE = "http://localhost:3000"

def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1280, "height": 800})
        page = context.new_page()
        
        page.goto(f"{BASE}/login")
        page.wait_for_load_state('networkidle')
        page.fill('input[placeholder*="用户名"], input[placeholder*="账号"]', 'admin')
        page.fill('input[type="password"]', '123456')
        page.click('button[type="submit"], button:has-text("登录"), button:has-text("登 录")')
        page.wait_for_load_state('networkidle')
        time.sleep(2)
        
        page.goto(f"{BASE}/app-center")
        page.wait_for_load_state('networkidle')
        time.sleep(2)
        
        result = page.evaluate("""
            async () => {
                const authRaw = localStorage.getItem('prod_auth');
                let token = '';
                if (authRaw) {
                    try { token = JSON.parse(authRaw).token || JSON.parse(authRaw).access_token || authRaw; }
                    catch(e) { token = authRaw; }
                }
                
                const res = await fetch('/dev-api/ai/business/application/page?pageNum=1&pageSize=5', {
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                return await res.json();
            }
        """)
        
        records = result.get('data', {}).get('records', []) if isinstance(result.get('data'), dict) else []
        for r in records[:5]:
            print(f"  {r.get('applicationName')}: designStatus={r.get('designStatus')}, lastPublishVersion={r.get('lastPublishVersion')}, pageCount={r.get('pageCount')}")
        
        browser.close()

if __name__ == '__main__':
    main()
