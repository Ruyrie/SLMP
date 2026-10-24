import { defineStore } from 'pinia'
import { ApiError, dayjs, today as todayIso } from '@zmt/shared'
import {
  fetchDayList,
  fetchInspectionDetail,
  fetchPenHistory,
  fetchPenOverview,
  fetchRecordLogs,
  type InspectionDetail,
  type InspectionListItem,
  type PenHistoryParams,
  type PenInspectionOverview,
  type PenInspectionRow,
  type RecordLog
} from '@/api/inspect'
import { useUiStore } from '@/stores/ui'
import { useFarmStore } from '@/stores/farm'
import type { PageStatus } from '@/api/inspect'

type HistoryFilter = 'ALL' | 'ABNORMAL' | 'OVERDUE' | 'BACKFILL' | 'OFF_SITE'

function defaultRange(): { start: string; end: string } {
  const end = dayjs(todayIso())
  return { start: end.subtract(29, 'day').format('YYYY-MM-DD'), end: end.format('YYYY-MM-DD') }
}

export const useInspectionHistoryStore = defineStore('inspectHistory', {
  state: () => {
    const range = defaultRange()
    return {
      status: 'loading' as PageStatus,
      error: '',
      pens: [] as InspectionListItem[],
      selectedPenId: 0,
      overview: null as PenInspectionOverview | null,
      overviewLoading: false,
      rows: [] as PenInspectionRow[],
      total: 0,
      tabCounts: {} as Record<string, number>,
      listLoading: false,
      filter: 'ALL' as HistoryFilter,
      start: range.start,
      end: range.end,
      page: 1,
      size: 20,
      locateDate: '',
      controller: null as AbortController | null,
      detailOpen: false,
      detailLoading: false,
      detailError: '',
      detail: null as InspectionDetail | null,
      logs: [] as RecordLog[],
      logsLoading: false,
      logsOpen: false,
      loaded: false
    }
  },
  getters: {
    isStaff(): boolean {
      return useFarmStore().role === 'staff'
    },
    canBackfill(): boolean {
      return !this.isStaff
    },
    selectedPen(state): InspectionListItem | undefined {
      return state.pens.find((pen) => pen.pen_id === state.selectedPenId)
    },
    penCount(): number {
      return this.pens.length
    },
    pageTotal(): number {
      return Math.max(1, Math.ceil(this.total / this.size))
    },
    filterItems(state): Array<{ label: string; value: HistoryFilter; count?: number }> {
      const count = (key: HistoryFilter): number | undefined => {
        const value = Number(state.tabCounts[key])
        return Number.isFinite(value) ? value : undefined
      }
      return [
        { label: '全部', value: 'ALL', count: count('ALL') ?? state.total },
        { label: '异常', value: 'ABNORMAL', count: count('ABNORMAL') },
        { label: '超时', value: 'OVERDUE', count: count('OVERDUE') },
        { label: '补录', value: 'BACKFILL', count: count('BACKFILL') },
        { label: '非现场', value: 'OFF_SITE', count: count('OFF_SITE') }
      ]
    },
    emptyFilterText(state): string {
      const map: Record<string, string> = {
        ABNORMAL: '没有异常记录',
        OVERDUE: '没有超时记录',
        BACKFILL: '没有补录记录',
        OFF_SITE: '没有非现场记录'
      }
      return map[state.filter] || '这个栏舍还没有巡检记录'
    },
    backfillHint(state): string {
      if (!state.overview?.backfill_from || !state.overview?.backfill_to) return ''
      return `漏巡只能补录前 3 天内的记录。今天可补录 ${state.overview.backfill_from.slice(5).replace('-', '月')}日–${state.overview.backfill_to.slice(5).replace('-', '月')}日。超过 3 天的记录需要提交修改申请，管理员审核通过后才会修改。`
    }
  },
  actions: {
    async load(): Promise<void> {
      this.controller?.abort()
      this.controller = new AbortController()
      const { signal } = this.controller
      this.error = ''
      this.status = this.pens.length ? this.status : 'loading'
      try {
        const page = await fetchDayList({ date: todayIso(), status: 'ALL', page: 1, size: 100 }, signal)
        this.pens = page.list || []
        if (!this.pens.some((pen) => pen.pen_id === this.selectedPenId)) {
          const preferred =
            this.pens.find((pen) => pen.display_status === 'ABNORMAL') ||
            this.pens.find((pen) => (pen.responsible_names || []).length > 0) ||
            this.pens[0]
          this.selectedPenId = preferred?.pen_id || 0
        }
        this.status = this.pens.length ? 'ready' : 'empty'
        this.loaded = true
        if (this.selectedPenId) {
          await Promise.all([this.loadOverview(), this.loadList()])
        }
      } catch (error) {
        const err = error as ApiError
        if (err.name === 'CanceledError' || (error as { isCancel?: boolean }).isCancel) return
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message || '加载失败，请稍后重试'
      }
    },
    async loadOverview(): Promise<void> {
      if (!this.selectedPenId) return
      this.overviewLoading = true
      try {
        this.overview = await fetchPenOverview(this.selectedPenId)
      } catch {
        this.overview = null
      } finally {
        this.overviewLoading = false
      }
    },
    async loadList(): Promise<void> {
      if (!this.selectedPenId) {
        this.rows = []
        this.total = 0
        this.tabCounts = {}
        return
      }
      this.listLoading = true
      try {
        const params: PenHistoryParams = {
          filter: this.filter,
          start_date: this.start,
          end_date: this.end,
          page: this.page,
          size: this.size
        }
        if (this.locateDate) params.locate_date = this.locateDate
        const data = await fetchPenHistory(this.selectedPenId, params)
        this.rows = data.list || []
        this.total = data.total || 0
        this.tabCounts = { ...(data.tab_counts || {}) }
        if (data.page && data.page !== this.page) this.page = data.page
        this.locateDate = ''
      } catch (error) {
        const err = error as ApiError
        if (err.isForbidden) {
          this.status = 'forbidden'
          this.error = err.message
        } else {
          useUiStore().toast(err.message || '巡检记录加载失败', 'error')
        }
      } finally {
        this.listLoading = false
      }
    },
    selectPen(penId: number): void {
      if (this.selectedPenId === penId) return
      this.selectedPenId = penId
      this.page = 1
      this.locateDate = ''
      void this.loadOverview()
      void this.loadList()
    },
    setFilter(filter: HistoryFilter): void {
      if (this.filter === filter) return
      this.filter = filter
      this.page = 1
      void this.loadList()
    },
    setRange(start: string, end: string): void {
      if (!start || !end) return
      this.start = start
      this.end = end
      this.page = 1
      this.locateDate = ''
      void this.loadList()
    },
    setPage(page: number): void {
      this.page = page
      void this.loadList()
    },
    setSize(size: number): void {
      this.size = size
      this.page = 1
      void this.loadList()
    },
    locate(date: string): void {
      this.page = 1
      this.locateDate = date
      void this.loadList()
    },
    async openDetail(inspectionId: number): Promise<void> {
      this.detailOpen = true
      this.detailLoading = true
      this.detailError = ''
      this.detail = null
      this.logs = []
      try {
        this.detail = await fetchInspectionDetail(inspectionId)
        if (this.detail.has_revisions) void this.loadLogs(inspectionId)
      } catch (error) {
        this.detailError = (error as ApiError).message || '巡检详情加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    async loadLogs(inspectionId: number): Promise<void> {
      this.logsLoading = true
      try {
        this.logs = await fetchRecordLogs('INSPECTION', inspectionId)
      } catch {
        this.logs = []
      } finally {
        this.logsLoading = false
      }
    },
    async refresh(): Promise<void> {
      await this.load()
      if (this.selectedPenId) {
        await Promise.all([this.loadOverview(), this.loadList()])
      }
    },
    reset(): void {
      this.controller?.abort()
      this.pens = []
      this.rows = []
      this.total = 0
      this.tabCounts = {}
      this.selectedPenId = 0
      this.overview = null
      this.loaded = false
      this.status = 'loading'
    }
  }
})

export type { HistoryFilter }
