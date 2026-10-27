import type { CourseRow } from '@/api/content'

export function hasReadyCourseMedia(course: Pick<CourseRow, 'transcode_status' | 'p720' | 'p480'>): boolean {
  return course.transcode_status === 'SUCCESS'
    && course.p720?.status === 'SUCCESS' && course.p480?.status === 'SUCCESS'
}

export function canPublishCourse(course: CourseRow): boolean {
  return course.can_on_shelf === true && hasReadyCourseMedia(course)
}

export function courseTranscodeStatus(course: CourseRow): string {
  return course.transcode_status === 'SUCCESS' && !hasReadyCourseMedia(course)
    ? 'FAILED' : course.transcode_status || 'NONE'
}
