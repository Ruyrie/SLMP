import type { RouteLocationNormalizedLoaded } from 'vue-router'

/** 读取路由 query 中的正整数 ID（操作日志“打开对应记录”、消息跳转等带入），无效时返回 null */
export function routeQueryId(route: RouteLocationNormalizedLoaded, name = 'id'): number | null {
  const value = route.query[name]
  const id = typeof value === 'string' && /^[1-9]\d*$/.test(value) ? Number(value) : NaN
  return Number.isSafeInteger(id) ? id : null
}

/** 读取路由 query 中的文本参数（去掉首尾空格），没有时返回空串 */
export function routeQueryText(route: RouteLocationNormalizedLoaded, name: string): string {
  const value = route.query[name]
  return typeof value === 'string' ? value.trim() : ''
}
