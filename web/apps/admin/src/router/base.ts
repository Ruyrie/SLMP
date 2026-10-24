import { adminRoute } from './helpers'

export const baseRoutes = [
  adminRoute('WA-D01', '/base/categories', '品类品种与生长标准', () => import('@/views/base/CategoriesView.vue'), { perm: 'WA-D01:VIEW', crumbs: ['基础数据', '品类品种与生长标准'] }),
  adminRoute('WA-D02', '/base/immune-templates', '免疫程序模板', () => import('@/views/base/ImmuneTemplatesView.vue'), { perm: 'WA-D02:VIEW', crumbs: ['基础数据', '免疫程序模板'] }),
  adminRoute('WA-D03', '/base/drug-catalog', '兽药疫苗目录', () => import('@/views/base/DrugCatalogView.vue'), { perm: 'WA-D03:VIEW', crumbs: ['基础数据', '兽药疫苗目录'] }),
  adminRoute('WA-D04', '/base/inspect-templates', '巡检项目模板', () => import('@/views/base/InspectTemplatesView.vue'), { perm: 'WA-D04:VIEW', crumbs: ['基础数据', '巡检项目模板'] }),
  adminRoute('WA-D05', '/base/dicts', '其他字典', () => import('@/views/base/DictsView.vue'), { perm: 'WA-D05:VIEW', crumbs: ['基础数据', '其他字典'] })
]
