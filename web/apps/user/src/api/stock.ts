/**
 * 模块09 饲料与兽药疫苗库存接口（API-M01–M42，含借用的栏舍、批次、成员与目录接口）。
 * 服务端 JSON 使用 snake_case，这里按真实 VO 声明。
 */
import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/client'

/* ---------------- 通用 ---------------- */

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  has_more: boolean
  tab_counts?: Record<string, number>
}

export interface Qty {
  value?: number
  unit?: string
  unit_text?: string
  pack_unit?: string | null
  pack_size?: number | null
  pack_count?: number | null
  rest_value?: number | null
}

export interface UserBrief {
  user_id?: number
  name?: string
  role?: string
  role_text?: string
}

export interface StockActions {
  can_edit?: boolean
  edit_deadline?: string
  pending_request_id?: number | null
  edit_state?: 'EDITABLE' | 'REQUEST_ONLY' | 'PENDING' | string
  can_void?: boolean | null
  can_request_change?: boolean | null
  can_scrap?: boolean | null
  can_out?: boolean | null
  can_refund?: boolean | null
  can_edit_refund?: boolean | null
}

export type PageStatus = 'loading' | 'ready' | 'empty' | 'filtered_empty' | 'error' | 'forbidden'

function config(signal?: AbortSignal): AxiosRequestConfig | undefined {
  return signal ? { signal } : undefined
}

function idempotency(key: string): AxiosRequestConfig {
  return { headers: { 'Idempotency-Key': key } }
}

export function newRequestKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/* ---------------- M01 饲料库存概览 ---------------- */

export interface StockFlowItem {
  flow_id: number
  flow_type?: string
  flow_type_text?: string
  direction?: 'IN' | 'OUT' | string
  material_type?: 'FEED' | 'DRUG' | string
  material_name?: string
  lot_no?: string
  qty?: Qty
  stock_qty_after?: number | null
  related_desc?: string
  batch_id?: number | null
  batch_no?: string
  pen_name?: string
  operator_name?: string
  occur_at?: string
  biz_date?: string
  status?: string
  status_text?: string
  biz_type?: string
  biz_id?: number | null
  unit_price?: number
  amount?: number
}

export interface FeedStockOverview {
  banner?: {
    kind?: 'DAYS' | 'MIN' | 'MULTI' | string
    feed_id?: number | null
    feed_name?: string
    remaining_days?: number | null
    warn_days?: number | null
    stock?: Qty | null
    min_stock?: number | null
    feed_names?: string[]
    preselect_feed_id?: number | null
  } | null
  feed_count: number
  warn_count: number
  stock_amount?: number | null
  usage_7d_total?: number | null
  usage_7d_items?: Array<{ feed_id: number; feed_name?: string; qty?: number | null }>
  recent_flows?: StockFlowItem[]
  can_other_out?: boolean
  has_feed?: boolean
  farm_readonly?: boolean
}

export interface FeedStockItem {
  feed_id: number
  name: string
  spec?: string
  feed_type_code?: string
  feed_type_text?: string
  category_text?: string
  stock?: Qty
  remaining_days?: number | null
  remaining_basis?: string
  daily_use_qty?: number | null
  usage_qty?: number | null
  usage_days?: number | null
  avg_price?: number | null
  stock_amount?: number | null
  min_stock?: number | null
  warn_days?: number | null
  low_stock?: boolean
  low_stock_reason?: string
  low_stock_reason_text?: string
}

export interface FeedOption {
  feed_id: number
  name: string
  stock?: Qty
  remaining_days?: number | null
  low_stock?: boolean
  selectable?: boolean
  disabled_reason?: string
  avg_price?: number | null
  stock_version?: number
}

export interface FeedDetail {
  feed_id: number
  name: string
  feed_type_code?: string
  feed_type_text?: string
  spec?: string
  unit?: string
  unit_text?: string
  pack_unit?: string
  pack_size?: number | null
  category_id?: number | null
  category_text?: string
  min_stock?: number | null
  warn_days?: number | null
  unit_locked?: boolean
  edit_version?: number
  updated_at?: string
  updated_by_name?: string
}

