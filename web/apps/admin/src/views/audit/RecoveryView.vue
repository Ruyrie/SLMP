<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '@zmt/shared'
import { useAdminStore } from '@/stores/admin'
import { useRecoveryStore } from '@/stores/audit/recovery'
import { useAuditPendingStore } from '@/stores/audit/pending'
import { useUiStore } from '@/stores/ui'
import {
  getRecoveryVerificationRecords,
  proxyRecovery,
  resendRecoverySms,
  type RecoveryItem,
  type RecoveryVerificationRecord
} from '@/api/audit'
import { revealUserPhone } from '@/api/system'
import { fmtDateTime, fmtFull, fmtShortDate } from '@/views/audit/shared'

const route = useRoute()
const admin = useAdminStore()
const store = useRecoveryStore()
const pending = useAuditPendingStore()
const ui = useUiStore()

const canHandle = computed(() => admin.hasPerm('WA-R04:HANDLE'))
const detail = computed(() => store.detail)

const headers = [
  { title: '账号 · 原手机号', key: 'account', width: 180, sortable: false },
  { title: '新手机号', key: 'new_phone', width: 180, sortable: false },
  { title: '恢复码核对', key: 'code_check', width: 176, sortable: false },
  { title: '情况说明', key: 'description', sortable: false },
  { title: '提交方式', key: 'channel', width: 132, sortable: false },
  { title: '提交时间', key: 'submitted_at', width: 128, sortable: false },
  { title: '状态', key: 'status', width: 80, sortable: false },
  { title: '操作', key: 'actions', width: 60, align: 'end' as const, sortable: false }
]

const flow = [
  { title: '用户提交申请', desc: 'App、网页或客服代登记', status: 'done' },
  { title: '系统核对恢复码', desc: '连续填错 5 次锁定 24 小时', status: 'done' },
  { title: '人工核实身份', desc: '建议回拨新手机号', status: 'current' },
  { title: '重置密码并换绑', desc: '临时密码短信发到新手机号', status: 'wait' }
]

const ruleCards = [
  {
    tone: 'success',
    icon: 'check',
    title: '通过',
    items: [
      '重置密码，临时密码以短信发到新手机号',
      '登录手机号换绑为新手机号，旧恢复码作废',
      '其他设备上的登录立即失效',
      '首次登录先改密码、再保存新的恢复码；7 天内不能注销'
    ]
  },
  {
    tone: 'error',
    icon: 'block',
    title: '驳回',
    items: ['账号和原手机号保持不变', '驳回原因以短信发到新手机号，并记入账号的消息中心', '用户可以补充情况说明后重新申请']
  },
  {
    tone: 'neutral',
    icon: 'do_not_disturb_on',
    title: '不受理',
    items: ['没有恢复码的申请；客服代登记时同样按恢复码核对', '恢复码连续填错 5 次，24 小时内不能再提交', '同一账号已有待审核的申请时，不能再次提交']
  }
]

const drawerTitle = computed(() => (detail.value ? `${detail.value.user_name} · 账号找回申请` : '账号找回申请'))
const drawerSub = computed(() =>
  detail.value
    ? `${fmtFull(detail.value.submitted_at)} 通过 ${detail.value.channel?.submit_channel_text || '用户端'} 提交 · 原手机号 ${detail.value.old_phone}`
    : ''
)

const emptyTitle = computed(() => {
  if (store.keyword) return '没有符合条件的申请'
  if (store.tab === 'HANDLED') return '没有已处理的找回申请'
  return '没有待审核的找回申请'
})

const afterItems = computed(() => {
  const newPhone = detail.value?.after_actions?.new_phone || detail.value?.new_phone || '新手机号'
  const cancelDate = detail.value?.after_actions?.cancel_forbidden_until
  return [
    `重置密码，临时密码以短信发到新手机号 ${newPhone}`,
    `把登录手机号换绑为新手机号，旧恢复码作废`,
    `其他设备上的登录立即失效`,
    `首次登录先修改密码、再保存新的恢复码${cancelDate ? `；${fmtShortDate(cancelDate)}前不能注销` : ''}`
  ]
})

