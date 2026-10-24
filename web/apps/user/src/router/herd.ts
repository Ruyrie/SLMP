import { pageRoute } from './helpers'

export const herdRoutes = [
  pageRoute('WU-L01', '/herd/overview', '存栏总览', () => import('@/views/herd/StockOverviewView.vue'), { crumbs: ['生产管理', '存栏总览'] }),
  pageRoute('WU-L02', '/herd/pens', '栏舍管理', () => import('@/views/herd/PenListView.vue'), { crumbs: ['生产管理', '栏舍管理'] }),
  pageRoute('WU-L03', '/herd/batches', '批次列表与查询', () => import('@/views/herd/BatchListView.vue'), { crumbs: ['生产管理', '批次档案'] }),
  pageRoute('WU-L04', '/herd/batches/:batchId', '批次档案', () => import('@/views/herd/BatchDetailView.vue'), { crumbs: ['生产管理', '批次档案'] }),
  pageRoute('WU-L05', '/herd/entries', '批次入栏、转栏与死淘', () => import('@/views/herd/BatchEntryView.vue'), { crumbs: ['生产管理', '登记'] })
]
