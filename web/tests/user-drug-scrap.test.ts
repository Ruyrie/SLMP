import { describe, expect, it } from 'vitest'
import { scrapInput, scrapQuantityError } from '../apps/user/src/views/stock/scrap'
import { packQtyText, qtyText, flowQty } from '../apps/user/src/views/stock/stock'

const powder = { value: 3000, unit: 'G', unit_text: '克', pack_unit: '袋', pack_size: 500 }
describe('scrap quantities', () => {
  it('shows fractional inventory consistently in package text and flows', () => {
    const stock = { ...powder, value: 2982.05, pack_count: 5, rest_value: 482.05 }
    expect(packQtyText(stock)).toBe('5 袋 + 482.05 克（2,982.05 克）')
    expect(qtyText({ ...powder, value: 0.001 })).toBe('0.001 克')
    expect(flowQty({ flow_id: 1, direction: 'OUT', qty: stock })).toContain('2,982.05 克')
  })
  it('offers six bags only when they represent the entire stock exactly', () => {
    expect(scrapInput(powder)).toEqual({ unit: 'PACK', max: 6, text: '袋' })
    expect(scrapQuantityError(6, powder)).toBeUndefined()
    expect(scrapQuantityError(6.01, powder)).toContain('超过')
  })
  it('preserves the leftover quantity caused by the old unit bug', () => {
    const stock = { ...powder, value: 2982.05 }
    expect(scrapInput(stock)).toEqual({ unit: 'MEASURE', max: 2982.05, text: '克' })
    expect(scrapQuantityError(2982.05, stock)).toBeUndefined()
  })
  it('allows a one-thousandth residual and never rounds it to zero', () => {
    const stock = { ...powder, value: 0.001 }
    expect(scrapInput(stock).max).toBe(0.001)
    expect(scrapQuantityError(0.001, stock)).toBeUndefined()
    expect(scrapQuantityError(0.0001, stock)).toContain('3 位小数')
  })
  it('uses base units for vaccines and materials without a package', () => {
    const vaccine = { value: 300, unit: 'DOSE', unit_text: '头份', pack_unit: '瓶', pack_size: 10 }
    expect(scrapInput(vaccine).unit).toBe('MEASURE')
    expect(scrapQuantityError(1.5, vaccine)).toBe('头份必须是整数')
    expect(scrapInput({ value: 2, unit_text: '升' }).unit).toBe('MEASURE')
  })
  it('rejects partial packs that cannot be stored at ledger precision', () => {
    const smallPack = { ...powder, pack_size: 0.001, value: 1 }
    expect(scrapQuantityError(0.01, smallPack)).toBe('换算后的数量最多保留 3 位小数')
    expect(scrapQuantityError(1, smallPack)).toBeUndefined()
    expect(scrapQuantityError(NaN, smallPack)).toBe('必须大于 0')
  })
})