function verifyTone(result?: string): string {
  if (result === 'CODE_CORRECT') return 'success'
  if (result === 'LOCKED') return 'error'
  return 'warning'
}

function isHandled(status?: string): boolean {
  return status === 'APPROVED' || status === 'REJECTED'
}

function checkTone(level?: string): string {
  if (level === 'PASS') return 'success'
  if (level === 'WARN') return 'warning'
  return 'error'
}

function checkIcon(level?: string): string {
  if (level === 'PASS') return 'check_circle'
  if (level === 'WARN') return 'info'
  return 'error'
}

function farmText(): string {
  const farms = detail.value?.farms || []
  if (!farms.length) return '未加入养殖场'
  return farms.map((farm) => `${farm.farm_name} · ${farm.role_text}${farm.pen_text ? `（${farm.pen_text}）` : ''}`).join('；')
}

function openRow(row: RecoveryItem): void {
  void store.openDetail(row)
}

function refreshAll(): void {
  void store.fetchList()
  void pending.refresh()
}

/* ---------------- 恢复码校验记录 ---------------- */
const verifyRecords = ref<RecoveryVerificationRecord[]>([])
const verifyTotal = ref(0)
const verifyLoading = ref(false)
const verifyError = ref('')
let verifyToken = 0

async function loadVerifyRecords(id: number): Promise<void> {
  const token = ++verifyToken
  verifyLoading.value = true
  verifyError.value = ''
  try {
    const data = await getRecoveryVerificationRecords(id)
    if (token !== verifyToken) return
    verifyRecords.value = data.records || []
    verifyTotal.value = data.total || 0
  } catch (error) {
    if (token !== verifyToken) return
    verifyRecords.value = []
    verifyTotal.value = 0
    verifyError.value = error instanceof Error ? error.message : '校验记录加载失败'
    if (error instanceof ApiError && error.code === 40308) {
      ui.toast(error.message, 'error')
      await admin.me().catch(() => undefined)
    }
  } finally {
    if (token === verifyToken) verifyLoading.value = false
  }
}

/* ---------------- 查看完整手机号 ---------------- */
const revealedPhone = ref('')
const revealLoading = ref(false)

async function toastError(error: unknown, fallback: string): Promise<void> {
  ui.toast(error instanceof Error ? error.message : fallback, 'error')
  if (error instanceof ApiError && error.code === 40308) {
    await admin.me().catch(() => undefined)
  }
}

async function revealCurrentPhone(): Promise<void> {
  if (!detail.value || revealLoading.value) return
  const userId = detail.value.user_id
  revealLoading.value = true
  try {
    const data = await revealUserPhone(userId)
    revealedPhone.value = data.phone
    ui.toast(`已显示${data.name}的完整手机号`)
  } catch (error) {
    await toastError(error, '完整手机号查看失败')
  } finally {
    revealLoading.value = false
  }
}

/* ---------------- 重发找回结果短信 ---------------- */
const resendOpen = ref(false)
const resendSaving = ref(false)

async function submitResend(): Promise<void> {
  const current = detail.value
  if (!current || resendSaving.value) return
  resendSaving.value = true
  try {
    const result = await resendRecoverySms(current.id)
    resendOpen.value = false
    ui.toast(result.note ? `已重新发送到${result.phone}；${result.note}` : `已重新发送到${result.phone}`)
    await store.loadDetail(current.id)
  } catch (error) {
    await toastError(error, '重发短信失败')
  } finally {
    resendSaving.value = false
  }
}

/* ---------------- 客服代登记 ---------------- */
const proxyOpen = ref(false)
const proxySaving = ref(false)
const proxyError = ref('')
const proxyFieldErrors = reactive<Record<string, string>>({})
const proxyHasFieldError = computed(() => Object.keys(proxyFieldErrors).length > 0)
const proxyForm = reactive({
  phone: '',
  new_phone: '',
  recovery_code: '',
  description: ''
})

