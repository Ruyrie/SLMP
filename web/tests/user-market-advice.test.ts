import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '@zmt/shared'
import { useMarketStore } from '../apps/user/src/stores/finance/market'
import type { SaleAdvice, SaleOrderBatchOption } from '../apps/user/src/api/finance'

const api = vi.hoisted(() => ({ marketFilters: vi.fn(), marketTrend: vi.fn(), marketDaily: vi.fn(),
  batchOptions: vi.fn(), saleAdviceLatest: vi.fn(), saleAdviceHistory: vi.fn(), saleAdviceDetail: vi.fn(),
  regenerateSaleAdvice: vi.fn(), toast: vi.fn() }))
vi.mock('../apps/user/src/api/finance', () => ({ financeApi: api }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: api.toast }) }))

const batches = [{ batchId: 301, batchNo: 'A-301', currentCount: 30 }, { batchId: 302, batchNo: 'A-302', currentCount: 20 }]
const success = (id = 71, batchId = 301): SaleAdvice => ({ id, batchId, status: 'SUCCESS', conclusionText: '继续饲养' })
const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  api.marketFilters.mockResolvedValue({ provinces: [{ code: '44', name: '广东' }], categories: [{ id: 1, name: '猪' }], breeds: [] })
  api.marketTrend.mockResolvedValue({ empty: true })
  api.marketDaily.mockResolvedValue({ list: [], total: 0 })
  api.batchOptions.mockResolvedValue(batches)
  api.saleAdviceLatest.mockResolvedValue(success())
  api.saleAdviceHistory.mockResolvedValue({ list: [], total: 0 })
  api.saleAdviceDetail.mockResolvedValue(success())
  api.regenerateSaleAdvice.mockResolvedValue(success())
})
afterEach(() => {
  useMarketStore().dispose()
  vi.useRealTimers()
})

