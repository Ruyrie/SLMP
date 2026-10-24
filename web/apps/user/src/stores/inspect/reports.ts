import { defineStore } from 'pinia'
import { ApiError, dayjs, today as todayIso } from '@zmt/shared'
import {
  fetchAbnormalReports,
  fetchDayList,
  fetchDiagnosis,
  rediagnose as requestRediagnose,
  type AbnormalReportListItem,
  type DiagnosisDetail,
  type InspectionListItem,
  type PageStatus
} from '@/api/inspect'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'

export type ReportRow = AbnormalReportListItem
export type ReportStatus = 'ALL' | 'PENDING' | 'DIAGNOSED' | 'HANDLED' | 'CLOSED'
export type Urgency = 'ALL' | 'NORMAL' | 'ATTENTION' | 'URGENT'

interface ReportQuery {
  penId: number
  urgency: Urgency
  status: ReportStatus
  keyword: string
  start: string
  end: string
}

const STORAGE_KEY = 'zmt.inspect.reports.query'
const PAGE_SIZES = [20, 50, 100]

function defaultRange(): { start: string; end: string } {
  const end = dayjs(todayIso())
  return { start: end.subtract(29, 'day').format('YYYY-MM-DD'), end: end.format('YYYY-MM-DD') }
}

function defaultQuery(): ReportQuery {
  return { penId: 0, urgency: 'ALL', status: 'ALL', keyword: '', ...defaultRange() }
}

function emptyTabCounts(): Record<string, number> {
  return { ALL: 0, PENDING: 0, DIAGNOSED: 0, HANDLED: 0, CLOSED: 0 }
}

function isDate(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && dayjs(value).isValid()
}

function isStatus(value: unknown): value is ReportStatus {
  return value === 'ALL' || value === 'PENDING' || value === 'DIAGNOSED' || value === 'HANDLED' || value === 'CLOSED'
}

function isUrgency(value: unknown): value is Urgency {
  return value === 'ALL' || value === 'NORMAL' || value === 'ATTENTION' || value === 'URGENT'
}

/** 从 sessionStorage 恢复筛选条件，浏览器刷新后仍保持上次的筛选。 */
function readSavedState(): { query: ReportQuery; page: number; size: number; farmId: number } | null {
  if (typeof sessionStorage === 'undefined') return null
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as {
      query?: Partial<ReportQuery>
      page?: unknown
      size?: unknown
      farmId?: unknown
    }
    const saved = parsed.query || {}
    const defaults = defaultRange()
    const size = Number(parsed.size)
    return {
      query: {
        penId: Number(saved.penId) > 0 ? Number(saved.penId) : 0,
        urgency: isUrgency(saved.urgency) ? saved.urgency : 'ALL',
        status: isStatus(saved.status) ? saved.status : 'ALL',
        keyword: typeof saved.keyword === 'string' ? saved.keyword : '',
        start: isDate(saved.start) ? saved.start : defaults.start,
        end: isDate(saved.end) ? saved.end : defaults.end
      },
      page: Math.max(1, Math.floor(Number(parsed.page) || 1)),
      size: PAGE_SIZES.includes(size) ? size : 20,
      farmId: Number(parsed.farmId) || 0
    }
  } catch {
    return null
  }
}

function isCanceled(error: unknown): boolean {
  const err = error as { isCancel?: boolean; name?: string; code?: string } | null
  return !!err && (err.isCancel === true || err.name === 'CanceledError' || err.code === 'ERR_CANCELED')
}

function penNamesOf(list?: ReportRow[]): string[] {
  const names = new Set<string>()
  for (const item of list || []) {
    if (item.pen_name) names.add(item.pen_name)
  }
  return Array.from(names)
}

