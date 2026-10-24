import { defineStore } from 'pinia'
import type { ApiError } from '@zmt/shared'
import {
  healthApi,
  type BatchOption,
  type DeleteImpact,
  type DrugLotOptions,
  type ExecutorOption,
  type MedicationDrugOption,
  type MedicationRecord,
  type MedicationTarget,
  type ReleasePreview,
  type RecordLog,
  type Withdrawal
} from '@/api/health'
import { useUiStore } from '@/stores/ui'
import {
  batchLabel,
  dateText,
  errorText,
  fieldErrorsOf,
  healthLogItems,
  loadBatchOptions,
  monthDay,
  statusForError,
  todayISO,
  type HealthLogItem,
  type PageStatus
} from './shared'

export type MedicationFormMode = 'create' | 'edit'

export interface MedicationFormModel {
  targetType: 'BATCH' | 'PEN'
  batchId: number
  penId: number
  drugId: number
  lotId: number
  dose: string
  usedQty: string
  courseDays: string
  firstDate: string
  overDoseNote: string
  usageNote: string
  reason: string
  executorId: number | null
  reportId: number | null
}

function emptyModel(): MedicationFormModel {
  return {
    targetType: 'BATCH',
    batchId: 0,
    penId: 0,
    drugId: 0,
    lotId: 0,
    dose: '',
    usedQty: '',
    courseDays: '',
    firstDate: todayISO(),
    overDoseNote: '',
    usageNote: '',
    reason: '',
    executorId: null,
    reportId: null
  }
}

