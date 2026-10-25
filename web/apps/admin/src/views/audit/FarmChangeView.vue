<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { useFarmChangeStore } from '@/stores/audit/farmChange'
import { useAuditPendingStore } from '@/stores/audit/pending'
import type { FarmChangeItem } from '@/api/audit'
import { fileSize, fmtDateTime, fmtFull, fmtShortDate, statusIcon, statusTone } from '@/views/audit/shared'
import AuditImageViewer from '@/views/audit/components/AuditImageViewer.vue'
import AuditLocationMap from '@/views/audit/components/AuditLocationMap.vue'
import { locationMarker, type AuditLocationMarker } from '@/views/audit/components/location'

const route = useRoute()
const admin = useAdminStore()
const store = useFarmChangeStore()
const pending = useAuditPendingStore()

const viewerOpen = ref(false)
const viewerIndex = ref(0)
const failedImages = ref<Record<number, boolean>>({})

const canHandle = computed(() => admin.hasPerm('WA-R02:HANDLE'))
const detail = computed(() => store.detail)
const headers = computed(() => [
  { title: '养殖场', key: 'farm_name', width: 140, sortable: false },
  { title: '所有者', key: 'owner_name', width: 170, sortable: false },
  { title: '变更项', key: 'change_items', width: 150, sortable: false },
  { title: '变更说明', key: 'remark', sortable: false },
  { title: '提交时间', key: 'submitted_at', width: 132, sortable: false },
  { title: store.tab === 'PENDING' ? '已等待' : '审核时间', key: 'audit_time', width: 120, sortable: false },
  { title: '状态', key: 'status', width: 84, sortable: false },
  { title: '操作', key: 'actions', width: 64, align: 'end' as const, sortable: false }
])

const changeItems = [
  { title: '全部变更项', value: '' },
  { title: '名称', value: 'NAME' },
  { title: '地址', value: 'ADDRESS' },
  { title: '资质图', value: 'QUALIFICATION' }
]

const drawerTitle = computed(() => (detail.value ? `${detail.value.farm_name} · 资料变更审核` : '资料变更审核'))
const drawerSub = computed(() =>
  detail.value
    ? `${fmtDateTime(detail.value.submitted_at)} 提交 · 所有者 ${detail.value.owner_name} ${detail.value.owner_phone} · 审核期间旧信息照常有效`
    : ''
)

const emptyTitle = computed(() => {
  if (store.query.keyword || store.query.change_item) return '没有符合条件的申请'
  if (store.tab === 'APPROVED') return '没有已通过的资料变更'
  if (store.tab === 'REJECTED') return '没有已驳回的资料变更'
  return '没有待审核的资料变更'
})

const compareRows = computed(() =>
  (detail.value?.compare || []).map((row) => ({
    label: row.field,
    before: row.before || '—',
    after: row.after || '—'
  }))
)
const mapMarkers = computed(() => {
  const locations = detail.value?.locations
  return [
    locationMarker(locations?.current, '当前位置', 'neutral'),
    locationMarker(locations?.applied, '申请位置')
  ].filter((marker): marker is AuditLocationMarker => marker !== null)
})

const imagesCurrent = computed(() => detail.value?.images_current || [])
const imagesAfter = computed(() => detail.value?.images_after || [])

interface ViewerRow {
  url?: string
  thumb_url?: string
  label: string
  tag: string
  meta: string
}

const viewerItems = computed<ViewerRow[]>(() => {
  const list: ViewerRow[] = []
  imagesCurrent.value.forEach((image, index) => {
    list.push({
      url: image.url || image.thumb_url,
      thumb_url: image.thumb_url || image.url,
      label: `当前资质图 ${index + 1}`,
      tag: image.flag === 'REMOVED' ? '已删除' : '当前生效',
      meta: [detail.value?.farm_name, image.uploaded_at ? fmtFull(image.uploaded_at) : '', image.file_size ? fileSize(image.file_size) : '']
        .filter(Boolean)
        .join(' · ')
    })
  })
  imagesAfter.value.forEach((image, index) => {
    list.push({
      url: image.url || image.thumb_url,
      thumb_url: image.thumb_url || image.url,
      label: `变更后资质图 ${index + 1}`,
      tag: image.flag === 'NEW' ? '新增' : '保留',
      meta: [detail.value?.farm_name, image.uploaded_at ? fmtFull(image.uploaded_at) : '', image.file_size ? fileSize(image.file_size) : '']
        .filter(Boolean)
        .join(' · ')
    })
  })
  return list
})

function openCurrent(index: number): void {
  viewerIndex.value = index
  viewerOpen.value = true
}

