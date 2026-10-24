<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useReportsStore, type ReportMetric } from '@/stores/finance/reports'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { formatMoney, formatNumber, formatPercent } from '@zmt/shared'
import { dateText, dateTimeText, monthRangeText, signedPercent, trendTone, type Tone } from '@/stores/finance/shared'

const store = useReportsStore()
const farm = useFarmStore()
const router = useRouter()

const metricLabels: Record<ReportMetric, string> = {
  STOCK: '期末存栏',
  MORTALITY: '死亡率',
  FEED: '饲料用量',
  BALANCE: '收支结余'
}

const reportTypeItems = [
  { label: '周报', value: 'WEEKLY' },
  { label: '月报', value: 'MONTHLY' },
  { label: '本期至今', value: 'TO_DATE' }
]

const rangeItems = [
  { label: '整场', value: 'FARM' },
  { label: '按品类', value: 'CATEGORY' },
  { label: '按批次', value: 'BATCH' }
]

const farmItems = computed(() => {
  const rows = farm.farms
    .filter((item) => (item.role === 'OWNER' || item.role === 'MANAGER') && item.status === 'NORMAL')
    .map((item) => ({ title: item.name, value: item.farm_id }))
  if (rows.length > 1) rows.push({ title: '全部养殖场合计', value: 0 })
  return rows
})

const report = computed(() => store.report)
const stockMetric = computed(() => store.stockMetric)
const extraStocks = computed(() => (report.value?.metrics?.stock || []).slice(1))
const profitItems = computed(() => store.profitItems)
const runningBatches = computed(() => report.value?.runningBatches || [])

const periodText = computed(() => {
  const period = report.value?.period
  if (!period) return ''
  return `统计期 ${monthRangeText(period.start, period.end)}`
})

const summarySub = computed(() => {
  const period = report.value?.period
  const generated = store.summary?.generatedAt
  if (!period) return ''
  return `${period.label || monthRangeText(period.start, period.end)}${generated ? ` · ${dateTimeText(generated)} 生成` : ''}`
})

const stockDelta = computed(() => {
  const metric = stockMetric.value
  if (!metric) return ''
  const change = Number(metric.change || 0)
  return `较期初 ${change > 0 ? '+' : change < 0 ? '−' : ''}${formatNumber(Math.abs(change))} · 期初 ${formatNumber(metric.begin)} ${metric.countUnitText || ''}`
})

const mortalityDelta = computed(() => {
  const mortality = report.value?.metrics?.mortality
  if (!mortality || mortality.rate === undefined || mortality.rate === null) return '暂无死淘数据'
  if (mortality.comparable && mortality.changePoint !== undefined && mortality.changePoint !== null) {
    const point = Number(mortality.changePoint) * 100
    return `${point >= 0 ? '↑' : '↓'} ${Math.abs(point).toFixed(1)} 个百分点 · 死淘 ${formatNumber(mortality.deathCount)} 头`
  }
  return `死淘 ${formatNumber(mortality.deathCount)} 头`
})

const feedDelta = computed(() => {
  const feed = report.value?.metrics?.feed
  if (!feed) return ''
  if (feed.comparable && feed.changeRate !== undefined && feed.changeRate !== null) {
    return `${signedPercent(feed.changeRate)} · 较上月同期`
  }
  return '较上月同期无法比较'
})

const balanceDelta = computed(() => {
  const balance = report.value?.metrics?.balance
  if (!balance) return ''
  if (balance.comparable && balance.changeRate !== undefined && balance.changeRate !== null) {
    return `${signedPercent(balance.changeRate)} · 较上月同期`
  }
  return '较上月同期无法比较'
})

const trendLabels = computed(
  () =>
    (store.trend?.points || []).map((point) => {
      const date = String(point.date || '')
      const [, month, day] = date.split('-')
      return month && day ? `${Number(month)}/${Number(day)}` : date
    })
)

const trendValues = computed(() => (store.trend?.points || []).map((point) => Number(point.value || 0)))

const trendSub = computed(() => {
  const period = report.value?.period
  if (!period) return ''
  return `${monthRangeText(period.start, period.end)} · 点上方指标切换`
})

const equation = computed(() => store.trend?.equation)

