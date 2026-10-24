<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dayjs, formatDate, formatNumber, today as todayIso } from '@zmt/shared'
import { useInspectionHistoryStore, type HistoryFilter } from '@/stores/inspect/history'
import { useFarmReload } from '@/composables/useFarmReload'
import { useFarmStore } from '@/stores/farm'
import { monthDay, weekdayText } from '@/views/inspect/inspect'
import InspectionFormDrawer from './components/InspectionFormDrawer.vue'
import InspectionDetailDrawer from './components/InspectionDetailDrawer.vue'
import type { InspectionListItem, PenInspectionRow } from '@/api/inspect'

const store = useInspectionHistoryStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()

const rangeMenu = ref(false)
const rangeStart = ref(store.start)
const rangeEnd = ref(store.end)
const formOpen = ref(false)
const formMode = ref<'REGISTER' | 'MODIFY' | 'BACKFILL'>('MODIFY')
const formPenId = ref(0)
const formInspectionId = ref(0)
const formDate = ref('')

const isStaff = computed(() => farm.role === 'staff')
const desc = '按栏舍查看巡检记录；今天和前 3 天的记录可以直接修改，更早的记录需要提交修改申请'
const rangeText = computed(() => `${monthDay(store.start)} – ${monthDay(store.end)}`)
const penSub = computed(() => `有在栏批次 ${formatNumber(store.penCount)} 个`)
const overviewTitle = computed(() => store.overview?.pen_name || store.selectedPen?.pen_name || '选择栏舍')
const overviewSub = computed(() => {
  const info = store.overview
  if (!info) return ''
  const parts = [...(info.batch_nos || [])]
  if (info.responsible_names?.length) parts.push(`负责人 ${info.responsible_names.join('、')}`)
  parts.push(`近 7 天已巡 ${formatNumber(info.recent7_done_days || 0)} / ${formatNumber(info.recent7_should_days || 0)} 天`)
  return parts.join(' · ')
})
const filterItems = computed(() => store.filterItems)
const todayLabel = computed(() => formatDate(todayIso()))

onMounted(async () => {
  if (isStaff.value) store.filter = 'ALL'
  await boot()
})
useFarmReload(async () => {
  store.reset()
  await boot()
})

async function boot(): Promise<void> {
  await store.load()
  const queryPenId = Number(route.query.penId || 0)
  const locateDate = String(route.query.date || '')
  if (queryPenId && store.pens.some((pen) => pen.pen_id === queryPenId)) {
    if (locateDate) {
      const start = dayjs(locateDate).subtract(3, 'day').format('YYYY-MM-DD')
      store.start = start
      store.end = todayIso()
      rangeStart.value = start
      rangeEnd.value = todayIso()
    }
    store.selectPen(queryPenId)
    if (locateDate) store.locate(locateDate)
  }
}

function selectPen(pen: InspectionListItem): void {
  store.selectPen(pen.pen_id)
}

function applyRange(): void {
  if (!rangeStart.value || !rangeEnd.value) return
  if (rangeStart.value > rangeEnd.value) return
  rangeMenu.value = false
  store.setRange(rangeStart.value, rangeEnd.value)
}

function openRange(): void {
  rangeStart.value = store.start
  rangeEnd.value = store.end
  rangeMenu.value = true
}

function setFilter(value: string | number): void {
  store.setFilter(value as HistoryFilter)
}

function tagList(row: PenInspectionRow): Array<{ text: string; type: string; icon?: string }> {
  const tags: Array<{ text: string; type: string; icon?: string }> = []
  if (row.tags?.includes('ABNORMAL')) tags.push({ text: '异常', type: 'error', icon: row.abnormal_source === 'MANUAL' ? 'flag' : 'error' })
  if (row.tags?.includes('OVERDUE')) tags.push({ text: '超时', type: 'neutral', icon: 'schedule' })
  if (row.tags?.includes('BACKFILL')) tags.push({ text: '补录', type: 'neutral', icon: 'history' })
  if (row.tags?.includes('OFF_SITE')) tags.push({ text: '非现场', type: 'warning', icon: 'wrong_location' })
  else if (row.tags?.includes('ON_SITE')) tags.push({ text: '现场', type: 'neutral' })
  if (row.backfill_expired) tags.push({ text: '已超过补录期限', type: 'neutral' })
  if (row.pending_request_id) tags.push({ text: '待审核', type: 'warning' })
  return tags
}

