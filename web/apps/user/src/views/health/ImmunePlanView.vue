<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { DataTableHeader } from 'vuetify'
import { formatNumber } from '@zmt/shared'
import type { ImmunePlan, ImmunePlanOption, PreEntryItem } from '@/api/health'
import { useImmunePlanStore, type ImmuneTab } from '@/stores/health/immune'
import { useFarmReload } from '@/composables/useFarmReload'
import { queryId, useRouteAction } from '@/composables/useRouteAction'
import { useRole } from '@/composables/useRole'
import { useAuthStore } from '@/stores/auth'
import { dateText, dateTimeText, monthDay, todayISO } from '@/stores/health/shared'
import HealthDateField from './components/HealthDateField.vue'

const store = useImmunePlanStore()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { isStaff } = useRole()

const TAB_LABELS: Record<ImmuneTab, string> = {
  ALL: '全部',
  PENDING: '待接种',
  MISSED: '漏免',
  POSTPONED: '已延期',
  DONE: '已接种',
  CANCELLED: '已取消'
}

const headers: DataTableHeader[] = [
  { title: '疫苗 · 免次', key: 'vaccine', sortable: false },
  { title: '计划日期', key: 'date', sortable: false, width: 168 },
  { title: '届时日龄', key: 'age', sortable: false, width: 100, align: 'end' },
  { title: '负责人', key: 'who', sortable: false, width: 110 },
  { title: '说明', key: 'note', sortable: false, width: 230 },
  { title: '状态', key: 'status', sortable: false, width: 100 },
  { title: '操作', key: 'op', sortable: false, width: 210, align: 'end' }
]

const tab = computed<string>({
  get: () => store.query.status,
  set: (value: string) => store.setTab((value || 'ALL') as ImmuneTab)
})

const desc = computed(() => {
  if (store.overview?.has_template === false) return '品种没有免疫模板时，由所有者或场长手工添加项目'
  return '按品种的免疫程序模板和批次日龄生成；计划日期过后 7 天仍未登记标为“漏免”'
})

const registerSub = computed(() => {
  if (store.registerMode === 'view' || store.registerMode === 'edit') {
    return `${store.registerRecord?.pen_name || ''} · ${store.registerRecord?.batch_no || ''}`
  }
  const plan = store.registerForm?.plan
  if (plan) return `${plan.pen_name || ''} · ${plan.batch_no || ''} · 存栏 ${formatNumber(plan.current_count || 0)} ${store.countUnit}`
  return store.currentBatch ? `${store.currentBatch.pens?.[0]?.pen_name || ''} · ${store.currentBatch.batch_no || ''} · 存栏 ${formatNumber(store.currentBatch.current_count || 0)} ${store.countUnit}` : ''
})

const registerTitle = computed(() => {
  if (store.registerMode === 'view') return '接种记录'
  return '登记接种'
})

function planOptionItem(item: ImmunePlanOption): { title: string; value: number } {
  return {
    title: `${item.pen_name || ''} · ${item.vaccine_name || ''}${item.immune_round ? ` ${item.immune_round}` : ''} · ${item.status_text || ''}`,
    value: Number(item.plan_id || 0)
  }
}
/** 从计划表 / 待办带入的计划可能不在“今天可登记”列表里：把已加载的这条也放进选项，下拉不再显示原始 ID */
const planOptionItems = computed(() => {
  const items = store.registerPlanOptions.map(planOptionItem)
  const current = store.registerForm?.plan
  if (current?.plan_id && !items.some((item) => item.value === Number(current.plan_id))) items.unshift(planOptionItem(current))
  return items
})
const selectedPlanValue = computed(() => {
  const planId = Number(store.registerModel.planId || 0)
  return planId && planOptionItems.value.some((item) => item.value === planId) ? planId : undefined
})

const vaccineItems = computed(() => store.registerVaccines.map((item) => ({
  title: `${item.name || ''}${item.no_stock ? '（暂无库存）' : ''}`,
  value: Number(item.drug_id || 0)
})))

const methodItems = computed(() => {
  const items = store.registerMethods
    .filter((item) => item.code && item.name)
    .map((item) => ({ title: item.name || '', value: item.code || '' }))
  const current = store.registerModel.methodCode
  if (current && !items.some((item) => item.value === current)) {
    const name = store.registerRecord?.method_name || store.registerForm?.plan?.status_text
    items.push({ title: name || current, value: current })
  }
  return items
})

const addMethodItems = computed(() => store.addMethods
  .filter((item) => item.code && item.name)
  .map((item) => ({ title: item.name || '', value: item.code || '' })))

const selectableLots = computed(() => store.registerLotItems.filter((item) => !item.disabled))
const disabledLots = computed(() => store.registerLotItems.filter((item) => item.disabled))

