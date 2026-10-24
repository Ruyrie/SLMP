import { pageRoute } from './helpers'

export const todayRoutes = [
  pageRoute('WU-T01', '/', '工作台', () => import('@/views/today/WorkbenchView.vue'), {
    roles: ['owner', 'manager']
  }),
  pageRoute('WU-T02', '/workbench/staff', '工作台', () => import('@/views/today/WorkbenchStaffView.vue'), {
    roles: ['staff']
  }),
  pageRoute('WU-T03', '/calendar', '养殖日历', () => import('@/views/today/CalendarView.vue'), {
    allFarms: true
  }),
  pageRoute('WU-T04', '/messages', '消息中心', () => import('@/views/today/MessagesView.vue'), {
    allFarms: true
  })
]