const trendIsBar = computed(() => store.metric === 'FEED')

const profitTotal = computed(() => Number(report.value?.batchProfits?.profitTotal || 0))

function metricTone(metric: ReportMetric): Tone | 'primary' {
  if (metric === 'BALANCE') {
    const value = Number(report.value?.metrics?.balance?.amount || 0)
    return value > 0 ? 'success' : value < 0 ? 'error' : 'muted'
  }
  return 'muted'
}

function selectFarm(value: number): void {
  if (value === 0) {
    if (farm.viewAll) return
    farm.setViewAll(true)
    void store.setScope('ALL')
    return
  }
  if (farm.viewAll) farm.setViewAll(false)
  if (value !== farm.currentFarmId) {
    void farm.switchTo(value)
    store.scope = ''
  }
}

function selectRange(value: string): void {
  void store.setRange(value as 'FARM' | 'CATEGORY' | 'BATCH')
}

function selectRangeRef(value: number): void {
  void store.setRange(store.rangeType, value)
}

function goBatch(batchId?: number): void {
  if (!batchId) return
  void router.push({ name: 'WU-E05', query: { batchId: String(batchId) } })
}

function goBatchProfile(batchId?: number): void {
  if (!batchId) return
  void router.push({ name: 'WU-L04', params: { batchId: String(batchId) } })
}

function barWidth(profit?: number): number {
  if (!store.maxProfit) return 0
  return Math.min(100, (Math.abs(Number(profit || 0)) / store.maxProfit) * 100)
}

function onFarmReload(): void {
  void store.load()
}

function studyTone(value?: number): string {
  if (value === undefined || value === null) return 'muted'
  return Number(value) > 0 ? 'error' : Number(value) < 0 ? 'success' : 'muted'
}

onMounted(() => {
  if (farm.viewAll) store.scope = 'ALL'
  void store.load()
})

useFarmReload(onFarmReload)
onBeforeUnmount(() => window.clearTimeout(store.summaryTimer))
</script>

