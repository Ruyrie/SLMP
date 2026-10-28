import { watch } from 'vue'
import { useRoute, useRouter, type LocationQuery } from 'vue-router'

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
 * 处理 ?action=：页面首屏数据加载完成后调用 start() 执行一次；停留在本页时再次带 action 进入
 * （例如再点一次顶栏快捷登记）也会执行。执行后从地址栏去掉 action，刷新不会重复弹出，再次点击也能触发。
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
    const name = route.name
    try {
      await handler(action, { ...route.query })
    } finally {
      running = false
    }
    if (route.name === name && route.query.action) {
      const query = { ...route.query }
      delete query.action
      await router.replace({ query })
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