describe('market sale advice lifecycle', () => {
  it('waits for slow batch options and loads advice while history loads independently', async () => {
    const slow = deferred<SaleOrderBatchOption[]>()
    api.batchOptions.mockReturnValueOnce(slow.promise)
    const store = useMarketStore()
    const loading = store.load()
    await Promise.resolve()
    expect(store.batchStatus).toBe('loading')
    expect(store.historyStatus).toBe('empty')
    expect(api.saleAdviceLatest).not.toHaveBeenCalled()
    slow.resolve(batches)
    await loading
    expect(api.saleAdviceLatest).toHaveBeenCalledWith(301)
    expect(store.batchId).toBe(301)
    expect(store.adviceStatus).toBe('ready')
  })

  it('finishes both empty states without ever requesting AI for a batch id of zero', async () => {
    api.batchOptions.mockResolvedValue([])
    const store = useMarketStore()
    await store.load()
    await store.regenerate()
    expect(store.batchStatus).toBe('empty')
    expect(store.batchId).toBe(0)
    expect(store.adviceStatus).toBe('empty')
    expect(store.historyStatus).toBe('empty')
    expect(api.saleAdviceLatest).not.toHaveBeenCalled()
    expect(api.regenerateSaleAdvice).not.toHaveBeenCalled()
  })

  it('shows a batch loading failure instead of claiming the farm has no batches and retries', async () => {
    api.batchOptions.mockRejectedValueOnce(new ApiError({ code: -1, message: '网络超时' }))
    const store = useMarketStore()
    await store.load()
    expect(store.batchStatus).toBe('error')
    expect(store.batchError).toBe('网络超时')
    expect(store.adviceStatus).toBe('error')
    await store.loadBatches()
    expect(store.batchStatus).toBe('ready')
    expect(store.adviceStatus).toBe('ready')
  })

  it('fetches batches again on farm reload and ignores the old farm batch response', async () => {
    const old = deferred<SaleOrderBatchOption[]>()
    api.batchOptions.mockReturnValueOnce(old.promise)
    const store = useMarketStore()
    const first = store.load(301)
    api.batchOptions.mockResolvedValueOnce([{ batchId: 901, batchNo: 'NEW-901', currentCount: 5 }])
    api.saleAdviceLatest.mockResolvedValueOnce(success(91, 901))
    await store.load(0)
    old.resolve(batches)
    await first
    expect(api.batchOptions).toHaveBeenCalledTimes(2)
    expect(store.batchId).toBe(901)
    expect(store.advice?.batchId).toBe(901)
    expect(api.saleAdviceLatest).not.toHaveBeenCalledWith(301)
  })

  it('ignores an old batch advice response after another batch is selected', async () => {
    const old = deferred<SaleAdvice>()
    api.saleAdviceLatest.mockReturnValueOnce(old.promise)
    const store = useMarketStore()
    const loading = store.load()
    await Promise.resolve()
    api.saleAdviceLatest.mockResolvedValueOnce(success(72, 302))
    await store.selectBatch(302)
    old.resolve(success())
    await loading
    expect(store.advice?.batchId).toBe(302)
    expect(store.adviceStatus).toBe('ready')
  })

  it('ends stuck generation polling with a retryable error instead of a permanent skeleton', async () => {
    vi.useFakeTimers()
    api.saleAdviceLatest.mockResolvedValue({ id: 71, batchId: 301, status: 'GENERATING' })
    api.saleAdviceDetail.mockResolvedValue({ id: 71, batchId: 301, status: 'GENERATING' })
    const store = useMarketStore()
    await store.load()
    // 仍在生成时继续等待（服务端最长 120s），不在 20s 内提前判失败
    await vi.advanceTimersByTimeAsync(20_000)
    expect(store.adviceStatus).toBe('loading')
    await vi.advanceTimersByTimeAsync(140_000)
    expect(api.saleAdviceDetail.mock.calls.length).toBeGreaterThan(10)
    expect(store.adviceStatus).toBe('error')
    expect(store.adviceError).toContain('稍后重试')
    expect(store.pollTimer).toBeUndefined()
    api.saleAdviceLatest.mockResolvedValue(success())
    await store.loadAdvice()
    expect(store.adviceStatus).toBe('ready')
  })

  it('does not let an in-flight old poll overwrite a new batch or start another timer', async () => {
    vi.useFakeTimers()
    const old = deferred<SaleAdvice>()
    api.saleAdviceLatest.mockResolvedValueOnce({ id: 71, batchId: 301, status: 'GENERATING' })
    api.saleAdviceDetail.mockReturnValueOnce(old.promise)
    const store = useMarketStore()
    await store.load()
    await vi.advanceTimersByTimeAsync(1200)
    api.saleAdviceLatest.mockResolvedValueOnce(success(72, 302))
    await store.selectBatch(302)
    old.resolve({ id: 71, batchId: 301, status: 'GENERATING' })
    await vi.advanceTimersByTimeAsync(20_000)
    expect(store.advice?.batchId).toBe(302)
    expect(api.saleAdviceDetail).toHaveBeenCalledTimes(1)
    expect(store.pollTimer).toBeUndefined()
  })

  it('keeps actual history errors distinct from an empty history', async () => {
    api.batchOptions.mockResolvedValue([])
    api.saleAdviceHistory.mockRejectedValueOnce(new ApiError({ code: 50000, message: '历史加载失败' }))
    const store = useMarketStore()
    await store.load()
    expect(store.historyStatus).toBe('error')
    expect(store.historyError).toBe('历史加载失败')
    await store.loadHistory()
    expect(store.historyStatus).toBe('empty')
  })

  it('preserves ended-batch errors instead of showing a false empty farm', async () => {
    api.saleAdviceLatest.mockRejectedValueOnce(new ApiError({ code: 42211, message: '该批次已结束' }))
    const store = useMarketStore()
    await store.load()
    expect(store.batchStatus).toBe('ready')
    expect(store.adviceStatus).toBe('error')
    expect(store.adviceError).toBe('该批次已结束')
  })

  it('does not announce success when regeneration fails and guards repeated clicks', async () => {
    const store = useMarketStore()
    await store.load()
    const pending = deferred<SaleAdvice>()
    api.regenerateSaleAdvice.mockReturnValueOnce(pending.promise)
    const generating = store.regenerate()
    await store.regenerate()
    expect(api.regenerateSaleAdvice).toHaveBeenCalledTimes(1)
    pending.resolve({ id: 99, batchId: 301, status: 'FAILED' })
    await generating
    expect(store.adviceStatus).toBe('unavailable')
    expect(store.adviceGenerating).toBe(false)
    expect(api.toast).not.toHaveBeenCalledWith('已重新生成出栏建议')
  })

  it('ignores late responses after leaving the page', async () => {
    const pending = deferred<SaleAdvice>()
    api.saleAdviceLatest.mockReturnValueOnce(pending.promise)
    const store = useMarketStore()
    const loading = store.load()
    await Promise.resolve()
    store.dispose()
    pending.resolve(success())
    await loading
    expect(store.advice).toBeNull()
    expect(store.pollTimer).toBeUndefined()
  })
})
