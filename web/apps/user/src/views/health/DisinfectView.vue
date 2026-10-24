<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { DataTableHeader } from 'vuetify'
import { formatTime } from '@zmt/shared'
import type { DisinfectPlan, DisinfectRecord, DisinfectTask } from '@/api/health'
import { useDisinfectStore, type DisinfectTab, type PlanStatusFilter, type TaskStatusFilter } from '@/stores/health/disinfect'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { useFarmStore } from '@/stores/farm'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { dateText, dateTimeText, monthDay, nowInput, todayISO } from '@/stores/health/shared'
import HealthDateField from './components/HealthDateField.vue'
import HealthPhotoUpload from './components/HealthPhotoUpload.vue'

const store = useDisinfectStore()
const router = useRouter()
const farm = useFarmStore()
const auth = useAuthStore()
const ui = useUiStore()
const { isStaff } = useRole()
const registerPhotoKey = ref(0)
const recordPhotoKey = ref(0)
const registerPhotoBusy = ref(false)
const recordPhotoBusy = ref(false)

const taskHeaders: DataTableHeader[] = [
  { title: '栏舍', key: 'pen', sortable: false, width: 190 },
  { title: '任务日期', key: 'date', sortable: false, width: 120 },
  { title: '消毒剂与浓度 · 方式', key: 'drug', sortable: false },
  { title: '执行人', key: 'executor', sortable: false, width: 170 },
  { title: '实际执行', key: 'actual', sortable: false, width: 180 },
  { title: '状态', key: 'status', sortable: false, width: 100 },
  { title: '操作', key: 'op', sortable: false, width: 110, align: 'end' }
]

const planHeaders: DataTableHeader[] = [
  { title: '覆盖栏舍', key: 'pens', sortable: false },
  { title: '周期', key: 'cycle', sortable: false, width: 86 },
  { title: '起止日期', key: 'span', sortable: false, width: 170 },
  { title: '消毒剂与浓度 · 方式', key: 'drug', sortable: false, width: 220 },
  { title: '下次任务 / 说明', key: 'next', sortable: false, width: 250 },
  { title: '状态', key: 'status', sortable: false, width: 100 },
  { title: '操作', key: 'op', sortable: false, width: 160, align: 'end' }
]

const desc = computed(() => store.tab === 'tasks'
  ? '按计划为每个栏舍生成任务；空栏消毒和紧急消毒可以不依据计划，临时登记'
  : '系统按周期为每个栏舍各生成一条任务；计划暂停期间不生成任务')

const crumbs = computed(() => store.tab === 'tasks'
  ? ['防疫健康', '消毒管理', '消毒任务']
  : ['防疫健康', '消毒管理', '消毒计划'])

const tab = computed<string>({
  get: () => store.tab,
  set: (value: string) => store.setTab((value === 'plans' ? 'plans' : 'tasks') as DisinfectTab)
})

const pageSub = computed(() => farm.farmName || '')

function onTab(value: unknown): void {
  store.setTab(value === 'plans' ? 'plans' : 'tasks')
}

function onTaskStatus(value: unknown): void {
  store.setTaskStatus((value || 'ALL') as TaskStatusFilter)
}

function onPlanStatus(value: unknown): void {
  store.setPlanStatus((value || 'ALL') as PlanStatusFilter)
}

function onTaskPen(value: unknown): void {
  store.setTaskPen(Number(value || 0))
}

function openTemp(): void {
  registerPhotoKey.value += 1
  registerPhotoBusy.value = false
  void store.openRegisterTemp()
}

function openTaskRegister(task: DisinfectTask, backfill = false): void {
  registerPhotoKey.value += 1
  registerPhotoBusy.value = false
  void store.openRegisterForTask(task, backfill)
}

function openPlanCreate(): void {
  void store.openPlanCreate()
}

function openPlanEdit(plan: DisinfectPlan): void {
  void store.openPlanEdit(plan)
}

function openRecord(recordId?: number): void {
  if (!recordId) return
  recordPhotoKey.value += 1
  recordPhotoBusy.value = false
  void store.openRecord(recordId)
}

function editRecord(): void {
  if (!store.record) return
  recordPhotoKey.value += 1
  recordPhotoBusy.value = false
  void store.openRecord(Number(store.record.id), true)
}

function backToTaskList(): void {
  void store.fetchTasks()
}

function taskTile(task: DisinfectTask): string {
  if (task.status === 'OVERDUE') return 'zw-ico-tile--error'
  if (task.status === 'PENDING') return 'zw-ico-tile--primary'
  return ''
}

function taskStatusType(task: DisinfectTask): string {
  if (task.status === 'OVERDUE') return 'error'
  if (task.status === 'PENDING') return 'primary'
  return 'neutral'
}

function taskStatusIcon(task: DisinfectTask): string {
  if (task.status === 'OVERDUE') return 'schedule'
  if (task.status === 'DONE') return 'check'
  return ''
}

function taskExecLabel(task: DisinfectTask): string {
  if (isStaff.value && task.status === 'OVERDUE') return '已逾期，需所有者或场长补录'
  return task.executor_text || '—'
}

