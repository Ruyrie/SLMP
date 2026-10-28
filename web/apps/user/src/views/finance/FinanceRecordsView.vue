<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import type { DataTableHeader } from 'vuetify'
import { dayjs, formatMoney, formatNumber, formatPercent, today } from '@zmt/shared'
import { useFinanceRecordsStore } from '@/stores/finance/records'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { useFarmReload } from '@/composables/useFarmReload'
import { consumeRouteAction } from '@/composables/useRouteAction'
import {
  currentMonth,
  monthText,
  monthRangeText,
  signedPercent,
  sourceTone,
  trendTone,
  dateText,
  dateTimeText,
  recordIcon
} from '@/stores/finance/shared'
import VoucherUpload from './components/VoucherUpload.vue'
import FinanceDateField from './components/FinanceDateField.vue'
import StockDocumentDrawer from '@/views/stock/components/StockDocumentDrawer.vue'
import { financeApi, type FinanceRecordDetail } from '@/api/finance'
import type { StockFlowItem } from '@/api/stock'
import { stockDocumentScope } from '@/stores/stock/refund'

const store = useFinanceRecordsStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const { summary, form } = storeToRefs(store)
const sourceDocumentOpen = ref(false)
const sourceFlow = ref<StockFlowItem | null>(null)
const linkedRecordOpen = ref(false)
const linkedRecordLoading = ref(false)
const linkedRecordError = ref('')
const linkedRecord = ref<FinanceRecordDetail | null>(null)
let linkedRecordRequest = 0
let sourceRequest = 0

function closeLinkedRecord(): void {
  ++linkedRecordRequest
  linkedRecordOpen.value = false
  linkedRecord.value = null
  linkedRecordLoading.value = false
  linkedRecordError.value = ''
}

function onRefundSaved(): void {
  void Promise.all([store.loadSummary(), store.loadList()])
}

function onViewRefundRecord(id: number): void {
  // 已在相同流水地址时 router.push 不触发 query 监听，仍应重新打开更新后的详情。
  if (String(route.query.recordId) === String(id) && route.query.detail === '1') void openLinkedRecord(id)
}

async function openLinkedRecord(id: number): Promise<void> {
  const request = ++linkedRecordRequest
  const scope = stockDocumentScope()
  linkedRecordOpen.value = true
  linkedRecordLoading.value = true
  linkedRecord.value = null
  linkedRecordError.value = ''
  try {
    const record = await financeApi.recordDetail(id)
    if (request === linkedRecordRequest && scope === stockDocumentScope()) linkedRecord.value = record
  } catch (error) {
    if (request === linkedRecordRequest && scope === stockDocumentScope()) linkedRecordError.value = (error as Error).message || '关联流水加载失败'
  } finally {
    if (request === linkedRecordRequest && scope === stockDocumentScope()) linkedRecordLoading.value = false
  }
}

watch(stockDocumentScope, () => {
  closeLinkedRecord()
  sourceDocumentOpen.value = false
  sourceFlow.value = null
}, { flush: 'sync' })
onBeforeUnmount(() => { ++sourceRequest; closeLinkedRecord() })

const headers = computed<DataTableHeader[]>(() => {
  const rows: DataTableHeader[] = [
    { title: '日期', key: 'recordDate', sortable: false, width: 112 },
    { title: '收支项目', key: 'title', sortable: false },
    { title: '金额', key: 'amount', sortable: false, align: 'end', width: 116 },
    { title: '操作', key: 'actions', sortable: false, align: 'end', width: 160 }
  ]
  if (farm.viewAll) rows.splice(1, 0, { title: '养殖场', key: 'farmName', sortable: false, width: 132 })
  return rows
})

const desc = computed(() =>
  farm.viewAll
    ? '只合计你担任所有者或场长的养殖场；先分别相加收入和支出，再算结余'
    : '按记账日期归月；物资入库勾选“同步记账”、出栏收款和登记退货退款会自动生成账目'
)

const managerFarmCount = computed(
  () => farm.farms.filter((row) => row.role === 'OWNER' || row.role === 'MANAGER').length
)

const periodText = computed(() => {
  const value = summary.value
  if (!value) return ''
  const range = value.isCurrentMonth
    ? `截至 ${dateText(value.periodEnd)}`
    : `${dateText(value.periodStart)}–${dateText(value.periodEnd)}`
  return `${range} · 已作废的不计入`
})

const balanceDelta = computed(() => {
  const value = summary.value
  if (!value) return { text: '', tone: 'muted', arrow: false }
  if (!value.balanceComparable) return { text: '较上月同期无法比较', tone: 'muted', arrow: false }
  const rate = Number(value.balanceMomRate || 0)
  return {
    text: `较上月同期 ${signedPercent(rate)}`,
    tone: rate >= 0 ? 'success' : 'error',
    arrow: true
  }
})

const incomeDelta = computed(() => {
  const value = summary.value
  if (!value) return { text: '', tone: 'muted', arrow: false }
  if (!value.incomeComparable) return { text: '环比无法比较', tone: 'muted', arrow: false }
  const rate = Number(value.incomeMomRate || 0)
  return { text: `环比 ${signedPercent(rate)}`, tone: trendTone(rate, true), arrow: rate !== 0 }
})

const expenseDelta = computed(() => {
  const value = summary.value
  if (!value) return { text: '', tone: 'muted', arrow: false }
  if (!value.expenseComparable) return { text: '环比无法比较', tone: 'muted', arrow: false }
  const rate = Number(value.expenseMomRate || 0)
  return { text: `环比 ${signedPercent(rate)}`, tone: trendTone(rate, false), arrow: rate !== 0 }
})

const topExpenseShare = computed(() => {
  const rows = summary.value?.expenseShares || []
  return rows[0] ? `${rows[0].name}占 ${formatPercent(rows[0].shareRate)}` : ''
})

const tabs = computed(() => {
  const value = summary.value
  return [
    { label: '全部', value: 'ALL' },
    { label: '支出', value: 'EXPENSE' },
    { label: '收入', value: 'INCOME' },
    { label: '自动生成', value: 'AUTO', count: Number(value?.autoCount || 0) },
    { label: '手工记录', value: 'MANUAL', count: Number(value?.manualCount || 0) }
  ]
})

