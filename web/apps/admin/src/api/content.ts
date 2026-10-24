import type { PageVO } from '@zmt/shared'
import { http } from '@/api/client'

export interface NoticeSummary {
  month_sent_count: number
  month_important_count: number
  month_revoked_count: number
  scheduled_count: number
  draft_count: number
  avg_read_rate_30d?: number
  next_scheduled_at?: string
}

export interface NoticeRow {
  id: number
  title: string
  image_count: number
  content_length: number
  level: string
  level_text: string
  scope_type: string
  scope_text: string
  condition_text?: string | null
  account_count?: number | null
  status: string
  status_text: string
  scheduled_at?: string | null
  sent_at?: string | null
  revoked_at?: string | null
  canceled_at?: string | null
  read_rate?: number | null
  read_before_revoke?: number | null
  creator_name?: string
  updated_at: string
}

export interface NoticeImage {
  file_id: number
  url?: string
  thumb_url?: string
}

export interface NoticeFarm {
  farm_id: number
  name: string
}

export interface NoticeRoleStat {
  role: string
  role_text: string
  read: number
  delivered: number
  rate?: number | null
}

export interface NoticeTimelineItem {
  action: string
  action_text: string
  time: string
  admin_name?: string
  desc?: string
}

export interface NoticeDetail extends NoticeRow {
  content?: string
  image?: NoticeImage | null
  send_mode?: string
  send_mode_text?: string
  roles?: string[]
  province_codes?: string[]
  category_ids?: number[]
  farms?: NoticeFarm[]
  est_account_count?: number
  est_farm_count?: number
  delivered_count?: number | null
  read_count?: number | null
  unread_count?: number | null
  push_open_count?: number | null
  stat_at?: string | null
  role_stats?: NoticeRoleStat[]
  creator?: { name?: string; at?: string }
  sender?: { name?: string; at?: string }
  revoked_by_name?: string
  revoke_reason?: string
  canceled_by_name?: string
  cancel_reason?: string
  timeline?: NoticeTimelineItem[]
  version?: number
}

export interface NoticeQuery {
  status?: string
  level?: string
  scope_type?: string
  keyword?: string
  page?: number
  size?: number
}

export interface NoticePayload {
  submit_action: 'DRAFT' | 'SCHEDULE' | 'SEND_NOW'
  level: string
  title: string
  content?: string
  image_file_id?: number | null
  scope_type?: string
  roles?: string[]
  province_codes?: string[]
  category_ids?: number[]
  farm_ids?: number[]
  scheduled_at?: string | null
  copied_from_id?: number | null
  version?: number
}

export interface AudienceEstimate {
  account_count: number
  farm_count: number
}

export interface ExportResult {
  file_id: number
  file_name: string
  status: string
  url?: string
  row_count?: number
  expire_at?: string
}

export function getNoticeSummary() {
  return http().get<NoticeSummary>('/notices/summary')
}

export function getNotices(query: NoticeQuery) {
  return http().get<PageVO<NoticeRow>>('/notices', { ...query })
}

export function getNoticeDetail(id: number) {
  return http().get<NoticeDetail>(`/notices/${id}`)
}

export function estimateNoticeAudience(payload: {
  scope_type: string
  roles?: string[]
  province_codes?: string[]
  category_ids?: number[]
  farm_ids?: number[]
}) {
  return http().post<AudienceEstimate>('/notices/audience-estimate', payload)
}

export function createNotice(payload: NoticePayload) {
  return http().post<{ id: number; status: string; status_text: string; est_account_count?: number }>('/notices', payload)
}

export function updateNotice(id: number, payload: NoticePayload) {
  return http().put<{ id: number; status: string; version?: number }>(`/notices/${id}`, payload)
}

export function cancelNotice(id: number, reason: string, version?: number) {
  return http().post(`/notices/${id}/cancel`, { reason, version })
}

export function revokeNotice(id: number, reason: string, version?: number) {
  return http().post(`/notices/${id}/revoke`, { reason, version })
}

export function deleteNotice(id: number, version?: number) {
  return http().del(`/notices/${id}?version=${version ?? ''}`)
}