function rowStatusText(row: PenInspectionRow): string {
  if (row.inspect_status === 'MISSED') return row.backfill_expired ? '漏巡 · 已超过补录期限' : '漏巡'
  if (row.abnormal_summary) return row.abnormal_summary
  return row.result_summary || '正常'
}

function dateText(row: PenInspectionRow): string {
  return `${monthDay(row.inspect_date)} ${weekdayText(row.inspect_date)}${row.is_today ? ' · 今天' : ''}`
}

function openCreate(row: PenInspectionRow): void {
  if (!row.can_modify || !row.inspection_id) return
  store.detailOpen = false
  formMode.value = 'MODIFY'
  formPenId.value = store.selectedPenId
  formInspectionId.value = row.inspection_id
  formDate.value = row.inspect_date
  formOpen.value = true
}

function modifyFromDetail(): void {
  const detail = store.detail
  if (!detail) return
  store.detailOpen = false
  formMode.value = 'MODIFY'
  formPenId.value = detail.pen_id
  formInspectionId.value = detail.inspection_id
  formDate.value = detail.inspect_date || ''
  formOpen.value = true
}

function changeRequestFromDetail(): void {
  const detail = store.detail
  if (!detail) return
  openChangeRequest({ inspection_id: detail.inspection_id, inspect_date: detail.inspect_date || '', can_change_request: true } as PenInspectionRow)
}

function openBackfill(row: PenInspectionRow): void {
  if (!row.can_backfill) return
  formMode.value = 'BACKFILL'
  formPenId.value = store.selectedPenId
  formInspectionId.value = 0
  formDate.value = row.inspect_date
  formOpen.value = true
}

function openChangeRequest(row: PenInspectionRow): void {
  if (!row.can_change_request) return
  void router.push({
    name: 'WU-U01',
    query: {
      biz_type: 'INSPECTION',
      biz_id: String(row.inspection_id),
      action: 'create',
      ...(row.pending_request_id ? { request_id: String(row.pending_request_id) } : {})
    }
  })
}

function onFormSubmitted(): void {
  formOpen.value = false
  void store.refresh()
}

function openDetailDrawer(row: PenInspectionRow): void {
  if (!row.inspection_id) return
  void store.openDetail(row.inspection_id)
}

function goToday(): void {
  void router.push({ name: 'WU-I01' })
}

function onHeadClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.zw-back')) goToday()
}

function filteredEmptyText(): string {
  return store.emptyFilterText
}
</script>

