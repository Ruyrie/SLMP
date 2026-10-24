import { chromium } from 'playwright'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
for (let i=0;i<5;i++){ await page.getByPlaceholder('请输入手机号').fill('13800006620'); await page.getByPlaceholder('请输入密码').fill('Zmt12345'); await page.getByRole('button',{name:'登录'}).click(); await page.waitForTimeout(3000); if(!page.url().includes('/login')) break; await page.goto('http://127.0.0.1:5173/login',{waitUntil:'networkidle'}) }
await page.goto('http://127.0.0.1:5173/breeding/pending', { waitUntil: 'networkidle' })
await page.waitForTimeout(3000)
const info = await page.evaluate(() => {
  const tables = Array.from(document.querySelectorAll('table'))
  const row = document.querySelector('tr.b02-row')
  const td = row?.querySelector('td')
  const chain = []
  let el = td
  while (el && chain.length < 8) { chain.push({ tag: el.tagName, cls: el.className, display: getComputedStyle(el).display, w: Math.round(el.getBoundingClientRect().width) }); el = el.parentElement }
  return { tables: tables.length, tableCls: tables.map(t=>t.className), tableLayout: tables.map(t=>getComputedStyle(t).tableLayout), chain, tableW: tables[0]?.getBoundingClientRect().width, headerW: tables[0]?.querySelector('th')?.getBoundingClientRect().width, firstTdW: td?.getBoundingClientRect().width }
})
console.log(JSON.stringify(info,null,2))
await browser.close()
