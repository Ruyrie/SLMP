import type { FileVO, PageVO, Schema } from '@zmt/shared'
import { http } from '@/api/client'

/** 模块04 栏舍与存栏接口。响应字段保持服务端 snake_case。 */
/**
 * 生产提醒：未处理异常上报（reminder_type=ABNORMAL_REPORT，route=I06）带 report_id，进诊断详情。
 * 生成类型还没有这个字段，先在这里补上；老服务端不返回时为 undefined。
 */
export type ProductionReminder = Schema<'ProductionReminderItemVO'> & { report_id?: number }
/** 生产概览补在养批次数、启用栏舍数、有在养批次的栏舍数；老服务端没有这些字段时为 undefined。 */
export type ProductionOverview = Omit<Schema<'ProductionOverviewVO'>, 'reminders'> & {
  active_batch_count?: number
  pen_count?: number
  occupied_pen_count?: number
  reminders?: ProductionReminder[]
}
export type StockSummary = Schema<'StockSummaryVO'>
export type PenStockItem = Schema<'PenStockItemVO'>
export type CategoryStock = Schema<'CategoryStockVO'>
export type PenOption = Schema<'PenOptionVO'>
export type PenDetail = Schema<'PenDetailVO'>
export type PenBatch = NonNullable<PenDetail['batches']>[number]
export type PenFormOptions = Schema<'PenFormOptionsVO'>
export type PenFormCategory = NonNullable<PenFormOptions['categories']>[number]
export type ExecutorOption = Schema<'ExecutorOptionVO'>
export type BatchListItem = Schema<'BatchListItemVO'>
export type BatchDetail = Schema<'BatchDetailVO'> & { trace_enabled?: boolean }
export type BatchAction = NonNullable<BatchDetail['actions']>
export type BatchPen = NonNullable<BatchListItem['pens']>[number]
export type BatchLookup = Schema<'BatchLookupVO'>
export type BatchEventDetail = Schema<'BatchEventDetailVO'>
export type TimelineItem = Schema<'TimelineItemVO'>
export type PenStock = Schema<'PenStockVO'>
export type MyPens = Schema<'MyPensVO'>
export type MyPen = NonNullable<MyPens['pens']>[number]
export type MyPenBatch = NonNullable<MyPen['batches']>[number]
export type BatchEntryResult = Schema<'BatchEntryResultVO'>
export type TransferResult = Schema<'TransferResultVO'>
export type DeathResult = Schema<'DeathResultVO'>
export type RecordLog = Schema<'RecordLogVO'>
export type BaseCategory = Schema<'BaseCategoryVO'>
export type BaseBreed = Schema<'BaseCategoryBreedVO'>
export type RequestCheck = Schema<'RequestCheckVO'>

export interface PenListParams {
  page?: number
  size?: number
  keyword?: string
  sort?: 'CODE' | 'STOCK' | 'OCCUPANCY' | string
}

export interface PenOptionParams {
  categoryId?: number
  excludePenId?: number
  keyword?: string
  penType?: string
}

export interface BatchListParams {
  page?: number
  size?: number
  status?: 'ACTIVE' | 'ENDED' | 'ALL' | string
  keyword?: string
  sort?: 'ENTRY_DATE' | 'DAY_AGE' | string
  penId?: number
  categoryId?: number
}

export interface TimelineParams {
  page?: number
  size?: number
  type?: 'ALL' | 'FEED' | 'WEIGH' | 'IMMUNE' | 'DRUG' | 'INSPECT' | 'STOCK' | string
}

export interface PenCreatePayload {
  pen_code: string
  pen_name: string
  pen_type: string
  category_id: number
  capacity: number
}

export interface PenUpdatePayload {
  pen_name: string
  pen_type: string
  category_id: number
  capacity: number
  version: number
}

