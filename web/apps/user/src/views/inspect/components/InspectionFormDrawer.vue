<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { dayjs, formatDate, formatNumber, TZ } from '@zmt/shared'
import { useRouter } from 'vue-router'
import { ApiError } from '@zmt/shared'
import {
  backfillInspection,
  fetchFormContext,
  modifyInspection,
  newRequestKey,
  registerInspection,
  type InspectionFormContext,
  type InspectionLocationPayload,
  type InspectionSubmitResult,
  type MediaItem
} from '@/api/inspect'
import { useUiStore } from '@/stores/ui'
import { useFarmStore } from '@/stores/farm'
import MediaUpload, { type UploadItem } from './MediaUpload.vue'
import MediaPreviewDialog, { type PreviewItem } from './MediaPreviewDialog.vue'

export interface PenChoice {
  pen_id: number
  pen_name: string
  statusText?: string
  stock?: number
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    mode?: 'REGISTER' | 'MODIFY' | 'BACKFILL'
    penId?: number
    inspectionId?: number
    inspectDate?: string
    penOptions?: PenChoice[]
    staff?: boolean
  }>(),
  {
    mode: 'REGISTER',
    penId: 0,
    inspectionId: 0,
    inspectDate: '',
    penOptions: () => [],
    staff: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submitted: [result: InspectionSubmitResult]
  report: [inspectionId: number, penId: number]
}>()

const ui = useUiStore()
const farm = useFarmStore()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const context = ref<InspectionFormContext | null>(null)
const loadError = ref('')
const selectedPenId = ref(0)
const selectedDate = ref('')
const selections = reactive<Record<number, number>>({})
const manualMarked = ref(false)
const manualReason = ref('')
const remark = ref('')
const executorId = ref(0)
const mediaIds = ref<number[]>([])
const mediaKey = ref(0)
const mediaBusy = ref(false)
const mediaItems = ref<UploadItem[]>([])
const mediaFailed = computed(() => mediaItems.value.some((item) => item.error))
const previewOpen = ref(false)
const previewIndex = ref(0)
const showConfirm = ref(false)
const errors = reactive<Record<string, string>>({})
const locating = ref(false)
const locationStatus = ref<'NONE' | 'OK' | 'DENIED' | 'FAILED'>('NONE')
const coords = ref<{ latitude: number; longitude: number; accuracy?: number } | null>(null)
const dirty = ref(false)
let baseline = ''
let locateAttempted = false

