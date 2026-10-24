<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, dayjs } from '@zmt/shared'
import { useMessagesStore } from '@/stores/today/messages'
import { useFarmStore } from '@/stores/farm'
import { useRole } from '@/composables/useRole'
import { useUiStore } from '@/stores/ui'
import type { MessageBriefVO } from '@/api/today'
import {
  messageIcon,
  messageTag,
  messageTarget,
  messageTime,
  messageTone,
  messageActionText,
  payloadArr,
  payloadNum,
  payloadStr,
  statusTag,
  todoIcon,
  todoTone,
  useTodayLinks
} from './todo'
import ReminderDrawer from './components/ReminderDrawer.vue'

const route = useRoute()
const router = useRouter()
const farm = useFarmStore()
const store = useMessagesStore()
const ui = useUiStore()
const { isStaff } = useRole()
const { goRegister, goTarget } = useTodayLinks()

const {
  query,
  list,
  filteredUnread,
  status,
  error,
  selectedId,
  detail,
  detailStatus,
  detailError,
  todoDetail,
  todoStatus,
  todoError,
  reminderOpen,
  hasMore,
  loadingMore,
  acceptingId
} = storeToRefs(store)

const readingAll = ref(false)
const rejectOpen = ref(false)
const rejectTarget = ref<MessageBriefVO | null>(null)
const rejecting = ref(false)
const undoOpen = ref(false)
const deletedId = ref<number | null>(null)
let undoTimer = 0

const ownerTypes = [
  { title: '全部类型', value: '' },
  { title: '待办提醒', value: 'TODO' },
  { title: 'AI 诊断', value: 'AI' },
  { title: '审核结果', value: 'AUDIT' },
  { title: '成员与养殖场', value: 'MEMBER' },
  { title: '批号停用', value: 'LOT' },
  { title: '平台通知', value: 'NOTICE' }
]
const staffTypes = [
  { title: '全部类型', value: '' },
  { title: '待办提醒', value: 'TODO' },
  { title: 'AI 诊断', value: 'AI' },
  { title: '成员与养殖场', value: 'MEMBER' },
  { title: '平台通知', value: 'NOTICE' }
]
const typeItems = computed(() => (isStaff.value ? staffTypes : ownerTypes))

const category = computed({
  get: () => store.query.category,
  set: (value: string) => store.setCategory(value || '')
})
const farmId = computed({
  get: () => store.query.farmId,
  set: (value: number | null) => store.setFarm(value ?? null)
})
const unreadMode = computed(() => (store.query.unreadOnly ? 'unread' : 'all'))

const farmItems = computed(() => {
  const map = new Map<number, string>()
  for (const item of farm.farms) {
    if (item.farm_id) map.set(item.farm_id, item.name || '养殖场')
  }
  for (const message of list.value) {
    const id = message.farm?.farm_id
    if (id && !map.has(id)) map.set(id, message.farm?.name || '养殖场')
  }
  const items: { title: string; value: number | null }[] = [{ title: '全部养殖场', value: null }]
  for (const [value, title] of map) items.push({ title, value })
  return items
})

const desc = computed(() =>
  isStaff.value
    ? '只收到与你相关的消息 · 只保留最近 180 天'
    : '待办提醒、AI 诊断、审核结果和平台通知 · 只保留最近 180 天'
)

const detailTag = computed(() => (detail.value ? messageTag(detail.value) : null))
const detailTarget = computed(() => (detail.value ? messageTarget(detail.value) : null))
const detailTodoStatus = computed(() => (todoDetail.value ? statusTag(todoDetail.value) : null))

const noticeParagraphs = computed(() => {
  const content = detail.value ? payloadStr(detail.value, 'content') : ''
  return content
    .split(/\r?\n+/)
    .map((part) => part.trim())
    .filter(Boolean)
})

