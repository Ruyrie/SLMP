import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import { fetchDrugFlows, fetchDrugLot, type DrugLotDetail, type DrugFlowGroup, type StockFlowItem } from '@/api/stock'
import type { PageStatus } from '@/api/stock'

export const useDrugLotStore = defineStore('drugLot', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    detail: null as DrugLotDetail | null,
    lotId: 0,
    allFlows: [] as StockFlowItem[],
    flows: [] as StockFlowItem[],
    flowsTotal: 0,
    flowsPage: 1,
    flowsSize: 20,
    flowsGroup: 'ALL' as DrugFlowGroup,
    flowsLoading: false,
    loaded: false
  }),
  getters: {
    stockVersion(): number {
      return Number(this.detail?.stock_version || 0)
    },
    isRecall(state): boolean {
      return state.detail?.lot_status === 'RECALLED'
    },
    isExpired(state): boolean {
      return state.detail?.lot_status === 'EXPIRED'
    },
    isNear(state): boolean {
      return state.detail?.lot_status === 'NEAR_EXPIRY'
    },
    canOut(state): boolean {
      return (
        state.detail?.actions?.can_out !== false &&
        Number(state.detail?.stock?.value || 0) > 0 &&
        state.detail?.lot_status !== 'RECALLED' &&
        state.detail?.lot_status !== 'EXPIRED'
      )
    },
    canScrap(state): boolean {
      return state.detail?.actions?.can_scrap !== false && Number(state.detail?.stock?.value || 0) > 0
    },
    pageTotal(): number {
      return Math.max(1, Math.ceil(this.flowsTotal / this.flowsSize))
    }
  },
  actions: {
    async load(lotId: number): Promise<void> {
      this.lotId = lotId
      this.error = ''
      this.status = 'loading'
      try {
        this.detail = await fetchDrugLot(lotId)
        this.status = 'ready'
        this.loaded = true
        this.flowsPage = 1
        await this.loadFlows()
      } catch (error) {
        const err = error as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message || '批号详情加载失败'
      }
    },
    async loadFlows(): Promise<void> {
      if (!this.lotId) return
      this.flowsLoading = true
      try {
        const data = await fetchDrugFlows({
          drug_lot_id: this.lotId,
          flow_group: this.flowsGroup,
          page: 1,
          size: 200
        })
        this.allFlows = this.withBalances(data.list || [])
        this.flowsTotal = data.total || 0
        this.applyPage()
      } catch {
        this.allFlows = []
        this.flows = []
        this.flowsTotal = 0
      } finally {
        this.flowsLoading = false
      }
    },
    withBalances(rows: StockFlowItem[]): StockFlowItem[] {
      let running = Number(this.detail?.stock?.value || 0)
      return rows.map((flow) => {
        const copy = { ...flow, stock_qty_after: running }
        const qty = Number(flow.qty?.value || 0)
        running = flow.direction === 'IN' ? running - qty : running + qty
        return copy
      })
    },
    applyPage(): void {
      const from = (this.flowsPage - 1) * this.flowsSize
      this.flows = this.allFlows.slice(from, from + this.flowsSize)
    },
    async setFlowsGroup(group: DrugFlowGroup): Promise<void> {
      this.flowsGroup = group
      this.flowsPage = 1
      await this.loadFlows()
    },
    setFlowsPage(page: number): void {
      this.flowsPage = page
      this.applyPage()
    },
    setFlowsSize(size: number): void {
      this.flowsSize = size
      this.flowsPage = 1
      this.applyPage()
    },
    async refresh(): Promise<void> {
      await this.load(this.lotId)
    }
  }
})
