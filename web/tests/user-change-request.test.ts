import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '@zmt/shared'
import { displayChangeField, useChangeRequestStore, VOID_FIELD, DELETE_FIELD } from '../apps/user/src/stores/common/changeRequest'
import type { ChangeRequestFormVO, ChangeRequestVO } from '../apps/user/src/api/change'

const mocks = vi.hoisted(() => ({
  detail: vi.fn(), form: vi.fn(), list: vi.fn(), submit: vi.fn(), toast: vi.fn(),
  farm: { currentFarmId: 1001, switchTo: vi.fn() }
}))
vi.mock('../apps/user/src/api/change', () => ({ getChangeRequestDetail: mocks.detail, getChangeRequestForm: mocks.form,
  listChangeRequests: mocks.list, submitChangeRequest: mocks.submit }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: mocks.toast }) }))
vi.mock('../apps/user/src/stores/farm', () => ({ useFarmStore: () => mocks.farm }))

const form: ChangeRequestFormVO = {
  biz_type: 'LOSS', biz_id: 73, record_version: '2', void_allowed: false,
  fields: [{ field_code: 'input_qty', field_name: '报废数量', input_type: 'NUMBER', value: '6', value_unit: '袋', min: 0, max: 6 },
    { field_code: 'executor_id', field_name: '执行人', input_type: 'SELECT', value: '11',
      options: [{ value: '11', label: '李明' }, { value: '12', label: '张伟' }, { value: '13', label: '已离职', disabled: true }] }]
}
const detail: ChangeRequestVO = { id: 42, biz_type: 'LOSS', biz_id: 73, farm_id: 1002, status: 'REJECTED',
  change_type: 'UPDATE', field_code: 'input_qty', after_value: '5', reason: '数量有误' }

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  mocks.farm.currentFarmId = 1001
  mocks.farm.switchTo.mockImplementation(async (id: number) => { mocks.farm.currentFarmId = id })
  mocks.form.mockResolvedValue(structuredClone(form))
  mocks.detail.mockResolvedValue(structuredClone(detail))
  mocks.list.mockResolvedValue({ list: [], total: 0, tab_counts: { ALL: 0 } })
  mocks.submit.mockResolvedValue({ ...detail, status: 'PENDING' })
})

