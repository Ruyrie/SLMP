import { chromium } from 'playwright'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const messages = []
page.on('console', (message) => {
  if (message.type() === 'error' || message.type() === 'warning') messages.push(`${message.type()}: ${message.text()}`)
})
page.on('pageerror', (error) => messages.push(`pageerror: ${String(error)}`))

await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/web-login-2.png' })
await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForTimeout(3000)
console.log('url:', page.url())
console.log('messages:', JSON.stringify(messages.slice(0, 20), null, 2))
await page.goto('http://127.0.0.1:5173/calendar', { waitUntil: 'networkidle' })
await page.waitForTimeout(1000)
console.log('calendar url:', page.url())
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/web-calendar.png' })
await browser.close()
