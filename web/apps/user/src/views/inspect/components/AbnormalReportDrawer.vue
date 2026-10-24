<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ApiError, formatNumber } from '@zmt/shared'
import {
  createAbnormalReport,
  fetchReportForm,
  fetchSymptoms,
  newRequestKey,
  type MediaItem,
  type ReportFormContext,
  type Symptom
} from '@/api/inspect'
import { useUiStore } from '@/stores/ui'
import { useFarmStore } from '@/stores/farm'
import MediaUpload, { type UploadItem } from './MediaUpload.vue'
import MediaPreviewDialog, { type PreviewItem } from './MediaPreviewDialog.vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    inspectionId?: number
    penId?: number
  }>(),
  {
    inspectionId: 0,
    penId: 0
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [reportId: number]
}>()

const ui = useUiStore()
const farm = useFarmStore()

const loading = ref(false)
const submitting = ref(false)
const context = ref<ReportFormContext | null>(null)
const loadError = ref('')
const penId = ref(0)
const batchId = ref(0)
const sickCount = ref<number | null>(null)
const symptomIds = ref<number[]>([])
const symptoms = ref<Symptom[]>([])
const description = ref('')
const mediaIds = ref<number[]>([])
const mediaKey = ref(0)
const mediaItems = ref<UploadItem[]>([])
const mediaBusy = ref(false)
const mediaFailed = ref(false)
const previewOpen = ref(false)
const previewIndex = ref(0)
const showConfirm = ref(false)
const errors = reactive<Record<string, string>>({})
const dirty = ref(false)
let baseline = ''

const isInspectionSource = computed(() => !!context.value?.inspection_id || !!props.inspectionId)
const penItems = computed(() => (context.value?.pens || []).map((pen) => ({ title: pen.pen_name || '', value: pen.pen_id })))
const selectedPen = computed(() => context.value?.pens.find((pen) => pen.pen_id === penId.value))
const batchItems = computed(() =>
  (selectedPen.value?.batches || []).map((batch) => ({
    title: `${batch.batch_no || '在栏批次'} · 存栏 ${formatNumber(batch.stock_count || 0)} 头`,
    value: batch.batch_id
  }))
)
const selectedBatch = computed(() => selectedPen.value?.batches.find((batch) => batch.batch_id === batchId.value))
const stockLimit = computed(() => Number(selectedBatch.value?.stock_count || 0))
const batchHint = computed(() => {
  if (!selectedPen.value) return '选择栏舍后显示在栏批次'
  if (!selectedPen.value.batches?.length) return '这个栏舍没有在栏批次'
  if (selectedPen.value.batches.length === 1) return '栏舍只有一个在栏批次，已自动选中'
  return `不能超过批次当前存栏 ${formatNumber(stockLimit.value)} 头`
})
const sickHint = computed(() => {
  if (!selectedBatch.value) return '选择批次后显示存栏上限'
  return `不能超过批次当前存栏 ${formatNumber(stockLimit.value)} 头`
})
const subTitle = computed(() => {
  if (isInspectionSource.value) return `${selectedPen.value?.pen_name || ''} · 提交后 AI 诊断`
  return `从快捷登记直接上报 · ${farm.farmName || '当前养殖场'}`
})
const sourceText = computed(() => {
  const prefill = context.value?.prefill
  const registered = context.value?.source_text
  if (!isInspectionSource.value) return ''
  if (prefill?.description) return `已从今天的巡检带入栏舍、批次、异常原因和照片`
  return registered || '已从今天的巡检带入栏舍、批次和照片'
})
const mediaHint = computed(() => {
  const base = '最多 6 个，其中视频 1 段，不超过 30 秒'
  const fromInspection = (context.value?.prefill?.media || []).length
  return `${base}${fromInspection ? ` · 前 ${fromInspection} 个来自巡检` : ''}。图片 JPG / PNG 单张不超过 5MB，视频 MP4 不超过 30MB。`
})
const notifyText = computed(() => {
  const names = context.value?.notify_names || []
  if (!names.length) return '提交后 AI 返回 1–3 个可能病因、紧急程度和处理建议。'
  return `提交后 AI 返回 1–3 个可能病因、紧急程度和处理建议，结果同时通知你、${names.join('和')}。`
})
const selectedSymptomText = computed(() => `可多选 · 已选 ${symptomIds.value.length} 项`)

