import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  approveChangeRequest,
  getChangeRequestDetail,
  getChangeRequests,
  rejectChangeRequest,
  type ChangeRequestDetail,
  type ChangeRequestItem
} from '@/api/audit'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'
import { useAuditPendingStore } from '@/stores/audit/pending'

export type AuditLoadStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type ChangeRequestTab = 'PENDING' | 'APPROVED' | 'REJECTED'

function isConflict(error: ApiError): boolean {
  return [40401, 40902, 40903, 41001, 42201, 42205].includes(error.code)
}

export const useChangeRequestStore = defineStore('changeRequest', {
  state: () => ({
    tab: 'PENDING' as ChangeRequestTab,
    query: { keyword: '', source_module: '', impact: '' },
    page: 1,
    size: 20,
    list: [] as ChangeRequestItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    sellableDateCount: 0,
    status: 'loading' as AuditLoadStatus,
    error: '',
    detail: null as ChangeRequestDetail | null,
    detailLoading: false,
    detailError: '',
    drawer: false,
    activeId: 0,
    approveOpen: false,
    approveComment: '',
    rejectOpen: false,
    rejectReason: '',
    rejectError: '',
    submitting: false
  }),
  getters: {
    hasFilter: (state) => !!state.query.keyword.trim() || !!state.query.source_module || !!state.query.impact,
    blocked: (state) => state.detail?.validation?.level === 'BLOCK',
    warnValidation: (state) => state.detail?.validation?.level === 'WARN',
    approveTemplate: () => '修改原因充分，新值与原始单据一致，同意修改。',
    rejectTemplates: () => [
      '与原始单据不一致',
      '修改原因不充分',
      '新值超出业务范围',
      '可由养殖户直接修改，无需申请'
    ]
  },
  actions: {
    async fetchList(): Promise<void> {
      if (!this.list.length) this.status = 'loading'
      this.error = ''
      try {
        const data = await getChangeRequests({
          status: this.tab,
          keyword: this.query.keyword.trim() || undefined,
          source_module: this.query.source_module || undefined,
          impact: this.query.impact || undefined,
          page: this.page,
          size: this.size
        })
        this.list = data.list || []
        this.total = data.total || 0
        this.tabCounts = data.tab_counts || {}
        this.sellableDateCount = data.sellable_date_count || 0
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
          this.error = error instanceof Error ? error.message : '修改申请列表加载失败'
        }
      }
    },
    setTab(tab: string): void {
      if (this.tab === tab) return
      this.tab = tab as ChangeRequestTab
      this.page = 1
      this.list = []
      this.total = 0
      void this.fetchList()
    },
    resetFilters(): void {
      this.query = { keyword: '', source_module: '', impact: '' }
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
    async openDetail(row: ChangeRequestItem): Promise<void> {
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
      try {
        this.detail = await getChangeRequestDetail(id)
      } catch (error) {
        this.detailError = error instanceof Error ? error.message : '修改申请详情加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    openApprove(): void {
      if (!this.detail) return
      this.approveComment = '修改原因充分，新值与原始单据一致，同意修改。'
      this.drawer = false
      this.approveOpen = true
    },
    openReject(): void {
      if (!this.detail) return
      this.rejectReason = ''
      this.rejectError = ''
      this.drawer = false
      this.rejectOpen = true
    },
    useReason(text: string): void {
      if (!text) return
      this.rejectReason = text
      this.rejectError = ''
    },
    async approve(): Promise<void> {
      if (!this.detail) return
      if (this.blocked) {
        useUiStore().toast('原记录已变化，这条申请不能通过，请驳回并说明原因', 'error')
        return
      }
      if (!this.approveComment.trim()) {
        useUiStore().toast('请填写审核意见', 'error')
        return
      }
      this.submitting = true
      try {
        const result = await approveChangeRequest(this.detail.id, {
          review_comment: this.approveComment.trim(),
          version: this.detail.version
        })
        this.approveOpen = false
        const recalc = result.recalc_count || 0
        useUiStore().toast(
          recalc
            ? `已通过，系统已重新计算 ${recalc} 项数据，结果已发到${(result.notified_names || []).join('、') || '提交人'}的消息中心`
            : `已通过，结果已发到${(result.notified_names || []).join('、') || '提交人'}的消息中心`
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
        const result = await rejectChangeRequest(this.detail.id, {
          reject_reason: this.rejectReason.trim(),
          version: this.detail.version
        })
        this.rejectOpen = false
        useUiStore().toast(
          `已驳回，原因已发到${(result.notified_names || []).join('、') || '提交人'}的消息中心`
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
