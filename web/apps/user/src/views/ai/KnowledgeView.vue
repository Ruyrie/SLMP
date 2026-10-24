<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dayjs, formatDuration, TZ } from '@zmt/shared'
import type { KnowledgeBlock } from '@/api/ai'
import { useAiKnowledgeStore } from '@/stores/ai/knowledge'
import { stripCitationMarkers } from '@/stores/ai/shared'
import { useUiStore } from '@/stores/ui'
import { safeKnowledgeImageSrc, sanitizeKnowledgeHtml } from './knowledgeContent'

type BlockType = 'TEXT' | 'HEADING' | 'QUOTE' | 'LIST' | 'IMAGE'

const BLOCK_TYPES = new Set<BlockType>(['TEXT', 'HEADING', 'QUOTE', 'LIST', 'IMAGE'])

interface RenderableBlock {
  block: KnowledgeBlock
  key: string
  type: BlockType
  html: string
  text: string
  imageSrc: string
  alt: string
  highlighted: boolean
}

const store = useAiKnowledgeStore()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const detail = computed(() => store.detail)
const blocks = computed<KnowledgeBlock[]>(() => detail.value?.blocks || [])
const renderBlocks = computed<RenderableBlock[]>(() => {
  const highlightKey = detail.value?.highlight_para_key || ''
  return blocks.value
    .map<RenderableBlock>((block, index) => {
      const type = blockTypeOf(block)
      const text = (block.text || '').trim()
      const highlighted = !!block.para_key && block.para_key === highlightKey
      return {
        block,
        key: `${block.para_key || 'block'}:${index}`,
        type,
        html: type === 'IMAGE' ? '' : sanitizeKnowledgeHtml(block.content_html),
        text,
        imageSrc: type === 'IMAGE' ? safeKnowledgeImageSrc(block.image_url) : '',
        alt: text || '知识配图',
        highlighted
      }
    })
    .filter((item) => item.type === 'IMAGE' || !!item.html || !!item.text || item.highlighted)
})
const failedImages = reactive<Record<string, boolean>>({})
const previewOpen = ref(false)
const previewUrl = ref('')
const previewAlt = ref('')
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
  clearImageState()
  const citationId = Number(route.query.citation_id || 0)
  const messageId = Number(route.query.message_id || 0)
  await store.load(knowledgeId, citationId, messageId)
  if (store.status === 'ready') {
    await nextTick()
    scrollToHighlight()
  }
}

function blockTypeOf(block: KnowledgeBlock): BlockType {
  const value = (block.para_type || 'TEXT').toUpperCase() as BlockType
  return BLOCK_TYPES.has(value) ? value : 'TEXT'
}

function clearImageState(): void {
  for (const key of Object.keys(failedImages)) delete failedImages[key]
}

function onImageError(key: string): void {
  failedImages[key] = true
}

/** 图片加载后尺寸变化会移动锚点，被引用的图片块需要重新定位一次。 */
function onImageLoad(item: RenderableBlock): void {
  if (!item.highlighted) return
  void nextTick(() => scrollToHighlight())
}

function openPreview(url: string, alt?: string): void {
  if (!url) return
  previewUrl.value = url
  previewAlt.value = alt || '知识配图'
  previewOpen.value = true
}

/** content_html 内的图片由 v-html 插入，用事件委托补上全屏预览。 */
function onBodyClick(event: MouseEvent): void {
  const target = event.target
  if (!(target instanceof Element)) return
  const image = target.closest('img')
  if (!(image instanceof HTMLImageElement)) return
  if (image.closest('.k02-image-btn')) return
  const src = image.getAttribute('src') || ''
  if (!safeKnowledgeImageSrc(src)) return
  event.preventDefault()
  openPreview(src, image.alt || '知识配图')
}

/** content_html 内的图片加载后，如果它所在段落正是引用锚点，重新定位。 */
function onBodyMediaLoad(event: Event): void {
  const target = event.target
  if (!(target instanceof HTMLImageElement)) return
  if (!target.closest('.k02-html')) return
  const block = target.closest<HTMLElement>('[data-para-key]')
  if (!block || block.dataset.paraKey !== detail.value?.highlight_para_key) return
  void nextTick(() => scrollToHighlight())
}

