<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatNumber } from '@zmt/shared'
import { useWeighStore } from '@/stores/feeding/weigh'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { dayAgeAt, deviationText, monthDay, monthDayWeek, todayISO } from '@/stores/feeding/shared'
import type { WeighRecordRow } from '@/api/feeding'
import FeedingDateField from './components/FeedingDateField.vue'

interface ChartGeom {
  x: (index: number) => number
  y: (value: number) => number
}

const store = useWeighStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const { isStaff } = useRole()
const compareDraft = ref<number[]>([])
const localDate = todayISO()

const canManage = computed(() => !isStaff.value)
const activeBatch = computed(() => (store.curve?.batch?.status || store.form?.batch?.status || 'ACTIVE') === 'ACTIVE')
const growthTag = computed(() => store.growthTag)
const titleDesc = computed(() => store.desc)

const labels = computed<string[]>(() => {
  const curve = store.curve
  if (!curve) return []
  const min = Number(curve.x_min ?? 0)
  const max = Number(curve.x_max ?? min + 1) + 4
  const list: string[] = []
  for (let day = min; day <= max; day += 1) list.push(String(day))
  return list
})

function stdAt(day: number): number | null {
  const points = (store.curve?.std_points || [])
    .filter((item) => item.day_age !== undefined && item.std_weight !== undefined)
    .map((item) => ({ day: Number(item.day_age), value: Number(item.std_weight) }))
    .sort((a, b) => a.day - b.day)
  if (!points.length) return null
  if (day <= points[0].day) return points[0].value
  if (day >= points[points.length - 1].day) return points[points.length - 1].value
  for (let index = 1; index < points.length; index += 1) {
    const prev = points[index - 1]
    const next = points[index]
    if (day <= next.day) {
      const ratio = (day - prev.day) / Math.max(1, next.day - prev.day)
      return prev.value + (next.value - prev.value) * ratio
    }
  }
  return null
}

const stdValues = computed<(number | null)[]>(() => {
  if (!store.curve?.std_available) return []
  const max = Number(store.curve?.x_max ?? 0)
  return labels.value.map((label) => (Number(label) <= max ? stdAt(Number(label)) : null))
})

const series = computed(() => {
  const list: Array<{ name: string; data: (number | null)[]; color: string; dashed?: boolean; dots?: boolean }> = []
  if (stdValues.value.length) {
    list.push({ name: '标准', data: stdValues.value, color: 'chart-ref', dashed: true, dots: false })
  }
  for (const compare of store.curve?.compare_series || []) {
    const min = Number(store.curve?.x_min ?? 0)
    const map = new Map<number, number>()
    for (const point of compare.points || []) {
      if (point.day_age !== undefined && point.avg_weight !== undefined) map.set(Number(point.day_age), Number(point.avg_weight))
    }
    list.push({
      name: `${compare.batch_no || '对比批次'}${compare.status === 'ENDED' ? '（已结束）' : ''}`,
      data: labels.value.map((label) => map.get(Number(label)) ?? null),
      color: 'chart-2',
      dots: false
    })
  }
  const actualMap = new Map<number, number>()
  for (const point of store.curve?.actual_points || []) {
    if (point.day_age !== undefined && point.avg_weight !== undefined) actualMap.set(Number(point.day_age), Number(point.avg_weight))
  }
  const min = Number(store.curve?.x_min ?? 0)
  list.push({
    name: '实际',
    data: labels.value.map((label) => actualMap.get(Number(label)) ?? null),
    color: 'chart-1'
  })
  return list
})

const yRange = computed(() => {
  const values: number[] = []
  for (const item of series.value) {
    for (const value of item.data) {
      if (value !== null && value !== undefined) values.push(value)
    }
  }
  if (!values.length) return { min: 0, max: 100 }
  const min = Math.max(0, Math.floor(Math.min(...values) * 0.7 / 10) * 10)
  const max = Math.ceil(Math.max(...values) * 1.12 / 10) * 10
  return { min, max: Math.max(max, min + 10) }
})
const xEvery = computed(() => (labels.value.length > 60 ? 14 : labels.value.length > 30 ? 10 : 5))
const highlight = computed(() => {
  const current = Number(store.curve?.current_day_age ?? 0)
  const min = Number(store.curve?.x_min ?? 0)
  const index = current - min
  return index >= 0 && index < labels.value.length ? index : undefined
})
const currentStd = computed(() => {
  const day = Number(store.curve?.current_day_age ?? 0)
  return stdAt(day)
})
const latest = computed(() => store.metrics.avg_weight ?? null)

