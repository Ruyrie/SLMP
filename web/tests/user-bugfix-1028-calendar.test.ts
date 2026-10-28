import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({
  fetchCalendar: vi.fn(),
  fetchDayTodos: vi.fn(),
  fetchHome: vi.fn(),
  fetchOverdueTodos: vi.fn(),
  fetchWeekly: vi.fn()
}))
vi.mock('../apps/user/src/api/today', () => api)

const { useCalendarStore, loadClosedTodos, readTabCounts, dayPendingCount, sortClosed } = await import('../apps/user/src/stores/today/calendar')

const pending = (id: number) => ({ todo_id: id, title: `待处理${id}`, display_status: 'PENDING' })
const done = (id: number, time: string) => ({ todo_id: id, title: `已完成${id}`, display_status: 'DONE', completed_text: `李明 ${time} 登记` })
const ignored = (id: number, time: string) => ({ todo_id: id, title: `已忽略${id}`, display_status: 'IGNORED', ignore_text: `张伟 ${time} 忽略` })

function paramError(): Error {
  return Object.assign(new Error('状态筛选不正确'), { name: 'ApiError' })
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
})

describe('日列表分区工具', () => {
  it('tab_counts 只在新接口返回 CLOSED 时生效', () => {
    expect(readTabCounts({ UNFINISHED: 3, CLOSED: 5 })).toEqual({ unfinished: 3, closed: 5 })
    expect(readTabCounts({ ALL: 8 })).toBeNull()
    expect(readTabCounts(undefined)).toBeNull()
  })

  it('格子待处理数 = 总数 - 已关闭', () => {
    expect(dayPendingCount({ total: 8, closed_count: 5 })).toBe(3)
    expect(dayPendingCount({ total: 2 })).toBe(2)
    expect(dayPendingCount(null)).toBe(0)
  })

  it('已关闭按完成 / 忽略时刻倒序', () => {
    const list = sortClosed([done(1, '07:12'), ignored(2, '09:30'), done(3, '8:05')] as never[])
    expect(list.map((item) => item.todo_id)).toEqual([2, 3, 1])
  })
})

describe('已完成 / 已忽略加载', () => {
  it('新接口：status=CLOSED 直接用', async () => {
    api.fetchDayTodos.mockResolvedValue({ list: [done(1, '07:00')], total: 1, has_more: false })
    const result = await loadClosedTodos('2026-10-28', 1)
    expect(api.fetchDayTodos).toHaveBeenCalledTimes(1)
    expect(api.fetchDayTodos.mock.calls[0][0]).toMatchObject({ status: 'CLOSED', page: 1 })
    expect(result.list).toHaveLength(1)
  })

  it('旧接口不认 CLOSED 时回退为 ALL 并在本地筛选', async () => {
    api.fetchDayTodos.mockImplementation((params: { status: string }) => (
      params.status === 'CLOSED'
        ? Promise.reject(paramError())
        : Promise.resolve({ list: [pending(1), done(2, '07:00'), ignored(3, '10:00')], total: 3 })
    ))
    const result = await loadClosedTodos('2026-10-28', 1)
    expect(result.list.map((item) => item.todo_id)).toEqual([3, 2])
    expect(result.hasMore).toBe(false)
  })
})

describe('日历日视图：上面待处理、下面已完成 / 已忽略默认折叠', () => {
  it('新接口：数量只算待处理，折叠头数量来自 tab_counts', async () => {
    api.fetchDayTodos.mockImplementation((params: { status: string }) => Promise.resolve(
      params.status === 'UNFINISHED'
        ? { list: [pending(1), pending(2)], total: 2, has_more: false, tab_counts: { UNFINISHED: 2, CLOSED: 4 } }
        : { list: [done(3, '07:00')], total: 4, has_more: false }
    ))
    const store = useCalendarStore()
    store.selected = '2026-10-28'
    await store.fetchDayItems()
    expect(store.dayTotal).toBe(2)
    expect(store.closedTotal).toBe(4)
    expect(store.closedOpen).toBe(false)
    expect(api.fetchDayTodos).toHaveBeenCalledTimes(1)
    store.toggleClosed()
    await vi.waitFor(() => expect(store.closedStatus).toBe('ready'))
    expect(api.fetchDayTodos.mock.calls[1][0]).toMatchObject({ status: 'CLOSED' })
    expect(store.closedItems).toHaveLength(1)
  })

  it('旧接口：没有 tab_counts 时用日历格子上的已关闭数，待处理全部为空时显示“都处理完了”', async () => {
    api.fetchDayTodos.mockResolvedValue({ list: [], total: 0 })
    const store = useCalendarStore()
    store.selected = '2026-10-28'
    store.monthDays = [{ date: '2026-10-28', total: 3, closed_count: 3 }]
    await store.fetchDayItems()
    expect(store.dayItems).toEqual([])
    expect(store.listStatus).toBe('empty')
    expect(store.closedTotal).toBe(3)
  })

  it('旧接口把已关闭混进 UNFINISHED 时上段也只留待处理', async () => {
    api.fetchDayTodos.mockResolvedValue({ list: [pending(1), done(2, '07:00')], total: 2 })
    const store = useCalendarStore()
    store.selected = '2026-10-28'
    await store.fetchDayItems()
    expect(store.dayItems.map((item) => item.todo_id)).toEqual([1])
  })

  it('切换日期后折叠区收起', async () => {
    api.fetchDayTodos.mockResolvedValue({ list: [], total: 0, tab_counts: { UNFINISHED: 0, CLOSED: 1 } })
    const store = useCalendarStore()
    store.view = 'day'
    store.closedOpen = true
    store.select('2026-10-27')
    expect(store.closedOpen).toBe(false)
  })
})
