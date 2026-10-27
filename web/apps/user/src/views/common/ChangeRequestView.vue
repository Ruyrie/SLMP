<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getChangeRequestForm, type ChangeRequestFormVO, type ChangeRequestVO } from '@/api/change'
import { displayChangeField as displayField, useChangeRequestStore, VOID_FIELD, DELETE_FIELD } from '@/stores/common/changeRequest'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { formatMonthDayTime } from '@/views/account/accountForm'

const route = useRoute()
const router = useRouter()
const store = useChangeRequestStore()
const farm = useFarmStore()
const ui = useUiStore()
const originalOpen = ref(false)
const originalLoading = ref(false)
const originalForm = ref<ChangeRequestFormVO | null>(null)
const originalError = ref('')
const originalRows = computed(() => {
  const rows = [...(originalForm.value?.record_card || [])]
  for (const field of originalForm.value?.fields || []) {
    if (!rows.some((row) => row.label === field.field_name)) {
      rows.push({ label: field.field_name, value: [displayField(field), field.value_unit].filter(Boolean).join(' ') })
    }
  }
  return rows
})

const BIZ_TYPES = [
  { title: '入栏记录', value: 'BATCH_ENTRY' },
  { title: '转栏记录', value: 'BATCH_TRANSFER' },
  { title: '死淘记录', value: 'BATCH_DEATH' },
  { title: '产仔记录', value: 'FARROWING' },
  { title: '孵化记录', value: 'INCUBATION' },
  { title: '种畜死亡淘汰', value: 'BREEDING_EXIT' },
  { title: '投喂记录', value: 'FEED_RECORD' },
  { title: '称重记录', value: 'WEIGH_RECORD' },
  { title: '巡检记录', value: 'INSPECTION' },
  { title: '接种记录', value: 'IMMUNE_RECORD' },
  { title: '用药记录', value: 'MEDICATION_RECORD' },
  { title: '消毒记录', value: 'DISINFECT_RECORD' },
  { title: '入库单', value: 'INBOUND' },
  { title: '盘点单', value: 'STOCKTAKE' },
  { title: '报损单', value: 'LOSS' },
  { title: '出库单', value: 'OUTBOUND' },
  { title: '出栏单', value: 'SALE_ORDER' }
] as const

const BIZ_TYPE_LABELS: Record<string, string> = Object.fromEntries(
  BIZ_TYPES.map((item) => [item.value, item.title])
)
const BIZ_ICONS: Record<string, string> = {
  BATCH_ENTRY: 'add_box',
  BATCH_TRANSFER: 'swap_horiz',
  BATCH_DEATH: 'heart_minus',
  FARROWING: 'pets',
  INCUBATION: 'egg',
  BREEDING_EXIT: 'pets',
  FEED_RECORD: 'rice_bowl',
  WEIGH_RECORD: 'scale',
  INSPECTION: 'fact_check',
  IMMUNE_RECORD: 'vaccines',
  MEDICATION_RECORD: 'medication',
  DISINFECT_RECORD: 'sanitizer',
  INBOUND: 'inventory_2',
  STOCKTAKE: 'inventory',
  LOSS: 'remove_shopping_cart',
  OUTBOUND: 'local_shipping',
  SALE_ORDER: 'local_shipping'
}

const headers = [
  { title: '申请（记录 · 修改项）', key: 'title', sortable: false },
  { title: '养殖场', key: 'farm', sortable: false, width: 112 },
  { title: '修改前 → 修改后', key: 'change', sortable: false, width: 224 },
  { title: '状态', key: 'state', sortable: false, width: 104 },
  { title: '审核进展', key: 'progress', sortable: false, width: 272 },
  { title: '操作', key: 'op', sortable: false, width: 128, align: 'end' as const }
]

const farmOptions = computed(() => [
  { title: '全部养殖场', value: 0 },
  ...farm.farms
    .filter((item) => item.role !== 'STAFF')
    .map((item) => ({ title: item.name, value: item.farm_id }))
])

const tabItems = computed(() => [
  { value: 'ALL', label: '全部', count: store.tabCounts.ALL ?? store.total },
  { value: 'PENDING', label: '待审核', count: store.tabCounts.PENDING ?? 0 },
  { value: 'APPROVED', label: '已通过', count: store.tabCounts.APPROVED ?? 0 },
  { value: 'REJECTED', label: '已驳回', count: store.tabCounts.REJECTED ?? 0 }
])