function openAfter(index: number): void {
  viewerIndex.value = imagesCurrent.value.length + index
  viewerOpen.value = true
}

function refreshAll(): void {
  void store.fetchList()
  void pending.refresh()
}

function openRow(row: FarmChangeItem): void {
  void store.openDetail(row)
}

function itemText(item: FarmChangeItem): string[] {
  if (item.change_items_text?.length) return item.change_items_text
  return (item.change_items || []).map((code) => ({ NAME: '名称', ADDRESS: '地址', QUALIFICATION: '资质图' })[code] || code)
}

function flagTone(flag?: string): string {
  if (flag === 'NEW') return 'primary'
  if (flag === 'REMOVED') return 'error'
  return 'neutral'
}

function flagText(flag?: string): string {
  if (flag === 'NEW') return '新增'
  if (flag === 'REMOVED') return '已删除'
  return '保留'
}

onMounted(() => {
  const id = Number(route.query.change_id || route.query.id || 0)
  void store.fetchList()
  void pending.refresh()
  if (id) void store.openDetailById(id)
})
</script>

<template>
  <zw-shell side="admin" active="WA-R02" :crumbs="['审核中心', '资料变更审核']">
    <zw-page-head
      title="资料变更审核"
      desc="养殖场名称、地址和资质图的修改由所有者提交。审核期间旧信息照常有效，通过后新信息生效；驳回必须填写原因。"
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

    <div class="change-stats">
      <zw-stat
        label="待审核"
        icon="pending_actions"
        :value="store.stats.pending_count"
        unit="条"
        :foot="store.stats.earliest_at ? `最早一条 ${fmtDateTime(store.stats.earliest_at)} 提交` : '当前没有待审核的资料变更'"
        tone="warning"
      />
      <zw-stat
        label="近 30 天通过"
        icon="task_alt"
        :value="store.stats.approved_30d"
        unit="条"
        :foot="store.approvedBreakdown"
      />
      <zw-stat
        label="近 30 天驳回"
        icon="block"
        :value="store.stats.rejected_30d"
        unit="条"
        foot="驳回后所有者可修改并重新提交"
      />
    </div>

    <div class="zw-toolbar">
      <div class="zw-toolbar-l">
        <v-text-field
          v-model="store.query.keyword"
          class="zw-w-320"
          placeholder="搜索养殖场名称、所有者姓名或手机号"
          prepend-inner-icon="search"
          maxlength="30"
          @keydown.enter="store.fetchList()"
        />
        <v-select
          v-model="store.query.change_item"
          :items="changeItems"
          class="zw-w-140"
          @update:model-value="store.fetchList()"
        />
        <v-btn variant="text" color="primary" @click="store.fetchList()">查询</v-btn>
        <v-btn variant="text" @click="store.resetFilters()">重置</v-btn>
      </div>
    </div>

    <zw-card flush>
      <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@6" />
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
        <v-data-table :headers="headers" :items="store.list" item-key="change_id" hide-default-footer density="compact" class="change-table">
          <template #item.farm_name="{ item }">
            <div class="zw-cell-main">
              <a class="zw-link" @click="openRow(item as FarmChangeItem)">{{ item.farm_name }}</a>
            </div>
          </template>
          <template #item.owner_name="{ item }">
            <span>{{ item.owner_name }}</span>
            <span class="zw-muted num owner-phone">{{ item.owner_phone }}</span>
          </template>
          <template #item.change_items="{ item }">
            <span class="change-items">
              <zw-tag v-for="text in itemText(item as FarmChangeItem)" :key="text" type="outline" size="small">{{ text }}</zw-tag>
            </span>
          </template>
          <template #item.remark="{ item }">
            <span class="remark-cell" :title="item.remark || '所有者没有填写变更说明'">{{ item.remark || '所有者没有填写变更说明' }}</span>
          </template>
          <template #item.submitted_at="{ item }">
            <span class="num">{{ fmtDateTime(item.submitted_at) }}</span>
          </template>
          <template #item.audit_time="{ item }">
            <template v-if="store.tab === 'PENDING'">
              <span v-if="item.overdue" class="wait-late"><zw-icon name="schedule" :size="16" />{{ item.wait_text || '—' }}</span>
              <span v-else class="zw-muted">{{ item.wait_text || '—' }}</span>
            </template>
            <span v-else class="num">{{ item.audited_at ? fmtDateTime(item.audited_at) : '—' }}</span>
          </template>
          <template #item.status="{ item }">
            <zw-tag :type="statusTone(item.status)" :icon="statusIcon(item.status)">{{ item.status_text }}</zw-tag>
          </template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a v-if="store.tab === 'PENDING' && canHandle" @click="openRow(item as FarmChangeItem)">审核</a>
              <a v-else @click="openRow(item as FarmChangeItem)">详情</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot change-foot">
          <span class="zw-faint body-s">审核期间，用户端养殖场信息页的“注册信息”显示“待审核”，仍按旧信息使用</span>
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
        <v-skeleton-loader type="image@2" />
      </div>
      <zw-empty v-else-if="store.detailError" icon="error" title="资料变更详情加载失败" :desc="store.detailError" tone="error">
        <v-btn variant="outlined" @click="store.loadDetail(store.activeId)">重新加载</v-btn>
      </zw-empty>
      <template v-else-if="detail">
        <zw-notice type="info" icon="edit_note" title="变更说明">
          {{ detail.remark || '所有者没有填写变更说明' }}
        </zw-notice>

        <section class="change-sec">
          <div class="zw-section-title">变更对比</div>
          <zw-compare :rows="compareRows" before-title="当前生效" after-title="申请变更为" />
        </section>

        <section class="change-sec">
          <div class="zw-section-title">地图位置</div>
          <AuditLocationMap v-if="detail.locations?.current || detail.locations?.applied" :markers="mapMarkers" />
          <div v-else class="no-map">本次没有修改位置</div>
          <div class="map-foot">
            <span v-if="detail.locations?.applied">
              申请位置：北纬 {{ detail.locations.applied.latitude ?? '—' }}°，东经 {{ detail.locations.applied.longitude ?? '—' }}°
              <template v-if="detail.locations.applied.source_text"> · {{ detail.locations.applied.source_text }}</template>
              <template v-if="detail.locations.applied.loc_accuracy"> · 精度约 {{ detail.locations.applied.loc_accuracy }} 米</template>
            </span>
            <span v-else class="zw-faint">只修改名称或资质图，地址与位置不变</span>
            <span v-if="detail.locations?.distance_km">两点相距 {{ detail.locations.distance_km }} 千米</span>
          </div>
        </section>

        <section class="change-sec">
          <div class="zw-section-title">
            资质图
            <span class="zw-faint body-s normal-weight">
              新增 {{ imagesAfter.filter((image) => image.flag === 'NEW').length }} 张，共 {{ imagesAfter.length || imagesCurrent.length }} 张 · 点图片查看大图
            </span>
          </div>
          <div class="photo-row">
            <div class="photo-k">当前 {{ imagesCurrent.length }} 张</div>
            <div class="photo-list">
              <div v-for="(image, index) in imagesCurrent" :key="image.file_id" class="change-photo" @click="openCurrent(index)">
                <img
                  v-if="(image.thumb_url || image.url) && !failedImages[image.file_id]"
                  :src="image.thumb_url || image.url"
                  alt="资质图"
                  @error="failedImages[image.file_id] = true"
                />
                <zw-thumb v-else kind="doc" :w="120" :h="90" />
                <zw-tag v-if="image.flag === 'REMOVED'" class="photo-tag" type="error" size="small">已删除</zw-tag>
              </div>
              <span v-if="!imagesCurrent.length" class="zw-faint">无</span>
            </div>
          </div>
          <div class="photo-row">
            <div class="photo-k">变更后 {{ imagesAfter.length }} 张</div>
            <div class="photo-list">
              <div v-for="(image, index) in imagesAfter" :key="image.file_id" class="change-photo" @click="openAfter(index)">
                <img
                  v-if="(image.thumb_url || image.url) && !failedImages[image.file_id]"
                  :src="image.thumb_url || image.url"
                  alt="资质图"
                  @error="failedImages[image.file_id] = true"
                />
                <zw-thumb v-else kind="doc" :w="120" :h="90" />
                <zw-tag v-if="image.flag === 'NEW'" class="photo-tag" :type="flagTone(image.flag)" size="small">{{ flagText(image.flag) }}</zw-tag>
              </div>
              <span v-if="!imagesAfter.length" class="zw-faint">无</span>
            </div>
          </div>
        </section>

        <section v-if="detail.farm_overview" class="change-sec">
          <div class="zw-section-title">养殖场概况</div>
          <zw-desc :cols="3" bordered :label-width="88">
            <zw-desc-item label="审核通过">{{ fmtShortDate(detail.farm_overview.approved_at) }}</zw-desc-item>
            <zw-desc-item label="主要品类">{{ detail.farm_overview.category_name || '—' }}</zw-desc-item>
            <zw-desc-item label="当前存栏">{{ detail.farm_overview.current_count ?? '—' }} {{ detail.farm_overview.count_unit_text || '' }}</zw-desc-item>
            <zw-desc-item label="成员">{{ detail.farm_overview.member_count ?? '—' }} 人</zw-desc-item>
            <zw-desc-item label="资料变更">第 {{ detail.change_no }} 次</zw-desc-item>
            <zw-desc-item label="状态">{{ detail.farm_overview.farm_status_text || '—' }}</zw-desc-item>
          </zw-desc>
        </section>

        <section v-if="detail.review" class="change-sec">
          <div class="zw-section-title">审核结果</div>
          <zw-desc :cols="2" bordered :label-width="88">
            <zw-desc-item label="审核结果">
              <zw-tag :type="detail.review.result === 'APPROVED' ? 'success' : 'error'" :icon="detail.review.result === 'APPROVED' ? 'check' : 'block'">
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
          <span class="zw-foot-note">结果发到{{ detail.owner_name }}的消息中心；驳回原因显示在养殖场信息页</span>
          <template v-if="detail.status === 'PENDING'">
            <template v-if="canHandle">
              <v-btn variant="outlined" color="error" prepend-icon="block" @click="store.openReject()">驳回</v-btn>
              <v-btn color="primary" prepend-icon="check" @click="store.openApprove()">通过</v-btn>
            </template>
            <span v-else class="zw-foot-note">你没有处理权限，仅可查看</span>
          </template>
          <v-btn v-else variant="outlined" @click="store.drawer = false">关闭</v-btn>
        </template>
        <v-btn v-else variant="outlined" @click="store.drawer = false">关闭</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog
      v-model="store.approveOpen"
      :title="`通过${detail?.farm_name || '该养殖场'}的资料变更？`"
      width="480"
      icon="check_circle"
      tone="success"
    >
      <p class="dialog-p">
        通过后新的名称、地址、经纬度和资质图立即生效，结果发到{{ detail?.owner_name }}的消息中心。
      </p>
      <zw-form-item label="审核意见" required class="dialog-form">
        <v-textarea v-model="store.approveOpinion" maxlength="200" counter persistent-counter rows="3" />
      </zw-form-item>
      <template #actions>
        <v-btn variant="outlined" @click="store.approveOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" @click="store.approve()">通过</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog
      v-model="store.rejectOpen"
      :title="`驳回${detail?.farm_name || '该养殖场'}的资料变更？`"
      width="560"
      icon="block"
      tone="error"
    >
      <p class="dialog-p">
        驳回后{{ detail?.farm_name }}继续使用旧信息。驳回原因会显示在{{ detail?.owner_name }}的“养殖场信息”页和消息中心，修改后可以重新提交。
      </p>
      <zw-desc :cols="1" bordered :label-width="96" class="reject-summary">
        <zw-desc-item label="变更项">{{ (detail?.compare || []).filter((row) => row.changed).map((row) => row.field).join('、') || '—' }}</zw-desc-item>
        <zw-desc-item label="申请时间">{{ fmtFull(detail?.submitted_at) }}</zw-desc-item>
      </zw-desc>
      <zw-form-item label="常用原因" optional>
        <div class="reason-chips">
          <button
            v-for="reason in store.rejectTemplates"
            :key="reason.label"
            type="button"
            class="reason-chip"
            :class="{ on: store.rejectReason.includes(reason.text) }"
            @click="store.useReason(reason.text)"
          >
            <zw-icon v-if="store.rejectReason.includes(reason.text)" name="check" :size="16" />
            {{ reason.label }}
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

    <AuditImageViewer v-model="viewerOpen" :items="viewerItems" :initial-index="viewerIndex" title="资质图" />
  </zw-shell>
</template>

<style scoped>
.change-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.change-table :deep(td),
.change-table :deep(th) {
  white-space: nowrap;
}
.change-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.owner-phone {
  margin-left: 8px;
}
.change-items {
  display: inline-flex;
  gap: 6px;
}
.remark-cell {
  display: block;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-2);
}
.wait-late {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--warning);
}
.change-sec {
  margin-top: 24px;
}
.normal-weight {
  font-weight: 400;
}
.map-foot {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.no-map {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  border: 1px dashed var(--line-2);
  border-radius: var(--radius-sm);
  color: var(--text-3);
  font-size: 13px;
}
.photo-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.photo-row + .photo-row {
  margin-top: 12px;
}
.photo-k {
  width: 72px;
  flex-shrink: 0;
  padding-top: 4px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.photo-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.change-photo {
  position: relative;
  width: 120px;
  height: 90px;
  border: 1px solid var(--line-1);
  border-radius: 2px;
  overflow: hidden;
  cursor: zoom-in;
  background: var(--fill-3);
}
.change-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.photo-tag {
  position: absolute;
  left: 4px;
  bottom: 4px;
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
