import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkbenchStore } from '../apps/user/src/stores/today/workbench'
import { useFarmStore } from '../apps/user/src/stores/farm'
import WorkbenchView from '../apps/user/src/views/today/WorkbenchView.vue'
import WorkbenchStaffView from '../apps/user/src/views/today/WorkbenchStaffView.vue'
import TodoCollectionView from '../apps/user/src/views/today/TodoCollectionView.vue'
import { todayRoutes } from '../apps/user/src/router/today'

const api = vi.hoisted(() => ({ fetchHome: vi.fn(), fetchPens: vi.fn(), fetchTodayTodos: vi.fn() }))
const navigation = vi.hoisted(() => ({ push: vi.fn(), route: { query: { filter: 'ALL' } } }))
vi.mock('../apps/user/src/api/today', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: navigation.push }), useRoute: () => navigation.route }))
vi.mock('../apps/user/src/stores/notice', () => ({ useNoticeStore: () => ({ load: vi.fn() }) }))
vi.mock('../apps/user/src/views/today/components/TodoDetailDrawer.vue', () => ({
  default: { name: 'TodoDetailDrawer', props: ['modelValue', 'todoId'], template: '<div data-detail-drawer />' }
}))

let todayCount = 41
function response({ page = 1, size = 20, filter = 'ALL' }: { page?: number; size?: number; filter?: string }) {
  const count = filter === 'OVERDUE' ? 31 : filter === 'WARNING' ? 2 : todayCount
  const prefix = filter === 'OVERDUE' ? '历史逾期' : filter === 'WARNING' ? '早前库存预警' : '今日事项'
  const all = Array.from({ length: count }, (_, index) => ({
    todo_id: index + 1, title: `${prefix}${index + 1}`, category: filter === 'WARNING' ? 'WARNING' : 'WORK',
    display_status: filter === 'OVERDUE' ? 'OVERDUE' : 'PENDING'
  }))
  return { list: all.slice((page - 1) * size, page * size), total: count, page, size, tab_counts: { ALL: todayCount, OVERDUE: 31 }, pen_counts: [] }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  navigation.route = reactive({ query: { filter: 'ALL' } })
  todayCount = 41
  const farm = useFarmStore()
  farm.$patch({ currentFarmId: 1, farms: [{ farm_id: 1, role: 'OWNER' } as never] })
  api.fetchHome.mockResolvedValue({ role: 'OWNER', has_pen: true, ai_hint: '3号育肥舍', owner_metrics: { overdue_count: 31 }, staff_metrics: { pen_count: 1 } })
  api.fetchPens.mockResolvedValue([{ pen_id: 1, pen_type: 'FATTENING' }])
  api.fetchTodayTodos.mockImplementation((query) => Promise.resolve(response(query)))
})

