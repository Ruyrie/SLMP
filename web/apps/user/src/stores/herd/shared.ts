import { ApiError, dayjs, formatDate, today as beijingToday } from '@zmt/shared'
import type { BatchDetail, BatchListItem, PenStockItem } from '@/api/herd'

export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'
export type FormMode = 'create' | 'edit'

export function isForbiddenError(error: unknown): boolean {
  return error instanceof ApiError && error.isForbidden
}

export function errorText(error: unknown, fallback = '加载失败，请检查网络后重新加载'): string {
  if (error instanceof ApiError) {
    if (error.isNetwork) return '网络连接不稳定，没能取到数据。请检查网络后重新加载。'
    return error.message || fallback
  }
  if (error instanceof Error && error.message) return error.message
  return fallback
}

export function statusForError(error: unknown): PageStatus {
  return isForbiddenError(error) ? 'forbidden' : 'error'
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  const result: Record<string, string> = {}
  for (const item of error.fieldErrors) {
    if (item.field) result[item.field] = item.message
  }
  return result
}

export function sourceText(source?: string | null): string {
  if (source === 'SELF_BRED') return '自繁'
  if (source === 'PURCHASED') return '外购'
  return source || '—'
}

export function purposeText(purpose?: string | null): string {
  if (purpose === 'BREEDING') return '种用'
  if (purpose === 'COMMERCIAL') return '商品'
  return purpose || ''
}

export function batchStatusText(batch?: Pick<BatchListItem, 'status' | 'in_withdrawal'> | null): string {
  if (!batch) return '—'
  if (batch.in_withdrawal) return '休药中'
  if (batch.status === 'ENDED') return '已结束'
  if (batch.status === 'ACTIVE') return '在养'
  return batch.status || '—'
}

export function detailStatusText(detail?: Pick<BatchDetail, 'status' | 'withdrawal'> | null): string {
  if (!detail) return '—'
  if (detail.withdrawal?.in_withdrawal) return '休药中'
  if (detail.status === 'ENDED') return '已结束'
  if (detail.status === 'ACTIVE') return '在养'
  return detail.status || '—'
}

export function penStatusText(pen?: Pick<PenStockItem, 'display_status'> | null): string {
  if (!pen) return '—'
  if (pen.display_status === 'DISABLED') return '已停用'
  if (pen.display_status === 'EMPTY') return '空栏'
  if (pen.display_status === 'IN_USE') return '在用'
  return pen.display_status || '—'
}

export function penStatusTone(pen?: Pick<PenStockItem, 'display_status'> | null): 'primary' | 'neutral' | 'error' {
  if (pen?.display_status === 'DISABLED') return 'error'
  if (pen?.display_status === 'EMPTY') return 'neutral'
  return 'primary'
}

/** 月日（不含星期），用于表格和档案信息。 */
export function monthDay(value?: string | null): string {
  if (!value) return '—'
  const date = dayjs(value)
  return date.isValid() ? date.format('M月D日') : value
}

/** 带星期的日期，用于日期字段与摘要。 */
export function dateText(value?: string | null): string {
  return formatDate(value)
}

export function dateTimeText(value?: string | null): string {
  if (!value) return '—'
  const date = dayjs(value)
  return date.isValid() ? date.format('M月D日 HH:mm') : value
}

export function todayISO(): string {
  return beijingToday()
}

export function penHint(stock?: number | null, capacity?: number | null, unit = '头'): string {
  return `在栏 ${formatCount(stock)} ${unit} · 容量 ${formatCount(capacity)} ${unit}`
}

export function formatCount(value?: number | null): string {
  if (value === null || value === undefined) return '0'
  return Number(value).toLocaleString('en-US')
}

export function batchPensText(batch?: Pick<BatchListItem, 'pens'> | Pick<BatchDetail, 'pens'> | null, unit = '头'): string {
  if (!batch?.pens?.length) return '—'
  return batch.pens.map((pen) => `${pen.pen_name || '栏舍'} ${formatCount(pen.stock_count)} ${unit}`).join(' · ')
}

export function batchPenNames(batch?: Pick<BatchListItem, 'pens'> | null): string {
  if (!batch?.pens?.length) return '—'
  return batch.pens.map((pen) => pen.pen_name).filter(Boolean).join('、')
}

export function occupancyPercent(value?: number | null): number {
  if (!value || !Number.isFinite(Number(value))) return 0
  return Math.max(0, Math.min(100, Number(value) * 100))
}

export function overCapacityText(over?: number | null, unit = '头'): string {
  return over && over > 0 ? `超出 ${formatCount(over)} ${unit}` : ''
}
