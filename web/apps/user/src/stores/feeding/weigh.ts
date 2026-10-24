import { defineStore } from 'pinia'
import { ApiError, dayjs } from '@zmt/shared'
import type { RecordLog } from '@/api/herd'
import { herdApi } from '@/api/herd'
import type {
  GrowthCurve,
  WeighBatchOption,
  WeighCompareOption,
  WeighCycle,
  WeighForm,
  WeighRecordItem,
  WeighRecordRow
} from '@/api/feeding'
import { feedingApi } from '@/api/feeding'
import { useUiStore } from '@/stores/ui'
import {
  dayAgeAt,
  errorText,
  fieldErrorsOf,
  statusForError,
  todayISO,
  type PageStatus
} from './shared'

export type WeighMode = 'SAMPLE' | 'EACH'

interface WeighRowForm {
  weight: number | null
}

export const useWeighStore = defineStore('feedingWeigh', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    batchOptions: [] as WeighBatchOption[],
    batchOptionsLoading: false,
    batchId: 0,
    curve: null as GrowthCurve | null,
    curveLoading: false,
    curveError: '',
    records: [] as WeighRecordRow[],
    recordsTotal: 0,
    query: { page: 1, size: 20 },
    recordsLoading: false,
    compareOptions: [] as WeighCompareOption[],
    compareIds: [] as number[],
    compareMenu: false,
    compareLoading: false,
    loaded: false,

    drawer: false,
    drawerMode: 'create' as 'create' | 'edit',
    editingRecordId: 0,
    editingVersion: 0,
    formLoading: false,
    formError: '',
    form: null as WeighForm | null,
    formState: {
      mode: 'SAMPLE' as WeighMode,
      weighDate: todayISO(),
      sampleCount: null as number | null,
      totalWeight: null as number | null,
      items: [{ weight: null }] as WeighRowForm[]
    },
    errors: {} as Record<string, string>,
    submitting: false,

    cycleEditing: false,
    cycleInput: 2,
    cycleSaving: false,
    cycleError: '',

    logsDrawer: false,
    logsTitle: '',
    logs: [] as RecordLog[],
    logsLoading: false
  }),
  getters: {
    selectedBatch(state): WeighBatchOption | undefined {
      return state.batchOptions.find((item) => Number(item.batch_id) === state.batchId)
    },
    batchItems(state): Array<{ title: string; value: number; hint: string }> {
      return state.batchOptions.map((batch) => ({
        title: `${batch.batch_no || ''} · ${batch.pen_names || ''}`,
        value: Number(batch.batch_id || 0),
        hint: [batch.breed_name, batch.day_age ? `${batch.day_age} 日龄` : '', batch.growth_status_text]
          .filter(Boolean).join(' · ')
      }))
    },
    growthTag(): { text: string; type: string; icon: string } | null {
      const status = this.curve?.growth_status
      if (status === 'SLOW') return { text: '生长偏慢', type: 'warning', icon: 'trending_down' }
      if (status === 'FAST') return { text: '生长偏快', type: 'warning', icon: 'trending_up' }
      if (this.curve?.batch?.status && this.curve.batch.status !== 'ACTIVE') return { text: '已结束', type: 'neutral', icon: '' }
      return null
    },
    desc(): string {
      const batch = this.curve?.batch || this.form?.batch
      if (!batch) return '选择批次后查看称重记录与生长曲线'
      const entry = batch.entry_avg_weight
      return [
        batch.pen_names,
        batch.breed_name,
        batch.day_age !== undefined && batch.day_age !== null ? `${batch.day_age} 日龄` : '',
        batch.current_count ? `存栏 ${batch.current_count} 头` : '',
        batch.entry_date ? `${formatShort(batch.entry_date)}${batch.entry_age_days ? ` 以 ${batch.entry_age_days} 日龄入栏` : ''}${entry ? `，平均 ${Number(entry).toFixed(1)} 千克` : ''}` : ''
      ].filter(Boolean).join(' · ')
    },
    metrics(): NonNullable<GrowthCurve['metrics']> {
      return this.curve?.metrics || {}
    },
    latestPoint(state): NonNullable<GrowthCurve['actual_points']>[number] | null {
      const points = state.curve?.actual_points || []
      return points.length ? points[points.length - 1] : null
    },
    hasStd(): boolean {
      return !!this.curve?.std_available && !!this.curve?.std_points?.length
    },
    deviationTone(): 'error' | 'text-3' {
      const deviation = this.metrics.deviation_ratio
      const allowed = Number(this.metrics.allowed_deviation ?? 0)
      if (deviation === null || deviation === undefined) return 'text-3'
      return Math.abs(Number(deviation)) > allowed ? 'error' : 'text-3'
    },
    recordActionText(): (row: WeighRecordRow) => string {
      return (row: WeighRecordRow): string => {
        if (row.action === 'EDIT') return '修改'
        if (row.action === 'REQUEST_CHANGE') return '提交修改申请'
        return '查看'
      }
    },
    compareChips(state): Array<{ batchId: number; batchNo: string }> {
      return state.compareIds.map((id) => {
        const series = state.curve?.compare_series?.find((item) => Number(item.batch_id) === id)
        return { batchId: id, batchNo: series?.batch_no || String(id) }
      })
    },
    formAvg(): number | null {
      if (!this.formState) return null
      if (this.formState.mode === 'EACH') {
        const values = this.formState.items.map((item) => Number(item.weight)).filter((value) => Number.isFinite(value) && value > 0)
        if (!values.length) return null
        return values.reduce((sum, value) => sum + value, 0) / values.length
      }
      const count = Number(this.formState.sampleCount || 0)
      const total = Number(this.formState.totalWeight || 0)
      if (!count || !total) return null
      return total / count
    },
    formStdWeight(state): number | null {
      if (!state.form) return null
      const points = (state.form.std_points || []).filter((item) => item.day_age !== undefined && item.std_weight !== undefined)
      if (!points.length) return null
      const date = state.formState.weighDate
      const entryAge = Number(state.form?.batch?.entry_age_days || 0)
      const dayAge = date && state.form?.batch?.entry_date
        ? entryAge + dayAgeAt(state.form.batch.entry_date, date)
        : Number(state.form.day_age || entryAge)
      const sorted = points
        .map((item) => ({ dayAge: Number(item.day_age), std: Number(item.std_weight) }))
        .sort((a, b) => a.dayAge - b.dayAge)
      if (dayAge <= sorted[0].dayAge) return sorted[0].std
      if (dayAge >= sorted[sorted.length - 1].dayAge) return sorted[sorted.length - 1].std
      for (let index = 1; index < sorted.length; index += 1) {
        const prev = sorted[index - 1]
        const next = sorted[index]
        if (dayAge <= next.dayAge) {
          const ratio = (dayAge - prev.dayAge) / Math.max(1, next.dayAge - prev.dayAge)
          return prev.std + (next.std - prev.std) * ratio
        }
      }
      return null
    },
    formDeviation(): number | null {
      const avg = this.formAvg
      const std = this.formStdWeight
      if (avg === null || !std) return null
      return (avg - std) / std
    },
    formAllowed(): number {
      return Number(this.form?.allowed_deviation ?? 0.1)
    },
    formOutOfRange(): boolean {
      return this.formDeviation !== null && Math.abs(this.formDeviation) > this.formAllowed
    },
    formGrowthResult(): 'SLOW' | 'FAST' | 'NORMAL' | '' {
      if (this.formDeviation === null) return ''
      if (this.formDeviation < -this.formAllowed) return 'SLOW'
      if (this.formDeviation > this.formAllowed) return 'FAST'
      return 'NORMAL'
    },
    formGrowthText(): string {
      const result = this.formGrowthResult
      if (result === 'SLOW') return '生长偏慢'
      if (result === 'FAST') return '生长偏快'
      if (result === 'NORMAL') return '在允许范围内'
      return ''
    },
    formDailyGain(): number | null {
      const avg = this.formAvg
      if (avg === null || !this.form) return null
      const last = this.form.last_weigh
      const baseDate = last?.date || this.form.batch?.entry_date || ''
      const baseWeight = Number(last?.avg_weight ?? this.form.batch?.entry_avg_weight ?? 0)
      if (!baseDate || !baseWeight) return null
      const days = dayAgeAt(baseDate, this.formState.weighDate || this.form.weigh_date || todayISO())
      if (!days || days <= 0) return null
      return (avg - baseWeight) / days
    },
    formGainBaseDate(): string {
      return this.form?.last_weigh?.date || this.form?.batch?.entry_date || ''
    },
    formSampleLow(): boolean {
      if (this.formState.mode !== 'SAMPLE') return false
      const suggested = Number(this.form?.suggested_sample || 0)
      return !!suggested && Number(this.formState.sampleCount || 0) < suggested
    },
    formSampleText(): string {
      const suggested = Number(this.form?.suggested_sample || 0)
      return suggested ? `建议至少抽 ${suggested} 头（存栏的 ${Math.round(Number(this.form?.sample_ratio || 0.15) * 100)}%）` : ''
    },
    canRegister(): boolean {
      return this.form?.can_register !== false
    },
    recordItems(state): WeighRecordRow[] {
      return state.records
    }
  },
  actions: {
    async load(force = false): Promise<void> {
      if (this.loaded && !force) return
      this.status = 'loading'
      this.error = ''
      try {
        if (!this.batchOptions.length) await this.loadBatchOptions()
        if (!this.batchId && this.batchOptions.length) {
          const withRecords = this.batchOptions
            .filter((item) => item.last_avg_weight !== null && item.last_avg_weight !== undefined)
            .sort((a, b) => Number(b.day_age || 0) - Number(a.day_age || 0))
          this.batchId = Number((withRecords[0] || this.batchOptions[0]).batch_id || 0)
        }
        if (!this.batchId) {
          this.status = 'empty'
          return
        }
        await Promise.all([this.loadCurve(), this.loadRecords(), this.loadCompareOptions()])
        this.loaded = true
        this.status = this.curve ? 'ready' : 'empty'
      } catch (error) {
        this.status = statusForError(error)
        this.error = errorText(error, '生长曲线加载失败，请稍后重新加载')
      }
    },
    async loadBatchOptions(): Promise<void> {
      this.batchOptionsLoading = true
      try {
        this.batchOptions = await feedingApi.weighBatchOptions()
      } catch (error) {
        this.status = statusForError(error)
        this.error = errorText(error, '称重批次加载失败')
      } finally {
        this.batchOptionsLoading = false
      }
    },
    async setBatch(batchId: number): Promise<void> {
      if (!batchId || batchId === this.batchId) return
      this.batchId = batchId
      this.compareIds = []
      this.curve = null
      this.records = []
      this.query.page = 1
      this.status = 'loading'
      try {
        await Promise.all([this.loadCurve(), this.loadRecords(), this.loadCompareOptions()])
        this.status = this.curve ? 'ready' : 'empty'
      } catch (error) {
        this.status = statusForError(error)
        this.error = errorText(error, '生长曲线加载失败')
      }
    },
    async loadCurve(): Promise<void> {
      if (!this.batchId) return
      this.curveLoading = true
      this.curveError = ''
      try {
        this.curve = await feedingApi.growthCurve(this.batchId, this.compareIds)
      } catch (error) {
        this.curve = null
        this.curveError = errorText(error, '生长曲线加载失败')
      } finally {
        this.curveLoading = false
      }
    },
    async loadRecords(): Promise<void> {
      if (!this.batchId) return
      this.recordsLoading = true
      try {
        const data = await feedingApi.weighRecords(this.batchId, this.query.page, this.query.size)
        this.records = data.list || []
        this.recordsTotal = Number(data.total || 0)
      } catch {
        this.records = []
        this.recordsTotal = 0
      } finally {
        this.recordsLoading = false
      }
    },
    async loadCompareOptions(): Promise<void> {
      if (!this.batchId) return
      try {
        this.compareOptions = await feedingApi.compareOptions(this.batchId)
      } catch {
        this.compareOptions = []
      }
    },
    async setPage(page: number): Promise<void> {
      this.query.page = Math.max(1, page)
      await this.loadRecords()
    },
    async setSize(size: number): Promise<void> {
      this.query.size = Math.max(1, size)
      this.query.page = 1
      await this.loadRecords()
    },
    async applyCompare(ids: number[]): Promise<void> {
      this.compareIds = ids.slice(0, 3)
      this.compareMenu = false
      await this.loadCurve()
    },
    async removeCompare(id: number): Promise<void> {
      this.compareIds = this.compareIds.filter((item) => item !== id)
      await this.loadCurve()
    },
    toggleCompare(id: number): void {
      if (this.compareIds.includes(id)) {
        this.compareIds = this.compareIds.filter((item) => item !== id)
      } else if (this.compareIds.length < 3) {
        this.compareIds = [...this.compareIds, id]
      }
    },
    async openWeigh(recordId?: number): Promise<void> {
      if (!this.batchId) return
      this.drawer = true
      this.formLoading = true
      this.formError = ''
      this.errors = {}
      this.cycleEditing = false
      this.editingRecordId = Number(recordId || 0)
      this.drawerMode = recordId ? 'edit' : 'create'
      try {
        const form = await feedingApi.weighForm(this.batchId)
        this.form = form
        this.formState = {
          mode: 'SAMPLE',
          weighDate: form.weigh_date || todayISO(),
          sampleCount: null,
          totalWeight: null,
          items: [{ weight: null }]
        }
        if (recordId) {
          const record = await feedingApi.weighRecord(recordId)
          this.editingVersion = Number(record.version || 0)
          this.formState = {
            mode: (record.mode === 'EACH' ? 'EACH' : 'SAMPLE') as WeighMode,
            weighDate: record.weigh_date || form.weigh_date || todayISO(),
            sampleCount: record.sample_count ?? null,
            totalWeight: record.total_weight ?? null,
            items: (record.items || []).length
              ? (record.items || []).map((item) => ({ weight: item.weight ?? null }))
              : [{ weight: null }]
          }
        }
        this.cycleInput = Number(form.weigh_cycle?.per_month || form.weigh_cycle?.suggested_per_month || 2)
      } catch (error) {
        this.formError = errorText(error, '称重表单加载失败')
      } finally {
        this.formLoading = false
      }
    },
    async changeWeighDate(value: string): Promise<void> {
      if (!this.form || !value) return
      this.formState.weighDate = value
      try {
        const form = await feedingApi.weighForm(this.batchId, value)
        this.form = { ...form, ...{ weigh_date: value } }
      } catch {
        /* 保留本地按日龄折算的标准体重 */
      }
    },
    addItem(): void {
      const stock = Number(this.form?.stock_count || 0)
      if (stock && this.formState.items.length >= stock) return
      this.formState.items.push({ weight: null })
    },
    removeItem(index: number): void {
      if (this.formState.items.length <= 1) return
      this.formState.items.splice(index, 1)
    },
    validate(): boolean {
      this.errors = {}
      if (!this.formState.weighDate) this.errors.weigh_date = '请选择称重日期'
      else if (this.formState.weighDate > todayISO()) this.errors.weigh_date = '称重日期不能晚于今天'
      else if (this.form?.batch?.entry_date && this.formState.weighDate < this.form.batch.entry_date) {
        this.errors.weigh_date = `称重日期不能早于入栏日期 ${this.form.batch.entry_date}`
      }
      const stock = Number(this.form?.stock_count || 0)
      if (this.formState.mode === 'SAMPLE') {
        const count = Number(this.formState.sampleCount ?? 0)
        if (this.formState.sampleCount === null || !Number.isInteger(count) || count < 1) {
          this.errors.sample_count = '请填写抽样数'
        } else if (stock && count > stock) {
          this.errors.sample_count = `不能超过存栏 ${stock} 头`
        }
        const total = Number(this.formState.totalWeight ?? 0)
        if (this.formState.totalWeight === null || !Number.isFinite(total) || total <= 0) {
          this.errors.total_weight = '总重要大于 0 千克'
        }
      } else {
        if (stock && this.formState.items.length > stock) this.errors.items = `录入头数不能超过存栏 ${stock} 头`
        this.formState.items.forEach((item, index) => {
          const weight = Number(item.weight ?? 0)
          if (item.weight === null || !Number.isFinite(weight) || weight <= 0) {
            this.errors[`item-${index}`] = `请填写第 ${index + 1} 头的体重`
          }
        })
        if (!this.formState.items.some((item) => Number(item.weight || 0) > 0)) {
          this.errors.items = '请至少录入一头体重'
        }
      }
      return Object.keys(this.errors).length === 0
    },
    async submit(): Promise<boolean> {
      if (this.submitting) return false
      if (!this.form?.can_register) {
        useUiStore().toast(this.form?.block_message || '这个批次现在不能登记称重', 'error')
        return false
      }
      if (!this.validate()) return false
      this.submitting = true
      try {
        const items = this.formState.items
          .map((item) => Number(item.weight || 0))
          .filter((weight) => weight > 0)
          .map((weight) => ({ weight }))
        const payload = this.formState.mode === 'SAMPLE'
          ? {
              mode: 'SAMPLE' as const,
              sample_count: Number(this.formState.sampleCount),
              total_weight: Number(this.formState.totalWeight)
            }
          : {
              mode: 'EACH' as const,
              sample_count: items.length,
              items
            }
        if (this.drawerMode === 'edit' && this.editingRecordId) {
          await feedingApi.updateWeigh(this.editingRecordId, {
            ...payload,
            version: this.editingVersion
          })
          useUiStore().toast('已修改称重记录，生长曲线已重新计算')
        } else {
          const result = await feedingApi.createWeigh(this.batchId, {
            weigh_date: this.formState.weighDate,
            ...payload
          })
          const message = result.growth_change === 'SET_SLOW'
            ? '已登记称重，批次已标为生长偏慢'
            : result.growth_change === 'SET_FAST'
              ? '已登记称重，批次已标为生长偏快'
              : result.growth_change === 'CLEARED'
                ? '已登记称重，生长偏慢标记已取消'
                : '已登记称重，待办已自动完成'
          useUiStore().toast(message)
        }
        this.drawer = false
        await this.load(true)
        return true
      } catch (error) {
        if (error instanceof ApiError) {
          const fields = fieldErrorsOf(error)
          if (Object.keys(fields).length) this.errors = { ...fields }
          else useUiStore().toast(error.message || '提交称重失败', 'error')
        } else {
          useUiStore().toast('提交称重失败，请稍后重试', 'error')
        }
        return false
      } finally {
        this.submitting = false
      }
    },
    async saveCycle(reset = false): Promise<void> {
      if (this.cycleSaving || !this.batchId) return
      this.cycleError = ''
      if (!reset && (this.cycleInput < 1 || this.cycleInput > 31)) {
        this.cycleError = '称重周期需为 1–31 次/月'
        return
      }
      this.cycleSaving = true
      try {
        const cycle: WeighCycle = await feedingApi.updateWeighCycle(this.batchId, reset
          ? { reset: true }
          : { per_month: this.cycleInput })
        if (this.form) this.form = { ...this.form, weigh_cycle: cycle, can_edit_cycle: true }
        if (reset) this.cycleInput = Number(cycle.suggested_per_month || cycle.per_month || 2)
        this.cycleEditing = false
        useUiStore().toast('已修改称重周期，下次称重日期已重新推算')
      } catch (error) {
        this.cycleError = errorText(error, '修改称重周期失败')
      } finally {
        this.cycleSaving = false
      }
    },
    async openRecordLogs(recordId: number): Promise<void> {
      this.logsDrawer = true
      this.logsTitle = '称重修改记录'
      this.logsLoading = true
      try {
        this.logs = await herdApi.recordLogs('WEIGH_RECORD', recordId)
      } catch {
        this.logs = []
      } finally {
        this.logsLoading = false
      }
    },
    cycleText(): string {
      const cycle = this.form?.weigh_cycle
      if (!cycle) return '—'
      return `${Number(cycle.per_month || 0)} 次 / 月`
    },
    reset(): void {
      this.$reset()
    }
  }
})

function formatShort(date: string): string {
  const value = dayjs(date)
  return value.isValid() ? value.format('M月D日') : date
}
