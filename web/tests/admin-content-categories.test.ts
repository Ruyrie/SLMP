import { describe, expect, it } from 'vitest'
import type { CategoryNode } from '../apps/admin/src/api/base'
import { applicableCategoryOptions } from '../apps/admin/src/utils/contentCategories'

describe('knowledge and course applicable categories', () => {
  it('offers category ids for submission, including categories without any breeds', () => {
    const tree = [
      { id: 101, name: '育肥猪', status: 'ENABLED', breeds: [{ id: 1101, name: '杜长大', status: 'ENABLED' }, { id: 1102, name: '长白', status: 'ENABLED' }] },
      { id: 102, name: '种猪', status: 'DISABLED', breeds: [{ id: 1201, name: '长大二元', status: 'ENABLED' }] },
      { id: 103, name: '肉鸡', status: 'ENABLED', breeds: [] }
    ] as unknown as CategoryNode[]
    const selected = applicableCategoryOptions(tree)
    expect(selected).toEqual([{ title: '育肥猪', value: 101 }, { title: '肉鸡', value: 103 }])
    expect(selected.map(option => option.value)).not.toContain(1101)
    expect(selected.map(option => option.value)).not.toContain(102)
  })
  it('keeps an empty selection available for all categories', () => {
    expect(applicableCategoryOptions([])).toEqual([])
  })
})
