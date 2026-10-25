<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, formatDate, formatDateTime, formatNumber, formatPercent, today } from '@zmt/shared'
import {
  batchPublishMarketPrices,
  correctMarketPrice,
  downloadMarketPriceImportTemplate,
  getMarketConfigPermission,
  getMarketConfigs,
  getMarketEntryForm,
  getMarketHistory,
  getMarketPriceDetail,
  getMarketPrices,
  getMarketProgress,
  importMarketPrices,
  revokeMarketPrice,
  reviewMarketPrice,
  saveMarketConfigs,
  type MarketConfig,
  type MarketEntryForm,
  type MarketHistoryItem,
  type MarketImportResult,
  type MarketPriceDetail,
  type MarketPriceRow,
  type MarketProgress
} from '@/api/content'
import { getCategoryTree, type CategoryNode, type CategoryNodeBreed } from '@/api/base'
import ImportXlsxDialog from '@/views/common/ImportXlsxDialog.vue'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const canHandle = computed(() => admin.hasPerm('WA-C02:HANDLE'))
const isSuper = computed(() => admin.isSuper)

const list = ref<MarketPriceRow[]>([])
const total = ref(0)
const loading = ref(true)
const listLoading = ref(false)
const error = ref('')
const forbidden = ref(false)
const progress = ref<MarketProgress | null>(null)
const configs = ref<MarketConfig[]>([])
const configPermission = ref(false)
const categories = ref<CategoryNode[]>([])
const query = reactive({
  date: today(),
  status: 'ALL',
  province_code: '',
  category_id: null as number | null,
  page: 1,
  size: 20
})
const tabCounts = reactive<Record<string, number>>({ ALL: 0, PENDING_REVIEW: 0, PUBLISHED: 0, UNPUBLISHED: 0 })
const tabs = computed(() => [
  { label: '全部', value: 'ALL', count: tabCounts.ALL || 0 },
  { label: '待复核', value: 'PENDING_REVIEW', count: tabCounts.PENDING_REVIEW || 0 },
  { label: '已发布', value: 'PUBLISHED', count: tabCounts.PUBLISHED || 0 },
  { label: '未发布', value: 'UNPUBLISHED', count: tabCounts.UNPUBLISHED || 0 }
])
const provinceOptions = computed(() => {
  const map = new Map<string, string>()
  for (const config of configs.value) map.set(config.province_code, config.province_name)
  return [{ title: '全部地区', value: '' }, ...[...map.entries()].map(([value, title]) => ({ title, value }))]
})
const categoryFilterOptions = computed(() => [
  { title: '全部品类', value: null },
  ...[...new Map(configs.value.map((c) => [c.category_id, c.category_name])).entries()].map(([value, title]) => ({ title, value }))
])
const headers = [
  { title: '地区', key: 'province_name', sortable: false, width: 72 },
  { title: '品类 · 品种', key: 'category', sortable: false, width: 144 },
  { title: '价格类型', key: 'price_type', sortable: false, width: 80 },
  { title: '今日价', key: 'price', sortable: false, width: 132, align: 'end' as const },
  { title: '较上一次', key: 'change', sortable: false, width: 168 },
  { title: '状态', key: 'row_status', sortable: false, width: 104 },
  { title: '录入', key: 'entered_by_name', sortable: false, width: 96 },
  { title: '操作', key: 'actions', sortable: false, width: 132, align: 'end' as const }
]

function rowKey(row: MarketPriceRow): string {
  return `${row.config_id}`
}

function changeText(row: MarketPriceRow): string {
  if (row.change_rate === null || row.change_rate === undefined) return '—'
  const value = Number(row.change_rate)
  return `${value > 0 ? '+' : ''}${formatPercent(value, 1)}`
}

function changeSub(row: MarketPriceRow): string {
  return row.prev_price !== null && row.prev_price !== undefined
    ? `${formatDate(row.prev_price_date || '')} ${formatNumber(row.prev_price, 2)}${row.is_prev_corrected ? ' · 已更正' : ''}`
    : '无上一次价格'
}

async function loadBase(): Promise<void> {
  try {
    const [configData, permission, tree] = await Promise.all([getMarketConfigs(), getMarketConfigPermission(), getCategoryTree()])
    configs.value = configData || []
    configPermission.value = !!permission
    categories.value = tree || []
  } catch {
    configs.value = []
  }
}

async function fetchList(): Promise<void> {
  listLoading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const data = await getMarketPrices({
      date: query.date || undefined,
      status: query.status === 'ALL' ? undefined : query.status,
      province_code: query.province_code || undefined,
      category_id: query.category_id,
      page: query.page,
      size: query.size
    })
    list.value = data.list || []
    total.value = data.total || 0
    Object.assign(tabCounts, data.tab_counts || {})
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) {
      forbidden.value = true
    }
    error.value = e instanceof Error ? e.message : '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
    listLoading.value = false
  }
}

async function fetchProgress(): Promise<void> {
  try {
    progress.value = await getMarketProgress(query.date || undefined)
  } catch {
    progress.value = null
  }
}

function applyTab(value: string): void {
  query.status = value
  query.page = 1
  void fetchList()
}

function resetFilters(): void {
  query.status = 'ALL'
  query.province_code = ''
  query.category_id = null
  query.page = 1
  void fetchList()
}

function changePage(page: number): void {
  query.page = page
  void fetchList()
}

function changeSize(size: number): void {
  query.size = size
  query.page = 1
  void fetchList()
}

function changeDate(): void {
  query.page = 1
  void Promise.all([fetchList(), fetchProgress()])
}

function goPublishProvince(provinceName: string): void {
  const config = configs.value.find((c) => c.province_name === provinceName)
  if (!config) return
  openBatch(config.province_code)
}

/* ---------------- 表格导入 ---------------- */
const importOpen = ref(false)

function openImport(): void {
  if (!canHandle.value) {
    ui.toast('你没有行情导入权限', 'error')
    return
  }
  importOpen.value = true
}

function importSuccessText(result: MarketImportResult): string {
  return `已导入 ${result.success} 行行情`
}

async function afterImport(): Promise<void> {
  query.page = 1
  await Promise.all([fetchList(), fetchProgress()])
}

