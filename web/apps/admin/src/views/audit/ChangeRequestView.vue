<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { useChangeRequestStore } from '@/stores/audit/changeRequest'
import { useAuditPendingStore } from '@/stores/audit/pending'
import type { ChangeRequestItem } from '@/api/audit'
import { fmtDateTime, fmtFull, statusIcon, statusTone } from '@/views/audit/shared'

const route = useRoute()
const admin = useAdminStore()
const store = useChangeRequestStore()
const pending = useAuditPendingStore()

const canHandle = computed(() => admin.hasPerm('WA-R03:HANDLE'))
const detail = computed(() => store.detail)

const headers = [
  { title: '记录与修改项', key: 'record', width: 256, sortable: false },
  { title: '养殖场', key: 'farm_name', width: 104, sortable: false },
  { title: '来源', key: 'source', width: 92, sortable: false },
  { title: '修改前 → 修改后', key: 'change', width: 204, sortable: false },
  { title: '影响', key: 'impact', width: 176, sortable: false },
  { title: '提交人', key: 'submitter', width: 84, sortable: false },
  { title: '提交时间', key: 'submitted_at', width: 124, sortable: false },
  { title: '操作', key: 'actions', width: 60, align: 'end' as const, sortable: false }
]

const sourceModules = [
  { title: '全部来源', value: '' },
  { title: '栏舍存栏（转栏、死淘、入栏）', value: 'PEN_STOCK' },
  { title: '繁育（产仔、出雏）', value: 'BREEDING' },
  { title: '饲喂称重（投喂、称重）', value: 'FEEDING' },
  { title: '巡检（超过 3 天）', value: 'INSPECTION' },
  { title: '免疫用药消毒', value: 'HEALTH' },
  { title: '物资库存（入库、盘点、报损报废）', value: 'INVENTORY' },
  { title: '出栏单', value: 'SALE' }
]

const impacts = [
  { title: '全部影响', value: '' },
  { title: '存栏', value: 'STOCK' },
  { title: '成本', value: 'COST' },
  { title: '库存', value: 'INVENTORY' },
  { title: '可出栏日期', value: 'SELLABLE_DATE' },
  { title: '收入', value: 'INCOME' },
  { title: '统计', value: 'STATS' }
]

const emptyTitle = computed(() => {
  if (store.query.keyword || store.query.source_module || store.query.impact) return '没有符合条件的申请'
  if (store.tab === 'APPROVED') return '没有已通过的修改申请'
  if (store.tab === 'REJECTED') return '没有已驳回的修改申请'
  return '没有待审核的修改申请'
})

const drawerTitle = computed(() => (detail.value ? detail.value.header.title : '修改申请审核'))
const drawerSub = computed(() => {
  if (!detail.value) return ''
  const header = detail.value.header
  return `${header.farm_name} · ${header.submitter_name}（${header.submitter_role_text || '提交人'}）${fmtDateTime(header.submitted_at)} 提交 · 审核期间原记录照常有效`
})

const compareRows = computed(() => {
  const rows = (detail.value?.compare || []).map((row) => ({
    label: row.field,
    before: row.before || '—',
    after: row.after ?? row.before ?? '—',
    changed: row.changed ?? (row.after !== undefined && row.after !== null && row.after !== row.before)
  }))
  if (detail.value && (detail.value.change_type === 'DELETE' || detail.value.change_type === 'VOID')) {
    const label = detail.value.change_type === 'DELETE' ? '已删除' : '已作废'
    if (!rows.some((row) => row.label.includes('状态'))) {
      rows.unshift({ label: '记录状态', before: '有效', after: label, changed: true })
    }
  }
  return rows
})

/** 驳回确认框只列真正改了的字段 */
const changedSummary = computed(() =>
  compareRows.value
    .filter((row) => row.changed)
    .map((row) => `${row.label} ${row.before} → ${row.after}`)
    .join('；')
)

const actionLabel = computed(() => {
  if (detail.value?.change_type === 'DELETE') return '通过并删除记录'
  if (detail.value?.change_type === 'VOID') return '通过并作废'
  return '通过并重新计算'
})

