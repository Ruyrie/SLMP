import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { usePenListStore } from '../apps/user/src/stores/herd/penList'
import type { PenDetail, RecordLog } from '../apps/user/src/api/herd'

const api = vi.hoisted(() => ({
  penDetail: vi.fn(), recordLogs: vi.fn(), stockPens: vi.fn(), updatePen: vi.fn(),
  disablePen: vi.fn(), enablePen: vi.fn()
}))
vi.mock('../apps/user/src/api/herd', () => ({ herdApi: api }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

const pen = (id: number, version = 1): PenDetail => ({
  pen_id: id, pen_name: `${id} 号保育舍`, pen_code: `BY-0${id}`, pen_type: 'NURSERY',
  category_id: 101, capacity: 360, version
})
const log = (id: number): RecordLog => ({
  id, op_action: 'UPDATE', op_action_text: '修改',
  changes: [{ field_name: '容量', before: '320 头', after: '360 头' }],
  operator: { name: '测试操作人' }
})
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  api.penDetail.mockImplementation(async (id: number) => pen(id))
  api.recordLogs.mockResolvedValue([])
  api.stockPens.mockResolvedValue({ list: [], total: 0 })
})

describe('pen modification history', () => {
  it('renders the server Chinese field and complete quantity without rebuilding raw fields', async () => {
    const store = usePenListStore()
    api.recordLogs.mockResolvedValue([log(10)])
    await store.openDetail(1)
    await flushPromises()
    expect(api.recordLogs).toHaveBeenCalledWith('PEN', 1)
    expect(store.logItems[0].title).toBe('容量：320 头 → 360 头')
  })

  it('refreshes modification history after saving the opened pen', async () => {
    const store = usePenListStore()
    store.detail = pen(1)
    store.formMode = 'edit'
    store.form = { penId: 1, penCode: 'BY-01', penName: '1 号保育舍', penType: 'NURSERY', categoryId: 101, capacity: 400, version: 1 }
    api.updatePen.mockResolvedValue({ ...pen(1, 2), capacity: 400 })
    api.penDetail.mockResolvedValue({ ...pen(1, 2), capacity: 400 })
    api.recordLogs.mockResolvedValue([log(11)])

    expect(await store.saveForm()).toBe(true)
    expect(api.recordLogs).toHaveBeenCalledWith('PEN', 1)
    expect(store.penLogs.map((item) => item.id)).toEqual([11])
    expect(store.detail?.capacity).toBe(400)
  })

  it.each(['disable', 'enable'])('refreshes history after %s', async (action) => {
    const store = usePenListStore()
    store.detail = pen(1)
    store.details[1] = pen(1)
    api.disablePen.mockResolvedValue({ ...pen(1, 2), status: 'DISABLED' })
    api.enablePen.mockResolvedValue({ ...pen(1, 2), status: 'ENABLED' })
    api.recordLogs.mockResolvedValue([log(12)])
    if (action === 'disable') {
      store.askDisable(pen(1))
      await store.confirmDisable()
    } else {
      await store.enablePen(pen(1))
    }
    expect(api.recordLogs).toHaveBeenCalledWith('PEN', 1)
    expect(store.penLogs[0].id).toBe(12)
  })

  it('ignores old history when another pen is opened before it returns', async () => {
    const store = usePenListStore()
    const old = deferred<RecordLog[]>()
    store.detail = pen(1)
    api.recordLogs.mockImplementation((_type: string, id: number) => id === 1 ? old.promise : Promise.resolve([log(20)]))
    const first = store.loadPenLogs(1)
    await store.openDetail(2)
    await flushPromises()
    old.resolve([log(10)])
    await first
    expect(store.detail?.pen_id).toBe(2)
    expect(store.penLogs.map((item) => item.id)).toEqual([20])
    expect(store.logsLoading).toBe(false)
  })

  it('ignores a stale detail response as well as its history', async () => {
    const store = usePenListStore()
    const old = deferred<PenDetail>()
    api.penDetail.mockImplementation((id: number) => id === 1 ? old.promise : Promise.resolve(pen(2)))
    const first = store.openDetail(1)
    await store.openDetail(2)
    old.resolve(pen(1))
    await first
    expect(store.detail?.pen_id).toBe(2)
    expect(api.recordLogs).not.toHaveBeenCalledWith('PEN', 1)
  })

  it('distinguishes a history request error from an empty history and supports retry', async () => {
    const store = usePenListStore()
    store.detail = pen(1)
    api.recordLogs.mockRejectedValueOnce(new Error('网络中断')).mockResolvedValueOnce([log(30)])
    await store.loadPenLogs(1)
    expect(store.logsError).not.toBe('')
    expect(store.logsLoading).toBe(false)
    await store.loadPenLogs(1)
    expect(store.logsError).toBe('')
    expect(store.penLogs[0].id).toBe(30)
  })
})
