<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { formatDate, formatMoney, formatNumber, formatPercent, today } from '@zmt/shared'
import { useWorkbenchStore } from '@/stores/today/workbench'
import { useFarmReload } from '@/composables/useFarmReload'
import { useNoticeStore } from '@/stores/notice'
import type { PenOptionVO, TodoItemVO } from '@/api/today'
import { actionLabel, statusTag, subText, todoActionLink, todoIcon, todoTone, useTodayLinks } from './todo'
import TodoDetailDrawer from './components/TodoDetailDrawer.vue'

const router = useRouter()
const store = useWorkbenchStore()
const notice = useNoticeStore()
const { goRegister, goList } = useTodayLinks()
const {
  home,
  homeStatus,
  homeError,
  todos,
  reminders,
  total,
  tabCounts,
  query,
  todoStatus,
  todoError,
  pens,
  pensStatus
} = storeToRefs(store)

const drawerOpen = ref(false)
const activeTodoId = ref<number | null>(null)

onMounted(() => store.fetchAll())
useFarmReload(() => store.fetchAll())

const owner = computed(() => home.value?.owner_metrics || null)
const title = computed(() => {
  const value = home.value
  if (!value) return '工作台'
  return `${value.greeting || '你好'}，${value.user_name || ''}`
})

const desc = computed(() => {
  const value = home.value
  if (!value) return '今日待办与生产提醒'
  const farmName = value.farm?.name || ''
  let roleText = value.role_text || ''
  if (roleText.includes('本场')) roleText = roleText.replace('本场', farmName)
  else if (roleText && !roleText.startsWith('你是')) roleText = `你是${roleText}`
  if (!roleText) {
    const role = value.role === 'MANAGER' ? '场长' : value.role === 'STAFF' ? '员工' : '所有者'
    roleText = `你是${farmName}${role}`
  }
  return `${formatDate(value.server_time)} · ${roleText}`
})

const stockUnit = computed(() => owner.value?.count_unit_text || '头')
const statStock = computed(() => formatNumber(owner.value?.stock_count ?? 0))
const statTodo = computed(() => formatNumber(owner.value?.today_todo_count ?? 0))
const statWarning = computed(() => formatNumber(owner.value?.warning_count ?? 0))
const statReceivable = computed(() => formatMoney(owner.value?.receivable_amount ?? 0))
const stockFoot = computed(
  () =>
    `在养批次 ${formatNumber(owner.value?.batch_count ?? 0)} 个 · 栏舍占用 ${formatPercent(
      owner.value?.pen_occupancy_rate ?? 0
    )}`
)
const todoFoot = computed(() => `其中已逾期 ${formatNumber(owner.value?.overdue_count ?? 0)} 项`)
const warningFoot = computed(() => {
  const names = owner.value?.warning_names || []
  return names.length ? names.join('、') : '没有未解除的预警'
})
const receivableFoot = computed(() => {
  const count = owner.value?.receivable_overdue_count || 0
  if (!count) return '没有逾期的待收款'
  return `${formatNumber(count)} 笔已逾期 ${formatNumber(owner.value?.receivable_max_overdue_days ?? 0)} 天`
})

const filterItems = computed(() => [
  { label: '全部', value: 'ALL' },
  { label: '已逾期', value: 'OVERDUE', count: tabCounts.value.OVERDUE ?? 0 },
  { label: '作业', value: 'WORK' },
  { label: '经营与预警', value: 'BIZ_WARN' }
])

const headers: any[] = [
  { title: '待办', key: 'title', sortable: false },
  { title: '负责人', key: 'who', sortable: false, width: 110 },
  { title: '状态', key: 'status', sortable: false, width: 110 },
  { title: '操作', key: 'op', sortable: false, width: 96, align: 'end' }
]

const productionPenTypes = new Set(['NURSERY', 'FATTENING', 'BROODING', 'BROILER'])
const penRows = computed(() =>
  pens.value.filter((pen: PenOptionVO) => productionPenTypes.has(pen.pen_type || '') && !pen.disabled)
)
const noPen = computed(
  () => home.value?.has_pen === false || (pensStatus.value === 'ready' && pens.value.length === 0)
)
const penCapacity = computed(() => penRows.value.reduce((sum, pen) => sum + (pen.capacity || 0), 0))
const penUnit = computed(() => penRows.value[0]?.unit_text || stockUnit.value)
const penSub = computed(
  () => `${penRows.value.length} 个栏舍 · 容量 ${formatNumber(penCapacity.value)} ${penUnit.value}`
)

function penRatio(pen: PenOptionVO): number {
  return pen.capacity ? ((pen.stock_count || 0) / pen.capacity) * 100 : 0
}

function emptyTitle(): string {
  return todoStatus.value === 'filtered_empty' ? '没有符合筛选条件的待办' : '今天没有待办，也没有逾期事项。'
}

function emptyDesc(): string {
  return todoStatus.value === 'filtered_empty'
    ? '换一个筛选条件，或回到全部待办。'
    : '新的提醒会自动出现在这里。需要登记作业时，点顶部“登记”。'
}

function onFilterValue(value: string | number): void {
  store.setFilter(String(value))
}

function onPage(page: number): void {
  store.setPage(page)
}

function onSize(size: number): void {
  store.setSize(size)
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
  if (item.todo_type === 'WITHDRAWAL_END') void goList(todoActionLink(item))
  else void goRegister(todoActionLink(item))
}

function goCalendarToday(): void {
  void router.push({ name: 'WU-T03', query: { date: today() } })
}

