import type { FileVO } from '@zmt/shared'
import { http } from '@/api/client'

/**
 * 模块10 收支、出栏、行情与报表接口。
 * 服务端 JSON 为 snake_case，在本层统一转 camelCase；页面与 store 只处理 camelCase 字段。
 * 请求体保持服务端要求的 snake_case。
 */

export interface Paged<T> {
  list: T[]
  total: number
  page: number
  size: number
  hasMore: boolean
  tabCounts: Record<string, number>
}

export interface MonthBalance {
  balanceAmount?: number
  income?: number
  expenseAmount?: number
  prevBalanceAmount?: number
  balanceMomRate?: number
  comparable?: boolean
}

export interface ReceivableSummary {
  totalUnpaidAmount?: number
  orderCount?: number
  overdueCount?: number
  overdueUnpaidAmount?: number
  nearestDueDate?: string
}

export interface PriceCard {
  categoryName?: string
  provinceName?: string
  price?: number
  priceUnit?: string
  priceUnitText?: string
  changeRate?: number
  direction?: string
  priceDate?: string
  compareDate?: string
  comparePrice?: number
}

export interface BusinessOverview {
  today?: string
  pendingSupplementCount?: number
  monthBalance?: MonthBalance
  receivable?: ReceivableSummary
  priceCard?: PriceCard
  monthSales?: { orderCount?: number; saleAmount?: number }
  monthRecords?: { total?: number; autoCount?: number; manualCount?: number }
  todos?: { total?: number; items?: TodoItem[] }
}

export interface TodoItem {
  todoId?: number
  todoType?: string
  title?: string
  subtitle?: string
  dueDate?: string
  overdue?: boolean
  tagText?: string
  bizType?: string
  bizId?: number
}

export interface CategoryShare {
  categoryId?: number
  name?: string
  icon?: string
  amount?: number
  shareRate?: number
  momRate?: number
  comparable?: boolean
  trend?: string
}

export interface FarmFinance {
  farmId?: number
  farmName?: string
  role?: string
  roleText?: string
  mainCategoryName?: string
  income?: number
  expenseAmount?: number
  balanceAmount?: number
  incomeMomRate?: number
  expenseMomRate?: number
  balanceMomRate?: number
  incomeComparable?: boolean
  expenseComparable?: boolean
  balanceComparable?: boolean
  incomeShareRate?: number
}

export interface FinanceSummary {
  periodStart?: string
  periodEnd?: string
  isCurrentMonth?: boolean
  income?: number
  expenseAmount?: number
  balanceAmount?: number
  prevIncome?: number
  prevExpenseAmount?: number
  prevBalanceAmount?: number
  incomeComparable?: boolean
  expenseComparable?: boolean
  balanceComparable?: boolean
  incomeMomRate?: number
  expenseMomRate?: number
  balanceMomRate?: number
  recordCount?: number
  autoCount?: number
  manualCount?: number
  manageableFarmCount?: number
  expenseShares?: CategoryShare[]
  incomeShares?: CategoryShare[]
  farms?: FarmFinance[]
  staffFarmNames?: string[]
}

export interface FinanceRecord {
  id?: number
  farmId?: number
  categoryId?: number
  amount?: number
  recordDate?: string
  voucherCount?: number
  title?: string
  categoryName?: string
  categoryIcon?: string
  sourceType?: string
  sourceLabel?: string
  recordTypeText?: string
  statusText?: string
  editable?: boolean
  createdAt?: string
  recordType?: string
  status?: string
  farmName?: string
  sourceNo?: string
  subcategoryId?: number
  subcategoryName?: string
  detailText?: string
  note?: string
  batchId?: number
  batchNo?: string
  sourceTarget?: { bizType?: string; bizId?: number }
}

export interface MediaItem extends FileVO {
  sortNo?: number
  sourceText?: string
  shotBy?: string
  shotAt?: string
}

export interface UserBrief {
  userId?: number
  name?: string
  avatarUrl?: string
  role?: string
  roleText?: string
}

export interface FinanceRecordDetail extends FinanceRecord {
  vouchers?: MediaItem[]
  createdBy?: UserBrief
  version?: number
}

export interface FinanceRecordPage extends Paged<FinanceRecord> {
  totals?: { income?: number; expenseAmount?: number }
}

export interface FinanceCategory {
  id?: number
  code?: string
  name?: string
  icon?: string
  direction?: string
  sortNo?: number
  autoSource?: boolean
  markOnly?: boolean
  isFallback?: boolean
  subcategories?: { id?: number; name?: string; createdAt?: string }[]
}

