import type { FileVO } from '@zmt/shared'
import { http } from '@/api/client'
import { raw } from '@/api/raw'

/* ------------------------------------------------------------------ */
/* 服务端 VO（字段名与返回 JSON 一致，全部 snake_case）                */
/* ------------------------------------------------------------------ */

export interface AgreementItemVO {
  version?: string
  title?: string
  url?: string
}

export interface AgreementVO {
  user_agreement?: AgreementItemVO
  privacy_policy?: AgreementItemVO
  agreement_version?: string
  customer_service_phone?: string
}

export interface AccountUserVO {
  user_id?: number
  name?: string
  name_initial?: string
  phone_masked?: string
  avatar_url?: string | null
}

export interface RecoveryCodeVO {
  recovery_code_id: number
  code: string
  generated_at?: string
  phone_masked?: string
  scene?: string
  scene_text?: string
  is_regenerated?: boolean
}

export interface RecoveryInfoVO {
  new_phone_masked?: string
  recovered_at?: string
  cancel_available_date?: string
}

export interface AuthTokenVO {
  access_token: string
  [key: string]: unknown
  token_type?: string
  expires_in?: number
  refresh_expires_in?: number
  refresh_token?: string | null
  next_step?: string
  next_step_text?: string
  user?: AccountUserVO
  last_farm_id?: number
  recovery_info?: RecoveryInfoVO | null
  recovery_code?: RecoveryCodeVO | null
}

export interface SessionVO {
  user?: AccountUserVO
  next_step?: string
  next_step_text?: string
  recovery_info?: RecoveryInfoVO | null
  last_farm_id?: number
}

export interface SmsCodeSendVO {
  phone_masked?: string
  cooldown_seconds?: number
  expire_seconds?: number
}

export interface AccountFarmStatsVO {
  total_count?: number
  pending_audit_count?: number
  rejected_count?: number
}

export interface ReminderSummaryVO {
  digest_enabled?: boolean
  digest_time?: string
  quiet_enabled?: boolean
  quiet_start?: string
  quiet_end?: string
}

export interface AccountSecurityVO {
  phone_masked?: string
  password_updated_at?: string
  recovery_code_generated_at?: string
  cancel_restricted?: boolean
  cancel_available_date?: string
}

export interface AccountOverviewVO {
  user?: AccountUserVO
  profile_version?: number
  current_farm?: {
    farm_id?: number
    name?: string
    species_text?: string
    status?: string
    status_text?: string
    role?: string
    role_text?: string
  } | null
  farm_stats?: AccountFarmStatsVO
  change_request_pending_count?: number | null
  reminder_summary?: ReminderSummaryVO
  security?: AccountSecurityVO
}

export interface ProfileVO {
  name?: string
  name_initial?: string
  version?: number
}

export interface RecoveryCodeConfirmVO {
  recovery_code_id?: number
  status?: string
  confirmed_at?: string
  next_step?: string
}

export interface CancelOwnedFarmVO {
  farm_id: number
  name?: string
  farm_status?: string
  farm_status_text?: string
  disposal?: string
  disposal_text?: string
  current_stock?: number
  count_unit_text?: string
  member_count?: number
  transferable?: boolean
  transfer_pending_to_name?: string | null
}

export interface CancelExitFarmVO {
  farm_id: number
  name?: string
  role?: string
  role_text?: string
}

export interface CancellationPreviewVO {
  phone_masked?: string
  personal_data_desc?: string
  phone_release_date?: string
  checklist_version?: string
  can_cancel?: boolean
  block_reasons?: string[]
  cancel_available_date?: string | null
  farm_cleanup_date?: string | null
  owned_farms?: CancelOwnedFarmVO[]
  transferable_farm_count?: number
  exit_farms?: CancelExitFarmVO[]
}

export interface AccountRecoverySubmitVO {
  recovery_id?: number
  new_phone_masked?: string
  status?: string
  status_text?: string
  submitted_at?: string
}

