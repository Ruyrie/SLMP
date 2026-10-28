<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { useAdminFarmsStore } from '@/stores/system/farms'
import type { AdminFarmItem } from '@/api/system'
import { fmtApproved, fmtDateTime, fmtFull, fmtShortDate } from '@/views/system/shared'
import AuditImageViewer from '@/views/audit/components/AuditImageViewer.vue'

const route = useRoute()
const router = useRouter()
const admin = useAdminStore()
const store = useAdminFarmsStore()

const regionName = ref('')
const categoryName = ref('')
const viewerOpen = ref(false)
const viewerIndex = ref(0)

const canHandle = computed(() => admin.hasPerm('WA-S04:HANDLE'))
const detail = computed(() => store.detail)
const target = computed(() => store.target)

const headers = [
  { title: '养殖场', key: 'name', width: 176, sortable: false },
  { title: '所有者', key: 'owner', width: 160, sortable: false },
  { title: '地区', key: 'region', width: 136, sortable: false },
  { title: '成员', key: 'members', width: 64, align: 'end' as const, sortable: false },
  { title: '存栏', key: 'stock', width: 100, align: 'end' as const, sortable: false },
  { title: '审核通过', key: 'approved', width: 120, sortable: false },
  { title: '近 7 天登记', key: 'records', width: 96, align: 'end' as const, sortable: false },
  { title: '状态', key: 'status', sortable: false },
  { title: '操作', key: 'actions', width: 168, align: 'end' as const, sortable: false }
]

const statusSegs = computed(() => [
  { label: '全部', value: 'ALL', count: store.tabCounts.all ?? store.total },
  { label: '正常', value: 'NORMAL', count: store.tabCounts.normal ?? 0 },
  { label: '已停用', value: 'DISABLED', count: store.tabCounts.disabled ?? 0 },
  { label: '已冻结', value: 'FROZEN', count: store.tabCounts.frozen ?? 0 },
  { label: '待审核', value: 'PENDING', count: store.tabCounts.pending ?? 0 },
  { label: '已驳回', value: 'REJECTED', count: store.tabCounts.rejected ?? 0 }
])

const emptyTitle = computed(() => {
  if (store.hasFilter || store.query.status !== 'ALL') return '没有符合条件的养殖场'
  return '还没有养殖场'
})

const detailActions = computed(() => {
  const row = store.list.find((item) => item.farm_id === detail.value?.farm_id)
  return row?.actions || target.value?.actions || []
})

const detailRow = computed(() => store.list.find((item) => item.farm_id === detail.value?.farm_id) || null)

const detailMembers = computed(() => detail.value?.members || [])
const qualImages = computed(() => detail.value?.info?.qual_images || [])
const memberCount = computed(() => detail.value?.summary?.member_total ?? target.value?.member_count ?? 0)

const viewerItems = computed(() =>
  qualImages.value.map((image, index) => ({
    url: image.url || image.thumb_url,
    thumb_url: image.thumb_url || image.url,
    label: `资质图 ${index + 1}`,
    meta: detail.value?.name || ''
  }))
)

const records7d = (item: AdminFarmItem): number | undefined => item.records_7d ?? item.records7d

function statusTone(status: string): string {
  if (status === 'NORMAL') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'DISABLED' || status === 'FROZEN' || status === 'REJECTED') return 'error'
  return 'neutral'
}

function statusIcon(status: string): string | undefined {
  if (status === 'DISABLED') return 'block'
  if (status === 'FROZEN') return 'ac_unit'
  if (status === 'PENDING') return 'schedule'
  if (status === 'REJECTED') return 'block'
  return undefined
}

function tagTone(tag: string): string {
  if (tag.includes('通过') || tag.includes('正常')) return 'success'
  if (tag.includes('审核') || tag.includes('待')) return 'warning'
  if (tag.includes('冻结') || tag.includes('停用') || tag.includes('驳回')) return 'error'
  return 'neutral'
}

function hasAction(item: AdminFarmItem, action: string): boolean {
  return (item.actions || []).includes(action)
}

function canShowAction(item: AdminFarmItem, action: string): boolean {
  if (!hasAction(item, action)) return false
  if (action === 'FREEZE' || action === 'UNFREEZE') return canHandle.value
  if (action === 'GO_AUDIT') return admin.hasPerm('WA-R01:VIEW')
  if (action === 'VIEW_CHANGE') return admin.hasPerm('WA-R02:VIEW')
  return true
}