const targetPenId = computed(() => selectedPenId.value || props.penId)
const penName = computed(() => context.value?.pen?.pen_name || props.penOptions.find((pen) => pen.pen_id === targetPenId.value)?.pen_name || '')
const penCode = computed(() => context.value?.pen?.pen_code || '')
const title = computed(() => (props.mode === 'MODIFY' ? '修改巡检' : props.mode === 'BACKFILL' ? '补录巡检' : '登记巡检'))
const canManualMark = computed(() => !!context.value?.can_manual_mark && !props.staff)
const canChooseExecutor = computed(() => !!context.value?.can_choose_executor && !props.staff)
const templateItems = computed(() => context.value?.template?.items || [])
const isBackfill = computed(() => props.mode === 'BACKFILL')
const isModify = computed(() => props.mode === 'MODIFY')
const alreadyRegistered = computed(() => !!context.value?.already_registered)
const hasCheckAbnormal = computed(() =>
  templateItems.value.some((item) => {
    const optionId = selections[item.item_id]
    const option = item.options.find((row) => row.option_id === optionId)
    return !!option?.is_abnormal
  })
)
const firstAbnormal = computed(() => {
  for (const item of templateItems.value) {
    const optionId = selections[item.item_id]
    const option = item.options.find((row) => row.option_id === optionId)
    if (option?.is_abnormal) return { item: item.item_name, option: option.option_name }
  }
  return null
})
const manualLocked = computed(
  () => hasCheckAbnormal.value || context.value?.abnormal_locked_reason === 'REPORTED'
)
const isReported = computed(
  () => context.value?.abnormal_locked_reason === 'REPORTED' || !!context.value?.record?.report_id
)
const showReportAction = computed(
  () => !isBackfill.value && !isReported.value && (hasCheckAbnormal.value || manualMarked.value)
)
const backfillDates = computed(() => {
  const list = [...(context.value?.backfill_dates || [])]
  if (selectedDate.value && !list.includes(selectedDate.value)) list.unshift(selectedDate.value)
  return list.sort()
})
const backfillDateItems = computed(() =>
  backfillDates.value.map((date) => ({ title: `${formatDate(date)}${date === dayjs().tz(TZ).format('YYYY-MM-DD') ? ' · 今天' : ''}`, value: date }))
)
const penItems = computed(() =>
  props.penOptions.map((pen) => ({
    title: `${pen.pen_name}${pen.statusText ? ` · ${pen.statusText}` : ''}`,
    value: pen.pen_id
  }))
)
const executorItems = computed(() =>
  (context.value?.members || []).map((member) => ({
    title: member.label,
    value: Number(member.value)
  }))
)
const subTitle = computed(() => {
  if (!context.value) return ''
  const parts = [penName.value]
  if (penCode.value) parts.push(penCode.value)
  parts.push(`存栏 ${formatNumber(context.value.stock_count || 0)}`)
  if (isBackfill.value) {
    parts.push(selectedDate.value ? formatDate(selectedDate.value) : '选择补录日期')
  } else {
    parts.push(formatDate(selectedDate.value || context.value.inspect_date))
  }
  return parts.filter(Boolean).join(' · ')
})
const locationText = computed(() => {
  if (locating.value) return '正在定位'
  if (locationStatus.value === 'OK' && coords.value) {
    const distance = distanceToFarm()
    if (distance === null) return '已获得位置'
    const site = distance <= 1000 ? '现场' : '非现场'
    return `${site} · 距养殖场 ${distance >= 1000 ? `${(distance / 1000).toFixed(1)} 千米` : `${formatNumber(distance)} 米`}`
  }
  if (locationStatus.value === 'DENIED') return '无位置 · 未获得位置，不影响登记'
  if (locationStatus.value === 'FAILED') return '无位置 · 定位失败，不影响登记'
  return '未获得位置，不影响登记'
})
const locationTag = computed(() => {
  if (locating.value) return { text: '定位中', type: 'neutral' }
  if (locationStatus.value === 'OK') {
    const distance = distanceToFarm()
    if (distance !== null && distance > 1000) return { text: '非现场', type: 'warning' }
    return { text: '现场', type: 'neutral' }
  }
  if (locationStatus.value === 'NONE') return { text: '未定位', type: 'neutral' }
  return { text: '无位置', type: 'neutral' }
})
const registerTime = computed(() => {
  if (isBackfill.value) return '补录'
  if (context.value?.record?.registered_at) return context.value.record.registered_at.slice(11, 16)
  return dayjs().tz(TZ).format('HH:mm')
})
const reporterText = computed(() => {
  const name = context.value?.registrant?.name || ''
  if (isBackfill.value) return `${name} · 代登记`
  if (canChooseExecutor.value && executorId.value && executorId.value !== context.value?.default_executor_id) return `${name} · 代登记`
  return `${name} · 自动记录`
})
const registerHint = computed(() => {
  const date = context.value?.record?.registered_at
  if (!isModify.value || !date) return ''
  return `${formatDate(context.value?.record?.inspect_date || selectedDate.value)} 已由${context.value?.record?.registrant?.name || '他人'}登记，本次保存会修改这条记录。`
})
const alreadyText = computed(() => {
  const info = context.value?.already_registered
  if (!info) return ''
  return `${info.registrant_name || '他人'} 在 ${info.registered_at?.slice(11, 16) || ''} 已登记这条记录，本次保存会修改它。`
})
const abnormalNotice = computed(() => {
  if (isBackfill.value) {
    const text = hasCheckAbnormal.value && firstAbnormal.value
      ? `${firstAbnormal.value.item}为“${firstAbnormal.value.option}”，已自动标为异常。`
      : manualMarked.value
        ? '已手动标为异常。'
        : ''
    if (!text) return ''
    return `${text}补录记录不能转上报，如需上报请从快捷登记进入异常上报。`
  }
  if (hasCheckAbnormal.value && firstAbnormal.value) {
    return `${firstAbnormal.value.item}为“${firstAbnormal.value.option}”，已自动标为异常。点“转上报”会先保存本次巡检，再带入栏舍、批次和照片。`
  }
  if (manualMarked.value) {
    return '已手动标为异常。点“转上报”会先保存本次巡检，再带入栏舍、批次、异常原因和照片。'
  }
  return ''
})
const mediaHint = '最多 6 个，其中视频 1 段，不超过 30 秒。图片 JPG / PNG 单张不超过 5MB，视频 MP4 不超过 30MB。'

