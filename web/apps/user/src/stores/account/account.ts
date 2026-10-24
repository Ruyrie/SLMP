import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import router from '@/router'
import {
  bindAvatar,
  cancelAccount,
  changePassword,
  clearAvatar,
  confirmRecoveryCode,
  getAccountOverview,
  getCancellationPreview,
  getCurrentAgreement,
  regeneratePendingRecoveryCode,
  regenerateRecoveryCode,
  sendAccountSmsCode,
  updateProfile,
  uploadAvatar,
  type AccountOverviewVO,
  type AccountUserVO,
  type AgreementVO,
  type CancelOwnedFarmVO,
  type CancellationPreviewVO,
  type RecoveryCodeVO
} from '@/api/account'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import {
  MSG_NETWORK,
  MSG_TOO_FREQUENT,
  MSG_SMS_SEND_FAILED,
  MSG_PASSWORD_EMPTY,
  MSG_PASSWORD_MISMATCH,
  MSG_PASSWORD_SAME_CURRENT,
  nameProblem,
  passwordProblem,
  smsCodeProblem
} from '@/views/account/accountForm'

export type AccountPageStatus = 'loading' | 'ready' | 'error' | 'forbidden'

const RECOVER_BLOCK = 'RECOVERED_WITHIN_7_DAYS'
const ACTIVE_BLOCK = 'OWNED_FARM_ACTIVE'
const FROZEN_BLOCK = 'OWNED_FARM_FROZEN'

let recoveryTimer = 0
let cancelTimer = 0

interface FieldErrors {
  [key: string]: string
}

