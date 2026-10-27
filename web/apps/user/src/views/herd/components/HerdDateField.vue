<script setup lang="ts">
import { computed, ref } from 'vue'
import { useDate } from 'vuetify'
import { formatDate } from '@zmt/shared'
import { todayISO } from '@/stores/herd/shared'

const props = withDefaults(defineProps<{
  modelValue?: string
  max?: string
  disabled?: boolean
  error?: boolean
  placeholder?: string
}>(), {
  modelValue: '',
  max: '',
  disabled: false,
  error: false,
  placeholder: '请选择日期'
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const date = useDate()
const menu = ref(false)
const display = computed(() => props.modelValue ? formatDate(props.modelValue) : '')
const maxDate = computed(() => props.max || todayISO())

function onPick(value: unknown): void {
  const selected = value ? date.date(value) : null
  emit('update:modelValue', selected && date.isValid(selected) ? date.toISO(selected) : '')
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
      />
    </template>
    <v-date-picker
      :model-value="modelValue"
      :max="maxDate"
      :disabled="disabled"
      color="primary"
      @update:model-value="onPick"
    />
  </v-menu>
</template>