function taskActual(task: DisinfectTask): string {
  if (!task.actual_time) return '—'
  return `${monthDay(task.actual_time)} ${formatTime(task.actual_time)} · ${task.actual_executor_name || ''}`
}

function planStatusType(plan: DisinfectPlan): string {
  if (plan.status === 'ACTIVE') return 'primary'
  return 'neutral'
}

function planStatusIcon(plan: DisinfectPlan): string {
  if (plan.status === 'PAUSED') return 'pause'
  return ''
}

function planNext(plan: DisinfectPlan): string {
  if (plan.status === 'PAUSED') return plan.pause_text || '已暂停'
  if (plan.status === 'ENDED') return '计划已结束'
  return plan.next_task_text ? `下次任务 ${plan.next_task_text}` : plan.next_task_date ? `下次任务 ${monthDay(plan.next_task_date)}` : ''
}

function planTile(plan: DisinfectPlan): string {
  return plan.status === 'ACTIVE' ? 'zw-ico-tile--primary' : ''
}

function planPenLabel(plan: DisinfectPlan): string {
  return plan.pen_names_text || '—'
}

function taskDrugText(task: DisinfectTask): string {
  return `${task.disinfectant_name || ''} ${task.concentration || ''} · ${task.method_name || ''}`
}

function planDrugText(plan: DisinfectPlan): string {
  return `${plan.disinfectant_name || ''} ${plan.concentration || ''} · ${plan.method_name || ''}`
}

function taskDate(task: DisinfectTask): string {
  return task.date_text || monthDay(task.task_date)
}

function planSpan(plan: DisinfectPlan): string {
  return `${monthDay(plan.start_date)}–${monthDay(plan.end_date)}`
}

function calcTile(task: DisinfectTask): string {
  return taskTile(task)
}

function onTimeChange(): void {
  // 实际时间变化后不需要请求；保留占位以便模板扩展
}

function confirmConcentration(): void {
  store.confirmConcentration()
}

function confirmRecordConcentration(): void {
  store.recordModel.concentrationConfirmed = true
}

function setRecordConcentration(value: string): void {
  store.recordModel.concentration = value
  store.recordModel.concentrationConfirmed = false
}

function submitPlan(): void {
  void store.submitPlan()
}

function submitPause(): void {
  void store.submitPause()
}

function submitRegister(): void {
  void store.submitRegister()
}

function submitRecord(): void {
  void store.submitRecordEdit()
}

function goRecordChangeRequest(): void {
  if (!store.record) return
  void router.push({
    name: 'WU-U01',
    query: { action: 'create', biz_type: 'DISINFECT_RECORD', biz_id: String(store.record.id) }
  })
}

function openCalendar(): void {
  void router.push({ name: 'WU-T03' })
}

function isSelectedPen(penId: number): boolean {
  return store.planModel.penIds.includes(penId)
}

function onPlanDisinfectant(value: unknown): void {
  store.selectPlanDisinfectant(Number(value || 0))
}

function onRegisterType(value: unknown): void {
  void store.setRegisterType((value === 'EMPTY_PEN' || value === 'EMERGENCY' ? value : 'PLAN_TASK'))
}

function onTaskOption(value: unknown): void {
  void store.selectRegisterTask(Number(value || 0))
}

function onRegisterPen(value: unknown): void {
  void store.selectRegisterPen(Number(value || 0))
}

function onRegisterDrug(value: unknown): void {
  void store.selectRegisterDisinfectant(Number(value || 0))
}

function onRegisterLot(value: unknown): void {
  store.registerModel.lotId = Number(value || 0)
  void store.loadRegisterLots()
}

function onRegisterExecutor(value: unknown): void {
  store.registerModel.executorId = value === null || value === undefined || value === '' ? null : Number(value)
}

function onRecordLot(value: unknown): void {
  store.recordModel.lotId = Number(value || 0)
}

function onRecordExecutor(value: unknown): void {
  store.recordModel.executorId = value === null || value === undefined || value === '' ? null : Number(value)
}

function onRegisterConcentration(value: unknown): void {
  store.setConcentration(String(value || ''))
}

function onRecordConcentration(value: unknown): void {
  setRecordConcentration(String(value || ''))
}

function openPhoto(url: string): void {
  if (url) window.open(url, '_blank')
}

function onPhotoMessage(message: string): void {
  if (message) ui.toast(message, 'error')
}

function recordTypeTone(record: DisinfectRecord | null): string {
  if (record?.record_type === 'EMPTY_PEN') return '空栏消毒'
  if (record?.record_type === 'EMERGENCY') return '紧急消毒'
  return '计划任务'
}

function photoUrl(photo: { url?: string; thumb_url?: string }): string {
  return photo.url || photo.thumb_url || ''
}

onMounted(() => {
  void store.init(isStaff.value)
})

useFarmReload(async () => {
  await store.reload()
})
</script>

