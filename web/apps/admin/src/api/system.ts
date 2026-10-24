import type { PageVO } from '@zmt/shared'
import { http } from '@/api/client'

export interface AdminBrief {
  admin_id: number
  name: string
  username: string
  role_code: string
  role_name: string
  status: string
}

export interface AdminRow extends AdminBrief {
  is_self?: boolean
  role_id?: number
  phone?: string
  created_at?: string
  last_login_at?: string
  status_text?: string
  locked?: boolean
  locked_until?: string
  version?: number
  actions?: string[]
}

export interface AdminPageQuery {
  page?: number
  size?: number
}

export interface AdminCreatePayload {
  name: string
  username: string
  phone: string
  role_id: number
  reason: string
}

export interface AdminUpdatePayload {
  name: string
  phone: string
  role_id: number
  reason: string
  version: number
}

export interface AdminCreated {
  admin_id: number
  temp_password?: string
}

export interface TempPassword {
  temp_password?: string
}

export interface RoleItem {
  role_id: number
  role_code: string
  role_name: string
  description?: string
  member_count?: number
  editable?: boolean
}

export interface PermissionCell {
  perm_code: string
  checked: boolean
  locked?: boolean
}

export interface PermissionRow {
  module_code: string
  module_name: string
  page_code: string
  page_name: string
  view: PermissionCell
  handle?: PermissionCell | null
  handle_desc?: string
}

export interface RoleMember {
  admin_id: number
  name: string
  status: string
  status_text: string
}

export interface RolePermission {
  role: RoleItem
  members: RoleMember[]
  editable: boolean
  version: number
  rows: PermissionRow[]
  default_codes: string[]
}

export interface PermissionSaved {
  affected_admin_count: number
}

export interface OperationLogItem {
  log_id: number
  created_at: string
  operator_name: string
  operator_role: string
  operator_role_text: string
  op_type: string
  op_type_text: string
  op_action: string
  op_action_text: string
  result: string
  target_name: string
  reason: string
}

export interface OperationLogDetail {
  operator?: {
    admin_id?: number
    name?: string
    username?: string
    role?: string
    role_text?: string
  }
  op_type?: string
  op_type_text?: string
  op_action?: string
  op_action_text?: string
  result?: string
  result_text?: string
  target_name?: string
  reason?: string
  extra_text?: string
  notify_text?: string
  client?: { client_type?: string; client_type_text?: string; browser?: string; ip_region?: string }
  changes?: Array<{ field_name: string; before: string; after: string }>
  related?: Array<{ time: string; desc: string; source?: string; source_text?: string; status_text?: string }>
  link?: { page_code?: string; biz_type?: string; biz_id?: number; exists?: boolean }
}

export interface OperationLogQuery {
  date_from?: string
  date_to?: string
  admin_id?: number | null
  op_type?: string
  keyword?: string
  page?: number
  size?: number
}

export function getOperationLogs(query: OperationLogQuery) {
  return http().get<PageVO<OperationLogItem>>('/operation-logs', { ...query })
}

export function getOperationLogDetail(id: number) {
  return http().get<OperationLogDetail>(`/operation-logs/${id}`)
}

export function getAdmins() {
  return http().get<PageVO<AdminBrief>>('/admins', { page: 1, size: 100 })
}

export function getAdminPage(query: AdminPageQuery) {
  return http().get<PageVO<AdminRow>>('/admins', { ...query })
}

export function createAdmin(payload: AdminCreatePayload) {
  return http().post<AdminCreated>('/admins', payload)
}

export function updateAdmin(id: number, payload: AdminUpdatePayload) {
  return http().put<void>(`/admins/${id}`, payload)
}

export function resetAdminPassword(id: number, reason: string) {
  return http().post<TempPassword>(`/admins/${id}/reset-password`, { reason })
}

export function setAdminStatus(id: number, action: 'disable' | 'enable', reason: string, version: number) {
  return http().post<void>(`/admins/${id}/${action}`, { reason, version })
}

export function checkAdminUsername(username: string) {
  return http().get<{ available?: boolean; message?: string }>('/admins/username-check', { username })
}

export function getRoles() {
  return http().get<RoleItem[]>('/admin-roles')
}