/* ---------------- 批量发布 ---------------- */
const publishOpen = ref(false)
const entryLoading = ref(false)
const entryForm = ref<MarketEntryForm | null>(null)
const publishProvince = ref('')
const publishLoading = ref(false)
const publishError = ref('')
const publishFieldErrors = reactive<Record<string, string>>({})
const publishForm = reactive({
  price_date: today(),
  data_source: '',
  remark: '',
  rows: [] as Array<{ config_id: number; price: string; price_unit: string; category_name: string; breed_name: string; price_type_text: string; last_price?: number | null; last_unit_text?: string | null; last_date?: string | null; is_over: boolean; change_rate?: number | null; is_readonly: boolean }>
})

const publishRows = computed(() => publishForm.rows.filter((r) => !r.is_readonly && r.price !== '' && Number(r.price) > 0))
const overRows = computed(() => publishRows.value.filter((r) => r.is_over))

function calculatePublishRows(): void {
  for (const row of publishForm.rows) {
    const price = Number(row.price)
    const prev = row.last_price
    if (!price || price <= 0 || prev === null || prev === undefined || Number(prev) <= 0) {
      row.is_over = false
      row.change_rate = null
      continue
    }
    const rate = (price - Number(prev)) / Number(prev)
    row.change_rate = rate
    row.is_over = Math.abs(rate) > 0.2
  }
}

watch(() => publishForm.rows.map((r) => r.price).join(','), calculatePublishRows)

async function openBatch(provinceCode: string, date = query.date): Promise<void> {
  if (!canHandle.value) {
    ui.toast('你没有行情发布权限', 'error')
    return
  }
  publishProvince.value = provinceCode
  publishOpen.value = true
  entryLoading.value = true
  entryForm.value = null
  publishError.value = ''
  Object.keys(publishFieldErrors).forEach((k) => delete publishFieldErrors[k])
  publishForm.price_date = date
  publishForm.data_source = ''
  publishForm.remark = ''
  publishForm.rows = []
  try {
    const data = await getMarketEntryForm(date, provinceCode)
    entryForm.value = data
    publishForm.rows = (data.rows || []).map((row) => ({
      config_id: row.config_id,
      price: row.today_price !== null && row.today_price !== undefined ? String(row.today_price) : '',
      price_unit: row.default_unit,
      category_name: row.category_name,
      breed_name: row.breed_name,
      price_type_text: row.price_type_text,
      last_price: row.last_price,
      last_unit_text: row.last_unit_text,
      last_date: row.last_date,
      is_over: false,
      change_rate: null,
      is_readonly: row.is_readonly
    }))
    calculatePublishRows()
  } catch (e) {
    publishError.value = e instanceof Error ? e.message : '录入表加载失败'
  } finally {
    entryLoading.value = false
  }
}

function openPublishPicker(): void {
  const first = configs.value.find((c) => c.province_code && c.is_enabled)
  if (!first) {
    ui.toast('还没有启用的行情发布配置，请先在发布配置中添加', 'error')
    return
  }
  void openBatch(first.province_code)
}

const unitItems = [
  { title: '元/千克', value: 'YUAN_PER_KG' },
  { title: '元/头', value: 'YUAN_PER_HEAD' },
  { title: '元/只', value: 'YUAN_PER_BIRD' }
]

function validatePublish(): boolean {
  Object.keys(publishFieldErrors).forEach((k) => delete publishFieldErrors[k])
  publishError.value = ''
  if (!publishForm.price_date) publishFieldErrors.price_date = '请选择价格日期'
  if (!publishProvince.value) publishFieldErrors.province_code = '请选择地区'
  if (!publishForm.data_source.trim()) publishFieldErrors.data_source = '请填写数据来源'
  if (!publishRows.value.length) publishFieldErrors.items = '请至少填写一项价格'
  for (const [index, row] of publishForm.rows.entries()) {
    if (row.is_readonly || row.price === '') continue
    const value = Number(row.price)
    if (!Number.isFinite(value) || value <= 0) publishFieldErrors[`items[${index}].price`] = '价格必须大于 0'
    else if (!/^\d+(\.\d{1,2})?$/.test(row.price)) publishFieldErrors[`items[${index}].price`] = '最多保留 2 位小数'
  }
  const first = Object.values(publishFieldErrors)[0]
  if (first) publishError.value = first
  return !first
}

async function submitPublish(): Promise<void> {
  if (!validatePublish()) return
  publishLoading.value = true
  publishError.value = ''
  try {
    const result = await batchPublishMarketPrices({
      price_date: publishForm.price_date,
      province_code: publishProvince.value,
      data_source: publishForm.data_source.trim(),
      remark: publishForm.remark.trim() || undefined,
      items: publishRows.value.map((row) => ({ config_id: row.config_id, price: row.price, price_unit: row.price_unit }))
    })
    publishOpen.value = false
    ui.toast(result.review_count ? `已发布 ${result.published_count} 项，${result.review_count} 项等待复核` : `已发布 ${result.published_count} 项`)
    query.date = publishForm.price_date
    await Promise.all([fetchList(), fetchProgress()])
  } catch (e) {
    if (e instanceof Error) {
      publishError.value = e.message
      if ('fieldErrors' in e && Array.isArray((e as { fieldErrors?: unknown[] }).fieldErrors)) {
        for (const item of (e as { fieldErrors: Array<{ field: string; message: string }> }).fieldErrors) publishFieldErrors[item.field] = item.message
      }
    }
  } finally {
    publishLoading.value = false
  }
}

/* ---------------- 详情 / 复核 / 更正 / 历史 ---------------- */
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<MarketPriceDetail | null>(null)

async function openDetail(row: MarketPriceRow): Promise<void> {
  if (!row.price_id) return
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getMarketPriceDetail(row.price_id)
  } catch (e) {
    detailOpen.value = false
    ui.toast(e instanceof Error ? e.message : '详情加载失败', 'error')
  } finally {
    detailLoading.value = false
  }
}

const reviewOpen = ref(false)
const reviewSaving = ref(false)
const reviewError = ref('')
const reviewForm = reactive({ result: 'PASS', review_note: '' })
const reviewTarget = ref<MarketPriceDetail | null>(null)