const modeItems = [
  { label: '抽样汇总', value: 'SAMPLE' },
  { label: '逐头录入', value: 'EACH' }
]
const compareCount = computed(() => store.compareIds.length)

watch(() => store.batchId, () => {
  compareDraft.value = []
})

watch(() => store.compareMenu, (open) => {
  if (open) compareDraft.value = [...store.compareIds]
})

function onBatch(value: unknown): void {
  const batchId = Number(value || 0)
  if (!batchId) return
  void store.setBatch(batchId)
  void router.replace({ query: { ...route.query, batchId: String(batchId) } })
}

function onMode(value: unknown): void {
  store.formState.mode = value === 'EACH' ? 'EACH' : 'SAMPLE'
}

function openWeigh(recordId?: number): void {
  void store.openWeigh(recordId)
}

function handleRecord(row: WeighRecordRow): void {
  if (row.action === 'EDIT' && row.id) {
    openWeigh(Number(row.id))
  } else if (row.action === 'REQUEST_CHANGE' && row.id) {
    void router.push({ name: 'WU-U01', query: { biz_type: 'WEIGH_RECORD', biz_id: String(row.id), action: 'create' } })
  }
}

function recordTag(row: WeighRecordRow): { text: string; type: string } | null {
  if (row.deviation_ratio === null || row.deviation_ratio === undefined) return null
  return { text: deviationText(row.deviation_ratio), type: row.out_of_range ? 'warning' : 'neutral' }
}

function applyCompare(): void {
  void store.applyCompare(compareDraft.value)
}

function toggleCompare(batchId: number): void {
  if (compareDraft.value.includes(batchId)) {
    compareDraft.value = compareDraft.value.filter((item) => item !== batchId)
  } else if (compareDraft.value.length < 3) {
    compareDraft.value = [...compareDraft.value, batchId]
  }
}

function bandPath(g: ChartGeom): string {
  let upper = ''
  const lower: string[] = []
  stdValues.value.forEach((value, index) => {
    if (value === null) return
    upper += `${upper ? 'L' : 'M'}${g.x(index).toFixed(1)} ${g.y(value * 1.1).toFixed(1)}`
    lower.unshift(`L${g.x(index).toFixed(1)} ${g.y(value * 0.9).toFixed(1)}`)
  })
  return upper ? `${upper}${lower.join('')}Z` : ''
}

function fieldError(field: string): string {
  return store.errors[field] || ''
}

function onWeighDate(value: string): void {
  if (store.drawerMode === 'edit') return
  void store.changeWeighDate(value)
}

function onCycleSave(reset = false): void {
  void store.saveCycle(reset)
}

function closeDrawer(): void {
  store.drawer = false
}

async function init(): Promise<void> {
  const queryBatch = Number(route.query.batchId || 0)
  if (queryBatch) store.batchId = queryBatch
  await store.load()
  if (route.query.action === 'weigh') await store.openWeigh()
}

onMounted(init)

useFarmReload(async () => {
  store.reset()
  await init()
})
</script>

