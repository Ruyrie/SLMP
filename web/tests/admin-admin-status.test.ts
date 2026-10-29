import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminStatus } from '../apps/admin/src/views/system/shared'

describe('管理员列表状态', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('正常且未锁定显示正常', () => {
    expect(adminStatus({ status: 'NORMAL', status_text: '正常', locked: false })).toEqual({ text: '正常', tone: 'success' })
  })

  it('被锁定显示锁到几点，不再显示正常', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 29, 14, 5))
    expect(adminStatus({ status: 'NORMAL', status_text: '正常', locked: true, locked_until: '2026-10-29 14:30:00' }))
      .toEqual({ text: '已锁定（至 14:30）', tone: 'warning' })
  })

  it('锁定到第二天带日期', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 29, 23, 50))
    expect(adminStatus({ status: 'NORMAL', locked: true, locked_until: '2026-10-30 00:20:00' }).text).toBe('已锁定（至 10月30日 00:20）')
  })

  it('停用优先于锁定', () => {
    expect(adminStatus({ status: 'DISABLED', status_text: '已停用', locked: true, locked_until: '2026-10-29 14:30:00' }))
      .toEqual({ text: '已停用', tone: 'error' })
  })
})