function openReview(row: MarketPriceRow): void {
  if (!row.price_id) return
  void openReviewDetail(row.price_id)
}

async function openReviewDetail(id: number): Promise<void> {
  try {
    reviewTarget.value = await getMarketPriceDetail(id)
    reviewForm.result = 'PASS'
    reviewForm.review_note = ''
    reviewError.value = ''
    reviewOpen.value = true
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '加载失败', 'error')
  }
}

async function submitReview(): Promise<void> {
  if (!reviewTarget.value) return
  if (!reviewForm.review_note.trim()) {
    reviewError.value = '请填写复核说明'
    return
  }
  if (reviewTarget.value.entered_by_name && reviewTarget.value.entered_by_name === admin.displayName) {
    reviewError.value = '复核人不能是录入人'
    return
  }
  reviewSaving.value = true
  reviewError.value = ''
  try {
    await reviewMarketPrice(reviewTarget.value.price_id!, {
      result: reviewForm.result as 'PASS' | 'RETURN',
      review_note: reviewForm.review_note.trim(),
      correction_id: reviewTarget.value.pending_correction?.correction_id || null,
      version: reviewTarget.value.version
    })
    reviewOpen.value = false
    detailOpen.value = false
    ui.toast(reviewForm.result === 'PASS' ? '已复核通过并发布' : `已退回${reviewTarget.value.entered_by_name || '录入人'}修改`)
    await Promise.all([fetchList(), fetchProgress()])
  } catch (e) {
    reviewError.value = e instanceof Error ? e.message : '复核失败'
  } finally {
    reviewSaving.value = false
  }
}

const correctionOpen = ref(false)
const correctionSaving = ref(false)
const correctionError = ref('')
const correctionForm = reactive({ new_price: '', reason: '' })
const correctionTarget = ref<MarketPriceDetail | null>(null)

function openCorrection(row: MarketPriceRow): void {
  if (!row.price_id) return
  void openCorrectionDetail(row.price_id)
}

async function openCorrectionDetail(id: number): Promise<void> {
  try {
    correctionTarget.value = await getMarketPriceDetail(id)
    correctionForm.new_price = correctionTarget.value.price !== null && correctionTarget.value.price !== undefined ? String(correctionTarget.value.price) : ''
    correctionForm.reason = ''
    correctionError.value = ''
    correctionOpen.value = true
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '加载失败', 'error')
  }
}

async function submitCorrection(): Promise<void> {
  if (!correctionTarget.value) return
  if (!correctionForm.new_price) {
    correctionError.value = '请填写更正后的价格'
    return
  }
  if (Number(correctionForm.new_price) === Number(correctionTarget.value.price)) {
    correctionError.value = '更正后的价格不能与原价相同'
    return
  }
  if (!correctionForm.reason.trim()) {
    correctionError.value = '请填写更正原因'
    return
  }
  correctionSaving.value = true
  correctionError.value = ''
  try {
    const result = await correctMarketPrice(correctionTarget.value.price_id!, {
      new_price: correctionForm.new_price,
      reason: correctionForm.reason.trim(),
      version: correctionTarget.value.version || 0
    })
    correctionOpen.value = false
    detailOpen.value = false
    ui.toast(result.status === 'PENDING_REVIEW' ? '更正后涨跌超过 20%，已提交复核' : `已更正 ${formatDate(result.price_date)}的价格`)
    await Promise.all([fetchList(), fetchProgress()])
  } catch (e) {
    correctionError.value = e instanceof Error ? e.message : '更正失败'
  } finally {
    correctionSaving.value = false
  }
}

const revokeOpen = ref(false)
const revokeSaving = ref(false)
const revokeError = ref('')
const revokeReason = ref('')
const revokeTarget = ref<MarketPriceRow | null>(null)
const revokeVersion = ref(0)

async function openRevoke(row: MarketPriceRow): Promise<void> {
  revokeTarget.value = row
  revokeVersion.value = 0
  revokeReason.value = ''
  revokeError.value = ''
  revokeOpen.value = true
  try {
    const detail = await getMarketPriceDetail(row.price_id!)
    revokeVersion.value = detail.version || 0
  } catch {
    /* 提交时服务端返回错误 */
  }
}

async function submitRevoke(): Promise<void> {
  if (!revokeTarget.value?.price_id) return
  if (!revokeReason.value.trim()) {
    revokeError.value = '请填写撤销原因'
    return
  }
  revokeSaving.value = true
  revokeError.value = ''
  try {
    await revokeMarketPrice(revokeTarget.value.price_id, revokeReason.value.trim(), revokeVersion.value)
    revokeOpen.value = false
    detailOpen.value = false
    ui.toast('已撤销当天的价格')
    await Promise.all([fetchList(), fetchProgress()])
  } catch (e) {
    revokeError.value = e instanceof Error ? e.message : '撤销失败'
  } finally {
    revokeSaving.value = false
  }
}

const historyOpen = ref(false)
const historyLoading = ref(false)
const historyItems = ref<MarketHistoryItem[]>([])
const historyTitle = ref('')
const historyError = ref('')

async function openHistory(row: MarketPriceRow): Promise<void> {
  historyOpen.value = true
  historyLoading.value = true
  historyItems.value = []
  historyError.value = ''
  historyTitle.value = `${row.province_name} · ${row.category_name} · ${row.breed_name} · ${row.price_type_text}`
  try {
    historyItems.value = await getMarketHistory({
      province_code: row.province_code,
      category_id: row.category_id,
      breed_id: row.breed_id,
      price_type: row.price_type,
      days: 90
    })
  } catch (e) {
    historyError.value = e instanceof Error ? e.message : '历史价格加载失败'
  } finally {
    historyLoading.value = false
  }
}

