import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAccountStore } from '../apps/user/src/stores/account/account'
import { useAuthStore } from '../apps/user/src/stores/auth'

const api = vi.hoisted(() => ({
  clearAvatar: vi.fn(), uploadAvatar: vi.fn(), bindAvatar: vi.fn(), getAccountOverview: vi.fn()
}))
vi.mock('../apps/user/src/api/account', () => api)
vi.mock('../apps/user/src/router', () => ({ default: { replace: vi.fn() } }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  useAuthStore().user = { user_id: 10, name: '刘芳', avatar_url: '/old.png' }
})

describe('avatar synchronization', () => {
  it('removes the old top-bar avatar when the server explicitly returns null', async () => {
    api.clearAvatar.mockResolvedValue({ user_id: 10, avatar_url: null })
    api.getAccountOverview.mockResolvedValue({ user: { user_id: 10, name: '刘芳', avatar_url: null } })
    const account = useAccountStore()
    await account.removeAvatar()
    expect(account.user?.avatar_url).toBeNull()
    expect(useAuthStore().user?.avatar_url).toBeNull()
  })

  it('refreshes the personal page and shared session after a successful upload', async () => {
    api.uploadAvatar.mockResolvedValue({ file_id: 7, url: '/uploaded.png' })
    api.bindAvatar.mockResolvedValue({ user_id: 10, avatar_url: '/uploaded.png' })
    api.getAccountOverview.mockResolvedValue({ user: { user_id: 10, name: '刘芳', avatar_url: '/fresh-signed.png' } })
    const account = useAccountStore()
    await account.selectAvatar(new File(['image'], 'avatar.png', { type: 'image/png' }))
    expect(api.bindAvatar).toHaveBeenCalledWith(7)
    expect(account.user?.avatar_url).toBe('/fresh-signed.png')
    expect(useAuthStore().user?.avatar_url).toBe('/fresh-signed.png')
  })
})
