import { defineStore } from 'pinia'
import { dayjs } from '@zmt/shared'
import { getAdmins, getOperationLogDetail, getOperationLogs, type AdminBrief, type OperationLogDetail, type OperationLogItem } from '@/api/system'

export type LoadStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error'

function today(): string {
  return dayjs().tz('Asia/Shanghai').format('YYYY-MM-DD')
}

function weekAgo(): string {
  return dayjs().tz('Asia/Shanghai').subtract(6, 'day').format('YYYY-MM-DD')
}

export const useOperationLogsStore = defineStore('operationLogs', {
  state: () => ({
    query: {
      date_from: weekAgo(),
      date_to: today(),
      admin_id: 0,
      op_type: '',
      keyword: '',
      page: 1,
      size: 20
    },
    list: [] as OperationLogItem[],
    total: 0,
    admins: [] as AdminBrief[],
    status: 'loading' as LoadStatus,
    error: '',
    detail: null as OperationLogDetail | null,
    detailLoading: false,
    drawer: false
  }),
  actions: {
    async loadAdmins(): Promise<void> {
      try {
        const data = await getAdmins()
        this.admins = data.list || []
      } catch {
        this.admins = []
      }
    },
    async fetchList(): Promise<void> {
      this.status = this.list.length ? this.status : 'loading'
      this.error = ''
      try {
        const data = await getOperationLogs({
          ...this.query,
          admin_id: this.query.admin_id || undefined,
          op_type: this.query.op_type || undefined,
          keyword: this.query.keyword || undefined
        })
        this.list = data.list || []
        this.total = data.total || 0
        this.status = this.list.length ? 'ready' : this.query.keyword || this.query.op_type || this.query.admin_id ? 'filtered_empty' : 'empty'
      } catch (error) {
        this.status = 'error'
        this.error = error instanceof Error ? error.message : '加载失败'
      }
    },
    search(): void {
      this.query.page = 1
      void this.fetchList()
    },
    resetFilters(): void {
      this.query = {
        date_from: weekAgo(),
        date_to: today(),
        admin_id: 0,
        op_type: '',
        keyword: '',
        page: 1,
        size: 20
      }
      void this.fetchList()
    },
    async openDetail(id: number): Promise<void> {
      this.drawer = true
      this.detailLoading = true
      this.detail = null
      try {
        this.detail = await getOperationLogDetail(id)
      } catch (error) {
        this.detail = null
        this.error = error instanceof Error ? error.message : '详情加载失败'
      } finally {
        this.detailLoading = false
      }
    },
    exportCsv(): void {
      const headers = ['时间', '操作人', '类型', '操作', '对象', '原因/说明']
      const rows = this.list.map((item) => [
        item.created_at,
        `${item.operator_name}（${item.operator_role_text || ''}）`,
        item.op_type_text,
        item.op_action_text,
        item.target_name,
        item.reason
      ])
      const csv = [headers, ...rows].map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\r\n')
      const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `操作日志-${this.query.date_from}-${this.query.date_to}.csv`
      link.click()
      URL.revokeObjectURL(url)
    }
  }
})