export interface StockInbound {
  inbound_id: number
  inbound_no?: string
  material_type?: string
  material_name?: string
  category?: string
  category_text?: string
  feed_id?: number | null
  drug_id?: number | null
  drug_lot_id?: number | null
  lot_no?: string
  input_qty?: number
  input_unit?: string
  qty?: Qty
  unit_price?: number
  amount?: number
  supplier_name?: string
  production_date?: string
  shelf_life_date?: string
  expire_date?: string
  inbound_date?: string
  sync_finance?: boolean
  finance_record_id?: number | null
  status?: string
  status_text?: string
  has_outbound?: boolean
  near_expiry_days?: number | null
  actions?: StockActions
  created_by?: UserBrief
  created_at?: string
  version?: number
}

export interface StockLoss {
  loss_id: number
  loss_type?: string
  loss_type_text?: string
  material_name?: string
  drug_lot_id?: number | null
  lot_no?: string
  qty?: Qty
  remaining?: Qty
  reason?: string
  reason_text?: string
  remark?: string
  loss_date?: string
  status?: string
  unit_price?: number
  amount?: number
  actions?: StockActions
  created_by?: UserBrief
  created_at?: string
  version?: number
}

export interface StockOutbound {
  outbound_id: number
  outbound_no?: string
  out_type?: string
  out_type_text?: string
  material_type?: string
  material_name?: string
  category?: string
  feed_id?: number | null
  drug_id?: number | null
  drug_lot_id?: number | null
  lot_no?: string
  expire_date?: string
  input_qty?: number
  input_unit?: string
  qty?: Qty
  max_qty?: number | null
  destination?: string
  destination_text?: string
  target_type?: string
  batch?: { batch_id?: number; batch_no?: string; pen_id?: number; pen_name?: string } | null
  pen?: { pen_id?: number; name?: string; pen_type_text?: string } | null
  receiver?: UserBrief | null
  return_to?: string
  return_reason?: string
  return_reason_text?: string
  remark?: string
  out_date?: string
  status?: string
  status_text?: string
  unit_price?: number
  amount?: number
  allocations?: CostAllocationRow[]
  refund_status?: 'UNREFUNDED' | 'REFUNDED' | null
  refund_amount?: number | null
  refund_date?: string | null
  refund_remark?: string | null
  refund_registered_at?: string | null
  refund_registered_by?: UserBrief | null
  finance_record_id?: number | null
  refund_block_reason?: string | null
  refund_edit_deadline?: string | null
  actions?: StockActions
  created_by?: UserBrief
  created_at?: string
  version?: number
}

export interface CostAllocationRow {
  batch_id: number
  batch_no?: string
  head_count?: number | null
  ratio?: number | null
  amount?: number | null
}

export interface CostAllocation {
  total_amount?: number | null
  unit_price?: number | null
  rows: CostAllocationRow[]
}

export interface StocktakeSheet {
  stocktake_date?: string
  operator?: UserBrief
  items: Array<{
    feed_id: number
    feed_name?: string
    unit_text?: string
    book_qty?: number
    stock_version?: number
    low_stock?: boolean
    avg_price?: number | null
  }>
}

export interface Stocktake {
  stocktake_id: number
  stocktake_no?: string
  stocktake_date?: string
  operator?: UserBrief
  item_count?: number
  gain_count?: number
  loss_count?: number
  gain_amount?: number | null
  loss_amount?: number | null
  items?: Array<{
    item_id: number
    feed_id: number
    feed_name?: string
    unit_text?: string
    book_qty?: number
    actual_qty?: number
    diff_qty?: number
    result?: string
    result_text?: string
    avg_price?: number | null
    diff_amount?: number | null
  }> | null
  actions?: StockActions
  created_at?: string
  version?: number
}

export interface RequestStatus {
  status: 'DONE' | 'PROCESSING' | 'NOT_FOUND' | string
  biz_type?: string
  biz_id?: number | null
}

export function fetchFeedOverview(signal?: AbortSignal): Promise<FeedStockOverview> {
  return http().get<FeedStockOverview>('/feed-stock/overview', undefined, config(signal))
}

