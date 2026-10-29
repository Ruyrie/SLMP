import { defineStore } from 'pinia'
import type { BatchListItem, CategoryStock, PenStockItem, ProductionOverview, ProductionReminder, StockSummary } from '@/api/herd'
import { herdApi } from '@/api/herd'
import { currentFarmScope } from '@/stores/farmScope'
import { errorText, statusForError, type PageStatus } from './shared'

type OverviewTab = 'pen' | 'category' | 'batch'


export const useStockOverviewStore = defineStore('herdStockOverview', {
  state: () => ({
    tab: 'pen' as OverviewTab,
    status: 'loading' as PageStatus,
    error: '',
    overview: null as ProductionOverview | null,
    summary: null as StockSummary | null,
    pens: [] as PenStockItem[],
    categories: [] as CategoryStock[],
    batches: [] as BatchListItem[],
    keyword: '',
    penSort: 'CODE',
    batchSort: 'ENTRY_DATE',
    expandedCategories: [] as number[],
    page: 1,
    size: 20,
    loaded: false,
    farmScope: '',
    requestId: 0
  }),
  getters: {
    unit(state): string {
      return state.summary?.unit_text || state.overview?.unit_text || '头'
    },
    farmLabel(state): string {
      return state.overview?.farm_name || ''
    },
    isFarmBlocked(state): boolean {
      const status = state.overview?.farm_status
      return status === 'PENDING' || status === 'REJECTED' || status === 'DISABLED'
    },
    filteredPens(state): PenStockItem[] {
      const keyword = state.keyword.trim().toLowerCase()
      const rows = keyword
        ? state.pens.filter((pen) => {
            const text = `${pen.pen_name || ''} ${pen.pen_code || ''} ${pen.pen_type_text || ''}`.toLowerCase()
            return text.includes(keyword)
          })
        : [...state.pens]
      const occupancy = (pen: PenStockItem): number => Number(pen.occupancy_rate || 0)
      const stock = (pen: PenStockItem): number => Number(pen.stock_count || 0)
      if (state.penSort === 'STOCK') rows.sort((a, b) => stock(b) - stock(a))
      else if (state.penSort === 'OCCUPANCY') rows.sort((a, b) => occupancy(b) - occupancy(a))
      else rows.sort((a, b) => String(a.pen_code || '').localeCompare(String(b.pen_code || ''), 'zh-CN'))
      return rows
    },
    filteredCategories(state): CategoryStock[] {
      const keyword = state.keyword.trim().toLowerCase()
      if (!keyword) return state.categories
      return state.categories.filter((category) => {
        const text = `${category.category_name || ''} ${(category.pen_names || []).join(' ')}`.toLowerCase()
        return text.includes(keyword) || (category.batches || []).some((batch) => String(batch.batch_no || '').toLowerCase().includes(keyword))
      })
    },
    filteredBatches(state): BatchListItem[] {
      const keyword = state.keyword.trim().toLowerCase()
      const rows = keyword
        ? state.batches.filter((batch) => {
            const text = `${batch.batch_no || ''} ${batch.breed_title || ''} ${(batch.pens || []).map((pen) => pen.pen_name).join(' ')}`.toLowerCase()
            return text.includes(keyword)
          })
        : [...state.batches]
      if (state.batchSort === 'DAY_AGE') rows.sort((a, b) => Number(b.day_age || 0) - Number(a.day_age || 0))
      else rows.sort((a, b) => String(b.entry_date || '').localeCompare(String(a.entry_date || '')))
      return rows
    },
    currentTotal(): number {
      if (this.tab === 'pen') return this.filteredPens.length
      if (this.tab === 'category') return this.filteredCategories.length
      return this.filteredBatches.length
    },
    pagedPens(): PenStockItem[] {
      const from = (this.page - 1) * this.size
      return this.filteredPens.slice(from, from + this.size)
    },
    pagedBatches(): BatchListItem[] {
      const from = (this.page - 1) * this.size
      return this.filteredBatches.slice(from, from + this.size)
    },
    pageTotal(): number {
      return this.tab === 'pen' ? this.filteredPens.length : this.filteredBatches.length
    },
    /** 服务端已排好序：休药中 → 未处理异常上报 → 其余预警 → 今日巡检汇总。 */
    allReminders(state): ProductionReminder[] {
      return state.overview?.reminders || []
    },
    /** 侧栏默认只放前 3 条，点“查看全部”在原处展开 */
    reminders(state): ProductionReminder[] {
      return (state.overview?.reminders || []).slice(0, 3)
    },
    reminderTotal(state): number {
      return state.overview?.reminder_total ?? state.overview?.reminders?.length ?? 0
    },
    categoryStockFoot(): string {
      const categories = this.summary?.categories || []
      if (!categories.length) return '暂无存栏品类'
      return categories
        .slice(0, 3)
        .map((category) => `${category.category_name || '品类'} ${Number(category.stock || 0).toLocaleString('en-US')} ${this.unit}`)
        .join(' · ')
    },
    mortalityText(): string {
      if (!this.summary || !this.summary.entry_total) return '无数据'
      return (Number(this.summary.mortality_rate || 0) * 100).toFixed(1)
    },
    mortalityUnit(): string {
      return this.mortalityText === '无数据' ? '' : '%'
    },
    occupancyText(): string {
      return (Number(this.summary?.occupancy_rate || 0) * 100).toFixed(1)
    },
    capacityFoot(): string {
      const count = this.summary?.commercial_pen_count || 0
      const capacity = Number(this.summary?.capacity_total || 0).toLocaleString('en-US')
      return `${count} 个商品栏舍 · 容量 ${capacity} ${this.unit}`
    },
    batchFoot(): string {
      const withdrawal = this.summary?.withdrawal_batch_count || 0
      return withdrawal > 0 ? `其中休药中 ${withdrawal} 个` : '当前没有休药中的批次'
    },
    hasNoPens(state): boolean {
      return state.status === 'ready' && !state.pens.length
    },
    filteredEmpty(): boolean {
      return this.status === 'ready' && !!this.keyword.trim() && this.currentTotal === 0
    }
  },
  actions: {
    async load(force = false): Promise<void> {
      const scope = currentFarmScope()
      if (this.farmScope !== scope) {
        this.reset()
        this.farmScope = scope
      }
      if (this.loaded && !force) return
      const requestId = ++this.requestId
      this.status = 'loading'
      this.loaded = false
      this.error = ''
      try {
        const [overview, summary, pensPage, categories, batchesPage] = await Promise.all([
          herdApi.productionOverview(),
          herdApi.stockSummary(),
          herdApi.stockPens({ page: 1, size: 200, sort: 'CODE' }),
          herdApi.stockCategories(),
          herdApi.batches({ page: 1, size: 200, status: 'ACTIVE', sort: 'ENTRY_DATE' })
        ])
        if (requestId !== this.requestId || scope !== currentFarmScope()) return
        this.overview = overview
        this.summary = summary
        this.pens = pensPage.list || []
        this.categories = categories || []
        this.batches = batchesPage.list || []
        this.loaded = true
        this.status = this.pens.length || this.batches.length ? 'ready' : 'empty'
      } catch (error) {
        if (requestId !== this.requestId || scope !== currentFarmScope()) return
        this.status = statusForError(error)
        this.error = errorText(error, '存栏加载失败，请稍后重新加载')
      }
    },
    setTab(tab: OverviewTab): void {
      this.tab = tab
      this.page = 1
    },
    setKeyword(value: string): void {
      this.keyword = value
      this.page = 1
    },
    setPenSort(value: string): void {
      this.penSort = value
      this.page = 1
    },
    setBatchSort(value: string): void {
      this.batchSort = value
      this.page = 1
    },
    clearKeyword(): void {
      this.keyword = ''
      this.page = 1
    },
    setPage(page: number): void {
      this.page = Math.max(1, page)
    },
    setSize(size: number): void {
      this.size = Math.max(1, size)
      this.page = 1
    },
    toggleCategory(categoryId?: number): void {
      if (!categoryId) return
      const index = this.expandedCategories.indexOf(categoryId)
      if (index >= 0) this.expandedCategories.splice(index, 1)
      else this.expandedCategories.push(categoryId)
    },
    isCategoryOpen(categoryId?: number): boolean {
      return !!categoryId && this.expandedCategories.includes(categoryId)
    },
    reset(): void {
      const nextRequestId = this.requestId + 1
      this.$reset()
      this.requestId = nextRequestId
    }
  }
})
