import { pageRoute } from './helpers'

export const farmRoutes = [
  pageRoute('WU-F01', '/farms', '我的养殖场', () => import('@/views/farm/FarmListView.vue'), { needsFarm: false }),
  pageRoute('WU-F02', '/farms/new', '新建养殖场与资料变更', () => import('@/views/farm/FarmCreateView.vue'), {
    needsFarm: false,
    crumbs: ['我的养殖场', '新建养殖场']
  }),
  // farmRoles：按地址里的养殖场判断角色（不是当前场），员工打开这两页直接拦住，不再发 403 请求
  pageRoute('WU-F03', '/farms/:farmId', '养殖场信息', () => import('@/views/farm/FarmDetailView.vue'), {
    needsFarm: false, farmRoles: ['owner', 'manager']
  }),
  pageRoute('WU-F04', '/farms/:farmId/members', '成员管理', () => import('@/views/farm/FarmMembersView.vue'), {
    needsFarm: false, farmRoles: ['owner', 'manager']
  })
]
