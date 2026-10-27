import { defineStore } from 'pinia'
import { getAdminMessages, readAdminMessages, type AdminMessage, type MessageFilter } from '@/api/messages'
import { useAdminStore } from './admin'

export const useAdminMessagesStore = defineStore('admin-messages', {
  state: () => ({
    open: false,
    list: [] as AdminMessage[],
    filter: 'ALL' as MessageFilter,
    page: 1,
    size: 20,
    total: 0,
    allCount: 0,
    loading: false,
    busy: false,
    error: '',
    operationError: '',
    requestId: 0,
    session: 0
  }),
  actions: {
    async show(): Promise<void> {
      this.open = true
      this.filter = 'ALL'
      this.operationError = ''
      await this.load(1)
    },
    close(): void {
      this.open = false
      this.requestId++
      this.loading = false
    },
    reset(): void {
      const session = this.session + 1
      const requestId = this.requestId + 1
      this.$reset()
      this.session = session
      this.requestId = requestId
    },
    async setFilter(filter: MessageFilter): Promise<void> {
      this.filter = filter
      await this.load(1)
    },
    async load(page?: number): Promise<void> {
      page ??= this.page
      const requestId = ++this.requestId
      this.page = page
      this.loading = true
      this.error = ''
      const admin = useAdminStore()
      const unreadRevision = admin.unreadRevision
      try {
        const data = await getAdminMessages(this.filter, page, this.size)
        if (requestId !== this.requestId || !this.open) return
        this.list = data.list
        this.total = data.total
        this.allCount = data.tab_counts?.all ?? data.total
        if (data.tab_counts?.unread !== undefined && unreadRevision === admin.unreadRevision) {
          admin.setUnread(data.tab_counts.unread)
        }
        // An unread page may disappear after the last item on it is read elsewhere.
        const lastPage = Math.max(1, Math.ceil(data.total / this.size))
        if (!data.list.length && page > lastPage) {
          await this.load(lastPage)
        }
      } catch {
        if (requestId === this.requestId && this.open) this.error = '消息加载失败，请重试'
      } finally {
        if (requestId === this.requestId) this.loading = false
      }
    },
    async markRead(message?: AdminMessage): Promise<boolean> {
      if (this.busy) return false
      if (message?.read) return true
      const session = this.session
      this.busy = true
      this.operationError = ''
      try {
        const result = await readAdminMessages(message?.message_id)
        if (session !== this.session) return false
        const admin = useAdminStore()
        admin.setUnread(admin.unread - result.updated_count)
        for (const row of this.list) {
          if (!message || row.message_id === message.message_id) row.read = true
        }
        if (this.open) await this.load(this.filter === 'UNREAD' ? 1 : this.page)
        return session === this.session
      } catch {
        if (session === this.session) this.operationError = '标记已读失败，请重试'
        return false
      } finally {
        if (session === this.session) this.busy = false
      }
    }
  }
})
