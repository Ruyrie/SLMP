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

/**
 * 回答里的引用角标：约定写法 [^3]，也兼容模型偶尔写出的 [^P3]（P 为段落编号前缀）及括号内多余空格。
 * 每次调用返回新的正则，避免全局正则的 lastIndex 串用。
 */
export function citationPattern(): RegExp {
  return /\[\^\s*[Pp]?(\d+)\s*]/g
}

export function stripCitationMarkers(text: string): string {
  return text.replace(citationPattern(), '')
}

/** 段落里是否引用了第 seq 条资料（[^3] 或 [^P3]） */
export function hasCitation(text: string, seq: number): boolean {
  for (const match of text.matchAll(citationPattern())) {
    if (Number(match[1]) === seq) return true
  }
  return false
}

/**
 * 这条消息是否正在生成：流的 key 是重新生成 / 提问时的服务端消息 ID，
 * 本轮提问的本地气泡在完成前仍用负 ID，服务端 ID 记在 local_server_id 上，两者都要认。
 */
export function isStreamingMessage(streamKey: number | null | undefined, message: { id: number; local_server_id?: number }): boolean {
  if (!streamKey) return false
  return streamKey === message.id || (!!message.local_server_id && streamKey === message.local_server_id)
}

/** 复制回答：去掉角标，末尾附固定声明。 */
export function answerForCopy(text: string): string {
  const body = stripCitationMarkers(text || '').trim()
  return `${body}\n\n${DECLARATION}`
}
