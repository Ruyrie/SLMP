<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface PreviewItem {
  url: string
  video: boolean
  duration?: string
  name?: string
  tag?: string
  shotAt?: string
  shotBy?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    items: PreviewItem[]
    startIndex?: number
    sourceLabel?: string
    deletable?: boolean
  }>(),
  {
    startIndex: 0,
    sourceLabel: '',
    deletable: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  delete: [index: number]
}>()

const index = ref(0)
const videoRef = ref<HTMLVideoElement | null>(null)

watch(
  () => props.modelValue,
  (open) => {
    if (open) index.value = Math.min(Math.max(0, props.startIndex), Math.max(0, props.items.length - 1))
  }
)
watch(
  () => props.startIndex,
  (value) => {
    if (props.modelValue) index.value = Math.min(Math.max(0, value), Math.max(0, props.items.length - 1))
  }
)

const current = computed<PreviewItem | undefined>(() => props.items[index.value])
const total = computed(() => props.items.length)

function close(): void {
  emit('update:modelValue', false)
}

function go(delta: number): void {
  if (!total.value) return
  videoRef.value?.pause()
  index.value = (index.value + delta + total.value) % total.value
}

function pick(target: number): void {
  videoRef.value?.pause()
  index.value = target
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.modelValue) return
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    go(-1)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    go(1)
  }
}

function download(): void {
  if (!current.value?.url) return
  const link = document.createElement('a')
  link.href = current.value.url
  link.download = current.value.name || (current.value.video ? 'video.mp4' : 'photo.jpg')
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function removeCurrent(): void {
  emit('delete', index.value)
  if (index.value >= props.items.length - 1) index.value = Math.max(0, props.items.length - 2)
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    fullscreen
    transition="fade-transition"
    class="media-preview"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="mp" tabindex="0">
      <header class="mp-head">
        <button class="mp-icon" type="button" aria-label="关闭" @click="close">
          <zw-icon name="close" :size="24" />
        </button>
        <div class="mp-counter num">{{ total ? index + 1 : 0 }} / {{ total }}</div>
        <div class="mp-head-right">
          <span class="mp-tip">← → 切换 · Esc 关闭</span>
          <button v-if="deletable" class="mp-icon" type="button" aria-label="删除" @click="removeCurrent">
            <zw-icon name="delete" :size="22" />
          </button>
          <button class="mp-icon" type="button" aria-label="下载" @click="download">
            <zw-icon name="download" :size="22" />
          </button>
        </div>
      </header>

      <div class="mp-stage">
        <button v-if="total > 1" class="mp-arrow mp-arrow--left" type="button" aria-label="上一项" @click="go(-1)">
          <zw-icon name="chevron_left" :size="28" />
        </button>
        <div v-if="current" class="mp-media">
          <video
            v-if="current.video"
            ref="videoRef"
            :src="current.url"
            controls
            preload="metadata"
            playsinline
          />
          <img v-else :src="current.url" :alt="current.name || '照片'">
        </div>
        <div v-else class="mp-empty">没有可预览的照片或视频</div>
        <button v-if="total > 1" class="mp-arrow mp-arrow--right" type="button" aria-label="下一项" @click="go(1)">
          <zw-icon name="chevron_right" :size="28" />
        </button>
      </div>

      <div v-if="current" class="mp-meta">
        <div class="mp-source">
          {{ sourceLabel || '照片和视频' }}<template v-if="current.shotBy"> · 拍摄人{{ current.shotBy }}</template>
        </div>
        <div class="mp-sub">
          <template v-if="current.shotAt">{{ current.shotAt }} 拍摄</template>
          <template v-if="current.shotAt"> · </template>{{ current.video ? `视频 ${current.duration || ''}` : '照片' }}
        </div>
      </div>

      <div v-if="total > 1" class="mp-thumbs">
        <button
          v-for="(item, i) in items"
          :key="i"
          class="mp-thumb"
          :class="{ on: i === index }"
          type="button"
          @click="pick(i)"
        >
          <img v-if="!item.video" :src="item.url" alt="">
          <video v-else :src="item.url" preload="metadata" muted />
          <span v-if="item.video" class="mp-thumb-play"><zw-icon name="play_arrow" :size="14" fill /></span>
        </button>
      </div>
    </div>
  </v-dialog>
</template>

<style scoped>
.mp { position: fixed; inset: 0; background: var(--media-backdrop); color: var(--on-media); display: flex; flex-direction: column; align-items: center; outline: none; }
.mp-head { width: 100%; height: 56px; flex: 0 0 56px; display: flex; align-items: center; gap: 16px; padding: 0 20px; }
.mp-counter { flex: 1; text-align: center; font-size: 16px; font-weight: 500; }
.mp-head-right { display: flex; align-items: center; gap: 4px; }
.mp-tip { font-size: 12px; color: #ffffffa6; margin-right: 8px; }
.mp-icon { width: 40px; height: 40px; border: 0; border-radius: 50%; background: transparent; color: var(--on-media); display: inline-flex; align-items: center; justify-content: center; cursor: pointer; }
.mp-icon:hover { background: #ffffff1f; }
.mp-stage { flex: 1; min-height: 0; width: 100%; display: flex; align-items: center; justify-content: center; gap: 32px; padding: 0 24px; }
.mp-media { max-width: min(1120px, calc(100vw - 200px)); max-height: 100%; display: flex; align-items: center; justify-content: center; }
.mp-media img, .mp-media video { max-width: 100%; max-height: calc(100vh - 240px); border-radius: 4px; background: #000; }
.mp-arrow { flex: 0 0 auto; width: 48px; height: 48px; border: 0; border-radius: 50%; background: #ffffff1f; color: var(--on-media); display: inline-flex; align-items: center; justify-content: center; cursor: pointer; }
.mp-arrow:hover { background: #ffffff33; }
.mp-empty { color: #ffffffa6; }
.mp-meta { flex: 0 0 auto; padding: 12px 0 8px; text-align: center; }
.mp-source { font-size: 14px; }
.mp-sub { font-size: 12px; color: #ffffffb3; margin-top: 2px; }
.mp-thumbs { flex: 0 0 auto; display: flex; gap: 8px; padding: 8px 0 24px; max-width: 100%; overflow-x: auto; }
.mp-thumb { position: relative; width: 64px; height: 48px; padding: 0; border: 2px solid transparent; border-radius: 4px; overflow: hidden; background: #ffffff14; opacity: .55; cursor: pointer; }
.mp-thumb.on { opacity: 1; border-color: #fff; }
.mp-thumb img, .mp-thumb video { width: 100%; height: 100%; object-fit: cover; display: block; }
.mp-thumb-play { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); color: #fff; }
</style>

<style>
.media-preview .v-overlay__content { width: 100%; height: 100%; max-width: none; }
</style>
