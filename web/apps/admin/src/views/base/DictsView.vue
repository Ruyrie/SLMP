<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { routeQueryId, routeQueryText } from '@/utils/routeQuery'
import { ApiError, formatNumber } from '@zmt/shared'
import {
  createDictItem,
  getCategoryTree,
  getDictItems,
  getDictTypes,
  orderDictItems,
  setDictItemStatus,
  updateDictItem,
  type CategoryNode,
  type DictItem,
  type DictItemPayload,
  type DictType
} from '@/api/base'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const route = useRoute()
const canHandle = computed(() => admin.hasPerm('WA-D05:HANDLE'))

const types = ref<DictType[]>([])
const selectedType = ref('PEN_TYPE')
const items = ref<DictItem[]>([])
const categories = ref<CategoryNode[]>([])
const loading = ref(true)
const listLoading = ref(false)
const error = ref('')
const forbidden = ref(false)
const search = ref('')
const speciesFilter = ref('')
const categoryFilter = ref<number | null>(null)
const sortLoading = ref(false)

const currentType = computed(() => types.value.find((type) => type.dict_type === selectedType.value) || null)
/** 收入 / 支出一级分类（t_finance_category）：没有说明和“说明必填”，可以设图标 */
const isFinance = computed(() => selectedType.value === 'EXPENSE_CATEGORY' || selectedType.value === 'INCOME_CATEGORY')
const scopeMode = computed(() => currentType.value?.scope_mode || 'NONE')
const filteredItems = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return items.value
  return items.value.filter((item) => item.name.toLowerCase().includes(keyword) || (item.description || '').toLowerCase().includes(keyword))
})
const speciesOptions = [
  { title: '全部畜种', value: '' },
  { title: '猪', value: 'PIG' },
  { title: '禽', value: 'POULTRY' },
  { title: '牛', value: 'CATTLE' },
  { title: '羊', value: 'SHEEP' }
]
const categoryOptions = computed(() => [{ title: '全部品类', value: null }, ...categories.value.map((category) => ({ title: category.name, value: category.id }))])
const navDescription: Record<string, string> = {
  PEN_TYPE: '用户端新建栏舍时选择类型；容量单位跟随畜种。',
  DEATH_REASON: '死淘登记时单选；兜底项不能停用。',
  SYMPTOM: '按品类维护；异常上报时多选，AI 诊断按所选症状查找常见病症。',
  FEED_TYPE: '养殖户新增饲料档案时选择类型，不能手动输入。',
  VACCINATE_METHOD: '免疫模板和接种登记时选择；按畜种过滤。',
  DISINFECT_METHOD: '消毒计划和消毒登记时选择。',
  SCRAP_REASON: '库存报废时单选；选“其他”时说明必填。',
  PAYMENT_METHOD: '收支记账选择收款方式。',
  EXPENSE_CATEGORY: '收支记账的支出一级分类，养殖户可以在其下添加子类；平台统一维护。',
  INCOME_CATEGORY: '收支记账的收入一级分类；平台统一维护。'
}

function typeIcon(type: DictType): string {
  return type.icon || 'toc'
}

async function loadTypes(): Promise<void> {
  loading.value = true
  forbidden.value = false
  error.value = ''
  try {
    types.value = (await getDictTypes()) || []
    if (types.value.length && !types.value.some((type) => type.dict_type === selectedType.value)) {
      selectedType.value = types.value[0]!.dict_type
    }
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) forbidden.value = true
    error.value = e instanceof Error ? e.message : '字典加载失败'
  } finally {
    loading.value = false
  }
}

async function loadItems(): Promise<void> {
  listLoading.value = true
  error.value = ''
  try {
    items.value = (await getDictItems(selectedType.value, {
      species: scopeMode.value === 'SPECIES' && speciesFilter.value ? speciesFilter.value : undefined,
      category_id: scopeMode.value === 'CATEGORY' ? categoryFilter.value : null
    })) || []
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) forbidden.value = true
    error.value = e instanceof Error ? e.message : '字典项加载失败'
    items.value = []
  } finally {
    listLoading.value = false
  }
}

function selectType(type: DictType): void {
  selectedType.value = type.dict_type
  search.value = ''
  speciesFilter.value = ''
  categoryFilter.value = null
  void loadItems()
}

