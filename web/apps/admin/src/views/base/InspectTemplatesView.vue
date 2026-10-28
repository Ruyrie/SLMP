<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { routeQueryId } from '@/utils/routeQuery'
import { ApiError, formatDate, formatDateTime, formatNumber } from '@zmt/shared'
import {
  addInspectItem,
  createInspectTemplate,
  deleteInspectItem,
  discardInspectDraft,
  getInspectTemplate,
  getInspectTemplates,
  getInspectVersions,
  orderInspectItems,
  publishInspectTemplate,
  rollbackInspectTemplate,
  updateInspectItem,
  type InspectItem,
  type InspectItemPayload,
  type InspectTemplate,
  type InspectTemplateListItem,
  type TemplateVersion
} from '@/api/base'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const route = useRoute()
const canHandle = computed(() => admin.hasPerm('WA-D04:HANDLE'))

const templates = ref<InspectTemplateListItem[]>([])
const listLoading = ref(true)
const listError = ref('')
const forbidden = ref(false)
const selectedId = ref<number | null>(null)
const detail = ref<InspectTemplate | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

async function loadList(keep = true): Promise<void> {
  listLoading.value = true
  listError.value = ''
  try {
    templates.value = (await getInspectTemplates()) || []
    const all = templates.value.filter((item) => item.template_id)
    if (!keep || !all.some((item) => item.template_id === selectedId.value)) {
      if (all[0]?.template_id) await selectTemplate(all[0].template_id)
      else {
        selectedId.value = null
        detail.value = null
      }
    }
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) forbidden.value = true
    listError.value = e instanceof Error ? e.message : '模板加载失败'
  } finally {
    listLoading.value = false
  }
}

async function selectTemplate(id: number): Promise<void> {
  selectedId.value = id
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    detail.value = await getInspectTemplate(id)
  } catch (e) {
    detailError.value = e instanceof Error ? e.message : '模板详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail(): Promise<void> {
  if (!selectedId.value) return
  await Promise.all([selectTemplate(selectedId.value), loadList(true)])
}

/* ---------------- 新建模板 ---------------- */
const createOpen = ref(false)
const createSaving = ref(false)
const createError = ref('')
const createForm = reactive({ category_id: null as number | null, name: '' })
const availableCategories = computed(() => templates.value.filter((item) => !item.template_id).map((item) => ({ title: item.category_name, value: item.category_id })))

function openCreate(): void {
  createForm.category_id = availableCategories.value[0]?.value ?? null
  createForm.name = ''
  createError.value = ''
  createOpen.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.category_id) {
    createError.value = '请选择品类'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    const data = await createInspectTemplate({ category_id: createForm.category_id, name: createForm.name.trim() || undefined })
    createOpen.value = false
    ui.toast(`已保存“${data.name}”`)
    selectedId.value = data.template_id
    await Promise.all([loadList(true), selectTemplate(data.template_id)])
  } catch (e) {
    createError.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    createSaving.value = false
  }
}

/* ---------------- 项目编辑 ---------------- */
const itemOpen = ref(false)
const itemSaving = ref(false)
const itemError = ref('')
const itemEditing = ref<InspectItem | null>(null)
const itemForm = reactive({
  item_name: '',
  check_note: '',
  enabled: true,
  disable_reason: '',
  options: [] as Array<{ option_id?: number; option_name: string; is_abnormal: boolean }>
})

function openItemEdit(item?: InspectItem): void {
  itemEditing.value = item || null
  itemForm.item_name = item?.item_name || ''
  itemForm.check_note = item?.check_note || ''
  itemForm.enabled = item?.enabled ?? true
  itemForm.disable_reason = item?.disable_reason || ''
  itemForm.options = item?.options?.length
    ? item.options.map((option) => ({ option_id: option.option_id, option_name: option.option_name, is_abnormal: !!option.is_abnormal }))
    : [{ option_name: '正常', is_abnormal: false }, { option_name: '', is_abnormal: true }]
  itemError.value = ''
  itemOpen.value = true
}

