<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { FileVO } from '@zmt/shared'
import { uploadFile, type MediaItem } from '@/api/inspect'
import { mediaDuration, mediaIsVideo } from '@/views/inspect/inspect'

export interface UploadItem {
  localId: string
  file?: File
  fileId: number
  url: string
  previewUrl: string
  name: string
  progress: number
  uploading: boolean
  error: boolean
  video: boolean
  duration: string
  tag: string
  tagType: string
}

const props = withDefaults(
  defineProps<{
    modelValue?: number[]
    prefill?: MediaItem[]
    scene: 'INSPECTION' | 'ABNORMAL'
    max?: number
    maxVideo?: number
    thumbW?: number
    thumbH?: number
    hint?: string
    disabled?: boolean
  }>(),
  {
    modelValue: () => [],
    prefill: () => [],
    max: 6,
    maxVideo: 1,
    thumbW: 92,
    thumbH: 69,
    hint: '',
    disabled: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
  'update:busy': [value: boolean]
  'update:items': [value: UploadItem[]]
  preview: [index: number, items: UploadItem[]]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const items = ref<UploadItem[]>([])
const message = ref('')
const dragOver = ref(false)
const busy = computed(() => items.value.some((item) => item.uploading))
const videoCount = computed(() => items.value.filter((item) => item.video).length)
const full = computed(() => items.value.length >= props.max)

watch(busy, (value) => emit('update:busy', value), { immediate: true })
watch(
  items,
  (value) => {
    emit('update:items', value)
    emit(
      'update:modelValue',
      value.filter((item) => item.fileId > 0).map((item) => item.fileId)
    )
  },
  { deep: true }
)
watch(
  () => props.prefill,
  (value) => {
    if (!value?.length) return
    const existing = new Set(items.value.map((item) => item.fileId))
    let added = 0
    for (const media of value) {
      const id = Number(media.file_id || 0)
      if (!id || existing.has(id)) continue
      items.value.push({
        localId: `prefill-${id}`,
        fileId: id,
        url: media.url || media.thumb_url || '',
        previewUrl: media.url || '',
        name: media.source_text || (mediaIsVideo(media) ? '视频' : '照片'),
        progress: 100,
        uploading: false,
        error: false,
        video: mediaIsVideo(media),
        duration: mediaDuration(media),
        tag: media.source_text || '巡检',
        tagType: 'primary'
      })
      existing.add(id)
      added += 1
    }
    if (added) message.value = ''
  },
  { immediate: true }
)
watch(
  () => props.modelValue,
  (value) => {
    if (!value?.length && items.value.length && !busy.value && !props.prefill?.length) clearAll()
  }
)

function emitPreview(item: UploadItem): void {
  const index = items.value.findIndex((row) => row.localId === item.localId)
  if (index >= 0) emit('preview', index, items.value)
}

function openPicker(): void {
  if (props.disabled || busy.value || full.value) return
  message.value = ''
  inputRef.value?.click()
}

async function onFiles(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  for (const file of files) {
    if (items.value.length >= props.max) {
      message.value = `最多 ${props.max} 个照片和视频`
      break
    }
    if (file.type.startsWith('video/')) {
      if (!file.type.includes('mp4')) {
        message.value = '视频只支持 MP4 格式'
        continue
      }
      if (videoCount.value >= props.maxVideo) {
        message.value = '只能添加 1 段视频'
        continue
      }
      if (file.size > 30 * 1024 * 1024) {
        message.value = '视频不能超过 30MB，请重新选择'
        continue
      }
      const duration = await readVideoDuration(file)
      if (duration === null) {
        message.value = '无法读取视频时长，请重新选择'
        continue
      }
      if (duration > 30) {
        message.value = '视频不能超过 30 秒，请重新选择'
        continue
      }
      addFile(file, true, duration)
    } else {
      if (!['image/jpeg', 'image/png'].includes(file.type)) {
        message.value = '只支持 JPG 和 PNG 图片'
        continue
      }
      if (file.size > 5 * 1024 * 1024) {
        message.value = '图片不能超过 5MB'
        continue
      }
      addFile(file, false, 0)
    }
  }
}

function readVideoDuration(file: File): Promise<number | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      const duration = Number.isFinite(video.duration) ? video.duration : 0
      URL.revokeObjectURL(url)
      resolve(duration)
    }
    video.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    video.src = url
  })
}

