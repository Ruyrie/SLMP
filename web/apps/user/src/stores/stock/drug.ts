import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  fetchDrugLots,
  fetchDrugOverview,
  fetchStockSetting,
  saveStockSetting,
  type DrugLotItem,
  type DrugStockOverview,
  type DrugStockSetting
} from '@/api/stock'
import type { PageStatus } from '@/api/stock'

type LotCategory = '' | 'VET_DRUG' | 'VACCINE' | 'DISINFECTANT'
type LotGroup = 'ATTENTION' | 'NORMAL'

export const useDrugStockStore = defineStore('drugStock', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    overview: null as DrugStockOverview | null,
    category: '' as LotCategory,
    keyword: '',
    attention: [] as DrugLotItem[],
    normal: [] as DrugLotItem[],
    attentionTotal: 0,
    normalTotal: 0,
    attentionPage: 1,
    normalPage: 1,
    size: 20,
    loadingLots: false,
    setting: null as DrugStockSetting | null,
    settingLoading: false,
    loaded: false
  }),
  getters: {
    counts(state): { all: number; vet: number; vaccine: number; disinfectant: number } {
      const c = state.overview?.lot_counts
      return {
        all: c?.all || 0,
        vet: c?.vet_drug || 0,
        vaccine: c?.vaccine || 0,
        disinfectant: c?.disinfectant || 0
      }
    },
    categoryItems(): Array<{ label: string; value: LotCategory; count?: number }> {
      return [
        { label: '全部', value: '', count: this.counts.all },
        { label: '兽药', value: 'VET_DRUG', count: this.counts.vet },
        { label: '疫苗', value: 'VACCINE', count: this.counts.vaccine },
        { label: '消毒剂', value: 'DISINFECTANT', count: this.counts.disinfectant }
      ]
    },
    nearText(state): string {
      const n = state.overview?.near_days
      if (!n) return ''
      if (n.vet === n.vaccine && n.vaccine === n.disinfectant) return `到期前 ${n.vet} 天为临期`
      return `到期前 ${n.vet}–${Math.max(n.vet, n.vaccine, n.disinfectant)} 天为临期`
    },
    lowStockDrugs(state): NonNullable<DrugStockOverview['low_stock_banner']>['drugs'] {
      return state.overview?.low_stock_banner?.drugs || []
    },
    lowStockText(): string {
      const drugs = this.lowStockDrugs
      if (!drugs.length) return ''
      if (drugs.length === 1) {
        const drug = drugs[0]
        const usable = drug.usable
        const packUnit = usable?.pack_unit
        const shown = packUnit && Number(usable?.pack_count || 0) > 0 ? Number(usable?.pack_count) : Number(usable?.value || 0)
        const unit = packUnit || usable?.unit_text || ''
        return `${drug.name}库存不足，可用结存 ${shown.toLocaleString('en-US')} ${unit}，低于库存下限 ${drug.min_stock ?? '—'} ${unit}。`
      }
      return `${drugs.length} 种药品库存不足：${drugs.map((drug) => drug.name).join('、')}。`
    }
  },
  actions: {
    async load(): Promise<void> {
      this.error = ''
      this.status = this.attention.length || this.normal.length ? this.status : 'loading'
      try {
        const [overview, attention, normal] = await Promise.all([
          fetchDrugOverview(),
          fetchDrugLots({
            group: 'ATTENTION',
            category: this.category || undefined,
            keyword: this.keyword || undefined,
            page: this.attentionPage,
            size: this.size
          }),
          fetchDrugLots({
            group: 'NORMAL',
            category: this.category || undefined,
            keyword: this.keyword || undefined,
            page: this.normalPage,
            size: this.size
          })
        ])
        this.overview = overview
        this.attention = attention.list || []
        this.attentionTotal = attention.total || 0
        this.normal = normal.list || []
        this.normalTotal = normal.total || 0
        this.status = this.attention.length || this.normal.length ? 'ready' : 'empty'
        this.loaded = true
      } catch (error) {
        const err = error as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message || '兽药疫苗库存加载失败'
      }
    },
    async setCategory(category: LotCategory): Promise<void> {
      this.category = category
      this.attentionPage = 1
      this.normalPage = 1
      await this.load()
    },
    async search(keyword: string): Promise<void> {
      this.keyword = keyword
      this.attentionPage = 1
      this.normalPage = 1
      await this.load()
    },
    async setAttentionPage(page: number): Promise<void> {
      this.attentionPage = page
      await this.load()
    },
    async setNormalPage(page: number): Promise<void> {
      this.normalPage = page
      await this.load()
    },
    async setSize(size: number): Promise<void> {
      this.size = size
      this.attentionPage = 1
      this.normalPage = 1
      await this.load()
    },
    async loadSetting(): Promise<void> {
      if (this.setting) return
      this.settingLoading = true
      try {
        this.setting = await fetchStockSetting()
      } finally {
        this.settingLoading = false
      }
    },
    async saveSetting(payload: { near_days_vet: number; near_days_vaccine: number; near_days_disinfectant: number; version: number }): Promise<boolean> {
      this.settingLoading = true
      try {
        this.setting = await saveStockSetting(payload)
        await this.load()
        return true
      } finally {
        this.settingLoading = false
      }
    }
  }
})

export type { LotCategory, LotGroup }
