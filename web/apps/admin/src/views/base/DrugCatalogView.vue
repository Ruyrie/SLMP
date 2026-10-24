<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, formatDate, formatDateTime, formatNumber } from '@zmt/shared'
import {
  addBannedDrug,
  createDrug,
  createLotRecall,
  getBannedDrugs,
  getBannedHits,
  getDictItems,
  getDrugCatalog,
  getDrugDetail,
  getLotRecalls,
  getRecallFarms,
  previewLotRecall,
  setDrugStatus,
  updateBannedDrug,
  updateDrug,
  type BannedDrug,
  type DictItem,
  type DrugDetail,
  type DrugHit,
  type DrugLotRecall,
  type DrugRow,
  type DrugUsage,
  type RecallFarm,
  type RecallPreview
} from '@/api/base'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const canHandle = computed(() => admin.hasPerm('WA-D03:HANDLE'))

type Tab = 'VET_DRUG' | 'VACCINE' | 'DISINFECTANT' | 'BANNED' | 'RECALL'
const tab = ref<Tab>('VET_DRUG')
const tabCounts = reactive<Record<string, number>>({ VET_DRUG: 0, VACCINE: 0, DISINFECTANT: 0, BANNED: 0, RECALL: 0 })
const tabs = computed(() => [
  { label: '兽药', value: 'VET_DRUG', count: tabCounts.VET_DRUG || 0 },
  { label: '疫苗', value: 'VACCINE', count: tabCounts.VACCINE || 0 },
  { label: '消毒剂', value: 'DISINFECTANT', count: tabCounts.DISINFECTANT || 0 },
  { label: '禁用药', value: 'BANNED', count: tabCounts.BANNED || 0 },
  { label: '批号停用', value: 'RECALL', count: tabCounts.RECALL || 0 }
])
const speciesOptions = [
  { title: '猪', value: 'PIG' },
  { title: '禽', value: 'POULTRY' },
  { title: '牛', value: 'CATTLE' },
  { title: '羊', value: 'SHEEP' }
]
const statusOptions = [
  { title: '全部状态', value: '' },
  { title: '启用', value: 'ENABLED' },
  { title: '已停用', value: 'DISABLED' }
]
const bannedClassOptions = [
  { title: 'β-受体激动剂', value: 'BETA_AGONIST' },
  { title: '抗菌药', value: 'ANTIBACTERIAL' },
  { title: '硝基呋喃类', value: 'NITROFURAN' },
  { title: '激素类', value: 'HORMONE' },
  { title: '镇静类', value: 'SEDATIVE' },
  { title: '杀虫剂类', value: 'PESTICIDE' },
  { title: '其他', value: 'OTHER' }
]

/* ---------------- 目录 ---------------- */
const list = ref<DrugRow[]>([])
const total = ref(0)
const query = reactive({ keyword: '', species: '', dosageForm: '', status: '', page: 1, size: 20 })
const listLoading = ref(true)
const listError = ref('')
const forbidden = ref(false)
const dosageFormOptions = ref<Array<{ title: string; value: string }>>([])
const catalogHeaders = [
  { title: '通用名称', key: 'name', sortable: false, minWidth: 230 },
  { title: '剂型 · 规格', key: 'spec', sortable: false, width: 170 },
  { title: '推荐剂量', key: 'dose', sortable: false, width: 190 },
  { title: '休药期', key: 'withdrawal', sortable: false, width: 210 },
  { title: '状态', key: 'status', sortable: false, width: 82 },
  { title: '建档', key: 'archive_farm_count', sortable: false, width: 70, align: 'end' as const },
  { title: '操作', key: 'actions', sortable: false, width: 180, align: 'end' as const }
]

