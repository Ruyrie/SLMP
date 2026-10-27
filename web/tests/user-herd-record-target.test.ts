import { describe, expect, it } from 'vitest'
import { stockRecordTarget } from '../apps/user/src/views/herd/recordTarget'

describe('batch record navigation', () => {
  it.each([['ENTRY', 'BATCH_ENTRY'], ['TRANSFER', 'BATCH_TRANSFER'], ['DEATH', 'BATCH_DEATH'], ['SALE', 'SALE_ORDER']])('opens %s with its business record id', (record_type, biz_type) => {
    expect(stockRecordTarget({id: 210216, biz_id: 210224, record_type, biz_type})).toEqual({biz_type, biz_id: '210224'})
  })
  it('normalizes legacy batch links but never substitutes a timeline id for a missing business id', () => {
    expect(stockRecordTarget({id: 1, biz_id: 7, record_type: 'ENTRY', biz_type: 'BATCH'})).toEqual({biz_type: 'BATCH_ENTRY', biz_id: '7'})
    expect(stockRecordTarget({id: 1, record_type: 'ENTRY'})).toBeNull()
    expect(stockRecordTarget({id: 1, biz_id: 7, record_type: 'FEED'})).toBeNull()
    expect(stockRecordTarget({id: 1, biz_id: 7, record_type: 'SALE', biz_type: 'BATCH_DEATH'})).toBeNull()
  })
})
