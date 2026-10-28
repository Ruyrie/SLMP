<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, formatNumber, formatPercent } from '@zmt/shared'
import {
  useAbnormalReportsStore,
  type ReportRow,
  type ReportStatus,
  type Urgency
} from '@/stores/inspect/reports'
import { useFarmReload } from '@/composables/useFarmReload'
import { queryId, useRouteAction } from '@/composables/useRouteAction'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { monthDay, timeOnly, urgencyIcon, urgencyType } from '@/views/inspect/inspect'
import AbnormalReportDrawer from './components/AbnormalReportDrawer.vue'

const store = useAbnormalReportsStore()
const farm = useFarmStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

const rangeMenu = ref(false)
const rangeStart = ref(store.query.start)
const rangeEnd = ref(store.query.end)

const isStaff = computed(() => farm.role === 'staff')
const desc = computed(() =>
  isStaff.value ? '你只能为负责栏舍上报异常，并查看自己上报的诊断' : '上报后 AI 给出可能病因、紧急程度和处理建议；处理结果由所有者或场长登记'
)
const rangeText = computed(() => `${monthDay(store.query.start)} – ${monthDay(store.query.end)}`)
const rangeLabel = computed(() => (store.isDefaultRange ? '近 30 天上报' : '筛选区间上报'))
const statusItems = computed(() => store.statusItems)
const pendingHandle = computed(() => store.pendingHandleCount)
const statsReady = computed(() => store.statsLoaded && !store.statsLoading)
const rangeFoot = computed(() =>
  statsReady.value
    ? `已处理 ${formatNumber(store.handledCount)} 条 · 已结案 ${formatNumber(store.closedCount)} 条`
    : '统计加载中'
)
const emptyTitle = computed(() => {
  if (store.hasFilter) return '没有匹配的上报记录'
  return isStaff.value ? '你还没有上报过异常' : '还没有异常上报'
})
const emptyDesc = computed(() => {
  if (store.hasFilter) return '换一个筛选条件试试。'
  return isStaff.value ? '巡检发现异常后点“转上报”。' : '巡检发现异常时点“转上报”，或点“异常上报”直接上报。'
})

/** create（快捷登记）与 report（巡检转上报）都打开异常上报表单。 */
const routeAction = useRouteAction((action, query) => {
  if (action !== 'create' && action !== 'report') return
  store.openDrawer(queryId(query, 'inspectionId', 'inspection_id'), queryId(query, 'penId', 'pen_id'))
})

onMounted(async () => {
  await store.load()
  await routeAction.start()
})
onBeforeUnmount(() => store.cancelKeywordTimer())
useFarmReload(async () => {
  await store.load()
})

function openCreate(): void {
  store.openDrawer()
}

function onCreated(reportId: number): void {
  store.drawerOpen = false
  void router.push({ name: 'WU-I04', params: { id: String(reportId) } })
}

function openDiagnosis(row: ReportRow, handle = false): void {
  void router.push({
    name: 'WU-I04',
    params: { id: String(row.report_id) },
    query: handle ? { action: 'handle' } : undefined
  })
}

async function rediagnose(row: ReportRow): Promise<void> {
  if (!row.can_rediagnose || store.rediagnosingId) return
  try {
    await store.rediagnose(row.report_id)
    ui.toast('已重新发起诊断')
  } catch (error) {
    ui.toast((error as ApiError).message || '重新诊断失败，请稍后重试', 'error')
  }
}

function setPen(value: unknown): void {
  store.setPen(Number(value || 0))
}

function setUrgency(value: unknown): void {
  store.setUrgency(String(value || 'ALL') as Urgency)
}

function setStatus(value: string | number): void {
  store.setStatus(String(value) as ReportStatus)
}

function setKeyword(value: unknown): void {
  store.setKeyword(value)
}

function openRange(): void {
  rangeStart.value = store.query.start
  rangeEnd.value = store.query.end
  rangeMenu.value = true
}

function applyRange(): void {
  if (!rangeStart.value || !rangeEnd.value || rangeStart.value > rangeEnd.value) return
  store.setRange(rangeStart.value, rangeEnd.value)
  rangeMenu.value = false
}

function resetFilters(): void {
  store.reset()
  rangeStart.value = store.query.start
  rangeEnd.value = store.query.end
}

function statValue(value: number): string {
  return statsReady.value ? formatNumber(value) : '—'
}

function statFoot(value: string): string {
  return statsReady.value ? value : '统计加载中'
}

function rateText(rate: number | null, sick: number, stock: number): string {
  if (rate !== null && rate !== undefined) return formatPercent(rate, 1)
  if (!stock) return '—'
  return formatPercent(sick / stock, 1)
}

