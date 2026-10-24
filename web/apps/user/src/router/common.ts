import { pageRoute } from './helpers'

export const commonRoutes = [
  pageRoute('WU-U01', '/change-requests', '我的修改申请', () => import('@/views/common/ChangeRequestView.vue'), {
    needsFarm: false,
    roles: ['owner', 'manager']
  }),
  // 无权限、加载失败等状态页（守卫跳转 forbidden 时进入），不能再用“页面建设中”的占位页
  pageRoute('WU-U02', '/status/:type', '状态页', () => import('@/views/common/StatusView.vue'), { public: true, needsFarm: false }),
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
