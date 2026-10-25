import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  exportFarms,
  getAdminFarmDetail,
  getAdminFarms,
  setFarmFrozen,
  type AdminFarmDetail,
  type AdminFarmItem,
  type AdminFarmQuery
} from '@/api/system'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

export type FarmLoadStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type FarmStatusAction = 'freeze' | 'unfreeze'

export const useAdminFarmsStore = defineStore('adminFarms', {
  state: () => ({
    query: { status: 'ALL', keyword: '', province_code: '', category_id: null as number | null },
    page: 1,
    size: 20,
    list: [] as AdminFarmItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    status: 'loading' as FarmLoadStatus,
    error: '',
    detail: null as AdminFarmDetail | null,
    detailLoading: false,
    detailError: '',
    drawer: false,
    activeId: 0,
    confirmOpen: false,
    action: 'freeze' as FarmStatusAction,
    target: null as AdminFarmItem | null,
    reason: '',
    reasonError: '',
    smsOwner: false,
    submitting: false,
    exporting: false
  }),
  getters: {
    hasFilter: (state) => !!state.query.keyword.trim() || !!state.query.province_code || state.query.category_id !== null,
    buildQuery(state): AdminFarmQuery {
      return {
        status: state.query.status,
        keyword: state.query.keyword.trim() || undefined,
        province_code: state.query.province_code || undefined,
        category_id: state.query.category_id ?? undefined,
        page: this.page,
        size: this.size
      }
    }
  },
  actions: {
    async fetchList(): Promise<void> {
      if (!this.list.length) this.status = 'loading'
      this.error = ''
      try {
        const data = await getAdminFarms(this.buildQuery)
        this.list = data.list || []
        this.total = data.total || 0
        this.tabCounts = data.tab_counts || {}
        if (this.list.length) this.status = 'ready'
        else if (this.hasFilter || this.query.status !== 'ALL') this.status = 'filtered_empty'
        else this.status = 'empty'
      } catch (error) {
        if (error instanceof ApiError && error.isForbidden) {
          this.status = 'forbidden'
          this.error = error.message
          void useAdminStore().me().catch(() => undefined)
        } else {
          this.status = 'error'
          this.error = error instanceof Error ? error.message : '养殖场列表加载失败'
        }
      }
    },
    applyFilters(): void {
      this.page = 1
      void this.fetchList()
    },
    setStatus(status: string): void {
      if (this.query.status === status) return
      this.query.status = status
      this.page = 1
      this.list = []
      this.total = 0
      void this.fetchList()
    },
    resetFilters(): void {
      this.query = { status: 'ALL', keyword: '', province_code: '', category_id: null }
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
    async openDetail(row: AdminFarmItem): Promise<void> {
      this.activeId = row.farm_id
      this.drawer = true
      await this.loadDetail(row.farm_id)
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
        this.detail = await getAdminFarmDetail(id)
      } catch (error) {
        this.detailError = error instanceof Error ? error.message : '养殖场详情加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    async reloadDetail(): Promise<void> {
      if (!this.activeId) return
      try {
        this.detail = await getAdminFarmDetail(this.activeId)
      } catch {
        /* 忽略 */
      }
    },
    openConfirm(action: FarmStatusAction, row?: AdminFarmItem): void {
      const target =
        row ||
        this.list.find((item) => item.farm_id === (this.detail?.farm_id || this.activeId)) ||
        (this.detail
          ? ({
              farm_id: this.detail.farm_id,
              name: this.detail.name,
              version: this.detail.version,
              member_count: this.detail.summary?.member_total,
              owner_name: this.detail.owner?.name,
              owner_phone: this.detail.owner?.phone
            } as unknown as AdminFarmItem)
          : null)
      if (!target) return
      this.target = target
      this.action = action
      this.reason = ''
      this.reasonError = ''
      this.smsOwner = false
      this.drawer = false
      this.confirmOpen = true
    },
    async confirm(): Promise<void> {
      if (!this.target) return
      if (!this.reason.trim()) {
        this.reasonError = this.action === 'freeze' ? '请填写冻结原因' : '请填写解冻原因'
        return
      }
      this.submitting = true
      const target = this.target
      try {
        const result = await setFarmFrozen(target.farm_id, this.action, {
          reason: this.reason.trim(),
          version: target.version,
          ...(this.action === 'freeze' ? { sms_owner: this.smsOwner } : {})
        })
        if (this.action === 'freeze') {
          const memberCount =
            (result && 'member_count' in result && result.member_count) || target.member_count || 0
          useUiStore().toast(`已冻结${target.name}，原因已发到 ${memberCount} 名成员的消息中心`)
        } else {
          useUiStore().toast(`已解冻${target.name}`)
        }
        this.confirmOpen = false
        await this.fetchList()
        await this.reloadDetail()
      } catch (error) {
        if (error instanceof ApiError && error.code === 40308) {
          useUiStore().toast(error.message, 'error')
          await useAdminStore().me().catch(() => undefined)
        } else if (error instanceof ApiError && [40401, 40902, 40903, 41001, 42207].includes(error.code)) {
          useUiStore().toast(error.message, 'error')
          this.confirmOpen = false
          await this.fetchList()
        } else {
          useUiStore().toast(error instanceof Error ? error.message : '操作失败', 'error')
        }
      } finally {
        this.submitting = false
      }
    },
    async exportList(): Promise<void> {
      this.exporting = true
      try {
        const result = await exportFarms(this.buildQuery)
        useUiStore().toast(`已导出 ${result.row_count} 条记录`)
        const link = document.createElement('a')
        link.href = result.url
        link.rel = 'noopener'
        link.style.display = 'none'
        document.body.appendChild(link)
        link.click()
        link.remove()
      } catch (error) {
        useUiStore().toast(error instanceof Error ? error.message : '导出失败', 'error')
      } finally {
        this.exporting = false
      }
    }
  }
})
