import { defineStore } from 'pinia'
import {
  farmApi,
  type CreateFarmBody,
  type FarmAuditVO,
  type FarmCategoryVO,
  type FarmChangeVO,
  type FarmDetailVO,
  type QualFileVO,
  type RegistrationVO,
  type SubmitFarmChangeBody,
  type UpdateRegistrationBody
} from '@/api/farm'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { ApiError } from '@zmt/shared'
import {
  digitsOf,
  formatPhoneInput,
  isCancelError,
  PHONE_RE,
  textLength,
  type PageStatus
} from './shared'

export type CreateMode = 'new' | 'registration' | 'change'

export interface QualItem {
  key: string
  fileId: number | null
  url?: string
  thumbUrl?: string
  name: string
  size: number
  progress: number
  uploading: boolean
  failed: boolean
  reshoot: boolean
  isNew: boolean
  raw?: File
}

export interface FarmFormState {
  name: string
  mainCategoryId: number | null
  scale: string
  contactName: string
  contactPhone: string
  address: string
  province: string
  city: string
  district: string
  township: string
  adcode: string
  longitude: string
  latitude: string
  locAccuracy: number | null
  remark: string
}

export interface FarmLocationPayload {
  address: string
  province: string
  city: string
  district: string
  township: string
  adcode: string
  longitude: string
  latitude: string
  locAccuracy: number | null
}

interface FormSnapshot extends FarmFormState {
  qualIds: number[]
}

function emptyForm(): FarmFormState {
  return {
    name: '',
    mainCategoryId: null,
    scale: '',
    contactName: '',
    contactPhone: '',
    address: '',
    province: '',
    city: '',
    district: '',
    township: '',
    adcode: '',
    longitude: '',
    latitude: '',
    locAccuracy: null,
    remark: ''
  }
}