export const useAccountStore = defineStore('account', {
  state: () => ({
    status: 'loading' as AccountPageStatus,
    error: '',
    overview: null as AccountOverviewVO | null,
    agreement: null as AgreementVO | null,
    agreementLoading: false,
    nameEditing: false,
    nameDraft: '',
    nameSaving: false,
    nameError: '',
    avatarUploading: false,
    avatarProgress: 0,
    passwordOpen: false,
    password: {
      current: '',
      next: '',
      confirm: '',
      showCurrent: false,
      showNext: false,
      errors: {} as FieldErrors,
      submitting: false
    },
    recoveryOpen: false,
    recoveryStep: 1,
    recovery: {
      smsCode: '',
      masked: '',
      countdown: 0,
      sending: false,
      submitting: false,
      code: null as RecoveryCodeVO | null,
      saved: false,
      errors: {} as FieldErrors
    },
    cancelOpen: false,
    cancelLoading: false,
    cancelError: '',
    preview: null as CancellationPreviewVO | null,
    cancelConfirmed: false,
    cancelSmsCode: '',
    cancelMasked: '',
    cancelCountdown: 0,
    cancelSending: false,
    cancelSubmitting: false,
    cancelSmsError: '',
    aboutOpen: false
  }),
  getters: {
    user(state): AccountUserVO | null {
      return state.overview?.user || null
    },
    phoneMasked(state): string {
      return state.overview?.security?.phone_masked || state.overview?.user?.phone_masked || ''
    },
    farmStats(state) {
      return state.overview?.farm_stats || {}
    },
    pendingChangeRequests(state): number {
      return Number(state.overview?.change_request_pending_count || 0)
    },
    reminderText(state): string {
      const r = state.overview?.reminder_summary
      if (!r) return ''
      if (!r.digest_enabled) return '待办汇总已关闭'
      let text = r.digest_time ? `待办汇总 ${r.digest_time}` : '待办汇总'
      if (r.quiet_enabled && r.quiet_start && r.quiet_end) {
        text += ` · 免打扰 ${r.quiet_start}–${r.quiet_end}`
      }
      return text
    },
    currentFarmLabel(state): string {
      const farm = state.overview?.current_farm
      if (!farm) return ''
      const role =
        farm.role_text ||
        (farm.role === 'OWNER' ? '所有者' : farm.role === 'MANAGER' ? '场长' : farm.role === 'STAFF' ? '员工' : '')
      return `${farm.name || ''}${role ? ` · ${role}` : ''}`
    },
    blockingFarms(state): CancelOwnedFarmVO[] {
      const list = state.preview?.owned_farms || []
      return list.filter(
        (farm) => farm.disposal === 'TRANSFER_OR_DISABLE' || farm.disposal === 'WAIT_UNFREEZE'
      )
    },
    activeOwnedFarms(): CancelOwnedFarmVO[] {
      return this.blockingFarms.filter((farm) => farm.disposal === 'TRANSFER_OR_DISABLE')
    },
    transferCandidates(): CancelOwnedFarmVO[] {
      return this.activeOwnedFarms.filter(
        (farm) => farm.transferable !== false && !farm.transfer_pending_to_name
      )
    },
    disableCandidates(): CancelOwnedFarmVO[] {
      return this.activeOwnedFarms
    },
    frozenOwnedFarms(): CancelOwnedFarmVO[] {
      return this.blockingFarms.filter((farm) => farm.disposal === 'WAIT_UNFREEZE')
    },
    retainedOwnedFarms(state): CancelOwnedFarmVO[] {
      return (state.preview?.owned_farms || []).filter(
        (farm) => farm.disposal === 'RETAIN_180_DAYS'
      )
    },
    exitNames(state): string {
      return (state.preview?.exit_farms || []).map((farm) => farm.name || '').filter(Boolean).join('、')
    },
    recoveryRestricted(state): boolean {
      const preview = state.preview
      if (!preview) return false
      if (preview.cancel_available_date) return true
      return (preview.block_reasons || []).includes(RECOVER_BLOCK)
    },
    canCancel(state): boolean {
      return !!state.preview?.can_cancel
    },
    cancelActive(state): boolean {
      return (state.preview?.block_reasons || []).includes(ACTIVE_BLOCK)
    },
    cancelFrozen(state): boolean {
      return (state.preview?.block_reasons || []).includes(FROZEN_BLOCK)
    }
  },
  actions: {
    async load(): Promise<void> {
      if (!this.overview) this.status = 'loading'
      this.error = ''
      try {
        this.overview = await getAccountOverview()
        if (this.overview.user) this.applyUserToSession(this.overview.user)
        this.status = 'ready'
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40309) {
          this.redirectForcedStep(err)
          return
        }
        this.status = err.isForbidden ? 'forbidden' : 'error'
        this.error = err.message || '个人信息加载失败，请检查网络后重新加载。'
      }
    },
    redirectForcedStep(err: ApiError): void {
      const next = String((err.data as { next_step?: string } | undefined)?.next_step || '')
      if (next === 'RECOVERY_CHANGE_PASSWORD') {
        void router.replace({ name: 'WU-A03', query: { step: 'RECOVERY_CHANGE_PASSWORD' } })
        return
      }
      if (next === 'SAVE_RECOVERY_CODE') {
        void router.replace({ name: 'WU-A02', query: { step: 'SAVE_RECOVERY_CODE' } })
        return
      }
      this.status = 'error'
      this.error = err.message
    },
    async loadAgreement(): Promise<void> {
      if (this.agreement || this.agreementLoading) return
      this.agreementLoading = true
      try {
        this.agreement = await getCurrentAgreement()
      } catch {
        /* 协议打开失败时静默，点击链接时再重试 */
      } finally {
        this.agreementLoading = false
      }
    },
    openAgreementUrl(which: 'user' | 'privacy'): void {
      const item = which === 'user' ? this.agreement?.user_agreement : this.agreement?.privacy_policy
      if (!item?.url) {
        useUiStore().toast('协议加载失败，请稍后重试', 'error')
        void this.loadAgreement()
        return
      }
      window.open(item.url, '_blank', 'noopener,noreferrer')
    },
    /* ---------------- 姓名 ---------------- */
    startNameEdit(): void {
      if (!this.overview) {
        useUiStore().toast('个人信息还没有加载完成，请刷新后再修改', 'info')
        return
      }
      this.nameDraft = this.overview.user?.name || ''
      this.nameError = ''
      this.nameEditing = true
    },
    cancelNameEdit(): void {
      this.nameEditing = false
      this.nameError = ''
    },
    async saveName(): Promise<void> {
      if (this.nameSaving) return
      const problem = nameProblem(this.nameDraft)
      if (problem) {
        this.nameError = problem
        return
      }
      if (!this.overview) return
      const name = this.nameDraft.trim()
      this.nameSaving = true
      try {
        const data = await updateProfile(name, Number(this.overview.profile_version || 0))
        if (!this.overview.user) this.overview.user = {}
        this.overview.user.name = data.name || name
        this.overview.user.name_initial = data.name_initial || this.overview.user.name_initial
        this.overview.profile_version = Number(data.version || 0)
        this.applyUserToSession(this.overview.user)
        this.nameEditing = false
        this.nameError = ''
        useUiStore().toast('已保存姓名')
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40001) {
          const item = err.fieldErrors.find((x) => x.field === 'name')
          this.nameError = item?.message || err.message
          return
        }
        if (err.code === 40902) {
          this.nameEditing = false
          useUiStore().toast('内容已更新，请刷新后再修改', 'error')
          await this.load()
          return
        }
        if (err.code === 40309) {
          this.redirectForcedStep(err)
          return
        }
        useUiStore().toast(err.isNetwork ? MSG_NETWORK : err.message, 'error')
      } finally {
        this.nameSaving = false
      }
    },
    applyUserToSession(user: AccountUserVO): void {
      const auth = useAuthStore()
      auth.user = {
        ...(auth.user || {}),
        ...user,
        user_id: user.user_id ?? auth.user?.user_id,
        avatar_url: user.avatar_url !== undefined ? user.avatar_url : auth.user?.avatar_url
      }
    },
    /* ---------------- 头像 ---------------- */
    async selectAvatar(file: File): Promise<void> {
      if (this.avatarUploading) return
      if (!['image/jpeg', 'image/png'].includes(file.type)) {
        useUiStore().toast('只支持 JPG 或 PNG 图片，单张不超过 5MB', 'error')
        return
      }
      if (file.size > 5 * 1024 * 1024) {
        useUiStore().toast('只支持 JPG 或 PNG 图片，单张不超过 5MB', 'error')
        return
      }
      this.avatarUploading = true
      this.avatarProgress = 0
      try {
        const uploaded = await uploadAvatar(file, (percent) => {
          this.avatarProgress = percent
        })
        const user = await bindAvatar(uploaded.file_id)
        const avatarUrl = user.avatar_url || uploaded.url || null
        this.updateAvatarUrl(avatarUrl)
        this.applyUserToSession({ ...user, avatar_url: avatarUrl })
        useUiStore().toast('已更新头像')
        await this.load()
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40002) useUiStore().toast(err.message, 'error')
        else if (err.code === 40309) this.redirectForcedStep(err)
        else useUiStore().toast('头像上传失败，请稍后重试', 'error')
      } finally {
        this.avatarUploading = false
        this.avatarProgress = 0
      }
    },
    async removeAvatar(): Promise<void> {
      if (this.avatarUploading) return
      this.avatarUploading = true
      try {
        const user = await clearAvatar()
        this.updateAvatarUrl(null)
        this.applyUserToSession({ ...user, avatar_url: null })
        useUiStore().toast('已恢复默认头像')
        await this.load()
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40309) this.redirectForcedStep(err)
        else useUiStore().toast('恢复默认头像失败，请稍后重试', 'error')
      } finally {
        this.avatarUploading = false
      }
    },
    updateAvatarUrl(url: string | null): void {
      if (this.overview?.user) this.overview.user.avatar_url = url
    },
    /* ---------------- 修改密码 ---------------- */
    openPassword(): void {
      this.password = {
        current: '',
        next: '',
        confirm: '',
        showCurrent: false,
        showNext: false,
        errors: {},
        submitting: false
      }
      this.passwordOpen = true
    },
    closePassword(): void {
      if (!this.password.submitting) this.passwordOpen = false
    },
    async submitPassword(): Promise<void> {
      if (this.password.submitting) return
      const errors: FieldErrors = {}
      if (!this.password.current) errors.current_password = MSG_PASSWORD_EMPTY
      const nextProblem = passwordProblem(this.password.next)
      if (nextProblem) errors.new_password = nextProblem
      if (this.password.next && this.password.next === this.password.current) {
        errors.new_password = MSG_PASSWORD_SAME_CURRENT
      }
      if (!this.password.confirm) errors.confirm_password = MSG_PASSWORD_MISMATCH
      else if (this.password.next !== this.password.confirm) {
        errors.confirm_password = MSG_PASSWORD_MISMATCH
      }
      this.password.errors = errors
      if (Object.keys(errors).length) return
      this.password.submitting = true
      try {
        const token = await changePassword({
          current_password: this.password.current,
          new_password: this.password.next,
          confirm_password: this.password.confirm
        })
        useAuthStore().applyToken(token)
        this.passwordOpen = false
        useUiStore().toast('密码已修改，其他设备需要重新登录')
        await this.load()
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40001 && err.fieldErrors.length) {
          for (const item of err.fieldErrors) this.password.errors[item.field] = item.message
          return
        }
        if (err.code === 42902) {
          this.password.errors.current_password = err.message
          return
        }
        if (err.code === 40309) {
          this.redirectForcedStep(err)
          return
        }
        useUiStore().toast(err.isNetwork ? MSG_NETWORK : err.message, 'error')
      } finally {
        this.password.submitting = false
      }
    },
    /* ---------------- 重新生成恢复码 ---------------- */
    openRecovery(): void {
      window.clearInterval(recoveryTimer)
      this.recoveryStep = 1
      this.recovery = {
        smsCode: '',
        masked: '',
        countdown: 0,
        sending: false,
        submitting: false,
        code: null,
        saved: false,
        errors: {}
      }
      this.recoveryOpen = true
    },
    closeRecovery(): void {
      if (this.recovery.submitting) return
      if (this.recoveryStep === 2 && this.recovery.code) return
      this.recoveryOpen = false
    },
    async sendRecoverySms(): Promise<void> {
      if (this.recovery.sending || this.recovery.countdown > 0) return
      this.recovery.sending = true
      try {
        const data = await sendAccountSmsCode('REGENERATE_RECOVERY_CODE')
        this.recovery.masked = data.phone_masked || this.phoneMasked
        this.startRecoveryCountdown(data.cooldown_seconds || 60)
        useUiStore().toast('验证码已发送')
      } catch (e) {
        const err = e as ApiError
        if (err.code === 42901) useUiStore().toast(MSG_TOO_FREQUENT, 'error')
        else if (err.code === 40001 && err.fieldErrors.length) {
          this.recovery.errors.sms_code = err.fieldErrors[0].message
        } else useUiStore().toast(err.message || MSG_SMS_SEND_FAILED, 'error')
      } finally {
        this.recovery.sending = false
      }
    },
    startRecoveryCountdown(seconds: number): void {
      window.clearInterval(recoveryTimer)
      this.recovery.countdown = Math.max(1, Number(seconds) || 60)
      recoveryTimer = window.setInterval(() => {
        this.recovery.countdown -= 1
        if (this.recovery.countdown <= 0) window.clearInterval(recoveryTimer)
      }, 1000)
    },
    setRecoverySms(value: string): void {
      this.recovery.smsCode = String(value ?? '')
        .replace(/[^\d]/g, '')
        .slice(0, 6)
      delete this.recovery.errors.sms_code
    },
    async submitRecoveryCode(): Promise<void> {
      if (this.recovery.submitting) return
      const problem = smsCodeProblem(this.recovery.smsCode)
      if (problem) {
        this.recovery.errors.sms_code = problem
        return
      }
      this.recovery.submitting = true
      try {
        const code = await regenerateRecoveryCode(this.recovery.smsCode)
        this.recovery.code = code
        this.recovery.saved = false
        this.recoveryStep = 2
        this.recovery.errors = {}
        useUiStore().toast('已生成新的恢复码，旧恢复码已作废')
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40103) {
          this.recovery.errors.sms_code = err.message
          return
        }
        if (err.code === 40001 && err.fieldErrors.length) {
          this.recovery.errors.sms_code = err.fieldErrors[0].message
          return
        }
        if (err.code === 40309) {
          this.redirectForcedStep(err)
          return
        }
        useUiStore().toast(err.isNetwork ? MSG_NETWORK : err.message, 'error')
      } finally {
        this.recovery.submitting = false
      }
    },
    async confirmRecovery(): Promise<void> {
      const id = this.recovery.code?.recovery_code_id
      if (!id || !this.recovery.saved || this.recovery.submitting) return
      this.recovery.submitting = true
      try {
        await confirmRecoveryCode(id)
        this.recoveryOpen = false
        this.recovery.code = null
        useUiStore().toast('已生成新的恢复码，旧恢复码已作废')
        await this.load()
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40903 || err.code === 40401) {
          useUiStore().toast('恢复码已更新，请保存最新的恢复码', 'info')
          this.recovery.saved = false
          try {
            this.recovery.code = await regeneratePendingRecoveryCode()
          } catch {
            /* 保持当前码 */
          }
          return
        }
        useUiStore().toast(err.message || '保存恢复码失败，请稍后重试', 'error')
      } finally {
        this.recovery.submitting = false
      }
    },
    /* ---------------- 注销账号 ---------------- */
    async openCancel(): Promise<void> {
      window.clearInterval(cancelTimer)
      this.cancelOpen = true
      this.cancelConfirmed = false
      this.cancelSmsCode = ''
      this.cancelSmsError = ''
      this.cancelCountdown = 0
      await this.loadCancellationPreview()
    },
    async loadCancellationPreview(): Promise<void> {
      this.cancelLoading = true
      this.cancelError = ''
      try {
        this.preview = await getCancellationPreview()
        this.cancelMasked = ''
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40309) {
          this.cancelOpen = false
          this.redirectForcedStep(err)
          return
        }
        this.preview = null
        this.cancelError = err.message || '注销信息加载失败'
      } finally {
        this.cancelLoading = false
      }
    },
    closeCancel(): void {
      if (!this.cancelSubmitting) this.cancelOpen = false
    },
    setCancelConfirmed(value: boolean | null): void {
      this.cancelConfirmed = value === true
      this.cancelSmsError = ''
    },
    setCancelSms(value: string): void {
      this.cancelSmsCode = String(value ?? '')
        .replace(/[^\d]/g, '')
        .slice(0, 6)
      this.cancelSmsError = ''
    },
    async sendCancelSms(): Promise<void> {
      if (this.cancelSending || this.cancelCountdown > 0) return
      this.cancelSending = true
      try {
        const data = await sendAccountSmsCode('CANCEL_ACCOUNT')
        this.cancelMasked = data.phone_masked || this.phoneMasked
        this.startCancelCountdown(data.cooldown_seconds || 60)
        useUiStore().toast('验证码已发送')
      } catch (e) {
        const err = e as ApiError
        if (err.code === 42901) useUiStore().toast(MSG_TOO_FREQUENT, 'error')
        else if (err.code === 40310) useUiStore().toast(err.message, 'error')
        else useUiStore().toast(err.message || MSG_SMS_SEND_FAILED, 'error')
      } finally {
        this.cancelSending = false
      }
    },
    startCancelCountdown(seconds: number): void {
      window.clearInterval(cancelTimer)
      this.cancelCountdown = Math.max(1, Number(seconds) || 60)
      cancelTimer = window.setInterval(() => {
        this.cancelCountdown -= 1
        if (this.cancelCountdown <= 0) window.clearInterval(cancelTimer)
      }, 1000)
    },
    async submitCancel(): Promise<boolean> {
      if (this.cancelSubmitting || !this.preview?.can_cancel) return false
      const errors: FieldErrors = {}
      if (!this.cancelConfirmed) errors.confirmed = '请先勾选“我已了解注销后果”'
      const problem = smsCodeProblem(this.cancelSmsCode)
      if (problem) errors.sms_code = problem
      this.cancelSmsError = errors.sms_code || errors.confirmed || ''
      if (Object.keys(errors).length) return false
      this.cancelSubmitting = true
      try {
        await cancelAccount({
          sms_code: this.cancelSmsCode,
          confirmed: this.cancelConfirmed,
          checklist_version: this.preview.checklist_version || ''
        })
        return true
      } catch (e) {
        const err = e as ApiError
        if (err.code === 40310 || err.code === 40902) {
          useUiStore().toast(err.message, 'error')
          await this.loadCancellationPreview()
          this.cancelConfirmed = false
          this.cancelSmsCode = ''
          return false
        }
        if (err.code === 40103) {
          this.cancelSmsError = err.message
          return false
        }
        if (err.code === 40309) {
          this.cancelOpen = false
          this.redirectForcedStep(err)
          return false
        }
        useUiStore().toast(err.isNetwork ? MSG_NETWORK : err.message, 'error')
        return false
      } finally {
        this.cancelSubmitting = false
      }
    },
    openAbout(): void {
      this.aboutOpen = true
      void this.loadAgreement()
    },
    stopTimers(): void {
      window.clearInterval(recoveryTimer)
      window.clearInterval(cancelTimer)
    }
  }
})
