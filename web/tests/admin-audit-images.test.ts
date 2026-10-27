import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import AuditImage from '../apps/admin/src/views/audit/components/AuditImage.vue'
import AuditImageViewer from '../apps/admin/src/views/audit/components/AuditImageViewer.vue'

const pages: VueWrapper[] = []
function picture(props: Record<string, unknown>) {
  const page = mount(AuditImage, { props })
  pages.push(page)
  return page
}
afterEach(() => pages.splice(0).forEach((page) => page.unmount()))

describe('audit image recovery', () => {
  it('falls back to the original if its thumbnail is unavailable', async () => {
    const page = picture({ url: '/original.png', thumbUrl: '/thumb.png' })
    expect(page.get('img').attributes('src')).toBe('/thumb.png')
    await page.get('img').trigger('error')
    expect(page.get('img').attributes('src')).toBe('/original.png')
  })

  it('shows an explicit failure and lets users retry without opening the viewer', async () => {
    let parentClicks = 0
    const page = picture({ url: '/original.png', thumbUrl: '/original.png', onClick: () => parentClicks++ })
    await page.get('img').trigger('error')
    expect(page.get('[role="status"]').text()).toContain('图片加载失败')
    expect(page.find('svg').exists()).toBe(false)
    await page.get('button').trigger('click')
    expect(parentClicks).toBe(0)
    expect(page.get('img').attributes('src')).toBe('/original.png')
  })

  it('clears an old failure when refreshed signed URLs arrive', async () => {
    const page = picture({ url: '/image.png?expired' })
    await page.get('img').trigger('error')
    await page.setProps({ url: '/image.png?fresh' })
    expect(page.get('img').attributes('src')).toBe('/image.png?fresh')
    expect(page.find('[role="status"]').exists()).toBe(false)
  })

  it('reports missing URLs without fabricating certificate evidence', () => {
    const page = picture({})
    expect(page.text()).toBe('未提供图片地址')
    expect(page.find('img').exists()).toBe(false)
    expect(page.find('button').exists()).toBe(false)
  })

  it('supports thumbnail-only items and prefers the full image for preview', () => {
    expect(picture({ thumbUrl: '/only-thumb.png', preferThumbnail: false }).get('img').attributes('src')).toBe('/only-thumb.png')
    expect(picture({ url: '/full.png', thumbUrl: '/thumb.png', preferThumbnail: false }).get('img').attributes('src')).toBe('/full.png')
  })
})

describe('audit image viewer', () => {
  it('previews, zooms, rotates and switches actual submitted images', async () => {
    const page = mount(AuditImageViewer, {
      props: { modelValue: true, items: [{ url: '/first.png' }, { url: '/second.png' }] },
      global: { stubs: { Teleport: true, ZwIcon: true } }
    })
    pages.push(page)
    expect(page.get('.av-figure img').attributes('src')).toBe('/first.png')
    await page.get('button[aria-label="放大"]').trigger('click')
    await page.get('button[aria-label="向右旋转"]').trigger('click')
    expect(page.get('.av-figure img').attributes('style')).toContain('scale(1.2) rotate(90deg)')
    await page.get('button[aria-label="下一张"]').trigger('click')
    expect(page.get('.av-figure img').attributes('src')).toBe('/second.png')
    expect(page.get('.av-figure img').attributes('style')).toContain('scale(1) rotate(0deg)')
  })
})
