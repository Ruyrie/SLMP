import { chromium } from 'playwright'
import { execSync } from 'node:child_process'

const redis = 'D:/Android_Program/SLMP/server/tools/redis/redis-cli.exe'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const logs = []
page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') logs.push(`${m.type()}: ${m.text()}`) })
page.on('pageerror', (e) => logs.push(`pageerror: ${String(e)}`))

const captchaResponse = page.waitForResponse((r) => r.url().includes('/admin/v1/auth/captcha'))
await page.goto('http://127.0.0.1:5174/login', { waitUntil: 'domcontentloaded' })
const payload = await (await captchaResponse).json()
const code = execSync(`"${redis}" -h 127.0.0.1 -p 6379 GET admin:captcha:${payload.data.captcha_id}`).toString().trim()
await page.getByPlaceholder('请输入管理员账号').fill('zhoumin')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByPlaceholder('请输入右侧字符').fill(code)
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForTimeout(2000)
await page.goto('http://127.0.0.1:5174/system/admins', { waitUntil: 'networkidle' })
await page.getByRole('tab', { name: /角色与权限/ }).click()
await page.waitForTimeout(1800)
console.log('logs:', JSON.stringify(logs.slice(0, 10), null, 2))
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/admin-roles.png' })
await browser.close()
