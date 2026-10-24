import type { OptionVO, PageVO } from '@zmt/shared'
import { http } from '@/api/client'

export interface ChangeRequestUserBrief {
  user_id?: number
  name?: string
}

export interface ChangeRequestVO {
  id: number
  request_no?: string
  biz_type: string
  biz_type_text?: string
  biz_id: number
  farm_id: number
  farm_name?: string
  batch_id?: number
  record_locator?: string
  change_type: string
  field_code?: string
  field_name?: string
  value_unit?: string
  before_display?: string
  after_display?: string
  after_value?: string
  reason?: string
  impacts?: string[]
  recalc_tags?: string[]
  status: string
  status_text?: string
  submitted_by?: ChangeRequestUserBrief
  submitted_at?: string
  reviewed_at?: string
  review_comment?: string
  reject_reason?: string
  recalc_summary?: string
}

export interface ChangeRequestCardItem {
  label?: string
  value?: string
}

export interface ChangeRequestFormField {
  field_code: string
  field_name?: string
  value?: string
  value_unit?: string
  input_type?: string
  options?: OptionVO[]
  min?: number
  max?: number
}

export interface ChangeRequestFormVO {
  biz_type?: string
  biz_id?: number
  biz_type_text?: string
  record_locator?: string
  record_title?: string
  record_subtitle?: string
  record_version?: string
  record_card?: ChangeRequestCardItem[]
  fields?: ChangeRequestFormField[]
  linked_rules?: string[]
  review_tip?: string
  pending_request_id?: number | null
  void_allowed?: boolean
  void_notice?: string
  void_recalc_tags?: string[]
  recalc_tags?: Record<string, string[]>
  field_hints?: Record<string, string>
}

export interface ChangeRequestListQuery {
  page: number
  size: number
  status?: string
  farm_id?: number
  biz_type?: string
  keyword?: string
}

export interface ChangeRequestSubmitPayload {
  biz_type: string
  biz_id: number
  change_type: 'UPDATE' | 'VOID' | 'DELETE'
  field_code?: string | null
  new_value?: string | null
  record_version?: string
  reason: string
  resubmit_of_id?: number | null
}

export function listChangeRequests(
  query: ChangeRequestListQuery,
  signal?: AbortSignal
): Promise<PageVO<ChangeRequestVO>> {
  const params: Record<string, unknown> = { page: query.page, size: query.size }
  if (query.status) params.status = query.status
  if (query.farm_id) params.farm_id = query.farm_id
  if (query.biz_type) params.biz_type = query.biz_type
  if (query.keyword) params.keyword = query.keyword
  return http().get<PageVO<ChangeRequestVO>>('/change-requests', params, { signal })
}

export function getChangeRequestDetail(id: number): Promise<ChangeRequestVO> {
  return http().get<ChangeRequestVO>(`/change-requests/${id}`)
}

export function getChangeRequestForm(bizType: string, bizId: number): Promise<ChangeRequestFormVO> {
  return http().get<ChangeRequestFormVO>('/change-requests/form', {
    biz_type: bizType,
    biz_id: bizId
  })
}

export function submitChangeRequest(
  payload: ChangeRequestSubmitPayload,
  idempotencyKey?: string
): Promise<ChangeRequestVO> {
  const config = idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined
  return http().post<ChangeRequestVO>('/change-requests', payload, config)
}
