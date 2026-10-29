import type { RouteRecordRaw } from 'vue-router'

export interface PageMeta {
  page: string
  title: string
  crumbs?: string[]
  roles?: Array<'owner' | 'manager' | 'staff'>
  /** 按路由参数 farmId 指向的养殖场判断角色（养殖场信息、成员管理） */
  farmRoles?: Array<'owner' | 'manager' | 'staff'>
  allFarms?: boolean
  public?: boolean
  layout?: 'shell' | 'blank'
  needsFarm?: boolean
  perm?: string
  [key: string]: unknown
}

export function pageRoute(
  id: string,
  path: string,
  title: string,
  component: RouteRecordRaw['component'],
  meta: Partial<PageMeta> = {}
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
      needsFarm: true,
      ...meta
    }
  } as RouteRecordRaw
}

export const placeholder = () => import('@/views/_Placeholder.vue')