function addOption(): void {
  if (itemForm.options.length >= 6) return
  itemForm.options.push({ option_name: '', is_abnormal: itemForm.options.length > 0 })
}

function removeOption(index: number): void {
  itemForm.options.splice(index, 1)
}

function moveOption(index: number, direction: -1 | 1): void {
  const target = index + direction
  if (target < 0 || target >= itemForm.options.length) return
  const [option] = itemForm.options.splice(index, 1)
  if (!option) return
  itemForm.options.splice(target, 0, option)
}

function validateItem(): boolean {
  itemError.value = ''
  if (!itemForm.item_name.trim()) {
    itemError.value = '请填写项目名称'
    return false
  }
  if (itemForm.item_name.trim().length > 6) {
    itemError.value = '项目名称最多 6 个字'
    return false
  }
  if (itemForm.options.length < 2 || itemForm.options.length > 6) {
    itemError.value = '结果数量必须为 2–6 个'
    return false
  }
  for (const option of itemForm.options) {
    if (!option.option_name.trim()) {
      itemError.value = '请填写结果名称'
      return false
    }
    if (option.option_name.trim().length > 6) {
      itemError.value = '结果名称最多 6 个字'
      return false
    }
  }
  if (itemForm.options[0]?.is_abnormal) {
    itemError.value = '第一个结果须判定为正常'
    return false
  }
  if (!itemForm.options.some((option) => !option.is_abnormal)) {
    itemError.value = '至少保留 1 个判定为正常的结果'
    return false
  }
  if (!itemForm.enabled && !itemForm.disable_reason.trim()) {
    itemError.value = '请填写停用原因'
    return false
  }
  return true
}

function buildPayload(): InspectItemPayload {
  return {
    item_name: itemForm.item_name.trim(),
    check_note: itemForm.check_note.trim() || undefined,
    enabled: itemForm.enabled,
    disable_reason: !itemForm.enabled ? itemForm.disable_reason.trim() : undefined,
    options: itemForm.options.map((option) => ({
      option_id: option.option_id,
      option_name: option.option_name.trim(),
      is_abnormal: option.is_abnormal
    })),
    version: detail.value?.version || 0
  }
}

async function submitItem(): Promise<void> {
  if (!selectedId.value || !validateItem()) return
  itemSaving.value = true
  itemError.value = ''
  try {
    if (itemEditing.value) await updateInspectItem(selectedId.value, itemEditing.value.item_id, buildPayload())
    else await addInspectItem(selectedId.value, buildPayload())
    itemOpen.value = false
    ui.toast('已保存，发布后生效')
    await refreshDetail()
  } catch (e) {
    if (e instanceof ApiError) {
      itemError.value = e.message
    } else {
      itemError.value = e instanceof Error ? e.message : '保存失败'
    }
  } finally {
    itemSaving.value = false
  }
}

const deleteOpen = ref(false)
const deleteTarget = ref<InspectItem | null>(null)
const deleteSaving = ref(false)

function openDelete(item: InspectItem): void {
  deleteTarget.value = item
  deleteOpen.value = true
}

async function confirmDelete(): Promise<void> {
  if (!selectedId.value || !deleteTarget.value) return
  deleteSaving.value = true
  try {
    await deleteInspectItem(selectedId.value, deleteTarget.value.item_id, detail.value?.version || 0)
    deleteOpen.value = false
    ui.toast('已删除，发布后生效')
    await refreshDetail()
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '删除失败', 'error')
  } finally {
    deleteSaving.value = false
  }
}

async function moveItem(index: number, direction: -1 | 1): Promise<void> {
  if (!detail.value || !selectedId.value) return
  const target = index + direction
  if (target < 0 || target >= (detail.value.items?.length || 0)) return
  const items = [...(detail.value.items || [])]
  const [item] = items.splice(index, 1)
  if (!item) return
  items.splice(target, 0, item)
  try {
    await orderInspectItems(selectedId.value, items.map((entry) => entry.item_id), detail.value.version || 0)
    ui.toast('已调整顺序，发布后生效')
    await refreshDetail()
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '调整顺序失败', 'error')
  }
}

