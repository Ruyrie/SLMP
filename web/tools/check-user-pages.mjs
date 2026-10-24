// 用户端逐页检查：控制台错误、接口 4xx/5xx、空白页、跳转到登录或循环跳转，并截图。
// 用法：node tools/check-user-pages.mjs [手机号] [用户端地址] [截图目录]
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const [, , phone = '13800006620', base = 'http://localhost:5173', outDir = 'screenshots/cloud-fix/user'] = process.argv
const routes = (process.env.ROUTES || [
  '/', '/workbench/staff', '/calendar', '/messages', '/farms', '/farms/new', '/farms/1001', '/farms/1001/members',
  '/herd/overview', '/herd/pens', '/herd/batches', '/herd/batches/3001', '/herd/entries',
  '/breeding/animals', '/breeding/pending', '/breeding/incubation',
  '/feeding/tasks', '/feeding/plans', '/feeding/weights',
  '/inspect/today', '/inspect/history', '/inspect/reports', '/inspect/reports/6006',
  '/health/immune', '/health/medication', '/health/disinfect',
  '/stock/feed', '/stock/drugs', '/stock/lots/7001',
  '/finance/overview', '/finance/records', '/finance/sales', '/finance/sales/9106',
  '/finance/settlements', '/finance/market', '/finance/reports',
  '/ai', '/knowledge/8101', '/courses', '/courses/6102', '/change-requests', '/account'
].join(',')).split(',')
mkdirSync(outDir, { recursive: true })

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
let current = 'login'
let navigations = 0
const rows = new Map()
const row = (route) => {
  if (!rows.has(route)) rows.set(route, { route, final: '', errors: [], http: [], blank: false, navs: 0 })
  return rows.get(route)
}
page.on('console', (m) => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) row(current).errors.push(m.text().slice(0, 180)) })
page.on('pageerror', (e) => row(current).errors.push(`pageerror ${String(e).slice(0, 180)}`))
page.on('response', (r) => {
  const url = r.url()
  if (r.status() >= 400 && !url.includes('/@') && !url.endsWith('.map')) {
    const u = new URL(url)
    row(current).http.push(`${r.status()} ${r.request().method()} ${u.pathname}`)
  }
})
page.on('framenavigated', (f) => { if (f === page.mainFrame()) navigations++ })

await page.goto(`${base}/login`, { waitUntil: 'networkidle' })
await page.getByPlaceholder('请输入手机号').fill(phone)
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 }).catch(() => undefined)
await page.waitForLoadState('networkidle')

for (const route of routes) {
  current = route
  const r = row(route)
  navigations = 0
  const before = Date.now()
  // SPA 内跳转（与点菜单一致），每 8 个页面整页刷新一次，覆盖刷新令牌恢复会话
  if (routes.indexOf(route) % 8 === 7) {
    await page.goto(`${base}${route}`, { waitUntil: 'networkidle' }).catch((e) => r.errors.push(String(e).slice(0, 120)))
  } else {
    await page.evaluate((target) => {
      window.history.pushState({}, '', target)
      window.dispatchEvent(new PopStateEvent('popstate'))
    }, route)
    await page.waitForLoadState('networkidle').catch(() => undefined)
  }
  await page.waitForTimeout(1800)
  r.final = new URL(page.url()).pathname + new URL(page.url()).search
  r.navs = navigations
  r.ms = Date.now() - before
  const main = await page.locator('main, .zw-main, .v-main').first().innerText().catch(() => '')
  r.blank = main.replace(/\s+/g, '').length < 20
  r.emptyHints = await page.locator('.zw-empty').allInnerTexts().then((list) => list.map((t) => t.replace(/\s+/g, ' ').slice(0, 60))).catch(() => [])
  const name = route.replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_|_$/g, '') || 'root'
  await page.screenshot({ path: `${outDir}/${phone}-${name}.png` })
}

for (const r of rows.values()) {
  const flags = []
  if (r.final && r.final.split('?')[0] !== r.route && r.route !== 'login') flags.push(`-> ${r.final}`)
  if (r.blank) flags.push('BLANK')
  if (r.navs > 3) flags.push(`navs=${r.navs}`)
  console.log(`${r.route}\t${flags.join(' ')}\n   http: ${[...new Set(r.http)].join(' | ') || '-'}\n   console: ${[...new Set(r.errors)].join(' | ') || '-'}\n   empty: ${(r.emptyHints || []).join(' | ') || '-'}`)
}
await browser.close()