const isInitialEmpty = computed(() => store.status === 'empty' && !store.hasFilters)
const showTabs = computed(() => !isInitialEmpty.value)
const detail = computed(() => store.detail)
const detailTitle = computed(() => {
  const d = detail.value
  if (!d) return '修改申请'
  const action = d.change_type === 'VOID' ? '作废' : d.field_name || '修改'
  return `${d.biz_type_text || BIZ_TYPE_LABELS[d.biz_type] || '修改申请'} · ${action}`
})
const detailSub = computed(() => {
  const d = detail.value
  if (!d) return ''
  return [d.farm_name, d.record_locator].filter(Boolean).join(' · ')
})
const detailSteps = computed(() => {
  const d = detail.value
  if (!d) return []
  const submitted = {
    title: '已提交',
    desc: formatMonthDayTime(d.submitted_at),
    status: 'done'
  }
  if (d.status === 'PENDING') {
    return [
      submitted,
      { title: '平台审核', desc: '审核期间原记录照常有效', status: 'current' },
      { title: '审核结果', status: 'wait' }
    ]
  }
  if (d.status === 'REJECTED') {
    return [
      submitted,
      { title: '平台审核', status: 'done' },
      { title: '已驳回', desc: formatMonthDayTime(d.reviewed_at), status: 'error' }
    ]
  }
  if (d.status === 'APPROVED') {
    return [
      submitted,
      { title: '平台审核', status: 'done' },
      { title: '已通过', desc: formatMonthDayTime(d.reviewed_at), status: 'done' }
    ]
  }
  return [
    submitted,
    { title: '平台审核', status: 'done' },
    { title: '已撤销', desc: formatMonthDayTime(d.reviewed_at), status: 'error' }
  ]
})
const detailNotice = computed(() => {
  const d = detail.value
  if (!d) return { type: 'info' as const, icon: 'info', title: '', text: '' }
  if (d.status === 'PENDING') {
    return {
      type: 'info' as const,
      icon: 'info',
      title: '',
      text: '审核期间原记录照常有效，审核通过后才会修改并重新计算。'
    }
  }
  if (d.status === 'REJECTED') {
    return {
      type: 'error' as const,
      icon: 'block',
      title: '驳回原因',
      text: d.reject_reason || d.review_comment || '管理员未填写驳回原因。'
    }
  }
  if (d.status === 'APPROVED') {
    return {
      type: 'success' as const,
      icon: 'check_circle',
      title: '',
      text: d.recalc_summary
        ? `已通过，系统已按申请修改。${d.recalc_summary}`
        : '已通过，系统已按申请修改并重新计算相关数据。'
    }
  }
  return { type: 'info' as const, icon: 'undo', title: '', text: '这条申请已撤销，原记录保持不变。' }
})
const compareRows = computed(() => {
  const d = detail.value
  if (!d) return []
  const rows: Array<{ label: string; before: string; after: string }> = []
  if (d.change_type === 'VOID' || d.change_type === 'DELETE') {
    rows.push({ label: '记录状态', before: '有效', after: d.change_type === 'DELETE' ? '已删除' : '已作废' })
  }
  for (const item of store.detailForm?.record_card || []) {
    if (!item.label) continue
    const changed = d.change_type !== 'VOID' && item.label === d.field_name
    rows.push({
      label: item.label,
      before: changed ? d.before_display || '—' : item.value || '—',
      after: changed ? d.after_display || '—' : item.value || '—'
    })
  }
  if (
    d.change_type !== 'VOID' &&
    d.field_name &&
    !rows.some((row) => row.label === d.field_name)
  ) {
    rows.push({
      label: d.field_name,
      before: d.before_display || '—',
      after: d.after_display || '—'
    })
  }
  if (!rows.length) {
    rows.push({
      label: '记录',
      before: d.record_locator || '—',
      after: d.record_locator || '—'
    })
  }
  return rows
})
const recalcText = computed(() => {
  const d = detail.value
  if (!d) return ''
  if (d.status === 'APPROVED') return d.recalc_summary || '已重新计算相关数据'
  if (d.status === 'REJECTED') return '没有重新计算，原记录保持不变'
  if (d.status === 'PENDING') return '审核通过后重新计算'
  return '没有重新计算，原记录保持不变'
})
const fieldOptions = computed(() => {
  const list = (store.form?.fields || []).map((field) => ({
    title: field.field_name || field.field_code,
    value: field.field_code
  }))
  if (store.form?.void_allowed) {
    list.push({ title: '作废这条记录', value: VOID_FIELD })
  }
  if (store.form?.delete_allowed) {
    list.push({ title: '删除这条记录', value: DELETE_FIELD })
  }
  return list
})
const selectedFieldModel = computed({
  get: () => store.selectedField,
  set: (value: unknown) => store.selectField(String(value ?? ''))
})
const newValueModel = computed({
  get: () => store.newValue,
  set: (value: unknown) => store.setNewValue(String(value ?? ''))
})
const reasonModel = computed({
  get: () => store.reason,
  set: (value: unknown) => store.setReason(String(value ?? ''))
})
const currentHint = computed(() => store.form?.field_hints?.[store.selectedField] || '')
const reasonCount = computed(() => Array.from(store.reason).length)