/* ---------------- 发布 / 放弃 / 版本 ---------------- */
const publishOpen = ref(false)
const publishNote = ref('')
const publishError = ref('')
const publishSaving = ref(false)

function openPublish(): void {
  publishNote.value = ''
  publishError.value = ''
  publishOpen.value = true
}

async function submitPublish(): Promise<void> {
  if (!selectedId.value || !detail.value) return
  if (!publishNote.value.trim()) {
    publishError.value = '请填写修改说明'
    return
  }
  publishSaving.value = true
  publishError.value = ''
  try {
    const version = await publishInspectTemplate(selectedId.value, publishNote.value.trim(), detail.value.version || 0)
    publishOpen.value = false
    ui.toast(`已发布第 ${version.version_no} 版，下一次登记巡检起生效`)
    await refreshDetail()
  } catch (e) {
    publishError.value = e instanceof Error ? e.message : '发布失败'
  } finally {
    publishSaving.value = false
  }
}

const discardOpen = ref(false)
const discardSaving = ref(false)

async function confirmDiscard(): Promise<void> {
  if (!selectedId.value || !detail.value) return
  discardSaving.value = true
  try {
    await discardInspectDraft(selectedId.value, detail.value.version || 0)
    discardOpen.value = false
    ui.toast('已放弃未发布的修改')
    await refreshDetail()
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '放弃失败', 'error')
  } finally {
    discardSaving.value = false
  }
}

const versionOpen = ref(false)
const versions = ref<TemplateVersion[]>([])
const versionsLoading = ref(false)
const versionError = ref('')
const versionTarget = ref<TemplateVersion | null>(null)
const rollbackSaving = ref(false)

async function openVersions(): Promise<void> {
  if (!selectedId.value) return
  versionOpen.value = true
  versionsLoading.value = true
  versionError.value = ''
  versions.value = []
  versionTarget.value = null
  try {
    versions.value = await getInspectVersions(selectedId.value)
    versionTarget.value = versions.value[0] || null
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : '版本加载失败'
  } finally {
    versionsLoading.value = false
  }
}

async function submitRollback(): Promise<void> {
  if (!selectedId.value || !detail.value || !versionTarget.value) return
  rollbackSaving.value = true
  versionError.value = ''
  try {
    await rollbackInspectTemplate(selectedId.value, versionTarget.value.version_no, detail.value.version || 0)
    versionOpen.value = false
    ui.toast(`已回退到第 ${versionTarget.value.version_no} 版的内容，发布后生效`)
    await refreshDetail()
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : '回退失败'
  } finally {
    rollbackSaving.value = false
  }
}

function changeLabel(item: InspectItem): string {
  return { ADDED: '新增', MODIFIED: '已修改', DELETED: '已删除' }[item.change_type || 'NONE'] || ''
}

onMounted(async () => {
  const id = routeQueryId(route)
  if (id) {
    await selectTemplate(id)
    await loadList(true)
  } else {
    await loadList(false)
  }
})
</script>

