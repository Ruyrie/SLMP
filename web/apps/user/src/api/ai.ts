/**
 * 模块11 AI 养殖助手与养殖课堂接口（API-K01–K21）。
 * 服务端 JSON 使用 snake_case（Controller/VO 为准），这里按真实响应声明。
 */
import type { AxiosRequestConfig } from 'axios'
import { ApiError, appConfig, type FileVO } from '@zmt/shared'
import { http } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'

/* ---------------- 通用 ---------------- */

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  has_more: boolean
  tab_counts?: Record<string, number>
}

export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

function config(signal?: AbortSignal): AxiosRequestConfig | undefined {
  return signal ? { signal } : undefined
}

/** 生成请求标识（SSE 与提交类请求使用）。 */
export function newRequestKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** SSE 与 keepalive 上报共用的鉴权请求头。 */
export function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const auth = useAuthStore()
  const farm = useFarmStore()
  const headers: Record<string, string> = {
    'X-Client': appConfig.client,
    ...(extra || {})
  }
  if (auth.accessToken) headers.Authorization = `Bearer ${auth.accessToken}`
  if (farm.currentFarmId) headers['X-Farm-Id'] = String(farm.currentFarmId)
  return headers
}

/* ---------------- AI 历史会话（API-K01–K05） ---------------- */

export interface AiSessionItem {
  id: number
  title?: string
  topic?: string
  topic_text?: string
  batch_no?: string
  round_count?: number
  last_asked_at?: string
  date_group?: string
  date_group_text?: string
  has_correction?: boolean
  context_truncated?: boolean
  has_epidemic_alert?: boolean
}

export interface LinkedBatch {
  batch_id: number
  batch_no?: string
  category_name?: string
  stage_name?: string
  breed_name?: string
  pen_id?: number
  pen_name?: string
  day_age?: number
  current_count?: number
  count_unit_text?: string
}

export interface AiSessionDetail {
  id: number
  title?: string
  round_count?: number
  has_correction?: boolean
  context_truncated?: boolean
  has_epidemic_alert?: boolean
  batch_link_state?: string
  last_round_no?: number
  batch?: LinkedBatch
  invalid_batch_no?: string
}

export interface CitationBrief {
  id: number
  seq_no: number
  knowledge_id: number
  title?: string
  category_name?: string
  source_type?: string
  icon_type?: string
}

export interface Correction {
  content?: string
  display_time?: string
}

export interface AiMessage {
  id: number
  session_id?: number
  round_no?: number
  asked_at?: string
  question_text?: string
  images?: FileVO[]
  batch_id?: number | null
  batch_no?: string | null
  context_course_title?: string | null
  status?: string
  answer_type?: string | null
  answer_text?: string | null
  citations?: CitationBrief[]
  rating?: string | null
  rated_at?: string | null
  can_rate?: boolean
  can_copy?: boolean
  in_context?: boolean
  correction?: Correction | null
  error_code?: number | null
  error_message?: string | null
  answered_at?: string | null
}

export interface AiMessageSlice {
  list: AiMessage[]
  has_more: boolean
}

export interface LinkableBatches {
  batches: LinkedBatch[]
  panel_hint?: string
  empty_hint?: string
}

export interface SessionQuery {
  page?: number
  size?: number
  keyword?: string
}

export function fetchSessions(params: SessionQuery, signal?: AbortSignal): Promise<PageResult<AiSessionItem>> {
  return http().get<PageResult<AiSessionItem>>('/ai/sessions', { ...params }, config(signal))
}

export function fetchSession(sessionId: number, signal?: AbortSignal): Promise<AiSessionDetail> {
  return http().get<AiSessionDetail>(`/ai/sessions/${sessionId}`, undefined, config(signal))
}

export interface MessageSliceQuery {
  before_round_no?: number
  after_round_no?: number
  size?: number
}

export function fetchMessages(
  sessionId: number,
  params: MessageSliceQuery = {},
  signal?: AbortSignal
): Promise<AiMessageSlice> {
  return http().get<AiMessageSlice>(`/ai/sessions/${sessionId}/messages`, { ...params }, config(signal))
}

export function changeSessionBatch(sessionId: number, batchId: number | null): Promise<AiSessionDetail> {
  return http().request<AiSessionDetail>({
    url: `/ai/sessions/${sessionId}`,
    method: 'PATCH',
    data: { batch_id: batchId }
  })
}

export interface AiSessionCreateBody {
  /** 1–40 字；不传或空白时服务端入库并显示“新对话” */
  title?: string
  batch_id?: number | null
}

/**
 * 新建空会话（API-K01 扩展）。
 * 实测响应为 AiSessionDetail（id/title/round_count/batch_link_state 等），不含 batch_id、created_at、updated_at；
 * 关联批次以 batch_link_state + batch 表达，列表项信息由 GET /ai/sessions 返回。
 */
export function createSession(body: AiSessionCreateBody = {}): Promise<AiSessionDetail> {
  return http().post<AiSessionDetail>('/ai/sessions', body)
}

