import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  fetchFeedFlows,
  fetchFeedOverview,
  fetchFeeds,
  type FeedFlowGroup,
  type FeedStockItem,
  type FeedStockOverview,
  type StockFlowItem
} from '@/api/stock'
import type { PageStatus } from '@/api/stock'

export const useFeedStockStore = defineStore('feedStock', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    overview: null as FeedStockOverview | null,
    list: [] as FeedStockItem[],
    total: 0,
    page: 1,
    size: 20,
    loaded: false,
    flowsOpen: false,
    flowsFeedId: 0,
    flowsFeedName: '',
    flowsGroup: 'ALL' as FeedFlowGroup,
    flows: [] as StockFlowItem[],
    flowsTotal: 0,
    flowsPage: 1,
    flowsSize: 20,
    flowsLoading: false
  }),
  getters: {
    hasFeed(state): boolean {
      return state.list.length > 0
    },
    canOtherOut(state): boolean {
      return state.list.some((item) => Number(item.stock?.value || 0) > 0)
    },
    banner(state): FeedStockOverview['banner'] {
      return state.overview?.banner || null
    },
    warningFeeds(state): FeedStockItem[] {
      return state.list.filter((item) => item.low_stock)
    },
    feedNames(state): string {
      return state.list.map((item) => item.name).join('、')
    },
    usageFoot(state): string {
      return (state.overview?.usage_7d_items || [])
        .map((item) => `${item.feed_name} ${formatQty(item.qty)}`)
        .join(' · ')
    }
  },
  actions: {
    async load(): Promise<void> {
      this.error = ''
      this.status = this.list.length ? this.status : 'loading'
      try {
        const [overview, feeds] = await Promise.all([
          fetchFeedOverview(),
          fetchFeeds({ page: this.page, size: this.size })
        ])
        this.overview = overview
        this.list = feeds.list || []
        this.total = feeds.total || 0
        this.status = this.list.length ? 'ready' : 'empty'
        this.loaded = true
      } catch (error) {
        const err = error as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message || '饲料库存加载失败'
      }
    },
    async setPage(page: number): Promise<void> {
      this.page = page
      await this.load()
    },
    async setSize(size: number): Promise<void> {
      this.size = size
      this.page = 1
      await this.load()
    },
    async refresh(): Promise<void> {
      await this.load()
    },
    async openFlows(feedId = 0, feedName = ''): Promise<void> {
      this.flowsOpen = true
      this.flowsFeedId = feedId
      this.flowsFeedName = feedName
      this.flowsGroup = 'ALL'
      this.flowsPage = 1
      await this.loadFlows()
    },
    async loadFlows(): Promise<void> {
      this.flowsLoading = true
      try {
        const data = await fetchFeedFlows({
          feed_id: this.flowsFeedId || undefined,
          flow_group: this.flowsGroup,
          page: this.flowsPage,
          size: this.flowsSize
        })
        this.flows = data.list || []
        this.flowsTotal = data.total || 0
      } catch (error) {
        this.flows = []
        this.flowsTotal = 0
        this.error = (error as ApiError).message
      } finally {
        this.flowsLoading = false
      }
    },
    async setFlowsGroup(group: FeedFlowGroup): Promise<void> {
      this.flowsGroup = group
      this.flowsPage = 1
      await this.loadFlows()
    },
    async setFlowsPage(page: number): Promise<void> {
      this.flowsPage = page
      await this.loadFlows()
    },
    async setFlowsSize(size: number): Promise<void> {
      this.flowsSize = size
      this.flowsPage = 1
      await this.loadFlows()
    }
  }
})

function formatQty(value?: number | null): string {
  return `${Number(value || 0).toLocaleString('en-US')} 千克`
}
