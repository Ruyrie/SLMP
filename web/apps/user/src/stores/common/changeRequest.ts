import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  getChangeRequestDetail,
  getChangeRequestForm,
  listChangeRequests,
  submitChangeRequest,
  type ChangeRequestFormField,
  type ChangeRequestFormVO,
  type ChangeRequestVO
} from '@/api/change'
import { useUiStore } from '@/stores/ui'

export type ChangePageStatus =
  | 'loading'
  | 'ready'
  | 'empty'
  | 'filtered_empty'
  | 'error'
  | 'forbidden'

export const VOID_FIELD = '__VOID__'

interface FieldErrors {
  [key: string]: string
}

export interface ChangeQuery {
  page: number
  size: number
  status: string
  farmId: number
  bizType: string
  keyword: string
}

const MSG_PICK_FIELD = '请选择要修改的项目'
const MSG_NEW_VALUE_EMPTY = '请填写新值'
const MSG_SAME_VALUE = '新值与原值相同，不需要提交'
const MSG_REASON_EMPTY = '请填写修改原因'
const MSG_REASON_TOO_LONG = '修改原因不能超过 200 字'
const MSG_PENDING = '这条记录已有一条待审核的修改申请。审核完成前不能再次提交。'
const MSG_CHANGED = '内容已更新，请刷新后再修改'
const MSG_SUBMITTED = '已提交修改申请，等待管理员审核'
const MSG_NO_PERMISSION = '你没有这项操作的权限'

function newIdemKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function sameValue(input: string, original?: string): boolean {
  if (original === undefined || original === null) return false
  const a = Number(input)
  const b = Number(original)
  if (input.trim() !== '' && original.trim() !== '' && Number.isFinite(a) && Number.isFinite(b)) {
    return a === b
  }
  return input === original.trim()
}

