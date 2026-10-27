<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatNumber, formatPercent } from '@zmt/shared'
import type { DataTableHeader } from 'vuetify'
import type { BatchLookup, MyPen } from '@/api/herd'
import { useBatchListStore, type BatchTab } from '@/stores/herd/batchList'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { monthDay, sourceText } from '@/stores/herd/shared'

const store = useBatchListStore()
const farm = useFarmStore()
const router = useRouter()
const route = useRoute()
const ui = useUiStore()
const { isStaff } = useRole()

const tab = computed({
  get: () => store.query.status,
  set: (value: BatchTab) => store.setTab(value)
})

const activeTabCount = computed(() => store.activeCount)
const endedTabCount = computed(() => store.endedCount)
const allTabCount = computed(() => store.allCount)

const desc = computed(() => {
  const name = farm.farmName || '当前养殖场'
  return isStaff.value ? `${name} · 你负责 ${store.staffPenCount} 个栏舍` : `${name} · ${store.ownerDesc}`
})

const headers = computed<DataTableHeader[]>(() => {
  const rows: DataTableHeader[] = [
    { title: '批次号', key: 'batch_no', sortable: false, width: 126 },
    { title: '品种', key: 'breed', sortable: false, width: 112 },
    { title: '栏舍', key: 'pen', sortable: false },
    { title: '来源', key: 'source', sortable: false, width: 82 },
    { title: '入栏日期', key: 'entry_date', sortable: false, width: 132 },
    { title: '日龄', key: 'day_age', sortable: false, width: 98 },
    { title: '存栏', key: 'current_count', sortable: false, align: 'end', width: 98 },
    { title: '死亡率', key: 'mortality', sortable: false, align: 'end', width: 86 },
    { title: '状态', key: 'status', sortable: false, width: 96 }
  ]
  if (store.query.status === 'ENDED') {
    rows.push({ title: '结束日期', key: 'end_date', sortable: false, align: 'end', width: 132 })
  }
  rows.push({ title: '操作', key: 'actions', sortable: false, align: 'end', width: 96 })
  return rows
})

const lookupFieldError = computed(() => ['not_found', 'invalid_format', 'not_batch_qr'].includes(store.lookupState))
const lookupNoPermission = computed(() => store.lookupState === 'forbidden')
const ownerEmptyTitle = computed(() => {
  if (store.hasFilters) return '没有找到匹配的批次'
  if (store.query.status === 'ACTIVE') return '还没有在养批次'
  if (store.query.status === 'ENDED') return '还没有已结束的批次'
  return '还没有批次'
})
const ownerEmptyDesc = computed(() => {
  if (store.hasFilters) return '换一个栏舍或品类筛选条件试试。'
  if (store.query.status === 'ACTIVE') return '点“批次入栏”登记第一个批次。'
  return '批次入栏后会显示在这里。'
})

function openBatch(batchId?: number): void {
  if (!batchId) return
  void router.push({ name: 'WU-L04', params: { batchId: String(batchId) } })
}

function openEntry(): void {
  void router.push({ name: 'WU-L05', query: { action: 'create', tab: 'entry' } })
}

function resetFilters(): void {
  store.query.penId = 0
  store.query.categoryId = 0
  void store.fetchList()
}

function handleInput(value: string): void {
  store.lookupInput = String(value || '').toUpperCase()
  if (store.lookupState !== 'idle') store.resetLookup()
}

function clearLookup(): void {
  store.clearLookupInput()
}

async function openLookup(batchNo?: string): Promise<BatchLookup | null> {
  const result = await store.lookup(batchNo ?? store.lookupInput)
  if (!result) return null
  if (result.result === 'SWITCH_FARM' && result.farm_id) {
    try {
      await farm.switchTo(Number(result.farm_id))
      ui.toast(`已切换到${result.farm_name || '新的养殖场'}`)
      if (result.batch_id) {
        await router.push({
          name: 'WU-L04',
          params: { batchId: String(result.batch_id) },
          query: { switched: '1' }
        })
      }
    } catch {
      ui.toast('切换养殖场失败，请稍后重试', 'error')
    }
    return result
  }
  if (result.result === 'OK' && result.batch_id) {
    await router.push({ name: 'WU-L04', params: { batchId: String(result.batch_id) } })
  }
  return result
}

