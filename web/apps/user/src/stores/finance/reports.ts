import { defineStore } from 'pinia'
import type { ApiError } from '@zmt/shared'
import { financeApi, type BusinessReport, type ReportPeriod, type ReportSummary, type ReportTrend, type ReportExport } from '@/api/finance'
import { useUiStore } from '@/stores/ui'
import type { PageStatus } from '@/stores/finance/shared'

export type ReportMetric = 'STOCK' | 'MORTALITY' | 'FEED' | 'BALANCE'

/** 用临时 <a download> 下载签名地址（不打开新窗口，不会被弹窗拦截）。 */
export function downloadFile(url: string, fileName?: string): void {
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || ''
  link.rel = 'noopener'
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
}

export const useReportsStore = defineStore('financeReports', {
  state: () => ({
    reportType: 'TO_DATE' as 'TO_DATE' | 'WEEKLY' | 'MONTHLY',
    periodStart: '',
    scope: '' as '' | 'ALL',
    rangeType: 'FARM' as 'FARM' | 'CATEGORY' | 'BATCH',
    rangeRefId: 0,
    periods: [] as ReportPeriod[],
    periodsStatus: 'loading' as PageStatus,
    nextGenerationDate: '',
    periodsError: '',
    report: null as BusinessReport | null,
    status: 'loading' as PageStatus,
    error: '',
    metric: 'STOCK' as ReportMetric,
    trend: null as ReportTrend | null,
    trendStatus: 'loading' as PageStatus,
    trendError: '',
    summary: null as ReportSummary | null,
    summaryStatus: 'loading' as PageStatus,
    summaryError: '',
    regenerating: false,
    summaryTimer: 0 as number | undefined,
    categories: [] as { id?: number; name?: string }[],
    batches: [] as { batchId?: number; batchNo?: string; penName?: string; batchStatus?: string }[],
    rangeOptionsLoaded: false,
    exportOpen: false,
    exportFormat: 'XLSX',
    exportBusy: false,
    exportError: '',
    lastExport: null as ReportExport | null,
    loading: false
  }),
  getters: {
    reportTypeText(state): string {
      return state.reportType === 'WEEKLY' ? '周报' : state.reportType === 'MONTHLY' ? '月报' : '本期至今'
    },
    periodLabel(state): string {
      return state.report?.period?.label || ''
    },
    periodRangeText(state): string {
      const period = state.report?.period
      if (!period?.start || !period?.end) return ''
      const start = period.start.slice(5).replace('-', '月') + '日'
      const end = period.end.slice(5).replace('-', '月') + '日'
      return `${start}–${end}`
    },
    stockMetric(state) {
      const stock = state.report?.metrics?.stock || []
      return stock.length ? stock[0] : null
    },
    rangeText(): string {
      if (this.rangeType === 'CATEGORY') {
        return this.categories.find((item) => item.id === this.rangeRefId)?.name || '按品类'
      }
      if (this.rangeType === 'BATCH') {
        return this.batches.find((item) => item.batchId === this.rangeRefId)?.batchNo || '按批次'
      }
      return '整场'
    },
    canExport(state): boolean {
      return !state.report?.empty && state.status === 'ready'
    },
    profitItems(state): { batchId?: number; batchNo?: string; farmName?: string; profit?: number }[] {
      return [...(state.report?.batchProfits?.items || [])].sort((a, b) => Number(b.profit || 0) - Number(a.profit || 0))
    },
    maxProfit(): number {
      return this.profitItems.reduce((max, item) => Math.max(max, Math.abs(Number(item.profit || 0))), 0)
    }
  },
  actions: {
    async load(): Promise<void> {
      if (this.loading) return
      this.loading = true
      try {
        await this.loadPeriods()
        if (this.reportType !== 'TO_DATE' && !this.periodStart) {
          this.status = 'empty'
          this.report = null
          return
        }
        await this.loadReport()
      } finally {
        this.loading = false
      }
    },

    async loadPeriods(): Promise<void> {
      if (this.reportType === 'TO_DATE') {
        this.periods = []
        this.periodsStatus = 'ready'
        this.nextGenerationDate = ''
        return
      }
      this.periodsStatus = 'loading'
      this.periodsError = ''
      try {
        const data = await financeApi.reportPeriods(this.reportType, this.scope || undefined)
        this.periods = data.periods
        this.nextGenerationDate = data.nextGenerationDate || ''
        this.periodsStatus = data.periods.length ? 'ready' : 'empty'
        if (!this.periods.some((item) => item.periodStart === this.periodStart)) {
          this.periodStart = data.periods[0]?.periodStart || ''
        }
      } catch (e) {
        const err = e as ApiError
        this.periodsStatus = err.isForbidden ? 'forbidden' : 'error'
        this.periodsError = err.message
      }
    },

    payload(): {
      report_type: string
      period_start?: string
      scope?: string
      range_type?: string
      range_ref_id?: number | null
    } {
      return {
        report_type: this.reportType,
        period_start: this.reportType === 'TO_DATE' ? undefined : this.periodStart,
        scope: this.scope || undefined,
        range_type: this.rangeType,
        range_ref_id: this.rangeType === 'FARM' ? null : this.rangeRefId
      }
    },

    async loadReport(): Promise<void> {
      this.status = this.report ? this.status : 'loading'
      this.error = ''
      try {
        this.report = await financeApi.reportView(this.payload())
        this.status = this.report.empty ? 'empty' : 'ready'
        await Promise.all([this.loadSummary(), this.loadTrend()])
      } catch (e) {
        const err = e as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message
        if (err.fieldErrors.length) this.error = err.fieldErrors[0].message
      }
    },

    async loadTrend(): Promise<void> {
      if (this.status !== 'ready' && this.status !== 'empty') return
      this.trendStatus = 'loading'
      this.trendError = ''
      try {
        this.trend = await financeApi.reportTrend({ ...this.payload(), metric: this.metric })
        this.trendStatus = this.trend.points?.length ? 'ready' : 'empty'
      } catch (e) {
        const err = e as ApiError
        this.trendStatus = 'error'
        this.trendError = err.message
      }
    },

    async loadSummary(): Promise<void> {
      if (this.status !== 'ready' && this.status !== 'empty') return
      this.summaryStatus = this.summary?.status === 'SUCCESS' ? this.summaryStatus : 'loading'
      this.summaryError = ''
      try {
        this.summary = await financeApi.reportSummary(this.payload())
        this.applySummaryStatus()
      } catch (e) {
        const err = e as ApiError
        this.summaryStatus = 'error'
        this.summaryError = err.message
      }
    },

    applySummaryStatus(): void {
      const status = this.summary?.status
      if (status === 'SUCCESS') {
        this.summaryStatus = 'ready'
        window.clearTimeout(this.summaryTimer)
        return
      }
      if (status === 'FAILED') {
        this.summaryStatus = 'error'
        this.summaryError = 'AI 摘要生成失败，报表数据不受影响。'
        window.clearTimeout(this.summaryTimer)
        return
      }
      this.summaryStatus = 'loading'
      this.startSummaryPoll()
    },

    startSummaryPoll(): void {
      window.clearTimeout(this.summaryTimer)
      let attempts = 0
      const tick = async (): Promise<void> => {
        attempts += 1
        if (attempts > 20) {
          this.summaryStatus = 'error'
          this.summaryError = 'AI 摘要生成时间较长，请稍后刷新查看。'
          return
        }
        try {
          const summary = await financeApi.reportSummary(this.payload())
          this.summary = summary
          this.applySummaryStatus()
        } catch {
          this.summaryTimer = window.setTimeout(() => void tick(), 3000)
        }
      }
      this.summaryTimer = window.setTimeout(() => void tick(), 2500)
    },

    async setReportType(type: 'TO_DATE' | 'WEEKLY' | 'MONTHLY'): Promise<void> {
      if (this.reportType === type) return
      this.reportType = type
      this.periodStart = ''
      this.report = null
      this.summary = null
      this.trend = null
      await this.load()
    },

    async setPeriod(periodStart: string): Promise<void> {
      if (this.periodStart === periodStart) return
      this.periodStart = periodStart
      this.report = null
      this.summary = null
      await this.loadReport()
    },

    async setScope(scope: '' | 'ALL'): Promise<void> {
      if (this.scope === scope) return
      this.scope = scope
      this.report = null
      this.summary = null
      this.periodStart = ''
      await this.load()
    },

    async setRange(type: 'FARM' | 'CATEGORY' | 'BATCH', refId = 0): Promise<void> {
      this.rangeType = type
      this.rangeRefId = refId
      this.report = null
      this.summary = null
      if (type !== 'FARM' && !this.rangeOptionsLoaded) await this.loadRangeOptions()
      if (type === 'CATEGORY' && !this.rangeRefId) this.rangeRefId = Number(this.categories[0]?.id || 0)
      if (type === 'BATCH' && !this.rangeRefId) this.rangeRefId = Number(this.batches[0]?.batchId || 0)
      await this.loadReport()
    },

    async loadRangeOptions(): Promise<void> {
      this.rangeOptionsLoaded = true
      try {
        const [categories, batches] = await Promise.all([
          financeApi.baseCategories(),
          financeApi.settlementOptions({ page: 1, size: 100 })
        ])
        this.categories = categories.map((item) => ({ id: item.id, name: item.name }))
        this.batches = batches.list
      } catch {
        this.categories = []
        this.batches = []
      }
    },

    async setMetric(metric: ReportMetric): Promise<void> {
      if (this.metric === metric) return
      this.metric = metric
      await this.loadTrend()
    },

    async regenerateSummary(): Promise<void> {
      if (this.regenerating) return
      this.regenerating = true
      const ui = useUiStore()
      try {
        this.summary = await financeApi.regenerateReportSummary(this.payload())
        this.applySummaryStatus()
        ui.toast('已重新生成 AI 摘要')
      } catch (e) {
        const err = e as ApiError
        ui.toast(err.message || '生成失败，请稍后重试', 'error')
      } finally {
        this.regenerating = false
      }
    },

    openExport(): void {
      if (!this.canExport) return
      this.exportOpen = true
      this.exportError = ''
      this.exportFormat = 'XLSX'
    },
    async doExport(): Promise<void> {
      if (this.exportBusy) return
      this.exportBusy = true
      this.exportError = ''
      const ui = useUiStore()
      try {
        const result = await financeApi.exportReport({ ...this.payload(), format: this.exportFormat })
        this.lastExport = result
        // 请求返回后再 window.open 会被浏览器当成弹窗拦截；改为 <a download> 触发下载
        if (result.downloadUrl) downloadFile(result.downloadUrl, result.fileName)
        this.exportOpen = false
        ui.toast('已导出经营报表，浏览器开始下载', 'success', result.downloadUrl ? '重新下载' : '', () => {
          if (result.downloadUrl) downloadFile(result.downloadUrl, result.fileName)
        })
      } catch (e) {
        const err = e as ApiError
        this.exportError = err.message || '导出失败，请稍后重试'
        ui.toast(this.exportError, 'error')
      } finally {
        this.exportBusy = false
      }
    }
  }
})
