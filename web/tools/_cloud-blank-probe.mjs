import { chromium } from 'playwright'

const base = 'http://8.208.12.217'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
const logs = []
page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') logs.push(`${m.type()}: ${m.text().slice(0, 160)}`) })
page.on('pageerror', (e) => logs.push('pageerror: ' + String(e).slice(0, 200)))
await page.goto(`${base}/login`, { waitUntil: 'networkidle', timeout: 60000 })
await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForTimeout(5000)
for (const path of ['/inspect/today', '/health/disinfect', '/calendar', '/herd/batches', '/farms', '/breeding/animals']) {
  logs.length = 0
  await page.goto(base + path, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForTimeout(4500)
  const info = await page.evaluate(() => ({
    path: location.pathname,
    title: document.title,
    textLen: document.body.innerText.trim().length,
    text: document.body.innerText.trim().slice(0, 160).replace(/\s+/g, ' '),
    main: !!document.querySelector('main'),
    app: !!document.querySelector('#app'),
    htmlLen: document.querySelector('#app')?.innerHTML.length || 0,
    vt: !!document.querySelector('.v-main')
  }))
  console.log(JSON.stringify({ requested: path, ...info, logs: logs.slice(0, 5) }))
}
await browser.close()