function reset(): void {
  loading.value = false
  submitting.value = false
  context.value = null
  loadError.value = ''
  selectedPenId.value = props.penId
  selectedDate.value = props.inspectDate || ''
  Object.keys(selections).forEach((key) => delete selections[Number(key)])
  manualMarked.value = false
  manualReason.value = ''
  remark.value = ''
  executorId.value = 0
  mediaIds.value = []
  mediaKey.value += 1
  mediaItems.value = []
  mediaBusy.value = false
  Object.keys(errors).forEach((key) => delete errors[key])
  showConfirm.value = false
  locationStatus.value = 'NONE'
  coords.value = null
  locating.value = false
  locateAttempted = false
  dirty.value = false
  baseline = ''
}

function clearErrors(): void {
  Object.keys(errors).forEach((key) => delete errors[key])
  loadError.value = ''
}

function initFromContext(ctx: InspectionFormContext): void {
  context.value = ctx
  selectedPenId.value = ctx.pen?.pen_id || selectedPenId.value
  selectedDate.value = ctx.inspect_date || selectedDate.value
  if (isBackfill.value && ctx.backfill_dates?.length && !ctx.backfill_dates.includes(selectedDate.value)) {
    selectedDate.value = ctx.backfill_dates[ctx.backfill_dates.length - 1]
  }
  if (isBackfill.value && !selectedDate.value && ctx.backfill_dates?.length) {
    selectedDate.value = ctx.backfill_dates[ctx.backfill_dates.length - 1]
  }
  Object.keys(selections).forEach((key) => delete selections[Number(key)])
  for (const result of ctx.record?.item_results || []) {
    selections[result.item_id] = result.option_id
  }
  manualMarked.value = !!ctx.record?.is_manual_marked || ctx.abnormal_locked_reason === 'REPORTED'
  manualReason.value = ctx.record?.manual_reason || ''
  remark.value = ctx.record?.remark || ''
  executorId.value = ctx.record?.executor?.user_id || ctx.default_executor_id || 0
  const prefillMedia = (ctx.record?.media || []) as MediaItem[]
  mediaIds.value = prefillMedia.map((item) => Number(item.file_id || 0)).filter((id) => id > 0)
  mediaKey.value += 1
  markBaseline()
  if (!locateAttempted) locationStatus.value = 'NONE'
}

function markBaseline(): void {
  void nextTick(() => {
    baseline = snapshot()
    dirty.value = false
  })
}

function snapshot(): string {
  return JSON.stringify({
    selections,
    manualMarked: manualMarked.value,
    manualReason: manualReason.value,
    remark: remark.value,
    executorId: executorId.value,
    date: selectedDate.value,
    mediaIds: mediaIds.value
  })
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      reset()
      return
    }
    reset()
    if (props.penId) void loadContext(props.penId)
  },
  { immediate: true }
)

watch(
  () => props.penId,
  (penId) => {
    if (!props.modelValue || !penId || penId === context.value?.pen?.pen_id) return
    selectedPenId.value = penId
    void loadContext(penId)
  }
)

watch(
  () => [selections, manualMarked.value, manualReason.value, remark.value, executorId.value, selectedDate.value, mediaIds.value],
  () => {
    if (!context.value || !baseline) return
    dirty.value = snapshot() !== baseline
  },
  { deep: true }
)

