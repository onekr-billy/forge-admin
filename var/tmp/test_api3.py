"""Check designStatus via page.evaluate."""
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
        
        # Navigate to app center to ensure token is set
        page.goto(f"{BASE}/app-center")
        page.wait_for_load_state('networkidle')
        time.sleep(2)
        
        # Use page.evaluate to fetch with credentials
        result = page.evaluate("""
            async () => {
                // Try to get token from various sources
                let token = '';
                for (const key of ['token', 'ACCESS_TOKEN', 'access_token', 'Token']) {
                    const val = localStorage.getItem(key);
                    if (val) { token = val; break; }
                }
                if (!token) {
                    // Try all localStorage keys
                    const keys = Object.keys(localStorage);
                    return { error: 'no token found', keys: keys };
                }
                
                const res = await fetch('/dev-api/ai/business/application/page?pageNum=1&pageSize=5', {
                    headers: { 'Authorization': 'Bearer ' + token }
                });
                return await res.json();
            }
        """)
        
        if 'error' in result:
            print(f"Error: {result['error']}")
            print(f"localStorage keys: {result.get('keys', [])}")
            # Try using cookies instead
            result2 = page.evaluate("""
                async () => {
                    const res = await fetch('/dev-api/ai/business/application/page?pageNum=1&pageSize=5', {
                        credentials: 'include'
                    });
                    return await res.json();
                }
            """)
            result = result2
        
        records = result.get('data', {}).get('records', []) if isinstance(result.get('data'), dict) else []
        if not records:
            print(f"Response: {str(result)[:300]}")
        else:
            for r in records[:5]:
                print(f"  {r.get('applicationName')}: designStatus={r.get('designStatus')}, lastPublishVersion={r.get('lastPublishVersion')}, pageCount={r.get('pageCount')}")
        
        browser.close()

if __name__ == '__main__':
    main()
