import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import isoWeek from 'dayjs/plugin/isoWeek'

dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.extend(isoWeek)
dayjs.locale('zh-cn')

export const TZ = 'Asia/Shanghai'

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export function today(): string {
  return dayjs().tz(TZ).format('YYYY-MM-DD')
}

export function now(): string {
  return dayjs().tz(TZ).format('YYYY-MM-DD HH:mm:ss')
}

export function formatNumber(value: number | string | null | undefined, digits = 0): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  return n.toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

/** 金额：默认两位小数，整数不补零（原型 68,448 元 / 12.50 元） */
export function formatMoney(value: number | string | null | undefined, digits = 2): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  const fixed = n.toFixed(digits)
  const trimmed = fixed.replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1')
  return Number(trimmed).toLocaleString('en-US', { maximumFractionDigits: digits })
}

export function formatPercent(value: number | string | null | undefined, digits = 1): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return `${(n * 100).toFixed(digits)}%`
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const d = dayjs(value)
  if (!d.isValid()) return String(value)
  const current = dayjs().tz(TZ)
  if (d.year() !== current.year()) return d.format('YYYY年M月D日')
  return `${d.format('M月D日')} ${WEEK[d.day()]}`
}

export function formatDateShort(value: string | null | undefined): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD') : String(value)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : String(value)
}

export function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const text = String(value)
  if (/^\d{2}:\d{2}/.test(text)) return text.slice(0, 5)
  const d = dayjs(text)
  return d.isValid() ? d.format('HH:mm') : text
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) return '—'
  const s = Math.max(0, Math.round(Number(seconds)))
  const m = Math.floor(s / 60)
  const rest = s % 60
  return `${m}:${String(rest).padStart(2, '0')}`
}

/** 数字与中文单位之间空一格（200 头、68,448 元）；拉丁单位紧跟（5MB、720P） */
export function withUnit(value: string | number | null | undefined, unit?: string): string {
  if (value === null || value === undefined || value === '') return '—'
  if (!unit) return String(value)
  const latin = /^[A-Za-z%]+$/.test(unit)
  return `${value}${latin ? '' : ' '}${unit}`
}

export function diffDays(from: string, to: string): number {
  return dayjs(to).startOf('day').diff(dayjs(from).startOf('day'), 'day')
}

export { dayjs }
