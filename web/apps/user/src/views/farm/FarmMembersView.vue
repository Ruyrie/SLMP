<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { dayjs } from '@zmt/shared'
import type { InviteItemVO, MemberItemVO, MemberRoleCode } from '@/api/farm'
import { useFarmMembersStore, type MemberTab } from '@/stores/farm/members'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { formatPhoneInput, maskPhone } from '@/stores/farm/shared'

const route = useRoute()
const router = useRouter()
const appFarm = useFarmStore()
const store = useFarmMembersStore()
const { members, invites, detail, detailStatus, detailError, penOptions, removalImpact, removalStatus, removalError } = storeToRefs(store)

const searchText = ref('')
const lastMemberId = ref(0)
let searchTimer = 0

const memberOpen = ref(false)
const editingRole = ref(false)
const editingPens = ref(false)
const roleDraft = ref<MemberRoleCode>('STAFF')
const pensDraft = ref<number[]>([])
const removeOpen = ref(false)
const quitOpen = ref(false)
const inviteOpen = ref(false)
const invitePhone = ref('')
const inviteRole = ref<MemberRoleCode>('STAFF')
const invitePens = ref<number[]>([])
const inviteResendId = ref<number | null>(null)
const inviteFormError = ref('')
const invitePhoneError = ref('')
const inviteChecking = ref(false)
const cancelOpen = ref(false)
const cancelTarget = ref<InviteItemVO | null>(null)
const cancelReason = ref('')
const cancelSubmitting = ref(false)
const cancelDefaultReason = '所有者撤销邀请'

const farmId = computed(() => Number(route.params.farmId || 0))
const farmName = computed(() => store.farmDetail?.name || appFarm.farmName || '养殖场')
const canInvite = computed(() => store.canInvite)
const canCancelInvite = computed(() => store.canCancelInvite)
const pageDesc = computed(() => `${farmName.value} · 成员角色和负责栏舍的调整立即生效，不走修改申请。`)

const memberTabs = computed(() => [
  { value: 'all', label: '全部' },
  { value: 'manager', label: `场长 ${store.managerCount}` },
  { value: 'staff', label: `员工 ${store.staffCount}` },
  { value: 'invite', label: `邀请 ${store.inviteCount}` }
])

const memberHeaders = [
  { title: '成员', key: 'name', sortable: false },
  { title: '角色', key: 'role', sortable: false, width: 100 },
  { title: '负责栏舍', key: 'pens', sortable: false },
  { title: '加入时间', key: 'joined', sortable: false, width: 120 },
  { title: '操作', key: 'actions', sortable: false, width: 150, align: 'end' as const }
]

const inviteHeaders = computed(() => {
  const headers: Array<Record<string, unknown>> = [
    { title: '被邀请手机号', key: 'phone', sortable: false },
    { title: '邀请角色', key: 'role', sortable: false, width: 100 },
    { title: '负责栏舍', key: 'pens', sortable: false },
    { title: '邀请时间', key: 'invited', sortable: false, width: 110 },
    { title: '状态', key: 'status', sortable: false, width: 150 }
  ]
  if (canCancelInvite.value) headers.push({ title: '操作', key: 'actions', sortable: false, width: 110, align: 'end' })
  return headers
})

const memberEmptyTitle = computed(() => {
  if (store.keyword) return '没有找到相关成员'
  if (store.tab === 'manager') return '没有场长'
  if (store.tab === 'staff') return '没有员工'
  return '还没有其他成员'
})

const memberEmptyDesc = computed(() => {
  if (store.keyword) return '换个姓名或手机号关键词试试。'
  if (store.tab === 'manager') return canInvite.value ? '点“邀请成员”，邀请场长加入。' : '还没有场长。'
  if (store.tab === 'staff') return canInvite.value ? '点“邀请成员”，邀请员工加入。' : '还没有员工。'
  return canInvite.value ? '点“邀请成员”，邀请场长或员工加入。' : ''
})

const inviteEmptyTitle = computed(() => (store.keyword ? '没有找到相关邀请' : '没有邀请记录'))

const roleOptions: Array<{ value: MemberRoleCode; title: string; desc: string }> = [
  { value: 'MANAGER', title: '场长', desc: '可使用除成员管理、停用养殖场、转让所有权以外的全部功能' },
  { value: 'STAFF', title: '员工', desc: '只能查看和登记负责栏舍的作业记录，看不到收支和经营数据' }
]

const invitePenHint = computed(() => {
  if (!store.penOptions.length) return `${farmName.value}还没有栏舍，对方加入后可以再分配。`
  if (!invitePens.value.length) return '还没有选择负责栏舍。对方加入后看不到任何栏舍，可以之后在成员详情中分配。'
  return `可多选，已选 ${invitePens.value.length} 个；一个栏舍可以有多名负责员工`
})

