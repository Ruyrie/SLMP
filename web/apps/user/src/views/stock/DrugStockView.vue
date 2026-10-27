<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, formatDate, formatMoney, formatNumber, today as todayIso } from '@zmt/shared'
import {
  createDrug,
  createDrugInbound,
  createDrugOutbound,
  fetchActiveBatches,
  fetchAllocationPreview,
  fetchCatalog,
  fetchDrug,
  fetchDrugFlows,
  fetchDrugLotOptions,
  fetchDrugOptions,
  fetchDrugs,
  fetchFarmMembers,
  fetchPenOptions,
  fetchRecentPartners,
  newRequestKey,
  updateDrug,
  type ActiveBatch,
  type CatalogItem,
  type CostAllocation,
  type DrugArchiveItem,
  type DrugDetail,
  type DrugFlowGroup,
  type DrugLotItem,
  type DrugLotOption,
  type DrugOption,
  type FarmMember,
  type PenOption,
  type StockFlowItem
} from '@/api/stock'
import StockDocumentDrawer from '@/views/stock/components/StockDocumentDrawer.vue'
import { useDrugStockStore } from '@/stores/stock/drug'
import { useFarmReload } from '@/composables/useFarmReload'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import {
  allocationText,
  categoryIcon,
  categoryText,
  datetimeText,
  daysText,
  flowIcon,
  flowQty,
  lotStatusTag,
  lotStatusText,
  moneyText,
  numberValue,
  packQtyText,
  qtyText,
  qtyUnit,
  qtyValue
} from '@/views/stock/stock'

const store = useDrugStockStore()
const farm = useFarmStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

const isStaff = computed(() => farm.role === 'staff')
const desc = computed(() => `${farm.farmName || '当前养殖场'} · 兽药、疫苗和消毒剂按“物资 + 批号”分别结存，登记接种、用药、消毒时先到期先出`)
const keyword = ref('')
let searchTimer = 0

watch(keyword, (value) => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => void store.search(value.trim()), 300)
})

function onCategory(value: string | number): void {
  void store.setCategory(String(value) as '' | 'VET_DRUG' | 'VACCINE' | 'DISINFECTANT')
}

onMounted(async () => {
  if (isStaff.value) return
  await store.load()
  if (route.query.action === 'outbound') {
    await openOutbound(Number(route.query.drugId || 0), Number(route.query.lotId || 0))
  } else if (route.query.action === 'archive') {
    await openArchive(Number(route.query.drugId || 0))
  }
})
useFarmReload(async () => {
  if (!isStaff.value) await store.load()
})

/* ---------------- 入库 ---------------- */
const inboundOpen = ref(false)
const inboundLoading = ref(false)
const inboundSubmitting = ref(false)
const inboundOptions = ref<DrugOption[]>([])
const inboundBannedHint = ref('')
const inboundForm = reactive({
  drugId: 0,
  lotNo: '',
  inboundDate: todayIso(),
  expireDate: '',
  qty: null as number | null,
  price: null as number | null,
  syncFinance: true
})
const inboundErrors = reactive<Record<string, string>>({})
const inboundDrug = computed(() => inboundOptions.value.find((item) => item.drug_id === inboundForm.drugId))
const inboundAmount = computed(() => numberValue(inboundForm.qty) * numberValue(inboundForm.price))
const inboundUnit = computed(() => inboundDrug.value?.pack_unit || '瓶')

async function openInbound(drugId = 0): Promise<void> {
  inboundOpen.value = true
  inboundLoading.value = true
  inboundForm.drugId = drugId
  inboundForm.lotNo = ''
  inboundForm.inboundDate = todayIso()
  inboundForm.expireDate = ''
  inboundForm.qty = null
  inboundForm.price = null
  inboundForm.syncFinance = true
  Object.keys(inboundErrors).forEach((key) => delete inboundErrors[key])
  try {
    const result = await fetchDrugOptions('INBOUND')
    inboundOptions.value = result.list || []
    inboundBannedHint.value = result.banned_hint || ''
    if (!drugId) inboundForm.drugId = inboundOptions.value[0]?.drug_id || 0
    const drug = inboundOptions.value.find((item) => item.drug_id === inboundForm.drugId)
    if (drug?.usable) inboundForm.qty = null
  } catch (error) {
    ui.toast((error as ApiError).message || '药品选项加载失败', 'error')
  } finally {
    inboundLoading.value = false
  }
}

function onInboundDrug(value: unknown): void {
  inboundForm.drugId = Number(value || 0)
  delete inboundErrors.drug_id
}

function submitInbound(): void {
  Object.keys(inboundErrors).forEach((key) => delete inboundErrors[key])
  if (!inboundForm.drugId) inboundErrors.drug_id = '请选择药品'
  if (!inboundForm.lotNo.trim()) inboundErrors.lot_no = '请填写生产批号'
  else if (!/^[A-Za-z0-9-]{1,30}$/.test(inboundForm.lotNo.trim())) inboundErrors.lot_no = '只能输入字母、数字和短横线，最多 30 个字符'
  if (!inboundForm.inboundDate) inboundErrors.inbound_date = '请选择入库日期'
  if (!inboundForm.expireDate) inboundErrors.expire_date = '请选择有效期'
  else if (inboundForm.expireDate <= inboundForm.inboundDate) inboundErrors.expire_date = '早于入库日期，不能入库'
  if (!numberValue(inboundForm.qty) || numberValue(inboundForm.qty) <= 0) inboundErrors.qty = '必须大于 0'
  if (!numberValue(inboundForm.price) || numberValue(inboundForm.price) <= 0) inboundErrors.price = '必须大于 0'
  if (Object.keys(inboundErrors).length) return
  void submitInboundAsync()
}

async function submitInboundAsync(): Promise<void> {
  if (inboundSubmitting.value) return
  inboundSubmitting.value = true
  try {
    await createDrugInbound(
      {
        drug_id: inboundForm.drugId,
        lot_no: inboundForm.lotNo.trim(),
        inbound_date: inboundForm.inboundDate,
        expire_date: inboundForm.expireDate,
        input_qty: numberValue(inboundForm.qty),
        input_unit: 'PACK',
        unit_price: numberValue(inboundForm.price),
        sync_finance: inboundForm.syncFinance
      },
      newRequestKey()
    )
    ui.toast(`已入库 ${formatNumber(inboundForm.qty)} ${inboundUnit.value}${inboundDrug.value?.name || ''}${inboundForm.syncFinance ? '，已同步记一笔支出' : ''}`)
    inboundOpen.value = false
    await store.load()
  } catch (error) {
    applyErrors(error as ApiError, inboundErrors)
    ui.toast((error as ApiError).message || '入库失败，请重试', 'error')
  } finally {
    inboundSubmitting.value = false
  }
}

