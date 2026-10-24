/** WU-K04 视频播放：播放信息、本专题课程列表与学习进度同步。 */
import { defineStore } from 'pinia'
import {
  fetchCoursePlay,
  fetchTopic,
  type CourseCard,
  type CoursePlay,
  type PageStatus,
  type ProgressResult,
  type TopicDetail
} from '@/api/ai'
import { errorTextOf, isOffShelf, statusForError } from './shared'

export const useAiCoursePlayerStore = defineStore('aiCoursePlayer', {
  state: () => ({
    courseId: 0,
    status: 'loading' as PageStatus,
    error: '',
    offShelf: false,
    course: null as CoursePlay | null,
    topic: null as TopicDetail | null,
    related: [] as CourseCard[],
    requestId: 0
  }),
  getters: {
    topicCourses(state): CourseCard[] {
      if (state.topic?.courses?.length) return state.topic.courses
      return state.related
    },
    nextCourse(state): CourseCard | null {
      const nextId = state.course?.next_course_id
      if (!nextId) return null
      return this.topicCourses.find((item) => item.id === nextId) || null
    },
    currentIndex(): number {
      return this.topicCourses.findIndex((item) => item.id === this.courseId)
    }
  },
  actions: {
    async load(courseId: number): Promise<void> {
      const requestId = ++this.requestId
      this.courseId = courseId
      this.status = 'loading'
      this.error = ''
      this.offShelf = false
      this.course = null
      this.topic = null
      this.related = []
      try {
        const course = await fetchCoursePlay(courseId)
        if (requestId !== this.requestId) return
        this.course = course
        this.status = 'ready'
        if (course.topic?.id) {
          void this.loadTopic(course.topic.id, requestId)
        } else {
          this.related = course.related_courses || []
        }
      } catch (error) {
        if (requestId !== this.requestId) return
        this.offShelf = isOffShelf(error)
        this.status = statusForError(error)
        this.error = errorTextOf(error, '课程加载失败')
      }
    },
    async loadTopic(topicId: number, requestId: number): Promise<void> {
      try {
        const topic = await fetchTopic(topicId)
        if (requestId !== this.requestId) return
        this.topic = topic
      } catch {
        /* 专题列表失败不影响播放，右栏退回相关课程 */
        if (requestId === this.requestId) this.related = this.course?.related_courses || []
      }
    },
    applyProgress(result: ProgressResult): void {
      const course = this.course
      if (!course) return
      const position = Number(result.position_sec ?? 0)
      const completed = !!result.completed
      course.progress = {
        ...(course.progress || {}),
        position_sec: position,
        completed
      }
      const learnedStatus = result.learn_status || (completed ? 'COMPLETED' : position > 0 ? 'LEARNING' : 'NOT_STARTED')
      const ratio = Number(result.progress_ratio ?? 0)
      if (this.topic) {
        if (typeof result.topic_learned_count === 'number') this.topic.learned_count = result.topic_learned_count
        const item = this.topic.courses?.find((row) => row.id === this.courseId)
        if (item) {
          item.position_sec = position
          item.progress_ratio = ratio
          item.learn_status = learnedStatus
        }
      }
      if (course.topic) {
        if (typeof result.topic_learned_count === 'number') course.topic.learned_count = result.topic_learned_count
      }
      const related = this.related.find((row) => row.id === this.courseId)
      if (related) {
        related.position_sec = position
        related.progress_ratio = ratio
        related.learn_status = learnedStatus
      }
    }
  }
})
