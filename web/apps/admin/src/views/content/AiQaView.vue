<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { dayjs, formatDate, formatDateTime, formatNumber, formatPercent, today } from '@zmt/shared'
import {
  createAiQaTask,
  exportAiQa,
  getAiQaDetail,
  getAiQaList,
  getAiQaSummary,
  getAiQaTaskSuggestion,
  getContentCategories,
  getKnowledgePage,
  markAiQaNoAction,
  publishAiCorrection,
  updateAiCorrection,
  withdrawAiCorrection,
  type AiQaSummary,
  type ContentCategory,
  type QaDetail,
  type QaRow
} from '@/api/content'
import { getAdmins } from '@/api/system'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const canHandle = computed(() => admin.hasPerm('WA-C05:HANDLE'))

const summary = ref<AiQaSummary | null>(null)
const list = ref<QaRow[]>([])
const total = ref(0)
const loading = ref(true)
const listLoading = ref(false)
const error = ref('')
const forbidden = ref(false)
const tabCounts = reactive<Record<string, number>>({ ALL: 0, PENDING_USELESS: 0, NO_BASIS: 0, SPECIAL: 0, CORRECTED: 0 })
const query = reactive({
  segment: 'ALL',
  keyword: '',
  date_from: dayjs().tz('Asia/Shanghai').subtract(6, 'day').format('YYYY-MM-DD'),
  date_to: today(),
  is_spot_check: false,
  page: 1,
  size: 20
})
const categories = ref<ContentCategory[]>([])
const knowledgeOptions = ref<Array<{ title: string; value: number }>>([])
const adminOptions = ref<Array<{ title: string; value: number }>>([])
const tabs = computed(() => [
  { label: '全部', value: 'ALL', count: tabCounts.ALL || 0 },
  { label: '没用待处理', value: 'PENDING_USELESS', count: tabCounts.PENDING_USELESS || 0 },
  { label: '未找到依据', value: 'NO_BASIS', count: tabCounts.NO_BASIS || 0 },
  { label: '特殊回答', value: 'SPECIAL', count: tabCounts.SPECIAL || 0 },
  { label: '已更正', value: 'CORRECTED', count: tabCounts.CORRECTED || 0 }
])
const headers = [
  { title: '问题', key: 'question_text', sortable: false, minWidth: 300 },
  { title: '养殖场 · 提问人', key: 'asker', sortable: false, width: 152 },
  { title: '关联批次', key: 'batch_no', sortable: false, width: 104 },
  { title: '回答', key: 'answer', sortable: false, width: 124 },
  { title: '评价', key: 'rating', sortable: false, width: 76 },
  { title: '处理', key: 'handle', sortable: false, width: 104 },
  { title: '提问时间', key: 'asked_at', sortable: false, width: 116 },
  { title: '操作', key: 'actions', sortable: false, width: 116, align: 'end' as const }
]

function answerTone(type?: string): string {
  if (type === 'BANNED_DRUG' || type === 'EPIDEMIC') return 'error'
  return 'neutral'
}

function answerIcon(type?: string): string {
  if (type === 'BANNED_DRUG') return 'block'
  if (type === 'EPIDEMIC') return 'emergency'
  return 'info'
}

function answerLabel(row: QaRow): string {
  return row.answer_type_text || (row.answer_type === 'NORMAL' ? '普通' : '—')
}

function handleTone(status?: string | null): string {
  if (status === 'CORRECTED') return 'primary'
  if (status === 'PENDING') return 'warning'
  return 'neutral'
}

function handleIcon(status?: string | null): string {
  if (status === 'CORRECTED') return 'verified'
  if (status === 'PENDING') return 'pending'
  if (status === 'TASKED') return 'task_alt'
  return 'remove'
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await getAiQaSummary({ date_from: query.date_from, date_to: query.date_to })
  } catch (e) {
    if (e instanceof Error && 'isForbidden' in e) forbidden.value = true
  }
}

async function loadOptions(): Promise<void> {
  try {
    const [categoryData, adminData, knowledgeData] = await Promise.all([
      getContentCategories('ENABLED'),
      getAdmins(),
      getKnowledgePage({ status: 'ON_SHELF', page: 1, size: 100 })
    ])
    categories.value = categoryData || []
    adminOptions.value = (adminData.list || []).map((item) => ({ title: item.name, value: item.admin_id }))
    knowledgeOptions.value = (knowledgeData.list || []).map((item) => ({ title: item.title, value: item.id }))
  } catch {
    categories.value = []
  }
}

async function fetchList(): Promise<void> {
  listLoading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const data = await getAiQaList({
      segment: query.segment,
      keyword: query.keyword.trim() || undefined,
      date_from: query.date_from,
      date_to: query.date_to,
      is_spot_check: query.is_spot_check || undefined,
      page: query.page,
      size: query.size
    })
    list.value = data.list || []
    total.value = data.total || 0
    Object.assign(tabCounts, data.tab_counts || {})
  } catch (e) {
    if (e instanceof Error && 'isForbidden' in e) forbidden.value = true
    error.value = e instanceof Error ? e.message : '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
    listLoading.value = false
  }
}

