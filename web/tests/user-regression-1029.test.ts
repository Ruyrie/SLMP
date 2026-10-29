import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { useFarmStore } from '../apps/user/src/stores/farm'
import { usePenListStore } from '../apps/user/src/stores/herd/penList'
import { useHerdEntryStore } from '../apps/user/src/stores/herd/entry'
import { useBreedingPendingStore } from '../apps/user/src/stores/breeding/pending'
import { useDiagnosisStore } from '../apps/user/src/stores/inspect/diagnosis'
import { useSettlementStore } from '../apps/user/src/stores/finance/settlement'

const herd = vi.hoisted(() => ({
  stockPens: vi.fn(), penDetail: vi.fn(), batches: vi.fn(), penOptions: vi.fn(), penFormOptions: vi.fn(),
  baseCategories: vi.fn(), batchDetail: vi.fn(), batchPenStocks: vi.fn(), penExecutors: vi.fn()
}))
const breeding = vi.hoisted(() => ({ overview: vi.fn(), farrowDue: vi.fn(), hatchDue: vi.fn() }))
const inspect = vi.hoisted(() => ({
  fetchDiagnosis: vi.fn(), fetchDiagnosisState: vi.fn(), fetchRecordLogs: vi.fn(), rediagnose: vi.fn(), saveHandle: vi.fn()
}))
const finance = vi.hoisted(() => ({ settlementOptions: vi.fn(), settlementDetail: vi.fn() }))
vi.mock('../apps/user/src/api/herd', () => ({ herdApi: herd }))
vi.mock('../apps/user/src/api/breeding', () => ({ breedingApi: breeding }))
vi.mock('../apps/user/src/api/inspect', () => inspect)
vi.mock('../apps/user/src/api/finance', () => ({ financeApi: finance }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
  vi.resetAllMocks()
  const farm = useFarmStore()
  farm.farms = [{ farm_id: 1001, name: '东山猪场', role: 'OWNER' }, { farm_id: 1002, name: '西岭鸡场', role: 'OWNER' }]
  farm.setCurrent(1001)
  const farmPens = () => [{ pen_id: useFarmStore().currentFarmId, pen_name: useFarmStore().farmName, unit_text: '头' }]
  herd.stockPens.mockImplementation(async () => ({ list: farmPens(), total: 1 }))
  herd.penDetail.mockImplementation(async (id: number) => ({ pen_id: id, pen_name: `${id}` }))
  herd.batches.mockResolvedValue({ list: [], total: 0 })
  herd.penOptions.mockImplementation(async () => farmPens())
  herd.penFormOptions.mockResolvedValue({ categories: [] })
  herd.baseCategories.mockResolvedValue([])
})

describe('H5 切场后进入未挂载页面不再显示上一个场的数据', () => {
  it('栏舍列表带缓存进入时按新场重新加载', async () => {
    const store = usePenListStore()
    await store.load()
    expect(store.pens[0].pen_name).toBe('东山猪场')
    useFarmStore().setCurrent(1002)
    await store.load()
    expect(herd.stockPens).toHaveBeenCalledTimes(2)
    expect(store.pens[0].pen_name).toBe('西岭鸡场')
  })

  it('切场前发出的旧请求晚到时被丢弃', async () => {
    const store = usePenListStore()
    const old = deferred<{ list: Array<{ pen_id: number; pen_name: string }>; total: number }>()
    herd.stockPens.mockReturnValueOnce(old.promise)
    const first = store.load()
    useFarmStore().setCurrent(1002)
    await store.load()
    old.resolve({ list: [{ pen_id: 1001, pen_name: '东山猪场' }], total: 1 })
    await first
    expect(store.pens[0].pen_name).toBe('西岭鸡场')
  })

  it('批次核算切场后不沿用上一个场的批次', async () => {
    finance.settlementOptions.mockImplementation(async () => ({
      list: [{ batchId: useFarmStore().currentFarmId * 10 }], farmPendingSupplementCount: 0
    }))
    finance.settlementDetail.mockImplementation(async (id: number) => ({ batch: { batchId: id } }))
    const store = useSettlementStore()
    await store.load()
    expect(store.batchId).toBe(10010)
    useFarmStore().setCurrent(1002)
    await store.load()
    expect(store.batchId).toBe(10020)
    expect(finance.settlementDetail).toHaveBeenLastCalledWith(10020)
  })
})

