<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '@zmt/shared'
import { useAdminStore } from '@/stores/admin'
import { useUsersStore } from '@/stores/system/users'
import { useUiStore } from '@/stores/ui'
import { revealUserPhone, type AdminUserItem } from '@/api/system'
import { fmtLastLogin, fmtShortDate } from '@/views/system/shared'

const route = useRoute()
const admin = useAdminStore()
const store = useUsersStore()
const ui = useUiStore()

const canHandle = computed(() => admin.hasPerm('WA-S03:HANDLE'))
const detail = computed(() => store.detail)
const target = computed(() => store.target)
const canRevealPhone = computed(() => detail.value?.can_reveal_phone ?? canHandle.value)

/* 完整手机号只保存在当前页面的局部变量里，不写入 store，关闭抽屉即清除。 */
const revealedPhone = ref('')
const revealLoading = ref(false)

async function revealPhone(): Promise<void> {
  if (!detail.value || revealLoading.value) return
  revealLoading.value = true
  try {
    const result = await revealUserPhone(detail.value.user_id)
    revealedPhone.value = result.phone
    ui.toast(`已显示${result.name}的完整手机号`)
  } catch (error) {
    ui.toast(error instanceof Error ? error.message : '完整手机号查看失败', 'error')
    if (error instanceof ApiError && error.code === 40308) {
      await admin.me().catch(() => undefined)
    }
  } finally {
    revealLoading.value = false
  }
}

watch(
  () => store.drawer,
  (open) => {
    if (!open) revealedPhone.value = ''
  }
)

watch(
  () => store.activeId,
  () => {
    revealedPhone.value = ''
  }
)

const headers = [
  { title: '用户', key: 'user', width: 210, sortable: false },
  { title: '所在养殖场', key: 'farms', sortable: false },
  { title: '角色', key: 'roles', width: 170, sortable: false },
  { title: '注册时间', key: 'registered_at', width: 100, sortable: false },
  { title: '最近登录', key: 'last_login', width: 150, sortable: false },
  { title: '', key: 'flags', width: 104, sortable: false },
  { title: '状态', key: 'status', width: 92, sortable: false },
  { title: '操作', key: 'actions', width: 96, align: 'end' as const, sortable: false }
]

const roleFilters = [
  { title: '全部角色', value: '' },
  { title: '所有者', value: 'OWNER' },
  { title: '场长', value: 'MANAGER' },
  { title: '员工', value: 'STAFF' },
  { title: '未加入养殖场', value: 'NONE' }
]

const registeredFilters = [
  { title: '全部注册时间', value: 'ALL' },
  { title: '本月', value: 'MONTH' },
  { title: '近 3 个月', value: 'QUARTER' },
  { title: '今年', value: 'YEAR' }
]

const statusSegs = computed(() => [
  { label: '全部', value: 'ALL', count: store.tabCounts.all ?? store.total },
  { label: '正常', value: 'NORMAL', count: store.tabCounts.normal ?? 0 },
  { label: '已停用', value: 'DISABLED', count: store.tabCounts.disabled ?? 0 }
])

const emptyTitle = computed(() => {
  if (store.hasFilter || store.query.status !== 'ALL') return '没有符合条件的账号'
  return '还没有用户账号'
})

const loginLogs = computed(() => (detail.value?.login_logs || []).slice(0, 12))

const identityTip = computed(() => {
  if (detail.value?.identity_tip && target.value?.user_id === detail.value.user_id) return detail.value.identity_tip
  if ((target.value?.farm_count || 0) > 0) return '停用账号不会冻结他所在的养殖场；需要时请到养殖场管理中冻结。'
  return '停用后该账号不能登录 App 和网页版，登记过的记录保留。'
})

function rolesText(item: AdminUserItem): string {
  const counts = item.role_counts || { owner: 0, manager: 0, staff: 0 }
  const parts: string[] = []
  if (counts.owner) parts.push(`所有者 ${counts.owner}`)
  if (counts.manager) parts.push(`场长 ${counts.manager}`)
  if (counts.staff) parts.push(`员工 ${counts.staff}`)
  return parts.length ? parts.join(' · ') : '未加入养殖场'
}

function flagTone(code?: string): string {
  if (code === 'RECOVERY_LOCKED') return 'error'
  if (code === 'FARM_PENDING' || code === 'RECOVERY_PENDING') return 'warning'
  return 'neutral'
}

function farmStatusTone(status?: string): string {
  if (status === 'NORMAL') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'error'
  return 'neutral'
}

