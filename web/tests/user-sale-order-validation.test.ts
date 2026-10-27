import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSaleOrderFormStore } from '../apps/user/src/stores/finance/saleOrderForm'

const mocks = vi.hoisted(() => ({ create: vi.fn(), update: vi.fn(), toast: vi.fn() }))
vi.mock('../apps/user/src/api/finance', () => ({
  financeApi: { createSaleOrder: mocks.create, updateSaleOrder: mocks.update },
  newRequestKey: () => 'validation-test'
}))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: mocks.toast }) }))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  mocks.create.mockResolvedValue({ id: 1, remainingCount: 50 })
  mocks.update.mockResolvedValue({ id: 1 })
})

function form(mode: 'create' | 'edit' = 'create') {
  const store = useSaleOrderFormStore()
  store.mode = mode
  store.editId = mode === 'edit' ? 1 : 0
  store.batches = [{ batchId: 1, currentCount: 200000 }]
  Object.assign(store.form, { batchId: 1, quantity: 150, totalWeight: 1000, unitPrice: 15.6, buyerName: '测试收购商' })
  return store
}

describe('sale order numeric bounds', () => {
  it.each(['create', 'edit'] as const)('blocks the reported 100 million kg value in %s', async mode => {
    const store = form(mode)
    store.form.totalWeight = 100000000
    await store.submit()
    expect(store.errors).toContainEqual({ field: 'total_weight', message: '总重不能超过 99,999,999.99 千克，请核对输入' })
    expect(mocks.create).not.toHaveBeenCalled()
    expect(mocks.update).not.toHaveBeenCalled()
  })

  it.each(['BY_WEIGHT', 'BY_HEAD'] as const)('checks the calculated rounded amount with %s', async method => {
    const store = form()
    Object.assign(store.form, { pricingMethod: method, quantity: 100001, totalWeight: 100001, unitPrice: 99999.99 })
    await store.submit()
    expect(store.errors).toContainEqual(expect.objectContaining({ field: 'unit_price', message: expect.stringContaining('销售金额不能超过') }))
    expect(mocks.create).not.toHaveBeenCalled()
  })

  it('keeps the largest supported weight when the calculated amount fits', async () => {
    const store = form()
    Object.assign(store.form, { totalWeight: 99999999.99, unitPrice: 1 })
    await store.submit()
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ total_weight: 99999999.99 }), 'validation-test')
    expect(store.errors).toEqual([])
  })

  it('allows the reported normal-weight submission', async () => {
    const store = form()
    await store.submit()
    expect(mocks.create).toHaveBeenCalledOnce()
    expect(store.saleAmount).toBe(15600)
  })

  it.each([{ quantity: 1.5 }, { unitPrice: 100000 }])('rejects invalid quantity or price %j', values => {
    const store = form()
    Object.assign(store.form, values)
    expect(store.validate()).toBe(false)
  })
})
