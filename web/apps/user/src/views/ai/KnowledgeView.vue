<script setup lang="ts">
import { computed, nextTick, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dayjs, formatDuration, TZ } from '@zmt/shared'
import type { KnowledgeBlock } from '@/api/ai'
import { useAiKnowledgeStore } from '@/stores/ai/knowledge'
import { stripCitationMarkers } from '@/stores/ai/shared'
import { useUiStore } from '@/stores/ui'

const store = useAiKnowledgeStore()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const detail = computed(() => store.detail)
const blocks = computed<KnowledgeBlock[]>(() => detail.value?.blocks || [])
const sourceText = computed(() => detail.value?.source_type_text || '平台知识库')
const versionText = computed(() => {
  const value = detail.value
  if (!value) return ''
  const time = value.updated_at ? dayjs.tz(value.updated_at, TZ) : null
  const dateText = time?.isValid() ? `${time.format('M月D日')}更新` : ''
  return [`版本 ${value.version_no ?? 0}`, dateText].filter(Boolean).join(' · ')
})
const video = computed(() => detail.value?.embedded_video || null)
const citedMessage = computed(() => (store.messageStatus === 'ready' ? store.message : null))
const answerExcerpt = computed(() => {
  const raw = citedMessage.value?.answer_text || ''
  const seq = Number(route.query.seq || 0)
  let text = ''
  if (seq) {
    const paragraph = raw
      .split(/\n{2,}/)
      .find((item) => item.includes(`[^${seq}]`))
    if (paragraph) text = paragraph
  }
  if (!text) text = raw
  const clean = stripCitationMarkers(text).replace(/\s+/g, ' ').trim()
  if (!clean) return ''
  return clean.length > 180 ? `${clean.slice(0, 180)}…` : clean
})
const citationSeq = computed(() => {
  const seq = Number(route.query.seq || 0)
  if (seq) return seq
  const citation = citedMessage.value?.citations?.find((item) => item.id === store.citationId)
  return citation?.seq_no || 0
})
const answerTime = computed(() => {
  const value = citedMessage.value?.asked_at
  if (!value) return ''
  const time = dayjs.tz(value, TZ)
  if (!time.isValid()) return ''
  const now = dayjs().tz(TZ)
  if (time.isSame(now, 'day')) return `今天 ${time.format('HH:mm')}`
  return `${time.format('M月D日 HH:mm')}`
})
const messageIdForQuery = computed(() => store.messageId || 0)

onMounted(() => {
  void load()
})

watch(
  () => route.params.id,
  () => {
    void load()
  }
)

async function load(): Promise<void> {
  const knowledgeId = Number(route.params.id || 0)
  if (!knowledgeId) return
  const citationId = Number(route.query.citation_id || 0)
  const messageId = Number(route.query.message_id || 0)
  await store.load(knowledgeId, citationId, messageId)
  if (store.status === 'ready') {
    await nextTick()
    scrollToHighlight()
  }
}

function scrollToHighlight(): void {
  const key = detail.value?.highlight_para_key
  if (!key) return
  const targets = Array.from(document.querySelectorAll<HTMLElement>('[data-para-key]'))
  const target = targets.find((element) => element.dataset.paraKey === key)
  if (!target) return
  const container = document.querySelector<HTMLElement>('.zw-main .zw-content')
  if (!container) return
  const targetRect = target.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  const desired = container.scrollTop + targetRect.top - containerRect.top - 16
  const maxScroll = container.scrollHeight - container.clientHeight
  if (desired <= 0) return
  if (desired <= maxScroll) {
    container.scrollTo({ top: desired, behavior: 'smooth' })
  } else if (targetRect.bottom > containerRect.bottom - 24) {
    container.scrollTo({ top: maxScroll, behavior: 'smooth' })
  }
}

function onHeadClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.zw-back')) goBack()
}

function goBack(): void {
  if (window.history.length > 1) router.back()
  else void router.push({ name: 'WU-K01' })
}

function openVideo(): void {
  if (!video.value) return
  void router.push({
    name: 'WU-K04',
    params: { id: String(video.value.course_id) },
    query: { from: 'knowledge' }
  })
}

function openRelated(id: number): void {
  void router.push({ name: 'WU-K02', params: { id: String(id) } })
}

function backToChat(): void {
  const message = citedMessage.value
  if (!message?.session_id) {
    void router.push({ name: 'WU-K01' })
    return
  }
  void router.push({
    name: 'WU-K01',
    query: { session: String(message.session_id), message: String(message.id) }
  })
}

async function copyLink(): Promise<void> {
  if (!detail.value) return
  const url = `${window.location.origin}/knowledge/${detail.value.id}`
  try {
    await navigator.clipboard.writeText(url)
    ui.toast('已复制链接')
  } catch {
    ui.toast('复制失败，请手动复制地址栏链接', 'error')
  }
}

function blockClass(block: KnowledgeBlock): string[] {
  const classes = ['k02-block', `k02-block--${(block.para_type || 'TEXT').toLowerCase()}`]
  if (block.para_key && block.para_key === detail.value?.highlight_para_key) classes.push('k02-block--cited')
  return classes
}
</script>

