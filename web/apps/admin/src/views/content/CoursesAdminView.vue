<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, dayjs, formatDateTime, formatDuration, formatNumber, formatPercent } from '@zmt/shared'
import {
  completeMultipartUpload,
  createContentCategory,
  createCourse,
  createCourseTopic,
  deleteCourse,
  exportCourseStats,
  getAdminFile,
  getContentCategories,
  getCourseDetail,
  getCourseStats,
  getCourseSummary,
  getCourseTopics,
  getCourses,
  getKnowledgePage,
  initMultipartUpload,
  offShelfCourse,
  onShelfCourse,
  orderContentCategories,
  retryCourseTranscode,
  retryFileTranscode,
  setCourseTopicStatus,
  updateContentCategory,
  updateCourse,
  updateCourseTopic,
  uploadAdminFile,
  uploadMultipartPartProxy,
  type ContentCategory,
  type CourseRow,
  type CourseStats,
  type CourseSummary,
  type CourseTopic
} from '@/api/content'
import { getCategoryTree, type CategoryNode } from '@/api/base'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const canHandle = computed(() => admin.hasPerm('WA-C03:HANDLE'))

const summary = ref<CourseSummary | null>(null)
const topics = ref<CourseTopic[]>([])
const categories = ref<ContentCategory[]>([])
const applicableCategories = ref<CategoryNode[]>([])
const knowledgeOptions = ref<Array<{ title: string; value: number }>>([])
const list = ref<CourseRow[]>([])
const total = ref(0)
const loading = ref(true)
const listLoading = ref(false)
const error = ref('')
const forbidden = ref(false)
const tabCounts = reactive<Record<string, number>>({ ALL: 0, ON_SHELF: 0, OFF_SHELF: 0, DRAFT: 0 })
const query = reactive({
  topic_id: null as number | null,
  category_id: null as number | null,
  status: 'ALL',
  keyword: '',
  page: 1,
  size: 10
})

const tabs = computed(() => [
  { label: '全部', value: 'ALL', count: tabCounts.ALL || 0 },
  { label: '已上架', value: 'ON_SHELF', count: tabCounts.ON_SHELF || 0 },
  { label: '已下架', value: 'OFF_SHELF', count: tabCounts.OFF_SHELF || 0 },
  { label: '草稿', value: 'DRAFT', count: tabCounts.DRAFT || 0 }
])
const headers = [
  { title: '课程', key: 'title', sortable: false, minWidth: 238 },
  { title: '转码', key: 'transcode', sortable: false, width: 126 },
  { title: '状态', key: 'status', sortable: false, width: 76 },
  { title: '播放', key: 'play_count', sortable: false, width: 56, align: 'end' as const },
  { title: '完播率', key: 'finish_rate', sortable: false, width: 64, align: 'end' as const },
  { title: '更新', key: 'updated_at', sortable: false, width: 96 },
  { title: '操作', key: 'actions', sortable: false, width: 98, align: 'end' as const }
]
const allTopics = computed(() => topics.value.filter((topic) => topic.status !== 'OFF_SHELF'))
const categoryOptions = computed(() => categories.value.filter((c) => c.status === 'ENABLED').map((c) => ({ title: c.name, value: c.id })))
const topicOptions = computed(() => [{ title: '不加入专题', value: null }, ...allTopics.value.map((t) => ({ title: t.name, value: t.id }))])
const applicableOptions = computed(() => applicableCategories.value.flatMap((category) => (category.breeds || []).map((breed) => ({ title: `${category.name} · ${breed.name}`, value: breed.id }))))

function safeUrl(url?: string | null): string {
  if (!url) return ''
  try {
    const parsed = new URL(url)
    if (parsed.hostname.endsWith('.example.com')) return ''
    return url
  } catch {
    return ''
  }
}

function statusType(status: string): string {
  if (status === 'ON_SHELF') return 'success'
  if (status === 'OFF_SHELF') return 'neutral'
  return 'neutral'
}

function transcodeType(row: CourseRow): string {
  if (!row.transcode_status) return 'NONE'
  return row.transcode_status
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await getCourseSummary()
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) forbidden.value = true
  }
}

async function loadNav(): Promise<void> {
  try {
    const [topicData, categoryData] = await Promise.all([getCourseTopics('ALL'), getContentCategories('ALL')])
    topics.value = topicData || []
    categories.value = categoryData || []
  } catch {
    topics.value = []
    categories.value = []
  }
}

async function loadFormOptions(): Promise<void> {
  try {
    const [tree, knowledge] = await Promise.all([
      getCategoryTree(),
      getKnowledgePage({ status: 'ON_SHELF', page: 1, size: 100 })
    ])
    applicableCategories.value = tree || []
    knowledgeOptions.value = (knowledge.list || []).map((item) => ({ title: item.title, value: item.id }))
  } catch {
    applicableCategories.value = []
    knowledgeOptions.value = []
  }
}

async function fetchList(): Promise<void> {
  listLoading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const data = await getCourses({
      topic_id: query.topic_id,
      category_id: query.category_id,
      status: query.status === 'ALL' ? undefined : query.status,
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      size: query.size
    })
    list.value = data.list || []
    total.value = data.total || 0
    Object.assign(tabCounts, data.tab_counts || {})
  } catch (e) {
    if (e instanceof ApiError && e.isForbidden) forbidden.value = true
    error.value = e instanceof Error ? e.message : '加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
    listLoading.value = false
  }
}

function filterTopic(id: number | null): void {
  query.topic_id = query.topic_id === id ? null : id
  query.category_id = null
  query.page = 1
  void fetchList()
}

function filterCategory(id: number | null): void {
  query.category_id = query.category_id === id ? null : id
  query.topic_id = null
  query.page = 1
  void fetchList()
}

function applyStatus(value: string): void {
  query.status = value
  query.page = 1
  void fetchList()
}

