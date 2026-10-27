import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { herdRoutes } from '../apps/user/src/router/herd'
import { installGuards } from '../apps/user/src/router/guards'
import { useAuthStore } from '../apps/user/src/stores/auth'
import { useFarmStore } from '../apps/user/src/stores/farm'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
const raw = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('../apps/user/src/api/client', () => ({ http: () => api }))
vi.mock('../apps/user/src/api/raw', () => ({ raw }))

const TOKEN = 'k7Rm2xQ9pT'
const publicTrace = { batch_no: 'ZM2610-031', farm_name: '公开养殖场', category_name: '育肥猪', breed_name: '杜长大', source: 'PURCHASED', status: 'ACTIVE', entry_date: '2026-10-01', events: [] }
const blank = { template: '<main>ready</main>' }
const box = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) })
const empty = defineComponent({ props: ['title', 'desc'], setup: (props, { slots }) =>
  () => h('section', [h('h2', props.title), h('p', props.desc), slots.default?.()]) })
const button = defineComponent({ emits: ['click'], setup: (_, { slots, emit }) =>
  () => h('button', { onClick: () => emit('click') }, slots.default?.()) })

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

function lookupResult(result: object) {
  api.get.mockImplementation((url: string) => Promise.resolve(url.startsWith('/public/batch-traces/') ? publicTrace : result))
}

async function openManagement(view: Awaited<ReturnType<typeof render>>['view']) {
  await view.findAll('button').find((item) => item.text().includes('管理档案'))!.trigger('click')
  await flushPromises()
}

function routerForTest() {
  const shortLink = herdRoutes.find((route) => route.name === 'WU-L06')!
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'WU-T01', component: blank },
    { path: '/login', name: 'WU-A01', component: blank, meta: { public: true, layout: 'blank', needsFarm: false } },
    { path: '/farms', name: 'WU-F01', component: blank, meta: { needsFarm: false } },
    { path: '/herd/batches', name: 'WU-L03', component: blank },
    { path: '/herd/batches/:batchId', name: 'WU-L04', component: blank },
    shortLink
  ] })
  installGuards(router)
  return router
}

async function render(path = `/s/b/${TOKEN}`) {
  const router = routerForTest()
  const root = defineComponent({ setup: () => () => h(RouterView) })
  const view = mount(root, { global: { plugins: [router], stubs: {
    'zw-card': box, 'zw-empty': empty, 'v-btn': button, 'v-progress-circular': box
  } } })
  await router.push(path)
  await router.isReady()
  await flushPromises()
  return { router, view }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  lookupResult({ result: 'PUBLIC_TRACE', trace_token: TOKEN })
  sessionStorage.clear()
  useAuthStore().accessToken = 'qa-token'
  useAuthStore().user = { user_id: 10001 }
  const farm = useFarmStore()
  farm.loaded = true
  farm.currentFarmId = 1001
  farm.farms = [1001, 1002].map((farm_id) => ({ farm_id, name: `养殖场${farm_id}`,
    role: 'OWNER', role_text: '所有者', status: 'NORMAL', status_text: '正常' }))
})

