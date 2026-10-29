<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { routeQueryId, routeQueryText } from '@/utils/routeQuery'
import { ApiError, dayjs, formatDate, formatDateTime, formatNumber } from '@zmt/shared'
import {
  createBreed,
  createCategory,
  discardStandardDraft,
  getBreedDetail,
  getBreedStandard,
  getCategoryTree,
  getStandardImpact,
  getStandardVersions,
  publishStandard,
  rollbackStandard,
  saveFeedDraft,
  saveWeightDraft,
  updateBreed,
  updateCategory,
  type BreedDetail,
  type CategoryNode,
  type FeedStage,
  type FeedStdData,
  type FeedVersion,
  type StdImpact,
  type StdVersion,
  type StdWeightData,
  type StdWeightVersion
} from '@/api/base'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const route = useRoute()
const canHandle = computed(() => admin.hasPerm('WA-D01:HANDLE'))

const tree = ref<CategoryNode[]>([])
const treeKeyword = ref('')
const treeLoading = ref(true)
const treeError = ref('')
const forbidden = ref(false)
const selectedBreedId = ref<number | null>(null)
const detail = ref<BreedDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const activeTab = ref<'weight' | 'feed' | 'breeding'>('weight')
const weight = ref<StdWeightData | null>(null)
const feed = ref<FeedStdData | null>(null)
const standardLoading = ref(false)
const standardError = ref('')

const filteredTree = computed(() => {
  const keyword = treeKeyword.value.trim().toLowerCase()
  if (!keyword) return tree.value
  return tree.value
    .map((category) => ({
      ...category,
      breeds: category.breeds.filter((breed) => breed.name.toLowerCase().includes(keyword) || category.name.toLowerCase().includes(keyword))
    }))
    .filter((category) => category.breeds.length || category.name.toLowerCase().includes(keyword))
})
const selectedCategory = computed(() => tree.value.find((category) => category.breeds.some((breed) => breed.id === selectedBreedId.value)) || null)
const isPoultry = computed(() => detail.value?.category?.species === 'POULTRY' || selectedCategory.value?.species === 'POULTRY')
const currentWeight = computed<StdWeightVersion | null>(() => weight.value?.draft || weight.value?.online || weight.value?.scheduled || null)
const currentFeed = computed<FeedVersion | null>(() => feed.value?.draft || feed.value?.online || null)
const weightPoints = computed(() => currentWeight.value?.points || [])
const weightTolerance = computed(() => {
  const rate = Number(currentWeight.value?.tolerance_rate || 0)
  return rate > 0 && rate < 1 ? `±${(rate * 100).toFixed(0)}%` : '—'
})
const chartLabels = computed(() => weightPoints.value.map((point) => String(point.day_age)))
const chartSeries = computed(() => [
  { name: '标准体重', data: weightPoints.value.map((point) => Number(point.std_weight)), color: 'chart-1' },
  { name: '允许上限', data: weightPoints.value.map((point) => Number(point.upper || 0)), color: 'chart-ref', dashed: true },
  { name: '允许下限', data: weightPoints.value.map((point) => Number(point.lower || 0)), color: 'chart-ref', dashed: true }
])
const activeWeightVersion = computed(() => weight.value?.draft || weight.value?.online || null)
const activeFeedVersion = computed(() => feed.value?.draft || feed.value?.online || null)
const hasOnlineStandard = computed(() => versionType.value === 'weight' ? !!weight.value?.online : !!feed.value?.online)

async function loadTree(keepSelection = true, preferBreedId: number | null = null): Promise<void> {
  treeLoading.value = true
  treeError.value = ''
  try {
    tree.value = (await getCategoryTree()) || []
    if (!tree.value.length) {
      selectedBreedId.value = null
      detail.value = null
      return
    }
    if (!keepSelection || !tree.value.some((category) => category.breeds.some((breed) => breed.id === selectedBreedId.value))) {
      const breeds = tree.value.flatMap((category) => category.breeds)
      const first =
        (preferBreedId ? breeds.find((breed) => breed.id === preferBreedId) : undefined) ||
        breeds.find((breed) => breed.status === 'ENABLED') ||
        tree.value[0]?.breeds[0]
      if (first) await selectBreed(first.id)
    }
  } catch (e) {
    forbidden.value = e instanceof ApiError && e.isForbidden
    treeError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    treeLoading.value = false
  }
}

async function selectBreed(id: number): Promise<void> {
  selectedBreedId.value = id
  detail.value = null
  weight.value = null
  feed.value = null
  detailError.value = ''
  standardError.value = ''
  detailLoading.value = true
  standardLoading.value = true
  activeTab.value = 'weight'
  try {
    detail.value = await getBreedDetail(id)
    if (detail.value.category?.is_breeding === true) activeTab.value = 'breeding'
    await Promise.all([loadWeight(), loadFeed()])
  } catch (e) {
    detailError.value = e instanceof Error ? e.message : '品种详情加载失败'
  } finally {
    detailLoading.value = false
    standardLoading.value = false
  }
}

async function loadWeight(): Promise<void> {
  if (!selectedBreedId.value) return
  try {
    weight.value = (await getBreedStandard(selectedBreedId.value, 'weight')) as StdWeightData
  } catch (e) {
    if (e instanceof ApiError && e.code === 40401) weight.value = { breed_id: selectedBreedId.value }
    else standardError.value = e instanceof Error ? e.message : '标准体重加载失败'
  }
}

async function loadFeed(): Promise<void> {
  if (!selectedBreedId.value) return
  try {
    feed.value = (await getBreedStandard(selectedBreedId.value, 'feed')) as FeedStdData
  } catch (e) {
    if (e instanceof ApiError && e.code === 40401) feed.value = { breed_id: selectedBreedId.value }
    else standardError.value = e instanceof Error ? e.message : '饲喂标准加载失败'
  }
}

function switchTab(value: 'weight' | 'feed' | 'breeding'): void {
  activeTab.value = value
}

function breedSummary(breed: { gestation_days?: number | null; incubation_days?: number | null; has_std_weight?: boolean; has_feed_std?: boolean; has_immune_template?: boolean }): string {
  const parts: string[] = []
  if (breed.gestation_days) parts.push(`妊娠期 ${breed.gestation_days} 天`)
  if (breed.incubation_days) parts.push(`孵化期 ${breed.incubation_days} 天`)
  if (breed.has_std_weight) parts.push('标准体重')
  if (breed.has_feed_std) parts.push('饲喂标准')
  if (breed.has_immune_template) parts.push('免疫模板')
  return parts.length ? parts.join(' · ') : '还没有配置标准'
}

/* ---------------- 标准体重编辑 ---------------- */
const weightDrawer = ref(false)
const weightSaving = ref(false)
const weightPublishing = ref(false)
const weightFormError = ref('')
const weightImpact = ref<StdImpact | null>(null)
const weightForm = reactive({
  tolerance: 10,
  weighTimes: 2,
  effective_mode: 'IMMEDIATE',
  effective_date: '',
  update_note: '',
  /** 草稿版本号；草稿第一次保存后就是 0，所以“还没有草稿”用 null 表示 */
  version: null as number | null,
  points: [] as Array<{ day_age: number; std_weight: string; online_weight?: number | null; changed?: boolean }>
})

async function openWeightEditor(): Promise<void> {
  if (!selectedBreedId.value || !canHandle.value) return
  const source = weight.value?.draft || weight.value?.online
  const tolerance = Number(source?.tolerance_rate || 0.1)
  weightForm.tolerance = Number((tolerance * 100).toFixed(2))
  weightForm.weighTimes = source?.suggested_weigh_times || 2
  weightForm.effective_mode = source?.effective_mode || 'IMMEDIATE'
  weightForm.effective_date = source?.effective_date || ''
  weightForm.update_note = weight.value?.draft?.update_note || ''
  weightForm.version = weight.value?.draft?.version ?? null
  weightForm.points = (source?.points || []).map((point) => ({
    day_age: point.day_age,
    std_weight: String(point.std_weight),
    online_weight: point.online_weight ?? (weight.value?.online?.points?.find((p) => p.day_age === point.day_age)?.std_weight ?? point.std_weight),
    changed: point.changed
  }))
  if (!weightForm.points.length) weightForm.points = [{ day_age: 30, std_weight: '' }, { day_age: 60, std_weight: '' }]
  weightFormError.value = ''
  weightImpact.value = null
  weightDrawer.value = true
}

async function loadWeightImpact(): Promise<void> {
  if (!selectedBreedId.value) return
  try {
    weightImpact.value = await getStandardImpact(selectedBreedId.value, 'weight')
  } catch {
    weightImpact.value = null
  }
}