function addFile(file: File, video: boolean, duration: number): void {
  const url = URL.createObjectURL(file)
  const item: UploadItem = {
    localId: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    file,
    fileId: 0,
    url,
    previewUrl: url,
    name: file.name,
    progress: 0,
    uploading: true,
    error: false,
    video,
    duration: duration ? `${Math.floor(duration / 60)}:${String(Math.round(duration % 60)).padStart(2, '0')}` : '',
    tag: '',
    tagType: ''
  }
  items.value.push(item)
  void upload(item)
}

async function upload(item: UploadItem): Promise<void> {
  if (!item.file) return
  item.uploading = true
  item.error = false
  item.progress = 0
  try {
    const file = await uploadFile(item.file, props.scene, (percent) => {
      item.progress = percent
    })
    const result = file as FileVO
    item.fileId = Number(result.file_id || 0)
    if (result.url) item.previewUrl = result.url
    item.progress = 100
    item.uploading = false
  } catch {
    item.uploading = false
    item.error = true
  }
}

function retry(item: UploadItem): void {
  if (props.disabled) return
  void upload(item)
}

function remove(item: UploadItem): void {
  if (props.disabled) return
  if (item.file) URL.revokeObjectURL(item.url)
  const index = items.value.findIndex((row) => row.localId === item.localId)
  if (index >= 0) items.value.splice(index, 1)
  message.value = ''
}

function clearAll(): void {
  for (const item of items.value) {
    if (item.file) URL.revokeObjectURL(item.url)
  }
  items.value = []
}

function onDragOver(event: DragEvent): void {
  if (props.disabled) return
  event.preventDefault()
  dragOver.value = true
}

function onDragLeave(): void {
  dragOver.value = false
}

function onDrop(event: DragEvent): void {
  event.preventDefault()
  dragOver.value = false
  if (props.disabled || busy.value) return
  const files = Array.from(event.dataTransfer?.files || [])
  if (!files.length) return
  const fake = { target: { files, value: '' } } as unknown as Event
  void onFiles(fake)
}

defineExpose({ hasUploading: () => busy.value, hasFailed: () => items.value.some((item) => item.error), clearAll })
onBeforeUnmount(() => clearAll())
</script>

