<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatDate, formatMoney, formatNumber, today as todayIso } from '@zmt/shared'
import { ApiError } from '@zmt/shared'
import {
  confirmStocktake,
  createFeed,
  createFeedInbound,
  createFeedLoss,
  createFeedOtherOut,
  fetchCategories,
  fetchFeedDetail,
  fetchFeedOptions,
  fetchFeedTypes,
  fetchRecentPartners,
  fetchStocktake,
  fetchStocktakeSheet,
  fetchStocktakes,
  newRequestKey,
  updateFeed,
  type BaseCategory,
  type FeedDetail,
  type FeedFlowGroup,
  type FeedOption,
  type FeedStockItem,
  type FeedTypeDict,
  type StockFlowItem,
  type Stocktake,
  type StocktakeSheet
} from '@/api/stock'
import StockDocumentDrawer from '@/views/stock/components/StockDocumentDrawer.vue'
import { useFeedStockStore } from '@/stores/stock/feed'
import { useFarmReload } from '@/composables/useFarmReload'
import { queryId, useRouteAction } from '@/composables/useRouteAction'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import {
  datetimeText,
  flowIcon,
  flowQty,
  moneyText,
  numberValue,
  packQtyText,
  priceText,
  qtyText,
  qtyUnit,
  qtyValue,
  remainingText
} from '@/views/stock/stock'

const store = useFeedStockStore()
const farm = useFarmStore()
const ui = useUiStore()
const router = useRouter()
const route = useRoute()

const isStaff = computed(() => farm.role === 'staff')
const desc = computed(() => `${farm.farmName || '当前养殖场'} · 剩余可用天数按近 7 天日均用量估算，不足一天的部分舍去；金额按移动加权平均单价计算`)

/* ---------------- 入库 ---------------- */
const inboundOpen = ref(false)
const inboundOptions = ref<FeedOption[]>([])
const inboundPartners = ref<string[]>([])
const inboundLoading = ref(false)
const inboundSubmitting = ref(false)
const inboundForm = reactive({
  feedId: 0,
  qty: 1000 as number | null,
  price: null as number | null,
  productionDate: '',
  shelfLifeDate: '',
  supplier: '',
  inboundDate: todayIso(),
  syncFinance: true
})
const inboundErrors = reactive<Record<string, string>>({})
const inboundFeed = computed(() => inboundOptions.value.find((item) => item.feed_id === inboundForm.feedId))
const inboundAmount = computed(() => numberValue(inboundForm.qty) * numberValue(inboundForm.price))
const inboundUnit = computed(() => inboundFeed.value?.stock?.unit_text || '千克')

async function openInbound(feedId = 0): Promise<void> {
  inboundForm.feedId = feedId
  inboundForm.qty = 1000
  inboundForm.price = null
  inboundForm.productionDate = ''
  inboundForm.shelfLifeDate = ''
  inboundForm.supplier = ''
  inboundForm.inboundDate = todayIso()
  inboundForm.syncFinance = true
  Object.keys(inboundErrors).forEach((key) => delete inboundErrors[key])
  inboundOpen.value = true
  inboundLoading.value = true
  try {
    const [options, partners] = await Promise.all([
      fetchFeedOptions('INBOUND'),
      fetchRecentPartners('SUPPLIER').catch(() => [])
    ])
    inboundOptions.value = options
    inboundPartners.value = partners
    if (!feedId) inboundForm.feedId = options[0]?.feed_id || 0
  } catch (error) {
    ui.toast((error as ApiError).message || '入库选项加载失败', 'error')
  } finally {
    inboundLoading.value = false
  }
}

function onInboundFeed(value: unknown): void {
  inboundForm.feedId = Number(value || 0)
  delete inboundErrors.feed_id
}

function validateInbound(): boolean {
  Object.keys(inboundErrors).forEach((key) => delete inboundErrors[key])
  if (!inboundForm.feedId) inboundErrors.feed_id = '请选择饲料'
  if (!numberValue(inboundForm.qty) || numberValue(inboundForm.qty) <= 0) inboundErrors.qty = '必须大于 0'
  if (!numberValue(inboundForm.price) || numberValue(inboundForm.price) <= 0) inboundErrors.price = '必须大于 0'
  if (!inboundForm.supplier.trim()) inboundErrors.supplier = '请填写供应商'
  if (!inboundForm.inboundDate) inboundErrors.inboundDate = '请选择入库日期'
  if (inboundForm.productionDate && inboundForm.productionDate > inboundForm.inboundDate) inboundErrors.productionDate = '生产日期不能晚于入库日期'
  if (inboundForm.productionDate && inboundForm.shelfLifeDate && inboundForm.shelfLifeDate <= inboundForm.productionDate) inboundErrors.shelfLifeDate = '必须晚于生产日期'
  if (inboundForm.shelfLifeDate && inboundForm.inboundDate && inboundForm.shelfLifeDate <= inboundForm.inboundDate) inboundErrors.shelfLifeDate = '已过保质期，不能入库'
  return Object.keys(inboundErrors).length === 0
}

async function submitInbound(): Promise<void> {
  if (!validateInbound() || inboundSubmitting.value) return
  inboundSubmitting.value = true
  try {
    await createFeedInbound(
      {
        feed_id: inboundForm.feedId,
        input_qty: numberValue(inboundForm.qty),
        input_unit: 'MEASURE',
        unit_price: numberValue(inboundForm.price),
        production_date: inboundForm.productionDate || undefined,
        shelf_life_date: inboundForm.shelfLifeDate || undefined,
        supplier_name: inboundForm.supplier.trim(),
        inbound_date: inboundForm.inboundDate,
        sync_finance: inboundForm.syncFinance
      },
      newRequestKey()
    )
    ui.toast(`已入库 ${formatNumber(inboundForm.qty)} ${inboundUnit.value}${inboundFeed.value?.name || ''}${inboundForm.syncFinance ? '，已同步记一笔支出' : ''}`)
    inboundOpen.value = false
    await store.refresh()
  } catch (error) {
    applyErrors(error as ApiError, inboundErrors)
    ui.toast((error as ApiError).message || '入库失败，请重试', 'error')
  } finally {
    inboundSubmitting.value = false
  }
}

/* ---------------- 盘点与报损 ---------------- */
const stocktakeOpen = ref(false)
const sheet = ref<StocktakeSheet | null>(null)
const sheetLoading = ref(false)
const actuals = reactive<Record<number, number | null>>({})
const lossFeedId = ref(0)
const lossSubmitting = ref(false)
const stocktakeConfirm = ref(false)
const stocktakeSubmitting = ref(false)
const lossForm = reactive({ qty: null as number | null, reason: 'MILDEW', remark: '' })
const lossErrors = reactive<Record<string, string>>({})

const sheetRows = computed(() =>
  (sheet.value?.items || []).map((item) => {
    const actual = actuals[item.feed_id]
    const diff = actual === null || actual === undefined ? null : Number(actual) - numberValue(item.book_qty)
    return {
      ...item,
      actual,
      diff,
      diffAmount: diff === null ? null : diff * numberValue(item.avg_price)
    }
  })
)
const recordedRows = computed(() => sheetRows.value.filter((row) => row.actual !== null && row.actual !== undefined))

