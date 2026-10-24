import type { Router } from 'vue-router'
import { ApiError } from '@zmt/shared'
import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'

let bootPromise: Promise<void> | null = null

async function ensureSession(): Promise<void> {
  const auth = useAuthStore()
  if (auth.accessToken) return
  if (!bootPromise) {
    bootPromise = (async () => {
      const ok = await auth.refresh()
      if (!ok) throw new ApiError({ code: 40104, message: '登录已失效，请重新登录' })
      await auth.loadSession()
    })().finally(() => {
      bootPromise = null
    })
  }
  return bootPromise
}

export function installGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    const farm = useFarmStore()

    if (to.meta.public) {
      if (to.name === 'WU-A01' && auth.accessToken) {
        return { name: farm.role === 'staff' ? 'WU-T02' : 'WU-T01' }
      }
      // 状态页、404 带侧栏和顶栏：刷新后先尝试恢复会话，失败（未登录）也照常显示
      if (to.meta.layout === 'shell' && !auth.accessToken) {
        try {
          await ensureSession()
          if (!farm.loaded) await farm.load()
        } catch {
          /* 未登录也可以看状态页 */
        }
      }
      return true
    }

    try {
      await ensureSession()
      if (!farm.loaded) await farm.load()
    } catch {
      return { name: 'WU-A01', query: { redirect: to.fullPath } }
    }

    if (to.name === 'WU-T01' && farm.role === 'staff') return { name: 'WU-T02' }
    if (to.name === 'WU-T02' && farm.role !== 'staff') return { name: 'WU-T01' }

    if (to.meta.needsFarm !== false && !farm.currentFarmId) {
      return { name: 'WU-F01' }
    }

    if (farm.viewAll && !to.meta.allFarms) farm.setViewAll(false)

    const roles = to.meta.roles as string[] | undefined
    if (roles && !roles.includes(farm.role)) {
      return {
        name: 'WU-U02',
        params: { type: 'forbidden' },
        query: { reason: '你没有该页面的查看权限', redirect: to.fullPath }
      }
    }
    return true
  })

  router.afterEach((to) => {
    const title = String(to.meta.title || '')
    document.title = title ? `${title} - 智牧通` : '智牧通'
  })
}
