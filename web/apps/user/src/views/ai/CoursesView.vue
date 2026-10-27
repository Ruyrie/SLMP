<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dayjs, formatDuration, TZ } from '@zmt/shared'
import type { CourseCard, TopicCard, WatchHistory } from '@/api/ai'
import { useAiCoursesStore } from '@/stores/ai/courses'
import { useFarmReload } from '@/composables/useFarmReload'
import { useUiStore } from '@/stores/ui'
import { courseWatchText, coverKind, courseProgressPercent, topicDurationText } from './course'
import CourseCover from './components/CourseCover.vue'

const store = useAiCoursesStore()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const learnStatusItems = [
  { title: '全部学习状态', value: '' },
  { title: '未观看', value: 'NOT_STARTED' },
  { title: '学习中', value: 'LEARNING' },
  { title: '已学完', value: 'COMPLETED' }
]
const sortItems = [
  { title: '最近上架', value: '' },
  { title: '时长从短到长', value: 'DURATION_ASC' }
]

const continueItem = computed(() => store.continueWatching)
const continueSub = computed(() => {
  const item = continueItem.value
  if (!item) return ''
  const section = item.section_no ? `第 ${item.section_no} 节` : ''
  return [item.category_name, item.topic_name, section].filter(Boolean).join(' · ')
})
const continueProgress = computed(() => {
  const item = continueItem.value
  if (!item) return 0
  return courseProgressPercent(item)
})
const continueTimeText = computed(() => {
  const item = continueItem.value
  if (!item) return ''
  return `看到 ${formatDuration(item.position_sec)} · 还剩 ${formatDuration(item.remaining_sec)}`
})

const topicSub = computed(() => (topic: TopicCard) => `${topic.category_name || ''} · ${topicDurationText(topic.course_count, topic.total_duration_sec)}`)
const topicProgress = computed(() => (topic: TopicCard) => {
  const total = Number(topic.course_count || 0)
  return total ? (Number(topic.learned_count || 0) / total) * 100 : 0
})

const topicDetail = computed(() => store.topicDetail)
const topicTitle = computed(() => {
  if (store.topicStatus === 'offshelf') return '专题已下架'
  return topicDetail.value?.name || '专题详情'
})
const topicDrawerSub = computed(() => {
  const topic = topicDetail.value
  if (!topic) return ''
  return `${topic.category_name || ''} · 共 ${topic.course_count || 0} 节 · 约 ${Math.round((topic.total_duration_sec || 0) / 60)} 分钟 · 平台知识库`
})
const topicProgressText = computed(() => {
  const topic = topicDetail.value
  if (!topic) return ''
  return `已学 ${topic.learned_count || 0} / ${topic.course_count || 0}`
})
const topicResumeText = computed(() => {
  const topic = topicDetail.value
  if (!topic) return ''
  if (topic.resume?.course_id) {
    const course = topic.courses?.find((item) => item.id === topic.resume?.course_id)
    const section = topic.resume.section_no || course?.section_no || 0
    const name = course?.title ? ` · ${course.title}` : ''
    return `从第 ${section} 节 ${formatDuration(topic.resume.position_sec)} 继续${name}`
  }
  if (Number(topic.learned_count || 0) >= Number(topic.course_count || 0) && topic.course_count) return '全部课程已学完'
  if (Number(topic.learned_count || 0) > 0) return `已学 ${topic.learned_count} / ${topic.course_count}`
  return '还没有开始学习'
})
const topicActionText = computed(() => {
  const action = topicDetail.value?.action
  if (action === 'CONTINUE') return '继续学习'
  if (action === 'REVIEW') return '开始复习'
  return '开始学习'
})
const topicResumeCourseId = computed(() => {
  const topic = topicDetail.value
  if (!topic) return 0
  return topic.resume?.course_id || topic.courses?.[0]?.id || 0
})

const coursesEmptyTitle = computed(() => {
  if (store.query.keyword.trim()) return '没有找到相关课程'
  if (store.query.categoryId) return '这个分类还没有课程'
  if (store.hasFilters) return '没有符合筛选条件的课程'
  return '课程还在准备中'
})
const coursesEmptyDesc = computed(() => {
  if (store.query.keyword.trim()) return '换个关键词试试，或清除搜索条件。'
  if (store.query.categoryId) return '先看看其他分类，或查看全部课程。'
  if (store.hasFilters) return '换一个学习状态或排序方式试试。'
  return '上架后会出现在这里。'
})

