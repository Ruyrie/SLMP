import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useFarmStore } from '../apps/user/src/stores/farm'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../apps/user/src/api/client', () => ({ http: () => api }))

function farms(role = 'OWNER', status = 'NORMAL') {
  return { list: [{ farm_id: 1001, name: '东山猪场', role, status }, { farm_id: 1002, name: '北湾牛场', role: 'OWNER', status: 'NORMAL' }] }
}

describe('farm store refresh on window focus', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    api.get.mockReset()
  })

  it('does not bump revision when the current farm is unchanged', async () => {
    api.get.mockResolvedValue(farms())
    const store = useFarmStore()
    await store.load()
    const revision = store.revision
    await store.load(true)
    expect(store.currentFarmId).toBe(1001)
    expect(store.revision).toBe(revision)
  })

  it('bumps revision when the current farm role or status changes', async () => {
    api.get.mockResolvedValueOnce(farms()).mockResolvedValueOnce(farms('STAFF')).mockResolvedValueOnce(farms('STAFF', 'FROZEN'))
    const store = useFarmStore()
    await store.load()
    const revision = store.revision
    await store.load(true)
    expect(store.revision).toBe(revision + 1)
    await store.load(true)
    expect(store.revision).toBe(revision + 2)
  })
})
