<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatNumber } from '@zmt/shared'
import { useFeedPlanStore, type StageForm } from '@/stores/feeding/feedPlan'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { deviationText, monthDay, monthDayWeek, percentText } from '@/stores/feeding/shared'
import type { FeedAttentionItem } from '@/api/feeding'
import FeedDailyChart from './components/FeedDailyChart.vue'

const store = useFeedPlanStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const { isStaff } = useRole()
const expanded = ref('')
const dayMenu = ref(false)

const canView = computed(() => !isStaff.value)
const batchItems = computed(() => {
  const items = store.batchOptions.map((batch) => ({
    title: `${batch.batchNo} · ${batch.penNames}`,
    value: batch.batchId,
    hint: [batch.breedName, batch.dayAge ? `${batch.dayAge} 日龄` : '', batch.hasPlan ? '已设置方案' : '未设置'].filter(Boolean).join(' · ')
  }))
  if (store.tab === 'exec') return [{ title: '全部批次', value: 0, hint: '汇总全部在养批次' }, ...items]
  return items
})
const desc = computed(() => {
  if (!canView.value) return ''
  if (store.tab === 'exec' && store.batchId === 0) return '全部批次 · 统计截至昨天，今天还没结束的餐次不计入'
  return store.planTitleDesc
})
const summaryEmpty = computed(() => !store.execLoading && !!store.execSummary && !store.execSummary.has_data)
const abnormalValue = computed(() => formatNumber(store.execSummary?.abnormal_count || 0))
const execRateValue = computed(() => (store.execSummary?.exec_rate === undefined ? '—' : percentText(store.execSummary.exec_rate, 1)))
const actualQtyValue = computed(() => formatNumber(store.execSummary?.actual_qty || 0, 1))
const planQtyValue = computed(() => formatNumber(store.execSummary?.plan_qty || 0, 1))
const attentionSub = computed(() => `${formatNumber(store.attentionTotal)} 条`)
const mealOptions = [1, 2, 3, 4].map((value) => ({ title: `每天 ${value} 餐`, value }))
const rangeItems = store.execRangeItems

watch(() => store.stages, (stages: StageForm[]) => {
  if (!stages.length) {
    expanded.value = ''
    return
  }
  if (expanded.value && stages.some((stage, index) => stageKey(stage, index) === expanded.value)) return
  const currentIndex = stages.findIndex((stage) => stage.state === 'CURRENT')
  const target = currentIndex >= 0 ? stages[currentIndex] : stages.find((stage) => stage.editable) || stages[0]
  if (target) expanded.value = stageKey(target, stages.indexOf(target))
}, { deep: false })

function stageKey(stage: { stageId: number }, index: number): string {
  return stage.stageId ? String(stage.stageId) : `new-${index}`
}

function isExpanded(stage: StageForm, index: number): boolean {
  return stage.editable && expanded.value === stageKey(stage, index)
}

function toggleStage(stage: { stageId: number; editable: boolean }, index: number): void {
  if (!stage.editable) return
  const key = stageKey(stage, index)
  expanded.value = expanded.value === key ? '' : key
}

function stageError(index: number, field: string): string {
  return store.stageErrors[`${index}-${field}`] || ''
}

function stageAge(stage: { dayAgeStart: number; dayAgeEnd?: number }): string {
  return stage.dayAgeEnd === undefined ? `${stage.dayAgeStart} 日龄至出栏` : `${stage.dayAgeStart}–${stage.dayAgeEnd} 日龄`
}

function stageSummary(stage: { feedName: string; dailyQtyPerHead: number | null; mealsPerDay: number; lowStock: boolean }): string {
  return [
    stage.feedName || '未选择饲料',
    stage.dailyQtyPerHead === null ? '未设置投喂量' : `每头 ${stage.dailyQtyPerHead} 千克`,
    `${stage.mealsPerDay} 餐`,
    stage.lowStock ? '库存不足' : ''
  ].filter(Boolean).join(' · ')
}

