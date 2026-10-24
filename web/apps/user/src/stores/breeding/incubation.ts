import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import type { BaseCategory, PenOption, RecordLog } from '@/api/herd'
import { herdApi } from '@/api/herd'
import type {
  HatchDueItem,
  IncubationDetail,
  PoultryBatchOption
} from '@/api/breeding'
import { breedingApi } from '@/api/breeding'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import {
  addDays,
  errorText,
  fieldErrorsOf,
  isFuture,
  monthDayWeek,
  statusForError,
  todayISO,
  type PageStatus
} from './shared'

interface CapacityInfo {
  penName: string
  capacity: number
  currentCount: number
  afterCount: number
  overCount: number
  unit: string
}

export const useIncubationStore = defineStore('breedingIncubation', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    dueList: [] as HatchDueItem[],
    dueTotal: 0,
    selectedId: 0,
    detail: null as IncubationDetail | null,
    detailLoading: false,
    detailError: '',
    requestId: 0,

    formDrawer: false,
    formMode: 'create' as 'create' | 'edit',
    form: {
      breedingBatchId: 0,
      setDate: todayISO(),
      setCount: null as number | null,
      candleDate: '',
      fertileCount: null as number | null
    },
    formErrors: {} as Record<string, string>,
    submitting: false,
    optionsLoading: false,
    batchOptions: [] as PoultryBatchOption[],
    categories: [] as BaseCategory[],
    pens: [] as PenOption[],

    candleForm: { candleDate: todayISO(), fertileCount: null as number | null },
    candleErrors: {} as Record<string, string>,
    candleSubmitting: false,

    hatchForm: {
      hatchDate: todayISO(),
      hatchedCount: null as number | null,
      healthyCount: null as number | null,
      transferToBatch: true,
      targetPenId: 0,
      capacityConfirmed: false
    },
    hatchErrors: {} as Record<string, string>,
    hatchSubmitting: false,
    hatchCapacity: null as CapacityInfo | null,

    logsDrawer: false,
    logsTitle: '',
    logs: [] as RecordLog[],
    logsLoading: false
  }),
  getters: {
    farmName(): string {
      return useFarmStore().farmName || ''
    },
    currentStep(state): number {
      return Number(state.detail?.currentStep ?? 1)
    },
    isHatched(state): boolean {
      return state.detail?.status === 'HATCHED' || state.detail?.currentStep === 0
    },
    incubationId(state): number {
      return Number(state.detail?.incubationId || state.selectedId || 0)
    },
    flockText(state): string {
      return state.detail?.flockText || ''
    },
    titleDesc(state): string {
      const detail = state.detail
      if (!detail) return farmDesc()
      return [detail.flockText, detail.setDate ? `${monthDayWeek(detail.setDate)}入孵` : '', detail.breedName,
        detail.incubationDays ? `孵化期 ${detail.incubationDays} 天` : ''].filter(Boolean).join(' · ')
    },
    statusTag(state): { text: string; type: string; icon: string } {
      if (!state.detail) return { text: '', type: 'neutral', icon: '' }
      if (this.isHatched) return { text: '已出雏', type: 'neutral', icon: 'check' }
      const days = Number(state.detail.daysToHatch || 0)
      if (days === 0) return { text: '今天出雏', type: 'warning', icon: 'egg' }
      if (days < 0) return { text: `已超期 ${Math.abs(days)} 天`, type: 'warning', icon: 'egg' }
      return { text: `${days} 天后出雏`, type: 'primary', icon: 'egg' }
    },
    steps(state): Array<{ title: string; desc: string; status: string }> {
      const detail = state.detail
      if (!detail) return []
      const hatched = this.isHatched
      const candled = detail.fertileCount !== null && detail.fertileCount !== undefined
      return [
        {
          title: '入孵',
          desc: detail.setDate ? `${monthDayWeek(detail.setDate)} · ${Number(detail.setCount || 0).toLocaleString('en-US')} 枚` : '',
          status: 'done'
        },
        {
          title: '照蛋',
          desc: candled
            ? `${monthDayWeek(detail.candleDate)} · 受精蛋 ${Number(detail.fertileCount || 0).toLocaleString('en-US')} 枚`
            : (this.currentStep === 2 ? '待登记' : '未到'),
          status: candled ? 'done' : (this.currentStep === 2 ? 'current' : 'wait')
        },
        {
          title: '出雏',
          desc: hatched
            ? `${monthDayWeek(detail.hatchDate)} · 健雏 ${Number(detail.healthyCount || 0).toLocaleString('en-US')} 只`
            : (this.currentStep === 3 ? `预计${monthDayWeek(detail.expectedHatchDate)} · 待登记` : `预计${monthDayWeek(detail.expectedHatchDate)}`),
          status: hatched ? 'done' : (this.currentStep === 3 ? 'current' : 'wait')
        }
      ]
    },
    fertilityRateText(state): string {
      const value = state.detail?.fertilityRate
      return value === null || value === undefined ? '—' : `${(Number(value) * 100).toFixed(1)}%`
    },
    hatchRateText(state): string {
      const value = state.detail?.hatchRate
      return value === null || value === undefined ? '—' : `${(Number(value) * 100).toFixed(1)}%`
    },
    setCountText(state): string {
      return Number(state.detail?.setCount || 0).toLocaleString('en-US')
    },
    fertileCountText(state): string {
      const value = state.detail?.fertileCount
      return value === null || value === undefined ? '—' : Number(value).toLocaleString('en-US')
    },
    hatchSummaryText(state): string {
      if (!this.isHatched) return '今天'
      return `${Number(state.detail?.hatchedCount || 0).toLocaleString('en-US')} 只`
    },
    timelineItems(state): Array<{ title: string; time: string; desc: string; tone?: string; icon?: string }> {
      return (state.detail?.timeline || []).map((item) => ({
        title: item.stepText || '登记',
        time: item.registeredAt || '',
        desc: item.registrant?.name ? `${item.registrant.name} 登记` : '',
        tone: item.step === 'HATCH' ? 'success' : 'primary',
        icon: 'check'
      }))
    },
    logItems(state): Array<{ title: string; time: string; desc: string; tone?: string; icon?: string }> {
      return state.logs.map((log) => ({
        title: log.changes?.length
          ? log.changes.map((change) => `${change.field_name || '字段'}：${change.before || '—'} → ${change.after || '—'}`).join('；')
          : log.op_action_text || '修改记录',
        time: log.created_at || '',
        desc: log.operator?.name || '',
        tone: 'primary',
        icon: 'edit'
      }))
    },
    selectedBatch(state): PoultryBatchOption | undefined {
      return state.batchOptions.find((item) => Number(item.batch_id) === state.form.breedingBatchId)
    },
    predictedHatchDate(state): string {
      const batch = state.batchOptions.find((item) => Number(item.batch_id) === state.form.breedingBatchId)
      const breedId = Number(batch?.breed_id || 0)
      const category = state.categories.find((item) => (item.breeds || []).some((breed) => Number(breed.id) === breedId))
      const breed = category?.breeds?.find((item) => Number(item.id) === breedId)
      const days = Number(breed?.incubation_days || 0)
      if (!state.form.setDate || !days) return ''
      return addDays(state.form.setDate, days)
    },
    predictedHatchDays(state): number {
      const batch = state.batchOptions.find((item) => Number(item.batch_id) === state.form.breedingBatchId)
      const breedId = Number(batch?.breed_id || 0)
      const category = state.categories.find((item) => (item.breeds || []).some((breed) => Number(breed.id) === breedId))
      return Number(category?.breeds?.find((item) => Number(item.id) === breedId)?.incubation_days || 0)
    },
    weakCount(state): number | null {
      const hatched = state.hatchForm.hatchedCount
      const healthy = state.hatchForm.healthyCount
      if (hatched === null || healthy === null) return null
      return Math.max(0, Number(hatched) - Number(healthy))
    },
    hatchRatePreview(state): number | null {
      const setCount = Number(state.detail?.setCount || 0)
      if (!setCount || state.hatchForm.hatchedCount === null) return null
      return Number(state.hatchForm.hatchedCount) / setCount
    },
    hatchCapacityInfo(state): CapacityInfo | null {
      if (state.hatchCapacity) return state.hatchCapacity
      if (!state.hatchForm.transferToBatch) return null
      const pen = state.pens.find((item) => Number(item.pen_id) === state.hatchForm.targetPenId)
      if (!pen) return null
      const current = Number(pen.stock_count || 0)
      const capacity = Number(pen.capacity || 0)
      const after = current + Number(state.hatchForm.healthyCount || 0)
      return {
        penName: pen.pen_name || '',
        capacity,
        currentCount: current,
        afterCount: after,
        overCount: Math.max(0, after - capacity),
        unit: pen.unit_text || '只'
      }
    },
    penItems(state): Array<{ title: string; value: number; hint: string }> {
      return state.pens.filter((pen) => !pen.disabled).map((pen) => ({
        title: pen.pen_name || '栏舍',
        value: Number(pen.pen_id || 0),
        hint: `在栏 ${Number(pen.stock_count || 0).toLocaleString('en-US')} · 容量 ${Number(pen.capacity || 0).toLocaleString('en-US')}`
      }))
    },
    batchItems(state): Array<{ title: string; value: number; hint: string }> {
      return state.batchOptions.map((batch) => ({
        title: `${batch.pens?.[0]?.pen_name || ''} · ${batch.batch_no || ''}`,
        value: Number(batch.batch_id || 0),
        hint: `${batch.breed_name || ''} · 在栏 ${Number(batch.current_count || 0).toLocaleString('en-US')} 只`
      }))
    }
  },
  actions: {
    async load(force = false): Promise<void> {
      const farm = useFarmStore()
      if (farm.role === 'staff') {
        this.status = 'forbidden'
        this.error = '你没有登记繁育记录的权限'
        return
      }
      this.status = 'loading'
      this.error = ''
      const requestId = ++this.requestId
      try {
        await this.loadOptions()
        const data = await breedingApi.hatchDue({ page: 1, size: 50 })
        if (requestId !== this.requestId) return
        this.dueList = data.list || []
        this.dueTotal = Number(data.total || 0)
        const fromQuery = Number(this.selectedId || 0)
        if (fromQuery && this.detail && Number(this.detail.incubationId) === fromQuery) {
          this.status = 'ready'
          return
        }
        if (fromQuery && !this.dueList.some((item) => Number(item.incubationId) === fromQuery)) {
          await this.selectIncubation(fromQuery)
          return
        }
        const target = this.dueList.find((item) => Number(item.incubationId) === fromQuery)
          || this.dueList.find((item) => Number(item.daysToHatch) === 0)
          || this.dueList[0]
        if (target?.incubationId) {
          await this.selectIncubation(Number(target.incubationId))
        } else {
          this.detail = null
          this.status = 'empty'
        }
      } catch (error) {
        if (requestId !== this.requestId) return
        this.status = statusForError(error)
        this.error = errorText(error, '孵化记录加载失败')
      }
    },
    async selectIncubation(incubationId: number): Promise<void> {
      this.selectedId = incubationId
      this.detailLoading = true
      this.detailError = ''
      try {
        const detail = await breedingApi.incubationDetail(incubationId)
        this.detail = detail
        this.resetForms()
        this.status = 'ready'
      } catch (error) {
        this.detail = null
        this.detailError = errorText(error, '孵化详情加载失败')
        this.status = statusForError(error)
        this.error = this.detailError
      } finally {
        this.detailLoading = false
      }
    },
    resetForms(): void {
      const detail = this.detail
      this.candleForm = {
        candleDate: todayISO(),
        fertileCount: detail?.fertileCount ?? null
      }
      this.candleErrors = {}
      this.hatchForm = {
        hatchDate: todayISO(),
        hatchedCount: detail?.hatchedCount ?? null,
        healthyCount: detail?.healthyCount ?? null,
        transferToBatch: detail?.transferToBatch ?? true,
        targetPenId: Number(detail?.targetPenId || 0),
        capacityConfirmed: false
      }
      this.hatchErrors = {}
      this.hatchCapacity = null
    },
    async loadOptions(): Promise<void> {
      if (this.optionsLoading) return
      this.optionsLoading = true
      try {
        const [batches, categories, pens] = await Promise.all([
          breedingApi.poultryBreedingBatches(),
          herdApi.baseCategories(),
          herdApi.penOptions()
        ])
        this.batchOptions = batches.list || []
        this.categories = categories || []
        this.pens = pens || []
      } catch (error) {
        useUiStore().toast(errorText(error, '孵化表单选项加载失败'), 'error')
      } finally {
        this.optionsLoading = false
      }
    },
    async openCreate(): Promise<void> {
      await this.loadOptions()
      this.formMode = 'create'
      this.form = {
        breedingBatchId: Number(this.batchOptions[0]?.batch_id || 0),
        setDate: todayISO(),
        setCount: null,
        candleDate: '',
        fertileCount: null
      }
      this.formErrors = {}
      this.formDrawer = true
    },
    async openEdit(): Promise<void> {
      if (!this.detail) return
      await this.loadOptions()
      this.formMode = 'edit'
      this.form = {
        breedingBatchId: Number(this.detail.breedingBatchId || 0),
        setDate: this.detail.setDate || todayISO(),
        setCount: this.detail.setCount ?? null,
        candleDate: this.detail.candleDate || '',
        fertileCount: this.detail.fertileCount ?? null
      }
      this.formErrors = {}
      this.formDrawer = true
    },
    validateForm(): boolean {
      this.formErrors = {}
      if (!this.form.breedingBatchId) this.formErrors.breeding_batch_id = '请选择种禽群'
      if (!this.form.setDate) this.formErrors.set_date = '请选择入孵日期'
      else if (isFuture(this.form.setDate)) this.formErrors.set_date = '入孵日期不能晚于今天'
      const count = Number(this.form.setCount ?? 0)
      if (this.form.setCount === null || !Number.isInteger(count) || count < 1) this.formErrors.set_count = '入孵蛋数需为大于 0 的整数'
      if (this.formMode === 'edit') {
        if (this.form.candleDate && this.form.candleDate < this.form.setDate) {
          this.formErrors.candle_date = '照蛋日期不能早于入孵日期'
        }
        const fertile = this.form.fertileCount
        if (fertile !== null && (Number(fertile) < 0 || Number(fertile) > count)) {
          this.formErrors.fertile_count = `不能多于入孵蛋数 ${count.toLocaleString('en-US')} 枚`
        }
      }
      return Object.keys(this.formErrors).length === 0
    },
    async submitForm(): Promise<boolean> {
      if (this.submitting || !this.validateForm()) return false
      this.submitting = true
      try {
        if (this.formMode === 'create') {
          const result = await breedingApi.createIncubation({
            breeding_batch_id: this.form.breedingBatchId,
            set_date: this.form.setDate,
            set_count: Number(this.form.setCount)
          })
          this.formDrawer = false
          useUiStore().toast(`已登记入孵，预计 ${monthDayWeek(result.expectedHatchDate)} 出雏`)
          await this.load(true)
          if (result.incubationId) await this.selectIncubation(Number(result.incubationId))
        } else if (this.detail) {
          await breedingApi.updateIncubation(Number(this.detail.incubationId), {
            set_date: this.form.setDate,
            set_count: Number(this.form.setCount),
            candle_date: this.form.candleDate || undefined,
            fertile_count: this.form.fertileCount === null ? undefined : Number(this.form.fertileCount),
            version: Number(this.detail.version || 0)
          })
          this.formDrawer = false
          useUiStore().toast('已保存孵化记录')
          await this.selectIncubation(Number(this.detail.incubationId))
          await this.load(true)
        }
        return true
      } catch (error) {
        if (error instanceof ApiError) {
          const fields = fieldErrorsOf(error)
          if (Object.keys(fields).length) this.formErrors = { ...fields }
          else if (error.code === 40902 || error.code === 40903) {
            useUiStore().toast('记录已变化，请刷新后再修改', 'error')
            await this.selectIncubation(this.incubationId)
          } else {
            useUiStore().toast(error.message || '保存失败', 'error')
          }
        } else {
          useUiStore().toast('保存失败，请稍后重试', 'error')
        }
        return false
      } finally {
        this.submitting = false
      }
    },
    async submitCandling(): Promise<boolean> {
      if (this.candleSubmitting || !this.detail) return false
      this.candleErrors = {}
      if (!this.candleForm.candleDate) this.candleErrors.candle_date = '请选择照蛋日期'
      else if (isFuture(this.candleForm.candleDate)) this.candleErrors.candle_date = '照蛋日期不能晚于今天'
      else if (this.detail.setDate && this.candleForm.candleDate < this.detail.setDate) this.candleErrors.candle_date = '照蛋日期不能早于入孵日期'
      const setCount = Number(this.detail.setCount || 0)
      const fertile = Number(this.candleForm.fertileCount ?? 0)
      if (this.candleForm.fertileCount === null || !Number.isInteger(fertile) || fertile < 1) {
        this.candleErrors.fertile_count = '请填写受精蛋数'
      } else if (fertile > setCount) {
        this.candleErrors.fertile_count = `不能多于入孵蛋数 ${setCount.toLocaleString('en-US')} 枚`
      }
      if (Object.keys(this.candleErrors).length) return false
      this.candleSubmitting = true
      try {
        const detail = await breedingApi.candling(Number(this.detail.incubationId), {
          candle_date: this.candleForm.candleDate,
          fertile_count: fertile,
          version: Number(this.detail.version || 0)
        })
        this.detail = detail
        this.resetForms()
        useUiStore().toast(`已登记照蛋，受精率 ${((Number(detail.fertilityRate || 0)) * 100).toFixed(1)}%`)
        await this.load(true)
        return true
      } catch (error) {
        if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast('孵化记录已变化，请刷新后再登记', 'error')
          await this.selectIncubation(this.incubationId)
        } else {
          useUiStore().toast(errorText(error, '登记照蛋失败'), 'error')
        }
        return false
      } finally {
        this.candleSubmitting = false
      }
    },
    async submitHatch(): Promise<boolean> {
      if (this.hatchSubmitting || !this.detail) return false
      this.hatchErrors = {}
      const fertile = Number(this.detail.fertileCount || 0)
      if (!this.hatchForm.hatchDate) this.hatchErrors.hatch_date = '请选择出雏日期'
      else if (isFuture(this.hatchForm.hatchDate)) this.hatchErrors.hatch_date = '出雏日期不能晚于今天'
      else if (this.detail.setDate && this.hatchForm.hatchDate < this.detail.setDate) this.hatchErrors.hatch_date = '出雏日期不能早于入孵日期'
      const hatched = Number(this.hatchForm.hatchedCount ?? 0)
      if (this.hatchForm.hatchedCount === null || !Number.isInteger(hatched) || hatched < 1) {
        this.hatchErrors.hatched_count = '请填写出雏数'
      } else if (fertile && hatched > fertile) {
        this.hatchErrors.hatched_count = `不能多于受精蛋数 ${fertile.toLocaleString('en-US')} 枚`
      }
      const healthy = Number(this.hatchForm.healthyCount ?? 0)
      if (this.hatchForm.healthyCount === null || !Number.isInteger(healthy) || healthy < 0) {
        this.hatchErrors.healthy_count = '请填写健雏数'
      } else if (hatched && healthy > hatched) {
        this.hatchErrors.healthy_count = `健雏数不能多于出雏数 ${hatched.toLocaleString('en-US')} 只`
      }
      if (this.hatchForm.transferToBatch && healthy === 0) {
        this.hatchErrors.transfer_to_batch = '健雏数为 0，不能转为新批次'
      }
      if (this.hatchForm.transferToBatch && !this.hatchForm.targetPenId) {
        this.hatchErrors.target_pen_id = '请选择入栏鸡舍'
      }
      const capacity = this.hatchCapacityInfo
      if (capacity && capacity.overCount > 0 && !this.hatchForm.capacityConfirmed) {
        this.hatchErrors.capacity = '请先确认超出容量'
      }
      if (Object.keys(this.hatchErrors).length) return false
      this.hatchSubmitting = true
      try {
        const result = await breedingApi.hatch(Number(this.detail.incubationId), {
          hatch_date: this.hatchForm.hatchDate,
          hatched_count: hatched,
          healthy_count: healthy,
          transfer_to_batch: this.hatchForm.transferToBatch,
          target_pen_id: this.hatchForm.transferToBatch ? this.hatchForm.targetPenId : undefined,
          capacity_confirmed: this.hatchForm.capacityConfirmed || undefined,
          version: Number(this.detail.version || 0)
        })
        useUiStore().toast(result.batchNo ? '已登记出雏，新批次已入栏' : '已登记出雏')
        await this.selectIncubation(Number(this.detail.incubationId))
        await this.load(true)
        return true
      } catch (error) {
        if (error instanceof ApiError && error.code === 42206) {
          const data = error.data as {
            pen_name?: string; unit?: string; capacity?: number; current_count?: number; after_count?: number; over_count?: number
          } | undefined
          if (data) {
            this.hatchCapacity = {
              penName: data.pen_name || '',
              capacity: Number(data.capacity || 0),
              currentCount: Number(data.current_count || 0),
              afterCount: Number(data.after_count || 0),
              overCount: Number(data.over_count || 0),
              unit: data.unit || '只'
            }
            this.hatchErrors.capacity = '入栏后超过栏舍容量，请确认后再提交'
          } else {
            useUiStore().toast(error.message, 'error')
          }
        } else if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast('孵化记录已变化，请刷新后再登记', 'error')
          await this.selectIncubation(this.incubationId)
        } else {
          useUiStore().toast(errorText(error, '登记出雏失败'), 'error')
        }
        return false
      } finally {
        this.hatchSubmitting = false
      }
    },
    async openLogs(): Promise<void> {
      if (!this.detail) return
      this.logsDrawer = true
      this.logsTitle = `${this.detail.flockText || '孵化记录'} 修改记录`
      this.logsLoading = true
      try {
        this.logs = await herdApi.recordLogs('INCUBATION', Number(this.detail.incubationId))
      } catch {
        this.logs = []
      } finally {
        this.logsLoading = false
      }
    },
    reset(): void {
      this.$reset()
    }
  }
})

function farmDesc(): string {
  return '登记入孵、照蛋和出雏后，这里会显示孵化进度'
}
