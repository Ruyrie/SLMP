import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ApiError } from './error'
import type { ApiResponse } from '../types/api'

export interface HttpOptions {
  baseURL: string
  client: string
  /** 用户端访问令牌（只存在内存）；管理端不传，凭 Cookie。 */
  getToken?: () => string | null | undefined
  /** 当前养殖场 ID，作为 X-Farm-Id。 */
  getFarmId?: () => number | string | null | undefined
  /** “全部养殖场”视角时带 scope=ALL。 */
  isAllFarms?: () => boolean
  /** 40101 时刷新令牌；返回 true 表示已刷新，可重发一次。 */
  refresh?: () => Promise<boolean>
  onAuthExpired?: (reason?: string) => void
  timeout?: number
}

interface RetriableConfig extends AxiosRequestConfig {
  __retried?: boolean
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/**
 * 统一请求层：解包 {code,message,data,trace_id}，错误转 ApiError，
 * 40101 单飞刷新后重发一次；取消请求原样抛出（isCancel）。
 */
export class HttpClient {
  readonly instance: AxiosInstance
  private refreshing: Promise<boolean> | null = null

  constructor(private readonly options: HttpOptions) {
    this.instance = axios.create({
      baseURL: options.baseURL,
      timeout: options.timeout ?? 15000,
      withCredentials: true
    })
    this.instance.interceptors.request.use((config) => {
      const token = this.options.getToken?.()
      if (token) config.headers.Authorization = `Bearer ${token}`
      config.headers['X-Client'] = this.options.client
      const farmId = this.options.getFarmId?.()
      if (farmId !== null && farmId !== undefined && farmId !== '') {
        config.headers['X-Farm-Id'] = String(farmId)
      }
      const method = (config.method || 'get').toUpperCase()
      if (method !== 'GET' && !config.headers['Idempotency-Key']) {
        config.headers['Idempotency-Key'] = uuid()
      }
      if (this.options.isAllFarms?.() && !config.params?.scope) {
        config.params = { ...(config.params || {}), scope: 'ALL' }
      }
      return config
    })
    this.instance.interceptors.response.use(
      (response) => response,
      async (error: AxiosError<ApiResponse<unknown>>) => {
        if (axios.isCancel(error)) throw error
        const response = error.response
        if (!response) {
          throw new ApiError({
            code: -1,
            message: error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络连接失败，请检查网络后重试',
            status: 0
          })
        }
        const body = response.data
        const code = typeof body?.code === 'number' ? body.code : response.status
        const apiError = new ApiError({
          code,
          message: body?.message || `请求失败（${response.status}）`,
          data: body?.data,
          traceId: body?.trace_id,
          status: response.status
        })
        const config = error.config as RetriableConfig | undefined
        if (code === 40101 && config && !config.__retried && this.options.refresh) {
          config.__retried = true
          try {
            this.refreshing = this.refreshing || this.options.refresh().finally(() => (this.refreshing = null))
            const ok = await this.refreshing
            if (ok) return this.instance.request(config)
          } catch {
            /* 刷新失败按登录失效处理 */
          }
        }
        if (code === 40101 || code === 40104 || code === 40106) {
          this.options.onAuthExpired?.(apiError.reason)
        }
        throw apiError
      }
    )
  }

  async request<T>(config: AxiosRequestConfig): Promise<T> {
    const response = await this.instance.request<ApiResponse<T>>(config)
    return response.data.data as T
  }

  async raw<T>(config: AxiosRequestConfig) {
    return this.instance.request<ApiResponse<T>>(config)
  }

  get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>({ ...config, url, method: 'GET', params })
  }

  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>({ ...config, url, method: 'POST', data })
  }

  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>({ ...config, url, method: 'PUT', data })
  }

  del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>({ ...config, url, method: 'DELETE' })
  }

  /** 上传：multipart，带进度；不设总超时。 */
  async upload<T>(url: string, form: FormData, onProgress?: (percent: number) => void): Promise<T> {
    const response = await this.instance.post<ApiResponse<T>>(url, form, {
      timeout: 0,
      onUploadProgress: (event) => {
        if (onProgress && event.total) onProgress(Math.round((event.loaded / event.total) * 100))
      }
    })
    return response.data.data as T
  }
}

export { axios }
