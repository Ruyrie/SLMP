<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError, formatDateTime } from '@zmt/shared'
import { completeTodo, fetchTodoDetail, ignoreTodo } from '@/api/today'
import type { TodoDetailVO } from '@/api/today'
import { useRole } from '@/composables/useRole'
import { useUiStore } from '@/stores/ui'
import { statusTag, timeLabelText, todoIcon, todoTone, useTodayLinks } from '../todo'

const props = defineProps<{
  modelValue: boolean
  todoId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'changed'): void
}>()

const ui = useUiStore()
const { isManager } = useRole()
const { goRegister, go } = useTodayLinks()

const detail = ref<TodoDetailVO | null>(null)
const status = ref<'loading' | 'ready' | 'error'>('loading')
const error = ref('')

const doneOpen = ref(false)
const ignoreOpen = ref(false)
const ignoreReason = ref('')
const ignoreError = ref('')
const submitting = ref(false)
const doneSubmitting = ref(false)

const statusInfo = computed(() => (detail.value ? statusTag(detail.value) : null))
const canOperate = computed(() => !!detail.value && !!detail.value.can_register)
const hasActions = computed(() => {
  if (!detail.value) return false
  const item = detail.value
  if (item.display_status === 'DONE' || item.display_status === 'IGNORED') return false
  return !!(item.can_ignore || item.can_complete || item.can_register)
})

async function load(): Promise<void> {
  if (!props.todoId) return
  status.value = 'loading'
  error.value = ''
  try {
    detail.value = await fetchTodoDetail(props.todoId)
    status.value = 'ready'
  } catch (e) {
    status.value = 'error'
    error.value = e instanceof ApiError ? e.message : '待办加载失败，请稍后重试'
  }
}

watch(
  () => [props.modelValue, props.todoId] as const,
  ([open]) => {
    if (open) void load()
  },
  { immediate: true }
)

function close(): void {
  emit('update:modelValue', false)
}

function openDone(): void {
  if (!detail.value) return
  doneOpen.value = true
  emit('update:modelValue', false)
}

function openIgnore(): void {
  if (!detail.value) return
  ignoreReason.value = ''
  ignoreError.value = ''
  ignoreOpen.value = true
  emit('update:modelValue', false)
}

function register(): void {
  if (!detail.value) return
  const link = detail.value.register_link
  doneOpen.value = false
  ignoreOpen.value = false
  emit('update:modelValue', false)
  void goRegister(link)
}

function batchJump(): void {
  const link = detail.value?.related_batch?.link
  if (link) void go(link)
}

function handleConflict(e: unknown): boolean {
  const apiError = e as ApiError
  if (apiError?.code === 40903) {
    ui.toast('内容已更新，请刷新后再修改', 'error')
    doneOpen.value = false
    ignoreOpen.value = false
    void load()
    emit('changed')
    return true
  }
  return false
}

async function confirmDone(): Promise<void> {
  if (!detail.value?.todo_id || doneSubmitting.value) return
  doneSubmitting.value = true
  try {
    detail.value = await completeTodo(detail.value.todo_id, detail.value.version ?? 0)
    doneOpen.value = false
    ui.toast('已标记完成')
    emit('changed')
  } catch (e) {
    if (!handleConflict(e)) {
      ui.toast(e instanceof ApiError && e.message ? e.message : '网络连接不稳定，没有提交成功。请稍后重试。', 'error')
    }
  } finally {
    doneSubmitting.value = false
  }
}

function reasonLength(): number {
  return Array.from(ignoreReason.value.trim()).length
}

async function confirmIgnore(): Promise<void> {
  if (!detail.value?.todo_id || submitting.value) return
  ignoreError.value = ''
  if (!ignoreReason.value.trim()) {
    ignoreError.value = '请填写忽略原因'
    return
  }
  if (reasonLength() > 100) {
    ignoreError.value = '忽略原因不能超过 100 字'
    return
  }
  submitting.value = true
  try {
    detail.value = await ignoreTodo(detail.value.todo_id, detail.value.version ?? 0, ignoreReason.value.trim())
    ignoreOpen.value = false
    ui.toast('已忽略这条待办')
    emit('changed')
  } catch (e) {
    if (!handleConflict(e)) {
      ui.toast(e instanceof ApiError && e.message ? e.message : '网络连接不稳定，没有提交成功。请稍后重试。', 'error')
    }
  } finally {
    submitting.value = false
  }
}

