<script setup lang="ts">
import { computed, nextTick, onMounted, reactive } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { dayjs, formatDate, today } from '@zmt/shared'
import { useCalendarStore } from '@/stores/today/calendar'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import type { TodoItemVO, WeeklyDayVO } from '@/api/today'
import {
  actionLabel,
  calendarSubText,
  statusTag,
  timeLabelText,
  todoActionLink,
  todoIcon,
  todoTone,
  useTodayLinks
} from './todo'
import TodoDetailDrawer from './components/TodoDetailDrawer.vue'
import ReminderDrawer from './components/ReminderDrawer.vue'

const route = useRoute()
const farm = useFarmStore()
const store = useCalendarStore()
const { isStaff } = useRole()
const { goRegister, goList } = useTodayLinks()
const {
  view,
  selected,
  dayStatus,
  monthDays,
  weekDays,
  dayItems,
  dayTotal,
  overdueItems,
  status,
  listStatus,
  overdueStatus,
  error,
  listError,
  overdueError,
  home,
  drawerOpen,
  activeTodoId,
  reminderOpen
} = storeToRefs(store)

const expanded = reactive<Record<string, boolean>>({})
const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']

const scope = computed(() => (farm.viewAll ? 'ALL' : 'CURRENT'))
const scopeItems = computed(() => [
  { label: farm.farmName || '当前养殖场', value: 'CURRENT' },
  { label: '全部养殖场', value: 'ALL' }
])
const viewItems = [
  { label: '日', value: 'day' },
  { label: '周', value: 'week' },
  { label: '月', value: 'month' }
]

const staffBanner = computed(() => {
  const names = home.value?.staff_metrics?.pen_names || []
  const scopeText = names.length
    ? names.length > 3
      ? `${names[0]}等 ${names.length} 个栏舍`
      : names.join('、')
    : '你负责的栏舍'
  return `${farm.farmName} · 只显示你负责的 ${scopeText} 的作业待办。登记对应记录后，待办自动完成。`
})

const monthCells = computed(() => {
  const range = store.monthRange
  const first = dayjs(store.selected)
  const start = dayjs(range.start)
  const cells = []
  for (let index = 0; index < range.totalCells; index += 1) {
    const date = start.add(index, 'day')
    const key = date.format('YYYY-MM-DD')
    const data = store.monthMap[key]
    cells.push({
      key,
      date: key,
      d: date.date(),
      other: date.month() !== first.month(),
      isToday: key === today(),
      total: data?.total || 0,
      overdueCount: data?.overdue_count || 0,
      pendingCount: data?.pending_count || 0,
      closedCount: data?.closed_count || 0,
      overdueTitles: data?.overdue_titles || []
    })
  }
  return cells
})

const weekStrip = computed(() => {
  const start = dayjs(store.weekStart)
  return Array.from({ length: 7 }, (_, index) => {
    const date = start.add(index, 'day').format('YYYY-MM-DD')
    const data = store.weekMap[date]
    return {
      date,
      d: start.add(index, 'day').date(),
      weekday: WEEK_LABELS[start.add(index, 'day').day()],
      isToday: date === today(),
      total: data?.total || 0,
      overdueCount: data?.overdue_count || 0,
      pendingCount: data?.pending_count || 0,
      closedCount: data?.closed_count || 0
    }
  })
})

const weekGroups = computed(() =>
  weekDays.value.filter((day) => (day.total || 0) > 0 && (day.items || []).length > 0)
)

const weekHasItems = computed(() => weekGroups.value.length > 0)