/* ---------------- 出库 ---------------- */
const outboundOpen = ref(false)
const outboundLoading = ref(false)
const outboundSubmitting = ref(false)
const outboundType = ref<'USE' | 'RETURN'>('USE')
const outboundOptions = ref<DrugOption[]>([])
const lotOptions = ref<DrugLotOption[]>([])
const unselectableLots = ref<DrugLotOption[]>([])
const defaultLotId = ref(0)
const pens = ref<PenOption[]>([])
const batches = ref<ActiveBatch[]>([])
const members = ref<FarmMember[]>([])
const returnPartners = ref<string[]>([])
const allocation = ref<CostAllocation | null>(null)
const outboundForm = reactive({
  drugId: 0,
  lotId: 0,
  qty: null as number | null,
  targetType: 'BATCH' as 'BATCH' | 'PEN',
  batchId: 0,
  penId: 0,
  receiverId: 0,
  returnTo: '',
  returnReason: 'QUALITY',
  remark: '',
  outDate: todayIso()
})
const outboundErrors = reactive<Record<string, string>>({})
const outboundDrug = computed(() => outboundOptions.value.find((item) => item.drug_id === outboundForm.drugId))
const selectedLot = computed(() => lotOptions.value.find((item) => item.lot_id === outboundForm.lotId))
const outboundUnit = computed(() => (outboundDrug.value?.unit === 'DOSE' ? '头份' : outboundDrug.value?.pack_unit || '瓶'))
const outboundInputUnit = computed<'PACK' | 'MEASURE'>(() => (outboundDrug.value?.unit === 'DOSE' ? 'MEASURE' : 'PACK'))
const lotPackSize = computed(() => numberValue(selectedLot.value?.stock?.pack_size) || 1)
const lotPackPrice = computed(() => numberValue(selectedLot.value?.avg_price) * lotPackSize.value)
const maxInputQty = computed(() => {
  if (!selectedLot.value?.stock) return 0
  if (outboundInputUnit.value === 'MEASURE') return qtyValue(selectedLot.value.stock)
  return qtyValue(selectedLot.value.stock) / lotPackSize.value
})
const outboundPriceLabel = computed(() =>
  outboundInputUnit.value === 'MEASURE'
    ? `每${selectedLot.value?.stock?.unit_text || '头份'} ${formatMoney(selectedLot.value?.avg_price)} 元`
    : `每${outboundDrug.value?.pack_unit || '瓶'} ${formatMoney(lotPackPrice.value)} 元`
)
const outboundAmount = computed(() => {
  if (outboundType.value === 'RETURN') {
    return numberValue(outboundForm.qty) * (outboundInputUnit.value === 'MEASURE' ? numberValue(selectedLot.value?.avg_price) : lotPackPrice.value)
  }
  return numberValue(allocation.value?.total_amount)
})
const lotHint = computed(() => {
  const lot = selectedLot.value
  if (!lot) return '选择库存批号后显示有效期和结存'
  const parts = [`有效期至 ${lot.expire_date || '—'}`, lotStatusText(lot), `结存 ${packQtyText(lot.stock)}`]
  const earlier = lotOptions.value.find((item) => item.lot_id !== lot.lot_id && (item.expire_date || '') < (lot.expire_date || ''))
  if (earlier) parts.push(`批号 ${earlier.lot_no} 更早到期，建议优先领用`)
  return parts.join(' · ')
})
const targetPen = computed(() => pens.value.find((item) => item.pen_id === outboundForm.penId))
const targetBatch = computed(() => batches.value.find((item) => item.batch_id === outboundForm.batchId))

async function openOutbound(drugId = 0, lotId = 0): Promise<void> {
  outboundOpen.value = true
  outboundType.value = 'USE'
  outboundLoading.value = true
  outboundForm.drugId = drugId
  outboundForm.lotId = lotId
  outboundForm.qty = null
  outboundForm.targetType = 'BATCH'
  outboundForm.batchId = 0
  outboundForm.penId = 0
  outboundForm.receiverId = 0
  outboundForm.returnTo = ''
  outboundForm.returnReason = 'QUALITY'
  outboundForm.remark = ''
  outboundForm.outDate = todayIso()
  allocation.value = null
  Object.keys(outboundErrors).forEach((key) => delete outboundErrors[key])
  try {
    const [options, penList, batchPage, memberPage, partners] = await Promise.all([
      fetchDrugOptions('OUTBOUND'),
      fetchPenOptions().catch(() => []),
      fetchActiveBatches().catch(() => ({ list: [], total: 0, page: 1, size: 20, has_more: false })),
      farm.currentFarmId ? fetchFarmMembers(farm.currentFarmId).catch(() => ({ list: [], total: 0, page: 1, size: 20, has_more: false })) : Promise.resolve({ list: [], total: 0, page: 1, size: 20, has_more: false }),
      fetchRecentPartners('RETURN_TO').catch(() => [])
    ])
    outboundOptions.value = options.list || []
    pens.value = penList.filter((pen) => Number(pen.stock_count || 0) > 0)
    batches.value = batchPage.list || []
    members.value = memberPage.list || []
    returnPartners.value = partners
    if (!drugId) outboundForm.drugId = outboundOptions.value[0]?.drug_id || 0
    if (outboundForm.drugId) await loadLotOptions(lotId)
  } catch (error) {
    ui.toast((error as ApiError).message || '出库选项加载失败', 'error')
  } finally {
    outboundLoading.value = false
  }
}

async function loadLotOptions(preferLotId = 0): Promise<void> {
  if (!outboundForm.drugId) return
  const data = await fetchDrugLotOptions(outboundForm.drugId, outboundType.value === 'RETURN' ? 'RETURN' : 'USE')
  lotOptions.value = data.selectable || []
  unselectableLots.value = data.unselectable || []
  defaultLotId.value = Number(data.default_lot_id || 0)
  if (preferLotId) outboundForm.lotId = preferLotId
  else outboundForm.lotId = outboundType.value === 'RETURN' ? 0 : defaultLotId.value || lotOptions.value[0]?.lot_id || 0
  await refreshAllocation()
}

async function onOutboundDrug(value: unknown): Promise<void> {
  outboundForm.drugId = Number(value || 0)
  delete outboundErrors.drug_id
  await loadLotOptions()
}

function onOutboundType(value: string | number): void {
  outboundType.value = value === 'RETURN' ? 'RETURN' : 'USE'
  outboundForm.qty = null
  allocation.value = null
  void loadLotOptions()
}

function onLot(value: unknown): void {
  outboundForm.lotId = Number(value || 0)
  delete outboundErrors.lot_id
  void refreshAllocation()
}

function onTargetType(value: string | number): void {
  outboundForm.targetType = value === 'PEN' ? 'PEN' : 'BATCH'
  outboundForm.batchId = 0
  outboundForm.penId = 0
  allocation.value = null
  void refreshAllocation()
}

async function refreshAllocation(): Promise<void> {
  if (outboundType.value === 'RETURN' || !outboundForm.lotId || !numberValue(outboundForm.qty)) {
    allocation.value = null
    return
  }
  if (outboundForm.targetType === 'BATCH' && !outboundForm.batchId) {
    allocation.value = null
    return
  }
  if (outboundForm.targetType === 'PEN' && !outboundForm.penId) {
    allocation.value = null
    return
  }
  try {
    allocation.value = await fetchAllocationPreview({
      drug_lot_id: outboundForm.lotId,
      input_qty: numberValue(outboundForm.qty),
      input_unit: outboundInputUnit.value,
      target_type: outboundForm.targetType,
      ...(outboundForm.targetType === 'BATCH' ? { batch_id: outboundForm.batchId } : { pen_id: outboundForm.penId })
    })
  } catch {
    allocation.value = null
  }
}

function submitOutbound(): void {
  Object.keys(outboundErrors).forEach((key) => delete outboundErrors[key])
  if (!outboundForm.drugId) outboundErrors.drug_id = '请选择药品'
  if (!outboundForm.lotId) outboundErrors.lot_id = '请选择库存批号'
  if (!numberValue(outboundForm.qty) || numberValue(outboundForm.qty) <= 0) outboundErrors.qty = '必须大于 0'
  else if (numberValue(outboundForm.qty) > maxInputQty.value + 0.0001) {
    outboundErrors.qty = `超过该批号结存 ${formatNumber(maxInputQty.value, 2)} ${outboundUnit.value}，出库后库存不能为负数`
  }
  if (outboundType.value === 'USE') {
    if (!outboundForm.receiverId) outboundErrors.receiver_id = '请选择领用人'
    if (outboundForm.targetType === 'BATCH' && !outboundForm.batchId) outboundErrors.batch_id = '请选择批次'
    if (outboundForm.targetType === 'PEN' && !outboundForm.penId) outboundErrors.pen_id = '请选择栏舍'
  } else {
    if (!outboundForm.returnTo.trim()) outboundErrors.return_to = '请填写退回对象'
    if (!outboundForm.returnReason) outboundErrors.return_reason = '请选择退货原因'
  }
  if (Object.keys(outboundErrors).length) return
  void submitOutboundAsync()
}