function ignoreText(item: TodoDetailVO): string {
  const person = item.ignored_by?.name || ''
  const at = item.ignored_at ? formatDateTime(item.ignored_at) : ''
  return [person, at].filter(Boolean).join(' · ')
}
</script>

<template>
  <zw-drawer
    :model-value="modelValue"
    title="待办详情"
    :sub="detail ? `${detail.farm_name || ''} · ${detail.category_text || ''}待办` : undefined"
    :width="560"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-skeleton-loader v-if="status === 'loading'" type="heading, text, text, image" />

    <zw-empty
      v-else-if="status === 'error'"
      icon="cloud_off"
      title="待办加载失败"
      :desc="error"
      tone="error"
    >
      <v-btn variant="outlined" prepend-icon="refresh" @click="load">重新加载</v-btn>
    </zw-empty>

    <template v-else-if="detail">
      <div class="d-flex ga-2 mb-2">
        <zw-tag v-if="statusInfo" :type="statusInfo.type" :icon="statusInfo.icon">{{ statusInfo.text }}</zw-tag>
        <zw-tag>{{ detail.category_text || '待办' }}</zw-tag>
      </div>
      <div class="title-page">{{ detail.title }}</div>
      <div class="zw-muted mb-4">{{ detail.subtitle || detail.sub_text }}</div>

      <zw-notice
        v-if="detail.notice_text"
        class="mb-4"
        :type="detail.display_status === 'OVERDUE' ? 'warning' : 'info'"
        :icon="detail.display_status === 'OVERDUE' ? 'schedule' : 'info'"
      >
        {{ detail.notice_text }}
      </zw-notice>

      <zw-desc :cols="1" bordered label-width="112">
        <zw-desc-item label="来源模块">{{ detail.source_module_name || '—' }}</zw-desc-item>
        <zw-desc-item label="截止时间">
          <div>{{ detail.due_text || timeLabelText(detail) }}</div>
          <div v-if="detail.due_hint" class="zw-faint body-s">{{ detail.due_hint }}</div>
        </zw-desc-item>
        <zw-desc-item label="接收人">
          <div>{{ detail.receivers_text || '—' }}</div>
          <div v-if="detail.receivers_hint" class="zw-faint body-s">{{ detail.receivers_hint }}</div>
        </zw-desc-item>
        <zw-desc-item label="所属养殖场">{{ detail.farm_name || '—' }}</zw-desc-item>
        <zw-desc-item v-if="detail.detail_label" :label="detail.detail_label">{{ detail.detail_value || '—' }}</zw-desc-item>
        <zw-desc-item label="提醒">
          {{ detail.remind_text || (detail.remind_count ? `已提醒 ${detail.remind_count} 次` : '未提醒') }}
        </zw-desc-item>
        <zw-desc-item label="完成条件">{{ detail.complete_condition || '—' }}</zw-desc-item>
        <zw-desc-item v-if="detail.display_status === 'DONE' || detail.display_status === 'IGNORED'" label="完成方式">
          <div>{{ detail.complete_way_text || (detail.display_status === 'IGNORED' ? '已忽略' : '—') }}</div>
          <div v-if="detail.completed_at" class="zw-faint body-s">{{ formatDateTime(detail.completed_at) }}{{ detail.completed_by?.name ? ` · ${detail.completed_by.name}` : '' }}</div>
        </zw-desc-item>
        <zw-desc-item v-if="detail.display_status === 'IGNORED'" label="忽略原因">{{ detail.ignore_reason || detail.ignore_text || '—' }}</zw-desc-item>
        <zw-desc-item v-if="detail.display_status === 'IGNORED'" label="忽略人">{{ ignoreText(detail) || '—' }}</zw-desc-item>
      </zw-desc>

      <template v-if="detail.related_batch">
        <div class="zw-section-title mt-6">关联批次</div>
        <div class="t03-ctx" style="cursor: pointer" @click="batchJump">
          <span class="zw-ico-tile" :class="detail.todo_type === 'WITHDRAWAL_END' ? 'zw-ico-tile--warning' : 'zw-ico-tile--primary'">
            <zw-icon :name="detail.todo_type === 'WITHDRAWAL_END' ? 'medication' : 'fence'" :size="18" />
          </span>
          <div style="flex: 1; min-width: 0">
            <div class="zw-cell-main"><span class="num">{{ detail.related_batch.batch_no }}</span> · {{ detail.related_batch.pen_name }}</div>
            <div class="zw-cell-sub">{{ detail.related_batch.desc }}</div>
          </div>
          <zw-tag v-if="detail.related_batch.tag_text" type="warning" size="small">{{ detail.related_batch.tag_text }}</zw-tag>
          <zw-icon name="chevron_right" :size="20" color="text-3" />
        </div>
      </template>
    </template>

    <template v-if="hasActions" #foot>
      <v-btn v-if="isManager && detail?.can_ignore" variant="text" color="primary" class="mr-auto" @click="openIgnore">忽略</v-btn>
      <v-btn
        v-if="isManager && detail?.can_complete"
        variant="outlined"
        prepend-icon="done"
        @click="openDone"
      >标记完成</v-btn>
      <v-btn v-if="canOperate" color="primary" :prepend-icon="todoIcon(detail)" @click="register">去登记</v-btn>
    </template>
  </zw-drawer>

  <zw-dialog v-model="doneOpen" title="标记完成这条待办？" icon="task_alt" tone="info" :width="420">
    <div class="t03-ctx" v-if="detail">
      <span class="zw-ico-tile" :class="'zw-ico-tile--' + todoTone(detail)">
        <zw-icon :name="todoIcon(detail)" :size="18" />
      </span>
      <div>
        <div class="zw-cell-main">{{ detail.title }}</div>
        <div class="zw-cell-sub">{{ detail.category_text }} · {{ detail.subtitle }}</div>
      </div>
    </div>
    <p class="zw-muted">{{ detail?.mark_done_tip || '手动标记完成不会自动生成业务记录，相关库存也不会扣减。建议去登记。' }}</p>
    <template #actions>
      <v-btn variant="outlined" @click="register">去登记</v-btn>
      <v-btn color="primary" :loading="doneSubmitting" @click="confirmDone">标记完成</v-btn>
    </template>
  </zw-dialog>

  <zw-dialog v-model="ignoreOpen" title="忽略这条待办？" icon="warning" :width="420">
    <div class="t03-ctx" v-if="detail">
      <span class="zw-ico-tile" :class="'zw-ico-tile--' + todoTone(detail)">
        <zw-icon :name="todoIcon(detail)" :size="18" />
      </span>
      <div>
        <div class="zw-cell-main">{{ detail.title }}</div>
        <div class="zw-cell-sub">{{ detail.category_text }} · {{ detail.subtitle }}</div>
      </div>
    </div>
    <p class="zw-muted mb-3">忽略后不再提醒，也不计入完成率。请写明原因。</p>
    <zw-form-item label="忽略原因" required :error="ignoreError">
      <v-textarea
        v-model="ignoreReason"
        rows="2"
        :counter="100"
        persistent-counter
        autofocus
        @update:model-value="ignoreError = ''"
      />
    </zw-form-item>
    <template #actions>
      <v-btn variant="outlined" @click="ignoreOpen = false">取消</v-btn>
      <v-btn color="primary" :loading="submitting" @click="confirmIgnore">忽略</v-btn>
    </template>
  </zw-dialog>
</template>

<style scoped>
.t03-ctx {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--fill-1);
}
</style>
