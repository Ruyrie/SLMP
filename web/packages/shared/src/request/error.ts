import axios from 'axios'
import type { FieldError } from '../types/api'

export interface ApiErrorInit {
  code: number
  message: string
  data?: unknown
  traceId?: string
  status?: number
}

/** 统一的接口错误：页面只需要 message、fieldErrors 与 traceId。 */
export class ApiError extends Error {
  readonly code: number
  readonly data?: unknown
  readonly traceId?: string
  readonly status?: number

  constructor(init: ApiErrorInit) {
    super(init.message)
    this.name = 'ApiError'
    this.code = init.code
    this.data = init.data
    this.traceId = init.traceId
    this.status = init.status
  }

  get isNetwork(): boolean {
    return this.code === -1
  }

  get isUnauthorized(): boolean {
    return this.code === 40101 || this.code === 40104 || this.code === 40106
  }

  get isForbidden(): boolean {
    return this.code === 40302 || this.code === 40303 || this.code === 40304 || this.code === 40308
  }

  get fieldErrors(): FieldError[] {
    const data = this.data as { field_errors?: FieldError[] } | undefined
    return Array.isArray(data?.field_errors) ? data!.field_errors : []
  }

  get reason(): string | undefined {
    const data = this.data as { reason?: string } | undefined
    return data?.reason
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/** 把 axios / 未知异常统一转成 ApiError（用于不经过 HttpClient 的原始请求）。 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (axios.isAxiosError(error)) {
    const response = error.response
    if (!response) {
      return new ApiError({
        code: -1,
        message: error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络连接失败，请检查网络后重试',
        status: 0
      })
    }
    const body = response.data as { code?: number; message?: string; data?: unknown; trace_id?: string } | undefined
    return new ApiError({
      code: typeof body?.code === 'number' ? body.code : response.status,
      message: body?.message || `请求失败（${response.status}）`,
      data: body?.data,
      traceId: body?.trace_id,
      status: response.status
    })
  }
  return new ApiError({ code: -1, message: error instanceof Error ? error.message : '请求失败' })
}
