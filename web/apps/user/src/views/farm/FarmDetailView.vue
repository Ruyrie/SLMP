<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { dayjs, formatNumber } from '@zmt/shared'
import { useFarmDetailStore } from '@/stores/farm/detail'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { useFarmReload } from '@/composables/useFarmReload'
import { farmStatusMeta, formatPhoneInput } from '@/stores/farm/shared'
import FarmQualView from './components/FarmQualView.vue'

const route = useRoute()
const router = useRouter()
const store = useFarmDetailStore()
const appFarm = useFarmStore()
const ui = useUiStore()
const { detail, status, error, transferOpen, transferPage, transferLoading, transferError, transferSaving, basicSaving, actionLoading } = storeToRefs(store)

const basicOpen = ref(false)
const basicForm = reactive({ contactName: '', contactPhone: '', scale: '', intro: '' })
const basicErrors = reactive<Record<string, string>>({})
const disableOpen = ref(false)
const enableOpen = ref(false)
const quitOpen = ref(false)
const transferSelected = ref<number | null>(null)

const permissions = computed(() => detail.value?.permissions || {})
const statusMeta = computed(() => farmStatusMeta(detail.value?.status))
const pageTitle = computed(() => detail.value?.name || '养殖场信息')
const pageDesc = computed(() => {
  const value = detail.value
  if (!value) return ''
  const parts = [`你是${value.role_text || '成员'}`, value.main_category_name || value.species_text || '']
  if (value.current_stock !== undefined && value.current_stock !== null) {
    parts.push(`存栏 ${formatNumber(value.current_stock)} ${value.count_unit === 'BIRD' ? '只' : '头'}`)
  } else if (value.registration?.scale) {
    parts.push(`规模 ${formatNumber(value.registration.scale)} ${value.registration.count_unit_text || ''}`)
  }
  return parts.filter(Boolean).join(' · ')
})

const summaryText = computed(() => {
  const summary = detail.value?.member_summary
  if (!summary) return ''
  const base = [
    `所有者 ${summary.owner_count ?? 0} 人`,
    `场长 ${summary.manager_count ?? 0} 人`,
    `员工 ${summary.staff_count ?? 0} 人`
  ].join(' · ')
  return summary.invite_count ? `${base}；邀请 ${summary.invite_count} 条` : base
})

const registrationTag = computed(() => {
  if (detail.value?.pending_change) return { type: 'warning' as const, text: '待审核' }
  if (detail.value?.rejected_change) return { type: 'error' as const, text: '已驳回' }
  return null
})

const markers = computed(() => {
  const reg = detail.value?.registration
  if (!reg || reg.longitude === undefined || reg.latitude === undefined) return []
  return [{ x: 50, y: 50, label: detail.value?.name || '养殖场' }]
})

const pendingTransfer = computed(() => transferPage.value?.pending || detail.value?.pending_transfer || null)

const selectedCandidateName = computed(() => {
  if (!transferSelected.value) return '新所有者'
  return transferPage.value?.candidates?.find((item) => item.member_id === transferSelected.value)?.name || '新所有者'
})

const transferFarm = computed(() => {
  if (transferPage.value?.farm) return transferPage.value.farm
  return {
    farm_id: detail.value?.farm_id || 0,
    name: detail.value?.name || '',
    main_category_name: detail.value?.main_category_name,
    count_unit: detail.value?.count_unit,
    current_stock: detail.value?.current_stock
  }
})

const canSubmitChange = computed(
  () =>
    detail.value?.status === 'NORMAL' &&
    Boolean(permissions.value.can_submit_change) &&
    !detail.value?.pending_change
)

function load(farmId: number): void {
  void store.fetch(farmId)
}

useFarmReload(() => {
  if (store.actionLoading === 'quit') return
  const current = appFarm.currentFarmId
  const routeId = Number(route.params.farmId || 0)
  if (appFarm.role === 'staff') {
    void router.replace({ name: 'WU-T02' })
    return
  }
  if (current && current !== routeId) {
    void router.replace({ name: 'WU-F03', params: { farmId: current } })
    return
  }
  if (routeId) void store.fetch(routeId)
})

