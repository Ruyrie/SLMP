<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import type { DataTableHeader } from 'vuetify'
import { formatMoney, formatNumber, formatPercent } from '@zmt/shared'
import { useFinanceOverviewStore } from '@/stores/finance/overview'
import { useFarmReload } from '@/composables/useFarmReload'
import { useFarmStore } from '@/stores/farm'
import { useReceiveStore } from '@/stores/finance/receive'
import { dateText, dueTone, monthRangeText, priceText, reminderRule } from '@/stores/finance/shared'
import ReceiveDialog from './components/ReceiveDialog.vue'

const store = useFinanceOverviewStore()
const farm = useFarmStore()
const receive = useReceiveStore()
const router = useRouter()
const { data, receivables } = storeToRefs(store)
const receivableCard = ref<HTMLElement | null>(null)

const headers: DataTableHeader[] = [
  { title: '出栏单', key: 'batchNo', sortable: false },
  { title: '出栏', key: 'saleDate', sortable: false, width: 150 },
  { title: '约定收款', key: 'due', sortable: false, width: 206 },
  { title: '销售金额', key: 'saleAmount', sortable: false, align: 'end', width: 120 },
  { title: '已收', key: 'receivedAmount', sortable: false, align: 'end', width: 116 },
  { title: '未收', key: 'unpaidAmount', sortable: false, align: 'end', width: 108 },
  { title: '收款进度', key: 'progress', sortable: false, width: 146 },
  { title: '操作', key: 'actions', sortable: false, align: 'end', width: 124 }
]

const dueTabs = computed(() => {
  const counts = receivables.value?.tabCounts || {}
  return [
    { label: '全部', value: 'ALL', count: Number(counts.ALL ?? 0) },
    { label: '已逾期', value: 'OVERDUE', count: Number(counts.OVERDUE ?? 0) },
    { label: '7 天内到期', value: 'DUE_7D', count: Number(counts.DUE_7D ?? 0) }
  ]
})

const ruleText = computed(() => {
  const text = reminderRule(receivables.value?.remindRuleText)
  if (farm.role === 'manager') return text.replace('提醒你和场长', '提醒你、所有者和其他场长')
  return text
})

const monthBalance = computed(() => data.value?.monthBalance || {})
const receivable = computed(() => data.value?.receivable || {})
const priceCard = computed(() => data.value?.priceCard)
const monthSales = computed(() => data.value?.monthSales || {})
const monthRecords = computed(() => data.value?.monthRecords || {})
const summaryPeriodText = computed(() =>
  monthRangeText(store.summary?.periodStart, store.summary?.periodEnd)
)

const balanceDelta = computed(() => {
  const balance = monthBalance.value
  const rate = balance.balanceMomRate
  if (!balance.comparable || rate === undefined || rate === null) {
    return { text: '较上月同期无法比较', tone: 'muted', arrow: false }
  }
  const n = Number(rate)
  return {
    text: `较上月同期 ${n >= 0 ? '+' : '−'}${(Math.abs(n) * 100).toFixed(1)}%`,
    tone: n >= 0 ? 'success' : 'error',
    arrow: true
  }
})

const priceChange = computed(() => {
  const card = priceCard.value
  if (!card) return { text: '', tone: 'muted' as const, arrow: false }
  const rate = Number(card.changeRate || 0)
  if (!Number.isFinite(rate) || Math.abs(rate) < 0.00005) {
    return { text: '持平', tone: 'muted' as const, arrow: false }
  }
  const up = rate > 0
  return {
    text: `${up ? '涨' : '跌'} ${(Math.abs(rate) * 100).toFixed(1)}%`,
    tone: up ? ('success' as const) : ('error' as const),
    arrow: true
  }
})

const expenseShares = computed(() => {
  const rows = store.summary?.expenseShares || []
  return rows.map((row, index) => ({
    label: row.name,
    value: Number(row.amount || 0),
    color: `e10-o${(index % 6) + 1}`,
    text: `${formatMoney(row.amount)} 元 · ${formatPercent(row.shareRate)}`
  }))
})

