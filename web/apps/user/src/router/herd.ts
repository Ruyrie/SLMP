import { pageRoute } from './helpers'

export const herdRoutes = [
  pageRoute('WU-L06', '/s/b/:token', '扫码查看批次', () => import('@/views/herd/BatchQrLinkView.vue'), {
    layout: 'blank', needsFarm: false, public: true, crumbs: []
  }),
  // 存栏总览、栏舍管理只对所有者和场长开放（服务端对员工返回 403），员工在路由层直接拦住，不再发请求
  pageRoute('WU-L01', '/herd/overview', '存栏总览', () => import('@/views/herd/StockOverviewView.vue'), {
    crumbs: ['生产管理', '存栏总览'], roles: ['owner', 'manager']
  }),
  pageRoute('WU-L02', '/herd/pens', '栏舍管理', () => import('@/views/herd/PenListView.vue'), {
    crumbs: ['生产管理', '栏舍管理'], roles: ['owner', 'manager']
  }),
  pageRoute('WU-L03', '/herd/batches', '批次列表与查询', () => import('@/views/herd/BatchListView.vue'), { crumbs: ['生产管理', '批次档案'] }),
  pageRoute('WU-L04', '/herd/batches/:batchId', '批次档案', () => import('@/views/herd/BatchDetailView.vue'), {
    crumbs: ['生产管理', '批次档案'], farmScopedParent: 'WU-L03'
  }),
  pageRoute('WU-L05', '/herd/entries', '批次入栏、转栏与死淘', () => import('@/views/herd/BatchEntryView.vue'), { crumbs: ['生产管理', '登记'] })
]