const shareRows = computed(() =>
  store.shares.map((row, index) => ({
    ...row,
    color: `e10-o${(index % 6) + 1}`,
    momText:
      row.comparable && row.momRate !== undefined && row.momRate !== null
        ? `环比 ${signedPercent(row.momRate)}`
        : '环比无法比较',
    momTone:
      row.comparable && row.momRate !== undefined && row.momRate !== null
        ? trendTone(row.momRate, store.shareDirection === 'INCOME')
        : 'muted'
  }))
)

const donutItems = computed(() =>
  shareRows.value.map((row) => ({
    label: row.name,
    value: Number(row.amount || 0),
    color: row.color,
    text: `${formatPercent(row.shareRate)}`
  }))
)

const shareTotalText = computed(() =>
  formatMoney(store.shareDirection === 'INCOME' ? summary.value?.income : summary.value?.expenseAmount)
)

const filterCategoryName = computed(() => {
  const id = Number(store.query.categoryIds || 0)
  if (!id) return ''
  const category = store.categories.find((item) => item.id === id)
  return category?.name || ''
})

const batchFilterText = computed(() => {
  if (!store.query.batchId) return ''
  const row = store.list.find((item) => item.batchId === store.query.batchId)
  return row?.batchNo || `批次 ${store.query.batchId}`
})

const farms = computed(() => summary.value?.farms || [])
const farmTotals = computed(() => ({
  income: Number(summary.value?.income || 0),
  expense: Number(summary.value?.expenseAmount || 0),
  balance: Number(summary.value?.balanceAmount || 0)
}))

const monthOptions = computed(() => {
  const options: { title: string; value: string }[] = []
  let cursor = dayjs(`${currentMonth()}-01`)
  for (let index = 0; index < 24; index += 1) {
    options.push({ title: monthText(cursor.format('YYYY-MM')), value: cursor.format('YYYY-MM') })
    cursor = cursor.subtract(1, 'month')
  }
  return options
})

const monthMenu = ref(false)
const subMenu = ref(false)
const quickSubName = ref('')
const quickSubBusy = ref(false)
const uploadBusy = ref(false)
const formError = (field: string): string => store.fieldError(field)

let keywordTimer = 0

watch(
  () => store.query.keyword,
  (value) => {
    window.clearTimeout(keywordTimer)
    keywordTimer = window.setTimeout(() => {
      if (store.query.keyword !== value) return
      store.setKeyword(value)
    }, 300)
  }
)

watch(
  [() => form.value.amount, () => form.value.categoryId, () => form.value.recordDate],
  () => {
    if (!store.formOpen) return
    window.clearTimeout(duplicateTimer)
    duplicateTimer = window.setTimeout(() => {
      if (store.formOpen) void store.checkDuplicate()
    }, 500)
  }
)

let duplicateTimer = 0

const selectedCategory = computed(() => store.currentCategory)
const deleteDialog = computed({
  get: () => !!store.subDeleteTarget,
  set: (value: boolean) => {
    if (!value) store.subDeleteTarget = null
  }
})
const showBatchMarkHint = computed(() => {
  const code = selectedCategory.value?.code || ''
  return ['FEED', 'DRUG', 'SEEDLING'].includes(code) && !!form.value.batchId
})

function setViewAll(value: boolean): void {
  if (farm.viewAll === value) return
  farm.setViewAll(value)
  store.reset()
  void store.load()
}

function switchMonth(month: string): void {
  monthMenu.value = false
  store.setMonth(month)
}

function setType(value: unknown): void {
  store.setType(String(value))
}

function selectCategory(categoryId?: number): void {
  if (categoryId) store.setCategory(categoryId)
}

async function addQuickSubcategory(): Promise<void> {
  const categoryId = form.value.categoryId
  if (!categoryId || quickSubBusy.value) return
  quickSubBusy.value = true
  const id = await store.createSubcategory(categoryId, quickSubName.value)
  if (id) {
    form.value.subcategoryId = id
    quickSubName.value = ''
    subMenu.value = false
  }
  quickSubBusy.value = false
}

function openRecord(item: Record<string, unknown>): void {
  if (item.sourceType !== 'MANUAL') return
  void store.openEdit(item as never)
}

async function openSource(item: Record<string, unknown> | FinanceRecordDetail): Promise<void> {
  const target = item.sourceTarget as { bizType?: string; bizId?: number } | undefined
  if (!target?.bizType || !target.bizId) return
  // 退货退款与入库支出：在本页打开来源单据（饲料、兽药入库都能看，不再一律跳到饲料库存页）
  if (target.bizType === 'OUTBOUND' || target.bizType === 'INBOUND') {
    const inbound = target.bizType === 'INBOUND'
    const request = ++sourceRequest
    const targetFarm = Number(item.farmId || farm.currentFarmId)
    closeLinkedRecord()
    try {
      if (targetFarm !== farm.currentFarmId) await farm.switchTo(targetFarm)
      await nextTick()
      if (request !== sourceRequest || farm.currentFarmId !== targetFarm) return
      sourceFlow.value = inbound
        ? { flow_id: 0, biz_type: 'INBOUND', biz_id: target.bizId, flow_type: 'INBOUND' }
        : { flow_id: 0, biz_type: 'OUTBOUND', biz_id: target.bizId, flow_type: 'RETURN' }
      sourceDocumentOpen.value = true
    } catch (error) {
      if (request === sourceRequest) ui.toast((error as Error).message || (inbound ? '来源入库单打开失败' : '来源退货单打开失败'), 'error')
    }
    return
  }
  if (target.bizType === 'SALE_ORDER') {
    void router.push({ name: 'WU-E04', params: { id: String(target.bizId) } })
    return
  }
  if (target.bizType === 'DRUG_LOT') {
    void router.push({ name: 'WU-M03', params: { lotId: String(target.bizId) } })
  }
}

function subText(item: Record<string, unknown>): string {
  if (item.status === 'VOID') return '手工记录 · 已作废，不计入汇总'
  if (item.sourceType && item.sourceType !== 'MANUAL') {
    return String(item.detailText || item.sourceNo || '')
  }
  const parts = ['手工记录']
  if (item.batchNo) parts.push(String(item.batchNo))
  else if (item.voucherCount) parts.push(`${item.voucherCount} 张凭证`)
  return parts.join(' · ')
}