const penWarnings = computed(() => {
  if (!editingPens.value) return ''
  const removed = penOptions.value.filter(
    (pen) => pen.selected && !pensDraft.value.includes(pen.pen_id) && (pen.other_staff_count ?? 0) === 0
  )
  if (!removed.length) return ''
  return `取消后 ${removed.map((pen) => pen.name).join('、')} 将没有负责员工，提醒和待办改发给所有者和场长`
})

const canRemove = computed(() => Boolean(detail.value?.actions?.can_remove))
const canQuit = computed(() => Boolean(detail.value?.actions?.can_quit))
const canChangeRole = computed(() => Boolean(detail.value?.actions?.can_change_role))
const canAdjustPens = computed(() => Boolean(detail.value?.actions?.can_adjust_pens))

const removeParagraphs = computed(() => {
  const impact = removalImpact.value
  const name = detail.value?.name || '该成员'
  if (!impact || detail.value?.role !== 'STAFF') return [] as string[]
  const paragraphs: string[] = []
  if (impact.pending_work_todo_count) {
    paragraphs.push(`${name}还没完成的作业类待办会转给所有者和场长。`)
  }
  if (impact.orphan_pens?.length) {
    paragraphs.push(`${impact.orphan_pens.map((pen) => pen.name).join('、')}将没有负责员工，请在移出后重新分配。`)
  }
  return paragraphs
})

function onTab(value: unknown): void {
  void store.setTab(value as MemberTab)
}

function onSearchInput(value: string): void {
  searchText.value = value
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    void store.search(value)
  }, 300)
}

function formatDay(value?: string): string {
  return value ? dayjs(value).format('M月D日') : '—'
}

function joinedText(member: MemberItemVO): string {
  const day = formatDay(member.joined_at)
  return member.join_type === 'CREATE' ? `${day} 创建` : `${day} 加入`
}

function inviteStatusType(status: string): 'primary' | 'error' | 'neutral' | 'success' {
  if (status === 'PENDING') return 'primary'
  if (status === 'EXPIRED') return 'error'
  if (status === 'ACCEPTED') return 'success'
  return 'neutral'
}

function inviteIcon(status: string): string {
  if (status === 'PENDING') return 'person_add'
  if (status === 'EXPIRED') return 'schedule'
  if (status === 'REJECTED') return 'person_off'
  return 'person_add'
}

function inviteNote(invite: InviteItemVO): string {
  if (invite.status === 'PENDING') return invite.expires_at ? `${formatDay(invite.expires_at)}前有效` : '等待对方接受'
  if (invite.status === 'EXPIRED') return '7 天未处理'
  if (invite.status === 'REJECTED') return invite.handled_at ? `${formatDay(invite.handled_at)}拒绝` : '对方已拒绝'
  return invite.status_text || ''
}

function inviteStatusText(invite: InviteItemVO): string {
  return invite.status_text || ({ PENDING: '待接受', EXPIRED: '已过期', REJECTED: '已拒绝', ACCEPTED: '已加入' }[invite.status] || '')
}

async function openMember(member: MemberItemVO, adjustPens = false): Promise<void> {
  lastMemberId.value = member.member_id
  memberOpen.value = true
  editingRole.value = false
  editingPens.value = false
  roleDraft.value = member.role === 'MANAGER' ? 'MANAGER' : 'STAFF'
  pensDraft.value = []
  await store.openMember(member.member_id)
  if (adjustPens && detail.value?.actions?.can_adjust_pens) await startPensEdit()
}

function closeMember(): void {
  memberOpen.value = false
  editingRole.value = false
  editingPens.value = false
}

function startRoleEdit(): void {
  if (!detail.value) return
  roleDraft.value = detail.value.role === 'MANAGER' ? 'MANAGER' : 'STAFF'
  editingRole.value = true
  editingPens.value = false
}

async function saveRole(): Promise<void> {
  const ok = await store.updateRole(roleDraft.value)
  if (!ok) return
  editingRole.value = false
  if (roleDraft.value === 'STAFF') await startPensEdit()
}

async function startPensEdit(): Promise<void> {
  if (!detail.value) return
  editingRole.value = false
  editingPens.value = true
  await store.loadPenOptions(detail.value.member_id)
  pensDraft.value = penOptions.value.filter((pen) => pen.selected).map((pen) => pen.pen_id)
}

function cancelPensEdit(): void {
  editingPens.value = false
  pensDraft.value = []
}

function toggleDraftPen(penId: number): void {
  const index = pensDraft.value.indexOf(penId)
  if (index >= 0) pensDraft.value.splice(index, 1)
  else pensDraft.value.push(penId)
}

