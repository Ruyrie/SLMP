import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import type {
  BatchDetail,
  BatchEntryResult,
  BatchListItem,
  BaseCategory,
  DeathResult,
  ExecutorOption,
  PenFormOptions,
  PenOption,
  PenStock,
  PenStockItem,
  TransferResult
} from '@/api/herd'
import { herdApi, newRequestKey } from '@/api/herd'
import { useUiStore } from '@/stores/ui'
import { claimFarmScope, currentFarmScope, resetFarmCache } from '@/stores/farmScope'
import {
  errorText,
  fieldErrorsOf,
  statusForError,
  todayISO,
  type PageStatus
} from './shared'

export type HerdActionTab = 'entry' | 'transfer' | 'death'
export type EntrySource = 'PURCHASED' | 'SELF_BRED'
export type EntryPurpose = 'COMMERCIAL' | 'BREEDING'

interface EntryForm {
  penId: number
  breedId: number
  purpose: EntryPurpose
  source: EntrySource
  entryDate: string
  entryAgeDays: number | null
  entryCount: number | null
  entryAvgWeight: number | null
  introUnitPrice: number | null
  capacityConfirmed: boolean
}

interface TransferForm {
  fromPenId: number
  toPenId: number
  quantity: number | null
  eventDate: string
  remark: string
  capacityConfirmed: boolean
  seenStockCount: number
}

interface DeathForm {
  batchId: number
  penId: number
  quantity: number | null
  reasonCode: 'SICK' | 'CULL' | 'ACCIDENT' | 'OTHER'
  eventDate: string
  executorUserId: number
  remark: string
  fileIds: number[]
  seenStockCount: number
}

interface CapacityOver {
  penName: string
  capacity: number
  current: number
  after: number
  over: number
}

interface BatchContext {
  batchId: number
  batch: BatchDetail | null
  pens: PenStock[]
  loading: boolean
  error: string
  executors: ExecutorOption[]
  executorsLoading: boolean
}

const emptyEntryForm = (): EntryForm => ({
  penId: 0,
  breedId: 0,
  purpose: 'COMMERCIAL',
  source: 'PURCHASED',
  entryDate: todayISO(),
  entryAgeDays: null,
  entryCount: null,
  entryAvgWeight: null,
  introUnitPrice: null,
  capacityConfirmed: false
})

const emptyTransferForm = (): TransferForm => ({
  fromPenId: 0,
  toPenId: 0,
  quantity: null,
  eventDate: todayISO(),
  remark: '',
  capacityConfirmed: false,
  seenStockCount: 0
})

const emptyDeathForm = (): DeathForm => ({
  batchId: 0,
  penId: 0,
  quantity: null,
  reasonCode: 'SICK',
  eventDate: todayISO(),
  executorUserId: 0,
  remark: '',
  fileIds: [],
  seenStockCount: 0
})

const emptyContext = (): BatchContext => ({
  batchId: 0,
  batch: null,
  pens: [],
  loading: false,
  error: '',
  executors: [],
  executorsLoading: false
})

