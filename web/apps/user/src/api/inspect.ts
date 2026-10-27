/**
 * 模块07 日常巡检与异常上报 AI 诊断接口（API-I01–I26）。
 * 服务端 JSON 使用 snake_case（与 OpenAPI 生成类型的 camelCase 不同），这里按真实 VO 声明。
 */
import type { AxiosRequestConfig } from 'axios'
import type { FileVO } from '@zmt/shared'
import { http } from '@/api/client'

/* ---------------- 通用 ---------------- */

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  has_more: boolean
  tab_counts?: Record<string, number>
}

export interface MediaItem extends FileVO {
  media_type?: string
  mime_type?: string
  duration_sec?: number
  sort_no?: number
  source_text?: string
  shot_by?: string
  shot_at?: string
}

export interface UserBrief {
  user_id?: number
  name?: string
  avatar_url?: string
  role?: string
  role_text?: string
}

export interface OptionVO {
  value: string
  label: string
  disabled?: boolean
  hint?: string
}

export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

function config(signal?: AbortSignal): AxiosRequestConfig | undefined {
  return signal ? { signal } : undefined
}

/* ---------------- API-I01 统计 ---------------- */

export interface InspectionSummary {
  farm_name?: string
  date?: string
  deadline_time?: string
  remind_time?: string
  scope?: 'ALL_PENS' | 'MY_PENS' | string
  should_count: number
  done_count: number
  pending_count: number
  abnormal_count: number
  missed_count: number
  responsible_pen_count?: number | null
  no_owner_pending_count?: number | null
  completion_rate?: number | null
  deadline_passed: boolean
  all_done: boolean
  can_edit_deadline: boolean
  first_abnormal_pen_name?: string | null
  first_abnormal_reported?: boolean | null
}

export function fetchSummary(date?: string, signal?: AbortSignal): Promise<InspectionSummary> {
  return http().get<InspectionSummary>('/inspections/summary', date ? { date } : undefined, config(signal))
}

/* ---------------- API-I02 某日栏舍清单 ---------------- */

export interface InspectionListItem {
  inspection_id: number
  pen_id: number
  pen_code?: string
  pen_name?: string
  batch_id?: number | null
  batch_nos?: string[]
  stock_count?: number
  responsible_names?: string[]
  display_status: 'ABNORMAL' | 'PENDING' | 'DONE' | 'MISSED' | string
  display_status_text?: string
  abnormal_source?: 'CHECK' | 'MANUAL' | string
  abnormal_summary?: string
  manual_reason?: string
  registrant_name?: string
  registered_at?: string
  location_status?: 'ON_SITE' | 'OFF_SITE' | 'NO_LOCATION' | string
  distance_m?: number | null
  is_overdue?: boolean
  is_backfill?: boolean
  deadline_passed?: boolean
  report_id?: number | null
  report_status?: string | null
  can_register?: boolean
  can_modify?: boolean
  can_report?: boolean
  can_view_diagnosis?: boolean
  version?: number
}

export interface DayListParams {
  date?: string
  status?: 'ALL' | 'PENDING' | 'ABNORMAL' | 'DONE' | string
  page?: number
  size?: number
}

export function fetchDayList(params: DayListParams, signal?: AbortSignal): Promise<PageResult<InspectionListItem>> {
  return http().get<PageResult<InspectionListItem>>('/inspections', { ...params }, config(signal))
}

/* ---------------- API-I03 前 3 天问题 ---------------- */

export interface RecentIssue {
  inspection_id: number
  pen_id: number
  pen_name?: string
  inspect_date?: string
  issue_type?: 'OVERDUE' | 'BACKFILL' | 'MISSED' | string
  issue_type_text?: string
  registrant_name?: string
  registered_at?: string
  backfill_until?: string
  can_backfill?: boolean
}

export function fetchRecentIssues(signal?: AbortSignal): Promise<RecentIssue[]> {
  return http().get<RecentIssue[]>('/inspections/recent-issues', undefined, config(signal))
}

/* ---------------- API-I04 / I05 截止时间 ---------------- */

export interface InspectionSetting {
  farm_name?: string
  deadline_time?: string
  remind_time?: string
  default_deadline?: string
  quick_options?: string[]
  min_time?: string
  max_time?: string
  owner_manager_names?: string[]
  updated_by_name?: string
  updated_at?: string
}

export function fetchInspectionSetting(signal?: AbortSignal): Promise<InspectionSetting> {
  return http().get<InspectionSetting>('/inspection-settings/current', undefined, config(signal))
}

