import { defineStore } from 'pinia'
import type { ApiError } from '@zmt/shared'
import { financeApi, type BatchSettlement, type SettlementOption } from '@/api/finance'
import { useUiStore } from '@/stores/ui'
import type { PageStatus } from '@/stores/finance/shared'

export const useSettlementStore = defineStore('financeSettlement', {
  state: () => ({
    options: [] as SettlementOption[],
    optionsStatus: 'loading' as PageStatus,
    optionsError: '',
    farmPending: 0,
    batchId: 0,
    detail: null as BatchSettlement | null,
    status: 'loading' as PageStatus,
    error: '',
    confirmOpen: false,
    confirmBusy: false,
    confirmError: ''
  }),
  getters: {
    selectedOption(state): SettlementOption | undefined {
      return state.options.find((item) => Number(item.batchId) === state.batchId)
    },
    locked(state): boolean {
      return state.detail?.status === 'LOCKED'
    },
    pendingSupplements(state): { id?: number; bizDate?: string; title?: string; amount?: number; costGroup?: string }[] {
      return state.detail?.supplements || []
    },
    hasPendingSupplements(state): boolean {
      return (state.detail?.supplements?.length || 0) > 0
    },
    batchSelectItems(state): { title: string; value: number; props: Record<string, unknown> }[] {
      return state.options.map((item) => ({
        title: `${item.batchNo || ''} · ${item.penName || ''}`,
        value: Number(item.batchId || 0),
        props: {
          subtitle: `${item.batchStatus === 'ACTIVE' ? '在养' : '已结束'}${
            item.pendingSupplementCount ? ` · ${item.pendingSupplementCount} 笔补录待确认` : ''
          }`
        }
      }))
    },
    costPerHeadNote(): string {
      const basis = this.detail?.perHeadBasis
      const count = this.detail?.perHeadCount ?? 0
      return basis === 'SOLD' ? `按出栏 ${count} 头平摊` : `截至今天按存栏 ${count} 头平摊`
    },
    profitLabel(): string {
      return this.locked ? '批次盈亏' : '截至今天盈亏'
    }
  },
  actions: {
    async loadOptions(): Promise<void> {
      this.optionsStatus = 'loading'
      this.optionsError = ''
      try {
        const page = await financeApi.settlementOptions({ page: 1, size: 100 })
        this.options = page.list
        this.farmPending = page.farmPendingSupplementCount || 0
        this.optionsStatus = page.list.length ? 'ready' : 'empty'
      } catch (e) {
        const err = e as ApiError
        this.optionsStatus = err.isForbidden ? 'forbidden' : 'error'
        this.optionsError = err.message
      }
    },

    async selectBatch(batchId: number): Promise<void> {
      if (!batchId || (batchId === this.batchId && this.detail)) return
      this.batchId = batchId
      await this.loadDetail()
    },

    async load(batchId?: number): Promise<void> {
      await this.loadOptions()
      const target = batchId || this.batchId || Number(this.options[0]?.batchId || 0)
      this.batchId = target
      if (target) await this.loadDetail()
      else this.status = 'empty'
    },

    async loadDetail(): Promise<void> {
      if (!this.batchId) {
        this.status = 'empty'
        return
      }
      this.status = this.detail ? this.status : 'loading'
      this.error = ''
      try {
        this.detail = await financeApi.settlementDetail(this.batchId)
        this.status = 'ready'
      } catch (e) {
        const err = e as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message
      }
    },

    openConfirm(): void {
      if (!this.hasPendingSupplements) return
      this.confirmOpen = true
      this.confirmError = ''
    },

    async confirm(): Promise<void> {
      if (!this.detail?.batch?.batchId || this.confirmBusy) return
      const ids = this.pendingSupplements.map((item: { id?: number }) => Number(item.id)).filter(Boolean)
      if (!ids.length) {
        this.confirmOpen = false
        return
      }
      this.confirmBusy = true
      this.confirmError = ''
      const ui = useUiStore()
      try {
        this.detail = await financeApi.confirmSupplements(Number(this.detail.batch.batchId), ids, this.detail.version)
        this.confirmOpen = false
        ui.toast('已重新计算批次核算')
        void this.loadOptions()
      } catch (e) {
        const err = e as ApiError
        this.confirmError = err.message || '确认失败，请稍后重试'
      } finally {
        this.confirmBusy = false
      }
    }
  }
})