async function loadContext(penId: number, date?: string): Promise<void> {
  if (!penId) return
  loading.value = true
  loadError.value = ''
  clearErrors()
  try {
    const ctx = await fetchFormContext({
      pen_id: penId,
      mode: props.mode,
      inspect_date: props.mode === 'BACKFILL' ? date || selectedDate.value || props.inspectDate || undefined : undefined,
      inspection_id: props.mode === 'MODIFY' ? props.inspectionId : undefined
    })
    initFromContext(ctx)
  } catch (error) {
    const err = error as ApiError
    loadError.value = err.message || '表单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function onPenChange(value: number): void {
  if (isModify.value) return
  selectedPenId.value = Number(value || 0)
  context.value = null
  if (selectedPenId.value) void loadContext(selectedPenId.value)
}

function onDateChange(value: string): void {
  if (!value || value === selectedDate.value) return
  selectedDate.value = value
  if (selectedPenId.value) void loadContext(selectedPenId.value, value)
}

function chooseOption(itemId: number, optionId: number): void {
  if (submitting.value) return
  selections[itemId] = optionId
  delete errors[`item-${itemId}`]
}

function toggleManual(value: boolean | null): void {
  if (submitting.value) return
  if (manualLocked.value) {
    ui.toast(
      context.value?.abnormal_locked_reason === 'REPORTED'
        ? '已转上报，不能取消异常标记'
        : '检查结果有异常项，不能取消异常标记',
      'error'
    )
    return
  }
  manualMarked.value = !!value
  delete errors.manual_reason
}

function allowLocation(): void {
  if (locating.value) return
  locateAttempted = true
  if (!navigator.geolocation) {
    locationStatus.value = 'FAILED'
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      coords.value = {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracy: position.coords.accuracy
      }
      locationStatus.value = 'OK'
      locating.value = false
    },
    (error) => {
      locationStatus.value = error.code === 1 ? 'DENIED' : 'FAILED'
      locating.value = false
      ui.toast('未获得位置，不影响登记', 'info')
    },
    { enableHighAccuracy: true, timeout: 10000 }
  )
}

function distanceToFarm(): number | null {
  const lat = context.value?.farm_latitude
  const lng = context.value?.farm_longitude
  if (!coords.value || lat === null || lat === undefined || lng === null || lng === undefined) return null
  const rad = (value: number): number => (value * Math.PI) / 180
  const dLat = rad(coords.value.latitude - lat)
  const dLon = rad(coords.value.longitude - lng)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(lat)) * Math.cos(rad(coords.value.latitude)) * Math.sin(dLon / 2) ** 2
  return Math.round(6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)))
}

function locationPayload(): InspectionLocationPayload {
  if (locationStatus.value === 'OK' && coords.value) {
    return {
      latitude: coords.value.latitude,
      longitude: coords.value.longitude,
      accuracy_m: coords.value.accuracy,
      client_status: 'OK'
    }
  }
  if (locationStatus.value === 'FAILED') return { client_status: 'FAILED' }
  return { client_status: 'DENIED' }
}

function validate(): boolean {
  clearErrors()
  if (!targetPenId.value) errors.pen = '请选择栏舍'
  if (!context.value) {
    loadError.value = '请先加载巡检表单'
    return false
  }
  if (isBackfill.value) {
    if (!selectedDate.value) errors.date = '请选择补录日期'
    if (!executorId.value) errors.executor = '请选择实际执行人'
  }
  for (const item of templateItems.value) {
    if (!selections[item.item_id]) errors[`item-${item.item_id}`] = `请选择${item.item_name}`
  }
  if (manualMarked.value && !hasCheckAbnormal.value) {
    const reason = manualReason.value.trim()
    if (!reason) errors.manual_reason = '请填写异常原因'
    else if (reason.length > 100) errors.manual_reason = '异常原因不能超过 100 字'
  }
  if (remark.value.length > 200) errors.remark = '说明不能超过 200 字'
  if (mediaBusy.value) {
    ui.toast('照片和视频还在上传，请稍候', 'info')
    return false
  }
  if (mediaFailed.value) {
    ui.toast('有文件上传失败，请重试或删除后再提交', 'error')
    return false
  }
  return Object.keys(errors).length === 0
}

function scrollToError(): void {
  void nextTick(() => {
    const target = document.querySelector('.zw-drawer .zw-form-item--error, .zw-drawer .insp-drawer-error')
    target?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  })
}

