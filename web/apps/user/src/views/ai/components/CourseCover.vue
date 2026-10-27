<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  src?: string | null
  alt?: string
  kind?: string
  w?: string | number
  h?: string | number
  radius?: number
}>(), { src: '', alt: '课程封面', kind: 'course', w: '100%', h: '100%', radius: 2 })
const failed = ref(false)
const imageSource = computed(() => {
  if (!props.src) return ''
  try {
    // Demo records use this reserved CDN host; their illustration remains the fallback.
    return new URL(props.src, window.location.href).hostname.endsWith('.example.com') ? '' : props.src
  } catch {
    return ''
  }
})
const showImage = computed(() => !!imageSource.value && !failed.value)
const dimensions = computed(() => ({
  width: typeof props.w === 'number' ? `${props.w}px` : props.w,
  height: typeof props.h === 'number' ? `${props.h}px` : props.h,
  borderRadius: `${props.radius}px`
}))
watch(() => props.src, () => { failed.value = false })
</script>

<template>
  <span class="course-cover" :style="dimensions" :role="showImage ? undefined : 'img'" :aria-label="showImage ? undefined : alt">
    <img v-if="showImage" :key="imageSource" :src="imageSource" :alt="alt" loading="lazy" @error="failed = true" />
    <zw-thumb v-else :kind="kind" w="100%" h="100%" :radius="radius" aria-hidden="true" />
  </span>
</template>

<style scoped>
.course-cover { display: block; position: relative; overflow: hidden; flex-shrink: 0; }
.course-cover > img { width: 100%; height: 100%; display: block; object-fit: cover; }
</style>
