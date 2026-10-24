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
