import type { PageVO } from '@zmt/shared'
import { http } from '@/api/client'

/* ---------------- WA-D01 品类 / 品种 / 生长标准 ---------------- */

export interface CategoryNodeBreed {
  id: number
  name: string
  status: string
  status_text?: string
  sort_no: number
  gestation_days?: number | null
  incubation_days?: number | null
  has_std_weight?: boolean
  has_feed_std?: boolean
  has_immune_template?: boolean
}

export interface CategoryNode {
  id: number
  name: string
  species: string
  species_text?: string
  count_unit: string
  count_unit_text?: string
  is_breeding?: boolean
  status: string
  status_text?: string
  sort_no: number
  breed_count: number
  breeds: CategoryNodeBreed[]
}

export interface CategoryPayload {
  name: string
  species: string
  is_breeding?: boolean
  sort_no?: number
  status?: string
  disable_reason?: string
  version?: number
}

export interface BreedDetail {
  id: number
  name: string
  description?: string
  category?: { id: number; name: string; species?: string; count_unit?: string; is_breeding?: boolean; version?: number }
  gestation_days?: number | null
  incubation_days?: number | null
  pregnancy_check_days?: number | null
  offspring_breed?: { id: number; name: string } | null
  status: string
  status_text?: string
  disable_reason?: string
  active_batch_count?: number
  farm_count?: number
  breeding_animal_count?: number
  tabs?: string[]
  last_modified_by_name?: string
  last_modified_at?: string
  version?: number
}

export interface BreedPayload {
  category_id: number
  name: string
  description?: string
  gestation_days?: number | null
  incubation_days?: number | null
  pregnancy_check_days?: number | null
  offspring_breed_id?: number | null
  sort_no?: number
  status?: string
  disable_reason?: string
  change_reason?: string
  version?: number
}

export interface StdWeightPoint {
  day_age: number
  std_weight: number
  lower?: number
  upper?: number
  daily_gain?: number | null
  online_weight?: number | null
  diff?: number | null
  changed?: boolean
}

export interface StdWeightVersion {
  version_id?: number
  version_no?: number | null
  status?: string
  status_text?: string
  tolerance_rate?: number
  suggested_weigh_times?: number
  effective_mode?: string
  effective_date?: string
  update_note?: string
  published_by_name?: string
  published_at?: string
  points?: StdWeightPoint[]
  changed_count?: number
  version?: number
}

export interface StdWeightData {
  breed_id: number
  online?: StdWeightVersion
  scheduled?: StdWeightVersion
  draft?: StdWeightVersion
}

export interface FeedStage {
  stage_id?: number
  sort_no?: number
  stage_name: string
  stage_short_name: string
  day_age_start: number
  day_age_end?: number | null
  feed_type_code: string
  feed_type_text?: string
  suggested_feed_name: string
  daily_feed: number
  meals_per_day: number
  meal_times: string[]
  changed?: boolean
}

export interface FeedVersion {
  version_id?: number
  version_no?: number | null
  status?: string
  status_text?: string
  deviation_rate?: number
  update_note?: string
  published_by_name?: string
  published_at?: string
  applied_plan_count?: number
  version?: number
  stages?: FeedStage[]
}

export interface FeedStdData {
  breed_id: number
  online?: FeedVersion
  draft?: FeedVersion
}

export interface StdImpact {
  active_batch_count?: number
  farm_count?: number
  affected_batch_count?: number
  applied_plan_count?: number
  flag_changes?: Array<{ flag?: string; before?: number; after?: number }>
}

export interface StdVersion {
  version_id: number
  version_no: number
  status: string
  status_text?: string
  effective_mode?: string
  effective_date?: string
  update_note?: string
  rollback_from_no?: number | null
  published_by_name?: string
  published_at?: string
  tolerance_rate?: number
  suggested_weigh_times?: number
  deviation_rate?: number
  points?: StdWeightPoint[]
  stages?: FeedStage[]
}

export function getCategoryTree(keyword = '') {
  return http().get<CategoryNode[]>('/categories/tree', { keyword })
}

export function createCategory(payload: CategoryPayload) {
  return http().post('/categories', payload)
}

export function updateCategory(id: number, payload: CategoryPayload) {
  return http().put(`/categories/${id}`, payload)
}

export function getBreedDetail(id: number) {
  return http().get<BreedDetail>(`/breeds/${id}`)
}

export function createBreed(payload: BreedPayload) {
  return http().post('/breeds', payload)
}

