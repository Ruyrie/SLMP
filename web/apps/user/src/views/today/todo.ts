/**
 * 模块03 页面共用的显示规则、图标、状态标签与去向映射。
 * 文案和状态词以服务端 VO 为准，这里只做展示层归一化。
 */
import { useRouter } from 'vue-router'
import { ApiError, dayjs, today } from '@zmt/shared'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import type {
  LinkVO,
  MessageBriefVO,
  MessageDetailVO,
  TodoItemVO
} from '@/api/today'

export type TagType = 'neutral' | 'primary' | 'success' | 'warning' | 'error' | 'ai' | 'farm' | 'outline' | 'solid'

export interface TagInfo {
  text: string
  type: TagType
  icon?: string
}

export interface NavTarget {
  name: string
  params?: Record<string, string>
  query?: Record<string, string>
}

const TODO_ICONS: Record<string, string> = {
  FEEDING: 'rice_bowl',
  INSPECTION: 'fact_check',
  IMMUNIZATION: 'vaccines',
  DISINFECTION: 'sanitizer',
  WEIGHING: 'scale',
  FARROWING: 'pregnant_woman',
  HATCHING: 'egg',
  FEED_LOW_STOCK: 'inventory_2',
  DRUG_LOW_STOCK: 'inventory_2',
  DRUG_NEAR_EXPIRY: 'inventory_2',
  DRUG_EXPIRED: 'inventory_2',
  WITHDRAWAL_END: 'medication',
  RECEIVABLE: 'payments',
  ABNORMAL_REPORT: 'report'
}

export function todoIcon(todo: Pick<TodoItemVO, 'todo_type'> | null | undefined): string {
  return TODO_ICONS[todo?.todo_type || ''] || 'task_alt'
}

export function todoTone(todo: Pick<TodoItemVO, 'display_status' | 'category'>): string {
  if (todo.display_status === 'OVERDUE') return 'error'
  if (todo.display_status === 'DONE' || todo.display_status === 'IGNORED') return 'neutral'
  if (todo.category === 'WARNING') return 'warning'
  if (todo.category === 'BUSINESS') return 'neutral'
  return 'primary'
}

export function timeLabelText(todo: Pick<TodoItemVO, 'time_type' | 'due_time' | 'time_label'>): string {
  if (todo.time_type === 'ALL_DAY' || !todo.due_time) return '全天'
  if (todo.time_type === 'BEFORE_TIME') return `${todo.due_time} 前`
  return todo.due_time
}

/** 表格/列表右侧的状态标签：状态词只有待处理、已完成、已逾期、已忽略，今天的作业用时间代替待处理。 */
export function statusTag(todo: TodoItemVO, date = today()): TagInfo {
  if (todo.display_status === 'DONE') return { text: '已完成', type: 'neutral', icon: 'check' }
  if (todo.display_status === 'IGNORED') return { text: '已忽略', type: 'neutral' }
  if (todo.display_status === 'OVERDUE') return { text: '已逾期', type: 'error', icon: 'schedule' }
  if (todo.category === 'WARNING') return { text: todo.tail_text || '预警', type: 'warning' }
  if (todo.category === 'BUSINESS') return { text: todo.tail_text || '待处理', type: 'neutral' }
  if (todo.due_date === date) {
    return { text: todo.time_type === 'ALL_DAY' ? '今天' : timeLabelText(todo), type: 'primary' }
  }
  return { text: '待处理', type: 'primary' }
}

const KEEP_TAILS = new Set(['全天', '待处理', '已完成', '已忽略', '预警', '今天'])

/** 去掉副文字末尾重复的负责人：服务端的 sub_text 里带了“ · 张伟 / · 无负责员工”。 */
export function subText(todo: TodoItemVO): string {
  let text = todo.sub_text || ''
  if (!text) return ''
  const assignee = todo.assignee_text
  if (assignee && text.endsWith(` · ${assignee}`)) {
    text = text.slice(0, text.length - assignee.length - 3)
  }
  const index = text.lastIndexOf(' · ')
  if (index > 0) {
    const tail = text.slice(index + 3)
    if (/^[\u4e00-\u9fff]{2,4}$/.test(tail) && !KEEP_TAILS.has(tail)) {
      text = text.slice(0, index)
    }
  }
  return text
}

/** 日历当日列表的副文字：时间 · 负责人 / 预警 · 全天 / 已完成登记信息。 */
export function calendarSubText(todo: TodoItemVO): string {
  if (todo.display_status === 'DONE') return todo.completed_text || subText(todo)
  if (todo.display_status === 'IGNORED') return todo.ignore_reason || todo.ignore_text || '已忽略'
  if (todo.category === 'WARNING' || todo.category === 'BUSINESS') {
    const category = (todo.category_text || '').replace('类', '')
    return `${category || '待办'} · ${todo.time_type === 'ALL_DAY' ? '全天' : timeLabelText(todo)}`
  }
  return `${timeLabelText(todo)} · ${todo.assignee_text || '无负责员工'}`
}

