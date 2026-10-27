import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import { herdApi, type BatchEventDetail } from '@/api/herd'
import { useUiStore } from '@/stores/ui'
import { errorText, fieldErrorsOf, todayISO } from './shared'

const titles: Record<string, string> = { ENTRY: '入栏', TRANSFER: '转栏', DEATH: '死淘' }
const emptyForm = () => ({ quantity: null as number | null, eventDate: '', entryAgeDays: null as number | null,
  entryAvgWeight: null as number | null, introUnitPrice: null as number | null, remark: '', capacityConfirmed: false })
const validDecimal = (value: number | null, max: number, min = 0) => value !== null && Number.isFinite(value)
  && value >= min && value <= max && Math.abs(value * 100 - Math.round(value * 100)) < 1e-6

export const useHerdRecordEditStore = defineStore('herdRecordEdit', {
  state: () => ({
    visible: false, loading: false, saving: false, requestId: 0,
    record: null as BatchEventDetail | null,
    form: emptyForm(), errors: {} as Record<string, string>, error: '', capacityMessage: '',
    target: null as { eventId: number; batchId: number; type: string } | null
  }),
  getters: {
    title: state => `修改${titles[state.target?.type || ''] || ''}记录`,
    isEntry: state => state.record?.event_type === 'ENTRY',
    purchased: state => state.record?.entry?.source === 'PURCHASED'
  },
  actions: {
    reset(): void {
      const next = this.requestId + 1
      this.$reset()
      this.requestId = next
    },
    async open(eventId: number, expectedBatchId: number, expectedType: string): Promise<void> {
      this.reset()
      if (!eventId || !expectedBatchId || !titles[expectedType]) return
      const requestId = this.requestId
      this.target = { eventId, batchId: expectedBatchId, type: expectedType }
      this.visible = true
      this.loading = true
      try {
        const record = await herdApi.batchEvent(eventId)
        if (requestId !== this.requestId) return
        if (record.event_id !== eventId || record.batch_id !== expectedBatchId || record.event_type !== expectedType)
          throw new Error('记录与当前批次不一致，请刷新后重试')
        if (!record.change_state?.can_edit) throw new Error('这条记录已不能直接修改，请刷新批次后提交修改申请')
        if (!record.pen?.pen_id || (expectedType === 'ENTRY'
          ? record.entry?.batch_version === undefined || !record.entry?.breed_id
          : record.version === undefined)) throw new Error('记录信息不完整，请刷新后重试')
        this.record = record
        this.form = { quantity: record.quantity ?? null, eventDate: record.event_date || '',
          entryAgeDays: record.entry?.entry_age_days ?? null, entryAvgWeight: record.entry?.entry_avg_weight ?? null,
          introUnitPrice: record.entry?.intro_unit_price ?? null, remark: record.remark || '', capacityConfirmed: false }
      } catch (error) {
        if (requestId === this.requestId) this.error = errorText(error)
      } finally {
        if (requestId === this.requestId) this.loading = false
      }
    },
    async reload(): Promise<void> {
      const target = this.target
      if (target) await this.open(target.eventId, target.batchId, target.type)
    },
    setQuantity(value: unknown): void {
      this.form.quantity = value === '' || value === null ? null : Number(value)
      this.form.capacityConfirmed = false
      this.capacityMessage = ''
      delete this.errors.quantity
      delete this.errors.entry_count
    },
    validate(): boolean {
      const form = this.form
      this.errors = {}
      if (form.quantity === null || !Number.isSafeInteger(form.quantity) || form.quantity <= 0)
        this.errors.quantity = '数量需为大于 0 的整数'
      if (!/^\d{4}-\d{2}-\d{2}$/.test(form.eventDate) || form.eventDate > todayISO()) this.errors.event_date = '请选择不晚于今天的日期'
      if (this.isEntry) {
        if (form.entryAgeDays === null || !Number.isInteger(form.entryAgeDays) || form.entryAgeDays < 0 || form.entryAgeDays > 3650)
          this.errors.entry_age_days = '入栏日龄需为 0 至 3650 的整数'
        if (form.entryAvgWeight !== null && !validDecimal(form.entryAvgWeight, 9999.99, 0.01))
          this.errors.entry_avg_weight = '体重需大于 0，最多保留 2 位小数'
        if (this.purchased && !validDecimal(form.introUnitPrice, 999999.99)) this.errors.intro_unit_price = '请填写有效的引种单价，最多保留 2 位小数'
      } else if (form.remark.length > 100) this.errors.remark = '备注最多 100 字'
      return Object.keys(this.errors).length === 0
    },
    async save(): Promise<boolean> {
      const record = this.record
      if (!record || !this.visible || this.saving || !this.validate()) return false
      if (this.capacityMessage && !this.form.capacityConfirmed) return false
      const requestId = this.requestId
      this.saving = true
      this.error = ''
      try {
        const form = this.form
        if (record.event_type === 'ENTRY') {
          const entry = record.entry!
          await herdApi.updateEntry(record.batch_id!, {
            pen_id: record.pen!.pen_id!, breed_id: entry.breed_id!, purpose: entry.purpose as 'COMMERCIAL' | 'BREEDING',
            source: entry.source as 'PURCHASED' | 'SELF_BRED', entry_date: form.eventDate,
            entry_count: form.quantity!, entry_age_days: form.entryAgeDays!,
            entry_avg_weight: form.entryAvgWeight ?? undefined,
            intro_unit_price: this.purchased ? form.introUnitPrice! : undefined,
            capacity_confirmed: form.capacityConfirmed, version: entry.batch_version!
          })
        } else if (record.event_type === 'TRANSFER') {
          if (!record.to_pen?.pen_id) throw new Error('转入栏舍信息不完整，请重新加载')
          await herdApi.updateTransfer(record.event_id!, { from_pen_id: record.pen!.pen_id!, to_pen_id: record.to_pen.pen_id,
            quantity: form.quantity!, event_date: form.eventDate, remark: form.remark,
            capacity_confirmed: form.capacityConfirmed, version: record.version! })
        } else if (record.event_type === 'DEATH') {
          await herdApi.updateDeath(record.event_id!, { pen_id: record.pen!.pen_id!, quantity: form.quantity!,
            event_date: form.eventDate, reason_code: record.reason_code!, executor_user_id: record.executor?.user_id,
            remark: form.remark, file_ids: (record.photos || []).map(photo => photo.file_id!).filter(Boolean), version: record.version! })
        }
        if (requestId !== this.requestId) return false
        this.visible = false
        useUiStore().toast('记录已修改，存栏已同步更新')
        return true
      } catch (error) {
        if (requestId !== this.requestId) return false
        this.errors = fieldErrorsOf(error)
        this.error = errorText(error, '修改失败，请重试')
        if (error instanceof ApiError && error.code === 42206) {
          const data = (error.data || {}) as Record<string, unknown>
          this.capacityMessage = `${data.pen_name || '栏舍'}修改后在栏 ${data.after || '—'} 头，超过容量 ${data.capacity || '—'} 头`
          this.form.capacityConfirmed = false
          this.error = ''
        }
        return false
      } finally {
        if (requestId === this.requestId) this.saving = false
      }
    }
  }
})
