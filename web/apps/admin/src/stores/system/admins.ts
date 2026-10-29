import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import {
  checkAdminUsername,
  createAdmin,
  getAdminPage,
  getRolePermissions,
  getRoles,
  resetAdminPassword,
  saveRolePermissions,
  setAdminStatus,
  updateAdmin,
  type AdminRow,
  type PermissionCell,
  type RoleItem,
  type RolePermission
} from '@/api/system'
import { useUiStore } from '@/stores/ui'

export type LoadStatus = 'loading' | 'ready' | 'empty' | 'error'

interface AdminForm {
  name: string
  username: string
  phone: string
  role_id: number | null
  reason: string
}

function emptyForm(): AdminForm {
  return { name: '', username: '', phone: '', role_id: null, reason: '' }
}

export const useAdminsStore = defineStore('admins', {
  state: () => ({
    tab: 'admins' as 'admins' | 'roles',
    query: { page: 1, size: 20 },
    list: [] as AdminRow[],
    total: 0,
    status: 'loading' as LoadStatus,
    error: '',
    roles: [] as RoleItem[],
    selectedRoleId: 0,
    permission: null as RolePermission | null,
    draft: [] as string[],
    permissionReason: '',
    permissionLoading: false,
    savingPermissions: false,
    drawer: false,
    editing: null as AdminRow | null,
    form: emptyForm(),
    formErrors: {} as Record<string, string>,
    submitting: false,
    resetOpen: false,
    resetTarget: null as AdminRow | null,
    resetReason: '',
    resetting: false,
    tempPassword: '',
    tempExpireAt: '',
    tempTitle: '',
    tempTarget: '',
    tempOpen: false,
    usernameChecking: false,
    usernameChecked: '',
    statusOpen: false,
    statusTarget: null as AdminRow | null,
    statusAction: 'disable' as 'disable' | 'enable',
    statusReason: '',
    statusSaving: false
  }),
  getters: {
    isCreate: (state) => !state.editing,
    roleOptions: (state) => state.roles.map((role) => ({ title: role.role_name, value: role.role_id })),
    membersText: (state) => (state.permission?.members || []).map((member) => `${member.name}${member.status === 'DISABLED' ? '（已停用）' : ''}`).join('、')
  },
  actions: {
    async fetchAdmins(): Promise<void> {
      this.status = this.list.length ? this.status : 'loading'
      this.error = ''
      try {
        const data = await getAdminPage(this.query)
        this.list = data.list || []
        this.total = data.total || 0
        this.status = this.list.length ? 'ready' : 'empty'
      } catch (error) {
        this.status = 'error'
        this.error = error instanceof Error ? error.message : '加载失败'
      }
    },
    async fetchRoles(): Promise<void> {
      const roles = await getRoles()
      this.roles = roles || []
      if (this.roles.length && !this.roles.some((role) => role.role_id === this.selectedRoleId)) {
        this.selectedRoleId = this.roles[0]!.role_id
      }
      if (this.selectedRoleId) await this.loadPermissions(this.selectedRoleId)
    },
    async loadPermissions(roleId: number): Promise<void> {
      this.permissionLoading = true
      this.permissionReason = ''
      try {
        this.permission = await getRolePermissions(roleId)
        this.draft = this.permission.rows
          .flatMap((row) => [row.view, row.handle].filter((cell): cell is PermissionCell => !!cell))
          .filter((cell) => cell.checked)
          .map((cell) => cell.perm_code)
      } catch (error) {
        useUiStore().toast(error instanceof Error ? error.message : '权限加载失败', 'error')
      } finally {
        this.permissionLoading = false
      }
    },
    async selectRole(roleId: number): Promise<void> {
      this.selectedRoleId = roleId
      await this.loadPermissions(roleId)
    },
    togglePermission(cell: PermissionCell, checked: boolean): void {
      if (cell.locked || !this.permission?.editable) return
      const set = new Set(this.draft)
      if (checked) set.add(cell.perm_code)
      else set.delete(cell.perm_code)
      if (cell.perm_code.endsWith(':HANDLE')) {
        const viewCode = cell.perm_code.replace(':HANDLE', ':VIEW')
        if (checked) set.add(viewCode)
      }
      if (cell.perm_code.endsWith(':VIEW') && !checked) {
        set.delete(cell.perm_code.replace(':VIEW', ':HANDLE'))
      }
      this.draft = [...set]
    },
    resetPermissions(): void {
      this.draft = [...(this.permission?.default_codes || [])]
    },
    async savePermissions(): Promise<void> {
      if (!this.permission) return
      if (!this.permissionReason.trim()) {
        useUiStore().toast('请填写修改原因', 'error')
        return
      }
      this.savingPermissions = true
      try {
        await saveRolePermissions(this.selectedRoleId, this.draft, this.permissionReason.trim(), this.permission.version)
        useUiStore().toast('权限已保存，已登录的管理员刷新后生效')
        await this.loadPermissions(this.selectedRoleId)
      } catch (error) {
        useUiStore().toast(error instanceof Error ? error.message : '保存失败', 'error')
      } finally {
        this.savingPermissions = false
      }
    },
    openCreate(): void {
      this.editing = null
      this.form = emptyForm()
      const normalRole = this.roles.find((role) => role.role_code !== 'SUPER_ADMIN') || this.roles[0]
      this.form.role_id = normalRole ? normalRole.role_id : null
      this.formErrors = {}
      this.usernameChecked = ''
      this.drawer = true
    },
    /** 新增管理员时登录账号失焦预检（包含已停用账号）；网络失败不拦截，保存时服务端仍会校验 */
    async checkUsername(): Promise<boolean> {
      if (!this.isCreate) return true
      const username = this.form.username.trim()
      if (!username) return true
      if (!/^[A-Za-z0-9]{4,20}$/.test(username)) {
        this.formErrors.username = '账号需为 4–20 位字母和数字'
        return false
      }
      if (this.usernameChecked === username && !this.formErrors.username) return true
      this.usernameChecking = true
      try {
        const result = await checkAdminUsername(username)
        if (this.form.username.trim() !== username) return true
        if (result && result.available === false) {
          this.formErrors.username = result.message || '这个登录账号已被使用（含已停用账号），请换一个'
          this.usernameChecked = ''
          return false
        }
        if (this.formErrors.username) delete this.formErrors.username
        this.usernameChecked = username
        return true
      } catch (error) {
        if (error instanceof ApiError && error.fieldErrors.length) {
          this.formErrors.username = error.fieldErrors[0]!.message
          return false
        }
        if (error instanceof ApiError && error.code === 40001 && error.message) {
          this.formErrors.username = error.message
          return false
        }
        return true
      } finally {
        this.usernameChecking = false
      }
    },
    showTempPassword(title: string, target: string, password?: string, expireAt?: string): void {
      this.tempTitle = title
      this.tempTarget = target
      this.tempPassword = password || ''
      this.tempExpireAt = expireAt || ''
      this.tempOpen = true
    },
    closeTempPassword(): void {
      this.tempOpen = false
      this.tempPassword = ''
      this.tempExpireAt = ''
    },
    openEdit(row: AdminRow): void {
      this.editing = row
      // 列表里的手机号是脱敏的，编辑时留空表示不改，填写才更新
      this.form = {
        name: row.name,
        username: row.username,
        phone: '',
        role_id: row.role_id || null,
        reason: ''
      }
      this.formErrors = {}
      this.drawer = true
    },
    async submit(): Promise<void> {
      this.formErrors = {}
      if (!this.form.name.trim()) this.formErrors.name = '请输入姓名'
      if (this.isCreate && !this.form.phone.trim()) this.formErrors.phone = '请输入手机号'
      else if (this.form.phone.trim() && !/^1\d{10}$/.test(this.form.phone.trim())) this.formErrors.phone = '请输入 11 位手机号'
      if (!this.form.role_id) this.formErrors.role_id = '请选择角色'
      if (!this.form.reason.trim()) this.formErrors.reason = '请填写原因'
      if (this.isCreate && !/^[A-Za-z0-9]{4,20}$/.test(this.form.username.trim())) {
        this.formErrors.username = '账号需为 4–20 位字母和数字'
      }
      if (Object.keys(this.formErrors).length) return
      if (this.isCreate && !(await this.checkUsername())) return
      this.submitting = true
      try {
        if (this.isCreate) {
          const created = await createAdmin({
            name: this.form.name.trim(),
            username: this.form.username.trim(),
            phone: this.form.phone.trim(),
            role_id: this.form.role_id!,
            reason: this.form.reason.trim()
          })
          this.drawer = false
          if (created.temp_password) {
            this.showTempPassword('管理员已创建', `${this.form.name.trim()}（${this.form.username.trim()}）`, created.temp_password, created.temp_password_expire_at)
          } else {
            useUiStore().toast('管理员已新增，但没有拿到临时密码，请在列表中为其重置密码', 'error')
          }
        } else if (this.editing) {
          await updateAdmin(this.editing.admin_id, {
            name: this.form.name.trim(),
            phone: this.form.phone.trim() || undefined,
            role_id: this.form.role_id!,
            reason: this.form.reason.trim(),
            version: this.editing.version || 0
          })
          this.drawer = false
          useUiStore().toast('管理员信息已保存')
        }
        // 角色人数跟着管理员变化，一起刷新
        await Promise.all([this.fetchAdmins(), this.fetchRoles().catch(() => undefined)])
      } catch (error) {
        if (error instanceof ApiError) {
          for (const item of error.fieldErrors) this.formErrors[item.field] = item.message
          useUiStore().toast(error.message, 'error')
        } else {
          useUiStore().toast('保存失败', 'error')
        }
      } finally {
        this.submitting = false
      }
    },
    openReset(row: AdminRow): void {
      this.resetTarget = row
      this.resetReason = ''
      this.resetOpen = true
    },
    async confirmReset(): Promise<void> {
      if (!this.resetTarget) return
      if (!this.resetReason.trim()) {
        useUiStore().toast('请填写修改原因', 'error')
        return
      }
      this.resetting = true
      try {
        const target = this.resetTarget
        const result = await resetAdminPassword(target.admin_id, this.resetReason.trim())
        this.resetOpen = false
        if (result?.temp_password) {
          this.showTempPassword('密码已重置', `${target.name}（${target.username}）`, result.temp_password, result.temp_password_expire_at)
        } else {
          useUiStore().toast('密码已重置，但没有拿到临时密码，请重新重置', 'error')
        }
        await this.fetchAdmins()
      } catch (error) {
        useUiStore().toast(error instanceof Error ? error.message : '重置失败', 'error')
      } finally {
        this.resetting = false
      }
    },
    openStatus(row: AdminRow, action: 'disable' | 'enable'): void {
      this.statusTarget = row
      this.statusAction = action
      this.statusReason = ''
      this.statusOpen = true
    },
    async confirmStatus(): Promise<void> {
      if (!this.statusTarget) return
      if (!this.statusReason.trim()) {
        useUiStore().toast('请填写原因', 'error')
        return
      }
      this.statusSaving = true
      try {
        await setAdminStatus(this.statusTarget.admin_id, this.statusAction, this.statusReason.trim(), this.statusTarget.version || 0)
        this.statusOpen = false
        useUiStore().toast(this.statusAction === 'disable' ? '已停用该管理员' : '已启用该管理员')
        await Promise.all([this.fetchAdmins(), this.fetchRoles().catch(() => undefined)])
      } catch (error) {
        useUiStore().toast(error instanceof Error ? error.message : '操作失败', 'error')
      } finally {
        this.statusSaving = false
      }
    }
  }
})
