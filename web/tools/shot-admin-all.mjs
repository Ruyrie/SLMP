import { chromium } from 'playwright'
import { execSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'

const outDir = 'D:/Android_Program/SLMP/web/screenshots/admin'
const base = 'http://127.0.0.1:5174'
const redis = 'D:/Android_Program/SLMP/server/tools/redis/redis-cli.exe'
const routes = [
  '/', '/audit/farms', '/audit/changes', '/audit/change-requests', '/audit/recoveries',
  '/content/notices', '/content/market', '/content/courses', '/content/knowledge', '/content/ai-qa',
  '/base/categories', '/base/immune-templates', '/base/drug-catalog', '/base/inspect-templates', '/base/dicts',
  '/system/users', '/system/farms', '/system/admins', '/system/logs'
]
mkdirSync(outDir, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()
const problems = []
let current = ''
page.on('console', (message) => {
  if (message.type() === 'error' || message.type() === 'warning') problems.push({ route: current, type: message.type(), text: message.text().slice(0, 240) })
})
page.on('pageerror', (error) => problems.push({ route: current, type: 'pageerror', text: String(error).slice(0, 240) }))

const captchaResponse = page.waitForResponse((r) => r.url().includes('/admin/v1/auth/captcha'))
await page.goto(`${base}/login`, { waitUntil: 'networkidle' })
const captcha = await (await captchaResponse).json()
const code = execSync(`"${redis}" -h 127.0.0.1 -p 6379 GET admin:captcha:${captcha.data.captcha_id}`).toString().trim()
await page.getByPlaceholder('请输入管理员账号').fill('zhoumin')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByPlaceholder('请输入右侧字符').fill(code)
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForTimeout(2500)

for (const route of routes) {
  current = route
  try {
    await page.evaluate((target) => {
      window.history.pushState({}, '', target)
      window.dispatchEvent(new PopStateEvent('popstate'))
    }, route)
    await page.waitForTimeout(2200)
    const name = route.replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_|_$/g, '') || 'root'
    await page.screenshot({ path: `${outDir}/${name}.png` })
    if (new URL(page.url()).pathname === '/login') problems.push({ route, type: 'auth', text: 'redirected to login' })
  } catch (error) {
    problems.push({ route, type: 'nav', text: String(error).slice(0, 240) })
  }
}
console.log(JSON.stringify({ count: routes.length, problems }, null, 2))
await browser.close()
