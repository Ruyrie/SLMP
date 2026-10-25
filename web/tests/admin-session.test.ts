import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { installChunkReload, installGuards, isChunkLoadError } from '../apps/admin/src/router/guards'
import { useAdminStore } from '../apps/admin/src/stores/admin'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../apps/admin/src/api/client', () => ({ http: () => api }))

const profile = { admin_id: 1, name: '周敏', username: 'zhoumin', role_code: 'SUPER_ADMIN' }

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'WA-S01', component: {}, meta: { public: true } },
      { path: '/', name: 'WA-S02', component: {} },
      { path: '/users', name: 'users', component: {} },
      { path: '/universal/:type', name: 'WA-U02', component: {} }
    ]
  })
  installGuards(router)
  return router
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
})

describe('admin stale chunk after redeploy', () => {
  function chunkRouter(reload: (href: string) => void) {
    const router = createRouter({
      history: createMemoryHistory('/admin/'),
      routes: [
        { path: '/', name: 'home', component: {} },
        {
          path: '/system/logs', name: 'logs',
          component: () => Promise.reject(new TypeError('Failed to fetch dynamically imported module: /admin/assets/OperationLogsView-old.js'))
        }
      ]
    })
    installChunkReload(router, reload)
    return router
  }

  beforeEach(() => sessionStorage.clear())

  it('recognises browser chunk-load errors only', () => {
    expect(isChunkLoadError(new TypeError('Failed to fetch dynamically imported module: /admin/assets/a.js'))).toBe(true)
    expect(isChunkLoadError(new TypeError('Importing a module script failed.'))).toBe(true)
    expect(isChunkLoadError(new Error('Request failed with status code 500'))).toBe(false)
  })

  it('reloads the production target once and does not loop if the retry fails', async () => {
    const reload = vi.fn()
    const router = chunkRouter(reload)
    await router.push('/')
    await router.push('/system/logs').catch(() => undefined)
    expect(reload).toHaveBeenCalledExactlyOnceWith('/admin/system/logs')
    await router.push('/system/logs').catch(() => undefined)
    expect(reload).toHaveBeenCalledTimes(1)
  })
})

describe('admin Cookie session', () => {
  it('restores a normal session on the login page and opens the overview', async () => {
    api.get.mockResolvedValue({ admin: profile, must_change_password: false })
    const router = makeRouter()
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('WA-S02')
    expect(api.get).toHaveBeenCalledExactlyOnceWith('/auth/me')
  })

  it('restores a forced-password session without redirecting the login page in a loop', async () => {
    api.get.mockResolvedValue({ admin: profile, must_change_password: true })
    const router = makeRouter()
    await router.push('/users')
    expect(router.currentRoute.value.name).toBe('WA-S01')
    expect(router.currentRoute.value.query.redirect).toBe('/users')
    expect(useAdminStore().mustChangePassword).toBe(true)
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('WA-S01')
    expect(api.get).toHaveBeenCalledTimes(1)
  })

  it('keeps a signed-out visitor on login after one failed restoration probe', async () => {
    api.get.mockRejectedValue(new Error('no Cookie session'))
    const router = makeRouter()
    await router.push('/users')
    expect(router.currentRoute.value.name).toBe('WA-S01')
    await router.push('/')
    expect(api.get).toHaveBeenCalledTimes(1)
    expect(useAdminStore().loaded).toBe(false)
  })

  it('sends the server password contract and clears session state on logout', async () => {
    const store = useAdminStore()
    store.applyLogin({ admin: profile, must_change_password: true })
    await store.changePassword('Old12345', 'New12345', 'New12345')
    expect(api.put).toHaveBeenCalledWith('/auth/password', {
      old_password: 'Old12345', new_password: 'New12345', confirm_password: 'New12345'
    })
    await store.logout()
    expect(store.loaded).toBe(false)
    expect(store.mustChangePassword).toBe(false)
    expect(store.sessionChecked).toBe(true)
    const router = makeRouter()
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('WA-S01')
    expect(api.get).not.toHaveBeenCalled()
  })
})