const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function momentText(value?: string, withWeek = false): string {
  if (!value) return '—'
  const date = dayjs(value)
  if (!date.isValid()) return value
  const prefix = date.year() === dayjs().year() ? date.format('M月D日') : date.format('YYYY年M月D日')
  return `${prefix}${withWeek ? ` ${WEEK_LABELS[date.day()]}` : ''} ${date.format('HH:mm')}`
}

function shortMoment(value?: string): string {
  return momentText(value)
}

function invitePending(message: MessageBriefVO): boolean {
  return message.action?.action_type === 'INVITE' && message.action.status === 'PENDING' && !!message.action.can_accept
}

function isPendingInviteDetail(): boolean {
  return (
    detail.value?.msg_type === 'FARM_INVITE' &&
    detail.value.action?.status === 'PENDING' &&
    !!detail.value.action.can_accept
  )
}

function inviteExpireText(): string {
  if (!detail.value) return '—'
  const value = detail.value.action?.expire_at || payloadStr(detail.value, 'expire_at')
  if (!value) return detail.value.action?.status_text || '—'
  return `${dayjs(value).format('M月D日 HH:mm')} 前处理`
}

function inviteStatusText(): string {
  if (!detail.value) return '—'
  return detail.value.action?.status_text || payloadStr(detail.value, 'status') || '—'
}

function openReject(message: MessageBriefVO): void {
  rejectTarget.value = message
  rejectOpen.value = true
}

async function acceptMessage(message: MessageBriefVO): Promise<void> {
  const action = message.action
  if (!action?.action_id) return
  try {
    const name = await store.acceptInvite(action.action_id, message.farm?.name)
    ui.toast(`已加入${name || message.farm?.name || '养殖场'}`)
  } catch (e) {
    ui.toast(e instanceof ApiError ? e.message : '没有接受邀请，请稍后重试', 'error')
  }
}

async function confirmReject(): Promise<void> {
  const action = rejectTarget.value?.action
  if (!action?.action_id) return
  rejecting.value = true
  try {
    await store.rejectInvite(action.action_id)
    rejectOpen.value = false
    ui.toast('已拒绝邀请')
  } catch (e) {
    ui.toast(e instanceof ApiError ? e.message : '没有拒绝成功，请稍后重试', 'error')
  } finally {
    rejecting.value = false
  }
}

async function readAll(): Promise<void> {
  if (readingAll.value) return
  readingAll.value = true
  try {
    const count = await store.readAll()
    ui.toast(count ? '已全部标为已读' : '没有未读消息', 'info')
  } catch (e) {
    ui.toast(e instanceof ApiError ? e.message : '操作失败，请稍后重试', 'error')
  } finally {
    readingAll.value = false
  }
}

function onUnread(value: string | number): void {
  store.setUnreadOnly(String(value) === 'unread')
}

function onIntersect(visible: boolean): void {
  if (visible) void store.loadMore()
}

function openMessage(id?: number): void {
  if (id) void store.select(id)
}

async function removeMessage(): Promise<void> {
  const id = detail.value?.message_id
  if (!id) return
  try {
    await store.remove(id)
    deletedId.value = id
    undoOpen.value = true
    window.clearTimeout(undoTimer)
    undoTimer = window.setTimeout(() => {
      undoOpen.value = false
      deletedId.value = null
    }, 3000)
  } catch (e) {
    ui.toast(e instanceof ApiError ? e.message : '删除失败，请稍后重试', 'error')
  }
}

async function undoDelete(): Promise<void> {
  const id = deletedId.value
  window.clearTimeout(undoTimer)
  undoOpen.value = false
  if (!id) return
  try {
    await store.restore(id)
    ui.toast('已恢复这条消息')
  } catch (e) {
    ui.toast(e instanceof ApiError ? e.message : '撤销失败，请稍后重试', 'error')
  } finally {
    deletedId.value = null
  }
}