function resetFilters(): void {
  query.topic_id = null
  query.category_id = null
  query.status = 'ALL'
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

/* ---------------- 课程编辑 ---------------- */
const drawer = ref(false)
const detailLoading = ref(false)
const editing = ref<CourseRow | null>(null)
const saving = ref(false)
const formError = ref('')
const fieldErrors = reactive<Record<string, string>>({})
const coverUploading = ref(false)
const coverProgress = ref(0)
const coverError = ref('')
const videoUploading = ref(false)
const videoProgress = ref(0)
const videoError = ref('')
const videoStatus = ref('')
const videoInput = ref<HTMLInputElement | null>(null)
const form = reactive({
  title: '',
  category_id: null as number | null,
  topic_id: null as number | null,
  section_no: null as number | null,
  applicable_category_ids: [] as number[],
  knowledge_id: null as number | null,
  intro: '',
  video_file_id: null as number | null,
  video_name: '',
  video_size: 0,
  video_duration: 0,
  upload_status: '',
  transcode_status: '',
  transcode_error: '',
  cover_file_id: null as number | null,
  cover_url: '',
  cover_source: 'AUTO',
  version: 0,
  status: 'DRAFT'
})

function clearErrors(): void {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

function openCreate(): void {
  editing.value = null
  clearErrors()
  Object.assign(form, {
    title: '', category_id: categories.value.find((c) => c.status === 'ENABLED')?.id || null,
    topic_id: null, section_no: null, applicable_category_ids: [], knowledge_id: null, intro: '',
    video_file_id: null, video_name: '', video_size: 0, video_duration: 0,
    upload_status: '', transcode_status: '', transcode_error: '', cover_file_id: null,
    cover_url: '', cover_source: 'AUTO', version: 0, status: 'DRAFT'
  })
  formError.value = ''
  coverError.value = ''
  videoError.value = ''
  drawer.value = true
}

async function openEdit(row: CourseRow): Promise<void> {
  clearErrors()
  formError.value = ''
  coverError.value = ''
  videoError.value = ''
  drawer.value = true
  detailLoading.value = true
  try {
    const data = await getCourseDetail(row.id)
    editing.value = data
    form.title = data.title || ''
    form.category_id = data.category_id || null
    form.topic_id = data.topic_id || null
    form.section_no = data.section_no || null
    form.applicable_category_ids = [...(data.applicable_category_ids || [])]
    form.knowledge_id = data.knowledge_id || null
    form.intro = data.intro || ''
    form.video_file_id = data.video?.file_id || null
    form.video_name = data.video?.original_name || ''
    form.video_size = data.video?.file_size || 0
    form.video_duration = data.video?.duration_sec || data.duration_sec || 0
    form.upload_status = data.video?.upload_status || data.upload_status || ''
    form.transcode_status = data.transcode_status || data.video?.transcode_status || ''
    form.transcode_error = data.transcode_error || ''
    form.cover_file_id = data.cover_file_id || null
    form.cover_url = safeUrl(data.cover_url)
    form.cover_source = data.cover_source || 'AUTO'
    form.version = data.version || 0
    form.status = data.status
  } catch (e) {
    drawer.value = false
    ui.toast(e instanceof Error ? e.message : '课程详情加载失败', 'error')
  } finally {
    detailLoading.value = false
  }
}

function validate(): boolean {
  clearErrors()
  if (!form.title.trim()) fieldErrors.title = '请填写课程标题'
  else if (form.title.trim().length > 30) fieldErrors.title = '标题不能超过 30 字'
  if (!form.category_id) fieldErrors.category_id = '请选择分类'
  if (form.topic_id && !form.section_no) fieldErrors.section_no = '加入专题时请填写节次'
  return !Object.keys(fieldErrors).length
}

function buildPayload(action: 'DRAFT' | 'ON_SHELF') {
  return {
    submit_action: action,
    title: form.title.trim(),
    category_id: form.category_id,
    topic_id: form.topic_id,
    section_no: form.topic_id ? form.section_no : null,
    applicable_category_ids: form.applicable_category_ids,
    knowledge_id: form.knowledge_id,
    intro: form.intro.trim(),
    video_file_id: form.video_file_id,
    cover_source: form.cover_source,
    cover_file_id: form.cover_file_id,
    version: editing.value?.version
  }
}

async function submit(action: 'DRAFT' | 'ON_SHELF'): Promise<void> {
  if (!validate()) return
  if (action === 'ON_SHELF' && form.transcode_status !== 'SUCCESS') {
    formError.value = '720P 和 480P 都转码完成后才能上架'
    return
  }
  if (action === 'ON_SHELF' && !form.cover_file_id) {
    formError.value = '请选择封面'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    if (editing.value) await updateCourse(editing.value.id, buildPayload(action))
    else await createCourse(buildPayload(action))
    drawer.value = false
    ui.toast(action === 'ON_SHELF' ? '已上架，用户端立即可见' : '已存为草稿')
    await Promise.all([fetchList(), loadSummary()])
  } catch (e) {
    if (e instanceof Error) {
      formError.value = e.message
      if ('fieldErrors' in e && Array.isArray((e as { fieldErrors?: unknown[] }).fieldErrors)) {
        for (const item of (e as { fieldErrors: Array<{ field: string; message: string }> }).fieldErrors) fieldErrors[item.field] = item.message
      }
    }
  } finally {
    saving.value = false
  }
}

async function onCoverSelected(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  ;(event.target as HTMLInputElement).value = ''
  if (!file) return
  lastCoverFile.value = file
  await uploadCover(file)
}

async function uploadCover(file: File): Promise<void> {
  coverError.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    coverError.value = '只支持 JPG 或 PNG 图片，单张不超过 5MB'
    return
  }
  coverUploading.value = true
  coverProgress.value = 0
  try {
    const data = await uploadAdminFile(file, 'COURSE_COVER', (percent) => { coverProgress.value = percent })
    form.cover_file_id = data.file_id
    form.cover_url = safeUrl(data.url)
    form.cover_source = 'UPLOAD'
  } catch (e) {
    coverError.value = e instanceof Error ? e.message : '封面上传失败'
  } finally {
    coverUploading.value = false
  }
}

const coverInput = ref<HTMLInputElement | null>(null)
const lastCoverFile = ref<File | null>(null)
const lastVideoFile = ref<File | null>(null)

function retryCover(): void {
  if (lastCoverFile.value) void uploadCover(lastCoverFile.value)
}

async function retryVideo(): Promise<void> {
  if (lastVideoFile.value) await uploadVideo(lastVideoFile.value)
}

function formatBytes(size?: number | null): string {
  if (!size) return '—'
  if (size >= 1024 * 1024 * 1024) return `${(size / (1024 * 1024 * 1024)).toFixed(1)}GB`
  return `${(size / (1024 * 1024)).toFixed(0)}MB`
}

function courseStatusText(status?: string): string {
  if (status === 'ON_SHELF') return '已上架'
  if (status === 'OFF_SHELF') return '已下架'
  if (status === 'DRAFT') return '草稿'
  return status || ''
}

async function onVideoSelected(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  ;(event.target as HTMLInputElement).value = ''
  if (!file) return
  lastVideoFile.value = file
  await uploadVideo(file)
}

async function uploadVideo(file: File): Promise<void> {
  videoError.value = ''
  if (!/\.(mp4|mov)$/i.test(file.name)) {
    videoError.value = '只支持 MP4 和 MOV，不超过 2GB、30 分钟'
    return
  }
  if (file.size > 2 * 1024 * 1024 * 1024) {
    videoError.value = '只支持 MP4 和 MOV，不超过 2GB、30 分钟'
    return
  }
  videoUploading.value = true
  videoProgress.value = 0
  videoError.value = ''
  form.video_name = file.name
  form.video_size = file.size
  try {
    const init = await initMultipartUpload({
      scene: 'COURSE_VIDEO',
      original_name: file.name,
      file_size: file.size,
      mime_type: file.type || 'video/mp4'
    })
    const parts: Array<{ part_no: number; etag: string }> = []
    for (let index = 0; index < init.part_count; index += 1) {
      const start = index * init.part_size
      const chunk = file.slice(start, Math.min(start + init.part_size, file.size))
      const part = await uploadMultipartPartProxy(init.file_id, index + 1, chunk)
      parts.push({ part_no: index + 1, etag: part.etag })
      videoProgress.value = Math.round(((index + 1) / init.part_count) * 100)
    }
    const fileData = await completeMultipartUpload(init.file_id, parts)
    form.video_file_id = init.file_id
    form.video_duration = fileData.duration_sec || 0
    form.transcode_status = fileData.transcode_status || 'WAITING'
    videoStatus.value = '上传完成，已开始转码'
    void pollTranscode(init.file_id)
  } catch (e) {
    videoError.value = e instanceof Error ? e.message : '视频上传失败，请重试'
  } finally {
    videoUploading.value = false
  }
}

async function pollTranscode(fileId: number): Promise<void> {
  for (let i = 0; i < 90; i += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 4000))
    try {
      const data = await getAdminFile(fileId)
      const progress720 = data.variants?.find((v) => v.variant_code === 'HLS_720P')?.progress || 0
      const progress480 = data.variants?.find((v) => v.variant_code === 'HLS_480P')?.progress || 0
      videoProgress.value = Math.round(Math.max(progress720, progress480) * 100)
      if (data.transcode_status === 'SUCCESS') {
        form.transcode_status = 'SUCCESS'
        form.cover_url = safeUrl(data.cover_candidates?.[0]?.url) || form.cover_url
        form.cover_file_id = data.cover_candidates?.[0]?.file_id || form.cover_file_id
        videoStatus.value = '转码完成，封面已自动截取'
        return
      }
      if (data.transcode_status === 'FAILED') {
        form.transcode_status = 'FAILED'
        form.transcode_error = data.transcode_error || '转码失败'
        videoStatus.value = '转码失败'
        return
      }
      form.transcode_status = data.transcode_status || 'PROCESSING'
      videoStatus.value = progress720 > 0 ? `720P 转码中 ${Math.round(progress720 * 100)}%` : '等待转码'
    } catch {
      /* 网络暂停时下次轮询继续 */
    }
  }
  videoStatus.value = '转码仍在进行，可稍后刷新查看'
}

async function retryTranscode(): Promise<void> {
  if (editing.value) {
    try {
      await retryCourseTranscode(editing.value.id)
      form.transcode_status = 'WAITING'
      ui.toast('已重新开始转码')
      if (form.video_file_id) void pollTranscode(form.video_file_id)
    } catch (e) {
      ui.toast(e instanceof Error ? e.message : '重试失败', 'error')
    }
  } else if (form.video_file_id) {
    try {
      await retryFileTranscode(form.video_file_id)
      form.transcode_status = 'WAITING'
      ui.toast('已重新开始转码')
      void pollTranscode(form.video_file_id)
    } catch (e) {
      ui.toast(e instanceof Error ? e.message : '重试失败', 'error')
    }
  }
}

/* ---------------- 行操作 ---------------- */
const offOpen = ref(false)
const offSaving = ref(false)
const offError = ref('')
const offReason = ref('')
const offTarget = ref<CourseRow | null>(null)
const offVersion = ref(0)

async function openOffShelf(row: CourseRow): Promise<void> {
  offTarget.value = row
  offVersion.value = row.version || 0
  offReason.value = ''
  offError.value = ''
  offOpen.value = true
  try {
    const detail = await getCourseDetail(row.id)
    offTarget.value = detail
    offVersion.value = detail.version || offVersion.value
  } catch {
    /* 打开失败时由提交返回版本冲突 */
  }
}

