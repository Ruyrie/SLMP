import { pageRoute } from './helpers'

export const aiRoutes = [
  pageRoute('WU-K01', '/ai', 'AI 养殖助手', () => import('@/views/ai/AiAssistantView.vue'), { crumbs: ['学习与助手', 'AI 养殖助手'] }),
  pageRoute('WU-K02', '/knowledge/:id', '知识原文', () => import('@/views/ai/KnowledgeView.vue'), { crumbs: ['学习与助手', 'AI 养殖助手', '知识原文'] }),
  pageRoute('WU-K03', '/courses', '养殖课堂', () => import('@/views/ai/CoursesView.vue'), { crumbs: ['学习与助手', '养殖课堂'] }),
  pageRoute('WU-K04', '/courses/:id', '视频播放', () => import('@/views/ai/CoursePlayerView.vue'), { crumbs: ['学习与助手', '养殖课堂'] })
]
