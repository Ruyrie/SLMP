import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import HealthPhotoUpload from '../apps/user/src/views/health/components/HealthPhotoUpload.vue'
import HerdPhotoUpload from '../apps/user/src/views/herd/components/HerdPhotoUpload.vue'
import VoucherUpload from '../apps/user/src/views/finance/components/VoucherUpload.vue'

const uploads = vi.hoisted(() => ({ health: vi.fn(), herd: vi.fn(), finance: vi.fn() }))
vi.mock('../apps/user/src/api/health', () => ({ healthApi: { uploadPhoto: uploads.health } }))
vi.mock('../apps/user/src/api/herd', () => ({ herdApi: { uploadFile: uploads.herd } }))
vi.mock('../apps/user/src/api/finance', () => ({ financeApi: { uploadFile: uploads.finance } }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

function deferred() {
  let resolve!: (value: { file_id?: number }) => void
  let reject!: (error: Error) => void
  const promise = new Promise<{ file_id?: number }>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

let wrapper: VueWrapper
beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = vi.fn(() => 'blob:photo')
    static revokeObjectURL = vi.fn()
  })
})
afterEach(() => { wrapper?.unmount(); vi.unstubAllGlobals() })

describe.each([
  { name: '消毒照片', component: HealthPhotoUpload, upload: uploads.health, prefix: '.hp', progressArg: 1 },
  { name: '死淘照片', component: HerdPhotoUpload, upload: uploads.herd, prefix: '.herd-photo', progressArg: 2 },
  { name: '财务凭证', component: VoucherUpload, upload: uploads.finance, prefix: '.finance-voucher', progressArg: 2 }
])('$name upload and parent submission', ({ component, upload, prefix, progressArg }) => {
  function page() {
    wrapper = mount(defineComponent({
      setup() {
        const files = ref<number[]>([])
        const busy = ref(false)
        const submitted = ref<number[] | null>(null)
        return () => h('form', [
          h(component, {
            modelValue: files.value,
            'onUpdate:modelValue': (ids: number[]) => { files.value = ids },
            'onUpdate:busy': (value: boolean) => { busy.value = value }
          }),
          h('button', { type: 'button', 'data-submit': '', disabled: busy.value,
            onClick: () => { submitted.value = [...files.value] } }, '提交'),
          h('output', submitted.value === null ? '' : JSON.stringify(submitted.value))
        ])
      }
    }), { global: { stubs: { ZwIcon: true, VProgressCircular: {
      props: ['modelValue'], template: '<span class="upload-progress">{{ modelValue }}%</span>'
    } } } })
  }
  async function choose(count = 1) {
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true,
      value: Array.from({ length: count }, (_, i) => new File(['image'], `${i}.png`, { type: 'image/png' })) })
    await input.trigger('change')
    await flushPromises()
  }
  function submitDisabled() { return (wrapper.get('[data-submit]').element as HTMLButtonElement).disabled }

  it('unlocks submission as soon as upload finishes, with the returned file ID', async () => {
    const request = deferred()
    upload.mockReturnValueOnce(request.promise)
    page()
    await choose()
    expect(submitDisabled()).toBe(true)
    upload.mock.calls[0][progressArg](47)
    await flushPromises()
    expect(wrapper.get('.upload-progress').text()).toBe('47%')
    request.resolve({ file_id: 81 })
    await flushPromises()
    expect(wrapper.find(`${prefix}-mask`).exists()).toBe(false)
    expect(submitDisabled()).toBe(false)
    await wrapper.get('[data-submit]').trigger('click')
    expect(wrapper.get('output').text()).toBe('[81]')
  })

  it('keeps submission locked until all selected photos finish, regardless of completion order', async () => {
    const first = deferred(), second = deferred()
    upload.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    page()
    await choose(2)
    second.resolve({ file_id: 82 })
    await flushPromises()
    expect(submitDisabled()).toBe(true)
    first.resolve({ file_id: 81 })
    await flushPromises()
    expect(submitDisabled()).toBe(false)
    await wrapper.get('[data-submit]').trigger('click')
    expect(wrapper.get('output').text()).toBe('[81,82]')
  })

  it('shows a failed upload immediately and unlocks submission after retry succeeds', async () => {
    const failed = deferred(), retried = deferred()
    upload.mockReturnValueOnce(failed.promise).mockReturnValueOnce(retried.promise)
    page()
    await choose()
    failed.reject(new Error('network timeout'))
    await flushPromises()
    expect(submitDisabled()).toBe(false)
    await wrapper.get(`${prefix}-retry`).trigger('click')
    expect(submitDisabled()).toBe(true)
    retried.resolve({ file_id: 83 })
    await flushPromises()
    expect(submitDisabled()).toBe(false)
    await wrapper.get('[data-submit]').trigger('click')
    expect(wrapper.get('output').text()).toBe('[83]')
  })

  it('shows an invalid upload response as a retryable error instead of a successful attachment', async () => {
    const request = deferred()
    upload.mockReturnValueOnce(request.promise)
    page()
    await choose()
    request.resolve({})
    await flushPromises()
    expect(submitDisabled()).toBe(false)
    expect(wrapper.get(`${prefix}-retry`).text()).toContain('上传失败')
    await wrapper.get('[data-submit]').trigger('click')
    expect(wrapper.get('output').text()).toBe('[]')
  })
})