function leaveForbidden(): void {
  store.resetLookup()
}

async function autoLookupFromRoute(): Promise<void> {
  const raw = route.query.keyword
  const keyword = Array.isArray(raw) ? String(raw[0] || '') : String(raw || '')
  if (!keyword.trim()) return
  store.lookupInput = keyword.trim().toUpperCase()
  await openLookup(keyword.trim())
}

function taskIcon(type?: string): string {
  const map: Record<string, string> = {
    FEED: 'rice_bowl',
    VACCINE: 'vaccines',
    IMMUNE: 'vaccines',
    INSPECT: 'fact_check',
    DISINFECT: 'sanitizer',
    WEIGH: 'scale'
  }
  return type ? map[type.toUpperCase()] || 'fact_check' : 'fact_check'
}

function taskRoute(task: { route?: string }): string | null {
  const map: Record<string, string> = {
    G01: 'WU-G01',
    G02: 'WU-G01',
    G03: 'WU-G03',
    D01: 'WU-D01',
    D02: 'WU-D02',
    D03: 'WU-D03',
    I01: 'WU-I01',
    I02: 'WU-I02'
  }
  return task.route ? map[task.route] || null : null
}

function openTask(task: { route?: string }): void {
  const name = taskRoute(task)
  if (name) void router.push({ name })
}

function penAge(pen: MyPen): string {
  return formatNumber(pen.batches?.[0]?.day_age || 0)
}

function penStage(pen: MyPen): string {
  return pen.batches?.[0]?.stage_name || '—'
}

function openPenBatch(pen: MyPen): void {
  const batchId = pen.batches?.[0]?.batch_id
  if (batchId) openBatch(batchId)
}

onMounted(async () => {
  await store.load(isStaff.value)
  await autoLookupFromRoute()
})

watch(
  () => route.query.keyword,
  async () => {
    await autoLookupFromRoute()
  }
)

useFarmReload(() => store.load(isStaff.value, true))
</script>

