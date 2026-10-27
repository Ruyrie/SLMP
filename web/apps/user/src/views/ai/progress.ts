/** 课程播放进度上报：每 10 秒 TICK，暂停 / 拖动 / 结束 / 离开页面即时上报（keepalive）。 */
import { dayjs, TZ } from '@zmt/shared'
import {
  newRequestKey,
  reportCourseProgress,
  type CourseProgressPayload,
  type EntryType,
  type ProgressResult,
  type VideoQuality
} from '@/api/ai'

export interface ProgressTick {
  position: number
  duration: number
  playing: boolean
  quality: VideoQuality
}

export interface ProgressReporterOptions {
  onResult?: (result: ProgressResult) => void
  onError?: (error: unknown) => void
}

export class CourseProgressReporter {
  private courseId = 0
  private token = ''
  private entryType: EntryType = 'COURSE'
  private quality: VideoQuality = 'P720'
  private active = false
  private lastTickAt = 0
  private lastReportAt = 0
  private lastPosition = 0
  private watchedSec = 0

  constructor(private readonly options: ProgressReporterOptions = {}) {}

  begin(courseId: number, entryType: EntryType, quality: VideoQuality, position: number, duration: number): void {
    if (!courseId) return
    this.courseId = courseId
    this.entryType = entryType
    this.quality = quality
    this.token = newRequestKey()
    this.active = true
    this.lastTickAt = Date.now()
    this.lastReportAt = Date.now()
    this.lastPosition = Math.max(0, position)
    this.watchedSec = 0
    this.send('START', this.lastPosition, 0)
    if (duration > 0) this.lastPosition = position
  }

  tick(payload: ProgressTick): void {
    if (!this.active || !this.courseId) return
    if (payload.quality !== this.quality) this.quality = payload.quality
    const now = Date.now()
    if (payload.playing) {
      const delta = Math.min(5, Math.max(0, (now - this.lastTickAt) / 1000))
      this.watchedSec = Math.min(60, this.watchedSec + delta)
    }
    this.lastTickAt = now
    this.lastPosition = Math.max(0, payload.position)
    if (now - this.lastReportAt >= 10000) {
      this.lastReportAt = now
      this.send('TICK', this.lastPosition, this.takeWatched())
    }
  }

  pause(position: number, quality: VideoQuality): void {
    if (!this.active) return
    this.quality = quality
    this.lastPosition = Math.max(0, position)
    this.send('PAUSE', this.lastPosition, this.takeWatched())
  }

  seek(position: number, quality: VideoQuality): void {
    if (!this.active) return
    this.quality = quality
    this.lastPosition = Math.max(0, position)
    this.send('SEEK', this.lastPosition, this.takeWatched())
  }

  ended(position: number, duration: number): void {
    if (!this.active) return
    this.lastPosition = Math.max(0, position)
    if (duration > 0) this.lastPosition = Math.min(this.lastPosition, duration)
    this.send('END', this.lastPosition, this.takeWatched())
    this.active = false
  }

  switchCourse(position: number): void {
    if (!this.active) return
    this.lastPosition = Math.max(0, position)
    this.send('SWITCH', this.lastPosition, this.takeWatched())
    this.active = false
  }

  leave(position?: number): void {
    if (!this.active) return
    if (typeof position === 'number') this.lastPosition = Math.max(0, position)
    this.send('LEAVE', this.lastPosition, this.takeWatched())
    this.active = false
  }

  engagement(kind: 'ask_ai' | 'knowledge', position: number, quality: VideoQuality): void {
    if (!this.active) return
    this.quality = quality
    this.lastPosition = Math.max(0, position)
    const payload = this.payload('TICK', this.lastPosition, 0)
    if (kind === 'ask_ai') payload.ask_ai_clicked = true
    else payload.knowledge_clicked = true
    this.dispatch(payload)
  }

  setQuality(quality: VideoQuality): void {
    this.quality = quality
  }

  private takeWatched(): number {
    const value = Math.min(60, Math.max(0, Math.round(this.watchedSec)))
    this.watchedSec = 0
    return value
  }

  private payload(event: CourseProgressPayload['event'], position: number, watched: number): CourseProgressPayload {
    return {
      play_token: this.token,
      event,
      position_sec: Math.max(0, Math.floor(position)),
      watched_sec: watched,
      quality: this.quality,
      entry_type: this.entryType,
      client_time: dayjs().tz(TZ).format('YYYY-MM-DD HH:mm:ss')
    }
  }

  private send(event: CourseProgressPayload['event'], position: number, watched: number): void {
    if (!this.courseId || !this.token) return
    this.dispatch(this.payload(event, position, watched))
  }

  private dispatch(payload: CourseProgressPayload): void {
    void reportCourseProgress(this.courseId, payload)
      .then((result) => {
        if (result && this.options.onResult) this.options.onResult(result)
      })
      .catch((error: unknown) => {
        if (this.options.onError) this.options.onError(error)
      })
  }
}
