import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import NoticeContent from '../apps/user/src/views/today/components/NoticeContent.vue'
import MediaPreviewDialog from '../apps/user/src/views/inspect/components/MediaPreviewDialog.vue'
import { useMessagesStore } from '../apps/user/src/stores/today/messages'

const api = vi.hoisted(() => ({ fetchMessages: vi.fn(), fetchMessageDetail: vi.fn() }))
vi.mock('../apps/user/src/api/today', () => api)
vi.mock('../apps/user/src/stores/notice', () => ({ useNoticeStore: () => ({ load: vi.fn() }) }))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
})

describe('user platform notice', () => {
  it('renders formatted content and opens the administrator image in the media preview', async () => {
    const view = shallowMount(NoticeContent, {
      props: { content: '<p>请<strong>注意防寒</strong></p>', image: { file_id: 51, url: '/notice.jpg' } }
    })
    expect(view.find('strong').text()).toBe('注意防寒')
    expect(view.find('img[alt="通知配图"]').attributes('src')).toBe('/notice.jpg')
    await view.get('button[aria-label="查看通知配图"]').trigger('click')
    expect(view.findComponent(MediaPreviewDialog).props('modelValue')).toBe(true)
    expect(view.findComponent(MediaPreviewDialog).props('items')).toEqual([
      { url: '/notice.jpg', video: false, name: '通知配图' }
    ])
  })

  it('removes executable notice markup', () => {
    const view = shallowMount(NoticeContent, {
      props: { content: '<p onclick="alert(1)">安全正文</p><script>alert(2)</script><img src=x onerror="alert(3)">' }
    })
    expect(view.find('.notice-body').html()).toContain('安全正文')
    expect(view.find('.notice-body').html()).not.toMatch(/onclick|onerror|script|<img/)
  })

  it('keeps the image preview open when the same image receives a renewed signature', async () => {
    const view = shallowMount(NoticeContent, {
      props: { image: { file_id: 51, url: '/first-signature.jpg' } }
    })
    await view.get('button[aria-label="查看通知配图"]').trigger('click')
    await view.setProps({ image: { file_id: 51, url: '/renewed-signature.jpg' } })
    expect(view.findComponent(MediaPreviewDialog).props('modelValue')).toBe(true)
    expect(view.findComponent(MediaPreviewDialog).props('items')[0].url).toBe('/renewed-signature.jpg')
    await view.setProps({ image: null })
    expect(view.findComponent(MediaPreviewDialog).props('modelValue')).toBe(false)
  })

  it('requests a new signed image URL after an image loading failure', async () => {
    const view = shallowMount(NoticeContent, {
      props: { image: { file_id: 51, url: '/expired.jpg' } },
      global: { stubs: { 'zw-empty': { template: '<div><slot /></div>' }, 'v-btn': { template: '<button><slot /></button>' } } }
    })
    await view.get('img').trigger('error')
    await view.get('button').trigger('click')
    expect(view.emitted('refresh')).toHaveLength(1)
    await view.setProps({ image: { file_id: 51, url: '/renewed.jpg' } })
    expect(view.get('img').attributes('src')).toBe('/renewed.jpg')
  })

  it('refreshes the open notice after revocation and keeps pagination without clearing the screen', async () => {
    const store = useMessagesStore()
    const before = { message_id: 501, msg_type: 'NOTICE', read: true, revoked: false }
    store.list = [before]
    store.status = 'ready'
    store.selectedId = 501
    store.detailStatus = 'ready'
    store.detail = { ...before, payload: { content: '旧正文', image: { url: '/old.jpg' } } }
    store.query.page = 2
    api.fetchMessages.mockImplementation(({ page }: { page: number }) => Promise.resolve({
      list: [{ message_id: page === 1 ? 501 : 502, revoked: true }],
      total: 2, filtered_unread: 0, has_more: page === 1
    }))
    api.fetchMessageDetail.mockResolvedValue({ ...before, revoked: true, payload: { revoked: true } })

    const refresh = store.refreshVisible()
    expect(store.detail?.payload?.content).toBe('旧正文')
    expect(store.status).toBe('ready')
    await refresh
    expect(store.list.map((message) => message.message_id)).toEqual([501, 502])
    expect(store.detail?.revoked).toBe(true)
    expect(store.detail?.payload).not.toHaveProperty('image')
    expect(store.detail?.payload?.content).toBeUndefined()
    expect(store.query.page).toBe(2)
    expect(store.hasMore).toBe(false)
  })

  it('refetches a selected notice when the user opens the same message again', async () => {
    const store = useMessagesStore()
    store.selectedId = 501
    store.detailStatus = 'ready'
    store.detail = { message_id: 501, msg_type: 'NOTICE', revoked: false }
    api.fetchMessageDetail.mockResolvedValue({ message_id: 501, msg_type: 'NOTICE', revoked: true })
    await store.select(501)
    expect(api.fetchMessageDetail).toHaveBeenCalledWith(501)
    expect(store.detail?.revoked).toBe(true)
  })

  it('does not let an older same-message response overwrite a freshly revoked notice', async () => {
    const store = useMessagesStore()
    let finishOlder!: (value: unknown) => void
    api.fetchMessageDetail.mockReturnValueOnce(new Promise((resolve) => { finishOlder = resolve }))
      .mockResolvedValueOnce({ message_id: 501, msg_type: 'NOTICE', revoked: true })
    const older = store.select(501)
    await store.select(501)
    finishOlder({ message_id: 501, msg_type: 'NOTICE', revoked: false, payload: { content: '旧正文' } })
    await older
    expect(store.detail?.revoked).toBe(true)
    expect(store.detail?.payload?.content).toBeUndefined()
  })

  it('does not replace a newly selected message with an older request error', async () => {
    const store = useMessagesStore()
    let rejectOlder!: (reason: unknown) => void
    api.fetchMessageDetail.mockReturnValueOnce(new Promise((_, reject) => { rejectOlder = reject }))
      .mockResolvedValueOnce({ message_id: 502, msg_type: 'NOTICE', revoked: true })
    const older = store.select(501)
    await store.select(502)
    rejectOlder(new Error('旧请求超时'))
    await older
    expect(store.detail?.message_id).toBe(502)
    expect(store.detailStatus).toBe('ready')
    expect(store.detailError).toBe('')
  })
})
