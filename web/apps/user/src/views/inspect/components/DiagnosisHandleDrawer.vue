<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, formatNumber } from '@zmt/shared'
import type { DiagnosisDetail, HandleSavePayload, HandleSaveResult } from '@/api/inspect'
import { useDiagnosisStore } from '@/stores/inspect/diagnosis'
import { useUiStore } from '@/stores/ui'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    detail: DiagnosisDetail | null
  }>(),
  {}
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [result: HandleSaveResult]
}>()

const ui = useUiStore()
const router = useRouter()
const store = useDiagnosisStore()

const saving = ref(false)
const errors = reactive<Record<string, string>>({})
const form = reactive({
  diagnostician: '',
  treatment: '',
  usedDrug: null as boolean | null,
  curedCount: 0,
  culledCount: 0,
  aiMatch: null as 'MATCH' | 'NOT_MATCH' | null
})

const report = computed(() => props.detail?.report)
const handle = computed(() => props.detail?.handle)
const sickCount = computed(() => Number(report.value?.sick_count || 0))
const total = computed(() => Number(form.curedCount || 0) + Number(form.culledCount || 0))
const percent = computed(() => (sickCount.value ? Math.min(100, (total.value / sickCount.value) * 100) : 0))
const willClose = computed(() => sickCount.value > 0 && total.value === sickCount.value)
const closed = computed(() => props.detail?.status === 'CLOSED')
const title = computed(() => (handle.value ? '修改处理结果' : '登记处理结果'))
const subTitle = computed(() => {
  const parts = [report.value?.pen_name || '', report.value?.batch_no || '']
  parts.push(`发病 ${formatNumber(sickCount.value)} 头`)
  parts.push('只有所有者和场长可以登记')
  return parts.filter(Boolean).join(' · ')
})
const hasDiagnosis = computed(() => !!props.detail?.diagnosis)
const aiCause = computed(() => props.detail?.diagnosis?.causes?.[0]?.disease_name || '暂无 AI 诊断')
const urgency = computed(() => props.detail?.diagnosis?.urgency_text || '')
const urgencyType = computed(() => {
  if (props.detail?.diagnosis?.urgency === 'URGENT') return 'error'
  if (props.detail?.diagnosis?.urgency === 'ATTENTION') return 'warning'
  return 'neutral'
})
const convertHint = computed(() => {
  if (handle.value?.medication_converted) return '已转为用药记录'
  return '先保存处理结果，再打开登记用药，自动带入栏舍和批次，在用药记录中填写药品和用量'
})
const closeHint = computed(() => {
  if (!willClose.value) return ''
  return `治愈和死淘合计已达到发病数量 ${formatNumber(sickCount.value)} 头，保存后记录自动结案，不能再修改。`
})
const matchOptions: Array<{ label: string; value: 'MATCH' | 'NOT_MATCH' }> = [
  { label: '相符', value: 'MATCH' },
  { label: '不相符', value: 'NOT_MATCH' }
]

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const current = props.detail?.handle
    form.diagnostician = current?.diagnostician || ''
    form.treatment = current?.treatment || ''
    form.usedDrug = current ? !!current.used_drug : null
    form.curedCount = Number(current?.cured_count || 0)
    form.culledCount = Number(current?.culled_count || 0)
    form.aiMatch = current?.ai_match === 'MATCH' || current?.ai_match === 'NOT_MATCH' ? current.ai_match : null
    Object.keys(errors).forEach((key) => delete errors[key])
  },
  { immediate: true }
)

function onDrawerModel(value: boolean): void {
  if (!saving.value) emit('update:modelValue', value)
}

function chooseMatch(value: 'MATCH' | 'NOT_MATCH'): void {
  form.aiMatch = value
  delete errors.ai_match
}

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  const diagnostician = form.diagnostician.trim()
  if (!diagnostician) errors.diagnostician = '请填写诊断人'
  else if (diagnostician.length > 20) errors.diagnostician = '诊断人不能超过 20 个字'
  const treatment = form.treatment.trim()
  if (!treatment) errors.treatment = '请填写处理方式'
  else if (treatment.length > 200) errors.treatment = '处理方式不能超过 200 个字'
  if (form.usedDrug === null) errors.used_drug = '请选择是否使用药品'
  if (hasDiagnosis.value && !form.aiMatch) errors.ai_match = '请选择 AI 诊断是否相符'
  if (Number(form.curedCount) < 0 || Number(form.culledCount) < 0) errors.count = '治愈和死淘数量不能为负数'
  else if (total.value > sickCount.value) errors.count = `治愈和死淘合计不能超过发病数量 ${formatNumber(sickCount.value)} 头`
  return Object.keys(errors).length === 0
}