function addWeightPoint(): void {
  const last = weightForm.points[weightForm.points.length - 1]
  weightForm.points.push({ day_age: (last?.day_age || 0) + 10, std_weight: '', online_weight: null })
}

function removeWeightPoint(index: number): void {
  weightForm.points.splice(index, 1)
}

function validateWeight(): boolean {
  weightFormError.value = ''
  if (!weightForm.tolerance || weightForm.tolerance < 1 || weightForm.tolerance > 50) {
    weightFormError.value = '允许偏差必须在 1%–50% 之间'
    return false
  }
  if (!weightForm.weighTimes || weightForm.weighTimes < 1 || weightForm.weighTimes > 8) {
    weightFormError.value = '建议每月称重次数必须在 1–8 之间'
    return false
  }
  if (weightForm.effective_mode === 'ON_DATE' && !weightForm.effective_date) {
    weightFormError.value = '请选择生效日期'
    return false
  }
  if (weightForm.points.length < 2) {
    weightFormError.value = '至少填写 2 个日龄点'
    return false
  }
  const seen = new Set<number>()
  let previous = 0
  for (const point of [...weightForm.points].sort((a, b) => a.day_age - b.day_age)) {
    if (!point.day_age || point.day_age < 1 || point.day_age > 999) {
      weightFormError.value = '日龄必须在 1–999 之间'
      return false
    }
    if (seen.has(point.day_age)) {
      weightFormError.value = `日龄 ${point.day_age} 重复`
      return false
    }
    seen.add(point.day_age)
    if (!point.std_weight || Number(point.std_weight) <= 0) {
      weightFormError.value = `请填写 ${point.day_age} 日龄的标准体重`
      return false
    }
    if (Number(point.std_weight) <= previous) {
      weightFormError.value = '标准体重必须随日龄递增'
      return false
    }
    previous = Number(point.std_weight)
  }
  return true
}

async function saveWeight(closeAfter = false): Promise<boolean> {
  if (!selectedBreedId.value || !validateWeight()) return false
  weightSaving.value = true
  try {
    const points = [...weightForm.points].sort((a, b) => a.day_age - b.day_age).map((point) => ({ day_age: point.day_age, std_weight: point.std_weight }))
    const data = await saveWeightDraft(selectedBreedId.value, {
      tolerance_rate: weightForm.tolerance / 100,
      suggested_weigh_times: weightForm.weighTimes,
      effective_mode: weightForm.effective_mode,
      effective_date: weightForm.effective_mode === 'ON_DATE' ? weightForm.effective_date || null : null,
      points,
      version: weightForm.version ?? undefined,
      update_note: weightForm.update_note.trim() || undefined
    })
    weight.value = data
    weightForm.version = data.draft?.version ?? weightForm.version
    ui.toast('标准体重草稿已保存，发布后生效')
    if (closeAfter) weightDrawer.value = false
    void loadWeightImpact()
    return true
  } catch (e) {
    weightFormError.value = e instanceof Error ? e.message : '保存失败'
    return false
  } finally {
    weightSaving.value = false
  }
}

async function publishWeight(): Promise<void> {
  if (!selectedBreedId.value) return
  if (!weightForm.update_note.trim()) {
    weightFormError.value = '请填写更新说明'
    return
  }
  const saved = await saveWeight(false)
  if (!saved) return
  if (weightForm.version === null) {
    weightFormError.value = '草稿还没有保存成功，请重试'
    return
  }
  weightPublishing.value = true
  weightFormError.value = ''
  try {
    await publishStandard(selectedBreedId.value, 'weight', weightForm.update_note.trim(), weightForm.version)
    weightDrawer.value = false
    ui.toast('已发布新版本，用户端已按新标准重新计算')
    await Promise.all([loadWeight(), loadTree(true)])
  } catch (e) {
    weightFormError.value = e instanceof Error ? e.message : '发布失败'
  } finally {
    weightPublishing.value = false
  }
}

/* ---------------- 饲喂标准编辑 ---------------- */
const feedDrawer = ref(false)
const feedSaving = ref(false)
const feedPublishing = ref(false)
const feedFormError = ref('')
const feedImpact = ref<StdImpact | null>(null)
const feedForm = reactive({
  deviation: 10,
  update_note: '',
  /** 同标准体重：草稿版本号从 0 开始，没有草稿用 null */
  version: null as number | null,
  stages: [] as Array<{
    stage_name: string
    stage_short_name: string
    day_age_start: number
    day_age_end: number | null
    feed_type_code: string
    suggested_feed_name: string
    daily_feed: string
    meals_per_day: number
    meal_times: string
  }>
})

const feedTypeOptions = [
  { title: '全价料', value: 'COMPLETE' },
  { title: '浓缩料', value: 'CONCENTRATE' },
  { title: '预混料', value: 'PREMIX' },
  { title: '能量原料', value: 'ENERGY' },
  { title: '蛋白原料', value: 'PROTEIN' },
  { title: '青粗饲料', value: 'ROUGHAGE' },
  { title: '添加剂', value: 'ADDITIVE' }
]

async function openFeedEditor(): Promise<void> {
  if (!selectedBreedId.value || !canHandle.value) return
  const source = feed.value?.draft || feed.value?.online
  const deviation = Number(source?.deviation_rate || 0.1)
  feedForm.deviation = Number((deviation * 100).toFixed(2))
  feedForm.update_note = feed.value?.draft?.update_note || ''
  feedForm.version = feed.value?.draft?.version ?? null
  feedForm.stages = (source?.stages || []).map((stage) => ({
    stage_name: stage.stage_name,
    stage_short_name: stage.stage_short_name,
    day_age_start: stage.day_age_start,
    day_age_end: stage.day_age_end || null,
    feed_type_code: stage.feed_type_code,
    suggested_feed_name: stage.suggested_feed_name,
    daily_feed: String(stage.daily_feed),
    meals_per_day: stage.meals_per_day,
    meal_times: (stage.meal_times || []).join(', ')
  }))
  if (!feedForm.stages.length) addFeedStage()
  feedFormError.value = ''
  feedImpact.value = null
  feedDrawer.value = true
}

async function loadFeedImpact(): Promise<void> {
  if (!selectedBreedId.value) return
  try {
    feedImpact.value = await getStandardImpact(selectedBreedId.value, 'feed')
  } catch {
    feedImpact.value = null
  }
}

function addFeedStage(): void {
  const last = feedForm.stages[feedForm.stages.length - 1]
  const start = last?.day_age_end ? Number(last.day_age_end) + 1 : feedForm.stages.length ? 101 : 1
  feedForm.stages.push({
    stage_name: '',
    stage_short_name: '',
    day_age_start: start,
    day_age_end: null,
    feed_type_code: 'COMPLETE',
    suggested_feed_name: '',
    daily_feed: '',
    meals_per_day: 3,
    meal_times: '07:00, 11:30, 17:00'
  })
}

function removeFeedStage(index: number): void {
  feedForm.stages.splice(index, 1)
}

function validateFeed(): boolean {
  feedFormError.value = ''
  if (!feedForm.deviation || feedForm.deviation < 1 || feedForm.deviation > 50) {
    feedFormError.value = '投喂异常判定默认值必须在 1%–50% 之间'
    return false
  }
  if (!feedForm.stages.length) {
    feedFormError.value = '请至少填写 1 个饲喂阶段'
    return false
  }
  for (const [index, stage] of feedForm.stages.entries()) {
    if (!stage.stage_name.trim() || !stage.stage_short_name.trim()) {
      feedFormError.value = `第 ${index + 1} 个阶段请填写阶段名称和简称`
      return false
    }
    if (!stage.day_age_start || stage.day_age_start < 1) {
      feedFormError.value = '首段起始日龄必须大于 0'
      return false
    }
    if (!stage.feed_type_code || !stage.suggested_feed_name.trim()) {
      feedFormError.value = `第 ${index + 1} 个阶段请选择饲料类型并填写建议饲料`
      return false
    }
    if (!stage.daily_feed || Number(stage.daily_feed) <= 0) {
      feedFormError.value = `第 ${index + 1} 个阶段请填写每日投喂量`
      return false
    }
    if (!stage.meals_per_day || stage.meals_per_day < 1 || stage.meals_per_day > 4) {
      feedFormError.value = '每日餐次必须在 1–4 之间'
      return false
    }
    const times = stage.meal_times.split(/[,，\s]+/).filter(Boolean)
    if (!times.length) {
      feedFormError.value = '请填写投喂时间'
      return false
    }
  }
  return true
}

