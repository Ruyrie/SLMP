/** 侧边菜单与顶栏快捷登记（与原型 ZW.menus 一致；key 即路由名 = 页面编号）。 */
export interface MenuItem {
  key: string
  label: string
  icon?: string
  badge?: number
  badgeKey?: string
  match?: string[]
  perm?: string
}

export interface MenuGroup {
  group: string
  icon?: string
  children: MenuItem[]
  perm?: string
}

export interface QuickItem {
  key: string
  label: string
  icon: string
  action?: string
}

export type MenuNode = MenuItem | MenuGroup

export function isGroup(node: MenuNode): node is MenuGroup {
  return 'group' in node
}

const work: QuickItem[] = [
  { key: 'WU-I01', label: '巡检', icon: 'fact_check', action: 'create' },
  { key: 'WU-G01', label: '投喂', icon: 'rice_bowl', action: 'create' },
  { key: 'WU-G03', label: '称重', icon: 'scale', action: 'create' },
  { key: 'WU-D01', label: '免疫接种', icon: 'vaccines', action: 'create' },
  { key: 'WU-D02', label: '用药', icon: 'medication', action: 'create' },
  { key: 'WU-D03', label: '消毒', icon: 'sanitizer', action: 'create' },
  { key: 'WU-L05', label: '死淘转栏', icon: 'swap_horiz', action: 'create' }
]

const farmer: QuickItem[] = [
  { key: 'WU-B02', label: '产仔', icon: 'pets', action: 'create' },
  { key: 'WU-E03', label: '出栏单', icon: 'local_shipping', action: 'create' },
  { key: 'WU-E02', label: '记一笔', icon: 'payments', action: 'create' },
  { key: 'WU-I03', label: '异常上报', icon: 'report', action: 'create' }
]

export function quickItems(role: string): { work: QuickItem[]; farmer: QuickItem[] } {
  return { work, farmer: role === 'staff' ? [] : farmer }
}

export function userMenus(role: 'owner' | 'manager' | 'staff'): MenuNode[] {
  const staff = role === 'staff'
  const head: MenuNode[] = [
    { key: staff ? 'WU-T02' : 'WU-T01', label: '工作台', icon: 'space_dashboard' },
    { key: 'WU-T03', label: '养殖日历', icon: 'calendar_month' },
    { key: 'WU-T04', label: '消息中心', icon: 'notifications', badgeKey: 'unread' }
  ]
  const production: MenuGroup = staff
    ? {
        group: '我的栏舍',
        icon: 'fence',
        children: [
          { key: 'WU-L03', label: '负责栏舍与批次', match: ['WU-L03', 'WU-L04', 'WU-L05'] },
          { key: 'WU-B02', label: '待产与待出雏' },
          { key: 'WU-G01', label: '饲喂任务' },
          { key: 'WU-G03', label: '称重登记' }
        ]
      }
    : {
        group: '生产管理',
        icon: 'fence',
        children: [
          { key: 'WU-L01', label: '存栏总览' },
          { key: 'WU-L02', label: '栏舍管理' },
          { key: 'WU-L03', label: '批次档案', match: ['WU-L03', 'WU-L04', 'WU-L05'] },
          { key: 'WU-B01', label: '繁育管理', match: ['WU-B01', 'WU-B02', 'WU-B03'] },
          { key: 'WU-G01', label: '饲喂管理', match: ['WU-G01', 'WU-G02'] },
          { key: 'WU-G03', label: '称重与生长' }
        ]
      }
  const health: MenuGroup = {
    group: '防疫健康',
    icon: 'health_and_safety',
    children: [
      { key: 'WU-I01', label: staff ? '日常巡检' : '日常巡检', match: ['WU-I01', 'WU-I02'] },
      { key: 'WU-I03', label: staff ? '异常上报' : '异常与诊断', match: ['WU-I03', 'WU-I04'] },
      { key: 'WU-D01', label: staff ? '免疫接种' : '免疫计划' },
      { key: 'WU-D02', label: '用药与休药' },
      { key: 'WU-D03', label: staff ? '消毒任务' : '消毒管理' }
    ]
  }
  const study: MenuGroup = {
    group: '学习与助手',
    icon: 'auto_awesome',
    children: [
      { key: 'WU-K01', label: 'AI 养殖助手', match: ['WU-K01', 'WU-K02'] },
      { key: 'WU-K03', label: '养殖课堂', match: ['WU-K03', 'WU-K04'] }
    ]
  }
  const settings: MenuGroup = staff
    ? {
        group: '设置',
        icon: 'settings',
        children: [
          { key: 'WU-F01', label: '我的养殖场' },
          { key: 'WU-A04', label: '个人中心' }
        ]
      }
    : {
        group: '设置',
        icon: 'settings',
        children: [
          { key: 'WU-F03', label: '养殖场信息', match: ['WU-F03', 'WU-F02'] },
          { key: 'WU-F04', label: '成员管理' },
          { key: 'WU-F01', label: '我的养殖场' },
          { key: 'WU-U01', label: '我的修改申请' },
          { key: 'WU-A04', label: '个人中心' }
        ]
      }
  if (staff) return [...head, production, health, study, settings]
  return [
    ...head,
    production,
    health,
    {
      group: '物资库存',
      icon: 'inventory_2',
      children: [
        { key: 'WU-M01', label: '饲料库存' },
        { key: 'WU-M02', label: '兽药与疫苗', match: ['WU-M02', 'WU-M03'] }
      ]
    },
    {
      group: '经营管理',
      icon: 'payments',
      children: [
        { key: 'WU-E01', label: '经营概览' },
        { key: 'WU-E02', label: '收支记账' },
        { key: 'WU-E03', label: '出栏单', match: ['WU-E03', 'WU-E04'] },
        { key: 'WU-E05', label: '批次核算' },
        { key: 'WU-E06', label: '市场行情' },
        { key: 'WU-E07', label: '经营报表' }
      ]
    },
    study,
    settings
  ]
}