async function save(toMedication = false): Promise<void> {
  if (saving.value) return
  if (!validate()) return
  saving.value = true
  try {
    const payload: HandleSavePayload = {
      diagnostician: form.diagnostician.trim(),
      treatment: form.treatment.trim(),
      used_drug: !!form.usedDrug,
      cured_count: Number(form.curedCount || 0),
      culled_count: Number(form.culledCount || 0),
      ai_match: hasDiagnosis.value ? (form.aiMatch || 'MATCH') : 'NONE',
      confirm_close: willClose.value,
      to_medication: toMedication,
      version: handle.value?.version ?? 0
    }
    const result = await store.saveHandle(payload)
    if (!result) return
    emit('saved', result)
    emit('update:modelValue', false)
    const deathPenId = result.death_prompt?.pen_id || report.value?.pen_id
    const deathBatchId = result.death_prompt?.batch_id || report.value?.batch_id
    if (toMedication) {
      ui.toast('已保存处理结果，正在打开用药登记')
      const prefill = result.medication_prefill
      void router.push({
        name: 'WU-D02',
        query: {
          action: 'create',
          penId: String(prefill?.pen_id || report.value?.pen_id || ''),
          batchId: String(prefill?.batch_id || report.value?.batch_id || ''),
          reportId: String(prefill?.report_id || props.detail?.report_id || ''),
          diagnosisId: String(prefill?.diagnosis_id || props.detail?.diagnosis?.diagnosis_id || ''),
          reason: prefill?.reason || ''
        }
      })
    } else if (result.closed) {
      ui.toast('已保存处理结果，记录已结案')
    } else if (Number(result.culled_count || 0) > 0 && deathBatchId) {
      ui.toast('已保存处理结果。死淘需另行登记', 'success', '去登记', () => {
        void router.push({
          name: 'WU-L05',
          query: { action: 'create', tab: 'death', batchId: String(deathBatchId), ...(deathPenId ? { penId: String(deathPenId) } : {}) }
        })
      })
    } else {
      ui.toast('已保存处理结果')
    }
  } catch (error) {
    const err = error as ApiError
    for (const field of err.fieldErrors) errors[field.field] = field.message
    if (err.code === 40902) errors.count = err.message
    else ui.toast(err.message || '保存失败，请重试', 'error')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <zw-drawer
    :model-value="modelValue"
    :title="title"
    :sub="subTitle"
    width="560"
    @update:model-value="onDrawerModel"
  >
    <div class="handle-summary">
      <span class="zw-ico-tile zw-ico-tile--ai"><zw-icon name="auto_awesome" :size="18" fill /></span>
      <div class="handle-summary-main">
        <div class="handle-summary-title">AI：{{ aiCause }}</div>
        <div class="handle-summary-sub">
          {{ report?.reporter?.name || '' }} {{ report?.reported_at?.slice(5, 16) || '' }} 上报<template v-if="report?.symptom_names?.length"> · {{ report.symptom_names.join('、') }}</template>
        </div>
      </div>
      <zw-tag v-if="urgency" :type="urgencyType" size="small">{{ urgency }}</zw-tag>
    </div>

    <zw-notice v-if="closed" type="info" class="mb-4" icon="lock">已结案，处理结果不能再修改</zw-notice>

    <div class="zw-form">
      <zw-form-item label="诊断人" required :error="errors.diagnostician" hint="填写实际做出诊断的人，最多 20 字">
        <v-text-field
          v-model="form.diagnostician"
          placeholder="例如 赵磊（执业兽医）"
          maxlength="20"
          :disabled="closed"
          :error="!!errors.diagnostician"
        />
      </zw-form-item>
      <zw-form-item label="处理方式" required :error="errors.treatment">
        <v-textarea
          v-model="form.treatment"
          placeholder="隔离发病猪，栏舍带猪消毒，补液，饮水中加抗菌药。"
          rows="3"
          counter="200"
          maxlength="200"
          :disabled="closed"
          :error="!!errors.treatment"
        />
      </zw-form-item>
      <zw-form-item label="用药" required :error="errors.used_drug">
        <v-radio-group v-model="form.usedDrug" inline :disabled="closed">
          <v-radio :value="false" label="未使用药品" />
          <v-radio :value="true" label="使用了药品" />
        </v-radio-group>
      </zw-form-item>
      <div v-if="form.usedDrug" class="handle-convert">
        <v-btn
          variant="tonal"
          color="primary"
          size="small"
          prepend-icon="medication"
          :disabled="closed || handle?.medication_converted"
          :loading="saving"
          @click="save(true)"
        >
          {{ handle?.medication_converted ? '已转为用药记录' : '转为用药记录' }}
        </v-btn>
        <span>{{ convertHint }}</span>
      </div>
    </div>

    <div class="handle-section">
      <div class="handle-section-head">
        <span class="zw-section-title">转归</span>
        <span class="handle-section-hint">合计达到 {{ formatNumber(sickCount) }} 头后自动结案</span>
      </div>
      <div class="zw-form-2">
        <zw-form-item label="治愈" :error="errors.count">
          <v-text-field
            v-model.number="form.curedCount"
            type="number"
            min="0"
            suffix="头"
            :disabled="closed"
            :error="!!errors.count"
          />
        </zw-form-item>
        <zw-form-item label="死淘" :error="errors.count">
          <v-text-field
            v-model.number="form.culledCount"
            type="number"
            min="0"
            suffix="头"
            :disabled="closed"
            :error="!!errors.count"
          />
        </zw-form-item>
      </div>
      <div class="handle-progress">
        <v-progress-linear :model-value="percent" height="6" rounded />
        <span class="num">合计 {{ formatNumber(total) }} / {{ formatNumber(sickCount) }} 头</span>
      </div>
      <div v-if="errors.count" class="handle-error">{{ errors.count }}</div>
    </div>

    <div class="handle-section">
      <div class="handle-section-head">
        <span class="zw-section-title">AI 诊断与实际是否相符</span>
      </div>
      <div class="handle-match">
        <button
          v-for="option in matchOptions"
          :key="option.value"
          type="button"
          class="handle-match-btn"
          :class="{ on: form.aiMatch === option.value }"
          :disabled="closed || !hasDiagnosis"
          @click="chooseMatch(option.value)"
        >
          <zw-icon v-if="form.aiMatch === option.value" name="check" :size="16" />
          {{ option.label }}
        </button>
      </div>
      <div v-if="errors.ai_match" class="handle-error">{{ errors.ai_match }}</div>
    </div>

    <zw-notice v-if="closeHint" type="warning" class="mt-4">{{ closeHint }}</zw-notice>
    <zw-notice v-else type="info" class="mt-4">保存后状态变为“已处理”。处理结果可以修改，系统保留修改记录；结案后不能再修改。</zw-notice>

    <template #foot>
      <span class="handle-foot-note">死淘需另行登记，本模块不扣减存栏</span>
      <v-btn variant="outlined" :disabled="saving" @click="onDrawerModel(false)">取消</v-btn>
      <v-btn v-if="!closed" color="primary" :loading="saving" @click="save(false)">
        {{ willClose ? '保存并结案' : '保存' }}
      </v-btn>
    </template>
  </zw-drawer>
</template>

<style scoped>
.handle-summary { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--fill-1); margin-bottom: 16px; }
.handle-summary-main { flex: 1; min-width: 0; }
.handle-summary-title { font-size: 14px; font-weight: 500; color: var(--text-1); }
.handle-summary-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.handle-convert { display: flex; align-items: flex-start; gap: 10px; padding: 10px 12px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 12px; line-height: 18px; color: var(--text-2); }
.handle-section { margin-top: 20px; }
.handle-section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.handle-section-head .zw-section-title { margin: 0; }
.handle-section-hint { font-size: 12px; color: var(--text-3); }
.handle-progress { display: flex; align-items: center; gap: 12px; margin-top: 8px; }
.handle-progress .v-progress-linear { width: 200px; flex: 0 0 200px; }
.handle-progress span { font-size: 12px; color: var(--text-2); }
.handle-error { margin-top: 6px; font-size: 12px; line-height: 18px; color: var(--error); }
.handle-match { display: flex; gap: 8px; }
.handle-match-btn { display: inline-flex; align-items: center; gap: 4px; height: 32px; padding: 0 18px; border: 1px solid var(--control-border); border-radius: var(--radius-sm); background: var(--bg-card); color: var(--text-1); font: inherit; font-size: 14px; cursor: pointer; }
.handle-match-btn.on { border-color: var(--primary); background: var(--primary-subtle); color: var(--primary); font-weight: 500; padding-left: 12px; }
.handle-match-btn:disabled { cursor: default; opacity: .55; }
.handle-foot-note { margin-right: auto; font-size: 12px; color: var(--text-3); }
</style>
