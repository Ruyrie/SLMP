import { describe, expect, it } from 'vitest'
import { setupHttp } from '../apps/user/src/api/client'
import { financeApi } from '../apps/user/src/api/finance'

describe('finance record modification history', () => {
  it('queries the same finance record and displays the server before/after fields', async () => {
    const client = setupHttp({ baseURL: '/api/v1', client: 'WEB', getFarmId: () => 1001 })
    client.instance.defaults.adapter = async (config) => {
      expect(config.url).toBe('/record-logs')
      expect(config.params).toEqual({ biz_type: 'FINANCE_RECORD', biz_id: 98001 })
      return {
        status: 200, statusText: 'OK', headers: {}, config,
        data: { code: 0, data: [{ id: 1, op_action: 'UPDATE', op_action_text: '修改', operator: { name: 'QA 操作人' },
          changes: [{ field_name: '金额', before: '18.00 元', after: '25.50 元' }], created_at: '2026-10-26 10:00:00' }] }
      }
    }
    const logs = await financeApi.recordLogs('FINANCE_RECORD', 98001)
    expect(logs).toHaveLength(1)
    expect(logs[0].opActionText).toBe('修改')
    expect(logs[0].changes).toEqual([{ fieldName: '金额', before: '18.00 元', after: '25.50 元' }])
    expect(logs[0].createdAt).toBe('2026-10-26 10:00:00')
  })
})