function aiText(row: ReportRow): string {
  if (row.top_cause_name) return row.top_cause_name
  if (row.ai_state === 'DIAGNOSING' || row.ai_state === 'FAILED' || row.ai_state === 'UNAVAILABLE') {
    return row.ai_state_text || '等待诊断'
  }
  if (row.status === 'PENDING') return '等待诊断'
  return '—'
}

function statusTag(row: { status: string; status_text?: string }): { type: string; icon: string; text: string } {
  if (row.status === 'PENDING') return { type: 'neutral', icon: 'hourglass_top', text: row.status_text || '待诊断' }
  if (row.status === 'DIAGNOSED') return { type: 'primary', icon: '', text: row.status_text || '已诊断' }
  if (row.status === 'HANDLED') return { type: 'neutral', icon: '', text: row.status_text || '已处理' }
  return { type: 'neutral', icon: 'check', text: row.status_text || '已结案' }
}

function urgencyTag(row: { urgency?: string; urgency_text?: string }): { type: string; icon: string; text: string } | null {
  if (!row.urgency) return null
  return { type: urgencyType(row.urgency), icon: urgencyIcon(row.urgency), text: row.urgency_text || row.urgency }
}
</script>

<template>
  <zw-shell side="user" active="WU-I03" :crumbs="isStaff ? ['防疫健康', '异常上报'] : ['防疫健康', '异常与诊断', '异常上报']">
    <zw-page-head title="异常上报" :desc="desc">
      <template #actions>
        <v-btn color="primary" prepend-icon="report" @click="openCreate">异常上报</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading'" class="i03-loading">
      <div class="zw-grid-4 mb-4">
        <v-skeleton-loader v-for="item in 4" :key="item" type="article" />
      </div>
      <zw-card><v-skeleton-loader type="table-row@5" /></zw-card>
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '当前角色无权查看异常上报。'">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="上报记录加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div v-if="!isStaff" class="zw-grid-4 mb-4">
        <zw-stat
          label="今天上报"
          icon="report"
          :value="statValue(store.todayCount)"
          unit="次"
          :foot="statFoot(store.todayPens)"
        />
        <zw-stat
          label="紧急"
          icon="emergency"
          :value="statValue(store.urgentCount)"
          unit="条"
          tone="error"
          :foot="statFoot(store.urgentNames)"
        />
        <zw-stat
          label="待登记处理"
          icon="edit_note"
          :value="statValue(pendingHandle)"
          unit="条"
          tone="warning"
          foot="已诊断，等待登记处理结果"
        />
        <zw-stat
          :label="rangeLabel"
          icon="calendar_month"
          :value="statValue(store.totalCount)"
          unit="次"
          :foot="rangeFoot"
        />
      </div>
      <zw-notice v-else type="info" class="mb-4">
        AI 诊断仅供参考，需执业兽医确认。处理结果、转归和 AI 是否相符由所有者或场长登记。
      </zw-notice>

      <div class="zw-toolbar i03-toolbar">
        <div class="zw-toolbar-l">
          <v-text-field
            :model-value="store.query.keyword"
            placeholder="搜索栏舍 / 批次 / 病因 / 描述 / 上报人"
            prepend-inner-icon="search"
            clearable
            hide-details
            class="i03-search"
            @update:model-value="setKeyword"
          />
          <v-select
            :model-value="store.query.penId"
            :items="store.penItems"
            class="i03-select"
            hide-details
            @update:model-value="setPen"
          />
          <v-select
            :model-value="store.query.urgency"
            :items="store.urgencyItems"
            class="i03-select"
            hide-details
            @update:model-value="setUrgency"
          />
          <v-menu v-model="rangeMenu" :close-on-content-click="false">
            <template #activator="{ props: menuProps }">
              <v-text-field
                v-bind="menuProps"
                :model-value="rangeText"
                readonly
                append-inner-icon="calendar_month"
                class="i03-date"
                hide-details
                @click="openRange"
              />
            </template>
            <div class="i03-range-pop">
              <div class="i03-range-fields">
                <v-text-field v-model="rangeStart" type="date" label="开始日期" hide-details />
                <v-text-field v-model="rangeEnd" type="date" label="结束日期" hide-details />
              </div>
              <div class="i03-range-actions">
                <v-btn variant="text" @click="rangeMenu = false">取消</v-btn>
                <v-btn color="primary" @click="applyRange">确定</v-btn>
              </div>
            </div>
          </v-menu>
          <v-btn variant="text" color="primary" @click="resetFilters">重置</v-btn>
        </div>
        <div class="zw-toolbar-r">
          <zw-seg :model-value="store.query.status" :items="statusItems" size="small" @update:model-value="setStatus" />
        </div>
      </div>

      <zw-card flush>
        <v-progress-linear v-if="store.listLoading" indeterminate color="primary" height="2" />
        <div v-if="store.listLoading && !store.rows.length" class="i03-table-loading">
          <v-skeleton-loader type="table-row@5" />
        </div>
        <zw-empty
          v-else-if="!store.rows.length"
          icon="report"
          :title="emptyTitle"
          :desc="emptyDesc"
        >
          <v-btn v-if="!store.hasFilter" color="primary" prepend-icon="report" @click="openCreate">异常上报</v-btn>
        </zw-empty>
        <template v-else>
          <v-table class="i03-table" hover>
            <thead>
              <tr>
                <th style="width: 132px">上报时间</th>
                <th style="width: 132px">栏舍 / 批次</th>
                <th v-if="!isStaff" style="width: 118px">发病</th>
                <th v-if="!isStaff">症状</th>
                <th v-if="!isStaff" style="width: 88px">上报人</th>
                <th style="width: 250px">AI 诊断（可能性最高）</th>
                <th style="width: 96px">状态</th>
                <th style="width: 150px" class="text-end">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in store.rows" :key="row.report_id" class="i03-row" @click="openDiagnosis(row)">
                <td class="num">{{ monthDay(row.reported_at) }} {{ timeOnly(row.reported_at) }}</td>
                <td>
                  <div class="zw-cell-main">{{ row.pen_name }}</div>
                  <div class="zw-cell-sub num">{{ row.batch_no }}</div>
                </td>
                <td v-if="!isStaff">
                  <div class="num">{{ formatNumber(row.sick_count) }} 头</div>
                  <div class="zw-cell-sub num">发病率 {{ rateText(row.sick_rate, row.sick_count, row.stock_snapshot) }}</div>
                </td>
                <td v-if="!isStaff" class="i03-symptoms">{{ row.symptom_names.join('、') || '—' }}</td>
                <td v-if="!isStaff">{{ row.reporter_name || '—' }}</td>
                <td>
                  <div class="i03-ai">
                    <span>{{ aiText(row) }}</span>
                    <zw-tag v-if="urgencyTag(row)" size="small" :type="urgencyTag(row)?.type" :icon="urgencyTag(row)?.icon">
                      {{ urgencyTag(row)?.text }}
                    </zw-tag>
                  </div>
                </td>
                <td>
                  <zw-tag :type="statusTag(row).type" :icon="statusTag(row).icon" size="small">{{ statusTag(row).text }}</zw-tag>
                </td>
                <td class="text-end">
                  <span class="zw-cell-actions">
                    <a @click.stop="openDiagnosis(row)">查看诊断</a>
                    <a v-if="row.can_handle" @click.stop="openDiagnosis(row, true)">
                      {{ row.status === 'HANDLED' ? '修改处理' : '登记处理' }}
                    </a>
                    <a v-if="row.can_rediagnose" @click.stop="rediagnose(row)">
                      {{ store.rediagnosingId === row.report_id ? '诊断中…' : '重新诊断' }}
                    </a>
                  </span>
                </td>
              </tr>
            </tbody>
          </v-table>
          <div class="zw-table-foot i03-table-foot">
            <span class="zw-faint body-s">{{ isStaff ? '处理结果由所有者或场长登记' : '紧急程度：一般 / 关注 / 紧急；命中重大动物疫病一律为紧急' }}</span>
            <zw-pager
              :total="store.total"
              :page="store.page"
              :size="store.size"
              @update:page="store.setPage"
              @update:size="store.setSize"
            />
          </div>
        </template>
      </zw-card>
    </template>

    <AbnormalReportDrawer
      v-model="store.drawerOpen"
      :inspection-id="store.drawerInspectionId"
      :pen-id="store.drawerPenId"
      @created="onCreated"
    />
  </zw-shell>
</template>

<style scoped>
.i03-loading { display: flex; flex-direction: column; }
.i03-toolbar { flex-wrap: wrap; row-gap: 10px; }
.i03-toolbar .zw-toolbar-l { gap: 10px; }
.i03-toolbar .zw-toolbar-r { flex: 0 0 auto; margin-left: auto; }
.i03-toolbar :deep(.zw-seg--small .zw-seg-item) { padding: 0 9px; gap: 4px; flex: 0 0 auto; white-space: nowrap; }
.i03-search { width: 240px; flex: 0 0 240px; }
.i03-select { width: 150px; flex: 0 0 150px; }
.i03-date { width: 180px; flex: 0 0 180px; }
.i03-table :deep(td) { white-space: nowrap; }
.i03-row { cursor: pointer; }
.i03-symptoms { white-space: normal !important; max-width: 260px; }
.i03-ai { display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.i03-table-foot { padding: 12px 16px !important; border-top: 1px solid var(--line-1); }
.i03-table-loading { padding: 8px 16px; }
.i03-range-pop { padding: 16px; width: 360px; }
.i03-range-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.i03-range-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
</style>
