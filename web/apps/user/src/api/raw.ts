import axios from 'axios'
import { appConfig, toApiError } from '@zmt/shared'

/** 登录、刷新、找回等未登录请求用；不附加 Authorization，但仍带 Cookie 与 X-Client。 */
export const raw = axios.create({
  baseURL: appConfig.apiBase,
  timeout: 20000,
  withCredentials: true
})

raw.interceptors.request.use((config) => {
  config.headers['X-Client'] = appConfig.client
  return config
})

raw.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(toApiError(error))
)
