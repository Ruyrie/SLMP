import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import HerdDateField from '../apps/user/src/views/herd/components/HerdDateField.vue'

const wrappers: ReturnType<typeof mount>[] = []
function field() {
  const wrapper = mount(HerdDateField, { global: { plugins: [createVuetify()], stubs: {
    VMenu: { template: '<div><slot name="activator" :props="{}" /><slot /></div>' },
    VTextField: true,
    VDatePicker: { name: 'VDatePicker', emits: ['update:modelValue'], template: '<div />' }
  } } })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('herd date field', () => {
  it.each([0, 23])('accepts the date picker Date object at local hour %i without shifting day', hour => {
    const wrapper = field()
    wrapper.getComponent({ name: 'VDatePicker' }).vm.$emit('update:modelValue', new Date(2026, 9, 27, hour))
    expect(wrapper.emitted('update:modelValue')).toEqual([['2026-10-27']])
  })
  it('continues accepting ISO strings and clears an empty selection', () => {
    const wrapper = field()
    const picker = wrapper.getComponent({ name: 'VDatePicker' })
    picker.vm.$emit('update:modelValue', '2026-10-26')
    picker.vm.$emit('update:modelValue', null)
    expect(wrapper.emitted('update:modelValue')).toEqual([['2026-10-26'], ['']])
  })
})
