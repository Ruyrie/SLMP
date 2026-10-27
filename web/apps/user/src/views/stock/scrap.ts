import type { Qty } from '@/api/stock'

/** 库存以千分之一基础单位记账；整包装输入必须能精确表示结存。 */
function milli(value: number | undefined | null): bigint | null {
  const text = String(value ?? '')
  if (!/^\d+(\.\d{1,3})?$/.test(text)) return null
  const [whole, fraction = ''] = text.split('.')
  return BigInt(whole!) * 1000n + BigInt(fraction.padEnd(3, '0'))
}

export function scrapInput(stock?: Qty): { unit: 'PACK' | 'MEASURE'; max: number; text: string } {
  const qty = milli(stock?.value)
  const pack = milli(stock?.pack_size)
  if (stock?.unit !== 'DOSE' && stock?.unit !== 'KG' && stock?.pack_unit && qty !== null
      && pack !== null && pack > 0n && (qty * 100n) % pack === 0n) {
    return { unit: 'PACK', max: Number(qty * 100n / pack) / 100, text: stock.pack_unit }
  }
  return { unit: 'MEASURE', max: Number(stock?.value || 0), text: stock?.unit_text || stock?.unit || '' }
}

export function scrapQuantityError(input: number, stock?: Qty): string | undefined {
  const mode = scrapInput(stock)
  if (!Number.isFinite(input) || input <= 0) return '必须大于 0'
  if (input > mode.max) return `超过当前结存 ${mode.max} ${mode.text}`
  if (stock?.unit === 'DOSE' && !Number.isInteger(input)) return '头份必须是整数'
  const precision = mode.unit === 'PACK' ? 2 : 3
  if (!new RegExp(`^\\d+(\\.\\d{1,${precision}})?$`).test(String(input))) {
    return `数量最多保留 ${precision} 位小数`
  }
  if (mode.unit === 'PACK') {
    const pack = milli(stock?.pack_size)
    const hundredths = BigInt(Math.round(input * 100))
    if (pack === null || (hundredths * pack) % 100n !== 0n) return '换算后的数量最多保留 3 位小数'
  }
  return undefined
}