export function fetchFeeds(params: { page: number; size: number }, signal?: AbortSignal): Promise<PageResult<FeedStockItem>> {
  return http().get<PageResult<FeedStockItem>>('/feeds', { ...params }, config(signal))
}

export function fetchFeedOptions(purpose: 'INBOUND' | 'OTHER_OUT', keyword?: string, signal?: AbortSignal): Promise<FeedOption[]> {
  return http().get<FeedOption[]>('/feeds/options', { purpose, keyword }, config(signal))
}

export function fetchFeedDetail(feedId: number, signal?: AbortSignal): Promise<FeedDetail> {
  return http().get<FeedDetail>(`/feeds/${feedId}`, undefined, config(signal))
}

export interface FeedSavePayload {
  name: string
  feed_type_code: string
  spec?: string
  pack_unit?: string
  pack_size?: number | null
  category_id?: number | null
  min_stock?: number | null
  warn_days?: number | null
  edit_version?: number
}

export function createFeed(payload: FeedSavePayload): Promise<FeedDetail> {
  return http().post<FeedDetail>('/feeds', payload)
}

export function updateFeed(feedId: number, payload: FeedSavePayload): Promise<FeedDetail> {
  return http().put<FeedDetail>(`/feeds/${feedId}`, payload)
}

/* ---------------- M08 / M30 入库 ---------------- */

export interface FeedInboundPayload {
  feed_id: number
  input_qty: number
  input_unit?: string
  unit_price: number
  production_date?: string
  shelf_life_date?: string
  supplier_name: string
  inbound_date: string
  sync_finance?: boolean
}

export interface DrugInboundPayload {
  drug_id: number
  lot_no: string
  inbound_date: string
  expire_date: string
  input_qty: number
  input_unit?: string
  unit_price: number
  sync_finance?: boolean
}

export function createFeedInbound(payload: FeedInboundPayload, key: string): Promise<StockInbound> {
  return http().post<StockInbound>('/feed-inbounds', payload, idempotency(key))
}

export function createDrugInbound(payload: DrugInboundPayload, key: string): Promise<StockInbound> {
  return http().post<StockInbound>('/drug-inbounds', payload, idempotency(key))
}

export function fetchStockInbound(inboundId: number): Promise<StockInbound> {
  return http().get<StockInbound>(`/stock-inbounds/${inboundId}`)
}

export function fetchRecentPartners(kind: 'SUPPLIER' | 'RETURN_TO', keyword?: string): Promise<string[]> {
  return http().get<string[]>('/stock-partners/recent', { kind, keyword })
}

/* ---------------- M13 / M28 流水 ---------------- */

export type FeedFlowGroup = 'ALL' | 'INBOUND' | 'FEED_USE' | 'OTHER_OUT' | 'LOSS' | 'STOCKTAKE'
export type DrugFlowGroup = 'ALL' | 'INBOUND' | 'USE' | 'RETURN' | 'SCRAP'

export function fetchFeedFlows(
  params: { feed_id?: number; flow_group: FeedFlowGroup; page: number; size: number },
  signal?: AbortSignal
): Promise<PageResult<StockFlowItem>> {
  return http().get<PageResult<StockFlowItem>>('/feed-flows', { ...params }, config(signal))
}

export function fetchDrugFlows(
  params: { drug_lot_id?: number; drug_id?: number; flow_group: DrugFlowGroup; page: number; size: number },
  signal?: AbortSignal
): Promise<PageResult<StockFlowItem>> {
  return http().get<PageResult<StockFlowItem>>('/drug-flows', { ...params }, config(signal))
}

/* ---------------- M14–M18 盘点与报损 ---------------- */

export function fetchStocktakeSheet(signal?: AbortSignal): Promise<StocktakeSheet> {
  return http().get<StocktakeSheet>('/stocktakes/sheet', undefined, config(signal))
}

export interface StocktakeConfirmPayload {
  items: Array<{ feed_id: number; book_qty: number; stock_version: number; actual_qty: number }>
}

export function confirmStocktake(payload: StocktakeConfirmPayload, key: string): Promise<Stocktake> {
  return http().post<Stocktake>('/stocktakes', payload, idempotency(key))
}

