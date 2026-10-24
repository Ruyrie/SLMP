/** 环境配置集中读取，业务代码不直接访问 import.meta.env。 */
export interface AppConfig {
  title: string
  apiBase: string
  client: string
  pollInterval: number
  amapKey: string
}

export const appConfig: AppConfig = {
  title: import.meta.env.VITE_APP_TITLE || '智牧通',
  apiBase: import.meta.env.VITE_API_BASE || '/api/v1',
  client: import.meta.env.VITE_CLIENT || 'WEB',
  pollInterval: Number(import.meta.env.VITE_POLL_INTERVAL || 60000),
  amapKey: import.meta.env.VITE_AMAP_KEY || ''
}