watch(
  () => route.params.farmId,
  (value) => {
    const id = Number(value || 0)
    if (id && id !== store.farmId) load(id)
  }
)

onMounted(() => {
  const id = Number(route.params.farmId || appFarm.currentFarmId || 0)
  if (id) load(id)
})

/** 个人中心“注销账号”前的转让 / 停用：?action=transfer|disable 进入后直接打开对应操作。 */
function applyAction(): void {
  const action = String(route.query.action || '')
  const value = detail.value
  if (!action || !value || Number(value.farm_id) !== Number(route.params.farmId || value.farm_id)) return
  if (action === 'transfer') {
    if (value.role === 'OWNER' && permissions.value.can_transfer && value.status !== 'DISABLED') void openTransfer()
    else ui.toast('这个养殖场现在不能转让所有权', 'info')
  } else if (action === 'disable') {
    if (value.role === 'OWNER' && permissions.value.can_disable && value.status === 'NORMAL') disableOpen.value = true
    else ui.toast(value.status === 'DISABLED' ? '这个养殖场已经停用' : '这个养殖场现在不能停用', 'info')
  }
  const query = { ...route.query }
  delete query.action
  void router.replace({ query })
}

watch(detail, () => applyAction())
watch(() => route.query.action, (value) => {
  if (value) applyAction()
})

function openBasic(): void {
  const basic = detail.value?.basic
  if (!basic) return
  basicForm.contactName = basic.contact_name || ''
  basicForm.contactPhone = formatPhoneInput(basic.contact_phone || '')
  basicForm.scale = basic.scale === undefined || basic.scale === null ? '' : String(basic.scale)
  basicForm.intro = basic.intro || ''
  for (const key of Object.keys(basicErrors)) delete basicErrors[key]
  basicOpen.value = true
}

function onBasicPhone(value: string | number): void {
  basicForm.contactPhone = formatPhoneInput(String(value))
}

function validateBasic(): boolean {
  for (const key of Object.keys(basicErrors)) delete basicErrors[key]
  if (!basicForm.contactName.trim()) basicErrors.contactName = '请填写联系人'
  else if (Array.from(basicForm.contactName.trim()).length > 20) basicErrors.contactName = '联系人不能超过 20 个字'
  const phone = basicForm.contactPhone.replace(/\s/g, '')
  if (!/^1\d{10}$/.test(phone)) basicErrors.contactPhone = '请输入 11 位手机号'
  const scale = Number(basicForm.scale)
  if (!Number.isInteger(scale) || scale < 1 || scale > 999999) basicErrors.scale = '规模需为 1–999,999 之间的整数'
  if (Array.from(basicForm.intro.trim()).length > 200) basicErrors.intro = '简介不能超过 200 个字'
  return Object.keys(basicErrors).length === 0
}

async function saveBasic(): Promise<void> {
  if (!validateBasic()) return
  const ok = await store.updateBasic({
    contact_name: basicForm.contactName.trim(),
    contact_phone: basicForm.contactPhone.replace(/\s/g, ''),
    scale: Number(basicForm.scale),
    intro: basicForm.intro.trim(),
    version: detail.value?.basic?.version
  })
  if (ok) basicOpen.value = false
}

async function confirmDisable(): Promise<void> {
  const ok = await store.disable()
  if (ok) disableOpen.value = false
}

async function confirmEnable(): Promise<void> {
  const ok = await store.enable()
  if (ok) enableOpen.value = false
}

function onMembers(): void {
  if (!detail.value) return
  void router.push({ name: 'WU-F04', params: { farmId: detail.value.farm_id } })
}

function goMembersFromTransfer(): void {
  closeTransfer()
  onMembers()
}

function openChange(): void {
  if (!detail.value) return
  void router.push({ name: 'WU-F02', query: { mode: 'change', farmId: String(detail.value.farm_id) } })
}

