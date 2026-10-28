/**
 * 高德 JS API 2.0 加载与坐标工具（养殖场选点用）。
 * - Key：VITE_AMAP_KEY；安全密钥只在开发环境从 VITE_AMAP_SECURITY_CODE 读取，
 *   生产环境走同源 /_AMapService 代理（Nginx 追加 jscode），生产包里不出现安全密钥。
 * - Key 缺失或脚本加载失败时抛错，由选点对话框降级为手填。
 */
import AMapLoader from '@amap/amap-jsapi-loader'

/* 高德 JS API 没有随包的类型声明，这里只描述用到的少量成员。 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AMapNamespace = any

export const AMAP_PLUGINS = ['AMap.Geocoder', 'AMap.AutoComplete', 'AMap.PlaceSearch', 'AMap.Geolocation', 'AMap.Scale', 'AMap.ToolBar']

let loading: Promise<AMapNamespace> | null = null

export function amapKey(): string {
  return String(import.meta.env.VITE_AMAP_KEY || '').trim()
}

/** 安全密钥配置：必须在加载 JS API 之前写到 window 上。 */
export function applySecurityConfig(target: Record<string, unknown> = window as unknown as Record<string, unknown>): void {
  if (target._AMapSecurityConfig) return
  if (import.meta.env.DEV) {
    const code = String(import.meta.env.VITE_AMAP_SECURITY_CODE || '').trim()
    if (code) target._AMapSecurityConfig = { securityJsCode: code }
    return
  }
  target._AMapSecurityConfig = { serviceHost: `${window.location.origin}/_AMapService` }
}

export function loadAmap(): Promise<AMapNamespace> {
  if (loading) return loading
  const key = amapKey()
  if (!key) return Promise.reject(new Error('NO_KEY'))
  applySecurityConfig()
  loading = AMapLoader.load({ key, version: '2.0', plugins: AMAP_PLUGINS }).catch((error: unknown) => {
    loading = null
    throw error
  })
  return loading
}

/* ---------------- 坐标转换：WGS-84 → GCJ-02（高德地图使用 GCJ-02） ---------------- */

const A = 6378245.0
const EE = 0.00669342162296594323

function outOfChina(lng: number, lat: number): boolean {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(x: number, y: number): number {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin((y / 3.0) * Math.PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * Math.PI) + 320 * Math.sin((y * Math.PI) / 30.0)) * 2.0) / 3.0
  return ret
}

function transformLng(x: number, y: number): number {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin((x / 3.0) * Math.PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * Math.PI) + 300.0 * Math.sin((x / 30.0) * Math.PI)) * 2.0) / 3.0
  return ret
}

/** 浏览器定位得到的是 WGS-84，直接画在高德底图上会偏几百米，先转成 GCJ-02。 */
export function wgs84ToGcj02(lng: number, lat: number): [number, number] {
  if (outOfChina(lng, lat)) return [lng, lat]
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * Math.PI)
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * Math.PI)
  return [lng + dLng, lat + dLat]
}

/* ---------------- 逆地理结果 → 表单字段 ---------------- */

export interface RegeoFields {
  address: string
  province: string
  city: string
  district: string
  township: string
  adcode: string
}

function text(value: unknown): string {
  // 高德对缺失字段返回空数组（例如直辖市的 city）
  return typeof value === 'string' ? value.trim() : ''
}

/** 把高德逆地理编码的 regeocode 转成表单字段；直辖市没有 city 时用省名补上。 */
export function regeoToFields(regeocode: {
  formattedAddress?: unknown
  addressComponent?: Record<string, unknown>
} | null | undefined): RegeoFields | null {
  if (!regeocode) return null
  const component = regeocode.addressComponent || {}
  const province = text(component.province)
  const adcode = text(component.adcode)
  if (!province && !adcode) return null
  const district = text(component.district)
  return {
    address: text(regeocode.formattedAddress),
    province,
    city: text(component.city) || (/^(北京|上海|天津|重庆)/.test(province) ? province : ''),
    district,
    township: text(component.township),
    adcode
  }
}

export function formatCoord(value: number): string {
  return Number(value).toFixed(6)
}
