<script setup lang="ts">
import { computed } from 'vue'
import type { CitationBrief } from '@/api/ai'
import type { LocalAiMessage } from '@/stores/ai/assistant'

const props = defineProps<{
  message: LocalAiMessage
  activeCitationId: number | null
  streaming: boolean
  retryDisabled: boolean
}>()

const emit = defineEmits<{
  (event: 'retry', messageId: number): void
  (event: 'rate', message: LocalAiMessage, rating: 'USEFUL' | 'USELESS'): void
  (event: 'copy', text: string): void
  (event: 'cite', message: LocalAiMessage, citation: CitationBrief): void
  (event: 'preview', url: string): void
}>()

interface Segment {
  type: 'text' | 'strong' | 'cite'
  text?: string
  seq?: number
}

const NO_BASIS_LEAD = '未找到平台知识依据，仅供参考。'

const images = computed(() => (props.message.images || []).filter((item) => item.url || item.thumb_url))
const answerText = computed(() => props.message.answer_text || '')
const isThinking = computed(() => props.message.status === 'ANSWERING' && props.streaming && !answerText.value)
const isStreamingText = computed(() => props.message.status === 'ANSWERING' && props.streaming && !!answerText.value)
const isAnswered = computed(() => props.message.status === 'ANSWERED')
const isOffTopic = computed(() => props.message.answer_type === 'OFF_TOPIC')
const isBanned = computed(() => props.message.answer_type === 'BANNED_DRUG')
const showActions = computed(() => isAnswered.value && !!props.message.can_rate && !isOffTopic.value)
const showDeclaration = computed(
  () => isAnswered.value || (!!answerText.value && props.message.status !== 'ANSWERING')
)
const showText = computed(() => isAnswered.value || isStreamingText.value || !!answerText.value)
const stateText = computed(() => {
  if (props.message.local_stopped) return '已停止生成'
  const status = props.message.status
  if (status === 'TIMEOUT') return '回答超时，点击重新生成'
  if (status === 'INTERRUPTED' || status === 'ANSWERING') return '回答中断，点击重新生成'
  if (status === 'FAILED') {
    return answerText.value ? '回答中断，点击重新生成' : 'AI 助手暂时不可用，点击重新生成'
  }
  return '回答中断，点击重新生成'
})
const showStateRow = computed(
  () => !isAnswered.value && !props.streaming && !isThinking.value && props.message.status !== 'ANSWERED'
)
const retryable = computed(() => {
  if (props.message.local_stopped) return true
  return ['TIMEOUT', 'INTERRUPTED', 'FAILED', 'ANSWERING'].includes(props.message.status || '')
})
const retryTarget = computed(() => props.message.local_server_id || props.message.id)
const canRetry = computed(() => retryTarget.value > 0)

const paragraphs = computed<Segment[][]>(() => {
  const text = answerText.value.replace(/\r\n/g, '\n').trim()
  if (!text) return []
  const blocks = text.split(/\n{2,}/)
  const result: Segment[][] = []
  blocks.forEach((block, blockIndex) => {
    let rest = block
    const segments: Segment[] = []
    if (blockIndex === 0 && props.message.answer_type === 'NO_BASIS' && rest.startsWith(NO_BASIS_LEAD)) {
      segments.push({ type: 'strong', text: NO_BASIS_LEAD })
      rest = rest.slice(NO_BASIS_LEAD.length)
    }
    const pattern = /\[\^(\d+)]/g
    let last = 0
    let match: RegExpExecArray | null
    while ((match = pattern.exec(rest))) {
      if (match.index > last) segments.push({ type: 'text', text: rest.slice(last, match.index) })
      const after = match.index + match[0].length
      const punctuation = rest.slice(after, after + 1)
      const tail = /[。，、；：！？]/.test(punctuation) ? punctuation : ''
      segments.push({ type: 'cite', seq: Number(match[1]), text: tail })
      last = after + tail.length
    }
    if (last < rest.length) segments.push({ type: 'text', text: rest.slice(last) })
    result.push(segments.filter((item) => item.type === 'cite' || (item.text || '').length > 0))
  })
  return result
})

function citationOf(seq?: number): CitationBrief | undefined {
  if (!seq) return undefined
  return (props.message.citations || []).find((item) => item.seq_no === seq)
}

function sourceIcon(citation: CitationBrief): string {
  return citation.icon_type === 'ARTICLE' ? 'description' : 'menu_book'
}

function imageUrl(url?: string): string {
  return url || ''
}

