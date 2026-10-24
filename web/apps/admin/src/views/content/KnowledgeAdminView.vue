<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ApiError, dayjs, formatDateTime, formatNumber, formatPercent, today } from '@zmt/shared'
import {
  closeKnowledgeTask,
  createKnowledge,
  createKnowledgeTask,
  deleteKnowledge,
  getContentCategories,
  getCourseSummary,
  getCourses,
  getKnowledgeEdit,
  getKnowledgePage,
  getKnowledgeTasks,
  getKnowledgeVersion,
  getKnowledgeVersions,
  offShelfKnowledge,
  onShelfKnowledge,
  publishKnowledge,
  rollbackKnowledge,
  saveKnowledgeDraft,
  uploadAdminFile,
  type ContentCategory,
  type CourseRow,
  type CourseSummary,
  type KnowledgeEdit,
  type KnowledgeRow,
  type KnowledgeTask,
  type KnowledgeVersion,
  type KnowledgeVersionItem
} from '@/api/content'
import { getAdmins } from '@/api/system'
import { getCategoryTree, type CategoryNode } from '@/api/base'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const admin = useAdminStore()
const canHandle = computed(() => admin.hasPerm('WA-C04:HANDLE'))

const tab = ref<'items' | 'tasks'>('items')
const list = ref<KnowledgeRow[]>([])
const total = ref(0)
const loading = ref(true)
const listLoading = ref(false)
const error = ref('')
const forbidden = ref(false)
const taskPending = ref(0)
const tabCounts = reactive<Record<string, number>>({ ALL: 0, ON_SHELF: 0, DRAFT: 0, OFF_SHELF: 0 })
const categories = ref<ContentCategory[]>([])
const applicableCategories = ref<CategoryNode[]>([])
const courses = ref<CourseRow[]>([])
const query = reactive({
  status: 'ALL',
  category_id: null as number | null,
  source_type: '',
  keyword: '',
  page: 1,
  size: 20
})
const tabs = computed(() => [
  { label: '全部', value: 'ALL', count: tabCounts.ALL || 0 },
  { label: '已上架', value: 'ON_SHELF', count: tabCounts.ON_SHELF || 0 },
  { label: '草稿', value: 'DRAFT', count: tabCounts.DRAFT || 0 },
  { label: '已下架', value: 'OFF_SHELF', count: tabCounts.OFF_SHELF || 0 }
])
const sourceOptions = [
  { title: '全部来源', value: '' },
  { title: '平台知识库', value: 'PLATFORM' },
  { title: '常见病症', value: 'DISEASE' },
  { title: '兽药目录', value: 'DRUG_CATALOG' }
]
const categoryOptions = computed(() => categories.value.map((c) => ({ title: c.name, value: c.id })))
const headers = [
  { title: '标题', key: 'title', sortable: false, minWidth: 320 },
  { title: '分类', key: 'category_name', sortable: false, width: 92 },
  { title: '来源', key: 'source_type_text', sortable: false, width: 100 },
  { title: '版本', key: 'version_label', sortable: false, width: 92 },
  { title: '状态', key: 'status', sortable: false, width: 88 },
  { title: '被 AI 引用', key: 'cite_count30d', sortable: false, width: 106, align: 'end' as const },
  { title: '有用率', key: 'useful_rate_30d', sortable: false, width: 76, align: 'end' as const },
  { title: '更新', key: 'updated_at', sortable: false, width: 136 },
  { title: '操作', key: 'actions', sortable: false, width: 140, align: 'end' as const }
]

function statusTone(status: string): string {
  if (status === 'ON_SHELF') return 'success'
  return 'neutral'
}

async function loadOptions(): Promise<void> {
  try {
    const [categoryData, tree, courseData] = await Promise.all([
      getContentCategories('ALL'),
      getCategoryTree(),
      getCourses({ status: 'ON_SHELF', page: 1, size: 100 })
    ])
    categories.value = categoryData || []
    applicableCategories.value = tree || []
    courses.value = courseData.list || []
  } catch {
    categories.value = []
  }
}

