import type { FarmStatusCode } from '@/api/farm'

/** 页面通用状态机：默认 / 空 / 筛选空 / 加载失败 / 无权 */
export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

export const PHONE_RE = /^1\d{10}$/

export function isCancelError(error: unknown): boolean {
  const value = error as { code?: string; name?: string } | null
  return value?.code === 'ERR_CANCELED' || value?.name === 'CanceledError' || value?.name === 'AbortError'
}

export function digitsOf(value: string | null | undefined): string {
  return String(value || '').replace(/\D/g, '')
}

/** 联系电话按 3-4-4 分组显示，提交时去掉空格 */
export function formatPhoneInput(value: string | null | undefined): string {
  const digits = digitsOf(value).slice(0, 11)
  if (digits.length <= 3) return digits
  if (digits.length <= 7) return `${digits.slice(0, 3)} ${digits.slice(3)}`
  return `${digits.slice(0, 3)} ${digits.slice(3, 7)} ${digits.slice(7)}`
}

export function maskPhone(value: string | null | undefined): string {
  const digits = digitsOf(value)
  if (digits.length !== 11) return String(value || '')
  return `${digits.slice(0, 3)}****${digits.slice(7)}`
}

export interface StatusMeta {
  label: string
  tagType: 'success' | 'warning' | 'error' | 'primary' | 'neutral'
  icon: string
  tone: 'success' | 'warning' | 'error' | 'neutral'
}

export function farmStatusMeta(status: FarmStatusCode | string | undefined): StatusMeta {
  switch (status) {
    case 'PENDING':
      return { label: '待审核', tagType: 'warning', icon: 'schedule', tone: 'warning' }
    case 'REJECTED':
      return { label: '已驳回', tagType: 'error', icon: 'block', tone: 'error' }
    case 'DISABLED':
      return { label: '已停用', tagType: 'error', icon: 'block', tone: 'error' }
    case 'FROZEN':
      return { label: '已冻结', tagType: 'error', icon: 'ac_unit', tone: 'error' }
    default:
      return { label: '已通过', tagType: 'success', icon: 'check', tone: 'success' }
  }
}

/** 与后端一致：长度按 Unicode 码点计数 */
export function textLength(value: string | null | undefined): number {
  return Array.from(String(value || '')).length
}
