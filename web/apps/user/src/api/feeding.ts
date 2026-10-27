import type { PageVO } from '@zmt/shared'
import { http } from '@/api/client'

/**
 * 模块06 饲喂与生长称重接口。
 * FeedTaskVO / FeedPlanVO / FeedExecVO / WeighVO 未包含在当前 OpenAPI 生成文件里，
 * 这里按服务端 VO（server/.../biz/feeding/vo/*.java）逐字段声明，保持 snake_case。
 */
export interface FeedUser {
  user_id?: number
  name?: string
  role?: string
  role_text?: string
}

export interface FeedOption {
  feed_id?: number
  feed_name?: string
  unit?: string
  stock_qty?: number
  remaining_days?: number
  low_stock?: boolean
  selectable?: boolean
  disabled_reason?: string
}

export interface FeedPlanShortage {
  feed_id?: number
  feed_name?: string
  stock_qty?: number
  today_need_qty?: number
  short_qty?: number
  restock_before_meal_name?: string
}

export interface FeedTaskSummary {
  date?: string
  weekday_text?: string
  pen_count?: number
  task_total?: number
  done_count?: number
  overdue_count?: number
  plan_total_qty?: number
  actual_total_qty?: number
  plan_shortage_more_count?: number
  first_overdue?: {
    pen_name?: string
    meal_name?: string
    feed_time?: string
    overdue_minutes?: number
  }
  status_counts?: { pending?: number; done?: number; overdue?: number }
  pen_options?: Array<{ pen_id?: number; pen_name?: string; meal_count?: number }>
  plan_shortage?: FeedPlanShortage[]
  can_stock_in?: boolean
  can_manage?: boolean
  empty_reason?: string
}

export interface FeedTaskItem {
  task_id?: number
  task_date?: string
  meal_no?: number
  meal_code?: string
  meal_name?: string
  feed_time?: string
  pen_id?: number
  pen_name?: string
  batch_id?: number
  batch_no?: string
  feed_id?: number
  feed_name?: string
  low_stock?: boolean
  plan_qty?: number
  actual_qty?: number
  assignee_name?: string
  executor_name?: string
  registered_at?: string
  status?: string
  status_text?: string
  abnormal_type?: string
  is_backfill?: boolean
  overdue_minutes?: number
  record_id?: number
  action?: string
}

export interface FeedTaskForm {
  task_id?: number
  task_date?: string
  status?: string
  status_text?: string
  pen_id?: number
  pen_name?: string
  meal_name?: string
  feed_time?: string
  batch_id?: number
  batch_no?: string
  breed_name?: string
  day_age?: number
  batch_stock?: number
  head_count?: number
  daily_qty_per_head?: number
  meals_per_day?: number
  plan_qty?: number
  abnormal_threshold?: number
  default_feed?: FeedOption
  feed_options?: FeedOption[]
  default_executor?: FeedUser
  executor_options?: FeedUser[]
  current_user?: FeedUser
  can_register?: boolean
  block_reason?: string
  is_overdue?: boolean
  backfill_end_at?: string
  record_id?: number
  can_stock_in?: boolean
}

export interface FeedRecordDetail {
  record_id?: number
  task_id?: number
  status?: string
  status_text?: string
  pen_name?: string
  meal_name?: string
  feed_time?: string
  feed_date?: string
  batch_id?: number
  batch_no?: string
  breed_name?: string
  day_age?: number
  batch_stock?: number
  feed?: FeedOption
  plan_qty?: number
  plan_formula?: { head_count?: number; daily_qty_per_head?: number; meals_per_day?: number }
  actual_qty?: number
  deviation_ratio?: number
  abnormal_type?: string
  abnormal_type_text?: string
  abnormal_threshold?: number
  executor?: FeedUser
  recorder?: FeedUser
  is_proxy?: boolean
  is_backfill?: boolean
  registered_at?: string
  remark?: string
  view_mode?: string
  readonly_reason?: string
  change_state?: { can_edit?: boolean; edit_deadline?: string }
  can_void?: boolean
  can_request_change?: boolean
  change_fields?: string[]
  recalc_tags?: string[]
  todo_completed?: boolean
  proxy_executor_name?: string
  version?: number
}

export interface FeedPlanBatchOption {
  batch_id?: number
  batch_no?: string
  pen_names?: string
  breed_name?: string
  day_age?: number
  has_plan?: boolean
}

