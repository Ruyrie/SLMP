import { dayjs, formatMoney, formatNumber, formatPercent, today } from '@zmt/shared'

export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

export const PAYMENT_METHODS = [
  { label: '银行转账', value: 'BANK_TRANSFER' },
  { label: '微信', value: 'WECHAT' },
  { label: '支付宝', value: 'ALIPAY' },
  { label: '现金', value: 'CASH' }
]

export function paymentMethodText(code?: string): string {
  return PAYMENT_METHODS.find((item) => item.value === code)?.label || code || '—'
}

/** 支出分类 6 档单色系（模块10 页面级变量，见 Web UI 设计文档 2.2） */
export const COST_COLORS = ['#C25E00', '#CE7E33', '#D99B61', '#E3B58A', '#EAC8A8', '#F0D8C2']

export const MINUS = '−'

export function amountText(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  return formatMoney(value)
}

/** 金额带正负号：收入 +、支出 −（U+2212），0 不带符号 */
export function signedAmount(value: number | string | null | undefined, digits = 2): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  if (n === 0) return '0'
  return `${n > 0 ? '+' : MINUS}${formatMoney(Math.abs(n), digits)}`
}

/** 单价固定两位小数（4.40、16.00），与 App 文案一致 */
export function priceText(value: number | string | null | undefined, digits = 2): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  return n.toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

export function percentText(value: number | string | null | undefined, digits = 1): string {
  if (value === null || value === undefined || value === '') return '—'
  return formatPercent(value, digits)
}

/** 比例变化：+32.1% / −16.0% / 0.0% */
export function signedPercent(value: number | string | null | undefined, digits = 1): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  const text = `${(Math.abs(n) * 100).toFixed(digits)}%`
  if (Math.abs(n) < 0.00005) return text
  return `${n > 0 ? '+' : MINUS}${text}`
}

export function trendText(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '环比无法比较'
  const n = Number(value)
  if (!Number.isFinite(n)) return '环比无法比较'
  if (Math.abs(n) < 0.00005) return '环比持平'
  return `${n > 0 ? '↑' : '↓'} 环比 ${signedPercent(n)}`
}

export type Tone = 'success' | 'error' | 'muted'

/** 有环比时的好坏颜色：按含义决定（收入上升 success、支出上升 error） */
export function trendTone(value: number | string | null | undefined, upIsGood = true): Tone {
  const n = Number(value)
  if (!Number.isFinite(n) || Math.abs(n) < 0.00005) return 'muted'
  return (n > 0) === upIsGood ? 'success' : 'error'
}

export function paymentTone(status?: string): 'primary' | 'success' | 'neutral' {
  if (status === 'PAID') return 'success'
  if (status === 'PARTIAL') return 'primary'
  return 'neutral'
}

export function paymentIcon(status?: string): string {
  return status === 'PAID' ? 'check_circle' : ''
}

export function dueTone(state?: string): 'error' | 'warning' | 'neutral' {
  if (state === 'OVERDUE') return 'error'
  if (state === 'DUE_SOON') return 'warning'
  return 'neutral'
}

export function sourceTone(sourceType?: string): 'primary' | 'neutral' {
  return sourceType && sourceType !== 'MANUAL' ? 'primary' : 'neutral'
}

export function recordIcon(categoryIcon?: string): string {
  return categoryIcon || 'receipt_long'
}

export function monthText(month: string): string {
  const value = dayjs(`${month}-01`)
  return value.isValid() ? `${value.year()}年${value.month() + 1}月` : month
}

export function monthShort(month: string): string {
  const value = dayjs(`${month}-01`)
  return value.isValid() ? `${value.month() + 1}月` : month
}

export function currentMonth(): string {
  return today().slice(0, 7)
}

export function shiftMonth(month: string, delta: number): string {
  return dayjs(`${month}-01`).add(delta, 'month').format('YYYY-MM')
}

/** 本月（含今天）的日期范围说明 */
export function monthRangeText(start?: string, end?: string): string {
  if (!start || !end) return ''
  const s = dayjs(start)
  const e = dayjs(end)
  if (!s.isValid() || !e.isValid()) return ''
  return `${s.month() + 1}月${s.date()}日–${e.date()}日`
}

export function dateText(value?: string): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? `${d.month() + 1}月${d.date()}日` : value
}

export function dateTimeText(value?: string): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? `${d.month() + 1}月${d.date()}日 ${d.format('HH:mm')}` : value
}

export function countUnitText(value: number | string | null | undefined, unit = '头'): string {
  return `${formatNumber(value ?? 0)} ${unit}`
}

export function daysText(days?: number): string {
  if (days === null || days === undefined) return ''
  if (days === 0) return '今天'
  if (days === 1) return '明天'
  return `${days} 天后`
}

/** 待收款提醒说明：服务端优先，缺失时用 App 文案 */
export function reminderRule(serverText?: string): string {
  return (
    serverText ||
    '约定收款日前 1 天提醒你和场长，逾期后每 3 天提醒一次。没有填写约定收款日期的，按出栏后第 7 天计算。'
  )
}

export function managerRolesText(role?: string): string {
  if (role === 'OWNER') return '所有者'
  if (role === 'MANAGER') return '场长'
  if (role === 'STAFF') return '员工'
  return ''
}

export function errorField(errors: { field: string; message: string }[], field: string): string {
  return errors.find((item) => item.field === field || item.field.startsWith(`${field}.`))?.message || ''
}