export const useMedicationStore = defineStore('healthMedication', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    batchLoading: false,
    batchError: '',
    batches: [] as BatchOption[],
    batchId: 0,
    withdrawal: null as Withdrawal | null,
    records: [] as MedicationRecord[],
    total: 0,
    query: { page: 1, size: 20 },
    loaded: false,
    staffView: false,
    requestId: 0,

    formOpen: false,
    formMode: 'create' as MedicationFormMode,
    formLoading: false,
    submitting: false,
    formError: '',
    model: emptyModel(),
    errors: {} as Record<string, string>,
    targetsBatch: [] as MedicationTarget[],
    targetsPen: [] as MedicationTarget[],
    targetsLoading: false,
    drugs: [] as MedicationDrugOption[],
    bannedHint: '',
    drugsLoading: false,
    lots: null as DrugLotOptions | null,
    lotsLoading: false,
    executors: [] as ExecutorOption[],
    preview: null as ReleasePreview | null,
    previewLoading: false,
    previewSeq: 0,
    editingId: 0,
    editingVersion: 0,

    deleteOpen: false,
    deleteTarget: null as MedicationRecord | null,
    deleteImpact: null as DeleteImpact | null,
    deleteImpactLoading: false,
    deleteSubmitting: false,
    deleteErrors: {} as Record<string, string>,

    notifyOpen: false,
    notifyTarget: null as MedicationRecord | null,
    notifySubmitting: false,

    logOpen: false,
    logLoading: false,
    logError: '',
    logTargetTitle: '',
    logs: [] as RecordLog[],

    detail: null as MedicationRecord | null
  }),
  getters: {
    currentBatch(state): BatchOption | null {
      return state.batches.find((batch) => Number(batch.batch_id) === state.batchId) || null
    },
    batchItems(state): Array<{ title: string; value: number }> {
      return state.batches.map((batch) => ({ title: batchLabel(batch), value: Number(batch.batch_id || 0) }))
    },
    batchOverview(): string {
      const batch = this.currentBatch
      if (!batch) return ''
      return `存栏 ${batch.current_count || 0} ${batch.unit_text || '头'}`
    },
    withdrawalKind(state): 'NONE' | 'IN_WITHDRAWAL' | 'ENDED' {
      const status = state.withdrawal?.status
      return status === 'IN_WITHDRAWAL' || status === 'ENDED' ? status : 'NONE'
    },
    withdrawalText(state): string {
      if (this.withdrawalKind === 'IN_WITHDRAWAL') return `休药中 · 剩 ${state.withdrawal?.days_left || 0} 天`
      if (this.withdrawalKind === 'ENDED') return '已到期'
      return '暂无用药记录'
    },
    withdrawalNote(state): string {
      if (this.withdrawalKind === 'IN_WITHDRAWAL') {
        const remind = state.staffView ? '' : state.withdrawal?.remind_text || ''
        return `休药期内不能登记出栏。${remind}`.trim()
      }
      if (this.withdrawalKind === 'ENDED') return '休药期已结束，可以登记出栏。'
      return '这个批次没有休药限制，可以登记出栏。'
    },
    withdrawalProgress(state): number {
      if (this.withdrawalKind === 'ENDED') return 100
      const value = Number(state.withdrawal?.progress || 0)
      return Math.max(0, Math.min(100, Math.round(value * 100)))
    },
    recordsSub(): string {
      return `${this.total} 条 · 按首次用药日期倒序`
    },
    selectedTarget(): MedicationTarget | null {
      const model = this.model
      if (model.targetType === 'BATCH') {
        return this.targetsBatch.find((item) => Number(item.batch_id) === model.batchId) || null
      }
      return this.targetsPen.find((item) => Number(item.pen_id) === model.penId) || null
    },
    targetHelper(): string {
      const target = this.selectedTarget
      if (!target) return ''
      if (this.model.targetType === 'BATCH') return `存栏 ${target.current_count || 0} 头`
      if (!target.batches?.length) return `${target.pen_name || '栏舍'}没有在栏批次，不能登记用药`
      return target.batches.map((batch) => `${batch.batch_no || ''}（${batch.stock_count || 0} 头）`).join('、')
    },
    batchTargetItems(state): Array<{ title: string; value: number; subtitle: string }> {
      return state.targetsBatch.map((item) => ({
        title: `${item.batch_no || ''} · ${item.pen_name || ''}`,
        value: Number(item.batch_id || 0),
        subtitle: `存栏 ${item.current_count || 0} 头`
      }))
    },
    penTargetItems(state): Array<{ title: string; value: number; subtitle: string }> {
      return state.targetsPen.map((item) => ({
        title: item.pen_name || '',
        value: Number(item.pen_id || 0),
        subtitle: item.batches?.length
          ? `在栏批次 ${item.batches.map((batch) => `${batch.batch_no || ''}（${batch.stock_count || 0} 头）`).join('、')}`
          : '没有在栏批次'
      }))
    },
    drugItems(state): Array<{ title: string; value: number; subtitle: string }> {
      return state.drugs.map((drug) => ({
        title: drug.drug_name || '',
        value: Number(drug.drug_id || 0),
        subtitle: `${drug.hint_text || ''}${drug.available_qty !== null && drug.available_qty !== undefined ? ` · 结存 ${drug.available_qty} ${drug.stock_unit || ''}` : ''}`
      }))
    },
    selectedDrug: (state): MedicationDrugOption | null =>
      state.drugs.find((drug) => Number(drug.drug_id) === state.model.drugId) || null,
    lotItems(state): Array<{ title: string; value: number; subtitle: string; disabled: boolean }> {
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
          lot.avg_price !== null && lot.avg_price !== undefined ? `单价 ${lot.avg_price} 元` : '',
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
    selectedLot: (state) => {
      const options = state.lots
      if (!options || !state.model.lotId) return null
      return [...(options.selectable || []), ...(options.unselectable || [])]
        .find((lot) => Number(lot.lot_id) === Number(state.model.lotId)) || null
    },
    lotHelper(): string {
      const lot = this.selectedLot
      if (!lot) return '默认取有效期最早的批号'
      const parts = [lot.expire_date ? `${lot.expire_date} 到期` : '']
      if (lot.priority_out) parts.push('先到期先出')
      return parts.filter(Boolean).join(' · ')
    },
    doseHelper(): string {
      const drug = this.selectedDrug
      if (!drug || drug.recommended_dose === null || drug.recommended_dose === undefined) return ''
      return `推荐 ${drug.recommended_dose} ${drug.dose_unit || ''}`
    },
    lastDateText(): string {
      const days = Number(this.model.courseDays)
      if (!this.model.firstDate || !Number.isInteger(days) || days < 1) return ''
      const date = new Date(`${this.model.firstDate}T00:00:00`)
      date.setDate(date.getDate() + days - 1)
      const iso = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
      return `末次 ${monthDay(iso)}`
    },
    overDose(): boolean {
      if (this.preview?.over_dose !== null && this.preview?.over_dose !== undefined) return !!this.preview.over_dose
      const drug = this.selectedDrug
      const dose = Number(this.model.dose)
      return !!drug?.recommended_dose && Number.isFinite(dose) && dose > Number(drug.recommended_dose)
    },
    categoryMismatch(): boolean {
      if (this.preview?.category_mismatch !== null && this.preview?.category_mismatch !== undefined) {
        return !!this.preview.category_mismatch
      }
      return this.selectedDrug ? this.selectedDrug.applicable === false : false
    },
    releaseText(): string {
      return this.preview?.ready ? this.preview.release_text || '' : ''
    },
    usedHelper(): string {
      const unit = this.selectedDrug?.stock_unit || ''
      return unit ? `按${unit}填写，登记后扣减库存` : '按记账单位填写，登记后扣减库存'
    },
    executorItems(state): Array<{ title: string; value: number }> {
      return state.executors.map((member) => ({
        title: member.display_name || member.name || '',
        value: Number(member.user_id || 0)
      }))
    },
    formTitle(state): string {
      return state.formMode === 'edit' ? '修改用药记录' : '登记用药'
    },
    formSub(state): string {
      return state.formMode === 'edit' ? '保存后重新计算批次的可出栏日期' : '提交后重新计算批次的可出栏日期'
    },
    deleteMode(): 'direct' | 'request' {
      return this.deleteImpact?.impact === 'NO_CHANGE_TODAY' ? 'direct' : 'request'
    },
    deleteBody(): string {
      const impact = this.deleteImpact
      if (!impact) return ''
      if (impact.impact === 'ADVANCE') {
        return '这项修改会使可出栏日期提前，需要提交修改申请，由管理员审核。审核通过前，记录和可出栏日期保持不变。'
      }
      if (impact.impact === 'LOCKED') {
        return '批次已出栏，记录已锁定，需要提交修改申请，由管理员审核。审核通过前，记录保持不变。'
      }
      if (impact.impact === 'NO_CHANGE_EARLIER') {
        return '删除会影响库存和批次成本，需要提交修改申请，由管理员审核。审核通过前，记录保持不变。'
      }
      return `删除后可出栏日期不变，仍为 ${dateText(impact.release_date_before)}。`
    },
    deleteSubject(): string {
      const impact = this.deleteImpact
      if (impact?.record_title) return impact.record_title
      const record = this.deleteTarget
      return record ? `${record.drug_name || ''} · ${monthDay(record.first_date)}–${monthDay(record.last_date)}` : ''
    },
    releaseChangeText(): { before: string; after: string } {
      return {
        before: dateText(this.deleteImpact?.release_date_before),
        after: dateText(this.deleteImpact?.release_date_after)
      }
    },
    notifyText(): string {
      const impact = this.deleteImpact
      if (impact?.impact === 'ADVANCE') {
        return `删除会使可出栏日期提前（${dateText(impact.release_date_before)} → ${dateText(impact.release_date_after)}），需要由所有者或场长提交修改申请。`
      }
      return '这条用药记录需要由所有者或场长提交修改申请。'
    },
    notifyName(): string {
      return this.deleteImpact?.notify_name || '场长'
    },
    logItems(state): HealthLogItem[] {
      return healthLogItems(state.logs, 'MEDICATION_RECORD')
    }
  },
  actions: {
    async load(): Promise<void> {
      this.loaded = true
      await this.loadBatches()
    },
    async loadBatches(): Promise<void> {
      this.batchLoading = true
      this.batchError = ''
      try {
        const list = await loadBatchOptions()
        this.batches = list
        if (!list.length) {
          this.batchId = 0
          this.withdrawal = null
          this.records = []
          this.total = 0
          this.status = 'empty'
          return
        }
        const found = list.find((batch) => Number(batch.batch_id) === this.batchId)
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
      this.withdrawal = null
      this.records = []
      this.total = 0
      this.query.page = 1
      await this.loadBatches()
    },
    async loadAll(): Promise<void> {
      if (!this.batchId) return
      const requestId = ++this.requestId
      if (!this.withdrawal && !this.records.length) this.status = 'loading'
      this.error = ''
      try {
        const [withdrawal, page] = await Promise.all([
          healthApi.withdrawal(this.batchId),
          healthApi.medicationRecords(this.batchId, { ...this.query })
        ])
        if (requestId !== this.requestId) return
        this.withdrawal = withdrawal
        this.records = page.list || []
        this.total = Number(page.total || 0)
        this.status = 'ready'
      } catch (error) {
        if (requestId !== this.requestId) return
        this.status = statusForError(error)
        this.error = errorText(error, '用药档案加载失败，请稍后重新加载')
      }
    },
    async fetchRecords(): Promise<void> {
      if (!this.batchId) return
      const requestId = ++this.requestId
      try {
        const page = await healthApi.medicationRecords(this.batchId, { ...this.query })
        if (requestId !== this.requestId) return
        this.records = page.list || []
        this.total = Number(page.total || 0)
        this.status = 'ready'
      } catch (error) {
        if (requestId !== this.requestId) return
        this.status = statusForError(error)
        this.error = errorText(error, '用药记录加载失败')
      }
    },
    async selectBatch(batchId: number): Promise<void> {
      if (!batchId || batchId === this.batchId) return
      this.batchId = batchId
      this.query.page = 1
      this.withdrawal = null
      this.records = []
      this.total = 0
      this.status = 'loading'
      await this.loadAll()
    },
    setPage(page: number): void {
      this.query.page = Math.max(1, Number(page) || 1)
      void this.fetchRecords()
    },
    setSize(size: number): void {
      this.query.size = Math.max(1, Number(size) || 20)
      this.query.page = 1
      void this.fetchRecords()
    },
    async openCreate(): Promise<void> {
      this.formMode = 'create'
      this.editingId = 0
      this.editingVersion = 0
      this.detail = null
      this.formOpen = true
      this.formLoading = true
      this.submitting = false
      this.errors = {}
      this.formError = ''
      this.preview = null
      this.bannedHint = ''
      this.drugs = []
      this.lots = null
      this.executors = []
      this.model = emptyModel()
      this.model.batchId = this.batchId
      this.model.targetType = 'BATCH'
      await this.loadTargets()
      const batchTarget = this.targetsBatch.find((item) => Number(item.batch_id) === this.batchId)
      if (batchTarget?.pen_id) {
        this.model.penId = Number(batchTarget.pen_id)
        void this.loadExecutors(Number(batchTarget.pen_id))
        this.model.executorId = null
      }
      await this.loadDrugs()
      this.formLoading = false
    },
    async openEdit(record: MedicationRecord): Promise<void> {
      this.formMode = 'edit'
      this.editingId = Number(record.id || 0)
      this.editingVersion = Number(record.version || 0)
      this.formOpen = true
      this.formLoading = true
      this.submitting = false
      this.errors = {}
      this.formError = ''
      this.preview = null
      this.model = emptyModel()
      this.model.targetType = record.target_type === 'PEN' ? 'PEN' : 'BATCH'
      this.model.batchId = Number(record.batch_id || 0)
      this.model.penId = Number(record.pen_id || 0)
      await Promise.all([this.loadTargets(), this.loadDrugs()])
      try {
        const detail = await healthApi.medicationRecord(Number(record.id))
        this.detail = detail
        this.model = {
          targetType: detail.target_type === 'PEN' ? 'PEN' : 'BATCH',
          batchId: Number(detail.batch_id || 0),
          penId: Number(detail.pen_id || 0),
          drugId: Number(detail.drug_id || 0),
          lotId: Number(detail.drug_lot_id || 0),
          dose: detail.dose === null || detail.dose === undefined ? '' : String(detail.dose),
          usedQty: detail.used_qty === null || detail.used_qty === undefined ? '' : String(detail.used_qty),
          courseDays: detail.course_days === null || detail.course_days === undefined ? '' : String(detail.course_days),
          firstDate: detail.first_date || todayISO(),
          overDoseNote: detail.over_dose_note || '',
          usageNote: detail.usage_note || '',
          reason: detail.reason || '',
          executorId: detail.executor_id === null || detail.executor_id === undefined ? null : Number(detail.executor_id),
          reportId: detail.report_id === null || detail.report_id === undefined ? null : Number(detail.report_id)
        }
        this.editingVersion = Number(detail.version || this.editingVersion)
        if (this.model.penId) void this.loadExecutors(this.model.penId)
        await this.loadLots()
        this.schedulePreview()
      } catch (error) {
        this.formError = errorText(error, '用药记录加载失败')
      } finally {
        this.formLoading = false
      }
    },
    async loadTargets(): Promise<void> {
      this.targetsLoading = true
      try {
        const [batches, pens] = await Promise.all([
          healthApi.medicationTargets({ target_type: 'BATCH' }),
          healthApi.medicationTargets({ target_type: 'PEN' })
        ])
        this.targetsBatch = batches || []
        this.targetsPen = pens || []
      } catch {
        this.targetsBatch = []
        this.targetsPen = []
      } finally {
        this.targetsLoading = false
      }
    },
    async loadExecutors(penId: number): Promise<void> {
      if (!penId || this.staffView) return
      try {
        this.executors = await healthApi.penExecutors(penId)
        if (this.formMode === 'create' && !this.model.executorId) {
          const member = this.executors.find((item) => item.role === 'STAFF')
            || this.executors.find((item) => item.is_me)
            || this.executors[0]
          if (member?.user_id) this.model.executorId = Number(member.user_id)
        }
      } catch {
        this.executors = []
      }
    },
    async loadDrugs(): Promise<void> {
      this.drugsLoading = true
      try {
        const params = this.model.targetType === 'BATCH'
          ? { batch_id: this.model.batchId }
          : { pen_id: this.model.penId }
        const result = await healthApi.medicationDrugOptions(params)
        this.drugs = result.list || []
        this.bannedHint = result.banned_hint || ''
      } catch (error) {
        this.drugs = []
        this.bannedHint = ''
        this.formError = errorText(error, '药品加载失败')
      } finally {
        this.drugsLoading = false
      }
    },
    async setTargetType(type: 'BATCH' | 'PEN'): Promise<void> {
      if (this.model.targetType === type) return
      this.model.targetType = type
      this.model.drugId = 0
      this.model.lotId = 0
      this.lots = null
      this.preview = null
      this.errors = {}
      if (type === 'BATCH') this.model.penId = Number(this.selectedTarget?.pen_id || 0)
      await this.loadDrugs()
      if (this.model.penId) void this.loadExecutors(this.model.penId)
    },
    async selectTarget(id: number): Promise<void> {
      if (this.model.targetType === 'BATCH') {
        this.model.batchId = id
        this.model.penId = Number(this.targetsBatch.find((item) => Number(item.batch_id) === id)?.pen_id || 0)
      } else {
        this.model.penId = id
      }
      this.model.drugId = 0
      this.model.lotId = 0
      this.lots = null
      this.preview = null
      this.errors = {}
      if (this.model.penId) void this.loadExecutors(this.model.penId)
      await this.loadDrugs()
    },
    async selectDrug(drugId: number): Promise<void> {
      this.model.drugId = drugId
      this.errors = {}
      const drug = this.drugs.find((item) => Number(item.drug_id) === drugId)
      if (drug) {
        if (drug.recommended_dose !== null && drug.recommended_dose !== undefined) {
          this.model.dose = String(drug.recommended_dose)
        }
        if (drug.recommended_course_days !== null && drug.recommended_course_days !== undefined) {
          this.model.courseDays = String(drug.recommended_course_days)
        }
      }
      this.model.lotId = 0
      await this.loadLots()
      this.schedulePreview()
    },
    async loadLots(): Promise<void> {
      if (!this.model.drugId) {
        this.lots = null
        return
      }
      this.lotsLoading = true
      try {
        this.lots = await healthApi.drugLots(
          this.model.drugId,
          'MEDICATE',
          Number(this.model.usedQty) > 0 ? Number(this.model.usedQty) : undefined
        )
        const ids = [
          ...(this.lots.selectable || []),
          ...(this.lots.unselectable || [])
        ].map((lot) => Number(lot.lot_id))
        if (!ids.includes(Number(this.model.lotId)) || !this.model.lotId) {
          this.model.lotId = Number(this.lots.default_lot_id || 0)
        }
      } catch {
        this.lots = null
      } finally {
        this.lotsLoading = false
      }
    },
    schedulePreview(): void {
      const seq = ++this.previewSeq
      window.setTimeout(() => {
        if (seq !== this.previewSeq) return
        void this.requestPreview()
      }, 300)
    },
    async requestPreview(): Promise<void> {
      const model = this.model
      const courseDays = Number(model.courseDays)
      const dose = Number(model.dose)
      const complete = (model.batchId || model.penId) && model.drugId && model.firstDate
        && Number.isInteger(courseDays) && courseDays > 0 && Number.isFinite(dose) && dose > 0
      if (!complete) {
        this.preview = null
        return
      }
      this.previewLoading = true
      try {
        this.preview = await healthApi.medicationPreview({
          target_type: model.targetType,
          batch_id: model.targetType === 'BATCH' ? model.batchId : null,
          pen_id: model.targetType === 'PEN' ? model.penId : null,
          drug_id: model.drugId,
          first_date: model.firstDate,
          course_days: courseDays,
          dose,
          ...(this.formMode === 'edit' && this.editingId ? { exclude_record_id: this.editingId } : {})
        })
      } catch {
        this.preview = null
      } finally {
        this.previewLoading = false
      }
    },
    validate(): Record<string, string> {
      const errors: Record<string, string> = {}
      const model = this.model
      if (model.targetType === 'BATCH' && !model.batchId) errors.batch_id = '请选择批次'
      if (model.targetType === 'PEN' && !model.penId) errors.pen_id = '请选择栏舍'
      if (model.targetType === 'PEN') {
        const target = this.targetsPen.find((item) => Number(item.pen_id) === model.penId)
        if (target && !target.batches?.length) errors.pen_id = `${target.pen_name || '栏舍'}没有在栏批次，不能登记用药`
      }
      if (!model.drugId) errors.drug_id = '请选择药品'
      const lot = this.selectedLot
      if (model.drugId && (!lot || this.lots?.selectable?.every((item) => Number(item.lot_id) !== Number(model.lotId)))) {
        errors.drug_lot_id = '请选择库存批号'
      }
      const dose = Number(model.dose)
      if (!Number.isFinite(dose) || dose <= 0) errors.dose = '剂量需大于 0'
      else if (String(model.dose).includes('.') && (String(model.dose).split('.')[1] || '').length > 3) {
        errors.dose = '最多 3 位小数'
      }
      const course = Number(model.courseDays)
      if (!Number.isInteger(course) || course < 1) errors.course_days = '疗程至少 1 天'
      else if (course > 90) errors.course_days = '疗程不能超过 90 天'
      if (!model.firstDate) errors.first_date = '请选择首次用药日期'
      else if (model.firstDate > todayISO()) errors.first_date = '首次用药日期不能晚于今天'
      const used = Number(model.usedQty)
      if (!Number.isFinite(used) || used <= 0) errors.used_qty = '请填写实际用量'
      else if (lot?.stock?.value !== null && lot?.stock?.value !== undefined && used > Number(lot.stock.value)) {
        errors.used_qty = `不能超过批号 ${lot.lot_no || ''} 结存 ${lot.stock.value} ${lot.stock.unit_text || ''}`
      }
      if (this.overDose && !model.overDoseNote.trim()) errors.over_dose_note = '请填写超量说明'
      if (this.categoryMismatch && !model.usageNote.trim()) errors.usage_note = '请填写用药说明'
      const reason = model.reason.trim()
      if (!reason) errors.reason = '请填写用药原因'
      else if (reason.length > 100) errors.reason = '用药原因最多 100 字'
      return errors
    },
    async submit(): Promise<void> {
      if (this.submitting) return
      const errors = this.validate()
      this.errors = errors
      if (Object.keys(errors).length) return
      this.submitting = true
      this.formError = ''
      try {
        if (this.formMode === 'edit' && this.editingId) {
          await healthApi.updateMedication(this.editingId, {
            drug_id: this.model.drugId,
            drug_lot_id: this.model.lotId,
            dose: Number(this.model.dose),
            used_qty: Number(this.model.usedQty),
            course_days: Number(this.model.courseDays),
            first_date: this.model.firstDate,
            over_dose_note: this.overDose ? this.model.overDoseNote.trim() : null,
            usage_note: this.categoryMismatch ? this.model.usageNote.trim() : null,
            reason: this.model.reason.trim(),
            executor_id: this.staffView ? null : this.model.executorId,
            report_id: this.model.reportId,
            version: this.editingVersion
          })
          useUiStore().toast('已保存修改')
        } else {
          const result = await healthApi.createMedication({
            target_type: this.model.targetType,
            batch_id: this.model.targetType === 'BATCH' ? this.model.batchId : null,
            pen_id: this.model.targetType === 'PEN' ? this.model.penId : null,
            drug_id: this.model.drugId,
            drug_lot_id: this.model.lotId,
            dose: Number(this.model.dose),
            used_qty: Number(this.model.usedQty),
            course_days: Number(this.model.courseDays),
            first_date: this.model.firstDate,
            over_dose_note: this.overDose ? this.model.overDoseNote.trim() : null,
            usage_note: this.categoryMismatch ? this.model.usageNote.trim() : null,
            reason: this.model.reason.trim(),
            executor_id: this.staffView ? null : this.model.executorId,
            report_id: this.model.reportId
          })
          useUiStore().toast(result.toast || '已登记用药')
        }
        this.formOpen = false
        await this.loadAll()
      } catch (error) {
        this.applySubmitError(error)
      } finally {
        this.submitting = false
      }
    },
    async openDelete(record: MedicationRecord): Promise<void> {
      this.deleteTarget = record
      this.deleteImpact = null
      this.deleteErrors = {}
      this.deleteOpen = true
      this.deleteImpactLoading = true
      try {
        this.deleteImpact = await healthApi.medicationDeleteImpact(Number(record.id))
      } catch (error) {
        useUiStore().toast(errorText(error, '删除影响查询失败'), 'error')
        this.deleteOpen = false
      } finally {
        this.deleteImpactLoading = false
      }
    },
    async confirmDelete(): Promise<void> {
      const target = this.deleteTarget
      const impact = this.deleteImpact
      if (!target || !impact || this.deleteSubmitting) return
      if (impact.impact !== 'NO_CHANGE_TODAY') return
      this.deleteSubmitting = true
      try {
        const result = await healthApi.deleteMedication(Number(target.id), Number(target.version || 0))
        this.deleteOpen = false
        useUiStore().toast(result.toast || '已删除用药记录')
        await this.loadAll()
      } catch (error) {
        const apiError = error as ApiError
        if (/修改申请/.test(apiError?.message || '')) {
          this.deleteErrors = { reason: apiError.message }
        } else {
          useUiStore().toast(apiError?.message || '删除失败，请稍后重试', 'error')
        }
      } finally {
        this.deleteSubmitting = false
      }
    },
    deleteRequestQuery(): Record<string, string> {
      return {
        action: 'create',
        biz_type: 'MEDICATION_RECORD',
        biz_id: String(this.deleteTarget?.id || 0)
      }
    },
    openNotify(record: MedicationRecord): void {
      this.notifyTarget = record
      this.notifyOpen = true
      this.deleteImpact = null
      this.deleteTarget = record
      void healthApi.medicationDeleteImpact(Number(record.id)).then((impact) => {
        if (Number(this.notifyTarget?.id) === Number(record.id)) this.deleteImpact = impact
      }).catch(() => {
        this.deleteImpact = null
      })
    },
    async submitNotify(): Promise<void> {
      if (!this.notifyTarget || this.notifySubmitting) return
      this.notifySubmitting = true
      try {
        const result = await healthApi.notifyManager(Number(this.notifyTarget.id), 'DELETE')
        const names = (result.notified_names || []).join('、')
        this.notifyOpen = false
        useUiStore().toast(names ? `已通知${names}` : '已通知场长')
      } catch (error) {
        useUiStore().toast(errorText(error, '通知失败，请稍后重试'), 'error')
      } finally {
        this.notifySubmitting = false
      }
    },
    async openLogs(record: MedicationRecord): Promise<void> {
      this.logOpen = true
      this.logLoading = true
      this.logError = ''
      this.logs = []
      this.logTargetTitle = `${record.drug_name || ''} · ${monthDay(record.first_date)}–${monthDay(record.last_date)}`
      try {
        this.logs = await healthApi.recordLogs('MEDICATION_RECORD', Number(record.id))
      } catch (error) {
        this.logError = errorText(error, '修改记录加载失败')
      } finally {
        this.logLoading = false
      }
    },
    applySubmitError(error: unknown): void {
      const fields = fieldErrorsOf(error)
      if (Object.keys(fields).length) this.errors = { ...fields }
      const apiError = error as ApiError
      if (apiError?.code === 42203 || apiError?.code === 42201) {
        this.errors = { ...this.errors, drug_lot_id: apiError.message }
        void this.loadLots()
        return
      }
      if (apiError?.message) useUiStore().toast(apiError.message, 'error')
    }
  }
})
