<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dayjs, formatDate, formatNumber, formatTime, today as todayIso } from '@zmt/shared'
import { useInspectionTodayStore, type ListStatus } from '@/stores/inspect/today'
import { useFarmReload } from '@/composables/useFarmReload'
import { queryId, useRouteAction } from '@/composables/useRouteAction'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { dateWithWeek, locationLabel, monthDay } from '@/views/inspect/inspect'
import InspectionFormDrawer from './components/InspectionFormDrawer.vue'
import AbnormalReportDrawer from './components/AbnormalReportDrawer.vue'
import type { InspectionListItem, RecentIssue } from '@/api/inspect'

const store = useInspectionTodayStore()
const farm = useFarmStore()
const ui = useUiStore()
const router = useRouter()

const dateMenu = ref(false)
const deadlineValue = ref('')
const deadlineSaving = ref(false)

const isStaff = computed(() => farm.role === 'staff')
const today = todayIso()
const dateText = computed(() => dateWithWeek(store.query.date))
const scopeText = computed(() => (isStaff.value ? '你负责的在栏批次栏舍' : '有在栏批次的栏舍'))
const desc = computed(() => {
  const parts = [store.farmName, dateWithWeek(store.query.date)]
  parts.push(isStaff.value ? `你负责 ${formatNumber(store.summary?.responsible_pen_count || store.list.length)} 个栏舍` : '有在栏批次的栏舍')
  return parts.join(' · ')
})
const reminderTime = computed(() => {
  if (!store.summary?.deadline_time) return ''
  return dayjs(`2000-01-01 ${store.summary.deadline_time}`).subtract(1, 'hour').format('HH:mm')
})
const pendingFoot = computed(() => {
  if (isStaff.value) {
    const first = store.list.find((row) => row.display_status === 'PENDING')
    if (first) return `${first.pen_name} · ${store.summary?.deadline_time || ''} 前完成`
    return '今天没有未巡栏舍'
  }
  if (store.noOwnerPending) return `其中 ${formatNumber(store.noOwnerPending)} 个无负责员工`
  return '按截止时间提醒负责员工'
})
const abnormalFoot = computed(() => {
  if (!store.summary?.abnormal_count) return '今天没有异常栏舍'
  const name = store.summary.first_abnormal_pen_name || '异常栏舍'
  return `${name} · ${store.summary.first_abnormal_reported ? '已上报' : '未上报'}`
})
const segItems = computed(() => [
  { label: '全部', value: 'ALL', count: store.tabCounts.ALL || 0 },
  { label: '未巡', value: 'PENDING', count: store.tabCounts.PENDING || 0 },
  { label: '异常', value: 'ABNORMAL', count: store.tabCounts.ABNORMAL || 0 },
  { label: '已巡', value: 'DONE', count: store.tabCounts.DONE || 0 }
])
const todaySub = computed(() =>
  isStaff.value
    ? `${formatDate(store.query.date)} · ${formatNumber(store.list.length)} 个负责栏舍`
    : '按异常、未巡、已巡排列'
)
const yesterdayText = computed(() => (store.yesterdayDate ? monthDay(store.yesterdayDate) : ''))
const yesterdayRows = computed(() => store.yesterdayList.slice(0, 6))

function reload(): void {
  store.reset()
  void store.load()
}

const routeAction = useRouteAction((action, query) => {
  if (action !== 'create') return
  openRegister(queryId(query, 'penId', 'pen_id'))
})

onMounted(async () => {
  if (isStaff.value) store.query.status = 'ALL'
  if (!isStaff.value) void store.loadSetting()
  await store.load()
  await routeAction.start()
})
useFarmReload(reload)

function setSeg(value: string | number): void {
  store.setStatus(value as ListStatus)
}

function onDate(value: unknown): void {
  const text = value instanceof Date ? dayjs(value).format('YYYY-MM-DD') : String(value || '')
  if (!text) return
  dateMenu.value = false
  if (text > today) return
  store.setDate(text)
}

function openDeadline(): void {
  deadlineValue.value = store.summary?.deadline_time || store.setting?.deadline_time || '18:00'
  store.openDeadline()
}

async function saveDeadline(): Promise<void> {
  deadlineSaving.value = true
  try {
    await store.saveDeadline(deadlineValue.value)
  } finally {
    deadlineSaving.value = false
  }
}

