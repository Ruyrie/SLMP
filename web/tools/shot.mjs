import { chromium } from 'playwright'

const [, , path = '/', output = 'D:/Android_Program/SLMP/logs/shot.png', phone = '13800006620'] = process.argv
const base = process.env.ZMT_WEB_BASE || 'http://127.0.0.1:5173'
const password = process.env.ZMT_PASSWORD || 'Zmt12345'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const logs = []
page.on('console', (message) => {
  if (message.type() === 'error' || message.type() === 'warning') logs.push(`${message.type()}: ${message.text()}`)
})
page.on('pageerror', (error) => logs.push(`pageerror: ${String(error)}`))

await page.goto(`${base}/login`, { waitUntil: 'networkidle' })
await page.getByPlaceholder('请输入手机号').fill(phone)
await page.getByPlaceholder('请输入密码').fill(password)
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 }).catch(() => undefined)
await page.waitForTimeout(1500)
const currentPath = new URL(page.url()).pathname
if (currentPath !== path) {
  await page.goto(`${base}${path}`, { waitUntil: 'networkidle' })
}
await page.waitForTimeout(1200)
await page.screenshot({ path: output, fullPage: false })
console.log('url:', page.url())
console.log('screenshot:', output)
if (logs.length) console.log('console:', JSON.stringify(logs.slice(0, 15), null, 2))
await browser.close()