async function submitOffShelf(): Promise<void> {
  if (!offTarget.value) return
  if (!offReason.value.trim()) {
    offError.value = '请填写下架原因'
    return
  }
  offSaving.value = true
  try {
    await offShelfCourse(offTarget.value.id, offReason.value.trim(), offVersion.value)
    offOpen.value = false
    ui.toast('已下架这个课程')
    await Promise.all([fetchList(), loadSummary()])
  } catch (e) {
    offError.value = e instanceof Error ? e.message : '下架失败'
  } finally {
    offSaving.value = false
  }
}

async function quickOnShelf(row: CourseRow): Promise<void> {
  try {
    const detail = await getCourseDetail(row.id)
    await onShelfCourse(row.id, detail.version)
    ui.toast('已上架，用户端立即可见')
    await Promise.all([fetchList(), loadSummary()])
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '上架失败', 'error')
  }
}

const deleteOpen = ref(false)
const deleteSaving = ref(false)
const deleteTarget = ref<CourseRow | null>(null)
const deleteVersion = ref(0)

function openDelete(row: CourseRow): void {
  deleteTarget.value = row
  deleteVersion.value = row.version || 0
  deleteOpen.value = true
  void getCourseDetail(row.id).then((detail) => { deleteVersion.value = detail.version || deleteVersion.value }).catch(() => undefined)
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  deleteSaving.value = true
  try {
    await deleteCourse(deleteTarget.value.id, deleteVersion.value)
    deleteOpen.value = false
    ui.toast('已删除课程草稿')
    await Promise.all([fetchList(), loadSummary()])
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '删除失败', 'error')
  } finally {
    deleteSaving.value = false
  }
}

async function quickRetry(row: CourseRow): Promise<void> {
  try {
    await retryCourseTranscode(row.id)
    ui.toast('已重新开始转码')
    await fetchList()
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '重试失败', 'error')
  }
}

/* ---------------- 数据 ---------------- */
const statsOpen = ref(false)
const statsLoading = ref(false)
const stats = ref<CourseStats | null>(null)
const statsTarget = ref<CourseRow | null>(null)

async function openStats(row: CourseRow): Promise<void> {
  statsOpen.value = true
  statsLoading.value = true
  statsTarget.value = row
  stats.value = null
  try {
    stats.value = await getCourseStats(row.id)
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '数据加载失败', 'error')
    statsOpen.value = false
  } finally {
    statsLoading.value = false
  }
}

async function exportStats(): Promise<void> {
  if (!statsTarget.value) return
  try {
    const result = await exportCourseStats(statsTarget.value.id)
    if (result.url) window.open(result.url, '_blank', 'noopener')
    ui.toast('数据已导出')
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '导出失败', 'error')
  }
}

/* ---------------- 分类与专题管理 ---------------- */
const manageOpen = ref(false)
const manageTab = ref<'topics' | 'categories'>('topics')
const manageSaving = ref(false)
const manageError = ref('')
const newTopic = reactive({ name: '', category_id: null as number | null, intro: '' })
const newCategory = reactive({ name: '' })

/* 专题排序 / 封面 / 课程编排 —— 均复用 ADM-C37/C38 现有接口 */
const topicDraggingId = ref<number | null>(null)
const topicDragOverId = ref<number | null>(null)
const topicCoverInput = ref<HTMLInputElement | null>(null)
const topicCoverTarget = ref<CourseTopic | null>(null)
const topicCoverUploadingId = ref<number | null>(null)
const topicCoverProgress = ref(0)
const topicCoverError = ref('')
const lastTopicCoverFile = ref<File | null>(null)
const arrangingTopicId = ref<number | null>(null)
const arrangingCourses = ref<Array<{ course_id: number; title?: string; section_no: number; duration_sec?: number | null; status?: string }>>([])
const arrangeLoading = ref(false)
const arrangeSaving = ref(false)
const arrangeError = ref('')
const coursePool = ref<CourseRow[]>([])
const courseToAdd = ref<number | null>(null)

const arrangeOptions = computed(() => coursePool.value
  .filter((course) => !arrangingCourses.value.some((item) => item.course_id === course.id))
  .filter((course) => !course.topic_id || course.topic_id === arrangingTopicId.value)
  .map((course) => ({
    title: course.topic_name ? `${course.title}（${course.topic_name}）` : course.title,
    value: course.id
  })))

function openManage(): void {
  manageOpen.value = true
  manageTab.value = 'topics'
  manageError.value = ''
  newCategory.name = ''
  Object.assign(newTopic, { name: '', category_id: categories.value[0]?.id || null, intro: '' })
  void loadNav()
}

async function addTopic(): Promise<void> {
  if (!newTopic.name.trim()) {
    manageError.value = '请填写专题名称'
    return
  }
  if (!newTopic.category_id) {
    manageError.value = '请选择分类'
    return
  }
  manageSaving.value = true
  manageError.value = ''
  try {
    await createCourseTopic({ name: newTopic.name.trim(), category_id: newTopic.category_id, intro: newTopic.intro.trim(), sort_no: topics.value.length + 1 })
    newTopic.name = ''
    newTopic.intro = ''
    await loadNav()
    ui.toast('已保存专题')
  } catch (e) {
    manageError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    manageSaving.value = false
  }
}

async function toggleTopic(topic: CourseTopic): Promise<void> {
  if (manageSaving.value || topicCoverUploadingId.value) return
  const status = topic.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF'
  const reason = status === 'OFF_SHELF' ? window.prompt('请填写下架专题的原因') || '' : ''
  if (status === 'OFF_SHELF' && !reason.trim()) return
  manageSaving.value = true
  manageError.value = ''
  try {
    await setCourseTopicStatus(topic.id, status, reason, topic.version)
    await loadNav()
    ui.toast(status === 'ON_SHELF' ? '已上架专题' : '已下架专题')
  } catch (e) {
    manageError.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    manageSaving.value = false
  }
}

async function saveTopic(topic: CourseTopic): Promise<void> {
  if (!topic.category_id || !topic.name.trim() || manageSaving.value || topicCoverUploadingId.value) return
  manageSaving.value = true
  manageError.value = ''
  try {
    await updateCourseTopic(topic.id, {
      name: topic.name.trim(),
      category_id: topic.category_id,
      intro: topic.intro || '',
      sort_no: topic.sort_no,
      cover_file_id: topic.cover_file_id ?? null,
      version: topic.version,
      courses: (topic.courses || []).map((course) => ({ course_id: course.course_id, section_no: course.section_no }))
    })
    await loadNav()
    ui.toast('已保存专题')
  } catch (e) {
    manageError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    manageSaving.value = false
  }
}

/* ---------------- 专题拖动排序 ---------------- */
function onTopicDragStart(topic: CourseTopic, event: DragEvent): void {
  if (manageSaving.value || topicCoverUploadingId.value) {
    event.preventDefault()
    return
  }
  topicDraggingId.value = topic.id
  event.dataTransfer?.setData('text/plain', String(topic.id))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function onTopicDragOver(topic: CourseTopic, event: DragEvent): void {
  if (manageSaving.value || !topicDraggingId.value || topicDraggingId.value === topic.id) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  topicDragOverId.value = topic.id
}

function onTopicDragEnd(): void {
  topicDraggingId.value = null
  topicDragOverId.value = null
}

async function onTopicDrop(topic: CourseTopic, event: DragEvent): Promise<void> {
  event.preventDefault()
  const sourceId = topicDraggingId.value
  onTopicDragEnd()
  if (!sourceId || sourceId === topic.id) return
  const from = topics.value.findIndex((item) => item.id === sourceId)
  const to = topics.value.findIndex((item) => item.id === topic.id)
  if (from < 0 || to < 0) return
  const ordered = [...topics.value]
  const [moved] = ordered.splice(from, 1)
  if (!moved) return
  ordered.splice(to, 0, moved)
  topics.value = ordered
  await persistTopicOrder(ordered)
}

async function moveTopic(index: number, direction: -1 | 1): Promise<void> {
  if (manageSaving.value || topicCoverUploadingId.value) return
  const target = index + direction
  if (target < 0 || target >= topics.value.length) return
  const ordered = [...topics.value]
  const [moved] = ordered.splice(index, 1)
  if (!moved) return
  ordered.splice(target, 0, moved)
  topics.value = ordered
  await persistTopicOrder(ordered)
}

/** 没有独立的专题排序接口：按新顺序逐个调用 ADM-C38 写入 sort_no，并保留原有课程关系。 */
async function persistTopicOrder(ordered: CourseTopic[]): Promise<void> {
  manageSaving.value = true
  manageError.value = ''
  try {
    const changed = ordered
      .map((topic, index) => ({ topic, sortNo: index + 1 }))
      .filter(({ topic, sortNo }) => topic.sort_no !== sortNo)
    for (const { topic, sortNo } of changed) {
      await updateCourseTopic(topic.id, {
        name: topic.name.trim(),
        category_id: topic.category_id,
        intro: topic.intro || '',
        sort_no: sortNo,
        cover_file_id: topic.cover_file_id ?? null,
        version: topic.version,
        courses: (topic.courses || []).map((course) => ({ course_id: course.course_id, section_no: course.section_no }))
      })
    }
    await loadNav()
    ui.toast('已调整专题顺序')
  } catch (e) {
    manageError.value = e instanceof Error ? e.message : '调整专题顺序失败'
    await loadNav()
  } finally {
    manageSaving.value = false
  }
}

/* ---------------- 专题封面 ---------------- */
function triggerTopicCover(topic: CourseTopic): void {
  if (manageSaving.value || topicCoverUploadingId.value) return
  topicCoverTarget.value = topic
  topicCoverError.value = ''
  topicCoverInput.value?.click()
}

async function onTopicCoverSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const topic = topicCoverTarget.value
  if (!file || !topic) return
  lastTopicCoverFile.value = file
  await uploadTopicCover(topic, file)
}

