import { defineStore } from 'pinia'
import { ApiError, dayjs, formatDate, today } from '@zmt/shared'
import {
  fetchCalendar,
  fetchDayTodos,
  fetchHome,
  fetchOverdueTodos,
  fetchWeekly
} from '@/api/today'
import type { CalendarDayVO, HomeVO, TodoItemVO, WeeklyDayVO } from '@/api/today'
import { useFarmStore } from '@/stores/farm'

export type CalendarView = 'month' | 'week' | 'day'
export type ListStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type ClosedStatus = 'idle' | 'loading' | 'ready' | 'error'

/** 日列表分两段：上面“待处理”（UNFINISHED），下面“已完成 / 已忽略”（CLOSED，默认折叠）。 */
export const DAY_PAGE_SIZE = 50

const VIEW_KEY = 'zmt.calendar.view'

function storedView(): CalendarView | null {
  if (typeof sessionStorage === 'undefined') return null
  const value = sessionStorage.getItem(VIEW_KEY)
  return value === 'month' || value === 'week' || value === 'day' ? value : null
}

function errorState(error: unknown): { status: ListStatus; message: string } {
  const apiError = error as ApiError
  return {
    status: apiError?.isForbidden ? 'forbidden' : 'error',
    message: apiError?.message || '网络连接不稳定，请稍后重试'
  }
}

function isCancel(error: unknown): boolean {
  return (error as { name?: string })?.name === 'CanceledError'
}

export function isClosedTodo(item: Pick<TodoItemVO, 'display_status'>): boolean {
  return item.display_status === 'DONE' || item.display_status === 'IGNORED'
}

/** 列表项没有完成 / 忽略时间字段，从“李明 07:12 登记”“张伟 09:30 忽略”里取时刻排序。 */
function closedAt(item: TodoItemVO): string {
  const text = item.display_status === 'DONE' ? item.completed_text : item.ignore_text
  return /\d{1,2}:\d{2}/.exec(text || '')?.[0]?.padStart(5, '0') || ''
}

/** 已关闭的按完成 / 忽略时间倒序（与服务端 status=CLOSED 的排序一致，旧接口回退时在本地排）。 */
export function sortClosed(items: TodoItemVO[]): TodoItemVO[] {
  return [...items].sort((a, b) => closedAt(b).localeCompare(closedAt(a)))
}

/** 服务端 tab_counts 固定返回 {UNFINISHED, CLOSED}；旧接口没有时返回 null，由日历格子上的数量兜底。 */
export function readTabCounts(counts: Record<string, number> | undefined | null): { unfinished: number; closed: number } | null {
  if (!counts || counts.CLOSED === undefined || counts.CLOSED === null) return null
  return {
    unfinished: Number(counts.UNFINISHED ?? counts.PENDING ?? 0),
    closed: Number(counts.CLOSED || 0)
  }
}

/** 某天待处理数量：日历格子的总数减去已关闭数。 */
export function dayPendingCount(day: Pick<CalendarDayVO, 'total' | 'closed_count'> | undefined | null): number {
  if (!day) return 0
  return Math.max(0, Number(day.total || 0) - Number(day.closed_count || 0))
}

/**
 * 按日期加载“已完成 / 已忽略”：优先 status=CLOSED；旧接口不认 CLOSED（报参数错误）或忽略它时，
 * 回退为 status=ALL 后在本地筛出已完成 / 已忽略。
 */
export async function loadClosedTodos(
  date: string,
  page: number,
  signal?: AbortSignal
): Promise<{ list: TodoItemVO[]; total: number; hasMore: boolean }> {
  try {
    const data = await fetchDayTodos({ date, status: 'CLOSED', page, size: DAY_PAGE_SIZE }, signal)
    const list = data.list || []
    if (list.every(isClosedTodo)) {
      const total = Number(data.total || 0)
      return { list, total, hasMore: data.has_more ?? page * DAY_PAGE_SIZE < total }
    }
  } catch (error) {
    if (isCancel(error)) throw error
  }
  // 服务端单页上限 100（原日列表也按 100 取）
  const all = await fetchDayTodos({ date, status: 'ALL', page: 1, size: 100 }, signal)
  const list = sortClosed((all.list || []).filter(isClosedTodo))
  return { list, total: list.length, hasMore: false }
}