function buildFeedStages() {
  return feedForm.stages.map((stage) => ({
    stage_name: stage.stage_name.trim(),
    stage_short_name: stage.stage_short_name.trim(),
    day_age_start: stage.day_age_start,
    day_age_end: stage.day_age_end || null,
    feed_type_code: stage.feed_type_code,
    suggested_feed_name: stage.suggested_feed_name.trim(),
    daily_feed: stage.daily_feed,
    meals_per_day: stage.meals_per_day,
    meal_times: stage.meal_times.split(/[,，\s]+/).filter(Boolean)
  }))
}

async function saveFeed(closeAfter = false): Promise<boolean> {
  if (!selectedBreedId.value || !validateFeed()) return false
  feedSaving.value = true
  try {
    const data = await saveFeedDraft(selectedBreedId.value, {
      deviation_rate: feedForm.deviation / 100,
      stages: buildFeedStages(),
      version: feedForm.version ?? undefined,
      update_note: feedForm.update_note.trim() || undefined
    })
    feed.value = data
    feedForm.version = data.draft?.version ?? feedForm.version
    ui.toast('饲喂标准草稿已保存，发布后生效')
    if (closeAfter) feedDrawer.value = false
    void loadFeedImpact()
    return true
  } catch (e) {
    feedFormError.value = e instanceof Error ? e.message : '保存失败'
    return false
  } finally {
    feedSaving.value = false
  }
}

async function publishFeed(): Promise<void> {
  if (!selectedBreedId.value) return
  if (!feedForm.update_note.trim()) {
    feedFormError.value = '请填写更新说明'
    return
  }
  const saved = await saveFeed(false)
  if (!saved) return
  if (feedForm.version === null) {
    feedFormError.value = '草稿还没有保存成功，请重试'
    return
  }
  feedPublishing.value = true
  feedFormError.value = ''
  try {
    await publishStandard(selectedBreedId.value, 'feed', feedForm.update_note.trim(), feedForm.version)
    feedDrawer.value = false
    ui.toast('已发布新版本，用户端已按新标准重新计算')
    await Promise.all([loadFeed(), loadTree(true)])
  } catch (e) {
    feedFormError.value = e instanceof Error ? e.message : '发布失败'
  } finally {
    feedPublishing.value = false
  }
}

/* ---------------- 版本记录 ---------------- */
const versionOpen = ref(false)
const versionType = ref<'weight' | 'feed'>('weight')
const versions = ref<StdVersion[]>([])
const versionSelected = ref<StdVersion | null>(null)
const versionsLoading = ref(false)
const versionError = ref('')
const rollbackReason = ref('')
const rollbackSaving = ref(false)

async function openVersions(type: 'weight' | 'feed'): Promise<void> {
  if (!selectedBreedId.value) return
  versionType.value = type
  versionOpen.value = true
  versionsLoading.value = true
  versions.value = []
  versionSelected.value = null
  rollbackReason.value = ''
  versionError.value = ''
  try {
    versions.value = await getStandardVersions(selectedBreedId.value, type)
    versionSelected.value = versions.value[0] || null
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : '版本加载失败'
  } finally {
    versionsLoading.value = false
  }
}

const versionCompare = computed(() => {
  if (!versionSelected.value) return []
  if (versionType.value === 'weight') {
    return (versionSelected.value.points || []).map((point) => ({
      label: `${point.day_age} 日龄`,
      before: point.std_weight !== null && point.std_weight !== undefined ? `${point.std_weight} 千克` : '—',
      after: point.std_weight !== null && point.std_weight !== undefined ? `${point.std_weight} 千克` : '—'
    }))
  }
  return (versionSelected.value.stages || []).map((stage) => ({
    label: stage.stage_name,
    before: `${stage.day_age_start}–${stage.day_age_end || '出栏'} 日龄`,
    after: `${stage.daily_feed} 千克 · ${stage.meals_per_day} 餐`
  }))
})

async function submitRollback(): Promise<void> {
  if (!selectedBreedId.value || !versionSelected.value) return
  if (!rollbackReason.value.trim()) {
    versionError.value = '请填写回退原因'
    return
  }
  const online = versionType.value === 'weight' ? weight.value?.online : feed.value?.online
  if (!online?.version_no) {
    versionError.value = '当前没有线上版本，不能回退'
    return
  }
  if (versionSelected.value.version_no === online.version_no && !window.confirm('选中的就是线上版本，仍要生成新版本吗？')) return
  rollbackSaving.value = true
  versionError.value = ''
  try {
    await rollbackStandard(selectedBreedId.value, versionType.value, {
      target_version_no: versionSelected.value.version_no,
      reason: rollbackReason.value.trim(),
      online_version_no: online.version_no
    })
    versionOpen.value = false
    ui.toast(`已回退到第 ${versionSelected.value.version_no} 版的内容，生成新版本`)
    await Promise.all([loadWeight(), loadFeed(), loadTree(true)])
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : '回退失败'
  } finally {
    rollbackSaving.value = false
  }
}

const discardOpen = ref(false)
const discardType = ref<'weight' | 'feed'>('weight')
const discardSaving = ref(false)

function openDiscard(type: 'weight' | 'feed'): void {
  discardType.value = type
  discardOpen.value = true
}

async function confirmDiscard(): Promise<void> {
  if (!selectedBreedId.value) return
  discardSaving.value = true
  try {
    const version = discardType.value === 'weight' ? weight.value?.draft?.version : feed.value?.draft?.version
    await discardStandardDraft(selectedBreedId.value, discardType.value, version)
    discardOpen.value = false
    ui.toast('已放弃未发布的修改')
    await Promise.all([loadWeight(), loadFeed()])
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '放弃失败', 'error')
  } finally {
    discardSaving.value = false
  }
}

/* ---------------- 品种 / 品类 ---------------- */
const breedDrawer = ref(false)
const breedSaving = ref(false)
const breedError = ref('')
const breedFieldErrors = reactive<Record<string, string>>({})
const breedForm = reactive({
  category_id: null as number | null,
  name: '',
  description: '',
  gestation_days: null as number | null,
  incubation_days: null as number | null,
  pregnancy_check_days: null as number | null,
  offspring_breed_id: null as number | null,
  status: 'ENABLED',
  disable_reason: '',
  change_reason: '',
  version: 0,
  id: null as number | null
})
const breedCategory = computed(() => tree.value.find((category) => category.id === breedForm.category_id) || null)
const breedIsPoultry = computed(() => breedCategory.value?.species === 'POULTRY')
const breedIsBreeding = computed(() => !!breedCategory.value?.is_breeding)
/** 与服务端一致：只有改妊娠期 / 孵化期 / 待妊检天数（或停用）时修改原因必填 */
const breedReasonRequired = computed(() => {
  if (!breedForm.id || !detail.value) return false
  const gestation = breedIsPoultry.value ? null : breedForm.gestation_days || null
  const incubation = breedIsPoultry.value ? breedForm.incubation_days || null : null
  const check = breedIsPoultry.value ? null : breedForm.pregnancy_check_days || null
  return gestation !== (detail.value.gestation_days || null)
    || incubation !== (detail.value.incubation_days || null)
    || check !== (detail.value.pregnancy_check_days || null)
})
const offspringOptions = computed(() => {
  const species = breedCategory.value?.species
  return tree.value
    .filter((category) => category.species === species)
    .flatMap((category) => category.breeds.filter((breed) => breed.status === 'ENABLED' && breed.id !== breedForm.id).map((breed) => ({ title: `${breed.name}（${category.name}）`, value: breed.id })))
})

function openBreedCreate(): void {
  breedForm.id = null
  breedForm.category_id = selectedCategory.value?.id || tree.value.find((category) => category.status === 'ENABLED')?.id || null
  breedForm.name = ''
  breedForm.description = ''
  breedForm.gestation_days = null
  breedForm.incubation_days = breedIsPoultry.value ? 21 : null
  breedForm.pregnancy_check_days = null
  breedForm.offspring_breed_id = null
  breedForm.status = 'ENABLED'
  breedForm.disable_reason = ''
  breedForm.change_reason = ''
  breedForm.version = 0
  breedError.value = ''
  Object.keys(breedFieldErrors).forEach((key) => delete breedFieldErrors[key])
  breedDrawer.value = true
}

