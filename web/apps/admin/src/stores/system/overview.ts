import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  getFarmMap,
  getOverview,
  getOverviewTrend,
  getRegionDetail,
  type FarmMapCluster,
  type FarmMapData,
  type OverviewData,
  type RegionDetail,
  type TrendData
} from '@/api/system'
import { useAdminStore } from '@/stores/admin'

export type OverviewStatus = 'loading' | 'ready' | 'empty' | 'error' | 'forbidden'

/** 地图聚合点返回的是 150000 这类省级编码，地区明细接口按 2/4/6 位前缀查询。 */
export function regionPrefix(code: string): string {
  if (!code) return ''
  if (code.endsWith('0000')) return code.slice(0, 2)
  if (code.endsWith('00')) return code.slice(0, 4)
  return code
}

// 并发请求序号：切换品类 / 刷新时只接受最后一次请求的结果，避免旧响应把新筛选覆盖掉。
let loadSeq = 0
let mapSeq = 0
let regionSeq = 0
let trendSeq = 0

export const useOverviewStore = defineStore('overview', {
  state: () => ({
    status: 'loading' as OverviewStatus,
    error: '',
    refreshing: false,
    data: null as OverviewData | null,
    map: null as FarmMapData | null,
    mapLoading: false,
    mapError: '',
    categoryId: null as number | null,
    region: null as RegionDetail | null,
    regionOpen: false,
    regionLoading: false,
    regionError: '',
    trendMetric: 'ACTIVE_USER',
    trend: null as TrendData | null,
    trendLoading: false,
    trendError: '',
    trendCache: {} as Record<string, TrendData>
  }),
  getters: {
    pendingTotal: (state) => state.data?.pending?.total || 0,
    overdueTotal: (state) => state.data?.pending?.overdue_count || 0,
    failedBlocks: (state) => state.data?.failed_blocks || [],
    /** 所有品类（含 0 个已通过养殖场的品类），供地图筛选下拉使用。 */
    categoryOptions: (state) => state.data?.category_dist || [],
    selectedCategory: (state) => (state.categoryId ? state.data?.category_dist?.find((item) => item.category_id === state.categoryId) || null : null)
  },
  actions: {
    async load(): Promise<void> {
      const token = ++loadSeq
      if (!this.data) this.status = 'loading'
      this.error = ''
      try {
        const data = await getOverview()
        if (token !== loadSeq) return
        this.data = data
        this.status = 'ready'
      } catch (error) {
        if (token !== loadSeq) return
        if (error instanceof ApiError && error.isForbidden) {
          this.status = 'forbidden'
          this.error = error.message
          void useAdminStore().me().catch(() => undefined)
        } else {
          this.status = 'error'
          this.error = error instanceof Error ? error.message : '平台概览加载失败'
        }
        return
      }
      await Promise.allSettled([this.fetchMap(), this.fetchTrend(this.trendMetric)])
    },
    async refresh(): Promise<void> {
      this.refreshing = true
      try {
        await this.load()
      } finally {
        this.refreshing = false
      }
    },
    async fetchMap(): Promise<void> {
      const token = ++mapSeq
      this.mapLoading = true
      this.mapError = ''
      try {
        const data = await getFarmMap({
          level: 'PROVINCE',
          category_id: this.categoryId ?? undefined
        })
        if (token !== mapSeq) return
        this.map = data
      } catch (error) {
        if (token !== mapSeq) return
        this.mapError = error instanceof Error ? error.message : '地图数据加载失败'
      } finally {
        if (token === mapSeq) this.mapLoading = false
      }
    },
    async setCategory(categoryId: number | null): Promise<void> {
      if (this.categoryId === categoryId) return
      this.categoryId = categoryId
      this.regionOpen = false
      await this.fetchMap()
    },
    async openRegion(cluster: FarmMapCluster): Promise<void> {
      const token = ++regionSeq
      this.regionLoading = true
      this.regionError = ''
      this.regionOpen = true
      this.region = null
      try {
        const data = await getRegionDetail(regionPrefix(cluster.region_code), this.categoryId)
        if (token !== regionSeq) return
        this.region = data
      } catch (error) {
        if (token !== regionSeq) return
        this.regionError = error instanceof Error ? error.message : '地区明细加载失败'
      } finally {
        if (token === regionSeq) this.regionLoading = false
      }
    },
    closeRegion(): void {
      regionSeq += 1
      this.regionOpen = false
      this.regionLoading = false
    },
    async fetchTrend(metric: string): Promise<void> {
      const token = ++trendSeq
      this.trendMetric = metric
      const cached = this.trendCache[metric]
      if (cached) {
        this.trend = cached
        this.trendError = ''
        this.trendLoading = false
        return
      }
      this.trendLoading = true
      this.trendError = ''
      try {
        const data = await getOverviewTrend(metric)
        if (token !== trendSeq) return
        this.trend = data
        this.trendCache = { ...this.trendCache, [metric]: data }
      } catch (error) {
        if (token !== trendSeq) return
        this.trend = null
        this.trendError = error instanceof Error ? error.message : '趋势数据加载失败'
      } finally {
        if (token === trendSeq) this.trendLoading = false
      }
    }
  }
})
