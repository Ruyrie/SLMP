import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import AdminLoginView from '../apps/admin/src/views/system/AdminLoginView.vue'
import { useAdminStore } from '../apps/admin/src/stores/admin'
import { ApiError } from '../packages/shared/src/request/error'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../apps/admin/src/api/client', () => ({ http: () => api }))

const input = defineComponent({
  props: ['modelValue', 'placeholder'], emits: ['update:modelValue'],
  template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
})
const button = defineComponent({
  props: ['loading', 'disabled'], emits: ['click'],
  template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
})

async function loginPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/login', name: 'WA-S01', component: {} }, { path: '/', name: 'WA-S02', component: {} }
  ] })
  await router.push('/login')
  return mount(AdminLoginView, {
    global: {
      plugins: [router],
      stubs: {
        VTextField: input, VBtn: button, VCheckbox: true,
        VDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
        ZwIcon: true, ZwFormItem: { template: '<div><slot /></div>' },
        ZwNotice: { template: '<div role="alert"><slot /></div>' }
      }
    }
  })
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.resetAllMocks()
  api.get.mockResolvedValue({ captcha_id: 'fresh-captcha', image: 'data:image/png;base64,AAAA' })
})

describe('admin login form', () => {
  it('refreshes a consumed CAPTCHA after a password rejection', async () => {
    api.post.mockRejectedValue(new ApiError({ code: 40102, message: '账号或密码不正确' }))
    const page = await loginPage()
    await flushPromises()
    await page.get('input[placeholder="请输入管理员账号"]').setValue('zhoumin')
    await page.get('input[placeholder="请输入密码"]').setValue('Wrong1234')
    await page.get('input[placeholder="请输入右侧字符"]').setValue('ABCD')
    await page.findAll('button').find((item) => item.text() === '登录')!.trigger('click')
    await flushPromises()
    expect(api.post).toHaveBeenCalledTimes(1)
    expect(api.get).toHaveBeenCalledTimes(2)
    expect((page.get('input[placeholder="请输入右侧字符"]').element as HTMLInputElement).value).toBe('')
    expect(page.text()).toContain('账号或密码不正确')
    page.unmount()
  })

  it('opens the password form for a restored session and exits through server logout', async () => {
    const admin = useAdminStore()
    admin.applyLogin({ admin: { username: 'zhoumin' }, must_change_password: true })
    const page = await loginPage()
    expect(page.text()).toContain('首次登录请修改密码')
    expect(api.get).not.toHaveBeenCalled()
    await page.findAll('button').find((item) => item.text() === '退出登录')!.trigger('click')
    await flushPromises()
    expect(api.post).toHaveBeenCalledWith('/auth/logout')
    expect(admin.loaded).toBe(false)
    expect(page.text()).not.toContain('首次登录请修改密码')
    expect(api.get).toHaveBeenCalledWith('/auth/captcha')
    page.unmount()
  })
})
