import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { dayjs, today } from '@zmt/shared'
import { useFarmStore } from '../apps/user/src/stores/farm'
import { useAuthStore } from '../apps/user/src/stores/auth'
import { useStockRefundStore } from '../apps/user/src/stores/stock/refund'
import StockDocumentDrawer from '../apps/user/src/views/stock/components/StockDocumentDrawer.vue'
import type { StockOutbound } from '../apps/user/src/api/stock'

const api = vi.hoisted(() => ({ registerStockRefund: vi.fn(), updateStockRefund: vi.fn(), newRequestKey: vi.fn(),
  fetchStockInbound: vi.fn(), fetchStockOutbound: vi.fn(), fetchStockLoss: vi.fn() }))
const navigation = vi.hoisted(() => ({ push: vi.fn(), toast: vi.fn() }))
vi.mock('../apps/user/src/api/stock', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: navigation.push }) }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: navigation.toast }) }))

const original = (): StockOutbound => ({ outbound_id: 73, outbound_no: 'CK-QA-73', material_type: 'DRUG',
  material_name: '隔离测试兽药', out_type: 'RETURN', out_date: dayjs(today()).subtract(2, 'day').format('YYYY-MM-DD'),
  status: 'NORMAL', amount: 120, version: 3, refund_status: 'UNREFUNDED', actions: { can_refund: true } })
const refunded = (): StockOutbound => ({ ...original(), refund_status: 'REFUNDED', refund_amount: 99.5,
  refund_date: today(), refund_remark: '实际到账', finance_record_id: 703, version: 4,
  actions: { can_refund: false, can_edit_refund: true } })
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r }); return { promise, resolve } }
let key = 0
beforeEach(() => {
  vi.resetAllMocks()
  sessionStorage.clear()
  setActivePinia(createPinia())
  useAuthStore().user = { user_id: 1 }
  const farm = useFarmStore()
  farm.farms = [{ farm_id: 1001, name: '场一', role: 'OWNER' }, { farm_id: 1002, name: '场二', role: 'OWNER' }]
  farm.setCurrent(1001)
  key = 0
  api.newRequestKey.mockImplementation(() => `refund-key-${++key}`)
  api.registerStockRefund.mockResolvedValue(refunded())
  api.updateStockRefund.mockResolvedValue({ ...refunded(), refund_amount: 88, version: 5 })
  api.fetchStockOutbound.mockResolvedValue(original())
})
afterEach(() => vi.restoreAllMocks())

