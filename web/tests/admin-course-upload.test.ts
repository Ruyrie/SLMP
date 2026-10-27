import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CoursesAdminView from '../apps/admin/src/views/content/CoursesAdminView.vue'
import * as content from '../apps/admin/src/api/content'
import { useAdminStore } from '../apps/admin/src/stores/admin'

vi.mock('../apps/admin/src/api/content', async (original) => {
  const actual = await original<Record<string, unknown>>()
  return Object.fromEntries(Object.entries(actual).map(([key, value]) => [key, typeof value === 'function' ? vi.fn() : value]))
})
vi.mock('../apps/admin/src/api/base', () => ({ getCategoryTree: vi.fn(async () => []) }))

const input = defineComponent({ props: ['modelValue', 'placeholder'], emits: ['update:modelValue'],
  template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' })
const button = defineComponent({ props: ['disabled', 'loading'], emits: ['click'],
  template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' })
const container = { template: '<div><slot /><slot name="actions" /><slot name="foot" /></div>' }
const drawer = { props: ['modelValue', 'title'], emits: ['update:modelValue'],
  template: '<section v-if="modelValue" class="test-drawer"><h2>{{title}}</h2><button aria-label="关闭抽屉" @click="$emit(\'update:modelValue\',false)">关闭</button><slot /><slot name="foot" /></section>' }
let wrapper: VueWrapper

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}
function byText(text: string) {
  const result = wrapper.findAll('button').find((node) => node.text() === text)
  if (!result) throw new Error(`Button missing: ${text}`)
  return result
}
async function choose(kind: 'video' | 'cover', name: string) {
  const control = wrapper.get(`input[type="file"][accept^="${kind === 'video' ? 'video/' : 'image/'}"]`)
  Object.defineProperty(control.element, 'files', { configurable: true,
    value: [new File(['qa'], name, { type: kind === 'video' ? 'video/mp4' : 'image/png' })] })
  await control.trigger('change')
  await flushPromises()
}
async function page(query = '') {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/courses', name: 'courses', component: {} }
  ] })
  await router.push('/courses' + query)
  const wrappers = Object.fromEntries(['ZwShell', 'ZwPageHead', 'ZwCard', 'ZwFormItem', 'ZwDesc',
    'ZwDescItem', 'ZwEmpty', 'ZwNotice', 'ZwStat', 'ZwTag', 'VTabs', 'VTab'].map((name) => [name, container]))
  wrapper = mount(CoursesAdminView, { global: { plugins: [router], stubs: {
    ...wrappers, VBtn: button, VTextField: input, VTextarea: input, VSelect: input,
    ZwDrawer: drawer, ZwDialog: drawer, VDataTable: true, ZwIcon: true, ZwSeg: true,
    ZwPager: true, VSkeletonLoader: true, VProgressLinear: true, VProgressCircular: true
  } } })
  await flushPromises()
  if (!query) await byText('上传课程').trigger('click')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  useAdminStore().applyLogin({ admin: { admin_id: 1, role_code: 'SUPER_ADMIN' } })
  vi.resetAllMocks()
  vi.useFakeTimers()
  vi.mocked(content.getCourses).mockResolvedValue({ list: [], total: 0, page: 1, size: 10, has_more: false })
  vi.mocked(content.getCourseTopics).mockResolvedValue([])
  vi.mocked(content.getContentCategories).mockResolvedValue([{ id: 1, name: '测试分类', status: 'ENABLED', sort_no: 1 }])
  vi.mocked(content.getKnowledgePage).mockResolvedValue({ list: [], total: 0, page: 1, size: 20, has_more: false })
  vi.mocked(content.getCourseSummary).mockResolvedValue({ on_shelf_count: 0, topic_count: 0, category_count: 1 })
  vi.mocked(content.initMultipartUpload).mockResolvedValue({ file_id: 10, part_size: 1024, part_count: 1,
    original_name: 'qa.mp4', part_urls: [], uploaded_parts: [] })
  vi.mocked(content.uploadMultipartPartProxy).mockResolvedValue({ part_no: 1, etag: 'qa-etag' })
  vi.mocked(content.completeMultipartUpload).mockResolvedValue({ file_id: 10, transcode_status: 'WAITING' })
  vi.mocked(content.uploadAdminFile).mockResolvedValue({ file_id: 77, url: 'http://127.0.0.1:9000/qa-cover.png' })
})
afterEach(() => { wrapper?.unmount(); vi.clearAllTimers(); vi.useRealTimers() })

