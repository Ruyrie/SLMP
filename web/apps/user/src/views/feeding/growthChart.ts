import type { GrowthCurve, WeighStdPoint } from '@/api/feeding'

/** 对比按同一日龄坐标展示，范围包含所有批次，不能只用主批次的建议范围。 */
export function growthAgeLabels(curve: GrowthCurve | null): string[] {
  if (!curve) return []
  const ages = [curve.x_min, curve.x_max]
  for (const point of curve.actual_points || []) ages.push(point.day_age)
  for (const series of curve.compare_series || []) {
    for (const point of series.points || []) {
      if (Number.isFinite(point.avg_weight)) ages.push(point.day_age)
    }
  }
  const valid = ages.filter((age): age is number => typeof age === 'number' && Number.isFinite(age))
  const start = Math.floor(Math.min(...(valid.length ? valid : [0])) / 10) * 10
  const end = Math.max(start + 10, Math.ceil(Math.max(...(valid.length ? valid : [10])) / 10) * 10)
  const interval = growthAgeLabelInterval(end - start + 1)
  // 尾部与相邻刻度留足一个间隔，避免组件强制显示的最后标签挤在前一标签旁。
  const alignedEnd = start + Math.ceil((end - start) / interval) * interval
  return Array.from({ length: alignedEnd - start + 1 }, (_, index) => String(start + index))
}

export function growthAgeLabelInterval(labelCount: number): number {
  const raw = Math.max(1, labelCount - 1) / 6
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const residual = raw / magnitude
  return Math.max(1, (residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 5 ? 5 : 10) * magnitude)
}

export function growthComparisonNotices(curve: GrowthCurve | null): string[] {
  return (curve?.compare_series || []).flatMap((series) => {
    const count = (series.points || []).filter((point) => Number.isFinite(point.day_age) && Number.isFinite(point.avg_weight)).length
    if (count > 1) return []
    return [`${series.batch_no || '对比批次'}${count ? ' 仅有 1 个数据点，已显示为圆点' : ' 暂无可对比的称重数据'}`]
  })
}

export function standardReferenceDay(curve: GrowthCurve | null): number | undefined {
  return curve?.metrics?.day_age ?? curve?.current_day_age
}

export function growthWeightDigits(value: number | null | undefined): number {
  return value !== null && value !== undefined && Math.abs(value) < 1 ? 2 : 1
}

/** 标准只在已公布的日龄范围内插值，超出范围不外推。 */
export function standardAt(points: WeighStdPoint[], day: number, field: 'std_weight' | 'lower' | 'upper' = 'std_weight'): number | null {
  const sorted = points.filter((point) => Number.isFinite(point.day_age) && Number.isFinite(point[field]))
    .map((point) => ({ day: Number(point.day_age), value: Number(point[field]) }))
    .sort((a, b) => a.day - b.day)
  if (!sorted.length || day < sorted[0].day || day > sorted[sorted.length - 1].day) return null
  const exact = sorted.find((point) => point.day === day)
  if (exact) return exact.value
  for (let index = 1; index < sorted.length; index += 1) {
    const previous = sorted[index - 1]
    const next = sorted[index]
    if (day < next.day) return previous.value + (next.value - previous.value) * (day - previous.day) / (next.day - previous.day)
  }
  return null
}

export function standardBandAt(curve: GrowthCurve | null, day: number): { lower: number; upper: number } | null {
  if (!curve?.std_available) return null
  const points = curve.std_points || []
  const value = standardAt(points, day)
  if (value === null) return null
  const allowed = Number(curve.metrics?.allowed_deviation ?? 0.1)
  return {
    lower: standardAt(points, day, 'lower') ?? value * (1 - allowed),
    upper: standardAt(points, day, 'upper') ?? value * (1 + allowed)
  }
}

/** 小体重品类也按实际变化范围选择刻度。 */
export function growthWeightRange(values: number[]): { min: number; max: number } {
  const finite = values.filter(Number.isFinite)
  if (!finite.length) return { min: 0, max: 1 }
  const low = Math.min(...finite)
  const high = Math.max(...finite)
  const raw = Math.max(high - low, high * 0.05, 0.01) / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const residual = raw / magnitude
  const step = (residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 5 ? 5 : 10) * magnitude
  const min = Math.max(0, Math.floor(low / step) * step)
  return { min, max: Math.max(Math.ceil(high / step) * step, min + step) }
}
