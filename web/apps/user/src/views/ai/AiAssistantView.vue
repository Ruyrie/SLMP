<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAiAssistantStore } from '@/stores/ai/assistant'
import { useFarmReload } from '@/composables/useFarmReload'
import { useFarmStore } from '@/stores/farm'
import { formatNumber } from '@zmt/shared'
import AiHistoryPanel from './components/AiHistoryPanel.vue'
import AiChatThread from './components/AiChatThread.vue'
import AiComposer from './components/AiComposer.vue'
import AiReferencePanel from './components/AiReferencePanel.vue'

const store = useAiAssistantStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const batchMenu = ref(false)
const composer = ref<InstanceType<typeof AiComposer> | null>(null)

const batchLabel = computed(() => {
  const batch = store.currentBatch
  if (!batch) return '未关联批次'
  const parts = [
    batch.batch_no || '',
    batch.stage_name || batch.category_name || '',
    batch.day_age ? `${batch.day_age} 日龄` : '',
    batch.current_count !== undefined ? `存栏 ${formatNumber(batch.current_count)} ${batch.count_unit_text || farm.countUnit}` : ''
  ]
  return parts.filter(Boolean).join(' · ')
})
const panelHint = computed(() => {
  return store.panelHint || '只能关联当前养殖场的在栏批次。提问时会带上批次的品种、日龄、存栏和最近一次称重体重。'
})

onMounted(async () => {
  const query = route.query
  await store.bootstrap({
    sessionId: Number(query.session || 0) || undefined,
    isNew: query.new === '1',
    question: typeof query.q === 'string' ? query.q : undefined,
    courseId: Number(query.course_id || 0) || undefined,
    courseTitle: typeof query.course_title === 'string' ? query.course_title : undefined,
    messageId: Number(query.message || 0) || undefined
  })
  syncRoute()
  if (store.draft) composer.value?.focusInput()
})

useFarmReload(async () => {
  store.resetForFarm()
  await store.bootstrap({})
  syncRoute()
})

onBeforeUnmount(() => {
  store.leave()
})

watch(
  () => store.currentSessionId,
  () => syncRoute()
)

function syncRoute(): void {
  if (!store.initialized) return
  const next: Record<string, string> = {}
  for (const [key, value] of Object.entries(route.query)) {
    if (typeof value === 'string') next[key] = value
  }
  if (store.currentSessionId) {
    next.session = String(store.currentSessionId)
    delete next.new
    delete next.message
    delete next.course_id
    delete next.course_title
  } else {
    delete next.session
    next.new = '1'
  }
  if (next.q && !store.draft) delete next.q
  const current = JSON.stringify(route.query)
  const target = JSON.stringify(next)
  if (current !== target) void router.replace({ name: 'WU-K01', query: next })
}

function chooseBatch(batchId: number | null): void {
  batchMenu.value = false
  void store.changeBatch(batchId)
}

function goReport(): void {
  const batch = store.currentBatch
  const query: Record<string, string> = { action: 'report' }
  if (batch?.pen_id) query.penId = String(batch.pen_id)
  if (batch?.batch_id) query.batchId = String(batch.batch_id)
  void router.push({ name: 'WU-I03', query })
}

function clearContextCourse(): void {
  store.clearContextCourse()
}
</script>

