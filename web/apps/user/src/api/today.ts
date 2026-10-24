/**
 * 模块03 今日首页、养殖日历与消息中心接口（API-T01–T18、F21/F22）。
 * 服务端 JSON 使用 snake_case（与 OpenAPI 生成类型不一致），这里的类型按真实响应声明。
 */
import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/client'

/* ---------------- 通用 ---------------- */

export interface FarmBrief {
  farm_id?: number
  name?: string
  species_text?: string
  status?: string
  status_text?: string
  role?: string
  role_text?: string
  main_category_name?: string
  current_stock?: number
  count_unit_text?: string
  can_switch?: boolean
  is_current?: boolean
}

export interface LinkVO {
  route?: string
  webRoute?: string
  farm_id?: number
  farm_name?: string
  need_switch?: boolean
  params?: Record<string, string>
  page_title?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  has_more: boolean
  tab_counts?: Record<string, number>
}

function config(signal?: AbortSignal): AxiosRequestConfig | undefined {
  return signal ? { signal } : undefined
}

/* ---------------- API-T01 首页 ---------------- */

export interface OwnerMetricsVO {
  stock_count?: number
  batch_count?: number
  today_todo_count?: number
  overdue_count?: number
  warning_count?: number
  receivable_overdue_count?: number
  receivable_max_overdue_days?: number
  count_unit?: string
  count_unit_text?: string
  pen_occupancy_rate?: number
  warning_names?: string[]
  receivable_amount?: number
}

export interface StaffMetricsVO {
  pen_count?: number
  today_todo_count?: number
  overdue_count?: number
  today_done_count?: number
  pen_names?: string[]
  today_done_names?: string[]
  overdue_first_text?: string
}

export interface MyPenBatch {
  batch_id?: number
  batch_no?: string
  breed_text?: string
  breed_title?: string
  day_age?: number
  head_count?: number
  qty_in_pen?: number
  pen_name?: string
  stage_text?: string
}

export interface MyPenVO {
  pen_id?: number
  pen_name?: string
  pen_type_text?: string
  capacity?: number
  todo_count?: number
  today_task_done?: number
  today_task_total?: number
  batches?: MyPenBatch[]
  todo_names?: string[]
  in_withdrawal?: boolean
  withdrawal_days_left?: number
  link?: LinkVO
}

export interface HomeVO {
  server_time?: string
  greeting?: string
  user_name?: string
  avatar_text?: string
  role?: string
  role_text?: string
  farm_status?: string
  farm_status_text?: string
  farm?: FarmBrief
  has_pen?: boolean
  unread_count?: number
  owner_metrics?: OwnerMetricsVO
  staff_metrics?: StaffMetricsVO
  ai_hint?: string
  my_pens?: MyPenVO[]
}

export function fetchHome(signal?: AbortSignal): Promise<HomeVO> {
  return http().get<HomeVO>('/home', undefined, config(signal))
}

/* ---------------- API-T02–T09 待办 ---------------- */

export interface TodoItemVO {
  todo_id?: number
  farm_id?: number
  farm_name?: string
  todo_type?: string
  todo_type_text?: string
  category?: string
  category_text?: string
  display_status?: string
  display_status_text?: string
  title?: string
  sub_text?: string
  tail_text?: string
  due_date?: string
  due_time?: string
  time_type?: string
  time_label?: string
  overdue_days?: number
  pen_id?: number
  pen_name?: string
  batch_id?: number
  batch_no?: string
  assignee_text?: string
  amount?: number
  completed_text?: string
  overdue_done?: boolean
  ignore_reason?: string
  ignore_text?: string
  action_text?: string
  list_link?: LinkVO
  register_link?: LinkVO
  version?: number
}

export interface RelatedBatchVO {
  batch_id?: number
  batch_no?: string
  pen_name?: string
  desc?: string
  tag_text?: string
  link?: LinkVO
}

