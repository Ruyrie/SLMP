<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatDate } from '@zmt/shared'

const props = withDefaults(defineProps<{
  modelValue?: string
  min?: string
  max?: string
  disabled?: boolean
  error?: boolean
  placeholder?: string
}>(), {
  modelValue: '',
  min: '',
  max: '',
  disabled: false,
  error: false,
  placeholder: '请选择日期'
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const menu = ref(false)
const display = computed(() => (props.modelValue ? formatDate(props.modelValue) : ''))

function onPick(value: unknown): void {
  if (typeof value !== 'string' || !value) return
  emit('update:modelValue', value)
  menu.value = false
}
</script>

<template>
  <v-menu v-model="menu" :close-on-content-click="false" location="bottom start" :disabled="disabled">
    <template #activator="{ props: activatorProps }">
      <v-text-field
        v-bind="activatorProps"
        :model-value="display"
        :placeholder="placeholder"
        :disabled="disabled"
        :error="error"
        readonly
        append-inner-icon="calendar_month"
        @click="menu = !disabled"
        @keydown.enter="menu = !disabled"
      />
    </template>
    <v-date-picker
      :model-value="modelValue"
      :min="min || undefined"
      :max="max || undefined"
      :disabled="disabled"
      color="primary"
      @update:model-value="onPick"
    />
  </v-menu>
</template>