export interface BatchCreatePayload {
  pen_id: number
  breed_id: number
  purpose: 'COMMERCIAL' | 'BREEDING'
  source: 'PURCHASED' | 'SELF_BRED'
  entry_date: string
  entry_age_days: number
  entry_count: number
  entry_avg_weight?: number
  intro_unit_price?: number
  capacity_confirmed?: boolean
}

export interface EntryUpdatePayload extends BatchCreatePayload { version: number }
export interface TransferUpdatePayload {
  from_pen_id: number
  to_pen_id: number
  quantity: number
  event_date: string
  remark?: string
  capacity_confirmed: boolean
  version: number
}
export interface DeathUpdatePayload {
  pen_id: number
  quantity: number
  event_date: string
  reason_code: string
  executor_user_id?: number
  remark?: string
  file_ids: number[]
  version: number
}

export interface TransferCreatePayload {
  batch_id: number
  from_pen_id: number
  to_pen_id: number
  quantity: number
  event_date: string
  remark?: string
  capacity_confirmed?: boolean
  seen_stock_count?: number
}

export interface DeathCreatePayload {
  batch_id: number
  pen_id?: number
  quantity: number
  reason_code: 'SICK' | 'CULL' | 'ACCIDENT' | 'OTHER'
  event_date: string
  executor_user_id?: number
  remark?: string
  file_ids?: number[]
  seen_stock_count?: number
}

export interface UploadedFile {
  fileId: number
  url: string
  name: string
}

function idempotencyHeaders(key?: string): Record<string, string> | undefined {
  return key ? { 'Idempotency-Key': key } : undefined
}

/**
 * 服务端 JSON 与 OpenAPI 生成类型均使用 snake_case，响应直接透传。
 * 保留类型断言小包装，避免各请求函数重复声明泛型。
 */
function camelize<T>(value: unknown): T {
  return value as T
}

function normalizePage<T>(page: PageVO<unknown>): PageVO<T> {
  return page as PageVO<T>
}

function normalizeList<T>(rows: unknown): T[] {
  return Array.isArray(rows) ? (rows as T[]) : []
}

