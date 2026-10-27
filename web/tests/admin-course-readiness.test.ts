import { describe, expect, it } from 'vitest'
import { canPublishCourse, courseTranscodeStatus } from '../apps/admin/src/utils/courseReadiness'
import type { CourseRow } from '../apps/admin/src/api/content'

const ready = (): CourseRow => ({ id: 6302, title: '历史课程', status: 'OFF_SHELF', status_text: '已下架',
  transcode_status: 'SUCCESS', can_on_shelf: true, p720: { status: 'SUCCESS' }, p480: { status: 'SUCCESS' } })

describe('course publishing readiness', () => {
  it('permits a historical course without a bound file when the server confirms both playable qualities', () => {
    expect(canPublishCourse(ready())).toBe(true)
    expect(courseTranscodeStatus(ready())).toBe('SUCCESS')
  })
  it.each(['p720', 'p480'] as const)('does not treat a stale SUCCESS flag as %s being ready', (quality) => {
    const course = ready(); delete course[quality]
    expect(canPublishCourse(course)).toBe(false)
    expect(courseTranscodeStatus(course)).toBe('FAILED')
  })
  it('respects server metadata restrictions despite ready video', () => {
    expect(canPublishCourse({ ...ready(), can_on_shelf: false })).toBe(false)
  })
  it('blocks a processing replacement even if old slots show success', () => {
    expect(canPublishCourse({ ...ready(), transcode_status: 'PROCESSING' })).toBe(false)
  })
})
