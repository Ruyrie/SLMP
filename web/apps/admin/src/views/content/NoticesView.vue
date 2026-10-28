<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { routeQueryId } from '@/utils/routeQuery'
import { ApiError, dayjs, formatDateTime, formatNumber, formatPercent } from '@zmt/shared'
import {
  cancelNotice,
  createNotice,
  deleteNotice,
  estimateNoticeAudience,
  exportNoticeReadStats,
  getNoticeDetail,
  getNoticeFarmOptions,
  getNotices,
  getNoticeSummary,
  revokeNotice,
  updateNotice,
  uploadAdminFile,
  type NoticeDetail,
  type NoticeFarm,
  type NoticeRow,
  type NoticeSummary
} from '@/api/content'
import { getCategoryTree, type CategoryNode } from '@/api/base'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const route = useRoute()
const canHandle = computed(() => admin.hasPerm('WA-C01:HANDLE'))

const loading = ref(true)
const listLoading = ref(false)
const error = ref('')
const forbidden = ref(false)
const summary = ref<NoticeSummary | null>(null)
const list = ref<NoticeRow[]>([])
const total = ref(0)
const tabCounts = reactive<Record<string, number>>({ ALL: 0, DRAFT: 0, SCHEDULED: 0, SENT: 0, REVOKED: 0, CANCELED: 0 })
const query = reactive({ status: 'ALL', level: '', scope_type: '', keyword: '', page: 1, size: 20 })
const categories = ref<CategoryNode[]>([])
const farms = ref<NoticeFarm[]>([])
const farmKeyword = ref('')

const tabs = computed(() => [
  { label: '全部', value: 'ALL', count: tabCounts.ALL || 0 },
  { label: '草稿', value: 'DRAFT', count: tabCounts.DRAFT || 0 },
  { label: '待发送', value: 'SCHEDULED', count: tabCounts.SCHEDULED || 0 },
  { label: '已发送', value: 'SENT', count: tabCounts.SENT || 0 },
  { label: '已撤回', value: 'REVOKED', count: tabCounts.REVOKED || 0 },
  { label: '已取消', value: 'CANCELED', count: tabCounts.CANCELED || 0 }
])
const levelItems = [
  { title: '全部级别', value: '' },
  { title: '重要', value: 'IMPORTANT' },
  { title: '普通', value: 'NORMAL' }
]
const scopeItems = [
  { title: '全部接收范围', value: '' },
  { title: '全部用户', value: 'ALL' },
  { title: '按条件筛选', value: 'CONDITION' },
  { title: '指定养殖场', value: 'FARM' }
]
const roleOptions = [
  { title: '所有者', value: 'OWNER' },
  { title: '场长', value: 'MANAGER' },
  { title: '员工', value: 'STAFF' }
]
const provinceOptions = [
  ['110000', '北京'], ['120000', '天津'], ['130000', '河北'], ['140000', '山西'], ['150000', '内蒙古'],
  ['210000', '辽宁'], ['220000', '吉林'], ['230000', '黑龙江'], ['310000', '上海'], ['320000', '江苏'],
  ['330000', '浙江'], ['340000', '安徽'], ['350000', '福建'], ['360000', '江西'], ['370000', '山东'],
  ['410000', '河南'], ['420000', '湖北'], ['430000', '湖南'], ['440000', '广东'], ['450000', '广西'],
  ['460000', '海南'], ['500000', '重庆'], ['510000', '四川'], ['520000', '贵州'], ['530000', '云南'],
  ['540000', '西藏'], ['610000', '陕西'], ['620000', '甘肃'], ['630000', '青海'], ['640000', '宁夏'],
  ['650000', '新疆']
].map(([value, title]) => ({ title, value }))

const headers = [
  { title: '通知', key: 'title', sortable: false, minWidth: 280 },
  { title: '级别', key: 'level', sortable: false, width: 84 },
  { title: '接收范围', key: 'scope', sortable: false, width: 190 },
  { title: '状态', key: 'status', sortable: false, width: 94 },
  { title: '发送时间', key: 'time', sortable: false, width: 146 },
  { title: '阅读率', key: 'read', sortable: false, width: 132 },
  { title: '创建人', key: 'creator', sortable: false, width: 78 },
  { title: '操作', key: 'actions', sortable: false, width: 124, align: 'end' as const }
]

function canEdit(row: NoticeRow): boolean {
  return canHandle.value && (row.status === 'DRAFT' || row.status === 'SCHEDULED')
}

function statusTone(status: string): string {
  if (status === 'DRAFT') return 'neutral'
  if (status === 'SCHEDULED') return 'primary'
  if (status === 'SENT') return 'success'
  if (status === 'REVOKED') return 'warning'
  return 'neutral'
}

function statusIcon(status: string): string {
  return { DRAFT: 'edit_note', SCHEDULED: 'schedule', SENT: 'check', REVOKED: 'undo', CANCELED: 'block' }[status] || 'info'
}

function statusFoot(row: NoticeRow): string {
  if (row.status === 'SCHEDULED' && row.scheduled_at) return '定时发送'
  if (row.status === 'REVOKED') return `${row.revoked_at ? formatDateTime(row.revoked_at) : ''} 撤回`
  if (row.status === 'CANCELED') return '发送前已取消'
  return ''
}

function timeText(row: NoticeRow): string {
  if (row.status === 'SCHEDULED') return row.scheduled_at ? formatDateTime(row.scheduled_at) : '—'
  if (row.sent_at) return formatDateTime(row.sent_at)
  return row.updated_at ? formatDateTime(row.updated_at) : '—'
}

function scopeSub(row: NoticeRow): string {
  return [row.condition_text, row.account_count ? `${formatNumber(row.account_count)} 个账号` : ''].filter(Boolean).join(' · ')
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await getNoticeSummary()
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) forbidden.value = true
  }
}

async function fetchList(): Promise<void> {
  listLoading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const data = await getNotices({
      status: query.status === 'ALL' ? undefined : query.status,
      level: query.level || undefined,
      scope_type: query.scope_type || undefined,
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      size: query.size
    })
    list.value = data.list || []
    total.value = data.total || 0
    Object.assign(tabCounts, data.tab_counts || {})
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) {
      forbidden.value = true
      error.value = e.message
    } else {
      error.value = e instanceof Error ? e.message : '加载失败'
    }
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
    listLoading.value = false
  }
}

