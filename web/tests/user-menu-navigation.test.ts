import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { menuRouteParams, missingRouteParams } from '../packages/shared/src/layouts/shell'

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'WU-T01', component: {} },
      { path: '/farms', name: 'WU-F01', component: {} },
      { path: '/farms/:farmId', name: 'WU-F03', component: {} },
      { path: '/farms/:farmId/members', name: 'WU-F04', component: {} }
    ]
  })
}

describe('侧边菜单带路径参数的路由', () => {
  it('为 /farms/:farmId 补当前养殖场编号', () => {
    expect(menuRouteParams('/farms/:farmId', 1001)).toEqual({ farmId: '1001' })
    expect(menuRouteParams('/farms/:farmId/members', 1001)).toEqual({ farmId: '1001' })
  })

  it('无参数路由和未知养殖场不产生参数', () => {
    expect(menuRouteParams('/farms', 1001)).toEqual({})
    expect(menuRouteParams('/farms/:farmId', 0)).toEqual({})
    expect(menuRouteParams(undefined, 1001)).toEqual({})
  })

  it('缺少 farmId 时能被识别并回退到养殖场列表', () => {
    const path = '/farms/:farmId'
    expect(missingRouteParams(path, {})).toEqual(['farmId'])
    expect(missingRouteParams(path, { farmId: '1001' })).toEqual([])
  })

  it('补上参数后 WU-F03 / WU-F04 都能正常跳转', async () => {
    const router = makeRouter()
    const farmId = 1001
    for (const name of ['WU-F03', 'WU-F04']) {
      const record = router.getRoutes().find((item) => item.name === name)
      await router.push({ name, params: menuRouteParams(record?.path, farmId) })
      expect(router.currentRoute.value.name).toBe(name)
      expect(router.currentRoute.value.params.farmId).toBe(String(farmId))
    }
  })

  it('不补参数时 vue-router 会直接抛出 Missing required param', () => {
    const router = makeRouter()
    expect(() => router.push({ name: 'WU-F03' })).toThrow(/Missing required param/)
  })
})
