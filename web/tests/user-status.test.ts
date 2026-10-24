import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { commonRoutes } from '../apps/user/src/router/common'
import { installGuards } from '../apps/user/src/router/guards'
import { useAuthStore } from '../apps/user/src/stores/auth'
import { useFarmStore } from '../apps/user/src/stores/farm'
import StatusView from '../apps/user/src/views/common/StatusView.vue'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
const raw = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('../apps/user/src/api/client', () => ({ http: () => api }))
vi.mock('../apps/user/src/api/raw', () => ({ raw }))

const blank = { template: '<div />' }

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'WU-A01', component: blank, meta: { public: true, layout: 'blank', needsFarm: false } },
      { path: '/', name: 'WU-T01', component: blank, meta: { roles: ['owner', 'manager'] } },
      { path: '/workbench/staff', name: 'WU-T02', component: blank, meta: { roles: ['staff'] } },
      { path: '/finance/overview', name: 'WU-E01', component: blank, meta: { roles: ['owner', 'manager'] } },
      { path: '/status/:type', name: 'WU-U02', component: blank, meta: { public: true, layout: 'shell', needsFarm: false } }
    ]
  })
  installGuards(router)
  return router
}

function signedIn() {
  raw.post.mockResolvedValue({ data: { data: { access_token: 't1', user: { user_id: 10003, name: '李明', avatar_url: null } } } })
  api.get.mockImplementation((url: string) => {
    if (url === '/auth/session') return Promise.resolve({ user: { user_id: 10003, name: '李明', avatar_url: null } })
    if (url === '/farms') return Promise.resolve({ list: [{ farm_id: 1001, name: '东山猪场', role: 'STAFF', status: 'NORMAL' }] })
    return Promise.reject(new Error(`unexpected ${url}`))
  })
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  sessionStorage.clear()
})

describe('user status page', () => {
  it('serves the forbidden page with the real status view instead of the construction placeholder', async () => {
    const route = commonRoutes.find((item) => item.name === 'WU-U02')
    const loaded = await (route?.component as () => Promise<{ default: unknown }>)()
    expect(loaded.default).toBe(StatusView)
  })

  it('shows the permission reason passed by the guard', async () => {
    const router = makeRouter()
    signedIn()
    await router.push({ name: 'WU-U02', params: { type: 'forbidden' }, query: { reason: '你没有该页面的查看权限' } })
    const slot = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) })
    const empty = defineComponent({
      props: { title: String, desc: String },
      setup: (props, { slots }) => () => h('section', [props.title, ' ', props.desc, slots.default?.()])
    })
    const view = mount(StatusView, {
      global: { plugins: [router], stubs: { 'zw-shell': slot, 'zw-card': slot, 'zw-empty': empty, 'v-btn': slot } }
    })
    expect(view.text()).toContain('无权查看')
    expect(view.text()).toContain('你没有该页面的查看权限')
    expect(view.text()).not.toContain('页面建设中')
  })

  it('sends a staff member to the status page and keeps the session after a reload there', async () => {
    signedIn()
    const router = makeRouter()
    await router.push('/finance/overview')
    expect(router.currentRoute.value.name).toBe('WU-U02')
    expect(router.currentRoute.value.params.type).toBe('forbidden')

    // 整页刷新：新的 Pinia 与路由，直接打开状态页
    setActivePinia(createPinia())
    const reloaded = makeRouter()
    await reloaded.push('/status/forbidden')
    expect(reloaded.currentRoute.value.name).toBe('WU-U02')
    expect(useAuthStore().user?.name).toBe('李明')
    expect(useFarmStore().loaded).toBe(true)
  })

  it('still shows the status page to a signed-out visitor', async () => {
    raw.post.mockRejectedValue(new Error('no refresh cookie'))
    const router = makeRouter()
    await router.push('/status/not-found')
    expect(router.currentRoute.value.name).toBe('WU-U02')
    expect(useAuthStore().accessToken).toBeNull()
    expect(api.get).not.toHaveBeenCalled()
  })
})