function askAi(): void {
  void router.push({
    name: 'WU-K01',
    query: home.value?.ai_hint ? { q: home.value.ai_hint, new: '1' } : { new: '1' }
  })
}

function onChanged(): void {
  void store.fetchAll()
  void notice.load()
}
</script>

<template>
  <zw-shell side="user" active="WU-T01" :crumbs="['工作台']">
    <zw-page-head :title="title" :desc="desc">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="calendar_month" @click="router.push({ name: 'WU-T03' })">养殖日历</v-btn>
      </template>
    </zw-page-head>

    <template v-if="homeStatus === 'loading'">
      <div class="zw-grid-4 mb-4">
        <v-skeleton-loader v-for="index in 4" :key="index" type="card" />
      </div>
      <v-skeleton-loader type="text" class="mb-4" />
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
      <zw-notice v-if="store.farmBlocked" type="warning" icon="domain" :title="`${home.farm?.name || '养殖场'}${home.farm_status === 'PENDING' ? '还在审核' : '审核未通过'}`">
        {{ home.farm_status === 'PENDING' ? '审核通过后才能登记生产记录。' : '请按审核意见修改资料后重新提交。' }}
        <template #action>
          <a @click="router.push({ name: 'WU-F01', query: { action: 'audit' } })">查看审核结果</a>
        </template>
      </zw-notice>

      <template v-else>
        <div class="zw-grid-4 mb-4">
          <zw-stat
            class="wb-stat-link"
            label="存栏"
            icon="fence"
            :value="statStock"
            :unit="stockUnit"
            :foot="stockFoot"
            tone="primary"
            @click="router.push({ name: 'WU-L01' })"
          />
          <zw-stat
            class="wb-stat-link"
            label="今日待办"
            icon="checklist"
            :value="statTodo"
            unit="项"
            :foot="todoFoot"
            @click="goCalendarToday"
          />
          <zw-stat
            class="wb-stat-link"
            label="预警"
            icon="warning"
            :value="statWarning"
            unit="项"
            :foot="warningFoot"
            tone="warning"
            @click="goCalendarToday"
          />
          <zw-stat
            class="wb-stat-link"
            label="待收款"
            icon="payments"
            :value="statReceivable"
            unit="元"
            :foot="receivableFoot"
            @click="router.push({ name: 'WU-E01' })"
          />
        </div>

        <zw-notice type="ai" icon="auto_awesome" class="mb-4 wb-ai">
          问问 AI 养殖助手<template v-if="home.ai_hint"> · {{ home.ai_hint }}</template>
          <template #action><a @click="askAi">去提问</a></template>
        </zw-notice>

        <div class="wb-main">
          <zw-card title="今天和已逾期" :sub="`${total} 项`" flush class="wb-todo">
            <template #extra>
              <zw-seg
                size="small"
                :model-value="query.filter"
                :items="filterItems"
                @update:model-value="onFilterValue"
              />
            </template>

            <zw-empty
              v-if="noPen"
              icon="fence"
              title="还没有栏舍和批次"
              desc="先新增栏舍，再登记批次入栏，系统会按批次生成待办。"
            >
              <v-btn color="primary" @click="router.push({ name: 'WU-L02', query: { action: 'create' } })">新增栏舍</v-btn>
            </zw-empty>

            <v-skeleton-loader v-else-if="todoStatus === 'loading'" type="table-row@5" />

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
              <v-btn v-if="todoStatus === 'filtered_empty'" variant="outlined" @click="store.setFilter('ALL')">清除筛选</v-btn>
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
                <template #item.who="{ item }">{{ item.assignee_text || '—' }}</template>
                <template #item.status="{ item }">
                  <zw-tag :type="statusTag(item).type" :icon="statusTag(item).icon">{{ statusTag(item).text }}</zw-tag>
                </template>
                <template #item.op="{ item }">
                  <span class="zw-cell-actions"><a @click.stop="onAction(item)">{{ actionLabel(item) }}</a></span>
                </template>
              </v-data-table>
              <div class="zw-table-foot zw-table-foot--in">
                <span class="zw-faint body-s">已完成的待办在养殖日历中查看</span>
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

          <div class="wb-side zw-stack">
            <zw-card v-if="reminders.length" title="生产提醒">
              <div class="zw-stack wb-reminders">
                <zw-notice
                  v-for="item in reminders"
                  :key="item.todo_id"
                  type="warning"
                  :icon="todoIcon(item)"
                  :title="item.title"
                  class="wb-remind"
                  @click="openTodo(item)"
                >
                  {{ item.sub_text }}
                </zw-notice>
              </div>
            </zw-card>

            <zw-card v-if="penRows.length" title="栏舍占用" :sub="penSub">
              <template #extra>
                <a class="zw-link" @click="router.push({ name: 'WU-L01' })">存栏总览</a>
              </template>
              <div v-for="pen in penRows" :key="pen.pen_id" class="zw-meter">
                <div class="zw-between">
                  <span>{{ pen.pen_name }}</span>
                  <span class="num zw-muted">
                    <zw-tag v-if="!pen.stock_count" size="small" class="mr-2">空栏</zw-tag>
                    {{ formatNumber(pen.stock_count) }} / {{ formatNumber(pen.capacity) }} {{ penUnit }}
                  </span>
                </div>
                <v-progress-linear
                  :model-value="penRatio(pen)"
                  height="6"
                  rounded
                  :color="penRatio(pen) > 95 ? 'warning' : 'primary'"
                />
              </div>
            </zw-card>
          </div>
        </div>
      </template>
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
.wb-reminders {
  gap: 12px;
}
.wb-remind {
  cursor: pointer;
}
.wb-ai {
  align-items: center;
}
</style>
