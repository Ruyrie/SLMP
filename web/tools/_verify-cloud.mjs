import { chromium } from 'playwright'

const base = 'http://8.208.12.217'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
const errors = []
const netFail = []
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 200)) })
page.on('pageerror', (e) => errors.push('pageerror: ' + String(e).slice(0, 200)))
page.on('response', (r) => { if (r.status() >= 400) netFail.push(`${r.status()} ${r.url().slice(0, 120)}`) })

const t0 = Date.now()
await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(2500)
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/diag/verify-login.png', timeout: 20000 })

const iconState = await page.evaluate(async () => {
  await document.fonts.ready
  const el = document.querySelector('.zw-mso')
  const r = performance.getEntriesByType('resource').filter((x) => x.name.includes('woff2')).map((x) => ({ name: x.name.split('/').pop(), size: x.transferSize, ms: Math.round(x.duration) }))
  return { fontLoaded: document.fonts.check('16px "Material Symbols Outlined"'), firstIcon: el && el.textContent, sampleWidth: el && Math.round(el.getBoundingClientRect().width), fonts: r }
})

await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForTimeout(6000)
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/diag/verify-workbench.png', timeout: 20000 })

const pageInfo = await page.evaluate(() => {
  const icons = [...document.querySelectorAll('.zw-mso')].slice(0, 12).map((e) => e.textContent.trim())
  const iconWidths = [...document.querySelectorAll('.zw-mso')].slice(0, 12).map((e) => Math.round(e.getBoundingClientRect().width))
  const imgs = [...document.images].map((i) => ({ src: i.currentSrc.slice(0, 100), ok: i.complete && i.naturalWidth > 0 }))
  return { url: location.pathname, icons, iconWidths, imgs }
})

console.log(JSON.stringify({ loadMs: Date.now() - t0, iconState, pageInfo, errors: errors.slice(0, 10), netFail: netFail.slice(0, 10) }, null, 1))
await browser.close()
