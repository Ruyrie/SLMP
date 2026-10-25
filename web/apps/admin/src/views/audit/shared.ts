import { dayjs, formatDate, formatDateTime } from '@zmt/shared'

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

/** 列表里的“今天 07:52 / 昨天 18:20 / 10月12日 20:31” */
export function fmtLastLogin(value?: string | null, client?: string): string {
  if (!value) return '—'
  const d = dayjs(value)
  if (!d.isValid()) return String(value)
  const today = dayjs().startOf('day')
  const diff = d.startOf('day').diff(today, 'day')
  const prefix = diff === 0 ? '今天' : diff === -1 ? '昨天' : d.format('M月D日')
  const text = `${prefix} ${d.format('HH:mm')}`
  return client ? `${text} · ${client}` : text
}

export function fileSize(bytes?: number): string {
  if (!bytes || !Number.isFinite(Number(bytes))) return ''
  const size = Number(bytes)
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)}MB`
  if (size >= 1024) return `${Math.round(size / 1024)}KB`
  return `${size}B`
}

/** 省份/地区名，去掉“自治区 / 省 / 市”等后缀，用于地图标记。 */
export function shortRegion(name?: string): string {
  if (!name) return ''
  return name.replace(/(壮族自治区|回族自治区|维吾尔自治区|自治区|特别行政区|省|市)$/g, '')
}

export function statusTone(status?: string): string {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'error'
  if (status === 'PENDING') return 'warning'
  return 'neutral'
}

export function statusIcon(status?: string): string {
  if (status === 'APPROVED') return 'check'
  if (status === 'REJECTED') return 'block'
  if (status === 'PENDING') return 'schedule'
  return 'info'
}
