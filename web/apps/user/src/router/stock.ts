import { pageRoute } from './helpers'

export const stockRoutes = [
  pageRoute('WU-M01', '/stock/feed', '饲料库存', () => import('@/views/stock/FeedStockView.vue'), {
    crumbs: ['物资库存', '饲料库存']
  }),
  pageRoute('WU-M02', '/stock/drugs', '兽药与疫苗库存', () => import('@/views/stock/DrugStockView.vue'), {
    crumbs: ['物资库存', '兽药与疫苗']
  }),
  pageRoute('WU-M03', '/stock/lots/:lotId', '批号详情与出入库流水', () => import('@/views/stock/DrugLotView.vue'), {
    crumbs: ['物资库存', '兽药与疫苗']
  })
]