export function exportNoticeReadStats(payload: NoticeQuery) {
  return http().post<ExportResult>('/notices/read-stats/export', payload)
}

export function getNoticeFarmOptions(keyword = '') {
  return http().get<NoticeFarm[]>('/notices/farm-options', { keyword })
}

/* ---------------- 市场行情 ---------------- */

export interface MarketPriceRow {
  config_id: number
  price_id?: number | null
  province_code: string
  province_name: string
  category_id: number
  category_name: string
  breed_id: number
  breed_name: string
  price_type: string
  price_type_text: string
  price?: number | null
  price_unit?: string | null
  price_unit_text?: string | null
  change_rate?: number | null
  prev_price?: number | null
  prev_price_date?: string | null
  is_prev_corrected?: boolean
  is_over_threshold?: boolean
  row_status: string
  row_status_text: string
  review_kind?: string | null
  review_kind_text?: string | null
  entered_by_name?: string | null
  entered_at?: string | null
  can_review?: boolean
  can_correct?: boolean
  unpublished_text?: string | null
}

export interface MarketProgress {
  price_date: string
  unpublished_count: number
  unpublished: Array<{
    config_id: number
    province_name: string
    category_name: string
    breed_name: string
    price_type: string
    price_type_text: string
    last_price?: number | null
    last_unit?: string | null
    last_date?: string | null
  }>
  provinces: Array<{ province_code: string; province_name: string; published: number; total: number }>
}

export interface MarketEntryRow {
  config_id: number
  category_name: string
  breed_name: string
  price_type: string
  price_type_text: string
  last_price?: number | null
  last_unit?: string | null
  last_unit_text?: string | null
  last_date?: string | null
  default_unit: string
  default_unit_text: string
  today_status: string
  today_price?: number | null
  is_readonly: boolean
  return_note?: string | null
}

export interface MarketEntryForm {
  price_date: string
  province_code: string
  province_name: string
  rows: MarketEntryRow[]
}

export interface MarketPriceDetail extends MarketPriceRow {
  price_date: string
  status: string
  status_text: string
  near7_min?: number | null
  near7_max?: number | null
  data_source?: string
  remark?: string
  reviewer_name?: string
  reviewed_at?: string
  review_result?: string
  review_result_text?: string
  review_note?: string
  is_corrected?: boolean
  corrected_at?: string
  corrections?: MarketCorrection[]
  pending_correction?: MarketCorrection | null
  version?: number
}

export interface MarketCorrection {
  correction_id: number
  price_date: string
  old_price: number
  new_price: number
  change_rate_after?: number
  reason: string
  status: string
  status_text?: string
  corrected_by_name?: string
  corrected_at?: string
  reviewer_name?: string
  reviewed_at?: string
  review_note?: string
}

export interface MarketHistoryItem {
  price_date: string
  price_id: number
  price: number
  price_unit: string
  price_unit_text?: string
  change_rate?: number | null
  status: string
  status_text?: string
  entered_by_name?: string
  entered_at?: string
  is_corrected?: boolean
  corrections?: MarketCorrection[]
}

export interface MarketConfig {
  config_id: number
  province_code: string
  province_name: string
  category_id: number
  category_name: string
  breed_id: number
  breed_name: string
  price_type: string
  price_type_text: string
  default_unit: string
  default_unit_text: string
  sort_no: number
  is_enabled: boolean
}

export interface BatchPublishResult {
  published_count: number
  review_count: number
  items: Array<{ config_id: number; price_id?: number; status: string; change_rate?: number | null }>
}

export function getMarketPrices(query: {
  date?: string
  status?: string
  province_code?: string
  category_id?: number | null
  page?: number
  size?: number
}) {
  return http().get<PageVO<MarketPriceRow>>('/market-prices', { ...query })
}

export function getMarketProgress(date?: string) {
  return http().get<MarketProgress>('/market-prices/progress', { date })
}

export function getMarketEntryForm(date: string, provinceCode: string) {
  return http().get<MarketEntryForm>('/market-prices/entry-form', { date, province_code: provinceCode })
}