async function fetchList(): Promise<void> {
  listLoading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const data = await getKnowledgePage({
      status: query.status === 'ALL' ? undefined : query.status,
      category_id: query.category_id,
      source_type: query.source_type || undefined,
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      size: query.size
    })
    list.value = data.list || []
    total.value = data.total || 0
    Object.assign(tabCounts, data.tab_counts || {})
    taskPending.value = data.task_pending_count || 0
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

function applyStatus(value: string): void {
  query.status = value
  query.page = 1
  void fetchList()
}

function resetFilters(): void {
  query.status = 'ALL'
  query.category_id = null
  query.source_type = ''
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

/* ---------------- 正文 HTML 与编辑区 ---------------- */
interface EditorImage {
  fileId: number | null
  url: string
  alt: string
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function textToHtml(text?: string): string {
  if (!text) return ''
  return text
    .split(/\n{2,}/)
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => `<p>${escapeHtml(part).replace(/\n/g, '<br>')}</p>`)
    .join('')
}

function contentText(html?: string): string {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const parts: string[] = []
  const walk = (node: Element): void => {
    if (node.matches('div[data-type="video"]')) {
      parts.push('【教学视频】')
      return
    }
    if (node.matches('img')) {
      parts.push('【图片】')
      return
    }
    if (node.matches('p, li, h1, h2, h3, h4, h5, h6, blockquote')) {
      const text = (node.textContent || '').trim()
      if (text) parts.push(text)
      return
    }
    for (const child of Array.from(node.children)) walk(child)
  }
  for (const child of Array.from(doc.body.children)) walk(child)
  if (!parts.length) {
    const text = (doc.body.textContent || '').trim()
    if (text) parts.push(text)
  }
  return parts.join('\n\n')
}

function courseTitle(courseId: number): string {
  return courses.value.find((course) => course.id === courseId)?.title || `课程 #${courseId}`
}

/* ---------------- 编辑 ---------------- */
const drawer = ref(false)
const detailLoading = ref(false)
const editing = ref<KnowledgeEdit | null>(null)
const saving = ref(false)
const savingAction = ref<'DRAFT' | 'PUBLISH' | ''>('')
const formError = ref('')
const fieldErrors = reactive<Record<string, string>>({})
const form = reactive({
  title: '',
  category_id: null as number | null,
  display_type: 'ARTICLE',
  is_major_epidemic: false,
  applicable_category_ids: [] as number[],
  keywords: [] as string[],
  content: '',
  change_note: '',
  source_task_id: null as number | null,
  version: 0
})

const editorEl = ref<HTMLDivElement | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const imageUploading = ref(false)
const imageProgress = ref(0)
const imageError = ref('')
const lastImageFile = ref<File | null>(null)
const videoToInsert = ref<number | null>(null)

const contentLength = computed(() => {
  if (!form.content) return 0
  const doc = new DOMParser().parseFromString(form.content, 'text/html')
  doc.querySelectorAll('img, div[data-type="video"]').forEach((node) => node.remove())
  return (doc.body.textContent || '').replace(/\s+/g, ' ').trim().length
})

const contentImages = computed<EditorImage[]>(() => {
  if (!form.content) return []
  const doc = new DOMParser().parseFromString(form.content, 'text/html')
  return Array.from(doc.body.querySelectorAll('img'))
    .map((img) => ({
      fileId: Number(img.getAttribute('data-file-id') || 0) || null,
      url: img.getAttribute('src') || '',
      alt: img.getAttribute('alt') || ''
    }))
    .filter((image) => !!image.url || image.fileId !== null)
})

const previewTagWhitelist = new Set(['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'div', 'blockquote', 'ul', 'ol', 'li', 'strong', 'b', 'em', 'i', 'br', 'img', 'span'])

const previewHtml = computed(() => {
  if (!form.content.trim()) return ''
  const doc = new DOMParser().parseFromString(form.content, 'text/html')
  doc.querySelectorAll('script, style, iframe, object, embed, link, meta').forEach((node) => node.remove())
  const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT)
  const elements: Element[] = []
  while (walker.nextNode()) elements.push(walker.currentNode as Element)
  for (const element of elements) {
    if (!element.parentNode) continue
    const tag = element.tagName.toLowerCase()
    if (!previewTagWhitelist.has(tag)) {
      element.replaceWith(...Array.from(element.childNodes))
      continue
    }
    for (const attr of Array.from(element.attributes)) {
      const name = attr.name.toLowerCase()
      const allowed = tag === 'img' ? ['src', 'alt', 'data-file-id'] : tag === 'div' ? ['data-type', 'data-course-id'] : []
      if (!allowed.includes(name)) element.removeAttribute(attr.name)
    }
    if (tag === 'img') {
      const src = element.getAttribute('src') || ''
      if (!/^(https?:)?\/\//i.test(src) && !src.startsWith('/')) {
        element.remove()
        continue
      }
      element.setAttribute('class', 'knowledge-preview-img')
    }
    if (tag === 'div' && element.getAttribute('data-type') === 'video') {
      const courseId = Number(element.getAttribute('data-course-id') || 0)
      element.setAttribute('class', 'knowledge-preview-video')
      element.textContent = `课堂视频 · ${courseTitle(courseId)}`
    }
  }
  return doc.body.innerHTML
})

async function setEditorContent(html: string): Promise<void> {
  await nextTick()
  if (!editorEl.value) return
  editorEl.value.innerHTML = html.trim() ? html : '<p><br></p>'
  decorateEditor()
  form.content = editorEl.value.innerHTML
}

/** 服务端按块解析正文；把顶层裸文本包成段落，避免保存时丢字。 */
function normalizeContentHtml(html: string): string {
  if (!html.trim()) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  for (const node of Array.from(doc.body.childNodes)) {
    if (node.nodeType !== Node.TEXT_NODE) continue
    const text = node.textContent || ''
    if (!text.trim()) {
      node.remove()
      continue
    }
    const paragraph = doc.createElement('p')
    paragraph.textContent = text
    node.replaceWith(paragraph)
  }
  return doc.body.innerHTML
}

function decorateEditor(): void {
  const root = editorEl.value
  if (!root) return
  for (const img of Array.from(root.querySelectorAll('img'))) {
    img.classList.add('knowledge-editor-img')
    if (!img.getAttribute('alt')) img.setAttribute('alt', '知识正文图片')
  }
  for (const block of Array.from(root.querySelectorAll('div[data-type="video"]'))) {
    block.classList.add('knowledge-video-block')
    block.setAttribute('contenteditable', 'false')
    block.textContent = `课堂视频 · ${courseTitle(Number(block.getAttribute('data-course-id') || 0))}`
  }
}

function syncEditor(): void {
  if (editorEl.value) form.content = editorEl.value.innerHTML
}

function onEditorInput(): void {
  syncEditor()
  if (fieldErrors.content) delete fieldErrors.content
}

function onEditorPaste(event: ClipboardEvent): void {
  event.preventDefault()
  const text = event.clipboardData?.getData('text/plain') || ''
  if (!text) return
  document.execCommand('insertHTML', false, escapeHtml(text).replace(/\r\n/g, '\n').replace(/\n/g, '<br>'))
  syncEditor()
}

function insertTopLevelNode(node: HTMLElement): void {
  const root = editorEl.value
  if (!root) return
  root.focus()
  const selection = window.getSelection()
  let anchor: Node | null = null
  if (selection && selection.rangeCount) {
    let current: Node | null = selection.getRangeAt(0).startContainer
    while (current && current !== root) {
      if (current.parentNode === root) {
        anchor = current
        break
      }
      current = current.parentNode
    }
  }
  if (anchor) anchor.parentNode?.insertBefore(node, anchor.nextSibling)
  else root.appendChild(node)
  const paragraph = document.createElement('p')
  paragraph.innerHTML = '<br>'
  node.after(paragraph)
  const range = document.createRange()
  range.setStart(paragraph, 0)
  range.collapse(true)
  selection?.removeAllRanges()
  selection?.addRange(range)
  syncEditor()
  root.focus()
}

function createImageElement(image: EditorImage): HTMLImageElement {
  const img = document.createElement('img')
  img.setAttribute('src', image.url)
  if (image.fileId) img.setAttribute('data-file-id', String(image.fileId))
  img.setAttribute('alt', image.alt || '知识正文图片')
  img.className = 'knowledge-editor-img'
  return img
}

function createVideoElement(courseId: number, title: string): HTMLElement {
  const block = document.createElement('div')
  block.setAttribute('data-type', 'video')
  block.setAttribute('data-course-id', String(courseId))
  block.setAttribute('contenteditable', 'false')
  block.className = 'knowledge-video-block'
  block.textContent = `课堂视频 · ${title}`
  return block
}

function insertVideo(courseId: number | null): void {
  if (!courseId || !editorEl.value) return
  insertTopLevelNode(createVideoElement(courseId, courseTitle(courseId)))
  videoToInsert.value = null
  ui.toast(`已插入视频：${courseTitle(courseId)}`, 'info')
}

function triggerImageUpload(): void {
  if (saving.value || imageUploading.value) return
  imageError.value = ''
  imageInput.value?.click()
}

async function onImageSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  lastImageFile.value = file
  await uploadKnowledgeImage(file)
}

async function uploadKnowledgeImage(file: File): Promise<void> {
  imageError.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    imageError.value = '只支持 JPG 或 PNG 图片，单张不超过 5MB'
    lastImageFile.value = null
    return
  }
  if (!editorEl.value) return
  imageUploading.value = true
  imageProgress.value = 0
  try {
    const data = await uploadAdminFile(file, 'KNOWLEDGE_MEDIA', (percent) => { imageProgress.value = percent })
    if (!data.file_id || !data.url) throw new Error('上传结果缺少图片地址')
    insertTopLevelNode(createImageElement({ fileId: data.file_id, url: data.url, alt: file.name }))
    lastImageFile.value = null
    ui.toast('已插入正文图片')
  } catch (e) {
    imageError.value = e instanceof Error ? e.message : '图片上传失败，请重试'
  } finally {
    imageUploading.value = false
  }
}

async function retryImageUpload(): Promise<void> {
  if (lastImageFile.value) await uploadKnowledgeImage(lastImageFile.value)
}

function removeImage(image: EditorImage): void {
  const root = editorEl.value
  if (!root || saving.value) return
  const images = Array.from(root.querySelectorAll('img'))
  const target = images.find((img) => (image.fileId ? img.getAttribute('data-file-id') === String(image.fileId) : img.getAttribute('src') === image.url))
  if (!target) return
  const next = target.nextElementSibling
  target.remove()
  if (next && next.tagName === 'P' && !(next.textContent || '').trim() && !next.querySelector('img')) next.remove()
  syncEditor()
  ui.toast('已删除正文图片', 'info')
}

const keywordOptions = computed(() => {
  const set = new Set<string>(['休药期', '可出栏日期', '饲喂', '免疫', '消毒', '腹泻', '通风'])
  for (const keyword of form.keywords) set.add(keyword)
  return [...set].map((value) => ({ title: value, value }))
})
const applicableOptions = computed(() => applicableCategories.value.flatMap((category) => (category.breeds || []).map((breed) => ({ title: `${category.name} · ${breed.name}`, value: breed.id }))))
const onlineText = computed(() => editing.value?.online ? `线上为 版本 ${editing.value.online.version_no}${editing.value.online.published_at ? ' · ' + dayjs(editing.value.online.published_at).format('M月D日') + ' ' + (editing.value.online.published_by_name || '') + '发布' : ''}` : '还没有发布版本')

function clearErrors(): void {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

function openCreate(task?: KnowledgeTask): void {
  editing.value = null
  detailLoading.value = false
  clearErrors()
  formError.value = ''
  imageError.value = ''
  imageProgress.value = 0
  lastImageFile.value = null
  videoToInsert.value = null
  Object.assign(form, {
    title: task?.title || '',
    category_id: task?.category_id || categories.value.find((c) => c.status === 'ENABLED')?.id || null,
    display_type: 'ARTICLE',
    is_major_epidemic: false,
    applicable_category_ids: [],
    keywords: [],
    content: task?.points ? textToHtml(task.points) : '',
    change_note: '',
    source_task_id: task?.id || null,
    version: 0
  })
  drawer.value = true
  void setEditorContent(form.content)
}

async function openEdit(row: KnowledgeRow, task?: KnowledgeTask): Promise<void> {
  clearErrors()
  formError.value = ''
  imageError.value = ''
  imageProgress.value = 0
  lastImageFile.value = null
  videoToInsert.value = null
  drawer.value = true
  detailLoading.value = true
  try {
    const data = await getKnowledgeEdit(row.id)
    editing.value = data
    const contentData = data.draft || data.online_content
    if (!contentData) throw new Error('没有可编辑的内容')
    form.title = contentData.title || data.online_content?.title || row.title
    form.category_id = contentData.category_id || data.online_content?.category_id || null
    form.display_type = contentData.display_type || data.display_type || 'ARTICLE'
    form.is_major_epidemic = !!(contentData.is_major_epidemic ?? data.is_major_epidemic)
    form.applicable_category_ids = [...(contentData.applicable_category_ids || [])]
    form.keywords = [...(contentData.keywords || [])]
    form.content = contentData.content || ''
    form.change_note = data.draft?.change_note || ''
    form.source_task_id = task?.id || data.draft?.source_task_id || null
    form.version = data.version || 0
  } catch (e) {
    drawer.value = false
    ui.toast(e instanceof Error ? e.message : '条目加载失败', 'error')
  } finally {
    detailLoading.value = false
    if (drawer.value) await setEditorContent(form.content)
  }
}

function validate(publish: boolean): boolean {
  clearErrors()
  formError.value = ''
  if (!form.title.trim()) fieldErrors.title = '请填写标题'
  else if (form.title.trim().length > 40) fieldErrors.title = '标题不能超过 40 字'
  if (!form.category_id) fieldErrors.category_id = '请选择分类'
  if (!contentLength.value) fieldErrors.content = '请填写正文'
  else if (contentLength.value > 20000) fieldErrors.content = '正文需为 1–20000 字'
  if (form.keywords.length > 8) fieldErrors.keywords = '关键词最多 8 个'
  if (publish && editing.value && !form.change_note.trim()) fieldErrors.change_note = '请填写修改说明'
  return !Object.keys(fieldErrors).length
}

function buildPayload(action: 'DRAFT' | 'PUBLISH') {
  return {
    title: form.title.trim(),
    category_id: form.category_id!,
    source_type: editing.value ? undefined : 'PLATFORM',
    is_major_epidemic: form.display_type === 'STANDARD' ? form.is_major_epidemic : false,
    display_type: form.display_type,
    applicable_category_ids: form.applicable_category_ids,
    keywords: form.keywords,
    content: normalizeContentHtml(form.content),
    source_task_id: form.source_task_id,
    submit_action: action,
    change_note: form.change_note.trim() || undefined,
    version: editing.value?.version
  }
}

async function submit(action: 'DRAFT' | 'PUBLISH'): Promise<void> {
  if (!validate(action === 'PUBLISH')) return
  saving.value = true
  savingAction.value = action
  formError.value = ''
  try {
    let version = form.version
    if (editing.value) {
      await saveKnowledgeDraft(editing.value.id, buildPayload(action))
      version = (await getKnowledgeEdit(editing.value.id)).version || version
      if (action === 'PUBLISH') await publishKnowledge(editing.value.id, form.change_note.trim(), version)
    } else {
      await createKnowledge(buildPayload(action))
    }
    drawer.value = false
    ui.toast(action === 'PUBLISH' ? (editing.value?.online ? `已发布版本 ${(editing.value.online.version_no || 0) + 1}，AI 将按新版本回答` : '已发布，AI 将按新版本回答') : '已保存草稿')
    await Promise.all([fetchList(), loadTaskPending()])
  } catch (e) {
    if (e instanceof ApiError) {
      for (const item of e.fieldErrors) fieldErrors[item.field] = item.message
      formError.value = e.message
    } else {
      formError.value = e instanceof Error ? e.message : '保存失败'
    }
  } finally {
    saving.value = false
    savingAction.value = ''
  }
}

/* ---------------- 发布 / 上下架 / 删除 ---------------- */
const actionOpen = ref(false)
const actionKind = ref<'off' | 'delete' | ''>('')
const actionTarget = ref<KnowledgeRow | null>(null)
const actionReason = ref('')
const actionError = ref('')
const actionSaving = ref(false)

function openAction(row: KnowledgeRow, kind: 'off' | 'delete'): void {
  actionTarget.value = row
  actionKind.value = kind
  actionReason.value = ''
  actionError.value = ''
  actionOpen.value = true
}

async function confirmAction(): Promise<void> {
  if (!actionTarget.value) return
  if (actionKind.value === 'off' && !actionReason.value.trim()) {
    actionError.value = '请填写下架原因'
    return
  }
  actionSaving.value = true
  actionError.value = ''
  try {
    const edit = await getKnowledgeEdit(actionTarget.value.id)
    if (actionKind.value === 'off') {
      await offShelfKnowledge(actionTarget.value.id, actionReason.value.trim(), edit.version)
    } else {
      await deleteKnowledge(actionTarget.value.id, edit.version)
    }
    actionOpen.value = false
    ui.toast(actionKind.value === 'off' ? '已下架这个条目' : '已删除知识草稿')
    await fetchList()
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    actionSaving.value = false
  }
}

async function quickOnShelf(row: KnowledgeRow): Promise<void> {
  try {
    const edit = await getKnowledgeEdit(row.id)
    await onShelfKnowledge(row.id, edit.version)
    ui.toast('已上架，AI 将重新引用')
    await fetchList()
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '上架失败', 'error')
  }
}

const publishOpen = ref(false)
const publishTarget = ref<KnowledgeRow | null>(null)
const publishNote = ref('')
const publishError = ref('')
const publishSaving = ref(false)
const publishVersion = ref(0)

async function openPublish(row: KnowledgeRow): Promise<void> {
  publishTarget.value = row
  publishNote.value = row.has_draft ? '补充：可出栏日期以批次档案为准' : ''
  publishError.value = ''
  publishOpen.value = true
  try {
    publishVersion.value = (await getKnowledgeEdit(row.id)).version || 0
  } catch (e) {
    publishError.value = e instanceof Error ? e.message : '版本加载失败'
  }
}

async function submitPublish(): Promise<void> {
  if (!publishTarget.value) return
  if (!publishNote.value.trim()) {
    publishError.value = '请填写修改说明'
    return
  }
  publishSaving.value = true
  publishError.value = ''
  try {
    await publishKnowledge(publishTarget.value.id, publishNote.value.trim(), publishVersion.value)
    publishOpen.value = false
    ui.toast('已发布，AI 将按新版本回答')
    await fetchList()
  } catch (e) {
    publishError.value = e instanceof Error ? e.message : '发布失败'
  } finally {
    publishSaving.value = false
  }
}

/* ---------------- 版本 ---------------- */
const versionOpen = ref(false)
const versionLoading = ref(false)
const versionTarget = ref<KnowledgeRow | null>(null)
const versions = ref<KnowledgeVersionItem[]>([])
const selectedVersion = ref<KnowledgeVersion | null>(null)
const selectedVersionNo = ref<number | null>(null)
const versionError = ref('')
const rollbackReason = ref('')
const rollbackSaving = ref(false)
const versionTargetVersion = ref(0)

async function openVersions(row: KnowledgeRow): Promise<void> {
  versionOpen.value = true
  versionTarget.value = row
  versionLoading.value = true
  versionError.value = ''
  versions.value = []
  selectedVersion.value = null
  rollbackReason.value = ''
  try {
    versionTargetVersion.value = (await getKnowledgeEdit(row.id)).version || 0
    versions.value = await getKnowledgeVersions(row.id)
    if (versions.value.length) await selectVersion(versions.value[0]!.version_no)
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : '版本加载失败'
  } finally {
    versionLoading.value = false
  }
}

async function selectVersion(versionNo: number): Promise<void> {
  if (!versionTarget.value) return
  selectedVersionNo.value = versionNo
  try {
    selectedVersion.value = await getKnowledgeVersion(versionTarget.value.id, versionNo)
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : '版本加载失败'
  }
}

async function submitRollback(): Promise<void> {
  if (!versionTarget.value || !selectedVersionNo.value) return
  if (!rollbackReason.value.trim()) {
    versionError.value = '请填写回退原因'
    return
  }
  const online = versions.value.find((v) => v.is_online)
  if (!online) {
    versionError.value = '当前没有线上版本，不能回退'
    return
  }
  rollbackSaving.value = true
  versionError.value = ''
  try {
    const result = await rollbackKnowledge(versionTarget.value.id, selectedVersionNo.value, rollbackReason.value.trim(), versionTargetVersion.value)
    versionOpen.value = false
    ui.toast(`已回退，生成版本 ${result.new_version_no}`)
    await fetchList()
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : '回退失败'
  } finally {
    rollbackSaving.value = false
  }
}

/* ---------------- 补任务 ---------------- */
const taskList = ref<KnowledgeTask[]>([])
const taskLoading = ref(false)
const taskError = ref('')
const taskTab = ref('ALL')
const taskPage = ref(1)
const taskSize = ref(20)
const taskTotal = ref(0)
const taskCounts = reactive<Record<string, number>>({ ALL: 0, PENDING: 0, DONE: 0, CLOSED: 0 })
const taskTabs = [
  { label: '全部', value: 'ALL' },
  { label: '待处理', value: 'PENDING' },
  { label: '已完成', value: 'DONE' },
  { label: '已关闭', value: 'CLOSED' }
]
const taskHeaders = [
  { title: '任务标题', key: 'title', sortable: false, minWidth: 220 },
  { title: '来源', key: 'source_type_text', sortable: false, width: 110 },
  { title: '补充方式', key: 'mode', sortable: false, width: 130 },
  { title: '相似问题', key: 'similar_count', sortable: false, width: 88, align: 'end' as const },
  { title: '指派给', key: 'assignee_name', sortable: false, width: 100 },
  { title: '期望完成', key: 'due_date', sortable: false, width: 116 },
  { title: '状态', key: 'status', sortable: false, width: 90 },
  { title: '操作', key: 'actions', sortable: false, width: 120, align: 'end' as const }
]
const adminOptions = ref<Array<{ title: string; value: number }>>([])
const taskCreateOpen = ref(false)
const taskSaving = ref(false)
const taskFormError = ref('')
const taskForm = reactive({
  title: '',
  mode: 'NEW_ENTRY',
  target_knowledge_id: null as number | null,
  category_id: null as number | null,
  assignee_id: null as number | null,
  due_date: dayjs().tz('Asia/Shanghai').add(7, 'day').format('YYYY-MM-DD'),
  points: '',
  source_type: 'MANUAL'
})

async function loadTaskPending(): Promise<void> {
  try {
    const data = await getKnowledgeTasks({ status: 'PENDING', page: 1, size: 1 })
    taskPending.value = data.total || 0
  } catch {
    /* 忽略 */
  }
}

async function fetchTasks(): Promise<void> {
  taskLoading.value = true
  taskError.value = ''
  try {
    const data = await getKnowledgeTasks({ status: taskTab.value === 'ALL' ? undefined : taskTab.value, page: taskPage.value, size: taskSize.value })
    taskList.value = data.list || []
    taskTotal.value = data.total || 0
    Object.assign(taskCounts, data.tab_counts || {})
  } catch (e) {
    taskError.value = e instanceof Error ? e.message : '任务加载失败'
    taskList.value = []
  } finally {
    taskLoading.value = false
  }
}

async function openTasks(): Promise<void> {
  tab.value = 'tasks'
  taskPage.value = 1
  await fetchTasks()
}

function changeTaskPage(page: number): void {
  taskPage.value = page
  void fetchTasks()
}

function changeTaskSize(size: number): void {
  taskSize.value = size
  taskPage.value = 1
  void fetchTasks()
}

function openTaskCreate(prefill?: KnowledgeTask): void {
  taskForm.title = prefill?.title || ''
  taskForm.mode = prefill?.mode || 'NEW_ENTRY'
  taskForm.target_knowledge_id = prefill?.target_knowledge_id || null
  taskForm.category_id = prefill?.category_id || categories.value[0]?.id || null
  taskForm.assignee_id = prefill?.assignee_id || null
  taskForm.due_date = prefill?.due_date || dayjs().tz('Asia/Shanghai').add(7, 'day').format('YYYY-MM-DD')
  taskForm.points = prefill?.points || ''
  taskFormError.value = ''
  taskCreateOpen.value = true
  if (!adminOptions.value.length) {
    getAdmins().then((data) => { adminOptions.value = (data.list || []).map((a) => ({ title: a.name, value: a.admin_id })) }).catch(() => undefined)
  }
}

async function submitTask(): Promise<void> {
  if (!taskForm.title.trim()) {
    taskFormError.value = '请填写任务标题'
    return
  }
  if (!taskForm.category_id) {
    taskFormError.value = '请选择分类'
    return
  }
  if (!taskForm.assignee_id) {
    taskFormError.value = '请选择指派给'
    return
  }
  if (!taskForm.points.trim()) {
    taskFormError.value = '请填写补充要点'
    return
  }
  taskSaving.value = true
  taskFormError.value = ''
  try {
    await createKnowledgeTask({
      title: taskForm.title.trim(),
      source_type: taskForm.source_type,
      mode: taskForm.mode,
      target_knowledge_id: taskForm.mode === 'SUPPLEMENT' ? taskForm.target_knowledge_id : null,
      category_id: taskForm.category_id!,
      assignee_id: taskForm.assignee_id,
      due_date: taskForm.due_date,
      points: taskForm.points.trim()
    })
    taskCreateOpen.value = false
    ui.toast('已创建知识补充任务')
    await Promise.all([fetchTasks(), loadTaskPending()])
  } catch (e) {
    taskFormError.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    taskSaving.value = false
  }
}

const closeOpen = ref(false)
const closeTarget = ref<KnowledgeTask | null>(null)
const closeReason = ref('')
const closeError = ref('')
const closeSaving = ref(false)

function openCloseTask(task: KnowledgeTask): void {
  closeTarget.value = task
  closeReason.value = ''
  closeError.value = ''
  closeOpen.value = true
}

async function submitCloseTask(): Promise<void> {
  if (!closeTarget.value) return
  if (!closeReason.value.trim()) {
    closeError.value = '请填写关闭原因'
    return
  }
  closeSaving.value = true
  closeError.value = ''
  try {
    await closeKnowledgeTask(closeTarget.value.id, closeReason.value.trim(), closeTarget.value.version ?? 0)
    closeOpen.value = false
    ui.toast('已关闭任务')
    await Promise.all([fetchTasks(), loadTaskPending()])
  } catch (e) {
    closeError.value = e instanceof Error ? e.message : '关闭失败'
  } finally {
    closeSaving.value = false
  }
}

function taskEdit(task: KnowledgeTask): void {
  if (task.mode === 'SUPPLEMENT' && task.target_knowledge_id) {
    openEdit({ id: task.target_knowledge_id, title: task.target_title || task.title } as KnowledgeRow, task)
  } else {
    openCreate(task)
  }
}

const taskTabItems = computed(() => taskTabs.map((t) => ({ ...t, count: taskCounts[t.value] || 0 })))

onMounted(async () => {
  loading.value = true
  void loadOptions()
  await Promise.all([fetchList(), loadTaskPending()])
  loading.value = false
})
</script>

<template>
  <zw-shell side="admin" active="WA-C04" :crumbs="['内容运营', '知识库管理']">
    <zw-page-head title="知识库管理" desc="知识条目是 AI 养殖助手回答的依据，也在用户端“知识原文”中展示。只有已上架的条目会被 AI 引用；每次发布生成一个新版本，可以查看历史并回退。">
      <template #actions>
        <v-btn v-if="tab === 'tasks' && canHandle" color="primary" prepend-icon="add" @click="openTaskCreate()">新建补充任务</v-btn>
        <v-btn v-else-if="canHandle" color="primary" prepend-icon="add" @click="openCreate()">新建条目</v-btn>
      </template>
      <v-tabs v-model="tab" class="zw-tabs-line knowledge-tabs" @update:model-value="tab === 'tasks' && fetchTasks()">
        <v-tab value="items">知识条目 {{ tabCounts.ALL || 0 }}</v-tab>
        <v-tab value="tasks">补充任务 {{ taskPending }}</v-tab>
      </v-tabs>
    </zw-page-head>

    <zw-notice v-if="forbidden" type="error" title="你没有知识库的查看权限">请联系超级管理员在“管理员与权限”中开通 WA-C04 的查看权限。</zw-notice>

    <template v-if="!forbidden && tab === 'items'">
      <zw-card flush>
        <div class="zw-toolbar knowledge-toolbar">
          <div class="zw-toolbar-l">
            <zw-seg :items="tabs" :model-value="query.status" @update:model-value="applyStatus(String($event))" />
          </div>
          <div class="zw-toolbar-r">
            <v-text-field v-model="query.keyword" class="zw-w-200" placeholder="搜索标题或关键词" prepend-inner-icon="search" density="compact" hide-details clearable @keydown.enter="fetchList()" @click:clear="fetchList()" />
            <v-select v-model="query.category_id" :items="categoryOptions" class="zw-w-120" placeholder="全部分类" density="compact" hide-details clearable @update:model-value="fetchList()" />
            <v-select v-model="query.source_type" :items="sourceOptions" class="zw-w-120" density="compact" hide-details @update:model-value="fetchList()" />
          </div>
        </div>
        <v-skeleton-loader v-if="loading" type="table-row@7" />
        <zw-empty v-else-if="error" icon="cloud_off" title="知识条目加载失败" :desc="error" tone="error">
          <v-btn variant="outlined" @click="fetchList()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!list.length && (query.status !== 'ALL' || query.keyword || query.category_id || query.source_type)" icon="search_off" title="没有符合条件的条目" desc="换一个关键词、分类或来源试试。">
          <v-btn variant="text" color="primary" @click="resetFilters">清除筛选</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!list.length" icon="menu_book" title="还没有知识条目" desc="新建条目并上架后，AI 养殖助手才能引用。">
          <v-btn v-if="canHandle" color="primary" @click="openCreate()">新建条目</v-btn>
        </zw-empty>
        <template v-else>
          <v-data-table :headers="headers" :items="list" item-key="id" hide-default-footer density="compact" :loading="listLoading" class="knowledge-table">
            <template #item.title="{ item }">
              <div class="knowledge-title-line">
                <span class="knowledge-title" :title="item.title">{{ item.title }}</span>
                <zw-icon v-if="item.has_video" name="smart_display" :size="16" color="text-3" />
                <zw-tag v-if="item.has_draft" type="warning" size="small">待发布</zw-tag>
              </div>
            </template>
            <template #item.category_name="{ item }">{{ item.category_name || '—' }}</template>
            <template #item.source_type_text="{ item }"><span :class="{ 'zw-muted': item.source_type === 'PLATFORM' }">{{ item.source_type_text }}</span></template>
            <template #item.version_label="{ item }">
              <span>{{ item.version_label || '—' }}</span>
              <div v-if="item.has_draft" class="knowledge-sub">草稿未发布</div>
            </template>
            <template #item.status="{ item }"><zw-tag :type="statusTone(item.status)" size="small">{{ item.status_text }}</zw-tag></template>
            <template #item.cite_count30d="{ item }">
              <span v-if="item.cite_count30d" class="num knowledge-cite"><zw-icon name="auto_awesome" :size="13" />{{ formatNumber(item.cite_count30d) }}</span>
              <span v-else class="zw-faint">—</span>
            </template>
            <template #item.useful_rate_30d="{ item }"><span class="num">{{ item.useful_rate_30d !== undefined && item.useful_rate_30d !== null ? formatPercent(item.useful_rate_30d) : '—' }}</span></template>
            <template #item.updated_at="{ item }">
              <div>{{ item.updated_at ? dayjs(item.updated_at).format('M月D日') : '—' }}</div>
              <div class="knowledge-sub">{{ item.updated_by_name || '—' }}</div>
            </template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a v-if="item.is_readonly" class="zw-link" @click="ui.toast('兽药目录条目请在兽药疫苗目录中维护', 'info')">查看</a>
                <a v-else-if="canHandle" class="zw-link" @click="openEdit(item as KnowledgeRow)">编辑</a>
                <a v-if="!item.is_readonly" class="zw-link" @click="openVersions(item as KnowledgeRow)">历史</a>
                <a v-if="item.has_draft && canHandle" class="zw-link" @click="openPublish(item as KnowledgeRow)">发布</a>
                <a v-if="item.status === 'ON_SHELF' && canHandle" class="zw-link danger" @click="openAction(item as KnowledgeRow, 'off')">下架</a>
                <a v-if="item.status === 'OFF_SHELF' && canHandle" class="zw-link" @click="quickOnShelf(item as KnowledgeRow)">上架</a>
                <a v-if="item.status === 'DRAFT' && canHandle" class="zw-link danger" @click="openAction(item as KnowledgeRow, 'delete')">删除</a>
              </div>
            </template>
          </v-data-table>
          <div class="zw-table-foot">
            <span class="zw-muted">被 AI 引用为近 30 天次数；兽药目录条目从兽药疫苗目录同步，这里只读</span>
            <zw-pager :total="total" :page="query.page" :size="query.size" @update:page="changePage" @update:size="changeSize" />
          </div>
        </template>
      </zw-card>
    </template>

    <template v-if="!forbidden && tab === 'tasks'">
      <zw-card flush>
        <div class="zw-toolbar knowledge-toolbar">
          <zw-seg :items="taskTabItems" :model-value="taskTab" @update:model-value="taskTab = String($event); taskPage = 1; fetchTasks()" />
        </div>
        <v-skeleton-loader v-if="taskLoading" type="table-row@6" />
        <zw-empty v-else-if="taskError" icon="cloud_off" title="任务加载失败" :desc="taskError" tone="error">
          <v-btn variant="outlined" @click="fetchTasks()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="!taskList.length" icon="task_alt" title="没有补充任务" desc="从 AI 问答转为知识补充，或手动创建任务后在这里跟进。" />
        <template v-else>
          <v-data-table :headers="taskHeaders" :items="taskList" item-key="id" hide-default-footer density="compact">
            <template #item.title="{ item }">
              <div class="knowledge-title">{{ item.title }}</div>
              <div v-if="item.source_question" class="knowledge-sub">{{ item.source_question }}</div>
            </template>
            <template #item.mode="{ item }">{{ item.mode_text }}<span v-if="item.target_title" class="zw-muted"> · {{ item.target_title }}</span></template>
            <template #item.similar_count="{ item }"><span class="num">{{ item.similar_count || 0 }}</span></template>
            <template #item.assignee_name="{ item }">{{ item.assignee_name || '—' }}</template>
            <template #item.due_date="{ item }">
              <span :class="{ 'knowledge-overdue': item.is_overdue }">{{ item.due_date ? dayjs(item.due_date).format('M月D日') : '—' }}</span>
            </template>
            <template #item.status="{ item }">
              <zw-tag :type="item.status === 'PENDING' ? 'warning' : item.status === 'DONE' ? 'success' : 'neutral'" size="small">{{ item.status_text }}</zw-tag>
            </template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a v-if="item.status === 'PENDING' && canHandle" class="zw-link" @click="taskEdit(item as KnowledgeTask)">去编辑</a>
                <a v-if="item.status === 'PENDING' && canHandle" class="zw-link danger" @click="openCloseTask(item as KnowledgeTask)">关闭</a>
                <span v-else-if="item.status !== 'PENDING'" class="zw-muted">{{ item.status === 'DONE' ? '已完成' : item.close_reason || '已关闭' }}</span>
              </div>
            </template>
          </v-data-table>
          <div class="zw-table-foot">
            <span class="zw-muted">条目发布后任务自动完成；关闭任务需要填写原因</span>
            <zw-pager :total="taskTotal" :page="taskPage" :size="taskSize" @update:page="changeTaskPage" @update:size="changeTaskSize" />
          </div>
        </template>
      </zw-card>
    </template>

    <!-- 编辑条目 -->
    <zw-drawer v-model="drawer" :title="editing ? '编辑知识条目' : '新建知识条目'" :sub="editing ? `${form.title || '知识条目'} · 保存草稿不影响线上内容，发布后生成新版本` : '保存草稿后可继续编辑；发布后 AI 立即引用'" width="760">
      <v-skeleton-loader v-if="detailLoading" type="paragraph@8" />
      <div v-else class="knowledge-form">
        <div v-if="editing" class="knowledge-online-bar">
          <zw-icon name="verified" :size="18" color="primary" />
          <span>{{ onlineText }}</span>
          <span v-if="editing.cite_count30d" class="zw-muted">近 30 天被 AI 引用 {{ formatNumber(editing.cite_count30d) }} 次</span>
          <span v-if="editing.useful_rate_30d !== undefined" class="zw-muted">有用率 {{ formatPercent(editing.useful_rate_30d) }}</span>
        </div>
        <div class="zw-form-2">
          <zw-form-item label="标题" required :error="fieldErrors.title">
            <v-text-field v-model="form.title" placeholder="请输入知识条目标题" maxlength="40" counter :error="!!fieldErrors.title" />
          </zw-form-item>
          <zw-form-item label="分类" required :error="fieldErrors.category_id">
            <v-select v-model="form.category_id" :items="categoryOptions" placeholder="请选择分类" :error="!!fieldErrors.category_id" />
          </zw-form-item>
          <zw-form-item label="内容类型">
            <v-select v-model="form.display_type" :items="[{ title: '文章', value: 'ARTICLE' }, { title: '标准', value: 'STANDARD' }]" />
          </zw-form-item>
          <zw-form-item label="适用品类" optional hint="不选表示全部品类">
            <v-select v-model="form.applicable_category_ids" :items="applicableOptions" multiple chips density="compact" hide-details placeholder="全部品类" />
          </zw-form-item>
        </div>
        <zw-form-item label="关键词" optional :error="fieldErrors.keywords" hint="AI 查找依据时优先匹配，最多 8 个">
          <v-combobox v-model="form.keywords" :items="keywordOptions" multiple chips closable-chips density="compact" hide-details :error="!!fieldErrors.keywords" />
        </zw-form-item>

        <zw-form-item label="正文" required :error="fieldErrors.content">
          <div class="knowledge-editor">
            <div class="knowledge-editor-bar">
              <div class="knowledge-insert">
                <v-select
                  :model-value="videoToInsert"
                  :items="courses.map((c) => ({ title: c.title, value: c.id }))"
                  placeholder="插入课堂视频"
                  density="compact"
                  hide-details
                  clearable
                  class="knowledge-video-select"
                  :disabled="saving || imageUploading"
                  @update:model-value="insertVideo($event as number | null)"
                />
                <v-btn
                  variant="outlined"
                  size="small"
                  prepend-icon="add_photo_alternate"
                  :disabled="saving || imageUploading"
                  @click="triggerImageUpload"
                >{{ imageUploading ? `上传中 ${imageProgress}%` : '插入图片' }}</v-btn>
                <span class="zw-muted">JPG / PNG，不超过 5MB；图片单独成段，可随时删除</span>
              </div>
              <span class="zw-muted">{{ contentLength }} / 20000</span>
            </div>
            <v-progress-linear v-if="imageUploading" :model-value="imageProgress" height="4" color="primary" />
            <div v-if="imageError" class="knowledge-image-error">
              {{ imageError }} <a v-if="lastImageFile" class="zw-link" @click="retryImageUpload">重试</a>
            </div>
            <div
              ref="editorEl"
              class="knowledge-rich-editor"
              :contenteditable="saving ? 'false' : 'true'"
              :data-empty="contentLength === 0 ? 'true' : 'false'"
              data-placeholder="请输入正文。按回车分段，可插入课堂视频和图片。"
              @input="onEditorInput"
              @paste="onEditorPaste"
            ></div>
            <div v-if="contentImages.length" class="knowledge-image-list">
              <span class="knowledge-image-list-title">正文图片</span>
              <div v-for="(image, index) in contentImages" :key="`${image.fileId || image.url}-${index}`" class="knowledge-image-chip">
                <img v-if="image.url" :src="image.url" alt="" />
                <span>图片 {{ index + 1 }}</span>
                <a class="zw-link danger" :class="{ 'is-disabled': saving }" @click="removeImage(image)">删除</a>
              </div>
            </div>
            <div class="knowledge-preview">
              <div class="knowledge-preview-head"><span>预览</span><span class="zw-muted">图片与视频按正文顺序显示</span></div>
              <div v-if="contentLength" class="knowledge-preview-body" v-html="previewHtml"></div>
              <div v-else class="knowledge-preview-empty">还没有正文内容</div>
            </div>
          </div>
          <input ref="imageInput" type="file" accept="image/jpeg,image/png" hidden @change="onImageSelected" />
        </zw-form-item>
        <div class="zw-form-2">
          <zw-form-item label="修改说明" :required="!!editing" :error="fieldErrors.change_note" hint="显示在版本历史中，记入操作日志">
            <v-text-field v-model="form.change_note" placeholder="请说明这次修改" maxlength="100" :error="!!fieldErrors.change_note" />
          </zw-form-item>
          <zw-form-item v-if="form.display_type === 'STANDARD'" label="重大疫病">
            <v-switch v-model="form.is_major_epidemic" label="标记为疑似重大动物疫病" density="compact" hide-details />
          </zw-form-item>
        </div>
        <div v-if="form.source_task_id" class="knowledge-task-hint"><zw-icon name="task_alt" :size="16" />本次编辑来自知识补充任务，发布后任务会自动完成。</div>
        <div v-if="formError" class="knowledge-form-error">{{ formError }}</div>
      </div>
      <template #foot>
        <span class="zw-foot-note">发布后 AI 立即按新版本回答，并记入操作日志</span>
        <v-btn variant="outlined" :loading="saving && savingAction === 'DRAFT'" :disabled="saving || imageUploading" @click="submit('DRAFT')">保存草稿</v-btn>
        <v-btn color="primary" prepend-icon="publish" :loading="saving && savingAction === 'PUBLISH'" :disabled="saving || imageUploading" @click="submit('PUBLISH')">发布</v-btn>
      </template>
    </zw-drawer>

    <!-- 发布草稿 -->
    <zw-dialog v-model="publishOpen" title="发布这个知识条目？" width="520" icon="publish" tone="primary">
      <p class="knowledge-dialog-strong">{{ publishTarget?.title }}</p>
      <p class="knowledge-dialog-text">发布后 AI 立即按新版本回答；已有回答中的来源保留，用户打开时看到版本更新提示。</p>
      <zw-form-item label="修改说明" required>
        <v-textarea v-model="publishNote" placeholder="请填写本次修改说明" :rows="3" maxlength="100" counter />
      </zw-form-item>
      <div v-if="publishError" class="knowledge-form-error">{{ publishError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="publishSaving" @click="publishOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="publishSaving" @click="submitPublish">发布</v-btn>
      </template>
    </zw-dialog>

    <!-- 版本历史 -->
    <zw-drawer v-model="versionOpen" :title="`版本历史 · ${versionTarget?.title || ''}`" sub="每次发布或回退都生成一个新版本，旧版本始终保留" width="760">
      <v-skeleton-loader v-if="versionLoading" type="paragraph@6" />
      <div v-else class="knowledge-version-layout">
        <div class="knowledge-version-list">
          <button v-for="version in versions" :key="version.version_no" type="button" class="knowledge-version-item" :class="{ on: selectedVersionNo === version.version_no }" @click="selectVersion(version.version_no)">
            <div class="knowledge-version-head">版本 {{ version.version_no }}<zw-tag v-if="version.is_online" type="success" size="small">线上</zw-tag></div>
            <div class="knowledge-sub">{{ version.published_at ? formatDateTime(version.published_at) : '' }}{{ version.published_by_name ? ' · ' + version.published_by_name : '' }}</div>
            <div class="knowledge-version-note">{{ version.change_note || '—' }}</div>
          </button>
          <div v-if="!versions.length" class="zw-muted">还没有发布版本</div>
        </div>
        <div class="knowledge-version-detail">
          <template v-if="selectedVersion">
            <div class="zw-section-title">版本 {{ selectedVersion.version_no }} 与线上版本的差异</div>
            <zw-compare v-if="selectedVersion.compare?.length" :rows="selectedVersion.compare.map((item) => ({ label: item.field_text, before: item.old_value || '—', after: item.new_value || '—' }))" />
            <div v-else class="zw-muted">没有可比较的字段差异</div>
            <div class="zw-section-title">版本正文</div>
            <div class="knowledge-version-content">{{ contentText(selectedVersion.content) }}</div>
            <div v-if="!versions.find((v) => v.version_no === selectedVersionNo)?.is_online" class="knowledge-rollback">
              <div class="knowledge-rollback-title"><zw-icon name="history" :size="18" />回退到版本 {{ selectedVersion?.version_no }}</div>
              <div class="knowledge-dialog-text">回退会把版本 {{ selectedVersion?.version_no }} 的内容发布为新版本，当前线上版本保留在历史中。AI 立即按回退后的内容回答。</div>
              <zw-form-item label="回退原因" required>
                <v-textarea v-model="rollbackReason" placeholder="请填写回退原因" :rows="2" maxlength="100" counter />
              </zw-form-item>
            </div>
          </template>
          <div v-if="versionError" class="knowledge-form-error">{{ versionError }}</div>
        </div>
      </div>
      <template #foot>
        <v-btn variant="outlined" @click="versionOpen = false">关闭</v-btn>
        <v-btn v-if="selectedVersion && !versions.find((v) => v.version_no === selectedVersionNo)?.is_online" color="primary" :loading="rollbackSaving" @click="submitRollback">确认回退</v-btn>
      </template>
    </zw-drawer>

    <!-- 下架 / 删除 -->
    <zw-dialog v-model="actionOpen" :title="actionKind === 'off' ? '下架这条知识？' : '删除知识草稿？'" width="480" :icon="actionKind === 'off' ? 'unpublished' : 'delete'" tone="error">
      <p class="knowledge-dialog-strong">{{ actionTarget?.title }}</p>
      <p v-if="actionKind === 'off'" class="knowledge-dialog-text">下架后，AI 不再引用这条知识；已有回答中的来源保留，用户点开时看到“这条知识已下架，不能查看原文”。</p>
      <p v-else class="knowledge-dialog-text">只能删除未发布过的草稿；删除后不能恢复。</p>
      <zw-form-item v-if="actionKind === 'off'" label="下架原因" required>
        <v-textarea v-model="actionReason" placeholder="请填写下架原因" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <div v-if="actionError" class="knowledge-form-error">{{ actionError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="actionSaving" @click="actionOpen = false">取消</v-btn>
        <v-btn color="error" :loading="actionSaving" @click="confirmAction">{{ actionKind === 'off' ? '下架' : '删除' }}</v-btn>
      </template>
    </zw-dialog>

    <!-- 新建任务 -->
    <zw-drawer v-model="taskCreateOpen" title="新建知识补充任务" sub="任务会指派给有知识库处理权限的管理员，并发送站内消息" width="560">
      <div class="zw-form">
        <zw-form-item label="任务标题" required>
          <v-text-field v-model="taskForm.title" placeholder="最多 40 字" maxlength="40" />
        </zw-form-item>
        <div class="zw-form-2">
          <zw-form-item label="补充方式" required>
            <v-select v-model="taskForm.mode" :items="[{ title: '新建条目', value: 'NEW_ENTRY' }, { title: '补充已有条目', value: 'SUPPLEMENT' }]" />
          </zw-form-item>
          <zw-form-item label="分类" required>
            <v-select v-model="taskForm.category_id" :items="categoryOptions" placeholder="请选择分类" />
          </zw-form-item>
          <zw-form-item v-if="taskForm.mode === 'SUPPLEMENT'" label="目标条目" required>
            <v-select v-model="taskForm.target_knowledge_id" :items="list.map((item) => ({ title: item.title, value: item.id }))" placeholder="请选择已有条目" />
          </zw-form-item>
          <zw-form-item label="指派给" required>
            <v-select v-model="taskForm.assignee_id" :items="adminOptions" placeholder="请选择管理员" />
          </zw-form-item>
          <zw-form-item label="期望完成" required>
            <v-text-field v-model="taskForm.due_date" type="date" :min="today()" />
          </zw-form-item>
        </div>
        <zw-form-item label="补充要点" required>
          <v-textarea v-model="taskForm.points" placeholder="写清需要补充的内容" :rows="4" maxlength="200" counter />
        </zw-form-item>
        <div v-if="taskFormError" class="knowledge-form-error">{{ taskFormError }}</div>
      </div>
      <template #foot>
        <v-btn variant="outlined" :disabled="taskSaving" @click="taskCreateOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="taskSaving" @click="submitTask">创建任务</v-btn>
      </template>
    </zw-drawer>

    <!-- 关闭任务 -->
    <zw-dialog v-model="closeOpen" title="关闭这个补充任务？" width="440" icon="task_alt" tone="warning">
      <p class="knowledge-dialog-strong">{{ closeTarget?.title }}</p>
      <p class="knowledge-dialog-text">关闭后任务不再出现在待处理中，来源问答的处理状态不会自动变更。</p>
      <zw-form-item label="关闭原因" required>
        <v-textarea v-model="closeReason" placeholder="请填写关闭原因" :rows="2" maxlength="100" counter />
      </zw-form-item>
      <div v-if="closeError" class="knowledge-form-error">{{ closeError }}</div>
      <template #actions>
        <v-btn variant="outlined" :disabled="closeSaving" @click="closeOpen = false">取消</v-btn>
        <v-btn color="error" :loading="closeSaving" @click="submitCloseTask">关闭任务</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.knowledge-tabs { margin-top: 8px; }
.knowledge-toolbar { padding: 16px; }
.knowledge-title-line { display: flex; align-items: center; gap: 5px; min-width: 0; }
.knowledge-title { font-weight: 500; max-width: 330px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.knowledge-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.knowledge-cite { display: inline-flex; align-items: center; gap: 3px; color: var(--tertiary); }
.knowledge-table :deep(tbody tr) { height: 40px; }
.knowledge-form { display: flex; flex-direction: column; gap: 18px; }
.knowledge-online-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 16px; background: var(--fill-1); border-radius: var(--radius-sm); font-size: 13px; color: var(--text-2); }
.knowledge-editor { border: 1px solid var(--line-2); border-radius: var(--radius-sm); overflow: hidden; }
.knowledge-editor-bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px 12px; padding: 8px 12px; background: var(--fill-1); }
.knowledge-insert { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; flex: 1; min-width: 0; font-size: 12px; }
.knowledge-video-select { max-width: 190px; min-width: 150px; }
.knowledge-rich-editor { position: relative; min-height: 260px; max-height: 420px; overflow: auto; padding: 14px 16px; font-size: 14px; line-height: 23px; color: var(--text-1); outline: none; }
.knowledge-rich-editor[data-empty="true"]::before { content: attr(data-placeholder); position: absolute; left: 16px; top: 14px; color: var(--text-3); pointer-events: none; }
.knowledge-rich-editor :deep(p) { margin: 0 0 10px; }
.knowledge-rich-editor :deep(h2), .knowledge-rich-editor :deep(h3) { margin: 0 0 10px; font-size: 16px; }
.knowledge-rich-editor :deep(blockquote) { margin: 0 0 10px; padding: 2px 0 2px 12px; border-left: 3px solid var(--line-2); color: var(--text-2); }
.knowledge-rich-editor :deep(ul), .knowledge-rich-editor :deep(ol) { margin: 0 0 10px; padding-left: 22px; }
.knowledge-rich-editor :deep(img) { display: block; max-width: 100%; max-height: 240px; margin: 4px 0 10px; border-radius: var(--radius-sm); }
.knowledge-rich-editor :deep(.knowledge-video-block) { display: flex; align-items: center; gap: 8px; margin: 4px 0 10px; padding: 9px 12px; border-radius: var(--radius-sm); background: var(--primary-subtle); color: var(--primary); font-size: 13px; }
.knowledge-image-error { padding: 6px 12px; font-size: 12px; color: var(--error); background: var(--fill-1); }
.knowledge-image-list { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 9px 12px; border-top: 1px dashed var(--line-1); background: var(--bg-card); }
.knowledge-image-list-title { font-size: 12px; color: var(--text-3); }
.knowledge-image-chip { display: inline-flex; align-items: center; gap: 6px; padding: 3px 9px 3px 4px; border: 1px solid var(--line-1); border-radius: 999px; background: var(--fill-1); font-size: 12px; }
.knowledge-image-chip img { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; display: block; }
.knowledge-preview { border-top: 1px solid var(--line-2); background: var(--bg-card); }
.knowledge-preview-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 12px; background: var(--fill-1); font-size: 12px; color: var(--text-3); }
.knowledge-preview-head > span:first-child { color: var(--text-1); font-weight: 600; }
.knowledge-preview-body { padding: 12px 16px; max-height: 220px; overflow: auto; font-size: 13px; line-height: 22px; }
.knowledge-preview-body :deep(p) { margin: 0 0 8px; }
.knowledge-preview-body :deep(h2), .knowledge-preview-body :deep(h3) { margin: 0 0 8px; font-size: 15px; }
.knowledge-preview-body :deep(blockquote) { margin: 0 0 8px; padding: 2px 0 2px 10px; border-left: 3px solid var(--line-2); color: var(--text-2); }
.knowledge-preview-body :deep(ul), .knowledge-preview-body :deep(ol) { margin: 0 0 8px; padding-left: 20px; }
.knowledge-preview-body :deep(img) { display: block; max-width: 100%; max-height: 200px; margin: 6px 0; border-radius: var(--radius-sm); }
.knowledge-preview-body :deep(.knowledge-preview-video) { display: flex; align-items: center; gap: 8px; margin: 4px 0 8px; padding: 8px 10px; border-radius: var(--radius-sm); background: var(--primary-subtle); color: var(--primary); font-size: 12px; }
.knowledge-preview-empty { padding: 14px 16px; font-size: 12px; color: var(--text-3); }
.is-disabled { color: var(--text-3) !important; pointer-events: none; opacity: 0.45; }
.knowledge-task-hint { display: flex; align-items: center; gap: 6px; padding: 10px 14px; border-radius: var(--radius-sm); background: var(--primary-subtle); color: var(--primary); font-size: 13px; }
.knowledge-form-error { margin-top: 8px; color: var(--error); font-size: 12px; }
.knowledge-dialog-strong { font-size: 15px; font-weight: 600; margin: 0 0 6px; }
.knowledge-dialog-text { font-size: 13px; line-height: 21px; color: var(--text-2); margin-bottom: 12px; }
.knowledge-version-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 18px; align-items: start; }
.knowledge-version-list { border: 1px solid var(--line-1); border-radius: var(--radius-sm); max-height: 600px; overflow: auto; }
.knowledge-version-item { width: 100%; text-align: left; border: 0; border-bottom: 1px solid var(--line-1); padding: 10px 14px; background: var(--bg-card); cursor: pointer; font: inherit; }
.knowledge-version-item:hover { background: var(--fill-1); }
.knowledge-version-item.on { background: var(--primary-subtle); }
.knowledge-version-head { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 500; }
.knowledge-version-note { margin-top: 2px; font-size: 12px; color: var(--text-2); line-height: 18px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.knowledge-version-detail { min-width: 0; }
.knowledge-version-content { border: 1px solid var(--line-1); border-radius: var(--radius-sm); padding: 12px 14px; font-size: 13px; line-height: 22px; white-space: pre-wrap; max-height: 280px; overflow: auto; }
.knowledge-rollback { margin-top: 18px; border: 1px solid var(--line-1); border-left: 3px solid var(--warning); background: var(--fill-1); border-radius: var(--radius-sm); padding: 14px 16px; }
.knowledge-rollback-title { display: flex; align-items: center; gap: 6px; font-weight: 600; color: var(--warning); margin-bottom: 6px; }
.knowledge-overdue { color: var(--error); }
</style>
