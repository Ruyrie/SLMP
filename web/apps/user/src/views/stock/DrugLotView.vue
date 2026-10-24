<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, formatDate, formatMoney, formatNumber, today as todayIso } from '@zmt/shared'
import {
  createDrugScrap,
  fetchStockInbound,
  newRequestKey,
  type DrugFlowGroup,
  type StockFlowItem,
  type StockInbound
} from '@/api/stock'
import StockDocumentDrawer from '@/views/stock/components/StockDocumentDrawer.vue'
import { useDrugLotStore } from '@/stores/stock/lot'
import { useFarmReload } from '@/composables/useFarmReload'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import {
  categoryIcon,
  categoryText,
  datetimeText,
  daysText,
  flowIcon,
  flowQty,
  flowTimeText,
  moneyText,
  monthDayText,
  numberValue,
  packQtyText,
  qtyUnit,
  qtyValue
} from '@/views/stock/stock'
const store = useDrugLotStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const lotId = computed(() => Number(route.params.lotId || 0))
const isStaff = computed(() => farm.role === 'staff')
const detail = computed(() => store.detail)
const unit = computed(() => detail.value?.stock?.unit_text || '')
const stockValue = computed(() => qtyValue(detail.value?.stock))
const flowGroups: Array<{ label: string; value: DrugFlowGroup }> = [
  { label: '全部', value: 'ALL' },
  { label: '入库', value: 'INBOUND' },
  { label: '领用', value: 'USE' },
  { label: '退货', value: 'RETURN' },
  { label: '报废', value: 'SCRAP' }
]
const banner = computed(() => {
  if (!detail.value) return null
  if (store.isRecall) {
    return {
      type: 'error',
      icon: 'block',
      title: '该批号已被平台停用：',
      text: `原因是${detail.value.recall_reason || '平台停用'}。该批号不能出库，登记接种时也选不到，只能报废。`
    }
  }
  if (store.isExpired) {
    return {
      type: 'error',
      icon: 'event_busy',
      title: '该批号已过期：',
      text: `有效期至 ${detail.value.expire_date || '—'}。该批号不能出库，登记用药时也选不到，只能报废。`
    }
  }
  if (store.isNear) {
    return {
      type: 'warning',
      icon: 'schedule',
      title: '该批号临期：',
      text: `${daysText(detail.value.days_to_expire)}。登记接种、用药或消毒时会优先扣减这个批号。`
    }
  }
  return null
})
const statusTag = computed(() => {
  const item = detail.value
  if (!item) return null
  if (item.lot_status === 'RECALLED') return { type: 'error', icon: 'block', text: '已停用' }
  if (item.lot_status === 'EXPIRED') return { type: 'error', icon: 'event_busy', text: '已过期' }
  if (item.lot_status === 'NEAR_EXPIRY') return { type: 'warning', icon: 'schedule', text: `临期 ${item.days_to_expire ?? 0} 天` }
  if (item.priority_out) return { type: 'neutral', icon: '', text: '优先出库' }
  return { type: 'neutral', icon: '', text: '正常' }
})
const desc = computed(() => {
  if (!detail.value) return ''
  return `批号 ${detail.value.lot_no || '—'} · 有效期至 ${detail.value.expire_date || '—'} · 入库 ${detail.value.first_inbound?.inbound_date ? monthDayText(detail.value.first_inbound.inbound_date) : '—'}`
})
const firstInboundText = computed(() => {
  const first = detail.value?.first_inbound
  const item = detail.value
  if (!first) return '—'
  const price = first.unit_price ? ` · 每${item?.stock?.pack_unit || '瓶'} ${formatMoney(first.unit_price)} 元` : ''
  return `${first.inbound_date ? monthDayText(first.inbound_date) : '—'} · ${packQtyText(first.qty)}${price}`
})
const outOrderText = computed(() => {
  if (store.isRecall) return '已停用，不能出库'
  if (store.isExpired) return '已过期，不能出库'
  if (store.isNear || detail.value?.priority_out) return '该药品有效期最早的可用批号，优先出库'
  return '按有效期先到期先出'
})
const actionsCanOut = computed(() => store.canOut && !isStaff.value)
const actionsCanScrap = computed(() => store.canScrap && !isStaff.value)
const documentOpen = ref(false)
const selectedFlow = ref<StockFlowItem | null>(null)
const inboundDoc = ref<StockInbound | null>(null)
let inboundDocSeq = 0
const changeTargetId = computed(() => Number(detail.value?.first_inbound?.inbound_id || 0))
const canRequestChange = computed(() => {
  if (!changeTargetId.value) return false
  const allowed = inboundDoc.value?.actions?.can_request_change
  if (allowed === true || allowed === false) return allowed
  return !!detail.value?.has_outbound
})

