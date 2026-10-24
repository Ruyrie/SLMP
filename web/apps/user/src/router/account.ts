import { pageRoute } from './helpers'

export const accountRoutes = [
  pageRoute('WU-A01', '/login', '登录', () => import('@/views/account/LoginView.vue'), {
    public: true,
    layout: 'blank',
    needsFarm: false,
    crumbs: []
  }),
  pageRoute('WU-A02', '/register', '注册与保存恢复码', () => import('@/views/account/RegisterView.vue'), {
    public: true,
    layout: 'blank',
    needsFarm: false
  }),
  pageRoute('WU-A03', '/recover', '找回密码与账号', () => import('@/views/account/RecoverView.vue'), {
    public: true,
    layout: 'blank',
    needsFarm: false
  }),
  pageRoute('WU-A04', '/account', '个人中心与账号安全', () => import('@/views/account/AccountView.vue'), {
    needsFarm: false
  })
]