describe('stock return refunds', () => {
  it('starts with a blank actual amount and cancelling performs no write', () => {
    const store = useStockRefundStore()
    store.begin(original())
    expect(store.amount).toBe('')
    store.amount = 99.5
    store.close()
    expect(api.registerStockRefund).not.toHaveBeenCalled()
    expect(api.newRequestKey).not.toHaveBeenCalled()
  })

  it('registers one linked refund and suppresses duplicate concurrent clicks', async () => {
    const pending = deferred<StockOutbound>()
    api.registerStockRefund.mockReturnValueOnce(pending.promise)
    const store = useStockRefundStore()
    store.begin(original()); store.amount = '99.50'; store.remark = ' 实际到账 '
    const first = store.submit()
    await expect(store.submit()).resolves.toBeNull()
    expect(api.registerStockRefund).toHaveBeenCalledOnce()
    expect(api.registerStockRefund).toHaveBeenCalledWith(73, { amount: 99.5, refund_date: today(), remark: '实际到账', version: 3 }, 'refund-key-1')
    pending.resolve(refunded())
    expect((await first)?.finance_record_id).toBe(703)
    expect(store.open).toBe(false)
  })

  it('keeps identical payload and key after uncertain failure, closing and reopening', async () => {
    api.registerStockRefund.mockRejectedValueOnce({ status: 0, message: '连接中断' })
    const store = useStockRefundStore()
    store.begin(original()); store.amount = 99.5
    await store.submit()
    expect(store.fieldsLocked).toBe(true)
    store.close(); store.begin(original())
    expect(store.amount).toBe(99.5)
    store.amount = 120 // UI is disabled; even direct state changes must not change a retry.
    await store.submit()
    expect(api.registerStockRefund.mock.calls[1]).toEqual(api.registerStockRefund.mock.calls[0])
    expect(api.newRequestKey).toHaveBeenCalledOnce()
  })

  it('allows correction after a known server validation rejection', async () => {
    api.registerStockRefund.mockRejectedValueOnce({ status: 422, message: '说明无效', fieldErrors: [{ field: 'remark', message: '请核对说明' }] })
    const store = useStockRefundStore()
    store.begin(original()); store.amount = 99.5
    await store.submit()
    expect(store.fieldsLocked).toBe(false)
    expect(store.errors.remark).toBe('请核对说明')
    store.remark = '已核对'
    await store.submit()
    expect(api.registerStockRefund.mock.calls[1][2]).toBe('refund-key-2')
  })

  it.each(['0', '-1', '0.001', '10000000000', '1e2', 'NaN'])('rejects invalid monetary value %s before making a request', async amount => {
    const store = useStockRefundStore()
    store.begin(original()); store.amount = amount
    await store.submit()
    expect(store.errors.amount).toBeTruthy()
    expect(api.registerStockRefund).not.toHaveBeenCalled()
  })

  it('validates real dates, the return date boundary, future dates and remark length', async () => {
    const store = useStockRefundStore()
    store.begin(original()); store.amount = 1
    for (const date of ['2026-02-30', dayjs(today()).add(1, 'day').format('YYYY-MM-DD'), dayjs(today()).subtract(3, 'day').format('YYYY-MM-DD')]) {
      store.date = date
      await store.submit()
      expect(store.errors.refund_date).toBeTruthy()
    }
    store.date = today(); store.remark = '字'.repeat(201)
    await store.submit()
    expect(store.errors.remark).toBeTruthy()
    expect(api.registerStockRefund).not.toHaveBeenCalled()
  })

  it('does not expose a refund form to staff, feed outbounds, or blocked returns', () => {
    const store = useStockRefundStore()
    store.begin({ ...original(), material_type: 'FEED' }); expect(store.open).toBe(false)
    store.begin({ ...original(), actions: { can_refund: false } }); expect(store.open).toBe(false)
    useFarmStore().farms[0]!.role = 'STAFF'
    store.begin(original()); expect(store.open).toBe(false)
  })

  it('ignores success after switching documents or farms', async () => {
    const pending = deferred<StockOutbound>()
    api.registerStockRefund.mockReturnValueOnce(pending.promise)
    const store = useStockRefundStore()
    store.begin(original()); store.amount = 99.5
    const operation = store.submit()
    store.begin({ ...original(), outbound_id: 74 })
    pending.resolve(refunded())
    await expect(operation).resolves.toBeNull()
    expect(store.document?.outbound_id).toBe(74)
    useFarmStore().setCurrent(1002)
    store.amount = 25
    await expect(store.submit()).resolves.toBeNull()
    expect(api.registerStockRefund).toHaveBeenCalledOnce()
  })

  it('prefills only permitted same-day corrections and updates the original record', async () => {
    const store = useStockRefundStore()
    store.begin({ ...refunded(), actions: { can_edit_refund: false } }, 'edit')
    expect(store.open).toBe(false)
    store.begin(refunded(), 'edit')
    expect(store.amount).toBe(99.5)
    store.amount = 88
    expect((await store.submit())?.finance_record_id).toBe(703)
    expect(api.updateStockRefund).toHaveBeenCalledWith(73, { amount: 88, refund_date: today(), remark: '实际到账', version: 4 }, 'refund-key-1')
    expect(api.registerStockRefund).not.toHaveBeenCalled()
  })

  it('retries a correction with the original idempotency key', async () => {
    api.updateStockRefund.mockRejectedValueOnce({ status: 500, message: '暂时失败' })
    const store = useStockRefundStore()
    store.begin(refunded(), 'edit'); store.amount = 88
    await store.submit(); await store.submit()
    expect(api.updateStockRefund.mock.calls[1]).toEqual(api.updateStockRefund.mock.calls[0])
  })
})