async function uploadTopicCover(topic: CourseTopic, file: File): Promise<void> {
  topicCoverError.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    topicCoverError.value = '只支持 JPG 或 PNG 图片，单张不超过 5MB'
    lastTopicCoverFile.value = null
    return
  }
  topicCoverUploadingId.value = topic.id
  topicCoverProgress.value = 0
  try {
    const data = await uploadAdminFile(file, 'TOPIC_COVER', (percent) => { topicCoverProgress.value = percent })
    if (!data.file_id) throw new Error('上传结果缺少文件编号')
    await updateCourseTopic(topic.id, {
      name: topic.name.trim(),
      category_id: topic.category_id,
      intro: topic.intro || '',
      sort_no: topic.sort_no,
      cover_file_id: data.file_id,
      version: topic.version,
      courses: (topic.courses || []).map((course) => ({ course_id: course.course_id, section_no: course.section_no }))
    })
    await loadNav()
    ui.toast('已更新专题封面')
  } catch (e) {
    topicCoverError.value = e instanceof Error ? e.message : '封面上传失败'
  } finally {
    topicCoverUploadingId.value = null
  }
}

async function retryTopicCover(): Promise<void> {
  if (lastTopicCoverFile.value && topicCoverTarget.value) {
    await uploadTopicCover(topicCoverTarget.value, lastTopicCoverFile.value)
  }
}

/* ---------------- 专题课程编排 ---------------- */
async function toggleArrange(topic: CourseTopic): Promise<void> {
  if (manageSaving.value || topicCoverUploadingId.value) return
  if (arrangingTopicId.value === topic.id) {
    arrangingTopicId.value = null
    return
  }
  arrangingTopicId.value = topic.id
  arrangingCourses.value = (topic.courses || []).map((course) => ({
    course_id: course.course_id,
    title: course.title,
    section_no: course.section_no,
    duration_sec: course.duration_sec,
    status: course.status
  }))
  courseToAdd.value = null
  arrangeError.value = ''
  arrangeLoading.value = true
  try {
    const first = await getCourses({ page: 1, size: 100 })
    let items = first.list || []
    const pages = Math.ceil((first.total || 0) / 100)
    for (let page = 2; page <= pages; page += 1) {
      const next = await getCourses({ page, size: 100 })
      items = items.concat(next.list || [])
    }
    coursePool.value = items
  } catch (e) {
    arrangeError.value = e instanceof Error ? e.message : '课程列表加载失败'
  } finally {
    arrangeLoading.value = false
  }
}

function addArrangeCourse(): void {
  const course = coursePool.value.find((item) => item.id === courseToAdd.value)
  if (!course) return
  if (arrangingCourses.value.some((item) => item.course_id === course.id)) return
  arrangingCourses.value.push({
    course_id: course.id,
    title: course.title,
    section_no: arrangingCourses.value.length + 1,
    duration_sec: course.duration_sec,
    status: course.status
  })
  courseToAdd.value = null
  arrangeError.value = ''
}

function removeArrangeCourse(index: number): void {
  arrangingCourses.value.splice(index, 1)
  arrangingCourses.value.forEach((course, order) => { course.section_no = order + 1 })
}

function moveArrangeCourse(index: number, direction: -1 | 1): void {
  const target = index + direction
  if (target < 0 || target >= arrangingCourses.value.length) return
  const [moved] = arrangingCourses.value.splice(index, 1)
  if (!moved) return
  arrangingCourses.value.splice(target, 0, moved)
  arrangingCourses.value.forEach((course, order) => { course.section_no = order + 1 })
}

async function saveArrange(): Promise<void> {
  const topic = topics.value.find((item) => item.id === arrangingTopicId.value)
  if (!topic) return
  arrangeSaving.value = true
  arrangeError.value = ''
  try {
    const updated = await updateCourseTopic(topic.id, {
      name: topic.name.trim(),
      category_id: topic.category_id,
      intro: topic.intro || '',
      sort_no: topic.sort_no,
      cover_file_id: topic.cover_file_id ?? null,
      version: topic.version,
      courses: arrangingCourses.value.map((course, index) => ({ course_id: course.course_id, section_no: index + 1 }))
    })
    const index = topics.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) topics.value[index] = updated
    arrangingTopicId.value = null
    await loadNav()
    ui.toast('已保存课程编排')
  } catch (e) {
    arrangeError.value = e instanceof Error ? e.message : '课程编排保存失败'
  } finally {
    arrangeSaving.value = false
  }
}

async function addCategory(): Promise<void> {
  if (!newCategory.name.trim()) {
    manageError.value = '请填写分类名称'
    return
  }
  manageSaving.value = true
  manageError.value = ''
  try {
    await createContentCategory({ name: newCategory.name.trim(), sort_no: categories.value.length + 1, status: 'ENABLED' })
    newCategory.name = ''
    await loadNav()
    ui.toast('已保存分类')
  } catch (e) {
    manageError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    manageSaving.value = false
  }
}

async function saveCategory(category: ContentCategory): Promise<void> {
  manageSaving.value = true
  manageError.value = ''
  try {
    await updateContentCategory(category.id, { name: category.name.trim(), status: category.status, version: category.version })
    await loadNav()
    ui.toast('已保存分类')
  } catch (e) {
    manageError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    manageSaving.value = false
  }
}

async function moveCategory(index: number, direction: -1 | 1): Promise<void> {
  const target = index + direction
  if (target < 0 || target >= categories.value.length) return
  const ordered = [...categories.value]
  const [item] = ordered.splice(index, 1)
  if (!item) return
  ordered.splice(target, 0, item)
  manageSaving.value = true
  try {
    await orderContentCategories(ordered.map((c) => c.id))
    await loadNav()
    ui.toast('已调整顺序')
  } catch (e) {
    manageError.value = e instanceof Error ? e.message : '调整顺序失败'
  } finally {
    manageSaving.value = false
  }
}

onMounted(async () => {
  loading.value = true
  void loadFormOptions()
  await Promise.all([loadSummary(), loadNav(), fetchList()])
  loading.value = false
})
</script>

