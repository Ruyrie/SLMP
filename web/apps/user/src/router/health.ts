import { pageRoute } from './helpers'

export const healthRoutes = [
  pageRoute('WU-D01', '/health/immune', '免疫计划', () => import('@/views/health/ImmunePlanView.vue'), { crumbs: ['防疫健康', '免疫计划'] }),
  pageRoute('WU-D02', '/health/medication', '用药与休药', () => import('@/views/health/MedicationView.vue'), { crumbs: ['防疫健康', '用药与休药'] }),
  pageRoute('WU-D03', '/health/disinfect', '消毒管理', () => import('@/views/health/DisinfectView.vue'), { crumbs: ['防疫健康', '消毒管理'] })
]
