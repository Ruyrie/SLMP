import { defineStore } from 'pinia'
import type { ApiError } from '@zmt/shared'
import {
  healthApi,
  type DictItem,
  type DisinfectPlan,
  type DisinfectPlanDetail,
  type DisinfectPlanPreview,
  type DisinfectRecord,
  type DisinfectTask,
  type DisinfectTaskOption,
  type DisinfectantOption,
  type DrugLotOptions,
  type ExecutorOption,
  type OverdueBanner,
  type PenOption,
  type RecordLog
} from '@/api/health'
import { useUiStore } from '@/stores/ui'
import {
  dateText,
  errorText,
  fieldErrorsOf,
  healthLogItems,
  isValidConcentration,
  monthDay,
  nowInput,
  sameConcentration,
  statusForError,
  toDateTimePayload,
  todayISO,
  type HealthLogItem,
  type PageStatus
} from './shared'

export type DisinfectTab = 'tasks' | 'plans'
export type TaskStatusFilter = 'ALL' | 'OVERDUE' | 'PENDING' | 'DONE'
export type PlanStatusFilter = 'ALL' | 'ACTIVE' | 'PAUSED'
export type RecordType = 'PLAN_TASK' | 'EMPTY_PEN' | 'EMERGENCY'
export type RegisterKind = 'register' | 'temp' | 'view' | 'edit'

export interface DisinfectRecordModel {
  recordType: RecordType
  taskId: number
  penId: number
  actualTime: string
  executorId: number | null
  drugId: number
  lotId: number
  usageQty: string
  concentration: string
  concentrationConfirmed: boolean
  methodCode: string
  fileIds: number[]
}

function emptyRecordModel(): DisinfectRecordModel {
  return {
    recordType: 'PLAN_TASK',
    taskId: 0,
    penId: 0,
    actualTime: nowInput(),
    executorId: null,
    drugId: 0,
    lotId: 0,
    usageQty: '',
    concentration: '',
    concentrationConfirmed: false,
    methodCode: '',
    fileIds: []
  }
}