async function loadInboundDoc(): Promise<void> {
  const seq = ++inboundDocSeq
  const inboundId = changeTargetId.value
  inboundDoc.value = null
  if (!inboundId) return
  try {
    const doc = await fetchStockInbound(inboundId)
    if (seq === inboundDocSeq) inboundDoc.value = doc
  } catch {
    if (seq === inboundDocSeq) inboundDoc.value = null
  }
}

async function load(): Promise<void> {
  if (!lotId.value) return
  await store.load(lotId.value)
  await loadInboundDoc()
}
onMounted(load)
watch(() => route.params.lotId, () => void load())
useFarmReload(load)

function goBack(): void {
  void router.push({ name: 'WU-M02' })
}

function onHeadClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.zw-back')) goBack()
}

function openOutbound(): void {
  void router.push({ name: 'WU-M02', query: { action: 'outbound', lotId: String(lotId.value), drugId: String(detail.value?.drug_id || '') } })
}

function openDrugArchive(): void {
  void router.push({ name: 'WU-M02', query: { action: 'archive', drugId: String(detail.value?.drug_id || '') } })
}

function openRecallMessage(): void {
  void router.push({ name: 'WU-T04', query: { messageId: String(detail.value?.recall_message_id || '') } })
}

function openChangeRequest(): void {
  if (!changeTargetId.value) {
    ui.toast('这个批号没有入库单，无法提交修改申请', 'info')
    return
  }
  if (!canRequestChange.value) {
    ui.toast('这张入库单还没有发生出库，可以直接修改，不需要提交修改申请', 'info')
    return
  }
  void router.push({
    name: 'WU-U01',
    query: {
      action: 'create',
      biz_type: 'INBOUND',
      biz_id: String(changeTargetId.value)
    }
  })
}

function openChangeLog(): void {
  if (!changeTargetId.value) {
    ui.toast('这个批号没有入库单，暂无修改记录', 'info')
    return
  }
  // WU-U01 列表只支持按 biz_type / keyword 筛选；biz_id 一并写入地址，keyword 用入库单号
  // 把列表限定在这张入库单，避免同类型其他单据混在一起。
  const query: Record<string, string> = {
    biz_type: 'INBOUND',
    biz_id: String(changeTargetId.value)
  }
  if (inboundDoc.value?.inbound_no) query.keyword = inboundDoc.value.inbound_no
  void router.push({ name: 'WU-U01', query })
}

function openFlow(flow: StockFlowItem): void {
  selectedFlow.value = flow
  documentOpen.value = true
}

function onFlowGroup(value: string | number): void {
  void store.setFlowsGroup(value as DrugFlowGroup)
}

function exportFlows(): void {
  const rows = [['时间', '类型', '关联', '经办人', '数量', '结存']]
  for (const flow of store.allFlows) {
    rows.push([
      flow.occur_at || '',
      flow.flow_type_text || '',
      flow.related_desc || flow.pen_name || flow.batch_no || '',
      flow.operator_name || '',
      flowQty(flow),
      flow.stock_qty_after === null || flow.stock_qty_after === undefined ? '' : `${flow.stock_qty_after} ${flow.qty?.unit_text || ''}`
    ])
  }
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\r\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `批号${detail.value?.lot_no || lotId.value}-出入库流水.csv`
  link.click()
  URL.revokeObjectURL(link.href)
  ui.toast(`已导出批号 ${detail.value?.lot_no || ''} 的出入库流水`)
}

