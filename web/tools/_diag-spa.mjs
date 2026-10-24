import { chromium } from 'playwright'

const base = 'http://8.208.12.217'
const routes = [
  '/', '/calendar', '/messages', '/farms', '/farms/1001', '/farms/1001/members',
  '/herd/overview', '/herd/pens', '/herd/batches', '/herd/batches/3001', '/herd/entries',
  '/breeding/animals', '/breeding/pending', '/breeding/incubation',
  '/feeding/tasks', '/feeding/plans', '/feeding/weights',
  '/inspect/today', '/inspect/history', '/inspect/reports',
  '/health/immune', '/health/medication', '/health/disinfect',
  '/stock/feed', '/stock/drugs',
  '/finance/overview', '/finance/records', '/finance/sales', '/finance/settlements', '/finance/market', '/finance/reports',
  '/ai', '/courses', '/change-requests', '/account'
]

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const problems = []
let current = ''
page.on('console', (m) => { if (m.type() === 'error') problems.push({ route: current, type: 'console', text: m.text().slice(0, 220) }) })
page.on('pageerror', (e) => problems.push({ route: current, type: 'pageerror', text: String(e).slice(0, 220) }))
page.on('response', (r) => { if (r.status() >= 400) problems.push({ route: current, type: 'http' + r.status(), text: r.url().replace(base, '').slice(0, 160) }) })

await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(2000)
await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => undefined)
await page.waitForTimeout(3000)

const summaries = []
for (const route of routes) {
  current = route
  const before = problems.length
  await page.evaluate((t) => { window.history.pushState({}, '', t); window.dispatchEvent(new PopStateEvent('popstate')) }, route)
  await page.waitForTimeout(2800)
  const info = await page.evaluate(() => {
    const txt = document.body.innerText
    const empty = /暂无数据|暂无记录|还没有|空空如也|加载失败|请求失败/.test(txt)
    const skeleton = document.querySelectorAll('.v-skeleton-loader').length
    const h = document.querySelector('h1, .zw-page-title')
    return { title: h ? h.textContent.trim().slice(0, 40) : '', empty, skeleton, len: txt.length }
  })
  summaries.push({ route, ...info, problems: problems.length - before })
  if (info.empty || info.skeleton > 2 || problems.length > before) {
    await page.screenshot({ path: `D:/Android_Program/SLMP/logs/diag/route-${route.replace(/[^a-z0-9]+/gi, '_')}.png`, timeout: 20000 }).catch(() => undefined)
  }
}

console.log(JSON.stringify({ summaries, problems: problems.slice(0, 40) }, null, 1))
await browser.close()
