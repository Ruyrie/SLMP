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