export function batchPublishMarketPrices(payload: {
  price_date: string
  province_code: string
  data_source: string
  remark?: string
  items: Array<{ config_id: number; price: number | string; price_unit: string }>
}) {
  return http().post<BatchPublishResult>('/market-prices/batch-publish', payload)
}

export function getMarketPriceDetail(id: number) {
  return http().get<MarketPriceDetail>(`/market-prices/${id}`)
}

export function reviewMarketPrice(id: number, payload: {
  result: 'PASS' | 'RETURN'
  review_note: string
  correction_id?: number | null
  version?: number
}) {
  return http().post<MarketPriceDetail>(`/market-prices/${id}/review`, payload)
}

export function correctMarketPrice(id: number, payload: { new_price: number | string; reason: string; version: number }) {
  return http().post<{
    correction_id: number
    price_date: string
    status: string
    status_text: string
    recalculated?: Array<{ price_date: string; old_rate: number; new_rate: number }>
  }>(`/market-prices/${id}/corrections`, payload)
}

export function getMarketHistory(params: {
  province_code: string
  category_id: number
  breed_id: number
  price_type: string
  days?: number
}) {
  return http().get<MarketHistoryItem[]>('/market-prices/history', { ...params })
}

export function getMarketConfigs(provinceCode?: string) {
  return http().get<MarketConfig[]>('/market-price-configs', { province_code: provinceCode })
}

export function getMarketConfigPermission() {
  return http().get<boolean>('/market-price-configs/permission')
}

export function saveMarketConfigs(payload: {
  province_code: string
  configs: Array<{
    config_id?: number
    category_id: number
    breed_id: number
    price_type: string
    default_unit: string
    sort_no: number
    is_enabled: boolean
  }>
  reason: string
}) {
  return http().put<MarketConfig[]>('/market-price-configs', payload)
}

export function revokeMarketPrice(id: number, reason: string, version?: number) {
  return http().post<MarketPriceDetail>(`/market-prices/${id}/revoke`, { reason, version })
}

/* ---------------- 养殖课堂 ---------------- */

export interface CourseNavTopic {
  id: number
  name: string
  on_shelf_count: number
}

export interface CourseNavCategory {
  id: number
  name: string
  count: number
}

export interface CourseSummary {
  on_shelf_count: number
  topic_count: number
  category_count: number
  plays30d?: number
  viewers30d?: number
  avg_finish_rate?: number
  transcoding_count?: number
  failed_count?: number
  nav?: { total: number; topics: CourseNavTopic[]; categories: CourseNavCategory[] }
}

export interface CourseTranscodePart {
  status: string
  progress?: number
}

export interface CourseRow {
  id: number
  title: string
  cover_url?: string | null
  duration_sec?: number | null
  category_id?: number
  category_name?: string
  topic_id?: number | null
  topic_name?: string | null
  section_no?: number | null
  upload_status?: string
  transcode_status: string
  transcode_status_text?: string
  p720?: CourseTranscodePart | null
  p480?: CourseTranscodePart | null
  status: string
  status_text: string
  play_count?: number
  finish_rate?: number
  updated_at?: string
  updated_by_name?: string
  can_on_shelf?: boolean
  intro?: string
  knowledge_id?: number
  knowledge_title?: string
  applicable_category_ids?: number[]
  video?: {
    file_id: number
    original_name?: string
    file_size?: number
    duration_sec?: number
    width?: number
    height?: number
    uploaded_at?: string
    upload_status?: string
    transcode_status?: string
  }
  transcode_error?: string | null
  cover_file_id?: number | null
  cover_source?: string
  cover_candidates?: Array<{ file_id: number; url: string }>
  on_shelf_at?: string
  on_shelf_by_name?: string
  off_shelf_at?: string
  off_reason?: string
  version?: number
}

export interface CourseTopic {
  id: number
  name: string
  category_id: number
  category_name?: string
  intro?: string
  cover_file_id?: number | null
  cover_url?: string
  status: string
  status_text?: string
  off_reason?: string
  sort_no: number
  course_count?: number
  total_duration_sec?: number
  duration_text?: string
  version?: number
  courses?: Array<{ course_id: number; section_no: number; title?: string; duration_sec?: number; status?: string }>
}

