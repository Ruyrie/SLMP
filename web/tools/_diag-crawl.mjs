import { chromium } from 'playwright'
import { writeFileSync, mkdirSync } from 'node:fs'

const base = 'http://8.208.12.217'
const outDir = 'D:/Android_Program/SLMP/logs/diag'
mkdirSync(outDir, { recursive: true })

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
await page.route('**/*.woff2', (r) => r.abort())

const api = []
let current = ''
page.on('response', async (r) => {
  const u = r.url()
  if (!u.includes('/api/v1/')) return
  let json = null
  try { json = await r.json() } catch {}
  const path = new URL(u).pathname
  const data = json && json.data
  const empty = data == null || (Array.isArray(data) && data.length === 0) || (typeof data === 'object' && !Array.isArray(data) && Object.keys(data).length === 0)
  api.push({ route: current, path, query: new URL(u).search.slice(0, 120), status: r.status(), code: json && json.code, message: json && json.message, empty, size: data ? JSON.stringify(data).length : 0,
             listKeys: data && typeof data === 'object' && !Array.isArray(data) ? Object.entries(data).filter(([, v]) => Array.isArray(v)).map(([k, v]) => `${k}:${v.length}`) : undefined })
})

const icons = new Set()
const pages = []

await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(3000)
await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForTimeout(4000)
await page.screenshot({ path: `${outDir}/00-workbench.png`, timeout: 15000 }).catch((e) => console.log('shot fail', String(e).slice(0, 80)))

const collectIcons = async () => {
  try {
    const names = await page.evaluate(() => [...document.querySelectorAll('.zw-mso, .v-icon')].map((e) => e.textContent.trim()).filter((t) => t && t.length < 40))
    names.forEach((n) => icons.add(n))
  } catch {}
}

for (const route of routes) {
  current = route
  try {
    await page.goto(base + route, { waitUntil: 'domcontentloaded', timeout: 45000 })
    await page.waitForTimeout(3200)
    await collectIcons()
    const name = route.replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_|_$/g, '') || 'root'
    await page.screenshot({ path: `${outDir}/${name}.png`, timeout: 15000 }).catch(() => undefined)
    const title = await page.evaluate(() => {
      const h = document.querySelector('h1, .zw-page-title, .v-toolbar-title')
      return h ? h.textContent.trim().slice(0, 60) : ''
    })
    const emptyState = await page.evaluate(() => {
      const t = document.body.innerText
      return /暂无|没有数据|加载中|加载失败|出错了/.test(t)
    })
    pages.push({ route, url: page.url(), title, emptyState })
  } catch (e) {
    pages.push({ route, error: String(e).slice(0, 150) })
  }
}

writeFileSync(`${outDir}/api.json`, JSON.stringify(api, null, 2), 'utf-8')
writeFileSync(`${outDir}/pages.json`, JSON.stringify(pages, null, 2), 'utf-8')
writeFileSync(`${outDir}/icons.json`, JSON.stringify([...icons].sort(), null, 2), 'utf-8')
console.log(JSON.stringify({ icons: [...icons].sort(), pages: pages.filter((p) => p.emptyState || p.error) }, null, 2))
await browser.close()