function weekdayText(date?: string): string {
  if (!date) return ''
  return formatDateWeekday(date)
}

function formatDateWeekday(date: string): string {
  const value = dayjs(date)
  if (!value.isValid()) return ''
  return `周${'日一二三四五六'[value.day()]}`
}

function isToday(date?: string): boolean {
  return !!date && date === today()
}

function amountDisplay(item: { recordType?: string; amount?: number }): string {
  const sign = item.recordType === 'INCOME' ? '+' : '−'
  return `${sign}${formatMoney(item.amount)} 元`
}

function shareText(row: { amount?: number }): string {
  return `${formatMoney(row.amount)} 元`
}

async function switchFarm(farmId?: number): Promise<void> {
  if (!farmId) return
  const target = farm.farms.find((row) => row.farm_id === farmId)
  try {
    await farm.switchTo(farmId)
    ui.toast(`已切换到${target?.name || '该养殖场'}`)
  } catch (e) {
    ui.toast((e as { message?: string }).message || '切换养殖场失败', 'error')
  }
}

function openCategories(): void {
  store.openCategories()
}

function onCategoryClick(): void {
  store.checkDuplicate()
}

function locateDuplicate(): void {
  const duplicate = store.duplicate
  if (!duplicate?.recordId) return
  void store.locateRecord(duplicate.recordId, duplicate.recordDate)
}

async function handleQuery(): Promise<void> {
  if (route.query.action === 'create') {
    // 先去掉 action 再打开表单（抽屉在路由变化时会自动关闭）；刷新不重复弹出，再次点击也能触发
    await consumeRouteAction(route, router)
    await store.openCreate()
  }
  const recordId = Number(route.query.recordId || 0)
  if (recordId) {
    if (route.query.detail === '1') void openLinkedRecord(recordId)
    await store.locateRecord(recordId, String(route.query.date || ''))
  }
  const batchId = Number(route.query.batchId || 0)
  if (batchId && !store.query.batchId) {
    store.query.batchId = batchId
    store.query.page = 1
    await store.loadList()
  }
}

onMounted(async () => {
  await store.load()
  await handleQuery()
})

watch(() => [route.query.recordId, route.query.date, route.query.detail], () => void handleQuery())
watch(() => route.query.action, (value) => {
  if (value === 'create' && store.status !== 'loading') void handleQuery()
})

useFarmReload(async () => {
  store.reset()
  await store.load()
})
</script>

