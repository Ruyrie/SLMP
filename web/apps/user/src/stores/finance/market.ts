import { defineStore } from 'pinia'
import type { ApiError } from '@zmt/shared'
import { dayjs } from '@zmt/shared'
import {
  financeApi,
  type DailyPrice,
  type MarketFilter,
  type MarketTrend,
  type SaleAdvice,
  type SaleOrderBatchOption
} from '@/api/finance'
import { useUiStore } from '@/stores/ui'
import type { PageStatus } from '@/stores/finance/shared'

type AdviceStatus = PageStatus | 'unavailable'

export const useMarketStore = defineStore('financeMarket', {
  state: () => ({
    filters: null as MarketFilter | null,
    filterStatus: 'loading' as PageStatus,
    filterError: '',
    provinceCode: '',
    categoryId: 0,
    breedId: 0,
    priceType: 'SLAUGHTER',
    rangeDays: 30,
    trend: null as MarketTrend | null,
    trendStatus: 'loading' as PageStatus,
    trendError: '',
    daily: [] as DailyPrice[],
    dailyTotal: 0,
    dailyPage: 1,
    dailySize: 20,
    dailyStatus: 'loading' as PageStatus,
    dailyError: '',
    batches: [] as SaleOrderBatchOption[],
    batchStatus: 'loading' as PageStatus,
    batchError: '',
    batchId: 0,
    advice: null as SaleAdvice | null,
    adviceStatus: 'loading' as AdviceStatus,
    adviceError: '',
    adviceGenerating: false,
    history: [] as SaleAdvice[],
    historyTotal: 0,
    historyStatus: 'loading' as PageStatus,
    historyError: '',
    historyOpen: false,
    historyDetail: null as SaleAdvice | null,
    historyDetailLoading: false,
    pollTimer: 0 as number | undefined,
    touched: false,
    scope: 0,
    batchRequest: 0,
    adviceRequest: 0,
    historyRequest: 0,
    historyDetailRequest: 0,
    filterRequest: 0,
    trendRequest: 0,
    dailyRequest: 0,
    pollRequest: 0
  }),
  getters: {
    provinceName(state): string {
      return state.filters?.provinces.find((item) => item.code === state.provinceCode)?.name || '地区'
    },
    categoryName(state): string {
      return state.filters?.categories.find((item) => item.id === state.categoryId)?.name || '品类'
    },
    breedName(state): string {
      if (!state.breedId) return '全部品种'
      return state.filters?.breeds.find((item) => item.categoryId === state.categoryId && item.breedId === state.breedId)?.name || '全部品种'
    },
    priceTypeName(state): string {
      return state.priceType === 'SEEDLING' ? '种苗价' : '出栏价'
    },
    unitText(state): string {
      return state.trend?.latest?.priceUnitText || (state.priceType === 'SEEDLING' ? '元/头' : '元/千克')
    },
    /** 出栏价上涨有利，种苗价上涨不利 */
    priceUpIsGood(state): boolean {
      return state.priceType !== 'SEEDLING'
    },
    categoryBreeds(state): { categoryId?: number; breedId?: number; name?: string }[] {
      return (state.filters?.breeds || []).filter((item) => item.categoryId === state.categoryId)
    },
    chartLabels(state): string[] {
      return (state.trend?.series || []).map((item) => {
        const date = dayjs(item.date)
        return date.isValid() ? `${date.month() + 1}/${date.date()}` : String(item.date || '')
      })
    },
    chartValues(state): (number | null)[] {
      return (state.trend?.series || []).map((item) =>
        item.price === undefined || item.price === null ? null : Number(item.price)
      )
    },
    averageSeries(state): (number | null)[] {
      const avg = state.trend?.stats?.avgPrice
      if (avg === undefined || avg === null) return []
      return (state.trend?.series || []).map(() => Number(avg))
    },
    changeText(state): string {
      const change = state.trend?.change
      const rate = change?.changeRate
      if (rate === undefined || rate === null) return '持平'
      const n = Number(rate)
      if (!Number.isFinite(n) || Math.abs(n) < 0.00005) return '持平'
      const arrow = n > 0 ? '↑' : '↓'
      return `${arrow} ${n > 0 ? '涨' : '跌'} ${(Math.abs(n) * 100).toFixed(1)}%`
    },
    changeTone(): 'success' | 'error' | 'muted' {
      const rate = Number(this.trend?.change?.changeRate || 0)
      if (!Number.isFinite(rate) || Math.abs(rate) < 0.00005) return 'muted'
      return (rate > 0) === this.priceUpIsGood ? 'success' : 'error'
    },
    adviceUnavailable(state): boolean {
      return state.advice?.status === 'FAILED'
    },
    adviceConclusion(state): string {
      return state.advice?.conclusionText || '—'
    }
  },
  actions: {
    async load(preferredBatchId?: number): Promise<void> {
      this.dispose()
      const scope = this.scope
      this.batchId = preferredBatchId ?? this.batchId
      this.batches = []
      this.advice = null
      this.adviceError = ''
      this.adviceStatus = 'loading'
      this.history = []
      this.historyTotal = 0
      this.closeHistory()
      // 批次、建议和历史独立于行情图表加载，慢接口不阻塞其他区域。
      await Promise.all([
        this.loadBatches(),
        this.loadHistory(),
        this.loadFilters().then(async () => {
          if (scope !== this.scope || this.filterStatus !== 'ready') return
          await Promise.all([this.loadTrend(), this.loadDaily()])
        })
      ])
    },

    stopPoll(): void {
      window.clearTimeout(this.pollTimer)
      this.pollTimer = undefined
      this.pollRequest++
    },

    dispose(): void {
      this.scope++
      this.stopPoll()
      this.adviceGenerating = false
    },

    async loadFilters(): Promise<void> {
      const scope = this.scope
      const request = ++this.filterRequest
      this.filterStatus = 'loading'
      this.filterError = ''
      try {
        const filters = await financeApi.marketFilters()
        if (scope !== this.scope || request !== this.filterRequest) return
        this.filters = filters
        const defaults = filters.defaults || {}
        this.provinceCode =
          this.provinceCode ||
          defaults.provinceCode ||
          filters.provinces[0]?.code ||
          ''
        this.categoryId = this.categoryId || Number(defaults.categoryId || filters.categories[0]?.id || 0)
        if (!this.breedId) this.breedId = 0
        this.priceType = this.priceType || defaults.priceType || 'SLAUGHTER'
        this.filterStatus = 'ready'
      } catch (e) {
        if (scope !== this.scope || request !== this.filterRequest) return
        const err = e as ApiError
        this.filterStatus = err.isForbidden ? 'forbidden' : 'error'
        this.filterError = err.message
      }
    },

    async loadTrend(): Promise<void> {
      const scope = this.scope
      const request = ++this.trendRequest
      if (!this.provinceCode || !this.categoryId) {
        this.trend = null
        this.trendStatus = 'empty'
        return
      }
      this.trendStatus = 'loading'
      this.trendError = ''
      try {
        const trend = await financeApi.marketTrend({
          province_code: this.provinceCode,
          category_id: this.categoryId,
          breed_id: this.breedId || 0,
          price_type: this.priceType,
          range_days: this.rangeDays
        })
        if (scope !== this.scope || request !== this.trendRequest) return
        this.trend = trend
        this.trendStatus = trend.empty ? 'empty' : 'ready'
      } catch (e) {
        if (scope !== this.scope || request !== this.trendRequest) return
        const err = e as ApiError
        this.trendStatus = err.isForbidden ? 'forbidden' : 'error'
        this.trendError = err.message
      }
    },

    async loadDaily(): Promise<void> {
      const scope = this.scope
      const request = ++this.dailyRequest
      if (!this.provinceCode || !this.categoryId) {
        this.daily = []
        this.dailyTotal = 0
        this.dailyStatus = 'empty'
        return
      }
      this.dailyStatus = this.daily.length ? this.dailyStatus : 'loading'
      this.dailyError = ''
      try {
        const page = await financeApi.marketDaily({
          province_code: this.provinceCode,
          category_id: this.categoryId,
          breed_id: this.breedId || 0,
          price_type: this.priceType,
          range_days: this.rangeDays,
          page: this.dailyPage,
          size: this.dailySize
        })
        if (scope !== this.scope || request !== this.dailyRequest) return
        this.daily = page.list
        this.dailyTotal = page.total
        this.dailyStatus = page.list.length ? 'ready' : 'empty'
      } catch (e) {
        if (scope !== this.scope || request !== this.dailyRequest) return
        const err = e as ApiError
        this.dailyStatus = err.isForbidden ? 'forbidden' : 'error'
        this.dailyError = err.message
      }
    },

    async loadBatches(): Promise<void> {
      const scope = this.scope
      const request = ++this.batchRequest
      this.stopPoll()
      this.adviceRequest++
      this.batchStatus = 'loading'
      this.batchError = ''
      this.adviceStatus = 'loading'
      try {
        const batches = await financeApi.batchOptions()
        if (scope !== this.scope || request !== this.batchRequest) return
        this.batches = batches.filter((batch) => Number(batch.batchId) > 0)
        if (!this.batches.some((batch) => Number(batch.batchId) === this.batchId)) {
          this.batchId = Number(this.batches[0]?.batchId || 0)
        }
        this.batchStatus = this.batches.length ? 'ready' : 'empty'
        await this.loadAdvice()
      } catch (e) {
        if (scope !== this.scope || request !== this.batchRequest) return
        const err = e as ApiError
        this.batches = []
        this.batchId = 0
        this.advice = null
        this.batchStatus = err.isForbidden ? 'forbidden' : 'error'
        this.batchError = err.message || '在养批次加载失败，请重试'
        this.adviceStatus = 'error'
        this.adviceError = this.batchError
      }
    },

    async setFilter(patch: { provinceCode?: string; categoryId?: number; breedId?: number; priceType?: string }): Promise<void> {
      this.touched = true
      if (patch.provinceCode !== undefined) this.provinceCode = patch.provinceCode
      if (patch.categoryId !== undefined) {
        this.categoryId = patch.categoryId
        this.breedId = 0
      }
      if (patch.breedId !== undefined) this.breedId = patch.breedId
      if (patch.priceType !== undefined) this.priceType = patch.priceType
      this.dailyPage = 1
      await Promise.all([this.loadTrend(), this.loadDaily()])
    },

    async setRange(days: number): Promise<void> {
      if (this.rangeDays === days) return
      this.rangeDays = days
      this.dailyPage = 1
      await Promise.all([this.loadTrend(), this.loadDaily()])
    },

    restoreDefaults(): void {
      const defaults = this.filters?.defaults
      if (!defaults) return
      this.touched = false
      void this.setFilter({
        provinceCode: defaults.provinceCode,
        categoryId: Number(defaults.categoryId || 0),
        breedId: Number(defaults.breedId || 0),
        priceType: defaults.priceType || 'SLAUGHTER'
      })
    },

    setDailyPage(page: number): void {
      this.dailyPage = page
      void this.loadDaily()
    },

    setDailySize(size: number): void {
      this.dailySize = size
      this.dailyPage = 1
      void this.loadDaily()
    },

    async selectBatch(batchId: number): Promise<void> {
      if (batchId !== 0 && !this.batches.some((batch) => Number(batch.batchId) === batchId)) return
      if (this.batchId === batchId) return
      this.batchId = batchId
      this.advice = null
      await this.loadAdvice()
    },

    async loadAdvice(): Promise<void> {
      const scope = this.scope
      const request = ++this.adviceRequest
      const batchId = this.batchId
      this.stopPoll()
      this.adviceGenerating = false
      if (!this.batchId) {
        this.advice = null
        this.adviceStatus = 'empty'
        return
      }
      this.adviceStatus = this.advice ? this.adviceStatus : 'loading'
      this.adviceError = ''
      try {
        const advice = await financeApi.saleAdviceLatest(batchId)
        if (scope !== this.scope || request !== this.adviceRequest || batchId !== this.batchId) return
        this.advice = advice
        this.applyAdviceStatus(advice)
        if (advice.status === 'GENERATING' && advice.id) this.startPoll(Number(advice.id))
        if (advice.status === 'SUCCESS') void this.loadHistory()
      } catch (e) {
        if (scope !== this.scope || request !== this.adviceRequest || batchId !== this.batchId) return
        const err = e as ApiError
        this.adviceStatus = err.isForbidden ? 'forbidden' : 'error'
        this.adviceError = err.message
      }
    },

    applyAdviceStatus(advice: SaleAdvice): void {
      if (advice.status === 'FAILED') this.adviceStatus = 'unavailable'
      else if (advice.status === 'GENERATING' && advice.id) this.adviceStatus = 'loading'
      else if (advice.status === 'SUCCESS') this.adviceStatus = 'ready'
      else {
        this.adviceStatus = 'error'
        this.adviceError = '建议状态异常，请重试'
      }
    },

    startPoll(id: number): void {
      this.stopPoll()
      const scope = this.scope
      const request = this.adviceRequest
      const poll = this.pollRequest
      const batchId = this.batchId
      const deadline = Date.now() + 45_000
      const current = (): boolean => scope === this.scope && request === this.adviceRequest
        && poll === this.pollRequest && batchId === this.batchId
      let attempts = 0
      const tick = async (): Promise<void> => {
        if (!current()) return
        attempts += 1
        if (attempts > 10 || Date.now() >= deadline) {
          this.adviceStatus = 'error'
          this.adviceError = '建议生成时间较长，请稍后重试'
          this.stopPoll()
          return
        }
        try {
          const advice = await financeApi.saleAdviceDetail(id)
          if (!current()) return
          this.advice = advice
          this.applyAdviceStatus(advice)
          if (advice.status === 'GENERATING') {
            this.pollTimer = window.setTimeout(() => void tick(), 1500)
          } else {
            this.stopPoll()
            if (advice.status === 'SUCCESS') void this.loadHistory()
          }
        } catch {
          if (!current()) return
          this.pollTimer = window.setTimeout(() => void tick(), 2000)
        }
      }
      this.pollTimer = window.setTimeout(() => void tick(), 1200)
    },

    async regenerate(): Promise<void> {
      if (!this.batchId || this.adviceGenerating || this.adviceStatus === 'loading') return
      const scope = this.scope
      const request = ++this.adviceRequest
      const batchId = this.batchId
      this.stopPoll()
      this.adviceGenerating = true
      this.adviceStatus = 'loading'
      const ui = useUiStore()
      try {
        const advice = await financeApi.regenerateSaleAdvice(batchId)
        if (scope !== this.scope || request !== this.adviceRequest || batchId !== this.batchId) return
        this.advice = advice
        this.applyAdviceStatus(advice)
        if (advice.status === 'GENERATING' && advice.id) this.startPoll(Number(advice.id))
        if (advice.status === 'SUCCESS') {
          ui.toast('已重新生成出栏建议')
          void this.loadHistory()
        } else if (advice.status === 'GENERATING') ui.toast('正在生成出栏建议')
      } catch (e) {
        if (scope !== this.scope || request !== this.adviceRequest || batchId !== this.batchId) return
        const err = e as ApiError
        this.adviceStatus = 'error'
        this.adviceError = err.message
        ui.toast(err.message || '生成失败，请稍后重试', 'error')
      } finally {
        if (scope === this.scope && request === this.adviceRequest) this.adviceGenerating = false
      }
    },

    async loadHistory(): Promise<void> {
      const scope = this.scope
      const request = ++this.historyRequest
      this.historyStatus = 'loading'
      this.historyError = ''
      try {
        const page = await financeApi.saleAdviceHistory({ page: 1, size: 20 })
        if (scope !== this.scope || request !== this.historyRequest) return
        this.history = page.list
        this.historyTotal = page.total
        this.historyStatus = page.list.length ? 'ready' : 'empty'
      } catch (e) {
        if (scope !== this.scope || request !== this.historyRequest) return
        const err = e as ApiError
        this.historyStatus = err.isForbidden ? 'forbidden' : 'error'
        this.historyError = err.message
      }
    },

    async openHistory(advice: SaleAdvice): Promise<void> {
      if (!advice.id) return
      const scope = this.scope
      const request = ++this.historyDetailRequest
      this.historyOpen = true
      this.historyDetail = advice
      this.historyDetailLoading = true
      try {
        const detail = await financeApi.saleAdviceDetail(advice.id)
        if (scope === this.scope && request === this.historyDetailRequest) this.historyDetail = detail
      } catch {
        /* 保留列表中的摘要 */
      } finally {
        if (scope === this.scope && request === this.historyDetailRequest) this.historyDetailLoading = false
      }
    },

    closeHistory(): void {
      this.historyDetailRequest++
      this.historyOpen = false
      this.historyDetail = null
      this.historyDetailLoading = false
    }
  }
})