export interface DuplicateCheck {
  hit?: boolean
  recordId?: number
  categoryName?: string
  amount?: number
  recordDate?: string
  sourceLabel?: string
}

export interface RecordLog {
  id?: number
  operator?: UserBrief
  opAction?: string
  opActionText?: string
  changes?: { fieldName?: string; before?: string; after?: string }[]
  reason?: string
  changeRequestNo?: string
  createdAt?: string
}

export interface SaleOrderItem {
  id?: number
  batchId?: number
  batchNo?: string
  penName?: string
  buyerName?: string
  saleDate?: string
  quantity?: number
  countUnitText?: string
  totalWeight?: number
  saleAmount?: number
  receivedAmount?: number
  unpaidAmount?: number
  progressRate?: number
  paymentStatusText?: string
  hasPendingChange?: boolean
  effectiveDueDate?: string
  dueStateText?: string
  voidByReview?: boolean
  createdAt?: string
  paymentStatus?: string
  dueState?: string
  dueDate?: string
  overdueDays?: number
  daysToDue?: number
  paidOffDate?: string
  voidedAt?: string
  voidReason?: string
}

export interface PenAllocation {
  penId?: number
  penName?: string
  quantity?: number
}

export interface SaleReceipt {
  id?: number
  amount?: number
  receiptDate?: string
  paymentMethod?: string
  paymentMethodText?: string
  operatorName?: string
  financeRecordId?: number
  status?: string
}

export interface SettlementBrief {
  status?: string
  profit?: number
  saleIncome?: number
  costTotal?: number
  costPerHead?: number
  fcr?: number
  pendingSupplementCount?: number
}

export interface SaleOrderDetail extends SaleOrderItem {
  saleNo?: string
  categoryName?: string
  breedName?: string
  pricingMethod?: string
  pricingMethodText?: string
  unitPrice?: number
  avgWeight?: number
  batchStatus?: string
  registrant?: UserBrief
  registeredAt?: string
  contentVersion?: number
  version?: number
  refUnitPrice?: number
  penAllocations?: PenAllocation[]
  receipts?: SaleReceipt[]
  pendingChange?: { changeRequestId?: number; changeType?: string; fieldCode?: string; summaryText?: string }
  actions?: {
    canReceive?: boolean
    canEdit?: boolean
    canVoid?: boolean
    canApplyChange?: boolean
    changeInReview?: boolean
  }
  settlementBrief?: SettlementBrief
  receiptResult?: { amount?: number; unpaidAmount?: number; paidOff?: boolean }
}

export interface MonthGroup {
  month?: string
  label?: string
  count?: number
  saleAmount?: number
}

export interface SaleOrderPage extends Paged<SaleOrderItem> {
  monthGroups: MonthGroup[]
}

export interface ReceivablePage extends Paged<SaleOrderItem> {
  reminderReceivers?: string
  remindRuleText?: string
  summary?: ReceivableSummary
}

export interface SaleOrderBatchOption {
  batchId?: number
  batchNo?: string
  penName?: string
  categoryId?: number
  categoryName?: string
  breedName?: string
  countUnit?: string
  countUnitText?: string
  dayAge?: number
  currentCount?: number
  entryDate?: string
  inWithdrawal?: boolean
  pens?: { penId?: number; penName?: string; stockCount?: number }[]
  sellableDate?: string
  withdrawalDaysLeft?: number
  withdrawalDrugName?: string
}

export interface SaleOrderPrefill {
  marketPrice?: { provinceName?: string; categoryName?: string; price?: number; priceUnitText?: string; priceDate?: string }
  recentBuyers?: string[]
  lastBuyer?: string
  lastPaymentMethod?: string
}

export interface SaleOrderCreated {
  id?: number
  batchNo?: string
  remainingCount?: number
  countUnitText?: string
  batchEnded?: boolean
}

export interface ChangePreview {
  saleAmountBefore?: number
  unpaidAmountBefore?: number
  receivedAmount?: number
  profitBefore?: number
  saleAmountAfter?: number
  unpaidAmountAfter?: number
  profitAfter?: number
  turnToLoss?: boolean
  restoreCount?: number
  voidReceiptCount?: number
  voidReceiptAmount?: number
}