/* ---------------- 发布配置 ---------------- */
const configOpen = ref(false)
const configRows = ref<MarketConfig[]>([])
const configForm = reactive({
  province_code: '',
  reason: '',
  rows: [] as Array<{
    config_id?: number
    category_id: number | null
    breed_id: number
    price_type: string
    default_unit: string
    sort_no: number
    is_enabled: boolean
  }>
})
const configError = ref('')
const configSaving = ref(false)
const configBreedOptions = computed(() => {
  const result: Array<{ title: string; value: number }> = []
  for (const category of categories.value) {
    for (const breed of category.breeds || []) result.push({ title: `${category.name} · ${breed.name}`, value: breed.id })
  }
  return result
})
const configCategoryOptions = computed(() => categories.value.map((c) => ({ title: c.name, value: c.id })))
const configTypes = [
  { title: '出栏价', value: 'SLAUGHTER' },
  { title: '种苗价', value: 'SEEDLING' }
]
const configUnits = unitItems
const configProvinceOptions = [{ title: '内蒙古', value: '150000' }, { title: '河北', value: '130000' }, { title: '辽宁', value: '210000' }, { title: '吉林', value: '220000' }, { title: '黑龙江', value: '230000' }, { title: '山东', value: '370000' }]

function openConfig(): void {
  if (!isSuper.value) {
    ui.toast('只有超级管理员可以修改发布配置', 'error')
    return
  }
  configOpen.value = true
  configError.value = ''
  configForm.reason = ''
  const first = configs.value[0]
  configForm.province_code = first?.province_code || '150000'
  void loadConfigProvince()
}

async function loadConfigProvince(): Promise<void> {
  configError.value = ''
  try {
    configRows.value = await getMarketConfigs(configForm.province_code)
    configForm.rows = configRows.value.map((row) => ({
      config_id: row.config_id,
      category_id: row.category_id,
      breed_id: row.breed_id,
      price_type: row.price_type,
      default_unit: row.default_unit,
      sort_no: row.sort_no,
      is_enabled: row.is_enabled
    }))
    if (!configForm.rows.length) addConfigRow()
  } catch (e) {
    configError.value = e instanceof Error ? e.message : '配置加载失败'
  }
}

function addConfigRow(): void {
  configForm.rows.push({ category_id: null, breed_id: 0, price_type: 'SLAUGHTER', default_unit: 'YUAN_PER_KG', sort_no: configForm.rows.length + 1, is_enabled: true })
}

async function submitConfig(): Promise<void> {
  configError.value = ''
  if (!configForm.reason.trim()) {
    configError.value = '请填写修改原因'
    return
  }
  if (!configForm.rows.length || configForm.rows.some((row) => !row.category_id)) {
    configError.value = '请选择品类'
    return
  }
  configSaving.value = true
  try {
    await saveMarketConfigs({
      province_code: configForm.province_code,
      configs: configForm.rows.map((row, index) => ({
        ...row,
        category_id: row.category_id!,
        sort_no: index + 1
      })),
      reason: configForm.reason.trim()
    })
    configOpen.value = false
    ui.toast('已保存发布配置')
    await loadBase()
  } catch (e) {
    configError.value = e instanceof Error ? e.message : '配置保存失败'
  } finally {
    configSaving.value = false
  }
}

watch(() => configForm.province_code, (value, old) => {
  if (value && value !== old && configOpen.value) void loadConfigProvince()
})

onMounted(async () => {
  loading.value = true
  await Promise.all([loadBase(), fetchProgress(), fetchList()])
  loading.value = false
})
</script>