async function openStocktake(): Promise<void> {
  stocktakeOpen.value = true
  stocktakeConfirm.value = false
  lossFeedId.value = 0
  await loadSheet()
}

async function loadSheet(): Promise<void> {
  sheetLoading.value = true
  try {
    sheet.value = await fetchStocktakeSheet()
    for (const item of sheet.value.items || []) {
      if (!(item.feed_id in actuals)) actuals[item.feed_id] = null
    }
  } catch (error) {
    ui.toast((error as ApiError).message || '盘点表加载失败', 'error')
  } finally {
    sheetLoading.value = false
  }
}

function openLoss(item: StocktakeSheet['items'][number]): void {
  lossFeedId.value = item.feed_id
  lossForm.qty = null
  lossForm.reason = 'MILDEW'
  lossForm.remark = ''
  Object.keys(lossErrors).forEach((key) => delete lossErrors[key])
}

async function submitLoss(item: StocktakeSheet['items'][number]): Promise<void> {
  Object.keys(lossErrors).forEach((key) => delete lossErrors[key])
  if (!numberValue(lossForm.qty) || numberValue(lossForm.qty) <= 0) lossErrors.qty = '必须大于 0'
  else if (numberValue(lossForm.qty) > numberValue(item.book_qty)) {
    lossErrors.qty = `超过当前结存 ${formatNumber(item.book_qty)} ${item.unit_text}，出库后库存不能为负数`
  }
  if (!lossForm.reason) lossErrors.reason = '请选择报损原因'
  if (lossForm.reason === 'OTHER' && !lossForm.remark.trim()) lossErrors.remark = '请写明报损原因'
  if (Object.keys(lossErrors).length || lossSubmitting.value) return
  lossSubmitting.value = true
  try {
    await createFeedLoss(
      {
        feed_id: item.feed_id,
        input_qty: numberValue(lossForm.qty),
        input_unit: 'MEASURE',
        reason: lossForm.reason,
        remark: lossForm.remark.trim() || undefined,
        stock_version: Number(item.stock_version || 0)
      },
      newRequestKey()
    )
    ui.toast(`已登记报损 ${formatNumber(lossForm.qty)} ${item.unit_text || '千克'}${item.feed_name || ''}`)
    lossFeedId.value = 0
    await loadSheet()
    await store.refresh()
  } catch (error) {
    applyErrors(error as ApiError, lossErrors)
    ui.toast((error as ApiError).message || '报损失败，请重试', 'error')
  } finally {
    lossSubmitting.value = false
  }
}

function prepareStocktake(): void {
  if (!recordedRows.value.length) {
    ui.toast('至少录入一种饲料的实盘数量', 'info')
    return
  }
  stocktakeConfirm.value = true
}

async function submitStocktake(): Promise<void> {
  if (stocktakeSubmitting.value) return
  stocktakeSubmitting.value = true
  try {
    const data = await confirmStocktake(
      {
        items: recordedRows.value.map((row) => ({
          feed_id: row.feed_id,
          book_qty: numberValue(row.book_qty),
          stock_version: Number(row.stock_version || 0),
          actual_qty: Number(row.actual)
        }))
      },
      newRequestKey()
    )
    ui.toast(`已确认盘点，结存已更新（盘盈 ${data.gain_count || 0} 条 · 盘亏 ${data.loss_count || 0} 条）`)
    stocktakeOpen.value = false
    stocktakeConfirm.value = false
    await store.refresh()
  } catch (error) {
    ui.toast((error as ApiError).message || '盘点失败，请重试', 'error')
  } finally {
    stocktakeSubmitting.value = false
  }
}

/* ---------------- 其他出库 ---------------- */
const outboundOpen = ref(false)
const outboundOptions = ref<FeedOption[]>([])
const outboundLoading = ref(false)
const outboundSubmitting = ref(false)
const outboundForm = reactive({
  feedId: 0,
  qty: null as number | null,
  destination: 'RETURN_SUPPLIER',
  remark: '',
  outDate: todayIso()
})
const outboundErrors = reactive<Record<string, string>>({})
const outboundFeed = computed(() => outboundOptions.value.find((item) => item.feed_id === outboundForm.feedId))
const outboundUnit = computed(() => outboundFeed.value?.stock?.unit_text || '千克')
const outboundAmount = computed(() => numberValue(outboundForm.qty) * numberValue(outboundFeed.value?.avg_price))
const destinationItems = [
  { title: '退回供应商', value: 'RETURN_SUPPLIER' },
  { title: '调往他场', value: 'TRANSFER_FARM' },
  { title: '出售', value: 'SALE' },
  { title: '其他', value: 'OTHER' }
]

function onOutboundFeed(value: unknown): void {
  outboundForm.feedId = Number(value || 0)
  delete outboundErrors.feed_id
}

function onFlowGroup(value: string | number): void {
  void store.setFlowsGroup(value as FeedFlowGroup)
}
const outboundNote = computed(() => {
  if (outboundForm.destination === 'TRANSFER_FARM') return '对方养殖场不会自动入库，需要对方自行登记入库'
  if (outboundForm.destination === 'OTHER') return '请在说明中写明出库原因'
  return '系统不会自动记账，收到退款或货款时请在收支记账中记一笔'
})

async function openOtherOut(feedId = 0): Promise<void> {
  outboundForm.feedId = feedId
  outboundForm.qty = null
  outboundForm.destination = 'RETURN_SUPPLIER'
  outboundForm.remark = ''
  outboundForm.outDate = todayIso()
  Object.keys(outboundErrors).forEach((key) => delete outboundErrors[key])
  outboundOpen.value = true
  outboundLoading.value = true
  try {
    outboundOptions.value = await fetchFeedOptions('OTHER_OUT')
    if (!feedId) {
      const first = outboundOptions.value.find((item) => item.selectable !== false)
      outboundForm.feedId = first?.feed_id || 0
    }
  } catch (error) {
    ui.toast((error as ApiError).message || '出库选项加载失败', 'error')
  } finally {
    outboundLoading.value = false
  }
}

function submitOtherOut(): void {
  Object.keys(outboundErrors).forEach((key) => delete outboundErrors[key])
  if (!outboundForm.feedId) outboundErrors.feed_id = '请选择饲料'
  const max = qtyValue(outboundFeed.value?.stock)
  if (!numberValue(outboundForm.qty) || numberValue(outboundForm.qty) <= 0) outboundErrors.qty = '必须大于 0'
  else if (numberValue(outboundForm.qty) > max) outboundErrors.qty = `超过当前结存 ${formatNumber(max)} ${outboundUnit.value}，出库后库存不能为负数`
  if (!outboundForm.destination) outboundErrors.destination = '请选择出库去向'
  if ((outboundForm.destination === 'TRANSFER_FARM' || outboundForm.destination === 'OTHER') && !outboundForm.remark.trim()) {
    outboundErrors.remark = outboundForm.destination === 'OTHER' ? '请写明出库原因' : '请写明调往哪个养殖场'
  }
  if (Object.keys(outboundErrors).length) return
  void submitOtherOutAsync()
}