async function submitOutboundAsync(): Promise<void> {
  if (outboundSubmitting.value) return
  outboundSubmitting.value = true
  try {
    const result = await createDrugOutbound(
      {
        out_type: outboundType.value,
        drug_id: outboundForm.drugId,
        drug_lot_id: outboundForm.lotId,
        input_qty: numberValue(outboundForm.qty),
        input_unit: outboundInputUnit.value,
        target_type: outboundType.value === 'USE' ? outboundForm.targetType : undefined,
        batch_id: outboundType.value === 'USE' && outboundForm.targetType === 'BATCH' ? outboundForm.batchId : undefined,
        pen_id: outboundType.value === 'USE' && outboundForm.targetType === 'PEN' ? outboundForm.penId : undefined,
        receiver_id: outboundType.value === 'USE' ? outboundForm.receiverId : undefined,
        return_to: outboundType.value === 'RETURN' ? outboundForm.returnTo.trim() : undefined,
        return_reason: outboundType.value === 'RETURN' ? outboundForm.returnReason : undefined,
        remark: outboundForm.remark.trim() || undefined,
        out_date: outboundForm.outDate,
        stock_version: Number(selectedLot.value?.stock_version || 0)
      },
      newRequestKey()
    )
    const label = outboundType.value === 'RETURN' ? '退货' : '领用'
    ui.toast(`已登记${label} ${formatNumber(outboundForm.qty)} ${outboundUnit.value}${outboundDrug.value?.name || ''}${result.allocations?.length && outboundType.value === 'USE' ? `，成本已计入 ${result.allocations.length} 个批次` : ''}`)
    outboundOpen.value = false
    await store.load()
  } catch (error) {
    applyErrors(error as ApiError, outboundErrors)
    ui.toast((error as ApiError).message || '出库失败，请重试', 'error')
  } finally {
    outboundSubmitting.value = false
  }
}

/* ---------------- 药品档案 ---------------- */
const archiveOpen = ref(false)
const archiveView = ref<'list' | 'edit' | 'create'>('list')
const archiveLoading = ref(false)
const archiveSubmitting = ref(false)
const archiveList = ref<DrugArchiveItem[]>([])
const archiveDetail = ref<DrugDetail | null>(null)
const catalogItems = ref<CatalogItem[]>([])
const catalogKeyword = ref('')
const archiveForm = reactive({
  category: 'VET_DRUG',
  catalogId: null as number | null,
  spec: '',
  packUnit: '',
  packSize: null as number | null,
  minStock: null as number | null,
  version: 0,
  unit: ''
})
const archiveErrors = reactive<Record<string, string>>({})
const archiveCategoryItems = [
  { title: '兽药', value: 'VET_DRUG' },
  { title: '疫苗', value: 'VACCINE' },
  { title: '消毒剂', value: 'DISINFECTANT' }
]
let catalogTimer = 0

async function openArchive(drugId = 0): Promise<void> {
  archiveOpen.value = true
  archiveView.value = 'list'
  archiveLoading.value = true
  archiveDetail.value = null
  try {
    await loadArchiveList()
    if (drugId) await openArchiveEdit(drugId)
  } catch (error) {
    ui.toast((error as ApiError).message || '药品档案加载失败', 'error')
  } finally {
    archiveLoading.value = false
  }
}

async function loadArchiveList(): Promise<void> {
  const result = await fetchDrugs()
  archiveList.value = result.list || []
}

async function openArchiveEdit(drugId: number): Promise<void> {
  archiveView.value = 'edit'
  archiveLoading.value = true
  try {
    const detail = await fetchDrug(drugId)
    archiveDetail.value = detail
    archiveForm.category = detail.category || 'VET_DRUG'
    archiveForm.catalogId = detail.catalog_id ?? null
    archiveForm.spec = detail.spec || ''
    archiveForm.packUnit = detail.pack_unit || ''
    archiveForm.packSize = detail.pack_size ?? null
    archiveForm.minStock = detail.min_stock ?? null
    archiveForm.version = detail.version || 0
    archiveForm.unit = detail.unit || ''
    Object.keys(archiveErrors).forEach((key) => delete archiveErrors[key])
  } catch (error) {
    ui.toast((error as ApiError).message || '药品档案加载失败', 'error')
  } finally {
    archiveLoading.value = false
  }
}

function openArchiveCreate(): void {
  archiveView.value = 'create'
  archiveDetail.value = null
  archiveForm.category = 'VET_DRUG'
  archiveForm.catalogId = null
  archiveForm.spec = ''
  archiveForm.packUnit = ''
  archiveForm.packSize = null
  archiveForm.minStock = null
  archiveForm.version = 0
  archiveForm.unit = ''
  catalogItems.value = []
  catalogKeyword.value = ''
  Object.keys(archiveErrors).forEach((key) => delete archiveErrors[key])
  void searchCatalog()
}

function onCatalogKeyword(value: string): void {
  catalogKeyword.value = value
  window.clearTimeout(catalogTimer)
  catalogTimer = window.setTimeout(() => void searchCatalog(), 300)
}

async function searchCatalog(): Promise<void> {
  try {
    const result = await fetchCatalog({ drug_category: archiveForm.category, keyword: catalogKeyword.value.trim() || undefined, size: 20 })
    catalogItems.value = result.items || []
  } catch {
    catalogItems.value = []
  }
}

function chooseCatalog(item: CatalogItem): void {
  archiveForm.catalogId = item.id
  archiveForm.spec = item.spec || ''
  archiveForm.packUnit = item.pack_unit || ''
  archiveForm.packSize = item.pack_size ?? null
  archiveForm.unit = item.measure_unit || ''
  delete archiveErrors.catalog_id
}

function submitArchive(): void {
  Object.keys(archiveErrors).forEach((key) => delete archiveErrors[key])
  if (archiveView.value === 'create' && !archiveForm.catalogId) archiveErrors.catalog_id = '请选择名称'
  if (numberValue(archiveForm.minStock) < 0) archiveErrors.minStock = '不能小于 0'
  if (archiveForm.packSize !== null && numberValue(archiveForm.packSize) <= 0) archiveErrors.packSize = '每包装折合数必须大于 0'
  if (Object.keys(archiveErrors).length) return
  void submitArchiveAsync()
}

async function submitArchiveAsync(): Promise<void> {
  if (archiveSubmitting.value) return
  archiveSubmitting.value = true
  try {
    const payload = {
      spec: archiveForm.spec.trim() || undefined,
      pack_unit: archiveForm.packUnit.trim() || undefined,
      pack_size: archiveForm.packSize ?? undefined,
      min_stock: archiveForm.minStock ?? undefined,
      min_stock_unit: archiveForm.unit || undefined,
      version: archiveView.value === 'edit' ? archiveForm.version : undefined
    }
    if (archiveView.value === 'create') {
      const detail = await createDrug({ ...payload, category: archiveForm.category, catalog_id: archiveForm.catalogId, unit: archiveForm.unit || undefined })
      ui.toast(`已新增药品档案：${detail.name}`)
    } else {
      const detail = await updateDrug(Number(archiveDetail.value?.drug_id || 0), payload)
      ui.toast(`已保存${detail.name}的档案`)
    }
    await loadArchiveList()
    archiveView.value = 'list'
    await store.load()
  } catch (error) {
    applyErrors(error as ApiError, archiveErrors)
    ui.toast((error as ApiError).message || '保存失败，请重试', 'error')
  } finally {
    archiveSubmitting.value = false
  }
}

