<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatMoney, formatNumber, today } from '@zmt/shared'
import { dateText } from '@/stores/finance/shared'
import { useSaleOrderFormStore } from '@/stores/finance/saleOrderForm'
import { useFarmStore } from '@/stores/farm'
import FinanceDateField from './FinanceDateField.vue'

const emit = defineEmits<{ saved: [{ created: unknown; detail: unknown }] }>()
const store = useSaleOrderFormStore()
const farm = useFarmStore()
const router = useRouter()

const title = computed(() => (store.mode === 'edit' ? '修改出栏单' : '登记出栏单'))
const sub = computed(() =>
  store.mode === 'edit'
    ? `${farm.farmName || '当前养殖场'} · 保存后重新计算销售金额，存量按差额恢复或扣减`
    : `${farm.farmName || '当前养殖场'} · 提交后自动扣减批次存栏，生成待收款提醒`
)

const withdrawalTitle = computed(() => {
  if (!store.withdrawal) return ''
  const date = store.withdrawal.sellableDate
  return `批次在休药期内，暂不能登记出栏${date ? `，可出栏日期 ${dateText(date)}` : ''}。`
})

const batchHint = computed(() => {
  const batch = store.selectedBatch
  if (!batch) return ''
  return `${batch.penName || ''} · ${batch.dayAge || 0} 日龄 · 存栏 ${formatNumber(batch.currentCount || 0)} ${
    batch.countUnitText || '头'
  } · ${batch.inWithdrawal ? `休药中${batch.withdrawalDaysLeft !== undefined ? ` · 剩 ${batch.withdrawalDaysLeft} 天` : ''}` : '不在休药期'}`
})

const ruleText = computed(() => {
  const batch = store.selectedBatch
  if (!batch) return ''
  const unit = batch.countUnitText || '头'
  if (store.form.quantity && store.remaining <= 0) {
    return `提交后 ${batch.batchNo} 全部出栏，批次自动结束并结算核算。`
  }
  if (!store.form.quantity) {
    return `出栏数量不能超过批次存栏 ${formatNumber(batch.currentCount || 0)} ${unit}。提交后自动扣减存栏，全部出栏后批次自动结束。`
  }
  return `出栏数量不能超过批次存栏 ${formatNumber(batch.currentCount || 0)} ${unit}。提交后自动扣减存栏，${
    batch.batchNo
  } 剩 ${formatNumber(store.remaining)} ${unit}；全部出栏后批次自动结束。`
})

const quantityHint = computed(() =>
  store.selectedBatch ? `最多 ${formatNumber(store.maxQuantity)} ${store.unitText}` : ''
)

const weightHint = computed(() =>
  store.avgWeight > 0 ? `均重 ${store.avgWeight.toFixed(1)} 千克` : '填写后自动计算均重'
)

const penSum = computed(() => store.penRows.reduce((sum, row) => sum + Number(row.quantity || 0), 0))

async function submit(): Promise<void> {
  const result = await store.submit()
  if (result.created || result.detail) emit('saved', result)
}

function goEntry(): void {
  store.close()
  void router.push({ name: 'WU-L05' })
}
</script>