export function saveInspectionSetting(deadlineTime: string): Promise<InspectionSetting> {
  return http().put<InspectionSetting>('/inspection-settings/current', { deadline_time: deadlineTime })
}

/* ---------------- API-I06 表单上下文 ---------------- */

export interface InspectionTemplateOption {
  option_id: number
  option_name: string
  is_abnormal?: boolean
  sort_no?: number
}

export interface InspectionTemplateItem {
  item_id: number
  item_name: string
  sort_no?: number
  options: InspectionTemplateOption[]
}

export interface InspectionFormContext {
  mode: 'REGISTER' | 'MODIFY' | 'BACKFILL' | string
  pen?: {
    pen_id: number
    pen_code?: string
    pen_name?: string
    responsible_names?: string[]
  }
  batches?: Array<{ batch_id: number; batch_no?: string; stock_count?: number }>
  stock_count?: number
  category_id?: number
  inspect_date?: string
  registrant?: UserBrief
  can_choose_executor?: boolean
  can_manual_mark?: boolean
  members?: OptionVO[]
  default_executor_id?: number
  template?: {
    template_id: number
    template_version: number
    items: InspectionTemplateItem[]
  }
  deadline_time?: string
  deadline_passed?: boolean
  already_registered?: { inspection_id: number; registrant_name?: string; registered_at?: string } | null
  can_report?: boolean
  abnormal_locked_reason?: 'REPORTED' | 'CHECK' | string | null
  backfill_dates?: string[]
  backfill_range?: { from?: string; to?: string }
  record?: InspectionDetail | null
  farm_latitude?: number | null
  farm_longitude?: number | null
}

export interface FormContextParams {
  pen_id: number
  mode: 'REGISTER' | 'MODIFY' | 'BACKFILL'
  inspect_date?: string
  inspection_id?: number
}

export function fetchFormContext(params: FormContextParams, signal?: AbortSignal): Promise<InspectionFormContext> {
  return http().get<InspectionFormContext>('/inspections/form-context', { ...params }, config(signal))
}

/* ---------------- API-I07 / I08 / I09 提交 ---------------- */

export interface InspectionItemSelection {
  item_id: number
  option_id: number
}

export interface InspectionLocationPayload {
  latitude?: number
  longitude?: number
  accuracy_m?: number
  client_status: 'OK' | 'DENIED' | 'FAILED'
}

export interface InspectionSubmitPayload {
  pen_id: number
  client_request_id: string
  item_results: InspectionItemSelection[]
  is_manual_marked?: boolean
  manual_reason?: string
  remark?: string
  executor_id?: number
  location?: InspectionLocationPayload
  media_file_ids?: number[]
  version?: number
}

export interface InspectionBackfillPayload {
  pen_id: number
  inspect_date: string
  executor_id: number
  client_request_id: string
  item_results: InspectionItemSelection[]
  is_manual_marked?: boolean
  manual_reason?: string
  remark?: string
  location?: InspectionLocationPayload
  media_file_ids?: number[]
}

export interface InspectionSubmitResult {
  inspection_id: number
  version: number
  pen_name?: string
  inspect_date?: string
  is_abnormal?: boolean
  is_overdue?: boolean
  is_backfill?: boolean
  todo_completed?: boolean
  location_status?: string
  distance_m?: number | null
}

export function registerInspection(payload: InspectionSubmitPayload): Promise<InspectionSubmitResult> {
  return http().post<InspectionSubmitResult>('/inspections', payload, {
    headers: { 'Idempotency-Key': payload.client_request_id }
  })
}

export function modifyInspection(inspectionId: number, payload: InspectionSubmitPayload): Promise<InspectionSubmitResult> {
  return http().put<InspectionSubmitResult>(`/inspections/${inspectionId}`, payload)
}

export function backfillInspection(payload: InspectionBackfillPayload): Promise<InspectionSubmitResult> {
  return http().post<InspectionSubmitResult>('/inspections/backfills', payload, {
    headers: { 'Idempotency-Key': payload.client_request_id }
  })
}

export function findInspectionByRequest(clientRequestId: string): Promise<InspectionSubmitResult> {
  return http().get<InspectionSubmitResult>(`/inspections/by-request/${encodeURIComponent(clientRequestId)}`)
}

/* ---------------- API-I10 巡检详情 ---------------- */

