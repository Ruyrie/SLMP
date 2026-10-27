import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import MediaUpload from '../apps/user/src/views/inspect/components/MediaUpload.vue'
import { uploadFile } from '../apps/user/src/api/inspect'

vi.mock('../apps/user/src/api/inspect', () => ({ uploadFile: vi.fn() }))

let wrapper: VueWrapper
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
function page() {
  wrapper = mount(MediaUpload, {
    props: { scene: 'INSPECTION', modelValue: [],
      'onUpdate:modelValue': (value: number[]) => { void wrapper.setProps({ modelValue: value }) } },
    global: { stubs: { ZwIcon: true, VProgressCircular: true } }
  })
}
async function choose(name = 'inspection.png') {
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true,
    value: [new File(['image'], name, { type: 'image/png' })] })
  await input.trigger('change')
  await flushPromises()
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = vi.fn(() => 'blob:inspection')
    static revokeObjectURL = vi.fn()
  })
})
afterEach(() => { wrapper?.unmount(); vi.unstubAllGlobals() })

describe('inspection media upload', () => {
  it('renders live progress and emits the uploaded file ID without another user action', async () => {
    const request = deferred<{ file_id: number; url: string }>()
    vi.mocked(uploadFile).mockReturnValueOnce(request.promise)
    page()
    await choose()
    vi.mocked(uploadFile).mock.calls[0][2]?.(47)
    await flushPromises()
    expect(wrapper.get('.insp-upload-mask').text()).toContain('47%')
    request.resolve({ file_id: 81, url: '/inspection.png' })
    await flushPromises()
    expect(wrapper.find('.insp-upload-mask').exists()).toBe(false)
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[81]])
    expect(wrapper.emitted('update:busy')?.at(-1)).toEqual([false])
    expect(wrapper.get('button[aria-label="删除"]').exists()).toBe(true)
  })

  it('keeps a failed image visible for retry, then allows it to be deleted', async () => {
    vi.mocked(uploadFile).mockRejectedValueOnce(new Error('请求超时'))
    page()
    await choose()
    expect(wrapper.get('.insp-upload-retry').text()).toContain('点击重试')
    expect(wrapper.emitted('update:busy')?.at(-1)).toEqual([false])
    vi.mocked(uploadFile).mockResolvedValueOnce({ file_id: 82, url: '/retry.png' })
    await wrapper.get('.insp-upload-retry').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[82]])
    await wrapper.get('button[aria-label="删除"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('.insp-upload-item').exists()).toBe(false)
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[]])
  })

  it('finishes a valid MP4 upload and clears busy without another user action', async () => {
    const request = deferred<{ file_id: number }>()
    vi.mocked(uploadFile).mockReturnValueOnce(request.promise)
    page()
    const createElement = document.createElement.bind(document)
    const createSpy = vi.spyOn(document, 'createElement').mockImplementation((tag, options) => {
      const element = createElement(tag, options)
      if (tag === 'video') {
        Object.defineProperty(element, 'duration', { value: 10 })
        queueMicrotask(() => element.dispatchEvent(new Event('loadedmetadata')))
      }
      return element
    })
    try {
      const input = wrapper.get('input[type="file"]')
      Object.defineProperty(input.element, 'files', { configurable: true,
        value: [new File(['video'], 'inspection.mp4', { type: 'video/mp4' })] })
      await input.trigger('change')
      await flushPromises()
      expect(wrapper.emitted('update:busy')?.at(-1)).toEqual([true])
      request.resolve({ file_id: 84 })
      await flushPromises()
      expect(wrapper.find('.insp-upload-mask').exists()).toBe(false)
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[84]])
      expect(wrapper.emitted('update:busy')?.at(-1)).toEqual([false])
    } finally {
      createSpy.mockRestore()
    }
  })

  it('cancels an in-flight upload and ignores its late response', async () => {
    const request = deferred<{ file_id: number }>()
    vi.mocked(uploadFile).mockReturnValueOnce(request.promise)
    page()
    await choose()
    const signal = vi.mocked(uploadFile).mock.calls[0][3]
    await wrapper.get('button[aria-label="取消上传"]').trigger('click')
    expect(signal?.aborted).toBe(true)
    expect(wrapper.find('.insp-upload-item').exists()).toBe(false)
    request.resolve({ file_id: 83 })
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[]])
    expect(wrapper.emitted('update:busy')?.at(-1)).toEqual([false])
  })

  it('aborts uploads and releases previews when the form is closed', async () => {
    vi.mocked(uploadFile).mockReturnValueOnce(new Promise(() => {}))
    page()
    await choose()
    const signal = vi.mocked(uploadFile).mock.calls[0][3]
    wrapper.unmount()
    expect(signal?.aborted).toBe(true)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:inspection')
  })

  it('treats a response without a file ID as a retryable failure', async () => {
    vi.mocked(uploadFile).mockResolvedValueOnce({})
    page()
    await choose()
    expect(wrapper.get('.insp-upload-retry').text()).toContain('上传失败')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([[]])
  })
})