async function openTransfer(): Promise<void> {
  transferSelected.value = null
  await store.openTransfer()
  if (!transferPage.value?.pending) {
    const candidates = transferPage.value?.candidates || []
    if (candidates.length === 1) transferSelected.value = candidates[0].member_id
  }
}

function closeTransfer(): void {
  store.closeTransfer()
}

async function confirmTransfer(): Promise<void> {
  if (!transferSelected.value) return
  const ok = await store.createTransfer(transferSelected.value)
  if (ok) store.closeTransfer()
}

async function cancelTransfer(): Promise<void> {
  const ok = await store.cancelTransfer()
  if (ok) await store.openTransfer()
}

async function confirmQuit(): Promise<void> {
  const ok = await store.quit()
  if (ok) {
    quitOpen.value = false
    if (appFarm.currentFarmId) await router.push({ name: 'WU-T01' })
    else await router.push({ name: 'WU-F01' })
  }
}

async function reloadTransfer(): Promise<void> {
  await openTransfer()
}

function formatDate(value?: string): string {
  return value ? dayjs(value).format('M月D日 HH:mm') : '—'
}

function transferConfirmText(): string {
  if (detail.value?.status === 'DISABLED') return '停用期间不能转让所有权'
  return ''
}
</script>

<template>
  <zw-shell side="user" active="WU-F03" :crumbs="['设置', '养殖场信息']">
    <v-skeleton-loader v-if="status === 'loading'" type="article, actions" />

    <zw-card v-else-if="status === 'forbidden'">
      <zw-empty
        icon="lock"
        title="无权查看"
        :desc="error || '养殖场信息和成员管理只对所有者和场长开放。如需查看，请联系所有者。'"
        tone="warning"
      >
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="status === 'error' || !detail">
      <zw-empty icon="cloud_off" title="养殖场信息加载失败" :desc="error || '网络连接不稳定，没能取到养殖场信息。'" tone="error">
        <v-btn variant="outlined" @click="load(Number(route.params.farmId))">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <zw-page-head :title="pageTitle" :desc="pageDesc">
        <template #tags>
          <zw-tag :type="statusMeta.tagType" :icon="statusMeta.icon">{{ statusMeta.label }}</zw-tag>
        </template>
        <template #actions>
          <v-btn v-if="store.canManageMembers" variant="outlined" prepend-icon="group" @click="onMembers">成员管理</v-btn>
        </template>
      </zw-page-head>

      <zw-notice v-if="detail.status === 'DISABLED'" type="error" icon="block" class="mb-4">
        {{ detail.name }}已停用。数据只能查看，不能登记新的记录。
      </zw-notice>
      <zw-notice v-else-if="detail.restrict_tip" icon="lock" class="mb-4">{{ detail.restrict_tip }}</zw-notice>

      <div class="f03-cols">
        <div class="f03-main zw-stack">
          <zw-card title="基本信息" sub="可直接修改">
            <template v-if="store.canEditBasic" #extra>
              <v-btn variant="text" color="primary" prepend-icon="edit" @click="openBasic">修改</v-btn>
            </template>
            <zw-desc :cols="2" bordered :label-width="104">
              <zw-desc-item label="联系人">{{ detail.basic?.contact_name || '—' }}</zw-desc-item>
              <zw-desc-item label="联系电话">
                <span class="num">{{ detail.basic?.contact_phone_masked || detail.basic?.contact_phone || '—' }}</span>
              </zw-desc-item>
              <zw-desc-item label="规模" span="2">
                设计存栏 <span class="num">{{ formatNumber(detail.basic?.scale) }}</span>
                {{ detail.registration?.count_unit_text || (detail.count_unit === 'BIRD' ? '只' : '头') }}
              </zw-desc-item>
              <zw-desc-item label="简介" span="2">{{ detail.basic?.intro || '—' }}</zw-desc-item>
            </zw-desc>
          </zw-card>

          <zw-card title="注册信息" :sub="detail.role === 'OWNER' ? '修改需重新审核' : '由所有者提交修改'">
            <template #extra>
              <zw-tag v-if="registrationTag" :type="registrationTag.type" size="small">{{ registrationTag.text }}</zw-tag>
              <v-btn
                v-if="canSubmitChange"
                variant="text"
                color="primary"
                prepend-icon="edit_note"
                @click="openChange"
              >提交资料变更</v-btn>
              <v-btn
                v-else-if="detail.role === 'OWNER' && detail.pending_change"
                variant="text"
                color="primary"
                @click="openChange"
              >查看变更内容</v-btn>
              <v-btn
                v-else-if="detail.role === 'OWNER' && detail.rejected_change"
                variant="text"
                color="primary"
                prepend-icon="edit"
                @click="openChange"
              >重新修改</v-btn>
            </template>

            <zw-notice v-if="detail.pending_change" type="warning" icon="schedule" class="mb-4">
              资料变更已在 {{ formatDate(detail.pending_change.submitted_at) }} 提交，审核期间旧信息照常有效，结果发到消息中心。
            </zw-notice>
            <zw-notice v-else-if="detail.rejected_change" type="error" icon="rule" class="mb-4">
              <b>资料变更已驳回。</b>{{ detail.rejected_change.reject_reason || '请按审核意见修改后重新提交。' }}
            </zw-notice>

            <zw-desc :cols="1" bordered :label-width="104">
              <zw-desc-item label="名称">{{ detail.registration?.name || detail.name }}</zw-desc-item>
              <zw-desc-item label="地址">{{ detail.registration?.address || '—' }}</zw-desc-item>
              <zw-desc-item label="资质图">
                <FarmQualView
                  v-if="detail.registration?.qualification_files?.length"
                  :files="detail.registration.qualification_files"
                  :width="96"
                  :height="72"
                />
                <span v-else class="zw-faint">暂无资质图</span>
              </zw-desc-item>
            </zw-desc>

            <zw-notice icon="info" class="mt-4">
              名称、地址和资质图的修改由所有者提交，需要重新审核，审核期间旧信息照常有效。
            </zw-notice>
          </zw-card>
        </div>

        <div class="f03-side zw-stack">
          <zw-card title="位置" flush>
            <zw-map :height="180" :controls="false" :scale="false" :labels="[]" :markers="markers" />
            <div class="f03-loc">
              <div class="f03-loc-t">{{ detail.registration?.address || '暂无地址' }}</div>
              <div class="f03-loc-d">位置随注册信息审核；修改位置需提交资料变更</div>
            </div>
          </zw-card>

          <zw-card title="成员" :sub="`${detail.member_summary?.member_total ?? 0} 人`">
            <template #extra><a class="zw-link" @click="onMembers">成员管理</a></template>
            <div class="f03-avs">
              <zw-avatar
                v-for="(avatar, index) in detail.member_summary?.avatars || []"
                :key="index"
                :name="avatar"
                :src="detail.member_summary?.avatar_urls?.[index]"
                :size="32"
              />
              <span v-if="!detail.member_summary?.avatars?.length" class="zw-faint">暂无成员</span>
            </div>
            <div class="f03-mem-d">{{ summaryText }}</div>
          </zw-card>

          <zw-card title="养殖场操作" flush>
            <template v-if="detail.role === 'OWNER'">
              <div v-if="permissions.can_transfer" class="f03-op">
                <span class="zw-ico-tile"><zw-icon name="swap_horiz" :size="18" /></span>
                <div class="f03-op-main">
                  <div class="f03-op-t">转让所有权</div>
                  <div class="f03-op-d">
                    {{ pendingTransfer ? `待${pendingTransfer.to_name || '对方'}确认` : '只能转给本场场长，对方确认后生效' }}
                  </div>
                </div>
                <v-btn variant="outlined" :disabled="detail.status === 'DISABLED'" @click="openTransfer">
                  {{ pendingTransfer ? '查看' : '转让' }}
                </v-btn>
              </div>
              <div v-if="permissions.can_disable && detail.status === 'NORMAL'" class="f03-op">
                <span class="zw-ico-tile zw-ico-tile--error"><zw-icon name="block" :size="18" /></span>
                <div class="f03-op-main">
                  <div class="f03-op-t danger">停用养殖场</div>
                  <div class="f03-op-d">数据保留，可重新启用</div>
                </div>
                <v-btn variant="outlined" color="error" :loading="actionLoading === 'disable'" @click="disableOpen = true">停用</v-btn>
              </div>
              <div v-if="detail.status === 'DISABLED'" class="f03-op">
                <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="restart_alt" :size="18" /></span>
                <div class="f03-op-main">
                  <div class="f03-op-t">重新启用养殖场</div>
                  <div class="f03-op-d">启用后，成员可以继续登记新的记录</div>
                </div>
                <v-btn variant="outlined" color="primary" :loading="actionLoading === 'enable'" @click="enableOpen = true">启用</v-btn>
              </div>
            </template>
            <template v-else>
              <div v-if="permissions.can_quit" class="f03-op">
                <span class="zw-ico-tile zw-ico-tile--error"><zw-icon name="logout" :size="18" /></span>
                <div class="f03-op-main">
                  <div class="f03-op-t danger">退出养殖场</div>
                  <div class="f03-op-d">退出后看不到{{ detail.name }}的数据，你登记过的记录保留</div>
                </div>
                <v-btn variant="outlined" color="error" @click="quitOpen = true">退出</v-btn>
              </div>
              <div v-if="!permissions.can_quit" class="f03-op">
                <span class="zw-ico-tile"><zw-icon name="info" :size="18" /></span>
                <div class="f03-op-main">
                  <div class="f03-op-t">暂无可用操作</div>
                  <div class="f03-op-d">如需调整养殖场，请联系所有者</div>
                </div>
              </div>
            </template>
          </zw-card>
        </div>
      </div>

      <zw-drawer v-model="basicOpen" title="修改基本信息" sub="保存后立即生效，系统保留修改记录" :width="560">
        <div class="zw-form">
          <zw-form-item label="联系人" required :error="basicErrors.contactName">
            <v-text-field v-model="basicForm.contactName" maxlength="20" />
          </zw-form-item>
          <zw-form-item label="联系电话" required :error="basicErrors.contactPhone">
            <v-text-field :model-value="basicForm.contactPhone" @update:model-value="onBasicPhone" />
          </zw-form-item>
          <zw-form-item label="设计存栏" required :error="basicErrors.scale">
            <v-text-field v-model="basicForm.scale" type="number" min="1" max="999999" :suffix="detail.registration?.count_unit_text || (detail.count_unit === 'BIRD' ? '只' : '头')" />
          </zw-form-item>
          <zw-form-item label="简介" optional :error="basicErrors.intro">
            <template #label-extra><span class="f03-count num">{{ basicForm.intro.length }} / 200</span></template>
            <v-textarea v-model="basicForm.intro" rows="3" maxlength="200" />
          </zw-form-item>
        </div>
        <template #foot>
          <v-btn variant="outlined" :disabled="basicSaving" @click="basicOpen = false">取消</v-btn>
          <v-btn color="primary" :loading="basicSaving" @click="saveBasic">保存</v-btn>
        </template>
      </zw-drawer>

      <zw-dialog v-model="disableOpen" :title="`停用${detail.name}？`" icon="block" tone="error" :width="420">
        <p class="f03-dlg-p">停用后，{{ detail.name }}的数据全部保留，但只能查看，不能再登记新的记录。</p>
        <p class="f03-dlg-p">以后你可以在养殖场信息中重新启用。</p>
        <template #actions>
          <v-btn variant="outlined" @click="disableOpen = false">取消</v-btn>
          <v-btn color="error" :loading="actionLoading === 'disable'" @click="confirmDisable">停用</v-btn>
        </template>
      </zw-dialog>

      <zw-dialog v-model="enableOpen" :title="`重新启用${detail.name}？`" icon="restart_alt" tone="info" :width="420">
        <p class="f03-dlg-p">启用后，成员可以继续登记新的记录。</p>
        <template #actions>
          <v-btn variant="outlined" @click="enableOpen = false">取消</v-btn>
          <v-btn color="primary" :loading="actionLoading === 'enable'" @click="confirmEnable">启用</v-btn>
        </template>
      </zw-dialog>

      <zw-dialog v-model="quitOpen" :title="`退出${detail.name}？`" icon="logout" tone="error" :width="420">
        <p class="f03-dlg-p">退出后，你从下一次操作起就看不到{{ detail.name }}的数据。你登记过的记录保留。</p>
        <template #actions>
          <v-btn variant="outlined" @click="quitOpen = false">取消</v-btn>
          <v-btn color="error" :loading="actionLoading === 'quit'" @click="confirmQuit">退出</v-btn>
        </template>
      </zw-dialog>

      <zw-dialog :model-value="transferOpen" title="转让所有权" icon="swap_horiz" tone="info" :width="560" @update:model-value="closeTransfer">
        <v-skeleton-loader v-if="transferLoading" type="list-item-two-line@4" />
        <zw-empty v-else-if="transferError" icon="cloud_off" title="转让信息加载失败" :desc="transferError" tone="error">
          <v-btn variant="outlined" @click="reloadTransfer">重新加载</v-btn>
        </zw-empty>
        <template v-else-if="transferPage">
          <div class="f03-farm">
            <span class="zw-ico-tile zw-ico-tile--primary zw-ico-tile--lg"><zw-icon name="domain" :size="22" /></span>
            <div>
              <div class="f03-farm-t">{{ transferFarm.name }}</div>
              <div class="f03-farm-d">
                你是所有者<template v-if="transferFarm.main_category_name"> · {{ transferFarm.main_category_name }}</template>
                <template v-if="transferFarm.current_stock !== undefined"> · 存栏 {{ formatNumber(transferFarm.current_stock) }} {{ transferFarm.count_unit === 'BIRD' ? '只' : '头' }}</template>
              </div>
            </div>
          </div>

          <template v-if="pendingTransfer">
            <div class="f03-sec">转让请求</div>
            <zw-notice icon="schedule">
              <b>已向{{ pendingTransfer.to_name || '对方' }}发送转让请求，等待对方确认。</b>确认前你仍是{{ detail.name }}的所有者。
            </zw-notice>
            <div class="f03-hint">发起于 {{ formatDate(pendingTransfer.created_at) }}，确认有效期至 {{ formatDate(pendingTransfer.expires_at) }}。</div>
          </template>

          <template v-else>
            <div class="f03-sec">选择新的所有者</div>
            <div v-if="!transferPage.candidates?.length" class="f03-transfer-empty">
              <zw-empty icon="person_search" title="本场还没有场长" :desc="`${detail.name}还没有场长。先在成员详情中把一名成员改为场长，再转让所有权。`">
                <v-btn color="primary" variant="outlined" @click="goMembersFromTransfer">去成员管理</v-btn>
              </zw-empty>
            </div>
            <template v-else>
              <div class="f03-pick-list">
                <div
                  v-for="candidate in transferPage.candidates"
                  :key="candidate.member_id"
                  class="f03-pick"
                  :class="{ on: transferSelected === candidate.member_id }"
                  role="radio"
                  tabindex="0"
                  :aria-checked="transferSelected === candidate.member_id"
                  @click="transferSelected = candidate.member_id"
                  @keydown.enter="transferSelected = candidate.member_id"
                >
                  <zw-avatar :name="candidate.name" :src="candidate.avatar_url" :size="32" />
                  <div>
                    <div>{{ candidate.name }}</div>
                    <div class="f03-farm-d num">场长 · {{ candidate.phone_masked || '—' }}</div>
                  </div>
                  <zw-icon :name="transferSelected === candidate.member_id ? 'radio_button_checked' : 'radio_button_unchecked'" :size="20" class="f03-radio" />
                </div>
              </div>
              <div class="f03-hint">只能从本场的场长中选择。要转给员工，需要先把他的角色改为场长。</div>

              <div class="f03-sec">转让生效后</div>
              <div class="f03-after">
                <div>
                  <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="workspace_premium" :size="18" /></span>
                  <div>
                    <div class="f03-after-t">{{ selectedCandidateName }}成为所有者</div>
                    <div class="f03-after-d">可以邀请和移出成员、修改角色、停用养殖场、转让所有权</div>
                  </div>
                </div>
                <div>
                  <span class="zw-ico-tile"><zw-icon name="person" :size="18" /></span>
                  <div>
                    <div class="f03-after-t">你变为场长</div>
                    <div class="f03-after-d">仍可使用上述操作以外的全部功能，包括查看经营数据和提交修改申请</div>
                  </div>
                </div>
              </div>

              <zw-notice icon="schedule" class="mt-4">
                <b>对方确认后才生效。</b>{{ selectedCandidateName === '新所有者' ? '对方' : selectedCandidateName }}会在消息中心收到转让请求，确认前你仍是{{ detail.name }}的所有者。
              </zw-notice>
            </template>
          </template>
        </template>

        <template #actions>
          <template v-if="pendingTransfer">
            <v-btn variant="text" color="error" :loading="transferSaving" @click="cancelTransfer">撤回转让请求</v-btn>
            <span style="flex: 1"></span>
            <v-btn variant="outlined" @click="closeTransfer">关闭</v-btn>
          </template>
          <template v-else-if="transferPage?.candidates?.length">
            <v-btn variant="outlined" @click="closeTransfer">取消</v-btn>
            <v-btn color="primary" :disabled="!transferSelected || Boolean(transferConfirmText())" :loading="transferSaving" @click="confirmTransfer">确认转让</v-btn>
          </template>
          <template v-else>
            <v-btn variant="outlined" @click="closeTransfer">关闭</v-btn>
          </template>
        </template>
      </zw-dialog>
    </template>
  </zw-shell>