export interface SettlementOption {
  batchId?: number
  batchNo?: string
  penName?: string
  batchStatus?: string
  settlementStatus?: string
  pendingSupplementCount?: number
}

export interface SettlementOptionPage extends Paged<SettlementOption> {
  farmPendingSupplementCount?: number
}

export interface BatchSettlement {
  settlementId?: number
  status?: string
  settledAt?: string
  calcAt?: string
  profit?: number
  saleIncome?: number
  receivedAmount?: number
  costTotal?: number
  perHeadBasis?: string
  perHeadCount?: number
  version?: number
  batch?: {
    batchId?: number
    batchNo?: string
    penName?: string
    breedName?: string
    categoryName?: string
    countUnitText?: string
    dayAge?: number
    entryCount?: number
    deathCount?: number
    soldCount?: number
    currentCount?: number
    endDate?: string
  }
  costPerHead?: number
  profitPerHead?: number
  fcr?: number
  feedKg?: number
  netGainKg?: number
  costs?: { costGroup?: string; costGroupText?: string; amount?: number; shareRate?: number; note?: string }[]
  saleOrders?: {
    id?: number
    saleDate?: string
    quantity?: number
    totalWeight?: number
    unitPrice?: number
    paymentStatus?: string
    saleAmount?: number
  }[]
  supplements?: { id?: number; bizDate?: string; title?: string; amount?: number; costGroup?: string }[]
  previewAfter?: { costOther?: number; costTotal?: number; profit?: number }
  recentCostRecords?: { title?: string; bizDate?: string; amount?: number; costGroupText?: string }[]
}

export interface MarketFilter {
  provinces: { code?: string; name?: string }[]
  categories: { id?: number; name?: string }[]
  breeds: { categoryId?: number; breedId?: number; name?: string }[]
  priceTypes: { code?: string; name?: string }[]
  defaults?: { provinceCode?: string; categoryId?: number; breedId?: number; priceType?: string }
  defaultDesc?: string
}

export interface MarketTrend {
  latest?: { price?: number; priceUnit?: string; priceUnitText?: string; priceDate?: string }
  change?: { changeRate?: number; direction?: string; compareDate?: string; comparePrice?: number }
  series?: { date?: string; price?: number; corrected?: boolean }[]
  stats?: { maxPrice?: number; maxDate?: string; minPrice?: number; minDate?: string; avgPrice?: number }
  empty?: boolean
}

export interface DailyPrice {
  priceDate?: string
  price?: number
  priceUnitText?: string
  changeRate?: number
  direction?: string
  corrected?: boolean
  correctedAt?: string
}

export interface SaleAdvice {
  id?: number
  batchId?: number
  batchNo?: string
  penName?: string
  status?: string
  conclusion?: string
  conclusionText?: string
  inWithdrawal?: boolean
  basisInsufficient?: boolean
  generatedAt?: string
  windowStart?: string
  windowEnd?: string
  sellableDate?: string
  withdrawalDaysLeft?: number
  reasons?: { withdrawal?: string; age?: string; weight?: string; price?: string }
  risks?: string[]
  basisNote?: string
  batchEndDate?: string
  sourceLabel?: string
}

export interface ReportPeriodInfo {
  start?: string
  end?: string
  label?: string
  toDate?: string
}

export interface ReportPeriod {
  periodStart?: string
  periodEnd?: string
  label?: string
  generatedAt?: string
}

export interface ReportCheckItem {
  batchId?: number
  batchNo?: string
  penName?: string
  farmName?: string
  expected?: number
  actual?: number
  diff?: number
}

export interface BusinessReport {
  period?: ReportPeriodInfo
  scope?: string
  includedFarms?: { farmId?: number; name?: string; role?: string; roleText?: string }[]
  staffFarmNames?: string[]
  empty?: boolean
  dataCheck?: { hasIssue?: boolean; batchCount?: number; moreCount?: number; items?: ReportCheckItem[] }
  metrics?: {
    stock?: { countUnit?: string; countUnitText?: string; begin?: number; end?: number; change?: number }[]
    mortality?: { rate?: number; changePoint?: number; deathCount?: number; comparable?: boolean }
    feed?: { kg?: number; changeRate?: number; comparable?: boolean }
    balance?: { amount?: number; changeRate?: number; comparable?: boolean }
  }
  batchProfits?: { profitTotal?: number; items?: { batchId?: number; batchNo?: string; farmName?: string; profit?: number }[] }
  runningBatches?: { batchId?: number; batchNo?: string; penName?: string; costTotal?: number }[]
}