export interface ContentCategory {
  id: number
  name: string
  sort_no: number
  status: string
  status_text?: string
  course_count?: number
  knowledge_count?: number
  version?: number
}

export interface CoursePayload {
  submit_action?: 'DRAFT' | 'ON_SHELF'
  title?: string
  category_id?: number | null
  topic_id?: number | null
  section_no?: number | null
  applicable_category_ids?: number[]
  knowledge_id?: number | null
  intro?: string
  video_file_id?: number | null
  cover_source?: string
  cover_file_id?: number | null
  version?: number
}

export interface CourseStats {
  play_count: number
  viewer_count: number
  finish_count: number
  finish_rate: number
  avg_watch_sec: number
  daily: Array<{ date: string; plays: number }>
  progress_buckets: Array<{ bucket: string; users: number }>
  top_exit_range?: { start_sec: number; end_sec: number } | null
  client_share?: { app: number; web: number }
  quality_share?: { p720: number; p480: number }
  mobile_play_count?: number
  ask_ai_count?: number
  knowledge_click_count?: number
  topic?: { learners: number; all_finished: number; avg_finished: number; total_sections: number } | null
  stat_at?: string
}

export function getCourseSummary() {
  return http().get<CourseSummary>('/courses/summary')
}

export function getCourses(query: {
  topic_id?: number | null
  category_id?: number | null
  status?: string
  transcode_status?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return http().get<PageVO<CourseRow>>('/courses', { ...query })
}

export function getCourseDetail(id: number) {
  return http().get<CourseRow>(`/courses/${id}`)
}

export function createCourse(payload: CoursePayload) {
  return http().post<{ id: number; status: string; transcode_status?: string; version?: number }>('/courses', payload)
}

export function updateCourse(id: number, payload: CoursePayload) {
  return http().put<{ id: number; status: string; version?: number }>(`/courses/${id}`, payload)
}

export function retryCourseTranscode(id: number) {
  return http().post(`/courses/${id}/transcode/retry`)
}

export function onShelfCourse(id: number, version?: number) {
  return http().post(`/courses/${id}/on-shelf`, { version })
}

export function offShelfCourse(id: number, reason: string, version?: number) {
  return http().post(`/courses/${id}/off-shelf`, { off_reason: reason, version })
}

export function deleteCourse(id: number, version?: number) {
  return http().del(`/courses/${id}?version=${version ?? ''}`)
}

export function getCourseStats(id: number) {
  return http().get<CourseStats>(`/courses/${id}/stats`)
}

export function exportCourseStats(id: number) {
  return http().post<ExportResult>(`/courses/${id}/stats/export`)
}

export function getCourseTopics(status = 'ALL') {
  return http().get<CourseTopic[]>('/course-topics', { status })
}

export function createCourseTopic(payload: Partial<CourseTopic> & { courses?: Array<{ course_id: number; section_no: number }> }) {
  return http().post<CourseTopic>('/course-topics', payload)
}

export function updateCourseTopic(id: number, payload: Partial<CourseTopic> & { courses?: Array<{ course_id: number; section_no: number }> }) {
  return http().put<CourseTopic>(`/course-topics/${id}`, payload)
}

export function setCourseTopicStatus(id: number, status: string, reason: string, version?: number) {
  return http().post<CourseTopic>(`/course-topics/${id}/status`, { status, reason, version })
}

export function getContentCategories(status = 'ALL') {
  return http().get<ContentCategory[]>('/content-categories', { status })
}

export function createContentCategory(payload: { name: string; sort_no?: number; status?: string }) {
  return http().post<ContentCategory>('/content-categories', payload)
}

export function updateContentCategory(id: number, payload: { name: string; status: string; version?: number }) {
  return http().put<ContentCategory>(`/content-categories/${id}`, payload)
}

export function orderContentCategories(ids: number[]) {
  return http().put('/content-categories/order', { ids })
}

/* ---------------- 知识库 ---------------- */

export interface KnowledgeRow {
  id: number
  title: string
  has_video: boolean
  video_title?: string | null
  video_duration_sec?: number | null
  category_id?: number
  category_name?: string
  source_type: string
  source_type_text?: string
  version_label?: string
  has_draft?: boolean
  status: string
  status_text: string
  is_major_epidemic?: boolean
  display_type?: string
  cite_count30d?: number
  useful_rate_30d?: number
  updated_at?: string
  updated_by_name?: string
  is_readonly?: boolean
  version?: number
}

export interface KnowledgeContent {
  title: string
  category_id: number
  category_name?: string
  display_type?: string
  is_major_epidemic?: boolean
  applicable_category_ids?: number[]
  keywords?: string[]
  content: string
}

export interface KnowledgeDraft extends KnowledgeContent {
  draft_id?: number
  change_note?: string
  source_task_id?: number | null
  saved_by_name?: string
  saved_at?: string
}

export interface KnowledgeEdit {
  id: number
  source_type: string
  status: string
  is_major_epidemic?: boolean
  display_type?: string
  version?: number
  online?: { version_no: number; published_at?: string; published_by_name?: string }
  online_content?: KnowledgeContent
  draft?: KnowledgeDraft
  draft_owner?: { name?: string; saved_at?: string }
  cite_count30d?: number
  useful_rate_30d?: number
  stat_at?: string
  paragraphs?: Array<{ para_key: string; cite_count30d?: number; is_frequent?: boolean }>
}

export interface KnowledgeVersionItem {
  version_no: number
  is_online: boolean
  published_at: string
  published_by_name?: string
  change_note?: string
  rollback_from_no?: number | null
}

export interface KnowledgeVersion {
  version_no: number
  title: string
  category_name?: string
  applicable_category_names?: string[]
  keywords?: string[]
  content: string
  compare?: Array<{ field: string; field_text: string; old_value: string; new_value: string; changed: boolean }>
  paragraph_diff?: Array<{ para_key: string; diff: string }>
}

export interface KnowledgeTask {
  id: number
  title: string
  source_type: string
  source_type_text?: string
  source_message_id?: number | null
  source_question?: string | null
  mode: string
  mode_text?: string
  target_knowledge_id?: number | null
  target_title?: string | null
  category_id?: number
  category_name?: string
  similar_count?: number
  assignee_id?: number
  assignee_name?: string
  due_date?: string
  is_overdue?: boolean
  points?: string
  status: string
  status_text: string
  result_knowledge_id?: number | null
  result_version_no?: number | null
  completed_at?: string | null
  close_reason?: string | null
  created_by_name?: string
  created_at?: string
  version?: number
}

export interface KnowledgePayload {
  title: string
  category_id: number
  source_type?: string
  is_major_epidemic?: boolean
  display_type?: string
  applicable_category_ids?: number[]
  keywords?: string[]
  content: string
  source_task_id?: number | null
  submit_action?: 'DRAFT' | 'PUBLISH'
  change_note?: string
  version?: number
}

export function getKnowledgePage(query: {
  status?: string
  category_id?: number | null
  source_type?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return http().get<PageVO<KnowledgeRow> & { task_pending_count?: number }>('/knowledge', { ...query })
}

export function getKnowledgeEdit(id: number) {
  return http().get<KnowledgeEdit>(`/knowledge/${id}`)
}

export function createKnowledge(payload: KnowledgePayload) {
  return http().post<{ id: number; status: string; version?: number }>('/knowledge', payload)
}

export function saveKnowledgeDraft(id: number, payload: KnowledgePayload) {
  return http().put<{ id: number; version?: number }>(`/knowledge/${id}/draft`, payload)
}

export function publishKnowledge(id: number, changeNote: string, version?: number) {
  return http().post<{ online_version_no: number }>(`/knowledge/${id}/publish`, { change_note: changeNote, version })
}

export function getKnowledgeVersions(id: number) {
  return http().get<KnowledgeVersionItem[]>(`/knowledge/${id}/versions`)
}

export function getKnowledgeVersion(id: number, versionNo: number) {
  return http().get<KnowledgeVersion>(`/knowledge/${id}/versions/${versionNo}`)
}

export function rollbackKnowledge(id: number, targetVersionNo: number, reason: string, version?: number) {
  return http().post<{ new_version_no: number; affected_answers_30d?: number }>(`/knowledge/${id}/rollback`, {
    target_version_no: targetVersionNo,
    reason,
    version
  })
}

export function offShelfKnowledge(id: number, reason: string, version?: number) {
  return http().post(`/knowledge/${id}/off-shelf`, { off_reason: reason, version })
}

export function onShelfKnowledge(id: number, version?: number) {
  return http().post(`/knowledge/${id}/on-shelf`, { version })
}

export function deleteKnowledge(id: number, version?: number) {
  return http().del(`/knowledge/${id}?version=${version ?? ''}`)
}

export function getKnowledgeTasks(query: { status?: string; page?: number; size?: number }) {
  return http().get<PageVO<KnowledgeTask>>('/knowledge-tasks', { ...query })
}

export function createKnowledgeTask(payload: {
  title: string
  source_type: string
  source_message_id?: number
  mode: string
  target_knowledge_id?: number | null
  category_id: number
  assignee_id: number
  due_date: string
  points: string
  similar_count?: number
}) {
  return http().post<KnowledgeTask>('/knowledge-tasks', payload)
}

export function closeKnowledgeTask(id: number, reason: string, version?: number) {
  return http().post(`/knowledge-tasks/${id}/close`, { close_reason: reason, version })
}

/* ---------------- AI 问答 ---------------- */

export interface AiQaSummary {
  date_from: string
  date_to: string
  qa_count: number
  asker_count: number
  useful_rate?: number
  rated_count?: number
  useful_count?: number
  pending_useless_count?: number
  no_basis_count?: number
  no_basis_ratio?: number
}

export interface QaRow {
  message_id: number
  session_id: number
  round_no: number
  question_text: string
  image_count?: number
  farm_id?: number
  farm_name?: string
  asker_id?: number
  asker_name?: string
  batch_no?: string | null
  answer_type?: string
  answer_type_text?: string
  rating?: string | null
  rating_text?: string | null
  can_rate?: boolean
  handle_status?: string | null
  handle_status_text?: string | null
  is_spot_check?: boolean
  asked_at: string
}

export interface AiCorrection {
  correction_id: number
  message_id: number
  content: string
  status: string
  status_text?: string
  published_at?: string
  first_published_at?: string
  published_by_name?: string
  withdrawn_at?: string
  withdraw_reason?: string
  task_id?: number | null
  version?: number
}

export interface QaRound {
  message_id: number
  round_no: number
  asked_at: string
  question_text: string
  images?: Array<{ file_id?: number; url?: string }>
  status?: string
  answer_type?: string
  answer_type_text?: string
  answer_text?: string
  rating?: string | null
  rating_text?: string | null
  rated_at?: string
  citations?: Array<{
    seq_no: number
    title: string
    source_type: string
    icon_type?: string
    knowledge_id?: number
    version_no?: number
    para_key?: string
  }>
}

export interface QaDetail {
  message_id: number
  handle_status?: string
  handle_status_text?: string
  focus_round_no?: number
  answer_type?: string
  answer_type_text?: string
  rating?: string | null
  rating_text?: string | null
  session?: {
    session_id: number
    farm_name?: string
    asker_name?: string
    asker_role?: string
    started_at?: string
    round_count?: number
  }
  context?: {
    batch_no?: string
    breed_text?: string
    breed_name?: string
    pen_name?: string
    day_age?: number
    current_count?: number
    count_unit_text?: string
    withdrawal_drugs?: Array<{ name?: string; last_used_at?: string; withdrawal_days?: number } | string>
    sellable_date?: string
  }
  rounds?: QaRound[]
  spot_check?: { title?: string; content?: string; checked_at?: string } | null
  correction?: AiCorrection | null
  suggest_task_target?: { knowledge_id?: number; title?: string } | null
  task?: KnowledgeTask | null
  can_correct?: boolean
  can_update_correction?: boolean
  can_no_action?: boolean
  can_create_task?: boolean
}

export function getAiQaSummary(query: { date_from?: string; date_to?: string }) {
  return http().get<AiQaSummary>('/ai-qa/summary', { ...query })
}

export function getAiQaList(query: {
  segment?: string
  keyword?: string
  date_from?: string
  date_to?: string
  is_spot_check?: boolean
  page?: number
  size?: number
}) {
  return http().get<PageVO<QaRow>>('/ai-qa', { ...query })
}

export function getAiQaDetail(id: number) {
  return http().get<QaDetail>(`/ai-qa/${id}`)
}

export function publishAiCorrection(id: number, payload: {
  content: string
  create_task?: boolean
  task_target_knowledge_id?: number | null
  task_title?: string
  assignee_id?: number
}) {
  return http().post<AiCorrection>(`/ai-qa/${id}/correction`, payload)
}

export function updateAiCorrection(id: number, content: string, version?: number) {
  return http().put<AiCorrection>(`/ai-qa/${id}/correction`, { content, version })
}

export function withdrawAiCorrection(id: number, reason: string, version?: number) {
  return http().post<AiCorrection>(`/ai-qa/${id}/correction/withdraw`, { withdraw_reason: reason, version })
}

export function markAiQaNoAction(id: number, version?: number) {
  return http().post(`/ai-qa/${id}/no-action`, { version })
}

export function getAiQaTaskSuggestion(id: number) {
  return http().get<{
    suggested_title?: string
    suggested_category_id?: number
    similar_count_30d?: number
    similar_keywords?: string[]
  }>(`/ai-qa/${id}/task-suggestion`)
}

export function exportAiQa(payload: {
  segment?: string
  keyword?: string
  date_from?: string
  date_to?: string
  is_spot_check?: boolean
}) {
  return http().post<ExportResult>('/ai-qa/export', payload)
}

export function createAiQaTask(id: number, payload: {
  title: string
  mode: string
  target_knowledge_id?: number | null
  category_id: number
  assignee_id: number
  due_date: string
  points: string
  similar_count?: number
}) {
  return http().post(`/ai-qa/${id}/task`, payload)
}

/* ---------------- 管理端文件 ---------------- */

export interface AdminFile {
  file_id: number
  scene?: string
  original_name?: string
  file_size?: number
  mime_type?: string
  width?: number
  height?: number
  duration_sec?: number
  upload_status?: string
  transcode_status?: string
  status?: string
  url?: string
  transcode_error?: string | null
  variants?: Array<{ variant_code: string; status: string; progress?: number; error_message?: string | null }>
  cover_candidates?: Array<{ file_id: number; url: string }>
}

export function uploadAdminFile(file: File, scene: string, onProgress?: (percent: number) => void) {
  const form = new FormData()
  form.append('file', file)
  form.append('scene', scene)
  return http().upload<AdminFile>('/files', form, onProgress)
}

export function getAdminFile(id: number) {
  return http().get<AdminFile>(`/files/${id}`)
}

export function initMultipartUpload(payload: {
  scene: string
  original_name: string
  file_size: number
  mime_type?: string
  file_id?: number | null
}) {
  return http().post<{
    file_id: number
    part_size: number
    part_count: number
    original_name: string
    part_urls: Array<{ part_no: number; url: string }>
    uploaded_parts: number[]
    url_expire_at?: string
  }>('/files/multipart', payload)
}

export function uploadMultipartPartProxy(fileId: number, partNo: number, chunk: Blob) {
  const form = new FormData()
  form.append('file', chunk, `part-${partNo}`)
  return http().upload<{ part_no: number; etag: string }>(`/files/${fileId}/multipart/part?part_no=${partNo}`, form)
}

export function completeMultipartUpload(fileId: number, parts: Array<{ part_no: number; etag: string }>) {
  return http().post<AdminFile>(`/files/${fileId}/multipart/complete`, { parts })
}

export function retryFileTranscode(id: number) {
  return http().post<AdminFile>(`/files/${id}/transcode`)
}