function openAudit(item: AdminFarmItem): void {
  void store.openDetail(item)
}

function goAudit(item: AdminFarmItem): void {
  void router.push({ name: 'WA-R01', query: item.pending_audit_id ? { audit_id: String(item.pending_audit_id) } : {} })
}

function goChange(item: AdminFarmItem): void {
  void router.push({ name: 'WA-R02', query: item.pending_change_id ? { change_id: String(item.pending_change_id) } : {} })
}

function openDetailAction(): void {
  if (detail.value?.pending_change_id) {
    void router.push({ name: 'WA-R02', query: { change_id: String(detail.value.pending_change_id) } })
  }
}

function openFreezeDetail(): void {
  const row = detailRow.value || target.value
  if (row) store.openConfirm('freeze', row)
}

function openUnfreezeDetail(): void {
  const row = detailRow.value || target.value
  if (row) store.openConfirm('unfreeze', row)
}

function openViewer(index = 0): void {
  viewerIndex.value = index
  viewerOpen.value = true
}

function clearProvince(): void {
  store.query.province_code = ''
  regionName.value = ''
  store.applyFilters()
}

function clearCategory(): void {
  store.query.category_id = null
  categoryName.value = ''
  store.applyFilters()
}

function resetFilters(): void {
  regionName.value = ''
  categoryName.value = ''
  store.resetFilters()
}

onMounted(() => {
  const keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  const province = typeof route.query.province_code === 'string' ? route.query.province_code : ''
  const region = typeof route.query.region_name === 'string' ? route.query.region_name : ''
  const category = Number(route.query.category_id || 0)
  const categoryLabel = typeof route.query.category_name === 'string' ? route.query.category_name : ''
  store.query.status = 'ALL'
  store.query.keyword = keyword
  store.query.province_code = province
  store.query.category_id = category > 0 ? category : null
  regionName.value = region
  categoryName.value = categoryLabel
  void store.fetchList()
  if (route.query.id) void store.openDetailById(Number(route.query.id))
})
</script>

