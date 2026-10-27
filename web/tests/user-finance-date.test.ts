import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createVuetify } from 'vuetify'
import { today } from '@zmt/shared'
import FinanceDateField from '../apps/user/src/views/finance/components/FinanceDateField.vue'
import { useSaleOrderFormStore } from '../apps/user/src/stores/finance/saleOrderForm'

const mocks = vi.hoisted(() => ({ createSaleOrder: vi.fn(), updateSaleOrder: vi.fn(), toast: vi.fn() }))
vi.mock('../apps/user/src/api/finance', () => ({
  financeApi: { createSaleOrder: mocks.createSaleOrder, updateSaleOrder: mocks.updateSaleOrder },
  newRequestKey: () => 'date-regression'
}))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: mocks.toast }) }))

function field(props: Record<string, unknown> = {}) {
  return mount(FinanceDateField, {
    props,
    global: {
      plugins: [createVuetify()],
      stubs: {
        VMenu: { template: '<div><slot name="activator" :props="{}" /><slot /></div>' },
        VTextField: true,
        VDatePicker: { name: 'VDatePicker', props: ['modelValue', 'min', 'max'], emits: ['update:modelValue'], template: '<div />' }
      }
    }
  })
}

const fields: ReturnType<typeof field>[] = []
function mountField(props: Record<string, unknown> = {}) {
  const view = field(props)
  fields.push(view)
  return view
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  mocks.createSaleOrder.mockResolvedValue({ id: 1, remainingCount: 190 })
  mocks.updateSaleOrder.mockResolvedValue({ id: 1 })
})
afterEach(() => fields.splice(0).forEach(view => view.unmount()))

describe('finance date picker API values', () => {
  it.each([0, 23])('keeps the selected local calendar date at %i:00', async hour => {
    const view = mountField()
    view.getComponent({ name: 'VDatePicker' }).vm.$emit('update:modelValue', new Date(2026, 9, 30, hour))
    expect(view.emitted('update:modelValue')).toEqual([['2026-10-30']])
  })

  it('preserves ISO dates and optional empty values', () => {
    const view = mountField({ modelValue: '2026-10-30', min: '2026-10-27', max: '2026-11-01' })
    const picker = view.getComponent({ name: 'VDatePicker' })
    expect(picker.props()).toMatchObject({ modelValue: '2026-10-30', min: '2026-10-27', max: '2026-11-01' })
    picker.vm.$emit('update:modelValue', '2026-10-30')
    picker.vm.$emit('update:modelValue', null)
    expect(view.emitted('update:modelValue')).toEqual([['2026-10-30'], ['']])
  })

  it.each(['create', 'edit'] as const)('submits selected sale and due dates as date-only strings for %s', async mode => {
    const store = useSaleOrderFormStore()
    store.mode = mode
    store.editId = mode === 'edit' ? 1 : 0
    store.batches = [{ batchId: 1, currentCount: 200 }]
    Object.assign(store.form, { batchId: 1, quantity: 10, totalWeight: 1000, unitPrice: 15.6, buyerName: '测试收购商' })
    const [year, month, day] = today().split('-').map(Number)
    for (const key of ['saleDate', 'dueDate'] as const) {
      const view = mountField({ 'onUpdate:modelValue': (value: string) => { store.form[key] = value } })
      view.getComponent({ name: 'VDatePicker' }).vm.$emit('update:modelValue', new Date(year, month - 1, day))
    }
    const result = await store.submit()
    const payload = expect.objectContaining({ sale_date: today(), due_date: today() })
    if (mode === 'create') {
      expect(mocks.createSaleOrder).toHaveBeenCalledWith(payload, 'date-regression')
      expect(result.created?.id).toBe(1)
    } else {
      expect(mocks.updateSaleOrder).toHaveBeenCalledWith(1, payload)
      expect(result.detail?.id).toBe(1)
    }
    expect(store.errors).toEqual([])
  })
})