async function fetchCatalog(): Promise<void> {
  if (tab.value !== 'VET_DRUG' && tab.value !== 'VACCINE' && tab.value !== 'DISINFECTANT') return
  listLoading.value = true
  listError.value = ''
  forbidden.value = false
  try {
    const data = await getDrugCatalog({
      drug_category: tab.value,
      keyword: query.keyword.trim() || undefined,
      species: query.species || undefined,
      dosage_form: query.dosageForm || undefined,
      status: query.status || undefined,
      page: query.page,
      size: query.size
    })
    list.value = data.list || []
    total.value = data.total || 0
    Object.assign(tabCounts, data.tab_counts || {})
    dosageFormOptions.value = [...new Set(list.value.map((item) => item.dosage_form).filter(Boolean))].map((value) => ({ title: value, value }))
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) forbidden.value = true
    listError.value = e instanceof Error ? e.message : '加载失败'
    list.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

function switchTab(value: Tab): void {
  tab.value = value
  if (value === 'BANNED') {
    void fetchBanned()
  } else if (value === 'RECALL') {
    void fetchRecalls()
  } else {
    query.page = 1
    void fetchCatalog()
  }
}

function applyCatalogFilters(): void {
  query.page = 1
  void fetchCatalog()
}

function resetFilters(): void {
  query.keyword = ''
  query.species = ''
  query.dosageForm = ''
  query.status = ''
  query.page = 1
  void fetchCatalog()
}

function changePage(page: number): void {
  if (tab.value === 'BANNED') {
    bannedPage.value = page
    void fetchBanned()
  } else if (tab.value === 'RECALL') {
    recallsPage.value = page
    void fetchRecalls()
  } else {
    query.page = page
    void fetchCatalog()
  }
}

function changeSize(size: number): void {
  if (tab.value === 'BANNED') {
    bannedSize.value = size
    bannedPage.value = 1
    void fetchBanned()
  } else if (tab.value === 'RECALL') {
    recallsSize.value = size
    recallsPage.value = 1
    void fetchRecalls()
  } else {
    query.size = size
    query.page = 1
    void fetchCatalog()
  }
}

function primaryUsages(row: DrugRow): DrugUsage[] {
  return (row.usages || []).filter((usage) => usage.applicable)
}

function doseText(row: DrugRow): string {
  const usages = primaryUsages(row)
  if (!usages.length) return '—'
  return usages.map((usage) => `${usage.species_text || usage.species} ${usage.admin_route_text || ''} ${usage.recommended_dose ?? ''} ${usage.dose_unit_text || ''}`).join('；')
}

function withdrawalItems(row: DrugRow): DrugUsage[] {
  return primaryUsages(row).filter((usage) => usage.withdrawal_days !== null && usage.withdrawal_days !== undefined)
}

/* ---------------- 药品编辑 ---------------- */
const drawer = ref(false)
const editorLoading = ref(false)
const editing = ref<DrugDetail | null>(null)
const saving = ref(false)
const formError = ref('')
const formErrors = reactive<Record<string, string>>({})
const methodDicts = ref<DictItem[]>([])
const disinfectDicts = ref<DictItem[]>([])
const vaccineDoseUnitOptions = [
  { title: '头份 / 头', value: 'DOSE_PER_HEAD' },
  { title: '毫升 / 头', value: 'ML_PER_HEAD' },
  { title: '羽份 / 只', value: 'DOSE_PER_BIRD' }
]
const measureUnits = [
  { title: '克', value: 'G' },
  { title: '毫升', value: 'ML' },
  { title: '升', value: 'L' },
  { title: '头份', value: 'DOSE' },
  { title: '千克', value: 'KG' }
]
const adminRoutes = [
  { title: '混饲', value: 'MIX_FEED' },
  { title: '混饮', value: 'MIX_WATER' },
  { title: '肌肉注射', value: 'IM' },
  { title: '皮下注射', value: 'SC' },
  { title: '静脉注射', value: 'IV' },
  { title: '口服', value: 'ORAL' },
  { title: '外用', value: 'EXTERNAL' }
]
const drugDoseUnits = [
  { title: '克/吨料', value: 'G_PER_T_FEED' },
  { title: '克/升水', value: 'G_PER_L_WATER' },
  { title: '毫升/10千克体重', value: 'ML_PER_10KG' },
  { title: '毫克/千克体重', value: 'MG_PER_KG' },
  { title: '毫升/头', value: 'ML_PER_HEAD' },
  { title: '克/头', value: 'G_PER_HEAD' }
]
const form = reactive({
  drug_category: 'VET_DRUG',
  name: '',
  alias: '',
  dosage_form: '',
  spec: '',
  measure_unit: 'G',
  pack_unit: '袋',
  pack_size: '100',
  vaccine_dose_unit: 'DOSE_PER_HEAD',
  default_vaccinate_method_code: 'IM',
  concentration: '',
  disinfect_method_codes: [] as string[],
  change_reason: '',
  version: 0,
  archive_farm_count: 0,
  usages: [] as Array<{
    species: string
    species_text: string
    applicable: boolean
    admin_route: string
    recommended_dose: string
    dose_unit: string
    course_days: number | null
    withdrawal_days: number | null
  }>
})

function emptyUsages(): typeof form.usages {
  return speciesOptions.map((species) => ({
    species: species.value,
    species_text: species.title,
    applicable: true,
    admin_route: form.drug_category === 'VACCINE' ? 'IM' : 'MIX_FEED',
    recommended_dose: '',
    dose_unit: form.drug_category === 'VACCINE' ? 'DOSE_PER_HEAD' : 'G_PER_T_FEED',
    course_days: 5,
    withdrawal_days: 0
  }))
}

function resetForm(): void {
  form.drug_category = tab.value === 'BANNED' || tab.value === 'RECALL' ? 'VET_DRUG' : tab.value
  form.name = ''
  form.alias = ''
  form.dosage_form = ''
  form.spec = ''
  form.measure_unit = form.drug_category === 'VACCINE' ? 'DOSE' : 'G'
  form.pack_unit = '袋'
  form.pack_size = '100'
  form.vaccine_dose_unit = 'DOSE_PER_HEAD'
  form.default_vaccinate_method_code = 'IM'
  form.concentration = ''
  form.disinfect_method_codes = []
  form.change_reason = ''
  form.version = 0
  form.archive_farm_count = 0
  form.usages = emptyUsages()
  Object.keys(formErrors).forEach((key) => delete formErrors[key])
  formError.value = ''
}

function openCreate(): void {
  editing.value = null
  resetForm()
  if (tab.value !== 'BANNED' && tab.value !== 'RECALL') form.drug_category = tab.value
  drawer.value = true
}

async function openEdit(row: DrugRow): Promise<void> {
  drawer.value = true
  editorLoading.value = true
  editing.value = null
  resetForm()
  try {
    const data = await getDrugDetail(row.id)
    editing.value = data
    form.drug_category = data.drug_category
    form.name = data.name
    form.alias = data.alias || ''
    form.dosage_form = data.dosage_form
    form.spec = data.spec
    form.measure_unit = data.measure_unit
    form.pack_unit = data.pack_unit
    form.pack_size = String(data.pack_size ?? '')
    form.vaccine_dose_unit = data.vaccine_dose_unit || 'DOSE_PER_HEAD'
    form.default_vaccinate_method_code = data.default_vaccinate_method_code || 'IM'
    form.concentration = data.concentration || ''
    form.disinfect_method_codes = (data.disinfect_methods || []).map((method) => method.code)
    form.version = data.version || 0
    form.archive_farm_count = data.archive_farm_count || 0
    form.usages = speciesOptions.map((species) => {
      const usage = (data.usages || []).find((item) => item.species === species.value)
      return {
        species: species.value,
        species_text: species.title,
        applicable: usage?.applicable !== false,
        admin_route: usage?.admin_route || (data.drug_category === 'VACCINE' ? 'IM' : 'MIX_FEED'),
        recommended_dose: usage?.recommended_dose !== null && usage?.recommended_dose !== undefined ? String(usage.recommended_dose) : '',
        dose_unit: usage?.dose_unit || (data.drug_category === 'VACCINE' ? 'DOSE_PER_HEAD' : 'G_PER_T_FEED'),
        course_days: usage?.course_days ?? 5,
        withdrawal_days: usage?.withdrawal_days ?? 0
      }
    })
  } catch (e) {
    drawer.value = false
    ui.toast(e instanceof Error ? e.message : '药品详情加载失败', 'error')
  } finally {
    editorLoading.value = false
  }
}

function validateDrug(): boolean {
  Object.keys(formErrors).forEach((key) => delete formErrors[key])
  formError.value = ''
  if (!form.name.trim()) formErrors.name = '请填写通用名称'
  if (!form.dosage_form.trim()) formErrors.dosage_form = '请填写剂型'
  if (!form.spec.trim()) formErrors.spec = '请填写规格'
  if (!form.pack_unit.trim()) formErrors.pack_unit = '请填写包装单位'
  if (!form.pack_size || Number(form.pack_size) <= 0) formErrors.pack_size = '每个包装折合数量必须大于 0'
  if (form.drug_category === 'VACCINE' && !form.vaccine_dose_unit) formErrors.vaccine_dose_unit = '请选择疫苗剂量单位'
  if (editing.value && !form.change_reason.trim()) formErrors.change_reason = '请填写修改原因，记入操作日志'
  if (form.drug_category !== 'DISINFECTANT') {
    for (const usage of form.usages) {
      if (!usage.applicable) continue
      if (!usage.recommended_dose || Number(usage.recommended_dose) <= 0) formErrors.usages = `请填写${usage.species_text}的推荐剂量`
      if (!usage.course_days || usage.course_days < 1 || usage.course_days > 60) formErrors.usages = '推荐疗程必须在 1–60 天之间'
      if (usage.withdrawal_days === null || usage.withdrawal_days === undefined || usage.withdrawal_days < 0 || usage.withdrawal_days > 999) formErrors.usages = '休药期必须在 0–999 天之间'
    }
  }
  return !Object.keys(formErrors).length
}

function buildPayload() {
  return {
    drug_category: form.drug_category,
    name: form.name.trim(),
    alias: form.alias.trim() || undefined,
    dosage_form: form.dosage_form.trim(),
    spec: form.spec.trim(),
    measure_unit: form.measure_unit,
    pack_unit: form.pack_unit.trim(),
    pack_size: form.pack_size,
    vaccine_dose_unit: form.drug_category === 'VACCINE' ? form.vaccine_dose_unit : null,
    default_vaccinate_method_code: form.drug_category === 'VACCINE' ? form.default_vaccinate_method_code : null,
    concentration: form.drug_category === 'DISINFECTANT' ? form.concentration.trim() || null : null,
    disinfect_method_codes: form.drug_category === 'DISINFECTANT' ? form.disinfect_method_codes : [],
    usages: form.drug_category === 'DISINFECTANT'
      ? []
      : form.usages.map((usage) => ({
        species: usage.species,
        applicable: usage.applicable,
        admin_route: usage.applicable ? usage.admin_route : null,
        recommended_dose: usage.applicable ? usage.recommended_dose : null,
        dose_unit: usage.applicable ? usage.dose_unit : null,
        course_days: usage.applicable ? usage.course_days : null,
        withdrawal_days: usage.withdrawal_days
      })),
    change_reason: form.change_reason.trim() || undefined,
    version: editing.value?.version
  }
}

async function submitDrug(): Promise<void> {
  if (!validateDrug()) return
  saving.value = true
  formError.value = ''
  try {
    if (editing.value) await updateDrug(editing.value.id, buildPayload())
    else await createDrug(buildPayload())
    drawer.value = false
    ui.toast(editing.value ? '已保存' : `已保存“${form.name.trim()}”`)
    await fetchCatalog()
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

async function loadDicts(): Promise<void> {
  try {
    const [methods, disinfects] = await Promise.all([getDictItems('VACCINATE_METHOD'), getDictItems('DISINFECT_METHOD')])
    methodDicts.value = methods || []
    disinfectDicts.value = disinfects || []
  } catch {
    methodDicts.value = []
    disinfectDicts.value = []
  }
}

const methodOptions = computed(() => methodDicts.value.filter((item) => item.status === 'ENABLED').map((item) => ({ title: item.name, value: item.code })))
const disinfectOptions = computed(() => disinfectDicts.value.filter((item) => item.status === 'ENABLED').map((item) => ({ title: item.name, value: item.code })))
const isVaccine = computed(() => form.drug_category === 'VACCINE')
const isDisinfectant = computed(() => form.drug_category === 'DISINFECTANT')

/* ---------------- 停用 / 启用 ---------------- */
const statusOpen = ref(false)
const statusTarget = ref<DrugRow | null>(null)
const statusValue = ref('DISABLED')
const statusReason = ref('')
const statusError = ref('')
const statusSaving = ref(false)
const statusVersion = ref(0)

async function openStatus(row: DrugRow, status: string): Promise<void> {
  statusTarget.value = row
  statusValue.value = status
  statusVersion.value = row.version || 0
  statusReason.value = ''
  statusError.value = ''
  statusOpen.value = true
  try {
    const detail = await getDrugDetail(row.id)
    statusVersion.value = detail.version || 0
  } catch {
    /* 版本取不到时由服务端返回冲突提示 */
  }
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
    await setDrugStatus(statusTarget.value.id, statusValue.value, statusReason.value.trim(), statusVersion.value)
    statusOpen.value = false
    ui.toast(statusValue.value === 'DISABLED' ? `已停用${statusTarget.value.name}` : `已启用${statusTarget.value.name}`)
    await fetchCatalog()
  } catch (e) {
    statusError.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    statusSaving.value = false
  }
}

/* ---------------- 禁用药 ---------------- */
const bannedList = ref<BannedDrug[]>([])
const bannedTotal = ref(0)
const bannedPage = ref(1)
const bannedSize = ref(20)
const bannedKeyword = ref('')
const bannedClass = ref('')
const bannedLoading = ref(false)
const bannedError = ref('')
const bannedHeaders = [
  { title: '名称', key: 'name', sortable: false, width: 200 },
  { title: '别名', key: 'alias', sortable: false, minWidth: 220 },
  { title: '类别', key: 'banned_class_text', sortable: false, width: 160 },
  { title: '列入时间', key: 'listed_at', sortable: false, width: 130 },
  { title: '近 30 天拦截', key: 'hit_count_30d', sortable: false, width: 110, align: 'end' as const },
  { title: '操作', key: 'actions', sortable: false, width: 120, align: 'end' as const }
]

async function fetchBanned(): Promise<void> {
  bannedLoading.value = true
  bannedError.value = ''
  try {
    const data = await getBannedDrugs({ keyword: bannedKeyword.value.trim() || undefined, banned_class: bannedClass.value || undefined, page: bannedPage.value, size: bannedSize.value })
    bannedList.value = data.list || []
    bannedTotal.value = data.total || 0
    tabCounts.BANNED = data.total || 0
  } catch (e) {
    bannedError.value = e instanceof Error ? e.message : '禁用药加载失败'
    bannedList.value = []
  } finally {
    bannedLoading.value = false
  }
}

const bannedOpen = ref(false)
const bannedSaving = ref(false)
const bannedError2 = ref('')
const bannedTarget = ref<DrugRow | null>(null)
const bannedForm = reactive({
  catalog_id: null as number | null,
  name: '',
  alias: '',
  banned_class: 'BETA_AGONIST',
  reason: ''
})
const bannedEditing = ref<BannedDrug | null>(null)

function openAddBanned(row?: DrugRow): void {
  bannedTarget.value = row || null
  bannedEditing.value = null
  bannedForm.catalog_id = row?.id || null
  bannedForm.name = row?.name || ''
  bannedForm.alias = row?.alias || ''
  bannedForm.banned_class = 'BETA_AGONIST'
  bannedForm.reason = ''
  bannedError2.value = ''
  bannedOpen.value = true
}

function openEditBanned(row: BannedDrug): void {
  bannedEditing.value = row
  bannedTarget.value = null
  bannedForm.catalog_id = row.catalog_id || null
  bannedForm.name = row.name
  bannedForm.alias = row.alias || ''
  bannedForm.banned_class = row.banned_class
  bannedForm.reason = row.reason || ''
  bannedError2.value = ''
  bannedOpen.value = true
}

async function submitBanned(): Promise<void> {
  if (!bannedEditing.value && !bannedForm.catalog_id && !bannedForm.name.trim()) {
    bannedError2.value = '请填写名称'
    return
  }
  if (!bannedForm.banned_class) {
    bannedError2.value = '请选择禁用类别'
    return
  }
  if (!bannedEditing.value && !bannedForm.reason.trim()) {
    bannedError2.value = '请填写列入原因'
    return
  }
  bannedSaving.value = true
  bannedError2.value = ''
  try {
    if (bannedEditing.value) {
      await updateBannedDrug(bannedEditing.value.id, { alias: bannedForm.alias.trim(), banned_class: bannedForm.banned_class, version: bannedEditing.value.version || 0 })
      ui.toast('已保存')
    } else {
      await addBannedDrug({
        catalog_id: bannedForm.catalog_id,
        name: bannedForm.catalog_id ? undefined : bannedForm.name.trim(),
        alias: bannedForm.alias.trim() || undefined,
        banned_class: bannedForm.banned_class,
        reason: bannedForm.reason.trim()
      })
      ui.toast('已列入禁用药，用户端不能再选择')
    }
    bannedOpen.value = false
    await fetchBanned()
    if (tab.value === 'VET_DRUG' || tab.value === 'VACCINE' || tab.value === 'DISINFECTANT') await fetchCatalog()
  } catch (e) {
    bannedError2.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    bannedSaving.value = false
  }
}

const hitsOpen = ref(false)
const hitsLoading = ref(false)
const hits = ref<DrugHit[]>([])
const hitsTotal = ref(0)
const hitsTarget = ref<BannedDrug | null>(null)

async function openHits(row: BannedDrug): Promise<void> {
  hitsTarget.value = row
  hitsOpen.value = true
  hitsLoading.value = true
  try {
    const data = await getBannedHits(row.id, { page: 1, size: 50 })
    hits.value = data.list || []
    hitsTotal.value = data.total || 0
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '拦截记录加载失败', 'error')
    hits.value = []
  } finally {
    hitsLoading.value = false
  }
}

/* ---------------- 批号停用 ---------------- */
const recalls = ref<DrugLotRecall[]>([])
const recallsTotal = ref(0)
const recallsPage = ref(1)
const recallsSize = ref(20)
const recallsLoading = ref(false)
const recallsError = ref('')
const recallHeaders = [
  { title: '药品', key: 'catalog_name', sortable: false, width: 190 },
  { title: '生产批号', key: 'lot_no', sortable: false, width: 110 },
  { title: '有效期至', key: 'expire_date', sortable: false, width: 110 },
  { title: '停用原因', key: 'reason', sortable: false, minWidth: 200 },
  { title: '影响', key: 'impact', sortable: false, width: 190 },
  { title: '停用时间', key: 'recalled_at', sortable: false, width: 160 },
  { title: '状态', key: 'status', sortable: false, width: 84 },
  { title: '操作', key: 'actions', sortable: false, width: 92, align: 'end' as const }
]

async function fetchRecalls(): Promise<void> {
  recallsLoading.value = true
  recallsError.value = ''
  try {
    const data = await getLotRecalls({ page: recallsPage.value, size: recallsSize.value })
    recalls.value = data.list || []
    recallsTotal.value = data.total || 0
    tabCounts.RECALL = data.total || 0
  } catch (e) {
    recallsError.value = e instanceof Error ? e.message : '批号停用加载失败'
    recalls.value = []
  } finally {
    recallsLoading.value = false
  }
}

const recallOpen = ref(false)
const recallSaving = ref(false)
const recallError = ref('')
const recallDrugs = ref<DrugRow[]>([])
const recallForm = reactive({ catalog_id: null as number | null, lot_no: '', reason: '' })
const recallPreview = ref<RecallPreview | null>(null)
const previewLoading = ref(false)
let previewTimer = 0

async function openRecallCreate(): Promise<void> {
  recallOpen.value = true
  recallForm.catalog_id = null
  recallForm.lot_no = ''
  recallForm.reason = ''
  recallPreview.value = null
  recallError.value = ''
  try {
    const [vet, vaccine] = await Promise.all([
      getDrugCatalog({ drug_category: 'VET_DRUG', status: 'ENABLED', page: 1, size: 100 }),
      getDrugCatalog({ drug_category: 'VACCINE', status: 'ENABLED', page: 1, size: 100 })
    ])
    recallDrugs.value = [...(vet.list || []), ...(vaccine.list || [])]
  } catch {
    recallDrugs.value = []
  }
}

watch(() => [recallForm.catalog_id, recallForm.lot_no] as const, () => {
  window.clearTimeout(previewTimer)
  recallPreview.value = null
  if (!recallForm.catalog_id || !recallForm.lot_no.trim()) return
  previewTimer = window.setTimeout(() => { void loadPreview() }, 300)
})

async function loadPreview(): Promise<void> {
  if (!recallForm.catalog_id || !recallForm.lot_no.trim()) return
  previewLoading.value = true
  try {
    recallPreview.value = await previewLotRecall(recallForm.catalog_id, recallForm.lot_no.trim())
  } catch (e) {
    recallError.value = e instanceof Error ? e.message : '批号预览失败'
  } finally {
    previewLoading.value = false
  }
}

async function submitRecall(): Promise<void> {
  recallError.value = ''
  if (!recallForm.catalog_id) {
    recallError.value = '请选择药品'
    return
  }
  if (!recallForm.lot_no.trim()) {
    recallError.value = '请填写生产批号'
    return
  }
  if (!recallForm.reason.trim()) {
    recallError.value = '请填写停用原因'
    return
  }
  recallSaving.value = true
  try {
    const result = await createLotRecall({ catalog_id: recallForm.catalog_id, lot_no: recallForm.lot_no.trim(), reason: recallForm.reason.trim() })
    recallOpen.value = false
    ui.toast(`已停用批号 ${result.lot_no}，已通知 ${result.notified_farm_count || result.farm_count || 0} 个养殖场`)
    await fetchRecalls()
  } catch (e) {
    recallError.value = e instanceof Error ? e.message : '停用失败'
  } finally {
    recallSaving.value = false
  }
}

const progressOpen = ref(false)
const progressLoading = ref(false)
const progressTarget = ref<DrugLotRecall | null>(null)
const farms = ref<RecallFarm[]>([])
const farmsTotal = ref(0)

async function openProgress(row: DrugLotRecall): Promise<void> {
  progressOpen.value = true
  progressTarget.value = row
  progressLoading.value = true
  try {
    const data = await getRecallFarms(row.id, { page: 1, size: 50 })
    farms.value = data.list || []
    farmsTotal.value = data.total || 0
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '处理进度加载失败', 'error')
    farms.value = []
  } finally {
    progressLoading.value = false
  }
}