/** content_html 内的坏图替换成占位，避免浏览器默认破图图标影响版面。 */
function onBodyMediaError(event: Event): void {
  const target = event.target
  if (!(target instanceof HTMLImageElement)) return
  if (target.closest('.k02-image-btn') || !target.closest('.k02-html')) return
  if (target.dataset.broken === '1') return
  target.dataset.broken = '1'
  const alt = target.alt || '图片加载失败'
  const placeholder = document.createElement('span')
  placeholder.className = 'k02-inline-broken'
  placeholder.setAttribute('role', 'img')
  placeholder.setAttribute('aria-label', alt)
  placeholder.textContent = alt
  target.replaceWith(placeholder)
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
  const classes = ['k02-block', `k02-block--${blockTypeOf(block).toLowerCase()}`]
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

              <div
                v-if="renderBlocks.length"
                class="k02-body"
                @click="onBodyClick"
                @load.capture="onBodyMediaLoad"
                @error.capture="onBodyMediaError"
              >
                <div
                  v-for="item in renderBlocks"
                  :key="item.key"
                  :data-para-key="item.block.para_key || undefined"
                  :class="blockClass(item.block)"
                >
                  <div v-if="item.highlighted" class="k02-cited">
                    <zw-tag type="ai" icon="auto_awesome">AI 回答引用了这一段</zw-tag>
                  </div>

                  <template v-if="item.type === 'IMAGE'">
                    <button
                      v-if="item.imageSrc && !failedImages[item.key]"
                      type="button"
                      class="k02-image-btn"
                      :aria-label="`查看大图：${item.alt}`"
                      @click="openPreview(item.imageSrc, item.alt)"
                    >
                      <img
                        :src="item.imageSrc"
                        :alt="item.alt"
                        loading="lazy"
                        decoding="async"
                        @load="onImageLoad(item)"
                        @error="onImageError(item.key)"
                      >
                    </button>
                    <div
                      v-else
                      class="k02-image-fallback"
                      role="img"
                      :aria-label="`${item.alt}，${item.imageSrc ? '图片加载失败' : '图片暂不可用'}`"
                    >
                      <zw-icon name="broken_image" :size="24" />
                      <span class="k02-image-fallback-main">{{ item.alt }}</span>
                      <span class="k02-image-fallback-sub">{{ item.imageSrc ? '图片加载失败' : '图片暂不可用' }}</span>
                    </div>
                  </template>

                  <template v-else-if="item.type === 'HEADING'">
                    <h3 v-if="item.html" class="k02-heading k02-html" v-html="item.html" />
                    <h3 v-else class="k02-heading">{{ item.text }}</h3>
                  </template>

                  <template v-else-if="item.type === 'QUOTE'">
                    <blockquote v-if="item.html" class="k02-quote-body k02-html" v-html="item.html" />
                    <blockquote v-else class="k02-quote-body"><p>{{ item.text }}</p></blockquote>
                  </template>

                  <template v-else-if="item.type === 'LIST'">
                    <div v-if="item.html" class="k02-html k02-html-list" v-html="item.html" />
                    <div v-else class="k02-list-row">
                      <span class="k02-li" aria-hidden="true" />
                      <p class="k02-list-text">{{ item.text }}</p>
                    </div>
                  </template>

                  <template v-else>
                    <div v-if="item.html" class="k02-html" v-html="item.html" />
                    <p v-else>{{ item.text }}</p>
                  </template>
                </div>
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

    <v-dialog
      v-model="previewOpen"
      fullscreen
      transition="fade-transition"
      scrim="#000000E6"
      class="k02-preview-dialog"
    >
      <div class="k02-preview" @click.self="previewOpen = false">
        <img v-if="previewUrl" :src="previewUrl" :alt="previewAlt">
        <div v-else class="k02-preview-empty">图片暂不可用</div>
        <button type="button" class="k02-preview-close" aria-label="关闭大图" @click="previewOpen = false">
          <zw-icon name="close" :size="24" />
        </button>
        <div v-if="previewAlt" class="k02-preview-cap">{{ previewAlt }}</div>
      </div>
    </v-dialog>
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
.k02-block > p,
.k02-quote-body p,
.k02-list-text {
  margin: 0;
  font-size: 15px;
  line-height: 26px;
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-word;
}
.k02-block--heading {
  margin-top: 20px;
  margin-bottom: 12px;
}
.k02-heading {
  margin: 0;
  font-size: 16px;
  line-height: 26px;
  font-weight: 600;
  color: var(--text-1);
  word-break: break-word;
}
.k02-block--quote {
  padding: 10px 16px 12px 14px;
  border-left: 3px solid var(--tertiary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--fill-1);
  color: var(--text-1);
}
.k02-quote-body {
  margin: 0;
  padding: 0;
  border: 0;
  font-size: 15px;
  line-height: 26px;
  color: inherit;
  white-space: pre-wrap;
}
.k02-list-row {
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
.k02-html {
  font-size: 15px;
  line-height: 26px;
  color: var(--text-1);
  word-break: break-word;
}
.k02-html :deep(p) {
  margin: 0 0 8px;
}
.k02-html :deep(p:last-child) {
  margin-bottom: 0;
}
.k02-html :deep(h1),
.k02-html :deep(h2),
.k02-html :deep(h3),
.k02-html :deep(h4) {
  margin: 12px 0 6px;
  font-size: 16px;
  line-height: 26px;
  font-weight: 600;
  color: var(--text-1);
}
.k02-html :deep(h1:first-child),
.k02-html :deep(h2:first-child),
.k02-html :deep(h3:first-child),
.k02-html :deep(h4:first-child) {
  margin-top: 0;
}
.k02-html :deep(ul),
.k02-html :deep(ol) {
  margin: 0 0 8px;
  padding-left: 22px;
}
.k02-html :deep(ul) {
  list-style: disc;
}
.k02-html :deep(ol) {
  list-style: decimal;
}
.k02-html :deep(li) {
  margin: 2px 0;
  padding-left: 2px;
}
.k02-html :deep(li p) {
  margin: 0;
}
.k02-html :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 14px;
  border-left: 3px solid var(--tertiary);
  background: var(--fill-1);
  color: var(--text-1);
}
.k02-html :deep(a) {
  color: var(--primary);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.k02-html :deep(a:hover) {
  color: var(--primary-hover);
}
.k02-html :deep(a:not([href])) {
  color: inherit;
  text-decoration: none;
}
.k02-html :deep(strong) {
  font-weight: 600;
}
.k02-html :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 8px 0;
  border-radius: var(--radius-md);
  background: var(--fill-1);
  cursor: zoom-in;
}
.k02-html :deep(.k02-inline-broken) {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 480px;
  min-height: 72px;
  margin: 8px 0;
  padding: 12px 14px;
  border: 1px dashed var(--line-2);
  border-radius: var(--radius-md);
  background: var(--fill-1);
  color: var(--text-2);
  font-size: 13px;
  line-height: 20px;
}
.k02-block--cited {
  padding: 10px 16px 12px 14px;
  border-left: 3px solid var(--tertiary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--fill-1);
  animation: k02-flash 600ms ease-out;
}
.k02-block--cited.k02-block--heading {
  padding-top: 10px;
}
.k02-cited {
  margin-bottom: 8px;
}
.k02-image-btn {
  display: block;
  width: fit-content;
  max-width: 100%;
  min-width: 160px;
  min-height: 96px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--fill-1);
  cursor: zoom-in;
  line-height: 0;
}
.k02-image-btn img {
  display: block;
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-md);
}
.k02-image-fallback {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  width: 100%;
  max-width: 480px;
  min-height: 96px;
  padding: 16px;
  border: 1px dashed var(--line-2);
  border-radius: var(--radius-md);
  background: var(--fill-1);
  color: var(--text-2);
}
.k02-image-fallback-main {
  font-size: 14px;
  line-height: 20px;
  color: var(--text-2);
}
.k02-image-fallback-sub {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
@keyframes k02-flash {
  from { background: var(--tertiary-container); }
  to { background: var(--fill-1); }
}
.k02-preview {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 56px 72px 48px;
  background: var(--media-backdrop);
  color: var(--on-media);
  cursor: zoom-out;
}
.k02-preview img {
  max-width: 100%;
  max-height: calc(100vh - 150px);
  object-fit: contain;
  border-radius: var(--radius-sm);
  background: #000000;
  cursor: default;
}
.k02-preview-close {
  position: absolute;
  top: 18px;
  right: 22px;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: #FFFFFF1F;
  color: var(--on-media);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.k02-preview-close:hover {
  background: #FFFFFF33;
}
.k02-preview-cap {
  max-width: 80vw;
  font-size: 13px;
  line-height: 20px;
  color: #FFFFFFB3;
  text-align: center;
}
.k02-preview-empty {
  font-size: 14px;
  color: #FFFFFFA6;
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

<style>
.k02-preview-dialog .v-overlay__content {
  width: 100%;
  height: 100%;
  max-width: none;
}
</style>
