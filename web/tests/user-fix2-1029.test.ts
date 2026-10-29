import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useFarmStore } from '../apps/user/src/stores/farm'
import { farmSwitchTarget, useFarmReload } from '../apps/user/src/composables/useFarmReload'
import { useSettlementStore } from '../apps/user/src/stores/finance/settlement'
import { costNoteText } from '../apps/user/src/stores/finance/shared'
import { useImmunePlanStore } from '../apps/user/src/stores/health/immune'
import { useMedicationStore } from '../apps/user/src/stores/health/medication'
import { useFeedTaskStore } from '../apps/user/src/stores/feeding/feedTask'
import { useBreedingPendingStore } from '../apps/user/src/stores/breeding/pending'
import { checkNotDueText } from '../apps/user/src/stores/breeding/shared'
import { allocationText, flowTimeText } from '../apps/user/src/views/stock/stock'
import { hasCitation, isStreamingMessage, stripCitationMarkers } from '../apps/user/src/stores/ai/shared'
import AiMessageBlock from '../apps/user/src/views/ai/components/AiMessageBlock.vue'
import type { LocalAiMessage } from '../apps/user/src/stores/ai/assistant'

const finance = vi.hoisted(() => ({ settlementOptions: vi.fn(), settlementDetail: vi.fn(), batchOptions: vi.fn() }))
vi.mock('../apps/user/src/api/finance', () => ({ financeApi: finance }))
vi.mock('../apps/user/src/stores/ui', () => ({ useUiStore: () => ({ toast: vi.fn() }) }))

beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
  vi.resetAllMocks()
  const farm = useFarmStore()
  farm.farms = [
    { farm_id: 1001, name: '东山猪场', role: 'OWNER', count_unit_text: '头' },
    { farm_id: 1002, name: '西岭鸡场', role: 'OWNER', count_unit_text: '只' }
  ]
  farm.setCurrent(1002)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('N6 鸡场计数单位与千分位', () => {
  it('免疫、用药、饲喂、成本分摊按当前养殖场 / 批次单位显示并加千分位', () => {
    const immune = useImmunePlanStore()
    immune.overview = { pen_name: '2 号鸡舍', batch_no: 'ZM2610-026', breed_name: '白羽肉鸡', day_age: 19, current_count: 9300 } as never
    expect(immune.addSub).toContain('存栏 9,300 只')
    immune.batches = [{ batch_id: 3201, unit_text: '羽' }] as never
    immune.batchId = 3201
    expect(immune.countUnit).toBe('羽')

    const medication = useMedicationStore()
    medication.targetsBatch = [{ batch_id: 3201, batch_no: 'ZM2610-026', pen_name: '2 号鸡舍', current_count: 9300 }] as never
    expect(medication.batchTargetItems[0].subtitle).toBe('存栏 9,300 只')

    const feed = useFeedTaskStore()
    feed.record = { plan_formula: { head_count: 9300, daily_qty_per_head: 0.12, meals_per_day: 3 } } as never
    expect(feed.formulaText).toContain('9,300 只 × 每只日投喂 0.12 千克 ÷ 3 餐')

    expect(allocationText({ batch_id: 3201, head_count: 9300, ratio: 0.5 }, '只')).toBe('存栏 9,300 只 · 50.0%')
  })

  it('繁育待产、待妊检用养殖场单位；核算成本说明“9500 头 × 3.2 元”换成批次单位', () => {
    const pending = useBreedingPendingStore()
    expect(pending.countUnit).toBe('只')
    pending.overview = { countUnitText: '头' } as never
    pending.pendingChecks = [{ animalId: 1 }, { animalId: 2 }] as never
    expect(pending.checkDueText).toBe('待妊检 · 2 头')

    expect(costNoteText('9500 头 × 3.2 元', '只')).toBe('9,500 只 × 3.2 元')
    expect(costNoteText('100 头 × 500 元', '头')).toBe('100 头 × 500 元')
    expect(costNoteText('人工、水电等', '只')).toBe('人工、水电等')
    expect(costNoteText(undefined, '只')).toBe('')
  })
})