describe('H6 转栏 / 死淘选批次后退出骨架屏', () => {
  it('批次上下文加载完成后响应式 getter 拿到批次', async () => {
    herd.batchDetail.mockResolvedValue({ batch_id: 7, batch_no: 'ZM-7', pens: [{ pen_id: 1, pen_name: '1 号', stock_count: 30 }] })
    herd.batchPenStocks.mockResolvedValue([{ pen_id: 1, stock_count: 30 }])
    const store = useHerdEntryStore()
    const loading = store.selectTransferBatch(7)
    expect(store.transferContext.loading).toBe(true)
    expect(store.transferBatch).toBeNull()
    await loading
    expect(store.transferContext.loading).toBe(false)
    expect(store.transferBatch?.batch_no).toBe('ZM-7')
    expect(store.transferForm.fromPenId).toBe(1)
  })

  it('换批次后旧批次的结果不再覆盖', async () => {
    const old = deferred<{ batch_id: number }>()
    herd.batchDetail.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ batch_id: 8, pens: [] })
    herd.batchPenStocks.mockResolvedValue([])
    const store = useHerdEntryStore()
    const first = store.selectDeathBatch(7)
    await store.selectDeathBatch(8)
    old.resolve({ batch_id: 7 })
    await first
    expect(store.deathBatch?.batch_id).toBe(8)
  })
})

describe('M12 入栏表单栏舍选项跟着改名 / 停用更新', () => {
  it('带缓存再进入和打开入栏表单时重新拉栏舍', async () => {
    const store = useHerdEntryStore()
    await store.load(false)
    expect(store.penOptions[0].pen_name).toBe('东山猪场')
    herd.penOptions.mockResolvedValue([{ pen_id: 1001, pen_name: '改名后的栏舍' }])
    await store.load(false)
    await flushPromises()
    expect(store.penOptions[0].pen_name).toBe('改名后的栏舍')
    herd.penOptions.mockResolvedValue([])
    await store.openEntry()
    expect(store.entryPenItems).toEqual([])
  })
})

describe('M9 待产列表登记 / 翻页 / 搜索后仍按当前页签请求', () => {
  it('不带参数刷新时沿用当前页签', async () => {
    breeding.overview.mockResolvedValue({})
    breeding.farrowDue.mockResolvedValue({ list: [{ animalId: 1 }], total: 1 })
    breeding.hatchDue.mockResolvedValue({ list: [], total: 0 })
    const store = useBreedingPendingStore()
    await store.load()
    store.query.keyword = '0142'
    store.setKeyword()
    await flushPromises()
    store.setPage(2)
    await flushPromises()
    await store.refresh()
    expect(breeding.hatchDue).not.toHaveBeenCalled()
    expect(breeding.farrowDue).toHaveBeenCalledTimes(4)
    expect(store.dueList).toHaveLength(1)
  })

  it('配种不足 18 天登记妊检阳性在本地拦下', async () => {
    const store = useBreedingPendingStore()
    store.checkForm = { checkDate: '2026-10-20', result: 'POSITIVE' }
    const ok = await store.submitCheck({ animalId: 1, earTag: '0142', firstMatingDate: '2026-10-10' } as never)
    expect(ok).toBe(false)
    expect(store.checkErrors.result).toBe('配种后满 18 天才能登记妊检阳性')
  })
})

describe('M10 看过诊断详情后新诊断仍然轮询', () => {
  it('dispose 后再 load 会恢复轮询', async () => {
    vi.useFakeTimers()
    try {
      inspect.fetchDiagnosis.mockResolvedValue({ report_id: 2, status: 'PENDING', ai_state: 'DIAGNOSING' })
      inspect.fetchDiagnosisState.mockResolvedValue({ report_id: 2, status: 'PENDING', ai_state: 'DIAGNOSING' })
      const store = useDiagnosisStore()
      await store.load(1)
      store.dispose()
      await store.load(2)
      await vi.advanceTimersByTimeAsync(3100)
      expect(inspect.fetchDiagnosisState).toHaveBeenCalledWith(2)
      store.dispose()
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('M11 全部养殖场视角下点当前场退回单场', () => {
  it('switchTo 当前场时关闭全部视角', async () => {
    const farm = useFarmStore()
    farm.setViewAll(true)
    await farm.switchTo(1001)
    expect(farm.viewAll).toBe(false)
  })
})
