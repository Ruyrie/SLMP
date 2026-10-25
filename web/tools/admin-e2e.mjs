// 管理端端到端检查：取验证码 → 错误验证码重试 → 种子管理员登录 → 首登强制改密 → 新密码重新登录
// → 逐个点击左侧菜单打开所有页面 → 刷新与深链接保持登录 → 退出。
// 每一步记录控制台错误、4xx/5xx、空白页、跳转次数和没有渲染成字形的图标。
//
// 用法（本机，验证码从本机 Redis 读取 admin:captcha:{id}）：
//   ADMIN_BASE=http://127.0.0.1:4174/admin node tools/admin-e2e.mjs   # 生产构建 + vite preview（/admin/ 子路径）
//   ADMIN_BASE=http://127.0.0.1:5174 node tools/admin-e2e.mjs         # 开发模式
// 可选：ADMIN_USER / ADMIN_PASSWORD（默认种子 zhoumin / Zmt12345）、ADMIN_NEW_PASSWORD、SHOT_DIR、REDIS_CLI
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { probeIcons } from './_icon-probe-lib.mjs'

const base = (process.env.ADMIN_BASE || 'http://127.0.0.1:4174/admin').replace(/\/$/, '')
if (!['127.0.0.1', 'localhost', '[::1]'].includes(new URL(base).hostname)) {
  throw new Error('This script changes a test password; ADMIN_BASE must use a local isolated test server.')
}
const basePath = new URL(base + '/').pathname.replace(/\/$/, '')
const user = process.env.ADMIN_USER || 'zhoumin'
const initialPassword = process.env.ADMIN_PASSWORD || 'Zmt12345'
const newPassword = process.env.ADMIN_NEW_PASSWORD || 'Zmt2026admin'
const shotDir = process.env.SHOT_DIR || 'output/playwright/admin/' + (basePath ? 'preview' : 'dev')
const redisCli = process.env.REDIS_CLI || 'D:/Android_Program/SLMP/server/tools/redis/redis-cli.exe'
const redisDatabase = process.env.REDIS_DATABASE || '0'

const menus = [
  { group: null, label: '平台概览', path: '/' },
  { group: '审核中心', label: '养殖场审核', path: '/audit/farms' },
  { group: '审核中心', label: '资料变更审核', path: '/audit/changes' },
  { group: '审核中心', label: '修改申请审核', path: '/audit/change-requests' },
  { group: '审核中心', label: '账号找回', path: '/audit/recoveries' },
  { group: '内容运营', label: '通知发布', path: '/content/notices' },
  { group: '内容运营', label: '市场行情发布', path: '/content/market' },
  { group: '内容运营', label: '养殖课堂管理', path: '/content/courses' },
  { group: '内容运营', label: '知识库管理', path: '/content/knowledge' },
  { group: '内容运营', label: 'AI 问答与更正', path: '/content/ai-qa' },
  { group: '基础数据', label: '品类品种与标准', path: '/base/categories' },
  { group: '基础数据', label: '免疫程序模板', path: '/base/immune-templates' },
  { group: '基础数据', label: '兽药疫苗目录', path: '/base/drug-catalog' },
  { group: '基础数据', label: '巡检项目模板', path: '/base/inspect-templates' },
  { group: '基础数据', label: '其他字典', path: '/base/dicts' },
  { group: '用户与系统', label: '用户账号', path: '/system/users' },
  { group: '用户与系统', label: '养殖场管理', path: '/system/farms' },
  { group: '用户与系统', label: '管理员与权限', path: '/system/admins' },
  { group: '用户与系统', label: '操作日志', path: '/system/logs' }
]

