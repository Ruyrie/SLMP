import { chromium } from 'playwright'
const path = process.argv[2] || '/breeding/pending'
const output = process.argv[3] || 'D:/Android_Program/SLMP/logs/u5-shot.png'
const phone = process.argv[4] || '13800006620'
const password = 'Zmt12345'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const logs = []
page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') logs.push(`${m.type()}: ${m.text()}`) })
page.on('pageerror', (e) => logs.push(`pageerror: ${String(e)}`))
await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
let ok = false
for (let i = 0; i < 5 && !ok; i++) {
  await page.getByPlaceholder('请输入手机号').fill(phone)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForTimeout(3500)
  ok = !page.url().includes('/login')
  if (!ok) await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
}
if (!ok) { console.log('LOGIN FAILED'); await page.screenshot({ path: output }); await browser.close(); process.exit(2) }
await page.goto(`http://127.0.0.1:5173${path}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(3500)
await page.screenshot({ path: output })
console.log('url:', page.url())
console.log('screenshot:', output)
if (logs.length) console.log('console:', JSON.stringify(logs.slice(0, 15), null, 2))
await browser.close()
