import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { useBatchListStore } from '../apps/user/src/stores/herd/batchList'
import { useFarmStore } from '../apps/user/src/stores/farm'
import { useAuthStore } from '../apps/user/src/stores/auth'
import { setupHttp } from '../apps/user/src/api/client'

const api = vi.hoisted(() => ({ batches: vi.fn(), myPens: vi.fn(), penOptions: vi.fn(), baseCategories: vi.fn(), lookupBatch: vi.fn() }))
vi.mock('../apps/user/src/api/herd', () => ({ herdApi: api }))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
function page(id?: number) {
  return { list: id ? [{ batch_id: id, batch_no: `BATCH-${id}`, current_count: 870 }] : [],
    total: id ? 1 : 0, tab_counts: { ACTIVE: id ? 1 : 0, ENDED: 0, ALL: id ? 1 : 0 } }
}

beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
  vi.resetAllMocks()
  const farm = useFarmStore()
  farm.farms = [{ farm_id: 1001, name: '东山猪场', role: 'OWNER' }, { farm_id: 1002, name: '北湾牛场', role: 'OWNER' }]
  farm.setCurrent(1001)
  api.batches.mockResolvedValue(page(1))
  api.myPens.mockResolvedValue({ pens: [], pen_count: 0 })
  api.penOptions.mockImplementation(async () => [{ pen_id: useFarmStore().currentFarmId, pen_name: useFarmStore().farmName }])
  api.baseCategories.mockResolvedValue([{ id: 101, name: '测试品类' }])
})

