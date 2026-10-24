import { chromium } from 'playwright'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
page.on('console', (m) => { if (m.text().includes('U5 exec')) console.log('LOG', m.text()) })
page.on('request', (req) => { if (req.url().includes('/feed-executions')) console.log('REQ', req.url().replace('http://127.0.0.1:5173','')) })
await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
let ok=false
for (let i=0;i<6 && !ok;i++){ await page.getByPlaceholder('请输入手机号').fill('13800006620'); await page.getByPlaceholder('请输入密码').fill('Zmt12345'); await page.getByRole('button',{name:'登录'}).click(); await page.waitForTimeout(3000); ok=!page.url().includes('/login'); if(!ok) await page.goto('http://127.0.0.1:5173/login',{waitUntil:'networkidle'}) }
console.log('login ok', ok, page.url())
if(ok){ await page.goto('http://127.0.0.1:5173/feeding/plans?tab=exec', { waitUntil: 'networkidle' }); await page.waitForTimeout(4000) }
await browser.close()
