<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { AdminMessage, MessageFilter } from '@/api/messages'
import { useAdminStore } from '@/stores/admin'
import { useAdminMessagesStore } from '@/stores/messages'
import { messageTarget } from '@/utils/messageTarget'

const admin = useAdminStore()
const messages = useAdminMessagesStore()
const router = useRouter()
const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
const drawerElement = () => document.querySelector<HTMLElement>('[role="dialog"][aria-label="管理端消息"]')
const tabs = computed(() => [
  { value: 'ALL', label: `全部 (${messages.allCount})` },
  { value: 'UNREAD', label: `未读 (${admin.unread})` }
])
const targetOf = (message: AdminMessage) => messageTarget(message, router, admin.hasPerm)

async function openMessage(message: AdminMessage): Promise<void> {
  const session = messages.session
  const target = targetOf(message)
  if (!(await messages.markRead(message))) return
  if (session !== messages.session || !admin.loaded || admin.mustChangePassword) return
  if (target) {
    messages.close()
    await router.push(target)
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    messages.close()
  } else if (event.key === 'Tab') {
    const root = drawerElement()
    const buttons = Array.from(root?.querySelectorAll<HTMLButtonElement>('button:not([disabled])') || [])
      .filter((button) => button.offsetParent !== null)
    const first = buttons[0]
    const last = buttons[buttons.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first?.focus()
    }
  }
}

onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  await nextTick()
  drawerElement()?.querySelector<HTMLButtonElement>('button[aria-label="关闭"]')?.focus()
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  if (previousFocus?.isConnected) previousFocus.focus()
})

</script>

<template>
  <zw-drawer :model-value="messages.open" title="管理端消息" sub="查看本人收到的消息与提醒" :width="480"
    role="dialog" aria-label="管理端消息" aria-modal="true"
    @update:model-value="!$event && messages.close()">
    <div class="admin-messages-tools">
      <zw-seg :items="tabs" :model-value="messages.filter"
        @update:model-value="messages.setFilter($event as MessageFilter)" />
      <v-btn variant="text" size="small" :disabled="messages.loading || messages.busy" @click="messages.load()">刷新</v-btn>
    </div>
    <v-btn class="mb-4" variant="text" size="small" :disabled="!admin.unread || messages.loading || messages.busy"
      :loading="messages.busy" @click="messages.markRead()">全部标为已读</v-btn>
    <v-alert v-if="messages.operationError" type="error" variant="tonal" density="compact" class="mb-4" role="alert">
      {{ messages.operationError }}
    </v-alert>
    <div v-if="messages.loading" class="admin-messages-state" role="status">
      <v-progress-circular indeterminate color="primary" :size="28" />
      <span>正在加载消息…</span>
    </div>
    <zw-empty v-else-if="messages.error" icon="cloud_off" title="消息加载失败" :desc="messages.error">
      <v-btn variant="outlined" @click="messages.load()">重试</v-btn>
    </zw-empty>
    <zw-empty v-else-if="!messages.list.length" icon="notifications_none"
      :title="messages.filter === 'UNREAD' ? '暂无未读消息' : '暂无消息'" />
    <ul v-else class="admin-messages-list" aria-label="管理端消息列表">
      <li v-for="message in messages.list" :key="message.message_id" class="admin-message" :class="{ unread: !message.read }">
        <div class="admin-message-meta">
          <span>{{ message.msg_type_text || '系统消息' }}</span>
          <time :datetime="message.created_at">{{ message.created_at?.replace('T', ' ').slice(0, 16) }}</time>
          <zw-tag v-if="!message.read" type="primary" size="small">未读</zw-tag>
        </div>
        <div class="admin-message-title">{{ message.title }}</div>
        <p v-if="message.summary" class="admin-message-summary">{{ message.summary }}</p>
        <div class="admin-message-actions">
          <v-btn v-if="!message.read" variant="text" size="small" :disabled="messages.busy"
            @click="messages.markRead(message)">标为已读</v-btn>
          <v-btn v-if="targetOf(message)" variant="text" color="primary" size="small" :disabled="messages.busy"
            @click="openMessage(message)">查看相关事项<zw-icon name="chevron_right" :size="16" /></v-btn>
        </div>
      </li>
    </ul>
    <template #foot>
      <span class="zw-foot-note">共 {{ messages.total }} 条</span>
      <v-btn variant="text" :disabled="messages.page <= 1 || messages.loading || messages.busy"
        @click="messages.load(messages.page - 1)">上一页</v-btn>
      <span>{{ messages.page }} / {{ Math.max(1, Math.ceil(messages.total / messages.size)) }}</span>
      <v-btn variant="text" :disabled="messages.page * messages.size >= messages.total || messages.loading || messages.busy"
        @click="messages.load(messages.page + 1)">下一页</v-btn>
    </template>
  </zw-drawer>
</template>

<style scoped>
.admin-messages-tools { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.admin-messages-list { list-style: none; padding: 0; margin: 0; }
.admin-message { padding: 16px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); margin-bottom: 12px; }
.admin-message.unread { border-left: 3px solid var(--primary); background: var(--primary-subtle); }
.admin-message-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-size: 12px; color: var(--text-3); }
.admin-message-meta time { margin-left: auto; }
.admin-message-title { margin-top: 8px; font-weight: 600; overflow-wrap: anywhere; }
.admin-message-summary { margin-top: 8px; color: var(--text-2); font-size: 13px; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.admin-message-actions { display: flex; justify-content: flex-end; margin-top: 8px; }
.admin-messages-state { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 48px 0; color: var(--text-3); }
</style>
