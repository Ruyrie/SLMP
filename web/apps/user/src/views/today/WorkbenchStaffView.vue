<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { formatDate, formatNumber, today } from '@zmt/shared'
import { useWorkbenchStore } from '@/stores/today/workbench'
import { useFarmReload } from '@/composables/useFarmReload'
import { useNoticeStore } from '@/stores/notice'
import type { MyPenVO, TodoItemVO } from '@/api/today'
import { actionLabel, statusTag, subText, todoActionLink, todoIcon, todoTone, useTodayLinks } from './todo'
import TodoDetailDrawer from './components/TodoDetailDrawer.vue'

const router = useRouter()
const store = useWorkbenchStore()
const notice = useNoticeStore()
const { goRegister } = useTodayLinks()
const {
  home,
  homeStatus,
  homeError,
  todos,
  total,
  tabCounts,
  penCounts,
  query,
  todoStatus,
  todoError
} = storeToRefs(store)

const drawerOpen = ref(false)
const activeTodoId = ref<number | null>(null)

onMounted(() => store.fetchAll())
useFarmReload(() => store.fetchAll())

const metrics = computed(() => home.value?.staff_metrics || null)
const noPen = computed(() => home.value?.has_pen === false || (metrics.value?.pen_count ?? 0) === 0)
const unit = computed(() => home.value?.farm?.count_unit_text || '头')
const myPens = computed(() => home.value?.my_pens || [])

const title = computed(() => {
  const value = home.value
  if (!value) return '工作台'
  return `${value.greeting || '你好'}，${value.user_name || ''}`
})

const desc = computed(() => {
  const value = home.value
  if (!value) return '今日作业与负责栏舍'
  const farmName = value.farm?.name || ''
  let roleText = value.role_text || ''
  if (roleText.includes('本场')) roleText = roleText.replace('本场', farmName)
  else if (roleText && !roleText.startsWith('你是')) roleText = `你是${roleText}`
  if (!roleText) roleText = `你是${farmName}员工`
  return `${formatDate(value.server_time)} · ${roleText}`
})

const statPens = computed(() => (noPen.value ? 0 : metrics.value?.pen_count ?? 0))
const statTodos = computed(() => (noPen.value ? 0 : metrics.value?.today_todo_count ?? 0))
const statOverdue = computed(() => (noPen.value ? 0 : metrics.value?.overdue_count ?? 0))
const statDone = computed(() => (noPen.value ? 0 : metrics.value?.today_done_count ?? 0))
const pensFoot = computed(() => {
  if (noPen.value) return '还没有分配栏舍'
  const names = metrics.value?.pen_names || []
  return names.length ? names.join('、') : '—'
})
const todosFoot = computed(() => (noPen.value ? '没有待办' : `其中已逾期 ${formatNumber(metrics.value?.overdue_count ?? 0)} 项`))
const overdueFoot = computed(() => (noPen.value ? '没有逾期事项' : metrics.value?.overdue_first_text || '没有逾期事项'))
const doneFoot = computed(() => {
  if (noPen.value) return '今天还没有登记'
  const names = metrics.value?.today_done_names || []
  return names.length ? `${names.join('、')}已登记` : '今天还没有登记'
})

const filterItems = computed(() => {
  const items = [
    { label: '全部', value: 'ALL' },
    { label: '已逾期', value: 'OVERDUE', count: tabCounts.value.OVERDUE ?? 0 }
  ]
  for (const pen of penCounts.value) {
    if (pen.pen_id) items.push({ label: pen.pen_name || '栏舍', value: `PEN:${pen.pen_id}`, count: pen.count ?? 0 })
  }
  return items
})

const headers: any[] = [
  { title: '待办', key: 'title', sortable: false },
  { title: '关联批次', key: 'batch', sortable: false, width: 130 },
  { title: '状态', key: 'status', sortable: false, width: 110 },
  { title: '操作', key: 'op', sortable: false, width: 90, align: 'end' }
]

function onFilterValue(value: string | number): void {
  onFilter(String(value))
}

function onPage(page: number): void {
  store.setPage(page)
}

function onSize(size: number): void {
  store.setSize(size)
}

function onFilter(value: string): void {
  if (value.startsWith('PEN:')) store.setFilterAndPen('ALL', Number(value.slice(4)))
  else store.setFilterAndPen(value, null)
}

function emptyTitle(): string {
  return todoStatus.value === 'filtered_empty' ? '没有符合筛选条件的待办' : '今天没有待办，也没有逾期事项。'
}

function emptyDesc(): string {
  return todoStatus.value === 'filtered_empty'
    ? '换一个筛选条件，或回到全部待办。'
    : '新的提醒会自动出现在这里。需要登记作业时，点顶部“登记”。'
}

function openTodo(item: TodoItemVO): void {
  if (!item.todo_id) return
  activeTodoId.value = item.todo_id
  drawerOpen.value = true
}

