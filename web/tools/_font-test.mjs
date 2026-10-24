import { chromium } from 'playwright'
import { readFileSync } from 'node:fs'

const font = readFileSync('D:/Android_Program/SLMP/logs/diag/material-symbols-subset.woff2').toString('base64')
const icons = ['home', 'space_dashboard', 'vaccines', 'location_on', 'add', 'check_circle', 'calendar_month', 'workspace_premium', 'qr_code_2', 'receipt_long']
const spans = icons.map((n) => `<div><span class="mso">${n}</span><span class="mso fill">${n}</span><span class="ref">${n}</span></div>`).join('')

const html = `<!doctype html><meta charset="utf-8"><style>
@font-face { font-family:'Material Symbols Outlined'; font-style:normal; font-weight:100 700; font-display:block;
  src:url(data:font/woff2;base64,${font}) format('woff2'); }
body{font-family:Arial;font-size:15px}
.mso{font-family:'Material Symbols Outlined';font-weight:normal;font-style:normal;line-height:1;display:inline-block;
  font-feature-settings:'liga';font-variation-settings:'FILL' 0,'wght' 400,'GRAD' 0,'opsz' 20;width:1em;height:1em;font-size:32px;overflow:hidden;vertical-align:middle}
.mso.fill{font-variation-settings:'FILL' 1,'wght' 400,'GRAD' 0,'opsz' 20}
.ref{display:inline-block;width:220px;color:#888;font-size:12px}
</style><body>${spans}</body>`

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 900, height: 700 } })
await page.setContent(html, { waitUntil: 'load' })
await page.evaluate(() => document.fonts.ready)
const info = await page.evaluate(() => ({
  loaded: document.fonts.check('32px "Material Symbols Outlined"'),
  faces: [...document.fonts].map((f) => ({ family: f.family, status: f.status })),
  widths: [...document.querySelectorAll('.mso')].map((e) => ({ text: e.textContent, w: Math.round(e.getBoundingClientRect().width) }))
}))
await page.screenshot({ path: 'D:/Android_Program/SLMP/logs/diag/font-test.png' })
console.log(JSON.stringify(info, null, 1))
await browser.close()
