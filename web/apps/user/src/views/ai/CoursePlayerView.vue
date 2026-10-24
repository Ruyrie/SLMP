<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dayjs, formatDuration, TZ } from '@zmt/shared'
import type { CourseCard, EntryType, ProgressResult, VideoQuality } from '@/api/ai'
import { useAiCoursePlayerStore } from '@/stores/ai/coursePlayer'
import { useUiStore } from '@/stores/ui'
import { coverKind, learnStatusText, progressPercent } from './course'
import { CourseProgressReporter } from './progress'
import CourseVideoPlayer from './components/CourseVideoPlayer.vue'

const store = useAiCoursePlayerStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

interface PlayerExpose {
  seekTo(seconds: number): void
  play(): void
  pause(): void
  replay(): void
  getPosition(): number
  getDuration(): number
  getQuality(): VideoQuality
}

const player = ref<PlayerExpose | null>(null)
const playPosition = ref(0)
const playPlaying = ref(false)
const liveQuality = ref<VideoQuality>('P720')
const justCompleted = ref(false)
const resumeText = ref('')
const resumeTimer = ref(0)
let reporter: CourseProgressReporter | null = null
let progressStartedFor = 0
let resumeShown = false

const course = computed(() => store.course)
const courseId = computed(() => Number(route.params.id || 0))
const topic = computed(() => store.topic)
const lessons = computed(() => store.topicCourses)
const nextCourse = computed(() => store.nextCourse)
const pageTitle = computed(() => course.value?.title || '视频播放')
const duration = computed(() => Number(course.value?.duration_sec || 0))
const completed = computed(() => !!course.value?.progress?.completed)
const currentPercent = computed(() => {
  if (completed.value) return 100
  return duration.value ? Math.min(100, (playPosition.value / duration.value) * 100) : 0
})
const learnedCount = computed(() => Number(topic.value?.learned_count ?? course.value?.topic?.learned_count ?? 0))
const courseCount = computed(() => Number(topic.value?.course_count ?? course.value?.topic?.course_count ?? 0))
const topicPercent = computed(() => (courseCount.value ? (learnedCount.value / courseCount.value) * 100 : 0))
const updatedText = computed(() => {
  const value = course.value?.updated_at
  if (!value) return ''
  const time = dayjs.tz(value, TZ)
  return time.isValid() ? `${time.format('M月D日')}更新` : ''
})
const metaText = computed(() => {
  const value = course.value
  if (!value) return ''
  return [formatDuration(value.duration_sec), value.source_label, updatedText.value].filter(Boolean).join(' · ')
})
const entryType = computed<EntryType>(() => {
  const from = String(route.query.from || '')
  if (from === 'continue') return 'CONTINUE'
  if (from === 'topic') return 'TOPIC'
  if (from === 'history') return 'HISTORY'
  if (from === 'knowledge') return 'KNOWLEDGE'
  return 'COURSE'
})

const crumbs = computed(() => ['学习与助手', '养殖课堂', pageTitle.value])

