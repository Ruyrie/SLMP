import { defineStore } from 'pinia'
import { ApiError, formatDateTime } from '@zmt/shared'
import type { PenFormCategory, PenFormOptions, PenStockItem, PenDetail, RecordLog } from '@/api/herd'
import { herdApi, type PenCreatePayload, type PenUpdatePayload } from '@/api/herd'
import { useUiStore } from '@/stores/ui'
import {
  errorText,
  fieldErrorsOf,
  penStatusText,
  statusForError,
  todayISO,
  type FormMode,
  type PageStatus
} from './shared'

interface PenFormState {
  penId: number
  penCode: string
  penName: string
  penType: string
  categoryId: number
  capacity: number | null
  version: number
}

export interface PenActionTarget {
  pen_id?: number
  pen_name?: string
}

const emptyForm = (): PenFormState => ({
  penId: 0,
  penCode: '',
  penName: '',
  penType: '',
  categoryId: 0,
  capacity: null,
  version: 0
})

export const usePenListStore = defineStore('herdPenList', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    pens: [] as PenStockItem[],
    query: { page: 1, size: 20, keyword: '', penType: '', displayStatus: 'ALL' },
    details: {} as Record<number, PenDetail>,
    loadingDetails: [] as number[],
    detailFailures: [] as number[],
    formOptions: null as PenFormOptions | null,
    loadingOptions: false,
    formDrawer: false,
    formMode: 'create' as FormMode,
    form: emptyForm(),
    formErrors: {} as Record<string, string>,
    submitting: false,
    detailDrawer: false,
    detail: null as PenDetail | null,
    detailLoading: false,
    detailError: '',
    detailRequestId: 0,
    penLogs: [] as RecordLog[],
    logsLoading: false,
    logsError: '',
    logsRequestId: 0,
    disableDialog: false,
    disablePen: null as PenActionTarget | null,
    statusBusy: false,
    loaded: false,
    requestId: 0
  }),
  getters: {
    filteredPens(state): PenStockItem[] {
      const keyword = state.query.keyword.trim().toLowerCase()
      return state.pens.filter((pen) => {
        if (state.query.penType && pen.pen_type !== state.query.penType) return false
        if (state.query.displayStatus !== 'ALL' && pen.display_status !== state.query.displayStatus) return false
        if (!keyword) return true
        return `${pen.pen_name || ''} ${pen.pen_code || ''}`.toLowerCase().includes(keyword)
      })
    },
    pagedPens(): PenStockItem[] {
      const from = (this.query.page - 1) * this.query.size
      return this.filteredPens.slice(from, from + this.query.size)
    },
    total(): number {
      return this.filteredPens.length
    },
    summary(state): { inUse: number; empty: number; disabled: number; capacity: number } {
      let inUse = 0
      let empty = 0
      let disabled = 0
      let capacity = 0
      for (const pen of state.pens) {
        if (pen.display_status === 'DISABLED') disabled += 1
        else if (pen.display_status === 'EMPTY') empty += 1
        else inUse += 1
        capacity += Number(pen.capacity || 0)
      }
      return { inUse, empty, disabled, capacity }
    },
    filteredEmpty(): boolean {
      return this.status === 'ready' && this.total === 0 && (!!this.query.keyword || !!this.query.penType || this.query.displayStatus !== 'ALL')
    },
    statusItems(): Array<{ title: string; value: string }> {
      return [
        { title: '全部状态', value: 'ALL' },
        { title: '在用', value: 'IN_USE' },
        { title: '空栏', value: 'EMPTY' },
        { title: '已停用', value: 'DISABLED' }
      ]
    },
    typeItems(state): Array<{ title: string; value: string }> {
      return [{ title: '全部类型', value: '' }, ...(state.formOptions?.pen_types || []).map((item) => ({
        title: item.label || String(item.value),
        value: String(item.value)
      }))]
    },
    categoryItems(state): Array<{ title: string; value: number; unit: string }> {
      return (state.formOptions?.categories || []).map((item) => ({
        title: item.category_name || '',
        value: Number(item.category_id || 0),
        unit: item.count_unit_text || '头'
      }))
    },
    formUnit(): string {
      const category = this.formOptions?.categories?.find((item) => Number(item.category_id) === Number(this.form.categoryId))
      return category?.count_unit_text || '头'
    },
    formTitle(): string {
      return this.formMode === 'create' ? '新增栏舍' : '编辑栏舍'
    },
    disableText(): string {
      return this.disablePen ? `停用 ${this.disablePen.pen_name}？` : '停用栏舍？'
    },
    staffOf(): (penId?: number) => string {
      return (penId?: number): string => {
        const detail = penId ? this.details[penId] : undefined
        if (!detail?.staff?.length) return ''
        return detail.staff.map((item) => item.name).filter(Boolean).join('、')
      }
    },
    logItems(state): Array<{ title: string; time: string; desc: string; tone?: string; icon?: string }> {
      return state.penLogs.map((log) => {
        const action = log.op_action || ''
        let title = log.op_action_text || '修改栏舍信息'
        if (!log.changes?.length) {
          if (action === 'CREATE') title = '新增栏舍'
          else if (action === 'DISABLE') title = '停用栏舍'
          else if (action === 'ENABLE') title = '启用栏舍'
        }
        return {
          title: log.changes?.length
            ? log.changes.map((change) => `${change.field_name || '字段'}：${change.before || '—'} → ${change.after || '—'}`).join('；')
            : title,
          time: formatDateTime(log.created_at),
          desc: log.operator?.name || '',
          tone: action === 'CREATE' ? 'neutral' : 'primary',
          icon: action === 'CREATE' ? 'add' : action === 'DISABLE' ? 'block' : action === 'ENABLE' ? 'restart_alt' : 'edit'
        }
      })
    }
  },
  actions: {
    async load(force = false): Promise<void> {
      if (this.loaded && !force) {
        void this.ensurePageDetails()
        return
      }
      const requestId = ++this.requestId
      this.status = this.pens.length ? this.status : 'loading'
      this.error = ''
      try {
        const page = await herdApi.stockPens({ page: 1, size: 200, sort: 'CODE' })
        if (requestId !== this.requestId) return
        this.pens = page.list || []
        this.loaded = true
        this.status = this.pens.length ? 'ready' : 'empty'
        if (this.query.page > Math.max(1, Math.ceil(this.total / this.query.size))) this.query.page = 1
        await this.ensurePageDetails()
      } catch (error) {
        if (requestId !== this.requestId) return
        this.status = statusForError(error)
        this.error = errorText(error, '栏舍加载失败，请稍后重新加载')
      }
    },
    async ensurePageDetails(): Promise<void> {
      const missing = this.pagedPens.map((pen) => Number(pen.pen_id)).filter((id): id is number => !!id && !this.details[id] && !this.loadingDetails.includes(id) && !this.detailFailures.includes(id))
      if (!missing.length) return
      this.loadingDetails.push(...missing)
      await Promise.allSettled(missing.map(async (id) => {
        try {
          const detail = await herdApi.penDetail(id)
          this.details[id] = detail
          this.detailFailures = this.detailFailures.filter((item) => item !== id)
        } catch {
          if (!this.detailFailures.includes(id)) this.detailFailures.push(id)
          /* 列表已能展示，负责员工加载失败不阻塞整页 */
        } finally {
          this.loadingDetails = this.loadingDetails.filter((item) => item !== id)
        }
      }))
    },
    async loadFormOptions(): Promise<void> {
      if (this.formOptions || this.loadingOptions) return
      this.loadingOptions = true
      try {
        this.formOptions = await herdApi.penFormOptions()
      } catch (error) {
        useUiStore().toast(errorText(error, '栏舍表单选项加载失败'), 'error')
      } finally {
        this.loadingOptions = false
      }
    },
    async openCreate(): Promise<void> {
      await this.loadFormOptions()
      this.formMode = 'create'
      this.form = emptyForm()
      this.form.penType = String(this.formOptions?.pen_types?.[0]?.value || '')
      this.form.categoryId = Number(this.formOptions?.categories?.[0]?.category_id || 0)
      this.formErrors = {}
      this.formDrawer = true
    },
    async openEdit(penId: number): Promise<void> {
      await this.loadFormOptions()
      let detail = this.details[penId]
      if (!detail) {
        try {
          detail = await herdApi.penDetail(penId)
          this.details[penId] = detail
        } catch (error) {
          useUiStore().toast(errorText(error, '栏舍信息加载失败'), 'error')
          return
        }
      }
      this.formMode = 'edit'
      this.form = {
        penId: Number(detail.pen_id || penId),
        penCode: detail.pen_code || '',
        penName: detail.pen_name || '',
        penType: detail.pen_type || '',
        categoryId: Number(detail.category_id || 0),
        capacity: detail.capacity ?? null,
        version: Number(detail.version || 0)
      }
      this.formErrors = {}
      // Temporary drawers share the same edge; the detail otherwise covers the editor.
      this.detailDrawer = false
      this.formDrawer = true
    },
    validateForm(): boolean {
      this.formErrors = {}
      const code = this.form.penCode.trim().toUpperCase()
      if (this.formMode === 'create') {
        if (!code) this.formErrors.pen_code = '请填写栏舍编号'
        else if (!/^[A-Za-z0-9-]{1,10}$/.test(code)) this.formErrors.pen_code = '栏舍编号最多 10 位，只能包含字母、数字和短横线'
      }
      if (!this.form.penName.trim()) this.formErrors.pen_name = '请填写栏舍名称'
      else if ([...this.form.penName.trim()].length > 20) this.formErrors.pen_name = '栏舍名称最多 20 个字'
      if (!this.form.penType) this.formErrors.pen_type = '请选择栏舍类型'
      if (!this.form.categoryId) this.formErrors.category_id = '请选择适用品类'
      if (this.form.capacity === null || this.form.capacity === undefined || !Number.isInteger(Number(this.form.capacity)) || Number(this.form.capacity) < 1) {
        this.formErrors.capacity = '容量需为大于 0 的整数'
      }
      return Object.keys(this.formErrors).length === 0
    },
    async saveForm(): Promise<boolean> {
      if (this.submitting || !this.validateForm()) return false
      this.submitting = true
      try {
        if (this.formMode === 'create') {
          const payload: PenCreatePayload = {
            pen_code: this.form.penCode.trim().toUpperCase(),
            pen_name: this.form.penName.trim(),
            pen_type: this.form.penType,
            category_id: this.form.categoryId,
            capacity: Number(this.form.capacity)
          }
          const detail = await herdApi.createPen(payload)
          this.details[Number(detail.pen_id)] = detail
          this.formDrawer = false
          useUiStore().toast(`已新增${detail.pen_name || '栏舍'}`)
        } else {
          const payload: PenUpdatePayload = {
            pen_name: this.form.penName.trim(),
            pen_type: this.form.penType,
            category_id: this.form.categoryId,
            capacity: Number(this.form.capacity),
            version: this.form.version
          }
          const detail = await herdApi.updatePen(this.form.penId, payload)
          this.details[Number(detail.pen_id)] = detail
          this.formDrawer = false
          useUiStore().toast('已保存栏舍信息')
        }
        await this.load(true)
        if (this.detail) await this.refreshDetail(Number(this.detail.pen_id))
        return true
      } catch (error) {
        if (error instanceof ApiError) {
          const fields = fieldErrorsOf(error)
          if (Object.keys(fields).length) this.formErrors = { ...fields }
          if (error.code === 40902 || error.code === 40903) {
            useUiStore().toast(error.message || '内容已更新，请刷新后再修改', 'error')
          } else if (!Object.keys(fields).length) {
            useUiStore().toast(error.message, 'error')
          }
        } else {
          useUiStore().toast('保存失败，请稍后重试', 'error')
        }
        return false
      } finally {
        this.submitting = false
      }
    },
    async openDetail(penId: number): Promise<void> {
      const requestId = ++this.detailRequestId
      ++this.logsRequestId
      this.detailDrawer = true
      this.detail = null
      this.detailError = ''
      this.detailLoading = true
      this.penLogs = []
      this.logsError = ''
      this.logsLoading = false
      try {
        const detail = await herdApi.penDetail(penId)
        if (requestId !== this.detailRequestId) return
        this.detail = detail
        this.details[penId] = detail
        void this.loadPenLogs(penId)
      } catch (error) {
        if (requestId !== this.detailRequestId) return
        this.detail = null
        this.detailError = errorText(error, '栏舍详情加载失败')
      } finally {
        if (requestId === this.detailRequestId) this.detailLoading = false
      }
    },
    async refreshDetail(penId: number): Promise<void> {
      const requestId = this.detailRequestId
      try {
        const detail = await herdApi.penDetail(penId)
        this.details[penId] = detail
        if (requestId === this.detailRequestId && this.detail?.pen_id === penId) {
          this.detail = detail
          await this.loadPenLogs(penId)
        }
      } catch {
        /* 保持旧值 */
      }
    },
    async loadPenLogs(penId: number): Promise<void> {
      const requestId = ++this.logsRequestId
      this.logsLoading = true
      this.logsError = ''
      try {
        const logs = await herdApi.recordLogs('PEN', penId)
        if (requestId !== this.logsRequestId || this.detail?.pen_id !== penId) return
        this.penLogs = logs
      } catch (error) {
        if (requestId !== this.logsRequestId || this.detail?.pen_id !== penId) return
        this.penLogs = []
        this.logsError = errorText(error, '修改记录加载失败，请重试')
      } finally {
        if (requestId === this.logsRequestId) this.logsLoading = false
      }
    },
    askDisable(pen: PenActionTarget): void {
      this.disablePen = pen
      this.disableDialog = true
    },
    async confirmDisable(): Promise<void> {
      if (!this.disablePen || this.statusBusy) return
      const id = Number(this.disablePen.pen_id)
      this.statusBusy = true
      try {
        const detail = await this.getDetailForAction(id)
        const updated = await herdApi.disablePen(id, Number(detail.version || 0))
        this.details[id] = updated
        if (this.detail?.pen_id === id) {
          this.detail = updated
          await this.loadPenLogs(id)
        }
        this.disableDialog = false
        useUiStore().toast(`已停用${updated.pen_name || this.disablePen.pen_name || '栏舍'}`)
        await this.load(true)
      } catch (error) {
        if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast('栏舍状态已变化，列表已刷新', 'error')
          await this.load(true)
        } else {
          useUiStore().toast(errorText(error, '停用栏舍失败'), 'error')
        }
      } finally {
        this.statusBusy = false
      }
    },
    async enablePen(pen: PenActionTarget): Promise<void> {
      if (!pen.pen_id || this.statusBusy) return
      this.statusBusy = true
      try {
        const detail = await this.getDetailForAction(Number(pen.pen_id))
        const updated = await herdApi.enablePen(Number(pen.pen_id), Number(detail.version || 0))
        this.details[Number(pen.pen_id)] = updated
        if (this.detail?.pen_id === updated.pen_id) {
          this.detail = updated
          await this.loadPenLogs(Number(updated.pen_id))
        }
        useUiStore().toast(`已启用${updated.pen_name || pen.pen_name || '栏舍'}`)
        await this.load(true)
      } catch (error) {
        if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast('栏舍状态已变化，列表已刷新', 'error')
          await this.load(true)
        } else {
          useUiStore().toast(errorText(error, '启用栏舍失败'), 'error')
        }
      } finally {
        this.statusBusy = false
      }
    },
    async getDetailForAction(penId: number): Promise<PenDetail> {
      const existing = this.details[penId]
      if (existing) return existing
      const detail = await herdApi.penDetail(penId)
      this.details[penId] = detail
      return detail
    },
    setPage(page: number): void {
      this.query.page = Math.max(1, page)
      void this.ensurePageDetails()
    },
    setSize(size: number): void {
      this.query.size = Math.max(1, size)
      this.query.page = 1
      void this.ensurePageDetails()
    },
    applyFilter(): void {
      this.query.page = 1
      void this.ensurePageDetails()
    },
    resetFilters(): void {
      this.query.keyword = ''
      this.query.penType = ''
      this.query.displayStatus = 'ALL'
      this.query.page = 1
      void this.ensurePageDetails()
    },
    rowStatus(pen: PenStockItem): string {
      return penStatusText(pen)
    },
    isDisableVisible(pen: PenStockItem): boolean {
      return pen.display_status === 'EMPTY' && !pen.is_breeding_pen
    },
    isEnableVisible(pen: PenStockItem): boolean {
      return pen.display_status === 'DISABLED'
    },
    reset(): void {
      this.$reset()
    }
  }
})

export type { PenFormCategory }
