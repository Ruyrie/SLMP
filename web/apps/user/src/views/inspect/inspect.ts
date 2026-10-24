import { dayjs, formatDate, formatDateTime, formatNumber, formatPercent, formatTime } from '@zmt/shared'
import type { DiagnosisDetail, InspectionDetail, MediaItem } from '@/api/inspect'

/** 页面共用的小工具：状态文案、颜色语义与媒体信息。 */

export function weekdayText(date?: string): string {
  if (!date) return ''
  const d = dayjs(date)
  return d.isValid() ? `周${'日一二三四五六'[d.day()]}` : ''
}

export function monthDay(date?: string): string {
  if (!date) return '—'
  const d = dayjs(date)
  return d.isValid() ? d.format('M月D日') : String(date)
}

export function dateWithWeek(date?: string): string {
  if (!date) return '—'
  const d = dayjs(date)
  return d.isValid() ? `${d.format('M月D日')} ${weekdayText(date)}` : String(date)
}

export function dayTime(value?: string): string {
  if (!value) return '—'
  return formatDateTime(value)
}

export function timeOnly(value?: string): string {
  if (!value) return ''
  return /^\d{2}:\d{2}/.test(value) ? value.slice(0, 5) : formatTime(value)
}

export function locationLabel(status?: string, distanceM?: number | null): string {
  if (status === 'ON_SITE') return distanceM ? `现场 · 距养殖场 ${formatNumber(distanceM)} 米` : '现场'
  if (status === 'OFF_SITE') return distanceM ? `非现场 · 距养殖场 ${distanceText(distanceM)}` : '非现场'
  return '无位置'
}

export function distanceText(m?: number | null): string {
  if (m === null || m === undefined) return ''
  if (m >= 1000) return `${(m / 1000).toFixed(1)} 千米`
  return `${formatNumber(m)} 米`
}

export function locationTone(status?: string): string {
  if (status === 'ON_SITE') return 'neutral'
  if (status === 'OFF_SITE') return 'warning'
  return 'neutral'
}

export function inspectionStatusType(status?: string): string {
  if (status === 'ABNORMAL') return 'error'
  if (status === 'PENDING' || status === 'MISSED') return 'primary'
  return 'neutral'
}

export function reportTagType(status?: string): string {
  if (status === 'PENDING') return 'warning'
  if (status === 'DIAGNOSED') return 'primary'
  if (status === 'HANDLED' || status === 'CLOSED') return 'neutral'
  return 'neutral'
}

export function reportTagIcon(status?: string): string {
  if (status === 'PENDING') return 'hourglass_top'
  if (status === 'CLOSED') return 'check'
  return ''
}

export function reportStatusText(status?: string, fallback?: string): string {
  if (fallback) return fallback
  const map: Record<string, string> = {
    PENDING: '待诊断',
    DIAGNOSED: '已诊断',
    HANDLED: '已处理',
    CLOSED: '已结案'
  }
  return status ? map[status] || status : '—'
}

export function urgencyType(urgency?: string): string {
  if (urgency === 'URGENT') return 'error'
  if (urgency === 'ATTENTION') return 'warning'
  return 'neutral'
}

export function urgencyIcon(urgency?: string): string {
  if (urgency === 'URGENT') return 'emergency'
  if (urgency === 'ATTENTION') return 'priority_high'
  return ''
}

export function likelihoodPercent(likelihood?: string): number {
  if (likelihood === 'HIGH') return 75
  if (likelihood === 'MEDIUM') return 45
  return 20
}

export function mediaIsVideo(item?: unknown): boolean {
  const media = item as { media_type?: string; mime_type?: string; content_type?: string } | null | undefined
  if (!media) return false
  return media.media_type === 'VIDEO' || media.mime_type?.startsWith('video/') || media.content_type?.startsWith('video/') || false
}

export function durationText(seconds?: number | null): string {
  if (seconds === null || seconds === undefined) return ''
  const s = Math.max(0, Math.round(seconds))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

export function mediaDuration(item?: MediaItem | null): string {
  if (!item) return ''
  if (item.duration_sec !== undefined && item.duration_sec !== null) return durationText(item.duration_sec)
  const ms = (item as { duration_ms?: number }).duration_ms
  return ms ? durationText(ms / 1000) : ''
}

export function mediaName(item?: MediaItem | null): string {
  if (!item) return ''
  return item.source_text || (mediaIsVideo(item) ? '视频' : '照片')
}

export function inspectionItemText(detail?: InspectionDetail | null): string {
  const rows = detail?.item_results || []
  const abnormal = rows.filter((row) => row.option_abnormal)
  if (abnormal.length) return abnormal.map((row) => `${row.item_name} ${row.option_name}`).join('、')
  if (detail?.is_manual_marked) return `手动标记 · ${detail.manual_reason || ''}`
  if (rows.length) return '各项正常'
  return detail?.is_abnormal ? detail.abnormal_summary || '异常' : '正常'
}

export function buildSteps(detail?: DiagnosisDetail | null): Array<{ title: string; desc?: string; status: string; icon?: string }> {
  const steps = detail?.steps || []
  const titles: Record<string, string> = { PENDING: '待诊断', DIAGNOSED: '已诊断', HANDLED: '已处理', CLOSED: '已结案' }
  return steps.map((step) => ({
    title: titles[step.code] || step.code,
    desc: step.desc || (step.time ? `${monthDay(step.time)} ${timeOnly(step.time)}` : ''),
    status: step.state === 'DONE' ? 'done' : step.state === 'CURRENT' ? 'current' : 'wait'
  }))
}

export function reportDateText(value?: string): string {
  if (!value) return '—'
  return formatDate(value)
}

export function sickRate(sick?: number, stock?: number): string {
  if (!sick || !stock) return '—'
  return formatPercent(sick / stock, 1)
}

export function isManagerRole(role?: string): boolean {
  return role === 'OWNER' || role === 'MANAGER'
}
