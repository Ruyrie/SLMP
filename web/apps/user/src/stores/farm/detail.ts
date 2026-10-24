import { defineStore } from 'pinia'
import {
  farmApi,
  type FarmDetailVO,
  type TransferPageVO,
  type UpdateFarmBasicBody
} from '@/api/farm'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import type { ApiError } from '@zmt/shared'
import { isCancelError, type PageStatus } from './shared'

function messageOf(error: unknown, fallback: string): string {
  const value = error as ApiError | undefined
  return value?.message || fallback
}

export const useFarmDetailStore = defineStore('farmDetail', {
  state: () => ({
    farmId: 0,
    detail: null as FarmDetailVO | null,
    status: 'loading' as PageStatus,
    error: '',
    basicSaving: false,
    actionLoading: '',
    transferOpen: false,
    transferLoading: false,
    transferError: '',
    transferPage: null as TransferPageVO | null,
    transferSaving: false
  }),
  getters: {
    name: (state): string => state.detail?.name || '',
    permissions(state) {
      return state.detail?.permissions || {}
    },
    isOwner: (state): boolean => state.detail?.role === 'OWNER',
    isManager: (state): boolean => state.detail?.role === 'MANAGER',
    canEditBasic(): boolean {
      return Boolean(this.permissions.can_edit_basic) && this.detail?.status !== 'DISABLED'
    },
    canManageMembers(): boolean {
      return Boolean(this.permissions.can_view_members)
    }
  },
  actions: {
    async fetch(farmId: number): Promise<void> {
      this.farmId = farmId
      this.status = 'loading'
      this.error = ''
      try {
        this.detail = await farmApi.detail(farmId)
        this.status = 'ready'
      } catch (error) {
        if (isCancelError(error)) return
        const value = error as ApiError
        this.status = value?.isForbidden ? 'forbidden' : 'error'
        this.error = messageOf(error, '养殖场信息加载失败，请稍后重试')
      }
    },
    async refresh(): Promise<void> {
      if (this.farmId) await this.fetch(this.farmId)
    },
    async updateBasic(body: UpdateFarmBasicBody): Promise<boolean> {
      if (this.basicSaving) return false
      this.basicSaving = true
      try {
        const basic = await farmApi.updateBasic(this.farmId, body)
        if (this.detail) this.detail.basic = basic
        useUiStore().toast('已保存基本信息')
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '保存失败，请稍后重试'), 'error')
        return false
      } finally {
        this.basicSaving = false
      }
    },
    async disable(): Promise<boolean> {
      const version = this.detail?.basic?.version
      if (!this.detail || version === undefined) {
        useUiStore().toast('内容已更新，请刷新后再修改', 'error')
        return false
      }
      this.actionLoading = 'disable'
      try {
        this.detail = await farmApi.disable(this.detail.farm_id, version)
        await useFarmStore().load(true)
        useUiStore().toast(`已停用${this.detail.name}`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '停用养殖场失败，请稍后重试'), 'error')
        return false
      } finally {
        this.actionLoading = ''
      }
    },
    async enable(): Promise<boolean> {
      const version = this.detail?.basic?.version
      if (!this.detail || version === undefined) {
        useUiStore().toast('内容已更新，请刷新后再修改', 'error')
        return false
      }
      this.actionLoading = 'enable'
      try {
        this.detail = await farmApi.enable(this.detail.farm_id, version)
        await useFarmStore().load(true)
        useUiStore().toast(`已重新启用${this.detail.name}`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '重新启用失败，请稍后重试'), 'error')
        return false
      } finally {
        this.actionLoading = ''
      }
    },
    async openTransfer(): Promise<void> {
      this.transferOpen = true
      this.transferPage = null
      this.transferError = ''
      this.transferLoading = true
      try {
        this.transferPage = await farmApi.transferPage(this.farmId)
      } catch (error) {
        this.transferError = messageOf(error, '转让信息加载失败，请稍后重试')
      } finally {
        this.transferLoading = false
      }
    },
    closeTransfer(): void {
      this.transferOpen = false
      this.transferPage = null
      this.transferError = ''
    },
    async createTransfer(toMemberId: number): Promise<boolean> {
      if (this.transferSaving) return false
      this.transferSaving = true
      try {
        const transfer = await farmApi.createTransfer(this.farmId, toMemberId)
        if (this.transferPage) this.transferPage.pending = transfer
        if (this.detail) this.detail.pending_transfer = transfer
        useUiStore().toast(`已向${transfer.to_name || '对方'}发送转让请求，对方确认后生效`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '转让请求发送失败，请稍后重试'), 'error')
        return false
      } finally {
        this.transferSaving = false
      }
    },
    async cancelTransfer(): Promise<boolean> {
      const transferId = this.transferPage?.pending?.transfer_id
      if (!transferId) return false
      this.transferSaving = true
      try {
        await farmApi.cancelTransfer(transferId)
        if (this.transferPage) this.transferPage.pending = null
        if (this.detail) this.detail.pending_transfer = null
        useUiStore().toast('已撤回转让请求')
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '撤回转让请求失败，请稍后重试'), 'error')
        return false
      } finally {
        this.transferSaving = false
      }
    },
    async quit(): Promise<boolean> {
      if (!this.detail) return false
      const name = this.detail.name
      this.actionLoading = 'quit'
      const appFarm = useFarmStore()
      try {
        await farmApi.quit(this.detail.farm_id)
        await appFarm.load(true)
        if (!appFarm.farms.length) appFarm.setCurrent(0)
        useUiStore().toast(`你已退出${name}`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '退出养殖场失败，请稍后重试'), 'error')
        return false
      } finally {
        this.actionLoading = ''
      }
    }
  }
})