const tools = computed(() => [
  {
    icon: 'payments',
    title: '收支记账',
    sub: `本月 ${formatNumber(monthRecords.value.total || 0)} 笔`,
    to: 'WU-E02'
  },
  {
    icon: 'local_shipping',
    title: '出栏单',
    sub: (monthSales.value.orderCount || 0) > 0 ? `本月出栏 ${monthSales.value.orderCount} 张` : '本月暂无出栏',
    to: 'WU-E03'
  },
  {
    icon: 'calculate',
    title: '批次核算',
    sub:
      (data.value?.pendingSupplementCount || 0) > 0
        ? `${data.value?.pendingSupplementCount} 笔补录待确认`
        : '查看批次盈亏',
    to: 'WU-E05'
  },
  { icon: 'trending_up', title: '市场行情', sub: '出栏价 · 种苗价', to: 'WU-E06' },
  { icon: 'bar_chart', title: '经营报表', sub: '周报 · 月报', to: 'WU-E07' }
])

const trendYMin = computed(() => {
  const values = store.chartValues.filter((item): item is number => item !== null && Number.isFinite(Number(item)))
  if (!values.length) return undefined
  return Math.floor(Math.min(...values) * 10 - 2) / 10
})

const trendYMax = computed(() => {
  const values = store.chartValues.filter((item): item is number => item !== null && Number.isFinite(Number(item)))
  if (!values.length) return undefined
  return Math.ceil(Math.max(...values) * 10 + 2) / 10
})

function go(name: string, query?: Record<string, string>): void {
  void router.push({ name, query })
}

function goRecords(): void {
  void router.push({ name: 'WU-E02', query: { action: 'create' } })
}

function goSales(): void {
  void router.push({ name: 'WU-E03' })
}

function goMarket(): void {
  void router.push({ name: 'WU-E06' })
}

function goSalesThisMonth(): void {
  void router.push({ name: 'WU-E03', query: { month: store.summary?.periodStart?.slice(0, 7) || '' } })
}