describe('today collection loading and scope', () => {
  it('shows three today items and requests historical warnings directly, without loading overdue rows', async () => {
    const store = useWorkbenchStore()
    await store.enterHome()
    expect(store.todos).toHaveLength(3)
    expect(store.total).toBe(41)
    expect(store.overdueExpanded).toBe(false)
    expect(api.fetchTodayTodos.mock.calls.map(([query]) => query.filter)).toEqual(['ALL', 'WARNING'])
    expect(store.reminders[0].title).toBe('早前库存预警1')
    expect(api.fetchTodayTodos.mock.calls[1][0]).toEqual({ page: 1, size: 3, filter: 'WARNING' })
  })

  it('loads overdue only on expansion and resets it on reentry or farm reload', async () => {
    const store = useWorkbenchStore()
    await store.enterHome()
    await store.toggleOverdue()
    expect(store.overdueTodos).toHaveLength(5)
    expect(store.overdueExpanded).toBe(true)
    await store.fetchAll()
    expect(store.overdueExpanded).toBe(true)
    store.query.filter = 'OVERDUE'
    store.query.penId = 8
    await store.enterHome()
    expect(store.query).toEqual({ page: 1, size: 3, filter: 'ALL', penId: null })
    expect(store.overdueExpanded).toBe(false)
    expect(store.overdueTodos).toEqual([])
  })

  it('preserves independent daily and overdue pages during action refresh', async () => {
    const store = useWorkbenchStore()
    await store.enterCollection()
    expect(store.todos).toHaveLength(20)
    await store.toggleOverdue()
    store.setPage(2)
    store.setOverduePage(2)
    await flushPromises()
    await store.refreshCollection()
    expect(store.query.page).toBe(2)
    expect(store.overduePage).toBe(2)
    expect(store.overdueExpanded).toBe(true)
    expect(store.todos[0].title).toBe('今日事项21')
    expect(store.overdueTodos[0].title).toBe('历史逾期21')
  })

  it('returns to the last valid page after the final item on a page is handled', async () => {
    const store = useWorkbenchStore()
    todayCount = 21
    await store.enterCollection()
    store.setPage(2)
    await flushPromises()
    expect(store.todos).toHaveLength(1)
    todayCount = 20
    await store.refreshCollection()
    expect(store.query.page).toBe(1)
    expect(store.todos).toHaveLength(20)
    expect(store.todoStatus).toBe('ready')
  })

  it('requests WARNING alone for the warning collection', async () => {
    const store = useWorkbenchStore()
    await store.enterCollection('WARNING')
    expect(api.fetchTodayTodos).toHaveBeenCalledTimes(1)
    expect(api.fetchTodayTodos.mock.calls[0][0]).toMatchObject({ filter: 'WARNING', page: 1, size: 20 })
    expect(store.todos.every((item) => item.category === 'WARNING')).toBe(true)
  })

  it('never requests warnings for staff, including a direct warning URL', async () => {
    useFarmStore().farms[0].role = 'STAFF'
    const store = useWorkbenchStore()
    await store.enterHome()
    expect(api.fetchTodayTodos.mock.calls.map(([query]) => query.filter)).toEqual(['ALL'])
    expect(api.fetchPens).not.toHaveBeenCalled()
    await store.enterCollection('WARNING')
    expect(api.fetchTodayTodos).toHaveBeenCalledTimes(1)
    expect(store.todoStatus).toBe('forbidden')
    expect(store.todos).toEqual([])
  })

  it('ignores a delayed old-farm response after a new collection entry', async () => {
    let finishOld!: (value: unknown) => void
    api.fetchTodayTodos.mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve }))
    const store = useWorkbenchStore()
    const old = store.enterCollection()
    await store.enterCollection('WARNING')
    finishOld(response({ filter: 'ALL' }))
    await old
    expect(store.query.filter).toBe('WARNING')
    expect(store.todos[0].title).toBe('早前库存预警1')
  })
})

const stubs = {
  'zw-shell': { template: '<div><slot /></div>' },
  'zw-page-head': { props: ['title'], template: '<header>{{ title }}<slot name="actions" /></header>' },
  'zw-card': { props: ['title'], template: '<section><h2>{{ title }}</h2><slot name="extra" /><slot /></section>' },
  'zw-stat': { props: ['label', 'value'], emits: ['click'], template: '<button :data-stat="label" @click="$emit(\'click\')">{{ label }} {{ value }}</button>' },
  'zw-empty': { props: ['title'], template: '<div>{{ title }}<slot /></div>' },
  'zw-notice': { template: '<aside><slot /><slot name="action" /></aside>' },
  'v-btn': { template: '<button><slot /></button>' },
  'v-data-table': { name: 'VDataTable', props: ['items', 'itemsPerPage'], emits: ['click:row'], template: '<div data-table><button v-for="item in items" :key="item.todo_id" @click="$emit(\'click:row\', $event, { item })">{{ item.title }}</button></div>' },
  'zw-icon': true, 'zw-tag': true, 'zw-pager': true, 'zw-seg': true, 'v-progress-linear': true, 'v-skeleton-loader': true
}