function loadCategories(): void {
  getCategoryTree()
    .then((data) => { categories.value = data || [] })
    .catch(() => { categories.value = [] })
}

function applyStatus(value: string): void {
  query.status = value
  query.page = 1
  void fetchList()
}

function resetFilters(): void {
  query.status = 'ALL'
  query.level = ''
  query.scope_type = ''
  query.keyword = ''
  query.page = 1
  void fetchList()
}

function changePage(page: number): void {
  query.page = page
  void fetchList()
}

function changeSize(size: number): void {
  query.size = size
  query.page = 1
  void fetchList()
}

/* ---------------- 编辑 ---------------- */
const drawer = ref(false)
const detailDrawer = ref(false)
const detailLoading = ref(false)
const detail = ref<NoticeDetail | null>(null)
const editing = ref<NoticeDetail | null>(null)
const saving = ref(false)
const formError = ref('')
const fieldErrors = reactive<Record<string, string>>({})
const imageUploading = ref(false)
const imageProgress = ref(0)
const imageError = ref('')
const imageInput = ref<HTMLInputElement | null>(null)
const lastImageFile = ref<File | null>(null)
const audience = ref<{ account_count: number; farm_count: number } | null>(null)
const audienceLoading = ref(false)
const form = reactive({
  level: 'NORMAL',
  title: '',
  content: '',
  image_file_id: null as number | null,
  image_url: '',
  scope_type: 'ALL',
  roles: ['OWNER', 'MANAGER'] as string[],
  province_codes: [] as string[],
  category_ids: [] as number[],
  farm_ids: [] as number[],
  send_mode: 'NOW' as 'NOW' | 'SCHEDULED',
  scheduled_at: dayjs().tz('Asia/Shanghai').add(30, 'minute').format('YYYY-MM-DDTHH:mm')
})
let audienceTimer = 0

function htmlToText(html?: string): string {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html.replace(/<br\s*\/?>/gi, '\n').replace(/<\/(p|li|div)>/gi, '\n'), 'text/html')
  return (doc.body.textContent || '').replace(/\n{3,}/g, '\n\n').trim()
}

function textToHtml(text: string): string {
  const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return escaped.split(/\n{2,}/).map((p) => `<p>${p.replace(/\n/g, '<br>')}</p>`).join('')
}

function clearFields(): void {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

function openCreate(copyFrom?: NoticeRow): void {
  editing.value = null
  form.level = 'NORMAL'
  form.title = copyFrom ? `${copyFrom.title}（副本）`.slice(0, 50) : ''
  form.content = ''
  form.image_file_id = null
  form.image_url = ''
  form.scope_type = 'ALL'
  form.roles = ['OWNER', 'MANAGER']
  form.province_codes = []
  form.category_ids = []
  form.farm_ids = []
  form.send_mode = 'NOW'
  form.scheduled_at = dayjs().tz('Asia/Shanghai').add(30, 'minute').format('YYYY-MM-DDTHH:mm')
  audience.value = null
  formError.value = ''
  clearFields()
  drawer.value = true
  if (copyFrom) {
    void copyDetail(copyFrom.id)
  }
}

async function openEdit(row: NoticeRow): Promise<void> {
  editing.value = null
  formError.value = ''
  clearFields()
  drawer.value = true
  try {
    const data = await getNoticeDetail(row.id)
    editing.value = data
    form.level = data.level || 'NORMAL'
    form.title = data.title
    form.content = htmlToText(data.content)
    form.image_file_id = data.image?.file_id || null
    form.image_url = data.image?.url || data.image?.thumb_url || ''
    form.scope_type = data.scope_type || 'ALL'
    form.roles = data.roles?.length ? [...data.roles] : ['OWNER', 'MANAGER']
    form.province_codes = [...(data.province_codes || [])]
    form.category_ids = [...(data.category_ids || [])]
    form.farm_ids = (data.farms || []).map((f) => f.farm_id)
    form.send_mode = data.send_mode === 'SCHEDULED' ? 'SCHEDULED' : 'NOW'
    form.scheduled_at = data.scheduled_at ? dayjs(data.scheduled_at).format('YYYY-MM-DDTHH:mm') : form.scheduled_at
    audience.value = data.est_account_count !== undefined ? { account_count: data.est_account_count, farm_count: data.est_farm_count || 0 } : null
  } catch (e) {
    drawer.value = false
    ui.toast(e instanceof Error ? e.message : '详情加载失败', 'error')
  }
}

async function copyDetail(id: number): Promise<void> {
  try {
    const data = await getNoticeDetail(id)
    if (editing.value) return
    form.level = data.level || 'NORMAL'
    form.content = htmlToText(data.content)
    form.scope_type = data.scope_type || 'ALL'
    form.roles = data.roles?.length ? [...data.roles] : form.roles
    form.province_codes = [...(data.province_codes || [])]
    form.category_ids = [...(data.category_ids || [])]
    form.farm_ids = (data.farms || []).map((f) => f.farm_id)
    form.image_file_id = data.image?.file_id || null
    form.image_url = data.image?.url || data.image?.thumb_url || ''
  } catch {
    /* 复制来源已删除时保持空表单 */
  }
}

async function openDetail(row: NoticeRow): Promise<void> {
  await openDetailById(row.id)
}

async function openDetailById(id: number): Promise<void> {
  detailDrawer.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getNoticeDetail(id)
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '详情加载失败', 'error')
    detailDrawer.value = false
  } finally {
    detailLoading.value = false
  }
}

watch(
  () => [form.scope_type, form.roles.join(','), form.province_codes.join(','), form.category_ids.join(','), form.farm_ids.join(',')],
  () => {
    if (!drawer.value) return
    window.clearTimeout(audienceTimer)
    audienceTimer = window.setTimeout(() => { void estimateAudience() }, 300)
  }
)