const stubs = {
  'zw-drawer': { props: ['modelValue', 'title'], template: '<section v-if="modelValue"><h2>{{title}}</h2><slot/><slot name="foot"/></section>' },
  'zw-dialog': { props: ['modelValue', 'title'], template: '<section v-if="modelValue"><h2>{{title}}</h2><slot/><slot name="actions"/></section>' },
  'zw-desc': { template: '<dl><slot/></dl>' }, 'zw-desc-item': { template: '<dd><slot/></dd>' },
  'zw-notice': { template: '<aside><slot/></aside>' }, 'zw-tag': { template: '<span><slot/></span>' },
  'v-btn': { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot/></button>' },
  'zw-icon': true, 'v-skeleton-loader': true, 'v-text-field': true, 'v-textarea': true
}
const flow = { flow_id: 1, biz_id: 73, biz_type: 'OUTBOUND', flow_type: 'RETURN' }
describe('refund document drawer', () => {
  it('emits the saved refund so financial list and totals can refresh', async () => {
    const view = mount(StockDocumentDrawer, { props: { modelValue: true, flow }, global: { stubs } })
    await flushPromises()
    await view.findAll('button').find(button => button.text() === '登记退款')!.trigger('click')
    useStockRefundStore().amount = 99.5
    await view.findAll('button').find(button => button.text() === '确认到账并登记')!.trigger('click')
    await flushPromises()
    expect(view.emitted('refund-saved')?.[0]?.[0]).toMatchObject({ outbound_id: 73, finance_record_id: 703, refund_status: 'REFUNDED' })
    expect(view.text()).toContain('已登记退款')
    view.unmount()
  })
  it('loads an initially open drawer and exposes registration, not manual-income guidance', async () => {
    const view = mount(StockDocumentDrawer, { props: { modelValue: true, flow }, global: { stubs } })
    await flushPromises()
    expect(view.text()).toContain('登记退款')
    expect(view.text()).toContain('请勿重复手工记账')
    expect(view.text()).not.toContain('记一笔收入')
    view.unmount()
  })
  it('shows refund status and directs the linked income to its exact record detail', async () => {
    api.fetchStockOutbound.mockResolvedValue(refunded())
    const view = mount(StockDocumentDrawer, { props: { modelValue: true, flow }, global: { stubs } })
    await flushPromises()
    expect(view.text()).toContain('已登记退款')
    expect(view.text()).toContain('99.5 元')
    await view.findAll('button').find(button => button.text() === '查看关联收支流水')!.trigger('click')
    expect(view.emitted('view-finance-record')?.[0]).toEqual([703])
    expect(navigation.push).toHaveBeenCalledWith({ name: 'WU-E02', query: { recordId: '703', date: today(), detail: '1' } })
    view.unmount()
  })
  it('does not let a slow original detail overwrite another document, and clears on farm switch', async () => {
    const slow = deferred<StockOutbound>()
    api.fetchStockOutbound.mockReturnValueOnce(slow.promise).mockResolvedValueOnce({ ...original(), outbound_id: 74, material_name: '新单药品' })
    const view = mount(StockDocumentDrawer, { props: { modelValue: true, flow }, global: { stubs } })
    await view.setProps({ flow: { ...flow, biz_id: 74 } })
    await flushPromises()
    slow.resolve(original()); await flushPromises()
    expect(view.text()).toContain('新单药品')
    expect(view.text()).not.toContain('隔离测试兽药')
    useFarmStore().setCurrent(1002); await flushPromises()
    expect(view.text()).not.toContain('新单药品')
    expect(view.emitted('update:modelValue')?.at(-1)).toEqual([false])
    view.unmount()
  })
  it('hides financial refund controls for staff', async () => {
    const view = mount(StockDocumentDrawer, { props: { modelValue: true, flow, staff: true }, global: { stubs } })
    await flushPromises()
    expect(view.text()).not.toContain('登记退款')
    expect(view.text()).not.toContain('退款到账')
    view.unmount()
  })
})
