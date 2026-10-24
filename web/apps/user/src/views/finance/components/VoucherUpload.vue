<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { financeApi } from '@/api/finance'
import { useUiStore } from '@/stores/ui'

interface VoucherItem {
  localId: string
  file?: File
  fileId: number
  url: string
  name: string
  progress: number
  uploading: boolean
  error: boolean
}

const props = withDefaults(defineProps<{
  modelValue?: number[]
  max?: number
  disabled?: boolean
}>(), {
  modelValue: () => [],
  max: 6,
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
  'update:busy': [value: boolean]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const items = ref<VoucherItem[]>([])
const ui = useUiStore()
const busy = computed(() => items.value.some((item) => item.uploading))

watch(busy, (value) => emit('update:busy', value), { immediate: true })

function emitIds(): void {
  emit('update:modelValue', items.value.filter((item) => item.fileId > 0).map((item) => item.fileId))
}

function openPicker(): void {
  if (props.disabled || busy.value || items.value.length >= props.max) return
  inputRef.value?.click()
}

function onFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  for (const file of files) {
    if (items.value.length >= props.max) {
      ui.toast(`最多上传 ${props.max} 张凭证`, 'error')
      break
    }
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      ui.toast('只支持 JPG 或 PNG 图片', 'error')
      continue
    }
    if (file.size > 5 * 1024 * 1024) {
      ui.toast('图片不能超过 5MB', 'error')
      continue
    }
    addFile(file)
  }
}

function addFile(file: File): void {
  const url = URL.createObjectURL(file)
  const item: VoucherItem = {
    localId: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    file,
    fileId: 0,
    url,
    name: file.name,
    progress: 0,
    uploading: true,
    error: false
  }
  items.value.push(item)
  void upload(item, file)
}

async function upload(item: VoucherItem, file: File): Promise<void> {
  item.uploading = true
  item.error = false
  item.progress = 0
  try {
    const result = await financeApi.uploadFile(file, 'VOUCHER', (percent) => {
      item.progress = percent
    })
    item.fileId = Number(result.file_id || 0)
    item.progress = 100
    item.uploading = false
    emitIds()
  } catch {
    item.uploading = false
    item.error = true
  }
}

function remove(item: VoucherItem): void {
  if (props.disabled) return
  URL.revokeObjectURL(item.url)
  items.value = items.value.filter((row) => row.localId !== item.localId)
  emitIds()
}

function preview(item: VoucherItem): void {
  if (item.uploading || item.error) return
  window.open(item.url, '_blank')
}

function retry(item: VoucherItem): void {
  if (props.disabled || !item.file) return
  void upload(item, item.file)
}

onBeforeUnmount(() => {
  for (const item of items.value) URL.revokeObjectURL(item.url)
})
</script>

<template>
  <div class="finance-vouchers">
    <div class="finance-voucher-grid">
      <div v-for="item in items" :key="item.localId" class="finance-voucher" :class="{ 'is-error': item.error }">
        <img :src="item.url" :alt="item.name" @click="preview(item)">
        <span v-if="item.uploading" class="finance-voucher-mask">
          <v-progress-circular :model-value="item.progress" :size="26" :width="3" color="white" />
        </span>
        <button v-if="!item.uploading" class="finance-voucher-remove" type="button" aria-label="移除凭证" @click.stop="remove(item)">
          <zw-icon name="close" :size="13" />
        </button>
        <button v-if="item.error" class="finance-voucher-retry" type="button" @click="retry(item)">上传失败<br>点击重试</button>
      </div>
      <button
        v-if="items.length < max"
        class="finance-voucher-add"
        type="button"
        :disabled="disabled"
        @click="openPicker"
      >
        <zw-icon name="add" :size="20" />
        <span>上传</span>
      </button>
    </div>
    <div class="finance-voucher-hint">JPG 或 PNG，单张不超过 5MB，最多 {{ max }} 张</div>
    <input ref="inputRef" type="file" accept="image/jpeg,image/png" multiple hidden @change="onFiles">
  </div>
</template>

<style scoped>
.finance-voucher-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.finance-voucher { position: relative; width: 80px; height: 80px; border-radius: var(--radius-sm); overflow: hidden; border: 1px solid var(--line-1); background: var(--fill-2); }
.finance-voucher img { width: 100%; height: 100%; object-fit: cover; display: block; cursor: zoom-in; }
.finance-voucher-mask { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: #00000066; }
.finance-voucher-remove { position: absolute; top: 3px; right: 3px; width: 17px; height: 17px; border: 0; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: var(--inverse-surface); color: var(--inverse-on-surface); cursor: pointer; }
.finance-voucher-retry { position: absolute; inset: 0; border: 0; background: var(--error-container); color: var(--on-error-container); font: inherit; font-size: 11px; line-height: 16px; cursor: pointer; }
.finance-voucher-add { width: 80px; height: 80px; border: 1px dashed var(--control-border); border-radius: var(--radius-sm); background: var(--fill-1); color: var(--text-2); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; font: inherit; font-size: 12px; cursor: pointer; }
.finance-voucher-add:hover { border-color: var(--primary); color: var(--primary); }
.finance-voucher-add:disabled { opacity: .5; cursor: default; }
.finance-voucher-hint { margin-top: 6px; font-size: 12px; line-height: 18px; color: var(--text-3); }
</style>

