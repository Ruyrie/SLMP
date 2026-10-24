<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useReminderStore } from '@/stores/today/reminder'
import type { ReminderPatch } from '@/api/today'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const store = useReminderStore()
const { setting, status, error } = storeToRefs(store)
const helpOpen = ref(false)

const notificationDenied = computed<boolean>(() => {
  if (typeof window === 'undefined' || !('Notification' in window)) return false
  return window.Notification.permission === 'denied'
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) void store.load(true)
  },
  { immediate: true }
)

const digestOptions = computed(() => {
  const options = ['06:30', '07:00', '07:30', '08:00'].map((value) => ({ title: value, value }))
  const current = setting.value?.digest_time
  if (current && !options.some((item) => item.value === current)) options.unshift({ title: current, value: current })
  return options
})

const quietStartOptions = computed(() => {
  const options = ['21:00', '22:00', '23:00'].map((value) => ({ title: value, value }))
  const current = setting.value?.quiet_start
  if (current && !options.some((item) => item.value === current)) options.unshift({ title: current, value: current })
  return options
})

const quietEndOptions = computed(() => {
  const options = [
    { title: '次日 06:00', value: '06:00' },
    { title: '次日 07:00', value: '07:00' },
    { title: '次日 08:00', value: '08:00' }
  ]
  const current = setting.value?.quiet_end
  if (current && !options.some((item) => item.value === current)) {
    options.unshift({ title: `次日 ${current}`, value: current })
  }
  return options
})

function save(patch: ReminderPatch, field: string): void {
  void store.save(patch, field)
}

function toggleTodo(value: boolean | null): void {
  save({ todo_remind_enabled: !!value }, 'todo_remind_enabled')
}

function toggleDigest(value: boolean | null): void {
  save({ digest_enabled: !!value }, 'digest_enabled')
}

function toggleQuiet(value: boolean | null): void {
  save({ quiet_enabled: !!value }, 'quiet_enabled')
}

function selectDigest(value: string | null): void {
  if (value) save({ digest_time: value }, 'digest_time')
}

function selectQuietStart(value: string | null): void {
  if (value) save({ quiet_start: value }, 'quiet_start')
}

function selectQuietEnd(value: string | null): void {
  if (value) save({ quiet_end: value }, 'quiet_end')
}
</script>

