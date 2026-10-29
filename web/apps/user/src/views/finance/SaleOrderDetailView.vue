<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import type { DataTableHeader } from 'vuetify'
import { formatMoney, formatNumber, formatPercent } from '@zmt/shared'
import { useSaleOrderDetailStore } from '@/stores/finance/saleOrderDetail'
import { useSaleOrderFormStore } from '@/stores/finance/saleOrderForm'
import { useReceiveStore } from '@/stores/finance/receive'
import { useUiStore } from '@/stores/ui'
import { priceText, dateText, dateTimeText, dueTone, paymentIcon, paymentMethodText, paymentTone } from '@/stores/finance/shared'
import SaleOrderFormDrawer from './components/SaleOrderFormDrawer.vue'
import ReceiveDialog from './components/ReceiveDialog.vue'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'

const store = useSaleOrderDetailStore()
const formStore = useSaleOrderFormStore()
const receive = useReceiveStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()
const { detail } = storeToRefs(store)
const farm = useFarmStore()
/** 计数单位：出栏单的批次品类（鸡“只”、猪“头”），没有时按当前养殖场 */
const unit = computed(() => detail.value?.countUnitText || farm.countUnit)
const moreMenu = ref(false)
const pageRef = ref<HTMLElement | null>(null)

const receiptHeaders: DataTableHeader[] = [
  { title: '收款日期', key: 'receiptDate', sortable: false, width: 150 },
  { title: '收款金额', key: 'amount', sortable: false, align: 'end', width: 140 },
  { title: '收款方式', key: 'paymentMethodText', sortable: false, width: 120 },
  { title: '登记人', key: 'operatorName', sortable: false, width: 110 },
  { title: '收入记录', key: 'financeRecordId', sortable: false }
]

const desc = computed(() => {
  const value = detail.value
  if (!value) return ''
  const breed = [value.breedName, value.categoryName].filter(Boolean).join('')
  return `${value.penName || ''} · ${breed} · ${dateText(value.saleDate)}出栏 · ${value.buyerName || ''}`
})

const dueLine = computed(() => {
  const value = detail.value
  if (!value) return ''
  if (value.paymentStatus === 'PAID') return `${dateText(value.paidOffDate)}已收齐`
  if (value.paymentStatus === 'VOIDED') return `${dateText(value.voidedAt)}已作废`
  return `约定收款 ${dateText(value.effectiveDueDate)}`
})

const priceLine = computed(() => {
  const value = detail.value
  if (!value) return ''
  if (value.pricingMethod === 'BY_HEAD') {
    return `${formatNumber(value.quantity)} ${unit.value} · 每${unit.value} ${priceText(value.unitPrice)} 元`
  }
  return `${formatNumber(value.quantity)} ${unit.value} · ${formatNumber(
    value.totalWeight,
    1
  )} 千克 · 每千克 ${priceText(value.unitPrice)} 元`
})

const sortedReceipts = computed(() =>
  [...(detail.value?.receipts || [])].sort((a, b) => String(b.receiptDate || '').localeCompare(String(a.receiptDate || '')))
)

const activeReceipts = computed(() => (detail.value?.receipts || []).filter(item => item.status !== 'VOID'))
const receiptsTotal = computed(() => activeReceipts.value.reduce((sum, item) => sum + Number(item.amount || 0), 0))

const timelineItems = computed(() => {
  const value = detail.value
  const items: {
    title: string
    time?: string
    desc?: string
    tone?: string
    icon?: string
    tag?: string
    tagType?: string
  }[] = []
  if (!value) return items
  const pending = value.pendingChange
  if (pending) {
    items.push({
      title: `提交修改申请 · ${changeTypeText(pending.changeType)}`,
      time: dateTimeText(value.registeredAt),
      desc: pending.summaryText || '审核期间原单照常有效',
      tone: 'warning',
      icon: 'edit_note',
      tag: '待审核',
      tagType: 'warning'
    })
  }
  items.push({
    title: '登记出栏单',
    time: dateTimeText(value.registeredAt),
    desc: `${formatNumber(value.quantity)} ${unit.value} · ${formatNumber(
      value.totalWeight,
      1
    )} 千克 · ${value.registrant?.name || ''}`,
    tone: 'success',
    icon: 'local_shipping'
  })
  for (const log of store.logs) {
    items.push({
      title: `${log.opActionText || '修改'} · ${log.operator?.name || ''}`,
      time: dateTimeText(log.createdAt),
      desc: log.changes?.length
        ? log.changes.map((change) => `${change.fieldName || ''} ${change.before || '—'} → ${change.after || '—'}`).join('；')
        : log.reason || '',
      tone: 'neutral',
      icon: 'history'
    })
  }
  return items
})

