<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { useFarmAuditStore } from '@/stores/audit/farmAudit'
import { useAuditPendingStore } from '@/stores/audit/pending'
import type { FarmAuditItem } from '@/api/audit'
import { fileSize, fmtDate, fmtDateTime, fmtFull, fmtShortDate, mapPoint, statusIcon, statusTone } from '@/views/audit/shared'
import AuditImageViewer from '@/views/audit/components/AuditImageViewer.vue'

const route = useRoute()
const admin = useAdminStore()
const store = useFarmAuditStore()
const pending = useAuditPendingStore()

const failedImages = ref<Record<number, boolean>>({})
const viewerOpen = ref(false)
const viewerIndex = ref(0)

const canHandle = computed(() => admin.hasPerm('WA-R01:HANDLE'))
const headers = computed(() => [
  { title: '养殖场', key: 'farm_name', width: 140, sortable: false },
  { title: '所有者', key: 'owner_name', width: 160, sortable: false },
  { title: '品类 · 规模', key: 'category', width: 156, sortable: false },
  { title: '所在地区', key: 'region_text', width: 136, sortable: false },
  { title: '资质图', key: 'image_count', width: 64, align: 'end' as const, sortable: false },
  { title: '提交', key: 'submit_no', width: 88, sortable: false },
  { title: '提交时间', key: 'submitted_at', width: 132, sortable: false },
  { title: store.tab === 'PENDING' ? '已等待' : '审核时间', key: 'audit_time', width: 116, sortable: false },
  { title: '状态', key: 'status', width: 76, sortable: false },
  { title: '操作', key: 'actions', width: 64, align: 'end' as const, sortable: false }
])

const submitTypes = [
  { title: '全部提交', value: '' },
  { title: '首次提交', value: 'FIRST' },
  { title: '重新提交', value: 'RESUBMIT' }
]

const emptyTitle = computed(() => {
  if (store.query.keyword || store.query.submit_type) return '没有符合条件的申请'
  if (store.tab === 'APPROVED') return '没有已通过的注册申请'
  if (store.tab === 'REJECTED') return '没有已驳回的注册申请'
  return '没有待审核的注册申请'
})

const detail = computed(() => store.detail)
const drawerTitle = computed(() => (detail.value ? `${detail.value.info.name} · 注册审核` : '注册审核详情'))
const drawerSub = computed(() =>
  detail.value
    ? `第 ${detail.value.submit_no} 次提交 · ${fmtDateTime(detail.value.submitted_at)} · 所有者 ${detail.value.owner.name} ${detail.value.owner.phone}`
    : ''
)

const mapMarkers = computed(() => {
  if (!detail.value?.location) return []
  const point = mapPoint(detail.value.location.longitude, detail.value.location.latitude)
  return [{ x: point.x, y: point.y, label: detail.value.info.name }]
})

const detailImages = computed(() => detail.value?.images || [])

const viewerItems = computed(() =>
  detailImages.value.map((image, index) => ({
    url: image.url || image.thumb_url,
    thumb_url: image.thumb_url || image.url,
    label: `资质图 ${index + 1}`,
    tag: image.reshoot ? '本次重拍' : image.same_as_last ? '与上次相同' : image.need_reshoot ? '需重拍' : '',
    meta: [
      detail.value?.info.name,
      image.uploader_name ? `${image.uploader_name} 上传` : '',
      image.uploaded_at ? fmtFull(image.uploaded_at) : '',
      image.file_size ? fileSize(image.file_size) : ''
    ]
      .filter(Boolean)
      .join(' · ')
  }))
)

const reshootImages = computed(() => (detail.value?.images || []).filter((image) => image.file_id))

function openViewer(index: number): void {
  viewerIndex.value = index
  viewerOpen.value = true
}

function refreshAll(): void {
  void store.fetchList()
  void pending.refresh()
}

function tagText(status: string): string {
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已驳回'
  return '待审核'
}

function openRow(row: FarmAuditItem): void {
  void store.openDetail(row)
}

function ownerFarmsText(): string {
  const farms = detail.value?.owner.other_farms || []
  if (!farms.length) return '—'
  return farms
    .map((farm) => `${farm.name}（${farm.audit_status_text}${farm.category_name ? ` · ${farm.category_name}` : ''}${farm.current ? ' · 本次审核' : ''}）`)
    .join('、')
}

