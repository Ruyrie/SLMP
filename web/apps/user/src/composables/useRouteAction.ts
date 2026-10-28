import { nextTick, watch } from 'vue'
import { useRoute, useRouter, type LocationQuery, type RouteLocationNormalizedLoaded, type Router } from 'vue-router'

/**
 * 页面登记动作（?action=）的统一约定：
 * - create：打开本页的主登记表单（顶栏“快捷登记”、待办“去登记”都用它）；
 * - 页面原有的专用动作词（weigh、report、inbound、outbound、archive、mating、farrowing 等）继续可用。
 * 参数同时接受 camelCase 与待办去向里的 snake_case（taskId / task_id）。
 */
export function queryText(query: LocationQuery, ...keys: string[]): string {
  for (const key of keys) {
    const raw = query[key]
    const value = Array.isArray(raw) ? raw[0] : raw
    if (value !== null && value !== undefined && value !== '') return String(value)
  }
  return ''
}

export function queryId(query: LocationQuery, ...keys: string[]): number {
  const value = Number(queryText(query, ...keys))
  return Number.isFinite(value) && value > 0 ? Math.trunc(value) : 0
}

/**
 * 先从地址栏去掉 action（及 extraKeys），再返回原来的参数。
 * 必须在打开抽屉之前做：Vuetify 的临时抽屉（zw-drawer）在路由变化时会自动关闭，
 * 打开后再改地址栏会把刚打开的抽屉关掉。去掉后刷新不会重复弹出，再次点击快捷登记也能触发。
 */
export async function consumeRouteAction(
  route: RouteLocationNormalizedLoaded,
  router: Router,
  extraKeys: string[] = []
): Promise<LocationQuery> {
  const original = { ...route.query }
  const query = { ...route.query }
  let changed = false
  for (const key of ['action', ...extraKeys]) {
    if (key in query) {
      delete query[key]
      changed = true
    }
  }
  if (changed) {
    await router.replace({ query })
    await nextTick()
  }
  return original
}

/**
 * 处理 ?action=：页面首屏数据加载完成后调用 start() 执行一次；停留在本页时再次带 action 进入
 * （例如再点一次顶栏快捷登记）也会执行。
 */
export function useRouteAction(handler: (action: string, query: LocationQuery) => unknown) {
  const route = useRoute()
  const router = useRouter()
  let ready = false
  let running = false

  async function run(): Promise<void> {
    const action = queryText(route.query, 'action')
    if (!action || running) return
    running = true
    try {
      const query = await consumeRouteAction(route, router)
      await handler(action, query)
    } finally {
      running = false
    }
  }

  watch(
    () => route.query.action,
    (value) => {
      if (ready && value) void run()
    }
  )

  return {
    /** 首屏数据加载后调用。 */
    async start(): Promise<void> {
      ready = true
      await run()
    }
  }
}
