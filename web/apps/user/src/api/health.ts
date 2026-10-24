import type { FileVO, PageVO, Schema } from '@zmt/shared'
import { http } from '@/api/client'

/**
 * 模块08 免疫、用药与消毒接口。
 * 响应字段与请求体都直接使用服务端 snake_case（与 generated.ts 一致），不做驼峰转换。
 */
export type BatchOption = Schema<'BatchListItemVO'>
export type PenOption = Schema<'PenOptionVO'>
export type MyPens = Schema<'MyPensVO'>
export type MyPen = NonNullable<MyPens['pens']>[number]
export type ExecutorOption = Schema<'ExecutorOptionVO'>
export type DictItem = Schema<'BaseDictItemVO'>

export type ImmunePlan = Schema<'ImmunePlanVO'>
export type ImmunePlanOverview = Schema<'ImmunePlanOverviewVO'>
export type PreEntryItem = NonNullable<ImmunePlanOverview['pre_entry_items']>[number]
export type ImmunePlanCounts = NonNullable<ImmunePlanOverview['counts']>
export type ImmunePlanOption = Schema<'ImmunePlanOptionVO'>
export type ImmuneRegisterForm = Schema<'ImmuneRegisterFormVO'>
export type VaccineOption = Schema<'VaccineOptionVO'>
export type DrugLot = Schema<'DrugLotVO'>
export type DrugLotOption = Schema<'DrugLotOptionVO'>
export type DrugLotOptions = Schema<'DrugLotOptionsVO'>
export type ImmuneRecord = Schema<'ImmuneRecordVO'>
export type RecordLog = Schema<'RecordLogVO'>

export type Withdrawal = Schema<'WithdrawalVO'>
export type WithdrawalDrug = NonNullable<Withdrawal['drugs']>[number]
export type MedicationRecord = Schema<'MedicationRecordVO'>
export type MedicationTarget = Omit<Schema<'MedicationTargetVO'>, 'batches'> & {
  batches?: Array<{ batch_id?: number; batch_no?: string; stock_count?: number }>
}
export type MedicationDrugOption = Schema<'MedicationDrugOptionVO'>
export type MedicationDrugOptions = Schema<'MedicationDrugOptionsVO'>
export type ReleasePreview = Schema<'ReleaseDatePreviewVO'>
export type MedicationCreateResult = Schema<'MedicationCreateResultVO'>
export type DeleteImpact = Schema<'DeleteImpactVO'>
export type NotifyResult = Schema<'NotifyResultVO'>

export type DisinfectPlan = Schema<'DisinfectPlanVO'>
export type DisinfectPlanDetail = Schema<'DisinfectPlanDetailVO'>
export type DisinfectPlanPreview = Schema<'DisinfectPlanPreviewVO'>
export type DisinfectTask = Schema<'DisinfectTaskVO'>
export type DisinfectTaskOption = Schema<'DisinfectTaskOptionVO'>
export type DisinfectantOption = Schema<'DisinfectantOptionVO'>
export type DisinfectRecord = Schema<'DisinfectRecordVO'>
export type OverdueBanner = Schema<'OverdueBanner'>
export type DisinfectTaskPage = PageVO<DisinfectTask> & { overdue_banner?: OverdueBanner }

/* ---------------- 请求体（服务端 snake_case） ---------------- */

export interface ImmunePlanCreatePayload {
  drug_id: number
  plan_mode: 'BY_DAY_AGE' | 'BY_DATE'
  plan_age_day?: number
  plan_date?: string
  dose: number
  method_code: string
  immune_round?: string
  due_time?: string
}

export interface ImmunePlanPostponePayload {
  new_date: string
  reason: string
  version: number
}

export interface ImmunePlanUpdatePayload {
  dose?: number
  method_code?: string
  due_time?: string
  version: number
}

export interface ImmuneRecordCreatePayload {
  plan_id: number
  drug_id: number
  drug_lot_id: number
  dose: number
  quantity: number
  method_code: string
  vaccinate_date: string
  executor_id?: number | null
}

