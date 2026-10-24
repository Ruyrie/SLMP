import { chromium } from 'playwright'
import { execSync } from 'node:child_process'

const redis = 'D:/Android_Program/SLMP/server/tools/redis/redis-cli.exe'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const logs = []
const failures = []
page.on('console', (message) => {
  if (message.type() === 'error' || message.type() === 'warning') logs.push(`${message.type()}: ${message.text()}`)
})
page.on('pageerror', (error) => logs.push(`pageerror: ${String(error)}`))
page.on('response', (response) => {
  if (response.status() >= 400) failures.push(`${response.status()} ${response.url()}`)
})

const captchaResponse = page.waitForResponse((response) => response.url().includes('/admin/v1/auth/captcha'))
await page.goto('http://127.0.0.1:5174/login', { waitUntil: 'domcontentloaded' })
const response = await captchaResponse
const payload = await response.json()
const captchaId = payload.data.captcha_id
const code = execSync(`"${redis}" -h 127.0.0.1 -p 6379 GET admin:captcha:${captchaId}`).toString().trim()

await page.getByPlaceholder('请输入管理员账号').fill('zhoumin')
await page.getByPlaceholder('请输入密码').fill('Zmt12345')
await page.getByPlaceholder('请输入右侧字符').fill(code)
await page.getByRole('button', { name: '登录', exact: true }).click()
await page.waitForTimeout(2500)
console.log('url after login:', page.url())
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/admin-home.png' })

await page.goto('http://127.0.0.1:5174/system/admins', { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
console.log('url logs:', page.url())
console.log('text:', (await page.locator('body').innerText()).slice(0, 600))
console.log('failures:', JSON.stringify(failures.slice(0, 10), null, 2))
console.log('logs:', JSON.stringify(logs.slice(0, 10), null, 2))
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/admin-admins.png' })
await browser.close()
