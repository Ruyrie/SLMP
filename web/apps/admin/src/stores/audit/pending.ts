import { defineStore } from 'pinia'
import { getPendingCounts, type PendingCounts } from '@/api/audit'
import { useAdminStore } from '@/stores/admin'

function emptyCounts(): PendingCounts {
  return {
    farm_audit: 0,
    farm_change: 0,
    change_request: 0,
    account_recovery: 0,
    overdue_total: 0,
    items: [],
    sellable_date_count: 0
  }
}

/** 审核中心角标：每次处理完申请后刷新，同时写回外壳与平台概览的待处理数。 */
export const useAuditPendingStore = defineStore('auditPending', {
  state: () => ({
    counts: emptyCounts(),
    loading: false,
    loadedAt: 0
  }),
  getters: {
    total: (state) =>
      (state.counts.farm_audit || 0) +
      (state.counts.farm_change || 0) +
      (state.counts.change_request || 0) +
      (state.counts.account_recovery || 0)
  },
  actions: {
    async refresh(): Promise<void> {
      this.loading = true
      try {
        const data = await getPendingCounts()
        this.counts = { ...emptyCounts(), ...data }
        this.loadedAt = Date.now()
        const admin = useAdminStore()
        admin.badges = {
          ...admin.badges,
          farm_audit: data.farm_audit || 0,
          farm_change: data.farm_change || 0,
          change_request: data.change_request || 0,
          account_recovery: data.account_recovery || 0
        }
      } catch {
        /* 角标失败不阻塞页面；保留上次数值 */
      } finally {
        this.loading = false
      }
    }
  }
})