export function fetchStocktakes(params: { page: number; size: number }, signal?: AbortSignal): Promise<PageResult<Stocktake>> {
  return http().get<PageResult<Stocktake>>('/stocktakes', { ...params }, config(signal))
}

export interface FeedLossPayload {
  feed_id: number
  input_qty: number
  input_unit?: string
  reason: string
  remark?: string
  stock_version: number
}

export function createFeedLoss(payload: FeedLossPayload, key: string): Promise<StockLoss> {
  return http().post<StockLoss>('/feed-losses', payload, idempotency(key))
}

export function fetchStockLoss(lossId: number): Promise<StockLoss> {
  return http().get<StockLoss>(`/stock-losses/${lossId}`)
}

/* ---------------- M20 饲料其他出库 ---------------- */

export interface FeedOtherOutPayload {
  feed_id: number
  input_qty: number
  input_unit?: string
  destination: 'RETURN_SUPPLIER' | 'TRANSFER_FARM' | 'SALE' | 'OTHER'
  remark?: string
  out_date: string
  stock_version: number
}

export function createFeedOtherOut(payload: FeedOtherOutPayload, key: string): Promise<StockOutbound> {
  return http().post<StockOutbound>('/feed-other-outbounds', payload, idempotency(key))
}

export function fetchStockOutbound(outboundId: number): Promise<StockOutbound> {
  return http().get<StockOutbound>(`/stock-outbounds/${outboundId}`)
}

export interface StockRefundPayload {
  amount: number
  refund_date: string
  remark?: string
  version: number
}

/** 仅登记已到账退款；服务端原子创建关联收入，不重复扣库存。 */
export function registerStockRefund(outboundId: number, payload: StockRefundPayload, key: string): Promise<StockOutbound> {
  return http().post<StockOutbound>(`/stock-outbounds/${outboundId}/refund`, payload, idempotency(key))
}

export function updateStockRefund(outboundId: number, payload: StockRefundPayload, key: string): Promise<StockOutbound> {
  return http().put<StockOutbound>(`/stock-outbounds/${outboundId}/refund`, payload, idempotency(key))
}

/* ---------------- M24–M27 兽药与疫苗 ---------------- */

export interface DrugStockOverview {
  lot_counts?: { all: number; vet_drug: number; vaccine: number; disinfectant: number }
  near_days?: { vet: number; vaccine: number; disinfectant: number }
  low_stock_banner?: {
    drugs: Array<{ drug_id: number; name?: string; usable?: Qty; min_stock?: number | null }>
    preselect_drug_id?: number | null
  } | null
  attention_count: number
  normal_count: number
  has_any_lot: boolean
  farm_readonly: boolean
}

export interface DrugLotItem {
  lot_id: number
  drug_id: number
  drug_name?: string
  category?: 'VET_DRUG' | 'VACCINE' | 'DISINFECTANT' | string
  category_text?: string
  lot_no?: string
  expire_date?: string
  stock?: Qty
  lot_status?: 'NORMAL' | 'NEAR_EXPIRY' | 'EXPIRED' | 'RECALLED' | 'BANNED' | string
  lot_status_text?: string
  days_to_expire?: number | null
  priority_out?: boolean
  status_note?: string
}

export interface DrugLotDetail extends DrugLotItem {
  stock_amount?: number | null
  avg_price?: number | null
  pack_price?: number | null
  first_inbound?: {
    inbound_id: number
    inbound_date?: string
    qty?: Qty
    unit_price?: number | null
  } | null
  inbound_count?: number
  has_outbound?: boolean
  drug_banned?: boolean
  actions?: StockActions
  recalled_at?: string
  recall_operator_text?: string
  recall_reason?: string
  recall_message_id?: number | null
  stock_version?: number
}

export interface DrugLotOption {
  lot_id: number
  lot_no?: string
  expire_date?: string
  days_to_expire?: number | null
  lot_status?: string
  lot_status_text?: string
  stock?: Qty
  priority_out?: boolean
  insufficient?: boolean
  can_go_scrap?: boolean
  avg_price?: number | null
  stock_version?: number
}

export interface DrugLotOptions {
  selectable: DrugLotOption[]
  unselectable: DrugLotOption[]
  default_lot_id?: number | null
}

