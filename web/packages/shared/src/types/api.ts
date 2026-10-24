/** 服务端统一响应与通用 VO（字段名保持 snake_case，与 05 号文档一致）。 */
import type { components } from './generated'

export type Schemas = components['schemas']

/** 按 schema 名取类型，例如 Schema<'HomeVO'> */
export type Schema<K extends keyof Schemas> = Schemas[K]

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  trace_id: string
}

export interface PageVO<T> {
  list: T[]
  total: number
  page: number
  size: number
  has_more: boolean
  tab_counts?: Record<string, number>
}

export interface FieldError {
  field: string
  message: string
}

export interface OptionVO {
  value: string | number
  label: string
  disabled?: boolean
  hint?: string
}

export interface FileVO {
  file_id: number
  url?: string
  thumb_url?: string
  status?: string
  status_text?: string
  duration_ms?: number
  width?: number
  height?: number
  size?: number
  content_type?: string
  [key: string]: unknown
}

export interface PageQuery {
  page?: number
  size?: number
  keyword?: string
  [key: string]: unknown
}

export const PAGE_SIZE = 20
