import { chromium } from 'playwright'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()
const events = []
page.on('response', async (res) => {
  if (res.url().includes('/auth/')) {
    events.push({ url: res.url(), status: res.status(), setCookie: (await res.headersArray()).filter((h) => h.name.toLowerCase() === 'set-cookie').map((h) => h.value.slice(0, 120)) })
  }
})
await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForTimeout(2500)
console.log('cookies after login:', (await context.cookies()).map((c) => ({ name: c.name, path: c.path, expires: c.expires })))
console.log('events login:', JSON.stringify(events, null, 2))
events.length = 0
await page.goto('http://127.0.0.1:5173/', { waitUntil: 'networkidle' })
await page.waitForTimeout(1000)
console.log('url:', page.url())
console.log('cookies after /:', (await context.cookies()).map((c) => ({ name: c.name, path: c.path, expires: c.expires })))
console.log('events /:', JSON.stringify(events, null, 2))
await browser.close()