<template>
  <zw-shell side="user" active="WU-I02" :crumbs="['防疫健康', '日常巡检', '巡检历史']">
    <zw-page-head title="巡检历史" :desc="desc" back @click="onHeadClick">
      <template #actions>
        <v-menu v-model="rangeMenu" :close-on-content-click="false">
          <template #activator="{ props: menuProps }">
            <v-text-field
              v-bind="menuProps"
              :model-value="rangeText"
              readonly
              append-inner-icon="calendar_month"
              class="zw-w-200"
              hide-details
              @click="openRange"
            />
          </template>
          <div class="i02-range-pop">
            <div class="i02-range-fields">
              <v-text-field v-model="rangeStart" type="date" label="开始日期" hide-details />
              <v-text-field v-model="rangeEnd" type="date" label="结束日期" :max="todayIso()" hide-details />
            </div>
            <div class="i02-range-actions">
              <v-btn variant="text" @click="rangeMenu = false">取消</v-btn>
              <v-btn color="primary" @click="applyRange">确定</v-btn>
            </div>
          </div>
        </v-menu>
        <v-btn variant="outlined" prepend-icon="fact_check" @click="goToday">今日巡检</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading'" class="i02-loading">
      <zw-card><v-skeleton-loader type="list-item-three-line@4" /></zw-card>
      <div class="i02-main">
        <zw-card><v-skeleton-loader type="list-item@4" /></zw-card>
        <zw-card><v-skeleton-loader type="date-picker" /><v-skeleton-loader type="table-row@5" /></zw-card>
      </div>
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '当前角色无权查看巡检历史。'">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="巡检历史加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.refresh()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'empty'">
      <zw-empty icon="fence" title="还没有在栏栏舍" desc="批次入栏后，所在栏舍会自动列入巡检清单。">
        <v-btn color="primary" @click="router.push({ name: 'WU-L05', query: { action: 'create' } })">批次入栏</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div class="i02-main">
        <zw-card title="栏舍" :sub="penSub" flush class="i02-pens">
          <div class="i02-pen-list">
            <button
              v-for="pen in store.pens"
              :key="pen.pen_id"
              type="button"
              class="i02-pen"
              :class="{ on: pen.pen_id === store.selectedPenId }"
              @click="selectPen(pen)"
            >
              <div class="i02-pen-main">
                <div class="i02-pen-name">{{ pen.pen_name }}</div>
                <div class="i02-pen-sub num">
                  {{ pen.pen_code }} · {{ pen.responsible_names?.join('、') || '无负责员工' }}
                </div>
              </div>
              <zw-tag
                :type="pen.display_status === 'ABNORMAL' ? 'error' : pen.display_status === 'DONE' ? 'neutral' : 'primary'"
                :icon="pen.display_status === 'ABNORMAL' ? 'error' : pen.display_status === 'DONE' ? 'check' : ''"
                size="small"
              >
                {{ pen.display_status_text }}
              </zw-tag>
            </button>
          </div>
        </zw-card>

        <div class="i02-right">
          <zw-card :title="overviewTitle" :sub="overviewSub">
            <template #extra>
              <span class="i02-today-note">今天按北京时间 {{ todayLabel }} 计算</span>
            </template>
            <div v-if="store.overviewLoading" class="i02-days">
              <v-skeleton-loader v-for="item in 7" :key="item" type="article" />
            </div>
            <div v-else class="i02-days">
              <button
                v-for="day in store.overview?.days || []"
                :key="day.date"
                type="button"
                class="i02-day"
                :class="{ today: day.date === todayIso() }"
                @click="store.locate(day.date)"
              >
                <span class="i02-day-date">{{ monthDay(day.date) }}</span>
                <span class="i02-day-week">{{ weekdayText(day.date) }}</span>
                <span class="i02-day-tag">
                  <zw-tag
                    :type="day.day_tag === 'ABNORMAL' ? 'error' : day.day_tag === 'MISSED' ? 'warning' : day.day_tag === 'PENDING' ? 'primary' : 'neutral'"
                    :icon="day.day_tag === 'ABNORMAL' ? 'flag' : day.day_tag === 'BACKFILL' ? 'history' : day.day_tag === 'OVERDUE' ? 'schedule' : day.day_tag === 'DONE' ? 'check' : ''"
                    size="small"
                  >
                    {{ day.day_tag === 'ABNORMAL' ? '异常' : day.day_tag === 'MISSED' ? '漏巡' : day.day_tag === 'PENDING' ? '未巡' : day.day_tag === 'BACKFILL' ? '补录' : day.day_tag === 'OVERDUE' ? '超时' : day.day_tag === 'DONE' ? '已巡' : '—' }}
                  </zw-tag>
                </span>
              </button>
            </div>
          </zw-card>

          <zw-card title="巡检记录" :sub="`${store.total} 条`" flush class="mt-4">
            <template #extra>
              <zw-seg :model-value="store.filter" :items="filterItems" size="small" @update:model-value="setFilter" />
            </template>
            <zw-notice v-if="!isStaff" type="info" class="i02-rule">
              {{ store.backfillHint || '漏巡只能补录前 3 天内的记录。超过 3 天的记录需要提交修改申请，管理员审核通过后才会修改。' }}
            </zw-notice>
            <div v-if="store.listLoading" class="i02-table-loading"><v-skeleton-loader type="table-row@5" /></div>
            <zw-empty
              v-else-if="!store.rows.length"
              icon="search_off"
              :title="store.filter === 'ALL' ? '这个栏舍还没有巡检记录' : filteredEmptyText()"
              :desc="store.filter === 'ALL' ? '有在栏批次时，每天会自动列入巡检清单。' : '换一个筛选条件试试。'"
            />
            <template v-else>
              <v-table class="i02-table" hover>
                <thead>
                  <tr>
                    <th style="width: 150px">日期</th>
                    <th style="width: 168px">标注</th>
                    <th>巡检结果</th>
                    <th style="width: 168px">登记</th>
                    <th style="width: 190px" class="text-end">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in store.rows" :key="`${row.inspection_id}-${row.inspect_date}`">
                    <td>
                      <div class="i02-date">{{ dateText(row) }}</div>
                    </td>
                    <td>
                      <div class="i02-tags">
                        <zw-tag
                          v-for="tag in tagList(row)"
                          :key="tag.text"
                          :type="tag.type"
                          :icon="tag.icon"
                          size="small"
                        >
                          {{ tag.text }}
                        </zw-tag>
                        <span v-if="!tagList(row).length" class="zw-faint">—</span>
                      </div>
                    </td>
                    <td>
                      <div :class="{ 'i02-abn': row.inspect_status !== 'MISSED' && row.abnormal_summary }">{{ rowStatusText(row) }}</div>
                      <div v-if="row.manual_reason" class="i02-manual">
                        <zw-icon name="flag" :size="14" fill />手动标记 · {{ row.manual_reason }}
                      </div>
                      <div v-else-if="row.inspect_status === 'MISSED' && !row.backfill_expired" class="zw-cell-sub">可以补录</div>
                    </td>
                    <td>
                      <template v-if="row.registrant_name">
                        <div class="num">{{ row.registrant_name }} {{ row.registered_at?.slice(11, 16) }}</div>
                        <div v-if="row.tags?.includes('BACKFILL')" class="zw-cell-sub">
                          {{ row.registered_at ? `${monthDay(row.registered_at)}补录` : '补录' }}
                        </div>
                      </template>
                      <span v-else class="zw-faint">—</span>
                    </td>
                    <td class="text-end">
                      <span class="zw-cell-actions">
                        <a v-if="row.inspection_id" @click="openDetailDrawer(row)">查看详情</a>
                        <a v-if="row.can_modify" @click="openCreate(row)">修改巡检</a>
                        <a v-if="row.can_backfill" @click="openBackfill(row)">补录</a>
                        <v-menu v-if="row.can_change_request || row.has_revisions || row.pending_request_id" location="bottom end">
                          <template #activator="{ props: menuProps }">
                            <button v-bind="menuProps" class="i02-more" type="button" aria-label="更多">
                              <zw-icon name="more_horiz" :size="18" />
                            </button>
                          </template>
                          <v-list density="compact" class="i02-menu">
                            <v-list-item v-if="row.can_change_request" @click="openChangeRequest(row)">
                              <v-list-item-title>提交修改申请</v-list-item-title>
                              <template #append><span class="i02-menu-note">需审核</span></template>
                            </v-list-item>
                            <v-list-item v-if="row.pending_request_id" @click="router.push({ name: 'WU-U01', query: { status: 'PENDING' } })">
                              <v-list-item-title>查看修改申请</v-list-item-title>
                            </v-list-item>
                            <v-list-item v-if="row.has_revisions" @click="openDetailDrawer(row)">
                              <v-list-item-title>查看修改记录</v-list-item-title>
                            </v-list-item>
                          </v-list>
                        </v-menu>
                        <span v-if="!row.inspection_id" class="zw-faint">—</span>
                      </span>
                    </td>
                  </tr>
                </tbody>
              </v-table>
              <div class="zw-table-foot i02-table-foot">
                <span class="zw-faint body-s">按日期倒序；漏巡日期单独成行，3 天内可补录</span>
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
        </div>
      </div>
    </template>

    <InspectionDetailDrawer
      v-model="store.detailOpen"
      :detail="store.detail"
      :loading="store.detailLoading"
      :error="store.detailError"
      :logs="store.logs"
      :logs-loading="store.logsLoading"
      @modify="modifyFromDetail"
      @change-request="changeRequestFromDetail"
    />

    <InspectionFormDrawer
      v-model="formOpen"
      :mode="formMode"
      :pen-id="formPenId"
      :inspection-id="formInspectionId"
      :inspect-date="formDate"
      :pen-options="store.pens.map((pen) => ({ pen_id: pen.pen_id, pen_name: pen.pen_name || '', statusText: pen.display_status_text, stock: pen.stock_count }))"
      :staff="isStaff"
      @submitted="onFormSubmitted"
    />
  </zw-shell>