function onCite(seq?: number): void {
  const citation = citationOf(seq)
  if (!citation) return
  emit('cite', props.message, citation)
}

function onRate(rating: 'USEFUL' | 'USELESS'): void {
  emit('rate', props.message, rating)
}
</script>

<template>
  <div class="k01-round" :data-message-id="message.id">
    <div class="k01-q">
      <div v-if="images.length" class="k01-q-imgs">
        <button
          v-for="(image, index) in images"
          :key="index"
          class="k01-q-img"
          type="button"
          aria-label="查看图片"
          @click="emit('preview', imageUrl(image.url || image.thumb_url))"
        >
          <img :src="imageUrl(image.url || image.thumb_url)" :alt="`附图 ${index + 1}`" />
        </button>
      </div>
      <div v-if="message.question_text" class="k01-q-bubble">{{ message.question_text }}</div>
    </div>

    <div class="k01-a">
      <span class="k01-ava" aria-hidden="true"><zw-icon name="auto_awesome" :size="18" fill /></span>
      <div class="k01-a-body">
        <div v-if="isThinking" class="k01-thinking">
          <span class="k01-dots"><i /><i /><i /></span>
          <span>正在思考…</span>
        </div>

        <template v-if="showText">
          <zw-tag v-if="isBanned" type="error" icon="block" class="k01-banned">禁用药，不能使用</zw-tag>
          <div class="k01-answer">
            <p v-for="(block, blockIndex) in paragraphs" :key="blockIndex">
              <template v-for="(segment, segmentIndex) in block" :key="segmentIndex">
                <b v-if="segment.type === 'strong'">{{ segment.text }}</b>
                <span v-else-if="segment.type === 'cite'" class="k01-cite-wrap">
                  <button
                    type="button"
                    class="k01-cite"
                    :class="{ on: activeCitationId === citationOf(segment.seq)?.id }"
                    :aria-label="`查看引用 ${segment.seq}`"
                    @click="onCite(segment.seq)"
                  >{{ segment.seq }}</button><span v-if="segment.text">{{ segment.text }}</span>
                </span>
                <span v-else>{{ segment.text }}</span>
              </template>
              <span v-if="isStreamingText && blockIndex === paragraphs.length - 1" class="k01-cursor" aria-hidden="true" />
            </p>
          </div>
        </template>

        <template v-if="showStateRow">
          <div v-if="message.local_stopped" class="k01-state k01-state--stopped">
            <span>已停止生成</span>
            <button type="button" class="k01-regen" :disabled="retryDisabled || !canRetry" @click="emit('retry', retryTarget)">
              <zw-icon name="refresh" :size="16" />重新生成
            </button>
          </div>
          <button
            v-else-if="retryable"
            type="button"
            class="k01-state k01-state--error"
            :disabled="retryDisabled || !canRetry"
            @click="emit('retry', retryTarget)"
          >
            <zw-icon name="error" :size="18" fill />
            <span>{{ stateText }}</span>
          </button>
        </template>

        <template v-if="isAnswered">
          <div v-if="message.citations?.length" class="k01-srcs">
            <button
              v-for="citation in message.citations"
              :key="citation.id"
              type="button"
              class="k01-src"
              :class="{ on: activeCitationId === citation.id }"
              @click="emit('cite', message, citation)"
            >
              <zw-icon :name="sourceIcon(citation)" :size="16" />
              {{ citation.seq_no }} {{ citation.title }}
            </button>
          </div>
        </template>

        <div v-if="showDeclaration" class="k01-decl">
          <zw-icon name="info" :size="14" />
          <span>由 AI 生成，仅供参考</span>
          <span v-if="showActions" class="k01-acts">
            <v-tooltip text="有用">
              <template #activator="{ props: tooltipProps }">
                <button
                  v-bind="tooltipProps"
                  class="k01-icon-btn"
                  :class="{ on: message.rating === 'USEFUL' }"
                  type="button"
                  aria-label="有用"
                  :aria-pressed="message.rating === 'USEFUL'"
                  @click="onRate('USEFUL')"
                >
                  <zw-icon name="thumb_up" :size="18" :fill="message.rating === 'USEFUL'" />
                </button>
              </template>
            </v-tooltip>
            <v-tooltip text="没用">
              <template #activator="{ props: tooltipProps }">
                <button
                  v-bind="tooltipProps"
                  class="k01-icon-btn"
                  :class="{ on: message.rating === 'USELESS' }"
                  type="button"
                  aria-label="没用"
                  :aria-pressed="message.rating === 'USELESS'"
                  @click="onRate('USELESS')"
                >
                  <zw-icon name="thumb_down" :size="18" :fill="message.rating === 'USELESS'" />
                </button>
              </template>
            </v-tooltip>
            <v-tooltip text="复制">
              <template #activator="{ props: tooltipProps }">
                <button v-bind="tooltipProps" class="k01-icon-btn" type="button" aria-label="复制" @click="emit('copy', answerText)">
                  <zw-icon name="content_copy" :size="18" />
                </button>
              </template>
            </v-tooltip>
          </span>
        </div>

        <div v-if="message.correction" class="k01-fix">
          <div class="k01-fix-title">
            <zw-icon name="verified" :size="18" fill />
            平台更正说明<template v-if="message.correction.display_time"> · {{ message.correction.display_time }}</template>
          </div>
          <div class="k01-fix-body">{{ message.correction.content }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.k01-round {
  padding-top: 4px;
}
.k01-q {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.k01-q-imgs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.k01-q-img {
  width: 56px;
  height: 56px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: zoom-in;
  background: var(--fill-3);
}
.k01-q-img img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.k01-q-bubble {
  max-width: 75%;
  padding: 9px 14px;
  border-radius: var(--radius-md);
  background: var(--fill-2);
  font-size: 14px;
  line-height: 22px;
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-word;
}
.k01-a {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
.k01-ava {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--tertiary-container);
  color: var(--tertiary);
}
.k01-a-body {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  line-height: 24px;
  padding-top: 2px;
  color: var(--text-1);
}
.k01-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 28px;
  font-size: 14px;
  color: var(--text-2);
}
.k01-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.k01-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-3);
  animation: k01-bounce 1.2s infinite ease-in-out;
}
.k01-dots i:nth-child(2) {
  animation-delay: 0.15s;
}
.k01-dots i:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes k01-bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-4px); }
}
.k01-banned {
  margin-bottom: 8px;
}
.k01-answer p {
  margin: 0 0 8px;
  white-space: pre-wrap;
  word-break: break-word;
}
.k01-answer b {
  font-weight: 600;
}
.k01-cite-wrap {
  display: inline;
  white-space: nowrap;
}
.k01-cite {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin: 0 2px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--fill-3);
  color: var(--text-2);
  font-size: 11px;
  line-height: 1;
  vertical-align: 2px;
  cursor: pointer;
  font-weight: 500;
}
.k01-cite.on {
  background: var(--primary);
  color: var(--on-primary);
}
.k01-cursor {
  display: inline-block;
  width: 2px;
  height: 16px;
  margin-left: 1px;
  background: var(--text-1);
  vertical-align: -3px;
  animation: k01-caret 1s step-end infinite;
}
@keyframes k01-caret {
  0%, 50% { opacity: 1; }
  50.01%, 100% { opacity: 0; }
}
.k01-srcs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 4px 0 10px;
}
.k01-src {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px 0 8px;
  border: 1px solid var(--line-2);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  font-size: 13px;
  line-height: 20px;
  color: var(--text-1);
  cursor: pointer;
  font-family: inherit;
}
.k01-src :deep(.zw-mso) {
  color: var(--primary);
}
.k01-src.on {
  border-color: var(--primary);
  background: var(--primary-subtle);
  color: var(--primary);
}
.k01-decl {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  margin-top: 2px;
}
.k01-acts {
  margin-left: auto;
  display: inline-flex;
  gap: 2px;
}
.k01-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
}
.k01-icon-btn:hover {
  background: var(--fill-2);
}
.k01-icon-btn.on {
  color: var(--primary);
}
.k01-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 14px;
  line-height: 24px;
  color: var(--error);
  background: transparent;
  border: 0;
  padding: 0;
  font-family: inherit;
}
button.k01-state {
  cursor: pointer;
}
button.k01-state:hover span {
  text-decoration: underline;
}
button.k01-state:disabled,
.k01-regen:disabled {
  opacity: 0.38;
  cursor: default;
}
.k01-state--stopped {
  color: var(--text-2);
}
.k01-regen {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--primary);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}
.k01-regen:hover {
  background: var(--primary-subtle);
}
.k01-fix {
  margin-top: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  border: 1px solid var(--line-1);
}
.k01-fix-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  line-height: 22px;
  font-weight: 600;
}
.k01-fix-title :deep(.zw-mso) {
  color: var(--primary);
}
.k01-fix-body {
  margin-top: 4px;
  font-size: 14px;
  line-height: 22px;
}
</style>