export const useDisinfectStore = defineStore('healthDisinfect', {
  state: () => ({
    tab: 'tasks' as DisinfectTab,
    staffView: false,
    penOptions: [] as PenOption[],

    taskStatus: 'ready' as PageStatus,
    taskError: '',
    tasks: [] as DisinfectTask[],
    taskTotal: 0,
    taskQuery: { page: 1, size: 20, status: 'ALL' as TaskStatusFilter, penId: 0 },
    taskCounts: { pending: 0, done: 0, overdue: 0 },
    overdueBanner: null as OverdueBanner | null,
    taskRequestId: 0,

    planStatus: 'ready' as PageStatus,
    planError: '',
    plans: [] as DisinfectPlan[],
    planTotal: 0,
    planQuery: { page: 1, size: 20, status: 'ALL' as PlanStatusFilter },
    planCounts: { all: 0, active: 0, paused: 0 },
    planRequestId: 0,

    optionsLoaded: false,
    optionsLoading: false,
    disinfectants: [] as DisinfectantOption[],
    methods: [] as DictItem[],
    taskOptions: [] as DisinfectTaskOption[],
    taskOptionsLoading: false,

    planDrawer: false,
    planMode: 'create' as 'create' | 'edit',
    planLoading: false,
    planSubmitting: false,
    planErrors: {} as Record<string, string>,
    planPreview: null as DisinfectPlanPreview | null,
    planPreviewLoading: false,
    planPreviewSeq: 0,
    planEditingId: 0,
    planEditingVersion: 0,
    planDetail: null as DisinfectPlanDetail | null,
    planModel: {
      penIds: [] as number[],
      cycleDays: '',
      startDate: '',
      endDate: '',
      catalogId: 0,
      concentration: '',
      methodCode: ''
    },

    pauseOpen: false,
    pauseTarget: null as DisinfectPlan | null,
    pauseSubmitting: false,
    pauseErrors: {} as Record<string, string>,
    pauseReason: '',

    registerOpen: false,
    registerKind: 'register' as RegisterKind,
    registerLoading: false,
    registerSubmitting: false,
    registerError: '',
    registerModel: emptyRecordModel(),
    registerErrors: {} as Record<string, string>,
    registerBackfill: false,
    lots: null as DrugLotOptions | null,
    lotsLoading: false,
    executors: [] as ExecutorOption[],

    recordOpen: false,
    recordLoading: false,
    recordSubmitting: false,
    recordError: '',
    recordEdit: false,
    record: null as DisinfectRecord | null,
    recordModel: emptyRecordModel(),
    recordErrors: {} as Record<string, string>,
    recordLots: null as DrugLotOptions | null,
    recordLotsLoading: false,
    recordExecutors: [] as ExecutorOption[],

    logOpen: false,
    logLoading: false,
    logError: '',
    logTitle: '',
    logs: [] as RecordLog[]
  }),
  getters: {
    penItems(state): Array<{ title: string; value: number }> {
      return [
        { title: '全部栏舍', value: 0 },
        ...state.penOptions.map((pen) => ({ title: pen.pen_name || '', value: Number(pen.pen_id || 0) }))
      ]
    },
    enabledPens(state): PenOption[] {
      return state.penOptions.filter((pen) => !pen.disabled)
    },
    taskSegments(): Array<{ label: string; value: TaskStatusFilter; count?: number }> {
      const counts = this.taskCounts
      return [
        { label: '全部', value: 'ALL' as TaskStatusFilter },
        { label: '已逾期', value: 'OVERDUE' as TaskStatusFilter, count: counts.overdue },
        { label: '待执行', value: 'PENDING' as TaskStatusFilter, count: counts.pending },
        { label: '已执行', value: 'DONE' as TaskStatusFilter, count: counts.done }
      ]
    },
    planSegments(): Array<{ label: string; value: PlanStatusFilter; count: number }> {
      const counts = this.planCounts
      return [
        { label: '全部', value: 'ALL' as PlanStatusFilter, count: counts.all },
        { label: '启用中', value: 'ACTIVE' as PlanStatusFilter, count: counts.active },
        { label: '已暂停', value: 'PAUSED' as PlanStatusFilter, count: counts.paused }
      ]
    },
    planSummaryTitle(): string {
      const preview = this.planPreview
      if (!preview?.ready) return preview?.ready_text || '填写栏舍、周期和起止日期后显示将生成的任务数'
      if (this.planMode === 'edit' && preview.regenerate_from) {
        return `修改后将重新生成 ${dateText(preview.regenerate_from)}以后未执行的任务`
      }
      return `将生成 ${preview.task_count || 0} 条消毒任务`
    },
    planSummaryText(): string {
      const preview = this.planPreview
      if (!preview?.ready) return ''
      if (this.planMode === 'edit' && preview.regenerate_from) {
        return `已执行和已逾期的任务不变。${preview.remind_text || ''}`
      }
      return preview.remind_text || ''
    },
    planPenText(state): string {
      const preview = state.planPreview
      if (preview?.pen_text) return preview.pen_text
      const count = state.planModel.penIds.length
      if (!count) return ''
      return `已选 ${count} 个栏舍，系统为每个栏舍各生成一条任务。`
    },
    planStockHint(): string {
      return this.selectedDisinfectant?.stock_hint_text || ''
    },
    planStockWarning(): boolean {
      const option = this.selectedDisinfectant
      return !!option && option.stock_hint_type !== 'NORMAL'
    },
    selectedDisinfectant: (state): DisinfectantOption | null =>
      state.disinfectants.find((item) => Number(item.catalog_id) === Number(state.planModel.catalogId)) || null,
    planMethodItems(): Array<{ title: string; value: string }> {
      const allowed = this.selectedDisinfectant?.method_codes || []
      const list = allowed.length
        ? this.methods.filter((method) => allowed.includes(method.code || ''))
        : this.methods
      return list.map((method) => ({ title: method.name || '', value: method.code || '' }))
    },
    planDisinfectantItems(state): Array<{ title: string; value: number; subtitle: string }> {
      return state.disinfectants.map((item) => ({
        title: item.name || '',
        value: Number(item.catalog_id || 0),
        subtitle: `${item.recommended_concentration || ''}${item.stock_hint_text ? ` · ${item.stock_hint_text}` : ''}`
      }))
    },
    planDoseHelper(): string {
      const days = Number(this.planModel.cycleDays)
      return Number.isInteger(days) && days > 0 ? `每 ${days} 天一次` : ''
    },
    registerTitle(state): string {
      if (state.registerKind === 'view') return '消毒记录'
      if (state.registerKind === 'edit') return '修改消毒记录'
      return '登记消毒'
    },
    registerSub(state): string {
      if (state.registerKind === 'view' || state.registerKind === 'edit') return this.recordPenName
      if (state.registerModel.recordType === 'PLAN_TASK') {
        const task = this.selectedTask
        return task
          ? `${task.pen_name || ''} · 计划任务 · 登记后任务变为已执行，待办自动完成`
          : '选择一条待执行或已逾期的消毒任务后登记'
      }
      return `${this.penName(state.registerModel.penId) ? `${this.penName(state.registerModel.penId)} · ` : ''}${state.registerModel.recordType === 'EMPTY_PEN' ? '空栏消毒' : '紧急消毒'} · 临时登记`
    },
    penName: (state) => (penId: number) => {
      const pen = state.penOptions.find((item) => Number(item.pen_id) === Number(penId))
      return pen?.pen_name || ''
    },
    selectedTask(state): DisinfectTaskOption | null {
      return state.taskOptions.find((item) => Number(item.task_id) === Number(state.registerModel.taskId)) || null
    },
    taskOptionItems(): Array<{ title: string; value: number; subtitle: string }> {
      return this.taskOptions.map((task) => ({
        title: `${task.pen_name || ''} · ${task.date_text || monthDay(task.task_date)}`,
        value: Number(task.task_id || 0),
        subtitle: `${task.overdue ? '已逾期 · ' : ''}${task.plan_text || ''}`
      }))
    },
    registerPenItems(state): Array<{ title: string; value: number }> {
      return state.penOptions
        .filter((pen) => !pen.disabled)
        .map((pen) => ({ title: pen.pen_name || '', value: Number(pen.pen_id || 0) }))
    },
    registerDisinfectantItems(state): Array<{ title: string; value: number; subtitle: string }> {
      return state.disinfectants.map((item) => ({
        title: item.name || '',
        value: Number(item.drug_id || 0),
        subtitle: item.stock_hint_text || ''
      }))
    },
    registerSelectedDisinfectant(): DisinfectantOption | null {
      const drugId = this.registerModel.drugId
      return this.disinfectants.find((item) => Number(item.drug_id) === drugId) || null
    },
    registerMethodItems(): Array<{ title: string; value: string }> {
      const allowed = this.registerSelectedDisinfectant?.method_codes || []
      const list = allowed.length
        ? this.methods.filter((method) => allowed.includes(method.code || ''))
        : this.methods
      return list.map((method) => ({ title: method.name || '', value: method.code || '' }))
    },
    registerLotItems(state): Array<{ title: string; value: number; subtitle: string; disabled: boolean }> {
      const options = state.lots
      if (!options) return []
      const toItem = (lot: NonNullable<DrugLotOptions['selectable']>[number], disabled: boolean) => ({
        title: lot.lot_no || '',
        value: Number(lot.lot_id || 0),
        subtitle: [
          lot.expire_date ? `${lot.expire_date} 到期` : '',
          lot.stock?.value !== null && lot.stock?.value !== undefined
            ? `结存 ${lot.stock.value} ${lot.stock.unit_text || ''}`
            : '',
          lot.priority_out ? '先到期先出' : '',
          lot.lot_status === 'EXPIRED' ? '已过期，只能报废' : '',
          lot.lot_status === 'RECALLED' || lot.lot_status === 'BANNED' ? '平台已停用，只能报废' : ''
        ].filter(Boolean).join(' · '),
        disabled
      })
      return [
        ...(options.selectable || []).map((lot) => toItem(lot, false)),
        ...(options.unselectable || []).map((lot) => toItem(lot, true))
      ]
    },
    selectedRegisterLot(): NonNullable<DrugLotOptions['selectable']>[number] | null {
      const options = this.lots
      if (!options || !this.registerModel.lotId) return null
      return [...(options.selectable || []), ...(options.unselectable || [])]
        .find((lot) => Number(lot.lot_id) === Number(this.registerModel.lotId)) || null
    },
    registerLotHelper(): string {
      const lot = this.selectedRegisterLot
      if (!lot) return '默认取有效期最早的批号'
      const parts = [lot.expire_date ? `${lot.expire_date} 到期` : '']
      if (lot.priority_out) parts.push('先到期先出')
      return parts.filter(Boolean).join(' · ')
    },
    registerPlanText(): string {
      return this.selectedTask?.plan_text || ''
    },
    registerConcentrationMismatch(): boolean {
      if (this.registerModel.recordType !== 'PLAN_TASK') return false
      const task = this.selectedTask
      if (!task?.plan_concentration) return false
      return !sameConcentration(this.registerModel.concentration, task.plan_concentration)
    },
    registerConcentrationHelper(): string {
      const task = this.selectedTask
      return this.registerModel.recordType === 'PLAN_TASK' && task?.plan_concentration
        ? `计划浓度 ${task.plan_concentration}`
        : ''
    },
    registerUsageHelper(): string {
      const lot = this.selectedRegisterLot
      if (!lot?.stock?.value && lot?.stock?.value !== 0) return '按原液计，登记后扣减库存'
      return `按原液计 · 批号结存 ${lot.stock.value} ${lot.stock.unit_text || ''}`
    },
    registerTypeHint(state): string {
      if (state.registerModel.recordType === 'EMPTY_PEN') return '空栏消毒不计入批次成本'
      if (state.registerModel.recordType === 'EMERGENCY') return '紧急消毒单独统计次数，不计入执行率'
      return '空栏消毒和紧急消毒可以不依据计划，临时登记'
    },
    registerTypeSegments(): Array<{ label: string; value: RecordType }> {
      return [
        { label: '计划任务', value: 'PLAN_TASK' },
        { label: '空栏消毒', value: 'EMPTY_PEN' },
        { label: '紧急消毒', value: 'EMERGENCY' }
      ]
    },
    registerExecutorItems(state): Array<{ title: string; value: number }> {
      return state.executors.map((member) => ({
        title: member.display_name || member.name || '',
        value: Number(member.user_id || 0)
      }))
    },
    recordLogItems(state): HealthLogItem[] {
      return healthLogItems(state.logs, 'DISINFECT_RECORD')
    },
    logItems(state): HealthLogItem[] {
      return healthLogItems(state.logs, 'DISINFECT_RECORD')
    },
    recordLotItems(state): Array<{ title: string; value: number; subtitle: string; disabled: boolean }> {
      const options = state.recordLots
      if (!options) return []
      return [
        ...(options.selectable || []).map((lot) => ({
          title: lot.lot_no || '',
          value: Number(lot.lot_id || 0),
          subtitle: [lot.expire_date ? `${lot.expire_date} 到期` : '', lot.priority_out ? '先到期先出' : ''].filter(Boolean).join(' · '),
          disabled: false
        })),
        ...(options.unselectable || []).map((lot) => ({
          title: lot.lot_no || '',
          value: Number(lot.lot_id || 0),
          subtitle: '不可选择',
          disabled: true
        }))
      ]
    },
    recordPenName(): string {
      return this.record?.pen_name || this.penName(this.recordModel.penId)
    },
    recordTypeText(): string {
      const type = this.record?.record_type || this.recordModel.recordType
      return type === 'EMPTY_PEN' ? '空栏消毒' : type === 'EMERGENCY' ? '紧急消毒' : '计划任务'
    },
    recordKindText(): string {
      if (!this.record) return ''
      if (this.record.in_batch_cost === false) return '空栏消毒不计入批次成本'
      if (this.record.in_exec_rate === false) return '紧急消毒单独统计次数，不计入执行率'
      return '计入执行率与批次成本'
    },
    taskEmptyText(): string {
      if (this.staffView) return '你负责的栏舍暂时没有消毒任务。'
      if (this.taskQuery.status !== 'ALL') {
        const label = this.taskQuery.status === 'OVERDUE' ? '已逾期' : this.taskQuery.status === 'PENDING' ? '待执行' : '已执行'
        return `没有${label}的任务。`
      }
      return '暂时没有消毒任务'
    },
    taskEmptyDesc(): string {
      if (this.staffView) return '任务由消毒计划按周期生成，或由所有者、场长临时登记。'
      if (this.taskQuery.status !== 'ALL') return '换一个状态或栏舍筛选试试。'
      return '新建消毒计划后，系统会按周期生成任务。'
    },
    planEmptyText(): string {
      if (this.planQuery.status !== 'ALL') {
        const label = this.planQuery.status === 'ACTIVE' ? '启用中' : '已暂停'
        return `没有${label}的计划。`
      }
      return '还没有消毒计划'
    },
    planEmptyDesc(): string {
      return this.planQuery.status === 'ALL'
        ? '新建一个计划，系统会按周期为每个栏舍生成消毒任务。'
        : '换一个状态筛选试试。'
    }
  },
  actions: {
    async init(isStaff: boolean): Promise<void> {
      this.staffView = isStaff
      if (isStaff) this.tab = 'tasks'
      await this.loadPens()
      if (!isStaff) void this.fetchPlans()
      await this.fetchTasks()
    },
    async reload(): Promise<void> {
      await this.loadPens()
      if (!this.staffView) await this.fetchPlans()
      await this.fetchTasks()
    },
    async loadPens(): Promise<void> {
      try {
        if (this.staffView) {
          const data = await healthApi.myPens()
          this.penOptions = (data.pens || []).map((pen) => ({
            pen_id: pen.pen_id,
            pen_code: pen.pen_code,
            pen_name: pen.pen_name,
            pen_type: pen.pen_type,
            stock_count: pen.stock_count,
            capacity: pen.capacity,
            disabled: false,
            unit_text: pen.unit_text
          }))
        } else {
          const list = await healthApi.pens()
          this.penOptions = (list || []).filter((pen) => !pen.disabled)
        }
      } catch {
        this.penOptions = []
      }
    },
    setTab(tab: DisinfectTab): void {
      if (this.staffView && tab === 'plans') return
      if (this.tab === tab) return
      this.tab = tab
      void this.loadOptions()
      if (!this.staffView && tab === 'plans') void this.fetchPlans()
      if (tab === 'tasks') void this.fetchTasks()
    },
    setTaskStatus(status: TaskStatusFilter): void {
      if (this.taskQuery.status === status) return
      this.taskQuery.status = status
      this.taskQuery.page = 1
      void this.fetchTasks()
    },
    setTaskPen(penId: number): void {
      this.taskQuery.penId = Number(penId || 0)
      this.taskQuery.page = 1
      void this.fetchTasks()
    },
    setTaskPage(page: number): void {
      this.taskQuery.page = Math.max(1, Number(page) || 1)
      void this.fetchTasks()
    },
    setTaskSize(size: number): void {
      this.taskQuery.size = Math.max(1, Number(size) || 20)
      this.taskQuery.page = 1
      void this.fetchTasks()
    },
    setPlanStatus(status: PlanStatusFilter): void {
      if (this.planQuery.status === status) return
      this.planQuery.status = status
      this.planQuery.page = 1
      void this.fetchPlans()
    },
    setPlanPage(page: number): void {
      this.planQuery.page = Math.max(1, Number(page) || 1)
      void this.fetchPlans()
    },
    setPlanSize(size: number): void {
      this.planQuery.size = Math.max(1, Number(size) || 20)
      this.planQuery.page = 1
      void this.fetchPlans()
    },
    async fetchTasks(): Promise<void> {
      const requestId = ++this.taskRequestId
      if (!this.tasks.length) this.taskStatus = 'loading'
      this.taskError = ''
      try {
        const page = await healthApi.disinfectTasks({
          page: this.taskQuery.page,
          size: this.taskQuery.size,
          status: this.taskQuery.status,
          ...(this.taskQuery.penId ? { pen_id: this.taskQuery.penId } : {})
        })
        if (requestId !== this.taskRequestId) return
        this.tasks = page.list || []
        this.taskTotal = Number(page.total || 0)
        this.taskCounts = {
          pending: Number(page.tab_counts?.pending || 0),
          done: Number(page.tab_counts?.done || 0),
          overdue: Number(page.tab_counts?.overdue || 0)
        }
        this.overdueBanner = page.overdue_banner || null
        this.taskStatus = this.tasks.length
          ? 'ready'
          : this.taskQuery.status === 'ALL' && !this.taskQuery.penId
            ? 'empty'
            : 'filtered_empty'
      } catch (error) {
        if (requestId !== this.taskRequestId) return
        this.taskStatus = statusForError(error)
        this.taskError = errorText(error, '消毒任务加载失败，请稍后重新加载')
      }
    },
    async fetchPlans(): Promise<void> {
      if (this.staffView) return
      const requestId = ++this.planRequestId
      if (!this.plans.length) this.planStatus = 'loading'
      this.planError = ''
      try {
        const page = await healthApi.disinfectPlans({
          page: this.planQuery.page,
          size: this.planQuery.size,
          status: this.planQuery.status
        })
        if (requestId !== this.planRequestId) return
        this.plans = page.list || []
        this.planTotal = Number(page.total || 0)
        this.planCounts = {
          all: Number(page.tab_counts?.all || 0),
          active: Number(page.tab_counts?.active || 0),
          paused: Number(page.tab_counts?.paused || 0)
        }
        this.planStatus = this.plans.length ? 'ready' : this.planQuery.status === 'ALL' ? 'empty' : 'filtered_empty'
      } catch (error) {
        if (requestId !== this.planRequestId) return
        this.planStatus = statusForError(error)
        this.planError = errorText(error, '消毒计划加载失败，请稍后重新加载')
      }
    },
    async loadOptions(): Promise<void> {
      if (this.optionsLoaded && (this.disinfectants.length || this.methods.length)) return
      this.optionsLoading = true
      try {
        const [disinfectants, methods] = await Promise.all([
          healthApi.disinfectants(),
          healthApi.dict('DISINFECT_METHOD')
        ])
        this.disinfectants = disinfectants || []
        this.methods = methods || []
        this.optionsLoaded = true
      } catch {
        this.optionsLoaded = true
      } finally {
        this.optionsLoading = false
      }
    },
    // ===== 消毒计划 =====
    async openPlanCreate(): Promise<void> {
      this.planMode = 'create'
      this.planEditingId = 0
      this.planEditingVersion = 0
      this.planDetail = null
      this.planErrors = {}
      this.planPreview = null
      this.planModel = {
        penIds: [],
        cycleDays: '',
        startDate: '',
        endDate: '',
        catalogId: 0,
        concentration: '',
        methodCode: ''
      }
      this.planDrawer = true
      this.planLoading = true
      await this.loadOptions()
      this.planLoading = false
    },
    async openPlanEdit(plan: DisinfectPlan): Promise<void> {
      this.planMode = 'edit'
      this.planEditingId = Number(plan.id || 0)
      this.planEditingVersion = Number(plan.version || 0)
      this.planErrors = {}
      this.planPreview = null
      this.planDrawer = true
      this.planLoading = true
      await this.loadOptions()
      try {
        const detail = await healthApi.disinfectPlan(Number(plan.id))
        this.planDetail = detail
        this.planEditingVersion = Number(detail.version || this.planEditingVersion)
        this.planModel = {
          penIds: (detail.pen_ids || []).map((id) => Number(id)),
          cycleDays: detail.cycle_days === null || detail.cycle_days === undefined ? '' : String(detail.cycle_days),
          startDate: detail.start_date || '',
          endDate: detail.end_date || '',
          catalogId: Number(detail.catalog_id || 0),
          concentration: detail.concentration || '',
          methodCode: detail.method_code || ''
        }
        this.schedulePlanPreview()
      } catch (error) {
        useUiStore().toast(errorText(error, '消毒计划加载失败'), 'error')
        this.planDrawer = false
      } finally {
        this.planLoading = false
      }
    },
    togglePlanPen(penId: number): void {
      const ids = new Set(this.planModel.penIds)
      if (ids.has(penId)) ids.delete(penId)
      else ids.add(penId)
      this.planModel.penIds = Array.from(ids)
      this.planErrors = { ...this.planErrors, pen_ids: '' }
      this.schedulePlanPreview()
    },
    selectPlanDisinfectant(catalogId: number): void {
      const option = this.disinfectants.find((item) => Number(item.catalog_id) === catalogId)
      this.planModel.catalogId = catalogId
      if (!option) return
      const previous = this.disinfectants.find((item) => item.recommended_concentration === this.planModel.concentration)
      const untouched = !this.planModel.concentration.trim()
        || (previous !== undefined && previous.recommended_concentration === this.planModel.concentration)
      if (untouched && option.recommended_concentration) this.planModel.concentration = option.recommended_concentration
      if (this.planModel.methodCode && option.method_codes?.length && !option.method_codes.includes(this.planModel.methodCode)) {
        this.planModel.methodCode = ''
      }
    },
    schedulePlanPreview(): void {
      const seq = ++this.planPreviewSeq
      window.setTimeout(() => {
        if (seq !== this.planPreviewSeq) return
        void this.requestPlanPreview()
      }, 300)
    },
    async requestPlanPreview(): Promise<void> {
      const model = this.planModel
      const cycle = Number(model.cycleDays)
      if (!model.penIds.length || !Number.isInteger(cycle) || cycle < 1 || !model.startDate || !model.endDate) {
        this.planPreview = null
        return
      }
      this.planPreviewLoading = true
      try {
        this.planPreview = await healthApi.previewDisinfectPlan({
          pen_ids: model.penIds,
          cycle_days: cycle,
          start_date: model.startDate,
          end_date: model.endDate,
          ...(this.planMode === 'edit' && this.planEditingId ? { plan_id: this.planEditingId } : {})
        })
      } catch {
        this.planPreview = null
      } finally {
        this.planPreviewLoading = false
      }
    },
    validatePlan(): Record<string, string> {
      const errors: Record<string, string> = {}
      const model = this.planModel
      if (!model.penIds.length) errors.pen_ids = '请至少选择 1 个栏舍'
      const cycle = Number(model.cycleDays)
      if (!Number.isInteger(cycle) || cycle < 1 || cycle > 365) errors.cycle_days = '周期天数需为大于 0 的整数'
      const skipPast = this.planMode === 'edit' && !!this.planDetail?.start_date
        && this.planDetail.start_date < todayISO()
        && this.planDetail.start_date === model.startDate
      if (!model.startDate) errors.start_date = '请选择开始日期'
      else if (!skipPast && model.startDate < todayISO()) errors.start_date = '开始日期不能早于今天'
      if (!model.endDate) errors.end_date = '请选择结束日期'
      else if (model.startDate && model.endDate < model.startDate) errors.end_date = '结束日期不能早于开始日期'
      else if (model.startDate) {
        const days = (new Date(model.endDate).getTime() - new Date(model.startDate).getTime()) / 86400000
        if (days > 366) errors.end_date = '起止日期跨度不能超过 366 天'
      }
      if (!model.catalogId) errors.catalog_id = '请选择消毒剂'
      if (!isValidConcentration(model.concentration)) errors.concentration = '请按 1:500 的格式填写浓度'
      if (!model.methodCode) errors.method_code = '请选择消毒方式'
      return errors
    },
    async submitPlan(): Promise<void> {
      if (this.planSubmitting) return
      const errors = this.validatePlan()
      this.planErrors = errors
      if (Object.keys(errors).length) return
      this.planSubmitting = true
      try {
        const payload = {
          pen_ids: this.planModel.penIds,
          cycle_days: Number(this.planModel.cycleDays),
          start_date: this.planModel.startDate,
          end_date: this.planModel.endDate,
          catalog_id: this.planModel.catalogId,
          concentration: this.planModel.concentration.trim(),
          method_code: this.planModel.methodCode,
          ...(this.planMode === 'edit'
            ? { due_time: this.planDetail?.due_time, version: this.planEditingVersion }
            : {})
        }
        if (this.planMode === 'edit') {
          await healthApi.updateDisinfectPlan(this.planEditingId, payload)
          useUiStore().toast('已保存修改')
        } else {
          const plan = await healthApi.createDisinfectPlan(payload)
          useUiStore().toast(
            plan.toast ||
            (plan.task_count === null || plan.task_count === undefined
              ? '已保存并启用'
              : `已保存并启用，生成 ${plan.task_count} 条消毒任务`)
          )
        }
        this.planDrawer = false
        await this.fetchPlans()
        await this.fetchTasks()
      } catch (error) {
        this.applyPlanError(error)
      } finally {
        this.planSubmitting = false
      }
    },
    askPause(plan: DisinfectPlan): void {
      this.pauseTarget = plan
      this.pauseReason = ''
      this.pauseErrors = {}
      this.pauseOpen = true
    },
    async submitPause(): Promise<void> {
      if (this.pauseSubmitting || !this.pauseTarget) return
      const reason = this.pauseReason.trim()
      if (reason.length > 30) {
        this.pauseErrors = { reason: '暂停原因最多 30 字' }
        return
      }
      this.pauseSubmitting = true
      try {
        await healthApi.pauseDisinfectPlan(Number(this.pauseTarget.id), {
          reason: reason || undefined,
          version: Number(this.pauseTarget.version || 0)
        })
        this.pauseOpen = false
        useUiStore().toast('已暂停计划，暂停期间不生成任务')
        await this.fetchPlans()
        await this.fetchTasks()
      } catch (error) {
        const fields = fieldErrorsOf(error)
        if (Object.keys(fields).length) this.pauseErrors = fields
        useUiStore().toast(errorText(error, '暂停失败，请稍后重试'), 'error')
      } finally {
        this.pauseSubmitting = false
      }
    },
    async resumePlan(plan: DisinfectPlan): Promise<void> {
      try {
        const result = await healthApi.resumeDisinfectPlan(Number(plan.id), Number(plan.version || 0))
        useUiStore().toast(result.next_task_text ? `已启用计划，下次任务 ${result.next_task_text}` : '已启用计划')
        await this.fetchPlans()
        await this.fetchTasks()
      } catch (error) {
        useUiStore().toast(errorText(error, '启用失败，请稍后重试'), 'error')
      }
    },
    // ===== 登记消毒 =====
    async openRegisterTemp(): Promise<void> {
      this.registerKind = 'temp'
      this.registerBackfill = false
      this.registerErrors = {}
      this.registerError = ''
      this.registerModel = emptyRecordModel()
      this.registerModel.recordType = 'EMPTY_PEN'
      this.registerOpen = true
      this.registerLoading = true
      await Promise.all([this.loadOptions(), this.loadTaskOptions(), this.loadPens()])
      this.registerLoading = false
    },
    async openRegisterForTask(task: DisinfectTask, backfill = false): Promise<void> {
      this.registerKind = 'register'
      this.registerBackfill = backfill
      this.registerErrors = {}
      this.registerError = ''
      this.registerModel = emptyRecordModel()
      this.registerOpen = true
      this.registerLoading = true
      await Promise.all([this.loadOptions(), this.loadTaskOptions(), this.loadPens()])
      const option = this.taskOptions.find((item) => Number(item.task_id) === Number(task.id))
      if (option) {
        await this.selectRegisterTask(Number(option.task_id))
      } else {
        this.registerModel.recordType = 'PLAN_TASK'
        this.registerModel.taskId = Number(task.id)
        this.registerModel.penId = Number(task.pen_id || 0)
        this.registerModel.concentration = task.concentration || ''
        if (task.pen_id) void this.loadExecutors(Number(task.pen_id))
      }
      this.registerLoading = false
    },
    async loadTaskOptions(): Promise<void> {
      this.taskOptionsLoading = true
      try {
        this.taskOptions = await healthApi.disinfectTaskOptions()
      } catch {
        this.taskOptions = []
      } finally {
        this.taskOptionsLoading = false
      }
    },
    async setRegisterType(type: RecordType): Promise<void> {
      if (this.registerModel.recordType === type) return
      this.registerModel.recordType = type
      this.registerModel.taskId = 0
      this.registerModel.penId = 0
      this.registerModel.drugId = 0
      this.registerModel.lotId = 0
      this.registerModel.concentration = ''
      this.registerModel.methodCode = ''
      this.registerModel.concentrationConfirmed = false
      this.registerErrors = {}
      this.lots = null
      if (type === 'PLAN_TASK' && !this.taskOptions.length) await this.loadTaskOptions()
    },
    async selectRegisterTask(taskId: number): Promise<void> {
      const task = this.taskOptions.find((item) => Number(item.task_id) === taskId)
      if (!task) return
      this.registerModel.taskId = taskId
      this.registerModel.recordType = 'PLAN_TASK'
      this.registerModel.penId = Number(task.pen_id || 0)
      this.registerModel.drugId = Number(task.drug_id || 0)
      this.registerModel.lotId = 0
      this.registerModel.concentration = task.plan_concentration || ''
      this.registerModel.concentrationConfirmed = false
      this.registerModel.methodCode = task.method_code || ''
      this.registerErrors = {}
      this.lots = null
      if (task.default_executor_id) this.registerModel.executorId = Number(task.default_executor_id)
      void this.loadExecutors(Number(task.pen_id || 0))
      if (task.drug_id) await this.loadRegisterLots(Number(task.default_lot_id || 0))
    },
    async selectRegisterPen(penId: number): Promise<void> {
      this.registerModel.penId = penId
      this.registerModel.executorId = null
      this.registerErrors = {}
      void this.loadExecutors(penId)
    },
    async selectRegisterDisinfectant(drugId: number): Promise<void> {
      const option = this.disinfectants.find((item) => Number(item.drug_id) === drugId)
      this.registerModel.drugId = drugId
      this.registerModel.lotId = 0
      this.lots = null
      if (option?.recommended_concentration && !this.registerModel.concentration) {
        this.registerModel.concentration = option.recommended_concentration
      }
      if (option?.method_codes?.length && !option.method_codes.includes(this.registerModel.methodCode)) {
        this.registerModel.methodCode = option.method_codes[0] || ''
      }
      this.registerModel.concentrationConfirmed = false
      await this.loadRegisterLots()
    },
    async loadRegisterLots(preferredLotId = 0): Promise<void> {
      if (!this.registerModel.drugId) {
        this.lots = null
        return
      }
      this.lotsLoading = true
      try {
        this.lots = await healthApi.drugLots(
          this.registerModel.drugId,
          'DISINFECT',
          Number(this.registerModel.usageQty) > 0 ? Number(this.registerModel.usageQty) : undefined
        )
        const ids = [
          ...(this.lots.selectable || []),
          ...(this.lots.unselectable || [])
        ].map((lot) => Number(lot.lot_id))
        const wanted = Number(preferredLotId || this.registerModel.lotId || this.lots.default_lot_id || 0)
        this.registerModel.lotId = ids.includes(wanted) ? wanted : Number(this.lots.default_lot_id || 0)
      } catch {
        this.lots = null
      } finally {
        this.lotsLoading = false
      }
    },
    async loadExecutors(penId: number): Promise<void> {
      if (!penId || this.staffView) return
      try {
        this.executors = await healthApi.penExecutors(penId)
        if (!this.registerModel.executorId) {
          const member = this.executors.find((item) => item.role === 'STAFF')
            || this.executors.find((item) => item.is_me)
            || this.executors[0]
          if (member?.user_id) this.registerModel.executorId = Number(member.user_id)
        }
      } catch {
        this.executors = []
      }
    },
    setConcentration(value: string): void {
      this.registerModel.concentration = value
      this.registerModel.concentrationConfirmed = false
    },
    confirmConcentration(): void {
      this.registerModel.concentrationConfirmed = true
    },
    validateRegister(): Record<string, string> {
      const errors: Record<string, string> = {}
      const model = this.registerModel
      if (model.recordType === 'PLAN_TASK') {
        if (!model.taskId) {
          errors.task_id = this.taskOptions.length
            ? '请选择消毒任务'
            : '今天没有待执行的消毒任务。可以切换到空栏消毒或紧急消毒临时登记。'
        }
      } else if (!model.penId) {
        errors.pen_id = '请选择栏舍'
      }
      if (!model.actualTime) errors.actual_time = '请选择实际时间'
      else if (model.actualTime > nowInput()) errors.actual_time = '实际时间不能晚于现在'
      const task = this.selectedTask
      if (this.registerBackfill && task?.task_date && model.actualTime.slice(0, 10) < task.task_date) {
        errors.actual_time = `实际时间不能早于任务日期 ${dateText(task.task_date)}`
      }
      if (!model.drugId) errors.drug_id = '请选择消毒剂'
      const lot = this.selectedRegisterLot
      if (model.drugId && (!lot || this.lots?.selectable?.every((item) => Number(item.lot_id) !== Number(model.lotId)))) {
        errors.drug_lot_id = '请选择消毒剂批号'
      }
      const usage = Number(model.usageQty)
      if (!Number.isFinite(usage) || usage <= 0) errors.usage_qty = '用量需大于 0'
      else if (lot?.stock?.value !== null && lot?.stock?.value !== undefined && usage > Number(lot.stock.value)) {
        errors.usage_qty = `不能超过批号 ${lot.lot_no || ''} 结存 ${lot.stock.value} ${lot.stock.unit_text || ''}`
      }
      if (!isValidConcentration(model.concentration)) errors.concentration = '请按 1:500 的格式填写浓度'
      if (!model.methodCode) errors.method_code = '请选择消毒方式'
      if (this.registerConcentrationMismatch && !model.concentrationConfirmed) {
        errors.concentration_confirmed = '请先确认本次浓度'
      }
      return errors
    },
    async submitRegister(): Promise<void> {
      if (this.registerSubmitting) return
      const errors = this.validateRegister()
      this.registerErrors = errors
      if (Object.keys(errors).length) return
      this.registerSubmitting = true
      try {
        const record = await healthApi.createDisinfectRecord({
          record_type: this.registerModel.recordType,
          task_id: this.registerModel.recordType === 'PLAN_TASK' ? this.registerModel.taskId : null,
          pen_id: this.registerModel.recordType === 'PLAN_TASK' ? null : this.registerModel.penId,
          actual_time: toDateTimePayload(this.registerModel.actualTime),
          executor_id: this.staffView ? null : this.registerModel.executorId,
          drug_id: this.registerModel.drugId,
          drug_lot_id: this.registerModel.lotId,
          usage_qty: Number(this.registerModel.usageQty),
          concentration: this.registerModel.concentration.trim(),
          concentration_confirmed: this.registerConcentrationMismatch ? true : undefined,
          method_code: this.registerModel.methodCode,
          file_ids: this.registerModel.fileIds
        })
        useUiStore().toast(
          record.toast ||
          (this.registerModel.recordType === 'EMPTY_PEN'
            ? '已登记空栏消毒'
            : this.registerModel.recordType === 'EMERGENCY'
              ? '已登记紧急消毒'
              : '已登记消毒，待办已自动完成')
        )
        this.registerOpen = false
        await this.fetchTasks()
        await this.fetchPlans()
      } catch (error) {
        this.applyRegisterError(error)
      } finally {
        this.registerSubmitting = false
      }
    },
    // ===== 消毒记录查看 / 修改 =====
    async openRecord(recordId: number, edit = false): Promise<void> {
      this.recordOpen = true
      this.recordLoading = true
      this.recordEdit = edit
      this.recordError = ''
      this.recordErrors = {}
      this.record = null
      this.recordLots = null
      await this.loadOptions()
      try {
        const record = await healthApi.disinfectRecord(recordId)
        this.record = record
        this.recordModel = {
          recordType: record.record_type === 'EMPTY_PEN' || record.record_type === 'EMERGENCY' ? record.record_type : 'PLAN_TASK',
          taskId: Number(record.task_id || 0),
          penId: Number(record.pen_id || 0),
          actualTime: (record.actual_time || '').replace(' ', 'T').slice(0, 16),
          executorId: record.executor_id === null || record.executor_id === undefined ? null : Number(record.executor_id),
          drugId: Number(record.drug_id || 0),
          lotId: Number(record.drug_lot_id || 0),
          usageQty: record.usage_qty === null || record.usage_qty === undefined ? '' : String(record.usage_qty),
          concentration: record.concentration || '',
          concentrationConfirmed: !!record.concentration_confirmed,
          methodCode: record.method_code || '',
          fileIds: (record.photos || []).map((photo) => Number(photo.file_id || 0)).filter((id) => id > 0)
        }
        if (edit) {
          await this.loadRecordLots()
          void this.loadRecordExecutors(Number(record.pen_id || 0))
        }
      } catch (error) {
        this.recordError = errorText(error, '消毒记录加载失败')
      } finally {
        this.recordLoading = false
      }
    },
    async loadRecordLots(): Promise<void> {
      if (!this.recordModel.drugId) return
      this.recordLotsLoading = true
      try {
        this.recordLots = await healthApi.drugLots(
          this.recordModel.drugId,
          'DISINFECT',
          Number(this.recordModel.usageQty) > 0 ? Number(this.recordModel.usageQty) : undefined
        )
      } catch {
        this.recordLots = null
      } finally {
        this.recordLotsLoading = false
      }
    },
    async loadRecordExecutors(penId: number): Promise<void> {
      if (!penId || this.staffView) return
      try {
        this.recordExecutors = await healthApi.penExecutors(penId)
      } catch {
        this.recordExecutors = []
      }
    },
    async submitRecordEdit(): Promise<void> {
      if (!this.record || this.recordSubmitting) return
      const errors: Record<string, string> = {}
      const model = this.recordModel
      if (!model.actualTime) errors.actual_time = '请选择实际时间'
      else if (model.actualTime > nowInput()) errors.actual_time = '实际时间不能晚于现在'
      const usage = Number(model.usageQty)
      if (!Number.isFinite(usage) || usage <= 0) errors.usage_qty = '用量需大于 0'
      if (!isValidConcentration(model.concentration)) errors.concentration = '请按 1:500 的格式填写浓度'
      if (!model.methodCode) errors.method_code = '请选择消毒方式'
      const planConcentration = this.record.plan_concentration
      const mismatch = !!planConcentration && !sameConcentration(model.concentration, planConcentration)
      if (mismatch && !model.concentrationConfirmed) errors.concentration_confirmed = '请先确认本次浓度'
      this.recordErrors = errors
      if (Object.keys(errors).length) return
      this.recordSubmitting = true
      try {
        const updated = await healthApi.updateDisinfectRecord(Number(this.record.id), {
          actual_time: toDateTimePayload(model.actualTime),
          executor_id: this.staffView ? null : model.executorId,
          drug_lot_id: model.lotId,
          usage_qty: usage,
          concentration: model.concentration.trim(),
          concentration_confirmed: mismatch ? true : undefined,
          method_code: model.methodCode,
          file_ids: model.fileIds,
          version: Number(this.record.version || 0)
        })
        this.record = updated
        this.recordEdit = false
        useUiStore().toast('已保存修改')
        await this.fetchTasks()
      } catch (error) {
        const fields = fieldErrorsOf(error)
        if (Object.keys(fields).length) this.recordErrors = fields
        useUiStore().toast(errorText(error, '保存失败，请稍后重试'), 'error')
      } finally {
        this.recordSubmitting = false
      }
    },
    async loadLogs(recordId: number, title: string): Promise<void> {
      this.logOpen = true
      this.logLoading = true
      this.logError = ''
      this.logTitle = title
      this.logs = []
      try {
        this.logs = await healthApi.recordLogs('DISINFECT_RECORD', recordId)
      } catch (error) {
        this.logError = errorText(error, '修改记录加载失败')
      } finally {
        this.logLoading = false
      }
    },
    openLogsFor(record: DisinfectRecord): void {
      void this.loadLogs(Number(record.id), `${record.pen_name || ''} · ${dateText(record.task_date || record.actual_time)}`)
    },
    applyPlanError(error: unknown): void {
      const fields = fieldErrorsOf(error)
      if (Object.keys(fields).length) this.planErrors = fields
      const apiError = error as ApiError
      if (apiError?.message) useUiStore().toast(apiError.message, 'error')
    },
    applyRegisterError(error: unknown): void {
      const fields = fieldErrorsOf(error)
      if (Object.keys(fields).length) this.registerErrors = fields
      const apiError = error as ApiError
      if (apiError?.code === 42203 || apiError?.code === 42201) {
        this.registerErrors = { ...this.registerErrors, drug_lot_id: apiError.message }
        void this.loadRegisterLots()
        return
      }
      if (apiError?.message) useUiStore().toast(apiError.message, 'error')
    }
  }
})
