/** WU-K01 AI 养殖助手：历史会话、对话、流式回答、引用来源与关联批次。 */
import { defineStore } from 'pinia'
import { dayjs, readSse, TZ } from '@zmt/shared'
import {
  askUrl,
  authHeaders,
  changeSessionBatch,
  createSession as createSessionApi,
  deleteSession as deleteSessionApi,
  fetchKnowledge,
  fetchLinkableBatches,
  fetchMessage,
  fetchMessages,
  fetchSession,
  fetchSessions,
  newRequestKey,
  rateMessage,
  renameSession as renameSessionApi,
  retryUrl,
  type AiMessage,
  type AiRating,
  type AiSessionDetail,
  type AiSessionItem,
  type AskPayload,
  type CitationBrief,
  type KnowledgeDetail,
  type LinkedBatch,
  type PageStatus,
  type StreamCitation,
  type StreamDelta,
  type StreamDone,
  type StreamError,
  type StreamMeta,
  type StreamProgress
} from '@/api/ai'
import { useUiStore } from '@/stores/ui'
import { answerForCopy, errorTextOf, sseErrorMessage, statusForError } from './shared'

export interface LocalAiMessage extends AiMessage {
  processing_steps?: StreamProgress[]
  local_stopped?: boolean
  /** 新会话提问时，SSE meta 先返回服务端消息 ID；本地球泡在 done 前仍用负 ID。 */
  local_server_id?: number
}

export interface CitationDetailState {
  status: 'loading' | 'ready' | 'error'
  data?: KnowledgeDetail
  error?: string
}

interface StreamState {
  key: number
  messageId: number
  sessionId: number
  metaReceived: boolean
  text: string
  status: 'waiting' | 'streaming' | 'stopping'
  citations: CitationBrief[]
  startedAt: number
}

export interface AskInput {
  text: string
  images: import('@zmt/shared').FileVO[]
  contextCourseId?: number
}

export interface AskResult {
  ok: boolean
  error?: string
  restore?: boolean
}

export interface SessionGroup {
  label: string
  items: AiSessionItem[]
}

const NEW_CHAT_TITLE = '新对话'
const SESSION_TITLE_MAX = 40

function timeNow(): string {
  return dayjs().tz(TZ).format('YYYY-MM-DD HH:mm:ss')
}

