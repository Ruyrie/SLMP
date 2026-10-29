import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'

/**
 * 当前缓存归属：用户 + 养殖场 + 切场序号 + 角色。任一变化都说明 store 里的数据不再属于当前视角。
 * 切场序号在切换养殖场、进出“全部养殖场”视角时都会加一。
 */
export function currentFarmScope(): string {
  const farm = useFarmStore()
  return `${useAuthStore().user?.user_id || 0}:${farm.currentFarmId}:${farm.revision}:${farm.role}`
}

interface FarmScopedStore {
  farmScope: string
  $state: object
  $reset(): void
  $patch(partial: Record<string, unknown>): void
}

/** 请求序号类字段：清空缓存时保持递增，避免清空前发出的旧请求与新请求撞号后把旧场数据写回。 */
const COUNTER_KEY = /(request|seq)/i

/** 清空 store 但保留请求序号。 */
export function resetFarmCache(store: Omit<FarmScopedStore, 'farmScope'>): void {
  const counters: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(store.$state)) {
    if (typeof value === 'number' && COUNTER_KEY.test(key)) counters[key] = value
  }
  store.$reset()
  if (Object.keys(counters).length) store.$patch(counters)
}

/**
 * 页面未挂载时也可能切场：store 在每次使用缓存前核对归属，不属于当前用户 / 养殖场就整体清空。
 * 返回当前归属，异步请求回来后再与 currentFarmScope() 比对，切场后的旧响应直接丢弃。
 */
export function claimFarmScope(store: FarmScopedStore): string {
  const scope = currentFarmScope()
  if (store.farmScope !== scope) {
    // 首次认领（还没有按任何养殖场加载过）只记下归属，不清空页面先行写入的筛选、批次等状态
    if (store.farmScope) resetFarmCache(store)
    store.farmScope = scope
  }
  return scope
}