</template>

<style scoped>
.f03-cols {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.f03-main {
  flex: 1 1 0;
  min-width: 0;
}
.f03-side {
  width: 360px;
  flex-shrink: 0;
}
.f03-loc {
  padding: 12px 20px 16px;
}
.f03-loc-t {
  color: var(--text-1);
}
.f03-loc-d {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.f03-avs {
  display: flex;
  align-items: center;
  gap: 8px;
}
.f03-mem-d {
  margin-top: 8px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.f03-op {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
}
.f03-op + .f03-op {
  border-top: 1px solid var(--line-1);
}
.f03-op-main {
  flex: 1;
  min-width: 0;
}
.f03-op-t {
  color: var(--text-1);
  font-weight: 500;
}
.f03-op-t.danger {
  color: var(--error);
}
.f03-op-d {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.f03-dlg-p {
  margin: 0 0 8px;
  color: var(--text-2);
}
.f03-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
}
.f03-farm {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
}
.f03-farm-t {
  font-weight: 600;
}
.f03-farm-d {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.f03-sec {
  margin: 16px 0 8px;
  font-weight: 600;
  color: var(--text-1);
}
.f03-pick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.f03-pick {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--control-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.f03-pick.on {
  border-color: var(--primary);
  background: var(--primary-subtle);
}
.f03-pick .f03-radio {
  margin-left: auto;
  color: var(--control-border);
}
.f03-pick.on .f03-radio {
  color: var(--primary);
}
.f03-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.f03-after {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.f03-after > div {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.f03-after-t {
  font-weight: 500;
  color: var(--text-1);
}
.f03-after-d {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-2);
}
.f03-transfer-empty {
  margin-top: 4px;
}
</style>