async function openBreedEdit(): Promise<void> {
  if (!detail.value) return
  breedForm.id = detail.value.id
  breedForm.category_id = detail.value.category?.id || null
  breedForm.name = detail.value.name
  breedForm.description = detail.value.description || ''
  breedForm.gestation_days = detail.value.gestation_days || null
  breedForm.incubation_days = detail.value.incubation_days || null
  breedForm.pregnancy_check_days = detail.value.pregnancy_check_days || null
  breedForm.offspring_breed_id = detail.value.offspring_breed?.id || null
  breedForm.status = detail.value.status
  breedForm.disable_reason = detail.value.disable_reason || ''
  breedForm.change_reason = ''
  breedForm.version = detail.value.version || 0
  breedError.value = ''
  Object.keys(breedFieldErrors).forEach((key) => delete breedFieldErrors[key])
  breedDrawer.value = true
}

function validateBreed(): boolean {
  Object.keys(breedFieldErrors).forEach((key) => delete breedFieldErrors[key])
  breedError.value = ''
  if (!breedForm.category_id) breedFieldErrors.category_id = '请选择所属品类'
  if (!breedForm.name.trim()) breedFieldErrors.name = '请填写品种名称'
  else if (breedForm.name.trim().length > 20) breedFieldErrors.name = '品种名称最多 20 个字'
  if (breedIsPoultry.value && !breedForm.incubation_days) breedFieldErrors.incubation_days = '请填写孵化期'
  if (!breedIsPoultry.value && breedForm.gestation_days && breedForm.pregnancy_check_days && breedForm.pregnancy_check_days >= breedForm.gestation_days) {
    breedFieldErrors.pregnancy_check_days = '待妊检天数必须小于妊娠期'
  }
  if (breedForm.status === 'DISABLED' && !breedForm.change_reason.trim() && !breedForm.disable_reason.trim()) {
    breedError.value = '停用时请填写修改原因'
    return false
  }
  if (breedReasonRequired.value && !breedForm.change_reason.trim() && breedForm.status !== 'DISABLED') {
    breedError.value = '修改妊娠期、孵化期或待妊检天数时请填写修改原因，记入操作日志'
    return false
  }
  return !Object.keys(breedFieldErrors).length
}

async function submitBreed(): Promise<void> {
  if (!validateBreed()) return
  breedSaving.value = true
  breedError.value = ''
  try {
    const payload = {
      category_id: breedForm.category_id!,
      name: breedForm.name.trim(),
      description: breedForm.description.trim() || undefined,
      gestation_days: breedIsPoultry.value ? null : breedForm.gestation_days,
      incubation_days: breedIsPoultry.value ? breedForm.incubation_days : null,
      pregnancy_check_days: breedIsPoultry.value ? null : breedForm.pregnancy_check_days,
      offspring_breed_id: breedForm.offspring_breed_id,
      status: breedForm.status,
      disable_reason: breedForm.disable_reason.trim() || undefined,
      change_reason: breedForm.change_reason.trim() || undefined,
      version: breedForm.id ? breedForm.version : undefined
    }
    if (breedForm.id) await updateBreed(breedForm.id, payload)
    else await createBreed(payload)
    breedDrawer.value = false
    ui.toast(breedForm.id ? '品种信息已保存' : `已保存${breedForm.name.trim()}`)
    await loadTree(false)
    if (breedForm.id) await selectBreed(breedForm.id)
    else {
      const newBreed = tree.value.flatMap((c) => c.breeds).find((b) => b.name === breedForm.name.trim())
      if (newBreed) await selectBreed(newBreed.id)
    }
  } catch (e) {
    if (e instanceof ApiError) {
      for (const item of e.fieldErrors) breedFieldErrors[item.field] = item.message
      breedError.value = e.message
    } else {
      breedError.value = e instanceof Error ? e.message : '保存失败'
    }
  } finally {
    breedSaving.value = false
  }
}

const categoryDrawer = ref(false)
const categorySaving = ref(false)
const categoryError = ref('')
const categoryTarget = ref<CategoryNode | null>(null)
const categoryForm = reactive({
  name: '',
  species: 'PIG',
  is_breeding: false,
  sort_no: 1,
  status: 'ENABLED',
  disable_reason: '',
  version: 0
})
const speciesOptions = [
  { title: '猪', value: 'PIG' },
  { title: '禽', value: 'POULTRY' },
  { title: '牛', value: 'CATTLE' },
  { title: '羊', value: 'SHEEP' }
]

function openCategoryCreate(): void {
  categoryTarget.value = null
  categoryForm.name = ''
  categoryForm.species = 'PIG'
  categoryForm.is_breeding = false
  categoryForm.sort_no = tree.value.length + 1
  categoryForm.status = 'ENABLED'
  categoryForm.disable_reason = ''
  categoryForm.version = 0
  categoryError.value = ''
  categoryDrawer.value = true
}

async function openCategoryEdit(category: CategoryNode): Promise<void> {
  categoryTarget.value = category
  categoryForm.name = category.name
  categoryForm.species = category.species
  categoryForm.is_breeding = !!category.is_breeding
  categoryForm.sort_no = category.sort_no || 1
  categoryForm.status = category.status
  categoryForm.disable_reason = ''
  categoryForm.version = (category as CategoryNode & { version?: number }).version || 0
  categoryError.value = ''
  categoryDrawer.value = true
  const firstBreed = category.breeds[0]
  if (firstBreed) {
    try {
      const breed = await getBreedDetail(firstBreed.id)
      categoryForm.version = breed.category?.version ?? categoryForm.version
    } catch {
      /* 取不到版本时由服务端返回冲突 */
    }
  }
}

async function submitCategory(): Promise<void> {
  categoryError.value = ''
  if (!categoryForm.name.trim()) {
    categoryError.value = '请填写品类名称'
    return
  }
  if (categoryForm.status === 'DISABLED' && !categoryForm.disable_reason.trim()) {
    categoryError.value = '请填写停用原因'
    return
  }
  categorySaving.value = true
  try {
    const payload = {
      name: categoryForm.name.trim(),
      species: categoryForm.species,
      is_breeding: categoryForm.is_breeding,
      sort_no: categoryForm.sort_no,
      status: categoryForm.status,
      disable_reason: categoryForm.disable_reason.trim() || undefined,
      version: categoryTarget.value ? categoryForm.version : undefined
    }
    if (categoryTarget.value) await updateCategory(categoryTarget.value.id, payload)
    else await createCategory(payload)
    categoryDrawer.value = false
    ui.toast(`已保存“${categoryForm.name.trim()}”`)
    await loadTree(false)
  } catch (e) {
    categoryError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    categorySaving.value = false
  }
}

function formatPoint(value?: number | null): string {
  return value !== null && value !== undefined ? Number(value).toFixed(1) : '—'
}

function dailyGain(index: number): string {
  const points = weightPoints.value
  if (index <= 0) return '—'
  const prev = points[index - 1]
  const current = points[index]
  if (!prev || !current) return '—'
  const days = current.day_age - prev.day_age
  if (!days) return '—'
  return ((Number(current.std_weight) - Number(prev.std_weight)) / days).toFixed(2)
}

/** 从操作日志带 ?breed_id= / ?category_id=（可带 tab=weight|feed）进入时定位到对应品种或品类 */
onMounted(async () => {
  const breedId = routeQueryId(route, 'breed_id')
  const categoryId = routeQueryId(route, 'category_id')
  if (!breedId && !categoryId) {
    void loadTree(false)
    return
  }
  if (!breedId && categoryId) {
    await loadTree(false)
    const category = tree.value.find((item) => item.id === categoryId)
    if (category) {
      treeKeyword.value = category.name
      const breed = category.breeds.find((item) => item.status === 'ENABLED') || category.breeds[0]
      if (breed && breed.id !== selectedBreedId.value) await selectBreed(breed.id)
    }
  } else {
    await loadTree(false, breedId)
  }
  const tab = routeQueryText(route, 'tab')
  if (tab === 'weight' || tab === 'feed') activeTab.value = tab
})
</script>

