import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  approveRecovery,
  getRecoveries,
  getRecoveryDetail,
  rejectRecovery,
  type RecoveryDetail,
  type RecoveryItem
} from '@/api/audit'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'
import { useAuditPendingStore } from '@/stores/audit/pending'

export type AuditLoadStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type RecoveryTab = 'PENDING' | 'HANDLED'

function isConflict(error: ApiError): boolean {
  return [40401, 40902, 40903, 40904, 41001].includes(error.code)
}

export const useRecoveryStore = defineStore('recovery', {
  state: () => ({
    tab: 'PENDING' as RecoveryTab,
    keyword: '',
    page: 1,
    size: 20,
    list: [] as RecoveryItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    status: 'loading' as AuditLoadStatus,
    error: '',
    detail: null as RecoveryDetail | null,
    detailLoading: false,
    detailError: '',
    drawer: false,
    activeId: 0,
    verifyMethod: 'CALLBACK_NEW_PHONE',
    handleNote: '',
    noteError: '',
    confirmOpen: false,
    confirmAction: 'approve' as 'approve' | 'reject',
    submitting: false
  }),
  getters: {
    hasFilter: (state) => !!state.keyword.trim(),
    isPending: (state) => state.tab === 'PENDING',
    verifyMethods: (state) =>
      state.detail?.verify_methods?.length
        ? state.detail.verify_methods
        : [
            { value: 'CALLBACK_NEW_PHONE', label: '回拨新手机号，本人确认' },
            { value: 'CONTACT_FARM_OWNER', label: '联系养殖场所有者确认身份' },
            { value: 'OTHER', label: '其他方式' }
          ],
    afterCancelDate: (state) => state.detail?.after_actions?.cancel_forbidden_until || ''
  },
  actions: {
    async fetchList(): Promise<void> {
      if (!this.list.length) this.status = 'loading'
      this.error = ''
      try {
        const data = await getRecoveries({
          tab: this.tab,
          keyword: this.keyword.trim() || undefined,
          page: this.page,
          size: this.size
        })
        this.list = data.list || []
        this.total = data.total || 0
        this.tabCounts = data.tab_counts || {}
        if (this.list.length) this.status = 'ready'
        else if (this.hasFilter) this.status = 'filtered_empty'
        else this.status = 'empty'
      } catch (error) {
        if (error instanceof ApiError && error.isForbidden) {
          this.status = 'forbidden'
          this.error = error.message
          void useAdminStore().me().catch(() => undefined)
        } else {
          this.status = 'error'
          this.error = error instanceof Error ? error.message : '找回申请列表加载失败'
        }
      }
    },
    setTab(tab: string): void {
      if (this.tab === tab) return
      this.tab = tab as RecoveryTab
      this.page = 1
      this.list = []
      this.total = 0
      void this.fetchList()
    },
    resetFilters(): void {
      this.keyword = ''
      this.page = 1
      void this.fetchList()
    },
    changePage(page: number): void {
      this.page = page
      void this.fetchList()
    },
    changeSize(size: number): void {
      this.size = size
      this.page = 1
      void this.fetchList()
    },
    async openDetail(row: RecoveryItem): Promise<void> {
      this.activeId = row.id
      this.drawer = true
      await this.loadDetail(row.id)
    },
    async openDetailById(id: number): Promise<void> {
      this.activeId = id
      this.drawer = true
      await this.loadDetail(id)
    },
    async loadDetail(id: number): Promise<void> {
      this.detailLoading = true
      this.detailError = ''
      this.detail = null
      this.handleNote = ''
      this.noteError = ''
      try {
        this.detail = await getRecoveryDetail(id)
        this.verifyMethod =
          this.detail.verify_method || this.detail.verify_methods?.[0]?.value || 'CALLBACK_NEW_PHONE'
        this.handleNote = ''
      } catch (error) {
        this.detailError = error instanceof Error ? error.message : '找回申请详情加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    openConfirm(action: 'approve' | 'reject'): void {
      if (!this.detail) return
      if (!this.handleNote.trim()) {
        this.noteError = action === 'approve' ? '请填写处理说明' : '请填写驳回原因'
        return
      }
      this.noteError = ''
      this.confirmAction = action
      this.drawer = false
      this.confirmOpen = true
    },
    async confirm(): Promise<void> {
      if (!this.detail) return
      if (!this.verifyMethod) {
        useUiStore().toast('请选择核实方式', 'error')
        return
      }
      if (!this.handleNote.trim()) {
        this.noteError = this.confirmAction === 'approve' ? '请填写处理说明' : '请填写驳回原因'
        return
      }
      this.submitting = true
      const payload = {
        verify_method: this.verifyMethod,
        handle_note: this.handleNote.trim(),
        version: this.detail.version
      }
      try {
        if (this.confirmAction === 'approve') {
          const result = await approveRecovery(this.detail.id, payload)
          useUiStore().toast(
            `已通过${result.target_name}的找回申请，临时密码已发到${result.sms_phone || this.detail.new_phone}`
          )
        } else {
          const result = await rejectRecovery(this.detail.id, payload)
          useUiStore().toast(`已驳回，原因已以短信发到${result.sms_phone || this.detail.new_phone}`)
        }
        this.confirmOpen = false
        await this.afterAction()
      } catch (error) {
        await this.handleActionError(error)
      } finally {
        this.submitting = false
      }
    },
    async afterAction(): Promise<void> {
      this.drawer = false
      if (this.list.length === 1 && this.page > 1) this.page -= 1
      await Promise.all([this.fetchList(), useAuditPendingStore().refresh()])
    },
    async handleActionError(error: unknown): Promise<void> {
      if (!(error instanceof ApiError)) {
        useUiStore().toast(error instanceof Error ? error.message : '操作失败', 'error')
        return
      }
      if (error.code === 40308) {
        useUiStore().toast(error.message, 'error')
        await useAdminStore().me().catch(() => undefined)
        return
      }
      useUiStore().toast(error.message, 'error')
      if (isConflict(error)) {
        this.confirmOpen = false
        await this.afterAction()
      }
    }
  }
})