<template>
  <zw-shell side="user" active="WU-K01" :crumbs="['学习与助手', 'AI 养殖助手']" :pad="false">
    <div class="k01">
      <AiHistoryPanel />

      <section class="k01-main">
        <header class="k01-head">
          <span class="k01-head-label">关联批次</span>
          <v-menu v-model="batchMenu" location="bottom start" :close-on-content-click="false">
            <template #activator="{ props: activatorProps }">
              <button v-bind="activatorProps" type="button" class="k01-batch" :class="{ on: batchMenu }">
                <zw-icon name="link" :size="18" />
                <span class="k01-batch-label">{{ batchLabel }}</span>
                <zw-icon :name="batchMenu ? 'keyboard_arrow_up' : 'keyboard_arrow_down'" :size="18" />
              </button>
            </template>
            <div class="zw-pop k01-bpop">
              <div class="k01-bpop-head">
                <div class="k01-bpop-title">关联批次</div>
                <div class="k01-bpop-desc">{{ panelHint }}</div>
              </div>
              <div v-if="store.batchesStatus === 'loading'" class="k01-bpop-state">
                <v-progress-circular indeterminate size="20" width="2" color="primary" />
                <span>批次加载中…</span>
              </div>
              <div v-else-if="store.batchesStatus === 'error' || store.batchesStatus === 'forbidden'" class="k01-bpop-state">
                <span class="k01-bpop-error">{{ store.batchesError || '批次加载失败' }}</span>
                <button type="button" class="zw-link" @click="store.loadBatches()">重新加载</button>
              </div>
              <div v-else-if="!store.batches.length" class="k01-bpop-state">
                <span>{{ store.emptyHint || '当前没有可关联的在栏批次' }}</span>
              </div>
              <template v-else>
                <button
                  v-for="batch in store.batches"
                  :key="batch.batch_id"
                  type="button"
                  class="k01-bitem"
                  :class="{ on: store.currentBatchId === batch.batch_id }"
                  @click="chooseBatch(batch.batch_id)"
                >
                  <zw-icon
                    :name="store.currentBatchId === batch.batch_id ? 'radio_button_checked' : 'radio_button_unchecked'"
                    :size="20"
                    class="k01-bradio"
                  />
                  <span class="k01-btxt">
                    <span class="k01-bmain"><span class="num">{{ batch.batch_no }}</span> · {{ batch.breed_name || batch.stage_name }}</span>
                    <span class="k01-bsub">
                      {{ batch.pen_name || '' }}<template v-if="batch.day_age"> · {{ batch.day_age }} 日龄</template><template v-if="batch.current_count !== undefined"> · 存栏 {{ formatNumber(batch.current_count) }} {{ batch.count_unit_text || farm.countUnit }}</template>
                    </span>
                  </span>
                  <zw-tag v-if="store.currentBatchId === batch.batch_id" type="primary" size="small">当前</zw-tag>
                </button>
              </template>
              <div class="zw-pop-div" />
              <button type="button" class="k01-bitem" :class="{ on: !store.currentBatchId }" @click="chooseBatch(null)">
                <zw-icon :name="!store.currentBatchId ? 'radio_button_checked' : 'radio_button_unchecked'" :size="20" class="k01-bradio" />
                <span class="k01-btxt">
                  <span class="k01-bmain">不关联批次</span>
                  <span class="k01-bsub">按通用养殖知识回答</span>
                </span>
              </button>
            </div>
          </v-menu>

          <span v-if="store.contextCourse" class="k01-context">
            <span>正在问：{{ store.contextCourse.title }}</span>
            <button type="button" aria-label="取消背景" @click="clearContextCourse">
              <zw-icon name="close" :size="14" />
            </button>
          </span>
        </header>

        <div v-if="store.hasEpidemic" class="k01-banner">
          <zw-notice type="error" icon="emergency">
            <b>疑似重大动物疫病。</b>请联系执业兽医，并按规定向当地动物防疫机构报告。
            <template #action><a @click="goReport">去异常上报</a></template>
          </zw-notice>
        </div>

        <AiChatThread />
        <AiComposer ref="composer" />
      </section>

      <AiReferencePanel />
    </div>
  </zw-shell>
</template>

<style scoped>
.k01 {
  display: flex;
  height: 100%;
  min-height: 0;
  background: var(--bg-card);
  overflow: hidden;
}
.k01-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.k01-head {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  border-bottom: 1px solid var(--line-1);
  min-width: 0;
}
.k01-head-label {
  font-size: 14px;
  color: var(--text-2);
  white-space: nowrap;
}
.k01-batch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  height: 32px;
  padding: 0 8px 0 10px;
  border: 1px solid var(--control-border);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-1);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}
.k01-batch:hover,
.k01-batch.on {
  border-color: var(--primary);
}
.k01-batch > :deep(.zw-mso:first-child) {
  color: var(--primary);
}
.k01-batch > :deep(.zw-mso:last-child) {
  color: var(--text-2);
}
.k01-batch-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.k01-context {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: 4px 6px 4px 10px;
  border-radius: var(--radius-sm);
  background: var(--fill-2);
  color: var(--text-2);
  font-size: 12px;
  white-space: nowrap;
  max-width: 260px;
}
.k01-context > span {
  overflow: hidden;
  text-overflow: ellipsis;
}
.k01-context button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
}
.k01-context button:hover {
  background: var(--fill-3);
}
.k01-banner {
  padding: 12px 24px 0;
  flex-shrink: 0;
}
.k01-bpop {
  width: 420px;
  padding: 0 0 8px;
  max-height: 480px;
  overflow: auto;
}
.k01-bpop-head {
  padding: 14px 16px 10px;
}
.k01-bpop-title {
  font-size: 14px;
  line-height: 22px;
  font-weight: 600;
}
.k01-bpop-desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k01-bpop-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 13px;
  color: var(--text-2);
}
.k01-bpop-error {
  color: var(--error);
}
.k01-bitem {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 8px 16px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: var(--text-1);
}
.k01-bitem:hover {
  background: var(--fill-1);
}
.k01-bitem.on {
  background: var(--primary-subtle);
}
.k01-bradio {
  color: var(--text-3);
}
.k01-bitem.on .k01-bradio {
  color: var(--primary);
}
.k01-btxt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.k01-bmain {
  font-size: 14px;
  line-height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.k01-bsub {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
