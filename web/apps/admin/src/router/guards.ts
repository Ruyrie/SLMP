import type { Router } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

export function installGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const admin = useAdminStore()
    if (to.meta.public) {
      if (to.name === 'WA-S01' && admin.loaded) return { name: 'WA-S02' }
      return true
    }
    if (!admin.loaded) {
      try {
        await admin.me()
      } catch {
        return { name: 'WA-S01', query: { redirect: to.fullPath } }
      }
    }
    if (admin.mustChangePassword && to.name !== 'WA-S01') {
      return { name: 'WA-S01' }
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
