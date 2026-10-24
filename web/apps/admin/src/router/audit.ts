import { adminRoute } from './helpers'

export const auditRoutes = [
  adminRoute('WA-R01', '/audit/farms', '养殖场注册审核', () => import('@/views/audit/FarmAuditView.vue'), {
    perm: 'WA-R01:VIEW',
    crumbs: ['审核中心', '养殖场注册审核']
  }),
  adminRoute('WA-R02', '/audit/changes', '资料变更审核', () => import('@/views/audit/FarmChangeView.vue'), {
    perm: 'WA-R02:VIEW',
    crumbs: ['审核中心', '资料变更审核']
  }),
  adminRoute('WA-R03', '/audit/change-requests', '修改申请审核', () => import('@/views/audit/ChangeRequestView.vue'), {
    perm: 'WA-R03:VIEW',
    crumbs: ['审核中心', '修改申请审核']
  }),
  adminRoute('WA-R04', '/audit/recoveries', '账号找回', () => import('@/views/audit/RecoveryView.vue'), {
    perm: 'WA-R04:VIEW',
    crumbs: ['审核中心', '账号找回']
  })
]
