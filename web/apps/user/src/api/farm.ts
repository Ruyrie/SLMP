import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/client'
import type { FileVO, PageVO } from '@zmt/shared'

/** 模块02 养殖场与成员管理：接口与字段全部按服务端 VO/Request 的 snake_case 定义。 */

export type FarmRelation = 'ALL' | 'CREATED' | 'JOINED'
export type FarmRoleCode = 'OWNER' | 'MANAGER' | 'STAFF'
export type FarmStatusCode = 'NORMAL' | 'PENDING' | 'REJECTED' | 'DISABLED' | 'FROZEN'
export type MemberRoleCode = 'MANAGER' | 'STAFF'
export type InviteStatus = 'PENDING' | 'EXPIRED' | 'REJECTED' | 'ACCEPTED'
export type ChangeField = 'NAME' | 'ADDRESS' | 'QUALIFICATION'

export interface FarmListParams {
  page?: number
  size?: number
  relation?: FarmRelation
}

export interface FarmListItemVO {
  farm_id: number
  name: string
  status: FarmStatusCode
  status_text: string
  role: FarmRoleCode
  role_text: string
  join_type?: 'CREATE' | 'INVITE'
  species_text?: string
  main_category_name?: string
  count_unit?: string
  count_unit_text?: string
  current_stock?: number
  scale?: number
  my_pen_count?: number
  is_current?: boolean
  can_switch?: boolean
  can_quit?: boolean
  last_switch_at?: string
}

export interface QualFileVO {
  file_id: number
  url?: string
  thumb_url?: string
  media_type?: string
  mime_type?: string
  size?: number
  width?: number
  height?: number
  status?: string
  sort_no?: number
  reshoot?: boolean
  is_new?: boolean
}

export interface RegistrationVO {
  name?: string
  main_category_id?: number
  main_category_name?: string
  count_unit?: string
  count_unit_text?: string
  scale?: number
  contact_name?: string
  contact_phone?: string
  contact_phone_masked?: string
  address?: string
  province?: string
  city?: string
  district?: string
  township?: string
  adcode?: string
  longitude?: number | string
  latitude?: number | string
  loc_accuracy?: number | null
  qualification_count?: number
  qualification_files?: QualFileVO[]
}

export interface FarmBasicVO {
  contact_name?: string
  contact_phone?: string
  contact_phone_masked?: string
  intro?: string
  scale?: number
  version?: number
}

export interface MemberSummaryVO {
  member_total?: number
  owner_count?: number
  manager_count?: number
  staff_count?: number
  invite_count?: number
  invite_pending_count?: number
  avatars?: string[]
  unassigned_pens?: PenBriefVO[]
  show_unassigned_banner?: boolean
}

export interface PenBriefVO {
  pen_id: number
  name: string
  pen_type_text?: string
}

export interface FarmPermissionVO {
  can_view_farm_info?: boolean
  can_view_members?: boolean
  can_edit_basic?: boolean
  can_adjust_pens?: boolean
  can_submit_change?: boolean
  can_disable?: boolean
  can_invite?: boolean
  can_change_role?: boolean
  can_enable?: boolean
  can_transfer?: boolean
  can_remove_member?: boolean
  can_quit?: boolean
  can_view_finance?: boolean
  can_submit_change_request?: boolean
  writable?: boolean
}

export interface ChangeBriefVO {
  change_id?: number
  submitted_at?: string
  reject_reason?: string
  audited_at?: string
}

export interface TransferVO {
  transfer_id?: number
  farm_id?: number
  to_member_id?: number
  farm_name?: string
  from_user_name?: string
  to_name?: string
  status?: string
  status_text?: string
  created_at?: string
  expires_at?: string
  handled_at?: string
  cancel_reason?: string
}

export interface FarmDetailVO {
  farm_id: number
  current_stock?: number
  name: string
  status: FarmStatusCode
  status_text: string
  role: FarmRoleCode
  role_text: string
  species_text?: string
  main_category_name?: string
  count_unit?: string
  approved_at?: string
  disabled_at?: string
  restrict_tip?: string
  basic?: FarmBasicVO
  registration?: RegistrationVO
  member_summary?: MemberSummaryVO
  permissions?: FarmPermissionVO
  pending_change?: ChangeBriefVO | null
  rejected_change?: ChangeBriefVO | null
  pending_transfer?: TransferVO | null
}

export interface AuditRecordVO {
  type: 'SUBMIT' | 'RESUBMIT' | 'REJECT' | 'APPROVE' | string
  type_text?: string
  time?: string
  status_tag?: string
  desc?: string
  submit_no?: number
}

export interface FarmAuditVO {
  farm_id: number
  name: string
  status: FarmStatusCode
  status_text: string
  audit_status?: string
  first_submitted_at?: string
  submitted_at?: string
  restrict_tip?: string
  submit_no?: number
  audit_version?: number
  reshoot_count?: number
  audited_at?: string
  reject_reason?: string
  registration?: RegistrationVO
  records?: AuditRecordVO[]
}

