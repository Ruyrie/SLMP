<script setup lang="ts">
import { computed } from 'vue'
import { dayjs, TZ } from '@zmt/shared'
import type { AiSessionItem } from '@/api/ai'
import { useAiAssistantStore } from '@/stores/ai/assistant'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'

const store = useAiAssistantStore()
const farm = useFarmStore()
const ui = useUiStore()

const farmLine = computed(() => `${farm.farmName || '当前养殖场'} · 只显示你本人的会话`)

function topicIcon(topic?: string): string {
  switch (topic) {
    case 'MEDICATION':
      return 'medication'
    case 'FEEDING':
      return 'rice_bowl'
    case 'DISEASE':
      return 'healing'
    case 'IMMUNE':
      return 'vaccines'
    case 'DISINFECT':
      return 'sanitizer'
    default:
      return 'question_answer'
  }
}

function timeLabel(item: AiSessionItem): string {
  const time = item.last_asked_at ? dayjs.tz(item.last_asked_at, TZ) : null
  if (!item.last_asked_at || !time?.isValid()) return ''
  const now = dayjs().tz(TZ)
  if (item.date_group === 'TODAY' || item.date_group === 'YESTERDAY') return time.format('HH:mm')
  if (time.isSame(now, 'year')) return time.format('M月D日')
  return time.format('YYYY年M月D日')
}

function subtitle(item: AiSessionItem): string {
  const batch = item.batch_no || '未关联批次'
  const rounds = `${item.round_count || 0} 轮`
  const time = timeLabel(item)
  return `${batch} · ${rounds}${time ? ` · ${time}` : ''}`
}

function open(item: AiSessionItem): void {
  void store.openSession(item.id)
}

function newChat(): void {
  if (store.newChat()) ui.toast('已开始新对话')
}

function retry(): void {
  void store.loadSessions()
}
</script>

<template>
  <aside class="k01-side" aria-label="历史会话">
    <div class="k01-side-head">
      <div>
        <div class="k01-side-title">历史会话</div>
        <div class="k01-side-farm">{{ farmLine }}</div>
      </div>
      <v-btn block variant="outlined" color="primary" prepend-icon="add_comment" @click="newChat">新对话</v-btn>
      <v-text-field
        :model-value="store.sessionKeyword"
        placeholder="搜索会话"
        prepend-inner-icon="search"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        @update:model-value="store.setSessionKeyword(String($event || ''))"
        @click:clear="store.setSessionKeyword('')"
      />
    </div>

    <div class="k01-side-list">
      <template v-if="store.sessionsStatus === 'loading' && !store.sessions.length">
        <div v-for="index in 6" :key="index" class="k01-sess-skel">
          <span class="k01-skel-ico" />
          <span class="k01-skel-text">
            <i />
            <i class="short" />
          </span>
        </div>
      </template>

      <div v-else-if="store.sessionsStatus === 'error' || store.sessionsStatus === 'forbidden'" class="k01-side-state">
        <zw-empty
          icon="cloud_off"
          :tone="store.sessionsStatus === 'forbidden' ? 'warning' : 'error'"
          :title="store.sessionsStatus === 'forbidden' ? '无权查看' : '会话加载失败'"
          :desc="store.sessionsError"
        >
          <v-btn variant="outlined" size="small" prepend-icon="refresh" @click="retry">重新加载</v-btn>
        </zw-empty>
      </div>

      <div v-else-if="!store.sessions.length" class="k01-side-state">
        <zw-empty
          v-if="store.sessionKeyword"
          icon="search_off"
          title="没有找到相关会话"
          desc="换个关键词试试"
        />
        <zw-empty
          v-else
          icon="history"
          title="还没有历史会话"
          desc="在 AI 养殖助手中提出第一个问题后，会话会保存在这里。"
        />
      </div>

      <template v-else>
        <template v-for="group in store.groupedSessions" :key="group.label">
          <div v-if="group.label" class="k01-group">{{ group.label }}</div>
          <button
            v-for="item in group.items"
            :key="item.id"
            type="button"
            class="k01-sess"
            :class="{ on: item.id === store.currentSessionId }"
            @click="open(item)"
          >
            <span class="k01-sess-ico"><zw-icon :name="topicIcon(item.topic)" :size="16" /></span>
            <span class="k01-sess-txt">
              <span class="k01-sess-title">{{ item.title || '未命名会话' }}</span>
              <span class="k01-sess-sub">{{ subtitle(item) }}</span>
              <span v-if="item.has_correction || item.context_truncated || item.has_epidemic_alert" class="k01-sess-tags">
                <zw-tag v-if="item.has_correction" size="small" icon="verified">有更正说明</zw-tag>
                <zw-tag v-if="item.context_truncated" size="small">上下文为最近 10 轮</zw-tag>
                <zw-tag v-if="item.has_epidemic_alert" size="small" type="error">疑似重大疫病</zw-tag>
              </span>
            </span>
          </button>
        </template>

        <div v-if="store.sessionsHasMore" class="k01-side-more">
          <v-btn variant="text" size="small" color="primary" :loading="store.sessionsLoadingMore" @click="store.loadMoreSessions()">
            加载更多
          </v-btn>
        </div>
        <div v-else-if="store.sessions.length >= 20" class="k01-side-more k01-faint body-s">
          已显示全部 {{ store.sessionsTotal }} 条
        </div>
      </template>
    </div>

    <div class="k01-side-foot">
      <zw-icon name="info" :size="14" /><span>点开会话可以继续提问。同一会话保留最近 10 轮问答作为上下文，更早的问答不再参与回答。</span>
    </div>
  </aside>
