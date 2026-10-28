import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  approveFarmChange,
  getFarmChangeDetail,
  getFarmChanges,
  rejectFarmChange,
  type FarmChangeDetail,
  type FarmChangeItem,
  type FarmChangeStats,
  type FarmChangeStatsRaw
} from '@/api/audit'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'
import { useAuditPendingStore } from '@/stores/audit/pending'

export type AuditLoadStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type FarmChangeTab = 'PENDING' | 'APPROVED' | 'REJECTED'

function emptyStats(): FarmChangeStats {
  return {
    pending_count: 0,
    approved_30d: 0,
    approved_30d_by_item: { NAME: 0, ADDRESS: 0, QUALIFICATION: 0 },
    rejected_30d: 0
  }
}

/** 服务端 list 接口的统计字段既有 camelCase 也有文档中的 snake_case，这里统一。 */
function normalizeStats(raw?: FarmChangeStatsRaw): FarmChangeStats {
  if (!raw) return emptyStats()
  return {
    pending_count: raw.pending_count ?? raw.pendingCount ?? 0,
    earliest_at: raw.earliest_at ?? raw.earliestAt,
    approved_30d: raw.approved_30d ?? raw.approved30d ?? 0,
    approved_30d_by_item:
      raw.approved_30d_by_item ?? raw.approved30d_by_item ?? raw.approved30dByItem ?? { NAME: 0, ADDRESS: 0, QUALIFICATION: 0 },
    rejected_30d: raw.rejected_30d ?? raw.rejected30d ?? 0
  }
}

function isConflict(error: ApiError): boolean {
  return [40401, 40902, 40903, 40905, 41001].includes(error.code)
}

export const useFarmChangeStore = defineStore('farmChange', {
  state: () => ({
    tab: 'PENDING' as FarmChangeTab,
    query: { keyword: '', change_item: '' },
    page: 1,
    size: 20,
    list: [] as FarmChangeItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    stats: emptyStats(),
    status: 'loading' as AuditLoadStatus,
    error: '',
    detail: null as FarmChangeDetail | null,
    detailLoading: false,
    detailError: '',
    drawer: false,
    activeId: 0,
    approveOpen: false,
    approveOpinion: '',
    rejectOpen: false,
    rejectReason: '',
    rejectError: '',
    submitting: false
  }),
  getters: {
    hasFilter: (state) => !!state.query.keyword.trim() || !!state.query.change_item,
    approveTemplate: (state) => state.detail?.approve_template || '变更内容与资质材料一致，同意生效。',
    rejectTemplates: (state) => state.detail?.reject_templates || [],
    approvedBreakdown: (state) => {
      const map = state.stats.approved_30d_by_item || {}
      return `名称 ${map.NAME || 0} · 地址 ${map.ADDRESS || 0} · 资质图 ${map.QUALIFICATION || 0}（一条可含多项）`
    }
  },
  actions: {
    async fetchList(): Promise<void> {
      if (!this.list.length) this.status = 'loading'
      this.error = ''
      try {
        const data = await getFarmChanges({
          status: this.tab,
          keyword: this.query.keyword.trim() || undefined,
          change_item: this.query.change_item || undefined,
          page: this.page,
          size: this.size
        })
        this.list = data.list || []
        this.total = data.total || 0
        this.tabCounts = data.tab_counts || {}
        this.stats = normalizeStats(data.stats)
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
          this.error = error instanceof Error ? error.message : '资料变更列表加载失败'
        }
      }
    },
    setTab(tab: string): void {
      if (this.tab === tab) return
      this.tab = tab as FarmChangeTab
      this.page = 1
      this.list = []
      this.total = 0
      void this.fetchList()
    },
    search(): void {
      this.page = 1
      void this.fetchList()
    },
    resetFilters(): void {
      this.query = { keyword: '', change_item: '' }
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
    async openDetail(row: FarmChangeItem): Promise<void> {
      this.activeId = row.change_id
      this.drawer = true
      await this.loadDetail(row.change_id)
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
        this.detail = await getFarmChangeDetail(id)
      } catch (error) {
        this.detailError = error instanceof Error ? error.message : '资料变更详情加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    openApprove(): void {
      if (!this.detail) return
      this.approveOpinion = this.detail.approve_template || '变更内容与资质材料一致，同意生效。'
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
        const result = await approveFarmChange(this.detail.change_id, {
          audit_opinion: this.approveOpinion.trim(),
          version: this.detail.version
        })
        this.approveOpen = false
        useUiStore().toast(`已通过${result.target_name}的资料变更，新信息已生效`)
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
        const result = await rejectFarmChange(this.detail.change_id, {
          reject_reason: this.rejectReason.trim(),
          version: this.detail.version
        })
        this.rejectOpen = false
        useUiStore().toast(
          `已驳回${result.target_name}的资料变更，原因已发到${(result.notified_names || []).join('、') || '所有者'}的消息中心`
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
