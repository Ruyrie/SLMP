import QRCode from 'qrcode'

/**
 * 批次二维码标签：按批次的 qr_content 用 qrcode 库直接生成 PNG（与页面上 zw-qr 的参数一致），
 * 不再从页面读取 svg —— zw-qr 渲染的是 <img>，读 svg 会一直报“二维码尚未生成”。
 */
export async function qrDataUrl(content: string, size = 320): Promise<string> {
  const text = String(content || '').trim()
  if (!text) throw new Error('二维码尚未生成')
  return await QRCode.toDataURL(text, { margin: 1, width: size, errorCorrectionLevel: 'M' })
}

async function loadImage(src: string): Promise<HTMLImageElement> {
  return await new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('二维码图片生成失败'))
    image.src = src
  })
}

/** 下载带批次号与栏舍说明的二维码标签 PNG。 */
export async function downloadQrLabel(content: string, options: {
  batchNo: string
  label: string
  filename?: string
}): Promise<void> {
  const qrSize = 320
  const qr = await qrDataUrl(content, qrSize)
  const width = 480
  const height = 590
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('浏览器不支持图片导出')
  ctx.fillStyle = '#FFFFFF'
  ctx.fillRect(0, 0, width, height)
  ctx.fillStyle = '#1D2129'
  ctx.textAlign = 'center'
  ctx.font = '600 18px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText('智牧通 · 批次标签', width / 2, 42)
  const image = await loadImage(qr)
  ctx.drawImage(image, (width - qrSize) / 2, 70, qrSize, qrSize)
  ctx.font = '600 28px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText(options.batchNo, width / 2, 450)
  ctx.fillStyle = '#4E5969'
  ctx.font = '400 15px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText(options.label, width / 2, 486)
  const link = document.createElement('a')
  link.download = options.filename || `${options.batchNo}.png`
  link.href = canvas.toDataURL('image/png')
  document.body.appendChild(link)
  link.click()
  link.remove()
}

/** 打开浏览器打印预览，只打印 60 × 40 毫米的标签。窗口先同步打开，避免生成图片后再打开被拦截。 */
export async function printQrLabel(content: string, options: { batchNo: string; label: string }): Promise<void> {
  if (!String(content || '').trim()) throw new Error('二维码尚未生成')
  const win = window.open('', '_blank', 'width=520,height=620')
  if (!win) throw new Error('浏览器阻止了打印窗口，请允许弹窗后重试')
  let image = ''
  try {
    image = await qrDataUrl(content, 320)
  } catch (error) {
    win.close()
    throw error
  }
  const safe = (value: string): string => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${safe(options.batchNo)}</title>
    <style>
      @page { size: 60mm 40mm; margin: 0; }
      html, body { margin: 0; padding: 0; background: #fff; color: #1D2129; font-family: "PingFang SC", "Microsoft YaHei", sans-serif; }
      .label { width: 60mm; height: 40mm; box-sizing: border-box; padding: 3mm; display: flex; align-items: center; gap: 3mm; }
      .label img { width: 28mm; height: 28mm; display: block; }
      .text { min-width: 0; }
      .no { font-size: 12px; font-weight: 600; letter-spacing: .2px; word-break: break-all; }
      .desc { margin-top: 3mm; font-size: 8px; line-height: 1.5; color: #4E5969; }
      @media print { body { width: 60mm; height: 40mm; } }
    </style></head><body>
    <div class="label"><img src="${image}" alt="二维码"><div class="text"><div class="no">${safe(options.batchNo)}</div><div class="desc">${safe(options.label)}</div></div></div>
    <script>window.onload = function () { window.focus(); window.print(); setTimeout(function () { window.close(); }, 300); }<\/script>
    </body></html>`)
  win.document.close()
}
