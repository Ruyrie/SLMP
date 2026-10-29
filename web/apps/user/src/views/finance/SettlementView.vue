<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { formatMoney, formatNumber, formatPercent } from '@zmt/shared'
import { useSettlementStore } from '@/stores/finance/settlement'
import { useFarmReload } from '@/composables/useFarmReload'
import { costNoteText, dateText, dateTimeText, paymentIcon, paymentTone, priceText } from '@/stores/finance/shared'

const store = useSettlementStore()
const route = useRoute()
const router = useRouter()
const { detail } = storeToRefs(store)

const batch = computed(() => detail.value?.batch)
const locked = computed(() => detail.value?.status === 'LOCKED')

const unit = computed(() => store.countUnit)

const desc = computed(() => {
  const value = batch.value
  if (!value) return ''
  // 接口的 batch 没有结束日期时用结算锁定时间（全部出栏后自动结算）；都没有就不写日期
  const endDay = value.endDate || detail.value?.settledAt
  const state = locked.value
    ? endDay ? `${dateText(endDay)}全部出栏` : '已全部出栏'
    : `在养 ${value.dayAge || 0} 日龄`
  return `${value.penName || ''} · ${value.breedName || ''}${value.categoryName || ''} · ${state} · 入栏 ${formatNumber(
    value.entryCount
  )} ${unit.value} · 死淘 ${formatNumber(value.deathCount)} · ${
    locked.value ? '出栏' : '存栏'
  } ${formatNumber(locked.value ? value.soldCount : value.currentCount)} ${unit.value}`
})

const batchItems = computed(() => store.batchSelectItems)
const selectedBatchId = computed({
  get: () => store.batchId,
  set: (value: number) => {
    void switchBatch(value)
  }
})

const profitValue = computed(() => Number(detail.value?.profit || 0))

const costItems = computed(() =>
  (detail.value?.costs || []).map((item, index) => ({
    ...item,
    amountValue: Number(item.amount || 0),
    color: `e10-o${(index % 6) + 1}`,
    percent: formatPercent(item.shareRate)
  }))
)

const donutItems = computed(() =>
  costItems.value.map((item) => ({
    label: item.costGroupText,
    value: item.amountValue,
    color: item.color,
    text: `${formatMoney(item.amountValue)} 元`
  }))
)

const maxIncomeCost = computed(() =>
  Math.max(Number(detail.value?.saleIncome || 0), Number(detail.value?.costTotal || 0), 1)
)

const incomeWidth = computed(() =>
  Math.min(100, (Number(detail.value?.saleIncome || 0) / maxIncomeCost.value) * 100)
)

const costWidth = computed(() =>
  Math.min(100, (Number(detail.value?.costTotal || 0) / maxIncomeCost.value) * 100)
)

const timelineItems = computed(() => {
  const value = detail.value
  if (!value) return []
  const items: {
    title: string
    time?: string
    desc?: string
    tone?: string
    icon?: string
    tag?: string
    tagType?: string
  }[] = []
  for (const supplement of value.supplements || []) {
    items.push({
      title: `补录费用待确认 · ${supplement.title || ''}`,
      time: dateText(supplement.bizDate),
      desc: `手工记录关联本批次，核算已锁定，确认后才会计入（${formatMoney(supplement.amount)} 元）`,
      tone: 'warning',
      icon: 'pending_actions',
      tag: '待确认',
      tagType: 'warning'
    })
  }
  for (const record of value.recentCostRecords || []) {
    if ((value.supplements || []).some((item) => item.title === record.title && item.bizDate === record.bizDate)) continue
    items.push({
      title: `${record.title || '补录费用'} · ${record.costGroupText || ''}`,
      time: dateText(record.bizDate),
      desc: `${formatMoney(record.amount)} 元`,
      tone: 'neutral',
      icon: 'receipt_long'
    })
  }
  if (locked.value) {
    items.push({
      title: '全部出栏，自动结算并锁定',
      time: dateTimeText(value.settledAt),
      desc: `销售金额 ${formatMoney(value.saleIncome)} 元 · ${formatNumber(value.saleOrders?.reduce((sum, order) => sum + Number(order.quantity || 0), 0))} ${
        unit.value
      }`,
      tone: 'success',
      icon: 'lock'
    })
  } else {
    items.push({
      title: '实时核算 · 截至今天',
      time: dateTimeText(value.calcAt),
      desc: '全部出栏后系统自动结算并锁定核算',
      tone: 'primary',
      icon: 'calculate'
    })
  }
  return items
})

const firstSupplement = computed(() => detail.value?.supplements?.[0])

