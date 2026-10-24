// 头像全链路检查（用户端）：上传 → 顶栏 / 个人中心 / 成员列表显示图片 → 刷新 → 其他账号可见 → 换头像 → 恢复默认。
// 用法：node tools/check-avatar.mjs [用户端地址] [截图目录]
// 服务端的 MINIO_PUBLIC_ENDPOINT 需指向用户端地址（与云端 Nginx /zmt-private/ 同源转发一致）。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'

const [, , base = 'http://localhost:5173', outDir = 'screenshots/cloud-fix/user'] = process.argv
const OWNER = '13800006620'
const MANAGER = '13900001234'
const STAFF = '13700005678'
const FARM = 1001
mkdirSync(outDir, { recursive: true })

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const problems = []
const steps = []

async function makeImage(color, file) {
  const page = await browser.newPage({ viewport: { width: 160, height: 160 } })
  await page.setContent(`<body style="margin:0"><div style="width:160px;height:160px;background:${color}"></div></body>`)
  await page.screenshot({ path: file })
  await page.close()
  return file
}

async function session(phone, label) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`${label} console: ${m.text().slice(0, 160)}`) })
  page.on('pageerror', (e) => problems.push(`${label} pageerror: ${String(e).slice(0, 160)}`))
  page.on('response', (r) => {
    const url = r.url()
    if (r.status() >= 400 && (url.includes('/zmt-private/') || url.includes('/api/v1/'))) problems.push(`${label} ${r.status()} ${url.split('?')[0]}`)
  })
  await page.goto(`${base}/login`, { waitUntil: 'networkidle' })
  await page.getByPlaceholder('请输入手机号').fill(phone)
  await page.getByPlaceholder('请输入密码').fill('Zmt12345')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 })
  await page.waitForLoadState('networkidle')
  return { context, page, label }
}

/** 页面上所有头像：名称、图片地址、是否真正加载出来、首字 */
async function avatars(page) {
  await page.waitForTimeout(800)
  return page.$$eval('.zw-avatar', (nodes) => nodes.map((node) => {
    const img = node.querySelector('img')
    return {
      label: node.getAttribute('aria-label') || '',
      src: img ? img.getAttribute('src') : null,
      loaded: !!img && img.complete && img.naturalWidth > 0,
      text: img ? '' : node.textContent.trim()
    }
  }))
}

async function expectAvatar(page, where, name, wantImage, shot) {
  const list = (await avatars(page)).filter((a) => a.label === `${name}的头像`)
  const ok = list.length > 0 && list.every((a) => (wantImage ? a.loaded : !a.src && a.text === name[0]))
  steps.push({ where, name, expect: wantImage ? 'image' : 'initial', ok, found: list.map((a) => (a.src ? `${a.loaded ? 'loaded' : 'BROKEN'} ${a.src.split('?')[0]}` : `text ${a.text}`)) })
  if (shot) await page.screenshot({ path: `${outDir}/${shot}.png` })
  if (!ok) problems.push(`头像不符合预期：${where} ${name}`)
  return list
}

async function go(page, path) {
  await page.goto(`${base}${path}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(600)
}

async function upload(page, file) {
  await go(page, '/account')
  const done = page.waitForResponse((r) => r.url().includes('/api/v1/account/avatar') && r.request().method() === 'PUT')
  await page.setInputFiles('input.a04-file', file)
  await done
  await page.waitForLoadState('networkidle')
}

async function clear(page) {
  await go(page, '/account')
  await page.locator('.a04-avatar-btn').click()
  const done = page.waitForResponse((r) => r.url().includes('/api/v1/account/avatar') && r.request().method() === 'DELETE')
  await page.getByText('恢复默认头像').click()
  await done
  await page.waitForLoadState('networkidle')
}

const red = await makeImage('#d64545', `${outDir}/_avatar-red.png`)
const blue = await makeImage('#2f6fd6', `${outDir}/_avatar-blue.png`)

const owner = await session(OWNER, '王建国')
const manager = await session(MANAGER, '刘芳')
const staff = await session(STAFF, '李明')

// 0. 起始状态：没有头像时显示首字（上次检查残留的头像先恢复默认）
for (const s of [owner, manager, staff]) {
  await go(s.page, '/account')
  if (await s.page.locator('.a04-avatar-btn img').count()) await clear(s.page)
}
await expectAvatar(owner.page, '初始顶栏', '王建国', false)

// 1. 王建国上传头像
await upload(owner.page, red)
const first = await expectAvatar(owner.page, '上传后个人中心+顶栏', '王建国', true, 'avatar-01-owner-account')
await go(owner.page, `/farms/${FARM}/members`)
await expectAvatar(owner.page, '上传后成员列表', '王建国', true, 'avatar-02-owner-members')
await owner.page.reload({ waitUntil: 'networkidle' })
await expectAvatar(owner.page, '刷新后成员列表+顶栏', '王建国', true)

// 2. 刘芳（场长）看 1001 成员列表
await go(manager.page, `/farms/${FARM}/members`)
await expectAvatar(manager.page, '刘芳看成员列表', '王建国', true, 'avatar-03-manager-members')

// 3. 李明（员工）上传头像，王建国看成员列表
await upload(staff.page, blue)
await expectAvatar(staff.page, '李明个人中心+顶栏', '李明', true, 'avatar-04-staff-account')
await go(owner.page, `/farms/${FARM}/members`)
await expectAvatar(owner.page, '王建国看成员列表中的李明', '李明', true)

// 3b. 养殖场信息页成员卡、转让候选（刘芳）、栏舍负责员工（李明负责 3 号育肥舍 2003）
await upload(manager.page, red)
await go(owner.page, `/farms/${FARM}`)
for (const initial of ['王', '刘', '李']) await expectAvatar(owner.page, '养殖场信息成员卡', initial, true, initial === '王' ? 'avatar-08-farm-info' : '')
await owner.page.getByRole('button', { name: '转让' }).click()
await owner.page.waitForLoadState('networkidle')
await expectAvatar(owner.page, '转让候选场长', '刘芳', true, 'avatar-09-transfer')
await owner.page.keyboard.press('Escape')
await go(owner.page, '/herd/pens?penId=2003')
await expectAvatar(owner.page, '栏舍负责员工', '李明', true, 'avatar-10-pen-staff')
await clear(manager.page)

// 4. 王建国换头像：地址必须换成新文件
await upload(owner.page, blue)
const second = await expectAvatar(owner.page, '换头像后', '王建国', true)
const firstKey = first[0]?.src?.split('?')[0]
const secondKey = second[0]?.src?.split('?')[0]
steps.push({ where: '换头像后地址变化', ok: !!firstKey && !!secondKey && firstKey !== secondKey, found: [firstKey, secondKey] })

// 5. 恢复默认：各处回到首字，刷新后仍是首字
await clear(owner.page)
await expectAvatar(owner.page, '恢复默认后个人中心+顶栏', '王建国', false, 'avatar-05-owner-cleared')
await owner.page.reload({ waitUntil: 'networkidle' })
await expectAvatar(owner.page, '恢复默认后刷新', '王建国', false)
await go(manager.page, `/farms/${FARM}/members`)
await expectAvatar(manager.page, '恢复默认后刘芳看成员列表', '王建国', false)
await clear(staff.page)
await expectAvatar(staff.page, '李明恢复默认', '李明', false)

console.log(JSON.stringify({ steps, problems }, null, 2))
await browser.close()