<template>
  <zw-drawer v-model="store.open" :title="title" :sub="sub" width="560">
    <div v-if="store.loading" class="fin-order-loading">
      <v-skeleton-loader type="article, table-row@6" />
    </div>
    <zw-empty
      v-else-if="store.loadError"
      icon="cloud_off"
      tone="error"
      title="批次加载失败"
      :desc="store.loadError"
    >
      <v-btn variant="outlined" @click="store.loadBatches()">重新加载</v-btn>
    </zw-empty>
    <template v-else>
      <zw-notice v-if="store.withdrawal" type="warning" icon="block" :title="withdrawalTitle" class="mb-4">
        休药期结束前“提交”不可用，届时再登记出栏单。
      </zw-notice>

      <div class="zw-form">
        <zw-form-item
          label="批次"
          required
          :error="store.fieldError('batch_id') || store.fieldError('pen_quantities')"
        >
          <template #hint>
            <template v-if="batchHint">{{ batchHint }}</template>
            <template v-else>
              当前养殖场没有在养批次，先登记批次入栏。
              <a class="zw-link" @click="goEntry">去入栏</a>
            </template>
          </template>
          <v-select
            :model-value="store.form.batchId"
            :items="store.batches"
            :item-title="(item: Record<string, unknown>) => String(item.batchNo || '')"
            item-value="batchId"
            :disabled="store.mode === 'edit' || store.submitting"
            :error="!!store.fieldError('batch_id')"
            placeholder="选择批次"
            @update:model-value="store.setBatch(Number($event || 0))"
          >
            <template #selection="{ item }">
              <span class="num">{{ item.raw.batchNo }}</span>
              <span class="zw-faint body-s ml-2">{{ item.raw.penName }}</span>
            </template>
            <template #item="{ props: itemProps, item }">
              <v-list-item v-bind="itemProps" :subtitle="`${item.raw.penName} · 存栏 ${item.raw.currentCount} ${item.raw.countUnitText}`">
                <template #title>
                  <span class="fin-batch-option">
                    <span class="num">{{ item.raw.batchNo }}</span>
                    <zw-tag v-if="item.raw.inWithdrawal" type="warning" size="small" icon="medication">
                      休药中 · 剩 {{ item.raw.withdrawalDaysLeft }} 天
                    </zw-tag>
                    <zw-tag v-else type="success" size="small" icon="check_circle">可出栏</zw-tag>
                  </span>
                </template>
              </v-list-item>
            </template>
          </v-select>
        </zw-form-item>

        <div class="zw-form-2">
          <zw-form-item label="出栏日期" required :error="store.fieldError('sale_date')" hint="默认今天，不能晚于今天">
            <FinanceDateField v-model="store.form.saleDate" :max="today()" :disabled="store.submitting" />
          </zw-form-item>
          <zw-form-item label="计价方式" required>
            <zw-seg
              :model-value="store.form.pricingMethod"
              :items="[
                { label: '按重量', value: 'BY_WEIGHT' },
                { label: '按头', value: 'BY_HEAD' }
              ]"
              @update:model-value="store.setPricingMethod($event as 'BY_WEIGHT' | 'BY_HEAD')"
            />
          </zw-form-item>
        </div>

        <div class="zw-form-2">
          <zw-form-item label="数量" required :error="store.fieldError('quantity')" :hint="quantityHint">
            <v-text-field
              v-model="store.form.quantity"
              type="number"
              min="1"
              :suffix="store.unitText"
              :error="!!store.fieldError('quantity')"
              :disabled="store.submitting"
              placeholder="输入数量"
            />
          </zw-form-item>
          <zw-form-item label="总重" required :error="store.fieldError('total_weight')" :hint="weightHint">
            <v-text-field
              v-model="store.form.totalWeight"
              type="number"
              min="0"
              suffix="千克"
              :error="!!store.fieldError('total_weight')"
              :disabled="store.submitting"
              placeholder="输入总重"
            />
          </zw-form-item>
        </div>

        <zw-form-item label="单价" required :error="store.fieldError('unit_price')" :hint="store.priceReference">
          <v-text-field
            v-model="store.form.unitPrice"
            type="number"
            min="0"
            :suffix="store.priceUnit"
            class="fin-w-240"
            :error="!!store.fieldError('unit_price')"
            :disabled="store.submitting"
            placeholder="输入单价"
          />
        </zw-form-item>

        <div v-if="store.needsPenSplit" class="zw-form-item">
          <label class="zw-form-label">各栏舍出栏数量</label>
          <div class="fin-pen-split">
            <div v-for="row in store.penRows" :key="row.penId" class="fin-pen-row">
              <span class="fin-pen-name">{{ row.penName }}</span>
              <span class="zw-faint body-s">在栏 {{ formatNumber(row.stockCount) }} {{ store.unitText }}</span>
              <v-text-field
                v-model="row.quantity"
                type="number"
                min="0"
                density="compact"
                :suffix="store.unitText"
                :disabled="store.submitting || !!store.withdrawal"
                class="fin-w-120"
              />
            </div>
            <div class="zw-faint body-s">合计 {{ formatNumber(penSum) }} / {{ formatNumber(Number(store.form.quantity || 0)) }} {{ store.unitText }}</div>
            <div v-if="store.fieldError('pen_quantities')" class="fin-error">{{ store.fieldError('pen_quantities') }}</div>
          </div>
        </div>

        <div class="zw-form-2">
          <zw-form-item
            label="收购商"
            required
            :error="store.fieldError('buyer_name')"
            hint="可选用过的收购商或直接输入，最多 30 字"
          >
            <v-combobox
              v-model="store.form.buyerName"
              :items="store.buyerItems"
              :disabled="store.submitting"
              :error="!!store.fieldError('buyer_name')"
              placeholder="输入或选择收购商"
              maxlength="30"
            />
          </zw-form-item>
          <zw-form-item
            label="约定收款日期"
            optional
            :error="store.fieldError('due_date')"
            :hint="store.form.dueDate ? '约定日前 1 天提醒你和场长收款' : '不填写时，按出栏后第 7 天提醒收款'"
          >
            <FinanceDateField
              v-model="store.form.dueDate"
              :min="store.form.saleDate"
              :disabled="store.submitting"
              placeholder="选择日期"
            />
          </zw-form-item>
        </div>
      </div>

      <zw-notice v-if="!store.withdrawal && store.selectedBatch" class="mt-4" icon="info">
        {{ ruleText }}
      </zw-notice>
    </template>

    <template #foot>
      <div class="fin-amount">
        <div class="zw-faint body-s">销售金额 · 自动计算</div>
        <div class="fin-amount-v">{{ store.hasAmount ? `${formatMoney(store.saleAmount, 0)} 元` : '—' }}</div>
      </div>
      <v-spacer />
      <v-btn variant="outlined" :disabled="store.submitting" @click="store.close()">取消</v-btn>
      <v-btn
        color="primary"
        :loading="store.submitting"
        :disabled="!store.canSubmit || store.submitting || store.loading"
        @click="submit"
      >
        {{ store.mode === 'edit' ? '保存修改' : '提交' }}
      </v-btn>
    </template>
  </zw-drawer>
</template>

<style scoped>
.fin-order-loading { padding: 4px 0; }
.fin-batch-option { display: inline-flex; align-items: center; gap: 8px; }
.fin-w-240 { width: 240px; }
.fin-w-120 { width: 120px; }
.fin-pen-split { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.fin-pen-row { display: flex; align-items: center; gap: 12px; }
.fin-pen-name { width: 120px; font-size: 14px; color: var(--text-1); }
.fin-pen-row :deep(.v-input) { margin: 0; }
.fin-error { font-size: 12px; line-height: 18px; color: var(--error); }
.fin-amount { display: flex; flex-direction: column; gap: 2px; }
.fin-amount-v { font-size: 20px; line-height: 28px; font-weight: 600; color: var(--text-1); }
</style>