<template>
  <zw-shell side="user" active="WU-D03" :crumbs="crumbs">
    <zw-page-head title="消毒管理" :desc="desc">
      <template #actions>
        <v-btn v-if="store.tab === 'tasks'" color="primary" prepend-icon="add" @click="openTemp">登记临时消毒</v-btn>
        <v-btn v-else color="primary" prepend-icon="add" @click="openPlanCreate">新建计划</v-btn>
      </template>
      <v-tabs :model-value="store.tab" class="zw-tabs-line" @update:model-value="onTab">
        <v-tab value="tasks">消毒任务</v-tab>
        <v-tab v-if="!isStaff" value="plans">消毒计划 {{ store.planCounts.all }}</v-tab>
      </v-tabs>
    </zw-page-head>

    <!-- 消毒任务 -->
    <template v-if="store.tab === 'tasks'">
      <div class="zw-toolbar">
        <div class="zw-toolbar-l d-toolbar">
          <zw-seg :model-value="store.taskQuery.status" :items="store.taskSegments" @update:model-value="onTaskStatus" />
          <v-select
            :model-value="store.taskQuery.penId"
            :items="store.penItems"
            item-title="title"
            item-value="value"
            class="zw-w-160"
            hide-details
            @update:model-value="onTaskPen"
          />
        </div>
        <div class="zw-toolbar-r">
          <span class="body-s zw-faint">待执行只列今天和明天的任务，更远的任务在<a class="zw-link" @click="openCalendar">养殖日历</a>查看</span>
        </div>
      </div>

      <zw-notice v-if="store.overdueBanner?.show" type="info" icon="notifications_active" class="mb-4">
        {{ store.overdueBanner.text }}
      </zw-notice>

      <zw-card v-if="store.taskStatus === 'forbidden'">
        <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.taskError || '你没有这个栏舍的查看权限'" />
      </zw-card>
      <zw-card v-else-if="store.taskStatus === 'loading'">
        <v-skeleton-loader type="table-heading, table-row@6" />
      </zw-card>
      <zw-card v-else-if="store.taskStatus === 'error'">
        <zw-empty icon="cloud_off" tone="error" title="消毒任务加载失败" :desc="store.taskError">
          <v-btn color="primary" variant="outlined" @click="backToTaskList">重新加载</v-btn>
        </zw-empty>
      </zw-card>
      <zw-card v-else-if="store.taskStatus === 'empty' || store.taskStatus === 'filtered_empty' || !store.tasks.length" flush>
        <zw-empty
          :icon="store.taskStatus === 'filtered_empty' ? 'search_off' : 'sanitizer'"
          :title="store.taskEmptyText"
          :desc="store.taskEmptyDesc"
        >
          <v-btn v-if="!isStaff && store.taskStatus === 'empty'" color="primary" @click="openPlanCreate">新建计划</v-btn>
          <v-btn v-else-if="store.taskStatus === 'filtered_empty'" variant="text" @click="onTaskStatus('ALL')">查看全部</v-btn>
        </zw-empty>
      </zw-card>
      <zw-card v-else flush>
        <v-data-table :headers="taskHeaders" :items="store.tasks" item-value="id" hide-default-footer class="d-table">
          <template #item.pen="{ item }">
            <div class="d-main">
              <span class="zw-ico-tile" :class="taskTile(item)"><zw-icon name="sanitizer" :size="18" /></span>
              <span class="zw-cell-main">{{ item.pen_name }}</span>
            </div>
          </template>
          <template #item.date="{ item }"><span class="d-nowrap num">{{ taskDate(item) }}</span></template>
          <template #item.drug="{ item }"><span class="d-nowrap">{{ taskDrugText(item) }}</span></template>
          <template #item.executor="{ item }">
            <div class="d-nowrap">{{ taskExecLabel(item) }}</div>
            <div v-if="item.no_staff && !isStaff" class="zw-cell-sub">无负责员工</div>
          </template>
          <template #item.actual="{ item }"><span class="d-nowrap num">{{ taskActual(item) }}</span></template>
          <template #item.status="{ item }">
            <zw-tag :type="taskStatusType(item)" :icon="taskStatusIcon(item) || undefined">{{ item.status_text || (item.status === 'PENDING' ? '待执行' : item.status === 'OVERDUE' ? '已逾期' : '已执行') }}</zw-tag>
          </template>
          <template #item.op="{ item }">
            <span class="d-ops zw-cell-actions">
              <a v-if="item.status === 'PENDING'" @click="openTaskRegister(item)">登记消毒</a>
              <a v-else-if="item.status === 'OVERDUE' && !isStaff" @click="openTaskRegister(item, true)">补录</a>
              <a v-else-if="item.status === 'DONE' && item.record_id" @click="openRecord(item.record_id)">查看</a>
              <span v-else-if="item.status === 'OVERDUE'" class="zw-faint">待补录</span>
            </span>
          </template>
        </v-data-table>
        <div class="zw-table-foot zw-table-foot--in">
          <span class="zw-faint body-s">按 已逾期 → 待执行 → 已执行 排列；临时消毒不计入执行率，空栏消毒不计入批次成本</span>
          <zw-pager
            :total="store.taskTotal"
            :page="store.taskQuery.page"
            :size="store.taskQuery.size"
            @update:page="store.setTaskPage"
            @update:size="store.setTaskSize"
          />
        </div>
      </zw-card>
    </template>

    <!-- 消毒计划 -->
    <template v-else>
      <div class="zw-toolbar">
        <div class="zw-toolbar-l">
          <zw-seg :model-value="store.planQuery.status" :items="store.planSegments" @update:model-value="onPlanStatus" />
        </div>
      </div>

      <zw-card v-if="store.planStatus === 'forbidden'">
        <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.planError || '只有所有者和场长可以管理消毒计划'" />
      </zw-card>
      <zw-card v-else-if="store.planStatus === 'loading'">
        <v-skeleton-loader type="table-heading, table-row@3" />
      </zw-card>
      <zw-card v-else-if="store.planStatus === 'error'">
        <zw-empty icon="cloud_off" tone="error" title="消毒计划加载失败" :desc="store.planError">
          <v-btn color="primary" variant="outlined" @click="store.fetchPlans()">重新加载</v-btn>
        </zw-empty>
      </zw-card>
      <zw-card v-else-if="store.planStatus === 'empty' || store.planStatus === 'filtered_empty' || !store.plans.length" flush>
        <zw-empty
          :icon="store.planStatus === 'filtered_empty' ? 'search_off' : 'sanitizer'"
          :title="store.planEmptyText"
          :desc="store.planEmptyDesc"
        >
          <v-btn v-if="store.planStatus === 'empty'" color="primary" @click="openPlanCreate">新建计划</v-btn>
          <v-btn v-else variant="text" @click="onPlanStatus('ALL')">查看全部</v-btn>
        </zw-empty>
      </zw-card>
      <zw-card v-else flush>
        <v-data-table :headers="planHeaders" :items="store.plans" item-value="id" hide-default-footer class="d-table">
          <template #item.pens="{ item }">
            <div class="d-main">
              <span class="zw-ico-tile" :class="planTile(item)"><zw-icon name="sanitizer" :size="18" /></span>
              <span class="zw-cell-main">{{ planPenLabel(item) }}</span>
            </div>
          </template>
          <template #item.cycle="{ item }"><span class="d-nowrap">每 {{ item.cycle_days || 0 }} 天</span></template>
          <template #item.span="{ item }"><span class="d-nowrap num">{{ planSpan(item) }}</span></template>
          <template #item.drug="{ item }"><span class="d-nowrap">{{ planDrugText(item) }}</span></template>
          <template #item.next="{ item }"><span class="d-nowrap" :class="{ 'zw-faint': item.status !== 'ACTIVE' }">{{ planNext(item) }}</span></template>
          <template #item.status="{ item }">
            <zw-tag :type="planStatusType(item)" :icon="planStatusIcon(item) || undefined">{{ item.status_text || (item.status === 'ACTIVE' ? '启用中' : item.status === 'PAUSED' ? '已暂停' : '已结束') }}</zw-tag>
          </template>
          <template #item.op="{ item }">
            <span class="d-ops zw-cell-actions">
              <a v-if="item.status === 'ACTIVE'" @click="store.askPause(item)">暂停计划</a>
              <a v-else-if="item.status === 'PAUSED'" @click="store.resumePlan(item)">启用计划</a>
              <a @click="openPlanEdit(item)">修改计划</a>
            </span>
          </template>
        </v-data-table>
        <div class="zw-table-foot zw-table-foot--in">
          <span class="zw-faint body-s">系统按周期为每个栏舍各生成一条任务；计划暂停期间不生成任务。修改计划不需要审核，系统保留修改记录</span>
          <zw-pager
            :total="store.planTotal"
            :page="store.planQuery.page"
            :size="store.planQuery.size"
            @update:page="store.setPlanPage"
            @update:size="store.setPlanSize"
          />
        </div>
      </zw-card>
    </template>

    <!-- 新建 / 修改消毒计划 -->
    <zw-drawer
      v-model="store.planDrawer"
      :title="store.planMode === 'edit' ? '修改消毒计划' : '新建消毒计划'"
      :sub="store.planMode === 'edit' ? pageSub : `${pageSub} · 保存后按周期为每个栏舍各生成一条任务`"
      width="560"
    >
      <v-skeleton-loader v-if="store.planLoading" type="article, paragraph@5" />
      <template v-else>
        <zw-form-item label="栏舍（可多选）" required :error="store.planErrors.pen_ids">
          <div class="d-chips">
            <button
              v-for="pen in store.enabledPens"
              :key="pen.pen_id"
              type="button"
              :class="{ on: isSelectedPen(Number(pen.pen_id || 0)) }"
              @click="store.togglePlanPen(Number(pen.pen_id || 0))"
            >
              <zw-icon v-if="isSelectedPen(Number(pen.pen_id || 0))" name="check" :size="16" />
              {{ pen.pen_name }}
            </button>
          </div>
          <div v-if="store.planPenText" class="d-pen-tip">{{ store.planPenText }}</div>
        </zw-form-item>

        <div class="zw-form-3 mt-5">
          <zw-form-item label="周期天数" required :error="store.planErrors.cycle_days" :hint="store.planDoseHelper">
            <v-text-field v-model="store.planModel.cycleDays" type="number" suffix="天" @update:model-value="store.schedulePlanPreview()" />
          </zw-form-item>
          <zw-form-item label="开始日期" required :error="store.planErrors.start_date">
            <HealthDateField v-model="store.planModel.startDate" :min="todayISO()" @update:model-value="store.schedulePlanPreview()" />
          </zw-form-item>
          <zw-form-item label="结束日期" required :error="store.planErrors.end_date">
            <HealthDateField v-model="store.planModel.endDate" :min="store.planModel.startDate" @update:model-value="store.schedulePlanPreview()" />
          </zw-form-item>
        </div>

        <div class="zw-form-2 mt-4">
          <zw-form-item label="消毒剂（字典）" required :error="store.planErrors.catalog_id">
            <v-select
              :model-value="store.planModel.catalogId || undefined"
              :items="store.planDisinfectantItems"
              item-title="title"
              item-value="value"
              @update:model-value="onPlanDisinfectant"
            />
            <template #hint>
              <span :class="store.planStockWarning ? 'd-warning-text' : ''">{{ store.planStockHint }}</span>
            </template>
          </zw-form-item>
          <zw-form-item label="浓度" required :error="store.planErrors.concentration" :hint="store.selectedDisinfectant ? '已预填字典推荐浓度' : '选择消毒剂后自动填入推荐浓度'">
            <v-text-field v-model="store.planModel.concentration" placeholder="1:500" />
          </zw-form-item>
        </div>

        <zw-form-item label="消毒方式（字典）" required class="mt-4" :error="store.planErrors.method_code">
          <v-radio-group v-model="store.planModel.methodCode" inline hide-details>
            <v-radio v-for="method in store.planMethodItems" :key="method.value" :label="method.title" :value="method.value" />
          </v-radio-group>
        </zw-form-item>

        <div class="d-sum mt-5">
          <zw-icon name="event_repeat" :size="22" />
          <div class="d-sum-main">
            <div class="d-sum-t">{{ store.planSummaryTitle }}</div>
            <div v-if="store.planSummaryText" class="d-sum-d">{{ store.planSummaryText }}</div>
          </div>
          <v-progress-circular v-if="store.planPreviewLoading" indeterminate size="18" width="2" />
        </div>
      </template>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="store.planDrawer = false">取消</v-btn>
        <v-btn color="primary" prepend-icon="check" :loading="store.planSubmitting" :disabled="store.planLoading" @click="submitPlan">
          {{ store.planMode === 'edit' ? '保存修改' : '保存并启用' }}
        </v-btn>
      </template>
    </zw-drawer>

    <!-- 暂停计划 -->
    <zw-dialog v-model="store.pauseOpen" title="暂停消毒计划" width="420">
      <div class="d-dialog-sub">{{ store.pauseTarget?.pen_names_text || '' }}</div>
      <zw-form-item label="暂停原因" optional hint="会显示在计划列表中；最多 30 字" :error="store.pauseErrors.reason">
        <v-text-field v-model="store.pauseReason" maxlength="30" counter />
      </zw-form-item>
      <div class="d-pause-note">暂停期间不生成新的消毒任务，已生成的任务不变。</div>
      <template #actions>
        <v-btn variant="outlined" @click="store.pauseOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.pauseSubmitting" @click="submitPause">暂停计划</v-btn>
      </template>
    </zw-dialog>

    <!-- 登记消毒 -->
    <zw-drawer v-model="store.registerOpen" title="登记消毒" :sub="store.registerSub" width="560">
      <v-skeleton-loader v-if="store.registerLoading" type="article, paragraph@5" />
      <template v-else>
        <zw-notice v-if="store.registerError" type="warning" class="mb-4">{{ store.registerError }}</zw-notice>
        <zw-form-item label="登记类型" required :hint="store.registerTypeHint">
          <zw-seg
            :model-value="store.registerModel.recordType"
            :items="store.registerTypeSegments"
            @update:model-value="onRegisterType"
          />
        </zw-form-item>

        <div class="zw-form mt-4">
          <zw-form-item
            v-if="store.registerModel.recordType === 'PLAN_TASK'"
            label="消毒任务"
            required
            :error="store.registerErrors.task_id"
            :hint="store.registerPlanText"
          >
            <v-select
              :model-value="store.registerModel.taskId || undefined"
              :items="store.taskOptionItems"
              item-title="title"
              item-value="value"
              placeholder="选择待执行或已逾期的任务"
              :loading="store.taskOptionsLoading"
              @update:model-value="onTaskOption"
            />
            <div v-if="!store.taskOptionsLoading && !store.taskOptionItems.length" class="d-hint">
              今天没有待执行的消毒任务。可以切换到空栏消毒或紧急消毒临时登记。
            </div>
          </zw-form-item>
          <zw-form-item
            v-else
            label="栏舍"
            required
            :error="store.registerErrors.pen_id"
            :hint="store.registerTypeHint"
          >
            <v-select
              :model-value="store.registerModel.penId || undefined"
              :items="store.registerPenItems"
              item-title="title"
              item-value="value"
              @update:model-value="onRegisterPen"
            />
          </zw-form-item>

          <div class="zw-form-2">
            <zw-form-item label="实际时间" required :error="store.registerErrors.actual_time">
              <v-text-field
                v-model="store.registerModel.actualTime"
                type="datetime-local"
                step="60"
                :max="nowInput()"
                @update:model-value="onTimeChange"
              />
            </zw-form-item>
            <zw-form-item v-if="!isStaff" label="实际执行人" :hint="`登记人：${auth.displayName}`">
              <v-select
                :model-value="store.registerModel.executorId || undefined"
                :items="store.registerExecutorItems"
                item-title="title"
                item-value="value"
                placeholder="选择本场成员"
                @update:model-value="onRegisterExecutor"
              />
            </zw-form-item>
          </div>

          <zw-form-item label="消毒剂" required :error="store.registerErrors.drug_id">
            <v-select
              :model-value="store.registerModel.drugId || undefined"
              :items="store.registerDisinfectantItems"
              item-title="title"
              item-value="value"
              :disabled="store.registerModel.recordType === 'PLAN_TASK'"
              @update:model-value="onRegisterDrug"
            />
          </zw-form-item>

          <zw-form-item label="消毒剂批号" required :error="store.registerErrors.drug_lot_id" :hint="store.registerLotHelper">
            <v-select
              :model-value="store.registerModel.lotId || undefined"
              :items="store.registerLotItems"
              item-title="title"
              item-value="value"
              :loading="store.lotsLoading"
              @update:model-value="onRegisterLot"
            />
          </zw-form-item>

          <div class="zw-form-2">
            <zw-form-item label="用量" required :error="store.registerErrors.usage_qty" :hint="store.registerUsageHelper">
              <v-text-field v-model="store.registerModel.usageQty" type="number" suffix="升" @blur="store.loadRegisterLots()" />
            </zw-form-item>
            <zw-form-item label="浓度" required :error="store.registerErrors.concentration">
              <v-text-field
                :model-value="store.registerModel.concentration"
                placeholder="1:500"
                :append-inner-icon="store.registerConcentrationMismatch ? 'warning' : undefined"
                class="d-warn-ico"
                @update:model-value="onRegisterConcentration"
              />
              <template #hint>
                <span :class="store.registerConcentrationMismatch ? 'd-warning-text' : ''">{{ store.registerConcentrationHelper }}</span>
              </template>
            </zw-form-item>
          </div>

          <zw-notice v-if="store.registerConcentrationMismatch" type="warning" class="mb-3">
            <template v-if="store.registerModel.concentrationConfirmed">
              <b>已确认本次按 {{ store.registerModel.concentration }} 配制。</b>
            </template>
            <template v-else>
              <b>浓度与计划不一致。</b>请确认本次按 {{ store.registerModel.concentration }} 配制后再登记。
            </template>
            <template v-if="!store.registerModel.concentrationConfirmed" #action>
              <a class="d-confirm" @click="confirmConcentration">确认</a>
            </template>
          </zw-notice>
          <div v-if="store.registerErrors.concentration_confirmed" class="d-error">{{ store.registerErrors.concentration_confirmed }}</div>

          <zw-form-item label="消毒方式" required :error="store.registerErrors.method_code">
            <v-radio-group v-model="store.registerModel.methodCode" inline hide-details>
              <v-radio v-for="method in store.registerMethodItems" :key="method.value" :label="method.title" :value="method.value" />
            </v-radio-group>
          </zw-form-item>
        </div>

        <div class="d-sec">
          <span class="zw-section-title">现场照片</span>
          <span class="zw-faint body-s num">{{ store.registerModel.fileIds.length }} / 6</span>
        </div>
        <HealthPhotoUpload
          :key="registerPhotoKey"
          v-model="store.registerModel.fileIds"
          :max="6"
          @update:busy="registerPhotoBusy = $event"
          @message="onPhotoMessage"
        />
      </template>
      <template #foot>
        <span class="zw-foot-note">照片上传完成后才能提交</span>
        <v-btn variant="outlined" @click="store.registerOpen = false">取消</v-btn>
        <v-btn color="primary" prepend-icon="sanitizer" :loading="store.registerSubmitting" :disabled="store.registerLoading || registerPhotoBusy" @click="submitRegister">
          登记消毒
        </v-btn>
      </template>
    </zw-drawer>

    <!-- 消毒记录查看 / 修改 -->
    <zw-drawer v-model="store.recordOpen" :title="store.recordEdit ? '修改消毒记录' : '消毒记录'" :sub="store.recordPenName" width="560">
      <v-skeleton-loader v-if="store.recordLoading" type="article, paragraph@4" />
      <template v-else-if="store.record">
        <zw-notice v-if="store.recordError" type="warning" class="mb-4">{{ store.recordError }}</zw-notice>
        <template v-if="!store.recordEdit">
          <zw-desc :cols="2">
            <zw-desc-item label="栏舍">{{ store.record.pen_name || '—' }}</zw-desc-item>
            <zw-desc-item label="登记类型">{{ recordTypeTone(store.record) }}</zw-desc-item>
            <zw-desc-item label="任务日期">{{ store.record.task_date ? dateText(store.record.task_date) : '—' }}</zw-desc-item>
            <zw-desc-item label="实际时间">{{ store.record.actual_time ? dateTimeText(store.record.actual_time) : '—' }}</zw-desc-item>
            <zw-desc-item label="执行人">{{ store.record.executor_name || '—' }}</zw-desc-item>
            <zw-desc-item label="登记人">{{ store.record.creator_name || '—' }}</zw-desc-item>
            <zw-desc-item label="消毒剂">{{ store.record.disinfectant_name || '—' }}</zw-desc-item>
            <zw-desc-item label="批号">{{ store.record.lot_no || '—' }}</zw-desc-item>
            <zw-desc-item label="用量">{{ store.record.usage_qty ?? '—' }} {{ store.record.usage_unit || '' }}</zw-desc-item>
            <zw-desc-item label="浓度">
              {{ store.record.concentration || '—' }}
              <span v-if="store.record.plan_concentration" class="zw-faint">（计划 {{ store.record.plan_concentration }}）</span>
            </zw-desc-item>
            <zw-desc-item label="消毒方式">{{ store.record.method_name || '—' }}</zw-desc-item>
            <zw-desc-item label="统计口径">{{ store.recordKindText }}</zw-desc-item>
          </zw-desc>
          <section class="d-sec">
            <span class="zw-section-title">现场照片</span>
            <span class="zw-faint body-s num">{{ (store.record.photos || []).length }} 张</span>
          </section>
          <div v-if="store.record.photos?.length" class="d-photos">
            <img v-for="photo in store.record.photos" :key="photo.file_id" :src="photoUrl(photo)" alt="现场照片" @click="openPhoto(photoUrl(photo))">
          </div>
          <div v-else class="zw-faint body-s">没有现场照片</div>
          <section class="d-sec">
            <span class="zw-section-title">修改记录</span>
          </section>
          <v-skeleton-loader v-if="store.logLoading" type="list-item-two-line@2" />
          <zw-timeline v-else-if="store.logItems.length" :items="store.logItems" />
          <div v-else class="zw-faint body-s">还没有修改记录</div>
        </template>
        <template v-else>
          <div class="zw-form">
            <zw-form-item label="实际时间" required :error="store.recordErrors.actual_time">
              <v-text-field v-model="store.recordModel.actualTime" type="datetime-local" step="60" :max="nowInput()" />
            </zw-form-item>
            <zw-form-item v-if="!isStaff" label="实际执行人">
              <v-select
                :model-value="store.recordModel.executorId || undefined"
                :items="store.recordExecutors.map((member) => ({ title: member.display_name || member.name || '', value: Number(member.user_id || 0) }))"
                item-title="title"
                item-value="value"
                @update:model-value="onRecordExecutor"
              />
            </zw-form-item>
            <zw-form-item label="消毒剂批号" :error="store.recordErrors.drug_lot_id">
              <v-select
                :model-value="store.recordModel.lotId || undefined"
                :items="store.recordLotItems"
                item-title="title"
                item-value="value"
                :loading="store.recordLotsLoading"
                @update:model-value="onRecordLot"
              />
            </zw-form-item>
            <div class="zw-form-2">
              <zw-form-item label="用量" required :error="store.recordErrors.usage_qty">
                <v-text-field v-model="store.recordModel.usageQty" type="number" :suffix="store.record.usage_unit || '升'" />
              </zw-form-item>
              <zw-form-item label="浓度" required :error="store.recordErrors.concentration">
                <v-text-field
                  :model-value="store.recordModel.concentration"
                  placeholder="1:500"
                  :append-inner-icon="store.record.plan_concentration && store.recordModel.concentration !== store.record.plan_concentration ? 'warning' : undefined"
                  class="d-warn-ico"
                  @update:model-value="onRecordConcentration"
                />
                <template #hint>
                  <span v-if="store.record.plan_concentration" class="d-warning-text">计划浓度 {{ store.record.plan_concentration }}</span>
                </template>
              </zw-form-item>
            </div>
            <zw-notice
              v-if="store.record.plan_concentration && store.recordModel.concentration !== store.record.plan_concentration"
              type="warning"
              class="mb-3"
            >
              <template v-if="store.recordModel.concentrationConfirmed">
                <b>已确认本次按 {{ store.recordModel.concentration }} 配制。</b>
              </template>
              <template v-else>
                <b>浓度与计划不一致。</b>请确认本次按 {{ store.recordModel.concentration }} 配制后再保存。
              </template>
              <template v-if="!store.recordModel.concentrationConfirmed" #action>
                <a class="d-confirm" @click="confirmRecordConcentration">确认</a>
              </template>
            </zw-notice>
            <zw-form-item label="消毒方式" required :error="store.recordErrors.method_code">
              <v-radio-group v-model="store.recordModel.methodCode" inline hide-details>
                <v-radio v-for="method in store.methods" :key="method.code" :label="method.name" :value="method.code" />
              </v-radio-group>
            </zw-form-item>
          </div>
          <div class="d-sec">
            <span class="zw-section-title">现场照片</span>
            <span class="zw-faint body-s num">{{ store.recordModel.fileIds.length }} / 6</span>
          </div>
          <HealthPhotoUpload
            :key="recordPhotoKey"
            v-model="store.recordModel.fileIds"
            :initial="store.record.photos || []"
            :max="6"
            @update:busy="recordPhotoBusy = $event"
            @message="onPhotoMessage"
          />
        </template>
      </template>
      <template #foot>
        <template v-if="store.record && !store.recordEdit">
          <v-btn variant="outlined" @click="store.recordOpen = false">关闭</v-btn>
          <v-btn v-if="store.record.actions?.includes('CHANGE_REQUEST')" variant="outlined" @click="goRecordChangeRequest">提交修改申请</v-btn>
          <v-btn v-else-if="store.record.actions?.includes('EDIT')" variant="outlined" prepend-icon="edit" @click="editRecord">修改记录</v-btn>
          <v-btn variant="text" prepend-icon="history" @click="store.openLogsFor(store.record)">查看修改记录</v-btn>
        </template>
        <template v-else-if="store.recordEdit">
          <span class="zw-foot-note">{{ recordPhotoBusy ? '照片上传完成后才能提交' : '保存后重新计算统计口径' }}</span>
          <v-btn variant="outlined" @click="store.recordEdit = false">取消</v-btn>
          <v-btn color="primary" :loading="store.recordSubmitting" :disabled="recordPhotoBusy" @click="submitRecord">保存修改</v-btn>
        </template>
        <v-btn v-else variant="outlined" @click="store.recordOpen = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <!-- 修改记录 -->
    <zw-drawer v-model="store.logOpen" title="消毒记录修改记录" :sub="store.logTitle" width="560">
      <v-skeleton-loader v-if="store.logLoading" type="list-item-two-line@3" />
      <zw-empty v-else-if="store.logError" icon="cloud_off" tone="error" title="修改记录加载失败" :desc="store.logError" />
      <zw-timeline v-else-if="store.logItems.length" :items="store.logItems" />
      <zw-empty v-else icon="history" title="还没有修改记录" desc="修改消毒记录后会显示在这里。" />
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.d-toolbar { display: flex; align-items: center; gap: 12px; }
.d-main { display: flex; align-items: center; gap: 10px; white-space: nowrap; }
.d-nowrap { white-space: nowrap; }
.d-ops { display: inline-flex; gap: 12px; white-space: nowrap; }
.d-table :deep(td) { white-space: nowrap; }
.d-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.d-chips button { display: inline-flex; align-items: center; gap: 4px; height: 32px; padding: 0 14px; border: 1px solid var(--control-border); border-radius: var(--radius-sm); background: var(--bg-card); color: var(--text-1); font: inherit; font-size: 14px; cursor: pointer; }
.d-chips button.on { border-color: var(--primary); background: var(--primary-subtle); color: var(--primary); font-weight: 500; padding-left: 10px; }
.d-pen-tip { margin-top: 8px; font-size: 12px; line-height: 20px; color: var(--text-3); }
.d-sum { display: flex; gap: 12px; padding: 14px 16px; border-radius: var(--radius-sm); background: var(--fill-2); }
.d-sum > .zw-mso { color: var(--text-2); margin-top: 1px; }
.d-sum-main { flex: 1; min-width: 0; }
.d-sum-t { font-weight: 600; }
.d-sum-d { font-size: 14px; line-height: 22px; color: var(--text-2); }
.d-warning-text { color: var(--warning); }
.d-warn-ico :deep(.v-field__append-inner > .v-icon) { color: var(--warning) !important; }
.d-confirm { font-weight: 600; text-decoration: underline; cursor: pointer; }
.d-error { margin: -4px 0 12px; font-size: 12px; color: var(--error); }
.d-sec { display: flex; align-items: center; justify-content: space-between; margin: 20px 0 8px; }
.d-sec .zw-section-title { margin: 0; }
.d-photos { display: flex; flex-wrap: wrap; gap: 10px; }
.d-photos img { width: 92px; height: 69px; object-fit: cover; border-radius: var(--radius-sm); border: 1px solid var(--line-1); cursor: zoom-in; }
.d-dialog-sub { margin-bottom: 12px; font-weight: 600; }
.d-pause-note { margin-top: 12px; font-size: 12px; line-height: 20px; color: var(--text-3); }
.d-hint { margin-top: 4px; font-size: 12px; line-height: 18px; color: var(--text-3); }
</style>