/* ---------------- 登记报废 ---------------- */
const scrapOpen = ref(false)
const scrapConfirm = ref(false)
const scrapSubmitting = ref(false)
const scrapForm = reactive({
  qty: 0,
  reason: 'OTHER',
  remark: '',
  date: ''
})
const scrapErrors = reactive<Record<string, string>>({})
const scrapReasonItems = computed(() => {
  const items = []
  if (store.isRecall) items.push({ title: '平台停用', value: 'PLATFORM_RECALL' })
  if (store.isExpired) items.push({ title: '已过期', value: 'EXPIRED' })
  items.push({ title: '破损', value: 'DAMAGED' }, { title: '其他', value: 'OTHER' })
  return items
})
const scrapInputUnit = computed<'PACK' | 'MEASURE'>(() => (detail.value?.category === 'VACCINE' ? 'MEASURE' : 'PACK'))
const scrapUnit = computed(() =>
  scrapInputUnit.value === 'MEASURE'
    ? detail.value?.stock?.unit_text || '头份'
    : detail.value?.stock?.pack_unit || detail.value?.stock?.unit_text || '件'
)
const scrapMaxQty = computed(() => {
  const stock = detail.value?.stock
  if (!stock) return 0
  if (scrapInputUnit.value === 'MEASURE') return qtyValue(stock)
  const packSize = numberValue(stock.pack_size) || 1
  return qtyValue(stock) / packSize
})
const scrapUnitPrice = computed(() =>
  scrapInputUnit.value === 'MEASURE' ? numberValue(detail.value?.avg_price) : numberValue(detail.value?.pack_price)
)
const scrapAmount = computed(() => numberValue(scrapForm.qty) * scrapUnitPrice.value)

function openScrap(): void {
  scrapOpen.value = true
  scrapConfirm.value = false
  scrapForm.qty = Number(scrapMaxQty.value.toFixed(2))
  scrapForm.reason = store.isRecall ? 'PLATFORM_RECALL' : store.isExpired ? 'EXPIRED' : ''
  scrapForm.remark = store.isRecall ? detail.value?.recall_reason || '' : ''
  scrapForm.date = todayIso()
  Object.keys(scrapErrors).forEach((key) => delete scrapErrors[key])
}

function prepareScrap(): void {
  Object.keys(scrapErrors).forEach((key) => delete scrapErrors[key])
  if (!numberValue(scrapForm.qty) || numberValue(scrapForm.qty) <= 0) scrapErrors.qty = '必须大于 0'
  else if (numberValue(scrapForm.qty) > scrapMaxQty.value + 0.0001) scrapErrors.qty = `超过当前结存 ${formatNumber(scrapMaxQty.value, 2)} ${scrapUnit.value}`
  if (!scrapForm.reason) scrapErrors.reason = '请选择报废原因'
  if (scrapForm.reason === 'OTHER' && !scrapForm.remark.trim()) scrapErrors.remark = '请写明报废原因'
  if (scrapForm.date && scrapForm.date < (detail.value?.first_inbound?.inbound_date || '')) {
    scrapErrors.date = `报废日期不能早于入库日期 ${formatDate(detail.value?.first_inbound?.inbound_date || '')}`
  }
  if (Object.keys(scrapErrors).length) return
  scrapConfirm.value = true
}

async function submitScrap(): Promise<void> {
  if (scrapSubmitting.value) return
  scrapSubmitting.value = true
  try {
    await createDrugScrap(
      {
        drug_lot_id: lotId.value,
        input_qty: numberValue(scrapForm.qty),
        input_unit: scrapInputUnit.value,
        reason: scrapForm.reason as 'PLATFORM_RECALL' | 'EXPIRED' | 'DAMAGED' | 'OTHER',
        remark: scrapForm.remark.trim() || undefined,
        scrap_date: scrapForm.date || undefined,
        stock_version: store.stockVersion
      },
      newRequestKey()
    )
    const cleared = numberValue(scrapForm.qty) >= scrapMaxQty.value
    ui.toast(cleared ? `已登记报废，${detail.value?.drug_name || ''} ${detail.value?.lot_no || ''} 结存已清零` : `已登记报废 ${formatNumber(scrapForm.qty, 2)} ${scrapUnit.value}`)
    scrapOpen.value = false
    scrapConfirm.value = false
    await store.refresh()
  } catch (error) {
    const err = error as ApiError
    for (const field of err.fieldErrors || []) scrapErrors[field.field] = field.message
    ui.toast(err.message || '报废失败，请重试', 'error')
  } finally {
    scrapSubmitting.value = false
  }
}
</script>

