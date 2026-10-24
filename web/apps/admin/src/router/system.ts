import { adminRoute } from './helpers'

export const systemRoutes = [
  adminRoute('WA-S02', '/', '平台概览', () => import('@/views/system/OverviewView.vue'), { perm: 'WA-S02:VIEW', crumbs: ['平台概览'] }),
  adminRoute('WA-S03', '/system/users', '用户账号', () => import('@/views/system/UsersView.vue'), {
    perm: 'WA-S03:VIEW',
    crumbs: ['用户与系统', '用户账号']
  }),
  adminRoute('WA-S04', '/system/farms', '养殖场管理', () => import('@/views/system/AdminFarmsView.vue'), {
    perm: 'WA-S04:VIEW',
    crumbs: ['用户与系统', '养殖场管理']
  }),
  adminRoute('WA-S05', '/system/admins', '管理员与权限', () => import('@/views/system/AdminsView.vue'), { perm: 'WA-S05:VIEW', crumbs: ['用户与系统', '管理员与权限'] }),
  adminRoute('WA-S06', '/system/logs', '操作日志', () => import('@/views/system/OperationLogsView.vue'), { perm: 'WA-S06:VIEW', crumbs: ['用户与系统', '操作日志'] }),
  adminRoute('WA-U02', '/status/:type', '状态页', () => import('@/views/common/StatusView.vue'), {
    public: true,
    crumbs: []
  }),
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/common/StatusView.vue'),
    meta: { page: 'WA-U02', title: '页面不存在', public: true, crumbs: [] }
  }
]