export interface DrugOption {
  drug_id: number
  name?: string
  category?: string
  spec?: string
  unit?: string
  pack_unit?: string
  pack_size?: number | null
  usable?: Qty
}

export interface DrugOptionListResult {
  list: DrugOption[]
  banned_hint?: string | null
}

export interface DrugArchiveItem {
  drug_id: number
  catalog_id?: number | null
  name?: string
  category?: string
  usable?: Qty
  min_stock?: number | null
  low_stock?: boolean
  has_stock?: boolean
  banned?: boolean
}

export interface DrugArchiveListResult {
  total: number
  list: DrugArchiveItem[]
}

export interface DrugDetail {
  drug_id: number
  category?: string
  catalog_id?: number | null
  name?: string
  catalog_source_text?: string
  spec?: string
  unit?: string
  pack_unit?: string
  pack_unit_options?: string[]
  pack_size?: number | null
  pack_size_locked?: boolean
  min_stock?: number | null
  usable?: Qty
  unit_locked?: boolean
  banned?: boolean
  near_days?: number | null
  version?: number
  updated_at?: string
  updated_by_name?: string
}

export interface DrugSavePayload {
  category?: string
  catalog_id?: number | null
  spec?: string
  unit?: string
  pack_unit?: string
  pack_size?: number | null
  min_stock?: number | null
  min_stock_unit?: string
  version?: number
}

export interface DrugStockSetting {
  near_days_vet: number
  near_days_vaccine: number
  near_days_disinfectant: number
  version: number
}

export function fetchDrugOverview(signal?: AbortSignal): Promise<DrugStockOverview> {
  return http().get<DrugStockOverview>('/drug-stock/overview', undefined, config(signal))
}

export function fetchDrugLots(
  params: { group: 'ALL' | 'ATTENTION' | 'NORMAL'; category?: string; keyword?: string; page: number; size: number },
  signal?: AbortSignal
): Promise<PageResult<DrugLotItem>> {
  return http().get<PageResult<DrugLotItem>>('/drug-lots', { ...params }, config(signal))
}

export function fetchDrugLot(lotId: number, signal?: AbortSignal): Promise<DrugLotDetail> {
  return http().get<DrugLotDetail>(`/drug-lots/${lotId}`, undefined, config(signal))
}

export function fetchDrugLotOptions(
  drugId: number,
  purpose: 'USE' | 'RETURN',
  needQty?: number,
  signal?: AbortSignal
): Promise<DrugLotOptions> {
  return http().get<DrugLotOptions>('/drug-lots/options', { drug_id: drugId, purpose, need_qty: needQty }, config(signal))
}

export function fetchDrugs(params: { keyword?: string; category?: string } = {}, signal?: AbortSignal): Promise<DrugArchiveListResult> {
  return http().get<DrugArchiveListResult>('/drugs', { ...params }, config(signal))
}

export function fetchDrugOptions(purpose: 'INBOUND' | 'OUTBOUND', keyword?: string, signal?: AbortSignal): Promise<DrugOptionListResult> {
  return http().get<DrugOptionListResult>('/drugs/options', { purpose, keyword }, config(signal))
}

export function fetchDrug(drugId: number, signal?: AbortSignal): Promise<DrugDetail> {
  return http().get<DrugDetail>(`/drugs/${drugId}`, undefined, config(signal))
}

export function createDrug(payload: DrugSavePayload): Promise<DrugDetail> {
  return http().post<DrugDetail>('/drugs', payload)
}

export function updateDrug(drugId: number, payload: DrugSavePayload): Promise<DrugDetail> {
  return http().put<DrugDetail>(`/drugs/${drugId}`, payload)
}

/* ---------------- M30–M33 入库 / 报废 / 出库 ---------------- */

export interface DrugOutboundPayload {
  out_type: 'USE' | 'RETURN'
  drug_id: number
  drug_lot_id: number
  input_qty: number
  input_unit?: string
  target_type?: 'BATCH' | 'PEN'
  batch_id?: number
  pen_id?: number
  receiver_id?: number
  return_to?: string
  return_reason?: string
  remark?: string
  out_date: string
  stock_version: number
}