function applySegment(value: string): void {
  query.segment = value
  query.page = 1
  void fetchList()
}

function applyDates(): void {
  if (!query.date_from || !query.date_to) return
  query.page = 1
  void Promise.all([fetchList(), loadSummary()])
}

function resetFilters(): void {
  query.segment = 'ALL'
  query.keyword = ''
  query.date_from = dayjs().tz('Asia/Shanghai').subtract(6, 'day').format('YYYY-MM-DD')
  query.date_to = today()
  query.is_spot_check = false
  query.page = 1
  void Promise.all([fetchList(), loadSummary()])
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

/* ---------------- 详情 ---------------- */
const drawer = ref(false)
const detailLoading = ref(false)
const detail = ref<QaDetail | null>(null)
const detailError = ref('')

async function openDetail(row: QaRow): Promise<void> {
  drawer.value = true
  detailLoading.value = true
  detail.value = null
  detailError.value = ''
  try {
    detail.value = await getAiQaDetail(row.message_id)
    if (detail.value?.correction) correctionContent.value = detail.value.correction.content
  } catch (e) {
    detailError.value = e instanceof Error ? e.message : '详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function safeImage(url?: string): string {
  if (!url) return ''
  try {
    return new URL(url).hostname.endsWith('.example.com') ? '' : url
  } catch {
    return ''
  }
}

async function openAndCorrect(row: QaRow): Promise<void> {
  await openDetail(row)
  if (detail.value) openCorrection()
}

async function openAndTask(row: QaRow): Promise<void> {
  await openDetail(row)
  if (detail.value) await openTask()
}

/* ---------------- 更正说明 ---------------- */
const correctionOpen = ref(false)
const correctionMode = ref<'create' | 'edit'>('create')
const correctionContent = ref('')
const correctionError = ref('')
const correctionSaving = ref(false)
const correctionCreateTask = ref(false)
const correctionTaskTitle = ref('')
const correctionAssignee = ref<number | null>(null)
const correctionTargetKnowledge = ref<number | null>(null)

function openCorrection(): void {
  if (!detail.value) return
  correctionMode.value = detail.value.correction ? 'edit' : 'create'
  correctionContent.value = detail.value.correction?.content || ''
  correctionError.value = ''
  correctionCreateTask.value = false
  correctionTaskTitle.value = detail.value.suggest_task_target?.title || ''
  correctionAssignee.value = null
  correctionTargetKnowledge.value = detail.value.suggest_task_target?.knowledge_id || null
  correctionOpen.value = true
}

async function submitCorrection(): Promise<void> {
  if (!detail.value) return
  if (!correctionContent.value.trim()) {
    correctionError.value = '请填写更正说明'
    return
  }
  if (correctionContent.value.trim().length > 200) {
    correctionError.value = '更正说明不能超过 200 字'
    return
  }
  if (correctionCreateTask.value && !correctionTaskTitle.value.trim()) {
    correctionError.value = '请填写知识补充任务的标题'
    return
  }
  if (correctionCreateTask.value && !correctionAssignee.value) {
    correctionError.value = '请选择知识补充任务的指派人'
    return
  }
  correctionSaving.value = true
  correctionError.value = ''
  try {
    if (correctionMode.value === 'edit') {
      await updateAiCorrection(detail.value.message_id, correctionContent.value.trim(), detail.value.correction?.version)
      ui.toast('更正说明已修改')
    } else {
      await publishAiCorrection(detail.value.message_id, {
        content: correctionContent.value.trim(),
        create_task: correctionCreateTask.value,
        task_target_knowledge_id: correctionTargetKnowledge.value,
        task_title: correctionTaskTitle.value.trim() || undefined,
        assignee_id: correctionAssignee.value || undefined
      })
      ui.toast('已发布更正说明，提问人再次打开会话时可以看到')
    }
    correctionOpen.value = false
    await refreshDetail()
    await fetchList()
  } catch (e) {
    correctionError.value = e instanceof Error ? e.message : '发布失败'
  } finally {
    correctionSaving.value = false
  }
}

async function refreshDetail(): Promise<void> {
  if (!detail.value) return
  detail.value = await getAiQaDetail(detail.value.message_id)
}

const withdrawOpen = ref(false)
const withdrawReason = ref('')
const withdrawError = ref('')
const withdrawSaving = ref(false)

function openWithdraw(): void {
  withdrawReason.value = ''
  withdrawError.value = ''
  withdrawOpen.value = true
}

async function submitWithdraw(): Promise<void> {
  if (!detail.value?.correction) return
  if (!withdrawReason.value.trim()) {
    withdrawError.value = '请填写撤回原因'
    return
  }
  withdrawSaving.value = true
  withdrawError.value = ''
  try {
    await withdrawAiCorrection(detail.value.message_id, withdrawReason.value.trim(), detail.value.correction.version)
    withdrawOpen.value = false
    ui.toast('已撤回更正说明')
    await refreshDetail()
    await fetchList()
  } catch (e) {
    withdrawError.value = e instanceof Error ? e.message : '撤回失败'
  } finally {
    withdrawSaving.value = false
  }
}

/* ---------------- 无需处理 ---------------- */
const noActionOpen = ref(false)
const noActionSaving = ref(false)

async function confirmNoAction(): Promise<void> {
  if (!detail.value) return
  noActionSaving.value = true
  try {
    await markAiQaNoAction(detail.value.message_id, detail.value.correction?.version)
    noActionOpen.value = false
    ui.toast('已标为无需处理')
    await refreshDetail()
    await fetchList()
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '操作失败', 'error')
  } finally {
    noActionSaving.value = false
  }
}

/* ---------------- 转知识补充任务 ---------------- */
const taskOpen = ref(false)
const taskSaving = ref(false)
const taskError = ref('')
const taskForm = reactive({
  title: '',
  mode: 'NEW_ENTRY',
  target_knowledge_id: null as number | null,
  category_id: null as number | null,
  assignee_id: null as number | null,
  due_date: dayjs().tz('Asia/Shanghai').add(7, 'day').format('YYYY-MM-DD'),
  points: '',
  similar_count: 0
})

async function openTask(): Promise<void> {
  if (!detail.value) return
  taskError.value = ''
  taskForm.title = detail.value.suggest_task_target?.title || detail.value.rounds?.[0]?.question_text?.slice(0, 20) || ''
  taskForm.mode = detail.value.suggest_task_target?.knowledge_id ? 'SUPPLEMENT' : 'NEW_ENTRY'
  taskForm.target_knowledge_id = detail.value.suggest_task_target?.knowledge_id || null
  taskForm.category_id = categories.value[0]?.id || null
  taskForm.assignee_id = null
  taskForm.due_date = dayjs().tz('Asia/Shanghai').add(7, 'day').format('YYYY-MM-DD')
  taskForm.points = ''
  taskForm.similar_count = 0
  taskOpen.value = true
  try {
    const suggestion = await getAiQaTaskSuggestion(detail.value.message_id)
    taskForm.title = suggestion.suggested_title || taskForm.title
    taskForm.category_id = suggestion.suggested_category_id || taskForm.category_id
    taskForm.similar_count = suggestion.similar_count_30d || 0
  } catch {
    /* 建议生成失败不阻塞 */
  }
}

async function submitTask(): Promise<void> {
  if (!detail.value) return
  if (!taskForm.title.trim()) {
    taskError.value = '请填写任务标题'
    return
  }
  if (!taskForm.category_id) {
    taskError.value = '请选择分类'
    return
  }
  if (!taskForm.assignee_id) {
    taskError.value = '请选择指派给'
    return
  }
  if (!taskForm.points.trim()) {
    taskError.value = '请填写补充要点'
    return
  }
  taskSaving.value = true
  taskError.value = ''
  try {
    await createAiQaTask(detail.value.message_id, {
      title: taskForm.title.trim(),
      mode: taskForm.mode,
      target_knowledge_id: taskForm.mode === 'SUPPLEMENT' ? taskForm.target_knowledge_id : null,
      category_id: taskForm.category_id!,
      assignee_id: taskForm.assignee_id,
      due_date: taskForm.due_date,
      points: taskForm.points.trim(),
      similar_count: taskForm.similar_count
    })
    taskOpen.value = false
    ui.toast('已创建知识补充任务')
    await refreshDetail()
    await fetchList()
  } catch (e) {
    taskError.value = e instanceof Error ? e.message : '创建任务失败'
  } finally {
    taskSaving.value = false
  }
}

async function exportRecords(): Promise<void> {
  try {
    const result = await exportAiQa({
      segment: query.segment,
      keyword: query.keyword.trim() || undefined,
      date_from: query.date_from,
      date_to: query.date_to,
      is_spot_check: query.is_spot_check || undefined
    })
    if (result.url) window.open(result.url, '_blank', 'noopener')
    ui.toast(`已导出 ${formatNumber(result.row_count || 0)} 行问答记录`)
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '导出失败', 'error')
  }
}

