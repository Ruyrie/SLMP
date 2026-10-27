<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatMoney, formatNumber } from '@zmt/shared'
import { useMarketStore } from '@/stores/finance/market'
import { useSaleOrderFormStore } from '@/stores/finance/saleOrderForm'
import { useFarmReload } from '@/composables/useFarmReload'
import { dateText, dateTimeText, priceText, signedPercent, trendTone } from '@/stores/finance/shared'

const store = useMarketStore()
const formStore = useSaleOrderFormStore()
const router = useRouter()
const route = useRoute()

const priceTypeItems = computed(() =>
  (store.filters?.priceTypes || []).map((item) => ({ label: item.name, value: item.code }))
)

const rangeItems = [
  { label: '7 天', value: 7 },
  { label: '30 天', value: 30 },
  { label: '90 天', value: 90 }
]

const xEvery = computed(() => (store.rangeDays === 7 ? 1 : store.rangeDays === 30 ? 5 : 14))

const series = computed(() => {
  const rows: { name: string; data: (number | null)[]; area?: boolean; dashed?: boolean; color?: string }[] = [
    { name: store.priceTypeName, data: store.chartValues, area: true, color: 'chart-1' }
  ]
  if (store.averageSeries.length) {
    rows.push({ name: `平均价 ${priceText(store.trend?.stats?.avgPrice)}`, data: store.averageSeries, dashed: true, color: 'chart-ref' })
  }
  return rows
})

const yMin = computed(() => {
  const values = store.chartValues.filter((value): value is number => value !== null && Number.isFinite(Number(value)))
  if (!values.length) return undefined
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pad = Math.max((max - min) * 0.15, 0.02)
  return Math.floor((min - pad) * 100) / 100
})

const yMax = computed(() => {
  const values = store.chartValues.filter((value): value is number => value !== null && Number.isFinite(Number(value)))
  if (!values.length) return undefined
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pad = Math.max((max - min) * 0.15, 0.02)
  return Math.ceil((max + pad) * 100) / 100
})

const adviceReasonRows = computed(() => {
  const reasons = store.advice?.reasons
  if (!reasons) return []
  return [
    { label: '休药', value: reasons.withdrawal },
    { label: '日龄', value: reasons.age },
    { label: '体重', value: reasons.weight },
    { label: '价格', value: reasons.price }
  ].filter((row) => !!row.value)
})

const adviceWindow = computed(() => {
  const advice = store.advice
  if (!advice?.windowStart || !advice?.windowEnd) return ''
  return `${dateText(advice.windowStart)}–${dateText(advice.windowEnd)}`
})

const adviceTag = computed(() => {
  const advice = store.advice
  if (!advice?.inWithdrawal) return null
  return `休药中${advice.withdrawalDaysLeft !== undefined ? ` · 剩 ${advice.withdrawalDaysLeft} 天` : ''}`
})

const batchItems = computed(() =>
  store.batches.map((batch) => ({
    title: [batch.batchNo, batch.penName].filter(Boolean).join(' · '),
    value: Number(batch.batchId || 0)
  }))
)

const selectedBatch = computed(() => store.batches.find((batch) => Number(batch.batchId) === store.batchId))

const batchHint = computed(() => {
  if (store.batchStatus === 'loading') return '正在加载在养批次…'
  if (store.batchStatus === 'error' || store.batchStatus === 'forbidden') return '在养批次加载失败，请重试。'
  const batch = selectedBatch.value
  if (!batch) return '当前养殖场没有在养批次，入栏后才能获取出栏建议。'
  return `${batch.breedName || ''}${batch.categoryName || ''} · ${batch.dayAge || 0} 日龄 · 存栏 ${formatNumber(
    batch.currentCount
  )} ${batch.countUnitText || '头'}`
})

const canCreateSale = computed(() => store.advice?.conclusion === 'SELL_SOON')