<template>
  <zw-shell side="user" active="WU-M03" :crumbs="['物资库存', '兽药与疫苗', '批号详情']">
    <zw-page-head :title="detail?.drug_name || '批号详情'" :desc="desc" back @click="onHeadClick">
      <template #tags>
        <zw-tag v-if="statusTag" :type="statusTag.type" :icon="statusTag.icon" size="small">{{ statusTag.text }}</zw-tag>
        <zw-tag v-if="detail?.category" size="small">{{ detail?.category_text || categoryText(detail?.category) }}</zw-tag>
      </template>
      <template #actions>
        <v-menu location="bottom end">
          <template #activator="{ props: menuProps }">
            <v-btn v-bind="menuProps" variant="outlined" prepend-icon="more_horiz">更多</v-btn>
          </template>
          <v-list density="compact" class="stock-menu">
            <v-list-item prepend-icon="medical_information" @click="openDrugArchive"><v-list-item-title>药品档案</v-list-item-title></v-list-item>
            <v-list-item v-if="detail?.first_inbound && canRequestChange" prepend-icon="edit_note" @click="openChangeRequest">
              <v-list-item-title>提交修改申请</v-list-item-title>
              <template #append><span class="stock-menu-note">需审核</span></template>
            </v-list-item>
            <v-list-item v-if="detail?.first_inbound" prepend-icon="history" @click="openChangeLog">
              <v-list-item-title>查看修改记录</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-btn v-if="actionsCanScrap" variant="outlined" color="error" prepend-icon="delete" @click="openScrap">登记报废</v-btn>
        <v-btn v-if="actionsCanOut" color="primary" prepend-icon="output" @click="openOutbound">出库</v-btn>
      </template>
    </zw-page-head>

    <zw-card v-if="isStaff">
      <zw-empty icon="lock" tone="warning" title="你没有物资库存的查看权限" desc="入库、盘点和报废由所有者或场长处理。">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <div v-else-if="store.status === 'loading'" class="stock-loading">
      <zw-card><v-skeleton-loader type="heading" /></zw-card>
      <div class="zw-grid-3 mt-4"><v-skeleton-loader v-for="item in 3" :key="item" type="article" /></div>
      <div class="lot-main mt-4"><v-skeleton-loader type="table-row@5" /><v-skeleton-loader type="article" /></div>
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '你没有物资库存的查看权限。'">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="批号加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load(lotId)">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="detail">
      <zw-notice v-if="banner" :type="banner.type" :icon="banner.icon" class="mb-4">
        <strong>{{ banner.title }}</strong>{{ banner.text }}
      </zw-notice>

      <div class="zw-grid-3 mb-4">
        <zw-stat label="结存" :icon="categoryIcon(detail.category)" :value="formatNumber(detail.stock?.value ?? 0)" :unit="unit" :foot="detail.stock?.pack_unit && detail.stock?.pack_count ? packQtyText(detail.stock) : (store.isNear ? '优先出库 · 先到期先出' : '')" />
        <zw-stat label="库存金额" icon="payments" :value="formatMoney(detail.stock_amount)" unit="元" :foot="detail.pack_price ? `每${detail.stock?.pack_unit || '瓶'} ${formatMoney(detail.pack_price)} 元` : detail.avg_price ? `每${unit} ${formatMoney(detail.avg_price)} 元` : '—'" />
        <zw-stat label="有效期至" icon="event" :value="detail.expire_date || '—'" :foot="store.isRecall ? '已停用，不能出库，只能报废' : store.isExpired ? '已过期，不能出库，只能报废' : daysText(detail.days_to_expire)" />
      </div>

      <div class="lot-main">
        <zw-card title="出入库流水" sub="报废损失不计入批次成本" flush class="lot-flows">
          <template #extra>
            <v-btn size="small" variant="outlined" prepend-icon="download" @click="exportFlows">导出</v-btn>
          </template>
          <div class="lot-flow-toolbar">
            <zw-seg :model-value="store.flowsGroup" :items="flowGroups" size="small" @update:model-value="onFlowGroup" />
          </div>
          <div v-if="store.flowsLoading" class="lot-loading"><v-skeleton-loader type="table-row@4" /></div>
          <zw-empty v-else-if="!store.flows.length" icon="receipt_long" title="还没有出入库记录" desc="登记入库后会显示在这里。" />
          <template v-else>
            <v-table class="stock-table stock-table--compact" hover>
              <thead>
                <tr>
                  <th style="width: 148px">时间</th>
                  <th style="width: 140px">类型</th>
                  <th>关联</th>
                  <th style="width: 100px">经办人</th>
                  <th class="text-end" style="width: 108px">数量</th>
                  <th class="text-end" style="width: 96px">结存</th>
                  <th class="text-end" style="width: 64px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="flow in store.flows" :key="flow.flow_id" class="stock-flow-row" @click="openFlow(flow)">
                  <td class="num">{{ flowTimeText(flow.occur_at) }}</td>
                  <td><div class="stock-flow-type"><span class="zw-ico-tile stock-ico-sm"><zw-icon :name="flowIcon(flow.flow_type)" :size="16" /></span>{{ flow.flow_type_text }}</div></td>
                  <td class="zw-muted">{{ flow.related_desc || flow.pen_name || flow.batch_no || '—' }}</td>
                  <td>{{ flow.operator_name || '—' }}</td>
                  <td class="text-end num" :class="flow.direction === 'IN' ? 'stock-in-text' : ''">{{ flowQty(flow) }}</td>
                  <td class="text-end num zw-muted">{{ flow.stock_qty_after === null || flow.stock_qty_after === undefined ? '—' : `${formatNumber(flow.stock_qty_after)} ${flow.qty?.unit_text || ''}` }}</td>
                  <td class="text-end"><a class="stock-link" @click.stop="openFlow(flow)">详情</a></td>
                </tr>
              </tbody>
            </v-table>
            <div class="zw-table-foot stock-table-foot">
              <span class="zw-faint body-s">按时间倒序 · 点流水行查看单据详情</span>
              <zw-pager :total="store.flowsTotal" :page="store.flowsPage" :size="store.flowsSize" @update:page="store.setFlowsPage" @update:size="store.setFlowsSize" />
            </div>
          </template>
        </zw-card>

        <zw-card title="批号信息" class="lot-info">
          <zw-desc :cols="1" :label-width="76">
            <zw-desc-item label="药品">{{ detail.drug_name }}</zw-desc-item>
            <zw-desc-item label="类别">{{ detail.category_text || categoryText(detail.category) }} · 每{{ detail.stock?.pack_unit || '瓶' }} {{ formatNumber(detail.stock?.pack_size || 0) }} {{ unit }}</zw-desc-item>
            <zw-desc-item label="生产批号">{{ detail.lot_no }}</zw-desc-item>
            <zw-desc-item label="有效期至">{{ detail.expire_date }}</zw-desc-item>
            <zw-desc-item label="入库">{{ firstInboundText }}</zw-desc-item>
            <zw-desc-item v-if="detail.recalled_at" label="停用时间">{{ datetimeText(detail.recalled_at) }} · {{ detail.recall_operator_text || '平台管理员' }}</zw-desc-item>
            <zw-desc-item v-if="detail.recall_reason" label="停用原因">{{ detail.recall_reason }}</zw-desc-item>
            <zw-desc-item v-if="detail.recall_message_id" label="停用通知"><a class="stock-link" @click="openRecallMessage">在消息中心查看</a></zw-desc-item>
            <zw-desc-item v-else label="出库顺序">{{ outOrderText }}</zw-desc-item>
          </zw-desc>
        </zw-card>
      </div>
    </template>

    <!-- 登记报废 -->
    <zw-drawer v-model="scrapOpen" title="登记报废" sub="报废损失不计入批次成本" width="560">
      <div class="scrap-lot">
        <span class="zw-ico-tile" :class="store.isRecall || store.isExpired ? 'zw-ico-tile--error' : 'zw-ico-tile--warning'"><zw-icon :name="categoryIcon(detail?.category)" :size="18" /></span>
        <div>
          <div class="zw-cell-main">{{ detail?.drug_name }}</div>
          <div class="zw-cell-sub">批号 {{ detail?.lot_no }} · 结存 {{ packQtyText(detail?.stock) }}</div>
        </div>
        <zw-tag v-if="statusTag" :type="statusTag.type" :icon="statusTag.icon" size="small">{{ statusTag.text }}</zw-tag>
      </div>
      <div class="zw-form-2">
        <zw-form-item label="报废数量" required :error="scrapErrors.qty" :hint="`最多 ${formatNumber(scrapMaxQty, 2)} ${scrapUnit}，全部报废后该批号结存清零`">
          <v-text-field v-model.number="scrapForm.qty" type="number" min="0" :suffix="scrapUnit" :error="!!scrapErrors.qty" />
        </zw-form-item>
        <zw-form-item label="报废日期" :error="scrapErrors.date">
          <v-text-field v-model="scrapForm.date" type="date" :min="detail?.first_inbound?.inbound_date" :error="!!scrapErrors.date" />
        </zw-form-item>
      </div>
      <zw-form-item label="报废原因" required :error="scrapErrors.reason" class="mt-3">
        <v-select v-model="scrapForm.reason" :items="scrapReasonItems" :error="!!scrapErrors.reason" />
      </zw-form-item>
      <zw-form-item label="说明" :optional="scrapForm.reason !== 'OTHER'" :error="scrapErrors.remark" class="mt-3" :hint="store.isRecall ? '已带入停用原因，可以修改；原因选“其他”时必填' : '原因选“其他”时必填'">
        <v-text-field v-model="scrapForm.remark" maxlength="100" :error="!!scrapErrors.remark" />
      </zw-form-item>
      <div class="stock-amount mt-4">
        <span>报废损失</span>
        <strong class="num">{{ moneyText(scrapAmount) }}</strong>
      </div>
      <zw-notice type="info" class="mt-4">报废损失不计入批次成本。报废记录确认后，修改需要提交修改申请。</zw-notice>
      <template #foot>
        <template v-if="scrapConfirm">
          <span class="scrap-confirm">
            <zw-icon name="delete" :size="20" />
            <span>
              <strong>{{ store.isRecall ? '报废这批疫苗？' : '报废这批物资？' }}</strong>
              {{ detail?.drug_name }} {{ detail?.lot_no }} {{ numberValue(scrapForm.qty) >= scrapMaxQty ? `共 ${packQtyText(detail?.stock)} 将全部报废` : `中的 ${formatNumber(scrapForm.qty, 2)} ${scrapUnit} 将报废` }}，损失 {{ formatMoney(scrapAmount) }} 元，不计入批次成本。确认后如需修改，要提交修改申请。
            </span>
          </span>
          <v-btn variant="outlined" @click="scrapConfirm = false">取消</v-btn>
          <v-btn color="error" prepend-icon="delete" :loading="scrapSubmitting" @click="submitScrap">报废</v-btn>
        </template>
        <template v-else>
          <v-btn variant="outlined" @click="scrapOpen = false">取消</v-btn>
          <v-btn color="error" prepend-icon="delete" @click="prepareScrap">登记报废</v-btn>
        </template>
      </template>
    </zw-drawer>

    <!-- 单据详情（入库单 / 领用单 / 退货单 / 报废单） -->
    <StockDocumentDrawer v-model="documentOpen" :flow="selectedFlow" :staff="isStaff" />
  </zw-shell>
