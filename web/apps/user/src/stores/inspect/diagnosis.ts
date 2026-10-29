import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  fetchDiagnosis,
  fetchDiagnosisState,
  fetchRecordLogs,
  rediagnose as requestRediagnose,
  saveHandle as requestSaveHandle,
  type DiagnosisDetail,
  type DiagnosisState,
  type HandleSavePayload,
  type HandleSaveResult,
  type RecordLog
} from '@/api/inspect'
import type { PageStatus } from '@/api/inspect'

const POLL_INTERVAL = 3000
const POLL_MAX_TRIES = 40

export const useDiagnosisStore = defineStore('diagnosisDetail', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    detail: null as DiagnosisDetail | null,
    handleOpen: false,
    handleSubmitting: false,
    handleLogsOpen: false,
    handleLogsLoading: false,
    handleLogs: [] as RecordLog[],
    rediagnosing: false,
    pollTimer: 0 as number,
    pollTries: 0,
    reportId: 0,
    disposed: false,
    requestId: 0,
    pollToken: 0
  }),
  getters: {
    permissions(state): NonNullable<DiagnosisDetail['permissions']> {
      return state.detail?.permissions || {}
    },
    canHandle(): boolean {
      return !!this.permissions.can_handle
    },
    canModifyHandle(): boolean {
      return !!this.permissions.can_modify_handle
    },
    canRediagnose(): boolean {
      return !!this.permissions.can_rediagnose
    },
    isPending(state): boolean {
      return state.detail?.status === 'PENDING'
    },
    isClosed(state): boolean {
      return state.detail?.status === 'CLOSED'
    },
    aiState(state): string {
      return state.detail?.ai_state || ''
    },
    urgency(state): string {
      return state.detail?.diagnosis?.urgency || ''
    },
    urgencyText(state): string {
      return state.detail?.diagnosis?.urgency_text || ''
    },
    isUrgent(): boolean {
      return this.urgency === 'URGENT'
    },
    needsPolling(): boolean {
      if (!this.detail) return false
      if (this.detail.status === 'PENDING') return true
      return this.detail.ai_state === 'DIAGNOSING'
    },
    stepItems(): Array<{ title: string; desc?: string; status: string }> {
      const titles: Record<string, string> = { PENDING: '待诊断', DIAGNOSED: '已诊断', HANDLED: '已处理', CLOSED: '已结案' }
      return (this.detail?.steps || []).map((step) => ({
        title: titles[step.code] || step.code,
        desc: step.desc || (step.time ? step.time.slice(5, 16) : ''),
        status: step.state === 'DONE' ? 'done' : step.state === 'CURRENT' ? 'current' : 'wait'
      }))
    }
  },
  actions: {
    async load(reportId: number): Promise<void> {
      // 离开上一份详情时 dispose 过：重新进入（含新诊断）必须恢复，否则轮询第一拍就退出
      this.disposed = false
      if (reportId !== this.reportId) this.detail = null
      this.reportId = reportId
      this.status = this.detail ? this.status : 'loading'
      this.error = ''
      this.stopPolling()
      const requestId = ++this.requestId
      try {
        const detail = await fetchDiagnosis(reportId)
        if (requestId !== this.requestId || this.disposed) return
        this.detail = detail
        this.status = 'ready'
        if (this.needsPolling) this.startPolling()
      } catch (error) {
        if (requestId !== this.requestId || this.disposed) return
        const err = error as ApiError
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message || '诊断详情加载失败'
      }
    },
    startPolling(): void {
      this.stopPolling()
      this.pollTries = 0
      const token = ++this.pollToken
      const reportId = this.reportId
      // 换了报告、页面离开或重新开始轮询后，旧的计时器不再继续
      const current = (): boolean => !this.disposed && token === this.pollToken && reportId === this.reportId
      const tick = async (): Promise<void> => {
        if (!current()) return
        this.pollTries += 1
        try {
          const state: DiagnosisState = await fetchDiagnosisState(reportId)
          if (!current()) return
          if (state.ai_state !== 'DIAGNOSING' && state.status !== 'PENDING') {
            this.stopPolling()
            await this.load(reportId)
            return
          }
        } catch {
          /* 轮询失败继续，直到上限 */
        }
        if (!current()) return
        if (this.pollTries >= POLL_MAX_TRIES) {
          this.stopPolling()
          return
        }
        this.pollTimer = window.setTimeout(() => void tick(), POLL_INTERVAL)
      }
      this.pollTimer = window.setTimeout(() => void tick(), POLL_INTERVAL)
    },
    stopPolling(): void {
      this.pollToken += 1
      if (this.pollTimer) {
        window.clearTimeout(this.pollTimer)
        this.pollTimer = 0
      }
    },
    async rediagnose(): Promise<void> {
      if (!this.reportId || this.rediagnosing) return
      this.rediagnosing = true
      try {
        await requestRediagnose(this.reportId)
        await this.load(this.reportId)
      } catch (error) {
        this.error = (error as ApiError).message || 'AI 诊断仍不可用，服务恢复后会自动重新诊断'
        throw error
      } finally {
        this.rediagnosing = false
      }
    },
    openHandle(): void {
      this.handleOpen = true
    },
    async openHandleLogs(): Promise<void> {
      const handleId = this.detail?.handle?.handle_id
      if (!handleId) return
      this.handleLogsOpen = true
      this.handleLogsLoading = true
      try {
        this.handleLogs = await fetchRecordLogs('DIAGNOSIS_HANDLE', handleId)
      } catch {
        this.handleLogs = []
      } finally {
        this.handleLogsLoading = false
      }
    },
    closeHandle(): void {
      if (this.handleSubmitting) return
      this.handleOpen = false
    },
    async saveHandle(payload: HandleSavePayload): Promise<HandleSaveResult | null> {
      if (!this.reportId || this.handleSubmitting) return null
      this.handleSubmitting = true
      try {
        const result = await requestSaveHandle(this.reportId, payload)
        this.handleOpen = false
        await this.load(this.reportId)
        return result
      } finally {
        this.handleSubmitting = false
      }
    },
    dispose(): void {
      this.disposed = true
      this.stopPolling()
      this.detail = null
      this.status = 'loading'
    }
  }
})