function changeTypeText(type?: string): string {
  return type === 'VOID' ? '作废' : type === 'QUANTITY' ? '修改数量' : '修改单价'
}

const settlement = computed(() => detail.value?.settlementBrief)
const settlementStatusText = computed(() => {
  const status = settlement.value?.status
  return status === 'LOCKED' ? '已结算并锁定' : status === 'RUNNING' ? '截至今天' : ''
})

const canReceive = computed(() => store.canReceive && detail.value?.paymentStatus !== 'VOIDED')
const canEdit = computed(() => !store.hasReceipts && detail.value?.paymentStatus !== 'VOIDED')
const canVoid = computed(() => canEdit.value && detail.value?.paymentStatus !== 'PAID')

function goBack(): void {
  if (window.history.length > 1) router.back()
  else void router.push({ name: 'WU-E03' })
}

function onHeadClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.zw-back')) goBack()
}

function openReceive(): void {
  if (detail.value) receive.show(detail.value as never)
}

function onReceiveDone(): void {
  void store.refresh()
}

async function openEdit(): Promise<void> {
  if (!detail.value) return
  await formStore.openEdit(detail.value)
}

async function onSaved(): Promise<void> {
  await store.refresh()
}

function goSettlement(): void {
  if (!detail.value?.batchId) return
  void router.push({ name: 'WU-E05', query: { batchId: String(detail.value.batchId) } })
}

function goApplyChange(): void {
  if (!detail.value?.id) return
  if (store.pendingChange) {
    ui.toast('这张出栏单已有一条待审核的修改申请，审核完成后再提交')
    return
  }
  void router.push({
    name: 'WU-U01',
    query: {
      action: 'create',
      biz_type: 'SALE_ORDER',
      biz_id: String(detail.value.id)
    }
  })
}

function scrollRecords(): void {
  moreMenu.value = false
  const el = document.getElementById('e04-records')
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el?.classList.add('e04-flash')
  window.setTimeout(() => el?.classList.remove('e04-flash'), 1600)
}

function openIncomeRecord(financeRecordId?: number, receiptDate?: string): void {
  if (!financeRecordId) return
  void router.push({ name: 'WU-E02', query: { recordId: String(financeRecordId), date: receiptDate || '' } })
}

function openBatch(): void {
  if (!detail.value?.batchId) return
  void router.push({ name: 'WU-L04', params: { batchId: String(detail.value.batchId) } })
}

onMounted(() => void store.load(Number(route.params.id || 0)))
// 切换养殖场：出栏单属于旧场，回到出栏单列表（见路由 farmScopedParent）
useFarmReload(() => store.load(Number(route.params.id || 0)))
watch(
  () => route.params.id,
  (id) => {
    if (id && Number(id) !== store.id) void store.load(Number(id))
  }
)
</script>