export function updateBreed(id: number, payload: BreedPayload) {
  return http().put(`/breeds/${id}`, payload)
}

export function getBreedStandard(id: number, type: 'weight' | 'feed') {
  return http().get<StdWeightData | FeedStdData>(`/breeds/${id}/standards/${type}`)
}

export function saveWeightDraft(id: number, payload: {
  tolerance_rate: number
  suggested_weigh_times: number
  effective_mode: string
  effective_date?: string | null
  points: Array<{ day_age: number; std_weight: number | string }>
  version?: number
  /** 暂存在草稿上的更新说明（发布时以发布请求为准） */
  update_note?: string
}) {
  return http().put<StdWeightData>(`/breeds/${id}/standards/weight/draft`, payload)
}

export function saveFeedDraft(id: number, payload: {
  deviation_rate: number
  stages: Array<{
    stage_name: string
    stage_short_name: string
    day_age_start: number
    day_age_end?: number | null
    feed_type_code: string
    suggested_feed_name: string
    daily_feed: number | string
    meals_per_day: number
    meal_times: string[]
  }>
  version?: number
  update_note?: string
}) {
  return http().put<FeedStdData>(`/breeds/${id}/standards/feed/draft`, payload)
}

export function discardStandardDraft(id: number, type: 'weight' | 'feed', version?: number) {
  return http().del(`/breeds/${id}/standards/${type}/draft?version=${version ?? ''}`)
}

export function getStandardImpact(id: number, type: 'weight' | 'feed') {
  return http().get<StdImpact>(`/breeds/${id}/standards/${type}/impact`)
}

export function publishStandard(id: number, type: 'weight' | 'feed', updateNote: string, version?: number) {
  return http().post<StdVersion>(`/breeds/${id}/standards/${type}/publish`, { update_note: updateNote, version })
}

export function getStandardVersions(id: number, type: 'weight' | 'feed') {
  return http().get<StdVersion[]>(`/breeds/${id}/standards/${type}/versions`)
}

export function rollbackStandard(id: number, type: 'weight' | 'feed', payload: {
  target_version_no: number
  reason: string
  online_version_no: number
}) {
  return http().post<StdVersion>(`/breeds/${id}/standards/${type}/rollback`, payload)
}

/* ---------------- WA-D02 免疫模板 ---------------- */

export interface ImmuneTemplateGroupItem {
  template_id?: number | null
  breed_id: number
  breed_name: string
  name?: string
  item_count: number
  online_version_no?: number | null
  active_batch_count?: number
  has_draft?: boolean
  has_by_date?: boolean
}

export interface ImmuneTemplateGroup {
  category_id: number
  category_name: string
  items: ImmuneTemplateGroupItem[]
}

export interface ImmuneDraftChange {
  change_type: string
  vaccine_name?: string
  dose_round?: string
  item_name?: string
  field_name?: string | null
  before?: string | null
  after?: string | null
}

export interface ImmuneItem {
  item_id: number
  sort_no: number
  vaccine_id: number
  vaccine_name: string
  dose_round: string
  schedule_type: string
  schedule_type_text?: string
  day_age?: number | null
  plan_month?: number | null
  plan_day?: number | null
  advance_days?: number | null
  dose: number
  dose_unit: string
  dose_unit_text?: string
  vaccinate_method_code: string
  vaccinate_method_text?: string
  remark?: string
  change_type?: string
  before?: ImmuneItem | null
}

export interface ImmuneTemplate {
  template_id: number
  name: string
  breed?: { id: number; name: string }
  category?: { id: number; name: string }
  online_version_no?: number
  published_by_name?: string
  published_at?: string
  active_batch_count?: number
  breeding_farm_count?: number | null
  has_draft?: boolean
  draft_changes?: ImmuneDraftChange[]
  items?: ImmuneItem[]
  version?: number
}

export interface ImmuneItemPayload {
  vaccine_id: number
  dose_round: string
  schedule_type: string
  day_age?: number | null
  plan_month?: number | null
  plan_day?: number | null
  advance_days?: number | null
  dose: number | string
  dose_unit: string
  vaccinate_method_code: string
  remark?: string
  version: number
}

export interface TemplateVersion {
  version_id: number
  version_no: number
  status: string
  status_text?: string
  change_note?: string
  item_count?: number
  published_by_name?: string
  published_at?: string
  items?: unknown[]
}

export function getImmuneTemplates(query: { keyword?: string; category_id?: number | null; status?: string } = {}) {
  return http().get<ImmuneTemplateGroup[]>('/immune-templates', { ...query })
}

