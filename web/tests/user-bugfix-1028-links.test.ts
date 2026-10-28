import { describe, expect, it, vi } from 'vitest'
import { quickItems } from '../packages/shared/src/menus'
import { queryId, queryText } from '../apps/user/src/composables/useRouteAction'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ query: {} }) }))
vi.mock('../apps/user/src/stores/farm', () => ({ useFarmStore: () => ({}) }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

const { resolveLinkTarget, actionLabel } = await import('../apps/user/src/views/today/todo')

describe('路由参数读取兼容 camelCase 与 snake_case', () => {
  it('按顺序取第一个有效值', () => {
    expect(queryId({ task_id: '12' }, 'taskId', 'task_id')).toBe(12)
    expect(queryId({ taskId: '7', task_id: '12' }, 'taskId', 'task_id')).toBe(7)
    expect(queryId({ taskId: ['9'] }, 'taskId')).toBe(9)
    expect(queryId({ taskId: 'abc' }, 'taskId')).toBe(0)
    expect(queryText({ reason: '腹泻' }, 'reason')).toBe('腹泻')
  })
})

describe('顶栏快捷登记统一带 action=create', () => {
  it('死淘转栏落在死淘页签', () => {
    const { work, farmer } = quickItems('owner')
    expect([...work, ...farmer].every((item) => item.action === 'create')).toBe(true)
    expect(work.find((item) => item.key === 'WU-L05')?.query).toEqual({ tab: 'death' })
    expect(quickItems('staff').farmer).toEqual([])
  })
})

describe('待办去向：参数改名并带目标页的动作词', () => {
  const link = (route: string, params: Record<string, string>) => ({ route, params })

  it('作业类去登记', () => {
    expect(resolveLinkTarget(link('G02', { todo_id: '1', task_id: '70107', pen_id: '3' }), { create: true }))
      .toEqual({ name: 'WU-G01', query: { taskId: '70107', action: 'create' } })
    expect(resolveLinkTarget(link('I02', { pen_id: '2003', record_id: '5' }), { create: true }))
      .toEqual({ name: 'WU-I01', query: { penId: '2003', action: 'create' } })
    expect(resolveLinkTarget(link('D03', { batch_id: '3001', plan_item_id: '88' }), { create: true }))
      .toEqual({ name: 'WU-D01', query: { batchId: '3001', planItemId: '88', action: 'create' } })
    expect(resolveLinkTarget(link('D08', { task_id: '41' }), { create: true }))
      .toEqual({ name: 'WU-D03', query: { taskId: '41', action: 'create' } })
    expect(resolveLinkTarget(link('G05', { batch_id: '3001', cycle_id: '9' }), { create: true }))
      .toEqual({ name: 'WU-G03', query: { batchId: '3001', cycleId: '9', action: 'create' } })
    expect(resolveLinkTarget(link('B04', { pen_id: '2007' }), { create: true }))
      .toEqual({ name: 'WU-B02', query: { action: 'farrowing' } })
  })

  it('预警与经营类', () => {
    expect(resolveLinkTarget(link('M03', { feed_id: '5101' }), { create: true }))
      .toEqual({ name: 'WU-M01', query: { feedId: '5101', action: 'inbound' } })
    expect(resolveLinkTarget(link('M08', { drug_id: '61' }), { create: true }))
      .toEqual({ name: 'WU-M02', query: { drugId: '61', action: 'inbound' } })
    expect(resolveLinkTarget(link('M07', { drug_id: '61' }), { create: true }))
      .toEqual({ name: 'WU-M02', query: { drugId: '61', action: 'scrap' } })
    expect(resolveLinkTarget(link('E06', { batch_id: '3001' }), { create: true }))
      .toEqual({ name: 'WU-E03', query: { batchId: '3001', action: 'create' } })
    expect(resolveLinkTarget(link('D05', { batch_id: '3001' })))
      .toEqual({ name: 'WU-D02', query: { batchId: '3001' } })
    expect(resolveLinkTarget(link('M06', { lot_id: '8' }))).toEqual({ name: 'WU-M03', params: { lotId: '8' } })
  })

  it('异常上报待办（I06）去处理打开诊断详情的处理抽屉', () => {
    expect(resolveLinkTarget(link('I06', { report_id: '501' }), { create: true }))
      .toEqual({ name: 'WU-I04', params: { id: '501' }, query: { action: 'handle' } })
    expect(resolveLinkTarget(link('I06', { report_id: '501' }))).toEqual({ name: 'WU-I04', params: { id: '501' } })
    expect(actionLabel({ todo_type: 'ABNORMAL_REPORT', category: 'WARNING', display_status: 'PENDING' })).toBe('去处理')
  })

  it('只看列表时不带动作词', () => {
    expect(resolveLinkTarget(link('G02', { task_id: '70107' }))).toEqual({ name: 'WU-G01', query: { taskId: '70107' } })
    expect(resolveLinkTarget(link('B02', {}))).toEqual({ name: 'WU-B02' })
    expect(resolveLinkTarget(link('T04', {}))).toBeNull()
  })

  it('临期 / 过期待办显示“去处理”，库存不足显示“去入库”', () => {
    expect(actionLabel({ todo_type: 'DRUG_EXPIRED', category: 'WARNING', display_status: 'PENDING' })).toBe('去处理')
    expect(actionLabel({ todo_type: 'FEED_LOW_STOCK', category: 'WARNING', display_status: 'PENDING' })).toBe('去入库')
  })
})
