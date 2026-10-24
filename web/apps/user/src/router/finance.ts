import { pageRoute } from './helpers'

const managerOnly = { roles: ['owner', 'manager'] as Array<'owner' | 'manager'> }

export const financeRoutes = [
  pageRoute('WU-E01', '/finance/overview', '经营概览', () => import('@/views/finance/FinanceOverviewView.vue'), {
    ...managerOnly,
    crumbs: ['经营管理', '经营概览']
  }),
  pageRoute('WU-E02', '/finance/records', '收支记账', () => import('@/views/finance/FinanceRecordsView.vue'), {
    ...managerOnly,
    allFarms: true,
    crumbs: ['经营管理', '收支记账']
  }),
  pageRoute('WU-E03', '/finance/sales', '出栏单', () => import('@/views/finance/SaleOrdersView.vue'), {
    ...managerOnly,
    crumbs: ['经营管理', '出栏单']
  }),
  pageRoute('WU-E04', '/finance/sales/:id', '出栏单详情与收款', () => import('@/views/finance/SaleOrderDetailView.vue'), {
    ...managerOnly,
    crumbs: ['经营管理', '出栏单']
  }),
  pageRoute('WU-E05', '/finance/settlements', '批次核算', () => import('@/views/finance/SettlementView.vue'), {
    ...managerOnly,
    crumbs: ['经营管理', '批次核算']
  }),
  pageRoute('WU-E06', '/finance/market', '市场行情与 AI 出栏建议', () => import('@/views/finance/MarketView.vue'), {
    ...managerOnly,
    crumbs: ['经营管理', '市场行情']
  }),
  pageRoute('WU-E07', '/finance/reports', '经营报表', () => import('@/views/finance/ReportsView.vue'), {
    ...managerOnly,
    allFarms: true,
    crumbs: ['经营管理', '经营报表']
  })
]