async function savePens(): Promise<void> {
  const ok = await store.updatePens(pensDraft.value)
  if (ok) {
    editingPens.value = false
    pensDraft.value = []
  }
}

function askRemove(): void {
  memberOpen.value = false
  removeOpen.value = true
  void store.loadRemovalImpact()
}

async function confirmRemove(): Promise<void> {
  const ok = await store.removeMember()
  if (ok) {
    removeOpen.value = false
    memberOpen.value = false
  }
}

function askQuit(): void {
  memberOpen.value = false
  quitOpen.value = true
}

async function confirmQuit(): Promise<void> {
  const ok = await store.quit()
  if (ok) {
    quitOpen.value = false
    if (appFarm.currentFarmId) await router.push({ name: 'WU-T01' })
    else await router.push({ name: 'WU-F01' })
  }
}

function openInvite(invite?: InviteItemVO): void {
  if (!canInvite.value) return
  inviteFormError.value = ''
  invitePhoneError.value = ''
  inviteResendId.value = invite?.invite_id ?? null
  invitePhone.value = invite?.invitee_phone ? formatPhoneInput(invite.invitee_phone) : ''
  inviteRole.value = invite?.role === 'MANAGER' ? 'MANAGER' : 'STAFF'
  invitePens.value = (invite?.pens || []).map((pen) => pen.pen_id)
  inviteOpen.value = true
  void store.loadPenOptions()
}

function closeInvite(): void {
  inviteOpen.value = false
  invitePens.value = []
  inviteResendId.value = null
}

function toggleInvitePen(penId: number): void {
  const index = invitePens.value.indexOf(penId)
  if (index >= 0) invitePens.value.splice(index, 1)
  else invitePens.value.push(penId)
}

function validateInvitePhone(): boolean {
  invitePhoneError.value = ''
  const digits = invitePhone.value.replace(/\D/g, '')
  if (!digits) {
    invitePhoneError.value = '请输入对方的手机号'
    return false
  }
  if (!/^1\d{10}$/.test(digits)) {
    invitePhoneError.value = '请输入 11 位手机号'
    return false
  }
  const masked = maskPhone(digits)
  const self = store.allMembers.find((member) => member.is_me && member.phone_masked === masked)
  if (self) {
    invitePhoneError.value = '不能邀请你自己'
    return false
  }
  const existing = store.allMembers.find((member) => member.phone_masked === masked)
  if (existing) {
    invitePhoneError.value = `这个手机号已经是${farmName.value}的成员`
    return false
  }
  const pending = invites.value.find(
    (item) => item.status === 'PENDING' && item.invitee_phone_masked === masked && item.invite_id !== inviteResendId.value
  )
  if (pending) {
    invitePhoneError.value = `已向这个手机号发出邀请，${formatDay(pending.expires_at)}前有效`
    return false
  }
  return true
}

async function submitInvite(): Promise<void> {
  if (inviteChecking.value) return
  if (!validateInvitePhone()) return
  inviteChecking.value = true
  const ok = await store.sendInvite({
    invitee_phone: invitePhone.value.replace(/\D/g, ''),
    role: inviteRole.value,
    pen_ids: inviteRole.value === 'STAFF' ? [...invitePens.value] : [],
    resend_of_id: inviteResendId.value || undefined
  })
  inviteChecking.value = false
  if (ok) {
    closeInvite()
    return
  }
  invitePhoneError.value = store.inviteFieldError
  inviteFormError.value = store.inviteFormError
}

function askCancel(invite: InviteItemVO): void {
  if (!canCancelInvite.value || invite.status !== 'PENDING') return
  cancelTarget.value = invite
  cancelReason.value = cancelDefaultReason
  cancelOpen.value = true
}

function closeCancel(): void {
  if (cancelSubmitting.value) return
  cancelOpen.value = false
  cancelTarget.value = null
  cancelReason.value = ''
}

async function confirmCancel(): Promise<void> {
  if (cancelSubmitting.value || !cancelTarget.value) return
  cancelSubmitting.value = true
  const ok = await store.cancelInvite(cancelTarget.value.invite_id, cancelReason.value)
  cancelSubmitting.value = false
  if (ok) closeCancel()
}

function retryMember(): void {
  if (lastMemberId.value) void store.openMember(lastMemberId.value)
  else if (farmId.value) void store.reload()
}

function goInviteFromEmpty(): void {
  openInvite()
}

function onFarmReload(): void {
  if (store.submitting) return
  const current = appFarm.currentFarmId
  const routeId = Number(route.params.farmId || 0)
  if (appFarm.role === 'staff') {
    void router.replace({ name: 'WU-T02' })
    return
  }
  if (current && current !== routeId) {
    void router.replace({ name: 'WU-F04', params: { farmId: current } })
    return
  }
  void store.reload()
}