export interface InspectionDetail {
  inspection_id: number
  pen_id: number
  pen_name?: string
  batch_nos?: string[]
  inspect_date?: string
  inspect_status?: string
  inspect_status_text?: string
  item_results?: Array<{
    item_id: number
    item_name?: string
    option_id: number
    option_name?: string
    option_abnormal?: boolean
    sort_no?: number
  }>
  is_abnormal?: boolean
  abnormal_source?: string
  abnormal_summary?: string
  is_manual_marked?: boolean
  manual_reason?: string
  manual_marked_by_name?: string
  manual_marked_at?: string
  remark?: string
  media?: MediaItem[]
  registrant?: UserBrief
  executor?: UserBrief
  is_proxy?: boolean
  registered_at?: string
  location_status?: string
  location_status_text?: string
  distance_m?: number | null
  is_overdue?: boolean
  is_backfill?: boolean
  template_version?: number
  report_id?: number | null
  report_status?: string | null
  edit_mode?: 'NONE' | 'DIRECT' | 'CHANGE_REQUEST' | string
  change_state?: {
    pending_request_id?: number | null
    status?: string
    status_text?: string
  } | null
  has_revisions?: boolean
  version?: number
}

export function fetchInspectionDetail(inspectionId: number, signal?: AbortSignal): Promise<InspectionDetail> {
  return http().get<InspectionDetail>(`/inspections/${inspectionId}`, undefined, config(signal))
}

/* ---------------- API-I11 / I12 栏舍历史 ---------------- */

export interface PenInspectionRow {
  inspection_id: number
  inspect_date: string
  is_today?: boolean
  inspect_status?: string
  tags?: string[]
  abnormal_source?: string
  abnormal_summary?: string
  manual_reason?: string
  registrant_name?: string
  registered_at?: string
  can_modify?: boolean
  modify_target?: 'REGISTER' | 'BACKFILL' | string
  can_change_request?: boolean
  pending_request_id?: number | null
  has_revisions?: boolean
  can_backfill?: boolean
  backfill_until?: string
  backfill_expired?: boolean
  result_summary?: string
}

export interface PenHistoryParams {
  filter?: 'ALL' | 'ABNORMAL' | 'OVERDUE' | 'BACKFILL' | 'OFF_SITE' | string
  start_date?: string
  end_date?: string
  locate_date?: string
  page?: number
  size?: number
}

export function fetchPenHistory(
  penId: number,
  params: PenHistoryParams,
  signal?: AbortSignal
): Promise<PageResult<PenInspectionRow>> {
  return http().get<PageResult<PenInspectionRow>>(`/pens/${penId}/inspections`, { ...params }, config(signal))
}

export interface PenInspectionOverview {
  pen_id: number
  pen_name?: string
  batch_nos?: string[]
  responsible_names?: string[]
  recent7_done_days?: number
  recent7_should_days?: number
  today?: string
  backfill_from?: string
  backfill_to?: string
  days?: Array<{
    date: string
    day_tag?: 'ABNORMAL' | 'BACKFILL' | 'OVERDUE' | 'DONE' | 'MISSED' | 'PENDING' | 'NONE' | string
    abnormal_source?: string
    inspection_id?: number | null
  }>
}

export function fetchPenOverview(penId: number, to?: string, signal?: AbortSignal): Promise<PenInspectionOverview> {
  return http().get<PenInspectionOverview>(`/pens/${penId}/inspection-overview`, to ? { to } : undefined, config(signal))
}

/* ---------------- API-I14 上报表单上下文 ---------------- */

export interface ReportFormPenBatch {
  batch_id: number
  batch_no?: string
  category_id?: number
  stock_count?: number
  breed_title?: string
  day_age?: number | null
}

export interface ReportFormPen {
  pen_id: number
  pen_name?: string
  batches: ReportFormPenBatch[]
}

export interface ReportFormContext {
  source?: 'INSPECTION' | 'DIRECT' | string
  source_text?: string
  inspection_id?: number | null
  pens: ReportFormPen[]
  prefill?: {
    pen_id?: number | null
    batch_id?: number | null
    description?: string
    media?: MediaItem[]
  }
  symptoms: Symptom[]
  notify_names?: string[]
  max_media_count?: number
  max_video_count?: number
}

export interface Symptom {
  symptom_id: number
  name: string
  sort_no?: number
}

export interface ReportFormParams {
  inspection_id?: number
  pen_id?: number
}

export function fetchReportForm(params: ReportFormParams = {}, signal?: AbortSignal): Promise<ReportFormContext> {
  const query: Record<string, number> = {}
  if (params.inspection_id) query.inspection_id = params.inspection_id
  if (params.pen_id) query.pen_id = params.pen_id
  return http().get<ReportFormContext>('/abnormal-reports/form-context', query, config(signal))
}