export function getImmuneTemplate(id: number) {
  return http().get<ImmuneTemplate>(`/immune-templates/${id}`)
}

export function createImmuneTemplate(payload: { breed_id: number; name?: string; copy_from_template_id?: number }) {
  return http().post<ImmuneTemplate>('/immune-templates', payload)
}

export function addImmuneItem(id: number, payload: ImmuneItemPayload) {
  return http().post<ImmuneItem>(`/immune-templates/${id}/items`, payload)
}

export function updateImmuneItem(id: number, itemId: number, payload: ImmuneItemPayload) {
  return http().put<ImmuneItem>(`/immune-templates/${id}/items/${itemId}`, payload)
}

export function deleteImmuneItem(id: number, itemId: number, version: number) {
  return http().del(`/immune-templates/${id}/items/${itemId}?version=${version}`)
}

export function orderImmuneItems(id: number, itemIds: number[], version: number) {
  return http().put(`/immune-templates/${id}/items/order`, { item_ids: itemIds, version })
}

export function publishImmuneTemplate(id: number, changeNote: string, version: number) {
  return http().post<TemplateVersion>(`/immune-templates/${id}/publish`, { change_note: changeNote, version })
}

export function discardImmuneDraft(id: number, version: number) {
  return http().post(`/immune-templates/${id}/discard`, { version })
}

export function getImmuneVersions(id: number) {
  return http().get<TemplateVersion[]>(`/immune-templates/${id}/versions`)
}

export function rollbackImmuneTemplate(id: number, targetVersionNo: number, version: number) {
  return http().post<ImmuneTemplate>(`/immune-templates/${id}/rollback`, { target_version_no: targetVersionNo, version })
}

/* ---------------- WA-D03 兽药疫苗目录 ---------------- */

export interface DrugUsage {
  species: string
  species_text?: string
  applicable?: boolean
  admin_route?: string | null
  admin_route_text?: string | null
  recommended_dose?: number | null
  dose_unit?: string | null
  dose_unit_text?: string | null
  course_days?: number | null
  withdrawal_days?: number | null
}

export interface DrugRow {
  id: number
  drug_category: string
  drug_category_text?: string
  name: string
  alias?: string
  dosage_form: string
  spec: string
  measure_unit: string
  measure_unit_text?: string
  pack_unit: string
  pack_size?: number
  vaccine_dose_unit?: string
  vaccine_dose_unit_text?: string
  default_vaccinate_method_code?: string
  default_vaccinate_method_text?: string
  concentration?: string
  disinfect_methods?: Array<{ code: string; name: string }>
  usages?: DrugUsage[]
  status: string
  status_text?: string
  banned?: boolean
  archive_farm_count?: number
  last_changed_by_name?: string
  last_changed_at?: string
  sort_no?: number
  version?: number
}

export interface DrugDetail extends DrugRow {
  disable_reason?: string
  last_change_note?: string
  active_batch_used_count?: number
  recalculated_batch_count?: number
  version?: number
}

export interface DrugPayload {
  drug_category: string
  name: string
  alias?: string
  dosage_form: string
  spec: string
  measure_unit: string
  pack_unit: string
  pack_size: number | string
  vaccine_dose_unit?: string | null
  default_vaccinate_method_code?: string | null
  concentration?: string | null
  disinfect_method_codes?: string[]
  usages?: Array<{
    species: string
    applicable: boolean
    admin_route?: string | null
    recommended_dose?: number | string | null
    dose_unit?: string | null
    course_days?: number | null
    withdrawal_days?: number | null
  }>
  change_reason?: string
  version?: number
}

export interface DrugImportError {
  row: number
  message: string
}

export interface DrugImportResult {
  total: number
  success: number
  failed: number
  errors: DrugImportError[]
  file_name?: string
}

function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

export async function downloadDrugCatalogImportTemplate(): Promise<void> {
  const response = await http().instance.get<Blob>('/drug-catalog/import-template', { responseType: 'blob' })
  saveBlob(response.data, '兽药疫苗目录导入模板.xlsx')
}

export function importDrugCatalog(file: File, onProgress?: (percent: number) => void): Promise<DrugImportResult> {
  const form = new FormData()
  form.append('file', file)
  return http().upload<DrugImportResult>('/drug-catalog/import', form, onProgress)
}

