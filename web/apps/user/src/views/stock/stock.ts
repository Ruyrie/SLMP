import { dayjs, formatMoney, formatNumber } from '@zmt/shared'
import type { CostAllocationRow, Qty, StockFlowItem } from '@/api/stock'

/** 库存模块共用展示工具：数量、包装换算、流水图标与状态标签。 */

export function numberValue(value?: number | string | null): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

export function qtyValue(qty?: Qty | null): number {
  return numberValue(qty?.value)
}

export function qtyUnit(qty?: Qty | null, fallback = ''): string {
  return qty?.unit_text || fallback
}

/** 记账数量 + 单位 */
export function qtyText(qty?: Qty | null, fallbackUnit = ''): string {
  if (!qty) return '—'
  return `${formatNumber(qty.value ?? 0, decimals(qty.value))} ${qtyUnit(qty, fallbackUnit)}`.trim()
}

/** 包装优先：30 瓶（300 头份）；没有包装时 500 千克 */
export function packQtyText(qty?: Qty | null, fallbackUnit = ''): string {
  if (!qty) return '—'
  if (qty.pack_unit && Number(qty.pack_count || 0) > 0) {
    const pack = `${formatNumber(qty.pack_count ?? 0)} ${qty.pack_unit}`
    const rest = Number(qty.rest_value || 0) > 0 ? ` + ${formatNumber(qty.rest_value ?? 0, decimals(qty.rest_value))} ${qty.unit_text || ''}` : ''
    return `${pack}${rest}（${formatNumber(qty.value ?? 0, decimals(qty.value))} ${qty.unit_text || ''}）`
  }
  return qtyText(qty, fallbackUnit)
}

/** 按实际精度显示记账数量，最多三位，不把零头显示为零。 */
export function decimals(value?: number | string | null): number {
  const n = Number(value || 0)
  return Math.min(3, String(n).split('.')[1]?.length || 0)
}

export function priceText(value?: number | null, unit = '千克'): string {
  if (value === null || value === undefined) return '—'
  return `每${unit} ${formatMoney(value)} 元`
}

export function moneyText(value?: number | null): string {
  return value === null || value === undefined ? '—' : `${formatMoney(value)} 元`
}

export function flowQty(flow: StockFlowItem): string {
  if (!flow.qty) return '—'
  const sign = flow.direction === 'IN' ? '+' : '−'
  return `${sign}${formatNumber(flow.qty.value ?? 0, decimals(flow.qty.value))} ${flow.qty.unit_text || ''}`
}

export function flowIcon(flowType?: string): string {
  if (flowType === 'FEED_USE') return 'rice_bowl'
  if (flowType === 'INBOUND') return 'inventory_2'
  if (flowType === 'VACCINE_USE') return 'vaccines'
  if (flowType === 'MEDICATION_USE') return 'medication'
  if (flowType === 'DISINFECT_USE') return 'sanitizer'
  if (flowType === 'MANUAL_USE') return 'output'
  if (flowType === 'RETURN') return 'assignment_return'
  if (flowType === 'SCRAP' || flowType === 'LOSS') return 'delete'
  if (flowType === 'OTHER_OUT') return 'output'
  if (flowType === 'STOCKTAKE_GAIN') return 'trending_up'
  if (flowType === 'STOCKTAKE_LOSS') return 'trending_down'
  return 'inventory_2'
}

export function categoryIcon(category?: string): string {
  if (category === 'VACCINE') return 'vaccines'
  if (category === 'DISINFECTANT') return 'sanitizer'
  if (category === 'VET_DRUG') return 'medication'
  return 'inventory_2'
}

export function categoryText(category?: string): string {
  if (category === 'VACCINE') return '疫苗'
  if (category === 'DISINFECTANT') return '消毒剂'
  if (category === 'VET_DRUG') return '兽药'
  return ''
}

export function lotStatusTag(status?: string): { type: string; icon: string; text: string } {
  if (status === 'RECALLED') return { type: 'error', icon: 'block', text: '已停用' }
  if (status === 'EXPIRED') return { type: 'error', icon: 'event_busy', text: '已过期' }
  if (status === 'BANNED') return { type: 'error', icon: 'block', text: '禁用药' }
  if (status === 'NEAR_EXPIRY') return { type: 'warning', icon: 'schedule', text: '临期' }
  return { type: 'neutral', icon: '', text: '正常' }
}

export function lotStatusText(lot: { lot_status?: string; lot_status_text?: string; days_to_expire?: number | null }): string {
  if (lot.lot_status === 'NEAR_EXPIRY') {
    const days = Number(lot.days_to_expire || 0)
    return days > 0 ? `临期 ${days} 天` : '临期'
  }
  return lot.lot_status_text || lotStatusTag(lot.lot_status).text
}

export function allocationText(row: CostAllocationRow): string {
  const head = row.head_count ? `存栏 ${formatNumber(row.head_count)} 头` : ''
  const ratio = row.ratio !== null && row.ratio !== undefined ? `${(Number(row.ratio) * 100).toFixed(1)}%` : ''
  return [head, ratio].filter(Boolean).join(' · ')
}

export function shortDate(value?: string): string {
  if (!value) return '—'
  return value.length >= 10 ? value.slice(5, 10) : value
}

export function monthDayText(value?: string): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('M月D日') : String(value)
}

export function flowTimeText(value?: string): string {
  if (!value) return '—'
  const d = dayjs(value)
  if (!d.isValid()) return String(value)
  return `${d.format('M月D日 HH:mm')}`
}

export function datetimeText(value?: string): string {
  if (!value) return '—'
  return value.slice(0, 16)
}

export function remainingText(item: { remaining_days?: number | null; remaining_basis?: string }): string {
  if (item.remaining_basis === 'NONE') return '暂无消耗'
  if (item.remaining_days === null || item.remaining_days === undefined) return '—'
  return `${formatNumber(item.remaining_days)} 天`
}

export function daysText(days?: number | null): string {
  if (days === null || days === undefined) return ''
  if (days < 0) return `已过期 ${Math.abs(days)} 天`
  return `还有 ${days} 天到期`
}