<template>
  <zw-shell side="admin" active="WA-D04" :crumbs="['基础数据', '巡检项目模板']">
    <zw-page-head title="巡检项目模板" desc="按品类配置登记巡检时逐项检查的项目和可选结果。每项必选一个结果，选到判定为“异常”的结果时，这次巡检自动标为异常。发布修改记入操作日志。">
      <template #actions>
        <v-btn v-if="canHandle" color="primary" prepend-icon="add" @click="openCreate">新建模板</v-btn>
      </template>
    </zw-page-head>

    <zw-notice v-if="forbidden" type="error" title="你没有巡检模板的查看权限">请联系超级管理员在“管理员与权限”中开通 WA-D04 的查看权限。</zw-notice>

    <div v-if="!forbidden" class="inspect-layout">
      <zw-card flush class="inspect-list">
        <v-skeleton-loader v-if="listLoading" type="list-item@6" />
        <zw-empty v-else-if="listError" icon="cloud_off" title="模板加载失败" :desc="listError" tone="error">
          <v-btn variant="outlined" @click="loadList(false)">重新加载</v-btn>
        </zw-empty>
        <template v-else>
          <div v-for="item in templates" :key="item.category_id" class="inspect-list-item" :class="{ on: selectedId === item.template_id, muted: !item.template_id }" @click="item.template_id && selectTemplate(item.template_id)">
            <div class="inspect-list-main">
              <div class="inspect-list-name">{{ item.category_name }}</div>
              <div class="inspect-list-sub">
                <template v-if="item.template_id">{{ item.item_count }} 项<template v-if="item.online_version_no"> · 第 {{ item.online_version_no }} 版</template></template>
                <template v-else>未配置模板</template>
              </div>
            </div>
            <zw-tag v-if="item.has_draft" type="warning" size="small">有修改</zw-tag>
          </div>
        </template>
        <div class="inspect-list-foot">
          <v-btn v-if="canHandle" variant="outlined" size="small" block prepend-icon="add" :disabled="!availableCategories.length" @click="openCreate">新建模板</v-btn>
        </div>
      </zw-card>

      <zw-card v-if="detailLoading || detailError || !detail" flush class="inspect-detail">
        <v-skeleton-loader v-if="detailLoading" type="article@6" />
        <zw-empty v-else-if="detailError" icon="cloud_off" title="模板详情加载失败" :desc="detailError" tone="error">
          <v-btn variant="outlined" @click="selectedId && selectTemplate(selectedId)">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else icon="fact_check" title="选择一个巡检模板" desc="从左侧选择品类，编辑巡检项目和结果。" />
      </zw-card>
      <zw-card v-else flush class="inspect-detail">
        <div class="inspect-detail-head">
          <div>
            <div class="inspect-detail-name">{{ detail.name }}<zw-tag v-if="detail.online_version_no" type="success" icon="check" size="small">已发布</zw-tag></div>
            <div class="inspect-meta">
              第 {{ detail.online_version_no || 0 }} 版<template v-if="detail.published_at"> · {{ formatDate(detail.published_at) }} {{ detail.published_by_name || '' }}发布</template>
              <template v-if="detail.pen_count"> · 用于 {{ formatNumber(detail.pen_count) }} 个育肥猪栏舍</template>
            </div>
          </div>
          <div class="inspect-actions">
            <v-btn variant="outlined" size="small" prepend-icon="history" @click="openVersions">版本记录</v-btn>
            <v-btn v-if="canHandle" color="primary" size="small" prepend-icon="add" @click="openItemEdit()">添加项目</v-btn>
          </div>
        </div>

        <div v-if="detail.has_draft" class="inspect-draft">
          <zw-notice type="warning" :title="`有 ${detail.draft_changes?.length || 1} 项修改还没有发布`">
            <span v-for="(change, index) in detail.draft_changes || []" :key="index">
              {{ { ADDED: '新增项目', MODIFIED: '修改项目', DELETED: '删除项目' }[change.change_type] || change.change_type }}“{{ change.item_name }}”{{ change.field_name ? '：' + change.field_name : '' }}
              <template v-if="change.before || change.after"> {{ change.before || '—' }} → {{ change.after || '—' }}</template><span v-if="index < (detail.draft_changes?.length || 0) - 1">；</span>
            </span>
            发布前用户端仍按第 {{ detail.online_version_no || 0 }} 版显示 {{ detail.preview_items?.length || 0 }} 个项目。
            <template #action>
              <v-btn v-if="canHandle" variant="text" size="small" color="primary" @click="openPublish">发布修改</v-btn>
              <v-btn v-if="canHandle" variant="text" size="small" color="error" @click="discardOpen = true">放弃修改</v-btn>
            </template>
          </zw-notice>
        </div>

        <div class="inspect-detail-body">
          <div class="inspect-items">
            <div v-for="(item, index) in detail.items || []" :key="item.item_id" class="inspect-item" :class="{ 'inspect-item--added': item.change_type === 'ADDED', 'inspect-item--disabled': !item.enabled }">
              <div class="inspect-item-sort">
                <zw-icon name="drag_indicator" :size="16" />
                <span class="num">{{ index + 1 }}</span>
                <span class="inspect-sort-btns">
                  <a :class="{ disabled: index === 0 }" @click="index > 0 && moveItem(index, -1)">↑</a>
                  <a :class="{ disabled: index === (detail.items?.length || 0) - 1 }" @click="index < (detail.items?.length || 0) - 1 && moveItem(index, 1)">↓</a>
                </span>
              </div>
              <div class="inspect-item-main">
                <div class="inspect-item-name">
                  {{ item.item_name }}
                  <zw-tag v-if="item.change_type && item.change_type !== 'NONE'" type="warning" size="small">{{ changeLabel(item as InspectItem) }}</zw-tag>
                  <zw-tag v-if="!item.enabled" size="small">已停用</zw-tag>
                </div>
                <div v-if="item.check_note" class="inspect-sub">{{ item.check_note }}</div>
                <div class="inspect-options">
                  <span v-for="option in item.options" :key="option.option_id || option.option_name" class="inspect-option" :class="{ 'inspect-option--abnormal': option.is_abnormal }">
                    <zw-icon v-if="!option.is_abnormal" name="check" :size="12" />{{ option.option_name }}
                  </span>
                </div>
              </div>
              <div class="zw-cell-actions">
                <a v-if="canHandle" class="zw-link" @click="openItemEdit(item as InspectItem)">编辑</a>
                <a v-if="canHandle && item.change_type === 'ADDED'" class="zw-link danger" @click="openDelete(item as InspectItem)">删除</a>
                <a v-else-if="canHandle && !item.enabled" class="zw-link" @click="openItemEdit(item as InspectItem)">启用</a>
              </div>
            </div>
            <zw-empty v-if="!detail.items?.length" icon="fact_check" title="还没有巡检项目" desc="添加项目并发布后，用户端登记巡检时才会显示。">
              <v-btn v-if="canHandle" color="primary" @click="openItemEdit()">添加项目</v-btn>
            </zw-empty>
          </div>
          <div class="inspect-legend">正常标签“判定为正常”；异常标签“判定为异常，选中时这次巡检自动标为异常”。拖动或使用上下按钮调整项目在用户端的顺序。</div>
          <div v-if="detail.preview_items?.length" class="inspect-preview">
            <div class="inspect-preview-head"><zw-icon name="smartphone" :size="14" />用户端预览（登记巡检 · 巡检项目）</div>
            <div class="inspect-preview-body">
              <div class="inspect-preview-title">巡检项目 <span>每项必选</span></div>
              <div v-for="item in detail.preview_items" :key="item.item_id" class="inspect-preview-item">
                <div class="inspect-preview-name">{{ item.item_name }}</div>
                <div class="inspect-preview-options">
                  <span v-for="option in item.options" :key="option.option_id" class="inspect-preview-chip" :class="{ abnormal: option.is_abnormal }">{{ option.option_name }}</span>
                </div>
              </div>
            </div>
            <div class="inspect-sub">只显示已发布的项目；未发布、已停用的项目用户端看不到。</div>
          </div>
        </div>

        <div v-if="detail.has_draft" class="inspect-publish-foot">
          <span class="zw-foot-note">保存后在模板中标为“未发布”</span>
          <v-btn v-if="canHandle" variant="outlined" size="small" @click="discardOpen = true">放弃修改</v-btn>
          <v-btn v-if="canHandle" color="primary" size="small" prepend-icon="publish" @click="openPublish">发布修改</v-btn>
        </div>
      </zw-card>
    </div>

    <!-- 新建模板 -->
    <zw-drawer v-model="createOpen" title="新建巡检模板" sub="每个启用品类最多一个模板" width="560">
      <div class="zw-form">
        <zw-form-item label="品类" required>
          <v-select v-model="createForm.category_id" :items="availableCategories" placeholder="请选择未配置的品类" />
        </zw-form-item>
        <zw-form-item label="模板名称" optional hint="为空时使用“品类名 + 巡检模板”">
          <v-text-field v-model="createForm.name" placeholder="例如：种猪巡检模板" maxlength="20" />
        </zw-form-item>
        <div v-if="createError" class="inspect-error">{{ createError }}</div>
      </div>
      <template #foot>
        <span class="zw-foot-note">新建后需要添加项目并发布</span>
        <v-btn variant="outlined" :disabled="createSaving" @click="createOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="createSaving" @click="submitCreate">保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 项目编辑 -->
    <zw-drawer v-model="itemOpen" :title="itemEditing ? '编辑巡检项目' : '添加巡检项目'" :sub="detail ? `${detail.name} · 保存后需要发布才对用户端生效` : ''" width="560">
      <div class="zw-form-2">
        <zw-form-item label="项目名称" required hint="最多 6 个字">
          <v-text-field v-model="itemForm.item_name" maxlength="6" placeholder="例如：精神状态" />
        </zw-form-item>
        <zw-form-item label="检查说明" optional hint="管理端备注，用户端不显示">
          <v-text-field v-model="itemForm.check_note" maxlength="50" placeholder="例如：看站立、走动和对人的反应" />
        </zw-form-item>
      </div>
      <div class="zw-section-title">可选结果与异常判定 <span class="inspect-sub">2–6 个结果；至少 1 个判定为“正常”，排在第一位</span></div>
      <div class="inspect-option-edit">
        <div v-for="(option, index) in itemForm.options" :key="index" class="inspect-option-row">
          <zw-icon name="drag_indicator" :size="16" />
          <v-text-field v-model="option.option_name" density="compact" hide-details maxlength="6" placeholder="结果名称" />
          <zw-seg :model-value="option.is_abnormal ? 'ABNORMAL' : 'NORMAL'" size="small" :items="[{ label: '正常', value: 'NORMAL' }, { label: '异常', value: 'ABNORMAL' }]" @update:model-value="option.is_abnormal = $event === 'ABNORMAL'" />
          <div class="inspect-option-order"><a :class="{ disabled: index === 0 }" @click="index > 0 && moveOption(index, -1)">↑</a><a :class="{ disabled: index === itemForm.options.length - 1 }" @click="index < itemForm.options.length - 1 && moveOption(index, 1)">↓</a></div>
          <v-btn icon="delete" variant="text" size="small" :disabled="itemForm.options.length <= 2" @click="removeOption(index)" />
        </div>
        <v-btn variant="text" color="primary" prepend-icon="add" :disabled="itemForm.options.length >= 6" @click="addOption">添加结果</v-btn>
      </div>
      <div class="inspect-enable-row">
        <div><div>启用</div><div class="inspect-sub">停用后用户端不再显示这一项，已提交的巡检记录保留原结果</div></div>
        <v-switch v-model="itemForm.enabled" color="primary" density="compact" hide-details />
      </div>
      <zw-form-item v-if="!itemForm.enabled" label="停用原因" required>
        <v-textarea v-model="itemForm.disable_reason" placeholder="请填写停用原因" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <zw-notice icon="report">选到异常结果时，用户端提示“粪便为‘水样’，已自动标为异常。……”并出现“转上报”；多项异常时依次列出。</zw-notice>
      <div v-if="itemError" class="inspect-error">{{ itemError }}</div>
      <template #foot>
        <span class="zw-foot-note">保存后在模板中标为“未发布”</span>
        <v-btn variant="outlined" :disabled="itemSaving" @click="itemOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="itemSaving" @click="submitItem">保存</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog v-model="deleteOpen" title="删除这个巡检项目？" width="440" icon="delete" tone="error">
      <p class="inspect-dialog-text">{{ deleteTarget?.item_name }}</p>
      <p class="inspect-dialog-text">只有未发布的新增项目可以删除；已发布项目请在编辑中停用。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="deleteSaving" @click="deleteOpen = false">取消</v-btn>
        <v-btn color="error" :loading="deleteSaving" @click="confirmDelete">删除</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="publishOpen" title="发布巡检模板修改？" width="480" icon="publish" tone="primary">
      <p class="inspect-dialog-text">{{ detail?.name }} 将从第 {{ detail?.online_version_no || 0 }} 版更新为第 {{ (detail?.online_version_no || 0) + 1 }} 版。发布后，用户端下一次登记巡检起显示新的项目和结果；已提交的巡检记录不变。</p>
      <zw-form-item label="修改说明" required>
        <v-textarea v-model="publishNote" placeholder="请填写修改说明" :rows="3" maxlength="100" counter />
      </zw-form-item>
      <div v-if="publishError" class="inspect-error">{{ publishError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="publishSaving" @click="publishOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="publishSaving" @click="submitPublish">发布第 {{ (detail?.online_version_no || 0) + 1 }} 版</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="discardOpen" title="放弃未发布的修改？" width="440" icon="undo" tone="warning">
      <p class="inspect-dialog-text">放弃后恢复线上版本，未发布的修改不能恢复。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="discardSaving" @click="discardOpen = false">继续保留</v-btn>
        <v-btn color="error" :loading="discardSaving" @click="confirmDiscard">放弃修改</v-btn>
      </template>
    </zw-dialog>

    <zw-drawer v-model="versionOpen" :title="`版本记录 · ${detail?.name || ''}`" sub="回退会把历史版本项目复制为新草稿，发布后生效" width="640">
      <v-skeleton-loader v-if="versionsLoading" type="paragraph@5" />
      <template v-else>
        <div v-if="versionError" class="inspect-error">{{ versionError }}</div>
        <button v-for="version in versions" :key="version.version_id" type="button" class="inspect-version-item" :class="{ on: versionTarget?.version_id === version.version_id }" @click="versionTarget = version">
          <span class="inspect-version-name">第 {{ version.version_no }} 版 <zw-tag :type="version.status === 'PUBLISHED' ? 'success' : 'neutral'" size="small">{{ version.status_text || version.status }}</zw-tag></span>
          <span class="zw-muted">{{ version.published_at ? formatDateTime(version.published_at) : '' }} · {{ version.published_by_name || '' }} · {{ version.item_count || 0 }} 项</span>
          <span class="inspect-version-note">{{ version.change_note || '—' }}</span>
        </button>
        <div v-if="!versions.length" class="zw-muted">还没有版本记录</div>
        <div v-if="versionTarget && versionTarget.version_no !== detail?.online_version_no" class="inspect-rollback">
          <div class="zw-section-title">回退到第 {{ versionTarget.version_no }} 版</div>
          <p class="zw-muted">回退会复制该版本的项目与结果为新草稿，发布后生效。</p>
          <v-btn color="primary" variant="outlined" prepend-icon="history" :loading="rollbackSaving" @click="submitRollback">回退到这一版</v-btn>
        </div>
      </template>
      <template #foot><v-btn variant="outlined" @click="versionOpen = false">关闭</v-btn></template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.inspect-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 16px; align-items: start; }
.inspect-list { min-height: 580px; display: flex; flex-direction: column; }
.inspect-list-item { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-bottom: 1px solid var(--line-1); cursor: pointer; }
.inspect-list-item:hover { background: var(--fill-1); }
.inspect-list-item.on { background: var(--primary-subtle); }
.inspect-list-item.muted { color: var(--text-3); }
.inspect-list-main { flex: 1; min-width: 0; }
.inspect-list-name { font-size: 14px; font-weight: 500; }
.inspect-list-sub { font-size: 12px; color: var(--text-3); }
.inspect-list-foot { margin-top: auto; padding: 12px 16px; border-top: 1px solid var(--line-1); }
.inspect-detail { min-height: 580px; }
.inspect-detail-head { display: flex; align-items: flex-start; gap: 12px; padding: 18px 20px 14px; border-bottom: 1px solid var(--line-1); }
.inspect-detail-head > div:first-child { flex: 1; min-width: 0; }
.inspect-detail-name { display: flex; align-items: center; gap: 8px; font-size: 18px; font-weight: 600; }
.inspect-meta { margin-top: 3px; font-size: 12px; color: var(--text-2); }
.inspect-actions { display: flex; gap: 8px; }
.inspect-draft { padding: 12px 20px 0; }
.inspect-detail-body { padding: 16px 20px 20px; }
.inspect-items { border-top: 1px solid var(--line-1); }
.inspect-item { display: flex; align-items: flex-start; gap: 12px; padding: 14px 4px; border-bottom: 1px solid var(--line-1); }
.inspect-item--added { background: var(--primary-subtle); }
.inspect-item--disabled { color: var(--text-3); }
.inspect-item-sort { width: 56px; display: flex; align-items: center; gap: 3px; color: var(--text-3); font-size: 12px; padding-top: 2px; }
.inspect-sort-btns { display: flex; flex-direction: column; line-height: 10px; }
.inspect-sort-btns a { font-size: 10px; cursor: pointer; }
.inspect-sort-btns a.disabled { opacity: 0.3; cursor: default; }
.inspect-item-main { flex: 1; min-width: 0; }
.inspect-item-name { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 500; }
.inspect-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.inspect-options { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.inspect-option { height: 24px; display: inline-flex; align-items: center; gap: 2px; padding: 0 8px; border: 1px solid var(--line-2); border-radius: 2px; font-size: 12px; color: var(--text-1); background: var(--bg-card); }
.inspect-option--abnormal { background: var(--error-container); color: var(--on-error-container); border-color: transparent; }
.inspect-legend { padding: 10px 0 0; font-size: 12px; color: var(--text-3); }
.inspect-preview { margin-top: 18px; border: 1px solid var(--line-1); border-radius: 10px; padding: 14px; background: #F8FAFD; max-width: 520px; }
.inspect-preview-head { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-3); }
.inspect-preview-title { margin-top: 10px; font-size: 14px; font-weight: 600; }
.inspect-preview-title span { font-size: 12px; font-weight: 400; color: var(--text-3); margin-left: 6px; }
.inspect-preview-item { padding: 10px 0; border-bottom: 1px dashed var(--line-1); }
.inspect-preview-name { font-size: 13px; }
.inspect-preview-options { display: flex; gap: 6px; margin-top: 6px; flex-wrap: wrap; }
.inspect-preview-chip { height: 26px; padding: 0 10px; border-radius: 6px; border: 1px solid #747775; background: #fff; font-size: 12px; color: #444746; display: inline-flex; align-items: center; }
.inspect-preview-chip.abnormal { background: var(--error-container); border-color: transparent; color: var(--on-error-container); }
.inspect-publish-foot { display: flex; align-items: center; justify-content: flex-end; gap: 8px; padding: 12px 20px; border-top: 1px solid var(--line-1); background: var(--fill-1); }
.inspect-error { margin-top: 8px; font-size: 12px; color: var(--error); }
.inspect-option-edit { display: flex; flex-direction: column; gap: 8px; padding: 12px 0; border-top: 1px solid var(--line-1); border-bottom: 1px solid var(--line-1); margin: 8px 0 16px; }
.inspect-option-row { display: flex; align-items: center; gap: 8px; }
.inspect-option-row .v-text-field { flex: 1; }
.inspect-option-order { display: flex; flex-direction: column; line-height: 11px; }
.inspect-option-order a { font-size: 10px; color: var(--text-3); cursor: pointer; }
.inspect-option-order a.disabled { opacity: 0.3; cursor: default; }
.inspect-enable-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0 16px; }
.inspect-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); }
.inspect-version-item { width: 100%; display: flex; flex-direction: column; gap: 3px; padding: 10px 14px; border: 1px solid var(--line-1); background: var(--bg-card); border-radius: var(--radius-sm); margin-bottom: 6px; text-align: left; font: inherit; cursor: pointer; }
.inspect-version-item.on { border-color: var(--primary); background: var(--primary-subtle); }
.inspect-version-name { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 500; }
.inspect-version-note { font-size: 12px; color: var(--text-2); }
.inspect-rollback { margin-top: 14px; border-top: 1px solid var(--line-1); padding-top: 10px; }
</style>