export interface BannedDrug {
  id: number
  name: string
  alias?: string
  banned_class: string
  banned_class_text?: string
  catalog_id?: number | null
  reason?: string
  listed_at: string
  listed_by_name?: string
  hit_count30d?: number
  version?: number
}

export interface DrugHit {
  hit_at: string
  hit_source: string
  hit_source_text?: string
  farm_name?: string
  user_name?: string
  keyword?: string
}

export interface RecallPreview {
  expire_date?: string
  farm_count: number
  total_qty?: number
  qty_unit?: string
  qty_unit_text?: string
  sample_farm_name?: string
  sample_farm_qty?: number
  has_stock_record?: boolean
}

export interface DrugLotRecall {
  id: number
  catalog_id: number
  catalog_name: string
  drug_category: string
  drug_category_text?: string
  lot_no: string
  expire_date?: string
  reason: string
  farm_count?: number
  total_qty?: number
  qty_unit?: string
  qty_unit_text?: string
  scrapped_farm_count?: number
  recalled_at: string
  recalled_by_name?: string
  notified_farm_count?: number
}

export interface RecallFarm {
  farm_id: number
  farm_name: string
  qty_at_recall?: number
  qty_unit?: string
  qty_unit_text?: string
  scrap_status: string
  scrap_status_text?: string
  scrapped_qty?: number
  scrapped_at?: string
}

export function getDrugCatalog(query: {
  drug_category: string
  keyword?: string
  species?: string
  dosage_form?: string
  status?: string
  page?: number
  size?: number
}) {
  return http().get<PageVO<DrugRow>>('/drug-catalog', { ...query })
}

export function getDrugDetail(id: number) {
  return http().get<DrugDetail>(`/drug-catalog/${id}`)
}

export function createDrug(payload: DrugPayload) {
  return http().post<DrugDetail>('/drug-catalog', payload)
}

export function updateDrug(id: number, payload: DrugPayload) {
  return http().put<DrugDetail>(`/drug-catalog/${id}`, payload)
}

export function setDrugStatus(id: number, status: string, reason: string, version: number) {
  return http().post<DrugDetail>(`/drug-catalog/${id}/status`, { status, reason, version })
}

export function getBannedDrugs(query: { keyword?: string; banned_class?: string; page?: number; size?: number }) {
  return http().get<PageVO<BannedDrug>>('/banned-drugs', { ...query })
}

export function getBannedImpact(catalogId: number) {
  return http().get<{
    archive_farm_count: number
    stock_totals: Array<{ qty: number; unit: string; unit_text?: string }>
  }>('/banned-drugs/impact', { catalog_id: catalogId })
}

export function addBannedDrug(payload: {
  catalog_id?: number | null
  name?: string
  alias?: string
  banned_class: string
  reason: string
}) {
  return http().post<BannedDrug>('/banned-drugs', payload)
}

export function updateBannedDrug(id: number, payload: { alias?: string; banned_class: string; version: number }) {
  return http().put<BannedDrug>(`/banned-drugs/${id}`, payload)
}

export function getBannedHits(id: number, query: { page?: number; size?: number }) {
  return http().get<PageVO<DrugHit>>(`/banned-drugs/${id}/hits`, { ...query })
}

export function getLotRecalls(query: { page?: number; size?: number }) {
  return http().get<PageVO<DrugLotRecall>>('/drug-lot-recalls', { ...query })
}

export function previewLotRecall(catalogId: number, lotNo: string) {
  return http().get<RecallPreview>('/drug-lot-recalls/preview', { catalog_id: catalogId, lot_no: lotNo })
}

export function createLotRecall(payload: { catalog_id: number; lot_no: string; reason: string }) {
  return http().post<DrugLotRecall>('/drug-lot-recalls', payload)
}

export function getRecallFarms(id: number, query: { page?: number; size?: number }) {
  return http().get<PageVO<RecallFarm>>(`/drug-lot-recalls/${id}/farms`, { ...query })
}

/* ---------------- WA-D04 巡检模板 ---------------- */

export interface InspectTemplateListItem {
  category_id: number
  category_name: string
  template_id?: number | null
  template_name?: string
  item_count: number
  online_version_no?: number | null
  has_draft?: boolean
}

export interface InspectOption {
  option_id?: number
  option_name: string
  is_abnormal: boolean
}

export interface InspectItem {
  item_id: number
  sort_no: number
  item_name: string
  check_note?: string
  enabled: boolean
  disable_reason?: string
  change_type?: string
  options: InspectOption[]
  before?: InspectItem | null
}

