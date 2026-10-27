import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { healthApi } from '../apps/user/src/api/health'
import { useDisinfectStore, type RecordType } from '../apps/user/src/stores/health/disinfect'

vi.mock('../apps/user/src/api/health', () => ({ healthApi: {
  createDisinfectRecord: vi.fn(), updateDisinfectRecord: vi.fn(), disinfectRecord: vi.fn(),
  disinfectTasks: vi.fn(), disinfectPlans: vi.fn(), disinfectants: vi.fn(), dict: vi.fn(),
  drugLots: vi.fn(), penExecutors: vi.fn()
} }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-27T16:30:00Z'))
  vi.mocked(healthApi.createDisinfectRecord).mockResolvedValue({ id: 9001 })
  vi.mocked(healthApi.updateDisinfectRecord).mockResolvedValue({ id: 9001 })
  vi.mocked(healthApi.disinfectTasks).mockResolvedValue({ list: [], total: 0 })
  vi.mocked(healthApi.disinfectPlans).mockResolvedValue({ list: [], total: 0 })
  vi.mocked(healthApi.disinfectants).mockResolvedValue([])
  vi.mocked(healthApi.dict).mockResolvedValue([])
  vi.mocked(healthApi.drugLots).mockResolvedValue({ selectable: [] })
  vi.mocked(healthApi.penExecutors).mockResolvedValue([])
})
afterEach(() => vi.useRealTimers())

describe('disinfection datetime wire contract', () => {
  it.each(['PLAN_TASK', 'EMPTY_PEN', 'EMERGENCY'] as RecordType[])(
    'submits %s with the server Beijing time format and uploaded photo IDs', async recordType => {
      const store = useDisinfectStore()
      Object.assign(store.registerModel, {
        recordType, taskId: 7001, penId: 2001, actualTime: '2026-10-28T00:24', executorId: 99002,
        drugId: 3001, lotId: 4001, usageQty: '19.5', concentration: '1:500', methodCode: 'SPRAY', fileIds: [81]
      })
      store.taskOptions = [{ task_id: 7001, plan_concentration: '1:500' }]
      store.lots = { selectable: [{ lot_id: 4001, stock: { value: 19.5, unit_text: '升' } }] }
      await store.submitRegister()
      expect(store.registerErrors).toEqual({})
      expect(healthApi.createDisinfectRecord).toHaveBeenCalledWith(expect.objectContaining({
        record_type: recordType, actual_time: '2026-10-28 00:24:00', usage_qty: 19.5, file_ids: [81]
      }))
    }
  )

  it.each([
    ['2026-10-28T00:24', '2026-10-28 00:24:00'],
    ['2026-10-28T00:24:35', '2026-10-28 00:24:35']
  ])('submits an edited datetime %s without a timezone shift', async (input, expected) => {
    const store = useDisinfectStore()
    store.record = { id: 9001, version: 1 }
    Object.assign(store.recordModel, {
      actualTime: input, usageQty: '19.5', concentration: '1:500', methodCode: 'SPRAY', fileIds: [81]
    })
    await store.submitRecordEdit()
    expect(store.recordErrors).toEqual({})
    expect(healthApi.updateDisinfectRecord).toHaveBeenCalledWith(9001, expect.objectContaining({
      actual_time: expected, file_ids: [81]
    }))
  })

  it('loads a saved server time into datetime-local format and rejects future edits', async () => {
    vi.mocked(healthApi.disinfectRecord).mockResolvedValue({
      id: 9001, actual_time: '2026-10-28 00:24:00', usage_qty: 19.5,
      concentration: '1:500', method_code: 'SPRAY', photos: [{ file_id: 81 }]
    })
    const store = useDisinfectStore()
    await store.openRecord(9001, true)
    expect(store.recordModel.actualTime).toBe('2026-10-28T00:24')
    store.recordModel.actualTime = '2026-10-28T00:31'
    await store.submitRecordEdit()
    expect(store.recordErrors.actual_time).toBe('实际时间不能晚于现在')
    expect(healthApi.updateDisinfectRecord).not.toHaveBeenCalled()
  })
})