async function estimateAudience(): Promise<void> {
  if (form.scope_type === 'CONDITION' && !form.roles.length) {
    audience.value = null
    return
  }
  if (form.scope_type === 'FARM' && !form.farm_ids.length) {
    audience.value = null
    return
  }
  audienceLoading.value = true
  try {
    audience.value = await estimateNoticeAudience({
      scope_type: form.scope_type,
      roles: form.scope_type === 'CONDITION' ? form.roles : undefined,
      province_codes: form.province_codes.length ? form.province_codes : undefined,
      category_ids: form.category_ids.length ? form.category_ids : undefined,
      farm_ids: form.scope_type === 'FARM' ? form.farm_ids : undefined
    })
  } catch {
    audience.value = null
  } finally {
    audienceLoading.value = false
  }
}

async function searchFarms(): Promise<void> {
  try {
    farms.value = await getNoticeFarmOptions(farmKeyword.value.trim())
  } catch {
    farms.value = []
  }
}

function pickFarm(farmId: number): void {
  if (!form.farm_ids.includes(farmId)) form.farm_ids.push(farmId)
  farmKeyword.value = ''
}

function removeFarm(farmId: number): void {
  form.farm_ids = form.farm_ids.filter((id) => id !== farmId)
}

async function onImageSelected(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  ;(event.target as HTMLInputElement).value = ''
  if (!file) return
  lastImageFile.value = file
  await uploadImage(file)
}

async function uploadImage(file: File): Promise<void> {
  imageError.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    imageError.value = '只支持 JPG 或 PNG 图片，单张不超过 5MB'
    return
  }
  imageUploading.value = true
  imageProgress.value = 0
  try {
    const data = await uploadAdminFile(file, 'NOTICE_IMAGE', (percent) => { imageProgress.value = percent })
    form.image_file_id = data.file_id
    form.image_url = data.url || ''
  } catch (e) {
    imageError.value = e instanceof Error ? e.message : '上传失败，请重试'
  } finally {
    imageUploading.value = false
  }
}

function retryImage(): void {
  if (lastImageFile.value) void uploadImage(lastImageFile.value)
}

function validateForm(action: 'DRAFT' | 'SCHEDULE' | 'SEND_NOW'): boolean {
  clearFields()
  formError.value = ''
  if (!form.title.trim()) fieldErrors.title = '请填写通知标题'
  else if (form.title.trim().length > 50) fieldErrors.title = '标题不能超过 50 字'
  if (action !== 'DRAFT') {
    if (!form.content.trim()) fieldErrors.content = '请填写正文'
    else if (form.content.length > 2000) fieldErrors.content = '正文不能超过 2000 字'
    if (form.scope_type === 'CONDITION' && !form.roles.length) fieldErrors.roles = '至少选择一个角色'
    if (form.scope_type === 'FARM' && !form.farm_ids.length) fieldErrors.farm_ids = '请选择至少一个养殖场'
    if (form.send_mode === 'SCHEDULED') {
      const time = dayjs(form.scheduled_at)
      if (!form.scheduled_at || time.isBefore(dayjs().tz('Asia/Shanghai').add(10, 'minute'))) {
        fieldErrors.scheduled_at = '定时发送时间至少在 10 分钟以后'
      }
    }
  }
  return Object.keys(fieldErrors).length === 0
}

function buildPayload(action: 'DRAFT' | 'SCHEDULE' | 'SEND_NOW') {
  return {
    submit_action: action,
    level: form.level,
    title: form.title.trim(),
    content: textToHtml(form.content),
    image_file_id: form.image_file_id,
    scope_type: form.scope_type,
    roles: form.scope_type === 'CONDITION' ? form.roles : [],
    province_codes: form.scope_type === 'CONDITION' ? form.province_codes : [],
    category_ids: form.scope_type === 'CONDITION' ? form.category_ids : [],
    farm_ids: form.scope_type === 'FARM' ? form.farm_ids : [],
    scheduled_at: action === 'SCHEDULE' ? dayjs(form.scheduled_at).format('YYYY-MM-DD HH:mm:ss') : null,
    copied_from_id: null,
    version: editing.value?.version
  }
}

const sendConfirmOpen = ref(false)
const pendingAction = ref<'DRAFT' | 'SCHEDULE' | 'SEND_NOW'>('DRAFT')

function submit(action: 'DRAFT' | 'SCHEDULE' | 'SEND_NOW'): void {
  if (!canHandle.value) {
    ui.toast('你没有通知发布权限', 'error')
    return
  }
  if (!validateForm(action)) return
  if (action === 'SEND_NOW') {
    pendingAction.value = action
    sendConfirmOpen.value = true
    return
  }
  void doSubmit(action)
}

async function doSubmit(action: 'DRAFT' | 'SCHEDULE' | 'SEND_NOW'): Promise<void> {
  saving.value = true
  formError.value = ''
  try {
    if (editing.value) {
      await updateNotice(editing.value.id, buildPayload(action))
    } else {
      await createNotice(buildPayload(action))
    }
    sendConfirmOpen.value = false
    drawer.value = false
    ui.toast(
      action === 'DRAFT'
        ? '已保存草稿'
        : action === 'SCHEDULE'
          ? `已设为定时发送，${dayjs(form.scheduled_at).format('M月D日 HH:mm')} 发送`
          : audience.value
            ? `已发送，正在推送给 ${formatNumber(audience.value.account_count)} 个账号`
            : '已发送'
    )
    await Promise.all([fetchList(), loadSummary()])
  } catch (e) {
    if (e instanceof ApiError) {
      for (const item of e.fieldErrors) fieldErrors[item.field] = item.message
      if (e.code === 40308) {
        ui.toast(e.message, 'error')
        await admin.me().catch(() => undefined)
      } else {
        formError.value = e.message
        ui.toast(e.message, 'error')
      }
    } else {
      formError.value = '保存失败，请稍后重试'
      ui.toast(formError.value, 'error')
    }
  } finally {
    saving.value = false
  }
}

/* ---------------- 操作对话框 ---------------- */
const actionOpen = ref(false)
const actionKind = ref<'cancel' | 'revoke' | 'delete' | ''>('')
const actionRow = ref<NoticeRow | null>(null)
const actionReason = ref('')
const actionSaving = ref(false)
const actionError = ref('')
const actionVersion = ref(0)

