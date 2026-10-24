<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { dayjs, formatNumber } from '@zmt/shared'
import type { FarmListItemVO } from '@/api/farm'
import { useFarmListStore } from '@/stores/farm/list'
import { useFarmReload } from '@/composables/useFarmReload'
import { useUiStore } from '@/stores/ui'
import FarmQualView from './components/FarmQualView.vue'
import { farmStatusMeta } from '@/stores/farm/shared'

const router = useRouter()
const route = useRoute()
const ui = useUiStore()
const store = useFarmListStore()
const {
  list,
  total,
  relation,
  tabCounts,
  status,
  error,
  auditOpen,
  audit,
  auditStatus,
  auditError,
  auditFarm,
  quitting,
  deleting,
  switchingId
} = storeToRefs(store)

const deleteOpen = ref(false)
const deleteTarget = ref<FarmListItemVO | null>(null)
const queryHandled = ref(false)

const headers = [
  { title: '养殖场', key: 'name', sortable: false, width: 232 },
  { title: '我的角色', key: 'role', sortable: false, width: 96 },
  { title: '概况', key: 'overview', sortable: false },
  { title: '审核状态', key: 'status', sortable: false, width: 142 },
  { title: '操作', key: 'actions', sortable: false, width: 178, align: 'end' as const }
]

const segItems = computed(() => [
  { label: '全部', value: 'ALL', count: tabCounts.value.all ?? total.value },
  { label: '我创建的', value: 'CREATED', count: tabCounts.value.created ?? 0 },
  { label: '我加入的', value: 'JOINED', count: tabCounts.value.joined ?? 0 }
])

const cardSub = computed(() => {
  const all = tabCounts.value.all ?? total.value
  const pending = tabCounts.value.pending_audit ?? 0
  return pending ? `共 ${all} 个 · ${pending} 个待审核` : `共 ${all} 个`
})

const auditSub = computed(() => {
  const farm = auditFarm.value
  if (!farm) return ''
  return `${farm.name} · 你是${farm.role_text}`
})

const auditSubmitted = computed(() => {
  const value = audit.value?.submitted_at
  if (!value) return ''
  return `${dayjs(value).format('M月D日 HH:mm')} 提交 · 审核结果会发到消息中心`
})

const auditTimeline = computed(() => {
  const records = audit.value?.records || []
  return records.map((record) => {
    const tone =
      record.type === 'REJECT'
        ? 'error'
        : record.type === 'APPROVE'
          ? 'success'
          : record.type === 'RESUBMIT'
            ? 'warning'
            : 'neutral'
    const icon =
      record.type === 'REJECT'
        ? 'block'
        : record.type === 'APPROVE'
          ? 'check'
          : record.type === 'RESUBMIT'
            ? 'schedule'
            : 'send'
    return {
      title: record.type_text || record.type,
      time: record.time ? dayjs(record.time).format('M月D日 HH:mm') : '',
      tag: record.status_tag || '',
      tagType: tone === 'error' ? 'error' : tone === 'warning' ? 'warning' : tone === 'success' ? 'success' : 'neutral',
      tone,
      icon,
      desc: record.desc || ''
    }
  })
})

const auditRegistration = computed(() => audit.value?.registration || null)
const canDelete = computed(() => {
  const farm = audit.value || auditFarm.value
  return !!farm && (farm.status === 'PENDING' || farm.status === 'REJECTED')
})
const deleteTitle = computed(() => `删除${deleteTarget.value?.name || audit.value?.name || '养殖场'}？`)

function fromText(farm: FarmListItemVO): string {
  if (farm.status === 'PENDING') return '审核通过前只能修改注册信息'
  if (farm.status === 'REJECTED') return '已驳回，修改后可重新提交'
  if (farm.status === 'FROZEN') return '已被平台冻结，暂时不能使用'
  if (farm.status === 'DISABLED') return '已停用，只能查看'
  return farm.join_type === 'CREATE' ? '我创建的' : '我加入的'
}

