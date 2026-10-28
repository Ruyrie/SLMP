import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import type { BaseCategory, PenOption, RecordLog } from '@/api/herd'
import { herdApi } from '@/api/herd'
import type {
  AnimalExitPayload,
  AnimalFilter,
  AnimalSavePayload,
  AnimalSource,
  BreedingAnimalDetail,
  BreedingAnimalItem,
  BreedingOverview,
  BreedingRecord,
  ExitType
} from '@/api/breeding'
import { breedingApi } from '@/api/breeding'
import { useUiStore } from '@/stores/ui'
import {
  animalStatusText,
  errorText,
  fieldErrorsOf,
  statusForError,
  todayISO,
  type PageStatus
} from './shared'

export type FormMode = 'create' | 'edit'

interface AnimalFormState {
  animalId: number
  earTag: string
  breedId: number
  birthDate: string
  source: AnimalSource
  penId: number
  initialParity: number | null
  version: number
}

function emptyForm(): AnimalFormState {
  return {
    animalId: 0,
    earTag: '',
    breedId: 0,
    birthDate: '',
    source: 'PURCHASED',
    penId: 0,
    initialParity: 0,
    version: 0
  }
}

export const useBreedingAnimalStore = defineStore('breedingAnimalList', {
  state: () => ({
    status: 'loading' as PageStatus,
    error: '',
    overview: null as BreedingOverview | null,
    list: [] as BreedingAnimalItem[],
    total: 0,
    query: {
      page: 1,
      size: 20,
      status: 'ALL' as AnimalFilter | string,
      keyword: '',
      penId: 0
    },
    showExited: false,
    loaded: false,
    requestId: 0,

    categories: [] as BaseCategory[],
    pens: [] as PenOption[],
    optionsLoaded: false,
    optionsLoading: false,

    formDrawer: false,
    formMode: 'create' as FormMode,
    form: emptyForm(),
    formErrors: {} as Record<string, string>,
    submitting: false,

    detailDrawer: false,
    detail: null as BreedingAnimalDetail | null,
    detailLoading: false,
    detailError: '',
    records: [] as BreedingRecord[],
    recordsTotal: 0,
    recordsPage: 1,
    recordsSize: 20,
    recordsLoading: false,

    logsDrawer: false,
    logsTitle: '',
    logs: [] as RecordLog[],
    logsLoading: false,

    exitDialog: false,
    exitAnimalId: 0,
    exitEarTag: '',
    exitVersion: 0,
    exitAnimalLabel: '',
    exitForm: { exitType: 'CULL' as ExitType, exitDate: todayISO(), reason: '' },
    exitErrors: {} as Record<string, string>,
    exitSubmitting: false,

    exportBusy: false
  }),
  getters: {
    counts(state): { all: number; empty: number; mated: number; pregnant: number; lactating: number; exited: number } {
      const counts = state.overview?.counts
      return {
        all: Number(counts?.all || 0),
        empty: Number(counts?.empty || 0),
        mated: Number(counts?.mated || 0),
        pregnant: Number(counts?.pregnant || 0),
        lactating: Number(counts?.lactating || 0),
        exited: Number(counts?.exited || 0)
      }
    },
    segItems(): Array<{ label: string; value: string; count: number }> {
      return [
        { label: '全部', value: 'ALL', count: this.counts.all },
        { label: '空怀', value: 'EMPTY', count: this.counts.empty },
        { label: '已配种', value: 'MATED', count: this.counts.mated },
        { label: '妊娠', value: 'PREGNANT', count: this.counts.pregnant },
        { label: '哺乳', value: 'LACTATING', count: this.counts.lactating }
      ]
    },
    penItems(state): Array<{ title: string; value: number }> {
      return [
        { title: '全部栏舍', value: 0 },
        ...(state.overview?.penOptions || []).map((pen) => ({
          title: pen.name || '栏舍',
          value: Number(pen.penId || 0)
        }))
      ]
    },
    dueSoonText(state): string {
      const list = state.overview?.dueSoon || []
      if (!list.length) return ''
      const total = Number(state.overview?.dueSoonTotal || list.length)
      if (total > 2) return `今天和 3 天内共有 ${total} 头${state.overview?.animalLabel || '母猪'}预产。`
      return list.map((item) => {
        const days = Number(item.daysToDue || 0)
        if (days <= 0) return `${item.earTag || ''} 今天预产。`
        return `${item.earTag || ''} 预产期 ${formatMonthDayWeek(item.expectedDueDate)}，还有 ${days} 天。`
      }).join('')
    },
    breedItems(state): Array<{ title: string; value: number; gestationDays: number; categoryName: string }> {
      const items: Array<{ title: string; value: number; gestationDays: number; categoryName: string }> = []
      for (const category of state.categories) {
        for (const breed of category.breeds || []) {
          if (breed.gestation_days === null || breed.gestation_days === undefined) continue
          items.push({
            title: `${breed.name || '品种'}${category.name ? ` · ${category.name}` : ''}`,
            value: Number(breed.id || 0),
            gestationDays: Number(breed.gestation_days || 0),
            categoryName: category.name || ''
          })
        }
      }
      return items
    },
    penOptions(state): Array<{ title: string; value: number; unit: string }> {
      return state.pens
        .filter((pen) => !pen.disabled)
        .map((pen) => ({
          title: `${pen.pen_name || '栏舍'}${pen.capacity ? ` · 容量 ${pen.capacity}` : ''}`,
          value: Number(pen.pen_id || 0),
          unit: pen.unit_text || '头'
        }))
    },
    filteredEmpty(state): boolean {
      return state.status === 'filtered_empty' || (state.status === 'ready' && state.list.length === 0 && this.hasFilter)
    },
    hasFilter(state): boolean {
      return !!state.query.keyword || state.query.status !== 'ALL' || !!state.query.penId
    },
    detailSubtitle(state): string {
      const detail = state.detail
      if (!detail) return ''
      return [detail.breedName, detail.animalLabel, detail.parity ? `第 ${detail.parity} 胎` : '', detail.penName]
        .filter(Boolean)
        .join(' · ')
    },
    nextActionText(state): string {
      return state.detail?.nextActionText || ''
    },
    formTitle(): string {
      return this.formMode === 'create' ? '新增种畜' : '编辑档案'
    },
    logItems(state): Array<{ title: string; time: string; desc: string; tone?: string; icon?: string }> {
      return state.logs.map((log) => ({
        title: log.changes?.length
          ? log.changes.map((change) => `${change.field_name || '字段'}：${change.before || '—'} → ${change.after || '—'}`).join('；')
          : log.op_action_text || '修改档案',
        time: log.created_at || '',
        desc: log.operator?.name || '',
        tone: 'primary',
        icon: 'edit'
      }))
    }
  },
  actions: {
    async load(force = false): Promise<void> {
      if (this.loaded && !force) return
      await this.loadOverview()
      await this.loadList(force)
    },
    async loadOverview(): Promise<void> {
      try {
        this.overview = await breedingApi.overview()
      } catch (error) {
        if (this.status !== 'forbidden') {
          this.status = statusForError(error)
          this.error = errorText(error, '繁育概况加载失败')
        }
      }
    },
    async loadList(force = false): Promise<void> {
      if (this.showExited) this.query.status = 'EXITED'
      const requestId = ++this.requestId
      if (!force && !this.list.length) this.status = 'loading'
      this.error = ''
      try {
        const data = await breedingApi.animals({
          page: this.query.page,
          size: this.query.size,
          status: this.query.status,
          keyword: this.query.keyword.trim() || undefined,
          penId: this.query.penId || undefined
        })
        if (requestId !== this.requestId) return
        this.list = data.list || []
        this.total = Number(data.total || 0)
        this.loaded = true
        if (!this.list.length) {
          this.status = this.hasFilter || this.showExited ? 'filtered_empty' : 'empty'
        } else {
          this.status = 'ready'
        }
        const maxPage = Math.max(1, Math.ceil(this.total / this.query.size))
        if (this.query.page > maxPage) this.query.page = maxPage
      } catch (error) {
        if (requestId !== this.requestId) return
        this.status = statusForError(error)
        this.error = errorText(error, '种畜档案加载失败，请稍后重新加载')
      }
    },
    async loadFormOptions(): Promise<void> {
      if (this.optionsLoaded || this.optionsLoading) return
      this.optionsLoading = true
      try {
        const [categories, pens] = await Promise.all([herdApi.baseCategories(), herdApi.penOptions()])
        this.categories = categories || []
        this.pens = pens || []
        this.optionsLoaded = true
      } catch (error) {
        useUiStore().toast(errorText(error, '档案表单选项加载失败'), 'error')
      } finally {
        this.optionsLoading = false
      }
    },
    setFilter(status: string): void {
      this.showExited = status === 'EXITED'
      this.query.status = status
      this.query.page = 1
      void this.loadList(true)
    },
    setPen(penId: number): void {
      this.query.penId = penId
      this.query.page = 1
      void this.loadList(true)
    },
    setKeyword(): void {
      this.query.page = 1
      void this.loadList(true)
    },
    clearFilters(): void {
      this.query.keyword = ''
      this.query.penId = 0
      this.query.status = 'ALL'
      this.query.page = 1
      this.showExited = false
      void this.loadList(true)
    },
    setPage(page: number): void {
      this.query.page = Math.max(1, page)
      void this.loadList()
    },
    setSize(size: number): void {
      this.query.size = Math.max(1, size)
      this.query.page = 1
      void this.loadList()
    },
    async toggleExited(): Promise<void> {
      this.showExited = !this.showExited
      this.query.status = this.showExited ? 'EXITED' : 'ALL'
      this.query.page = 1
      await this.loadList(true)
    },
    async refresh(): Promise<void> {
      await this.loadOverview()
      await this.loadList(true)
    },
    async openCreate(): Promise<void> {
      await this.loadFormOptions()
      this.formMode = 'create'
      this.form = emptyForm()
      this.form.breedId = Number(this.breedItems[0]?.value || 0)
      this.form.penId = Number(this.penOptions[0]?.value || 0)
      this.formErrors = {}
      this.formDrawer = true
    },
    async openEdit(animalId: number): Promise<void> {
      await this.loadFormOptions()
      let detail = this.detail?.animalId === animalId ? this.detail : null
      if (!detail) {
        try {
          detail = await breedingApi.animalDetail(animalId)
        } catch (error) {
          useUiStore().toast(errorText(error, '种畜档案加载失败'), 'error')
          return
        }
      }
      this.formMode = 'edit'
      this.form = {
        animalId: Number(detail.animalId || animalId),
        earTag: detail.earTag || '',
        breedId: Number(detail.breedId || 0),
        birthDate: detail.birthDate || '',
        source: (detail.source as AnimalSource) || 'PURCHASED',
        penId: Number(detail.penId || 0),
        initialParity: Number(detail.initialParity || 0),
        version: Number(detail.version || 0)
      }
      this.formErrors = {}
      this.formDrawer = true
    },
    validateForm(): boolean {
      this.formErrors = {}
      if (!this.form.earTag.trim()) this.formErrors.ear_tag = '请填写耳号'
      else if ([...this.form.earTag.trim()].length > 20) this.formErrors.ear_tag = '耳号最多 20 个字'
      if (!this.form.breedId) this.formErrors.breed_id = '请选择品种'
      if (!this.form.penId) this.formErrors.pen_id = '请选择所在栏舍'
      if (this.form.birthDate && this.form.birthDate > todayISO()) this.formErrors.birth_date = '出生日期不能晚于今天'
      const parity = Number(this.form.initialParity ?? 0)
      if (!Number.isInteger(parity) || parity < 0 || parity > 30) this.formErrors.initial_parity = '建档时胎次需为 0–30'
      return Object.keys(this.formErrors).length === 0
    },
    async saveForm(): Promise<boolean> {
      if (this.submitting || !this.validateForm()) return false
      this.submitting = true
      const payload: AnimalSavePayload = {
        ear_tag: this.form.earTag.trim(),
        breed_id: this.form.breedId,
        birth_date: this.form.birthDate || undefined,
        source: this.form.source,
        pen_id: this.form.penId,
        initial_parity: Number(this.form.initialParity ?? 0),
        version: this.formMode === 'edit' ? this.form.version : undefined
      }
      try {
        if (this.formMode === 'create') {
          const detail = await breedingApi.createAnimal(payload)
          this.formDrawer = false
          useUiStore().toast(`已新增${detail.earTag || '种畜'}档案`)
        } else {
          const detail = await breedingApi.updateAnimal(this.form.animalId, payload)
          this.detail = detail
          this.formDrawer = false
          useUiStore().toast('已保存档案')
        }
        await this.refresh()
        return true
      } catch (error) {
        if (error instanceof ApiError) {
          const fields = fieldErrorsOf(error)
          if (Object.keys(fields).length) this.formErrors = { ...fields }
          else useUiStore().toast(error.message || '保存失败', 'error')
        } else {
          useUiStore().toast('保存失败，请稍后重试', 'error')
        }
        return false
      } finally {
        this.submitting = false
      }
    },
    async openDetail(animalId: number): Promise<void> {
      this.detailDrawer = true
      this.detailLoading = true
      this.detailError = ''
      this.records = []
      this.recordsTotal = 0
      this.recordsPage = 1
      try {
        const [detail, records] = await Promise.all([
          breedingApi.animalDetail(animalId),
          breedingApi.animalRecords(animalId, 1, this.recordsSize)
        ])
        this.detail = detail
        this.records = records.list || []
        this.recordsTotal = Number(records.total || 0)
      } catch (error) {
        this.detail = null
        this.detailError = errorText(error, '种畜档案加载失败')
      } finally {
        this.detailLoading = false
      }
    },
    async setRecordsSize(size: number): Promise<void> {
      this.recordsSize = Math.max(1, Number(size) || 20)
      await this.loadRecords(1)
    },
    async loadRecords(page?: number): Promise<void> {
      if (!this.detail) return
      const targetPage = page ?? this.recordsPage
      this.recordsLoading = true
      try {
        const data = await breedingApi.animalRecords(Number(this.detail.animalId), targetPage, this.recordsSize)
        this.records = data.list || []
        this.recordsTotal = Number(data.total || 0)
        this.recordsPage = targetPage
      } catch (error) {
        useUiStore().toast(errorText(error, '繁育记录加载失败'), 'error')
      } finally {
        this.recordsLoading = false
      }
    },
    async openAnimalLogs(): Promise<void> {
      if (!this.detail) return
      this.logsTitle = `${this.detail.earTag || ''} 修改记录`
      this.logsDrawer = true
      this.logsLoading = true
      try {
        this.logs = await herdApi.recordLogs('BREEDING_ANIMAL', Number(this.detail.animalId))
      } catch {
        this.logs = []
      } finally {
        this.logsLoading = false
      }
    },
    async openRecordLogs(record: BreedingRecord): Promise<void> {
      const type = record.recordType || ''
      if (!type || !record.recordId) return
      this.logsTitle = `${record.recordTypeText || '记录'} 修改记录`
      this.logsDrawer = true
      this.logsLoading = true
      try {
        this.logs = await herdApi.recordLogs(type, Number(record.recordId))
      } catch {
        this.logs = []
      } finally {
        this.logsLoading = false
      }
    },
    openExit(animalId: number, earTag: string, version: number, label: string): void {
      this.exitAnimalId = animalId
      this.exitEarTag = earTag
      this.exitVersion = version
      this.exitAnimalLabel = label
      this.exitForm = { exitType: 'CULL', exitDate: todayISO(), reason: '' }
      this.exitErrors = {}
      this.exitDialog = true
      this.detailDrawer = false
    },
    async confirmExit(): Promise<boolean> {
      this.exitErrors = {}
      if (!this.exitForm.exitDate) this.exitErrors.exit_date = '请选择日期'
      else if (this.exitForm.exitDate > todayISO()) this.exitErrors.exit_date = '日期不能晚于今天'
      if (!this.exitForm.reason.trim()) this.exitErrors.reason = '请填写原因'
      else if ([...this.exitForm.reason.trim()].length > 50) this.exitErrors.reason = '原因最多 50 个字'
      if (Object.keys(this.exitErrors).length) return false
      if (this.exitSubmitting) return false
      this.exitSubmitting = true
      const payload: AnimalExitPayload = {
        exit_type: this.exitForm.exitType,
        exit_date: this.exitForm.exitDate,
        reason: this.exitForm.reason.trim(),
        version: this.exitVersion
      }
      try {
        const detail = await breedingApi.exitAnimal(this.exitAnimalId, payload)
        this.exitDialog = false
        useUiStore().toast(payload.exit_type === 'CULL'
          ? `已登记淘汰，${detail.earTag || this.exitEarTag} 不再出现在配种选择中`
          : `已登记死亡，${detail.earTag || this.exitEarTag} 的历史记录已保留`)
        this.detail = null
        await this.refresh()
        return true
      } catch (error) {
        if (error instanceof ApiError && (error.code === 40902 || error.code === 40903)) {
          useUiStore().toast('档案已变化，列表已刷新', 'error')
          await this.refresh()
        } else {
          useUiStore().toast(errorText(error, '登记失败'), 'error')
        }
        return false
      } finally {
        this.exitSubmitting = false
      }
    },
    /** 导出当前筛选下的全部种畜：按 200 条一页取完（服务端单页上限 200），不再只导第一页。 */
    async exportRows(): Promise<BreedingAnimalItem[]> {
      this.exportBusy = true
      try {
        const size = 200
        const rows: BreedingAnimalItem[] = []
        for (let page = 1; page <= 100; page += 1) {
          const data = await breedingApi.animals({
            page,
            size,
            status: this.showExited ? 'EXITED' : this.query.status,
            keyword: this.query.keyword.trim() || undefined,
            penId: this.query.penId || undefined
          })
          const list = data.list || []
          rows.push(...list)
          const total = Number(data.total || 0)
          if (!list.length || list.length < size || (total && rows.length >= total)) break
        }
        return rows
      } catch (error) {
        useUiStore().toast(errorText(error, '导出失败'), 'error')
        return []
      } finally {
        this.exportBusy = false
      }
    },
    reset(): void {
      this.$reset()
    }
  }
})

function formatMonthDayWeek(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return value
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
  return `${date.getMonth() + 1}月${date.getDate()}日 ${week}`
}

export { animalStatusText }