describe('batch QR web landing', () => {
  it('shows public traceability anonymously without requiring login or farm selection', async () => {
    useAuthStore().$reset()
    useFarmStore().currentFarmId = 0
    const { router, view } = await render()
    expect(router.currentRoute.value.name).toBe('WU-L06')
    expect(view.text()).toContain('ZM2610-031')
    expect(view.text()).toContain('公开溯源')
    expect(api.get).toHaveBeenCalledExactlyOnceWith(`/public/batch-traces/${TOKEN}`)
    expect(raw.post).not.toHaveBeenCalled()
    expect(api.post).not.toHaveBeenCalled()
  })

  it('preserves the public link as login return address only when a visitor requests management', async () => {
    useAuthStore().$reset()
    raw.post.mockRejectedValue(new Error('no refresh Cookie'))
    const { router, view } = await render()
    await openManagement(view)
    expect(router.currentRoute.value.name).toBe('WU-A01')
    expect(router.currentRoute.value.query.redirect).toBe(`/s/b/${TOKEN}`)
  })

  it('looks up permissions and opens the current farm archive only after the management click', async () => {
    lookupResult({ result: 'OK', batch_id: 3001, farm_id: 1001 })
    const { router, view } = await render()
    expect(router.currentRoute.value.name).toBe('WU-L06')
    expect(api.get).toHaveBeenCalledTimes(1)
    await openManagement(view)
    expect(api.get).toHaveBeenCalledWith('/batches/lookup', { qrContent: `${window.location.origin}/s/b/${TOKEN}` })
    expect(router.currentRoute.value.params.batchId).toBe('3001')
    expect(api.post).not.toHaveBeenCalled()
  })

  it('waits for the farm switch before opening a batch from another joined farm', async () => {
    const switching = deferred<void>()
    lookupResult({ result: 'SWITCH_FARM', batch_id: 3201, farm_id: 1002 })
    api.post.mockReturnValue(switching.promise)
    const { router, view } = await render()
    await openManagement(view)
    expect(api.post).toHaveBeenCalledWith('/farms/1002/switch')
    expect(router.currentRoute.value.name).toBe('WU-L06')
    switching.resolve()
    await flushPromises()
    expect(useFarmStore().currentFarmId).toBe(1002)
    expect(router.currentRoute.value.params.batchId).toBe('3201')
    expect(router.currentRoute.value.query.switched).toBe('1')
  })

  it.each([
    'PUBLIC_TRACE', 'NO_PERMISSION'
  ])('keeps an external %s visitor on the public page without switching farms', async (result) => {
    lookupResult({ result, trace_token: TOKEN })
    const { router, view } = await render()
    await openManagement(view)
    expect(view.text()).toContain('ZM2610-031')
    expect(view.text()).toContain('完整管理档案仅向有权限的场内成员开放')
    expect(router.currentRoute.value.name).toBe('WU-L06')
    expect(useFarmStore().currentFarmId).toBe(1001)
    expect(api.post).not.toHaveBeenCalled()
  })

  it('does not display archived data when the public link is disabled or no longer exists', async () => {
    api.get.mockRejectedValue({ code: 40401 })
    const { router, view } = await render()
    expect(router.currentRoute.value.name).toBe('WU-L06')
    expect(view.text()).toContain('不存在、已停用或已更新')
    expect(view.text()).not.toContain('ZM2610-031')
  })

  it.each(['Xl8cQ2fN4w', 'BRD000015A'])('reads the existing alphanumeric QR token %s without changing its case', async (token) => {
    const { view } = await render(`/s/b/${token}`)
    expect(view.text()).toContain('ZM2610-031')
    expect(api.get).toHaveBeenCalledExactlyOnceWith(`/public/batch-traces/${token}`)
  })

  it.each(['k7Rm2xQ9p_', 'short', 'k7Rm2xQ9p中'])('does not look up a malformed QR token %s', async (token) => {
    const { view } = await render(`/s/b/${token}`)
    expect(view.text()).toContain('二维码地址不完整')
    expect(api.get).not.toHaveBeenCalled()
  })

  it('offers a working retry after a network failure', async () => {
    api.get.mockRejectedValueOnce(new Error('offline'))
    const { router, view } = await render()
    expect(view.text()).toContain('溯源信息暂时无法加载')
    api.get.mockResolvedValue(publicTrace)
    await view.findAll('button').find((item) => item.text() === '重新加载')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('WU-L06')
    expect(view.text()).toContain('ZM2610-031')
  })

  it('does not navigate into a batch after a failed farm switch', async () => {
    lookupResult({ result: 'SWITCH_FARM', batch_id: 3201, farm_id: 1002 })
    api.post.mockRejectedValue(new Error('offline'))
    const { router, view } = await render()
    await openManagement(view)
    expect(router.currentRoute.value.name).toBe('WU-L06')
    expect(view.text()).toContain('管理档案暂时无法打开')
    expect(useFarmStore().currentFarmId).toBe(1001)
  })

  it('discards an old public response after another QR has loaded', async () => {
    const old = deferred<object>()
    api.get.mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce({ ...publicTrace, batch_no: 'ZM2610-032' })
    const { router, view } = await render()
    await router.push('/s/b/aaaaaaa234')
    await flushPromises()
    old.resolve(publicTrace)
    await flushPromises()
    expect(router.currentRoute.value.params.token).toBe('aaaaaaa234')
    expect(view.text()).toContain('ZM2610-032')
    expect(view.text()).not.toContain('ZM2610-031')
  })

  it.each(['success', 'failure'])('ignores an obsolete management %s after scanning another code', async (outcome) => {
    const old = deferred<object>()
    api.get.mockImplementation((url: string) => url === '/batches/lookup' ? old.promise : Promise.resolve(publicTrace))
    const { router, view } = await render()
    await openManagement(view)
    await router.push('/s/b/aaaaaaa234')
    await flushPromises()
    if (outcome === 'success') old.resolve({ result: 'SWITCH_FARM', batch_id: 3201, farm_id: 1002 })
    else old.reject(new Error('old request failed'))
    await flushPromises()
    expect(router.currentRoute.value.params.token).toBe('aaaaaaa234')
    expect(view.text()).not.toContain('管理档案暂时无法打开')
    expect(api.post).not.toHaveBeenCalled()
  })

  it('serializes overlapping farm switches so a newer QR never opens with the previous farm', async () => {
    const oldSwitch = deferred<void>()
    let lookups = 0
    api.get.mockImplementation((url: string) => Promise.resolve(url.startsWith('/public/') ? publicTrace :
      (++lookups === 1 ? { result: 'SWITCH_FARM', batch_id: 3201, farm_id: 1002 } : { result: 'OK', batch_id: 3001, farm_id: 1001 })))
    api.post.mockReturnValueOnce(oldSwitch.promise).mockResolvedValueOnce(undefined)
    const { router, view } = await render()
    await openManagement(view)
    await router.push('/s/b/aaaaaaa234')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('WU-L06')
    oldSwitch.resolve()
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('WU-L06')
    await openManagement(view)
    expect(api.post.mock.calls.map((call) => call[0])).toEqual(['/farms/1002/switch', '/farms/1001/switch'])
    expect(useFarmStore().currentFarmId).toBe(1001)
    expect(router.currentRoute.value.params.batchId).toBe('3001')
  })
})