function toNumber(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function snapshotOf(form: FarmFormState, files: QualItem[]): FormSnapshot {
  return {
    ...form,
    qualIds: files
      .map((file) => file.fileId)
      .filter((id): id is number => typeof id === 'number' && Number.isFinite(id) && id > 0)
      .sort((a, b) => a - b)
  }
}

function messageOf(error: unknown, fallback: string): string {
  const value = error as ApiError | undefined
  return value?.message || fallback
}

export const useFarmCreateStore = defineStore('farmCreate', {
  state: () => ({
    mode: 'new' as CreateMode,
    farmId: 0,
    status: 'loading' as PageStatus,
    error: '',
    form: emptyForm(),
    original: null as FormSnapshot | null,
    categories: [] as FarmCategoryVO[],
    qualFiles: [] as QualItem[],
    audit: null as FarmAuditVO | null,
    detail: null as FarmDetailVO | null,
    latestChange: null as FarmChangeVO | null,
    formErrors: {} as Record<string, string>,
    qualError: '',
    submitting: false,
    uploading: 0,
    nameChecking: false,
    leaveConfirm: false
  }),
  getters: {
    category(state): FarmCategoryVO | undefined {
      return state.categories.find((item) => item.id === state.form.mainCategoryId)
    },
    unitText(): string {
      return this.category?.count_unit_text || '头'
    },
    isChange: (state): boolean => state.mode === 'change',
    isRegistration: (state): boolean => state.mode === 'registration',
    isReadonly(state): boolean {
      return state.mode === 'change' && state.latestChange?.status === 'PENDING'
    },
    pendingUploadCount: (state): number => state.qualFiles.filter((file) => file.uploading).length,
    failedUploadCount: (state): number => state.qualFiles.filter((file) => file.failed).length,
    uploadedCount: (state): number => state.qualFiles.length,
    qualIds(state): number[] {
      return state.qualFiles
        .map((file) => file.fileId)
        .filter((id): id is number => typeof id === 'number' && Number.isFinite(id) && id > 0)
    },
    changedFields(): Array<'NAME' | 'ADDRESS' | 'QUALIFICATION'> {
      if (!this.original) return []
      const result: Array<'NAME' | 'ADDRESS' | 'QUALIFICATION'> = []
      if (this.form.name.trim() !== String(this.original.name || '').trim()) result.push('NAME')
      const currentAddress = [
        this.form.address,
        this.form.province,
        this.form.city,
        this.form.district,
        this.form.township,
        this.form.adcode,
        String(this.form.longitude),
        String(this.form.latitude)
      ].join('|')
      const originalAddress = [
        this.original.address,
        this.original.province,
        this.original.city,
        this.original.district,
        this.original.township,
        this.original.adcode,
        String(this.original.longitude),
        String(this.original.latitude)
      ].join('|')
      if (currentAddress !== originalAddress) result.push('ADDRESS')
      const currentIds = [...this.qualIds].sort((a, b) => a - b).join(',')
      const originalIds = [...this.original.qualIds].sort((a, b) => a - b).join(',')
      if (currentIds !== originalIds) result.push('QUALIFICATION')
      return result
    },
    hasChanges(): boolean {
      if (this.mode !== 'change') return this.isDirty
      return this.changedFields.length > 0
    },
    isDirty(): boolean {
      if (!this.original) return false
      return JSON.stringify(snapshotOf(this.form, this.qualFiles)) !== JSON.stringify(this.original)
    },
    canSubmit(): boolean {
      return !this.submitting && !this.isReadonly
    },
    title(): string {
      if (this.mode === 'change') return '资料变更'
      if (this.mode === 'registration') return '修改注册信息'
      return '新建养殖场'
    }
  },
  actions: {
    resetState(): void {
      this.form = emptyForm()
      this.original = null
      this.categories = []
      this.qualFiles = []
      this.audit = null
      this.detail = null
      this.latestChange = null
      this.formErrors = {}
      this.qualError = ''
      this.submitting = false
      this.uploading = 0
      this.nameChecking = false
      this.error = ''
      this.leaveConfirm = false
      this.status = 'loading'
    },
    async init(mode: CreateMode, farmId = 0): Promise<void> {
      this.resetState()
      this.mode = mode
      this.farmId = farmId
      try {
        this.categories = await farmApi.categories()
        if (mode === 'new') {
          const auth = useAuthStore()
          this.form.contactName = String(auth.user?.name || '')
          this.original = snapshotOf(this.form, this.qualFiles)
          this.status = 'ready'
          return
        }
        if (!farmId) {
          this.status = 'error'
          this.error = '缺少养殖场编号，无法打开该页面'
          return
        }
        if (mode === 'registration') {
          const audit = await farmApi.audit(farmId)
          this.audit = audit
          if (audit.status !== 'PENDING' && audit.status !== 'REJECTED') {
            this.status = 'error'
            this.error = '该养殖场已通过审核，请从养殖场信息中提交资料变更'
            return
          }
          this.fillRegistration(audit.registration)
          this.original = snapshotOf(this.form, this.qualFiles)
          this.status = 'ready'
          return
        }
        const detail = await farmApi.detail(farmId)
        this.detail = detail
        if (detail.status !== 'NORMAL') {
          this.status = 'error'
          this.error =
            detail.status === 'DISABLED'
              ? `${detail.name}已停用，停用期间不能提交资料变更`
              : '当前养殖场状态不支持资料变更'
          return
        }
        let latest: FarmChangeVO | null = null
        try {
          latest = await farmApi.latestChange(farmId)
        } catch {
          latest = null
        }
        this.latestChange = latest
        this.fillRegistration(detail.registration)
        if (latest?.after && (latest.status === 'PENDING' || latest.status === 'REJECTED')) {
          this.applyChangeSnapshot(latest)
        } else if (latest?.remark) {
          this.form.remark = latest.remark
        }
        this.original = snapshotOfFromRegistration(detail.registration)
        this.status = 'ready'
      } catch (error) {
        if (isCancelError(error)) return
        const value = error as ApiError
        this.status = value?.isForbidden ? 'forbidden' : 'error'
        this.error = messageOf(error, '页面加载失败，请稍后重试')
      }
    },
    fillRegistration(registration?: RegistrationVO): void {
      if (!registration) return
      this.form.name = registration.name || ''
      this.form.mainCategoryId = registration.main_category_id ?? null
      this.form.scale = registration.scale === null || registration.scale === undefined ? '' : String(registration.scale)
      this.form.contactName = registration.contact_name || ''
      this.form.contactPhone = formatPhoneInput(registration.contact_phone || '')
      this.form.address = registration.address || ''
      this.form.province = registration.province || ''
      this.form.city = registration.city || ''
      this.form.district = registration.district || ''
      this.form.township = registration.township || ''
      this.form.adcode = registration.adcode || ''
      this.form.longitude = registration.longitude === undefined || registration.longitude === null ? '' : String(registration.longitude)
      this.form.latitude = registration.latitude === undefined || registration.latitude === null ? '' : String(registration.latitude)
      this.form.locAccuracy = registration.loc_accuracy ?? null
      this.qualFiles = (registration.qualification_files || []).map((file, index) => qualFromVO(file, index))
    },
    applyChangeSnapshot(change: FarmChangeVO): void {
      const after = change.after
      if (!after) return
      this.form.name = after.name || this.form.name
      this.form.address = after.address || ''
      this.form.province = after.province || ''
      this.form.city = after.city || ''
      this.form.district = after.district || ''
      this.form.township = after.township || ''
      this.form.adcode = after.adcode || ''
      this.form.longitude = after.longitude === undefined || after.longitude === null ? '' : String(after.longitude)
      this.form.latitude = after.latitude === undefined || after.latitude === null ? '' : String(after.latitude)
      this.form.locAccuracy = after.loc_accuracy ?? null
      this.form.remark = change.remark || ''
      if (after.qualification_files) {
        this.qualFiles = after.qualification_files.map((file, index) => qualFromVO(file, index))
      }
    },
    setCategory(categoryId: number | null): void {
      this.form.mainCategoryId = categoryId
    },
    setLocation(location: FarmLocationPayload): void {
      this.form.address = location.address
      this.form.province = location.province
      this.form.city = location.city
      this.form.district = location.district
      this.form.township = location.township
      this.form.adcode = location.adcode
      this.form.longitude = location.longitude
      this.form.latitude = location.latitude
      this.form.locAccuracy = location.locAccuracy
    },
    async checkName(): Promise<void> {
      const name = this.form.name.trim()
      if (!name) {
        delete this.formErrors.name
        return
      }
      if (this.nameChecking) return
      if (this.mode === 'change' && name === this.original?.name) return
      this.nameChecking = true
      try {
        const result = await farmApi.checkName(name, this.mode === 'new' ? undefined : this.farmId)
        if (!result.available && result.message) this.formErrors.name = result.message
        else if (!result.available) this.formErrors.name = '养殖场名称已存在，请换一个名称'
        else if (this.formErrors.name) delete this.formErrors.name
      } catch {
        /* 名称检查失败不阻塞填写，提交时服务端会再校验 */
      } finally {
        this.nameChecking = false
      }
    },
    validate(): boolean {
      const errors: Record<string, string> = {}
      const name = this.form.name.trim()
      if (!name) errors.name = '请填写养殖场名称'
      else if (textLength(name) > 20) errors.name = '养殖场名称不能超过 20 个字'
      if (this.mode !== 'change') {
        if (!this.form.mainCategoryId) errors.main_category_id = '请选择主要品类'
        const scale = toNumber(this.form.scale)
        if (scale === null) errors.scale = '请填写规模'
        else if (!Number.isInteger(scale) || scale < 1 || scale > 999999) errors.scale = '规模需为 1–999,999 之间的整数'
        const contact = this.form.contactName.trim()
        if (!contact) errors.contact_name = '请填写联系人'
        else if (textLength(contact) > 20) errors.contact_name = '联系人不能超过 20 个字'
        const phone = digitsOf(this.form.contactPhone)
        if (!PHONE_RE.test(phone)) errors.contact_phone = '请输入 11 位手机号'
      }
      if (!this.form.address.trim()) errors.address = '请在地图上选择养殖场位置'
      else if (!this.form.province.trim()) errors.address = '请在地图上选择养殖场位置'
      else if (!this.form.district.trim()) errors.address = '请在地图上选择养殖场位置'
      else if (!/^\d{6}$/.test(this.form.adcode.trim())) errors.adcode = '请填写 6 位行政区划代码'
      const longitude = toNumber(this.form.longitude)
      const latitude = toNumber(this.form.latitude)
      if (longitude === null || longitude < 73 || longitude > 136) errors.longitude = '请填写正确的经度（73–136）'
      if (latitude === null || latitude < 3 || latitude > 54) errors.latitude = '请填写正确的纬度（3–54）'
      if (this.mode === 'change' && textLength(this.form.remark.trim()) > 100) {
        errors.remark = '变更说明不能超过 100 个字'
      }
      this.formErrors = errors
      if (!this.qualFiles.length) this.qualError = '请至少上传 1 张资质图'
      else if (this.qualFiles.length > 9) this.qualError = '资质图最多上传 9 张'
      else this.qualError = ''
      return Object.keys(errors).length === 0 && !this.qualError
    },
    async submit(): Promise<number | null> {
      if (this.submitting) return null
      if (this.isReadonly) {
        useUiStore().toast('资料变更审核中，审核期间不能再次提交', 'info')
        return null
      }
      if (this.mode === 'change' && !this.changedFields.length) {
        useUiStore().toast('你还没有修改任何内容', 'info')
        return null
      }
      if (this.pendingUploadCount) {
        useUiStore().toast('资质图还在上传，请稍候', 'info')
        return null
      }
      if (this.failedUploadCount) {
        useUiStore().toast('有文件上传失败，请重试或删除后再提交', 'error')
        return null
      }
      if (!this.validate()) {
        useUiStore().toast('请检查表单中标出的字段', 'error')
        return null
      }
      this.submitting = true
      try {
        if (this.mode === 'new') {
          const body: CreateFarmBody = {
            name: this.form.name.trim(),
            main_category_id: this.form.mainCategoryId,
            scale: toNumber(this.form.scale),
            contact_name: this.form.contactName.trim(),
            contact_phone: digitsOf(this.form.contactPhone),
            address: this.form.address.trim(),
            province: this.form.province.trim(),
            city: this.form.city.trim(),
            district: this.form.district.trim(),
            township: this.form.township.trim(),
            adcode: this.form.adcode.trim(),
            longitude: toNumber(this.form.longitude),
            latitude: toNumber(this.form.latitude),
            loc_accuracy: this.form.locAccuracy ?? undefined,
            qualification_file_ids: this.qualIds
          }
          const data = await farmApi.create(body)
          useUiStore().toast('已提交审核，结果会发到消息中心')
          return data.farm_id
        }
        if (this.mode === 'registration') {
          const body: UpdateRegistrationBody = {
            name: this.form.name.trim(),
            main_category_id: this.form.mainCategoryId,
            scale: toNumber(this.form.scale),
            contact_name: this.form.contactName.trim(),
            contact_phone: digitsOf(this.form.contactPhone),
            address: this.form.address.trim(),
            province: this.form.province.trim(),
            city: this.form.city.trim(),
            district: this.form.district.trim(),
            township: this.form.township.trim(),
            adcode: this.form.adcode.trim(),
            longitude: toNumber(this.form.longitude),
            latitude: toNumber(this.form.latitude),
            loc_accuracy: this.form.locAccuracy ?? undefined,
            qualification_file_ids: this.qualIds,
            audit_version: this.audit?.audit_version ?? 0
          }
          const data = await farmApi.updateRegistration(this.farmId, body)
          this.audit = data
          useUiStore().toast('已重新提交审核')
          return data.farm_id || this.farmId
        }
        const body: SubmitFarmChangeBody = {
          name: this.form.name.trim(),
          address: this.form.address.trim(),
          province: this.form.province.trim(),
          city: this.form.city.trim(),
          district: this.form.district.trim(),
          township: this.form.township.trim(),
          adcode: this.form.adcode.trim(),
          longitude: toNumber(this.form.longitude),
          latitude: toNumber(this.form.latitude),
          loc_accuracy: this.form.locAccuracy ?? undefined,
          qualification_file_ids: this.qualIds,
          remark: this.form.remark.trim()
        }
        const data = await farmApi.submitChange(this.farmId, body)
        this.latestChange = data
        useUiStore().toast('已提交资料变更，审核期间旧信息照常有效')
        return this.farmId
      } catch (error) {
        const value = error as ApiError
        if (value?.fieldErrors?.length) {
          const errors: Record<string, string> = {}
          let handled = false
          for (const fieldError of value.fieldErrors) {
            if (fieldError.field === 'qualification_file_ids') {
              this.qualError = fieldError.message
              handled = true
            } else if (fieldError.field in this.form || fieldError.field === 'main_category_id') {
              errors[fieldError.field] = fieldError.message
              handled = true
            }
          }
          if (Object.keys(errors).length) this.formErrors = { ...this.formErrors, ...errors }
          if (!handled) useUiStore().toast(value.message, 'error')
        } else {
          useUiStore().toast(messageOf(error, '提交失败，请稍后重试'), 'error')
        }
        return null
      } finally {
        this.submitting = false
      }
    },
    async addFiles(files: File[] | FileList): Promise<void> {
      if (this.isReadonly) return
      this.qualError = ''
      const incoming = Array.from(files)
      const remaining = Math.max(0, 9 - this.qualFiles.length)
      if (!remaining) {
        useUiStore().toast('最多 9 张，已达到上限', 'info')
        return
      }
      const accepted: File[] = []
      let invalidType = 0
      let tooLarge = 0
      for (const file of incoming) {
        const isImage = /^image\/(jpeg|png)$/i.test(file.type) || /\.(jpe?g|png)$/i.test(file.name)
        if (!isImage) {
          invalidType += 1
          continue
        }
        if (file.size > 5 * 1024 * 1024) {
          tooLarge += 1
          continue
        }
        accepted.push(file)
      }
      if (invalidType) useUiStore().toast('只能上传 JPG 或 PNG 图片', 'error')
      if (tooLarge) useUiStore().toast('这张图片超过 5MB，请换一张', 'error')
      const picked = accepted.slice(0, remaining)
      if (accepted.length > remaining) useUiStore().toast(`最多 9 张，已添加前 ${picked.length} 张`, 'info')
      for (const file of picked) {
        const item: QualItem = {
          key: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
          fileId: null,
          name: file.name,
          size: file.size,
          progress: 0,
          uploading: true,
          failed: false,
          reshoot: false,
          isNew: true,
          raw: file
        }
        this.qualFiles.push(item)
        // 必须通过响应式数组取回代理对象再修改，否则 fileId 不会触发 qualIds/changedFields 等计算属性
        const reactiveItem = this.qualFiles[this.qualFiles.length - 1]
        void this.uploadOne(reactiveItem)
      }
    },
    async uploadOne(item: QualItem): Promise<void> {
      if (!item.raw) return
      item.uploading = true
      item.failed = false
      item.progress = 0
      this.uploading += 1
      try {
        const data = await farmApi.uploadQual(item.raw, (percent) => {
          item.progress = percent
        })
        item.fileId = Number(data.file_id)
        item.url = data.url
        item.thumbUrl = data.thumb_url || data.url
        item.progress = 100
        item.uploading = false
        item.failed = false
        item.raw = undefined
      } catch (error) {
        if (isCancelError(error)) return
        item.uploading = false
        item.failed = true
        item.progress = 0
        useUiStore().toast('上传失败，点击重试', 'error')
      } finally {
        this.uploading = Math.max(0, this.uploading - 1)
      }
    },
    retryUpload(item: QualItem): void {
      if (!item.raw || item.uploading) return
      void this.uploadOne(item)
    },
    removeFile(item: QualItem): void {
      this.qualError = ''
      const index = this.qualFiles.findIndex((file) => file.key === item.key)
      if (index >= 0) this.qualFiles.splice(index, 1)
    }
  }
})

function qualFromVO(file: QualFileVO, index: number): QualItem {
  return {
    key: `server-${file.file_id}-${index}`,
    fileId: file.file_id,
    url: file.url,
    thumbUrl: file.thumb_url || file.url,
    name: `资质图 ${index + 1}`,
    size: file.size || 0,
    progress: 100,
    uploading: false,
    failed: false,
    reshoot: Boolean(file.reshoot),
    isNew: Boolean(file.is_new)
  }
}

function snapshotOfFromRegistration(registration?: RegistrationVO): FormSnapshot | null {
  if (!registration) return null
  const form = emptyForm()
  form.name = registration.name || ''
  form.mainCategoryId = registration.main_category_id ?? null
  form.scale = registration.scale === null || registration.scale === undefined ? '' : String(registration.scale)
  form.contactName = registration.contact_name || ''
  form.contactPhone = formatPhoneInput(registration.contact_phone || '')
  form.address = registration.address || ''
  form.province = registration.province || ''
  form.city = registration.city || ''
  form.district = registration.district || ''
  form.township = registration.township || ''
  form.adcode = registration.adcode || ''
  form.longitude = registration.longitude === undefined || registration.longitude === null ? '' : String(registration.longitude)
  form.latitude = registration.latitude === undefined || registration.latitude === null ? '' : String(registration.latitude)
  form.locAccuracy = registration.loc_accuracy ?? null
  return {
    ...form,
    qualIds: (registration.qualification_files || [])
      .map((file) => file.file_id)
      .filter((id): id is number => typeof id === 'number' && Number.isFinite(id) && id > 0)
      .sort((a, b) => a - b)
  }
}