export interface ImmuneRecordUpdatePayload {
  drug_lot_id?: number
  dose?: number
  quantity?: number
  method_code?: string
  vaccinate_date?: string
  executor_id?: number | null
  version: number
}

export interface MedicationPreviewPayload {
  target_type: 'BATCH' | 'PEN'
  batch_id?: number | null
  pen_id?: number | null
  drug_id: number
  first_date: string
  course_days: number
  dose?: number
  exclude_record_id?: number
}

export interface MedicationCreatePayload {
  target_type: 'BATCH' | 'PEN'
  batch_id?: number | null
  pen_id?: number | null
  drug_id: number
  drug_lot_id: number
  dose: number
  used_qty: number
  course_days: number
  first_date: string
  over_dose_note?: string | null
  usage_note?: string | null
  reason: string
  executor_id?: number | null
  report_id?: number | null
  diagnosis_id?: number | null
}

export interface MedicationUpdatePayload {
  drug_id: number
  drug_lot_id: number
  dose: number
  used_qty: number
  course_days: number
  first_date: string
  over_dose_note?: string | null
  usage_note?: string | null
  reason: string
  executor_id?: number | null
  report_id?: number | null
  version: number
}

export interface DisinfectPlanSavePayload {
  pen_ids: number[]
  cycle_days: number
  start_date: string
  end_date: string
  catalog_id: number
  concentration: string
  method_code: string
  due_time?: string
  version?: number
}

export interface DisinfectPlanPreviewPayload {
  pen_ids: number[]
  cycle_days: number
  start_date: string
  end_date: string
  plan_id?: number
}

export interface DisinfectRecordCreatePayload {
  record_type: 'PLAN_TASK' | 'EMPTY_PEN' | 'EMERGENCY'
  task_id?: number | null
  pen_id?: number | null
  actual_time: string
  executor_id?: number | null
  drug_id: number
  drug_lot_id: number
  usage_qty: number
  concentration: string
  concentration_confirmed?: boolean
  method_code: string
  file_ids?: number[]
}

export interface DisinfectRecordUpdatePayload {
  actual_time: string
  executor_id?: number | null
  drug_lot_id?: number
  usage_qty: number
  concentration: string
  concentration_confirmed?: boolean
  method_code: string
  file_ids?: number[]
  version: number
}

function versionQuery(version?: number | null): string {
  return version === null || version === undefined ? '' : `?version=${encodeURIComponent(String(version))}`
}

