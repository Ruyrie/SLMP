import { adminRoute } from './helpers'

export const contentRoutes = [
  adminRoute('WA-C01', '/content/notices', '通知发布', () => import('@/views/content/NoticesView.vue'), { perm: 'WA-C01:VIEW', crumbs: ['内容运营', '通知发布'] }),
  adminRoute('WA-C02', '/content/market', '市场行情发布', () => import('@/views/content/MarketAdminView.vue'), { perm: 'WA-C02:VIEW', crumbs: ['内容运营', '市场行情发布'] }),
  adminRoute('WA-C03', '/content/courses', '养殖课堂管理', () => import('@/views/content/CoursesAdminView.vue'), { perm: 'WA-C03:VIEW', crumbs: ['内容运营', '养殖课堂管理'] }),
  adminRoute('WA-C04', '/content/knowledge', '知识库管理', () => import('@/views/content/KnowledgeAdminView.vue'), { perm: 'WA-C04:VIEW', crumbs: ['内容运营', '知识库管理'] }),
  adminRoute('WA-C05', '/content/ai-qa', 'AI 问答与更正', () => import('@/views/content/AiQaView.vue'), { perm: 'WA-C05:VIEW', crumbs: ['内容运营', 'AI 问答与更正'] })
]