watch([speciesFilter, categoryFilter], () => { void loadItems() })

function scopeText(item: DictItem): string {
  if (!item.scopes.length) return '全部品类'
  return item.scopes
    .map((scope) => {
      if (scope.scope_type === 'SPECIES') {
        return { PIG: '猪', POULTRY: '禽', CATTLE: '牛', SHEEP: '羊' }[scope.species || ''] || scope.species || '—'
      }
      return scope.category_name || categories.value.find((category) => category.id === scope.category_id)?.name || '—'
    })
    .join('、')
}

function scopeSummary(item: DictItem): string {
  if (currentType.value?.scope_mode === 'CATEGORY' && item.scopes.some((scope) => scope.category_id)) {
    return item.scopes.map((scope) => scope.category_name || categories.value.find((category) => category.id === scope.category_id)?.name || '').filter(Boolean).join('、') || '全部品类'
  }
  return scopeText(item)
}

/* ---------------- 编辑 ---------------- */
const drawer = ref(false)
const editing = ref<DictItem | null>(null)
const saving = ref(false)
const formError = ref('')
const formErrors = reactive<Record<string, string>>({})
const form = reactive({
  name: '',
  description: '',
  icon: '',
  remark_required: false,
  sort_no: 1,
  scope_species: [] as string[],
  scope_categories: [] as number[],
  version: 0
})

function openCreate(): void {
  editing.value = null
  form.name = ''
  form.description = ''
  form.icon = ''
  form.remark_required = false
  form.sort_no = (items.value.length || 0) + 1
  form.scope_species = scopeMode.value === 'SPECIES' ? [speciesFilter.value || 'PIG'] : []
  form.scope_categories = scopeMode.value === 'CATEGORY' ? [categoryFilter.value || categories.value[0]?.id].filter((id): id is number => !!id) : []
  form.version = 0
  formError.value = ''
  Object.keys(formErrors).forEach((key) => delete formErrors[key])
  drawer.value = true
}

function openEdit(item: DictItem): void {
  editing.value = item
  form.name = item.name
  form.description = item.description || ''
  form.icon = item.icon || ''
  form.remark_required = !!item.remark_required
  form.sort_no = item.sort_no || 1
  form.scope_species = item.scopes.filter((scope) => scope.scope_type === 'SPECIES' && scope.species).map((scope) => scope.species!)
  form.scope_categories = item.scopes.filter((scope) => scope.scope_type === 'CATEGORY' && scope.category_id).map((scope) => scope.category_id!)
  form.version = item.version || 0
  formError.value = ''
  Object.keys(formErrors).forEach((key) => delete formErrors[key])
  drawer.value = true
}

function buildPayload(): DictItemPayload {
  const scopes: DictItemPayload['scopes'] = []
  if (scopeMode.value === 'SPECIES') {
    for (const species of form.scope_species) scopes.push({ scope_type: 'SPECIES', species })
  } else if (scopeMode.value === 'CATEGORY') {
    for (const categoryId of form.scope_categories) scopes.push({ scope_type: 'CATEGORY', category_id: categoryId })
  }
  return {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    icon: form.icon.trim() || undefined,
    remark_required: form.remark_required,
    sort_no: form.sort_no,
    scopes,
    version: editing.value?.version
  }
}

async function submit(): Promise<void> {
  Object.keys(formErrors).forEach((key) => delete formErrors[key])
  formError.value = ''
  if (!form.name.trim()) formErrors.name = '请填写名称'
  if (scopeMode.value === 'SPECIES' && !form.scope_species.length) formErrors.scopes = '请选择适用畜种'
  if (scopeMode.value === 'CATEGORY' && !form.scope_categories.length) formErrors.scopes = '请选择适用品类'
  if (Object.keys(formErrors).length) return
  saving.value = true
  try {
    if (editing.value) await updateDictItem(selectedType.value, editing.value.id, buildPayload())
    else await createDictItem(selectedType.value, buildPayload())
    drawer.value = false
    ui.toast('已保存')
    await Promise.all([loadTypes(), loadItems()])
  } catch (e) {
    if (e instanceof ApiError) {
      for (const error of e.fieldErrors) formErrors[error.field] = error.message
      formError.value = e.message
    } else {
      formError.value = e instanceof Error ? e.message : '保存失败'
    }
  } finally {
    saving.value = false
  }
}

