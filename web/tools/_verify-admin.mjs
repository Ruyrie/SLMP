import { chromium } from 'playwright'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const errs = []
page.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
page.on('response', (r) => { if (r.status() >= 400) errs.push(r.status() + ' ' + r.url().slice(0, 120)) })
await page.goto('http://8.208.12.217/admin/', { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(4000)
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/diag/verify-admin-login.png' })
const info = await page.evaluate(async () => {
  await document.fonts.ready
  return { title: document.title, font: document.fonts.check('16px "Material Symbols Outlined"'), icons: [...document.querySelectorAll('.zw-mso')].length }
})
console.log(JSON.stringify({ info, errs }, null, 1))
await browser.close()