export const useAbnormalReportsStore = defineStore('abnormalReports', {
  state: () => {
    const saved = readSavedState()
    return {
      status: 'loading' as PageStatus,
      error: '',
      rows: [] as ReportRow[],
      total: 0,
      tabCounts: emptyTabCounts(),
      pens: [] as InspectionListItem[],
      query: saved?.query || defaultQuery(),
      page: saved?.page || 1,
      size: saved?.size || 20,
      listLoading: false,
      requestSeq: 0,
      controller: null as AbortController | null,
      keywordTimer: 0,
      statsLoading: false,
      statsLoaded: false,
      statsSeq: 0,
      statsTabCounts: emptyTabCounts(),
      todayCount: 0,
      todayPenNames: [] as string[],
      urgentCount: 0,
      urgentPenNames: [] as string[],
      farmId: saved?.farmId || 0,
      detailCache: {} as Record<number, DiagnosisDetail>,
      detailLoading: false,
      rediagnosingId: 0,
      drawerOpen: false,
      drawerInspectionId: 0,
      drawerPenId: 0,
      loaded: false
    }
  },
  getters: {
    isStaff(): boolean {
      return useFarmStore().role === 'staff'
    },
    /** 服务端已按条件过滤并分页，rows 即当前页结果。 */
    filtered(state): ReportRow[] {
      return state.rows
    },
    paged(state): ReportRow[] {
      return state.rows
    },
    pageTotal(state): number {
      return Math.max(1, Math.ceil(state.total / state.size))
    },
    hasFilter(state): boolean {
      const defaults = defaultRange()
      return (
        !!state.query.penId ||
        state.query.urgency !== 'ALL' ||
        state.query.status !== 'ALL' ||
        !!state.query.keyword.trim() ||
        state.query.start !== defaults.start ||
        state.query.end !== defaults.end
      )
    },
    isDefaultRange(state): boolean {
      const defaults = defaultRange()
      return state.query.start === defaults.start && state.query.end === defaults.end
    },
    todayPens(state): string {
      if (state.todayPenNames.length) return state.todayPenNames.join('、')
      return state.todayCount ? '今天有上报' : '今天还没有上报'
    },
    urgentNames(state): string {
      if (!state.urgentCount) return '没有紧急诊断'
      const names = state.urgentPenNames.join('、')
      return names ? `${names} · 已立即推送` : '已立即推送'
    },
    pendingHandleCount(state): number {
      return Number(state.statsTabCounts.DIAGNOSED || 0)
    },
    handledCount(state): number {
      return Number(state.statsTabCounts.HANDLED || 0)
    },
    closedCount(state): number {
      return Number(state.statsTabCounts.CLOSED || 0)
    },
    totalCount(state): number {
      return Number(state.statsTabCounts.ALL || 0)
    },
    statusItems(state): Array<{ label: string; value: ReportStatus; count: number }> {
      const count = (status: ReportStatus): number => Number(state.tabCounts[status] || 0)
      return [
        { label: '全部', value: 'ALL', count: count('ALL') },
        { label: '待诊断', value: 'PENDING', count: count('PENDING') },
        { label: '已诊断', value: 'DIAGNOSED', count: count('DIAGNOSED') },
        { label: '已处理', value: 'HANDLED', count: count('HANDLED') },
        { label: '已结案', value: 'CLOSED', count: count('CLOSED') }
      ]
    },
    penItems(state): Array<{ title: string; value: number }> {
      const seen = new Set<number>()
      const items: Array<{ title: string; value: number }> = [{ title: '全部栏舍', value: 0 }]
      for (const pen of state.pens) {
        if (!pen.pen_id || seen.has(pen.pen_id)) continue
        seen.add(pen.pen_id)
        items.push({ title: pen.pen_name || pen.pen_code || `栏舍 ${pen.pen_id}`, value: pen.pen_id })
      }
      return items
    },
    urgencyItems(): Array<{ title: string; value: Urgency }> {
      return [
        { title: '全部紧急程度', value: 'ALL' },
        { title: '一般', value: 'NORMAL' },
        { title: '关注', value: 'ATTENTION' },
        { title: '紧急', value: 'URGENT' }
      ]
    }
  },
  actions: {
    async load(): Promise<void> {
      const farmId = useFarmStore().currentFarmId
      if (this.farmId && this.farmId !== farmId) {
        // 换场后栏舍、上报记录和详情缓存都属于旧场，不能继续展示。
        this.query.penId = 0
        this.page = 1
        this.pens = []
        this.rows = []
        this.total = 0
        this.tabCounts = emptyTabCounts()
        this.statsTabCounts = emptyTabCounts()
        this.detailCache = {}
        this.status = 'loading'
        this.loaded = false
      }
      if (farmId) this.farmId = farmId
      this.error = ''
      await Promise.all([this.loadPens(), this.loadList(), this.loadStats()])
    },
    async loadPens(): Promise<void> {
      if (this.pens.length) return
      try {
        const page = await fetchDayList({ date: todayIso(), status: 'ALL', page: 1, size: 100 })
        this.pens = page.list || []
      } catch {
        this.pens = []
      }
    },
    async loadList(): Promise<void> {
      this.cancelKeywordTimer()
      this.controller?.abort()
      const controller = new AbortController()
      this.controller = controller
      this.requestSeq += 1
      const seq = this.requestSeq
      if (!this.rows.length) this.status = 'loading'
      this.listLoading = true
      this.error = ''
      try {
        const keyword = this.query.keyword.trim()
        const data = await fetchAbnormalReports(
          {
            page: this.page,
            size: this.size,
            status: this.query.status,
            pen_id: this.query.penId || undefined,
            urgency: this.query.urgency,
            keyword: keyword || undefined,
            start_date: this.query.start,
            end_date: this.query.end
          },
          controller.signal
        )
        if (seq !== this.requestSeq) return
        this.rows = data.list || []
        this.total = Number(data.total || 0)
        this.tabCounts = { ...emptyTabCounts(), ...(data.tab_counts || {}) }
        if (data.size && data.size !== this.size) this.size = data.size
        if (!this.rows.length && this.total > 0 && this.page > 1) {
          const lastPage = Math.max(1, Math.ceil(this.total / this.size))
          if (lastPage !== this.page) {
            this.page = lastPage
            this.persistState()
            this.listLoading = false
            return this.loadList()
          }
        }
        this.status = this.total ? 'ready' : this.hasFilter ? 'filtered_empty' : 'empty'
        this.loaded = true
        this.persistState()
      } catch (error) {
        if (seq !== this.requestSeq || isCanceled(error)) return
        const err = error as ApiError
        if (!this.rows.length) {
          this.status = err.isForbidden ? 'forbidden' : 'error'
          this.error = err.message || '上报记录加载失败'
        } else {
          useUiStore().toast(err.message || '上报记录刷新失败，请稍后重试', 'error')
        }
      } finally {
        if (seq === this.requestSeq) this.listLoading = false
      }
    },
    async loadStats(): Promise<void> {
      if (this.isStaff) return
      this.statsSeq += 1
      const seq = this.statsSeq
      this.statsLoading = true
      try {
        const today = todayIso()
        const [rangePage, todayPage, urgentPage] = await Promise.all([
          fetchAbnormalReports({
            page: 1,
            size: 1,
            status: 'ALL',
            start_date: this.query.start,
            end_date: this.query.end
          }),
          fetchAbnormalReports({ page: 1, size: 100, status: 'ALL', start_date: today, end_date: today }),
          fetchAbnormalReports({
            page: 1,
            size: 100,
            status: 'ALL',
            urgency: 'URGENT',
            start_date: this.query.start,
            end_date: this.query.end
          })
        ])
        if (seq !== this.statsSeq) return
        this.statsTabCounts = { ...emptyTabCounts(), ...(rangePage.tab_counts || {}) }
        this.todayCount = Number(todayPage.total || 0)
        this.todayPenNames = penNamesOf(todayPage.list)
        this.urgentCount = Number(urgentPage.total || 0)
        this.urgentPenNames = penNamesOf(urgentPage.list)
        this.statsLoaded = true
      } catch {
        if (seq === this.statsSeq) this.statsLoaded = false
      } finally {
        if (seq === this.statsSeq) this.statsLoading = false
      }
    },
    async fetchDetail(reportId: number): Promise<DiagnosisDetail | null> {
      if (this.detailCache[reportId]) return this.detailCache[reportId]
      this.detailLoading = true
      try {
        const detail = await fetchDiagnosis(reportId)
        this.detailCache[reportId] = detail
        return detail
      } catch {
        return null
      } finally {
        this.detailLoading = false
      }
    },
    async rediagnose(reportId: number): Promise<void> {
      if (!reportId || this.rediagnosingId) return
      this.rediagnosingId = reportId
      try {
        await requestRediagnose(reportId)
        delete this.detailCache[reportId]
        await this.loadList()
      } finally {
        this.rediagnosingId = 0
      }
    },
    cancelKeywordTimer(): void {
      if (!this.keywordTimer) return
      window.clearTimeout(this.keywordTimer)
      this.keywordTimer = 0
    },
    persistState(): void {
      if (typeof sessionStorage === 'undefined') return
      try {
        sessionStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({ query: this.query, page: this.page, size: this.size, farmId: this.farmId })
        )
      } catch {
        /* 隐私模式下写入失败不影响页面 */
      }
    },
    setKeyword(value: unknown): void {
      const keyword = String(value ?? '')
      if (this.query.keyword === keyword) return
      this.query.keyword = keyword
      this.page = 1
      this.persistState()
      this.cancelKeywordTimer()
      this.keywordTimer = window.setTimeout(() => {
        this.keywordTimer = 0
        void this.loadList()
      }, 300)
    },
    setPen(penId: number): void {
      if (this.query.penId === penId) return
      this.cancelKeywordTimer()
      this.query.penId = penId
      this.page = 1
      this.persistState()
      void this.loadList()
    },
    setUrgency(urgency: Urgency): void {
      if (this.query.urgency === urgency) return
      this.cancelKeywordTimer()
      this.query.urgency = urgency
      this.page = 1
      this.persistState()
      void this.loadList()
    },
    setStatus(status: ReportStatus): void {
      if (this.query.status === status) return
      this.cancelKeywordTimer()
      this.query.status = status
      this.page = 1
      this.persistState()
      void this.loadList()
    },
    setRange(start: string, end: string): void {
      if (!start || !end || start > end) return
      if (this.query.start === start && this.query.end === end) return
      this.cancelKeywordTimer()
      this.query.start = start
      this.query.end = end
      this.page = 1
      this.persistState()
      void this.loadList()
      void this.loadStats()
    },
    setPage(page: number): void {
      const target = Math.floor(Number(page))
      if (!Number.isFinite(target) || target < 1 || target === this.page) return
      this.cancelKeywordTimer()
      this.page = target
      this.persistState()
      void this.loadList()
    },
    setSize(size: number): void {
      const target = Math.floor(Number(size))
      if (!PAGE_SIZES.includes(target) || target === this.size) return
      this.cancelKeywordTimer()
      this.size = target
      this.page = 1
      this.persistState()
      void this.loadList()
    },
    reset(): void {
      this.cancelKeywordTimer()
      this.query = defaultQuery()
      this.page = 1
      this.persistState()
      void this.loadList()
      void this.loadStats()
    },
    openDrawer(inspectionId = 0, penId = 0): void {
      this.drawerInspectionId = inspectionId
      this.drawerPenId = penId
      this.drawerOpen = true
    }
  }
})
