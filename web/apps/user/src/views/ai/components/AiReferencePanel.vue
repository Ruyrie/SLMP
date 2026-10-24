<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { CitationBrief, KnowledgeBlock } from '@/api/ai'
import { useAiAssistantStore } from '@/stores/ai/assistant'

const store = useAiAssistantStore()
const router = useRouter()
const expandedKey = ref('')

const citations = computed(() => store.selectedCitations)
const selectedMessage = computed(() => store.selectedMessage)
const selectedIsLatest = computed(
  () => !!selectedMessage.value && selectedMessage.value.id === store.messages[store.messages.length - 1]?.id
)
const subText = computed(() => {
  const count = citations.value.length
  if (!count) return ''
  if (selectedIsLatest.value) return `当前回答 · ${count} 条`
  return `第 ${selectedMessage.value?.round_no || 1} 轮回答 · ${count} 条`
})

watch(
  () => store.selectedCitationId,
  (id) => {
    if (!id) return
    const citation = citations.value.find((item) => item.id === id)
    if (citation) expandedKey.value = keyOf(citation)
  },
  { immediate: true }
)

function keyOf(citation: CitationBrief): string {
  return `${citation.knowledge_id}:${citation.id}`
}

function stateOf(citation: CitationBrief) {
  return store.citationDetails[keyOf(citation)]
}

function sourceText(citation: CitationBrief): string {
  switch (citation.source_type) {
    case 'DISEASE':
      return '常见病症'
    case 'DRUG_CATALOG':
      return '兽药目录'
    default:
      return '平台知识库'
  }
}

function toggle(citation: CitationBrief): void {
  const key = keyOf(citation)
  if (expandedKey.value === key) {
    expandedKey.value = ''
    return
  }
  expandedKey.value = key
  store.selectCitation(store.selectedMessage?.id || 0, citation)
}

function quoteText(citation: CitationBrief): string {
  const data = stateOf(citation)?.data
  if (!data) return ''
  const blocks = (data.blocks || []) as KnowledgeBlock[]
  const highlight = data.highlight_para_key
  const target = highlight ? blocks.find((block) => block.para_key === highlight) : undefined
  if (target?.text) return target.text
  return highlight ? '被引用的段落已在更新版本中调整，请查看知识原文。' : blocks[0]?.text || ''
}

function nextText(citation: CitationBrief): string {
  const data = stateOf(citation)?.data
  if (!data) return ''
  const blocks = (data.blocks || []) as KnowledgeBlock[]
  const index = blocks.findIndex((block) => block.para_key === data.highlight_para_key)
  if (index < 0) return ''
  const next = blocks[index + 1]
  if (!next || next.para_type === 'HEADING') return ''
  return next.text || ''
}

function openKnowledge(citation: CitationBrief): void {
  const seq = citation.seq_no
  void router.push({
    name: 'WU-K02',
    params: { id: String(citation.knowledge_id) },
    query: {
      citation_id: String(citation.id),
      message_id: selectedMessage.value?.id ? String(selectedMessage.value.id) : undefined,
      seq: String(seq)
    }
  })
}

function openVideo(citation: CitationBrief): void {
  openKnowledge(citation)
}

function retry(citation: CitationBrief): void {
  store.retryCitation(citation)
}
</script>