onMounted(async () => {
  await store.bootstrap()
  const topicId = Number(route.query.topic || 0)
  if (topicId) await openTopic(topicId, false)
})

useFarmReload(async () => {
  await store.reload()
})

function goCourse(courseId?: number, from = 'course'): void {
  if (!courseId) return
  void router.push({ name: 'WU-K04', params: { id: String(courseId) }, query: { from } })
}

function goContinue(): void {
  if (continueItem.value) goCourse(continueItem.value.course_id, 'continue')
}

function openTopic(topicId: number, syncQuery = true): void {
  if (syncQuery) {
    const query = { ...route.query, topic: String(topicId) }
    void router.replace({ name: 'WU-K03', query })
  }
  void store.openTopic(topicId)
}

function onTopicClick(topic: TopicCard): void {
  openTopic(topic.id)
}

function onTopicDrawer(value: boolean): void {
  store.topicDrawer = value
  if (!value) {
    store.closeTopic()
    if (route.query.topic) {
      const query = { ...route.query }
      delete query.topic
      void router.replace({ name: 'WU-K03', query })
    }
  }
}

function lessonTitle(item: CourseCard): string {
  const section = item.section_no ? `${item.section_no}. ` : ''
  return `${section}${item.title || ''}`
}

function lessonStatus(item: CourseCard): string {
  if (item.learn_status === 'COMPLETED') return '已学完'
  if (item.learn_status === 'LEARNING') return '学到一半'
  return '未观看'
}

function lessonTime(item: CourseCard): string {
  if (item.learn_status === 'LEARNING') {
    return `看到 ${formatDuration(item.position_sec)} / ${formatDuration(item.duration_sec)}`
  }
  return formatDuration(item.duration_sec)
}

function historyWhen(value?: string | null): string {
  if (!value) return ''
  const time = dayjs.tz(value, TZ)
  if (!time.isValid()) return value
  const now = dayjs().tz(TZ)
  if (time.isSame(now, 'day')) return `今天 ${time.format('HH:mm')}`
  if (time.isSame(now.subtract(1, 'day'), 'day')) return `昨天 ${time.format('HH:mm')}`
  return time.format('M月D日')
}

function goTopicResume(): void {
  const courseId = topicResumeCourseId.value
  if (!courseId) return
  store.topicDrawer = false
  goCourse(courseId, 'topic')
}

function goHistory(item: WatchHistory): void {
  store.historyDrawer = false
  goCourse(item.id, 'history')
}

async function copyTopicLink(): Promise<void> {
  const topic = topicDetail.value
  if (!topic) return
  try {
    await navigator.clipboard.writeText(`${window.location.origin}/courses?topic=${topic.id}`)
    ui.toast('已复制链接')
  } catch {
    ui.toast('复制失败，请手动复制地址栏链接', 'error')
  }
}

function retryAll(): void {
  void store.bootstrap()
}

function onSearch(value: string): void {
  store.setKeyword(value)
}

function clearFilters(): void {
  store.resetFilters()
}
</script>

