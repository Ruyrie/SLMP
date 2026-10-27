import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAdminStore } from '../apps/admin/src/stores/admin'
import { useAdminMessagesStore } from '../apps/admin/src/stores/messages'
import { messageTarget } from '../apps/admin/src/utils/messageTarget'
import type { AdminMessage } from '../apps/admin/src/api/messages'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../apps/admin/src/api/client', () => ({ http: () => api }))

function message(id: number, read = false): AdminMessage {
  return { message_id: id, title: `行情提醒 ${id}`, msg_type: 'MARKET_PRICE_UNPUBLISHED',
    msg_type_text: '行情未发布', level: 'NORMAL', route_page: 'WA-C02', read, created_at: '2026-10-26 10:00:00' }
}
function page(list = [message(1), message(2)], unread = 2) {
  return { list, total: list.length, page: 1, size: 20, has_more: false,
    tab_counts: { all: list.length, unread } }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  const admin = useAdminStore()
  admin.applyLogin({ admin: { admin_id: 1, name: '管理员' } })
  admin.unread = 2
  api.get.mockResolvedValue(page())
})

describe('admin bell messages', () => {
  it('opens the real paginated message endpoint and takes the unread badge from the server', async () => {
    api.get.mockResolvedValue(page([message(1)], 1))
    const store = useAdminMessagesStore()
    await store.show()
    expect(store.open).toBe(true)
    expect(api.get).toHaveBeenCalledWith('/messages', { read: 'ALL', page: 1, size: 20 })
    expect(store.list[0].title).toBe('行情提醒 1')
    expect(useAdminStore().unread).toBe(1)
  })

  it('does not replace the unread tab with a late response from the all tab', async () => {
    const old = deferred<ReturnType<typeof page>>()
    api.get.mockReturnValueOnce(old.promise).mockResolvedValueOnce(page([message(2)], 1))
    const store = useAdminMessagesStore()
    const loading = store.show()
    await store.setFilter('UNREAD')
    old.resolve(page())
    await loading
    expect(store.filter).toBe('UNREAD')
    expect(store.list.map((row) => row.message_id)).toEqual([2])
    expect(useAdminStore().unread).toBe(1)
  })

  it('marks just the chosen message and refreshes the badge and read state', async () => {
    const store = useAdminMessagesStore()
    await store.show()
    api.post.mockResolvedValue({ updated_count: 1 })
    api.get.mockResolvedValue(page([message(1, true), message(2)], 1))
    expect(await store.markRead(store.list[0])).toBe(true)
    expect(api.post).toHaveBeenCalledWith('/messages/read', { message_ids: [1] })
    expect(store.list[0].read).toBe(true)
    expect(store.list[1].read).toBe(false)
    expect(useAdminStore().unread).toBe(1)
  })

  it('keeps messages unread and exposes a retryable error when the write fails', async () => {
    const store = useAdminMessagesStore()
    await store.show()
    api.post.mockRejectedValue(new Error('offline'))
    expect(await store.markRead(store.list[0])).toBe(false)
    expect(store.list[0].read).toBe(false)
    expect(useAdminStore().unread).toBe(2)
    expect(store.operationError).toBe('标记已读失败，请重试')
    expect(store.busy).toBe(false)
  })

  it('retains a newly arrived unread message after marking all older messages read', async () => {
    const store = useAdminMessagesStore()
    await store.show()
    api.post.mockResolvedValue({ updated_count: 2 })
    api.get.mockResolvedValue(page([message(1, true), message(2, true), message(3)], 1))
    await store.markRead()
    expect(api.post).toHaveBeenCalledWith('/messages/read', { all: true })
    expect(useAdminStore().unread).toBe(1)
    expect(store.list[2].read).toBe(false)
  })

  it('does not change another login session when an earlier read operation finishes late', async () => {
    const store = useAdminMessagesStore()
    await store.show()
    const write = deferred<{ updated_count: number }>()
    api.post.mockReturnValue(write.promise)
    const marking = store.markRead(store.list[0])
    store.reset()
    useAdminStore().applyLogin({ admin: { admin_id: 2 } })
    useAdminStore().unread = 7
    write.resolve({ updated_count: 1 })
    await marking
    expect(store.open).toBe(false)
    expect(store.list).toEqual([])
    expect(useAdminStore().unread).toBe(7)
  })

  it('ignores a response after closing and recovers from a later load failure', async () => {
    const old = deferred<ReturnType<typeof page>>()
    const store = useAdminMessagesStore()
    api.get.mockReturnValueOnce(old.promise)
    const loading = store.show()
    store.close()
    old.resolve(page())
    await loading
    expect(store.list).toEqual([])
    api.get.mockRejectedValueOnce(new Error('offline'))
    await store.show()
    expect(store.error).toBe('消息加载失败，请重试')
    expect(store.loading).toBe(false)
    await store.load()
    expect(store.error).toBe('')
    expect(store.list).toHaveLength(2)
  })

  it('returns to the last available page after messages on a later page disappear', async () => {
    const store = useAdminMessagesStore()
    store.open = true
    api.get.mockResolvedValueOnce({ ...page([], 1), total: 21 })
      .mockResolvedValueOnce({ ...page([message(21)], 1), total: 21 })
    await store.load(3)
    expect(api.get).toHaveBeenLastCalledWith('/messages', { read: 'ALL', page: 2, size: 20 })
    expect(store.page).toBe(2)
    expect(store.list[0].message_id).toBe(21)
    expect(store.loading).toBe(false)
  })

  it('does not restore a read badge from an older unread poll', async () => {
    const admin = useAdminStore()
    const store = useAdminMessagesStore()
    await store.show()
    const oldPoll = deferred<{ unread_count: number }>()
    api.get.mockReturnValueOnce(oldPoll.promise)
    const polling = admin.loadUnread()
    api.post.mockResolvedValue({ updated_count: 2 })
    api.get.mockResolvedValue(page([message(1, true), message(2, true)], 0))
    await store.markRead()
    oldPoll.resolve({ unread_count: 2 })
    await polling
    expect(admin.unread).toBe(0)
  })

  it('does not apply an unread poll after logging out and into another account', async () => {
    const admin = useAdminStore()
    const oldPoll = deferred<{ unread_count: number }>()
    api.get.mockReturnValueOnce(oldPoll.promise)
    const polling = admin.loadUnread()
    admin.reset()
    admin.applyLogin({ admin: { admin_id: 2 } })
    admin.setUnread(7)
    oldPoll.resolve({ unread_count: 2 })
    await polling
    expect(admin.unread).toBe(7)
  })

  it('keeps a newer unread poll valid while refreshing the same administrator profile', async () => {
    const admin = useAdminStore()
    const profile = deferred<{ admin: { admin_id: number }, unread_message_count: number }>()
    const poll = deferred<{ unread_count: number }>()
    api.get.mockReturnValueOnce(profile.promise).mockReturnValueOnce(poll.promise)
    const refreshing = admin.me()
    const polling = admin.loadUnread()
    profile.resolve({ admin: { admin_id: 1 }, unread_message_count: 2 })
    await refreshing
    poll.resolve({ unread_count: 1 })
    await polling
    expect(admin.unread).toBe(1)
  })

  it('does not navigate an old message when accounts change during the post-read refresh', async () => {
    const store = useAdminMessagesStore()
    await store.show()
    api.post.mockResolvedValue({ updated_count: 1 })
    const refresh = deferred<ReturnType<typeof page>>()
    api.get.mockReturnValueOnce(refresh.promise)
    const marking = store.markRead(store.list[0])
    await vi.waitFor(() => expect(api.get).toHaveBeenCalledTimes(2))
    store.reset()
    useAdminStore().applyLogin({ admin: { admin_id: 2 } })
    refresh.resolve(page())
    expect(await marking).toBe(false)
  })
})

describe('admin message destinations', () => {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'WA-S02', component: {} },
    { path: '/content/market', name: 'WA-C02', component: {}, meta: { perm: 'WA-C02:VIEW' } },
    { path: '/login', name: 'WA-S01', component: {}, meta: { public: true } },
    { path: '/farm/:id', name: 'farm', component: {} }
  ] })

  it('opens a permitted local admin page with scalar route parameters', () => {
    const item = { ...message(1), route_params: { task_id: 6, tab: 'tasks', pending: true, nested: {} } }
    const target = messageTarget(item, router, (perm) => perm === 'WA-C02:VIEW')
    expect(router.resolve(target!).fullPath).toBe('/content/market?task_id=6&tab=tasks&pending=true')
  })

  it('keeps inaccessible, unknown, public and incomplete destinations inside the message list', () => {
    expect(messageTarget(message(1), router, () => false)).toBeNull()
    for (const route_page of [undefined, 'https://example.com', 'WA-S01', 'farm']) {
      expect(messageTarget({ ...message(1), route_page }, router, () => true)).toBeNull()
    }
  })
})