/* ---------------- 启停 ---------------- */
const statusOpen = ref(false)
const statusTarget = ref<DictItem | null>(null)
const statusValue = ref('DISABLED')
const statusReason = ref('')
const statusError = ref('')
const statusSaving = ref(false)

function openStatus(item: DictItem, status: string): void {
  statusTarget.value = item
  statusValue.value = status
  statusReason.value = ''
  statusError.value = ''
  statusOpen.value = true
}

async function submitStatus(): Promise<void> {
  if (!statusTarget.value) return
  if (!statusReason.value.trim()) {
    statusError.value = statusValue.value === 'DISABLED' ? '请填写停用原因' : '请填写启用原因'
    return
  }
  statusSaving.value = true
  statusError.value = ''
  try {
    await setDictItemStatus(selectedType.value, statusTarget.value.id, statusValue.value, statusReason.value.trim(), statusTarget.value.version || 0)
    statusOpen.value = false
    ui.toast(statusValue.value === 'DISABLED' ? `已停用“${statusTarget.value.name}”` : `已启用“${statusTarget.value.name}”`)
    await Promise.all([loadTypes(), loadItems()])
  } catch (e) {
    statusError.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    statusSaving.value = false
  }
}

/* ---------------- 排序 ---------------- */
async function move(index: number, direction: -1 | 1): Promise<void> {
  if (sortLoading.value) return
  if (search.value.trim() || speciesFilter.value || (scopeMode.value === 'CATEGORY' && !categoryFilter.value)) {
    ui.toast('请先清除搜索或选择品类/畜种，再调整整个字典的顺序', 'info')
    return
  }
  const target = index + direction
  if (target < 0 || target >= items.value.length) return
  const ordered = [...items.value]
  const [item] = ordered.splice(index, 1)
  if (!item) return
  ordered.splice(target, 0, item)
  sortLoading.value = true
  try {
    await orderDictItems(selectedType.value, ordered.map((entry) => entry.id), scopeMode.value === 'CATEGORY' ? categoryFilter.value : null)
    items.value = ordered
    ui.toast('已调整顺序')
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '调整顺序失败', 'error')
  } finally {
    sortLoading.value = false
  }
}

interface DictHeader {
  title: string
  key: string
  sortable: boolean
  width?: number
  minWidth?: number
  align?: 'start' | 'end' | 'center'
}
const headers = computed<DictHeader[]>(() => {
  const base: DictHeader[] = [
    { title: '排序', key: 'sort', sortable: false, width: 84 },
    { title: '名称', key: 'name', sortable: false, minWidth: 160 },
    { title: '说明', key: 'description', sortable: false, minWidth: 220 }
  ]
  if (scopeMode.value !== 'NONE') base.push({ title: '适用范围', key: 'scope', sortable: false, width: 170 })
  base.push(
    { title: '使用', key: 'usage', sortable: false, width: 100, align: 'end' },
    { title: '状态', key: 'status', sortable: false, width: 86 },
    { title: '操作', key: 'actions', sortable: false, width: 110, align: 'end' }
  )
  return base
})

onMounted(async () => {
  loading.value = true
  try {
    const tree = await getCategoryTree()
    categories.value = tree || []
  } catch {
    categories.value = []
  }
  const routeType = routeQueryText(route, 'dict_type')
  if (routeType) selectedType.value = routeType
  await loadTypes()
  await loadItems()
  // 从操作日志带 ?dict_type=&id= 进入：按名称筛出对应字典项
  const id = routeQueryId(route)
  const target = id ? items.value.find((item) => item.id === id) : undefined
  if (target) search.value = target.name
})
</script>