export interface FeedPlanStage {
  stage_id?: number
  sort_no?: number
  stage_name?: string
  day_age_start?: number
  day_age_end?: number
  state?: string
  editable?: boolean
  exec_start_date?: string
  exec_end_date?: string
  enter_date?: string
  feed_id?: number
  feed_name?: string
  low_stock?: boolean
  daily_qty_per_head?: number
  std_daily_qty?: number
  meals_per_day?: number
  meal_times?: Array<{ meal_no?: number; meal_code?: string; meal_name?: string; feed_time?: string }>
  adjusted_fields?: string[]
  today_plan_qty?: number
  per_meal_qty?: number
  estimate_daily_qty?: number
}

export interface FeedPlan {
  plan_id?: number
  has_plan?: boolean
  batch?: {
    batch_id?: number
    batch_no?: string
    pen_names?: string
    breed_name?: string
    day_age?: number
    current_count?: number
    entry_date?: string
    entry_age_days?: number
  }
  std_card?: {
    std_available?: boolean
    std_name?: string
    applied_at?: string
    applied_by_name?: string
    adjusted_count?: number
    std_updated?: boolean
  }
  abnormal_threshold?: number
  stages?: FeedPlanStage[]
  feed_options?: FeedOption[]
  today_generated_meals?: number
  version?: number
}

export interface FeedStdStage {
  std_stage_id?: number
  stage_name?: string
  day_age_start?: number
  day_age_end?: number
  std_feed_name?: string
  std_feed_type_code?: string
  matched_feed_id?: number
  matched_feed_name?: string
  daily_qty?: number
  meals_per_day?: number
  meal_times?: string[]
}

export interface FeedStdApply {
  std_available?: boolean
  std_version_id?: number
  std_name?: string
  default_abnormal_threshold?: number
  stages?: FeedStdStage[]
}

export interface FeedPlanSaveStage {
  stage_id?: number
  std_stage_id?: number
  stage_name?: string
  day_age_start?: number
  day_age_end?: number
  feed_id?: number
  daily_qty_per_head?: number
  meals_per_day?: number
  meal_times?: string[]
}

export interface FeedExecDaily {
  date?: string
  actual_qty?: number
  plan_qty?: number
  planned_meals?: number
  done_meals?: number
  deviation_ratio?: number
  over_threshold?: boolean
}

export interface FeedExecSummary {
  start_date?: string
  end_date?: string
  range?: string
  batch_count?: number
  planned_meals?: number
  done_meals?: number
  exec_rate?: number
  deviation_ratio?: number
  deviation_direction?: string
  actual_qty?: number
  plan_qty?: number
  abnormal_count?: number
  under_count?: number
  over_count?: number
  abnormal_same_ratio?: number
  plan_line_type?: string
  plan_daily_qty?: number
  daily?: FeedExecDaily[]
  has_data?: boolean
  empty_reason?: string
}

export interface FeedAttentionItem {
  type?: string
  date?: string
  meal_name?: string
  batch_id?: number
  batch_no?: string
  pen_name?: string
  plan_qty?: number
  actual_qty?: number
  executor_name?: string
  abnormal_type?: string
  deviation_ratio?: number
  task_status?: string
  task_status_text?: string
  task_id?: number
  record_id?: number
  can_backfill?: boolean
  is_today?: boolean
}

export interface WeighBatchOption {
  batch_id?: number
  batch_no?: string
  pen_names?: string
  breed_name?: string
  day_age?: number
  status?: string
  last_avg_weight?: number
  growth_status?: string
  growth_status_text?: string
}

export interface WeighStdPoint {
  day_age?: number
  std_weight?: number
  lower?: number
  upper?: number
}

export interface WeighCycle {
  per_month?: number
  interval_days?: number
  customized?: boolean
  suggested_per_month?: number
  next_weigh_date?: string
}

export interface WeighForm {
  batch?: {
    batch_id?: number
    batch_no?: string
    pen_names?: string
    breed_name?: string
    day_age?: number
    current_count?: number
    entry_date?: string
    entry_age_days?: number
    entry_avg_weight?: number
    status?: string
  }
  weigh_date?: string
  day_age?: number
  stock_count?: number
  first_weigh?: boolean
  last_weigh?: { date?: string; avg_weight?: number }
  existing_record_id?: number
  can_register?: boolean
  block_message?: string
  std_available?: boolean
  std_points?: WeighStdPoint[]
  std_weight_at_date?: number
  allowed_deviation?: number
  current_growth_status?: string
  sample_ratio?: number
  suggested_sample?: number
  weigh_cycle?: WeighCycle
  can_edit_cycle?: boolean
}

