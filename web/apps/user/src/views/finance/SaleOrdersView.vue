<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import type { DataTableHeader } from 'vuetify'
import { formatMoney, formatNumber, formatPercent } from '@zmt/shared'
import { useSaleOrdersStore } from '@/stores/finance/saleOrders'
import { useSaleOrderFormStore } from '@/stores/finance/saleOrderForm'
import { useReceiveStore } from '@/stores/finance/receive'
import { useFarmReload } from '@/composables/useFarmReload'
import { queryId } from '@/composables/useRouteAction'
import { dateText, dueTone, paymentIcon, paymentTone } from '@/stores/finance/shared'
import SaleOrderFormDrawer from './components/SaleOrderFormDrawer.vue'
import ReceiveDialog from './components/ReceiveDialog.vue'

const store = useSaleOrdersStore()
const formStore = useSaleOrderFormStore()
const receive = useReceiveStore()
const route = useRoute()
const router = useRouter()
const { list } = storeToRefs(store)

const headers: DataTableHeader[] = [
  { title: '批次号', key: 'batchNo', sortable: false, width: 150 },
  { title: '出栏', key: 'saleDate', sortable: false, width: 150 },
  { title: '收购商', key: 'buyerName', sortable: false },
  { title: '销售金额', key: 'saleAmount', sortable: false, align: 'end', width: 130 },
  { title: '收款进度', key: 'progress', sortable: false, width: 160 },
  { title: '未收', key: 'unpaidAmount', sortable: false, align: 'end', width: 124 },
  { title: '收款状态', key: 'paymentStatus', sortable: false, width: 170 },
  { title: '操作', key: 'actions', sortable: false, align: 'end', width: 116 }
]

const rows = computed(() =>
  list.value.map((item) => ({
    ...item,
    month: item.saleDate ? item.saleDate.slice(0, 7) : 'unknown'
  }))
)

const groupedRows = computed(() => {
  const groups: { month: string; items: typeof rows.value }[] = []
  for (const row of rows.value) {
    const month = String(row.month || 'unknown')
    const group = groups.find((item) => item.month === month)
    if (group) group.items.push(row)
    else groups.push({ month, items: [row] })
  }
  return groups.map((group) => ({
    ...group,
    info: groupInfo(group.month),
    voidedCount: group.items.filter((item) => item.paymentStatus === 'VOIDED').length
  }))
})

const emptyTitle = computed(() => {
  if (store.query.keyword) return `没有找到与“${store.query.keyword}”相关的出栏单`
  const labels: Record<string, string> = {
    UNPAID: '没有未收款的出栏单',
    PARTIAL: '没有部分收款的出栏单',
    PAID: '没有已收款的出栏单',
    VOIDED: '没有已作废的出栏单'
  }
  return labels[store.query.status] || '还没有出栏单'
})

const emptyDesc = computed(() =>
  store.query.keyword
    ? '换个批次号或收购商试试。'
    : store.query.status === 'ALL'
      ? '批次可以出栏时，点“登记出栏单”记录出栏和收款。'
      : '当前养殖场还没有这个收款状态的出栏单。'
)

function groupInfo(month: string): { label: string; count: number; saleAmount?: number } {
  const group = store.groupOf(month)
  return {
    label: group?.label || month,
    count: Number(group?.count || 0),
    saleAmount: group?.saleAmount
  }
}

function goDetail(id?: number): void {
  if (!id) return
  void router.push({ name: 'WU-E04', params: { id: String(id) } })
}

function openReceive(item: Record<string, unknown>): void {
  receive.show(item as never)
}

async function openCreate(preselected?: number): Promise<void> {
  await formStore.openCreate(preselected)
}

async function onSaved(payload: { created: unknown; detail: unknown }): Promise<void> {
  const created = payload.created as { id?: number } | null
  if (created?.id) {
    await router.push({ name: 'WU-E04', params: { id: String(created.id) } })
    return
  }
  if (payload.detail) await store.load()
}

function onReceiveDone(): void {
  void store.load()
}

function resetFilters(): void {
  store.resetFilters()
}

async function handleQuery(): Promise<void> {
  const month = String(route.query.month || '')
  if (month && store.query.month !== month) {
    store.query.month = month
    await store.load()
  }
  const batchId = queryId(route.query, 'batchId', 'batch_id')
  if (route.query.action === 'create') {
    await openCreate(batchId)
    await clearAction()
  } else if (batchId) await openCreate(batchId)
}