function deadlineRemind(value: string): string {
  if (!value) return '—'
  return dayjs(`2000-01-01 ${value}`).subtract(1, 'hour').format('HH:mm')
}

function setDeadline(value: string | number): void {
  deadlineValue.value = String(value)
}

function openRegister(penId = 0): void {
  if (isStaff.value && !store.list.length) {
    ui.toast('你还没有负责的栏舍，暂时不能登记巡检', 'info')
    return
  }
  store.openRegister(penId)
}

function openModify(row: InspectionListItem): void {
  store.openModify(row)
}

function openReport(row: InspectionListItem): void {
  if (!row.inspection_id || !row.can_report) return
  store.openReport(row.inspection_id, row.pen_id)
}

function openDiagnosis(row: InspectionListItem): void {
  if (!row.report_id) return
  void router.push({ name: 'WU-I04', params: { id: String(row.report_id) } })
}

function issueTitle(issue: RecentIssue): string {
  return `${issue.pen_name || ''} · ${monthDay(issue.inspect_date)}`
}

function issueDesc(issue: RecentIssue): string {
  if (issue.issue_type === 'MISSED') return `漏巡 · 可补录至 ${monthDay(issue.backfill_until)}`
  if (issue.issue_type === 'BACKFILL') return `${monthDay(issue.inspect_date)} ${issue.registrant_name || ''}补录`
  if (issue.issue_type === 'OVERDUE') return `${issue.registrant_name || ''} ${formatTime(issue.registered_at)} 登记，晚于截止时间`
  return issue.issue_type_text || ''
}

function issueTag(issue: RecentIssue): { text: string; icon: string } {
  if (issue.issue_type === 'MISSED') return { text: '漏巡', icon: 'report' }
  if (issue.issue_type === 'BACKFILL') return { text: '补录', icon: 'history' }
  return { text: '超时', icon: 'schedule' }
}

function statusTag(row: InspectionListItem): { type: string; icon: string } {
  if (row.display_status === 'ABNORMAL') {
    return { type: 'error', icon: row.abnormal_source === 'MANUAL' ? 'flag' : 'error' }
  }
  if (row.display_status === 'DONE') return { type: 'neutral', icon: 'check' }
  if (row.display_status === 'MISSED') return { type: 'neutral', icon: 'report' }
  return { type: 'primary', icon: '' }
}

function onSubmitted(): void {
  store.onSubmitted()
}

function onReportCreated(reportId: number): void {
  store.reportOpen = false
  void router.push({ name: 'WU-I04', params: { id: String(reportId) } })
}

function onReportFromForm(inspectionId: number, penId: number): void {
  store.openReport(inspectionId, penId)
}

function goHistory(): void {
  void router.push({ name: 'WU-I02' })
}
</script>