<template>
  <div class="insp-upload" @dragover="onDragOver" @dragleave="onDragLeave" @drop="onDrop">
    <div class="insp-upload-grid">
      <div
        v-for="item in items"
        :key="item.localId"
        class="insp-upload-item"
        :class="{ 'insp-upload-item--error': item.error }"
        :style="{ width: thumbW + 'px', height: thumbH + 'px' }"
        @click="item.uploading || item.error ? undefined : emitPreview(item)"
      >
        <img v-if="!item.video && item.previewUrl" :src="item.previewUrl" :alt="item.name">
        <video v-else-if="item.video && item.previewUrl" :src="item.previewUrl" muted preload="metadata" />
        <span v-if="item.video" class="insp-upload-play"><zw-icon name="play_arrow" :size="20" fill /></span>
        <span v-if="item.duration" class="insp-upload-dur num">{{ item.duration }}</span>
        <span v-if="item.tag" class="insp-upload-tag">{{ item.tag }}</span>
        <span v-if="item.uploading" class="insp-upload-mask">
          <v-progress-circular :model-value="item.progress" :size="30" :width="3" color="white" />
          <span class="num">{{ item.progress }}%</span>
        </span>
        <button
          v-if="item.error"
          class="insp-upload-retry"
          type="button"
          @click.stop="retry(item)"
        >
          <zw-icon name="refresh" :size="18" />
          <span>上传失败</span>
          <span>点击重试</span>
        </button>
        <button
          v-if="!item.uploading && !disabled"
          class="insp-upload-del"
          type="button"
          aria-label="删除"
          @click.stop="remove(item)"
        >
          <zw-icon name="close" :size="13" />
        </button>
      </div>
      <button
        v-if="!full"
        class="insp-upload-add"
        :class="{ 'insp-upload-add--over': dragOver }"
        :style="{ width: items.length ? thumbW + 'px' : '100%', height: items.length ? thumbH + 'px' : '132px' }"
        type="button"
        :disabled="disabled"
        @click="openPicker"
      >
        <zw-icon :name="items.length ? 'add' : 'cloud_upload'" :size="items.length ? 24 : 32" />
        <span v-if="items.length" class="insp-upload-add-text">上传</span>
        <template v-else>
          <span class="insp-upload-title">点击或拖拽文件到这里上传</span>
          <span v-if="hint" class="insp-upload-hint">{{ hint }}</span>
        </template>
      </button>
    </div>
    <div v-if="items.length && hint" class="insp-upload-hint insp-upload-hint--below">{{ hint }}</div>
    <div v-if="message" class="insp-upload-error">{{ message }}</div>
    <input
      ref="inputRef"
      type="file"
      accept="image/jpeg,image/png,video/mp4"
      multiple
      hidden
      @change="onFiles"
    >
  </div>
</template>

<style scoped>
.insp-upload-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.insp-upload-item { position: relative; flex: 0 0 auto; border-radius: var(--radius-sm); overflow: hidden; border: 1px solid var(--line-1); background: var(--fill-2); cursor: zoom-in; }
.insp-upload-item img, .insp-upload-item video { width: 100%; height: 100%; object-fit: cover; display: block; }
.insp-upload-item--error { cursor: default; }
.insp-upload-play { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 32px; height: 32px; border-radius: 50%; background: #00000073; color: #fff; display: inline-flex; align-items: center; justify-content: center; pointer-events: none; }
.insp-upload-dur { position: absolute; left: 5px; bottom: 5px; padding: 0 5px; border-radius: 2px; background: #000000b3; color: #fff; font-size: 11px; line-height: 18px; }
.insp-upload-tag { position: absolute; right: 4px; bottom: 4px; padding: 0 5px; border-radius: 2px; background: #000000b3; color: #fff; font-size: 11px; line-height: 17px; }
.insp-upload-mask { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; background: #00000080; color: #fff; font-size: 11px; }
.insp-upload-retry { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 1px; border: 0; background: #b3261ed9; color: #fff; font: inherit; font-size: 11px; line-height: 16px; cursor: pointer; }
.insp-upload-del { position: absolute; top: 3px; right: 3px; width: 18px; height: 18px; border: 0; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: #1d2129cc; color: #fff; cursor: pointer; }
.insp-upload-add { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; border: 1px dashed var(--control-border); border-radius: var(--radius-sm); background: var(--fill-1); color: var(--text-2); font: inherit; font-size: 12px; cursor: pointer; }
.insp-upload-add:hover, .insp-upload-add--over { border-color: var(--primary); background: var(--primary-subtle); color: var(--primary); }
.insp-upload-add:disabled { cursor: default; opacity: .55; }
.insp-upload-title { font-size: 14px; color: var(--text-2); }
.insp-upload-hint { font-size: 12px; line-height: 18px; color: var(--text-3); text-align: center; }
.insp-upload-hint--below { margin-top: 8px; text-align: left; }
.insp-upload-error { margin-top: 6px; font-size: 12px; line-height: 18px; color: var(--error); }
.insp-upload-add-text { font-size: 12px; }
</style>
