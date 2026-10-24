import type { PageVO } from '@zmt/shared'
import { http } from '@/api/client'

/* ---------------- ADM-R01 四类待审核数 ---------------- */

export interface PendingCountItem {
  queue: 'FARM_AUDIT' | 'FARM_CHANGE' | 'CHANGE_REQUEST' | 'ACCOUNT_RECOVERY' | string
  count: number
  earliest_at?: string
  overdue_count?: number
}

export interface PendingCounts {
  farm_audit: number
  farm_change: number
  change_request: number
  account_recovery: number
  overdue_total: number
  items?: PendingCountItem[]
  sellable_date_count?: number
}

/* ---------------- ADM-R02–R05 养殖场注册审核 ---------------- */

export interface FarmAuditQuery {
  status?: string
  keyword?: string
  category_id?: number
  province_code?: string
  submit_type?: string
  page?: number
  size?: number
}

export interface FarmAuditItem {
  audit_id: number
  farm_id: number
  farm_name: string
  owner_name: string
  owner_phone: string
  category_name: string
  scale: number
  count_unit_text: string
  region_text: string
  image_count: number
  submit_no: number
  submitted_at: string
  wait_minutes?: number
  wait_text?: string
  overdue?: boolean
  status: string
  status_text: string
  auditor_name?: string
  audited_at?: string
  audit_opinion?: string
  reject_reason?: string
}

export interface FarmAuditPage extends PageVO<FarmAuditItem> {
  overdue_count?: number
  overdue_farm_names?: string[]
}

export interface AuditImage {
  file_id: number
  url?: string
  thumb_url?: string
  sort_no?: number
  reshoot?: boolean
  same_as_last?: boolean
  need_reshoot?: boolean
  flag?: string
  file_size?: number
  uploaded_at?: string
  uploader_name?: string
}

export interface ReasonTemplate {
  label: string
  text: string
}

export interface ReviewInfo {
  result?: string
  result_text?: string
  reviewer_name?: string
  reviewed_at?: string
  opinion?: string
  reject_reason?: string
}

export interface FarmAuditOtherFarm {
  farm_id: number
  name: string
  audit_status: string
  audit_status_text: string
  category_name?: string
  current?: boolean
}

export interface FarmAuditDetail {
  audit_id: number
  farm_id: number
  submit_no: number
  status: string
  status_text: string
  version: number
  submitted_at: string
  owner: {
    user_id: number
    name: string
    phone: string
    registered_at?: string
    other_farms?: FarmAuditOtherFarm[]
  }
  info: {
    name: string
    category_name: string
    scale: number
    count_unit_text: string
    contact_name: string
    contact_phone: string
    address: string
    region_text: string
    intro?: string
  }
  last_reject?: { reason: string; audited_at: string; auditor_name: string } | null
  change_summary?: string | null
  images?: AuditImage[]
  location?: {
    longitude?: number
    latitude?: number
    loc_accuracy?: number
    nearest_farm_name?: string
    distance_km?: number
  } | null
  history?: Array<{ event: string; time: string; actor?: string; desc?: string; tag?: string }>
  approve_template?: string
  reject_templates?: ReasonTemplate[]
  review?: ReviewInfo | null
}

export interface AuditResult {
  target_name: string
  notified_names: string[]
  sms_phone?: string
  recalc_count?: number
  recalc_summary?: string
}

export function getPendingCounts() {
  return http().get<PendingCounts>('/audit/pending-counts')
}

export function getFarmAudits(query: FarmAuditQuery) {
  return http().get<FarmAuditPage>('/farm-audits', { ...query })
}

export function getFarmAuditDetail(id: number) {
  return http().get<FarmAuditDetail>(`/farm-audits/${id}`)
}

export function approveFarmAudit(id: number, payload: { audit_opinion: string; version: number }) {
  return http().post<AuditResult>(`/farm-audits/${id}/approve`, payload)
}

export function rejectFarmAudit(
  id: number,
  payload: { reject_reason: string; reshoot_file_ids?: number[]; version: number }
) {
  return http().post<AuditResult>(`/farm-audits/${id}/reject`, payload)
}

export interface SmsResendResult {
  id: number
  phone: string
  sms_status?: string
  sms_status_text?: string
  retry_count?: number
  queued_at?: string
  note?: string
}

export function resendFarmAuditSms(id: number) {
  return http().post<SmsResendResult>(`/farm-audits/${id}/resend-sms`, {})
}

/* ---------------- ADM-R06–R09 资料变更审核 ---------------- */

export interface FarmChangeQuery {
  status?: string
  keyword?: string
  change_item?: string
  page?: number
  size?: number
}

