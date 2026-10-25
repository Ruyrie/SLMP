<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AuditImage from './AuditImage.vue'

export interface ViewerItem {
  url?: string
  thumb_url?: string
  label?: string
  meta?: string
  tag?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    items: ViewerItem[]
    initialIndex?: number
    title?: string
  }>(),
  { initialIndex: 0, title: '资质图' }
)

const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>()

const index = ref(0)
const zoom = ref(1)
const rotate = ref(0)

const current = computed<ViewerItem | null>(() => props.items[index.value] || null)
const total = computed(() => props.items.length)

function reset(): void {
  index.value = Math.min(Math.max(props.initialIndex, 0), Math.max(props.items.length - 1, 0))
  zoom.value = 1
  rotate.value = 0
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      reset()
      window.addEventListener('keydown', onKey)
    } else {
      window.removeEventListener('keydown', onKey)
    }
  },
  { immediate: true }
)

watch(
  () => props.initialIndex,
  (value) => {
    if (props.modelValue) index.value = Math.min(Math.max(value, 0), Math.max(props.items.length - 1, 0))
  }
)

watch(index, () => {
  zoom.value = 1
  rotate.value = 0
})

function close(): void {
  emit('update:modelValue', false)
}

function step(delta: number): void {
  if (!total.value) return
  index.value = (index.value + delta + total.value) % total.value
}

function zoomBy(delta: number): void {
  zoom.value = Math.min(3, Math.max(0.5, Number((zoom.value + delta).toFixed(2))))
}

function onKey(event: KeyboardEvent): void {
  if (!props.modelValue) return
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
  } else if (event.key === 'ArrowLeft') {
    step(-1)
  } else if (event.key === 'ArrowRight') {
    step(1)
  }
}

onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="av-root" role="dialog" aria-modal="true" @click.self="close">
      <div class="av">
        <div class="av-bar">
          <div class="av-title">
            <b>{{ title }} {{ total ? index + 1 : 0 }} / {{ total }}</b>
            <span v-if="current?.meta">{{ current.meta }}</span>
            <span v-if="current?.tag" class="av-tag">{{ current.tag }}</span>
          </div>
          <button class="av-btn" type="button" aria-label="关闭" @click="close">
            <zw-icon name="close" :size="22" />
          </button>
        </div>

        <div class="av-stage">
          <button v-if="total > 1" class="av-btn" type="button" aria-label="上一张" @click="step(-1)">
            <zw-icon name="chevron_left" :size="26" />
          </button>
          <div class="av-figure" :style="{ width: '760px', height: '475px' }">
            <AuditImage
              :key="index"
              :url="current?.url"
              :thumb-url="current?.thumb_url"
              :prefer-thumbnail="false"
              :alt="current?.label || '资质图'"
              :image-style="{ transform: `scale(${zoom}) rotate(${rotate}deg)` }"
            />
          </div>
          <button v-if="total > 1" class="av-btn" type="button" aria-label="下一张" @click="step(1)">
            <zw-icon name="chevron_right" :size="26" />
          </button>
        </div>

      <div class="av-tools">
        <button class="av-btn" type="button" aria-label="缩小" @click="zoomBy(-0.2)">
          <zw-icon name="zoom_out" :size="20" />
        </button>
        <span class="av-zoom num">{{ Math.round(zoom * 100) }}%</span>
        <button class="av-btn" type="button" aria-label="放大" @click="zoomBy(0.2)">
          <zw-icon name="zoom_in" :size="20" />
        </button>
        <button class="av-btn" type="button" aria-label="向右旋转" @click="rotate = (rotate + 90) % 360">
          <zw-icon name="rotate_right" :size="20" />
        </button>
      </div>

      <div v-if="total > 1" class="av-strip">
        <button
          v-for="(item, i) in items"
          :key="i"
          type="button"
          class="av-thumb"
          :class="{ on: i === index }"
          :aria-label="`第 ${i + 1} 张`"
          @click="index = i"
        >
          <AuditImage :url="item.url" :thumb-url="item.thumb_url" :alt="item.label || '资质图'" />
        </button>
      </div>
    </div>
    </div>
  </Teleport>
</template>

<style scoped>
.av-root {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: #000000e6;
  display: flex;
}
.av {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  color: #ffffff;
}
.av-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 56px;
  padding: 0 16px 0 24px;
  background: #00000066;
  flex-shrink: 0;
}
.av-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  line-height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.av-title b {
  margin-right: 12px;
  font-weight: 600;
}
.av-tag {
  margin-left: 12px;
  padding: 0 8px;
  border-radius: 2px;
  background: #ffffff26;
  font-size: 12px;
}
.av-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: #ffffff1f;
  color: #ffffff;
  cursor: pointer;
  flex-shrink: 0;
}
.av-btn:hover {
  background: #ffffff33;
}
.av-stage {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  min-height: 0;
  padding: 16px 0;
}
.av-figure {
  position: relative;
  max-width: 90vw;
  max-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #111111;
  border-radius: 4px;
  box-shadow: 0 8px 32px #00000080;
}
.av-tools {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.av-zoom {
  min-width: 52px;
  text-align: center;
  font-size: 13px;
  line-height: 40px;
}
.av-strip {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 16px 0 24px;
  flex-shrink: 0;
}
.av-thumb {
  width: 96px;
  height: 64px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  opacity: 0.6;
  outline: 2px solid transparent;
  cursor: pointer;
  overflow: hidden;
}
.av-thumb.on {
  opacity: 1;
  outline-color: #ffffff;
}
</style>
