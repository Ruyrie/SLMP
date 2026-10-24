import { chromium } from 'playwright'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto('http://127.0.0.1:5174/login', { waitUntil: 'networkidle' })
const info = await page.evaluate(() => {
  const el = document.querySelector('.admin-feature .zw-mso')
  if (!el) return null
  const style = getComputedStyle(el)
  return { color: style.color, fontFamily: style.fontFamily, background: style.backgroundColor, outer: el.outerHTML.slice(0, 200) }
})
console.log(info)
await browser.close()