<template>
  <zw-shell side="user" active="WU-G03" :crumbs="isStaff ? ['我的栏舍', '称重登记'] : ['生产管理', '称重与生长', '称重与生长曲线']">
    <zw-page-head title="称重与生长曲线" :desc="titleDesc">
      <template #tags>
        <zw-tag v-if="growthTag" :type="growthTag.type" :icon="growthTag.icon || undefined">{{ growthTag.text }}</zw-tag>
      </template>
      <template #actions>
        <span class="zw-filter-label">批次</span>
        <v-select :model-value="store.batchId" :items="store.batchItems" item-title="title" item-value="value" class="zw-w-240" @update:model-value="onBatch">
          <template #item="{ props: itemProps, item }">
            <v-list-item v-bind="itemProps" :subtitle="item.raw.hint" />
          </template>
        </v-select>
        <v-btn v-if="activeBatch" color="primary" prepend-icon="scale" @click="openWeigh()">称重登记</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading'">
      <div class="zw-grid-4 mb-4"><v-skeleton-loader v-for="item in 4" :key="item" type="article" /></div>
      <v-skeleton-loader type="image, table-row@4" />
    </div>

    <div v-else-if="store.status === 'empty'">
      <zw-card>
        <zw-empty icon="scale" title="还没有称重记录" desc="登记第一次称重后，这里会画出实际体重和标准体重的对照曲线。">
          <v-btn color="primary" :disabled="!store.batchId" @click="openWeigh()">称重登记</v-btn>
        </zw-empty>
      </zw-card>
    </div>

    <div v-else-if="store.status === 'error'">
      <zw-card>
        <zw-empty icon="cloud_off" tone="error" title="生长曲线加载失败" :desc="store.error">
          <v-btn color="primary" variant="outlined" @click="store.load(true)">重新加载</v-btn>
        </zw-empty>
      </zw-card>
    </div>

    <template v-else>
      <div class="zw-grid-4 mb-4">
        <zw-stat label="平均体重" icon="scale" :value="store.metrics.avg_weight === undefined ? '—' : formatNumber(store.metrics.avg_weight, 1)" unit="千克" :foot="store.metrics.weigh_date ? `${store.metrics.day_age} 日龄 · ${monthDay(store.metrics.weigh_date)}称重` : '还没有称重记录'" />
        <zw-stat label="偏离标准" icon="trending_down" :value="store.metrics.deviation_ratio === null || store.metrics.deviation_ratio === undefined ? '无数据' : deviationText(store.metrics.deviation_ratio)" :unit="store.metrics.deviation_ratio === null || store.metrics.deviation_ratio === undefined ? '' : '%'">
          <template #foot><span :class="store.deviationTone === 'error' ? 'g03-err' : ''">{{ store.metrics.std_weight ? `标准 ${formatNumber(store.metrics.std_weight, 1)} 千克 · 允许 ±${Math.round(Number(store.metrics.allowed_deviation || 0.1) * 100)}%` : '平台还没有该品种的标准体重' }}</span></template>
        </zw-stat>
        <zw-stat label="日增重" icon="show_chart" :value="store.metrics.daily_gain === undefined || store.metrics.daily_gain === null ? '—' : formatNumber(store.metrics.daily_gain, 2)" unit="千克" :foot="store.metrics.prev_daily_gain ? `上次 ${formatNumber(store.metrics.prev_daily_gain, 2)} 千克` : '首次称重'" />
        <zw-stat label="累计增重" icon="add_chart" :value="store.metrics.cumulative_gain === undefined || store.metrics.cumulative_gain === null ? '—' : formatNumber(store.metrics.cumulative_gain, 1)" unit="千克" :foot="store.curve?.batch?.entry_avg_weight ? `入栏 ${formatNumber(store.curve.batch.entry_avg_weight, 1)} 千克 · ${store.curve.batch.entry_age_days || 0} 日龄` : '入栏时没有填写平均体重'" />
      </div>

      <zw-notice v-if="store.curve?.std_updated_notice" type="info" class="mb-4">品种标准体重已更新，偏离情况已按新标准重新计算。</zw-notice>

      <div class="g03-layout">
        <zw-card title="平均体重 · 按日龄" sub="横轴为日龄，实线为实际、虚线为标准">
          <template #extra>
            <span class="body-s zw-faint">同品种对比</span>
            <span v-for="chip in store.compareChips" :key="chip.batchId" class="g03-chip">
              <i />
              <span class="num">{{ chip.batchNo }}</span>
              <zw-icon name="close" :size="14" @click="store.removeCompare(chip.batchId)" />
            </span>
            <v-menu v-if="store.compareIds.length < 3" v-model="store.compareMenu" :close-on-content-click="false" location="bottom end">
              <template #activator="{ props: activatorProps }">
                <v-btn v-bind="activatorProps" size="small" variant="outlined" prepend-icon="add">添加批次</v-btn>
              </template>
              <div class="zw-pop g03-compare-pop">
                <div class="zw-pop-title">选择对比批次<span class="zw-pop-sub">只列出同品种的批次，最多选 3 个</span></div>
                <div v-if="!store.compareOptions.length" class="g03-compare-empty"><span class="zw-faint">当前养殖场还没有其他同品种批次</span></div>
                <label v-for="option in store.compareOptions" :key="option.batch_id" class="g03-compare-row" :class="{ dis: !compareDraft.includes(Number(option.batch_id)) && compareDraft.length >= 3 }">
                  <v-checkbox :model-value="compareDraft.includes(Number(option.batch_id))" hide-details density="compact" @update:model-value="toggleCompare(Number(option.batch_id))" />
                  <span class="num">{{ option.batch_no }}</span>
                  <span class="zw-faint">{{ option.pen_names }} · {{ option.status === 'ENDED' ? '已结束' : '在养' }}<template v-if="option.latest_avg_weight"> · 最近平均 {{ formatNumber(option.latest_avg_weight, 1) }} 千克（{{ option.latest_day_age }} 日龄）</template></span>
                </label>
                <div class="g03-compare-foot">
                  <span class="zw-faint body-s">已选 {{ compareDraft.length }} 个，最多选 3 个</span>
                  <v-btn size="small" color="primary" :disabled="!compareDraft.length" @click="applyCompare">确定</v-btn>
                </div>
              </div>
            </v-menu>
          </template>

          <div class="g03-legend">
            <span><i class="zw-lg-line" style="--c: var(--chart-1)" />实际</span>
            <span v-if="store.hasStd"><i class="zw-lg-dash" style="--c: var(--chart-ref)" />标准</span>
            <span v-for="compare in store.curve?.compare_series || []" :key="compare.batch_id"><i class="zw-lg-line" style="--c: var(--chart-2)" />{{ compare.batch_no }}{{ compare.status === 'ENDED' ? '（已结束）' : '' }}</span>
            <span v-if="store.hasStd"><i class="g03-band" />允许偏差 ±{{ Math.round(Number(store.metrics.allowed_deviation || 0.1) * 100) }}%</span>
            <span class="zw-chart-unit">单位：千克</span>
          </div>

          <v-skeleton-loader v-if="store.curveLoading" type="image" />
          <zw-line-chart
            v-else
            :labels="labels"
            :series="series"
            :height="400"
            :y-min="yRange.min"
            :y-max="yRange.max"
            :x-every="xEvery"
            :highlight="highlight"
            :legend="false"
          >
            <template #default="{ g }">
              <path v-if="stdValues.length" :d="bandPath(g as ChartGeom)" style="fill: var(--chart-ref); opacity: 0.14" />
              <text v-if="currentStd !== null" class="g03-ann" :x="g.x(Number(highlight)) + 8" :y="g.y(currentStd) - 10">标准 {{ formatNumber(currentStd, 1) }}</text>
              <text v-if="latest !== null && highlight !== undefined" class="g03-ann g03-ann--p" :x="g.x(Number(highlight)) + 8" :y="g.y(Number(latest)) + 18">{{ formatNumber(latest, 1) }}</text>
            </template>
          </zw-line-chart>
          <div v-if="!store.hasStd" class="g03-no-std">平台还没有该品种的标准体重，只记录实际体重。</div>
        </zw-card>

        <zw-card title="称重记录" :sub="`${formatNumber(store.recordsTotal)} 条`" class="g03-record-card">
          <template #extra><a class="zw-link" @click="openWeigh()">称重登记</a></template>
          <v-skeleton-loader v-if="store.recordsLoading" type="list-item-two-line@3" />
          <div v-else-if="!store.records.length" class="g03-empty-line"><span class="zw-faint">还没有称重记录</span></div>
          <div v-for="row in store.records" :key="row.id" class="g03-rec">
            <span class="zw-ico-tile" :class="row.out_of_range ? 'zw-ico-tile--warning' : 'zw-ico-tile--primary'"><zw-icon name="scale" :size="18" /></span>
            <div>
              <div class="g03-rec-head">
                <span class="zw-cell-main">{{ monthDay(row.weigh_date) }} · {{ row.day_age }} 日龄</span>
                <zw-tag v-if="recordTag(row)" :type="recordTag(row)?.type" size="small">{{ recordTag(row)?.text }}</zw-tag>
              </div>
              <div class="zw-cell-sub">平均 {{ formatNumber(row.avg_weight || 0, 1) }} 千克<template v-if="row.std_weight"> · 标准 {{ formatNumber(row.std_weight, 1) }} 千克</template><template v-if="row.daily_gain"> · 日增重 {{ formatNumber(row.daily_gain, 2) }} 千克</template></div>
              <div class="g03-rec-foot">
                <span class="zw-cell-sub">{{ row.mode === 'EACH' ? `逐头 ${row.sample_count || 0} 头` : `抽样 ${row.sample_count || 0} 头` }} · {{ row.recorder_name || '—' }}登记</span>
                <a v-if="row.action && row.action !== 'NONE'" class="zw-link body-s" @click="handleRecord(row)">{{ row.action === 'EDIT' ? '修改' : '提交修改申请' }}</a>
              </div>
            </div>
          </div>
          <template v-if="store.recordsTotal > store.query.size" #foot>
            <zw-pager :total="store.recordsTotal" :page="store.query.page" :size="store.query.size" @update:page="store.setPage" @update:size="store.setSize" />
          </template>
        </zw-card>
      </div>
    </template>

    <!-- 称重登记 -->
    <zw-drawer v-model="store.drawer" :title="store.drawerMode === 'edit' ? '修改称重' : '称重登记'" :sub="`${farm.farmName || '当前养殖场'} · 提交后更新生长曲线和生长标记`" :width="560" @update:model-value="(value: unknown) => { if (!value) closeDrawer() }">
      <div v-if="store.formLoading">
        <v-skeleton-loader type="image, article, table-row@4" />
      </div>
      <zw-empty v-else-if="store.formError" icon="cloud_off" tone="error" title="称重表单加载失败" :desc="store.formError">
        <v-btn variant="outlined" @click="closeDrawer">关闭</v-btn>
      </zw-empty>
      <template v-else-if="store.form">
        <div class="g03-batch">
          <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="scale" :size="18" /></span>
          <div>
            <div class="zw-cell-main"><span class="num">{{ store.form.batch?.batch_no }}</span> · {{ store.form.batch?.pen_names }}</div>
            <div class="zw-cell-sub">{{ store.form.batch?.breed_name }} · {{ store.form.batch?.day_age }} 日龄 · 存栏 {{ formatNumber(store.form.batch?.current_count || 0) }} 头<template v-if="store.form.last_weigh"> · 上次称重 {{ monthDay(store.form.last_weigh.date) }} · 平均 {{ formatNumber(store.form.last_weigh.avg_weight || 0, 1) }} 千克</template><template v-else> · 首次称重<template v-if="store.form.batch?.entry_avg_weight"> · 入栏平均 {{ formatNumber(store.form.batch.entry_avg_weight, 1) }} 千克</template><template v-else> · 入栏未填平均体重</template></template></div>
          </div>
        </div>

        <zw-notice v-if="store.form.can_register === false" type="error" class="mb-4">{{ store.form.block_message || '这个批次现在不能登记称重' }}</zw-notice>

        <div class="zw-form-2">
          <zw-form-item label="录入方式" required>
            <zw-seg :model-value="store.formState.mode" :items="modeItems" @update:model-value="onMode" />
          </zw-form-item>
          <zw-form-item label="称重日期" required :error="fieldError('weigh_date')">
            <FeedingDateField :model-value="store.formState.weighDate" :max="localDate" :min="store.form.batch?.entry_date || ''" :disabled="store.drawerMode === 'edit'" :error="!!fieldError('weigh_date')" @update:model-value="onWeighDate" />
          </zw-form-item>
          <template v-if="store.formState.mode === 'SAMPLE'">
            <zw-form-item label="抽样数" required :error="fieldError('sample_count')" :hint="`抽样数不能超过存栏 ${store.form.stock_count || 0} 头`">
              <v-text-field v-model.number="store.formState.sampleCount" type="number" min="1" :max="store.form.stock_count" suffix="头" :error="!!fieldError('sample_count')" />
            </zw-form-item>
            <zw-form-item label="总重" required :error="fieldError('total_weight')" hint="最多 1 位小数">
              <v-text-field v-model.number="store.formState.totalWeight" type="number" min="0" step="0.1" suffix="千克" :error="!!fieldError('total_weight')" />
            </zw-form-item>
          </template>
        </div>

        <template v-if="store.formState.mode === 'EACH'">
          <div class="g03-each-head">
            <span class="zw-muted">已录 {{ store.formState.items.filter((item) => Number(item.weight || 0) > 0).length }} 头 · 合计 {{ formatNumber(store.formState.items.reduce((sum, item) => sum + (Number(item.weight) || 0), 0), 1) }} 千克</span>
            <span class="body-s zw-faint">录入头数不能超过存栏 {{ store.form.stock_count || 0 }} 头</span>
          </div>
          <div class="g03-heads">
            <div v-for="(item, index) in store.formState.items" :key="index" class="g03-head-row">
              <span class="zw-muted num">第 {{ index + 1 }} 头</span>
              <v-text-field v-model.number="item.weight" type="number" min="0" step="0.1" suffix="千克" density="compact" hide-details :error="!!fieldError(`item-${index}`)" />
              <zw-icon name="close" :size="18" class="g03-remove" @click="store.removeItem(index)" />
            </div>
          </div>
          <v-btn variant="text" color="primary" prepend-icon="add" class="mt-2" :disabled="Number(store.form.stock_count || 0) > 0 && store.formState.items.length >= Number(store.form.stock_count)" @click="store.addItem()">添加一头</v-btn>
          <div v-if="fieldError('items')" class="g03-err">{{ fieldError('items') }}</div>
        </template>

        <zw-notice v-if="store.formSampleLow" type="warning" icon="info" class="mt-4">
          <b>抽样数偏少。</b>{{ store.formSampleText }}，否则平均体重可能不准。仍可以保存。
        </zw-notice>

        <div class="g03-res">
          <div class="g03-res-head">
            <span class="zw-muted">平均体重</span>
            <zw-tag v-if="store.formOutOfRange" type="warning" :icon="store.formGrowthResult === 'SLOW' ? 'trending_down' : 'trending_up'">{{ store.formGrowthText }}</zw-tag>
            <zw-tag v-else-if="store.formGrowthResult === 'NORMAL'" size="small">在允许范围内</zw-tag>
          </div>
          <div class="g03-res-big"><b class="num">{{ store.formAvg === null ? '—' : formatNumber(store.formAvg, 1) }}</b><span class="zw-muted">千克</span></div>
          <div class="body-s zw-faint num" v-if="store.formAvg !== null">{{ store.formState.mode === 'SAMPLE' ? `${formatNumber(store.formState.totalWeight || 0, 1)} 千克 ÷ ${store.formState.sampleCount} 头` : `合计 ${formatNumber(store.formState.items.reduce((sum, item) => sum + (Number(item.weight) || 0), 0), 1)} 千克 ÷ ${store.formState.items.filter((item) => Number(item.weight || 0) > 0).length} 头` }}</div>
          <div class="g03-res-div" />
          <div class="g03-res-row"><span>标准体重（{{ store.formState.weighDate ? dayAgeAt(store.form.batch?.entry_date, store.formState.weighDate) : store.form.day_age }} 日龄）</span><span class="num">{{ store.formStdWeight === null ? '无数据' : `${formatNumber(store.formStdWeight, 1)} 千克` }}</span></div>
          <div class="g03-res-row"><span>偏离标准</span><span class="num" :class="{ 'g03-err': store.formOutOfRange }">{{ store.formDeviation === null ? '—' : deviationText(store.formDeviation) }}（允许 ±{{ Math.round(store.formAllowed * 100) }}%）</span></div>
          <div class="g03-res-row"><span>日增重（与 {{ store.formGainBaseDate ? monthDay(store.formGainBaseDate) : '上次称重' }} 比）</span><span class="num">{{ store.formDailyGain === null ? '—' : `${formatNumber(store.formDailyGain, 2)} 千克` }}</span></div>
          <div v-if="store.form.first_weigh && store.form.batch?.entry_avg_weight === undefined" class="body-s zw-faint mt-1">入栏时没有填写平均体重，首次称重不计算日增重。</div>
          <div v-else-if="store.formGrowthResult === 'SLOW'" class="body-s zw-faint mt-1">提交后批次标为生长偏慢，下次称重回到范围内自动取消</div>
          <div v-else-if="store.formGrowthResult === 'FAST'" class="body-s zw-faint mt-1">提交后批次标为生长偏快，下次称重回到范围内自动取消</div>
          <div v-else-if="store.formGrowthResult === 'NORMAL'" class="body-s zw-faint mt-1">在允许范围内</div>
        </div>

        <div class="g03-cycle">
          <span class="zw-muted">称重周期 <span v-if="!store.form.weigh_cycle?.customized" class="zw-faint">（品种建议值）</span></span>
          <span v-if="!store.cycleEditing" class="g03-cycle-r">
            <span class="num">{{ store.cycleText() }}</span>
            <a v-if="canManage" class="zw-link ml-3" @click="store.cycleEditing = true; store.cycleInput = Number(store.form.weigh_cycle?.per_month || 2)">修改</a>
          </span>
          <span v-else class="g03-cycle-edit">
            <v-text-field v-model.number="store.cycleInput" type="number" min="1" max="31" suffix="次 / 月" density="compact" hide-details class="g03-cycle-input" />
            <v-btn size="small" variant="text" :loading="store.cycleSaving" @click="onCycleSave(true)">恢复建议值</v-btn>
            <v-btn size="small" color="primary" :loading="store.cycleSaving" @click="onCycleSave(false)">保存</v-btn>
            <v-btn size="small" variant="text" @click="store.cycleEditing = false">取消</v-btn>
          </span>
        </div>
        <div v-if="store.cycleError" class="g03-err">{{ store.cycleError }}</div>
      </template>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="closeDrawer">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" :disabled="store.form?.can_register === false" @click="store.submit()">{{ store.drawerMode === 'edit' ? '保存修改' : '提交称重' }}</v-btn>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.g03-layout { display: flex; gap: 16px; align-items: flex-start; }
