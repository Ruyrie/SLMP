import { defineStore } from 'pinia'
import type { ApiError } from '@zmt/shared'
import { dayjs, formatDate, today } from '@zmt/shared'
import {
  financeApi,
  type BusinessOverview,
  type FinanceSummary,
  type MarketTrend,
  type ReceivablePage
} from '@/api/finance'
import { currentMonth, type PageStatus } from '@/stores/finance/shared'

export const useFinanceOverviewStore = defineStore('financeOverview', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    data: null as BusinessOverview | null,
    summary: null as FinanceSummary | null,
    summaryStatus: 'loading' as PageStatus,
    summaryError: '',
    dueFilter: 'ALL',
    receivables: null as ReceivablePage | null,
    receivableStatus: 'loading' as PageStatus,
    receivableError: '',
    trend: null as MarketTrend | null,
    chartLabels: [] as string[],
    chartValues: [] as (number | null)[],
    chartStatus: 'loading' as PageStatus,
    chartError: '',
    loading: false
  }),
  getters: {
    todayDesc: () => `${formatDate(today())} · 本月数据截至今天`,
    monthNumber: () => dayjs(today()).month() + 1,
    hasReceivables(state): boolean {
      return (state.receivables?.total || 0) > 0 || (state.receivables?.list?.length || 0) > 0
    }
  },
  actions: {
    async load(): Promise<void> {
      if (this.loading) return
      this.loading = true
      this.status = this.data ? this.status : 'loading'
      this.error = ''
      void this.loadPriceChart()
      void this.loadReceivables()
      void this.loadSummary()
      try {
        this.data = await financeApi.overview()
        this.status = 'ready'
      } catch (e) {
        const err = e as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message
      } finally {
        this.loading = false
      }
    },

    async loadSummary(): Promise<void> {
      this.summaryStatus = this.summary ? this.summaryStatus : 'loading'
      this.summaryError = ''
      try {
        this.summary = await financeApi.monthlySummary(currentMonth())
        this.summaryStatus = 'ready'
      } catch (e) {
        const err = e as ApiError
        this.summaryStatus = 'error'
        this.summaryError = err.message
      }
    },

    async loadReceivables(): Promise<void> {
      this.receivableStatus = this.receivables?.list?.length ? this.receivableStatus : 'loading'
      this.receivableError = ''
      try {
        this.receivables = await financeApi.receivables({
          due_filter: this.dueFilter,
          page: 1,
          size: 20
        })
        this.receivableStatus = this.receivables.list.length ? 'ready' : 'empty'
      } catch (e) {
        const err = e as ApiError
        this.receivableStatus = err.isForbidden ? 'forbidden' : 'error'
        this.receivableError = err.message
      }
    },

    setDueFilter(value: string): void {
      if (this.dueFilter === value) return
      this.dueFilter = value
      void this.loadReceivables()
    },

    async loadPriceChart(): Promise<void> {
      this.chartStatus = 'loading'
      this.chartError = ''
      try {
        const filters = await financeApi.marketFilters()
        const defaults = filters.defaults || {}
        const trend = await financeApi.marketTrend({
          province_code: defaults.provinceCode,
          category_id: defaults.categoryId,
          breed_id: defaults.breedId,
          price_type: 'SLAUGHTER',
          range_days: 30
        })
        this.trend = trend
        const series = trend.series || []
        this.chartLabels = series.map((item) => {
          const date = dayjs(item.date)
          return date.isValid() ? `${date.month() + 1}/${date.date()}` : String(item.date || '')
        })
        this.chartValues = series.map((item) =>
          item.price === undefined || item.price === null ? null : Number(item.price)
        )
        this.chartStatus = series.length ? 'ready' : 'empty'
      } catch (e) {
        const err = e as ApiError
        this.chartStatus = err.isForbidden ? 'forbidden' : 'error'
        this.chartError = err.message
      }
    },

    /** 登记收款成功后刷新概览与待收款表 */
    async afterReceipt(): Promise<void> {
      await this.load()
    }
  }
})