function selectDate(date: string): void {
  if (view.value === 'week') {
    store.select(date)
    void nextTick(() => {
      document.getElementById(`week-day-${date}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
    return
  }
  store.select(date)
}

function setScope(value: string): void {
  const all = value === 'ALL'
  if (farm.viewAll === all) return
  farm.setViewAll(all)
}

function onScopeValue(value: string | number): void {
  setScope(String(value))
}

function onViewValue(value: string | number): void {
  store.setView(String(value) as 'day' | 'week' | 'month')
}

function onDayStatusValue(value: string | number): void {
  store.setDayStatus(String(value) as 'ALL' | 'UNFINISHED')
}

function toggleGroup(date: string): void {
  expanded[date] = !expanded[date]
}

function visibleItems(day: WeeklyDayVO): TodoItemVO[] {
  const items = day.items || []
  return expanded[day.date || ''] ? items : items.slice(0, 5)
}

function restCount(day: WeeklyDayVO): number {
  return Math.max(0, (day.total || (day.items || []).length) - 5)
}

function hasRest(day: WeeklyDayVO): boolean {
  return (day.items || []).length > 5
}

function categoryTime(item: TodoItemVO): string {
  const category = (item.category_text || '').replace('类', '')
  return `${category || '待办'} · ${item.display_status === 'DONE' ? '已完成' : item.display_status === 'IGNORED' ? '已忽略' : timeLabelText(item)}`
}

function personText(item: TodoItemVO): string {
  if (item.display_status === 'DONE') return item.completed_text || '—'
  if (item.display_status === 'IGNORED') return item.ignore_text || item.ignore_reason || '系统自动忽略'
  return item.assignee_text || '—'
}

function isMuted(item: TodoItemVO): boolean {
  return item.display_status === 'DONE' || item.display_status === 'IGNORED'
}

function openItem(item: TodoItemVO): void {
  if (!item.todo_id) return
  store.openTodo(item.todo_id)
}

function onItemAction(item: TodoItemVO): void {
  if (item.todo_type === 'WITHDRAWAL_END') void goList(todoActionLink(item))
  else void goRegister(todoActionLink(item))
}

function onWeekAction(item: TodoItemVO): void {
  if (item.display_status === 'PENDING' || item.display_status === 'OVERDUE') onItemAction(item)
  else openItem(item)
}

function onChanged(): void {
  store.onTodoChanged()
}

function applyRouteQuery(): void {
  const date = String(route.query.date || '')
  if (/^\d{4}-\d{2}-\d{2}$/.test(date)) store.selected = date
  const todoId = Number(route.query.todo_id)
  if (Number.isFinite(todoId) && todoId > 0) store.openTodo(todoId)
}

onMounted(async () => {
  if (isStaff.value && farm.viewAll) farm.setViewAll(false)
  await store.init()
  const date = String(route.query.date || '')
  if (/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    store.selected = date
    await store.fetchCurrent()
  }
  applyRouteQuery()
})

useFarmReload(() => store.reload())
</script>

<template>
  <zw-shell side="user" active="WU-T03" :all-farms="farm.viewAll" :crumbs="['养殖日历']">
    <zw-page-head
      title="养殖日历"
      :desc="isStaff ? '只显示你负责栏舍的作业待办，点待办查看详情' : '点待办查看详情，点“去登记”直接进入登记'"
    >
      <template #actions>
        <v-btn variant="outlined" prepend-icon="settings" @click="reminderOpen = true">提醒设置</v-btn>
      </template>
    </zw-page-head>

    <div class="zw-toolbar">
      <div class="zw-toolbar-l">
        <zw-seg v-if="!isStaff" :model-value="scope" :items="scopeItems" @update:model-value="onScopeValue" />
        <zw-seg :model-value="view" :items="viewItems" @update:model-value="onViewValue" />
        <span class="t03-period">
          <button class="zw-icon-btn" type="button" aria-label="上一个" @click="store.shift(-1)">
            <zw-icon name="chevron_left" :size="20" />
          </button>
          <span class="t03-period-text">{{ store.periodText }}</span>
          <button class="zw-icon-btn" type="button" aria-label="下一个" @click="store.shift(1)">
            <zw-icon name="chevron_right" :size="20" />
          </button>
        </span>
        <v-btn variant="outlined" size="small" @click="store.goToday()">今天</v-btn>
      </div>
      <div class="zw-toolbar-r t03-legend">
        <span><i class="t03-dot t03-dot--late"></i>有已逾期</span>
        <span><i class="t03-dot t03-dot--todo"></i>有待处理</span>
        <span><i class="t03-dot t03-dot--done"></i>已完成或已忽略</span>
      </div>
    </div>

    <template v-if="view === 'month'">
      <div class="t03-main">
        <zw-card flush class="t03-cal">
          <div class="t03-wh">
            <div v-for="week in ['一', '二', '三', '四', '五', '六', '日']" :key="week">{{ week }}</div>
          </div>

          <v-skeleton-loader v-if="status === 'loading'" type="image" class="t03-skeleton" />

          <zw-empty
            v-else-if="status === 'error' || status === 'forbidden'"
            :icon="status === 'forbidden' ? 'lock' : 'cloud_off'"
            :title="status === 'forbidden' ? '无权查看' : '待办加载失败'"
            :desc="error"
            :tone="status === 'forbidden' ? undefined : 'error'"
          >
            <v-btn v-if="status === 'error'" variant="outlined" prepend-icon="refresh" @click="store.fetchMonth()">重新加载</v-btn>
          </zw-empty>

          <div v-else class="t03-grid" :style="{ gridTemplateRows: `repeat(${monthCells.length / 7}, minmax(0, 1fr))` }">
            <div
              v-for="cell in monthCells"
              :key="cell.key"
              class="t03-cell"
              :class="{ other: cell.other, sel: cell.date === selected && !cell.other, today: cell.isToday }"
              @click="selectDate(cell.date)"
            >
              <div style="min-width: 0">
                <span class="t03-d" :class="{ today: cell.isToday }">{{ cell.d }}</span>
                <span v-if="cell.isToday" class="t03-d-tag">今天</span>
                <div v-if="cell.overdueTitles.length" class="t03-note">
                  <div v-for="(title, index) in cell.overdueTitles.slice(0, 2)" :key="index">{{ title }}</div>
                </div>
              </div>
              <div v-if="cell.overdueCount || cell.pendingCount || cell.closedCount" class="t03-cnts">
                <span v-if="cell.overdueCount" class="t03-cnt"><i class="t03-dot t03-dot--late"></i>{{ cell.overdueCount }}</span>
                <span v-if="cell.pendingCount" class="t03-cnt"><i class="t03-dot t03-dot--todo"></i>{{ cell.pendingCount }}</span>
                <span v-if="cell.closedCount" class="t03-cnt"><i class="t03-dot t03-dot--done"></i>{{ cell.closedCount }}</span>
              </div>
            </div>
          </div>
        </zw-card>

        <zw-card :title="store.dayTitle" :sub="`${dayTotal} 项`" flush class="t03-day">
          <template #extra>
            <zw-seg
              size="small"
              :model-value="dayStatus"
              :items="[{ label: '全部', value: 'ALL' }, { label: '未完成', value: 'UNFINISHED' }]"
              @update:model-value="onDayStatusValue"
            />
          </template>

          <v-skeleton-loader v-if="listStatus === 'loading'" type="list-item-two-line@6" />

          <zw-empty
            v-else-if="listStatus === 'error' || listStatus === 'forbidden'"
            :icon="listStatus === 'forbidden' ? 'lock' : 'cloud_off'"
            :title="listStatus === 'forbidden' ? '无权查看' : '待办加载失败'"
            :desc="listError"
            tone="error"
          >
            <v-btn v-if="listStatus === 'error'" variant="outlined" prepend-icon="refresh" @click="store.fetchDayItems()">重新加载</v-btn>
          </zw-empty>

          <zw-empty
            v-else-if="listStatus === 'empty' || listStatus === 'filtered_empty'"
            icon="task_alt"
            :title="listStatus === 'filtered_empty' ? '这一天没有未完成的待办' : '这一天没有待办。'"
            :desc="isStaff ? '你负责的栏舍在这一天没有作业待办。' : '换一个日期，或点“今天”回到今天。'"
          />

          <div v-else class="t03-list">
            <div
              v-for="item in dayItems"
              :key="item.todo_id"
              class="t03-item"
              :class="{ 'is-done': item.display_status === 'DONE', 'is-ign': item.display_status === 'IGNORED' }"
              @click="openItem(item)"
            >
              <span class="zw-ico-tile" :class="'zw-ico-tile--' + todoTone(item)">
                <zw-icon :name="todoIcon(item)" :size="18" />
              </span>
              <div class="t03-item-main">
                <div class="t03-item-title">{{ item.title }}</div>
                <div class="t03-item-sub">
                  <zw-tag v-if="farm.viewAll && item.farm_name" type="farm" size="small">{{ item.farm_name }}</zw-tag>
                  <span>{{ calendarSubText(item) }}</span>
                </div>
              </div>
              <div class="t03-item-r">
                <zw-tag :type="statusTag(item).type" :icon="statusTag(item).icon">{{ statusTag(item).text }}</zw-tag>
                <a
                  v-if="item.display_status === 'PENDING' || item.display_status === 'OVERDUE'"
                  @click.stop="onItemAction(item)"
                >{{ actionLabel(item) }}</a>
              </div>
            </div>
          </div>
        </zw-card>
      </div>
    </template>

    <template v-else-if="view === 'week'">
      <zw-notice v-if="farm.viewAll" class="mb-4">全部养殖场只包含你有权查看的待办。员工只计入负责栏舍的作业类待办。</zw-notice>

      <zw-card flush>
        <div class="t03-strip">
          <div
            v-for="day in weekStrip"
            :key="day.date"
            class="t03-strip-c"
            :class="{ sel: day.date === selected }"
            @click="selectDate(day.date)"
          >
            <div class="t03-strip-w">周{{ day.weekday }}</div>
            <div class="t03-strip-d">
              <span class="t03-d" :class="{ today: day.isToday }">{{ day.d }}</span>
              <span class="t03-strip-n">{{ day.total }} 项</span>
            </div>
            <div v-if="day.overdueCount || day.pendingCount || day.closedCount" class="t03-cnts">
              <span v-if="day.overdueCount" class="t03-cnt"><i class="t03-dot t03-dot--late"></i>{{ day.overdueCount }}</span>
              <span v-if="day.pendingCount" class="t03-cnt"><i class="t03-dot t03-dot--todo"></i>{{ day.pendingCount }}</span>
              <span v-if="day.closedCount" class="t03-cnt"><i class="t03-dot t03-dot--done"></i>{{ day.closedCount }}</span>
            </div>
          </div>
        </div>

        <v-skeleton-loader v-if="status === 'loading'" type="table-row@5" />

        <zw-empty
          v-else-if="status === 'error' || status === 'forbidden'"
          :icon="status === 'forbidden' ? 'lock' : 'cloud_off'"
          :title="status === 'forbidden' ? '无权查看' : '待办加载失败'"
          :desc="error"
          tone="error"
        >
          <v-btn v-if="status === 'error'" variant="outlined" prepend-icon="refresh" @click="store.fetchWeek()">重新加载</v-btn>
        </zw-empty>

        <zw-empty v-else-if="!weekHasItems" icon="task_alt" title="这一周没有待办。" desc="换一周，或点“今天”回到今天。" />

        <v-table v-else class="t03-tbl" hover>
          <thead>
            <tr>
              <th>待办</th>
              <th style="width: 130px">养殖场</th>
              <th style="width: 160px">类别 · 时间</th>
              <th style="width: 150px">负责人</th>
              <th style="width: 110px">状态</th>
              <th style="width: 100px" class="text-end">操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="day in weekGroups" :key="day.date">
              <tr :id="`week-day-${day.date}`" class="t03-grp">
                <td colspan="6">
                  {{ formatDate(day.date) }}<template v-if="day.is_today"> · 今天</template>
                  <span class="t03-grp-n">{{ day.total }} 项</span>
                </td>
              </tr>
              <tr v-for="item in visibleItems(day)" :key="item.todo_id" :class="{ 'zw-row-muted': isMuted(item) }">
                <td>
                  <div class="t03-tbl-title">
                    <span class="zw-ico-tile" :class="'zw-ico-tile--' + todoTone(item)">
                      <zw-icon :name="todoIcon(item)" :size="18" />
                    </span>
                    <span :class="{ 'zw-cell-main': !isMuted(item) }" :style="item.display_status === 'DONE' ? 'text-decoration: line-through' : ''">{{ item.title }}</span>
                  </div>
                </td>
                <td><zw-tag type="farm" size="small">{{ item.farm_name }}</zw-tag></td>
                <td class="zw-muted">{{ categoryTime(item) }}</td>
                <td class="zw-muted">{{ personText(item) }}</td>
                <td><zw-tag :type="statusTag(item).type" :icon="statusTag(item).icon">{{ statusTag(item).text }}</zw-tag></td>
                <td class="text-end">
                  <span class="zw-cell-actions">
                    <a @click.stop="onWeekAction(item)">{{ item.display_status === 'PENDING' || item.display_status === 'OVERDUE' ? actionLabel(item) : '详情' }}</a>
                  </span>
                </td>
              </tr>
              <tr v-if="hasRest(day)" class="t03-more">
                <td colspan="6">
                  <a class="zw-link d-inline-flex align-center ga-1" @click="toggleGroup(day.date || '')">
                    <zw-icon :name="expanded[day.date || ''] ? 'expand_less' : 'expand_more'" :size="18" />
                    {{ expanded[day.date || ''] ? '收起' : `展开其余 ${restCount(day)} 项` }}
                  </a>
                </td>
              </tr>
            </template>
          </tbody>
        </v-table>
      </zw-card>
    </template>

    <template v-else>
      <zw-notice v-if="isStaff" class="mb-4">{{ staffBanner }}</zw-notice>

      <zw-card flush class="mb-4">
        <div class="t03-strip">
          <div
            v-for="day in weekStrip"
            :key="day.date"
            class="t03-strip-c"
            :class="{ sel: day.date === selected }"
            @click="selectDate(day.date)"
          >
            <div class="t03-strip-w">周{{ day.weekday }}</div>
            <div class="t03-strip-d">
              <span class="t03-d" :class="{ today: day.isToday }">{{ day.d }}</span>
              <span class="t03-strip-n">{{ day.total }} 项</span>
            </div>
            <div v-if="day.overdueCount || day.pendingCount || day.closedCount" class="t03-cnts">
              <span v-if="day.overdueCount" class="t03-cnt"><i class="t03-dot t03-dot--late"></i>{{ day.overdueCount }}</span>
              <span v-if="day.pendingCount" class="t03-cnt"><i class="t03-dot t03-dot--todo"></i>{{ day.pendingCount }}</span>
              <span v-if="day.closedCount" class="t03-cnt"><i class="t03-dot t03-dot--done"></i>{{ day.closedCount }}</span>
            </div>
          </div>
        </div>
      </zw-card>

      <div class="t03-day-main">
        <zw-card :title="store.dayTitle" :sub="`${dayTotal} 项`" flush class="t03-timeline-card">
          <v-skeleton-loader v-if="listStatus === 'loading'" type="list-item-two-line@5" />

          <zw-empty
            v-else-if="listStatus === 'error' || listStatus === 'forbidden'"
            :icon="listStatus === 'forbidden' ? 'lock' : 'cloud_off'"
            :title="listStatus === 'forbidden' ? '无权查看' : '待办加载失败'"
            :desc="listError"
            tone="error"
          >
            <v-btn v-if="listStatus === 'error'" variant="outlined" prepend-icon="refresh" @click="store.fetchDayItems()">重新加载</v-btn>
          </zw-empty>

          <zw-empty
            v-else-if="listStatus === 'empty' || listStatus === 'filtered_empty'"
            icon="task_alt"
            title="这一天没有待办。"
            :desc="isStaff ? '你负责的栏舍在这一天没有作业待办。' : '换一个日期，或点“今天”回到今天。'"
          />

          <div v-else class="t03-tl">
            <div v-for="group in store.dayGroups" :key="group.time" class="t03-tl-row">
              <div class="t03-tl-time">{{ group.time }}</div>
              <div class="t03-tl-items">
                <div
                  v-for="item in group.items"
                  :key="item.todo_id"
                  class="t03-item"
                  :class="{ 'is-done': item.display_status === 'DONE', 'is-ign': item.display_status === 'IGNORED' }"
                  @click="openItem(item)"
                >
                  <span class="zw-ico-tile" :class="'zw-ico-tile--' + todoTone(item)">
                    <zw-icon :name="todoIcon(item)" :size="18" />
                  </span>
                  <div class="t03-item-main">
                    <div class="t03-item-title">{{ item.title }}</div>
                    <div class="t03-item-sub">
                      <zw-tag v-if="farm.viewAll && item.farm_name" type="farm" size="small">{{ item.farm_name }}</zw-tag>
                      <span>{{ calendarSubText(item) }}</span>
                    </div>
                  </div>
                  <zw-tag :type="statusTag(item).type" :icon="statusTag(item).icon">{{ statusTag(item).text }}</zw-tag>
                  <span class="t03-item-op">
                    <a v-if="item.display_status === 'PENDING' || item.display_status === 'OVERDUE'" @click.stop="onItemAction(item)">{{ actionLabel(item) }}</a>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </zw-card>

        <div class="t03-day-side zw-stack">
          <zw-card v-if="overdueItems.length" title="已逾期" :sub="`${overdueItems.length} 项`" flush>
            <v-skeleton-loader v-if="overdueStatus === 'loading'" type="list-item-two-line@2" />
            <zw-empty v-else-if="overdueStatus === 'error'" icon="cloud_off" title="待办加载失败" :desc="overdueError" tone="error" />
            <div v-else>
              <div
                v-for="item in overdueItems"
                :key="item.todo_id"
                class="t03-item"
                style="border-bottom: 0"
                @click="openItem(item)"
              >
                <span class="zw-ico-tile" :class="'zw-ico-tile--' + todoTone(item)">
                  <zw-icon :name="todoIcon(item)" :size="18" />
                </span>
                <div class="t03-item-main">
                  <div class="t03-item-title">{{ item.title }}</div>
                  <div class="t03-item-sub">{{ item.sub_text }}</div>
                </div>
                <div class="t03-item-r">
                  <zw-tag type="error" icon="schedule">已逾期</zw-tag>
                  <a @click.stop="onItemAction(item)">{{ actionLabel(item) }}</a>
                </div>
              </div>
            </div>
          </zw-card>
          <zw-notice v-if="isStaff" icon="info">只在查看今天时汇总之前各天仍未完成的待办。需要调整计划时，请联系所有者或场长。</zw-notice>
        </div>
      </div>
    </template>

    <TodoDetailDrawer v-model="drawerOpen" :todo-id="activeTodoId" @changed="onChanged" />
    <ReminderDrawer v-model="reminderOpen" />
  </zw-shell>
</template>

<style scoped>
.t03-period {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.t03-period-text {
  min-width: 132px;
  text-align: center;
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}
.t03-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-2);
}
.t03-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.t03-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.t03-dot--late {
  background: var(--error);
}
.t03-dot--todo {
  background: var(--primary);
}
.t03-dot--done {
  background: var(--control-border);
}
.t03-main {
  display: flex;
  gap: var(--space-4);
  align-items: stretch;
  height: calc(100vh - 244px);
  min-height: 520px;
}
.t03-cal {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.t03-cal :deep(.zw-card-body--flush) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.t03-wh {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  height: 36px;
  align-items: center;
  background: var(--fill-1);
  border-bottom: 1px solid var(--line-1);
  font-size: 12px;
  color: var(--text-2);
  flex-shrink: 0;
}
.t03-wh > div {
  padding: 0 12px;
}
.t03-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}
.t03-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 8px 10px 10px 12px;
  border-right: 1px solid var(--line-1);
  border-bottom: 1px solid var(--line-1);
  cursor: pointer;
  min-width: 0;
}
.t03-cell:nth-child(7n) {
  border-right: 0;
}
.t03-cell:nth-last-child(-n + 7) {
  border-bottom: 0;
}
.t03-cell:hover {
  background: var(--fill-1);
}
.t03-cell.sel {
  background: var(--primary-subtle);
  box-shadow: inset 0 0 0 1px var(--primary);
}
.t03-cell.other .t03-d {
  color: var(--text-disabled);
}
.t03-cell.other .t03-cnts {
  opacity: 0.55;
}
.t03-d {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  margin-left: -6px;
  border-radius: 14px;
  font-size: 14px;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}
.t03-d.today {
  background: var(--primary);
  color: var(--on-primary);
  font-weight: 600;
}
.t03-d-tag {
  margin-left: 4px;
  font-size: 12px;
  color: var(--primary);
  font-weight: 500;
}
.t03-cnts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-2);
}
.t03-cnt {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-variant-numeric: tabular-nums;
}
.t03-note {
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--error);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.t03-skeleton {
  flex: 1;
  min-height: 0;
}
.t03-day {
  width: 400px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.t03-day :deep(.zw-card-body--flush) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.t03-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.t03-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px 10px 20px;
  border-bottom: 1px solid var(--line-1);
  cursor: pointer;
  min-width: 0;
}
.t03-item:hover {
  background: var(--fill-1);
}
.t03-item-main {
  flex: 1;
  min-width: 0;
}
.t03-item-title {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.t03-item-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.t03-item.is-done .t03-item-title {
  text-decoration: line-through;
  color: var(--text-3);
  font-weight: 400;
}
.t03-item.is-ign .t03-item-title {
  color: var(--text-3);
  font-weight: 400;
}
.t03-item-r {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}
.t03-item-r a {
  font-size: 12px;
  line-height: 18px;
  color: var(--primary);
  cursor: pointer;
}
.t03-item-op {
  width: 56px;
  flex-shrink: 0;
  text-align: right;
  font-size: 12px;
  line-height: 18px;
}
.t03-item-op a {
  color: var(--primary);
  cursor: pointer;
}
.t03-strip {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}
.t03-strip-c {
  padding: 10px 16px 12px;
  border-right: 1px solid var(--line-1);
  cursor: pointer;
}
.t03-strip-c:last-child {
  border-right: 0;
}
.t03-strip-c.sel {
  background: var(--primary-subtle);
  box-shadow: inset 0 -2px 0 var(--primary);
}
.t03-strip-w {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.t03-strip-d {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0 6px;
  font-size: 16px;
  line-height: 28px;
  font-weight: 600;
  color: var(--text-1);
}
.t03-strip-d .t03-d {
  margin-left: -2px;
  font-size: 16px;
}
.t03-strip-n {
  font-size: 12px;
  color: var(--text-3);
  font-weight: 400;
  margin-left: auto;
}
.t03-tbl :deep(td) {
  height: 52px !important;
}
.t03-grp td {
  height: 40px !important;
  background: var(--fill-1);
  font-weight: 600;
}
.t03-grp .t03-grp-n {
  font-weight: 400;
  color: var(--text-3);
  margin-left: 8px;
}
.t03-more td {
  height: 40px !important;
}
.t03-tbl-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.t03-tl {
  padding: 4px 0 8px;
}
.t03-tl-row {
  display: flex;
  gap: 0;
}
.t03-tl-time {
  width: 72px;
  flex-shrink: 0;
  padding: 14px 0 0 20px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}
.t03-tl-items {
  flex: 1;
  min-width: 0;
  border-left: 1px solid var(--line-1);
}
.t03-tl-items .t03-item {
  padding-left: 16px;
}
.t03-day-main {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
}
.t03-timeline-card {
  flex: 1 1 0;
  min-width: 0;
}
.t03-day-side {
  width: 360px;
  flex-shrink: 0;
}
</style>
