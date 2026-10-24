import { defineStore } from 'pinia'
import type { ApiError, FieldError } from '@zmt/shared'
import { formatMoney, today } from '@zmt/shared'
import { financeApi, newRequestKey, type SaleOrderDetail, type SaleOrderItem } from '@/api/finance'
import { useUiStore } from '@/stores/ui'

export interface ReceiveTarget {
  id: number
  batchNo?: string
  saleDate?: string
  saleAmount?: number
  receivedAmount?: number
  unpaidAmount?: number
}

export const useReceiveStore = defineStore('financeReceive', {
  state: () => ({
    open: false,
    target: null as ReceiveTarget | null,
    submitting: false,
    errors: [] as FieldError[],
    form: {
      amount: '' as string | number,
      date: today(),
      method: 'BANK_TRANSFER'
    }
  }),
  getters: {
    maxAmount(state): number {
      return Number(state.target?.unpaidAmount || 0)
    },
    hint(): string {
      const max = Number(this.maxAmount)
      const total = Number(this.target?.saleAmount || 0)
      return `最多 ${formatMoney(max)} 元，收款合计不能超过销售金额 ${formatMoney(total)} 元`
    },
    amountError(state): string {
      return state.errors.find((item) => item.field === 'amount')?.message || ''
    },
    dateError(state): string {
      return state.errors.find((item) => item.field === 'receipt_date')?.message || ''
    }
  },
  actions: {
    show(order: ReceiveTarget | SaleOrderItem | SaleOrderDetail, lastMethod?: string): void {
      const target = order as ReceiveTarget & SaleOrderItem
      if (!target?.id) return
      this.target = {
        id: Number(target.id),
        batchNo: target.batchNo,
        saleDate: target.saleDate,
        saleAmount: Number(target.saleAmount || 0),
        receivedAmount: Number(target.receivedAmount || 0),
        unpaidAmount: Number(target.unpaidAmount || 0)
      }
      this.form.amount = Number(target.unpaidAmount || 0).toFixed(2)
      this.form.date = today()
      this.form.method = lastMethod || 'BANK_TRANSFER'
      this.errors = []
      this.open = true
    },

    close(): void {
      this.open = false
      this.submitting = false
    },

    validate(): boolean {
      this.errors = []
      const amount = Number(this.form.amount)
      if (this.form.amount === '' || this.form.amount === null) {
        this.errors.push({ field: 'amount', message: '请输入收款金额' })
      } else if (!Number.isFinite(amount) || amount <= 0) {
        this.errors.push({ field: 'amount', message: '收款金额必须大于 0' })
      } else if (Math.round(amount * 100) !== Number((amount * 100).toFixed(0))) {
        this.errors.push({ field: 'amount', message: '金额最多保留 2 位小数' })
      } else if (amount - this.maxAmount > 0.0001) {
        this.errors.push({ field: 'amount', message: `不能超过未收金额 ${formatMoney(this.maxAmount)} 元` })
      }
      if (!this.form.date) {
        this.errors.push({ field: 'receipt_date', message: '请选择收款日期' })
      } else if (this.form.date > today()) {
        this.errors.push({ field: 'receipt_date', message: '收款日期不能晚于今天' })
      } else if (this.target?.saleDate && this.form.date < this.target.saleDate) {
        this.errors.push({ field: 'receipt_date', message: `收款日期不能早于出栏日期 ${this.target.saleDate}` })
      }
      if (!this.form.method) {
        this.errors.push({ field: 'payment_method', message: '请选择收款方式' })
      }
      return this.errors.length === 0
    },

    async submit(): Promise<SaleOrderDetail | null> {
      if (!this.target || this.submitting) return null
      if (!this.validate()) return null
      this.submitting = true
      const ui = useUiStore()
      try {
        const detail = await financeApi.addReceipt(
          this.target.id,
          {
            amount: Number(Number(this.form.amount).toFixed(2)),
            receipt_date: this.form.date,
            payment_method: this.form.method
          },
          newRequestKey()
        )
        const result = detail.receiptResult
        if (result?.paidOff) {
          ui.toast(`已登记收款 ${formatMoney(result.amount)} 元，出栏单已收齐`)
        } else {
          ui.toast(`已登记收款 ${formatMoney(result?.amount)} 元，还有 ${formatMoney(result?.unpaidAmount)} 元未收`)
        }
        this.open = false
        return detail
      } catch (e) {
        const err = e as ApiError
        const fieldErrors = err.fieldErrors
        if (fieldErrors.length) {
          this.errors = fieldErrors
        } else if (err.code === 42209 || err.code >= 42200) {
          ui.toast(err.message, 'error')
          this.open = false
        } else {
          ui.toast(err.message || '登记收款失败，请稍后重试', 'error')
        }
        return null
      } finally {
        this.submitting = false
      }
    }
  }
})