/** 表单已打开后去掉 action：刷新不重复弹出，再次点击快捷登记也能触发。 */
async function clearAction(): Promise<void> {
  if (!route.query.action) return
  const query = { ...route.query }
  delete query.action
  await router.replace({ query })
}

onMounted(async () => {
  await store.load()
  await handleQuery()
})

useFarmReload(() => store.load())

watch(
  () => route.query,
  () => {
    if (store.status === 'loading') return
    const month = String(route.query.month || '')
    if (month && month !== store.query.month) {
      store.query.month = month
      void store.load()
    }
    if (route.query.action === 'create') {
      void openCreate(queryId(route.query, 'batchId', 'batch_id')).then(clearAction)
    }
  }
)
</script>

<template>
  <zw-shell side="user" active="WU-E03" :crumbs="['经营管理', '出栏单']">
    <zw-page-head
      title="出栏单"
      desc="本养殖场的全部出栏单，已收齐和已作废的也在这里查看；未收齐的款项在经营概览中跟进"
    >
      <template #actions>
        <v-btn color="primary" prepend-icon="add" @click="openCreate()">登记出栏单</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading' && !list.length">
      <v-skeleton-loader type="article, table-row@8" />
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="你没有查看经营数据的权限" desc="经营功能只对所有者和场长开放。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="出栏单加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load()">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div class="fin-toolbar e03-toolbar">
        <zw-seg size="small" :model-value="store.query.status" :items="store.tabs" @update:model-value="store.setStatus($event as string)" />
        <div class="fin-toolbar-right">
          <v-select
            :model-value="store.query.month"
            :items="store.monthOptions"
            item-title="title"
            item-value="value"
            class="e03-month"
            density="compact"
            hide-details
            @update:model-value="store.setMonth($event as string)"
          />
          <v-text-field
            v-model="store.query.keyword"
            class="e03-search"
            placeholder="搜索批次号或收购商"
            prepend-inner-icon="search"
            density="compact"
            hide-details
            clearable
            maxlength="30"
            @click:clear="store.setKeyword('')"
          />
        </div>
      </div>

      <zw-card flush>
        <zw-empty v-if="store.status === 'empty' || store.status === 'filtered_empty'" icon="local_shipping" :title="emptyTitle" :desc="emptyDesc">
          <v-btn v-if="store.query.status === 'ALL' && !store.query.keyword" color="primary" @click="openCreate()">登记出栏单</v-btn>
          <v-btn v-else variant="text" @click="resetFilters">查看全部出栏单</v-btn>
        </zw-empty>

        <template v-else>
          <template v-for="(group, groupIndex) in groupedRows" :key="group.month">
            <div class="e03-group">
              <span class="e03-group-label">{{ group.info.label }}</span>
              <span class="e03-group-count">· {{ group.info.count }} 张</span>
              <span v-if="store.query.status !== 'VOIDED'" class="e03-group-amount">
                <template v-if="Number(group.info.saleAmount) > 0 || !group.voidedCount">
                  销售金额 {{ formatMoney(group.info.saleAmount) }} 元
                </template>
                <template v-else>已作废 {{ group.voidedCount }} 张不计入</template>
              </span>
            </div>
            <v-data-table
              :headers="headers"
              :items="group.items"
              item-value="id"
              hide-default-footer
              :hide-default-header="groupIndex > 0"
              class="e03-table"
              :row-props="(data: { item: Record<string, unknown> }) => ({
                class: data.item.paymentStatus === 'VOIDED' ? 'e03-row--void' : ''
              })"
              @click:row="(_event: unknown, data: { item: Record<string, unknown> }) => goDetail(Number(data.item.id || 0))"
            >
              <template #item.batchNo="{ item }">
                <div class="py-2">
                  <div class="zw-cell-main num">{{ item.batchNo }}</div>
                  <div class="zw-cell-sub">{{ item.penName }}</div>
                </div>
              </template>
              <template #item.saleDate="{ item }">
                <div>{{ dateText(item.saleDate) }}</div>
                <div class="zw-cell-sub num">
                  {{ formatNumber(item.quantity) }} {{ item.countUnitText || '头' }} ·
                  {{ formatNumber(item.totalWeight, 1) }} 千克
                </div>
              </template>
              <template #item.buyerName="{ item }">
                <span class="e03-ellipsis">{{ item.buyerName || '—' }}</span>
              </template>
              <template #item.saleAmount="{ item }">
                <span class="num e03-nowrap" :class="{ 'e03-strike': item.paymentStatus === 'VOIDED' }">{{ formatMoney(item.saleAmount) }} 元</span>
              </template>
              <template #item.progress="{ item }">
                <div v-if="item.paymentStatus !== 'VOIDED'" class="e03-progress">
                  <div class="e03-progress-bar">
                    <v-progress-linear :model-value="Number(item.progressRate || 0) * 100" height="6" rounded color="primary" />
                    <span class="num">{{ formatPercent(item.progressRate) }}</span>
                  </div>
                  <div class="zw-cell-sub num">已收 {{ formatMoney(item.receivedAmount) }} 元</div>
                </div>
                <span v-else class="e03-void-note">{{ dateText(String(item.voidedAt || '')) }}作废</span>
              </template>
              <template #item.unpaidAmount="{ item }">
                <template v-if="item.paymentStatus !== 'VOIDED'">
                  <span class="num e03-left" :class="{ late: item.dueState === 'OVERDUE' }">{{ formatMoney(item.unpaidAmount) }} 元</span>
                  <div v-if="item.dueState === 'OVERDUE'" class="zw-cell-sub e03-due-late">
                    <zw-icon name="schedule" :size="13" />{{ item.dueStateText }}
                  </div>
                  <div v-else-if="item.paymentStatus === 'PAID'" class="zw-cell-sub">收齐 / {{ dateText(item.paidOffDate) }}</div>
                  <div v-else class="zw-cell-sub">约定 {{ dateText(item.effectiveDueDate) }}</div>
                </template>
                <span v-else class="e03-void-note">{{ item.voidReason || '已作废' }}</span>
              </template>
              <template #item.paymentStatus="{ item }">
                <div class="e03-status">
                  <zw-tag v-if="item.hasPendingChange" type="warning" size="small" icon="edit_note">待审核</zw-tag>
                  <zw-tag :type="paymentTone(item.paymentStatus)" size="small" :icon="paymentIcon(item.paymentStatus)">
                    {{ item.paymentStatusText }}
                  </zw-tag>
                </div>
              </template>
              <template #item.actions="{ item }">
                <span class="zw-cell-actions" @click.stop>
                  <a v-if="item.paymentStatus === 'UNPAID' || item.paymentStatus === 'PARTIAL'" @click="openReceive(item)">登记收款</a>
                  <a @click="goDetail(item.id)">详情</a>
                </span>
              </template>
            </v-data-table>
          </template>

          <div class="zw-table-foot zw-table-foot--in">
            <span class="e03-rule zw-faint body-s">
              按出栏日期倒序；月份销售金额不含已作废的出栏单
            </span>
            <zw-pager
              :total="store.total"
              :page="store.query.page"
              :size="store.query.size"
              @update:page="store.setPage"
              @update:size="store.setSize"
            />
          </div>
        </template>
      </zw-card>
    </template>

    <SaleOrderFormDrawer @saved="onSaved" />
    <ReceiveDialog @done="onReceiveDone" />
  </zw-shell>
