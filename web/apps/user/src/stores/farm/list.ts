import { defineStore } from 'pinia'
import { farmApi, type FarmAuditVO, type FarmListItemVO, type FarmRelation } from '@/api/farm'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import type { ApiError } from '@zmt/shared'
import { isCancelError, type PageStatus } from './shared'

function messageOf(error: unknown, fallback: string): string {
  const value = error as ApiError | undefined
  return value?.message || fallback
}

export const useFarmListStore = defineStore('farmList', {
  state: () => ({
    relation: 'ALL' as FarmRelation,
    query: { page: 1, size: 20 },
    list: [] as FarmListItemVO[],
    total: 0,
    tabCounts: {} as Record<string, number>,
    status: 'loading' as PageStatus,
    error: '',
    controller: null as AbortController | null,
    switchingId: 0,
    quitTarget: null as FarmListItemVO | null,
    quitting: false,
    auditOpen: false,
    auditFarm: null as FarmListItemVO | null,
    audit: null as FarmAuditVO | null,
    auditStatus: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
    auditError: '',
    deleting: false
  }),
  getters: {
    allCount: (state): number => state.tabCounts.all ?? state.total,
    pendingAuditCount: (state): number => state.tabCounts.pending_audit ?? 0,
    isEmpty: (state): boolean => state.status === 'empty',
    filteredEmpty: (state): boolean => state.status === 'filtered_empty',
    auditTargetName: (state): string => state.audit?.name || state.auditFarm?.name || ''
  },
  actions: {
    async fetch(silent = false): Promise<void> {
      this.controller?.abort()
      const controller = new AbortController()
      this.controller = controller
      if (!silent) this.status = 'loading'
      this.error = ''
      try {
        const data = await farmApi.list(
          { page: this.query.page, size: this.query.size, relation: this.relation },
          { signal: controller.signal }
        )
        if (controller !== this.controller) return
        this.list = data.list || []
        this.total = data.total || 0
        this.tabCounts = data.tab_counts || {}
        if (this.list.length) this.status = 'ready'
        else if (this.relation !== 'ALL') this.status = 'filtered_empty'
        else this.status = 'empty'
      } catch (error) {
        if (controller !== this.controller || isCancelError(error)) return
        const value = error as ApiError
        this.status = value?.isForbidden ? 'forbidden' : 'error'
        this.error = messageOf(error, '养殖场列表加载失败')
      }
    },
    async setRelation(relation: FarmRelation): Promise<void> {
      if (this.relation === relation) return
      this.relation = relation
      this.query.page = 1
      await this.fetch()
    },
    async setPage(page: number): Promise<void> {
      if (page === this.query.page) return
      this.query.page = Math.max(1, page)
      await this.fetch()
    },
    async setSize(size: number): Promise<void> {
      if (size === this.query.size) return
      this.query.size = size
      this.query.page = 1
      await this.fetch()
    },
    async switchFarm(farmId: number): Promise<void> {
      if (this.switchingId) return
      this.switchingId = farmId
      const appFarm = useFarmStore()
      try {
        await appFarm.switchTo(farmId)
        await appFarm.load(true)
        await this.fetch(true)
      } catch (error) {
        useUiStore().toast(messageOf(error, '切换养殖场失败，请稍后重试'), 'error')
      } finally {
        this.switchingId = 0
      }
    },
    async quitFarm(farm: FarmListItemVO): Promise<void> {
      if (this.quitting) return
      this.quitting = true
      const appFarm = useFarmStore()
      try {
        await farmApi.quit(farm.farm_id)
        await appFarm.load(true)
        if (!appFarm.farms.length) appFarm.setCurrent(0)
        this.quitTarget = null
        await this.fetch(true)
        useUiStore().toast(`你已退出${farm.name}`)
      } catch (error) {
        useUiStore().toast(messageOf(error, '退出养殖场失败，请稍后重试'), 'error')
      } finally {
        this.quitting = false
      }
    },
    async openAudit(farm: FarmListItemVO): Promise<void> {
      this.auditOpen = true
      this.auditFarm = farm
      this.audit = null
      this.auditError = ''
      this.auditStatus = 'loading'
      try {
        this.audit = await farmApi.audit(farm.farm_id)
        this.auditStatus = 'ready'
      } catch (error) {
        this.auditStatus = 'error'
        this.auditError = messageOf(error, '审核结果加载失败，请稍后重试')
      }
    },
    closeAudit(): void {
      this.auditOpen = false
      this.audit = null
      this.auditFarm = null
      this.auditStatus = 'idle'
      this.auditError = ''
    },
    async deleteFarm(farm: FarmListItemVO): Promise<boolean> {
      if (this.deleting) return false
      this.deleting = true
      const appFarm = useFarmStore()
      try {
        await farmApi.deleteFarm(farm.farm_id)
        await appFarm.load(true)
        if (!appFarm.farms.length) appFarm.setCurrent(0)
        this.closeAudit()
        this.quitTarget = null
        await this.fetch(true)
        useUiStore().toast(`已删除${farm.name}`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '删除养殖场失败，请稍后重试'), 'error')
        return false
      } finally {
        this.deleting = false
      }
    }
  }
})