useFarmReload(onFarmReload)

watch(
  () => route.params.farmId,
  (value) => {
    const id = Number(value || 0)
    if (id && id !== store.farmId) void initPage(id)
  }
)

async function initPage(id: number): Promise<void> {
  await store.init(id)
  if (route.query.action === 'create' && store.canInvite) {
    openInvite()
    await router.replace({ name: 'WU-F04', params: { farmId: id }, query: {} })
  }
}

onMounted(async () => {
  const id = Number(route.params.farmId || appFarm.currentFarmId || 0)
  if (id) await initPage(id)
})

onBeforeUnmount(() => {
  window.clearTimeout(searchTimer)
})
</script>

<template>
  <zw-shell side="user" active="WU-F04" :crumbs="['设置', '成员管理']">
    <zw-page-head title="成员管理" :desc="pageDesc">
      <template v-if="canInvite" #actions>
        <v-btn color="primary" prepend-icon="person_add" @click="openInvite()">邀请成员</v-btn>
      </template>
      <v-tabs :model-value="store.tab" class="zw-tabs-line" @update:model-value="onTab">
        <v-tab v-for="tab in memberTabs" :key="tab.value" :value="tab.value">{{ tab.label }}</v-tab>
      </v-tabs>
    </zw-page-head>

    <zw-card v-if="store.forbidden">
      <zw-empty
        icon="lock"
        title="无权查看"
        :desc="`你没有${farmName}成员管理的查看权限。养殖场信息和成员管理只对所有者和场长开放，如需查看请联系所有者。`"
        tone="warning"
      >
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <zw-notice v-if="store.showUnassigned" type="warning" icon="warning" class="mb-4">
        {{ store.unassignedNames }}没有负责员工，提醒和待办暂时发给所有者和场长。在员工的成员详情中点“调整栏舍”即可分配。
      </zw-notice>

      <div class="f04-cols">
        <div class="f04-main zw-stack">
          <zw-card v-if="store.tab !== 'invite'" title="成员" :sub="`${store.memberTotal} 人`" flush>
            <template #extra>
              <v-text-field
                :model-value="searchText"
                class="zw-w-240"
                prepend-inner-icon="search"
                placeholder="搜索姓名或手机号"
                clearable
                @update:model-value="onSearchInput"
              />
            </template>

            <v-skeleton-loader v-if="store.memberStatus === 'loading'" type="table-row@4" />
            <zw-empty v-else-if="store.memberStatus === 'error'" icon="cloud_off" title="成员列表加载失败" :desc="store.memberError" tone="error">
              <v-btn variant="outlined" @click="store.fetchMembers()">重新加载</v-btn>
            </zw-empty>
            <zw-empty
              v-else-if="store.memberStatus === 'empty' || store.memberStatus === 'filtered_empty'"
              icon="group"
              :title="memberEmptyTitle"
              :desc="memberEmptyDesc"
            >
              <v-btn v-if="canInvite && !store.keyword" color="primary" prepend-icon="person_add" @click="goInviteFromEmpty">邀请成员</v-btn>
            </zw-empty>
            <template v-else>
              <v-data-table :headers="memberHeaders" :items="members" item-value="member_id" hide-default-footer class="f04-table">
                <template #item.name="{ item }">
                  <div class="f04-who">
                    <zw-avatar :name="item.name" :size="32" />
                    <div>
                      <div class="zw-cell-main">{{ item.name }}<span v-if="item.is_me" class="f04-me">（你）</span></div>
                      <div class="zw-cell-sub num">{{ item.phone_masked || '—' }}</div>
                    </div>
                  </div>
                </template>
                <template #item.role="{ item }">
                  <zw-tag :type="item.role === 'OWNER' ? 'primary' : 'neutral'">{{ item.role_text }}</zw-tag>
                </template>
                <template #item.pens="{ item }">
                  <span v-if="item.all_pens" class="f04-pens-all">全部栏舍</span>
                  <span v-else-if="item.pens?.length" class="f04-pens">
                    <zw-tag v-for="pen in item.pens" :key="pen.pen_id" type="outline" size="small">{{ pen.name }}</zw-tag>
                  </span>
                  <span v-else class="zw-faint">—</span>
                </template>
                <template #item.joined="{ item }">{{ joinedText(item) }}</template>
                <template #item.actions="{ item }">
                  <span class="zw-cell-actions">
                    <a @click="openMember(item)">详情</a>
                    <a v-if="item.can_adjust_pens" @click="openMember(item, true)">调整栏舍</a>
                  </span>
                </template>
              </v-data-table>
              <div class="f04-foot">
                <span v-if="store.memberTotal <= 1 && canInvite && !store.keyword" class="zw-faint body-s">
                  还没有其他成员。点“邀请成员”，邀请场长或员工加入。
                </span>
                <span v-else class="zw-faint body-s">成员角色和负责栏舍的调整立即生效</span>
                <span style="flex: 1"></span>
                <zw-pager
                  :total="store.memberTotal"
                  :page="store.memberQuery.page"
                  :size="store.memberQuery.size"
                  @update:page="store.setMemberPage($event)"
                  @update:size="store.setMemberSize($event)"
                />
              </div>
            </template>
          </zw-card>

          <zw-card
            v-if="store.tab === 'all' || store.tab === 'invite'"
            title="邀请"
            :sub="`${store.inviteTotal} 条 · 7 天内有效`"
            flush
          >
            <template v-if="store.tab === 'invite'" #extra>
              <v-text-field
                :model-value="searchText"
                class="zw-w-240"
                prepend-inner-icon="search"
                placeholder="搜索被邀请手机号"
                clearable
                @update:model-value="onSearchInput"
              />
            </template>
            <v-skeleton-loader v-if="store.inviteStatus === 'loading'" type="table-row@3" />
            <zw-empty v-else-if="store.inviteStatus === 'error'" icon="cloud_off" title="邀请记录加载失败" :desc="store.inviteError" tone="error">
              <v-btn variant="outlined" @click="store.fetchInvites()">重新加载</v-btn>
            </zw-empty>
            <zw-empty v-else-if="store.inviteStatus === 'empty' || store.inviteStatus === 'filtered_empty'" icon="mail" :title="inviteEmptyTitle" desc="邀请 7 天内有效，过期或被拒绝后可以重新邀请。" />
            <template v-else>
              <v-data-table :headers="inviteHeaders" :items="invites" item-value="invite_id" hide-default-footer class="f04-table">
                <template #item.phone="{ item }">
                  <div class="f04-who">
                    <span class="zw-ico-tile"><zw-icon :name="inviteIcon(item.status)" :size="18" /></span>
                    <span class="zw-cell-main num">{{ item.invitee_phone_masked || item.invitee_phone || '—' }}</span>
                  </div>
                </template>
                <template #item.role="{ item }"><span>{{ item.role_text }}</span></template>
                <template #item.pens="{ item }">
                  <span v-if="!item.pens?.length" class="f04-pens-all">全部栏舍</span>
                  <span v-else class="f04-pens">
                    <zw-tag v-for="pen in item.pens" :key="pen.pen_id" type="outline" size="small">{{ pen.name }}</zw-tag>
                  </span>
                </template>
                <template #item.invited="{ item }">{{ formatDay(item.invited_at) }}</template>
                <template #item.status="{ item }">
                  <div class="f04-invite-status">
                    <zw-tag :type="inviteStatusType(item.status)">{{ inviteStatusText(item) }}</zw-tag>
                    <div class="zw-cell-sub">{{ inviteNote(item) }}</div>
                  </div>
                </template>
                <template #item.actions="{ item }">
                  <span class="zw-cell-actions">
                    <a v-if="canCancelInvite && item.status === 'PENDING'" @click="askCancel(item)">撤销</a>
                    <a v-if="canInvite && item.can_reinvite" @click="openInvite(item)">重新邀请</a>
                  </span>
                </template>
              </v-data-table>
              <div class="f04-foot">
                <span class="zw-faint body-s">邀请 7 天内有效，过期或被拒绝后可以重新邀请</span>
                <span style="flex: 1"></span>
                <zw-pager
                  :total="store.inviteTotal"
                  :page="store.invitesQuery.page"
                  :size="store.invitesQuery.size"
                  @update:page="store.setInvitePage($event)"
                  @update:size="store.setInviteSize($event)"
                />
              </div>
            </template>
          </zw-card>
        </div>

        <div class="f04-side">
          <zw-card title="角色与权限">
            <ul class="f04-roles">
              <li>
                <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="workspace_premium" :size="18" /></span>
                <div>
                  <div class="f04-roles-t">所有者</div>
                  <div class="f04-roles-d">可使用全部功能，包括邀请和移出成员、修改角色、停用养殖场、转让所有权。</div>
                </div>
              </li>
              <li>
                <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="manage_accounts" :size="18" /></span>
                <div>
                  <div class="f04-roles-t">场长</div>
                  <div class="f04-roles-d">可使用除成员管理、停用养殖场、转让所有权以外的全部功能；可以给员工调整负责栏舍。</div>
                </div>
              </li>
              <li>
                <span class="zw-ico-tile"><zw-icon name="person" :size="18" /></span>
                <div>
                  <div class="f04-roles-t">员工</div>
                  <div class="f04-roles-d">只能查看和登记负责栏舍的作业记录，看不到收支和经营数据。</div>
                </div>
              </li>
            </ul>
            <div class="f04-note">被移出的成员从下一次操作起看不到本场，历史记录的登记人仍显示原姓名。</div>
          </zw-card>
        </div>
      </div>

      <zw-drawer v-model="inviteOpen" title="邀请成员" :sub="`${farmName} · 对方在消息中心同意后加入`" :width="560">
        <div class="zw-form">
          <zw-form-item label="对方手机号" required :error="invitePhoneError" hint="对方用这个手机号注册或登录后，在消息中心同意即可加入">
            <v-text-field
              v-model="invitePhone"
              prefix="+86"
              placeholder="139 0476 2051"
              maxlength="13"
              :autofocus="!inviteResendId"
              @update:model-value="invitePhone = formatPhoneInput(String($event))"
            />
          </zw-form-item>
          <zw-form-item label="角色" required>
            <div class="f04-radios">
              <div
                v-for="option in roleOptions"
                :key="option.value"
                class="f04-radio"
                :class="{ on: inviteRole === option.value }"
                role="radio"
                tabindex="0"
                :aria-checked="inviteRole === option.value"
                @click="inviteRole = option.value"
                @keydown.enter="inviteRole = option.value"
              >
                <zw-icon :name="inviteRole === option.value ? 'radio_button_checked' : 'radio_button_unchecked'" :size="20" />
                <div>
                  <div class="f04-radio-t">{{ option.title }}</div>
                  <div class="f04-radio-d">{{ option.desc }}</div>
                </div>
              </div>
            </div>
          </zw-form-item>
          <zw-form-item v-if="inviteRole === 'STAFF'" label="负责栏舍" :hint="invitePenHint">
            <v-skeleton-loader v-if="store.penLoading" type="chip@4" />
            <div v-else-if="store.penError" class="f04-inline-error">{{ store.penError }}</div>
            <div v-else-if="penOptions.length" class="f04-chips">
              <span
                v-for="pen in penOptions"
                :key="pen.pen_id"
                class="f04-chip"
                :class="{ on: invitePens.includes(pen.pen_id) }"
                role="checkbox"
                tabindex="0"
                :aria-checked="invitePens.includes(pen.pen_id)"
                @click="toggleInvitePen(pen.pen_id)"
                @keydown.enter="toggleInvitePen(pen.pen_id)"
              >
                <zw-icon v-if="invitePens.includes(pen.pen_id)" name="check" :size="16" />
                {{ pen.name }}
              </span>
            </div>
          </zw-form-item>
        </div>
        <zw-notice icon="schedule" class="mt-5">邀请 7 天内有效，过期或被拒绝后可以重新邀请。</zw-notice>
        <div v-if="inviteFormError" class="f04-inline-error mt-2">{{ inviteFormError }}</div>
        <template #foot>
          <span class="zw-foot-note">带 * 为必填</span>
          <v-btn variant="outlined" :disabled="inviteChecking" @click="closeInvite">取消</v-btn>
          <v-btn color="primary" prepend-icon="send" :loading="inviteChecking" @click="submitInvite">发送邀请</v-btn>
        </template>
      </zw-drawer>

      <zw-dialog
        v-model="cancelOpen"
        :title="`撤销对 ${cancelTarget?.invitee_phone_masked || '该手机号'} 的邀请？`"
        icon="undo"
        tone="error"
        :width="480"
        :no-close="cancelSubmitting"
      >
        <p class="f04-dlg-p">
          撤销后这条邀请立即失效，对方不能再通过它加入{{ farmName }}。如果还需要邀请对方，可以稍后重新发送。
        </p>
        <zw-form-item label="撤销原因" optional hint="最多 100 字。不填写时按“所有者撤销邀请”记录。">
          <v-text-field v-model="cancelReason" maxlength="100" counter="100" placeholder="所有者撤销邀请" />
        </zw-form-item>
        <template #actions>
          <v-btn variant="outlined" :disabled="cancelSubmitting" @click="closeCancel">取消</v-btn>
          <v-btn color="error" :loading="cancelSubmitting" :disabled="cancelSubmitting" @click="confirmCancel">撤销邀请</v-btn>
        </template>
      </zw-dialog>

      <zw-drawer :model-value="memberOpen" title="成员详情" :sub="farmName" :width="560" @update:model-value="closeMember">
        <v-skeleton-loader v-if="detailStatus === 'loading'" type="list-item-avatar-two-line@3, paragraph" />
        <zw-empty v-else-if="detailStatus === 'error'" icon="cloud_off" title="成员详情加载失败" :desc="detailError" tone="error">
          <v-btn variant="outlined" @click="retryMember">重新加载</v-btn>
        </zw-empty>
        <template v-else-if="detail">
          <div class="f04-dhead">
            <zw-avatar :name="detail.name" :size="56" />
            <div>
              <div class="f04-dname">{{ detail.name }}<zw-tag :type="detail.role === 'OWNER' ? 'primary' : 'neutral'">{{ detail.role_text }}</zw-tag></div>
              <div class="f04-dsub">{{ joinedText(detail) }} {{ farmName }}</div>
            </div>
          </div>

          <div class="zw-section-title mt-6">联系方式</div>
          <zw-desc :cols="1" bordered :label-width="104">
            <zw-desc-item label="手机号"><span class="num">{{ detail.phone_masked || detail.phone || '—' }}</span></zw-desc-item>
          </zw-desc>

          <div class="zw-section-title mt-6">角色与负责栏舍</div>
          <zw-desc :cols="1" bordered :label-width="104">
            <zw-desc-item label="角色">
              <div v-if="editingRole" class="f04-edit-block">
                <div class="f04-radios f04-radios--sm">
                  <div
                    v-for="option in roleOptions"
                    :key="option.value"
                    class="f04-radio"
                    :class="{ on: roleDraft === option.value }"
                    role="radio"
                    tabindex="0"
                    :aria-checked="roleDraft === option.value"
                    @click="roleDraft = option.value"
                    @keydown.enter="roleDraft = option.value"
                  >
                    <zw-icon :name="roleDraft === option.value ? 'radio_button_checked' : 'radio_button_unchecked'" :size="20" />
                    <div>
                      <div class="f04-radio-t">{{ option.title }}</div>
                      <div class="f04-radio-d">{{ option.desc }}</div>
                    </div>
                  </div>
                </div>
                <div class="f04-edit-actions">
                  <v-btn variant="outlined" size="small" :disabled="store.submitting" @click="editingRole = false">取消</v-btn>
                  <v-btn color="primary" size="small" :loading="store.submitting" @click="saveRole">保存</v-btn>
                </div>
              </div>
              <div v-else class="f04-dval">
                {{ detail.role_text }}
                <a v-if="canChangeRole" class="zw-link" @click="startRoleEdit">修改角色</a>
              </div>
            </zw-desc-item>
            <zw-desc-item label="负责栏舍">
              <div v-if="editingPens" class="f04-edit-block">
                <v-skeleton-loader v-if="store.penLoading" type="chip@4" />
                <div v-else-if="store.penError" class="f04-inline-error">{{ store.penError }}</div>
                <div v-else-if="penOptions.length" class="f04-chips">
                  <span
                    v-for="pen in penOptions"
                    :key="pen.pen_id"
                    class="f04-chip"
                    :class="{ on: pensDraft.includes(pen.pen_id) }"
                    role="checkbox"
                    tabindex="0"
                    :aria-checked="pensDraft.includes(pen.pen_id)"
                    @click="toggleDraftPen(pen.pen_id)"
                    @keydown.enter="toggleDraftPen(pen.pen_id)"
                  >
                    <zw-icon v-if="pensDraft.includes(pen.pen_id)" name="check" :size="16" />
                    {{ pen.name }}
                  </span>
                </div>
                <div v-else class="zw-faint body-s">本场还没有栏舍</div>
                <div v-if="penWarnings" class="f04-pen-warning">{{ penWarnings }}</div>
                <div class="f04-edit-actions">
                  <v-btn variant="outlined" size="small" :disabled="store.submitting" @click="cancelPensEdit">取消</v-btn>
                  <v-btn color="primary" size="small" :loading="store.submitting" @click="savePens">保存</v-btn>
                </div>
              </div>
              <div v-else class="f04-dval">
                <template v-if="detail.all_pens">全部栏舍</template>
                <template v-else-if="detail.pens?.length">
                  <zw-tag v-for="pen in detail.pens" :key="pen.pen_id" type="outline">{{ pen.name }}</zw-tag>
                </template>
                <span v-else class="zw-faint">未分配负责栏舍</span>
                <a v-if="canAdjustPens" class="zw-link" @click="startPensEdit">调整栏舍</a>
              </div>
            </zw-desc-item>
          </zw-desc>

          <zw-notice icon="info" class="mt-4">
            修改角色和调整栏舍立即生效，{{ detail.name }}下一次操作就按新范围显示栏舍、批次和待办。
          </zw-notice>

          <template v-if="canRemove || canQuit">
            <div class="zw-section-title mt-6">危险操作</div>
            <div v-if="canRemove" class="f04-danger">
              <span class="zw-ico-tile zw-ico-tile--error"><zw-icon name="person_remove" :size="18" /></span>
              <div style="flex: 1">
                <div class="f04-danger-t">移出养殖场</div>
                <div class="f04-danger-d">只有所有者可以操作</div>
              </div>
              <v-btn variant="outlined" color="error" @click="askRemove">移出</v-btn>
            </div>
            <div v-else-if="canQuit" class="f04-danger">
              <span class="zw-ico-tile zw-ico-tile--error"><zw-icon name="logout" :size="18" /></span>
              <div style="flex: 1">
                <div class="f04-danger-t">退出养殖场</div>
                <div class="f04-danger-d">退出后看不到{{ farmName }}的数据</div>
              </div>
              <v-btn variant="outlined" color="error" @click="askQuit">退出</v-btn>
            </div>
          </template>
        </template>
      </zw-drawer>

      <zw-dialog v-model="removeOpen" :title="`把${detail?.name || '该成员'}移出${farmName}？`" icon="person_remove" tone="error" :width="480">
        <p class="f04-dlg-p">
          移出后，{{ detail?.name || '该成员' }}从下一次操作起就看不到{{ farmName }}的数据，他的账号仍可继续使用。他登记过的记录保留，登记人仍显示原姓名。
        </p>
        <v-skeleton-loader v-if="removalStatus === 'loading'" type="text@2" />
        <template v-else>
          <p v-for="(paragraph, index) in removeParagraphs" :key="index" class="f04-dlg-p">{{ paragraph }}</p>
          <p v-if="removalStatus === 'error'" class="f04-inline-error">{{ removalError }}</p>
        </template>
        <template #actions>
          <v-btn variant="outlined" :disabled="store.submitting" @click="removeOpen = false">取消</v-btn>
          <v-btn color="error" :loading="store.submitting" @click="confirmRemove">移出</v-btn>
        </template>
      </zw-dialog>

      <zw-dialog v-model="quitOpen" :title="`退出${farmName}？`" icon="logout" tone="error" :width="420">
        <p class="f04-dlg-p">退出后，你从下一次操作起就看不到{{ farmName }}的数据。你登记过的记录保留。</p>
        <template #actions>
          <v-btn variant="outlined" :disabled="store.submitting" @click="quitOpen = false">取消</v-btn>
          <v-btn color="error" :loading="store.submitting" @click="confirmQuit">退出</v-btn>
        </template>
      </zw-dialog>
    </template>
  </zw-shell>