export interface InspectTemplate {
  template_id: number
  name: string
  category?: { id: number; name: string }
  online_version_no?: number
  published_by_name?: string
  published_at?: string
  pen_count?: number
  has_draft?: boolean
  draft_changes?: Array<{
    change_type: string
    item_name?: string
    field_name?: string | null
    before?: string | null
    after?: string | null
  }>
  items?: InspectItem[]
  preview_items?: InspectItem[]
  version?: number
}

export interface InspectItemPayload {
  item_name: string
  check_note?: string
  enabled: boolean
  disable_reason?: string
  options: Array<{ option_id?: number; option_name: string; is_abnormal: boolean }>
  version: number
}

export function getInspectTemplates() {
  return http().get<InspectTemplateListItem[]>('/inspect-templates')
}

export function getInspectTemplate(id: number) {
  return http().get<InspectTemplate>(`/inspect-templates/${id}`)
}

export function createInspectTemplate(payload: { category_id: number; name?: string }) {
  return http().post<InspectTemplate>('/inspect-templates', payload)
}

export function addInspectItem(id: number, payload: InspectItemPayload) {
  return http().post<InspectItem>(`/inspect-templates/${id}/items`, payload)
}

export function updateInspectItem(id: number, itemId: number, payload: InspectItemPayload) {
  return http().put<InspectItem>(`/inspect-templates/${id}/items/${itemId}`, payload)
}

export function deleteInspectItem(id: number, itemId: number, version: number) {
  return http().del(`/inspect-templates/${id}/items/${itemId}?version=${version}`)
}

export function orderInspectItems(id: number, itemIds: number[], version: number) {
  return http().put(`/inspect-templates/${id}/items/order`, { item_ids: itemIds, version })
}

export function publishInspectTemplate(id: number, changeNote: string, version: number) {
  return http().post<TemplateVersion>(`/inspect-templates/${id}/publish`, { change_note: changeNote, version })
}

export function discardInspectDraft(id: number, version: number) {
  return http().post(`/inspect-templates/${id}/discard`, { version })
}

export function getInspectVersions(id: number) {
  return http().get<TemplateVersion[]>(`/inspect-templates/${id}/versions`)
}

export function rollbackInspectTemplate(id: number, targetVersionNo: number, version: number) {
  return http().post<InspectTemplate>(`/inspect-templates/${id}/rollback`, { target_version_no: targetVersionNo, version })
}

/* ---------------- WA-D05 其他字典 ---------------- */

export interface DictScope {
  scope_type: string
  species?: string | null
  category_id?: number | null
  category_name?: string | null
}

export interface DictItem {
  id: number
  code: string
  name: string
  description?: string
  icon?: string
  scopes: DictScope[]
  remark_required?: boolean
  is_fallback?: boolean
  is_system?: boolean
  sort_no: number
  status: string
  status_text?: string
  disable_reason?: string
  usage_count?: number
  usage_label?: string
  diseases?: Array<{ id: number; name: string }>
  /** 仅收入 / 支出分类：自动记账来源与子类数 */
  auto_source_text?: string | null
  subcategory_count?: number | null
  version?: number
}

export interface DictType {
  dict_type: string
  name: string
  icon: string
  item_count: number
  scope_mode: string
  platform_only?: boolean
}

export interface DictItemPayload {
  name: string
  description?: string
  icon?: string
  remark_required?: boolean
  sort_no?: number
  scopes?: Array<{ scope_type: string; species?: string | null; category_id?: number | null }>
  disease_ids?: number[]
  version?: number
}

export function getDictTypes() {
  return http().get<DictType[]>('/dicts')
}

export function getDictItems(dictType: string, query: { species?: string; category_id?: number | null } = {}) {
  return http().get<DictItem[]>(`/dicts/${dictType}/items`, { ...query })
}

export function createDictItem(dictType: string, payload: DictItemPayload) {
  return http().post<DictItem>(`/dicts/${dictType}/items`, payload)
}

export function updateDictItem(dictType: string, id: number, payload: DictItemPayload) {
  return http().put<DictItem>(`/dicts/${dictType}/items/${id}`, payload)
}

export function orderDictItems(dictType: string, itemIds: number[], categoryId?: number | null) {
  return http().put(`/dicts/${dictType}/items/order`, { item_ids: itemIds, category_id: categoryId ?? null })
}

export function setDictItemStatus(dictType: string, id: number, status: string, reason: string, version: number) {
  return http().post<DictItem>(`/dicts/${dictType}/items/${id}/status`, { status, reason, version })
}
