import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import { fetchHome, fetchPens, fetchTodayTodos } from '@/api/today'
import type { HomeVO, PenCount, PenOptionVO, TodoItemVO } from '@/api/today'
import { useFarmStore } from '@/stores/farm'

export type LoadStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

function errorState(error: unknown): { status: LoadStatus; message: string } {
  const apiError = error as ApiError
  return {
    status: apiError?.isForbidden ? 'forbidden' : 'error',
    message: apiError?.message || '网络连接不稳定，请稍后重试'
  }
}

/** 首页只预览今日待办；逾期独立按需加载，合集提供完整分页。 */
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
    screen: 'home' as 'home' | 'collection',
    overdueExpanded: false,
    overdueTodos: [] as TodoItemVO[],
    overdueTotal: 0,
    overduePage: 1,
    overduePageSize: 20,
    overdueStatus: 'idle' as LoadStatus,
    overdueError: '',
    query: {
      page: 1,
      size: 3,
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
    homeController: null as AbortController | null,
    reminderController: null as AbortController | null,
    overdueController: null as AbortController | null,
    pensController: null as AbortController | null
  }),
  getters: {
    isStaff: (state): boolean => state.home?.role === 'STAFF',
    ownerMetrics: (state) => state.home?.owner_metrics || null,
    staffMetrics: (state) => state.home?.staff_metrics || null,
    farmBlocked: (state): boolean =>
      state.home?.farm_status === 'PENDING' || state.home?.farm_status === 'REJECTED',
    noPen: (state): boolean => state.home?.has_pen === false,
    filtered: (state): boolean => state.query.filter !== 'ALL' || !!state.query.penId,
    overdueCount: (state): number => state.tabCounts.OVERDUE ?? state.home?.owner_metrics?.overdue_count
      ?? state.home?.staff_metrics?.overdue_count ?? 0,
    overdueSize: (state): number => state.screen === 'home' ? 5 : state.overduePageSize
  },
  actions: {
    resetView(screen: 'home' | 'collection', filter = 'ALL'): void {
      this.controller?.abort()
      this.homeController?.abort()
      this.reminderController?.abort()
      this.overdueController?.abort()
      this.pensController?.abort()
      this.screen = screen
      this.home = null
      this.pens = []
      this.pensStatus = 'idle'
      this.query = { page: 1, size: screen === 'home' ? 3 : 20, filter, penId: null }
      this.todos = []
      this.reminders = []
      this.total = 0
      this.tabCounts = {}
      this.overdueExpanded = false
      this.overdueTodos = []
      this.overdueTotal = 0
      this.overduePage = 1
      this.overduePageSize = 20
      this.overdueStatus = 'idle'
      this.overdueError = ''
    },
    async enterHome(): Promise<void> {
      this.resetView('home')
      await this.fetchAll()
    },
    async enterCollection(filter: 'ALL' | 'WARNING' = 'ALL'): Promise<void> {
      this.resetView('collection', filter)
      await this.fetchTodos()
    },
    async refreshCollection(): Promise<void> {
      await Promise.all([this.fetchTodos(), this.overdueExpanded ? this.fetchOverdue() : Promise.resolve()])
    },
    async fetchAll(): Promise<void> {
      const farm = useFarmStore()
      this.homeStatus = 'loading'
      this.homeError = ''
      const homeTask = this.fetchHome()
      const todoTask = this.fetchTodos()
      const reminderTask = this.fetchReminders()
      const penTask = farm.role === 'staff' ? Promise.resolve() : this.fetchPens()
      const overdueTask = this.overdueExpanded ? this.fetchOverdue() : Promise.resolve()
      await Promise.all([homeTask, todoTask, reminderTask, penTask, overdueTask])
      this.loaded = true
    },
    async fetchHome(): Promise<void> {
      this.homeController?.abort()
      const controller = this.homeController = new AbortController()
      try {
        const home = await fetchHome(controller.signal)
        if (controller.signal.aborted) return
        this.home = home
        this.homeStatus = 'ready'
      } catch (error) {
        if (controller.signal.aborted) return
        const state = errorState(error)
        this.homeStatus = state.status
        this.homeError = state.message
      }
    },
    async fetchTodos(): Promise<void> {
      this.controller?.abort()
      const controller = this.controller = new AbortController()
      this.todoStatus = 'loading'
      this.todoError = ''
      if (this.query.filter === 'WARNING' && useFarmStore().role === 'staff') {
        this.todos = []
        this.total = 0
        this.todoStatus = 'forbidden'
        this.todoError = '预警合集仅向所有者和场长开放'
        return
      }
      try {
        const data = await fetchTodayTodos(
          {
            page: this.query.page,
            size: this.query.size,
            filter: this.query.penId ? undefined : this.query.filter,
            pen_id: this.query.penId || undefined
          },
          controller.signal
        )
        if (controller.signal.aborted) return
        const lastPage = Math.max(1, Math.ceil((data.total || 0) / this.query.size))
        if (this.query.page > lastPage) {
          this.query.page = lastPage
          await this.fetchTodos()
          return
        }
        this.todos = data.list || []
        this.total = data.total || 0
        this.tabCounts = data.tab_counts || {}
        this.penCounts = data.pen_counts || []
        if (this.todos.length) this.todoStatus = 'ready'
        else this.todoStatus = this.filtered ? 'filtered_empty' : 'empty'
      } catch (error) {
        if (controller.signal.aborted || (error as { name?: string })?.name === 'CanceledError') return
        const state = errorState(error)
        this.todoStatus = state.status
        this.todoError = state.message
      }
    },
    async toggleOverdue(): Promise<void> {
      this.overdueExpanded = !this.overdueExpanded
      if (!this.overdueExpanded) {
        this.overdueController?.abort()
        return
      }
      this.overduePage = 1
      await this.fetchOverdue()
    },
    async fetchOverdue(): Promise<void> {
      this.overdueController?.abort()
      if (!this.overdueExpanded || this.query.filter === 'WARNING') return
      const controller = this.overdueController = new AbortController()
      this.overdueStatus = 'loading'
      this.overdueError = ''
      try {
        // 员工按栏舍筛选时逾期区也只算该栏舍，数量与列表一致
        const data = await fetchTodayTodos(
          { page: this.overduePage, size: this.overdueSize, filter: 'OVERDUE', pen_id: this.query.penId || undefined },
          controller.signal
        )
        if (controller.signal.aborted) return
        const lastPage = Math.max(1, Math.ceil((data.total || 0) / this.overdueSize))
        if (this.overduePage > lastPage) {
          this.overduePage = lastPage
          await this.fetchOverdue()
          return
        }
        this.overdueTodos = data.list || []
        this.overdueTotal = data.total || 0
        this.tabCounts = { ...this.tabCounts, OVERDUE: this.overdueTotal }
        this.overdueStatus = this.overdueTodos.length ? 'ready' : 'empty'
      } catch (error) {
        if (controller.signal.aborted) return
        const state = errorState(error)
        this.overdueStatus = state.status
        this.overdueError = state.message
      }
    },
    setOverduePage(page: number): void {
      if (this.overduePage === page) return
      this.overduePage = page
      void this.fetchOverdue()
    },
    setOverdueSize(size: number): void {
      if (this.overduePageSize === size) return
      this.overduePageSize = size
      this.overduePage = 1
      void this.fetchOverdue()
    },
    async fetchPens(): Promise<void> {
      this.pensController?.abort()
      const controller = this.pensController = new AbortController()
      this.pensStatus = 'loading'
      this.pensError = ''
      try {
        const pens = await fetchPens(controller.signal)
        if (controller.signal.aborted) return
        this.pens = pens
        this.pensStatus = 'ready'
      } catch (error) {
        if (controller.signal.aborted || (error as { name?: string })?.name === 'CanceledError') return
        const state = errorState(error)
        this.pensStatus = state.status
        this.pensError = state.message
      }
    },
    /** 直接请求未解除的预警，避免首页今日列表遗漏历史预警。员工不请求此接口。 */
    async fetchReminders(): Promise<void> {
      this.reminderController?.abort()
      this.reminders = []
      if (useFarmStore().role === 'staff') return
      const controller = this.reminderController = new AbortController()
      try {
        const data = await fetchTodayTodos({ page: 1, size: 3, filter: 'WARNING' }, controller.signal)
        if (controller.signal.aborted) return
        this.reminders = data.list || []
      } catch {
        /* 生产提醒加载失败不阻塞工作台 */
      }
    },
    setFilter(filter: string): void {
      this.setFilterAndPen(filter, null)
    },
    setFilterAndPen(filter: string, penId: number | null): void {
      if (this.query.filter === filter && this.query.penId === penId) return
      const penChanged = this.query.penId !== penId
      this.query.filter = filter
      this.query.penId = penId
      this.query.page = 1
      void this.fetchTodos()
      if (penChanged) this.refreshOverdueForPen()
    },
    setPenFilter(penId: number | null): void {
      if (this.query.penId === penId) return
      this.query.penId = penId
      this.query.filter = 'ALL'
      this.query.page = 1
      void this.fetchTodos()
      this.refreshOverdueForPen()
    },
    refreshOverdueForPen(): void {
      if (!this.overdueExpanded) return
      this.overduePage = 1
      void this.fetchOverdue()
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
