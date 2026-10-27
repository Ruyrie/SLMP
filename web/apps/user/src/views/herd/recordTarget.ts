import type { TimelineItem } from '@/api/herd'

const recordTypes: Record<string, string> = {
  ENTRY: 'BATCH_ENTRY', TRANSFER: 'BATCH_TRANSFER', DEATH: 'BATCH_DEATH', SALE: 'SALE_ORDER'
}

/** 修改申请使用原业务流水 ID，不是批次 ID 或时间线 ID。 */
export function stockRecordTarget(item: TimelineItem): { biz_type: string; biz_id: string } | null {
  const type = recordTypes[item.record_type || '']
  const id = Number(item.biz_id)
  if (!type || !Number.isSafeInteger(id) || id <= 0) return null
  if (item.biz_type && item.biz_type !== 'BATCH' && item.biz_type !== type) return null
  return { biz_type: type, biz_id: String(id) }
}