<template>
  <zw-shell side="user" active="WU-I01" :crumbs="['防疫健康', '日常巡检', '今日巡检']">
    <zw-page-head title="今日巡检" :desc="desc">
      <template #actions>
        <v-menu v-if="!isStaff" v-model="dateMenu" :close-on-content-click="false">
          <template #activator="{ props: menuProps }">
            <v-text-field
              v-bind="menuProps"
              :model-value="dateText"
              readonly
              append-inner-icon="calendar_month"
              class="zw-w-160"
              hide-details
            />
          </template>
          <v-date-picker :model-value="store.query.date" :max="today" @update:model-value="onDate" />
        </v-menu>
        <v-btn variant="outlined" prepend-icon="history" @click="goHistory">巡检历史</v-btn>
        <v-btn color="primary" prepend-icon="fact_check" @click="openRegister()">登记巡检</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading'" class="i01-loading">
      <div class="zw-grid-5 mb-4">
        <v-skeleton-loader v-for="item in 5" :key="item" type="article" />
      </div>
      <zw-card>
        <v-skeleton-loader type="table-row@5" />
      </zw-card>
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '当前角色无权查看巡检。'">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="巡检加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div class="zw-grid-5 mb-4">
        <zw-stat
          :label="isStaff ? '应巡' : '全场应巡'"
          icon="fact_check"
          :value="formatNumber(store.summary?.should_count || 0)"
          unit="个"
          :foot="scopeText"
        />
        <zw-stat
          label="已巡"
          icon="task_alt"
          :value="formatNumber(store.summary?.done_count || 0)"
          unit="个"
        >
          <template #foot>
            <div class="i01-progress">
              <v-progress-linear :model-value="store.completionPercent" height="4" rounded />
              <span>完成 {{ formatNumber(store.completionPercent, 1) }}%</span>
            </div>
          </template>
        </zw-stat>
        <zw-stat
          label="未巡"
          icon="pending_actions"
          :value="formatNumber(store.summary?.pending_count || 0)"
          unit="个"
          :foot="pendingFoot"
        />
        <zw-stat
          label="异常"
          icon="report"
          :value="formatNumber(store.summary?.abnormal_count || 0)"
          unit="个"
          tone="error"
          :foot="abnormalFoot"
        />
        <zw-stat label="截止时间" icon="schedule" :value="store.summary?.deadline_time || '—'">
          <template #foot>
            <span>{{ store.summary?.remind_time || reminderTime }} {{ isStaff ? '会提醒你' : '提醒负责员工' }}</span>
            <a v-if="store.summary?.can_edit_deadline" class="i01-edit" @click="openDeadline">修改</a>
          </template>
        </zw-stat>
      </div>

      <zw-notice v-if="store.summary?.deadline_passed && store.summary?.pending_count" type="warning" class="mb-4">
        已过今天的截止时间 {{ store.summary?.deadline_time }}，未巡栏舍登记后标为“超时”。
      </zw-notice>
      <zw-notice v-else-if="store.summary?.all_done" type="success" class="mb-4">
        今天的巡检已全部完成。
      </zw-notice>

      <div class="i01-main">
        <div class="i01-main-left">
          <zw-card :title="isStaff ? '今天' : '今日栏舍清单'" :sub="todaySub" flush>
            <template v-if="!isStaff" #extra>
              <zw-seg :model-value="store.query.status" :items="segItems" size="small" @update:model-value="setSeg" />
            </template>
            <div v-if="store.status === 'empty'" class="i01-empty">
              <zw-empty :icon="isStaff ? 'fence' : 'fact_check'" :title="store.emptyTitle" :desc="store.emptyDesc">
                <v-btn v-if="!isStaff" color="primary" @click="router.push({ name: 'WU-L05', query: { action: 'create' } })">批次入栏</v-btn>
              </zw-empty>
            </div>
            <div v-else-if="store.status === 'filtered_empty'" class="i01-empty">
              <zw-empty icon="search_off" :title="store.emptyTitle" :desc="store.emptyDesc" />
            </div>
            <template v-else>
              <v-table class="i01-table" hover>
                <thead>
                  <tr>
                    <th style="width: 32%">栏舍</th>
                    <th style="width: 110px">负责人</th>
                    <th>巡检结果</th>
                    <th style="width: 150px">登记</th>
                    <th style="width: 88px">状态</th>
                    <th style="width: 132px" class="text-end">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in store.list" :key="`${row.pen_id}-${row.inspection_id}`">
                    <td>
                      <div class="i01-pen">
                        <span class="zw-ico-tile" :class="row.display_status === 'ABNORMAL' ? 'zw-ico-tile--error' : row.display_status === 'DONE' ? '' : 'zw-ico-tile--primary'">
                          <zw-icon name="fact_check" :size="18" />
                        </span>
                        <div>
                          <div class="zw-cell-main">{{ row.pen_name }}</div>
                          <div class="zw-cell-sub num">{{ row.pen_code }} · 存栏 {{ formatNumber(row.stock_count || 0) }} 头</div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span v-if="row.responsible_names?.length">{{ row.responsible_names.join('、') }}</span>
                      <span v-else class="zw-faint">无负责员工</span>
                    </td>
                    <td>
                      <template v-if="row.display_status === 'ABNORMAL'">
                        <div class="i01-abn">{{ row.abnormal_summary || '异常' }}</div>
                        <div class="zw-cell-sub">{{ row.report_id ? '已上报' : '未上报' }}</div>
                      </template>
                      <template v-else-if="row.display_status === 'DONE'">
                        <div>各项正常</div>
                        <div class="zw-cell-sub">{{ row.is_backfill ? '补录' : row.is_overdue ? '超时登记' : '按时登记' }}</div>
                      </template>
                      <template v-else-if="row.display_status === 'MISSED'">
                        <div class="zw-faint">漏巡</div>
                      </template>
                      <template v-else>
                        <div>{{ row.deadline_passed ? '已过截止时间' : `${store.summary?.deadline_time || ''} 前完成` }}</div>
                        <div class="zw-cell-sub">{{ row.responsible_names?.length ? '等待登记' : '提醒所有者和场长' }}</div>
                      </template>
                    </td>
                    <td>
                      <template v-if="row.registrant_name">
                        <div>{{ row.registrant_name }} <span class="num">{{ row.registered_at?.slice(11, 16) }}</span></div>
                        <div class="zw-cell-sub">
                          <zw-tag v-if="row.location_status" size="small" :type="row.location_status === 'OFF_SITE' ? 'warning' : 'neutral'">
                            {{ locationLabel(row.location_status, row.distance_m) }}
                          </zw-tag>
                        </div>
                      </template>
                      <span v-else class="zw-faint">—</span>
                    </td>
                    <td>
                      <zw-tag :type="statusTag(row).type" :icon="statusTag(row).icon" size="small">
                        {{ row.display_status_text || row.display_status }}
                      </zw-tag>
                    </td>
                    <td class="text-end">
                      <span class="zw-cell-actions">
                        <a v-if="row.can_view_diagnosis" @click="openDiagnosis(row)">查看诊断</a>
                        <a v-if="row.can_report" @click="openReport(row)">转上报</a>
                        <a v-if="row.can_register" @click="openRegister(row.pen_id)">登记巡检</a>
                        <a v-if="row.can_modify" @click="openModify(row)">修改</a>
                      </span>
                    </td>
                  </tr>
                </tbody>
              </v-table>
              <div class="zw-table-foot i01-table-foot">
                <span class="zw-faint body-s">没有在栏批次的栏舍不列入清单；同一栏舍每天只有一条记录</span>
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

          <zw-card v-if="isStaff" :title="`昨天 · ${yesterdayText}`" sub="更早的记录在巡检历史中查看" flush class="mt-4">
            <template #extra>
              <a class="i01-link" @click="goHistory">巡检历史</a>
            </template>
            <div v-if="store.yesterdayLoading" class="i01-empty"><v-skeleton-loader type="table-row@2" /></div>
            <zw-empty v-else-if="!yesterdayRows.length" icon="history" title="昨天没有巡检记录" desc="巡检记录会按天保存在巡检历史中。" />
            <div v-else class="i01-yesterday">
              <div v-for="row in yesterdayRows" :key="`${row.pen_id}-${row.inspection_id}`" class="i01-yesterday-row">
                <div class="i01-pen">
                  <span class="zw-ico-tile"><zw-icon name="fact_check" :size="18" /></span>
                  <div>
                    <div class="zw-cell-main">{{ row.pen_name }}</div>
                    <div class="zw-cell-sub num">{{ row.pen_code }}</div>
                  </div>
                </div>
                <div class="i01-yesterday-mid">
                  {{ row.registrant_name || '—' }} {{ row.registered_at ? `${row.registered_at.slice(11, 16)} 登记` : '' }}
                  <span v-if="row.location_status" class="zw-faint"> · {{ locationLabel(row.location_status, row.distance_m) }}</span>
                </div>
                <zw-tag :type="row.is_overdue ? 'neutral' : 'neutral'" :icon="row.is_overdue ? 'schedule' : 'check'" size="small">
                  {{ row.is_overdue ? '超时' : row.display_status_text }}
                </zw-tag>              </div>
            </div>
          </zw-card>

          <zw-card v-if="!isStaff" title="提醒规则" sub="随截止时间变化" class="mt-4">
            <div class="i01-rules">
              <div class="i01-rule">
                <zw-icon name="notifications_active" :size="20" />
                <div>
                  <div class="i01-rule-title">{{ reminderTime }} 提醒负责员工</div>
                  <div class="i01-rule-sub">截止前 1 小时；栏舍没有负责员工时，改为提醒所有者和场长</div>
                </div>
              </div>
              <div class="i01-rule">
                <zw-icon name="notification_important" :size="20" />
                <div>
                  <div class="i01-rule-title">{{ store.summary?.deadline_time }} 提醒所有者和场长</div>
                  <div class="i01-rule-sub">到点仍有栏舍未巡检时发送给{{ (store.setting?.owner_manager_names || []).join('、') || '所有者和场长' }}</div>
                </div>
              </div>
            </div>
          </zw-card>
        </div>

        <aside class="i01-aside">
          <zw-card v-if="!isStaff" title="前 3 天 · 超时与补录" flush>
            <template #extra>
              <a class="i01-link" @click="goHistory">全部历史</a>
            </template>
            <div v-if="!store.issues.length" class="i01-issue-empty">前 3 天没有超时或补录记录</div>
            <div v-else class="i01-issues">
              <div v-for="issue in store.issues" :key="`${issue.inspection_id}-${issue.issue_type}`" class="i01-issue">
                <span class="zw-ico-tile">
                  <zw-icon :name="issue.issue_type === 'MISSED' ? 'report' : issue.issue_type === 'BACKFILL' ? 'history' : 'schedule'" :size="18" />
                </span>
                <div class="i01-issue-main">
                  <div class="i01-issue-title">{{ issueTitle(issue) }}</div>
                  <div class="i01-issue-sub">{{ issueDesc(issue) }}</div>
                </div>
                <v-btn
                  v-if="issue.issue_type === 'MISSED' && issue.can_backfill"
                  size="small"
                  variant="tonal"
                  color="primary"
                  @click="store.openBackfill(issue.pen_id, issue.inspect_date || '')"
                >
                  补录
                </v-btn>
                <zw-tag v-else size="small" :icon="issueTag(issue).icon">{{ issueTag(issue).text }}</zw-tag>
              </div>
            </div>
            <div class="i01-issue-note">
              <zw-icon name="info" :size="16" />
              <span>只能补录前 3 天内的漏巡，提交后标为“补录”。超过 3 天的记录需要提交修改申请。</span>
            </div>
          </zw-card>

          <template v-else>
            <zw-card title="提醒">
              <div class="i01-staff-rule">
                <zw-icon name="schedule" :size="20" />
                <div>
                  <div class="i01-rule-title">截止 {{ store.summary?.deadline_time }}</div>
                  <div class="i01-rule-sub">{{ store.summary?.remind_time }} 会提醒你；超过截止时间登记的记录标为“超时”</div>
                </div>
              </div>
              <div class="i01-staff-rule">
                <zw-icon name="location_on" :size="20" />
                <div>
                  <div class="i01-rule-title">登记时记录位置</div>
                  <div class="i01-rule-sub">距养殖场 1 千米以内标“现场”，超过标“非现场”，只标注、不拦截登记</div>
                </div>
              </div>
            </zw-card>
            <zw-notice type="info" class="mt-4">
              发现异常后点“转上报”，AI 会给出可能病因和处理建议。处理结果由所有者或场长登记。
            </zw-notice>
          </template>
        </aside>
      </div>
    </template>

    <InspectionFormDrawer
      v-model="store.registerOpen"
      :mode="store.registerMode"
      :pen-id="store.registerPenId"
      :inspection-id="store.registerInspectionId"
      :inspect-date="store.registerDate"
      :pen-options="store.penChoices"
      :staff="isStaff"
      @submitted="onSubmitted"
      @report="onReportFromForm"
    />

    <AbnormalReportDrawer
      v-model="store.reportOpen"
      :inspection-id="store.reportInspectionId"
      :pen-id="store.reportPenId"
      @created="onReportCreated"
    />

    <zw-dialog v-model="store.deadlineOpen" title="巡检截止时间" :width="480">
      <div class="i01-deadline-desc">{{ store.farmName }}每天的巡检截止时间，对全场栏舍生效。</div>
      <zw-form-item label="截止时间" required hint="默认 18:00；超过截止时间登记的记录标为“超时”">
        <div class="i01-deadline-row">
          <v-text-field v-model="deadlineValue" type="time" class="i01-time" :min="store.setting?.min_time" :max="store.setting?.max_time" />
          <zw-seg
            :model-value="deadlineValue"
            :items="(store.setting?.quick_options || ['16:00', '17:00', '18:00', '19:00']).map((time) => ({ label: time, value: time }))"
            @update:model-value="setDeadline"
          />
        </div>
      </zw-form-item>
      <div class="i01-deadline-rules-title">提醒规则</div>
      <div class="i01-deadline-rule">
        <zw-icon name="notifications_active" :size="20" />
        <div>
          <div class="i01-rule-title">{{ deadlineRemind(deadlineValue) }} 提醒负责员工</div>
          <div class="i01-rule-sub">截止前 1 小时；栏舍没有负责员工时，改为提醒所有者和场长</div>
        </div>
      </div>
      <div class="i01-deadline-rule">
        <zw-icon name="notification_important" :size="20" />
        <div>
          <div class="i01-rule-title">{{ deadlineValue }} 提醒所有者和场长</div>
          <div class="i01-rule-sub">到点仍有栏舍未巡检时发送给{{ (store.setting?.owner_manager_names || []).join('、') || '所有者和场长' }}</div>
        </div>
      </div>
      <template #actions>
        <v-btn variant="outlined" @click="store.deadlineOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="deadlineSaving" @click="saveDeadline">保存</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.i01-loading { display: flex; flex-direction: column; }