const registerPlanHint = computed(() => {
  const form = store.registerForm
  if (!form?.plan) return ''
  return `${form.plan.batch_no || ''} · 存栏 ${formatNumber(form.plan.current_count || 0)} ${store.countUnit} · 计划日期 ${form.plan.plan_date_text || monthDay(form.plan.plan_date)}`
})

const registerDeductionText = computed(() => {
  const lot = selectableLots.value.find((item) => item.value === Number(store.registerModel.lotId))
  const need = store.registerNeedQty
  if (!lot || !need) return ''
  const unit = store.registerForm?.dose_unit?.split('/')[0]?.trim() || '头份'
  return `登记后自动扣减批号 ${lot.title} 共 ${formatNumber(need, need % 1 === 0 ? 0 : 2)} ${unit}，对应待办自动完成。`
})

const registerLotHint = computed(() => {
  const lot = store.registerLotItems.find((item) => item.value === Number(store.registerModel.lotId))
  if (!lot) return '已默认选择有效期最早的批号；过期和停用的批号只能由所有者或场长登记报废'
  const priority = store.registerModel.lotId === Number(store.registerLots?.default_lot_id)
  return `${lot.subtitle}${priority ? ' · 已默认选择有效期最早的批号' : ''}`
})

const registerIsView = computed(() => store.registerMode === 'view')
const registerIsEdit = computed(() => store.registerMode === 'edit')
const registerQuantityUnit = computed(() => store.countUnit)

const canPostponeDialog = computed(() => !!store.postponeTarget)
const batchOptions = computed(() => store.batchItems)

function onBatchChange(value: unknown): void {
  void store.selectBatch(Number(value || 0))
}

function hasAction(plan: ImmunePlan, action: string): boolean {
  return !!plan.actions?.includes(action)
}

function canRegister(plan: ImmunePlan): boolean {
  return !store.batchEnded && hasAction(plan, 'REGISTER')
}

function canBackfill(plan: ImmunePlan): boolean {
  return !store.batchEnded && hasAction(plan, 'BACKFILL')
}

function canMenu(plan: ImmunePlan): boolean {
  return !store.batchEnded && (hasAction(plan, 'EDIT') || hasAction(plan, 'DELETE'))
}

function hasRowAction(plan: ImmunePlan): boolean {
  return canRegister(plan) || canBackfill(plan) || !!plan.record_id || (!store.batchEnded && hasAction(plan, 'POSTPONE')) || canMenu(plan)
}

function statusType(plan: ImmunePlan): string {
  if (plan.status === 'PENDING') return 'primary'
  if (plan.status === 'MISSED') return 'error'
  if (plan.status === 'POSTPONED') return 'warning'
  return 'neutral'
}

function statusIcon(plan: ImmunePlan): string {
  if (plan.status === 'DONE') return 'check'
  if (plan.status === 'MISSED') return 'error'
  if (plan.status === 'POSTPONED') return 'event_repeat'
  return ''
}

function tileClass(plan: ImmunePlan): string {
  if (plan.status === 'PENDING') return 'zw-ico-tile--primary'
  if (plan.status === 'MISSED') return 'zw-ico-tile--error'
  if (plan.status === 'POSTPONED') return 'zw-ico-tile--warning'
  return ''
}

function dateCell(plan: ImmunePlan): string {
  if (plan.status === 'CANCELLED') return `原计划 ${monthDay(plan.plan_date)}`
  if (plan.status === 'POSTPONED') return `${monthDay(plan.postpone_from_date)} → ${monthDay(plan.plan_date)}`
  return `${monthDay(plan.plan_date)}${plan.is_today ? ' · 今天' : ''}`
}

function noteText(plan: ImmunePlan): string {
  if (plan.status === 'DONE') {
    const parts = [monthDay(plan.record_date), plan.record_user_name, plan.record_quantity ? `${formatNumber(plan.record_quantity)} ${store.countUnit}` : '']
    return parts.filter(Boolean).join(' · ')
  }
  if (plan.status === 'MISSED') {
    return isStaff.value ? '超过 7 天未登记，需所有者或场长补录' : '超过 7 天未登记'
  }
  if (plan.status === 'POSTPONED') {
    return `${plan.postponed_by_name || '—'}延期`
  }
  if (plan.status === 'CANCELLED') {
    if (plan.cancel_type === 'BATCH_ENDED') return '批次结束自动取消'
    if (plan.cancelled_by_name && plan.cancelled_by_name === auth.displayName) return '你已删除'
    return `${plan.cancelled_by_name || '—'}已删除`
  }
  return plan.remind_text || '前 1 天和当天各提醒一次'
}

function noteTone(plan: ImmunePlan): string {
  if (plan.status === 'MISSED') return 'h-note--error'
  if (plan.status === 'CANCELLED') return 'zw-faint'
  return ''
}