<template>
  <zw-shell
    side="user"
    :role="isStaff ? 'staff' : undefined"
    active="WU-L03"
    :crumbs="isStaff ? ['我的栏舍', '负责栏舍与批次'] : ['生产管理', '批次档案']"
  >
    <zw-page-head :title="isStaff ? '我的栏舍' : '批次列表与查询'" :desc="desc">
      <template v-if="!isStaff" #actions>
        <v-btn color="primary" prepend-icon="move_to_inbox" @click="openEntry">批次入栏</v-btn>
      </template>
    </zw-page-head>

    <zw-card class="mb-4">
      <div class="l03-find">
        <span class="l03-find-ico"><zw-icon name="qr_code_2" :size="22" /></span>
        <div class="l03-find-main">
          <div class="title-s">输入批次号查档案</div>
          <div class="zw-faint body-s">
            批次标签上的二维码对应批次号。顶栏搜索框同样可以输入批次号；你加入的其他养殖场的批次会自动切换到该养殖场再打开。
          </div>
          <div class="l03-find-row">
            <v-text-field
              :model-value="store.lookupInput"
              placeholder="例如 ZM2610-031"
              prepend-inner-icon="search"
              :error="lookupFieldError"
              :error-messages="lookupFieldError ? store.lookupMessage : ''"
              clearable
              @update:model-value="handleInput"
              @keydown.enter="openLookup()"
              @click:clear="clearLookup"
            />
            <v-btn variant="outlined" :loading="store.lookupLoading" @click="openLookup()">打开档案</v-btn>
          </div>
        </div>
      </div>
    </zw-card>

    <zw-card v-if="store.status === 'forbidden' || lookupNoPermission">
      <zw-empty
        icon="lock"
        tone="warning"
        :title="lookupNoPermission ? '你没有这个批次的查看权限' : '无权查看'"
        :desc="lookupNoPermission
          ? (store.lookupMessage || `批次 ${store.lookupBatchNo} 不在你负责的栏舍内，或不属于你加入的养殖场。扫描或输入你加入的其他养殖场的批次号时，会自动切换到该养殖场并打开档案。`)
          : (store.error || '当前角色无权查看批次列表。')"
      >
        <v-btn variant="outlined" @click="lookupNoPermission ? leaveForbidden() : router.push({ name: 'WU-T01' })">
          {{ isStaff ? '返回我的栏舍' : '返回批次列表' }}
        </v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="isStaff">
      <div v-if="store.status === 'loading'" class="zw-grid-2">
        <v-skeleton-loader v-for="item in 2" :key="item" type="article" />
      </div>
      <zw-card v-else-if="store.status === 'error'">
        <zw-empty icon="cloud_off" tone="error" title="批次加载失败" :desc="store.error">
          <v-btn color="primary" variant="outlined" @click="store.load(true, true)">重新加载</v-btn>
        </zw-empty>
      </zw-card>
      <zw-card v-else-if="store.status === 'empty' || !store.myPens?.pens?.length">
        <zw-empty icon="fence" title="你还没有负责的栏舍" desc="请联系所有者或场长在成员管理中为你分配栏舍。" />
      </zw-card>
      <div v-else class="zw-grid-2 l03-pens">
        <zw-card v-for="pen in store.myPens?.pens || []" :key="pen.pen_id">
          <div class="l03-pen-head">
            <span class="zw-ico-tile zw-ico-tile--primary zw-ico-tile--lg"><zw-icon name="fence" :size="20" /></span>
            <div class="l03-pen-title">
              <div class="title-card">{{ pen.pen_name }}</div>
              <div class="zw-faint body-s">{{ pen.pen_type_text || '栏舍' }} · 容量 {{ formatNumber(pen.capacity || 0) }} {{ pen.unit_text || '头' }}</div>
            </div>
            <v-btn v-if="pen.batches?.length" variant="text" color="primary" append-icon="chevron_right" @click="openPenBatch(pen)">批次档案</v-btn>
          </div>

          <template v-if="pen.batches?.length">
            <div v-for="batch in pen.batches" :key="batch.batch_id" class="l03-batch l03-batch--link" @click="openBatch(batch.batch_id)">
              <zw-icon name="folder_open" :size="20" color="text-2" />
              <div class="l03-batch-main">
                <div class="num zw-cell-main">{{ batch.batch_no }}</div>
                <div class="zw-cell-sub">在栏批次 · {{ batch.breed_title }}</div>
              </div>
              <zw-tag v-if="batch.in_withdrawal" type="warning" icon="hourglass_top">
                休药中{{ batch.withdrawal_remaining_days ? ` · 剩 ${batch.withdrawal_remaining_days} 天` : '' }}
              </zw-tag>
            </div>

            <div class="l03-kpis">
              <div><div class="l03-kpi-l">存栏</div><div class="l03-kpi-v num">{{ formatNumber(pen.stock_count || 0) }}<small>头</small></div></div>
              <div><div class="l03-kpi-l">日龄</div><div class="l03-kpi-v num">{{ penAge(pen) }}<small>天</small></div></div>
              <div><div class="l03-kpi-l">生长阶段</div><div class="l03-kpi-v">{{ penStage(pen) }}</div></div>
            </div>

            <div class="l03-tasks">
              <div class="zw-between body-s">
                <span class="title-s">今日任务</span>
                <span class="zw-muted num">{{ pen.task_done || 0 }} / {{ pen.task_total || 0 }} 已完成</span>
              </div>
              <v-progress-linear
                :model-value="pen.task_total ? Number(pen.task_done || 0) / Number(pen.task_total) * 100 : 0"
                height="6"
                rounded
                class="mt-2 mb-1"
              />
              <div v-if="pen.tasks?.length">
                <div v-for="task in pen.tasks" :key="task.todo_id || task.title" class="l03-task">
                  <zw-icon :name="taskIcon(task.todo_type)" :size="18" />
                  <div class="l03-task-t">{{ task.title }}<span>{{ task.time_text || task.subtitle }}</span></div>
                  <zw-tag type="primary">待处理</zw-tag>
                  <span class="zw-cell-actions"><a @click="openTask(task)">去登记</a></span>
                </div>
              </div>
              <div v-else class="l03-no-task">
                <zw-icon :name="pen.task_total && pen.task_done === pen.task_total ? 'check_circle' : 'task_alt'" :size="18" />
                {{ pen.task_total && pen.task_done === pen.task_total ? '今天的任务都完成了' : '今天没有待处理任务' }}
              </div>
            </div>
          </template>
          <div v-else class="l03-pen-empty">
            <zw-tag>空栏</zw-tag>
            <span class="zw-faint">容量 {{ formatNumber(pen.capacity || 0) }} {{ pen.unit_text || '头' }}</span>
          </div>
        </zw-card>
      </div>
    </template>

    <zw-card v-else-if="store.status === 'loading'">
      <v-skeleton-loader type="table-heading, table-row@6" />
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="批次档案加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load(false, true)">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'empty' || !store.list.length" flush>
      <zw-empty :icon="store.hasFilters ? 'search_off' : 'folder_open'" :title="ownerEmptyTitle" :desc="ownerEmptyDesc">
        <v-btn v-if="!store.hasFilters && store.query.status === 'ACTIVE'" color="primary" @click="openEntry">批次入栏</v-btn>
        <v-btn v-else variant="text" @click="resetFilters">清除筛选</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else flush>
      <div class="l03-table-head">
        <v-tabs v-model="tab" class="zw-tabs-line l03-tabs">
          <v-tab value="ACTIVE">在养 {{ activeTabCount }}</v-tab>
          <v-tab value="ENDED">已结束 {{ endedTabCount }}</v-tab>
          <v-tab value="ALL">全部 {{ allTabCount }}</v-tab>
        </v-tabs>
        <div class="l03-filters">
          <v-select v-model="store.query.penId" :items="store.penItems" item-title="title" item-value="value" class="zw-w-160" @update:model-value="store.setPen" />
          <v-select v-model="store.query.categoryId" :items="store.categoryItems" item-title="title" item-value="value" class="zw-w-140" @update:model-value="store.setCategory" />
        </div>
      </div>
      <v-data-table :headers="headers" :items="store.list" item-value="batch_id" hide-default-footer class="l03-table">
        <template #item.batch_no="{ item }">
          <span class="batch-link">
            <a class="zw-link num" @click="openBatch(item.batch_id)">{{ item.batch_no }}</a>
            <zw-tag v-if="item.purpose === 'BREEDING'" size="small">种用</zw-tag>
          </span>
        </template>
        <template #item.breed="{ item }">
          <div>{{ item.breed_name }}</div>
          <div class="zw-cell-sub">{{ item.category_name }}</div>
        </template>
        <template #item.pen="{ item }">
          <div>{{ (item.pens || []).map((pen) => pen.pen_name).join('、') || '—' }}</div>
          <div v-if="item.pens?.length" class="zw-cell-sub num">在栏 {{ formatNumber(item.pens[0].stock_count || 0) }} 头</div>
        </template>
        <template #item.source="{ item }"><zw-tag>{{ sourceText(item.source) }}</zw-tag></template>
        <template #item.entry_date="{ item }">
          <div class="num">{{ monthDay(item.entry_date) }}</div>
          <div class="zw-cell-sub num">{{ item.entry_age_days ?? 0 }} 日龄入栏</div>
        </template>
        <template #item.day_age="{ item }">
          <div class="num">{{ formatNumber(item.day_age || 0) }} 天</div>
          <div class="zw-cell-sub">{{ item.stage_name || '—' }}</div>
        </template>
        <template #item.current_count="{ item }">
          <span v-if="item.status === 'ACTIVE'" class="num">{{ formatNumber(item.current_count || 0) }} 头</span>
          <span v-else class="zw-faint">已全部出栏</span>
        </template>
        <template #item.mortality="{ item }"><span class="num">{{ formatPercent(item.mortality_rate, 1) }}</span></template>
        <template #item.status="{ item }">
          <zw-tag v-if="item.in_withdrawal" type="warning" icon="hourglass_top">休药中</zw-tag>
          <zw-tag v-else-if="item.status === 'ENDED'">已结束</zw-tag>
          <zw-tag v-else type="primary">在养</zw-tag>
        </template>
        <template #item.end_date="{ item }"><span class="num">{{ monthDay(item.end_date) }}</span></template>
        <template #item.actions="{ item }"><span class="zw-cell-actions"><a @click="openBatch(item.batch_id)">查看档案</a></span></template>
      </v-data-table>
      <div class="zw-table-foot zw-table-foot--in">
        <span class="zw-faint body-s">批次号由系统生成，全平台唯一，不能修改</span>
        <zw-pager
          :total="store.total"
          :page="store.query.page"
          :size="store.query.size"
          @update:page="store.setPage"
          @update:size="store.setSize"
        />
      </div>
    </zw-card>
  </zw-shell>
