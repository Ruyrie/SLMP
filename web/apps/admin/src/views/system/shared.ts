import { dayjs, formatDate, formatDateTime, formatPercent } from '@zmt/shared'

const WEEK = ['日', '一', '二', '三', '四', '五', '六']

export function fmtDateTime(value?: string | null, withYear = false): string {
  if (!value) return '—'
  const d = dayjs(value)
  if (!d.isValid()) return String(value)
  return d.format(withYear ? 'YYYY-MM-DD HH:mm' : 'M月D日 HH:mm')
}

export function fmtFull(value?: string | null): string {
  return value ? formatDateTime(value) : '—'
}

export function fmtDate(value?: string | null): string {
  if (!value) return '—'
  const d = dayjs(value)
  if (!d.isValid()) return String(value)
  return `${d.format('M月D日')} 周${WEEK[d.day()]}`
}

export function fmtShortDate(value?: string | null): string {
  if (!value) return '—'
  const d = dayjs(value)
  if (!d.isValid()) return String(value)
  return d.format('M月D日')
}

export function fmtTime(value?: string | null): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('HH:mm') : String(value)
}

export function fmtLastLogin(value?: string | null, client?: string): string {
  if (!value) return '—'
  const d = dayjs(value)
  if (!d.isValid()) return String(value)
  const diff = d.startOf('day').diff(dayjs().startOf('day'), 'day')
  const prefix = diff === 0 ? '今天' : diff === -1 ? '昨天' : d.format('M月D日')
  return `${prefix} ${d.format('HH:mm')}${client ? ` · ${client}` : ''}`
}

export function fmtApproved(value?: string | null): string {
  if (!value) return '—'
  const d = dayjs(value)
  if (!d.isValid()) return String(value)
  const now = dayjs()
  if (d.isSame(now, 'day')) return `今天 ${d.format('HH:mm')}`
  if (d.year() === now.year()) return d.format('M月D日')
  return d.format('YYYY年M月D日')
}

export function pct(value?: number | null, digits = 1): string {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '—'
  return formatPercent(Number(value), digits)
}

export function hoursText(value?: number | null): string {
  if (value === null || value === undefined || !Number.isFinite(Number(value)) || Number(value) < 0) return '—'
  return `${Number(value).toFixed(1)} 小时`
}

export function dateRange(start?: string | null, end?: string | null): string {
  if (!start && !end) return '—'
  if (!start) return fmtShortDate(end)
  if (!end) return fmtShortDate(start)
  return `${fmtShortDate(start)}–${fmtShortDate(end)}`
}

export function fmtValue(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—'
  return typeof value === 'number' ? value.toLocaleString('en-US') : String(value)
}

export function shortRegion(name?: string): string {
  if (!name) return ''
  return name.replace(/(壮族自治区|回族自治区|维吾尔自治区|自治区|特别行政区|省|市)$/g, '')
}

export function mapPoint(lng?: number | null, lat?: number | null): { x: number; y: number } {
  const x = lng === null || lng === undefined ? 50 : ((Number(lng) - 73) / (135 - 73)) * 100
  const y = lat === null || lat === undefined ? 40 : ((54 - Number(lat)) / (54 - 18)) * 100
  return {
    x: Math.min(95, Math.max(5, x)),
    y: Math.min(94, Math.max(6, y))
  }
}

export { formatDate }

