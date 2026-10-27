<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWorkbenchStore } from '@/stores/today/workbench'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { useNoticeStore } from '@/stores/notice'
import type { TodoItemVO } from '@/api/today'
import { actionLabel, statusTag, subText, todoActionLink, todoIcon, todoTone, useTodayLinks } from './todo'
import TodoDetailDrawer from './components/TodoDetailDrawer.vue'

const route = useRoute()
const router = useRouter()
const store = useWorkbenchStore()
const farm = useFarmStore()
const notice = useNoticeStore()
const { goRegister, goList } = useTodayLinks()
const warning = computed(() => route.query.filter === 'WARNING')
const title = computed(() => warning.value ? '预警合集' : '每日待办')
const drawerOpen = ref(false)
const activeTodoId = ref<number | null>(null)
const headers: any[] = [
  { title: '事项', key: 'title', sortable: false },
  { title: '负责人', key: 'who', sortable: false, width: 110 },
  { title: '状态', key: 'status', sortable: false, width: 110 },
  { title: '操作', key: 'op', sortable: false, width: 96, align: 'end' }
]
const filters = computed(() => farm.role === 'staff'
  ? [{ label: '全部栏舍', value: 'ALL' }, ...store.penCounts.filter((pen) => pen.pen_id).map((pen) => ({
    label: pen.pen_name || '栏舍', value: `PEN:${pen.pen_id}`, count: pen.count ?? 0
  }))]
  : [{ label: '全部', value: 'ALL' }, { label: '作业', value: 'WORK' }, { label: '经营与预警', value: 'BIZ_WARN' }])

const sections = computed(() => {
  const rows = [{
    key: 'today', title: warning.value ? '未解除的预警' : '今日待办', total: store.total,
    open: true, status: store.todoStatus, error: store.todoError, items: store.todos,
    page: store.query.page, size: store.query.size
  }]
  if (!warning.value && (store.overdueCount > 0 || store.overdueExpanded)) rows.push({
    key: 'overdue', title: '已逾期', total: store.overdueCount,
    open: store.overdueExpanded, status: store.overdueStatus, error: store.overdueError,
    items: store.overdueTodos, page: store.overduePage, size: store.overdueSize
  })
  return rows
})

function load(): Promise<void> {
  drawerOpen.value = false
  activeTodoId.value = null
  return store.enterCollection(warning.value ? 'WARNING' : 'ALL')
}
onMounted(load)
watch(() => route.query.filter, load)
useFarmReload(load)

function reload(key: string): void {
  if (key === 'overdue') void store.fetchOverdue()
  else void store.fetchTodos()
}
function page(key: string, value: number): void {
  if (key === 'overdue') store.setOverduePage(value)
  else store.setPage(value)
}
function size(key: string, value: number): void {
  if (key === 'overdue') store.setOverdueSize(value)
  else store.setSize(value)
}
function selectFilter(value: string | number): void {
  const filter = String(value)
  if (filter.startsWith('PEN:')) store.setFilterAndPen('ALL', Number(filter.slice(4)))
  else store.setFilterAndPen(filter, null)
}
function openTodo(item: TodoItemVO): void {
  if (!item.todo_id) return
  activeTodoId.value = item.todo_id
  drawerOpen.value = true
}
function onRow(_event: Event, slot: { item: TodoItemVO }): void { openTodo(slot.item) }
function onAction(item: TodoItemVO): void {
  if (item.todo_type === 'WITHDRAWAL_END') void goList(todoActionLink(item))
  else void goRegister(todoActionLink(item))
}
function onChanged(): void {
  void store.refreshCollection()
  void notice.load()
}
</script>

<template>
  <zw-shell side="user" :active="farm.role === 'staff' ? 'WU-T02' : 'WU-T01'" :crumbs="['工作台', title]">
    <zw-page-head :title="title" :desc="warning ? '集中查看所有尚未解除的预警，恢复正常后自动移出。' : '先处理今天的事项，历史逾期可按需展开。'">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="calendar_month" @click="router.push({ name: 'WU-T03' })">养殖日历</v-btn>
      </template>
    </zw-page-head>
    <div class="zw-stack">
      <zw-card v-for="section in sections" :key="section.key" :title="section.title" :sub="`${section.total} 项`" flush>
        <template #extra>
          <v-btn v-if="section.key === 'overdue'" variant="text" :append-icon="store.overdueExpanded ? 'expand_less' : 'expand_more'"
            :aria-expanded="store.overdueExpanded" aria-controls="collection-overdue" @click="store.toggleOverdue()">
            {{ store.overdueExpanded ? '收起' : '展开查看' }}
          </v-btn>
          <zw-seg v-else-if="!warning" size="small" :items="filters"
            :model-value="store.query.penId ? `PEN:${store.query.penId}` : store.query.filter" @update:model-value="selectFilter" />
        </template>
        <div v-if="section.open" :id="section.key === 'overdue' ? 'collection-overdue' : undefined">
          <v-skeleton-loader v-if="section.status === 'loading'" type="table-row@3" />
          <zw-empty v-else-if="section.status === 'forbidden'" icon="lock" title="无权查看" :desc="section.error" tone="error" />
          <zw-empty v-else-if="section.status === 'error'" icon="cloud_off" title="加载失败" :desc="section.error" tone="error">
            <v-btn variant="outlined" @click="reload(section.key)">重新加载</v-btn>
          </zw-empty>
          <zw-empty v-else-if="!section.items.length" icon="task_alt"
            :title="section.key === 'overdue' ? '没有未完成的逾期事项' : warning ? '没有未解除的预警' : store.filtered ? '没有符合筛选条件的今日待办' : '今天没有未完成待办'">
            <v-btn v-if="section.key === 'today' && !warning && store.filtered" variant="outlined" @click="selectFilter('ALL')">清除筛选</v-btn>
          </zw-empty>
          <template v-else>
            <v-data-table :headers="headers" :items="section.items" :items-per-page="-1" hide-default-footer density="comfortable" @click:row="onRow">
              <template #item.title="{ item }">
                <div class="collection-todo-cell">
                  <span class="zw-ico-tile" :class="'zw-ico-tile--' + todoTone(item)"><zw-icon :name="todoIcon(item)" :size="18" /></span>
                  <div><div class="zw-cell-main">{{ item.title }}</div><div class="zw-cell-sub">{{ subText(item) }}</div></div>
                </div>
              </template>
              <template #item.who="{ item }">{{ item.assignee_text || '—' }}</template>
              <template #item.status="{ item }"><zw-tag :type="statusTag(item).type" :icon="statusTag(item).icon">{{ statusTag(item).text }}</zw-tag></template>
              <template #item.op="{ item }"><span class="zw-cell-actions"><a @click.stop="onAction(item)">{{ actionLabel(item) }}</a></span></template>
            </v-data-table>
            <div class="zw-table-foot zw-table-foot--in">
              <span class="zw-faint body-s">{{ warning ? '处理后自动更新预警状态' : '已完成的事项可在养殖日历中查看' }}</span>
              <zw-pager :total="section.total" :page="section.page" :size="section.size"
                @update:page="page(section.key, $event)" @update:size="size(section.key, $event)" />
            </div>
          </template>
        </div>
      </zw-card>
    </div>
    <TodoDetailDrawer v-model="drawerOpen" :todo-id="activeTodoId" @changed="onChanged" />
  </zw-shell>
</template>

<style scoped>
.collection-todo-cell { display: flex; align-items: center; gap: 12px; padding: 4px 0; }
</style>
