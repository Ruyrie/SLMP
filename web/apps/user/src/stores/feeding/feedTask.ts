import { defineStore } from 'pinia'
import { ApiError, dayjs, formatNumber } from '@zmt/shared'
import type {
  FeedRecordDetail,
  FeedTaskForm,
  FeedTaskItem,
  FeedTaskSummary
} from '@/api/feeding'
import { feedingApi } from '@/api/feeding'
import { useUiStore } from '@/stores/ui'
import { useFarmStore } from '@/stores/farm'
import { claimFarmScope, currentFarmScope, resetFarmCache } from '@/stores/farmScope'
import {
  errorText,
  fieldErrorsOf,
  minutesText,
  statusForError,
  taskStatusTag,
  todayISO,
  type PageStatus
} from './shared'

export type TaskDrawerMode = 'register' | 'edit' | 'view'

interface FeedFormState {
  feedId: number
  actualQty: number | null
  executorId: number
  remark: string
}

export const useFeedTaskStore = defineStore('feedingTask', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    summary: null as FeedTaskSummary | null,
    list: [] as FeedTaskItem[],
    total: 0,
    query: { page: 1, size: 20, status: '', penId: 0 },
    loaded: false,
    farmScope: '',
    requestId: 0,

    drawer: false,
    drawerMode: 'register' as TaskDrawerMode,
    formLoading: false,
    formError: '',
    form: null as FeedTaskForm | null,
    record: null as FeedRecordDetail | null,
    recordLoading: false,
    formState: { feedId: 0, actualQty: null, executorId: 0, remark: '' } as FeedFormState,
    errors: {} as Record<string, string>,
    submitting: false,
    voidConfirm: false,
    voidSubmitting: false
  }),
  getters: {
    counts(): { all: number; pending: number; done: number; overdue: number } {
      const counts = this.summary?.status_counts
      return {
        all: Number(this.summary?.task_total || 0),
        pending: Number(counts?.pending || 0),
        done: Number(counts?.done || 0),
        overdue: Number(counts?.overdue || 0)
      }
    },
    segItems(): Array<{ label: string; value: string; count: number }> {
      return [
        { label: '全部', value: '', count: this.counts.all },
        { label: '待投喂', value: 'PENDING', count: this.counts.pending },
        { label: '已投喂', value: 'DONE', count: this.counts.done },
        { label: '已逾期', value: 'OVERDUE', count: this.counts.overdue }
      ]
    },
    penItems(): Array<{ title: string; value: number }> {
      return [
        { title: '全部栏舍', value: 0 },
        ...(this.summary?.pen_options || []).map((pen) => ({
          title: pen.pen_name || '栏舍',
          value: Number(pen.pen_id || 0)
        }))
      ]
    },
    planShortages(): NonNullable<FeedTaskSummary['plan_shortage']> {
      return (this.summary?.plan_shortage || []).slice(0, 2)
    },
    moreShortageCount(): number {
      return Number(this.summary?.plan_shortage_more_count || 0)
    },
    summaryFoot(): { plan: string; done: string; overdue: string } {
      const summary = this.summary
      if (!summary) return { plan: '', done: '', overdue: '' }
      const penCount = Number(summary.pen_count || 0)
      const meals = Number(summary.task_total || 0)
      const done = Number(summary.done_count || 0)
      const planFoot = `${penCount} 个栏舍 · ${meals} 餐`
      const doneFoot = `实投合计 ${Number(summary.actual_total_qty || 0).toLocaleString('en-US')} 千克`
      let overdueFoot = '没有逾期未登记的餐次'
      const first = summary.first_overdue
      if (Number(summary.overdue_count || 0) > 0 && first) {
        overdueFoot = `${first.pen_name || ''} ${first.meal_name || ''} · 超过投喂时间 ${minutesText(first.overdue_minutes)}`
      }
      return { plan: planFoot, done: doneFoot, overdue: overdueFoot }
    },
    dateText(): string {
      const date = this.summary?.date || todayISO()
      const weekday = this.summary?.weekday_text || dayjs(date).format('ddd')
      return `${dayjs(date).format('M月D日')} ${weekday}`
    },
    emptyText(): { title: string; desc: string } {
      const reason = this.summary?.empty_reason
      if (reason === 'NO_PLAN') {
        return { title: '今天没有饲喂任务', desc: '还没有设置饲喂方案的批次不会生成任务。先为在养批次设置饲喂方案。' }
      }
      return { title: '今天没有饲喂任务', desc: '还没有设置饲喂方案的批次不会生成任务。先为在养批次设置饲喂方案。' }
    },
    formPlan(): FeedTaskForm | null {
      return this.form
    },
    selectedFeed(): NonNullable<FeedTaskForm['feed_options']>[number] | undefined {
      return (this.form?.feed_options || []).find((item) => Number(item.feed_id) === this.formState.feedId)
    },
    deviation(): number | null {
      const plan = Number(this.form?.plan_qty || 0)
      const actual = Number(this.formState.actualQty ?? NaN)
      if (!plan || !Number.isFinite(actual)) return null
      return (actual - plan) / plan
    },
    abnormal(): boolean {
      const threshold = Number(this.form?.abnormal_threshold ?? 0.1)
      return this.deviation !== null && Math.abs(this.deviation) > threshold
    },
    abnormalText(): string {
      const deviation = this.deviation
      if (deviation === null) return ''
      const threshold = Number(this.form?.abnormal_threshold ?? 0.1) * 100
      const percent = Math.abs(deviation * 100).toFixed(1)
      return deviation < 0
        ? `投喂异常：实际比计划少 ${percent}%，超过 ${threshold.toFixed(0)}%。确认后这次投喂会标为投喂异常。`
        : `投喂异常：实际比计划多 ${percent}%，超过 ${threshold.toFixed(0)}%。确认后这次投喂会标为投喂异常。`
    },
    formulaText(): string {
      const formula = this.record?.plan_formula
      const head = Number(formula?.head_count ?? this.form?.head_count ?? 0)
      const perHead = Number(formula?.daily_qty_per_head ?? this.form?.daily_qty_per_head ?? 0)
      const meals = Number(formula?.meals_per_day ?? this.form?.meals_per_day ?? 0)
      const unit = useFarmStore().countUnit
      const base = head && perHead && meals
        ? `${formatNumber(head)} ${unit} × 每${unit}日投喂 ${perHead} 千克 ÷ ${meals} 餐`
        : ''
      const deviation = this.deviation
      const suffix = deviation === null ? '' : ` · 偏差 ${(deviation * 100).toFixed(1)}%`
      return `${base}${suffix}`
    },
    stockError(): string {
      const feed = this.selectedFeed
      const actual = Number(this.formState.actualQty ?? 0)
      if (!feed) return ''
      const stock = Number(feed.stock_qty || 0)
      if (actual > stock) return `不能超过结存 ${stock.toLocaleString('en-US')} 千克`
      return ''
    },
    exceedsStock(): boolean {
      const feed = this.selectedFeed
      const actual = Number(this.formState.actualQty ?? 0)
      return !!feed && actual > Number(feed.stock_qty || 0)
    },
    formFeedOptions(): Array<{ title: string; value: number; hint: string; disabled: boolean; low: boolean }> {
      return (this.form?.feed_options || []).map((feed) => ({
        title: feed.feed_name || '饲料',
        value: Number(feed.feed_id || 0),
        hint: `结存 ${Number(feed.stock_qty || 0).toLocaleString('en-US')} 千克${feed.remaining_days ? ` · 剩余可用 ${feed.remaining_days} 天` : ' · 暂无消耗'}`,
        disabled: feed.selectable === false,
        low: !!feed.low_stock
      }))
    },
    executorItems(): Array<{ title: string; value: number }> {
      return (this.form?.executor_options || []).map((user) => ({
        title: `${user.name || '成员'}${user.role_text ? ` · ${user.role_text}` : ''}`,
        value: Number(user.user_id || 0)
      }))
    },
    isProxy(): boolean {
      const current = Number(this.form?.current_user?.user_id || 0)
      return !!current && this.formState.executorId !== current
    },
    proxyText(): string {
      const current = this.form?.current_user
      if (!current) return ''
      return this.isProxy
        ? `登记人：${current.name || '当前用户'}（代登记）`
        : `登记人：${current.name || '当前用户'}`
    },
    title(): string {
      if (this.drawerMode === 'edit') return '修改投喂'
      if (this.drawerMode === 'view') return '投喂记录'
      return '登记投喂'
    },
    task(): FeedTaskForm | null {
      return this.form
    }
  },
  actions: {
    /** 页面未挂载时也可能切场：使用缓存前先核对归属，不是当前养殖场就清空。 */
    prepareFarmScope(): string {
      return claimFarmScope(this)
    },
    async load(force = false): Promise<void> {
      const scope = this.prepareFarmScope()
      if (this.loaded && !force) return
      const requestId = ++this.requestId
      this.status = this.list.length ? this.status : 'loading'
      this.error = ''
      try {
        const [summary, page] = await Promise.all([
          feedingApi.todaySummary(),
          feedingApi.todayTasks({
            page: this.query.page,
            size: this.query.size,
            status: this.query.status || undefined,
            penId: this.query.penId || undefined
          })
        ])
        if (requestId !== this.requestId || scope !== currentFarmScope()) return
        this.summary = summary
        this.list = page.list || []
        this.total = Number(page.total || 0)
        this.loaded = true
        if (!this.total) {
          this.status = this.query.status || this.query.penId ? 'filtered_empty' : 'empty'
        } else {
          this.status = 'ready'
        }
      } catch (error) {
        if (requestId !== this.requestId || scope !== currentFarmScope()) return
        this.status = statusForError(error)
        this.error = errorText(error, '饲喂任务加载失败，请稍后重新加载')
      }
    },
    async refresh(): Promise<void> {
      await this.load(true)
    },
    setStatus(status: string): void {
      this.query.status = status
      this.query.page = 1
      void this.load(true)
    },
    setPen(penId: number): void {
      this.query.penId = penId
      this.query.page = 1
      void this.load(true)
    },
    setPage(page: number): void {
      this.query.page = Math.max(1, page)
      void this.load()
    },
    setSize(size: number): void {
      this.query.size = Math.max(1, size)
      this.query.page = 1
      void this.load()
    },
    resetFilters(): void {
      this.query.status = ''
      this.query.penId = 0
      this.query.page = 1
      void this.load(true)
    },
    taskStatus(item: FeedTaskItem): { text: string; type: string; icon: string } {
      return taskStatusTag(item.status, item.abnormal_type)
    },
    async openTask(item: FeedTaskItem): Promise<void> {
      const taskId = Number(item.task_id || 0)
      if (!taskId) return
      this.drawer = true
      this.formError = ''
      this.errors = {}
      this.record = null
      this.voidConfirm = false
      this.formLoading = true
      try {
        const form = await feedingApi.taskForm(taskId)
        this.form = form
        const recordId = Number(item.record_id || form.record_id || 0)
        if (item.action === 'REGISTER') {
          this.drawerMode = 'register'
          this.formState = {
            feedId: Number(form.default_feed?.feed_id || 0),
            actualQty: Number(form.plan_qty || 0) || null,
            executorId: Number(form.default_executor?.user_id || form.current_user?.user_id || 0),
            remark: ''
          }
        } else {
          this.drawerMode = item.action === 'EDIT' ? 'edit' : 'view'
          if (recordId) {
            this.recordLoading = true
            const record = await feedingApi.feedRecord(recordId)
            this.record = record
            this.formState = {
              feedId: Number(record.feed?.feed_id || 0),
              actualQty: Number(record.actual_qty || 0),
              executorId: Number(record.executor?.user_id || 0),
              remark: record.remark || ''
            }
            if (this.drawerMode === 'edit' && record.view_mode && record.view_mode !== 'EDIT') {
              this.drawerMode = 'view'
            }
          }
        }
      } catch (error) {
        this.formError = errorText(error, '投喂任务加载失败')
      } finally {
        this.formLoading = false
        this.recordLoading = false
      }
    },
    close(): void {
      this.drawer = false
      this.form = null
      this.record = null
      this.voidConfirm = false
    },
    validate(): boolean {
      this.errors = {}
      if (!this.formState.feedId) this.errors.feed_id = '请选择饲料'
      const actual = Number(this.formState.actualQty ?? NaN)
      if (!Number.isFinite(actual) || actual <= 0) this.errors.actual_qty = '实际投喂量要大于 0 千克'
      else if (this.exceedsStock) this.errors.actual_qty = this.stockError
      if (this.drawerMode !== 'view' && Number(this.form?.current_user?.user_id || 0) && !this.formState.executorId) {
        this.errors.executor_id = '请选择实际执行人'
      }
      if ([...this.formState.remark].length > 100) this.errors.remark = '备注最多 100 字'
      return Object.keys(this.errors).length === 0
    },
    async submit(): Promise<boolean> {
      if (this.submitting || this.drawerMode === 'view') return false
      if (!this.validate()) return false
      if (this.drawerMode === 'register' && this.form?.can_register === false) {
        useUiStore().toast('这条任务已经不能登记了，请刷新后查看', 'error')
        return false
      }
      this.submitting = true
      try {
        if (this.drawerMode === 'edit' && this.record) {
          const detail = await feedingApi.updateFeedRecord(Number(this.record.record_id), {
            feed_id: this.formState.feedId,
            actual_qty: Number(this.formState.actualQty),
            executor_id: this.formState.executorId || undefined,
            remark: this.formState.remark.trim() || undefined,
            version: Number(this.record.version || 0)
          })
          this.record = detail
          useUiStore().toast('已修改投喂记录，饲料出库已同步更正')
        } else {
          const executor = this.executorItems.find((item) => item.value === this.formState.executorId)
          const detail = await feedingApi.createFeedRecord({
            task_id: Number(this.form?.task_id),
            feed_id: this.formState.feedId,
            actual_qty: Number(this.formState.actualQty),
            executor_id: this.formState.executorId || undefined,
            remark: this.formState.remark.trim() || undefined
          })
          this.record = detail
          useUiStore().toast(detail.is_proxy
            ? `已代${detail.proxy_executor_name || executor?.title.split(' · ')[0] || '执行人'}登记投喂，待办已自动完成`
            : '已登记投喂，待办已自动完成')
        }
        this.close()
        await this.refresh()
        return true
      } catch (error) {
        if (error instanceof ApiError) {
          const fields = fieldErrorsOf(error)
          if (Object.keys(fields).length) this.errors = { ...fields }
          else useUiStore().toast(error.message || '提交失败', 'error')
        } else {
          useUiStore().toast('提交失败，请稍后重试', 'error')
        }
        return false
      } finally {
        this.submitting = false
      }
    },
    async voidRecord(): Promise<boolean> {
      if (!this.record || this.voidSubmitting) return false
      this.voidSubmitting = true
      try {
        await feedingApi.voidFeedRecord(Number(this.record.record_id), Number(this.record.version || 0))
        useUiStore().toast('已作废投喂记录，饲料出库已同步作废')
        this.close()
        await this.refresh()
        return true
      } catch (error) {
        useUiStore().toast(errorText(error, '作废失败'), 'error')
        return false
      } finally {
        this.voidSubmitting = false
      }
    },
    reset(): void {
      resetFarmCache(this)
    }
  }
})