function statusText(plan: ImmunePlan): string {
  return plan.status_text || TAB_LABELS[(plan.status || 'PENDING') as ImmuneTab] || ''
}

function onPlanSelect(value: unknown): void {
  void store.selectPlan(Number(value || 0))
}

function onPlanModeChange(value: unknown): void {
  store.addModel.planMode = value === 'BY_DATE' ? 'BY_DATE' : 'BY_DAY_AGE'
}

function onVaccineSelect(value: unknown): void {
  store.selectVaccine(Number(value || 0))
}

function onAddVaccineSelect(value: unknown): void {
  const id = Number(value || 0)
  store.addModel.vaccineId = id
  const vaccine = store.addVaccines.find((item) => Number(item.drug_id) === id)
  if (vaccine?.default_method_code) store.addModel.methodCode = vaccine.default_method_code
}

function onExecutorChange(value: unknown): void {
  store.registerModel.executorId = value === null || value === undefined || value === '' ? null : Number(value)
}

function goChangeRequest(): void {
  void router.push({ name: 'WU-U01', query: store.openChangeRequest() })
}

function responsibleText(plan: ImmunePlan): string {
  const text = plan.responsible_text || ''
  return text.replace(/负责$/, '') || '—'
}

function openRegisterFor(plan: ImmunePlan): void {
  void store.openRegisterFor(Number(plan.id), hasAction(plan, 'BACKFILL'))
}

function openBackfill(plan: ImmunePlan): void {
  void store.openRegisterFor(Number(plan.id), true)
}

function openRecord(plan: ImmunePlan): void {
  if (!plan.record_id) return
  void store.openRecord(Number(plan.record_id))
}

function resetFilter(): void {
  store.setTab('ALL')
}

function preEntryBusy(item: PreEntryItem): boolean {
  return store.actionPlanId === Number(item.plan_id)
}

function confirmPreEntry(item: PreEntryItem): void {
  void store.confirmPreEntry(item)
}

function joinPreEntry(item: PreEntryItem): void {
  void store.joinPreEntry(item)
}

function submitRegister(): void {
  void store.submitRegister()
}

function submitAdd(continueAdding: boolean): void {
  void store.submitAdd(continueAdding)
}

function submitPostpone(): void {
  void store.submitPostpone()
}

function submitDelete(): void {
  void store.confirmDelete()
}

function openAddEdit(plan: ImmunePlan): void {
  store.openAddEdit(plan)
}

/** create：带 planItemId（待办去登记）时登记该免疫项目，否则打开“登记接种”。 */
const routeAction = useRouteAction(async (action, query) => {
  if (action !== 'create') return
  const batchId = queryId(query, 'batchId', 'batch_id')
  if (batchId && batchId !== store.batchId) await store.selectBatch(batchId)
  const planId = queryId(query, 'planItemId', 'plan_item_id', 'planId')
  if (planId) {
    const plan = store.list.find((item) => Number(item.id) === planId)
    store.openRegisterFor(planId, plan ? hasAction(plan, 'BACKFILL') : false)
    return
  }
  store.openRegister()
})

onMounted(async () => {
  store.staffView = isStaff.value
  const preferred = queryId(route.query, 'batchId', 'batch_id')
  await store.load(preferred || undefined)
  await routeAction.start()
})

useFarmReload(async () => {
  store.staffView = isStaff.value
  await store.reload()
})
</script>