<template>
  <zw-shell side="admin" active="WA-D01" :crumbs="['基础数据', '品类品种与生长标准']">
    <zw-page-head title="品类品种与生长标准" desc="品类和品种供用户端选择；妊娠期、孵化期用于推算预产期和预计出雏日；标准体重和饲喂标准用于称重偏离判断和“套用品种标准”。修改都记入操作日志。" />

    <zw-notice v-if="forbidden" type="error" title="你没有基础数据的查看权限">请联系超级管理员在“管理员与权限”中开通 WA-D01 的查看权限。</zw-notice>

    <div v-if="!forbidden" class="category-layout">
      <zw-card flush class="category-tree-card">
        <div class="category-search"><v-text-field v-model="treeKeyword" placeholder="搜索品种" prepend-inner-icon="search" density="compact" hide-details clearable /></div>
        <v-skeleton-loader v-if="treeLoading" type="list-item@8" />
        <zw-empty v-else-if="treeError" icon="cloud_off" title="品类加载失败" :desc="treeError" tone="error">
          <v-btn variant="outlined" @click="loadTree(false)">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!tree.length" icon="pets" title="还没有品类" desc="新增品类和品种后，用户端建档时才能选择。">
          <v-btn v-if="canHandle" color="primary" @click="openCategoryCreate">新增品类</v-btn>
        </zw-empty>
        <template v-else>
          <div class="category-tree-scroll">
            <div v-for="category in filteredTree" :key="category.id" class="category-group">
              <div class="category-group-head">
                <zw-icon :name="category.species === 'POULTRY' ? 'egg' : 'pets'" :size="16" />
                <span>{{ category.name }}</span>
                <span class="num">{{ category.breed_count }} 个品种</span>
                <a v-if="canHandle" class="category-edit" title="编辑品类" @click="openCategoryEdit(category)">编辑</a>
              </div>
              <button v-for="breed in category.breeds" :key="breed.id" type="button" class="category-breed" :class="{ on: selectedBreedId === breed.id }" @click="selectBreed(breed.id)">
                <span class="category-breed-main">
                  <span class="category-breed-name">{{ breed.name }}</span>
                  <span class="category-breed-sub">{{ breedSummary(breed) }}</span>
                </span>
                <zw-tag v-if="breed.status === 'DISABLED'" size="small">已停用</zw-tag>
              </button>
            </div>
            <zw-empty v-if="!filteredTree.length" icon="search_off" title="没有找到相关品种" desc="换一个名称试试。" />
          </div>
          <div class="category-tree-foot">
            <v-btn v-if="canHandle" variant="outlined" size="small" prepend-icon="add" @click="openCategoryCreate">新增品类</v-btn>
            <v-btn v-if="canHandle" variant="outlined" size="small" prepend-icon="add" @click="openBreedCreate">新增品种</v-btn>
          </div>
        </template>
      </zw-card>

      <zw-card v-if="detailLoading || detailError || !detail" flush class="category-detail-card">
        <v-skeleton-loader v-if="detailLoading" type="article@6" />
        <zw-empty v-else-if="detailError" icon="cloud_off" title="品种详情加载失败" :desc="detailError" tone="error">
          <v-btn variant="outlined" @click="selectedBreedId && selectBreed(selectedBreedId)">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else icon="pets" title="选择一个品种" desc="从左侧选择品种，查看生长标准与繁育参数。" />
      </zw-card>
      <zw-card v-else flush class="category-detail-card">
        <div class="category-detail-head">
          <span class="zw-ico-tile zw-ico-tile--primary zw-ico-tile--lg"><zw-icon name="pets" :size="22" /></span>
          <div class="category-detail-title">
            <div class="category-detail-name">
              {{ detail.name }}
              <zw-tag type="outline" size="small">{{ detail.category?.name || '—' }}</zw-tag>
              <zw-tag :type="detail.status === 'ENABLED' ? 'success' : 'neutral'" :icon="detail.status === 'ENABLED' ? 'check' : 'block'" size="small">{{ detail.status_text || (detail.status === 'ENABLED' ? '启用' : '已停用') }}</zw-tag>
            </div>
            <div class="category-detail-meta">
              {{ detail.description || '暂无品种说明' }} · 计数单位 {{ detail.category?.count_unit === 'BIRD' ? '只' : '头' }}
              <template v-if="detail.active_batch_count !== undefined"> · 在养批次 {{ formatNumber(detail.active_batch_count) }} 个<template v-if="detail.farm_count">（分布 {{ formatNumber(detail.farm_count) }} 个养殖场）</template></template>
              <template v-if="detail.breeding_animal_count"> · 在档种畜 {{ formatNumber(detail.breeding_animal_count) }} 头</template>
            </div>
          </div>
          <v-btn v-if="canHandle" variant="outlined" prepend-icon="edit" @click="openBreedEdit">编辑品种</v-btn>
        </div>

        <v-tabs :model-value="activeTab" class="zw-tabs-line category-tabs" @update:model-value="switchTab($event as 'weight' | 'feed' | 'breeding')">
          <v-tab value="weight">标准体重</v-tab>
          <v-tab value="feed">饲喂标准</v-tab>
          <v-tab value="breeding">{{ isPoultry ? '孵化参数' : '繁育参数' }}</v-tab>
        </v-tabs>

        <div v-if="standardError" class="category-inline-error">{{ standardError }}</div>

        <template v-if="activeTab === 'weight'">
          <div v-if="standardLoading" class="category-detail-pad"><v-skeleton-loader type="table-row@6" /></div>
          <template v-else>
            <div class="category-version-bar">
              <zw-icon name="verified" :size="18" color="primary" />
              <span v-if="weight?.online">
                当前为 <b>第 {{ weight.online.version_no }} 版</b>
                <template v-if="weight.online.published_at"> · {{ formatDate(weight.online.published_at) }} {{ weight.online.published_by_name || '' }}发布</template>
                · 允许偏差 <b>{{ weightTolerance }}</b>
              </span>
              <span v-else-if="activeWeightVersion">还没有发布过标准体重，下面是未发布的草稿 · 允许偏差 <b>{{ weightTolerance }}</b></span>
              <span v-else>还没有标准体重，请先编辑并发布。</span>
              <span class="category-version-actions">
                <v-btn variant="text" color="primary" size="small" prepend-icon="history" @click="openVersions('weight')">版本记录</v-btn>
                <v-btn v-if="canHandle" color="primary" size="small" prepend-icon="edit" @click="openWeightEditor">编辑标准</v-btn>
              </span>
            </div>
            <div v-if="weight?.draft" class="category-draft-tip">
              <zw-icon name="warning" :size="16" />有 {{ weight.draft.changed_count || '若干' }} 项修改还没有发布
              <span class="category-draft-actions"><a class="zw-link" @click="openWeightEditor">继续编辑</a><a class="zw-link danger" @click="openDiscard('weight')">放弃修改</a></span>
            </div>
            <div v-if="!weightPoints.length" class="category-detail-pad">
              <zw-empty icon="scale" title="还没有标准体重" desc="编辑标准体重并发布后，用户端称重会按日龄折算标准。">
                <v-btn v-if="canHandle" color="primary" @click="openWeightEditor">编辑标准</v-btn>
              </zw-empty>
            </div>
            <div v-else class="category-detail-body category-weight-grid">
              <div class="zw-table-wrap">
                <v-table density="compact" class="category-mini-table">
                  <thead><tr><th>日龄</th><th class="text-end">标准体重</th><th class="text-end">下限</th><th class="text-end">上限</th><th class="text-end">日增重</th></tr></thead>
                  <tbody>
                    <tr v-for="(point, index) in weightPoints" :key="point.day_age">
                      <td>{{ point.day_age }} 日龄</td>
                      <td class="text-end"><b>{{ formatPoint(point.std_weight) }}</b></td>
                      <td class="text-end zw-muted">{{ formatPoint(point.lower) }}</td>
                      <td class="text-end zw-muted">{{ formatPoint(point.upper) }}</td>
                      <td class="text-end zw-muted">{{ dailyGain(index) }}</td>
                    </tr>
                  </tbody>
                </v-table>
              </div>
              <div>
                <div class="zw-section-title">标准体重曲线</div>
                <zw-line-chart :labels="chartLabels" :series="chartSeries" :height="290" unit="千克 · 横轴为日龄" :x-every="2" />
                <div class="zw-section-title">用户端如何使用</div>
                <div class="category-usage">
                  <div><zw-icon name="scale" :size="18" color="primary" /><span>称重登记按日龄折算标准体重，偏离超过 {{ weightTolerance }} 标为“生长偏慢”或“生长偏快”。</span></div>
                  <div><zw-icon name="show_chart" :size="18" color="primary" /><span>生长曲线画出标准线和允许偏差带。发布新版本后，用户端提示“品种标准体重已更新，偏离情况已按新标准重新计算。”</span></div>
                </div>
              </div>
            </div>
          </template>
        </template>

        <template v-if="activeTab === 'feed'">
          <div v-if="standardLoading" class="category-detail-pad"><v-skeleton-loader type="table-row@5" /></div>
          <template v-else>
            <div class="category-version-bar">
              <zw-icon name="verified" :size="18" color="primary" />
              <span v-if="feed?.online">
                当前为 <b>第 {{ feed.online.version_no }} 版</b>
                <template v-if="feed.online.published_at"> · {{ formatDate(feed.online.published_at) }} {{ feed.online.published_by_name || '' }}发布</template>
                · 投喂异常判定 ±{{ Number((activeFeedVersion?.deviation_rate || 0) * 100).toFixed(0) }}%
              </span>
              <span v-else-if="activeFeedVersion">还没有发布过饲喂标准，下面是未发布的草稿 · 投喂异常判定 ±{{ Number((activeFeedVersion.deviation_rate || 0) * 100).toFixed(0) }}%</span>
              <span v-else>还没有饲喂标准，请先编辑并发布。</span>
              <span class="category-version-actions">
                <v-btn variant="text" color="primary" size="small" prepend-icon="history" @click="openVersions('feed')">版本记录</v-btn>
                <v-btn v-if="canHandle" color="primary" size="small" prepend-icon="edit" @click="openFeedEditor">编辑标准</v-btn>
              </span>
            </div>
            <div v-if="feed?.draft" class="category-draft-tip">
              <zw-icon name="warning" :size="16" />有未发布的饲喂标准修改
              <span class="category-draft-actions"><a class="zw-link" @click="openFeedEditor">继续编辑</a><a class="zw-link danger" @click="openDiscard('feed')">放弃修改</a></span>
            </div>
            <div v-if="!currentFeed?.stages?.length" class="category-detail-pad">
              <zw-empty icon="rice_bowl" title="还没有饲喂标准" desc="按生长阶段维护建议饲料和每日投喂量，用户端可套用品种标准。">
                <v-btn v-if="canHandle" color="primary" @click="openFeedEditor">编辑标准</v-btn>
              </zw-empty>
            </div>
            <div v-else class="category-detail-body">
              <div class="zw-table-wrap">
                <v-table class="category-feed-table">
                  <thead><tr><th>生长阶段</th><th>日龄范围</th><th>建议饲料</th><th class="text-end">每头日投喂量</th><th>餐次</th><th>投喂时间</th></tr></thead>
                  <tbody>
                    <tr v-for="stage in currentFeed?.stages || []" :key="stage.stage_id || stage.stage_name">
                      <td><b>{{ stage.stage_name }}</b></td>
                      <td class="num">{{ stage.day_age_start }}–{{ stage.day_age_end || '出栏' }} 日龄</td>
                      <td>{{ stage.suggested_feed_name }}</td>
                      <td class="text-end num">{{ stage.daily_feed }} 千克</td>
                      <td>每天 {{ stage.meals_per_day }} 餐</td>
                      <td class="num">{{ (stage.meal_times || []).join(' / ') }}</td>
                    </tr>
                  </tbody>
                </v-table>
              </div>
              <div class="zw-form-3 category-feed-rules">
                <zw-form-item label="投喂异常判定（默认值）" hint="实际投喂量偏离计划量超过这个比例时标为投喂异常"><div class="zw-readonly">{{ Number((currentFeed?.deviation_rate || 0) * 100).toFixed(0) }}%</div></zw-form-item>
                <zw-form-item label="下一阶段换料" hint="批次进入下一生长阶段后，次日起自动换用新的投喂量"><div class="zw-readonly">次日起自动换用</div></zw-form-item>
                <zw-form-item label="饲料匹配" hint="套用时按“建议饲料”匹配本场饲料档案中同名或同类型的饲料"><div class="zw-readonly">按名称和饲料类型匹配</div></zw-form-item>
              </div>
              <div class="zw-notice">发布新版本不会改动用户已保存的饲喂方案，由养殖户决定是否重新“套用品种标准”。</div>
            </div>
          </template>
        </template>

        <template v-if="activeTab === 'breeding'">
          <div class="category-detail-body">
            <zw-desc :cols="2" bordered :label-width="120">
              <zw-desc-item v-if="isPoultry" label="孵化期">{{ detail.incubation_days || '—' }} 天</zw-desc-item>
              <zw-desc-item v-else label="妊娠期">{{ detail.gestation_days || '种猪不设标准体重' }}</zw-desc-item>
              <zw-desc-item v-if="isPoultry" label="预计出雏">{{ detail.incubation_days ? `入孵日期 + ${detail.incubation_days} 天` : '—' }}</zw-desc-item>
              <zw-desc-item v-else label="预产提醒">预产期前 3 天和当天各提醒一次</zw-desc-item>
              <zw-desc-item v-if="!isPoultry" label="待妊检天数">{{ detail.pregnancy_check_days || '—' }} 天</zw-desc-item>
              <zw-desc-item label="推算规则" :span="2">{{ isPoultry ? '预计出雏日 = 入孵日期 + 孵化期' : '预产期 = 首次配种日期 + 妊娠期；同一情期再次配种时，仍按首次配种日期计算' }}</zw-desc-item>
              <zw-desc-item label="后代默认品种">{{ detail.offspring_breed?.name || '—' }}</zw-desc-item>
              <zw-desc-item label="上次修改">{{ detail.last_modified_at ? `${formatDateTime(detail.last_modified_at)} · ${detail.last_modified_by_name || ''}` : '—' }}</zw-desc-item>
            </zw-desc>
            <zw-notice class="category-breeding-notice">修改妊娠期只影响之后登记的配种；已推算的预产期不变，避免已安排的产房和待办被打乱。</zw-notice>
            <div class="zw-grid-2 category-breeding-links">
              <zw-card title="免疫程序模板">
                <div class="zw-between"><span class="zw-muted">{{ selectedBreedId ? '查看这个品种使用的免疫模板与版本。' : '' }}</span><v-btn variant="outlined" size="small" @click="$router.push('/base/immune-templates')">打开免疫模板</v-btn></div>
              </zw-card>
              <zw-card title="巡检项目模板">
                <div class="zw-between"><span class="zw-muted">巡检模板按品类配置，登记巡检时使用。</span><v-btn variant="outlined" size="small" @click="$router.push('/base/inspect-templates')">打开巡检模板</v-btn></div>
              </zw-card>
            </div>
          </div>
        </template>
      </zw-card>
    </div>

    <!-- 标准体重编辑 -->
    <zw-drawer v-model="weightDrawer" :title="`编辑标准体重 · ${detail?.name || ''}`" sub="保存后生成草稿；发布后用户端立即按新标准计算" width="760">
      <div class="zw-form-3 category-std-form">
        <zw-form-item label="允许偏差" required hint="超出时标为生长偏慢 / 偏快">
          <v-text-field v-model.number="weightForm.tolerance" type="number" min="1" max="50" prefix="±" suffix="%" />
        </zw-form-item>
        <zw-form-item label="建议每月称重次数" required>
          <v-text-field v-model.number="weightForm.weighTimes" type="number" min="1" max="8" suffix="次" />
        </zw-form-item>
        <zw-form-item label="生效时间" required>
          <v-select v-model="weightForm.effective_mode" :items="[{ title: '发布后立即生效', value: 'IMMEDIATE' }, { title: '指定日期生效', value: 'ON_DATE' }]" />
        </zw-form-item>
        <zw-form-item v-if="weightForm.effective_mode === 'ON_DATE'" label="指定日期" required>
          <v-text-field v-model="weightForm.effective_date" type="date" />
        </zw-form-item>
      </div>
      <div class="zw-table-wrap">
        <v-table density="compact" class="category-edit-table">
          <thead><tr><th>日龄</th><th class="text-end">线上</th><th class="text-end">新值（千克）</th><th class="text-end">变化</th><th class="text-end">日增重</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(point, index) in weightForm.points" :key="index">
              <td><v-text-field v-model.number="point.day_age" type="number" min="1" max="999" density="compact" hide-details class="category-days-input" /></td>
              <td class="text-end zw-muted">{{ point.online_weight !== null && point.online_weight !== undefined ? formatPoint(Number(point.online_weight)) : '—' }}</td>
              <td class="text-end"><v-text-field v-model="point.std_weight" type="number" step="0.1" min="0" density="compact" hide-details class="category-weight-input" /></td>
              <td class="text-end" :class="{ 'category-changed': point.online_weight !== null && point.online_weight !== undefined && Number(point.std_weight) !== Number(point.online_weight) }">
                {{ point.online_weight !== null && point.online_weight !== undefined && point.std_weight ? (Number(point.std_weight) - Number(point.online_weight)).toFixed(1) : '—' }}
              </td>
              <td class="text-end zw-muted">{{ index > 0 && point.std_weight ? ((Number(point.std_weight) - Number(weightForm.points[index - 1]?.std_weight || 0)) / Math.max(1, point.day_age - Number(weightForm.points[index - 1]?.day_age || 0))).toFixed(2) : '—' }}</td>
              <td><a class="zw-link danger" @click="removeWeightPoint(index)">删除</a></td>
            </tr>
          </tbody>
        </v-table>
      </div>
      <v-btn variant="text" color="primary" prepend-icon="add" class="mt-2" @click="addWeightPoint">添加日龄点</v-btn>
      <zw-notice v-if="weightImpact" type="warning" :title="`发布后影响 ${formatNumber(weightImpact.active_batch_count || 0)} 个在养批次`">
        生长曲线和称重偏离按新标准重新计算，用户端提示“品种标准体重已更新，偏离情况已按新标准重新计算。”；分布 {{ formatNumber(weightImpact.farm_count || 0) }} 个养殖场。
      </zw-notice>
      <zw-form-item label="更新说明" required hint="保存草稿时一起暂存；发布后显示在版本记录中，记入操作日志">
        <v-text-field v-model="weightForm.update_note" placeholder="请说明这次调整的依据" maxlength="100" />
      </zw-form-item>
      <div v-if="weightFormError" class="category-inline-error">{{ weightFormError }}</div>
      <template #foot>
        <span class="zw-foot-note">发布记入操作日志；可以在版本记录中回退</span>
        <v-btn variant="outlined" :loading="weightSaving" :disabled="weightPublishing" @click="saveWeight(false)">保存草稿</v-btn>
        <v-btn color="primary" prepend-icon="publish" :loading="weightPublishing" :disabled="weightSaving" @click="publishWeight">发布新版本</v-btn>
      </template>
    </zw-drawer>

    <!-- 饲喂标准编辑 -->
    <zw-drawer v-model="feedDrawer" :title="`编辑饲喂标准 · ${detail?.name || ''}`" sub="保存后生成草稿；发布后用户端即可套用新标准" width="760">
      <zw-form-item label="投喂异常判定（默认值）" required hint="实际投喂量偏离计划量超过这个比例时标为投喂异常">
        <v-text-field v-model.number="feedForm.deviation" type="number" min="1" max="50" suffix="%" class="category-deviation" />
      </zw-form-item>
      <div class="zw-table-wrap">
        <v-table density="compact" class="category-edit-table category-feed-edit">
          <thead><tr><th>阶段名称</th><th>简称</th><th>起始日龄</th><th>结束日龄</th><th>饲料类型</th><th>建议饲料</th><th>每日投喂量</th><th>餐次</th><th>投喂时间</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(stage, index) in feedForm.stages" :key="index">
              <td><v-text-field v-model="stage.stage_name" density="compact" hide-details maxlength="10" /></td>
              <td><v-text-field v-model="stage.stage_short_name" density="compact" hide-details maxlength="10" /></td>
              <td><v-text-field v-model.number="stage.day_age_start" type="number" min="1" density="compact" hide-details /></td>
              <td><v-text-field v-model.number="stage.day_age_end" type="number" min="1" density="compact" hide-details placeholder="出栏" /></td>
              <td><v-select v-model="stage.feed_type_code" :items="feedTypeOptions" density="compact" hide-details /></td>
              <td><v-text-field v-model="stage.suggested_feed_name" density="compact" hide-details maxlength="20" /></td>
              <td><v-text-field v-model="stage.daily_feed" type="number" step="0.01" density="compact" hide-details suffix="kg" /></td>
              <td><v-text-field v-model.number="stage.meals_per_day" type="number" min="1" max="4" density="compact" hide-details /></td>
              <td><v-text-field v-model="stage.meal_times" density="compact" hide-details placeholder="07:00, 11:30" /></td>
              <td><a class="zw-link danger" @click="removeFeedStage(index)">删除</a></td>
            </tr>
          </tbody>
        </v-table>
      </div>
      <v-btn variant="text" color="primary" prepend-icon="add" class="mt-2" @click="addFeedStage">添加阶段</v-btn>
      <zw-notice v-if="feedImpact" type="warning" title="发布新版本的影响">
        发布新版本不会改动用户已保存的方案；{{ feedImpact.applied_plan_count || 0 }} 个饲喂方案可在用户端重新套用。
      </zw-notice>
      <zw-form-item label="更新说明" required hint="保存草稿时一起暂存；发布后显示在版本记录中，记入操作日志">
        <v-text-field v-model="feedForm.update_note" placeholder="请说明这次调整的依据" maxlength="100" />
      </zw-form-item>
      <div v-if="feedFormError" class="category-inline-error">{{ feedFormError }}</div>
      <template #foot>
        <v-btn variant="outlined" :loading="feedSaving" :disabled="feedPublishing" @click="saveFeed(false)">保存草稿</v-btn>
        <v-btn color="primary" prepend-icon="publish" :loading="feedPublishing" :disabled="feedSaving" @click="publishFeed">发布新版本</v-btn>
      </template>
    </zw-drawer>

    <!-- 版本记录 -->
    <zw-drawer v-model="versionOpen" :title="`版本记录 · ${detail?.name || ''}`" sub="每个版本都保留，可查看内容并回退" width="640">
      <v-skeleton-loader v-if="versionsLoading" type="paragraph@6" />
      <template v-else>
        <div class="zw-timeline category-versions">
          <button v-for="version in versions" :key="version.version_no" type="button" class="category-version-item" :class="{ on: versionSelected?.version_no === version.version_no }" @click="versionSelected = version">
            <span class="category-version-name">第 {{ version.version_no }} 版 <zw-tag v-if="versionSelected?.version_no === version.version_no" type="primary" size="small">查看中</zw-tag></span>
            <span class="zw-muted">{{ version.published_at ? formatDateTime(version.published_at) : '' }} · {{ version.published_by_name || '' }}</span>
            <span class="category-version-note">{{ version.update_note || '—' }}</span>
          </button>
          <div v-if="!versions.length" class="zw-muted">还没有版本记录</div>
        </div>
        <template v-if="versionSelected">
          <div class="zw-section-title">版本内容</div>
          <zw-compare :rows="versionCompare.length ? versionCompare : [{ label: '内容', before: '—', after: '—' }]" before-title="版本内容" after-title="摘要" />
          <div v-if="versionSelected.update_note" class="zw-muted category-version-desc">{{ versionSelected.update_note }}</div>
          <div v-if="hasOnlineStandard" class="category-rollback">
            <div class="zw-section-title">回退到第 {{ versionSelected.version_no }} 版</div>
            <p class="zw-muted">回退会把该版本内容生成新的线上版本，当前版本保留在历史中。</p>
            <zw-form-item label="回退原因" required>
              <v-textarea v-model="rollbackReason" placeholder="请填写回退原因" :rows="2" maxlength="100" counter />
            </zw-form-item>
            <div v-if="versionError" class="category-inline-error">{{ versionError }}</div>
            <v-btn color="primary" variant="outlined" prepend-icon="history" :loading="rollbackSaving" @click="submitRollback">确认回退</v-btn>
          </div>
        </template>
      </template>
      <template #foot><v-btn variant="outlined" @click="versionOpen = false">关闭</v-btn></template>
    </zw-drawer>

    <!-- 放弃草稿 -->
    <zw-dialog v-model="discardOpen" title="放弃未发布的修改？" width="440" icon="undo" tone="warning">
      <p class="category-dialog-text">放弃后，用户端仍按当前线上版本运行，未发布的修改不能恢复。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="discardSaving" @click="discardOpen = false">继续保留</v-btn>
        <v-btn color="error" :loading="discardSaving" @click="confirmDiscard">放弃修改</v-btn>
      </template>
    </zw-dialog>

    <!-- 品种编辑 -->
    <zw-drawer v-model="breedDrawer" :title="breedForm.id ? '编辑品种' : '新增品种'" sub="品种名称在用户端的批次、种畜档案和行情筛选中显示" width="560">
      <div class="zw-form-2">
        <zw-form-item label="所属品类" required :error="breedFieldErrors.category_id">
          <v-select v-model="breedForm.category_id" :items="tree.map((c) => ({ title: c.name, value: c.id }))" placeholder="请选择品类" :error="!!breedFieldErrors.category_id" />
        </zw-form-item>
        <zw-form-item label="品种名称" required :error="breedFieldErrors.name">
          <v-text-field v-model="breedForm.name" placeholder="请输入品种名称" maxlength="20" counter :error="!!breedFieldErrors.name" />
        </zw-form-item>
        <zw-form-item label="说明" optional style="grid-column: 1 / -1">
          <v-text-field v-model="breedForm.description" placeholder="例如：杜洛克 × 长白 × 大白三元杂交" maxlength="100" />
        </zw-form-item>
        <zw-form-item v-if="!breedIsPoultry" :label="breedIsBreeding ? '妊娠期' : '妊娠期（选填）'" :required="breedIsBreeding" hint="用于推算预产期：首次配种日期 + 妊娠期">
          <v-text-field v-model.number="breedForm.gestation_days" type="number" min="1" max="400" suffix="天" :error="!!breedFieldErrors.gestation_days" />
        </zw-form-item>
        <zw-form-item v-else label="孵化期" required hint="用于推算预计出雏日：入孵日期 + 孵化期">
          <v-text-field v-model.number="breedForm.incubation_days" type="number" min="1" max="400" suffix="天" :error="!!breedFieldErrors.incubation_days" />
        </zw-form-item>
        <zw-form-item v-if="!breedIsPoultry && breedForm.gestation_days" label="待妊检天数" hint="必须小于妊娠期">
          <v-text-field v-model.number="breedForm.pregnancy_check_days" type="number" min="1" suffix="天" :error="!!breedFieldErrors.pregnancy_check_days" />
        </zw-form-item>
        <zw-form-item label="后代默认品种" optional hint="同畜种且启用的品种">
          <v-select v-model="breedForm.offspring_breed_id" :items="offspringOptions" clearable placeholder="不设置" />
        </zw-form-item>
        <zw-form-item label="状态">
          <v-switch v-model="breedForm.status" :true-value="'ENABLED'" :false-value="'DISABLED'" :label="breedForm.status === 'ENABLED' ? '启用' : '已停用'" density="compact" hide-details />
        </zw-form-item>
      </div>
      <zw-form-item v-if="breedForm.status === 'DISABLED'" label="停用原因" required>
        <v-textarea v-model="breedForm.change_reason" placeholder="停用后新建批次和种畜档案时不能再选这个品种" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <zw-form-item v-else label="修改原因" :required="breedReasonRequired" :optional="!breedReasonRequired" hint="修改妊娠期、孵化期、待妊检天数或停用时必填">
        <v-text-field v-model="breedForm.change_reason" placeholder="记入操作日志" maxlength="100" />
      </zw-form-item>
      <zw-notice>修改妊娠期只影响之后登记的配种；已推算的预产期不变，避免已安排的产房和待办被打乱。</zw-notice>
      <div v-if="breedError" class="category-inline-error">{{ breedError }}</div>
      <template #foot>
        <span class="zw-foot-note">保存记入操作日志</span>
        <v-btn variant="outlined" :disabled="breedSaving" @click="breedDrawer = false">取消</v-btn>
        <v-btn color="primary" :loading="breedSaving" @click="submitBreed">保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 品类编辑 -->
    <zw-drawer v-model="categoryDrawer" :title="categoryTarget ? '编辑品类' : '新增品类'" sub="品类决定用户端可选的畜种和计数单位" width="560">
      <div class="zw-form-2">
        <zw-form-item label="品类名称" required>
          <v-text-field v-model="categoryForm.name" placeholder="最多 10 个字" maxlength="10" />
        </zw-form-item>
        <zw-form-item label="畜种" required>
          <v-select v-model="categoryForm.species" :items="speciesOptions" :disabled="!!categoryTarget" />
        </zw-form-item>
        <zw-form-item label="排序">
          <v-text-field v-model.number="categoryForm.sort_no" type="number" min="0" />
        </zw-form-item>
        <zw-form-item label="种畜品类">
          <v-switch v-model="categoryForm.is_breeding" :label="categoryForm.is_breeding ? '是' : '否'" :disabled="categoryForm.species === 'POULTRY'" density="compact" hide-details />
        </zw-form-item>
        <zw-form-item label="状态">
          <v-select v-model="categoryForm.status" :items="[{ title: '启用', value: 'ENABLED' }, { title: '停用', value: 'DISABLED' }]" />
        </zw-form-item>
      </div>
      <zw-form-item v-if="categoryForm.status === 'DISABLED'" label="停用原因" required>
        <v-textarea v-model="categoryForm.disable_reason" placeholder="请填写停用原因" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <div v-if="categoryError" class="category-inline-error">{{ categoryError }}</div>
      <template #foot>
        <span class="zw-foot-note">保存记入操作日志</span>
        <v-btn variant="outlined" :disabled="categorySaving" @click="categoryDrawer = false">取消</v-btn>
        <v-btn color="primary" :loading="categorySaving" @click="submitCategory">保存</v-btn>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.category-layout { display: grid; grid-template-columns: 248px minmax(0, 1fr); gap: 16px; align-items: start; }
