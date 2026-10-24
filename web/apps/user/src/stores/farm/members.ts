import { defineStore } from 'pinia'
import {
  farmApi,
  type FarmDetailVO,
  type InviteItemVO,
  type InviteMemberBody,
  type MemberDetailVO,
  type MemberItemVO,
  type MemberRoleCode,
  type MemberSummaryVO,
  type PenOptionVO,
  type RemovalImpactVO
} from '@/api/farm'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import type { ApiError } from '@zmt/shared'
import { isCancelError, type PageStatus } from './shared'

export type MemberTab = 'all' | 'manager' | 'staff' | 'invite'

function messageOf(error: unknown, fallback: string): string {
  const value = error as ApiError | undefined
  return value?.message || fallback
}

export const useFarmMembersStore = defineStore('farmMembers', {
  state: () => ({
    farmId: 0,
    farmDetail: null as FarmDetailVO | null,
    farmDetailError: '',
    tab: 'all' as MemberTab,
    keyword: '',
    memberQuery: { page: 1, size: 20 },
    invitesQuery: { page: 1, size: 20 },
    members: [] as MemberItemVO[],
    allMembers: [] as MemberItemVO[],
    memberTotal: 0,
    memberStatus: 'loading' as PageStatus,
    memberError: '',
    invites: [] as InviteItemVO[],
    inviteTotal: 0,
    inviteStatus: 'loading' as PageStatus,
    inviteError: '',
    summary: null as MemberSummaryVO | null,
    summaryError: '',
    memberController: null as AbortController | null,
    inviteController: null as AbortController | null,
    detail: null as MemberDetailVO | null,
    detailStatus: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
    detailError: '',
    penOptions: [] as PenOptionVO[],
    penLoading: false,
    penError: '',
    submitting: false,
    removalImpact: null as RemovalImpactVO | null,
    removalStatus: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
    removalError: '',
    inviteFormError: '',
    inviteFieldError: ''
  }),
  getters: {
    managerCount: (state): number => state.summary?.manager_count ?? 0,
    staffCount: (state): number => state.summary?.staff_count ?? 0,
    inviteCount: (state): number => state.summary?.invite_count ?? state.invites.length,
    memberCount: (state): number => state.summary?.member_total ?? state.members.length,
    showUnassigned(): boolean {
      return Boolean(this.summary?.show_unassigned_banner && this.summary?.unassigned_pens?.length)
    },
    unassignedNames(): string {
      return (this.summary?.unassigned_pens || []).map((pen) => pen.name).join('、')
    },
    forbidden(): boolean {
      return this.memberStatus === 'forbidden' || this.inviteStatus === 'forbidden'
    },
    canInvite(): boolean {
      if (this.farmDetail) return Boolean(this.farmDetail.permissions?.can_invite)
      return this.allMembers.some((member) => member.is_me && member.role === 'OWNER')
    },
    isOwner(): boolean {
      if (this.farmDetail) return this.farmDetail.role === 'OWNER'
      return this.allMembers.some((member) => member.is_me && member.role === 'OWNER')
    },
    loading(): boolean {
      return (
        (this.memberStatus === 'loading' && this.tab !== 'invite') ||
        (this.inviteStatus === 'loading' && (this.tab === 'all' || this.tab === 'invite'))
      )
    }
  },
  actions: {
    async init(farmId: number): Promise<void> {
      this.memberController?.abort()
      this.inviteController?.abort()
      this.farmId = farmId
      this.tab = 'all'
      this.keyword = ''
      this.farmDetail = null
      this.farmDetailError = ''
      this.memberQuery.page = 1
      this.invitesQuery.page = 1
      this.members = []
      this.allMembers = []
      this.invites = []
      this.memberTotal = 0
      this.inviteTotal = 0
      this.detail = null
      this.detailStatus = 'idle'
      this.penOptions = []
      this.removalImpact = null
      this.removalStatus = 'idle'
      this.summary = null
      await Promise.all([this.fetchMembers(), this.fetchInvites(), this.fetchSummary(), this.fetchFarmDetail()])
    },
    async reload(): Promise<void> {
      await Promise.all([this.fetchMembers(), this.fetchInvites(), this.fetchSummary(), this.fetchFarmDetail()])
    },
    async fetchFarmDetail(): Promise<void> {
      if (!this.farmId) return
      try {
        this.farmDetail = await farmApi.detail(this.farmId)
        this.farmDetailError = ''
      } catch (error) {
        this.farmDetail = null
        this.farmDetailError = messageOf(error, '养殖场信息加载失败')
      }
    },
    async fetchMembers(): Promise<void> {
      if (!this.farmId) return
      this.memberController?.abort()
      const controller = new AbortController()
      this.memberController = controller
      this.memberStatus = 'loading'
      this.memberError = ''
      const role: 'ALL' | MemberRoleCode =
        this.tab === 'manager' ? 'MANAGER' : this.tab === 'staff' ? 'STAFF' : 'ALL'
      try {
        const data = await farmApi.members(
          this.farmId,
          {
            page: this.memberQuery.page,
            size: this.memberQuery.size,
            role,
            keyword: this.keyword || undefined
          },
          { signal: controller.signal }
        )
        if (controller !== this.memberController) return
        this.members = data.list || []
        if (role === 'ALL' && !this.keyword) this.allMembers = this.members
        this.memberTotal = data.total || 0
        if (this.members.length) this.memberStatus = 'ready'
        else if (this.keyword) this.memberStatus = 'filtered_empty'
        else this.memberStatus = 'empty'
      } catch (error) {
        if (controller !== this.memberController || isCancelError(error)) return
        const value = error as ApiError
        this.memberStatus = value?.isForbidden ? 'forbidden' : 'error'
        this.memberError = messageOf(error, '成员列表加载失败')
      }
    },
    async fetchInvites(): Promise<void> {
      if (!this.farmId) return
      if (this.tab !== 'all' && this.tab !== 'invite') return
      this.inviteController?.abort()
      const controller = new AbortController()
      this.inviteController = controller
      this.inviteStatus = 'loading'
      this.inviteError = ''
      try {
        const data = await farmApi.invites(
          this.farmId,
          {
            page: this.invitesQuery.page,
            size: this.invitesQuery.size,
            keyword: this.keyword || undefined
          },
          { signal: controller.signal }
        )
        if (controller !== this.inviteController) return
        this.invites = data.list || []
        this.inviteTotal = data.total || 0
        if (this.invites.length) this.inviteStatus = 'ready'
        else if (this.keyword) this.inviteStatus = 'filtered_empty'
        else this.inviteStatus = 'empty'
      } catch (error) {
        if (controller !== this.inviteController || isCancelError(error)) return
        const value = error as ApiError
        this.inviteStatus = value?.isForbidden ? 'forbidden' : 'error'
        this.inviteError = messageOf(error, '邀请记录加载失败')
      }
    },
    async fetchSummary(): Promise<void> {
      if (!this.farmId) return
      try {
        this.summary = await farmApi.memberSummary(this.farmId)
        this.summaryError = ''
      } catch (error) {
        this.summary = null
        this.summaryError = messageOf(error, '成员概况加载失败')
      }
    },
    async setTab(tab: MemberTab): Promise<void> {
      if (this.tab === tab) return
      this.tab = tab
      this.memberQuery.page = 1
      this.invitesQuery.page = 1
      if (tab === 'invite') {
        await this.fetchInvites()
        return
      }
      await this.fetchMembers()
      if (tab === 'all') await this.fetchInvites()
    },
    async search(keyword: string): Promise<void> {
      this.keyword = keyword.trim()
      this.memberQuery.page = 1
      this.invitesQuery.page = 1
      const tasks: Array<Promise<void>> = []
      if (this.tab !== 'invite') tasks.push(this.fetchMembers())
      if (this.tab === 'all' || this.tab === 'invite') tasks.push(this.fetchInvites())
      await Promise.all(tasks)
    },
    async setMemberPage(page: number): Promise<void> {
      if (page === this.memberQuery.page) return
      this.memberQuery.page = page
      await this.fetchMembers()
    },
    async setMemberSize(size: number): Promise<void> {
      if (size === this.memberQuery.size) return
      this.memberQuery.size = size
      this.memberQuery.page = 1
      await this.fetchMembers()
    },
    async setInvitePage(page: number): Promise<void> {
      if (page === this.invitesQuery.page) return
      this.invitesQuery.page = page
      await this.fetchInvites()
    },
    async setInviteSize(size: number): Promise<void> {
      if (size === this.invitesQuery.size) return
      this.invitesQuery.size = size
      this.invitesQuery.page = 1
      await this.fetchInvites()
    },
    async openMember(memberId: number): Promise<void> {
      this.detail = null
      this.detailStatus = 'loading'
      this.detailError = ''
      this.penOptions = []
      this.penError = ''
      this.removalImpact = null
      this.removalStatus = 'idle'
      this.inviteError = ''
      this.inviteFieldError = ''
      try {
        this.detail = await farmApi.member(this.farmId, memberId)
        this.detailStatus = 'ready'
      } catch (error) {
        this.detailStatus = 'error'
        this.detailError = messageOf(error, '成员详情加载失败，请稍后重试')
      }
    },
    closeMember(): void {
      this.detail = null
      this.detailStatus = 'idle'
      this.detailError = ''
      this.penOptions = []
      this.removalImpact = null
      this.removalStatus = 'idle'
    },
    async loadPenOptions(memberId?: number): Promise<void> {
      this.penLoading = true
      this.penError = ''
      this.penOptions = []
      try {
        this.penOptions = await farmApi.penOptions(this.farmId, memberId)
      } catch (error) {
        this.penError = messageOf(error, '可分配栏舍加载失败，请稍后重试')
      } finally {
        this.penLoading = false
      }
    },
    async updateRole(role: MemberRoleCode): Promise<boolean> {
      if (!this.detail || this.submitting) return false
      if (this.detail.version === undefined) {
        useUiStore().toast('内容已更新，请刷新后再修改', 'error')
        return false
      }
      this.submitting = true
      try {
        const result = await farmApi.updateMemberRole(this.farmId, this.detail.member_id, role, this.detail.version)
        if (result.member) this.detail = result.member
        await Promise.all([this.fetchMembers(), this.fetchSummary()])
        const roleText = role === 'MANAGER' ? '场长' : '员工'
        useUiStore().toast(`已把${this.detail?.name || '该成员'}改为${roleText}`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '修改角色失败，请稍后重试'), 'error')
        return false
      } finally {
        this.submitting = false
      }
    },
    async updatePens(penIds: number[]): Promise<boolean> {
      if (!this.detail || this.submitting) return false
      if (this.detail.version === undefined) {
        useUiStore().toast('内容已更新，请刷新后再修改', 'error')
        return false
      }
      this.submitting = true
      try {
        const result = await farmApi.updateMemberPens(this.farmId, this.detail.member_id, penIds, this.detail.version)
        if (result.member) this.detail = result.member
        await Promise.all([this.fetchMembers(), this.fetchSummary()])
        useUiStore().toast(`已更新${this.detail?.name || '该成员'}的负责栏舍`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '调整负责栏舍失败，请稍后重试'), 'error')
        return false
      } finally {
        this.submitting = false
      }
    },
    async loadRemovalImpact(): Promise<void> {
      if (!this.detail) return
      this.removalStatus = 'loading'
      this.removalError = ''
      this.removalImpact = null
      try {
        this.removalImpact = await farmApi.removalImpact(this.farmId, this.detail.member_id)
        this.removalStatus = 'ready'
      } catch (error) {
        this.removalStatus = 'error'
        this.removalError = messageOf(error, '移出影响加载失败，请稍后重试')
      }
    },
    async removeMember(): Promise<boolean> {
      if (!this.detail || this.submitting) return false
      if (this.detail.version === undefined) {
        useUiStore().toast('内容已更新，请刷新后再修改', 'error')
        return false
      }
      const name = this.detail.name
      this.submitting = true
      try {
        await farmApi.removeMember(this.farmId, this.detail.member_id, this.detail.version)
        this.closeMember()
        await Promise.all([this.fetchMembers(), this.fetchInvites(), this.fetchSummary()])
        useUiStore().toast(`已把${name}移出养殖场`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '移出成员失败，请稍后重试'), 'error')
        return false
      } finally {
        this.submitting = false
      }
    },
    async sendInvite(body: InviteMemberBody): Promise<boolean> {
      if (this.submitting) return false
      this.submitting = true
      this.inviteFormError = ''
      this.inviteFieldError = ''
      try {
        await farmApi.sendInvite(this.farmId, body)
        await Promise.all([this.fetchInvites(), this.fetchSummary()])
        useUiStore().toast('已发送邀请，对方同意后加入')
        return true
      } catch (error) {
        const value = error as ApiError
        if (value?.fieldErrors?.length) {
          const fieldError = value.fieldErrors[0]
          this.inviteFieldError = fieldError.field === 'invitee_phone' ? fieldError.message : ''
          this.inviteFormError = this.inviteFieldError ? '' : fieldError.message
        } else {
          this.inviteFormError = messageOf(error, '发送邀请失败，请稍后重试')
        }
        return false
      } finally {
        this.submitting = false
      }
    },
    async quit(): Promise<boolean> {
      const appFarm = useFarmStore()
      const name = this.farmDetail?.name || appFarm.farmName || '该养殖场'
      this.submitting = true
      try {
        await farmApi.quit(this.farmId)
        await appFarm.load(true)
        if (!appFarm.farms.length) appFarm.setCurrent(0)
        useUiStore().toast(`你已退出${name}`)
        return true
      } catch (error) {
        useUiStore().toast(messageOf(error, '退出养殖场失败，请稍后重试'), 'error')
        return false
      } finally {
        this.submitting = false
      }
    }
  }
})
