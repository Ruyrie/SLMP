// 用户端顶栏交互：验证码登录（本机 mock 123456）→ 切换养殖场 → 全部养殖场 → 退出登录 → 密码登录回到工作台。
// 用法：node tools/check-user-shell.mjs [用户端地址] [截图目录]
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const [, , base = 'http://localhost:5173', outDir = 'screenshots/cloud-fix/user'] = process.argv
mkdirSync(outDir, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const problems = []
const steps = []
page.on('console', (m) => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) problems.push(m.text().slice(0, 160)) })
page.on('pageerror', (e) => problems.push(`pageerror ${String(e).slice(0, 160)}`))
page.on('response', (r) => { if (r.status() >= 500) problems.push(`${r.status()} ${new URL(r.url()).pathname}`) })
const step = async (name, check) => {
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(800)
  const value = await check()
  steps.push({ name, ok: !!value, value: typeof value === 'string' ? value : undefined })
}

await page.goto(`${base}/login`, { waitUntil: 'networkidle' })
await page.getByRole('tab', { name: '验证码登录' }).click()
await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByText('获取验证码').click()
await page.waitForTimeout(800)
await page.getByPlaceholder('6 位验证码').fill('123456')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 })
await step('验证码登录进入工作台', async () => new URL(page.url()).pathname === '/' && (await page.locator('.zw-farm').innerText()))

await page.locator('.zw-farm').click()
const other = page.locator('.zw-farm-item').filter({ hasNotText: '东山猪场' }).first()
const otherName = (await other.locator('.zw-farm-name').innerText()).trim()
await other.click()
await step(`切换到${otherName}`, async () => (await page.locator('.zw-farm').innerText()).includes(otherName) && (await page.locator('.zw-farm').innerText()))
await page.screenshot({ path: `${outDir}/shell-01-switch-farm.png` })

await page.goto(`${base}/calendar`, { waitUntil: 'networkidle' })
await page.locator('.zw-farm').click()
await page.locator('.zw-farm-all').click()
await step('养殖日历切换全部养殖场', async () => (await page.locator('.zw-farm').innerText()).includes('全部养殖场') && 'all')
await page.screenshot({ path: `${outDir}/shell-02-all-farms.png` })
await page.goto(`${base}/herd/pens`, { waitUntil: 'networkidle' })
await step('进入单场页面自动退出全部视角', async () => !(await page.locator('.zw-farm').innerText()).includes('全部养殖场') && (await page.locator('.zw-farm').innerText()))

await page.locator('.zw-farm').click()
await page.locator('.zw-farm-item').filter({ hasText: '东山猪场' }).first().click()
await page.locator('.zw-user').click()
await page.getByText('退出登录').click()
await step('退出登录回到登录页', async () => new URL(page.url()).pathname === '/login' && 'login')
await page.goto(`${base}/herd/pens`, { waitUntil: 'networkidle' })
await step('退出后访问业务页跳登录', async () => new URL(page.url()).pathname === '/login' && page.url())

await page.getByPlaceholder('请输入手机号').fill('13800006620')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 })
await step('密码登录后回到原页面', async () => new URL(page.url()).pathname === '/herd/pens' && page.url())

console.log(JSON.stringify({ steps, problems }, null, 2))
await browser.close()
