<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatNumber, formatTime } from '@zmt/shared'
import type { DataTableHeader } from 'vuetify'
import { useFeedTaskStore } from '@/stores/feeding/feedTask'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { queryId, useRouteAction } from '@/composables/useRouteAction'
import { useUiStore } from '@/stores/ui'
import { useRole } from '@/composables/useRole'
import { minutesText } from '@/stores/feeding/shared'
import type { FeedTaskItem } from '@/api/feeding'

const store = useFeedTaskStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const { isStaff } = useRole()

const canManage = computed(() => !isStaff.value)
const headers: DataTableHeader[] = [
  { title: '餐次', key: 'meal', sortable: false, width: 96 },
  { title: '栏舍', key: 'pen', sortable: false, width: 120 },
  { title: '批次', key: 'batch', sortable: false, width: 120 },
  { title: '饲料', key: 'feed', sortable: false },
  { title: '计划量', key: 'plan', sortable: false, width: 100, align: 'end' },
  { title: '实投', key: 'actual', sortable: false, width: 100, align: 'end' },
  { title: '执行人', key: 'executor', sortable: false, width: 96 },
  { title: '状态', key: 'status', sortable: false, width: 210 },
  { title: '操作', key: 'actions', sortable: false, width: 90, align: 'end' }
]

type TaskRow = FeedTaskItem & { first: boolean }
const rows = computed<TaskRow[]>(() => store.list.map((item, index, list) => ({
  ...item,
  first: index === 0 || list[index - 1]?.meal_no !== item.meal_no
})))

const desc = computed(() => {
  const summary = store.summary
  const pens = Number(summary?.pen_count || 0)
  const farmName = farm.farmName || '当前养殖场'
  const roleText = isStaff.value ? '你负责的栏舍' : `${farmName} ${pens} 个栏舍`
  return `${store.dateText} · ${roleText} · 投喂时间前 15 分钟提醒${isStaff.value ? '你' : '负责员工'}`
})
const planValue = computed(() => formatNumber(store.summary?.plan_total_qty || 0))
const doneValue = computed(() => `${formatNumber(store.summary?.done_count || 0)} / ${formatNumber(store.summary?.task_total || 0)}`)
const overdueValue = computed(() => `${formatNumber(store.summary?.overdue_count || 0)} 餐`)
const taskSub = computed(() => `${formatNumber(store.summary?.task_total || 0)} 餐 · ${isStaff.value ? '只显示你负责的栏舍' : '按餐次排列'}`)

function onStatus(value: unknown): void {
  store.setStatus(String(value ?? ''))
}

function onPen(value: unknown): void {
  store.setPen(Number(value || 0))
}

function rowProps({ item, index }: { item: TaskRow; index: number }): Record<string, unknown> {
  return { class: item.first && index > 0 ? 'g01-first' : '' }
}

function actionText(item: FeedTaskItem): string {
  if (item.action === 'REGISTER') return '去登记'
  if (item.action === 'EDIT') return '修改'
  return '查看'
}

function statusNote(item: FeedTaskItem): string {
  if (item.status === 'OVERDUE') return `超过投喂时间 ${minutesText(item.overdue_minutes)}`
  if (item.status === 'DONE' || item.status === 'MANUAL_DONE') {
    const who = item.executor_name || item.assignee_name || '员工'
    return `${who}${item.registered_at ? ` ${formatTime(item.registered_at)}` : ''} 登记`
  }
  return item.status_text || '待投喂'
}

function openTask(item: FeedTaskItem): void {
  void store.openTask(item)
}

function goPlan(tab: 'plan' | 'exec'): void {
  void router.push({ name: 'WU-G02', query: { tab } })
}

function goInbound(feedId?: number): void {
  void router.push({ name: 'WU-M01', query: feedId ? { action: 'inbound', feedId: String(feedId) } : { action: 'inbound' } })
}

function fieldError(field: string): string {
  return store.errors[field] || ''
}

function submitChange(): void {
  const record = store.record
  if (!record) return
  void router.push({
    name: 'WU-U01',
    query: {
      biz_type: 'FEED_RECORD',
      biz_id: String(record.record_id || ''),
      action: 'create'
    }
  })
}

function closeDrawer(): void {
  store.close()
}

