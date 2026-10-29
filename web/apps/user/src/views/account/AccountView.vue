<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import RecoveryCodeCard from './components/RecoveryCodeCard.vue'
import { useAccountStore } from '@/stores/account/account'
import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'
import { useNoticeStore } from '@/stores/notice'
import { useUiStore } from '@/stores/ui'
import { useRole } from '@/composables/useRole'
import { formatFullDate, formatMonthDayWeek } from './accountForm'

const router = useRouter()
const store = useAccountStore()
const auth = useAuthStore()
const farm = useFarmStore()
const notice = useNoticeStore()
const ui = useUiStore()
const { isStaff } = useRole()

const avatarMenu = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const loggingOut = ref(false)
const pickMode = ref<'' | 'transfer' | 'disable'>('')
const version = '1.0.0'

const user = computed(() => store.user)
const avatarUrl = computed(() => user.value?.avatar_url || '')
const avatarName = computed(() => user.value?.name || user.value?.name_initial || auth.user?.name || '')
const farmStats = computed(() => store.farmStats)
const pendingCount = computed(() => store.pendingChangeRequests)
const showChangeRequest = computed(() => {
  if (isStaff.value) return false
  if (pendingCount.value > 0) return true
  const stats = farmStats.value
  if (!stats || !stats.total_count) return false
  return (stats.total_count || 0) - (stats.pending_audit_count || 0) - (stats.rejected_count || 0) > 0
})
const farmsSub = computed(() => {
  const stats = farmStats.value
  if (!stats || !stats.total_count) return '还没有养殖场'
  const parts = [`共 ${stats.total_count} 个`]
  if (stats.pending_audit_count) parts.push(`${stats.pending_audit_count} 个待审核`)
  if (stats.rejected_count) parts.push(`${stats.rejected_count} 个已驳回`)
  return parts.join(' · ')
})
const farmRows = computed(() => farm.farms)
const currentFarmText = computed(() => store.currentFarmLabel)
const cancelBanner = computed(() => {
  const preview = store.preview
  if (!preview) return ''
  if (store.activeOwnedFarms.length) {
    const names = store.activeOwnedFarms.map((item) => item.name || '').filter(Boolean).join('、')
    return `${names}仍在使用，请先转让给场长或停用后再注销。停用的养殖场数据保留 180 天，期间其他成员仍可查看和导出。`
  }
  if (store.frozenOwnedFarms.length) {
    const names = store.frozenOwnedFarms.map((item) => item.name || '').filter(Boolean).join('、')
    return `${names}已被平台冻结，暂时不能注销。解冻后先转让给其他成员或停用，再注销。原因见消息中心。`
  }
  if (store.retainedOwnedFarms.length && preview.can_cancel) {
    return `已停用的养殖场数据保留 180 天，${formatFullDate(preview.farm_cleanup_date)}后清理。期间其他成员仍可查看和导出，清理时他们会收到“养殖场已被删除”的消息。`
  }
  return ''
})
const cancelBannerType = computed(() => {
  if (store.activeOwnedFarms.length || store.frozenOwnedFarms.length) return 'warning'
  return 'info'
})
const pickCandidates = computed(() => {
  if (pickMode.value === 'transfer') return store.transferCandidates
  if (pickMode.value === 'disable') return store.disableCandidates
  return []
})
const canSubmitCancel = computed(
  () => store.canCancel && store.cancelConfirmed && store.cancelSmsCode.length === 6
)

onMounted(async () => {
  void store.loadAgreement()
  void farm.refreshList()
  await store.load()
})

onBeforeUnmount(() => store.stopTimers())

function onAvatarPick(): void {
  avatarMenu.value = false
  avatarInput.value?.click()
}

function onAvatarClear(): void {
  avatarMenu.value = false
  void store.removeAvatar()
}

function onAvatarFile(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) void store.selectAvatar(file)
}

function setPasswordField(field: 'current' | 'next' | 'confirm', value: string): void {
  store.password[field] = value
  const key =
    field === 'current' ? 'current_password' : field === 'next' ? 'new_password' : 'confirm_password'
  delete store.password.errors[key]
}

async function logout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logout()
  } finally {
    loggingOut.value = false
    farm.reset()
    notice.stop()
    notice.reset()
    ui.toast('已退出登录')
    await router.push({ name: 'WU-A01' })
  }
}