function stageDateText(stage: { state: string; execStartDate?: string; execEndDate?: string; enterDate?: string }): string {
  if (stage.state === 'CURRENT') {
    return `${stage.execStartDate ? monthDay(stage.execStartDate) : ''}起执行${stage.execEndDate ? ` · 至 ${monthDay(stage.execEndDate)}` : ''}`
  }
  if (stage.state === 'ENDED') return '已结束'
  return stage.enterDate ? `${monthDay(stage.enterDate)}进入，次日起自动换用` : '待进入'
}

function onMeals(stage: StageForm, value: unknown): void {
  if (!stage.editable) return
  store.setStageMeals(stage, Number(value))
  store.recalcStageSummary()
}

function onStageQty(stage: StageForm, value: unknown): void {
  store.setStageQty(stage, String(value ?? ''))
  store.recalcStageSummary()
}

function onStageTime(stage: StageForm, index: number, value: unknown): void {
  store.setStageTime(stage, index, String(value || ''))
}

function stagePlanText(stage: StageForm): string {
  if (stage.state === 'CURRENT') return store.todayPlanText
  const count = Number(store.plan?.batch?.current_count || 0)
  const total = Number(stage.dailyQtyPerHead || 0) * count
  if (!total) return '按当前存栏估算'
  return `${total.toLocaleString('en-US')} 千克 · 每餐 ${(total / Math.max(1, stage.mealsPerDay)).toLocaleString('en-US')} 千克`
}

function onBatch(value: unknown): void {
  const batchId = Number(value || 0)
  if (batchId === 0 && store.tab === 'plan') return
  void store.setBatch(batchId)
  void router.replace({ query: { ...route.query, batchId: String(batchId), tab: store.tab } })
}

function onTab(value: unknown): void {
  const tab = value === 'exec' ? 'exec' : 'plan'
  if (tab === 'plan' && store.batchId === 0) {
    const first = store.batchOptions[0]
    if (first) void store.setBatch(first.batchId)
  }
  void store.setTab(tab)
  void router.replace({ query: { ...route.query, tab } })
}

function onRange(value: unknown): void {
  void store.setRange(String(value) as 'LAST_7' | 'LAST_30' | 'BATCH')
}

function openThreshold(): void {
  store.openThreshold()
}

function fieldError(field: string): string {
  return store.stageErrors[field] || ''
}

function attentionTag(item: FeedAttentionItem): { text: string; type: string } {
  if (item.type === 'ABNORMAL') return { text: '投喂异常', type: 'warning' }
  return { text: '已逾期', type: 'error' }
}

function attentionIcon(item: FeedAttentionItem): string {
  return item.type === 'ABNORMAL' ? 'rice_bowl' : 'schedule'
}

function openAttention(item: FeedAttentionItem): void {
  void store.openAttention(item)
}

function openRecord(item: FeedAttentionItem): void {
  if (item.record_id) void store.openAttention(item)
}

function closeAttention(): void {
  store.attentionDrawer = false
}