function buildPayload(): {
  item_results: Array<{ item_id: number; option_id: number }>
  is_manual_marked: boolean
  manual_reason?: string
  remark?: string
  executor_id?: number
  location: InspectionLocationPayload
  media_file_ids: number[]
} {
  const itemResults = templateItems.value
    .filter((item) => selections[item.item_id])
    .map((item) => ({ item_id: item.item_id, option_id: selections[item.item_id] }))
  return {
    item_results: itemResults,
    is_manual_marked: manualMarked.value,
    manual_reason: manualMarked.value ? manualReason.value.trim() || undefined : undefined,
    remark: remark.value.trim() || undefined,
    executor_id: canChooseExecutor.value ? executorId.value : undefined,
    location: locationPayload(),
    media_file_ids: mediaIds.value
  }
}

async function submit(thenReport = false): Promise<void> {
  if (submitting.value) return
  if (!validate()) {
    scrollToError()
    return
  }
  submitting.value = true
  const key = newRequestKey()
  try {
    const base = buildPayload()
    let result: InspectionSubmitResult
    if (isBackfill.value) {
      result = await backfillInspection({
        pen_id: targetPenId.value,
        inspect_date: selectedDate.value,
        executor_id: executorId.value,
        client_request_id: key,
        ...base
      })
      ui.toast(`已补录 ${penName.value} ${formatDate(selectedDate.value)} 的巡检`)
    } else if (isModify.value || alreadyRegistered.value) {
      const inspectionId = props.inspectionId || context.value?.record?.inspection_id || context.value?.already_registered?.inspection_id || 0
      result = await modifyInspection(inspectionId, {
        pen_id: targetPenId.value,
        client_request_id: key,
        version: context.value?.record?.version ?? 0,
        ...base
      })
      ui.toast('已保存修改')
    } else {
      result = await registerInspection({
        pen_id: targetPenId.value,
        client_request_id: key,
        ...base
      })
      ui.toast(result.is_abnormal ? `已登记巡检，${penName.value}已标为异常` : `已登记 ${penName.value}的巡检`)
    }
    emit('submitted', result)
    if (thenReport) emit('report', result.inspection_id, targetPenId.value)
    emit('update:modelValue', false)
  } catch (error) {
    const err = error as ApiError
    for (const field of err.fieldErrors) {
      errors[field.field] = field.message
    }
    if (err.code === 40903) {
      loadError.value = err.message
    } else {
      ui.toast(err.message || '提交没有完成，请重试', 'error')
    }
    scrollToError()
  } finally {
    submitting.value = false
  }
}

function tryClose(): void {
  if (submitting.value) return
  if (dirty.value || mediaBusy.value || mediaFailed.value) {
    showConfirm.value = true
    return
  }
  emit('update:modelValue', false)
}

function discard(): void {
  showConfirm.value = false
  emit('update:modelValue', false)
}

function openPreview(index: number, items: UploadItem[]): void {
  mediaItems.value = items
  previewIndex.value = index
  previewOpen.value = true
}

const previewItems = computed<PreviewItem[]>(() =>
  mediaItems.value.map((item) => ({
    url: item.previewUrl || item.url,
    video: item.video,
    duration: item.duration,
    name: item.name,
    shotAt: '',
    shotBy: ''
  }))
)

function goDeath(): void {
  const batchId = context.value?.batches?.[0]?.batch_id
  void router.push({
    name: 'WU-L05',
    query: { action: 'create', tab: 'death', ...(batchId ? { batchId: String(batchId) } : {}) }
  })
}

function onDrawerModel(value: boolean): void {
  if (!value) tryClose()
}

function onPenSelected(value: unknown): void {
  onPenChange(Number(value || 0))
}

function onDateSelected(value: unknown): void {
  onDateChange(String(value || ''))
}
</script>

