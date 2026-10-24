import { pageRoute } from './helpers'

export const farmRoutes = [
  pageRoute('WU-F01', '/farms', '我的养殖场', () => import('@/views/farm/FarmListView.vue'), { needsFarm: false }),
  pageRoute('WU-F02', '/farms/new', '新建养殖场与资料变更', () => import('@/views/farm/FarmCreateView.vue'), {
    needsFarm: false,
    crumbs: ['我的养殖场', '新建养殖场']
  }),
  pageRoute('WU-F03', '/farms/:farmId', '养殖场信息', () => import('@/views/farm/FarmDetailView.vue'), { needsFarm: false }),
  pageRoute('WU-F04', '/farms/:farmId/members', '成员管理', () => import('@/views/farm/FarmMembersView.vue'), { needsFarm: false })
]
