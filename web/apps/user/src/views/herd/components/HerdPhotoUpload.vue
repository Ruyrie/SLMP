<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { herdApi } from '@/api/herd'

interface PhotoItem {
  localId: string
  file: File
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
const items = ref<PhotoItem[]>([])
const message = ref('')
const busy = computed(() => items.value.some((item) => item.uploading))

watch(busy, (value) => emit('update:busy', value), { immediate: true })
watch(() => props.modelValue, (value) => {
  if (!value?.length && !busy.value && items.value.length) clearAll()
})

function emitIds(): void {
  emit('update:modelValue', items.value.filter((item) => item.fileId > 0).map((item) => item.fileId))
}

function openPicker(): void {
  if (props.disabled || busy.value || items.value.length >= props.max) return
  message.value = ''
  inputRef.value?.click()
}

function onFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  for (const file of files) {
    if (items.value.length >= props.max) {
      message.value = `最多添加 ${props.max} 张照片`
      break
    }
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      message.value = '只支持 JPG 或 PNG 格式'
      continue
    }
    if (file.size > 5 * 1024 * 1024) {
      message.value = '单张照片不能超过 5MB'
      continue
    }
    addFile(file)
  }
}

function addFile(file: File): void {
  // 上传回调与页面必须修改同一个响应式对象，才能及时解除提交锁定。
  const item = reactive<PhotoItem>({
    localId: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    file,
    fileId: 0,
    url: URL.createObjectURL(file),
    name: file.name,
    progress: 0,
    uploading: true,
    error: false
  })
  items.value.push(item)
  void upload(item)
}

async function upload(item: PhotoItem): Promise<void> {
  item.uploading = true
  item.error = false
  item.progress = 0
  try {
    const file = await herdApi.uploadFile(item.file, 'DEATH', (percent) => {
      item.progress = percent
    })
    const fileId = Number(file.file_id)
    if (!Number.isInteger(fileId) || fileId <= 0) throw new Error('上传未返回有效文件编号')
    item.fileId = fileId
    item.progress = 100
    item.uploading = false
    emitIds()
  } catch {
    item.uploading = false
    item.error = true
  }
}

function retry(item: PhotoItem): void {
  if (props.disabled) return
  void upload(item)
}

function preview(item: PhotoItem): void {
  if (item.uploading || item.error) return
  window.open(item.url, '_blank')
}

function remove(item: PhotoItem): void {
  if (props.disabled) return
  URL.revokeObjectURL(item.url)
  const index = items.value.findIndex((row) => row.localId === item.localId)
  if (index >= 0) items.value.splice(index, 1)
  emitIds()
}

function clearAll(): void {
  for (const item of items.value) URL.revokeObjectURL(item.url)
  items.value = []
  emitIds()
}

onBeforeUnmount(() => {
  for (const item of items.value) URL.revokeObjectURL(item.url)
})
</script>

<template>
  <div class="herd-photos">
    <div class="herd-photo-grid">
      <div v-for="item in items" :key="item.localId" class="herd-photo" :class="{ 'herd-photo--error': item.error }">
        <img :src="item.url" :alt="item.name" @click="preview(item)">
        <span v-if="item.uploading" class="herd-photo-mask">
          <v-progress-circular :model-value="item.progress" :size="30" :width="3" color="white" />
        </span>
        <button v-if="!item.uploading" class="herd-photo-remove" type="button" aria-label="移除照片" @click.stop="remove(item)">
          <zw-icon name="close" :size="14" />
        </button>
        <button v-if="item.error" class="herd-photo-retry" type="button" @click="retry(item)">上传失败<br>点击重试</button>
      </div>
      <button v-if="items.length < max" class="herd-photo-add" type="button" :disabled="disabled" @click="openPicker">
        <zw-icon name="add_a_photo" :size="22" />
        <span>上传</span>
      </button>
    </div>
    <div class="herd-photo-hint">选填 · JPG 或 PNG，单张不超过 5MB，最多 {{ max }} 张</div>
    <div v-if="message" class="herd-photo-error">{{ message }}</div>
    <input ref="inputRef" type="file" accept="image/jpeg,image/png" multiple hidden @change="onFiles">
  </div>
</template>

<style scoped>
.herd-photo-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.herd-photo { position: relative; width: 96px; height: 72px; border-radius: var(--radius-sm); overflow: hidden; border: 1px solid var(--line-1); background: var(--fill-2); }
.herd-photo img { width: 100%; height: 100%; object-fit: cover; display: block; cursor: zoom-in; }
.herd-photo-mask { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; background: #00000066; }
.herd-photo-remove { position: absolute; top: 3px; right: 3px; width: 18px; height: 18px; border: 0; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: var(--inverse-surface); color: var(--inverse-on-surface); cursor: pointer; }
.herd-photo-retry { position: absolute; inset: 0; border: 0; background: var(--error-container); color: var(--on-error-container); font: inherit; font-size: 11px; line-height: 16px; cursor: pointer; }
.herd-photo-add { width: 96px; height: 72px; border: 1px dashed var(--control-border); border-radius: var(--radius-sm); background: var(--fill-1); color: var(--text-2); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; font: inherit; font-size: 12px; cursor: pointer; }
.herd-photo-add:hover { border-color: var(--primary); color: var(--primary); }
.herd-photo-add:disabled { cursor: default; opacity: .55; }
.herd-photo-hint { margin-top: 8px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.herd-photo-error { margin-top: 4px; font-size: 12px; line-height: 18px; color: var(--error); }
</style>