const compareRows = computed(() => {
  const value = detail.value
  const after = value?.previewAfter
  if (!value || !after) return []
  const costOther = (value.costs || []).find((item) => item.costGroup === 'OTHER')?.amount || 0
  return [
    { label: '其他费用', before: `${formatMoney(costOther)} 元`, after: `${formatMoney(after.costOther)} 元` },
    { label: '总成本', before: `${formatMoney(value.costTotal)} 元`, after: `${formatMoney(after.costTotal)} 元` },
    { label: '批次盈亏', before: `${Number(value.profit) >= 0 ? '+' : '−'}${formatMoney(Math.abs(Number(value.profit || 0)))} 元`, after: `${Number(after.profit) >= 0 ? '+' : '−'}${formatMoney(Math.abs(Number(after.profit || 0)))} 元` }
  ]
})

async function switchBatch(batchId: number): Promise<void> {
  if (!batchId) return
  await store.selectBatch(batchId)
  await router.replace({ name: 'WU-E05', query: { batchId: String(batchId) } })
}

function goRecords(costGroup?: string): void {
  if (!detail.value?.batch?.batchId) return
  void router.push({
    name: 'WU-E02',
    query: { batchId: String(detail.value.batch.batchId), ...(costGroup === 'OTHER' ? { type: 'EXPENSE' } : {}) }
  })
}

function goSaleOrder(id?: number): void {
  if (!id) return
  void router.push({ name: 'WU-E04', params: { id: String(id) } })
}

onMounted(() => void store.load(Number(route.query.batchId || 0)))
// 在本页切换养殖场：批次下拉与核算明细换成新场的（地址里的批次属于旧场，不再沿用）
useFarmReload(() => {
  if (route.query.batchId) void router.replace({ query: { ...route.query, batchId: undefined } })
  void store.load()
})
watch(
  () => route.query.batchId,
  (value) => {
    const batchId = Number(value || 0)
    if (batchId && batchId !== store.batchId) void store.selectBatch(batchId)
  }
)
</script>

