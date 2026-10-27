import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { HttpClient } from '../packages/shared/src/request/http'
import { setupHttp } from '../apps/user/src/api/client'
import { fetchHome, fetchMessages, fetchUnreadCount, readAllMessages } from '../apps/user/src/api/today'
import { useNoticeStore } from '../apps/user/src/stores/notice'

let requests: InternalAxiosRequestConfig[]

function installAdapter(client: HttpClient) {
  client.instance.defaults.adapter = async (config) => {
    requests.push(config)
    return {
      status: 200, statusText: 'OK', headers: {}, config,
      data: { code: 0, data: { list: [], total_unread: 7, by_category: { NOTICE: 3 } } }
    }
  }
}

function transmittedFarm(config: InternalAxiosRequestConfig) {
  return AxiosHeaders.from(config.headers).toJSON()['X-Farm-Id']
}

beforeEach(() => {
  requests = []
  setActivePinia(createPinia())
  installAdapter(setupHttp({ baseURL: '/api/v1', client: 'WEB', getToken: () => 'test-token', getFarmId: () => 1002 }))
})

describe('message scopes through the real request interceptor', () => {
  it('continues to add the current farm for ordinary farm APIs', async () => {
    await fetchHome()
    expect(transmittedFarm(requests[0])).toBe('1002')
  })

  it('preserves an explicit case-insensitive farm override', async () => {
    const client = new HttpClient({ baseURL: '/api/v1', client: 'WEB', getFarmId: () => 1002 })
    installAdapter(client)
    await client.get('/home', undefined, { headers: { 'x-farm-id': '1001' } })
    expect(AxiosHeaders.from(requests[0].headers).get('X-Farm-Id')).toBe('1001')
  })

  it('shows all farms in the message list despite a different current farm', async () => {
    const controller = new AbortController()
    await fetchMessages({ category: 'NOTICE' }, controller.signal)
    expect(transmittedFarm(requests[0])).toBeUndefined()
    expect(requests[0].params.farm_id).toBeUndefined()
    expect(requests[0].signal).toBe(controller.signal)
    expect(requests[0].headers.get('Authorization')).toBe('Bearer test-token')
    expect(requests[0].headers.get('X-Client')).toBe('WEB')
  })

  it('uses the selected message filter instead of the top bar farm', async () => {
    await fetchMessages({ farm_id: 1001, unread_only: true })
    expect(transmittedFarm(requests[0])).toBeUndefined()
    expect(requests[0].params).toMatchObject({ farm_id: 1001, unread_only: true })
  })

  it('uses all farms for unread totals and the explicit farm for a filtered count', async () => {
    await fetchUnreadCount()
    await fetchUnreadCount({ farm_id: 1001, category: 'NOTICE' })
    expect(requests.map(transmittedFarm)).toEqual([undefined, undefined])
    expect(requests[1].params).toMatchObject({ farm_id: 1001, category: 'NOTICE' })
    await useNoticeStore().load()
    expect(transmittedFarm(requests[2])).toBeUndefined()
    expect(useNoticeStore().unread).toBe(7)
    expect(useNoticeStore().byCategory).toEqual({ NOTICE: 3 })
  })

  it('marks the same explicit all-farm or single-farm selection as read', async () => {
    await readAllMessages({ category: 'NOTICE' })
    await readAllMessages({ farm_id: 1001, category: 'NOTICE' })
    expect(requests.map(transmittedFarm)).toEqual([undefined, undefined])
    expect(JSON.parse(requests[0].data)).toEqual({ category: 'NOTICE' })
    expect(JSON.parse(requests[1].data)).toEqual({ farm_id: 1001, category: 'NOTICE' })
    expect(requests[0].headers.get('Idempotency-Key')).toBeTruthy()
  })

  it('keeps the all-farm header suppression after an access-token refresh', async () => {
    const refresh = vi.fn().mockResolvedValue(true)
    const client = new HttpClient({ baseURL: '/api/v1', client: 'WEB', getFarmId: () => 1002, refresh })
    client.instance.defaults.adapter = async (config) => {
      requests.push(config)
      if (requests.length === 1) {
        throw new AxiosError('expired', 'ERR_BAD_REQUEST', config, undefined, {
          status: 401, statusText: 'Unauthorized', headers: {}, config,
          data: { code: 40101, message: 'expired' }
        })
      }
      return { status: 200, statusText: 'OK', headers: {}, config, data: { code: 0, data: {} } }
    }
    await client.get('/messages', undefined, { headers: { 'X-Farm-Id': null } })
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(requests).toHaveLength(2)
    expect(requests.map(transmittedFarm)).toEqual([undefined, undefined])
  })
})
