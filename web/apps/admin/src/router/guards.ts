import type { Router } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

export function installGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const admin = useAdminStore()
    // Restore the Cookie session on both login and protected deep links.
    if (!admin.loaded && !admin.sessionChecked) {
      try {
        await admin.me()
      } catch {
        admin.reset()
      }
    }
    if (to.meta.public) {
      if (to.name === 'WA-S01' && admin.loaded && !admin.mustChangePassword) return { name: 'WA-S02' }
      return true
    }
    if (!admin.loaded) {
      return { name: 'WA-S01', query: { redirect: to.fullPath } }
    }
    if (admin.mustChangePassword && to.name !== 'WA-S01') {
      return { name: 'WA-S01', query: { redirect: to.fullPath } }
    }
    const perm = to.meta.perm as string | undefined
    if (perm && !admin.hasPerm(perm)) {
      return {
        name: 'WA-U02',
        params: { type: 'forbidden' },
        query: { reason: '你没有该页面的操作权限，如需处理请联系超级管理员', redirect: to.fullPath }
      }
    }
    return true
  })

  router.afterEach((to) => {
    const title = String(to.meta.title || '')
    document.title = title ? `${title} - 智牧通管理端` : '智牧通管理端'
  })
}

const CHUNK_RELOAD_KEY = 'zmt.admin.chunk-reload'

export function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '')
  return /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS/i.test(message)
}

/** Recover once when an open page still references chunks removed by a deployment. */
export function installChunkReload(router: Router, reload: (href: string) => void = (href) => window.location.assign(href)): void {
  router.onError((error, to) => {
    if (!isChunkLoadError(error)) return
    const href = router.resolve(to.fullPath).href
    try {
      if (sessionStorage.getItem(CHUNK_RELOAD_KEY) === href) return
      sessionStorage.setItem(CHUNK_RELOAD_KEY, href)
    } catch {
      // Without storage a reload cannot be bounded, so leave the original error visible.
      return
    }
    reload(href)
  })
  router.afterEach((_to, _from, failure) => {
    if (failure) return
    try {
      sessionStorage.removeItem(CHUNK_RELOAD_KEY)
    } catch {
      /* Storage may be unavailable in private browsing. */
    }
  })
}