/* ---------------- 临期天数 ---------------- */
const settingsOpen = ref(false)
const settingsSubmitting = ref(false)
const settingForm = reactive({ vet: 30, vaccine: 30, disinfectant: 30, version: 0 })
const settingErrors = reactive<Record<string, string>>({})

async function openSettings(): Promise<void> {
  settingsOpen.value = true
  await store.loadSetting()
  settingForm.vet = store.setting?.near_days_vet || 30
  settingForm.vaccine = store.setting?.near_days_vaccine || 30
  settingForm.disinfectant = store.setting?.near_days_disinfectant || 30
  settingForm.version = store.setting?.version || 0
}

async function saveSettings(): Promise<void> {
  Object.keys(settingErrors).forEach((key) => delete settingErrors[key])
  for (const [key, value] of Object.entries({ vet: settingForm.vet, vaccine: settingForm.vaccine, disinfectant: settingForm.disinfectant })) {
    if (!Number.isInteger(Number(value)) || Number(value) < 1 || Number(value) > 365) settingErrors[key] = '临期天数必须是 1–365'
  }
  if (Object.keys(settingErrors).length || settingsSubmitting.value) return
  settingsSubmitting.value = true
  try {
    await store.saveSetting({
      near_days_vet: Number(settingForm.vet),
      near_days_vaccine: Number(settingForm.vaccine),
      near_days_disinfectant: Number(settingForm.disinfectant),
      version: Number(store.setting?.version || 0)
    })
    ui.toast('已保存临期天数设置')
    settingsOpen.value = false
  } catch (error) {
    ui.toast((error as ApiError).message || '保存失败，请重试', 'error')
  } finally {
    settingsSubmitting.value = false
  }
}

/* ---------------- 流水 ---------------- */
const flowsOpen = ref(false)
const flows = ref<StockFlowItem[]>([])
const flowsTotal = ref(0)
const flowsPage = ref(1)
const flowsSize = ref(20)
const flowsGroup = ref<DrugFlowGroup>('ALL')
const flowsLoading = ref(false)
const documentOpen = ref(false)
const selectedFlow = ref<StockFlowItem | null>(null)
const returnToFlows = ref(false)
const flowGroups: Array<{ label: string; value: DrugFlowGroup }> = [
  { label: '全部', value: 'ALL' },
  { label: '入库', value: 'INBOUND' },
  { label: '领用', value: 'USE' },
  { label: '退货', value: 'RETURN' },
  { label: '报废', value: 'SCRAP' }
]

async function openFlows(): Promise<void> {
  flowsOpen.value = true
  flowsPage.value = 1
  flowsGroup.value = 'ALL'
  await loadFlows()
}

async function loadFlows(): Promise<void> {
  flowsLoading.value = true
  try {
    const data = await fetchDrugFlows({ flow_group: flowsGroup.value, page: flowsPage.value, size: flowsSize.value })
    flows.value = data.list || []
    flowsTotal.value = data.total || 0
  } catch {
    flows.value = []
    flowsTotal.value = 0
  } finally {
    flowsLoading.value = false
  }
}

function onFlowGroup(value: string | number): void {
  flowsGroup.value = value as DrugFlowGroup
  flowsPage.value = 1
  void loadFlows()
}

function setFlowsPage(page: number): void {
  flowsPage.value = page
  void loadFlows()
}

function setFlowsSize(size: number): void {
  flowsSize.value = size
  flowsPage.value = 1
  void loadFlows()
}

function openFlow(flow: StockFlowItem): void {
  selectedFlow.value = flow
  returnToFlows.value = true
  flowsOpen.value = false
  documentOpen.value = true
}

function onDocumentToggle(open: boolean): void {
  documentOpen.value = open
  if (!open && returnToFlows.value) {
    returnToFlows.value = false
    flowsOpen.value = true
  }
}

function onBatch(value: unknown): void {
  outboundForm.batchId = Number(value || 0)
  delete outboundErrors.batch_id
  void refreshAllocation()
}

function onPen(value: unknown): void {
  outboundForm.penId = Number(value || 0)
  delete outboundErrors.pen_id
  void refreshAllocation()
}

function onReceiver(value: unknown): void {
  outboundForm.receiverId = Number(value || 0)
  delete outboundErrors.receiver_id
}

function onArchiveCategory(value: string | number): void {
  archiveForm.category = String(value)
  void searchCatalog()
}

/* ---------------- 公共 ---------------- */
function applyErrors(error: ApiError, target: Record<string, string>): void {
  for (const field of error.fieldErrors || []) target[field.field] = field.message
}

function goLot(lotId: number, action = ''): void {
  void router.push({ name: 'WU-M03', params: { lotId: String(lotId) }, query: action ? { action } : undefined })
}

function statusTagText(item: DrugLotItem): { type: string; icon: string; text: string } {
  const tag = lotStatusTag(item.lot_status)
  return { ...tag, text: lotStatusText(item) }
}
</script>