function onRow(_event: Event, slot: { item: TodoItemVO }): void {
  openTodo(slot.item)
}

function onAction(item: TodoItemVO): void {
  void goRegister(todoActionLink(item))
}

function openPen(pen: MyPenVO): void {
  const batchId = pen.batches?.[0]?.batch_id
  if (batchId) void router.push({ name: 'WU-L04', params: { batchId: String(batchId) } })
  else void router.push({ name: 'WU-L03' })
}

function penStock(pen: MyPenVO): number {
  return (pen.batches || []).reduce((sum, batch) => sum + (batch.head_count || 0), 0)
}

function penAge(pen: MyPenVO): number | string {
  return pen.batches?.[0]?.day_age ?? '—'
}

function penStage(pen: MyPenVO): string {
  return pen.batches?.[0]?.stage_text || '—'
}

function penProgress(pen: MyPenVO): number {
  const total = pen.today_task_total || 0
  return total ? ((pen.today_task_done || 0) / total) * 100 : 0
}

function goCalendarToday(): void {
  void router.push({ name: 'WU-T03', query: { date: today() } })
}

function onChanged(): void {
  void store.fetchAll()
  void notice.load()
}
</script>

<template>
  <zw-shell side="user" active="WU-T02" :crumbs="['工作台']">
    <zw-page-head :title="title" :desc="desc">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="calendar_month" @click="router.push({ name: 'WU-T03' })">养殖日历</v-btn>
      </template>
    </zw-page-head>

    <template v-if="homeStatus === 'loading'">
      <div class="zw-grid-4 mb-4">
        <v-skeleton-loader v-for="index in 4" :key="index" type="card" />
      </div>
      <v-skeleton-loader type="table-row@6" />
    </template>

    <zw-card v-else-if="homeStatus === 'forbidden'">
      <zw-empty icon="lock" title="无权查看" :desc="homeError || '你没有工作台的查看权限'" tone="error" />
    </zw-card>

    <zw-card v-else-if="homeStatus === 'error'">
      <zw-empty icon="cloud_off" title="工作台加载失败" :desc="homeError" tone="error">
        <v-btn variant="outlined" prepend-icon="refresh" @click="store.fetchAll()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="home">
      <div class="zw-grid-4 mb-4">
        <zw-stat
          class="wb-stat-link"
          label="负责栏舍"
          icon="fence"
          :value="statPens"
          unit="个"
          :foot="pensFoot"
          tone="primary"
          @click="router.push({ name: 'WU-L03' })"
        />
        <zw-stat label="今日待办" icon="checklist" :value="statTodos" unit="项" :foot="todosFoot" @click="goCalendarToday" />
        <zw-stat label="已逾期" icon="schedule" :value="statOverdue" unit="项" :foot="overdueFoot" @click="goCalendarToday" />
        <zw-stat label="今日已完成" icon="task_alt" :value="statDone" unit="项" :foot="doneFoot" @click="goCalendarToday" />
      </div>

      <zw-card v-if="noPen" flush>
        <zw-empty
          icon="fence"
          title="你还没有负责的栏舍"
          desc="请联系所有者或场长分配栏舍。分配后，负责栏舍的作业待办会出现在这里。"
        />
      </zw-card>

      <div v-else class="wb-main">
        <zw-card title="今天和已逾期" :sub="`${total} 项`" flush class="wb-todo">
          <template #extra>
            <zw-seg
              size="small"
              :model-value="query.penId ? `PEN:${query.penId}` : query.filter"
              :items="filterItems"
              @update:model-value="onFilterValue"
            />
          </template>

          <v-skeleton-loader v-if="todoStatus === 'loading'" type="table-row@5" />

          <zw-empty
            v-else-if="todoStatus === 'error'"
            icon="cloud_off"
            title="今日待办加载失败"
            :desc="todoError"
            tone="error"
          >
            <v-btn variant="outlined" prepend-icon="refresh" @click="store.fetchTodos()">重新加载</v-btn>
          </zw-empty>

          <zw-empty
            v-else-if="todoStatus === 'empty' || todoStatus === 'filtered_empty'"
            icon="task_alt"
            :title="emptyTitle()"
            :desc="emptyDesc()"
          >
            <v-btn v-if="todoStatus === 'filtered_empty'" variant="outlined" @click="store.setFilterAndPen('ALL', null)">清除筛选</v-btn>
          </zw-empty>

          <template v-else>
            <v-data-table
              :headers="headers"
              :items="todos"
              hide-default-footer
              density="comfortable"
              @click:row="onRow"
            >
              <template #item.title="{ item }">
                <div class="wb-todo-cell">
                  <span class="zw-ico-tile" :class="'zw-ico-tile--' + todoTone(item)">
                    <zw-icon :name="todoIcon(item)" :size="18" />
                  </span>
                  <div>
                    <div class="zw-cell-main">{{ item.title }}</div>
                    <div class="zw-cell-sub">{{ subText(item) }}</div>
                  </div>
                </div>
              </template>
              <template #item.batch="{ item }">
                <span v-if="item.batch_no" class="num">{{ item.batch_no }}</span>
                <span v-else class="zw-faint">—</span>
              </template>
              <template #item.status="{ item }">
                <zw-tag :type="statusTag(item).type" :icon="statusTag(item).icon">{{ statusTag(item).text }}</zw-tag>
              </template>
              <template #item.op="{ item }">
                <span class="zw-cell-actions"><a @click.stop="onAction(item)">{{ actionLabel(item) }}</a></span>
              </template>
            </v-data-table>
            <div class="zw-table-foot zw-table-foot--in">
              <span class="zw-faint body-s">登记对应记录后，待办自动完成。需要调整计划时，请联系所有者或场长。</span>
              <zw-pager
                :total="total"
                :page="query.page"
                :size="query.size"
                @update:page="onPage"
                @update:size="onSize"
              />
            </div>
          </template>
        </zw-card>

        <div class="wb-side">
          <zw-card title="我负责的栏舍" :sub="`${myPens.length} 个`" flush>
            <template #extra>
              <a class="zw-link" @click="router.push({ name: 'WU-L03' })">全部批次</a>
            </template>
            <div v-for="pen in myPens" :key="pen.pen_id" class="t02-pen" @click="openPen(pen)">
              <div class="t02-pen-head">
                <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="fence" :size="18" /></span>
                <div class="t02-pen-title">
                  <div class="t02-pen-name">{{ pen.pen_name }}</div>
                  <div class="t02-pen-sub">{{ pen.pen_type_text }} · 容量 {{ formatNumber(pen.capacity) }} {{ unit }}</div>
                </div>
                <zw-tag v-if="pen.in_withdrawal" type="warning" icon="hourglass_top" size="small">
                  休药中<template v-if="pen.withdrawal_days_left"> · 剩 {{ pen.withdrawal_days_left }} 天</template>
                </zw-tag>
                <zw-icon v-else name="chevron_right" :size="20" color="text-3" />
              </div>
              <div v-for="batch in pen.batches || []" :key="batch.batch_id" class="t02-batch">
                <zw-icon name="folder_open" :size="18" />
                <span class="num">{{ batch.batch_no }}</span>
                <span>在栏批次 · {{ batch.breed_text || batch.breed_title || '—' }}</span>
              </div>
              <div v-if="!(pen.batches || []).length" class="t02-batch">
                <zw-icon name="folder_open" :size="18" />
                <span>暂无在栏批次</span>
              </div>
              <div class="t02-kpis">
                <div><div class="t02-kpi-l">存栏</div><div class="t02-kpi-v num">{{ formatNumber(penStock(pen)) }}<small>{{ unit }}</small></div></div>
                <div><div class="t02-kpi-l">日龄</div><div class="t02-kpi-v num">{{ penAge(pen) }}<small>天</small></div></div>
                <div><div class="t02-kpi-l">生长阶段</div><div class="t02-kpi-v">{{ penStage(pen) }}</div></div>
              </div>
              <div class="t02-prog">
                <div class="zw-between">
                  <span>今日任务</span>
                  <span class="num">{{ pen.today_task_done || 0 }} / {{ pen.today_task_total || 0 }} 已完成</span>
                </div>
                <v-progress-linear :model-value="penProgress(pen)" height="6" rounded />
              </div>
            </div>
          </zw-card>
        </div>
      </div>
    </template>

    <TodoDetailDrawer v-model="drawerOpen" :todo-id="activeTodoId" @changed="onChanged" />
  </zw-shell>