export interface TodoDetailVO extends TodoItemVO {
  subtitle?: string
  source_module_name?: string
  due_text?: string
  due_hint?: string
  receivers_text?: string
  receivers_hint?: string
  status_note?: string
  detail_label?: string
  detail_value?: string
  remind_count?: number
  remind_text?: string
  complete_condition?: string
  notice_text?: string
  mark_done_tip?: string
  complete_way?: string
  complete_way_text?: string
  completed_at?: string
  completed_by?: { user_id?: number; name?: string }
  ignored_at?: string
  ignored_by?: { user_id?: number; name?: string }
  related_batch?: RelatedBatchVO
  can_register?: boolean
  can_complete?: boolean
  can_ignore?: boolean
}

export interface PenCount {
  pen_id?: number
  pen_name?: string
  count?: number
}

export interface TodoPageVO extends PageResult<TodoItemVO> {
  pen_counts?: PenCount[]
}

export interface CalendarDayVO {
  date?: string
  is_today?: boolean
  total?: number
  overdue_count?: number
  pending_count?: number
  closed_count?: number
  overdue_titles?: string[]
}

export interface WeeklyDayVO extends CalendarDayVO {
  items?: TodoItemVO[]
}

export interface TodayTodoParams {
  page?: number
  size?: number
  filter?: string
  pen_id?: number
}

export function fetchTodayTodos(params: TodayTodoParams, signal?: AbortSignal): Promise<TodoPageVO> {
  return http().get<TodoPageVO>('/todos/today', { ...params }, config(signal))
}

export interface CalendarParams {
  start_date: string
  end_date: string
  category?: string
}

export function fetchCalendar(params: CalendarParams, signal?: AbortSignal): Promise<{ days?: CalendarDayVO[] }> {
  return http().get<{ days?: CalendarDayVO[] }>('/todos/calendar', { ...params }, config(signal))
}

export interface WeeklyParams {
  week_start: string
  per_day?: number
  category?: string
}

export function fetchWeekly(params: WeeklyParams, signal?: AbortSignal): Promise<{ days?: WeeklyDayVO[] }> {
  return http().get<{ days?: WeeklyDayVO[] }>('/todos/weekly', { ...params }, config(signal))
}

export interface DayTodoParams {
  date: string
  status?: string
  page?: number
  size?: number
}

export function fetchDayTodos(params: DayTodoParams, signal?: AbortSignal): Promise<PageResult<TodoItemVO>> {
  return http().get<PageResult<TodoItemVO>>('/todos', { ...params }, config(signal))
}

export interface OverdueParams {
  page?: number
  size?: number
}

export function fetchOverdueTodos(params: OverdueParams, signal?: AbortSignal): Promise<PageResult<TodoItemVO>> {
  return http().get<PageResult<TodoItemVO>>('/todos/overdue', { ...params }, config(signal))
}

export function fetchTodoDetail(id: number): Promise<TodoDetailVO> {
  return http().get<TodoDetailVO>(`/todos/${id}`)
}

export function completeTodo(id: number, version: number): Promise<TodoDetailVO> {
  return http().post<TodoDetailVO>(`/todos/${id}/complete`, { version })
}

export function ignoreTodo(id: number, version: number, reason: string): Promise<TodoDetailVO> {
  return http().post<TodoDetailVO>(`/todos/${id}/ignore`, { version, reason })
}

/* ---------------- 栏舍（T01 栏舍占用） ---------------- */

export interface PenOptionVO {
  pen_id?: number
  pen_code?: string
  pen_name?: string
  pen_type?: string
  stock_count?: number
  capacity?: number
  disabled?: boolean
  hint?: string
  unit_text?: string
}

export function fetchPens(signal?: AbortSignal): Promise<PenOptionVO[]> {
  return http().get<PenOptionVO[]>('/pens', undefined, config(signal))
}

/* ---------------- API-T12–T18 消息中心 ---------------- */

export interface MessageActionVO {
  action_type?: string
  action_id?: number
  status?: string
  status_text?: string
  role_text?: string
  expire_at?: string
  can_accept?: boolean
}