<template>
  <zw-shell side="user" active="WU-M02" :crumbs="['物资库存', '兽药与疫苗']">
    <zw-page-head title="兽药与疫苗库存" :desc="desc">
      <template #actions>
        <template v-if="!isStaff">
          <v-menu location="bottom end">
            <template #activator="{ props: menuProps }">
              <v-btn v-bind="menuProps" variant="outlined" prepend-icon="more_horiz">更多</v-btn>
            </template>
            <v-list density="compact" class="stock-menu">
              <v-list-item prepend-icon="medical_information" @click="openArchive()"><v-list-item-title>药品档案</v-list-item-title></v-list-item>
              <v-list-item prepend-icon="schedule" @click="openSettings"><v-list-item-title>临期天数设置</v-list-item-title></v-list-item>
              <v-list-item prepend-icon="receipt_long" @click="openFlows"><v-list-item-title>全部出入库流水</v-list-item-title></v-list-item>
            </v-list>
          </v-menu>
          <v-btn variant="outlined" prepend-icon="output" @click="openOutbound()">领用或退货</v-btn>
          <v-btn color="primary" prepend-icon="add" @click="openInbound()">入库</v-btn>
        </template>
      </template>
    </zw-page-head>

    <zw-card v-if="isStaff">
      <zw-empty icon="lock" tone="warning" title="你没有物资库存的查看权限" desc="入库、盘点和报废由所有者或场长处理。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div v-if="store.status === 'loading'" class="stock-loading">
        <zw-card><v-skeleton-loader type="table-row@5" /></zw-card>
      </div>
      <zw-card v-else-if="store.status === 'error'">
        <zw-empty icon="cloud_off" tone="error" title="库存加载失败" :desc="store.error">
          <v-btn color="primary" variant="outlined" @click="store.load()">重新加载</v-btn>
        </zw-empty>
      </zw-card>

      <template v-else>
        <div class="zw-toolbar stock-toolbar">
          <div class="zw-toolbar-l">
            <zw-seg :model-value="store.category" :items="store.categoryItems" @update:model-value="onCategory" />
            <v-text-field v-model="keyword" placeholder="输入药品名称或批号" prepend-inner-icon="search" class="zw-w-240" hide-details clearable />
          </div>
          <div class="stock-toolbar-note">
            <zw-icon name="info" :size="16" />
            <span>按“物资 + 批号”分别结存 · {{ store.nearText }}</span>
          </div>
        </div>

        <zw-notice v-if="store.lowStockDrugs.length" type="warning" class="mb-4">
          <strong>{{ store.lowStockText }}</strong>
          <template #action><a class="stock-link" @click="openInbound(store.overview?.low_stock_banner?.preselect_drug_id || 0)">去入库</a></template>
        </zw-notice>

        <div v-if="store.status === 'empty'" class="mb-4">
          <zw-card>
            <zw-empty icon="medication" title="还没有兽药和疫苗库存" desc="登记入库后，这里按批号显示结存和有效期。">
              <v-btn color="primary" prepend-icon="add" @click="openInbound()">入库</v-btn>
            </zw-empty>
          </zw-card>
        </div>

        <zw-card v-if="store.attention.length" title="需要处理" :sub="`${store.attentionTotal} 个批号 · 已停用、已过期的批号只能报废`" flush class="mb-4">
          <v-table class="stock-table" hover>
            <thead>
              <tr>
                <th>物资</th>
                <th style="width: 112px">批号</th>
                <th style="width: 120px">有效期至</th>
                <th class="text-end" style="width: 170px">结存</th>
                <th style="width: 130px">状态</th>
                <th style="width: 300px">说明</th>
                <th class="text-end" style="width: 192px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in store.attention" :key="item.lot_id" class="stock-lot-row" @click="goLot(item.lot_id)">
                <td>
                  <div class="stock-material">
                    <span class="zw-ico-tile" :class="item.lot_status === 'NEAR_EXPIRY' ? 'zw-ico-tile--warning' : 'zw-ico-tile--error'">
                      <zw-icon :name="categoryIcon(item.category)" :size="18" />
                    </span>
                    <div>
                      <div class="zw-cell-main">{{ item.drug_name }}</div>
                      <div class="zw-cell-sub">{{ item.category_text || categoryText(item.category) }}</div>
                    </div>
                  </div>
                </td>
                <td class="num">{{ item.lot_no }}</td>
                <td class="num">{{ item.expire_date }}</td>
                <td class="text-end num">{{ packQtyText(item.stock) }}</td>
                <td><zw-tag :type="statusTagText(item).type" :icon="statusTagText(item).icon" size="small">{{ statusTagText(item).text }}</zw-tag></td>
                <td class="zw-muted">{{ item.status_note || (item.lot_status === 'RECALLED' ? '平台停用，只能报废' : item.lot_status === 'EXPIRED' ? '已过期，不能出库，只能报废' : daysText(item.days_to_expire)) }}</td>
                <td class="text-end">
                  <span class="zw-cell-actions">
                    <a v-if="item.lot_status === 'NEAR_EXPIRY'" @click.stop="openOutbound(item.drug_id, item.lot_id)">出库</a>
                    <a @click.stop="goLot(item.lot_id, 'scrap')">登记报废</a>
                    <a @click.stop="goLot(item.lot_id)">详情</a>
                  </span>
                </td>
              </tr>
            </tbody>
          </v-table>
          <div v-if="store.attentionTotal > store.attention.length" class="zw-table-foot stock-table-foot">
            <span />
            <zw-pager :total="store.attentionTotal" :page="store.attentionPage" :size="store.size" @update:page="store.setAttentionPage" @update:size="store.setSize" />
          </div>
        </zw-card>

        <zw-card title="正常" :sub="`${store.normalTotal} 个批号 · 同一物资有效期最早的批号优先出库`" flush>
          <div v-if="!store.normal.length && !store.attention.length">
            <zw-empty icon="search_off" title="没有找到匹配的批号" desc="换一个药品名称或批号试试。" />
          </div>
          <template v-else>
            <v-table class="stock-table" hover>
              <thead>
                <tr>
                  <th>物资</th>
                  <th style="width: 112px">批号</th>
                  <th style="width: 120px">有效期至</th>
                  <th class="text-end" style="width: 170px">结存</th>
                  <th style="width: 130px">状态</th>
                  <th style="width: 300px">说明</th>
                  <th class="text-end" style="width: 150px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in store.normal" :key="item.lot_id" class="stock-lot-row" @click="goLot(item.lot_id)">
                  <td>
                    <div class="stock-material">
                      <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon :name="categoryIcon(item.category)" :size="18" /></span>
                      <div>
                        <div class="zw-cell-main">{{ item.drug_name }}</div>
                        <div class="zw-cell-sub">{{ item.category_text || categoryText(item.category) }}</div>
                      </div>
                    </div>
                  </td>
                  <td class="num">{{ item.lot_no }}</td>
                  <td class="num">{{ item.expire_date }}</td>
                  <td class="text-end num">{{ packQtyText(item.stock) }}</td>
                  <td>
                    <zw-tag v-if="item.priority_out" size="small">优先出库</zw-tag>
                    <span v-else class="zw-faint">正常</span>
                  </td>
                  <td class="zw-muted">{{ item.priority_out ? '登记接种时默认选择这个批号' : item.status_note || '' }}</td>
                  <td class="text-end">
                    <span class="zw-cell-actions">
                      <a @click.stop="openOutbound(item.drug_id, item.lot_id)">出库</a>
                      <a @click.stop="goLot(item.lot_id)">详情</a>
                    </span>
                  </td>
                </tr>
              </tbody>
            </v-table>
            <div class="zw-table-foot stock-table-foot">
              <span class="zw-faint body-s">不显示单价和金额</span>
              <zw-pager :total="store.normalTotal" :page="store.normalPage" :size="store.size" @update:page="store.setNormalPage" @update:size="store.setSize" />
            </div>
          </template>
        </zw-card>
      </template>
    </template>

    <!-- 入库 -->
    <zw-drawer v-model="inboundOpen" title="兽药与疫苗入库" sub="同一药品、同一批号再次入库时并入原批号结存" width="560">
      <div v-if="inboundLoading" class="stock-drawer-loading"><v-skeleton-loader type="article" /></div>
      <template v-else>
        <zw-form-item label="药品" required :error="inboundErrors.drug_id" hint="只能从兽药目录中选择，禁用药不能入库">
          <v-select :model-value="inboundForm.drugId" :items="inboundOptions.map((item) => ({ title: item.name, value: item.drug_id }))" :error="!!inboundErrors.drug_id" @update:model-value="onInboundDrug" />
        </zw-form-item>
        <div v-if="inboundBannedHint" class="stock-banned">{{ inboundBannedHint }}</div>
        <zw-form-item label="生产批号" required :error="inboundErrors.lot_no" hint="只能输入字母、数字和短横线，最多 30 个字符" class="mt-4">
          <v-text-field v-model="inboundForm.lotNo" maxlength="30" :error="!!inboundErrors.lot_no" />
        </zw-form-item>
        <div class="zw-form-2 mt-3">
          <zw-form-item label="入库日期" required :error="inboundErrors.inbound_date">
            <v-text-field v-model="inboundForm.inboundDate" type="date" :error="!!inboundErrors.inbound_date" />
          </zw-form-item>
          <zw-form-item label="有效期至" required :error="inboundErrors.expire_date">
            <v-text-field v-model="inboundForm.expireDate" type="date" :error="!!inboundErrors.expire_date" />
          </zw-form-item>
        </div>
        <div class="zw-form-2 mt-3">
          <zw-form-item label="数量" required :error="inboundErrors.qty" :hint="`必须大于 0${inboundDrug?.pack_size ? `，按目录每${inboundDrug.pack_unit} ${inboundDrug.pack_size} ${inboundDrug.unit === 'DOSE' ? '头份' : inboundDrug.unit === 'G' ? '克' : '毫升'}换算` : ''}`">
            <v-text-field v-model.number="inboundForm.qty" type="number" min="0" :suffix="inboundUnit" :error="!!inboundErrors.qty" />
          </zw-form-item>
          <zw-form-item label="单价" required :error="inboundErrors.price" hint="必须大于 0">
            <v-text-field v-model.number="inboundForm.price" type="number" min="0" :suffix="`元 / ${inboundUnit}`" :error="!!inboundErrors.price" />
          </zw-form-item>
        </div>
        <div class="stock-amount mt-3">
          <span>入库金额</span>
          <strong class="num">{{ inboundForm.qty && inboundForm.price ? moneyText(inboundAmount) : '—' }}</strong>
        </div>
        <label class="stock-sync">
          <v-checkbox v-model="inboundForm.syncFinance" hide-details />
          <div>
            <div class="stock-sync-title">同步记账</div>
            <div class="stock-sync-sub">确认后在收支流水中生成一条支出：兽药疫苗 {{ inboundForm.qty && inboundForm.price ? formatMoney(inboundAmount) : '—' }} 元，来源标为“兽药疫苗入库单”。入库单作废时，这条支出同步作废。</div>
          </div>
        </label>
      </template>
      <template #foot>
        <span class="stock-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="inboundOpen = false">取消</v-btn>
        <v-btn color="primary" prepend-icon="check" :loading="inboundSubmitting" @click="submitInbound">确认入库</v-btn>
      </template>
    </zw-drawer>

    <!-- 出库 -->
    <zw-drawer v-model="outboundOpen" title="兽药与疫苗出库" sub="登记当天可以修改或作废，之后修改需要提交修改申请" width="600">
      <div v-if="outboundLoading" class="stock-drawer-loading"><v-skeleton-loader type="article" /></div>
      <template v-else>
        <zw-seg :model-value="outboundType" :items="[{ label: '领用', value: 'USE' }, { label: '退货', value: 'RETURN' }]" @update:model-value="onOutboundType" />
        <zw-notice :type="outboundType === 'RETURN' ? 'info' : 'info'" class="mt-3">
          {{ outboundType === 'RETURN' ? '退货不计入批次成本。实际收到退款后，请在退货单详情登记退款，系统会自动生成关联收入，请勿重复手工记账。' : '领用不会生成接种、用药或消毒记录，也不计算休药期。用于免疫、治疗和计划消毒的，请在免疫计划、用药与休药、消毒管理中登记，系统会自动扣减库存。' }}
        </zw-notice>
        <div class="zw-form-2 mt-4">
          <zw-form-item label="药品" required :error="outboundErrors.drug_id">
            <v-select :model-value="outboundForm.drugId" :items="outboundOptions.map((item) => ({ title: item.name, value: item.drug_id }))" :error="!!outboundErrors.drug_id" @update:model-value="onOutboundDrug" />
          </zw-form-item>
          <zw-form-item label="库存批号" required :error="outboundErrors.lot_id" :hint="lotHint">
            <v-select :model-value="outboundForm.lotId || null" :items="lotOptions.map((item) => ({ title: `${item.lot_no}${item.priority_out ? ' · 优先出库' : ''}${item.insufficient ? ' · 结存不足' : ''}`, value: item.lot_id, props: { disabled: item.insufficient } }))" :error="!!outboundErrors.lot_id" @update:model-value="onLot" />
          </zw-form-item>
        </div>
        <div class="zw-form-2 mt-3">
          <zw-form-item :label="outboundType === 'RETURN' ? '退货数量' : '领用数量'" required :error="outboundErrors.qty" :hint="selectedLot ? `最多 ${formatNumber(maxInputQty, 2)} ${outboundUnit}（${qtyText(selectedLot.stock)}），按该批号结存计算` : ''">
            <v-text-field v-model.number="outboundForm.qty" type="number" min="0" :suffix="outboundUnit" :error="!!outboundErrors.qty" @blur="refreshAllocation" />
          </zw-form-item>
          <zw-form-item v-if="outboundType === 'USE'" label="关联对象" required>
            <v-select :model-value="outboundForm.targetType" :items="[{ title: '批次', value: 'BATCH' }, { title: '栏舍', value: 'PEN' }]" @update:model-value="onTargetType" />
          </zw-form-item>
          <zw-form-item v-else label="退货原因" required :error="outboundErrors.return_reason">
            <v-select v-model="outboundForm.returnReason" :items="[{ title: '质量问题', value: 'QUALITY' }, { title: '包装破损', value: 'PACKAGE_DAMAGED' }, { title: '采购过多', value: 'OVER_PURCHASE' }, { title: '其他', value: 'OTHER' }]" :error="!!outboundErrors.return_reason" />
          </zw-form-item>
        </div>

        <template v-if="outboundType === 'USE'">
          <div class="zw-form-2 mt-3">
            <zw-form-item v-if="outboundForm.targetType === 'BATCH'" label="批次" required :error="outboundErrors.batch_id" :hint="targetBatch ? `存栏 ${formatNumber(targetBatch.current_count || 0)} · 成本全部计入该批次` : ''">
              <v-select :model-value="outboundForm.batchId || null" :items="batches.map((item) => ({ title: `${item.batch_no} · ${(item.pens || []).map((pen) => pen.pen_name).join('、')}`, value: item.batch_id }))" :error="!!outboundErrors.batch_id" @update:model-value="onBatch" />
            </zw-form-item>
            <zw-form-item v-else label="栏舍" required :error="outboundErrors.pen_id" :hint="targetPen ? `在栏 ${formatNumber(targetPen.stock_count || 0)} ${targetPen.unit_text || '头'}，成本按存栏量比例分摊` : ''">
              <v-select :model-value="outboundForm.penId || null" :items="pens.map((item) => ({ title: `${item.pen_name}（在栏 ${formatNumber(item.stock_count || 0)}）`, value: item.pen_id }))" :error="!!outboundErrors.pen_id" @update:model-value="onPen" />
            </zw-form-item>
            <zw-form-item label="领用日期" required>
              <v-text-field v-model="outboundForm.outDate" type="date" />
            </zw-form-item>
          </div>
          <div class="zw-form-2 mt-3">
            <zw-form-item label="领用人" required :error="outboundErrors.receiver_id" :hint="`登记人：${farm.role === 'staff' ? '本人' : '当前账号'}`">
              <v-select :model-value="outboundForm.receiverId || null" :items="members.map((item) => ({ title: `${item.name} · ${item.role_text || ''}`, value: item.user_id }))" :error="!!outboundErrors.receiver_id" @update:model-value="onReceiver" />
            </zw-form-item>
            <zw-form-item label="说明">
              <v-text-field v-model="outboundForm.remark" maxlength="100" />
            </zw-form-item>
          </div>
          <div class="stock-alloc">
            <div class="stock-alloc-head">
              <span>领用成本</span>
              <strong class="num">{{ allocation ? moneyText(allocation.total_amount) : outboundForm.qty && selectedLot ? moneyText(numberValue(outboundForm.qty) * numberValue(selectedLot.avg_price)) : '—' }}</strong>
            </div>
            <div v-if="allocation?.rows?.length" class="stock-alloc-rows">
              <div v-for="row in allocation.rows" :key="row.batch_id" class="stock-alloc-row">
                <span>{{ row.batch_no }} · {{ allocationText(row) }}</span>
                <span class="num">{{ moneyText(row.amount) }}</span>
              </div>
            </div>
            <div v-else class="stock-alloc-empty">选择批次或栏舍后显示成本分摊</div>
          </div>
        </template>

        <template v-else>
          <div class="zw-form-2 mt-3">
            <zw-form-item label="退回对象" required :error="outboundErrors.return_to">
              <v-combobox v-model="outboundForm.returnTo" :items="returnPartners" :error="!!outboundErrors.return_to" />
            </zw-form-item>
            <zw-form-item label="退货日期" required>
              <v-text-field v-model="outboundForm.outDate" type="date" />
            </zw-form-item>
          </div>
          <zw-form-item label="说明" class="mt-3">
            <v-text-field v-model="outboundForm.remark" maxlength="100" />
          </zw-form-item>
          <div class="stock-amount mt-3">
            <span>退货金额</span>
            <strong class="num">{{ outboundForm.qty && selectedLot ? moneyText(outboundAmount) : '—' }}</strong>
          </div>
        </template>
      </template>
      <template #foot>
        <span class="stock-foot-note">带 * 为必填{{ selectedLot?.avg_price ? ` · ${outboundPriceLabel}` : '' }}</span>
        <v-btn variant="outlined" @click="outboundOpen = false">取消</v-btn>
        <v-btn color="primary" :prepend-icon="outboundType === 'RETURN' ? 'assignment_return' : 'output'" :loading="outboundSubmitting" @click="submitOutbound">
          {{ outboundType === 'RETURN' ? '确认退货' : '确认领用' }}
        </v-btn>
      </template>
    </zw-drawer>

    <!-- 药品档案 -->
    <zw-drawer v-model="archiveOpen" :title="archiveView === 'create' ? '新增药品档案' : archiveView === 'edit' ? '药品档案' : '药品档案'" sub="修改后系统保留修改记录" width="600">
      <div v-if="archiveLoading" class="stock-drawer-loading"><v-skeleton-loader type="article@3" /></div>
      <template v-else-if="archiveView === 'list'">
        <div class="stock-archive-head">
          <span>共 {{ archiveList.length }} 种 · 点击查看或修改</span>
          <v-btn size="small" color="primary" variant="tonal" prepend-icon="add" @click="openArchiveCreate">新增药品档案</v-btn>
        </div>
        <div class="stock-archive-list">
          <button v-for="item in archiveList" :key="item.drug_id" type="button" class="stock-archive-row" @click="openArchiveEdit(item.drug_id)">
            <span class="zw-ico-tile" :class="item.low_stock ? 'zw-ico-tile--warning' : 'zw-ico-tile--primary'"><zw-icon :name="categoryIcon(item.category)" :size="18" /></span>
            <div class="stock-archive-main">
              <div class="zw-cell-main">{{ item.name }}</div>
              <div class="zw-cell-sub">{{ categoryText(item.category) }} · 可用结存 {{ packQtyText(item.usable) }}{{ item.min_stock ? ` · 下限 ${item.min_stock} ${item.usable?.unit_text || ''}` : '' }}</div>
            </div>
            <zw-tag v-if="item.banned" type="error" size="small">禁用药</zw-tag>
            <zw-tag v-else-if="item.low_stock" type="warning" size="small">库存不足</zw-tag>
          </button>
        </div>
      </template>
      <template v-else-if="archiveView === 'edit' && archiveDetail">
        <div class="stock-readonly-card">
          <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon :name="categoryIcon(archiveDetail.category)" :size="18" /></span>
          <div>
            <div class="zw-cell-main">{{ archiveDetail.name }}</div>
            <div class="zw-cell-sub">{{ categoryText(archiveDetail.category) }} · {{ archiveDetail.spec || '未填写规格' }} · 可用结存 {{ packQtyText(archiveDetail.usable) }}</div>
          </div>
        </div>
        <div class="zw-form-2">
          <zw-form-item label="规格" hint="最多 20 个字">
            <v-text-field v-model="archiveForm.spec" maxlength="20" />
          </zw-form-item>
          <zw-form-item label="库存单位">
            <div class="zw-readonly">{{ archiveDetail.unit || '—' }}</div>
          </zw-form-item>
        </div>
        <div class="zw-form-2 mt-3">
          <zw-form-item label="包装单位" hint="最多 4 个字">
            <v-text-field v-model="archiveForm.packUnit" maxlength="4" />
          </zw-form-item>
          <zw-form-item label="库存下限" :error="archiveErrors.minStock" hint="可用批号结存合计低于下限时提醒">
            <v-text-field v-model.number="archiveForm.minStock" type="number" min="0" :suffix="archiveDetail.usable?.unit_text || ''" :error="!!archiveErrors.minStock" />
          </zw-form-item>
        </div>
        <div class="stock-near-row">
          <zw-icon name="schedule" :size="20" />
          <div>
            <div class="stock-sync-title">临期天数：到期前 {{ store.nearText.replace('到期前 ', '').replace(' 天为临期', '') }} 天</div>
            <div class="stock-sync-sub">按类别设置，对全部{{ categoryText(archiveDetail.category) }}生效</div>
          </div>
          <a class="stock-link" @click="openSettings">修改</a>
        </div>
        <div class="stock-foot-meta">上次修改：{{ archiveDetail.updated_at ? datetimeText(archiveDetail.updated_at) : '—' }} · {{ archiveDetail.updated_by_name || '—' }}</div>
      </template>
      <template v-else-if="archiveView === 'create'">
        <zw-form-item label="类别" required>
          <zw-seg v-model="archiveForm.category" :items="archiveCategoryItems" @update:model-value="onArchiveCategory" />
        </zw-form-item>
        <zw-form-item label="名称" required :error="archiveErrors.catalog_id" hint="从兽药目录中选择，本场已建档的药品不能重复建档" class="mt-4">
          <v-text-field v-model="catalogKeyword" placeholder="搜索兽药目录" prepend-inner-icon="search" hide-details @update:model-value="onCatalogKeyword" />
          <div class="stock-catalog-list">
            <button v-for="item in catalogItems" :key="item.id" type="button" class="stock-catalog-row" :class="{ on: archiveForm.catalogId === item.id }" @click="chooseCatalog(item)">
              <div>
                <div class="zw-cell-main">{{ item.name }}</div>
                <div class="zw-cell-sub">{{ item.spec || '' }} · {{ item.measure_unit || '' }}</div>
              </div>
              <zw-icon v-if="archiveForm.catalogId === item.id" name="check" :size="18" />
            </button>
            <div v-if="!catalogItems.length" class="zw-faint stock-catalog-empty">兽药目录中没有找到相关药品。目录由平台维护，暂时不能自行添加。</div>
          </div>
        </zw-form-item>
        <div class="zw-form-2 mt-4">
          <zw-form-item label="规格">
            <v-text-field v-model="archiveForm.spec" maxlength="20" />
          </zw-form-item>
          <zw-form-item label="库存下限">
            <v-text-field v-model.number="archiveForm.minStock" type="number" min="0" />
          </zw-form-item>
        </div>
        <zw-notice type="info" class="mt-4">新增后可在入库时选择该药品。已有出入库记录的药品，类别和名称不能修改。</zw-notice>
      </template>
      <template #foot>
        <template v-if="archiveView === 'list'">
          <v-btn variant="outlined" @click="archiveOpen = false">关闭</v-btn>
        </template>
        <template v-else>
          <v-btn variant="text" @click="archiveView = 'list'">返回列表</v-btn>
          <v-btn variant="outlined" @click="archiveOpen = false">取消</v-btn>
          <v-btn color="primary" :loading="archiveSubmitting" @click="submitArchive">保存</v-btn>
        </template>
      </template>
    </zw-drawer>

    <!-- 临期天数 -->
    <zw-dialog v-model="settingsOpen" title="临期天数设置" :width="420">
      <div class="stock-dialog-desc">到期前多少天标为临期，并提醒所有者和场长一次。</div>
      <div class="zw-form-2">
        <zw-form-item label="兽药" :error="settingErrors.vet"><v-text-field v-model.number="settingForm.vet" type="number" suffix="天" :error="!!settingErrors.vet" /></zw-form-item>
        <zw-form-item label="疫苗" :error="settingErrors.vaccine"><v-text-field v-model.number="settingForm.vaccine" type="number" suffix="天" :error="!!settingErrors.vaccine" /></zw-form-item>
        <zw-form-item label="消毒剂" :error="settingErrors.disinfectant"><v-text-field v-model.number="settingForm.disinfectant" type="number" suffix="天" :error="!!settingErrors.disinfectant" /></zw-form-item>
      </div>
      <template #actions>
        <v-btn variant="outlined" @click="settingsOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="settingsSubmitting" @click="saveSettings">保存</v-btn>
      </template>
    </zw-dialog>

    <!-- 流水 -->
    <zw-drawer v-model="flowsOpen" title="出入库流水" :sub="`共 ${flowsTotal} 条`" width="760">
      <div class="stock-flow-toolbar"><zw-seg :model-value="flowsGroup" :items="flowGroups" size="small" @update:model-value="onFlowGroup" /></div>
      <div v-if="flowsLoading" class="stock-drawer-loading"><v-skeleton-loader type="table-row@6" /></div>
      <zw-empty v-else-if="!flows.length" icon="receipt_long" title="还没有出入库记录" desc="登记入库后会显示在这里。" />
      <template v-else>
        <v-table class="stock-table stock-table--compact" hover>
          <thead>
            <tr>
              <th style="width: 142px">时间</th>
              <th style="width: 140px">类型</th>
              <th>关联</th>
              <th style="width: 100px">经办人</th>
              <th class="text-end" style="width: 118px">数量</th>
              <th class="text-end" style="width: 64px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="flow in flows" :key="flow.flow_id" class="stock-flow-row" @click="openFlow(flow)">
              <td class="num">{{ datetimeText(flow.occur_at) }}</td>
              <td><div class="stock-flow-type"><span class="zw-ico-tile stock-ico-sm"><zw-icon :name="flowIcon(flow.flow_type)" :size="16" /></span>{{ flow.flow_type_text }}</div></td>
              <td class="zw-muted">{{ flow.related_desc || flow.pen_name || flow.batch_no || '—' }}</td>
              <td>{{ flow.operator_name || '—' }}</td>
              <td class="text-end num" :class="flow.direction === 'IN' ? 'stock-in-text' : ''">{{ flowQty(flow) }}</td>
              <td class="text-end"><a class="stock-link" @click.stop="openFlow(flow)">详情</a></td>
            </tr>
          </tbody>
        </v-table>
        <div class="zw-table-foot stock-table-foot">
          <span class="zw-faint body-s">按时间倒序</span>
          <zw-pager :total="flowsTotal" :page="flowsPage" :size="flowsSize" @update:page="setFlowsPage" @update:size="setFlowsSize" />
        </div>
      </template>
      <template #foot><v-btn variant="outlined" @click="flowsOpen = false">关闭</v-btn></template>
    </zw-drawer>

    <!-- 单据详情（入库单 / 领用单 / 退货单 / 报废单） -->
    <StockDocumentDrawer :model-value="documentOpen" :flow="selectedFlow" :staff="isStaff" @update:model-value="onDocumentToggle" />
  </zw-shell>
