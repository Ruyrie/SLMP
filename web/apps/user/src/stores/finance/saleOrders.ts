import { defineStore } from 'pinia'
import type { ApiError } from '@zmt/shared'
import { dayjs } from '@zmt/shared'
import { financeApi, type MonthGroup, type SaleOrderItem } from '@/api/finance'
import type { PageStatus } from '@/stores/finance/shared'

export const useSaleOrdersStore = defineStore('financeSaleOrders', {
  state: () => ({
    query: { page: 1, size: 20, status: 'ALL', keyword: '', month: '' },
    list: [] as SaleOrderItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    monthGroups: [] as MonthGroup[],
    status: 'loading' as PageStatus,
    error: '',
    controller: null as AbortController | null,
    keywordTimer: 0 as number | undefined
  }),
  getters: {
    tabs(state): { label: string; value: string; count: number }[] {
      return [
        { label: '全部', value: 'ALL', count: Number(state.tabCounts.ALL ?? state.total) },
        { label: '未收款', value: 'UNPAID', count: Number(state.tabCounts.UNPAID ?? 0) },
        { label: '部分收款', value: 'PARTIAL', count: Number(state.tabCounts.PARTIAL ?? 0) },
        { label: '已收款', value: 'PAID', count: Number(state.tabCounts.PAID ?? 0) },
        { label: '已作废', value: 'VOIDED', count: Number(state.tabCounts.VOIDED ?? 0) }
      ]
    },
    monthOptions(): { title: string; value: string }[] {
      const options = [{ title: '全部月份', value: '' }]
      let cursor = dayjs().startOf('month')
      for (let i = 0; i < 13; i += 1) {
        options.push({
          title: cursor.year() === dayjs().year() ? `${cursor.month() + 1}月` : `${cursor.year()}年${cursor.month() + 1}月`,
          value: cursor.format('YYYY-MM')
        })
        cursor = cursor.subtract(1, 'month')
      }
      return options
    },
    groupOf(state) {
      return (month?: string): MonthGroup | undefined =>
        state.monthGroups.find((group) => group.month === month)
    },
    filteredEmpty(state): boolean {
      return state.status === 'filtered_empty'
    }
  },
  actions: {
    async load(): Promise<void> {
      this.controller?.abort()
      const controller = new AbortController()
      this.controller = controller
      this.status = this.list.length ? this.status : 'loading'
      this.error = ''
      try {
        const page = await financeApi.saleOrders({
          page: this.query.page,
          size: this.query.size,
          status: this.query.status,
          keyword: this.query.keyword || undefined,
          month: this.query.month || undefined
        })
        if (controller.signal.aborted) return
        this.list = page.list
        this.total = page.total
        this.tabCounts = page.tabCounts
        this.monthGroups = page.monthGroups
        const filtered = this.query.keyword || this.query.status !== 'ALL' || this.query.month
        this.status = this.list.length ? 'ready' : filtered ? 'filtered_empty' : 'empty'
      } catch (e) {
        const err = e as ApiError
        if (err.name === 'CanceledError' || err.name === 'AbortError') return
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message
      }
    },

    setStatus(value: string): void {
      if (this.query.status === value) return
      this.query.status = value
      this.query.page = 1
      void this.load()
    },

    setMonth(value: string): void {
      if (this.query.month === value) return
      this.query.month = value
      this.query.page = 1
      void this.load()
    },

    setKeyword(value: string): void {
      this.query.keyword = value
      window.clearTimeout(this.keywordTimer)
      this.keywordTimer = window.setTimeout(() => {
        this.query.page = 1
        void this.load()
      }, 300)
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

    resetFilters(): void {
      this.query = { page: 1, size: 20, status: 'ALL', keyword: '', month: '' }
      void this.load()
    }
  }
})