function canDisable(item: AdminUserItem): boolean {
  return canHandle.value && item.status === 'NORMAL' && (item.actions || []).includes('DISABLE')
}

function canEnable(item: AdminUserItem): boolean {
  return canHandle.value && item.status !== 'NORMAL' && (item.actions || []).includes('ENABLE')
}

function resetFilters(): void {
  store.resetFilters()
}

onMounted(() => {
  const keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  if (keyword) store.query.keyword = keyword
  void store.fetchList()
  if (route.query.id) void store.openDetailById(Number(route.query.id))
})
</script>

<template>
  <zw-shell side="admin" active="WA-S03" :crumbs="['用户与系统', '用户账号']">
    <zw-page-head
      title="用户账号"
      desc="App 和网页版共用的用户账号，以手机号为唯一标识。停用和启用都必须填写原因，记入操作日志。"
    >
      <template #actions>
        <v-btn
          v-if="canHandle"
          variant="outlined"
          prepend-icon="download"
          :loading="store.exporting"
          @click="store.exportList()"
        >
          导出
        </v-btn>
      </template>
    </zw-page-head>

    <div class="zw-toolbar">
      <div class="zw-toolbar-l">
        <zw-seg :model-value="store.query.status" :items="statusSegs" @update:model-value="store.setStatus(String($event))" />
        <v-text-field
          v-model="store.query.keyword"
          class="zw-w-240"
          placeholder="搜索姓名或手机号"
          prepend-inner-icon="search"
          maxlength="20"
          @keydown.enter="store.applyFilters()"
        />
        <v-select
          v-model="store.query.role"
          :items="roleFilters"
          class="zw-w-140"
          @update:model-value="store.applyFilters()"
        />
        <v-select
          v-model="store.query.registered"
          :items="registeredFilters"
          class="zw-w-140"
          @update:model-value="store.applyFilters()"
        />
        <v-btn variant="text" color="primary" @click="store.applyFilters()">查询</v-btn>
        <v-btn variant="text" @click="resetFilters">重置</v-btn>
      </div>
    </div>

    <zw-card flush>
      <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@8" />
      <zw-empty v-else-if="store.status === 'forbidden'" icon="lock" title="你没有用户账号的查看权限" :desc="store.error">
        <v-btn variant="outlined" @click="$router.push({ name: 'WA-S02' })">返回平台概览</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'error'" icon="cloud_off" title="账号列表加载失败" :desc="store.error" tone="error">
        <v-btn variant="outlined" @click="store.fetchList()">重新加载</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'empty'" icon="group" :title="emptyTitle" desc="用户注册后会出现在这里。" />
      <zw-empty v-else-if="store.status === 'filtered_empty'" icon="search_off" title="没有符合条件的账号" desc="换一个筛选条件试试。">
        <v-btn variant="text" color="primary" @click="resetFilters">查看全部</v-btn>
      </zw-empty>
      <template v-else>
        <v-data-table :headers="headers" :items="store.list" item-key="user_id" hide-default-footer density="compact" class="user-table">
          <template #item.user="{ item }">
            <div class="user-cell">
              <zw-avatar :name="item.name" :src="item.avatar_url" :size="28" :tone="item.status === 'NORMAL' ? 'primary' : 'neutral'" />
              <a class="zw-link" :class="{ 'zw-muted': item.status !== 'NORMAL' }" @click="store.openDetail(item as AdminUserItem)">{{ item.name }}</a>
              <span class="zw-muted num">{{ item.phone }}</span>
            </div>
          </template>
          <template #item.farms="{ item }">
            <span class="farms-cell" :class="{ 'zw-faint': !item.farm_names_text }" :title="item.farm_names_text">
              {{ item.farm_names_text || '未加入养殖场' }}
            </span>
          </template>
          <template #item.roles="{ item }">
            <span :class="{ 'zw-faint': !item.role_counts?.owner && !item.role_counts?.manager && !item.role_counts?.staff }">{{ rolesText(item as AdminUserItem) }}</span>
          </template>
          <template #item.registered_at="{ item }">
            <span class="num">{{ fmtShortDate(item.registered_at) }}</span>
          </template>
          <template #item.last_login="{ item }">
            <span class="num">{{ fmtLastLogin(item.last_login_at, item.last_login_client_text) }}</span>
          </template>
          <template #item.flags="{ item }">
            <span class="flag-list">
              <zw-tag v-for="flag in item.flags || []" :key="flag.code" :type="flagTone(flag.code)" size="small">{{ flag.text }}</zw-tag>
            </span>
          </template>
          <template #item.status="{ item }">
            <zw-tag :type="item.status === 'NORMAL' ? 'success' : 'error'" :icon="item.status === 'NORMAL' ? undefined : 'block'">
              {{ item.status_text }}
            </zw-tag>
          </template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a @click="store.openDetail(item as AdminUserItem)">详情</a>
              <a v-if="canDisable(item as AdminUserItem)" class="danger" @click="store.openStatusFromRow(item as AdminUserItem, 'disable')">停用</a>
              <a v-if="canEnable(item as AdminUserItem)" @click="store.openStatusFromRow(item as AdminUserItem, 'enable')">启用</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot user-foot">
          <span class="zw-faint body-s">手机号中间 4 位隐藏；导出文件同样隐藏</span>
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

    <zw-drawer v-model="store.drawer" title="账号详情" :sub="detail ? `${detail.name} · ${fmtShortDate(detail.registered_at)}注册` : ''" width="560">
      <div v-if="store.detailLoading" class="zw-stack">
        <v-skeleton-loader type="paragraph@6" />
        <v-skeleton-loader type="table@3" />
      </div>
      <zw-empty v-else-if="store.detailError" icon="error" title="账号详情加载失败" :desc="store.detailError" tone="error">
        <v-btn variant="outlined" @click="store.openDetailById(store.activeId)">重新加载</v-btn>
      </zw-empty>
      <template v-else-if="detail">
        <div class="user-head">
          <zw-avatar :name="detail.name" :src="detail.avatar_url" :size="48" :tone="detail.status === 'NORMAL' ? 'primary' : 'neutral'" />
          <div>
            <div class="user-head-name">
              {{ detail.name }}
              <zw-tag :type="detail.status === 'NORMAL' ? 'success' : 'error'" size="small">{{ detail.status_text }}</zw-tag>
            </div>
            <div class="user-head-sub num">
              {{ revealedPhone || detail.phone }}
              <template v-if="canRevealPhone">
                <a v-if="!revealedPhone" class="zw-link phone-reveal" @click="revealPhone">{{ revealLoading ? '加载中…' : '查看完整号码' }}</a>
                <a v-else class="zw-link phone-reveal" @click="revealedPhone = ''">隐藏</a>
              </template>
              <template v-if="detail.role_summary"> · {{ detail.role_summary }}</template>
            </div>
          </div>
        </div>

        <section class="user-sec">
          <div class="zw-section-title">账号信息</div>
          <zw-desc :cols="2" bordered :label-width="88">
            <zw-desc-item label="注册">{{ fmtShortDate(detail.registered_at) }} · {{ detail.register_client_text || 'App' }}</zw-desc-item>
            <zw-desc-item label="恢复码">{{ detail.recovery_code_created_at ? `${fmtShortDate(detail.recovery_code_created_at)}生成` : '没有生成' }}</zw-desc-item>
            <zw-desc-item label="修改密码">{{ detail.password_updated_at ? fmtShortDate(detail.password_updated_at) : '没有记录' }}</zw-desc-item>
            <zw-desc-item label="账号找回">{{ detail.recovery_text || '没有记录' }}</zw-desc-item>
            <zw-desc-item label="最近登录" :span="2">
              {{ fmtLastLogin(detail.last_login_at, detail.last_login_client_text) }}
            </zw-desc-item>
          </zw-desc>
        </section>

        <section class="user-sec">
          <div class="zw-section-title">所在养殖场与角色</div>
          <table v-if="detail.farms?.length" class="mini-table">
            <thead><tr><th>养殖场</th><th>角色</th><th>状态</th><th class="right">规模</th></tr></thead>
            <tbody>
              <tr v-for="farm in detail.farms" :key="farm.farm_id">
                <td>{{ farm.farm_name }}</td>
                <td><zw-tag :type="farm.role === 'OWNER' ? 'primary' : 'neutral'" size="small">{{ farm.role_text }}</zw-tag></td>
                <td><zw-tag :type="farmStatusTone(farm.farm_status)" size="small">{{ farm.farm_status_text }}</zw-tag></td>
                <td class="right">{{ farm.scale_text || '—' }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="zw-faint">未加入养殖场</div>
        </section>

        <section class="user-sec">
          <div class="zw-section-title">
            登录记录
            <span class="zw-faint body-s normal-weight">保留近 90 天{{ (detail.login_logs?.length || 0) > loginLogs.length ? ` · 显示最近 ${loginLogs.length} 条` : '' }}</span>
          </div>
          <table v-if="loginLogs.length" class="mini-table">
            <thead><tr><th>时间</th><th>终端</th><th>IP 属地</th><th>结果</th></tr></thead>
            <tbody>
              <tr v-for="(log, index) in loginLogs" :key="index">
                <td class="num">{{ fmtShortDate(log.time) }} {{ log.time.slice(11, 16) }}</td>
                <td>{{ log.client_text }}</td>
                <td>{{ log.ip_region || '—' }}</td>
                <td :class="log.result === 'FAIL' ? 'zw-error-text' : ''">{{ log.result_text }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="zw-faint">没有登录记录</div>
        </section>

        <zw-notice v-if="detail.identity_tip" type="info" class="user-tip">{{ detail.identity_tip }}</zw-notice>
      </template>

      <template #foot>
        <span class="zw-foot-note">停用后不能登录 App 和网页版，记录保留</span>
        <template v-if="detail && canHandle">
          <v-btn v-if="detail.status === 'NORMAL'" variant="outlined" color="error" prepend-icon="block" @click="store.openStatus('disable')">停用账号</v-btn>
          <v-btn v-else color="primary" prepend-icon="check_circle" @click="store.openStatus('enable')">启用账号</v-btn>
        </template>
        <v-btn variant="outlined" @click="store.drawer = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog
      v-model="store.confirmOpen"
      :title="store.action === 'disable' ? `停用${target?.name || '该用户'}的账号？` : `启用${target?.name || '该用户'}的账号？`"
      width="520"
      :icon="store.action === 'disable' ? 'block' : 'check_circle'"
      :tone="store.action === 'disable' ? 'error' : 'success'"
    >
      <template v-if="store.action === 'disable'">
        <p class="dialog-p">
          停用后，{{ target?.name }}不能登录 App 和网页版，已登录的设备立即退出。他登记过的记录保留，登记人仍显示{{ target?.name }}。
        </p>
        <zw-notice type="info" class="dialog-notice">{{ identityTip }}</zw-notice>
        <zw-form-item label="停用原因" required :error="store.reasonError" hint="原因会记入操作日志；勾选下方选项时一并发到本人的消息中心" class="dialog-form">
          <v-textarea
            v-model="store.reason"
            placeholder="请填写停用原因，最多 200 字"
            maxlength="200"
            counter
            persistent-counter
            rows="3"
            :error="!!store.reasonError"
          />
        </zw-form-item>
        <v-checkbox v-model="store.notifyMessage" label="站内消息通知本人（重新启用后可在消息中心看到停用原因）" class="notify-check" />
      </template>
      <template v-else>
        <p class="dialog-p">
          启用后，{{ target?.name }}可以重新登录 App 和网页版，原登记记录和所在养殖场关系保持不变。
        </p>
        <zw-form-item label="启用原因" required :error="store.reasonError" hint="原因会记入操作日志" class="dialog-form">
          <v-textarea
            v-model="store.reason"
            placeholder="请填写启用原因，最多 200 字"
            maxlength="200"
            counter
            persistent-counter
            rows="3"
            :error="!!store.reasonError"
          />
        </zw-form-item>
      </template>
      <template #actions>
        <v-btn variant="outlined" @click="store.confirmOpen = false">取消</v-btn>
        <v-btn
          :color="store.action === 'disable' ? 'error' : 'primary'"
          :loading="store.submitting"
          @click="store.confirmStatus()"
        >
          {{ store.action === 'disable' ? '停用' : '启用' }}
        </v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.user-table :deep(td),
.user-table :deep(th) {
  white-space: nowrap;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.farms-cell {
  display: block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.flag-list {
  display: inline-flex;
  gap: 4px;
  flex-wrap: nowrap;
}
.user-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.user-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
}
.user-head-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
}
.user-head-sub {
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.phone-reveal {
  margin-left: 6px;
  font-size: 12px;
  white-space: nowrap;
}
.user-sec {
  margin-top: 24px;
}
.mini-table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--line-1);
  font-size: 14px;
}
.mini-table th {
  padding: 8px 12px;
  background: var(--fill-1);
  color: var(--text-2);
  font-weight: 600;
  text-align: left;
  border-bottom: 1px solid var(--line-1);
}
.mini-table td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--line-1);
}
.mini-table td.right {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.normal-weight {
  font-weight: 400;
}
.user-tip {
  margin-top: 24px;
}
.dialog-p {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}
.dialog-notice {
  margin-bottom: 4px;
}
.dialog-form {
  margin-top: 14px;
}
.notify-check {
  margin-top: 4px;
}
</style>
