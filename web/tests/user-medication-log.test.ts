import { describe, expect, it } from 'vitest'
import { healthLogItems } from '../apps/user/src/stores/health/shared'

describe('medication archive audit presentation', () => {
  it('renders the shared API Chinese labels, units and actual previous values', () => {
    const items = healthLogItems([{
      op_action: 'UPDATE',
      operator: { name: '王建国' },
      changes: [
        { field_name: '实际用量', before: '500 克', after: '555 克' },
        { field_name: '疗程', before: '5 天', after: '6 天' }
      ]
    }], 'MEDICATION_RECORD')
    expect(items[0].title).toBe('修改用药记录')
    expect(items[0].desc).toContain('实际用量：500 克 → 555 克')
    expect(items[0].desc).toContain('疗程：5 天 → 6 天')
    expect(items[0].desc).not.toContain('used_qty')
  })

  it('preserves unknown historical values instead of presenting them as empty', () => {
    const items = healthLogItems([{
      op_action: 'UPDATE',
      changes: [{ field_name: '实际用量', before: '未记录', after: '555' }]
    }, { op_action: 'CREATE' }], 'MEDICATION_RECORD')
    expect(items[0].desc).toContain('实际用量：未记录 → 555')
    expect(items[1].title).toBe('登记用药')
  })
})
