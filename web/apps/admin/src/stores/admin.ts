import { defineStore } from 'pinia'
import { http } from '@/api/client'

export interface AdminProfile {
  admin_id?: number
  name?: string
  username?: string
  role_code?: string
  role_code_text?: string
  avatar_url?: string | null
}

export interface AdminLoginResult {
  admin?: AdminProfile
  must_change_password?: boolean
  temp_password_expire_at?: string
  idle_minutes?: number
  permissions?: string[]
  menus?: unknown[]
}

export interface AdminMe extends AdminLoginResult {
  badges?: Record<string, number>
  unread_message_count?: number
}

export interface Captcha {
  captcha_id: string
  image: string
  expire_seconds?: number
}

export const useAdminStore = defineStore('admin', {
  state: () => ({
    admin: null as AdminProfile | null,
    perms: [] as string[],
    menus: [] as unknown[],
    badges: {} as Record<string, number>,
    unread: 0,
    unreadRevision: 0,
    mustChangePassword: false,
    loaded: false,
    sessionChecked: false,
    timer: 0 as number | ReturnType<typeof setInterval>
  }),
  getters: {
    isSuper: (state) => state.admin?.role_code === 'SUPER_ADMIN',
    displayName: (state) => state.admin?.name || '',
    roleName: (state) => state.admin?.role_code_text || '管理员',
    hasPerm: (state) => (perm: string | undefined) =>
      !perm || state.admin?.role_code === 'SUPER_ADMIN' || state.perms.includes(perm)
  },
  actions: {
    applyLogin(result: AdminLoginResult): void {
      if (!this.loaded || (result.admin && result.admin.admin_id !== this.admin?.admin_id)) this.unreadRevision++
      this.admin = result.admin || this.admin
      this.perms = result.permissions || []
      this.menus = result.menus || []
      this.mustChangePassword = !!result.must_change_password
      this.loaded = true
      this.sessionChecked = true
    },
    async fetchCaptcha(): Promise<Captcha> {
      return http().get<Captcha>('/auth/captcha')
    },
    async login(username: string, password: string, captchaId: string, captchaCode: string): Promise<AdminLoginResult> {
      const result = await http().post<AdminLoginResult>('/auth/login', {
        username,
        password,
        captcha_id: captchaId,
        captcha_code: captchaCode
      })
      this.applyLogin(result)
      return result
    },
    async me(): Promise<AdminMe> {
      const unreadRevision = this.unreadRevision
      const data = await http().get<AdminMe>('/auth/me')
      const updateUnread = unreadRevision === this.unreadRevision
      this.applyLogin(data)
      this.badges = data.badges || {}
      if (updateUnread) this.setUnread(data.unread_message_count || 0)
      return data
    },
    setUnread(count: number): void {
      this.unreadRevision++
      this.unread = Math.max(0, count)
    },
    async loadUnread(): Promise<void> {
      const revision = ++this.unreadRevision
      try {
        const data = await http().get<{ unread_count?: number }>('/messages/unread-count')
        if (revision === this.unreadRevision && this.loaded) this.setUnread(data.unread_count || 0)
      } catch {
        /* 静默 */
      }
    },
    startPolling(): void {
      if (this.timer) return
      void this.loadUnread()
      this.timer = window.setInterval(() => void this.loadUnread(), 60000)
    },
    stopPolling(): void {
      if (this.timer) window.clearInterval(this.timer)
      this.timer = 0
    },
    async changePassword(currentPassword: string, newPassword: string, confirmPassword: string): Promise<void> {
      await http().put('/auth/password', {
        old_password: currentPassword,
        new_password: newPassword,
        confirm_password: confirmPassword
      })
    },
    async logout(): Promise<void> {
      try {
        await http().post('/auth/logout')
      } catch {
        /* 忽略 */
      } finally {
        this.reset()
      }
    },
    reset(): void {
      this.unreadRevision++
      this.admin = null
      this.perms = []
      this.menus = []
      this.badges = {}
      this.unread = 0
      this.mustChangePassword = false
      this.loaded = false
      this.sessionChecked = true
      this.stopPolling()
    }
  }
})