<template>
  <zw-shell side="user" active="WU-E07" :crumbs="['经营管理', '经营报表']">
    <zw-page-head title="经营报表" desc="每周一生成上周周报、每月 1 日生成上月月报；本期至今随记录实时更新">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="download" :disabled="!store.canExport" @click="store.openExport()">导出</v-btn>
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
      <zw-empty icon="cloud_off" tone="error" title="经营报表加载失败" :desc="store.error">
        <v-btn variant="outlined" @click="store.load()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <zw-notice
        v-if="report?.dataCheck?.hasIssue"
        type="warning"
        icon="rule"
        :title="`数据待核对：${report.dataCheck.batchCount || 0} 个批次的期初存栏加期间变动与期末存栏不一致，相关指标可能不准确。修正记录后报表会自动重新计算。`"
        class="mb-4"
      >
        <div class="e07-check-grid">
          <div
            v-for="item in (report.dataCheck.items || []).slice(0, 3)"
            :key="item.batchId"
            class="e07-check-item"
            @click="goBatchProfile(item.batchId)"
          >
            <div class="e07-check-main">
              <div class="zw-cell-main num">{{ item.batchNo }} · {{ item.penName }}</div>
              <div class="zw-cell-sub">期初加变动 {{ item.expected }} {{ stockMetric?.countUnitText || '头' }} · 期末 {{ item.actual }} {{ stockMetric?.countUnitText || '头' }}</div>
            </div>
            <zw-tag type="warning" size="small">差 {{ Math.abs(Number(item.diff || 0)) }} {{ stockMetric?.countUnitText || '头' }}</zw-tag>
            <zw-icon name="chevron_right" :size="18" color="text-3" />
          </div>
          <div v-if="(report.dataCheck.items || []).length > 3" class="e07-check-more">
            还有 {{ (report.dataCheck.items || []).length - 3 }} 个批次
          </div>
        </div>
        <a class="zw-link" @click="report.dataCheck.items?.length === 1 ? goBatchProfile(report.dataCheck.items[0].batchId) : goBatchProfile(report.dataCheck.items?.[0]?.batchId)">查看批次记录</a>
      </zw-notice>

      <div class="e07-filters">
        <zw-seg
          :model-value="store.reportType"
          :items="reportTypeItems"
          @update:model-value="store.setReportType($event as 'TO_DATE' | 'WEEKLY' | 'MONTHLY')"
        />
        <v-select
          :model-value="farm.viewAll ? 0 : farm.currentFarmId"
          :items="farmItems"
          item-title="title"
          item-value="value"
          class="e07-farm"
          density="compact"
          hide-details
          prepend-inner-icon="domain"
          @update:model-value="selectFarm(Number($event || 0))"
        />
        <v-select
          :model-value="store.rangeType"
          :items="rangeItems"
          item-title="label"
          item-value="value"
          class="e07-range"
          density="compact"
          hide-details
          @update:model-value="selectRange(String($event || 'FARM'))"
        />
        <v-select
          v-if="store.rangeType === 'CATEGORY'"
          :model-value="store.rangeRefId"
          :items="store.categories"
          item-title="name"
          item-value="id"
          class="e07-range"
          density="compact"
          hide-details
          @update:model-value="selectRangeRef(Number($event || 0))"
        />
        <v-select
          v-if="store.rangeType === 'BATCH'"
          :model-value="store.rangeRefId"
          :items="store.batches"
          item-title="batchNo"
          item-value="batchId"
          class="e07-farm"
          density="compact"
          hide-details
          @update:model-value="selectRangeRef(Number($event || 0))"
        />
        <v-select
          v-if="store.reportType !== 'TO_DATE'"
          :model-value="store.periodStart"
          :items="store.periods"
          item-title="label"
          item-value="periodStart"
          class="e07-period"
          density="compact"
          hide-details
          :disabled="!store.periods.length"
          @update:model-value="store.setPeriod(String($event || ''))"
        />
        <div class="e07-filters-right">
          <span class="e07-period-text">{{ periodText }}</span>
          <span v-if="report?.period?.toDate" class="zw-faint body-s">截至今天</span>
        </div>
      </div>

      <zw-notice v-if="farm.viewAll" class="mb-4" icon="info">
        只合计你担任所有者或场长的养殖场。
        <template v-if="report?.staffFarmNames && report.staffFarmNames.length">{{ report.staffFarmNames.join('、') }}你是员工，不计入。</template>
      </zw-notice>

      <zw-notice
        v-if="store.status === 'empty' && store.reportType !== 'TO_DATE' && !store.periodStart"
        type="warning"
        icon="calendar_month"
        :title="`${store.reportTypeText}还没有生成`"
      >
        {{ store.reportType === 'WEEKLY' ? '本周的周报将在' : '本月的月报将在' }}
        {{ dateText(store.nextGenerationDate) }}生成，可以先查看本期至今。
        <template #action><a class="zw-link" @click="store.setReportType('TO_DATE')">查看本期至今</a></template>
      </zw-notice>

      <zw-notice
        v-else-if="report?.empty && store.status === 'empty'"
        icon="info"
        title="本期还没有经营数据"
      >
        记账、登记出栏或批次入栏后，报表会自动更新。
      </zw-notice>

      <template v-if="report && !report.empty">
        <zw-ai title="AI 分析摘要" :sub="summarySub">
          <template #extra>
            <a class="zw-link" :class="{ 'e07-disabled': store.regenerating }" @click="!store.regenerating && store.regenerateSummary()">重新生成</a>
          </template>

          <v-skeleton-loader v-if="store.summaryStatus === 'loading'" type="list-item-two-line@3" />
          <div v-else-if="store.summaryStatus === 'error'" class="e07-summary-error">
            <span>AI 摘要生成失败，报表数据不受影响。</span>
            <v-btn variant="outlined" size="small" prepend-icon="refresh" @click="store.loadSummary()">重新生成</v-btn>
          </div>
          <template v-else-if="store.summary">
            <div class="e07-summary-row">
              <span class="e07-summary-k">亮点</span>
              <span class="e07-summary-v">{{ store.summary.highlight || '暂无' }}</span>
            </div>
            <div class="e07-summary-row">
              <span class="e07-summary-k">问题</span>
              <span class="e07-summary-v">{{ store.summary.problem || '暂无' }}</span>
            </div>
            <div class="e07-summary-row">
              <span class="e07-summary-k">建议</span>
              <span class="e07-summary-v">{{ store.summary.suggestion || '暂无' }}</span>
            </div>
          </template>
        </zw-ai>

        <div class="e07-metrics">
          <button
            type="button"
            class="e07-metric"
            :class="{ on: store.metric === 'STOCK' }"
            @click="store.setMetric('STOCK')"
          >
            <div class="e07-metric-label"><zw-icon name="inventory_2" :size="18" />期末存栏</div>
            <div class="e07-metric-v num">{{ formatNumber(stockMetric?.end) }} <span>{{ stockMetric?.countUnitText || '头' }}</span></div>
            <div class="e07-metric-foot">{{ stockDelta }}</div>
            <div v-if="extraStocks.length" class="e07-metric-foot">
              <template v-for="(item, index) in extraStocks" :key="item.countUnit">
                <span v-if="index" class="mr-1">·</span>{{ formatNumber(item.end) }} {{ item.countUnitText }}
              </template>
            </div>
          </button>

          <button
            type="button"
            class="e07-metric"
            :class="{ on: store.metric === 'MORTALITY' }"
            @click="store.setMetric('MORTALITY')"
          >
            <div class="e07-metric-label"><zw-icon name="trending_down" :size="18" />死亡率</div>
            <div class="e07-metric-v num">
              <template v-if="report.metrics?.mortality?.rate !== undefined && report.metrics?.mortality?.rate !== null">
                {{ formatPercent(report.metrics.mortality.rate, 2) }}
              </template>
              <template v-else>无数据</template>
            </div>
            <div class="e07-metric-foot" :class="`fin-delta--${studyTone(report.metrics?.mortality?.changePoint)}`">{{ mortalityDelta }}</div>
          </button>

          <button
            type="button"
            class="e07-metric"
            :class="{ on: store.metric === 'FEED' }"
            @click="store.setMetric('FEED')"
          >
            <div class="e07-metric-label"><zw-icon name="rice_bowl" :size="18" />饲料用量</div>
            <div class="e07-metric-v num">{{ formatNumber(report.metrics?.feed?.kg) }} <span>千克</span></div>
            <div class="e07-metric-foot">{{ feedDelta }}</div>
          </button>

          <button
            type="button"
            class="e07-metric"
            :class="{ on: store.metric === 'BALANCE' }"
            @click="store.setMetric('BALANCE')"
          >
            <div class="e07-metric-label"><zw-icon name="account_balance_wallet" :size="18" />收支结余</div>
            <div class="e07-metric-v num" :class="`fin-delta--${metricTone('BALANCE')}`">{{ formatMoney(report.metrics?.balance?.amount) }} <span>元</span></div>
            <div class="e07-metric-foot" :class="`fin-delta--${trendTone(report.metrics?.balance?.changeRate, true)}`">{{ balanceDelta }}</div>
          </button>
        </div>

        <div class="e07-main">
          <zw-card :title="`${metricLabels[store.metric]} · 逐日变化`" :sub="trendSub">
            <v-skeleton-loader v-if="store.trendStatus === 'loading'" type="image" />
            <zw-empty v-else-if="store.trendStatus !== 'ready'" icon="show_chart" title="暂无趋势数据" :desc="store.trendError || '本期还没有可绘制的数据。'" />
            <template v-else>
              <zw-bar-chart
                v-if="trendIsBar"
                :labels="trendLabels"
                :series="[{ name: '饲料用量', data: trendValues, color: 'chart-1' }]"
                :height="230"
                unit="千克"
                :legend="false"
              />
              <zw-line-chart
                v-else
                :labels="trendLabels"
                :series="[{ name: metricLabels[store.metric], data: trendValues, area: true, color: 'chart-1' }]"
                :height="230"
                :unit="store.metric === 'STOCK' ? (extraStocks.length ? '头/只' : stockMetric?.countUnitText || '头') : store.metric === 'BALANCE' ? '元' : '%'"
                :ticks="4"
                :x-every="Math.max(1, Math.round(trendLabels.length / 7))"
                :legend="false"
              />
              <div v-if="equation && store.metric === 'STOCK'" class="e07-equation num">
                期末 {{ formatNumber(equation.end) }} = 期初 {{ formatNumber(equation.begin) }} + 入栏
                {{ formatNumber(equation.entry) }} − 死淘 {{ formatNumber(equation.death) }} − 出栏
                {{ formatNumber(equation.sold) }}
              </div>
            </template>
          </zw-card>

          <zw-card title="批次盈亏对比" :sub="`本期合计 ${profitTotal >= 0 ? '+' : '−'}${formatMoney(Math.abs(profitTotal))} 元`">
            <div v-if="profitItems.length" class="e07-profit-list">
              <div v-for="item in profitItems" :key="item.batchId" class="e07-profit-row" @click="goBatch(item.batchId)">
                <span class="e07-profit-no num">{{ item.batchNo }}</span>
                <div class="e07-profit-track">
                  <i
                    class="e07-profit-bar"
                    :class="{ negative: Number(item.profit) < 0 }"
                    :style="{ width: `${barWidth(item.profit)}%` }"
                  />
                </div>
                <span class="e07-profit-v num" :class="Number(item.profit) >= 0 ? 'positive' : 'negative'">
                  {{ Number(item.profit) >= 0 ? '+' : '−' }}{{ formatMoney(Math.abs(Number(item.profit || 0))) }} 元
                </span>
              </div>
            </div>
            <div v-else class="e07-profit-empty zw-faint">本期没有结束的批次</div>
            <div class="e07-running zw-faint body-s">仍在饲养的批次只显示累计成本</div>
            <div class="e07-running-list">
              <div v-for="batch in runningBatches" :key="batch.batchId" class="e07-running-row" @click="goBatch(batch.batchId)">
                <span class="num">{{ batch.batchNo }}</span>
                <span class="zw-faint body-s">{{ batch.penName }}</span>
                <span class="e07-running-v num">{{ formatMoney(batch.costTotal) }} 元</span>
              </div>
              <div v-if="!runningBatches.length" class="zw-faint body-s">没有在养批次</div>
            </div>
          </zw-card>
        </div>
      </template>
    </template>

    <zw-dialog v-model="store.exportOpen" title="导出经营报表" icon="download" width="480">
      <div class="e07-export-sum">
        {{ farm.viewAll ? '全部养殖场' : farm.farmName }} · {{ store.rangeText }} ·
        {{ store.reportType === 'TO_DATE' ? '本期至今' : store.reportTypeText }}（{{ store.periodLabel || store.periodRangeText }}）
      </div>
      <div class="e07-export-formats">
        <button
          type="button"
          class="e07-format"
          :class="{ on: store.exportFormat === 'XLSX' }"
          @click="store.exportFormat = 'XLSX'"
        >
          <zw-icon name="table_chart" :size="20" />
          <div class="e07-format-main">
            <div class="e07-format-title">表格（.xlsx）</div>
            <div class="e07-format-desc">指标、逐日数据和批次盈亏明细，可以再编辑</div>
          </div>
          <zw-icon :name="store.exportFormat === 'XLSX' ? 'radio_button_checked' : 'radio_button_unchecked'" :size="20" />
        </button>
        <button
          type="button"
          class="e07-format e07-format--disabled"
          disabled
        >
          <zw-icon name="picture_as_pdf" :size="20" />
          <div class="e07-format-main">
            <div class="e07-format-title">文档（.pdf）</div>
            <div class="e07-format-desc">平台暂只支持表格导出，PDF 稍后开放</div>
          </div>
          <zw-icon name="radio_button_unchecked" :size="20" />
        </button>
      </div>
      <div class="e07-export-note zw-faint body-s">
        按当前报表类型、养殖场、范围和统计期生成，完成后由浏览器下载到“下载”文件夹。
      </div>
      <div v-if="store.exportError" class="e07-export-error">{{ store.exportError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="store.exportBusy" @click="store.exportOpen = false">取消</v-btn>
        <v-btn color="primary" prepend-icon="download" :loading="store.exportBusy" @click="store.doExport()">导出</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.e07-filters { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.e07-farm { width: 200px; }
.e07-range { width: 120px; }
.e07-period { width: 180px; }
.e07-filters-right { margin-left: auto; display: flex; align-items: baseline; gap: 8px; }
.e07-period-text { font-size: 14px; color: var(--text-2); }
.fin-delta--success { color: var(--success); }
.fin-delta--error { color: var(--error); }
.fin-delta--muted { color: var(--text-3); }
.e07-summary-row { display: flex; gap: 12px; font-size: 14px; line-height: 24px; }
.e07-summary-k { width: 36px; flex: 0 0 36px; font-weight: 600; color: var(--on-tertiary-container); }
.e07-summary-v { flex: 1; min-width: 0; }
.e07-summary-error { display: flex; align-items: center; gap: 12px; color: var(--text-2); font-size: 14px; }
.e07-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin: 16px 0; }
.e07-metric { text-align: left; padding: 14px 20px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--bg-card); cursor: pointer; font: inherit; position: relative; }
.e07-metric:hover { border-color: var(--primary); }
.e07-metric.on { border-color: var(--primary); background: var(--primary-subtle); }
.e07-metric.on::after { content: ''; position: absolute; left: 0; right: 0; bottom: -1px; height: 2px; background: var(--primary); }
.e07-metric-label { display: flex; align-items: center; gap: 6px; font-size: 14px; color: var(--text-2); }
.e07-metric-v { margin-top: 6px; font-size: 24px; line-height: 32px; font-weight: 600; color: var(--text-1); }
.e07-metric-v span { font-size: 14px; font-weight: 400; color: var(--text-2); }
.e07-metric-foot { margin-top: 2px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.e07-main { display: grid; grid-template-columns: minmax(0, 1fr) 420px; gap: 16px; align-items: start; }
.e07-equation { margin-top: 12px; padding: 8px 12px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 12px; color: var(--text-2); }
.e07-profit-list { display: flex; flex-direction: column; gap: 6px; }
.e07-profit-row { display: flex; align-items: center; gap: 10px; height: 36px; cursor: pointer; }
.e07-profit-row:hover { background: var(--fill-1); }
.e07-profit-no { width: 92px; flex: 0 0 92px; font-size: 13px; }
.e07-profit-track { flex: 1; height: 16px; border-radius: 2px; background: var(--fill-2); overflow: hidden; }
.e07-profit-bar { display: block; height: 100%; border-radius: 2px; background: var(--chart-1); }
.e07-profit-bar.negative { background: var(--error); }
.e07-profit-v { width: 96px; text-align: right; font-size: 14px; }
.e07-profit-v.positive { color: var(--success); }
.e07-profit-v.negative { color: var(--error); }
.e07-profit-empty { padding: 20px 0; text-align: center; }
.e07-running { margin-top: 12px; padding: 8px 0 4px; border-top: 1px dashed var(--line-1); }
.e07-running-list { display: flex; flex-direction: column; }
.e07-running-row { display: flex; align-items: center; gap: 8px; height: 32px; border-bottom: 1px dashed var(--line-1); cursor: pointer; font-size: 13px; }
.e07-running-row:hover { background: var(--fill-1); }
.e07-running-v { margin-left: auto; }
.e07-check-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 10px 0; }
.e07-check-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--bg-card); border-radius: var(--radius-sm); cursor: pointer; }
.e07-check-main { flex: 1; min-width: 0; }
.e07-check-more { display: flex; align-items: center; justify-content: center; font-size: 12px; color: var(--text-3); }
.e07-export-sum { padding: 10px 12px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 14px; color: var(--text-2); margin-bottom: 16px; }
.e07-export-formats { display: flex; flex-direction: column; gap: 10px; }
.e07-format { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--line-2); border-radius: var(--radius-sm); background: var(--bg-card); cursor: pointer; font: inherit; text-align: left; }
.e07-format.on { border-color: var(--primary); background: var(--primary-subtle); }
.e07-format--disabled { opacity: .55; cursor: not-allowed; }
.e07-format-main { flex: 1; min-width: 0; }
.e07-format-title { font-size: 14px; font-weight: 500; color: var(--text-1); }
.e07-format-desc { font-size: 12px; line-height: 18px; color: var(--text-3); }
.e07-export-note { margin-top: 12px; }
.e07-export-error { margin-top: 8px; font-size: 12px; color: var(--error); }
.e07-disabled { color: var(--text-disabled); cursor: default; }
</style>