.i01-main { display: flex; align-items: flex-start; gap: 16px; }
.i01-main-left { flex: 1; min-width: 0; }
.i01-aside { width: 320px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; }
.i01-progress { display: flex; align-items: center; gap: 8px; width: 100%; }
.i01-progress .v-progress-linear { width: 80px; flex: 0 0 80px; }
.i01-edit { color: var(--primary); cursor: pointer; margin-left: auto; }
.i01-table :deep(td) { white-space: nowrap; }
.i01-table :deep(.v-data-table__th) { white-space: nowrap; }
.i01-pen { display: flex; align-items: center; gap: 10px; min-width: 0; }
.i01-abn { color: var(--error); font-weight: 500; }
.i01-empty { padding: 12px 0; }
.i01-table-foot { padding: 12px 16px !important; border-top: 1px solid var(--line-1); }
.i01-yesterday { padding: 4px 16px 8px; }
.i01-yesterday-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px dashed var(--line-1); }
.i01-yesterday-row:last-child { border-bottom: 0; }
.i01-yesterday-mid { flex: 1; min-width: 0; font-size: 13px; color: var(--text-2); }
.i01-link { color: var(--primary); cursor: pointer; }
.i01-rules { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 4px 0; }
.i01-rule { display: flex; align-items: flex-start; gap: 10px; }
.i01-rule .zw-mso { color: var(--text-3); margin-top: 1px; }
.i01-rule-title { font-size: 14px; line-height: 20px; font-weight: 500; color: var(--text-1); }
.i01-rule-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.i01-issues { padding: 0 16px; }
.i01-issue { display: flex; align-items: center; gap: 10px; padding: 12px 0; }
.i01-issue + .i01-issue { border-top: 1px solid var(--line-1); }
.i01-issue-main { flex: 1; min-width: 0; }
.i01-issue-title { font-size: 13px; color: var(--text-1); }
.i01-issue-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.i01-issue-empty { padding: 16px; font-size: 12px; color: var(--text-3); }
.i01-issue-note { display: flex; align-items: flex-start; gap: 6px; margin: 4px 16px 16px; padding: 9px 12px; border-radius: var(--radius-sm); background: var(--primary-subtle); color: var(--text-1); font-size: 12px; line-height: 18px; }
.i01-issue-note .zw-mso { color: var(--primary); margin-top: 1px; }
.i01-staff-rule { display: flex; align-items: flex-start; gap: 10px; padding: 8px 0; }
.i01-staff-rule + .i01-staff-rule { border-top: 1px dashed var(--line-1); }
.i01-staff-rule .zw-mso { color: var(--text-3); margin-top: 1px; }
.i01-pending-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 0; }
.i01-pending-row + .i01-pending-row { border-top: 1px dashed var(--line-1); }
.i01-pending-main { min-width: 0; }
.i01-deadline-desc { font-size: 14px; color: var(--text-2); margin-bottom: 16px; }
.i01-deadline-row { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.i01-time { width: 130px; flex: 0 0 130px; }
.i01-deadline-rules-title { font-size: 14px; font-weight: 600; margin: 20px 0 8px; }
.i01-deadline-rule { display: flex; align-items: flex-start; gap: 10px; padding: 8px 0; }
.i01-deadline-rule + .i01-deadline-rule { border-top: 1px solid var(--line-1); }
.i01-deadline-rule .zw-mso { color: var(--text-3); margin-top: 1px; }
</style>
