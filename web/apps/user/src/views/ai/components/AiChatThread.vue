<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { dayjs, TZ } from '@zmt/shared'
import type { CitationBrief } from '@/api/ai'
import { useAiAssistantStore } from '@/stores/ai/assistant'
import type { LocalAiMessage } from '@/stores/ai/assistant'
import { isStreamingMessage } from '@/stores/ai/shared'
import AiMessageBlock from './AiMessageBlock.vue'

const store = useAiAssistantStore()
const scrollEl = ref<HTMLElement | null>(null)
const followBottom = ref(true)
const previewUrl = ref('')
const previewOpen = ref(false)

const showJump = computed(() => !followBottom.value && store.messages.length > 0)
const activeCitationId = computed(() => {
  if (store.selectedMessageId !== store.selectedMessage?.id) return null
  return store.selectedCitationId
})

interface ThreadItem {
  key: string
  type: 'time' | 'notice' | 'message' | 'context'
  message?: LocalAiMessage
  text?: string
}

function parseTime(value?: string) {
  return value ? dayjs.tz(value, TZ) : null
}

function timeDivider(value?: string): string {
  const time = parseTime(value)
  if (!time || !time.isValid()) return ''
  const now = dayjs().tz(TZ)
  const hm = time.format('HH:mm')
  if (time.isSame(now, 'day')) return `今天 ${hm}`
  if (time.isSame(now.subtract(1, 'day'), 'day')) return `昨天 ${hm}`
  return `${time.format('M月D日')} ${hm}`
}

function needDivider(previous?: LocalAiMessage, current?: LocalAiMessage): boolean {
  const now = parseTime(current?.asked_at)
  if (!now) return false
  const before = parseTime(previous?.asked_at)
  if (!before) return true
  return now.diff(before, 'minute') >= 30
}

const threadItems = computed<ThreadItem[]>(() => {
  const items: ThreadItem[] = []
  let previous: LocalAiMessage | undefined
  let contextInserted = false
  store.messages.forEach((message) => {
    if (
      !contextInserted &&
      message.in_context &&
      !previous?.in_context &&
      previous &&
      (message.round_no || 0) > 1
    ) {
      items.push({ key: 'context', type: 'context', text: '以下为最近 10 轮，更早的问答不再参与回答' })
      contextInserted = true
    }
    if (needDivider(previous, message)) {
      items.push({ key: `time-${message.id}`, type: 'time', text: timeDivider(message.asked_at) })
    }
    items.push({ key: `message-${message.id}`, type: 'message', message })
    previous = message
  })
  return items
})

function distanceToBottom(el: HTMLElement): number {
  return el.scrollHeight - el.scrollTop - el.clientHeight
}

function scrollToBottom(behavior: ScrollBehavior = 'auto'): void {
  const el = scrollEl.value
  if (!el) return
  el.scrollTo({ top: el.scrollHeight, behavior })
  followBottom.value = true
}

function onScroll(): void {
  const el = scrollEl.value
  if (!el) return
  const distance = distanceToBottom(el)
  followBottom.value = distance <= 40
  if (el.scrollTop < 60 && store.messagesHasMore && !store.messagesLoadingMore) {
    void loadEarlier()
  }
}

async function loadEarlier(): Promise<void> {
  const el = scrollEl.value
  if (!el) return
  const previousHeight = el.scrollHeight
  const previousTop = el.scrollTop
  const added = await store.loadEarlier()
  if (!added) return
  await nextTick()
  el.scrollTop = el.scrollHeight - previousHeight + previousTop
}

watch(
  () => store.scrollBottomSignal,
  () => {
    if (!followBottom.value) return
    void nextTick(() => scrollToBottom('auto'))
  }
)

watch(
  () => store.messagesStatus,
  (status) => {
    if (status === 'ready' || status === 'empty') {
      followBottom.value = true
      void nextTick(() => scrollToBottom('auto'))
    }
  }
)

watch(
  () => store.focusMessageId,
  async (messageId) => {
    if (!messageId) return
    await nextTick()
    const el = scrollEl.value
    if (!el) return
    const target = el.querySelector<HTMLElement>(`[data-message-id="${messageId}"]`)
    if (!target) return
    followBottom.value = false
    el.scrollTo({ top: Math.max(target.offsetTop - 16, 0), behavior: 'smooth' })
  }
)

onMounted(() => {
  void nextTick(() => scrollToBottom('auto'))
})

onBeforeUnmount(() => {
  store.leave()
})

function openPreview(url?: string): void {
  if (!url) return
  previewUrl.value = url
  previewOpen.value = true
}

function onRetry(messageId: number): void {
  void store.retry(messageId)
}

function onRate(message: LocalAiMessage, rating: 'USEFUL' | 'USELESS'): void {
  void store.rate(message, rating)
}

function onCopy(text: string): void {
  void store.copyAnswer(text)
}

function onCite(message: LocalAiMessage, citation: CitationBrief): void {
  store.selectCitation(message.id, citation)
}

function retryDisabled(): boolean {
  return store.isStreaming
}
</script>

