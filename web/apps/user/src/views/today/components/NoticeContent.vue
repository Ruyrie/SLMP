<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import type { FileVO } from '@zmt/shared'
import MediaPreviewDialog from '@/views/inspect/components/MediaPreviewDialog.vue'

const props = defineProps<{ content?: string; image?: Partial<FileVO> | null }>()
const emit = defineEmits<{ refresh: [] }>()
const previewOpen = ref(false)
const imageFailed = ref(false)
const imageUrl = computed(() => props.image?.url || props.image?.thumb_url || '')
const html = computed(() => DOMPurify.sanitize(props.content || '', {
  ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'ol', 'ul', 'li'],
  ALLOWED_ATTR: [],
  ALLOW_DATA_ATTR: false,
  ALLOW_ARIA_ATTR: false
}))
const previewItems = computed(() => imageUrl.value
  ? [{ url: imageUrl.value, video: false, name: '通知配图' }]
  : [])

watch(imageUrl, () => {
  imageFailed.value = false
})
watch(() => props.image?.file_id, () => {
  previewOpen.value = false
})
</script>

<template>
  <div v-if="html" class="notice-body" v-html="html" />
  <zw-empty v-else-if="!imageUrl" icon="campaign" title="通知内容为空" desc="这条通知没有正文内容。" />
  <div v-if="imageUrl" class="notice-image">
    <zw-empty v-if="imageFailed" icon="broken_image" title="配图加载失败" desc="请重新加载通知后重试。">
      <v-btn variant="outlined" @click="emit('refresh')">重新加载</v-btn>
    </zw-empty>
    <button v-else type="button" aria-label="查看通知配图" @click="previewOpen = true">
      <img :src="imageUrl" alt="通知配图" @error="imageFailed = true" />
    </button>
  </div>
  <MediaPreviewDialog v-model="previewOpen" :items="previewItems" source-label="通知配图" />
</template>

<style scoped>
.notice-body { font-size: 14px; line-height: 1.8; white-space: pre-line; overflow-wrap: anywhere; }
.notice-body :deep(p) { margin: 0 0 12px; }
.notice-body :deep(ol), .notice-body :deep(ul) { padding-left: 24px; }
.notice-image { margin-top: 16px; }
.notice-image button { display: block; padding: 0; border: 0; background: transparent; cursor: zoom-in; max-width: 100%; }
.notice-image img { display: block; max-width: 100%; max-height: 400px; object-fit: contain; border-radius: var(--radius-sm); }
</style>