/** 重命名会话（API-K04 扩展）：只传 title 时保留原关联批次。 */
export function renameSession(sessionId: number, title: string): Promise<AiSessionDetail> {
  return http().request<AiSessionDetail>({
    url: `/ai/sessions/${sessionId}`,
    method: 'PATCH',
    data: { title }
  })
}

/** 删除会话（软删除，重复删除幂等返回成功）。 */
export function deleteSession(sessionId: number): Promise<null> {
  return http().del<null>(`/ai/sessions/${sessionId}`)
}

export function fetchLinkableBatches(signal?: AbortSignal): Promise<LinkableBatches> {
  return http().get<LinkableBatches>('/ai/linkable-batches', undefined, config(signal))
}

export function fetchMessage(messageId: number): Promise<AiMessage> {
  return http().get<AiMessage>(`/ai/messages/${messageId}`)
}

export type AiRating = 'USEFUL' | 'USELESS' | null

export function rateMessage(messageId: number, rating: AiRating): Promise<{ rating?: string | null; rated_at?: string }> {
  return http().request({ url: `/ai/messages/${messageId}/rating`, method: 'PUT', data: { rating } })
}

/* ---------------- SSE 流事件 ---------------- */

export interface StreamMeta {
  message_id: number
  session_id: number
  round_no: number
  session_created: boolean
  batch_link_state?: string
}

export interface StreamDelta {
  text: string
}

export interface StreamCitation {
  citations: CitationBrief[]
}

export interface StreamDone {
  answer_type?: string
  finish_reason?: string
  message?: AiMessage
}

export interface StreamError {
  code?: number
  message?: string
  status?: string
}

export interface AskPayload {
  session_id?: number
  question_text?: string
  image_file_ids?: number[]
  batch_id?: number
  context_course_id?: number
}

export function askUrl(): string {
  return `${appConfig.apiBase}/ai/messages`
}

export function retryUrl(messageId: number): string {
  return `${appConfig.apiBase}/ai/messages/${messageId}/retry`
}

/* ---------------- 知识原文（API-K12） ---------------- */

export interface KnowledgeBlock {
  para_key?: string
  para_type?: string
  text?: string
  content_html?: string
  image_url?: string
}

export interface EmbeddedVideo {
  course_id: number
  title?: string
  duration_sec?: number
  cover_url?: string
}

export interface RelatedKnowledge {
  id: number
  title?: string
  category_name?: string
  source_type?: string
  source_type_text?: string
  version_no?: number
  icon_type?: string
}

export interface KnowledgeDetail {
  id: number
  title?: string
  category_name?: string
  source_type?: string
  source_type_text?: string
  version_no?: number
  updated_at?: string
  share_url?: string
  embedded_video?: EmbeddedVideo | null
  blocks?: KnowledgeBlock[]
  related_items?: RelatedKnowledge[]
  highlight_para_key?: string | null
  cited_version_no?: number | null
  updated_after_answer?: boolean
}

export function fetchKnowledge(knowledgeId: number, citationId?: number, signal?: AbortSignal): Promise<KnowledgeDetail> {
  return http().get<KnowledgeDetail>(
    `/knowledge-items/${knowledgeId}`,
    citationId ? { citation_id: citationId } : undefined,
    config(signal)
  )
}

/* ---------------- 养殖课堂（API-K14–K21） ---------------- */

export interface CourseCategory {
  id: number
  name?: string
  sort_no?: number
}

export type LearnStatus = 'NOT_STARTED' | 'LEARNING' | 'COMPLETED'

export interface CourseCard {
  id: number
  title?: string
  cover_url?: string
  duration_sec?: number
  published_at?: string
  category_name?: string
  learn_status?: LearnStatus | string
  position_sec?: number
  progress_ratio?: number
  topic_id?: number | null
  topic_name?: string
  section_no?: number | null
}

export interface ContinueWatching {
  course_id: number
  title?: string
  cover_url?: string
  category_name?: string
  duration_sec?: number
  position_sec?: number
  remaining_sec?: number
  progress_ratio?: number
  topic_name?: string
  section_no?: number
}

export interface TopicCard {
  id: number
  name?: string
  category_name?: string
  cover_url?: string
  course_count?: number
  total_duration_sec?: number
  learned_count?: number
  last_learned_at?: string
}

export interface TopicDetail extends TopicCard {
  intro?: string
  action?: 'START' | 'CONTINUE' | 'REVIEW' | string
  courses?: CourseCard[]
  resume?: { course_id?: number; section_no?: number; position_sec?: number } | null
}

export interface WatchHistory extends CourseCard {
  last_watched_at?: string
}

export interface VideoSource {
  quality: 'P720' | 'P480' | string
  hls_url?: string
  size_bytes?: number
}

export interface CourseTopicBrief {
  id: number
  name?: string
  learned_count?: number
  course_count?: number
}