<template>
  <zw-shell side="admin" active="WA-C02" :crumbs="['内容运营', '市场行情发布']">
    <zw-page-head
      title="市场行情发布"
      desc="按地区、品类、品种发布每天的出栏价和种苗价，用户端市场行情和出栏单的参考价都取这里的数据。较上一个有数据的日期涨跌超过 20% 的价格，要由另一位管理员复核后才对用户可见。"
    >
      <template #actions>
        <v-text-field v-model="query.date" type="date" class="zw-w-160 market-date" :max="today()" density="compact" hide-details prepend-inner-icon="calendar_month" @update:model-value="changeDate" />
        <v-btn v-if="canHandle" variant="outlined" prepend-icon="tune" @click="openConfig">发布配置</v-btn>
        <v-btn v-if="canHandle" color="primary" prepend-icon="add" @click="openPublishPicker">发布行情</v-btn>
      </template>
    </zw-page-head>

    <zw-notice v-if="forbidden" type="error" title="你没有行情查看权限">请联系超级管理员在“管理员与权限”中开通 WA-C02 的查看权限。</zw-notice>

    <div v-if="!forbidden" class="market-layout">
      <zw-card flush class="market-main">
        <div class="zw-toolbar market-toolbar">
          <div class="zw-toolbar-l">
            <zw-seg :items="tabs" :model-value="query.status" @update:model-value="applyTab(String($event))" />
          </div>
          <div class="zw-toolbar-r">
            <v-btn v-if="canHandle" variant="outlined" prepend-icon="upload_file" @click="openImport">导入表格</v-btn>
            <v-select v-model="query.province_code" :items="provinceOptions" class="zw-w-120" density="compact" hide-details @update:model-value="fetchList()" />
            <v-select v-model="query.category_id" :items="categoryFilterOptions" class="zw-w-120" density="compact" hide-details @update:model-value="fetchList()" />
          </div>
        </div>

        <v-skeleton-loader v-if="loading" type="table-row@7" />
        <zw-empty v-else-if="error" icon="cloud_off" title="行情加载失败" :desc="error" tone="error">
          <v-btn variant="outlined" @click="fetchList()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!list.length && (query.status !== 'ALL' || query.province_code || query.category_id)" icon="search_off" title="没有符合条件的价格" desc="换一个地区或状态试试。">
          <v-btn variant="text" color="primary" @click="resetFilters">清除筛选</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!list.length" icon="trending_up" title="今天还没有发布行情" desc="按地区录入今天的出栏价和种苗价，用户端市场行情会显示最新价。">
          <v-btn v-if="canHandle" color="primary" @click="openPublishPicker">发布行情</v-btn>
        </zw-empty>
        <template v-else>
          <v-data-table :headers="headers" :items="list" :item-key="rowKey" hide-default-footer density="compact" :loading="listLoading" class="market-table">
            <template #item.province_name="{ item }">{{ item.province_name }}</template>
            <template #item.category="{ item }">
              <div>{{ item.category_name }}</div>
              <div class="market-sub">{{ item.breed_name }}</div>
            </template>
            <template #item.price_type="{ item }">{{ item.price_type_text }}</template>
            <template #item.price="{ item }">
              <template v-if="item.price !== null && item.price !== undefined">
                <span class="market-price num">{{ formatNumber(item.price, 2) }}</span>
                <span class="market-unit"> {{ item.price_unit_text || '' }}</span>
              </template>
              <span v-else class="zw-faint">—</span>
            </template>
            <template #item.change="{ item }">
              <template v-if="item.change_rate !== null && item.change_rate !== undefined">
                <span class="market-change" :class="{ 'market-change--warn': item.is_over_threshold }">
                  <zw-icon :name="Number(item.change_rate) >= 0 ? 'arrow_upward' : 'arrow_downward'" :size="14" />{{ changeText(item as MarketPriceRow) }}
                </span>
                <div class="market-sub" :class="{ 'market-sub--warn': item.is_over_threshold }">{{ changeSub(item as MarketPriceRow) }}</div>
              </template>
              <span v-else class="zw-faint">—</span>
            </template>
            <template #item.row_status="{ item }">
              <zw-tag v-if="item.row_status === 'PENDING_REVIEW'" type="warning" icon="rule" size="small">待复核</zw-tag>
              <zw-tag v-else-if="item.row_status === 'PUBLISHED'" type="success" icon="check" size="small">已发布</zw-tag>
              <zw-tag v-else size="small">未发布</zw-tag>
            </template>
            <template #item.entered_by_name="{ item }">
              <template v-if="item.entered_by_name">
                <div>{{ item.entered_by_name }}</div>
                <div class="market-sub">{{ item.entered_at ? formatDateTime(item.entered_at).slice(11) : '' }}</div>
              </template>
              <span v-else class="zw-faint">—</span>
            </template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a v-if="item.row_status === 'PENDING_REVIEW' && item.can_review !== false && canHandle" class="zw-link" @click="openReview(item as MarketPriceRow)">复核</a>
                <a v-if="(item.row_status === 'PENDING_REVIEW' || item.row_status === 'PUBLISHED')" class="zw-link" @click="openHistory(item as MarketPriceRow)">历史</a>
                <a v-if="item.row_status === 'PUBLISHED' && item.can_correct !== false && canHandle" class="zw-link" @click="openCorrection(item as MarketPriceRow)">更正</a>
                <a v-if="item.row_status === 'PUBLISHED' && query.date === today() && canHandle" class="zw-link danger" @click="openRevoke(item as MarketPriceRow)">撤销</a>
                <a v-if="item.row_status === 'UNPUBLISHED' && canHandle" class="zw-link" @click="openBatch(item.province_code)">发布</a>
                <a v-if="item.price_id && item.row_status !== 'UNPUBLISHED'" class="zw-link" @click="openDetail(item as MarketPriceRow)">详情</a>
              </div>
            </template>
          </v-data-table>
          <div class="zw-table-foot">
            <span class="zw-muted">涨跌按方向显示箭头，超过 20% 用警示色；待复核的价格用户端暂不显示</span>
            <zw-pager :total="total" :page="query.page" :size="query.size" @update:page="changePage" @update:size="changeSize" />
          </div>
        </template>
      </zw-card>

      <aside class="market-side">
        <zw-card :title="`今天未发布 ${progress?.unpublished_count || 0} 项`" class="market-side-card">
          <template #extra>
            <a class="zw-link" @click="progress?.unpublished[0] && goPublishProvince(progress.unpublished[0].province_name)">去发布</a>
          </template>
          <div v-if="progress?.unpublished?.length" class="market-unpublished">
            <div v-for="item in progress.unpublished.slice(0, 6)" :key="item.config_id" class="market-unpublished-row">
              <div class="market-unpublished-main">
                <div>{{ item.province_name }} · {{ item.category_name }} · {{ item.price_type_text }}</div>
                <div class="market-sub">{{ item.breed_name }}<template v-if="item.last_price"> · 上次 {{ formatNumber(item.last_price, 2) }}{{ item.last_unit }}</template></div>
              </div>
              <a class="zw-link" @click="goPublishProvince(item.province_name)">去发布</a>
            </div>
          </div>
          <zw-empty v-else icon="check_circle" title="今天的行情都已处理" desc="未发布和待复核的价格会在这里列出。" />
          <div class="market-hint"><zw-icon name="schedule" :size="16" />10:00 仍未发布的，系统提醒内容运营；用户端显示上一个有数据的日期。</div>
        </zw-card>

        <zw-card title="各地区发布进度" class="market-side-card">
          <div v-if="progress?.provinces?.length" class="market-progress-list">
            <div v-for="province in progress.provinces" :key="province.province_code" class="market-progress-row">
              <span class="market-progress-name">{{ province.province_name }}</span>
              <v-progress-linear :model-value="province.total ? (province.published / province.total) * 100 : 0" height="6" rounded :color="province.published < province.total ? 'warning' : 'primary'" class="market-progress-bar" />
              <span class="num market-progress-num">{{ province.published }} / {{ province.total }}</span>
            </div>
          </div>
          <zw-empty v-else icon="map" title="暂无发布配置" desc="超级管理员配置地区和品类后，这里显示每日进度。" />
        </zw-card>
      </aside>
    </div>

    <!-- 发布抽屉 -->
    <zw-drawer v-model="publishOpen" title="发布行情" sub="按地区一次录入当天的全部价格；涨跌超过 20% 的价格提交后进入待复核" width="760">
      <div class="zw-form-3 market-form-head">
        <zw-form-item label="价格日期" required :error="publishFieldErrors.price_date">
          <v-text-field v-model="publishForm.price_date" type="date" :max="today()" :error="!!publishFieldErrors.price_date" @update:model-value="publishProvince && openBatch(publishProvince, publishForm.price_date)" />
        </zw-form-item>
        <zw-form-item label="地区" required>
          <v-select v-model="publishProvince" :items="provinceOptions.filter((p) => p.value)" :error="!!publishFieldErrors.province_code" @update:model-value="openBatch(String($event), publishForm.price_date)" />
        </zw-form-item>
        <zw-form-item label="数据来源" required :error="publishFieldErrors.data_source">
          <v-text-field v-model="publishForm.data_source" placeholder="例如：9 个收购点报价均价" maxlength="50" :error="!!publishFieldErrors.data_source" />
        </zw-form-item>
      </div>

      <v-skeleton-loader v-if="entryLoading" type="table-row@5" />
      <template v-else-if="entryForm">
        <div class="market-entry-title">{{ entryForm.province_name }} · 今天要发布的价格 <span class="zw-muted">按发布配置列出，留空的不发布</span></div>
        <div class="market-entry-table">
          <table>
            <thead><tr><th>品类 / 品种</th><th>价格类型</th><th>上一次价格</th><th class="text-end">今日价</th><th>计价单位</th><th>较上一次</th></tr></thead>
            <tbody>
              <tr v-for="(row, index) in publishForm.rows" :key="row.config_id" :class="{ 'entry-row-over': row.is_over, 'entry-row-readonly': entryForm.rows[index]?.is_readonly }">
                <td><b>{{ row.category_name }}</b><span class="market-sub"> · {{ row.breed_name }}</span></td>
                <td>{{ row.price_type_text }}</td>
                <td class="num">{{ row.last_price !== null && row.last_price !== undefined ? `${formatNumber(row.last_price, 2)}${row.last_unit_text ? ' ' + row.last_unit_text : ''}` : '—' }}</td>
                <td class="text-end">
                  <v-text-field v-model="row.price" density="compact" hide-details type="number" step="0.01" min="0" class="market-price-input" :disabled="entryForm.rows[index]?.is_readonly" :error="!!publishFieldErrors[`items[${index}].price`]" />
                </td>
                <td>
                  <v-select v-model="row.price_unit" :items="unitItems" density="compact" hide-details :disabled="entryForm.rows[index]?.is_readonly" class="market-unit-select" />
                </td>
                <td>
                  <template v-if="row.change_rate !== null && row.change_rate !== undefined">
                    <span :class="row.is_over ? 'market-change market-change--warn' : 'market-change'">{{ formatPercent(row.change_rate) }}</span>
                    <div v-if="row.is_over" class="market-sub market-sub--warn">需复核</div>
                    <div v-else-if="entryForm.rows[index]?.is_readonly" class="market-sub">今天已发布</div>
                  </template>
                  <span v-else class="zw-faint">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="overRows.length" class="market-over-warning">
          <zw-notice type="warning" :title="`${overRows.length} 项价格涨跌超过 20%，提交后需要复核`">
            {{ overRows.map((row) => `${row.category_name} · ${row.breed_name} · ${row.price_type_text}`).join('；') }}。请核对原始报价；复核通过前，用户端仍显示上一个有数据日期的价格。
          </zw-notice>
        </div>
        <zw-form-item label="备注" optional>
          <v-textarea v-model="publishForm.remark" placeholder="说明价格波动原因，复核人可以看到；最多 100 字" :rows="2" maxlength="100" counter />
        </zw-form-item>
      </template>
      <div v-if="publishError" class="market-form-error">{{ publishError }}</div>
      <template #foot>
        <span class="zw-foot-note">发布记入操作日志；发布后可以更正，更正会在用户端标“已更正”</span>
        <v-btn variant="outlined" :disabled="publishLoading" @click="publishOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="publishLoading" :disabled="!publishRows.length" @click="submitPublish">
          发布 {{ publishRows.length }} 项<template v-if="overRows.length">，{{ overRows.length }} 项提交复核</template>
        </v-btn>
      </template>
    </zw-drawer>

    <!-- 复核 -->
    <zw-dialog v-model="reviewOpen" title="复核这条价格" width="520" icon="rule" tone="warning">
      <template v-if="reviewTarget">
        <div class="market-review-head">{{ reviewTarget.province_name }} · {{ reviewTarget.category_name }} · {{ reviewTarget.breed_name }} · {{ reviewTarget.price_type_text }}</div>
        <div class="market-review-grid">
          <span>录入值</span><b>{{ formatNumber(reviewTarget.price, 2) }} {{ reviewTarget.price_unit_text }} · {{ reviewTarget.entered_by_name || '—' }} {{ reviewTarget.entered_at ? formatDateTime(reviewTarget.entered_at) : '' }}</b>
          <span>较上一次</span><b class="market-warn-text">{{ changeText(reviewTarget as MarketPriceRow) }}（{{ reviewTarget.prev_price !== null && reviewTarget.prev_price !== undefined ? formatNumber(reviewTarget.prev_price, 2) : '—' }} {{ reviewTarget.price_unit_text }}）</b>
          <span>近 7 天</span><b>{{ formatNumber(reviewTarget.near7_min, 2) }} – {{ formatNumber(reviewTarget.near7_max, 2) }} {{ reviewTarget.price_unit_text }}</b>
          <span>数据来源</span><b>{{ reviewTarget.data_source || '—' }}</b>
        </div>
        <zw-form-item label="复核结果" required>
          <v-radio-group v-model="reviewForm.result" inline hide-details density="compact">
            <v-radio label="通过并发布" value="PASS" />
            <v-radio label="退回修改" value="RETURN" />
          </v-radio-group>
        </zw-form-item>
        <zw-form-item label="复核说明" required hint="退回后录入人收到消息；复核人不能是录入人，结果记入操作日志">
          <v-textarea v-model="reviewForm.review_note" placeholder="请填写复核说明" :rows="3" maxlength="100" counter />
        </zw-form-item>
        <div v-if="reviewError" class="market-form-error">{{ reviewError }}</div>
        <div v-if="reviewTarget.pending_correction" class="market-correction-pending">这是一条更正待复核：原价 {{ formatNumber(reviewTarget.price, 2) }}，申请更正为 {{ formatNumber(reviewTarget.pending_correction.new_price, 2) }}；{{ reviewTarget.pending_correction.reason }}</div>
      </template>
      <template #actions>
        <v-btn variant="outlined" :disabled="reviewSaving" @click="reviewOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="reviewSaving" @click="submitReview">提交复核</v-btn>
      </template>
    </zw-dialog>

    <!-- 更正 -->
    <zw-dialog v-model="correctionOpen" title="更正历史价格" width="520" icon="edit" tone="primary">
      <template v-if="correctionTarget">
        <div class="market-review-head">{{ formatDate(correctionTarget.price_date) }} · {{ correctionTarget.province_name }} · {{ correctionTarget.category_name }} · {{ correctionTarget.breed_name }} · {{ correctionTarget.price_type_text }}</div>
        <zw-form-item label="更正为" required>
          <div class="market-correct-row">
            <span class="market-old-price">{{ formatNumber(correctionTarget.price, 2) }}</span>
            <zw-icon name="arrow_forward" :size="16" />
            <v-text-field v-model="correctionForm.new_price" type="number" step="0.01" min="0" density="compact" hide-details suffix="元" class="market-correct-input" />
          </div>
        </zw-form-item>
        <zw-form-item label="更正原因" required>
          <v-textarea v-model="correctionForm.reason" placeholder="请说明更正原因，记入操作日志" :rows="3" maxlength="100" counter />
        </zw-form-item>
        <zw-notice type="info">更正后用户端会标“已更正”；后续日期的涨跌会按新价格重算。若更正后较上一次涨跌超过 20%，需要另一位管理员复核后才生效。</zw-notice>
        <div v-if="correctionError" class="market-form-error">{{ correctionError }}</div>
      </template>
      <template #actions>
        <v-btn variant="outlined" :disabled="correctionSaving" @click="correctionOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="correctionSaving" @click="submitCorrection">确认更正</v-btn>
      </template>
    </zw-dialog>

    <!-- 撤销 -->
    <zw-dialog v-model="revokeOpen" title="撤销当天的价格？" width="480" icon="block" tone="error">
      <p class="market-dialog-text">撤销后用户端立即看不到这条价格。只有当天已发布的价格可以撤销，操作记入操作日志。</p>
      <zw-form-item label="撤销原因" required>
        <v-textarea v-model="revokeReason" placeholder="请填写撤销原因" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <div v-if="revokeError" class="market-form-error">{{ revokeError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="revokeSaving" @click="revokeOpen = false">取消</v-btn>
        <v-btn color="error" :loading="revokeSaving" @click="submitRevoke">确认撤销</v-btn>
      </template>
    </zw-dialog>

    <!-- 价格详情 -->
    <zw-drawer v-model="detailOpen" title="价格详情" width="640">
      <v-skeleton-loader v-if="detailLoading" type="paragraph@6" />
      <template v-else-if="detail">
        <div class="market-review-head">{{ detail.province_name }} · {{ detail.category_name }} · {{ detail.breed_name }} · {{ detail.price_type_text }}</div>
        <zw-desc :cols="2" bordered :label-width="96" class="market-desc">
          <zw-desc-item label="价格">{{ formatNumber(detail.price, 2) }} {{ detail.price_unit_text }}</zw-desc-item>
          <zw-desc-item label="状态">{{ detail.status_text || detail.row_status_text }}</zw-desc-item>
          <zw-desc-item label="较上一次">{{ changeText(detail as MarketPriceRow) }}（{{ formatNumber(detail.prev_price, 2) }}）</zw-desc-item>
          <zw-desc-item label="近 7 天">{{ formatNumber(detail.near7_min, 2) }} – {{ formatNumber(detail.near7_max, 2) }}</zw-desc-item>
          <zw-desc-item label="数据来源">{{ detail.data_source || '—' }}</zw-desc-item>
          <zw-desc-item label="录入人">{{ detail.entered_by_name || '—' }} <span class="zw-muted">{{ detail.entered_at ? formatDateTime(detail.entered_at) : '' }}</span></zw-desc-item>
          <zw-desc-item label="备注" :span="2">{{ detail.remark || '—' }}</zw-desc-item>
          <zw-desc-item label="复核" :span="2">{{ detail.reviewer_name ? `${detail.reviewer_name} · ${detail.review_result_text || ''} · ${detail.review_note || ''}` : '未复核' }}</zw-desc-item>
        </zw-desc>
        <template v-if="detail.corrections?.length">
          <div class="zw-section-title">更正记录</div>
          <zw-timeline :items="detail.corrections.map((c) => ({ title: `${formatNumber(c.old_price, 2)} → ${formatNumber(c.new_price, 2)} · ${c.status_text || c.status}`, time: c.corrected_at ? formatDateTime(c.corrected_at) : '', desc: `${c.reason}${c.corrected_by_name ? ' · ' + c.corrected_by_name : ''}` }))" />
        </template>
      </template>
      <template #foot>
        <v-btn variant="outlined" @click="detailOpen = false">关闭</v-btn>
        <v-btn v-if="detail?.can_correct !== false && detail?.status === 'PUBLISHED' && canHandle" color="primary" @click="detailOpen = false; openCorrectionDetail(detail.price_id!)">更正价格</v-btn>
        <v-btn v-if="detail?.status === 'PENDING_REVIEW' && canHandle" color="primary" @click="detailOpen = false; openReviewDetail(detail.price_id!)">复核</v-btn>
      </template>
    </zw-drawer>

    <!-- 历史 -->
    <zw-drawer v-model="historyOpen" title="近 90 天价格" :sub="historyTitle" width="640">
      <v-skeleton-loader v-if="historyLoading" type="table-row@6" />
      <zw-empty v-else-if="historyError" icon="cloud_off" title="历史价格加载失败" :desc="historyError" tone="error" />
      <zw-empty v-else-if="!historyItems.length" icon="trending_up" title="还没有历史价格" desc="发布后这里会显示价格变化。" />
      <v-data-table v-else :headers="[{ title: '日期', key: 'price_date' }, { title: '价格', key: 'price', align: 'end' }, { title: '涨跌', key: 'change_rate', align: 'end' }, { title: '录入', key: 'entered_by_name' }]" :items="historyItems" hide-default-footer density="compact">
        <template #item.price_date="{ item }">{{ formatDate(item.price_date) }}</template>
        <template #item.price="{ item }"><span class="num">{{ formatNumber(item.price, 2) }} {{ item.price_unit_text }}</span></template>
        <template #item.change_rate="{ item }"><span class="num">{{ item.change_rate !== null && item.change_rate !== undefined ? formatPercent(item.change_rate) : '—' }}</span></template>
        <template #item.entered_by_name="{ item }">{{ item.entered_by_name || '—' }}<span v-if="item.is_corrected" class="zw-muted"> · 已更正</span></template>
      </v-data-table>
      <template #foot><v-btn variant="outlined" @click="historyOpen = false">关闭</v-btn></template>
    </zw-drawer>

    <ImportXlsxDialog
      v-model="importOpen"
      title="导入市场行情"
      hint="按模板填写日期、省份、品类、品种、价格等列；单次最多 200 行，文件不超过 2MB。任一行校验失败整个文件回滚，不会留下脏数据。"
      :download-template="downloadMarketPriceImportTemplate"
      :upload="importMarketPrices"
      :success-text="importSuccessText"
      @success="afterImport"
    />

    <!-- 发布配置 -->
    <zw-drawer v-model="configOpen" title="行情发布配置" sub="按地区维护启用的品类、品种、价格类型与默认单位；新增、修改都记入操作日志" width="760">
      <div class="zw-form-2 market-config-head">
        <zw-form-item label="地区" required>
          <v-select v-model="configForm.province_code" :items="configProvinceOptions" />
        </zw-form-item>
        <zw-form-item label="修改原因" required>
          <v-text-field v-model="configForm.reason" placeholder="请填写修改原因" maxlength="100" />
        </zw-form-item>
      </div>
      <div class="zw-table-wrap">
        <v-table density="compact" class="market-config-table">
          <thead><tr><th>品类</th><th>品种</th><th>价格类型</th><th>默认单位</th><th class="text-center">启用</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in configForm.rows" :key="index">
              <td><v-select v-model="row.category_id" :items="configCategoryOptions" density="compact" hide-details class="market-config-select" /></td>
              <td>
                <v-select v-model="row.breed_id" :items="[{ title: '全部品种', value: 0 }, ...configBreedOptions]" density="compact" hide-details class="market-config-select" />
              </td>
              <td><v-select v-model="row.price_type" :items="configTypes" density="compact" hide-details class="market-config-select" /></td>
              <td><v-select v-model="row.default_unit" :items="configUnits" density="compact" hide-details class="market-config-select" /></td>
              <td class="text-center"><v-switch v-model="row.is_enabled" density="compact" hide-details /></td>
              <td><a class="zw-link danger" @click="configForm.rows.splice(index, 1)">删除</a></td>
            </tr>
          </tbody>
        </v-table>
      </div>
      <v-btn variant="text" color="primary" prepend-icon="add" class="mt-2" @click="addConfigRow">添加配置</v-btn>
      <div v-if="configError" class="market-form-error">{{ configError }}</div>
      <template #foot>
        <span class="zw-foot-note">只有超级管理员可以修改发布配置</span>
        <v-btn variant="outlined" @click="configOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="configSaving" @click="submitConfig">保存配置</v-btn>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.market-date { max-width: 168px; }
.market-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 16px; align-items: start; }
.market-side { display: flex; flex-direction: column; gap: 16px; }
.market-side-card { min-width: 0; }
.market-toolbar { padding: 16px; }
.market-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.market-sub--warn { color: var(--warning); }
.market-table :deep(tbody tr) { height: 56px; }
.market-price { font-size: 15px; font-weight: 500; }
.market-unit { font-size: 12px; color: var(--text-2); }
.market-change { display: inline-flex; align-items: center; gap: 2px; font-size: 13px; }
.market-change--warn { color: var(--warning); font-weight: 500; }
.market-unpublished { display: flex; flex-direction: column; }
.market-unpublished-row { display: flex; gap: 8px; align-items: flex-start; justify-content: space-between; padding: 9px 0; border-bottom: 1px dashed var(--line-1); font-size: 13px; }
.market-unpublished-row:last-child { border-bottom: 0; }
.market-unpublished-main { min-width: 0; }
.market-unpublished-main > div:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.market-hint { display: flex; gap: 6px; padding: 10px 12px; margin-top: 10px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 12px; line-height: 18px; color: var(--text-2); }
.market-progress-list { display: flex; flex-direction: column; gap: 12px; }
.market-progress-row { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.market-progress-name { width: 54px; flex-shrink: 0; }
.market-progress-bar { flex: 1; }
.market-progress-num { width: 48px; text-align: right; font-size: 12px; }
.market-form-head { margin-bottom: 18px; }
.market-entry-title { font-size: 14px; font-weight: 500; margin-bottom: 8px; }
.market-entry-title .zw-muted { font-weight: 400; font-size: 12px; margin-left: 6px; }
.market-entry-table { border: 1px solid var(--line-1); border-radius: var(--radius-sm); overflow: hidden; }
.market-entry-table table { width: 100%; border-collapse: collapse; }
.market-entry-table th { height: 38px; padding: 0 10px; background: var(--fill-1); font-size: 12px; font-weight: 600; text-align: left; color: var(--text-2); white-space: nowrap; }
.market-entry-table td { height: 48px; padding: 4px 8px; border-top: 1px solid var(--line-1); font-size: 13px; vertical-align: middle; }
.market-entry-table .text-end { text-align: right; }
.entry-row-over { background: var(--warning-container); }
.entry-row-readonly { background: var(--fill-1); color: var(--text-3); }
.market-price-input { width: 104px; }
.market-price-input :deep(input) { text-align: right; }
.market-unit-select { width: 104px; }
.market-over-warning { margin: 12px 0; }
.market-form-error { margin-top: 10px; font-size: 12px; color: var(--error); }
.market-review-head { font-size: 15px; font-weight: 600; margin-bottom: 12px; }
.market-review-grid { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 8px 12px; font-size: 13px; padding: 12px 14px; background: var(--fill-1); border-radius: var(--radius-sm); margin-bottom: 14px; }
.market-review-grid > span { color: var(--text-2); }
.market-warn-text { color: var(--warning); }
.market-correction-pending { margin-top: 8px; padding: 8px 10px; background: var(--warning-container); border-radius: var(--radius-sm); font-size: 12px; }
.market-correct-row { display: flex; align-items: center; gap: 10px; }
.market-old-price { color: var(--text-3); text-decoration: line-through; font-size: 16px; }
.market-correct-input { max-width: 160px; }
.market-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); }
.market-desc { margin-bottom: 16px; }
.market-config-head { margin-bottom: 14px; }
.market-config-table :deep(td) { padding: 4px; }
.market-config-select { min-width: 110px; }
.market-config-select :deep(.v-field) { font-size: 12px; }
</style>
