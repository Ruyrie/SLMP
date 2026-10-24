import { pageRoute, placeholder } from './helpers'

export const commonRoutes = [
  pageRoute('WU-U01', '/change-requests', '我的修改申请', () => import('@/views/common/ChangeRequestView.vue'), {
    needsFarm: false,
    roles: ['owner', 'manager']
  }),
  pageRoute('WU-U02', '/status/:type', '状态页', placeholder, { public: true, needsFarm: false }),
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/common/StatusView.vue'),
    meta: {
      page: 'WU-U02',
      title: '页面不存在',
      public: true,
      needsFarm: false,
      layout: 'shell',
      crumbs: []
    }
  }
]