function categoryText(category: string): string {
  return { VET_DRUG: '兽药', VACCINE: '疫苗', DISINFECTANT: '消毒剂' }[category] || category
}

onMounted(async () => {
  listLoading.value = true
  void loadDicts()
  await fetchCatalog()
})
</script>

<template>
  <zw-shell side="admin" active="WA-D03" :crumbs="['基础数据', '兽药疫苗目录']">
    <zw-page-head title="兽药疫苗目录" desc="养殖户的药品档案、入库和登记用药只能从这里选；休药期决定用户端的可出栏日期。停用批号、标记禁用药、修改休药期都要填写原因，并记入操作日志。">
      <template #actions>
        <v-btn v-if="canHandle && tab === 'RECALL'" color="error" variant="outlined" prepend-icon="block" @click="openRecallCreate">停用批号</v-btn>
        <v-btn v-else-if="canHandle && tab === 'BANNED'" color="primary" prepend-icon="add" @click="openAddBanned()">添加禁用药</v-btn>
        <v-btn v-else-if="canHandle" color="primary" prepend-icon="add" @click="openCreate">新增药品</v-btn>
      </template>
      <v-tabs :model-value="tab" class="zw-tabs-line drug-tabs" @update:model-value="switchTab($event as Tab)">
        <v-tab v-for="item in tabs" :key="item.value" :value="item.value">{{ item.label }} {{ item.count }}</v-tab>
      </v-tabs>
    </zw-page-head>

    <zw-notice v-if="forbidden" type="error" title="你没有兽药疫苗目录的查看权限">请联系超级管理员在“管理员与权限”中开通 WA-D03 的查看权限。</zw-notice>

    <template v-if="!forbidden && (tab === 'VET_DRUG' || tab === 'VACCINE' || tab === 'DISINFECTANT')">
      <zw-notice v-if="tab === 'DISINFECTANT'" type="info">消毒剂即用户端的“消毒剂字典”。停用后不能再建档和入库，已有库存照常使用。</zw-notice>
      <zw-card flush>
        <div class="zw-toolbar drug-toolbar">
          <div class="zw-toolbar-l">
            <v-text-field v-model="query.keyword" class="zw-w-240" placeholder="搜索通用名称或别名" prepend-inner-icon="search" density="compact" hide-details clearable @keydown.enter="applyCatalogFilters" @click:clear="applyCatalogFilters" />
            <v-select v-if="tab !== 'DISINFECTANT'" v-model="query.species" :items="[{ title: '全部适用畜种', value: '' }, ...speciesOptions]" class="zw-w-140" density="compact" hide-details @update:model-value="applyCatalogFilters" />
            <v-select v-model="query.dosageForm" :items="[{ title: '全部剂型', value: '' }, ...dosageFormOptions]" class="zw-w-120" density="compact" hide-details @update:model-value="applyCatalogFilters" />
            <v-select v-model="query.status" :items="statusOptions" class="zw-w-120" density="compact" hide-details @update:model-value="applyCatalogFilters" />
            <v-btn variant="text" color="primary" @click="applyCatalogFilters">查询</v-btn>
          </div>
          <span class="zw-muted drug-tip">休药期以国家兽药标准为准</span>
        </div>
        <v-skeleton-loader v-if="listLoading" type="table-row@7" />
        <zw-empty v-else-if="listError" icon="cloud_off" title="目录加载失败" :desc="listError" tone="error">
          <v-btn variant="outlined" @click="fetchCatalog()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!list.length && (query.keyword || query.species || query.status || query.dosageForm)" icon="search_off" title="没有符合条件的药品" desc="换一个关键词或筛选条件试试。">
          <v-btn variant="text" color="primary" @click="resetFilters">清除筛选</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!list.length" icon="medication" title="这个分类还没有药品" desc="新增药品后，养殖户才能建档和入库。">
          <v-btn v-if="canHandle" color="primary" @click="openCreate">新增药品</v-btn>
        </zw-empty>
        <template v-else>
          <v-data-table :headers="catalogHeaders" :items="list" item-key="id" hide-default-footer density="compact" :loading="listLoading" class="drug-table">
            <template #item.name="{ item }">
              <div class="drug-name">
                <span>{{ item.name }}</span>
                <span v-if="item.alias" class="drug-alias" :title="item.alias">（{{ item.alias }}）</span>
                <zw-tag v-if="item.banned" type="error" icon="block" size="small">禁用药</zw-tag>
              </div>
            </template>
            <template #item.spec="{ item }">
              <span :title="`${item.dosage_form} · ${item.spec}`">{{ item.dosage_form }} · {{ item.spec }}</span>
            </template>
            <template #item.dose="{ item }">
              <div v-if="tab === 'DISINFECTANT'">{{ item.concentration || '—' }}<span class="drug-sub"> · {{ (item.disinfect_methods || []).map((m) => m.name).join('、') }}</span></div>
              <div v-else class="drug-dose" :title="doseText(item as DrugRow)">{{ doseText(item as DrugRow) || '—' }}</div>
            </template>
            <template #item.withdrawal="{ item }">
              <div v-if="tab === 'DISINFECTANT'" class="zw-faint">—</div>
              <div v-else class="drug-withdrawal">
                <span v-for="usage in withdrawalItems(item as DrugRow)" :key="usage.species" :class="{ 'drug-withdrawal--zero': !usage.withdrawal_days }">{{ usage.species_text }} {{ usage.withdrawal_days }} 天</span>
                <span v-if="!withdrawalItems(item as DrugRow).length" class="zw-faint">—</span>
              </div>
            </template>
            <template #item.status="{ item }">
              <zw-tag v-if="item.status === 'DISABLED'" size="small">已停用</zw-tag>
              <span v-else class="zw-muted">启用</span>
            </template>
            <template #item.archive_farm_count="{ item }"><span class="num">{{ formatNumber(item.archive_farm_count || 0) }}</span></template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a v-if="canHandle" class="zw-link" @click="openEdit(item as DrugRow)">编辑</a>
                <a v-if="canHandle && item.status === 'ENABLED'" class="zw-link danger" @click="openStatus(item as DrugRow, 'DISABLED')">停用</a>
                <a v-if="canHandle && item.status !== 'ENABLED'" class="zw-link" @click="openStatus(item as DrugRow, 'ENABLED')">启用</a>
                <a v-if="canHandle && !item.banned && tab !== 'DISINFECTANT'" class="zw-link danger" @click="openAddBanned(item as DrugRow)">标为禁用药</a>
              </div>
            </template>
          </v-data-table>
          <div class="zw-table-foot">
            <span class="zw-muted">“建档”为已建药品档案的养殖场数</span>
            <zw-pager :total="total" :page="query.page" :size="query.size" @update:page="changePage" @update:size="changeSize" />
          </div>
        </template>
      </zw-card>
    </template>

    <template v-if="!forbidden && tab === 'BANNED'">
      <zw-notice type="error" icon="block" title="禁用药不能入库、领用和登记用药">
        养殖户的药品选择中不出现禁用药，搜索命中时会提示“为禁用药，不能入库”；AI 问答回答顶部显示“禁用药，不能使用”。
      </zw-notice>
      <zw-card flush>
        <div class="zw-toolbar drug-toolbar">
          <div class="zw-toolbar-l">
            <v-text-field v-model="bannedKeyword" class="zw-w-240" placeholder="搜索名称或别名" prepend-inner-icon="search" density="compact" hide-details clearable @keydown.enter="fetchBanned" @click:clear="fetchBanned" />
            <v-select v-model="bannedClass" :items="[{ title: '全部类别', value: '' }, ...bannedClassOptions]" class="zw-w-160" density="compact" hide-details @update:model-value="fetchBanned" />
          </div>
        </div>
        <v-skeleton-loader v-if="bannedLoading" type="table-row@6" />
        <zw-empty v-else-if="bannedError" icon="cloud_off" title="禁用药加载失败" :desc="bannedError" tone="error">
          <v-btn variant="outlined" @click="fetchBanned()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!bannedList.length" icon="block" title="还没有禁用药" desc="从目录标记或直接添加禁用药后，用户端不能再选择。" />
        <template v-else>
          <v-data-table :headers="bannedHeaders" :items="bannedList" item-key="id" hide-default-footer density="compact">
            <template #item.name="{ item }"><span class="drug-banned-name"><zw-icon name="block" :size="16" />{{ item.name }}</span></template>
            <template #item.alias="{ item }">{{ item.alias || '—' }}</template>
            <template #item.banned_class_text="{ item }">{{ item.banned_class_text }}</template>
            <template #item.listed_at="{ item }">{{ item.listed_at ? formatDate(item.listed_at) : '—' }}</template>
            <template #item.hit_count_30d="{ item }"><span class="num">{{ formatNumber(item.hit_count30d || 0) }}</span></template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a v-if="canHandle" class="zw-link" @click="openEditBanned(item as BannedDrug)">编辑</a>
                <a class="zw-link" @click="openHits(item as BannedDrug)">拦截记录</a>
              </div>
            </template>
          </v-data-table>
          <div class="zw-table-foot"><span class="zw-muted">近 30 天拦截含用户端搜索、建档、入库和 AI 问答命中</span><zw-pager :total="bannedTotal" :page="bannedPage" :size="bannedSize" @update:page="changePage" @update:size="changeSize" /></div>
        </template>
      </zw-card>
    </template>

    <template v-if="!forbidden && tab === 'RECALL'">
      <zw-notice type="info">停用的生产批号在所有养殖场都不能出库，登记接种和用药时选不到，只能报废；有库存的养殖场的所有者和场长会收到“批号停用”消息。</zw-notice>
      <zw-card flush>
        <v-skeleton-loader v-if="recallsLoading" type="table-row@6" />
        <zw-empty v-else-if="recallsError" icon="cloud_off" title="批号停用加载失败" :desc="recallsError" tone="error">
          <v-btn variant="outlined" @click="fetchRecalls()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!recalls.length" icon="inventory_2" title="还没有停用的批号" desc="发现生产批号问题时可从这里停用，并通知有库存的养殖场。">
          <v-btn v-if="canHandle" color="error" variant="outlined" @click="openRecallCreate">停用批号</v-btn>
        </zw-empty>
        <template v-else>
          <v-data-table :headers="recallHeaders" :items="recalls" item-key="id" hide-default-footer density="compact">
            <template #item.catalog_name="{ item }"><div>{{ item.catalog_name }}</div><div class="drug-sub">{{ categoryText(item.drug_category) }}</div></template>
            <template #item.lot_no="{ item }"><span class="num">{{ item.lot_no }}</span></template>
            <template #item.expire_date="{ item }">{{ item.expire_date ? formatDate(item.expire_date) : '—' }}</template>
            <template #item.reason="{ item }"><span class="drug-reason" :title="item.reason">{{ item.reason }}</span></template>
            <template #item.impact="{ item }">{{ item.farm_count || 0 }} 个养殖场 · {{ formatNumber(item.total_qty, 2) }} {{ item.qty_unit_text || '' }}<div class="drug-sub">已报废 {{ item.scrapped_farm_count || 0 }} 个养殖场</div></template>
            <template #item.recalled_at="{ item }">{{ item.recalled_at ? formatDateTime(item.recalled_at) : '—' }}<span v-if="item.recalled_by_name" class="zw-muted"> · {{ item.recalled_by_name }}</span></template>
            <template #item.status><zw-tag type="error" icon="block" size="small">已停用</zw-tag></template>
            <template #item.actions="{ item }"><div class="zw-cell-actions"><a class="zw-link" @click="openProgress(item as DrugLotRecall)">查看影响</a></div></template>
          </v-data-table>
          <div class="zw-table-foot"><span class="zw-muted">停用后不能恢复；误停用时联系超级管理员处理</span><zw-pager :total="recallsTotal" :page="recallsPage" :size="recallsSize" @update:page="changePage" @update:size="changeSize" /></div>
        </template>
      </zw-card>
    </template>

    <!-- 药品编辑 -->
    <zw-drawer v-model="drawer" :title="editing ? `编辑${categoryText(form.drug_category)} · ${editing.name}` : '新增药品'" sub="目录信息供全平台使用；用户端药品档案中的名称和规格从这里带出" width="760">
      <v-skeleton-loader v-if="editorLoading" type="paragraph@8" />
      <div v-else class="drug-form">
        <zw-form-item label="类别" required>
          <zw-seg
            :model-value="form.drug_category"
            :items="[{ label: '兽药', value: 'VET_DRUG' }, { label: '疫苗', value: 'VACCINE' }, { label: '消毒剂', value: 'DISINFECTANT' }]"
            @update:model-value="form.drug_category = String($event); form.measure_unit = form.drug_category === 'VACCINE' ? 'DOSE' : form.measure_unit; form.usages = emptyUsages()"
          />
          <div v-if="editing && form.archive_farm_count" class="zw-form-hint">已有 {{ form.archive_farm_count }} 个养殖场建档，不能修改类别</div>
        </zw-form-item>
        <div class="zw-form-3">
          <zw-form-item label="通用名称" required :error="formErrors.name">
            <v-text-field v-model="form.name" placeholder="例如：氟苯尼考粉" maxlength="40" :error="!!formErrors.name" />
          </zw-form-item>
          <zw-form-item label="别名" optional :error="formErrors.alias">
            <v-text-field v-model="form.alias" placeholder="顿号分隔，搜索时也能找到" maxlength="100" />
          </zw-form-item>
          <zw-form-item label="剂型" required :error="formErrors.dosage_form">
            <v-text-field v-model="form.dosage_form" placeholder="例如：粉剂" maxlength="20" :error="!!formErrors.dosage_form" />
          </zw-form-item>
          <zw-form-item label="规格" required :error="formErrors.spec">
            <v-text-field v-model="form.spec" placeholder="例如：100 克/袋" maxlength="20" :error="!!formErrors.spec" />
          </zw-form-item>
          <zw-form-item label="记账单位" required>
            <v-select v-model="form.measure_unit" :items="measureUnits" :disabled="form.drug_category === 'VACCINE'" />
          </zw-form-item>
          <zw-form-item label="包装单位" required :error="formErrors.pack_unit">
            <v-text-field v-model="form.pack_unit" placeholder="例如：袋、瓶" maxlength="4" :error="!!formErrors.pack_unit" />
          </zw-form-item>
          <zw-form-item label="每个包装折合数量" required :error="formErrors.pack_size">
            <v-text-field v-model="form.pack_size" type="number" step="0.001" min="0" :suffix="form.measure_unit === 'DOSE' ? '头份' : ''" :error="!!formErrors.pack_size" />
          </zw-form-item>
          <zw-form-item v-if="isVaccine" label="疫苗剂量单位" required :error="formErrors.vaccine_dose_unit">
            <v-select v-model="form.vaccine_dose_unit" :items="vaccineDoseUnitOptions" />
          </zw-form-item>
          <zw-form-item v-if="isVaccine" label="默认接种方式" optional>
            <v-select v-model="form.default_vaccinate_method_code" :items="methodOptions" placeholder="请选择接种方式" />
          </zw-form-item>
          <zw-form-item v-if="isDisinfectant" label="推荐浓度" optional>
            <v-text-field v-model="form.concentration" placeholder="例如：1:500" maxlength="20" />
          </zw-form-item>
        </div>
        <zw-form-item v-if="isDisinfectant" label="适用消毒方式" optional>
          <v-select v-model="form.disinfect_method_codes" :items="disinfectOptions" multiple chips density="compact" hide-details placeholder="请选择消毒方式" />
        </zw-form-item>

        <template v-if="!isDisinfectant">
          <div class="zw-section-title">用量与休药期 <span class="zw-muted drug-form-sub">按畜种设置；关掉“适用”后用户端登记用药时提示不适用</span></div>
          <div class="zw-table-wrap">
            <v-table density="compact" class="drug-usage-table">
              <thead><tr><th>畜种</th><th>适用</th><th>给药方式</th><th>推荐剂量</th><th>剂量单位</th><th>推荐疗程</th><th>休药期</th></tr></thead>
              <tbody>
                <tr v-for="usage in form.usages" :key="usage.species" :class="{ 'drug-usage-row--off': !usage.applicable }">
                  <td><b>{{ usage.species_text }}</b></td>
                  <td><v-switch v-model="usage.applicable" density="compact" hide-details /></td>
                  <td><v-select v-model="usage.admin_route" :items="adminRoutes" density="compact" hide-details :disabled="!usage.applicable" /></td>
                  <td><v-text-field v-model="usage.recommended_dose" type="number" step="0.001" density="compact" hide-details :disabled="!usage.applicable" class="drug-dose-input" /></td>
                  <td><v-select v-model="usage.dose_unit" :items="isVaccine ? vaccineDoseUnitOptions : drugDoseUnits" density="compact" hide-details :disabled="!usage.applicable" class="drug-unit-select" /></td>
                  <td><v-text-field v-model.number="usage.course_days" type="number" min="1" max="60" density="compact" hide-details suffix="天" :disabled="!usage.applicable" class="drug-days-input" /></td>
                  <td><v-text-field v-model.number="usage.withdrawal_days" type="number" min="0" max="999" density="compact" hide-details suffix="天" :disabled="!usage.applicable" class="drug-days-input" /></td>
                </tr>
              </tbody>
            </v-table>
          </div>
        </template>

        <zw-notice v-if="!isDisinfectant" type="warning" title="休药期决定用户端的可出栏日期">
          保存休药期修改时需要填写原因。修改后用过本品的在养批次会按新休药期重新计算可出栏日期。
        </zw-notice>
        <zw-form-item v-if="editing" label="修改原因" required :error="formErrors.change_reason" hint="记入操作日志">
          <v-text-field v-model="form.change_reason" placeholder="请说明修改原因" maxlength="100" :error="!!formErrors.change_reason" />
        </zw-form-item>
        <div v-if="formError" class="drug-form-error">{{ formError }}</div>
      </div>
      <template #foot>
        <span class="zw-foot-note">保存记入操作日志</span>
        <v-btn variant="outlined" :disabled="saving" @click="drawer = false">取消</v-btn>
        <v-btn color="primary" :loading="saving" @click="submitDrug">保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 停用 / 启用 -->
    <zw-dialog v-model="statusOpen" :title="statusValue === 'DISABLED' ? '停用这个药品？' : '启用这个药品？'" width="480" :icon="statusValue === 'DISABLED' ? 'block' : 'check_circle'" :tone="statusValue === 'DISABLED' ? 'error' : 'primary'">
      <p class="drug-dialog-strong">{{ statusTarget?.name }}</p>
      <p class="drug-dialog-text">{{ statusValue === 'DISABLED' ? '停用后不能再建档和入库，已有库存照常使用。' : '启用后可以重新建档和入库；已列入禁用药的目录项不能启用。' }}</p>
      <zw-form-item :label="statusValue === 'DISABLED' ? '停用原因' : '启用原因'" required>
        <v-textarea v-model="statusReason" :placeholder="statusValue === 'DISABLED' ? '例如：按农业农村部公告停止使用' : '请填写启用原因'" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <div v-if="statusError" class="drug-form-error">{{ statusError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="statusSaving" @click="statusOpen = false">取消</v-btn>
        <v-btn :color="statusValue === 'DISABLED' ? 'error' : 'primary'" :loading="statusSaving" @click="submitStatus">{{ statusValue === 'DISABLED' ? '停用' : '启用' }}</v-btn>
      </template>
    </zw-dialog>

    <!-- 禁用药编辑 -->
    <zw-dialog v-model="bannedOpen" :title="bannedEditing ? '编辑禁用药' : bannedForm.catalog_id ? '把药品列为禁用药？' : '添加禁用药'" width="520" icon="block" tone="error">
      <p v-if="bannedForm.catalog_id" class="drug-dialog-strong">{{ bannedForm.name }}</p>
      <div v-if="!bannedEditing" class="zw-form">
        <zw-form-item v-if="!bannedForm.catalog_id" label="名称" required>
          <v-text-field v-model="bannedForm.name" placeholder="禁用药名称" maxlength="40" />
        </zw-form-item>
        <zw-form-item label="别名" optional>
          <v-text-field v-model="bannedForm.alias" placeholder="顿号分隔" maxlength="100" />
        </zw-form-item>
        <zw-form-item label="禁用类别" required>
          <v-select v-model="bannedForm.banned_class" :items="bannedClassOptions" />
        </zw-form-item>
        <zw-form-item label="列入原因" required>
          <v-textarea v-model="bannedForm.reason" placeholder="请填写公告或依据" :rows="2" maxlength="100" counter />
        </zw-form-item>
      </div>
      <div v-else class="zw-form">
        <zw-form-item label="别名" optional>
          <v-text-field v-model="bannedForm.alias" maxlength="100" />
        </zw-form-item>
        <zw-form-item label="禁用类别" required>
          <v-select v-model="bannedForm.banned_class" :items="bannedClassOptions" />
        </zw-form-item>
      </div>
      <p v-if="!bannedEditing" class="drug-dialog-text">列入后不能再入库和领用，现有库存只能登记报废；用户端搜索命中时会提示为禁用药。</p>
      <div v-if="bannedError2" class="drug-form-error">{{ bannedError2 }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="bannedSaving" @click="bannedOpen = false">取消</v-btn>
        <v-btn color="error" :loading="bannedSaving" @click="submitBanned">{{ bannedEditing ? '保存' : '列入禁用药' }}</v-btn>
      </template>
    </zw-dialog>

    <!-- 拦截记录 -->
    <zw-drawer v-model="hitsOpen" :title="`拦截记录 · ${hitsTarget?.name || ''}`" :sub="`近 30 天共 ${hitsTotal} 次`" width="640">
      <v-skeleton-loader v-if="hitsLoading" type="table-row@5" />
      <zw-empty v-else-if="!hits.length" icon="history" title="还没有拦截记录" desc="用户端搜索、建档、入库或 AI 问答命中禁用药时会记录。">
      </zw-empty>
      <v-table v-else density="compact">
        <thead><tr><th>时间</th><th>来源</th><th>养殖场</th><th>用户</th><th>关键词</th></tr></thead>
        <tbody><tr v-for="(hit, index) in hits" :key="index"><td>{{ formatDateTime(hit.hit_at) }}</td><td>{{ hit.hit_source_text }}</td><td>{{ hit.farm_name || '—' }}</td><td>{{ hit.user_name || '—' }}</td><td>{{ hit.keyword || '—' }}</td></tr></tbody>
      </v-table>
      <template #foot><v-btn variant="outlined" @click="hitsOpen = false">关闭</v-btn></template>
    </zw-drawer>

    <!-- 停用批号 -->
    <zw-drawer v-model="recallOpen" title="停用这个批号？" sub="停用后不能恢复；有库存的养殖场会收到消息" width="520">
      <div class="zw-form">
        <zw-form-item label="药品" required>
          <v-autocomplete v-model="recallForm.catalog_id" :items="recallDrugs.map((d) => ({ title: `${d.name}（${categoryText(d.drug_category)}）`, value: d.id }))" placeholder="搜索药品" />
        </zw-form-item>
        <zw-form-item label="生产批号" required>
          <v-text-field v-model="recallForm.lot_no" placeholder="例如：2025047" maxlength="30" />
        </zw-form-item>
        <div v-if="previewLoading" class="drug-dialog-text">正在查询库存…</div>
        <div v-else-if="recallPreview" class="drug-recall-preview">
          <div><span>有效期至</span><b>{{ recallPreview.expire_date ? formatDate(recallPreview.expire_date) : '—' }}</b></div>
          <div><span>库存</span><b>{{ recallPreview.farm_count }} 个养殖场有库存，合计 {{ formatNumber(recallPreview.total_qty, 2) }} {{ recallPreview.qty_unit_text || '' }}<template v-if="recallPreview.sample_farm_name">（其中 {{ recallPreview.sample_farm_name }} {{ formatNumber(recallPreview.sample_farm_qty, 2) }}）</template></b></div>
          <div><span>停用后</span><b>不能出库，登记接种时选不到，只能报废</b></div>
        </div>
        <zw-form-item label="停用原因" required hint="原样显示在用户端的批号详情中，也记入操作日志">
          <v-textarea v-model="recallForm.reason" placeholder="例如：生产企业召回，抽检效价不合格" :rows="2" maxlength="100" counter />
        </zw-form-item>
        <p class="drug-danger-text">停用后不能恢复。</p>
        <div v-if="recallError" class="drug-form-error">{{ recallError }}</div>
      </div>
      <template #foot>
        <v-btn variant="outlined" :disabled="recallSaving" @click="recallOpen = false">取消</v-btn>
        <v-btn color="error" :loading="recallSaving" @click="submitRecall">停用</v-btn>
      </template>
    </zw-drawer>

    <!-- 处理进度 -->
    <zw-drawer v-model="progressOpen" :title="`处理进度 · ${progressTarget?.catalog_name || ''} ${progressTarget?.lot_no || ''}`" sub="停用后各养殖场的库存只能报废" width="640">
      <v-skeleton-loader v-if="progressLoading" type="table-row@5" />
      <zw-empty v-else-if="!farms.length" icon="inventory_2" title="没有养殖场库存记录" desc="该批号没有养殖场入库记录，但仍已停用。" />
      <v-table v-else density="compact">
        <thead><tr><th>养殖场</th><th class="text-end">停用时结存</th><th>用户端显示</th><th>报废</th></tr></thead>
        <tbody>
          <tr v-for="farm in farms" :key="farm.farm_id">
            <td>{{ farm.farm_name }}</td>
            <td class="text-end num">{{ formatNumber(farm.qty_at_recall, 2) }} {{ farm.qty_unit_text }}</td>
            <td><zw-tag type="error" icon="block" size="small">已停用</zw-tag><div class="drug-sub">只能报废</div></td>
            <td><span v-if="farm.scrap_status === 'SCRAPPED'" class="zw-muted">{{ farm.scrapped_at ? formatDate(farm.scrapped_at) : '' }} 已报废</span><zw-tag v-else-if="farm.scrap_status === 'PARTIAL'" type="warning" size="small">部分报废</zw-tag><zw-tag v-else type="warning" size="small">未报废</zw-tag></td>
          </tr>
        </tbody>
      </v-table>
      <template #foot><v-btn variant="outlined" @click="progressOpen = false">关闭</v-btn></template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.drug-tabs { margin-top: 8px; }
.drug-toolbar { padding: 16px; }
.drug-tip { font-size: 12px; white-space: nowrap; }
.drug-table :deep(tbody tr) { height: 40px; }
.drug-table :deep(td) { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.drug-name { display: flex; align-items: center; gap: 4px; font-weight: 500; min-width: 0; }
.drug-alias { font-size: 11px; font-weight: 400; color: var(--text-3); max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.drug-sub { font-size: 12px; line-height: 18px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; }
.drug-dose { font-size: 12px; line-height: 18px; color: var(--text-2); max-width: 190px; overflow: hidden; text-overflow: ellipsis; }
.drug-withdrawal { display: flex; flex-wrap: nowrap; gap: 4px; }
.drug-withdrawal span { padding: 1px 6px; border-radius: 2px; background: var(--fill-2); font-size: 11px; }
.drug-withdrawal--zero { color: var(--text-3); }
.drug-banned-name { display: flex; align-items: center; gap: 4px; color: var(--error); font-weight: 500; }
.drug-reason { display: block; max-width: 230px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.drug-form { display: flex; flex-direction: column; gap: 16px; }
.drug-form-sub { font-size: 12px; font-weight: 400; color: var(--text-3); margin-left: 6px; }
.drug-usage-table :deep(td) { padding: 4px; height: 52px; }
.drug-usage-row--off td { color: var(--text-3); background: var(--fill-1); }
.drug-dose-input { width: 100px; }
.drug-unit-select { min-width: 126px; }
.drug-days-input { width: 82px; }
.drug-form-error { color: var(--error); font-size: 12px; margin-top: 6px; }
.drug-dialog-strong { font-size: 15px; font-weight: 600; margin: 0 0 6px; }
.drug-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); }
.drug-recall-preview { padding: 10px 14px; border-radius: var(--radius-sm); background: var(--fill-1); display: flex; flex-direction: column; gap: 8px; font-size: 13px; }
.drug-recall-preview > div { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 8px; }
.drug-recall-preview span { color: var(--text-2); }
.drug-recall-preview b { font-weight: 500; }
.drug-danger-text { font-size: 13px; color: var(--error); margin: 0; }
</style>