<template>
  <div ref="pageRef">
    <zw-shell side="user" active="WU-E03" :crumbs="['经营管理', '出栏单', detail?.batchNo || '详情']">
      <div v-if="store.status === 'loading' && !detail">
        <v-skeleton-loader type="article, article, table-row@5" />
      </div>

      <zw-card v-else-if="store.status === 'forbidden'">
        <zw-empty icon="lock" tone="warning" title="你没有查看经营数据的权限" desc="经营功能只对所有者和场长开放。">
          <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
        </zw-empty>
      </zw-card>

      <zw-card v-else-if="store.status === 'error'">
        <zw-empty icon="cloud_off" tone="error" title="出栏单加载失败" :desc="store.error">
          <v-btn variant="outlined" @click="goBack">返回</v-btn>
          <v-btn color="primary" variant="outlined" @click="store.load(store.id)">重新加载</v-btn>
        </zw-empty>
      </zw-card>

      <template v-else-if="detail">
        <zw-page-head :title="`出栏单 ${detail.batchNo}`" :desc="desc" :back="true" @click="onHeadClick">
          <template #tags>
            <zw-tag :type="paymentTone(detail.paymentStatus)" :icon="paymentIcon(detail.paymentStatus)" size="small">
              {{ detail.paymentStatusText }}
            </zw-tag>
            <zw-tag v-if="store.pendingChange" type="warning" size="small" icon="edit_note">修改申请待审核</zw-tag>
          </template>
          <template #actions>
            <v-btn variant="outlined" prepend-icon="calculate" @click="goSettlement">查看批次核算</v-btn>
            <v-menu v-model="moreMenu" location="bottom end">
              <template #activator="{ props: activator }">
                <v-btn v-bind="activator" variant="outlined" append-icon="arrow_drop_down">更多</v-btn>
              </template>
              <div class="zw-pop">
                <template v-if="store.hasReceipts">
                  <div class="zw-pop-item" :class="{ 'e04-disabled': store.pendingChange }" @click="store.pendingChange ? null : goApplyChange()">
                    <zw-icon name="edit_note" :size="18" />
                    <span class="e04-pop-label">提交修改申请</span>
                    <span class="e04-pop-note">{{ store.pendingChange ? '审核中' : '需审核' }}</span>
                  </div>
                  <div class="zw-pop-item" @click="scrollRecords">
                    <zw-icon name="history" :size="18" />
                    <span class="e04-pop-label">查看修改记录</span>
                  </div>
                </template>
                <template v-else-if="detail.paymentStatus !== 'VOIDED'">
                  <div class="zw-pop-item" @click="moreMenu = false; openEdit()">
                    <zw-icon name="edit" :size="18" />
                    <span class="e04-pop-label">修改</span>
                  </div>
                  <div class="zw-pop-item" @click="scrollRecords">
                    <zw-icon name="history" :size="18" />
                    <span class="e04-pop-label">查看修改记录</span>
                  </div>
                  <div class="zw-pop-div" />
                  <div v-if="canVoid" class="zw-pop-item e04-danger" @click="moreMenu = false; store.openVoid()">
                    <zw-icon name="block" :size="18" />
                    <span class="e04-pop-label">作废</span>
                  </div>
                </template>
                <template v-else>
                  <div class="zw-pop-item" @click="scrollRecords">
                    <zw-icon name="history" :size="18" />
                    <span class="e04-pop-label">查看修改记录</span>
                  </div>
                </template>
              </div>
            </v-menu>
            <v-btn v-if="canReceive" color="primary" prepend-icon="add" @click="openReceive">登记收款</v-btn>
          </template>
        </zw-page-head>

        <zw-notice v-if="store.hasReceipts && !store.pendingChange" class="mb-4" icon="info">
          这张出栏单已有收款。修改数量、单价或作废，需要提交修改申请，管理员审核通过后生效，审核期间原单照常有效。
        </zw-notice>
        <zw-notice v-else-if="store.pendingChange" class="mb-4" icon="info">
          修改申请待审核：{{ detail.pendingChange?.summaryText || '审核期间原单照常有效。' }}
          <template #action><a class="zw-link" @click="goApplyChange">查看</a></template>
        </zw-notice>

        <div class="e04-top">
          <zw-card title="收款进度" class="e04-progress-card">
            <template #extra>
              <div class="e04-due">
                <zw-icon name="event" :size="18" :color="detail.paymentStatus === 'PAID' ? 'success' : 'text-3'" />
                <span>{{ dueLine }}</span>
                <zw-tag
                  v-if="detail.paymentStatus !== 'PAID' && detail.paymentStatus !== 'VOIDED' && (detail.dueState === 'OVERDUE' || detail.dueState === 'DUE_SOON')"
                  :type="dueTone(detail.dueState)"
                  :icon="detail.dueState === 'OVERDUE' ? 'schedule' : ''"
                  size="small"
                >{{ detail.dueStateText }}</zw-tag>
              </div>
            </template>

            <div class="e04-amounts">
              <div class="e04-amount-main">
                <div class="zw-faint body-s">销售金额</div>
                <div class="e04-sale num">{{ formatMoney(detail.saleAmount) }} 元</div>
                <div class="zw-faint body-s">{{ priceLine }}</div>
              </div>
              <div class="e04-amount-item">
                <div class="zw-faint body-s">已收</div>
                <div class="e04-received num">已收 {{ formatMoney(detail.receivedAmount) }} 元</div>
                <div class="zw-faint body-s">{{ activeReceipts.length }} 笔有效收款</div>
              </div>
              <div class="e04-amount-item">
                <div class="zw-faint body-s">未收</div>
                <div class="e04-unpaid num" :class="{ late: detail.dueState === 'OVERDUE' }">
                  {{ formatMoney(detail.unpaidAmount) }} 元
                </div>
                <div class="zw-faint body-s">收款合计不能超过销售金额</div>
              </div>
            </div>

            <div class="e04-bar">
              <v-progress-linear :model-value="store.progressPercent" height="8" rounded color="primary" />
              <div class="e04-bar-labels">
                <span>已收 {{ formatPercent(detail.progressRate) }}</span>
                <span>未收 {{ detail.paymentStatus === 'VOIDED' ? '—' : formatPercent(1 - Number(detail.progressRate || 0)) }}</span>
              </div>
            </div>
          </zw-card>

          <zw-card title="出栏信息" class="e04-info-card">
            <zw-desc :cols="1" :label-width="72">
              <zw-desc-item label="批次号">
                <a class="zw-link num" @click="openBatch">{{ detail.batchNo }}</a>
                <span class="zw-faint body-s ml-1">{{ detail.batchStatus === 'ENDED' ? '· 已全部出栏' : '· 在养' }}</span>
              </zw-desc-item>
              <zw-desc-item label="计价方式">
                {{ detail.pricingMethodText || '' }} · 每{{ detail.pricingMethod === 'BY_HEAD' ? unit : '千克' }}
                {{ priceText(detail.unitPrice) }} 元
              </zw-desc-item>
              <zw-desc-item label="平均体重">{{ formatNumber(detail.avgWeight, 1) }} 千克</zw-desc-item>
              <zw-desc-item label="收购商">{{ detail.buyerName || '—' }}</zw-desc-item>
              <zw-desc-item label="登记人">
                {{ detail.registrant?.name || '—' }} · {{ dateTimeText(detail.registeredAt) }}
              </zw-desc-item>
            </zw-desc>
          </zw-card>
        </div>

        <div class="e04-bottom">
          <zw-card id="e04-records" title="收款记录" :sub="`${activeReceipts.length} 笔有效收款 · 合计 ${formatMoney(receiptsTotal)} 元`" flush>
            <v-data-table :headers="receiptHeaders" :items="sortedReceipts" item-value="id" hide-default-footer>
              <template #item.receiptDate="{ item }">
                <span>{{ dateText(item.receiptDate) }}</span>
              </template>
              <template #item.amount="{ item }">
                <span class="num e04-strong" :class="{ 'text-decoration-line-through': item.status === 'VOID' }">{{ formatMoney(item.amount) }} 元</span>
                <zw-tag v-if="item.status === 'VOID'" size="small">已作废</zw-tag>
              </template>
              <template #item.paymentMethodText="{ item }">{{ item.paymentMethodText || paymentMethodText(item.paymentMethod) }}</template>
              <template #item.operatorName="{ item }">{{ item.operatorName || '—' }}</template>
              <template #item.financeRecordId="{ item }">
                <span v-if="item.financeRecordId" class="e04-income">
                  <zw-tag size="small" icon="receipt_long">{{ item.status === 'VOID' ? '收入已作废' : '已生成收入' }}</zw-tag>
                  <a class="zw-link" @click="openIncomeRecord(item.financeRecordId, item.receiptDate)">查看</a>
                </span>
                <span v-else class="zw-faint">—</span>
              </template>
              <template #no-data>
                <div class="e04-empty-note">还没有收款记录</div>
              </template>
            </v-data-table>
            <div class="e04-receipt-foot zw-faint body-s">
              每笔收款自动生成一条收入记录（出栏销售），可在收支记账中查看
            </div>
          </zw-card>

          <zw-card title="修改记录" sub="登记、修改与审核结果" class="e04-timeline-card">
            <v-skeleton-loader v-if="store.logsLoading" type="list-item-two-line@2" />
            <zw-timeline v-else-if="timelineItems.length" :items="timelineItems" />
            <div v-else class="e04-empty-note">还没有修改记录</div>
          </zw-card>
        </div>

        <zw-card
          title="批次核算"
          :sub="`${detail.batchNo} · ${settlementStatusText}`"
          class="mt-4"
        >
          <template #extra>
            <a class="zw-link" @click="goSettlement">查看批次核算</a>
          </template>
          <div v-if="settlement" class="e04-settle">
            <div class="e04-settle-item">
              <div class="zw-faint body-s">{{ settlement.status === 'LOCKED' ? '批次盈亏' : '截至今天盈亏' }}</div>
              <div
                class="e04-settle-v num"
                :class="Number(settlement.profit) > 0 ? 'positive' : Number(settlement.profit) < 0 ? 'negative' : ''"
              >{{ settlement.profit == null ? '待核算' : `${Number(settlement.profit) > 0 ? '+' : Number(settlement.profit) < 0 ? '−' : ''}${formatMoney(Math.abs(Number(settlement.profit)))} 元` }}</div>
            </div>
            <div class="e04-settle-item">
              <div class="zw-faint body-s">销售收入</div>
              <div class="e04-settle-v num">{{ formatMoney(settlement.saleIncome) }} 元</div>
            </div>
            <div class="e04-settle-item">
              <div class="zw-faint body-s">总成本</div>
              <div class="e04-settle-v num">{{ formatMoney(settlement.costTotal) }} 元</div>
            </div>
            <div class="e04-settle-item">
              <div class="zw-faint body-s">单{{ unit }}成本</div>
              <div class="e04-settle-v num">{{ formatMoney(settlement.costPerHead) }} 元</div>
            </div>
            <div class="e04-settle-item">
              <div class="zw-faint body-s">料肉比</div>
              <div class="e04-settle-v num">{{ settlement.fcr !== undefined && settlement.fcr !== null ? Number(settlement.fcr).toFixed(2) : '暂不可计算' }}</div>
            </div>
          </div>
          <div class="e04-settle-note zw-faint body-s">
            销售收入按出栏单金额计算，不论货款是否收齐<template v-if="Number(detail.receivedAmount)">（已收 {{ formatMoney(detail.receivedAmount) }} 元）</template>。
            <template v-if="settlement?.pendingSupplementCount">该批次有 {{ settlement.pendingSupplementCount }} 笔补录费用待确认，确认后重新计算。</template>
            <template v-if="settlement && settlement.fcr == null">料肉比需要完整的入栏、称重和饲料记录，且净增重须大于 0。</template>
          </div>
        </zw-card>
      </template>

      <SaleOrderFormDrawer @saved="onSaved" />
      <ReceiveDialog @done="onReceiveDone" />

      <zw-dialog v-model="store.voidOpen" title="作废这张出栏单？" icon="block" tone="error" width="480">
        <p>
          作废后 {{ detail?.batchNo }} 存栏恢复 {{ formatNumber(detail?.quantity || 0) }} {{ unit }}，批次回到在养状态。
        </p>
        <zw-form-item label="作废原因" required :error="store.voidError">
          <v-textarea v-model="store.voidReason" rows="2" maxlength="200" counter="200" placeholder="写写作废原因，最多 200 字" />
        </zw-form-item>
        <template #actions>
          <v-btn variant="outlined" :disabled="store.voidBusy" @click="store.voidOpen = false">取消</v-btn>
          <v-btn color="error" :loading="store.voidBusy" :disabled="!store.voidReason.trim()" @click="store.confirmVoid()">作废</v-btn>
        </template>
      </zw-dialog>
    </zw-shell>
  </div>