<template>
  <zw-shell side="admin" active="WA-C03" :crumbs="['内容运营', '养殖课堂管理']">
    <zw-page-head title="养殖课堂管理" desc="上传教学视频后系统转码为 HLS 720P / 480P 并截取封面，转码完成才能上架。上架、下架记入操作日志；学习进度按账号记录，App 和网页版共用。">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="category" :disabled="!canHandle" @click="openManage">管理分类</v-btn>
        <v-btn variant="outlined" prepend-icon="playlist_add" :disabled="!canHandle" @click="manageTab = 'topics'; openManage()">新建专题</v-btn>
        <v-btn v-if="canHandle" color="primary" prepend-icon="upload" @click="openCreate">上传课程</v-btn>
      </template>
    </zw-page-head>

    <zw-notice v-if="forbidden" type="error" title="你没有养殖课堂的查看权限">请联系超级管理员在“管理员与权限”中开通 WA-C03 的查看权限。</zw-notice>

    <div v-if="!forbidden" class="zw-grid-4 course-stats">
      <zw-stat label="已上架课程" icon="smart_display" :value="formatNumber(summary?.on_shelf_count || 0)" unit="节" :foot="`专题 ${summary?.topic_count || 0} 个 · 分类 ${summary?.category_count || 0} 个`" />
      <zw-stat label="近 30 天播放" icon="play_circle" :value="formatNumber(summary?.plays30d || 0)" unit="次" :foot="`观看人数 ${formatNumber(summary?.viewers30d || 0)} 人`" />
      <zw-stat label="平均完播率" icon="task_alt" :value="summary?.avg_finish_rate !== undefined ? formatPercent(summary.avg_finish_rate) : '—'" foot="看完 90% 以上算学完" info="学完人数 ÷ 观看人数，按课程平均" />
      <zw-stat label="转码" icon="sync" tone="warning" :value="`${summary?.transcoding_count || 0} 个`" :foot="summary?.failed_count ? `另有 ${summary.failed_count} 个转码失败，需要重试` : '暂无转码失败'" />
    </div>

    <div v-if="!forbidden" class="course-layout">
      <zw-card flush class="course-nav-card">
        <div class="course-nav-head">课程分类</div>
        <button type="button" class="course-nav-item" :class="{ on: !query.topic_id && !query.category_id }" @click="resetFilters">
          <zw-icon name="video_library" :size="18" /><span>全部课程</span><span class="num">{{ summary?.nav?.total || tabCounts.ALL || 0 }}</span>
        </button>
        <div class="course-nav-group">
          <span>专题</span><a class="zw-link" @click="manageTab = 'topics'; openManage()">管理</a>
        </div>
        <button v-for="topic in allTopics" :key="topic.id" type="button" class="course-nav-item" :class="{ on: query.topic_id === topic.id }" @click="filterTopic(topic.id)">
          <zw-icon name="playlist_play" :size="18" /><span class="course-nav-name">{{ topic.name }}</span><span class="num">{{ topic.course_count || 0 }} 节</span>
        </button>
        <div class="course-nav-group"><span>分类</span><a class="zw-link" @click="manageTab = 'categories'; openManage()">管理</a></div>
        <button v-for="category in categories.filter((c) => c.status === 'ENABLED')" :key="category.id" type="button" class="course-nav-item" :class="{ on: query.category_id === category.id }" @click="filterCategory(category.id)">
          <zw-icon name="sell" :size="18" /><span class="course-nav-name">{{ category.name }}</span><span class="num">{{ category.course_count || 0 }}</span>
        </button>
      </zw-card>

      <zw-card flush class="course-main">
        <div class="zw-toolbar course-toolbar">
          <div class="zw-toolbar-l">
            <zw-seg :items="tabs" :model-value="query.status" @update:model-value="applyStatus(String($event))" />
          </div>
          <div class="zw-toolbar-r">
            <v-text-field v-model="query.keyword" class="zw-w-220" placeholder="搜索课程标题" prepend-inner-icon="search" density="compact" hide-details clearable @keydown.enter="fetchList()" @click:clear="fetchList()" />
          </div>
        </div>
        <v-skeleton-loader v-if="loading" type="table-row@7" />
        <zw-empty v-else-if="error" icon="cloud_off" title="课程加载失败" :desc="error" tone="error">
          <v-btn variant="outlined" @click="fetchList()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!list.length && (query.topic_id || query.category_id || query.keyword || query.status !== 'ALL')" icon="search_off" title="没有符合条件的课程" desc="换一个专题、分类或关键词试试。">
          <v-btn variant="text" color="primary" @click="resetFilters">清除筛选</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!list.length" icon="smart_display" title="还没有课程" desc="上传教学视频后，转码完成即可上架到养殖课堂。">
          <v-btn v-if="canHandle" color="primary" @click="openCreate">上传课程</v-btn>
        </zw-empty>
        <template v-else>
          <v-data-table :headers="headers" :items="list" item-key="id" hide-default-footer density="compact" :loading="listLoading" class="course-table">
            <template #item.title="{ item }">
              <div class="course-title-cell">
                <div class="course-cover">
                  <img v-if="safeUrl(item.cover_url)" :src="safeUrl(item.cover_url)" alt="" />
                  <zw-icon v-else name="movie" :size="22" />
                  <span v-if="item.duration_sec" class="course-duration num">{{ formatDuration(item.duration_sec) }}</span>
                </div>
                <div class="course-title-main">
                  <div class="course-title">{{ item.title }}</div>
                  <div class="course-sub">{{ item.category_name }}<template v-if="item.topic_name"> · {{ item.topic_name }} 第 {{ item.section_no }} 节</template><template v-else> · 不属于专题</template></div>
                </div>
              </div>
            </template>
            <template #item.transcode="{ item }">
              <div v-if="item.transcode_status === 'SUCCESS'" class="course-transcode-ok"><zw-icon name="check_circle" :size="14" /><span>720P · 480P</span></div>
              <div v-else-if="item.transcode_status === 'FAILED'" class="course-transcode-fail">
                <span><zw-icon name="error" :size="14" />转码失败</span>
                <a v-if="canHandle" class="zw-link" @click.stop="quickRetry(item as CourseRow)">重试转码</a>
              </div>
              <div v-else-if="item.transcode_status === 'PROCESSING' || item.transcode_status === 'WAITING'" class="course-transcode-progress">
                <div><span>720P</span><v-progress-linear :model-value="Math.round((item.p720?.progress || 0) * 100)" height="4" rounded color="primary" class="course-progress" /><span class="num">{{ Math.round((item.p720?.progress || 0) * 100) }}%</span></div>
                <div class="course-sub">480P {{ item.p480?.status === 'WAITING' ? '等待中' : `${Math.round((item.p480?.progress || 0) * 100)}%` }}</div>
              </div>
              <span v-else class="zw-faint">未上传视频</span>
            </template>
            <template #item.status="{ item }"><zw-tag :type="statusType(item.status)" icon="check" size="small">{{ item.status_text }}</zw-tag></template>
            <template #item.play_count="{ item }"><span class="num">{{ item.play_count !== undefined && item.play_count !== null ? formatNumber(item.play_count) : '—' }}</span></template>
            <template #item.finish_rate="{ item }"><span class="num">{{ item.finish_rate !== undefined && item.finish_rate !== null ? formatPercent(item.finish_rate) : '—' }}</span></template>
            <template #item.updated_at="{ item }">
              <div>{{ item.updated_at ? dayjs(item.updated_at).format('M月D日') : '—' }}</div>
              <div class="course-sub">{{ item.updated_by_name || '—' }}</div>
            </template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a v-if="canHandle" class="zw-link" @click="openEdit(item as CourseRow)">编辑</a>
                <a v-if="item.status !== 'DRAFT'" class="zw-link" @click="openStats(item as CourseRow)">数据</a>
                <a v-if="item.status === 'ON_SHELF' && canHandle" class="zw-link danger" @click="openOffShelf(item as CourseRow)">下架</a>
                <a v-if="item.status === 'OFF_SHELF' && canHandle" class="zw-link" @click="quickOnShelf(item as CourseRow)">上架</a>
                <a v-if="item.status === 'DRAFT' && canHandle" class="zw-link danger" @click="openDelete(item as CourseRow)">删除</a>
              </div>
            </template>
          </v-data-table>
          <div class="zw-table-foot">
            <span class="zw-muted">带封面的列表每页 10 条；转码完成才能上架</span>
            <zw-pager :total="total" :page="query.page" :size="query.size" @update:page="changePage" @update:size="changeSize" />
          </div>
        </template>
      </zw-card>
    </div>

    <!-- 上传/编辑课程 -->
    <zw-drawer v-model="drawer" :title="editing ? '编辑课程' : '上传课程'" sub="视频上传后自动转码；转码完成、封面和标题填好后才能上架" width="760">
      <v-skeleton-loader v-if="detailLoading" type="paragraph@8" />
      <div v-else class="course-form">
        <zw-form-item label="视频文件" required>
          <div class="course-video-box">
            <div class="course-video-row">
              <span class="course-video-icon"><zw-icon name="movie" :size="24" /></span>
              <div class="course-video-main">
                <div>{{ form.video_name || '还没有上传视频' }}</div>
                <div class="course-sub">
                  <template v-if="form.video_name">{{ formatBytes(form.video_size) }}<template v-if="form.video_duration"> · {{ formatDuration(form.video_duration) }}</template></template>
                  <template v-else>MP4 / MOV，不超过 2GB、30 分钟；上传后转码为 HLS 720P 和 480P 两档</template>
                </div>
              </div>
              <v-btn variant="text" size="small" prepend-icon="upload" :disabled="videoUploading" @click="videoInput?.click()">{{ form.video_file_id ? '重新上传' : '选择视频' }}</v-btn>
            </div>
            <div v-if="videoUploading" class="course-video-progress">
              <v-progress-linear :model-value="videoProgress" height="6" rounded color="primary" />
              <span class="num">{{ videoProgress }}%</span>
            </div>
            <div v-else-if="form.video_file_id" class="course-transcode-lines">
              <div><span class="course-transcode-label">720P</span><span class="course-sub">{{ form.transcode_status === 'SUCCESS' ? '转码完成' : form.transcode_status === 'FAILED' ? '转码失败' : form.transcode_status === 'PROCESSING' ? `转码中 ${videoProgress}%` : '等待转码' }}</span></div>
              <div><span class="course-transcode-label">480P</span><span class="course-sub">{{ form.transcode_status === 'SUCCESS' ? '转码完成' : '等待中' }}</span></div>
              <div v-if="videoStatus" class="course-sub">{{ videoStatus }}</div>
              <a v-if="form.transcode_status === 'FAILED' && canHandle" class="zw-link" @click="retryTranscode">重试转码</a>
            </div>
            <div v-if="videoError" class="course-form-error">{{ videoError }} <a v-if="lastVideoFile" class="zw-link" @click="retryVideo">重新上传</a></div>
          </div>
          <input ref="videoInput" type="file" accept="video/mp4,video/quicktime,.mp4,.mov" hidden @change="onVideoSelected" />
        </zw-form-item>

        <zw-form-item label="封面" required>
          <div class="course-cover-upload">
            <button type="button" class="course-cover-item" :class="{ on: form.cover_source === 'AUTO' && form.cover_file_id }" :disabled="coverUploading" @click="coverInput?.click()">
              <img v-if="form.cover_url" :src="form.cover_url" alt="封面" />
              <zw-icon v-else name="add_photo_alternate" :size="26" />
              <span>{{ form.cover_url ? '已选封面' : '上传封面' }}</span>
              <span v-if="form.cover_url" class="course-cover-check"><zw-icon name="check_circle" :size="14" />已选</span>
            </button>
            <div class="course-cover-tip">16:9；转码完成后自动截取 3 张，也可以上传 JPG / PNG，不超过 5MB</div>
            <v-progress-linear v-if="coverUploading" :model-value="coverProgress" height="4" rounded color="primary" class="course-cover-progress" />
            <div v-if="coverError" class="course-form-error">{{ coverError }} <a v-if="lastCoverFile" class="zw-link" @click="retryCover">重试</a></div>
          </div>
          <input ref="coverInput" type="file" accept="image/jpeg,image/png" hidden @change="onCoverSelected" />
        </zw-form-item>

        <div class="zw-form-2">
          <zw-form-item label="标题" required :error="fieldErrors.title">
            <v-text-field v-model="form.title" placeholder="请输入课程标题" maxlength="30" counter :error="!!fieldErrors.title" />
          </zw-form-item>
          <zw-form-item label="分类" required :error="fieldErrors.category_id">
            <v-select v-model="form.category_id" :items="categoryOptions" placeholder="请选择分类" :error="!!fieldErrors.category_id" />
          </zw-form-item>
          <zw-form-item label="所属专题" optional>
            <v-select v-model="form.topic_id" :items="topicOptions" placeholder="不加入专题" @update:model-value="!form.topic_id && (form.section_no = null)" />
          </zw-form-item>
          <zw-form-item label="节次" :required="!!form.topic_id" :error="fieldErrors.section_no" hint="加入专题时必填，与已有节次冲突时其后的课程顺延">
            <v-text-field v-model.number="form.section_no" type="number" min="1" placeholder="第几节" :disabled="!form.topic_id" :error="!!fieldErrors.section_no" />
          </zw-form-item>
        </div>
        <zw-form-item label="适用品类" optional hint="不选表示全部品类">
          <v-select v-model="form.applicable_category_ids" :items="applicableOptions" multiple chips density="compact" hide-details placeholder="全部品类" />
        </zw-form-item>
        <zw-form-item label="关联知识条目" optional hint="用户端显示“图文原文”，点“问 AI”时作为提问背景">
          <v-select v-model="form.knowledge_id" :items="knowledgeOptions" clearable placeholder="不关联" />
        </zw-form-item>
        <zw-form-item label="简介" optional>
          <v-textarea v-model="form.intro" placeholder="简单介绍课程内容" :rows="3" maxlength="200" counter />
        </zw-form-item>
        <div v-if="formError" class="course-form-error">{{ formError }}</div>
      </div>
      <template #foot>
        <span class="zw-foot-note">上架后记入操作日志，用户端立即可见</span>
        <v-btn variant="outlined" :disabled="saving" @click="submit('DRAFT')">存为草稿</v-btn>
        <v-btn color="primary" prepend-icon="publish" :loading="saving" :disabled="form.transcode_status !== 'SUCCESS' || !form.cover_file_id" @click="submit('ON_SHELF')">上架</v-btn>
      </template>
    </zw-drawer>

    <!-- 数据 -->
    <zw-drawer v-model="statsOpen" :title="statsTarget ? `课程数据 · ${statsTarget.title}` : '课程数据'" width="760">
      <v-skeleton-loader v-if="statsLoading" type="paragraph@7" />
      <template v-else-if="stats">
        <div class="zw-grid-4 course-stat-cards">
          <zw-stat label="播放" :value="formatNumber(stats.play_count)" unit="次" />
          <zw-stat label="观看人数" :value="formatNumber(stats.viewer_count)" unit="人" />
          <zw-stat label="完播率" :value="formatPercent(stats.finish_rate)" tone="primary" :foot="`学完 ${formatNumber(stats.finish_count)} 人`" />
          <zw-stat label="人均观看" :value="formatDuration(stats.avg_watch_sec)" :foot="`视频时长 ${formatDuration(statsTarget?.duration_sec)}`" />
        </div>
        <div class="zw-section-title">每日播放次数</div>
        <div v-if="stats.daily?.length" class="course-daily-bars">
          <div v-for="item in stats.daily" :key="item.date" class="course-daily-item">
            <div class="course-daily-value num">{{ item.plays }}</div>
            <div class="course-daily-bar" :style="{ height: `${Math.max(4, (item.plays / Math.max(...stats.daily.map((d) => d.plays), 1)) * 150)}px` }" />
            <div class="course-sub">{{ dayjs(item.date).format('M/D') }}</div>
          </div>
        </div>
        <div v-else class="zw-muted">还没有播放数据</div>
        <div class="zw-grid-2 course-stat-grid">
          <div>
            <div class="zw-section-title">观看进度分布</div>
            <div class="course-buckets">
              <div v-for="bucket in stats.progress_buckets || []" :key="bucket.bucket" class="course-bucket-row">
                <span class="course-bucket-label">{{ { LT25: '不到 25%', P25_50: '25%–50%', P50_75: '50%–75%', P75_90: '75%–90%', FINISHED: '90% 以上' }[bucket.bucket] || bucket.bucket }}</span>
                <v-progress-linear :model-value="stats.viewer_count ? (bucket.users / stats.viewer_count) * 100 : 0" height="8" rounded :color="bucket.bucket === 'FINISHED' ? 'primary' : 'text-3'" class="course-bucket-bar" />
                <span class="num">{{ formatNumber(bucket.users) }} 人</span>
              </div>
            </div>
            <div v-if="stats.top_exit_range" class="course-sub">退出最多的位置在 {{ formatDuration(stats.top_exit_range.start_sec) }}–{{ formatDuration(stats.top_exit_range.end_sec) }}</div>
          </div>
          <div>
            <div class="zw-section-title">播放来源与清晰度</div>
            <div class="course-kv"><span>App / 网页版</span><b>{{ formatPercent(stats.client_share?.app) }} / {{ formatPercent(stats.client_share?.web) }}</b></div>
            <div class="course-kv"><span>720P / 480P</span><b>{{ formatPercent(stats.quality_share?.p720) }} / {{ formatPercent(stats.quality_share?.p480) }}</b></div>
            <div class="course-kv"><span>移动网络播放</span><b>{{ formatNumber(stats.mobile_play_count) }} 次</b></div>
            <div class="course-kv"><span>点“问 AI”</span><b>{{ formatNumber(stats.ask_ai_count) }} 次</b></div>
            <div class="course-kv"><span>点“图文原文”</span><b>{{ formatNumber(stats.knowledge_click_count) }} 次</b></div>
          </div>
        </div>
        <template v-if="stats.topic">
          <div class="zw-section-title">所在专题</div>
          <zw-desc :cols="3" bordered>
            <zw-desc-item label="学习人数">{{ formatNumber(stats.topic.learners) }} 人</zw-desc-item>
            <zw-desc-item label="全部学完">{{ formatNumber(stats.topic.all_finished) }} 人</zw-desc-item>
            <zw-desc-item label="平均已学">{{ stats.topic.avg_finished }} / {{ stats.topic.total_sections }} 节</zw-desc-item>
          </zw-desc>
        </template>
      </template>
      <template #foot>
        <span class="zw-foot-note">学完判定：看到 90% 以上</span>
        <v-btn variant="outlined" @click="statsOpen = false">关闭</v-btn>
        <v-btn color="primary" variant="outlined" prepend-icon="download" :disabled="!canHandle" @click="exportStats">导出数据</v-btn>
      </template>
    </zw-drawer>

    <!-- 下架 -->
    <zw-dialog v-model="offOpen" title="下架这个课程？" width="480" icon="unpublished" tone="error">
      <p class="course-dialog-strong">{{ offTarget?.title }}</p>
      <p class="course-dialog-text">下架后，用户在养殖课堂、专题和搜索中看不到这个课程，正在播放的用户看到“这个视频已下架”；学习进度保留，重新上架后恢复。</p>
      <zw-form-item label="下架原因" required hint="记入操作日志，用户端不显示">
        <v-textarea v-model="offReason" placeholder="请填写下架原因" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <div v-if="offError" class="course-form-error">{{ offError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="offSaving" @click="offOpen = false">取消</v-btn>
        <v-btn color="error" :loading="offSaving" @click="submitOffShelf">下架</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="deleteOpen" title="删除这个课程草稿？" width="440" icon="delete" tone="error">
      <p class="course-dialog-strong">{{ deleteTarget?.title }}</p>
      <p class="course-dialog-text">删除后不能恢复，原片与封面绑定会释放。</p>
      <template #actions>
        <v-btn variant="outlined" :disabled="deleteSaving" @click="deleteOpen = false">取消</v-btn>
        <v-btn color="error" :loading="deleteSaving" @click="confirmDelete">删除</v-btn>
      </template>
    </zw-dialog>

    <!-- 分类与专题管理 -->
    <zw-drawer v-model="manageOpen" title="分类与专题管理" sub="专题可拖动排序、上传封面并编排课程；分类排序会同步到用户端筛选项" width="760">
      <v-tabs v-model="manageTab" class="zw-tabs-line manage-tabs">
        <v-tab value="topics">专题</v-tab>
        <v-tab value="categories">分类</v-tab>
      </v-tabs>
      <template v-if="manageTab === 'topics'">
        <div class="manage-add">
          <v-text-field v-model="newTopic.name" placeholder="专题名称，最多 20 字" maxlength="20" density="compact" hide-details />
          <v-select v-model="newTopic.category_id" :items="categoryOptions" placeholder="分类" density="compact" hide-details />
          <v-btn color="primary" :loading="manageSaving" @click="addTopic">新建专题</v-btn>
        </div>
        <div v-if="!topics.length" class="zw-empty manage-empty">还没有专题</div>
        <div v-for="(topic, index) in topics" :key="topic.id" class="manage-item">
          <div
            class="manage-row"
            :class="{ 'manage-row--dragging': topicDraggingId === topic.id, 'manage-row--over': topicDragOverId === topic.id }"
            @dragover="onTopicDragOver(topic, $event)"
            @drop="onTopicDrop(topic, $event)"
          >
            <span
              class="manage-drag"
              draggable="true"
              title="拖动调整专题顺序"
              @dragstart="onTopicDragStart(topic, $event)"
              @dragend="onTopicDragEnd"
            ><zw-icon name="drag_indicator" :size="18" /></span>
            <button
              type="button"
              class="manage-cover"
              :disabled="manageSaving || topicCoverUploadingId === topic.id"
              :title="topic.cover_url ? '更换专题封面' : '上传专题封面'"
              @click="triggerTopicCover(topic)"
            >
              <img v-if="safeUrl(topic.cover_url)" :src="safeUrl(topic.cover_url)" alt="" />
              <zw-icon v-else name="add_photo_alternate" :size="20" />
              <span v-if="topicCoverUploadingId === topic.id" class="manage-cover-mask">
                <v-progress-circular :model-value="topicCoverProgress" :size="26" :width="3" color="primary" />
              </span>
            </button>
            <div class="manage-row-main">
              <v-text-field v-model="topic.name" density="compact" hide-details maxlength="20" />
              <v-select v-model="topic.category_id" :items="categoryOptions" density="compact" hide-details />
              <span class="zw-tag" :class="topic.status === 'ON_SHELF' ? 'zw-tag--success' : 'zw-tag--neutral'">{{ topic.status_text || topic.status }}</span>
            </div>
            <div class="manage-row-sub">
              <span class="zw-muted">排序 {{ index + 1 }} · {{ topic.course_count || 0 }} 节 · {{ topic.duration_text || '' }}</span>
              <div class="zw-cell-actions">
                <a class="zw-link" :class="{ 'is-disabled': index === 0 || manageSaving }" @click="moveTopic(index, -1)">上移</a>
                <a class="zw-link" :class="{ 'is-disabled': index === topics.length - 1 || manageSaving }" @click="moveTopic(index, 1)">下移</a>
                <a class="zw-link" :class="{ 'is-disabled': manageSaving }" @click="toggleArrange(topic)">{{ arrangingTopicId === topic.id ? '收起编排' : '编排课程' }}</a>
                <a class="zw-link" :class="{ 'is-disabled': manageSaving }" @click="saveTopic(topic)">保存</a>
                <a class="zw-link" :class="{ danger: topic.status === 'ON_SHELF', 'is-disabled': manageSaving }" @click="toggleTopic(topic)">{{ topic.status === 'ON_SHELF' ? '下架' : '上架' }}</a>
              </div>
            </div>
          </div>
          <div v-if="topicCoverUploadingId === topic.id" class="manage-cover-progress">
            <v-progress-linear :model-value="topicCoverProgress" height="4" rounded color="primary" />
            <span class="num">{{ topicCoverProgress }}%</span>
          </div>
          <div v-if="topicCoverError && topicCoverTarget?.id === topic.id" class="course-form-error manage-cover-error">
            {{ topicCoverError }} <a v-if="lastTopicCoverFile" class="zw-link" @click="retryTopicCover">重试</a>
          </div>

          <div v-if="arrangingTopicId === topic.id" class="manage-arrange">
            <div class="manage-arrange-head">
              <div><b>课程编排</b><span class="zw-muted">调整后保存，节次按 1–n 自动重排</span></div>
              <v-btn variant="text" size="small" :disabled="arrangeSaving" @click="arrangingTopicId = null">收起</v-btn>
            </div>
            <div v-if="arrangeLoading" class="manage-arrange-loading">
              <v-progress-circular indeterminate size="18" /><span>课程列表加载中</span>
            </div>
            <template v-else>
              <div v-if="!arrangingCourses.length" class="zw-empty manage-arrange-empty">还没有课程，从下面添加</div>
              <div v-for="(course, order) in arrangingCourses" :key="course.course_id" class="manage-arrange-row">
                <span class="manage-arrange-no num">{{ order + 1 }}</span>
                <div class="manage-arrange-main">
                  <div class="manage-arrange-title">{{ course.title || `课程 #${course.course_id}` }}</div>
                  <div class="manage-arrange-sub">
                    <span v-if="course.duration_sec">{{ formatDuration(course.duration_sec) }}</span>
                    <span v-if="course.status">{{ courseStatusText(course.status) }}</span>
                  </div>
                </div>
                <div class="zw-cell-actions">
                  <a class="zw-link" :class="{ 'is-disabled': order === 0 || arrangeSaving }" @click="moveArrangeCourse(order, -1)">上移</a>
                  <a class="zw-link" :class="{ 'is-disabled': order === arrangingCourses.length - 1 || arrangeSaving }" @click="moveArrangeCourse(order, 1)">下移</a>
                  <a class="zw-link danger" :class="{ 'is-disabled': arrangeSaving }" @click="removeArrangeCourse(order)">移除</a>
                </div>
              </div>
              <div class="manage-arrange-add">
                <v-select v-model="courseToAdd" :items="arrangeOptions" placeholder="选择要添加的课程" density="compact" hide-details class="manage-arrange-select" />
                <v-btn variant="outlined" prepend-icon="add" :disabled="!courseToAdd || arrangeSaving" @click="addArrangeCourse">添加课程</v-btn>
              </div>
              <div v-if="arrangeError" class="course-form-error">{{ arrangeError }}</div>
              <div class="manage-arrange-foot">
                <span class="zw-muted">从其他专题移入会被服务端拒绝，请先在原专题移除</span>
                <v-btn variant="text" :disabled="arrangeSaving" @click="arrangingTopicId = null">取消</v-btn>
                <v-btn color="primary" :loading="arrangeSaving" :disabled="arrangeSaving" @click="saveArrange">保存编排</v-btn>
              </div>
            </template>
          </div>
        </div>
        <input ref="topicCoverInput" type="file" accept="image/jpeg,image/png" hidden @change="onTopicCoverSelected" />
      </template>
      <template v-else>
        <div class="manage-add">
          <v-text-field v-model="newCategory.name" placeholder="分类名称，最多 10 字" maxlength="10" density="compact" hide-details />
          <v-btn color="primary" :loading="manageSaving" @click="addCategory">新增分类</v-btn>
        </div>
        <div v-for="(category, index) in categories" :key="category.id" class="manage-row">
          <div class="manage-row-main">
            <v-text-field v-model="category.name" density="compact" hide-details maxlength="10" />
            <v-select v-model="category.status" :items="[{ title: '启用', value: 'ENABLED' }, { title: '停用', value: 'DISABLED' }]" density="compact" hide-details />
            <span class="zw-muted">课程 {{ category.course_count || 0 }} · 知识 {{ category.knowledge_count || 0 }}</span>
          </div>
          <div class="zw-cell-actions">
            <a class="zw-link" @click="moveCategory(index, -1)">上移</a>
            <a class="zw-link" @click="moveCategory(index, 1)">下移</a>
            <a class="zw-link" @click="saveCategory(category)">保存</a>
          </div>
        </div>
      </template>
      <div v-if="manageError" class="course-form-error">{{ manageError }}</div>
      <template #foot><v-btn variant="outlined" @click="manageOpen = false">关闭</v-btn></template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.course-stats { margin-bottom: 16px; }
.course-layout { display: grid; grid-template-columns: 232px minmax(0, 1fr); gap: 16px; align-items: start; }
.course-main { min-width: 0; overflow: hidden; }
.course-toolbar { flex-wrap: wrap; }
.course-toolbar .zw-toolbar-r { flex: 0 0 auto; }
.course-toolbar .zw-toolbar-r :deep(.v-input) { flex: 0 0 220px; width: 220px; }
.course-toolbar .zw-toolbar-l :deep(.zw-seg) { flex-shrink: 0; }
.course-nav-card { padding: 6px 0 4px; }
.course-nav-head { padding: 10px 14px; font-size: 13px; font-weight: 600; color: var(--text-2); }
.course-nav-item { width: 100%; height: 38px; padding: 0 14px; display: flex; align-items: center; gap: 8px; border: 0; background: transparent; font: inherit; font-size: 13px; text-align: left; cursor: pointer; color: var(--text-1); }
.course-nav-item:hover { background: var(--fill-1); }
.course-nav-item.on { background: var(--primary-subtle); color: var(--primary); font-weight: 500; }
.course-nav-item .num { margin-left: auto; color: var(--text-3); font-size: 12px; font-weight: 400; }
.course-nav-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.course-nav-group { display: flex; align-items: center; justify-content: space-between; padding: 10px 14px 4px; font-size: 12px; color: var(--text-3); }
.course-nav-group a { font-size: 12px; }
.course-toolbar { padding: 16px; }
.course-title-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.course-cover { width: 80px; height: 45px; border-radius: 2px; background: var(--fill-2); overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; color: var(--text-3); position: relative; }
.course-cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.course-duration { position: absolute; right: 2px; bottom: 2px; padding: 0 3px; border-radius: 2px; background: rgba(0, 0, 0, 0.65); color: #fff; font-size: 10px; line-height: 14px; }
.course-title-main { min-width: 0; }
.course-title { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 250px; }
.course-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.course-table :deep(tbody tr) { height: 60px; }
.course-transcode-ok { display: flex; align-items: center; gap: 4px; color: var(--success); font-size: 12px; }
.course-transcode-fail { display: flex; flex-direction: column; gap: 2px; color: var(--error); font-size: 12px; }
.course-transcode-fail span { display: flex; align-items: center; gap: 3px; }
.course-transcode-progress > div { display: flex; align-items: center; gap: 4px; font-size: 12px; }
.course-progress { width: 46px; }
.course-form { display: flex; flex-direction: column; gap: 18px; }
.course-video-box { border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--fill-1); padding: 12px 16px; }
.course-video-row { display: flex; align-items: center; gap: 12px; }
.course-video-icon { width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-sm); background: var(--primary-subtle); color: var(--primary); }
.course-video-main { flex: 1; min-width: 0; }
.course-video-main > div:first-child { font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.course-video-progress { display: flex; align-items: center; gap: 10px; margin-top: 10px; font-size: 12px; color: var(--text-2); }
.course-transcode-lines { display: flex; gap: 18px; flex-wrap: wrap; margin-top: 10px; }
.course-transcode-label { font-size: 12px; color: var(--text-3); margin-right: 6px; }
.course-cover-upload { display: flex; flex-direction: column; gap: 6px; }
.course-cover-item { width: 128px; height: 72px; border: 1px dashed var(--line-2); border-radius: var(--radius-sm); background: var(--fill-1); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; font-size: 11px; color: var(--text-3); cursor: pointer; overflow: hidden; position: relative; padding: 0; }
.course-cover-item.on { border: 2px solid var(--primary); }
.course-cover-item img { width: 100%; height: 100%; object-fit: cover; }
.course-cover-check { position: absolute; right: 2px; bottom: 2px; display: flex; align-items: center; gap: 2px; padding: 0 3px; background: var(--primary); color: #fff; font-size: 10px; border-radius: 2px; }
.course-cover-tip { font-size: 12px; color: var(--text-3); }
.course-cover-progress { width: 128px; }
.course-form-error { font-size: 12px; color: var(--error); margin-top: 6px; }
.course-stat-cards { margin-bottom: 18px; }
.course-stat-grid { margin-top: 16px; align-items: start; }
.course-daily-bars { display: flex; align-items: flex-end; gap: 12px; height: 200px; padding: 10px 6px 0; border-bottom: 1px solid var(--line-1); }
.course-daily-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.course-daily-value { font-size: 11px; color: var(--text-2); }
.course-daily-bar { width: 100%; max-width: 44px; background: var(--primary); border-radius: 2px 2px 0 0; }
.course-buckets { display: flex; flex-direction: column; gap: 12px; margin-bottom: 8px; }
.course-bucket-row { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.course-bucket-label { width: 76px; color: var(--text-2); flex-shrink: 0; }
.course-bucket-bar { flex: 1; }
.course-kv { display: flex; justify-content: space-between; gap: 12px; padding: 9px 0; border-bottom: 1px dashed var(--line-1); font-size: 13px; }
.course-kv span { color: var(--text-2); }
.course-dialog-strong { font-size: 15px; font-weight: 600; margin: 0 0 6px; }
.course-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); margin-bottom: 14px; }
.manage-tabs { margin-bottom: 16px; }
.manage-add { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.manage-add .v-btn { flex-shrink: 0; }
.manage-item { border-bottom: 1px solid var(--line-1); }
.manage-row { display: grid; grid-template-columns: 22px 64px minmax(0, 1fr); grid-template-areas: 'drag cover main' '. . sub'; align-items: center; gap: 6px 8px; padding: 10px 0; }
.manage-row-main { grid-area: main; display: grid; grid-template-columns: minmax(0, 1fr) 116px auto; align-items: center; gap: 8px; min-width: 0; }
.manage-row-sub { grid-column: 2 / -1; display: flex; align-items: center; gap: 8px; }
.manage-row-sub .zw-muted { margin-right: auto; font-size: 12px; }
.manage-item > .manage-row { border-bottom: 0; }
.manage-row--dragging { opacity: 0.5; }
.manage-row--over { background: var(--primary-subtle); box-shadow: inset 0 2px 0 var(--primary); }
.manage-drag { grid-area: drag; display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 32px; color: var(--text-3); cursor: grab; border-radius: 3px; }
.manage-drag:hover { background: var(--fill-1); color: var(--text-1); }
.manage-drag:active { cursor: grabbing; }
.manage-cover { grid-area: cover; position: relative; width: 64px; height: 36px; flex-shrink: 0; padding: 0; border: 1px dashed var(--line-2); border-radius: var(--radius-sm); background: var(--fill-1); color: var(--text-3); display: inline-flex; align-items: center; justify-content: center; overflow: hidden; cursor: pointer; }
.manage-cover:hover { border-color: var(--primary); color: var(--primary); }
.manage-cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.manage-cover-mask { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(255, 255, 255, 0.72); }
.manage-cover-progress { display: flex; align-items: center; gap: 8px; padding: 0 0 6px 30px; font-size: 12px; color: var(--text-3); }
.manage-cover-progress :deep(.v-progress-linear) { max-width: 160px; }
.manage-cover-error { padding: 0 0 6px 30px; margin-top: 0; }
.is-disabled { color: var(--text-3) !important; pointer-events: none; opacity: 0.45; }
.manage-arrange { margin: 0 0 12px 30px; padding: 12px 14px; border: 1px solid var(--line-2); border-radius: var(--radius-sm); background: var(--fill-1); }
.manage-arrange-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; font-size: 13px; }
.manage-arrange-head b { margin-right: 8px; }
.manage-arrange-loading { display: flex; align-items: center; gap: 8px; padding: 12px 0; font-size: 12px; color: var(--text-3); }
.manage-arrange-empty { padding: 12px 0; font-size: 12px; }
.manage-arrange-row { display: flex; align-items: center; gap: 10px; padding: 7px 0; border-bottom: 1px dashed var(--line-1); }
.manage-arrange-no { width: 20px; height: 20px; border-radius: 50%; background: var(--primary-subtle); color: var(--primary); font-size: 11px; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; }
.manage-arrange-main { flex: 1; min-width: 0; }
.manage-arrange-title { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manage-arrange-sub { display: flex; gap: 10px; font-size: 12px; color: var(--text-3); }
.manage-arrange-add { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
.manage-arrange-select { max-width: 380px; }
.manage-arrange-foot { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
.manage-arrange-foot .zw-muted { margin-right: auto; font-size: 12px; }
.manage-empty { padding: 24px 0; }
</style>