function reset(): void {
  context.value = null
  loading.value = false
  submitting.value = false
  loadError.value = ''
  penId.value = 0
  batchId.value = 0
  sickCount.value = null
  symptomIds.value = []
  symptoms.value = []
  description.value = ''
  mediaIds.value = []
  mediaKey.value += 1
  mediaItems.value = []
  mediaBusy.value = false
  mediaFailed.value = false
  showConfirm.value = false
  dirty.value = false
  baseline = ''
  Object.keys(errors).forEach((key) => delete errors[key])
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      reset()
      return
    }
    reset()
    void loadForm()
  },
  { immediate: true }
)

watch(
  () => [penId.value, batchId.value, symptomIds.value, description.value, sickCount.value, mediaIds.value],
  () => {
    if (!context.value || !baseline) return
    dirty.value = snapshot() !== baseline
  },
  { deep: true }
)

function snapshot(): string {
  return JSON.stringify({
    penId: penId.value,
    batchId: batchId.value,
    sickCount: sickCount.value,
    symptomIds: symptomIds.value,
    description: description.value,
    mediaIds: mediaIds.value
  })
}

function markBaseline(): void {
  void nextTick(() => {
    baseline = snapshot()
    dirty.value = false
  })
}

async function loadForm(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const ctx = await fetchReportForm({
      inspection_id: props.inspectionId || undefined,
      pen_id: props.penId || undefined
    })
    context.value = ctx
    const prefill = ctx.prefill || {}
    penId.value = Number(prefill.pen_id || ctx.pens[0]?.pen_id || 0)
    const pen = ctx.pens.find((row) => row.pen_id === penId.value)
    batchId.value = Number(prefill.batch_id || pen?.batches?.[0]?.batch_id || 0)
    description.value = prefill.description || ''
    mediaIds.value = (prefill.media || []).map((item) => Number((item as MediaItem).file_id || 0)).filter((id) => id > 0)
    mediaKey.value += 1
    symptoms.value = ctx.symptoms || []
    markBaseline()
  } catch (error) {
    loadError.value = (error as ApiError).message || '上报表单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function onPenSelected(value: unknown): void {
  penId.value = Number(value || 0)
  const pen = context.value?.pens.find((row) => row.pen_id === penId.value)
  const only = pen?.batches?.length === 1 ? pen.batches[0].batch_id : 0
  batchId.value = only
  delete errors.pen
  delete errors.batch
  if (batchId.value) void loadSymptoms(batchId.value)
}

function onBatchSelected(value: unknown): void {
  batchId.value = Number(value || 0)
  delete errors.batch
  if (batchId.value) void loadSymptoms(batchId.value)
  if (sickCount.value && sickCount.value > stockLimit.value) {
    errors.sick_count = `不能超过批次当前存栏 ${formatNumber(stockLimit.value)} 头`
  } else {
    delete errors.sick_count
  }
}

async function loadSymptoms(id: number): Promise<void> {
  try {
    symptoms.value = await fetchSymptoms(id)
  } catch {
    symptoms.value = context.value?.symptoms || []
  }
}

