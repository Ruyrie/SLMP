<script setup lang="ts">
import { computed } from 'vue'
import { useUiStore } from '@/stores/ui'
import { copyText, downloadRecoveryCodeImage, formatRecoveryCode } from '../accountForm'

const props = withDefaults(
  defineProps<{
    code: string
    phoneMasked?: string
    generatedAt?: string
    label?: string
    compact?: boolean
  }>(),
  {
    phoneMasked: '',
    generatedAt: '',
    label: '你的恢复码 · 16 位字母和数字',
    compact: false
  }
)

const ui = useUiStore()
const formatted = computed(() => formatRecoveryCode(props.code))

async function copy(): Promise<void> {
  const ok = await copyText(formatted.value)
  ui.toast(ok ? '已复制恢复码' : '复制失败，请手动选择恢复码', ok ? 'success' : 'error')
}

function download(): void {
  const ok = downloadRecoveryCodeImage(props.code, props.phoneMasked, props.generatedAt)
  ui.toast(
    ok ? '已下载恢复码图片' : '下载失败，请截图或抄下恢复码',
    ok ? 'success' : 'error'
  )
}
</script>

<template>
  <div class="rc-card" :class="{ 'rc-card--compact': compact }">
    <div class="rc-label">{{ label }}</div>
    <div class="rc-val">{{ formatted }}</div>
    <div class="rc-ops">
      <v-btn variant="outlined" prepend-icon="content_copy" @click="copy">复制</v-btn>
      <v-btn variant="outlined" prepend-icon="download" @click="download">下载为图片</v-btn>
    </div>
  </div>
</template>

<style scoped>
.rc-card {
  padding: 16px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  text-align: center;
}
.rc-label {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.rc-val {
  margin: 6px 0 12px;
  font-family: Consolas, 'SFMono-Regular', Menlo, monospace;
  font-size: 28px;
  line-height: 40px;
  font-weight: 600;
  letter-spacing: 2px;
  color: var(--text-1);
  white-space: nowrap;
}
.rc-ops {
  display: flex;
  justify-content: center;
  gap: 8px;
}
.rc-card--compact .rc-val {
  font-size: 24px;
  line-height: 34px;
}
</style>
