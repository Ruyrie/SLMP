import { describe, expect, it } from 'vitest'
import { setupHttp } from '../apps/user/src/api/client'
import { registerStockRefund, updateStockRefund } from '../apps/user/src/api/stock'

describe('stock refund API contract', () => {
  it('uses linked outbound refund endpoints and preserves Idempotency-Key for both writes', async () => {
    const client = setupHttp({ baseURL: '/api/v1', client: 'WEB', getFarmId: () => 1001 })
    const requests: Array<{ method?: string; url?: string; body: unknown; key: unknown; farm: unknown }> = []
    client.instance.defaults.adapter = async config => {
      requests.push({ method: config.method, url: config.url, body: JSON.parse(config.data), key: config.headers.get('Idempotency-Key'), farm: config.headers.get('X-Farm-Id') })
      return { status: 200, statusText: 'OK', headers: {}, config, data: { code: 0, data: { outbound_id: 73, refund_status: 'REFUNDED', finance_record_id: 703 } } }
    }
    const payload = { amount: 99.5, refund_date: '2026-10-27', remark: '已到账', version: 3 }
    expect((await registerStockRefund(73, payload, 'refund-73')).finance_record_id).toBe(703)
    await updateStockRefund(73, payload, 'correct-73')
    expect(requests).toEqual([
      { method: 'post', url: '/stock-outbounds/73/refund', body: payload, key: 'refund-73', farm: '1001' },
      { method: 'put', url: '/stock-outbounds/73/refund', body: payload, key: 'correct-73', farm: '1001' }
    ])
  })
})