function changeToneClass(direction?: string): string {
  if (!direction || direction === 'FLAT') return 'muted'
  const up = direction === 'UP'
  return up === store.priceUpIsGood ? 'success' : 'error'
}

function dailyChangeText(rate?: number): string {
  if (rate === undefined || rate === null) return '—'
  return signedPercent(rate)
}

function dailyTone(rate?: number): string {
  return `fin-delta--${trendTone(rate, store.priceUpIsGood)}`
}

function restoreDefaults(): void {
  store.restoreDefaults()
}

async function createSale(): Promise<void> {
  await formStore.openCreate(store.batchId)
}

function goSales(): void {
  void router.push({ name: 'WU-E03' })
}

function openHistory(advice: Record<string, unknown>): void {
  void store.openHistory(advice as never)
}

onMounted(() => {
  const batchId = Number(route.query.batchId || route.query.batch_id || 0)
  void store.load(batchId)
})
onBeforeUnmount(() => store.dispose())
useFarmReload(() => {
  void store.load(0)
})
</script>

<template>
  <zw-shell side="user" active="WU-E06" :crumbs="['经营管理', '市场行情']">
    <zw-page-head
      title="市场行情"
      desc="价格由平台每天发布，按发布时的计价单位显示；AI 出栏建议结合休药、日龄、体重和价格给出"
    />

    <div v-if="store.filterStatus === 'loading'">
      <v-skeleton-loader type="article, article" />
    </div>
    <zw-card v-else-if="store.filterStatus === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="你没有查看经营数据的权限" desc="经营功能只对所有者和场长开放。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>
    <zw-card v-else-if="store.filterStatus === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="行情加载失败" :desc="store.filterError">
        <v-btn variant="outlined" @click="store.load()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div class="e06-toolbar">
        <v-select
          :model-value="store.provinceCode"
          :items="store.filters?.provinces || []"
          item-title="name"
          item-value="code"
          class="e06-filter"
          density="compact"
          hide-details
          prepend-inner-icon="location_on"
          @update:model-value="store.setFilter({ provinceCode: String($event || '') })"
        />
        <v-select
          :model-value="store.categoryId"
          :items="store.filters?.categories || []"
          item-title="name"
          item-value="id"
          class="e06-filter"
          density="compact"
          hide-details
          @update:model-value="store.setFilter({ categoryId: Number($event || 0) })"
        />
        <v-select
          :model-value="store.breedId"
          :items="store.categoryBreeds"
          item-title="name"
          item-value="breedId"
          class="e06-filter"
          density="compact"
          hide-details
          @update:model-value="store.setFilter({ breedId: Number($event || 0) })"
        />
        <zw-seg
          :model-value="store.priceType"
          :items="priceTypeItems"
          @update:model-value="store.setFilter({ priceType: String($event) })"
        />
        <div class="e06-toolbar-right">
          <span v-if="!store.touched" class="zw-faint body-s">{{ store.filters?.defaultDesc }}</span>
          <a v-else class="zw-link" @click="restoreDefaults">恢复默认</a>
        </div>
      </div>

      <div class="e06-layout">
        <div class="e06-left">
          <zw-card
            :title="`${store.categoryName} · ${store.priceTypeName} · ${store.provinceName}`"
            :sub="`单位：${store.unitText}`"
          >
            <template #extra>
              <span class="zw-faint body-s">{{ dateText(store.trend?.latest?.priceDate) }}更新</span>
              <zw-seg
                size="small"
                class="ml-3"
                :model-value="store.rangeDays"
                :items="rangeItems"
                @update:model-value="store.setRange(Number($event))"
              />
            </template>

            <zw-empty
              v-if="store.trendStatus === 'empty'"
              icon="show_chart"
              :title="`${store.provinceName}暂时没有${store.categoryName}${store.priceTypeName}`"
              desc="可以切换地区或品类查看。"
            />
            <v-skeleton-loader v-else-if="store.trendStatus === 'loading'" type="image, article" />
            <zw-empty v-else-if="store.trendStatus === 'error'" icon="cloud_off" tone="error" title="行情加载失败" :desc="store.trendError">
              <v-btn variant="outlined" @click="store.loadTrend()">重新加载</v-btn>
            </zw-empty>
            <template v-else-if="store.trend">
              <div class="e06-price-head">
                <span class="e06-price num">{{ priceText(store.trend.latest?.price) }}</span>
                <span class="zw-muted">{{ store.unitText }}</span>
                <span class="e06-change" :class="`fin-delta--${store.changeTone}`">{{ store.changeText }}</span>
                <span v-if="store.trend.change?.compareDate" class="zw-faint body-s">
                  较 {{ dateText(store.trend.change.compareDate) }} {{ priceText(store.trend.change.comparePrice) }} 元
                </span>
              </div>

              <zw-line-chart
                :labels="store.chartLabels"
                :series="series"
                :height="240"
                :unit="store.unitText"
                :y-min="yMin"
                :y-max="yMax"
                :ticks="5"
                :x-every="xEvery"
              />

              <div class="e06-stats">
                <div class="e06-stat">
                  <div class="zw-faint body-s">最高 · {{ dateText(store.trend.stats?.maxDate) }}</div>
                  <div class="e06-stat-v num">{{ priceText(store.trend.stats?.maxPrice) }} 元</div>
                </div>
                <div class="e06-stat">
                  <div class="zw-faint body-s">最低 · {{ dateText(store.trend.stats?.minDate) }}</div>
                  <div class="e06-stat-v num">{{ priceText(store.trend.stats?.minPrice) }} 元</div>
                </div>
                <div class="e06-stat">
                  <div class="zw-faint body-s">平均</div>
                  <div class="e06-stat-v num">{{ priceText(store.trend.stats?.avgPrice) }} 元</div>
                </div>
              </div>
            </template>
          </zw-card>

          <zw-card title="每日价格" :sub="`${store.unitText} · 按日期倒序`" flush class="mt-4">
            <v-data-table
              :headers="[
                { title: '日期', key: 'priceDate', sortable: false, width: 180 },
                { title: '价格', key: 'price', sortable: false, align: 'end', width: 140 },
                { title: '较前一日', key: 'changeRate', sortable: false, align: 'end', width: 160 },
                { title: '说明', key: 'note', sortable: false }
              ]"
              :items="store.daily"
              item-value="priceDate"
              hide-default-footer
            >
              <template #item.priceDate="{ item }">
                <span>{{ dateText(item.priceDate) }}</span>
              </template>
              <template #item.price="{ item }">
                <span class="num e06-strong">{{ priceText(item.price) }} 元</span>
              </template>
              <template #item.changeRate="{ item }">
                <span class="num" :class="dailyTone(item.changeRate)">{{ dailyChangeText(item.changeRate) }}</span>
              </template>
              <template #item.note="{ item }">
                <v-tooltip v-if="item.corrected" :text="`管理员在 ${dateText(item.correctedAt)} 更正了这一天的价格`" location="top">
                  <template #activator="{ props: tip }">
                    <span v-bind="tip"><zw-tag size="small" icon="edit">已更正</zw-tag></span>
                  </template>
                </v-tooltip>
                <span v-else class="zw-faint">—</span>
              </template>
              <template #no-data>
                <div class="e06-empty-note">当前筛选还没有发布价格</div>
              </template>
            </v-data-table>
            <div class="zw-table-foot zw-table-foot--in">
              <span class="zw-faint body-s">被管理员更正过的价格会显示“已更正”</span>
              <zw-pager
                :total="store.dailyTotal"
                :page="store.dailyPage"
                :size="store.dailySize"
                @update:page="store.setDailyPage"
                @update:size="store.setDailySize"
              />
            </div>
          </zw-card>
        </div>

        <div class="e06-right">
          <zw-ai
            title="AI 出栏建议"
            :sub="store.advice?.status === 'GENERATING' ? '正在生成' : store.advice?.generatedAt ? `${dateTimeText(store.advice.generatedAt)} 生成` : ''"
            disclaimer="仅供参考，不构成交易建议"
          >
            <template #extra>
              <a
                class="zw-link"
                :class="{ 'e06-disabled': !store.batchId || store.adviceGenerating || store.adviceStatus === 'loading' }"
                @click="store.batchId && !store.adviceGenerating && store.adviceStatus !== 'loading' && store.regenerate()"
              >重新生成</a>
            </template>

            <div class="e06-batch">
              <v-select
                :model-value="store.batchStatus === 'ready' ? store.batchId || null : null"
                :items="batchItems"
                item-title="title"
                item-value="value"
                density="compact"
                hide-details
                :disabled="!store.batches.length || store.batchStatus === 'loading'"
                :loading="store.batchStatus === 'loading'"
                placeholder="选择在栏批次"
                @update:model-value="store.selectBatch(Number($event || 0))"
              />
              <div class="e06-batch-hint">{{ batchHint }}</div>
            </div>

            <div v-if="store.batchStatus === 'error' || store.batchStatus === 'forbidden'" class="e06-ai-fail">
              <zw-icon name="cloud_off" :size="32" />
              <div>{{ store.batchError || '在养批次加载失败' }}</div>
              <v-btn variant="outlined" size="small" prepend-icon="refresh" @click="store.loadBatches()">重新加载批次</v-btn>
            </div>

            <div v-else-if="store.adviceStatus === 'loading'">
              <div v-if="store.batchStatus === 'ready'" class="zw-faint body-s">正在获取出栏建议，首次生成可能需要约一分钟。</div>
              <v-skeleton-loader type="article, list-item-two-line@3" />
            </div>

            <div v-else-if="store.adviceStatus === 'unavailable'" class="e06-ai-fail">
              <zw-icon name="cloud_off" :size="32" />
              <div>出栏建议暂时无法生成，请稍后重新生成。</div>
              <v-btn variant="outlined" size="small" prepend-icon="refresh" @click="store.regenerate()">重新生成</v-btn>
            </div>

            <div v-else-if="store.adviceStatus === 'error' || store.adviceStatus === 'forbidden'" class="e06-ai-fail">
              <zw-icon name="cloud_off" :size="32" />
              <div>{{ store.adviceError || '出栏建议加载失败' }}</div>
              <v-btn variant="outlined" size="small" prepend-icon="refresh" @click="store.loadAdvice()">重试</v-btn>
            </div>

            <div v-else-if="store.adviceStatus === 'empty'" class="e06-ai-fail">
              <zw-icon name="inbox" :size="32" />
              <div>{{ store.batches.length ? '请选择在养批次获取出栏建议。' : '当前养殖场没有在养批次，入栏后才能获取出栏建议。' }}</div>
            </div>

            <template v-else-if="store.advice">
              <div class="e06-conclusion">
                <span class="e06-conclusion-text">{{ store.advice.conclusionText }}</span>
                <zw-tag v-if="adviceTag" type="warning" size="small" icon="medication">{{ adviceTag }}</zw-tag>
                
              </div>

              <div v-if="adviceWindow" class="e06-window">
                建议时间段：<strong>{{ adviceWindow }}</strong>
                <template v-if="store.advice.sellableDate">，不早于可出栏日期 {{ dateText(store.advice.sellableDate) }}</template>。
              </div>

              <div v-if="store.advice.basisInsufficient" class="e06-basis">
                <zw-icon name="warning" :size="16" />
                <span>依据不足：{{ store.advice.basisNote || '最近一次称重或价格数据不足。' }}</span>
              </div>

              <div class="e06-ai-card">
                <div class="e06-ai-label">理由</div>
                <div v-for="row in adviceReasonRows" :key="row.label" class="e06-reason">
                  <span class="e06-reason-k">{{ row.label }}</span>
                  <span class="e06-reason-v">{{ row.value }}</span>
                </div>
                <div v-if="!adviceReasonRows.length" class="zw-faint body-s">暂无更多依据说明</div>
                <template v-if="store.advice.risks?.length">
                  <div class="e06-ai-div" />
                  <div class="e06-ai-label">风险</div>
                  <div v-for="risk in store.advice.risks" :key="risk" class="e06-risk">
                    <zw-icon name="warning" :size="16" />
                    <span>{{ risk }}</span>
                  </div>
                </template>
              </div>

              <div v-if="canCreateSale" class="e06-ai-actions">
                <v-btn color="primary" variant="tonal" prepend-icon="add" @click="createSale">登记出栏单</v-btn>
              </div>
            </template>
          </zw-ai>

          <zw-card title="历史建议" sub="当前养殖场 · 按生成时间倒序" flush class="mt-4">
            <v-skeleton-loader v-if="store.historyStatus === 'loading'" type="list-item-two-line@3" />
            <zw-empty v-else-if="store.historyStatus === 'error' || store.historyStatus === 'forbidden'" icon="cloud_off" title="历史建议加载失败" :desc="store.historyError">
              <v-btn variant="outlined" size="small" @click="store.loadHistory()">重试</v-btn>
            </zw-empty>
            <zw-empty v-else-if="store.historyStatus !== 'ready'" icon="history" title="还没有历史建议" desc="生成出栏建议后会保留在这里。" />
            <div v-else class="e06-history">
              <div v-for="advice in store.history" :key="advice.id" class="e06-history-row" @click="openHistory(advice)">
                <span class="e06-history-ico"><zw-icon name="auto_awesome" :size="18" /></span>
                <div class="e06-history-main">
                  <div class="e06-history-title">{{ advice.conclusionText || '出栏建议' }}</div>
                  <div class="zw-cell-sub">{{ advice.batchNo }} · {{ dateText(advice.generatedAt) }}</div>
                </div>
                <zw-icon name="chevron_right" :size="18" color="text-3" />
              </div>
            </div>
          </zw-card>
        </div>
      </div>
    </template>

    <zw-drawer v-model="store.historyOpen" :title="store.historyDetail?.conclusionText || '历史建议'" :sub="store.historyDetail ? `${store.historyDetail.batchNo} · ${dateText(store.historyDetail.generatedAt)}` : ''" width="560">
      <v-skeleton-loader v-if="store.historyDetailLoading" type="article, list-item-two-line@3" />
      <template v-else-if="store.historyDetail">
        <div class="e06-conclusion">
          <span class="e06-conclusion-text">{{ store.historyDetail.conclusionText }}</span>
          <zw-tag v-if="store.historyDetail.inWithdrawal" type="warning" size="small" icon="medication">
            休药中{{ store.historyDetail.withdrawalDaysLeft !== undefined ? ` · 剩 ${store.historyDetail.withdrawalDaysLeft} 天` : '' }}
          </zw-tag>
        </div>
        <div v-if="store.historyDetail.windowStart" class="e06-window">
          建议时间段：<strong>{{ dateText(store.historyDetail.windowStart) }}–{{ dateText(store.historyDetail.windowEnd) }}</strong>
        </div>
        <div class="e06-ai-card">
          <div class="e06-ai-label">理由</div>
          <div v-for="(value, key) in store.historyDetail.reasons || {}" :key="key" class="e06-reason">
            <span class="e06-reason-k">{{ key === 'withdrawal' ? '休药' : key === 'age' ? '日龄' : key === 'weight' ? '体重' : '价格' }}</span>
            <span class="e06-reason-v">{{ value }}</span>
          </div>
          <template v-if="store.historyDetail.risks?.length">
            <div class="e06-ai-div" />
            <div class="e06-ai-label">风险</div>
            <div v-for="risk in store.historyDetail.risks" :key="risk" class="e06-risk">
              <zw-icon name="warning" :size="16" /><span>{{ risk }}</span>
            </div>
          </template>
        </div>
        <zw-notice icon="info" class="mt-4">仅供参考，不构成交易建议</zw-notice>
      </template>
      <template #foot>
        <v-btn variant="outlined" @click="store.closeHistory()">关闭</v-btn>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.e06-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.e06-filter { width: 140px; }
