import { watch } from 'vue'
import { useRoute, useRouter, type LocationQuery, type RouteLocationNormalizedLoaded, type Router } from 'vue-router'
import { useFarmStore } from '@/stores/farm'

/**
 * 地址栏里指向某个养殖场数据的编号（批次、栏舍、种畜、批号等）。
 * 切到别的养殖场后这些编号不属于新场，再按它们请求会 404，所以切场时去掉。
 */
export const FARM_SCOPED_QUERY_KEYS = [
  'batchId',
  'batch_id',
  'batch_no',
  'penId',
  'pen_id',
  'animalId',
  'animal_id',
  'incubationId',
  'recordId',
  'record_id',
  'lotId',
  'lot_id',
  'drugId',
  'drug_id',
  'inboundId'
]

export type FarmSwitchTarget = { name: string } | { query: LocationQuery }

/**
 * 切场后当前地址要换成什么：
 * - 详情页（路由 meta.farmScopedParent，如批次档案、出栏单详情）回到对应列表页；
 * - 带批次等编号参数的页面去掉这些参数，留在本页按新场重新加载；
 * - 不需要换时返回 null。
 */
export function farmSwitchTarget(route: Pick<RouteLocationNormalizedLoaded, 'meta' | 'query'>): FarmSwitchTarget | null {
  const parent = route.meta?.farmScopedParent
  if (typeof parent === 'string' && parent) return { name: parent }
  const query: LocationQuery = { ...route.query }
  let changed = false
  for (const key of FARM_SCOPED_QUERY_KEYS) {
    if (key in query) {
      delete query[key]
      changed = true
    }
  }
  return changed ? { query } : null
}

/**
 * 切换养殖场后重新加载页面数据：在页面 setup 中调用，传入加载函数。
 * 真正换了养殖场时先清理地址里属于旧场的批次等参数（详情页回到列表），再重新加载，
 * 避免按旧场的批次编号请求新场数据（404）。进出“全部养殖场”视角、刷新养殖场列表时直接重新加载。
 */
export function useFarmReload(reload: () => void | Promise<void>): void {
  const farm = useFarmStore()
  const route = useRoute() as RouteLocationNormalizedLoaded | undefined
  const router = useRouter() as Router | undefined
  let farmId = farm.currentFarmId
  watch(
    () => farm.revision,
    async () => {
      const switched = farmId !== 0 && farm.currentFarmId !== farmId
      farmId = farm.currentFarmId
      const target = switched && route && router ? farmSwitchTarget(route) : null
      if (target && router) {
        try {
          await router.replace(target)
        } catch {
          // 导航被打断（例如同时跳到了没有权限的提示页）时按原逻辑重新加载
        }
        // 已离开详情页：列表页挂载后自己按新场加载
        if ('name' in target) return
      }
      await reload()
    }
  )
}
