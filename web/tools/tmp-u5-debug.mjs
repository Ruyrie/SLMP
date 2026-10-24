import { chromium } from 'playwright'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
page.on('response', async (res) => { if (res.url().includes('/api/') && !res.url().includes('/src/')) console.log('RESP', res.status(), res.url().replace('http://127.0.0.1:5173',''), (await res.text().catch(()=>'' )).slice(0,220)) })
page.on('console', (m) => { if (m.type() !== 'debug' && m.type() !== 'verbose') console.log('CONSOLE', m.type(), m.text()) })
await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
for (let i = 0; i < 3; i++) {
  await page.getByPlaceholder('请输入手机号').fill('13800006620')
  await page.getByPlaceholder('请输入密码').fill('Zmt12345')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForTimeout(3500)
  console.log('after login url', page.url())
  if (!page.url().includes('/login')) break
}
await page.goto('http://127.0.0.1:5173/breeding/animals', { waitUntil: 'networkidle' })
await page.waitForTimeout(4000)
console.log('url', page.url())
const content = page.locator('.zw-content')
console.log('count', await content.count())
if (await content.count()) console.log('text', (await content.innerText()).slice(0,900))
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/u5-b01-debug.png' })
await browser.close()
