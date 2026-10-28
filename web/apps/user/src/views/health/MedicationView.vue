<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { DataTableHeader } from 'vuetify'
import type { MedicationRecord, WithdrawalDrug } from '@/api/health'
import { useMedicationStore } from '@/stores/health/medication'
import { useFarmReload } from '@/composables/useFarmReload'
import { queryId, queryText, useRouteAction } from '@/composables/useRouteAction'
import { useUiStore } from '@/stores/ui'
import { fetchLinkableReports, type LinkableReport } from '@/api/inspect'
import { useRole } from '@/composables/useRole'
import { useAuthStore } from '@/stores/auth'
import { dateText, monthDay, todayISO } from '@/stores/health/shared'
import HealthDateField from './components/HealthDateField.vue'

const store = useMedicationStore()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const { isStaff } = useRole()

const recordHeaders: DataTableHeader[] = [
  { title: '药品 · 给药方式', key: 'drug', sortable: false },
  { title: '用药时间', key: 'span', sortable: false, width: 160 },
  { title: '疗程', key: 'course', sortable: false, width: 80, align: 'end' },
  { title: '批号', key: 'lot', sortable: false, width: 110 },
  { title: '休药天数', key: 'withdrawal', sortable: false, width: 100, align: 'end' },
  { title: '用药原因', key: 'reason', sortable: false, width: 170 },
  { title: '登记人', key: 'creator', sortable: false, width: 100 },
  { title: '操作', key: 'op', sortable: false, width: 220, align: 'end' }
]

const calcHeaders: DataTableHeader[] = [
  { title: '药品', key: 'drug', sortable: false },
  { title: '末次用药', key: 'last', sortable: false, width: 110 },
  { title: '休药天数', key: 'days', sortable: false, width: 100, align: 'end' },
  { title: '到期日', key: 'due', sortable: false, width: 110 },
  { title: '结果', key: 'tag', sortable: false, width: 100 }
]

const desc = '可出栏日期 = 各药品末次用药日 + 休药天数，取最晚的一天；用药记录变动后立即重新计算'

const batchOptions = computed(() => store.batchItems)
const drugs = computed<WithdrawalDrug[]>(() => store.withdrawal?.drugs || [])
const calcEmpty = computed(() => store.withdrawalKind === 'NONE' || !drugs.value.length)
const releaseLine = computed(() => {
  if (store.withdrawalKind === 'NONE') return ''
  return `可出栏 ${dateText(store.withdrawal?.release_date)}`
})
const targetItems = computed(() => (store.model.targetType === 'BATCH' ? store.batchTargetItems : store.penTargetItems))
const executorItems = computed(() => store.executorItems)
const methodMismatch = computed(() => store.categoryMismatch)
const isEdit = computed(() => store.formMode === 'edit')
const deleteImpactLoading = computed(() => store.deleteImpactLoading)
const changeBefore = computed(() => store.releaseChangeText.before)
const changeAfter = computed(() => store.releaseChangeText.after)

function onBatchChange(value: unknown): void {
  void store.selectBatch(Number(value || 0))
}

function onTargetType(value: unknown): void {
  void store.setTargetType(value === 'PEN' ? 'PEN' : 'BATCH')
}

function onTargetChange(value: unknown): void {
  void store.selectTarget(Number(value || 0))
}

function onDrugChange(value: unknown): void {
  void store.selectDrug(Number(value || 0))
}

function onFirstDateChange(value: string): void {
  store.model.firstDate = value
  store.schedulePreview()
}

function onCourseChange(): void {
  store.schedulePreview()
}

function onUsedQtyBlur(): void {
  void store.loadLots()
}

function onLotSelect(value: unknown): void {
  store.model.lotId = Number(value || 0)
  void store.loadLots()
}

function onExecutorSelect(value: unknown): void {
  store.model.executorId = value === null || value === undefined || value === '' ? null : Number(value)
}

function onDoseChange(): void {
  store.schedulePreview()
}

function submit(): void {
  void store.submit()
}

function confirmDelete(): void {
  void store.confirmDelete()
}

