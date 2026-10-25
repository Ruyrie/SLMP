<script setup lang="ts">
import { computed, ref, watch, type CSSProperties } from 'vue'

const props = withDefaults(defineProps<{
  url?: string
  thumbUrl?: string
  alt?: string
  preferThumbnail?: boolean
  imageStyle?: CSSProperties
}>(), { alt: '资质图', preferThumbnail: true })

const sources = computed(() => [...new Set((props.preferThumbnail
  ? [props.thumbUrl, props.url]
  : [props.url, props.thumbUrl]).filter((url): url is string => Boolean(url)))])
const attempt = ref(0)
const source = computed(() => sources.value[attempt.value])
watch(sources, () => { attempt.value = 0 })
</script>

<template>
  <div class="audit-image">
    <img v-if="source" :key="source" :src="source" :alt="alt" :style="imageStyle" @error="attempt += 1" />
    <div v-else class="audit-image-error" role="status">
      <span>{{ sources.length ? '图片加载失败' : '未提供图片地址' }}</span>
      <button v-if="sources.length" type="button" @click.stop="attempt = 0">重新加载图片</button>
    </div>
  </div>
</template>

<style scoped>
.audit-image { display: grid; grid-template: minmax(0, 1fr) / minmax(0, 1fr); place-items: center; width: 100%; height: 100%; min-width: 0; min-height: 0; }
.audit-image img { display: block; width: 100%; height: 100%; min-width: 0; min-height: 0; object-fit: contain; }
.audit-image-error { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 12px; color: inherit; text-align: center; font-size: 12px; }
.audit-image-error button { color: inherit; text-decoration: underline; cursor: pointer; }
</style>