export const herdApi = {
  async productionOverview(): Promise<ProductionOverview> {
    return camelize<ProductionOverview>(await http().get<unknown>('/production/overview'))
  },

  async stockSummary(): Promise<StockSummary> {
    return camelize<StockSummary>(await http().get<unknown>('/stock/summary'))
  },

  async stockPens(params: PenListParams = {}): Promise<PageVO<PenStockItem>> {
    return normalizePage<PenStockItem>(await http().get<PageVO<unknown>>('/stock/pens', { ...params }))
  },

  async stockCategories(params: { keyword?: string } = {}): Promise<CategoryStock[]> {
    return normalizeList<CategoryStock>(await http().get<unknown>('/stock/categories', { ...params }))
  },

  async penOptions(params: PenOptionParams = {}): Promise<PenOption[]> {
    return normalizeList<PenOption>(await http().get<unknown>('/pens', { ...params }))
  },

  async penFormOptions(): Promise<PenFormOptions> {
    return camelize<PenFormOptions>(await http().get<unknown>('/pens/form-options'))
  },

  async penDetail(penId: number): Promise<PenDetail> {
    return camelize<PenDetail>(await http().get<unknown>(`/pens/${penId}`))
  },

  async penExecutors(penId: number): Promise<ExecutorOption[]> {
    return normalizeList<ExecutorOption>(await http().get<unknown>(`/pens/${penId}/executors`))
  },

  async createPen(payload: PenCreatePayload): Promise<PenDetail> {
    return camelize<PenDetail>(await http().post<unknown>('/pens', payload))
  },

  async updatePen(penId: number, payload: PenUpdatePayload): Promise<PenDetail> {
    return camelize<PenDetail>(await http().put<unknown>(`/pens/${penId}`, payload))
  },

  async disablePen(penId: number, version: number): Promise<PenDetail> {
    return camelize<PenDetail>(await http().post<unknown>(`/pens/${penId}/disable`, { version }))
  },

  async enablePen(penId: number, version: number): Promise<PenDetail> {
    return camelize<PenDetail>(await http().post<unknown>(`/pens/${penId}/enable`, { version }))
  },

  async myPens(): Promise<MyPens> {
    return camelize<MyPens>(await http().get<unknown>('/my-pens'))
  },

  async batches(params: BatchListParams = {}): Promise<PageVO<BatchListItem>> {
    return normalizePage<BatchListItem>(await http().get<PageVO<unknown>>('/batches', { ...params }))
  },

  async batchDetail(batchId: number): Promise<BatchDetail> {
    return camelize<BatchDetail>(await http().get<unknown>(`/batches/${batchId}`))
  },

  async batchTimeline(batchId: number, params: TimelineParams = {}): Promise<PageVO<TimelineItem>> {
    return normalizePage<TimelineItem>(await http().get<PageVO<unknown>>(`/batches/${batchId}/timeline`, { ...params }))
  },

  async batchPenStocks(batchId: number): Promise<PenStock[]> {
    return normalizeList<PenStock>(await http().get<unknown>(`/batches/${batchId}/pen-stocks`))
  },

  async lookupBatch(params: { batchNo?: string; qrContent?: string }): Promise<BatchLookup> {
    return camelize<BatchLookup>(await http().get<unknown>('/batches/lookup', { ...params }))
  },

  async batchEvent(eventId: number): Promise<BatchEventDetail> {
    return camelize<BatchEventDetail>(await http().get<unknown>(`/batch-events/${eventId}`))
  },

  async updateEntry(batchId: number, payload: EntryUpdatePayload): Promise<BatchDetail> {
    return camelize<BatchDetail>(await http().put<unknown>(`/batches/${batchId}/entry`, payload))
  },

  async updateTransfer(eventId: number, payload: TransferUpdatePayload): Promise<TransferResult> {
    return camelize<TransferResult>(await http().put<unknown>(`/batch-transfers/${eventId}`, payload))
  },

  async updateDeath(eventId: number, payload: DeathUpdatePayload): Promise<DeathResult> {
    return camelize<DeathResult>(await http().put<unknown>(`/batch-deaths/${eventId}`, payload))
  },

  async createBatch(payload: BatchCreatePayload, requestKey?: string): Promise<BatchEntryResult> {
    return camelize<BatchEntryResult>(await http().post<unknown>('/batches', payload, { headers: idempotencyHeaders(requestKey) }))
  },

  async createTransfer(payload: TransferCreatePayload, requestKey?: string): Promise<TransferResult> {
    return camelize<TransferResult>(await http().post<unknown>('/batch-transfers', payload, { headers: idempotencyHeaders(requestKey) }))
  },

  async createDeath(payload: DeathCreatePayload, requestKey?: string): Promise<DeathResult> {
    return camelize<DeathResult>(await http().post<unknown>('/batch-deaths', payload, { headers: idempotencyHeaders(requestKey) }))
  },

  async checkRequest(requestKey: string): Promise<RequestCheck> {
    return camelize<RequestCheck>(await http().get<unknown>(`/batch-events/by-request/${encodeURIComponent(requestKey)}`))
  },

  async recordLogs(bizType: string, bizId: number): Promise<RecordLog[]> {
    return normalizeList<RecordLog>(await http().get<unknown>('/record-logs', { biz_type: bizType, biz_id: bizId }))
  },

  async baseCategories(): Promise<BaseCategory[]> {
    return normalizeList<BaseCategory>(await http().get<unknown>('/base/categories'))
  },

  uploadFile(file: File, scene: string, onProgress?: (percent: number) => void): Promise<FileVO> {
    const form = new FormData()
    form.append('file', file)
    form.append('scene', scene)
    return http().upload<FileVO>('/files', form, onProgress)
  }
}

/** 浏览器环境生成幂等键；提交超时后可用它查询是否已经保存。 */
export function newRequestKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