function historyTone(tag?: string): string {
  if (tag === '已驳回') return 'error'
  if (tag === '已通过') return 'success'
  if (tag === '待审核') return 'warning'
  return 'primary'
}

function historyIcon(event: string, tag?: string): string {
  if (tag === '已驳回') return 'block'
  if (tag === '已通过') return 'check'
  if (event.includes('提交')) return 'upload'
  return 'history'
}

const historyItems = computed(() =>
  (detail.value?.history || []).map((item) => ({
    title: item.actor ? `${item.event} · ${item.actor}` : item.event,
    time: fmtFull(item.time),
    desc: item.desc,
    tone: historyTone(item.tag),
    icon: historyIcon(item.event, item.tag),
    tag: item.tag,
    tagType: item.tag === '已驳回' ? 'error' : item.tag === '已通过' ? 'success' : 'warning'
  }))
)

onMounted(() => {
  const id = Number(route.query.audit_id || route.query.id || 0)
  void store.fetchList()
  void pending.refresh()
  if (id) void store.openDetailById(id)
})
</script>

<template>
  <zw-shell side="admin" active="WA-R01" :crumbs="['审核中心', '养殖场注册审核']">
    <zw-page-head
      title="养殖场注册审核"
      desc="审核通过后养殖场才能使用栏舍、批次、库存和邀请成员等功能。驳回必须填写原因，原因会原样显示在所有者的“审核结果”页。"
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
          class="zw-w-320"
          placeholder="搜索养殖场名称、所有者姓名或手机号"
          prepend-inner-icon="search"
          maxlength="30"
          @keydown.enter="store.fetchList()"
        />
        <v-select
          v-model="store.query.submit_type"
          :items="submitTypes"
          class="zw-w-140"
          @update:model-value="store.fetchList()"
        />
        <v-btn variant="text" color="primary" @click="store.fetchList()">查询</v-btn>
        <v-btn variant="text" @click="store.resetFilters()">重置</v-btn>
      </div>
      <div class="zw-toolbar-r">
        <span class="zw-faint body-s">按提交时间排序，先提交的在前</span>
      </div>
    </div>

    <zw-notice v-if="store.tab === 'PENDING' && store.overdueCount" type="warning" icon="schedule" class="audit-overdue">
      <b>{{ store.overdueCount }} 条申请已等待超过 48 小时。</b>
      <template v-if="store.overdueNames.length">{{ store.overdueNames.join('、') }}，请优先处理。</template>
      列表按提交时间排序，先提交的在前。
    </zw-notice>

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
        <v-data-table
          :headers="headers"
          :items="store.list"
          item-key="audit_id"
          hide-default-footer
          density="compact"
          class="audit-table"
        >
          <template #item.farm_name="{ item }">
            <div class="zw-cell-main">
              <a class="zw-link" @click="openRow(item as FarmAuditItem)">{{ item.farm_name }}</a>
            </div>
          </template>
          <template #item.owner_name="{ item }">
            <span>{{ item.owner_name }}</span>
            <span class="zw-muted num owner-phone">{{ item.owner_phone }}</span>
          </template>
          <template #item.category="{ item }">
            <span>{{ item.category_name }} · {{ item.scale }} {{ item.count_unit_text }}</span>
          </template>
          <template #item.image_count="{ item }">
            <span class="num">{{ item.image_count ? `${item.image_count} 张` : '—' }}</span>
          </template>
          <template #item.submit_no="{ item }">
            <zw-tag v-if="item.submit_no > 1" type="primary" size="small" icon="history">第 {{ item.submit_no }} 次</zw-tag>
            <span v-else class="zw-muted">首次</span>
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
            <zw-tag :type="statusTone(item.status)" :icon="statusIcon(item.status)">{{ item.status_text || tagText(item.status) }}</zw-tag>
          </template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a v-if="store.tab === 'PENDING' && canHandle" @click="openRow(item as FarmAuditItem)">审核</a>
              <a v-else @click="openRow(item as FarmAuditItem)">详情</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot audit-foot">
          <span class="zw-faint body-s">提交超过 48 小时未处理的申请标为橙色</span>
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
      <zw-empty v-else-if="store.detailError" icon="error" title="申请详情加载失败" :desc="store.detailError" tone="error">
        <v-btn variant="outlined" @click="store.loadDetail(store.activeId)">重新加载</v-btn>
      </zw-empty>
      <template v-else-if="detail">
        <zw-notice
          v-if="detail.last_reject"
          type="error"
          icon="rule"
          :title="`上次驳回原因（${fmtFull(detail.last_reject.audited_at)} · ${detail.last_reject.auditor_name}）`"
        >
          {{ detail.last_reject.reason }}
        </zw-notice>
        <div v-if="detail.change_summary" class="change-summary">本次修改：{{ detail.change_summary }}</div>

        <section class="audit-sec">
          <div class="zw-section-title">注册信息</div>
          <zw-desc :cols="3" bordered :label-width="88">
            <zw-desc-item label="养殖场名称">{{ detail.info.name }}</zw-desc-item>
            <zw-desc-item label="主要品类">{{ detail.info.category_name }}</zw-desc-item>
            <zw-desc-item label="规模">{{ detail.info.scale }} {{ detail.info.count_unit_text }}</zw-desc-item>
            <zw-desc-item label="联系人">{{ detail.info.contact_name }}</zw-desc-item>
            <zw-desc-item label="联系电话"><span class="num">{{ detail.info.contact_phone }}</span></zw-desc-item>
            <zw-desc-item label="账号注册">{{ fmtShortDate(detail.owner.registered_at) }}</zw-desc-item>
            <zw-desc-item label="地址" :span="3">{{ detail.info.address }}</zw-desc-item>
            <zw-desc-item label="名下养殖场" :span="3">{{ ownerFarmsText() }}</zw-desc-item>
            <zw-desc-item v-if="detail.info.intro" label="养殖场简介" :span="3">{{ detail.info.intro }}</zw-desc-item>
          </zw-desc>
        </section>

        <section class="audit-sec audit-two">
          <div>
            <div class="zw-section-title">
              资质图 · {{ detailImages.length }} 张
              <span v-if="detailImages.length" class="zw-faint body-s normal-weight">点图片查看大图</span>
            </div>
            <div v-if="detailImages.length" class="photo-list">
              <div v-for="(image, index) in detailImages" :key="image.file_id" class="photo-item" @click="openViewer(index)">
                <div class="photo-frame">
                  <img
                    v-if="(image.thumb_url || image.url) && !failedImages[image.file_id]"
                    :src="image.thumb_url || image.url"
                    alt="资质图"
                    @error="failedImages[image.file_id] = true"
                  />
                  <zw-thumb v-else kind="doc" :w="160" :h="120" />
                  <span class="photo-index num">{{ index + 1 }} / {{ detailImages.length }}</span>
                </div>
                <div class="photo-cap">
                  <span>资质图 {{ index + 1 }}</span>
                  <zw-tag v-if="image.reshoot" type="primary" size="small">本次重拍</zw-tag>
                  <span v-else-if="image.same_as_last" class="zw-faint">与上次相同</span>
                  <zw-tag v-if="image.need_reshoot" type="error" size="small">需重拍</zw-tag>
                </div>
              </div>
            </div>
            <div v-else class="no-image">
              <zw-icon name="photo_library" :size="28" />
              <span>本次提交没有上传资质图</span>
            </div>
          </div>

          <div>
            <div class="zw-section-title">地图位置</div>
            <zw-map :height="148" :markers="mapMarkers" :legend="detail.location ? [{ type: 'primary', label: detail.info.name }] : []" />
            <div v-if="detail.location" class="map-foot">
              <span>
                北纬 {{ detail.location.latitude ?? '—' }}°，东经 {{ detail.location.longitude ?? '—' }}°
                <template v-if="detail.location.loc_accuracy"> · 精度约 {{ detail.location.loc_accuracy }} 米</template>
              </span>
              <span v-if="detail.location.nearest_farm_name">距{{ detail.location.nearest_farm_name }} {{ detail.location.distance_km ?? '—' }} 千米</span>
            </div>
            <div v-else class="zw-faint map-foot">地图位置：未提供</div>
          </div>
        </section>

        <section class="audit-sec">
          <div class="zw-section-title">历史提交记录</div>
          <zw-timeline v-if="historyItems.length" :items="historyItems" />
          <div v-else class="zw-faint">暂无提交记录</div>
        </section>

        <section v-if="detail.review" class="audit-sec">
          <div class="zw-section-title">审核结果</div>
          <zw-desc :cols="2" bordered :label-width="88">
            <zw-desc-item label="审核结果">
              <zw-tag :type="detail.review.result === 'APPROVED' ? 'success' : 'error'" :icon="detail.review.result === 'APPROVED' ? 'check' : 'block'">
                {{ detail.review.result_text || (detail.review.result === 'APPROVED' ? '已通过' : '已驳回') }}
              </zw-tag>
            </zw-desc-item>
            <zw-desc-item label="审核人">{{ detail.review.reviewer_name || '—' }}</zw-desc-item>
            <zw-desc-item label="审核时间">{{ fmtFull(detail.review.reviewed_at) }}</zw-desc-item>
            <zw-desc-item label="意见 / 原因" :span="2">
              {{ detail.review.opinion || detail.review.reject_reason || '—' }}
            </zw-desc-item>
          </zw-desc>
        </section>
      </template>

      <template #foot>
        <template v-if="detail">
          <span class="zw-foot-note">通过或驳回后，结果发到{{ detail.owner.name }}的消息中心</span>
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
      :title="`通过${detail?.info.name || '该养殖场'}的注册申请？`"
      width="480"
      icon="check_circle"
      tone="success"
    >
      <p class="dialog-p">
        通过后{{ detail?.info.name }}可以使用栏舍、批次、库存和邀请成员等全部功能，结果发到{{ detail?.owner.name }}的消息中心。
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
      :title="`驳回${detail?.info.name || '该养殖场'}的注册申请？`"
      width="560"
      icon="block"
      tone="error"
    >
      <p class="dialog-p">驳回原因会原样显示在{{ detail?.owner.name }}的“审核结果”页和消息中心，修改后可以重新提交。</p>
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
      <zw-form-item
        v-if="reshootImages.length"
        label="需重拍的资质图"
        optional
        hint="勾选的图片在所有者的审核结果页标为“需重拍”"
        class="dialog-form"
      >
        <div class="reshoot-row">
          <div v-for="image in reshootImages" :key="image.file_id" class="reshoot-item">
            <img v-if="image.thumb_url || image.url" :src="image.thumb_url || image.url" alt="资质图" />
            <zw-thumb v-else kind="doc" :w="104" :h="78" />
            <v-checkbox-btn
              class="reshoot-check"
              :model-value="store.reshootIds.includes(image.file_id)"
              @update:model-value="store.toggleReshoot(image.file_id)"
            />
          </div>
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
.audit-overdue {
  margin-bottom: 16px;
}
.audit-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.await-late,
.wait-late {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--warning);
}
.owner-phone {
  margin-left: 8px;
}
.audit-table :deep(td),
.audit-table :deep(th) {
  white-space: nowrap;
}
.change-summary {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.audit-sec {
  margin-top: 24px;
}
.audit-two {
  display: grid;
  grid-template-columns: 336px minmax(0, 1fr);
  gap: 24px;
}
.normal-weight {
  font-weight: 400;
}
.photo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.photo-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: zoom-in;
}
.photo-frame {
  position: relative;
  width: 160px;
  height: 120px;
  border: 1px solid var(--line-1);
  border-radius: 2px;
  overflow: hidden;
  background: var(--fill-3);
}
.photo-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.photo-index {
  position: absolute;
  right: 4px;
  bottom: 4px;
  padding: 0 4px;
  border-radius: 2px;
  background: #000000a6;
  color: #ffffff;
  font-size: 11px;
  line-height: 16px;
}
.photo-cap {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.no-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 120px;
  border: 1px dashed var(--line-2);
  border-radius: var(--radius-sm);
  color: var(--text-3);
  font-size: 13px;
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
.reshoot-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.reshoot-item {
  position: relative;
  width: 104px;
  height: 78px;
  overflow: hidden;
  border: 1px solid var(--line-1);
  border-radius: 2px;
}
.reshoot-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.reshoot-check {
  position: absolute;
  top: 0;
  left: 2px;
  background: #ffffffcc;
  border-radius: 2px;
}
</style>