function overview(farm: FarmListItemVO): string {
  if (farm.status === 'FROZEN') return '已被平台冻结，暂时不能使用'
  if (farm.status === 'PENDING' || farm.status === 'REJECTED') {
    const parts = [farm.main_category_name, farm.scale ? `规模 ${formatNumber(farm.scale)} ${farm.count_unit_text || ''}` : '']
    return parts.filter(Boolean).join(' · ')
  }
  if (farm.role === 'STAFF') return `负责 ${farm.my_pen_count ?? 0} 个栏舍`
  const parts: string[] = []
  if (farm.main_category_name) parts.push(farm.main_category_name)
  if (farm.current_stock !== undefined && farm.current_stock !== null) {
    parts.push(`存栏 ${formatNumber(farm.current_stock)} ${farm.count_unit_text || ''}`.trim())
  }
  if (farm.status === 'DISABLED') parts.push('已停用，只能查看')
  return parts.join(' · ')
}

type ActionKey = 'detail' | 'members' | 'switch' | 'quit' | 'audit' | 'registration' | 'frozen'
interface RowAction {
  key: ActionKey
  label: string
  danger?: boolean
}

function actionsFor(farm: FarmListItemVO): RowAction[] {
  const actions: RowAction[] = []
  if (farm.status === 'PENDING') return [{ key: 'audit', label: '审核结果' }]
  if (farm.status === 'REJECTED') return [{ key: 'registration', label: '修改注册信息' }]
  if (farm.status === 'FROZEN') return [{ key: 'frozen', label: '查看原因' }]
  if (farm.is_current) {
    if (farm.role !== 'STAFF' && farm.status === 'NORMAL') {
      actions.push({ key: 'detail', label: '养殖场信息' }, { key: 'members', label: '成员管理' })
    }
    if (farm.role !== 'STAFF' && farm.status === 'DISABLED') {
      actions.push({ key: 'detail', label: '养殖场信息' }, { key: 'members', label: '成员管理' })
    }
  } else if (farm.can_switch !== false) {
    actions.push({ key: 'switch', label: '切换' })
  }
  if (farm.can_quit) actions.push({ key: 'quit', label: '退出', danger: true })
  return actions
}

function runAction(farm: FarmListItemVO, action: RowAction): void {
  if (action.key === 'detail') void router.push({ name: 'WU-F03', params: { farmId: farm.farm_id } })
  else if (action.key === 'members') void router.push({ name: 'WU-F04', params: { farmId: farm.farm_id } })
  else if (action.key === 'switch') void store.switchFarm(farm.farm_id)
  else if (action.key === 'quit') store.quitTarget = farm
  else if (action.key === 'audit') void store.openAudit(farm)
  else if (action.key === 'registration') openRegistration(farm.farm_id)
  else if (action.key === 'frozen') {
    ui.toast(`${farm.name}已被平台冻结，暂时不能使用。原因见消息中心。`, 'error')
  }
}

function openRegistration(farmId: number): void {
  void router.push({ name: 'WU-F02', query: { mode: 'registration', farmId: String(farmId) } })
}

function openCreate(): void {
  void router.push({ name: 'WU-F02' })
}

function onRelation(value: unknown): void {
  void store.setRelation(value as 'ALL' | 'CREATED' | 'JOINED')
}

function rowProps({ item }: { item: FarmListItemVO }): Record<string, unknown> {
  return item.status === 'PENDING' || item.status === 'REJECTED' ? { class: 'f01-row-muted' } : {}
}

function retryAudit(): void {
  if (auditFarm.value) void store.openAudit(auditFarm.value)
}

function onAuditToggle(value: boolean): void {
  if (value) {
    store.auditOpen = true
    return
  }
  store.closeAudit()
  if (route.query.audit) void router.replace({ name: 'WU-F01', query: {} })
}

function editRegistration(): void {
  const farm = audit.value || auditFarm.value
  if (!farm) return
  store.closeAudit()
  openRegistration(farm.farm_id)
}

function askDelete(): void {
  const farm = audit.value || auditFarm.value
  if (!farm) return
  deleteTarget.value = auditFarm.value || null
  store.closeAudit()
  deleteOpen.value = true
}

async function confirmDelete(): Promise<void> {
  const farm = deleteTarget.value
  if (!farm) return
  const ok = await store.deleteFarm(farm)
  if (ok) deleteOpen.value = false
}