export const useHerdEntryStore = defineStore('herdEntry', {
  state: () => ({
    tab: 'entry' as HerdActionTab,
    managerMode: true,
    status: 'loading' as PageStatus,
    error: '',
    batches: [] as BatchListItem[],
    penOptions: [] as PenOption[],
    stockPens: [] as PenStockItem[],
    formOptions: null as PenFormOptions | null,
    categories: [] as BaseCategory[],
    loaded: false,
    farmScope: '',
    requestId: 0,
    // 入栏
    entryOpen: false,
    entryErrors: {} as Record<string, string>,
    entryForm: emptyEntryForm(),
    entryServerOver: null as CapacityOver | null,
    entryResult: null as BatchEntryResult | null,
    entrySuccess: false,
    entryRequestKey: '',
    // 转栏
    transferOpen: false,
    transferErrors: {} as Record<string, string>,
    transferForm: emptyTransferForm(),
    transferServerOver: null as CapacityOver | null,
    transferResult: null as TransferResult | null,
    transferRequestKey: '',
    transferContext: emptyContext(),
    // 死淘
    deathOpen: false,
    deathErrors: {} as Record<string, string>,
    deathForm: emptyDeathForm(),
    deathResult: null as DeathResult | null,
    deathRequestKey: '',
    deathContext: emptyContext(),
    submitting: false
  }),
  getters: {
    isStaffView(): boolean {
      return this.tab === 'death' && !this.formOptions && !this.penOptions.length
    },
    activeBatches(state): BatchListItem[] {
      return state.batches
    },
    entrySelectedPen(state): PenOption | undefined {
      return state.penOptions.find((pen) => Number(pen.pen_id) === state.entryForm.penId)
    },
    entryStockPen(state): PenStockItem | undefined {
      return state.stockPens.find((pen) => Number(pen.pen_id) === state.entryForm.penId)
    },
    entryCategoryName(): string {
      return this.entryStockPen?.category_name || ''
    },
    entryCategory(state) {
      const stockPen = state.stockPens.find((pen) => Number(pen.pen_id) === state.entryForm.penId)
      return state.formOptions?.categories?.find((category) => category.category_name === stockPen?.category_name)
    },
    entryUnit(): string {
      return this.entryCategory?.count_unit_text || this.entrySelectedPen?.unit_text || '头'
    },
    entryBreedOptions(state): Array<{ title: string; value: number }> {
      const category = state.categories.find((item) => item.name === this.entryCategoryName)
      return (category?.breeds || []).map((breed) => ({ title: breed.name || '', value: Number(breed.id || 0) }))
    },
    entryPenItems(state): Array<{ title: string; value: number; subtitle: string; disabled: boolean }> {
      return state.penOptions.map((pen) => {
        const stock = state.stockPens.find((item) => Number(item.pen_id) === Number(pen.pen_id))
        return {
          title: pen.pen_name || pen.pen_code || '',
          value: Number(pen.pen_id || 0),
          subtitle: `在栏 ${Number(pen.stock_count || 0).toLocaleString('en-US')} ${pen.unit_text || '头'} · 容量 ${Number(pen.capacity || 0).toLocaleString('en-US')} ${pen.unit_text || '头'}${stock?.category_name ? ` · ${stock.category_name}` : ''}`,
          disabled: !!pen.disabled
        }
      })
    },
    entryOver(state): CapacityOver | null {
      if (state.entryServerOver) return state.entryServerOver
      const pen = state.penOptions.find((item) => Number(item.pen_id) === state.entryForm.penId)
      const count = Number(state.entryForm.entryCount || 0)
      if (!pen || count <= 0) return null
      const current = Number(pen.stock_count || 0)
      const capacity = Number(pen.capacity || 0)
      const over = Math.max(0, current + count - capacity)
      if (over <= 0) return null
      return {
        penName: pen.pen_name || '栏舍',
        capacity,
        current,
        after: current + count,
        over
      }
    },
    transferFromStock(state): number {
      const row = state.transferContext.batch?.pens?.find((pen) => Number(pen.pen_id) === state.transferForm.fromPenId)
      return Number(row?.stock_count || 0)
    },
    transferUnit(state): string {
      return state.transferContext.batch?.unit_text || '头'
    },
    transferFromItems(): Array<{ title: string; value: number; subtitle: string }> {
      return (this.transferContext.batch?.pens || []).map((pen) => ({
        title: pen.pen_name || '',
        value: Number(pen.pen_id || 0),
        subtitle: `本批次在栏 ${Number(pen.stock_count || 0).toLocaleString('en-US')} ${this.transferUnit}`
      }))
    },
    transferToItems(state): Array<{ title: string; value: number; subtitle: string; disabled: boolean }> {
      const categoryName = state.transferContext.batch?.category_name
      return state.penOptions
        .filter((pen) => Number(pen.pen_id) !== state.transferForm.fromPenId)
        .map((pen) => {
          const stockPen = state.stockPens.find((item) => Number(item.pen_id) === Number(pen.pen_id))
          const sameCategory = !categoryName || !stockPen?.category_name || stockPen.category_name === categoryName
          return {
            title: pen.pen_name || '',
            value: Number(pen.pen_id || 0),
            subtitle: `在栏 ${Number(pen.stock_count || 0).toLocaleString('en-US')} ${pen.unit_text || '头'} · 容量 ${Number(pen.capacity || 0).toLocaleString('en-US')} ${pen.unit_text || '头'}`,
            disabled: !!pen.disabled || !sameCategory
          }
        })
    },
    transferOver(state): CapacityOver | null {
      if (state.transferServerOver) return state.transferServerOver
      const toPen = state.penOptions.find((pen) => Number(pen.pen_id) === state.transferForm.toPenId)
      const quantity = Number(state.transferForm.quantity || 0)
      if (!toPen || quantity <= 0) return null
      const current = Number(toPen.stock_count || 0)
      const capacity = Number(toPen.capacity || 0)
      const over = Math.max(0, current + quantity - capacity)
      if (over <= 0) return null
      return {
        penName: toPen.pen_name || '栏舍',
        capacity,
        current,
        after: current + quantity,
        over
      }
    },
    transferBatch: (state): BatchDetail | null => state.transferContext.batch,
    deathBatch: (state): BatchDetail | null => state.deathContext.batch,
    deathUnit(): string {
      return this.deathContext.batch?.unit_text || '头'
    },
    deathPenItems(): Array<{ title: string; value: number; subtitle: string }> {
      return (this.deathContext.batch?.pens || []).map((pen) => ({
        title: pen.pen_name || '',
        value: Number(pen.pen_id || 0),
        subtitle: `在栏 ${Number(pen.stock_count || 0).toLocaleString('en-US')} ${this.deathUnit}`
      }))
    },
    deathStock(): number {
      const row = this.deathContext.pens.find((pen) => Number(pen.pen_id) === this.deathForm.penId)
      if (row) return Number(row.stock_count || 0)
      const batchRow = this.deathContext.batch?.pens?.find((pen) => Number(pen.pen_id) === this.deathForm.penId)
      return Number(batchRow?.stock_count || 0)
    },
    executorItems(state): Array<{ title: string; value: number }> {
      return state.deathContext.executors.map((item) => ({
        title: item.display_name || item.name || '',
        value: Number(item.user_id || 0)
      }))
    },
    deathQuantityHint(): string {
      const pen = this.deathContext.batch?.pens?.find((item) => Number(item.pen_id) === this.deathForm.penId)
      if (!pen) return ''
      return `${pen.pen_name || '栏舍'} · 在栏 ${Number(pen.stock_count || 0).toLocaleString('en-US')} ${this.deathUnit}`
    },
    canSubmitEntry(state): boolean {
      if (state.submitting) return false
      if (this.entryOver && !state.entryForm.capacityConfirmed) return false
      return true
    },
    canSubmitTransfer(state): boolean {
      if (state.submitting || !state.transferContext.batch) return false
      if (this.transferOver && !state.transferForm.capacityConfirmed) return false
      return true
    }
  },
  actions: {
    /** 页面未挂载时也可能切场：使用缓存前先核对归属，不是当前养殖场就清空。 */
    prepareFarmScope(): string {
      return claimFarmScope(this)
    },
    async load(isStaff: boolean, force = false): Promise<void> {
      const scope = this.prepareFarmScope()
      if (this.loaded && !force) {
        // 栏舍可能在别的页面改名、停用、调整容量：带缓存进入时后台刷新一次选项
        void this.refreshBase(isStaff)
        return
      }
      const requestId = ++this.requestId
      this.managerMode = !isStaff
      this.status = this.batches.length ? this.status : 'loading'
      this.error = ''
      try {
        if (isStaff) {
          this.tab = 'death'
          const page = await herdApi.batches({ page: 1, size: 100, status: 'ACTIVE' })
          if (requestId !== this.requestId || scope !== currentFarmScope()) return
          this.batches = page.list || []
          this.loaded = true
          this.status = this.batches.length ? 'ready' : 'empty'
          return
        }
        const [page, pens, options, categories, stocks] = await Promise.all([
          herdApi.batches({ page: 1, size: 100, status: 'ACTIVE', sort: 'ENTRY_DATE' }),
          herdApi.penOptions(),
          herdApi.penFormOptions(),
          herdApi.baseCategories(),
          herdApi.stockPens({ page: 1, size: 200, sort: 'CODE' })
        ])
        if (requestId !== this.requestId || scope !== currentFarmScope()) return
        this.batches = page.list || []
        this.penOptions = pens || []
        this.formOptions = options
        this.categories = categories || []
        this.stockPens = stocks.list || []
        this.loaded = true
        this.status = this.batches.length || this.penOptions.length ? 'ready' : 'empty'
      } catch (error) {
        if (requestId !== this.requestId || scope !== currentFarmScope()) return
        this.status = statusForError(error)
        this.error = errorText(error, '登记数据加载失败，请稍后重新加载')
      }
    },
    /** 刷新批次与栏舍选项（栏舍改名、停用、在栏数变化后下拉要跟着变）；失败保留原数据。 */
    async refreshBase(isStaff: boolean): Promise<void> {
      const scope = this.farmScope
      const fresh = (): boolean => scope === this.farmScope && scope === currentFarmScope()
      const jobs: Promise<unknown>[] = [
        herdApi.batches({ page: 1, size: 100, status: 'ACTIVE', sort: 'ENTRY_DATE' }).then((page) => {
          if (fresh()) this.batches = page.list || []
        })
      ]
      if (!isStaff) {
        jobs.push(herdApi.penOptions().then((pens) => {
          if (fresh()) this.penOptions = pens || []
        }))
        jobs.push(herdApi.stockPens({ page: 1, size: 200, sort: 'CODE' }).then((stocks) => {
          if (fresh()) this.stockPens = stocks.list || []
        }))
        jobs.push(herdApi.penFormOptions().then((options) => {
          if (fresh() && options) this.formOptions = options
        }))
      }
      await Promise.allSettled(jobs)
      if (!fresh() || !this.entryOpen || !this.entryForm.penId) return
      const pen = this.penOptions.find((item) => Number(item.pen_id) === this.entryForm.penId)
      // 已选栏舍被停用或不再可选：清空选择，避免提交到停用栏舍
      if (!pen || pen.disabled) {
        this.entryForm.penId = 0
        this.entryForm.breedId = 0
        this.entryForm.capacityConfirmed = false
        this.entryServerOver = null
      }
    },
    setTab(tab: HerdActionTab): void {
      this.tab = tab
      this.entryOpen = false
      this.transferOpen = false
      this.deathOpen = false
    },
    async openEntry(): Promise<void> {
      if (!this.formOptions) return
      this.entryForm = emptyEntryForm()
      this.entryErrors = {}
      this.entryServerOver = null
      this.entryRequestKey = ''
      this.entryOpen = true
      const selectable = this.penOptions.filter((pen) => !pen.disabled)
      if (selectable.length === 1) this.chooseEntryPen(Number(selectable[0].pen_id || 0))
      // 打开表单时重拉栏舍：改名、停用后下拉立即跟上
      await this.refreshBase(false)
    },
    chooseEntryPen(penId: number): void {
      this.entryForm.penId = Number(penId || 0)
      this.entryForm.breedId = 0
      this.entryForm.capacityConfirmed = false
      this.entryServerOver = null
      delete this.entryErrors.pen_id
      delete this.entryErrors.breed_id
      const category = this.entryCategory
      if (category) this.entryForm.purpose = category.is_breeding ? 'BREEDING' : 'COMMERCIAL'
    },
    chooseEntryBreed(breedId: number): void {
      this.entryForm.breedId = Number(breedId || 0)
      delete this.entryErrors.breed_id
    },
    onEntryCount(value: number | null): void {
      this.entryForm.entryCount = value
      this.resetEntryOver()
      delete this.entryErrors.entry_count
      delete this.entryErrors.capacity
    },
    onEntryWeight(value: number | null): void {
      this.entryForm.entryAvgWeight = value
      delete this.entryErrors.entry_avg_weight
    },
    onEntryAge(value: number | null): void {
      this.entryForm.entryAgeDays = value
      delete this.entryErrors.entry_age_days
    },
    async selectTransferBatch(batchId: number): Promise<void> {
      this.transferContext = emptyContext()
      if (batchId) await this.loadContext('transfer', batchId)
    },
    onTransferQuantity(value: number | null): void {
      this.transferForm.quantity = value
      this.resetTransferOver()
      delete this.transferErrors.quantity
      delete this.transferErrors.capacity
    },
    onDeathQuantity(value: number | null): void {
      this.deathForm.quantity = value
      delete this.deathErrors.quantity
    },
    setEntrySource(source: EntrySource): void {
      this.entryForm.source = source
      if (source === 'SELF_BRED') {
        this.entryForm.introUnitPrice = null
        delete this.entryErrors.intro_unit_price
      }
    },
    resetEntryOver(): void {
      this.entryServerOver = null
      this.entryForm.capacityConfirmed = false
    },
    validateEntry(): boolean {
      this.entryErrors = {}
      const form = this.entryForm
      if (!form.penId) this.entryErrors.pen_id = '请选择栏舍'
      if (!form.breedId) this.entryErrors.breed_id = '请选择品种'
      if (!form.entryDate) this.entryErrors.entry_date = '请选择入栏日期'
      else if (form.entryDate > todayISO()) this.entryErrors.entry_date = '入栏日期不能晚于今天'
      if (form.entryAgeDays === null || form.entryAgeDays === undefined || !Number.isInteger(Number(form.entryAgeDays)) || Number(form.entryAgeDays) < 0) {
        this.entryErrors.entry_age_days = '入栏日龄需为 0 或正整数'
      }
      if (form.entryCount === null || !Number.isInteger(Number(form.entryCount)) || Number(form.entryCount) <= 0) {
        this.entryErrors.entry_count = '数量需为大于 0 的整数'
      }
      if (form.entryAvgWeight !== null && form.entryAvgWeight !== undefined && String(form.entryAvgWeight) !== '') {
        if (!(Number(form.entryAvgWeight) > 0) || !hasAtMostTwoDecimals(form.entryAvgWeight)) {
          this.entryErrors.entry_avg_weight = '请输入大于 0 的数字，最多保留 2 位小数'
        }
      }
      if (form.source === 'PURCHASED') {
        if (form.introUnitPrice === null || form.introUnitPrice === undefined || String(form.introUnitPrice) === '') {
          this.entryErrors.intro_unit_price = '请填写引种单价'
        } else if (!(Number(form.introUnitPrice) > 0) || !hasAtMostTwoDecimals(form.introUnitPrice)) {
          this.entryErrors.intro_unit_price = '引种单价最多保留 2 位小数'
        }
      }
      if (this.entryOver && !form.capacityConfirmed) this.entryErrors.capacity = '请勾选容量确认后再提交'
      return Object.keys(this.entryErrors).length === 0
    },
    async submitEntry(): Promise<boolean> {
      if (this.submitting || !this.validateEntry()) return false
      this.submitting = true
      if (!this.entryRequestKey) this.entryRequestKey = newRequestKey()
      try {
        const result = await herdApi.createBatch({
          pen_id: this.entryForm.penId,
          breed_id: this.entryForm.breedId,
          purpose: this.entryForm.purpose,
          source: this.entryForm.source,
          entry_date: this.entryForm.entryDate,
          entry_age_days: Number(this.entryForm.entryAgeDays),
          entry_count: Number(this.entryForm.entryCount),
          entry_avg_weight: this.entryForm.entryAvgWeight ?? undefined,
          intro_unit_price: this.entryForm.source === 'PURCHASED' ? Number(this.entryForm.introUnitPrice) : undefined,
          capacity_confirmed: this.entryForm.capacityConfirmed
        }, this.entryRequestKey)
        this.applyEntrySuccess(result)
        await this.refreshBase(false)
        return true
      } catch (error) {
        await this.handleEntryError(error)
        return false
      } finally {
        this.submitting = false
      }
    },
    applyEntrySuccess(result: BatchEntryResult): void {
      this.entryResult = result
      this.entryOpen = false
      this.entrySuccess = true
      this.entryRequestKey = ''
      this.entryServerOver = null
      useUiStore().toast(`批次 ${result.batch_no || ''} 已生成`)
    },
    async handleEntryError(error: unknown): Promise<void> {
      if (error instanceof ApiError) {
        if (error.code === 42206) {
          this.entryServerOver = capacityOverFromError(error)
          this.entryForm.capacityConfirmed = false
          this.entryErrors = {}
          this.entryRequestKey = ''
          useUiStore().toast('入栏后超过栏舍容量，请确认后重新提交', 'error')
          return
        }
        if (error.isNetwork) {
          const recovered = await this.recoverEntryRequest()
          if (recovered) return
        }
        if (!error.isNetwork) this.entryRequestKey = ''
        const fields = fieldErrorsOf(error)
        if (Object.keys(fields).length) {
          this.entryErrors = { ...fields }
          useUiStore().toast('请检查表单中的错误', 'error')
        } else {
          useUiStore().toast(error.message, 'error')
        }
      } else {
        this.entryRequestKey = ''
        useUiStore().toast('提交失败，请稍后重试', 'error')
      }
    },
    async recoverEntryRequest(): Promise<boolean> {
      if (!this.entryRequestKey) return false
      try {
        const check = await herdApi.checkRequest(this.entryRequestKey)
        if (check.exists && check.result) {
          this.applyEntrySuccess(check.result as BatchEntryResult)
          return true
        }
      } catch {
        /* 查询失败按未保存处理 */
      }
      useUiStore().toast('提交没有成功，请重试', 'error')
      return false
    },
    async openTransfer(batchId?: number): Promise<void> {
      this.transferOpen = true
      this.transferErrors = {}
      this.transferServerOver = null
      this.transferRequestKey = ''
      this.transferResult = null
      this.transferForm = emptyTransferForm()
      const target = Number(batchId || this.transferContext.batchId || 0)
      if (target && target !== this.transferContext.batchId) await this.loadContext('transfer', target)
      this.syncTransferFromContext()
    },
    async openDeath(batchId?: number): Promise<void> {
      this.deathOpen = true
      this.deathErrors = {}
      this.deathRequestKey = ''
      this.deathResult = null
      this.deathForm = emptyDeathForm()
      const target = Number(batchId || this.deathContext.batchId || 0)
      if (target && target !== this.deathContext.batchId) await this.loadContext('death', target)
      this.syncDeathFromContext()
    },
    async loadContext(kind: 'transfer' | 'death', batchId: number): Promise<void> {
      if (!batchId) return
      const current = (): BatchContext => (kind === 'transfer' ? this.transferContext : this.deathContext)
      const assign = (next: BatchContext): void => {
        if (kind === 'transfer') this.transferContext = next
        else this.deathContext = next
      }
      if (current().batchId === batchId && current().batch) return
      // 每一步都换成新对象再赋值：先赋给响应式 state 再改原始对象，界面收不到变化会一直骨架屏
      const pending: BatchContext = { ...emptyContext(), batchId, loading: true }
      assign(pending)
      const scope = this.farmScope
      // 期间又换了批次 / 养殖场：旧请求的结果直接丢弃
      const stale = (): boolean => current().batchId !== batchId || !current().loading || scope !== this.farmScope
      try {
        const [batch, pens] = await Promise.all([herdApi.batchDetail(batchId), herdApi.batchPenStocks(batchId)])
        if (stale()) return
        assign({ ...emptyContext(), batchId, batch, pens: pens || [] })
        if (kind === 'transfer') this.syncTransferFromContext()
        else this.syncDeathFromContext()
      } catch (error) {
        if (stale()) return
        assign({
          ...emptyContext(),
          batchId,
          error: errorText(error, kind === 'transfer' ? '转栏批次加载失败' : '死淘批次加载失败')
        })
      }
    },
    syncTransferFromContext(): void {
      const batch = this.transferContext.batch
      if (!batch) return
      const pens = batch.pens || []
      this.transferForm.fromPenId = pens.length === 1 ? Number(pens[0].pen_id || 0) : 0
      this.transferForm.toPenId = 0
      this.transferForm.quantity = null
      this.transferForm.eventDate = todayISO()
      this.transferForm.remark = ''
      this.transferForm.capacityConfirmed = false
      this.transferForm.seenStockCount = Number(pens[0]?.stock_count || 0)
      this.transferErrors = {}
      this.transferServerOver = null
    },
    syncDeathFromContext(): void {
      const batch = this.deathContext.batch
      if (!batch) return
      const pens = batch.pens || []
      this.deathForm.batchId = Number(batch.batch_id || 0)
      this.deathForm.penId = pens.length === 1 ? Number(pens[0].pen_id || 0) : 0
      this.deathForm.quantity = null
      this.deathForm.reasonCode = 'SICK'
      this.deathForm.eventDate = todayISO()
      this.deathForm.executorUserId = 0
      this.deathForm.remark = ''
      this.deathForm.fileIds = []
      this.deathForm.seenStockCount = Number(pens[0]?.stock_count || 0)
      this.deathErrors = {}
      if (this.managerMode && this.deathForm.penId) void this.loadExecutors()
    },
    selectTransferFromPen(penId: number): void {
      this.transferForm.fromPenId = Number(penId || 0)
      const row = this.transferContext.batch?.pens?.find((pen) => Number(pen.pen_id) === this.transferForm.fromPenId)
      this.transferForm.seenStockCount = Number(row?.stock_count || 0)
      this.transferForm.quantity = null
      this.transferForm.toPenId = 0
      this.transferForm.capacityConfirmed = false
      this.transferServerOver = null
      delete this.transferErrors.quantity
      delete this.transferErrors.to_pen_id
    },
    selectTransferToPen(penId: number): void {
      this.transferForm.toPenId = Number(penId || 0)
      this.transferForm.capacityConfirmed = false
      this.transferServerOver = null
      delete this.transferErrors.to_pen_id
    },
    resetTransferOver(): void {
      this.transferServerOver = null
      this.transferForm.capacityConfirmed = false
    },
    async selectDeathBatch(batchId: number): Promise<void> {
      this.deathForm.batchId = Number(batchId || 0)
      this.deathForm.penId = 0
      this.deathForm.quantity = null
      this.deathContext = emptyContext()
      if (batchId) await this.loadContext('death', batchId)
      this.syncDeathFromContext()
    },
    async selectDeathPen(penId: number): Promise<void> {
      this.deathForm.penId = Number(penId || 0)
      this.deathForm.quantity = null
      const row = this.deathContext.batch?.pens?.find((pen) => Number(pen.pen_id) === this.deathForm.penId)
      this.deathForm.seenStockCount = Number(row?.stock_count || 0)
      delete this.deathErrors.quantity
      if (this.managerMode) await this.loadExecutors()
    },
    async loadExecutors(): Promise<void> {
      if (!this.formOptions) return
      if (!this.deathForm.penId) {
        this.deathContext.executors = []
        return
      }
      this.deathContext.executorsLoading = true
      try {
        const rows = await herdApi.penExecutors(this.deathForm.penId)
        this.deathContext.executors = rows || []
        const me = rows?.find((item) => item.is_me)
        if (!this.deathForm.executorUserId) this.deathForm.executorUserId = Number(me?.user_id || rows?.[0]?.user_id || 0)
      } catch {
        this.deathContext.executors = []
      } finally {
        this.deathContext.executorsLoading = false
      }
    },
    validateTransfer(): boolean {
      this.transferErrors = {}
      const form = this.transferForm
      if (!form.fromPenId) this.transferErrors.from_pen_id = '请选择转出栏舍'
      if (!form.toPenId) this.transferErrors.to_pen_id = '请选择转入栏舍'
      if (form.fromPenId && form.fromPenId === form.toPenId) this.transferErrors.to_pen_id = '转入栏舍不能与转出栏舍相同'
      if (form.quantity === null || !Number.isInteger(Number(form.quantity)) || Number(form.quantity) <= 0) {
        this.transferErrors.quantity = '数量需为大于 0 的整数'
      } else if (Number(form.quantity) > this.transferFromStock) {
        this.transferErrors.quantity = `不能超过转出栏舍的在栏数 ${this.transferFromStock} ${this.transferUnit}`
      }
      if (!form.eventDate) this.transferErrors.event_date = '请选择转栏日期'
      else if (form.eventDate > todayISO()) this.transferErrors.event_date = '转栏日期不能晚于今天'
      else if (this.transferContext.batch?.entry_date && form.eventDate < this.transferContext.batch.entry_date) {
        this.transferErrors.event_date = `转栏日期不能早于入栏日期 ${monthDayText(this.transferContext.batch.entry_date)}`
      }
      if (this.transferOver && !form.capacityConfirmed) this.transferErrors.capacity = '请勾选容量确认后再保存'
      return Object.keys(this.transferErrors).length === 0
    },
    async submitTransfer(): Promise<boolean> {
      if (this.submitting || !this.validateTransfer()) return false
      this.submitting = true
      if (!this.transferRequestKey) this.transferRequestKey = newRequestKey()
      try {
        const result = await herdApi.createTransfer({
          batch_id: this.transferContext.batchId,
          from_pen_id: this.transferForm.fromPenId,
          to_pen_id: this.transferForm.toPenId,
          quantity: Number(this.transferForm.quantity),
          event_date: this.transferForm.eventDate,
          remark: this.transferForm.remark || undefined,
          capacity_confirmed: this.transferForm.capacityConfirmed,
          seen_stock_count: this.transferForm.seenStockCount
        }, this.transferRequestKey)
        this.transferResult = result
        this.transferOpen = false
        this.transferRequestKey = ''
        this.transferServerOver = null
        useUiStore().toast(result.message || '转栏已登记')
        await this.refreshBase(false)
        await this.loadContext('transfer', result.batch_id || this.transferContext.batchId)
        return true
      } catch (error) {
        await this.handleTransferError(error)
        return false
      } finally {
        this.submitting = false
      }
    },
    async handleTransferError(error: unknown): Promise<void> {
      if (!(error instanceof ApiError)) {
        useUiStore().toast('保存失败，请稍后重试', 'error')
        return
      }
      if (error.code === 42206) {
        this.transferServerOver = capacityOverFromError(error)
        this.transferForm.capacityConfirmed = false
        this.transferErrors = {}
        this.transferRequestKey = ''
        useUiStore().toast('转入后超过栏舍容量，请确认后重新保存', 'error')
        return
      }
      if (error.code === 42205) {
        this.transferErrors.quantity = error.message
        await this.reloadTransferContext()
        return
      }
      if (error.isNetwork) {
        try {
          const check = await herdApi.checkRequest(this.transferRequestKey)
          if (check.exists) {
            this.transferOpen = false
            this.transferRequestKey = ''
            useUiStore().toast(check.result && typeof check.result === 'object' ? String((check.result as { message?: string }).message || '转栏已保存') : '转栏已保存')
            return
          }
        } catch {
          /* 查询失败按未保存处理 */
        }
      }
      if (!error.isNetwork) this.transferRequestKey = ''
      const fields = fieldErrorsOf(error)
      if (Object.keys(fields).length) {
        this.transferErrors = { ...fields }
        useUiStore().toast('请检查表单中的错误', 'error')
      } else {
        useUiStore().toast(error.message, 'error')
      }
    },
    async reloadTransferContext(): Promise<void> {
      const id = this.transferContext.batchId
      this.transferContext = emptyContext()
      if (id) await this.loadContext('transfer', id)
      const batch = this.transferContext.batch
      if (batch) {
        const row = batch.pens?.find((pen) => Number(pen.pen_id) === this.transferForm.fromPenId)
        this.transferForm.seenStockCount = Number(row?.stock_count || 0)
      }
    },
    validateDeath(): boolean {
      this.deathErrors = {}
      const form = this.deathForm
      if (!form.batchId) this.deathErrors.batch_id = '请选择批次'
      if (!form.penId) this.deathErrors.pen_id = '请选择栏舍'
      if (form.quantity === null || !Number.isInteger(Number(form.quantity)) || Number(form.quantity) <= 0) {
        this.deathErrors.quantity = '数量需为大于 0 的整数'
      } else if (Number(form.quantity) > this.deathStock) {
        const penName = this.deathContext.batch?.pens?.find((pen) => Number(pen.pen_id) === form.penId)?.pen_name || '栏舍'
        this.deathErrors.quantity = `不能超过 ${penName} 的在栏数 ${this.deathStock} ${this.deathUnit}`
      }
      if (!form.reasonCode) this.deathErrors.reason_code = '请选择死淘原因'
      if (form.reasonCode === 'OTHER' && !form.remark.trim()) this.deathErrors.remark = '选择“其他”时请写明原因'
      if (!form.eventDate) this.deathErrors.event_date = '请选择日期'
      else if (form.eventDate > todayISO()) this.deathErrors.event_date = '日期不能晚于今天'
      else if (this.deathContext.batch?.entry_date && form.eventDate < this.deathContext.batch.entry_date) {
        this.deathErrors.event_date = `日期不能早于入栏日期 ${monthDayText(this.deathContext.batch.entry_date)}`
      }
      return Object.keys(this.deathErrors).length === 0
    },
    async submitDeath(): Promise<boolean> {
      if (this.submitting || !this.validateDeath()) return false
      this.submitting = true
      if (!this.deathRequestKey) this.deathRequestKey = newRequestKey()
      try {
        const result = await herdApi.createDeath({
          batch_id: this.deathForm.batchId,
          pen_id: this.deathForm.penId,
          quantity: Number(this.deathForm.quantity),
          reason_code: this.deathForm.reasonCode,
          event_date: this.deathForm.eventDate,
          executor_user_id: this.deathForm.executorUserId || undefined,
          remark: this.deathForm.remark || undefined,
          file_ids: this.deathForm.fileIds,
          seen_stock_count: this.deathForm.seenStockCount
        }, this.deathRequestKey)
        this.deathResult = result
        this.deathOpen = false
        this.deathRequestKey = ''
        useUiStore().toast(result.message || '死淘已登记')
        await this.refreshBase(!this.formOptions)
        if (this.deathContext.batchId) {
          const id = this.deathContext.batchId
          this.deathContext = emptyContext()
          await this.loadContext('death', id)
        }
        return true
      } catch (error) {
        await this.handleDeathError(error)
        return false
      } finally {
        this.submitting = false
      }
    },
    async handleDeathError(error: unknown): Promise<void> {
      if (!(error instanceof ApiError)) {
        useUiStore().toast('保存失败，请稍后重试', 'error')
        return
      }
      if (error.code === 42205) {
        const quantityMessage = error.message
        const id = this.deathContext.batchId
        this.deathContext = emptyContext()
        if (id) await this.loadContext('death', id)
        this.deathErrors.quantity = quantityMessage
        return
      }
      if (error.isNetwork) {
        try {
          const check = await herdApi.checkRequest(this.deathRequestKey)
          if (check.exists) {
            this.deathOpen = false
            this.deathRequestKey = ''
            useUiStore().toast(check.result && typeof check.result === 'object' ? String((check.result as { message?: string }).message || '死淘已保存') : '死淘已保存')
            return
          }
        } catch {
          /* 查询失败按未保存处理 */
        }
      }
      if (!error.isNetwork) this.deathRequestKey = ''
      const fields = fieldErrorsOf(error)
      if (Object.keys(fields).length) {
        this.deathErrors = { ...fields }
        useUiStore().toast('请检查表单中的错误', 'error')
      } else {
        useUiStore().toast(error.message, 'error')
      }
    },
    resetEntry(): void {
      this.entryForm = emptyEntryForm()
      this.entryErrors = {}
      this.entryServerOver = null
      this.entryRequestKey = ''
    },
    resetTransfer(): void {
      this.transferForm = emptyTransferForm()
      this.transferErrors = {}
      this.transferServerOver = null
      this.transferRequestKey = ''
      this.transferResult = null
    },
    resetDeath(): void {
      this.deathForm = emptyDeathForm()
      this.deathErrors = {}
      this.deathRequestKey = ''
      this.deathResult = null
    },
    reset(): void {
      resetFarmCache(this)
    }
  }
})

function hasAtMostTwoDecimals(value: number | string): boolean {
  const text = String(value)
  if (!text.includes('.')) return true
  return text.split('.')[1].length <= 2
}

function capacityOverFromError(error: ApiError): CapacityOver {
  const data = (error.data || {}) as Record<string, unknown>
  return {
    penName: String(data.pen_name || '栏舍'),
    capacity: Number(data.capacity || 0),
    current: Number(data.current || 0),
    after: Number(data.after || 0),
    over: Number(data.over || 0)
  }
}

function monthDayText(value: string): string {
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getMonth() + 1}月${date.getDate()}日`
}