function askVoid(): void {
  store.voidConfirm = true
}

function cancelVoid(): void {
  store.voidConfirm = false
}

/** 待办 / 快捷登记进入：带 taskId 打开该餐次；只带 action=create 时打开第一条待登记的餐次。 */
async function openFromRoute(create: boolean): Promise<void> {
  const taskId = queryId(route.query, 'taskId', 'task_id')
  const item = taskId ? store.list.find((task) => Number(task.task_id) === taskId) : undefined
  if (item) {
    await store.openTask(item)
    return
  }
  if (!create) return
  const next = store.list.find((task) => task.action === 'REGISTER')
  if (next) await store.openTask(next)
  else useUiStore().toast(taskId ? '这餐投喂任务不在今天的列表里，可能已登记或已调整' : '今天没有待登记的投喂任务', 'info')
}

const routeAction = useRouteAction(async (action) => {
  if (action === 'create' || action === 'register') await openFromRoute(true)
})

onMounted(async () => {
  await store.load()
  if (!route.query.action) await openFromRoute(false)
  await routeAction.start()
})

useFarmReload(async () => {
  store.reset()
  await store.load()
})
</script>

<template>
  <zw-shell side="user" active="WU-G01" :crumbs="isStaff ? ['我的栏舍', '饲喂任务'] : ['生产管理', '饲喂管理', '饲喂任务']">
    <zw-page-head title="饲喂任务" :desc="desc">
      <template v-if="canManage" #actions>
        <v-btn variant="outlined" prepend-icon="tune" @click="goPlan('plan')">饲喂方案</v-btn>
        <v-btn variant="outlined" prepend-icon="bar_chart" @click="goPlan('exec')">执行情况</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'forbidden'">
      <zw-card>
        <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '你没有查看饲喂任务的权限'">
          <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
        </zw-empty>
      </zw-card>
    </div>

    <div v-else-if="store.status === 'loading'">
      <div class="zw-grid-3 mb-4">
        <v-skeleton-loader v-for="item in 3" :key="item" type="article" />
      </div>
      <v-skeleton-loader type="table-row@6" />
    </div>

    <template v-else>
      <div class="zw-grid-3 mb-4">
        <zw-stat label="今日计划" icon="rice_bowl" :value="planValue" unit="千克" :foot="store.summaryFoot.plan" tone="primary" />
        <zw-stat label="已投喂" icon="task_alt" :value="doneValue" unit="餐" :foot="store.summaryFoot.done" />
        <zw-stat label="已逾期" icon="schedule" :value="overdueValue" :tone="Number(store.summary?.overdue_count || 0) > 0 ? 'error' : undefined" :foot="store.summaryFoot.overdue" />
      </div>

      <zw-notice v-for="item in store.planShortages" :key="item.feed_id" type="warning" icon="inventory_2" class="mb-3">
        <b>{{ item.feed_name }}库存不足。</b>结存 {{ formatNumber(item.stock_qty || 0) }} 千克，今天还需 {{ formatNumber(item.today_need_qty || 0) }} 千克，{{ item.restock_before_meal_name ? `${item.restock_before_meal_name}前需要补货` : '需要补货' }}。
        <template v-if="canManage" #action><a @click="goInbound(item.feed_id)">去入库</a></template>
      </zw-notice>
      <div v-if="store.moreShortageCount" class="g01-more-shortage">另有 {{ store.moreShortageCount }} 种饲料库存不足</div>

      <zw-card title="今日任务" :sub="taskSub" flush>
        <template #extra>
          <zw-seg size="small" :model-value="store.query.status" :items="store.segItems" @update:model-value="onStatus" />
          <v-select :model-value="store.query.penId" :items="store.penItems" item-title="title" item-value="value" class="zw-w-140" @update:model-value="onPen" />
        </template>

        <zw-empty v-if="store.status === 'error'" icon="cloud_off" tone="error" title="饲喂任务加载失败" :desc="store.error">
          <v-btn color="primary" variant="outlined" @click="store.load(true)">重新加载</v-btn>
        </zw-empty>

        <zw-empty v-else-if="store.status === 'empty'" icon="rice_bowl" :title="store.emptyText.title" :desc="isStaff ? '你负责的栏舍今天没有饲喂任务。' : store.emptyText.desc">
          <v-btn v-if="canManage" color="primary" @click="goPlan('plan')">设置饲喂方案</v-btn>
        </zw-empty>

        <zw-empty v-else-if="store.status === 'filtered_empty'" icon="search_off" title="没有符合条件的饲喂任务" desc="换一个状态或栏舍试试。">
          <v-btn variant="text" @click="store.resetFilters()">清除筛选</v-btn>
        </zw-empty>

        <template v-else>
          <v-data-table :headers="headers" :items="rows" item-value="task_id" hide-default-footer :row-props="rowProps" class="g01-table">
            <template #item.meal="{ item }">
              <div v-if="item.first" class="g01-meal">{{ item.meal_name }}<span class="num">{{ item.feed_time }}</span></div>
            </template>
            <template #item.pen="{ item }">{{ item.pen_name || '—' }}</template>
            <template #item.batch="{ item }"><span class="num">{{ item.batch_no || '—' }}</span></template>
            <template #item.feed="{ item }">
              <span class="g01-feed">{{ item.feed_name || '—' }}<zw-tag v-if="item.low_stock && item.status !== 'DONE'" type="warning" size="small">库存不足</zw-tag></span>
            </template>
            <template #item.plan="{ item }"><span class="num">{{ formatNumber(item.plan_qty || 0) }} 千克</span></template>
            <template #item.actual="{ item }"><span class="num" :class="{ 'zw-faint': !item.actual_qty }">{{ item.actual_qty ? `${formatNumber(item.actual_qty, 1)} 千克` : '—' }}</span></template>
            <template #item.executor="{ item }">
              <span :class="{ 'zw-faint': !item.executor_name && !item.assignee_name }">{{ item.executor_name || item.assignee_name || '无负责员工' }}</span>
            </template>
            <template #item.status="{ item }">
              <div class="g01-st">
                <zw-tag :type="store.taskStatus(item).type" :icon="store.taskStatus(item).icon || undefined">{{ store.taskStatus(item).text }}</zw-tag>
                <small :class="{ err: item.status === 'OVERDUE' }">{{ statusNote(item) }}</small>
              </div>
            </template>
            <template #item.actions="{ item }">
              <span class="zw-cell-actions"><a @click="openTask(item)">{{ actionText(item) }}</a></span>
            </template>
          </v-data-table>
          <div class="zw-table-foot zw-table-foot--in">
            <span class="zw-faint body-s">{{ isStaff ? '只显示你负责栏舍的任务；确认后不能再修改' : '确认投喂后自动生成饲喂领用出库，待办自动完成' }}</span>
            <zw-pager :total="store.total" :page="store.query.page" :size="store.query.size" @update:page="store.setPage" @update:size="store.setSize" />
          </div>
        </template>
      </zw-card>
    </template>

    <!-- 登记 / 修改 / 查看投喂 -->
    <zw-drawer :model-value="store.drawer" :title="store.title" :sub="`${farm.farmName || '当前养殖场'} · ${store.drawerMode === 'view' ? '投喂记录' : '确认后自动生成饲喂领用出库'}`" :width="560" @update:model-value="(value: unknown) => { if (!value) closeDrawer() }">
      <div v-if="store.formLoading || store.recordLoading">
        <v-skeleton-loader type="image, article, table-row@4" />
      </div>
      <zw-empty v-else-if="store.formError" icon="cloud_off" tone="error" title="投喂任务加载失败" :desc="store.formError">
        <v-btn variant="outlined" @click="closeDrawer">关闭</v-btn>
      </zw-empty>
      <template v-else-if="store.form">
        <div class="g01-task">
          <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="rice_bowl" :size="18" /></span>
          <div>
            <div class="zw-cell-main">{{ store.form.pen_name }} · {{ store.form.meal_name }} {{ store.form.feed_time }}</div>
            <div class="zw-cell-sub"><span class="num">{{ store.form.batch_no }}</span> · {{ store.form.breed_name }} · {{ store.form.day_age }} 日龄 · 存栏 {{ formatNumber(store.form.head_count || store.form.batch_stock || 0) }} 头</div>
          </div>
        </div>

        <template v-if="store.drawerMode === 'view'">
          <zw-desc :cols="1" bordered :label-width="100">
            <zw-desc-item label="饲料">{{ store.record?.feed?.feed_name || '—' }}<span class="zw-faint"> · 结存 {{ formatNumber(store.record?.feed?.stock_qty || 0) }} 千克</span></zw-desc-item>
            <zw-desc-item label="计划量"><span class="num">{{ formatNumber(store.record?.plan_qty || store.form.plan_qty || 0) }} 千克</span></zw-desc-item>
            <zw-desc-item label="实际投喂量"><span class="num">{{ formatNumber(store.record?.actual_qty || 0, 1) }} 千克</span></zw-desc-item>
            <zw-desc-item label="偏差"><span class="num">{{ store.record?.deviation_ratio !== undefined && store.record?.deviation_ratio !== null ? `${store.record.deviation_ratio > 0 ? '+' : ''}${(store.record.deviation_ratio * 100).toFixed(1)}%` : '—' }}</span>
              <zw-tag v-if="store.record?.abnormal_type && store.record.abnormal_type !== 'NONE'" type="warning" size="small">投喂异常</zw-tag>
            </zw-desc-item>
            <zw-desc-item label="执行人">{{ store.record?.executor?.name || '—' }}<span v-if="store.record?.is_proxy" class="zw-faint">（代登记）</span></zw-desc-item>
            <zw-desc-item label="登记人">{{ store.record?.recorder?.name || '—' }}</zw-desc-item>
            <zw-desc-item label="登记时间">{{ store.record?.registered_at || '—' }}</zw-desc-item>
            <zw-desc-item label="备注">{{ store.record?.remark || '—' }}</zw-desc-item>
          </zw-desc>
          <zw-notice v-if="isStaff" type="info" class="mt-4">这条投喂已确认，不能再修改。需要更正时请联系所有者或场长。</zw-notice>
          <zw-notice v-else-if="store.record?.readonly_reason === 'NOT_TODAY' || store.record?.view_mode === 'READONLY'" type="info" class="mt-4">这条记录不是今天登记的，修改需要提交修改申请，由管理员审核。</zw-notice>
        </template>

        <template v-else>
          <div class="zw-form-2">
            <zw-form-item label="饲料" required style="grid-column: 1 / -1" :error="fieldError('feed_id')" :hint="store.selectedFeed ? `结存 ${formatNumber(store.selectedFeed.stock_qty || 0)} 千克${store.selectedFeed.remaining_days ? ` · 剩余可用 ${store.selectedFeed.remaining_days} 天` : ' · 暂无消耗'}` : ''">
              <v-select v-model="store.formState.feedId" :items="store.formFeedOptions" item-title="title" item-value="value" :error="!!fieldError('feed_id')">
                <template #item="{ props: itemProps, item }">
                  <v-list-item v-bind="itemProps" :subtitle="item.raw.hint">
                    <template #append><zw-tag v-if="item.raw.low" type="warning" size="small">库存不足</zw-tag></template>
                  </v-list-item>
                </template>
                <template #append-inner><zw-tag v-if="store.selectedFeed?.low_stock" type="warning" size="small">库存不足</zw-tag></template>
              </v-select>
            </zw-form-item>
            <zw-form-item label="计划量">
              <div class="g01-plan num">{{ formatNumber(store.form.plan_qty || 0) }} 千克<span>按饲喂方案</span></div>
            </zw-form-item>
            <zw-form-item label="实际投喂量" required :error="fieldError('actual_qty')">
              <v-text-field v-model.number="store.formState.actualQty" type="number" min="0" step="0.1" suffix="千克" :error="!!fieldError('actual_qty')" />
            </zw-form-item>
          </div>
          <div v-if="store.formulaText" class="g01-calc num" :class="{ 'g01-calc--err': !!store.stockError }">{{ store.stockError || store.formulaText }}</div>

          <zw-notice v-if="store.abnormal" type="warning" class="mt-4">{{ store.abnormalText }}</zw-notice>

          <div class="zw-form mt-4">
            <zw-form-item v-if="canManage" label="实际执行人" required :error="fieldError('executor_id')" :hint="store.proxyText">
              <v-select v-model="store.formState.executorId" :items="store.executorItems" item-title="title" item-value="value" :error="!!fieldError('executor_id')" />
            </zw-form-item>
            <div v-else class="g01-registrar">登记人：{{ store.form.current_user?.name || '当前用户' }}</div>
            <zw-form-item label="备注" optional :error="fieldError('remark')" hint="最多 100 字">
              <v-text-field v-model="store.formState.remark" maxlength="100" counter="100" :error="!!fieldError('remark')" />
            </zw-form-item>
          </div>

          <zw-notice v-if="store.exceedsStock" type="error" class="mt-4">
            <b>库存不足，不能确认投喂。</b>本次需要 {{ formatNumber(store.formState.actualQty || 0, 1) }} 千克，结存 {{ formatNumber(store.selectedFeed?.stock_qty || 0) }} 千克，请先登记入库。
            <template v-if="canManage" #action><a @click="goInbound(store.formState.feedId)">去入库</a></template>
          </zw-notice>
          <zw-notice v-else type="info" icon="inventory_2" class="mt-4">
            确认后自动生成 {{ formatNumber(store.formState.actualQty || 0, 1) }} 千克饲喂领用出库，待办自动完成。{{ canManage ? '你可以修改或作废当天的记录，出库同步更正。' : '确认后不能再修改，需要更正时请联系所有者或场长。' }}
          </zw-notice>
        </template>
      </template>

      <template v-if="store.drawerMode === 'view'" #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="closeDrawer">关闭</v-btn>
        <v-btn v-if="store.record?.can_request_change" variant="outlined" color="primary" @click="submitChange">提交修改申请</v-btn>
        <span v-if="store.record?.can_request_change" class="zw-faint body-s">需审核</span>
      </template>
      <template v-else-if="store.voidConfirm" #foot>
        <div class="g01-void">
          <div><b>作废这条投喂记录？</b><span>关联的 {{ formatNumber(store.record?.actual_qty || 0, 1) }} 千克饲喂领用出库会同步作废，作废后不能恢复。</span></div>
          <v-btn variant="outlined" size="small" @click="cancelVoid">取消</v-btn>
          <v-btn color="error" size="small" :loading="store.voidSubmitting" @click="store.voidRecord()">作废</v-btn>
        </div>
      </template>
      <template v-else #foot>
        <span v-if="store.drawerMode === 'edit' && store.record?.can_void" class="g01-void-link"><a @click="askVoid">作废这条记录</a></span>
        <span v-else class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="closeDrawer">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" :disabled="store.exceedsStock" @click="store.submit()">{{ store.drawerMode === 'edit' ? '保存修改' : '确认投喂' }}</v-btn>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.g01-more-shortage { margin: -4px 0 12px; font-size: 12px; color: var(--text-3); }