async function submitOtherOutAsync(): Promise<void> {
  if (outboundSubmitting.value) return
  outboundSubmitting.value = true
  try {
    await createFeedOtherOut(
      {
        feed_id: outboundForm.feedId,
        input_qty: numberValue(outboundForm.qty),
        input_unit: 'MEASURE',
        destination: outboundForm.destination as 'RETURN_SUPPLIER' | 'TRANSFER_FARM' | 'SALE' | 'OTHER',
        remark: outboundForm.remark.trim() || undefined,
        out_date: outboundForm.outDate,
        stock_version: Number(outboundFeed.value?.stock_version || 0)
      },
      newRequestKey()
    )
    ui.toast(`已登记其他出库 ${formatNumber(outboundForm.qty)} ${outboundUnit.value}${outboundFeed.value?.name || ''}`)
    outboundOpen.value = false
    await store.refresh()
  } catch (error) {
    applyErrors(error as ApiError, outboundErrors)
    ui.toast((error as ApiError).message || '出库失败，请重试', 'error')
  } finally {
    outboundSubmitting.value = false
  }
}

/* ---------------- 饲料档案 ---------------- */
const archiveOpen = ref(false)
const archiveLoading = ref(false)
const archiveSubmitting = ref(false)
const archiveMode = ref<'create' | 'edit'>('create')
const archiveForm = reactive({
  feedId: 0,
  name: '',
  type: '',
  spec: '',
  packUnit: '',
  packSize: null as number | null,
  categoryId: null as number | null,
  minStock: null as number | null,
  warnDays: 7 as number | null,
  editVersion: 0
})
const archiveErrors = reactive<Record<string, string>>({})
const archiveDetail = ref<FeedDetail | null>(null)
const feedTypes = ref<FeedTypeDict[]>([])
const categories = ref<BaseCategory[]>([])

async function openArchive(feedId = 0): Promise<void> {
  archiveMode.value = feedId ? 'edit' : 'create'
  Object.keys(archiveErrors).forEach((key) => delete archiveErrors[key])
  archiveLoading.value = true
  archiveOpen.value = true
  try {
    if (!feedTypes.value.length) feedTypes.value = await fetchFeedTypes()
    if (!categories.value.length) categories.value = await fetchCategories()
    if (feedId) {
      const detail = await fetchFeedDetail(feedId)
      archiveDetail.value = detail
      archiveForm.feedId = detail.feed_id
      archiveForm.name = detail.name || ''
      archiveForm.type = detail.feed_type_code || ''
      archiveForm.spec = detail.spec || ''
      archiveForm.packUnit = detail.pack_unit || ''
      archiveForm.packSize = detail.pack_size ?? null
      archiveForm.categoryId = detail.category_id ?? null
      archiveForm.minStock = detail.min_stock ?? null
      archiveForm.warnDays = detail.warn_days ?? 7
      archiveForm.editVersion = detail.edit_version || 0
    } else {
      archiveDetail.value = null
      archiveForm.feedId = 0
      archiveForm.name = ''
      archiveForm.type = ''
      archiveForm.spec = ''
      archiveForm.packUnit = ''
      archiveForm.packSize = null
      archiveForm.categoryId = null
      archiveForm.minStock = null
      archiveForm.warnDays = 7
      archiveForm.editVersion = 0
    }
  } catch (error) {
    ui.toast((error as ApiError).message || '饲料档案加载失败', 'error')
  } finally {
    archiveLoading.value = false
  }
}

async function submitArchive(): Promise<void> {
  Object.keys(archiveErrors).forEach((key) => delete archiveErrors[key])
  if (!archiveForm.name.trim()) archiveErrors.name = '请填写名称'
  if (!archiveForm.type) archiveErrors.type = '请选择类型'
  if (numberValue(archiveForm.minStock) < 0) archiveErrors.minStock = '不能小于 0'
  if (archiveForm.warnDays !== null && (!Number.isInteger(Number(archiveForm.warnDays)) || Number(archiveForm.warnDays) < 1)) {
    archiveErrors.warnDays = '必须是大于 0 的整数'
  }
  if (Object.keys(archiveErrors).length || archiveSubmitting.value) return
  archiveSubmitting.value = true
  try {
    const payload = {
      name: archiveForm.name.trim(),
      feed_type_code: archiveForm.type,
      spec: archiveForm.spec.trim() || undefined,
      pack_unit: archiveForm.packUnit.trim() || undefined,
      pack_size: archiveForm.packSize ?? undefined,
      category_id: archiveForm.categoryId ?? undefined,
      min_stock: archiveForm.minStock ?? undefined,
      warn_days: archiveForm.warnDays ?? undefined,
      edit_version: archiveMode.value === 'edit' ? archiveForm.editVersion : undefined
    }
    if (archiveMode.value === 'edit') await updateFeed(archiveForm.feedId, payload)
    else await createFeed(payload)
    ui.toast(archiveMode.value === 'edit' ? `已保存${archiveForm.name}的档案` : `已新增饲料：${archiveForm.name}`)
    archiveOpen.value = false
    await store.refresh()
  } catch (error) {
    applyErrors(error as ApiError, archiveErrors)
    ui.toast((error as ApiError).message || '保存失败，请重试', 'error')
  } finally {
    archiveSubmitting.value = false
  }
}

/* ---------------- 流水 ---------------- */
const flowGroups: Array<{ label: string; value: FeedFlowGroup }> = [
  { label: '全部', value: 'ALL' },
  { label: '入库', value: 'INBOUND' },
  { label: '饲喂领用', value: 'FEED_USE' },
  { label: '其他出库', value: 'OTHER_OUT' },
  { label: '报损', value: 'LOSS' },
  { label: '盘盈盘亏', value: 'STOCKTAKE' }
]

async function openFeedFlows(feedId = 0, name = ''): Promise<void> {
  await store.openFlows(feedId, name)
}

/* ---------------- 公共 ---------------- */
function applyErrors(error: ApiError, target: Record<string, string>): void {
  for (const field of error.fieldErrors || []) {
    target[field.field] = field.message
  }
}

function bannerText(): string {
  const banner = store.banner
  if (!banner) return ''
  const unit = banner.stock?.unit_text || '千克'
  if (banner.kind === 'MULTI') return `${banner.feed_names?.length || 0} 种饲料库存不足：${(banner.feed_names || []).join('、')}。`
  if (banner.kind === 'MIN') {
    return `${banner.feed_name || ''}库存不足，结存 ${formatNumber(banner.stock?.value ?? 0)} ${unit}，低于库存下限 ${formatNumber(banner.min_stock ?? 0)} ${unit}。`
  }
  return `${banner.feed_name || ''}库存不足，剩余可用 ${banner.remaining_days ?? 0} 天，低于预警天数 ${banner.warn_days ?? 0} 天。`
}

function bannerPreselect(): number {
  return Number(store.banner?.preselect_feed_id || store.banner?.feed_id || 0)
}

function inboundForBanner(): void {
  void openInbound(bannerPreselect())
}

function feedTypeText(item: FeedStockItem): string {
  return [item.feed_type_text, item.spec, item.category_text].filter(Boolean).join(' · ')
}

function lowStockFoot(): string {
  const first = store.warningFeeds[0]
  if (!first) return '所有饲料库存正常'
  return `${first.name} · 剩余可用 ${remainingText(first)}`
}