<template>
  <zw-shell side="user" active="WU-E02" :crumbs="['经营管理', '收支记账']">
    <StockDocumentDrawer v-model="sourceDocumentOpen" :flow="sourceFlow" :staff="farm.role === 'staff'" @refund-saved="onRefundSaved" @view-finance-record="onViewRefundRecord" @changed="store.load()" />
    <zw-drawer :model-value="linkedRecordOpen" title="关联收支流水" width="560" @update:model-value="!$event && closeLinkedRecord()">
      <v-skeleton-loader v-if="linkedRecordLoading" type="article" />
      <zw-notice v-else-if="linkedRecordError" type="warning">
        {{ linkedRecordError }}
        <v-btn variant="text" @click="openLinkedRecord(Number(route.query.recordId))">重试</v-btn>
      </zw-notice>
      <template v-else-if="linkedRecord">
        <zw-desc :cols="2" bordered :label-width="88">
          <zw-desc-item label="收支项目">{{ linkedRecord.title || '—' }}</zw-desc-item>
          <zw-desc-item label="金额">{{ amountDisplay(linkedRecord) }}</zw-desc-item>
          <zw-desc-item label="记账日期">{{ linkedRecord.recordDate || '—' }}</zw-desc-item>
          <zw-desc-item label="状态">{{ linkedRecord.statusText || '有效' }}</zw-desc-item>
          <zw-desc-item label="来源">{{ linkedRecord.sourceLabel || '—' }}</zw-desc-item>
          <zw-desc-item label="来源单号">{{ linkedRecord.sourceNo || '—' }}</zw-desc-item>
          <zw-desc-item label="说明" :span="2">{{ linkedRecord.note || linkedRecord.detailText || '—' }}</zw-desc-item>
        </zw-desc>
        <zw-notice v-if="linkedRecord.sourceType === 'DRUG_RETURN_REFUND'" type="info" class="mt-4">由退货单登记退款自动生成；需要更正时请在登记当天返回退货单操作，请勿重复手工记账。</zw-notice>
      </template>
      <template #foot>
        <v-btn variant="outlined" @click="closeLinkedRecord">关闭</v-btn>
        <v-btn v-if="linkedRecord?.sourceTarget?.bizId" color="primary" @click="openSource(linkedRecord)">查看原单据</v-btn>
      </template>
    </zw-drawer>
    <zw-page-head title="收支记账" :desc="desc">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="category" @click="openCategories">支出分类</v-btn>
        <v-btn v-if="!farm.viewAll" color="primary" prepend-icon="add" @click="store.openCreate()">记一笔</v-btn>
      </template>
    </zw-page-head>

    <div class="fin-toolbar">
      <div class="fin-month">
        <button class="fin-month-btn" type="button" aria-label="上个月" @click="store.prevMonth()">
          <zw-icon name="chevron_left" :size="18" />
        </button>
        <v-menu v-model="monthMenu" location="bottom start">
          <template #activator="{ props: activator }">
            <button v-bind="activator" class="fin-month-text" type="button">
              {{ monthText(store.month) }}
              <zw-icon name="arrow_drop_down" :size="18" />
            </button>
          </template>
          <v-list density="compact" max-height="320" class="fin-month-list">
            <v-list-item
              v-for="item in monthOptions"
              :key="item.value"
              :title="item.title"
              :active="item.value === store.month"
              @click="switchMonth(item.value)"
            />
          </v-list>
        </v-menu>
        <button class="fin-month-btn" type="button" aria-label="下个月" :disabled="store.isFutureMonth" @click="store.nextMonth()">
          <zw-icon name="chevron_right" :size="18" />
        </button>
      </div>

      <zw-seg
        v-if="managerFarmCount > 1"
        class="ml-3"
        :model-value="farm.viewAll ? 'ALL' : 'FARM'"
        :items="[
          { label: farm.farmName || '当前养殖场', value: 'FARM', icon: 'domain' },
          { label: '全部养殖场合计', value: 'ALL', icon: 'apps' }
        ]"
        @update:model-value="setViewAll($event === 'ALL')"
      />

      <div class="fin-toolbar-right zw-faint body-s">{{ periodText }}</div>
    </div>

    <div v-if="store.summaryStatus === 'loading' && !summary">
      <v-skeleton-loader type="article, article, table-row@6" />
    </div>

    <zw-card v-else-if="store.summaryStatus === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="你没有查看经营数据的权限" desc="经营功能只对所有者和场长开放。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.summaryStatus === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="经营数据加载失败" :desc="store.summaryError">
        <v-btn variant="outlined" @click="store.loadSummary()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="summary">
      <div class="zw-grid-4 mb-4">
        <zw-stat
          :label="farm.viewAll ? '合计结余' : '本月结余'"
          icon="savings"
          :value="formatMoney(summary.balanceAmount)"
          unit="元"
          tone="primary"
        >
          <template #foot>
            <span class="fin-delta" :class="`fin-delta--${balanceDelta.tone}`">
              <zw-icon v-if="balanceDelta.arrow" :name="Number(summary.balanceMomRate) >= 0 ? 'arrow_upward' : 'arrow_downward'" :size="14" />
              {{ balanceDelta.text }}
            </span>
            <span class="fin-break">{{ farm.viewAll ? '上月合计' : '上月同期' }} {{ formatMoney(summary.prevBalanceAmount) }} 元</span>
          </template>
        </zw-stat>

        <zw-stat
          :label="farm.viewAll ? '合计收入' : '收入'"
          icon="south_west"
          :value="formatMoney(summary.income)"
          unit="元"
        >
          <template #foot>
            <span class="fin-delta" :class="`fin-delta--${incomeDelta.tone}`">
              <zw-icon v-if="incomeDelta.arrow" :name="Number(summary.incomeMomRate) >= 0 ? 'arrow_upward' : 'arrow_downward'" :size="14" />
              {{ incomeDelta.text }}
            </span>
            <span v-if="farm.viewAll" class="fin-break">
              {{ farms.map((row) => `${row.farmName} ${formatMoney(row.income)} 元`).join(' · ') }}
            </span>
            <span v-else class="fin-break">出栏收款自动生成收入记录</span>
          </template>
        </zw-stat>

        <zw-stat
          :label="farm.viewAll ? '合计支出' : '支出'"
          icon="north_east"
          :value="formatMoney(summary.expenseAmount)"
          unit="元"
        >
          <template #foot>
            <span class="fin-delta" :class="`fin-delta--${expenseDelta.tone}`">
              <zw-icon v-if="expenseDelta.arrow" :name="Number(summary.expenseMomRate) >= 0 ? 'arrow_upward' : 'arrow_downward'" :size="14" />
              {{ expenseDelta.text }}
            </span>
            <span v-if="farm.viewAll" class="fin-break">
              {{ farms.map((row) => `${row.farmName} ${formatMoney(row.expenseAmount)} 元`).join(' · ') }}
            </span>
            <span v-else-if="topExpenseShare" class="fin-break">{{ topExpenseShare }}</span>
          </template>
        </zw-stat>

        <zw-stat
          :label="farm.viewAll ? '计入合计的养殖场' : '收支笔数'"
          icon="receipt_long"
          :value="farm.viewAll ? formatNumber(summary.manageableFarmCount || 0) : formatNumber(summary.recordCount || 0)"
          :unit="farm.viewAll ? '个' : '笔'"
        >
          <template #foot>
            <span v-if="farm.viewAll" class="fin-break">
              {{ farms.map((row) => `${row.farmName}（${row.roleText}）`).join('、') }}
            </span>
            <span v-else class="fin-break">
              自动生成 {{ summary.autoCount || 0 }} 笔 · 手工记录 {{ summary.manualCount || 0 }} 笔
            </span>
          </template>
        </zw-stat>
      </div>

      <zw-notice v-if="farm.viewAll" class="mb-4" icon="info">
        只合计你担任所有者或场长的养殖场。<template v-if="summary.staffFarmNames && summary.staffFarmNames.length">
          {{ summary.staffFarmNames.join('、') }}你是员工，不计入。</template>
      </zw-notice>

      <zw-card v-if="farm.viewAll" title="按养殖场" :sub="`${farms.length} 个 · 先相加收入和支出，再算结余`" flush class="mb-4">
        <v-data-table :headers="[
          { title: '养殖场', key: 'farmName', sortable: false },
          { title: '收入', key: 'income', sortable: false, align: 'end', width: 180 },
          { title: '支出', key: 'expenseAmount', sortable: false, align: 'end', width: 180 },
          { title: '结余', key: 'balanceAmount', sortable: false, align: 'end', width: 200 },
          { title: '收入占比', key: 'incomeShareRate', sortable: false, align: 'end', width: 120 },
          { title: '操作', key: 'actions', sortable: false, align: 'end', width: 160 }
        ]" :items="farms" item-value="farmId" hide-default-footer>
          <template #item.farmName="{ item }">
            <div class="py-2">
              <zw-tag type="farm" size="small" icon="domain">{{ item.farmName }}</zw-tag>
              <div class="zw-cell-sub">{{ item.roleText }}<template v-if="item.mainCategoryName"> · {{ item.mainCategoryName }}</template></div>
            </div>
          </template>
          <template #item.income="{ item }">
            <div class="fin-num">{{ formatMoney(item.income) }} 元</div>
            <div class="fin-sub-delta" :class="`fin-delta--${item.incomeComparable ? (Number(item.incomeMomRate) >= 0 ? 'success' : 'error') : 'muted'}`">
              {{ item.incomeComparable ? `↑ 环比 ${signedPercent(item.incomeMomRate)}` : '环比无法比较' }}
            </div>
          </template>
          <template #item.expenseAmount="{ item }">
            <div class="fin-num">{{ formatMoney(item.expenseAmount) }} 元</div>
            <div class="fin-sub-delta" :class="`fin-delta--${item.expenseComparable ? (Number(item.expenseMomRate) > 0 ? 'error' : 'success') : 'muted'}`">
              {{ item.expenseComparable ? `↑ 环比 ${signedPercent(item.expenseMomRate)}` : '环比无法比较' }}
            </div>
          </template>
          <template #item.balanceAmount="{ item }">
            <div class="fin-num">{{ formatMoney(item.balanceAmount) }} 元</div>
            <div class="fin-sub-delta" :class="`fin-delta--${item.balanceComparable ? (Number(item.balanceMomRate) >= 0 ? 'success' : 'error') : 'muted'}`">
              {{ item.balanceComparable ? `↑ 较上月同期 ${signedPercent(item.balanceMomRate)}` : '较上月同期无法比较' }}
            </div>
          </template>
          <template #item.incomeShareRate="{ item }">
            <span class="num">{{ item.incomeShareRate !== undefined && item.incomeShareRate !== null ? formatPercent(item.incomeShareRate) : '—' }}</span>
          </template>
          <template #item.actions="{ item }">
            <span class="zw-cell-actions"><a @click="switchFarm(item.farmId)">切换到该场查看</a></span>
          </template>
          <template #tfoot>
            <tr class="fin-total-row">
              <td>合计</td>
              <td class="text-end num">{{ formatMoney(farmTotals.income) }} 元</td>
              <td class="text-end num">{{ formatMoney(farmTotals.expense) }} 元</td>
              <td class="text-end num">{{ formatMoney(farmTotals.balance) }} 元</td>
              <td class="text-end num">100.0%</td>
              <td />
            </tr>
          </template>
        </v-data-table>
      </zw-card>

      <div class="fin-main" :class="{ 'fin-main--all': farm.viewAll }">
        <zw-card v-if="!farm.viewAll" title="分类占比" sub="单位：元">
          <template #extra>
            <zw-seg
              size="small"
              :model-value="store.shareDirection"
              :items="[
                { label: '支出', value: 'EXPENSE' },
                { label: '收入', value: 'INCOME' }
              ]"
              @update:model-value="store.setShareDirection($event as 'EXPENSE' | 'INCOME')"
            />
          </template>
          <template v-if="shareRows.length">
            <div class="fin-donut">
              <zw-donut
                :items="donutItems"
                :value="shareTotalText"
                :title="store.shareDirection === 'INCOME' ? '本月收入' : '本月支出'"
                :size="156"
                :thickness="20"
                :legend="false"
              />
            </div>
            <ul class="fin-share-list">
              <li v-for="row in shareRows" :key="row.categoryId" class="fin-share-row" @click="store.filterByCategory(row.categoryId)">
                <i class="fin-dot" :style="{ background: `var(--${row.color})` }" />
                <span class="fin-share-name">{{ row.name }}</span>
                <span class="fin-share-pct num">{{ formatPercent(row.shareRate) }}</span>
                <span class="fin-share-amount num">{{ shareText(row) }}</span>
                <span class="fin-share-mom" :class="`fin-delta--${row.momTone}`">{{ row.momText }}</span>
              </li>
            </ul>
          </template>
          <zw-empty
            v-else
            icon="donut_large"
            :title="`${dayjs(store.month + '-01').month() + 1}月还没有收支记录`"
            desc="点“记一笔”记录开销，物资入库勾选同步记账后也会自动生成。"
          >
            <v-btn color="primary" variant="outlined" @click="store.openCreate()">记一笔</v-btn>
          </zw-empty>
        </zw-card>

        <zw-card
          title="收支流水"
          :sub="farm.viewAll
            ? `每条记录带养殖场标签 · 共 ${summary.recordCount || 0} 笔`
            : `本月 ${summary.recordCount || 0} 笔，其中自动生成 ${summary.autoCount || 0} 笔`"
          flush
        >
          <template #extra>
            <v-text-field
              v-model="store.query.keyword"
              class="fin-search"
              placeholder="搜索分类、说明、批次号或金额"
              prepend-inner-icon="search"
              density="compact"
              hide-details
              clearable
              @click:clear="store.setKeyword('')"
            />
          </template>

          <div class="fin-filterbar">
            <zw-seg size="small" :model-value="store.query.type" :items="tabs" @update:model-value="setType" />
            <div class="fin-filter-total zw-faint body-s">
              <span v-if="filterCategoryName" class="fin-chip">
                {{ filterCategoryName }}
                <button type="button" aria-label="清除分类筛选" @click="store.clearCategoryFilter()">×</button>
              </span>
              <span v-if="batchFilterText" class="fin-chip">
                批次 {{ batchFilterText }}
                <button type="button" aria-label="清除批次筛选" @click="store.query.batchId = 0; store.setPage(1)">×</button>
              </span>
              收入 {{ formatMoney(store.totals.income) }} 元 · 支出 {{ formatMoney(store.totals.expenseAmount) }} 元
            </div>
          </div>

          <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@6" />
          <zw-empty
            v-else-if="store.status === 'error'"
            icon="cloud_off"
            tone="error"
            title="收支流水加载失败"
            :desc="store.error"
          >
            <v-btn variant="outlined" @click="store.loadList()">重新加载</v-btn>
          </zw-empty>
          <zw-empty
            v-else-if="store.status === 'empty'"
            icon="receipt_long"
            :title="`${dayjs(store.month + '-01').month() + 1}月还没有收支记录`"
            desc="点“记一笔”开始记账。"
          >
            <v-btn color="primary" variant="outlined" @click="store.openCreate()">记一笔</v-btn>
          </zw-empty>
          <zw-empty
            v-else-if="store.status === 'filtered_empty'"
            icon="search_off"
            title="没有符合条件的记录"
            desc="换个筛选条件试试。"
          >
            <v-btn variant="text" @click="store.clearFilters()">清除筛选</v-btn>
          </zw-empty>
          <template v-else>
            <v-data-table
              :headers="headers"
              :items="store.list"
              item-value="id"
              hide-default-footer
              class="fin-records-table"
              :row-props="(data: { item: Record<string, unknown> }) => ({
                'data-record-id': data.item.id,
                class: Number(store.highlightId) === Number(data.item.id) ? 'fin-row-highlight' : ''
              })"
            >
              <template #item.recordDate="{ item }">
                <div>{{ dateText(item.recordDate) }}</div>
                <div class="zw-cell-sub">{{ weekdayText(item.recordDate) }}<template v-if="isToday(item.recordDate)"> · 今天</template></div>
              </template>
              <template #item.farmName="{ item }">
                <zw-tag type="farm" size="small" icon="domain">{{ item.farmName || farm.farmName }}</zw-tag>
              </template>
              <template #item.title="{ item }">
                <div class="fin-record" :class="{ 'fin-record--void': item.status === 'VOID' }" @click="openRecord(item)">
                  <span class="zw-ico-tile" :class="item.status === 'VOID' ? '' : 'zw-ico-tile--primary'">
                    <zw-icon :name="recordIcon(item.categoryIcon)" :size="18" />
                  </span>
                  <div class="fin-record-text">
                    <div class="zw-cell-main">
                      <span class="fin-record-title">{{ item.title }}</span>
                      <zw-tag
                        v-if="item.status === 'VOID'"
                        size="small"
                        class="ml-1"
                      >已作废</zw-tag>
                      <zw-tag
                        v-else-if="item.sourceType && item.sourceType !== 'MANUAL'"
                        type="primary"
                        size="small"
                        icon="link"
                        class="ml-1"
                      >{{ item.sourceLabel }}</zw-tag>
                    </div>
                    <div class="zw-cell-sub">{{ subText(item) }}</div>
                  </div>
                </div>
              </template>
              <template #item.amount="{ item }">
                <span class="num fin-amount" :class="{ 'fin-amount--void': item.status === 'VOID' }">{{ amountDisplay(item) }}</span>
              </template>
              <template #item.actions="{ item }">
                <span class="zw-cell-actions">
                  <template v-if="item.sourceType !== 'MANUAL'">
                    <a @click="openSource(item)">原单据</a>
                    <v-tooltip text="自动生成，不能单独修改；原单据变化时同步更新" location="top">
                      <template #activator="{ props: tip }">
                        <zw-icon v-bind="tip" name="lock" :size="16" class="zw-faint" />
                      </template>
                    </v-tooltip>
                  </template>
                  <template v-else-if="item.status !== 'VOID'">
                    <a @click="store.openEdit(item)">修改</a>
                    <a class="danger" @click="store.askVoid(item)">作废</a>
                    <a @click="store.openLogs(item)">修改记录</a>
                  </template>
                  <template v-else>
                    <a @click="store.openLogs(item)">修改记录</a>
                  </template>
                </span>
              </template>
            </v-data-table>
            <div class="zw-table-foot zw-table-foot--in">
              <span class="zw-faint body-s">按记账日期倒序；自动生成的记录不能单独修改</span>
              <zw-pager
                :total="store.total"
                :page="store.query.page"
                :size="store.query.size"
                @update:page="store.setPage"
                @update:size="store.setSize"
              />
            </div>
          </template>
        </zw-card>
      </div>
    </template>

    <!-- 记一笔 / 修改记录 -->
    <zw-drawer
      v-model="store.formOpen"
      :title="store.formMode === 'edit' ? '修改记录' : '记一笔'"
      :sub="`${farm.farmName || '当前养殖场'} · 手工记录，保存后可修改或作废，系统保留修改记录`"
      width="560"
    >
      <div v-if="store.formLoading" class="fin-drawer-loading"><v-skeleton-loader type="article, table-row@5" /></div>
      <zw-empty v-else-if="store.formLoadError" icon="cloud_off" tone="error" title="记录加载失败" :desc="store.formLoadError">
        <v-btn variant="outlined" @click="store.closeForm()">关闭</v-btn>
      </zw-empty>
      <template v-else>
        <div class="zw-form-2">
          <zw-form-item label="类型" required :error="formError('record_type')">
            <zw-seg
              :model-value="form.recordType"
              :items="[
                { label: '支出', value: 'EXPENSE' },
                { label: '收入', value: 'INCOME' }
              ]"
              @update:model-value="store.setRecordType($event as string)"
            />
            <div v-if="store.formMode === 'edit'" class="zw-faint body-s mt-1">类型不可切换</div>
          </zw-form-item>
          <zw-form-item label="金额" required :error="formError('amount')" hint="必须大于 0，最多两位小数">
            <v-text-field
              v-model="form.amount"
              type="number"
              min="0"
              suffix="元"
              :error="!!formError('amount')"
              :disabled="store.submitting"
              @blur="onCategoryClick"
            />
          </zw-form-item>
        </div>

        <zw-notice v-if="store.duplicate" type="warning" icon="content_copy" title="可能重复记账" class="mb-4">
          {{ dateText(store.duplicate.recordDate) }}已有一条金额相同的自动记录，{{ store.duplicate.categoryName }}
          {{ formatMoney(store.duplicate.amount) }} 元，{{ store.duplicate.sourceLabel }}。确认不是同一笔再保存。
          <template #action><a class="zw-link" @click="locateDuplicate">查看</a></template>
        </zw-notice>

        <zw-form-item label="分类" required :error="formError('category_id')">
          <div class="fin-chips">
            <button
              v-for="category in store.activeCategories"
              :key="category.id"
              type="button"
              class="fin-chip-btn"
              :class="{ on: form.categoryId === category.id }"
              :disabled="store.submitting"
              @click="selectCategory(category.id)"
            >
              <zw-icon :name="category.icon || 'category'" :size="16" />
              {{ category.name }}
              <zw-icon v-if="form.categoryId === category.id" name="check" :size="16" />
            </button>
          </div>
        </zw-form-item>

        <zw-form-item
          v-if="form.recordType === 'EXPENSE' && form.categoryId"
          :label="`${selectedCategory?.name || '分类'}下的子类`"
          optional
          hint="再次点击可取消选择"
        >
          <div class="fin-chips">
            <button
              v-for="sub in store.subcategories"
              :key="sub.id"
              type="button"
              class="fin-chip-btn fin-chip-btn--sub"
              :class="{ on: form.subcategoryId === sub.id }"
              :disabled="store.submitting"
              @click="store.toggleSubcategory(Number(sub.id || 0))"
            >
              {{ sub.name }}
            </button>
            <v-menu v-model="subMenu" location="bottom start" :close-on-content-click="false">
              <template #activator="{ props: activator }">
                <button v-bind="activator" type="button" class="fin-chip-btn fin-chip-btn--add" :disabled="!form.categoryId">
                  <zw-icon name="add" :size="16" /> 添加子类
                </button>
              </template>
              <div class="fin-sub-pop">
                <v-text-field v-model="quickSubName" placeholder="子类名称，1–10 个字" density="compact" maxlength="10" hide-details @keydown.enter="addQuickSubcategory" />
                <v-btn color="primary" variant="tonal" :loading="quickSubBusy" :disabled="!quickSubName.trim()" @click="addQuickSubcategory">添加</v-btn>
              </div>
            </v-menu>
          </div>
        </zw-form-item>

        <div class="zw-form-2">
          <zw-form-item label="日期" required :error="formError('record_date')" hint="不能晚于今天">
            <FinanceDateField v-model="form.recordDate" :max="today()" :disabled="store.submitting" />
          </zw-form-item>
          <zw-form-item label="关联批次" optional :hint="showBatchMarkHint ? '只做标记，成本按出库或引种单价计入批次' : '关联后计入批次收入或成本'">
            <v-select
              v-model="form.batchId"
              :items="store.batchSelectItems"
              item-title="title"
              item-value="value"
              :loading="store.batchLoading"
              :disabled="store.submitting"
              placeholder="不关联"
            />
          </zw-form-item>
        </div>

        <zw-form-item v-if="form.recordType === 'INCOME'" label="说明" optional :error="formError('note')" hint="最多 30 字">
          <v-text-field v-model="form.note" maxlength="30" :disabled="store.submitting" placeholder="例如 能繁母猪补贴" />
        </zw-form-item>

        <zw-form-item label="凭证照片" optional>
          <VoucherUpload v-model="form.fileIds" :max="6" :disabled="store.submitting" @update:busy="uploadBusy = $event" />
        </zw-form-item>

        <zw-notice v-if="form.recordType === 'INCOME'" icon="info" class="mt-4">
          出栏收款会自动生成收入记录，不用再手工记一笔
        </zw-notice>
      </template>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" :disabled="store.submitting" @click="store.closeForm()">取消</v-btn>
        <v-btn
          color="primary"
          :loading="store.submitting"
          :disabled="store.submitting || uploadBusy || store.formLoading"
          @click="store.submitForm()"
        >保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 支出分类 -->
    <zw-drawer v-model="store.categoryOpen" title="支出分类" sub="一级分类 7 个，由平台维护 · 子类可添加、改名、删除" width="560">
      <zw-notice icon="info" class="mb-4">
        一级分类由平台统一维护，不能修改，你可以在下面添加自己的子类。平台停用的一级分类，记账时不再显示，已有账目保留原分类。
      </zw-notice>
      <div class="fin-cat-list">
        <div v-for="category in store.expenseCategories" :key="category.id" class="fin-cat-item">
          <div class="fin-cat-head" @click="store.toggleCategory(category.id)">
            <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon :name="category.icon || 'category'" :size="18" /></span>
            <div class="fin-cat-name">
              <div>{{ category.name }}</div>
              <div class="zw-cell-sub">
                平台维护 ·
                <template v-if="!category.subcategories?.length">暂无子类</template>
                <template v-else-if="category.subcategories.length <= 2">{{ category.subcategories.map((sub) => sub.name).join('、') }}</template>
                <template v-else>{{ category.subcategories.length }} 个子类</template>
              </div>
            </div>
            <zw-icon :name="store.expandedCategoryId === category.id ? 'expand_less' : 'expand_more'" :size="20" color="text-3" />
          </div>
          <div v-if="store.expandedCategoryId === category.id" class="fin-cat-body">
            <div v-for="sub in category.subcategories || []" :key="sub.id" class="fin-sub-row">
              <template v-if="store.subEditingId === sub.id">
                <v-text-field v-model="store.subEditName" density="compact" maxlength="10" hide-details class="fin-sub-input" />
                <a @click="store.saveRename()">保存</a>
                <a class="zw-faint" @click="store.cancelRename()">取消</a>
              </template>
              <template v-else>
                <span class="fin-sub-name">{{ sub.name }}</span>
                <a @click="store.startRename(sub.id, sub.name)">改名</a>
                <a class="danger" @click="store.askDelete(sub, Number(category.id))">删除</a>
              </template>
            </div>
            <div class="fin-sub-row">
              <v-text-field
                v-model="store.newSubName"
                placeholder="新子类名称，1–10 个字"
                density="compact"
                maxlength="10"
                hide-details
                class="fin-sub-input"
                @keydown.enter="store.addSubcategory()"
              />
              <v-btn
                variant="tonal"
                color="primary"
                size="small"
                prepend-icon="add"
                :loading="store.subAdding"
                :disabled="!store.newSubName.trim()"
                @click="store.addSubcategory()"
              >添加</v-btn>
            </div>
          </div>
        </div>
      </div>
      <template #foot>
        <span class="zw-foot-note">一级分类由平台维护，子类只在本场生效</span>
        <v-btn variant="outlined" @click="store.categoryOpen = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <!-- 修改记录 -->
    <zw-drawer v-model="store.logOpen" title="修改记录" :sub="store.logTarget?.title" width="560">
      <v-skeleton-loader v-if="store.logLoading" type="list-item-two-line@3" />
      <zw-empty v-else-if="store.logError" icon="cloud_off" tone="error" title="修改记录加载失败" :desc="store.logError" />
      <zw-timeline
        v-else-if="store.logs.length"
        :items="store.logs.map((log) => ({
          title: `${log.opActionText || log.opAction || '修改'} · ${log.operator?.name || ''}`,
          time: dateTimeText(log.createdAt),
          desc: log.changes?.length
            ? log.changes.map((change) => `${change.fieldName || ''} ${change.before || '—'} → ${change.after || '—'}`).join('；')
            : log.reason || ''
        }))"
      />
      <div v-else class="fin-log-empty">暂无可查看的修改记录</div>
      <template #foot>
        <v-btn variant="outlined" @click="store.logOpen = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <!-- 作废记录 -->
    <zw-dialog v-model="store.voidOpen" title="作废这条记录？" icon="block" tone="error" width="460">
      <p>作废后不再计入月度汇总和批次成本，记录仍保留在流水中。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="store.voidBusy" @click="store.voidOpen = false">取消</v-btn>
        <v-btn color="error" :loading="store.voidBusy" @click="store.confirmVoid()">作废</v-btn>
      </template>
    </zw-dialog>

    <!-- 删除子类 -->
    <zw-dialog
      v-model="deleteDialog"
      :title="`删除子类“${store.subDeleteTarget?.name || ''}”？`"
      icon="delete"
      tone="error"
      width="460"
    >
      <p>已有账目保留原子类名称，记账时不再显示这个子类。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="store.subDeleteBusy" @click="store.subDeleteTarget = null">取消</v-btn>
        <v-btn color="error" :loading="store.subDeleteBusy" @click="store.confirmDelete()">删除</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.fin-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.fin-toolbar-right { margin-left: auto; }