onBeforeUnmount(() => {
  store.closeDetail()
  if (store.submitOpen) store.discard()
})

onMounted(async () => {
  const status = String(route.query.status || 'ALL').toUpperCase()
  store.query.status = ['ALL', 'PENDING', 'APPROVED', 'REJECTED'].includes(status) ? status : 'ALL'
  store.query.farmId = Number(route.query.farm_id || route.query.farmId || 0) || 0
  store.query.keyword = String(route.query.keyword || '')
  store.query.page = Math.max(1, Number(route.query.page || 1) || 1)

  const bizType = String(route.query.biz_type || '')
  const bizId = Number(route.query.biz_id || 0)
  const isCreate = route.query.action === 'create' && !!bizType && bizId > 0
  store.query.bizType = isCreate
    ? String(route.query.filter_biz_type || '')
    : String(route.query.biz_type || '')
  if (isCreate) {
    await store.openCreate(bizType, bizId)
    stripCreateQuery()
  } else if (route.query.request_id || route.query.id) {
    const id = Number(route.query.request_id || route.query.id || 0)
    if (id > 0) await store.openDetail(id)
  }
  await store.fetchList()
})

function syncQuery(): void {
  const query: Record<string, string> = {}
  if (store.query.status !== 'ALL') query.status = store.query.status
  if (store.query.farmId) query.farm_id = String(store.query.farmId)
  if (store.query.bizType) query.biz_type = store.query.bizType
  if (store.query.keyword) query.keyword = store.query.keyword
  if (store.query.page > 1) query.page = String(store.query.page)
  void router.replace({ query })
}

/** 从其他模块带 action=create 进入后清掉入口参数，但不触发路由重渲染，避免抽屉被卸载 */
function stripCreateQuery(): void {
  const query: Record<string, string> = {}
  if (store.query.status !== 'ALL') query.status = store.query.status
  if (store.query.farmId) query.farm_id = String(store.query.farmId)
  if (store.query.keyword) query.keyword = store.query.keyword
  if (store.query.page > 1) query.page = String(store.query.page)
  const url = router.resolve({ path: route.path, query }).fullPath
  window.history.replaceState(window.history.state, '', url)
}

function onTabChange(value: unknown): void {
  store.query.status = String(value ?? 'ALL')
  store.query.page = 1
  store.closeDetail()
  syncQuery()
  void store.fetchList()
}

function applyFilters(): void {
  store.query.page = 1
  store.closeDetail()
  syncQuery()
  void store.fetchList()
}

function resetFilters(): void {
  store.resetFilters()
  store.closeDetail()
  syncQuery()
}

function changePage(page: number): void {
  if (page === store.query.page) return
  store.query.page = page
  syncQuery()
  void store.fetchList()
}

function changeSize(size: number): void {
  if (size === store.query.size) return
  store.query.size = size
  store.query.page = 1
  syncQuery()
  void store.fetchList()
}

function rowProps(data: { item: unknown }): Record<string, unknown> {
  const item = data.item as ChangeRequestVO
  return {
    class: store.detailOpen && store.detail?.id === item.id ? 'zw-row-on' : undefined,
    onClick: () => {
      void store.openDetail(item.id)
    }
  }
}

function statusTagType(status: string): string {
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'error'
  if (status === 'APPROVED') return 'neutral'
  return 'neutral'
}

function statusText(item: ChangeRequestVO): string {
  return item.status_text || (item.status === 'PENDING' ? '待审核' : item.status === 'APPROVED' ? '已通过' : item.status === 'REJECTED' ? '已驳回' : '已撤销')
}

function progressTime(item: ChangeRequestVO): string {
  if (item.status === 'PENDING') return formatMonthDayTime(item.submitted_at, ' 提交')
  if (item.status === 'REJECTED') return formatMonthDayTime(item.reviewed_at, ' 驳回')
  if (item.status === 'APPROVED') return formatMonthDayTime(item.reviewed_at, ' 通过')
  return formatMonthDayTime(item.reviewed_at, ' 撤销')
}

function progressNote(item: ChangeRequestVO): string {
  if (item.status === 'PENDING') return '审核期间原记录照常有效'
  if (item.status === 'REJECTED') return `驳回原因：${item.reject_reason || item.review_comment || '—'}`
  if (item.status === 'APPROVED') return item.recalc_summary || '已重新计算相关数据'
  return '已撤销，原记录保持不变'
}

function updateFarm(): void {
  applyFilters()
}