function ratingText(row: QaRow): string {
  if (!row.can_rate) return '不能评价'
  return row.rating_text || '未评价'
}

function cleanAnswer(text?: string): string {
  return (text || '—').replace(/\[\^\d+\]/g, '')
}

onMounted(async () => {
  loading.value = true
  void loadOptions()
  await Promise.all([fetchList(), loadSummary()])
  loading.value = false
})
</script>

<template>
  <zw-shell side="admin" active="WA-C05" :crumbs="['内容运营', 'AI 问答与更正']">
    <zw-page-head title="AI 问答与更正" desc="回答不准确时补充更正说明，提问人再次打开会话时在原回答下方看到“平台更正说明”；缺少依据的问题可以转为知识补充任务。">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="download" :disabled="!canHandle" @click="exportRecords">导出记录</v-btn>
      </template>
    </zw-page-head>

    <zw-notice v-if="forbidden" type="error" title="你没有 AI 问答的查看权限">请联系超级管理员在“管理员与权限”中开通 WA-C05 的查看权限。</zw-notice>

    <div v-if="!forbidden" class="zw-grid-4 ai-stats">
      <zw-stat label="近 7 天问答" icon="forum" :value="formatNumber(summary?.qa_count || 0)" unit="次" :foot="`提问人 ${formatNumber(summary?.asker_count || 0)} 人`" />
      <zw-stat label="有用率" icon="thumb_up" :value="summary?.useful_rate !== undefined ? formatPercent(summary.useful_rate) : '—'" :foot="`已评价 ${formatNumber(summary?.rated_count || 0)} 次，有用 ${formatNumber(summary?.useful_count || 0)} 次`" info="有用次数 ÷ 已评价次数；拒答的回答不能评价，不计入" />
      <zw-stat label="评价为“没用”待处理" icon="thumb_down" tone="warning" :value="formatNumber(summary?.pending_useless_count || 0)" unit="条" foot="补充更正或标为无需处理后移出" />
      <zw-stat label="未找到依据" icon="travel_explore" :value="formatNumber(summary?.no_basis_count || 0)" unit="次" :foot="`占 ${formatPercent(summary?.no_basis_ratio, 1)}，可转为知识补充任务`" />
    </div>

    <zw-card v-if="!forbidden" flush>
      <div class="zw-toolbar ai-toolbar">
        <div class="zw-toolbar-l">
          <zw-seg :items="tabs" :model-value="query.segment" @update:model-value="applySegment(String($event))" />
        </div>
        <div class="zw-toolbar-r ai-filters">
          <v-text-field v-model="query.keyword" class="zw-w-240" placeholder="搜索问题、养殖场或提问人" prepend-inner-icon="search" density="compact" hide-details clearable @keydown.enter="fetchList()" @click:clear="fetchList()" />
          <v-text-field v-model="query.date_from" type="date" class="ai-date" density="compact" hide-details @update:model-value="applyDates" />
          <span class="zw-muted">至</span>
          <v-text-field v-model="query.date_to" type="date" class="ai-date" density="compact" hide-details @update:model-value="applyDates" />
          <v-checkbox v-model="query.is_spot_check" label="只看抽查" density="compact" hide-details @update:model-value="fetchList()" />
        </div>
      </div>
      <v-skeleton-loader v-if="loading" type="table-row@7" />
      <zw-empty v-else-if="error" icon="cloud_off" title="问答记录加载失败" :desc="error" tone="error">
        <v-btn variant="outlined" @click="fetchList()">重新加载</v-btn>
      </zw-empty>
      <zw-empty v-else-if="!list.length && (query.segment !== 'ALL' || query.keyword || query.is_spot_check)" icon="search_off" title="没有符合条件的问答" desc="换一个分段或关键词试试。">
        <v-btn variant="text" color="primary" @click="resetFilters">清除筛选</v-btn>
      </zw-empty>
      <zw-empty v-else-if="!list.length" icon="forum" title="近 7 天没有问答记录" desc="用户向 AI 提问后，问答会出现在这里。" />
      <template v-else>
        <v-data-table :headers="headers" :items="list" item-key="message_id" hide-default-footer density="compact" :loading="listLoading" class="ai-table">
          <template #item.question_text="{ item }">
            <div class="ai-question-line">
              <span class="ai-question" :title="item.question_text">{{ item.question_text }}</span>
              <zw-icon v-if="item.image_count" name="image" :size="15" color="text-3" />
              <zw-tag v-if="item.is_spot_check" type="warning" size="small">抽查</zw-tag>
            </div>
          </template>
          <template #item.asker="{ item }"><span>{{ item.farm_name || '—' }}</span><span class="zw-muted"> · {{ item.asker_name || '—' }}</span></template>
          <template #item.batch_no="{ item }"><span v-if="item.batch_no" class="num">{{ item.batch_no }}</span><span v-else class="zw-faint">未关联</span></template>
          <template #item.answer="{ item }">
            <zw-tag v-if="item.answer_type && item.answer_type !== 'NORMAL'" :type="answerTone(item.answer_type)" :icon="answerIcon(item.answer_type)" size="small">{{ answerLabel(item as QaRow) }}</zw-tag>
            <span v-else class="zw-muted">普通</span>
          </template>
          <template #item.rating="{ item }">
            <span v-if="item.rating === 'USELESS'" class="ai-rating ai-rating--bad"><zw-icon name="thumb_down" :size="14" />没用</span>
            <span v-else-if="item.rating === 'USEFUL'" class="ai-rating"><zw-icon name="thumb_up" :size="14" />有用</span>
            <span v-else class="zw-faint">{{ ratingText(item as QaRow) }}</span>
          </template>
          <template #item.handle="{ item }">
            <zw-tag v-if="item.handle_status" :type="handleTone(item.handle_status)" :icon="handleIcon(item.handle_status)" size="small">{{ item.handle_status_text }}</zw-tag>
            <span v-else class="zw-faint">—</span>
          </template>
          <template #item.asked_at="{ item }">
            <div>{{ item.asked_at ? dayjs(item.asked_at).format('M月D日') : '—' }}</div>
            <div class="ai-sub">{{ item.asked_at ? dayjs(item.asked_at).format('HH:mm') : '' }}</div>
          </template>
          <template #item.actions="{ item }">
            <div class="zw-cell-actions">
              <a class="zw-link" @click="openDetail(item as QaRow)">查看</a>
              <a v-if="item.handle_status === 'PENDING' && item.answer_type !== 'NO_BASIS' && canHandle" class="zw-link" @click="openAndCorrect(item as QaRow)">补充更正</a>
              <a v-if="item.handle_status === 'PENDING' && item.answer_type === 'NO_BASIS' && canHandle" class="zw-link" @click="openAndTask(item as QaRow)">转知识任务</a>
            </div>
          </template>
        </v-data-table>
        <div class="zw-table-foot">
          <span class="zw-muted">只显示问答内容和评价；提问人的批次数据在详情中按提问时的快照显示</span>
          <zw-pager :total="total" :page="query.page" :size="query.size" @update:page="changePage" @update:size="changeSize" />
        </div>
      </template>
    </zw-card>

    <!-- 详情 -->
    <zw-drawer v-model="drawer" title="问答详情" :sub="detail?.session ? `${detail.session.farm_name || ''} · ${detail.session.asker_name || ''}${detail.session.asker_role ? '（' + detail.session.asker_role + '）' : ''} · ${detail.session.started_at ? formatDateTime(detail.session.started_at) : ''} · 会话共 ${detail.session.round_count || 0} 轮` : ''" width="760">
      <v-skeleton-loader v-if="detailLoading" type="paragraph@9" />
      <zw-empty v-else-if="detailError" icon="cloud_off" title="详情加载失败" :desc="detailError" tone="error" />
      <template v-else-if="detail">
        <div v-if="detail.context" class="ai-context">
          <div v-if="detail.context.batch_no">
            关联批次 <span class="num">{{ detail.context.batch_no }}</span> · {{ detail.context.breed_name || detail.context.breed_text || '—' }} · {{ detail.context.day_age || '—' }} 日龄 · 存栏 {{ formatNumber(detail.context.current_count || 0) }} {{ detail.context.count_unit_text || '头' }}<template v-if="detail.context.pen_name"> · {{ detail.context.pen_name }}</template>
          </div>
          <div v-if="detail.context.withdrawal_drugs?.length">
            提问时的用药记录：{{ detail.context.withdrawal_drugs.map((drug) => typeof drug === 'string' ? drug : `${drug.name || '—'}${drug.last_used_at ? ' ' + formatDate(drug.last_used_at) : ''}${drug.withdrawal_days !== undefined ? '（休药 ' + drug.withdrawal_days + ' 天）' : ''}`).join('；') }}
          </div>
          <div v-if="detail.context.sellable_date">按提问时档案，最早可出栏 {{ formatDate(detail.context.sellable_date) }}</div>
        </div>

        <div v-for="round in detail.rounds || []" :key="round.message_id" class="ai-round">
          <div class="ai-round-time">{{ formatDateTime(round.asked_at) }} · 第 {{ round.round_no }} 轮</div>
          <div class="ai-question-bubble">{{ round.question_text }}</div>
          <div v-if="round.images?.length" class="ai-round-images">
            <img v-for="image in round.images.filter((item) => !!safeImage(item.url))" :key="image.file_id" :src="safeImage(image.url)" alt="提问附图" />
            <div v-if="!round.images.some((item) => !!safeImage(item.url))" class="ai-sub">附图 {{ round.images.length }} 张</div>
          </div>
          <div class="ai-answer">
            <span class="ai-avatar"><zw-icon name="auto_awesome" :size="18" fill /></span>
            <div class="ai-answer-main">
              <div v-if="round.answer_type && round.answer_type !== 'NORMAL'" class="ai-special">
                <zw-tag :type="answerTone(round.answer_type)" :icon="answerIcon(round.answer_type)" size="small">{{ round.answer_type_text }}</zw-tag>
              </div>
              <div class="ai-answer-text">{{ cleanAnswer(round.answer_text) }}</div>
              <div v-if="round.citations?.length" class="ai-citations">
                <span v-for="citation in round.citations" :key="citation.seq_no" class="ai-citation"><zw-icon :name="citation.icon_type === 'STANDARD' ? 'description' : 'menu_book'" :size="14" />{{ citation.seq_no }} {{ citation.title }}</span>
              </div>
              <div class="ai-answer-foot">
                <span><zw-icon name="info" :size="14" />由 AI 生成，仅供参考</span>
                <span v-if="round.rating" :class="{ 'ai-rating--bad': round.rating === 'USELESS' }" class="ai-rating">{{ round.rating_text }}<span v-if="round.rated_at" class="zw-muted"> · {{ dayjs(round.rated_at).format('HH:mm') }}</span></span>
                <span v-else class="zw-muted">{{ round.rating_text || '未评价' }}</span>
              </div>
            </div>
          </div>
        </div>

        <zw-notice v-if="detail.spot_check" type="warning" icon="fact_check" :title="`抽查提示：${detail.spot_check.title || ''}`">
          {{ detail.spot_check.content || '' }}<span v-if="detail.spot_check.checked_at" class="zw-muted"> · {{ formatDateTime(detail.spot_check.checked_at) }}</span>
        </zw-notice>

        <template v-if="detail.correction">
          <div class="zw-section-title">平台更正说明</div>
          <div class="ai-correction-card">
            <div class="ai-correction-head"><zw-icon name="verified" :size="18" fill />平台更正说明 · {{ detail.correction.published_at ? formatDateTime(detail.correction.published_at) : '' }}</div>
            <div class="ai-correction-content">{{ detail.correction.content }}</div>
            <div class="ai-sub">发布人 {{ detail.correction.published_by_name || '—' }} · {{ detail.correction.status_text || detail.correction.status }}</div>
          </div>
          <zw-form-item v-if="canHandle && detail.can_update_correction" label="修改更正说明">
            <v-textarea v-model="correctionContent" :rows="3" maxlength="200" counter />
          </zw-form-item>
          <div v-if="correctionError && correctionMode === 'edit'" class="ai-form-error">{{ correctionError }}</div>
        </template>
        <zw-notice v-if="!detail.correction" type="info" title="补充更正说明">
          原回答保持原样，更正说明显示在原回答下方，不单独推送消息；提问人再次打开会话时可以看到。
        </zw-notice>

        <template v-if="detail.task">
          <div class="zw-section-title">知识补充任务</div>
          <div class="ai-task-card">
            <div><b>{{ detail.task.title }}</b> · <span class="zw-muted">{{ detail.task.status_text || detail.task.status }}</span></div>
            <div class="ai-sub">指派给 {{ detail.task.assignee_name || '—' }} · 期望完成 {{ detail.task.due_date ? formatDate(detail.task.due_date) : '—' }}</div>
          </div>
        </template>
      </template>

      <template #foot>
        <span class="zw-foot-note">发布记入操作日志；发布后可以修改或撤回更正说明</span>
        <v-btn variant="outlined" @click="drawer = false">关闭</v-btn>
        <template v-if="detail && canHandle">
          <v-btn v-if="detail.can_no_action || detail.handle_status === 'PENDING'" variant="outlined" @click="noActionOpen = true">标为无需处理</v-btn>
          <v-btn v-if="detail.can_create_task || detail.handle_status === 'PENDING'" variant="outlined" @click="openTask">转知识任务</v-btn>
          <v-btn v-if="detail.correction && detail.can_update_correction" variant="outlined" @click="openCorrection">修改更正说明</v-btn>
          <v-btn v-if="detail.correction" variant="outlined" color="error" @click="openWithdraw">撤回更正说明</v-btn>
          <v-btn v-if="!detail.correction" color="primary" prepend-icon="verified" @click="openCorrection">补充更正说明</v-btn>
        </template>
      </template>
    </zw-drawer>

    <!-- 更正说明 -->
    <zw-dialog v-model="correctionOpen" :title="correctionMode === 'edit' ? '修改更正说明' : '补充更正说明'" width="760" icon="verified" tone="primary">
      <p class="ai-dialog-text">原回答保持原样，更正说明显示在原回答下方；发布后记入操作日志。</p>
      <div class="ai-correction-layout">
        <div>
          <zw-form-item label="更正说明" required :error="correctionError">
            <v-textarea v-model="correctionContent" placeholder="请填写更正说明，最多 200 字" :rows="5" maxlength="200" counter />
          </zw-form-item>
          <template v-if="correctionMode === 'create'">
            <v-checkbox v-model="correctionCreateTask" label="同时生成知识补充任务" density="compact" hide-details />
            <template v-if="correctionCreateTask">
              <div class="zw-form-2 ai-task-form">
                <zw-form-item label="目标条目" optional>
                  <v-select v-model="correctionTargetKnowledge" :items="knowledgeOptions" clearable placeholder="选择要补充的条目" />
                </zw-form-item>
                <zw-form-item label="任务标题" required>
                  <v-text-field v-model="correctionTaskTitle" placeholder="最多 40 字" maxlength="40" />
                </zw-form-item>
                <zw-form-item label="指派给" required>
                  <v-select v-model="correctionAssignee" :items="adminOptions" placeholder="请选择管理员" />
                </zw-form-item>
              </div>
            </template>
          </template>
          <zw-notice type="info">发布后，提问人再次打开这个会话时在原回答下方看到“平台更正说明”，历史会话中这个会话标出“有更正说明”。不单独推送消息。</zw-notice>
        </div>
        <aside class="ai-preview">
          <div class="ai-preview-title"><zw-icon name="smartphone" :size="14" />用户端预览</div>
          <div class="ai-preview-card">
            <div class="ai-preview-head"><zw-icon name="verified" :size="18" fill />平台更正说明 · {{ dayjs().format('M月D日 HH:mm') }}</div>
            <div class="ai-preview-body">{{ correctionContent || '更正说明会显示在这里。' }}</div>
          </div>
        </aside>
      </div>
      <div v-if="correctionError" class="ai-form-error">{{ correctionError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="correctionSaving" @click="correctionOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="correctionSaving" @click="submitCorrection">{{ correctionMode === 'edit' ? '保存修改' : '发布更正说明' }}</v-btn>
      </template>
    </zw-dialog>

    <!-- 撤回更正 -->
    <zw-dialog v-model="withdrawOpen" title="撤回更正说明？" width="440" icon="undo" tone="error">
      <p class="ai-dialog-text">撤回后用户端更正卡消失，这条问答的处理状态回到待处理。撤回原因记入操作日志。</p>
      <zw-form-item label="撤回原因" required>
        <v-textarea v-model="withdrawReason" placeholder="请填写撤回原因" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <div v-if="withdrawError" class="ai-form-error">{{ withdrawError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="withdrawSaving" @click="withdrawOpen = false">取消</v-btn>
        <v-btn color="error" :loading="withdrawSaving" @click="submitWithdraw">撤回</v-btn>
      </template>
    </zw-dialog>

    <!-- 无需处理 -->
    <zw-dialog v-model="noActionOpen" title="标为无需处理？" width="440" icon="remove_done" tone="primary">
      <p class="ai-dialog-text">标为无需处理后，这条问答移出“没用待处理”，不再需要补充更正或转任务。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="noActionSaving" @click="noActionOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="noActionSaving" @click="confirmNoAction">确认</v-btn>
      </template>
    </zw-dialog>

    <!-- 转知识任务 -->
    <zw-dialog v-model="taskOpen" title="转为知识补充任务" width="640" icon="task_alt" tone="primary">
      <div v-if="detail?.rounds?.length" class="ai-task-source">
        <div class="ai-task-source-q">{{ detail.rounds[0]?.question_text }}</div>
        <div class="ai-sub">{{ detail.session?.farm_name }} · {{ detail.session?.asker_name }} · {{ detail.session?.started_at ? formatDateTime(detail.session.started_at) : '' }}</div>
      </div>
      <div v-if="taskForm.similar_count" class="ai-similar"><zw-icon name="travel_explore" :size="16" />近 30 天有 {{ taskForm.similar_count }} 个相似问题没有找到依据。</div>
      <div class="zw-form" style="margin-top: 16px">
        <div class="zw-form-2">
          <zw-form-item label="补充方式" required>
            <v-select v-model="taskForm.mode" :items="[{ title: '新建条目', value: 'NEW_ENTRY' }, { title: '补充已有条目', value: 'SUPPLEMENT' }]" />
          </zw-form-item>
          <zw-form-item label="建议标题" required>
            <v-text-field v-model="taskForm.title" placeholder="最多 40 字" maxlength="40" />
          </zw-form-item>
          <zw-form-item label="分类" required>
            <v-select v-model="taskForm.category_id" :items="categories.map((c) => ({ title: c.name, value: c.id }))" placeholder="请选择分类" />
          </zw-form-item>
          <zw-form-item label="指派给" required>
            <v-select v-model="taskForm.assignee_id" :items="adminOptions" placeholder="请选择管理员" />
          </zw-form-item>
          <zw-form-item label="期望完成" required>
            <v-text-field v-model="taskForm.due_date" type="date" :min="today()" />
          </zw-form-item>
          <zw-form-item v-if="taskForm.mode === 'SUPPLEMENT'" label="目标条目" required>
            <v-select v-model="taskForm.target_knowledge_id" :items="knowledgeOptions" placeholder="请选择已有条目" />
          </zw-form-item>
        </div>
        <zw-form-item label="补充要点" required>
          <v-textarea v-model="taskForm.points" placeholder="写清需要补充的内容" :rows="3" maxlength="200" counter />
        </zw-form-item>
        <div v-if="taskError" class="ai-form-error">{{ taskError }}</div>
      </div>
      <template #actions>
        <v-btn variant="outlined" :disabled="taskSaving" @click="taskOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="taskSaving" @click="submitTask">创建任务</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.ai-stats { margin-bottom: 16px; }
.ai-toolbar { padding: 16px; flex-direction: column; align-items: stretch; gap: 10px; }
.ai-toolbar .zw-toolbar-l { width: 100%; flex-shrink: 0; }
.ai-toolbar :deep(.zw-seg) { flex-shrink: 0; max-width: 100%; overflow-x: auto; }
.ai-toolbar :deep(.zw-seg-item) { white-space: nowrap; }
.ai-filters { width: 100%; flex-wrap: wrap; justify-content: flex-start; }
.ai-date { max-width: 138px; }
.ai-table :deep(tbody tr) { height: 40px; }
.ai-table :deep(td) { white-space: nowrap; }
.ai-question-line { display: flex; align-items: center; gap: 5px; min-width: 0; }
.ai-question { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.ai-rating { display: inline-flex; align-items: center; gap: 2px; font-size: 12px; }
.ai-rating--bad { color: var(--error); }
.ai-context { padding: 10px 14px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 13px; line-height: 22px; color: var(--text-2); margin-bottom: 16px; }
.ai-round { margin-bottom: 18px; }
.ai-round-time { text-align: center; font-size: 12px; color: var(--text-3); margin-bottom: 10px; }
.ai-question-bubble { margin-left: auto; max-width: 92%; width: fit-content; padding: 10px 14px; background: var(--fill-2); border-radius: 20px; font-size: 14px; line-height: 22px; }
.ai-round-images { display: flex; justify-content: flex-end; gap: 6px; margin-top: 6px; }
.ai-round-images img { max-width: 140px; max-height: 100px; border-radius: var(--radius-sm); object-fit: cover; }
.ai-answer { display: flex; gap: 10px; margin-top: 12px; }
.ai-avatar { width: 28px; height: 28px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; background: var(--tertiary-container); color: var(--tertiary); }
.ai-answer-main { flex: 1; min-width: 0; border: 1px solid var(--line-1); border-radius: 8px; padding: 14px 16px; }
.ai-answer-text { font-size: 14px; line-height: 23px; white-space: pre-wrap; }
.ai-special { margin-bottom: 8px; }
.ai-citations { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.ai-citation { display: inline-flex; align-items: center; gap: 4px; height: 28px; padding: 0 8px; border: 1px solid var(--control-border); border-radius: 6px; font-size: 12px; color: var(--primary); }
.ai-answer-foot { display: flex; align-items: center; gap: 10px; margin-top: 10px; font-size: 12px; color: var(--text-3); }
.ai-answer-foot > span:first-child { display: inline-flex; align-items: center; gap: 3px; }
.ai-correction-card { border: 1px solid var(--primary); border-radius: 6px; background: var(--primary-subtle); padding: 12px 16px; margin-bottom: 12px; }
.ai-correction-head { display: flex; align-items: center; gap: 6px; color: var(--primary); font-size: 14px; font-weight: 500; }
.ai-correction-content { font-size: 14px; line-height: 23px; margin-top: 6px; }
.ai-task-card { padding: 10px 14px; background: var(--fill-1); border-radius: var(--radius-sm); font-size: 13px; }
.ai-form-error { margin-top: 8px; color: var(--error); font-size: 12px; }
.ai-correction-layout { display: grid; grid-template-columns: minmax(0, 1fr) 248px; gap: 20px; align-items: start; margin-bottom: 16px; }
.ai-task-form { margin-top: 10px; }
.ai-preview-title { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-3); margin-bottom: 6px; }
.ai-preview-card { background: var(--fill-1); border: 1px solid var(--line-1); border-radius: 10px; padding: 12px 14px; min-height: 120px; }
.ai-preview-head { display: flex; align-items: center; gap: 4px; color: var(--primary); font-size: 13px; font-weight: 500; }
.ai-preview-body { margin-top: 6px; font-size: 13px; line-height: 21px; white-space: pre-wrap; }
.ai-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); }
.ai-task-source { border: 1px solid var(--line-1); border-radius: var(--radius-sm); padding: 10px 14px; }
.ai-task-source-q { font-size: 14px; font-weight: 500; }
.ai-similar { display: flex; align-items: center; gap: 6px; margin-top: 10px; padding: 8px 12px; border-radius: var(--radius-sm); background: var(--warning-container); color: var(--on-warning-container); font-size: 13px; }
</style>
