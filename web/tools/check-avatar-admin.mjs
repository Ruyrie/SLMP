// 管理端头像显示检查：用户上传头像后，管理端用户列表 / 用户详情 / 养殖场成员都显示图片。
// 用法：node tools/check-avatar-admin.mjs [用户端地址] [管理端地址] [截图目录]
// 本机验证码从 Redis 读取；超级管理员首次登录按提示改密码（只在本机库）。
import { chromium } from 'playwright'
import { execSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'

const [, , userBase = 'http://localhost:5173', adminBase = 'http://localhost:5184', outDir = 'screenshots/cloud-fix/user'] = process.argv
const redis = process.env.REDIS_CLI || 'D:/Android_Program/SLMP/server/tools/redis/redis-cli.exe'
const NEW_PASSWORD = process.env.ADMIN_NEW_PASSWORD || 'Zmt12345abc'
mkdirSync(outDir, { recursive: true })

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const problems = []
const steps = []

// 1. 王建国上传头像
const userPage = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const imgPage = await browser.newPage({ viewport: { width: 120, height: 120 } })
await imgPage.setContent('<body style="margin:0"><div style="width:120px;height:120px;background:#2a9d5c"></div></body>')
await imgPage.screenshot({ path: `${outDir}/_avatar-green.png` })
await userPage.goto(`${userBase}/login`, { waitUntil: 'networkidle' })
await userPage.getByPlaceholder('请输入手机号').fill('13800006620')
await userPage.getByPlaceholder('请输入密码').fill('Zmt12345')
await userPage.getByRole('button', { name: '登录' }).click()
await userPage.waitForURL((url) => !url.pathname.startsWith('/login'))
await userPage.goto(`${userBase}/account`, { waitUntil: 'networkidle' })
const bound = userPage.waitForResponse((r) => r.url().includes('/api/v1/account/avatar') && r.request().method() === 'PUT')
await userPage.setInputFiles('input.a04-file', `${outDir}/_avatar-green.png`)
await bound

// 2. 管理端登录（必要时完成首次改密）
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
page.on('console', (m) => { if (m.type() === 'error') problems.push(`console: ${m.text().slice(0, 160)}`) })
page.on('pageerror', (e) => problems.push(`pageerror: ${String(e).slice(0, 160)}`))
page.on('response', (r) => { if (r.status() >= 400 && r.url().includes('/zmt-private/')) problems.push(`${r.status()} ${r.url().split('?')[0]}`) })

async function adminLogin(password) {
  const captcha = page.waitForResponse((r) => r.url().includes('/admin/v1/auth/captcha'))
  await page.goto(`${adminBase}/login`, { waitUntil: 'domcontentloaded' })
  const id = (await (await captcha).json()).data.captcha_id
  const code = execSync(`"${redis}" -h 127.0.0.1 -p 6379 GET admin:captcha:${id}`).toString().trim()
  await page.getByPlaceholder('请输入管理员账号').fill('zhoumin')
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByPlaceholder('请输入右侧字符').fill(code)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForTimeout(2500)
}

await adminLogin('Zmt12345')
if (await page.getByPlaceholder('请输入新密码').isVisible().catch(() => false)) {
  await page.getByPlaceholder('请输入当前密码').fill('Zmt12345')
  await page.getByPlaceholder('请输入新密码').fill(NEW_PASSWORD)
  await page.getByPlaceholder('再次输入新密码').fill(NEW_PASSWORD)
  await page.getByPlaceholder('再次输入新密码').press('Enter')
  await page.waitForTimeout(2500)
  await adminLogin(NEW_PASSWORD)
} else if (page.url().includes('/login')) {
  await adminLogin(NEW_PASSWORD)
}

async function check(where, path, shot) {
  await page.goto(`${adminBase}${path}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const list = await page.$$eval('.zw-avatar', (nodes) => nodes
    .filter((n) => n.getAttribute('aria-label') === '王建国的头像')
    .map((n) => { const img = n.querySelector('img'); return img ? `${img.complete && img.naturalWidth > 0 ? 'loaded' : 'BROKEN'} ${img.getAttribute('src').split('?')[0]}` : `text ${n.textContent.trim()}` }))
  steps.push({ where, ok: list.length > 0 && list.every((x) => x.startsWith('loaded')), found: list })
  await page.screenshot({ path: `${outDir}/${shot}.png` })
}

await check('管理端用户列表 + 详情', '/system/users?id=10001', 'avatar-06-admin-users')
await check('管理端养殖场详情成员', '/system/farms?id=1001', 'avatar-07-admin-farm-members')

console.log(JSON.stringify({ adminUrl: page.url(), steps, problems }, null, 2))
await browser.close()
