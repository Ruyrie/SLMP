import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import type { BatchDetail, TimelineItem } from '@/api/herd'
import { herdApi } from '@/api/herd'
import { useUiStore } from '@/stores/ui'
import { errorText, type PageStatus } from './shared'

export type BatchDetailStatus = 'loading' | 'ready' | 'forbidden' | 'not_found' | 'deleted' | 'error'
export type TimelineFilter = 'ALL' | 'FEED' | 'WEIGH' | 'IMMUNE' | 'DRUG' | 'INSPECT' | 'STOCK'

export const useBatchDetailStore = defineStore('herdBatchDetail', {
  state: () => ({
    status: 'loading' as BatchDetailStatus,
    error: '',
    batch: null as BatchDetail | null,
    batchId: 0,
    timeline: [] as TimelineItem[],
    timelineStatus: 'loading' as PageStatus,
    timelineError: '',
    timelinePage: 1,
    timelineSize: 20,
    timelineTotal: 0,
    timelineFilter: 'ALL' as TimelineFilter,
    loadingMore: false,
    expanded: [] as number[],
    loaded: false,
    requestId: 0
  }),
  getters: {
    hasMore(state): boolean {
      return state.timeline.length < state.timelineTotal
    },
    isStaffView(state): boolean {
      return state.batch?.actions?.can_transfer === false && state.batch?.actions?.can_submit_change_request === false
    },
    timelineEmptyText(state): string {
      const map: Record<TimelineFilter, string> = {
        ALL: '这个批次还没有生产记录',
        FEED: '还没有投喂记录',
        WEIGH: '还没有称重记录',
        IMMUNE: '还没有免疫记录',
        DRUG: '还没有用药记录',
        INSPECT: '还没有巡检记录',
        STOCK: '还没有存栏变动记录'
      }
      return map[state.timelineFilter]
    },
    canTransfer(state): boolean {
      return !!state.batch?.actions?.can_transfer
    },
    canRegisterDeath(state): boolean {
      return !!state.batch?.actions?.can_register_death
    },
    canRequestChange(state): boolean {
      return !!state.batch?.actions?.can_submit_change_request
    }
  },
  actions: {
    async load(batchId: number, force = false): Promise<void> {
      const numericId = Number(batchId)
      if (!numericId) {
        this.status = 'not_found'
        this.error = '批次不存在或无权查看'
        return
      }
      if (this.loaded && this.batchId === numericId && !force) return
      const requestId = ++this.requestId
      this.status = 'loading'
      this.error = ''
      this.batchId = numericId
      try {
        const batch = await herdApi.batchDetail(numericId)
        if (requestId !== this.requestId) return
        this.batch = batch
        this.loaded = true
        this.status = 'ready'
        this.timeline = []
        this.timelinePage = 1
        this.timelineTotal = 0
        this.expanded = []
        await this.loadTimeline(true)
      } catch (error) {
        if (requestId !== this.requestId) return
        this.batch = null
        if (error instanceof ApiError) {
          if (error.code === 41001) this.status = 'deleted'
          else if (error.code === 40401) this.status = 'not_found'
          else if (error.isForbidden) this.status = 'forbidden'
          else this.status = 'error'
          this.error = error.message
        } else {
          this.status = 'error'
          this.error = errorText(error)
        }
      }
    },
    async loadTimeline(reset = false): Promise<void> {
      if (!this.batchId) return
      if (reset) {
        this.timelinePage = 1
        this.timeline = []
        this.timelineStatus = 'loading'
      } else {
        this.loadingMore = true
      }
      this.timelineError = ''
      try {
        const data = await herdApi.batchTimeline(this.batchId, {
          page: this.timelinePage,
          size: this.timelineSize,
          type: this.timelineFilter
        })
        this.timeline = reset ? (data.list || []) : [...this.timeline, ...(data.list || [])]
        this.timelineTotal = Number(data.total || 0)
        this.timelineStatus = this.timeline.length ? 'ready' : 'empty'
      } catch (error) {
        this.timelineError = errorText(error, '生产记录加载失败')
        this.timelineStatus = this.timeline.length ? 'ready' : 'error'
        if (!reset) useUiStore().toast(this.timelineError, 'error')
      } finally {
        this.loadingMore = false
      }
    },
    setFilter(filter: TimelineFilter): void {
      if (this.timelineFilter === filter) return
      this.timelineFilter = filter
      void this.loadTimeline(true)
    },
    async loadMore(): Promise<void> {
      if (this.loadingMore || !this.hasMore) return
      const nextPage = this.timelinePage + 1
      this.timelinePage = nextPage
      await this.loadTimeline(false)
      if (this.timelineStatus === 'error') this.timelinePage = nextPage - 1
    },
    toggleExpanded(id?: number): void {
      if (!id) return
      const index = this.expanded.indexOf(id)
      if (index >= 0) this.expanded.splice(index, 1)
      else this.expanded.push(id)
    },
    isExpanded(id?: number): boolean {
      return !!id && this.expanded.includes(id)
    },
    reset(): void {
      this.$reset()
    }
  }
})