.fin-month { display: inline-flex; align-items: center; border: 1px solid var(--control-border); border-radius: var(--radius-sm); background: var(--bg-card); height: 32px; }
.fin-month-btn { width: 32px; height: 30px; border: 0; background: transparent; color: var(--text-2); display: inline-flex; align-items: center; justify-content: center; cursor: pointer; }
.fin-month-btn:disabled { color: var(--text-disabled); cursor: default; }
.fin-month-text { height: 30px; padding: 0 8px; border: 0; background: transparent; font: inherit; font-size: 14px; color: var(--text-1); display: inline-flex; align-items: center; gap: 2px; cursor: pointer; }
.fin-month-list { min-width: 160px; }
.fin-main { display: grid; grid-template-columns: 380px minmax(0, 1fr); gap: 16px; align-items: start; }
.fin-main--all { grid-template-columns: minmax(0, 1fr); }
.fin-donut { display: flex; justify-content: center; padding: 8px 0 4px;
  --e10-o1: #C25E00; --e10-o2: #CE7E33; --e10-o3: #D99B61; --e10-o4: #E3B58A; --e10-o5: #EAC8A8; --e10-o6: #F0D8C2; }
.fin-share-list { list-style: none; margin: 12px 0 0; padding: 0; }
.fin-share-row { display: flex; align-items: center; gap: 6px; height: 40px; border-top: 1px solid var(--line-1); font-size: 14px; cursor: pointer; }
.fin-share-row:hover { background: var(--fill-1); }
.fin-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 8px; }
.fin-share-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fin-share-pct { width: 44px; flex: 0 0 44px; text-align: right; font-size: 12px; color: var(--text-3); }
.fin-share-amount { width: 84px; flex: 0 0 84px; text-align: right; }
.fin-share-mom { width: 86px; flex: 0 0 86px; text-align: right; font-size: 12px; white-space: nowrap; }
.fin-break { flex-basis: 100%; }
.fin-delta { display: inline-flex; align-items: center; gap: 2px; font-size: 12px; }
.fin-delta--success { color: var(--success); }
.fin-delta--error { color: var(--error); }
.fin-delta--muted { color: var(--text-3); }
.fin-search { width: 280px; min-width: 280px; max-width: 280px; }
.fin-filterbar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; border-bottom: 1px solid var(--line-1); }
.fin-filter-total { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.fin-chip { display: inline-flex; align-items: center; gap: 4px; height: 24px; padding: 0 8px; border-radius: var(--radius-sm); background: var(--primary-subtle); color: var(--primary); font-size: 12px; }
.fin-chip button { border: 0; background: transparent; color: inherit; cursor: pointer; font: inherit; }
.fin-records-table :deep(td) { vertical-align: middle; }
.fin-record { display: flex; align-items: center; gap: 12px; cursor: default; }
.fin-record--void { color: var(--text-3); }
.fin-record--void .fin-record-title { text-decoration: line-through; }
.fin-record-text { min-width: 0; }
.fin-record-title { font-size: 14px; }
.fin-amount { font-weight: 500; white-space: nowrap; }
.fin-amount--void { color: var(--text-3); text-decoration: line-through; }
.fin-row-highlight :deep(td) { background: var(--primary-subtle) !important; }
.fin-drawer-loading { padding: 4px 0; }
.fin-chips { display: flex; flex-wrap: wrap; gap: 8px 6px; }
.fin-chip-btn { display: inline-flex; align-items: center; gap: 4px; height: 28px; padding: 0 10px; border: 1px solid var(--line-2); border-radius: var(--radius-sm); background: var(--bg-card); color: var(--text-1); font: inherit; font-size: 14px; cursor: pointer; }
.fin-chip-btn.on { border-color: var(--primary); background: var(--primary-subtle); color: var(--primary); font-weight: 500; }
.fin-chip-btn--sub { height: 26px; font-size: 13px; }
.fin-chip-btn--add { border-style: dashed; color: var(--primary); background: transparent; }
.fin-chip-btn:disabled { opacity: .55; cursor: default; }
.fin-sub-pop { display: flex; align-items: center; gap: 8px; padding: 10px; background: var(--bg-card); border: 1px solid var(--line-1); border-radius: var(--radius-sm); box-shadow: var(--shadow-popover); }
.fin-sub-pop :deep(.v-input) { width: 200px; }
.fin-cat-list { border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.fin-cat-item + .fin-cat-item { border-top: 1px solid var(--line-1); }
.fin-cat-head { display: flex; align-items: center; gap: 12px; padding: 10px 16px; cursor: pointer; }
.fin-cat-head:hover { background: var(--fill-1); }
.fin-cat-name { flex: 1; min-width: 0; font-size: 14px; font-weight: 500; }
.fin-cat-body { padding: 4px 16px 12px 60px; background: var(--fill-1); }
.fin-sub-row { display: flex; align-items: center; gap: 12px; min-height: 40px; border-bottom: 1px dashed var(--line-1); font-size: 14px; }
.fin-sub-row:last-child { border-bottom: 0; }
.fin-sub-name { flex: 1; }
.fin-sub-input { max-width: 240px; }
.fin-sub-row a { color: var(--primary); cursor: pointer; }
.fin-sub-row a.danger { color: var(--error); }
.fin-sub-row a.zw-faint { color: var(--text-3); }
.fin-log-empty { padding: 24px 0; text-align: center; color: var(--text-3); font-size: 14px; }
.fin-num { font-size: 14px; }
.fin-sub-delta { font-size: 12px; line-height: 18px; }
.fin-total-row { background: var(--fill-1); font-weight: 600; }
.fin-total-row td { height: 48px; font-size: 14px; }
</style>
