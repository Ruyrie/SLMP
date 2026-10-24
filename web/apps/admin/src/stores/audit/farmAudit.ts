import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  approveFarmAudit,
  getFarmAuditDetail,
  getFarmAudits,
  rejectFarmAudit,
  type FarmAuditDetail,
  type FarmAuditItem
} from '@/api/audit'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'
import { useAuditPendingStore } from '@/stores/audit/pending'

export type AuditLoadStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type FarmAuditTab = 'PENDING' | 'APPROVED' | 'REJECTED'

function isConflict(error: ApiError): boolean {
  return [40401, 40902, 40903, 41001].includes(error.code)
}

export const useFarmAuditStore = defineStore('farmAudit', {
  state: () => ({
    tab: 'PENDING' as FarmAuditTab,
    query: { keyword: '', submit_type: '' },
    page: 1,
    size: 20,
    list: [] as FarmAuditItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    overdueCount: 0,
    overdueNames: [] as string[],
    status: 'loading' as AuditLoadStatus,
    error: '',
    detail: null as FarmAuditDetail | null,
    detailLoading: false,
    detailError: '',
    drawer: false,
    activeId: 0,
    approveOpen: false,
    approveOpinion: '',
    rejectOpen: false,
    rejectReason: '',
    rejectError: '',
    reshootIds: [] as number[],
    submitting: false
  }),
  getters: {
    hasFilter: (state) => !!state.query.keyword.trim() || !!state.query.submit_type,
    isPending: (state) => state.tab === 'PENDING',
    approveTemplate: (state) => state.detail?.approve_template || '资质图清晰完整，地图位置与地址一致。',
    rejectTemplates: (state) => state.detail?.reject_templates || [],
    canHandle: (state) => state.detail?.status === 'PENDING'
  },
  actions: {
    async fetchList(): Promise<void> {
      if (!this.list.length) this.status = 'loading'
      this.error = ''
      try {
        const data = await getFarmAudits({
          status: this.tab,
          keyword: this.query.keyword.trim() || undefined,
          submit_type: this.query.submit_type || undefined,
          page: this.page,
          size: this.size
        })
        this.list = data.list || []
        this.total = data.total || 0
        this.tabCounts = data.tab_counts || {}
        this.overdueCount = data.overdue_count || 0
        this.overdueNames = data.overdue_farm_names || []
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
          this.error = error instanceof Error ? error.message : '申请列表加载失败'
        }
      }
    },
    setTab(tab: string): void {
      if (this.tab === tab) return
      this.tab = tab as FarmAuditTab
      this.page = 1
      this.list = []
      this.total = 0
      void this.fetchList()
    },
    resetFilters(): void {
      this.query = { keyword: '', submit_type: '' }
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
    async openDetail(row: FarmAuditItem): Promise<void> {
      this.activeId = row.audit_id
      this.drawer = true
      await this.loadDetail(row.audit_id)
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
      try {
        this.detail = await getFarmAuditDetail(id)
      } catch (error) {
        this.detailError = error instanceof Error ? error.message : '申请详情加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    openApprove(): void {
      if (!this.detail) return
      this.approveOpinion = this.detail.approve_template || '资质图清晰完整，地图位置与地址一致。'
      this.drawer = false
      this.approveOpen = true
    },
    openReject(): void {
      if (!this.detail) return
      this.rejectReason = ''
      this.rejectError = ''
      this.reshootIds = []
      this.drawer = false
      this.rejectOpen = true
    },
    toggleReshoot(id: number): void {
      this.reshootIds = this.reshootIds.includes(id)
        ? this.reshootIds.filter((item) => item !== id)
        : [...this.reshootIds, id]
    },
    useReason(text: string): void {
      if (!text) return
      if (this.rejectReason.includes(text)) return
      this.rejectReason = this.rejectReason ? `${this.rejectReason}\n${text}` : text
      this.rejectError = ''
    },
    async approve(): Promise<void> {
      if (!this.detail) return
      if (!this.approveOpinion.trim()) {
        useUiStore().toast('请填写审核意见', 'error')
        return
      }
      this.submitting = true
      try {
        const result = await approveFarmAudit(this.detail.audit_id, {
          audit_opinion: this.approveOpinion.trim(),
          version: this.detail.version
        })
        this.approveOpen = false
        useUiStore().toast(
          `已通过${result.target_name}的注册申请，结果已发到${(result.notified_names || []).join('、') || '所有者'}的消息中心`
        )
        await this.afterAction()
      } catch (error) {
        await this.handleActionError(error)
      } finally {
        this.submitting = false
      }
    },
    async reject(): Promise<void> {
      if (!this.detail) return
      if (!this.rejectReason.trim()) {
        this.rejectError = '请填写驳回原因'
        return
      }
      this.submitting = true
      try {
        const result = await rejectFarmAudit(this.detail.audit_id, {
          reject_reason: this.rejectReason.trim(),
          reshoot_file_ids: this.reshootIds,
          version: this.detail.version
        })
        this.rejectOpen = false
        useUiStore().toast(
          `已驳回${result.target_name}的注册申请，原因已发到${(result.notified_names || []).join('、') || '所有者'}的消息中心`
        )
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
        this.approveOpen = false
        this.rejectOpen = false
        await this.afterAction()
      }
    }
  }
})