const quitOpen = computed({
  get: () => Boolean(store.quitTarget),
  set: (value: boolean) => {
    if (!value) store.quitTarget = null
  }
})

async function confirmQuit(): Promise<void> {
  const farm = store.quitTarget
  if (!farm) return
  await store.quitFarm(farm)
}

async function handleQuery(): Promise<void> {
  if (queryHandled.value) return
  queryHandled.value = true
  const auditId = Number(route.query.audit || 0)
  if (auditId) {
    const farm = list.value.find((item) => item.farm_id === auditId)
    if (farm) await store.openAudit(farm)
  }
}

onMounted(async () => {
  await store.fetch()
  await handleQuery()
})

useFarmReload(() => store.fetch(true))

const errorText = computed(() => error.value || '网络连接不稳定，请稍后重试')
</script>

<template>
  <zw-shell side="user" active="WU-F01" :crumbs="['设置', '我的养殖场']">
    <zw-page-head
      title="我的养殖场"
      desc="你创建和加入的养殖场。每个养殖场单独审核；你在每个养殖场中的角色和负责栏舍互相独立。"
    >
      <template #actions>
        <v-btn color="primary" prepend-icon="add" @click="openCreate">新建养殖场</v-btn>
      </template>
    </zw-page-head>

    <div class="f01-cols">
      <zw-card title="养殖场" :sub="cardSub" flush class="f01-main">
        <template #extra>
          <zw-seg size="small" :model-value="relation" :items="segItems" @update:model-value="onRelation" />
        </template>

        <v-skeleton-loader v-if="status === 'loading'" type="table-row@5" />

        <zw-empty v-else-if="status === 'forbidden'" icon="lock" title="无权查看" :desc="error || '你没有该页面的查看权限'" tone="warning">
          <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
        </zw-empty>

        <zw-empty v-else-if="status === 'error'" icon="cloud_off" title="加载失败" :desc="errorText" tone="error">
          <v-btn variant="outlined" @click="store.fetch()">重新加载</v-btn>
        </zw-empty>

        <zw-empty
          v-else-if="status === 'empty'"
          icon="domain"
          title="还没有养殖场"
          desc="新建一个养殖场，或在消息中心同意别人的邀请。"
        >
          <v-btn color="primary" prepend-icon="add" @click="openCreate">新建养殖场</v-btn>
        </zw-empty>

        <zw-empty
          v-else-if="status === 'filtered_empty'"
          icon="domain"
          :title="relation === 'JOINED' ? '没有我加入的养殖场' : '没有我创建的养殖场'"
          desc="可以切换筛选条件查看其他养殖场。"
        >
          <v-btn variant="text" @click="store.setRelation('ALL')">查看全部</v-btn>
        </zw-empty>

        <template v-else>
          <v-data-table
            :headers="headers"
            :items="list"
            item-value="farm_id"
            hide-default-footer
            :row-props="rowProps"
            class="f01-table"
          >
            <template #item.name="{ item }">
              <div class="f01-name">
                <span class="zw-ico-tile" :class="item.status === 'PENDING' || item.status === 'REJECTED' ? 'zw-ico-tile--warning' : 'zw-ico-tile--primary'">
                  <zw-icon name="domain" :size="18" />
                </span>
                <div class="f01-name-main">
                  <div class="f01-name-t">
                    <span class="zw-cell-main">{{ item.name }}</span>
                    <zw-tag v-if="item.is_current" type="primary" size="small">当前</zw-tag>
                  </div>
                  <div class="zw-cell-sub">{{ fromText(item) }}</div>
                </div>
              </div>
            </template>
            <template #item.role="{ item }">
              <zw-tag :type="item.role === 'OWNER' ? 'primary' : 'neutral'">{{ item.role_text }}</zw-tag>
            </template>
            <template #item.overview="{ item }">
              <span class="f01-overview">{{ overview(item) }}</span>
            </template>
            <template #item.status="{ item }">
              <div class="f01-status">
                <zw-tag v-if="item.status === 'PENDING'" type="warning" icon="schedule">待审核</zw-tag>
                <zw-tag v-else-if="item.status === 'REJECTED'" type="error" icon="block">已驳回</zw-tag>
                <zw-tag v-else-if="item.status === 'FROZEN'" type="error" icon="ac_unit">已冻结</zw-tag>
                <template v-else>
                  <zw-tag type="success" icon="check">已通过</zw-tag>
                  <zw-tag v-if="item.status === 'DISABLED'" type="error" icon="block">已停用</zw-tag>
                </template>
              </div>
            </template>
            <template #item.actions="{ item }">
              <span class="zw-cell-actions">
                <a
                  v-for="action in actionsFor(item)"
                  :key="action.key"
                  :class="{ danger: action.danger, disabled: action.key === 'switch' && switchingId === item.farm_id }"
                  @click="runAction(item, action)"
                >{{ action.key === 'switch' && switchingId === item.farm_id ? '切换中…' : action.label }}</a>
              </span>
            </template>
          </v-data-table>
          <div class="zw-table-foot f01-foot">
            <span class="zw-faint body-s">当前养殖场排第一，其余按最近切换时间排列；待审核、已驳回、已停用的排在最后</span>
            <zw-pager
              :total="total"
              :page="store.query.page"
              :size="store.query.size"
              @update:page="store.setPage($event)"
              @update:size="store.setSize($event)"
            />
          </div>
        </template>
      </zw-card>

      <div class="f01-side">
        <zw-card title="说明">
          <ul class="f01-rules">
            <li>
              <span class="zw-ico-tile zw-ico-tile--warning"><zw-icon name="fact_check" :size="18" /></span>
              <div>
                <div class="f01-rules-t">每个养殖场单独审核</div>
                <div class="f01-rules-d">待审核或已驳回的养殖场只能修改注册信息，栏舍、批次、库存和邀请成员要等审核通过后才能使用。</div>
              </div>
            </li>
            <li>
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="badge" :size="18" /></span>
              <div>
                <div class="f01-rules-t">角色按养殖场区分</div>
                <div class="f01-rules-d">切换养殖场后，左侧菜单和可见内容按你在该场的角色显示；员工看不到收支和经营数据。</div>
              </div>
            </li>
            <li>
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="apps" :size="18" /></span>
              <div>
                <div class="f01-rules-t">全部养殖场视角</div>
                <div class="f01-rules-d">只在养殖日历、消息中心、收支记账、经营报表中提供，其余页面只显示当前养殖场。</div>
              </div>
            </li>
            <li>
              <span class="zw-ico-tile"><zw-icon name="logout" :size="18" /></span>
              <div>
                <div class="f01-rules-t">退出养殖场</div>
                <div class="f01-rules-d">场长和员工可以退出加入的养殖场；所有者不能直接退出，需要先把所有权转让给场长。</div>
              </div>
            </li>
          </ul>
        </zw-card>
      </div>
    </div>

    <zw-drawer :model-value="auditOpen" title="审核结果" :sub="auditSub" :width="560" @update:model-value="onAuditToggle">
      <v-skeleton-loader v-if="auditStatus === 'loading'" type="list-item-two-line@6" />

      <zw-empty v-else-if="auditStatus === 'error'" icon="cloud_off" title="审核结果加载失败" :desc="auditError" tone="error">
        <v-btn variant="outlined" @click="retryAudit">重新加载</v-btn>
      </zw-empty>

      <template v-else-if="audit">
        <div class="f01-audit-head">
          <span class="zw-ico-tile zw-ico-tile--warning zw-ico-tile--lg"><zw-icon name="domain" :size="22" /></span>
          <div>
            <div class="f01-audit-name">
              {{ audit.name }}
              <zw-tag :type="farmStatusMeta(audit.status).tagType" :icon="farmStatusMeta(audit.status).icon">
                {{ audit.status_text || farmStatusMeta(audit.status).label }}
              </zw-tag>
            </div>
            <div class="f01-audit-sub">{{ auditSubmitted }}</div>
          </div>
        </div>

        <zw-notice v-if="audit.restrict_tip" icon="lock" class="mt-4">{{ audit.restrict_tip }}</zw-notice>
        <zw-notice v-if="audit.status === 'REJECTED' && audit.reject_reason" type="error" icon="rule" title="驳回原因" class="mt-4">
          {{ audit.reject_reason }}
        </zw-notice>

        <div class="zw-section-title mt-6">注册信息</div>
        <zw-desc v-if="auditRegistration" :cols="2" bordered :label-width="88">
          <zw-desc-item label="名称">{{ auditRegistration.name || '—' }}</zw-desc-item>
          <zw-desc-item label="主要品类">{{ auditRegistration.main_category_name || auditFarm?.main_category_name || '—' }}</zw-desc-item>
          <zw-desc-item label="规模">
            <span class="num">{{ formatNumber(auditRegistration.scale) }} {{ auditRegistration.count_unit_text || '' }}</span>
          </zw-desc-item>
          <zw-desc-item label="联系人">{{ auditRegistration.contact_name || '—' }}</zw-desc-item>
          <zw-desc-item label="联系电话" span="2">
            <span class="num">{{ auditRegistration.contact_phone_masked || auditRegistration.contact_phone || '—' }}</span>
          </zw-desc-item>
          <zw-desc-item label="地址" span="2">{{ auditRegistration.address || '—' }}</zw-desc-item>
          <zw-desc-item label="资质图" span="2">
            <FarmQualView
              v-if="auditRegistration.qualification_files?.length"
              :files="auditRegistration.qualification_files"
              :width="80"
              :height="60"
            />
            <span v-else class="zw-faint">暂无资质图</span>
          </zw-desc-item>
        </zw-desc>

        <div class="zw-section-title mt-6">审核记录</div>
        <zw-timeline :items="auditTimeline" />
      </template>

      <template #foot>
        <template v-if="audit">
          <v-btn v-if="canDelete" variant="text" color="error" prepend-icon="delete" @click="askDelete">删除养殖场</v-btn>
          <span style="flex: 1"></span>
          <v-btn variant="outlined" prepend-icon="edit" @click="editRegistration">修改注册信息</v-btn>
        </template>
      </template>
    </zw-drawer>

    <zw-dialog v-model="deleteOpen" :title="deleteTitle" icon="delete" tone="error" :width="420">
      <p class="f01-dialog-p">{{ deleteTarget?.name }}还没有通过审核。删除后注册信息和资质图一并删除，不能恢复。</p>
      <template #actions>
        <v-btn variant="outlined" @click="deleteOpen = false">取消</v-btn>
        <v-btn color="error" :loading="deleting" @click="confirmDelete">删除</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="quitOpen" :title="`退出${store.quitTarget?.name || '养殖场'}？`" icon="logout" tone="error" :width="420">
      <p class="f01-dialog-p">退出后，你从下一次操作起就看不到{{ store.quitTarget?.name || '该养殖场' }}的数据。你登记过的记录保留。</p>
      <template #actions>
        <v-btn variant="outlined" @click="store.quitTarget = null">取消</v-btn>
        <v-btn color="error" :loading="quitting" @click="confirmQuit">退出</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.f01-cols {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.f01-main {
  flex: 1 1 0;
  min-width: 0;
}
.f01-side {
  width: 300px;
  flex-shrink: 0;
}
.f01-name {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}
.f01-name-main {
  min-width: 0;
}
.f01-name-t {
  display: flex;
  align-items: center;
  gap: 8px;
}
.f01-overview {
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}
.f01-status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.f01-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
}
.f01-rules {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.f01-rules li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.f01-rules .zw-ico-tile {
  margin-top: 2px;
}
.f01-rules-t {
  font-weight: 500;
  color: var(--text-1);
}
.f01-rules-d {
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.f01-audit-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.f01-audit-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
}
.f01-audit-sub {
  margin-top: 2px;
  color: var(--text-2);
}
.f01-dialog-p {
  margin: 0;
  color: var(--text-2);
}
:deep(.f01-table .v-data-table__tr) {
  height: 56px;
}
:deep(.f01-row-muted .zw-cell-main) {
  color: var(--text-2);
}
:deep(.f01-table .v-data-table__th) {
  height: 44px;
  background: var(--fill-1);
  color: var(--text-2);
  font-weight: 600;
}
:deep(.f01-table .v-data-table__td) {
  border-bottom: 1px solid var(--line-1);
}
:deep(.f01-table .v-data-table__tr:hover) {
  background: var(--fill-1);
}
</style>
