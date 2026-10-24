<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatDate } from '@zmt/shared'

const props = withDefaults(defineProps<{
  modelValue?: string
  max?: string
  min?: string
  placeholder?: string
  disabled?: boolean
}>(), {
  modelValue: '',
  max: '',
  min: '',
  placeholder: '选择日期',
  disabled: false
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const menu = ref(false)
const display = computed(() => (props.modelValue ? formatDate(props.modelValue) : ''))

function pick(value: unknown): void {
  emit('update:modelValue', value ? String(value) : '')
  menu.value = false
}
</script>

<template>
  <v-menu v-model="menu" :close-on-content-click="false" location="bottom start" :disabled="disabled">
    <template #activator="{ props: activator }">
      <v-text-field
        v-bind="activator"
        :model-value="display"
        :placeholder="placeholder"
        readonly
        :disabled="disabled"
        append-inner-icon="calendar_month"
        class="finance-date"
      />
    </template>
    <v-date-picker
      :model-value="modelValue || null"
      :max="max || undefined"
      :min="min || undefined"
      @update:model-value="pick"
    />
  </v-menu>
</template>

<style scoped>
.finance-date :deep(input) { cursor: pointer; }
.finance-date :deep(.v-field) { cursor: pointer; }
</style>