<template>
  <zw-shell side="user" active="WU-K02" :crumbs="['学习与助手', 'AI 养殖助手', '知识原文']">
    <template v-if="store.status === 'loading'">
      <zw-page-head title="知识原文" back @click="onHeadClick">
        <template #desc><span class="zw-faint">知识原文加载中…</span></template>
      </zw-page-head>
      <div class="k02">
        <div class="k02-main">
          <zw-card flush>
            <div class="k02-article">
              <v-skeleton-loader type="heading, text@2, image" />
              <v-skeleton-loader type="text@5" class="mt-6" />
            </div>
          </zw-card>
        </div>
        <div class="k02-aside zw-stack">
          <v-skeleton-loader type="card" />
          <v-skeleton-loader type="card" />
        </div>
      </div>
    </template>

    <template v-else-if="store.offShelf">
      <zw-page-head title="知识原文" back @click="onHeadClick" />
      <zw-card>
        <zw-empty icon="visibility_off" title="这条知识已下架，不能查看原文" desc="AI 回答中的原回答和来源会保留，你可以回到对话继续提问。">
          <v-btn variant="tonal" color="primary" prepend-icon="arrow_back" @click="goBack">返回</v-btn>
        </zw-empty>
      </zw-card>
    </template>

    <template v-else-if="store.status === 'error' || store.status === 'forbidden' || store.notFound">
      <zw-page-head title="知识原文" back @click="onHeadClick" />
      <zw-card>
        <zw-empty
          icon="cloud_off"
          :tone="store.status === 'forbidden' ? 'warning' : 'error'"
          :title="store.notFound ? '知识条目不存在或无权查看' : '知识原文加载失败'"
          :desc="store.error"
        >
          <v-btn variant="outlined" prepend-icon="refresh" @click="load">重新加载</v-btn>
          <v-btn variant="text" @click="goBack">返回</v-btn>
        </zw-empty>
      </zw-card>
    </template>

    <template v-else-if="detail">
      <zw-page-head :title="detail.title" :desc="`${sourceText} · ${versionText}`" back @click="onHeadClick">
        <template #tags><zw-tag v-if="detail.category_name">{{ detail.category_name }}</zw-tag></template>
        <template #actions>
          <v-btn variant="outlined" prepend-icon="link" @click="copyLink">复制链接</v-btn>
        </template>
      </zw-page-head>

      <zw-notice v-if="detail.updated_after_answer" type="info" icon="update" class="k02-updated">
        这条知识在回答后已更新，当前显示版本 {{ detail.version_no }}，被引用的内容可能有变化。
      </zw-notice>

      <div class="k02">
        <div class="k02-main">
          <zw-card flush>
            <div class="k02-article">
              <template v-if="video">
                <button type="button" class="k02-video" aria-label="播放内嵌教学视频" @click="openVideo">
                  <zw-thumb kind="course" class="k02-video-thumb" />
                  <span class="k02-video-play"><zw-icon name="play_arrow" :size="36" fill /></span>
                  <span class="k02-dur"><zw-icon name="smart_display" :size="14" />{{ formatDuration(video.duration_sec) }}</span>
                </button>
                <div class="k02-cap">教学视频 · {{ video.title }}</div>
              </template>

              <div class="k02-body">
                <template v-for="block in blocks" :key="block.para_key || block.text">
                  <div
                    v-if="block.para_key === detail.highlight_para_key"
                    :data-para-key="block.para_key"
                    :class="blockClass(block)"
                  >
                    <div class="k02-quote">
                      <zw-tag type="ai" icon="auto_awesome">AI 回答引用了这一段</zw-tag>
                      <p>{{ block.text }}</p>
                    </div>
                  </div>
                  <h3 v-else-if="block.para_type === 'HEADING'" :data-para-key="block.para_key" :class="blockClass(block)">
                    {{ block.text }}
                  </h3>
                  <blockquote v-else-if="block.para_type === 'QUOTE'" :data-para-key="block.para_key" :class="blockClass(block)">
                    {{ block.text }}
                  </blockquote>
                  <div v-else-if="block.para_type === 'IMAGE'" :data-para-key="block.para_key" :class="blockClass(block)">
                    <img v-if="block.image_url" :src="block.image_url" :alt="block.text || '条目图片'" />
                  </div>
                  <div v-else :data-para-key="block.para_key" :class="blockClass(block)">
                    <span v-if="block.para_type === 'LIST'" class="k02-li" aria-hidden="true" />
                    <p v-else>{{ block.text }}</p>
                    <p v-if="block.para_type === 'LIST'" class="k02-list-text">{{ block.text }}</p>
                  </div>
                </template>
              </div>

              <div class="k02-foot">
                <zw-icon name="info" :size="14" />
                本条目由{{ sourceText }}维护，内容以平台兽药目录与休药期标准为准。
              </div>
            </div>
          </zw-card>
        </div>

        <div class="k02-aside zw-stack">
          <zw-card v-if="citedMessage && store.citationId" title="引用这一段的回答">
            <div class="k02-ref-q">{{ citedMessage.question_text }}</div>
            <div class="k02-ref-a">
              {{ answerExcerpt }}<sup v-if="citationSeq">{{ citationSeq }}</sup>
            </div>
            <div class="k02-ref-decl"><zw-icon name="info" :size="14" />由 AI 生成，仅供参考</div>
            <div class="k02-ref-meta">
              <span>{{ answerTime }} · 回答中的引用 {{ citationSeq }}</span>
              <a class="zw-link" @click="backToChat">回到对话</a>
            </div>
          </zw-card>
          <v-skeleton-loader v-else-if="messageIdForQuery && store.messageStatus === 'loading'" type="card" />

          <zw-card title="条目信息">
            <zw-desc :cols="1" :label-width="70">
              <zw-desc-item label="分类">{{ detail.category_name || '—' }}</zw-desc-item>
              <zw-desc-item label="来源">{{ sourceText }}</zw-desc-item>
              <zw-desc-item label="版本">{{ versionText }}</zw-desc-item>
              <zw-desc-item v-if="video" label="教学视频">{{ video.title }} · <span class="num">{{ formatDuration(video.duration_sec) }}</span></zw-desc-item>
            </zw-desc>
          </zw-card>

          <zw-card v-if="detail.related_items?.length" title="相关条目" flush>
            <button
              v-for="item in detail.related_items"
              :key="item.id"
              type="button"
              class="k02-rel"
              @click="openRelated(item.id)"
            >
              <span class="zw-ico-tile zw-ico-tile--primary">
                <zw-icon :name="item.icon_type === 'ARTICLE' ? 'description' : 'menu_book'" :size="18" />
              </span>
              <span class="k02-rel-txt">
                <span class="k02-rel-t">{{ item.title }}</span>
                <span class="k02-rel-s">{{ item.category_name }} · {{ item.source_type === 'PLATFORM' ? `版本 ${item.version_no ?? 0}` : item.source_type_text }}</span>
              </span>
              <zw-icon name="chevron_right" :size="20" />
            </button>
          </zw-card>
        </div>
      </div>
    </template>
  </zw-shell>
