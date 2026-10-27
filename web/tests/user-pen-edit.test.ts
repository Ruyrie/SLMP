import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { herdApi } from '../apps/user/src/api/herd'
import { usePenListStore } from '../apps/user/src/stores/herd/penList'

vi.mock('../apps/user/src/api/herd', () => ({ herdApi: {
  penDetail: vi.fn(), penFormOptions: vi.fn(), recordLogs: vi.fn(),
  updatePen: vi.fn(), stockPens: vi.fn()
} }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))
const detail = { pen_id: 5, pen_code: 'YF-05', pen_name: '5 号育肥舍', pen_type: 'FATTEN', category_id: 1,
  capacity: 400, version: 2, display_status: 'EMPTY' }

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  vi.mocked(herdApi.penDetail).mockResolvedValue({ ...detail })
  vi.mocked(herdApi.penFormOptions).mockResolvedValue({ pen_types: [], categories: [] })
  vi.mocked(herdApi.recordLogs).mockResolvedValue([])
  vi.mocked(herdApi.stockPens).mockResolvedValue({ list: [], total: 0, page: 1, size: 200 })
})

describe('pen detail to edit', () => {
  it('replaces the detail drawer with a prefilled edit form', async () => {
    const store = usePenListStore()
    await store.openDetail(5)
    await store.openEdit(5)
    expect(store.detailDrawer).toBe(false)
    expect(store.formDrawer).toBe(true)
    expect(store.form).toMatchObject({ penId: 5, penCode: 'YF-05', penName: '5 号育肥舍', capacity: 400, version: 2 })
  })

  it('saves edited values and refreshes the cached detail', async () => {
    const store = usePenListStore()
    await store.openDetail(5)
    await store.openEdit(5)
    store.form.penName = '新育肥舍'
    store.form.capacity = 450
    const updated = { ...detail, pen_name: '新育肥舍', capacity: 450, version: 3 }
    vi.mocked(herdApi.updatePen).mockResolvedValue(updated)
    vi.mocked(herdApi.penDetail).mockResolvedValue(updated)
    expect(await store.saveForm()).toBe(true)
    expect(herdApi.updatePen).toHaveBeenCalledWith(5, {
      pen_name: '新育肥舍', capacity: 450, pen_type: 'FATTEN', category_id: 1, version: 2
    })
    expect(store.formDrawer).toBe(false)
    expect(store.detail?.pen_name).toBe('新育肥舍')
  })

  it('keeps the detail visible if another pen cannot be loaded for editing', async () => {
    const store = usePenListStore()
    await store.openDetail(5)
    vi.mocked(herdApi.penDetail).mockRejectedValueOnce(new Error('网络错误'))
    await store.openEdit(6)
    expect(store.detailDrawer).toBe(true)
    expect(store.formDrawer).toBe(false)
  })
})