export interface ChangeSnapshotVO {
  name?: string
  address?: string
  qualification_files?: QualFileVO[]
  province?: string
  city?: string
  district?: string
  township?: string
  adcode?: string
  longitude?: number | string
  latitude?: number | string
  loc_accuracy?: number | null
}

export interface FarmChangeVO {
  change_id?: number
  change_no?: number
  status?: string
  status_text?: string
  submitted_at?: string
  audited_at?: string
  reject_reason?: string
  remark?: string
  changed_fields?: ChangeField[]
  before?: ChangeSnapshotVO
  after?: ChangeSnapshotVO
  added_count?: number
  removed_count?: number
}

export interface MemberItemVO {
  member_id: number
  user_id?: number
  name: string
  avatar_url?: string | null
  name_initial?: string
  phone_masked?: string
  role: FarmRoleCode
  role_text?: string
  join_type?: string
  join_type_text?: string
  joined_at?: string
  is_me?: boolean
  all_pens?: boolean
  can_adjust_pens?: boolean
  pens?: PenBriefVO[]
  version?: number
}

export interface MemberActionsVO {
  can_change_role?: boolean
  can_adjust_pens?: boolean
  can_remove?: boolean
  can_quit?: boolean
}

export interface MemberDetailVO extends MemberItemVO {
  phone?: string
  farm_name?: string
  actions?: MemberActionsVO
}

export interface MemberChangeResultVO {
  member?: MemberDetailVO
  newly_unassigned_pens?: PenBriefVO[]
}

export interface MemberRemoveResultVO {
  unassigned_pens?: PenBriefVO[]
}

export interface RemovalImpactVO {
  member_id?: number
  member_name?: string
  role?: string
  pending_work_todo_count?: number
  orphan_pens?: PenBriefVO[]
}

export interface InviteItemVO {
  invite_id: number
  invitee_phone_masked?: string
  invitee_phone?: string
  role: MemberRoleCode
  role_text?: string
  status: InviteStatus
  status_text?: string
  invited_at?: string
  expires_at?: string
  handled_at?: string
  can_reinvite?: boolean
  pens?: PenBriefVO[]
}

export interface PenOptionVO {
  pen_id: number
  pen_code?: string
  name: string
  selected?: boolean
  other_staff_count?: number
}

export interface TransferCandidateVO {
  member_id: number
  user_id?: number
  name: string
  name_initial?: string
  phone_masked?: string
}

export interface TransferFarmVO {
  farm_id: number
  name: string
  main_category_name?: string
  count_unit?: string
  current_stock?: number
}

export interface TransferPageVO {
  farm?: TransferFarmVO
  candidates?: TransferCandidateVO[]
  pending?: TransferVO | null
}

export interface NameCheckVO {
  available: boolean
  message?: string
}

export interface FarmCategoryVO {
  id: number
  name: string
  species?: string
  species_text?: string
  count_unit?: string
  count_unit_text?: string
  is_breeding?: boolean
  breeds?: Array<{ id: number; name: string }>
}

export interface CreateFarmBody {
  name: string
  main_category_id: number | null
  scale: number | null
  contact_name: string
  contact_phone: string
  address: string
  province: string
  city: string
  district: string
  township: string
  adcode: string
  longitude: number | null
  latitude: number | null
  loc_accuracy?: number | null
  qualification_file_ids: number[]
}

export interface UpdateRegistrationBody extends CreateFarmBody {
  audit_version: number
}

export interface SubmitFarmChangeBody {
  name: string
  address: string
  province: string
  city: string
  district: string
  township: string
  adcode: string
  longitude: number | null
  latitude: number | null
  loc_accuracy?: number | null
  qualification_file_ids: number[]
  remark?: string
}

export interface UpdateFarmBasicBody {
  contact_name?: string
  contact_phone?: string
  intro?: string
  scale?: number
  version?: number
}

export interface MemberListParams {
  page?: number
  size?: number
  role?: 'ALL' | MemberRoleCode
  keyword?: string
}

export interface InviteListParams {
  page?: number
  size?: number
  keyword?: string
}

export interface InviteMemberBody {
  invitee_phone: string
  role: MemberRoleCode
  pen_ids?: number[]
  resend_of_id?: number
}