export const useAiAssistantStore = defineStore('aiAssistant', {
  state: () => ({
    initialized: false,
    /* 左栏：历史会话 */
    sessions: [] as AiSessionItem[],
    sessionsTotal: 0,
    sessionsPage: 1,
    sessionsHasMore: false,
    sessionsStatus: 'loading' as PageStatus,
    sessionsError: '',
    sessionKeyword: '',
    sessionsLoadingMore: false,
    sessionsRequestId: 0,
    keywordTimer: 0,
    /* 会话管理（新建 / 重命名 / 删除） */
    creatingSession: false,
    renamingSessionId: null as number | null,
    deletingSessionId: null as number | null,
    sessionOpsToken: 0,
    /* 当前会话与对话 */
    currentSessionId: 0,
    conversationVersion: 0,
    session: null as AiSessionDetail | null,
    sessionStatus: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
    sessionError: '',
    messages: [] as LocalAiMessage[],
    messagesStatus: 'empty' as PageStatus,
    messagesError: '',
    messagesHasMore: false,
    messagesLoadingMore: false,
    sessionRequestId: 0,
    /* 关联批次 */
    batches: [] as LinkedBatch[],
    batchesStatus: 'loading' as PageStatus,
    batchesError: '',
    panelHint: '',
    emptyHint: '',
    localBatchId: null as number | null,
    batchChanging: false,
    /* 引用来源 */
    selectedMessageId: null as number | null,
    selectedCitationId: null as number | null,
    citationDetails: {} as Record<string, CitationDetailState>,
    /* 流式状态 */
    streaming: null as StreamState | null,
    abortController: null as AbortController | null,
    watchdog: 0,
    pendingAskResolve: null as ((result: AskResult) => void) | null,
    /* 页面提示 */
    notice: '',
    draft: '',
    contextCourse: null as { id: number; title: string } | null,
    scrollBottomSignal: 0,
    focusMessageId: null as number | null
  }),
  getters: {
    currentBatch(state): LinkedBatch | null {
      if (state.session && state.session.batch_link_state === 'VALID' && state.session.batch) {
        return state.session.batch
      }
      return state.batches.find((item) => item.batch_id === state.localBatchId) || null
    },
    currentBatchId(): number | undefined {
      return this.currentBatch?.batch_id
    },
    groupedSessions(state): SessionGroup[] {
      if (state.sessionKeyword.trim()) return [{ label: '', items: state.sessions }]
      const order = ['今天', '昨天', '近 7 天', '更早']
      const map = new Map<string, AiSessionItem[]>()
      for (const item of state.sessions) {
        const label = item.date_group_text || '更早'
        const list = map.get(label) || []
        list.push(item)
        map.set(label, list)
      }
      const result: SessionGroup[] = []
      for (const label of order) {
        const items = map.get(label)
        if (items?.length) result.push({ label, items })
      }
      return result
    },
    selectedMessage(state): LocalAiMessage | null {
      if (state.selectedMessageId) {
        const found = state.messages.find((item) => item.id === state.selectedMessageId)
        if (found) return found
      }
      for (let index = state.messages.length - 1; index >= 0; index -= 1) {
        if (state.messages[index].citations?.length) return state.messages[index]
      }
      return null
    },
    selectedCitations(): CitationBrief[] {
      return this.selectedMessage?.citations || []
    },
    hasEpidemic(state): boolean {
      return !!state.session?.has_epidemic_alert || state.messages.some((item) => item.answer_type === 'EPIDEMIC')
    },
    isStreaming(state): boolean {
      return !!state.streaming
    },
    activeCitationKey(state): string {
      return state.selectedMessageId && state.selectedCitationId
        ? `${state.selectedMessageId}:${state.selectedCitationId}`
        : ''
    }
  },
  actions: {
    /* ---------------- 初始化 ---------------- */
    async bootstrap(options: {
      sessionId?: number
      isNew?: boolean
      question?: string
      courseId?: number
      courseTitle?: string
      messageId?: number
    }): Promise<void> {
      this.draft = options.question || ''
      this.contextCourse = options.courseId
        ? { id: options.courseId, title: options.courseTitle || '' }
        : null
      this.initialized = false
      await Promise.all([this.loadBatches(), this.loadSessions()])
      if (options.isNew || options.courseId) {
        this.startNewConversation()
      } else if (options.sessionId) {
        await this.openSession(options.sessionId)
      } else if (this.sessions.length) {
        await this.openSession(this.sessions[0].id)
      } else {
        this.startNewConversation()
      }
      if (options.messageId) this.focusMessageId = options.messageId
      this.initialized = true
    },
    async loadBatches(): Promise<void> {
      if (!this.batches.length) this.batchesStatus = 'loading'
      this.batchesError = ''
      try {
        const data = await fetchLinkableBatches()
        this.batches = data.batches || []
        this.panelHint = data.panel_hint || ''
        this.emptyHint = data.empty_hint || ''
        this.batchesStatus = this.batches.length ? 'ready' : 'empty'
      } catch (error) {
        this.batchesStatus = statusForError(error)
        this.batchesError = errorTextOf(error, '批次加载失败')
      }
    },
    /* ---------------- 历史会话 ---------------- */
    async loadSessions(silent = false): Promise<void> {
      const requestId = ++this.sessionsRequestId
      if (!silent) this.sessionsStatus = this.sessions.length ? this.sessionsStatus : 'loading'
      this.sessionsError = ''
      try {
        const data = await fetchSessions({ page: 1, size: 20, keyword: this.sessionKeyword.trim() || undefined })
        if (requestId !== this.sessionsRequestId) return
        this.sessions = data.list || []
        this.sessionsTotal = data.total || 0
        this.sessionsPage = data.page || 1
        this.sessionsHasMore = !!data.has_more
        this.sessionsStatus = this.sessions.length ? 'ready' : 'empty'
      } catch (error) {
        if (requestId !== this.sessionsRequestId) return
        if (!silent) {
          this.sessionsStatus = statusForError(error)
          this.sessionsError = errorTextOf(error, '会话加载失败，请稍后重新加载')
        }
      }
    },
    setSessionKeyword(keyword: string): void {
      this.sessionKeyword = keyword
      if (this.keywordTimer) window.clearTimeout(this.keywordTimer)
      this.keywordTimer = window.setTimeout(() => {
        void this.loadSessions()
      }, 300)
    },
    async loadMoreSessions(): Promise<void> {
      if (this.sessionsLoadingMore || !this.sessionsHasMore) return
      const requestId = this.sessionsRequestId
      this.sessionsLoadingMore = true
      try {
        const nextPage = this.sessionsPage + 1
        const data = await fetchSessions({
          page: nextPage,
          size: 20,
          keyword: this.sessionKeyword.trim() || undefined
        })
        if (requestId !== this.sessionsRequestId) return
        const existing = new Set(this.sessions.map((item) => item.id))
        this.sessions = [...this.sessions, ...(data.list || []).filter((item) => !existing.has(item.id))]
        this.sessionsPage = data.page || nextPage
        this.sessionsHasMore = !!data.has_more
        this.sessionsTotal = data.total || this.sessionsTotal
      } catch (error) {
        if (requestId !== this.sessionsRequestId) return
        useUiStore().toast(errorTextOf(error, '加载更多会话失败'), 'error')
      } finally {
        this.sessionsLoadingMore = false
      }
    },
    /* ---------------- 会话管理（新建 / 重命名 / 删除） ---------------- */
    /** 左栏“新对话”：真实创建空会话，插入列表顶部并选中；失败保留原列表。 */
    async createSession(): Promise<AiSessionDetail | null> {
      if (this.creatingSession) return null
      const token = this.sessionOpsToken
      const keepBatch = this.currentBatch
      this.creatingSession = true
      try {
        const detail = await createSessionApi({})
        if (token !== this.sessionOpsToken) return null
        const title = detail.title || NEW_CHAT_TITLE
        const item: AiSessionItem = {
          id: detail.id,
          title,
          topic: 'OTHER',
          round_count: 0,
          last_asked_at: timeNow(),
          date_group: 'TODAY',
          date_group_text: '今天',
          has_correction: !!detail.has_correction,
          context_truncated: !!detail.context_truncated,
          has_epidemic_alert: !!detail.has_epidemic_alert
        }
        const hadKeyword = !!this.sessionKeyword.trim()
        if (this.keywordTimer) {
          window.clearTimeout(this.keywordTimer)
          this.keywordTimer = 0
        }
        this.sessionKeyword = ''
        // 丢弃仍在途的旧列表响应，避免覆盖刚创建的会话
        this.sessionsRequestId += 1
        this.startNewConversation()
        this.sessions = [item, ...this.sessions.filter((row) => row.id !== item.id)]
        this.sessionsTotal = hadKeyword ? this.sessions.length : this.sessionsTotal + 1
        this.sessionsStatus = 'ready'
        this.currentSessionId = item.id
        this.session = detail
        this.sessionStatus = 'ready'
        this.sessionError = ''
        this.applySessionBatch(detail)
        // 保留用户当前选择的关联批次（尚未写入空会话，首次提问时随消息关联）
        this.localBatchId = keepBatch?.batch_id ?? null
        useUiStore().toast('已开始新对话')
        void this.loadSessions(true)
        return detail
      } catch (error) {
        if (token === this.sessionOpsToken) {
          useUiStore().toast(errorTextOf(error, '新建会话失败，请稍后重试'), 'error')
        }
        return null
      } finally {
        this.creatingSession = false
      }
    },
    /** 重命名会话：成功返回空串，失败返回可展示的错误文案（同时 Snackbar）。 */
    async renameSession(sessionId: number, title: string): Promise<string> {
      if (!sessionId) return '会话不存在'
      if (this.renamingSessionId) return '正在保存，请稍候'
      const text = (title || '').trim()
      if (!text) return '标题不能为空'
      if (text.length > SESSION_TITLE_MAX) return '标题不能超过 40 字'
      const token = this.sessionOpsToken
      this.renamingSessionId = sessionId
      try {
        const detail = await renameSessionApi(sessionId, text)
        if (token !== this.sessionOpsToken) return ''
        const finalTitle = detail.title || text
        const item = this.sessions.find((row) => row.id === sessionId)
        if (item) item.title = finalTitle
        if (this.currentSessionId === sessionId && this.session) this.session.title = finalTitle
        this.sessionsRequestId += 1
        useUiStore().toast('已修改会话标题')
        return ''
      } catch (error) {
        const message = errorTextOf(error, '重命名失败，请稍后重试')
        if (token === this.sessionOpsToken) useUiStore().toast(message, 'error')
        return message
      } finally {
        if (this.renamingSessionId === sessionId) this.renamingSessionId = null
      }
    },
    /** 删除会话：成功移除；删的是当前会话时自动选中下一条，没有下一条则回到空态。 */
    async deleteSession(sessionId: number): Promise<boolean> {
      if (!sessionId || this.deletingSessionId) return false
      const token = this.sessionOpsToken
      this.deletingSessionId = sessionId
      try {
        await deleteSessionApi(sessionId)
        if (token !== this.sessionOpsToken) return false
        const wasCurrent = sessionId === this.currentSessionId
        const index = this.sessions.findIndex((row) => row.id === sessionId)
        this.sessionsRequestId += 1
        this.sessions = this.sessions.filter((row) => row.id !== sessionId)
        this.sessionsTotal = Math.max(0, this.sessionsTotal - 1)
        if (!this.sessions.length) {
          this.sessionsStatus = this.sessionKeyword.trim() ? 'filtered_empty' : 'empty'
        }
        if (wasCurrent) {
          if (this.abortController) this.abortController.abort()
          this.clearStream()
          this.resolveAsk({ ok: false })
          this.startNewConversation()
          const next = this.sessions[index] || this.sessions[index - 1] || this.sessions[0] || null
          if (next) await this.openSession(next.id)
        }
        useUiStore().toast('已删除会话')
        return true
      } catch (error) {
        if (token === this.sessionOpsToken) {
          useUiStore().toast(errorTextOf(error, '删除会话失败，请稍后重试'), 'error')
        }
        return false
      } finally {
        if (this.deletingSessionId === sessionId) this.deletingSessionId = null
      }
    },
    /* ---------------- 会话与对话 ---------------- */
    startNewConversation(): void {
      if (this.abortController) this.abortController.abort()
      this.clearStream()
      // 让仍在途的会话详情/消息响应失效，避免旧会话内容回填
      this.sessionRequestId += 1
      this.currentSessionId = 0
      this.session = null
      this.sessionStatus = 'idle'
      this.sessionError = ''
      this.messages = []
      this.messagesStatus = 'empty'
      this.messagesError = ''
      this.messagesHasMore = false
      this.selectedMessageId = null
      this.selectedCitationId = null
      this.notice = ''
      this.conversationVersion += 1
      this.scrollBottomSignal += 1
    },
    /** 仅在本地开启空对话，不请求服务端；左栏“新对话”已改用 createSession。 */
    newChat(): boolean {
      if (!this.currentSessionId && !this.messages.length) return false
      const keep = this.currentBatch
      const keepCourse = this.contextCourse
      this.startNewConversation()
      this.localBatchId = keep?.batch_id ?? null
      this.contextCourse = keepCourse
      return true
    },
    async openSession(sessionId: number): Promise<void> {
      if (!sessionId || sessionId === this.currentSessionId) return
      if (this.abortController) this.abortController.abort()
      this.clearStream()
      const requestId = ++this.sessionRequestId
      this.currentSessionId = sessionId
      this.sessionStatus = 'loading'
      this.sessionError = ''
      this.session = null
      this.messages = []
      this.messagesStatus = 'loading'
      this.messagesError = ''
      this.messagesHasMore = false
      this.selectedMessageId = null
      this.selectedCitationId = null
      this.notice = ''
      this.localBatchId = null
      this.focusMessageId = null
      this.conversationVersion += 1
      this.scrollBottomSignal += 1
      try {
        const [detail, slice] = await Promise.all([
          fetchSession(sessionId),
          fetchMessages(sessionId, { size: 20 })
        ])
        if (requestId !== this.sessionRequestId) return
        this.session = detail
        this.sessionStatus = 'ready'
        this.applySessionBatch(detail)
        this.messages = (slice.list || []).map((item) => ({ ...item }))
        this.messagesHasMore = !!slice.has_more
        this.messagesStatus = this.messages.length ? 'ready' : 'empty'
        this.selectDefaultCitation()
        if (detail.has_epidemic_alert) {
          // 会话详情已带回疫病标记，横幅不能关闭
        }
        this.scrollBottomSignal += 1
      } catch (error) {
        if (requestId !== this.sessionRequestId) return
        const status = statusForError(error)
        this.sessionStatus = 'error'
        this.sessionError = errorTextOf(error, '会话加载失败')
        this.messagesStatus = status
        this.messagesError = this.sessionError
      }
    },
    applySessionBatch(detail: AiSessionDetail): void {
      const state = detail.batch_link_state || 'NONE'
      if (state === 'VALID' && detail.batch) {
        this.localBatchId = detail.batch.batch_id
        this.notice = ''
      } else if (state === 'NOT_IN_STOCK' && detail.invalid_batch_no) {
        this.localBatchId = null
        this.notice = `${detail.invalid_batch_no} 已不在栏，之后的提问按通用养殖知识回答`
      } else if (state === 'NO_PERMISSION') {
        this.localBatchId = null
        this.notice = '你已不负责这个批次所在的栏舍，之后的提问不再带上该批次的数据'
      } else {
        this.localBatchId = null
      }
    },
    async loadMessages(): Promise<void> {
      if (!this.currentSessionId) return
      const requestId = this.sessionRequestId
      this.messagesStatus = 'loading'
      this.messagesError = ''
      try {
        const slice = await fetchMessages(this.currentSessionId, { size: 20 })
        if (requestId !== this.sessionRequestId) return
        this.messages = (slice.list || []).map((item) => ({ ...item }))
        this.messagesHasMore = !!slice.has_more
        this.messagesStatus = this.messages.length ? 'ready' : 'empty'
        this.selectDefaultCitation()
        this.scrollBottomSignal += 1
      } catch (error) {
        this.messagesStatus = statusForError(error)
        this.messagesError = errorTextOf(error, '对话加载失败')
      }
    },
    async loadSessionDetail(sessionId: number, applyBatch: boolean): Promise<void> {
      try {
        const detail = await fetchSession(sessionId)
        if (sessionId !== this.currentSessionId) return
        this.session = detail
        if (applyBatch) this.applySessionBatch(detail)
      } catch {
        /* 会话详情刷新失败不阻塞对话 */
      }
    },
    async reloadSession(): Promise<void> {
      const sessionId = this.currentSessionId
      if (!sessionId) return
      this.currentSessionId = 0
      await this.openSession(sessionId)
    },
    async loadEarlier(): Promise<number> {
      if (!this.currentSessionId || this.messagesLoadingMore || !this.messagesHasMore) return 0
      const first = this.messages[0]
      const before = first?.round_no
      if (!before) return 0
      this.messagesLoadingMore = true
      try {
        const slice = await fetchMessages(this.currentSessionId, { before_round_no: before, size: 20 })
        const existing = new Set(this.messages.map((item) => item.id))
        const earlier = (slice.list || []).filter((item) => !existing.has(item.id)).map((item) => ({ ...item }))
        this.messages = [...earlier, ...this.messages]
        this.messagesHasMore = !!slice.has_more
        return earlier.length
      } catch (error) {
        useUiStore().toast(errorTextOf(error, '加载更早的问答失败'), 'error')
        return 0
      } finally {
        this.messagesLoadingMore = false
      }
    },
    selectDefaultCitation(): void {
      for (let index = this.messages.length - 1; index >= 0; index -= 1) {
        const message = this.messages[index]
        const citation = message.citations?.[0]
        if (message.id > 0 && citation) {
          this.selectedMessageId = message.id
          this.selectedCitationId = citation.id
          void this.loadCitationDetail(citation)
          return
        }
      }
      this.selectedMessageId = null
      this.selectedCitationId = null
    },
    /* ---------------- 关联批次 ---------------- */
    async changeBatch(batchId: number | null): Promise<void> {
      if (this.batchChanging) return
      if (!this.currentSessionId) {
        this.localBatchId = batchId
        this.notice = ''
        return
      }
      if (this.currentBatchId === batchId) return
      this.batchChanging = true
      try {
        const detail = await changeSessionBatch(this.currentSessionId, batchId)
        this.session = detail
        this.applySessionBatch(detail)
        if (batchId) {
          const batch = detail.batch
          this.notice = `已改为关联 ${batch?.batch_no || ''}，之后的提问按这个批次回答`
        } else {
          this.notice = '已取消关联批次，之后的提问按通用养殖知识回答'
        }
        this.scrollBottomSignal += 1
      } catch (error) {
        useUiStore().toast(errorTextOf(error, '关联批次失败'), 'error')
        void this.loadSessionDetail(this.currentSessionId, true)
      } finally {
        this.batchChanging = false
      }
    },
    setContextCourse(courseId: number, title: string): void {
      this.contextCourse = { id: courseId, title }
    },
    clearContextCourse(): void {
      this.contextCourse = null
    },
    /* ---------------- 引用来源 ---------------- */
    citationKey(citation: CitationBrief): string {
      return `${citation.knowledge_id}:${citation.id}`
    },
    selectCitation(messageId: number, citation: CitationBrief): void {
      this.selectedMessageId = messageId
      this.selectedCitationId = citation.id
      void this.loadCitationDetail(citation)
    },
    async loadCitationDetail(citation: CitationBrief): Promise<void> {
      const key = this.citationKey(citation)
      const current = this.citationDetails[key]
      if (current && (current.status === 'loading' || current.status === 'ready')) return
      this.citationDetails[key] = { status: 'loading' }
      try {
        const data = await fetchKnowledge(citation.knowledge_id, citation.id)
        this.citationDetails[key] = { status: 'ready', data }
      } catch (error) {
        this.citationDetails[key] = { status: 'error', error: errorTextOf(error, '来源加载失败') }
      }
    },
    retryCitation(citation: CitationBrief): void {
      const key = this.citationKey(citation)
      delete this.citationDetails[key]
      void this.loadCitationDetail(citation)
    },
    /* ---------------- 提问与流式回答 ---------------- */
    async ask(input: AskInput): Promise<AskResult> {
      if (this.streaming) {
        const message = '上一个问题还在回答中，请稍后再问'
        useUiStore().toast(message, 'error')
        return { ok: false, error: message }
      }
      const text = (input.text || '').trim()
      const images = input.images || []
      if (!text && !images.length) return { ok: false }
      if ([...text].length > 500) {
        const message = '问题不能超过 500 字'
        useUiStore().toast(message, 'error')
        return { ok: false, error: message }
      }
      const controller = new AbortController()
      const key = -Date.now() - Math.floor(Math.random() * 1000)
      const roundNo = this.messages.reduce((max, item) => Math.max(max, item.round_no || 0), 0) + 1
      const batch = this.currentBatch
      const local: LocalAiMessage = {
        id: key,
        session_id: this.currentSessionId || undefined,
        round_no: roundNo,
        asked_at: timeNow(),
        question_text: text,
        images,
        batch_id: batch?.batch_id ?? null,
        batch_no: batch?.batch_no ?? null,
        status: 'ANSWERING',
        answer_type: null,
        answer_text: '',
        citations: [],
        can_rate: false,
        can_copy: false,
        in_context: true
      }
      this.messages.push(local)
      this.streaming = {
        key,
        messageId: 0,
        sessionId: this.currentSessionId,
        metaReceived: false,
        text: '',
        status: 'waiting',
        citations: [],
        startedAt: Date.now()
      }
      this.abortController = controller
      this.startWatchdog(key)
      this.scrollBottomSignal += 1
      const payload: AskPayload = {
        session_id: this.currentSessionId || undefined,
        question_text: text || undefined,
        image_file_ids: images.map((item) => Number(item.file_id)).filter((id) => !!id),
        batch_id: batch?.batch_id,
        context_course_id: input.contextCourseId
      }
      const promise = new Promise<AskResult>((resolve) => {
        this.pendingAskResolve = resolve
      })
      void this.runAsk(key, payload, controller)
      return promise
    },
    async runAsk(key: number, payload: AskPayload, controller: AbortController): Promise<void> {
      try {
        await readSse(
          askUrl(),
          {
            method: 'POST',
            headers: authHeaders({
              'Content-Type': 'application/json',
              Accept: 'text/event-stream',
              'Idempotency-Key': newRequestKey()
            }),
            body: JSON.stringify(payload)
          },
          {
            onEvent: (event, data) => {
              if (event === 'meta') this.resolveAsk({ ok: true })
              this.handleStreamEvent(key, event, data)
            }
          },
          controller.signal
        )
        if (this.streaming?.key === key) this.failRound(key, 'INTERRUPTED')
      } catch (error) {
        if ((error as Error)?.name === 'AbortError') return
        const current = this.streaming
        if (current?.key === key) {
          if (current.metaReceived) {
            this.failRound(key, 'INTERRUPTED')
          } else {
            this.removeLocalMessage(key)
            this.clearStream()
            const message = sseErrorMessage(error)
            this.resolveAsk({ ok: false, error: message, restore: true })
          }
        } else {
          this.resolveAsk({ ok: true })
        }
      }
    },
    streamMessage(key: number): LocalAiMessage | undefined {
      return this.messages.find((item) => item.id === key || item.local_server_id === key)
    },
    handleStreamEvent(key: number, event: string, data: unknown): void {
      const stream = this.streaming
      if (!stream || stream.key !== key) return
      const message = this.streamMessage(key)
      if (event === 'meta') {
        const meta = data as StreamMeta
        stream.metaReceived = true
        stream.messageId = meta.message_id
        stream.sessionId = meta.session_id
        if (message) {
          message.session_id = meta.session_id
          message.local_server_id = meta.message_id
        }
        if (this.contextCourse) this.contextCourse = null
        if (meta.session_id && meta.session_id !== this.currentSessionId) {
          this.currentSessionId = meta.session_id
          this.conversationVersion += 1
          void this.loadSessionDetail(meta.session_id, true)
        }
        if (meta.session_created) void this.loadSessions(true)
      } else if (event === 'progress') {
        const progress = data as StreamProgress
        if (message && progress?.stage && progress.message) {
          const steps = message.processing_steps || []
          message.processing_steps = [...steps.filter((item) => item.stage !== progress.stage), progress]
          this.scrollBottomSignal += 1
        }
      } else if (event === 'analysis_summary') {
        const summary = data as StreamDelta
        if (message && summary?.text) message.analysis_summary = summary.text
      } else if (event === 'delta') {
        const delta = data as StreamDelta
        const text = delta?.text || ''
        if (!text) return
        stream.text += text
        stream.status = 'streaming'
        if (message) message.answer_text = (message.answer_text || '') + text
        this.scrollBottomSignal += 1
      } else if (event === 'citation') {
        const payload = data as StreamCitation
        stream.citations = payload?.citations || []
        if (message) message.citations = stream.citations
      } else if (event === 'done') {
        this.completeRound(key, data as StreamDone)
      } else if (event === 'error') {
        const payload = data as StreamError
        const status = payload?.status || 'FAILED'
        this.failRound(key, status, payload?.message)
      }
    },
    completeRound(key: number, done: StreamDone): void {
      const stream = this.streaming
      if (!stream || stream.key !== key) return
      const index = this.messages.findIndex((item) => item.id === key || item.local_server_id === key)
      const final = done.message
      let answered: LocalAiMessage | null = null
      if (final) {
        answered = {
          ...final,
          citations: final.citations || stream.citations,
          processing_steps: index >= 0 ? this.messages[index].processing_steps : [],
          analysis_summary: final.analysis_summary || (index >= 0 ? this.messages[index].analysis_summary : null)
        }
        if (index >= 0) this.messages.splice(index, 1, answered)
        else this.messages.push(answered)
      } else if (index >= 0) {
        const current = this.messages[index]
        if (current.local_server_id) current.id = current.local_server_id
        current.status = 'ANSWERED'
        current.answer_type = done.answer_type || current.answer_type
        current.citations = stream.citations
        current.can_rate = true
        current.can_copy = true
        answered = current
      }
      if (answered) this.afterAnswer(answered)
      this.clearStream()
      this.scrollBottomSignal += 1
      this.resolveAsk({ ok: true })
      void this.loadSessions(true)
      if (this.currentSessionId) void this.loadSessionDetail(this.currentSessionId, false)
    },
    afterAnswer(message: LocalAiMessage): void {
      if (message.answer_type === 'EPIDEMIC' && this.session) this.session.has_epidemic_alert = true
      const citation = message.citations?.[0]
      if (message.id > 0 && citation) {
        this.selectedMessageId = message.id
        this.selectedCitationId = citation.id
        void this.loadCitationDetail(citation)
      }
    },
    failRound(key: number, status: string, message?: string): void {
      const stream = this.streaming
      if (!stream || stream.key !== key) {
        this.resolveAsk({ ok: true })
        return
      }
      const current = this.streamMessage(key)
      if (current) {
        current.status = status
        current.error_message = message || null
        current.can_rate = false
        current.can_copy = false
      }
      this.clearStream()
      this.scrollBottomSignal += 1
      this.resolveAsk({ ok: true })
    },
    stopStream(): void {
      const stream = this.streaming
      if (!stream) return
      this.abortController?.abort()
      const message = this.streamMessage(stream.key)
      if (message) {
        message.status = 'INTERRUPTED'
        message.local_stopped = true
        message.can_rate = false
        message.can_copy = false
      }
      this.clearStream()
      this.scrollBottomSignal += 1
      this.resolveAsk({ ok: true })
    },
    startWatchdog(key: number): void {
      if (this.watchdog) window.clearTimeout(this.watchdog)
      this.watchdog = window.setTimeout(() => {
        const stream = this.streaming
        if (!stream || stream.key !== key) return
        this.abortController?.abort()
        if (stream.metaReceived) {
          this.failRound(key, 'TIMEOUT')
        } else {
          this.removeLocalMessage(key)
          this.clearStream()
          this.resolveAsk({ ok: false, error: '回答超时，请稍后重试', restore: true })
        }
      }, 120000)
    },
    clearStream(): void {
      this.abortController = null
      this.streaming = null
      if (this.watchdog) {
        window.clearTimeout(this.watchdog)
        this.watchdog = 0
      }
    },
    resolveAsk(result: AskResult): void {
      const resolve = this.pendingAskResolve
      this.pendingAskResolve = null
      if (resolve) resolve(result)
    },
    removeLocalMessage(key: number): void {
      this.messages = this.messages.filter((item) => item.id !== key)
    },
    async retry(messageId: number): Promise<void> {
      if (!messageId || messageId < 1) return
      if (this.streaming) {
        useUiStore().toast('上一个问题还在回答中，请稍后再问', 'error')
        return
      }
      const message = this.messages.find((item) => item.id === messageId || item.local_server_id === messageId)
      if (!message) return
      message.status = 'ANSWERING'
      message.answer_type = null
      message.answer_text = ''
      message.analysis_summary = null
      message.source_note = null
      message.processing_steps = []
      message.citations = []
      message.rating = null
      message.error_message = null
      message.local_stopped = false
      message.can_rate = false
      message.can_copy = false
      const controller = new AbortController()
      this.streaming = {
        key: messageId,
        messageId,
        sessionId: this.currentSessionId,
        metaReceived: false,
        text: '',
        status: 'waiting',
        citations: [],
        startedAt: Date.now()
      }
      this.abortController = controller
      this.startWatchdog(messageId)
      this.scrollBottomSignal += 1
      try {
        await readSse(
          retryUrl(messageId),
          {
            method: 'POST',
            headers: authHeaders({
              'Content-Type': 'application/json',
              Accept: 'text/event-stream',
              'Idempotency-Key': newRequestKey()
            })
          },
          {
            onEvent: (event, data) => this.handleStreamEvent(messageId, event, data)
          },
          controller.signal
        )
        if (this.streaming?.key === messageId) this.failRound(messageId, 'INTERRUPTED')
      } catch (error) {
        if ((error as Error)?.name === 'AbortError') return
        if (this.streaming?.key === messageId) {
          if (this.streaming.metaReceived) {
            this.failRound(messageId, 'INTERRUPTED')
          } else {
            this.clearStream()
            useUiStore().toast(sseErrorMessage(error), 'error')
            void this.refreshMessage(messageId)
          }
        }
      }
    },
    async refreshMessage(messageId: number): Promise<void> {
      try {
        const message = await fetchMessage(messageId)
        const index = this.messages.findIndex((item) => item.id === messageId)
        if (index >= 0) this.messages.splice(index, 1, { ...message })
      } catch {
        /* 保持当前内容 */
      }
    },
    /* ---------------- 评价与复制 ---------------- */
    async rate(message: LocalAiMessage, rating: AiRating): Promise<void> {
      if (!message.can_rate || message.id < 1) return
      const previous = (message.rating || null) as AiRating
      const next: AiRating = previous === rating ? null : rating
      message.rating = next
      try {
        const result = await rateMessage(message.id, next)
        message.rating = (result.rating || null) as AiRating
        useUiStore().toast('已记录你的评价')
      } catch (error) {
        message.rating = previous
        useUiStore().toast(errorTextOf(error, '评价失败，请稍后重试'), 'error')
      }
    },
    async copyAnswer(text: string): Promise<void> {
      try {
        await navigator.clipboard.writeText(answerForCopy(text))
        useUiStore().toast('已复制回答')
      } catch {
        useUiStore().toast('复制失败，请手动选择文字复制', 'error')
      }
    },
    /* ---------------- 店铺切换与离开 ---------------- */
    resetForFarm(): void {
      if (this.abortController) this.abortController.abort()
      this.clearStream()
      this.resolveAsk({ ok: false })
      // 切换养殖场时作废在途的会话请求与操作，避免旧场数据回填
      this.sessionsRequestId += 1
      this.sessionRequestId += 1
      this.sessionOpsToken += 1
      this.creatingSession = false
      this.renamingSessionId = null
      this.deletingSessionId = null
      this.sessions = []
      this.sessionsTotal = 0
      this.sessionsPage = 1
      this.sessionsHasMore = false
      this.sessionsStatus = 'loading'
      this.sessionsError = ''
      this.sessionKeyword = ''
      this.batches = []
      this.batchesStatus = 'loading'
      this.batchesError = ''
      this.currentSessionId = 0
      this.session = null
      this.sessionStatus = 'idle'
      this.messages = []
      this.messagesStatus = 'empty'
      this.messagesHasMore = false
      this.selectedMessageId = null
      this.selectedCitationId = null
      this.citationDetails = {}
      this.localBatchId = null
      this.notice = ''
      this.contextCourse = null
      this.conversationVersion += 1
      this.initialized = false
    },
    leave(): void {
      if (this.abortController) this.abortController.abort()
      this.clearStream()
      this.resolveAsk({ ok: false })
    }
  }
})