<template>
  <aside class="k01-ref" aria-label="引用来源">
    <header class="k01-ref-head">
      <span class="k01-ref-title">引用来源</span>
      <span v-if="subText" class="k01-ref-sub">{{ subText }}</span>
    </header>
    <div class="k01-ref-body">
      <template v-if="citations.length">
        <div
          v-for="citation in citations"
          :key="citation.id"
          class="k01-rc"
          :class="{ on: expandedKey === keyOf(citation) || store.selectedCitationId === citation.id }"
        >
          <button type="button" class="k01-rc-head" @click="toggle(citation)">
            <span class="k01-rc-no num">{{ citation.seq_no }}</span>
            <span class="k01-rc-txt">
              <span class="k01-rc-title">{{ citation.title }}</span>
              <span class="k01-rc-meta">
                <zw-tag v-if="citation.category_name" size="small">{{ citation.category_name }}</zw-tag>
                {{ sourceText(citation) }}
              </span>
            </span>
            <zw-icon :name="expandedKey === keyOf(citation) ? 'expand_less' : 'expand_more'" :size="20" />
          </button>
          <div v-if="expandedKey === keyOf(citation)" class="k01-rc-body">
            <div v-if="stateOf(citation)?.status === 'loading'" class="k01-rc-skel">
              <i /><i /><i />
            </div>
            <div v-else-if="stateOf(citation)?.status === 'error'" class="k01-rc-error">
              <span>{{ stateOf(citation)?.error }}</span>
              <button type="button" class="zw-link" @click="retry(citation)">重新加载</button>
            </div>
            <template v-else>
              <div class="k01-quote">
                <zw-tag type="ai" size="small" icon="auto_awesome">AI 回答引用了这一段</zw-tag>
                <p>{{ quoteText(citation) }}</p>
              </div>
              <p v-if="nextText(citation)" class="k01-rc-more">{{ nextText(citation) }}</p>
              <button
                v-if="stateOf(citation)?.data?.embedded_video"
                type="button"
                class="k01-rc-video"
                @click="openVideo(citation)"
              >
                <span class="k01-rc-video-cover"><zw-icon name="play_arrow" :size="22" fill /></span>
                <span class="k01-rc-video-txt">
                  <span class="k01-rc-video-t">{{ stateOf(citation)?.data?.embedded_video?.title }}</span>
                  <span class="k01-rc-video-s">教学视频 · 可在原文中播放</span>
                </span>
                <zw-icon name="chevron_right" :size="18" />
              </button>
              <v-btn block variant="outlined" color="primary" append-icon="arrow_forward" class="mt-3" @click="openKnowledge(citation)">
                查看知识原文
              </v-btn>
            </template>
          </div>
        </div>
        <div class="k01-ref-note">
          <zw-icon name="info" :size="14" />
          <span>点回答中的角标或来源，这里显示对应条目的原文摘要。AI 只从平台知识库、常见病症知识和兽药目录中查找依据。</span>
        </div>
      </template>

      <zw-empty
        v-else
        icon="menu_book"
        title="还没有引用来源"
        desc="提问后，回答引用的平台知识会列在这里。点回答中的角标或来源，可以查看原文摘要。"
      />
    </div>
  </aside>
</template>

<style scoped>
.k01-ref {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--line-1);
  background: var(--fill-1);
  min-height: 0;
}
.k01-ref-head {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px 0 20px;
  border-bottom: 1px solid var(--line-1);
  background: var(--bg-card);
}
.k01-ref-title {
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}
.k01-ref-sub {
  font-size: 12px;
  color: var(--text-3);
}
.k01-ref-body {
  flex: 1;
  overflow: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.k01-rc {
  background: var(--bg-card);
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
}
.k01-rc.on {
  border-color: var(--primary);
}
.k01-rc-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 12px 12px 12px 14px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.k01-rc-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--fill-3);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 500;
}
.k01-rc.on .k01-rc-no {
  background: var(--primary);
  color: var(--on-primary);
}
.k01-rc-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.k01-rc-title {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
}
.k01-rc-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k01-rc-head > :deep(.zw-mso) {
  color: var(--text-3);
  margin-top: 1px;
}
.k01-rc-body {
  padding: 0 14px 14px;
}
.k01-quote {
  border-left: 2px solid var(--tertiary);
  background: var(--fill-1);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  padding: 8px 12px 10px;
}
.k01-quote p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 21px;
  color: var(--text-1);
  white-space: pre-wrap;
}
.k01-rc-more {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 21px;
  color: var(--text-2);
}
.k01-rc-video {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin: 12px 0;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.k01-rc-video-cover {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 54px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  background: var(--media-backdrop);
  color: var(--on-media);
}
.k01-rc-video-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.k01-rc-video-t {
  font-size: 13px;
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.k01-rc-video-s {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k01-rc-skel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}
.k01-rc-skel i {
  height: 12px;
  border-radius: 6px;
  background: var(--fill-2);
}
.k01-rc-skel i:last-child {
  width: 60%;
}
.k01-rc-error {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--error);
}
.k01-ref-note {
  display: flex;
  gap: 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
  padding: 0 2px;
}
.k01-ref-note :deep(.zw-mso) {
  margin-top: 1px;
}
.k01-ref-body :deep(.zw-empty) {
  padding: 48px 12px;
}
.k01-ref-body :deep(.zw-empty-ico) {
  width: 64px;
  height: 64px;
}
.k01-ref-body :deep(.zw-empty-title) {
  font-size: 14px;
}
.k01-ref-body :deep(.zw-empty-desc) {
  font-size: 12px;
  line-height: 20px;
}
</style>