export interface WeighRecordItem {
  id?: number
  batch_id?: number
  batch_no?: string
  weigh_date?: string
  day_age?: number
  stock_count?: number
  mode?: string
  sample_count?: number
  total_weight?: number
  items?: Array<{ seq_no?: number; weight?: number }>
  avg_weight?: number
  suggested_sample?: number
  sample_low?: boolean
  std_weight?: number
  allowed_deviation?: number
  deviation_ratio?: number
  out_of_range?: boolean
  growth_result?: string
  growth_result_text?: string
  gain_base_type?: string
  gain_base_date?: string
  gain_base_weight?: number
  daily_gain?: number
  recorder?: FeedUser
  created_at?: string
  change_state?: { can_edit?: boolean; edit_deadline?: string }
  can_request_change?: boolean
  change_fields?: string[]
  recalc_tags?: string[]
  growth_change?: string
  todo_completed?: boolean
  next_weigh_date?: string
  version?: number
}

export interface WeighRecordRow {
  id?: number
  weigh_date?: string
  day_age?: number
  avg_weight?: number
  sample_count?: number
  mode?: string
  recorder_name?: string
  std_weight?: number
  deviation_ratio?: number
  out_of_range?: boolean
  daily_gain?: number
  action?: string
}

export interface GrowthCurve {
  batch?: WeighForm['batch']
  growth_status?: string
  growth_status_text?: string
  metrics?: {
    comparison_source?: 'RECORDED' | 'CURRENT_STANDARD'
    avg_weight?: number
    day_age?: number
    weigh_date?: string
    deviation_ratio?: number
    std_weight?: number
    allowed_deviation?: number
    daily_gain?: number
    prev_daily_gain?: number
    prev_gain_base_type?: string
    cumulative_gain?: number
  }
  actual_points?: Array<{ date?: string; day_age?: number; avg_weight?: number; std_weight?: number; is_entry?: boolean }>
  std_available?: boolean
  std_points?: WeighStdPoint[]
  x_min?: number
  x_max?: number
  current_day_age?: number
  compare_series?: Array<{
    batch_id?: number
    batch_no?: string
    status?: string
    points?: Array<{ day_age?: number; avg_weight?: number }>
  }>
  std_updated_notice?: boolean
}

export interface WeighCompareOption {
  batch_id?: number
  batch_no?: string
  pen_names?: string
  status?: string
  latest_avg_weight?: number
  latest_day_age?: number
}

export interface FeedTaskParams {
  page?: number
  size?: number
  status?: string
  penId?: number
}

export interface FeedExecParams {
  batchId?: number
  allBatches?: boolean
  range?: 'LAST_7' | 'LAST_30' | 'BATCH' | 'CUSTOM'
  startDate?: string
  endDate?: string
  date?: string
  page?: number
  size?: number
}

export interface FeedRecordCreatePayload {
  task_id: number
  feed_id: number
  actual_qty: number
  executor_id?: number
  remark?: string
}

export interface FeedRecordUpdatePayload {
  feed_id: number
  actual_qty: number
  executor_id?: number
  remark?: string
  version: number
}

export interface FeedPlanSavePayload {
  applied_std_version_id?: number
  abnormal_threshold: number
  stages: FeedPlanSaveStage[]
  version?: number
}

export interface WeighCreatePayload {
  weigh_date: string
  mode: 'SAMPLE' | 'EACH'
  sample_count?: number
  total_weight?: number
  items?: Array<{ weight: number }>
}

export interface WeighUpdatePayload {
  mode: 'SAMPLE' | 'EACH'
  sample_count?: number
  total_weight?: number
  items?: Array<{ weight: number }>
  version: number
}

export interface WeighCyclePayload {
  per_month?: number
  reset?: boolean
  version?: number
}

function execParams(params: FeedExecParams): Record<string, unknown> {
  const query: Record<string, unknown> = { range: params.range || 'LAST_7' }
  if (params.batchId) query.batch_id = params.batchId
  if (params.allBatches) query.all_batches = true
  if (params.range === 'CUSTOM') {
    query.start_date = params.startDate
    query.end_date = params.endDate
  }
  return query
}

