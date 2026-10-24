import { defineStore } from 'pinia'
import type { ApiError } from '@zmt/shared'
import { financeApi, type RecordLog, type SaleOrderDetail } from '@/api/finance'
import { useUiStore } from '@/stores/ui'
import type { PageStatus } from '@/stores/finance/shared'

export const useSaleOrderDetailStore = defineStore('financeSaleOrderDetail', {
  state: () => ({
    id: 0,
    status: 'loading' as PageStatus,
    error: '',
    detail: null as SaleOrderDetail | null,
    voidOpen: false,
    voidReason: '',
    voidError: '',
    voidBusy: false,
    logs: [] as RecordLog[],
    logsLoading: false,
    logsError: '',
    loading: false
  }),
  getters: {
    canReceive(state): boolean {
      const detail = state.detail
      if (!detail) return false
      if (detail.paymentStatus === 'PAID' || detail.paymentStatus === 'VOIDED') return false
      return detail.actions?.canReceive !== false
    },
    hasReceipts(state): boolean {
      return (state.detail?.receipts?.length || 0) > 0
    },
    pendingChange(state): boolean {
      return !!state.detail?.pendingChange || !!state.detail?.actions?.changeInReview
    },
    progressPercent(state): number {
      return Math.min(100, Math.max(0, Number(state.detail?.progressRate || 0) * 100))
    }
  },
  actions: {
    async load(id: number): Promise<void> {
      if (!id) return
      if (this.loading) return
      this.id = id
      this.loading = true
      this.status = this.detail && Number(this.detail.id) === id ? this.status : 'loading'
      this.error = ''
      try {
        this.detail = await financeApi.saleOrderDetail(id)
        this.status = 'ready'
        void this.loadLogs()
      } catch (e) {
        const err = e as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message
      } finally {
        this.loading = false
      }
    },

    async refresh(): Promise<void> {
      if (!this.id) return
      this.detail = null
      await this.load(this.id)
    },

    async loadLogs(): Promise<void> {
      if (!this.id) return
      this.logsLoading = true
      this.logsError = ''
      try {
        this.logs = await financeApi.recordLogs('SALE_ORDER', this.id)
      } catch (e) {
        this.logsError = (e as ApiError).message
      } finally {
        this.logsLoading = false
      }
    },

    openVoid(): void {
      this.voidReason = ''
      this.voidError = ''
      this.voidOpen = true
    },

    async confirmVoid(): Promise<void> {
      if (!this.detail?.id || this.voidBusy) return
      const reason = this.voidReason.trim()
      if (!reason) {
        this.voidError = '请填写作废原因'
        return
      }
      if ([...reason].length > 200) {
        this.voidError = '作废原因不能超过 200 字'
        return
      }
      this.voidBusy = true
      const ui = useUiStore()
      try {
        this.detail = await financeApi.voidSaleOrder(this.detail.id, reason, this.detail.version)
        this.voidOpen = false
        ui.toast('已作废出栏单，存栏已恢复')
      } catch (e) {
        const err = e as ApiError
        this.voidError = err.fieldErrors[0]?.message || err.message || '作废失败'
      } finally {
        this.voidBusy = false
      }
    }
  }
})