describe('N8 切场后清掉属于旧场的批次参数', () => {
  function setupRouter() {
    const blank = defineComponent({ render: () => h('div') })
    return createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: blank },
        { path: '/feeding/plans', name: 'WU-G02', component: blank },
        { path: '/herd/batches', name: 'WU-L03', component: blank },
        { path: '/herd/batches/:batchId', name: 'WU-L04', component: blank, meta: { farmScopedParent: 'WU-L03' } }
      ]
    })
  }

  async function mountPage(path: string, reload: (batchId: unknown) => void) {
    const router = setupRouter()
    await router.push(path)
    await router.isReady()
    const Page = defineComponent({
      setup() {
        useFarmReload(() => reload(router.currentRoute.value.query.batchId))
        return () => h('div')
      }
    })
    mount(Page, { global: { plugins: [router] } })
    return router
  }

  it('带 batchId 的页面切场：先去掉批次参数再按新场重新加载', async () => {
    const reload = vi.fn()
    const router = await mountPage('/feeding/plans?batchId=3201&tab=exec', reload)
    // 切场接口成功后 farm.switchTo 调用 setCurrent：这里直接模拟
    useFarmStore().setCurrent(1001)
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ tab: 'exec' })
    expect(reload).toHaveBeenCalledTimes(1)
    expect(reload).toHaveBeenCalledWith(undefined)
  })

  it('批次档案等详情页切场回到列表页，不再按旧编号请求', async () => {
    const reload = vi.fn()
    const router = await mountPage('/herd/batches/3201', reload)
    useFarmStore().setCurrent(1001)
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('WU-L03')
    expect(reload).not.toHaveBeenCalled()
  })

  it('没有换养殖场（刷新列表、进出全部养殖场视角）时不动地址，只重新加载', async () => {
    const reload = vi.fn()
    const router = await mountPage('/feeding/plans?batchId=3201', reload)
    useFarmStore().setCurrent(1002)
    useFarmStore().setViewAll(true)
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ batchId: '3201' })
    expect(reload).toHaveBeenCalledWith('3201')
  })

  it('farmSwitchTarget 只去掉批次、栏舍等编号参数', () => {
    expect(farmSwitchTarget({ meta: {}, query: { batch_id: '1', penId: '2', tab: 'due', action: 'create' } })).toEqual({
      query: { tab: 'due', action: 'create' }
    })
    expect(farmSwitchTarget({ meta: {}, query: { tab: 'due' } })).toBeNull()
    expect(farmSwitchTarget({ meta: { farmScopedParent: 'WU-E03' }, query: {} })).toEqual({ name: 'WU-E03' })
  })
})

describe('N9 批次核算头部与休药提示', () => {
  const chickenDetail = {
    status: 'RUNNING',
    perHeadBasis: 'STOCK',
    perHeadCount: 9300,
    saleOrders: [],
    batch: { batchId: 3201, batchNo: 'ZM2610-026', countUnitText: '只' }
  }

  it('平摊口径带千分位和批次单位；只有休药中的批次提示“休药期结束后可登记出栏”', async () => {
    finance.settlementOptions.mockResolvedValue({ list: [{ batchId: 3201 }, { batchId: 3202 }] })
    finance.settlementDetail.mockImplementation(async (batchId: number) => ({
      ...chickenDetail,
      batch: { ...chickenDetail.batch, batchId }
    }))
    finance.batchOptions.mockResolvedValue([
      { batchId: 3201, inWithdrawal: true, sellableDate: '2026-11-06' },
      { batchId: 3202, inWithdrawal: false }
    ])
    const store = useSettlementStore()
    await store.load(3201)
    await flushPromises()
    expect(store.costPerHeadNote).toBe('截至今天按存栏 9,300 只平摊')
    expect(store.withdrawalHint).toBe('休药期结束后可登记出栏（11月6日起）')

    await store.selectBatch(3202)
    await flushPromises()
    expect(store.withdrawalHint).toBe('')
  })

  it('已结算的批次不查休药', async () => {
    finance.settlementOptions.mockResolvedValue({ list: [{ batchId: 3010 }] })
    finance.settlementDetail.mockResolvedValue({ ...chickenDetail, status: 'LOCKED', perHeadBasis: 'SOLD', perHeadCount: 98, batch: { batchId: 3010, countUnitText: '头' } })
    const store = useSettlementStore()
    await store.load(3010)
    await flushPromises()
    expect(store.costPerHeadNote).toBe('按出栏 98 头平摊')
    expect(finance.batchOptions).not.toHaveBeenCalled()
    expect(store.withdrawalHint).toBe('')
  })
})

