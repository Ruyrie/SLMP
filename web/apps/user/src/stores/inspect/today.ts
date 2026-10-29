import { defineStore } from 'pinia'
import { ApiError, dayjs, today as todayIso } from '@zmt/shared'
import {
  fetchDayList,
  fetchInspectionSetting,
  fetchRecentIssues,
  fetchSummary,
  saveInspectionSetting,
  type DayListParams,
  type InspectionListItem,
  type InspectionSetting,
  type InspectionSummary,
  type RecentIssue
} from '@/api/inspect'
import { useUiStore } from '@/stores/ui'
import { currentFarmScope } from '@/stores/farmScope'
import { useFarmStore } from '@/stores/farm'
import type { PageStatus } from '@/api/inspect'

type ListStatus = 'ALL' | 'PENDING' | 'ABNORMAL' | 'DONE'

interface Query {
  date: string
  status: ListStatus
  page: number
  size: number
}

export const useInspectionTodayStore = defineStore('inspectToday', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    summary: null as InspectionSummary | null,
    list: [] as InspectionListItem[],
    allRows: [] as InspectionListItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    issues: [] as RecentIssue[],
    setting: null as InspectionSetting | null,
    settingLoading: false,
    yesterdayDate: '',
    /** 昨天列表缓存所属的养殖场视角 */
    yesterdayScope: '',
    yesterdayList: [] as InspectionListItem[],
    yesterdayLoading: false,
    query: {
      date: todayIso(),
      status: 'ALL' as ListStatus,
      page: 1,
      size: 20
    } as Query,
    registerOpen: false,
    registerMode: 'REGISTER' as 'REGISTER' | 'MODIFY' | 'BACKFILL',
    registerPenId: 0,
    registerInspectionId: 0,
    registerDate: '',
    deadlineOpen: false,
    reportOpen: false,
    reportInspectionId: 0,
    reportPenId: 0,
    controller: null as AbortController | null,
    loaded: false
  }),
  getters: {
    isStaff(state): boolean {
      return useFarmStore().role === 'staff'
    },
    isManager(): boolean {
      return !this.isStaff
    },
    farmName(): string {
      return this.summary?.farm_name || useFarmStore().farmName || '当前养殖场'
    },
    isToday(state): boolean {
      return state.query.date === todayIso()
    },
    isFiltered(state): boolean {
      return state.query.status !== 'ALL'
    },
    pageTotal(): number {
      return Math.max(1, Math.ceil(this.total / this.query.size))
    },
    completionPercent(): number {
      return Math.round(Number(this.summary?.completion_rate || 0) * 1000) / 10
    },
    noOwnerPending(): number {
      return Number(this.summary?.no_owner_pending_count || 0)
    },
    penChoices(state): Array<{ pen_id: number; pen_name: string; status: string; statusText: string; stock: number }> {
      return state.allRows.map((row) => ({
        pen_id: row.pen_id,
        pen_name: row.pen_name || '',
        status: row.display_status,
        statusText: row.display_status_text || '',
        stock: Number(row.stock_count || 0)
      }))
    },
    emptyTitle(state): string {
      if (state.query.status === 'PENDING') return '今天的栏舍都已巡检'
      if (state.query.status === 'ABNORMAL') return '今天没有异常栏舍'
      if (state.query.status === 'DONE') return '还没有已巡的栏舍'
      return this.isStaff ? '你还没有负责的栏舍' : '今天没有需要巡检的栏舍'
    },
    emptyDesc(): string {
      if (!this.isToday) return '选其他日期可以查看当天的巡检结果'
      if (this.isStaff) return '请联系所有者或场长分配栏舍'
      return '批次入栏后，所在栏舍会自动列入巡检清单'
    }
  },
  actions: {
    async load(): Promise<void> {
      this.controller?.abort()
      this.controller = new AbortController()
      const { signal } = this.controller
      this.error = ''
      this.status = this.list.length ? this.status : 'loading'
      try {
        const result = await Promise.all([
          fetchSummary(this.query.date, signal),
          fetchDayList({ date: this.query.date, status: 'ALL', page: 1, size: 100 }, signal),
          this.isManager ? fetchRecentIssues(signal) : Promise.resolve([])
        ])
        this.summary = result[0] as InspectionSummary
        const allPage = result[1] as Awaited<ReturnType<typeof fetchDayList>>
        this.allRows = allPage.list || []
        this.tabCounts = allPage.tab_counts || {}
        if (this.isManager) this.issues = (result[2] as RecentIssue[]) || []
        if (this.query.status === 'ALL') {
          this.list = this.allRows
          this.total = allPage.total || 0
        } else {
          const page = await fetchDayList(
            { date: this.query.date, status: this.query.status, page: this.query.page, size: this.query.size },
            signal
          )
          this.list = page.list || []
          this.total = page.total || 0
        }
        this.status = this.list.length ? 'ready' : this.isFiltered ? 'filtered_empty' : 'empty'
        this.loaded = true
        if (this.isStaff) void this.loadYesterday()
      } catch (error) {
        if ((error as ApiError).name === 'CanceledError' || (error as { isCancel?: boolean }).isCancel) return
        const err = error as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message || '加载失败，请稍后重试'
      }
    },
    async loadYesterday(): Promise<void> {
      const yesterday = dayjs(todayIso()).subtract(1, 'day').format('YYYY-MM-DD')
      // 缓存还要核对养殖场视角：切场后昨天的巡检要按新场重新取
      const scope = currentFarmScope()
      if (this.yesterdayDate === yesterday && this.yesterdayScope === scope && this.yesterdayList.length) return
      this.yesterdayDate = yesterday
      this.yesterdayScope = scope
      this.yesterdayLoading = true
      try {
        const page = await fetchDayList({ date: yesterday, status: 'ALL', page: 1, size: 100 })
        this.yesterdayList = (page.list || []).filter((row) => row.display_status !== 'PENDING')
      } catch {
        this.yesterdayList = []
      } finally {
        this.yesterdayLoading = false
      }
    },
    setStatus(status: ListStatus): void {
      if (this.query.status === status) return
      this.query.status = status
      this.query.page = 1
      void this.load()
    },
    setPage(page: number): void {
      this.query.page = page
      void this.load()
    },
    setSize(size: number): void {
      this.query.size = size
      this.query.page = 1
      void this.load()
    },
    setDate(date: string): void {
      if (!date) return
      this.query.date = date
      this.query.page = 1
      void this.load()
    },
    openRegister(penId = 0): void {
      this.registerMode = 'REGISTER'
      this.registerPenId = penId
      this.registerInspectionId = 0
      this.registerDate = ''
      this.registerOpen = true
    },
    openModify(row: InspectionListItem): void {
      if (!row.can_modify) {
        useUiStore().toast('这条记录现在不能修改', 'error')
        return
      }
      this.registerMode = 'MODIFY'
      this.registerPenId = row.pen_id
      this.registerInspectionId = row.inspection_id
      this.registerDate = this.query.date
      this.registerOpen = true
    },
    openBackfill(penId: number, date: string): void {
      this.registerMode = 'BACKFILL'
      this.registerPenId = penId
      this.registerInspectionId = 0
      this.registerDate = date
      this.registerOpen = true
    },
    openDeadline(): void {
      this.deadlineOpen = true
      void this.loadSetting()
    },
    async loadSetting(): Promise<void> {
      if (this.setting) return
      this.settingLoading = true
      try {
        this.setting = await fetchInspectionSetting()
      } catch (error) {
        useUiStore().toast((error as ApiError).message || '截止时间加载失败', 'error')
      } finally {
        this.settingLoading = false
      }
    },
    async saveDeadline(time: string): Promise<boolean> {
      if (!time) {
        useUiStore().toast('请选择截止时间', 'error')
        return false
      }
      this.settingLoading = true
      try {
        this.setting = await saveInspectionSetting(time)
        useUiStore().toast(`巡检截止时间已改为 ${time}`)
        this.deadlineOpen = false
        void this.load()
        return true
      } catch (error) {
        useUiStore().toast((error as ApiError).message || '保存失败，请检查网络后重试', 'error')
        return false
      } finally {
        this.settingLoading = false
      }
    },
    openReport(inspectionId: number, penId: number): void {
      this.reportInspectionId = inspectionId
      this.reportPenId = penId
      this.reportOpen = true
    },
    onSubmitted(): void {
      this.registerOpen = false
      this.reportOpen = false
      void this.load()
    },
    reset(): void {
      this.controller?.abort()
      this.summary = null
      this.list = []
      this.allRows = []
      this.total = 0
      this.tabCounts = {}
      this.issues = []
      this.yesterdayList = []
      this.yesterdayDate = ''
      this.query.date = todayIso()
      this.query.status = 'ALL'
      this.query.page = 1
      this.status = 'loading'
      this.loaded = false
    }
  }
})

export type { ListStatus }