async function openAction(row: NoticeRow, kind: 'cancel' | 'revoke' | 'delete'): Promise<void> {
  actionRow.value = row
  actionKind.value = kind
  actionReason.value = ''
  actionError.value = ''
  actionVersion.value = 0
  actionOpen.value = true
  try {
    const data = await getNoticeDetail(row.id)
    actionVersion.value = data.version || 0
  } catch {
    /* 版本冲突由服务端提示 */
  }
}

async function confirmAction(): Promise<void> {
  const row = actionRow.value
  if (!row) return
  if (actionKind.value !== 'delete' && !actionReason.value.trim()) {
    actionError.value = actionKind.value === 'cancel' ? '请填写取消原因' : '请填写撤回原因'
    return
  }
  actionSaving.value = true
  actionError.value = ''
  try {
    if (actionKind.value === 'cancel') await cancelNotice(row.id, actionReason.value.trim(), actionVersion.value)
    if (actionKind.value === 'revoke') await revokeNotice(row.id, actionReason.value.trim(), actionVersion.value)
    if (actionKind.value === 'delete') await deleteNotice(row.id, actionVersion.value)
    actionOpen.value = false
    ui.toast(actionKind.value === 'cancel' ? '已取消发送' : actionKind.value === 'revoke' ? '已撤回这条通知' : '已删除草稿')
    await Promise.all([fetchList(), loadSummary()])
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    actionSaving.value = false
  }
}

async function exportReadStats(): Promise<void> {
  try {
    const result = await exportNoticeReadStats({
      status: undefined,
      level: query.level || undefined,
      scope_type: query.scope_type || undefined,
      keyword: query.keyword.trim() || undefined
    })
    if (result.url) window.open(result.url, '_blank', 'noopener')
    ui.toast(`已导出 ${formatNumber(result.row_count || 0)} 行阅读数据`)
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '导出失败', 'error')
  }
}

onMounted(async () => {
  loading.value = true
  loadCategories()
  await Promise.all([fetchList(), loadSummary()])
  loading.value = false
  const id = routeQueryId(route)
  if (id) void openDetailById(id)
})
</script>