</template>

<style scoped>
.k02 {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.k02-main {
  flex: 1;
  min-width: 0;
}
.k02-aside {
  width: 320px;
  flex-shrink: 0;
}
.k02-article {
  padding: 24px 32px 28px;
}
.k02-updated {
  margin-bottom: 16px;
}
.k02-video {
  position: relative;
  display: block;
  width: 600px;
  max-width: 100%;
  aspect-ratio: 16 / 9;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--media-backdrop);
  cursor: pointer;
}
.k02-video-thumb {
  position: absolute !important;
  inset: 0;
  width: 100% !important;
  height: 100% !important;
}
.k02-video-play {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 64px;
  height: 64px;
  margin: -32px 0 0 -32px;
  border-radius: 50%;
  background: #00000080;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.k02-video:hover .k02-video-play {
  background: #000000a6;
}
.k02-dur {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--radius-xs);
  background: #000000a6;
  color: var(--on-media);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.k02-cap {
  margin-top: 8px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k02-body {
  margin-top: 20px;
}
.k02-block {
  margin: 0 0 16px;
}
.k02-block p {
  margin: 0;
  font-size: 15px;
  line-height: 26px;
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-word;
}
.k02-block--heading {
  margin: 20px 0 12px;
  font-size: 16px;
  line-height: 26px;
  font-weight: 600;
  color: var(--text-1);
}
.k02-block--quote {
  padding: 10px 16px 12px 14px;
  border-left: 3px solid var(--tertiary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--fill-1);
  font-size: 15px;
  line-height: 26px;
  color: var(--text-1);
  white-space: pre-wrap;
}
.k02-block--list {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.k02-li {
  width: 5px;
  height: 5px;
  margin-top: 11px;
  border-radius: 50%;
  background: var(--text-2);
  flex-shrink: 0;
}
.k02-list-text {
  flex: 1;
}
.k02-block--image img {
  max-width: 100%;
  border-radius: var(--radius-sm);
}
.k02-quote {
  padding: 10px 16px 12px 14px;
  border-left: 3px solid var(--tertiary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--fill-1);
  animation: k02-flash 600ms ease-out;
}
.k02-quote p {
  margin: 8px 0 0;
  font-size: 15px;
  line-height: 26px;
  color: var(--text-1);
  white-space: pre-wrap;
}
@keyframes k02-flash {
  from { background: var(--tertiary-container); }
  to { background: var(--fill-1); }
}
.k02-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 16px;
  border-top: 1px solid var(--line-1);
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k02-ref-q {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
}
.k02-ref-a {
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  font-size: 13px;
  line-height: 21px;
  color: var(--text-2);
}
.k02-ref-decl {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k02-ref-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-3);
}
.k02-rel {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 20px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.k02-rel + .k02-rel {
  border-top: 1px solid var(--line-1);
}
.k02-rel:hover {
  background: var(--fill-1);
}
.k02-rel-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.k02-rel-t {
  font-size: 14px;
  line-height: 22px;
}
.k02-rel-s {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k02-rel > :deep(.zw-mso:last-child) {
  color: var(--text-3);
}
</style>