<template>
  <zw-drawer
    :model-value="modelValue"
    title="提醒设置"
    sub="对账号生效，覆盖你加入的全部养殖场"
    :width="560"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-skeleton-loader v-if="status === 'loading'" type="text, text, text, text" />

    <zw-empty v-else-if="status === 'error'" icon="cloud_off" title="设置加载失败" :desc="error" tone="error">
      <v-btn variant="outlined" prepend-icon="refresh" @click="store.load(true)">重新加载</v-btn>
    </zw-empty>

    <template v-else-if="setting">
      <zw-notice type="warning" icon="notifications" class="mb-4" v-if="notificationDenied">
        浏览器通知已关闭，网页上收不到待办提醒。
        <template #action><a @click="helpOpen = true">去开启</a></template>
      </zw-notice>
      <zw-notice class="mb-4" icon="info">App 推送和浏览器通知使用同一套设置，修改后立即保存。</zw-notice>

      <div class="zw-section-title">推送</div>
      <div class="mb-4">
        <div class="t03-set-row">
          <div class="t03-set-main">
            <div class="t03-set-t">待办提醒</div>
            <div class="t03-set-d">到达提醒时间时推送，标明所属养殖场</div>
          </div>
          <v-switch
            :model-value="!!setting.todo_remind_enabled"
            :loading="store.saving === 'todo_remind_enabled'"
            @update:model-value="toggleTodo"
          />
        </div>
        <div class="t03-set-row">
          <div class="t03-set-main">
            <div class="t03-set-t">每日待办汇总</div>
            <div class="t03-set-d">每天推送一条，只含你有权查看的待办</div>
          </div>
          <v-switch
            :model-value="!!setting.digest_enabled"
            :loading="store.saving === 'digest_enabled'"
            @update:model-value="toggleDigest"
          />
        </div>
        <div v-if="setting.digest_enabled" class="t03-set-row">
          <div class="t03-set-main">
            <div class="t03-set-t">汇总推送时间</div>
            <div class="t03-set-d">全部养殖场的待办合并为一条</div>
          </div>
          <v-select
            :items="digestOptions"
            :model-value="setting.digest_time"
            item-title="title"
            item-value="value"
            class="zw-w-120"
            :loading="store.saving === 'digest_time'"
            @update:model-value="selectDigest"
          />
        </div>
      </div>

      <div class="zw-section-title">免打扰</div>
      <div class="mb-4">
        <div class="t03-set-row">
          <div class="t03-set-main">
            <div class="t03-set-t">免打扰时段</div>
            <div class="t03-set-d">时段内推送延后发送，消息照常进入消息中心</div>
          </div>
          <v-switch
            :model-value="!!setting.quiet_enabled"
            :loading="store.saving === 'quiet_enabled'"
            @update:model-value="toggleQuiet"
          />
        </div>
        <template v-if="setting.quiet_enabled">
          <div class="t03-set-row">
            <div class="t03-set-main"><div class="t03-set-t">开始时间</div></div>
            <v-select
              :items="quietStartOptions"
              :model-value="setting.quiet_start"
              item-title="title"
              item-value="value"
              class="zw-w-120"
              :loading="store.saving === 'quiet_start'"
              @update:model-value="selectQuietStart"
            />
          </div>
          <div class="t03-set-row">
            <div class="t03-set-main">
              <div class="t03-set-t">结束时间</div>
              <div class="t03-set-d">早于开始时间时为次日</div>
            </div>
            <v-select
              :items="quietEndOptions"
              :model-value="setting.quiet_end"
              item-title="title"
              item-value="value"
              class="zw-w-140"
              :loading="store.saving === 'quiet_end'"
              @update:model-value="selectQuietEnd"
            />
          </div>
        </template>
      </div>

      <div class="zw-section-title">不受免打扰限制</div>
      <div class="t03-set-row">
        <span class="zw-ico-tile"><zw-icon name="campaign" :size="18" /></span>
        <div class="t03-set-main">
          <div class="t03-set-t">重要通知</div>
          <div class="t03-set-d">平台发布的重要通知，一定推送</div>
        </div>
        <span class="t03-set-v">始终推送</span>
      </div>
      <div class="t03-set-row">
        <span class="zw-ico-tile zw-ico-tile--ai"><zw-icon name="auto_awesome" :size="18" /></span>
        <div class="t03-set-main">
          <div class="t03-set-t">紧急的 AI 诊断结果</div>
          <div class="t03-set-d">发给上报人、所有者和场长</div>
        </div>
        <span class="t03-set-v">立即推送</span>
      </div>
    </template>

    <template #foot>
      <span class="zw-foot-note">修改后立即保存</span>
      <v-btn variant="outlined" @click="emit('update:modelValue', false)">关闭</v-btn>
    </template>
  </zw-drawer>

  <zw-dialog v-model="helpOpen" title="开启浏览器通知" icon="notifications" tone="info" :width="420">
    <p class="zw-muted">请点浏览器地址栏左侧的站点图标，在“网站设置”中把“通知”改为“允许”，然后刷新页面。</p>
    <template #actions><v-btn color="primary" @click="helpOpen = false">我知道了</v-btn></template>
  </zw-dialog>
</template>

<style scoped>
.t03-set-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line-1);
}
.t03-set-row:last-child {
  border-bottom: 0;
}
.t03-set-main {
  flex: 1;
  min-width: 0;
}
.t03-set-t {
  font-size: 14px;
  line-height: 22px;
  color: var(--text-1);
}
.t03-set-d {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.t03-set-v {
  font-size: 14px;
  color: var(--text-2);
}
</style>