export function fetchSymptoms(batchId: number, signal?: AbortSignal): Promise<Symptom[]> {
  return http().get<Symptom[]>('/symptoms', { batch_id: batchId }, config(signal))
}

/* ---------------- API-I16 上报 ---------------- */

export interface AbnormalReportCreatePayload {
  client_request_id: string
  pen_id: number
  batch_id: number
  inspection_id?: number
  sick_count: number
  symptom_ids: number[]
  description?: string
  media_file_ids?: number[]
}

export interface ReportCreateResult {
  report_id: number
  status: string
  ai_state: string
  poll_interval_sec?: number
  poll_max_sec?: number
}

export function createAbnormalReport(payload: AbnormalReportCreatePayload): Promise<ReportCreateResult> {
  return http().post<ReportCreateResult>('/abnormal-reports', payload, {
    headers: { 'Idempotency-Key': payload.client_request_id }
  })
}

export function findReportByRequest(clientRequestId: string): Promise<ReportCreateResult> {
  return http().get<ReportCreateResult>(`/abnormal-reports/by-request/${encodeURIComponent(clientRequestId)}`)
}

/* ---------------- API-I19 / I20 / I21 / I22 诊断 ---------------- */

export interface DiagnosisCause {
  rank_no: number
  disease_name?: string
  likelihood?: 'HIGH' | 'MEDIUM' | 'LOW' | string
  likelihood_text?: string
  is_major_epidemic?: boolean
  knowledge_id?: number | null
}

export interface DiagnosisResult {
  diagnosis_id: number
  generated_at?: string
  conclusion?: string
  disclaimer?: string
  urgency?: 'NORMAL' | 'ATTENTION' | 'URGENT' | string
  urgency_text?: string
  urgency_basis?: string
  is_major_epidemic?: boolean
  suggest_vet?: boolean
  emergency_notice?: string
  causes?: DiagnosisCause[]
  advices?: string[]
}

export interface DiagnosisHandle {
  handle_id: number
  diagnostician?: string
  treatment?: string
  used_drug?: boolean
  medication_converted?: boolean
  cured_count?: number
  culled_count?: number
  total_count?: number
  ai_match?: 'MATCH' | 'NOT_MATCH' | 'NONE' | string
  ai_match_text?: string
  handled_by_name?: string
  handled_at?: string
  updated_by_name?: string
  updated_at?: string
  has_revisions?: boolean
  closed?: boolean
  version?: number
}

export interface DiagnosisDetail {
  report_id: number
  version: number
  status: 'PENDING' | 'DIAGNOSED' | 'HANDLED' | 'CLOSED' | string
  status_text?: string
  ai_state?: 'DIAGNOSING' | 'DONE' | 'UNAVAILABLE' | 'FAILED' | string
  ai_state_text?: string
  report: {
    pen_id: number
    pen_name?: string
    batch_id: number
    batch_no?: string | null
    reporter?: UserBrief
    reported_at?: string
    source?: string
    inspection_id?: number | null
    inspection_registered_at?: string
    sick_count?: number
    stock_snapshot?: number
    sick_rate?: number | null
    symptom_names?: string[]
    description?: string
    media?: MediaItem[]
    media_count?: number
  }
  steps?: Array<{ code: string; state: 'DONE' | 'CURRENT' | 'TODO' | string; time?: string; desc?: string }>
  diagnosis?: DiagnosisResult | null
  handle?: DiagnosisHandle | null
  last_failure?: { failed_at?: string; reason_text?: string; trigger_type?: string } | null
  no_diagnosis_text?: string
  notified_names?: string[]
  medication_prefill?: {
    pen_id: number
    batch_id: number
    report_id: number
    diagnosis_id?: number | null
    reason?: string
  } | null
  permissions?: {
    can_handle?: boolean
    can_modify_handle?: boolean
    can_rediagnose?: boolean
    can_view_inspection?: boolean
    can_view_revisions?: boolean
    can_convert_medication?: boolean
  }
}

export function fetchDiagnosis(reportId: number, signal?: AbortSignal): Promise<DiagnosisDetail> {
  return http().get<DiagnosisDetail>(`/abnormal-reports/${reportId}`, undefined, config(signal))
}

export interface DiagnosisState {
  report_id: number
  status: string
  ai_state: string
  diagnosed_at?: string
  urgency?: string
  last_failure_text?: string
  auto_retry_pending?: boolean
}

