import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { courseProgressPercent, progressPercent } from '../apps/user/src/views/ai/course'
import { CourseProgressReporter } from '../apps/user/src/views/ai/progress'
import CourseVideoPlayer from '../apps/user/src/views/ai/components/CourseVideoPlayer.vue'
import CoursePlayerView from '../apps/user/src/views/ai/CoursePlayerView.vue'
import { useAiCoursePlayerStore } from '../apps/user/src/stores/ai/coursePlayer'

const api = vi.hoisted(() => ({ reportCourseProgress: vi.fn(), newRequestKey: vi.fn(), fetchCoursePlay: vi.fn(), fetchTopic: vi.fn() }))
const navigation = vi.hoisted(() => ({ route: { params: { id: '6201' }, query: {} }, push: vi.fn() }))
vi.mock('../apps/user/src/api/ai', () => api)
vi.mock('hls.js', () => ({ default: class { static isSupported() { return false } } }))
vi.mock('vue-router', () => ({ useRoute: () => navigation.route, useRouter: () => ({ push: navigation.push }) }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
  navigation.route = reactive({ params: { id: '6201' }, query: {} })
  api.newRequestKey.mockReturnValue('test-play-token')
  api.reportCourseProgress.mockResolvedValue(undefined)
  api.fetchCoursePlay.mockImplementation((id) => Promise.resolve({ id, title: `课程${id}`, duration_sec: 310,
    sources: [], progress: { resume_position_sec: 0, position_sec: 0, completed: false } }))
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
  vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockReturnValue('probably')
})
afterEach(() => vi.restoreAllMocks())

const stubs = {
  'zw-icon': true, 'zw-thumb': true, 'zw-empty': true, 'zw-tag': true, CourseCover: true, 'v-progress-circular': true,
  'v-progress-linear': true, 'v-skeleton-loader': true,
  'v-btn': { template: '<button><slot /></button>' },
  'zw-shell': { template: '<div><slot /></div>' },
  'zw-card': { template: '<div><slot name="extra" /><slot /></div>' }
}

async function player(duration: number, resumePosition = 0) {
  const view = mount(CourseVideoPlayer, { props: {
    durationHint: 310, resumePosition, sources: [{ quality: 'P720', hls_url: '/video.m3u8' }]
  }, global: { stubs } })
  await flushPromises()
  const video = view.get('video').element as HTMLVideoElement
  Object.defineProperty(video, 'duration', { configurable: true, value: duration })
  await view.get('video').trigger('loadedmetadata')
  return { view, video }
}

describe('course progress and actual media duration', () => {
  it('uses the displayed resume position instead of a polluted maximum progress ratio', () => {
    expect(courseProgressPercent({ position_sec: 1, duration_sec: 634, progress_ratio: 1, learn_status: 'LEARNING' }))
      .toBeCloseTo(100 / 634)
    expect(courseProgressPercent({ position_sec: 1, duration_sec: 634, progress_ratio: 1, learn_status: 'COMPLETED' })).toBe(100)
    expect(progressPercent(0.5)).toBe(50)
    expect(progressPercent(Number.NaN)).toBe(0)
  })

  it('uses actual 22-second metadata and reports 22, not a 310-second duration hint, on END', async () => {
    const { view, video } = await player(22)
    expect(view.get('.k04-time').text()).toBe('0:00 / 0:22')
    expect(view.get('input[aria-label="播放进度"]').attributes('max')).toBe('22')
    video.currentTime = 22
    await view.get('video').trigger('ended')
    expect(view.emitted('ended')?.[0]).toEqual([{ position: 22, duration: 22 }])
    expect(view.emitted('tick')?.at(-1)?.[0]).toMatchObject({ position: 22, duration: 22 })
    view.unmount()
  })

  it('restarts an out-of-range legacy resume instead of falsely reporting that old position', async () => {
    const { view, video } = await player(22, 84)
    expect(video.currentTime).toBe(0)
    expect(view.emitted('ready')?.[0]).toEqual([{ duration: 22, resume: 0, quality: 'P720' }])
    view.unmount()
  })

  it('preserves an in-range resume and uses whole elapsed seconds for duration labels', async () => {
    const { view, video } = await player(634.584, 84)
    expect(video.currentTime).toBe(84)
    expect(view.get('.k04-time').text()).toBe('1:24 / 10:34')
    expect(view.emitted('ready')?.[0]).toEqual([{ duration: 634.584, resume: 84, quality: 'P720' }])
    view.unmount()
  })

  it('does not begin progress reporting until valid video metadata is available', async () => {
    const { view } = await player(Number.NaN, 84)
    expect(view.emitted('ready')).toBeUndefined()
    view.unmount()
  })

  it('never advances END position to a nominal duration and floors fractional positions', () => {
    const reporter = new CourseProgressReporter()
    reporter.begin(6201, 'COURSE', 'P720', 0, 310)
    reporter.ended(22.9, 310)
    expect(api.reportCourseProgress.mock.calls.at(-1)).toEqual([6201, expect.objectContaining({ event: 'END', position_sec: 22 })])
  })

  it('rejects a late progress response from the previous course', async () => {
    let oldResult!: (value: unknown) => void
    api.reportCourseProgress.mockImplementationOnce(() => new Promise((resolve) => { oldResult = resolve }))
    const view = mount(CoursePlayerView, { global: { stubs: { ...stubs,
      CourseVideoPlayer: { name: 'CourseVideoPlayer', template: '<div />', methods: {
        getPosition: () => 0, getDuration: () => 22, getQuality: () => 'P720'
      } }
    } } })
    await flushPromises()
    view.getComponent({ name: 'CourseVideoPlayer' }).vm.$emit('ready', { duration: 22, resume: 0, quality: 'P720' })
    navigation.route.params.id = '6202'
    await flushPromises()
    oldResult({ position_sec: 310, progress_ratio: 1, completed: true, just_completed: true, learn_status: 'COMPLETED' })
    await flushPromises()
    const store = useAiCoursePlayerStore()
    expect(store.course?.id).toBe(6202)
    expect(store.course?.progress?.completed).toBe(false)
    expect(store.course?.progress?.position_sec).toBe(0)
    view.unmount()
  })
})
