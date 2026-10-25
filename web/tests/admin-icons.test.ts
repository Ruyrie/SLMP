// @vitest-environment node
// 图标字体是 Material Symbols 的子集（只含用到的图标）。字体里缺某个连字时页面会显示英文单词，
// 这里直接解析 woff2 的 cmap / GSUB，确认两端源码里用到的图标名都在字体里。
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { brotliDecompressSync } from 'node:zlib'
import { describe, expect, it } from 'vitest'

const web = fileURLToPath(new URL('..', import.meta.url))
const fontPath = join(web, 'packages/shared/src/theme/fonts/MaterialSymbolsOutlined.woff2')

const KNOWN_TAGS = [
  'cmap', 'head', 'hhea', 'hmtx', 'maxp', 'name', 'OS/2', 'post', 'cvt ', 'fpgm', 'glyf', 'loca', 'prep', 'CFF ', 'VORG', 'EBDT',
  'EBLC', 'gasp', 'hdmx', 'kern', 'LTSH', 'PCLT', 'VDMX', 'vhea', 'vmtx', 'BASE', 'GDEF', 'GPOS', 'GSUB', 'EBSC', 'JSTF', 'MATH',
  'CBDT', 'CBLC', 'COLR', 'CPAL', 'SVG ', 'sbix', 'acnt', 'avar', 'bdat', 'bloc', 'bsln', 'cvar', 'fdsc', 'feat', 'fmtx', 'fvar',
  'gvar', 'hsty', 'just', 'lcar', 'mort', 'morx', 'opbd', 'prop', 'trak', 'Zapf', 'Silf', 'Glat', 'Gloc', 'Feat', 'Sill'
]

/** 解开 woff2，返回各表的原始数据（cmap、GSUB 不做变换，可直接读）。 */
function woff2Tables(buf: Buffer): Map<string, Buffer> {
  if (buf.toString('latin1', 0, 4) !== 'wOF2') throw new Error('不是 woff2 字体')
  const numTables = buf.readUInt16BE(12)
  const compressedSize = buf.readUInt32BE(20)
  let p = 48
  const base128 = (): number => {
    let value = 0
    for (let i = 0; i < 5; i++) {
      const byte = buf[p++]
      value = value * 128 + (byte & 0x7f)
      if (!(byte & 0x80)) return value
    }
    throw new Error('UIntBase128 格式错误')
  }
  const dir: { tag: string; length: number }[] = []
  for (let i = 0; i < numTables; i++) {
    const flags = buf[p++]
    let tag = KNOWN_TAGS[flags & 0x3f]
    if ((flags & 0x3f) === 0x3f) {
      tag = buf.toString('latin1', p, p + 4)
      p += 4
    }
    const version = (flags >> 6) & 3
    const origLength = base128()
    const transformed = tag === 'glyf' || tag === 'loca' ? version === 0 : version !== 0
    dir.push({ tag, length: transformed ? base128() : origLength })
  }
  const data = brotliDecompressSync(buf.subarray(p, p + compressedSize))
  const tables = new Map<string, Buffer>()
  let offset = 0
  for (const table of dir) {
    tables.set(table.tag, data.subarray(offset, offset + table.length))
    offset += table.length
  }
  return tables
}

/** 字形编号 → 字符（只关心 a-z、0-9、_，大小写共用字形时取小写）。 */
function glyphChars(cmap: Buffer): Map<number, string> {
  const result = new Map<number, string>()
  const add = (glyph: number, code: number) => {
    const ch = String.fromCharCode(code)
    if (!glyph || !/[A-Za-z0-9_]/.test(ch)) return
    if (!result.has(glyph) || ch === ch.toLowerCase()) result.set(glyph, ch.toLowerCase())
  }
  const count = cmap.readUInt16BE(2)
  for (let i = 0; i < count; i++) {
    const offset = cmap.readUInt32BE(8 + i * 8)
    const format = cmap.readUInt16BE(offset)
    if (format === 4) {
      const segX2 = cmap.readUInt16BE(offset + 6)
      const ends = offset + 14
      const starts = ends + segX2 + 2
      const deltas = starts + segX2
      const ranges = deltas + segX2
      for (let s = 0; s < segX2 / 2; s++) {
        const start = cmap.readUInt16BE(starts + s * 2)
        const end = Math.min(cmap.readUInt16BE(ends + s * 2), 0x7f)
        const delta = cmap.readInt16BE(deltas + s * 2)
        const rangeOffset = cmap.readUInt16BE(ranges + s * 2)
        for (let c = start; c <= end; c++) {
          let glyph = (c + delta) & 0xffff
          if (rangeOffset) {
            glyph = cmap.readUInt16BE(ranges + s * 2 + rangeOffset + (c - start) * 2)
            if (glyph) glyph = (glyph + delta) & 0xffff
          }
          add(glyph, c)
        }
      }
    } else if (format === 12) {
      const groups = cmap.readUInt32BE(offset + 12)
      for (let g = 0; g < groups; g++) {
        const base = offset + 16 + g * 12
        const start = cmap.readUInt32BE(base)
        const end = Math.min(cmap.readUInt32BE(base + 4), 0x7f)
        for (let c = start; c <= end; c++) add(cmap.readUInt32BE(base + 8) + c - start, c)
      }
    }
  }
  return result
}

