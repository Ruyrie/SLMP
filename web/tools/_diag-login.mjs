import { chromium } from 'playwright'

const base = 'http://8.208.12.217'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const net = []
page.on('response', async (r) => {
  const u = r.url()
  if (!u.includes('/api/v1/') && !u.includes('/admin/v1/')) return
  if (!u.includes('auth')) return
  let body = ''
  try { body = (await r.text()).slice(0, 400) } catch {}
  net.push({ url: u, status: r.status(), body, setCookie: (r.headers()['set-cookie'] || '').slice(0, 200) })
})
page.on('console', (m) => { if (m.type() === 'error') net.push({ console: m.text().slice(0, 300) }) })
page.on('pageerror', (e) => net.push({ pageerror: String(e).slice(0, 300) }))

await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(6000)
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/diag-login-before.png' })

const iconInfo = await page.evaluate(async () => {
  await document.fonts.ready
  const el = document.querySelector('.zw-mso')
  const loaded = document.fonts.check('16px "Material Symbols Outlined"')
  const faces = [...document.fonts].map((f) => ({ family: f.family, status: f.status, weight: f.weight }))
  const cs = el ? getComputedStyle(el) : null
  return {
    hasIconEl: !!el,
    iconText: el ? el.textContent : null,
    fontFamily: cs ? cs.fontFamily : null,
    fontFeature: cs ? cs.fontFeatureSettings : null,
    fontLoaded: loaded,
    faces,
    fontRequest: performance.getEntriesByType('resource').filter((r) => r.name.includes('woff')).map((r) => ({ name: r.name, size: r.transferSize, duration: Math.round(r.duration) }))
  }
})

await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForTimeout(4000)
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/diag-login-after.png' })

const cookies = await page.context().cookies()
const alert = await page.evaluate(() => {
  const a = document.querySelector('.v-alert, .zw-alert, [role="alert"]')
  return a ? a.textContent.trim().slice(0, 200) : null
})
console.log(JSON.stringify({ url: page.url(), alert, iconInfo, cookies: cookies.map((c) => ({ name: c.name, secure: c.secure, httpOnly: c.httpOnly, sameSite: c.sameSite, domain: c.domain })), net }, null, 2))
await browser.close()
