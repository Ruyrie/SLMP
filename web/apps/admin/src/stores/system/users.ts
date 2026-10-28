import { defineStore } from 'pinia'
import { ApiError, TZ, dayjs } from '@zmt/shared'
import {
  exportUsers,
  getUserDetail,
  getUsers,
  setUserEnabled,
  type AdminUserDetail,
  type AdminUserItem,
  type AdminUserQuery
} from '@/api/system'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

export type UserLoadStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type UserStatusAction = 'disable' | 'enable'

function rangeDates(registered: string): { from?: string; to?: string } {
  const now = dayjs().tz(TZ)
  if (registered === 'MONTH') return { from: now.startOf('month').format('YYYY-MM-DD'), to: now.format('YYYY-MM-DD') }
  if (registered === 'QUARTER') return { from: now.subtract(3, 'month').format('YYYY-MM-DD'), to: now.format('YYYY-MM-DD') }
  if (registered === 'YEAR') return { from: now.startOf('year').format('YYYY-MM-DD'), to: now.format('YYYY-MM-DD') }
  return {}
}

export const useUsersStore = defineStore('users', {
  state: () => ({
    query: { status: 'ALL', keyword: '', role: '', registered: 'ALL' },
    page: 1,
    size: 20,
    list: [] as AdminUserItem[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    status: 'loading' as UserLoadStatus,
    error: '',
    detail: null as AdminUserDetail | null,
    detailLoading: false,
    detailError: '',
    drawer: false,
    activeId: 0,
    confirmOpen: false,
    action: 'disable' as UserStatusAction,
    target: null as AdminUserItem | null,
    reason: '',
    reasonError: '',
    notifyMessage: true,
    submitting: false,
    exporting: false
  }),
  getters: {
    hasFilter: (state) =>
      !!state.query.keyword.trim() || !!state.query.role || state.query.registered !== 'ALL',
    isDisabled: (state) => state.detail?.status === 'DISABLED'
  },
  actions: {
    buildQuery(): AdminUserQuery {
      const range = rangeDates(this.query.registered)
      return {
        status: this.query.status,
        keyword: this.query.keyword.trim() || undefined,
        role: this.query.role || undefined,
        registered_from: range.from,
        registered_to: range.to,
        page: this.page,
        size: this.size
      }
    },
    async fetchList(): Promise<void> {
      if (!this.list.length) this.status = 'loading'
      this.error = ''
      try {
        const data = await getUsers(this.buildQuery())
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
          this.error = error instanceof Error ? error.message : '账号列表加载失败'
        }
      }
    },
    setStatus(status: string): void {
      if (this.query.status === status) return
      this.query.status = status
      this.page = 1
      this.list = []
      this.total = 0
      void this.fetchList()
    },
    applyFilters(): void {
      this.page = 1
      void this.fetchList()
    },
    resetFilters(): void {
      this.query = { status: 'ALL', keyword: '', role: '', registered: 'ALL' }
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
    async openDetail(row: AdminUserItem): Promise<void> {
      this.activeId = row.user_id
      this.drawer = true
      await this.loadDetail(row.user_id)
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
        this.detail = await getUserDetail(id)
      } catch (error) {
        this.detailError = error instanceof Error ? error.message : '账号详情加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    async reloadDetail(): Promise<void> {
      if (!this.activeId) return
      const id = this.activeId
      try {
        this.detail = await getUserDetail(id)
      } catch {
        /* 忽略 */
      }
    },
    openStatus(action: UserStatusAction): void {
      const detail = this.detail
      if (!detail) return
      const target =
        this.list.find((item) => item.user_id === detail.user_id) ||
        ({
          user_id: detail.user_id,
          name: detail.name,
          phone: detail.phone,
          farm_names_text: (detail.farms || []).map((farm) => farm.farm_name).join('、'),
          farm_count: detail.farms?.length || 0,
          registered_at: detail.registered_at || '',
          status: detail.status,
          status_text: detail.status_text,
          version: detail.version
        } as AdminUserItem)
      this.target = target
      this.action = action
      this.reason = ''
      this.reasonError = ''
      this.notifyMessage = true
      this.drawer = false
      this.confirmOpen = true
    },
    openStatusFromRow(row: AdminUserItem, action: UserStatusAction): void {
      this.target = row
      this.action = action
      this.reason = ''
      this.reasonError = ''
      this.notifyMessage = true
      this.confirmOpen = true
    },
    async confirmStatus(): Promise<void> {
      if (!this.target) return
      if (!this.reason.trim()) {
        this.reasonError = this.action === 'disable' ? '请填写停用原因' : '请填写启用原因'
        return
      }
      this.submitting = true
      try {
        await setUserEnabled(this.target.user_id, this.action, {
          reason: this.reason.trim(),
          version: this.target.version,
          ...(this.action === 'disable' ? { notify_message: this.notifyMessage } : {})
        })
        useUiStore().toast(
          this.action === 'disable' ? `已停用${this.target.name}的账号` : `已启用${this.target.name}的账号`
        )
        this.confirmOpen = false
        await this.fetchList()
        await this.reloadDetail()
      } catch (error) {
        if (error instanceof ApiError && error.code === 40308) {
          useUiStore().toast(error.message, 'error')
          await useAdminStore().me().catch(() => undefined)
        } else if (error instanceof ApiError && [40401, 40902, 40903].includes(error.code)) {
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
        const result = await exportUsers(this.buildQuery())
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
