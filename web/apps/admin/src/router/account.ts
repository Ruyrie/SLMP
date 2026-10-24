import { adminRoute } from './helpers'

export const accountRoutes = [
  adminRoute('WA-S01', '/login', '管理端登录', () => import('@/views/system/AdminLoginView.vue'), {
    public: true,
    layout: 'blank',
    crumbs: []
  })
]