export const adminMenus: MenuNode[] = [
  { key: 'WA-S02', label: '平台概览', icon: 'dashboard', perm: 'WA-S02:VIEW' },
  {
    group: '审核中心',
    icon: 'fact_check',
    children: [
      { key: 'WA-R01', label: '养殖场审核', perm: 'WA-R01:VIEW', badgeKey: 'farmAudit' },
      { key: 'WA-R02', label: '资料变更审核', perm: 'WA-R02:VIEW', badgeKey: 'farmChange' },
      { key: 'WA-R03', label: '修改申请审核', perm: 'WA-R03:VIEW', badgeKey: 'changeRequest' },
      { key: 'WA-R04', label: '账号找回', perm: 'WA-R04:VIEW', badgeKey: 'recovery' }
    ]
  },
  {
    group: '内容运营',
    icon: 'campaign',
    children: [
      { key: 'WA-C01', label: '通知发布', perm: 'WA-C01:VIEW' },
      { key: 'WA-C02', label: '市场行情发布', perm: 'WA-C02:VIEW' },
      { key: 'WA-C03', label: '养殖课堂管理', perm: 'WA-C03:VIEW' },
      { key: 'WA-C04', label: '知识库管理', perm: 'WA-C04:VIEW' },
      { key: 'WA-C05', label: 'AI 问答与更正', perm: 'WA-C05:VIEW' }
    ]
  },
  {
    group: '基础数据',
    icon: 'database',
    children: [
      { key: 'WA-D01', label: '品类品种与标准', perm: 'WA-D01:VIEW' },
      { key: 'WA-D02', label: '免疫程序模板', perm: 'WA-D02:VIEW' },
      { key: 'WA-D03', label: '兽药疫苗目录', perm: 'WA-D03:VIEW' },
      { key: 'WA-D04', label: '巡检项目模板', perm: 'WA-D04:VIEW' },
      { key: 'WA-D05', label: '其他字典', perm: 'WA-D05:VIEW' }
    ]
  },
  {
    group: '用户与系统',
    icon: 'manage_accounts',
    children: [
      { key: 'WA-S03', label: '用户账号', perm: 'WA-S03:VIEW' },
      { key: 'WA-S04', label: '养殖场管理', perm: 'WA-S04:VIEW' },
      { key: 'WA-S05', label: '管理员与权限', perm: 'WA-S05:VIEW' },
      { key: 'WA-S06', label: '操作日志', perm: 'WA-S06:VIEW' }
    ]
  }
]

export function filterMenus(nodes: MenuNode[], perms: string[]): MenuNode[] {
  const allowed = (perm?: string) => !perm || perms.includes(perm) || perms.includes('*')
  const out: MenuNode[] = []
  for (const node of nodes) {
    if (isGroup(node)) {
      const children = node.children.filter((item) => allowed(item.perm))
      if (children.length) out.push({ ...node, children })
    } else if (allowed(node.perm)) {
      out.push(node)
    }
  }
  return out
}
