import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createVuetify } from 'vuetify'
import BreedingDateField from '../apps/user/src/views/breeding/components/BreedingDateField.vue'
import { useBreedingPendingStore } from '../apps/user/src/stores/breeding/pending'
import { dueGroupLabel } from '../apps/user/src/stores/breeding/shared'

const api = vi.hoisted(() => ({ context: vi.fn(), pens: vi.fn(), create: vi.fn(), toast: vi.fn() }))
vi.mock('../apps/user/src/api/breeding', () => ({ breedingApi: { registerContext: api.context, createFarrowing: api.create } }))
vi.mock('../apps/user/src/api/herd', () => ({ herdApi: { penOptions: api.pens } }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: api.toast }) }))
const fields: ReturnType<typeof mount>[] = []
const ctx = (animalId = 4131, breedId = 1101) => ({ animal: { animalId }, offspringBreed: { breedId, categoryId: 101, breedName: '杜长大' }, defaultPen: { penId: 2002 } })
beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  api.context.mockResolvedValue(ctx())
  api.pens.mockResolvedValue([{ pen_id: 2002, capacity: 400, stock_count: 200, disabled: false }, { pen_id: 2007, disabled: true }])
  api.create.mockResolvedValue({ batchId: 123, batchNo: 'ZM2610-080' })
})
afterEach(() => fields.splice(0).forEach(view => view.unmount()))

async function form() {
  const store = useBreedingPendingStore()
  store.drawer = 'farrowing'
  store.farrowingOptions = [{ animalId: 4131, version: 0, earTag: '0131' }]
  Object.assign(store.farrowForm, { animalId: 4131, totalBorn: 40, liveBorn: 40, stillborn: 0 })
  await store.loadFarrowContext()
  vi.spyOn(store, 'refresh').mockResolvedValue()
  return store
}
describe('breeding registration regression', () => {
  it.each([0, 23])('keeps the selected calendar day from Date at %i:00', hour => {
    const view = mount(BreedingDateField, { global: { plugins: [createVuetify()], stubs: {
      VMenu: { template: '<div><slot name="activator" :props="{}" /><slot /></div>' }, VTextField: true,
      VDatePicker: { name: 'VDatePicker', emits: ['update:modelValue'], template: '<div />' }
    } } })
    fields.push(view)
    view.getComponent({ name: 'VDatePicker' }).vm.$emit('update:modelValue', new Date(2026, 9, 27, hour))
    expect(view.emitted('update:modelValue')).toEqual([['2026-10-27']])
  })
  it('uses the configured offspring category and creates a batch for 40 live piglets', async () => {
    const store = await form()
    expect(api.pens).toHaveBeenCalledWith({ categoryId: 101 })
    expect(store.farrowPens.map(p => p.pen_id)).toEqual([2002])
    expect(await store.submitFarrowing()).toBe(true)
    expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ offspring_breed_id: 1101, target_pen_id: 2002, live_born: 40, transfer_to_batch: true }))
    expect(api.toast).toHaveBeenCalledWith('已登记产仔，新批次 ZM2610-080 已入栏')
  })
  it('does not overwrite a newly selected sow with an older context response', async () => {
    let finishA!: (value: ReturnType<typeof ctx>) => void
    api.context.mockReturnValueOnce(new Promise(resolve => { finishA = resolve })).mockResolvedValueOnce(ctx(4132, 1102))
    const store = useBreedingPendingStore()
    store.drawer = 'farrowing'; store.farrowForm.animalId = 4131
    const a = store.loadFarrowContext()
    store.farrowForm.animalId = 4132
    await store.loadFarrowContext()
    finishA(ctx()); await a
    expect(store.farrowForm.offspringBreedId).toBe(1102)
    expect(store.farrowContext?.animal?.animalId).toBe(4132)
  })
  it('rejects fractional counts and unavailable pens before submitting', async () => {
    const store = await form()
    store.farrowForm.totalBorn = store.farrowForm.liveBorn = 40.5
    expect(await store.submitFarrowing()).toBe(false)
    expect(store.farrowErrors.total_born).toBeTruthy()
    store.farrowForm.totalBorn = store.farrowForm.liveBorn = 40
    store.farrowForm.targetPenId = 2007
    expect(await store.submitFarrowing()).toBe(false)
    expect(store.farrowErrors.target_pen_id).toBeTruthy()
    expect(api.create).not.toHaveBeenCalled()
  })
  it('does not require a target pen when automatic entry is off', async () => {
    const store = await form()
    store.farrowForm.transferToBatch = false; store.farrowForm.targetPenId = 0
    expect(await store.submitFarrowing()).toBe(true)
    expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ target_pen_id: undefined, offspring_breed_id: undefined, transfer_to_batch: false }))
  })
  it('reports empty status when there are no live piglets and automatic entry is off', async () => {
    const store = await form()
    Object.assign(store.farrowForm, { totalBorn: 2, liveBorn: 0, stillborn: 2, transferToBatch: false })
    api.create.mockResolvedValue({ animalStatus: 'EMPTY' })
    expect(await store.submitFarrowing()).toBe(true)
    expect(api.toast).toHaveBeenCalledWith('已登记产仔，0131 转为空怀')
  })
  it('labels overdue items separately from today', () => { expect(dueGroupLabel('OVERDUE')).toBe('已逾期') })
})
