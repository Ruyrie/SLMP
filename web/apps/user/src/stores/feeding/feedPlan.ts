import { defineStore } from 'pinia'
import { ApiError, dayjs } from '@zmt/shared'
import type { RecordLog } from '@/api/herd'
import { herdApi } from '@/api/herd'
import type {
  FeedAttentionItem,
  FeedExecSummary,
  FeedOption,
  FeedPlan,
  FeedPlanBatchOption,
  FeedPlanSaveStage,
  FeedPlanStage,
  FeedRecordDetail,
  FeedStdApply
} from '@/api/feeding'
import { feedingApi } from '@/api/feeding'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import {
  errorText,
  fieldErrorsOf,
  planStateText,
  statusForError,
  todayISO,
  type PageStatus
} from './shared'

export type FeedPlanTab = 'plan' | 'exec'

export interface StageForm {
  stageId: number
  stdStageId?: number
  stageName: string
  dayAgeStart: number
  dayAgeEnd?: number
  state: string
  editable: boolean
  execStartDate?: string
  execEndDate?: string
  enterDate?: string
  feedId: number
  feedName: string
  dailyQtyPerHead: number | null
  stdDailyQty: number | null
  mealsPerDay: number
  mealTimes: string[]
  adjustedFields: string[]
  todayPlanQty?: number
  perMealQty?: number
  estimateDailyQty?: number
  lowStock: boolean
}

export interface BatchChoice {
  batchId: number
  batchNo: string
  penNames: string
  breedName: string
  dayAge: number
  hasPlan: boolean
  all?: boolean
}

const DEFAULT_TIMES = ['07:00', '11:30', '17:00', '20:00']

function stageForm(stage: FeedPlanStage): StageForm {  const times = (stage.meal_times || []).map((meal) => meal.feed_time || '').filter(Boolean)
  while (times.length < Number(stage.meals_per_day || 1)) times.push(DEFAULT_TIMES[times.length] || '08:00')
  return {
    stageId: Number(stage.stage_id || 0),
    stageName: stage.stage_name || '阶段',
    dayAgeStart: Number(stage.day_age_start || 0),
    dayAgeEnd: stage.day_age_end === null || stage.day_age_end === undefined ? undefined : Number(stage.day_age_end),
    state: stage.state || 'UPCOMING',
    editable: !!stage.editable,
    execStartDate: stage.exec_start_date,
    execEndDate: stage.exec_end_date,
    enterDate: stage.enter_date,
    feedId: Number(stage.feed_id || 0),
    feedName: stage.feed_name || '',
    dailyQtyPerHead: stage.daily_qty_per_head === null || stage.daily_qty_per_head === undefined ? null : Number(stage.daily_qty_per_head),
    stdDailyQty: stage.std_daily_qty === null || stage.std_daily_qty === undefined ? null : Number(stage.std_daily_qty),
    mealsPerDay: Number(stage.meals_per_day || 1),
    mealTimes: times.slice(0, Number(stage.meals_per_day || 1)),
    adjustedFields: stage.adjusted_fields || [],
    todayPlanQty: stage.today_plan_qty === undefined ? undefined : Number(stage.today_plan_qty),
    perMealQty: stage.per_meal_qty === undefined ? undefined : Number(stage.per_meal_qty),
    estimateDailyQty: stage.estimate_daily_qty === undefined ? undefined : Number(stage.estimate_daily_qty),
    lowStock: !!stage.low_stock
  }
}

function snapshotOf(state: { abnormalThreshold: number; appliedStdVersionId: number; stages: StageForm[] }): unknown {
  return {
    threshold: state.abnormalThreshold,
    applied: state.appliedStdVersionId,
    stages: state.stages.map((stage) => ({
      id: stage.stageId,
      feed: stage.feedId,
      qty: stage.dailyQtyPerHead,
      meals: stage.mealsPerDay,
      times: stage.mealTimes
    }))
  }
}

function formatCnDate(value?: string | null): string {
  if (!value) return ''
  const date = dayjs(value)
  return date.isValid() ? date.format('M月D日') : value
}