/* ------------------------------------------------------------------ */
/* 未登录接口（协议、验证码、注册、重置密码、找回申请）                */
/* ------------------------------------------------------------------ */

export async function getCurrentAgreement(): Promise<AgreementVO> {
  const res = await raw.get<{ code: number; data: AgreementVO }>('/agreements/current')
  return res.data.data
}

export async function sendSmsCode(payload: {
  phone: string
  scene: string
  old_phone?: string
}): Promise<SmsCodeSendVO> {
  const res = await raw.post<{ code: number; data: SmsCodeSendVO }>('/sms-codes', payload)
  return res.data.data
}

export async function registerAccount(payload: {
  phone: string
  sms_code: string
  password: string
  agree_terms: boolean
  agreement_version: string
  device_id: string
  device_model?: string
  os_version?: string
}): Promise<AuthTokenVO> {
  const res = await raw.post<{ code: number; data: AuthTokenVO }>('/auth/register', payload)
  return res.data.data
}

export async function resetPassword(payload: {
  phone: string
  sms_code: string
  new_password: string
}): Promise<void> {
  await raw.post('/auth/password-resets', payload)
}

export async function submitAccountRecovery(payload: {
  old_phone: string
  recovery_code: string
  new_phone: string
  new_phone_sms_code: string
  description: string
}): Promise<AccountRecoverySubmitVO> {
  const res = await raw.post<{ code: number; data: AccountRecoverySubmitVO }>('/account-recoveries', payload)
  return res.data.data
}

/* ------------------------------------------------------------------ */
/* 已登录接口                                                          */
/* ------------------------------------------------------------------ */

export function getSession(): Promise<SessionVO> {
  return http().get<SessionVO>('/auth/session')
}

export function getAccountOverview(): Promise<AccountOverviewVO> {
  return http().get<AccountOverviewVO>('/account/overview')
}

export function updateProfile(name: string, version: number): Promise<ProfileVO> {
  return http().request<ProfileVO>({
    url: '/account/profile',
    method: 'PATCH',
    data: { name, version }
  })
}

export function changePassword(payload: {
  current_password: string
  new_password: string
  confirm_password: string
}): Promise<AuthTokenVO> {
  return http().put<AuthTokenVO>('/account/password', payload)
}

export function changePasswordAfterRecovery(payload: {
  new_password: string
  confirm_password: string
}): Promise<AuthTokenVO> {
  return http().put<AuthTokenVO>('/account/password/after-recovery', payload)
}

export function sendAccountSmsCode(scene: string): Promise<SmsCodeSendVO> {
  return http().post<SmsCodeSendVO>('/account/sms-codes', { scene })
}

export function regenerateRecoveryCode(smsCode: string): Promise<RecoveryCodeVO> {
  return http().post<RecoveryCodeVO>('/account/recovery-codes', { sms_code: smsCode })
}

export function regeneratePendingRecoveryCode(): Promise<RecoveryCodeVO> {
  return http().post<RecoveryCodeVO>('/account/recovery-codes/pending')
}

export function confirmRecoveryCode(id: number): Promise<RecoveryCodeConfirmVO> {
  return http().post<RecoveryCodeConfirmVO>(`/account/recovery-codes/${id}/confirm`)
}

export async function uploadAvatar(file: File, onProgress?: (percent: number) => void): Promise<FileVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('scene', 'AVATAR')
  return http().upload<FileVO>('/files', form, onProgress)
}

export function bindAvatar(fileId: number): Promise<AccountUserVO> {
  return http().put<AccountUserVO>('/account/avatar', { file_id: fileId })
}

export function clearAvatar(): Promise<AccountUserVO> {
  return http().del<AccountUserVO>('/account/avatar')
}

export function getCancellationPreview(): Promise<CancellationPreviewVO> {
  return http().get<CancellationPreviewVO>('/account/cancellation-preview')
}

export function cancelAccount(payload: {
  sms_code: string
  confirmed: boolean
  checklist_version: string
}): Promise<void> {
  return http().post<void>('/account/cancellation', payload)
}