export const useChangeRequestStore = defineStore('changeRequest', {
  state: () => ({
    query: {
      page: 1,
      size: 20,
      status: 'ALL',
      farmId: 0,
      bizType: '',
      keyword: ''
    } as ChangeQuery,
    list: [] as ChangeRequestVO[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    status: 'loading' as ChangePageStatus,
    error: '',
    controller: null as AbortController | null,
    detail: null as ChangeRequestVO | null,
    detailForm: null as ChangeRequestFormVO | null,
    detailOpen: false,
    detailLoading: false,
    submitOpen: false,
    form: null as ChangeRequestFormVO | null,
    formLoading: false,
    formError: '',
    selectedField: '',
    newValue: '',
    reason: '',
    fieldErrors: {} as FieldErrors,
    submitting: false,
    dirty: false,
    confirmDiscard: false,
    resubmitOfId: 0,
    sourceDetail: null as ChangeRequestVO | null,
    createBizType: '',
    createBizId: 0,
    pendingWarning: false,
    idemKey: ''
  }),
  getters: {
    hasFilters(state): boolean {
      return (
        state.query.status !== 'ALL' ||
        state.query.farmId > 0 ||
        !!state.query.bizType ||
        !!state.query.keyword
      )
    },
    currentField(state): ChangeRequestFormField | null {
      if (!state.form || state.selectedField === VOID_FIELD) return null
      return (state.form.fields || []).find((field) => field.field_code === state.selectedField) || null
    },
    voidSelected(state): boolean {
      return state.selectedField === VOID_FIELD
    },
    currentRecalcTags(state): string[] {
      if (!state.form) return []
      if (state.selectedField === VOID_FIELD) return state.form.void_recalc_tags || []
      return state.form.recalc_tags?.[state.selectedField] || []
    },
    hasPending(state): boolean {
      return !!state.form?.pending_request_id
    },
    canSubmit(state): boolean {
      return !!state.form && !state.form.pending_request_id && !state.submitting
    },
    submitSubtitle(state): string {
      const farm = state.sourceDetail?.farm_name
      const type = state.form?.biz_type_text || ''
      return [farm, type].filter(Boolean).join(' · ')
    },
    detailStatusText(state): string {
      return state.detail?.status_text || ''
    },
    listEmptyText(state): string {
      if (state.query.status === 'PENDING') return '没有待审核的修改申请'
      if (state.query.status === 'APPROVED') return '没有已通过的修改申请'
      if (state.query.status === 'REJECTED') return '没有已驳回的修改申请'
      return '没有符合条件的修改申请'
    }
  },
  actions: {
    setQuery(partial: Partial<ChangeQuery>, reload = true): void {
      this.query = { ...this.query, ...partial }
      if (reload) void this.fetchList()
    },
    resetFilters(): void {
      this.query.keyword = ''
      this.query.farmId = 0
      this.query.bizType = ''
      this.query.page = 1
      void this.fetchList()
    },
    async fetchList(): Promise<void> {
      this.controller?.abort()
      const controller = new AbortController()
      this.controller = controller
      this.status = 'loading'
      this.error = ''
      try {
        const data = await listChangeRequests(
          {
            page: this.query.page,
            size: this.query.size,
            status: this.query.status === 'ALL' ? undefined : this.query.status,
            farm_id: this.query.farmId || undefined,
            biz_type: this.query.bizType || undefined,
            keyword: this.query.keyword || undefined
          },
          controller.signal
        )
        this.list = data.list || []
        this.total = Number(data.total || 0)
        this.tabCounts = data.tab_counts || {}
        if (!this.list.length) {
          this.status = this.hasFilters || this.query.page > 1 ? 'filtered_empty' : 'empty'
        } else {
          this.status = 'ready'
        }
      } catch (e) {
        if (
          (e as { name?: string }).name === 'CanceledError' ||
          (e as { code?: string }).code === 'ERR_CANCELED'
        ) {
          return
        }
        const err = e as ApiError
        if (err instanceof ApiError && err.isUnauthorized) return
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error =
          err.code === 40303
            ? '员工不能提交或查看修改申请，如需修改请联系所有者或场长。'
            : err.message || '修改申请加载失败，请检查网络后重新加载。'
      }
    },
    async openDetail(id: number): Promise<void> {
      this.detailOpen = true
      this.detailLoading = true
      this.detail = null
      this.detailForm = null
      try {
        const detail = await getChangeRequestDetail(id)
        this.detail = detail
        try {
          this.detailForm = await getChangeRequestForm(detail.biz_type, detail.biz_id)
        } catch {
          this.detailForm = null
        }
      } catch (e) {
        const err = e as ApiError
        useUiStore().toast(err.message || '修改申请加载失败，请稍后重试', 'error')
        this.detailOpen = false
      } finally {
        this.detailLoading = false
      }
    },
    closeDetail(): void {
      this.detailOpen = false
      this.detail = null
      this.detailForm = null
    },
    async openCreate(bizType: string, bizId: number): Promise<void> {
      this.closeDetail()
      this.resetSubmit()
      this.submitOpen = true
      this.sourceDetail = null
      this.resubmitOfId = 0
      this.createBizType = bizType
      this.createBizId = bizId
      await this.loadForm(bizType, bizId)
    },
    async reloadSubmitForm(): Promise<void> {
      if (this.sourceDetail?.biz_type && this.sourceDetail.biz_id) {
        await this.loadForm(this.sourceDetail.biz_type, Number(this.sourceDetail.biz_id))
        return
      }
      if (this.createBizType && this.createBizId) {
        await this.loadForm(this.createBizType, this.createBizId)
      }
    },
    async openResubmit(id: number): Promise<void> {
      this.resetSubmit()
      this.submitOpen = true
      this.formLoading = true
      try {
        const detail = await getChangeRequestDetail(id)
        this.sourceDetail = detail
        this.resubmitOfId = id
        const form = await getChangeRequestForm(detail.biz_type, detail.biz_id)
        this.applyForm(form, true)
        this.selectedField = detail.change_type === 'VOID' ? VOID_FIELD : detail.field_code || ''
        this.newValue = detail.after_value || ''
        this.reason = detail.reason || ''
        this.dirty = true
      } catch (e) {
        const err = e as ApiError
        this.submitOpen = false
        useUiStore().toast(err.message || '重新提交失败，请稍后重试', 'error')
      } finally {
        this.formLoading = false
      }
    },
    async loadForm(bizType: string, bizId: number): Promise<void> {
      this.formLoading = true
      this.formError = ''
      try {
        const form = await getChangeRequestForm(bizType, bizId)
        this.applyForm(form, false)
      } catch (e) {
        const err = e as ApiError
        this.formError = err.message || '表单加载失败，请稍后重试'
        useUiStore().toast(this.formError, 'error')
      } finally {
        this.formLoading = false
      }
    },
    applyForm(form: ChangeRequestFormVO, resubmit: boolean): void {
      this.form = form
      this.idemKey = newIdemKey()
      this.fieldErrors = {}
      const fields = form.fields || []
      if (!resubmit) {
        this.selectedField = fields.length === 1 && !form.void_allowed ? fields[0].field_code : ''
        this.newValue = ''
        this.reason = ''
        this.dirty = false
      }
      this.pendingWarning = !!form.pending_request_id
    },
    selectField(fieldCode: string): void {
      if (this.selectedField === fieldCode) return
      this.selectedField = fieldCode
      this.newValue = ''
      delete this.fieldErrors.field_code
      delete this.fieldErrors.new_value
      this.dirty = true
      this.idemKey = this.idemKey || newIdemKey()
    },
    setNewValue(value: string): void {
      this.newValue = String(value ?? '')
      delete this.fieldErrors.new_value
      this.dirty = true
    },
    setReason(value: string): void {
      this.reason = String(value ?? '').slice(0, 200)
      delete this.fieldErrors.reason
      this.dirty = true
    },
    validate(): FieldErrors {
      const errors: FieldErrors = {}
      if (!this.selectedField) errors.field_code = MSG_PICK_FIELD
      if (this.selectedField && this.selectedField !== VOID_FIELD) {
        const field = this.currentField
        const value = this.newValue.trim()
        if (!value) errors.new_value = MSG_NEW_VALUE_EMPTY
        else if (field) {
          if (sameValue(value, field.value)) errors.new_value = MSG_SAME_VALUE
          if (field.input_type === 'NUMBER') {
            const number = Number(value)
            const unit = field.value_unit ? ` ${field.value_unit}` : ''
            if (!Number.isFinite(number)) {
              errors.new_value = '请输入数字'
            } else if (field.max !== undefined && field.max !== null && number > field.max) {
              errors.new_value = `${field.field_name || '新值'}不能超过 ${field.max}${unit}`
            } else if (field.min !== undefined && field.min !== null && number < field.min) {
              errors.new_value = `${field.field_name || '新值'}不能小于 ${field.min}${unit}`
            }
          }
        }
      }
      const reason = this.reason.trim()
      if (!reason) errors.reason = MSG_REASON_EMPTY
      else if (Array.from(reason).length > 200) errors.reason = MSG_REASON_TOO_LONG
      return errors
    },
    async submit(): Promise<ChangeRequestVO | null> {
      if (!this.form || this.submitting) return null
      if (this.form.pending_request_id) {
        useUiStore().toast(MSG_PENDING, 'error')
        return null
      }
      const errors = this.validate()
      this.fieldErrors = errors
      if (Object.keys(errors).length) return null
      const isVoid = this.selectedField === VOID_FIELD
      if (!this.idemKey) this.idemKey = newIdemKey()
      this.submitting = true
      try {
        const result = await submitChangeRequest(
          {
            biz_type: this.form.biz_type || '',
            biz_id: Number(this.form.biz_id || 0),
            change_type: isVoid ? 'VOID' : 'UPDATE',
            field_code: isVoid ? null : this.selectedField,
            new_value: isVoid ? null : this.newValue.trim(),
            record_version: this.form.record_version || '',
            reason: this.reason.trim(),
            resubmit_of_id: this.resubmitOfId || null
          },
          this.idemKey
        )
        this.submitting = false
        this.idemKey = ''
        this.dirty = false
        this.submitOpen = false
        this.confirmDiscard = false
        this.form = null
        useUiStore().toast(MSG_SUBMITTED)
        await this.fetchList()
        return result
      } catch (e) {
        this.submitting = false
        const err = e as ApiError
        if (err.isNetwork) {
          useUiStore().toast('网络连接失败，已保留填写的内容', 'error')
          return null
        }
        this.idemKey = ''
        this.handleSubmitError(err)
        return null
      }
    },
    handleSubmitError(err: ApiError): void {
      if (err.code === 40001) {
        if (err.fieldErrors.length) {
          for (const item of err.fieldErrors) this.fieldErrors[item.field] = item.message
        } else {
          useUiStore().toast(err.message, 'error')
        }
        return
      }
      if (err.code === 40902 || err.code === 40903) {
        useUiStore().toast(MSG_CHANGED, 'error')
        if (this.form?.biz_type && this.form?.biz_id) {
          void this.loadForm(this.form.biz_type, Number(this.form.biz_id))
        }
        return
      }
      if (err.code === 40906) {
        this.pendingWarning = true
        if (this.form) this.form.pending_request_id = 1
        useUiStore().toast(MSG_PENDING, 'error')
        return
      }
      if (err.code === 40303) {
        useUiStore().toast(MSG_NO_PERMISSION, 'error')
        this.submitOpen = false
        return
      }
      if (err.code === 42207 || err.code === 41001) {
        useUiStore().toast(err.message, 'error')
        this.submitOpen = false
        return
      }
      if (err.code === 40309) {
        useUiStore().toast(err.message, 'error')
        return
      }
      useUiStore().toast(err.message || '提交失败，请稍后重试', 'error')
    },
    requestClose(): void {
      if (this.submitting) return
      if (this.dirty) {
        this.confirmDiscard = true
        return
      }
      this.submitOpen = false
      this.resetSubmit()
    },
    keepEditing(): void {
      this.confirmDiscard = false
    },
    discard(): void {
      this.confirmDiscard = false
      this.submitOpen = false
      this.resetSubmit()
    },
    resetSubmit(): void {
      this.form = null
      this.formLoading = false
      this.formError = ''
      this.selectedField = ''
      this.newValue = ''
      this.reason = ''
      this.fieldErrors = {}
      this.dirty = false
      this.confirmDiscard = false
      this.resubmitOfId = 0
      this.sourceDetail = null
      this.createBizType = ''
      this.createBizId = 0
      this.pendingWarning = false
      this.idemKey = ''
    }
  }
})