function toggleSymptom(id: number): void {
  const index = symptomIds.value.indexOf(id)
  if (index >= 0) symptomIds.value.splice(index, 1)
  else symptomIds.value.push(id)
  delete errors.symptoms
  if (description.value.trim().length >= 10) delete errors.description
}

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (!penId.value) errors.pen = '请选择栏舍'
  if (!batchId.value) errors.batch = '请选择批次'
  if (!sickCount.value) errors.sick_count = '请填写发病数量'
  else if (Number(sickCount.value) < 1) errors.sick_count = '发病数量至少为 1 头'
  else if (Number(sickCount.value) > stockLimit.value) errors.sick_count = `不能超过批次当前存栏 ${formatNumber(stockLimit.value)} 头`
  if (!symptomIds.value.length && description.value.trim().length < 10) {
    errors.description = '请至少勾选一项症状，或描述不少于 10 个字'
  }
  if (description.value.length > 500) errors.description = '描述不能超过 500 字'
  if (mediaBusy.value) {
    ui.toast('照片和视频还在上传，请稍候', 'info')
    return false
  }
  if (mediaFailed.value || mediaItems.value.some((item) => item.error)) {
    ui.toast('有文件上传失败，请重试或删除后再提交', 'error')
    return false
  }
  return Object.keys(errors).length === 0
}

function scrollToError(): void {
  void nextTick(() => {
    document.querySelector('.zw-drawer .zw-form-item--error, .zw-drawer .report-field-error')?.scrollIntoView({
      block: 'center',
      behavior: 'smooth'
    })
  })
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!validate()) {
    scrollToError()
    return
  }
  submitting.value = true
  try {
    const key = newRequestKey()
    const result = await createAbnormalReport({
      client_request_id: key,
      pen_id: penId.value,
      batch_id: batchId.value,
      inspection_id: props.inspectionId || context.value?.inspection_id || undefined,
      sick_count: Number(sickCount.value || 0),
      symptom_ids: symptomIds.value,
      description: description.value.trim() || undefined,
      media_file_ids: mediaIds.value
    })
    ui.toast('已提交上报，正在诊断')
    emit('created', result.report_id)
    emit('update:modelValue', false)
  } catch (error) {
    const err = error as ApiError
    for (const field of err.fieldErrors) errors[field.field] = field.message
    ui.toast(err.message || '提交没有完成，请重试', 'error')
    scrollToError()
  } finally {
    submitting.value = false
  }
}

function tryClose(): void {
  if (submitting.value) return
  if (dirty.value) {
    showConfirm.value = true
    return
  }
  emit('update:modelValue', false)
}

function onDrawerModel(value: boolean): void {
  if (!value) tryClose()
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
    name: item.name
  }))
)
</script>