async function switchFarm(farmId: number, status?: string): Promise<void> {
  if (status === 'PENDING' || status === 'REJECTED') {
    await router.push({ name: 'WU-F01' })
    return
  }
  if (farmId === farm.currentFarmId) return
  try {
    await farm.switchTo(farmId)
    const target = farm.farms.find((item) => item.farm_id === farmId)
    ui.toast(`已切换到${target?.name || '新的养殖场'}`)
    await store.load()
  } catch {
    ui.toast('切换养殖场失败，请稍后重试', 'error')
  }
}

function farmLine1(item: { role_text?: string; status?: string; main_category_name?: string; current_stock?: number; count_unit_text?: string }): string {
  if (item.status === 'PENDING') return `${item.role_text || '所有者'} · 审核通过前只能修改注册信息`
  if (item.status === 'REJECTED') return `${item.role_text || '所有者'} · 审核未通过`
  if (item.current_stock !== undefined && item.current_stock !== null) {
    return `${item.role_text || ''} · ${item.main_category_name || ''} · 存栏 ${item.current_stock} ${item.count_unit_text || ''}`
  }
  return item.role_text || ''
}

function cancelFarmLine1(item: { farm_status?: string; farm_status_text?: string; current_stock?: number; count_unit_text?: string }): string {
  if (item.farm_status === 'NORMAL' && item.current_stock !== undefined) {
    return `所有者 · 存栏 ${item.current_stock} ${item.count_unit_text || ''}`
  }
  return `所有者 · ${item.farm_status_text || ''}`
}

function cancelFarmLine2(item: { disposal_text?: string; transfer_pending_to_name?: string | null }): string {
  if (item.transfer_pending_to_name) return `已发起转让，待${item.transfer_pending_to_name}确认`
  return item.disposal_text || ''
}

function cancelLineError(item: { disposal?: string; transfer_pending_to_name?: string | null }): boolean {
  return (
    item.disposal === 'TRANSFER_OR_DISABLE' ||
    item.disposal === 'WAIT_UNFREEZE' ||
    !!item.transfer_pending_to_name
  )
}

function onPasswordToggle(value: boolean): void {
  if (value) store.passwordOpen = true
  else store.closePassword()
}

function onRecoveryToggle(value: boolean): void {
  if (value) store.recoveryOpen = true
  else store.closeRecovery()
}

function onCancelToggle(value: boolean): void {
  if (value) {
    pickMode.value = ''
    void store.openCancel()
  } else {
    pickMode.value = ''
    store.closeCancel()
  }
}

function onTransfer(): void {
  const candidates = store.transferCandidates
  if (!candidates.length) {
    ui.toast('这些养殖场还没有场长，不能转让。可以先在成员管理中邀请场长。', 'info')
    return
  }
  chooseFarmAction(candidates, 'transfer')
}

function onDisable(): void {
  const candidates = store.disableCandidates
  if (!candidates.length) return
  chooseFarmAction(candidates, 'disable')
}

function chooseFarmAction(
  candidates: Array<{ farm_id: number; name?: string }>,
  action: 'transfer' | 'disable'
): void {
  if (candidates.length === 1) {
    void goFarmAction(candidates[0].farm_id, action)
    return
  }
  pickMode.value = action
}

function pickFarm(farmId: number): void {
  const action = pickMode.value === 'transfer' ? 'transfer' : 'disable'
  pickMode.value = ''
  void goFarmAction(farmId, action)
}

async function goFarmAction(farmId: number, action: 'transfer' | 'disable'): Promise<void> {
  store.cancelOpen = false
  if (farm.currentFarmId !== farmId) {
    try {
      await farm.switchTo(farmId)
      const target = farm.farms.find((item) => item.farm_id === farmId)
      ui.toast(`已切换到${target?.name || '养殖场'}`)
    } catch {
      ui.toast('切换养殖场失败，请稍后重试', 'error')
      return
    }
  }
  await router.push({ name: 'WU-F03', params: { farmId }, query: { action } })
}

async function exportReports(): Promise<void> {
  store.cancelOpen = false
  await router.push({ name: 'WU-E07', query: { scope: 'MINE' } })
}

