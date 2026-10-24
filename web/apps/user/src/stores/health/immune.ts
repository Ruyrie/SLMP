import { defineStore } from 'pinia'
import { dayjs, type ApiError } from '@zmt/shared'
import {
  healthApi,
  type BatchOption,
  type DictItem,
  type DrugLotOptions,
  type ExecutorOption,
  type ImmunePlan,
  type ImmunePlanOption,
  type ImmunePlanOverview,
  type ImmuneRecord,
  type ImmuneRegisterForm,
  type PreEntryItem,
  type RecordLog,
  type VaccineOption
} from '@/api/health'
import { useUiStore } from '@/stores/ui'
import {
  batchLabel,
  batchOverviewText,
  changeRequestQuery,
  dateText,
  errorText,
  fieldErrorsOf,
  healthLogItems,
  loadBatchOptions,
  statusForError,
  todayISO,
  type HealthLogItem,
  type PageStatus
} from './shared'

export type ImmuneTab = 'ALL' | 'PENDING' | 'MISSED' | 'POSTPONED' | 'DONE' | 'CANCELLED'
export type RegisterMode = 'create' | 'backfill' | 'edit' | 'view'

export interface RegisterModel {
  planId: number
  vaccineId: number
  lotId: number
  dose: string
  quantity: string
  methodCode: string
  vaccinateDate: string
  executorId: number | null
}

export interface AddModel {
  vaccineId: number
  planMode: 'BY_DAY_AGE' | 'BY_DATE'
  planAgeDay: string
  planDate: string
  dose: string
  methodCode: string
}