</template>

<style scoped>
.stock-loading { display: flex; flex-direction: column; }
.stock-menu { min-width: 210px; }
.stock-toolbar { align-items: flex-start; }
.stock-toolbar-note { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-2); padding-top: 6px; }
.stock-toolbar-note .zw-mso { color: var(--text-2); }
.stock-link { color: var(--primary); cursor: pointer; }
.stock-table :deep(td) { white-space: nowrap; }
.stock-table--compact :deep(td) { height: 44px; }
.stock-table-foot { padding: 12px 16px !important; border-top: 1px solid var(--line-1); }
.stock-material { display: flex; align-items: center; gap: 10px; }
.stock-flow-type { display: flex; align-items: center; gap: 8px; }
.stock-flow-row { cursor: pointer; }
.stock-lot-row { cursor: pointer; }
.stock-ico-sm { width: 28px; height: 28px; }
.stock-in-text { color: var(--success); }
.stock-banned { margin-top: 6px; font-size: 12px; color: var(--error); }
.stock-amount { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-radius: var(--radius-sm); background: var(--fill-1); }
.stock-amount span { font-size: 14px; color: var(--text-2); }
.stock-amount strong { font-size: 20px; color: var(--text-1); }
.stock-sync { display: flex; align-items: flex-start; gap: 10px; margin-top: 16px; padding: 12px 14px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); cursor: pointer; }
.stock-sync-title { font-size: 14px; font-weight: 600; color: var(--text-1); }
.stock-sync-sub { font-size: 12px; line-height: 18px; color: var(--text-2); margin-top: 2px; }
.stock-foot-note { margin-right: auto; font-size: 12px; color: var(--text-3); }
.stock-drawer-loading { padding: 8px 0; }
.stock-alloc { margin-top: 16px; padding: 12px 16px; border-radius: var(--radius-sm); background: var(--fill-1); }
.stock-alloc-head { display: flex; align-items: center; justify-content: space-between; }
.stock-alloc-head span { font-size: 14px; color: var(--text-2); }
.stock-alloc-head strong { font-size: 20px; color: var(--text-1); }
.stock-alloc-rows { border-top: 1px solid var(--line-1); margin-top: 8px; padding-top: 8px; }
.stock-alloc-row { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; color: var(--text-2); padding: 3px 0; }
.stock-alloc-empty { margin-top: 8px; font-size: 12px; color: var(--text-3); }
.stock-archive-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; font-size: 12px; color: var(--text-3); }
.stock-archive-list { display: flex; flex-direction: column; }
.stock-archive-row { display: flex; align-items: center; gap: 10px; width: 100%; padding: 10px 8px; border: 0; border-bottom: 1px solid var(--line-1); background: transparent; font: inherit; text-align: left; cursor: pointer; }
.stock-archive-row:hover { background: var(--fill-1); }
.stock-archive-main { flex: 1; min-width: 0; }
.stock-readonly-card { display: flex; align-items: center; gap: 10px; padding: 10px 12px; margin-bottom: 16px; border-radius: var(--radius-sm); background: var(--fill-1); }
.stock-near-row { display: flex; align-items: center; gap: 10px; margin-top: 16px; padding: 10px 12px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.stock-near-row > div { flex: 1; }
.stock-foot-meta { margin-top: 12px; font-size: 12px; color: var(--text-3); }
.stock-catalog-list { margin-top: 8px; max-height: 220px; overflow-y: auto; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.stock-catalog-row { display: flex; align-items: center; justify-content: space-between; width: 100%; padding: 8px 12px; border: 0; border-bottom: 1px solid var(--line-1); background: transparent; font: inherit; text-align: left; cursor: pointer; }
.stock-catalog-row:hover, .stock-catalog-row.on { background: var(--primary-subtle); }
.stock-catalog-empty { padding: 10px 12px; }
.stock-dialog-desc { font-size: 14px; line-height: 22px; color: var(--text-2); margin-bottom: 16px; }
.stock-flow-toolbar { display: flex; justify-content: flex-start; margin-bottom: 12px; }
</style>
