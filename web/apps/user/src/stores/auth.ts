import { defineStore } from 'pinia'
import type { ApiResponse } from '@zmt/shared'
import { raw } from '@/api/raw'
import { http } from '@/api/client'

export interface AccountUser {
  user_id?: number
  name?: string
  phone?: string
  phone_masked?: string
  avatar_url?: string | null
  status?: string
  status_text?: string
  [key: string]: unknown
}

export interface AuthToken {
  access_token: string
  token_type?: string
  expires_in?: number
  next_step?: string
  next_step_text?: string
  user?: AccountUser
  last_farm_id?: number
  recovery_code?: { code?: string; [key: string]: unknown }
  [key: string]: unknown
}

export interface SessionInfo {
  user?: AccountUser
  next_step?: string
  next_step_text?: string
  last_farm_id?: number
  [key: string]: unknown
}

function deviceId(): string {
  const key = 'zmt.device.id'
  let value = typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null
  if (!value) {
    value = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : String(Date.now())
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, value)
  }
  return value
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: null as string | null,
    user: null as AccountUser | null,
    nextStep: '',
    nextStepText: '',
    lastFarmId: 0,
    sessionLoaded: false,
    recoveryCode: '' as string
  }),
  getters: {
    isLoggedIn: (state) => !!state.accessToken,
    displayName: (state) => state.user?.name || '用户',
    phone: (state) => state.user?.phone_masked || state.user?.phone || ''
  },
  actions: {
    applyToken(input: unknown): void {
      const token = input as AuthToken
      this.accessToken = token.access_token
      if (token.user) this.user = token.user
      this.nextStep = token.next_step || ''
      this.nextStepText = token.next_step_text || ''
      if (token.last_farm_id) this.lastFarmId = token.last_farm_id
      if (token.recovery_code?.code) this.recoveryCode = String(token.recovery_code.code)
    },
    async loginPassword(phone: string, password: string): Promise<AuthToken> {
      const res = await raw.post<ApiResponse<AuthToken>>('/auth/login/password', {
        phone,
        password,
        device_id: deviceId()
      })
      this.applyToken(res.data.data)
      return res.data.data
    },
    async loginSms(phone: string, smsCode: string): Promise<AuthToken> {
      const res = await raw.post<ApiResponse<AuthToken>>('/auth/login/sms', {
        phone,
        sms_code: smsCode,
        device_id: deviceId()
      })
      this.applyToken(res.data.data)
      return res.data.data
    },
    async sendSms(phone: string, scene: string): Promise<{ phone_masked?: string; cooldown_seconds?: number }> {
      const res = await raw.post<ApiResponse<{ phone_masked?: string; cooldown_seconds?: number }>>('/sms-codes', {
        phone,
        scene
      })
      return res.data.data
    },
    async refresh(): Promise<boolean> {
      const doRefresh = async (): Promise<boolean> => {
        try {
          const res = await raw.post<ApiResponse<AuthToken>>('/auth/token/refresh', {})
          this.applyToken(res.data.data)
          return true
        } catch {
          this.clear()
          return false
        }
      }
      // 多标签页 / 并发刷新时用 Web Locks 串行化，避免刷新令牌轮换互相作废
      const locks = typeof navigator !== 'undefined' ? (navigator as unknown as { locks?: { request: (name: string, cb: () => Promise<boolean>) => Promise<boolean> } }).locks : undefined
      if (locks?.request) {
        try {
          return await locks.request('zmt.auth.refresh', doRefresh)
        } catch {
          return false
        }
      }
      return doRefresh()
    },
    async loadSession(): Promise<SessionInfo | null> {
      try {
        const data = await http().get<SessionInfo>('/auth/session')
        this.user = data.user || this.user
        this.nextStep = data.next_step || ''
        this.nextStepText = data.next_step_text || ''
        if (data.last_farm_id) this.lastFarmId = data.last_farm_id
        this.sessionLoaded = true
        return data
      } catch {
        return null
      }
    },
    async logout(): Promise<void> {
      try {
        await http().post('/auth/logout')
      } catch {
        /* 退出失败也清空本地 */
      } finally {
        this.clear()
      }
    },
    clear(): void {
      this.accessToken = null
      this.user = null
      this.nextStep = ''
      this.nextStepText = ''
      this.lastFarmId = 0
      this.sessionLoaded = false
      this.recoveryCode = ''
    }
  }
})
