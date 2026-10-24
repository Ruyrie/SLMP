import type { RouteRecordRaw } from 'vue-router'

export interface AdminPageMeta {
  page: string
  title: string
  crumbs?: string[]
  perm?: string
  public?: boolean
  layout?: 'shell' | 'blank'
  [key: string]: unknown
}

export function adminRoute(
  id: string,
  path: string,
  title: string,
  component: RouteRecordRaw['component'],
  meta: Partial<AdminPageMeta> = {}
): RouteRecordRaw {
  return {
    path,
    name: id,
    component,
    meta: {
      page: id,
      title,
      crumbs: meta.crumbs || [title],
      layout: 'shell',
      ...meta
    }
  } as RouteRecordRaw
}

export const placeholder = () => import('@/views/_Placeholder.vue')
