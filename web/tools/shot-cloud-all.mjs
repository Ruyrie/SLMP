import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const [, , phone = '13800006620', outDir = 'D:/Android_Program/SLMP/logs/cloud-user'] = process.argv
const base = 'http://8.208.12.217'
const routes = [
  '/', '/calendar', '/messages', '/farms', '/farms/new', '/farms/1001', '/farms/1001/members',
  '/herd/overview', '/herd/pens', '/herd/batches', '/herd/batches/3001', '/herd/entries',
  '/breeding/animals', '/breeding/pending', '/breeding/incubation',
  '/feeding/tasks', '/feeding/plans', '/feeding/weights',
  '/inspect/today', '/inspect/history', '/inspect/reports', '/inspect/reports/6006',
  '/health/immune', '/health/medication', '/health/disinfect',
  '/stock/feed', '/stock/drugs', '/stock/lots/7001',
  '/finance/overview', '/finance/records', '/finance/sales', '/finance/sales/9106',
  '/finance/settlements', '/finance/market', '/finance/reports',
  '/ai', '/knowledge/8101', '/courses', '/courses/6102', '/change-requests', '/account'
]
mkdirSync(outDir, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const results = []
let current = ''
page.on('console', (message) => {
  if (message.type() === 'error' || message.type() === 'warning') results.push({ route: current, type: message.type(), text: message.text().slice(0, 200) })
})
page.on('pageerror', (error) => results.push({ route: current, type: 'pageerror', text: String(error).slice(0, 200) }))

await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(2500)
await page.getByPlaceholder('请输入手机号').fill(phone)
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 }).catch(() => undefined)
await page.waitForTimeout(2600)

for (const route of routes) {
  current = route
  try {
    await page.evaluate((target) => {
      window.history.pushState({}, '', target)
      window.dispatchEvent(new PopStateEvent('popstate'))
    }, route)
    await page.waitForTimeout(2600)
    const path = new URL(page.url()).pathname
    const name = route.replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_|_$/g, '') || 'root'
    await page.screenshot({ path: `${outDir}/${name}.png` })
    if (path === '/login') results.push({ route, type: 'auth', text: 'redirected to login' })
  } catch (error) {
    results.push({ route, type: 'nav', text: String(error).slice(0, 200) })
  }
}
console.log(JSON.stringify({ count: routes.length, problems: results }, null, 2))
await browser.close()