function flowTime(value?: string): string {
  if (!value) return '—'
  const today = todayIso()
  if (value.slice(0, 10) === today) return `今天 ${value.slice(11, 16)}`
  return `${value.slice(5, 10)} ${value.slice(11, 16)}`
}

/* ---------------- 单据详情（入库单 / 出库单 / 报损单，可当天修改、作废） ---------------- */
const documentOpen = ref(false)
const selectedFlow = ref<StockFlowItem | null>(null)

function openFlowDoc(flow: StockFlowItem): void {
  selectedFlow.value = flow
  documentOpen.value = true
}

function openInboundDoc(inboundId: number): void {
  openFlowDoc({ flow_id: 0, biz_type: 'INBOUND', biz_id: inboundId, material_type: 'FEED' })
}

async function onDocumentChanged(): Promise<void> {
  await store.refresh()
  if (store.flowsOpen) await store.loadFlows()
}

/* ---------------- 盘点记录 ---------------- */
const stocktakesOpen = ref(false)
const stocktakesLoading = ref(false)
const stocktakes = ref<Stocktake[]>([])
const stocktakesTotal = ref(0)
const stocktakesPage = ref(1)
const stocktakesSize = ref(10)
const stocktakeExpanded = ref(0)
const stocktakeDetails = reactive<Record<number, Stocktake>>({})

async function openStocktakes(): Promise<void> {
  stocktakesOpen.value = true
  stocktakesPage.value = 1
  stocktakeExpanded.value = 0
  await loadStocktakes()
}

async function loadStocktakes(): Promise<void> {
  stocktakesLoading.value = true
  try {
    const page = await fetchStocktakes({ page: stocktakesPage.value, size: stocktakesSize.value })
    stocktakes.value = page.list || []
    stocktakesTotal.value = Number(page.total || 0)
  } catch (error) {
    ui.toast((error as ApiError).message || '盘点记录加载失败', 'error')
  } finally {
    stocktakesLoading.value = false
  }
}

function setStocktakesPage(page: number): void {
  stocktakesPage.value = page
  void loadStocktakes()
}

function setStocktakesSize(size: number): void {
  stocktakesSize.value = size
  stocktakesPage.value = 1
  void loadStocktakes()
}

async function toggleStocktake(item: Stocktake): Promise<void> {
  if (stocktakeExpanded.value === item.stocktake_id) {
    stocktakeExpanded.value = 0
    return
  }
  stocktakeExpanded.value = item.stocktake_id
  if (item.items?.length || stocktakeDetails[item.stocktake_id]) return
  try {
    stocktakeDetails[item.stocktake_id] = await fetchStocktake(item.stocktake_id)
  } catch (error) {
    ui.toast((error as ApiError).message || '盘点详情加载失败', 'error')
  }
}

function stocktakeItems(item: Stocktake): NonNullable<Stocktake['items']> {
  return item.items?.length ? item.items : stocktakeDetails[item.stocktake_id]?.items || []
}

/* ---------------- 路由参数：去入库、定位入库单 ---------------- */
const routeAction = useRouteAction(async (action, query) => {
  if (action === 'inbound' || action === 'create') await openInbound(queryId(query, 'feedId', 'feed_id'))
  else if (action === 'stocktake') await openStocktake()
})

async function applyRoute(): Promise<void> {
  if (isStaff.value) return
  const inboundId = queryId(route.query, 'inboundId', 'inbound_id')
  if (inboundId && !route.query.action) openInboundDoc(inboundId)
  await routeAction.start()
}

watch(() => route.query.inboundId, (value) => {
  const id = Number(value || 0)
  if (id && !isStaff.value) openInboundDoc(id)
})

onMounted(async () => {
  if (!isStaff.value) await store.load()
  await applyRoute()
})
useFarmReload(async () => {
  if (!isStaff.value) await store.load()
})
</script>

