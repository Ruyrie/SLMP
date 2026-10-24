import { defineStore } from 'pinia'
import { ApiError, dayjs, today as todayIso } from '@zmt/shared'
import {
  fetchDayList,
  fetchDiagnosis,
  fetchLinkableReports,
  type DiagnosisDetail,
  type InspectionListItem,
  type LinkableReport
} from '@/api/inspect'
import { fetchMessages } from '@/api/today'
import { useFarmStore } from '@/stores/farm'
import type { PageStatus } from '@/api/inspect'

export interface ReportRow {
  report_id: number
  reported_at: string
  reported_date: string
  pen_id: number
  pen_name: string
  batch_no: string
  sick_count: number
  stock_snapshot: number
  sick_rate: number | null
  symptom_names: string[]
  reporter_name: string
  top_cause: string
  urgency: string
  urgency_text: string
  status: string
  status_text: string
  source: string
  inspection_id: number | null
  detailLoaded: boolean
}

type ReportStatus = 'ALL' | 'PENDING' | 'DIAGNOSED' | 'HANDLED' | 'CLOSED'
type Urgency = 'ALL' | 'NORMAL' | 'ATTENTION' | 'URGENT'

function toDate(value?: string): string {
  return value ? value.slice(0, 10) : ''
}

export const useAbnormalReportsStore = defineStore('abnormalReports', {
  state: () => {
    const end = dayjs(todayIso())
    return {
      status: 'loading' as PageStatus,
      error: '',
      pens: [] as InspectionListItem[],
      rows: [] as ReportRow[],
      detailCache: {} as Record<number, DiagnosisDetail>,
      query: {
        penId: 0,
        urgency: 'ALL' as Urgency,
        status: 'ALL' as ReportStatus,
        start: end.subtract(29, 'day').format('YYYY-MM-DD'),
        end: end.format('YYYY-MM-DD')
      },
      page: 1,
      size: 20,
      drawerOpen: false,
      drawerInspectionId: 0,
      drawerPenId: 0,
      detailLoading: false,
      loaded: false
    }
  },
  getters: {
    isStaff(): boolean {
      return useFarmStore().role === 'staff'
    },
    filtered(state): ReportRow[] {
      return state.rows.filter((row) => {
        if (state.query.penId && row.pen_id !== state.query.penId) return false
        if (state.query.urgency !== 'ALL' && row.urgency !== state.query.urgency) return false
        if (state.query.status !== 'ALL' && row.status !== state.query.status) return false
        if (row.reported_date && row.reported_date < state.query.start) return false
        if (row.reported_date && row.reported_date > state.query.end) return false
        return true
      })
    },
    paged(): ReportRow[] {
      const from = (this.page - 1) * this.size
      return this.filtered.slice(from, from + this.size)
    },
    total(): number {
      return this.filtered.length
    },
    pageTotal(): number {
      return Math.max(1, Math.ceil(this.total / this.size))
    },
    hasFilter(): boolean {
      return (
        !!this.query.penId ||
        this.query.urgency !== 'ALL' ||
        this.query.status !== 'ALL' ||
        this.query.start !== dayjs(todayIso()).subtract(29, 'day').format('YYYY-MM-DD') ||
        this.query.end !== todayIso()
      )
    },
    todayCount(state): number {
      const today = todayIso()
      return state.rows.filter((row) => row.reported_date === today).length
    },
    todayPens(state): string {
      const today = todayIso()
      const names = Array.from(new Set(state.rows.filter((row) => row.reported_date === today).map((row) => row.pen_name)))
      return names.length ? names.join('、') : '今天还没有上报'
    },
    urgentCount(state): number {
      return state.rows.filter((row) => row.urgency === 'URGENT').length
    },
    urgentNames(state): string {
      const names = Array.from(new Set(state.rows.filter((row) => row.urgency === 'URGENT').map((row) => row.pen_name)))
      return names.length ? `${names.join('、')} · 已立即推送` : '没有紧急诊断'
    },
    pendingHandleCount(state): number {
      return state.rows.filter((row) => row.status === 'DIAGNOSED').length
    },
    handledCount(state): number {
      return state.rows.filter((row) => row.status === 'HANDLED').length
    },
    closedCount(state): number {
      return state.rows.filter((row) => row.status === 'CLOSED').length
    },
    totalCount(): number {
      return this.rows.length
    },
    statusItems(state): Array<{ label: string; value: ReportStatus; count: number }> {
      const count = (status: ReportStatus): number =>
        state.rows.filter((row) => status === 'ALL' || row.status === status).length
      return [
        { label: '全部', value: 'ALL', count: count('ALL') },
        { label: '待诊断', value: 'PENDING', count: count('PENDING') },
        { label: '已诊断', value: 'DIAGNOSED', count: count('DIAGNOSED') },
        { label: '已处理', value: 'HANDLED', count: count('HANDLED') },
        { label: '已结案', value: 'CLOSED', count: count('CLOSED') }
      ]
    },
    penItems(state): Array<{ title: string; value: number }> {
      return [{ title: '全部栏舍', value: 0 }, ...state.pens.map((pen) => ({ title: pen.pen_name || '', value: pen.pen_id }))]
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
      this.error = ''
      this.status = this.rows.length ? this.status : 'loading'
      this.detailLoading = true
      try {
        const page = await fetchDayList({ date: todayIso(), status: 'ALL', page: 1, size: 100 })
        this.pens = page.list || []
        if (this.isStaff) {
          // 员工只能查看自己上报的诊断：优先用 AI 诊断消息里的 biz_id 反查，避免逐条请求触发 403。
          const messages = await fetchMessages({ category: 'AI', page: 1, size: 100 })
          const farmId = useFarmStore().currentFarmId
          const ids = Array.from(
            new Set(
              (messages.list || [])
                .filter(
                  (item) =>
                    item.biz_type === 'ABNORMAL_REPORT' &&
                    Number(item.biz_id || 0) > 0 &&
                    (!farmId || Number(item.farm?.farm_id || 0) === farmId)
                )
                .map((item) => Number(item.biz_id))
            )
          )
          let rows: ReportRow[] = []
          if (ids.length) {
            const details = await Promise.all(ids.map((id) => this.detailCache[id] || this.fetchDetail(id)))
            rows = details
              .filter((detail): detail is DiagnosisDetail => !!detail)
              .map((detail) => this.mergeDetail(this.baseRowFromDetail(detail), detail))
          }
          if (!rows.length) {
            // 没有诊断消息时（新环境或待诊断），退回按负责栏舍的可关联上报，只展示不需要详情的字段。
            const groups = await Promise.all(
              this.pens.map(async (pen) => {
                try {
                  return await fetchLinkableReports({ pen_id: pen.pen_id })
                } catch {
                  return [] as LinkableReport[]
                }
              })
            )
            const byId = new Map<number, LinkableReport>()
            for (const group of groups) {
              for (const item of group) {
                if (item.status !== 'PENDING' && !byId.has(item.report_id)) byId.set(item.report_id, item)
              }
            }
            rows = Array.from(byId.values())
              .sort((a, b) => String(b.reported_at || '').localeCompare(String(a.reported_at || '')))
              .map((link) => this.baseRow(link))
          }
          this.rows = rows.sort((a, b) => b.reported_at.localeCompare(a.reported_at))
          this.status = this.rows.length ? 'ready' : 'empty'
          this.loaded = true
          return
        }
        const penIds = this.pens.map((pen) => pen.pen_id)
        const groups = await Promise.all(
          penIds.map(async (penId) => {
            try {
              return await fetchLinkableReports({ pen_id: penId })
            } catch {
              return [] as LinkableReport[]
            }
          })
        )
        const byId = new Map<number, LinkableReport>()
        for (const group of groups) {
          for (const item of group) {
            if (!byId.has(item.report_id)) byId.set(item.report_id, item)
          }
        }
        const links = Array.from(byId.values()).sort((a, b) => String(b.reported_at || '').localeCompare(String(a.reported_at || '')))
        const baseRows = links.map((link) => this.baseRow(link))
        this.rows = baseRows
        this.status = baseRows.length ? 'ready' : 'empty'
        this.loaded = true
        const tasks = links.map(async (link, index) => {
          const detail = this.detailCache[link.report_id] || (await this.fetchDetail(link.report_id))
          if (detail) this.applyDetail(index, detail)
        })
        await Promise.all(tasks)
      } catch (error) {
        const err = error as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message || '上报记录加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    baseRowFromDetail(detail: DiagnosisDetail): ReportRow {
      const report = detail.report
      return {
        report_id: detail.report_id,
        reported_at: report.reported_at || '',
        reported_date: toDate(report.reported_at),
        pen_id: report.pen_id,
        pen_name: report.pen_name || '',
        batch_no: report.batch_no || '',
        sick_count: report.sick_count || 0,
        stock_snapshot: report.stock_snapshot || 0,
        sick_rate: report.sick_rate ?? null,
        symptom_names: report.symptom_names || [],
        reporter_name: report.reporter?.name || '',
        top_cause: detail.diagnosis?.causes?.[0]?.disease_name || '',
        urgency: detail.diagnosis?.urgency || '',
        urgency_text: detail.diagnosis?.urgency_text || '',
        status: detail.status,
        status_text: detail.status_text || '',
        source: report.source || '',
        inspection_id: report.inspection_id ?? null,
        detailLoaded: true
      }
    },
    baseRow(link: LinkableReport): ReportRow {
      const status = link.status || ''
      const statusMap: Record<string, string> = { PENDING: '待诊断', DIAGNOSED: '已诊断', HANDLED: '已处理', CLOSED: '已结案' }
      return {
        report_id: link.report_id,
        reported_at: link.reported_at || '',
        reported_date: toDate(link.reported_at),
        pen_id: link.pen_id,
        pen_name: link.pen_name || '',
        batch_no: link.batch_no || '',
        sick_count: 0,
        stock_snapshot: 0,
        sick_rate: null,
        symptom_names: [],
        reporter_name: '',
        top_cause: link.top_cause_name || '',
        urgency: '',
        urgency_text: '',
        status,
        status_text: statusMap[status] || '',
        source: '',
        inspection_id: null,
        detailLoaded: false
      }
    },
    mergeDetail(row: ReportRow, detail: DiagnosisDetail): ReportRow {
      return {
        ...row,
        sick_count: detail.report.sick_count || 0,
        stock_snapshot: detail.report.stock_snapshot || 0,
        sick_rate: detail.report.sick_rate ?? null,
        symptom_names: detail.report.symptom_names || [],
        reporter_name: detail.report.reporter?.name || '',
        top_cause: detail.diagnosis?.causes?.[0]?.disease_name || row.top_cause,
        urgency: detail.diagnosis?.urgency || '',
        urgency_text: detail.diagnosis?.urgency_text || '',
        status: detail.status,
        status_text: detail.status_text || '',
        source: detail.report.source || '',
        inspection_id: detail.report.inspection_id ?? null,
        detailLoaded: true
      }
    },
    async fetchDetail(reportId: number): Promise<DiagnosisDetail | null> {
      try {
        const detail = await fetchDiagnosis(reportId)
        this.detailCache[reportId] = detail
        return detail
      } catch {
        return null
      }
    },
    applyDetail(index: number, detail: DiagnosisDetail): void {
      const row = this.rows[index]
      if (!row) return
      Object.assign(row, this.mergeDetail(row, detail))
    },
    setPen(penId: number): void {
      this.query.penId = penId
      this.page = 1
    },
    setUrgency(urgency: Urgency): void {
      this.query.urgency = urgency
      this.page = 1
    },
    setStatus(status: ReportStatus): void {
      this.query.status = status
      this.page = 1
    },
    setRange(start: string, end: string): void {
      if (!start || !end) return
      this.query.start = start
      this.query.end = end
      this.page = 1
    },
    setPage(page: number): void {
      this.page = page
    },
    setSize(size: number): void {
      this.size = size
      this.page = 1
    },
    reset(): void {
      const end = dayjs(todayIso())
      this.query = {
        penId: 0,
        urgency: 'ALL',
        status: 'ALL',
        start: end.subtract(29, 'day').format('YYYY-MM-DD'),
        end: end.format('YYYY-MM-DD')
      }
      this.page = 1
    },
    openDrawer(inspectionId = 0, penId = 0): void {
      this.drawerInspectionId = inspectionId
      this.drawerPenId = penId
      this.drawerOpen = true
    }
  }
})

export type { ReportStatus, Urgency }
