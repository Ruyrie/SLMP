// 图标探针公共函数：在页面里把每个 Material Symbols 图标名单独用图标字体渲染，
// 宽度接近 1em 视为字形，否则说明字体里缺这个连字（页面上会显示成英文单词）。
export async function probeIcons(page) {
  return page.evaluate(async () => {
    await document.fonts.ready
    const nodes = [...document.querySelectorAll('.zw-mso, .v-icon')]
    const names = [...new Set(nodes.map((el) => (el.textContent || '').trim()).filter(Boolean))]
    const probe = document.createElement('span')
    probe.style.cssText =
      "position:absolute;left:-9999px;top:0;white-space:nowrap;font-family:'Material Symbols Outlined';font-size:40px;font-feature-settings:'liga';line-height:1"
    document.body.appendChild(probe)
    const result = names.map((name) => {
      probe.textContent = name
      const w = probe.getBoundingClientRect().width
      return { name, glyph: w <= 48 }
    })
    probe.remove()
    const faces = [...document.fonts].filter((f) => f.family.includes('Material')).map((f) => f.status)
    return { faces, total: names.length, broken: result.filter((r) => !r.glyph).map((r) => r.name), all: result.map((r) => r.name) }
  })
}
