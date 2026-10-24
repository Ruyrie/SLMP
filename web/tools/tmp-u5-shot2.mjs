import { chromium } from 'playwright'
const path = process.argv[2] || '/'
const output = process.argv[3] || 'D:/Android_Program/SLMP/logs/u5-shot.png'
const phone = process.argv[4] || '13800006620'
const password = 'Zmt12345'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()
const logs = []
page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') logs.push(`${m.type()}: ${m.text()}`) })
page.on('pageerror', (e) => logs.push(`pageerror: ${String(e)}`))
const res = await context.request.post('http://127.0.0.1:8080/api/v1/auth/login/password', {
  headers: { 'X-Client': 'WEB' },
  data: { phone, password, device_id: 'u5-shot' }
})
console.log('login status', res.status(), (await res.text()).slice(0, 120))
await page.goto(`http://127.0.0.1:5173${path}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(3500)
await page.screenshot({ path: output })
console.log('url:', page.url())
console.log('screenshot:', output)
if (logs.length) console.log('console:', JSON.stringify(logs.slice(0, 15), null, 2))
await browser.close()
