<script setup lang="ts">
import { computed } from 'vue'
import { useHerdRecordEditStore } from '@/stores/herd/recordEdit'
import { todayISO } from '@/stores/herd/shared'
import HerdDateField from './HerdDateField.vue'

const emit = defineEmits<{ saved: [] }>()
const store = useHerdRecordEditStore()
const visible = computed({ get: () => store.visible, set: value => { if (!value && !store.saving) store.reset() } })
const subtitle = computed(() => [store.record?.batch_no, store.record?.pen?.name,
  store.record?.to_pen?.name ? `转入 ${store.record.to_pen.name}` : ''].filter(Boolean).join(' · '))
function number(value: unknown): number | null { return value === '' || value === null ? null : Number(value) }
async function save(): Promise<void> { if (await store.save()) emit('saved') }
</script>

<template>
  <zw-drawer v-model="visible" :title="store.title" :sub="subtitle" width="560">
    <v-skeleton-loader v-if="store.loading" type="article, article" />
    <template v-else>
      <zw-notice v-if="store.error" type="error">{{ store.error }}</zw-notice>
      <template v-if="store.record">
        <zw-notice type="info">修改后将更新原记录与存栏，系统保留修改记录。</zw-notice>
        <div class="herd-edit-fields">
          <zw-form-item :label="store.isEntry ? '入栏数量' : '数量'" required :error="store.errors.quantity || store.errors.entry_count">
            <v-text-field :model-value="store.form.quantity ?? ''" type="number" min="1" step="1" suffix="头" :disabled="store.saving" @update:model-value="store.setQuantity" />
          </zw-form-item>
          <zw-form-item :label="store.isEntry ? '入栏日期' : '记录日期'" required :error="store.errors.event_date || store.errors.entry_date">
            <HerdDateField v-model="store.form.eventDate" :max="todayISO()" :disabled="store.saving" :error="!!(store.errors.event_date || store.errors.entry_date)" />
          </zw-form-item>
          <template v-if="store.isEntry">
            <zw-form-item label="入栏日龄" required :error="store.errors.entry_age_days">
              <v-text-field :model-value="store.form.entryAgeDays ?? ''" type="number" min="0" step="1" suffix="天" :disabled="store.saving" @update:model-value="store.form.entryAgeDays = number($event)" />
            </zw-form-item>
            <zw-form-item label="入栏平均体重（选填）" :error="store.errors.entry_avg_weight">
              <v-text-field :model-value="store.form.entryAvgWeight ?? ''" type="number" min="0.01" step="0.01" suffix="千克" :disabled="store.saving" @update:model-value="store.form.entryAvgWeight = number($event)" />
            </zw-form-item>
            <zw-form-item v-if="store.purchased" label="引种单价" required :error="store.errors.intro_unit_price">
              <v-text-field :model-value="store.form.introUnitPrice ?? ''" type="number" min="0" step="0.01" suffix="元 / 头" :disabled="store.saving" @update:model-value="store.form.introUnitPrice = number($event)" />
            </zw-form-item>
          </template>
          <zw-form-item v-else label="备注" :error="store.errors.remark">
            <v-textarea v-model="store.form.remark" :disabled="store.saving" maxlength="100" counter="100" rows="3" />
          </zw-form-item>
        </div>
        <zw-notice v-if="store.capacityMessage" type="warning">
          {{ store.capacityMessage }}
          <v-checkbox v-model="store.form.capacityConfirmed" label="已核对栏舍容量，确认继续修改" :disabled="store.saving" hide-details />
        </zw-notice>
      </template>
    </template>
    <template #foot>
      <v-btn v-if="store.error" variant="text" :disabled="store.saving" @click="store.reload()">重新加载</v-btn>
      <v-spacer />
      <v-btn variant="outlined" :disabled="store.saving" @click="store.reset()">取消</v-btn>
      <v-btn color="primary" :loading="store.saving" :disabled="!store.record || store.loading || store.saving || (!!store.capacityMessage && !store.form.capacityConfirmed)" @click="save">保存修改</v-btn>
    </template>
  </zw-drawer>
</template>

<style scoped>
.herd-edit-fields { display: grid; gap: 18px; margin-top: 20px; }
</style>
