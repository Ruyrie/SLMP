import { ApiError, dayjs, today as beijingToday } from '@zmt/shared'

export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type LoadState = 'loading' | 'ready' | 'error' | 'empty'

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

export function todayISO(): string {
  return beijingToday()
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

export function shortDay(date?: string | null): string {
  if (!date) return ''
  const value = dayjs(date)
  return value.isValid() ? value.format('D日') : ''
}

export function dayAgeAt(entryDate: string | undefined, date: string): number {
  if (!entryDate || !date) return 0
  return dayjs(date).startOf('day').diff(dayjs(entryDate).startOf('day'), 'day')
}

export function deviationText(ratio?: number | null, digits = 1): string {
  if (ratio === null || ratio === undefined || !Number.isFinite(Number(ratio))) return '—'
  const value = Number(ratio) * 100
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(digits)}%`
}

export function percentText(ratio?: number | null, digits = 1): string {
  if (ratio === null || ratio === undefined || !Number.isFinite(Number(ratio))) return '—'
  return `${(Number(ratio) * 100).toFixed(digits)}`
}

export function minutesText(minutes?: number | null): string {
  const total = Math.max(0, Math.round(Number(minutes || 0)))
  const hours = Math.floor(total / 60)
  const rest = total % 60
  if (!hours) return `${rest} 分钟`
  if (!rest) return `${hours} 小时`
  return `${hours} 小时 ${rest} 分`
}

export function taskStatusTag(status?: string | null, abnormal?: string | null): { text: string; type: string; icon: string } {
  if (abnormal && abnormal !== 'NONE') return { text: '投喂异常', type: 'warning', icon: 'warning' }
  if (status === 'OVERDUE') return { text: '已逾期', type: 'error', icon: 'schedule' }
  if (status === 'DONE' || status === 'MANUAL_DONE') return { text: '已投喂', type: 'neutral', icon: 'check' }
  return { text: '待投喂', type: 'primary', icon: '' }
}

export function growthTag(status?: string | null): { text: string; type: string; icon: string } | null {
  if (status === 'SLOW') return { text: '生长偏慢', type: 'warning', icon: 'trending_down' }
  if (status === 'FAST') return { text: '生长偏快', type: 'warning', icon: 'trending_up' }
  return null
}

export function planStateText(state?: string | null): string {
  if (state === 'CURRENT') return '当前阶段'
  if (state === 'ENDED') return '已结束'
  return '待进入'
}