</template>

<style scoped>
.l03-find { display: flex; align-items: flex-start; gap: 16px; }
.l03-find-ico { display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: var(--radius-sm); background: var(--primary-subtle); color: var(--primary); flex-shrink: 0; }
.l03-find-main { flex: 1; min-width: 0; }
.l03-find-row { display: flex; align-items: flex-start; gap: 8px; margin-top: 8px; }
.l03-find-row :deep(.v-input) { flex: 0 0 360px; max-width: 360px; }
.l03-find-row :deep(input) { text-transform: uppercase; font-variant-numeric: tabular-nums; }
.l03-pens { align-items: start; }
.l03-pen-head { display: flex; align-items: center; gap: 12px; }
.l03-pen-title { flex: 1; min-width: 0; }
.l03-batch { display: flex; align-items: center; gap: 10px; margin-top: 16px; padding: 10px 12px; border-radius: var(--radius-sm); background: var(--fill-1); }
.l03-batch--link { cursor: pointer; }
.l03-batch--link:hover { background: var(--fill-2); }
.l03-batch-main { flex: 1; min-width: 0; }
.l03-kpis { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin-top: 16px; }
.l03-kpi-l { font-size: 12px; line-height: 18px; color: var(--text-3); }
.l03-kpi-v { font-size: 22px; line-height: 30px; font-weight: 600; color: var(--text-1); }
.l03-kpi-v small { font-size: 13px; font-weight: 400; color: var(--text-2); margin-left: 2px; }
.l03-tasks { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--line-1); }
.l03-task { display: flex; align-items: center; gap: 10px; min-height: 40px; font-size: 14px; }
.l03-task .zw-mso { color: var(--text-2); }
.l03-task-t { flex: 1; min-width: 0; }
.l03-task-t span { color: var(--text-3); margin-left: 8px; }
.l03-no-task { display: flex; align-items: center; gap: 8px; padding: 12px 0; font-size: 14px; color: var(--text-3); }
.l03-pen-empty { display: flex; align-items: center; gap: 8px; padding-top: 16px; border-top: 1px solid var(--line-1); margin-top: 16px; }
.l03-table-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 4px 16px 0; }
.l03-tabs { border-bottom: 0 !important; }
.l03-filters { display: flex; align-items: center; gap: 12px; }
.l03-table :deep(td) { white-space: nowrap; }
.batch-link { display: inline-flex; align-items: center; gap: 6px; }
.l03-table :deep(.v-tab) { margin-right: 24px; }
</style>
