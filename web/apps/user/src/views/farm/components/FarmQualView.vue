<script setup lang="ts">
import { computed, ref } from 'vue'
import type { QualFileVO } from '@/api/farm'

const props = withDefaults(
  defineProps<{
    files?: QualFileVO[]
    width?: number
    height?: number
  }>(),
  { files: () => [], width: 80, height: 60 }
)

const open = ref(false)
const index = ref(0)
const current = computed(() => (props.files || [])[index.value])

function preview(target: number): void {
  index.value = target
  open.value = true
}

function move(delta: number): void {
  const total = props.files?.length || 0
  if (!total) return
  index.value = (index.value + delta + total) % total
}
</script>

<template>
  <div class="fqual">
    <div
      v-for="(file, i) in files"
      :key="file.file_id ?? i"
      class="fqual-thumb"
      role="button"
      tabindex="0"
      :style="{ width: `${width}px`, height: `${height}px` }"
      @click="preview(i)"
      @keydown.enter="preview(i)"
    >
      <img v-if="file.thumb_url || file.url" :src="file.thumb_url || file.url" alt="资质图" />
      <zw-thumb v-else kind="doc" :w="width" :h="height" />
      <span v-if="file.reshoot" class="fqual-flag fqual-flag--error">需重拍</span>
      <span v-else-if="file.is_new" class="fqual-flag">新增</span>
    </div>

    <v-dialog v-model="open" width="760" scrim="#000000A6">
      <div class="fqual-viewer">
        <div class="fqual-viewer-head">
          <span class="num">{{ index + 1 }} / {{ files.length }}</span>
          <button type="button" class="fqual-viewer-close" aria-label="关闭" @click="open = false">
            <zw-icon name="close" :size="20" />
          </button>
        </div>
        <div class="fqual-viewer-body">
          <button v-if="files.length > 1" type="button" class="fqual-nav" aria-label="上一张" @click="move(-1)">
            <zw-icon name="chevron_left" :size="28" />
          </button>
          <img v-if="current && (current.url || current.thumb_url)" :src="current.url || current.thumb_url" alt="资质图大图" />
          <zw-thumb v-else kind="doc" :w="520" :h="360" />
          <button v-if="files.length > 1" type="button" class="fqual-nav" aria-label="下一张" @click="move(1)">
            <zw-icon name="chevron_right" :size="28" />
          </button>
        </div>
        <div v-if="current?.reshoot" class="fqual-viewer-note">这张资质图被标记为“需重拍”</div>
      </div>
    </v-dialog>
  </div>
</template>

<style scoped>
.fqual {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.fqual-thumb {
  position: relative;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--fill-3);
  cursor: zoom-in;
  flex-shrink: 0;
}
.fqual-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.fqual-flag {
  position: absolute;
  left: 4px;
  bottom: 4px;
  padding: 0 4px;
  border-radius: 2px;
  background: var(--primary);
  color: var(--on-primary);
  font-size: 11px;
  line-height: 16px;
}
.fqual-flag--error {
  background: var(--error);
}
.fqual-viewer {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.fqual-viewer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--line-1);
  font-size: 13px;
  color: var(--text-2);
}
.fqual-viewer-close {
  border: 0;
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  display: inline-flex;
}
.fqual-viewer-body {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  background: var(--media-backdrop);
  min-height: 400px;
}
.fqual-viewer-body img {
  max-width: 620px;
  max-height: 480px;
  object-fit: contain;
}
.fqual-nav {
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: #FFFFFF1F;
  color: #FFFFFF;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.fqual-nav:hover {
  background: #FFFFFF33;
}
.fqual-viewer-note {
  padding: 10px 16px;
  background: var(--error-container);
  color: var(--on-error-container);
  font-size: 13px;
}
</style>