function exportCsv(): void {
  const summary = store.execSummary
  if (!summary?.daily?.length) return
  const header = ['日期', '实际投喂量(千克)', '计划量(千克)', '已完成餐次', '计划餐次', '偏差']
  const lines = [header, ...(summary.daily || []).map((day) => [
    day.date || '',
    String(day.actual_qty ?? 0),
    String(day.plan_qty ?? 0),
    String(day.done_meals ?? 0),
    String(day.planned_meals ?? 0),
    deviationText(day.deviation_ratio)
  ])]
  const csv = lines.map((line) => line.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\r\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `饲喂执行情况-${summary.start_date || ''}-${summary.end_date || ''}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

async function init(): Promise<void> {
  if (isStaff.value) {
    store.status = 'forbidden'
    store.error = '你没有查看饲喂方案的权限'
    return
  }
  const queryBatch = Number(route.query.batchId || 0)
  if (queryBatch) store.batchId = queryBatch
  await store.load()
  if (route.query.tab === 'exec') await store.setTab('exec')
}

onMounted(init)

useFarmReload(async () => {
  store.reset()
  await init()
})

watch(() => store.tab, (tab) => {
  if (tab === 'exec' && !store.execSummary) void store.loadExec(true)
})
</script>

<template>
  <zw-shell side="user" active="WU-G02" :crumbs="isStaff ? ['我的栏舍', '饲喂方案与执行情况'] : ['生产管理', '饲喂管理', '饲喂方案与执行情况']">
    <zw-page-head back :title="'饲喂方案与执行情况'" :desc="desc" @click="(event: MouseEvent) => { if ((event.target as HTMLElement)?.closest('.zw-back')) router.push({ name: 'WU-G01' }) }">
      <template v-if="canView" #actions>
        <span class="zw-filter-label">批次</span>
        <v-select
          :model-value="store.batchId"
          :items="batchItems"
          item-title="title"
          item-value="value"
          class="zw-w-280"
          @update:model-value="onBatch"
        >
          <template #item="{ props: itemProps, item }">
            <v-list-item v-bind="itemProps" :subtitle="item.raw.hint" />
          </template>
        </v-select>
      </template>
      <v-tabs v-if="canView" :model-value="store.tab" class="zw-tabs-line" @update:model-value="onTab">
        <v-tab value="plan">饲喂方案</v-tab>
        <v-tab value="exec">执行情况</v-tab>
      </v-tabs>
    </zw-page-head>

    <div v-if="store.status === 'forbidden'">
      <zw-card>
        <zw-empty icon="lock" tone="warning" title="你没有查看饲喂方案的权限" desc="饲喂方案和执行统计只有所有者和场长可以查看。如需调整投喂量，请联系所有者或场长。">
          <v-btn color="primary" variant="tonal" @click="router.push({ name: 'WU-G01' })">返回饲喂任务</v-btn>
        </zw-empty>
      </zw-card>
    </div>

    <div v-else-if="store.status === 'loading' || (store.tab === 'plan' && store.planLoading)">
      <v-skeleton-loader type="article, table-row@8" />
    </div>

    <template v-else-if="store.tab === 'plan'">
      <zw-card v-if="store.planError || store.status === 'error'" flush>
        <zw-empty icon="cloud_off" tone="error" title="饲喂方案加载失败" :desc="store.planError || store.error">
          <v-btn color="primary" variant="outlined" @click="store.loadPlan()">重新加载</v-btn>
        </zw-empty>
      </zw-card>

      <div v-else class="g02-layout">
        <zw-card title="按生长阶段" sub="批次进入下一生长阶段后，次日自动换用新的投喂量">
          <div v-if="!store.stages.length" class="g02-no-plan">
            <zw-empty icon="tune" title="这个批次还没有饲喂方案" desc="套用品种标准后再按需调整，保存后系统按方案生成每天的饲喂任务。">
              <v-btn color="primary" :loading="store.stdLoading" @click="store.applyStandard()">套用品种标准</v-btn>
            </zw-empty>
          </div>

          <template v-else>
            <div
              v-for="(stage, index) in store.stages"
              :key="stageKey(stage, index)"
              class="g02-stage"
              :class="{ on: stage.state === 'CURRENT', open: isExpanded(stage, index), ended: stage.state === 'ENDED' }"
            >
              <div class="g02-stage-head" @click="toggleStage(stage, index)">
                <span class="g02-stage-name">{{ stage.stageName }}</span>
                <span class="g02-stage-age num">{{ stageAge(stage) }}</span>
                <zw-tag v-if="stage.state === 'CURRENT'" type="primary">当前阶段</zw-tag>
                <zw-tag v-else-if="stage.state === 'ENDED'" size="small">已结束</zw-tag>
                <span v-if="!isExpanded(stage, index)" class="g02-stage-sum">{{ stageSummary(stage) }}</span>
                <span class="g02-stage-r">{{ stageDateText(stage) }}<zw-icon v-if="stage.editable" :name="isExpanded(stage, index) ? 'expand_less' : 'expand_more'" :size="20" /></span>
              </div>

              <div v-if="isExpanded(stage, index)" class="g02-stage-body">
                <div class="g02-form">
                  <zw-form-item label="饲料" required :error="stageError(index, 'feed')">
                    <v-select v-model="stage.feedId" :items="store.feedOptionsFor()" item-title="title" item-value="value" :error="!!stageError(index, 'feed')">
                      <template #item="{ props: itemProps, item }">
                        <v-list-item v-bind="itemProps" :subtitle="item.raw.hint">
                          <template #append><zw-tag v-if="item.raw.low" type="warning" size="small">库存不足</zw-tag></template>
                        </v-list-item>
                      </template>
                    </v-select>
                  </zw-form-item>
                  <zw-form-item label="每头日投喂量" required :error="stageError(index, 'qty')" :hint="stage.stdDailyQty === null ? '平台没有该阶段的标准值' : `品种标准 ${stage.stdDailyQty} 千克`">
                    <v-text-field :model-value="stage.dailyQtyPerHead" type="number" min="0" step="0.1" suffix="千克" :error="!!stageError(index, 'qty')" @update:model-value="(value: unknown) => onStageQty(stage, value)" />
                  </zw-form-item>
                  <zw-form-item label="餐次" required>
                    <v-select :model-value="stage.mealsPerDay" :items="mealOptions" item-title="title" item-value="value" @update:model-value="(value: unknown) => onMeals(stage, value)" />
                  </zw-form-item>
                  <zw-form-item label="投喂时间" required style="grid-column: 1 / -1" :error="stageError(index, 'time')" hint="时间个数等于餐次，保存时按先后排序">
                    <div class="g02-times">
                      <label v-for="(time, timeIndex) in stage.mealTimes" :key="timeIndex" class="g02-time">
                        <zw-icon name="schedule" :size="16" />
                        <input type="time" :value="time" @input="onStageTime(stage, timeIndex, ($event.target as HTMLInputElement).value)" />
                      </label>
                    </div>
                  </zw-form-item>
                </div>
                <div class="g02-total">
                  <span class="zw-muted">{{ stage.state === 'CURRENT' ? '今天计划量' : '按当前存栏估算' }} <span v-if="stage.state === 'CURRENT'" class="zw-faint num">（{{ store.todayPlanFormula }}）</span></span>
                  <b class="num">{{ stagePlanText(stage) }}</b>
                </div>
              </div>
            </div>

            <div class="g02-rule">
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="percent" :size="18" /></span>
              <div>
                <div class="zw-cell-main">投喂异常判定</div>
                <div class="zw-cell-sub">实际偏离计划量超过 {{ store.abnormalThreshold.toFixed(0) }}% 时标为投喂异常</div>
              </div>
              <a class="zw-link" @click="openThreshold">修改</a>
            </div>
            <div v-if="store.stageErrors.threshold" class="g02-err mt-2">{{ store.stageErrors.threshold }}</div>
          </template>

          <template #foot>
            <span class="zw-foot-note">保存后系统按新方案生成之后的饲喂任务，已生成的今日任务不变</span>
            <v-btn variant="outlined" :disabled="store.saving" @click="store.resetPlanChanges()">取消</v-btn>
            <v-btn color="primary" :loading="store.saving" :disabled="!store.stages.length" @click="store.savePlan()">保存方案</v-btn>
          </template>
        </zw-card>

        <div class="g02-side">
          <zw-card title="品种标准">
            <div class="g02-std-head">
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="rule" :size="18" /></span>
              <div>
                <div class="zw-cell-main">{{ store.stdName || '品种标准' }}</div>
                <div class="zw-cell-sub">
                  <template v-if="store.stdCard.applied_at">{{ monthDayWeek(store.stdCard.applied_at) }}套用<template v-if="store.adjustedCount"> · 你调整了 {{ store.adjustedCount }} 项</template></template>
                  <template v-else>还没有套用品种标准</template>
                  <template v-if="store.stdCard.std_updated"> · 标准已更新</template>
                </div>
              </div>
            </div>
            <table v-if="store.compareRows.length" class="g02-std">
              <thead><tr><th>阶段</th><th class="r">标准</th><th class="r">本方案</th></tr></thead>
              <tbody>
                <tr v-for="row in store.compareRows" :key="row.name">
                  <td>{{ row.name }}</td>
                  <td class="r num">{{ row.std }}</td>
                  <td class="r num">{{ row.actual }} <zw-tag v-if="row.adjusted" type="warning" size="small">已调整</zw-tag></td>
                </tr>
              </tbody>
            </table>
            <v-btn variant="tonal" color="primary" block class="mt-4" :loading="store.stdLoading" @click="store.applyStandard()">套用品种标准</v-btn>
          </zw-card>

          <zw-card title="修改记录">
            <v-skeleton-loader v-if="store.logsLoading" type="list-item-two-line@2" />
            <zw-timeline v-else-if="store.planLogItems.length" :items="store.planLogItems" />
            <div v-else class="g02-empty-line"><span class="zw-faint">还没有修改记录</span></div>
          </zw-card>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="zw-toolbar">
        <div class="zw-toolbar-l">
          <zw-seg :model-value="store.execRange" :items="rangeItems" @update:model-value="onRange" />
          <v-menu v-model="dayMenu" :close-on-content-click="false" location="bottom start">
            <template #activator="{ props: activatorProps }">
              <v-text-field v-bind="activatorProps" :model-value="store.execRangeLabel" readonly append-inner-icon="date_range" class="zw-w-220" />
            </template>
            <v-date-picker
              color="primary"
              multiple="range"
              :max="new Date().toISOString().slice(0, 10)"
              @update:model-value="(value: unknown) => { const list = Array.isArray(value) ? value : []; if (list.length === 2) { void store.setCustomRange(String(list[0]), String(list[1])); dayMenu = false } }"
            />
          </v-menu>
          <span class="g02-range"><zw-icon name="info" :size="16" />{{ store.execRangeLabel }} · 统计截至昨天，今天还没结束的餐次不计入</span>
        </div>
        <div class="zw-toolbar-r"><v-btn variant="outlined" prepend-icon="download" :disabled="!store.execSummary?.daily?.length" @click="exportCsv">导出</v-btn></div>
      </div>

      <div v-if="store.execError">
        <zw-card flush><zw-empty icon="cloud_off" tone="error" title="执行情况加载失败" :desc="store.execError"><v-btn color="primary" variant="outlined" @click="store.loadExec(true)">重新加载</v-btn></zw-empty></zw-card>
      </div>
      <div v-else-if="store.execLoading">
        <div class="zw-grid-4 mb-4"><v-skeleton-loader v-for="item in 4" :key="item" type="article" /></div>
        <v-skeleton-loader type="image, table-row@4" />
      </div>

      <template v-else>
        <div class="zw-grid-4 mb-4">
          <zw-stat label="执行率" icon="task_alt" :value="execRateValue" unit="%" :foot="`已完成 ${formatNumber(store.execSummary?.done_meals || 0)} / ${formatNumber(store.execSummary?.planned_meals || 0)} 餐`" />
          <zw-stat label="投喂偏差" icon="swap_vert" :value="store.deviationText" unit="%" :foot="store.deviationFoot" />
          <zw-stat label="累计用量" icon="rice_bowl" :value="actualQtyValue" unit="千克" :foot="`计划 ${planQtyValue} 千克`" />
          <zw-stat label="投喂异常" icon="warning" :value="abnormalValue" unit="次" :tone="Number(store.execSummary?.abnormal_count || 0) > 0 ? 'warning' : undefined" :foot="store.abnormalFoot" />
        </div>

        <zw-card v-if="summaryEmpty" flush>
          <zw-empty icon="bar_chart" title="这段时间没有饲喂任务" :desc="store.execSummary?.empty_reason === 'NO_ACTIVE_BATCH' ? '还没有在养批次。批次入栏并设置饲喂方案后，这里会显示执行情况。' : '换一个时间范围或批次看看。'" />
        </zw-card>

        <div v-else class="g02-exec-layout">
          <zw-card title="每日投喂量" :sub="store.batchId ? `${store.selectedBatch?.batchNo || ''} · 点柱子查看当天明细` : '全部批次汇总'">
            <div class="zw-chart-legend">
              <span><i class="zw-lg-box" style="--c: var(--chart-1)" />实际</span>
              <span><i class="zw-lg-dash" style="--c: var(--chart-ref)" />计划{{ store.execSummary?.plan_daily_qty ? ` ${formatNumber(store.execSummary.plan_daily_qty)} 千克` : '' }}</span>
              <span class="zw-chart-unit">单位：千克</span>
            </div>
            <FeedDailyChart
              :daily="store.execSummary?.daily || []"
              :plan-qty="Number(store.execSummary?.plan_daily_qty || 0)"
              :line-type="store.execSummary?.plan_line_type || 'FLAT'"
            />
          </zw-card>

          <zw-card title="需要关注" :sub="attentionSub" class="g02-att-card">
            <div v-if="!store.attention.length" class="g02-empty-line"><span class="zw-faint">这段时间没有投喂异常和逾期未投的餐次</span></div>
            <div v-for="item in store.attention" :key="`${item.type}-${item.task_id}-${item.date}`" class="g02-att" @click="openAttention(item)">
              <span class="zw-ico-tile" :class="item.type === 'ABNORMAL' ? 'zw-ico-tile--warning' : 'zw-ico-tile--error'"><zw-icon :name="attentionIcon(item)" :size="18" /></span>
              <div>
                <div class="zw-cell-main">{{ item.is_today ? '今天' : monthDayWeek(item.date) }} {{ item.meal_name }}<span v-if="store.batchId === 0 && item.batch_no" class="zw-faint"> · {{ item.batch_no }}</span></div>
                <div class="zw-cell-sub">{{ item.type === 'ABNORMAL' ? `实投 ${formatNumber(item.actual_qty || 0, 1)} 千克 · 计划 ${formatNumber(item.plan_qty || 0, 1)} 千克` : `计划 ${formatNumber(item.plan_qty || 0, 1)} 千克 · 未登记` }}<template v-if="item.executor_name"> · {{ item.executor_name }}</template></div>
              </div>
              <zw-tag :type="attentionTag(item).type" :icon="item.type === 'OVERDUE' ? 'schedule' : undefined">{{ attentionTag(item).text }}</zw-tag>
            </div>
            <template v-if="store.attentionTotal > store.attentionSize" #foot>
              <zw-pager :total="store.attentionTotal" :page="store.attentionPage" :size="store.attentionSize" @update:page="store.setAttentionPage" />
            </template>
          </zw-card>
        </div>
      </template>
    </template>

    <!-- 投喂异常判定 -->
    <zw-dialog v-model="store.thresholdDialog" title="投喂异常判定" :width="420">
      <div class="zw-form">
        <zw-form-item label="偏差比例" required :error="store.thresholdError" hint="实际投喂量偏离计划量超过这个比例时，标为投喂异常">
          <v-text-field v-model.number="store.thresholdInput" type="number" min="1" max="100" suffix="%" :error="!!store.thresholdError" />
        </zw-form-item>
      </div>
      <template #actions>
        <v-btn variant="outlined" @click="store.thresholdDialog = false">取消</v-btn>
        <v-btn color="primary" :loading="store.saving" @click="store.saveThreshold()">保存</v-btn>
      </template>
    </zw-dialog>

    <!-- 关注记录详情 -->
    <zw-drawer v-model="store.attentionDrawer" title="投喂记录" :sub="store.attentionItem ? `${monthDayWeek(store.attentionItem.date)} ${store.attentionItem.meal_name}` : ''" width="560" @update:model-value="(value: unknown) => { if (!value) closeAttention() }">
      <div v-if="store.attentionRecordLoading"><v-skeleton-loader type="article, table-row@3" /></div>
      <template v-else-if="store.attentionRecord">
        <zw-desc :cols="1" bordered :label-width="100">
          <zw-desc-item label="栏舍">{{ store.attentionRecord.pen_name }} · {{ store.attentionRecord.batch_no }}</zw-desc-item>
          <zw-desc-item label="饲料">{{ store.attentionRecord.feed?.feed_name || '—' }}</zw-desc-item>
          <zw-desc-item label="计划量"><span class="num">{{ formatNumber(store.attentionRecord.plan_qty || 0, 1) }} 千克</span></zw-desc-item>
          <zw-desc-item label="实际投喂量"><span class="num">{{ formatNumber(store.attentionRecord.actual_qty || 0, 1) }} 千克</span></zw-desc-item>
          <zw-desc-item label="偏差"><span class="num">{{ deviationText(store.attentionRecord.deviation_ratio) }}</span></zw-desc-item>
          <zw-desc-item label="执行人">{{ store.attentionRecord.executor?.name || '—' }}</zw-desc-item>
          <zw-desc-item label="登记时间">{{ store.attentionRecord.registered_at || '—' }}</zw-desc-item>
          <zw-desc-item label="备注">{{ store.attentionRecord.remark || '—' }}</zw-desc-item>
        </zw-desc>
        <zw-notice type="info" class="mt-4">{{ store.attentionRecord.view_mode === 'READONLY' ? '这条记录不是今天登记的，修改需要提交修改申请，由管理员审核。' : '当天记录可以在饲喂任务中修改或作废。' }}</zw-notice>
      </template>
      <zw-empty v-else icon="schedule" title="这条餐次还没有投喂记录" :desc="store.attentionItem ? `计划 ${formatNumber(store.attentionItem.plan_qty || 0, 1)} 千克 · 未登记` : ''" />
      <template #foot>
        <v-btn variant="outlined" @click="closeAttention">关闭</v-btn>
        <v-btn v-if="store.attentionRecord?.can_request_change" color="primary" variant="outlined" @click="router.push({ name: 'WU-U01', query: { biz_type: 'FEED_RECORD', biz_id: String(store.attentionRecord?.record_id || ''), action: 'create' } })">提交修改申请</v-btn>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.g02-layout { display: flex; gap: 16px; align-items: flex-start; }
.g02-layout > :first-child { flex: 1 1 0; min-width: 0; }
.g02-side { width: 340px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; }
.g02-exec-layout { display: flex; gap: 16px; align-items: flex-start; }
.g02-exec-layout > :first-child { flex: 1 1 0; min-width: 0; }
.g02-att-card { width: 400px; flex-shrink: 0; }
.g02-stage { border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.g02-stage + .g02-stage { margin-top: 12px; }
.g02-stage.on { border-color: var(--inverse-primary); }
.g02-stage-head { display: flex; align-items: center; gap: 10px; padding: 12px 16px; cursor: pointer; }
.g02-stage.on .g02-stage-head { background: var(--primary-subtle); border-bottom: 1px solid var(--primary-container); }
.g02-stage.ended .g02-stage-head { cursor: default; }
.g02-stage-name { font-size: 15px; font-weight: 600; }
.g02-stage-age { color: var(--text-2); }
.g02-stage-sum { color: var(--text-2); }
.g02-stage-r { margin-left: auto; display: flex; align-items: center; gap: 8px; color: var(--text-3); font-size: 12px; }
.g02-stage-body { padding: 16px; }
.g02-form { display: grid; gap: 16px 20px; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) minmax(0, 1fr); }
.g02-times { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.g02-time { display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 12px; border: 1px solid var(--control-border); border-radius: var(--radius-sm); font-variant-numeric: tabular-nums; color: var(--text-1); background: var(--bg-card); }
.g02-time input { border: 0; outline: 0; background: transparent; font: inherit; color: inherit; width: 74px; }
.g02-total { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; padding: 10px 14px; border-radius: var(--radius-sm); background: var(--fill-1); }
.g02-total b { font-size: 16px; }
.g02-rule { display: flex; align-items: center; gap: 12px; margin-top: 12px; padding: 12px 16px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.g02-rule > div { flex: 1; }
.g02-std-head { display: flex; align-items: center; gap: 12px; }
.g02-std-head > div { flex: 1; min-width: 0; }
.g02-std { width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 13px; }
.g02-std th { text-align: left; font-weight: 500; color: var(--text-3); padding: 6px 0; border-bottom: 1px solid var(--line-1); }
.g02-std td { padding: 8px 0; border-bottom: 1px solid var(--line-1); }
.g02-std .r { text-align: right; }
.g02-range { display: inline-flex; align-items: center; gap: 6px; color: var(--text-2); font-size: 13px; }
.g02-att { display: flex; align-items: flex-start; gap: 12px; padding: 12px 0; cursor: pointer; }
.g02-att + .g02-att { border-top: 1px solid var(--line-1); }
.g02-att > div { flex: 1; min-width: 0; }
.g02-att:hover { background: var(--fill-1); }
.g02-empty-line { padding: 16px 0; text-align: center; }
.g02-no-plan { padding: 24px 0; }
.g02-err { color: var(--error); font-size: 12px; }
</style>
