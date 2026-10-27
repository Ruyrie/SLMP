import { defineStore } from 'pinia'
import { dayjs, today } from '@zmt/shared'
import { newRequestKey, registerStockRefund, updateStockRefund, type StockOutbound, type StockRefundPayload } from '@/api/stock'
import { useFarmStore } from '@/stores/farm'
import { useAuthStore } from '@/stores/auth'

interface RefundAttempt { key: string; payload: StockRefundPayload }

export function stockDocumentScope(): string {
  const farm = useFarmStore()
  return `${useAuthStore().user?.user_id || 0}:${farm.currentFarmId}:${farm.revision}:${farm.role}`
}

function attemptScope(outboundId: number): string {
  return `${useAuthStore().user?.user_id || 0}:${useFarmStore().currentFarmId}:${outboundId}`
}

export const useStockRefundStore = defineStore('stockReturnRefund', {
  state: () => ({
    open: false,
    mode: 'register' as 'register' | 'edit',
    submitting: false,
    error: '',
    errors: {} as Record<string, string>,
    amount: '' as string | number,
    date: today(),
    remark: '',
    document: null as StockOutbound | null,
    scope: '',
    request: 0,
    attemptId: '',
    attempts: {} as Record<string, RefundAttempt>
  }),
  getters: {
    uncertain(state): boolean { return !!state.attempts[state.attemptId] },
    fieldsLocked(): boolean { return this.submitting || this.uncertain }
  },
  actions: {
    begin(document: StockOutbound, mode: 'register' | 'edit' = 'register'): void {
      this.close()
      const permitted = mode === 'edit' ? document.actions?.can_edit_refund : document.actions?.can_refund
      if (!permitted
          || useFarmStore().role === 'staff' || document.material_type !== 'DRUG' || document.out_type !== 'RETURN') return
      this.mode = mode
      this.document = document
      this.scope = stockDocumentScope()
      this.attemptId = `${attemptScope(document.outbound_id)}:${mode}`
      const attempt = this.attempts[this.attemptId]
      // 实际到账金额由用户确认，库存成本不是应退金额，不能静默预填。
      this.amount = attempt?.payload.amount ?? (mode === 'edit' ? document.refund_amount ?? '' : '')
      this.date = attempt?.payload.refund_date ?? (mode === 'edit' ? document.refund_date || today() : today())
      this.remark = attempt?.payload.remark ?? (mode === 'edit' ? document.refund_remark || '' : '')
      this.error = attempt ? '上次提交结果未确认，请使用原内容重试；系统会防止重复登记。' : ''
      this.open = true
    },
    close(): void {
      ++this.request
      this.open = false
      this.submitting = false
      this.document = null
      this.scope = ''
      this.attemptId = ''
      this.error = ''
      this.errors = {}
    },
    validate(): boolean {
      const errors: Record<string, string> = {}
      const amount = String(this.amount).trim()
      if (!/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount) <= 0 || Number(amount) > 9999999999.99) {
        errors.amount = '请输入大于 0、最多 2 位小数的实际到账金额'
      }
      const parsed = dayjs(this.date)
      if (!/^\d{4}-\d{2}-\d{2}$/.test(this.date) || !parsed.isValid() || parsed.format('YYYY-MM-DD') !== this.date) errors.refund_date = '请选择有效的到账日期'
      else if (this.date > today()) errors.refund_date = '到账日期不能晚于今天'
      else if (this.document?.out_date && this.date < this.document.out_date) errors.refund_date = '到账日期不能早于退货日期'
      if (this.remark.trim().length > 200) errors.remark = '说明不能超过 200 字'
      this.errors = errors
      return !Object.keys(errors).length
    },
    async submit(): Promise<StockOutbound | null> {
      const document = this.document
      const permitted = this.mode === 'edit' ? document?.actions?.can_edit_refund : document?.actions?.can_refund
      if (!this.open || this.submitting || !document || this.scope !== stockDocumentScope()
          || !permitted || useFarmStore().role === 'staff') return null
      if (!this.uncertain && !this.validate()) return null
      const request = ++this.request
      const scope = this.scope
      const attemptId = this.attemptId
      const attempt = this.attempts[attemptId] ||= { key: newRequestKey(), payload: {
        amount: Number(this.amount), refund_date: this.date, remark: this.remark.trim() || undefined,
        version: Number(document.version || 0)
      } }
      this.submitting = true
      this.error = ''
      const current = (): boolean => request === this.request && scope === stockDocumentScope() && this.open
      try {
        const save = this.mode === 'edit' ? updateStockRefund : registerStockRefund
        const result = await save(document.outbound_id, attempt.payload, attempt.key)
        delete this.attempts[attemptId]
        if (!current()) return null
        this.close()
        return result
      } catch (error) {
        const err = error as { status?: number; message?: string; fieldErrors?: Array<{ field: string; message: string }> }
        // 连接中断、服务端错误和处理中冲突可能已入账，保留同一请求内容与幂等键。
        const rejected = !!err.status && [400, 401, 403, 404, 422].includes(err.status)
        if (rejected) delete this.attempts[attemptId]
        if (!current()) return null
        this.error = err.message || '退款登记结果未确认，请重试'
        if (!rejected) this.error += '；重试将使用原内容，避免重复登记。'
        this.errors = Object.fromEntries((err.fieldErrors || []).map(field => [field.field, field.message]))
        return null
      } finally {
        if (current()) this.submitting = false
      }
    }
  }
})