<template>
  <zw-shell side="admin" active="WA-S04" :crumbs="['用户与系统', '养殖场管理']">
    <zw-page-head
      title="养殖场管理"
      desc="平台全部养殖场。管理端只显示存栏、成员和活跃度等汇总数据。冻结和解冻都必须填写原因，原因发到该养殖场全部成员的消息中心。"
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

    <div class="zw-toolbar farm-status-bar">
      <div class="zw-toolbar-l">
        <zw-seg :model-value="store.query.status" :items="statusSegs" @update:model-value="store.setStatus(String($event))" />
      </div>
    </div>

    <div class="zw-toolbar">
      <div class="zw-toolbar-l">
        <v-text-field
          v-model="store.query.keyword"
          class="zw-w-320"
          placeholder="搜索养殖场名称、所有者姓名或手机号"
          prepend-inner-icon="search"
          maxlength="20"
          @keydown.enter="store.applyFilters()"
        />
        <zw-tag v-if="store.query.province_code" type="primary" icon="location_on">
          {{ regionName || `省份 ${store.query.province_code}` }}
          <button class="chip-close" type="button" aria-label="清除省份筛选" @click="clearProvince"><zw-icon name="close" :size="14" /></button>
        </zw-tag>
        <zw-tag v-if="store.query.category_id" type="primary" icon="category">
          {{ categoryName || `品类 ${store.query.category_id}` }}
          <button class="chip-close" type="button" aria-label="清除品类筛选" @click="clearCategory"><zw-icon name="close" :size="14" /></button>
        </zw-tag>
        <v-btn variant="text" color="primary" @click="store.applyFilters()">查询</v-btn>
        <v-btn variant="text" @click="resetFilters">重置</v-btn>
      </div>
      <div class="zw-toolbar-r">
        <span class="zw-faint body-s">近 7 天登记指昨天往前 7 个自然日的生产登记条数</span>
      </div>
    </div>

    <zw-card flush>
      <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@8" />
      <zw-empty v-else-if="store.status === 'forbidden'" icon="lock" title="你没有养殖场管理的查看权限" :desc="store.error">
        <v-btn variant="outlined" @click="$router.push({ name: 'WA-S02' })">返回平台概览</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'error'" icon="cloud_off" title="养殖场列表加载失败" :desc="store.error" tone="error">
        <v-btn variant="outlined" @click="store.fetchList()">重新加载</v-btn>
      </zw-empty>
      <zw-empty v-else-if="store.status === 'empty'" icon="domain" :title="emptyTitle" desc="养殖场注册后会出现在这里。" />
      <zw-empty v-else-if="store.status === 'filtered_empty'" icon="search_off" title="没有符合条件的养殖场" desc="换一个筛选条件试试。">
        <v-btn variant="text" color="primary" @click="resetFilters">查看全部</v-btn>
      </zw-empty>
      <template v-else>
        <v-data-table :headers="headers" :items="store.list" item-key="farm_id" hide-default-footer density="compact" class="farm-table">
          <template #item.name="{ item }">
            <a class="zw-link zw-cell-main" @click="openAudit(item as AdminFarmItem)">{{ item.name }}</a>
            <span class="zw-muted farm-category">{{ item.category_name || '—' }}</span>
          </template>
          <template #item.owner="{ item }">
            <span>{{ item.owner_name }}</span>
            <span class="zw-muted num owner-phone">{{ item.owner_phone }}</span>
          </template>
          <template #item.region="{ item }">{{ item.region_text || '—' }}</template>
          <template #item.members="{ item }"><span class="num">{{ item.member_count ?? '—' }}</span></template>
          <template #item.stock="{ item }">
            <span v-if="item.stock_count === null || item.stock_count === undefined" class="zw-faint">—</span>
            <span v-else class="num">{{ item.stock_count }} {{ item.stock_unit }}</span>
          </template>
          <template #item.approved="{ item }"><span class="num">{{ fmtApproved(item.approved_at) }}</span></template>
          <template #item.records="{ item }">
            <span v-if="records7d(item as AdminFarmItem) === null || records7d(item as AdminFarmItem) === undefined" class="zw-faint">—</span>
            <span v-else class="num">{{ records7d(item as AdminFarmItem) }} 条</span>
          </template>
          <template #item.status="{ item }">
            <span class="farm-status">
              <zw-tag :type="statusTone(item.farm_status)" :icon="statusIcon(item.farm_status)">{{ item.farm_status_text }}</zw-tag>
              <zw-tag v-if="item.change_pending" type="warning" size="small">资料变更待审核</zw-tag>
            </span>
          </template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a @click="openAudit(item as AdminFarmItem)">详情</a>
              <a v-if="canShowAction(item as AdminFarmItem, 'GO_AUDIT')" @click="goAudit(item as AdminFarmItem)">去审核</a>
              <a v-if="canShowAction(item as AdminFarmItem, 'VIEW_CHANGE')" @click="goChange(item as AdminFarmItem)">查看变更</a>
              <a v-if="canShowAction(item as AdminFarmItem, 'UNFREEZE')" @click="store.openConfirm('unfreeze', item as AdminFarmItem)">解冻</a>
              <a v-if="canShowAction(item as AdminFarmItem, 'FREEZE')" class="danger" @click="store.openConfirm('freeze', item as AdminFarmItem)">冻结</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot farm-foot">
          <span class="zw-faint body-s">“已停用”由所有者在养殖场信息页操作，管理端只查看</span>
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

    <zw-drawer v-model="store.drawer" title="养殖场详情" :sub="detail ? `${detail.name}${detail.info?.created_at ? ` · ${fmtShortDate(detail.info.created_at)}创建` : ''}` : ''" width="760">
      <div v-if="store.detailLoading" class="zw-stack">
        <v-skeleton-loader type="paragraph@4" />
        <v-skeleton-loader type="table@3" />
      </div>
      <zw-empty v-else-if="store.detailError" icon="error" title="养殖场详情加载失败" :desc="store.detailError" tone="error">
        <v-btn variant="outlined" @click="store.openDetailById(store.activeId)">重新加载</v-btn>
      </zw-empty>
      <template v-else-if="detail">
        <div class="farm-head">
          <span class="zw-ico-tile zw-ico-tile--lg zw-ico-tile--primary"><zw-icon name="domain" :size="22" /></span>
          <div>
            <div class="farm-head-name">
              {{ detail.name }}
              <zw-tag v-for="tag in detail.tags || []" :key="tag" :type="tagTone(tag)" size="small">{{ tag }}</zw-tag>
            </div>
            <div class="farm-head-sub">
              {{ detail.category_name || '—' }} · 所有者 {{ detail.owner?.name || '—' }}
              <span class="num">{{ detail.owner?.phone || '' }}</span> · {{ detail.address || '—' }}
            </div>
          </div>
        </div>

        <zw-notice v-if="detail.frozen" type="error" icon="ac_unit" :title="`已冻结（${fmtFull(detail.frozen.frozen_at)} · ${detail.frozen.frozen_by_name || '平台'}）`">
          {{ detail.frozen.frozen_reason || '原因见操作日志' }}
        </zw-notice>

        <section class="farm-sec">
          <div class="zw-section-title">
            汇总数据
            <span class="zw-faint body-s normal-weight">不显示单笔账目和凭证</span>
          </div>
          <div class="mini-stats">
            <div>
              <div class="mini-k">存栏</div>
              <div class="mini-v num">{{ detail.summary?.stock_count ?? '—' }}<small>{{ detail.summary?.stock_unit || '' }}</small></div>
            </div>
            <div>
              <div class="mini-k">在养批次</div>
              <div class="mini-v num">{{ detail.summary?.active_batch_count ?? '—' }}<small>个</small></div>
            </div>
            <div>
              <div class="mini-k">近 7 天生产登记</div>
              <div class="mini-v num">{{ detail.summary?.records_7d ?? detail.summary?.records7d ?? '—' }}<small>条</small></div>
            </div>
            <div>
              <div class="mini-k">近 7 天活跃成员</div>
              <div class="mini-v num">
                {{ detail.summary?.active_members_7d ?? detail.summary?.active_members7d ?? '—' }}<small>/ {{ detail.summary?.member_total ?? '—' }} 人</small>
              </div>
            </div>
          </div>
        </section>

        <section class="farm-sec">
          <div class="zw-section-title">注册信息</div>
          <zw-desc :cols="3" bordered :label-width="88">
            <zw-desc-item label="主要品类">{{ detail.category_name || '—' }}</zw-desc-item>
            <zw-desc-item label="规模">{{ detail.info?.scale_text || '—' }}</zw-desc-item>
            <zw-desc-item label="资质图">
              <a v-if="qualImages.length" class="zw-link" @click="openViewer(0)">{{ qualImages.length }} 张</a>
              <span v-else class="zw-faint">0 张</span>
            </zw-desc-item>
            <zw-desc-item label="联系人">{{ detail.info?.contact_name || '—' }}</zw-desc-item>
            <zw-desc-item label="联系电话"><span class="num">{{ detail.info?.contact_phone || '—' }}</span></zw-desc-item>
            <zw-desc-item label="创建">{{ fmtShortDate(detail.info?.created_at) }}</zw-desc-item>
            <zw-desc-item label="地址" :span="3">{{ detail.address || '—' }}</zw-desc-item>
          </zw-desc>
        </section>

        <section class="farm-sec">
          <div class="zw-section-title">
            成员 · {{ detailMembers.length }} 人
            <span v-if="detail.invites" class="zw-faint body-s normal-weight">
              另有邀请 {{ (detail.invites.pending || 0) + (detail.invites.expired || 0) }} 条（待接受 {{ detail.invites.pending || 0 }}、已过期 {{ detail.invites.expired || 0 }}）
            </span>
          </div>
          <table v-if="detailMembers.length" class="mini-table">
            <thead><tr><th>成员</th><th>角色</th><th>负责范围</th><th>加入</th><th>最近登录</th></tr></thead>
            <tbody>
              <tr v-for="member in detailMembers" :key="member.user_id">
                <td>
                  <div class="member-cell">
                    <zw-avatar :name="member.name" :src="member.avatar_url" :size="24" />
                    {{ member.name }}
                    <span class="zw-muted num">{{ member.phone }}</span>
                  </div>
                </td>
                <td><zw-tag :type="member.role === 'OWNER' ? 'primary' : 'neutral'" size="small">{{ member.role_text }}</zw-tag></td>
                <td>{{ member.pen_text || '全部栏舍' }}</td>
                <td class="num">{{ fmtShortDate(member.joined_at) }}</td>
                <td class="num">{{ member.last_login_at ? fmtDateTime(member.last_login_at) : '—' }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="zw-faint">还没有成员</div>
        </section>

        <section class="farm-sec">
          <div class="zw-section-title">平台记录</div>
          <zw-timeline v-if="detail.timeline?.length" :items="detail.timeline.map((item) => ({ title: item.actor ? `${item.event} · ${item.actor}` : item.event, time: fmtFull(item.time), desc: item.desc, tag: item.tag, tagType: item.tag?.includes('驳回') ? 'error' : item.tag?.includes('通过') ? 'success' : 'warning', tone: item.tag?.includes('驳回') ? 'error' : item.tag?.includes('通过') ? 'success' : 'warning', icon: item.tag?.includes('驳回') ? 'block' : item.tag?.includes('通过') ? 'check' : 'history' }))" />
          <div v-else class="zw-faint">暂无平台记录</div>
        </section>
      </template>

      <template #foot>
        <template v-if="detail">
          <span class="zw-foot-note">冻结后全部成员暂时不能使用，数据保留</span>
          <v-btn v-if="detail.pending_change_id && admin.hasPerm('WA-R02:VIEW')" variant="outlined" @click="openDetailAction">查看资料变更</v-btn>
          <template v-if="canHandle">
            <v-btn
              v-if="detail.frozen || detailActions.includes('UNFREEZE')"
              color="primary"
              prepend-icon="check_circle"
              @click="openUnfreezeDetail"
            >
              解冻养殖场
            </v-btn>
            <v-btn
              v-else-if="detailActions.includes('FREEZE')"
              variant="outlined"
              color="error"
              prepend-icon="ac_unit"
              @click="openFreezeDetail"
            >
              冻结养殖场
            </v-btn>
          </template>
        </template>
        <v-btn variant="outlined" @click="store.drawer = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog
      v-model="store.confirmOpen"
      :title="store.action === 'freeze' ? `冻结${target?.name || '该养殖场'}？` : `解冻${target?.name || '该养殖场'}？`"
      width="560"
      :icon="store.action === 'freeze' ? 'ac_unit' : 'check_circle'"
      :tone="store.action === 'freeze' ? 'error' : 'success'"
    >
      <template v-if="store.action === 'freeze'">
        <p class="dialog-p">冻结后：</p>
        <ul class="effect-list zw-muted">
          <li>{{ target?.name }}的 {{ memberCount }} 名成员暂时不能使用：切换养殖场时显示“已被平台冻结，暂时不能使用”</li>
          <li>数据全部保留，停止生成待办和提醒；解冻后恢复</li>
          <li>冻结原因以站内消息发到全部成员（含所有者{{ target?.owner_name ? ` ${target.owner_name}` : '' }}）的消息中心；不发短信</li>
        </ul>
        <zw-form-item label="冻结原因" required :error="store.reasonError" class="dialog-form">
          <v-textarea
            v-model="store.reason"
            placeholder="请填写冻结原因，最多 200 字"
            maxlength="200"
            counter
            persistent-counter
            rows="3"
            :error="!!store.reasonError"
          />
        </zw-form-item>
      </template>
      <template v-else>
        <p class="dialog-p">解冻后，{{ target?.name }}的全部成员可以继续使用，待办和提醒恢复生成。</p>
        <zw-form-item label="解冻原因" required :error="store.reasonError" class="dialog-form">
          <v-textarea
            v-model="store.reason"
            placeholder="请填写解冻原因，最多 200 字"
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
          :color="store.action === 'freeze' ? 'error' : 'primary'"
          :loading="store.submitting"
          @click="store.confirm()"
        >
          {{ store.action === 'freeze' ? '冻结' : '解冻' }}
        </v-btn>
      </template>
    </zw-dialog>

    <AuditImageViewer v-model="viewerOpen" :items="viewerItems" :initial-index="viewerIndex" title="资质图" />
  </zw-shell>
</template>

<style scoped>
.farm-status-bar {
  margin-bottom: 12px;
}
.farm-table :deep(td),
.farm-table :deep(th) {
  white-space: nowrap;
}
.farm-category {
  margin-left: 8px;
}
.owner-phone {
  margin-left: 8px;
}
.farm-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.farm-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.chip-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  margin-left: 4px;
}
.farm-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 20px;
}
.farm-head-name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
}
.farm-head-sub {
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.farm-sec {
  margin-top: 24px;
}
.normal-weight {
  font-weight: 400;
}
.mini-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
}
.mini-stats > div {
  padding: 12px 16px;
}
.mini-stats > div + div {
  border-left: 1px solid var(--line-1);
}
.mini-k {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.mini-v {
  margin-top: 2px;
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
}
.mini-v small {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-2);
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
.member-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dialog-p {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}
.effect-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 22px;
}
.dialog-form {
  margin-top: 14px;
}
.notify-check {
  margin-top: 4px;
}
</style>
