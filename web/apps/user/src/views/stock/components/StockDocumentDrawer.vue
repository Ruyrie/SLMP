<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { formatMoney, formatNumber, today } from '@zmt/shared'
import {
  fetchStockInbound,
  fetchStockLoss,
  fetchStockOutbound,
  type StockFlowItem,
  type StockInbound,
  type StockLoss,
  type StockOutbound
} from '@/api/stock'
import { allocationText, datetimeText, flowQty, moneyText, packQtyText } from '@/views/stock/stock'
import { useStockRefundStore, stockDocumentScope } from '@/stores/stock/refund'
import { useUiStore } from '@/stores/ui'
import { useFarmStore } from '@/stores/farm'

/**
 * 出入库流水 → 单据详情；退货单支持登记与当日更正实际到账退款。
 * 入库、出库、报废分别走 API-M09 / M21 / M19；接种、用药、消毒等自动扣减产生的流水没有
 * 独立单据接口，回退为“列表行 + 流水详情”，不补造服务端没有的字段。
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    flow: StockFlowItem | null
    staff?: boolean
  }>(),
  {
    flow: null,
    staff: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'refund-saved': [value: StockOutbound]
  'view-finance-record': [id: number]
}>()

type DocKind = 'INBOUND' | 'OUTBOUND' | 'LOSS' | 'FLOW'

const BIZ_LABELS: Record<string, string> = {
  INBOUND: '入库单',
  OUTBOUND: '出库单',
  LOSS: '报废单',
  IMMUNE_RECORD: '接种记录',
  MEDICATION_RECORD: '用药记录',
  DISINFECT_RECORD: '消毒记录',
  FEED_RECORD: '投喂记录',
  STOCKTAKE: '盘点单',
  CHANGE_REQUEST: '修改申请'
}

const inbound = ref<StockInbound | null>(null)
const outbound = ref<StockOutbound | null>(null)
const loss = ref<StockLoss | null>(null)
const loading = ref(false)
const error = ref('')
const refund = useStockRefundStore()
const router = useRouter()
const ui = useUiStore()
const farm = useFarmStore()
let loadSeq = 0
const canManageRefund = computed(() => !props.staff && farm.role !== 'staff' && isReturn.value
  && outbound.value?.material_type === 'DRUG')

const docKind = computed<DocKind>(() => {
  const type = props.flow?.biz_type
  if (type === 'INBOUND' || type === 'OUTBOUND' || type === 'LOSS') return type
  return 'FLOW'
})
const isReturn = computed(
  () => props.flow?.flow_type === 'RETURN' || outbound.value?.out_type === 'RETURN'
)
const title = computed(() => {
  if (docKind.value === 'INBOUND') return '入库单详情'
  if (docKind.value === 'OUTBOUND') return isReturn.value ? '退货单详情' : '领用单详情'
  if (docKind.value === 'LOSS') return '报废单详情'
  const name = props.flow?.flow_type_text
  return name ? `${name}流水详情` : '出入库流水详情'
})
const subTitle = computed(() => {
  const fallbackId = props.flow?.biz_id ? `#${props.flow.biz_id}` : ''
  if (docKind.value === 'INBOUND') {
    return [inbound.value?.inbound_no || fallbackId, '只读'].filter(Boolean).join(' · ')
  }
  if (docKind.value === 'OUTBOUND') {
    return [outbound.value?.outbound_no || fallbackId, isReturn.value ? '' : '只读'].filter(Boolean).join(' · ')
  }
  if (docKind.value === 'LOSS') {
    return [loss.value?.loss_type_text || '报废单', loss.value?.loss_id ? `#${loss.value.loss_id}` : fallbackId, '只读']
      .filter(Boolean)
      .join(' · ')
  }
  return [props.flow?.flow_type_text || '', props.flow?.occur_at ? datetimeText(props.flow.occur_at) : '', '只读']
    .filter(Boolean)
    .join(' · ')
})
const inboundPriceUnit = computed(() => {
  const qty = inbound.value?.qty
  return inbound.value?.input_unit === 'PACK' ? qty?.pack_unit || qty?.unit_text || '' : qty?.unit_text || ''
})
const outboundTargetText = computed(() => {
  const o = outbound.value
  if (!o) return '—'
  if (o.target_type === 'PEN') return o.pen?.name || '—'
  return o.batch?.batch_no || o.pen?.name || o.destination_text || '—'
})
const sourceText = computed(() => {
  const flow = props.flow
  if (!flow?.biz_type) return ''
  return `${BIZ_LABELS[flow.biz_type] || flow.biz_type}${flow.biz_id ? ` #${flow.biz_id}` : ''}`
})
const flowStockAfter = computed(() => {
  const flow = props.flow
  if (flow?.stock_qty_after === null || flow?.stock_qty_after === undefined) return '—'
  return `${formatNumber(flow.stock_qty_after)} ${flow.qty?.unit_text || ''}`.trim()
})

function statusTagType(status?: string): string {
  return status === 'VOID' ? 'error' : 'neutral'
}

function priceText(value?: number | null, unit?: string | null): string {
  if (value === null || value === undefined) return '—'
  return `每${unit || '单位'} ${formatMoney(value)} 元`
}

async function load(): Promise<void> {
  const flow = props.flow
  const seq = ++loadSeq
  const scope = stockDocumentScope()
  inbound.value = null
  outbound.value = null
  loss.value = null
  error.value = ''
  const bizId = Number(flow?.biz_id || 0)
  if (!flow || !bizId || docKind.value === 'FLOW') {
    loading.value = false
    return
  }
  loading.value = true
  try {
    if (docKind.value === 'INBOUND') {
      const data = await fetchStockInbound(bizId)
      if (seq === loadSeq && scope === stockDocumentScope()) inbound.value = data
    } else if (docKind.value === 'OUTBOUND') {
      const data = await fetchStockOutbound(bizId)
      if (seq === loadSeq && scope === stockDocumentScope()) outbound.value = data
    } else if (docKind.value === 'LOSS') {
      const data = await fetchStockLoss(bizId)
      if (seq === loadSeq && scope === stockDocumentScope()) loss.value = data
    }
  } catch (e) {
    if (seq === loadSeq && scope === stockDocumentScope()) error.value = (e as { message?: string }).message || '单据详情加载失败'
  } finally {
    if (seq === loadSeq && scope === stockDocumentScope()) loading.value = false
  }
}

function clearDocument(): void {
  ++loadSeq
  inbound.value = outbound.value = loss.value = null
  loading.value = false
  error.value = ''
  refund.close()
}

async function submitRefund(): Promise<void> {
  const seq = loadSeq
  const scope = stockDocumentScope()
  const mode = refund.mode
  const result = await refund.submit()
  if (!result || seq !== loadSeq || scope !== stockDocumentScope() || !props.modelValue) return
  outbound.value = result
  emit('refund-saved', result)
  ui.toast(mode === 'edit' ? '退款已更正，关联收入已同步更新' : '退款已登记，已自动生成关联收入')
}

function openFinanceRecord(): void {
  const id = outbound.value?.finance_record_id
  if (!id || !canManageRefund.value) return
  emit('update:modelValue', false)
  emit('view-finance-record', id)
  void router.push({ name: 'WU-E02', query: { recordId: String(id), date: outbound.value?.refund_date || '', detail: '1' } })
}

watch(() => [props.modelValue, props.flow?.flow_id, props.flow?.biz_id, props.flow?.biz_type, props.staff], () => {
  clearDocument()
  if (props.modelValue) void load()
}, { immediate: true })

watch(stockDocumentScope, () => {
  clearDocument()
  if (props.modelValue) emit('update:modelValue', false)
}, { flush: 'sync' })

onBeforeUnmount(clearDocument)
</script>

<template>
  <zw-drawer
    :model-value="modelValue"
    :title="title"
    :sub="subTitle"
    width="560"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="loading" class="stock-doc-loading">
      <v-skeleton-loader type="heading" />
      <v-skeleton-loader type="list-item-two-line@4" />
    </div>

    <template v-else-if="docKind === 'INBOUND' && inbound">
      <div class="stock-doc-head">
        <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="inventory_2" :size="18" /></span>
        <div>
          <div class="zw-cell-main">{{ inbound.material_name || '—' }}</div>
          <div class="zw-cell-sub">{{ inbound.category_text || '兽药与疫苗' }} · 批号 {{ inbound.lot_no || '—' }}</div>
        </div>
        <zw-tag size="small" :type="statusTagType(inbound.status)">{{ inbound.status_text || inbound.status || '—' }}</zw-tag>
      </div>
      <zw-desc :cols="2" bordered :label-width="88">
        <zw-desc-item label="单号">{{ inbound.inbound_no || `#${inbound.inbound_id}` }}</zw-desc-item>
        <zw-desc-item label="入库日期">{{ inbound.inbound_date || '—' }}</zw-desc-item>
        <zw-desc-item label="物料">{{ inbound.material_name || '—' }}</zw-desc-item>
        <zw-desc-item label="生产批号">{{ inbound.lot_no || '—' }}</zw-desc-item>
        <zw-desc-item label="数量">{{ packQtyText(inbound.qty) }}</zw-desc-item>
        <zw-desc-item label="单位">{{ inbound.qty?.unit_text || '—' }}</zw-desc-item>
        <zw-desc-item label="有效期至">{{ inbound.expire_date || '—' }}</zw-desc-item>
        <zw-desc-item label="状态">{{ inbound.status_text || '—' }}</zw-desc-item>
        <template v-if="!staff">
          <zw-desc-item label="单价">{{ priceText(inbound.unit_price, inboundPriceUnit) }}</zw-desc-item>
          <zw-desc-item label="入库金额">{{ moneyText(inbound.amount) }}</zw-desc-item>
        </template>
        <zw-desc-item label="经办人">
          {{ inbound.created_by?.name || '—' }}<template v-if="inbound.created_by?.role_text">（{{ inbound.created_by.role_text }}）</template>
        </zw-desc-item>
        <zw-desc-item label="登记时间">{{ datetimeText(inbound.created_at) }}</zw-desc-item>
      </zw-desc>
      <zw-notice v-if="inbound.has_outbound" type="info" class="stock-doc-notice">
        该入库单已发生出库，数量和单价不能直接修改；需要变更时请提交修改申请。
      </zw-notice>
    </template>

    <template v-else-if="docKind === 'OUTBOUND' && outbound">
      <div class="stock-doc-head">
        <span class="zw-ico-tile zw-ico-tile--primary">
          <zw-icon :name="isReturn ? 'assignment_return' : 'output'" :size="18" />
        </span>
        <div>
          <div class="zw-cell-main">{{ outbound.material_name || '—' }}</div>
          <div class="zw-cell-sub">{{ outbound.out_type_text || (isReturn ? '退货' : '领用') }} · 批号 {{ outbound.lot_no || '—' }}</div>
        </div>
        <zw-tag size="small" :type="statusTagType(outbound.status)">{{ outbound.status_text || outbound.status || '—' }}</zw-tag>
      </div>
      <zw-desc :cols="2" bordered :label-width="88">
        <zw-desc-item label="单号">{{ outbound.outbound_no || `#${outbound.outbound_id}` }}</zw-desc-item>
        <zw-desc-item :label="isReturn ? '退货日期' : '领用日期'">{{ outbound.out_date || '—' }}</zw-desc-item>
        <zw-desc-item label="物料">{{ outbound.material_name || '—' }}</zw-desc-item>
        <zw-desc-item label="生产批号">{{ outbound.lot_no || '—' }}</zw-desc-item>
        <zw-desc-item label="数量">{{ packQtyText(outbound.qty) }}</zw-desc-item>
        <zw-desc-item label="单位">{{ outbound.qty?.unit_text || '—' }}</zw-desc-item>
        <zw-desc-item label="有效期至">{{ outbound.expire_date || '—' }}</zw-desc-item>
        <zw-desc-item label="状态">{{ outbound.status_text || '—' }}</zw-desc-item>
        <template v-if="!isReturn">
          <zw-desc-item label="领用人">{{ outbound.receiver?.name || '—' }}</zw-desc-item>
          <zw-desc-item label="关联对象">{{ outboundTargetText }}</zw-desc-item>
        </template>
        <template v-else>
          <zw-desc-item label="退回对象">{{ outbound.return_to || '—' }}</zw-desc-item>
          <zw-desc-item label="退货原因">{{ outbound.return_reason_text || outbound.return_reason || '—' }}</zw-desc-item>
        </template>
        <template v-if="!staff">
          <zw-desc-item label="单价">{{ priceText(outbound.unit_price, outbound.qty?.unit_text) }}</zw-desc-item>
          <zw-desc-item :label="isReturn ? '退货金额' : '领用成本'">{{ moneyText(outbound.amount) }}</zw-desc-item>
        </template>
        <zw-desc-item label="说明" :span="2">{{ outbound.remark || '—' }}</zw-desc-item>
        <zw-desc-item label="经办人">
          {{ outbound.created_by?.name || '—' }}<template v-if="outbound.created_by?.role_text">（{{ outbound.created_by.role_text }}）</template>
        </zw-desc-item>
        <zw-desc-item label="登记时间">{{ datetimeText(outbound.created_at) }}</zw-desc-item>
      </zw-desc>

      <div v-if="canManageRefund" class="stock-doc-section">
        <div class="stock-doc-section-title">退款到账</div>
        <template v-if="outbound.refund_status === 'REFUNDED'">
          <zw-tag type="success" size="small" icon="check_circle">已登记退款</zw-tag>
          <zw-desc :cols="2" bordered :label-width="88" class="mt-3">
            <zw-desc-item label="到账金额">{{ moneyText(outbound.refund_amount) }}</zw-desc-item>
            <zw-desc-item label="到账日期">{{ outbound.refund_date || '—' }}</zw-desc-item>
            <zw-desc-item label="登记人">{{ outbound.refund_registered_by?.name || '—' }}</zw-desc-item>
            <zw-desc-item label="登记时间">{{ datetimeText(outbound.refund_registered_at || undefined) }}</zw-desc-item>
            <zw-desc-item label="说明" :span="2">{{ outbound.refund_remark || '—' }}</zw-desc-item>
          </zw-desc>
          <v-btn v-if="outbound.finance_record_id" variant="text" prepend-icon="receipt_long" class="mt-2" @click="openFinanceRecord">查看关联收支流水</v-btn>
          <zw-notice type="info" class="stock-doc-notice">
            已自动生成关联收入，请勿重复手工记账。退款仅支持登记当天更正；退货单数量和作废操作已锁定。
          </zw-notice>
        </template>
        <zw-notice v-else type="info">
          {{ outbound.refund_block_reason || '尚未登记退款。实际到账后请登记退款，系统会自动生成关联收入，请勿重复手工记账。' }}
        </zw-notice>
      </div>

      <div v-if="!staff && outbound.allocations?.length" class="stock-doc-section">
        <div class="stock-doc-section-title">成本分摊</div>
        <div v-for="row in outbound.allocations" :key="row.batch_id" class="stock-doc-alloc">
          <span>
            {{ row.batch_no || `#${row.batch_id}` }}<template v-if="allocationText(row)"> · {{ allocationText(row) }}</template>
          </span>
          <span class="num">{{ moneyText(row.amount) }}</span>
        </div>
      </div>
    </template>

    <template v-else-if="docKind === 'LOSS' && loss">
      <div class="stock-doc-head">
        <span class="zw-ico-tile zw-ico-tile--error"><zw-icon name="delete" :size="18" /></span>
        <div>
          <div class="zw-cell-main">{{ loss.material_name || '—' }}</div>
          <div class="zw-cell-sub">{{ loss.loss_type_text || '报废' }} · 批号 {{ loss.lot_no || '—' }}</div>
        </div>
        <zw-tag size="small" type="neutral">{{ loss.status === 'VOID' ? '已作废' : '有效' }}</zw-tag>
      </div>
      <zw-desc :cols="2" bordered :label-width="96">
        <zw-desc-item label="单号">报废单 #{{ loss.loss_id }}</zw-desc-item>
        <zw-desc-item label="报废日期">{{ loss.loss_date || '—' }}</zw-desc-item>
        <zw-desc-item label="物料">{{ loss.material_name || '—' }}</zw-desc-item>
        <zw-desc-item label="生产批号">{{ loss.lot_no || '—' }}</zw-desc-item>
        <zw-desc-item label="数量">{{ packQtyText(loss.qty) }}</zw-desc-item>
        <zw-desc-item label="单位">{{ loss.qty?.unit_text || '—' }}</zw-desc-item>
        <zw-desc-item label="报废原因">{{ loss.reason_text || loss.reason || '—' }}</zw-desc-item>
        <zw-desc-item label="报废后结存">{{ packQtyText(loss.remaining) }}</zw-desc-item>
        <template v-if="!staff">
          <zw-desc-item label="单价">{{ priceText(loss.unit_price, loss.qty?.unit_text) }}</zw-desc-item>
          <zw-desc-item label="报废损失">{{ moneyText(loss.amount) }}</zw-desc-item>
        </template>
        <zw-desc-item label="说明" :span="2">{{ loss.remark || '—' }}</zw-desc-item>
        <zw-desc-item label="经办人">
          {{ loss.created_by?.name || '—' }}<template v-if="loss.created_by?.role_text">（{{ loss.created_by.role_text }}）</template>
        </zw-desc-item>
        <zw-desc-item label="登记时间">{{ datetimeText(loss.created_at) }}</zw-desc-item>
      </zw-desc>
      <zw-notice type="info" class="stock-doc-notice">
        报废损失不计入批次成本；确认后如需修改，需要提交修改申请。
      </zw-notice>
    </template>

    <template v-else>
      <zw-notice v-if="error" type="warning" title="单据详情加载失败" class="stock-doc-notice">
        {{ error }}，以下为流水记录。
      </zw-notice>
      <zw-notice v-else-if="docKind !== 'FLOW'" type="info" class="stock-doc-notice">
        这条流水没有关联单据，以下为流水记录。
      </zw-notice>
      <zw-notice v-else type="info" class="stock-doc-notice">
        接种、用药、消毒等自动扣减产生的流水没有独立单据，以下为流水记录。
      </zw-notice>

      <template v-if="flow">
        <div class="stock-doc-head">
          <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="receipt_long" :size="18" /></span>
          <div>
            <div class="zw-cell-main">{{ flow.material_name || '—' }}</div>
            <div class="zw-cell-sub">{{ flow.flow_type_text || '出入库' }} · {{ flow.status_text || '—' }}</div>
          </div>
        </div>
        <zw-desc :cols="2" bordered :label-width="88">
          <zw-desc-item label="时间">{{ datetimeText(flow.occur_at) }}</zw-desc-item>
          <zw-desc-item label="类型">{{ flow.flow_type_text || '—' }}</zw-desc-item>
          <zw-desc-item label="物料">{{ flow.material_name || '—' }}</zw-desc-item>
          <zw-desc-item label="生产批号">{{ flow.lot_no || '—' }}</zw-desc-item>
          <zw-desc-item label="数量">{{ flowQty(flow) }}</zw-desc-item>
          <zw-desc-item label="单位">{{ flow.qty?.unit_text || '—' }}</zw-desc-item>
          <zw-desc-item label="结存">{{ flowStockAfter }}</zw-desc-item>
          <zw-desc-item label="状态">{{ flow.status_text || '—' }}</zw-desc-item>
          <zw-desc-item label="关联">{{ flow.related_desc || flow.pen_name || flow.batch_no || '—' }}</zw-desc-item>
          <zw-desc-item label="经办人">{{ flow.operator_name || '—' }}</zw-desc-item>
          <template v-if="!staff">
            <zw-desc-item label="单价">{{ priceText(flow.unit_price, flow.qty?.unit_text) }}</zw-desc-item>
            <zw-desc-item label="金额">{{ moneyText(flow.amount) }}</zw-desc-item>
          </template>
          <zw-desc-item v-if="sourceText" label="来源单据" :span="2">{{ sourceText }}</zw-desc-item>
        </zw-desc>
      </template>
    </template>

    <template #foot>
      <v-btn variant="outlined" @click="emit('update:modelValue', false)">关闭</v-btn>
      <v-btn v-if="canManageRefund && outbound?.actions?.can_refund" color="primary" prepend-icon="payments" @click="refund.begin(outbound)">登记退款</v-btn>
      <v-btn v-if="canManageRefund && outbound?.actions?.can_edit_refund" color="primary" prepend-icon="edit" @click="refund.begin(outbound, 'edit')">更正退款</v-btn>
    </template>
  </zw-drawer>

  <zw-dialog :model-value="refund.open" :title="refund.mode === 'edit' ? '更正退款' : '登记退款'" width="480" :persistent="refund.submitting" @update:model-value="!$event && !refund.submitting && refund.close()">
    <zw-notice type="info" class="mb-4">
      {{ refund.mode === 'edit' ? '更正将同步更新原关联收入，不新增收支流水。' : '确认退款已实际到账，且此前未手工登记这笔收入。保存后自动生成关联收入。' }}
    </zw-notice>
    <zw-notice v-if="refund.error" type="warning" class="mb-4">{{ refund.error }}</zw-notice>
    <v-text-field v-model="refund.amount" class="mb-4" label="实际到账金额（元）" inputmode="decimal" :disabled="refund.fieldsLocked" :error-messages="refund.errors.amount" placeholder="请输入实际收到的金额" />
    <v-text-field v-model="refund.date" class="mb-4" label="到账日期" type="date" :min="outbound?.out_date" :max="today()" :disabled="refund.fieldsLocked" :error-messages="refund.errors.refund_date" />
    <v-textarea v-model="refund.remark" label="说明（选填）" rows="3" maxlength="200" counter="200" :disabled="refund.fieldsLocked" :error-messages="refund.errors.remark" />
    <template #actions>
      <v-btn variant="outlined" :disabled="refund.submitting" @click="refund.close()">取消</v-btn>
      <v-btn color="primary" :loading="refund.submitting" @click="submitRefund">{{ refund.submitting ? '正在提交' : refund.uncertain ? '重试原提交' : refund.mode === 'edit' ? '确认更正' : '确认到账并登记' }}</v-btn>
    </template>
  </zw-dialog>
</template>

<style scoped>
.stock-doc-loading { padding: 8px 0; }
.stock-doc-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 16px;
  border-radius: var(--radius-sm);
  background: var(--fill-1);
}
.stock-doc-head > div { flex: 1; min-width: 0; }
.stock-doc-notice { margin-top: 16px; }
.stock-doc-section { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--line-1); }
.stock-doc-section-title { font-size: 14px; font-weight: 600; color: var(--text-1); margin-bottom: 8px; }
.stock-doc-alloc { display: flex; justify-content: space-between; gap: 12px; padding: 5px 0; font-size: 13px; color: var(--text-2); }
.stock-doc-alloc .num { color: var(--text-1); }
</style>