const validationType = computed(() => {
  const level = detail.value?.validation?.level
  if (level === 'BLOCK') return 'error'
  if (level === 'WARN') return 'warning'
  return 'success'
})

const validationIcon = computed(() => {
  const level = detail.value?.validation?.level
  if (level === 'BLOCK') return 'error'
  if (level === 'WARN') return 'warning'
  return 'check_circle'
})

const impactRows = computed(() => detail.value?.impact?.rows || [])
const recalcTags = computed(() => detail.value?.impact?.recalc_tags || [])

const recordHistory = computed(() =>
  (detail.value?.record_history || []).map((item) => ({
    title: `${item.field_name} ${item.before_display} → ${item.after_display}`,
    time: fmtFull(item.reviewed_at),
    desc: [item.reviewer_name ? `${item.reviewer_name} 审核` : '', item.recalc_summary || ''].filter(Boolean).join(' · '),
    tone: item.status_text === '已驳回' ? 'error' : 'success',
    icon: item.status_text === '已驳回' ? 'block' : 'check',
    tag: item.status_text,
    tagType: item.status_text === '已驳回' ? 'error' : 'success'
  }))
)

function refreshAll(): void {
  void store.fetchList()
  void pending.refresh()
}

function openRow(row: ChangeRequestItem): void {
  void store.openDetail(row)
}

function changeTone(item: ChangeRequestItem): string {
  if (item.change_type === 'DELETE' || item.change_type === 'VOID') return 'error'
  return 'neutral'
}

function impactTone(warn?: boolean): string {
  return warn ? 'warning' : 'neutral'
}

function reviewType(result?: string): string {
  return result === 'APPROVED' ? 'success' : 'error'
}

onMounted(() => {
  const id = Number(route.query.id || 0)
  void store.fetchList()
  void pending.refresh()
  if (id) void store.openDetailById(id)
})
</script>