<template>
  <zw-shell
    side="user"
    active="WU-D01"
    :crumbs="isStaff ? ['防疫健康', '免疫接种'] : ['防疫健康', '免疫计划']"
  >
    <zw-page-head title="免疫计划" :desc="desc">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="history" @click="store.openLogs()">查看修改记录</v-btn>
        <v-btn v-if="store.canManage" variant="outlined" prepend-icon="add" @click="store.openAdd()">添加项目</v-btn>
        <v-btn color="primary" prepend-icon="vaccines" @click="store.openRegister()">登记接种</v-btn>
      </template>
    </zw-page-head>

    <zw-notice v-if="store.batchEnded" type="warning" class="mb-4">批次已结束，未执行的计划已自动取消。</zw-notice>

    <div class="zw-toolbar">
      <div class="zw-toolbar-l h-batch">
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
        <span class="h-batch-text">{{ store.batchOverview }}</span>
      </div>
      <div class="zw-toolbar-r">
        <zw-seg v-model="tab" :items="store.tabSegments" />
      </div>
    </div>

    <zw-notice v-if="store.status === 'forbidden'" type="warning" title="无权查看" class="mb-4">
      {{ store.error || '你没有这个批次或栏舍的查看权限' }}
    </zw-notice>

    <zw-card v-else-if="store.status === 'loading' || store.batchLoading">
      <v-skeleton-loader type="table-heading, table-row@6" />
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="免疫计划加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.refresh()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="!store.batches.length">
      <zw-empty icon="vaccines" title="还没有在养批次" desc="批次入栏后，系统按品种的免疫程序模板生成免疫计划。" />
    </zw-card>

    <template v-else>
      <zw-card v-if="store.showPreEntry" title="入栏前项目" :sub="`${store.preEntryCount} 项待确认`" flush class="mb-4">
        <div class="h-pre-tip">
          批次入栏时已超过这些项目的日龄，请确认入栏前是否已接种。“确认已接种”不扣库存；“加入计划”生成一条计划日期为今天的待接种项目。
        </div>
        <div v-for="item in store.preEntryItems" :key="item.plan_id" class="h-pre">
          <span class="zw-ico-tile"><zw-icon name="vaccines" :size="18" /></span>
          <div class="h-pre-main">
            <div class="zw-cell-main">{{ item.vaccine_name }}</div>
            <div class="zw-cell-sub">{{ item.immune_round || '—' }} · 模板 {{ item.template_age_day || 0 }} 日龄</div>
          </div>
          <v-btn variant="outlined" :disabled="preEntryBusy(item)" @click="joinPreEntry(item)">加入计划</v-btn>
          <v-btn variant="tonal" color="primary" :loading="preEntryBusy(item)" @click="confirmPreEntry(item)">确认已接种</v-btn>
        </div>
      </zw-card>

      <zw-card flush title="免疫计划" :sub="store.summarySub">
        <template v-if="store.status === 'empty' || store.status === 'filtered_empty'">
          <zw-empty
            :icon="store.status === 'filtered_empty' ? 'search_off' : 'vaccines'"
            :title="store.emptyTitle()"
            :desc="
              store.overview?.has_template === false
                ? (isStaff
                  ? '该品种没有免疫模板，请联系所有者或场长添加免疫项目。'
                  : '平台暂无该品种的免疫程序模板，系统没有自动生成计划。添加后按计划日期提醒负责员工、所有者和场长。')
                : store.emptyDesc()
            "
          >
            <v-btn
              v-if="store.canManage && store.overview?.has_template === false && store.status === 'empty'"
              color="primary"
              prepend-icon="add"
              @click="store.openAdd()"
            >添加免疫项目</v-btn>
            <v-btn v-else-if="store.status === 'filtered_empty'" variant="text" @click="resetFilter">查看全部</v-btn>
          </zw-empty>
        </template>
        <template v-else>
          <v-data-table :headers="headers" :items="store.list" item-value="id" hide-default-footer class="h-table">
            <template #item.vaccine="{ item }">
              <div class="h-vac">
                <span class="zw-ico-tile" :class="tileClass(item)"><zw-icon name="vaccines" :size="18" /></span>
                <span class="zw-cell-main" :class="{ 'h-strike': item.status === 'CANCELLED' }">
                  {{ item.vaccine_name }}<template v-if="item.immune_round"> · {{ item.immune_round }}</template>
                </span>
              </div>
            </template>
            <template #item.date="{ item }">
              <span class="h-nowrap num">{{ dateCell(item) }}</span>
            </template>
            <template #item.age="{ item }"><span class="num">{{ item.age_at_plan ?? '—' }} 日龄</span></template>
            <template #item.who="{ item }"><span class="h-nowrap">{{ responsibleText(item) }}</span></template>
            <template #item.note="{ item }">
              <span :class="noteTone(item)">{{ noteText(item) }}</span>
            </template>
            <template #item.status="{ item }">
              <zw-tag :type="statusType(item)" :icon="statusIcon(item) || undefined">{{ statusText(item) }}</zw-tag>
            </template>
            <template #item.op="{ item }">
              <span class="h-ops zw-cell-actions">
                <a v-if="canRegister(item)" @click="openRegisterFor(item)">登记接种</a>
                <a v-else-if="canBackfill(item)" @click="openBackfill(item)">补录</a>
                <a v-if="item.record_id" @click="openRecord(item)">查看记录</a>
                <a v-if="!store.batchEnded && hasAction(item, 'POSTPONE')" @click="store.openPostpone(item)">调整日期</a>
                <v-menu v-if="canMenu(item)" location="bottom end">
                  <template #activator="{ props: menuProps }">
                    <button v-bind="menuProps" class="zw-icon-btn" type="button" aria-label="更多操作">
                      <zw-icon name="more_horiz" :size="18" />
                    </button>
                  </template>
                  <v-list density="compact">
                    <v-list-item v-if="hasAction(item, 'EDIT')" @click="openAddEdit(item)">修改剂量和方式</v-list-item>
                    <v-divider />
                    <v-list-item v-if="hasAction(item, 'DELETE')" class="text-error" @click="store.askDelete(item)">删除项目</v-list-item>
                  </v-list>
                </v-menu>
                <span v-if="!hasRowAction(item)" class="zw-faint">—</span>
              </span>
            </template>
          </v-data-table>
          <div class="zw-table-foot zw-table-foot--in">
            <span class="zw-faint body-s">
              {{ isStaff ? '漏免需所有者或场长补录；调整日期、添加项目由所有者或场长操作' : '状态由后端按北京时间计算；改期、添加、删除都会记入修改记录' }}
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

    <!-- 登记接种 / 查看接种记录 -->
    <zw-drawer v-model="store.registerOpen" :title="registerTitle" :sub="registerSub" width="560">
      <v-skeleton-loader v-if="store.registerLoading" type="article, paragraph@4" />
      <template v-else>
        <zw-notice v-if="store.registerError" type="warning" class="mb-4">
          {{ store.registerError }}
          <template v-if="isStaff">请联系所有者或场长先登记入库。</template>
        </zw-notice>

        <template v-if="registerIsView">
          <zw-desc :cols="2">
            <zw-desc-item label="免疫计划">
              {{ store.registerRecord?.vaccine_name }}<template v-if="store.registerRecord?.immune_round"> · {{ store.registerRecord.immune_round }}</template>
            </zw-desc-item>
            <zw-desc-item label="接种日期">{{ dateText(store.registerRecord?.vaccinate_date) }}</zw-desc-item>
            <zw-desc-item label="库存批号">{{ store.registerRecord?.lot_no || '—' }}</zw-desc-item>
            <zw-desc-item label="接种方式">{{ store.registerRecord?.method_name || store.registerRecord?.method_code || '—' }}</zw-desc-item>
            <zw-desc-item label="剂量">{{ store.registerRecord?.dose ?? '—' }} {{ store.registerRecord?.dose_unit || '' }}</zw-desc-item>
            <zw-desc-item label="接种数量">{{ store.registerRecord?.quantity !== undefined && store.registerRecord?.quantity !== null ? `${formatNumber(store.registerRecord.quantity)} ${store.countUnit}` : '—' }}</zw-desc-item>
            <zw-desc-item label="登记人">{{ store.registerRecord?.creator_name || '—' }}</zw-desc-item>
            <zw-desc-item label="实际执行人">{{ store.registerRecord?.executor_name || '—' }}</zw-desc-item>
            <zw-desc-item label="登记时间">{{ store.registerRecord?.created_at ? dateTimeText(store.registerRecord.created_at) : '—' }}</zw-desc-item>
            <zw-desc-item label="补录">{{ store.registerRecord?.backfill ? '是' : '否' }}</zw-desc-item>
          </zw-desc>
          <section class="h-sec">
            <div class="zw-section-title">修改记录</div>
            <v-skeleton-loader v-if="store.recordLogLoading" type="list-item-two-line@2" />
            <zw-timeline v-else-if="store.recordLogItems.length" :items="store.recordLogItems" />
            <div v-else class="zw-faint body-s">还没有修改记录</div>
          </section>
        </template>

        <template v-else>
          <div class="zw-form">
            <zw-form-item
              v-if="!registerIsEdit"
              label="免疫计划"
              required
              :error="store.registerErrors.plan_id"
              :hint="registerPlanHint || '从计划表或待办进入时已预填'"
            >
              <v-select
                :model-value="selectedPlanValue"
                :items="planOptionItems"
                item-title="title"
                item-value="value"
                placeholder="选择待接种、已延期或漏免的计划"
                :loading="store.registerPlanOptionsLoading"
                @update:model-value="onPlanSelect"
              />
              <div v-if="!store.registerPlanOptionsLoading && !planOptionItems.length" class="h-hint">
                今天没有待接种的免疫计划，可以稍后再来。
              </div>
            </zw-form-item>

            <div class="zw-form-2">
              <zw-form-item label="疫苗" required :error="store.registerErrors.drug_id">
                <v-select
                  :model-value="store.registerModel.vaccineId || undefined"
                  :items="vaccineItems"
                  item-title="title"
                  item-value="value"
                  @update:model-value="onVaccineSelect"
                />
              </zw-form-item>
              <zw-form-item label="接种日期" required :error="store.registerErrors.vaccinate_date">
                <HealthDateField
                  v-model="store.registerModel.vaccinateDate"
                  :min="store.registerForm?.date_min || ''"
                  :max="store.registerForm?.date_max || todayISO()"
                />
              </zw-form-item>
            </div>

            <zw-form-item
              label="库存批号"
              required
              :error="store.registerErrors.drug_lot_id"
              :hint="registerLotHint"
            >
              <div class="h-lots">
                <div
                  v-for="lot in selectableLots"
                  :key="lot.value"
                  class="h-lot"
                  :class="{ on: Number(store.registerModel.lotId) === lot.value }"
                  @click="store.registerModel.lotId = lot.value"
                >
                  <zw-icon :name="Number(store.registerModel.lotId) === lot.value ? 'radio_button_checked' : 'radio_button_unchecked'" :size="20" />
                  <span class="h-lot-main">
                    <span class="h-lot-no num">{{ lot.title }}</span>
                    <zw-tag v-if="lot.value === Number(store.registerLots?.default_lot_id)" size="small" type="primary">先到期先出</zw-tag>
                  </span>
                  <span class="h-lot-sub num">{{ lot.subtitle }}</span>
                </div>
                <template v-if="disabledLots.length">
                  <div class="h-lot-group">不可选择</div>
                  <div v-for="lot in disabledLots" :key="lot.value" class="h-lot dis">
                    <zw-icon name="radio_button_unchecked" :size="20" />
                    <span class="h-lot-main"><span class="h-lot-no num">{{ lot.title }}</span></span>
                    <span class="h-lot-sub num">{{ lot.subtitle }}</span>
                  </div>
                </template>
                <div v-if="store.registerLotsLoading" class="h-lot-loading"><v-progress-circular indeterminate size="18" width="2" /> 正在读取批号</div>
              </div>
            </zw-form-item>

            <div class="zw-form-2">
              <zw-form-item label="剂量" required :error="store.registerErrors.dose" :hint="`模板剂量 ${store.registerForm?.template_dose ?? 1} ${store.registerForm?.dose_unit || '头份 / 头'}`">
                <v-text-field v-model="store.registerModel.dose" type="number" :suffix="store.registerForm?.dose_unit || '头份 / 头'" @blur="store.loadRegisterLots()" />
              </zw-form-item>
              <zw-form-item label="接种数量" required :error="store.registerErrors.quantity" :hint="`不能超过存栏 ${formatNumber(store.registerCurrentCount)} ${registerQuantityUnit}`">
                <v-text-field v-model="store.registerModel.quantity" type="number" :suffix="registerQuantityUnit" @blur="store.loadRegisterLots()" />
              </zw-form-item>
            </div>

            <zw-form-item label="接种方式" required :error="store.registerErrors.method_code">
              <v-radio-group v-model="store.registerModel.methodCode" inline hide-details>
                <v-radio v-for="method in methodItems" :key="method.value" :label="method.title" :value="method.value" />
              </v-radio-group>
            </zw-form-item>

            <zw-form-item v-if="!isStaff" label="实际执行人" :hint="`登记人：${store.registerForm?.creator_name || auth.displayName}`">
              <v-select
                :model-value="store.registerModel.executorId || undefined"
                :items="store.registerExecutors.map((member) => ({ title: member.display_name || member.name || '', value: Number(member.user_id || 0) }))"
                item-title="title"
                item-value="value"
                placeholder="选择本场成员"
                @update:model-value="onExecutorChange"
              />
            </zw-form-item>

            <zw-form-item v-if="store.registerForm?.backfill" label="补录说明" optional>
              <div class="h-readonly">漏免补录：接种日期可选计划日期至今天。</div>
            </zw-form-item>
          </div>

          <zw-notice type="info" icon="inventory_2" class="mt-4">
            {{ registerDeductionText || '登记后按“剂量 × 接种数量”自动扣减批号库存，对应待办自动完成。' }}
          </zw-notice>
        </template>
      </template>
      <template #foot>
        <span class="zw-foot-note h-foot-note">
          <zw-icon name="person" :size="14" />
          {{ registerIsView ? `登记人：${store.registerRecord?.creator_name || '—'}` : `登记人：${isStaff ? '你（系统自动记录）' : `${store.registerForm?.creator_name || auth.displayName}（系统自动记录）`}` }}
        </span>
        <template v-if="registerIsView">
          <v-btn
            v-if="store.registerRecord?.actions?.includes('CHANGE_REQUEST')"
            variant="outlined"
            @click="$router.push({ name: 'WU-U01', query: store.openChangeRequest() })"
          >提交修改申请</v-btn>
          <v-btn
            v-else-if="store.registerRecord?.actions?.includes('EDIT')"
            variant="outlined"
            prepend-icon="edit"
            @click="store.openRecordEdit(Number(store.registerRecord?.id || 0))"
          >修改记录</v-btn>
          <span v-else-if="store.registerRecord?.edit_hint" class="zw-faint body-s">{{ store.registerRecord.edit_hint }}</span>
          <v-btn variant="outlined" @click="store.registerOpen = false">关闭</v-btn>
        </template>
        <template v-else>
          <v-btn variant="outlined" @click="store.registerOpen = false">取消</v-btn>
          <v-btn color="primary" prepend-icon="vaccines" :loading="store.registerSubmitting" :disabled="!!store.registerError" @click="submitRegister">
            {{ registerIsEdit ? '保存修改' : '登记接种' }}
          </v-btn>
        </template>
      </template>
    </zw-drawer>

    <!-- 添加 / 修改免疫项目 -->
    <zw-drawer v-model="store.addOpen" :title="store.addTitle" :sub="store.addSub" width="560">
      <zw-notice v-if="store.addMode === 'create' && store.overview?.has_template === false" type="info" class="mb-4">
        <b>该品种没有免疫模板，请手工添加。</b>平台暂无该品种的免疫程序模板，系统没有自动生成计划。
      </zw-notice>
      <div class="zw-form">
        <zw-form-item v-if="store.addMode === 'create'" label="疫苗" required :error="store.addErrors.drug_id" hint="从本场疫苗档案中选择；没有库存的疫苗名称后注“暂无库存”">
          <v-select
            :model-value="store.addModel.vaccineId || undefined"
            :items="store.addVaccineItems"
            item-title="title"
            item-value="value"
            :loading="store.addLoading"
            @update:model-value="onAddVaccineSelect"
          />
          <div v-if="!store.addLoading && !store.addVaccineItems.length" class="h-hint">本场还没有疫苗档案。请先在兽药与疫苗库存中新增疫苗。</div>
        </zw-form-item>
        <zw-form-item v-else label="疫苗">
          <div class="h-readonly">{{ store.addEditingName }}</div>
        </zw-form-item>

        <zw-form-item v-if="store.addMode === 'create'" label="计划时间" required>
          <zw-seg
            :model-value="store.addModel.planMode"
            :items="[{ label: '按日龄', value: 'BY_DAY_AGE' }, { label: '按日期', value: 'BY_DATE' }]"
            @update:model-value="onPlanModeChange"
          />
        </zw-form-item>

        <div v-if="store.addMode === 'create'" class="zw-form-2">
          <zw-form-item v-if="store.addModel.planMode === 'BY_DAY_AGE'" label="接种日龄" required :error="store.addErrors.plan_age_day">
            <v-text-field v-model="store.addModel.planAgeDay" type="number" suffix="日龄" />
          </zw-form-item>
          <zw-form-item v-else label="计划日期" required :error="store.addErrors.plan_date">
            <HealthDateField v-model="store.addModel.planDate" :min="todayISO()" />
          </zw-form-item>
          <zw-form-item :label="store.addModel.planMode === 'BY_DAY_AGE' ? '计划日期' : '届时日龄'" :hint="store.addModel.planMode === 'BY_DAY_AGE' ? '按日龄自动计算' : '按日期自动计算'">
            <div class="h-readonly">{{ store.addModel.planMode === 'BY_DAY_AGE' ? store.addPlanDateText : store.addAgeAtDateText }}</div>
          </zw-form-item>
        </div>

        <zw-form-item label="剂量" required :error="store.addErrors.dose" hint="按疫苗说明书填写">
          <v-text-field v-model="store.addModel.dose" type="number" :suffix="store.addDoseUnit" class="zw-w-240" />
        </zw-form-item>

        <zw-form-item label="接种方式" required :error="store.addErrors.method_code">
          <v-radio-group v-model="store.addModel.methodCode" inline hide-details>
            <v-radio v-for="method in addMethodItems" :key="method.value" :label="method.title" :value="method.value" />
          </v-radio-group>
        </zw-form-item>
      </div>

      <div class="h-remind">
        <zw-icon name="notifications" :size="16" />
        计划日期前 1 天和当天提醒该栏舍的负责员工、所有者和场长。
      </div>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="store.addOpen = false">取消</v-btn>
        <template v-if="store.addMode === 'create'">
          <v-btn variant="tonal" color="primary" :loading="store.addSubmitting" @click="submitAdd(true)">保存并继续添加</v-btn>
          <v-btn color="primary" prepend-icon="add" :loading="store.addSubmitting" @click="submitAdd(false)">添加项目</v-btn>
        </template>
        <v-btn v-else color="primary" :loading="store.addSubmitting" @click="submitAdd(false)">保存修改</v-btn>
      </template>
    </zw-drawer>

    <!-- 调整计划日期 -->
    <zw-dialog v-model="store.postponeOpen" title="调整计划日期" width="440">
      <p v-if="canPostponeDialog" class="h-dialog-text">{{ store.postponeRemindText }}</p>
      <div class="zw-form">
        <zw-form-item label="新日期" required :error="store.postponeErrors.new_date" :hint="store.postponeHint">
          <HealthDateField v-model="store.postponeModel.newDate" :min="todayISO()" />
        </zw-form-item>
        <zw-form-item label="延期原因" required :error="store.postponeErrors.reason" hint="必填，会记入修改记录；最多 50 字">
          <v-text-field v-model="store.postponeModel.reason" maxlength="50" counter />
        </zw-form-item>
      </div>
      <div class="h-lock"><zw-icon name="lock" :size="14" />只有所有者和场长可以调整计划日期</div>
      <template #actions>
        <v-btn variant="outlined" @click="store.postponeOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.postponeSubmitting" @click="submitPostpone">确认延期</v-btn>
      </template>
    </zw-dialog>

    <!-- 删除项目 -->
    <zw-dialog v-model="store.deleteOpen" title="删除这个免疫项目？" icon="delete" tone="warning" width="440">
      <div class="h-dialog-sub">
        {{ store.deleteTarget?.vaccine_name }}<template v-if="store.deleteTarget?.immune_round"> · {{ store.deleteTarget.immune_round }}</template>
        · {{ monthDay(store.deleteTarget?.plan_date) }}
      </div>
      <p class="zw-muted">删除后项目标为已取消，不再提醒，相关待办同时取消。</p>
      <template #actions>
        <v-btn variant="outlined" @click="store.deleteOpen = false">取消</v-btn>
        <v-btn color="error" :loading="store.deleteSubmitting" @click="submitDelete">删除</v-btn>
      </template>
    </zw-dialog>

    <!-- 修改记录 -->
    <zw-drawer v-model="store.logOpen" title="免疫计划修改记录" :sub="store.currentBatch ? store.currentBatch.batch_no : ''" width="560">
      <v-skeleton-loader v-if="store.logLoading" type="list-item-two-line@3" />
      <zw-empty v-else-if="store.logError" icon="cloud_off" tone="error" title="修改记录加载失败" :desc="store.logError" />
      <zw-timeline v-else-if="store.logItems.length" :items="store.logItems" />
      <zw-empty v-else icon="history" title="还没有修改记录" desc="改期、添加、删除项目后会显示在这里。" />
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.h-batch { display: flex; align-items: center; gap: 12px; }
.h-batch-text { font-size: 14px; color: var(--text-2); white-space: nowrap; }
.h-pre-tip { padding: 12px 20px; font-size: 12px; line-height: 20px; color: var(--text-3); border-bottom: 1px solid var(--line-1); background: var(--fill-1); }
.h-pre { display: flex; align-items: center; gap: 12px; padding: 12px 20px; }
.h-pre + .h-pre { border-top: 1px solid var(--line-1); }
.h-pre-main { flex: 1; min-width: 0; }
.h-vac { display: flex; align-items: center; gap: 12px; padding: 4px 0; white-space: nowrap; }
.h-nowrap { white-space: nowrap; }
.h-ops { display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; }
.h-strike { text-decoration: line-through; color: var(--text-3) !important; }
.h-note--error { color: var(--error); }
.h-table :deep(td) { white-space: nowrap; }
.h-lots { border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.h-lot { display: flex; align-items: center; gap: 10px; padding: 10px 14px; cursor: pointer; }
.h-lot + .h-lot { border-top: 1px solid var(--line-1); }
.h-lot.on { background: var(--primary-subtle); }
.h-lot > .zw-mso { color: var(--control-border); }
.h-lot.on > .zw-mso { color: var(--primary); }
.h-lot-main { display: flex; align-items: center; gap: 8px; min-width: 0; }
.h-lot-no { font-weight: 500; }
.h-lot-sub { margin-left: auto; font-size: 12px; color: var(--text-2); white-space: nowrap; }
.h-lot.dis { cursor: not-allowed; }
.h-lot.dis .h-lot-no, .h-lot.dis .h-lot-sub, .h-lot.dis > .zw-mso { color: var(--text-disabled); }
.h-lot-group { padding: 6px 14px; font-size: 12px; line-height: 18px; color: var(--text-3); background: var(--fill-1); border-top: 1px solid var(--line-1); }
.h-lot-loading { display: flex; align-items: center; gap: 8px; padding: 10px 14px; font-size: 12px; color: var(--text-3); }
.h-readonly { display: flex; align-items: center; min-height: 32px; padding: 0 12px; border-radius: var(--radius-sm); background: var(--fill-1); color: var(--text-1); }
.h-remind { display: flex; align-items: flex-start; gap: 6px; margin-top: 16px; font-size: 12px; line-height: 20px; color: var(--text-3); }
.h-remind .zw-mso { margin-top: 2px; }
.h-lock { display: flex; align-items: center; gap: 6px; margin-top: 12px; font-size: 12px; color: var(--text-3); }
.h-dialog-text { margin-bottom: 16px; color: var(--text-2); }
.h-dialog-sub { margin-bottom: 8px; font-weight: 600; }
.h-hint { margin-top: 4px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.h-sec { margin-top: 20px; }
.h-foot-note { display: inline-flex; align-items: center; gap: 6px; }
</style>
