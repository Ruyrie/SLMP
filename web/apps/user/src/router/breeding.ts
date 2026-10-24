import { pageRoute } from './helpers'

export const breedingRoutes = [
  pageRoute('WU-B01', '/breeding/animals', '种畜档案', () => import('@/views/breeding/BreedingAnimalView.vue'), { crumbs: ['生产管理', '繁育管理'] }),
  pageRoute('WU-B02', '/breeding/pending', '待产与登记', () => import('@/views/breeding/BreedingPendingView.vue'), { crumbs: ['生产管理', '繁育管理'] }),
  pageRoute('WU-B03', '/breeding/incubation', '禽类孵化', () => import('@/views/breeding/IncubationView.vue'), { crumbs: ['生产管理', '繁育管理'] })
]