export interface ReportTrend {
  metric?: string
  points?: { date?: string; value?: number }[]
  events?: { date?: string; type?: string; count?: number }[]
  equation?: { begin?: number; entry?: number; death?: number; sold?: number; end?: number }
}

export interface ReportSummary {
  status?: string
  highlight?: string
  problem?: string
  suggestion?: string
  generatedAt?: string
  outdated?: boolean
}

export interface ReportExport {
  exportId?: number
  fileId?: number
  fileName?: string
  downloadUrl?: string
  expireAt?: string
}

/* ---------- 转换 ---------- */

function camelKey(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, char: string) => char.toUpperCase())
}

function camelize<T>(value: unknown): T {
  if (Array.isArray(value)) return value.map((item) => camelize(item)) as T
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
      result[camelKey(key)] = camelize(item)
    }
    return result as T
  }
  return value as T
}

function normalizePage<T>(page: unknown): Paged<T> {
  const raw = (page || {}) as Record<string, unknown>
  const list = Array.isArray(raw.list) ? (raw.list as unknown[]).map((item) => camelize<T>(item)) : []
  return {
    list,
    total: Number(raw.total || 0),
    page: Number(raw.page || 1),
    size: Number(raw.size || list.length),
    hasMore: Boolean(raw.has_more ?? raw.hasMore),
    tabCounts: camelize<Record<string, number>>(raw.tab_counts || raw.tabCounts || {})
  }
}

function normalizeList<T>(rows: unknown): T[] {
  return Array.isArray(rows) ? rows.map((row) => camelize<T>(row)) : []
}

function normalizeRecordPage(page: unknown): FinanceRecordPage {
  const raw = (page || {}) as Record<string, unknown>
  return { ...normalizePage<FinanceRecord>(page), totals: camelize(raw.totals) }
}

function normalizeSaleOrderPage(page: unknown): SaleOrderPage {
  const raw = (page || {}) as Record<string, unknown>
  return { ...normalizePage<SaleOrderItem>(page), monthGroups: normalizeList<MonthGroup>(raw.month_groups) }
}

function normalizeReceivablePage(page: unknown): ReceivablePage {
  const raw = (page || {}) as Record<string, unknown>
  return {
    ...normalizePage<SaleOrderItem>(page),
    reminderReceivers: raw.reminder_receivers as string | undefined,
    remindRuleText: raw.remind_rule_text as string | undefined,
    summary: camelize<ReceivableSummary>(raw.summary)
  }
}

export interface FinanceRecordPayload {
  record_type: string
  amount: number
  category_id: number
  subcategory_id?: number | null
  note?: string
  record_date: string
  batch_id?: number | null
  file_ids?: number[]
  version?: number
}

export interface SaleOrderPayload {
  batch_id: number
  sale_date: string
  quantity: number
  total_weight?: number
  pricing_method: string
  unit_price: number
  buyer_name: string
  due_date?: string | null
  version?: number
}

export interface ReceiptPayload {
  amount: number
  receipt_date: string
  payment_method: string
}

export interface ReportQueryPayload {
  report_type: string
  period_start?: string
  scope?: string
  range_type?: string
  range_ref_id?: number | null
  metric?: string
  format?: string
  [key: string]: unknown
}