describe('my change requests', () => {
  it('submits a delete-only medication application with a reason and no new value', async () => {
    mocks.form.mockResolvedValue({ biz_type: 'MEDICATION_RECORD', biz_id: 5101, record_version: '2', fields: [], delete_allowed: true, void_allowed: false })
    const store = useChangeRequestStore()
    await store.openCreate('MEDICATION_RECORD', 5101)
    expect(store.selectedField).toBe('')
    expect(mocks.submit).not.toHaveBeenCalled()
    store.selectField(DELETE_FIELD)
    expect(store.deleteSelected).toBe(true)
    await store.submit()
    expect(store.fieldErrors.reason).toBe('请填写修改原因')
    expect(store.fieldErrors.new_value).toBeUndefined()
    store.setReason('登记有误，审核删除后重新登记')
    await store.submit()
    expect(mocks.submit).toHaveBeenCalledWith(expect.objectContaining({ biz_type: 'MEDICATION_RECORD', biz_id: 5101,
      change_type: 'DELETE', field_code: null, new_value: null, record_version: '2' }), expect.any(String))
  })

  it('rejects deletion when the server has not allowed it', async () => {
    const store = useChangeRequestStore()
    await store.openCreate('LOSS', 73)
    store.selectField(DELETE_FIELD)
    store.setReason('误填')
    await store.submit()
    expect(store.fieldErrors.field_code).toBe('这条记录暂不能删除')
    expect(mocks.submit).not.toHaveBeenCalled()
  })

  it('preserves the DELETE operation when resubmitting a rejected application', async () => {
    mocks.detail.mockResolvedValue({ ...detail, biz_type: 'MEDICATION_RECORD', change_type: 'DELETE', field_code: null, after_value: null })
    mocks.form.mockResolvedValue({ ...form, biz_type: 'MEDICATION_RECORD', fields: [], delete_allowed: true })
    const store = useChangeRequestStore()
    await store.openResubmit(42)
    expect(store.deleteSelected).toBe(true)
    expect(store.newValue).toBe('')
    await store.submit()
    expect(mocks.submit).toHaveBeenCalledWith(expect.objectContaining({ change_type: 'DELETE', resubmit_of_id: 42 }), expect.any(String))
  })
  it('loading an empty list and opening a form never submits a request', async () => {
    const store = useChangeRequestStore()
    await store.fetchList()
    expect(store.status).toBe('empty')
    await store.openCreate('LOSS', 73)
    expect(store.form?.biz_id).toBe(73)
    expect(mocks.submit).not.toHaveBeenCalled()
  })

  it('only fetches a rejected request form after switching to its farm', async () => {
    const store = useChangeRequestStore()
    mocks.form.mockImplementation(async () => {
      expect(mocks.farm.currentFarmId).toBe(1002)
      return structuredClone(form)
    })
    await store.openResubmit(42)
    expect(mocks.farm.switchTo).toHaveBeenCalledWith(1002)
    expect(store.resubmitOfId).toBe(42)
    expect(store.newValue).toBe('5')
    expect(store.reason).toBe('数量有误')
    expect(mocks.submit).not.toHaveBeenCalled()
  })

  it('does not open or submit in the wrong farm when switching fails', async () => {
    const store = useChangeRequestStore()
    mocks.farm.switchTo.mockRejectedValue(new Error('切换失败'))
    await store.openResubmit(42)
    expect(store.submitOpen).toBe(false)
    expect(mocks.form).not.toHaveBeenCalled()
    expect(mocks.submit).not.toHaveBeenCalled()
  })

  it('does not resubmit an approved request or continue during another farm switch', async () => {
    const store = useChangeRequestStore()
    mocks.detail.mockResolvedValueOnce({ ...detail, status: 'APPROVED' })
    await store.openResubmit(42)
    expect(mocks.form).not.toHaveBeenCalled()
    mocks.farm.switchTo.mockResolvedValue(undefined)
    await store.openResubmit(42)
    expect(store.submitOpen).toBe(false)
    expect(mocks.form).not.toHaveBeenCalled()
  })

  it('displays names, sends selected ids and rejects unavailable choices', async () => {
    const store = useChangeRequestStore()
    await store.openCreate('LOSS', 73)
    store.selectField('executor_id')
    expect(displayChangeField(store.currentField)).toBe('李明')
    store.setReason('执行人有误')
    for (const invalid of ['13', '张伟', '999']) {
      store.setNewValue(invalid)
      expect(await store.submit()).toBeNull()
      expect(store.fieldErrors.new_value).toBe('请选择有效的新值')
    }
    expect(mocks.submit).not.toHaveBeenCalled()
    store.setNewValue('12')
    await store.submit()
    expect(mocks.submit.mock.calls[0][0].new_value).toBe('12')
  })

  it('blocks unchanged quantities, stale fields and disallowed void requests', async () => {
    const store = useChangeRequestStore()
    await store.openCreate('LOSS', 73)
    store.selectField('input_qty')
    store.setReason('误填')
    store.setNewValue('6.000')
    await store.submit()
    expect(store.fieldErrors.new_value).toContain('相同')
    store.selectField('missing')
    store.setNewValue('1')
    await store.submit()
    expect(store.fieldErrors.field_code).toBe('请选择要修改的项目')
    store.selectField(VOID_FIELD)
    await store.submit()
    expect(store.fieldErrors.field_code).toBe('这条记录暂不能作废')
    expect(mocks.submit).not.toHaveBeenCalled()
  })

  it('reuses a key only for the unchanged request after a network failure', async () => {
    const store = useChangeRequestStore()
    await store.openCreate('LOSS', 73)
    store.selectField('input_qty')
    store.setNewValue('5')
    store.setReason('误填')
    mocks.submit.mockRejectedValue(new ApiError({ code: -1, message: 'timeout' }))
    await store.submit()
    await store.submit()
    expect(mocks.submit.mock.calls[0][1]).toBe(mocks.submit.mock.calls[1][1])
    store.setNewValue('4')
    await store.submit()
    expect(mocks.submit.mock.calls[2][1]).not.toBe(mocks.submit.mock.calls[1][1])
    expect(mocks.submit.mock.calls[2][0].new_value).toBe('4')
  })

  it('ignores the late detail result from an earlier selection', async () => {
    const store = useChangeRequestStore()
    let finish!: (value: ChangeRequestVO) => void
    mocks.detail.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const first = store.openDetail(42)
    mocks.detail.mockResolvedValueOnce({ ...detail, id: 43 })
    await store.openDetail(43)
    finish(detail)
    await first
    expect(store.detail?.id).toBe(43)
    store.closeDetail()
    expect(store.detail).toBeNull()
  })

  it('does not restore a form after the user closes it while loading', async () => {
    const store = useChangeRequestStore()
    let finish!: (value: ChangeRequestFormVO) => void
    mocks.form.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const loading = store.openCreate('LOSS', 73)
    store.discard()
    finish(form)
    await loading
    expect(store.submitOpen).toBe(false)
    expect(store.form).toBeNull()
  })
})