<template>
  <zw-shell side="user" active="WU-E05" :crumbs="['经营管理', '批次核算']">
    <div v-if="store.status === 'loading' && !detail">
      <v-skeleton-loader type="article, article, table-row@5" />
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="你没有查看经营数据的权限" desc="经营功能只对所有者和场长开放。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="批次核算加载失败" :desc="store.error">
        <v-btn variant="outlined" @click="store.loadOptions()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'empty' || !batch">
      <zw-empty icon="calculate" title="还没有可核算的批次" desc="批次入栏后可以在这里查看累计成本；全部出栏后自动结算并锁定。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-L05' })">去登记入栏</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="detail">
      <zw-page-head :title="`批次核算 · ${batch.batchNo}`" :desc="desc">
        <template #tags>
          <zw-tag v-if="locked" size="small" icon="lock">已结算并锁定</zw-tag>
          <zw-tag v-else type="primary" size="small">截至今天</zw-tag>
        </template>
        <template #actions>
          <v-select
            v-model="selectedBatchId"
            :items="batchItems"
            item-title="title"
            item-value="value"
            class="e05-select"
            density="compact"
            hide-details
            :menu-props="{ maxHeight: 360 }"
          />
          <v-btn variant="outlined" prepend-icon="receipt_long" @click="goRecords()">关联收支</v-btn>
        </template>
      </zw-page-head>

      <zw-notice
        v-if="locked && store.hasPendingSupplements"
        class="mb-4"
        icon="info"
        :title="`有 ${store.pendingSupplements.length} 笔补录费用待确认。`"
      >
        {{ dateText(firstSupplement?.bizDate) }} {{ firstSupplement?.title }}
        {{ formatMoney(firstSupplement?.amount) }} 元。核算已锁定，你确认后才会重新计算。
        <template #action><a class="zw-link" @click="store.openConfirm()">去确认</a></template>
      </zw-notice>
      <zw-notice v-else-if="!locked" class="mb-4" icon="info">
        批次仍在饲养，数据截至今天；全部出栏后系统自动结算并锁定核算。
      </zw-notice>

      <div class="zw-grid-4 mb-4">
        <zw-stat
          :label="store.profitLabel"
          icon="account_balance_wallet"
          :value="`${profitValue > 0 ? '+' : profitValue < 0 ? '−' : ''}${formatMoney(Math.abs(profitValue))}`"
          unit="元"
          :tone="profitValue > 0 ? 'success' : profitValue < 0 ? 'error' : undefined"
        >
          <template #foot>
            <span class="fin-break">销售收入 {{ formatMoney(detail.saleIncome) }} 元 − 总成本 {{ formatMoney(detail.costTotal) }} 元</span>
          </template>
        </zw-stat>

        <zw-stat
          :label="`单${unit}成本`"
          icon="payments"
          :value="detail.costPerHead !== undefined && detail.costPerHead !== null ? formatMoney(detail.costPerHead) : '无数据'"
          :unit="detail.costPerHead !== undefined && detail.costPerHead !== null ? '元' : ''"
        >
          <template #foot><span class="fin-break">{{ store.costPerHeadNote }}</span></template>
        </zw-stat>

        <zw-stat
          :label="`单${unit}盈亏`"
          icon="trending_up"
          :value="detail.profitPerHead !== undefined && detail.profitPerHead !== null
            ? `${Number(detail.profitPerHead) > 0 ? '+' : Number(detail.profitPerHead) < 0 ? '−' : ''}${formatMoney(Math.abs(Number(detail.profitPerHead)))}`
            : '无数据'"
          :unit="detail.profitPerHead !== undefined && detail.profitPerHead !== null ? '元' : ''"
          :tone="Number(detail.profitPerHead) > 0 ? 'success' : Number(detail.profitPerHead) < 0 ? 'error' : undefined"
        >
          <template #foot>
            <span class="fin-break">{{ detail.profitPerHead === null || detail.profitPerHead === undefined ? '出栏数为 0，出栏后计算' : store.costPerHeadNote }}</span>
          </template>
        </zw-stat>

        <zw-stat
          label="料肉比"
          icon="scale"
          :value="detail.fcr !== undefined && detail.fcr !== null ? Number(detail.fcr).toFixed(2) : '无数据'"
        >
          <template #foot>
            <span class="fin-break">
              饲料 {{ formatNumber(detail.feedKg) }} 千克 ÷ 净增重 {{ formatNumber(detail.netGainKg) }} 千克
            </span>
          </template>
        </zw-stat>
      </div>

      <div class="zw-grid-2">
        <zw-card title="收入与成本" :sub="locked ? '已结算' : '截至今天'">
          <div class="e05-bars">
            <div class="e05-bar-row">
              <span class="e05-bar-label">销售收入</span>
              <div class="e05-bar-track">
                <i class="e05-bar-fill e05-bar-fill--income" :style="{ width: `${incomeWidth}%` }" />
              </div>
              <span class="e05-bar-v num">{{ formatMoney(detail.saleIncome) }} 元</span>
            </div>
            <div class="e05-bar-row">
              <span class="e05-bar-label">总成本</span>
              <div class="e05-bar-track">
                <i class="e05-bar-fill e05-bar-fill--cost" :style="{ width: `${costWidth}%` }" />
              </div>
              <span class="e05-bar-v num">{{ formatMoney(detail.costTotal) }} 元</span>
            </div>
          </div>

          <div class="e05-sale-list">
            <div
              v-for="order in detail.saleOrders || []"
              :key="order.id"
              class="e05-sale-row"
              @click="goSaleOrder(order.id)"
            >
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="local_shipping" :size="18" /></span>
              <div class="e05-sale-main">
                <div class="zw-cell-main">出栏单 {{ order.saleDate ? dateText(order.saleDate) : '' }}</div>
                <div class="zw-cell-sub num">
                  {{ formatNumber(order.quantity) }} {{ unit }} ·
                  {{ formatNumber(order.totalWeight, 1) }} 千克 · 每千克 {{ priceText(order.unitPrice) }} 元
                </div>
              </div>
              <zw-tag :type="paymentTone(order.paymentStatus)" size="small" :icon="paymentIcon(order.paymentStatus)">
                {{ order.paymentStatus === 'PAID' ? '已收款' : order.paymentStatus === 'PARTIAL' ? '部分收款' : '未收款' }}
              </zw-tag>
              <span class="e05-sale-amount num">{{ formatMoney(order.saleAmount) }} 元</span>
            </div>
            <div v-if="!detail.saleOrders?.length" class="e05-sale-empty">
              <div class="zw-faint">还没有出栏单</div>
              <div v-if="store.withdrawalHint" class="zw-faint body-s">{{ store.withdrawalHint }}</div>
            </div>
          </div>

          <div class="e05-note zw-faint body-s">
            销售收入按出栏单金额计算，不论货款是否收齐<template v-if="Number(detail.receivedAmount)">（已收 {{ formatMoney(detail.receivedAmount) }} 元）</template>。
          </div>
        </zw-card>

        <zw-card title="成本构成" :sub="`总成本 ${formatMoney(detail.costTotal)} 元`">
          <template #extra><a class="zw-link" @click="goRecords()">查看关联支出</a></template>
          <div v-if="costItems.length" class="e05-cost">
            <zw-donut
              :items="donutItems"
              :value="formatMoney(detail.costTotal)"
              title="总成本（元）"
              :size="148"
              :thickness="20"
              :legend="false"
            />
            <ul class="e05-cost-list">
              <li v-for="item in costItems" :key="item.costGroup" class="e05-cost-row" @click="goRecords(item.costGroup)">
                <i class="e05-cost-dot" :style="{ background: `var(--${item.color})` }" />
                <span class="e05-cost-name">{{ item.costGroupText }}</span>
                <span class="e05-cost-note">{{ costNoteText(item.note, unit) || (item.costGroup === 'FEED' ? `${formatNumber(detail.feedKg)} 千克` : '') }}</span>
                <span class="e05-cost-amount num">{{ formatMoney(item.amountValue) }} 元</span>
                <span class="e05-cost-pct num">{{ item.percent }}</span>
              </li>
            </ul>
          </div>
          <zw-empty v-else icon="donut_large" title="还没有成本记录" desc="饲料入库、用药和关联支出会计入批次成本。" />
        </zw-card>
      </div>

      <zw-card title="核算记录" :sub="locked ? '结算、补录与重新计算' : '最近计入成本的记录'" flush class="mt-4">
        <div class="e05-timeline-wrap">
          <zw-timeline :items="timelineItems" />
        </div>
      </zw-card>
    </template>

    <zw-dialog
      v-model="store.confirmOpen"
      :title="`确认补录的${firstSupplement?.title || '费用'}？`"
      icon="pending_actions"
      tone="warning"
      width="480"
    >
      <p v-if="store.pendingSupplements.length === 1">
        {{ dateText(firstSupplement?.bizDate) }} {{ firstSupplement?.title }} {{ formatMoney(firstSupplement?.amount) }} 元将计入其他费用，批次盈亏变为
        <strong>{{ Number(detail?.previewAfter?.profit || 0) >= 0 ? '+' : '−' }}{{ formatMoney(Math.abs(Number(detail?.previewAfter?.profit || 0))) }} 元</strong>。
      </p>
      <p v-else>确认后这 {{ store.pendingSupplements.length }} 笔补录费用会计入批次成本，并重新计算批次盈亏。</p>
      <zw-compare v-if="compareRows.length" :rows="compareRows" before-title="当前" after-title="确认后" />
      <div v-if="store.confirmError" class="e05-confirm-error">{{ store.confirmError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="store.confirmBusy" @click="store.confirmOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.confirmBusy" @click="store.confirm()">确认并重算</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.e05-select { width: 320px; }
.fin-break { flex-basis: 100%; }
.e05-bars { display: flex; flex-direction: column; gap: 14px; padding: 4px 0 16px; }
.e05-bar-row { display: flex; align-items: center; gap: 12px; }
.e05-bar-label { width: 72px; font-size: 14px; color: var(--text-2); }
.e05-bar-track { flex: 1; height: 16px; border-radius: 2px; background: var(--fill-2); overflow: hidden; }
.e05-bar-fill { display: block; height: 100%; border-radius: 2px; }
.e05-bar-fill--income { background: var(--chart-1); }
.e05-bar-fill--cost { background: var(--chart-2); }
.e05-bar-v { width: 120px; text-align: right; font-size: 14px; font-weight: 500; }
.e05-sale-list { border-top: 1px solid var(--line-1); }
.e05-sale-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--line-1); cursor: pointer; }
.e05-sale-row:hover { background: var(--fill-1); }
.e05-sale-main { flex: 1; min-width: 0; }
.e05-sale-amount { font-weight: 500; white-space: nowrap; }
.e05-sale-empty { padding: 20px 0; text-align: center; }
.e05-note { padding-top: 10px; }
.e05-cost { display: flex; gap: 20px; align-items: center;
  --e10-o1: #C25E00; --e10-o2: #CE7E33; --e10-o3: #D99B61; --e10-o4: #E3B58A; --e10-o5: #EAC8A8; --e10-o6: #F0D8C2; }
.e05-cost-list { list-style: none; margin: 0; padding: 0; flex: 1; min-width: 0; }
.e05-cost-row { display: flex; align-items: center; gap: 8px; height: 40px; cursor: pointer; }
.e05-cost-row:hover { background: var(--fill-1); }
.e05-cost-dot { width: 10px; height: 10px; border-radius: 2px; flex: 0 0 10px; }
.e05-cost-name { width: 68px; font-size: 14px; }
.e05-cost-note { flex: 1; min-width: 0; font-size: 12px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.e05-cost-amount { width: 96px; text-align: right; font-size: 14px; }
.e05-cost-pct { width: 52px; text-align: right; font-size: 12px; color: var(--text-3); }
.e05-timeline-wrap { padding: 16px 20px; max-height: 260px; overflow: auto; }
.e05-confirm-error { margin-top: 8px; font-size: 12px; color: var(--error); }
</style>