function clearProxyErrors(): void {
  Object.keys(proxyFieldErrors).forEach((key) => delete proxyFieldErrors[key])
  proxyError.value = ''
}

function openProxy(): void {
  if (!canHandle.value) {
    ui.toast('你没有处理账号找回的权限', 'error')
    return
  }
  proxyForm.phone = ''
  proxyForm.new_phone = ''
  proxyForm.recovery_code = ''
  proxyForm.description = ''
  clearProxyErrors()
  proxyOpen.value = true
}

function normalizeCode(value: string): string {
  return value.replace(/[\s\u3000-]/g, '').toUpperCase()
}

function validateProxy(): boolean {
  clearProxyErrors()
  if (!/^1\d{10}$/.test(proxyForm.phone.trim())) {
    proxyFieldErrors.phone = '请输入正确的 11 位手机号'
  }
  if (proxyForm.new_phone.trim() && !/^1\d{10}$/.test(proxyForm.new_phone.trim())) {
    proxyFieldErrors.new_phone = '请输入正确的 11 位手机号'
  }
  if (!/^[A-Z0-9]{16}$/.test(normalizeCode(proxyForm.recovery_code))) {
    proxyFieldErrors.recovery_code = '恢复码应为 16 位字母和数字'
  }
  if (!proxyForm.description.trim()) {
    proxyFieldErrors.description = '请填写情况说明'
  }
  const first = Object.values(proxyFieldErrors)[0]
  if (first) proxyError.value = first
  return !first
}

async function submitProxy(): Promise<void> {
  if (!validateProxy()) return
  proxySaving.value = true
  proxyError.value = ''
  try {
    const result = await proxyRecovery({
      phone: proxyForm.phone.trim(),
      new_phone: proxyForm.new_phone.trim() || undefined,
      recovery_code: normalizeCode(proxyForm.recovery_code),
      description: proxyForm.description.trim()
    })
    proxyOpen.value = false
    ui.toast(`已代登记${result.user_name}的找回申请`)
    if (store.tab !== 'PENDING') {
      store.tab = 'PENDING'
      store.page = 1
    }
    await Promise.all([store.fetchList(), pending.refresh()])
    await store.openDetailById(result.id)
  } catch (error) {
    if (error instanceof ApiError) {
      for (const field of error.fieldErrors) proxyFieldErrors[field.field] = field.message
      proxyError.value = error.message
      if (error.code === 40308) {
        ui.toast(error.message, 'error')
        await admin.me().catch(() => undefined)
      }
    } else {
      proxyError.value = error instanceof Error ? error.message : '代登记失败'
    }
  } finally {
    proxySaving.value = false
  }
}

watch(
  () => [store.drawer, store.activeId] as const,
  ([open, id]) => {
    if (open && id) void loadVerifyRecords(id)
    if (!open) {
      verifyToken += 1
      verifyLoading.value = false
      verifyRecords.value = []
      verifyTotal.value = 0
      verifyError.value = ''
      revealedPhone.value = ''
      resendOpen.value = false
    }
  }
)

watch(
  () => store.activeId,
  () => {
    revealedPhone.value = ''
  }
)

onMounted(() => {
  const id = Number(route.query.id || 0)
  void store.fetchList()
  void pending.refresh()
  if (id) void store.openDetailById(id)
})
</script>

