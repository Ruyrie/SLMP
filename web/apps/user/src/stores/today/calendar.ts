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
export type DayStatus = 'ALL' | 'UNFINISHED'

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

export const useCalendarStore = defineStore('todayCalendar', {
  state: () => ({
    view: (storedView() || 'month') as CalendarView,
    selected: today(),
    dayStatus: 'ALL' as DayStatus,
    monthDays: [] as CalendarDayVO[],
    weekDays: [] as WeeklyDayVO[],
    dayItems: [] as TodoItemVO[],
    dayTotal: 0,
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
      try {
        const data = await fetchDayTodos(
          {
            date: this.selected,
            status: this.dayStatus,
            page: 1,
            size: 100
          },
          this.listController.signal
        )
        this.dayItems = data.list || []
        if (this.view === 'day') {
          this.dayTotal = data.total || 0
        } else {
          const day = this.monthMap[this.selected]
          this.dayTotal = day?.total || 0
        }
        this.listStatus = this.dayItems.length
          ? 'ready'
          : this.dayStatus === 'UNFINISHED'
            ? 'filtered_empty'
            : 'empty'
      } catch (error) {
        if (isCancel(error)) return
        const state = errorState(error)
        this.listStatus = state.status
        this.listError = state.message
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
      const date = dayjs(this.selected)
      if (this.view === 'month') this.selected = date.add(delta, 'month').format('YYYY-MM-DD')
      else if (this.view === 'week') this.selected = date.add(delta * 7, 'day').format('YYYY-MM-DD')
      else this.selected = date.add(delta, 'day').format('YYYY-MM-DD')
      void this.fetchCurrent()
    },
    goToday(): void {
      this.selected = today()
      void this.fetchCurrent()
    },
    select(date: string): void {
      if (this.selected === date) return
      this.selected = date
      if (this.view === 'day') {
        void this.fetchDayItems()
        void this.fetchOverdue()
      } else if (this.view === 'month') {
        void this.fetchDayItems()
      }
    },
    setDayStatus(status: DayStatus): void {
      if (this.dayStatus === status) return
      this.dayStatus = status
      void this.fetchDayItems()
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
