import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  confirmRecoveryCode,
  getCurrentAgreement,
  registerAccount,
  regeneratePendingRecoveryCode,
  sendSmsCode,
  type AgreementVO,
  type AuthTokenVO,
  type RecoveryCodeVO
} from '@/api/account'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import {
  deviceId,
  formatPhone,
  MSG_AGREE_TERMS,
  MSG_NETWORK,
  MSG_SMS_SEND_FAILED,
  MSG_TOO_FREQUENT,
  passwordProblem,
  phoneProblem,
  smsCodeProblem
} from '@/views/account/accountForm'

let timer = 0

export const useRegisterStore = defineStore('register', {
  state: () => ({
    agreement: null as AgreementVO | null,
    agreementLoading: false,
    phone: '',
    smsCode: '',
    password: '',
    showPassword: false,
    agree: false,
    masked: '',
    countdown: 0,
    sending: false,
    submitting: false,
    confirming: false,
    dialogOpen: false,
    saved: false,
    recoveryCode: null as RecoveryCodeVO | null,
    errors: {} as Record<string, string>,
    ready: false
  }),
  getters: {
    phoneDisplay: (state): string => formatPhone(state.phone),
    canSend(state): boolean {
      return state.phone.length === 11 && state.countdown <= 0 && !state.sending
    },
    canSubmit(state): boolean {
      return !!state.phone && !!state.smsCode && !!state.password && !state.submitting
    },
    codeDisplay(state): string {
      return state.recoveryCode?.code ? formatPhoneCode(state.recoveryCode.code) : ''
    }
  },
  actions: {
    setPhone(value: string): void {
      this.phone = String(value ?? '')
        .replace(/[^\d]/g, '')
        .slice(0, 11)
      delete this.errors.phone
    },
    setSmsCode(value: string): void {
      this.smsCode = String(value ?? '')
        .replace(/[^\d]/g, '')
        .slice(0, 6)
      delete this.errors.sms_code
    },
    setPassword(value: string): void {
      this.password = String(value ?? '')
      delete this.errors.password
    },
    setAgree(value: boolean | null): void {
      this.agree = value === true
      delete this.errors.agree_terms
    },
    async loadAgreement(): Promise<void> {
      if (this.agreementLoading) return
      this.agreementLoading = true
      try {
        this.agreement = await getCurrentAgreement()
      } catch {
        // 进入时失败不提示，注册前会重试
      } finally {
        this.agreementLoading = false
      }
    },
    startCountdown(seconds: number): void {
      this.countdown = Math.max(1, Number(seconds) || 60)
      window.clearInterval(timer)
      timer = window.setInterval(() => {
        this.countdown -= 1
        if (this.countdown <= 0) window.clearInterval(timer)
      }, 1000)
    },
    stopCountdown(): void {
      window.clearInterval(timer)
    },
    async sendCode(): Promise<void> {
      if (this.sending || this.countdown > 0) return
      const problem = phoneProblem(this.phone)
      if (problem) {
        this.errors.phone = problem
        return
      }
      this.sending = true
      try {
        const data = await sendSmsCode({ phone: this.phone, scene: 'REGISTER' })
        this.masked = data.phone_masked || ''
        this.startCountdown(data.cooldown_seconds || 60)
        useUiStore().toast('验证码已发送')
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40904) {
          this.errors.phone = err.message
        } else if (err.code === 42901) useUiStore().toast(MSG_TOO_FREQUENT, 'error')
        else useUiStore().toast(err.message || MSG_SMS_SEND_FAILED, 'error')
      } finally {
        this.sending = false
      }
    },
    validate(): Record<string, string> {
      const errors: Record<string, string> = {}
      const phone = phoneProblem(this.phone)
      if (phone) errors.phone = phone
      const code = smsCodeProblem(this.smsCode)
      if (code) errors.sms_code = code
      const pwd = passwordProblem(this.password)
      if (pwd) errors.password = pwd
      if (!this.agree) errors.agree_terms = MSG_AGREE_TERMS
      return errors
    },
    async register(): Promise<boolean> {
      if (this.submitting) return false
      this.errors = this.validate()
      if (Object.keys(this.errors).length) return false
      this.submitting = true
      try {
        let agreement = this.agreement
        if (!agreement?.agreement_version) {
          agreement = await getCurrentAgreement()
          this.agreement = agreement
        }
        const token = await registerAccount({
          phone: this.phone,
          sms_code: this.smsCode.trim(),
          password: this.password,
          agree_terms: true,
          agreement_version: agreement.agreement_version || '',
          device_id: deviceId()
        })
        useAuthStore().applyToken(token as AuthTokenVO)
        if (token.recovery_code?.code) {
          this.applyRecoveryCode(token.recovery_code)
        } else {
          await this.startSaveCodeStep()
        }
        return true
      } catch (e) {
        this.handleRegisterError(e as ApiError)
        return false
      } finally {
        this.submitting = false
      }
    },
    applyRecoveryCode(code: RecoveryCodeVO | null | undefined): void {
      if (!code?.code) return
      this.recoveryCode = code
      this.saved = false
      this.dialogOpen = true
    },
    async startSaveCodeStep(): Promise<boolean> {
      try {
        const code = await regeneratePendingRecoveryCode()
        this.applyRecoveryCode(code)
        return true
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40309) {
          const next = String((err.data as { next_step?: string } | undefined)?.next_step || '')
          useAuthStore().nextStep = next
          return false
        }
        useUiStore().toast(err.message || '恢复码加载失败，请稍后重试', 'error')
        return false
      }
    },
    async confirm(): Promise<boolean> {
      const id = this.recoveryCode?.recovery_code_id
      if (!id || !this.saved || this.confirming) return false
      this.confirming = true
      try {
        await confirmRecoveryCode(id)
        const auth = useAuthStore()
        auth.nextStep = ''
        auth.nextStepText = ''
        auth.recoveryCode = ''
        this.dialogOpen = false
        this.recoveryCode = null
        this.saved = false
        return true
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40903 || err.code === 40401) {
          useUiStore().toast('恢复码已更新，请保存最新的恢复码', 'info')
          this.saved = false
          await this.startSaveCodeStep()
          return false
        }
        useUiStore().toast(err.message || '保存恢复码失败，请稍后重试', 'error')
        return false
      } finally {
        this.confirming = false
      }
    },
    handleRegisterError(err: ApiError): void {
      if (err.code === 40904) {
        const message = err.message || '这个手机号已经注册，可以直接登录'
        this.errors.phone = message
        if (message.includes('已经注册') || message.includes('可直接登录')) {
          useUiStore().toast(message, 'info')
        }
        return
      }
      if (err.code === 40001) {
        const fields = err.fieldErrors
        if (fields.length) {
          for (const item of fields) {
            if (item.field === 'agreement_version') {
              this.agree = false
              this.agreement = null
              this.errors.agree_terms = '协议已更新，请重新阅读并同意'
              void this.loadAgreement()
            } else {
              this.errors[item.field] = item.message
            }
          }
          return
        }
        if (err.message.includes('协议已更新')) {
          this.agree = false
          this.agreement = null
          this.errors.agree_terms = '协议已更新，请重新阅读并同意'
          void this.loadAgreement()
          return
        }
      }
      if (err.code === 40103) {
        this.errors.sms_code = err.message
        return
      }
      if (err.isNetwork) {
        useUiStore().toast(MSG_NETWORK, 'error')
        return
      }
      useUiStore().toast(err.message || '注册失败，请稍后重试', 'error')
    }
  }
})

function formatPhoneCode(code: string): string {
  return code
    .replace(/[^A-Za-z0-9]/g, '')
    .toUpperCase()
    .replace(/(.{4})/g, '$1 ')
    .trim()
}
