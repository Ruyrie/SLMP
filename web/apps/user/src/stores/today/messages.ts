import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  acceptFarmInvite,
  deleteMessage,
  fetchMessageDetail,
  fetchMessages,
  fetchTodoDetail,
  openMessage,
  readAllMessages,
  rejectFarmInvite,
  restoreMessage
} from '@/api/today'
import type { MessageBriefVO, MessageDetailVO, TodoDetailVO } from '@/api/today'
import { useFarmStore } from '@/stores/farm'
import { farmApi } from '@/api/farm'
import { useNoticeStore } from '@/stores/notice'

export type MessageListStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type DetailStatus = 'idle' | 'loading' | 'ready' | 'error'

function errorState(error: unknown): { status: MessageListStatus; message: string } {
  const apiError = error as ApiError
  return {
    status: apiError?.isForbidden ? 'forbidden' : 'error',
    message: apiError?.message || '消息加载失败，请检查网络后重试'
  }
}

function isCancel(error: unknown): boolean {
  return (error as { name?: string })?.name === 'CanceledError'
}

export const useMessagesStore = defineStore('todayMessages', {
  state: () => ({
    query: {
      page: 1,
      size: 20,
      category: '',
      farmId: null as number | null,
      unreadOnly: false
    },
    list: [] as MessageBriefVO[],
    total: 0,
    filteredUnread: 0,
    hasMore: false,
    status: 'loading' as MessageListStatus,
    error: '',
    loadingMore: false,
    refreshing: false,
    selectedId: null as number | null,
    detail: null as MessageDetailVO | null,
    detailStatus: 'idle' as DetailStatus,
    detailError: '',
    todoDetail: null as TodoDetailVO | null,
    todoStatus: 'idle' as DetailStatus,
    todoError: '',
    reminderOpen: false,
    rejectOpen: false,
    rejectMessage: null as MessageBriefVO | null,
    acceptingId: null as number | null,
    controller: null as AbortController | null,
    detailController: null as AbortController | null
  }),
  getters: {
    filtered(state): boolean {
      return !!state.query.category || !!state.query.farmId || state.query.unreadOnly
    },
    groups(state): { title: string; items: MessageBriefVO[] }[] {
      const labels: Record<string, string> = { TODAY: '今天', YESTERDAY: '昨天', EARLIER: '更早' }
      const groups: { title: string; items: MessageBriefVO[] }[] = []
      for (const message of state.list) {
        const title = labels[message.date_group || 'EARLIER'] || '更早'
        const last = groups[groups.length - 1]
        if (last && last.title === title) last.items.push(message)
        else groups.push({ title, items: [message] })
      }
      return groups
    },
    selectedBrief(state): MessageBriefVO | null {
      return state.list.find((item) => item.message_id === state.selectedId) || null
    }
  },
  actions: {
    async fetchList(reset = true): Promise<void> {
      this.controller?.abort()
      const controller = new AbortController()
      this.controller = controller
      if (reset) {
        this.query.page = 1
        this.status = 'loading'
        this.list = []
      } else {
        this.loadingMore = true
      }
      this.error = ''
      try {
        const data = await fetchMessages(
          {
            page: this.query.page,
            size: this.query.size,
            category: this.query.category || undefined,
            farm_id: this.query.farmId || undefined,
            unread_only: this.query.unreadOnly || undefined
          },
          controller.signal
        )
        if (this.controller !== controller) return
        const incoming = data.list || []
        this.list = reset ? incoming : [...this.list, ...incoming]
        this.total = data.total || 0
        this.filteredUnread = data.filtered_unread || 0
        this.hasMore = !!data.has_more
        this.status = this.list.length ? 'ready' : this.filtered ? 'filtered_empty' : 'empty'
      } catch (error) {
        if (isCancel(error) || this.controller !== controller) return
        const state = errorState(error)
        this.status = state.status
        this.error = state.message
      } finally {
        if (this.controller === controller) this.loadingMore = false
      }
    },
    async loadMore(): Promise<void> {
      if (!this.hasMore || this.loadingMore || this.status === 'loading') return
      this.query.page += 1
      await this.fetchList(false)
    },
    /** 页面停留时同步撤回状态；保留当前分页和详情，避免轮询造成闪烁。 */
    async refreshVisible(): Promise<void> {
      if (this.refreshing || this.loadingMore || this.status === 'loading') return
      this.refreshing = true
      const controller = this.controller
      const selectedId = this.selectedId
      const previousDetail = this.detail
      try {
        const params = {
          size: this.query.size,
          category: this.query.category || undefined,
          farm_id: this.query.farmId || undefined,
          unread_only: this.query.unreadOnly || undefined
        }
        const [pages, detail] = await Promise.all([
          Promise.all(Array.from({ length: this.query.page }, (_, index) =>
            fetchMessages({ ...params, page: index + 1 }, controller?.signal))),
          selectedId && previousDetail?.msg_type === 'NOTICE'
            ? fetchMessageDetail(selectedId)
            : Promise.resolve(null)
        ])
        if (this.controller !== controller) return
        const lastPage = pages[pages.length - 1]
        if (lastPage) {
          this.list = pages.flatMap((page) => page.list || [])
          this.total = lastPage.total || 0
          this.filteredUnread = lastPage.filtered_unread || 0
          this.hasMore = !!lastPage.has_more
          this.status = this.list.length ? 'ready' : this.filtered ? 'filtered_empty' : 'empty'
        }
        if (detail && this.selectedId === selectedId && this.detail === previousDetail) {
          this.detail = detail
        }
      } catch {
        /* 后台同步失败保留当前内容，用户仍可使用重新加载。 */
      } finally {
        this.refreshing = false
      }
    },
    setCategory(category: string): void {
      if (this.query.category === category) return
      this.query.category = category
      this.clearSelection()
      void this.fetchList(true)
    },
    setFarm(farmId: number | null): void {
      if (this.query.farmId === farmId) return
      this.query.farmId = farmId
      this.clearSelection()
      void this.fetchList(true)
    },
    setUnreadOnly(unreadOnly: boolean): void {
      if (this.query.unreadOnly === unreadOnly) return
      this.query.unreadOnly = unreadOnly
      this.clearSelection()
      void this.fetchList(true)
    },
    clearFilters(): void {
      this.query.category = ''
      this.query.farmId = null
      this.query.unreadOnly = false
      this.clearSelection()
      void this.fetchList(true)
    },
    clearSelection(): void {
      this.detailController?.abort()
      this.selectedId = null
      this.detail = null
      this.detailStatus = 'idle'
      this.detailError = ''
      this.todoDetail = null
      this.todoStatus = 'idle'
    },
    async select(id: number): Promise<void> {
      this.selectedId = id
      await this.loadDetail(id)
    },
    /** 从推送或外部链接打开消息：先调 T15 标记打开，再取详情。 */
    async openFromPush(id: number): Promise<void> {
      try {
        await openMessage(id)
      } catch {
        /* 打开失败时仍尝试取详情，由详情给出失效或无权说明 */
      }
      this.selectedId = id
      await this.loadDetail(id)
    },
    async loadDetail(id: number): Promise<void> {
      this.detailController?.abort()
      const controller = new AbortController()
      this.detailController = controller
      this.detailStatus = 'loading'
      this.detailError = ''
      this.detail = null
      this.todoDetail = null
      this.todoStatus = 'idle'
      try {
        const detail = await fetchMessageDetail(id)
        if (this.detailController !== controller || this.selectedId !== id) return
        this.detail = detail
        this.detailStatus = 'ready'
        const brief = this.list.find((item) => item.message_id === id)
        if (brief && !brief.read) {
          brief.read = true
          this.filteredUnread = Math.max(0, this.filteredUnread - 1)
        } else if (brief) {
          brief.read = true
        }
        void useNoticeStore().load()
        if (detail.category === 'TODO' && detail.todo_id) {
          void this.loadTodoDetail(detail.todo_id)
        }
      } catch (error) {
        if (isCancel(error) || this.detailController !== controller || this.selectedId !== id) return
        this.detailStatus = 'error'
        this.detailError = error instanceof ApiError ? error.message : '消息加载失败，请检查网络后重试'
      }
    },
    async loadTodoDetail(todoId: number): Promise<void> {
      this.todoStatus = 'loading'
      this.todoError = ''
      try {
        this.todoDetail = await fetchTodoDetail(todoId)
        this.todoStatus = 'ready'
      } catch (error) {
        this.todoDetail = null
        this.todoStatus = 'error'
        this.todoError = error instanceof ApiError ? error.message : '待办加载失败'
      }
    },
    async readAll(): Promise<number> {
      const result = await readAllMessages({
        category: this.query.category || undefined,
        farm_id: this.query.farmId || undefined
      })
      for (const item of this.list) item.read = true
      if (this.detail) this.detail.read = true
      this.filteredUnread = 0
      void useNoticeStore().load()
      if (this.query.unreadOnly) {
        this.clearSelection()
        await this.fetchList(true)
      }
      return result.updated_count || 0
    },
    async remove(id: number): Promise<void> {
      const index = this.list.findIndex((item) => item.message_id === id)
      await deleteMessage(id)
      this.list = this.list.filter((item) => item.message_id !== id)
      this.total = Math.max(0, this.total - 1)
      if (this.selectedId === id) {
        const next = this.list[index] || this.list[index - 1] || this.list[0]
        if (next?.message_id) await this.select(next.message_id)
        else this.clearSelection()
      }
      void useNoticeStore().load()
    },
    async restore(id: number): Promise<void> {
      await restoreMessage(id)
      await this.fetchList(true)
      await this.select(id)
      void useNoticeStore().load()
    },
    async acceptInvite(inviteId: number, farmName?: string): Promise<string> {
      this.acceptingId = inviteId
      try {
        const result = await acceptFarmInvite(inviteId)
        await useFarmStore().load(true)
        void useNoticeStore().load()
        await this.fetchList(true)
        if (this.selectedId) await this.loadDetail(this.selectedId)
        this.markInviteStatus(inviteId, 'ACCEPTED')
        return result.farm_name || farmName || ''
      } finally {
        this.acceptingId = null
      }
    },
    async rejectInvite(inviteId: number): Promise<void> {
      await rejectFarmInvite(inviteId)
      void useNoticeStore().load()
      await this.fetchList(true)
      if (this.selectedId) await this.loadDetail(this.selectedId)
      this.markInviteStatus(inviteId, 'REJECTED')
    },
    /** 所有权转让：受让人接受后成为所有者，刷新养殖场列表与角色。 */
    async acceptTransfer(transferId: number, farmName?: string): Promise<string> {
      this.acceptingId = transferId
      try {
        const result = await farmApi.acceptTransfer(transferId)
        await useFarmStore().load(true)
        void useNoticeStore().load()
        await this.fetchList(true)
        if (this.selectedId) await this.loadDetail(this.selectedId)
        this.markInviteStatus(transferId, 'ACCEPTED')
        return result?.farm_name || farmName || ''
      } finally {
        this.acceptingId = null
      }
    },
    async rejectTransfer(transferId: number): Promise<void> {
      await farmApi.rejectTransfer(transferId)
      void useNoticeStore().load()
      await this.fetchList(true)
      if (this.selectedId) await this.loadDetail(this.selectedId)
      this.markInviteStatus(transferId, 'REJECTED')
    },
    /** 服务端邀请动作状态未回写消息快照时，本地按处理结果更新，避免按钮重复出现。 */
    markInviteStatus(inviteId: number, status: 'ACCEPTED' | 'REJECTED'): void {
      const patch = (action?: MessageBriefVO['action']): void => {
        if (!action || Number(action.action_id) !== inviteId) return
        action.status = status
        action.status_text = status === 'ACCEPTED' ? '已同意' : '已拒绝'
        action.can_accept = false
      }
      for (const item of this.list) {
        patch(item.action)
        if (Number(item.action?.action_id) === inviteId) item.icon_state = 'HANDLED'
      }
      if (this.detail) {
        patch(this.detail.action)
        if (Number(this.detail.action?.action_id) === inviteId) this.detail.icon_state = 'HANDLED'
      }
    },
    openReject(message: MessageBriefVO): void {
      this.rejectMessage = message
      this.rejectOpen = true
    }
  }
})