export interface MessageBriefVO {
  message_id?: number
  category?: string
  category_text?: string
  msg_type?: string
  level?: string
  level_text?: string
  title?: string
  summary?: string
  tag_text?: string
  tag_code?: string
  farm?: FarmBrief
  biz_type?: string
  biz_id?: number
  todo_id?: number
  route?: string
  web_route?: string
  read?: boolean
  revoked?: boolean
  created_at?: string
  sort_time?: string
  date_group?: string
  icon_state?: string
  merge_count?: number
  action?: MessageActionVO
}

export interface MessageDetailVO extends MessageBriefVO {
  target_status?: string
  target_status_text?: string
  link?: LinkVO
  deleted_record_type?: string
  page_title?: string
  deleted_title?: string
  deleted_desc?: string
  deny_title?: string
  deny_desc?: string
  total_unread?: number
  payload?: Record<string, unknown>
}

export interface MessagePageVO extends PageResult<MessageBriefVO> {
  filtered_unread?: number
}

export interface MessageQueryParams {
  page?: number
  size?: number
  category?: string
  farm_id?: number
  unread_only?: boolean
}

export function fetchMessages(params: MessageQueryParams, signal?: AbortSignal): Promise<MessagePageVO> {
  return http().get<MessagePageVO>('/messages', { ...params }, config(signal))
}

export function fetchMessageDetail(id: number): Promise<MessageDetailVO> {
  return http().get<MessageDetailVO>(`/messages/${id}`)
}

export interface OpenResultVO {
  target_status?: string
  target_status_text?: string
  link?: LinkVO
  deleted_record_type?: string
  page_title?: string
  deleted_title?: string
  deleted_desc?: string
  deny_title?: string
  deny_desc?: string
  total_unread?: number
}

export function openMessage(id: number): Promise<OpenResultVO> {
  return http().post<OpenResultVO>(`/messages/${id}/open`)
}

export interface ReadAllPayload {
  category?: string
  farm_id?: number
}

export interface ReadAllResult {
  updated_count?: number
  total_unread?: number
}

export function readAllMessages(payload: ReadAllPayload): Promise<ReadAllResult> {
  return http().post<ReadAllResult>('/messages/read-all', payload)
}

export interface MessageDeleteResult {
  deleted_at?: string
  undo_deadline?: string
}

export function deleteMessage(id: number): Promise<MessageDeleteResult> {
  return http().del<MessageDeleteResult>(`/messages/${id}`)
}

export function restoreMessage(id: number): Promise<MessageBriefVO> {
  return http().post<MessageBriefVO>(`/messages/${id}/restore`)
}

/* ---------------- API-T10–T11 提醒设置 ---------------- */

export interface ReminderSettingVO {
  todo_remind_enabled?: boolean
  digest_enabled?: boolean
  digest_time?: string
  quiet_enabled?: boolean
  quiet_start?: string
  quiet_end?: string
  quiet_end_next_day?: boolean
  summary_text?: string
}

export type ReminderPatch = Partial<
  Pick<
    ReminderSettingVO,
    'todo_remind_enabled' | 'digest_enabled' | 'digest_time' | 'quiet_enabled' | 'quiet_start' | 'quiet_end'
  >
>

export function fetchReminderSettings(): Promise<ReminderSettingVO> {
  return http().get<ReminderSettingVO>('/reminder-settings')
}

export function patchReminderSettings(patch: ReminderPatch): Promise<ReminderSettingVO> {
  return http().request<ReminderSettingVO>({
    url: '/reminder-settings',
    method: 'PATCH',
    data: patch
  })
}

/* ---------------- API-F21/F22 成员邀请 ---------------- */

export interface InviteAcceptVO {
  farm_id?: number
  farm_name?: string
  role?: string
  role_text?: string
}

export function acceptFarmInvite(inviteId: number): Promise<InviteAcceptVO> {
  return http().post<InviteAcceptVO>(`/farm-invites/${inviteId}/accept`)
}

export function rejectFarmInvite(inviteId: number): Promise<void> {
  return http().post<void>(`/farm-invites/${inviteId}/reject`)
}
