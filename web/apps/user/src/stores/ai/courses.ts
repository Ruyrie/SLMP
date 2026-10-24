/** WU-K03 养殖课堂：继续观看、专题、课程筛选与观看记录。 */
import { defineStore } from 'pinia'
import {
  fetchContinueWatching,
  fetchCourseCategories,
  fetchCourses,
  fetchTopics,
  fetchTopic,
  fetchWatchHistory,
  type ContinueWatching,
  type CourseCard,
  type CourseCategory,
  type PageStatus,
  type TopicCard,
  type TopicDetail,
  type WatchHistory
} from '@/api/ai'
import { errorTextOf, isOffShelf, statusForError } from './shared'

export interface CourseQueryState {
  page: number
  size: number
  categoryId: number
  learnStatus: string
  sort: string
  keyword: string
}

export const useAiCoursesStore = defineStore('aiCourses', {
  state: () => ({
    categories: [] as CourseCategory[],
    categoriesStatus: 'loading' as PageStatus,
    categoriesError: '',
    continueWatching: null as ContinueWatching | null,
    continueStatus: 'loading' as PageStatus,
    continueError: '',
    topics: [] as TopicCard[],
    topicsTotal: 0,
    topicsStatus: 'loading' as PageStatus,
    topicsError: '',
    topicsExpanded: false,
    courses: [] as CourseCard[],
    coursesTotal: 0,
    coursesStatus: 'loading' as PageStatus,
    coursesError: '',
    query: { page: 1, size: 20, categoryId: 0, learnStatus: '', sort: '', keyword: '' } as CourseQueryState,
    keywordTimer: 0,
    requestId: 0,
    topicDrawer: false,
    topicId: 0,
    topicDetail: null as TopicDetail | null,
    topicStatus: 'idle' as 'idle' | 'loading' | 'ready' | 'error' | 'offshelf',
    topicError: '',
    historyDrawer: false,
    history: [] as WatchHistory[],
    historyTotal: 0,
    historyPage: 1,
    historyHasMore: false,
    historyStatus: 'idle' as PageStatus,
    historyError: '',
    historyLoadingMore: false
  }),
  getters: {
    categoryItems(state): Array<{ label: string; value: number }> {
      return [{ label: '全部', value: 0 }, ...state.categories.map((item) => ({ label: item.name || '', value: item.id }))]
    },
    hasFilters(state): boolean {
      return !!state.query.categoryId || !!state.query.learnStatus || !!state.query.sort || !!state.query.keyword.trim()
    },
    coursesSubText(state): string {
      if (state.query.keyword.trim()) return `搜索结果 ${state.coursesTotal} 个`
      return `${state.coursesTotal} 个`
    },
    topicCards(state): TopicCard[] {
      return state.topicsExpanded ? state.topics : state.topics.slice(0, 2)
    }
  },
  actions: {
    async bootstrap(): Promise<void> {
      await Promise.all([this.loadCategories(), this.loadContinue(), this.loadTopics(), this.loadCourses()])
    },
    async loadCategories(): Promise<void> {
      this.categoriesStatus = this.categories.length ? this.categoriesStatus : 'loading'
      this.categoriesError = ''
      try {
        this.categories = await fetchCourseCategories()
        this.categoriesStatus = this.categories.length ? 'ready' : 'empty'
      } catch (error) {
        this.categoriesStatus = statusForError(error)
        this.categoriesError = errorTextOf(error, '分类加载失败')
      }
    },
    async loadContinue(): Promise<void> {
      this.continueStatus = 'loading'
      this.continueError = ''
      try {
        const data = await fetchContinueWatching()
        this.continueWatching = data
        this.continueStatus = data ? 'ready' : 'empty'
      } catch (error) {
        this.continueWatching = null
        this.continueStatus = statusForError(error)
        this.continueError = errorTextOf(error, '继续观看加载失败')
      }
    },
    async loadTopics(): Promise<void> {
      this.topicsStatus = this.topics.length ? this.topicsStatus : 'loading'
      this.topicsError = ''
      try {
        const data = await fetchTopics({ page: 1, size: 50 })
        this.topics = data.list || []
        this.topicsTotal = data.total || 0
        this.topicsStatus = this.topics.length ? 'ready' : 'empty'
      } catch (error) {
        this.topicsStatus = statusForError(error)
        this.topicsError = errorTextOf(error, '专题加载失败')
      }
    },
    async loadCourses(): Promise<void> {
      const requestId = ++this.requestId
      this.coursesStatus = this.courses.length ? this.coursesStatus : 'loading'
      this.coursesError = ''
      try {
        const keyword = this.query.keyword.trim()
        const data = await fetchCourses({
          page: this.query.page,
          size: this.query.size,
          category_id: this.query.categoryId || undefined,
          learn_status: this.query.learnStatus || undefined,
          sort: this.query.sort || undefined,
          keyword: keyword || undefined
        })
        if (requestId !== this.requestId) return
        this.courses = data.list || []
        this.coursesTotal = data.total || 0
        if (!this.courses.length) {
          this.coursesStatus = this.hasFilters ? 'filtered_empty' : 'empty'
        } else {
          this.coursesStatus = 'ready'
        }
      } catch (error) {
        if (requestId !== this.requestId) return
        this.coursesStatus = statusForError(error)
        this.coursesError = errorTextOf(error, '课程加载失败')
      }
    },
    setCategory(value: number): void {
      this.query.categoryId = Number(value || 0)
      this.query.page = 1
      void this.loadCourses()
    },
    setLearnStatus(value: string): void {
      this.query.learnStatus = value
      this.query.page = 1
      void this.loadCourses()
    },
    setSort(value: string): void {
      this.query.sort = value
      this.query.page = 1
      void this.loadCourses()
    },
    setKeyword(value: string): void {
      this.query.keyword = value
      if (this.keywordTimer) window.clearTimeout(this.keywordTimer)
      this.keywordTimer = window.setTimeout(() => {
        this.query.page = 1
        void this.loadCourses()
      }, 300)
    },
    setPage(page: number): void {
      this.query.page = Math.max(1, page)
      void this.loadCourses()
    },
    setSize(size: number): void {
      this.query.size = Math.max(1, size)
      this.query.page = 1
      void this.loadCourses()
    },
    resetFilters(): void {
      this.query.categoryId = 0
      this.query.learnStatus = ''
      this.query.sort = ''
      this.query.keyword = ''
      this.query.page = 1
      void this.loadCourses()
    },
    toggleTopics(): void {
      this.topicsExpanded = !this.topicsExpanded
    },
    async openTopic(topicId: number): Promise<void> {
      this.topicDrawer = true
      this.topicId = topicId
      this.topicStatus = 'loading'
      this.topicError = ''
      this.topicDetail = null
      try {
        this.topicDetail = await fetchTopic(topicId)
        this.topicStatus = 'ready'
      } catch (error) {
        this.topicStatus = isOffShelf(error) ? 'offshelf' : 'error'
        this.topicError = errorTextOf(error, '专题加载失败')
      }
    },
    closeTopic(): void {
      this.topicDrawer = false
      this.topicId = 0
      this.topicDetail = null
      this.topicStatus = 'idle'
    },
    async openHistory(): Promise<void> {
      this.historyDrawer = true
      if (!this.history.length) await this.loadHistory()
    },
    async loadHistory(): Promise<void> {
      this.historyStatus = 'loading'
      this.historyError = ''
      try {
        const data = await fetchWatchHistory({ page: 1, size: 20 })
        this.history = data.list || []
        this.historyTotal = data.total || 0
        this.historyPage = data.page || 1
        this.historyHasMore = !!data.has_more
        this.historyStatus = this.history.length ? 'ready' : 'empty'
      } catch (error) {
        this.historyStatus = statusForError(error)
        this.historyError = errorTextOf(error, '观看记录加载失败')
      }
    },
    async loadMoreHistory(): Promise<void> {
      if (this.historyLoadingMore || !this.historyHasMore) return
      this.historyLoadingMore = true
      try {
        const page = this.historyPage + 1
        const data = await fetchWatchHistory({ page, size: 20 })
        const existing = new Set(this.history.map((item) => item.id))
        this.history = [...this.history, ...(data.list || []).filter((item) => !existing.has(item.id))]
        this.historyPage = data.page || page
        this.historyHasMore = !!data.has_more
        this.historyTotal = data.total || this.historyTotal
      } catch (error) {
        this.historyError = errorTextOf(error, '加载更多观看记录失败')
      } finally {
        this.historyLoadingMore = false
      }
    },
    async reload(): Promise<void> {
      await Promise.all([this.loadContinue(), this.loadTopics(), this.loadCourses()])
    }
  }
})