export const financeApi = {
  overview(): Promise<BusinessOverview> {
    return http().get<unknown>('/business-overview').then((data) => camelize<BusinessOverview>(data))
  },

  monthlySummary(month: string, scope?: string): Promise<FinanceSummary> {
    return http()
      .get<unknown>('/finance-records/monthly-summary', { month, scope })
      .then((data) => camelize<FinanceSummary>(data))
  },

  records(params: Record<string, unknown>): Promise<FinanceRecordPage> {
    return http().get<unknown>('/finance-records', params).then(normalizeRecordPage)
  },

  recordDetail(id: number): Promise<FinanceRecordDetail> {
    return http().get<unknown>(`/finance-records/${id}`).then((data) => camelize<FinanceRecordDetail>(data))
  },

  createRecord(payload: FinanceRecordPayload, requestKey?: string): Promise<FinanceRecord> {
    return http()
      .post<unknown>('/finance-records', payload, requestKey ? { headers: { 'Idempotency-Key': requestKey } } : undefined)
      .then((data) => camelize<FinanceRecord>(data))
  },

  updateRecord(id: number, payload: FinanceRecordPayload): Promise<FinanceRecord> {
    return http().put<unknown>(`/finance-records/${id}`, payload).then((data) => camelize<FinanceRecord>(data))
  },

  voidRecord(id: number, version?: number): Promise<FinanceRecord> {
    return http().post<unknown>(`/finance-records/${id}/void`, { version }).then((data) => camelize<FinanceRecord>(data))
  },

  duplicateCheck(params: {
    record_type: string
    category_id: number
    amount: number
    record_date: string
    exclude_id?: number
  }): Promise<DuplicateCheck> {
    return http().get<unknown>('/finance-records/duplicate-check', params).then((data) => camelize<DuplicateCheck>(data))
  },

  categories(direction?: string): Promise<FinanceCategory[]> {
    return http().get<unknown>('/finance-categories', direction ? { direction } : {}).then((data) => normalizeList<FinanceCategory>(data))
  },

  addSubcategory(categoryId: number, name: string): Promise<{ id?: number; name?: string }> {
    return http()
      .post<unknown>('/expense-subcategories', { category_id: categoryId, name })
      .then((data) => camelize<{ id?: number; name?: string }>(data))
  },

  renameSubcategory(id: number, name: string): Promise<{ id?: number; name?: string }> {
    return http()
      .put<unknown>(`/expense-subcategories/${id}`, { name })
      .then((data) => camelize<{ id?: number; name?: string }>(data))
  },

  deleteSubcategory(id: number): Promise<void> {
    return http().del<void>(`/expense-subcategories/${id}`)
  },

  recordLogs(bizType: string, bizId: number): Promise<RecordLog[]> {
    return http()
      .get<unknown>('/record-logs', { biz_type: bizType, biz_id: bizId })
      .then((data) => normalizeList<RecordLog>(data))
  },

  /* 出栏单 */

  batchOptions(): Promise<SaleOrderBatchOption[]> {
    return http().get<unknown>('/sale-orders/batch-options').then((data) => normalizeList<SaleOrderBatchOption>(data))
  },

  saleOrderPrefill(batchId?: number, saleDate?: string): Promise<SaleOrderPrefill> {
    return http()
      .get<unknown>('/sale-orders/prefill', { batch_id: batchId, sale_date: saleDate })
      .then((data) => camelize<SaleOrderPrefill>(data))
  },

  createSaleOrder(payload: SaleOrderPayload, requestKey?: string): Promise<SaleOrderCreated> {
    return http()
      .post<unknown>('/sale-orders', payload, requestKey ? { headers: { 'Idempotency-Key': requestKey } } : undefined)
      .then((data) => camelize<SaleOrderCreated>(data))
  },

  saleOrders(params: Record<string, unknown>): Promise<SaleOrderPage> {
    return http().get<unknown>('/sale-orders', params).then(normalizeSaleOrderPage)
  },

  receivables(params: Record<string, unknown>): Promise<ReceivablePage> {
    return http().get<unknown>('/sale-orders/receivables', params).then(normalizeReceivablePage)
  },

  saleOrderDetail(id: number): Promise<SaleOrderDetail> {
    return http().get<unknown>(`/sale-orders/${id}`).then((data) => camelize<SaleOrderDetail>(data))
  },

  updateSaleOrder(id: number, payload: SaleOrderPayload): Promise<SaleOrderDetail> {
    return http().put<unknown>(`/sale-orders/${id}`, payload).then((data) => camelize<SaleOrderDetail>(data))
  },

  voidSaleOrder(id: number, reason: string, version?: number): Promise<SaleOrderDetail> {
    return http()
      .post<unknown>(`/sale-orders/${id}/void`, { reason, version })
      .then((data) => camelize<SaleOrderDetail>(data))
  },

  addReceipt(id: number, payload: ReceiptPayload, requestKey?: string): Promise<SaleOrderDetail> {
    return http()
      .post<unknown>(`/sale-orders/${id}/receipts`, payload, requestKey ? { headers: { 'Idempotency-Key': requestKey } } : undefined)
      .then((data) => camelize<SaleOrderDetail>(data))
  },

  changePreview(id: number, payload: Record<string, unknown>): Promise<ChangePreview> {
    return http()
      .post<unknown>(`/sale-orders/${id}/change-preview`, payload)
      .then((data) => camelize<ChangePreview>(data))
  },

  /* 批次核算 */

  settlementOptions(params: Record<string, unknown>): Promise<SettlementOptionPage> {
    return http().get<unknown>('/batch-settlements', params).then((page) => {
      const raw = (page || {}) as Record<string, unknown>
      return {
        ...normalizePage<SettlementOption>(page),
        farmPendingSupplementCount: Number(raw.farm_pending_supplement_count || 0)
      }
    })
  },

  settlementDetail(batchId: number): Promise<BatchSettlement> {
    return http().get<unknown>(`/batch-settlements/${batchId}`).then((data) => camelize<BatchSettlement>(data))
  },

  confirmSupplements(batchId: number, supplementIds: number[], version?: number): Promise<BatchSettlement> {
    return http()
      .post<unknown>(`/batch-settlements/${batchId}/confirm-supplements`, {
        supplement_ids: supplementIds,
        version
      })
      .then((data) => camelize<BatchSettlement>(data))
  },

  /* 行情与 AI */

  marketFilters(): Promise<MarketFilter> {
    return http().get<unknown>('/market-prices/filters').then((data) => camelize<MarketFilter>(data))
  },

  marketTrend(params: Record<string, unknown>): Promise<MarketTrend> {
    return http().get<unknown>('/market-prices/trend', params).then((data) => camelize<MarketTrend>(data))
  },

  marketDaily(params: Record<string, unknown>): Promise<Paged<DailyPrice>> {
    return http().get<unknown>('/market-prices/daily', params).then((page) => normalizePage<DailyPrice>(page))
  },

  saleAdviceLatest(batchId: number): Promise<SaleAdvice> {
    // 首次读取会同步生成：服务端允许 60s 推理及 10s 排队，不能沿用普通查询的 15s 超时。
    return http().get<unknown>('/sale-advices/latest', { batch_id: batchId }, { timeout: 90_000 })
      .then((data) => camelize<SaleAdvice>(data))
  },

  regenerateSaleAdvice(batchId: number): Promise<SaleAdvice> {
    return http().post<unknown>('/sale-advices', { batch_id: batchId }, { timeout: 90_000 })
      .then((data) => camelize<SaleAdvice>(data))
  },

  saleAdviceHistory(params: Record<string, unknown>): Promise<Paged<SaleAdvice>> {
    return http().get<unknown>('/sale-advices', params).then((page) => normalizePage<SaleAdvice>(page))
  },

  saleAdviceDetail(id: number): Promise<SaleAdvice> {
    return http().get<unknown>(`/sale-advices/${id}`).then((data) => camelize<SaleAdvice>(data))
  },

  /* 经营报表 */

  reportPeriods(reportType: string, scope?: string): Promise<{ periods: ReportPeriod[]; nextGenerationDate?: string }> {
    return http()
      .get<unknown>('/business-reports/periods', { report_type: reportType, scope })
      .then((data) => {
        const raw = (data || {}) as Record<string, unknown>
        return {
          periods: normalizeList<ReportPeriod>(raw.periods),
          nextGenerationDate: raw.next_generation_date as string | undefined
        }
      })
  },

  reportView(params: ReportQueryPayload): Promise<BusinessReport> {
    return http().get<unknown>('/business-reports/view', params).then((data) => camelize<BusinessReport>(data))
  },

  reportTrend(params: ReportQueryPayload): Promise<ReportTrend> {
    return http().get<unknown>('/business-reports/trend', params).then((data) => camelize<ReportTrend>(data))
  },

  reportSummary(params: ReportQueryPayload): Promise<ReportSummary> {
    return http().get<unknown>('/business-reports/ai-summary', params).then((data) => camelize<ReportSummary>(data))
  },

  regenerateReportSummary(payload: ReportQueryPayload): Promise<ReportSummary> {
    return http().post<unknown>('/business-reports/ai-summary/regenerate', payload).then((data) => camelize<ReportSummary>(data))
  },

  exportReport(payload: ReportQueryPayload): Promise<ReportExport> {
    return http().post<unknown>('/business-reports/export', payload).then((data) => camelize<ReportExport>(data))
  },

  uploadFile(file: File, scene: string, onProgress?: (percent: number) => void): Promise<FileVO> {
    const form = new FormData()
    form.append('file', file)
    form.append('scene', scene)
    return http().upload<FileVO>('/files', form, onProgress)
  },

  baseCategories(): Promise<{ id?: number; name?: string; count_unit_text?: string }[]> {
    return http().get<unknown>('/base/categories').then((data) => normalizeList(data))
  }
}

export function newRequestKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