</template>

<style scoped>
.k01-side {
  width: 264px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--fill-1);
  border-right: 1px solid var(--line-1);
  min-height: 0;
}
.k01-side-head {
  padding: 16px 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.k01-side-title {
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}
.k01-side-farm {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.k01-side-list {
  flex: 1;
  overflow: auto;
  padding: 0 8px 8px;
  min-height: 0;
}
.k01-group {
  padding: 12px 8px 4px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k01-sess {
  display: flex;
  gap: 10px;
  width: 100%;
  padding: 8px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  color: var(--text-1);
  text-align: left;
  font-family: inherit;
}
.k01-sess:hover {
  background: var(--fill-2);
}
.k01-sess.on {
  background: var(--primary-subtle);
}
.k01-sess.on .k01-sess-title {
  color: var(--primary);
  font-weight: 500;
}
.k01-sess-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  border: 1px solid var(--line-1);
  color: var(--text-2);
}
.k01-sess.on .k01-sess-ico {
  color: var(--primary);
  border-color: #c6d8f8;
}
.k01-sess-txt {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.k01-sess-title {
  font-size: 14px;
  line-height: 22px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.k01-sess-sub {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.k01-sess-tags {
  display: flex;
  gap: 6px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.k01-side-foot {
  display: flex;
  gap: 6px;
  padding: 12px 16px;
  border-top: 1px solid var(--line-1);
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.k01-side-foot :deep(.zw-mso) {
  margin-top: 1px;
}
.k01-side-state {
  padding: 16px 0;
}
.k01-side-state :deep(.zw-empty) {
  padding: 32px 16px;
}
.k01-side-state :deep(.zw-empty-ico) {
  width: 56px;
  height: 56px;
}
.k01-side-state :deep(.zw-empty-title) {
  font-size: 14px;
  margin-top: 12px;
}
.k01-side-state :deep(.zw-empty-desc) {
  font-size: 12px;
  line-height: 20px;
}
.k01-side-more {
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
}
.k01-sess-skel {
  display: flex;
  gap: 10px;
  padding: 8px;
}
.k01-skel-ico {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: var(--fill-2);
  flex-shrink: 0;
}
.k01-skel-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 3px;
}
.k01-skel-text i {
  display: block;
  height: 10px;
  border-radius: 5px;
  background: var(--fill-2);
}
.k01-skel-text i.short {
  width: 58%;
}
</style>
