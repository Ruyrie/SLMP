<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { routeQueryId } from '@/utils/routeQuery'
import { ApiError, formatDate, formatDateTime, formatNumber } from '@zmt/shared'
import {
  addImmuneItem,
  createImmuneTemplate,
  deleteImmuneItem,
  discardImmuneDraft,
  getDictItems,
  getDrugCatalog,
  getImmuneTemplate,
  getImmuneTemplates,
  getImmuneVersions,
  orderImmuneItems,
  publishImmuneTemplate,
  rollbackImmuneTemplate,
  updateImmuneItem,
  type DrugRow,
  type DictItem,
  type ImmuneItem,
  type ImmuneItemPayload,
  type ImmuneTemplate,
  type ImmuneTemplateGroup,
  type TemplateVersion
} from '@/api/base'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const route = useRoute()
const canHandle = computed(() => admin.hasPerm('WA-D02:HANDLE'))

const groups = ref<ImmuneTemplateGroup[]>([])
const listLoading = ref(true)
const listError = ref('')
const forbidden = ref(false)
const keyword = ref('')
const selectedId = ref<number | null>(null)
const detail = ref<ImmuneTemplate | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const vaccines = ref<DrugRow[]>([])
const methodDicts = ref<DictItem[]>([])

const filteredGroups = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  const result = groups.value.map((group) => ({
    ...group,
    breeds: group.items.filter((item) => !text || item.breed_name.toLowerCase().includes(text) || (item.name || '').toLowerCase().includes(text) || group.category_name.toLowerCase().includes(text))
  }))
  return result.filter((group) => group.breeds.length)
})