<template>
  <zw-drawer :model-value="modelValue" :title="title" :sub="subTitle" width="560" @update:model-value="onDrawerModel">
    <div v-if="loading" class="insp-drawer-loading">
      <v-skeleton-loader type="heading" />
      <v-skeleton-loader type="paragraph@3" />
      <v-skeleton-loader type="list-item-three-line@3" />
    </div>
    <div v-else-if="loadError" class="insp-drawer-error">
      <zw-notice type="warning" title="表单加载失败">{{ loadError }}</zw-notice>
      <v-btn variant="outlined" @click="targetPenId && loadContext(targetPenId)">重新加载</v-btn>
    </div>
    <template v-else>
      <zw-notice v-if="isBackfill" type="info" class="mb-4">
        只能补录前 3 天内的漏巡，提交后标为“补录”。超过 3 天的记录需要提交修改申请。
      </zw-notice>
      <zw-notice v-else-if="alreadyRegistered" type="info" class="mb-4">
        {{ alreadyText }}
      </zw-notice>
      <zw-notice v-else-if="registerHint" type="info" class="mb-4">
        {{ registerHint }}
      </zw-notice>
      <zw-notice v-else-if="context?.deadline_passed && !isModify" type="warning" class="mb-4">
        已过今天的截止时间 {{ context?.deadline_time }}，提交后标为“超时”。
      </zw-notice>

      <div v-if="!context" class="zw-form">
        <zw-form-item label="栏舍" required :error="errors.pen">
          <v-select
            :model-value="selectedPenId"
            :items="penItems"
            placeholder="选择要巡检的栏舍"
            :error="!!errors.pen"
            @update:model-value="onPenSelected"
          />
        </zw-form-item>
        <div class="insp-drawer-tip">选择栏舍后加载巡检项目和登记信息。</div>
      </div>

      <template v-else>
        <div class="zw-form-2">
          <zw-form-item label="栏舍" required :error="errors.pen">
            <v-select
              :model-value="selectedPenId"
              :items="penItems"
              :disabled="isModify"
              :error="!!errors.pen"
              @update:model-value="onPenSelected"
            />
          </zw-form-item>
          <zw-form-item v-if="isBackfill" label="补录日期" required :error="errors.date" :hint="context.backfill_range?.from ? `限 ${formatDate(context.backfill_range.from)}–${formatDate(context.backfill_range.to)}` : ''">
            <v-select
              :model-value="selectedDate"
              :items="backfillDateItems"
              :error="!!errors.date"
              @update:model-value="onDateSelected"
            />
          </zw-form-item>
          <zw-form-item
            v-else-if="canChooseExecutor"
            label="实际执行人"
            required
            :error="errors.executor"
            hint="代登记时必选"
          >
            <v-select
              v-model="executorId"
              :items="executorItems"
              :error="!!errors.executor"
            />
          </zw-form-item>
          <zw-form-item v-else label="巡检对象">
            <div class="zw-readonly">{{ penName }} · {{ formatNumber(context.stock_count || 0) }} {{ farm.countUnit }}</div>
          </zw-form-item>
        </div>

        <div class="insp-info">
          <div class="insp-info-cell">
            <zw-icon name="person" :size="18" />
            <div>
              <div class="insp-info-label">登记人</div>
              <div class="insp-info-value">{{ reporterText }}</div>
            </div>
          </div>
          <div class="insp-info-cell insp-info-cell--wide">
            <zw-icon name="location_on" :size="18" />
            <div>
              <div class="insp-info-label">位置</div>
              <div class="insp-info-value">
                <zw-tag :type="locationTag.type" size="small">{{ locationTag.text }}</zw-tag>
                <span class="insp-info-sub">{{ locationText }}</span>
                <a v-if="locationStatus === 'NONE' && !locating" class="insp-info-link" @click="allowLocation">允许定位</a>
              </div>
            </div>
          </div>
          <div class="insp-info-cell">
            <zw-icon :name="isBackfill ? 'history' : 'schedule'" :size="18" />
            <div>
              <div class="insp-info-label">{{ isBackfill ? '记录标注' : '登记时间' }}</div>
              <div class="insp-info-value">
                <zw-tag v-if="isBackfill" size="small">补录</zw-tag>
                <span v-else class="num">{{ registerTime }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="insp-section-head">
          <span class="zw-section-title">巡检项目</span>
          <span class="insp-section-hint">每项必选</span>
        </div>
        <div class="insp-items">
          <div v-for="item in templateItems" :key="item.item_id" class="insp-item">
            <div class="insp-item-name">
              <span>{{ item.item_name }}</span>
              <span v-if="selections[item.item_id] && item.options.find((option) => option.option_id === selections[item.item_id])?.is_abnormal" class="insp-item-abn">
                <zw-icon name="error" :size="14" fill />异常
              </span>
            </div>
            <div class="insp-opts">
              <button
                v-for="option in item.options"
                :key="option.option_id"
                type="button"
                class="insp-opt"
                :class="{
                  on: selections[item.item_id] === option.option_id,
                  'on-abn': selections[item.item_id] === option.option_id && option.is_abnormal
                }"
                @click="chooseOption(item.item_id, option.option_id)"
              >
                <zw-icon v-if="selections[item.item_id] === option.option_id" name="check" :size="16" />
                {{ option.option_name }}
              </button>
            </div>
            <div v-if="errors[`item-${item.item_id}`]" class="insp-item-error">{{ errors[`item-${item.item_id}`] }}</div>
          </div>
        </div>

        <div v-if="canManualMark" class="insp-mark" :class="{ 'insp-mark--on': manualMarked || manualLocked }">
          <div class="insp-mark-head" @click="!manualLocked && toggleManual(!manualMarked)">
            <span class="zw-ico-tile" :class="manualMarked || manualLocked ? 'zw-ico-tile--error' : ''">
              <zw-icon name="flag" :size="18" :fill="manualMarked || manualLocked" />
            </span>
            <div class="insp-mark-main">
              <div class="insp-mark-title">标记为异常</div>
              <div class="insp-mark-sub">
                {{ manualLocked
                  ? context?.abnormal_locked_reason === 'REPORTED'
                    ? '已转上报，不能取消异常标记'
                    : '已按检查结果自动标为异常'
                  : manualMarked
                    ? '填写异常原因后，栏舍标为异常'
                    : '各项都正常但你发现其他问题时打开' }}
              </div>
            </div>
            <span class="insp-mark-switch" @click.stop>
              <v-switch
                :model-value="manualMarked || manualLocked"
                :disabled="manualLocked"
                :color="manualMarked || manualLocked ? 'error' : 'primary'"
                @update:model-value="toggleManual"
              />
            </span>
          </div>
          <div v-if="manualMarked || manualLocked" class="insp-mark-body">
            <v-textarea
              v-model="manualReason"
              placeholder="写明你发现的问题，例如个别仔猪咳嗽"
              rows="2"
              counter="100"
              maxlength="100"
              :error="!!errors.manual_reason"
              :disabled="hasCheckAbnormal || context?.abnormal_locked_reason === 'REPORTED'"
            />
            <div v-if="errors.manual_reason" class="insp-item-error">{{ errors.manual_reason }}</div>
          </div>
        </div>

        <zw-notice v-if="abnormalNotice" type="warning" class="mt-3">{{ abnormalNotice }}</zw-notice>

        <div class="insp-section-head">
          <span class="zw-section-title">说明</span>
          <span class="insp-section-hint">选填</span>
        </div>
        <v-textarea
          v-model="remark"
          placeholder="补充说明当天巡检的情况"
          rows="2"
          counter="200"
          maxlength="200"
          :error="!!errors.remark"
        />
        <div v-if="errors.remark" class="insp-item-error">{{ errors.remark }}</div>

        <div class="insp-section-head">
          <span class="zw-section-title">照片和视频</span>
          <span class="insp-section-hint num">{{ mediaIds.length }} / 6</span>
        </div>
        <MediaUpload
          :key="mediaKey"
          v-model="mediaIds"
          :prefill="context?.record?.media || []"
          scene="INSPECTION"
          :max="6"
          :max-video="1"
          :hint="mediaHint"
          @update:busy="mediaBusy = $event"
          @update:items="mediaItems = $event"
          @preview="openPreview"
        />

        <div class="insp-death">
          <span><zw-icon name="info" :size="14" />发现死淘请另行登记，巡检不扣减存栏</span>
          <a @click="goDeath">去登记死淘</a>
        </div>
      </template>
    </template>

    <template #foot>
      <template v-if="showConfirm">
        <span class="insp-confirm-text">放弃这次{{ isBackfill ? '补录' : '登记' }}？已选择的结果和照片不会保存。</span>
        <v-btn variant="outlined" @click="showConfirm = false">继续填写</v-btn>
        <v-btn color="error" variant="outlined" @click="discard">放弃</v-btn>
      </template>
      <template v-else>
        <span class="insp-foot-note">{{ isBackfill ? '补录记录不能转上报' : '带 * 为必填' }}</span>
        <v-btn variant="outlined" :disabled="submitting" @click="tryClose">取消</v-btn>
        <v-btn
          v-if="showReportAction && !isBackfill"
          variant="tonal"
          color="primary"
          prepend-icon="report"
          :disabled="submitting"
          @click="submit(true)"
        >
          转上报
        </v-btn>
        <v-btn color="primary" :loading="submitting" @click="submit(false)">
          {{ isModify || alreadyRegistered ? '保存修改' : isBackfill ? '提交补录' : '提交' }}
        </v-btn>
      </template>
    </template>

    <MediaPreviewDialog v-model="previewOpen" :items="previewItems" :start-index="previewIndex" source-label="巡检" />
  </zw-drawer>