async function submitCancel(): Promise<void> {
  const ok = await store.submitCancel()
  if (!ok) return
  store.cancelOpen = false
  auth.clear()
  farm.reset()
  notice.stop()
  notice.reset()
  ui.toast('账号已注销')
  await router.replace({ name: 'WU-A01' })
}

function goChangeRequests(): void {
  void router.push({ name: 'WU-U01' })
}

function goReminder(): void {
  void router.push({ name: 'WU-T03', query: { dialog: 'reminder' } })
}
</script>

<template>
  <zw-shell side="user" active="WU-A04" :crumbs="['设置', '个人中心']">
    <zw-page-head
      title="个人中心"
      desc="账号信息对你加入的所有养殖场通用，App 和网页版使用同一个账号。"
    >
      <template #actions>
        <v-btn variant="outlined" prepend-icon="logout" :loading="loggingOut" @click="logout">
          退出登录
        </v-btn>
      </template>
    </zw-page-head>

    <template v-if="store.status === 'loading'">
      <zw-card class="mb-4">
        <v-skeleton-loader type="list-item-avatar-three-line" />
      </zw-card>
      <div class="a04-cols">
        <div class="a04-main zw-stack">
          <zw-card title="账号与安全" flush>
            <v-skeleton-loader type="list-item-two-line@3" />
          </zw-card>
          <zw-card title="注销" flush>
            <v-skeleton-loader type="list-item-two-line" />
          </zw-card>
        </div>
        <div class="a04-side zw-stack">
          <zw-card title="我的养殖场" flush>
            <v-skeleton-loader type="list-item-two-line@3" />
          </zw-card>
          <zw-card title="更多" flush>
            <v-skeleton-loader type="list-item-two-line@3" />
          </zw-card>
        </div>
      </div>
    </template>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" title="个人信息加载失败" :desc="store.error" tone="error">
        <v-btn color="primary" prepend-icon="refresh" @click="store.load()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" title="无权查看" :desc="store.error || '你没有该页面的查看权限'">
        <v-btn variant="outlined" @click="router.push('/')">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <zw-card class="mb-4">
        <div class="a04-me">
          <v-menu v-model="avatarMenu" location="bottom start">
            <template #activator="{ props: activatorProps }">
              <button v-bind="activatorProps" class="a04-avatar-btn" type="button" aria-label="头像">
                <zw-avatar :name="avatarName" :src="avatarUrl" :size="64" />
                <span class="a04-avatar-edit"><zw-icon name="photo_camera" :size="14" /></span>
                <span v-if="store.avatarUploading" class="a04-avatar-mask">
                  <v-progress-circular :model-value="store.avatarProgress" :size="30" :width="3" color="white" />
                </span>
              </button>
            </template>
            <v-list density="compact" class="a04-avatar-menu">
              <v-list-item prepend-icon="upload" title="上传头像" @click="onAvatarPick" />
              <v-list-item
                v-if="avatarUrl"
                prepend-icon="person"
                title="恢复默认头像"
                @click="onAvatarClear"
              />
            </v-list>
          </v-menu>
          <input
            ref="avatarInput"
            class="a04-file"
            type="file"
            accept="image/jpeg,image/png"
            @change="onAvatarFile"
          />
          <div class="a04-me-main">
            <div v-if="!store.nameEditing" class="a04-name">
              {{ user?.name || auth.user?.name || '用户' }}
              <a class="zw-link" @click="store.startNameEdit">修改姓名</a>
            </div>
            <div v-else class="a04-name-edit">
              <v-text-field
                v-model="store.nameDraft"
                class="a04-name-field"
                maxlength="20"
                placeholder="请输入姓名"
                :error="!!store.nameError"
                autofocus
                @keydown.enter="store.saveName"
                @keydown.esc="store.cancelNameEdit"
              />
              <v-btn color="primary" :loading="store.nameSaving" @click="store.saveName">保存</v-btn>
              <v-btn variant="text" @click="store.cancelNameEdit">取消</v-btn>
            </div>
            <div v-if="store.nameError" class="a04-name-error">{{ store.nameError }}</div>
            <div v-else-if="store.nameEditing" class="a04-name-hint">养殖场成员和登记记录中显示这个姓名</div>
            <div class="a04-me-sub num">{{ store.phoneMasked || auth.phone }}</div>
            <div v-if="currentFarmText" class="a04-me-farm">当前养殖场：{{ currentFarmText }}</div>
          </div>
        </div>
      </zw-card>

      <div class="a04-cols">
        <div class="a04-main zw-stack">
          <zw-card title="账号与安全" flush>
            <div class="a04-row">
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="smartphone" :size="18" /></span>
              <div class="a04-row-main">
                <div class="a04-row-t">
                  登录手机号 <span class="a04-row-v num">{{ store.phoneMasked }}</span>
                </div>
                <div class="a04-row-d">
                  用于登录和接收验证码。手机号不能直接修改；手机号无法使用时，凭恢复码找回账号并换绑新手机号。
                </div>
              </div>
            </div>
            <div class="a04-row">
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="lock" :size="18" /></span>
              <div class="a04-row-main">
                <div class="a04-row-t">登录密码</div>
                <div class="a04-row-d">修改后，其他设备上的登录会自动失效。</div>
              </div>
              <v-btn class="a04-row-act" variant="outlined" @click="store.openPassword()">修改密码</v-btn>
            </div>
            <div class="a04-row">
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="key" :size="18" /></span>
              <div class="a04-row-main">
                <div class="a04-row-t">恢复码</div>
                <div class="a04-row-d">
                  手机号不能使用时，需要用恢复码找回账号。恢复码只在生成时显示一次，找不到时请重新生成并保存；需要短信验证，旧恢复码同时作废。
                </div>
              </div>
              <v-btn class="a04-row-act" variant="outlined" @click="store.openRecovery()">重新生成</v-btn>
            </div>
          </zw-card>

          <zw-card title="注销" flush>
            <div class="a04-row a04-danger">
              <span class="zw-ico-tile zw-ico-tile--error"><zw-icon name="no_accounts" :size="18" /></span>
              <div class="a04-row-main">
                <div class="a04-row-t">注销账号</div>
                <div class="a04-row-d">
                  删除个人数据，不能撤销；你创建的养殖场须先转让或停用。通过客服找回账号后 7 天内不能注销。
                </div>
              </div>
              <v-btn class="a04-row-act" variant="outlined" color="error" @click="store.openCancel()">
                注销账号
              </v-btn>
            </div>
          </zw-card>
        </div>

        <div class="a04-side zw-stack">
          <zw-card title="我的养殖场" :sub="farmsSub" flush>
            <template #extra><a class="zw-link" @click="router.push({ name: 'WU-F01' })">管理我的养殖场</a></template>
            <ul v-if="farmRows.length" class="a04-list">
              <li
                v-for="item in farmRows"
                :key="item.farm_id"
                class="a04-li"
                :class="{ muted: item.status !== 'NORMAL' }"
                @click="switchFarm(item.farm_id, item.status)"
              >
                <span
                  class="zw-ico-tile"
                  :class="item.status === 'NORMAL' ? 'zw-ico-tile--primary' : 'zw-ico-tile--warning'"
                >
                  <zw-icon name="domain" :size="18" />
                </span>
                <div class="a04-li-main">
                  <div class="a04-li-t">
                    <span>{{ item.name }}</span>
                    <zw-tag v-if="item.farm_id === farm.currentFarmId" type="primary" size="small">当前</zw-tag>
                    <zw-tag v-else-if="item.status === 'PENDING'" type="warning" size="small">待审核</zw-tag>
                    <zw-tag v-else-if="item.status === 'REJECTED'" type="error" size="small">已驳回</zw-tag>
                  </div>
                  <div class="a04-li-d">{{ farmLine1(item) }}</div>
                </div>
                <zw-icon name="chevron_right" :size="18" class="chev" />
              </li>
            </ul>
            <div v-else class="a04-farm-empty">
              <div>还没有养殖场。新建一个养殖场，或在消息中心同意别人的邀请。</div>
              <v-btn variant="text" color="primary" @click="router.push({ name: 'WU-F02' })">新建养殖场</v-btn>
            </div>
          </zw-card>

          <zw-card title="更多" flush>
            <ul class="a04-list">
              <li v-if="showChangeRequest" class="a04-li" @click="goChangeRequests">
                <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="edit_note" :size="18" /></span>
                <div class="a04-li-main">
                  <div class="a04-li-t">
                    <span>我的修改申请</span>
                    <zw-tag v-if="pendingCount > 0" type="warning" size="small">{{ pendingCount }} 条待审核</zw-tag>
                  </div>
                  <div class="a04-li-d">提交记录和审核结果</div>
                </div>
                <zw-icon name="chevron_right" :size="18" class="chev" />
              </li>
              <li class="a04-li" @click="goReminder">
                <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="notifications" :size="18" /></span>
                <div class="a04-li-main">
                  <div class="a04-li-t"><span>提醒设置</span></div>
                  <div class="a04-li-d">{{ store.reminderText }}</div>
                </div>
                <zw-icon name="chevron_right" :size="18" class="chev" />
              </li>
              <li class="a04-li" @click="store.openAbout()">
                <span class="zw-ico-tile"><zw-icon name="info" :size="18" /></span>
                <div class="a04-li-main">
                  <div class="a04-li-t"><span>关于智牧通</span></div>
                  <div class="a04-li-d">版本 {{ version }} · 用户协议 · 隐私政策</div>
                </div>
                <zw-icon name="chevron_right" :size="18" class="chev" />
              </li>
            </ul>
          </zw-card>
        </div>
      </div>
    </template>

    <!-- 修改密码 -->
    <zw-drawer
      :model-value="store.passwordOpen"
      title="修改密码"
      sub="修改后，其他设备上的登录会自动失效，需要用新密码重新登录。"
      width="560"
      @update:model-value="onPasswordToggle"
    >
      <div class="zw-form">
        <zw-form-item label="当前密码" required :error="store.password.errors.current_password">
          <v-text-field
            :model-value="store.password.current"
            :type="store.password.showCurrent ? 'text' : 'password'"
            placeholder="请输入当前密码"
            autocomplete="current-password"
            :append-inner-icon="store.password.showCurrent ? 'visibility' : 'visibility_off'"
            :error="!!store.password.errors.current_password"
            @update:model-value="setPasswordField('current', $event)"
            @click:append-inner="store.password.showCurrent = !store.password.showCurrent"
          />
        </zw-form-item>
        <zw-form-item
          label="新密码"
          required
          hint="8–20 位，需同时包含字母和数字"
          :error="store.password.errors.new_password"
        >
          <v-text-field
            :model-value="store.password.next"
            :type="store.password.showNext ? 'text' : 'password'"
            placeholder="请输入新密码"
            autocomplete="new-password"
            :append-inner-icon="store.password.showNext ? 'visibility' : 'visibility_off'"
            :error="!!store.password.errors.new_password"
            @update:model-value="setPasswordField('next', $event)"
            @click:append-inner="store.password.showNext = !store.password.showNext"
          />
        </zw-form-item>
        <zw-form-item label="再次输入新密码" required :error="store.password.errors.confirm_password">
          <v-text-field
            :model-value="store.password.confirm"
            :type="store.password.showNext ? 'text' : 'password'"
            placeholder="请再输入一次"
            autocomplete="new-password"
            :error="!!store.password.errors.confirm_password"
            @update:model-value="setPasswordField('confirm', $event)"
            @keydown.enter="store.submitPassword"
          />
        </zw-form-item>
      </div>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="store.closePassword()">取消</v-btn>
        <v-btn color="primary" :loading="store.password.submitting" @click="store.submitPassword">
          修改密码
        </v-btn>
      </template>
    </zw-drawer>

    <!-- 重新生成恢复码 -->
    <zw-dialog
      :model-value="store.recoveryOpen"
      title="重新生成恢复码"
      icon="key"
      tone="info"
      width="480"
      :persistent="store.recoveryStep === 2"
      :no-close="store.recoveryStep === 2"
      @update:model-value="onRecoveryToggle"
    >
      <zw-steps
        small
        class="mb-4"
        :items="[
          { title: '短信验证', status: store.recoveryStep === 2 ? 'done' : 'current' },
          { title: '保存新的恢复码', status: store.recoveryStep === 2 ? 'current' : 'wait' }
        ]"
      />
      <template v-if="store.recoveryStep === 1">
        <p class="a04-dlg-lead">验证后会生成新的恢复码，旧恢复码立即作废。</p>
        <zw-form-item
          label="短信验证码"
          required
          :hint="store.recovery.masked ? `验证码已发送至 ${store.recovery.masked}` : ''"
          :error="store.recovery.errors.sms_code"
        >
          <v-text-field
            :model-value="store.recovery.smsCode"
            placeholder="6 位验证码"
            inputmode="numeric"
            maxlength="6"
            :error="!!store.recovery.errors.sms_code"
            @update:model-value="store.setRecoverySms($event)"
          >
            <template #append-inner>
              <span v-if="store.recovery.countdown > 0" class="a04-count"
                >{{ store.recovery.countdown }} 秒后重新获取</span
              >
              <span v-else class="a04-get" @click="store.sendRecoverySms">
                {{ store.recovery.sending ? '发送中…' : '获取验证码' }}
              </span>
            </template>
          </v-text-field>
        </zw-form-item>
      </template>
      <template v-else>
        <p class="a04-dlg-lead">
          已生成新的恢复码，旧恢复码已作废。手机号不能使用时，需要用这个恢复码找回账号。
        </p>
        <RecoveryCodeCard
          v-if="store.recovery.code"
          :code="store.recovery.code.code"
          :phone-masked="store.recovery.code.phone_masked || store.phoneMasked"
          :generated-at="store.recovery.code.generated_at"
          compact
        />
        <zw-notice type="warning" class="mt-4">
          <b>恢复码只显示这一次。</b>请截图或抄下来，放在安全的地方。没有恢复码的找回申请一律不受理。
        </zw-notice>
        <v-checkbox v-model="store.recovery.saved" class="mt-2" label="我已保存恢复码" />
      </template>
      <template #actions>
        <template v-if="store.recoveryStep === 1">
          <v-btn variant="outlined" @click="store.closeRecovery()">取消</v-btn>
          <v-btn
            color="primary"
            :loading="store.recovery.submitting"
            :disabled="store.recovery.smsCode.length !== 6"
            @click="store.submitRecoveryCode"
          >
            验证并生成
          </v-btn>
        </template>
        <v-btn
          v-else
          color="primary"
          :disabled="!store.recovery.saved"
          :loading="store.recovery.submitting"
          @click="store.confirmRecovery"
        >
          完成
        </v-btn>
      </template>
    </zw-dialog>

    <!-- 注销账号 -->
    <zw-dialog
      :model-value="store.cancelOpen"
      title="注销账号"
      icon="no_accounts"
      tone="error"
      width="560"
      @update:model-value="onCancelToggle"
    >
      <zw-notice type="error">
        <b>注销后立即生效，不能撤销。</b>已登录的设备全部退出，手机号保留 30 天后才能重新注册。
      </zw-notice>

      <template v-if="store.cancelLoading">
        <v-skeleton-loader class="mt-4" type="list-item-two-line@4" />
      </template>
      <template v-else-if="store.cancelError">
        <zw-empty icon="cloud_off" title="注销信息加载失败" :desc="store.cancelError" tone="error">
          <v-btn color="primary" prepend-icon="refresh" @click="store.loadCancellationPreview()">重新加载</v-btn>
        </zw-empty>
      </template>
      <template v-else-if="store.preview">
        <zw-notice v-if="store.recoveryRestricted" type="error" icon="event_busy" class="mt-3">
          <b>账号找回后 7 天内不能注销。</b>{{ formatMonthDayWeek(store.preview.cancel_available_date) }}起可以注销。
        </zw-notice>

        <div class="a04-sec">注销清单</div>
        <div class="a04-del">
          <div class="a04-del-row">
            <span class="zw-ico-tile"><zw-icon name="person" :size="18" /></span>
            <div class="a04-del-main">
              <div class="a04-del-t">个人数据</div>
              <div class="a04-del-d">{{ store.preview.personal_data_desc || '消息、AI 问答、待办设置、恢复码' }}</div>
            </div>
            <span class="a04-del-d">注销后删除</span>
          </div>
          <div v-for="owned in store.preview.owned_farms || []" :key="owned.farm_id" class="a04-del-row">
            <span
              class="zw-ico-tile"
              :class="
                cancelLineError(owned)
                  ? owned.disposal === 'WAIT_UNFREEZE'
                    ? 'zw-ico-tile--warning'
                    : 'zw-ico-tile--error'
                  : ''
              "
            >
              <zw-icon name="domain" :size="18" />
            </span>
            <div class="a04-del-main">
              <div class="a04-del-t">{{ owned.name }}</div>
              <div class="a04-del-d">{{ cancelFarmLine1(owned) }}</div>
              <div class="a04-del-d" :class="{ 'a04-del-err': cancelLineError(owned) }">
                {{ cancelFarmLine2(owned) }}
              </div>
            </div>
            <zw-tag icon="group">{{ owned.member_count || 0 }} 名成员</zw-tag>
          </div>
        </div>

        <zw-notice v-if="cancelBanner" :type="cancelBannerType" icon="info" class="mt-3">
          {{ cancelBanner }}
          <div class="a04-nlinks">
            <a v-if="store.activeOwnedFarms.length" @click="onTransfer">转让所有权</a>
            <a v-if="store.activeOwnedFarms.length" @click="onDisable">停用养殖场</a>
            <a v-if="store.retainedOwnedFarms.length || store.activeOwnedFarms.length" @click="exportReports">
              导出经营报表
            </a>
          </div>
        </zw-notice>

        <div v-if="pickMode" class="a04-pick">
          <div class="a04-pick-title">{{ pickMode === 'transfer' ? '选择要转让的养殖场' : '选择要停用的养殖场' }}</div>
          <button
            v-for="item in pickCandidates"
            :key="item.farm_id"
            type="button"
            class="a04-pick-item"
            @click="pickFarm(item.farm_id)"
          >
            <span>{{ item.name }}</span>
            <span class="zw-faint">{{ item.member_count || 0 }} 名成员</span>
          </button>
          <v-btn variant="text" size="small" @click="pickMode = ''">取消</v-btn>
        </div>

        <template v-if="store.exitNames">
          <div class="a04-sec">将要退出的养殖场</div>
          <div class="a04-del">
            <div class="a04-del-row">
              <span class="zw-ico-tile"><zw-icon name="logout" :size="18" /></span>
              <div class="a04-del-main">
                <div class="a04-del-t">退出{{ store.exitNames }}</div>
                <div class="a04-del-d">你登记的记录保留，登记人显示为“已注销用户”</div>
              </div>
            </div>
          </div>
        </template>

        <template v-if="store.canCancel">
          <v-checkbox
            :model-value="store.cancelConfirmed"
            class="a04-check"
            label="我已了解注销后果"
            @update:model-value="store.setCancelConfirmed"
          />
          <zw-form-item
            label="短信验证码"
            required
            :hint="store.cancelMasked ? `验证码已发送至 ${store.cancelMasked}` : ''"
            :error="store.cancelSmsError"
          >
            <v-text-field
              :model-value="store.cancelSmsCode"
              placeholder="6 位验证码"
              inputmode="numeric"
              maxlength="6"
              :error="!!store.cancelSmsError"
              @update:model-value="store.setCancelSms($event)"
            >
              <template #append-inner>
                <span v-if="store.cancelCountdown > 0" class="a04-count"
                  >{{ store.cancelCountdown }} 秒后重新获取</span
                >
                <span v-else class="a04-get" @click="store.sendCancelSms">
                  {{ store.cancelSending ? '发送中…' : '获取验证码' }}
                </span>
              </template>
            </v-text-field>
          </zw-form-item>
        </template>
        <div class="a04-note">
          未处理的转让、邀请和修改申请会自动撤回，找回申请自动驳回。通过客服找回账号后 7 天内不能注销。注销 30
          天后才能用同一手机号重新注册，得到的是一个全新的账号，以前的数据不会恢复。
        </div>
      </template>

      <template #actions>
        <v-btn variant="outlined" @click="store.closeCancel()">取消</v-btn>
        <v-btn
          v-if="!store.cancelError"
          color="error"
          prepend-icon="delete_forever"
          :disabled="!canSubmitCancel"
          :loading="store.cancelSubmitting"
          @click="submitCancel"
        >
          注销账号
        </v-btn>
      </template>
    </zw-dialog>

    <!-- 关于智牧通 -->
    <zw-dialog v-model="store.aboutOpen" title="关于智牧通" width="420">
      <div class="a04-about">
        <div class="a04-about-name">智牧通</div>
        <div class="a04-about-ver">版本 {{ version }}</div>
        <div class="a04-about-links">
          <a class="zw-link" @click="store.openAgreementUrl('user')">用户协议</a>
          <a class="zw-link" @click="store.openAgreementUrl('privacy')">隐私政策</a>
        </div>
      </div>
      <template #actions>
        <v-btn color="primary" @click="store.aboutOpen = false">知道了</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.a04-me {
  display: flex;
  align-items: center;
  gap: 20px;
}
.a04-avatar-btn {
  position: relative;
  width: 64px;
  height: 64px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
}
.a04-avatar-edit {
  position: absolute;
  right: -2px;
  bottom: -2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid var(--bg-card);
  background: var(--primary);
  color: var(--on-primary);
}
.a04-avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgb(0 0 0 / 45%);
}
.a04-avatar-menu {
  min-width: 180px;
}
.a04-file {
  display: none;
}
.a04-me-main {
  flex: 1;
  min-width: 0;
}
.a04-name {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
}
.a04-name a {
  font-size: 14px;
  font-weight: 400;
}
.a04-name-edit {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.a04-name-field {
  width: 240px;
  max-width: 240px;
  flex: 0 0 240px;
}
.a04-name-error {
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--error);
}
.a04-name-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.a04-me-sub {
  margin-top: 4px;
  color: var(--text-2);
}
.a04-me-farm {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.a04-cols {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.a04-main {
  flex: 1 1 0;
  min-width: 0;
}
.a04-side {
  width: 380px;
  flex-shrink: 0;
}
.a04-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 20px;
}
.a04-row + .a04-row {
  border-top: 1px solid var(--line-1);
}
.a04-row .zw-ico-tile {
  margin-top: 2px;
}
.a04-row-main {
  flex: 1;
  min-width: 0;
}
.a04-row-t {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: var(--text-1);
}
.a04-row-v {
  font-weight: 400;
  color: var(--text-1);
}
.a04-row-d {
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.a04-row-act {
  flex-shrink: 0;
  align-self: center;
}
.a04-danger .a04-row-t {
  color: var(--error);
}
.a04-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
}
.a04-li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  cursor: pointer;
}
.a04-li:hover {
  background: var(--fill-1);
}
.a04-li-main {
  flex: 1;
  min-width: 0;
}
.a04-li-t {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-1);
}
.a04-li-d {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.a04-li.muted .a04-li-t > span:first-child {
  color: var(--text-2);
}
.a04-li .chev {
  color: var(--text-3);
}
.a04-farm-empty {
  padding: 16px 20px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.a04-count {
  font-size: 14px;
  color: var(--text-3);
  white-space: nowrap;
}
.a04-get {
  font-size: 14px;
  color: var(--primary);
  white-space: nowrap;
  cursor: pointer;
}
.a04-dlg-lead {
  margin: 0 0 16px;
}
.a04-sec {
  margin: 16px 0 8px;
  font-size: 14px;
  line-height: 22px;
  font-weight: 600;
  color: var(--text-1);
}
.a04-del {
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
}
.a04-del-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
}
.a04-del-row + .a04-del-row {
  border-top: 1px solid var(--line-1);
}
.a04-del-main {
  flex: 1;
  min-width: 0;
}
.a04-del-t {
  color: var(--text-1);
}
.a04-del-d {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.a04-del-err {
  color: var(--error);
}
.a04-note {
  margin-top: 12px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.a04-nlinks {
  display: flex;
  gap: 24px;
  margin-top: 6px;
}
.a04-nlinks a {
  color: inherit;
  font-weight: 500;
  text-decoration: underline;
  cursor: pointer;
}
.a04-check {
  margin: 12px 0 8px -4px;
}
.a04-pick {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--fill-1);
}
.a04-pick-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
}
.a04-pick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-1);
  font: inherit;
  cursor: pointer;
}
.a04-pick-item + .a04-pick-item {
  margin-top: 6px;
}
.a04-about {
  padding: 8px 0 16px;
  text-align: center;
}
.a04-about-name {
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  color: var(--primary);
}
.a04-about-ver {
  margin-top: 4px;
  color: var(--text-2);
}
.a04-about-links {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
}
</style>
