import { defineStore } from 'pinia'
import { appConfig } from '@zmt/shared'
import { http } from '@/api/client'

interface UnreadCount {
  total_unread?: number
  filtered_unread?: number
  by_category?: Record<string, number>
}

export const useNoticeStore = defineStore('notice', {
  state: () => ({
    unread: 0,
    byCategory: {} as Record<string, number>,
    timer: 0 as number | ReturnType<typeof setInterval>,
    started: false
  }),
  actions: {
    async load(): Promise<void> {
      try {
        const data = await http().get<UnreadCount>('/messages/unread-count')
        this.unread = data.total_unread || 0
        this.byCategory = data.by_category || {}
      } catch {
        /* 轮询失败静默 */
      }
    },
    start(): void {
      if (this.started) return
      this.started = true
      void this.load()
      this.timer = window.setInterval(() => void this.load(), appConfig.pollInterval)
      document.addEventListener('visibilitychange', this.onVisible)
    },
    onVisible(): void {
      if (document.visibilityState === 'visible') void this.load()
    },
    stop(): void {
      this.started = false
      if (this.timer) window.clearInterval(this.timer)
      this.timer = 0
      document.removeEventListener('visibilitychange', this.onVisible)
    },
    reset(): void {
      this.unread = 0
      this.byCategory = {}
    }
  }
})