</template>

<style scoped>
.insp-drawer-loading { display: flex; flex-direction: column; gap: 12px; }
.insp-drawer-error { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; }
.insp-drawer-tip { font-size: 12px; line-height: 18px; color: var(--text-3); }
.insp-info { display: grid; grid-template-columns: 1fr 1.4fr 1fr; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--fill-1); margin: 16px 0; }
.insp-info-cell { display: flex; align-items: flex-start; gap: 8px; padding: 10px 12px; min-width: 0; }
.insp-info-cell + .insp-info-cell { border-left: 1px solid var(--line-1); }
.insp-info-cell .zw-mso { color: var(--text-2); margin-top: 2px; }
.insp-info-label { font-size: 12px; line-height: 18px; color: var(--text-3); }
.insp-info-value { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; font-size: 13px; line-height: 20px; color: var(--text-1); }
.insp-info-sub { font-size: 12px; color: var(--text-2); }
.insp-info-link { font-size: 12px; color: var(--primary); cursor: pointer; }
.insp-section-head { display: flex; align-items: center; justify-content: space-between; margin: 20px 0 8px; }
.insp-section-head .zw-section-title { margin: 0; }
.insp-section-hint { font-size: 12px; color: var(--text-3); }
.insp-items { border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.insp-item { display: flex; align-items: center; gap: 12px; min-height: 52px; padding: 8px 16px; flex-wrap: wrap; }
.insp-item + .insp-item { border-top: 1px solid var(--line-1); }
.insp-item-name { width: 84px; flex: 0 0 84px; font-size: 14px; color: var(--text-1); display: flex; flex-direction: column; gap: 2px; }
.insp-item-abn { display: inline-flex; align-items: center; gap: 2px; font-size: 12px; color: var(--error); }
.insp-opts { display: flex; flex-wrap: wrap; gap: 8px; flex: 1; }
.insp-opt { display: inline-flex; align-items: center; gap: 4px; height: 32px; padding: 0 14px; border: 1px solid var(--control-border); border-radius: var(--radius-sm); background: var(--bg-card); color: var(--text-1); font: inherit; font-size: 14px; cursor: pointer; }
.insp-opt:hover { border-color: var(--primary); color: var(--primary); }
.insp-opt.on { border-color: var(--primary); background: var(--primary-subtle); color: var(--primary); font-weight: 500; padding-left: 10px; }
.insp-opt.on-abn { border-color: var(--error); background: var(--error-container); color: var(--on-error-container); }
.insp-item-error { width: 100%; margin-top: 2px; font-size: 12px; line-height: 18px; color: var(--error); }
.insp-mark { border: 1px solid var(--line-1); border-radius: var(--radius-sm); margin-top: 12px; background: var(--bg-card); }
.insp-mark--on { border-color: var(--error-container); }
.insp-mark-head { display: flex; align-items: center; gap: 12px; padding: 10px 16px; cursor: pointer; }
.insp-mark-main { flex: 1; min-width: 0; }
.insp-mark-switch { display: inline-flex; align-items: center; }
.insp-mark-title { font-size: 14px; font-weight: 500; color: var(--text-1); }
.insp-mark-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.insp-mark-body { border-top: 1px solid var(--line-1); padding: 12px 16px; }
.insp-death { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 14px; font-size: 12px; color: var(--text-3); }
.insp-death span { display: inline-flex; align-items: center; gap: 4px; }
.insp-death a { color: var(--primary); cursor: pointer; }
.insp-foot-note { margin-right: auto; font-size: 12px; color: var(--text-3); }
.insp-confirm-text { margin-right: auto; font-size: 12px; line-height: 18px; color: var(--on-warning-container); }
</style>