export const useImmunePlanStore = defineStore('healthImmunePlan', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    batchLoading: false,
    batchError: '',
    batches: [] as BatchOption[],
    batchId: 0,
    overview: null as ImmunePlanOverview | null,
    list: [] as ImmunePlan[],
    total: 0,
    query: { page: 1, size: 20, status: 'ALL' as ImmuneTab },
    loaded: false,
    staffView: false,
    requestId: 0,
    actionPlanId: 0,
    confirmedVersions: {} as Record<number, number>,

    registerOpen: false,
    registerMode: 'create' as RegisterMode,
    registerLoading: false,
    registerSubmitting: false,
    registerError: '',
    registerForm: null as ImmuneRegisterForm | null,
    registerRecord: null as ImmuneRecord | null,
    registerRecordId: 0,
    registerModel: {
      planId: 0,
      vaccineId: 0,
      lotId: 0,
      dose: '',
      quantity: '',
      methodCode: '',
      vaccinateDate: '',
      executorId: null
    } as RegisterModel,
    registerErrors: {} as Record<string, string>,
    registerLots: null as DrugLotOptions | null,
    registerLotsLoading: false,
    registerVaccines: [] as VaccineOption[],
    registerMethods: [] as DictItem[],
    registerExecutors: [] as ExecutorOption[],
    registerPlanOptions: [] as ImmunePlanOption[],
    registerPlanOptionsLoading: false,

    addOpen: false,
    addMode: 'create' as 'create' | 'edit',
    addSubmitting: false,
    addLoading: false,
    addVaccines: [] as VaccineOption[],
    addMethods: [] as DictItem[],
    addErrors: {} as Record<string, string>,
    addModel: {
      vaccineId: 0,
      planMode: 'BY_DAY_AGE',
      planAgeDay: '',
      planDate: '',
      dose: '',
      methodCode: ''
    } as AddModel,
    addEditingId: 0,
    addEditingVersion: 0,
    addEditingName: '',

    postponeOpen: false,
    postponeTarget: null as ImmunePlan | null,
    postponeSubmitting: false,
    postponeErrors: {} as Record<string, string>,
    postponeModel: { newDate: '', reason: '' },

    deleteOpen: false,
    deleteTarget: null as ImmunePlan | null,
    deleteSubmitting: false,

    logOpen: false,
    logLoading: false,
    logError: '',
    logs: [] as RecordLog[],

    recordLogLoading: false,
    recordLogs: [] as RecordLog[]
  }),
  getters: {
    canManage(state): boolean {
      return !!state.overview?.can_manage && !state.overview?.batch_ended
    },
    batchEnded(state): boolean {
      return !!state.overview?.batch_ended
    },
    currentBatch(state): BatchOption | null {
      return state.batches.find((batch) => Number(batch.batch_id) === state.batchId) || null
    },
    batchItems(state): Array<{ title: string; value: number }> {
      return state.batches.map((batch) => ({
        title: batchLabel(batch),
        value: Number(batch.batch_id || 0)
      }))
    },
    counts(state): Record<ImmuneTab, number> {
      const counts = state.overview?.counts || {}
      return {
        ALL: Number(counts.all ?? 0),
        PENDING: Number(counts.pending ?? 0),
        MISSED: Number(counts.missed ?? 0),
        POSTPONED: Number(counts.postponed ?? 0),
        DONE: Number(counts.done ?? 0),
        CANCELLED: Number(counts.cancelled ?? 0)
      }
    },
    tabSegments(): Array<{ label: string; value: ImmuneTab; count: number }> {
      const counts = this.counts
      return [
        { label: '全部', value: 'ALL' as ImmuneTab, count: counts.ALL },
        { label: '待接种', value: 'PENDING' as ImmuneTab, count: counts.PENDING },
        { label: '漏免', value: 'MISSED' as ImmuneTab, count: counts.MISSED },
        { label: '已延期', value: 'POSTPONED' as ImmuneTab, count: counts.POSTPONED },
        { label: '已接种', value: 'DONE' as ImmuneTab, count: counts.DONE },
        { label: '已取消', value: 'CANCELLED' as ImmuneTab, count: counts.CANCELLED }
      ]
    },
    preEntryItems(state): PreEntryItem[] {
      if (!this.canManage) return []
      return state.overview?.pre_entry_items || []
    },
    preEntryCount(state): number {
      return Number(state.overview?.pre_entry_pending_count ?? (state.overview?.pre_entry_items || []).length)
    },
    showPreEntry(): boolean {
      return this.query.status === 'ALL' && this.canManage && this.preEntryItems.length > 0
    },
    summarySub(): string {
      const base = `${this.total} 项 · 按计划日期排列`
      return this.query.status === 'ALL'
        ? base
        : `${base}（${this.tabSegments.find((tab) => tab.value === this.query.status)?.label || ''}）`
    },
    batchOverview(): string {
      return batchOverviewText(this.currentBatch)
    },
    logItems(state): HealthLogItem[] {
      return healthLogItems(state.logs, 'IMMUNE_PLAN')
    },
    recordLogItems(state): HealthLogItem[] {
      return healthLogItems(state.recordLogs, 'IMMUNE_RECORD')
    },
    registerNeedQty(state): number | null {
      const dose = Number(state.registerModel.dose)
      const quantity = Number(state.registerModel.quantity)
      if (!Number.isFinite(dose) || !Number.isFinite(quantity) || dose <= 0 || quantity <= 0) return null
      return Math.round(dose * quantity * 1000) / 1000
    },
    registerPlan(state): ImmunePlanOption | null {
      return state.registerForm?.plan || null
    },
    registerCurrentCount(state): number {
      return Number(state.registerForm?.current_count ?? state.overview?.current_count ?? 0)
    },
    registerLotItems(state): Array<{ title: string; value: number; subtitle: string; disabled: boolean }> {
      const options = state.registerLots
      if (!options) return []
      const toItem = (lot: NonNullable<DrugLotOptions['selectable']>[number], disabled: boolean) => {
        const stock = lot.stock?.value
        const stockText = stock === null || stock === undefined ? '' : ` · 结存 ${stock} ${lot.stock?.unit_text || ''}`
        const expire = lot.expire_date ? `有效期至 ${lot.expire_date}` : ''
        const tag = lot.lot_status === 'EXPIRED'
          ? '已过期'
          : lot.lot_status === 'RECALLED' || lot.lot_status === 'BANNED'
            ? '已停用'
            : ''
        const price = lot.avg_price !== null && lot.avg_price !== undefined ? ` · 单价 ${lot.avg_price} 元` : ''
        return {
          title: String(lot.lot_no || ''),
          value: Number(lot.lot_id || 0),
          subtitle: `${expire}${stockText}${price}${tag ? ` · ${tag}` : ''}${disabled ? ' · 只能报废' : ''}`,
          disabled
        }
      }
      return [
        ...(options.selectable || []).map((lot) => toItem(lot, false)),
        ...(options.unselectable || []).map((lot) => toItem(lot, true))
      ]
    },
    registerDrugLotOptions(state): NonNullable<DrugLotOptions['selectable']> {
      return [...(state.registerLots?.selectable || []), ...(state.registerLots?.unselectable || [])]
    },
    addVaccineItems(state): Array<{ title: string; value: number; subtitle: string }> {
      return state.addVaccines.map((vaccine) => ({
        title: `${vaccine.name || '疫苗'}${vaccine.no_stock ? '（暂无库存）' : ''}`,
        value: Number(vaccine.drug_id || 0),
        subtitle: `结存 ${vaccine.available_qty ?? 0} ${vaccine.stock_unit || ''}`
      }))
    },
    addSelectedVaccine(state): VaccineOption | null {
      return state.addVaccines.find((item) => Number(item.drug_id) === state.addModel.vaccineId) || null
    },
    addDoseUnit(): string {
      return this.addSelectedVaccine?.dose_unit || '头份 / 头'
    },
    addPlanDateText(state): string {
      const overview = state.overview
      if (!overview?.entry_date) return '—'
      if (state.addModel.planMode === 'BY_DATE') return dateText(state.addModel.planDate)
      const age = Number(state.addModel.planAgeDay)
      if (!Number.isFinite(age) || age <= 0) return '—'
      const entryAge = Number(overview.entry_age_days || 0)
      const date = dayjs(overview.entry_date).add(age - entryAge, 'day')
      return date.isValid() ? dateText(date.format('YYYY-MM-DD')) : '—'
    },
    addAgeAtDateText(state): string {
      const overview = state.overview
      if (!overview?.entry_date) return '—'
      if (state.addModel.planMode === 'BY_DAY_AGE') {
        return state.addModel.planAgeDay ? `届时 ${state.addModel.planAgeDay} 日龄` : '按日龄自动计算'
      }
      const date = state.addModel.planDate
      if (!date) return '按日期自动计算'
      const entryAge = Number(overview.entry_age_days || 0)
      const days = dayjs(date).diff(dayjs(overview.entry_date), 'day')
      return `届时 ${entryAge + days} 日龄`
    },
    addTitle(state): string {
      return state.addMode === 'edit' ? '修改免疫项目' : '添加免疫项目'
    },
    addSub(state): string {
      const overview = state.overview
      if (!overview) return ''
      return `${overview.pen_name || ''} · ${overview.batch_no || ''} · ${overview.breed_name || ''} · ${overview.day_age || 0} 日龄 · 存栏 ${overview.current_count || 0} 头`
    },
    postponeHint(state): string {
      const overview = state.overview
      const newDate = state.postponeModel.newDate
      if (!overview?.entry_date || !newDate) return ''
      const days = dayjs(newDate).diff(dayjs(overview.entry_date), 'day')
      return `届时 ${Number(overview.entry_age_days || 0) + days} 日龄`
    },
    postponeRemindText(state): string {
      const target = state.postponeTarget
      if (!target) return ''
      const responsible = target.responsible_text || ''
      const head = `${target.vaccine_name || ''}${target.immune_round ? ` · ${target.immune_round}` : ''}，原计划 ${dateText(target.plan_date)}。改期后，系统在新日期前 1 天和当天提醒`
      if (!responsible || responsible === '所有者和场长负责') return `${head}所有者和场长。`
      return `${head}${responsible.replace(/负责$/, '')}和你。`
    }
  },
  actions: {
    async load(preferredBatchId?: number): Promise<void> {
      this.loaded = true
      await this.loadBatches(preferredBatchId)
    },
    async loadBatches(preferredBatchId?: number): Promise<void> {
      this.batchLoading = true
      this.batchError = ''
      try {
        const list = await loadBatchOptions()
        this.batches = list
        if (!list.length) {
          this.batchId = 0
          this.overview = null
          this.list = []
          this.total = 0
          this.status = 'empty'
          return
        }
        const preferred = Number(preferredBatchId || this.batchId || 0)
        const found = list.find((batch) => Number(batch.batch_id) === preferred)
        this.batchId = Number((found || list[0]).batch_id || 0)
        await this.loadAll()
      } catch (error) {
        this.batchError = errorText(error, '批次加载失败，请稍后重新加载')
        this.status = statusForError(error)
      } finally {
        this.batchLoading = false
      }
    },
    async reload(): Promise<void> {
      this.batchId = 0
      this.overview = null
      this.list = []
      this.total = 0
      this.query.page = 1
      this.query.status = 'ALL'
      await this.loadBatches()
    },
    async loadAll(): Promise<void> {
      if (!this.batchId) return
      const requestId = ++this.requestId
      if (!this.overview && !this.list.length) this.status = 'loading'
      this.error = ''
      try {
        const [overview, page] = await Promise.all([
          healthApi.immuneOverview(this.batchId),
          healthApi.immunePlans(this.batchId, { ...this.query })
        ])
        if (requestId !== this.requestId) return
        this.overview = overview
        this.list = page.list || []
        this.total = Number(page.total || 0)
        this.status = this.list.length
          ? 'ready'
          : this.query.status === 'ALL'
            ? 'empty'
            : 'filtered_empty'
      } catch (error) {
        if (requestId !== this.requestId) return
        this.status = statusForError(error)
        this.error = errorText(error, '免疫计划加载失败，请稍后重新加载')
      }
    },
    async fetchList(): Promise<void> {
      if (!this.batchId) return
      const requestId = ++this.requestId
      this.error = ''
      try {
        const page = await healthApi.immunePlans(this.batchId, { ...this.query })
        if (requestId !== this.requestId) return
        this.list = page.list || []
        this.total = Number(page.total || 0)
        this.status = this.list.length ? 'ready' : this.query.status === 'ALL' ? 'empty' : 'filtered_empty'
      } catch (error) {
        if (requestId !== this.requestId) return
        this.status = statusForError(error)
        this.error = errorText(error, '免疫计划加载失败，请稍后重新加载')
      }
    },
    async refresh(): Promise<void> {
      await this.loadAll()
    },
    async selectBatch(batchId: number): Promise<void> {
      if (!batchId || batchId === this.batchId) return
      this.batchId = batchId
      this.query.page = 1
      this.query.status = 'ALL'
      this.overview = null
      this.list = []
      this.total = 0
      this.status = 'loading'
      await this.loadAll()
    },
    setPage(page: number): void {
      this.query.page = Math.max(1, Number(page) || 1)
      void this.fetchList()
    },
    setSize(size: number): void {
      this.query.size = Math.max(1, Number(size) || 20)
      this.query.page = 1
      void this.fetchList()
    },
    setTab(status: ImmuneTab): void {
      if (this.query.status === status) return
      this.query.status = status
      this.query.page = 1
      void this.fetchList()
    },
    async confirmPreEntry(item: PreEntryItem): Promise<void> {
      if (this.actionPlanId) return
      this.actionPlanId = Number(item.plan_id || 0)
      try {
        const overview = await healthApi.preEntryConfirm(Number(item.plan_id), item.version)
        this.overview = overview
        const planId = Number(item.plan_id)
        this.confirmedVersions[planId] = Number(item.version || 0) + 1
        useUiStore().toast(`已确认${item.vaccine_name || ''}入栏前已接种`, 'success', '撤销', () => {
          void this.undoPreEntry(planId)
        })
      } catch (error) {
        this.toastError(error, '确认失败，请稍后重试')
      } finally {
        this.actionPlanId = 0
      }
    },
    async undoPreEntry(planId: number): Promise<void> {
      const version = this.confirmedVersions[planId]
      if (version === undefined) return
      try {
        const overview = await healthApi.preEntryUndo(planId, version)
        this.overview = overview
        delete this.confirmedVersions[planId]
        useUiStore().toast('已撤销确认')
      } catch (error) {
        this.toastError(error, '撤销失败，请稍后重试')
      }
    },
    async joinPreEntry(item: PreEntryItem): Promise<void> {
      if (this.actionPlanId) return
      this.actionPlanId = Number(item.plan_id || 0)
      try {
        await healthApi.preEntryJoin(Number(item.plan_id), item.version)
        useUiStore().toast('已加入免疫计划，计划日期为今天')
        await this.refresh()
      } catch (error) {
        this.toastError(error, '加入计划失败，请稍后重试')
      } finally {
        this.actionPlanId = 0
      }
    },
    openRegister(): void {
      this.registerMode = 'create'
      this.registerForm = null
      this.registerRecord = null
      this.registerRecordId = 0
      this.registerErrors = {}
      this.registerError = ''
      this.registerOpen = true
      this.resetRegisterModel()
      void this.loadRegisterOptions()
      void this.loadPlanOptions()
    },
    openRegisterFor(planId: number, backfill = false): void {
      this.registerMode = backfill ? 'backfill' : 'create'
      this.registerOpen = true
      this.registerErrors = {}
      this.registerError = ''
      this.registerForm = null
      this.registerRecord = null
      this.registerRecordId = 0
      this.resetRegisterModel()
      this.registerModel.planId = planId
      void this.loadRegisterOptions()
      void this.loadRegisterForm(planId, backfill)
    },
    async openRecord(recordId: number): Promise<void> {
      this.registerMode = 'view'
      this.registerOpen = true
      this.registerErrors = {}
      this.registerError = ''
      this.registerForm = null
      this.registerRecord = null
      this.registerRecordId = recordId
      this.recordLogs = []
      this.registerLoading = true
      try {
        const record = await healthApi.immuneRecord(recordId)
        this.registerRecord = record
        this.applyRecord(record)
        void this.loadRegisterOptions()
        void this.loadRecordLogs(recordId)
      } catch (error) {
        this.registerError = errorText(error, '接种记录加载失败')
      } finally {
        this.registerLoading = false
      }
    },
    async openRecordEdit(recordId: number): Promise<void> {
      await this.openRecord(recordId)
      if (this.registerError) return
      this.registerMode = 'edit'
      if (this.registerModel.vaccineId) void this.loadRegisterLots()
    },
    applyRecord(record: ImmuneRecord): void {
      this.registerRecordId = Number(record.id || 0)
      this.registerModel = {
        planId: Number(record.plan_id || 0),
        vaccineId: Number(record.drug_id || 0),
        lotId: Number(record.drug_lot_id || 0),
        dose: record.dose === null || record.dose === undefined ? '' : String(record.dose),
        quantity: record.quantity === null || record.quantity === undefined ? '' : String(record.quantity),
        methodCode: record.method_code || '',
        vaccinateDate: record.vaccinate_date || todayISO(),
        executorId: record.executor_id === null || record.executor_id === undefined ? null : Number(record.executor_id)
      }
    },
    resetRegisterModel(): void {
      this.registerModel = {
        planId: 0,
        vaccineId: 0,
        lotId: 0,
        dose: '',
        quantity: '',
        methodCode: '',
        vaccinateDate: todayISO(),
        executorId: null
      }
    },
    async loadRegisterOptions(): Promise<void> {
      if (!this.registerVaccines.length) {
        try {
          this.registerVaccines = await healthApi.vaccines(Number(this.overview?.category_id || 0) || undefined)
        } catch {
          this.registerVaccines = []
        }
      }
      if (!this.registerMethods.length) {
        try {
          this.registerMethods = await healthApi.dict('VACCINATE_METHOD', Number(this.overview?.category_id || 0) || undefined)
        } catch {
          this.registerMethods = []
        }
      }
      if (!this.registerExecutors.length && this.overview?.pen_id && this.canManage) {
        try {
          this.registerExecutors = await healthApi.penExecutors(Number(this.overview.pen_id))
        } catch {
          this.registerExecutors = []
        }
      }
    },
    async loadPlanOptions(): Promise<void> {
      this.registerPlanOptionsLoading = true
      try {
        const page = await healthApi.immunePlanOptions({ page: 1, size: 100 })
        this.registerPlanOptions = page.list || []
      } catch {
        this.registerPlanOptions = []
      } finally {
        this.registerPlanOptionsLoading = false
      }
    },
    async loadRegisterForm(planId: number, backfill = false): Promise<void> {
      this.registerLoading = true
      this.registerError = ''
      try {
        const form = await healthApi.immuneRegisterForm(planId, backfill)
        this.registerForm = form
        this.registerModel = {
          planId,
          vaccineId: Number(form.drug_id || 0),
          lotId: Number(form.default_lot?.lot_id || 0),
          dose: form.dose === null || form.dose === undefined ? '' : String(form.dose),
          quantity: form.quantity_default === null || form.quantity_default === undefined ? '' : String(form.quantity_default),
          methodCode: form.method_code || '',
          vaccinateDate: form.date_default || todayISO(),
          executorId: form.default_executor_id === null || form.default_executor_id === undefined
            ? null
            : Number(form.default_executor_id)
        }
        if (form.no_lot_text) this.registerError = form.no_lot_text
        void this.loadRegisterLots()
        if (this.canManage && this.overview?.pen_id) void this.loadRegisterOptions()
      } catch (error) {
        this.registerForm = null
        this.registerError = errorText(error, '登记表单加载失败')
      } finally {
        this.registerLoading = false
      }
    },
    async selectPlan(planId: number): Promise<void> {
      this.registerModel.planId = planId
      if (!planId) {
        this.registerForm = null
        return
      }
      const plan = this.registerPlanOptions.find((item) => Number(item.plan_id) === planId)
      await this.loadRegisterForm(planId, plan?.status === 'MISSED')
    },
    async loadRegisterLots(): Promise<void> {
      const drugId = this.registerModel.vaccineId
      if (!drugId) {
        this.registerLots = null
        return
      }
      this.registerLotsLoading = true
      try {
        this.registerLots = await healthApi.drugLots(drugId, 'VACCINATE', this.registerNeedQty || undefined)
        const ids = [
          ...(this.registerLots.selectable || []),
          ...(this.registerLots.unselectable || [])
        ].map((lot) => Number(lot.lot_id))
        if (!ids.includes(Number(this.registerModel.lotId))) {
          this.registerModel.lotId = Number(this.registerLots.default_lot_id || 0)
        }
      } catch {
        this.registerLots = null
      } finally {
        this.registerLotsLoading = false
      }
    },
    selectVaccine(vaccineId: number): void {
      this.registerModel.vaccineId = vaccineId
      const vaccine = this.registerVaccines.find((item) => Number(item.drug_id) === vaccineId)
      if (vaccine?.default_method_code) this.registerModel.methodCode = vaccine.default_method_code
      void this.loadRegisterLots()
    },
    validateRegister(): Record<string, string> {
      const errors: Record<string, string> = {}
      if (!this.registerModel.planId && this.registerMode === 'create') errors.plan_id = '请选择免疫计划'
      if (!this.registerModel.vaccineId) errors.drug_id = '请选择疫苗'
      const lotSelectable = (this.registerLots?.selectable || [])
        .some((lot) => Number(lot.lot_id) === Number(this.registerModel.lotId))
      if (this.registerMode !== 'view' && !lotSelectable) {
        errors.drug_lot_id = this.registerForm?.no_lot_text || '请选择库存批号'
      }
      const dose = Number(this.registerModel.dose)
      if (!Number.isFinite(dose) || dose <= 0) errors.dose = '剂量需大于 0'
      const quantity = Number(this.registerModel.quantity)
      if (!Number.isInteger(quantity) || quantity <= 0) errors.quantity = '接种数量需大于 0'
      else if (quantity > this.registerCurrentCount) {
        errors.quantity = `不能超过存栏 ${this.registerCurrentCount} 头`
      }
      if (!this.registerModel.methodCode) errors.method_code = '请选择接种方式'
      const date = this.registerModel.vaccinateDate
      if (!date) errors.vaccinate_date = '请选择接种日期'
      else if (date > todayISO()) errors.vaccinate_date = '接种日期不能晚于今天'
      else if (this.registerForm?.date_min && date < this.registerForm.date_min) {
        errors.vaccinate_date = `接种日期不能早于 ${dateText(this.registerForm.date_min)}`
      }
      return errors
    },
    async submitRegister(): Promise<void> {
      if (this.registerSubmitting) return
      if (this.registerMode === 'view') return
      const errors = this.validateRegister()
      this.registerErrors = errors
      if (Object.keys(errors).length) return
      const mode = this.registerMode
      this.registerSubmitting = true
      try {
        if (mode === 'edit' && this.registerRecordId) {
          await healthApi.updateImmuneRecord(this.registerRecordId, {
            drug_lot_id: this.registerModel.lotId,
            dose: Number(this.registerModel.dose),
            quantity: Number(this.registerModel.quantity),
            method_code: this.registerModel.methodCode,
            vaccinate_date: this.registerModel.vaccinateDate,
            executor_id: this.registerRecord?.executor_id ?? this.registerModel.executorId,
            version: Number(this.registerRecord?.version || 0)
          })
          useUiStore().toast('已保存修改')
        } else {
          const record = await healthApi.createImmuneRecord({
            plan_id: this.registerModel.planId,
            drug_id: this.registerModel.vaccineId,
            drug_lot_id: this.registerModel.lotId,
            dose: Number(this.registerModel.dose),
            quantity: Number(this.registerModel.quantity),
            method_code: this.registerModel.methodCode,
            vaccinate_date: this.registerModel.vaccinateDate,
            executor_id: this.staffView ? null : this.registerModel.executorId
          })
          useUiStore().toast(record.todo_completed === false ? '已登记接种' : '已登记接种，待办已自动完成')
        }
        this.registerOpen = false
        await this.refresh()
      } catch (error) {
        this.applySubmitError(error)
      } finally {
        this.registerSubmitting = false
      }
    },
    openAdd(): void {
      this.addMode = 'create'
      this.addEditingId = 0
      this.addEditingVersion = 0
      this.addEditingName = ''
      this.addErrors = {}
      this.addModel = {
        vaccineId: 0,
        planMode: 'BY_DAY_AGE',
        planAgeDay: '',
        planDate: todayISO(),
        dose: '',
        methodCode: ''
      }
      this.addOpen = true
      void this.loadAddOptions()
    },
    openAddEdit(plan: ImmunePlan): void {
      this.addMode = 'edit'
      this.addEditingId = Number(plan.id || 0)
      this.addEditingVersion = Number(plan.version || 0)
      this.addEditingName = `${plan.vaccine_name || ''}${plan.immune_round ? ` · ${plan.immune_round}` : ''}`
      this.addErrors = {}
      this.addModel = {
        vaccineId: 0,
        planMode: plan.plan_mode === 'BY_DATE' ? 'BY_DATE' : 'BY_DAY_AGE',
        planAgeDay: plan.age_at_plan === null || plan.age_at_plan === undefined ? '' : String(plan.age_at_plan),
        planDate: plan.plan_date || todayISO(),
        dose: plan.dose === null || plan.dose === undefined ? '' : String(plan.dose),
        methodCode: plan.method_code || ''
      }
      this.addOpen = true
      void this.loadAddOptions()
    },
    async loadAddOptions(): Promise<void> {
      this.addLoading = true
      try {
        if (!this.addVaccines.length) {
          this.addVaccines = await healthApi.vaccines(Number(this.overview?.category_id || 0) || undefined)
        }
        if (!this.addMethods.length) {
          this.addMethods = await healthApi.dict('VACCINATE_METHOD', Number(this.overview?.category_id || 0) || undefined)
        }
      } catch {
        /* 选项失败不阻塞表单，提交时给出字段错误 */
      } finally {
        this.addLoading = false
      }
    },
    validateAdd(): Record<string, string> {
      const errors: Record<string, string> = {}
      if (!this.addModel.vaccineId && this.addMode === 'create') errors.drug_id = '请选择疫苗'
      if (this.addMode === 'create') {
        if (this.addModel.planMode === 'BY_DAY_AGE') {
          const age = Number(this.addModel.planAgeDay)
          const dayAge = Number(this.overview?.day_age || 0)
          if (!Number.isInteger(age) || age <= 0) errors.plan_age_day = '请填写接种日龄'
          else if (age < dayAge) errors.plan_age_day = `不能早于批次当前日龄 ${dayAge} 日龄`
          else if (age > 3650) errors.plan_age_day = '接种日龄不能超过 3650'
        } else {
          if (!this.addModel.planDate) errors.plan_date = '请选择计划日期'
          else if (this.addModel.planDate < todayISO()) errors.plan_date = '计划日期不能早于今天'
        }
      }
      const dose = Number(this.addModel.dose)
      if (!Number.isFinite(dose) || dose <= 0) errors.dose = '剂量需大于 0'
      if (!this.addModel.methodCode) errors.method_code = '请选择接种方式'
      return errors
    },
    async submitAdd(continueAdding = false): Promise<void> {
      if (this.addSubmitting) return
      const errors = this.validateAdd()
      this.addErrors = errors
      if (Object.keys(errors).length) return
      this.addSubmitting = true
      try {
        if (this.addMode === 'edit' && this.addEditingId) {
          await healthApi.updateImmunePlan(this.addEditingId, {
            dose: Number(this.addModel.dose),
            method_code: this.addModel.methodCode,
            version: this.addEditingVersion
          })
          useUiStore().toast('已保存修改')
          this.addOpen = false
        } else {
          await healthApi.createImmunePlan(this.batchId, {
            drug_id: this.addModel.vaccineId,
            plan_mode: this.addModel.planMode,
            ...(this.addModel.planMode === 'BY_DAY_AGE'
              ? { plan_age_day: Number(this.addModel.planAgeDay) }
              : { plan_date: this.addModel.planDate }),
            dose: Number(this.addModel.dose),
            method_code: this.addModel.methodCode
          })
          useUiStore().toast(continueAdding ? '已添加，可以继续添加下一项' : '已添加免疫项目')
          if (continueAdding) {
            this.addModel = {
              vaccineId: 0,
              planMode: this.addModel.planMode,
              planAgeDay: '',
              planDate: todayISO(),
              dose: '',
              methodCode: ''
            }
          } else {
            this.addOpen = false
          }
        }
        await this.refresh()
      } catch (error) {
        this.applyAddError(error)
      } finally {
        this.addSubmitting = false
      }
    },
    openPostpone(plan: ImmunePlan): void {
      this.postponeTarget = plan
      const original = plan.plan_date || ''
      this.postponeModel = {
        newDate: original && original >= todayISO() ? original : '',
        reason: ''
      }
      this.postponeErrors = {}
      this.postponeOpen = true
    },
    async submitPostpone(): Promise<void> {
      if (this.postponeSubmitting || !this.postponeTarget) return
      const target = this.postponeTarget
      const errors: Record<string, string> = {}
      if (!this.postponeModel.newDate) errors.new_date = '请选择新日期'
      else if (this.postponeModel.newDate < todayISO()) errors.new_date = '新日期不能早于今天'
      else if (this.postponeModel.newDate === target.plan_date) errors.new_date = '新日期与原计划日期相同'
      if (!this.postponeModel.reason.trim()) errors.reason = '请填写延期原因'
      else if (this.postponeModel.reason.trim().length > 50) errors.reason = '延期原因最多 50 字'
      this.postponeErrors = errors
      if (Object.keys(errors).length) return
      this.postponeSubmitting = true
      try {
        const plan = await healthApi.postponeImmunePlan(Number(target.id), {
          new_date: this.postponeModel.newDate,
          reason: this.postponeModel.reason.trim(),
          version: Number(target.version || 0)
        })
        this.postponeOpen = false
        useUiStore().toast(`已延期到 ${dateText(plan.plan_date || this.postponeModel.newDate)}`)
        await this.refresh()
      } catch (error) {
        this.applyPostponeError(error)
      } finally {
        this.postponeSubmitting = false
      }
    },
    askDelete(plan: ImmunePlan): void {
      this.deleteTarget = plan
      this.deleteOpen = true
    },
    async confirmDelete(): Promise<void> {
      if (this.deleteSubmitting || !this.deleteTarget) return
      this.deleteSubmitting = true
      try {
        await healthApi.deleteImmunePlan(Number(this.deleteTarget.id), Number(this.deleteTarget.version || 0))
        this.deleteOpen = false
        useUiStore().toast('已删除项目')
        await this.refresh()
      } catch (error) {
        this.toastError(error, '删除失败，请稍后重试')
      } finally {
        this.deleteSubmitting = false
      }
    },
    async openLogs(): Promise<void> {
      if (!this.batchId) return
      this.logOpen = true
      this.logLoading = true
      this.logError = ''
      this.logs = []
      try {
        this.logs = await healthApi.recordLogs('IMMUNE_PLAN', this.batchId)
      } catch (error) {
        this.logError = errorText(error, '修改记录加载失败')
      } finally {
        this.logLoading = false
      }
    },
    async loadRecordLogs(recordId: number): Promise<void> {
      this.recordLogLoading = true
      this.recordLogs = []
      try {
        this.recordLogs = await healthApi.recordLogs('IMMUNE_RECORD', recordId)
      } catch {
        this.recordLogs = []
      } finally {
        this.recordLogLoading = false
      }
    },
    changeRequestLink(): Record<string, string> {
      return changeRequestQuery('IMMUNE_RECORD', this.registerRecordId)
    },
    emptyTitle(): string {
      if (this.query.status !== 'ALL') {
        const labels: Record<Exclude<ImmuneTab, 'ALL'>, string> = {
          PENDING: '待接种',
          MISSED: '漏免',
          POSTPONED: '已延期',
          DONE: '已接种',
          CANCELLED: '已取消'
        }
        return `没有${labels[this.query.status]}的项目。`
      }
      if (this.overview && this.overview.has_template === false) return '该品种没有免疫模板'
      return '还没有免疫计划'
    },
    emptyDesc(): string {
      if (this.query.status !== 'ALL') return '换一个状态筛选试试。'
      if (this.overview && this.overview.has_template === false) {
        return '平台暂无该品种的免疫程序模板，系统没有自动生成计划。添加后按计划日期提醒负责员工、所有者和场长。'
      }
      return '批次入栏后按品种的免疫程序模板生成。'
    },
    openChangeRequest(): Record<string, string> {
      return changeRequestQuery('IMMUNE_RECORD', this.registerRecordId)
    },
    applySubmitError(error: unknown): void {
      const fields = fieldErrorsOf(error)
      if (Object.keys(fields).length) {
        const mapped: Record<string, string> = {}
        if (fields.plan_id) mapped.plan_id = fields.plan_id
        if (fields.drug_id) mapped.drug_id = fields.drug_id
        if (fields.drug_lot_id) mapped.drug_lot_id = fields.drug_lot_id
        if (fields.dose) mapped.dose = fields.dose
        if (fields.quantity) mapped.quantity = fields.quantity
        if (fields.method_code) mapped.method_code = fields.method_code
        if (fields.vaccinate_date) mapped.vaccinate_date = fields.vaccinate_date
        this.registerErrors = mapped
      }
      const apiError = error as ApiError
      if (apiError?.code === 42203 || apiError?.code === 42201) {
        this.registerErrors = { ...this.registerErrors, drug_lot_id: apiError.message }
        void this.loadRegisterLots()
        return
      }
      if (apiError?.isForbidden || apiError?.message) useUiStore().toast(apiError.message, 'error')
    },
    applyAddError(error: unknown): void {
      const fields = fieldErrorsOf(error)
      if (Object.keys(fields).length) this.addErrors = fields
      const apiError = error as ApiError
      if (apiError?.message && !Object.keys(fields).length) useUiStore().toast(apiError.message, 'error')
    },
    applyPostponeError(error: unknown): void {
      const fields = fieldErrorsOf(error)
      if (Object.keys(fields).length) this.postponeErrors = fields
      const apiError = error as ApiError
      if (apiError?.message) useUiStore().toast(apiError.message, 'error')
    },
    toastError(error: unknown, fallback: string): void {
      const apiError = error as ApiError
      useUiStore().toast(apiError?.message || fallback, 'error')
    }
  }
})