async function navigateDetail(): Promise<void> {
  if (!detail.value || !detailTarget.value) return
  const farmIdValue = detail.value.farm?.farm_id
  if (farmIdValue && farmIdValue !== farm.currentFarmId) {
    try {
      await farm.switchTo(farmIdValue)
      ui.toast(`已切换到${detail.value.farm?.name || '目标养殖场'}`)
    } catch (e) {
      ui.toast(e instanceof ApiError ? e.message : '切换养殖场失败，请稍后重试', 'error')
      return
    }
  }
  goTarget(detailTarget.value)
}

function registerTodo(): void {
  if (todoDetail.value) void goRegister(todoDetail.value.register_link)
}

function openTodoDetail(): void {
  const id = detail.value?.todo_id
  if (id) void router.push({ name: 'WU-T03', query: { todo_id: String(id) } })
}

function retryDetail(): void {
  if (selectedId.value) void store.loadDetail(selectedId.value)
}

function todoTitle(): string {
  return todoDetail.value?.title || detail.value?.title || ''
}

function todoSubtitle(): string {
  const item = todoDetail.value
  if (!item) return ''
  return item.subtitle || item.sub_text || ''
}

function todoRelatedText(): string {
  const batch = todoDetail.value?.related_batch
  if (!batch) return '—'
  return [batch.batch_no, batch.pen_name, batch.desc].filter(Boolean).join(' · ')
}

function completeText(): string {
  const item = todoDetail.value
  if (!item) return '—'
  if (item.display_status === 'DONE') {
    return [item.completed_text, item.complete_way_text].filter(Boolean).join(' · ') || '已完成'
  }
  if (item.display_status === 'IGNORED') {
    return [item.ignore_reason, item.ignore_text].filter(Boolean).join(' · ') || '已忽略'
  }
  return item.display_status_text || '待处理'
}

onMounted(async () => {
  await store.fetchList(true)
  const id = Number(route.query.id)
  if (Number.isFinite(id) && id > 0) await store.openFromPush(id)
})

onBeforeUnmount(() => window.clearTimeout(undoTimer))
</script>

