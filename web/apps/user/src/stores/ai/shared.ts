/** 模块11 页面共用的错误与状态辅助。 */
import { ApiError } from '@zmt/shared'
import type { PageStatus } from '@/api/ai'

export type { PageStatus }

export function errorTextOf(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message || fallback
  if (error instanceof Error) return error.message || fallback
  return fallback
}

export function statusForError(error: unknown): PageStatus {
  if (error instanceof ApiError && error.isForbidden) return 'forbidden'
  return 'error'
}

/** 41001 记录已下架 / 已删除（知识原文、视频、专题）。 */
export function isOffShelf(error: unknown): boolean {
  return error instanceof ApiError && error.code === 41001
}

/** 40401 记录不存在或无权查看。 */
export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === 40401
}

/** SSE 响应在开流前是普通 JSON 错误：从 Error.message 里解析 message。 */
export function sseErrorMessage(error: unknown): string {
  const raw = error instanceof Error ? error.message : String(error || '')
  if (!raw) return '网络连接失败，请检查网络后重试'
  try {
    const body = JSON.parse(raw) as { message?: string }
    if (body && typeof body.message === 'string' && body.message) return body.message
  } catch {
    /* 非 JSON 时用原文 */
  }
  return raw
}

export const DECLARATION = '由 AI 生成，仅供参考'

export function stripCitationMarkers(text: string): string {
  return text.replace(/\[\^\d+]/g, '')
}

/** 复制回答：去掉角标，末尾附固定声明。 */
export function answerForCopy(text: string): string {
  const body = stripCitationMarkers(text || '').trim()
  return `${body}\n\n${DECLARATION}`
}