/** 操作文字：预警类去入库、经营类登记收款/去查看，作业类去登记。 */
export function actionLabel(todo: TodoItemVO): string {
  if (todo.display_status === 'DONE' || todo.display_status === 'IGNORED') return '详情'
  if (todo.todo_type === 'DRUG_NEAR_EXPIRY' || todo.todo_type === 'DRUG_EXPIRED' || todo.todo_type === 'ABNORMAL_REPORT') return '去处理'
  if (todo.category === 'WARNING') return '去入库'
  if (todo.todo_type === 'RECEIVABLE') return '登记收款'
  if (todo.todo_type === 'WITHDRAWAL_END') return '去查看'
  return todo.action_text || '去登记'
}

/** 列表操作对应的去向：休药期到期先看用药与休药，其余去登记。 */
export function todoActionLink(todo: TodoItemVO): LinkVO | undefined {
  if (todo.todo_type === 'WITHDRAWAL_END') return todo.list_link || todo.register_link
  return todo.register_link || todo.list_link
}

/* ---------------- 消息展示 ---------------- */

const MESSAGE_ICONS: Record<string, string> = {
  AI_DIAGNOSIS: 'auto_awesome',
  FARM_INVITE: 'group_add',
  INVITE_ACCEPTED: 'group_add',
  OWNER_TRANSFER: 'swap_horiz',
  TRANSFER_RESULT: 'swap_horiz',
  MEMBER_REMOVED: 'person_remove',
  MEMBER_ROLE_CHANGED: 'manage_accounts',
  FARM_DISABLED: 'domain_disabled',
  FARM_DELETED: 'domain_disabled',
  FARM_FROZEN: 'domain_disabled',
  FARM_ENABLED: 'domain',
  FARM_UNFROZEN: 'domain',
  LOT_RECALLED: 'block',
  NOTICE: 'campaign',
  REPORT_READY: 'bar_chart',
  STAFF_NOTIFY: 'notifications',
  CHANGE_REQ_RESULT: 'edit_note',
  FARM_AUDIT_RESULT: 'fact_check',
  FARM_CHANGE_RESULT: 'fact_check',
  RECOVERY_RESULT: 'manage_accounts',
  FEED_OVERDUE: 'rice_bowl',
  IMMUNE_MISSED: 'vaccines',
  DISINFECT_OVERDUE: 'sanitizer',
  WITHDRAWAL_DELAYED: 'medication',
  TODO_REMIND: 'task_alt'
}

const BIZ_ICONS: Record<string, string> = {
  FEED_TASK: 'rice_bowl',
  IMMUNE_PLAN: 'vaccines',
  DISINFECT_TASK: 'sanitizer',
  INSPECTION: 'fact_check',
  WEIGH_CYCLE: 'scale',
  FARROWING: 'pregnant_woman',
  BATCH: 'medication',
  SALE_ORDER: 'payments',
  DRUG_LOT: 'inventory_2'
}

export function messageIcon(message: MessageBriefVO): string {
  if (message.icon_state === 'DONE') return 'check_circle'
  if (message.icon_state === 'IGNORED') return 'do_not_disturb_on'
  const byType = MESSAGE_ICONS[message.msg_type || '']
  if (byType && byType !== 'task_alt') return byType
  if (byType === 'task_alt' && message.biz_type) return BIZ_ICONS[message.biz_type] || 'task_alt'
  if (message.biz_type && BIZ_ICONS[message.biz_type]) return BIZ_ICONS[message.biz_type]
  return byType || 'notifications'
}

export function messageTone(message: MessageBriefVO): string {
  if (message.icon_state === 'OVERDUE') return 'error'
  if (message.icon_state === 'DONE' || message.icon_state === 'IGNORED' || message.icon_state === 'HANDLED') {
    return 'neutral'
  }
  if (message.icon_state === 'PENDING') return 'primary'
  if (message.category === 'AI') return 'ai'
  if (message.category === 'LOT') return 'error'
  if (message.category === 'NOTICE') return 'primary'
  if (message.category === 'MEMBER') {
    return message.action?.status === 'PENDING' ? 'primary' : 'neutral'
  }
  return 'neutral'
}

const TAG_TYPES: Record<string, TagType> = {
  IMPORTANT: 'primary',
  ATTENTION: 'warning',
  URGENT: 'error',
  EMERGENCY: 'error',
  GENERAL: 'neutral',
  DONE: 'neutral',
  PASSED: 'neutral',
  REJECTED: 'error',
  RECALLED: 'error'
}