<template>
  <zw-shell side="user" active="WU-K03" :crumbs="['学习与助手', '养殖课堂']">
    <zw-page-head title="养殖课堂" desc="学习进度按账号记录，App 和网页版同步；看到 90% 以上算已学完。">
      <template #actions>
        <v-text-field
          :model-value="store.query.keyword"
          placeholder="搜索课程"
          prepend-inner-icon="search"
          class="zw-w-240"
          clearable
          @update:model-value="onSearch(String($event || ''))"
          @click:clear="onSearch('')"
        />
        <v-btn variant="outlined" prepend-icon="history" @click="store.openHistory()">观看记录</v-btn>
      </template>
    </zw-page-head>

    <div class="k03-top">
      <zw-card v-if="store.continueStatus === 'loading'" title="继续观看" class="k03-cont-card">
        <v-skeleton-loader type="image" />
      </zw-card>
      <zw-card v-else-if="store.continueStatus === 'error'" title="继续观看" class="k03-cont-card">
        <div class="k03-inline-error">
          <span>{{ store.continueError }}</span>
          <button type="button" class="zw-link" @click="store.loadContinue()">重新加载</button>
        </div>
      </zw-card>
      <zw-card v-else-if="continueItem" title="继续观看" class="k03-cont-card">
        <div class="k03-cont" role="button" tabindex="0" @click="goContinue" @keydown.enter="goContinue">
          <span class="k03-cont-cover">
            <CourseCover :src="continueItem.cover_url" :alt="continueItem.title" :kind="coverKind(continueItem.category_name)" class="k03-fill-thumb" />
            <span class="k03-play"><zw-icon name="play_arrow" :size="24" fill /></span>
            <span class="k03-bar"><i :style="{ width: `${continueProgress}%` }" /></span>
          </span>
          <span class="k03-cont-txt">
            <span class="k03-cont-t">{{ continueItem.title }}</span>
            <span class="k03-cont-s">{{ continueSub }}</span>
            <span class="k03-cont-p num">{{ continueTimeText }}</span>
            <v-btn color="primary" prepend-icon="play_arrow" class="mt-3" @click.stop="goContinue">继续播放</v-btn>
          </span>
        </div>
      </zw-card>

      <zw-card title="专题" :sub="store.topicsTotal ? `${store.topicsTotal} 个` : ''" class="k03-topic-card">
        <template #extra>
          <button v-if="store.topics.length" type="button" class="zw-link k03-link" @click="store.toggleTopics()">
            {{ store.topicsExpanded ? '收起' : '全部专题' }}
          </button>
        </template>
        <div v-if="store.topicsStatus === 'loading'" class="zw-grid-2">
          <v-skeleton-loader v-for="index in 2" :key="index" type="list-item-avatar-two-line" />
        </div>
        <div v-else-if="store.topicsStatus === 'error'" class="k03-inline-error">
          <span>{{ store.topicsError }}</span>
          <button type="button" class="zw-link" @click="store.loadTopics()">重新加载</button>
        </div>
        <div v-else-if="!store.topics.length" class="k03-inline-empty">还没有专题，管理员上架后会出现在这里。</div>
        <div v-else class="zw-grid-2">
          <button
            v-for="topic in store.topicCards"
            :key="topic.id"
            type="button"
            class="k03-topic"
            :class="{ on: store.topicDrawer && store.topicId === topic.id }"
            @click="onTopicClick(topic)"
          >
            <CourseCover :src="topic.cover_url" :alt="topic.name" :kind="coverKind(topic.category_name)" :w="64" :h="64" :radius="4" />
            <span class="k03-topic-txt">
              <span class="k03-topic-t">{{ topic.name }}</span>
              <span class="k03-topic-s">{{ topicSub(topic) }}</span>
              <span class="k03-topic-p">
                <v-progress-linear :model-value="topicProgress(topic)" height="4" rounded bg-color="fill-3" />
                <span class="num">已学 {{ topic.learned_count || 0 }} / {{ topic.course_count || 0 }}</span>
              </span>
            </span>
          </button>
        </div>
      </zw-card>
    </div>

    <zw-card title="全部课程" :sub="store.coursesSubText">
      <div class="zw-toolbar">
        <div class="zw-toolbar-l">
          <zw-seg :model-value="store.query.categoryId" :items="store.categoryItems" @update:model-value="store.setCategory(Number($event))" />
        </div>
        <div class="zw-toolbar-r">
          <v-select
            :model-value="store.query.learnStatus"
            :items="learnStatusItems"
            item-title="title"
            item-value="value"
            class="zw-w-160"
            @update:model-value="store.setLearnStatus(String($event ?? ''))"
          />
          <v-select
            :model-value="store.query.sort"
            :items="sortItems"
            item-title="title"
            item-value="value"
            class="zw-w-140"
            @update:model-value="store.setSort(String($event ?? ''))"
          />
        </div>
      </div>

      <div v-if="store.coursesStatus === 'loading' && !store.courses.length" class="k03-grid">
        <div v-for="index in 10" :key="index" class="k03-skel">
          <div class="k03-skel-cover" />
          <div class="k03-skel-line" />
          <div class="k03-skel-line short" />
        </div>
      </div>

      <zw-empty
        v-else-if="store.coursesStatus === 'error' || store.coursesStatus === 'forbidden'"
        icon="cloud_off"
        :tone="store.coursesStatus === 'forbidden' ? 'warning' : 'error'"
        :title="store.coursesStatus === 'forbidden' ? '无权查看' : '课程加载失败'"
        :desc="store.coursesError"
      >
        <v-btn variant="outlined" prepend-icon="refresh" @click="retryAll">重新加载</v-btn>
      </zw-empty>

      <zw-empty
        v-else-if="store.coursesStatus === 'empty' || store.coursesStatus === 'filtered_empty'"
        icon="menu_book"
        :title="coursesEmptyTitle"
        :desc="coursesEmptyDesc"
      >
        <v-btn v-if="store.hasFilters" variant="outlined" @click="clearFilters">查看全部课程</v-btn>
      </zw-empty>

      <template v-else>
        <div class="k03-grid">
          <button
            v-for="course in store.courses"
            :key="course.id"
            type="button"
            class="k03-vc"
            @click="goCourse(course.id, 'course')"
          >
            <span class="k03-cover">
              <CourseCover :src="course.cover_url" :alt="course.title" :kind="coverKind(course.category_name)" class="k03-fill-thumb" />
              <span class="k03-dur num"><zw-icon name="smart_display" :size="14" />{{ formatDuration(course.duration_sec) }}</span>
              <span v-if="course.learn_status !== 'NOT_STARTED'" class="k03-bar">
                <i :style="{ width: `${courseProgressPercent(course)}%` }" />
              </span>
            </span>
            <span class="k03-vt">{{ course.title }}</span>
            <span class="k03-vs">
              {{ course.category_name }} ·
              <template v-if="course.learn_status === 'COMPLETED'"><zw-icon name="check" :size="14" />已学完</template>
              <span v-else-if="course.learn_status === 'LEARNING'" class="on num">{{ courseWatchText(course.learn_status, course.position_sec) }}</span>
              <template v-else>未观看</template>
            </span>
          </button>
        </div>

        <div class="k03-foot">
          <span class="zw-faint body-s">视频由平台管理员上架，提供 720P 和 480P 两档清晰度</span>
          <zw-pager
            :total="store.coursesTotal"
            :page="store.query.page"
            :size="store.query.size"
            @update:page="store.setPage"
            @update:size="store.setSize"
          />
        </div>
      </template>
    </zw-card>

    <!-- 专题详情（页面内自绘抽屉：避免多个 v-navigation-drawer 同时挂载） -->
    <teleport to="body">
      <div v-if="store.topicDrawer" class="k03-scrim" @click.self="onTopicDrawer(false)">
        <aside class="k03-drawer" role="dialog" aria-modal="true" :aria-label="topicTitle">
          <header class="k03-drawer-head">
            <div class="k03-drawer-head-txt">
              <div class="k03-drawer-title">{{ topicTitle }}</div>
              <div v-if="topicDrawerSub" class="k03-drawer-sub">{{ topicDrawerSub }}</div>
            </div>
            <button class="zw-icon-btn" type="button" aria-label="关闭" @click="onTopicDrawer(false)">
              <zw-icon name="close" :size="20" />
            </button>
          </header>
          <div class="k03-drawer-body">
            <template v-if="store.topicStatus === 'loading'">
              <v-skeleton-loader type="article, list-item-three-line@4" />
            </template>
            <zw-empty
              v-else-if="store.topicStatus === 'offshelf'"
              icon="visibility_off"
              title="这个专题已下架"
              :desc="store.topicError"
            />
            <zw-empty
              v-else-if="store.topicStatus === 'error'"
              icon="cloud_off"
              tone="error"
              title="专题加载失败"
              :desc="store.topicError"
            >
              <v-btn variant="outlined" @click="store.openTopic(store.topicId)">重新加载</v-btn>
            </zw-empty>
            <template v-else-if="topicDetail">
              <p class="k03-intro">{{ topicDetail.intro }}</p>
              <div class="k03-prog">
                <div class="zw-between mb-3">
                  <span class="title-s">学习进度</span>
                  <span class="zw-muted num">{{ topicProgressText }}</span>
                </div>
                <v-progress-linear
                  :model-value="topicDetail.course_count ? ((topicDetail.learned_count || 0) / topicDetail.course_count) * 100 : 0"
                  height="6"
                  rounded
                  bg-color="fill-3"
                />
                <div class="k03-prog-sub num">{{ topicResumeText }}</div>
              </div>
              <div class="zw-section-title k03-sec">课程列表 · {{ topicDetail.course_count || 0 }} 节</div>

              <zw-empty v-if="!topicDetail.courses?.length" icon="menu_book" title="专题下还没有课程" desc="管理员上架课程后会出现在这里。" />
              <button
                v-for="lesson in topicDetail.courses"
                :key="lesson.id"
                type="button"
                class="k03-les"
                @click="goCourse(lesson.id, 'topic')"
              >
                <span class="k03-les-cover">
                  <CourseCover :src="lesson.cover_url" :alt="lesson.title" :kind="coverKind(lesson.category_name)" class="k03-fill-thumb" />
                  <span v-if="lesson.learn_status !== 'NOT_STARTED'" class="k03-bar">
                    <i :style="{ width: `${courseProgressPercent(lesson)}%` }" />
                  </span>
                </span>
                <span class="k03-les-txt">
                  <span class="k03-les-t">{{ lessonTitle(lesson) }}</span>
                  <span class="k03-les-s">
                    <zw-tag v-if="lesson.learn_status === 'COMPLETED'" size="small" icon="check">已学完</zw-tag>
                    <zw-tag v-else-if="lesson.learn_status === 'LEARNING'" size="small" type="primary">学到一半</zw-tag>
                    <zw-tag v-else size="small">未观看</zw-tag>
                    <span class="num" :class="{ 'k03-on': lesson.learn_status === 'LEARNING' }">{{ lessonTime(lesson) }}</span>
                  </span>
                </span>
              </button>
            </template>
          </div>
          <footer class="k03-drawer-foot">
            <span class="k03-foot-note">{{ topicResumeText }}</span>
            <v-btn variant="outlined" prepend-icon="link" @click="copyTopicLink">复制链接</v-btn>
            <v-btn color="primary" prepend-icon="play_arrow" :disabled="!topicResumeCourseId" @click="goTopicResume">{{ topicActionText }}</v-btn>
          </footer>
        </aside>
      </div>
    </teleport>

    <!-- 观看记录 -->
    <teleport to="body">
      <div v-if="store.historyDrawer" class="k03-scrim" @click.self="store.historyDrawer = false">
        <aside class="k03-drawer" role="dialog" aria-modal="true" aria-label="观看记录">
          <header class="k03-drawer-head">
            <div class="k03-drawer-head-txt">
              <div class="k03-drawer-title">观看记录</div>
              <div class="k03-drawer-sub">按最近观看时间排序 · App 和网页版同步</div>
            </div>
            <button class="zw-icon-btn" type="button" aria-label="关闭" @click="store.historyDrawer = false">
              <zw-icon name="close" :size="20" />
            </button>
          </header>
          <div class="k03-drawer-body">
            <div v-if="store.historyStatus === 'loading'" class="k03-history-skel">
              <v-skeleton-loader type="list-item-three-line@5" />
            </div>
            <zw-empty
              v-else-if="store.historyStatus === 'error'"
              icon="cloud_off"
              tone="error"
              title="观看记录加载失败"
              :desc="store.historyError"
            >
              <v-btn variant="outlined" @click="store.loadHistory()">重新加载</v-btn>
            </zw-empty>
            <zw-empty
              v-else-if="!store.history.length"
              icon="history"
              title="还没有观看记录"
              desc="看过的视频会按时间列在这里。"
            />
            <template v-else>
              <button v-for="item in store.history" :key="item.id" type="button" class="k03-les" @click="goHistory(item)">
                <span class="k03-les-cover">
                  <CourseCover :src="item.cover_url" :alt="item.title" :kind="coverKind(item.category_name)" class="k03-fill-thumb" />
                  <span class="k03-bar">
                    <i :style="{ width: `${courseProgressPercent(item)}%` }" />
                  </span>
                </span>
                <span class="k03-les-txt">
                  <span class="k03-les-t">{{ item.title }}</span>
                  <span class="k03-les-s">
                    <span>{{ item.category_name }}</span>
                    <zw-tag v-if="item.learn_status === 'COMPLETED'" size="small" icon="check">已学完</zw-tag>
                    <span v-else class="k03-on num">看到 {{ formatDuration(item.position_sec) }} / {{ formatDuration(item.duration_sec) }}</span>
                  </span>
                </span>
                <span class="k03-les-when">{{ historyWhen(item.last_watched_at) }}</span>
              </button>
              <div class="k03-history-more">
                <button v-if="store.historyHasMore" type="button" class="zw-link" :disabled="store.historyLoadingMore" @click="store.loadMoreHistory()">
                  {{ store.historyLoadingMore ? '加载中…' : '加载更多' }}
                </button>
                <span v-else class="zw-faint body-s">已显示全部 {{ store.historyTotal }} 条</span>
              </div>
            </template>
          </div>
        </aside>
      </div>
    </teleport>
  </zw-shell>