mkdirSync(shotDir, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()

let step = 'start'
let events = { console: [], http: [], navs: 0 }
const steps = []
page.on('console', (m) => {
  if (m.type() === 'error' || m.type() === 'warning') events.console.push(`${m.type()}: ${m.text().slice(0, 200)}`)
})
page.on('pageerror', (e) => events.console.push(`pageerror: ${String(e).slice(0, 200)}`))
page.on('requestfailed', (r) => {
  if (!r.url().includes('/messages/unread-count')) events.http.push(`FAILED ${r.method()} ${r.url().replace(base, '')} ${r.failure()?.errorText}`)
})
page.on('response', (r) => {
  if (r.status() >= 400) events.http.push(`${r.status()} ${r.request().method()} ${r.url().replace(/^https?:\/\/[^/]+/, '')}`)
})
page.on('framenavigated', (f) => {
  if (f === page.mainFrame()) events.navs++
})

const path = () => new URL(page.url()).pathname.slice(basePath.length) || '/'

async function record(name, extra = {}) {
  await page.waitForTimeout(300)
  const icons = await probeIcons(page)
  const contentText = await page.evaluate(() => (document.querySelector('.zw-content, .zw-auth, #app')?.textContent || '').trim().length)
  const notices = await page.evaluate(() =>
    [...document.querySelectorAll('.zw-notice--error, .zw-snack-content')].map((e) => (e.textContent || '').trim()).filter(Boolean)
  )
  const file = `${shotDir}/${String(steps.length).padStart(2, '0')}-${name.replace(/[^a-zA-Z0-9\u4e00-\u9fa5]+/g, '_')}.png`
  await page.screenshot({ path: file })
  const result = {
    step: name,
    path: path(),
    navs: events.navs,
    console: events.console,
    http: events.http,
    blank: contentText < 20,
    brokenIcons: icons.broken,
    iconCount: icons.total,
    notices,
    ...extra
  }
  steps.push(result)
  events = { console: [], http: [], navs: 0 }
  step = name
  return result
}

async function settle(ms = 1200) {
  try {
    await page.waitForLoadState('networkidle', { timeout: 8000 })
  } catch {
    /* 轮询请求可能让 networkidle 超时 */
  }
  await page.waitForTimeout(ms)
}

function captchaCode(id) {
  return execFileSync(redisCli, ['-h', '127.0.0.1', '-p', '6379', '-n', redisDatabase, 'GET', `admin:captcha:${id}`]).toString().trim()
}

let lastCaptcha = null
page.on('response', async (r) => {
  if (r.url().includes('/admin/v1/auth/captcha') && r.status() === 200) {
    try {
      lastCaptcha = (await r.json()).data
    } catch {
      /* ignore */
    }
  }
})

async function waitCaptcha(previousId) {
  for (let i = 0; i < 40; i++) {
    if (lastCaptcha && lastCaptcha.captcha_id !== previousId) return lastCaptcha
    await page.waitForTimeout(150)
  }
  throw new Error('验证码没有加载')
}

async function login(password, code) {
  await page.getByPlaceholder('请输入管理员账号').fill(user)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByPlaceholder('请输入右侧字符').fill(code)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await settle(1500)
}

async function openMenu(item) {
  if (item.group) {
    const sub = page.locator('.zw-menu-subitem', { hasText: item.label })
    if (!(await sub.first().isVisible().catch(() => false))) {
      await page.locator('.zw-menu-group-head', { hasText: item.group }).click()
      await page.waitForTimeout(200)
    }
    await page.locator('.zw-menu-subitem', { hasText: item.label }).first().click()
  } else {
    await page.locator('a.zw-menu-item', { hasText: item.label }).first().click()
  }
  await settle()
}

async function tryDialog(name) {
  // 先试页头的新增/发布按钮（弹窗），没有就点表格操作列的第一个查看/审核/详情（抽屉）
  let btn = page.locator('.zw-content .v-btn:visible').filter({ hasText: /新增|新建|发布|添加/ }).first()
  if (!(await btn.count())) {
    btn = page.locator('.zw-content table a:visible, .zw-content table button:visible').filter({ hasText: /^(查看|审核|详情|处理|编辑)$/ }).first()
  }
  if (!(await btn.count())) return null
  await btn.click().catch(() => undefined)
  await page.waitForTimeout(900)
  const visible = await page.locator('.v-overlay--active .zw-dialog, .zw-drawer.v-navigation-drawer--active').first().isVisible().catch(() => false)
  if (!visible) return null
  const icons = await probeIcons(page)
  const file = `${shotDir}/${String(steps.length).padStart(2, '0')}-${name}-dialog.png`
  await page.screenshot({ path: file })
  await page.keyboard.press('Escape')
  await page.waitForTimeout(500)
  if (await page.locator('.v-overlay--active .zw-dialog, .zw-drawer.v-navigation-drawer--active').first().isVisible().catch(() => false)) {
    await page.locator('.v-overlay--active .zw-icon-btn[aria-label="关闭"], .zw-drawer.v-navigation-drawer--active .zw-icon-btn[aria-label="关闭"]').first().click().catch(() => undefined)
    await page.waitForTimeout(400)
  }
  return { brokenIcons: icons.broken, iconCount: icons.total }
}

try {
  // 1. 打开根路径：未登录应跳到登录页并加载验证码
  await page.goto(base + '/', { waitUntil: 'networkidle' })
  const first = await waitCaptcha(null)
  await record('open-root', { captcha: !!first.image })

  // 2. 错误验证码：应提示错误并自动换一张
  await login(initialPassword, '0000')
  const second = await waitCaptcha(first.captcha_id)
  await record('wrong-captcha', { captchaRefreshed: second.captcha_id !== first.captcha_id })

  // 3. 种子管理员登录，首登应弹出强制改密
  let usedPassword = initialPassword
  await login(initialPassword, captchaCode(second.captcha_id))
  let mustChange = await page.getByText('首次登录请修改密码').isVisible().catch(() => false)
  if (!mustChange && path() === '/login') {
    // 已改过密码的库：改用新密码登录
    const c = await waitCaptcha(second.captcha_id)
    usedPassword = newPassword
    await login(newPassword, captchaCode(c.captcha_id))
    mustChange = await page.getByText('首次登录请修改密码').isVisible().catch(() => false)
  }
  await record('login-seed', { mustChange, usedPassword: usedPassword === initialPassword ? 'seed' : 'new' })

  if (mustChange) {
    // 4. 修改密码 → 回到登录页 → 用新密码登录
    const before = lastCaptcha?.captcha_id
    const dialog = page.locator('.v-overlay--active .zw-dialog')
    await dialog.getByPlaceholder('请输入当前密码').fill(usedPassword)
    await dialog.getByPlaceholder('请输入新密码').fill(newPassword)
    await dialog.getByPlaceholder('再次输入新密码').fill(newPassword)
    await dialog.getByRole('button', { name: '修改密码' }).click()
    await settle(1500)
    const c = await waitCaptcha(before)
    await record('password-changed', { dialogClosed: !(await dialog.isVisible().catch(() => false)) })
    await login(newPassword, captchaCode(c.captcha_id))
    await record('login-new-password', { mustChangeAgain: await page.getByText('首次登录请修改密码').isVisible().catch(() => false) })
  }

  // 5. 逐个打开菜单页
  for (const item of menus) {
    await openMenu(item)
    const dialog = await tryDialog(item.path.replace(/\W+/g, '_'))
    await record(item.label, { expected: item.path, ok: path() === item.path, dialog })
  }

  // 6. 刷新保持登录
  await page.reload({ waitUntil: 'networkidle' })
  await settle()
  await record('reload', { expected: '/system/logs', ok: path() === '/system/logs' })

  // 7. 新标签页深链接（同一 Cookie 会话）
  await page.goto(base + '/audit/farms', { waitUntil: 'networkidle' })
  await settle()
  await record('deep-link', { expected: '/audit/farms', ok: path() === '/audit/farms' })

  // 8. 退出登录 → 登录页；再访问受保护页应回到登录页
  await page.locator('.zw-user').click()
  await page.waitForTimeout(300)
  await page.locator('.zw-pop-item', { hasText: '退出登录' }).click()
  await settle()
  await record('logout', { expected: '/login', ok: path() === '/login' })
  await page.goto(base + '/system/users', { waitUntil: 'networkidle' })
  await settle()
  await record('after-logout-protected', { expected: '/login', ok: path() === '/login' })
} catch (error) {
  await record('error-' + step, { error: String(error).slice(0, 300) }).catch(() => undefined)
}

const summary = steps.map((s) => ({
  step: s.step,
  path: s.path,
  ok: s.ok ?? null,
  navs: s.navs,
  blank: s.blank,
  icons: s.iconCount,
  brokenIcons: s.brokenIcons.join(',') || '',
  dialog: s.dialog ? `${s.dialog.iconCount} icons${s.dialog.brokenIcons.length ? ' broken ' + s.dialog.brokenIcons.join(',') : ''}` : '',
  console: s.console.length ? s.console : '',
  http: s.http.length ? s.http : '',
  notices: s.notices.length ? s.notices : '',
  extra: Object.fromEntries(Object.entries(s).filter(([k]) => ['mustChange', 'captchaRefreshed', 'dialogClosed', 'mustChangeAgain', 'usedPassword', 'error', 'captcha'].includes(k)))
}))
const report = { base, summary }
writeFileSync(`${shotDir}/summary.json`, JSON.stringify(report, null, 2))
console.log(JSON.stringify(report, null, 1))
await browser.close()
const failures = summary.filter((s) => s.ok === false || s.blank || s.brokenIcons || s.extra.error
  || (s.dialog && s.dialog.includes(' broken '))
  || (s.console && s.console.some((m) => !/401 \(Unauthorized\)/.test(m)))
  || (s.http && s.http.some((m) => !/401 GET \/admin\/v1\/auth\/me|401 POST \/admin\/v1\/auth\/login/.test(m))))
if (summary.length < 25 || failures.length) {
  console.error(JSON.stringify({ failedSteps: failures.map((s) => s.step), stepCount: summary.length }))
  process.exitCode = 1
}