<template>
  <zw-shell side="admin" active="WA-D05" :crumbs="['基础数据', '其他字典', currentType?.name || '']">
    <zw-page-head title="其他字典" desc="用户端各处单选、多选的选项都来自这里。停用后选项不再出现，已有记录保留原值；新增、修改、停用都记入操作日志。" />

    <zw-notice v-if="forbidden" type="error" title="你没有其他字典的查看权限">请联系超级管理员在“管理员与权限”中开通 WA-D05 的查看权限。</zw-notice>

    <div v-if="!forbidden" class="dict-layout">
      <zw-card flush class="dict-nav-card">
        <v-skeleton-loader v-if="loading" type="list-item@8" />
        <template v-else>
          <button v-for="type in types" :key="type.dict_type" type="button" class="dict-nav-item" :class="{ on: selectedType === type.dict_type }" @click="selectType(type)">
            <zw-icon :name="typeIcon(type)" :size="18" /><span>{{ type.name }}</span><span class="num">{{ type.item_count }}</span>
          </button>
          <div class="dict-nav-sep" />
          <button type="button" class="dict-nav-item" @click="$router.push('/base/drug-catalog')">
            <zw-icon name="sanitizer" :size="18" /><span>消毒剂</span><span class="dict-nav-link">去兽药疫苗目录</span>
          </button>
        </template>
      </zw-card>

      <zw-card flush class="dict-main">
        <div class="dict-head">
          <div>
            <div class="dict-title">{{ currentType?.name || '字典' }} <zw-tag v-if="currentType?.platform_only" icon="lock" size="small">平台维护，用户不能改</zw-tag></div>
            <div class="dict-sub">{{ navDescription[selectedType] || '用户端选项由平台统一维护。' }}</div>
          </div>
          <div class="dict-head-actions">
            <v-select v-if="scopeMode === 'SPECIES'" v-model="speciesFilter" :items="speciesOptions" class="zw-w-140" density="compact" hide-details />
            <v-select v-if="scopeMode === 'CATEGORY'" v-model="categoryFilter" :items="categoryOptions" class="zw-w-140" density="compact" hide-details />
            <v-text-field v-model="search" placeholder="搜索名称或说明" prepend-inner-icon="search" density="compact" hide-details clearable class="zw-w-200" />
            <v-btn v-if="canHandle" color="primary" prepend-icon="add" @click="openCreate">新增{{ currentType?.name?.replace('类型', '') || '字典项' }}</v-btn>
          </div>
        </div>

        <div class="dict-notice">
          <zw-notice :icon="currentType?.icon || 'info'">
            <template v-if="selectedType === 'DEATH_REASON'">死淘登记按原因统计死亡率和淘汰率；选“其他”时说明必填。</template>
            <template v-else-if="selectedType === 'SYMPTOM'">用户端异常上报按批次所属品类显示症状，可多选；描述至少 10 个字，或至少勾选一项症状。关联的常见病症来自知识库，AI 诊断时优先检索。</template>
            <template v-else-if="selectedType === 'FEED_TYPE'">饲料档案的类型只能从这里选，不能手动输入；饲喂标准的“建议饲料”按类型匹配本场的饲料档案。</template>
            <template v-else-if="selectedType === 'PEN_TYPE'">栏舍类型按畜种维护，用户端新建栏舍时从对应畜种的类型中选择。</template>
            <template v-else-if="selectedType === 'VACCINATE_METHOD'">接种方式按畜种过滤，被免疫模板引用时不能停用。</template>
            <template v-else-if="isFinance">停用后，用户端记账时不再显示这个分类，已有流水保留原分类；兜底“其他”和自动记账的分类（饲料、兽药、引种入库、出栏收款等）不能停用。</template>
            <template v-else>停用后，用户端新增或编辑记录时不再显示这个选项；已有记录保留原值。</template>
          </zw-notice>
        </div>

        <v-skeleton-loader v-if="listLoading" type="table-row@6" />
        <zw-empty v-else-if="error" icon="cloud_off" title="字典项加载失败" :desc="error" tone="error">
          <v-btn variant="outlined" @click="loadItems()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!filteredItems.length && search" icon="search_off" title="没有找到相关字典项" desc="换一个名称试试。" />
        <zw-empty v-else-if="!filteredItems.length" icon="toc" title="这个字典还没有项" desc="新增字典项后，用户端才能选择。">
          <v-btn v-if="canHandle" color="primary" @click="openCreate">新增字典项</v-btn>
        </zw-empty>
        <template v-else>
          <v-data-table :headers="headers" :items="filteredItems" item-key="id" hide-default-footer density="compact" class="dict-table">
            <template #item.sort="{ item, index }">
              <div class="dict-sort">
                <zw-icon name="drag_indicator" :size="16" />
                <span class="num">{{ index + 1 }}</span>
                <span class="dict-sort-btns">
                  <a :class="{ disabled: index === 0 }" @click="index > 0 && move(index, -1)">↑</a>
                  <a :class="{ disabled: index === filteredItems.length - 1 }" @click="index < filteredItems.length - 1 && move(index, 1)">↓</a>
                </span>
              </div>
            </template>
            <template #item.name="{ item }">
              <div class="dict-name">
                <span v-if="item.icon" class="dict-ico"><zw-icon :name="item.icon" :size="15" /></span>
                <span>{{ item.name }}</span>
                <zw-tag v-if="item.remark_required" type="warning" size="small">说明必填</zw-tag>
                <zw-tag v-if="item.is_fallback" type="outline" size="small">兜底</zw-tag>
                <zw-tag v-if="isFinance && item.auto_source_text" type="neutral" size="small">自动记账 · {{ item.auto_source_text }}</zw-tag>
                <span v-if="isFinance && item.subcategory_count" class="zw-muted dict-sub-count">子类 {{ item.subcategory_count }}</span>
              </div>
            </template>
            <template #item.description="{ item }"><span class="dict-desc">{{ item.description || '—' }}</span></template>
            <template #item.scope="{ item }">{{ scopeSummary(item as DictItem) }}</template>
            <template #item.usage="{ item }"><span class="num">{{ item.usage_label || (item.usage_count ? `${formatNumber(item.usage_count)} 次` : '—') }}</span></template>
            <template #item.status="{ item }">
              <zw-tag v-if="item.status === 'DISABLED'" size="small">已停用</zw-tag>
              <span v-else class="zw-muted">启用</span>
            </template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a v-if="canHandle" class="zw-link" @click="openEdit(item as DictItem)">编辑</a>
                <a v-if="canHandle && item.status === 'ENABLED' && !item.is_fallback && !item.is_system" class="zw-link danger" @click="openStatus(item as DictItem, 'DISABLED')">停用</a>
                <a v-if="canHandle && item.status !== 'ENABLED'" class="zw-link" @click="openStatus(item as DictItem, 'ENABLED')">启用</a>
              </div>
            </template>
          </v-data-table>
          <div class="dict-foot">
            <span class="zw-muted"><template v-if="selectedType === 'DEATH_REASON'">“其他”是兜底选项，不能停用</template><template v-else-if="isFinance">“其他”是兜底分类，自动记账分类不能停用 · 调整顺序保存即生效</template><template v-else-if="scopeMode === 'CATEGORY'">按品类维护 · 调整顺序保存即生效</template><template v-else>调整顺序保存即生效</template></span>
            <span class="zw-muted">共 {{ filteredItems.length }} 项</span>
          </div>
        </template>
      </zw-card>
    </div>

    <zw-drawer v-model="drawer" :title="`${editing ? '编辑' : '新增'}${currentType?.name || '字典项'}`" :sub="editing ? `修改保存后用户端立即生效` : '保存后用户端立即可以选择'" width="560">
      <div class="zw-form-2">
        <zw-form-item label="名称" required :error="formErrors.name" hint="最多 10 个字">
          <v-text-field v-model="form.name" maxlength="10" :error="!!formErrors.name" />
        </zw-form-item>
        <zw-form-item label="排序" required hint="用户端按排序从左到右显示">
          <v-text-field v-model.number="form.sort_no" type="number" min="0" />
        </zw-form-item>
        <zw-form-item v-if="!isFinance" label="说明" optional style="grid-column: 1 / -1">
          <v-text-field v-model="form.description" maxlength="50" placeholder="给养殖户的简短说明" />
        </zw-form-item>
        <zw-form-item v-else label="图标" optional style="grid-column: 1 / -1" hint="Material Symbols 图标名，例如 inventory_2；留空用默认图标">
          <v-text-field v-model="form.icon" maxlength="40" placeholder="category" />
        </zw-form-item>
        <zw-form-item v-if="scopeMode === 'SPECIES'" label="适用畜种" required :error="formErrors.scopes" style="grid-column: 1 / -1">
          <v-select v-model="form.scope_species" :items="speciesOptions.filter((option) => option.value)" multiple chips density="compact" hide-details placeholder="请选择畜种" />
        </zw-form-item>
        <zw-form-item v-if="scopeMode === 'CATEGORY'" label="适用品类" required :error="formErrors.scopes" style="grid-column: 1 / -1">
          <v-select v-model="form.scope_categories" :items="categories.map((category) => ({ title: category.name, value: category.id }))" multiple chips density="compact" hide-details placeholder="请选择品类" />
        </zw-form-item>
        <zw-form-item v-if="!isFinance" label="选中后说明" required style="grid-column: 1 / -1" hint="必填时，用户端“说明”框改为必填">
          <v-switch v-model="form.remark_required" :label="form.remark_required ? '说明必填' : '说明选填'" density="compact" hide-details />
        </zw-form-item>
      </div>
      <div v-if="formError" class="dict-form-error">{{ formError }}</div>
      <template #foot>
        <span class="zw-foot-note">保存记入操作日志</span>
        <v-btn variant="outlined" :disabled="saving" @click="drawer = false">取消</v-btn>
        <v-btn color="primary" :loading="saving" @click="submit">保存</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog v-model="statusOpen" :title="statusValue === 'DISABLED' ? '停用这个字典项？' : '启用这个字典项？'" width="460" :icon="statusValue === 'DISABLED' ? 'block' : 'check_circle'" :tone="statusValue === 'DISABLED' ? 'error' : 'primary'">
      <p class="dict-dialog-strong">{{ statusTarget?.name }}</p>
      <p class="dict-dialog-text">{{ statusValue === 'DISABLED' ? '停用后，用户端新增或编辑记录时不再显示这个选项；已有记录保留原值，库存和流水不受影响。' : '启用后，用户端新增或编辑记录时重新显示这个选项。' }}</p>
      <zw-form-item :label="statusValue === 'DISABLED' ? '停用原因' : '启用原因'" required hint="记入操作日志，用户端不显示">
        <v-textarea v-model="statusReason" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <div v-if="statusError" class="dict-form-error">{{ statusError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="statusSaving" @click="statusOpen = false">取消</v-btn>
        <v-btn :color="statusValue === 'DISABLED' ? 'error' : 'primary'" :loading="statusSaving" @click="submitStatus">{{ statusValue === 'DISABLED' ? '停用' : '启用' }}</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.dict-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 16px; align-items: start; }
.dict-nav-card { padding: 6px 0; }
.dict-nav-item { width: 100%; height: 40px; display: flex; align-items: center; gap: 8px; padding: 0 14px; border: 0; background: transparent; font: inherit; font-size: 14px; text-align: left; cursor: pointer; color: var(--text-1); }
.dict-nav-item:hover { background: var(--fill-1); }
.dict-nav-item.on { background: var(--primary-subtle); color: var(--primary); font-weight: 500; }
.dict-nav-item .num { margin-left: auto; font-size: 12px; color: var(--text-3); font-weight: 400; }
.dict-nav-item .dict-nav-link { margin-left: auto; font-size: 11px; color: var(--primary); }
.dict-nav-sep { height: 1px; background: var(--line-1); margin: 6px 0; }
.dict-main { min-height: 560px; }
.dict-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 16px 20px 10px; }
.dict-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; }
.dict-sub { margin-top: 2px; font-size: 12px; color: var(--text-3); }
.dict-head-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dict-notice { padding: 0 20px 12px; }
.dict-table :deep(tbody tr) { height: 40px; }
.dict-name { display: flex; align-items: center; gap: 6px; }
.dict-sub-count { font-size: 12px; }
.dict-ico { width: 26px; height: 26px; border-radius: 4px; background: var(--primary-subtle); color: var(--primary); display: inline-flex; align-items: center; justify-content: center; }
.dict-desc { display: block; max-width: 340px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-2); }
.dict-sort { display: flex; align-items: center; gap: 3px; color: var(--text-3); }
.dict-sort-btns { display: flex; flex-direction: column; line-height: 10px; }
.dict-sort-btns a { font-size: 10px; cursor: pointer; }
.dict-sort-btns a.disabled { opacity: 0.3; cursor: default; }
.dict-foot { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; border-top: 1px solid var(--line-1); }
.dict-form-error { margin-top: 8px; color: var(--error); font-size: 12px; }
.dict-dialog-strong { font-size: 15px; font-weight: 600; margin: 0 0 6px; }
.dict-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); }
</style>