<template>
  <div ref="scrollEl" class="k01-scroll" @scroll.passive="onScroll">
    <div v-if="store.messagesStatus === 'loading'" class="k01-thread">
      <div v-for="group in 3" :key="group" class="k01-skel-group">
        <div class="k01-skel-bubble" />
        <div class="k01-skel-answer">
          <div class="k01-skel-avatar" />
          <div class="k01-skel-lines">
            <div class="k01-skel-line" />
            <div class="k01-skel-line k01-skel-line--short" />
            <div class="k01-skel-line" />
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="store.messagesStatus === 'error' || store.messagesStatus === 'forbidden'" class="k01-thread">
      <zw-empty
        icon="cloud_off"
        :tone="store.messagesStatus === 'forbidden' ? 'warning' : 'error'"
        :title="store.messagesStatus === 'forbidden' ? '无权查看' : '对话加载失败'"
        :desc="store.messagesError"
      >
        <v-btn v-if="store.messagesStatus === 'error'" variant="outlined" prepend-icon="refresh" @click="store.loadMessages()">重新加载</v-btn>
      </zw-empty>
    </div>

    <div v-else-if="!store.messages.length" class="k01-thread">
      <div class="k01-empty">
        <span class="k01-empty-ava"><zw-icon name="auto_awesome" :size="28" fill /></span>
        <div class="k01-empty-title">有什么养殖问题，都可以问我</div>
        <div class="k01-empty-desc">可以问饲养管理、常见病防治和用药规范。关联批次后，回答会结合批次的品种、日龄和存栏。</div>
      </div>
    </div>

    <div v-else class="k01-thread">
      <div v-if="store.messagesHasMore" class="k01-earlier">
        <v-progress-circular v-if="store.messagesLoadingMore" indeterminate size="18" width="2" color="primary" />
        <button v-else type="button" class="zw-link" @click="loadEarlier">加载更早的问答</button>
      </div>
      <template v-for="item in threadItems" :key="item.key">
        <div v-if="item.type === 'time'" class="k01-time">{{ item.text }}</div>
        <div v-else-if="item.type === 'context'" class="k01-context">{{ item.text }}</div>
        <AiMessageBlock
          v-else-if="item.message"
          :message="item.message"
          :active-citation-id="activeCitationId"
          :streaming="isStreamingMessage(store.streaming?.key, item.message)"
          :retry-disabled="retryDisabled()"
          @retry="onRetry"
          @rate="onRate"
          @copy="onCopy"
          @cite="onCite"
          @preview="openPreview"
        />
      </template>
      <div v-if="store.notice" class="k01-system">{{ store.notice }}</div>
    </div>

    <button v-if="showJump" type="button" class="k01-jump" @click="scrollToBottom('smooth')">
      <zw-icon name="arrow_downward" :size="16" />回到底部
    </button>

    <v-dialog v-model="previewOpen" width="720" max-width="92vw">
      <div class="k01-preview">
        <img v-if="previewUrl" :src="previewUrl" alt="附图预览" />
        <button type="button" class="k01-preview-close" aria-label="关闭" @click="previewOpen = false">
          <zw-icon name="close" :size="20" />
        </button>
      </div>
    </v-dialog>
  </div>
</template>

<style scoped>
.k01-scroll {
  flex: 1;
  overflow: auto;
  position: relative;
}
.k01-thread {
  max-width: 720px;
  margin: 0 auto;
  padding: 20px 24px 24px;
}
.k01-time {
  text-align: center;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  margin: 4px 0 16px;
}
.k01-context {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0 20px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  text-align: center;
}
.k01-context::before,
.k01-context::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--line-1);
}
.k01-system {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k01-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding-top: 120px;
}
.k01-empty-ava {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--tertiary-container);
  color: var(--tertiary);
}
.k01-empty-title {
  margin-top: 16px;
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}
.k01-empty-desc {
  margin-top: 6px;
  max-width: 400px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}
.k01-earlier {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
  font-size: 12px;
}
.k01-earlier button {
  border: 0;
  background: none;
  padding: 4px 8px;
  font: inherit;
  font-size: 12px;
}
.k01-jump {
  position: sticky;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px 0 10px;
  border: 1px solid var(--line-1);
  border-radius: 16px;
  background: var(--bg-card);
  box-shadow: var(--shadow-popover);
  color: var(--text-2);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  z-index: 2;
}
.k01-jump:hover {
  background: var(--fill-1);
}
.k01-skel-group {
  padding: 16px 0 24px;
}
.k01-skel-bubble {
  width: 45%;
  height: 40px;
  margin-left: auto;
  border-radius: var(--radius-md);
  background: var(--fill-2);
}
.k01-skel-answer {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}
.k01-skel-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--fill-2);
  flex-shrink: 0;
}
.k01-skel-lines {
  flex: 1;
}
.k01-skel-line {
  height: 12px;
  border-radius: 6px;
  background: var(--fill-2);
  margin-bottom: 10px;
}
.k01-skel-line--short {
  width: 62%;
}
.k01-preview {
  position: relative;
  background: var(--media-backdrop);
  border-radius: var(--radius-md);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.k01-preview img {
  display: block;
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
}
.k01-preview-close {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: #000000a6;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
</style>
