<script setup lang="ts">
import { ref, watch } from 'vue'
import { ApiError } from '@zmt/shared'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

interface XlsxImportError {
  row: number
  message: string
}

interface XlsxImportResult {
  total: number
  success: number
  failed: number
  errors: XlsxImportError[]
  file_name?: string
}

const props = defineProps<{
  modelValue: boolean
  title: string
  hint: string
  downloadTemplate: () => Promise<void>
  upload: (file: File, onProgress: (percent: number) => void) => Promise<XlsxImportResult>
  successText: (result: XlsxImportResult) => string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: [result: XlsxImportResult]
}>()

const admin = useAdminStore()
const ui = useUiStore()

const MAX_FILE_SIZE = 2 * 1024 * 1024
const fileInput = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const fileError = ref('')
const uploading = ref(false)
const percent = ref(0)
const result = ref<XlsxImportResult | null>(null)
const errorMessage = ref('')
const templateLoading = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (open) reset()
  }
)

function reset(): void {
  file.value = null
  fileError.value = ''
  uploading.value = false
  percent.value = 0
  result.value = null
  errorMessage.value = ''
}

function close(): void {
  if (uploading.value) return
  emit('update:modelValue', false)
}

function pickFile(): void {
  if (uploading.value) return
  fileInput.value?.click()
}

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

function validateFile(target: File): boolean {
  if (!/\.xlsx$/i.test(target.name)) {
    fileError.value = '只支持 .xlsx 文件，请先下载模板填写后上传'
    return false
  }
  if (!target.size) {
    fileError.value = '文件是空的，请填写模板后再上传'
    return false
  }
  if (target.size > MAX_FILE_SIZE) {
    fileError.value = '文件不能超过 2MB'
    return false
  }
  fileError.value = ''
  return true
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const picked = input.files?.[0] || null
  input.value = ''
  if (!picked) return
  file.value = picked
  result.value = null
  errorMessage.value = ''
  if (!validateFile(picked)) file.value = null
}

function extractResult(error: unknown): XlsxImportResult | null {
  if (!(error instanceof ApiError)) return null
  const data = error.data as Partial<XlsxImportResult> | undefined
  if (
    !data ||
    typeof data.total !== 'number' ||
    typeof data.success !== 'number' ||
    typeof data.failed !== 'number' ||
    !Array.isArray(data.errors)
  ) {
    return null
  }
  return {
    total: data.total,
    success: data.success,
    failed: data.failed,
    errors: data.errors as XlsxImportError[],
    file_name: data.file_name
  }
}

async function handleError(error: unknown): Promise<void> {
  const data = extractResult(error)
  if (data) {
    result.value = data
    errorMessage.value = error instanceof Error ? error.message : '导入失败'
    return
  }
  errorMessage.value = error instanceof Error ? error.message : '导入失败，请稍后重试'
  if (error instanceof ApiError && error.code === 40308) {
    ui.toast(error.message, 'error')
    await admin.me().catch(() => undefined)
  } else {
    ui.toast(errorMessage.value, 'error')
  }
}

async function submit(): Promise<void> {
  if (!file.value || uploading.value) return
  if (!validateFile(file.value)) return
  uploading.value = true
  percent.value = 0
  result.value = null
  errorMessage.value = ''
  try {
    const data = await props.upload(file.value, (value) => {
      percent.value = value
    })
    result.value = data
    if (data.success > 0 && data.failed === 0) {
      ui.toast(props.successText(data))
      emit('success', data)
    }
  } catch (error) {
    await handleError(error)
  } finally {
    uploading.value = false
  }
}

async function download(): Promise<void> {
  if (templateLoading.value) return
  templateLoading.value = true
  try {
    await props.downloadTemplate()
    ui.toast('模板已开始下载')
  } catch (error) {
    if (error instanceof ApiError && error.code === 40308) {
      ui.toast(error.message, 'error')
      await admin.me().catch(() => undefined)
    } else {
      ui.toast(error instanceof Error ? error.message : '模板下载失败', 'error')
    }
  } finally {
    templateLoading.value = false
  }
}
</script>