.g03-layout > :first-child { flex: 1 1 0; min-width: 0; }
.g03-record-card { width: 400px; flex-shrink: 0; }
.g03-legend { display: flex; align-items: center; gap: 16px; font-size: 12px; color: var(--text-2); margin-bottom: 4px; flex-wrap: wrap; }
.g03-legend > span { display: inline-flex; align-items: center; gap: 6px; }
.g03-legend .zw-chart-unit { margin-left: auto; }
.g03-band { display: inline-block; width: 14px; height: 10px; border-radius: 2px; background: var(--chart-ref); opacity: .22; }
.g03-chip { display: inline-flex; align-items: center; gap: 6px; height: 24px; padding: 0 6px 0 8px; border: 1px solid var(--line-2); border-radius: var(--radius-xs); font-size: 12px; color: var(--text-1); background: var(--bg-card); }
.g03-chip i { width: 10px; height: 3px; border-radius: 2px; background: var(--chart-2); }
.g03-chip :deep(.zw-mso) { color: var(--text-3); cursor: pointer; }
.g03-ann { font-size: 12px; fill: var(--text-2); font-variant-numeric: tabular-nums; }
.g03-ann--p { fill: var(--primary); font-weight: 600; }
.g03-no-std { margin-top: 8px; font-size: 12px; color: var(--text-3); }
.g03-rec { display: flex; align-items: flex-start; gap: 12px; padding: 12px 0; }
.g03-rec + .g03-rec { border-top: 1px solid var(--line-1); }
.g03-rec > div { flex: 1; min-width: 0; }
.g03-rec-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.g03-rec-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 2px; }
.g03-empty-line { padding: 16px 0; text-align: center; }
.g03-batch { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: var(--radius-sm); background: var(--fill-1); border: 1px solid var(--line-1); margin-bottom: 20px; }
.g03-batch > div { flex: 1; min-width: 0; }
.g03-each-head { display: flex; align-items: center; justify-content: space-between; margin: 16px 0 8px; }
.g03-heads { border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.g03-head-row { display: grid; grid-template-columns: 64px minmax(0, 1fr) 32px; align-items: center; gap: 8px; padding: 6px 12px; }
.g03-head-row + .g03-head-row { border-top: 1px solid var(--line-1); }
.g03-remove { color: var(--text-3); cursor: pointer; }
.g03-res { margin-top: 16px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); padding: 16px; }
.g03-res-head { display: flex; align-items: center; justify-content: space-between; }
.g03-res-big { display: flex; align-items: baseline; gap: 6px; margin-top: 4px; }
.g03-res-big b { font-size: 32px; line-height: 40px; font-weight: 600; }
.g03-res-div { height: 1px; background: var(--line-1); margin: 12px 0 8px; }
.g03-res-row { display: flex; justify-content: space-between; padding: 6px 0; gap: 12px; }
.g03-res-row span:first-child { color: var(--text-2); }
.g03-err { color: var(--error); }
.g03-cycle { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; padding: 10px 14px; border-radius: var(--radius-sm); background: var(--fill-1); gap: 12px; }
.g03-cycle-r, .g03-cycle-edit { display: inline-flex; align-items: center; gap: 8px; }
.g03-cycle-input { width: 130px; }
.g03-compare-pop { width: 360px; }
.g03-compare-row { display: flex; align-items: center; gap: 8px; padding: 8px 12px; font-size: 13px; cursor: pointer; }
.g03-compare-row.dis { opacity: .55; }
.g03-compare-row > .num { white-space: nowrap; }
.g03-compare-row .zw-faint { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.g03-compare-empty { padding: 12px; }
.g03-compare-foot { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px 12px; border-top: 1px solid var(--line-1); margin-top: 6px; }
</style>
