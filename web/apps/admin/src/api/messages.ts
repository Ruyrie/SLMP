import type { PageVO } from '@zmt/shared'
import { http } from './client'

export interface AdminMessage {
  message_id: number
  msg_type: string
  msg_type_text: string
  level: string
  title: string
  summary?: string
  route_page?: string | null
  route_params?: Record<string, unknown> | null
  read: boolean
  created_at: string
}

export type MessageFilter = 'ALL' | 'UNREAD'

export function getAdminMessages(read: MessageFilter, page: number, size: number) {
  return http().get<PageVO<AdminMessage>>('/messages', { read, page, size })
}

export function readAdminMessages(messageId?: number) {
  return http().post<{ updated_count: number }>('/messages/read',
    messageId === undefined ? { all: true } : { message_ids: [messageId] })
}
