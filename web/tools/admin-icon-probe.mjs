// 管理端页面探针：打开一个页面（默认云端登录页，只看不登录），记录控制台错误、失败请求、
// 字体响应头，并检查页面上每个图标是否渲染成字形。
// 用法：node tools/admin-icon-probe.mjs [url] [screenshot.png]
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname } from 'node:path'
import { probeIcons } from './_icon-probe-lib.mjs'

const url = process.argv[2] || 'http://8.208.12.217/admin/'
const shot = process.argv[3] || 'screenshots/cloud-fix/admin/cloud-login.png'

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const consoleErrors = []
const failed = []
const assets = []
page.on('console', (m) => {
  if (m.type() === 'error' || m.type() === 'warning') consoleErrors.push(`${m.type()}: ${m.text().slice(0, 240)}`)
})
page.on('pageerror', (e) => consoleErrors.push(`pageerror: ${String(e).slice(0, 240)}`))
page.on('requestfailed', (r) => failed.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`))
page.on('response', (r) => {
  if (r.status() >= 400) failed.push(`${r.status()} ${r.request().method()} ${r.url()}`)
  if (/\.(woff2?|js|css)(\?|$)/.test(r.url())) {
    const h = r.headers()
    assets.push({ url: r.url().replace(/^https?:\/\/[^/]+/, ''), status: r.status(), type: h['content-type'], length: h['content-length'], encoding: h['content-encoding'], cache: h['cache-control'] })
  }
})
const started = Date.now()
await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 })
const loadMs = Date.now() - started
await page.waitForTimeout(1500)
const icons = await probeIcons(page)
mkdirSync(dirname(shot), { recursive: true })
await page.screenshot({ path: shot })
console.log(JSON.stringify({ url, finalUrl: page.url(), title: await page.title(), loadMs, consoleErrors, failed, assets, icons }, null, 1))
await browser.close()
