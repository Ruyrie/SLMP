import { chromium } from 'playwright'

const base = 'http://8.208.12.217'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
let step = 'login'
const errors = []
const netFail = []
page.on('console', (m) => { if (m.type() === 'error') errors.push(`${step}: ${m.text().slice(0, 160)}`) })
page.on('pageerror', (e) => errors.push(`${step}: pageerror ${String(e).slice(0, 160)}`))
page.on('response', (r) => { if (r.status() >= 400) netFail.push(`${step}: ${r.status()} ${r.url().replace(base, '').slice(0, 100)}`) })

await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForTimeout(5000)

const paths = [
  '/', '/calendar', '/messages', '/herd/overview', '/herd/pens', '/herd/batches', '/herd/entries',
  '/inspect/today', '/inspect/history', '/inspect/reports', '/feeding/tasks', '/feeding/plans', '/feeding/weights',
  '/health/immune', '/health/medication', '/health/disinfect', '/stock/feed', '/stock/drugs',
  '/finance/overview', '/finance/records', '/finance/sales', '/finance/settlements', '/finance/market',
  '/finance/reports', '/breeding/animals', '/breeding/pending', '/breeding/incubation',
  '/ai', '/courses', '/farms', '/farms/1001', '/farms/1001/members', '/account', '/change-requests',
  '/workbench/staff'
]
const results = []
for (const path of paths) {
  step = path
  errors.length = 0
  netFail.length = 0
  await page.goto(base + path, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForTimeout(1800)
  const info = await page.evaluate(() => {
    const main = document.querySelector('main') || document.body
    const txt = (main.innerText || '').trim()
    return {
      path: location.pathname,
      title: document.title,
      textLen: txt.length,
      blank: txt.length < 20,
      bodyStart: txt.slice(0, 60).replace(/\s+/g, ' '),
      icons: document.querySelectorAll('.zw-mso').length,
      imgs: document.images.length,
      brokenImgs: [...document.images].filter((i) => !(i.complete && i.naturalWidth > 0)).length
    }
  })
  results.push({ ...info, errors: [...new Set(errors)].slice(0, 3), http: [...new Set(netFail)].slice(0, 4) })
}
// 员工身份
step = 'staff-login'
await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded' })
await page.evaluate(() => localStorage.clear())
await page.waitForTimeout(500)
await page.getByPlaceholder('请输入手机号').fill('13700005678')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForTimeout(5000)
for (const path of ['/workbench/staff', '/feeding/tasks', '/inspect/today', '/health/disinfect', '/stock/drugs']) {
  step = `staff ${path}`
  errors.length = 0; netFail.length = 0
  await page.goto(base + path, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1800)
  const info = await page.evaluate(() => {
    const txt = (document.querySelector('main') || document.body).innerText.trim()
    return { path: location.pathname, textLen: txt.length, blank: txt.length < 20, bodyStart: txt.slice(0, 50).replace(/\s+/g, ' ') }
  })
  results.push({ ...info, errors: [...new Set(errors)].slice(0, 3), http: [...new Set(netFail)].slice(0, 4) })
}
console.log(JSON.stringify({ total: results.length, blank: results.filter((r) => r.blank).length, withErrors: results.filter((r) => r.errors.length || r.http.length).map((r) => ({ path: r.path, errors: r.errors, http: r.http })), results: results.map((r) => `${r.path} len=${r.textLen} icons=${r.icons ?? '-'} ${r.bodyStart}`) }, null, 1))
await browser.close()
