import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import AiMessageBlock from '../apps/user/src/views/ai/components/AiMessageBlock.vue'
import { useAiAssistantStore, type LocalAiMessage } from '../apps/user/src/stores/ai/assistant'

beforeEach(() => setActivePinia(createPinia()))

const render = (message: LocalAiMessage, streaming = false) => mount(AiMessageBlock, {
  props: { message, streaming, activeCitationId: null, retryDisabled: false },
  global: { stubs: { ZwIcon: true, ZwTag: true, VTooltip: true } }
})

describe('AI processing and source presentation', () => {
  it('expands real progress during generation and folds it when the answer completes', async () => {
    const message: LocalAiMessage = {
      id: 1, status: 'ANSWERING',
      processing_steps: [{ stage: 'CONTEXT', message: '正在读取问题上下文' }, { stage: 'RETRIEVAL', message: '正在检索平台资料' }],
      analysis_summary: '检索到 1 段相关资料。'
    }
    const wrapper = render(message, true)
    expect(wrapper.get('.k01-processing-toggle').attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('.k01-processing-detail').text()).toContain('正在检索平台资料')
    await wrapper.setProps({ message: { ...message, status: 'ANSWERED', answer_text: '检查饮水。' }, streaming: false })
    expect(wrapper.get('.k01-processing-toggle').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.k01-processing-detail').exists()).toBe(false)
    await wrapper.get('.k01-processing-toggle').trigger('click')
    expect(wrapper.get('.k01-processing-detail').text()).toContain('已检索平台资料')
  })

  it('keeps historical no-basis notices in the source area rather than bold answer text', () => {
    const wrapper = render({ id: 1, status: 'ANSWERED', answer_type: 'NO_BASIS', answer_text: '**未找到平台知识依据，仅供参考。**\n\n检查饮水。' })
    expect(wrapper.get('.k01-answer').text()).toBe('检查饮水。')
    expect(wrapper.get('.k01-source-note').text()).toContain('暂无可核验的平台资料引用')
  })

  it('preserves verified reference actions and does not invent a missing-source warning', async () => {
    const citation = { id: 91, seq_no: 1, knowledge_id: 81, title: '饮水管理' }
    const message = { id: 1, status: 'ANSWERED', answer_type: 'NORMAL', answer_text: '检查饮水[^1]。', citations: [citation] }
    const wrapper = render(message)
    expect(wrapper.find('.k01-source-note').exists()).toBe(false)
    await wrapper.get('.k01-cite').trigger('click')
    expect(wrapper.emitted('cite')?.[0]).toEqual([message, citation])
  })

  it('handles progress separately from answer deltas and retains it after done', () => {
    const store = useAiAssistantStore()
    vi.spyOn(store, 'loadSessions').mockResolvedValue()
    store.messages = [{ id: -1, status: 'ANSWERING', answer_text: '' }]
    store.streaming = { key: -1, messageId: 55, sessionId: 10, metaReceived: true, text: '', status: 'waiting', citations: [], startedAt: Date.now() }
    store.handleStreamEvent(-1, 'progress', { stage: 'RETRIEVAL', message: '正在检索平台资料' })
    store.handleStreamEvent(-1, 'analysis_summary', { text: '检索到 1 段相关平台资料。' })
    expect(store.messages[0].answer_text).toBe('')
    store.handleStreamEvent(-1, 'delta', { text: '检查饮水。' })
    store.handleStreamEvent(-1, 'done', { message: { id: 55, status: 'ANSWERED', answer_text: '检查饮水。', analysis_summary: '回答已整理完成。', citations: [] } })
    expect(store.messages[0].processing_steps).toEqual([{ stage: 'RETRIEVAL', message: '正在检索平台资料' }])
    expect(store.messages[0].analysis_summary).toBe('回答已整理完成。')
    expect(store.messages[0].answer_text).toBe('检查饮水。')
    expect(store.streaming).toBeNull()
  })
})