export const feedingApi = {
  todaySummary(): Promise<FeedTaskSummary> {
    return http().get<FeedTaskSummary>('/feed-tasks/today/summary')
  },

  todayTasks(params: FeedTaskParams = {}): Promise<PageVO<FeedTaskItem>> {
    return http().get<PageVO<FeedTaskItem>>('/feed-tasks/today', { ...params })
  },

  taskForm(taskId: number): Promise<FeedTaskForm> {
    return http().get<FeedTaskForm>(`/feed-tasks/${taskId}`)
  },

  createFeedRecord(payload: FeedRecordCreatePayload): Promise<FeedRecordDetail> {
    return http().post<FeedRecordDetail>('/feed-records', payload)
  },

  feedRecord(recordId: number): Promise<FeedRecordDetail> {
    return http().get<FeedRecordDetail>(`/feed-records/${recordId}`)
  },

  updateFeedRecord(recordId: number, payload: FeedRecordUpdatePayload): Promise<FeedRecordDetail> {
    return http().put<FeedRecordDetail>(`/feed-records/${recordId}`, payload)
  },

  voidFeedRecord(recordId: number, version: number): Promise<FeedRecordDetail> {
    return http().post<FeedRecordDetail>(`/feed-records/${recordId}/void`, { version })
  },

  planBatchOptions(): Promise<FeedPlanBatchOption[]> {
    return http().get<FeedPlanBatchOption[]>('/feed-plans/batch-options')
  },

  feedPlan(batchId: number): Promise<FeedPlan> {
    return http().get<FeedPlan>(`/batches/${batchId}/feed-plan`)
  },

  feedPlanStandard(batchId: number): Promise<FeedStdApply> {
    return http().get<FeedStdApply>(`/batches/${batchId}/feed-plan/standard`)
  },

  saveFeedPlan(batchId: number, payload: FeedPlanSavePayload): Promise<FeedPlan> {
    return http().put<FeedPlan>(`/batches/${batchId}/feed-plan`, payload)
  },

  execSummary(params: FeedExecParams): Promise<FeedExecSummary> {
    return http().get<FeedExecSummary>('/feed-executions/summary', execParams(params))
  },

  execAttention(params: FeedExecParams): Promise<PageVO<FeedAttentionItem>> {
    return http().get<PageVO<FeedAttentionItem>>('/feed-executions/attention', {
      ...execParams(params),
      page: params.page || 1,
      size: params.size || 20
    })
  },

  execDaily(params: FeedExecParams): Promise<{ date?: string; items?: unknown[] }> {
    return http().get<{ date?: string; items?: unknown[] }>('/feed-executions/daily', {
      ...execParams(params),
      date: params.date
    })
  },

  weighBatchOptions(includeEnded = false): Promise<WeighBatchOption[]> {
    return http().get<WeighBatchOption[]>('/weigh-records/batch-options', { include_ended: includeEnded })
  },

  weighForm(batchId: number, date?: string): Promise<WeighForm> {
    return http().get<WeighForm>(`/batches/${batchId}/weigh-form`, date ? { date } : undefined)
  },

  createWeigh(batchId: number, payload: WeighCreatePayload): Promise<WeighRecordItem> {
    return http().post<WeighRecordItem>(`/batches/${batchId}/weigh-records`, payload)
  },

  weighRecord(recordId: number): Promise<WeighRecordItem> {
    return http().get<WeighRecordItem>(`/weigh-records/${recordId}`)
  },

  updateWeigh(recordId: number, payload: WeighUpdatePayload): Promise<WeighRecordItem> {
    return http().put<WeighRecordItem>(`/weigh-records/${recordId}`, payload)
  },

  updateWeighCycle(batchId: number, payload: WeighCyclePayload): Promise<WeighCycle> {
    return http().put<WeighCycle>(`/batches/${batchId}/weigh-cycle`, payload)
  },

  growthCurve(batchId: number, compareIds: number[] = []): Promise<GrowthCurve> {
    return http().get<GrowthCurve>(`/batches/${batchId}/growth-curve`, compareIds.length
      ? { compare_batch_ids: compareIds.join(',') }
      : undefined)
  },

  weighRecords(batchId: number, page = 1, size = 20): Promise<PageVO<WeighRecordRow>> {
    return http().get<PageVO<WeighRecordRow>>(`/batches/${batchId}/weigh-records`, { page, size })
  },

  compareOptions(batchId: number): Promise<WeighCompareOption[]> {
    return http().get<WeighCompareOption[]>(`/batches/${batchId}/growth-compare-options`)
  }
}