</template>

<style scoped>
.k03-scrim {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  justify-content: flex-end;
  background: var(--scrim);
}
.k03-drawer {
  display: flex;
  flex-direction: column;
  width: 560px;
  max-width: 100%;
  height: 100%;
  background: var(--bg-card);
  box-shadow: var(--shadow-dialog);
  animation: k03-slide-in 250ms ease;
}
@keyframes k03-slide-in {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}
.k03-drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 16px 24px;
  border-bottom: 1px solid var(--line-1);
  flex-shrink: 0;
}
.k03-drawer-head-txt {
  min-width: 0;
}
.k03-drawer-title {
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}
.k03-drawer-sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k03-drawer-body {
  flex: 1;
  overflow: auto;
  padding: 24px;
  min-height: 0;
}
.k03-drawer-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 24px;
  border-top: 1px solid var(--line-1);
  background: var(--bg-card);
  flex-shrink: 0;
}
.k03-foot-note {
  margin-right: auto;
  font-size: 12px;
  color: var(--text-3);
}
.k03-top {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  align-items: stretch;
}
.k03-top > :deep(.zw-card) {
  display: flex;
  flex-direction: column;
}
.k03-cont-card {
  width: 440px;
  flex-shrink: 0;
}
.k03-topic-card {
  flex: 1;
  min-width: 0;
}
.k03-link {
  border: 0;
  background: none;
  font: inherit;
  font-size: 14px;
  padding: 0;
}
.k03-cont {
  display: flex;
  gap: 16px;
  align-items: center;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.k03-cont-cover {
  position: relative;
  width: 176px;
  height: 99px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.k03-cont-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.k03-cont-t {
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}
.k03-cont-s {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k03-cont-p {
  margin-top: 6px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.k03-play {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 40px;
  height: 40px;
  margin: -20px 0 0 -20px;
  border-radius: 50%;
  background: #00000080;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.k03-fill-thumb {
  position: absolute !important;
  inset: 0;
  width: 100% !important;
  height: 100% !important;
}
.k03-topic {
  display: flex;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  cursor: pointer;
  background: transparent;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.k03-topic:hover {
  border-color: var(--primary);
}
.k03-topic.on {
  border-color: var(--primary);
  background: var(--primary-subtle);
}
.k03-topic-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.k03-topic-t {
  font-size: 15px;
  line-height: 24px;
  font-weight: 600;
}
.k03-topic-s {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k03-topic-p {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-2);
  white-space: nowrap;
}
.k03-topic-p :deep(.v-progress-linear) {
  flex: 1;
}
.k03-inline-error {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--error);
}
.k03-inline-empty {
  font-size: 13px;
  color: var(--text-3);
  padding: 8px 0;
}
.k03-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 20px 16px;
}
.k03-vc {
  display: block;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-1);
  text-align: left;
  font-family: inherit;
  cursor: pointer;
}
.k03-vc:hover .k03-vt {
  color: var(--primary);
}
.k03-cover {
  position: relative;
  display: block;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--fill-3);
}
.k03-dur {
  position: absolute;
  right: 6px;
  bottom: 8px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 20px;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: #000000a6;
  color: var(--on-media);
  font-size: 12px;
  line-height: 20px;
}
.k03-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
  background: #0000004d;
}
.k03-bar i {
  display: block;
  height: 100%;
  background: var(--primary);
}
.k03-vt {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.k03-vs {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k03-vs .on,
.k03-on {
  color: var(--primary);
}
.k03-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 20px;
}
.k03-intro {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-1);
}
.k03-prog {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  border: 1px solid var(--line-1);
}
.k03-prog-sub {
  margin-top: 8px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k03-sec {
  margin-top: 24px;
}
.k03-les {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 10px 12px;
  margin: 0 -12px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.k03-les:hover {
  background: var(--fill-1);
}
.k03-les-cover {
  position: relative;
  width: 120px;
  height: 68px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.k03-les-txt {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.k03-les-t {
  font-size: 14px;
  line-height: 22px;
  font-weight: 500;
}
.k03-les-s {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-3);
}
.k03-les-when {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}
.k03-history-more {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}
.k03-history-more button {
  border: 0;
  background: none;
  font: inherit;
  font-size: 13px;
}
.k03-skel-cover {
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-sm);
  background: var(--fill-2);
}
.k03-skel-line {
  height: 10px;
  border-radius: 5px;
  background: var(--fill-2);
  margin-top: 10px;
}
.k03-skel-line.short {
  width: 62%;
  margin-top: 8px;
}
.k03-history-skel {
  padding-top: 8px;
}
</style>