export const useFeedPlanStore = defineStore('feedingPlan', {  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    tab: 'plan' as FeedPlanTab,
    batchOptions: [] as BatchChoice[],
    batchId: 0,
    batchOptionsLoading: false,

    plan: null as FeedPlan | null,
    planLoading: false,
    planError: '',
    stages: [] as StageForm[],
    feedOptions: [] as FeedOption[],
    abnormalThreshold: 10,
    appliedStdVersionId: 0,
    stdApply: null as FeedStdApply | null,
    stdLoading: false,
    saving: false,
    savedSnapshot: '',
    stageErrors: {} as Record<string, string>,
    thresholdDialog: false,
    thresholdInput: 10,
    thresholdError: '',

    logs: [] as RecordLog[],
    logsLoading: false,

    execSummary: null as FeedExecSummary | null,
    execLoading: false,
    execError: '',
    execRange: 'LAST_7' as 'LAST_7' | 'LAST_30' | 'BATCH' | 'CUSTOM',
    execStartDate: '',
    execEndDate: '',
    execDateMenu: false,
    attention: [] as FeedAttentionItem[],
    attentionTotal: 0,
    attentionPage: 1,
    attentionSize: 20,
    attentionLoading: false,

    attentionDrawer: false,
    attentionItem: null as FeedAttentionItem | null,
    attentionRecord: null as FeedRecordDetail | null,
    attentionRecordLoading: false,

    loaded: false
  }),
  getters: {
    selectedBatch(state): BatchChoice | undefined {
      return state.batchOptions.find((item) => item.batchId === state.batchId)
    },
    execBatchChoices(state): BatchChoice[] {
      return state.batchOptions
    },
    planTitleDesc(state): string {
      const batch = state.plan?.batch
      if (!batch) return '选择批次后设置饲喂方案'
      return [batch.pen_names, batch.breed_name, batch.day_age ? `${batch.day_age} 日龄` : '',
        batch.current_count ? `存栏 ${batch.current_count} 头` : ''].filter(Boolean).join(' · ')
    },
    hasPlan(state): boolean {
      return !!state.plan?.has_plan
    },
    currentStage(state): StageForm | undefined {
      return state.stages.find((stage) => stage.state === 'CURRENT')
    },
    stdCard(state): NonNullable<FeedPlan['std_card']> {
      return state.plan?.std_card || {}
    },
    stdName(state): string {
      return state.plan?.std_card?.std_name || ''
    },
    adjustedCount(state): number {
      return Number(state.plan?.std_card?.adjusted_count || 0)
    },
    compareRows(state): Array<{ name: string; std: string; actual: string; adjusted: boolean }> {
      return state.stages.map((stage) => ({
        name: stage.stageName,
        std: stage.stdDailyQty === null ? '—' : `${stage.stdDailyQty} 千克`,
        actual: stage.dailyQtyPerHead === null ? '—' : `${stage.dailyQtyPerHead} 千克`,
        adjusted: stage.stdDailyQty !== null && stage.dailyQtyPerHead !== null
          && Math.abs(stage.stdDailyQty - stage.dailyQtyPerHead) > 0.0001
      }))
    },
    todayPlanText(state): string {
      const stage = state.stages.find((item) => item.state === 'CURRENT')
      if (!stage) return '今天没有执行中的阶段'
      const total = stage.todayPlanQty === undefined ? null : Number(stage.todayPlanQty)
      if (total === null) return '按当前存栏估算'
      const perMeal = stage.perMealQty === undefined ? null : Number(stage.perMealQty)
      return `${total.toLocaleString('en-US')} 千克${perMeal ? ` · 每餐 ${perMeal.toLocaleString('en-US')} 千克` : ''}`
    },
    todayPlanFormula(state): string {
      const stage = state.stages.find((item) => item.state === 'CURRENT')
      const count = Number(state.plan?.batch?.current_count || 0)
      if (!stage || !count) return ''
      const qty = Number(stage.dailyQtyPerHead || 0)
      return `${qty} 千克 × 存栏 ${count} 头 · 分 ${stage.mealsPerDay} 餐`
    },
    dirty(state): boolean {
      return JSON.stringify(snapshotOf({
        abnormalThreshold: state.abnormalThreshold,
        appliedStdVersionId: state.appliedStdVersionId,
        stages: state.stages
      })) !== state.savedSnapshot
    },
    execRangeLabel(state): string {
      if (!state.execSummary?.start_date) return ''
      return `${formatCnDate(state.execSummary.start_date)} 至 ${formatCnDate(state.execSummary.end_date)}`
    },
    execRangeItems(): Array<{ label: string; value: string }> {
      return [
        { label: '近 7 天', value: 'LAST_7' },
        { label: '近 30 天', value: 'LAST_30' },
        { label: '本批次', value: 'BATCH' }
      ]
    },
    deviationText(state): string {
      const value = state.execSummary?.deviation_ratio
      if (value === null || value === undefined) return '—'
      const sign = Number(value) > 0 ? '+' : ''
      return `${sign}${(Number(value) * 100).toFixed(1)}`
    },
    deviationFoot(state): string {
      const direction = state.execSummary?.deviation_direction
      if (direction === 'LESS') return '实际比计划少（按已完成餐次计算）'
      if (direction === 'MORE') return '实际比计划多（按已完成餐次计算）'
      return '与计划一致（按已完成餐次计算）'
    },
    abnormalFoot(state): string {
      const under = Number(state.execSummary?.under_count || 0)
      const over = Number(state.execSummary?.over_count || 0)
      const same = state.execSummary?.abnormal_same_ratio
      if (!under && !over) return '这段时间没有投喂异常'
      if (same !== null && same !== undefined) {
        return `均为${under >= over ? '少投' : '多投'} ${(Number(same) * 100).toFixed(1)}%`
      }
      return `少投 ${under} 次 · 多投 ${over} 次`
    },
    execEmpty(state): boolean {
      return !state.execLoading && !!state.execSummary && !state.execSummary.has_data
    },
    allBatches(state): boolean {
      return state.batchId === 0
    },
    planLogItems(state): Array<{ title: string; time: string; desc: string; tone?: string; icon?: string }> {
      return state.logs.map((log) => ({
        title: log.changes?.length
          ? log.changes.map((change) => `${change.field_name || '字段'}：${change.before || '—'} → ${change.after || '—'}`).join('；')
          : log.op_action_text || '修改饲喂方案',
        time: log.created_at || '',
        desc: log.operator?.name || '',
        tone: 'primary',
        icon: 'edit'
      }))
    }
  },
  actions: {
    snapshot(): unknown {
      return snapshotOf({
        abnormalThreshold: this.abnormalThreshold,
        appliedStdVersionId: this.appliedStdVersionId,
        stages: this.stages
      })
    },
    async load(force = false): Promise<void> {
      const farm = useFarmStore()
      if (farm.role === 'staff') {
        this.status = 'forbidden'
        this.error = '你没有查看饲喂方案的权限'
        return
      }
      if (this.loaded && !force) return
      this.status = 'loading'
      this.error = ''
      try {
        if (!this.batchOptions.length) await this.loadBatchOptions()
        if (!this.batchId && this.batchOptions.length) this.batchId = this.batchOptions[0].batchId
        await this.loadPlan()
        this.loaded = true
        this.status = this.plan ? 'ready' : 'empty'
      } catch (error) {
        this.status = statusForError(error)
        this.error = errorText(error, '饲喂方案加载失败')
      }
    },
    async loadBatchOptions(): Promise<void> {
      this.batchOptionsLoading = true
      try {
        const list: FeedPlanBatchOption[] = await feedingApi.planBatchOptions()
        this.batchOptions = list.map((item) => ({
          batchId: Number(item.batch_id || 0),
          batchNo: item.batch_no || '',
          penNames: item.pen_names || '',
          breedName: item.breed_name || '',
          dayAge: Number(item.day_age || 0),
          hasPlan: !!item.has_plan
        }))
      } catch (error) {
        this.status = statusForError(error)
        this.error = errorText(error, '方案批次加载失败')
      } finally {
        this.batchOptionsLoading = false
      }
    },
    async loadPlan(): Promise<void> {
      if (!this.batchId) return
      this.planLoading = true
      this.planError = ''
      this.stageErrors = {}
      try {
        const plan = await feedingApi.feedPlan(this.batchId)
        this.plan = plan
        this.feedOptions = plan.feed_options || []
        this.stages = (plan.stages || []).map(stageForm)
        this.abnormalThreshold = Number(plan.abnormal_threshold ?? 0.1) * 100
        this.appliedStdVersionId = 0
        this.savedSnapshot = JSON.stringify(this.snapshot())
        void this.loadLogs()
      } catch (error) {
        this.plan = null
        this.planError = errorText(error, '饲喂方案加载失败')
      } finally {
        this.planLoading = false
      }
    },
    async loadLogs(): Promise<void> {
      if (!this.plan?.plan_id) {
        this.logs = []
        return
      }
      this.logsLoading = true
      try {
        this.logs = await herdApi.recordLogs('FEED_PLAN', Number(this.plan.plan_id))
      } catch {
        this.logs = []
      } finally {
        this.logsLoading = false
      }
    },
    async setBatch(batchId: number): Promise<void> {
      if (batchId === 0) {
        this.batchId = 0
        this.execSummary = null
        this.attention = []
        this.attentionPage = 1
        if (this.tab !== 'exec') this.tab = 'exec'
        await this.loadExec(true)
        return
      }
      if (batchId === this.batchId) return
      this.batchId = batchId
      this.plan = null
      this.stages = []
      this.execSummary = null
      this.attention = []
      if (this.tab === 'plan') {
        await this.loadPlan()
      } else {
        await this.loadExec(true)
      }
    },
    async setTab(tab: FeedPlanTab): Promise<void> {
      this.tab = tab
      if (tab === 'exec' && !this.execSummary) await this.loadExec(true)
    },
    feedOptionsFor(): Array<{ title: string; value: number; hint: string; low: boolean }> {
      return this.feedOptions.map((feed) => ({
        title: feed.feed_name || '饲料',
        value: Number(feed.feed_id || 0),
        hint: `结存 ${Number(feed.stock_qty || 0).toLocaleString('en-US')} 千克${feed.remaining_days ? ` · 剩余可用 ${feed.remaining_days} 天` : ' · 暂无消耗'}`,
        low: !!feed.low_stock
      }))
    },
    stageStateText(state: string): string {
      return planStateText(state)
    },
    setStageQty(stage: StageForm, value: string): void {
      const qty = Number(value)
      stage.dailyQtyPerHead = Number.isFinite(qty) ? qty : null
    },
    setStageMeals(stage: StageForm, count: number): void {
      const meals = Math.max(1, Math.min(4, Number(count) || 1))
      stage.mealsPerDay = meals
      while (stage.mealTimes.length < meals) stage.mealTimes.push(DEFAULT_TIMES[stage.mealTimes.length] || '08:00')
      stage.mealTimes = stage.mealTimes.slice(0, meals)
    },
    setStageTime(stage: StageForm, index: number, value: string): void {
      stage.mealTimes[index] = value
    },
    recalcStageSummary(): void {
      const stage = this.currentStage
      const count = Number(this.plan?.batch?.current_count || 0)
      if (!stage || !count) return
      stage.todayPlanQty = Number(((stage.dailyQtyPerHead || 0) * count).toFixed(2))
      stage.perMealQty = Number(((stage.todayPlanQty || 0) / Math.max(1, stage.mealsPerDay)).toFixed(2))
    },
    async applyStandard(): Promise<void> {
      if (!this.batchId || this.stdLoading) return
      this.stdLoading = true
      try {
        const std = await feedingApi.feedPlanStandard(this.batchId)
        this.stdApply = std
        if (!std.std_available) {
          useUiStore().toast('平台还没有这个品种的饲喂标准，请手动设置各阶段的饲料和投喂量', 'info')
          return
        }
        if (!this.stages.length) {
          const dayAge = Number(this.plan?.batch?.day_age || 0)
          this.stages = (std.stages || []).map((item, index) => {
            const start = Number(item.day_age_start || 0)
            const end = item.day_age_end === undefined || item.day_age_end === null ? undefined : Number(item.day_age_end)
            const state = end !== undefined && dayAge > end ? 'ENDED' : (dayAge >= start ? 'CURRENT' : 'UPCOMING')
            const times = (item.meal_times || []).map((time) => String(time).slice(0, 5))
            return {
              stageId: 0,
              stdStageId: item.std_stage_id === undefined ? undefined : Number(item.std_stage_id),
              stageName: item.stage_name || `阶段 ${index + 1}`,
              dayAgeStart: start,
              dayAgeEnd: end,
              state,
              editable: state !== 'ENDED',
              feedId: Number(item.matched_feed_id || 0),
              feedName: item.matched_feed_name || item.std_feed_name || '',
              dailyQtyPerHead: Number(item.daily_qty || 0),
              stdDailyQty: Number(item.daily_qty || 0),
              mealsPerDay: Number(item.meals_per_day || 3),
              mealTimes: times.length ? times.slice(0, Number(item.meals_per_day || 3)) : DEFAULT_TIMES.slice(0, Number(item.meals_per_day || 3)),
              adjustedFields: [],
              lowStock: false
            } as StageForm
          })
          this.appliedStdVersionId = Number(std.std_version_id || 0)
          this.recalcStageSummary()
          useUiStore().toast(`已套用${std.std_name || '品种标准'}，保存后生效`)
          return
        }
        for (const stage of this.stages) {
          if (!stage.editable) continue
          const match = (std.stages || []).find((item) => item.stage_name === stage.stageName)
            || (std.stages || []).find((item) => item.day_age_start === stage.dayAgeStart)
          if (!match) continue
          stage.stdStageId = match.std_stage_id === undefined ? undefined : Number(match.std_stage_id)
          stage.feedId = Number(match.matched_feed_id || 0)
          stage.feedName = match.matched_feed_name || match.std_feed_name || ''
          stage.dailyQtyPerHead = Number(match.daily_qty || 0)
          stage.stdDailyQty = Number(match.daily_qty || 0)
          stage.mealsPerDay = Number(match.meals_per_day || stage.mealsPerDay)
          const times = (match.meal_times || []).map((time) => String(time).slice(0, 5))
          stage.mealTimes = times.length ? times.slice(0, stage.mealsPerDay) : stage.mealTimes.slice(0, stage.mealsPerDay)
          while (stage.mealTimes.length < stage.mealsPerDay) stage.mealTimes.push(DEFAULT_TIMES[stage.mealTimes.length] || '08:00')
        }
        this.appliedStdVersionId = Number(std.std_version_id || 0)
        const defaultThreshold = Number(std.default_abnormal_threshold || 0.1) * 100
        if (defaultThreshold) this.abnormalThreshold = defaultThreshold
        this.recalcStageSummary()
        useUiStore().toast(`已套用${std.std_name || '品种标准'}，保存后生效`)
      } catch (error) {
        useUiStore().toast(errorText(error, '套用品种标准失败'), 'error')
      } finally {
        this.stdLoading = false
      }
    },
    validate(): boolean {
      this.stageErrors = {}
      this.stages.forEach((stage, index) => {
        if (!stage.editable) return
        if (!stage.feedId) this.stageErrors[`${index}-feed`] = '请选择饲料'
        if (stage.dailyQtyPerHead === null || !Number.isFinite(stage.dailyQtyPerHead) || stage.dailyQtyPerHead <= 0) {
          this.stageErrors[`${index}-qty`] = '每头日投喂量要大于 0 千克'
        }
        const times = stage.mealTimes.map((time) => String(time || '').slice(0, 5))
        if (times.some((time) => !/^\d{2}:\d{2}$/.test(time))) {
          this.stageErrors[`${index}-time`] = '请填写投喂时间'
        } else if (new Set(times).size !== times.length) {
          this.stageErrors[`${index}-time`] = '投喂时间不能重复'
        } else if (times.length !== stage.mealsPerDay) {
          this.stageErrors[`${index}-time`] = '时间个数等于餐次'
        }
      })
      if (this.abnormalThreshold < 1 || this.abnormalThreshold > 100) {
        this.stageErrors.threshold = '偏差比例要在 1% 到 100% 之间'
      }
      return Object.keys(this.stageErrors).length === 0
    },
    async savePlan(): Promise<boolean> {
      if (this.saving) return false
      if (!this.validate()) {
        useUiStore().toast('请先修正方案中的错误', 'error')
        return false
      }
      this.recalcStageSummary()
      this.saving = true
      const stages: FeedPlanSaveStage[] = this.stages.map((stage) => ({
        stage_id: stage.stageId || undefined,
        std_stage_id: stage.stdStageId,
        stage_name: stage.stageName,
        day_age_start: stage.dayAgeStart,
        day_age_end: stage.dayAgeEnd,
        feed_id: stage.feedId,
        daily_qty_per_head: stage.dailyQtyPerHead === null ? undefined : Number(stage.dailyQtyPerHead),
        meals_per_day: stage.mealsPerDay,
        meal_times: stage.mealTimes.map((time) => String(time).slice(0, 5)).sort()
      }))
      try {
        const plan = await feedingApi.saveFeedPlan(this.batchId, {
          applied_std_version_id: this.appliedStdVersionId || undefined,
          abnormal_threshold: this.abnormalThreshold / 100,
          stages,
          version: this.plan?.version
        })
        this.plan = plan
        this.stages = (plan.stages || []).map(stageForm)
        this.abnormalThreshold = Number(plan.abnormal_threshold ?? this.abnormalThreshold / 100) * 100
        this.appliedStdVersionId = 0
        this.savedSnapshot = JSON.stringify(this.snapshot())
        useUiStore().toast('已保存饲喂方案')
        await this.loadLogs()
        return true
      } catch (error) {
        if (error instanceof ApiError) {
          const fields = fieldErrorsOf(error)
          if (Object.keys(fields).length) this.stageErrors = { ...fields }
          if (error.code === 40902 || error.code === 40903) {
            useUiStore().toast('内容已更新，请刷新后再修改', 'error')
          } else {
            useUiStore().toast(error.message || '保存饲喂方案失败', 'error')
          }
        } else {
          useUiStore().toast('保存饲喂方案失败，请稍后重试', 'error')
        }
        return false
      } finally {
        this.saving = false
      }
    },
    openThreshold(): void {
      this.thresholdInput = this.abnormalThreshold
      this.thresholdError = ''
      this.thresholdDialog = true
    },
    async saveThreshold(): Promise<boolean> {
      if (this.thresholdInput < 1 || this.thresholdInput > 100) {
        this.thresholdError = '偏差比例要在 1% 到 100% 之间'
        return false
      }
      this.abnormalThreshold = this.thresholdInput
      const ok = await this.savePlan()
      if (ok) this.thresholdDialog = false
      return ok
    },
    resetPlanChanges(): void {
      void this.loadPlan()
    },
    async loadExec(force = false): Promise<void> {
      if (!force && this.execSummary) return
      this.execLoading = true
      this.execError = ''
      try {
        const params = {
          batchId: this.batchId && !this.allBatches ? this.batchId : undefined,
          allBatches: this.allBatches,
          range: this.execRange,
          startDate: this.execStartDate || undefined,
          endDate: this.execEndDate || undefined
        }
        const [summary, attention] = await Promise.all([
          feedingApi.execSummary(params),
          feedingApi.execAttention({ ...params, page: this.attentionPage, size: this.attentionSize })
        ])
        this.execSummary = summary
        this.attention = attention.list || []
        this.attentionTotal = Number(attention.total || 0)
      } catch (error) {
        this.execError = errorText(error, '饲喂执行情况加载失败')
      } finally {
        this.execLoading = false
      }
    },
    async setRange(range: 'LAST_7' | 'LAST_30' | 'BATCH' | 'CUSTOM'): Promise<void> {
      this.execRange = range
      this.attentionPage = 1
      if (range !== 'CUSTOM') {
        this.execStartDate = ''
        this.execEndDate = ''
      }
      await this.loadExec(true)
    },
    async setCustomRange(start: string, end: string): Promise<void> {
      this.execRange = 'CUSTOM'
      this.execStartDate = start
      this.execEndDate = end
      this.attentionPage = 1
      await this.loadExec(true)
    },
    async setAttentionPage(page: number): Promise<void> {
      this.attentionPage = Math.max(1, page)
      await this.loadExec(true)
    },
    async openAttention(item: FeedAttentionItem): Promise<void> {
      this.attentionItem = item
      this.attentionRecord = null
      this.attentionDrawer = true
      if (item.record_id) {
        this.attentionRecordLoading = true
        try {
          this.attentionRecord = await feedingApi.feedRecord(Number(item.record_id))
        } catch {
          this.attentionRecord = null
        } finally {
          this.attentionRecordLoading = false
        }
      }
    },
    reset(): void {
      this.$reset()
    }
  }
})