<template>
  <zw-shell side="admin" active="WA-R03" :crumbs="['审核中心', '修改申请审核']">
    <zw-page-head
      title="修改申请审核"
      desc="会影响存栏、成本、库存、可出栏日期或统计结果的记录，养殖户在用户端提交修改申请。审核期间原记录照常有效，通过后系统按申请修改并重新计算相关数据。"
    >
      <template #actions>
        <v-btn variant="outlined" prepend-icon="refresh" :loading="store.status === 'loading'" @click="refreshAll">刷新</v-btn>
      </template>
      <v-tabs :model-value="store.tab" class="zw-tabs-line" @update:model-value="store.setTab(String($event))">
        <v-tab value="PENDING">待审核 {{ store.tabCounts.PENDING || 0 }}</v-tab>
        <v-tab value="APPROVED">已通过</v-tab>
        <v-tab value="REJECTED">已驳回</v-tab>
      </v-tabs>
    </zw-page-head>

    <div class="zw-toolbar">
      <div class="zw-toolbar-l">
        <v-text-field
          v-model="store.query.keyword"
          class="zw-w-280"
          placeholder="搜索养殖场、批次号或提交人"
          prepend-inner-icon="search"
          maxlength="30"
          @keydown.enter="store.search()"
        />
        <v-select
          v-model="store.query.source_module"
          :items="sourceModules"
          class="zw-w-160"
          @update:model-value="store.search()"
        />
        <v-select v-model="store.query.impact" :items="impacts" class="zw-w-140" @update:model-value="store.search()" />
        <v-btn variant="text" color="primary" @click="store.search()">查询</v-btn>
        <v-btn variant="text" @click="store.resetFilters()">重置</v-btn>
      </div>
      <div class="zw-toolbar-r">
        <zw-tag v-if="store.sellableDateCount" type="warning" size="small" icon="event">影响可出栏日期 {{ store.sellableDateCount }} 项</zw-tag>
        <span class="zw-faint body-s">按提交时间排序，先提交的在前</span>
      </div>
    </div>

    <zw-card flush>
      <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@8" />
      <zw-empty v-else-if="store.status === 'forbidden'" icon="lock" title="你没有审核中心的查看权限" desc="审核中心只对超级管理员和审核员开放。如需处理，请联系超级管理员。">
        <v-btn variant="outlined" @click="$router.push({ name: 'WA-S02' })">返回平台概览</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'error'" icon="cloud_off" title="申请列表加载失败" :desc="store.error" tone="error">
        <v-btn variant="outlined" @click="store.fetchList()">重新加载</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'empty'" icon="task_alt" :title="emptyTitle" desc="新的申请会出现在这里，并在侧边菜单显示数量。">
        <v-btn v-if="store.tab !== 'PENDING'" variant="text" color="primary" @click="store.setTab('PENDING')">查看待审核</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'filtered_empty'" icon="search_off" title="没有符合条件的申请" desc="换一个筛选条件试试。">
        <v-btn variant="text" color="primary" @click="store.resetFilters()">查看全部</v-btn>
      </zw-empty>
      <template v-else>
        <v-data-table :headers="headers" :items="store.list" item-key="id" hide-default-footer density="compact" class="change-req-table">
          <template #item.record="{ item }">
            <div class="zw-cell-main">
              <a class="zw-link" @click="openRow(item as ChangeRequestItem)">{{ item.title }}</a>
            </div>
            <div class="zw-cell-sub record-locator">{{ item.record_locator || item.request_no }}</div>
          </template>
          <template #item.farm_name="{ item }">{{ item.farm_name }}</template>
          <template #item.source="{ item }">
            <zw-tag type="outline" size="small">{{ item.source_module_text }}</zw-tag>
          </template>
          <template #item.change="{ item }">
            <span v-if="item.change_type === 'DELETE'" class="change-tag">
              <zw-tag type="error" size="small" icon="delete">删除整条记录</zw-tag>
            </span>
            <span v-else-if="item.change_type === 'VOID'" class="change-tag">
              <zw-tag type="error" size="small" icon="block">作废</zw-tag>
            </span>
            <span v-else class="change-diff">
              <span class="old">{{ item.before_display || '—' }}</span>
              <zw-icon name="arrow_forward" :size="16" />
              <span class="new">{{ item.after_display || '—' }}</span>
            </span>
          </template>
          <template #item.impact="{ item }">
            <span class="impact-tags">
              <zw-tag v-for="impact in item.impacts || []" :key="impact.code" :type="impactTone(impact.warn)" size="small">{{ impact.text }}</zw-tag>
            </span>
          </template>
          <template #item.submitter="{ item }">
            <div>{{ item.submitter_name }}</div>
            <div class="zw-cell-sub">{{ item.submitter_role_text }}</div>
          </template>
          <template #item.submitted_at="{ item }">
            <span class="num nowrap">{{ fmtDateTime(item.submitted_at) }}</span>
          </template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a v-if="store.tab === 'PENDING' && canHandle" @click="openRow(item as ChangeRequestItem)">审核</a>
              <a v-else @click="openRow(item as ChangeRequestItem)">详情</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot change-req-foot">
          <span class="zw-faint body-s">同一条记录同时只能有一条待审核的申请</span>
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

    <zw-drawer v-model="store.drawer" :title="drawerTitle" :sub="drawerSub" width="760">
      <div v-if="store.detailLoading" class="zw-stack">
        <v-skeleton-loader type="paragraph@6" />
        <v-skeleton-loader type="table@3" />
      </div>
      <zw-empty v-else-if="store.detailError" icon="error" title="修改申请详情加载失败" :desc="store.detailError" tone="error">
        <v-btn variant="outlined" @click="store.loadDetail(store.activeId)">重新加载</v-btn>
      </zw-empty>
      <template v-else-if="detail">
        <zw-notice v-if="detail.validation" :type="validationType" :icon="validationIcon" :title="detail.validation.level === 'BLOCK' ? '这条申请不能通过' : detail.validation.level === 'WARN' ? '请核对影响后再处理' : '系统校验通过'">
          {{ detail.validation.text }}
          <span v-if="detail.validation.error_code" class="zw-faint">（错误码 {{ detail.validation.error_code }}）</span>
        </zw-notice>

        <section class="req-sec">
          <div class="zw-section-title">原记录</div>
          <zw-desc :cols="3" bordered :label-width="88">
            <zw-desc-item v-for="row in detail.original || []" :key="row.label" :label="row.label" :span="row.span || 1">
              {{ row.value || '—' }}
            </zw-desc-item>
            <zw-desc-item v-if="detail.batch_no" label="批次号" :span="3">
              <span class="num">{{ detail.batch_no }}</span>
            </zw-desc-item>
          </zw-desc>
        </section>

        <section class="req-sec">
          <div class="zw-section-title">修改内容</div>
          <zw-compare :rows="compareRows" />
          <div class="reason-block">
            <div class="reason-k">修改原因</div>
            <div>{{ detail.reason || '提交人没有填写修改原因' }}</div>
          </div>
        </section>

        <section class="req-sec">
          <div class="zw-section-title">
            影响范围
            <span v-if="detail.impact?.note" class="zw-faint body-s normal-weight">{{ detail.impact.note }}</span>
          </div>
          <table v-if="impactRows.length" class="impact-table">
            <thead>
              <tr><th style="width: 36%">项目</th><th>当前</th><th>审核通过后</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in impactRows" :key="row.item">
                <td class="k">{{ row.item }}</td>
                <td class="v">{{ row.current }}</td>
                <td class="v after" :class="{ neg: row.is_loss }">{{ row.after }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-impact">来源模块没有提供逐项影响快照，通过后会按下列项目重新计算。</div>
          <div v-if="recalcTags.length" class="recalc">
            同时重新计算
            <zw-tag v-for="tag in recalcTags" :key="tag" size="small">{{ tag }}</zw-tag>
          </div>
        </section>

        <section v-if="recordHistory.length" class="req-sec">
          <div class="zw-section-title">这条记录的修改记录</div>
          <zw-timeline :items="recordHistory" />
        </section>

        <section v-if="detail.review" class="req-sec">
          <div class="zw-section-title">审核结果</div>
          <zw-desc :cols="2" bordered :label-width="88">
            <zw-desc-item label="审核结果">
              <zw-tag :type="reviewType(detail.review.result)" :icon="detail.review.result === 'APPROVED' ? 'check' : 'block'">
                {{ detail.review.result_text || (detail.review.result === 'APPROVED' ? '已通过' : '已驳回') }}
              </zw-tag>
            </zw-desc-item>
            <zw-desc-item label="审核人">{{ detail.review.reviewer_name || '—' }}</zw-desc-item>
            <zw-desc-item label="审核时间">{{ fmtFull(detail.review.reviewed_at) }}</zw-desc-item>
            <zw-desc-item label="意见 / 原因" :span="2">{{ detail.review.opinion || detail.review.reject_reason || '—' }}</zw-desc-item>
          </zw-desc>
        </section>
      </template>

      <template #foot>
        <template v-if="detail">
          <span class="zw-foot-note">{{ detail.notify_text || '结果发到提交人的消息中心，并显示在“我的修改申请”' }}</span>
          <template v-if="detail.status === 'PENDING'">
            <template v-if="canHandle">
              <v-btn variant="outlined" color="error" prepend-icon="block" @click="store.openReject()">驳回</v-btn>
              <v-btn color="primary" prepend-icon="check" :disabled="store.blocked" @click="store.openApprove()">{{ actionLabel }}</v-btn>
            </template>
            <span v-else class="zw-foot-note">你没有处理权限，仅可查看</span>
          </template>
          <v-btn v-else variant="outlined" @click="store.drawer = false">关闭</v-btn>
        </template>
        <v-btn v-else variant="outlined" @click="store.drawer = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog v-model="store.approveOpen" title="通过这条修改申请？" width="480" icon="check_circle" tone="success">
      <p class="dialog-p">
        通过后系统会按申请修改原记录并重新计算相关数据，结果发到{{ detail?.header.submitter_name }}的消息中心，并显示在“我的修改申请”。
      </p>
      <zw-form-item label="审核意见" required class="dialog-form">
        <v-textarea v-model="store.approveComment" maxlength="200" counter persistent-counter rows="3" />
      </zw-form-item>
      <template #actions>
        <v-btn variant="outlined" @click="store.approveOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" :disabled="store.blocked" @click="store.approve()">通过</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="store.rejectOpen" title="驳回这条修改申请？" width="560" icon="block" tone="error">
      <p class="dialog-p">驳回后原记录保持不变。驳回原因会显示在提交人的“我的修改申请”和消息中心，提交人可以修改后重新提交。</p>
      <zw-desc v-if="detail" :cols="1" bordered :label-width="96" class="reject-summary">
        <zw-desc-item label="申请">{{ detail.header.farm_name }} · {{ detail.header.title }}</zw-desc-item>
        <zw-desc-item label="修改">{{ changedSummary || '—' }}</zw-desc-item>
        <zw-desc-item label="提交人">{{ detail.header.submitter_name }}（{{ detail.header.submitter_role_text }}）· {{ fmtFull(detail.header.submitted_at) }}</zw-desc-item>
      </zw-desc>
      <zw-form-item label="常用原因" optional>
        <div class="reason-chips">
          <button
            v-for="reason in store.rejectTemplates"
            :key="reason"
            type="button"
            class="reason-chip"
            :class="{ on: store.rejectReason === reason }"
            @click="store.useReason(reason)"
          >
            <zw-icon v-if="store.rejectReason === reason" name="check" :size="16" />
            {{ reason }}
          </button>
        </div>
      </zw-form-item>
      <zw-form-item label="驳回原因" required :error="store.rejectError" class="dialog-form">
        <v-textarea
          v-model="store.rejectReason"
          placeholder="请填写驳回原因，最多 200 字"
          maxlength="200"
          counter
          persistent-counter
          rows="3"
          :error="!!store.rejectError"
        />
      </zw-form-item>
      <template #actions>
        <v-btn variant="outlined" @click="store.rejectOpen = false">取消</v-btn>
        <v-btn color="error" :loading="store.submitting" @click="store.reject()">驳回</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.change-req-table :deep(td),
.change-req-table :deep(th) {
  white-space: nowrap;
}
.change-req-table :deep(td) {
  padding-top: 8px !important;
  padding-bottom: 8px !important;
}
.change-req-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.record-locator {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.change-diff {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.change-diff .old {
  color: var(--text-3);
  text-decoration: line-through;
}
.change-diff .new {
  font-weight: 500;
  color: var(--text-1);
}
.change-diff .zw-mso {
  color: var(--text-3);
}
.change-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.impact-tags {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 4px;
}
.nowrap {
  white-space: nowrap;
}
.req-sec {
  margin-top: 24px;
}
.normal-weight {
  font-weight: 400;
}
.reason-block {
  margin-top: 12px;
  padding: 12px 16px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  font-size: 14px;
  line-height: 22px;
}
.reason-k {
  margin-bottom: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.impact-table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--line-1);
  font-size: 14px;
}
.impact-table th {
  padding: 9px 16px;
  background: var(--fill-1);
  color: var(--text-2);
  font-weight: 600;
  text-align: left;
  border-bottom: 1px solid var(--line-1);
}
.impact-table td {
  padding: 9px 16px;
  border-bottom: 1px solid var(--line-1);
}
.impact-table td.k {
  color: var(--text-2);
}
.impact-table td.v {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.impact-table td.after {
  background: var(--primary-subtle);
  font-weight: 500;
}
.impact-table td.neg {
  color: var(--error);
}
.empty-impact {
  padding: 12px 16px;
  border: 1px dashed var(--line-2);
  border-radius: var(--radius-sm);
  color: var(--text-3);
  font-size: 13px;
}
.recalc {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  font-size: 14px;
  color: var(--text-2);
}
.reject-summary {
  margin-bottom: 14px;
}
.dialog-p {
  margin: 0 0 14px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}
.dialog-form {
  margin-top: 14px;
}
.reason-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.reason-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--line-2);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-1);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.reason-chip.on {
  border-color: var(--primary);
  background: var(--primary-subtle);
  color: var(--primary);
}
</style>
