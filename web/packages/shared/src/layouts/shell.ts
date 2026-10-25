import { reactive } from 'vue'

export interface FarmBrief {
  farm_id: number
  name: string
  role: string
  role_text: string
  status: string
  status_text: string
  species_text?: string
  main_category_name?: string
  current_stock?: number
  count_unit_text?: string
  is_current?: boolean
  can_switch?: boolean
}

export type ShellSide = 'user' | 'admin'

interface ShellState {
  side: ShellSide
  role: 'owner' | 'manager' | 'staff'
  userName: string
  avatarUrl: string
  phone: string
  userId: number
  farmId: number
  farmName: string
  farmRoleLabel: string
  farms: FarmBrief[]
  allFarms: boolean
  unread: number
  perms: string[]
  badges: Record<string, number>
  collapsed: boolean
  adminRoleName: string
}

const COLLAPSED_KEY = 'zmt.sider.collapsed'

export const shellState = reactive<ShellState>({
  side: 'user',
  role: 'owner',
  userName: '',
  avatarUrl: '',
  phone: '',
  userId: 0,
  farmId: 0,
  farmName: '',
  farmRoleLabel: '',
  farms: [],
  allFarms: false,
  unread: 0,
  perms: [],
  badges: {},
  collapsed: typeof localStorage !== 'undefined' && localStorage.getItem(COLLAPSED_KEY) === '1',
  adminRoleName: ''
})

export interface ShellActions {
  switchFarm: (farmId: number) => void | Promise<void>
  toggleAllFarms: (value: boolean) => void | Promise<void>
  logout: () => void | Promise<void>
  searchBatch: (keyword: string) => void | Promise<void>
  openAccount: () => void | Promise<void>
  openMessages: () => void | Promise<void>
}

export const shellActions: ShellActions = {
  switchFarm: () => undefined,
  toggleAllFarms: () => undefined,
  logout: () => undefined,
  searchBatch: () => undefined,
  openAccount: () => undefined,
  openMessages: () => undefined
}

export function setShellActions(actions: Partial<ShellActions>): void {
  Object.assign(shellActions, actions)
}

export function toggleSider(): void {
  shellState.collapsed = !shellState.collapsed
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(COLLAPSED_KEY, shellState.collapsed ? '1' : '0')
  }
}

/**
 * 侧边菜单只保存路由名（页面编号），像养殖场信息 /farms/:farmId 这种
 * 带路径参数的页面需要把当前养殖场补进去，否则 router.push 会抛
 * Missing required param "farmId"，点击菜单没有任何反应。
 */
export function menuRouteParams(path: string | undefined | null, farmId: number): Record<string, string> {
  const params: Record<string, string> = {}
  if (!path || !(farmId > 0)) return params
  for (const match of path.matchAll(/:([A-Za-z0-9_]+)/g)) {
    const key = match[1]
    if (key === 'farmId') params[key] = String(farmId)
  }
  return params
}

/** 路由是否还有没补上的必填参数（用于决定菜单点击后是否回退到养殖场列表）。 */
export function missingRouteParams(path: string | undefined | null, params: Record<string, string>): string[] {
  if (!path) return []
  const missing: string[] = []
  for (const match of path.matchAll(/:([A-Za-z0-9_]+)/g)) {
    if (!params[match[1]]) missing.push(match[1])
  }
  return missing
}