onMounted(async () => {
  await loadCourse(courseId.value)
  window.addEventListener('pagehide', onPageHide)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  reporter?.leave(player.value?.getPosition() || playPosition.value)
  reporter = null
  if (resumeTimer.value) window.clearTimeout(resumeTimer.value)
  window.removeEventListener('pagehide', onPageHide)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

watch(
  () => route.params.id,
  async (value, previous) => {
    const nextId = Number(value || 0)
    const oldId = Number(previous || 0)
    if (!nextId || nextId === oldId) return
    reporter?.switchCourse(player.value?.getPosition() || playPosition.value)
    reporter = null
    progressStartedFor = 0
    await loadCourse(nextId)
  }
)

async function loadCourse(id: number): Promise<void> {
  if (!id) return
  await store.load(id)
  resumeShown = false
  justCompleted.value = false
  playPlaying.value = false
  if (store.status === 'ready' && store.course) {
    playPosition.value = Number(store.course.progress?.resume_position_sec || store.course.progress?.position_sec || 0)
    liveQuality.value = 'P720'
    progressStartedFor = 0
  }
}

function onReady(payload: { duration: number; resume: number; quality: VideoQuality }): void {
  const current = course.value
  if (!current) return
  liveQuality.value = payload.quality
  if (payload.resume > 0 && !resumeShown) {
    resumeShown = true
    resumeText.value = `从上次看到的 ${formatDuration(payload.resume)} 继续播放`
    if (resumeTimer.value) window.clearTimeout(resumeTimer.value)
    resumeTimer.value = window.setTimeout(() => {
      resumeText.value = ''
    }, 4000)
  }
  if (progressStartedFor === current.id) return
  progressStartedFor = current.id
  startProgress(payload.resume)
}

function startProgress(position: number): void {
  const current = course.value
  if (!current) return
  reporter?.leave(player.value?.getPosition() || playPosition.value)
  reporter = new CourseProgressReporter({
    onResult: (result: ProgressResult) => {
      store.applyProgress(result)
      if (result.just_completed) justCompleted.value = true
    }
  })
  reporter.begin(current.id, entryType.value, liveQuality.value, position, duration.value)
}

function onTick(payload: { position: number; duration: number; playing: boolean; quality: VideoQuality }): void {
  playPosition.value = payload.position
  playPlaying.value = payload.playing
  liveQuality.value = payload.quality
  reporter?.tick(payload)
}

function onStarted(): void {
  playPlaying.value = true
}

function onPaused(payload: { position: number; duration: number }): void {
  playPlaying.value = false
  playPosition.value = payload.position
  reporter?.pause(payload.position, liveQuality.value)
}

function onSeeked(payload: { position: number; duration: number }): void {
  playPosition.value = payload.position
  reporter?.seek(payload.position, liveQuality.value)
}

function onEnded(): void {
  playPlaying.value = false
  playPosition.value = duration.value
  reporter?.ended(duration.value, duration.value)
}

function onQualityChange(quality: VideoQuality): void {
  liveQuality.value = quality
  reporter?.setQuality(quality)
}

function onPlayerError(message: string): void {
  if (message.includes('画中画') || message.includes('不支持 HLS')) ui.toast(message, 'error')
}

function clickSeekZero(): void {
  player.value?.seekTo(0)
  playPosition.value = 0
  resumeText.value = ''
}

function replay(): void {
  player.value?.seekTo(0)
  player.value?.play()
  playPosition.value = 0
  justCompleted.value = false
  startProgress(0)
}

function switchTo(courseItem: CourseCard, from = 'topic'): void {
  if (!courseItem.id || courseItem.id === courseId.value) return
  reporter?.switchCourse(player.value?.getPosition() || playPosition.value)
  reporter = null
  progressStartedFor = 0
  void router.push({
    name: 'WU-K04',
    params: { id: String(courseItem.id) },
    query: { from }
  })
}

function goNext(): void {
  const next = nextCourse.value
  if (next) switchTo(next, String(route.query.from || 'topic'))
  else if (course.value?.next_course_id) {
    reporter?.switchCourse(player.value?.getPosition() || playPosition.value)
    progressStartedFor = 0
    void router.push({
      name: 'WU-K04',
      params: { id: String(course.value.next_course_id) },
      query: { from: String(route.query.from || 'topic') }
    })
  }
}

function lessonTitle(item: CourseCard): string {
  return `${item.section_no ? `${item.section_no}. ` : ''}${item.title || ''}`
}

function rowPercent(item: CourseCard): number {
  if (item.id === courseId.value) return currentPercent.value
  return progressPercent(item.progress_ratio, item.learn_status === 'COMPLETED')
}

function rowStatus(item: CourseCard): string {
  if (item.id === courseId.value) {
    if (justCompleted.value || completed.value) return '已学完 · 刚刚播放完'
    return '正在播放'
  }
  return `${formatDuration(item.duration_sec)} · ${learnStatusText(item.learn_status)}`
}

function askAi(): void {
  const value = course.value
  if (!value) return
  player.value?.pause()
  reporter?.engagement('ask_ai', player.value?.getPosition() || playPosition.value, liveQuality.value)
  void router.push({
    name: 'WU-K01',
    query: { new: '1', course_id: String(value.id), course_title: value.title || '' }
  })
}

function openKnowledge(): void {
  const value = course.value
  if (!value?.knowledge_id) return
  player.value?.pause()
  reporter?.engagement('knowledge', player.value?.getPosition() || playPosition.value, liveQuality.value)
  void router.push({ name: 'WU-K02', params: { id: String(value.knowledge_id) } })
}

async function copyLink(): Promise<void> {
  if (!course.value) return
  try {
    await navigator.clipboard.writeText(`${window.location.origin}/courses/${course.value.id}`)
    ui.toast('已复制链接')
  } catch {
    ui.toast('复制失败，请手动复制地址栏链接', 'error')
  }
}

function goTopic(): void {
  const value = topic.value
  if (!value) {
    void router.push({ name: 'WU-K03' })
    return
  }
  void router.push({ name: 'WU-K03', query: { topic: String(value.id) } })
}

function onPageHide(): void {
  reporter?.leave(player.value?.getPosition() || playPosition.value)
}

function onVisibilityChange(): void {
  if (document.visibilityState === 'hidden') {
    reporter?.leave(player.value?.getPosition() || playPosition.value)
  } else if (progressStartedFor === courseId.value) {
    startProgress(player.value?.getPosition() || playPosition.value)
  }
}
</script>

<template>
  <zw-shell side="user" active="WU-K04" :crumbs="crumbs">
    <div v-if="store.status === 'loading'" class="k04">
      <div class="k04-main">
        <div class="k04-player-skel"><v-progress-circular indeterminate size="48" width="3" color="white" /></div>
        <zw-card class="k04-info">
          <v-skeleton-loader type="article, text@2" />
        </zw-card>
      </div>
      <zw-card class="k04-side" flush>
        <v-skeleton-loader type="list-item-three-line@5" />
      </zw-card>
    </div>

    <zw-card v-else-if="store.offShelf" class="k04-state">
      <zw-empty icon="visibility_off" title="这个视频已下架" desc="课程列表和专题中已不再显示这个视频。">
        <v-btn variant="tonal" color="primary" prepend-icon="arrow_back" @click="router.push({ name: 'WU-K03' })">返回养殖课堂</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error' || store.status === 'forbidden'" class="k04-state">
      <zw-empty
        icon="cloud_off"
        :tone="store.status === 'forbidden' ? 'warning' : 'error'"
        :title="store.status === 'forbidden' ? '无权查看' : '视频加载失败'"
        :desc="store.error"
      >
        <v-btn variant="outlined" prepend-icon="refresh" @click="loadCourse(courseId)">重新加载</v-btn>
        <v-btn variant="text" @click="router.push({ name: 'WU-K03' })">返回课堂</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="course">
      <div class="k04">
        <div class="k04-main">
          <CourseVideoPlayer
            ref="player"
            :sources="course.sources || []"
            :resume-position="course.progress?.resume_position_sec || 0"
            :autoplay="true"
            :duration-hint="course.duration_sec || 0"
            :has-next="!!course.next_course_id"
            @ready="onReady"
            @tick="onTick"
            @started="onStarted"
            @paused="onPaused"
            @seeked="onSeeked"
            @ended="onEnded"
            @next="goNext"
            @quality-change="onQualityChange"
            @error="onPlayerError"
          >
            <template #ended>
              <div class="k04-end-t">本节已播放完</div>
              <div v-if="nextCourse" class="k04-end-s">下一节：{{ lessonTitle(nextCourse) }} · {{ formatDuration(nextCourse.duration_sec) }}</div>
              <div v-else class="k04-end-s">本专题已全部播放完</div>
              <div class="k04-end-btns">
                <v-btn variant="outlined" prepend-icon="replay" @click.stop="replay">重播</v-btn>
                <v-btn v-if="nextCourse" color="primary" prepend-icon="skip_next" @click.stop="goNext">播放下一节</v-btn>
              </div>
            </template>
          </CourseVideoPlayer>

          <zw-card class="k04-info">
            <div class="k04-title">{{ course.title }}</div>
            <div class="k04-meta">
              <zw-tag v-if="course.category_name">{{ course.category_name }}</zw-tag>
              <span class="num">{{ metaText }}</span>
            </div>
            <div class="k04-acts">
              <v-btn variant="tonal" color="primary" prepend-icon="auto_awesome" @click="askAi">问 AI 这个视频</v-btn>
              <v-btn v-if="course.knowledge_id" variant="outlined" prepend-icon="menu_book" @click="openKnowledge">图文原文</v-btn>
              <v-btn variant="text" prepend-icon="link" @click="copyLink">复制链接</v-btn>
            </div>
            <div class="k04-prog">
              <span>本节进度</span>
              <v-progress-linear :model-value="currentPercent" height="6" rounded bg-color="fill-3" class="k04-prog-bar" />
              <span v-if="completed" class="k04-done"><zw-icon name="check" :size="16" />已学完</span>
              <span v-else class="num">看到 {{ formatDuration(playPosition) }} / {{ formatDuration(duration) }}</span>
              <span class="zw-faint k04-note">进度自动保存，App 和网页版同步；看到 90% 以上算已学完</span>
            </div>
          </zw-card>
        </div>

        <zw-card class="k04-side" flush>
          <template #title>
            <button v-if="topic" type="button" class="k04-side-title" @click="goTopic">本专题 · {{ topic.name }}</button>
            <span v-else>相关课程</span>
          </template>
          <template v-if="topic" #extra>
            <span class="k04-side-count num">已学 {{ learnedCount }} / {{ courseCount }}</span>
          </template>
          <div v-if="topic" class="k04-side-sum">
            <v-progress-linear :model-value="topicPercent" height="4" rounded bg-color="fill-3" />
          </div>
          <button
            v-for="item in lessons"
            :key="item.id"
            type="button"
            class="k04-les"
            :class="{ on: item.id === courseId }"
            @click="switchTo(item, String(route.query.from || 'topic'))"
          >
            <span class="k04-les-cover">
              <zw-thumb :kind="coverKind(item.category_name)" class="k04-fill-thumb" />
              <span v-if="item.learn_status !== 'NOT_STARTED' || item.id === courseId" class="k04-bar">
                <i :style="{ width: `${rowPercent(item)}%` }" />
              </span>
            </span>
            <span class="k04-les-txt">
              <span class="k04-les-t">{{ lessonTitle(item) }}</span>
              <span class="k04-les-s">
                <template v-if="item.id === courseId">
                  <zw-icon :name="justCompleted || completed ? 'check' : 'graphic_eq'" :size="14" />
                  {{ rowStatus(item) }}
                </template>
                <template v-else>{{ rowStatus(item) }}</template>
              </span>
            </span>
          </button>
          <div v-if="!lessons.length" class="k04-side-empty">还没有其他课程</div>
          <div class="k04-side-foot">
            <v-btn v-if="topic" variant="text" color="primary" append-icon="chevron_right" @click="goTopic">查看专题详情</v-btn>
            <v-btn v-else variant="text" color="primary" append-icon="chevron_right" @click="router.push({ name: 'WU-K03' })">查看全部课程</v-btn>
          </div>
        </zw-card>
      </div>

      <div v-if="resumeText" class="k04-snack">
        <span>{{ resumeText }}</span>
        <button type="button" @click="clickSeekZero">回到开头</button>
      </div>
    </template>
  </zw-shell>
</template>

<style scoped>
.k04 {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.k04-main {
  flex: 1;
  min-width: 0;
}
.k04-side {
  width: 300px;
  flex-shrink: 0;
}
.k04-player-skel {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-sm);
  background: var(--media-backdrop);
}
.k04-state {
  max-width: 720px;
  margin: 0 auto;
}
.k04-info {
  margin-top: 16px;
}
.k04-title {
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
}
.k04-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-3);
}
.k04-acts {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
}
.k04-prog {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--line-1);
  font-size: 13px;
  color: var(--text-2);
}
.k04-prog-bar {
  flex: 1;
  max-width: 360px;
}
.k04-done {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.k04-note {
  font-size: 12px;
}
.k04-side :deep(.zw-card-head) {
  padding: 0 16px;
}
.k04-side-title {
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}
.k04-side-count {
  font-size: 12px;
  color: var(--text-2);
}
.k04-side-sum {
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-1);
}
.k04-les {
  display: flex;
  gap: 12px;
  width: 100%;
  padding: 10px 16px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.k04-les:hover {
  background: var(--fill-1);
}
.k04-les.on {
  background: var(--primary-subtle);
}
.k04-les-cover {
  position: relative;
  width: 96px;
  height: 54px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.k04-les.on .k04-les-cover {
  outline: 1px solid var(--primary);
}
.k04-fill-thumb {
  position: absolute !important;
  inset: 0;
  width: 100% !important;
  height: 100% !important;
}
.k04-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
  background: #0000004d;
}
.k04-bar i {
  display: block;
  height: 100%;
  background: var(--primary);
}
.k04-les-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.k04-les-t {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.k04-les.on .k04-les-t {
  color: var(--primary);
}
.k04-les-s {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k04-les.on .k04-les-s {
  color: var(--primary);
}
.k04-side-empty {
  padding: 16px;
  font-size: 13px;
  color: var(--text-3);
}
.k04-side-foot {
  display: flex;
  justify-content: center;
  padding: 10px 0;
  border-top: 1px solid var(--line-1);
}
.k04-snack {
  position: fixed;
  top: 72px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 40px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  background: var(--inverse-surface);
  color: var(--inverse-on-surface);
  box-shadow: var(--shadow-dialog);
  font-size: 14px;
}
.k04-snack button {
  border: 0;
  background: none;
  color: var(--inverse-primary);
  font: inherit;
  font-weight: 500;
  cursor: pointer;
  padding: 0 4px;
}
.k04-end-t {
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
  color: var(--on-media);
}
.k04-end-s {
  font-size: 13px;
  line-height: 20px;
  color: #ffffffcc;
}
.k04-end-btns {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}
.k04-end-btns :deep(.v-btn--variant-outlined) {
  background: transparent !important;
  color: var(--on-media) !important;
  border-color: #ffffffb3 !important;
}
</style>
