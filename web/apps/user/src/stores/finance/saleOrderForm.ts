import { defineStore } from 'pinia'
import type { ApiError, FieldError } from '@zmt/shared'
import { formatMoney, formatNumber, today } from '@zmt/shared'
import { useFarmStore } from '@/stores/farm'
import {
  financeApi,
  newRequestKey,
  type SaleOrderBatchOption,
  type SaleOrderCreated,
  type SaleOrderDetail,
  type SaleOrderPrefill
} from '@/api/finance'
import { useUiStore } from '@/stores/ui'

export interface PenQuantityRow {
  penId: number
  penName: string
  stockCount: number
  quantity: string | number
}

export interface SaleFormState {
  batchId: number
  saleDate: string
  quantity: string | number
  totalWeight: string | number
  pricingMethod: 'BY_WEIGHT' | 'BY_HEAD'
  unitPrice: string | number
  buyerName: string
  dueDate: string
  version?: number
}

export const useSaleOrderFormStore = defineStore('financeSaleOrderForm', {
  state: () => ({
    open: false,
    mode: 'create' as 'create' | 'edit',
    editId: 0,
    loading: false,
    submitting: false,
    loadError: '',
    batches: [] as SaleOrderBatchOption[],
    prefill: null as SaleOrderPrefill | null,
    form: {
      batchId: 0,
      saleDate: today(),
      quantity: '',
      totalWeight: '',
      pricingMethod: 'BY_WEIGHT',
      unitPrice: '',
      buyerName: '',
      dueDate: '',
      version: undefined
    } as SaleFormState,
    penRows: [] as PenQuantityRow[],
    errors: [] as FieldError[],
    withdrawal: null as null | { batchNo?: string; sellableDate?: string },
    lastPaymentMethod: ''
  }),
  getters: {
    selectedBatch(state): SaleOrderBatchOption | undefined {
      return state.batches.find((item) => Number(item.batchId) === Number(state.form.batchId))
    },
    selectedPens(state): SaleOrderBatchOption['pens'] {
      return state.batches.find((item) => Number(item.batchId) === Number(state.form.batchId))?.pens || []
    },
    needsPenSplit(state): boolean {
      return (state.batches.find((item) => Number(item.batchId) === Number(state.form.batchId))?.pens?.length || 0) > 1
    },
    unitText(): string {
      return this.selectedBatch?.countUnitText || useFarmStore().countUnit
    },
    priceUnit(): string {
      return this.form.pricingMethod === 'BY_HEAD' ? `元/${this.unitText}` : '元/千克'
    },
    maxQuantity(): number {
      return Number(this.selectedBatch?.currentCount || 0)
    },
    remaining(): number {
      return Math.max(0, this.maxQuantity - Number(this.form.quantity || 0))
    },
    avgWeight(): number {
      const quantity = Number(this.form.quantity || 0)
      const total = Number(this.form.totalWeight || 0)
      if (!quantity || !total) return 0
      return Math.round((total / quantity) * 10) / 10
    },
    saleAmount(): number {
      const quantity = Number(this.form.quantity || 0)
      const total = Number(this.form.totalWeight || 0)
      const price = Number(this.form.unitPrice || 0)
      if (!quantity || !price) return 0
      if (this.form.pricingMethod === 'BY_HEAD') return Math.round(quantity * price)
      if (!total) return 0
      return Math.round(total * price)
    },
    amountText(): string {
      return this.saleAmount > 0 ? `${formatMoney(this.saleAmount, 0)} 元` : '—'
    },
    hasAmount(): boolean {
      const quantity = Number(this.form.quantity || 0)
      const price = Number(this.form.unitPrice || 0)
      if (!quantity || !price) return false
      return this.form.pricingMethod === 'BY_HEAD' || Number(this.form.totalWeight || 0) > 0
    },
    canSubmit(): boolean {
      if (this.submitting) return false
      if (!this.batches.length) return false
      if (this.withdrawal) return false
      return true
    },
    batchItems(state): { title: string; value: number; props: Record<string, unknown> }[] {
      return state.batches.map((batch) => ({
        title: `${batch.batchNo || ''} · ${batch.penName || ''}`,
        value: Number(batch.batchId || 0),
        props: {
          subtitle: `存栏 ${formatNumber(batch.currentCount ?? 0)} ${batch.countUnitText || useFarmStore().countUnit}${batch.inWithdrawal ? ' · 休药中' : ''}`
        }
      }))
    },
    buyerItems(state): string[] {
      return state.prefill?.recentBuyers || []
    },
    priceReference(): string {
      const price = this.prefill?.marketPrice
      if (!price) return '参考：今天还没有当前品类出栏价'
      const province = price.provinceName || '本地区'
      const category = price.categoryName || '当前品类'
      return `参考：${province}${category}出栏价每千克 ${Number(price.price || 0).toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      })} 元`
    },
    fieldError(state) {
      return (field: string): string =>
        state.errors.find((item) => item.field === field || item.field.startsWith(`${field}.`))?.message || ''
    }
  },
  actions: {
    async openCreate(preselectedBatchId?: number): Promise<void> {
      this.mode = 'create'
      this.editId = 0
      this.form = {
        batchId: Number(preselectedBatchId || 0),
        saleDate: today(),
        quantity: '',
        totalWeight: '',
        pricingMethod: 'BY_WEIGHT',
        unitPrice: '',
        buyerName: '',
        dueDate: '',
        version: undefined
      }
      this.penRows = []
      this.errors = []
      this.withdrawal = null
      this.prefill = null
      this.open = true
      await this.loadBatches(preselectedBatchId)
    },

    async openEdit(order: SaleOrderDetail): Promise<void> {
      if (!order.id) return
      this.mode = 'edit'
      this.editId = Number(order.id)
      this.form = {
        batchId: Number(order.batchId || 0),
        saleDate: order.saleDate || today(),
        quantity: order.quantity ?? '',
        totalWeight: order.totalWeight ?? '',
        pricingMethod: (order.pricingMethod as 'BY_WEIGHT' | 'BY_HEAD') || 'BY_WEIGHT',
        unitPrice: order.unitPrice ?? '',
        buyerName: order.buyerName || '',
        dueDate: order.dueDate || '',
        version: order.version
      }
      this.penRows = []
      this.errors = []
      this.withdrawal = null
      this.open = true
      await this.loadBatches(this.form.batchId)
      await this.loadPrefill()
    },

    async loadBatches(preselectedBatchId?: number): Promise<void> {
      this.loading = true
      this.loadError = ''
      try {
        this.batches = await financeApi.batchOptions()
        const batchId = Number(preselectedBatchId || this.form.batchId || 0)
        if (batchId && this.batches.some((item) => Number(item.batchId) === batchId)) {
          this.form.batchId = batchId
        } else if (this.mode === 'create' && this.batches.length === 1) {
          this.form.batchId = Number(this.batches[0].batchId)
        }
        this.syncBatch()
        if (this.form.batchId) {
          await this.loadPrefill()
          if (this.mode === 'create' && this.prefill?.lastPaymentMethod) {
            this.lastPaymentMethod = this.prefill.lastPaymentMethod
          }
        }
      } catch (e) {
        this.loadError = (e as ApiError).message
        this.batches = []
      } finally {
        this.loading = false
      }
    },

    async loadPrefill(): Promise<void> {
      try {
        this.prefill = await financeApi.saleOrderPrefill(this.form.batchId || undefined, this.form.saleDate)
        if (this.mode === 'create' && !this.form.buyerName && this.prefill.lastBuyer) {
          this.form.buyerName = this.prefill.lastBuyer
        }
      } catch {
        this.prefill = null
      }
    },

    syncBatch(): void {
      const batch = this.selectedBatch
      if (!batch) {
        this.withdrawal = null
        this.penRows = []
        return
      }
      this.withdrawal = batch.inWithdrawal
        ? { batchNo: batch.batchNo, sellableDate: batch.sellableDate }
        : null
      this.penRows = (batch.pens || []).map((pen) => ({
        penId: Number(pen.penId || 0),
        penName: pen.penName || '',
        stockCount: Number(pen.stockCount || 0),
        quantity: ''
      }))
      if (this.form.quantity && Number(this.form.quantity) > Number(batch.currentCount || 0)) {
        this.form.quantity = batch.currentCount ?? ''
      }
    },

    async setBatch(batchId: number): Promise<void> {
      if (this.mode === 'edit') return
      this.form.batchId = batchId
      this.form.quantity = ''
      this.penRows = []
      this.withdrawal = null
      this.errors = []
      this.syncBatch()
      await this.loadPrefill()
    },

    setPricingMethod(method: 'BY_WEIGHT' | 'BY_HEAD'): void {
      if (this.form.pricingMethod === method) return
      this.form.pricingMethod = method
      this.form.unitPrice = ''
    },

    validate(): boolean {
      this.errors = []
      const quantity = Number(this.form.quantity)
      const total = Number(this.form.totalWeight)
      const price = Number(this.form.unitPrice)
      if (!this.form.batchId) this.errors.push({ field: 'batch_id', message: '请选择批次' })
      if (this.withdrawal) {
        this.errors.push({
          field: 'batch_id',
          message: `批次在休药期内，可出栏日期 ${this.withdrawal.sellableDate || ''}`
        })
      }
      if (this.form.saleDate > today()) {
        this.errors.push({ field: 'sale_date', message: '出栏日期不能晚于今天' })
      }
      if (this.form.quantity === '' || this.form.quantity === null) {
        this.errors.push({ field: 'quantity', message: '请输入数量' })
      } else if (!Number.isFinite(quantity) || quantity <= 0) {
        this.errors.push({ field: 'quantity', message: '数量必须大于 0' })
      } else if (!Number.isInteger(quantity)) {
        this.errors.push({ field: 'quantity', message: '数量必须为整数' })
      } else if (quantity > this.maxQuantity) {
        this.errors.push({ field: 'quantity', message: `不能超过批次当前存栏 ${formatNumber(this.maxQuantity)} ${this.unitText}` })
      }
      if (this.form.totalWeight === '' || this.form.totalWeight === null) {
        this.errors.push({ field: 'total_weight', message: '请输入总重' })
      } else if (!Number.isFinite(total) || total <= 0) {
        this.errors.push({ field: 'total_weight', message: '总重必须大于 0' })
      } else if (total > 99999999.99) {
        this.errors.push({ field: 'total_weight', message: '总重不能超过 99,999,999.99 千克，请核对输入' })
      } else if (Math.abs(total * 100 - Math.round(total * 100)) > 1e-6) {
        this.errors.push({ field: 'total_weight', message: '总重最多保留 2 位小数' })
      }
      if (this.form.unitPrice === '' || this.form.unitPrice === null) {
        this.errors.push({ field: 'unit_price', message: '请输入单价' })
      } else if (!Number.isFinite(price) || price <= 0) {
        this.errors.push({ field: 'unit_price', message: '单价必须大于 0' })
      } else if (price > 99999.99) {
        this.errors.push({ field: 'unit_price', message: '单价不能超过 99,999.99' })
      } else if (Math.abs(price * 100 - Math.round(price * 100)) > 1e-6) {
        this.errors.push({ field: 'unit_price', message: '单价最多保留 2 位小数' })
      }
      if (!this.errors.some(error => ['quantity', 'total_weight', 'unit_price'].includes(error.field))
        && this.saleAmount > 9999999999) {
        this.errors.push({ field: 'unit_price', message: '销售金额不能超过 9,999,999,999 元，请核对数量、总重和单价' })
      }
      if (!this.form.buyerName.trim()) {
        this.errors.push({ field: 'buyer_name', message: '请填写收购商' })
      } else if ([...this.form.buyerName.trim()].length > 30) {
        this.errors.push({ field: 'buyer_name', message: '收购商名称不能超过 30 字' })
      }
      if (this.form.dueDate && this.form.dueDate < this.form.saleDate) {
        this.errors.push({ field: 'due_date', message: '约定收款日期不能早于出栏日期' })
      }
      if (this.needsPenSplit && !this.withdrawal) {
        const sum = this.penRows.reduce((totalRow, row) => totalRow + Number(row.quantity || 0), 0)
        if (sum !== quantity) {
          this.errors.push({ field: 'pen_quantities', message: `各栏舍出栏数合计应等于出栏数量 ${formQuantityText(this.form.quantity)}` })
        }
        for (const row of this.penRows) {
          if (Number(row.quantity || 0) > row.stockCount) {
            this.errors.push({
              field: 'pen_quantities',
              message: `${row.penName}本批次在栏 ${formatNumber(row.stockCount)} ${this.unitText}，不能超过`
            })
          }
        }
      }
      return this.errors.length === 0
    },

    async submit(): Promise<{ created: SaleOrderCreated | null; detail: SaleOrderDetail | null }> {
      if (this.submitting) return { created: null, detail: null }
      if (!this.validate()) return { created: null, detail: null }
      this.submitting = true
      const ui = useUiStore()
      try {
        const payload = {
          batch_id: this.form.batchId,
          sale_date: this.form.saleDate,
          quantity: Number(this.form.quantity),
          total_weight: Number(this.form.totalWeight),
          pricing_method: this.form.pricingMethod,
          unit_price: Number(this.form.unitPrice),
          buyer_name: this.form.buyerName.trim(),
          due_date: this.form.dueDate || null,
          pen_quantities: this.needsPenSplit
            ? this.penRows
                .filter((row) => Number(row.quantity || 0) > 0)
                .map((row) => ({ pen_id: row.penId, quantity: Number(row.quantity) }))
            : undefined,
          version: this.form.version
        }
        if (this.mode === 'edit' && this.editId) {
          const detail = await financeApi.updateSaleOrder(this.editId, payload)
          ui.toast('已保存修改')
          this.open = false
          return { created: null, detail }
        }
        const created = await financeApi.createSaleOrder(payload, newRequestKey())
        ui.toast(
          created.batchEnded
            ? `已登记出栏单，${created.batchNo || ''} 已全部出栏`
            : `已登记出栏单，${created.batchNo || ''} 存栏剩 ${formatNumber(created.remainingCount ?? 0)} ${created.countUnitText || this.unitText}`
        )
        this.open = false
        return { created, detail: null }
      } catch (e) {
        const err = e as ApiError
        if (err.code === 42202) {
          const data = err.data as { sellable_date?: string } | undefined
          this.withdrawal = { batchNo: this.selectedBatch?.batchNo, sellableDate: data?.sellable_date }
          this.errors = [{ field: 'batch_id', message: err.message }]
        } else if (err.fieldErrors.length) {
          this.errors = err.fieldErrors
        } else if (err.code === 42205) {
          this.errors = [{ field: 'quantity', message: err.message }]
        } else {
          ui.toast(err.message || '提交失败，请稍后重试', 'error')
        }
        return { created: null, detail: null }
      } finally {
        this.submitting = false
      }
    },

    close(): void {
      this.open = false
      this.errors = []
      this.withdrawal = null
    }
  }
})

function formQuantityText(value: string | number): string {
  return Number(value || 0).toLocaleString('en-US')
}