</template>

<style scoped>
.stock-loading { display: flex; flex-direction: column; }
.stock-menu { min-width: 220px; }
.stock-menu-note { font-size: 12px; color: var(--text-3); }
.stock-link { color: var(--primary); cursor: pointer; }
.stock-table :deep(td) { white-space: nowrap; }
.stock-table--compact :deep(td) { height: 44px; }
.stock-flow-row { cursor: pointer; }
.stock-table-foot { padding: 12px 16px !important; border-top: 1px solid var(--line-1); }
.stock-in-text { color: var(--success); }
.stock-flow-type { display: flex; align-items: center; gap: 8px; }
.stock-ico-sm { width: 28px; height: 28px; }
.lot-main { display: flex; align-items: flex-start; gap: 16px; }
.lot-flows { flex: 1; min-width: 0; }
.lot-info { width: 380px; flex: 0 0 380px; }
.lot-flow-toolbar { padding: 12px 16px 0; }
.lot-loading { padding: 8px 16px; }
.scrap-lot { display: flex; align-items: center; gap: 10px; padding: 12px 14px; margin-bottom: 16px; border-radius: var(--radius-sm); background: var(--fill-1); }
.scrap-lot > div { flex: 1; }
.scrap-confirm { display: flex; align-items: flex-start; gap: 8px; margin-right: auto; padding: 9px 12px; border-radius: var(--radius-sm); background: var(--error-container); color: var(--on-error-container); font-size: 12px; line-height: 18px; }
.stock-amount { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-radius: var(--radius-sm); background: var(--fill-1); }
.stock-amount span { font-size: 14px; color: var(--text-2); }
.stock-amount strong { font-size: 20px; color: var(--text-1); }
</style>