<template>
  <zw-shell side="user" active="WU-M01" :crumbs="['物资库存', '饲料库存']">
    <zw-page-head title="饲料库存" :desc="desc">
      <template #actions>
        <template v-if="!isStaff">
          <v-menu location="bottom end">
            <template #activator="{ props: menuProps }">
              <v-btn v-bind="menuProps" variant="outlined" prepend-icon="more_horiz">更多</v-btn>
            </template>
            <v-list density="compact" class="stock-menu">
              <v-list-item prepend-icon="add" @click="openArchive()"><v-list-item-title>新增饲料</v-list-item-title></v-list-item>
              <v-list-item v-if="store.canOtherOut" prepend-icon="output" @click="openOtherOut()"><v-list-item-title>其他出库</v-list-item-title></v-list-item>
              <v-list-item prepend-icon="receipt_long" @click="openFeedFlows()"><v-list-item-title>全部出入库流水</v-list-item-title></v-list-item>
              <v-list-item prepend-icon="history" @click="openStocktakes()"><v-list-item-title>盘点记录</v-list-item-title></v-list-item>
            </v-list>
          </v-menu>
          <v-btn variant="outlined" prepend-icon="fact_check" @click="openStocktake">盘点与报损</v-btn>
          <v-btn color="primary" prepend-icon="add" @click="openInbound()">入库</v-btn>
        </template>
      </template>
    </zw-page-head>

    <zw-card v-if="isStaff">
      <zw-empty icon="lock" tone="warning" title="你没有物资库存的查看权限" desc="入库、盘点和报废由所有者或场长处理。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div v-if="store.status === 'loading'" class="stock-loading">
        <div class="zw-grid-4 mb-4"><v-skeleton-loader v-for="item in 4" :key="item" type="article" /></div>
        <zw-card><v-skeleton-loader type="table-row@4" /></zw-card>
      </div>

      <zw-card v-else-if="store.status === 'error'">
        <zw-empty icon="cloud_off" tone="error" title="库存加载失败" :desc="store.error">
          <v-btn color="primary" variant="outlined" @click="store.load()">重新加载</v-btn>
        </zw-empty>
      </zw-card>

      <template v-else>
        <zw-notice v-if="store.banner" type="warning" class="mb-4">
          <strong>{{ bannerText() }}</strong>
          <template #action>
            <a class="stock-link" @click="inboundForBanner">去入库</a>
          </template>
        </zw-notice>

        <div class="zw-grid-4 mb-4">
          <zw-stat label="饲料" :value="formatNumber(store.overview?.feed_count || store.list.length)" unit="种" icon="inventory_2" :foot="store.feedNames || '还没有饲料档案'" />
          <zw-stat label="库存金额" :value="formatMoney(store.overview?.stock_amount)" unit="元" icon="payments" foot="按移动加权平均单价" />
          <zw-stat label="库存预警" :value="formatNumber(store.overview?.warn_count || 0)" unit="项" icon="warning" tone="warning" :foot="lowStockFoot()" />
          <zw-stat label="近 7 天领用" :value="formatNumber(store.overview?.usage_7d_total || 0)" unit="千克" icon="rice_bowl" :foot="store.usageFoot || '近 7 天还没有领用'" />
        </div>

        <zw-card title="结存" sub="预警在前，其余按名称排列" flush>
          <div v-if="store.status === 'empty'">
            <zw-empty icon="inventory_2" title="还没有饲料档案" desc="先新增一种饲料，再登记入库。">
              <v-btn color="primary" @click="openArchive()">新增饲料</v-btn>
            </zw-empty>
          </div>
          <template v-else>
            <v-table class="stock-table" hover>
              <thead>
                <tr>
                  <th>饲料</th>
                  <th class="text-end" style="width: 110px">结存</th>
                  <th class="text-end" style="width: 96px">剩余可用</th>
                  <th class="text-end" style="width: 116px">近 7 天领用</th>
                  <th class="text-end" style="width: 140px">均价</th>
                  <th class="text-end" style="width: 104px">库存金额</th>
                  <th style="width: 150px">库存下限 · 预警天数</th>
                  <th style="width: 104px">状态</th>
                  <th class="text-end" style="width: 148px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in store.list" :key="item.feed_id">
                  <td>
                    <div class="stock-material">
                      <span class="zw-ico-tile" :class="item.low_stock ? 'zw-ico-tile--warning' : 'zw-ico-tile--primary'">
                        <zw-icon name="inventory_2" :size="18" />
                      </span>
                      <div>
                        <div class="zw-cell-main">{{ item.name }}</div>
                        <div class="zw-cell-sub">{{ feedTypeText(item) }}</div>
                      </div>
                    </div>
                  </td>
                  <td class="text-end num">{{ qtyText(item.stock) }}</td>
                  <td class="text-end num" :class="{ 'stock-warn-text': item.low_stock }">{{ remainingText(item) }}</td>
                  <td class="text-end num">{{ item.usage_qty === null || item.usage_qty === undefined ? '—' : `${formatNumber(item.usage_qty)} 千克` }}</td>
                  <td class="text-end num">{{ priceText(item.avg_price) }}</td>
                  <td class="text-end num">{{ moneyText(item.stock_amount) }}</td>
                  <td class="num">
                    <template v-if="item.min_stock !== null && item.min_stock !== undefined">{{ formatNumber(item.min_stock) }} {{ qtyUnit(item.stock) }} · </template>
                    {{ formatNumber(item.warn_days) }} 天
                  </td>
                  <td>
                    <zw-tag v-if="item.low_stock" type="warning" icon="warning" size="small">库存不足</zw-tag>
                    <span v-else class="zw-faint">正常</span>
                  </td>
                  <td class="text-end">
                    <span class="zw-cell-actions">
                      <a @click="openInbound(item.feed_id)">入库</a>
                      <a @click="openFeedFlows(item.feed_id, item.name)">流水</a>
                      <a @click="openArchive(item.feed_id)">档案</a>
                    </span>
                  </td>
                </tr>
              </tbody>
            </v-table>
            <div class="zw-table-foot stock-table-foot">
              <span class="zw-faint body-s">剩余可用 = 结存 ÷ 近 7 天日均用量，不足一天舍去</span>
              <zw-pager :total="store.total" :page="store.page" :size="store.size" @update:page="store.setPage" @update:size="store.setSize" />
            </div>
          </template>
        </zw-card>

        <zw-card title="最近出入库" :sub="`最近 ${store.overview?.recent_flows?.length || 0} 条`" flush class="mt-4">
          <template #extra>
            <a class="stock-link" @click="openFeedFlows()">全部流水</a>
          </template>
          <zw-empty v-if="!store.overview?.recent_flows?.length" icon="receipt_long" title="还没有出入库记录" desc="登记入库后会显示在这里。" />
          <v-table v-else class="stock-table stock-table--compact" hover>
            <thead>
              <tr>
                <th style="width: 142px">时间</th>
                <th style="width: 150px">类型</th>
                <th style="width: 150px">饲料</th>
                <th>关联</th>
                <th style="width: 104px">登记人</th>
                <th class="text-end" style="width: 132px">数量</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="flow in store.overview?.recent_flows || []" :key="flow.flow_id" class="stock-flow-row" title="查看单据" @click="openFlowDoc(flow)">
                <td class="num">{{ flowTime(flow.occur_at) }}</td>
                <td>
                  <div class="stock-flow-type">
                    <span class="zw-ico-tile stock-ico-sm"><zw-icon :name="flowIcon(flow.flow_type)" :size="16" /></span>
                    {{ flow.flow_type_text }}
                  </div>
                </td>
                <td>{{ flow.material_name }}</td>
                <td class="zw-muted">{{ flow.related_desc || flow.pen_name || '—' }}</td>
                <td>{{ flow.operator_name || '—' }}</td>
                <td class="text-end num" :class="flow.direction === 'IN' ? 'stock-in-text' : ''">{{ flowQty(flow) }}</td>
              </tr>
            </tbody>
          </v-table>
        </zw-card>
      </template>
    </template>

    <!-- 入库抽屉 -->
    <zw-drawer v-model="inboundOpen" title="饲料入库" sub="入库金额按数量 × 单价自动计算" width="560">
      <div v-if="inboundLoading" class="stock-drawer-loading"><v-skeleton-loader type="article" /></div>
      <template v-else>
        <zw-form-item label="饲料" required :error="inboundErrors.feed_id" :hint="inboundFeed ? `当前结存 ${qtyText(inboundFeed.stock)}，剩余可用 ${remainingText(inboundFeed)}` : ''">
          <v-select :model-value="inboundForm.feedId" :items="inboundOptions.map((item) => ({ title: `${item.name}${item.low_stock ? ' · 库存不足' : ''}`, value: item.feed_id }))" :error="!!inboundErrors.feed_id" @update:model-value="onInboundFeed" />
        </zw-form-item>
        <div class="zw-form-2 mt-3">
          <zw-form-item label="数量" required :error="inboundErrors.qty" hint="必须大于 0">
            <v-text-field v-model.number="inboundForm.qty" type="number" min="0" :suffix="inboundUnit" :error="!!inboundErrors.qty" />
          </zw-form-item>
          <zw-form-item label="单价" required :error="inboundErrors.price" hint="必须大于 0，最多两位小数">
            <v-text-field v-model.number="inboundForm.price" type="number" min="0" :suffix="`元 / ${inboundUnit}`" :error="!!inboundErrors.price" />
          </zw-form-item>
        </div>
        <div class="stock-amount">
          <span>入库金额</span>
          <strong class="num">{{ inboundForm.qty && inboundForm.price ? moneyText(inboundAmount) : '—' }}</strong>
        </div>
        <div class="zw-form-2 mt-4">
          <zw-form-item label="生产日期" :error="inboundErrors.productionDate">
            <v-text-field v-model="inboundForm.productionDate" type="date" :error="!!inboundErrors.productionDate" />
          </zw-form-item>
          <zw-form-item label="保质期至" :error="inboundErrors.shelfLifeDate" hint="必须晚于生产日期">
            <v-text-field v-model="inboundForm.shelfLifeDate" type="date" :error="!!inboundErrors.shelfLifeDate" />
          </zw-form-item>
        </div>
        <zw-form-item label="供应商" required :error="inboundErrors.supplier" hint="可从最近使用的供应商中选择，也可以直接输入" class="mt-4">
          <v-combobox v-model="inboundForm.supplier" :items="inboundPartners" :error="!!inboundErrors.supplier" />
        </zw-form-item>
        <zw-form-item label="入库日期" required :error="inboundErrors.inboundDate" class="mt-4">
          <v-text-field v-model="inboundForm.inboundDate" type="date" :error="!!inboundErrors.inboundDate" />
        </zw-form-item>
        <label class="stock-sync">
          <v-checkbox v-model="inboundForm.syncFinance" hide-details />
          <div>
            <div class="stock-sync-title">同步记账</div>
            <div class="stock-sync-sub">确认后在收支流水中生成一条支出：饲料采购 {{ inboundForm.qty && inboundForm.price ? formatMoney(inboundAmount) : '—' }} 元，来源标为“饲料入库单”。入库单作废时，这条支出同步作废。</div>
          </div>
        </label>
      </template>
      <template #foot>
        <span class="stock-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="inboundOpen = false">取消</v-btn>
        <v-btn color="primary" prepend-icon="check" :loading="inboundSubmitting" @click="submitInbound">确认入库</v-btn>
      </template>
    </zw-drawer>

    <!-- 盘点与报损 -->
    <zw-drawer v-model="stocktakeOpen" title="盘点与报损" :sub="sheet ? `盘点日期 ${formatDate(sheet.stocktake_date)} · ${sheet.operator?.name || ''}（盘点日期和盘点人不可修改）` : ''" width="760">
      <div v-if="sheetLoading" class="stock-drawer-loading"><v-skeleton-loader type="article@4" /></div>
      <template v-else>
        <div v-for="item in sheetRows" :key="item.feed_id" class="stocktake-block">
          <div class="stocktake-head">
            <span class="zw-ico-tile" :class="item.low_stock ? 'zw-ico-tile--warning' : 'zw-ico-tile--primary'"><zw-icon name="inventory_2" :size="18" /></span>
            <div class="stocktake-head-main">
              <div class="stocktake-name">{{ item.feed_name }}</div>
              <div class="zw-cell-sub">账面结存 {{ qtyText({ value: item.book_qty, unit_text: item.unit_text }) }} · {{ priceText(item.avg_price, item.unit_text || '千克') }}</div>
            </div>
            <v-btn variant="text" color="error" size="small" prepend-icon="delete_sweep" @click="openLoss(item)">登记报损</v-btn>
          </div>
          <div class="stocktake-body">
            <zw-form-item label="实盘数量" :hint="`账面 ${formatNumber(item.book_qty)} ${item.unit_text || '千克'}`">
              <v-text-field v-model.number="actuals[item.feed_id]" type="number" min="0" :suffix="item.unit_text || '千克'" />
            </zw-form-item>
            <div v-if="item.diff !== null" class="stocktake-result" :class="{ 'stocktake-result--loss': item.diff < 0 }">
              <zw-icon :name="item.diff < 0 ? 'trending_down' : item.diff > 0 ? 'trending_up' : 'check'" :size="18" />
              <span>{{ item.diff === 0 ? '账实相符' : `${item.diff < 0 ? '盘亏' : '盘盈'} ${formatNumber(Math.abs(item.diff))} ${item.unit_text || '千克'}` }}</span>
              <span v-if="item.diff !== 0" class="stocktake-amount">按均价折合 {{ moneyText(Math.abs(item.diffAmount || 0)) }}</span>
            </div>
          </div>
          <div v-if="lossFeedId === item.feed_id" class="stock-loss-form">
            <div class="stock-loss-title">登记报损 · {{ item.feed_name }}<span>当前结存 {{ formatNumber(item.book_qty) }} {{ item.unit_text || '千克' }} · 按今天登记</span></div>
            <div class="zw-form-2">
              <zw-form-item label="报损数量" required :error="lossErrors.qty">
                <v-text-field v-model.number="lossForm.qty" type="number" min="0" :suffix="item.unit_text || '千克'" :error="!!lossErrors.qty" />
              </zw-form-item>
              <zw-form-item label="报损原因" required :error="lossErrors.reason">
                <v-select v-model="lossForm.reason" :items="[{ title: '霉变', value: 'MILDEW' }, { title: '受潮', value: 'DAMP' }, { title: '鼠害', value: 'RODENT' }, { title: '过期', value: 'EXPIRED' }, { title: '其他', value: 'OTHER' }]" :error="!!lossErrors.reason" />
              </zw-form-item>
            </div>
            <zw-form-item label="说明" :error="lossErrors.remark" :optional="lossForm.reason !== 'OTHER'">
              <v-text-field v-model="lossForm.remark" maxlength="100" :error="!!lossErrors.remark" />
            </zw-form-item>
            <div class="stock-loss-actions">
              <v-btn variant="text" @click="lossFeedId = 0">取消</v-btn>
              <v-btn color="primary" size="small" :loading="lossSubmitting" @click="submitLoss(item)">登记报损</v-btn>
            </div>
          </div>
        </div>
        <zw-notice type="info" class="mt-4">确认后按实盘数量更新结存，并自动生成盘盈、盘亏记录。已确认的盘点结果需要提交修改申请才能更改。</zw-notice>
      </template>
      <template #foot>
        <template v-if="stocktakeConfirm">
          <span class="stock-confirm-text">确认这次盘点？将按实盘数量更新 {{ recordedRows.length }} 种饲料的结存，生成盘盈、盘亏记录。确认后如需更改，要提交修改申请。</span>
          <v-btn variant="outlined" @click="stocktakeConfirm = false">取消</v-btn>
          <v-btn color="primary" :loading="stocktakeSubmitting" @click="submitStocktake">确认</v-btn>
        </template>
        <template v-else>
          <span class="stock-foot-note">只提交已录入实盘数量的饲料</span>
          <v-btn variant="outlined" @click="stocktakeOpen = false">取消</v-btn>
          <v-btn color="primary" prepend-icon="check" @click="prepareStocktake">确认盘点</v-btn>
        </template>
      </template>
    </zw-drawer>

    <!-- 其他出库 -->
    <zw-drawer v-model="outboundOpen" title="饲料其他出库" sub="饲喂领用和报损以外的出库，例如退回供应商、调往他场、出售" width="560">
      <div v-if="outboundLoading" class="stock-drawer-loading"><v-skeleton-loader type="article" /></div>
      <template v-else>
        <zw-form-item label="饲料" required :error="outboundErrors.feed_id" :hint="outboundFeed ? `当前结存 ${qtyText(outboundFeed.stock)} · 均价每${outboundUnit} ${formatMoney(outboundFeed.avg_price)} 元` : '结存为 0 的饲料不能出库'">
          <v-select
            :model-value="outboundForm.feedId"
            :items="outboundOptions.map((item) => ({ title: item.name, value: item.feed_id, props: { disabled: item.selectable === false } }))"
            :error="!!outboundErrors.feed_id"
            @update:model-value="onOutboundFeed"
          />
        </zw-form-item>
        <div class="zw-form-2 mt-3">
          <zw-form-item label="出库数量" required :error="outboundErrors.qty" :hint="outboundFeed ? `最多 ${formatNumber(qtyValue(outboundFeed.stock))} ${outboundUnit}，出库后库存不能为负数` : ''">
            <v-text-field v-model.number="outboundForm.qty" type="number" min="0" :suffix="outboundUnit" :error="!!outboundErrors.qty" />
          </zw-form-item>
          <zw-form-item label="出库日期" required>
            <v-text-field v-model="outboundForm.outDate" type="date" />
          </zw-form-item>
        </div>
        <zw-form-item label="出库去向" required :error="outboundErrors.destination" class="mt-4">
          <v-select v-model="outboundForm.destination" :items="destinationItems" :error="!!outboundErrors.destination" />
        </zw-form-item>
        <div class="stock-dest-note">{{ outboundNote }}</div>
        <zw-form-item :label="outboundForm.destination === 'TRANSFER_FARM' || outboundForm.destination === 'OTHER' ? '说明' : '说明（选填）'" :error="outboundErrors.remark" class="mt-4">
          <v-text-field v-model="outboundForm.remark" maxlength="100" :error="!!outboundErrors.remark" />
        </zw-form-item>
        <div class="stock-amount mt-4">
          <span>按均价折合</span>
          <strong class="num">{{ outboundForm.qty ? moneyText(outboundAmount) : '—' }}</strong>
        </div>
        <zw-notice type="info" class="mt-4">其他出库不计入批次成本，也不计入饲料用量。登记当天可以修改或作废，之后修改需要提交修改申请。</zw-notice>
      </template>
      <template #foot>
        <span class="stock-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="outboundOpen = false">取消</v-btn>
        <v-btn color="primary" prepend-icon="output" :loading="outboundSubmitting" @click="submitOtherOut">确认出库</v-btn>
      </template>
    </zw-drawer>

    <!-- 饲料档案 -->
    <zw-drawer v-model="archiveOpen" :title="archiveMode === 'edit' ? '饲料档案' : '新增饲料'" sub="修改后系统保留修改记录" width="560">
      <div v-if="archiveLoading" class="stock-drawer-loading"><v-skeleton-loader type="article" /></div>
      <template v-else>
        <div v-if="archiveDetail" class="stock-readonly-card">
          <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="inventory_2" :size="18" /></span>
          <div>
            <div class="zw-cell-main">{{ archiveDetail.name }}</div>
            <div class="zw-cell-sub">{{ archiveDetail.feed_type_text }} · {{ archiveDetail.unit_text }} · 上次修改 {{ archiveDetail.updated_at ? datetimeText(archiveDetail.updated_at) : '—' }} · {{ archiveDetail.updated_by_name || '' }}</div>
          </div>
        </div>
        <div class="zw-form-2">
          <zw-form-item label="名称" required :error="archiveErrors.name" hint="最多 20 字">
            <v-text-field v-model="archiveForm.name" maxlength="20" :error="!!archiveErrors.name" />
          </zw-form-item>
          <zw-form-item label="类型" required :error="archiveErrors.type">
            <v-select v-model="archiveForm.type" :items="feedTypes.map((item) => ({ title: item.name, value: item.code }))" :error="!!archiveErrors.type" />
          </zw-form-item>
        </div>
        <div class="zw-form-2 mt-3">
          <zw-form-item label="规格" hint="最多 20 字">
            <v-text-field v-model="archiveForm.spec" maxlength="20" />
          </zw-form-item>
          <zw-form-item label="适用品类">
            <v-select v-model="archiveForm.categoryId" :items="categories.map((item) => ({ title: item.name, value: item.id }))" clearable />
          </zw-form-item>
        </div>
        <div class="zw-form-2 mt-3">
          <zw-form-item label="包装单位" hint="例如 袋、桶">
            <v-text-field v-model="archiveForm.packUnit" maxlength="4" :disabled="archiveDetail?.unit_locked" />
          </zw-form-item>
          <zw-form-item label="每包数量" hint="选填，按包装换算时使用">
            <v-text-field v-model.number="archiveForm.packSize" type="number" min="0" />
          </zw-form-item>
        </div>
        <div class="zw-form-2 mt-3">
          <zw-form-item label="库存下限" :error="archiveErrors.minStock" hint="低于下限时提醒">
            <v-text-field v-model.number="archiveForm.minStock" type="number" min="0" suffix="千克" :error="!!archiveErrors.minStock" />
          </zw-form-item>
          <zw-form-item label="预警天数" :error="archiveErrors.warnDays" hint="默认 7 天，可以修改">
            <v-text-field v-model.number="archiveForm.warnDays" type="number" min="1" max="365" suffix="天" :error="!!archiveErrors.warnDays" />
          </zw-form-item>
        </div>
        <zw-notice type="info" class="mt-4">库存低于下限，或剩余可用天数低于预警天数时，提醒所有者和场长一次，补货后解除。</zw-notice>
      </template>
      <template #foot>
        <v-btn variant="outlined" @click="archiveOpen = false">取消</v-btn>
        <v-btn color="primary" prepend-icon="check" :loading="archiveSubmitting" @click="submitArchive">保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 出入库流水 -->
    <zw-drawer v-model="store.flowsOpen" :title="store.flowsFeedId ? `${store.flowsFeedName} · 流水` : '出入库流水'" :sub="`共 ${store.flowsTotal} 条`" width="760">
      <div class="stock-flow-toolbar">
        <zw-seg :model-value="store.flowsGroup" :items="flowGroups" size="small" @update:model-value="onFlowGroup" />
      </div>
      <div v-if="store.flowsLoading" class="stock-drawer-loading"><v-skeleton-loader type="table-row@6" /></div>
      <zw-empty v-else-if="!store.flows.length" icon="receipt_long" title="还没有出入库记录" desc="登记入库后会显示在这里。" />
      <template v-else>
        <v-table class="stock-table stock-table--compact" hover>
          <thead>
            <tr>
              <th style="width: 142px">时间</th>
              <th style="width: 140px">类型</th>
              <th>关联</th>
              <th style="width: 100px">经办人</th>
              <th class="text-end" style="width: 120px">数量</th>
              <th class="text-end" style="width: 96px">结存</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="flow in store.flows" :key="flow.flow_id" class="stock-flow-row" title="查看单据" @click="openFlowDoc(flow)">
              <td class="num">{{ datetimeText(flow.occur_at) }}</td>
              <td>
                <div class="stock-flow-type">
                  <span class="zw-ico-tile stock-ico-sm"><zw-icon :name="flowIcon(flow.flow_type)" :size="16" /></span>
                  {{ flow.flow_type_text }}
                </div>
              </td>
              <td class="zw-muted">{{ flow.related_desc || flow.pen_name || '—' }}</td>
              <td>{{ flow.operator_name || '—' }}</td>
              <td class="text-end num" :class="flow.direction === 'IN' ? 'stock-in-text' : ''">{{ flowQty(flow) }}</td>
              <td class="text-end num zw-muted">{{ flow.stock_qty_after === null || flow.stock_qty_after === undefined ? '—' : `${formatNumber(flow.stock_qty_after)} ${flow.qty?.unit_text || ''}` }}</td>
            </tr>
          </tbody>
        </v-table>
        <div class="zw-table-foot stock-table-foot">
          <span class="zw-faint body-s">按时间倒序 · 点击一行查看单据，登记当天可以修改或作废</span>
          <zw-pager :total="store.flowsTotal" :page="store.flowsPage" :size="store.flowsSize" @update:page="store.setFlowsPage" @update:size="store.setFlowsSize" />
        </div>
      </template>
      <template #foot>
        <v-btn variant="outlined" @click="store.flowsOpen = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <!-- 盘点记录 -->
    <zw-drawer v-model="stocktakesOpen" title="盘点记录" :sub="`共 ${stocktakesTotal} 次`" width="680">
      <div v-if="stocktakesLoading" class="stock-drawer-loading"><v-skeleton-loader type="table-row@5" /></div>
      <zw-empty v-else-if="!stocktakes.length" icon="fact_check" title="还没有盘点记录" desc="点“盘点与报损”录入实盘数量并确认后，会显示在这里。" />
      <template v-else>
        <div v-for="item in stocktakes" :key="item.stocktake_id" class="stocktake-record">
          <div class="stocktake-record-head" @click="toggleStocktake(item)">
            <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="fact_check" :size="18" /></span>
            <div class="stocktake-head-main">
              <div class="stocktake-name">{{ item.stocktake_date || '—' }} · {{ item.stocktake_no || `#${item.stocktake_id}` }}</div>
              <div class="zw-cell-sub">
                {{ formatNumber(item.item_count || 0) }} 种饲料 · 盘盈 {{ formatNumber(item.gain_count || 0) }} 条 · 盘亏 {{ formatNumber(item.loss_count || 0) }} 条
                · {{ item.operator?.name || '—' }} {{ item.created_at ? datetimeText(item.created_at) : '' }}
              </div>
            </div>
            <div class="stocktake-record-amount num">
              <span v-if="item.gain_amount" class="stock-in-text">+{{ moneyText(item.gain_amount) }}</span>
              <span v-if="item.loss_amount" class="stock-warn-text">-{{ moneyText(item.loss_amount) }}</span>
            </div>
            <zw-icon :name="stocktakeExpanded === item.stocktake_id ? 'expand_less' : 'expand_more'" :size="20" />
          </div>
          <v-table v-if="stocktakeExpanded === item.stocktake_id" class="stock-table stock-table--compact" density="compact">
            <thead>
              <tr>
                <th>饲料</th>
                <th class="text-end">账面</th>
                <th class="text-end">实盘</th>
                <th class="text-end">差异</th>
                <th class="text-end">折合金额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!stocktakeItems(item).length"><td colspan="5" class="zw-faint">明细加载中…</td></tr>
              <tr v-for="row in stocktakeItems(item)" :key="row.item_id">
                <td>{{ row.feed_name }}</td>
                <td class="text-end num">{{ formatNumber(row.book_qty || 0) }} {{ row.unit_text || '千克' }}</td>
                <td class="text-end num">{{ formatNumber(row.actual_qty || 0) }} {{ row.unit_text || '千克' }}</td>
                <td class="text-end num" :class="{ 'stock-warn-text': Number(row.diff_qty || 0) < 0, 'stock-in-text': Number(row.diff_qty || 0) > 0 }">{{ row.result_text || formatNumber(row.diff_qty || 0) }}</td>
                <td class="text-end num">{{ moneyText(row.diff_amount) }}</td>
              </tr>
            </tbody>
          </v-table>
        </div>
        <div class="stock-table-foot">
          <zw-pager :total="stocktakesTotal" :page="stocktakesPage" :size="stocktakesSize" @update:page="setStocktakesPage" @update:size="setStocktakesSize" />
        </div>
      </template>
      <template #foot>
        <v-btn variant="outlined" @click="stocktakesOpen = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <StockDocumentDrawer v-model="documentOpen" :flow="selectedFlow" :staff="isStaff" @changed="onDocumentChanged" />
  </zw-shell>
