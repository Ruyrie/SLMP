<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { healthApi } from '@/api/health'

interface PhotoItem {
  localId: string
  file: File | null
  fileId: number
  url: string
  name: string
  progress: number
  uploading: boolean
  error: boolean
}

const props = withDefaults(defineProps<{
  modelValue?: number[]
  initial?: Array<{ file_id?: number; url?: string; thumb_url?: string }>
  max?: number
  disabled?: boolean
  hint?: string
}>(), {
  modelValue: () => [],
  initial: () => [],
  max: 6,
  disabled: false,
  hint: '可选 · JPG 或 PNG，单张不超过 5MB，最多 6 张'
})

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
  'update:busy': [value: boolean]
  'message': [value: string]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const items = ref<PhotoItem[]>([])
const busy = computed(() => items.value.some((item) => item.uploading))
const count = computed(() => items.value.length)

watch(busy, (value) => emit('update:busy', value), { immediate: true })
watch(() => props.initial, (value) => {
  if (!value?.length || items.value.length) return
  items.value = value
    .filter((item) => Number(item.file_id || 0) > 0)
    .map((item, index) => ({
      localId: `initial-${item.file_id || index}`,
      file: null,
      fileId: Number(item.file_id || 0),
      url: item.url || item.thumb_url || '',
      name: '',
      progress: 100,
      uploading: false,
      error: false
    }))
  emitIds()
}, { immediate: true })

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
      emit('message', `最多添加 ${props.max} 张照片`)
      break
    }
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      emit('message', '只支持 JPG 或 PNG 格式')
      continue
    }
    if (file.size > 5 * 1024 * 1024) {
      emit('message', '照片不能超过 5MB')
      continue
    }
    addFile(file)
  }
}

function addFile(file: File): void {
  const item: PhotoItem = {
    localId: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    file,
    fileId: 0,
    url: URL.createObjectURL(file),
    name: file.name,
    progress: 0,
    uploading: true,
    error: false
  }
  items.value.push(item)
  void upload(item)
}

async function upload(item: PhotoItem): Promise<void> {
  if (!item.file) return
  item.uploading = true
  item.error = false
  item.progress = 0
  try {
    const file = await healthApi.uploadPhoto(item.file, (percent) => {
      item.progress = percent
    })
    item.fileId = Number(file.file_id || 0)
    item.progress = 100
    item.uploading = false
    if (file.url) item.url = String(file.url)
    emitIds()
  } catch {
    item.uploading = false
    item.error = true
  }
}

function retry(item: PhotoItem): void {
  if (props.disabled || !item.file) return
  void upload(item)
}

function preview(item: PhotoItem): void {
  if (item.uploading || item.error || !item.url) return
  window.open(item.url, '_blank')
}

function remove(item: PhotoItem): void {
  if (props.disabled) return
  if (item.file) URL.revokeObjectURL(item.url)
  const index = items.value.findIndex((row) => row.localId === item.localId)
  if (index >= 0) items.value.splice(index, 1)
  emitIds()
}

function reset(): void {
  for (const item of items.value) {
    if (item.file) URL.revokeObjectURL(item.url)
  }
  items.value = []
  emitIds()
}

defineExpose({ reset })

onBeforeUnmount(() => {
  for (const item of items.value) {
    if (item.file) URL.revokeObjectURL(item.url)
  }
})
</script>

<template>
  <div class="hp">
    <div class="hp-grid">
      <div v-for="item in items" :key="item.localId" class="hp-item" :class="{ 'hp-item--error': item.error }">
        <img v-if="item.url" :src="item.url" :alt="item.name" @click="preview(item)">
        <span v-if="item.uploading" class="hp-mask">
          <v-progress-circular :model-value="item.progress" :size="30" :width="3" color="white" />
          <span class="num">{{ item.progress }}%</span>
        </span>
        <button v-if="!item.uploading" class="hp-remove" type="button" aria-label="移除照片" @click.stop="remove(item)">
          <zw-icon name="close" :size="14" />
        </button>
        <button v-if="item.error" class="hp-retry" type="button" @click="retry(item)">上传失败<br>点击重试</button>
      </div>
      <button v-if="items.length < max" class="hp-add" type="button" :disabled="disabled" @click="openPicker">
        <zw-icon name="add_a_photo" :size="22" />
        <span>上传</span>
      </button>
    </div>
    <div class="hp-hint">{{ hint }}<span v-if="items.length" class="hp-count num">{{ count }} / {{ max }}</span></div>
    <input ref="inputRef" type="file" accept="image/jpeg,image/png" multiple hidden @change="onFiles">
  </div>
</template>

<style scoped>
.hp-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.hp-item { position: relative; width: 92px; height: 69px; border-radius: var(--radius-sm); overflow: hidden; border: 1px solid var(--line-1); background: var(--fill-2); }
.hp-item img { width: 100%; height: 100%; object-fit: cover; display: block; cursor: zoom-in; }
.hp-mask { position: absolute; inset: 0; display: flex; flex-direction: column; gap: 2px; align-items: center; justify-content: center; background: #00000066; color: #fff; font-size: 11px; }
.hp-remove { position: absolute; top: 3px; right: 3px; width: 18px; height: 18px; border: 0; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: var(--inverse-surface); color: var(--inverse-on-surface); cursor: pointer; }
.hp-retry { position: absolute; inset: 0; border: 0; background: var(--error-container); color: var(--on-error-container); font: inherit; font-size: 11px; line-height: 16px; cursor: pointer; }
.hp-add { width: 92px; height: 69px; border: 1px dashed var(--control-border); border-radius: var(--radius-sm); background: var(--fill-1); color: var(--text-2); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; font: inherit; font-size: 12px; cursor: pointer; }
.hp-add:hover { border-color: var(--primary); color: var(--primary); }
.hp-add:disabled { cursor: default; opacity: .55; }
.hp-hint { margin-top: 8px; font-size: 12px; line-height: 18px; color: var(--text-3); display: flex; justify-content: space-between; gap: 12px; }
.hp-count { color: var(--text-2); }
</style>
