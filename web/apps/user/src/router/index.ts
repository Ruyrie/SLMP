import { createRouter, createWebHistory } from 'vue-router'
import { accountRoutes } from './account'
import { farmRoutes } from './farm'
import { todayRoutes } from './today'
import { herdRoutes } from './herd'
import { breedingRoutes } from './breeding'
import { feedingRoutes } from './feeding'
import { inspectRoutes } from './inspect'
import { healthRoutes } from './health'
import { stockRoutes } from './stock'
import { financeRoutes } from './finance'
import { aiRoutes } from './ai'
import { commonRoutes } from './common'
import { installGuards } from './guards'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    ...accountRoutes,
    ...farmRoutes,
    ...todayRoutes,
    ...herdRoutes,
    ...breedingRoutes,
    ...feedingRoutes,
    ...inspectRoutes,
    ...healthRoutes,
    ...stockRoutes,
    ...financeRoutes,
    ...aiRoutes,
    ...commonRoutes
  ],
  scrollBehavior: () => ({ top: 0 })
})

installGuards(router)

export default router
