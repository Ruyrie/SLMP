import { ApiError, dayjs, formatDate, formatDateTime, formatNumber, now, today } from '@zmt/shared'
import type { BatchOption, RecordLog } from '@/api/health'
import { healthApi } from '@/api/health'

export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

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

/** 列表里的日期：10月8日 */
export function monthDay(value?: string | null): string {
  if (!value) return '—'
  const date = dayjs(value)
  return date.isValid() ? date.format('M月D日') : String(value)
}

/** 日期 + 星期；共享 formatDate */
export function dateText(value?: string | null): string {
  if (!value) return '—'
  return formatDate(value)
}

/** 10月18日 16:20 */
export function dateTimeText(value?: string | null): string {
  if (!value) return '—'
  return formatDateTime(value)
}

export function todayISO(): string {
  return today()
}

/** 当前北京时间，datetime-local 的取值格式 YYYY-MM-DDTHH:mm。 */
export function nowInput(): string {
  return now().slice(0, 16).replace(' ', 'T')
}

export function toDateTimePayload(value: string): string {
  if (!value) return ''
  return value.length === 16 ? `${value}:00` : value
}

export function shiftDate(days: number): string {
  return dayjs(todayISO()).add(days, 'day').format('YYYY-MM-DD')
}

export function isBeforeToday(value?: string | null): boolean {
  if (!value) return false
  return dayjs(value).isBefore(dayjs(todayISO()), 'day')
}

export function batchLabel(batch?: BatchOption | null): string {
  if (!batch) return ''
  const pen = batch.pens?.[0]?.pen_name || ''
  return pen ? `${batch.batch_no || '批次'} · ${pen}` : String(batch.batch_no || '批次')
}

export function batchOverviewText(batch?: BatchOption | null): string {
  if (!batch) return ''
  return `${formatNumber(batch.day_age || 0)} 日龄 · 存栏 ${formatNumber(batch.current_count || 0)} ${batch.unit_text || '头'}`
}

export async function loadBatchOptions(): Promise<BatchOption[]> {
  const page = await healthApi.activeBatches()
  return page.list || []
}

/** 浓度按“1:n”中的 n 比较；任一侧格式不对时按字符串比较。 */
export function sameConcentration(left?: string | null, right?: string | null): boolean {
  const value = (text?: string | null): number | null => {
    if (!text) return null
    const match = /^\s*\d+\s*:\s*(\d+)\s*$/.exec(text)
    return match ? Number(match[1]) : null
  }
  const a = value(left)
  const b = value(right)
  if (a !== null && b !== null) return a === b
  return (left || '') === (right || '')
}

export function isValidConcentration(text?: string | null): boolean {
  if (!text) return false
  const match = /^\s*(\d+)\s*:\s*(\d+)\s*$/.exec(text)
  return !!match && Number(match[2]) > 0
}

export function formatQty(value: number | string | null | undefined, unit?: string | null): string {
  if (value === null || value === undefined || value === '') return '—'
  const text = formatNumber(value, Number(value) % 1 === 0 ? 0 : 2)
  return unit ? `${text} ${unit}` : text
}

const STATUS_TEXT: Record<string, string> = {
  PRE_ENTRY: '待确认',
  PRE_ENTRY_DONE: '已确认',
  PENDING: '待接种',
  POSTPONED: '已延期',
  MISSED: '漏免',
  DONE: '已接种',
  CANCELLED: '已取消',
  ACTIVE: '启用中',
  PAUSED: '已暂停',
  ENDED: '已结束',
  OVERDUE: '已逾期',
  PLAN_TASK: '计划任务',
  EMPTY_PEN: '空栏消毒',
  EMERGENCY: '紧急消毒',
  USER_DELETED: '手动删除',
  BATCH_ENDED: '批次已结束',
  PEN_EMPTY: '栏舍已无种畜'
}

function logValue(field: string, value?: string | null): string {
  if (!value) return '—'
  if (STATUS_TEXT[value]) return STATUS_TEXT[value]
  if (/date$|_at$/.test(field) && /^\d{4}-\d{2}-\d{2}/.test(value)) {
    return value.length > 10 ? formatDateTime(value) : formatDate(value)
  }
  return value
}

export interface HealthLogItem {
  title: string
  time: string
  desc: string
  tone?: string
  icon?: string
}

const LOG_ACTIONS: Record<string, Record<string, string>> = {
  IMMUNE_PLAN: {
    CREATE: '新增免疫项目',
    UPDATE: '修改剂量和方式',
    PRE_ENTRY_CONFIRM: '确认入栏前已接种',
    PRE_ENTRY_JOIN: '加入免疫计划',
    PRE_ENTRY_UNDO: '撤销确认',
    POSTPONE: '调整计划日期',
    CANCEL: '删除免疫项目',
    DELETE: '删除免疫项目'
  },
  IMMUNE_RECORD: { CREATE: '登记接种', UPDATE: '修改接种记录', DELETE: '删除接种记录' },
  MEDICATION_RECORD: { CREATE: '登记用药', UPDATE: '修改用药记录', DELETE: '删除用药记录' },
  DISINFECT_RECORD: { CREATE: '登记消毒', UPDATE: '修改消毒记录', DELETE: '删除消毒记录' }
}

const LOG_ICONS: Record<string, string> = {
  CREATE: 'add',
  PRE_ENTRY_CONFIRM: 'check_circle',
  PRE_ENTRY_JOIN: 'playlist_add',
  PRE_ENTRY_UNDO: 'undo',
  POSTPONE: 'event_repeat',
  CANCEL: 'delete',
  DELETE: 'delete',
  UPDATE: 'edit'
}

/** 修改记录 → zw-timeline 数据（业务动作、操作人、字段前后值、原因）。 */
export function healthLogItems(logs: RecordLog[], bizType: string): HealthLogItem[] {
  return (logs || []).map((log) => {
    const action = log.op_action || ''
    const label =
      LOG_ACTIONS[bizType]?.[action] ||
      (log.op_action_text && !/^[A-Z_]+$/.test(log.op_action_text) ? log.op_action_text : '') ||
      action ||
      '修改记录'
    const changes = (log.changes || [])
      .filter((change) => change.field_name || change.before || change.after)
      .map((change) => `${change.field_name || '内容'}：${logValue(change.field_name || '', change.before)} → ${logValue(change.field_name || '', change.after)}`)
      .join('；')
    const descParts = [changes, log.reason ? `原因：${log.reason}` : ''].filter(Boolean)
    return {
      title: label,
      time: dateTimeText(log.created_at),
      desc: [log.operator?.name || '系统', ...descParts].filter(Boolean).join(' · '),
      tone: action === 'CREATE' ? 'primary' : action === 'DELETE' || action === 'CANCEL' ? 'error' : 'neutral',
      icon: LOG_ICONS[action] || 'edit_note'
    }
  })
}

/** 提交修改申请：统一跳 WU-U01，由 U01 自动打开抽屉。 */
export function changeRequestQuery(bizType: string, bizId: number): Record<string, string> {
  return { action: 'create', biz_type: bizType, biz_id: String(bizId) }
}
