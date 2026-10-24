import { pageRoute } from './helpers'

export const inspectRoutes = [
  pageRoute('WU-I01', '/inspect/today', '今日巡检', () => import('@/views/inspect/InspectionTodayView.vue'), {
    crumbs: ['防疫健康', '日常巡检']
  }),
  pageRoute('WU-I02', '/inspect/history', '巡检历史', () => import('@/views/inspect/InspectionHistoryView.vue'), {
    crumbs: ['防疫健康', '日常巡检']
  }),
  pageRoute('WU-I03', '/inspect/reports', '异常上报', () => import('@/views/inspect/AbnormalReportView.vue'), {
    crumbs: ['防疫健康', '异常与诊断']
  }),
  pageRoute('WU-I04', '/inspect/reports/:id', '诊断详情', () => import('@/views/inspect/DiagnosisDetailView.vue'), {
    crumbs: ['防疫健康', '异常与诊断']
  })
]