<template>
  <zw-shell side="user" active="WU-T04" :all-farms="farm.viewAll" :crumbs="['消息中心']">
    <zw-page-head title="消息中心" :desc="desc">
      <template #actions>
        <v-btn variant="outlined" prepend-icon="done_all" :loading="readingAll" @click="readAll">全部标为已读</v-btn>
        <v-btn variant="outlined" prepend-icon="settings" @click="reminderOpen = true">提醒设置</v-btn>
      </template>
    </zw-page-head>

    <section class="zw-card t04-wrap">
      <aside class="t04-side">
        <div class="t04-filter">
          <v-select
            v-model="category"
            :items="typeItems"
            item-title="title"
            item-value="value"
            class="t04-select-type"
            aria-label="消息类型"
          />
          <v-select
            v-if="!isStaff"
            v-model="farmId"
            :items="farmItems"
            item-title="title"
            item-value="value"
            class="t04-select-farm"
            aria-label="养殖场"
          />
          <span class="t04-filter-grow"></span>
          <zw-seg
            size="small"
            :model-value="unreadMode"
            :items="[{ label: '全部', value: 'all' }, { label: '未读', value: 'unread', count: filteredUnread }]"
            @update:model-value="onUnread"
          />
        </div>

        <div class="t04-scroll">
          <v-skeleton-loader v-if="status === 'loading'" type="list-item-two-line@6" />

          <zw-empty
            v-else-if="status === 'forbidden'"
            icon="lock"
            title="无权查看"
            :desc="error"
            tone="error"
          />

          <zw-empty v-else-if="status === 'error'" icon="cloud_off" title="消息加载失败" :desc="error" tone="error">
            <v-btn variant="outlined" prepend-icon="refresh" @click="store.fetchList(true)">重新加载</v-btn>
          </zw-empty>

          <zw-empty
            v-else-if="status === 'empty'"
            icon="notifications"
            title="还没有消息"
            desc="待办提醒、AI 诊断结果、审核结果和平台通知都会出现在这里。"
          />

          <zw-empty
            v-else-if="status === 'filtered_empty'"
            icon="notifications_none"
            :title="query.unreadOnly ? '没有未读消息。' : '没有符合筛选条件的消息。'"
            desc="换一个类型、养殖场或未读条件试试。"
          >
            <v-btn variant="text" color="primary" @click="store.clearFilters()">清除筛选</v-btn>
          </zw-empty>

          <template v-else>
            <template v-for="group in store.groups" :key="group.title">
              <div class="t04-grp">{{ group.title }}</div>
              <div
                v-for="message in group.items"
                :key="message.message_id"
                class="t04-msg"
                :class="{ on: message.message_id === selectedId, 'is-unread': !message.read }"
                @click="openMessage(message.message_id)"
              >
                <span v-if="!message.read" class="t04-unread"></span>
                <span class="zw-ico-tile" :class="'zw-ico-tile--' + messageTone(message)">
                  <zw-icon :name="messageIcon(message)" :size="18" />
                </span>
                <div class="t04-msg-main">
                  <div class="t04-msg-title">{{ message.title }}</div>
                  <div class="t04-msg-sub">
                    <zw-tag v-if="message.farm" type="farm" size="small">{{ message.farm.name }}</zw-tag>
                    <span>{{ message.summary || message.category_text }}</span>
                  </div>
                  <div v-if="invitePending(message)" class="t04-msg-btns">
                    <v-btn size="small" variant="outlined" @click.stop="openReject(message)">拒绝</v-btn>
                    <v-btn
                      size="small"
                      variant="tonal"
                      color="primary"
                      :loading="acceptingId === message.action?.action_id"
                      @click.stop="acceptMessage(message)"
                    >同意</v-btn>
                  </div>
                </div>
                <div class="t04-msg-r">
                  <span class="t04-time">{{ messageTime(message) }}</span>
                  <zw-tag v-if="messageTag(message)" :type="messageTag(message)?.type || 'neutral'" :icon="messageTag(message)?.icon" size="small">
                    {{ messageTag(message)?.text }}
                  </zw-tag>
                </div>
              </div>
            </template>

            <div v-if="loadingMore" class="t04-more"><v-progress-circular indeterminate size="20" width="2" /></div>
            <div v-else-if="hasMore" v-intersect="onIntersect" class="t04-sentinel"></div>
            <div v-else class="t04-end">只保留最近 180 天的消息</div>
          </template>
        </div>
      </aside>

      <div class="t04-detail">
        <v-skeleton-loader v-if="detailStatus === 'loading'" type="heading, text, text, image" />

        <zw-empty v-else-if="detailStatus === 'error'" icon="cloud_off" title="消息加载失败" :desc="detailError" tone="error">
          <v-btn variant="outlined" prepend-icon="refresh" @click="retryDetail">重新加载</v-btn>
        </zw-empty>

        <zw-empty
          v-else-if="!detail"
          icon="notifications_none"
          title="选择一条消息查看详情"
          desc="左侧列表中选择一条消息，这里会显示完整内容和相关操作。"
        />

        <template v-else>
          <div class="t04-dh">
            <span class="zw-ico-tile zw-ico-tile--lg" :class="'zw-ico-tile--' + messageTone(detail)">
              <zw-icon :name="messageIcon(detail)" :size="22" />
            </span>
            <div class="t04-dh-main">
              <div class="t04-dh-title" :class="{ 'zw-muted': detail.revoked }">{{ detail.title }}</div>
              <div class="t04-dh-meta">
                <zw-tag v-if="detail.farm" type="farm" size="small">{{ detail.farm.name }}</zw-tag>
                <span>{{ detail.category_text }} · {{ shortMoment(detail.created_at) }}</span>
                <zw-tag v-if="detailTag" :type="detailTag.type" :icon="detailTag.icon" size="small">{{ detailTag.text }}</zw-tag>
              </div>
            </div>
            <v-menu location="bottom end">
              <template #activator="{ props: menuProps }">
                <button v-bind="menuProps" class="zw-icon-btn" type="button" aria-label="更多">
                  <zw-icon name="more_horiz" :size="20" />
                </button>
              </template>
              <v-list density="compact">
                <v-list-item @click="removeMessage">
                  <v-list-item-title>删除消息</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>
          </div>

          <zw-empty v-if="detail.revoked" icon="undo" title="该通知已撤回" desc="管理员已撤回这条通知，内容不再显示。">
            <div class="zw-faint body-s">
              平台通知 · {{ momentText(payloadStr(detail, 'sent_at') || detail.created_at, true) }} 发送<template v-if="payloadStr(detail, 'revoked_at')"> · {{ momentText(payloadStr(detail, 'revoked_at'), true) }} 撤回</template>
            </div>
          </zw-empty>

          <zw-empty
            v-else-if="detail.target_status === 'DELETED'"
            icon="link_off"
            :title="detail.deleted_title || '该记录已被删除，无法查看'"
            :desc="detail.deleted_desc || '这条消息关联的记录已被删除，相关待办和记录不再显示。消息本身仍保留在消息中心。'"
          />

          <zw-empty
            v-else-if="['NO_PERMISSION', 'NOT_MEMBER', 'FARM_FROZEN'].includes(detail.target_status || '')"
            icon="lock"
            :title="detail.deny_title || '无权查看'"
            :desc="detail.deny_desc || '你已没有这条消息关联记录的查看权限。'"
          />

          <template v-else-if="detail.msg_type === 'FARM_INVITE'">
            <zw-desc :cols="1" bordered label-width="112" style="max-width: 560px">
              <zw-desc-item label="养殖场">{{ payloadStr(detail, 'farm_name') || detail.farm?.name || '—' }}</zw-desc-item>
              <zw-desc-item label="邀请人">
                {{ payloadStr(detail, 'inviter_name') }}<template v-if="payloadStr(detail, 'inviter_role_text')">（{{ payloadStr(detail, 'inviter_role_text') }}）</template>
              </zw-desc-item>
              <zw-desc-item label="你的角色">{{ payloadStr(detail, 'role_text') || detail.action?.role_text || '—' }}</zw-desc-item>
              <zw-desc-item label="负责栏舍">{{ payloadArr(detail, 'pen_names').join('、') || '—' }}</zw-desc-item>
              <zw-desc-item label="有效期">{{ inviteExpireText() }}</zw-desc-item>
            </zw-desc>
            <zw-notice class="mt-4" style="max-width: 560px">
              同意后加入{{ payloadStr(detail, 'farm_name') || detail.farm?.name || '该养殖场' }}，可以在顶部切换到这个养殖场，查看负责栏舍的待办。
            </zw-notice>
            <div v-if="isPendingInviteDetail()" class="t04-actions">
              <v-btn variant="outlined" @click="openReject(detail)">拒绝</v-btn>
              <v-btn
                color="primary"
                :loading="acceptingId === detail.action?.action_id"
                @click="acceptMessage(detail)"
              >同意</v-btn>
            </div>
            <div v-else class="t04-handled">{{ inviteStatusText() }}</div>
          </template>

          <template v-else-if="detail.msg_type === 'OWNER_TRANSFER'">
            <zw-desc :cols="1" bordered label-width="112" style="max-width: 560px">
              <zw-desc-item label="养殖场">{{ payloadStr(detail, 'farm_name') || detail.farm?.name || '—' }}</zw-desc-item>
              <zw-desc-item label="转让人">{{ payloadStr(detail, 'from_user_name') || '—' }}</zw-desc-item>
              <zw-desc-item label="受让人">{{ payloadStr(detail, 'to_user_name') || '—' }}</zw-desc-item>
              <zw-desc-item label="状态">{{ detail.action?.status_text || payloadStr(detail, 'status') || '—' }}</zw-desc-item>
              <zw-desc-item label="有效期">{{ payloadStr(detail, 'expire_at') ? momentText(payloadStr(detail, 'expire_at')) : '—' }}</zw-desc-item>
            </zw-desc>
            <zw-notice class="mt-4" style="max-width: 560px" icon="info">
              所有权转让需要双方确认，请到 App 或对应养殖场页面处理。
            </zw-notice>
          </template>

          <template v-else-if="detail.msg_type === 'NOTICE'">
            <div class="t04-notice-meta">
              {{ payloadStr(detail, 'sender_name') || '智牧通平台' }} · {{ momentText(payloadStr(detail, 'sent_at') || detail.created_at, true) }} 发送
            </div>
            <div v-if="payloadArr(detail, 'target_farm_names').length" class="t04-notice-meta">
              发送给：{{ payloadArr(detail, 'target_farm_names').join('、') }}
            </div>
            <div v-if="noticeParagraphs.length" class="t04-body">
              <p v-for="(paragraph, index) in noticeParagraphs" :key="index">{{ paragraph }}</p>
            </div>
            <zw-empty v-else icon="campaign" title="通知内容为空" desc="这条通知没有正文内容。" />
          </template>

          <template v-else-if="detail.category === 'TODO'">
            <template v-if="todoDetail">
              <div class="t04-todo-head">
                <span class="zw-ico-tile zw-ico-tile--lg" :class="'zw-ico-tile--' + todoTone(todoDetail)">
                  <zw-icon :name="todoIcon(todoDetail)" :size="22" />
                </span>
                <div class="t04-todo-main">
                  <div class="t04-todo-title">{{ todoTitle() }}</div>
                  <div class="t04-todo-sub">{{ todoSubtitle() }}</div>
                </div>
                <zw-tag v-if="detailTodoStatus" :type="detailTodoStatus.type" :icon="detailTodoStatus.icon">{{ detailTodoStatus.text }}</zw-tag>
              </div>
              <zw-desc :cols="1" bordered label-width="112" style="max-width: 560px">
                <zw-desc-item label="类别">{{ todoDetail.category_text || '—' }}</zw-desc-item>
                <zw-desc-item label="截止时间">{{ todoDetail.due_text || todoDetail.subtitle || '—' }}</zw-desc-item>
                <zw-desc-item label="关联批次">{{ todoRelatedText() }}</zw-desc-item>
                <zw-desc-item label="完成情况">{{ completeText() }}</zw-desc-item>
                <zw-desc-item v-if="todoDetail.ignore_reason" label="忽略原因">{{ todoDetail.ignore_reason }}</zw-desc-item>
              </zw-desc>
              <div class="t04-actions">
                <v-btn v-if="todoDetail.can_register" color="primary" :prepend-icon="todoIcon(todoDetail)" @click="registerTodo">去登记</v-btn>
                <v-btn v-else variant="outlined" @click="openTodoDetail">查看待办详情</v-btn>
              </div>
            </template>
            <v-skeleton-loader v-else-if="todoStatus === 'loading'" type="text, text" />
            <zw-empty v-else icon="link_off" title="待办加载失败" :desc="todoError || '这条消息关联的待办暂时无法查看。'" tone="error">
              <v-btn v-if="detail.todo_id" variant="outlined" prepend-icon="refresh" @click="store.loadTodoDetail(detail.todo_id)">重新加载</v-btn>
            </zw-empty>
          </template>

          <template v-else-if="detail.msg_type === 'AI_DIAGNOSIS'">
            <zw-desc :cols="1" bordered label-width="112" style="max-width: 560px">
              <zw-desc-item label="栏舍">{{ payloadStr(detail, 'pen_name') || '—' }}</zw-desc-item>
              <zw-desc-item label="批次"><span class="num">{{ payloadStr(detail, 'batch_no') || '—' }}</span></zw-desc-item>
              <zw-desc-item label="上报人">{{ payloadStr(detail, 'reporter_name') || '—' }}</zw-desc-item>
              <zw-desc-item label="上报时间">{{ momentText(payloadStr(detail, 'reported_at')) }}</zw-desc-item>
              <zw-desc-item label="发病数量">{{ payloadNum(detail, 'sick_count') !== null ? `${payloadNum(detail, 'sick_count')} 头` : '—' }}</zw-desc-item>
              <zw-desc-item label="紧急程度">
                <zw-tag v-if="payloadStr(detail, 'urgency_text')" :type="payloadStr(detail, 'urgency') === 'URGENT' ? 'error' : payloadStr(detail, 'urgency') === 'ATTENTION' ? 'warning' : 'neutral'" :icon="payloadStr(detail, 'urgency') === 'ATTENTION' ? 'priority_high' : undefined">
                  {{ payloadStr(detail, 'urgency_text') }}
                </zw-tag>
                <span v-else>—</span>
              </zw-desc-item>
            </zw-desc>
            <zw-notice class="mt-4" style="max-width: 560px">诊断结论和处理建议在诊断详情中查看。</zw-notice>
            <div class="t04-actions">
              <v-btn color="primary" append-icon="arrow_forward" :disabled="!detailTarget" @click="navigateDetail">查看诊断详情</v-btn>
            </div>
          </template>

          <template v-else-if="detail.msg_type === 'LOT_RECALLED'">
            <zw-desc :cols="1" bordered label-width="112" style="max-width: 560px">
              <zw-desc-item label="批号"><span class="num">{{ payloadStr(detail, 'lot_no') || '—' }}</span></zw-desc-item>
              <zw-desc-item label="药品">{{ payloadStr(detail, 'drug_name') || '—' }}</zw-desc-item>
              <zw-desc-item label="当前结存">
                {{ payloadNum(detail, 'stock_qty') !== null ? `${payloadNum(detail, 'stock_qty')} ${payloadStr(detail, 'unit_text')}` : '—' }}
              </zw-desc-item>
              <zw-desc-item label="停用原因">{{ payloadStr(detail, 'recall_reason') || '—' }}</zw-desc-item>
            </zw-desc>
            <div class="t04-actions">
              <v-btn color="primary" append-icon="arrow_forward" :disabled="!detailTarget" @click="navigateDetail">查看批号</v-btn>
            </div>
          </template>

          <template v-else-if="detail.category === 'AUDIT'">
            <zw-desc :cols="1" bordered label-width="112" style="max-width: 560px">
              <zw-desc-item v-if="payloadStr(detail, 'request_no')" label="申请编号"><span class="num">{{ payloadStr(detail, 'request_no') }}</span></zw-desc-item>
              <zw-desc-item v-if="payloadStr(detail, 'record_type_text')" label="记录类型">{{ payloadStr(detail, 'record_type_text') }}</zw-desc-item>
              <zw-desc-item v-if="payloadStr(detail, 'field_summary')" label="修改内容">{{ payloadStr(detail, 'field_summary') }}</zw-desc-item>
              <zw-desc-item label="处理结果">
                <zw-tag v-if="detailTag" :type="detailTag.type" :icon="detailTag.icon">{{ detailTag.text }}</zw-tag>
                <span v-else>{{ detail.level_text || '—' }}</span>
              </zw-desc-item>
              <zw-desc-item v-if="payloadStr(detail, 'reject_reason')" label="驳回原因">{{ payloadStr(detail, 'reject_reason') }}</zw-desc-item>
            </zw-desc>
            <div class="t04-actions" v-if="detailTarget">
              <v-btn color="primary" append-icon="arrow_forward" @click="navigateDetail">{{ messageActionText(detail) }}</v-btn>
            </div>
          </template>

          <template v-else>
            <p class="t04-summary">{{ detail.summary || '这条消息没有更多内容。' }}</p>
            <div class="t04-actions" v-if="detailTarget">
              <v-btn color="primary" append-icon="arrow_forward" @click="navigateDetail">{{ messageActionText(detail) }}</v-btn>
            </div>
          </template>
        </template>
      </div>
    </section>

    <ReminderDrawer v-model="reminderOpen" />

    <v-snackbar v-model="undoOpen" :timeout="3000" location="top" content-class="zw-snack-content" class="zw-snack">
      <span class="zw-snack-inner"><zw-icon name="delete" :size="18" fill />已删除这条消息</span>
      <template #actions>
        <button class="zw-snack-action" type="button" @click="undoDelete">撤销</button>
      </template>
    </v-snackbar>

    <zw-dialog v-model="rejectOpen" title="拒绝这条邀请？" icon="warning" :width="420">
      <p class="mb-2">拒绝后邀请失效，需要对方重新邀请。</p>
      <p class="zw-faint body-s">{{ rejectTarget?.title }}</p>
      <template #actions>
        <v-btn variant="outlined" @click="rejectOpen = false">取消</v-btn>
        <v-btn color="error" :loading="rejecting" @click="confirmReject">拒绝</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.t04-wrap {
  display: flex;
  height: calc(100vh - 174px);
  min-height: 560px;
  overflow: hidden;
}
.t04-side {
  width: 440px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--line-1);
  min-height: 0;
}
.t04-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-1);
  flex-shrink: 0;
}
.t04-select-type {
  width: 116px;
  flex: 0 0 auto;
}
.t04-select-farm {
  width: 132px;
  flex: 0 0 auto;
}
.t04-filter-grow {
  flex: 1;
}
.t04-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.t04-grp {
  padding: 12px 16px 4px 24px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  font-weight: 500;
}
.t04-msg {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 10px 16px 10px 24px;
  cursor: pointer;
  border-left: 2px solid transparent;
}
.t04-msg:hover {
  background: var(--fill-1);
}
.t04-msg.on {
  background: var(--primary-subtle);
  border-left-color: var(--primary);
}
.t04-unread {
  position: absolute;
  left: 10px;
  top: 24px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
}
.t04-msg-main {
  flex: 1;
  min-width: 0;
}
.t04-msg-title {
  font-size: 14px;
  line-height: 22px;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.t04-msg.is-unread .t04-msg-title {
  font-weight: 600;
}
.t04-msg-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
}
.t04-msg-r {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}
.t04-time {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}
.t04-msg-btns {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.t04-end {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
}
.t04-more {
  display: flex;
  justify-content: center;
  padding: 12px;
}
.t04-sentinel {
  height: 1px;
}
.t04-detail {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding: 24px 32px;
}
.t04-dh {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding-bottom: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--line-1);
}
.t04-dh-main {
  flex: 1;
  min-width: 0;
}
.t04-dh-title {
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
  color: var(--text-1);
}
.t04-dh-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-3);
}
.t04-notice-meta {
  font-size: 14px;
  line-height: 22px;
  color: var(--text-3);
  margin-bottom: 8px;
}
.t04-body {
  max-width: 680px;
  font-size: 14px;
  line-height: 24px;
  color: var(--text-1);
}
.t04-body p {
  margin: 0 0 8px;
}
.t04-actions {
  display: flex;
  gap: 8px;
  margin-top: 20px;
}
.t04-handled {
  margin-top: 12px;
  font-size: 14px;
  color: var(--text-3);
}
.t04-todo-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
}
.t04-todo-main {
  flex: 1;
  min-width: 0;
}
.t04-todo-title {
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
  color: var(--text-1);
}
.t04-todo-sub {
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
  margin-top: 2px;
}
.t04-summary {
  max-width: 680px;
  font-size: 14px;
  line-height: 24px;
  color: var(--text-1);
}
</style>