export function getRolePermissions(id: number) {
  return http().get<RolePermission>(`/admin-roles/${id}/permissions`)
}

export function saveRolePermissions(id: number, permCodes: string[], reason: string, version: number) {
  return http().put<PermissionSaved>(`/admin-roles/${id}/permissions`, { perm_codes: permCodes, reason, version })
}

/* ------------------------------------------------------------------ */
/* ADM-S06–S19 平台概览、用户账号、养殖场管理（由页面代理追加）          */
/* ------------------------------------------------------------------ */

export interface OverviewPendingItem {
  type: string
  type_text: string
  count: number
  earliest_at?: string
  overdue_count?: number
  sellable_date_count?: number
  page_code: string
  can_handle?: boolean
}

export interface OverviewData {
  as_of: string
  pending?: {
    total: number
    overdue_count: number
    items: OverviewPendingItem[]
  } | null
  farm_stat?: {
    total: number
    approved: number
    normal: number
    disabled: number
    frozen: number
    pending: number
    rejected: number
  } | null
  user_stat?: { total: number; normal: number; disabled: number; new_this_month: number } | null
  active_stat?: {
    range_start?: string
    range_end?: string
    users_7d?: number
    users_7d_ratio?: number
    users_today?: number
    farms_7d?: number
    farms_7d_ratio?: number
    users7d?: number
    users7d_ratio?: number
    farms7d?: number
    farms7d_ratio?: number
  } | null
  category_dist?: Array<{ species: string; species_text: string; count: number; ratio: number }>
  month_audit?: {
    range_start?: string
    range_end?: string
    items: Array<{
      type: string
      type_text: string
      approved: number
      rejected: number
      avg_handle_hours?: number | null
    }>
  } | null
  recent_ops?: Array<{
    log_id: number
    operator_name: string
    op_type_text: string
    op_action_text: string
    target_name: string
    reason?: string
    created_at: string
  }>
  failed_blocks?: string[]
}

export interface FarmMapCluster {
  region_code: string
  region_name: string
  lng?: number
  lat?: number
  approved: number
  pending: number
  frozen: number
  total: number
}

export interface FarmMapData {
  level: string
  parent_code?: string | null
  legend: { approved: number; pending: number; frozen: number }
  clusters: FarmMapCluster[]
  points: Array<{ farm_id: number; name: string; lng?: number; lat?: number; farm_status: string }>
}

export interface RegionDetail {
  region_code: string
  region_name: string
  total: number
  approved: number
  pending: number
  frozen: number
  pending_farms: Array<{
    farm_id: number
    audit_id?: number
    farm_name: string
    category_name?: string
    wait_text?: string
    overdue?: boolean
    submit_no?: number
  }>
}

export interface TrendData {
  metric: string
  metric_text: string
  unit: string
  range_start: string
  range_end: string
  points: Array<{ date: string; value: number; avg7?: number | null }>
  summary?: {
    daily_avg?: number
    max_date?: string
    max_value?: number
    holiday_text?: string
    holiday_avg?: number
  } | null
}

export function getOverview() {
  return http().get<OverviewData>('/overview')
}

export function getFarmMap(params: { level?: string; parent_code?: string; category_group?: string } = {}) {
  return http().get<FarmMapData>('/overview/farm-map', { ...params })
}

export function getRegionDetail(regionCode: string) {
  return http().get<RegionDetail>(`/overview/regions/${regionCode}`)
}

export function getOverviewTrend(metric: string) {
  return http().get<TrendData>('/overview/trend', { metric })
}

/* ---------------- ADM-S10–S14 用户账号 ---------------- */

export interface AdminUserQuery {
  status?: string
  keyword?: string
  role?: string
  registered_from?: string
  registered_to?: string
  page?: number
  size?: number
}

export interface AdminUserItem {
  user_id: number
  name: string
  avatar_url?: string | null
  phone: string
  farm_names_text?: string
  farm_count?: number
  role_counts?: { owner: number; manager: number; staff: number }
  registered_at: string
  last_login_at?: string
  last_login_client?: string
  last_login_client_text?: string
  flags?: Array<{ code: string; text: string }>
  status: string
  status_text: string
  version: number
  actions?: string[]
}

