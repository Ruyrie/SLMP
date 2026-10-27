/** 养殖课堂页面共用的小工具（封面示意、学习状态、时长文案）。 */
import { formatDuration } from '@zmt/shared'

export function coverKind(category?: string | null): string {
  const text = category || ''
  if (text.includes('饲养')) return 'feed'
  if (text.includes('疾病')) return 'sick'
  if (text.includes('用药')) return 'drug'
  if (text.includes('繁育')) return 'pig'
  if (text.includes('免疫')) return 'chick'
  return 'course'
}

export function learnStatusText(status?: string | null): string {
  if (status === 'COMPLETED') return '已学完'
  if (status === 'LEARNING') return '学习中'
  return '未观看'
}

/** 0–100 的进度，用于进度条。 */
export function progressPercent(ratio?: number | null, completed?: boolean): number {
  if (completed) return 100
  const value = Number(ratio)
  if (!Number.isFinite(value)) return 0
  return Math.max(0, Math.min(100, value * 100))
}

/** 卡片标注“看到”的是续播位置，进度条也使用同一位置，避免最远历史位置造成误导。 */
export function courseProgressPercent(course: {
  position_sec?: number | null
  duration_sec?: number | null
  progress_ratio?: number | null
  learn_status?: string | null
}): number {
  if (course.learn_status === 'COMPLETED') return 100
  const duration = Number(course.duration_sec)
  const position = Number(course.position_sec)
  if (course.position_sec != null && Number.isFinite(position) && Number.isFinite(duration) && duration > 0) {
    return progressPercent(position / duration)
  }
  return progressPercent(course.progress_ratio)
}

export function topicDurationText(courseCount?: number, totalDurationSec?: number): string {
  const count = Number(courseCount || 0)
  const minutes = Math.max(1, Math.round(Number(totalDurationSec || 0) / 60))
  return `${count} 节 · 约 ${minutes} 分钟`
}

/** 课程卡第二行：分类 · 学习状态（看到 2:43 为 primary 色）。 */
export function courseWatchText(status?: string | null, positionSec?: number | null): string {
  if (status === 'COMPLETED') return '已学完'
  if (status === 'LEARNING') return `看到 ${formatDuration(positionSec || 0)}`
  return '未观看'
}

export function timeEvery(value?: string | null): string {
  return value || ''
}