<template>
  <zw-dialog
    :model-value="modelValue"
    :title="title"
    width="640"
    icon="upload_file"
    tone="primary"
    :persistent="uploading"
    :no-close="uploading"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <section class="import-step">
      <div class="import-step-head"><span class="import-step-no">1</span><span>下载模板</span></div>
      <p class="import-hint">{{ hint }}</p>
      <v-btn variant="outlined" prepend-icon="download" :loading="templateLoading" :disabled="uploading" @click="download">下载 xlsx 模板</v-btn>
    </section>

    <section class="import-step">
      <div class="import-step-head"><span class="import-step-no">2</span><span>选择文件并导入</span></div>
      <input ref="fileInput" type="file" accept=".xlsx" class="import-file-input" @change="onFileChange" />
      <div class="import-file" :class="{ 'import-file--error': !!fileError }">
        <zw-icon name="description" :size="28" />
        <div class="import-file-main">
          <div class="import-file-name">{{ file?.name || '还没有选择文件' }}</div>
          <div class="import-hint">{{ file ? formatSize(file.size) : '只支持 .xlsx，文件不超过 2MB' }}</div>
        </div>
        <v-btn variant="text" :disabled="uploading" @click="pickFile">{{ file ? '重新选择' : '选择文件' }}</v-btn>
      </div>
      <div v-if="fileError" class="import-error">{{ fileError }}</div>
      <div v-if="uploading" class="import-progress">
        <v-progress-linear :model-value="percent" height="6" rounded color="primary" />
        <span class="import-hint">正在上传 {{ percent }}%</span>
      </div>
    </section>

    <template v-if="result">
      <div class="import-divider"></div>
      <div class="import-summary">
        <div><span>总行数</span><b class="num">{{ result.total }}</b></div>
        <div class="import-summary-ok"><span>成功</span><b class="num">{{ result.success }}</b></div>
        <div :class="{ 'import-summary-bad': result.failed > 0 }"><span>失败</span><b class="num">{{ result.failed }}</b></div>
      </div>
      <zw-notice v-if="errorMessage" type="error" :title="errorMessage" class="import-notice">
        整个文件已回滚，没有写入任何数据；修正后可以重新导入。
      </zw-notice>
      <div v-if="result.errors.length" class="import-errors">
        <div class="import-errors-title">逐行错误</div>
        <div class="import-error-table">
          <table>
            <thead><tr><th>行号</th><th>错误信息</th></tr></thead>
            <tbody>
              <tr v-for="(row, index) in result.errors" :key="`${row.row}-${index}`">
                <td class="num">{{ row.row }}</td>
                <td>{{ row.message }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <zw-notice v-else-if="!errorMessage && result.success > 0" type="success" :title="`已导入 ${result.success} 行`">
        {{ result.file_name ? `${result.file_name} · ` : '' }}列表、进度与页签计数已刷新。
      </zw-notice>
    </template>

    <template #actions>
      <v-btn variant="outlined" :disabled="uploading" @click="close">
        {{ result && !errorMessage && result.success > 0 ? '完成' : '取消' }}
      </v-btn>
      <v-btn v-if="result?.errors.length" variant="outlined" :disabled="uploading" @click="pickFile">重新选择文件</v-btn>
      <v-btn
        v-if="!result || result.failed > 0 || !!errorMessage"
        color="primary"
        :loading="uploading"
        :disabled="!file || uploading"
        @click="submit"
      >
        {{ result ? '重试导入' : '开始导入' }}
      </v-btn>
    </template>
  </zw-dialog>
</template>

<style scoped>
.import-step {
  padding: 14px 0;
}
.import-step + .import-step {
  border-top: 1px solid var(--line-1);
}
.import-step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
}
.import-step-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--primary-subtle);
  color: var(--primary);
  font-size: 12px;
  line-height: 20px;
}
.import-hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.import-file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px dashed var(--line-2);
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  color: var(--text-3);
}
.import-file--error {
  border-color: var(--error);
}
.import-file-main {
  flex: 1;
  min-width: 0;
}
.import-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-1);
}
.import-file .import-hint {
  margin: 0;
}
.import-file-input {
  display: none;
}
.import-error {
  margin-top: 6px;
  font-size: 12px;
  line-height: 20px;
  color: var(--error);
}
.import-progress {
  margin-top: 10px;
}
.import-progress .import-hint {
  margin-top: 6px;
}
.import-divider {
  height: 1px;
  margin: 6px 0 14px;
  background: var(--line-1);
}
.import-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  background: var(--fill-1);
}
.import-summary > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  color: var(--text-2);
}
.import-summary b {
  font-size: 22px;
  line-height: 28px;
  font-weight: 600;
  color: var(--text-1);
}
.import-summary-ok b {
  color: var(--success);
}
.import-summary-bad b {
  color: var(--error);
}
.import-notice {
  margin-top: 12px;
}
.import-errors {
  margin-top: 14px;
}
.import-errors-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}
.import-error-table {
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
}
.import-error-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.import-error-table th {
  position: sticky;
  top: 0;
  height: 34px;
  padding: 0 12px;
  background: var(--fill-1);
  color: var(--text-2);
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}
.import-error-table td {
  padding: 7px 12px;
  border-top: 1px solid var(--line-1);
  color: var(--text-2);
}
.import-error-table td.num {
  width: 72px;
  color: var(--text-1);
  white-space: nowrap;
}
</style>
