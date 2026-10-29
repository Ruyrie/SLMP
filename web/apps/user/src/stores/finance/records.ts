import { defineStore } from 'pinia'
import type { ApiError, FieldError } from '@zmt/shared'
import { formatMoney, formatNumber, today } from '@zmt/shared'
import { useFarmStore } from '@/stores/farm'
import {
  financeApi,
  newRequestKey,
  type DuplicateCheck,
  type FinanceCategory,
  type FinanceRecord,
  type FinanceRecordDetail,
  type FinanceSummary,
  type RecordLog,
  type SaleOrderBatchOption,
  type SettlementOption
} from '@/api/finance'
import { useUiStore } from '@/stores/ui'
import { currentMonth, shiftMonth, type PageStatus } from '@/stores/finance/shared'

export interface RecordForm {
  recordType: string
  amount: string | number
  categoryId: number
  subcategoryId: number
  note: string
  recordDate: string
  batchId: number
  fileIds: number[]
  version?: number
}

function emptyForm(): RecordForm {
  return {
    recordType: 'EXPENSE',
    amount: '',
    categoryId: 0,
    subcategoryId: 0,
    note: '',
    recordDate: today(),
    batchId: 0,
    fileIds: [],
    version: undefined
  }
}

export const useFinanceRecordsStore = defineStore('financeRecords', {
  state: () => ({
    month: currentMonth(),
    viewAll: false,
    summary: null as FinanceSummary | null,
    summaryStatus: 'loading' as PageStatus,
    summaryError: '',
    query: { page: 1, size: 20, type: 'ALL', keyword: '', categoryIds: '', batchId: 0 },
    list: [] as FinanceRecord[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    totals: {} as { income?: number; expenseAmount?: number },
    status: 'loading' as PageStatus,
    error: '',
    controller: null as AbortController | null,
    categories: [] as FinanceCategory[],
    categoryStatus: 'loading' as PageStatus,
    categoryError: '',
    shareDirection: 'EXPENSE' as 'EXPENSE' | 'INCOME',
    formOpen: false,
    formMode: 'create' as 'create' | 'edit',
    formLoading: false,
    formLoadError: '',
    form: emptyForm(),
    formErrors: [] as FieldError[],
    submitting: false,
    duplicate: null as DuplicateCheck | null,
    duplicateLoading: false,
    batchOptions: [] as SaleOrderBatchOption[],
    endedBatches: [] as SettlementOption[],
    batchLoading: false,
    categoryOpen: false,
    expandedCategoryId: 0,
    newSubName: '',
    subAdding: false,
    subEditingId: 0,
    subEditName: '',
    subSaving: false,
    subDeleteTarget: null as { id: number; name: string; categoryId: number } | null,
    subDeleteBusy: false,
    voidOpen: false,
    voidTarget: null as FinanceRecord | null,
    voidBusy: false,
    logOpen: false,
    logTarget: null as FinanceRecord | null,
    logs: [] as RecordLog[],
    logLoading: false,
    logError: '',
    highlightId: 0,
    editingRecord: null as FinanceRecordDetail | null
  }),
  getters: {
    isFutureMonth: (state) => state.month >= currentMonth(),
    expenseCategories(state): FinanceCategory[] {
      return state.categories.filter((item) => item.direction === 'EXPENSE')
    },
    incomeCategories(state): FinanceCategory[] {
      return state.categories.filter((item) => item.direction === 'INCOME')
    },
    activeCategories(): FinanceCategory[] {
      return this.form.recordType === 'INCOME' ? this.incomeCategories : this.expenseCategories
    },
    currentCategory(state): FinanceCategory | undefined {
      return state.categories.find((item) => item.id === state.form.categoryId)
    },
    subcategories(): { id?: number; name?: string }[] {
      return this.currentCategory?.subcategories || []
    },
    filteredEmpty(state): boolean {
      return state.status === 'filtered_empty'
    },
    shares(): { categoryId?: number; name?: string; amount?: number; shareRate?: number; momRate?: number; comparable?: boolean; icon?: string }[] {
      const summary = this.summary
      if (!summary) return []
      return (this.shareDirection === 'INCOME' ? summary.incomeShares : summary.expenseShares) || []
    },
    shareTotal(): number {
      return Number(this.shareDirection === 'INCOME' ? this.summary?.income || 0 : this.summary?.expenseAmount || 0)
    },
    totalRecords(): number {
      return Number(this.summary?.recordCount || 0)
    },
    autoTabCount(state): number {
      return Number(state.tabCounts.AUTO ?? state.summary?.autoCount ?? 0)
    },
    manualTabCount(state): number {
      return Number(state.tabCounts.MANUAL ?? state.summary?.manualCount ?? 0)
    },
    batchSelectItems(state): { title: string; value: number }[] {
      const active = state.batchOptions.map((batch) => ({
        title: `${batch.batchNo || ''} · ${batch.penName || ''} · 存栏 ${formatNumber(batch.currentCount ?? 0)} ${batch.countUnitText || useFarmStore().countUnit}`,
        value: Number(batch.batchId || 0)
      }))
      const ended = state.endedBatches
        .filter((batch) => batch.batchStatus === 'ENDED')
        .map((batch) => ({
          title: `${batch.batchNo || ''} · ${batch.penName || ''} · 已结束`,
          value: Number(batch.batchId || 0)
        }))
      return [{ title: '不关联', value: 0 }, ...active, ...ended]
    },
    fieldError(state) {
      return (field: string): string =>
        state.formErrors.find((item) => item.field === field || item.field.startsWith(`${field}.`))?.message || ''
    }
  },
  actions: {
    async load(): Promise<void> {
      void this.loadCategories()
      void this.loadBatchOptions()
      await Promise.all([this.loadSummary(), this.loadList()])
    },

    async loadSummary(): Promise<void> {
      this.summaryStatus = this.summary ? this.summaryStatus : 'loading'
      this.summaryError = ''
      try {
        this.summary = await financeApi.monthlySummary(this.month)
        this.summaryStatus = 'ready'
      } catch (e) {
        const err = e as ApiError
        this.summaryStatus = err.isForbidden ? 'forbidden' : 'error'
        this.summaryError = err.message
      }
    },

    async loadList(): Promise<void> {
      this.controller?.abort()
      const controller = new AbortController()
      this.controller = controller
      this.status = this.list.length ? this.status : 'loading'
      this.error = ''
      try {
        const page = await financeApi.records({
          month: this.month,
          type: this.query.type,
          keyword: this.query.keyword || undefined,
          category_ids: this.query.categoryIds || undefined,
          batch_id: this.query.batchId || undefined,
          page: this.query.page,
          size: this.query.size
        })
        if (controller.signal.aborted) return
        this.list = page.list
        this.total = page.total
        this.tabCounts = page.tabCounts
        this.totals = page.totals || {}
        const filtered = this.query.keyword || this.query.type !== 'ALL' || this.query.categoryIds || this.query.batchId
        this.status = this.list.length ? 'ready' : filtered ? 'filtered_empty' : 'empty'
      } catch (e) {
        if ((e as ApiError)?.name === 'CanceledError' || (e as ApiError)?.name === 'AbortError') return
        const err = e as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message
      }
    },

    async loadCategories(): Promise<void> {
      this.categoryStatus = this.categories.length ? this.categoryStatus : 'loading'
      this.categoryError = ''
      try {
        this.categories = await financeApi.categories()
        this.categoryStatus = 'ready'
      } catch (e) {
        const err = e as ApiError
        this.categoryStatus = err.isForbidden ? 'forbidden' : 'error'
        this.categoryError = err.message
      }
    },

    async loadBatchOptions(): Promise<void> {
      if (this.batchLoading) return
      this.batchLoading = true
      try {
        const [active, settlements] = await Promise.all([
          financeApi.batchOptions(),
          financeApi.settlementOptions({ page: 1, size: 100 })
        ])
        this.batchOptions = active
        this.endedBatches = settlements.list
      } catch {
        this.batchOptions = []
      } finally {
        this.batchLoading = false
      }
    },

    setMonth(month: string): void {
      if (!month || month === this.month || month > currentMonth()) return
      this.month = month
      this.query.page = 1
      this.query.categoryIds = ''
      void this.loadSummary()
      void this.loadList()
    },

    prevMonth(): void {
      this.setMonth(shiftMonth(this.month, -1))
    },

    nextMonth(): void {
      if (this.isFutureMonth) return
      this.setMonth(shiftMonth(this.month, 1))
    },

    setType(type: string): void {
      if (this.query.type === type) return
      this.query.type = type
      this.query.page = 1
      void this.loadList()
    },

    setKeyword(keyword: string): void {
      this.query.keyword = keyword
      this.query.page = 1
      void this.loadList()
    },

    filterByCategory(categoryId?: number): void {
      if (!categoryId) return
      const id = String(categoryId)
      this.query.categoryIds = this.query.categoryIds === id ? '' : id
      this.query.page = 1
      void this.loadList()
    },

    clearCategoryFilter(): void {
      this.query.categoryIds = ''
      void this.loadList()
    },

    /** 记一笔抽屉内联添加子类：返回新子类 id 并自动选中 */
    async createSubcategory(categoryId: number, name: string): Promise<number> {
      const ui = useUiStore()
      const trimmed = name.trim()
      if (!trimmed) {
        ui.toast('请输入子类名称', 'error')
        return 0
      }
      if ([...trimmed].length > 10) {
        ui.toast('子类名称不能超过 10 个字', 'error')
        return 0
      }
      try {
        const result = await financeApi.addSubcategory(categoryId, trimmed)
        await this.loadCategories()
        ui.toast(`已添加子类“${trimmed}”`)
        return Number(result.id || 0)
      } catch (e) {
        const err = e as ApiError
        ui.toast(err.fieldErrors[0]?.message || err.message || '添加失败', 'error')
        return 0
      }
    },

    /** 疑似重复横幅“查看”：切到该记录所在月份并高亮 */
    async locateRecord(id?: number, recordDate?: string): Promise<void> {
      if (!id) return
      this.highlightId = id
      const month = (recordDate || today()).slice(0, 7)
      if (this.month !== month) this.month = month
      this.query.type = 'ALL'
      this.query.keyword = ''
      this.query.categoryIds = ''
      this.query.page = 1
      await Promise.all([this.loadSummary(), this.loadList()])
      window.setTimeout(() => {
        const el = document.querySelector(`[data-record-id="${id}"]`)
        el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }, 120)
      window.setTimeout(() => {
        if (this.highlightId === id) this.highlightId = 0
      }, 1800)
    },

    setPage(page: number): void {
      this.query.page = page
      void this.loadList()
    },

    clearFilters(): void {
      this.query.type = 'ALL'
      this.query.keyword = ''
      this.query.categoryIds = ''
      this.query.batchId = 0
      this.query.page = 1
      void this.loadList()
    },

    setSize(size: number): void {
      this.query.size = size
      this.query.page = 1
      void this.loadList()
    },

    setShareDirection(direction: 'EXPENSE' | 'INCOME'): void {
      this.shareDirection = direction
    },

    async openCreate(): Promise<void> {
      this.formMode = 'create'
      this.editingRecord = null
      this.form = emptyForm()
      this.formErrors = []
      this.duplicate = null
      this.formOpen = true
      await this.loadCategories()
    },

    async openEdit(record: FinanceRecord): Promise<void> {
      if (!record.id) return
      this.formMode = 'edit'
      this.formLoadError = ''
      this.formLoading = true
      this.formOpen = true
      this.formErrors = []
      this.duplicate = null
      try {
        const detail = await financeApi.recordDetail(record.id)
        this.editingRecord = detail
        this.form = {
          recordType: detail.recordType || 'EXPENSE',
          amount: Number(detail.amount || 0).toFixed(2),
          categoryId: Number(detail.categoryId || 0),
          subcategoryId: Number(detail.subcategoryId || 0),
          note: detail.note || '',
          recordDate: detail.recordDate || today(),
          batchId: Number(detail.batchId || 0),
          fileIds: (detail.vouchers || []).map((item) => Number(item.fileId || 0)).filter(Boolean),
          version: detail.version
        }
      } catch (e) {
        this.formLoadError = (e as ApiError).message
      } finally {
        this.formLoading = false
      }
    },

    closeForm(): void {
      this.formOpen = false
      this.duplicate = null
      this.formErrors = []
    },

    setRecordType(type: string): void {
      if (this.formMode === 'edit') return
      if (this.form.recordType === type) return
      this.form.recordType = type
      this.form.categoryId = 0
      this.form.subcategoryId = 0
      this.form.note = ''
      this.duplicate = null
    },

    setCategory(categoryId: number): void {
      if (this.form.categoryId === categoryId) return
      this.form.categoryId = categoryId
      this.form.subcategoryId = 0
      void this.checkDuplicate()
    },

    toggleSubcategory(id: number): void {
      this.form.subcategoryId = this.form.subcategoryId === id ? 0 : id
    },

    async checkDuplicate(): Promise<void> {
      const amount = Number(this.form.amount)
      if (!this.form.categoryId || !Number.isFinite(amount) || amount <= 0) {
        this.duplicate = null
        return
      }
      this.duplicateLoading = true
      try {
        const result = await financeApi.duplicateCheck({
          record_type: this.form.recordType,
          category_id: this.form.categoryId,
          amount,
          record_date: this.form.recordDate,
          exclude_id: this.editingRecord?.id
        })
        this.duplicate = result.hit ? result : null
      } catch {
        this.duplicate = null
      } finally {
        this.duplicateLoading = false
      }
    },

    validateForm(): boolean {
      this.formErrors = []
      const amount = Number(this.form.amount)
      if (this.form.amount === '' || this.form.amount === null) {
        this.formErrors.push({ field: 'amount', message: '请输入金额' })
      } else if (!Number.isFinite(amount) || amount <= 0) {
        this.formErrors.push({ field: 'amount', message: '金额必须大于 0' })
      } else if (Math.abs(amount * 100 - Math.round(amount * 100)) > 1e-6) {
        this.formErrors.push({ field: 'amount', message: '金额最多保留 2 位小数' })
      }
      if (!this.form.categoryId) this.formErrors.push({ field: 'category_id', message: '请选择分类' })
      if (!this.form.recordDate) {
        this.formErrors.push({ field: 'record_date', message: '请选择日期' })
      } else if (this.form.recordDate > today()) {
        this.formErrors.push({ field: 'record_date', message: '日期不能晚于今天' })
      }
      if (this.form.recordType === 'INCOME' && this.form.note.length > 30) {
        this.formErrors.push({ field: 'note', message: '说明不能超过 30 字' })
      }
      return this.formErrors.length === 0
    },

    async submitForm(): Promise<boolean> {
      if (this.submitting) return false
      if (!this.validateForm()) return false
      this.submitting = true
      const ui = useUiStore()
      const typeText = this.form.recordType === 'INCOME' ? '收入' : '支出'
      try {
        const payload = {
          record_type: this.form.recordType,
          amount: Number(Number(this.form.amount).toFixed(2)),
          category_id: this.form.categoryId,
          subcategory_id: this.form.subcategoryId || null,
          note: this.form.recordType === 'INCOME' ? this.form.note || undefined : undefined,
          record_date: this.form.recordDate,
          batch_id: this.form.batchId || null,
          file_ids: this.form.fileIds,
          version: this.form.version
        }
        if (this.formMode === 'edit' && this.editingRecord?.id) {
          await financeApi.updateRecord(this.editingRecord.id, payload)
          ui.toast('已保存修改')
        } else {
          await financeApi.createRecord(payload, newRequestKey())
          ui.toast(`已记一笔${typeText} ${formatMoney(Number(this.form.amount))} 元`)
        }
        this.formOpen = false
        this.duplicate = null
        this.query.page = 1
        await Promise.all([this.loadSummary(), this.loadList()])
        return true
      } catch (e) {
        const err = e as ApiError
        if (err.fieldErrors.length) {
          this.formErrors = err.fieldErrors
        } else {
          ui.toast(err.message || '保存失败，请稍后重试', 'error')
        }
        return false
      } finally {
        this.submitting = false
      }
    },

    askVoid(record: FinanceRecord): void {
      this.voidTarget = record
      this.voidOpen = true
    },

    async confirmVoid(): Promise<void> {
      if (!this.voidTarget?.id || this.voidBusy) return
      this.voidBusy = true
      const ui = useUiStore()
      try {
        await financeApi.voidRecord(this.voidTarget.id)
        ui.toast('已作废，不再计入汇总')
        this.voidOpen = false
        this.voidTarget = null
        await Promise.all([this.loadSummary(), this.loadList()])
      } catch (e) {
        ui.toast((e as ApiError).message || '作废失败', 'error')
      } finally {
        this.voidBusy = false
      }
    },

    async openLogs(record: FinanceRecord): Promise<void> {
      if (!record.id) return
      this.logTarget = record
      this.logOpen = true
      this.logs = []
      this.logError = ''
      this.logLoading = true
      try {
        this.logs = await financeApi.recordLogs('FINANCE_RECORD', record.id)
      } catch (e) {
        this.logError = (e as ApiError).message
      } finally {
        this.logLoading = false
      }
    },

    openCategories(): void {
      this.categoryOpen = true
      this.expandedCategoryId = this.expenseCategories[0]?.id || this.categories[0]?.id || 0
      this.newSubName = ''
      this.subEditingId = 0
      void this.loadCategories()
    },

    toggleCategory(id?: number): void {
      if (!id) return
      this.expandedCategoryId = this.expandedCategoryId === id ? 0 : id
      this.newSubName = ''
      this.subEditingId = 0
    },

    async addSubcategory(): Promise<void> {
      const categoryId = this.expandedCategoryId
      const name = this.newSubName.trim()
      if (!categoryId || !name || this.subAdding) return
      const ui = useUiStore()
      if ([...name].length > 10) {
        ui.toast('子类名称不能超过 10 个字', 'error')
        return
      }
      if (this.categories.find((item) => item.id === categoryId)?.subcategories?.some((item) => item.name === name)) {
        ui.toast(`${this.currentCategoryName(categoryId)}下已有“${name}”`, 'error')
        return
      }
      this.subAdding = true
      try {
        await financeApi.addSubcategory(categoryId, name)
        ui.toast(`已添加子类“${name}”`)
        this.newSubName = ''
        await this.loadCategories()
      } catch (e) {
        const err = e as ApiError
        ui.toast(err.fieldErrors[0]?.message || err.message || '添加失败', 'error')
      } finally {
        this.subAdding = false
      }
    },

    currentCategoryName(categoryId: number): string {
      return this.categories.find((item) => item.id === categoryId)?.name || ''
    },

    startRename(id?: number, name?: string): void {
      this.subEditingId = Number(id || 0)
      this.subEditName = name || ''
    },

    cancelRename(): void {
      this.subEditingId = 0
      this.subEditName = ''
    },

    async saveRename(): Promise<void> {
      const name = this.subEditName.trim()
      if (!this.subEditingId || !name || this.subSaving) return
      const ui = useUiStore()
      this.subSaving = true
      try {
        await financeApi.renameSubcategory(this.subEditingId, name)
        ui.toast('已修改子类名称')
        this.cancelRename()
        await this.loadCategories()
      } catch (e) {
        const err = e as ApiError
        ui.toast(err.fieldErrors[0]?.message || err.message || '改名失败', 'error')
      } finally {
        this.subSaving = false
      }
    },

    askDelete(item: { id?: number; name?: string }, categoryId: number): void {
      if (!item.id) return
      this.subDeleteTarget = { id: Number(item.id), name: item.name || '', categoryId }
    },

    async confirmDelete(): Promise<void> {
      if (!this.subDeleteTarget || this.subDeleteBusy) return
      this.subDeleteBusy = true
      const ui = useUiStore()
      try {
        await financeApi.deleteSubcategory(this.subDeleteTarget.id)
        ui.toast(`已删除子类“${this.subDeleteTarget.name}”`)
        this.subDeleteTarget = null
        await this.loadCategories()
      } catch (e) {
        ui.toast((e as ApiError).message || '删除失败', 'error')
      } finally {
        this.subDeleteBusy = false
      }
    },

    reset(): void {
      this.controller?.abort()
      this.controller = null
      this.month = currentMonth()
      this.summary = null
      this.summaryStatus = 'loading'
      this.list = []
      this.total = 0
      this.status = 'loading'
      this.query = { page: 1, size: 20, type: 'ALL', keyword: '', categoryIds: '', batchId: 0 }
      this.tabCounts = {}
      this.totals = {}
    }
  }
})