export interface FarmChangeItem {
  change_id: number
  farm_id: number
  farm_name: string
  owner_name: string
  owner_phone: string
  change_items?: string[]
  change_items_text?: string[]
  remark?: string
  submitted_at: string
  wait_minutes?: number
  wait_text?: string
  overdue?: boolean
  status: string
  status_text: string
  auditor_name?: string
  audited_at?: string
  audit_opinion?: string
  reject_reason?: string
}

export interface FarmChangeStats {
  pending_count: number
  earliest_at?: string
  approved_30d: number
  approved_30d_by_item: Record<string, number>
  rejected_30d: number
}

export interface FarmChangeStatsRaw {
  pending_count?: number
  pendingCount?: number
  earliest_at?: string
  earliestAt?: string
  approved_30d?: number
  approved30d?: number
  approved_30d_by_item?: Record<string, number>
  approved30d_by_item?: Record<string, number>
  approved30dByItem?: Record<string, number>
  rejected_30d?: number
  rejected30d?: number
}

export interface FarmChangePage extends PageVO<FarmChangeItem> {
  stats?: FarmChangeStatsRaw
}

export interface LocationPoint {
  longitude?: number
  latitude?: number
  address?: string
  loc_accuracy?: number
  source_text?: string
}

export interface FarmChangeDetail {
  change_id: number
  farm_id: number
  farm_name: string
  change_no: number
  status: string
  status_text: string
  version: number
  submitted_at: string
  owner_name: string
  owner_phone: string
  remark?: string
  compare?: Array<{ field: string; before: string; after: string; changed?: boolean }>
  locations?: {
    current?: LocationPoint | null
    applied?: LocationPoint | null
    distance_km?: number
  } | null
  images_current?: AuditImage[]
  images_after?: AuditImage[]
  farm_overview?: {
    approved_at?: string
    category_name?: string
    current_count?: number
    count_unit_text?: string
    member_count?: number
    farm_status_text?: string
  } | null
  approve_template?: string
  reject_templates?: ReasonTemplate[]
  review?: ReviewInfo | null
}

export function getFarmChanges(query: FarmChangeQuery) {
  return http().get<FarmChangePage>('/farm-changes', { ...query })
}

export function getFarmChangeDetail(id: number) {
  return http().get<FarmChangeDetail>(`/farm-changes/${id}`)
}

export function approveFarmChange(id: number, payload: { audit_opinion: string; version: number }) {
  return http().post<AuditResult>(`/farm-changes/${id}/approve`, payload)
}

export function rejectFarmChange(id: number, payload: { reject_reason: string; version: number }) {
  return http().post<AuditResult>(`/farm-changes/${id}/reject`, payload)
}

/* ---------------- ADM-R10–R13 修改申请审核 ---------------- */

export interface ChangeRequestQuery {
  status?: string
  keyword?: string
  source_module?: string
  impact?: string
  page?: number
  size?: number
}

export interface ChangeImpactTag {
  code: string
  text: string
  warn?: boolean
}

export interface ChangeRequestItem {
  id: number
  request_no: string
  biz_type?: string
  biz_type_text?: string
  title: string
  record_locator?: string
  farm_id: number
  farm_name: string
  source_module: string
  source_module_text: string
  change_type: string
  change_type_text: string
  before_display?: string
  after_display?: string
  impacts?: ChangeImpactTag[]
  submitter_name: string
  submitter_role?: string
  submitter_role_text?: string
  submitted_at: string
  wait_minutes?: number
  wait_text?: string
  overdue?: boolean
  status: string
  status_text: string
  reviewer_name?: string
  reviewed_at?: string
}

export interface ChangeRequestPage extends PageVO<ChangeRequestItem> {
  sellable_date_count?: number
}

export interface ChangeRequestDetail {
  id: number
  request_no: string
  status: string
  status_text: string
  version: number
  change_type: string
  change_type_text: string
  header: {
    title: string
    farm_name: string
    submitter_name: string
    submitter_role_text?: string
    submitted_at: string
    submit_channel_text?: string
  }
  original?: Array<{ label: string; value: string; span?: number }>
  compare?: Array<{ field: string; before: string; after?: string; changed?: boolean }>
  reason?: string
  impact?: {
    note?: string
    rows?: Array<{ item: string; current: string; after: string; is_loss?: boolean }>
    recalc_tags?: string[]
  } | null
  validation?: { level: string; text: string; error_code?: string } | null
  record_history?: Array<{
    field_name: string
    before_display: string
    after_display: string
    reviewer_name?: string
    reviewed_at?: string
    status_text?: string
    recalc_summary?: string
  }>
  batch_id?: number
  batch_no?: string
  notify_text?: string
  review?: ReviewInfo | null
}

