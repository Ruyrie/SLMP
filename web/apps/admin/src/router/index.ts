import { createRouter, createWebHistory } from 'vue-router'
import { accountRoutes } from './account'
import { auditRoutes } from './audit'
import { contentRoutes } from './content'
import { baseRoutes } from './base'
import { systemRoutes } from './system'
import { installGuards } from './guards'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [...accountRoutes, ...auditRoutes, ...contentRoutes, ...baseRoutes, ...systemRoutes],
  scrollBehavior: () => ({ top: 0 })
})

installGuards(router)

export default router
