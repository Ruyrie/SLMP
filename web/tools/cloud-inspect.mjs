import { chromium } from 'playwright'

const base = 'http://8.208.12.217'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const logs = []
page.on('console', (m) => logs.push({ type: m.type(), text: m.text().slice(0, 300) }))
page.on('requestfailed', (r) => logs.push({ type: 'requestfailed', text: r.url().slice(0, 200) + ' ' + (r.failure()?.errorText || '') }))
await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(6000)
console.log('url=' + page.url())
console.log('title=' + await page.title())
console.log('fontCheck=' + await page.evaluate(() => document.fonts.check('16px "Material Symbols Outlined"')))
console.log('fontStatus=' + await page.evaluate(() => Array.from(document.fonts).map((f) => `${f.family}:${f.status}`).join(',')))
console.log('iconStyle=' + await page.evaluate(() => {
  const el = document.querySelector('.zw-mso')
  if (!el) return 'no .zw-mso'
  const s = getComputedStyle(el)
  return `font=${s.fontFamily}; size=${s.fontSize}; w=${s.width}; text=${el.textContent}`
}))
console.log('resources=' + JSON.stringify(await page.evaluate(() => performance.getEntriesByType('resource').filter((r) => r.name.includes('Material') || r.name.includes('.woff')).map((r) => ({ name: r.name.split('/').pop(), duration: Math.round(r.duration), size: r.transferSize })))))
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/cloud-login-font.png' })
console.log('logs=' + JSON.stringify(logs.slice(0, 20), null, 2))
await browser.close()