describe('course upload state', () => {
  it('edits and republishes a ready historical course without reuploading a video', async () => {
    vi.mocked(content.getCourseDetail).mockResolvedValue({ id: 6302, title: '历史课程', category_id: 1,
      status: 'OFF_SHELF', status_text: '已下架', transcode_status: 'SUCCESS', can_on_shelf: true,
      p720: { status: 'SUCCESS' }, p480: { status: 'SUCCESS' }, cover_file_id: 77, cover_source: 'AUTO', version: 3 })
    await page('?course_id=6302')
    await byText('上架').trigger('click')
    await flushPromises()
    expect(content.updateCourse).toHaveBeenCalledWith(6302, expect.objectContaining({
      submit_action: 'ON_SHELF', video_file_id: null, cover_file_id: 77, version: 3
    }))
  })

  it('keeps the uploaded cover when automatic frames arrive, and saves that cover', async () => {
    await page()
    await choose('video', 'qa.mp4')
    await choose('cover', 'cover.png')
    vi.mocked(content.getAdminFile).mockResolvedValue({ file_id: 10, transcode_status: 'SUCCESS', duration_sec: 18,
      cover_candidates: [{ file_id: 99, url: 'http://127.0.0.1:9000/automatic-cover.jpg' }] })
    await vi.advanceTimersByTimeAsync(4000)
    await flushPromises()
    expect(wrapper.get('.course-cover-item img').attributes('src')).toBe('http://127.0.0.1:9000/qa-cover.png')
    await wrapper.get('input[placeholder="请输入课程标题"]').setValue('测试课程')
    await byText('存为草稿').trigger('click')
    await flushPromises()
    expect(content.createCourse).toHaveBeenCalledWith(expect.objectContaining({ cover_source: 'UPLOAD', cover_file_id: 77, video_file_id: 10 }))
  })

  it('does not attach a previous cover upload to a newly opened course', async () => {
    const old = deferred<{ file_id: number; url: string }>()
    vi.mocked(content.uploadAdminFile).mockReturnValueOnce(old.promise)
    await page()
    await choose('cover', 'old.png')
    expect(byText('存为草稿').attributes('disabled')).toBeDefined()
    await wrapper.get('button[aria-label="关闭抽屉"]').trigger('click')
    await byText('上传课程').trigger('click')
    old.resolve({ file_id: 77, url: 'http://127.0.0.1:9000/old-cover.png' })
    await flushPromises()
    expect(wrapper.find('.course-cover-item img').exists()).toBe(false)
    expect(byText('存为草稿').attributes('disabled')).toBeUndefined()
  })

  it('does not attach a previous video upload to a newly opened course', async () => {
    const old = deferred<Awaited<ReturnType<typeof content.completeMultipartUpload>>>()
    vi.mocked(content.completeMultipartUpload).mockReturnValueOnce(old.promise)
    await page()
    await choose('video', 'old.mp4')
    expect(byText('存为草稿').attributes('disabled')).toBeDefined()
    await wrapper.get('button[aria-label="关闭抽屉"]').trigger('click')
    await byText('上传课程').trigger('click')
    old.resolve({ file_id: 10, transcode_status: 'SUCCESS' })
    await flushPromises()
    expect(wrapper.text()).toContain('还没有上传视频')
    expect(content.getAdminFile).not.toHaveBeenCalled()
  })

  it('discards an old transcode response after another video has been selected', async () => {
    await page()
    const old = deferred<Awaited<ReturnType<typeof content.getAdminFile>>>()
    vi.mocked(content.getAdminFile).mockReturnValueOnce(old.promise)
    await choose('video', 'old.mp4')
    await vi.advanceTimersByTimeAsync(4000)
    vi.mocked(content.initMultipartUpload).mockResolvedValue({ file_id: 20, part_size: 1024, part_count: 1,
      original_name: 'new.mp4', part_urls: [], uploaded_parts: [] })
    vi.mocked(content.completeMultipartUpload).mockResolvedValue({ file_id: 20, transcode_status: 'WAITING' })
    await choose('video', 'new.mp4')
    old.resolve({ file_id: 10, transcode_status: 'SUCCESS', cover_candidates: [{ file_id: 99, url: 'http://127.0.0.1:9000/old.jpg' }] })
    await flushPromises()
    expect(wrapper.find('.course-cover-item img').exists()).toBe(false)
    vi.mocked(content.getAdminFile).mockResolvedValue({ file_id: 20, transcode_status: 'SUCCESS', cover_candidates: [{ file_id: 100, url: 'http://127.0.0.1:9000/new.jpg' }] })
    await vi.advanceTimersByTimeAsync(4000)
    await flushPromises()
    expect(wrapper.get('.course-cover-item img').attributes('src')).toBe('http://127.0.0.1:9000/new.jpg')
  })

  it('shows automatic retry and lets a final failure be retried', async () => {
    await page()
    await choose('video', 'qa.mp4')
    vi.mocked(content.getAdminFile).mockResolvedValueOnce({ file_id: 10, transcode_status: 'WAITING', transcode_error: 'temporary failure' })
    await vi.advanceTimersByTimeAsync(4000)
    expect(wrapper.text()).toContain('系统将自动重试')
    vi.mocked(content.getAdminFile).mockResolvedValueOnce({ file_id: 10, transcode_status: 'FAILED' })
    await vi.advanceTimersByTimeAsync(4000)
    const retry = wrapper.findAll('a').find((node) => node.text() === '重试转码')!
    await retry.trigger('click')
    await flushPromises()
    expect(content.retryFileTranscode).toHaveBeenCalledWith(10)
    expect(content.retryCourseTranscode).not.toHaveBeenCalled()
  })

  it('retries the newly uploaded file when replacing an existing course video', async () => {
    vi.mocked(content.getCourseDetail).mockResolvedValue({ id: 101, title: '原课程', category_id: 1,
      status: 'DRAFT', status_text: '草稿', transcode_status: 'SUCCESS', video: { file_id: 5 } })
    await page('?course_id=101')
    await choose('video', 'replacement.mp4')
    vi.mocked(content.getAdminFile).mockResolvedValueOnce({ file_id: 10, transcode_status: 'FAILED' })
    await vi.advanceTimersByTimeAsync(4000)
    await wrapper.findAll('a').find((node) => node.text() === '重试转码')!.trigger('click')
    await flushPromises()
    expect(content.retryFileTranscode).toHaveBeenCalledWith(10)
    expect(content.retryCourseTranscode).not.toHaveBeenCalled()
  })

  it('continues refreshing a pending video when its saved draft is reopened', async () => {
    vi.mocked(content.getCourseDetail).mockResolvedValue({ id: 101, title: '待转码课程', category_id: 1,
      status: 'DRAFT', status_text: '草稿', transcode_status: 'WAITING', video: { file_id: 5 } })
    vi.mocked(content.getAdminFile).mockResolvedValueOnce({ file_id: 5, transcode_status: 'SUCCESS',
      cover_candidates: [{ file_id: 99, url: 'http://127.0.0.1:9000/automatic-cover.jpg' }] })
    await page('?course_id=101')
    await vi.advanceTimersByTimeAsync(4000)
    await flushPromises()
    expect(content.getAdminFile).toHaveBeenCalledWith(5)
    expect(wrapper.get('.course-cover-item img').attributes('src')).toBe('http://127.0.0.1:9000/automatic-cover.jpg')
    expect(wrapper.text()).toContain('转码完成')
  })

  it('does not close or unlock a newer draft when an older save finishes', async () => {
    const old = deferred<Awaited<ReturnType<typeof content.createCourse>>>()
    const latest = deferred<Awaited<ReturnType<typeof content.createCourse>>>()
    vi.mocked(content.createCourse).mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    await page()
    await wrapper.get('input[placeholder="请输入课程标题"]').setValue('旧草稿')
    await byText('存为草稿').trigger('click')
    await wrapper.get('button[aria-label="关闭抽屉"]').trigger('click')
    await byText('上传课程').trigger('click')
    await wrapper.get('input[placeholder="请输入课程标题"]').setValue('新草稿')
    await byText('存为草稿').trigger('click')
    old.resolve({ id: 1, status: 'DRAFT' })
    await flushPromises()
    expect(wrapper.get('input[placeholder="请输入课程标题"]').element).toHaveProperty('value', '新草稿')
    expect(byText('存为草稿').attributes('disabled')).toBeDefined()
    latest.resolve({ id: 2, status: 'DRAFT' })
    await flushPromises()
    expect(wrapper.find('.test-drawer').exists()).toBe(false)
  })
})