async function loadList(keep = true): Promise<void> {
  listLoading.value = true
  listError.value = ''
  try {
    groups.value = (await getImmuneTemplates()) || []
    const all = groups.value.flatMap((group) => group.items)
    if (!keep || !all.some((item) => item.template_id === selectedId.value)) {
      const first = all.find((item) => !!item.template_id)
      if (first?.template_id) await selectTemplate(first.template_id)
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
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await getImmuneTemplate(id)
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

async function loadOptions(): Promise<void> {
  try {
    const [vaccineData, methodData] = await Promise.all([
      getDrugCatalog({ drug_category: 'VACCINE', status: 'ENABLED', page: 1, size: 100 }),
      getDictItems('VACCINATE_METHOD')
    ])
    vaccines.value = (vaccineData.list || []).filter((item) => !item.banned)
    methodDicts.value = (methodData || []).filter((item) => item.status === 'ENABLED')
  } catch {
    vaccines.value = []
    methodDicts.value = []
  }
}

function findGroupItem(id: number | null): ImmuneTemplateGroup['items'][number] | null {
  if (!id) return null
  for (const group of groups.value) {
    const item = group.items.find((entry) => entry.template_id === id)
    if (item) return item
  }
  return null
}

/* ---------------- 新建模板 ---------------- */
const createOpen = ref(false)
const createSaving = ref(false)
const createError = ref('')
const createForm = reactive({ breed_id: null as number | null, name: '', copy_from_template_id: null as number | null })
const unconfigured = computed(() => groups.value.flatMap((group) => group.items.filter((item) => !item.template_id).map((item) => ({ title: `${group.category_name} · ${item.breed_name}`, value: item.breed_id }))))
const copyOptions = computed(() => groups.value.flatMap((group) => group.items.filter((item) => !!item.template_id).map((item) => ({ title: `${item.name || item.breed_name}（第 ${item.online_version_no || 0} 版）`, value: item.template_id! }))))

function openCreate(): void {
  createForm.breed_id = unconfigured.value[0]?.value ?? null
  createForm.name = ''
  createForm.copy_from_template_id = null
  createError.value = ''
  createOpen.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.breed_id) {
    createError.value = '请选择品种'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    const data = await createImmuneTemplate({
      breed_id: createForm.breed_id,
      name: createForm.name.trim() || undefined,
      copy_from_template_id: createForm.copy_from_template_id || undefined
    })
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
const itemEditing = ref<ImmuneItem | null>(null)
const itemFormErrors = reactive<Record<string, string>>({})
const itemForm = reactive({
  vaccine_id: null as number | null,
  dose_round: '',
  schedule_type: 'BY_DAY_AGE',
  day_age: null as number | null,
  plan_month: null as number | null,
  plan_day: null as number | null,
  advance_days: 7,
  dose: '',
  dose_unit: 'DOSE_PER_HEAD',
  vaccinate_method_code: 'IM',
  remark: ''
})

const doseUnitOptions = [
  { title: '头份 / 头', value: 'DOSE_PER_HEAD' },
  { title: '毫升 / 头', value: 'ML_PER_HEAD' },
  { title: '羽份 / 只', value: 'DOSE_PER_BIRD' }
]
const methodOptions = computed(() => methodDicts.value.map((item) => ({ title: item.name, value: item.code })))
const monthOptions = Array.from({ length: 12 }, (_, index) => ({ title: `${index + 1} 月`, value: index + 1 }))
const dayOptions = Array.from({ length: 31 }, (_, index) => ({ title: `${index + 1} 日`, value: index + 1 }))

function openItemEdit(item?: ImmuneItem): void {
  if (!detail.value) return
  itemEditing.value = item || null
  itemForm.vaccine_id = item?.vaccine_id || null
  itemForm.dose_round = item?.dose_round || ''
  itemForm.schedule_type = item?.schedule_type || 'BY_DAY_AGE'
  itemForm.day_age = item?.day_age || null
  itemForm.plan_month = item?.plan_month || null
  itemForm.plan_day = item?.plan_day || null
  itemForm.advance_days = item?.advance_days || 7
  itemForm.dose = item?.dose ? String(item.dose) : ''
  itemForm.dose_unit = item?.dose_unit || 'DOSE_PER_HEAD'
  itemForm.vaccinate_method_code = item?.vaccinate_method_code || methodDicts.value[0]?.code || 'IM'
  itemForm.remark = item?.remark || ''
  itemError.value = ''
  Object.keys(itemFormErrors).forEach((key) => delete itemFormErrors[key])
  itemOpen.value = true
}

function onVaccineChange(): void {
  const vaccine = vaccines.value.find((item) => item.id === itemForm.vaccine_id)
  if (!vaccine) return
  if (vaccine.vaccine_dose_unit) itemForm.dose_unit = vaccine.vaccine_dose_unit
  if (vaccine.default_vaccinate_method_code) itemForm.vaccinate_method_code = vaccine.default_vaccinate_method_code
}

function validateItem(): boolean {
  Object.keys(itemFormErrors).forEach((key) => delete itemFormErrors[key])
  itemError.value = ''
  if (!itemForm.vaccine_id) itemFormErrors.vaccine_id = '请选择疫苗'
  if (!itemForm.dose_round.trim()) itemFormErrors.dose_round = '请填写免次'
  if (itemForm.schedule_type === 'BY_DAY_AGE') {
    if (!itemForm.day_age || itemForm.day_age < 1 || itemForm.day_age > 999) itemFormErrors.day_age = '接种日龄必须在 1–999 之间'
  } else {
    if (!itemForm.plan_month || !itemForm.plan_day) itemFormErrors.plan_date = '请选择正确的日期'
    if (!itemForm.advance_days || itemForm.advance_days < 1 || itemForm.advance_days > 60) itemFormErrors.advance_days = '提前生成必须在 1–60 天之间'
  }
  if (!itemForm.dose || Number(itemForm.dose) <= 0) itemFormErrors.dose = '剂量必须大于 0'
  if (!itemForm.vaccinate_method_code) itemFormErrors.vaccinate_method_code = '请选择接种方式'
  return !Object.keys(itemFormErrors).length
}

function buildItemPayload(): ImmuneItemPayload {
  return {
    vaccine_id: itemForm.vaccine_id!,
    dose_round: itemForm.dose_round.trim(),
    schedule_type: itemForm.schedule_type,
    day_age: itemForm.schedule_type === 'BY_DAY_AGE' ? itemForm.day_age : null,
    plan_month: itemForm.schedule_type === 'BY_DATE' ? itemForm.plan_month : null,
    plan_day: itemForm.schedule_type === 'BY_DATE' ? itemForm.plan_day : null,
    advance_days: itemForm.schedule_type === 'BY_DATE' ? itemForm.advance_days : null,
    dose: itemForm.dose,
    dose_unit: itemForm.dose_unit,
    vaccinate_method_code: itemForm.vaccinate_method_code,
    remark: itemForm.remark.trim() || undefined,
    version: detail.value?.version || 0
  }
}

async function submitItem(): Promise<void> {
  if (!selectedId.value || !validateItem()) return
  itemSaving.value = true
  itemError.value = ''
  try {
    if (itemEditing.value) await updateImmuneItem(selectedId.value, itemEditing.value.item_id, buildItemPayload())
    else await addImmuneItem(selectedId.value, buildItemPayload())
    itemOpen.value = false
    ui.toast('已保存，发布后生效')
    await refreshDetail()
  } catch (e) {
    if (e instanceof ApiError) {
      for (const error of e.fieldErrors) itemFormErrors[error.field] = error.message
    }
    itemError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    itemSaving.value = false
  }
}

const deleteOpen = ref(false)
const deleteTarget = ref<ImmuneItem | null>(null)
const deleteSaving = ref(false)

function openDeleteItem(item: ImmuneItem): void {
  deleteTarget.value = item
  deleteOpen.value = true
}

async function confirmDeleteItem(): Promise<void> {
  if (!selectedId.value || !deleteTarget.value) return
  deleteSaving.value = true
  try {
    await deleteImmuneItem(selectedId.value, deleteTarget.value.item_id, detail.value?.version || 0)
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
    await orderImmuneItems(selectedId.value, items.map((entry) => entry.item_id), detail.value.version || 0)
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
    const version = await publishImmuneTemplate(selectedId.value, publishNote.value.trim(), detail.value.version || 0)
    publishOpen.value = false
    ui.toast(`已发布第 ${version.version_no} 版，之后入栏的批次按新模板生成免疫计划`)
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
    await discardImmuneDraft(selectedId.value, detail.value.version || 0)
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
    versions.value = await getImmuneVersions(selectedId.value)
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
    await rollbackImmuneTemplate(selectedId.value, versionTarget.value.version_no, detail.value.version || 0)
    versionOpen.value = false
    ui.toast(`已回退到第 ${versionTarget.value.version_no} 版的内容，发布后生效`)
    await refreshDetail()
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : '回退失败'
  } finally {
    rollbackSaving.value = false
  }
}

const timelineMax = computed(() => {
  const ages = (detail.value?.items || []).filter((item) => item.schedule_type === 'BY_DAY_AGE').map((item) => item.day_age || 0)
  return Math.max(180, ...ages) + 20
})

function timelineLeft(item: ImmuneItem): number {
  return Math.min(98, Math.max(2, ((item.day_age || 0) / timelineMax.value) * 100))
}

function scheduleText(item: ImmuneItem): string {
  if (item.schedule_type === 'BY_DAY_AGE') return `按日龄 · ${item.day_age} 日龄`
  return `按日期 · 每年 ${item.plan_month}月${item.plan_day}日`
}

function changeLabel(item: ImmuneItem): string {
  return { ADDED: '新增', MODIFIED: '已修改', DELETED: '已删除' }[item.change_type || 'NONE'] || ''
}

onMounted(async () => {
  void loadOptions()
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
  <zw-shell side="admin" active="WA-D02" :crumbs="['基础数据', '免疫程序模板']">
    <zw-page-head title="免疫程序模板" desc="批次入栏后，系统按品种的模板和入栏日龄生成免疫计划；没有模板的品种由养殖户手工添加。模板修改发布后只对之后入栏的批次生效，并记入操作日志。" />

    <zw-notice v-if="forbidden" type="error" title="你没有免疫模板的查看权限">请联系超级管理员在“管理员与权限”中开通 WA-D02 的查看权限。</zw-notice>

    <div v-if="!forbidden" class="immune-layout">
      <zw-card flush class="immune-tree">
        <div class="immune-search"><v-text-field v-model="keyword" placeholder="搜索品种或模板" prepend-inner-icon="search" density="compact" hide-details clearable /></div>
        <v-skeleton-loader v-if="listLoading" type="list-item@8" />
        <zw-empty v-else-if="listError" icon="cloud_off" title="模板加载失败" :desc="listError" tone="error">
          <v-btn variant="outlined" @click="loadList(false)">重新加载</v-btn>
        </zw-empty>
        <div v-else class="immune-list">
          <div v-for="group in filteredGroups" :key="group.category_id" class="immune-group">
            <div class="immune-group-title">{{ group.category_name }}</div>
            <button v-for="item in group.breeds" :key="item.breed_id" type="button" class="immune-list-item" :class="{ on: selectedId === item.template_id, muted: !item.template_id }" @click="item.template_id && selectTemplate(item.template_id)">
              <span class="immune-list-main">
                <span class="immune-list-name">{{ item.name || item.breed_name }}</span>
                <span class="immune-list-sub">
                  <template v-if="item.template_id">{{ item.item_count }} 项<template v-if="item.online_version_no"> · 第 {{ item.online_version_no }} 版</template><template v-if="item.active_batch_count"> · {{ formatNumber(item.active_batch_count) }} 个批次</template></template>
                  <template v-else>未配置模板</template>
                </span>
              </span>
              <zw-tag v-if="item.has_draft" type="warning" size="small">有修改</zw-tag>
              <zw-tag v-else-if="!item.template_id" size="small">未配置</zw-tag>
            </button>
          </div>
          <zw-empty v-if="!filteredGroups.length" icon="search_off" title="没有找到相关模板" desc="换一个品种名称试试。" />
        </div>
        <div class="immune-list-foot">
          <v-btn v-if="canHandle" variant="outlined" size="small" prepend-icon="add" block @click="openCreate">新建模板</v-btn>
        </div>
      </zw-card>

      <zw-card v-if="detailLoading || detailError || !detail" flush class="immune-detail">
        <v-skeleton-loader v-if="detailLoading" type="article@6" />
        <zw-empty v-else-if="detailError" icon="cloud_off" title="模板详情加载失败" :desc="detailError" tone="error">
          <v-btn variant="outlined" @click="selectedId && selectTemplate(selectedId)">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else icon="vaccines" title="选择一个模板" desc="从左侧选择品种，查看或编辑免疫程序。" />
      </zw-card>
      <zw-card v-else flush class="immune-detail">
        <div class="immune-detail-head">
          <div class="immune-title">
            <div class="immune-detail-name">
              {{ detail.name }}
              <zw-tag type="outline" size="small">{{ detail.category?.name || '—' }}</zw-tag>
              <zw-tag v-if="detail.online_version_no" type="success" icon="check" size="small">已发布</zw-tag>
              <zw-tag v-else type="warning" size="small">未发布</zw-tag>
            </div>
            <div class="immune-meta">
              第 {{ detail.online_version_no || 0 }} 版<template v-if="detail.published_at"> · {{ formatDate(detail.published_at) }} {{ detail.published_by_name || '' }}发布</template>
              <template v-if="detail.active_batch_count"> · {{ formatNumber(detail.active_batch_count) }} 个在养批次按这份模板生成了免疫计划</template>
              <template v-if="detail.breeding_farm_count"> · 种畜分布在 {{ detail.breeding_farm_count }} 个养殖场</template>
            </div>
          </div>
          <div class="immune-actions">
            <v-btn variant="outlined" size="small" prepend-icon="history" @click="openVersions">版本记录</v-btn>
            <v-btn v-if="canHandle" color="primary" size="small" prepend-icon="add" @click="openItemEdit()">添加项目</v-btn>
          </div>
        </div>

        <div v-if="detail.has_draft" class="immune-draft">
          <zw-notice type="warning" :title="`有 ${detail.draft_changes?.length || 1} 项修改还没有发布`">
            <span v-for="(change, index) in detail.draft_changes || []" :key="index">
              {{ change.vaccine_name || change.item_name }}{{ change.dose_round ? ' · ' + change.dose_round : '' }}{{ change.field_name ? '：' + change.field_name : '' }}
              <template v-if="change.before || change.after"> {{ change.before || '—' }} → {{ change.after || '—' }}</template><span v-if="index < (detail.draft_changes?.length || 0) - 1">；</span>
            </span>
            发布前用户端仍按第 {{ detail.online_version_no || 0 }} 版生成免疫计划。
            <template #action>
              <v-btn v-if="canHandle" variant="text" size="small" color="primary" @click="openPublish">发布修改</v-btn>
              <v-btn v-if="canHandle" variant="text" size="small" color="error" @click="discardOpen = true">放弃修改</v-btn>
            </template>
          </zw-notice>
        </div>

        <div class="immune-detail-body">
          <zw-notice>批次入栏时，入栏日龄已超过的项目列为“入栏前项目”，由养殖户确认是否已接种；计划日期前 1 天和当天提醒负责员工、所有者和场长。</zw-notice>

          <div v-if="detail.items?.some((item) => item.schedule_type === 'BY_DAY_AGE')" class="immune-timeline-wrap">
            <div class="zw-section-title">免疫时间轴</div>
            <div class="immune-axis">
              <div class="immune-axis-range">
                <span v-for="item in detail.items.filter((entry) => entry.schedule_type === 'BY_DAY_AGE')" :key="item.item_id" class="immune-axis-point" :style="{ left: `${timelineLeft(item)}%` }">
                  <span class="immune-axis-day num">{{ item.day_age }}</span>
                  <span class="immune-axis-dot" />
                </span>
              </div>
              <div class="immune-axis-foot"><span>0 日龄</span><span>{{ timelineMax }} 日龄</span></div>
            </div>
          </div>

          <div v-if="!detail.items?.length" class="immune-empty">
            <zw-empty icon="vaccines" title="还没有免疫项目" desc="添加项目并发布后，后续入栏批次会按模板生成免疫计划。">
              <v-btn v-if="canHandle" color="primary" @click="openItemEdit()">添加项目</v-btn>
            </zw-empty>
          </div>
          <v-data-table v-else :items="detail.items" item-key="item_id" hide-default-footer density="compact" :headers="[
            { title: '序号', key: 'sort_no', width: 86, sortable: false },
            { title: '疫苗', key: 'vaccine_name', sortable: false },
            { title: '免次', key: 'dose_round', width: 100, sortable: false },
            { title: '计划时间', key: 'schedule', width: 230, sortable: false },
            { title: '剂量', key: 'dose', width: 120, sortable: false },
            { title: '接种方式', key: 'method', width: 100, sortable: false },
            { title: '操作', key: 'actions', width: 110, sortable: false, align: 'end' }
          ]" class="immune-table">
            <template #item.sort_no="{ item, index }">
              <div class="immune-sort">
                <zw-icon name="drag_indicator" :size="16" />
                <span class="num">{{ index + 1 }}</span>
                <span class="immune-sort-btns">
                  <a :class="{ disabled: index === 0 }" @click="index > 0 && moveItem(index, -1)">↑</a>
                  <a :class="{ disabled: index === (detail.items?.length || 0) - 1 }" @click="index < (detail.items?.length || 0) - 1 && moveItem(index, 1)">↓</a>
                </span>
              </div>
            </template>
            <template #item.vaccine_name="{ item }">
              <div>{{ item.vaccine_name }}</div>
              <div v-if="item.change_type && item.change_type !== 'NONE'" class="immune-sub"><zw-tag :type="item.change_type === 'ADDED' ? 'success' : 'warning'" size="small">{{ changeLabel(item as ImmuneItem) }}</zw-tag><span v-if="item.before" class="zw-muted">原 {{ item.before.vaccine_name }} · {{ item.before.dose_round }}</span></div>
            </template>
            <template #item.schedule="{ item }">
              <span>{{ scheduleText(item as ImmuneItem) }}</span>
              <div v-if="item.before && (item.before.day_age !== item.day_age || item.before.plan_month !== item.plan_month)" class="immune-sub">原 {{ scheduleText(item.before) }}</div>
            </template>
            <template #item.dose="{ item }">{{ item.dose }} {{ item.dose_unit_text || '' }}</template>
            <template #item.method="{ item }">{{ item.vaccinate_method_text || '—' }}</template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a v-if="canHandle" class="zw-link" @click="openItemEdit(item as ImmuneItem)">编辑</a>
                <a v-if="canHandle" class="zw-link danger" @click="openDeleteItem(item as ImmuneItem)">删除</a>
              </div>
            </template>
          </v-data-table>
          <div v-if="detail.items?.length" class="immune-foot"><span class="zw-muted">拖动序号调整顺序；同一日龄的项目按序号先后提醒</span><span class="zw-muted">共 {{ detail.items?.length || 0 }} 项</span></div>
        </div>

        <div v-if="detail.has_draft" class="immune-publish-foot">
          <span class="zw-foot-note">保存后在模板中标为“未发布”，发布后只对之后入栏的批次生效</span>
          <v-btn v-if="canHandle" variant="outlined" size="small" @click="discardOpen = true">放弃修改</v-btn>
          <v-btn v-if="canHandle" color="primary" size="small" prepend-icon="publish" @click="openPublish">发布修改</v-btn>
        </div>
      </zw-card>
    </div>

    <!-- 新建模板 -->
    <zw-drawer v-model="createOpen" title="新建免疫模板" sub="每个启用品种最多一个模板；可以复制已发布的相近模板" width="560">
      <div class="zw-form">
        <zw-form-item label="品种" required :error="createError && !createForm.breed_id ? createError : ''">
          <v-select v-model="createForm.breed_id" :items="unconfigured" placeholder="请选择未配置的品种" />
        </zw-form-item>
        <zw-form-item label="模板名称" optional hint="为空时使用“品种名 + 品类名”">
          <v-text-field v-model="createForm.name" placeholder="例如：杜长大育肥猪" maxlength="30" />
        </zw-form-item>
        <zw-form-item label="复制已有模板" optional hint="复制其线上项目为草稿">
          <v-select v-model="createForm.copy_from_template_id" :items="copyOptions" clearable placeholder="不复制" />
        </zw-form-item>
        <div v-if="createError" class="immune-error">{{ createError }}</div>
      </div>
      <template #foot>
        <span class="zw-foot-note">新建后需要添加项目并发布</span>
        <v-btn variant="outlined" :disabled="createSaving" @click="createOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="createSaving" @click="submitCreate">保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 项目编辑 -->
    <zw-drawer v-model="itemOpen" :title="itemEditing ? '编辑免疫项目' : '添加免疫项目'" :sub="detail ? `${detail.name} · 保存后需要发布才对用户端生效` : ''" width="560">
      <div class="zw-form-2">
        <zw-form-item label="疫苗" required :error="itemFormErrors.vaccine_id">
          <v-select v-model="itemForm.vaccine_id" :items="vaccines.map((v) => ({ title: v.name, value: v.id }))" placeholder="从疫苗目录选择" @update:model-value="onVaccineChange" />
        </zw-form-item>
        <zw-form-item label="免次" required :error="itemFormErrors.dose_round" hint="如首免、二免、加强免疫">
          <v-text-field v-model="itemForm.dose_round" placeholder="例如：二免" maxlength="10" />
        </zw-form-item>
        <zw-form-item label="计划时间" required style="grid-column: 1 / -1">
          <zw-seg v-model="itemForm.schedule_type" :items="[{ label: '按日龄', value: 'BY_DAY_AGE' }, { label: '按日期', value: 'BY_DATE' }]" />
          <div class="zw-form-hint">按日龄：入栏后按批次日龄生成；按日期：每年固定日期，适合种畜普免</div>
        </zw-form-item>
        <zw-form-item v-if="itemForm.schedule_type === 'BY_DAY_AGE'" label="接种日龄" required :error="itemFormErrors.day_age">
          <v-text-field v-model.number="itemForm.day_age" type="number" min="1" max="999" suffix="日龄" />
        </zw-form-item>
        <template v-else>
          <div class="zw-form-2" style="grid-column: 1 / -1">
            <zw-form-item label="每年日期" required :error="itemFormErrors.plan_date">
              <div class="immune-date-row">
                <v-select v-model="itemForm.plan_month" :items="monthOptions" density="compact" hide-details />
                <v-select v-model="itemForm.plan_day" :items="dayOptions" density="compact" hide-details />
              </div>
            </zw-form-item>
            <zw-form-item label="提前生成" required :error="itemFormErrors.advance_days" hint="到期前多少天生成免疫计划">
              <v-text-field v-model.number="itemForm.advance_days" type="number" min="1" max="60" suffix="天" />
            </zw-form-item>
          </div>
        </template>
        <zw-form-item label="剂量" required :error="itemFormErrors.dose">
          <v-text-field v-model="itemForm.dose" type="number" step="0.01" min="0" />
        </zw-form-item>
        <zw-form-item label="剂量单位" required>
          <v-select v-model="itemForm.dose_unit" :items="doseUnitOptions" />
        </zw-form-item>
        <zw-form-item label="接种方式" required :error="itemFormErrors.vaccinate_method_code">
          <v-select v-model="itemForm.vaccinate_method_code" :items="methodOptions" placeholder="请选择接种方式" />
        </zw-form-item>
      </div>
      <zw-form-item label="备注" optional>
        <v-textarea v-model="itemForm.remark" placeholder="例如：妊娠后期母猪顺延" :rows="3" maxlength="100" counter />
      </zw-form-item>
      <zw-notice icon="notifications">计划日期前 1 天和当天提醒该栏舍的负责员工、所有者和场长；栏舍没有负责员工时只提醒所有者和场长。</zw-notice>
      <div v-if="itemError" class="immune-error">{{ itemError }}</div>
      <template #foot>
        <span class="zw-foot-note">保存后在模板中标为“未发布”</span>
        <v-btn variant="outlined" :disabled="itemSaving" @click="itemOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="itemSaving" @click="submitItem">保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 删除项目 -->
    <zw-dialog v-model="deleteOpen" title="删除这个免疫项目？" width="440" icon="delete" tone="error">
      <p class="immune-dialog-text">{{ deleteTarget?.vaccine_name }} · {{ deleteTarget?.dose_round }}</p>
      <p class="immune-dialog-text">删除后在模板中标为未发布，发布后之后入栏的批次不再生成这一项。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="deleteSaving" @click="deleteOpen = false">取消</v-btn>
        <v-btn color="error" :loading="deleteSaving" @click="confirmDeleteItem">删除</v-btn>
      </template>
    </zw-dialog>

    <!-- 发布 -->
    <zw-dialog v-model="publishOpen" title="发布这次模板修改？" width="480" icon="publish" tone="primary">
      <p class="immune-dialog-text">{{ detail?.name }} 将从第 {{ detail?.online_version_no || 0 }} 版更新为第 {{ (detail?.online_version_no || 0) + 1 }} 版。只对之后入栏的批次生效；已生成的在养批次免疫计划不变。</p>
      <zw-form-item label="修改说明" required>
        <v-textarea v-model="publishNote" placeholder="请填写修改说明" :rows="3" maxlength="100" counter />
      </zw-form-item>
      <div v-if="publishError" class="immune-error">{{ publishError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="publishSaving" @click="publishOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="publishSaving" @click="submitPublish">发布第 {{ (detail?.online_version_no || 0) + 1 }} 版</v-btn>
      </template>
    </zw-dialog>

    <!-- 放弃 -->
    <zw-dialog v-model="discardOpen" title="放弃未发布的修改？" width="440" icon="undo" tone="warning">
      <p class="immune-dialog-text">放弃后恢复线上版本，未发布的修改不能恢复。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="discardSaving" @click="discardOpen = false">继续保留</v-btn>
        <v-btn color="error" :loading="discardSaving" @click="confirmDiscard">放弃修改</v-btn>
      </template>
    </zw-dialog>

    <!-- 版本 -->
    <zw-drawer v-model="versionOpen" :title="`版本记录 · ${detail?.name || ''}`" sub="回退会把历史版本项目复制为新草稿，发布后生效" width="640">
      <v-skeleton-loader v-if="versionsLoading" type="paragraph@5" />
      <template v-else>
        <div v-if="versionError" class="immune-error">{{ versionError }}</div>
        <button v-for="version in versions" :key="version.version_id" type="button" class="immune-version-item" :class="{ on: versionTarget?.version_id === version.version_id }" @click="versionTarget = version">
          <span class="immune-version-name">第 {{ version.version_no }} 版 <zw-tag :type="version.status === 'PUBLISHED' ? 'success' : 'neutral'" size="small">{{ version.status_text || version.status }}</zw-tag></span>
          <span class="zw-muted">{{ version.published_at ? formatDateTime(version.published_at) : '' }} · {{ version.published_by_name || '' }} · {{ version.item_count || 0 }} 项</span>
          <span class="immune-version-note">{{ version.change_note || '—' }}</span>
        </button>
        <div v-if="!versions.length" class="zw-muted">还没有版本记录</div>
        <div v-if="versionTarget && versionTarget.version_no !== detail?.online_version_no" class="immune-rollback">
          <div class="zw-section-title">回退到第 {{ versionTarget.version_no }} 版</div>
          <p class="zw-muted">回退会复制该版本项目为新草稿，发布后生效；当前线上版本不会删除。</p>
          <v-btn color="primary" variant="outlined" prepend-icon="history" :loading="rollbackSaving" @click="submitRollback">回退到这一版</v-btn>
        </div>
      </template>
      <template #foot><v-btn variant="outlined" @click="versionOpen = false">关闭</v-btn></template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.immune-layout { display: grid; grid-template-columns: 248px minmax(0, 1fr); gap: 16px; align-items: start; }
.immune-tree { min-height: 580px; display: flex; flex-direction: column; }
.immune-search { padding: 10px 12px 6px; }
.immune-list { flex: 1; overflow: auto; max-height: 680px; }
.immune-group-title { padding: 9px 12px 3px; font-size: 12px; font-weight: 600; color: var(--text-3); }
.immune-list-item { width: 100%; min-height: 48px; display: flex; align-items: center; gap: 8px; border: 0; background: transparent; font: inherit; text-align: left; padding: 5px 12px; cursor: pointer; }
.immune-list-item:hover { background: var(--fill-1); }
.immune-list-item.on { background: var(--primary-subtle); }
.immune-list-item.on .immune-list-name { color: var(--primary); font-weight: 500; }
.immune-list-item.muted { color: var(--text-3); }
.immune-list-main { flex: 1; min-width: 0; }
.immune-list-name { display: block; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.immune-list-sub { display: block; font-size: 11px; line-height: 17px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.immune-list-foot { padding: 10px 12px; border-top: 1px solid var(--line-1); }
.immune-detail { min-height: 580px; }
.immune-detail-head { display: flex; align-items: flex-start; gap: 12px; padding: 18px 20px 14px; border-bottom: 1px solid var(--line-1); }
.immune-title { flex: 1; min-width: 0; }
.immune-detail-name { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 18px; font-weight: 600; }
.immune-meta { margin-top: 3px; font-size: 12px; line-height: 20px; color: var(--text-2); }
.immune-actions { display: flex; gap: 8px; flex-shrink: 0; }
.immune-draft { padding: 12px 20px 0; }
.immune-detail-body { padding: 16px 20px 20px; }
.immune-timeline-wrap { margin: 18px 0; }
.immune-axis { border: 1px solid var(--line-1); border-radius: var(--radius-sm); padding: 42px 18px 10px; position: relative; height: 126px; background: var(--bg-card); }
.immune-axis-range { position: relative; height: 4px; background: var(--fill-3); border-radius: 2px; margin: 0 8px; }
.immune-axis-point { position: absolute; top: -20px; transform: translateX(-50%); display: flex; flex-direction: column; align-items: center; }
.immune-axis-day { font-size: 11px; color: var(--primary); }
.immune-axis-dot { width: 12px; height: 12px; border-radius: 50%; background: var(--primary); border: 2px solid var(--bg-card); margin-top: 2px; }
.immune-axis-foot { display: flex; justify-content: space-between; margin-top: 16px; font-size: 11px; color: var(--text-3); }
.immune-sort { display: flex; align-items: center; gap: 4px; }
.immune-sort-btns { display: inline-flex; flex-direction: column; margin-left: 4px; line-height: 10px; }
.immune-sort-btns a { font-size: 10px; color: var(--text-3); cursor: pointer; }
.immune-sort-btns a.disabled { opacity: 0.3; cursor: default; }
.immune-sub { display: flex; align-items: center; gap: 4px; font-size: 11px; line-height: 16px; color: var(--text-3); margin-top: 2px; }
.immune-table :deep(tbody tr) { height: 48px; }
.immune-foot { display: flex; justify-content: space-between; padding: 10px 20px; border-top: 1px solid var(--line-1); }
.immune-publish-foot { display: flex; justify-content: flex-end; align-items: center; gap: 8px; padding: 12px 20px; border-top: 1px solid var(--line-1); background: var(--fill-1); }
.immune-empty { padding: 20px 0; }
.immune-error { margin-top: 8px; color: var(--error); font-size: 12px; }
.immune-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); }
.immune-date-row { display: flex; gap: 8px; }
.immune-date-row .v-select { max-width: 110px; }
.immune-version-item { width: 100%; display: flex; flex-direction: column; gap: 3px; padding: 10px 14px; border: 1px solid var(--line-1); background: var(--bg-card); border-radius: var(--radius-sm); margin-bottom: 6px; text-align: left; font: inherit; cursor: pointer; }
.immune-version-item.on { border-color: var(--primary); background: var(--primary-subtle); }
.immune-version-name { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 500; }
.immune-version-note { font-size: 12px; color: var(--text-2); }
.immune-rollback { margin-top: 14px; border-top: 1px solid var(--line-1); padding-top: 10px; }
</style>
