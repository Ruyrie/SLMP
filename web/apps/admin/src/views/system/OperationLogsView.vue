<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { formatDateTime } from '@zmt/shared'
import { useAdminStore } from '@/stores/admin'
import { useOperationLogsStore } from '@/stores/system/operationLogs'
import type { OperationLogItem } from '@/api/system'

const store = useOperationLogsStore()
const admin = useAdminStore()
const router = useRouter()
const selected = ref<OperationLogItem | null>(null)

const opTypes = [
  { title: '全部类型', value: '' },
  { title: '登录与密码', value: 'LOGIN' },
  { title: '养殖场注册审核', value: 'FARM_AUDIT' },
  { title: '资料变更审核', value: 'FARM_CHANGE' },
  { title: '修改申请审核', value: 'CHANGE_REQUEST' },
  { title: '账号找回', value: 'ACCOUNT_RECOVERY' },
  { title: '养殖场冻结 / 解冻', value: 'FARM_FREEZE' },
  { title: '用户账号停用 / 启用', value: 'USER_STATUS' },
  { title: '通知发布', value: 'NOTICE' },
  { title: '市场行情发布', value: 'MARKET_PRICE' },
  { title: '养殖课堂', value: 'COURSE' },
  { title: '知识库', value: 'KNOWLEDGE' },
  { title: 'AI 问答更正', value: 'AI_CORRECTION' },
  { title: '基础数据', value: 'BASE_DATA' },
  { title: '管理员与权限', value: 'ADMIN_RBAC' }
]

const adminOptions = computed(() => [{ title: '全部管理员', value: 0 }, ...store.admins.map((item) => ({ title: `${item.name}（${item.username}）`, value: item.admin_id }))])

const headers = [
  { title: '时间', key: 'created_at', width: 150, sortable: false },
  { title: '操作人', key: 'operator_name', width: 140, sortable: false },
  { title: '类型', key: 'op_type_text', width: 150, sortable: false },
  { title: '操作', key: 'op_action_text', width: 140, sortable: false },
  { title: '对象', key: 'target_name', width: 230, sortable: false },
  { title: '原因 / 说明', key: 'reason', sortable: false },
  { title: '', key: 'actions', width: 64, sortable: false }
]

const changeRows = computed(() => (store.detail?.changes || []).map((item) => ({ label: item.field_name, before: item.before, after: item.after })))
const relatedItems = computed(() =>
  (store.detail?.related || []).map((item) => ({
    title: item.desc,
    time: formatDateTime(item.time),
    desc: [item.source_text, item.status_text].filter(Boolean).join(' · '),
    tone: 'neutral'
  }))
)

function actionTone(row: OperationLogItem): string {
  if (row.result === 'FAIL') return 'error'
  if (['APPROVE', 'ENABLE', 'UNFREEZE', 'PUBLISH', 'SEND', 'ON_SHELF', 'TOPIC_ON_SHELF', 'REVIEW_PASS'].includes(row.op_action)) return 'success'
  if (['REJECT', 'DISABLE', 'FREEZE', 'WITHDRAW', 'OFF_SHELF', 'TOPIC_OFF_SHELF', 'REVOKE', 'REVIEW_RETURN', 'CATEGORY_DISABLE'].includes(row.op_action)) return 'warning'
  return 'neutral'
}

/** 日志对应的记录：只在页面存在、当前管理员有查看权限且服务端给出定位参数时显示按钮 */
const recordTarget = computed<RouteLocationRaw | null>(() => {
  const link = store.detail?.link
  if (!link?.exists || !link.page_code) return null
  const record = router.getRoutes().find((item) => item.name === link.page_code)
  if (!record || record.meta.public || !admin.hasPerm(record.meta.perm as string | undefined)) return null
  const query = link.query && Object.keys(link.query).length ? { ...link.query } : link.biz_id ? { id: String(link.biz_id) } : null
  return query ? { name: link.page_code, query } : null
})

function openRecord(): void {
  if (!recordTarget.value) return
  store.drawer = false
  void router.push(recordTarget.value)
}

function openDetail(row: OperationLogItem): void {
  selected.value = row
  void store.openDetail(row.log_id)
}

function changePage(page: number): void {
  store.query.page = page
  void store.fetchList()
}

function changeSize(size: number): void {
  store.query.size = size
  store.query.page = 1
  void store.fetchList()
}

onMounted(() => {
  void store.loadAdmins()
  void store.fetchList()
})
</script>

