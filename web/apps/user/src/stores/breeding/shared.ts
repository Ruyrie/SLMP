import { ApiError, dayjs, today as beijingToday } from '@zmt/shared'

export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

export function statusForError(error: unknown): PageStatus {
  return error instanceof ApiError && error.isForbidden ? 'forbidden' : 'error'
}

export function errorText(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message || fallback
  if (error instanceof Error && error.message) return error.message
  return fallback
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  const result: Record<string, string> = {}
  if (!(error instanceof ApiError)) return result
  for (const item of error.fieldErrors) {
    if (item?.field) result[item.field] = item.message || '填写不正确'
  }
  return result
}

/** 妊检阳性须在首次配种后满 18 天登记（与服务端 MIN_POSITIVE_CHECK_DAYS 一致） */
export const PREG_CHECK_MIN_DAYS = 18

/**
 * 待妊检但配种还不满 18 天：不给“登记妊检”入口，返回置灰提示（“7 天后可妊检”）；可以登记时返回空串。
 * 优先用服务端的 pending_check（是否已进入待妊检清单），没有时按配种天数判断。
 */
export function checkNotDueText(item?: { nextAction?: string | null; pendingCheck?: boolean | null; daysSinceMating?: number | null } | null): string {
  if (!item || String(item.nextAction || '').toUpperCase() !== 'PREG_CHECK') return ''
  const days = Number(item.daysSinceMating ?? 0)
  const due = item.pendingCheck ?? days >= PREG_CHECK_MIN_DAYS
  if (due) return ''
  return `${Math.max(1, PREG_CHECK_MIN_DAYS - days)} 天后可妊检`
}

export function todayISO(): string {
  return beijingToday()
}

export function addDays(date: string, days: number): string {
  if (!date) return ''
  const value = dayjs(date)
  return value.isValid() ? value.add(days, 'day').format('YYYY-MM-DD') : ''
}

export function diffDays(from: string, to: string): number {
  if (!from || !to) return 0
  return dayjs(to).startOf('day').diff(dayjs(from).startOf('day'), 'day')
}

export function monthDay(date?: string | null): string {
  if (!date) return '—'
  const value = dayjs(date)
  return value.isValid() ? value.format('M月D日') : '—'
}

export function monthDayWeek(date?: string | null): string {
  if (!date) return '—'
  const value = dayjs(date)
  return value.isValid() ? value.format('M月D日 ddd') : '—'
}

/** 按北京时间的“今天”判断（不用浏览器本地时区），与服务端校验一致。 */
export function isFuture(date: string): boolean {
  if (!date) return false
  const value = dayjs(date)
  return value.isValid() && value.format('YYYY-MM-DD') > todayISO()
}

export function animalStatusTone(status?: string | null): string {
  if (status === 'PREGNANT' || status === 'LACTATING') return 'primary'
  if (status === 'CULLED' || status === 'DEAD') return 'error'
  return 'neutral'
}

export function animalStatusText(status?: string | null, fallback?: string | null): string {
  if (fallback) return fallback
  switch (status) {
    case 'EMPTY': return '空怀'
    case 'MATED': return '已配种'
    case 'PREGNANT': return '妊娠'
    case 'LACTATING': return '哺乳'
    case 'CULLED': return '已淘汰'
    case 'DEAD': return '已死亡'
    default: return '—'
  }
}

export function dueTone(days?: number | null): 'warning' | 'primary' | 'neutral' {
  const value = Number(days ?? 0)
  if (value <= 3) return 'warning'
  if (value <= 7) return 'primary'
  return 'neutral'
}

export function dueBadgeText(days?: number | null): string {
  const value = Number(days ?? 0)
  if (value === 0) return '今天'
  if (value < 0) return `${Math.abs(value)}`
  return `${value}`
}

export function dueGroupLabel(group?: string | null): string {
  if (group === 'OVERDUE') return '已逾期'
  if (group === 'TODAY') return '今天'
  if (group === 'WITHIN_7') return '7 天内'
  if (group === 'LATER') return '更晚'
  return group || '其他'
}

export function recordTone(recordType?: string | null): { tone: string; icon: string } {
  switch (recordType) {
    case 'MATING': return { tone: 'primary', icon: 'favorite' }
    case 'PREG_CHECK': return { tone: 'primary', icon: 'fact_check' }
    case 'FARROWING': return { tone: 'primary', icon: 'add_box' }
    case 'WEANING': return { tone: 'neutral', icon: 'child_care' }
    case 'BREEDING_EXIT': return { tone: 'error', icon: 'heart_minus' }
    default: return { tone: 'neutral', icon: 'history' }
  }
}