</template>

<style scoped>
.fin-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.fin-toolbar-right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.e03-month { width: 140px; }
.e03-search { width: 240px; }
.e03-table :deep(tbody tr) { cursor: pointer; }
.e03-table :deep(td) { white-space: nowrap; }
.e03-group { display: flex; align-items: center; height: 40px; padding: 0 16px; background: var(--fill-1); border-top: 1px solid var(--line-1); font-size: 14px; }
.e03-group-label { font-weight: 600; color: var(--text-1); }
.e03-group-count { color: var(--text-2); margin-left: 4px; }
.e03-group-amount { margin-left: auto; font-size: 12px; color: var(--text-3); }
.e03-ellipsis { display: block; max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.e03-nowrap { white-space: nowrap; }
.e03-progress-bar { display: flex; align-items: center; gap: 8px; }
.e03-progress-bar :deep(.v-progress-linear) { flex: 1; }
.e03-progress-bar span { width: 44px; text-align: right; font-size: 12px; color: var(--text-2); }
.e03-left { font-weight: 600; }
.e03-left.late { color: var(--error); }
.e03-due-late { display: flex; align-items: center; gap: 2px; color: var(--error); }
.e03-status { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.e03-row--void :deep(td) { color: var(--text-3); }
.e03-row--void .zw-cell-main { color: var(--text-2); }
.e03-strike { text-decoration: line-through; }
.e03-void-note { display: inline-block; max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-3); font-size: 12px; }
.e03-rule { display: inline-flex; align-items: center; gap: 6px; }
</style>