async function resubmit(id: number): Promise<void> {
  store.closeDetail()
  await store.openResubmit(id)
}

function onSubmitToggle(value: boolean): void {
  if (value) store.submitOpen = true
  else store.requestClose()
}

function onDetailToggle(value: boolean): void {
  if (!value) store.closeDetail()
}

function goHerd(): void {
  void router.push({ name: 'WU-L01' })
}

function originalRoute(
  item: ChangeRequestVO
): { name: string; params?: Record<string, string | number>; query?: Record<string, string> } | null {
  switch (item.biz_type) {
    case 'SALE_ORDER':
      return { name: 'WU-E04', params: { id: item.biz_id } }
    default:
      return null
  }
}

async function viewOriginalRecord(): Promise<void> {
  const item = store.detail
  if (!item) return
  const target = originalRoute(item)
  if (item.farm_id && farm.currentFarmId !== item.farm_id) {
    try {
      await farm.switchTo(item.farm_id)
      if (farm.currentFarmId !== item.farm_id) throw new Error('养殖场正在切换')
      const targetFarm = farm.farms.find((f) => f.farm_id === item.farm_id)
      ui.toast(`已切换到${targetFarm?.name || item.farm_name || '原养殖场'}`)
    } catch {
      ui.toast('切换养殖场失败，请稍后重试', 'error')
      return
    }
  }
  store.closeDetail()
  if (target) {
    await router.push(target)
    return
  }
  // 无独立详情路由的记录，按原业务 ID 展示当前记录；不要跳到未定位的模块列表。
  originalOpen.value = true
  originalLoading.value = true
  originalForm.value = null
  originalError.value = ''
  try {
    originalForm.value = await getChangeRequestForm(item.biz_type, item.biz_id)
  } catch (error) {
    originalError.value = (error as Error).message || '原记录加载失败，请稍后重试'
  } finally {
    originalLoading.value = false
  }
}
</script>