</template>

<style scoped>
.wb-stat-link {
  cursor: pointer;
}
.wb-stat-link:hover {
  border-color: var(--line-2);
}
.wb-main {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
}
.wb-todo {
  flex: 1 1 0;
  min-width: 0;
}
.wb-side {
  width: 340px;
  flex-shrink: 0;
}
.wb-todo-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
}
.t02-pen {
  padding: 16px 20px;
  cursor: pointer;
}
.t02-pen + .t02-pen {
  border-top: 1px solid var(--line-1);
}
.t02-pen:hover {
  background: var(--fill-1);
}
.t02-pen-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.t02-pen-title {
  flex: 1;
  min-width: 0;
}
.t02-pen-name {
  font-size: 14px;
  line-height: 22px;
  font-weight: 600;
  color: var(--text-1);
}
.t02-pen-sub {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.t02-batch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  font-size: 13px;
  color: var(--text-2);
}
.t02-batch .num {
  color: var(--text-1);
  font-weight: 500;
}
.t02-kpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 12px;
}
.t02-kpi-l {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.t02-kpi-v {
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
  color: var(--text-1);
}
.t02-kpi-v small {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-2);
  margin-left: 2px;
}
.t02-prog {
  margin-top: 12px;
}
.t02-prog .zw-between {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-2);
  margin-bottom: 6px;
}
</style>
