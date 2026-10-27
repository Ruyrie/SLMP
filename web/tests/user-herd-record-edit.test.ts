import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '@zmt/shared'
import { herdApi, type BatchEventDetail } from '../apps/user/src/api/herd'
import { useHerdRecordEditStore } from '../apps/user/src/stores/herd/recordEdit'
import { todayISO } from '../apps/user/src/stores/herd/shared'

vi.mock('../apps/user/src/api/herd', () => ({ herdApi: {
  batchEvent: vi.fn(), updateEntry: vi.fn(), updateTransfer: vi.fn(), updateDeath: vi.fn(), createBatch: vi.fn()
} }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))
const record = (): BatchEventDetail => ({ event_id: 41, batch_id: 12, event_type: 'ENTRY', event_date: todayISO(),
  quantity: 200, version: 7, pen: { pen_id: 8, name: '2 号保育舍' }, change_state: { can_edit: true },
  entry: { breed_id: 3, purpose: 'COMMERCIAL', source: 'PURCHASED', entry_age_days: 70,
    entry_avg_weight: 12, intro_unit_price: 1000, batch_version: 13 } })

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  vi.mocked(herdApi.batchEvent).mockResolvedValue(record())
})

describe('herd direct record editing', () => {
  it('loads original entry values and updates the existing batch with the batch version', async () => {
    const store = useHerdRecordEditStore()
    await store.open(41, 12, 'ENTRY')
    expect(store.form).toMatchObject({ quantity: 200, entryAgeDays: 70, introUnitPrice: 1000 })
    store.setQuantity(199)
    expect(await store.save()).toBe(true)
    expect(herdApi.updateEntry).toHaveBeenCalledWith(12, { pen_id: 8, breed_id: 3, purpose: 'COMMERCIAL',
      source: 'PURCHASED', entry_date: todayISO(), entry_count: 199, entry_age_days: 70,
      entry_avg_weight: 12, intro_unit_price: 1000, capacity_confirmed: false, version: 13 })
    expect(herdApi.createBatch).not.toHaveBeenCalled()
    expect(store.visible).toBe(false)
  })

  it('keeps both pens and the event version when updating a transfer', async () => {
    vi.mocked(herdApi.batchEvent).mockResolvedValue({ ...record(), event_type: 'TRANSFER', to_pen: { pen_id: 9 }, remark: '原备注' })
    const store = useHerdRecordEditStore()
    await store.open(41, 12, 'TRANSFER')
    store.setQuantity(180)
    expect(await store.save()).toBe(true)
    expect(herdApi.updateTransfer).toHaveBeenCalledWith(41, { from_pen_id: 8, to_pen_id: 9, quantity: 180,
      event_date: todayISO(), remark: '原备注', capacity_confirmed: false, version: 7 })
  })

  it('retains death reason, executor, photos and remark when changing only quantity', async () => {
    vi.mocked(herdApi.batchEvent).mockResolvedValue({ ...record(), event_type: 'DEATH', quantity: 4,
      reason_code: 'SICK', remark: '原始病情', executor: { user_id: 66 }, photos: [{ file_id: 9 }, { file_id: 10 }] })
    const store = useHerdRecordEditStore()
    await store.open(41, 12, 'DEATH')
    store.setQuantity(3)
    expect(await store.save()).toBe(true)
    expect(herdApi.updateDeath).toHaveBeenCalledWith(41, { pen_id: 8, quantity: 3, event_date: todayISO(),
      reason_code: 'SICK', executor_user_id: 66, file_ids: [9, 10], remark: '原始病情', version: 7 })
  })

  it.each([
    { batch_id: 99 }, { event_id: 99 }, { event_type: 'DEATH' }, { change_state: { can_edit: false } }
  ])('refuses records whose identity or current permissions changed: %j', async changed => {
    vi.mocked(herdApi.batchEvent).mockResolvedValue({ ...record(), ...changed })
    const store = useHerdRecordEditStore()
    await store.open(41, 12, 'ENTRY')
    expect(store.record).toBeNull()
    expect(store.error).toBeTruthy()
    expect(await store.save()).toBe(false)
    expect(herdApi.updateEntry).not.toHaveBeenCalled()
  })

  it('does not replace a newly opened record with an earlier request response', async () => {
    let resolve!: (value: BatchEventDetail) => void
    vi.mocked(herdApi.batchEvent).mockReturnValueOnce(new Promise(done => { resolve = done }))
    const store = useHerdRecordEditStore()
    const first = store.open(41, 12, 'ENTRY')
    vi.mocked(herdApi.batchEvent).mockResolvedValueOnce({ ...record(), event_id: 42 })
    await store.open(42, 12, 'ENTRY')
    resolve(record())
    await first
    expect(store.record?.event_id).toBe(42)
  })

  it('does not reopen the editor after the farm or batch view was reset', async () => {
    let resolve!: (value: BatchEventDetail) => void
    vi.mocked(herdApi.batchEvent).mockReturnValueOnce(new Promise(done => { resolve = done }))
    const store = useHerdRecordEditStore()
    const loading = store.open(41, 12, 'ENTRY')
    store.reset()
    resolve(record())
    await loading
    expect(store.visible).toBe(false)
    expect(store.record).toBeNull()
  })

  it('requires explicit capacity confirmation and clears it after quantity changes', async () => {
    vi.mocked(herdApi.updateEntry).mockRejectedValueOnce(new ApiError({ code: 42206, message: '容量超限',
      data: { pen_name: '2 号保育舍', after: 500, capacity: 400 } }))
    const store = useHerdRecordEditStore()
    await store.open(41, 12, 'ENTRY')
    store.setQuantity(500)
    expect(await store.save()).toBe(false)
    expect(store.capacityMessage).toContain('500')
    expect(await store.save()).toBe(false)
    expect(herdApi.updateEntry).toHaveBeenCalledTimes(1)
    store.form.capacityConfirmed = true
    store.setQuantity(501)
    expect(store.form.capacityConfirmed).toBe(false)
    store.form.capacityConfirmed = true
    expect(await store.save()).toBe(true)
    expect(vi.mocked(herdApi.updateEntry).mock.lastCall?.[1].capacity_confirmed).toBe(true)
  })

  it('preserves edits on a version conflict without retrying or creating a new batch', async () => {
    vi.mocked(herdApi.updateEntry).mockRejectedValue(new ApiError({ code: 40901, message: '记录已更新，请重新加载' }))
    const store = useHerdRecordEditStore()
    await store.open(41, 12, 'ENTRY')
    store.setQuantity(199)
    expect(await store.save()).toBe(false)
    expect(store.visible).toBe(true)
    expect(store.form.quantity).toBe(199)
    expect(store.error).toContain('重新加载')
    expect(herdApi.updateEntry).toHaveBeenCalledTimes(1)
    expect(herdApi.createBatch).not.toHaveBeenCalled()
  })

  it('rejects fractional quantities and malformed dates before sending an update', async () => {
    const store = useHerdRecordEditStore()
    await store.open(41, 12, 'ENTRY')
    store.setQuantity(1.5)
    store.form.eventDate = 'Tue Oct 27 2026'
    expect(await store.save()).toBe(false)
    expect(store.errors.quantity).toBeTruthy()
    expect(store.errors.event_date).toBeTruthy()
    expect(herdApi.updateEntry).not.toHaveBeenCalled()
  })
})