<template>
  <zw-shell side="user" active="WU-U01" :crumbs="['设置', '我的修改申请']">
    <zw-page-head
      title="我的修改申请"
      desc="你在各养殖场提交的修改申请都在这里，审核结果也会通过消息中心通知你。"
    >
      <template #actions>
        <v-btn variant="outlined" prepend-icon="refresh" @click="store.fetchList()">刷新</v-btn>
      </template>
      <v-tabs
        v-if="showTabs"
        :model-value="store.query.status"
        class="zw-tabs-line"
        @update:model-value="onTabChange"
      >
        <v-tab v-for="tab in tabItems" :key="tab.value" :value="tab.value">
          {{ tab.label }} <span class="u01-tab-count num">{{ tab.count }}</span>
        </v-tab>
      </v-tabs>
    </zw-page-head>

    <template v-if="isInitialEmpty">
      <zw-card class="u01-empty">
        <zw-empty
          icon="edit_note"
          title="还没有修改申请"
          desc="需要修改存栏、成本、库存等关键记录时，打开那条记录，在“更多”菜单中选择“提交修改申请”。"
        >
          <v-btn color="primary" prepend-icon="fence" @click="goHerd">去栏舍与存栏</v-btn>
        </zw-empty>
      </zw-card>
    </template>

    <template v-else>
      <zw-notice icon="info" class="mb-4">
        会影响存栏、成本、库存、可出栏日期或统计结果的记录不能直接修改，要在那条记录的“更多”菜单中提交修改申请，由平台管理员审核。<b>审核期间原记录照常有效</b>，审核通过后系统才会修改并重新计算相关数据。
      </zw-notice>

      <div class="zw-toolbar">
        <div class="zw-toolbar-l">
          <v-text-field
            v-model="store.query.keyword"
            class="zw-w-240"
            placeholder="搜索批次号、栏舍或物资"
            prepend-inner-icon="search"
            hide-details
            @keydown.enter="applyFilters"
          />
          <v-select
            v-model="store.query.farmId"
            class="zw-w-160"
            :items="farmOptions"
            item-title="title"
            item-value="value"
            hide-details
            @update:model-value="updateFarm"
          />
          <v-select
            v-model="store.query.bizType"
            class="zw-w-160"
            :items="[{ title: '全部记录类型', value: '' }, ...BIZ_TYPES]"
            item-title="title"
            item-value="value"
            hide-details
            @update:model-value="updateFarm"
          />
          <v-btn variant="text" color="primary" @click="resetFilters">重置</v-btn>
        </div>
        <div class="zw-toolbar-r"><span class="zw-faint body-s">按提交时间倒序</span></div>
      </div>

      <zw-card flush>
        <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@6" />
        <zw-empty
          v-else-if="store.status === 'error'"
          icon="cloud_off"
          title="修改申请加载失败"
          :desc="store.error"
          tone="error"
        >
          <v-btn color="primary" prepend-icon="refresh" @click="store.fetchList()">重新加载</v-btn>
        </zw-empty>
        <zw-empty
          v-else-if="store.status === 'forbidden'"
          icon="lock"
          title="无权查看"
          :desc="store.error || '你没有修改申请的查看权限'"
        >
          <v-btn variant="outlined" @click="router.push('/')">返回工作台</v-btn>
        </zw-empty>
        <zw-empty
          v-else-if="store.status === 'filtered_empty'"
          icon="search_off"
          :title="store.listEmptyText"
          desc="换一个筛选条件，或查看全部修改申请。"
        >
          <v-btn variant="outlined" @click="resetFilters">查看全部</v-btn>
        </zw-empty>
        <template v-else>
          <v-data-table
            :headers="headers"
            :items="store.list"
            item-value="id"
            hide-default-footer
            class="u01-table"
            :row-props="rowProps"
          >
            <template #item.title="{ item }">
              <div class="u01-title-cell">
                <span
                  class="zw-ico-tile"
                  :class="item.status === 'PENDING' ? 'zw-ico-tile--primary' : null"
                >
                  <zw-icon :name="BIZ_ICONS[item.biz_type] || 'edit_note'" :size="18" />
                </span>
                <div class="u01-min">
                  <div class="zw-cell-main">
                    {{ item.biz_type_text || BIZ_TYPE_LABELS[item.biz_type] || '修改申请' }} ·
                    {{ item.change_type === 'VOID' ? '作废' : item.change_type === 'DELETE' ? '删除' : item.field_name || '修改' }}
                  </div>
                  <div class="zw-cell-sub" :class="{ num: item.biz_type === 'SALE_ORDER' }">
                    {{ item.record_locator }}
                  </div>
                </div>
              </div>
            </template>
            <template #item.farm="{ item }">
              <zw-tag type="farm">{{ item.farm_name || '—' }}</zw-tag>
            </template>
            <template #item.change="{ item }">
              <span v-if="item.change_type === 'VOID' || item.change_type === 'DELETE'" class="u01-chg">
                <zw-tag type="error" icon="block" size="small">{{ item.change_type === 'DELETE' ? '删除' : '作废' }}</zw-tag>
                <del class="num">有效</del>
                <zw-icon name="arrow_forward" :size="16" />
                <b class="num">{{ item.change_type === 'DELETE' ? '已删除' : '已作废' }}</b>
              </span>
              <span v-else class="u01-chg">
                <del class="num">{{ item.before_display || '—' }}</del>
                <zw-icon name="arrow_forward" :size="16" />
                <b class="num">{{ item.after_display || '—' }}</b>
              </span>
            </template>
            <template #item.state="{ item }">
              <zw-tag :type="statusTagType(item.status)" :icon="item.status === 'APPROVED' ? 'check' : undefined">
                {{ statusText(item) }}
              </zw-tag>
            </template>
            <template #item.progress="{ item }">
              <div class="u01-prog">
                <div class="num">{{ progressTime(item) }}</div>
                <div class="u01-prog-sub" :class="{ 'is-reject': item.status === 'REJECTED' }">
                  {{ progressNote(item) }}
                </div>
              </div>
            </template>
            <template #item.op="{ item }">
              <span class="zw-cell-actions">
                <a @click.stop="store.openDetail(item.id)">查看</a>
                <a v-if="item.status === 'REJECTED'" @click.stop="resubmit(item.id)">重新提交</a>
              </span>
            </template>
          </v-data-table>
          <div class="zw-table-foot u01-foot">
            <span class="zw-faint body-s">
              <template v-if="store.total <= store.query.size">已显示全部 {{ store.total }} 条</template>
              <template v-else>
                第 {{ (store.query.page - 1) * store.query.size + 1 }}–{{
                  Math.min(store.query.page * store.query.size, store.total)
                }}
                条，共 {{ store.total }} 条
              </template>
            </span>
            <zw-pager
              :total="store.total"
              :page="store.query.page"
              :size="store.query.size"
              @update:page="changePage"
              @update:size="changeSize"
            />
          </div>
        </template>
      </zw-card>
    </template>

    <zw-card title="哪些记录需要提交修改申请" sub="其他记录在原页面直接修改，系统保留修改记录" class="mt-4">
      <div class="u01-guide">
        <div class="u01-guide-item">
          <zw-icon name="add_box" :size="18" />
          <div><div class="u01-guide-t">入栏记录</div><div class="u01-guide-d">已有投喂、称重等后续记录后，修改数量、日期、入栏日龄或引种单价</div></div>
        </div>
        <div class="u01-guide-item">
          <zw-icon name="swap_horiz" :size="18" />
          <div><div class="u01-guide-t">转栏、死淘记录</div><div class="u01-guide-d">登记超过 3 天后修改</div></div>
        </div>
        <div class="u01-guide-item">
          <zw-icon name="pets" :size="18" />
          <div><div class="u01-guide-t">产仔、出雏记录</div><div class="u01-guide-d">已转成新批次后，修改活仔数、健雏数等</div></div>
        </div>
        <div class="u01-guide-item">
          <zw-icon name="rice_bowl" :size="18" />
          <div><div class="u01-guide-t">投喂、接种记录</div><div class="u01-guide-d">修改非当天的记录</div></div>
        </div>
        <div class="u01-guide-item">
          <zw-icon name="scale" :size="18" />
          <div><div class="u01-guide-t">称重记录</div><div class="u01-guide-d">修改已结束批次的称重记录</div></div>
        </div>
        <div class="u01-guide-item">
          <zw-icon name="fact_check" :size="18" />
          <div><div class="u01-guide-t">巡检、消毒记录</div><div class="u01-guide-d">修改超过 3 天的记录</div></div>
        </div>
        <div class="u01-guide-item">
          <zw-icon name="medication" :size="18" />
          <div><div class="u01-guide-t">用药记录</div><div class="u01-guide-d">会使可出栏日期提前的修改，或批次出栏后已锁定的记录</div></div>
        </div>
        <div class="u01-guide-item">
          <zw-icon name="inventory_2" :size="18" />
          <div><div class="u01-guide-t">入库单、盘点与报废</div><div class="u01-guide-d">已发生出库的入库单改数量和单价；已确认的盘点结果；报废记录</div></div>
        </div>
        <div class="u01-guide-item">
          <zw-icon name="local_shipping" :size="18" />
          <div><div class="u01-guide-t">出栏单</div><div class="u01-guide-d">已有收款后修改数量、单价或作废，在出栏单详情中提交</div></div>
        </div>
      </div>
    </zw-card>

    <!-- 修改前后对比 -->
    <zw-drawer v-model="originalOpen" :title="originalForm?.record_title || '原记录'" :sub="originalForm?.record_subtitle" width="640">
      <v-skeleton-loader v-if="originalLoading" type="list-item-two-line@4" />
      <zw-notice v-else-if="originalError" type="error" icon="error">{{ originalError }}</zw-notice>
      <zw-desc v-else :cols="1" bordered :label-width="120">
        <zw-desc-item v-for="(row, index) in originalRows" :key="index" :label="row.label || '记录信息'">{{ row.value || '—' }}</zw-desc-item>
      </zw-desc>
    </zw-drawer>
    <zw-drawer
      :model-value="store.detailOpen"
      :title="detailTitle"
      :sub="detailSub"
      width="760"
      @update:model-value="onDetailToggle"
    >
      <v-skeleton-loader v-if="store.detailLoading" type="article, list-item-three-line@4" />
      <template v-else-if="detail">
        <div class="u01-block"><zw-steps :items="detailSteps" /></div>
        <div class="u01-block">
          <zw-notice :type="detailNotice.type" :icon="detailNotice.icon" :title="detailNotice.title">
            {{ detailNotice.text }}
          </zw-notice>
        </div>
        <div class="u01-block">
          <div class="zw-section-title">修改前后对比</div>
          <zw-compare before-title="原记录" after-title="申请修改为" :rows="compareRows" />
        </div>
        <div class="u01-block">
          <div class="zw-section-title">修改原因</div>
          <div class="u01-quote">{{ detail.reason || '—' }}</div>
        </div>
        <div class="u01-block">
          <div class="zw-section-title">申请信息</div>
          <zw-desc :cols="2" bordered :label-width="112">
            <zw-desc-item label="养殖场">{{ detail.farm_name || '—' }}</zw-desc-item>
            <zw-desc-item label="原记录">{{ detail.record_locator || '—' }}</zw-desc-item>
            <zw-desc-item label="提交人">
              {{ detail.submitted_by?.name || '—' }}
            </zw-desc-item>
            <zw-desc-item label="提交时间">
              <span class="num">{{ formatMonthDayTime(detail.submitted_at) }}</span>
            </zw-desc-item>
            <zw-desc-item label="审核结果">
              <zw-tag :type="statusTagType(detail.status)" size="small">{{ statusText(detail) }}</zw-tag>
              <span class="num u01-reviewed">{{ formatMonthDayTime(detail.reviewed_at) }}</span>
            </zw-desc-item>
            <zw-desc-item label="重新计算">{{ recalcText }}</zw-desc-item>
          </zw-desc>
          <div v-if="detail.recalc_tags?.length" class="u01-tags u01-recalc">
            <zw-tag v-for="tag in detail.recalc_tags" :key="tag">{{ tag }}</zw-tag>
          </div>
        </div>
      </template>
      <template #foot>
        <span class="zw-foot-note">
          {{ detail?.status === 'REJECTED' ? '重新提交时带入这次的修改内容和原因' : '审核结果会通过消息中心通知你' }}
        </span>
        <v-btn variant="outlined" prepend-icon="open_in_new" @click="viewOriginalRecord">查看原记录</v-btn>
        <v-btn
          v-if="detail?.status === 'REJECTED'"
          color="primary"
          prepend-icon="edit_note"
          @click="detail && resubmit(detail.id)"
        >
          重新提交
        </v-btn>
      </template>
    </zw-drawer>

    <!-- 提交修改申请 -->
    <zw-drawer
      :model-value="store.submitOpen"
      title="提交修改申请"
      :sub="store.submitSubtitle"
      width="560"
      @update:model-value="onSubmitToggle"
    >
      <v-skeleton-loader v-if="store.formLoading" type="article, list-item-three-line@3" />
      <zw-empty
        v-else-if="store.formError"
        icon="cloud_off"
        title="表单加载失败"
        :desc="store.formError"
        tone="error"
      >
        <v-btn color="primary" prepend-icon="refresh" @click="store.reloadSubmitForm()">
          重新加载
        </v-btn>
      </zw-empty>
      <template v-else-if="store.form">
        <zw-notice icon="info">
          {{ store.form.review_tip || '这条记录需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。' }}
        </zw-notice>
        <zw-notice v-if="store.hasPending || store.pendingWarning" type="warning" class="mt-3">
          <b>这条记录已有一条待审核的修改申请。</b>审核完成前不能再次提交。
        </zw-notice>
        <zw-notice
          v-if="store.sourceDetail?.reject_reason"
          type="error"
          icon="block"
          class="mt-3"
          title="驳回原因"
        >
          {{ store.sourceDetail.reject_reason }}
        </zw-notice>

        <div class="zw-section-title mt-6">原记录</div>
        <div class="u01-rec">
          <div class="u01-rec-head">
            <span class="zw-ico-tile zw-ico-tile--primary">
              <zw-icon :name="BIZ_ICONS[store.form.biz_type || ''] || 'edit_note'" :size="18" />
            </span>
            <div>
              <div class="zw-cell-main">{{ store.form.record_title || store.form.biz_type_text }}</div>
              <div class="zw-cell-sub">{{ store.form.record_subtitle || store.form.record_locator }}</div>
            </div>
          </div>
          <div v-if="store.form.record_card?.length" class="u01-kv">
            <div v-for="item in store.form.record_card" :key="item.label">
              <div class="u01-kv-l">{{ item.label }}</div>
              <div class="u01-kv-v num">{{ item.value }}</div>
            </div>
          </div>
        </div>

        <div class="zw-section-title mt-6">修改内容</div>
        <div class="zw-form">
          <zw-form-item
            label="修改项"
            required
            hint="只列出这条记录允许申请修改的项目"
            :error="store.fieldErrors.field_code"
          >
            <v-select
              v-model="selectedFieldModel"
              :items="fieldOptions"
              item-title="title"
              item-value="value"
              :error="!!store.fieldErrors.field_code"
            />
          </zw-form-item>

          <div v-if="!store.voidSelected && !store.deleteSelected" class="u01-val">
            <zw-form-item label="原值">
              <div class="zw-readonly u01-ro">
                <del class="num">{{ displayField(store.currentField) }}</del>
                <span class="u01-unit">{{ store.currentField?.value_unit }}</span>
              </div>
            </zw-form-item>
            <zw-icon name="arrow_forward" :size="20" class="u01-val-arrow" />
            <zw-form-item label="新值" required :hint="currentHint" :error="store.fieldErrors.new_value">
              <v-select
                v-if="store.currentField?.input_type === 'SELECT'"
                :model-value="store.newValue"
                :items="(store.currentField?.options || []).filter((option) => !option.disabled)"
                item-title="label"
                item-value="value"
                :error="!!store.fieldErrors.new_value"
                @update:model-value="newValueModel = $event"
              />
              <v-text-field
                v-else-if="store.currentField?.input_type === 'DATE'"
                v-model="newValueModel"
                type="date"
                :error="!!store.fieldErrors.new_value"
              />
              <v-text-field
                v-else
                v-model="newValueModel"
                :type="store.currentField?.input_type === 'NUMBER' ? 'number' : 'text'"
                :suffix="store.currentField?.value_unit"
                placeholder="请输入新值"
                :error="!!store.fieldErrors.new_value"
              />
            </zw-form-item>
          </div>

          <zw-notice v-if="store.voidSelected" type="warning" icon="block">
            {{ store.form.void_notice || '审核通过后这条记录作废，相关库存和统计会重新计算。' }}
          </zw-notice>
          <zw-notice v-if="store.deleteSelected" type="warning" icon="delete_outline">
            {{ store.form.biz_type === 'MEDICATION_RECORD'
              ? '审核通过后删除这条用药记录，回滚对应药品库存并重新计算休药期与可出栏日期。如需更正用药信息，请在审核完成后重新登记。'
              : '审核通过后删除这条记录并重新计算相关数据。审核期间原记录保持不变。' }}
          </zw-notice>

          <zw-form-item label="修改原因" required :error="store.fieldErrors.reason">
            <template #label-extra>
              <span class="u01-lc num">{{ reasonCount }} / 200</span>
            </template>
            <v-textarea
              v-model="reasonModel"
              rows="3"
              maxlength="200"
              placeholder="请说明修改原因，审核结果会通过消息中心通知你"
              :error="!!store.fieldErrors.reason"
            />
          </zw-form-item>

          <div v-if="store.currentRecalcTags.length">
            <div class="u01-recalc-t">审核通过后会重新计算</div>
            <div class="u01-tags">
              <zw-tag v-for="tag in store.currentRecalcTags" :key="tag">{{ tag }}</zw-tag>
            </div>
          </div>
          <div v-else-if="store.form.linked_rules?.length" class="u01-tags">
            <zw-tag v-for="rule in store.form.linked_rules" :key="rule">{{ rule }}</zw-tag>
          </div>
        </div>
      </template>

      <template #foot>
        <template v-if="store.confirmDiscard">
          <div class="u01-confirm">
            <zw-icon name="warning" :size="20" class="u01-confirm-ico" />
            <span><b>放弃这次修改申请？</b>填写的内容不会保存。</span>
          </div>
          <v-btn variant="outlined" @click="store.keepEditing()">继续填写</v-btn>
          <v-btn color="warning" @click="store.discard()">放弃</v-btn>
        </template>
        <template v-else>
          <span class="zw-foot-note">带 * 为必填</span>
          <v-btn variant="outlined" @click="store.requestClose()">取消</v-btn>
          <v-btn
            color="primary"
            :disabled="!store.canSubmit"
            :loading="store.submitting"
            @click="store.submit()"
          >
            提交修改申请
          </v-btn>
        </template>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.u01-table :deep(.v-data-table__tr) {
  cursor: pointer;
}
.u01-tab-count {
  margin-left: 4px;
}
.u01-title-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 3px 0;
}
.u01-min {
  min-width: 0;
}
.u01-chg {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.u01-chg del {
  color: var(--text-3);
}
.u01-chg b {
  font-weight: 500;
  color: var(--text-1);
}
.u01-chg .zw-mso {
  color: var(--text-3);
}
.u01-prog {
  padding: 2px 0;
}
.u01-prog-sub {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.u01-prog-sub.is-reject {
  color: var(--error);
}
.u01-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.u01-guide {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px 32px;
}
.u01-guide-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}
.u01-guide-item > .zw-mso {
  color: var(--text-3);
  margin-top: 2px;
}
.u01-guide-t {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
}
.u01-guide-d {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.u01-empty :deep(.zw-empty-desc) {
  max-width: 372px;
}
.u01-rec {
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  padding: 16px;
}
.u01-rec-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.u01-kv {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line-1);
}
.u01-kv-l {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-2);
}
.u01-kv-v {
  font-size: 14px;
  line-height: 22px;
  color: var(--text-1);
  word-break: break-all;
}
.u01-val {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 20px minmax(0, 1fr);
  gap: 0 12px;
  align-items: start;
}
.u01-val-arrow {
  margin-top: 34px;
  color: var(--text-3);
}
.u01-ro {
  gap: 8px;
}
.u01-ro del {
  color: var(--text-3);
}
.u01-ro .u01-unit {
  margin-left: auto;
  color: var(--text-2);
}
.u01-recalc-t {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
  margin-bottom: 8px;
}
.u01-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.u01-quote {
  background: var(--fill-1);
  border-radius: var(--radius-sm);
  padding: 9px 16px;
  color: var(--text-1);
}
.u01-block + .u01-block {
  margin-top: 24px;
}
.u01-lc {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
}
.u01-reviewed {
  margin-left: 8px;
  color: var(--text-3);
}
.u01-recalc {
  margin-top: 12px;
}
.u01-confirm {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: auto;
  color: var(--text-1);
}
.u01-confirm-ico {
  color: var(--warning);
}
</style>