</template>

<style scoped>
.i02-loading { display: flex; flex-direction: column; gap: 16px; }
.i02-main { display: flex; align-items: flex-start; gap: 16px; }
.i02-pens { width: 248px; flex: 0 0 248px; }
.i02-pen-list { padding: 4px 8px 8px; }
.i02-pen { width: 100%; display: flex; align-items: center; gap: 8px; padding: 10px 8px; border: 0; border-left: 3px solid transparent; border-radius: var(--radius-sm); background: transparent; text-align: left; font: inherit; cursor: pointer; }
.i02-pen:hover { background: var(--fill-1); }
.i02-pen.on { background: var(--primary-subtle); border-left-color: var(--primary); }
.i02-pen-main { flex: 1; min-width: 0; }
.i02-pen-name { font-size: 14px; font-weight: 500; color: var(--text-1); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.i02-pen.on .i02-pen-name { color: var(--primary); }
.i02-pen-sub { font-size: 12px; color: var(--text-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.i02-right { flex: 1; min-width: 0; }
.i02-today-note { font-size: 12px; color: var(--text-3); }
.i02-days { display: flex; gap: 8px; }
.i02-day { flex: 1; min-width: 0; height: 86px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--bg-card); font: inherit; cursor: pointer; }
.i02-day:hover { border-color: var(--primary); }
.i02-day.today { border-color: var(--primary); }
.i02-day-date { font-size: 14px; font-weight: 500; color: var(--text-1); }
.i02-day-week { font-size: 12px; color: var(--text-3); }
.i02-day-tag { margin-top: 2px; }
.i02-rule { margin: 12px 16px 12px; }
.i02-table :deep(td) { white-space: nowrap; }
.i02-date { font-size: 14px; color: var(--text-1); }
.i02-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.i02-abn { color: var(--error); font-weight: 500; }
.i02-manual { display: flex; align-items: center; gap: 3px; margin-top: 2px; font-size: 12px; color: var(--text-2); }
.i02-manual .zw-mso { color: var(--error); }
.i02-more { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--text-2); cursor: pointer; vertical-align: middle; }
.i02-more:hover { background: var(--fill-2); }
.i02-menu { min-width: 200px; }
.i02-menu-note { font-size: 12px; color: var(--text-3); }
.i02-table-foot { padding: 12px 16px !important; border-top: 1px solid var(--line-1); }
.i02-table-loading { padding: 8px 16px; }
.i02-range-pop { padding: 16px; width: 360px; }
.i02-range-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.i02-range-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
</style>
