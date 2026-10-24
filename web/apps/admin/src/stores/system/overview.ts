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

export const useOverviewStore = defineStore('overview', {
  state: () => ({
    status: 'loading' as OverviewStatus,
    error: '',
    refreshing: false,
    data: null as OverviewData | null,
    map: null as FarmMapData | null,
    mapLoading: false,
    mapError: '',
    categoryGroup: '',
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
    failedBlocks: (state) => state.data?.failed_blocks || []
  },
  actions: {
    async load(): Promise<void> {
      if (!this.data) this.status = 'loading'
      this.error = ''
      try {
        this.data = await getOverview()
        this.status = 'ready'
      } catch (error) {
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
      this.mapLoading = true
      this.mapError = ''
      try {
        this.map = await getFarmMap({
          level: 'PROVINCE',
          category_group: this.categoryGroup || undefined
        })
      } catch (error) {
        this.mapError = error instanceof Error ? error.message : '地图数据加载失败'
      } finally {
        this.mapLoading = false
      }
    },
    async setCategory(group: string): Promise<void> {
      this.categoryGroup = group
      this.regionOpen = false
      await this.fetchMap()
    },
    async openRegion(cluster: FarmMapCluster): Promise<void> {
      this.regionLoading = true
      this.regionError = ''
      this.regionOpen = true
      this.region = null
      try {
        this.region = await getRegionDetail(regionPrefix(cluster.region_code))
      } catch (error) {
        this.regionError = error instanceof Error ? error.message : '地区明细加载失败'
      } finally {
        this.regionLoading = false
      }
    },
    closeRegion(): void {
      this.regionOpen = false
    },
    async fetchTrend(metric: string): Promise<void> {
      this.trendMetric = metric
      const cached = this.trendCache[metric]
      if (cached) {
        this.trend = cached
        this.trendError = ''
        return
      }
      this.trendLoading = true
      this.trendError = ''
      try {
        const data = await getOverviewTrend(metric)
        this.trend = data
        this.trendCache = { ...this.trendCache, [metric]: data }
      } catch (error) {
        this.trend = null
        this.trendError = error instanceof Error ? error.message : '趋势数据加载失败'
      } finally {
        this.trendLoading = false
      }
    }
  }
})