export const healthApi = {
  /* ---------- 公共 ---------- */

  async activeBatches(): Promise<PageVO<BatchOption>> {
    return http().get<PageVO<BatchOption>>('/batches', { page: 1, size: 50, status: 'ACTIVE' })
  },

  async pens(): Promise<PenOption[]> {
    return http().get<PenOption[]>('/pens')
  },

  async myPens(): Promise<MyPens> {
    return http().get<MyPens>('/my-pens')
  },

  async penExecutors(penId: number): Promise<ExecutorOption[]> {
    return http().get<ExecutorOption[]>(`/pens/${penId}/executors`)
  },

  async dict(dictType: string, categoryId?: number): Promise<DictItem[]> {
    return http().get<DictItem[]>(`/base/dicts/${dictType}`, categoryId ? { category_id: categoryId } : undefined)
  },

  async drugLots(drugId: number, purpose: string, needQty?: number): Promise<DrugLotOptions> {
    return http().get<DrugLotOptions>('/drug-lots/options', {
      drug_id: drugId,
      purpose,
      ...(needQty ? { need_qty: needQty } : {})
    })
  },

  async recordLogs(bizType: string, bizId: number): Promise<RecordLog[]> {
    return http().get<RecordLog[]>('/record-logs', { biz_type: bizType, biz_id: bizId })
  },

  uploadPhoto(file: File, onProgress?: (percent: number) => void): Promise<FileVO> {
    const form = new FormData()
    form.append('file', file)
    form.append('scene', 'DISINFECT')
    return http().upload<FileVO>('/files', form, onProgress)
  },

  /* ---------- 免疫计划 ---------- */

  async immuneOverview(batchId: number): Promise<ImmunePlanOverview> {
    return http().get<ImmunePlanOverview>(`/batches/${batchId}/immune-plans/overview`)
  },

  async immunePlans(
    batchId: number,
    params: { page: number; size: number; status: string }
  ): Promise<PageVO<ImmunePlan>> {
    return http().get<PageVO<ImmunePlan>>(`/batches/${batchId}/immune-plans`, { ...params })
  },

  async preEntryConfirm(planId: number, version?: number): Promise<ImmunePlanOverview> {
    return http().post<ImmunePlanOverview>(`/immune-plans/${planId}/pre-entry-confirm`, { version })
  },

  async preEntryJoin(planId: number, version?: number): Promise<ImmunePlan> {
    return http().post<ImmunePlan>(`/immune-plans/${planId}/pre-entry-join`, { version })
  },

  async preEntryUndo(planId: number, version?: number): Promise<ImmunePlanOverview> {
    return http().post<ImmunePlanOverview>(`/immune-plans/${planId}/pre-entry-undo`, { version })
  },

  async postponeImmunePlan(planId: number, payload: ImmunePlanPostponePayload): Promise<ImmunePlan> {
    return http().post<ImmunePlan>(`/immune-plans/${planId}/postpone`, payload)
  },

  async createImmunePlan(batchId: number, payload: ImmunePlanCreatePayload): Promise<ImmunePlan> {
    return http().post<ImmunePlan>(`/batches/${batchId}/immune-plans`, payload)
  },

  async updateImmunePlan(planId: number, payload: ImmunePlanUpdatePayload): Promise<ImmunePlan> {
    return http().put<ImmunePlan>(`/immune-plans/${planId}`, payload)
  },

  async deleteImmunePlan(planId: number, version?: number): Promise<void> {
    await http().del<unknown>(`/immune-plans/${planId}${versionQuery(version)}`)
  },

  async immunePlanOptions(params: { page?: number; size?: number; keyword?: string } = {}): Promise<PageVO<ImmunePlanOption>> {
    return http().get<PageVO<ImmunePlanOption>>('/immune-plans/options', { ...params })
  },

  async immuneRegisterForm(planId: number, backfill?: boolean): Promise<ImmuneRegisterForm> {
    return http().get<ImmuneRegisterForm>(
      `/immune-plans/${planId}/register-form`,
      backfill ? { backfill: true } : undefined
    )
  },

  async vaccines(categoryId?: number): Promise<VaccineOption[]> {
    return http().get<VaccineOption[]>('/vaccines/options', categoryId ? { category_id: categoryId } : undefined)
  },

  async immuneRecord(recordId: number): Promise<ImmuneRecord> {
    return http().get<ImmuneRecord>(`/immune-records/${recordId}`)
  },

  async createImmuneRecord(payload: ImmuneRecordCreatePayload): Promise<ImmuneRecord> {
    return http().post<ImmuneRecord>('/immune-records', payload)
  },

  async updateImmuneRecord(recordId: number, payload: ImmuneRecordUpdatePayload): Promise<ImmuneRecord> {
    return http().put<ImmuneRecord>(`/immune-records/${recordId}`, payload)
  },

  /* ---------- 用药与休药 ---------- */

  async withdrawal(batchId: number): Promise<Withdrawal> {
    return http().get<Withdrawal>(`/batches/${batchId}/withdrawal`)
  },

  async medicationRecords(
    batchId: number,
    params: { page: number; size: number }
  ): Promise<PageVO<MedicationRecord>> {
    return http().get<PageVO<MedicationRecord>>(`/batches/${batchId}/medication-records`, { ...params })
  },

  async medicationTargets(params: { target_type: 'BATCH' | 'PEN'; keyword?: string }): Promise<MedicationTarget[]> {
    return http().get<MedicationTarget[]>('/medication/targets', { ...params })
  },

  async medicationDrugOptions(params: { batch_id?: number; pen_id?: number; keyword?: string }): Promise<MedicationDrugOptions> {
    return http().get<MedicationDrugOptions>('/medication/drug-options', { ...params })
  },

  async medicationPreview(payload: MedicationPreviewPayload): Promise<ReleasePreview> {
    return http().post<ReleasePreview>('/medication-records/preview', payload)
  },

  async createMedication(payload: MedicationCreatePayload): Promise<MedicationCreateResult> {
    return http().post<MedicationCreateResult>('/medication-records', payload)
  },

  async medicationRecord(recordId: number): Promise<MedicationRecord> {
    return http().get<MedicationRecord>(`/medication-records/${recordId}`)
  },

  async updateMedication(recordId: number, payload: MedicationUpdatePayload): Promise<MedicationRecord> {
    return http().put<MedicationRecord>(`/medication-records/${recordId}`, payload)
  },

  async medicationDeleteImpact(recordId: number): Promise<DeleteImpact> {
    return http().get<DeleteImpact>(`/medication-records/${recordId}/delete-impact`)
  },

  async deleteMedication(recordId: number, version?: number): Promise<{ toast?: string }> {
    return http().del<{ toast?: string }>(`/medication-records/${recordId}${versionQuery(version)}`)
  },

  async notifyManager(recordId: number, action?: string): Promise<NotifyResult> {
    return http().post<NotifyResult>(
      `/medication-records/${recordId}/notify-manager`,
      action ? { action } : {}
    )
  },

  /* ---------- 消毒计划 / 任务 / 记录 ---------- */

  async disinfectPlans(params: { page: number; size: number; status: string }): Promise<PageVO<DisinfectPlan>> {
    return http().get<PageVO<DisinfectPlan>>('/disinfect-plans', { ...params })
  },

  async disinfectPlan(planId: number): Promise<DisinfectPlanDetail> {
    return http().get<DisinfectPlanDetail>(`/disinfect-plans/${planId}`)
  },

  async previewDisinfectPlan(payload: DisinfectPlanPreviewPayload): Promise<DisinfectPlanPreview> {
    return http().post<DisinfectPlanPreview>('/disinfect-plans/preview', payload)
  },

  async createDisinfectPlan(payload: DisinfectPlanSavePayload): Promise<DisinfectPlan> {
    return http().post<DisinfectPlan>('/disinfect-plans', payload)
  },

  async updateDisinfectPlan(planId: number, payload: DisinfectPlanSavePayload): Promise<DisinfectPlan> {
    return http().put<DisinfectPlan>(`/disinfect-plans/${planId}`, payload)
  },

  async pauseDisinfectPlan(planId: number, payload: { reason?: string; version?: number }): Promise<DisinfectPlan> {
    return http().post<DisinfectPlan>(`/disinfect-plans/${planId}/pause`, payload)
  },

  async resumeDisinfectPlan(planId: number, version?: number): Promise<DisinfectPlan> {
    return http().post<DisinfectPlan>(`/disinfect-plans/${planId}/resume`, { version })
  },

  async disinfectants(): Promise<DisinfectantOption[]> {
    return http().get<DisinfectantOption[]>('/disinfectants/options')
  },

  async disinfectTasks(params: {
    page: number
    size: number
    status: string
    pen_id?: number
  }): Promise<DisinfectTaskPage> {
    return http().get<DisinfectTaskPage>('/disinfect-tasks', { ...params })
  },

  async disinfectTaskOptions(): Promise<DisinfectTaskOption[]> {
    return http().get<DisinfectTaskOption[]>('/disinfect-tasks/options')
  },

  async createDisinfectRecord(payload: DisinfectRecordCreatePayload): Promise<DisinfectRecord> {
    return http().post<DisinfectRecord>('/disinfect-records', payload)
  },

  async disinfectRecord(recordId: number): Promise<DisinfectRecord> {
    return http().get<DisinfectRecord>(`/disinfect-records/${recordId}`)
  },

  async updateDisinfectRecord(recordId: number, payload: DisinfectRecordUpdatePayload): Promise<DisinfectRecord> {
    return http().put<DisinfectRecord>(`/disinfect-records/${recordId}`, payload)
  }
}
