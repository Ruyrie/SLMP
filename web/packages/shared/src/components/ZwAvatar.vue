<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { avatarInitial, resolveAvatarUrl } from '../utils/avatar'

const props = withDefaults(defineProps<{
  name?: string | null
  src?: string | null
  size?: number | string
  tone?: string
}>(), { name: '', src: '', size: 32, tone: 'primary' })

const failed = ref(false)
const source = computed(() => resolveAvatarUrl(props.src))
const initial = computed(() => avatarInitial(props.name))
const dimension = computed(() => Math.max(1, Number(props.size) || 32))
watch(source, () => { failed.value = false })
</script>

<template>
  <span
    class="zw-avatar"
    :class="`zw-avatar--${tone}`"
    :style="{ width: `${dimension}px`, height: `${dimension}px`, fontSize: `${Math.round(dimension * 0.42)}px` }"
    role="img"
    :aria-label="name ? `${name}的头像` : '默认头像'"
  >
    <img v-if="source && !failed" :key="source" :src="source" alt="" @error="failed = true" />
    <span v-else>{{ initial }}</span>
  </span>
</template>

<style scoped>
.zw-avatar { overflow: hidden; vertical-align: middle; }
.zw-avatar img { display: block; width: 100%; height: 100%; object-fit: cover; }
</style>