</template>

<style scoped>
.f04-cols {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.f04-main {
  flex: 1 1 0;
  min-width: 0;
}
.f04-side {
  width: 300px;
  flex-shrink: 0;
}
.f04-who {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}
.f04-me {
  color: var(--text-2);
}
.f04-pens {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.f04-pens-all {
  color: var(--text-1);
}
.f04-invite-status {
  padding: 6px 0;
}
.f04-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.f04-roles {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.f04-roles li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.f04-roles-t {
  font-weight: 500;
  color: var(--text-1);
}
.f04-roles-d {
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.f04-note {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--line-1);
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.f04-radios {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.f04-radios--sm {
  grid-template-columns: 1fr;
}
.f04-radio {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--control-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.f04-radio .zw-mso {
  color: var(--control-border);
  margin-top: 1px;
}
.f04-radio.on {
  border-color: var(--primary);
  background: var(--primary-subtle);
}
.f04-radio.on .zw-mso {
  color: var(--primary);
}
.f04-radio-t {
  font-weight: 500;
  color: var(--text-1);
}
.f04-radio-d {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-2);
}
.f04-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.f04-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--control-border);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-1);
  cursor: pointer;
  font-size: 14px;
}
.f04-chip.on {
  border-color: var(--primary);
  background: var(--primary-subtle);
  color: var(--primary);
}
.f04-dhead {
  display: flex;
  align-items: center;
  gap: 16px;
}
.f04-dname {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
}
.f04-dsub {
  margin-top: 2px;
  color: var(--text-2);
}
.f04-dval {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.f04-dval .zw-link {
  margin-left: auto;
}
.f04-danger {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--error-container);
  border-radius: var(--radius-sm);
}
.f04-danger-t {
  color: var(--error);
  font-weight: 500;
}
.f04-danger-d {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.f04-dlg-p {
  margin: 0 0 8px;
  color: var(--text-2);
}
.f04-edit-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.f04-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.f04-pen-warning {
  font-size: 12px;
  line-height: 18px;
  color: var(--warning);
}
.f04-inline-error {
  font-size: 12px;
  line-height: 18px;
  color: var(--error);
}
:deep(.f04-table .v-data-table__tr) {
  height: 56px;
}
:deep(.f04-table .v-data-table__th) {
  height: 44px;
  background: var(--fill-1);
  color: var(--text-2);
  font-weight: 600;
}
:deep(.f04-table .v-data-table__td) {
  border-bottom: 1px solid var(--line-1);
}
:deep(.f04-table .v-data-table__tr:hover) {
  background: var(--fill-1);
}
</style>
