import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as audit from '../apps/admin/src/api/audit'
import * as system from '../apps/admin/src/api/system'
import { useFarmAuditStore } from '../apps/admin/src/stores/audit/farmAudit'
import { useFarmChangeStore } from '../apps/admin/src/stores/audit/farmChange'
import { useChangeRequestStore } from '../apps/admin/src/stores/audit/changeRequest'
import { useRecoveryStore } from '../apps/admin/src/stores/audit/recovery'
import { useOperationLogsStore } from '../apps/admin/src/stores/system/operationLogs'

vi.mock('../apps/admin/src/api/audit', async (original) => {
  const actual = await original<Record<string, unknown>>()
  return Object.fromEntries(Object.entries(actual).map(([key, value]) => [key, typeof value === 'function' ? vi.fn() : value]))
})
vi.mock('../apps/admin/src/api/system', async (original) => {
  const actual = await original<Record<string, unknown>>()
  return Object.fromEntries(Object.entries(actual).map(([key, value]) => [key, typeof value === 'function' ? vi.fn() : value]))
})

const emptyPage = { list: [], total: 0, page: 1, size: 20, has_more: false, tab_counts: {} }

function lastPage(fn: unknown): unknown {
  const calls = vi.mocked(fn as (...args: unknown[]) => unknown).mock.calls
  return (calls[calls.length - 1]?.[0] as { page?: number } | undefined)?.page
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  vi.mocked(audit.getFarmAudits).mockResolvedValue(emptyPage as never)
  vi.mocked(audit.getFarmChanges).mockResolvedValue(emptyPage as never)
  vi.mocked(audit.getChangeRequests).mockResolvedValue(emptyPage as never)
  vi.mocked(audit.getRecoveries).mockResolvedValue(emptyPage as never)
  vi.mocked(system.getOperationLogs).mockResolvedValue(emptyPage as never)
})

describe('审核中心与操作日志：改筛选回到第 1 页', () => {
  it('四个审核列表的 search() 先回到第 1 页再查询', async () => {
    const cases = [
      [useFarmAuditStore(), audit.getFarmAudits],
      [useFarmChangeStore(), audit.getFarmChanges],
      [useChangeRequestStore(), audit.getChangeRequests],
      [useRecoveryStore(), audit.getRecoveries]
    ] as const
    for (const [store, api] of cases) {
      store.page = 3
      store.search()
      await Promise.resolve()
      expect(store.page).toBe(1)
      expect(lastPage(api)).toBe(1)
    }
  })

  it('操作日志的 search() 也回到第 1 页', async () => {
    const store = useOperationLogsStore()
    store.query.page = 4
    store.query.keyword = '东山'
    store.search()
    await Promise.resolve()
    expect(store.query.page).toBe(1)
    expect(lastPage(system.getOperationLogs)).toBe(1)
  })
})
