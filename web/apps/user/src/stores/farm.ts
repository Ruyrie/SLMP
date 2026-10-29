import { defineStore } from 'pinia'
import { http } from '@/api/client'
import type { PageVO } from '@zmt/shared'
import type { FarmBrief } from '@zmt/shared'

export type FarmRole = 'owner' | 'manager' | 'staff'

const FARM_KEY = 'zmt.farm.id'
const ALL_KEY = 'zmt.farm.all'

function mapRole(role?: string): FarmRole {
  if (role === 'MANAGER') return 'manager'
  if (role === 'STAFF') return 'staff'
  return 'owner'
}

function readNumber(key: string): number {
  if (typeof sessionStorage === 'undefined') return 0
  const value = Number(sessionStorage.getItem(key) || 0)
  return Number.isFinite(value) ? value : 0
}

export const useFarmStore = defineStore('farm', {
  state: () => ({
    farms: [] as FarmBrief[],
    currentFarmId: readNumber(FARM_KEY),
    viewAll: typeof sessionStorage !== 'undefined' && sessionStorage.getItem(ALL_KEY) === '1',
    loaded: false,
    revision: 0,
    switching: false
  }),
  getters: {
    current: (state): FarmBrief | undefined => state.farms.find((farm) => farm.farm_id === state.currentFarmId),
    role(state): FarmRole {
      return mapRole(state.farms.find((farm) => farm.farm_id === state.currentFarmId)?.role)
    },
    roleText(): string {
      return this.role === 'staff' ? '员工' : this.role === 'manager' ? '场长' : '所有者'
    },
    isManager(): boolean {
      return this.role !== 'staff'
    },
    farmName: (state): string => state.farms.find((farm) => farm.farm_id === state.currentFarmId)?.name || '',
    /** 当前养殖场的计数单位（猪场“头”、鸡场“只”），没有时按“头” */
    countUnit: (state): string => state.farms.find((farm) => farm.farm_id === state.currentFarmId)?.count_unit_text || '头',
    switchableFarms: (state): FarmBrief[] => state.farms.filter((farm) => farm.can_switch !== false)
  },
  actions: {
    async load(force = false): Promise<void> {
      if (this.loaded && !force) return
      const data = await http().get<PageVO<FarmBrief>>('/farms', { page: 1, size: 100 })
      this.farms = data.list || []
      const saved = readNumber(FARM_KEY)
      const target =
        this.farms.find((farm) => farm.farm_id === saved && farm.can_switch !== false) ||
        this.farms.find((farm) => farm.is_current && farm.can_switch !== false) ||
        this.farms.find((farm) => farm.status === 'NORMAL' && farm.can_switch !== false) ||
        this.farms[0]
      if (target) this.setCurrent(target.farm_id)
      if (!this.farms.some((farm) => farm.farm_id === this.currentFarmId)) this.viewAll = false
      this.loaded = true
    },
    setCurrent(farmId: number): void {
      this.currentFarmId = farmId
      this.revision += 1
      if (typeof sessionStorage !== 'undefined') sessionStorage.setItem(FARM_KEY, String(farmId))
    },
    async switchTo(farmId: number): Promise<void> {
      if (this.switching) return
      if (farmId === this.currentFarmId) {
        // “全部养殖场”视角下点当前场：退回单场视角
        if (this.viewAll) this.setViewAll(false)
        return
      }
      this.switching = true
      try {
        await http().post(`/farms/${farmId}/switch`)
        this.viewAll = false
        if (typeof sessionStorage !== 'undefined') sessionStorage.setItem(ALL_KEY, '0')
        this.setCurrent(farmId)
      } finally {
        this.switching = false
      }
    },
    setViewAll(value: boolean): void {
      this.viewAll = value
      if (typeof sessionStorage !== 'undefined') sessionStorage.setItem(ALL_KEY, value ? '1' : '0')
      this.revision += 1
    },
    reset(): void {
      this.farms = []
      this.currentFarmId = 0
      this.viewAll = false
      this.loaded = false
      this.revision = 0
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.removeItem(FARM_KEY)
        sessionStorage.removeItem(ALL_KEY)
      }
    }
  }
})
