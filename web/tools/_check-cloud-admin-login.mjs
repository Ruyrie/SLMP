import { chromium } from 'playwright'

const base = 'http://8.208.12.217/admin'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
const errors = []
const netFail = []
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 200)) })
page.on('pageerror', (e) => errors.push('pageerror: ' + String(e).slice(0, 200)))
page.on('response', (r) => { if (r.status() >= 400) netFail.push(`${r.status()} ${r.url().slice(0, 120)}`) })

await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(3000)
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/diag/cloud-admin-login.png', timeout: 20000 })
const info = await page.evaluate(async () => {
  await document.fonts.ready
  const icons = [...document.querySelectorAll('.zw-mso')]
  const captcha = [...document.images].find((i) => (i.currentSrc || i.src || '').startsWith('data:image'))
  return {
    url: location.pathname,
    title: document.title,
    iconCount: icons.length,
    iconWidths: icons.slice(0, 8).map((e) => Math.round(e.getBoundingClientRect().width)),
    fontLoaded: document.fonts.check('16px "Material Symbols Outlined"'),
    captchaReady: !!captcha && captcha.complete && captcha.naturalWidth > 0,
    bodyText: document.body.innerText.slice(0, 120)
  }
})
console.log(JSON.stringify({ info, errors: errors.slice(0, 8), netFail: netFail.slice(0, 8) }, null, 1))
await browser.close()