</template>

<style scoped>
.e04-top { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 16px; align-items: stretch; }
.e04-bottom { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 16px; align-items: start; margin-top: 16px; }
.e04-due { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-2); }
.e04-amounts { display: grid; grid-template-columns: minmax(0, 1.6fr) 1fr 1fr; gap: 32px; align-items: start; }
.e04-amount-main .e04-sale { font-size: 32px; line-height: 42px; font-weight: 600; color: var(--text-1); }
.e04-amount-item { padding-top: 24px; }
.e04-received { font-size: 24px; line-height: 32px; font-weight: 600; color: var(--text-1); }
.e04-unpaid { font-size: 24px; line-height: 32px; font-weight: 600; color: var(--text-1); }
.e04-unpaid.late { color: var(--error); }
.e04-bar { margin-top: 20px; }
.e04-bar-labels { display: flex; justify-content: space-between; margin-top: 6px; font-size: 12px; color: var(--text-3); }
.e04-info-card :deep(.zw-desc-item) { padding: 6px 0; }
.e04-strong { font-weight: 500; }
.e04-income { display: inline-flex; align-items: center; gap: 8px; }
.e04-empty-note { padding: 24px 0; text-align: center; color: var(--text-3); font-size: 14px; }
.e04-receipt-foot { padding: 10px 16px; border-top: 1px solid var(--line-1); }
.e04-timeline-card :deep(.zw-card-body) { max-height: 240px; overflow: auto; }
.e04-settle { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; }
.e04-settle-v { font-size: 24px; line-height: 32px; font-weight: 600; color: var(--text-1); }
.e04-settle-v.positive { color: var(--success); }
.e04-settle-v.negative { color: var(--error); }
.e04-settle-note { margin-top: 12px; }
.e04-pop-label { flex: 1; }
.e04-pop-note { font-size: 12px; color: var(--text-3); }
.e04-pop-item { display: flex; align-items: center; gap: 10px; height: 36px; padding: 0 16px; cursor: pointer; }
.e04-pop-item:hover { background: var(--fill-1); }
.e04-pop-item.e04-danger { color: var(--error); }
.e04-pop-item.e04-disabled { color: var(--text-disabled); cursor: default; }
.e04-pop-item.e04-disabled:hover { background: transparent; }
.e04-flash { animation: e04-flash 1.5s ease; }
@keyframes e04-flash {
  0%, 100% { box-shadow: 0 0 0 0 transparent; }
  20% { box-shadow: 0 0 0 2px var(--primary); }
}
</style>
