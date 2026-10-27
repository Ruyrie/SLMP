import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError, today } from '@zmt/shared'
import { useReceiveStore } from '../apps/user/src/stores/finance/receive'

const mocks = vi.hoisted(() => ({ addReceipt: vi.fn(), newRequestKey: vi.fn(), toast: vi.fn() }))
vi.mock('../apps/user/src/api/finance', () => ({ financeApi: { addReceipt: mocks.addReceipt }, newRequestKey: mocks.newRequestKey }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: mocks.toast }) }))

const order = { id: 9102, batchNo: 'ZM2610-072', saleDate: today(), saleAmount: 33920, receivedAmount: 10000, unpaidAmount: 23920 }
const paid = { id: 9102, paymentStatus: 'PAID', receiptResult: { amount: 23920, unpaidAmount: 0, paidOff: true } }

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  let sequence = 0
  mocks.newRequestKey.mockImplementation(() => `receipt-${++sequence}`)
  mocks.addReceipt.mockResolvedValue(paid)
})

describe('sale receipt submission', () => {
  it('submits the exact remaining balance and closes after confirmed success', async () => {
    const store = useReceiveStore()
    store.show(order)
    expect(await store.submit()).toEqual(paid)
    expect(mocks.addReceipt).toHaveBeenCalledWith(9102, { amount: 23920, receipt_date: today(), payment_method: 'BANK_TRANSFER' }, 'receipt-1')
    expect(store.open).toBe(false)
    expect(store.submitting).toBe(false)
    expect(mocks.toast).toHaveBeenCalledWith(expect.stringContaining('出栏单已收齐'))
  })

  it('rejects more than two decimal places without silently rounding a receipt', async () => {
    const store = useReceiveStore()
    store.show(order)
    store.form.amount = '100.005'
    expect(await store.submit()).toBeNull()
    expect(store.amountError).toBe('金额最多保留 2 位小数')
    expect(mocks.addReceipt).not.toHaveBeenCalled()
    store.form.amount = '100.01'
    expect(store.validate()).toBe(true)
  })

  it('reuses the receipt key after an uncertain failure, including reopening the dialog', async () => {
    const store = useReceiveStore()
    store.show(order)
    mocks.addReceipt.mockRejectedValueOnce(new Error('请求超时'))
    expect(await store.submit()).toBeNull()
    expect(store.open).toBe(true)
    expect(mocks.toast).toHaveBeenCalledWith('请求超时', 'error')
    store.close()
    store.show(order)
    expect(await store.submit()).toEqual(paid)
    expect(mocks.addReceipt.mock.calls[0][2]).toBe(mocks.addReceipt.mock.calls[1][2])
    expect(mocks.newRequestKey).toHaveBeenCalledTimes(1)
  })

  it('gives changed receipt data a new key and preserves the dialog on server failures', async () => {
    const store = useReceiveStore()
    store.show(order)
    mocks.addReceipt.mockRejectedValueOnce(new ApiError({ code: 50000, message: '服务暂时不可用' }))
    await store.submit()
    expect(store.open).toBe(true)
    store.form.amount = '100.01'
    await store.submit()
    expect(mocks.addReceipt.mock.calls[1][2]).not.toBe(mocks.addReceipt.mock.calls[0][2])
  })

  it('keeps an in-flight submission locked against cancel and repeated confirmation', async () => {
    const store = useReceiveStore()
    store.show(order)
    let complete!: (value: typeof paid) => void
    mocks.addReceipt.mockImplementationOnce(() => new Promise((resolve) => { complete = resolve }))
    const pending = store.submit()
    store.close()
    expect(store.open).toBe(true)
    expect(await store.submit()).toBeNull()
    expect(mocks.addReceipt).toHaveBeenCalledTimes(1)
    complete(paid)
    await pending
    expect(store.open).toBe(false)
  })
})