<template>
  <zw-shell side="admin" active="WA-C01" :crumbs="['内容运营', '通知发布']">
    <zw-page-head title="通知发布" desc="通知发到用户端消息中心的“平台通知”。重要通知不受免打扰限制，发送后立即推送；发送、撤回、取消都会记入操作日志。">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="download" :disabled="!canHandle" @click="exportReadStats">导出阅读数据</v-btn>
        <v-btn v-if="canHandle" color="primary" prepend-icon="add" @click="openCreate()">新建通知</v-btn>
      </template>
    </zw-page-head>

    <zw-notice v-if="forbidden" type="error" title="你没有内容运营的权限">请联系超级管理员在“管理员与权限”中开通 WA-C01 的查看权限。</zw-notice>

    <div v-if="!forbidden" class="zw-grid-4 notice-stats">
      <zw-stat label="本月已发送" icon="campaign" :value="formatNumber(summary?.month_sent_count || 0)" unit="条" :foot="`其中重要通知 ${summary?.month_important_count || 0} 条，撤回 ${summary?.month_revoked_count || 0} 条`" />
      <zw-stat label="近 30 天平均阅读率" icon="visibility" :value="summary?.avg_read_rate_30d !== undefined ? formatPercent(summary.avg_read_rate_30d) : '—'" foot="不含已撤回的通知" info="已读账号数 ÷ 送达账号数；撤回的通知不计入" />
      <zw-stat label="待发送" icon="schedule" tone="primary" :value="formatNumber(summary?.scheduled_count || 0)" unit="条" :foot="summary?.next_scheduled_at ? `最近一条 ${formatDateTime(summary.next_scheduled_at)} 定时发送` : '暂无定时发送的通知'" />
      <zw-stat label="草稿" icon="edit_note" :value="formatNumber(summary?.draft_count || 0)" unit="条" foot="草稿只有管理员能看到" />
    </div>

    <zw-card v-if="!forbidden" flush>
      <div class="zw-toolbar notice-toolbar">
        <div class="zw-toolbar-l">
          <zw-seg :items="tabs" :model-value="query.status" @update:model-value="applyStatus(String($event))" />
        </div>
        <div class="zw-toolbar-r">
          <v-select v-model="query.level" :items="levelItems" class="zw-w-120" density="compact" hide-details @update:model-value="applyStatus(query.status)" />
          <v-select v-model="query.scope_type" :items="scopeItems" class="zw-w-140" density="compact" hide-details @update:model-value="applyStatus(query.status)" />
          <v-text-field v-model="query.keyword" class="zw-w-200" placeholder="搜索通知标题" prepend-inner-icon="search" density="compact" hide-details clearable @keydown.enter="fetchList()" @click:clear="fetchList()" />
          <v-btn variant="text" color="primary" @click="fetchList()">查询</v-btn>
        </div>
      </div>

      <v-skeleton-loader v-if="loading" type="table-row@7" />
      <zw-empty v-else-if="error" icon="cloud_off" title="通知加载失败" :desc="error" tone="error">
        <v-btn variant="outlined" @click="fetchList()">重新加载</v-btn>
      </zw-empty>
      <zw-empty v-else-if="!list.length && (query.keyword || query.level || query.scope_type || query.status !== 'ALL')" icon="search_off" title="没有符合条件的通知" desc="换一个关键词、级别或接收范围试试。">
        <v-btn variant="text" color="primary" @click="resetFilters">清除筛选</v-btn>
      </zw-empty>
      <zw-empty v-else-if="!list.length" icon="campaign" title="还没有通知" desc="新建通知后，用户在消息中心的“平台通知”中看到。">
        <v-btn v-if="canHandle" color="primary" @click="openCreate()">新建通知</v-btn>
      </zw-empty>
      <template v-else>
        <v-data-table :headers="headers" :items="list" item-key="id" hide-default-footer density="compact" class="notice-table" :loading="listLoading">
          <template #item.title="{ item }">
            <div class="notice-title-cell">
              <div class="notice-title-line">
                <span class="notice-title" :title="item.title" @click="openDetail(item as NoticeRow)">{{ item.title }}</span>
                <zw-icon v-if="item.image_count" name="image" :size="16" color="text-3" />
              </div>
              <div class="notice-sub">
                <template v-if="item.image_count">配图 {{ item.image_count }} 张 · </template>
                正文 {{ formatNumber(item.content_length) }} 字
              </div>
            </div>
          </template>
          <template #item.level="{ item }">
            <zw-tag v-if="item.level === 'IMPORTANT'" type="primary" icon="campaign" size="small">重要</zw-tag>
            <span v-else class="zw-muted">普通</span>
          </template>
          <template #item.scope="{ item }">
            <div class="notice-scope">{{ item.scope_text || '—' }}</div>
            <div v-if="scopeSub(item as NoticeRow)" class="notice-sub">{{ scopeSub(item as NoticeRow) }}</div>
          </template>
          <template #item.status="{ item }">
            <zw-tag :type="statusTone(item.status)" :icon="statusIcon(item.status)" size="small">{{ item.status_text }}</zw-tag>
          </template>
          <template #item.time="{ item }">
            <div class="num">{{ timeText(item as NoticeRow) }}</div>
            <div v-if="statusFoot(item as NoticeRow)" class="notice-sub">{{ statusFoot(item as NoticeRow) }}</div>
          </template>
          <template #item.read="{ item }">
            <div v-if="item.status === 'SENT' && item.read_rate !== null && item.read_rate !== undefined" class="read-cell">
              <v-progress-linear :model-value="Number(item.read_rate) * 100" height="6" rounded color="primary" class="read-bar" />
              <span class="num read-text">{{ formatPercent(item.read_rate) }}</span>
            </div>
            <div v-else-if="item.status === 'REVOKED'" class="notice-sub">撤回前已读 {{ formatNumber(item.read_before_revoke || 0) }}</div>
            <span v-else class="zw-faint">—</span>
          </template>
          <template #item.creator="{ item }">{{ item.creator_name || '—' }}</template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a v-if="canEdit(item as NoticeRow)" class="zw-link" @click="openEdit(item as NoticeRow)">编辑</a>
              <a v-if="item.status === 'SCHEDULED' && canHandle" class="zw-link danger" @click="openAction(item as NoticeRow, 'cancel')">取消发送</a>
              <a v-if="item.status === 'DRAFT' && canHandle" class="zw-link danger" @click="openAction(item as NoticeRow, 'delete')">删除</a>
              <a v-if="item.status !== 'DRAFT' && item.status !== 'SCHEDULED'" class="zw-link" @click="openDetail(item as NoticeRow)">查看</a>
              <a v-if="item.status === 'SENT' && canHandle" class="zw-link danger" @click="openAction(item as NoticeRow, 'revoke')">撤回</a>
              <a v-if="item.status === 'CANCELED' && canHandle" class="zw-link" @click="openCreate(item as NoticeRow)">复制</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot">
          <span class="zw-muted">撤回只对已发送的通知可用；待发送的通知可以取消发送</span>
          <zw-pager :total="total" :page="query.page" :size="query.size" @update:page="changePage" @update:size="changeSize" />
        </div>
      </template>
    </zw-card>

    <!-- 新建 / 编辑 -->
    <zw-drawer v-model="drawer" :title="editing ? '编辑通知' : '新建通知'" sub="草稿只有管理员能看到；定时发送前可以修改或取消" width="760">
      <div class="notice-form-layout">
        <div class="notice-form-main">
          <div class="notice-form-row">
            <zw-form-item label="通知级别" required>
              <v-radio-group v-model="form.level" inline hide-details density="compact">
                <v-radio label="重要" value="IMPORTANT" />
                <v-radio label="普通" value="NORMAL" />
              </v-radio-group>
              <div class="zw-form-hint">重要通知不受免打扰限制，立即推送</div>
            </zw-form-item>
            <zw-form-item label="标题" required :error="fieldErrors.title">
              <v-text-field v-model="form.title" placeholder="请输入通知标题" maxlength="50" counter :error="!!fieldErrors.title" />
            </zw-form-item>
          </div>

          <zw-form-item label="配图" optional>
            <div class="notice-upload">
              <button v-if="!form.image_url" type="button" class="notice-upload-box" :disabled="imageUploading" @click="imageInput?.click()">
                <zw-icon name="add_photo_alternate" :size="28" />
                <span>{{ imageUploading ? `上传中 ${imageProgress}%` : '上传配图' }}</span>
                <span class="notice-sub">JPG / PNG 不超过 5MB，最多 1 张</span>
              </button>
              <div v-else class="notice-image">
                <img :src="form.image_url" alt="通知配图" />
                <button type="button" class="notice-image-del" aria-label="删除配图" @click="form.image_file_id = null; form.image_url = ''"><zw-icon name="close" :size="14" /></button>
              </div>
              <v-progress-linear v-if="imageUploading" :model-value="imageProgress" height="4" rounded color="primary" class="notice-upload-progress" />
              <div v-if="imageError" class="notice-upload-error">
                {{ imageError }}
                <a v-if="lastImageFile" class="zw-link" @click="retryImage">重试</a>
              </div>
            </div>
            <input ref="imageInput" type="file" accept="image/jpeg,image/png" hidden @change="onImageSelected" />
          </zw-form-item>

          <zw-form-item label="正文" required :error="fieldErrors.content">
            <div class="notice-editor">
              <div class="notice-editor-bar">
                <span>正文</span>
                <span class="zw-muted">用户端不支持字号和颜色，只保留分段；每行一个段落</span>
              </div>
              <v-textarea v-model="form.content" placeholder="请输入通知正文，例如：各养殖场：&#10;请做好防寒准备。&#10;1. 检查门窗。" :rows="9" maxlength="2000" counter hide-details :error="!!fieldErrors.content" class="notice-textarea" />
            </div>
          </zw-form-item>

          <zw-form-item label="接收范围" required :error="fieldErrors.roles || fieldErrors.farm_ids">
            <v-radio-group v-model="form.scope_type" inline hide-details density="compact" class="notice-scope-radio">
              <v-radio label="全部用户" value="ALL" />
              <v-radio label="按条件筛选" value="CONDITION" />
              <v-radio label="指定养殖场" value="FARM" />
            </v-radio-group>
            <div v-if="form.scope_type === 'CONDITION'" class="notice-condition">
              <div class="notice-condition-row">
                <span class="notice-condition-label">角色</span>
                <v-checkbox v-for="role in roleOptions" :key="role.value" v-model="form.roles" :label="role.title" :value="role.value" hide-details density="compact" />
              </div>
              <div class="notice-condition-row">
                <span class="notice-condition-label">地区</span>
                <v-select v-model="form.province_codes" :items="provinceOptions" multiple chips density="compact" hide-details placeholder="全部地区" class="notice-condition-select" />
              </div>
              <div class="notice-condition-row">
                <span class="notice-condition-label">品类</span>
                <v-select v-model="form.category_ids" :items="categories.map((c) => ({ title: c.name, value: c.id }))" multiple chips density="compact" hide-details placeholder="全部品类" class="notice-condition-select" />
              </div>
            </div>
            <div v-if="form.scope_type === 'FARM'" class="notice-condition">
              <div class="notice-farm-picked">
                <zw-tag v-for="id in form.farm_ids" :key="id" type="outline" size="small">
                  {{ farms.find((f) => f.farm_id === id)?.name || `养殖场 ${id}` }}
                  <a class="notice-farm-remove" @click="removeFarm(id)">×</a>
                </zw-tag>
              </div>
              <v-text-field v-model="farmKeyword" placeholder="搜索并选择养殖场" prepend-inner-icon="search" density="compact" hide-details clearable @keydown.enter="searchFarms" @click:clear="farmKeyword = ''" />
              <div v-if="farms.length" class="notice-farm-options">
                <button v-for="farm in farms" :key="farm.farm_id" type="button" class="notice-farm-option" @click="pickFarm(farm.farm_id)">{{ farm.name }}</button>
              </div>
            </div>
            <div v-if="audienceLoading" class="notice-audience">正在估算预计送达…</div>
            <div v-else-if="audience" class="notice-audience"><zw-icon name="group" :size="16" />预计送达 {{ formatNumber(audience.account_count) }} 个账号，涉及 {{ formatNumber(audience.farm_count) }} 个养殖场</div>
          </zw-form-item>

          <zw-form-item label="发送时间" required :error="fieldErrors.scheduled_at">
            <div class="notice-schedule">
              <zw-seg v-model="form.send_mode" :items="[{ label: '立即发送', value: 'NOW' }, { label: '定时发送', value: 'SCHEDULED' }]" />
              <v-text-field v-if="form.send_mode === 'SCHEDULED'" v-model="form.scheduled_at" type="datetime-local" class="notice-datetime" density="compact" hide-details :min="dayjs().tz('Asia/Shanghai').add(10, 'minute').format('YYYY-MM-DDTHH:mm')" />
            </div>
            <div class="zw-form-hint">定时发送前可以修改或取消发送；到点后自动发送并推送</div>
          </zw-form-item>
          <zw-notice v-if="formError" type="error">{{ formError }}</zw-notice>
        </div>

        <aside class="notice-preview">
          <div class="notice-preview-title"><zw-icon name="smartphone" :size="14" />用户端预览</div>
          <div class="notice-preview-phone">
            <div class="notice-preview-head"><zw-icon name="arrow_back" :size="16" />通知详情</div>
            <div class="notice-preview-tags">
              <span v-if="form.level === 'IMPORTANT'" class="notice-preview-important"><zw-icon name="campaign" :size="12" />重要</span>
              <span class="notice-preview-normal">平台通知</span>
            </div>
            <div class="notice-preview-h">{{ form.title || '通知标题' }}</div>
            <div class="notice-preview-date">智牧通平台 · {{ form.send_mode === 'SCHEDULED' ? dayjs(form.scheduled_at).format('M月D日 HH:mm') : '立即' }}发布</div>
            <img v-if="form.image_url" :src="form.image_url" class="notice-preview-img" alt="配图预览" />
            <div class="notice-preview-body">{{ form.content || '通知正文会显示在这里。' }}</div>
          </div>
        </aside>
      </div>
      <template #foot>
        <span class="zw-foot-note">发送后记入操作日志；带 * 为必填</span>
        <v-btn variant="outlined" :disabled="saving" @click="submit('DRAFT')">保存草稿</v-btn>
        <v-btn v-if="form.send_mode === 'SCHEDULED'" color="primary" prepend-icon="schedule_send" :loading="saving" @click="submit('SCHEDULE')">定时发送</v-btn>
        <v-btn v-else color="primary" prepend-icon="send" :loading="saving" @click="submit('SEND_NOW')">发送</v-btn>
      </template>
    </zw-drawer>

    <!-- 详情 -->
    <zw-drawer v-model="detailDrawer" :title="detail?.title || '通知详情'" :sub="detail ? `${detail.status_text}${detail.sent_at ? ' · ' + formatDateTime(detail.sent_at) : ''}${detail.sender?.name ? ' · 发送人 ' + detail.sender.name : ''}` : ''" width="760">
      <v-skeleton-loader v-if="detailLoading" type="paragraph@8" />
      <template v-else-if="detail">
        <div class="notice-detail-tags">
          <zw-tag v-if="detail.level === 'IMPORTANT'" type="primary" icon="campaign" size="small">重要</zw-tag>
          <zw-tag :type="statusTone(detail.status)" :icon="statusIcon(detail.status)" size="small">{{ detail.status_text }}</zw-tag>
          <zw-tag type="outline" size="small">{{ detail.scope_text || '全部用户' }}</zw-tag>
          <span v-if="detail.condition_text" class="zw-muted">{{ detail.condition_text }}</span>
        </div>
        <zw-desc :cols="2" bordered :label-width="96" class="notice-detail-desc">
          <zw-desc-item label="接收范围">{{ detail.scope_text || '—' }}<span v-if="detail.condition_text" class="zw-muted"> · {{ detail.condition_text }}</span></zw-desc-item>
          <zw-desc-item label="送达账号">{{ detail.delivered_count !== null && detail.delivered_count !== undefined ? `${formatNumber(detail.delivered_count)} 个` : `${formatNumber(detail.account_count || 0)} 个（预计）` }}</zw-desc-item>
          <zw-desc-item label="创建人">{{ detail.creator?.name || detail.creator_name || '—' }}<span v-if="detail.creator?.at" class="zw-muted"> · {{ formatDateTime(detail.creator.at) }}</span></zw-desc-item>
          <zw-desc-item label="发送人">{{ detail.sender?.name || '—' }}<span v-if="detail.sender?.at" class="zw-muted"> · {{ formatDateTime(detail.sender.at) }}</span></zw-desc-item>
        </zw-desc>

        <template v-if="detail.status === 'SENT' || detail.status === 'REVOKED'">
          <div class="zw-section-title">阅读情况 <span class="zw-muted notice-stat-at">统计到 {{ detail.stat_at ? formatDateTime(detail.stat_at) : '—' }}</span></div>
          <div class="zw-grid-3 notice-read-stats">
            <zw-stat label="已读" :value="formatNumber(detail.read_count || 0)" unit="个账号" :foot="`阅读率 ${formatPercent(detail.read_rate)}`" tone="primary" />
            <zw-stat label="未读" :value="formatNumber(detail.unread_count || 0)" unit="个账号" foot="未读用户可在消息中心继续看到" />
            <zw-stat label="推送点开" :value="formatNumber(detail.push_open_count || 0)" unit="次" foot="从手机推送直接打开" />
          </div>
          <div v-if="detail.role_stats?.length" class="notice-role-stats">
            <div v-for="role in detail.role_stats" :key="role.role" class="notice-role-row">
              <span class="notice-role-name">{{ role.role_text }}</span>
              <v-progress-linear :model-value="Number(role.rate || 0) * 100" height="8" rounded color="primary" class="notice-role-bar" />
              <span class="num">{{ role.read }} / {{ role.delivered }} · {{ formatPercent(role.rate) }}</span>
            </div>
          </div>
          <div v-if="detail.status === 'REVOKED'" class="notice-revoked">
            <zw-icon name="undo" :size="18" />已撤回，撤回前已读 {{ formatNumber(detail.read_before_revoke || 0) }} 个账号
            <span v-if="detail.revoke_reason" class="zw-muted"> · {{ detail.revoke_reason }}</span>
          </div>
        </template>

        <div class="zw-section-title">正文</div>
        <div class="notice-detail-content">{{ htmlToText(detail.content) || '—' }}</div>
        <img v-if="detail.image?.url" :src="detail.image.url" class="notice-detail-image" alt="通知配图" />

        <template v-if="detail.timeline?.length">
          <div class="zw-section-title">操作记录</div>
          <zw-timeline :items="detail.timeline.map((item) => ({ title: item.action_text, time: formatDateTime(item.time), desc: item.desc || (item.admin_name ? item.admin_name : ''), tone: item.action === 'REVOKE' ? 'warning' : 'neutral' }))" />
        </template>
      </template>
      <template #foot>
        <v-btn variant="outlined" @click="detailDrawer = false">关闭</v-btn>
        <v-btn v-if="detail && canHandle && (detail.status === 'SCHEDULED' || detail.status === 'DRAFT')" color="primary" @click="detailDrawer = false; openEdit(detail as NoticeRow)">编辑</v-btn>
        <v-btn v-if="detail && canHandle && detail.status === 'SENT'" variant="outlined" color="error" prepend-icon="undo" @click="detailDrawer = false; openAction(detail as NoticeRow, 'revoke')">撤回通知</v-btn>
      </template>
    </zw-drawer>

    <!-- 发送确认 -->
    <zw-dialog v-model="sendConfirmOpen" title="发送这条通知？" width="440" icon="send" tone="primary">
      <p class="notice-dialog-text">
        将立即发给 <b>{{ formatNumber(audience?.account_count || 0) }}</b> 个账号。
        <template v-if="form.level === 'IMPORTANT'">重要通知不受免打扰限制，会立即推送。</template>
      </p>
      <p v-if="formError" class="notice-dialog-error">{{ formError }}</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="saving" @click="sendConfirmOpen = false">返回修改</v-btn>
        <v-btn color="primary" :loading="saving" @click="doSubmit(pendingAction)">发送</v-btn>
      </template>
    </zw-dialog>

    <!-- 取消 / 撤回 / 删除 -->
    <zw-dialog
      v-model="actionOpen"
      :title="actionKind === 'cancel' ? '取消发送这条通知？' : actionKind === 'revoke' ? '撤回这条通知？' : '删除这条草稿？'"
      width="480"
      :icon="actionKind === 'delete' ? 'delete' : actionKind === 'revoke' ? 'undo' : 'schedule'"
      :tone="actionKind === 'delete' || actionKind === 'revoke' ? 'error' : 'warning'"
    >
      <template v-if="actionRow">
        <p class="notice-dialog-strong">{{ actionRow.title }}</p>
        <p v-if="actionKind === 'cancel'" class="notice-dialog-text">取消后不会发送和推送，状态变为“已取消”。可以复制为新通知重新编辑。</p>
        <p v-else-if="actionKind === 'revoke'" class="notice-dialog-text">已送达 {{ formatNumber(actionRow.account_count || 0) }} 个账号。撤回后，未读用户不再看到这条通知；已读用户打开时看到“该通知已撤回”，内容不再显示。撤回后不能恢复。</p>
        <p v-else class="notice-dialog-text">删除后不能恢复，操作记入操作日志。</p>
        <zw-form-item v-if="actionKind !== 'delete'" label="原因" required hint="原因记入操作日志，用户端不显示">
          <v-textarea v-model="actionReason" :placeholder="actionKind === 'cancel' ? '请填写取消原因' : '请填写撤回原因'" :rows="2" maxlength="100" counter />
        </zw-form-item>
      </template>
      <div v-if="actionError" class="notice-dialog-error">{{ actionError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="actionSaving" @click="actionOpen = false">取消</v-btn>
        <v-btn :color="actionKind === 'cancel' ? 'error' : 'error'" :loading="actionSaving" @click="confirmAction">
          {{ actionKind === 'delete' ? '删除' : actionKind === 'cancel' ? '取消发送' : '撤回' }}
        </v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.notice-stats { margin-bottom: 16px; }
.notice-toolbar { padding: 16px; }
.notice-toolbar :deep(.v-field) { height: 38px; }
.notice-title-cell { min-width: 220px; }
.notice-title-line { display: flex; align-items: center; gap: 6px; min-width: 0; }
.notice-title { max-width: 360px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; cursor: pointer; }
.notice-title:hover { color: var(--primary); }
.notice-sub { margin-top: 2px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.notice-scope { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 180px; }
.notice-table :deep(tbody tr) { height: 56px; }
.read-cell { display: flex; align-items: center; gap: 8px; }
.read-bar { width: 70px; flex-shrink: 0; }
.read-text { width: 46px; text-align: right; font-size: 12px; }
.notice-form-layout { display: grid; grid-template-columns: minmax(0, 1fr) 248px; gap: 24px; align-items: start; }
.notice-form-main { display: flex; flex-direction: column; gap: 18px; }
.notice-form-row { display: grid; grid-template-columns: 172px minmax(0, 1fr); gap: 16px; }
.notice-upload { display: flex; flex-direction: column; gap: 6px; }
.notice-upload-box { width: 160px; height: 90px; border: 1px dashed var(--line-2); border-radius: var(--radius-sm); background: var(--fill-1); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; font-size: 13px; color: var(--text-2); cursor: pointer; }
.notice-upload-box:hover { border-color: var(--primary); color: var(--primary); }
.notice-upload-box .notice-sub { text-align: center; font-size: 11px; }
.notice-image { position: relative; width: 160px; height: 90px; border-radius: var(--radius-sm); overflow: hidden; background: var(--fill-2); }
.notice-image img { width: 100%; height: 100%; object-fit: cover; display: block; }
.notice-image-del { position: absolute; right: 4px; top: 4px; width: 20px; height: 20px; border: 0; border-radius: 50%; background: rgba(0, 0, 0, 0.6); color: #fff; display: flex; align-items: center; justify-content: center; cursor: pointer; }
.notice-upload-progress { width: 160px; }
.notice-upload-error { font-size: 12px; color: var(--error); display: flex; gap: 8px; align-items: center; }
.notice-editor { border: 1px solid var(--line-2); border-radius: var(--radius-sm); overflow: hidden; }
.notice-editor-bar { height: 36px; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 0 12px; background: var(--fill-1); font-size: 12px; color: var(--text-2); }
.notice-textarea :deep(.v-field__outline) { display: none; }
.notice-textarea :deep(.v-field) { border-radius: 0; }
.notice-textarea :deep(.v-field__input) { min-height: 196px; font-size: 14px; line-height: 22px; }
.notice-scope-radio { margin-bottom: 4px; }
.notice-scope-radio :deep(.v-label) { font-size: 14px; }
.notice-condition { background: var(--fill-1); border: 1px solid var(--line-1); border-radius: var(--radius-sm); padding: 12px 16px; display: flex; flex-direction: column; gap: 8px; }
.notice-condition-row { display: flex; align-items: center; gap: 10px; min-height: 32px; }
.notice-condition-label { width: 40px; flex-shrink: 0; color: var(--text-2); font-size: 13px; }
.notice-condition-select { max-width: 420px; }
.notice-farm-picked { display: flex; flex-wrap: wrap; gap: 6px; min-height: 0; }
.notice-farm-remove { margin-left: 4px; cursor: pointer; color: var(--text-3); }
.notice-farm-options { max-height: 128px; overflow: auto; border-top: 1px solid var(--line-1); padding-top: 6px; }
.notice-farm-option { display: block; width: 100%; text-align: left; border: 0; background: transparent; padding: 7px 8px; border-radius: var(--radius-xs); font-size: 13px; cursor: pointer; }
.notice-farm-option:hover { background: var(--primary-subtle); color: var(--primary); }
.notice-audience { display: flex; align-items: center; gap: 4px; font-size: 13px; color: var(--text-2); margin-top: 4px; }
.notice-schedule { display: flex; align-items: center; gap: 12px; }
.notice-datetime { max-width: 220px; }
.notice-preview-title { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-3); margin-bottom: 8px; }
.notice-preview-phone { border: 1px solid var(--line-1); border-radius: 12px; background: var(--fill-1); padding: 14px; min-height: 510px; }
.notice-preview-head { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-2); }
.notice-preview-tags { display: flex; gap: 6px; align-items: center; margin-top: 14px; flex-wrap: wrap; }
.notice-preview-important { height: 20px; padding: 0 6px; border-radius: 2px; display: inline-flex; align-items: center; gap: 2px; background: var(--primary-container); color: var(--on-primary-container); font-size: 11px; }
.notice-preview-normal { font-size: 11px; color: var(--text-3); }
.notice-preview-h { font-size: 18px; line-height: 26px; font-weight: 600; margin-top: 8px; word-break: break-all; }
.notice-preview-date { margin-top: 4px; font-size: 11px; color: var(--text-3); }
.notice-preview-img { width: 100%; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 6px; margin-top: 10px; }
.notice-preview-body { margin-top: 10px; font-size: 12px; line-height: 20px; color: var(--text-2); white-space: pre-wrap; overflow: hidden; max-height: 300px; }
.notice-detail-tags { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
.notice-detail-desc { margin-bottom: 18px; }
.notice-stat-at { margin-left: 8px; font-size: 12px; font-weight: 400; }
.notice-read-stats { margin-bottom: 16px; }
.notice-role-stats { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
.notice-role-row { display: flex; align-items: center; gap: 12px; font-size: 13px; }
.notice-role-name { width: 52px; color: var(--text-2); }
.notice-role-bar { max-width: 360px; }
.notice-revoked { display: flex; align-items: center; gap: 6px; padding: 10px 12px; background: var(--warning-container); border-radius: var(--radius-sm); color: var(--on-warning-container); font-size: 13px; margin-bottom: 16px; }
.notice-detail-content { white-space: pre-wrap; font-size: 14px; line-height: 24px; color: var(--text-1); padding: 14px 16px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--bg-card); }
.notice-detail-image { max-width: 100%; border-radius: var(--radius-sm); margin-top: 12px; }
.notice-dialog-strong { font-size: 15px; font-weight: 600; margin: 0 0 8px; word-break: break-all; }
.notice-dialog-text { margin: 0 0 14px; font-size: 13px; line-height: 21px; color: var(--text-2); }
.notice-dialog-error { margin: 8px 0 0; font-size: 12px; color: var(--error); }
</style>
