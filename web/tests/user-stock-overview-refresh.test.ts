import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, shallowMount } from '@vue/test-utils'
import StockOverviewView from '../apps/user/src/views/herd/StockOverviewView.vue'
import { useStockOverviewStore } from '../apps/user/src/stores/herd/overview'
import { useFarmStore } from '../apps/user/src/stores/farm'

const api = vi.hoisted(() => ({ productionOverview: vi.fn(), stockSummary: vi.fn(), stockPens: vi.fn(), stockCategories: vi.fn(), batches: vi.fn() }))
vi.mock('../apps/user/src/api/herd', () => ({ herdApi: api }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('../apps/user/src/composables/useRole', () => ({ useRole: () => ({ isStaff: false }) }))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

function snapshot(added = 0, name = '东山猪场') {
  return {
    overview: { farm_name: name, farm_status: 'NORMAL', reminder_total: 0, reminders: [] },
    summary: { total_stock: 670 + added, active_batch_count: added ? 4 : 3, unit_text: '头', categories: [{ category_id: 1, category_name: '育肥猪', stock: 670 + added }] },
    pens: [{ pen_id: 2, pen_code: 'BY-02', pen_name: '2号保育舍', stock_count: added, capacity: 400, batch_count: added ? 1 : 0,
      first_batch: added ? { batch_id: 79, batch_no: 'ZM2610-079', current_count: added } : undefined }],
    categories: [{ category_id: 1, category_name: '育肥猪', stock_count: 670 + added, batch_count: added ? 4 : 3 }],
    batches: added ? [{ batch_id: 79, batch_no: 'ZM2610-079', current_count: added }] : []
  }
}

let server = snapshot()
beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
  vi.resetAllMocks()
  const farm = useFarmStore()
  farm.farms = [{ farm_id: 1, name: '东山猪场', role: 'OWNER' }, { farm_id: 2, name: '北湾牛场', role: 'OWNER' }]
  farm.setCurrent(1)
  server = snapshot()
  api.productionOverview.mockImplementation(async () => structuredClone(server.overview))
  api.stockSummary.mockImplementation(async () => structuredClone(server.summary))
  api.stockPens.mockImplementation(async () => ({ list: structuredClone(server.pens), total: server.pens.length }))
  api.stockCategories.mockImplementation(async () => structuredClone(server.categories))
  api.batches.mockImplementation(async () => ({ list: structuredClone(server.batches), total: server.batches.length }))
})

function mountPage() {
  return shallowMount(StockOverviewView, {
    global: {
      stubs: {
        'zw-shell': { template: '<div><slot /></div>' },
        'zw-stat': { props: ['label', 'value'], template: '<div>{{ label }} {{ value }}</div>' },
        ...Object.fromEntries(['v-btn', 'v-data-table', 'v-progress-linear', 'v-select', 'v-skeleton-loader', 'v-tab', 'v-table', 'v-tabs', 'v-text-field',
          'zw-card', 'zw-donut', 'zw-empty', 'zw-icon', 'zw-notice', 'zw-page-head', 'zw-pager', 'zw-tag'].map(name => [name, true]))
      }
    }
  })
}

describe('stock overview refresh after a stock change', () => {
  it('refreshes all totals and the occupied pen when returning after a 200-head entry', async () => {
    const first = mountPage()
    await flushPromises()
    expect(first.text()).toContain('总存栏 670')
    first.unmount()
    server = snapshot(200)
    const returned = mountPage()
    await flushPromises()
    const store = useStockOverviewStore()
    expect(returned.text()).toContain('总存栏 870')
    expect(store.summary?.active_batch_count).toBe(4)
    expect(store.pens[0]).toMatchObject({ stock_count: 200, first_batch: { batch_no: 'ZM2610-079' } })
    expect(store.categories).toEqual(server.categories)
    expect(store.batches).toEqual(server.batches)
    for (const call of Object.values(api)) expect(call).toHaveBeenCalledTimes(2)
    returned.unmount()
  })

  it('does not let a pre-reset old-farm response replace the current farm', async () => {
    const store = useStockOverviewStore()
    const old = deferred<typeof server.summary>()
    api.stockSummary.mockReturnValueOnce(old.promise)
    const pending = store.load()
    useFarmStore().setCurrent(2)
    store.reset()
    server = snapshot(100, '北湾牛场')
    await store.load()
    old.resolve(snapshot().summary)
    await pending
    expect(store.farmLabel).toBe('北湾牛场')
    expect(store.summary?.total_stock).toBe(770)
    expect(store.pens[0].stock_count).toBe(100)
  })

  it('ignores a response after a farm switch even before the next page load starts', async () => {
    const store = useStockOverviewStore()
    const old = deferred<typeof server.summary>()
    api.stockSummary.mockReturnValueOnce(old.promise)
    const pending = store.load()
    useFarmStore().setCurrent(2)
    old.resolve(snapshot().summary)
    await pending
    expect(store.summary).toBeNull()
    expect(store.loaded).toBe(false)
  })

  it('shows a refresh error instead of treating the old snapshot as a successful fresh load', async () => {
    const page = mountPage()
    await flushPromises()
    const store = useStockOverviewStore()
    api.stockSummary.mockRejectedValueOnce(new Error('存栏刷新中断'))
    await store.load(true)
    await flushPromises()
    expect(store.status).toBe('error')
    expect(store.loaded).toBe(false)
    expect(page.text()).not.toContain('总存栏 670')
    server = snapshot(200)
    await store.load()
    expect(store.status).toBe('ready')
    expect(store.summary?.total_stock).toBe(870)
    page.unmount()
  })

  it('keeps the latest successful refresh when an older refresh later fails', async () => {
    const store = useStockOverviewStore()
    await store.load()
    const old = deferred<typeof server.summary>()
    api.stockSummary.mockReturnValueOnce(old.promise)
    const pending = store.load(true)
    server = snapshot(200)
    await store.load(true)
    old.reject(new Error('旧请求失败'))
    await pending
    expect(store.summary?.total_stock).toBe(870)
    expect(store.status).toBe('ready')
    expect(store.error).toBe('')
  })
})