export const useCalendarStore = defineStore('todayCalendar', {
  state: () => ({
    view: (storedView() || 'month') as CalendarView,
    selected: today(),
    monthDays: [] as CalendarDayVO[],
    weekDays: [] as WeeklyDayVO[],
    /** 待处理（未完成）分页 */
    dayItems: [] as TodoItemVO[],
    dayTotal: 0,
    dayPage: 1,
    dayHasMore: false,
    dayMoreLoading: false,
    /** 已完成 / 已忽略：默认折叠，展开后按 status=CLOSED 分页加载 */
    closedOpen: false,
    closedItems: [] as TodoItemVO[],
    closedServerTotal: null as number | null,
    closedPage: 1,
    closedHasMore: false,
    closedStatus: 'idle' as ClosedStatus,
    closedError: '',
    closedController: null as AbortController | null,
    /** 周视图：各天展开的已关闭待办 */
    weekClosed: {} as Record<string, { open: boolean; status: ClosedStatus; items: TodoItemVO[] }>,
    overdueItems: [] as TodoItemVO[],
    status: 'loading' as ListStatus,
    listStatus: 'loading' as ListStatus,
    overdueStatus: 'idle' as ListStatus,
    error: '',
    listError: '',
    overdueError: '',
    home: null as HomeVO | null,
    drawerOpen: false,
    activeTodoId: null as number | null,
    reminderOpen: false,
    initialized: false,
    mainController: null as AbortController | null,
    listController: null as AbortController | null,
    overdueController: null as AbortController | null
  }),
  getters: {
    todayDate: (): string => today(),
    monthRange(state): { start: string; end: string; first: string; daysInMonth: number; totalCells: number } {
      const first = dayjs(state.selected).startOf('month')
      const offset = (first.day() + 6) % 7
      const daysInMonth = first.daysInMonth()
      const totalCells = Math.ceil((offset + daysInMonth) / 7) * 7
      return {
        first: first.format('YYYY-MM-DD'),
        start: first.subtract(offset, 'day').format('YYYY-MM-DD'),
        end: first.add(totalCells - 1, 'day').format('YYYY-MM-DD'),
        daysInMonth,
        totalCells
      }
    },
    weekStart(state): string {
      const date = dayjs(state.selected)
      return date.subtract((date.day() + 6) % 7, 'day').format('YYYY-MM-DD')
    },
    periodText(state): string {
      if (state.view === 'month') return dayjs(state.selected).format('YYYY年M月')
      if (state.view === 'week') {
        const start = dayjs(state.selected).subtract((dayjs(state.selected).day() + 6) % 7, 'day')
        const end = start.add(6, 'day')
        const sameYear = start.year() === end.year()
        const sameMonth = sameYear && start.month() === end.month()
        if (sameMonth) return `${start.format('M月D日')}–${end.format('D日')}`
        if (sameYear) return `${start.format('M月D日')}–${end.format('M月D日')}`
        return `${start.format('YYYY年M月D日')}–${end.format('YYYY年M月D日')}`
      }
      return formatDate(state.selected)
    },
    dayTitle(state): string {
      return `${formatDate(state.selected)}${state.selected === today() ? ' · 今天' : ''}`
    },
    dayGroups(state): { time: string; items: TodoItemVO[] }[] {
      const groups = new Map<string, TodoItemVO[]>()
      for (const item of state.dayItems) {
        const key = item.time_type === 'ALL_DAY' || !item.due_time ? '全天' : item.time_label || item.due_time
        const list = groups.get(key)
        if (list) list.push(item)
        else groups.set(key, [item])
      }
      return Array.from(groups.entries())
        .map(([time, items]) => ({ time, items }))
        .sort((a, b) => {
          const rank = (value: string): number => {
            if (value === '全天') return -1
            const [hour, minute] = value.split(':').map((part) => Number(part))
            return Number.isFinite(hour) ? hour * 60 + (Number.isFinite(minute) ? minute : 0) : 9999
          }
          return rank(a.time) - rank(b.time)
        })
    },
    monthMap(state): Record<string, CalendarDayVO> {
      const map: Record<string, CalendarDayVO> = {}
      for (const day of state.monthDays) if (day.date) map[day.date] = day
      return map
    },
    weekMap(state): Record<string, WeeklyDayVO> {
      const map: Record<string, WeeklyDayVO> = {}
      for (const day of state.weekDays) if (day.date) map[day.date] = day
      return map
    },
    /** 已完成 / 已忽略数量：新接口用 tab_counts.CLOSED，旧接口用日历格子上的 closed_count。 */
    closedTotal(state): number {
      if (state.closedServerTotal !== null) return state.closedServerTotal
      const day = state.monthDays.find((item) => item.date === state.selected)
        || state.weekDays.find((item) => item.date === state.selected)
      if (day) return Number(day.closed_count || 0)
      return state.closedItems.length
    }
  },
  actions: {
    async init(): Promise<void> {
      const farm = useFarmStore()
      const saved = storedView()
      if (farm.role === 'staff') this.view = saved || 'day'
      else this.view = saved || 'month'
      if (farm.role === 'staff') void this.fetchHome()
      this.selected = today()
      this.initialized = true
      await this.fetchCurrent()
    },
    async fetchHome(): Promise<void> {
      try {
        this.home = await fetchHome()
      } catch {
        /* 员工日历顶部横幅缺少栏舍名时使用通用文案 */
      }
    },
    async fetchCurrent(): Promise<void> {
      if (this.view === 'month') {
        await this.fetchMonth()
        await this.fetchDayItems()
      } else if (this.view === 'week') {
        await this.fetchWeek()
      } else {
        await Promise.all([this.fetchDayItems(), this.fetchOverdue(), this.fetchWeek()])
      }
    },
    async fetchMonth(): Promise<void> {
      this.mainController?.abort()
      this.mainController = new AbortController()
      this.status = 'loading'
      this.error = ''
      try {
        const range = this.monthRange
        const data = await fetchCalendar(
          { start_date: range.start, end_date: range.end },
          this.mainController.signal
        )
        this.monthDays = data.days || []
        this.status = 'ready'
      } catch (error) {
        if (isCancel(error)) return
        const state = errorState(error)
        this.status = state.status === 'ready' ? 'error' : state.status
        this.error = state.message
      }
    },
    async fetchWeek(): Promise<void> {
      this.mainController?.abort()
      this.mainController = new AbortController()
      this.status = 'loading'
      this.error = ''
      try {
        const data = await fetchWeekly(
          { week_start: this.weekStart, per_day: 20 },
          this.mainController.signal
        )
        this.weekDays = data.days || []
        // 周视图分区：每天“已完成 / 已忽略”默认折叠，刷新后重新加载
        this.weekClosed = {}
        this.status = 'ready'
      } catch (error) {
        if (isCancel(error)) return
        const state = errorState(error)
        this.status = state.status === 'ready' ? 'error' : state.status
        this.error = state.message
      }
    },
    async fetchDayItems(): Promise<void> {
      this.listController?.abort()
      this.listController = new AbortController()
      this.listStatus = 'loading'
      this.listError = ''
      const date = this.selected
      try {
        const data = await fetchDayTodos(
          { date, status: 'UNFINISHED', page: 1, size: DAY_PAGE_SIZE },
          this.listController.signal
        )
        const list = (data.list || []).filter((item) => !isClosedTodo(item))
        const counts = readTabCounts(data.tab_counts)
        this.dayItems = list
        this.dayPage = 1
        this.dayTotal = counts ? counts.unfinished : Number(data.total || list.length)
        this.dayHasMore = data.has_more ?? this.dayItems.length < this.dayTotal
        this.closedServerTotal = counts ? counts.closed : null
        this.listStatus = this.dayItems.length ? 'ready' : 'empty'
        if (this.closedOpen) await this.fetchClosed(1)
        else {
          this.closedItems = []
          this.closedStatus = 'idle'
        }
      } catch (error) {
        if (isCancel(error)) return
        const state = errorState(error)
        this.listStatus = state.status
        this.listError = state.message
      }
    },
    async fetchMoreDayItems(): Promise<void> {
      if (!this.dayHasMore || this.dayMoreLoading) return
      this.dayMoreLoading = true
      try {
        const page = this.dayPage + 1
        const data = await fetchDayTodos({ date: this.selected, status: 'UNFINISHED', page, size: DAY_PAGE_SIZE })
        const known = new Set(this.dayItems.map((item) => item.todo_id))
        this.dayItems = [...this.dayItems, ...(data.list || []).filter((item) => !isClosedTodo(item) && !known.has(item.todo_id))]
        this.dayPage = page
        this.dayHasMore = data.has_more ?? this.dayItems.length < this.dayTotal
      } catch (error) {
        this.listError = errorState(error).message
      } finally {
        this.dayMoreLoading = false
      }
    },
    async fetchClosed(page = 1): Promise<void> {
      if (page === 1) this.closedController?.abort()
      this.closedController = new AbortController()
      this.closedStatus = 'loading'
      this.closedError = ''
      try {
        const data = await loadClosedTodos(this.selected, page, this.closedController.signal)
        if (page === 1) this.closedItems = data.list
        else {
          const known = new Set(this.closedItems.map((item) => item.todo_id))
          this.closedItems = [...this.closedItems, ...data.list.filter((item) => !known.has(item.todo_id))]
        }
        this.closedPage = page
        this.closedHasMore = data.hasMore
        this.closedStatus = 'ready'
      } catch (error) {
        if (isCancel(error)) return
        this.closedStatus = 'error'
        this.closedError = errorState(error).message
      }
    },
    toggleClosed(): void {
      this.closedOpen = !this.closedOpen
      if (this.closedOpen && this.closedStatus !== 'ready') void this.fetchClosed(1)
    },
    async toggleWeekClosed(date: string): Promise<void> {
      const current = this.weekClosed[date]
      if (current?.open) {
        this.weekClosed[date] = { ...current, open: false }
        return
      }
      this.weekClosed[date] = { open: true, status: 'loading', items: current?.items || [] }
      try {
        const data = await loadClosedTodos(date, 1)
        this.weekClosed[date] = { open: true, status: 'ready', items: data.list }
      } catch {
        this.weekClosed[date] = { open: true, status: 'error', items: [] }
      }
    },
    async fetchOverdue(): Promise<void> {
      this.overdueController?.abort()
      this.overdueController = new AbortController()
      if (this.selected !== today()) {
        this.overdueItems = []
        this.overdueStatus = 'ready'
        return
      }
      this.overdueStatus = 'loading'
      this.overdueError = ''
      try {
        const data = await fetchOverdueTodos({ page: 1, size: 20 }, this.overdueController.signal)
        this.overdueItems = data.list || []
        this.overdueStatus = 'ready'
      } catch (error) {
        if (isCancel(error)) return
        const state = errorState(error)
        this.overdueStatus = state.status
        this.overdueError = state.message
      }
    },
    setView(view: CalendarView): void {
      if (this.view === view) return
      this.view = view
      if (typeof sessionStorage !== 'undefined') sessionStorage.setItem(VIEW_KEY, view)
      void this.fetchCurrent()
    },
    shift(delta: number): void {
      this.closedOpen = false
      const date = dayjs(this.selected)
      if (this.view === 'month') this.selected = date.add(delta, 'month').format('YYYY-MM-DD')
      else if (this.view === 'week') this.selected = date.add(delta * 7, 'day').format('YYYY-MM-DD')
      else this.selected = date.add(delta, 'day').format('YYYY-MM-DD')
      void this.fetchCurrent()
    },
    goToday(): void {
      if (this.selected !== today()) this.closedOpen = false
      this.selected = today()
      void this.fetchCurrent()
    },
    select(date: string): void {
      if (this.selected === date) return
      this.selected = date
      this.closedOpen = false
      this.closedItems = []
      this.closedStatus = 'idle'
      if (this.view === 'day') {
        void this.fetchDayItems()
        void this.fetchOverdue()
      } else if (this.view === 'month') {
        void this.fetchDayItems()
      }
    },
    reload(): void {
      void this.fetchCurrent()
    },
    openTodo(id: number): void {
      this.activeTodoId = id
      this.drawerOpen = true
    },
    closeTodo(): void {
      this.drawerOpen = false
    },
    onTodoChanged(): void {
      void this.fetchCurrent()
    }
  }
})