describe('N10 配种不满 18 天不给登记妊检入口', () => {
  it('按服务端 pending_check 判断，缺字段时按配种天数', () => {
    expect(checkNotDueText({ nextAction: 'PREG_CHECK', pendingCheck: false, daysSinceMating: 5 })).toBe('13 天后可妊检')
    expect(checkNotDueText({ nextAction: 'PREG_CHECK', pendingCheck: true, daysSinceMating: 21 })).toBe('')
    expect(checkNotDueText({ nextAction: 'PREG_CHECK', daysSinceMating: 17 })).toBe('1 天后可妊检')
    expect(checkNotDueText({ nextAction: 'PREG_CHECK', daysSinceMating: 18 })).toBe('')
    expect(checkNotDueText({ nextAction: 'FARROWING', pendingCheck: false })).toBe('')
  })
})

describe('N11 / N17 AI 消息', () => {
  const render = (message: LocalAiMessage, streaming: boolean) => mount(AiMessageBlock, {
    props: { message, streaming, activeCitationId: null, retryDisabled: true },
    global: { stubs: { ZwIcon: true, ZwTag: true, VTooltip: true } }
  })

  it('重新生成本轮提问的回答（本地负 ID）时按生成中显示，不出现“回答中断”', () => {
    const message: LocalAiMessage = { id: -3, local_server_id: 55, status: 'ANSWERING', answer_text: '' }
    expect(isStreamingMessage(55, message)).toBe(true)
    expect(isStreamingMessage(-3, message)).toBe(true)
    expect(isStreamingMessage(56, message)).toBe(false)
    expect(isStreamingMessage(null, message)).toBe(false)
    const wrapper = render(message, isStreamingMessage(55, message))
    expect(wrapper.text()).toContain('正在连接 AI 助手')
    expect(wrapper.text()).not.toContain('回答中断')
    // 真正中断（没有流）时仍提示重新生成
    expect(render(message, false).text()).toContain('回答中断，点击重新生成')
  })

  it('[^P3] 这类引用写法也渲染成角标，复制和原文摘录时去掉', async () => {
    const citation = { id: 93, seq_no: 3, knowledge_id: 83, title: '通风管理' }
    const message = { id: 1, status: 'ANSWERED', answer_type: 'NORMAL', answer_text: '加强通风[^P3]。保持干燥[^ 1 ]', citations: [citation] }
    const wrapper = render(message, false)
    expect(wrapper.get('.k01-answer').text()).not.toContain('[^')
    const cites = wrapper.findAll('.k01-cite')
    expect(cites.map((item) => item.text())).toEqual(['3', '1'])
    await cites[0].trigger('click')
    expect(wrapper.emitted('cite')?.[0]).toEqual([message, citation])
    expect(stripCitationMarkers('加强通风[^P3]。[^2]')).toBe('加强通风。')
    expect(hasCitation('加强通风[^P3]。', 3)).toBe(true)
    expect(hasCitation('加强通风[^P3]。', 31)).toBe(false)
  })
})

describe('N12 批号流水时间', () => {
  it('往年的流水带年份，今年的不带', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-29T08:00:00+08:00'))
    expect(flowTimeText('2025-10-30 10:00:00')).toBe('2025年10月30日 10:00')
    expect(flowTimeText('2026-10-28 09:05:00')).toBe('10月28日 09:05')
  })
})
