import { beforeEach, describe, expect, it } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import { setupHttp } from '../apps/user/src/api/client'
import { financeApi } from '../apps/user/src/api/finance'

let requests: InternalAxiosRequestConfig[] = []
beforeEach(() => {
  requests = []
  const client = setupHttp({ baseURL: '/api/v1', client: 'WEB', getToken: () => 'test', getFarmId: () => 1001 })
  client.instance.defaults.adapter = async (config) => {
    requests.push(config)
    const data = config.url === '/sale-orders/batch-options'
      ? [{ batch_id: 301, batch_no: 'ZM-301', pen_name: '育肥舍', current_count: 30 }]
      : { id: 71, batch_id: 301, status: 'SUCCESS', conclusion_text: '继续饲养' }
    return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
  }
})

describe('market API contract', () => {
  it('reads the server batch array and converts snake_case ids and names', async () => {
    expect(await financeApi.batchOptions()).toEqual([{ batchId: 301, batchNo: 'ZM-301', penName: '育肥舍', currentCount: 30 }])
    expect(requests[0].url).toBe('/sale-orders/batch-options')
    expect(requests[0].headers.get('X-Farm-Id')).toBe('1001')
  })

  it('allows the server AI deadline while retaining finite request timeouts', async () => {
    expect((await financeApi.saleAdviceLatest(301)).conclusionText).toBe('继续饲养')
    await financeApi.regenerateSaleAdvice(301)
    // 服务端 120s 内返回、Nginx 150s：客户端超时不低于 150s（跨端约定）
    expect(requests.map((request) => request.timeout)).toEqual([155_000, 155_000])
    expect(requests[0].params).toEqual({ batch_id: 301 })
    expect(JSON.parse(requests[1].data)).toEqual({ batch_id: 301 })
  })
})
