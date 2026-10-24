import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ZwAvatar from '../packages/shared/src/components/ZwAvatar.vue'
import { avatarInitial, resolveAvatarUrl } from '../packages/shared/src/utils/avatar'

describe('avatar presentation', () => {
  it('resolves relative media at the site root for either app and preserves signed URLs', () => {
    expect(resolveAvatarUrl('uploads/avatar.png', 'https://farm.example')).toBe('https://farm.example/uploads/avatar.png')
    expect(resolveAvatarUrl('/api/v1/files/7/download?token=a%2Bb', 'https://farm.example')).toBe('https://farm.example/api/v1/files/7/download?token=a%2Bb')
    const signed = 'https://media.example/avatar.png?X-Amz-Signature=123&X-Amz-Expires=600'
    expect(resolveAvatarUrl(signed, 'https://farm.example')).toBe(signed)
  })

  it('uses the actual name and a neutral fallback for an empty profile', () => {
    expect(avatarInitial(' 刘芳 ')).toBe('刘')
    expect(avatarInitial(null)).toBe('用')
    const avatar = mount(ZwAvatar, { props: { name: null } })
    expect(avatar.text()).toBe('用')
    expect(avatar.find('img').exists()).toBe(false)
  })

  it('falls back on a broken image and retries when a fresh avatar URL arrives', async () => {
    const avatar = mount(ZwAvatar, { props: { name: '李明', src: '/old.png' } })
    await avatar.get('img').trigger('error')
    expect(avatar.find('img').exists()).toBe(false)
    expect(avatar.text()).toBe('李')
    await avatar.setProps({ src: '/new.png' })
    expect(avatar.get('img').attributes('src')).toContain('/new.png')
    await avatar.setProps({ src: null })
    expect(avatar.find('img').exists()).toBe(false)
    expect(avatar.text()).toBe('李')
  })
})