function goDeleteRequest(): void {
  const query = store.deleteRequestQuery()
  store.deleteOpen = false
  void router.push({ name: 'WU-U01', query })
}

function submitNotify(): void {
  void store.submitNotify()
}

function rowActions(record: MedicationRecord): Array<{ label: string; action: string }> {
  if (record.change_state?.pending_request_id) {
    return [
      { label: '查看修改申请', action: 'VIEW_REQUEST' },
      { label: '查看修改记录', action: 'VIEW_LOG' }
    ]
  }
  const labels: Record<string, string> = {
    EDIT: '修改',
    CHANGE_REQUEST: '提交修改申请',
    DELETE: '删除',
    VIEW_LOG: '查看修改记录',
    NOTIFY: '通知场长',
    VIEW_REQUEST: '查看修改申请'
  }
  return (record.actions || []).map((action) => ({ label: labels[action] || action, action }))
}

function runAction(record: MedicationRecord, action: string): void {
  if (action === 'EDIT') {
    void store.openEdit(record)
    return
  }
  if (action === 'DELETE') {
    void store.openDelete(record)
    return
  }
  if (action === 'NOTIFY') {
    store.openNotify(record)
    return
  }
  if (action === 'VIEW_LOG') {
    void store.openLogs(record)
    return
  }
  if (action === 'CHANGE_REQUEST') {
    void router.push({
      name: 'WU-U01',
      query: { action: 'create', biz_type: 'MEDICATION_RECORD', biz_id: String(record.id || 0) }
    })
    return
  }
  if (action === 'VIEW_REQUEST') {
    void router.push({ name: 'WU-U01', query: { request_id: String(record.change_state?.pending_request_id || 0) } })
  }
}

function tagType(drug: WithdrawalDrug): string {
  if (drug.tag === 'LATEST') return 'warning'
  if (drug.tag === 'ENDED') return 'success'
  return 'neutral'
}

function calcTile(drug: WithdrawalDrug): string {
  return drug.tag === 'LATEST' ? 'zw-ico-tile--warning' : ''
}

function drugLocked(record: MedicationRecord): boolean {
  return !!record.locked
}

function drugPending(record: MedicationRecord): boolean {
  return !!record.change_state?.pending_request_id
}

/** create：快捷登记 / 待办进入时打开“登记用药”，带批次时先切到该批次。 */
const routeAction = useRouteAction(async (action, query) => {
  const batchId = queryId(query, 'batchId', 'batch_id')
  if (batchId && batchId !== store.batchId && store.batches.some((batch) => Number(batch.batch_id) === batchId)) {
    await store.selectBatch(batchId)
  }
  if (action !== 'create') return
  if (!store.batches.length) {
    useUiStore().toast('还没有在养批次，批次入栏后才能登记用药', 'info')
    return
  }
  await store.openCreate()
  // 诊断处理“转用药登记”带来的预填：关联上报、用药原因
  const reportId = queryId(query, 'reportId', 'report_id')
  if (reportId) store.model.reportId = reportId
  const reason = queryText(query, 'reason')
  if (reason) store.model.reason = reason.slice(0, 100)
})

/* 关联异常上报（API-I24）：按所选批次 / 栏舍列出可关联的上报，选填 */
const linkableReports = ref<LinkableReport[]>([])
let linkableSeq = 0
async function loadLinkable(): Promise<void> {
  const seq = ++linkableSeq
  const model = store.model
  const params = model.targetType === 'PEN'
    ? (model.penId ? { pen_id: model.penId } : null)
    : (model.batchId ? { batch_id: model.batchId } : null)
  if (!params) {
    linkableReports.value = []
    return
  }
  try {
    const list = await fetchLinkableReports(params)
    if (seq === linkableSeq) linkableReports.value = list || []
  } catch {
    if (seq === linkableSeq) linkableReports.value = []
  }
}
watch(() => [store.formOpen, store.model.targetType, store.model.batchId, store.model.penId], () => {
  if (store.formOpen) void loadLinkable()
})
const linkableItems = computed(() => {
  const items = linkableReports.value.map((item) => ({
    title: item.label || [item.reported_at?.slice(5, 16), item.pen_name, item.top_cause_name].filter(Boolean).join(' · ') || `上报 #${item.report_id}`,
    value: Number(item.report_id)
  }))
  const current = Number(store.model.reportId || 0)
  if (current && !items.some((item) => item.value === current)) {
    items.unshift({ title: store.detail?.report_summary || `已关联的上报 #${current}`, value: current })
  }
  return items
})
function onReportSelect(value: unknown): void {
  store.model.reportId = value ? Number(value) : null
}