<template>
  <zw-shell side="admin" active="WA-R04" :crumbs="['审核中心', '账号找回']">
    <zw-page-head
      title="账号找回"
      desc="凭恢复码找回账号的申请。系统先核对恢复码，正确的申请由你核实身份后重置密码并换绑新手机号；没有恢复码的申请不受理。"
    >
      <template #actions>
        <v-btn v-if="canHandle" variant="outlined" prepend-icon="person_add" @click="openProxy">代登记找回</v-btn>
        <v-btn variant="outlined" prepend-icon="refresh" :loading="store.status === 'loading'" @click="refreshAll">刷新</v-btn>
      </template>
      <v-tabs :model-value="store.tab" class="zw-tabs-line" @update:model-value="store.setTab(String($event))">
        <v-tab value="PENDING">待审核 {{ store.tabCounts.PENDING || 0 }}</v-tab>
        <v-tab value="HANDLED">已处理</v-tab>
      </v-tabs>
    </zw-page-head>

    <zw-card v-if="store.tab === 'PENDING'" class="flow-card">
      <zw-steps :items="flow" />
    </zw-card>

    <div class="zw-toolbar">
      <div class="zw-toolbar-l">
        <v-text-field
          v-model="store.keyword"
          class="zw-w-240"
          placeholder="搜索姓名或完整手机号"
          prepend-inner-icon="search"
          maxlength="30"
          @keydown.enter="store.fetchList()"
        />
        <v-btn variant="text" color="primary" @click="store.fetchList()">查询</v-btn>
        <v-btn variant="text" @click="store.resetFilters()">重置</v-btn>
      </div>
      <div class="zw-toolbar-r">
        <span class="zw-faint body-s">恢复码不正确的申请由系统直接退回，不进入本列表</span>
      </div>
    </div>

    <zw-card flush>
      <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@5" />
      <zw-empty v-else-if="store.status === 'forbidden'" icon="lock" title="你没有审核中心的查看权限" desc="审核中心只对超级管理员和审核员开放。如需处理，请联系超级管理员。">
        <v-btn variant="outlined" @click="$router.push({ name: 'WA-S02' })">返回平台概览</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'error'" icon="cloud_off" title="申请列表加载失败" :desc="store.error" tone="error">
        <v-btn variant="outlined" @click="store.fetchList()">重新加载</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'empty'" icon="task_alt" :title="emptyTitle" desc="新的申请会出现在这里，并在侧边菜单显示数量。" />
      <zw-empty v-else-if="store.status === 'filtered_empty'" icon="search_off" title="没有符合条件的申请" desc="换一个筛选条件试试。">
        <v-btn variant="text" color="primary" @click="store.resetFilters()">查看全部</v-btn>
      </zw-empty>
      <template v-else>
        <v-data-table :headers="headers" :items="store.list" item-key="id" hide-default-footer density="compact" class="recovery-table">
          <template #item.account="{ item }">
            <a class="zw-link zw-cell-main" @click="openRow(item as RecoveryItem)">{{ item.user_name }}</a>
            <span class="zw-muted num owner-phone">{{ item.old_phone }}</span>
          </template>
          <template #item.new_phone="{ item }">
            <span class="num">{{ item.new_phone }}</span>
            <zw-tag v-if="item.new_phone_verified" type="success" size="small" icon="check" class="ml-2">已验证</zw-tag>
          </template>
          <template #item.code_check="{ item }">
            <span v-if="!item.code_fail_count" class="code-ok">
              <zw-icon name="check_circle" :size="16" />正确 · 第 {{ item.code_attempt_no || 1 }} 次
            </span>
            <span v-else class="code-retry">
              <zw-icon name="check_circle" :size="16" />正确 · 第 {{ item.code_attempt_no || 1 }} 次（错 {{ item.code_fail_count }} 次）
            </span>
          </template>
          <template #item.description="{ item }">
            <span class="desc-cell" :title="item.description">{{ item.description || '—' }}</span>
          </template>
          <template #item.channel="{ item }">{{ item.submit_channel_text || item.submit_channel || '—' }}</template>
          <template #item.submitted_at="{ item }">
            <span class="num">{{ fmtDateTime(item.submitted_at) }}</span>
          </template>
          <template #item.status="{ item }">
            <zw-tag :type="item.status === 'PENDING' ? 'warning' : item.status === 'APPROVED' ? 'success' : 'error'">
              {{ item.status_text }}
            </zw-tag>
          </template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a v-if="store.tab === 'PENDING' && canHandle" @click="openRow(item as RecoveryItem)">处理</a>
              <a v-else @click="openRow(item as RecoveryItem)">详情</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot recovery-foot">
          <span class="zw-faint body-s">恢复码不正确的申请由系统直接退回，不进入本列表</span>
          <zw-pager
            :total="store.total"
            :page="store.page"
            :size="store.size"
            @update:page="store.changePage"
            @update:size="store.changeSize"
          />
        </div>
      </template>
    </zw-card>

    <div v-if="store.tab === 'PENDING'" class="rule-grid">
      <zw-card v-for="rule in ruleCards" :key="rule.title">
        <h4 class="rule-title">
          <span class="zw-ico-tile" :class="`zw-ico-tile--${rule.tone}`"><zw-icon :name="rule.icon" :size="18" /></span>
          {{ rule.title }}
        </h4>
        <ul class="rule-list">
          <li v-for="text in rule.items" :key="text">{{ text }}</li>
        </ul>
      </zw-card>
    </div>

    <zw-drawer v-model="store.drawer" :title="drawerTitle" :sub="drawerSub" width="560">
      <div v-if="store.detailLoading" class="zw-stack">
        <v-skeleton-loader type="paragraph@6" />
        <v-skeleton-loader type="table@3" />
      </div>
      <zw-empty v-else-if="store.detailError" icon="error" title="找回申请详情加载失败" :desc="store.detailError" tone="error">
        <v-btn variant="outlined" @click="store.loadDetail(store.activeId)">重新加载</v-btn>
      </zw-empty>
      <template v-else-if="detail">
        <section class="recovery-sec first-sec">
          <div class="zw-section-title">系统校验</div>
          <ul class="checks">
            <li v-for="check in detail.checks || []" :key="check.code">
              <zw-icon :name="checkIcon(check.level)" :size="18" :class="`check-ico check-ico--${checkTone(check.level)}`" fill />
              <span class="check-text">{{ check.text }}</span>
              <span v-if="check.extra" class="check-extra">{{ check.extra }}</span>
            </li>
            <li v-if="!detail.checks?.length" class="zw-faint">没有系统校验记录</li>
          </ul>
        </section>

        <section class="recovery-sec">
          <div class="zw-section-title">申请与账号</div>
          <zw-desc :cols="1" bordered :label-width="104">
            <zw-desc-item label="情况说明">{{ detail.description || '—' }}</zw-desc-item>
            <zw-desc-item label="手机号">
              <span class="num">{{ revealedPhone || detail.old_phone }}</span>
              <template v-if="detail.can_reveal_phone">
                <a v-if="!revealedPhone" class="zw-link reveal-link" @click="revealCurrentPhone">{{ revealLoading ? '加载中…' : '查看完整手机号' }}</a>
                <a v-else class="zw-link reveal-link" @click="revealedPhone = ''">隐藏</a>
              </template>
              <span class="reveal-new">新手机号 <span class="num">{{ detail.new_phone }}</span></span>
            </zw-desc-item>
            <zw-desc-item label="账号">
              {{ detail.user_name }} · {{ fmtShortDate(detail.account?.registered_at) }}注册
              <template v-if="detail.account?.recovery_code_created_at"> · 恢复码 {{ fmtShortDate(detail.account.recovery_code_created_at) }}生成</template>
            </zw-desc-item>
            <zw-desc-item label="所在养殖场">{{ farmText() }}</zw-desc-item>
            <zw-desc-item label="提交方式">
              {{ detail.channel?.submit_device || detail.channel?.submit_channel_text || '—' }}
              <template v-if="detail.channel?.submit_ip_region"> · IP 属地 {{ detail.channel.submit_ip_region }}</template>
            </zw-desc-item>
          </zw-desc>
        </section>

        <section class="recovery-sec">
          <div class="zw-section-title">
            恢复码校验记录
            <span v-if="verifyTotal" class="zw-faint body-s normal-weight">共 {{ verifyTotal }} 条</span>
          </div>
          <v-skeleton-loader v-if="verifyLoading" type="table-row@2" />
          <zw-empty v-else-if="verifyError" icon="cloud_off" title="校验记录加载失败" :desc="verifyError" tone="error">
            <v-btn variant="text" color="primary" @click="loadVerifyRecords(detail.id)">重新加载</v-btn>
          </zw-empty>
          <div v-else-if="!verifyRecords.length" class="zw-faint">暂无校验记录</div>
          <div v-else class="verify-table-wrap">
            <table class="verify-table">
              <thead>
                <tr><th>时间</th><th>结果</th><th class="verify-num">剩余次数</th><th>IP / 属地</th><th>设备</th></tr>
              </thead>
              <tbody>
                <tr v-for="record in verifyRecords" :key="record.seq">
                  <td class="num verify-time">{{ fmtFull(record.checked_at) }}</td>
                  <td>
                    <zw-tag :type="verifyTone(record.result)" size="small">{{ record.result_text || record.result }}</zw-tag>
                    <div v-if="record.result_desc" class="verify-sub">{{ record.result_desc }}</div>
                  </td>
                  <td class="num verify-num">{{ record.remaining_attempts === undefined || record.remaining_attempts === null ? '—' : record.remaining_attempts }}</td>
                  <td>
                    <span class="num">{{ record.ip || '—' }}</span>
                    <div v-if="record.ip_region" class="verify-sub">{{ record.ip_region }}</div>
                  </td>
                  <td>{{ record.device || record.submit_channel_text || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <template v-if="detail.status === 'PENDING'">
          <section class="recovery-sec">
            <div class="zw-section-title">核实记录</div>
            <zw-form-item label="核实方式" required>
              <v-radio-group v-model="store.verifyMethod">
                <v-radio v-for="method in store.verifyMethods" :key="method.value" :label="method.label" :value="method.value" />
              </v-radio-group>
            </zw-form-item>
            <zw-form-item
              label="处理说明"
              required
              hint="通过和驳回都要填写，记入操作日志"
              :error="store.noteError"
              class="note-item"
            >
              <v-textarea
                v-model="store.handleNote"
                placeholder="请记录核实过程和结果，最多 200 字"
                maxlength="200"
                counter
                persistent-counter
                rows="3"
                :error="!!store.noteError"
              />
            </zw-form-item>
          </section>

          <zw-notice type="info" class="after-notice" title="通过后系统会">
            <ol class="after-list">
              <li v-for="text in afterItems" :key="text">{{ text }}</li>
            </ol>
          </zw-notice>
        </template>

        <template v-else>
          <section class="recovery-sec">
            <div class="zw-section-title">处理结果</div>
            <zw-desc :cols="1" bordered :label-width="104">
              <zw-desc-item label="结果">
                <zw-tag :type="detail.status === 'APPROVED' ? 'success' : 'error'" :icon="detail.status === 'APPROVED' ? 'check' : 'block'">
                  {{ detail.status_text }}
                </zw-tag>
              </zw-desc-item>
              <zw-desc-item label="处理人">{{ detail.handled_by_name || '—' }}</zw-desc-item>
              <zw-desc-item label="处理时间">{{ fmtFull(detail.handled_at) }}</zw-desc-item>
              <zw-desc-item label="核实方式">{{ detail.verify_method_text || '—' }}</zw-desc-item>
              <zw-desc-item label="处理说明">{{ detail.handle_note || '—' }}</zw-desc-item>
            </zw-desc>
          </section>
        </template>

        <zw-notice v-if="detail.sms?.warning" type="warning" class="sms-notice">{{ detail.sms.warning }}</zw-notice>
      </template>

      <template #foot>
        <template v-if="detail">
          <span class="zw-foot-note">
            {{ detail.status === 'PENDING' ? '通过后临时密码以短信发到新手机号' : '结果短信发送情况可在账号消息中查看' }}
          </span>
          <template v-if="detail.status === 'PENDING'">
            <template v-if="canHandle">
              <v-btn variant="outlined" color="error" prepend-icon="block" @click="store.openConfirm('reject')">驳回</v-btn>
              <v-btn color="primary" prepend-icon="check" @click="store.openConfirm('approve')">通过并发送临时密码</v-btn>
            </template>
            <span v-else class="zw-foot-note">你没有处理权限，仅可查看</span>
          </template>
          <template v-else>
            <v-btn v-if="canHandle && isHandled(detail.status)" variant="outlined" prepend-icon="sms" @click="resendOpen = true">重发短信</v-btn>
            <v-btn variant="outlined" @click="store.drawer = false">关闭</v-btn>
          </template>
        </template>
        <v-btn v-else variant="outlined" @click="store.drawer = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog
      v-model="store.confirmOpen"
      :title="store.confirmAction === 'approve' ? `通过${detail?.user_name || '该用户'}的找回申请？` : `驳回${detail?.user_name || '该用户'}的找回申请？`"
      width="480"
      :icon="store.confirmAction === 'approve' ? 'manage_accounts' : 'block'"
      :tone="store.confirmAction === 'approve' ? 'info' : 'error'"
    >
      <template v-if="store.confirmAction === 'approve'">
        <p class="dialog-p">
          通过后，{{ detail?.user_name }}的登录手机号由 {{ detail?.old_phone }} 换绑为
          <b>{{ detail?.new_phone }}</b>，临时密码以短信发到新手机号，旧恢复码和其他设备上的登录立即失效。
        </p>
        <p v-if="store.handleNote" class="dialog-muted">处理说明：{{ store.handleNote }}</p>
      </template>
      <template v-else>
        <p class="dialog-p">
          驳回后，{{ detail?.user_name }}的账号和原手机号 {{ detail?.old_phone }} 保持不变，驳回原因以短信发到新手机号
          {{ detail?.new_phone }}，并记入账号的消息中心。
        </p>
        <p v-if="store.handleNote" class="dialog-muted">驳回原因：{{ store.handleNote }}</p>
      </template>
      <template #actions>
        <v-btn variant="outlined" @click="store.confirmOpen = false">取消</v-btn>
        <v-btn
          :color="store.confirmAction === 'approve' ? 'primary' : 'error'"
          :loading="store.submitting"
          @click="store.confirm()"
        >
          {{ store.confirmAction === 'approve' ? '通过' : '驳回' }}
        </v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="resendOpen" title="重发找回结果短信？" width="480" icon="sms" tone="warning">
      <p class="dialog-p">
        将向 <b class="num">{{ detail?.new_phone }}</b> 重新发送{{ detail?.status === 'APPROVED' ? '通过结果和新的临时密码' : '驳回结果' }}短信。
      </p>
      <p v-if="detail?.status === 'APPROVED'" class="dialog-muted">重新发送会重新生成临时密码，之前发出的临时密码立即失效。</p>
      <p class="dialog-muted">发送结果可在账号消息中查看，本次操作记入操作日志。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="resendSaving" @click="resendOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="resendSaving" @click="submitResend">确认重发</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="proxyOpen" title="代用户登记找回申请" width="560" icon="person_add" tone="primary">
      <zw-notice type="info" class="proxy-notice">
        代登记前请核对来电人身份和恢复码；提交后进入待审核队列，不会立即换绑手机号。
      </zw-notice>
      <zw-form-item label="手机号" required :error="proxyFieldErrors.phone" class="proxy-form-item">
        <v-text-field v-model="proxyForm.phone" maxlength="11" placeholder="账号当前手机号" :error="!!proxyFieldErrors.phone" />
      </zw-form-item>
      <zw-form-item label="恢复码" required :error="proxyFieldErrors.recovery_code" hint="16 位字母和数字，不区分大小写" class="proxy-form-item">
        <v-text-field v-model="proxyForm.recovery_code" maxlength="19" placeholder="请输入 16 位恢复码" :error="!!proxyFieldErrors.recovery_code" />
      </zw-form-item>
      <zw-form-item label="新手机号" optional :error="proxyFieldErrors.new_phone" hint="留空表示与原手机号相同" class="proxy-form-item">
        <v-text-field v-model="proxyForm.new_phone" maxlength="11" placeholder="选填，换绑后的新手机号" :error="!!proxyFieldErrors.new_phone" />
      </zw-form-item>
      <zw-form-item label="情况说明" required :error="proxyFieldErrors.description" hint="客服工单号、核实过程等，最多 200 字" class="proxy-form-item">
        <v-textarea
          v-model="proxyForm.description"
          placeholder="请记录核实过程和代登记原因"
          maxlength="200"
          counter
          persistent-counter
          rows="3"
          :error="!!proxyFieldErrors.description"
        />
      </zw-form-item>
      <div v-if="proxyError && !proxyHasFieldError" class="proxy-error">{{ proxyError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="proxySaving" @click="proxyOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="proxySaving" @click="submitProxy">提交代登记</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.flow-card {
  margin-bottom: 16px;
}
.flow-card :deep(.zw-card-body) {
  padding: 16px 20px;
}
.recovery-table :deep(td),
.recovery-table :deep(th) {
  white-space: nowrap;
}
.recovery-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.owner-phone {
  margin-left: 8px;
}
.code-ok,
.code-retry {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.code-ok {
  color: var(--success);
}
.code-retry {
  color: var(--warning);
}
.desc-cell {
  display: block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-2);
}
.rule-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}
.rule-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 22px;
  font-weight: 600;
}
.rule-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-2);
  font-size: 13px;
  line-height: 22px;
}
.recovery-sec {
  margin-top: 24px;
}
.first-sec {
  margin-top: 0;
}
.checks {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
}
.checks li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 16px;
  font-size: 14px;
  line-height: 22px;
}
.checks li + li {
  border-top: 1px solid var(--line-1);
}
.check-ico--success {
  color: var(--success);
}
.check-ico--warning {
  color: var(--warning);
}
.check-ico--error {
  color: var(--error);
}
.check-text {
  flex: 1;
  min-width: 0;
}
.check-extra {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}
.note-item {
  margin-top: 14px;
}
.after-notice {
  margin-top: 24px;
}
.after-list {
  margin: 0;
  padding-left: 20px;
}
.after-list li {
  margin: 2px 0;
}
.sms-notice {
  margin-top: 16px;
}
.dialog-p {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}
.dialog-muted {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-3);
}
.dialog-p + .dialog-muted {
  margin-top: 8px;
}
.normal-weight {
  font-weight: 400;
}
.reveal-link {
  margin-left: 8px;
  font-size: 13px;
  white-space: nowrap;
}
.reveal-new {
  margin-left: 14px;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}
.verify-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
}
.verify-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  line-height: 18px;
}
.verify-table th {
  padding: 8px 10px;
  background: var(--fill-1);
  color: var(--text-2);
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}
.verify-table td {
  padding: 8px 10px;
  border-top: 1px solid var(--line-1);
  vertical-align: top;
  white-space: nowrap;
}
.verify-num {
  width: 64px;
  text-align: center;
}
.verify-sub {
  margin-top: 2px;
  font-size: 11px;
  line-height: 16px;
  color: var(--text-3);
  white-space: normal;
}
.proxy-notice {
  margin-bottom: 16px;
}
.proxy-form-item {
  margin-top: 14px;
}
.proxy-error {
  margin-top: 10px;
  font-size: 12px;
  line-height: 20px;
  color: var(--error);
}
</style>