export function fetchDiagnosisState(reportId: number, signal?: AbortSignal): Promise<DiagnosisState> {
  return http().get<DiagnosisState>(`/abnormal-reports/${reportId}/diagnosis-state`, undefined, config(signal))
}

export function rediagnose(reportId: number): Promise<DiagnosisState> {
  return http().post<DiagnosisState>(`/abnormal-reports/${reportId}/rediagnoses`)
}

export interface HandleSavePayload {
  diagnostician: string
  treatment: string
  used_drug: boolean
  cured_count: number
  culled_count: number
  ai_match: 'MATCH' | 'NOT_MATCH' | 'NONE'
  confirm_close?: boolean
  to_medication?: boolean
  version?: number
}

export interface HandleSaveResult {
  report_id: number
  handle_id: number
  status: string
  closed: boolean
  culled_count?: number
  death_prompt?: { pen_id: number; batch_id: number; culled_count: number } | null
  medication_prefill?: DiagnosisDetail['medication_prefill']
  version: number
}

export function saveHandle(reportId: number, payload: HandleSavePayload): Promise<HandleSaveResult> {
  return http().put<HandleSaveResult>(`/abnormal-reports/${reportId}/handle`, payload)
}

/* ---------------- API-I27 异常上报分页列表 ---------------- */

export interface AbnormalReportListParams {
  page?: number
  size?: number
  status?: 'ALL' | 'PENDING' | 'DIAGNOSED' | 'HANDLED' | 'CLOSED' | string
  pen_id?: number
  batch_id?: number
  urgency?: 'ALL' | 'NORMAL' | 'ATTENTION' | 'URGENT' | string
  keyword?: string
  start_date?: string
  end_date?: string
  mine?: boolean
}

export interface AbnormalReportListItem {
  report_id: number
  version: number
  reported_at: string
  report_date: string
  pen_id: number
  pen_name?: string
  batch_id: number
  batch_no?: string
  sick_count: number
  stock_snapshot: number
  sick_rate: number | null
  symptom_names: string[]
  description?: string
  status: string
  status_text?: string
  ai_state?: string
  ai_state_text?: string
  urgency?: string
  urgency_text?: string
  major_epidemic?: boolean
  top_cause_name?: string
  reporter_id?: number
  reporter_name?: string
  inspection_id?: number | null
  source?: string
  can_handle?: boolean
  can_rediagnose?: boolean
}

export interface AbnormalReportPage extends PageResult<AbnormalReportListItem> {
  tab_counts: Record<string, number>
}

/** API-I27 异常上报列表：服务端分页、筛选、状态角标与员工范围过滤。 */
export function fetchAbnormalReports(
  params: AbnormalReportListParams = {},
  signal?: AbortSignal
): Promise<AbnormalReportPage> {
  return http().get<AbnormalReportPage>('/abnormal-reports', { ...params }, config(signal))
}

/* ---------------- API-I24 可关联上报（用于汇总上报记录） ---------------- */

export interface LinkableReport {
  report_id: number
  diagnosis_id?: number | null
  reported_at?: string
  pen_id: number
  pen_name?: string
  batch_id: number
  batch_no?: string
  top_cause_name?: string
  status?: string
  label?: string
}

export function fetchLinkableReports(
  params: { pen_id?: number; batch_id?: number },
  signal?: AbortSignal
): Promise<LinkableReport[]> {
  return http().get<LinkableReport[]>('/abnormal-reports/linkable', { ...params }, config(signal))
}

/* ---------------- API-U14 修改记录 ---------------- */

export interface RecordLog {
  id: number
  operator?: UserBrief
  op_action?: string
  op_action_text?: string
  changes?: Array<{ field_name?: string; before?: string; after?: string }>
  reason?: string
  change_request_no?: string
  created_at?: string
}

export function fetchRecordLogs(bizType: string, bizId: number): Promise<RecordLog[]> {
  return http().get<RecordLog[]>('/record-logs', { biz_type: bizType, biz_id: bizId })
}

/* ---------------- 文件上传 ---------------- */

export function uploadFile(file: File, scene: 'INSPECTION' | 'ABNORMAL' | 'DEATH' | 'DISINFECT', onProgress?: (percent: number) => void, signal?: AbortSignal): Promise<FileVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('scene', scene)
  return http().upload<FileVO>('/files', form, onProgress, { signal, timeout: 120_000 })
}

/** 浏览器环境生成幂等键；提交超时后可用它查询是否已经保存。 */
export function newRequestKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