export interface CourseProgressInfo {
  position_sec?: number
  completed?: boolean
  resume_position_sec?: number
}

export interface CoursePlay {
  id: number
  title?: string
  category_name?: string
  duration_sec?: number
  source_label?: string
  updated_at?: string
  cover_url?: string
  share_url?: string
  sources?: VideoSource[]
  knowledge_id?: number | null
  section_no?: number | null
  next_course_id?: number | null
  topic?: CourseTopicBrief | null
  progress?: CourseProgressInfo
  related_courses?: CourseCard[]
}

export function fetchCourseCategories(signal?: AbortSignal): Promise<CourseCategory[]> {
  return http().get<CourseCategory[]>('/course-categories', undefined, config(signal))
}

export function fetchContinueWatching(signal?: AbortSignal): Promise<ContinueWatching | null> {
  return http().get<ContinueWatching | null>('/courses/continue', undefined, config(signal))
}

export function fetchTopics(params: { page?: number; size?: number; sort?: string } = {}, signal?: AbortSignal): Promise<PageResult<TopicCard>> {
  return http().get<PageResult<TopicCard>>('/course-topics', { ...params }, config(signal))
}

export function fetchTopic(topicId: number, signal?: AbortSignal): Promise<TopicDetail> {
  return http().get<TopicDetail>(`/course-topics/${topicId}`, undefined, config(signal))
}

export interface CourseQuery {
  page?: number
  size?: number
  category_id?: number
  learn_status?: string
  keyword?: string
  sort?: string
}

export function fetchCourses(params: CourseQuery, signal?: AbortSignal): Promise<PageResult<CourseCard>> {
  return http().get<PageResult<CourseCard>>('/courses', { ...params }, config(signal))
}

export function fetchCoursePlay(courseId: number, signal?: AbortSignal): Promise<CoursePlay> {
  return http().get<CoursePlay>(`/courses/${courseId}`, undefined, config(signal))
}

export function fetchWatchHistory(params: { page?: number; size?: number }, signal?: AbortSignal): Promise<PageResult<WatchHistory>> {
  return http().get<PageResult<WatchHistory>>('/course-progress', { ...params }, config(signal))
}

/* ---------------- 进度上报（API-K20） ---------------- */

export type ProgressEvent = 'START' | 'TICK' | 'PAUSE' | 'SEEK' | 'SWITCH' | 'LEAVE' | 'END'
export type EntryType = 'COURSE' | 'TOPIC' | 'CONTINUE' | 'HISTORY' | 'KNOWLEDGE'
export type VideoQuality = 'P720' | 'P480'

export interface CourseProgressPayload {
  play_token: string
  event: ProgressEvent
  position_sec: number
  watched_sec: number
  quality: VideoQuality
  network_type?: string
  entry_type?: EntryType
  /** yyyy-MM-dd HH:mm:ss（北京时间） */
  client_time: string
  ask_ai_clicked?: boolean
  knowledge_clicked?: boolean
}

export interface ProgressResult {
  position_sec?: number
  max_position_sec?: number
  progress_ratio?: number
  completed?: boolean
  just_completed?: boolean
  learn_status?: LearnStatus | string
  topic_learned_count?: number
}

/** 进度上报用 keepalive fetch，离开页面（pagehide）也能送达；带鉴权请求头。 */
export async function reportCourseProgress(
  courseId: number,
  payload: CourseProgressPayload
): Promise<ProgressResult | null> {
  const response = await fetch(`${appConfig.apiBase}/courses/${courseId}/progress`, {
    method: 'POST',
    credentials: 'include',
    keepalive: true,
    headers: authHeaders({ 'Content-Type': 'application/json', Accept: 'application/json' }),
    body: JSON.stringify(payload)
  })
  const text = await response.text()
  let body: { code?: number; message?: string; data?: ProgressResult | null } | null = null
  try {
    body = text ? (JSON.parse(text) as { code?: number; message?: string; data?: ProgressResult | null }) : null
  } catch {
    body = null
  }
  if (!response.ok || !body || body.code !== 0) {
    throw new ApiError({
      code: typeof body?.code === 'number' ? body.code : response.status,
      message: body?.message || `进度保存失败（${response.status}）`,
      status: response.status
    })
  }
  return body.data ?? null
}

/* ---------------- 上传与语音（模块11 附图、语音输入） ---------------- */

export function uploadAiImage(file: File, onProgress?: (percent: number) => void): Promise<FileVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('scene', 'AI_IMAGE')
  return http().upload<FileVO>('/files', form, onProgress)
}

export interface SpeechResult {
  recognized?: boolean
  text?: string
  duration_ms?: number
}

export function recognizeSpeech(audio: Blob, durationMs: number): Promise<SpeechResult> {
  const form = new FormData()
  form.append('audio', audio, 'voice.wav')
  form.append('duration_ms', String(Math.max(0, Math.round(durationMs))))
  return http().upload<SpeechResult>('/ai/speech-recognitions', form)
}
