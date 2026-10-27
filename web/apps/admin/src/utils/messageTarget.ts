import type { LocationQueryRaw, RouteLocationRaw, Router } from 'vue-router'
import type { AdminMessage } from '@/api/messages'

/** Resolve server page codes only against accessible routes in this admin app. */
export function messageTarget(
  message: AdminMessage,
  router: Router,
  hasPerm: (perm: string | undefined) => boolean
): RouteLocationRaw | null {
  if (!message.route_page) return null
  const record = router.getRoutes().find((item) => item.name === message.route_page)
  if (!record || record.meta.public || !hasPerm(record.meta.perm as string | undefined)) return null
  const query: LocationQueryRaw = {}
  for (const [key, value] of Object.entries(message.route_params || {})) {
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      query[key] = String(value)
    }
  }
  const target = { name: record.name!, query }
  try {
    router.resolve(target)
    return target
  } catch {
    return null
  }
}
