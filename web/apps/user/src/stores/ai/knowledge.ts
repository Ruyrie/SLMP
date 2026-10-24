/** WU-K02 知识原文：正文段落、AI 引用标注、内嵌视频与相关条目。 */
import { defineStore } from 'pinia'
import {
  fetchKnowledge,
  fetchMessage,
  type AiMessage,
  type KnowledgeDetail,
  type PageStatus
} from '@/api/ai'
import { errorTextOf, isNotFound, isOffShelf, statusForError } from './shared'

export const useAiKnowledgeStore = defineStore('aiKnowledge', {
  state: () => ({
    knowledgeId: 0,
    citationId: 0,
    messageId: 0,
    status: 'loading' as PageStatus,
    error: '',
    offShelf: false,
    notFound: false,
    detail: null as KnowledgeDetail | null,
    message: null as AiMessage | null,
    messageStatus: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
    requestId: 0
  }),
  actions: {
    async load(knowledgeId: number, citationId = 0, messageId = 0): Promise<void> {
      const requestId = ++this.requestId
      this.knowledgeId = knowledgeId
      this.citationId = citationId
      this.messageId = messageId
      this.status = 'loading'
      this.error = ''
      this.offShelf = false
      this.notFound = false
      this.detail = null
      try {
        const detail = await fetchKnowledge(knowledgeId, citationId || undefined)
        if (requestId !== this.requestId) return
        this.detail = detail
        this.status = 'ready'
        if (messageId) void this.loadMessage(messageId)
      } catch (error) {
        if (requestId !== this.requestId) return
        this.offShelf = isOffShelf(error)
        this.notFound = isNotFound(error)
        this.status = statusForError(error)
        this.error = errorTextOf(error, '知识原文加载失败')
      }
    },
    async loadMessage(messageId: number): Promise<void> {
      this.messageStatus = 'loading'
      try {
        this.message = await fetchMessage(messageId)
        this.messageStatus = 'ready'
      } catch {
        this.message = null
        this.messageStatus = 'error'
      }
    },
    reset(): void {
      this.requestId += 1
      this.knowledgeId = 0
      this.citationId = 0
      this.messageId = 0
      this.status = 'loading'
      this.error = ''
      this.offShelf = false
      this.notFound = false
      this.detail = null
      this.message = null
      this.messageStatus = 'idle'
    }
  }
})
