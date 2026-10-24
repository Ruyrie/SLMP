import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  changePasswordAfterRecovery,
  confirmRecoveryCode,
  getCurrentAgreement,
  getSession,
  regeneratePendingRecoveryCode,
  resetPassword,
  sendSmsCode,
  submitAccountRecovery,
  type RecoveryCodeVO,
  type RecoveryInfoVO,
  type SessionVO
} from '@/api/account'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import {
  MSG_NETWORK,
  MSG_SMS_SEND_FAILED,
  MSG_TOO_FREQUENT,
  normalizeRecoveryCode,
  passwordProblem,
  phoneProblem,
  smsCodeProblem,
  textLength,
  DESCRIPTION_MAX,
  MSG_DESCRIPTION,
  MSG_SAME_PHONE,
  recoveryCodeProblem
} from '@/views/account/accountForm'

export type RecoverMode = 'reset' | 'recover' | 'change' | 'save'

let resetTimer = 0
let recoverTimer = 0

interface FieldErrors {
  [key: string]: string
}

export const useRecoverStore = defineStore('recover', {
  state: () => ({
    mode: 'reset' as RecoverMode,
    initializing: false,
    servicePhone: '400-123-4567',
    reset: {
      phone: '',
      smsCode: '',
      password: '',
      showPassword: false,
      masked: '',
      countdown: 0,
      sending: false,
      submitting: false,
      errors: {} as FieldErrors
    },
    recover: {
      oldPhone: '',
      recoveryCode: '',
      newPhone: '',
      newPhoneCode: '',
      description: '',
      masked: '',
      countdown: 0,
      sending: false,
      submitting: false,
      lockBanner: '',
      errors: {} as FieldErrors
    },
    force: {
      recoveryInfo: null as RecoveryInfoVO | null,
      code: null as RecoveryCodeVO | null,
      saved: false,
      submitting: false,
      password: '',
      confirmPassword: '',
      showPassword: false,
      errors: {} as FieldErrors
    }
  }),
  getters: {
    resetCanSend(state): boolean {
      return state.reset.phone.length === 11 && state.reset.countdown <= 0 && !state.reset.sending
    },
    recoverCanSend(state): boolean {
      return (
        state.recover.oldPhone.length === 11 &&
        state.recover.newPhone.length === 11 &&
        state.recover.newPhone !== state.recover.oldPhone &&
        state.recover.countdown <= 0 &&
        !state.recover.sending
      )
    },
    recoverDirty(state): boolean {
      return !!(
        state.recover.recoveryCode ||
        state.recover.newPhone ||
        state.recover.newPhoneCode ||
        state.recover.description
      )
    },
    descriptionCount(state): number {
      return textLength(state.recover.description)
    },
    recoveryCodeDisplay(state): string {
      const code = normalizeRecoveryCode(state.recover.recoveryCode)
      return code.replace(/(.{4})/g, '$1 ').trim()
    },
    forceStep(state): number {
      return state.force.code ? 2 : 1
    }
  },
  actions: {
    async init(stepParam?: string, tabParam?: string): Promise<void> {
      this.initializing = true
      const auth = useAuthStore()
      try {
        const agreement = await getCurrentAgreement()
        if (agreement.customer_service_phone) this.servicePhone = agreement.customer_service_phone
      } catch {
        /* 使用默认客服电话 */
      }
      try {
        if (stepParam === 'RECOVERY_CHANGE_PASSWORD') {
          if (!auth.isLoggedIn) await auth.refresh()
          await this.loadForcedStep()
          return
        }
        if (stepParam === 'SAVE_RECOVERY_CODE' || stepParam === 'RECOVERY_SAVE_CODE') {
          if (!auth.isLoggedIn) await auth.refresh()
          await this.loadPendingCode()
          return
        }
        if (tabParam === 'recover') {
          this.mode = 'recover'
          return
        }
        if (tabParam === 'reset') {
          this.mode = 'reset'
          return
        }
        if (auth.isLoggedIn) {
          const session = await getSession()
          await this.applySession(session)
          return
        }
        if (auth.nextStep === 'RECOVERY_CHANGE_PASSWORD') {
          await this.loadForcedStep()
        } else if (auth.nextStep === 'SAVE_RECOVERY_CODE') {
          await this.loadPendingCode()
        }
      } catch {
        this.mode = 'reset'
      } finally {
        this.initializing = false
      }
    },
    async applySession(session: SessionVO): Promise<void> {
      if (session.next_step === 'RECOVERY_CHANGE_PASSWORD') {
        this.mode = 'change'
        this.force.recoveryInfo = session.recovery_info || null
        return
      }
      if (session.next_step === 'SAVE_RECOVERY_CODE') {
        await this.loadPendingCode()
        return
      }
      this.mode = 'reset'
    },
    async loadForcedStep(): Promise<void> {
      try {
        const session = await getSession()
        if (session.next_step === 'RECOVERY_CHANGE_PASSWORD') {
          this.mode = 'change'
          this.force.recoveryInfo = session.recovery_info || null
        } else if (session.next_step === 'SAVE_RECOVERY_CODE') {
          await this.loadPendingCode()
        } else {
          this.mode = 'reset'
        }
      } catch {
        this.mode = 'reset'
      }
    },
    async loadPendingCode(): Promise<void> {
      this.mode = 'save'
      try {
        this.force.code = await regeneratePendingRecoveryCode()
        this.force.saved = false
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40309) {
          await this.loadForcedStep()
          return
        }
        useUiStore().toast(err.message || '恢复码加载失败，请稍后重试', 'error')
        this.mode = 'reset'
      }
    },
    setMode(mode: RecoverMode): void {
      this.mode = mode
    },
    /* ---------------- 重置密码 ---------------- */
    sendResetCode(): void {
      if (this.reset.sending || this.reset.countdown > 0) return
      const problem = phoneProblem(this.reset.phone)
      if (problem) {
        this.reset.errors.phone = problem
        return
      }
      this.reset.sending = true
      sendSmsCode({ phone: this.reset.phone, scene: 'RESET_PASSWORD' })
        .then((data) => {
          this.reset.masked = data.phone_masked || ''
          this.startCountdown('reset', data.cooldown_seconds || 60)
          useUiStore().toast('验证码已发送')
        })
        .catch((e) => {
          const err = e as ApiError
          if (err.code === 42901) useUiStore().toast(MSG_TOO_FREQUENT, 'error')
          else if (err.code === 40001 && err.fieldErrors.length) {
            this.reset.errors.phone = err.fieldErrors[0].message
          } else useUiStore().toast(err.message || MSG_SMS_SEND_FAILED, 'error')
        })
        .finally(() => {
          this.reset.sending = false
        })
    },
    async submitReset(): Promise<boolean> {
      if (this.reset.submitting) return false
      const errors: FieldErrors = {}
      const phone = phoneProblem(this.reset.phone)
      if (phone) errors.phone = phone
      const code = smsCodeProblem(this.reset.smsCode)
      if (code) errors.sms_code = code
      const pwd = passwordProblem(this.reset.password)
      if (pwd) errors.new_password = pwd
      this.reset.errors = errors
      if (Object.keys(errors).length) return false
      this.reset.submitting = true
      try {
        await resetPassword({
          phone: this.reset.phone,
          sms_code: this.reset.smsCode,
          new_password: this.reset.password
        })
        return true
      } catch (e) {
        this.handleResetError(e as ApiError)
        return false
      } finally {
        this.reset.submitting = false
      }
    },
    handleResetError(err: ApiError): void {
      if (err.code === 40001) {
        if ((err.data as { reason?: string } | undefined)?.reason === 'PHONE_NOT_REGISTERED') {
          this.reset.errors.phone = '这个手机号还没有注册'
          return
        }
        if (err.fieldErrors.length) {
          for (const item of err.fieldErrors) {
            if (item.field === 'new_password') this.reset.errors.new_password = item.message
            else this.reset.errors[item.field] = item.message
          }
          return
        }
      }
      if (err.code === 40103) {
        this.reset.errors.sms_code = err.message
        return
      }
      if (err.code === 42901) {
        useUiStore().toast(MSG_TOO_FREQUENT, 'error')
        return
      }
      useUiStore().toast(err.isNetwork ? MSG_NETWORK : err.message || '重置失败，请稍后重试', 'error')
    },
    /* ---------------- 找回账号 ---------------- */
    sendRecoverCode(): void {
      if (this.recover.sending || this.recover.countdown > 0) return
      const errors: FieldErrors = {}
      const oldPhone = phoneProblem(this.recover.oldPhone)
      if (oldPhone) errors.old_phone = oldPhone
      const newPhone = phoneProblem(this.recover.newPhone)
      if (newPhone) errors.new_phone = newPhone
      if (!oldPhone && !newPhone && this.recover.oldPhone === this.recover.newPhone) {
        errors.new_phone = MSG_SAME_PHONE
      }
      this.recover.errors = { ...this.recover.errors, ...errors }
      if (Object.keys(errors).length) return
      this.recover.sending = true
      sendSmsCode({
        phone: this.recover.newPhone,
        scene: 'RECOVERY_NEW_PHONE',
        old_phone: this.recover.oldPhone
      })
        .then((data) => {
          this.recover.masked = data.phone_masked || ''
          this.startCountdown('recover', data.cooldown_seconds || 60)
          useUiStore().toast('验证码已发送')
        })
        .catch((e) => {
          const err = e as ApiError
          if (err.code === 40904) {
            this.recover.errors.new_phone = err.message
          } else if (err.code === 40001 && err.fieldErrors.length) {
            for (const item of err.fieldErrors) {
              this.recover.errors[item.field === 'phone' ? 'new_phone' : item.field] = item.message
            }
          } else if (err.code === 42901) {
            useUiStore().toast(MSG_TOO_FREQUENT, 'error')
          } else {
            useUiStore().toast(err.message || MSG_SMS_SEND_FAILED, 'error')
          }
        })
        .finally(() => {
          this.recover.sending = false
        })
    },
    async submitRecover(): Promise<string | null> {
      if (this.recover.submitting || this.recover.lockBanner) return null
      const errors: FieldErrors = {}
      const oldPhone = phoneProblem(this.recover.oldPhone)
      if (oldPhone) errors.old_phone = oldPhone
      const codeProblem = recoveryCodeProblem(this.recover.recoveryCode)
      if (codeProblem) errors.recovery_code = codeProblem
      const newPhone = phoneProblem(this.recover.newPhone)
      if (newPhone) errors.new_phone = newPhone
      if (!oldPhone && !newPhone && this.recover.oldPhone === this.recover.newPhone) {
        errors.new_phone = MSG_SAME_PHONE
      }
      const smsProblem = smsCodeProblem(this.recover.newPhoneCode)
      if (smsProblem) errors.new_phone_sms_code = smsProblem
      const description = this.recover.description.trim()
      if (!description) errors.description = MSG_DESCRIPTION
      else if (textLength(description) > DESCRIPTION_MAX) errors.description = MSG_DESCRIPTION
      this.recover.errors = errors
      if (Object.keys(errors).length) return null
      this.recover.submitting = true
      try {
        const data = await submitAccountRecovery({
          old_phone: this.recover.oldPhone,
          recovery_code: normalizeRecoveryCode(this.recover.recoveryCode),
          new_phone: this.recover.newPhone,
          new_phone_sms_code: this.recover.newPhoneCode,
          description
        })
        return data.new_phone_masked || this.recover.newPhone
      } catch (e) {
        this.handleRecoverError(e as ApiError)
        return null
      } finally {
        this.recover.submitting = false
      }
    },
    handleRecoverError(err: ApiError): void {
      if (err.code === 40105) {
        this.recover.errors.recovery_code = err.message
        return
      }
      if (err.code === 42903) {
        delete this.recover.errors.recovery_code
        this.recover.lockBanner = err.message
        return
      }
      if (err.code === 40904) {
        this.recover.errors.new_phone = err.message
        return
      }
      if (err.code === 40906 || err.code === 40301) {
        useUiStore().toast(err.message, 'info')
        return
      }
      if (err.code === 40001) {
        if (err.fieldErrors.length) {
          for (const item of err.fieldErrors) {
            this.recover.errors[item.field] = item.message
          }
          return
        }
      }
      if (err.code === 40103) {
        this.recover.errors.new_phone_sms_code = err.message
        return
      }
      useUiStore().toast(err.isNetwork ? MSG_NETWORK : err.message || '找回申请提交失败，请稍后重试', 'error')
    },
    /* ---------------- 找回后强制两步 ---------------- */
    setForcePassword(value: string): void {
      this.force.password = String(value ?? '')
      delete this.force.errors.new_password
    },
    setForceConfirm(value: string): void {
      this.force.confirmPassword = String(value ?? '')
      delete this.force.errors.confirm_password
    },
    async submitForcedPassword(): Promise<boolean> {
      if (this.force.submitting) return false
      const errors: FieldErrors = {}
      const pwd = passwordProblem(this.force.password)
      if (pwd) errors.new_password = pwd
      if (!this.force.confirmPassword) errors.confirm_password = '请再次输入新密码'
      else if (this.force.password !== this.force.confirmPassword) {
        errors.confirm_password = '两次输入的密码不一致'
      }
      this.force.errors = errors
      if (Object.keys(errors).length) return false
      this.force.submitting = true
      try {
        const token = await changePasswordAfterRecovery({
          new_password: this.force.password,
          confirm_password: this.force.confirmPassword
        })
        useAuthStore().applyToken(token)
        this.force.password = ''
        this.force.confirmPassword = ''
        if (token.recovery_code?.code) {
          this.force.code = token.recovery_code || null
          this.force.saved = false
          this.mode = 'save'
          useUiStore().toast('密码已修改，新的恢复码已生效')
        } else {
          await this.loadPendingCode()
          useUiStore().toast('密码已修改，新的恢复码已生效')
        }
        return true
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40001 && err.fieldErrors.length) {
          for (const item of err.fieldErrors) this.force.errors[item.field] = item.message
          return false
        }
        if (err.code === 40309) {
          await this.loadForcedStep()
          return false
        }
        useUiStore().toast(err.isNetwork ? MSG_NETWORK : err.message || '修改密码失败，请稍后重试', 'error')
        return false
      } finally {
        this.force.submitting = false
      }
    },
    async confirmForcedCode(): Promise<boolean> {
      const id = this.force.code?.recovery_code_id
      if (!id || !this.force.saved || this.force.submitting) return false
      this.force.submitting = true
      try {
        await confirmRecoveryCode(id)
        const auth = useAuthStore()
        auth.nextStep = ''
        auth.nextStepText = ''
        auth.recoveryCode = ''
        this.force.code = null
        return true
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40903 || err.code === 40401) {
          useUiStore().toast('恢复码已更新，请保存最新的恢复码', 'info')
          this.force.saved = false
          await this.loadPendingCode()
          return false
        }
        useUiStore().toast(err.message || '保存恢复码失败，请稍后重试', 'error')
        return false
      } finally {
        this.force.submitting = false
      }
    },
    startCountdown(target: 'reset' | 'recover', seconds: number): void {
      window.clearInterval(target === 'reset' ? resetTimer : recoverTimer)
      const set = (value: number): void => {
        if (target === 'reset') this.reset.countdown = value
        else this.recover.countdown = value
      }
      set(Math.max(1, Number(seconds) || 60))
      const id = window.setInterval(() => {
        const current = target === 'reset' ? this.reset.countdown : this.recover.countdown
        const next = current - 1
        set(next)
        if (next <= 0) window.clearInterval(id)
      }, 1000)
      if (target === 'reset') resetTimer = id
      else recoverTimer = id
    },
    stopTimers(): void {
      window.clearInterval(resetTimer)
      window.clearInterval(recoverTimer)
    },
    setResetPhone(value: string): void {
      this.reset.phone = phoneDigits(value)
      delete this.reset.errors.phone
    },
    setResetCode(value: string): void {
      this.reset.smsCode = digits(value).slice(0, 6)
      delete this.reset.errors.sms_code
    },
    setResetPassword(value: string): void {
      this.reset.password = String(value ?? '')
      delete this.reset.errors.new_password
    },
    setOldPhone(value: string): void {
      this.recover.oldPhone = phoneDigits(value)
      delete this.recover.errors.old_phone
    },
    setNewPhone(value: string): void {
      this.recover.newPhone = phoneDigits(value)
      delete this.recover.errors.new_phone
    },
    setRecoveryCode(value: string): void {
      this.recover.recoveryCode = normalizeRecoveryCode(value)
      delete this.recover.errors.recovery_code
    },
    setNewPhoneCode(value: string): void {
      this.recover.newPhoneCode = digits(value).slice(0, 6)
      delete this.recover.errors.new_phone_sms_code
    },
    setDescription(value: string): void {
      this.recover.description = String(value ?? '').slice(0, 200)
      delete this.recover.errors.description
    }
  }
})

function phoneDigits(value: string): string {
  return String(value ?? '')
    .replace(/[^\d]/g, '')
    .slice(0, 11)
}

function digits(value: string): string {
  return String(value ?? '').replace(/[^\d]/g, '')
}
