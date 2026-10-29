import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import type { PenOption } from '@/api/herd'
import { herdApi } from '@/api/herd'
import type {
  BreedingAnimalOption,
  BreedingOverview,
  FarrowDueItem,
  HatchDueItem,
  MatingMethod,
  PendingCheck,
  PregnancyResult,
  RegisterContext
} from '@/api/breeding'
import { breedingApi } from '@/api/breeding'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { claimFarmScope, currentFarmScope, resetFarmCache } from '@/stores/farmScope'
import {
  addDays,
  diffDays,
  errorText,
  fieldErrorsOf,
  isFuture,
  monthDayWeek,
  statusForError,
  todayISO,
  type PageStatus
} from './shared'

export type PendingTab = 'due' | 'hatch'
export type PendingDrawer = '' | 'mating' | 'farrowing' | 'weaning'

interface CapacityInfo {
  penName: string
  capacity: number
  currentCount: number
  afterCount: number
  overCount: number
  unit: string
}

export const useBreedingPendingStore = defineStore('breedingPending', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    overview: null as BreedingOverview | null,
    tab: 'due' as PendingTab,
    query: { page: 1, size: 20, keyword: '' },
    dueList: [] as FarrowDueItem[],
    hatchList: [] as HatchDueItem[],
    dueTotal: 0,
    hatchTotal: 0,
    loaded: false,
    farmScope: '',
    requestId: 0,

    drawer: '' as PendingDrawer,
    drawerSubmitting: false,

    matingOptions: [] as BreedingAnimalOption[],
    farrowingOptions: [] as BreedingAnimalOption[],
    weaningOptions: [] as BreedingAnimalOption[],
    optionsLoading: false,
    pens: [] as PenOption[],

    matingForm: { animalId: 0, matingDate: todayISO(), matingMethod: 'ARTIFICIAL' as MatingMethod, boarInfo: '' },
    matingContext: null as RegisterContext | null,
    matingContextLoading: false,
    matingErrors: {} as Record<string, string>,
    matingSubmitting: false,

    pendingChecks: [] as PendingCheck[],
    pendingChecksLoading: false,
    activeCheckId: 0,
    checkForm: { checkDate: todayISO(), result: '' as PregnancyResult | '' },
    checkErrors: {} as Record<string, string>,
    checkSubmitting: false,

    farrowForm: {
      animalId: 0,
      farrowDate: todayISO(),
      totalBorn: null as number | null,
      liveBorn: null as number | null,
      stillborn: null as number | null,
      transferToBatch: true,
      targetPenId: 0,
      offspringBreedId: 0,
      capacityConfirmed: false
    },
    farrowContext: null as RegisterContext | null,
    farrowContextLoading: false,
    farrowPenOptions: [] as PenOption[],
    farrowContextRequestId: 0,
    farrowErrors: {} as Record<string, string>,
    farrowSubmitting: false,
    farrowCapacity: null as CapacityInfo | null,

    weanForm: {
      animalId: 0,
      weanDate: todayISO(),
      weanedCount: null as number | null,
      litterWeight: null as number | null
    },
    weanContext: null as RegisterContext | null,
    weanContextLoading: false,
    weanErrors: {} as Record<string, string>,
    weanSubmitting: false
  }),
  getters: {
    dueCount(): number {
      return Number(this.overview?.farrowDueCount || this.dueTotal || 0)
    },
    hatchCount(): number {
      return Number(this.overview?.hatchDueCount || this.hatchTotal || 0)
    },
    animalLabel(): string {
      return this.overview?.animalLabel || '母猪'
    },
    countUnit(): string {
      return this.overview?.countUnitText || '头'
    },
    switchFarms(): NonNullable<BreedingOverview['switchFarms']> {
      return this.overview?.switchFarms || []
    },
    crossFarmText(): string {
      const farm = useFarmStore()
      const target = this.switchFarms[0]
      if (!target) return ''
      return `${farm.farmName || '当前养殖场'}没有孵化记录。切到${target.farmName || '禽类养殖场'}可以查看待出雏，预计出雏日提前 1 天和当天各提醒一次。`
    },
    selectedMatingAnimal(state): BreedingAnimalOption | undefined {
      return state.matingOptions.find((item) => Number(item.animalId) === state.matingForm.animalId)
    },
    matingDueDate(state): string {
      if (!state.matingForm.animalId) return ''
      if (state.matingContext?.expectedDueDate) return state.matingContext.expectedDueDate
      const option = state.matingOptions.find((item) => Number(item.animalId) === state.matingForm.animalId)
      const base = option?.firstMatingDate || state.matingForm.matingDate
      const days = Number(state.matingContext?.gestationDays ?? option?.gestationDays ?? 0)
      return base && days ? addDays(base, days) : ''
    },
    matingGestationDays(state): number {
      const option = state.matingOptions.find((item) => Number(item.animalId) === state.matingForm.animalId)
      return Number(state.matingContext?.gestationDays ?? option?.gestationDays ?? 0)
    },
    matingRemating(state): boolean {
      const option = state.matingOptions.find((item) => Number(item.animalId) === state.matingForm.animalId)
      return option?.status === 'MATED'
    },
    matingMinDate(state): string {
      return state.matingContext?.minMatingDate || ''
    },
    selectedFarrowAnimal(state): BreedingAnimalOption | undefined {
      return state.farrowingOptions.find((item) => Number(item.animalId) === state.farrowForm.animalId)
    },
    selectedWeanAnimal(state): BreedingAnimalOption | undefined {
      return state.weaningOptions.find((item) => Number(item.animalId) === state.weanForm.animalId)
    },
    farrowSumValid(state): boolean {
      const total = Number(state.farrowForm.totalBorn ?? 0)
      const live = Number(state.farrowForm.liveBorn ?? 0)
      const dead = Number(state.farrowForm.stillborn ?? 0)
      return state.farrowForm.totalBorn !== null && state.farrowForm.liveBorn !== null && state.farrowForm.stillborn !== null
        && total === live + dead
    },
    farrowSumError(state): string {
      if (state.farrowForm.totalBorn === null || state.farrowForm.liveBorn === null || state.farrowForm.stillborn === null) return ''
      const total = Number(state.farrowForm.totalBorn || 0)
      const live = Number(state.farrowForm.liveBorn || 0)
      const dead = Number(state.farrowForm.stillborn || 0)
      if (total === live + dead) return ''
      return `活仔 ${live} 头 + 死胎 ${dead} 头 = ${live + dead} 头，与总产仔 ${total} 头不一致`
    },
    farrowPens(state) {
      return state.farrowPenOptions.filter(pen => !pen.disabled)
    },
    farrowCapacityInfo(state): CapacityInfo | null {
      if (state.farrowCapacity) return state.farrowCapacity
      if (!state.farrowForm.transferToBatch) return null
      const pen = state.farrowPenOptions.find((item) => Number(item.pen_id) === state.farrowForm.targetPenId)
      const live = Number(state.farrowForm.liveBorn ?? 0)
      if (!pen) return null
      const current = Number(pen.stock_count || 0)
      const capacity = Number(pen.capacity || 0)
      const after = current + live
      return {
        penName: pen.pen_name || '',
        capacity,
        currentCount: current,
        afterCount: after,
        overCount: Math.max(0, after - capacity),
        unit: pen.unit_text || '头'
      }
    },
    checkDueText(state): string {
      const checks = state.pendingChecks
      if (!checks.length) return ''
      return `待妊检 · ${checks.length} 头`
    }
  },
  actions: {
    /** 页面未挂载时也可能切场：使用缓存前先核对归属，不是当前养殖场就清空。 */
    prepareFarmScope(): string {
      return claimFarmScope(this)
    },
    async load(force = false): Promise<void> {
      this.prepareFarmScope()
      if (this.loaded && !force) return
      await this.loadOverview()
      await this.loadTab(this.tab)
    },
    async loadOverview(): Promise<void> {
      const scope = this.farmScope
      try {
        const overview = await breedingApi.overview()
        if (scope !== currentFarmScope()) return
        this.overview = overview
      } catch (error) {
        if (scope !== currentFarmScope()) return
        this.status = statusForError(error)
        this.error = errorText(error, '繁育概况加载失败')
      }
    },
    /** 不带参数时刷新当前页签（登记后、翻页、搜索都走这里）。 */
    async loadTab(tab?: PendingTab): Promise<void> {
      this.tab = tab || this.tab
      const current = this.tab
      const requestId = ++this.requestId
      const scope = this.farmScope
      if (!this.dueList.length && !this.hatchList.length) this.status = 'loading'
      this.error = ''
      try {
        if (current === 'due') {
          const data = await breedingApi.farrowDue({
            page: this.query.page,
            size: this.query.size,
            keyword: this.query.keyword.trim() || undefined
          })
          if (requestId !== this.requestId || scope !== currentFarmScope()) return
          this.dueList = data.list || []
          this.dueTotal = Number(data.total || 0)
          this.status = this.dueList.length ? 'ready' : this.query.keyword ? 'filtered_empty' : 'empty'
        } else {
          const data = await breedingApi.hatchDue({
            page: this.query.page,
            size: this.query.size,
            keyword: this.query.keyword.trim() || undefined
          })
          if (requestId !== this.requestId || scope !== currentFarmScope()) return
          this.hatchList = data.list || []
          this.hatchTotal = Number(data.total || 0)
          this.status = this.hatchList.length ? 'ready' : this.query.keyword ? 'filtered_empty' : 'empty'
        }
        this.loaded = true
      } catch (error) {
        if (requestId !== this.requestId || scope !== currentFarmScope()) return
        this.status = statusForError(error)
        this.error = errorText(error, '待产列表加载失败，请稍后重新加载')
      }
    },
    setTab(tab: PendingTab): void {
      if (this.tab === tab) return
      this.query.page = 1
      void this.loadTab(tab)
    },
    setPage(page: number): void {
      this.query.page = Math.max(1, page)
      void this.loadTab()
    },
    setSize(size: number): void {
      this.query.size = Math.max(1, size)
      this.query.page = 1
      void this.loadTab()
    },
    setKeyword(): void {
      this.query.page = 1
      void this.loadTab()
    },
    async refresh(): Promise<void> {
      await this.loadOverview()
      await this.loadTab()
    },
    /** 每次打开登记都重拉栏舍：栏舍改名、停用、在栏数变化后下拉要跟着变；失败时保留已有选项。 */
    async loadPenOptions(): Promise<void> {
      const scope = this.farmScope
      try {
        const pens = await herdApi.penOptions()
        if (scope === this.farmScope) this.pens = pens || []
      } catch {
        if (scope === this.farmScope && !this.pens.length) this.pens = []
      }
    },
    async loadOptions(purpose: 'MATING' | 'FARROWING' | 'WEANING'): Promise<void> {
      this.optionsLoading = true
      try {
        const data = await breedingApi.animalOptions({ purpose, size: 50 })
        const list = data.list || []
        if (purpose === 'MATING') this.matingOptions = list
        else if (purpose === 'FARROWING') this.farrowingOptions = list
        else this.weaningOptions = list
      } catch (error) {
        useUiStore().toast(errorText(error, '登记下拉加载失败'), 'error')
      } finally {
        this.optionsLoading = false
      }
    },
    async openMating(animalId?: number, checkAnimalId?: number): Promise<void> {
      this.drawer = 'mating'
      this.matingErrors = {}
      this.matingForm = { animalId: 0, matingDate: todayISO(), matingMethod: 'ARTIFICIAL', boarInfo: '' }
      this.matingContext = null
      this.activeCheckId = 0
      this.checkForm = { checkDate: todayISO(), result: '' }
      this.checkErrors = {}
      await this.loadOptions('MATING')
      if (checkAnimalId) {
        await this.focusCheck(checkAnimalId)
      } else if (animalId) {
        this.matingForm.animalId = animalId
        await this.loadMatingContext()
      }
      void this.loadPendingChecks()
    },
    async loadMatingContext(): Promise<void> {
      if (!this.matingForm.animalId) {
        this.matingContext = null
        return
      }
      this.matingContextLoading = true
      try {
        const data = await breedingApi.registerContext(this.matingForm.animalId, 'MATING')
        this.matingContext = data
      } catch {
        this.matingContext = null
      } finally {
        this.matingContextLoading = false
      }
    },
    async loadPendingChecks(): Promise<void> {
      this.pendingChecksLoading = true
      try {
        const data = await breedingApi.pendingChecks({ page: 1, size: 50 })
        this.pendingChecks = data.list || []
      } catch {
        this.pendingChecks = []
      } finally {
        this.pendingChecksLoading = false
      }
    },
    async focusCheck(animalId: number): Promise<void> {
      await this.loadPendingChecks()
      const check = this.pendingChecks.find((item) => Number(item.animalId) === animalId)
      if (check) this.startCheck(check)
    },
    startCheck(check: PendingCheck): void {
      this.activeCheckId = Number(check.animalId)
      this.checkForm = { checkDate: todayISO(), result: '' }
      this.checkErrors = {}
    },
    cancelCheck(): void {
      this.activeCheckId = 0
      this.checkErrors = {}
    },
    async submitCheck(check: PendingCheck): Promise<boolean> {
      if (this.checkSubmitting) return false
      this.checkErrors = {}
      if (!this.checkForm.checkDate) this.checkErrors.check_date = '请选择妊检日期'
      else if (isFuture(this.checkForm.checkDate)) this.checkErrors.check_date = '妊检日期不能晚于今天'
      else if (check.firstMatingDate && this.checkForm.checkDate < check.firstMatingDate) {
        this.checkErrors.check_date = `妊检日期不能早于配种日期 ${monthDayWeek(check.firstMatingDate)}`
      }
      if (!this.checkForm.result) this.checkErrors.result = '请选择妊检结果'
      else if (
        this.checkForm.result === 'POSITIVE' &&
        !this.checkErrors.check_date &&
        check.firstMatingDate &&
        diffDays(check.firstMatingDate, this.checkForm.checkDate) < 18
      ) {
        this.checkErrors.result = '配种后满 18 天才能登记妊检阳性'
      }
      if (Object.keys(this.checkErrors).length) return false
      this.checkSubmitting = true
      try {
        const result = await breedingApi.createPregnancyCheck({
          animal_id: Number(check.animalId),
          animal_version: Number(check.animalVersion),
          cycle_id: Number(check.cycleId),
          check_date: this.checkForm.checkDate,
          result: this.checkForm.result as PregnancyResult
        })
        this.pendingChecks = this.pendingChecks.filter((item) => item.animalId !== check.animalId)
        this.activeCheckId = 0
        useUiStore().toast(result.animalStatus === 'PREGNANT'
          ? `已登记妊检，${check.earTag} 转为妊娠`
          : `已登记妊检，${check.earTag} 回到空怀`)
        this.matingOptions = this.matingOptions.filter((item) => item.animalId !== check.animalId)
        await this.refresh()
        return true
      } catch (error) {
        if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast(`${check.earTag} 的状态已变化，请刷新后再登记`, 'error')
          await this.refresh()
        } else {
          useUiStore().toast(errorText(error, '登记妊检失败'), 'error')
        }
        return false
      } finally {
        this.checkSubmitting = false
      }
    },
    async submitMating(): Promise<boolean> {
      if (this.matingSubmitting) return false
      this.matingErrors = {}
      const animal = this.matingOptions.find((item) => Number(item.animalId) === this.matingForm.animalId)
      if (!animal) this.matingErrors.animal_id = '请选择母猪'
      if (!this.matingForm.matingDate) this.matingErrors.mating_date = '请选择配种日期'
      else if (isFuture(this.matingForm.matingDate)) this.matingErrors.mating_date = '配种日期不能晚于今天'
      else if (this.matingMinDate && this.matingForm.matingDate < this.matingMinDate) {
        this.matingErrors.mating_date = `配种日期不能早于上次断奶日期 ${monthDayWeek(this.matingMinDate)}`
      }
      if ([...this.matingForm.boarInfo].length > 20) this.matingErrors.boar_info = '最多 20 个字'
      if (Object.keys(this.matingErrors).length || !animal) return false
      this.matingSubmitting = true
      try {
        const result = await breedingApi.createMating({
          animal_id: Number(animal.animalId),
          animal_version: Number(animal.version),
          mating_date: this.matingForm.matingDate,
          mating_method: this.matingForm.matingMethod,
          boar_info: this.matingForm.boarInfo.trim() || undefined
        })
        this.drawer = ''
        useUiStore().toast(`已登记配种，预产期 ${monthDayWeek(result.expectedDueDate)}`)
        await this.refresh()
        return true
      } catch (error) {
        if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast(`${animal.earTag} 的状态已变化，请刷新后再登记`, 'error')
          await this.refresh()
        } else {
          useUiStore().toast(errorText(error, '登记配种失败'), 'error')
        }
        return false
      } finally {
        this.matingSubmitting = false
      }
    },
    async openFarrowing(animalId?: number): Promise<void> {
      this.drawer = 'farrowing'
      ++this.farrowContextRequestId
      this.farrowContext = null
      this.farrowPenOptions = []
      this.farrowContextLoading = false
      this.farrowErrors = {}
      this.farrowCapacity = null
      this.farrowForm = {
        animalId: 0,
        farrowDate: todayISO(),
        totalBorn: null,
        liveBorn: null,
        stillborn: null,
        transferToBatch: true,
        targetPenId: 0,
        offspringBreedId: 0,
        capacityConfirmed: false
      }
      await Promise.all([this.loadOptions('FARROWING'), this.loadPenOptions()])
      if (animalId) {
        this.farrowForm.animalId = animalId
        await this.loadFarrowContext()
      }
    },
    async loadFarrowContext(): Promise<void> {
      const requestId = ++this.farrowContextRequestId
      const animalId = this.farrowForm.animalId
      this.farrowContext = null
      this.farrowPenOptions = []
      this.farrowForm.targetPenId = 0
      this.farrowForm.offspringBreedId = 0
      this.farrowContextLoading = !!animalId
      if (!animalId) return
      try {
        const data = await breedingApi.registerContext(animalId, 'FARROWING')
        if (requestId !== this.farrowContextRequestId || animalId !== this.farrowForm.animalId || this.drawer !== 'farrowing') return
        const categoryId = data.offspringBreed?.categoryId
        const pens = categoryId ? await herdApi.penOptions({ categoryId }) : []
        if (requestId !== this.farrowContextRequestId || animalId !== this.farrowForm.animalId || this.drawer !== 'farrowing') return
        this.farrowPenOptions = pens
        this.farrowContext = data
        this.farrowForm.offspringBreedId = Number(data.offspringBreed?.breedId || 0)
        const allowed = this.farrowPens
        const defaultId = Number(data.defaultPen?.penId || 0)
        this.farrowForm.targetPenId = Number(allowed.find(p => Number(p.pen_id) === defaultId)?.pen_id || allowed[0]?.pen_id || 0)
      } catch (error) {
        if (requestId !== this.farrowContextRequestId) return
        this.farrowErrors.animal_id = errorText(error, '母猪登记信息加载失败，请重新选择')
      } finally {
        if (requestId === this.farrowContextRequestId) this.farrowContextLoading = false
      }
    },
    async submitFarrowing(): Promise<boolean> {
      if (this.farrowSubmitting || this.farrowContextLoading) return false
      this.farrowErrors = {}
      const animal = this.farrowingOptions.find((item) => Number(item.animalId) === this.farrowForm.animalId)
      if (!animal) this.farrowErrors.animal_id = '请选择母猪'
      if (!this.farrowForm.farrowDate) this.farrowErrors.farrow_date = '请选择产仔日期'
      else if (isFuture(this.farrowForm.farrowDate)) this.farrowErrors.farrow_date = '产仔日期不能晚于今天'
      else if (animal?.firstMatingDate && this.farrowForm.farrowDate < animal.firstMatingDate) {
        this.farrowErrors.farrow_date = `产仔日期不能早于配种日期 ${monthDayWeek(animal.firstMatingDate)}`
      }
      const total = Number(this.farrowForm.totalBorn ?? 0)
      const live = Number(this.farrowForm.liveBorn ?? 0)
      const dead = Number(this.farrowForm.stillborn ?? 0)
      if (this.farrowForm.totalBorn === null || !Number.isInteger(total) || total < 1) this.farrowErrors.total_born = '总产仔数至少为 1 头'
      if (this.farrowForm.liveBorn === null || !Number.isInteger(live) || live < 0) this.farrowErrors.live_born = '请填写活仔数'
      if (this.farrowForm.stillborn === null || !Number.isInteger(dead) || dead < 0) this.farrowErrors.stillborn = '请填写死胎数'
      if (this.farrowForm.liveBorn !== null && this.farrowForm.totalBorn !== null && live > total) {
        this.farrowErrors.live_born = `活仔数不能多于总产仔 ${total} 头`
      }
      if (!this.farrowErrors.total_born && !this.farrowErrors.live_born && !this.farrowErrors.stillborn
        && this.farrowForm.totalBorn !== null && this.farrowForm.liveBorn !== null && this.farrowForm.stillborn !== null
        && total !== live + dead) {
        this.farrowErrors.live_born = '活仔数与死胎数之和不等于总产仔数'
        this.farrowErrors.stillborn = '活仔数与死胎数之和不等于总产仔数'
      }
      if (this.farrowForm.transferToBatch && live === 0) {
        this.farrowErrors.transfer_to_batch = '活仔数为 0，不能转为新批次。请关闭“活仔一键转为新批次”。'
      }
      if (this.farrowForm.transferToBatch && !this.farrowPens.some(p => Number(p.pen_id) === this.farrowForm.targetPenId)) {
        this.farrowErrors.target_pen_id = '请选择与后代品类匹配的入栏栏舍'
      }
      if (this.farrowForm.transferToBatch && !this.farrowForm.offspringBreedId) {
        this.farrowErrors.offspring_breed_id = '后代品种未加载，请重新选择母猪'
      }
      const capacity = this.farrowCapacityInfo
      if (capacity && capacity.overCount > 0 && !this.farrowForm.capacityConfirmed) {
        this.farrowErrors.capacity = '请先确认超出容量'
      }
      if (Object.keys(this.farrowErrors).length || !animal) return false
      this.farrowSubmitting = true
      try {
        const result = await breedingApi.createFarrowing({
          animal_id: Number(animal.animalId),
          animal_version: Number(animal.version),
          farrow_date: this.farrowForm.farrowDate,
          total_born: total,
          live_born: live,
          stillborn: dead,
          transfer_to_batch: this.farrowForm.transferToBatch,
          target_pen_id: this.farrowForm.transferToBatch ? this.farrowForm.targetPenId : undefined,
          offspring_breed_id: this.farrowForm.transferToBatch ? this.farrowForm.offspringBreedId || undefined : undefined,
          capacity_confirmed: this.farrowForm.capacityConfirmed || undefined
        })
        this.drawer = ''
        useUiStore().toast(result.batchNo
          ? `已登记产仔，新批次 ${result.batchNo} 已入栏`
          : `已登记产仔，${animal.earTag} 转为${result.animalStatus === 'EMPTY' ? '空怀' : '哺乳'}`)
        await this.refresh()
        return true
      } catch (error) {
        if (error instanceof ApiError && error.code === 42206) {
          const data = error.data as {
            pen_name?: string; unit?: string; capacity?: number; current_count?: number; after_count?: number; over_count?: number
          } | undefined
          if (data) {
            this.farrowCapacity = {
              penName: data.pen_name || '',
              capacity: Number(data.capacity || 0),
              currentCount: Number(data.current_count || 0),
              afterCount: Number(data.after_count || 0),
              overCount: Number(data.over_count || 0),
              unit: data.unit || '头'
            }
            this.farrowErrors.capacity = '入栏后超过栏舍容量，请确认后再提交'
          } else {
            useUiStore().toast(error.message, 'error')
          }
        } else if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast(`${animal.earTag} 的状态已变化，请刷新后再登记`, 'error')
          await this.refresh()
        } else {
          useUiStore().toast(errorText(error, '登记产仔失败'), 'error')
        }
        return false
      } finally {
        this.farrowSubmitting = false
      }
    },
    async openWeaning(animalId?: number): Promise<void> {
      this.drawer = 'weaning'
      this.weanErrors = {}
      this.weanForm = { animalId: 0, weanDate: todayISO(), weanedCount: null, litterWeight: null }
      await this.loadOptions('WEANING')
      if (animalId) {
        this.weanForm.animalId = animalId
        await this.loadWeanContext()
      }
    },
    async loadWeanContext(): Promise<void> {
      if (!this.weanForm.animalId) {
        this.weanContext = null
        return
      }
      this.weanContextLoading = true
      try {
        const data = await breedingApi.registerContext(this.weanForm.animalId, 'WEANING')
        this.weanContext = data
      } catch {
        this.weanContext = null
      } finally {
        this.weanContextLoading = false
      }
    },
    async submitWeaning(): Promise<boolean> {
      if (this.weanSubmitting) return false
      this.weanErrors = {}
      const animal = this.weaningOptions.find((item) => Number(item.animalId) === this.weanForm.animalId)
      if (!animal) this.weanErrors.animal_id = '请选择母猪'
      const farrowDate = this.weanContext?.farrowDate || animal?.lastFarrowDate || ''
      if (!this.weanForm.weanDate) this.weanErrors.wean_date = '请选择断奶日期'
      else if (isFuture(this.weanForm.weanDate)) this.weanErrors.wean_date = '断奶日期不能晚于今天'
      else if (farrowDate && this.weanForm.weanDate < farrowDate) {
        this.weanErrors.wean_date = `断奶日期不能早于产仔日期 ${monthDayWeek(farrowDate)}`
      }
      const max = Number(this.weanContext?.liveBorn ?? animal?.liveBorn ?? 0)
      const count = Number(this.weanForm.weanedCount ?? 0)
      if (this.weanForm.weanedCount === null || count < 1) this.weanErrors.weaned_count = '请填写断奶仔猪数'
      else if (max && count > max) this.weanErrors.weaned_count = `不能多于产仔时的活仔 ${max} 头`
      if (this.weanForm.litterWeight !== null && this.weanForm.litterWeight !== undefined) {
        const weight = Number(this.weanForm.litterWeight)
        if (!Number.isFinite(weight) || weight <= 0) this.weanErrors.litter_weight = '请输入大于 0 的重量'
        else if (Math.round(weight * 10) !== weight * 10) this.weanErrors.litter_weight = '最多 1 位小数'
      }
      if (Object.keys(this.weanErrors).length || !animal) return false
      this.weanSubmitting = true
      try {
        await breedingApi.createWeaning({
          animal_id: Number(animal.animalId),
          animal_version: Number(animal.version),
          wean_date: this.weanForm.weanDate,
          weaned_count: count,
          litter_weight: this.weanForm.litterWeight === null ? undefined : Number(this.weanForm.litterWeight)
        })
        this.drawer = ''
        useUiStore().toast(`已登记断奶，${animal.earTag} 回到空怀`)
        await this.refresh()
        return true
      } catch (error) {
        if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast(`${animal.earTag} 的状态已变化，请刷新后再登记`, 'error')
          await this.refresh()
        } else {
          useUiStore().toast(errorText(error, '登记断奶失败'), 'error')
        }
        return false
      } finally {
        this.weanSubmitting = false
      }
    },
    closeDrawer(): void {
      this.drawer = ''
      this.activeCheckId = 0
    },
    reset(): void {
      resetFarmCache(this)
    }
  }
})