.g01-meal { font-weight: 600; color: var(--text-1); white-space: nowrap; }
.g01-meal span { display: block; font-weight: 400; font-size: 12px; color: var(--text-3); }
.g01-feed { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
.g01-st { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: 6px 0; }
.g01-st small { font-size: 12px; line-height: 18px; color: var(--text-3); white-space: nowrap; }
.g01-st small.err { color: var(--error); }
.g01-task { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: var(--radius-sm); background: var(--fill-1); border: 1px solid var(--line-1); margin-bottom: 20px; }
.g01-task > div { flex: 1; min-width: 0; }
.g01-calc { margin-top: 6px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.g01-calc--err { color: var(--error); }
.g01-plan { display: flex; align-items: center; justify-content: space-between; min-height: 32px; padding: 0 11px; border-radius: var(--radius-sm); background: var(--fill-2); }
.g01-plan span { color: var(--text-2); }
.g01-registrar { min-height: 32px; font-size: 13px; color: var(--text-2); }
.g01-void { display: flex; align-items: center; gap: 12px; width: 100%; padding: 8px 12px; border-radius: var(--radius-sm); background: var(--error-container); color: var(--on-error-container); }
.g01-void > div { flex: 1; display: flex; flex-direction: column; font-size: 12px; line-height: 18px; }
.g01-void-link a { color: var(--error); font-size: 13px; cursor: pointer; }
.g01-table :deep(td) { white-space: nowrap; }
.g01-table :deep(tr.g01-first > td) { border-top: 1px solid var(--line-2); }
</style>