export const farmApi = {
  list(params: FarmListParams = {}, config?: AxiosRequestConfig): Promise<PageVO<FarmListItemVO>> {
    return http().get<PageVO<FarmListItemVO>>('/farms', { ...params }, config)
  },
  detail(farmId: number): Promise<FarmDetailVO> {
    return http().get<FarmDetailVO>(`/farms/${farmId}`)
  },
  audit(farmId: number): Promise<FarmAuditVO> {
    return http().get<FarmAuditVO>(`/farms/${farmId}/audit`)
  },
  checkName(name: string, excludeFarmId?: number): Promise<NameCheckVO> {
    return http().get<NameCheckVO>('/farms/name-check', {
      name,
      exclude_farm_id: excludeFarmId || undefined
    })
  },
  create(body: CreateFarmBody): Promise<FarmAuditVO> {
    return http().post<FarmAuditVO>('/farms', body)
  },
  updateRegistration(farmId: number, body: UpdateRegistrationBody): Promise<FarmAuditVO> {
    return http().put<FarmAuditVO>(`/farms/${farmId}/registration`, body)
  },
  deleteFarm(farmId: number): Promise<void> {
    return http().del<void>(`/farms/${farmId}`)
  },
  updateBasic(farmId: number, body: UpdateFarmBasicBody): Promise<FarmBasicVO> {
    return http().request<FarmBasicVO>({ url: `/farms/${farmId}/basic`, method: 'PATCH', data: body })
  },
  disable(farmId: number, version: number): Promise<FarmDetailVO> {
    return http().post<FarmDetailVO>(`/farms/${farmId}/disable`, { version })
  },
  enable(farmId: number, version: number): Promise<FarmDetailVO> {
    return http().post<FarmDetailVO>(`/farms/${farmId}/enable`, { version })
  },
  latestChange(farmId: number): Promise<FarmChangeVO | null> {
    return http().get<FarmChangeVO | null>(`/farms/${farmId}/changes/latest`)
  },
  submitChange(farmId: number, body: SubmitFarmChangeBody): Promise<FarmChangeVO> {
    return http().post<FarmChangeVO>(`/farms/${farmId}/changes`, body)
  },
  members(farmId: number, params: MemberListParams = {}, config?: AxiosRequestConfig): Promise<PageVO<MemberItemVO>> {
    return http().get<PageVO<MemberItemVO>>(`/farms/${farmId}/members`, { ...params }, config)
  },
  member(farmId: number, memberId: number): Promise<MemberDetailVO> {
    return http().get<MemberDetailVO>(`/farms/${farmId}/members/${memberId}`)
  },
  memberSummary(farmId: number): Promise<MemberSummaryVO> {
    return http().get<MemberSummaryVO>(`/farms/${farmId}/member-summary`)
  },
  penOptions(farmId: number, memberId?: number): Promise<PenOptionVO[]> {
    return http().get<PenOptionVO[]>(`/farms/${farmId}/pen-options`, {
      member_id: memberId || undefined
    })
  },
  updateMemberRole(farmId: number, memberId: number, role: MemberRoleCode, version: number): Promise<MemberChangeResultVO> {
    return http().put<MemberChangeResultVO>(`/farms/${farmId}/members/${memberId}/role`, { role, version })
  },
  updateMemberPens(farmId: number, memberId: number, penIds: number[], version: number): Promise<MemberChangeResultVO> {
    return http().put<MemberChangeResultVO>(`/farms/${farmId}/members/${memberId}/pens`, {
      pen_ids: penIds,
      version
    })
  },
  removalImpact(farmId: number, memberId: number): Promise<RemovalImpactVO> {
    return http().get<RemovalImpactVO>(`/farms/${farmId}/members/${memberId}/removal-impact`)
  },
  removeMember(farmId: number, memberId: number, version: number): Promise<MemberRemoveResultVO> {
    return http().request<MemberRemoveResultVO>({
      url: `/farms/${farmId}/members/${memberId}`,
      method: 'DELETE',
      params: { version }
    })
  },
  quit(farmId: number): Promise<void> {
    return http().post<void>(`/farms/${farmId}/quit`)
  },
  invites(farmId: number, params: InviteListParams = {}, config?: AxiosRequestConfig): Promise<PageVO<InviteItemVO>> {
    return http().get<PageVO<InviteItemVO>>(`/farms/${farmId}/invites`, { ...params }, config)
  },
  sendInvite(farmId: number, body: InviteMemberBody): Promise<InviteItemVO> {
    return http().post<InviteItemVO>(`/farms/${farmId}/invites`, body)
  },
  /** F23 撤销尚未接受的邀请；reason 为空时由服务端按“所有者撤销邀请”记录。 */
  cancelFarmInvite(farmId: number, inviteId: number, reason?: string): Promise<void> {
    const trimmed = reason?.trim()
    return http().post<void>(
      `/farms/${farmId}/invites/${inviteId}/cancel`,
      trimmed ? { reason: trimmed } : undefined
    )
  },
  transferPage(farmId: number): Promise<TransferPageVO> {
    return http().get<TransferPageVO>(`/farms/${farmId}/transfer`)
  },
  createTransfer(farmId: number, toMemberId: number): Promise<TransferVO> {
    return http().post<TransferVO>(`/farms/${farmId}/transfers`, { to_member_id: toMemberId })
  },
  cancelTransfer(transferId: number): Promise<TransferVO> {
    return http().post<TransferVO>(`/farm-transfers/${transferId}/cancel`)
  },
  categories(): Promise<FarmCategoryVO[]> {
    return http().get<FarmCategoryVO[]>('/base/categories')
  },
  uploadQual(file: File, onProgress?: (percent: number) => void): Promise<FileVO> {
    const form = new FormData()
    form.append('file', file)
    form.append('scene', 'FARM_QUAL')
    return http().upload<FileVO>('/files', form, onProgress)
  }
}