onMounted(async () => {
  store.staffView = isStaff.value
  await store.load()
  const batchId = queryId(route.query, 'batchId', 'batch_id')
  if (!route.query.action && batchId && batchId !== store.batchId && store.batches.some((batch) => Number(batch.batch_id) === batchId)) {
    await store.selectBatch(batchId)
  }
  await routeAction.start()
})

useFarmReload(async () => {
  store.staffView = isStaff.value
  await store.reload()
})
</script>

<template>
  <zw-shell side="user" active="WU-D02" :crumbs="['防疫健康', '用药与休药']">
    <zw-page-head title="用药与休药" :desc="desc">
      <template #tags>
        <v-tooltip
          text="休药期：最后一次用药到可以出栏之间必须间隔的天数，按兽药目录中各药品对该品类的规定计算。休药期内的批次不能登记出栏。"
          max-width="320"
        >
          <template #activator="{ props: tooltipProps }">
            <zw-icon v-bind="tooltipProps" name="help" :size="18" class="m-help" />
          </template>
        </v-tooltip>
      </template>
      <template #actions>
        <v-btn color="primary" prepend-icon="medication" @click="store.openCreate()">登记用药</v-btn>
      </template>
    </zw-page-head>

    <div class="zw-toolbar">
      <div class="zw-toolbar-l m-batch">
        <v-select
          :model-value="store.batchId || undefined"
          :items="batchOptions"
          item-title="title"
          item-value="value"
          class="zw-w-280"
          placeholder="选择批次"
          :loading="store.batchLoading"
          hide-details
          @update:model-value="onBatchChange"
        />
        <span class="m-batch-text">{{ store.batchOverview }}</span>
      </div>
    </div>

    <zw-notice v-if="store.status === 'forbidden'" type="warning" title="无权查看" class="mb-4">
      {{ store.error || '你没有这个批次或栏舍的查看权限' }}
    </zw-notice>

    <zw-card v-else-if="store.status === 'loading' || store.batchLoading">
      <v-skeleton-loader type="article, table-row@4" />
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="用药档案加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.loadAll()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="!store.batches.length">
      <zw-empty icon="medication" title="还没有在养批次" desc="批次入栏后可以登记用药，并显示休药期与可出栏日期。" />
    </zw-card>

    <template v-else>
      <div class="m-top">
        <div class="m-wd" :class="`m-wd--${store.withdrawalKind.toLowerCase()}`">
          <div class="m-wd-st">
            <zw-icon :name="store.withdrawalKind === 'ENDED' ? 'check_circle' : store.withdrawalKind === 'IN_WITHDRAWAL' ? 'hourglass_top' : 'medication'" :size="20" />
            {{ store.withdrawalText }}
          </div>
          <div v-if="releaseLine" class="m-wd-date num">{{ releaseLine }}</div>
          <template v-if="store.withdrawalKind !== 'NONE'">
            <v-progress-linear
              :model-value="store.withdrawalProgress"
              height="4"
              class="m-wd-bar"
              :color="store.withdrawalKind === 'ENDED' ? 'success' : 'warning'"
            />
            <div class="zw-between m-wd-foot">
              <span>末次用药 {{ monthDay(store.withdrawal?.last_med_date) }}</span>
              <span>休药 {{ store.withdrawal?.withdrawal_days || 0 }} 天</span>
            </div>
          </template>
          <div class="m-wd-note">{{ store.withdrawalNote }}</div>
        </div>

        <zw-card title="按药品计算" sub="取最晚的一天" flush class="m-calc">
          <v-data-table v-if="!calcEmpty" :headers="calcHeaders" :items="drugs" hide-default-footer class="m-calc-table">
            <template #item.drug="{ item }">
              <div class="m-drug">
                <span class="zw-ico-tile" :class="calcTile(item)"><zw-icon name="medication" :size="18" /></span>
                <span class="zw-cell-main">{{ item.drug_name }}</span>
              </div>
            </template>
            <template #item.last="{ item }"><span class="num">{{ monthDay(item.last_date) }}</span></template>
            <template #item.days="{ item }"><span class="num">{{ item.withdrawal_days || 0 }} 天</span></template>
            <template #item.due="{ item }"><b class="num">{{ monthDay(item.end_date) }}</b></template>
            <template #item.tag="{ item }">
              <zw-tag v-if="item.tag && item.tag !== 'NONE'" :type="tagType(item)">{{ item.tag_text || (item.tag === 'LATEST' ? '最晚' : '已到期') }}</zw-tag>
              <span v-else class="zw-faint">—</span>
            </template>
          </v-data-table>
          <div v-if="calcEmpty" class="m-calc-empty">
            {{ store.withdrawalKind === 'NONE' ? '这个批次还没有用药记录。' : '还没有按药品计算的休药数据。' }}
          </div>
          <div class="zw-table-foot zw-table-foot--in">
            <span class="m-calc-note">可出栏日期 = 各药品末次用药日 + 休药天数，取最晚的一天；用药记录变动后立即重新计算。</span>
          </div>
        </zw-card>
      </div>

      <zw-card flush title="用药记录" :sub="store.recordsSub">
        <template v-if="!store.records.length">
          <zw-empty
            icon="medication"
            title="还没有用药记录"
            desc="批次用药后点“登记用药”记录，系统会算出可出栏日期。"
          >
            <v-btn color="primary" @click="store.openCreate()">登记用药</v-btn>
          </zw-empty>
        </template>
        <template v-else>
          <v-data-table :headers="recordHeaders" :items="store.records" item-value="id" hide-default-footer class="m-table">
            <template #item.drug="{ item }">
              <div class="m-drug">
                <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="medication" :size="18" /></span>
                <span class="zw-cell-main">
                  {{ item.drug_name }}<template v-if="item.route_name"> · {{ item.route_name }}</template>
                </span>
                <zw-tag v-if="drugPending(item)" type="warning" size="small">待审核</zw-tag>
                <zw-tag v-if="drugLocked(item)" size="small" icon="lock">已锁定</zw-tag>
              </div>
            </template>
            <template #item.span="{ item }"><span class="m-nowrap num">{{ monthDay(item.first_date) }}–{{ monthDay(item.last_date) }}</span></template>
            <template #item.course="{ item }"><span class="num">{{ item.course_days || 0 }} 天</span></template>
            <template #item.lot="{ item }"><span class="num">{{ item.lot_no || '—' }}</span></template>
            <template #item.withdrawal="{ item }"><span class="num">{{ item.withdrawal_days || 0 }} 天</span></template>
            <template #item.reason="{ item }"><span class="m-ellipsis" :title="item.reason || ''">{{ item.reason || '—' }}</span></template>
            <template #item.creator="{ item }">{{ item.is_mine ? '你' : item.creator_name || '—' }}</template>
            <template #item.op="{ item }">
              <span class="m-ops zw-cell-actions">
                <a
                  v-for="op in rowActions(item)"
                  :key="op.action"
                  :class="{ danger: op.action === 'DELETE' }"
                  @click="runAction(item, op.action)"
                >{{ op.label }}</a>
                <span v-if="!rowActions(item).length" class="zw-faint">—</span>
              </span>
            </template>
          </v-data-table>
          <div class="zw-table-foot zw-table-foot--in">
            <span class="zw-faint body-s">
              {{ isStaff
                ? '你只能修改自己当天登记的记录；会使可出栏日期提前的删除和修改，由所有者或场长提交修改申请'
                : '会使可出栏日期提前的修改和删除需要提交修改申请；批次出栏后记录自动锁定' }}
            </span>
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
    </template>

    <!-- 登记 / 修改用药 -->
    <zw-drawer v-model="store.formOpen" :title="store.formTitle" :sub="store.formSub" width="560">
      <v-skeleton-loader v-if="store.formLoading" type="article, paragraph@5" />
      <template v-else>
        <zw-notice v-if="store.formError" type="warning" class="mb-4">{{ store.formError }}</zw-notice>
        <zw-notice v-if="store.bannedHint" type="warning" class="mb-4">{{ store.bannedHint }}</zw-notice>
        <div class="zw-form">
          <div class="zw-form-2">
            <zw-form-item label="用药对象" required hint="选栏舍时对舍内所有在栏批次生效">
              <zw-seg
                :model-value="store.model.targetType"
                :items="[{ label: '批次', value: 'BATCH' }, { label: '栏舍', value: 'PEN' }]"
                @update:model-value="onTargetType"
              />
            </zw-form-item>
            <zw-form-item :label="store.model.targetType === 'BATCH' ? '批次' : '栏舍'" required :error="store.errors.batch_id || store.errors.pen_id" :hint="store.targetHelper">
              <v-select
                :model-value="(store.model.targetType === 'BATCH' ? store.model.batchId : store.model.penId) || undefined"
                :items="targetItems"
                item-title="title"
                item-value="value"
                :disabled="isEdit"
                :loading="store.targetsLoading"
                @update:model-value="onTargetChange"
              />
            </zw-form-item>
          </div>

          <div class="zw-form-2">
            <zw-form-item label="药品（兽药目录）" required :error="store.errors.drug_id" :hint="store.selectedDrug?.hint_text || '禁用药不可选'">
              <v-select
                :model-value="store.model.drugId || undefined"
                :items="store.drugItems"
                item-title="title"
                item-value="value"
                :loading="store.drugsLoading"
                @update:model-value="onDrugChange"
              />
            </zw-form-item>
            <zw-form-item label="库存批号" required :error="store.errors.drug_lot_id" :hint="store.lotHelper">
              <v-select
                :model-value="store.model.lotId || undefined"
                :items="store.lotItems"
                item-title="title"
                item-value="value"
                :loading="store.lotsLoading"
                @update:model-value="onLotSelect"
              />
            </zw-form-item>
          </div>

          <div class="zw-form-2">
            <zw-form-item label="剂量" required :error="store.errors.dose">
              <v-text-field
                v-model="store.model.dose"
                type="number"
                :suffix="store.selectedDrug?.dose_unit || ''"
                :append-inner-icon="store.overDose ? 'warning' : undefined"
                class="m-warn-ico"
                @update:model-value="onDoseChange"
              />
              <template #hint>
                <span :class="store.overDose ? 'm-warning-text' : ''">{{ store.doseHelper }}</span>
              </template>
            </zw-form-item>
            <zw-form-item label="疗程" required :error="store.errors.course_days" :hint="store.lastDateText">
              <v-text-field v-model="store.model.courseDays" type="number" suffix="天" @update:model-value="onCourseChange" />
            </zw-form-item>
          </div>

          <zw-notice v-if="store.overDose" type="warning" class="mb-3">
            <b>超过推荐剂量。</b>请填写超量说明后再提交。
          </zw-notice>
          <zw-form-item v-if="store.overDose" label="超量说明" required :error="store.errors.over_dose_note" hint="最多 100 字">
            <v-text-field v-model="store.model.overDoseNote" maxlength="100" counter />
          </zw-form-item>

          <zw-notice v-if="methodMismatch" type="warning" class="mb-3">
            <b>该药品不适用于该批次的畜种。</b>请填写用药说明后再提交。
          </zw-notice>
          <zw-form-item v-if="methodMismatch" label="用药说明" required :error="store.errors.usage_note" hint="最多 100 字">
            <v-text-field v-model="store.model.usageNote" maxlength="100" counter />
          </zw-form-item>

          <zw-form-item label="实际用量" required :error="store.errors.used_qty" :hint="store.usedHelper">
            <v-text-field
              v-model="store.model.usedQty"
              type="number"
              :suffix="store.selectedDrug?.stock_unit || ''"
              @blur="onUsedQtyBlur"
            />
          </zw-form-item>

          <div class="zw-form-2">
            <zw-form-item label="首次用药日期" required :error="store.errors.first_date">
              <HealthDateField :model-value="store.model.firstDate" :max="todayISO()" @update:model-value="onFirstDateChange" />
            </zw-form-item>
            <zw-form-item v-if="!isStaff" label="实际执行人" :hint="`登记人：${store.detail?.creator_name || auth.displayName}`">
              <v-select
                :model-value="store.model.executorId || undefined"
                :items="executorItems"
                item-title="title"
                item-value="value"
                placeholder="选择本场成员"
                @update:model-value="onExecutorSelect"
              />
            </zw-form-item>
          </div>

          <zw-form-item label="用药原因" required :error="store.errors.reason" hint="最多 100 字">
            <v-text-field v-model="store.model.reason" maxlength="100" counter />
          </zw-form-item>

          <zw-form-item v-if="linkableItems.length" label="关联异常上报" optional hint="选择本批次 / 栏舍的异常上报，诊断详情里会显示这条用药">
            <v-select
              :model-value="store.model.reportId || undefined"
              :items="linkableItems"
              item-title="title"
              item-value="value"
              placeholder="不关联"
              clearable
              prepend-inner-icon="link"
              @update:model-value="onReportSelect"
            />
          </zw-form-item>
        </div>
      </template>
      <template #foot>
        <div class="m-foot-date">
          <span>提交后可出栏日期</span>
          <b class="num">{{ store.releaseText || '—' }}</b>
        </div>
        <v-btn variant="outlined" @click="store.formOpen = false">取消</v-btn>
        <v-btn color="primary" prepend-icon="medication" :loading="store.submitting" :disabled="store.formLoading" @click="submit">
          {{ isEdit ? '保存修改' : '登记用药' }}
        </v-btn>
      </template>
    </zw-drawer>

    <!-- 删除用药记录 -->
    <zw-dialog v-model="store.deleteOpen" title="删除这条用药记录？" icon="edit_note" tone="info" width="480">
      <div v-if="deleteImpactLoading" class="m-dialog-loading"><v-progress-circular indeterminate size="20" width="2" /> 正在判断删除影响</div>
      <template v-else>
        <div class="m-dialog-sub">{{ store.deleteSubject }}</div>
        <p class="zw-muted">{{ store.deleteBody }}</p>
        <div v-if="store.deleteImpact?.impact === 'ADVANCE'" class="m-dialog-chg">
          <span class="zw-faint">可出栏日期</span>
          <span class="old num">{{ changeBefore }}</span>
          <zw-icon name="arrow_forward" :size="16" />
          <span class="new num">{{ changeAfter }}</span>
        </div>
        <zw-form-item v-if="store.deleteErrors.reason" label="提示" :error="store.deleteErrors.reason" />
      </template>
      <template #actions>
        <v-btn variant="outlined" @click="store.deleteOpen = false">取消</v-btn>
        <v-btn v-if="store.deleteMode === 'direct'" color="error" :loading="store.deleteSubmitting" :disabled="deleteImpactLoading" @click="confirmDelete">删除</v-btn>
        <v-btn v-else color="primary" :disabled="deleteImpactLoading" @click="goDeleteRequest">提交修改申请</v-btn>
      </template>
    </zw-dialog>

    <!-- 员工通知场长 -->
    <zw-dialog v-model="store.notifyOpen" title="需要所有者或场长处理" icon="lock" tone="info" width="440">
      <div class="m-dialog-sub">{{ store.deleteSubject }}</div>
      <p class="zw-muted">{{ store.notifyText }}你可以通知{{ store.notifyName }}处理。</p>
      <template #actions>
        <v-btn variant="outlined" @click="store.notifyOpen = false">知道了</v-btn>
        <v-btn color="primary" prepend-icon="send" :loading="store.notifySubmitting" @click="submitNotify">通知{{ store.notifyName }}</v-btn>
      </template>
    </zw-dialog>

    <!-- 修改记录 -->
    <zw-drawer v-model="store.logOpen" title="用药记录修改记录" :sub="store.logTargetTitle" width="560">
      <v-skeleton-loader v-if="store.logLoading" type="list-item-two-line@3" />
      <zw-empty v-else-if="store.logError" icon="cloud_off" tone="error" title="修改记录加载失败" :desc="store.logError" />
      <zw-timeline v-else-if="store.logItems.length" :items="store.logItems" />
      <zw-empty v-else icon="history" title="还没有修改记录" desc="修改、删除用药记录后会显示在这里。" />
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.m-help { color: var(--text-3); cursor: help; }
.m-batch { display: flex; align-items: center; gap: 12px; }
.m-batch-text { font-size: 14px; color: var(--text-2); white-space: nowrap; }
.m-top { display: flex; align-items: stretch; gap: 16px; margin-bottom: 16px; }
.m-wd { width: 420px; flex-shrink: 0; box-sizing: border-box; padding: 20px 24px; border-radius: var(--radius-sm); background: var(--fill-2); color: var(--text-1); }
.m-wd--in_withdrawal { background: var(--warning-container); color: var(--on-warning-container); }
.m-wd--ended { background: var(--success-container); color: var(--on-success-container); }
.m-wd-st { display: flex; align-items: center; gap: 6px; font-size: 14px; line-height: 22px; font-weight: 600; }
.m-wd-date { margin-top: 8px; font-size: 28px; line-height: 36px; font-weight: 600; }
.m-wd-bar { margin-top: 16px; }
.m-wd-foot { margin-top: 6px; font-size: 12px; line-height: 20px; }
.m-wd-note { margin-top: 14px; padding-top: 12px; border-top: 1px solid rgba(0, 0, 0, .1); font-size: 12px; line-height: 20px; }
.m-calc { flex: 1 1 0; min-width: 0; }
.m-calc-table :deep(td) { white-space: nowrap; }
.m-calc-empty { padding: 0 20px 16px; font-size: 13px; color: var(--text-3); }
.m-calc-note { font-size: 12px; line-height: 20px; color: var(--text-3); }
.m-drug { display: flex; align-items: center; gap: 12px; padding: 4px 0; white-space: nowrap; }
.m-nowrap { white-space: nowrap; }
.m-ellipsis { display: inline-block; max-width: 160px; overflow: hidden; text-overflow: ellipsis; vertical-align: bottom; white-space: nowrap; }
.m-ops { display: inline-flex; gap: 12px; white-space: nowrap; }
.m-ops a.danger { color: var(--error); }
.m-table :deep(td) { white-space: nowrap; }
.m-warn-ico :deep(.v-field__append-inner > .v-icon) { color: var(--warning) !important; }
.m-warning-text { color: var(--warning); }
.m-foot-date { margin-right: auto; display: flex; flex-direction: column; }
.m-foot-date span { font-size: 12px; line-height: 18px; color: var(--text-3); }
.m-foot-date b { font-size: 16px; line-height: 24px; font-weight: 600; }
.m-dialog-sub { margin-bottom: 8px; font-weight: 600; }
.m-dialog-loading { display: flex; align-items: center; gap: 8px; color: var(--text-3); font-size: 13px; }
.m-dialog-chg { display: flex; align-items: center; gap: 8px; margin: 12px 0 16px; padding: 10px 14px; border-radius: var(--radius-sm); background: var(--fill-1); }
.m-dialog-chg .old { color: var(--text-3); text-decoration: line-through; }
.m-dialog-chg .new { font-weight: 600; color: var(--warning); }
.m-link { display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 10px; border: 1px solid var(--control-border); border-radius: var(--radius-sm); font-size: 14px; }
.m-link .zw-mso { color: var(--primary); }
</style>