<template>
  <zw-drawer :model-value="modelValue" title="异常上报" :sub="subTitle" width="640" @update:model-value="onDrawerModel">
    <div v-if="loading" class="report-loading">
      <v-skeleton-loader type="heading" />
      <v-skeleton-loader type="paragraph@2" />
      <v-skeleton-loader type="list-item-three-line@3" />
    </div>
    <div v-else-if="loadError" class="report-load-error">
      <zw-notice type="warning" title="上报表单加载失败">{{ loadError }}</zw-notice>
      <v-btn variant="outlined" @click="loadForm">重新加载</v-btn>
    </div>
    <template v-else-if="context">
      <div v-if="sourceText" class="report-source">
        <zw-icon name="link" :size="16" />
        <span>{{ sourceText }}</span>
      </div>

      <div class="zw-form-3">
        <zw-form-item label="栏舍" required :error="errors.pen">
          <v-select
            :model-value="penId"
            :items="penItems"
            placeholder="选择栏舍"
            :error="!!errors.pen"
            @update:model-value="onPenSelected"
          />
        </zw-form-item>
        <zw-form-item label="批次" required :error="errors.batch" :hint="batchHint">
          <v-select
            :model-value="batchId"
            :items="batchItems"
            placeholder="选择批次"
            :error="!!errors.batch"
            :disabled="!selectedPen"
            @update:model-value="onBatchSelected"
          />
        </zw-form-item>
        <zw-form-item label="发病数量" required :error="errors.sick_count" :hint="sickHint">
          <v-text-field
            v-model.number="sickCount"
            type="number"
            min="1"
            :max="stockLimit || undefined"
            suffix="头"
            placeholder="输入发病头数"
            :error="!!errors.sick_count"
            @update:model-value="errors.sick_count = ''"
          />
        </zw-form-item>
      </div>

      <div class="report-head">
        <span class="zw-section-title">症状</span>
        <span class="report-count">{{ selectedSymptomText }}</span>
      </div>
      <div class="report-chips">
        <button
          v-for="symptom in symptoms"
          :key="symptom.symptom_id"
          type="button"
          class="report-chip"
          :class="{ on: symptomIds.includes(symptom.symptom_id) }"
          @click="toggleSymptom(symptom.symptom_id)"
        >
          <zw-icon v-if="symptomIds.includes(symptom.symptom_id)" name="check" :size="16" />
          {{ symptom.name }}
        </button>
      </div>
      <div v-if="errors.symptoms" class="report-field-error">{{ errors.symptoms }}</div>

      <div class="report-head">
        <span class="zw-section-title">描述</span>
      </div>
      <v-textarea
        v-model="description"
        placeholder="描述发病情况，例如 12 头排水样稀粪，部分呕吐，采食比昨天明显减少。"
        rows="3"
        counter="500"
        maxlength="500"
        :error="!!errors.description"
        :hint="errors.description || '至少 10 个字，或至少勾选一项症状'"
        persistent-hint
        @update:model-value="errors.description = ''"
      />

      <div class="report-head">
        <span class="zw-section-title">照片和视频</span>
        <span class="report-count num">{{ mediaIds.length }} / {{ context.max_media_count || 6 }}</span>
      </div>
      <MediaUpload
        :key="mediaKey"
        v-model="mediaIds"
        :prefill="context.prefill?.media || []"
        scene="ABNORMAL"
        :max="context.max_media_count || 6"
        :max-video="context.max_video_count || 1"
        :hint="mediaHint"
        @update:busy="mediaBusy = $event"
        @update:items="mediaItems = $event"
        @preview="openPreview"
      />

      <zw-notice type="ai" icon="auto_awesome" class="mt-5">{{ notifyText }}</zw-notice>
    </template>

    <template #foot>
      <template v-if="showConfirm">
        <span class="report-confirm-text">放弃这次上报？填写的内容和照片不会保存。</span>
        <v-btn variant="outlined" @click="showConfirm = false">继续填写</v-btn>
        <v-btn color="error" variant="outlined" @click="discard">放弃</v-btn>
      </template>
      <template v-else>
        <span class="report-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" :disabled="submitting" @click="tryClose">取消</v-btn>
        <v-btn color="primary" prepend-icon="auto_awesome" :loading="submitting" @click="submit">提交并诊断</v-btn>
      </template>
    </template>

    <MediaPreviewDialog v-model="previewOpen" :items="previewItems" :start-index="previewIndex" source-label="异常上报" />
  </zw-drawer>
</template>

<style scoped>
.report-loading { display: flex; flex-direction: column; gap: 12px; }
.report-load-error { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; }
.report-source { display: flex; align-items: center; gap: 6px; margin-bottom: 16px; font-size: 12px; line-height: 20px; color: var(--text-2); }
.report-source .zw-mso { color: var(--primary); }
.report-head { display: flex; align-items: center; justify-content: space-between; margin: 20px 0 8px; }
.report-head .zw-section-title { margin: 0; }
.report-count { font-size: 12px; color: var(--text-3); }
.report-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.report-chip { display: inline-flex; align-items: center; gap: 4px; height: 32px; padding: 0 14px; border: 1px solid var(--control-border); border-radius: var(--radius-sm); background: var(--bg-card); color: var(--text-1); font: inherit; font-size: 14px; cursor: pointer; }
.report-chip:hover { border-color: var(--primary); color: var(--primary); }
.report-chip.on { border-color: var(--primary); background: var(--primary-subtle); color: var(--primary); font-weight: 500; padding-left: 10px; }
.report-field-error { margin-top: 4px; font-size: 12px; line-height: 18px; color: var(--error); }
.report-foot-note { margin-right: auto; font-size: 12px; color: var(--text-3); }
.report-confirm-text { margin-right: auto; font-size: 12px; line-height: 18px; color: var(--on-warning-container); }
</style>