.category-tree-card { min-height: 540px; display: flex; flex-direction: column; }
.category-search { padding: 10px 12px 6px; }
.category-tree-scroll { flex: 1; overflow: auto; max-height: 680px; }
.category-group-head { display: flex; align-items: center; gap: 6px; height: 34px; padding: 0 12px; font-size: 13px; font-weight: 600; color: var(--text-2); }
.category-group-head .num { margin-left: auto; font-size: 12px; font-weight: 400; color: var(--text-3); }
.category-edit { margin-left: 6px; font-size: 12px; color: var(--primary); cursor: pointer; font-weight: 400; }
.category-breed { width: 100%; min-height: 44px; display: flex; align-items: center; gap: 8px; padding: 5px 12px 5px 32px; border: 0; background: transparent; font: inherit; text-align: left; cursor: pointer; }
.category-breed:hover { background: var(--fill-1); }
.category-breed.on { background: var(--primary-subtle); }
.category-breed.on .category-breed-name { color: var(--primary); font-weight: 500; }
.category-breed-main { flex: 1; min-width: 0; }
.category-breed-name { display: block; font-size: 14px; }
.category-breed-sub { display: block; margin-top: 1px; font-size: 11px; line-height: 16px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-tree-foot { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid var(--line-1); }
.category-detail-card { min-height: 540px; }
.category-detail-head { display: flex; align-items: flex-start; gap: 12px; padding: 18px 20px 0; }
.category-detail-title { flex: 1; min-width: 0; }
.category-detail-name { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 18px; line-height: 26px; font-weight: 600; }
.category-detail-meta { margin-top: 3px; font-size: 12px; line-height: 20px; color: var(--text-2); }
.category-tabs { padding: 0 20px; margin-top: 8px; }
.category-version-bar { display: flex; align-items: center; gap: 10px; margin: 14px 20px 0; padding: 10px 14px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 13px; color: var(--text-2); }
.category-version-bar b { color: var(--text-1); font-weight: 500; }
.category-version-actions { margin-left: auto; display: flex; gap: 8px; }
.category-draft-tip { display: flex; align-items: center; gap: 6px; margin: 10px 20px 0; padding: 8px 12px; border-radius: var(--radius-sm); background: var(--warning-container); color: var(--on-warning-container); font-size: 12px; }
.category-draft-actions { margin-left: auto; display: flex; gap: 10px; }
.category-detail-body { padding: 16px 20px 20px; }
.category-weight-grid { display: grid; grid-template-columns: 430px minmax(0, 1fr); gap: 20px; align-items: start; }
.category-detail-pad { padding: 20px; }
.category-mini-table :deep(td), .category-mini-table :deep(th) { height: 34px; font-size: 13px; padding: 0 10px; }
.category-feed-table :deep(td), .category-feed-table :deep(th) { height: 40px; font-size: 13px; }
.category-feed-rules { margin-top: 16px; margin-bottom: 16px; }
.category-usage { display: flex; flex-direction: column; gap: 8px; font-size: 13px; line-height: 21px; }
.category-usage > div { display: flex; gap: 6px; }
.category-breeding-notice { margin-top: 16px; }
.category-breeding-links { margin-top: 16px; }
.category-std-form { margin-bottom: 16px; }
.category-edit-table :deep(td) { padding: 4px 6px; height: 44px; }
.category-days-input { width: 78px; }
.category-weight-input { width: 106px; }
.category-weight-input :deep(input) { text-align: right; }
.category-changed { color: var(--warning); font-size: 13px; }
.category-feed-edit :deep(td) { min-width: 74px; }
.category-deviation { max-width: 180px; }
.category-inline-error { margin: 10px 0; font-size: 12px; color: var(--error); }
.category-versions { display: flex; flex-direction: column; gap: 0; margin-bottom: 16px; }
.category-version-item { width: 100%; text-align: left; padding: 10px 14px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--bg-card); cursor: pointer; display: flex; flex-direction: column; gap: 2px; font: inherit; margin-bottom: 6px; }
.category-version-item.on { border-color: var(--primary); background: var(--primary-subtle); }
.category-version-name { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 500; }
.category-version-note { font-size: 12px; color: var(--text-2); }
.category-version-desc { margin-top: 8px; }
.category-rollback { margin-top: 16px; border-top: 1px solid var(--line-1); padding-top: 8px; }
.category-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); }
</style>
