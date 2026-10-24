import { pageRoute } from './helpers'

export const feedingRoutes = [
  pageRoute('WU-G01', '/feeding/tasks', '饲喂任务', () => import('@/views/feeding/FeedTaskView.vue'), { crumbs: ['生产管理', '饲喂管理'] }),
  pageRoute('WU-G02', '/feeding/plans', '饲喂方案与执行情况', () => import('@/views/feeding/FeedPlanView.vue'), { crumbs: ['生产管理', '饲喂管理'] }),
  pageRoute('WU-G03', '/feeding/weights', '称重与生长曲线', () => import('@/views/feeding/WeighView.vue'), { crumbs: ['生产管理', '称重与生长'] })
]
