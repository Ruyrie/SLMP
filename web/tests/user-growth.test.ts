import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { growthAgeLabels, growthAgeLabelInterval, growthComparisonNotices, growthWeightRange, standardAt, standardBandAt, standardReferenceDay } from '../apps/user/src/views/feeding/growthChart'
import { useWeighStore } from '../apps/user/src/stores/feeding/weigh'

const api = vi.hoisted(() => ({ weighBatchOptions: vi.fn(), growthCurve: vi.fn(), weighRecords: vi.fn(), compareOptions: vi.fn(), updateWeighCycle: vi.fn() }))
vi.mock('../apps/user/src/api/feeding', () => ({ feedingApi: api }))
vi.mock('../apps/user/src/api/herd', () => ({ herdApi: {} }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  api.weighBatchOptions.mockResolvedValue([{ batch_id: 1 }])
  api.growthCurve.mockImplementation((batchId: number) => Promise.resolve({ batch: { batch_id: batchId }, actual_points: [] }))
  api.weighRecords.mockResolvedValue({ list: [], total: 0 })
  api.compareOptions.mockResolvedValue([])
})

describe('growth chart', () => {
  it('includes comparison ages outside the main batch range and keeps age labels sparse', () => {
    const labels = growthAgeLabels({ x_min: 150, x_max: 387,
      actual_points: [{ day_age: 372, avg_weight: 20.8 }],
      compare_series: [{ batch_no: 'ZM2610-026', points: [{ day_age: 1, avg_weight: 0.04 }, { day_age: 10, avg_weight: 0.26 }] }] })
    expect(labels).toContain('1')
    expect(labels).toContain('372')
    const interval = growthAgeLabelInterval(labels.length)
    expect((labels.length - 1) / interval).toBeLessThanOrEqual(6)
    expect((labels.length - 1) % interval).toBe(0)
  })

  it('explains empty and single point comparisons without inventing a curve', () => {
    expect(growthComparisonNotices({ compare_series: [
      { batch_no: '空批次', points: [] },
      { batch_no: '单点批次', points: [{ day_age: 14, avg_weight: 0.38 }] },
      { batch_no: '多点批次', points: [{ day_age: 1, avg_weight: 0.04 }, { day_age: 14, avg_weight: 0.38 }] }
    ] })).toEqual(['空批次 暂无可对比的称重数据', '单点批次 仅有 1 个数据点，已显示为圆点'])
  })

  it('uses fractional kilogram ticks for poultry weights', () => {
    const range = growthWeightRange([0.04, 0.38])
    expect(range.min).toBeLessThanOrEqual(0.04)
    expect(range.max).toBeGreaterThanOrEqual(0.38)
    expect(range.max).toBeLessThan(1)
  })

  it('does not invent a standard outside the published ages', () => {
    const points = [{ day_age: 70, std_weight: 43 }, { day_age: 80, std_weight: 51 }]
    expect(standardAt(points, 75)).toBe(47)
    expect(standardAt(points, 69)).toBeNull()
    expect(standardAt(points, 81)).toBeNull()
  })

  it('compares the standard with the age of the latest weighing instead of today', () => {
    expect(standardReferenceDay({ current_day_age: 20, metrics: { day_age: 17 } })).toBe(17)
    expect(standardReferenceDay({ current_day_age: 20 })).toBe(20)
  })

  it('uses the published allowed band instead of always showing ten percent', () => {
    const points = [{ day_age: 10, std_weight: 1, lower: 0.8, upper: 1.2 }, { day_age: 20, std_weight: 2, lower: 1.6, upper: 2.4 }]
    const band = standardBandAt({ std_available: true, std_points: points }, 15)
    expect(band?.lower).toBeCloseTo(1.2)
    expect(band?.upper).toBeCloseTo(1.8)
    expect(standardBandAt({ std_available: true, std_points: [{ day_age: 10, std_weight: 1 }], metrics: { allowed_deviation: 0.2 } }, 10)).toEqual({ lower: 0.8, upper: 1.2 })
  })

  it('shows a curve request failure and permits retry instead of caching an empty page', async () => {
    const store = useWeighStore()
    api.growthCurve.mockRejectedValueOnce(new Error('网络暂时中断'))
    await store.load()
    expect(store.status).toBe('error')
    expect(store.error).toBe('网络暂时中断')
    expect(store.loaded).toBe(false)
    await store.load()
    expect(store.status).toBe('ready')
    expect(store.loaded).toBe(true)
  })

  it('keeps batch option errors distinct from an empty farm', async () => {
    const store = useWeighStore()
    api.weighBatchOptions.mockRejectedValueOnce(new Error('称重批次暂时不可用'))
    await store.load()
    expect(store.status).toBe('error')
    expect(store.error).toBe('称重批次暂时不可用')
    expect(api.growthCurve).not.toHaveBeenCalled()
  })

  it('does not display a stale batch response after switching batches', async () => {
    const store = useWeighStore()
    let finishOld: (value: unknown) => void = () => {}
    api.growthCurve.mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve }))
    const old = store.setBatch(1)
    await store.setBatch(2)
    finishOld({ batch: { batch_id: 1 }, actual_points: [] })
    await old
    expect(store.batchId).toBe(2)
    expect(store.curve?.batch?.batch_id).toBe(2)
  })

  it('shows a record loading error independently of the curve', async () => {
    const store = useWeighStore()
    api.weighRecords.mockRejectedValueOnce(new Error('记录加载中断'))
    await store.load()
    expect(store.status).toBe('ready')
    expect(store.recordsError).toBe('记录加载中断')
    await store.loadRecords()
    expect(store.recordsError).toBe('')
  })

  it('validates the supported monthly weighing frequency before submitting', async () => {
    const store = useWeighStore()
    store.batchId = 1
    for (const value of [9, 2.5]) {
      store.cycleInput = value
      await store.saveCycle()
      expect(store.cycleError).toBe('称重周期需为 1–8 次/月')
    }
    expect(api.updateWeighCycle).not.toHaveBeenCalled()
  })
})