.e06-toolbar-right { margin-left: auto; }
.e06-layout { display: grid; grid-template-columns: minmax(0, 1fr) 400px; gap: 16px; align-items: start; }
.e06-left { min-width: 0; }
.e06-price-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; flex-wrap: wrap; }
.e06-price { font-size: 32px; line-height: 42px; font-weight: 600; color: var(--text-1); }
.e06-change { font-size: 14px; }
.fin-delta--success { color: var(--success); }
.fin-delta--error { color: var(--error); }
.fin-delta--muted { color: var(--text-2); }
.e06-stats { display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px solid var(--line-1); margin-top: 12px; padding-top: 12px; }
.e06-stat { padding-left: 12px; }
.e06-stat + .e06-stat { border-left: 1px solid var(--line-1); }
.e06-stat-v { font-size: 16px; line-height: 24px; font-weight: 600; color: var(--text-1); }
.e06-strong { font-weight: 500; }
.e06-empty-note { padding: 24px 0; text-align: center; color: var(--text-3); font-size: 14px; }
.e06-batch :deep(.v-field) { background: var(--bg-card); }
.e06-batch-hint { margin-top: 6px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.e06-conclusion { display: flex; align-items: center; gap: 8px; margin-top: 14px; flex-wrap: wrap; }
.e06-conclusion-text { font-size: 20px; line-height: 28px; font-weight: 600; color: var(--on-tertiary-container); }
.e06-window { margin-top: 6px; font-size: 14px; line-height: 22px; }
.e06-basis { display: flex; align-items: flex-start; gap: 6px; margin-top: 8px; font-size: 12px; line-height: 18px; color: var(--warning); }
.e06-ai-card { margin-top: 12px; padding: 12px 14px; border-radius: var(--radius-sm); background: var(--bg-card); color: var(--text-1); }
.e06-ai-label { font-size: 14px; font-weight: 600; margin-bottom: 4px; }
.e06-reason { display: flex; gap: 8px; font-size: 13px; line-height: 20px; margin-top: 2px; }
.e06-reason-k { width: 28px; flex: 0 0 28px; color: var(--text-2); }
.e06-reason-v { flex: 1; min-width: 0; }
.e06-ai-div { height: 1px; background: var(--line-1); margin: 8px 0; }
.e06-risk { display: flex; align-items: flex-start; gap: 6px; font-size: 13px; line-height: 20px; margin-top: 2px; }
.e06-risk :deep(.zw-icon) { color: var(--warning); margin-top: 1px; }
.e06-ai-actions { margin-top: 12px; }
.e06-ai-fail { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 28px 16px; background: var(--bg-card); border-radius: var(--radius-sm); text-align: center; font-size: 14px; color: var(--text-2); margin-top: 14px; }
.e06-disabled { color: var(--text-disabled); cursor: default; }
.e06-history { max-height: 248px; overflow: auto; }
.e06-history-row { display: flex; align-items: center; gap: 12px; padding: 10px 20px; cursor: pointer; }
.e06-history-row:hover { background: var(--fill-1); }
.e06-history-ico { width: 32px; height: 32px; border-radius: var(--radius-sm); background: var(--tertiary-container); color: var(--on-tertiary-container); display: inline-flex; align-items: center; justify-content: center; flex: 0 0 32px; }
.e06-history-main { flex: 1; min-width: 0; }
.e06-history-title { font-size: 14px; line-height: 22px; font-weight: 500; color: var(--text-1); }
</style>


