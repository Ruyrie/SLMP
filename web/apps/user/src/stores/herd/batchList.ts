import { defineStore } from 'pinia'
import type { BatchListItem, BatchLookup, BaseCategory, MyPens, PenOption } from '@/api/herd'
import { herdApi } from '@/api/herd'
import {
  errorText,
  statusForError,
  type PageStatus
} from './shared'

export type BatchTab = 'ACTIVE' | 'ENDED' | 'ALL'
export type LookupState = 'idle' | 'not_found' | 'invalid_format' | 'forbidden' | 'not_batch_qr'

export const useBatchListStore = defineStore('herdBatchList', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    list: [] as BatchListItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    query: {
      page: 1,
      size: 20,
      status: 'ACTIVE' as BatchTab,
      penId: 0,
      categoryId: 0,
      sort: 'ENTRY_DATE'
    },
    penOptions: [] as PenOption[],
    categories: [] as BaseCategory[],
    myPens: null as MyPens | null,
    lookupInput: '',
    lookupLoading: false,
    lookupMessage: '',
    lookupState: 'idle' as LookupState,
    lookupBatchNo: '',
    loaded: false,
    requestId: 0
  }),
  getters: {
    activeCount(state): number {
      return state.tabCounts.ACTIVE ?? 0
    },
    endedCount(state): number {
      return state.tabCounts.ENDED ?? 0
    },
    allCount(state): number {
      return state.tabCounts.ALL ?? 0
    },
    filteredEmpty(state): boolean {
      if (state.status !== 'ready') return false
      return state.total === 0
    },
    hasFilters(state): boolean {
      return !!state.query.penId || !!state.query.categoryId
    },
    penItems(state): Array<{ title: string; value: number }> {
      return [{ title: '全部栏舍', value: 0 }, ...state.penOptions.map((pen) => ({
        title: pen.pen_name || '',
        value: Number(pen.pen_id || 0)
      }))]
    },
    categoryItems(state): Array<{ title: string; value: number }> {
      return [{ title: '全部品类', value: 0 }, ...state.categories.map((category) => ({
        title: category.name || '',
        value: Number(category.id || 0)
      }))]
    },
    ownerDesc(state): string {
      return `在养 ${this.activeCount} 个批次，已结束 ${this.endedCount} 个`
    },
    staffPenCount(state): number {
      return Number(state.myPens?.pen_count || state.myPens?.pens?.length || 0)
    }
  },
  actions: {
    async load(isStaff: boolean, force = false): Promise<void> {
      if (this.loaded && !force) return
      const requestId = ++this.requestId
      this.status = this.list.length || this.myPens ? this.status : 'loading'
      this.error = ''
      try {
        if (isStaff) {
          const data = await herdApi.myPens()
          if (requestId !== this.requestId) return
          this.myPens = data
          this.loaded = true
          this.status = data.pens?.length ? 'ready' : 'empty'
          return
        }
        const page = await herdApi.batches({ ...this.query, penId: this.query.penId || undefined, categoryId: this.query.categoryId || undefined })
        if (requestId !== this.requestId) return
        this.list = page.list || []
        this.total = page.total || 0
        this.tabCounts = page.tab_counts || {}
        this.loaded = true
        this.status = this.list.length ? 'ready' : 'empty'
        if (!this.penOptions.length || !this.categories.length) void this.loadOptions()
      } catch (error) {
        if (requestId !== this.requestId) return
        this.status = statusForError(error)
        this.error = errorText(error, '批次加载失败，请稍后重新加载')
      }
    },
    async loadOptions(): Promise<void> {
      try {
        const [pens, categories] = await Promise.all([herdApi.penOptions(), herdApi.baseCategories()])
        this.penOptions = pens || []
        this.categories = categories || []
      } catch {
        /* 筛选选项失败不阻塞批次表格 */
      }
    },
    async fetchList(): Promise<void> {
      this.status = this.list.length ? this.status : 'loading'
      this.error = ''
      try {
        const page = await herdApi.batches({
          ...this.query,
          penId: this.query.penId || undefined,
          categoryId: this.query.categoryId || undefined
        })
        this.list = page.list || []
        this.total = page.total || 0
        this.tabCounts = page.tab_counts || {}
        this.loaded = true
        this.status = this.list.length ? 'ready' : 'empty'
      } catch (error) {
        this.status = statusForError(error)
        this.error = errorText(error, '批次加载失败，请稍后重新加载')
      }
    },
    setTab(tab: BatchTab): void {
      if (this.query.status === tab) return
      this.query.status = tab
      this.query.page = 1
      void this.fetchList()
    },
    setPen(penId: number): void {
      this.query.penId = Number(penId || 0)
      this.query.page = 1
      void this.fetchList()
    },
    setCategory(categoryId: number): void {
      this.query.categoryId = Number(categoryId || 0)
      this.query.page = 1
      void this.fetchList()
    },
    setPage(page: number): void {
      this.query.page = Math.max(1, page)
      void this.fetchList()
    },
    setSize(size: number): void {
      this.query.size = Math.max(1, size)
      this.query.page = 1
      void this.fetchList()
    },
    resetLookup(): void {
      this.lookupState = 'idle'
      this.lookupMessage = ''
      this.lookupBatchNo = ''
    },
    clearLookupInput(): void {
      this.lookupInput = ''
      this.resetLookup()
    },
    async lookup(batchNo: string): Promise<BatchLookup | null> {
      const value = batchNo.trim().toUpperCase()
      this.lookupInput = value
      this.lookupMessage = ''
      this.lookupState = 'idle'
      this.lookupBatchNo = value
      if (!value) {
        this.lookupState = 'invalid_format'
        this.lookupMessage = '请输入批次号'
        return null
      }
      this.lookupLoading = true
      try {
        const result = await herdApi.lookupBatch({ batchNo: value })
        if (result.result === 'NOT_FOUND') {
          this.lookupState = 'not_found'
          this.lookupMessage = result.message || `没有找到批次 ${value}，请核对批次号`
          return result
        }
        if (result.result === 'INVALID_FORMAT') {
          this.lookupState = 'invalid_format'
          this.lookupMessage = result.message || '批次号格式不正确，例如 ZM2610-031'
          return result
        }
        if (result.result === 'NO_PERMISSION') {
          this.lookupState = 'forbidden'
          this.lookupMessage = result.message || '你没有这个批次的查看权限'
          return result
        }
        if (result.result === 'NOT_BATCH_QR') {
          this.lookupState = 'not_batch_qr'
          this.lookupMessage = result.message || '没有识别到批次二维码，请对准批次标签重试'
          return result
        }
        return result
      } catch (error) {
        this.lookupState = 'not_found'
        this.lookupMessage = errorText(error, '批次查询失败，请稍后重试')
        return null
      } finally {
        this.lookupLoading = false
      }
    },
    reset(): void {
      this.$reset()
    }
  }
})