export function messageTag(message: Pick<MessageBriefVO, 'tag_text' | 'tag_code' | 'level'>): TagInfo | null {
  if (!message.tag_text) return null
  const type = TAG_TYPES[message.tag_code || ''] || (message.level === 'IMPORTANT' ? 'primary' : 'neutral')
  const icon = message.tag_code === 'ATTENTION' ? 'priority_high' : undefined
  return { text: message.tag_text, type, icon }
}

export function dateGroupLabel(group?: string): string {
  if (group === 'TODAY') return '今天'
  if (group === 'YESTERDAY') return '昨天'
  return '更早'
}

/** 列表时间：今天/昨天写时刻，更早写日期（跨年带年份），按服务端时间字符串直接截取，避免时区偏移。 */
export function messageTime(message: Pick<MessageBriefVO, 'created_at' | 'date_group'>): string {
  const text = message.created_at || ''
  if (!text) return '—'
  if (message.date_group === 'TODAY' || message.date_group === 'YESTERDAY') return text.slice(11, 16)
  const date = dayjs(text.slice(0, 10))
  if (!date.isValid()) return text
  const now = dayjs(today())
  return date.year() === now.year() ? date.format('M月D日') : date.format('YYYY年M月D日')
}

/* ---------------- 去向 ---------------- */

/**
 * App 页面编号 → Web 路由与参数。服务端去向参数是 snake_case（task_id、batch_id…），
 * Web 页面读 camelCase；“去登记”时按目标页的动作词打开对应表单（见 composables/useRouteAction）。
 */
interface RouteSpec {
  name: string
  /** snake_case → 目标页参数名 */
  params?: Record<string, string>
  /** 去登记时的动作词；不填表示目标页没有对应表单，只打开页面 */
  create?: string
}

const ROUTE_MAP: Record<string, RouteSpec> = {
  G02: { name: 'WU-G01', params: { task_id: 'taskId' }, create: 'create' },
  I02: { name: 'WU-I01', params: { pen_id: 'penId' }, create: 'create' },
  D03: { name: 'WU-D01', params: { batch_id: 'batchId', plan_item_id: 'planItemId' }, create: 'create' },
  D08: { name: 'WU-D03', params: { task_id: 'taskId' }, create: 'create' },
  G05: { name: 'WU-G03', params: { batch_id: 'batchId', cycle_id: 'cycleId' }, create: 'create' },
  B02: { name: 'WU-B02' },
  B04: { name: 'WU-B02', create: 'farrowing' },
  // 出雏在孵化页按步骤登记，页面默认选中今天预计出雏的孵化批次
  B07: { name: 'WU-B03' },
  M01: { name: 'WU-M01', params: { feed_id: 'feedId' } },
  M03: { name: 'WU-M01', params: { feed_id: 'feedId' }, create: 'inbound' },
  M05: { name: 'WU-M02', params: { drug_id: 'drugId' } },
  M08: { name: 'WU-M02', params: { drug_id: 'drugId' }, create: 'inbound' },
  // 临期 / 过期：App M07 是登记报废，Web 在兽药库存页找到该药品需关注的批号后进入批号页报废
  M07: { name: 'WU-M02', params: { drug_id: 'drugId' }, create: 'scrap' },
  D05: { name: 'WU-D02', params: { batch_id: 'batchId' } },
  E06: { name: 'WU-E03', params: { batch_id: 'batchId' }, create: 'create' },
  E13: { name: 'WU-E07' },
  U02: { name: 'WU-U01' }
}

function renameParams(params: Record<string, string>, names: Record<string, string> = {}): Record<string, string> {
  const query: Record<string, string> = {}
  for (const [from, to] of Object.entries(names)) {
    const value = params[from]
    if (value !== undefined && value !== null && value !== '') query[to] = String(value)
  }
  return query
}

/** App 页面编号 → Web 路由；路径参数按 Web 路由定义映射，查询参数改成目标页读取的名字。 */
export function resolveLinkTarget(
  link: LinkVO | null | undefined,
  options: { create?: boolean } = {}
): NavTarget | null {
  if (!link?.route) return null
  const params = link.params || {}
  switch (link.route) {
    case 'M06':
      if (params.lot_id) return { name: 'WU-M03', params: { lotId: params.lot_id } }
      return {
        name: 'WU-M02',
        query: { ...renameParams(params, { drug_id: 'drugId' }), ...(options.create ? { action: 'scrap' } : {}) }
      }
    case 'E07':
      return params.sale_order_id
        ? { name: 'WU-E04', params: { id: params.sale_order_id } }
        : { name: 'WU-E03' }
    case 'I06':
      // 异常上报待办（ABNORMAL_REPORT）：去处理时打开诊断详情的处理抽屉
      return params.report_id
        ? { name: 'WU-I04', params: { id: params.report_id }, ...(options.create ? { query: { action: 'handle' } } : {}) }
        : { name: 'WU-I03' }
    case 'F07':
      return params.farm_id
        ? { name: 'WU-F04', params: { farmId: params.farm_id } }
        : { name: 'WU-F01' }
    case 'F03':
      return params.farm_id
        ? { name: 'WU-F03', params: { farmId: params.farm_id } }
        : { name: 'WU-F01' }
    case 'L06':
      return params.batch_id
        ? { name: 'WU-L04', params: { batchId: params.batch_id } }
        : { name: 'WU-L03' }
    case 'T04':
    case 'T07':
      return null
    default: {
      const spec = ROUTE_MAP[link.route]
      if (!spec) return null
      const query = renameParams(params, spec.params)
      if (options.create && spec.create) query.action = spec.create
      return Object.keys(query).length ? { name: spec.name, query } : { name: spec.name }
    }
  }
}

