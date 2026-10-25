export interface AuditLocationMarker {
  longitude: number
  latitude: number
  label: string
  tone: 'primary' | 'neutral'
}

type Coordinate = number | string | null | undefined

/** 审核地图使用真实经纬度；缺失坐标不能被 Number(null / '') 转成零点。 */
export function locationMarker(
  location: { longitude?: Coordinate; latitude?: Coordinate } | null | undefined,
  label: string,
  tone: AuditLocationMarker['tone'] = 'primary'
): AuditLocationMarker | null {
  const longitude = location?.longitude
  const latitude = location?.latitude
  if (longitude == null || latitude == null || String(longitude).trim() === '' || String(latitude).trim() === '') return null
  const lng = Number(longitude)
  const lat = Number(latitude)
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || Math.abs(lng) > 180 || Math.abs(lat) > 85.05112878) return null
  return { longitude: lng, latitude: lat, label, tone }
}