function coverage(gsub: Buffer, offset: number): number[] {
  const format = gsub.readUInt16BE(offset)
  const count = gsub.readUInt16BE(offset + 2)
  const glyphs: number[] = []
  for (let i = 0; i < count; i++) {
    if (format === 1) glyphs.push(gsub.readUInt16BE(offset + 4 + i * 2))
    else for (let g = gsub.readUInt16BE(offset + 4 + i * 6); g <= gsub.readUInt16BE(offset + 6 + i * 6); g++) glyphs.push(g)
  }
  return glyphs
}

/** GSUB 连字替换（类型 4，可能包在扩展类型 7 里）拼出全部图标名。 */
export function fontIconNames(file: string): Set<string> {
  const tables = woff2Tables(readFileSync(file))
  const chars = glyphChars(tables.get('cmap')!)
  const gsub = tables.get('GSUB')!
  const names = new Set<string>()
  const lookupList = gsub.readUInt16BE(8)
  for (let i = 0; i < gsub.readUInt16BE(lookupList); i++) {
    const lookup = lookupList + gsub.readUInt16BE(lookupList + 2 + i * 2)
    for (let j = 0; j < gsub.readUInt16BE(lookup + 4); j++) {
      let type = gsub.readUInt16BE(lookup)
      let sub = lookup + gsub.readUInt16BE(lookup + 6 + j * 2)
      if (type === 7) {
        type = gsub.readUInt16BE(sub + 2)
        sub += gsub.readUInt32BE(sub + 4)
      }
      if (type !== 4) continue
      const firsts = coverage(gsub, sub + gsub.readUInt16BE(sub + 2))
      for (let k = 0; k < gsub.readUInt16BE(sub + 4); k++) {
        const set = sub + gsub.readUInt16BE(sub + 6 + k * 2)
        for (let l = 0; l < gsub.readUInt16BE(set); l++) {
          const lig = set + gsub.readUInt16BE(set + 2 + l * 2)
          const glyphs = [firsts[k]]
          for (let m = 0; m < gsub.readUInt16BE(lig + 2) - 1; m++) glyphs.push(gsub.readUInt16BE(lig + 4 + m * 2))
          const text = glyphs.map((g) => chars.get(g)).join('')
          if (text.length === glyphs.length) names.add(text)
        }
      }
    }
  }
  return names
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return /\.(vue|ts)$/.test(name) && !name.endsWith('generated.ts') ? [path] : []
  })
}

const LITERAL = /['"`]([a-z][a-z0-9_]*)(?::fill)?['"`]/g

/** 只取确定是图标的位置：图标属性、图标绑定表达式、名字带 icon 的函数 / 对象里的返回值。 */
export function usedIconNames(text: string): Set<string> {
  const names = new Set<string>()
  // 比较里的字面量（status === 'disable'）不是图标名
  const literals = (expr: string) => {
    const values = expr
      .replace(/(?:===?|!==?|case)\s*(['"`])[^'"`]*\1/g, '')
      .replace(/(['"`])[^'"`]*\1\s*(?:===?|!==?|\))/g, '')
    for (const m of values.matchAll(LITERAL)) names.add(m[1])
  }
  // <zw-icon name="x"> / icon="x" / prepend-inner-icon="x" / ico="x"
  for (const m of text.matchAll(/(?:<zw-icon\b[^>]*?\sname|\s(?:[a-z-]*-)?icon|\sico)="([a-z][a-z0-9_]*)"/g)) names.add(m[1])
  // :name / :icon / :xxx-icon 绑定里的字面量
  for (const m of text.matchAll(/(?:<zw-icon\b[^>]*?\s:name|\s:(?:[a-z-]*-)?icon|\s:ico)="([^"]+)"/g)) literals(m[1].replace(/\\'/g, "'"))
  // 对象属性 icon: 'x'、menuIcon: 'x'、ico: 'x'
  for (const m of text.matchAll(/\b(?:[a-zA-Z]*Icon|icon|ico)\s*:\s*(['"])([a-z][a-z0-9_]*)\1/g)) names.add(m[2])
  // 名字带 icon 的函数 / 常量（statusIcon、todoIcon、iconAliases…）：取函数体里非比较位置的字面量
  for (const m of text.matchAll(/(?:function\s+|(?:const|let|var)\s+)(\w*[iI]con\w*)\b[^{\n]*\{/g)) {
    let depth = 1
    let i = m.index! + m[0].length
    while (i < text.length && depth) {
      if (text[i] === '{') depth++
      else if (text[i] === '}') depth--
      i++
    }
    literals(text.slice(m.index! + m[0].length, i - 1))
  }
  return names
}

describe('Material Symbols 图标子集', () => {
  const font = fontIconNames(fontPath)

  it('能从 woff2 解析出连字图标名', () => {
    expect(font.size).toBeGreaterThan(200)
    for (const name of ['dashboard', 'fact_check', 'campaign', 'database', 'manage_accounts', 'logout', 'close']) {
      expect(font.has(name), name).toBe(true)
    }
  })

  it('管理端、用户端与共享包用到的图标都在字体里', () => {
    const missing: string[] = []
    let total = 0
    for (const dir of ['apps/admin/src', 'apps/user/src', 'packages/shared/src']) {
      for (const file of sourceFiles(join(web, dir))) {
        for (const name of usedIconNames(readFileSync(file, 'utf8'))) {
          total++
          if (!font.has(name)) missing.push(`${name}  (${relative(web, file).replace(/\\/g, '/')})`)
        }
      }
    }
    expect(total).toBeGreaterThan(300)
    expect(missing, '字体子集缺这些图标，页面会显示成英文单词；用 tools/subset-icons.py 重新生成子集').toEqual([])
  })
})
