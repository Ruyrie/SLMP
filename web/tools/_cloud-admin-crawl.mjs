import { chromium } from 'playwright'

const base = 'http://8.208.12.217/admin'
const cookieValue = process.env.ADMIN_COOKIE
if (!cookieValue) throw new Error('ADMIN_COOKIE required')

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
await ctx.addCookies([{ name: 'ZMT_ADMIN_SESSION', value: cookieValue, domain: '8.208.12.217', path: '/admin/v1', httpOnly: true, sameSite: 'Strict' }])
const page = await ctx.newPage()
const errors = []
const netFail = []
let step = 'init'
page.on('console', (m) => { if (m.type() === 'error') errors.push(`${step}: ${m.text().slice(0, 160)}`) })
page.on('pageerror', (e) => errors.push(`${step}: pageerror ${String(e).slice(0, 160)}`))
page.on('response', (r) => { if (r.status() >= 400) netFail.push(`${step}: ${r.status()} ${r.url().replace('http://8.208.12.217', '').slice(0, 100)}`) })

const paths = ['/', '/audit/farms', '/audit/changes', '/audit/change-requests', '/audit/recoveries',
  '/content/notices', '/content/market', '/content/courses', '/content/knowledge', '/content/ai-qa',
  '/base/categories', '/base/immune-templates', '/base/drug-catalog', '/base/inspect-templates', '/base/dicts',
  '/system/users', '/system/farms', '/system/admins', '/system/logs']
const results = []
for (const path of paths) {
  step = path
  errors.length = 0; netFail.length = 0
  await page.goto(base + (path === '/' ? '/' : path), { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForTimeout(2500)
  const info = await page.evaluate(() => {
    const txt = document.body.innerText.trim()
    const icons = [...document.querySelectorAll('.zw-mso')]
    return {
      path: location.pathname,
      title: document.title,
      textLen: txt.length,
      bodyStart: txt.slice(0, 70).replace(/\s+/g, ' '),
      icons: icons.length,
      zeroIcons: icons.filter((e) => e.getBoundingClientRect().width < 8).length
    }
  })
  results.push({ ...info, errors: [...new Set(errors)].slice(0, 3), http: [...new Set(netFail)].slice(0, 4) })
}
console.log(JSON.stringify({
  total: results.length,
  blank: results.filter((r) => r.textLen < 20).length,
  badIcons: results.filter((r) => r.zeroIcons > 0).map((r) => r.path),
  issues: results.filter((r) => r.errors.length || r.http.length).map((r) => ({ path: r.path, errors: r.errors, http: r.http })),
  results: results.map((r) => `${r.path} len=${r.textLen} icons=${r.icons} ${r.bodyStart}`)
}, null, 1))
await browser.close()