<template>
  <zw-shell side="admin" active="WA-S06" :crumbs="['用户与系统', '操作日志']">
    <zw-page-head title="操作日志" desc="管理端的审核、冻结、停用、发布、基础数据修改和权限变更全部留痕，只能查看和导出。">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="download" :disabled="!store.list.length" @click="store.exportCsv()">导出当前页</v-btn>
      </template>
    </zw-page-head>

    <div class="zw-toolbar log-filters">
      <div class="zw-toolbar-l">
        <v-text-field v-model="store.query.date_from" type="date" class="zw-w-140" density="compact" hide-details />
        <span class="zw-muted">至</span>
        <v-text-field v-model="store.query.date_to" type="date" class="zw-w-140" density="compact" hide-details @keydown.enter="store.search()" />
        <v-select v-model="store.query.admin_id" :items="adminOptions" class="zw-w-200" density="compact" hide-details @update:model-value="store.search()" />
        <v-select v-model="store.query.op_type" :items="opTypes" class="zw-w-200" density="compact" hide-details @update:model-value="store.search()" />
        <v-text-field v-model="store.query.keyword" class="zw-w-240" placeholder="搜索对象、账号、批次号" prepend-inner-icon="search" density="compact" hide-details @keydown.enter="store.search()" />
        <v-btn variant="text" color="primary" @click="store.search()">查询</v-btn>
        <v-btn variant="text" @click="store.resetFilters()">重置</v-btn>
      </div>
    </div>

    <zw-card flush>
      <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@8" />
      <zw-empty v-else-if="store.status === 'error'" icon="cloud_off" title="加载失败" :desc="store.error" tone="error">
        <v-btn variant="outlined" @click="store.fetchList()">重新加载</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'empty'" icon="history" title="还没有操作日志" desc="审核、发布、停用等操作完成后会在这里留痕。" />
      <zw-empty v-else-if="store.status === 'filtered_empty'" icon="search_off" title="没有符合条件的日志" desc="换一个时间范围或关键词试试。">
        <v-btn variant="text" color="primary" @click="store.resetFilters()">查看全部</v-btn>
      </zw-empty>
      <template v-else>
        <v-data-table :headers="headers" :items="store.list" item-key="log_id" hide-default-footer density="compact" class="log-table">
          <template #item.created_at="{ item }">
            <span class="num">{{ formatDateTime(item.created_at) }}</span>
          </template>
          <template #item.operator_name="{ item }">
            <div class="zw-row">
              <span>{{ item.operator_name }}</span>
              <zw-tag v-if="item.operator_role_text" type="neutral" size="small">{{ item.operator_role_text }}</zw-tag>
            </div>
          </template>
          <template #item.op_type_text="{ item }">
            <zw-tag type="outline" size="small">{{ item.op_type_text }}</zw-tag>
          </template>
          <template #item.op_action_text="{ item }">
            <zw-tag :type="actionTone(item)" size="small">{{ item.op_action_text }}</zw-tag>
          </template>
          <template #item.target_name="{ item }">
            <span class="log-target" :title="item.target_name">{{ item.target_name || '—' }}</span>
          </template>
          <template #item.reason="{ item }">
            <span class="log-reason" :title="item.reason">{{ item.reason || '—' }}</span>
          </template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a class="zw-link" @click="openDetail(item)">详情</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot">
          <span class="zw-muted">导出的日志中手机号同样隐藏中间 4 位</span>
          <zw-pager :total="store.total" :page="store.query.page" :size="store.query.size" @update:page="changePage" @update:size="changeSize" />
        </div>
      </template>
    </zw-card>

    <zw-drawer v-model="store.drawer" title="操作日志详情" width="640">
      <div v-if="store.detailLoading" class="zw-stack">
        <v-skeleton-loader type="paragraph@4" />
      </div>
      <template v-else-if="store.detail">
        <div class="log-detail-head">
          <div>
            <div class="title-s">{{ store.detail.target_name || selected?.target_name || '—' }}</div>
            <div class="zw-muted">{{ selected ? formatDateTime(selected.created_at) : '' }}</div>
          </div>
          <zw-tag :type="store.detail.result === 'FAIL' ? 'error' : 'success'" size="small">{{ store.detail.result_text || (store.detail.result === 'FAIL' ? '失败' : '成功') }}</zw-tag>
        </div>

        <zw-desc :cols="2" class="log-desc">
          <zw-desc-item label="操作人">{{ store.detail.operator?.name || '—' }}<span v-if="store.detail.operator?.role_text" class="zw-muted">（{{ store.detail.operator.role_text }}）</span></zw-desc-item>
          <zw-desc-item label="登录账号">{{ store.detail.operator?.username || '—' }}</zw-desc-item>
          <zw-desc-item label="类型">{{ store.detail.op_type_text || '—' }}</zw-desc-item>
          <zw-desc-item label="操作">{{ store.detail.op_action_text || '—' }}</zw-desc-item>
          <zw-desc-item label="终端">{{ store.detail.client?.client_type_text || '—' }}<span v-if="store.detail.client?.browser" class="zw-muted"> · {{ store.detail.client.browser }}</span></zw-desc-item>
          <zw-desc-item label="IP 属地">{{ store.detail.client?.ip_region || '—' }}</zw-desc-item>
          <zw-desc-item label="原因 / 说明" :span="2">{{ store.detail.reason || '—' }}</zw-desc-item>
          <zw-desc-item v-if="store.detail.extra_text" label="补充" :span="2">{{ store.detail.extra_text }}</zw-desc-item>
        </zw-desc>

        <template v-if="changeRows.length">
          <div class="zw-section-title">变更内容</div>
          <zw-compare :rows="changeRows" before-title="修改前" after-title="修改后" />
        </template>

        <template v-if="relatedItems.length">
          <div class="zw-section-title">同一对象的其他记录</div>
          <zw-timeline :items="relatedItems" />
        </template>
      </template>
      <zw-empty v-else icon="error" title="详情加载失败" :desc="store.error">
        <v-btn variant="outlined" @click="selected && store.openDetail(selected.log_id)">重新加载</v-btn>
      </zw-empty>

      <template #foot>
        <v-btn variant="outlined" @click="store.drawer = false">关闭</v-btn>
        <v-btn v-if="recordTarget" color="primary" variant="outlined" @click="openRecord">
          打开对应记录
        </v-btn>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.log-filters {
  margin-bottom: 16px;
}
.log-filters :deep(.v-input) {
  flex: 0 0 auto;
}
.log-target,
.log-reason {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-target {
  max-width: 230px;
}
.log-reason {
  max-width: 300px;
}
.log-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line-1);
  margin-bottom: 16px;
}
.log-desc {
  margin-bottom: 20px;
}
.log-table :deep(.v-table__wrapper) {
  max-height: none;
}
</style>