</template>

<style scoped>
.stock-loading { display: flex; flex-direction: column; }
.stock-menu { min-width: 200px; }
.stock-link { color: var(--primary); cursor: pointer; }
.stock-table :deep(td) { white-space: nowrap; }
.stock-table--compact :deep(td) { height: 44px; }
.stock-table-foot { padding: 12px 16px !important; border-top: 1px solid var(--line-1); }
.stock-material { display: flex; align-items: center; gap: 10px; }
.stock-warn-text { color: var(--on-warning-container); font-weight: 600; }
.stock-in-text { color: var(--success); }
.stock-flow-type { display: flex; align-items: center; gap: 8px; }
.stock-ico-sm { width: 28px; height: 28px; }
.stock-amount { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-radius: var(--radius-sm); background: var(--fill-1); }
.stock-amount span { font-size: 14px; color: var(--text-2); }
.stock-amount strong { font-size: 20px; color: var(--text-1); }
.stock-sync { display: flex; align-items: flex-start; gap: 10px; margin-top: 16px; padding: 12px 14px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); cursor: pointer; }
.stock-sync-title { font-size: 14px; font-weight: 600; color: var(--text-1); }
.stock-sync-sub { font-size: 12px; line-height: 18px; color: var(--text-2); margin-top: 2px; }
.stock-foot-note { margin-right: auto; font-size: 12px; color: var(--text-3); }
.stock-confirm-text { margin-right: auto; font-size: 12px; line-height: 18px; color: var(--on-warning-container); }
.stock-drawer-loading { padding: 8px 0; }
.stocktake-block { border: 1px solid var(--line-1); border-radius: var(--radius-sm); margin-bottom: 12px; }
.stocktake-head { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-bottom: 1px solid var(--line-1); }
.stocktake-head-main { flex: 1; min-width: 0; }
.stocktake-name { font-size: 14px; font-weight: 500; color: var(--text-1); }
.stocktake-body { display: grid; grid-template-columns: 220px 1fr; gap: 16px; align-items: end; padding: 14px 16px; }
.stocktake-result { display: flex; align-items: center; gap: 8px; height: 32px; padding: 0 12px; border-radius: var(--radius-sm); background: var(--fill-2); font-size: 14px; color: var(--text-1); }
.stocktake-result--loss { color: var(--on-warning-container); background: var(--warning-container); }
.stocktake-amount { margin-left: auto; font-size: 12px; color: var(--text-2); }
.stock-loss-form { margin: 0 16px 14px; padding: 14px 16px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--fill-1); }
.stock-loss-title { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 12px; font-size: 14px; font-weight: 600; }
.stock-loss-title span { font-size: 12px; font-weight: 400; color: var(--text-3); }
.stock-loss-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
.stock-dest-note { margin-top: 4px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.stock-readonly-card { display: flex; align-items: center; gap: 10px; padding: 10px 12px; margin-bottom: 16px; border-radius: var(--radius-sm); background: var(--fill-1); }
.stock-flow-toolbar { display: flex; justify-content: flex-start; margin-bottom: 12px; }
.stock-flow-row { cursor: pointer; }
.stocktake-record { border: 1px solid var(--line-1); border-radius: var(--radius-sm); margin-bottom: 10px; overflow: hidden; }
.stocktake-record-head { display: flex; align-items: center; gap: 10px; padding: 12px 16px; cursor: pointer; }
.stocktake-record-amount { display: flex; flex-direction: column; align-items: flex-end; font-size: 13px; }
</style>
