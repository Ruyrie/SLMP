import type { ProductionReminder } from '@/api/herd'
import { resolveLinkTarget, type NavTarget } from '@/views/today/todo'

type ReminderLink = Pick<ProductionReminder, 'route' | 'batch_id' | 'todo_id' | 'report_id'>

export function reminderIcon(type?: string): string {
  if (type === 'ABNORMAL_REPORT') return 'report'
  if (type?.includes('WITHDRAWAL')) return 'medication'
  if (type?.includes('FEED')) return 'inventory_2'
  return 'notifications'
}

/**
 * 生产提醒的去向是 App 页面编号：休药 D05、饲料不足 M01、兽药不足 M05、临期 / 过期 M06、
 * 未处理异常上报 I06（带 report_id 进诊断详情）、巡检异常 I04（App 的巡检历史，Web 落到今日巡检，
 * 异常栏舍在那里转上报）。与待办共用 resolveLinkTarget，批次参数改成目标页读取的 batchId。
 * 返回 null 表示网页端没有对应页面。
 */
export function reminderTarget(item: ReminderLink): NavTarget | null {
  const route = item.route || ''
  if ((route === 'L04' || route === 'L06') && item.batch_id) {
    return { name: 'WU-L04', params: { batchId: String(item.batch_id) } }
  }
  if (route === 'L04') return null
  if (route === 'I04' || route === 'I01') return { name: 'WU-I01' }
  if (route === 'D02') {
    return item.batch_id ? { name: 'WU-D02', query: { batchId: String(item.batch_id) } } : { name: 'WU-D02' }
  }
  const params: Record<string, string> = {}
  if (item.batch_id) params.batch_id = String(item.batch_id)
  if (item.todo_id) params.todo_id = String(item.todo_id)
  if (item.report_id) params.report_id = String(item.report_id)
  return resolveLinkTarget({ route, params })
}
