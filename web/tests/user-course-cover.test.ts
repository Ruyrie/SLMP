import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CourseCover from '../apps/user/src/views/ai/components/CourseCover.vue'

describe('published course covers', () => {
  const fallback = { props: ['kind'], template: '<span data-fallback="true">{{kind}}</span>' }
  const cover = (props: Record<string, unknown> = {}) => mount(CourseCover, {
    props, global: { stubs: { ZwThumb: fallback } }
  })

  it('displays the uploaded image from the course API', () => {
    const wrapper = cover({ src: 'http://127.0.0.1:9000/course-cover.png', alt: '本地联动课程', kind: 'feed' })
    expect(wrapper.get('img').attributes('src')).toBe('http://127.0.0.1:9000/course-cover.png')
    expect(wrapper.get('img').attributes('alt')).toBe('本地联动课程')
    expect(wrapper.find('[data-fallback]').exists()).toBe(false)
  })

  it('keeps the illustrated fallback when a course has no cover', () => {
    const wrapper = cover({ kind: 'feed' })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[data-fallback]').text()).toBe('feed')
  })

  it('does not request the reserved demo CDN URLs', () => {
    const wrapper = cover({ src: 'https://cdn.zhimutong.example.com/course/6101.jpg', kind: 'feed' })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('[data-fallback]').exists()).toBe(true)
  })

  it('falls back on an image error and retries when a refreshed URL arrives', async () => {
    const wrapper = cover({ src: 'http://127.0.0.1:9000/old-signature.png', kind: 'feed' })
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('[data-fallback]').exists()).toBe(true)
    await wrapper.setProps({ src: 'http://127.0.0.1:9000/new-signature.png' })
    expect(wrapper.get('img').attributes('src')).toContain('new-signature.png')
    expect(wrapper.find('[data-fallback]').exists()).toBe(false)
  })
})