export interface AdminUserDetail {
  user_id: number
  name: string
  avatar_url?: string | null
  phone: string
  status: string
  status_text: string
  version: number
  can_reveal_phone?: boolean
  role_summary?: string
  registered_at?: string
  register_client_text?: string
  recovery_code_created_at?: string
  password_updated_at?: string
  recovery_text?: string
  last_login_at?: string
  last_login_client_text?: string
  farms?: Array<{
    farm_id: number
    farm_name: string
    role: string
    role_text: string
    farm_status: string
    farm_status_text: string
    scale_text?: string
    stock_count?: number
    count_unit_text?: string
    pen_count?: number
  }>
  login_logs?: Array<{ time: string; client_text: string; ip_region?: string; result: string; result_text: string }>
  identity_tip?: string
}

export interface ExportResult {
  file_id: number
  file_name: string
  row_count: number
  url: string
  expire_at: string
}

export function getUsers(query: AdminUserQuery) {
  return http().get<PageVO<AdminUserItem>>('/users', { ...query })
}

export function getUserDetail(id: number) {
  return http().get<AdminUserDetail>(`/users/${id}`)
}

export function setUserEnabled(
  id: number,
  action: 'disable' | 'enable',
  payload: { reason: string; version: number; notify_sms?: boolean }
) {
  return http().post<void>(`/users/${id}/${action}`, payload)
}

export function exportUsers(query: AdminUserQuery) {
  return http().post<ExportResult>('/users/export', undefined, { params: { ...query } })
}

export interface PhoneReveal {
  user_id: number
  name: string
  phone: string
}

export function revealUserPhone(id: number) {
  return http().post<PhoneReveal>(`/users/${id}/reveal-phone`)
}

/* ---------------- ADM-S15–S19 养殖场管理 ---------------- */

export interface AdminFarmQuery {
  status?: string
  keyword?: string
  category_id?: number
  province_code?: string
  page?: number
  size?: number
}

export interface AdminFarmItem {
  farm_id: number
  name: string
  category_name?: string
  owner_name: string
  owner_phone: string
  region_text?: string
  member_count?: number
  stock_count?: number
  stock_unit?: string
  approved_at?: string
  records_7d?: number
  records7d?: number
  farm_status: string
  farm_status_text: string
  change_pending?: boolean
  pending_audit_id?: number | null
  pending_change_id?: number | null
  version: number
  actions?: string[]
}

export interface AdminFarmDetail {
  farm_id: number
  name: string
  category_name?: string
  owner?: { name: string; phone: string } | null
  address?: string
  tags?: string[]
  summary?: {
    stock_count: number
    stock_unit: string
    active_batch_count: number
    records_7d?: number
    records7d?: number
    active_members_7d?: number
    active_members7d?: number
    member_total: number
  } | null
  info?: {
    scale_text?: string
    qual_images?: Array<{ file_id: number; url?: string; thumb_url?: string; media_type?: string; status?: string }>
    contact_name?: string
    contact_phone?: string
    created_at?: string
  } | null
  members?: Array<{
    user_id: number
    name: string
    avatar_url?: string | null
    phone: string
    role: string
    role_text: string
    pen_text?: string
    joined_at?: string
    last_login_at?: string
  }>
  invites?: { pending: number; expired: number } | null
  timeline?: Array<{ event: string; time: string; actor?: string; tag?: string; desc?: string }>
  frozen?: { frozen_at?: string; frozen_reason?: string; frozen_by_name?: string } | null
  pending_change_id?: number | null
  version: number
}

export function getAdminFarms(query: AdminFarmQuery) {
  return http().get<PageVO<AdminFarmItem>>('/farms', { ...query })
}

export function getAdminFarmDetail(id: number) {
  return http().get<AdminFarmDetail>(`/farms/${id}`)
}

export function setFarmFrozen(
  id: number,
  action: 'freeze' | 'unfreeze',
  payload: { reason: string; version: number; sms_owner?: boolean }
) {
  return http().post<{ member_count?: number; farm_name?: string } | void>(`/farms/${id}/${action}`, payload)
}

export function exportFarms(query: AdminFarmQuery) {
  return http().post<ExportResult>('/farms/export', undefined, { params: { ...query } })
}