describe('today collection page interactions', () => {
  it('keeps the warning metric at zero after resolution even when other reminders remain', async () => {
    api.fetchHome.mockResolvedValue({ role: 'OWNER', has_pen: true,
      owner_metrics: { warning_count: 0 }, reminder_total: 2 })
    api.fetchTodayTodos.mockImplementation((query) => Promise.resolve(query.filter === 'WARNING'
      ? { list: [], total: 0, page: 1, size: 20 } : response(query)))
    const view = mount(WorkbenchView, { global: { stubs } })
    await flushPromises()
    expect(view.get('[data-stat="预警"]').text()).toBe('预警 0')
    await view.get('[data-stat="预警"]').trigger('click')
    expect(navigation.push).toHaveBeenLastCalledWith({ name: 'WU-T09', query: { filter: 'WARNING' } })
    navigation.route.query.filter = 'WARNING'
    const collection = mount(TodoCollectionView, { global: { stubs } })
    await flushPromises()
    expect(useWorkbenchStore().total).toBe(0)
    expect(collection.findAll('[data-table] button')).toHaveLength(0)
    collection.unmount()
    view.unmount()
  })

  it('links home metrics to the correct collection and removes the AI banner', async () => {
    const view = mount(WorkbenchView, { global: { stubs } })
    await flushPromises()
    expect(view.text()).not.toContain('问问 AI')
    expect(view.text()).not.toContain('3号育肥舍')
    expect(view.findAll('[data-table] button')).toHaveLength(3)
    expect(view.find('#home-overdue').exists()).toBe(false)
    await view.get('[data-stat="预警"]').trigger('click')
    expect(navigation.push).toHaveBeenLastCalledWith({ name: 'WU-T09', query: { filter: 'WARNING' } })
    await view.get('[data-stat="今日待办"]').trigger('click')
    expect(navigation.push).toHaveBeenLastCalledWith({ name: 'WU-T09', query: { filter: 'ALL' } })
    await view.get('button[aria-controls="home-overdue"]').trigger('click')
    await flushPromises()
    expect(view.get('#home-overdue').text()).toContain('历史逾期1')
    await view.get('[data-table] button').trigger('click')
    expect(view.findComponent({ name: 'TodoDetailDrawer' }).props()).toMatchObject({ modelValue: true, todoId: 1 })
    view.unmount()
  })

  it('keeps staff overdue collapsed and daily navigation separate from the calendar', async () => {
    useFarmStore().farms[0].role = 'STAFF'
    const view = mount(WorkbenchStaffView, { global: { stubs } })
    await flushPromises()
    expect(view.find('#staff-home-overdue').exists()).toBe(false)
    await view.get('[data-stat="今日待办"]').trigger('click')
    expect(navigation.push).toHaveBeenLastCalledWith({ name: 'WU-T09', query: { filter: 'ALL' } })
    expect(api.fetchTodayTodos.mock.calls.every(([query]) => query.filter !== 'WARNING')).toBe(true)
    view.unmount()
  })

  it('renders all twenty fetched rows and folds overdue on entry, mode change and farm change', async () => {
    const view = mount(TodoCollectionView, { global: { stubs } })
    await flushPromises()
    expect(view.findAll('[data-table] button')).toHaveLength(20)
    expect(view.getComponent({ name: 'VDataTable' }).props('itemsPerPage')).toBe(-1)
    expect(view.find('#collection-overdue').exists()).toBe(false)
    await view.get('button[aria-controls="collection-overdue"]').trigger('click')
    await flushPromises()
    expect(view.find('#collection-overdue').exists()).toBe(true)
    useFarmStore().revision++
    await flushPromises()
    expect(view.find('#collection-overdue').exists()).toBe(false)
    navigation.route.query.filter = 'WARNING'
    await flushPromises()
    expect(view.text()).toContain('预警合集')
    expect(view.find('button[aria-controls="collection-overdue"]').exists()).toBe(false)
    expect(view.findAll('[data-table] button')).toHaveLength(2)
    expect(todayRoutes.find((route) => route.name === 'WU-T09')?.path).toBe('/todos')
    view.unmount()
  })
})