export interface DrugScrapPayload {
  drug_lot_id: number
  input_qty: number
  input_unit?: string
  reason: 'PLATFORM_RECALL' | 'EXPIRED' | 'DAMAGED' | 'OTHER'
  remark?: string
  scrap_date?: string
  stock_version: number
}

export function createDrugOutbound(payload: DrugOutboundPayload, key: string): Promise<StockOutbound> {
  return http().post<StockOutbound>('/drug-outbounds', payload, idempotency(key))
}

export function createDrugScrap(payload: DrugScrapPayload, key: string): Promise<StockLoss> {
  return http().post<StockLoss>('/drug-scraps', payload, idempotency(key))
}

export function fetchAllocationPreview(params: {
  drug_lot_id: number
  input_qty: number
  input_unit: string
  target_type: 'BATCH' | 'PEN'
  batch_id?: number
  pen_id?: number
}): Promise<CostAllocation> {
  return http().get<CostAllocation>('/drug-outbounds/allocation-preview', { ...params })
}

/* ---------------- M40 / M41 临期天数 ---------------- */

export function fetchStockSetting(signal?: AbortSignal): Promise<DrugStockSetting> {
  return http().get<DrugStockSetting>('/stock-settings', undefined, config(signal))
}

export function saveStockSetting(payload: Omit<DrugStockSetting, 'version'> & { version: number }): Promise<DrugStockSetting> {
  return http().put<DrugStockSetting>('/stock-settings', payload)
}

/* ---------------- M42 提交结果 ---------------- */

export function fetchRequestStatus(requestId: string): Promise<RequestStatus> {
  return http().get<RequestStatus>(`/stock-requests/${encodeURIComponent(requestId)}`)
}

/* ---------------- 借用的栏舍 / 批次 / 成员 / 目录 ---------------- */

export interface PenOption {
  pen_id: number
  pen_code?: string
  pen_name?: string
  pen_type?: string
  stock_count?: number
  capacity?: number
  unit_text?: string
  disabled?: boolean
}

export interface ActiveBatch {
  batch_id: number
  batch_no?: string
  breed_title?: string
  category_name?: string
  current_count?: number
  unit_text?: string
  pens?: Array<{ pen_id: number; pen_name?: string; stock_count?: number }>
}

export interface FarmMember {
  member_id: number
  user_id: number
  name?: string
  role?: string
  role_text?: string
  pens?: Array<{ pen_id: number; name?: string; pen_type_text?: string }>
}

export interface CatalogItem {
  id: number
  name?: string
  drug_category?: string
  spec?: string
  measure_unit?: string
  pack_unit?: string
  pack_size?: number | null
}

export interface CatalogResult {
  items: CatalogItem[]
  banned_hit?: string | null
}

export function fetchPenOptions(): Promise<PenOption[]> {
  return http().get<PenOption[]>('/pens', { page: 1, size: 100 })
}

export function fetchActiveBatches(): Promise<PageResult<ActiveBatch>> {
  return http().get<PageResult<ActiveBatch>>('/batches', { status: 'ACTIVE', page: 1, size: 100 })
}

export function fetchFarmMembers(farmId: number): Promise<PageResult<FarmMember>> {
  return http().get<PageResult<FarmMember>>(`/farms/${farmId}/members`, { page: 1, size: 100 })
}

export function fetchCatalog(params: { drug_category?: string; keyword?: string; size?: number }, signal?: AbortSignal): Promise<CatalogResult> {
  return http().get<CatalogResult>('/base/drug-catalog', { ...params }, config(signal))
}

export interface FeedTypeDict {
  id: number
  code: string
  name: string
}

export function fetchFeedTypes(): Promise<FeedTypeDict[]> {
  return http().get<FeedTypeDict[]>('/base/dicts/FEED_TYPE')
}

export interface BaseCategory {
  id: number
  name: string
  species?: string
  species_text?: string
  count_unit?: string
  count_unit_text?: string
  is_breeding?: boolean
}

export function fetchCategories(): Promise<BaseCategory[]> {
  return http().get<BaseCategory[]>('/base/categories')
}