describe('batch data belongs to the current farm', () => {
  it('reloads a cached inactive page and immediately clears the previous farm rows, counts and filters', async () => {
    const store = useBatchListStore()
    await store.load(false)
    await flushPromises()
    store.query.penId = 2001
    store.query.categoryId = 101
    store.query.page = 3
    expect(store.list[0].batch_id).toBe(1)
    expect(store.penOptions).toHaveLength(1)
    const next = deferred<ReturnType<typeof page>>()
    api.batches.mockReturnValueOnce(next.promise)
    useFarmStore().setCurrent(1002)
    const pending = store.load(false)
    expect(store.list).toEqual([])
    expect(store.total).toBe(0)
    expect(store.activeCount).toBe(0)
    expect(store.penOptions).toEqual([])
    expect(store.query).toMatchObject({ penId: 0, categoryId: 0, page: 1 })
    next.resolve(page())
    await pending
    await flushPromises()
    expect(store.status).toBe('empty')
    expect(store.list).toEqual([])
    expect(store.penOptions[0].pen_name).toBe('北湾牛场')
    expect(api.batches).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, penId: undefined, categoryId: undefined }))
  })

  it('does not let the old farm response win after reset and a newer empty-farm load', async () => {
    const store = useBatchListStore()
    const old = deferred<ReturnType<typeof page>>()
    api.batches.mockReturnValueOnce(old.promise).mockResolvedValueOnce(page())
    const pending = store.load(false)
    useFarmStore().setCurrent(1002)
    store.reset()
    await store.load(false)
    old.resolve(page(1))
    await pending
    expect(store.list).toEqual([])
    expect(store.total).toBe(0)
    expect(store.allCount).toBe(0)
    expect(store.status).toBe('empty')
  })

  it('does not restore stale rows or errors when the new farm request fails', async () => {
    const store = useBatchListStore()
    await store.load(false)
    useFarmStore().setCurrent(1002)
    api.batches.mockRejectedValueOnce(new Error('新场网络中断'))
    await store.load(false)
    expect(store.list).toEqual([])
    expect(store.tabCounts).toEqual({})
    expect(store.status).toBe('error')
  })

  it('rejects the oldest response after switching A to B and back to A', async () => {
    const store = useBatchListStore()
    const oldA = deferred<ReturnType<typeof page>>()
    api.batches.mockReturnValueOnce(oldA.promise).mockResolvedValueOnce(page()).mockResolvedValueOnce(page(3))
    const pending = store.load(false)
    useFarmStore().setCurrent(1002)
    await store.load(false)
    useFarmStore().setCurrent(1001)
    await store.load(false)
    oldA.resolve(page(1))
    await pending
    expect(store.list.map((item) => item.batch_id)).toEqual([3])
  })

  it('keeps the newest filter response and ignores an older filter error', async () => {
    const store = useBatchListStore()
    await store.load(false)
    const old = deferred<ReturnType<typeof page>>()
    api.batches.mockReturnValueOnce(old.promise).mockResolvedValueOnce(page(4))
    store.query.status = 'ENDED'
    const pending = store.fetchList()
    store.query.status = 'ALL'
    await store.fetchList()
    old.reject(new Error('旧筛选请求失败'))
    await pending
    expect(store.list.map((item) => item.batch_id)).toEqual([4])
    expect(store.status).toBe('ready')
    expect(store.error).toBe('')
  })

  it('does not copy delayed old-farm filter options into the new farm', async () => {
    const store = useBatchListStore()
    const oldPens = deferred<Array<{ pen_id: number; pen_name: string }>>()
    api.penOptions.mockReturnValueOnce(oldPens.promise)
    await store.load(false)
    useFarmStore().setCurrent(1002)
    await store.load(false)
    await flushPromises()
    oldPens.resolve([{ pen_id: 2001, pen_name: '猪场栏舍' }])
    await flushPromises()
    expect(store.penOptions.map((item) => item.pen_name)).toEqual(['北湾牛场'])
  })

  it('clears owner data when the current farm role becomes staff', async () => {
    const store = useBatchListStore()
    await store.load(false)
    useFarmStore().farms[0].role = 'STAFF'
    await store.load(true)
    expect(api.myPens).toHaveBeenCalledOnce()
    expect(store.list).toEqual([])
    expect(store.total).toBe(0)
    expect(store.status).toBe('empty')
  })

  it('does not navigate using a lookup result from before a farm switch', async () => {
    const store = useBatchListStore()
    const old = deferred<{ result: string; batch_id: number }>()
    api.lookupBatch.mockReturnValueOnce(old.promise)
    const pending = store.lookup('ZM2610-031')
    useFarmStore().setCurrent(1002)
    await store.load(false)
    old.resolve({ result: 'OK', batch_id: 1 })
    expect(await pending).toBeNull()
    expect(store.lookupInput).toBe('')
    expect(store.lookupLoading).toBe(false)
  })

  it('does not reuse another employee account assignments in the same farm', async () => {
    const store = useBatchListStore()
    useFarmStore().farms[0].role = 'STAFF'
    useAuthStore().user = { user_id: 10003 }
    api.myPens.mockResolvedValueOnce({ pens: [{ pen_id: 2001 }], pen_count: 1 }).mockResolvedValueOnce({ pens: [], pen_count: 0 })
    await store.load(true)
    expect(store.staffPenCount).toBe(1)
    useAuthStore().user = { user_id: 10004 }
    await store.load(true)
    expect(store.staffPenCount).toBe(0)
    expect(api.myPens).toHaveBeenCalledTimes(2)
  })

  it('uses the current farm header for each real batch API request', async () => {
    const { herdApi } = await vi.importActual<typeof import('../apps/user/src/api/herd')>('../apps/user/src/api/herd')
    const headers: unknown[] = []
    const client = setupHttp({ baseURL: '/api/v1', client: 'WEB', getFarmId: () => useFarmStore().currentFarmId })
    client.instance.defaults.adapter = async (config) => {
      headers.push(config.headers.get('X-Farm-Id'))
      return { status: 200, statusText: 'OK', headers: {}, config, data: { code: 0, data: page() } }
    }
    await herdApi.batches()
    useFarmStore().setCurrent(1002)
    await herdApi.batches()
    expect(headers).toEqual(['1001', '1002'])
  })
})