export function getChangeRequests(query: ChangeRequestQuery) {
  return http().get<ChangeRequestPage>('/change-requests', { ...query })
}

export function getChangeRequestDetail(id: number) {
  return http().get<ChangeRequestDetail>(`/change-requests/${id}`)
}

export function approveChangeRequest(id: number, payload: { review_comment: string; version: number }) {
  return http().post<AuditResult>(`/change-requests/${id}/approve`, payload)
}

export function rejectChangeRequest(id: number, payload: { reject_reason: string; version: number }) {
  return http().post<AuditResult>(`/change-requests/${id}/reject`, payload)
}

/* ---------------- ADM-R14–R17 账号找回 ---------------- */

export interface RecoveryQuery {
  tab?: string
  keyword?: string
  page?: number
  size?: number
}

export interface RecoveryItem {
  id: number
  user_id: number
  user_name: string
  old_phone: string
  new_phone: string
  new_phone_verified?: boolean
  code_attempt_no?: number
  code_fail_count?: number
  description?: string
  submit_channel?: string
  submit_channel_text?: string
  submitted_at: string
  wait_minutes?: number
  wait_text?: string
  overdue?: boolean
  status: string
  status_text: string
  handled_by_name?: string
  handled_at?: string
  version?: number
}

export interface RecoveryDetail {
  id: number
  user_id: number
  user_name: string
  status: string
  status_text: string
  version: number
  old_phone: string
  new_phone: string
  new_phone_verified?: boolean
  code_attempt_no?: number
  code_fail_count?: number
  submitted_at: string
  checks?: Array<{ code: string; level: string; text: string; extra?: string }>
  description?: string
  account?: {
    registered_at?: string
    recovery_code_created_at?: string
    recovered_at?: string
    last_login_at?: string
    last_login_client_text?: string
  } | null
  farms?: Array<{ farm_id: number; farm_name: string; role?: string; role_text?: string; pen_text?: string }>
  channel?: {
    submit_channel?: string
    submit_channel_text?: string
    submit_device?: string
    submit_ip_region?: string
  } | null
  after_actions?: { new_phone?: string; cancel_forbidden_until?: string } | null
  verify_methods?: Array<{ value: string; label: string }>
  verify_method?: string
  verify_method_text?: string
  handle_note?: string
  handled_by_name?: string
  handled_at?: string
  sms?: { status?: string; status_text?: string; retry_count?: number; warning?: string } | null
  can_reveal_phone?: boolean
  verify_logs?: Array<{
    checked_at: string
    result: string
    result_text: string
    result_desc?: string
    submit_channel_text?: string
    remaining_attempts?: number
    lock_until?: string
  }>
}

export function getRecoveries(query: RecoveryQuery) {
  return http().get<PageVO<RecoveryItem>>('/account-recoveries', { ...query })
}

export function getRecoveryDetail(id: number) {
  return http().get<RecoveryDetail>(`/account-recoveries/${id}`)
}

export function approveRecovery(
  id: number,
  payload: { verify_method: string; handle_note: string; version: number }
) {
  return http().post<AuditResult>(`/account-recoveries/${id}/approve`, payload)
}

export function rejectRecovery(
  id: number,
  payload: { verify_method: string; handle_note: string; version: number }
) {
  return http().post<AuditResult>(`/account-recoveries/${id}/reject`, payload)
}

export function resendRecoverySms(id: number) {
  return http().post<SmsResendResult>(`/account-recoveries/${id}/resend-sms`, {})
}

export interface RecoveryVerificationRecord {
  seq: number
  checked_at: string
  result: string
  result_text: string
  result_desc?: string
  fail_count?: number
  remaining_attempts?: number
  lock_until?: string
  ip?: string
  ip_region?: string
  device?: string
  submit_channel_text?: string
  proxy_admin_id?: number
  proxy_admin_name?: string
}

export interface RecoveryVerificationRecords {
  recovery_id: number
  total: number
  records: RecoveryVerificationRecord[]
}

export function getRecoveryVerificationRecords(id: number) {
  return http().get<RecoveryVerificationRecords>(`/account-recoveries/${id}/verification-records`)
}

export interface RecoveryProxyPayload {
  phone: string
  new_phone?: string
  recovery_code: string
  description: string
}

export interface RecoveryProxyResult {
  id: number
  user_id: number
  user_name: string
  old_phone: string
  new_phone: string
  status: string
  status_text: string
  submitted_at: string
}

export function proxyRecovery(payload: RecoveryProxyPayload) {
  return http().post<RecoveryProxyResult>('/account-recoveries/proxy', payload)
}