/** 消息右栏主按钮的去向。 */
export function messageTarget(message: MessageDetailVO): NavTarget | null {
  const farmId = message.farm?.farm_id
  switch (message.msg_type) {
    case 'AI_DIAGNOSIS':
      return message.biz_id ? { name: 'WU-I04', params: { id: String(message.biz_id) } } : null
    case 'LOT_RECALLED':
      return message.biz_id ? { name: 'WU-M03', params: { lotId: String(message.biz_id) } } : null
    case 'CHANGE_REQ_RESULT':
      return {
        name: 'WU-U01',
        query: message.biz_id ? { request_id: String(message.biz_id) } : undefined
      }
    case 'FARM_AUDIT_RESULT':
      return {
        name: 'WU-F01',
        query: {
          action: 'audit',
          ...(farmId ? { farmId: String(farmId) } : {})
        }
      }
    case 'FARM_CHANGE_RESULT':
      return farmId ? { name: 'WU-F03', params: { farmId: String(farmId) } } : { name: 'WU-F01' }
    case 'REPORT_READY':
      return { name: 'WU-E07' }
    case 'INVITE_ACCEPTED':
      return farmId ? { name: 'WU-F04', params: { farmId: String(farmId) } } : null
    default:
      return resolveLinkTarget(message.link)
  }
}

export function messageActionText(message: MessageDetailVO): string {
  if (message.msg_type === 'AI_DIAGNOSIS') return '查看诊断详情'
  if (message.msg_type === 'LOT_RECALLED') return '查看批号'
  if (message.msg_type === 'CHANGE_REQ_RESULT') return '查看修改申请'
  if (message.msg_type === 'FARM_AUDIT_RESULT' || message.msg_type === 'FARM_CHANGE_RESULT') return '查看审核结果'
  if (message.msg_type === 'REPORT_READY') return '查看报表'
  if (message.msg_type === 'INVITE_ACCEPTED') return '查看成员'
  return '去查看'
}

/* ---------------- 导航 ---------------- */

export function useTodayLinks() {
  const router = useRouter()
  const farm = useFarmStore()
  const ui = useUiStore()

  async function go(link: LinkVO | null | undefined, options: { create?: boolean; query?: Record<string, string> } = {}): Promise<boolean> {
    if (!link?.route) return false
    if (link.farm_id && link.farm_id !== farm.currentFarmId) {
      try {
        await farm.switchTo(link.farm_id)
        ui.toast(`已切换到${link.farm_name || '目标养殖场'}`)
      } catch (error) {
        ui.toast(error instanceof ApiError ? error.message : '切换养殖场失败，请稍后重试', 'error')
        return false
      }
    }
    const target = resolveLinkTarget(link, { create: options.create })
    if (!target) return false
    const query: Record<string, string> = { ...(target.query || {}), ...(options.query || {}) }
    await router.push({ name: target.name, params: target.params, query })
    return true
  }

  function goTarget(target: NavTarget): void {
    void router.push({ name: target.name, params: target.params, query: target.query })
  }

  return {
    go,
    goRegister: (link: LinkVO | null | undefined) => go(link, { create: true }),
    goList: (link: LinkVO | null | undefined) => go(link),
    goTarget
  }
}

/* ---------------- payload 读取 ---------------- */

export function payloadStr(message: MessageDetailVO, key: string): string {
  const value = message.payload?.[key]
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map((item) => String(item)).join('、')
  return String(value)
}

export function payloadNum(message: MessageDetailVO, key: string): number | null {
  const value = Number(message.payload?.[key])
  return Number.isFinite(value) ? value : null
}

export function payloadArr(message: MessageDetailVO, key: string): string[] {
  const value = message.payload?.[key]
  return Array.isArray(value) ? value.map((item) => String(item)) : []
}

export function messageTodoId(message: MessageBriefVO): number | null {
  return message.todo_id ? Number(message.todo_id) : null
}
