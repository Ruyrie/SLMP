<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { formatMoney, today } from '@zmt/shared'
import { dateText } from '@/stores/finance/shared'
import { useReceiveStore } from '@/stores/finance/receive'
import { PAYMENT_METHODS } from '@/stores/finance/shared'
import FinanceDateField from './FinanceDateField.vue'

const emit = defineEmits<{ done: [] }>()
const store = useReceiveStore()
const { target, submitting, form, amountError, dateError, hint } = storeToRefs(store)

async function confirm(): Promise<void> {
  const detail = await store.submit()
  if (detail) emit('done')
}
</script>

<template>
  <zw-dialog v-model="store.open" title="登记收款" width="480">
    <div v-if="target" class="fin-receive-summary">
      <span class="num">{{ target.batchNo }}</span> · 销售金额 {{ formatMoney(target.saleAmount) }} 元 · 已收
      {{ formatMoney(target.receivedAmount) }} 元
    </div>
    <div class="zw-form">
      <zw-form-item label="收款金额" required :error="amountError" :hint="hint">
        <v-text-field
          v-model="store.form.amount"
          type="number"
          min="0"
          suffix="元"
          autofocus
          :error="!!amountError"
          :disabled="submitting"
          @keydown.enter="confirm"
        />
      </zw-form-item>
      <zw-form-item
        label="收款日期"
        required
        :error="dateError"
        :hint="`不能晚于今天，不能早于出栏日期 ${dateText(target?.saleDate)}`"
      >
        <FinanceDateField
          v-model="store.form.date"
          :max="today()"
          :min="target?.saleDate"
          :disabled="submitting"
        />
      </zw-form-item>
      <zw-form-item label="收款方式" required>
        <zw-seg v-model="store.form.method" :items="PAYMENT_METHODS" />
      </zw-form-item>
    </div>
    <zw-notice icon="receipt_long" class="mt-4">
      每笔收款会自动生成一条收入记录（出栏销售），可在收支记账中查看。收齐后出栏单变为“已收款”，待收款提醒自动完成。
    </zw-notice>
    <template #actions>
      <v-btn variant="outlined" :disabled="submitting" @click="store.close()">取消</v-btn>
      <v-btn color="primary" :loading="submitting" :disabled="submitting" @click="confirm">确认收款</v-btn>
    </template>
  </zw-dialog>
</template>

<style scoped>
.fin-receive-summary { padding: 10px 12px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 14px; color: var(--text-2); margin-bottom: 16px; }
</style>
