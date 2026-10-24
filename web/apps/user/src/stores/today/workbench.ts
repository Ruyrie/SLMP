import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import { fetchHome, fetchPens, fetchTodayTodos } from '@/api/today'
import type { HomeVO, PenCount, PenOptionVO, TodoItemVO } from '@/api/today'
import { useFarmStore } from '@/stores/farm'

export type LoadStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

function errorState(error: unknown): { status: LoadStatus; message: string } {
  const apiError = error as ApiError
  return {
    status: apiError?.isForbidden ? 'forbidden' : 'error',
    message: apiError?.message || '网络连接不稳定，请稍后重试'
  }
}

/** 工作台（WU-T01 / WU-T02）共用：首页聚合 + 今天和已逾期 + 栏舍占用。 */
export const useWorkbenchStore = defineStore('todayWorkbench', {
  state: () => ({
    home: null as HomeVO | null,
    homeStatus: 'loading' as LoadStatus,
    homeError: '',
    todos: [] as TodoItemVO[],
    reminders: [] as TodoItemVO[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    penCounts: [] as PenCount[],
    query: {
      page: 1,
      size: 20,
      filter: 'ALL',
      penId: null as number | null
    },
    todoStatus: 'loading' as LoadStatus,
    todoError: '',
    pens: [] as PenOptionVO[],
    pensStatus: 'idle' as LoadStatus,
    pensError: '',
    loaded: false,
    controller: null as AbortController | null,
    pensController: null as AbortController | null
  }),
  getters: {
    isStaff: (state): boolean => state.home?.role === 'STAFF',
    ownerMetrics: (state) => state.home?.owner_metrics || null,
    staffMetrics: (state) => state.home?.staff_metrics || null,
    farmBlocked: (state): boolean =>
      state.home?.farm_status === 'PENDING' || state.home?.farm_status === 'REJECTED',
    noPen: (state): boolean => state.home?.has_pen === false,
    filtered: (state): boolean => state.query.filter !== 'ALL' || !!state.query.penId
  },
  actions: {
    async fetchAll(): Promise<void> {
      const farm = useFarmStore()
      this.query.page = 1
      this.homeStatus = 'loading'
      this.homeError = ''
      const homeTask = this.fetchHome()
      const todoTask = this.fetchTodos()
      const reminderTask = this.fetchReminders()
      const penTask = farm.role === 'staff' ? Promise.resolve() : this.fetchPens()
      await Promise.all([homeTask, todoTask, reminderTask, penTask])
      this.loaded = true
    },
    async fetchHome(): Promise<void> {
      try {
        this.home = await fetchHome()
        this.homeStatus = 'ready'
      } catch (error) {
        const state = errorState(error)
        this.homeStatus = state.status
        this.homeError = state.message
      }
    },
    async fetchTodos(): Promise<void> {
      this.controller?.abort()
      this.controller = new AbortController()
      this.todoStatus = 'loading'
      this.todoError = ''
      try {
        const data = await fetchTodayTodos(
          {
            page: this.query.page,
            size: this.query.size,
            filter: this.query.penId ? undefined : this.query.filter,
            pen_id: this.query.penId || undefined
          },
          this.controller.signal
        )
        this.todos = data.list || []
        this.total = data.total || 0
        this.tabCounts = data.tab_counts || {}
        this.penCounts = data.pen_counts || []
        if (this.todos.length) this.todoStatus = 'ready'
        else this.todoStatus = this.filtered ? 'filtered_empty' : 'empty'
      } catch (error) {
        if ((error as { name?: string })?.name === 'CanceledError') return
        const state = errorState(error)
        this.todoStatus = state.status
        this.todoError = state.message
      }
    },
    async fetchPens(): Promise<void> {
      this.pensController?.abort()
      this.pensController = new AbortController()
      this.pensStatus = 'loading'
      this.pensError = ''
      try {
        this.pens = await fetchPens(this.pensController.signal)
        this.pensStatus = 'ready'
      } catch (error) {
        if ((error as { name?: string })?.name === 'CanceledError') return
        const state = errorState(error)
        this.pensStatus = state.status
        this.pensError = state.message
      }
    },
    /** 生产提醒：从全部待办里取预警类和休药期到期，不受表格筛选影响。 */
    async fetchReminders(): Promise<void> {
      try {
        const data = await fetchTodayTodos({ page: 1, size: 50, filter: 'ALL' })
        this.reminders = (data.list || []).filter(
          (item) => item.category === 'WARNING' || item.todo_type === 'WITHDRAWAL_END'
        )
      } catch {
        /* 生产提醒加载失败不阻塞工作台 */
      }
    },
    setFilter(filter: string): void {
      this.setFilterAndPen(filter, null)
    },
    setFilterAndPen(filter: string, penId: number | null): void {
      if (this.query.filter === filter && this.query.penId === penId) return
      this.query.filter = filter
      this.query.penId = penId
      this.query.page = 1
      void this.fetchTodos()
    },
    setPenFilter(penId: number | null): void {
      if (this.query.penId === penId) return
      this.query.penId = penId
      this.query.filter = 'ALL'
      this.query.page = 1
      void this.fetchTodos()
    },
    setPage(page: number): void {
      if (this.query.page === page) return
      this.query.page = page
      void this.fetchTodos()
    },
    setSize(size: number): void {
      if (this.query.size === size) return
      this.query.size = size
      this.query.page = 1
      void this.fetchTodos()
    }
  }
})