function scrollReceivable(): void {
  receivableCard.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function openReceive(item: import('@/api/finance').SaleOrderItem): void {
  receive.show(item)
}

function goDetail(id?: number): void {
  if (!id) return
  void router.push({ name: 'WU-E04', params: { id: String(id) } })
}

function dueText(item: { dueDate?: string; effectiveDueDate?: string }): string {
  if (item.dueDate) return dateText(item.dueDate)
  if (item.effectiveDueDate) return `未填写 · 按 ${dateText(item.effectiveDueDate)} 提醒`
  return '—'
}

function onReceiveDone(): void {
  void store.afterReceipt()
}

onMounted(() => void store.load())
useFarmReload(() => {
  void store.load()
})
</script>

<template>
  <zw-shell side="user" active="WU-E01" :crumbs="['经营管理', '经营概览']">
    <zw-page-head title="经营概览" :desc="store.todayDesc">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="edit_note" @click="goRecords">记一笔</v-btn>
        <v-btn color="primary" prepend-icon="add" @click="goSales">登记出栏单</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading'">
      <v-skeleton-loader type="article, article, table-row@5" />
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="你没有查看经营数据的权限" desc="经营功能只对所有者和场长开放。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="经营数据加载失败" :desc="store.error || '网络连接不稳定，请检查网络后重新加载。'">
        <v-btn color="primary" variant="outlined" @click="store.load()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="data">
      <div class="zw-grid-4 mb-4">
        <zw-stat
          class="e01-click"
          label="本月结余"
          icon="account_balance_wallet"
          :value="formatMoney(monthBalance.balanceAmount)"
          unit="元"
          tone="primary"
          @click="go('WU-E02')"
        >
          <template #foot>
            <span class="e01-delta" :class="`e01-delta--${balanceDelta.tone}`">
              <zw-icon v-if="balanceDelta.arrow" :name="Number(monthBalance.balanceMomRate) >= 0 ? 'arrow_upward' : 'arrow_downward'" :size="14" />
              {{ balanceDelta.text }}
            </span>
            <span class="e01-break">收入 {{ formatMoney(monthBalance.income) }} 元 · 支出 {{ formatMoney(monthBalance.expenseAmount) }} 元</span>
          </template>
        </zw-stat>

        <zw-stat
          class="e01-click"
          label="待收款合计"
          icon="pending_actions"
          :value="formatMoney(receivable.totalUnpaidAmount)"
          unit="元"
          @click="scrollReceivable"
        >
          <template #foot>
            <span v-if="receivable.overdueCount" class="e01-late">
              <zw-icon name="schedule" :size="14" />{{ receivable.orderCount || 0 }} 张 · {{ receivable.overdueCount }} 张已逾期
            </span>
            <span v-else class="zw-faint">
              {{ receivable.orderCount || 0 }} 张 · 最近 {{ dateText(receivable.nearestDueDate) }}到期
            </span>
            <span class="e01-break">
              {{ receivable.overdueCount ? `已逾期未收 ${formatMoney(receivable.overdueUnpaidAmount)} 元` : '没有逾期的待收款' }}
            </span>
          </template>
        </zw-stat>

        <zw-stat
          class="e01-click"
          :label="`${priceCard?.categoryName || '当前品类'}出栏价`"
          icon="trending_up"
          :value="priceText(priceCard?.price)"
          :unit="priceCard?.priceUnitText || '元/千克'"
          @click="goMarket"
        >
          <template #foot>
            <span class="e01-delta" :class="`e01-delta--${priceChange.tone}`">
              <zw-icon v-if="priceChange.arrow" :name="Number(priceCard?.changeRate) >= 0 ? 'arrow_upward' : 'arrow_downward'" :size="14" />
              {{ priceChange.text }}
            </span>
            <span>{{ priceCard?.provinceName }}</span>
            <span v-if="priceCard" class="e01-break">
              {{ dateText(priceCard.priceDate) }}更新<template v-if="priceCard.compareDate">
                · 较 {{ dateText(priceCard.compareDate) }} {{ priceText(priceCard.comparePrice) }} 元</template>
            </span>
          </template>
        </zw-stat>

        <zw-stat
          class="e01-click"
          label="本月出栏"
          icon="local_shipping"
          :value="formatNumber(monthSales.orderCount || 0)"
          unit="张"
          @click="goSalesThisMonth"
        >
          <template #foot>
            <span v-if="monthSales.orderCount">销售金额 {{ formatMoney(monthSales.saleAmount) }} 元</span>
            <span v-else>本月暂无出栏</span>
            <span class="e01-break">按出栏日期归月 · 已作废的不计入</span>
          </template>
        </zw-stat>
      </div>

      <div class="e01-tools">
        <div v-for="tool in tools" :key="tool.title" class="e01-tool" @click="go(tool.to)">
          <span class="zw-ico-tile zw-ico-tile--primary zw-ico-tile--lg"><zw-icon :name="tool.icon" :size="20" /></span>
          <div class="e01-tool-txt">
            <div class="e01-tool-t">{{ tool.title }}</div>
            <div class="e01-tool-s">{{ tool.sub }}</div>
          </div>
          <zw-icon name="chevron_right" :size="18" class="e01-chev" />
        </div>
      </div>

      <div ref="receivableCard" class="e01-receivables">
        <zw-card
          title="待收款"
          :sub="
            receivables && receivables.total
              ? `${receivables.total} 张出栏单未收齐，已逾期的在前`
              : '还没有待收款的出栏单'
          "
          flush
        >
          <template #extra>
            <zw-seg size="small" :model-value="store.dueFilter" :items="dueTabs" @update:model-value="store.setDueFilter($event as string)" />
            <a class="zw-link ml-3" @click="goSales">全部出栏单</a>
          </template>

          <div v-if="store.receivableStatus === 'loading'" class="e01-card-pad">
            <v-skeleton-loader type="table-row@3" />
          </div>
          <div v-else-if="store.receivableStatus === 'error'" class="e01-card-pad">
            <zw-empty icon="cloud_off" tone="error" title="待收款加载失败" :desc="store.receivableError">
              <v-btn variant="outlined" @click="store.loadReceivables()">重新加载</v-btn>
            </zw-empty>
          </div>
          <zw-empty
            v-else-if="!receivables || !receivables.list.length"
            icon="task_alt"
            :title="
              store.dueFilter === 'OVERDUE'
                ? '没有已逾期的出栏单'
                : store.dueFilter === 'DUE_7D'
                  ? '7 天内没有到期的出栏单'
                  : '没有待收款的出栏单'
            "
            desc="登记出栏单后，未收齐的款项会显示在这里；已收齐的可以点“全部出栏单”查看。"
          />
          <template v-else>
            <v-data-table :headers="headers" :items="receivables.list" item-value="id" hide-default-footer class="e01-table">
              <template #item.batchNo="{ item }">
                <div class="py-2">
                  <div class="zw-cell-main num">{{ item.batchNo }}</div>
                  <div class="zw-cell-sub">{{ item.penName }} · {{ item.buyerName }}</div>
                </div>
              </template>
              <template #item.saleDate="{ item }">
                <div>{{ dateText(item.saleDate) }}</div>
                <div class="zw-cell-sub num">
                  {{ formatNumber(item.quantity) }} {{ item.countUnitText || '头' }} ·
                  {{ formatNumber(item.totalWeight, 1) }} 千克
                </div>
              </template>
              <template #item.due="{ item }">
                <div class="e01-due">
                  <span>{{ dueText(item) }}</span>
                  <zw-tag
                    v-if="item.dueState === 'OVERDUE' || item.dueState === 'DUE_SOON'"
                    :type="dueTone(item.dueState)"
                    :icon="item.dueState === 'OVERDUE' ? 'schedule' : ''"
                    size="small"
                  >{{ item.dueStateText }}</zw-tag>
                </div>
              </template>
              <template #item.saleAmount="{ item }">
                <span class="num e01-nowrap">{{ formatMoney(item.saleAmount) }} 元</span>
              </template>
              <template #item.receivedAmount="{ item }">
                <span class="num e01-nowrap">{{ formatMoney(item.receivedAmount) }} 元</span>
              </template>
              <template #item.unpaidAmount="{ item }">
                <span class="num e01-left e01-nowrap" :class="{ late: item.dueState === 'OVERDUE' }">{{ formatMoney(item.unpaidAmount) }} 元</span>
              </template>
              <template #item.progress="{ item }">
                <div class="e01-prog">
                  <v-progress-linear :model-value="Number(item.progressRate || 0) * 100" height="6" rounded color="primary" />
                  <span class="num">{{ formatPercent(item.progressRate) }}</span>
                </div>
              </template>
              <template #item.actions="{ item }">
                <span class="zw-cell-actions">
                  <a @click="openReceive(item)">登记收款</a>
                  <a @click="goDetail(item.id)">详情</a>
                </span>
              </template>
            </v-data-table>
            <div class="zw-table-foot zw-table-foot--in">
              <span class="e01-rule"><zw-icon name="notifications" :size="16" />{{ ruleText }}</span>
            </div>
          </template>
        </zw-card>
      </div>

      <div class="zw-grid-2 mt-4">
        <zw-card title="本月支出构成" :sub="summaryPeriodText">
          <template #extra><a class="zw-link" @click="go('WU-E02')">收支记账</a></template>
          <div v-if="expenseShares.length" class="e01-donut-wrap">
            <zw-donut
              :items="expenseShares"
              :value="formatMoney(monthBalance.expenseAmount)"
              :title="`${store.monthNumber}月支出（元）`"
              :size="160"
              :thickness="22"
            />
          </div>
          <zw-empty
            v-else-if="store.summaryStatus !== 'loading'"
            icon="donut_large"
            :title="`${store.monthNumber}月还没有收支记录`"
            desc="点“记一笔”记录开销，物资入库勾选同步记账后也会自动生成。"
          >
            <v-btn color="primary" variant="outlined" @click="goRecords">记一笔</v-btn>
          </zw-empty>
          <v-skeleton-loader v-else type="image, article" />
        </zw-card>

        <zw-card
          :title="`${priceCard?.categoryName || '当前品类'}出栏价 · 近 30 天`"
          :sub="`${priceCard?.provinceName || ''} · 全部品种`"
        >
          <template #extra><a class="zw-link" @click="goMarket">市场行情</a></template>
          <div v-if="priceCard" class="e01-price-head">
            <span class="e01-price">{{ priceText(priceCard.price) }}</span>
            <span class="zw-muted">{{ priceCard.priceUnitText || '元/千克' }}</span>
            <span class="e01-delta ml-2" :class="`e01-delta--${priceChange.tone}`">
              <zw-icon v-if="priceChange.arrow" :name="Number(priceCard.changeRate) >= 0 ? 'arrow_upward' : 'arrow_downward'" :size="14" />
              {{ priceChange.text }}
            </span>
            <span v-if="priceCard.compareDate" class="zw-faint body-s">
              较 {{ dateText(priceCard.compareDate) }} {{ priceText(priceCard.comparePrice) }} 元
            </span>
          </div>
          <v-skeleton-loader v-if="store.chartStatus === 'loading'" type="image" />
          <zw-empty
            v-else-if="store.chartStatus !== 'ready'"
            icon="show_chart"
            title="暂无走势数据"
            :desc="store.chartError || '当前地区还没有发布出栏价。'"
          />
          <zw-line-chart
            v-else
            :labels="store.chartLabels"
            :series="[{ name: '出栏价', data: store.chartValues, area: true }]"
            :height="140"
            :ticks="3"
            :x-every="6"
            :dots="false"
            :legend="false"
            :y-min="trendYMin"
            :y-max="trendYMax"
            :unit="priceCard?.priceUnitText || '元/千克'"
          />
        </zw-card>
      </div>
    </template>

    <ReceiveDialog @done="onReceiveDone" />
  </zw-shell>
</template>

<style scoped>
.e01-click { cursor: pointer; transition: border-color .2s; }
.e01-click:hover { border-color: var(--primary); }
.e01-break { flex-basis: 100%; }
.e01-delta { display: inline-flex; align-items: center; gap: 2px; font-size: 12px; }
.e01-delta--success { color: var(--success); }
.e01-delta--error { color: var(--error); }
.e01-delta--muted { color: var(--text-3); }
.e01-late { display: inline-flex; align-items: center; gap: 2px; color: var(--error); }
.e01-tools { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; margin-bottom: 16px; }
.e01-tool { display: flex; align-items: center; gap: 12px; height: 72px; padding: 0 16px; background: var(--bg-card); border: 1px solid var(--line-1); border-radius: var(--radius-sm); cursor: pointer; min-width: 0; }
.e01-tool:hover { border-color: var(--primary); }
.e01-tool-txt { min-width: 0; }
.e01-tool-t { font-size: 14px; line-height: 22px; font-weight: 500; color: var(--text-1); }
.e01-tool-s { font-size: 12px; line-height: 20px; color: var(--text-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.e01-chev { margin-left: auto; color: var(--text-3); }
.e01-receivables { margin-top: 16px; scroll-margin-top: 16px; }
.e01-card-pad { padding: 16px 20px; }
.e01-table :deep(td) { white-space: nowrap; }
.e01-due { display: flex; align-items: center; gap: 8px; }
.e01-nowrap { white-space: nowrap; }
.e01-prog { display: flex; align-items: center; gap: 8px; }
.e01-prog :deep(.v-progress-linear) { flex: 1; }
.e01-prog span { width: 44px; text-align: right; font-size: 12px; color: var(--text-2); }
.e01-left { font-weight: 600; }
.e01-left.late { color: var(--error); }
.e01-rule { display: flex; align-items: center; gap: 6px; font-size: 12px; line-height: 20px; color: var(--text-3); }
.e01-price-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; }
.e01-price { font-size: 24px; line-height: 32px; font-weight: 600; color: var(--text-1); }
.e01-donut-wrap {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
  --e10-o1: #C25E00;
  --e10-o2: #CE7E33;
  --e10-o3: #D99B61;
  --e10-o4: #E3B58A;
  --e10-o5: #EAC8A8;
  --e10-o6: #F0D8C2;
}
</style>

